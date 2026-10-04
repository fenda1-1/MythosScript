package com.mythos.mythosScriptMod.mcp;

import com.google.gson.*;
import com.mythos.mythosScriptMod.handlers.EmbeddedNavigationHandler;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.FreecamFeatureHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/** Semantic terrain stream and leased vanilla movement. No framebuffer, teleport or packet replay. */
public final class McpWorldView {
    private static final McpWorldView INSTANCE=new McpWorldView();
    private static final WorldInputLease input=new WorldInputLease();
    private static Object world,player;
    private static String token="";
    private static JsonObject terrain;
    private static final McpWorldTerrain terrainStream=new McpWorldTerrain();
    private static int[] origin;
    private static long terrainRevision, nextTerrain, lastPoll;
    private static boolean registered;
    private static int extent=32;
    private static float cameraYaw=135;
    private static float cameraPitch=0;
    private static final WorldInputLease actionLease=new WorldInputLease();
    private static String action="", actionStatus="";
    private static final Map<Integer,UUID> targets=new LinkedHashMap<>();
    private static BlockPos destination;
    private static boolean ownsAura, ownsNavigation;
    private static long actionStarted, nextRepath;
    private static int chaseTarget=-1;
    private static BlockPos chaseGoal;
    private static net.minecraft.util.math.Vec3d progressPosition;
    private static float progressHealth;
    private static long progressAt;
    private static volatile boolean accepting=true;
    private McpWorldView() {}
    public static void register() { if(!registered){MinecraftForge.EVENT_BUS.register(INSTANCE);registered=true;} }
    public static void setAccepting(boolean value) { accepting=value; }

    public static void tick() {
        Minecraft mc=Minecraft.getMinecraft();
        sync(mc);
        if(!action.isEmpty()) {
            if(!actionLease.active(System.nanoTime()) || !basicReason(mc).isEmpty()
                    || !PathSequenceEventListener.getActiveProgressSnapshots().isEmpty()) stopAction("操作已停止：失焦、连接超时或其他脚本接管");
            else try { tickAction(mc); } catch(Exception e) { stopAction("操作失败："+e.getMessage()); }
        }
        if(input.active(System.nanoTime())) {if(!accepting || !blockReason(mc).isEmpty())releaseInput(mc);}
        else clearMovement(mc);
        // A hidden/closed workbench must not retain a terrain cache indefinitely.
        if(System.nanoTime()-lastPoll>3_000_000_000L) { terrain=null;origin=null;terrainStream.clear(); }
        else terrainStream.tick();
    }

    private static void sync(Minecraft mc) {
        if(world!=mc.world || player!=mc.player) {
            stopAction("");
            releaseInput(mc);world=mc.world;player=mc.player;
            token=UUID.randomUUID().toString();terrain=null;origin=null;nextTerrain=0;terrainRevision++;terrainStream.clear();
        }
    }
    private static String blockReason(Minecraft mc) {
        String basic=basicReason(mc);if(!basic.isEmpty())return basic;
        if(!action.isEmpty())return "鼠标任务执行中；WASD 或 Esc 可中断";
        if(EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating() || !PathSequenceEventListener.getActiveProgressSnapshots().isEmpty())
            return "请先停止路径序列和自动寻路，再接管移动";
        return "";
    }
    private static String basicReason(Minecraft mc) {
        if(!accepting)return "MCP 服务已暂停";
        if(mc.world==null || mc.player==null)return "实例尚未进入世界";
        if(!mc.player.isEntityAlive())return "角色已死亡";
        if(mc.currentScreen!=null)return "请先关闭游戏内界面，再接管移动";
        if(FreecamFeatureHandler.isBody(mc.player))return "请先关闭自由相机";
        return "";
    }

    // Only clear input that we actually wrote; ordinary gameplay and script controls are untouched.
    private static MovementInput overridden;
    private static net.minecraft.client.settings.KeyBinding sprintKey;
    private static net.minecraft.client.entity.EntityPlayerSP sprintPlayer;
    private static void clearMovement(Minecraft mc) {
        if(overridden!=null) {
            overridden.moveForward=0;overridden.moveStrafe=0;overridden.jump=false;overridden.sneak=false;
            overridden.forwardKeyDown=false;overridden.backKeyDown=false;overridden.leftKeyDown=false;overridden.rightKeyDown=false;
            overridden=null;
        }
        if(sprintKey!=null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(sprintKey.getKeyCode(),org.lwjgl.input.Keyboard.isKeyDown(sprintKey.getKeyCode()));
            sprintKey=null;
        }
        if(sprintPlayer!=null){sprintPlayer.setSprinting(false);sprintPlayer=null;}
    }
    private static void releaseInput(Minecraft mc) { input.clear();clearMovement(mc); }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onInput(InputUpdateEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(event.getEntityPlayer()!=mc.player || !input.active(System.nanoTime()))return;
        if(!blockReason(mc).isEmpty()){releaseInput(mc);return;}
        int mask=input.keys(System.nanoTime());
        MovementInput movement=event.getMovementInput();
        movement.moveForward=WorldInputLease.forward(mask);movement.moveStrafe=WorldInputLease.strafe(mask);
        movement.forwardKeyDown=(mask&1)!=0;movement.backKeyDown=(mask&2)!=0;
        movement.leftKeyDown=(mask&4)!=0;movement.rightKeyDown=(mask&8)!=0;
        movement.jump=(mask&16)!=0;movement.sneak=(mask&32)!=0;
        sprintKey=mc.gameSettings.keyBindSprint;sprintPlayer=mc.player;
        boolean sprint=(mask&64)!=0 && !movement.sneak;
        // Let vanilla apply food, item-use, blindness, forward-motion and collision rules.
        net.minecraft.client.settings.KeyBinding.setKeyBindState(sprintKey.getKeyCode(),sprint);
        if(!sprint)mc.player.setSprinting(false);
        // Projection: screen X = X-Z; screen Y = (X+Z)/2-Y. W points up the screen.
        mc.player.rotationYaw=cameraYaw;mc.player.rotationPitch=cameraPitch;
        overridden=movement;
    }

    public static JsonObject call(JsonObject p) {
        Minecraft mc=Minecraft.getMinecraft();sync(mc);
        String op=p.has("operation")?string(p,"operation"):"poll";
        if(!Arrays.asList("poll","acquire","release","action","stop").contains(op))throw new IllegalArgumentException("Unknown worldview operation");
        String owner=p.has("owner")?string(p,"owner"):"";
        String expected=p.has("expectedToken")?string(p,"expectedToken"):"";
        long serial=p.has("sequence")?p.get("sequence").getAsLong():0;
        long now=System.nanoTime();
        long deadline=p.has("deadlineMs")?p.get("deadlineMs").getAsLong():0;
        boolean timely=deadline>=System.currentTimeMillis() && deadline<=System.currentTimeMillis()+2000;
        if(op.equals("stop")) {
            if(token.equals(expected) && actionLease.release(owner,serial,now))stopAction("已停止");
        }
        if(op.equals("action")) {
            if(!token.equals(expected) || !timely)throw new IllegalArgumentException("操作已过期或世界已切换");
            if(!basicReason(mc).isEmpty())throw new IllegalStateException(basicReason(mc));
            if(!PathSequenceEventListener.getActiveProgressSnapshots().isEmpty())throw new IllegalStateException("请先停止其他路径脚本");
            if(action.isEmpty() && (EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating() || KillAuraHandler.enabled || KillAuraHandler.isMcpRuntimeActive()))
                throw new IllegalStateException("请先停止其他寻路或杀戮光环任务");
            if(input.active(now) && !input.owned(owner,now))throw new IllegalStateException("移动已由其他窗口控制");
            if(actionLease.active(now)?!actionLease.update(owner,serial,0,now):!actionLease.acquire(owner,serial,now))
                throw new IllegalStateException("鼠标操作已被其他窗口占用");
            try { startAction(mc,p); } catch(RuntimeException e) {stopAction("操作失败："+e.getMessage());throw e;}
        } else if(timely && token.equals(expected) && p.has("keepAction")) actionLease.update(owner,serial,0,now);
        String reason=blockReason(mc);
        if(!reason.isEmpty())releaseInput(mc);
        boolean accepted=false;
        if(op.equals("release")) {
            if(token.equals(expected) && input.release(owner,serial,now)){clearMovement(mc);accepted=true;}
            return object("token",token,"controlled",false,"accepted",accepted);
        }
        if(op.equals("acquire") || p.has("keys")) {
            // Reject a command which waited behind a stalled client thread; never revive stale keys.
            long wall=System.currentTimeMillis();
            if(!token.equals(expected))reason="世界已切换，请重新接管";
            else if(deadline<wall || deadline>wall+2000)reason="控制请求已过期，请重新接管";
            else if(reason.isEmpty()) {
                accepted=op.equals("acquire")?input.acquire(owner,serial,now):input.update(owner,serial,integer(p,"keys",0,0,127),now);
                if(accepted && p.has("cameraQuarter")){cameraYaw=135f-90f*integer(p,"cameraQuarter",0,0,3);cameraPitch=0f;}
                // 桌面端第一/第三人称鼠标视角：[rotationYaw,rotationPitch]，覆盖离散的 cameraQuarter。
                if(accepted && p.has("look") && p.get("look").isJsonArray()) {
                    JsonArray look=p.getAsJsonArray("look");
                    if(look.size()>=2){cameraYaw=look.get(0).getAsFloat();cameraPitch=Math.max(-90f,Math.min(90f,look.get(1).getAsFloat()));}
                }
                if(!accepted)reason="控制租约已失效或已被占用，请重新接管";
            }
        }
        JsonObject result=object("token",token,"inWorld",mc.world!=null && mc.player!=null,
                "controlled",input.owned(owner,now),"canControl",reason.isEmpty(),"reason",reason,"accepted",accepted,
                "timestampMs",System.currentTimeMillis());
        if(mc.world==null || mc.player==null)return result;
        result.addProperty("actionStatus",actionStatus);
        result.addProperty("actionActive",!action.isEmpty() && actionLease.owned(owner,now));
        result.add("selected",GSON.toJsonTree(targets.keySet()));
        JsonArray route=new JsonArray();
        if(ownsNavigation || ownsAura) {
            com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor current=BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getCurrent();
            if(current!=null) {
                java.util.List<com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos> points=current.getPath().positions();
                for(int i=Math.max(0,current.getPosition());i<points.size() && route.size()<256;i++) {
                    BlockPos b=points.get(i);route.add(GSON.toJsonTree(new double[]{b.getX()+.5,b.getY()+.05,b.getZ()+.5}));
                }
            }
        }
        result.add("route",route);
        int requested=integer(p,"extent",32,16,128);
        if(requested!=extent){extent=requested;terrain=null;origin=null;terrainStream.clear();}
        lastPoll=now;
        int[] center={grid(mc.player.posX),grid(mc.player.posY),grid(mc.player.posZ)};
        if(bool(p,"terrainAsync",false)) {
            McpWorldTerrain.Snapshot sampled=terrainStream.request(mc.world,center,extent);
            if(sampled!=null && sampled.world!=terrain) {
                terrain=sampled.world; origin=sampled.origin; terrainRevision++;
            }
        } else if(terrain==null || !Arrays.equals(origin,center) || now>=nextTerrain) {
            JsonObject sampled=McpObservation.INSTANCE.worldViewSnapshot(object("groups",Arrays.asList("world"),"origin",center,"size",new int[]{extent,24,extent}));
            JsonObject next=sampled.getAsJsonObject("world");
            if(terrain==null || !Arrays.equals(origin,center) || !terrain.equals(next)){terrain=next;origin=center;terrainRevision++;}
            nextTerrain=now+1_000_000_000L;
        }
        result.addProperty("terrainRevision",terrainRevision);
        result.add("origin",GSON.toJsonTree(origin==null?center:origin));result.add("size",GSON.toJsonTree(new int[]{extent,24,extent}));
        if(terrain!=null && (!p.has("terrainRevision") || p.get("terrainRevision").getAsLong()!=terrainRevision)) result.add("world",terrain);
        result.add("player",actor(mc.player));
        JsonArray entities=new JsonArray();
        for(Entity entity:mc.world.loadedEntityList) {
            if(entity!=mc.player && !entity.isDead && entity.getDistanceSq(mc.player)<=24*24) {
                if(entities.size()>=128)break;
                entities.add(actor(entity));
            }
        }
        result.add("entities",entities);
        return result;
    }
    private static void stopRuntime() {
        if(ownsAura){KillAuraHandler.INSTANCE.stopAreaHuntAction();KillAuraHandler.endMcpRuntime();ownsAura=false;}
        if(ownsNavigation){EmbeddedNavigationHandler.INSTANCE.stop();ownsNavigation=false;}
        targets.clear();destination=null;action="";chaseTarget=-1;chaseGoal=null;
    }
    private static void stopAction(String status) {stopRuntime();actionLease.clear();actionStatus=status;}
    private static void startAction(Minecraft mc,JsonObject p) {
        String kind=string(p,"kind");
        if(!Arrays.asList("goto","interact","attack","hunt").contains(kind))throw new IllegalArgumentException("无效鼠标操作");
        Map<Integer,UUID> selected=new LinkedHashMap<>();
        BlockPos goal=null;
        if(kind.equals("goto")) {
            double[] pos=McpEventJournal.vector(p,"position");
            for(double v:pos)if(!Double.isFinite(v) || Math.abs(v)>30000000)throw new IllegalArgumentException("目标坐标无效");
            goal=new BlockPos(pos[0],pos[1],pos[2]);
            if(goal.distanceSq(mc.player.getPosition())>48*48 || !mc.world.isBlockLoaded(goal))throw new IllegalArgumentException("目标超出已加载交互范围");
            if(!mc.world.getBlockState(goal.down()).getMaterial().blocksMovement()
                    || !mc.world.getCollisionBoxes(mc.player,mc.player.getEntityBoundingBox().offset(goal.getX()+.5-mc.player.posX,goal.getY()-mc.player.posY,goal.getZ()+.5-mc.player.posZ)).isEmpty())
                throw new IllegalArgumentException("这里没有可站立的表面，请选择其他方块顶部");
        } else {
            JsonArray list=p.getAsJsonArray("targets");
            if(list==null || list.size()<1 || list.size()>128 || (!kind.equals("hunt") && list.size()!=1))throw new IllegalArgumentException("目标数量无效");
            for(JsonElement item:list) {
                JsonObject target=item.getAsJsonObject();int id=target.get("id").getAsInt();
                Entity entity=mc.world.getEntityByID(id);UUID uuid=UUID.fromString(string(target,"uuid"));
                if(!(entity instanceof EntityLivingBase) || entity==mc.player || !entity.isEntityAlive()
                        || !uuid.equals(entity.getUniqueID()) || entity.getDistanceSq(mc.player)>48*48)throw new IllegalArgumentException("目标已离开、死亡或已被替换");
                selected.put(id,uuid);
            }
        }
        stopRuntime();releaseInput(mc);
        action=kind;targets.putAll(selected);destination=goal;
        actionStarted=System.nanoTime();nextRepath=0;
        progressAt=actionStarted;progressPosition=mc.player.getPositionVector();progressHealth=Float.MAX_VALUE;
        if(kind.equals("hunt")) {
            KillAuraHandler.beginMcpRuntime(object("attackMode","NORMAL","huntEnabled",false,"huntMode","OFF",
                    "attackRange",3.0,"nearbyEntityScanRange",4.0,"requireLineOfSight",true,"throughWallAttack",false,
                    "enableNoCollision",false,"enableAntiKnockback",false,"aimOnlyMode",false,"onlyWeapon",false,
                    "onlyAttackWhenLookingAtTarget",false,"relockOnlyWhenNoCrosshairTarget",false,
                    "enableNameWhitelist",false,"enableNameBlacklist",false,"huntPickupItemsEnabled",false,
                    "targetsPerAttack",1,"ignoreInvisible",false),false,0);
            ownsAura=true;
        }
        if(goal!=null) {
            ownsNavigation=true;
            if(!EmbeddedNavigationHandler.INSTANCE.startGoto(goal.getX(),goal.getY(),goal.getZ(),true))throw new IllegalStateException("Baritone 无法启动寻路");
        }
        actionStatus=kind.equals("goto")?"正在计算路径…":kind.equals("interact")?"接近目标并交互":kind.equals("attack")?"接近目标并攻击一次":"持续攻击 · 已选择 "+targets.size()+" 个目标";
    }
    private static void tickAction(Minecraft mc) {
        long now=System.nanoTime();
        float health=0;for(Integer id:targets.keySet()){Entity e=mc.world.getEntityByID(id);if(e instanceof EntityLivingBase)health+=((EntityLivingBase)e).getHealth();}
        if(progressPosition.squareDistanceTo(mc.player.getPositionVector())>.25 || health<progressHealth-.01f) {
            progressAt=now;progressPosition=mc.player.getPositionVector();progressHealth=health;
        }
        if(now-progressAt>30_000_000_000L){stopAction("30 秒没有移动或造成伤害，目标可能不可达或免疫；已停止");return;}
        if(action.equals("goto")) {
            if(mc.player.getDistanceSq(destination.getX()+.5,destination.getY(),destination.getZ()+.5)<1){stopAction("已到达目的地");return;}
            if(now-actionStarted>3_000_000_000L && !EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating())stopAction("未能到达：没有可用路径或寻路已中断");
            else actionStatus="Baritone 寻路中 · 右键可更换目的地";
            return;
        }
        targets.entrySet().removeIf(entry->{Entity e=mc.world.getEntityByID(entry.getKey());return e==null || !e.isEntityAlive() || !entry.getValue().equals(e.getUniqueID());});
        if(targets.isEmpty()){stopAction("目标已死亡或离开加载范围，任务结束");return;}
        EntityLivingBase target=targets.containsKey(chaseTarget)?(EntityLivingBase)mc.world.getEntityByID(chaseTarget):null;double nearest=target==null?Double.MAX_VALUE:target.getDistanceSq(mc.player);
        if(target==null)for(Integer id:targets.keySet()) {EntityLivingBase e=(EntityLivingBase)mc.world.getEntityByID(id);double d=e.getDistanceSq(mc.player);if(d<nearest){target=e;nearest=d;}}
        chaseTarget=target.getEntityId();
        if(nearest>64*64){stopAction("目标超出追踪范围");return;}
        if(nearest>2.8*2.8 || !mc.player.canEntityBeSeen(target)) {
            if(now>=nextRepath && (chaseGoal==null || !chaseGoal.equals(target.getPosition()) || !EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating())) {
                ownsNavigation=true;
                chaseGoal=target.getPosition();
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(
                    new com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalNear(target.getPosition(),1));
                nextRepath=now+1_000_000_000L;
            }
            actionStatus="接近 "+target.getName()+" · 剩余 "+targets.size()+" 个目标";return;
        }
        if(ownsNavigation){EmbeddedNavigationHandler.INSTANCE.stop();ownsNavigation=false;}
        if(action.equals("hunt")) {
            KillAuraHandler.INSTANCE.tickAreaHunt(mc.player,new KillAuraHandler.AreaHuntOptions(mc.player.posX,mc.player.posY,mc.player.posZ,4,4,4,
                    e->targets.containsKey(e.getEntityId()) && targets.get(e.getEntityId()).equals(e.getUniqueID()),true));
            actionStatus="持续攻击 · 剩余 "+targets.size()+" 个目标";
        } else {
            double dx=target.posX-mc.player.posX,dz=target.posZ-mc.player.posZ,dy=target.posY+target.getEyeHeight()-mc.player.posY-mc.player.getEyeHeight();
            mc.player.rotationYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
            mc.player.rotationPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
            if(action.equals("attack")) {
                if(mc.player.getCooledAttackStrength(0)<.92f)return;
                mc.playerController.attackEntity(mc.player,target);mc.player.swingArm(EnumHand.MAIN_HAND);stopAction("已攻击一次");
            } else {
                net.minecraft.util.EnumActionResult result=mc.playerController.interactWithEntity(mc.player,target,EnumHand.MAIN_HAND);
                if(result==net.minecraft.util.EnumActionResult.PASS) result=mc.playerController.interactWithEntity(mc.player,target,EnumHand.OFF_HAND);
                stopAction(result==net.minecraft.util.EnumActionResult.SUCCESS?"已与目标交互":"已发送交互，目标未接受或没有可用交互");
            }
        }
    }
    private static int grid(double coordinate) {return (int)Math.floor(coordinate/4)*4;}
    private static JsonObject actor(Entity entity) {
        JsonObject result=object("id",entity.getEntityId(),"uuid",entity.getUniqueID().toString(),"name",entity.getName(),"pos",new double[]{entity.posX,entity.posY,entity.posZ},
                "yaw",entity.rotationYaw,"height",entity.height,"width",entity.width,"living",entity instanceof EntityLivingBase);
        net.minecraft.util.ResourceLocation type=net.minecraft.entity.EntityList.getKey(entity);
        result.addProperty("type",entity instanceof net.minecraft.entity.player.EntityPlayer ? "minecraft:player" : type==null ? "unknown" : type.toString());
        if(entity instanceof net.minecraft.entity.player.EntityPlayer) result.addProperty("npc",com.mythos.mythosScriptMod.utils.ModUtils.isNpcPlayer(entity));
        if(entity instanceof EntityLivingBase) {
            EntityLivingBase living=(EntityLivingBase)entity;
            result.addProperty("baby",living.isChild());
            result.addProperty("bodyYaw",living.renderYawOffset);
            result.addProperty("headYaw",living.rotationYawHead);
            result.addProperty("headPitch",living.rotationPitch);
        }
        if(entity instanceof net.minecraft.entity.passive.EntitySheep) {
            net.minecraft.entity.passive.EntitySheep sheep=(net.minecraft.entity.passive.EntitySheep)entity;
            result.addProperty("color",sheep.getFleeceColor().getMetadata());result.addProperty("sheared",sheep.getSheared());
        }
        if(entity instanceof net.minecraft.entity.passive.EntityVillager) result.addProperty("variant",((net.minecraft.entity.passive.EntityVillager)entity).getProfession());
        if(entity instanceof net.minecraft.entity.passive.EntityHorse) result.addProperty("variant",((net.minecraft.entity.passive.EntityHorse)entity).getHorseVariant());
        if(entity instanceof net.minecraft.entity.passive.EntityRabbit) result.addProperty("variant",((net.minecraft.entity.passive.EntityRabbit)entity).getRabbitType());
        if(entity instanceof net.minecraft.entity.passive.EntityOcelot) result.addProperty("variant",((net.minecraft.entity.passive.EntityOcelot)entity).getTameSkin());
        if(entity instanceof net.minecraft.entity.passive.EntityParrot) result.addProperty("variant",((net.minecraft.entity.passive.EntityParrot)entity).getVariant());
        if(entity instanceof net.minecraft.entity.passive.EntityLlama) result.addProperty("variant",((net.minecraft.entity.passive.EntityLlama)entity).getVariant());
        if(entity instanceof net.minecraft.entity.passive.EntityWolf) {
            net.minecraft.entity.passive.EntityWolf wolf=(net.minecraft.entity.passive.EntityWolf)entity;
            result.addProperty("tamed",wolf.isTamed());result.addProperty("color",wolf.getCollarColor().getMetadata());
        }
        if(entity instanceof net.minecraft.entity.passive.EntityPig) result.addProperty("saddled",((net.minecraft.entity.passive.EntityPig)entity).getSaddled());
        if(entity instanceof EntityLivingBase){result.addProperty("health",((EntityLivingBase)entity).getHealth());result.addProperty("maxHealth",((EntityLivingBase)entity).getMaxHealth());}
        return result;
    }
}
