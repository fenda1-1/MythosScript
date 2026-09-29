package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import java.nio.file.*;
import java.util.*;

/** Immutable MCP block-state capture. Collision shapes come from Minecraft, not a hand-drawn map. */
final class CapturedParkourWorld implements IBlockAccess {
    final Map<BlockPos, IBlockState> states = new HashMap<>();
    final List<AxisAlignedBB> boxes = new ArrayList<>();
    private final ParkourTrajectory.CollisionIndex collisionIndex;
    final AxisAlignedBB bounds;
    final double[] player = new double[3];
    private JsonObject physics;
    boolean allowFireContact;
    JsonArray effectRefresh = new JsonArray();

    CapturedParkourWorld(String file) throws Exception {
        this(Collections.singletonList(Paths.get("src/test/resources/parkour/" + file)));
    }
    CapturedParkourWorld(List<Path> files) throws Exception {
        Bootstrap.register();
        AxisAlignedBB combined=null;
        Set<AxisAlignedBB> capturedBoxes=new LinkedHashSet<>();
        boolean exact=true;
        String session=null;
        Integer dimension=null;
        boolean first=true;
        for(Path file:files) for(JsonObject json:readScenes(file)) {
        if(first) {
            JsonObject p=json.getAsJsonObject("player");
            allowFireContact=p.has("creative") && p.get("creative").getAsBoolean();
        }
        if(json.has("sessionId")) {
            String next=json.get("sessionId").getAsString();
            int dim=json.getAsJsonObject("player").get("dimension").getAsInt();
            if(session!=null && (!session.equals(next) || dimension!=dim))
                throw new IllegalArgumentException("Cannot merge different world sessions/dimensions");
            session=next;dimension=dim;
        }
        if(physics==null && json.has("physics")) physics=json.getAsJsonObject("physics");
        int[] origin = new int[3], size = new int[3];
        for (int i=0;i<3;i++) {
            origin[i]=json.getAsJsonArray("origin").get(i).getAsInt();
            size[i]=json.getAsJsonArray("size").get(i).getAsInt();
             if(first) player[i]=json.getAsJsonObject("player").getAsJsonArray("pos").get(i).getAsDouble();
        }
        AxisAlignedBB area=new AxisAlignedBB(origin[0]-size[0]/2,origin[1]-size[1]/2,origin[2]-size[2]/2,
                origin[0]-size[0]/2+size[0],origin[1]-size[1]/2+size[1],origin[2]-size[2]/2+size[2]);
        combined=combined==null?area:combined.union(area);
        JsonObject world=json.getAsJsonObject("world");
        if(world.has("dynamicBlocks") && world.getAsJsonArray("dynamicBlocks").size()>0)
            throw new IllegalArgumentException("Moving pistons need time-series replay, not a static scene: "+file);
        if(world.has("collisionBoxes")) {
            if(!"absolute".equals(world.get("collisionCoordinates").getAsString()))
                throw new IllegalArgumentException("Expected absolute collision coordinates");
            for(JsonElement box:world.getAsJsonArray("collisionBoxes")) {
                JsonArray a=box.getAsJsonArray();
                capturedBoxes.add(new AxisAlignedBB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),
                        a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble()));
            }
        } else exact=false;
        List<IBlockState> palette=new ArrayList<>();
        for (JsonElement element : world.getAsJsonArray("palette")) palette.add(parse(element.getAsString()));
        for (JsonElement element : world.getAsJsonArray("cuboids")) {
            JsonArray a=element.getAsJsonArray();
            IBlockState state=palette.get(a.get(6).getAsInt());
            for(int x=a.get(0).getAsInt();x<=a.get(3).getAsInt();x++)
                for(int y=a.get(1).getAsInt();y<=a.get(4).getAsInt();y++)
                    for(int z=a.get(2).getAsInt();z<=a.get(5).getAsInt();z++)
                         {
                             BlockPos pos=new BlockPos(origin[0]+x,origin[1]+y,origin[2]+z);
                             IBlockState previous=states.put(pos,state);
                             if(previous!=null && !previous.equals(state))
                                 throw new IllegalArgumentException("Overlapping captures disagree at "+pos+"; recapture the scene");
                         }
        }
        first=false;
        }
        if(combined==null) throw new IllegalArgumentException("No captured regions");
        bounds=combined;
        if(exact) { boxes.addAll(capturedBoxes);collisionIndex=new ParkourTrajectory.CollisionIndex(boxes);return; }
        for (Map.Entry<BlockPos,IBlockState> entry : states.entrySet()) {
            BlockPos pos=entry.getKey();
            IBlockState state=entry.getValue().getActualState(this,pos);
            // These captures contain full cubes, ladders, fences and non-solid blocks.
            // Reject unsupported multipart geometry instead of silently approximating it.
            if (state.getBlock() instanceof net.minecraft.block.BlockStairs)
                throw new IllegalArgumentException("Capture needs multipart stair collisions at " + pos);
            AxisAlignedBB collision=state.getCollisionBoundingBox(this,pos);
            if(collision!=null) boxes.add(collision.offset(pos));
        }
        collisionIndex=new ParkourTrajectory.CollisionIndex(boxes);
    }
    static List<JsonObject> readScenes(Path file) throws Exception {
        if(!file.getFileName().toString().endsWith(".zip")) return Collections.singletonList(read(file));
        List<JsonObject> scenes=new ArrayList<>();
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file.toFile())) {
            List<java.util.zip.ZipEntry> entries=new ArrayList<>();
            for(java.util.Enumeration<? extends java.util.zip.ZipEntry> e=zip.entries();e.hasMoreElements();)
                entries.add(e.nextElement());
            entries.sort(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName));
            for(java.util.zip.ZipEntry entry:entries) {
                if(entry.isDirectory()) continue;
                try(java.io.Reader reader=new java.io.InputStreamReader(zip.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                    scenes.add(new JsonParser().parse(reader).getAsJsonObject());
                }
            }
        }
        if(scenes.isEmpty()) throw new IllegalArgumentException("Empty scene archive: "+file);
        return scenes;
    }
    static JsonObject read(Path file) throws Exception {
        try(java.io.InputStream stream=Files.newInputStream(file);
                java.io.Reader reader=new java.io.InputStreamReader(file.toString().endsWith(".gz")
                        ? new java.util.zip.GZIPInputStream(stream) : stream,java.nio.charset.StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState parse(String text) {
        if(text.startsWith("__")) throw new IllegalArgumentException("Unknown capture cell: "+text);
        int split=text.indexOf('[');
        Block block=Block.REGISTRY.getObject(new ResourceLocation(split<0?text:text.substring(0,split)));
        if (block==Blocks.AIR && !text.equals("minecraft:air")) throw new IllegalArgumentException(text);
        IBlockState state=block.getDefaultState();
        if(split>=0) for(String field:text.substring(split+1,text.length()-1).split(",")) {
            String[] pair=field.split("=");
            IProperty property=block.getBlockState().getProperty(pair[0]);
            state=state.withProperty(property,(Comparable)property.parseValue(pair[1]).get());
        }
        return state;
    }
    ParkourTrajectory.Frame standing(double x,double y,double z) {
        ParkourTrajectory.Frame frame=new ParkourTrajectory.Frame();
        frame.x=x;frame.y=y;frame.z=z;frame.vy=-.0784000015258789;frame.ground=true;
        frame.snapshotEnvironment(this,states.keySet());
        if(physics!=null) {
            int amplifier=-1,ticks=0;
            for(JsonElement effect:physics.getAsJsonArray("effects")) {
                JsonObject e=effect.getAsJsonObject();
                String id=e.get("id").getAsString();
                if(id.equals("minecraft:levitation")) throw new IllegalArgumentException("Levitation is not supported by the offline model");
                if(id.equals("minecraft:jump_boost")) { amplifier=e.get("amplifier").getAsInt();ticks=e.get("duration").getAsInt(); }
            }
            frame.snapshotMovement(physics.get("movementSpeed").getAsDouble(),physics.get("sprinting").getAsBoolean(),
                    .02F,amplifier,ticks);
        }
        refreshEffects(frame);
        return frame;
    }
    /** Recorded map effects are re-observed at movement planning boundaries,
     * like the live Frame snapshot. Their finite duration still ticks down in search. */
    void refreshEffects(ParkourTrajectory.Frame frame) {
        for(JsonElement entry:effectRefresh) {
            JsonObject effect=entry.getAsJsonObject();
            JsonArray center=effect.getAsJsonArray("center");
            double dx=frame.x-center.get(0).getAsDouble(),dy=frame.y-center.get(1).getAsDouble(),
                    dz=frame.z-center.get(2).getAsDouble(),radius=effect.get("radius").getAsDouble();
            if(dx*dx+dy*dy+dz*dz>radius*radius) continue;
            if(!effect.get("id").getAsString().equals("minecraft:jump_boost"))
                throw new IllegalArgumentException("Unsupported recorded effect refresh: "+effect);
            frame.snapshotJumpBoost(effect.get("amplifier").getAsInt(),effect.get("duration").getAsInt());
        }
    }
    ParkourTrajectory.Frame capturedPlayer() {
        ParkourTrajectory.Frame frame=standing(player[0],player[1],player[2]);
        if(physics==null) throw new IllegalArgumentException("Capture has no player physics");
        if(physics.get("flying").getAsBoolean()) throw new IllegalArgumentException("Flying state cannot replay as walking physics; supply an explicit supported standing start or land before capturing");
        frame.airAcceleration=physics.get("airAcceleration").getAsFloat();
        JsonArray motion=physics.getAsJsonArray("motion");
        frame.vx=motion.get(0).getAsDouble();frame.vy=motion.get(1).getAsDouble();frame.vz=motion.get(2).getAsDouble();
        frame.ground=physics.get("onGround").getAsBoolean();
        frame.collidedHorizontally=physics.get("collidedHorizontally").getAsBoolean();
        JsonArray body=physics.getAsJsonArray("body");
        if(body!=null) frame.bodyHeight=body.get(4).getAsDouble()-body.get(1).getAsDouble();
        return frame;
    }
    boolean safe(AxisAlignedBB body) {
        return safe(body,false);
    }
    boolean swimmable(AxisAlignedBB body) {
        return safe(body,true);
    }
    private boolean safe(AxisAlignedBB body, boolean allowWater) {
        if (body.minX<bounds.minX || body.maxX>bounds.maxX || body.minY<bounds.minY || body.maxY>bounds.maxY
                || body.minZ<bounds.minZ || body.maxZ>bounds.maxZ) return false;
        for (AxisAlignedBB box:collisionIndex.nearby(body)) if(body.grow(-1e-7).intersects(box)) return false;
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(body.minX,body.minY,body.minZ),
                  new BlockPos(body.maxX,body.maxY,body.maxZ))) {
            if (!body.intersects(new AxisAlignedBB(pos))) continue;
            if(!states.containsKey(pos)) return false; // Holes between disjoint captures are UNKNOWN, not air.
            Block block=getBlockState(pos).getBlock();
            if(block==Blocks.FIRE && !allowFireContact || block==Blocks.WEB) return false;
            if(getBlockState(pos).getMaterial()==net.minecraft.block.material.Material.LAVA) {
                // Independent vanilla isMaterialInBB cell enumeration, not the
                // production predicate. A full-body rim contact may still burn.
                if(pos.getX()>=MathHelper.floor(body.minX+.1) && pos.getX()<=MathHelper.floor(body.maxX-.1)
                        && pos.getY()>=MathHelper.floor(body.minY+.4) && pos.getY()<=MathHelper.floor(body.maxY-.4)
                        && pos.getZ()>=MathHelper.floor(body.minZ+.1) && pos.getZ()<=MathHelper.floor(body.maxZ-.1)) return false;
            } else if(getBlockState(pos).getMaterial().isLiquid() && !allowWater) return false;
        }
        return true;
    }
    public IBlockState getBlockState(BlockPos pos) {
        // BetterBlockPos has a different hash implementation; world lookup is
        // coordinate-based, never dependent on the caller's BlockPos subtype.
        return states.getOrDefault(new BlockPos(pos.getX(),pos.getY(),pos.getZ()),Blocks.BARRIER.getDefaultState());
    }
    public TileEntity getTileEntity(BlockPos pos) { return null; }
    public int getCombinedLight(BlockPos pos,int lightValue) { return 0; }
    public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock()==Blocks.AIR; }
    public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
    public int getStrongPower(BlockPos pos,EnumFacing direction) { return 0; }
    public WorldType getWorldType() { return WorldType.DEFAULT; }
    public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean fallback) {
        return getBlockState(pos).isSideSolid(this,pos,side);
    }
}
