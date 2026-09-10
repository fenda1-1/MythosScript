package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** All mutable Minecraft state is read on the client thread, except integrated-server death evidence. */
public final class McpObservation {
    public static final McpObservation INSTANCE = new McpObservation();
    private final McpEventJournal journal = McpEventJournal.INSTANCE;
    private final Map<String,JsonObject> watches = new LinkedHashMap<>();
    private final Map<Integer,JsonObject> entities = new LinkedHashMap<>();
    private final Set<Integer> deathObserved = new HashSet<>();
    private final Map<String,String> blocks = new HashMap<>();
    private final ArrayBlockingQueue<Pending> pending = new ArrayBlockingQueue<>(2048);
    private final AtomicLong lostPackets = new AtomicLong();
    private Set<String> enabled = new LinkedHashSet<>(McpEventJournal.GROUPS);
    private World world;
    private Object connection;
    private JsonObject lastPlayer, lastGui;
    private JsonArray lastInventory;
    private int lastSlot = -1, sampleTicks = 5;
    private int[] extent = {10,10,10};
    private long tick;
    private long nextInteractionId;
    private volatile JsonObject actionContext;
    private volatile JsonObject lastContainerInteraction;
    private volatile long lastContainerInteractionMs;
    private boolean registered;
    private volatile boolean active;
    private volatile Channel channel;
    private volatile String epoch = "", playerUuid = "";
    private volatile int dimension;
    private volatile boolean integrated;
    private static final class Pending {
        final Object packet; final boolean outbound; final String epoch; final JsonObject actionContext;
        Pending(Object p, boolean out, String epoch) { this(p, out, epoch, null); }
        Pending(Object p, boolean out, String epoch, JsonObject actionContext) {
            packet=p; outbound=out; this.epoch=epoch; this.actionContext=actionContext;
        }
    }
    private McpObservation() {}
    public void register() { if (!registered) { registered=true; MinecraftForge.EVENT_BUS.register(this); } }
    public void setActive(boolean value) {
        active=value;
        if (!value) { epoch=""; channel=null; pending.clear(); actionContext=null; lastContainerInteraction=null;
            lastContainerInteractionMs=0L; journal.end(); journal.wake(); }
    }
    private void emit(String group, String type, JsonObject data) {
        if (!active || !enabled.contains(group)) return;
        JsonArray ids = new JsonArray();
        JsonObject test = object("group",group,"type",type,"timestampMs",System.currentTimeMillis(),"data",data);
        for (Map.Entry<String,JsonObject> w : watches.entrySet()) if (McpEventJournal.matches(test,w.getValue())) ids.add(w.getKey());
        data.add("watchIds",ids);
        journal.append(group,type,tick,data);
    }
    public void tick() {
        if (!active) return;
        Minecraft mc=Minecraft.getMinecraft();
        Object current=mc.getConnection()==null?null:mc.getConnection().getNetworkManager();
        if (world!=mc.world || connection!=current || epoch.isEmpty()) {
            if (world!=null) { emit("session","world_left",object("dimension",dimension)); journal.end(); }
            world=mc.world; connection=current;
            entities.clear(); deathObserved.clear(); blocks.clear(); watches.clear(); pending.clear();
            lastInventory=null; lastPlayer=null; lastGui=null; lastSlot=-1;
            nextInteractionId=0L; actionContext=null; lastContainerInteraction=null; lastContainerInteractionMs=0L;
            if (world!=null && mc.player!=null) {
                journal.begin(); epoch=journal.sessionId(); dimension=mc.player.dimension;
                playerUuid=mc.player.getUniqueID().toString(); integrated=mc.isIntegratedServerRunning();
                channel=mc.getConnection()==null?null:mc.getConnection().getNetworkManager().channel();
                emit("session","world_entered",object("dimension",dimension,"integratedServer",integrated,
                        "server",mc.getCurrentServerData()==null?"singleplayer":mc.getCurrentServerData().serverIP,
                        "playerId",mc.player.getEntityId(),"playerUuid",playerUuid,"watchReset",true));
            } else { epoch="no_world"; channel=null; integrated=false; }
        }
        tick++;
        Pending event;
        int drained=0;
        while (drained++<256 && (event=pending.poll())!=null) {
            if (!epoch.equals(event.epoch)) continue;
            try { if (event.packet instanceof JsonObject) {
                JsonObject d=(JsonObject)event.packet;
                if (relevant(d)) emit("entities","death_confirmed",d);
            } else packetOnClient(event.packet,event.outbound,event.actionContext); }
            catch (RuntimeException e) { emit("session","capture_error",object("source","packet","error",e.getClass().getSimpleName())); }
        }
        long lost=lostPackets.getAndSet(0);
        if (lost>0) emit("session","capture_gap",object("droppedEvents",lost,"reason","packet queue full"));
        if (mc.player!=null && world!=null) {
            captureEntities();
            captureInventory();
            if (tick%sampleTicks==0) {
                JsonObject p=player();
                if (!p.equals(lastPlayer)) { emit("player",lastPlayer==null?"baseline":"state_changed",copy(p)); lastPlayer=p; }
                if (enabled.contains("world")) captureBlocks();
            }
        }
        if (tick%sampleTicks==0 && enabled.contains("gui")) {
            JsonObject g=gui(false);
            if (!g.equals(lastGui)) {
                JsonObject guiEvent=copy(g);
                JsonObject correlation=recentContainerInteraction();
                if (correlation!=null) guiEvent.add("correlation",correlation);
                emit("gui",lastGui==null?"baseline":"changed",guiEvent); lastGui=g;
            }
        }
    }
    public JsonObject control(JsonObject p) {
        String op=p.has("operation")?string(p,"operation"):"groups";
        if (op.equals("groups")) {
            JsonArray groups=new JsonArray();
            String[] descriptions={"World/session boundaries, capture gaps", "Position, look, health and selected hotbar",
                    "Observed appearance, movement, health, death evidence, departure", "Full slots and deltas; gains are not necessarily pickups",
                    "Outgoing attack/use/container intents, container transaction/slot replies and pickup evidence", "Game/GUI key codes and mouse events; no typed text",
                    "GUI recognition snapshots, button and mouse interactions", "Player-centered block state deltas", "Received chat text"};
            for(int i=0;i<McpEventJournal.GROUPS.size();i++) groups.add(object("id",McpEventJournal.GROUPS.get(i),"description",descriptions[i],"enabled",enabled.contains(McpEventJournal.GROUPS.get(i))));
            return object("groups",groups,"sampleTicks",sampleTicks,"size",extent,"sessionId",journal.sessionId(),"watches",watchList(),
                    "transport","Cursor reads or waitMs long polling. client.py watch prints one JSON event per line.",
                    "evidence","Attack/use packets are intents. Death packets prove death, not killer. Integrated-server death includes damage source.",
                    "limits",object("events",12000,"bytes",16777216,"watches",32,"waitMs",25000,"snapshotBlocks",32768));
        }
        if (op.equals("configure")) {
            Set<String> next=p.has("groups")?McpEventJournal.strings(p,"groups"):enabled;
            if (!McpEventJournal.GROUPS.containsAll(next)) throw new IllegalArgumentException("Unknown group");
            int interval=integer(p,"sampleTicks",sampleTicks,1,200);
            int[] size=p.has("size")?size(p,32):extent;
            enabled=new LinkedHashSet<>(next); enabled.add("session"); sampleTicks=interval; extent=size;
            blocks.clear(); entities.clear(); lastInventory=null; lastPlayer=null; lastGui=null;
            emit("session","capture_configured",object("groups",enabled,"sampleTicks",sampleTicks,"size",extent));
            return control(object("operation","groups"));
        }
        if (op.equals("watch")) {
            if (world==null) throw new IllegalStateException("Join a world before creating a watch");
            String id=p.has("watchId")?string(p,"watchId"):UUID.randomUUID().toString();
            if (id.length()>128 || id.isEmpty()) throw new IllegalArgumentException("Invalid watchId");
            if (!watches.containsKey(id) && watches.size()>=32) throw new IllegalArgumentException("At most 32 watches");
            JsonObject filter=p.has("filter")?copy(p.getAsJsonObject("filter")):new JsonObject();
            Set<String> allowed=new HashSet<>(Arrays.asList("groups","types","entityIds","query","min","max","fromMs","toMs"));
            for(Map.Entry<String,JsonElement> f:filter.entrySet()) if(!allowed.contains(f.getKey())) throw new IllegalArgumentException("Unsupported watch filter: "+f.getKey());
            McpEventJournal.validateFilter(filter);
            watches.put(id,filter);
            return object("watchId",id,"sessionId",journal.sessionId(),"filter",filter,"baseline",journal.read(object("limit",1)).get("latestId"));
        }
        if (op.equals("unwatch")) return object("removed",watches.remove(string(p,"watchId"))!=null);
        if (op.equals("list_watches")) return object("watches",watchList(),"sessionId",journal.sessionId());
        throw new IllegalArgumentException("operation: groups/configure/watch/unwatch/list_watches/read");
    }
    private JsonArray watchList() {
        JsonArray a=new JsonArray();
        for(Map.Entry<String,JsonObject> e:watches.entrySet()) a.add(object("id",e.getKey(),"filter",e.getValue()));
        return a;
    }
    private boolean tracked(int id) {
        for(JsonObject w:watches.values()) if(McpEventJournal.strings(w,"entityIds").contains(String.valueOf(id))) return true;
        return false;
    }
    private void captureEntities() {
        if (!enabled.contains("entities")) return;
        Minecraft mc=Minecraft.getMinecraft();
        Map<Integer,JsonObject> next=new LinkedHashMap<>();
        for(Entity e:world.loadedEntityList) {
            if (next.size()>=1024) { emit("session","capture_gap",object("reason","entity sample limit 1024")); break; }
            if (!inBox(e,blockOrigin(mc.player),extent) && !tracked(e.getEntityId())) continue;
            JsonObject state=entity(e); int id=e.getEntityId();
            JsonObject old=entities.get(id); next.put(id,state);
            if (old==null || !old.get("uuid").equals(state.get("uuid"))) {
                deathObserved.remove(id); emit("entities","appeared",copy(state));
            } else {
                if (!Objects.equals(old.get("health"),state.get("health"))) emit("entities","health_changed",withBefore(state,old));
                if (tick%sampleTicks==0 && !old.get("pos").equals(state.get("pos"))) emit("entities","moved",withBefore(state,old));
            }
            if (e instanceof EntityLivingBase && ((EntityLivingBase)e).getHealth()<=0 && deathObserved.add(id))
                emit("entities","death_observed",object("entityId",id,"uuid",e.getUniqueID().toString(),"pos",pos(e),"evidence","client_health_zero","killerKnown",false));
        }
        for(Map.Entry<Integer,JsonObject> old:entities.entrySet()) if(!next.containsKey(old.getKey())) {
            Entity e=world.getEntityByID(old.getKey());
            JsonObject d=copy(old.getValue());
            d.addProperty("reason",e!=null?"left_observation_region":"no_longer_loaded");
            d.addProperty("deathPreviouslyObserved",deathObserved.contains(old.getKey()));
            emit("entities","departed",d);
            deathObserved.remove(old.getKey());
        }
        entities.clear(); entities.putAll(next);
    }
    private static JsonObject withBefore(JsonObject now,JsonObject before) {
        JsonObject d=copy(now); d.add("before",copy(before)); return d;
    }
    public static JsonObject item(ItemStack stack) {
        if(stack==null || stack.isEmpty()) return object("empty",true,"count",0);
        return object("empty",false,"registry",String.valueOf(stack.getItem().getRegistryName()),"name",stack.getDisplayName(),
                "count",stack.getCount(),"damage",stack.getItemDamage(),"maxDamage",stack.getMaxDamage(),
                "nbt",stack.hasTagCompound()?stack.getTagCompound().toString():null);
    }
    private JsonArray inventory() {
        Minecraft mc=Minecraft.getMinecraft(); JsonArray a=new JsonArray();
        if(mc.player!=null) for(int i=0;i<mc.player.inventory.getSizeInventory();i++) a.add(object("slot",i,"area",i<9?"hotbar":i<36?"main":i<40?"armor":"offhand","item",item(mc.player.inventory.getStackInSlot(i))));
        return a;
    }
    private void captureInventory() {
        Minecraft mc=Minecraft.getMinecraft();
        if (!enabled.contains("inventory")) return;
        JsonArray now=inventory();
        if (lastInventory==null) emit("inventory","baseline",object("slots",now));
        else for(int i=0;i<now.size();i++) if (!now.get(i).equals(lastInventory.get(i))) {
            JsonObject change=object("playerId",mc.player.getEntityId(),"slot",i,"before",lastInventory.get(i),
                    "after",now.get(i),"cause","unknown; correlate with interaction events");
            JsonObject correlation=recentContainerInteraction();
            if (correlation!=null) change.add("correlation",correlation);
            emit("inventory","slot_changed",change);
        }
        lastInventory=now;
        int slot=mc.player.inventory.currentItem;
        if (lastSlot!=slot) { emit("inventory","hotbar_selected",object("playerId",mc.player.getEntityId(),"before",lastSlot,"slot",slot,"item",item(mc.player.getHeldItemMainhand()))); lastSlot=slot; }
    }
    private void captureBlocks() {
        BlockPos origin=blockOrigin(Minecraft.getMinecraft().player);
        Map<String,String> next=new HashMap<>(); JsonArray changes=new JsonArray();
        for(int y=-extent[1]/2;y<extent[1]-extent[1]/2;y++) for(int z=-extent[2]/2;z<extent[2]-extent[2]/2;z++) for(int x=-extent[0]/2;x<extent[0]-extent[0]/2;x++) {
            BlockPos p=origin.add(x,y,z); String key=p.getX()+","+p.getY()+","+p.getZ(), state=block(p);
            next.put(key,state); String old=blocks.get(key);
            if (!Objects.equals(old,state)) changes.add(object("pos",new int[]{p.getX(),p.getY(),p.getZ()},"relative",new int[]{x,y,z},"before",old,"state",state));
        }
        if(changes.size()>0) emit("world",blocks.isEmpty()?"baseline":"blocks_changed",object("origin",xyz(origin),"size",extent,"changes",changes,"outsideRegion","not retained; use snapshot for current full coverage"));
        blocks.clear();blocks.putAll(next);
    }
    private String block(BlockPos p) {
        return block(world,p);
    }
    private static String block(World source,BlockPos p) {
        if(p.getY()<0 || p.getY()>=source.getHeight()) return "__out_of_world__";
        return source.isBlockLoaded(p,false)?source.getBlockState(p).toString():"__unloaded__";
    }
    private static double[] pos(Entity e) { return new double[]{e.posX,e.posY,e.posZ}; }
    private static BlockPos blockOrigin(Entity e) {
        int[] p=McpBlockVolume.origin(e.posX,e.posY,e.posZ);return new BlockPos(p[0],p[1],p[2]);
    }
    private static int[] xyz(BlockPos p) { return new int[]{p.getX(),p.getY(),p.getZ()}; }
    private static boolean inBox(Entity e,BlockPos o,int[] size) {
        return e.posX>=o.getX()-size[0]/2 && e.posX<o.getX()+size[0]-size[0]/2
            && e.posY>=o.getY()-size[1]/2 && e.posY<o.getY()+size[1]-size[1]/2
            && e.posZ>=o.getZ()-size[2]/2 && e.posZ<o.getZ()+size[2]-size[2]/2;
    }
    public static JsonObject entity(Entity e) {
        JsonObject d=object("entityId",e.getEntityId(),"uuid",e.getUniqueID().toString(),"name",e.getName(),
                "class",e.getClass().getSimpleName(),"registry",EntityList.getKey(e)==null?null:EntityList.getKey(e).toString(),
                "pos",pos(e),"yaw",e.rotationYaw,"pitch",e.rotationPitch,"alive",e.isEntityAlive(),"removed",e.isDead);
        if(e instanceof EntityLivingBase) { EntityLivingBase l=(EntityLivingBase)e; d.addProperty("health",l.getHealth());d.addProperty("maxHealth",l.getMaxHealth());d.addProperty("hurtTime",l.hurtTime);d.addProperty("deathTime",l.deathTime); }
        if(e instanceof EntityItem) d.add("item",item(((EntityItem)e).getItem()));
        return d;
    }
    private JsonObject player() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null) return object("present",false);
        JsonObject p=entity(mc.player); Vec3d v=mc.player.getLookVec();
        p.add("forward",GSON.toJsonTree(new double[]{v.x,v.y,v.z}));p.add("originBlock",GSON.toJsonTree(xyz(blockOrigin(mc.player))));
        p.addProperty("selectedHotbarSlot",mc.player.inventory.currentItem);p.add("mainHand",item(mc.player.getHeldItemMainhand()));
        p.add("offHand",item(mc.player.getHeldItemOffhand())); p.addProperty("food",mc.player.getFoodStats().getFoodLevel());
        p.addProperty("creative",mc.player.capabilities.isCreativeMode);p.addProperty("dimension",mc.player.dimension);
        return p;
    }
    private JsonObject gui(boolean includeNbt) {
        Minecraft mc=Minecraft.getMinecraft();
        GuiElementInspector.GuiSnapshot s=GuiElementInspector.captureCurrentSnapshot();
        if(s==null || mc.currentScreen==null) return object("open",false,"fingerprint","closed");
        JsonArray elements=new JsonArray();
        for(GuiElementInspector.GuiElementInfo e:s.getElements()) {
            if(elements.size()>=1024) break;
            elements.add(object("type",e.getType().name(),"path",e.getPath(),"text",e.getText(),"x",e.getX(),"y",e.getY(),"width",e.getWidth(),"height",e.getHeight(),"buttonId",e.getButtonId(),"slotIndex",e.getSlotIndex(),"controlType",e.getControlType(),"value",e.getValue(),"enabled",e.isEnabled(),"editable",e.isEditable(),"actions",e.getActions(),"choices",e.getChoices(),"visible",e.isVisible()));
        }
        JsonObject g=object("open",true,"screenClass",s.getScreenClassName(),"title",s.getTitle(),"width",mc.currentScreen.width,"height",mc.currentScreen.height,
                "coordinateSpace","scaled GUI pixels","elements",elements,"truncated",s.getElements().size()>elements.size());
        if(mc.player!=null && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
            JsonArray slots=new JsonArray();
            for(Slot slot:mc.player.openContainer.inventorySlots) {
                if(slots.size()>=512) break;
                JsonObject stack=item(slot.getStack()); if(!includeNbt) stack.remove("nbt");
                slots.add(object("slotId",slot.slotNumber,"inventoryIndex",slot.getSlotIndex(),"x",slot.xPos,"y",slot.yPos,"item",stack));
            }
            g.addProperty("windowId",mc.player.openContainer.windowId);g.add("slots",slots);
            g.addProperty("slotCoordinates","container-relative pixels; element rectangles use scaled screen pixels");
            g.addProperty("slotsTruncated",mc.player.openContainer.inventorySlots.size()>slots.size());
            g.add("cursorItem",item(mc.player.inventory.getItemStack()));
        }
        g.addProperty("fingerprint", Integer.toHexString(g.toString().hashCode()));
        return g;
    }
    private static int[] size(JsonObject p,int max) {
        double[] v=McpEventJournal.vector(p,"size");int[] result=new int[3];
        for(int i=0;i<3;i++) { if(v[i]!=Math.rint(v[i]) || v[i]<1 || v[i]>max) throw new IllegalArgumentException("size axes must be integers 1.."+max);result[i]=(int)v[i]; }
        return result;
    }
    public JsonObject snapshot(JsonObject p) {
        Minecraft mc=Minecraft.getMinecraft();
        Set<String> groups=McpEventJournal.strings(p,"groups");
        if(groups.isEmpty()) groups=new LinkedHashSet<>(Arrays.asList("player","entities","inventory","gui","world"));
        if(!McpEventJournal.GROUPS.containsAll(groups)) throw new IllegalArgumentException("Unknown group");
        int[] size=p.has("size")?size(p,32):extent;
        JsonObject result=object("sessionId",journal.sessionId(),"timestampMs",System.currentTimeMillis(),"tick",tick,"inWorld",mc.player!=null && mc.world!=null,
                "coordinates","World axes: +X east, +Y up, +Z south. Relative offsets from floor(player position); bounds inclusive. Forward vector carries view direction.",
                "server",mc.getCurrentServerData()==null?(mc.world==null?"offline":"singleplayer"):mc.getCurrentServerData().serverIP,
                "eventCursor",journal.read(object("limit",1)).get("latestId"));
        if(groups.contains("gui")) result.add("gui",gui(bool(p,"includeNbt",false)));
        if(mc.player==null || mc.world==null) return result;
        BlockPos origin=blockOrigin(mc.player);
        if(p.has("origin")) { double[] v=McpEventJournal.vector(p,"origin"); for(double n:v) if(n < -30000000 || n>30000000) throw new IllegalArgumentException("origin outside world bounds"); origin=new BlockPos(v[0],v[1],v[2]); }
        result.add("origin",GSON.toJsonTree(xyz(origin)));result.add("size",GSON.toJsonTree(size));
        if(groups.contains("player")) result.add("player",player());
        if(groups.contains("inventory")) {
            JsonArray currentInventory=inventory();
            result.add("inventory",currentInventory);
            result.addProperty("inventoryFingerprint",Integer.toHexString(currentInventory.toString().hashCode()));
        }
        if(groups.contains("entities")) {
            Set<String> ids=McpEventJournal.strings(p,"entityIds"); JsonArray list=new JsonArray();int total=0;
            for(Entity e:mc.world.loadedEntityList) if(ids.isEmpty()?inBox(e,origin,size):ids.contains(String.valueOf(e.getEntityId()))) {
                total++; if(list.size()<1024) { JsonObject d=entity(e);d.add("relative",GSON.toJsonTree(new double[]{e.posX-origin.getX(),e.posY-origin.getY(),e.posZ-origin.getZ()}));list.add(d); }
            }
            result.add("entities",list);result.addProperty("entityCount",total);result.addProperty("entitiesTruncated",total>list.size());
        }
        if(groups.contains("world")) {
            String[] cells=new String[size[0]*size[1]*size[2]];int n=0;
            int[] min={-size[0]/2,-size[1]/2,-size[2]/2};
            for(int y=0;y<size[1];y++) for(int z=0;z<size[2];z++) for(int x=0;x<size[0];x++) cells[n++]=block(mc.world,origin.add(x+min[0],y+min[1],z+min[2]));
            result.add("world",McpBlockVolume.pack(cells,size[0],size[1],size[2],min));
        }
        return result;
    }
    /** Netty hook: bounded enqueue only; no entity/world access or user code on the network thread. */
    public void packet(Channel source,Object packet,boolean outbound) {
        if(!active || source!=channel || !(packet instanceof SPacketEntityStatus || packet instanceof SPacketCollectItem
                || packet instanceof SPacketConfirmTransaction || packet instanceof SPacketSetSlot
                || packet instanceof CPacketUseEntity || packet instanceof CPacketPlayerTryUseItem || packet instanceof CPacketPlayerTryUseItemOnBlock
                || packet instanceof CPacketClickWindow || packet instanceof CPacketHeldItemChange)) return;
        if(!pending.offer(new Pending(packet,outbound,epoch,currentActionContext()))) lostPackets.incrementAndGet();
    }
    private void packetOnClient(Object packet,boolean outbound,JsonObject actionMarker) {
        Minecraft mc=Minecraft.getMinecraft(); if(mc.player==null || world==null) return;
        JsonObject d=object("evidence",outbound?"outgoing_packet_intent":"received_server_packet","playerId",mc.player.getEntityId(),"pos",pos(mc.player));
        if (actionMarker != null && !(packet instanceof SPacketEntityStatus)) d.add("actionContext",copy(actionMarker));
        if(packet instanceof SPacketConfirmTransaction) {
            SPacketConfirmTransaction confirmation=(SPacketConfirmTransaction)packet;
            d.remove("playerId"); d.remove("pos");
            d.addProperty("windowId",confirmation.getWindowId());
            d.addProperty("actionNumber",confirmation.getActionNumber());
            d.addProperty("accepted",confirmation.wasAccepted());
            JsonObject correlation=recentContainerInteraction();
            if(correlation!=null && correlation.get("windowId").getAsInt()==confirmation.getWindowId())
                d.add("correlation",correlation);
            emit("interaction","container_transaction_confirmed",d);
        } else if(packet instanceof SPacketSetSlot) {
            SPacketSetSlot update=(SPacketSetSlot)packet;
            d.remove("playerId"); d.remove("pos");
            d.addProperty("windowId",update.getWindowId()); d.addProperty("slot",update.getSlot());
            d.add("item",item(update.getStack()));
            JsonObject correlation=recentContainerInteraction();
            if(correlation!=null && correlation.get("windowId").getAsInt()==update.getWindowId())
                d.add("correlation",correlation);
            emit("interaction","container_slot_update",d);
        } else if(packet instanceof SPacketEntityStatus) {
            SPacketEntityStatus s=(SPacketEntityStatus)packet;
            if(s.getOpCode()!=3 && s.getOpCode()!=2) return;
            PacketBuffer buf=new PacketBuffer(Unpooled.buffer()); int id;
            try { s.writePacketData(buf);id=buf.readInt(); } catch(Exception e) { return; } finally { buf.release(); }
            d.addProperty("entityId",id);d.addProperty("killerKnown",false);
            d.remove("playerId");
            Entity e=world.getEntityByID(id); JsonObject cached=entities.get(id);
            if(e!=null) { d.add("entity",entity(e));d.add("pos",GSON.toJsonTree(pos(e))); }
            else if(cached!=null) { d.add("entity",cached);d.add("pos",cached.get("pos")); }
            else d.remove("pos");
            if (!relevant(d)) return;
            if(s.getOpCode()==3) deathObserved.add(id);
            emit("entities",s.getOpCode()==3?"death_confirmed":"hurt_confirmed",d);
        } else if(packet instanceof SPacketCollectItem) {
            SPacketCollectItem s=(SPacketCollectItem)packet;
            if(s.getEntityID()!=mc.player.getEntityId() && !entities.containsKey(s.getCollectedItemEntityID()) && !tracked(s.getEntityID())) return;
            d.remove("playerId");
            d.addProperty("collectorId",s.getEntityID());d.addProperty("itemEntityId",s.getCollectedItemEntityID());d.addProperty("count",s.getAmount());
            Entity e=world.getEntityByID(s.getCollectedItemEntityID());JsonObject cached=entities.get(s.getCollectedItemEntityID());
            if(e instanceof EntityItem) d.add("item",item(((EntityItem)e).getItem())); else if(cached!=null && cached.has("item")) d.add("item",cached.get("item"));
            emit("interaction","pickup_confirmed",d);
        } else {
            d.addProperty("selectedHotbarSlot",mc.player.inventory.currentItem);d.add("mainHand",item(mc.player.getHeldItemMainhand()));
            d.addProperty("stateTiming","client state when observation queue drained; packet fields are exact");
            if(packet instanceof CPacketUseEntity) {
                CPacketUseEntity p=(CPacketUseEntity)packet; Entity e=p.getEntityFromWorld(world);
                PacketBuffer buf=new PacketBuffer(Unpooled.buffer());
                try { p.writePacketData(buf);d.addProperty("targetId",buf.readVarInt()); } catch(Exception ignored) {} finally {buf.release();}
                d.addProperty("action",p.getAction().name()); if(e!=null)d.add("target",entity(e));
                emit("interaction","entity_interaction_intent",d);
            } else if(packet instanceof CPacketClickWindow) {
                CPacketClickWindow p=(CPacketClickWindow)packet;
                JsonObject interaction=object("interactionId",++nextInteractionId,"windowId",p.getWindowId(),
                        "slot",p.getSlotId(),"button",p.getUsedButton(),"clickType",p.getClickType().name());
                lastContainerInteraction=copy(interaction); lastContainerInteractionMs=System.currentTimeMillis();
                d.add("interaction",interaction);
                d.addProperty("windowId",p.getWindowId());d.addProperty("slot",p.getSlotId());d.addProperty("button",p.getUsedButton());d.addProperty("clickType",p.getClickType().name());
                d.add("clickedItem",item(p.getClickedItem()));emit("interaction","container_click_intent",d);
            } else if(packet instanceof CPacketHeldItemChange) {
                d.addProperty("slot",((CPacketHeldItemChange)packet).getSlotId());emit("interaction","hotbar_select_intent",d);
            } else if(packet instanceof CPacketPlayerTryUseItemOnBlock) {
                CPacketPlayerTryUseItemOnBlock p=(CPacketPlayerTryUseItemOnBlock)packet;
                d.add("blockPos",GSON.toJsonTree(xyz(p.getPos())));d.addProperty("face",p.getDirection().name());d.addProperty("hand",p.getHand().name());emit("interaction","use_block_intent",d);
            } else { d.addProperty("hand",((CPacketPlayerTryUseItem)packet).getHand().name());emit("interaction","use_item_intent",d); }
        }
    }

    /** Best-effort association between an outgoing container click and a later client state delta. */
    private JsonObject recentContainerInteraction() {
        JsonObject interaction=lastContainerInteraction;
        long age=System.currentTimeMillis()-lastContainerInteractionMs;
        if (interaction==null || age<0L || age>5000L) return null;
        JsonObject result=copy(interaction); result.addProperty("ageMs",age);
        result.addProperty("evidence","client_side_temporal_correlation");
        return result;
    }

    /** Called by the path runner immediately before an action is dispatched. */
    public static void markActionContext(String sequenceName, int stepIndex, int actionIndex,
            String actionType, String actionUuid) {
        JsonObject marker=object("sequence",sequenceName==null?"":sequenceName,"stepIndex",stepIndex,
                "actionIndex",actionIndex,"actionType",actionType==null?"":actionType,
                "actionUuid",actionUuid==null?"":actionUuid,"markedAtMs",System.currentTimeMillis());
        INSTANCE.actionContext=marker;
    }

    private JsonObject currentActionContext() {
        JsonObject marker=actionContext;
        if (marker==null || !marker.has("markedAtMs")) return null;
        long age=System.currentTimeMillis()-marker.get("markedAtMs").getAsLong();
        // The marker is published through a volatile reference and never
        // mutated after publication. Do not deep-copy on Netty threads; the
        // client-thread event consumer copies it before exposing it to MCP.
        return age<0L || age>5000L ? null : marker;
    }
    private boolean relevant(JsonObject d) {
        if(d.has("entityId") && tracked(d.get("entityId").getAsInt())) return true;
        if(!d.has("pos") || Minecraft.getMinecraft().player==null) return false;
        double[] v=McpEventJournal.vector(d,"pos");BlockPos o=blockOrigin(Minecraft.getMinecraft().player);int[] origin=xyz(o);
        for(int i=0;i<3;i++) if(v[i]<origin[i]-extent[i]/2 || v[i]>=origin[i]+extent[i]-extent[i]/2) return false;
        return true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void death(LivingDeathEvent event) {
        String observedEpoch=epoch;
        if(!active || !integrated || event.getEntityLiving().world.isRemote || event.getEntityLiving().dimension!=dimension) return;
        Entity source=event.getSource().getTrueSource();
        JsonObject d=entity(event.getEntityLiving());
        d.addProperty("evidence","integrated_server_living_death");d.addProperty("damageType",event.getSource().getDamageType());
        d.addProperty("killerKnown",source!=null);
        if(source!=null) { d.addProperty("attackerId",source.getEntityId());d.addProperty("attackerUuid",source.getUniqueID().toString());d.addProperty("killedByLocalPlayer",source.getUniqueID().toString().equals(playerUuid));
            if(source instanceof EntityLivingBase)d.add("attackerMainHand",item(((EntityLivingBase)source).getHeldItemMainhand())); }
        if(!pending.offer(new Pending(d,false,observedEpoch))) lostPackets.incrementAndGet();
    }
    @SubscribeEvent public void attack(AttackEntityEvent e) {
        if(active && e.getEntityPlayer().world.isRemote) emit("interaction","attack_attempt",object(
                "evidence","client_attack_event","attackerId",e.getEntityPlayer().getEntityId(),"targetId",e.getTarget().getEntityId(),
                "targetUuid",e.getTarget().getUniqueID().toString(),"pos",pos(e.getTarget()),"mainHand",item(e.getEntityPlayer().getHeldItemMainhand()),
                "actionContext",currentActionContext()));
    }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent e) { key("game"); }
    @SubscribeEvent public void guiKey(GuiScreenEvent.KeyboardInputEvent.Pre e) { key("gui"); }
    private void key(String scope) {
        if(active && Keyboard.isCreated()) emit("input","key",object("scope",scope,"keyCode",Keyboard.getEventKey(),"keyName",Keyboard.getKeyName(Keyboard.getEventKey()),"pressed",Keyboard.getEventKeyState(),"repeat",Keyboard.isRepeatEvent()));
    }
    @SubscribeEvent public void mouse(InputEvent.MouseInputEvent e) { mouse("game"); }
    @SubscribeEvent public void guiMouse(GuiScreenEvent.MouseInputEvent.Pre e) { mouse("gui"); }
    private void mouse(String scope) {
        if(active && Mouse.isCreated() && (Mouse.getEventButton()>=0 || Mouse.getEventDWheel()!=0)) emit("input","mouse",object("scope",scope,"button",Mouse.getEventButton(),"pressed",Mouse.getEventButtonState(),"wheel",Mouse.getEventDWheel(),"x",Mouse.getEventX(),"y",Mouse.getEventY(),"coordinates","display pixels, origin bottom-left"));
    }
    @SubscribeEvent public void guiAction(GuiScreenEvent.ActionPerformedEvent.Post e) {
        if(active) emit("gui","button_activated",object("screenClass",e.getGui().getClass().getName(),"buttonId",e.getButton().id,"text",e.getButton().displayString));
    }
    @SubscribeEvent public void chat(ClientChatReceivedEvent e) {
        if(active) emit("chat","received",object("text",e.getMessage().getUnformattedText(),"type",e.getType().name()));
    }
}
