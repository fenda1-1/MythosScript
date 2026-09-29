package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import net.minecraft.util.math.MathHelper;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.function.Predicate;

/** Bounded input search using vanilla gravity, drag, jump impulse and swept collisions.
 * Horizontal braking is explicit in the controls; vertical physics is unmodified. */
public final class ParkourTrajectory {
    private static final double HALF = .30000001192092896;
    private static final double HEIGHT = 1.7999999523162842;
    /** Worker-local spatial index over the immutable collision snapshot. */
    static final class CollisionIndex extends AbstractList<AxisAlignedBB> {
        final List<AxisAlignedBB> all;
        final Map<BlockPos,List<Integer>> cells = new HashMap<>();
        CollisionIndex(List<AxisAlignedBB> boxes) {
            all = boxes;
            for (int i=0;i<boxes.size();i++) {
                AxisAlignedBB b=boxes.get(i);
                for (int x=cell(b.minX);x<=cell(b.maxX);x++)
                    for (int y=cell(b.minY);y<=cell(b.maxY);y++)
                        for (int z=cell(b.minZ);z<=cell(b.maxZ);z++)
                            cells.computeIfAbsent(new BlockPos(x,y,z), k -> new ArrayList<>()).add(i);
            }
        }
        static int cell(double value) { return (int)Math.floor(value/4); }
        @Override public AxisAlignedBB get(int i) { return all.get(i); }
        @Override public int size() { return all.size(); }
        boolean intersects(AxisAlignedBB body) {
            int x1=cell(body.maxX),y1=cell(body.maxY),z1=cell(body.maxZ);
            for(int x=cell(body.minX);x<=x1;x++)
                for(int y=cell(body.minY);y<=y1;y++)
                    for(int z=cell(body.minZ);z<=z1;z++) {
                        List<Integer> ids=cells.get(new BlockPos(x,y,z));
                        if(ids!=null) for(int id:ids) if(all.get(id).intersects(body)) return true;
                    }
            return false;
        }
        List<AxisAlignedBB> nearby(AxisAlignedBB sweep) {
            int x0=cell(sweep.minX),x1=cell(sweep.maxX),y0=cell(sweep.minY),y1=cell(sweep.maxY),
                    z0=cell(sweep.minZ),z1=cell(sweep.maxZ);
            if(x0==x1 && y0==y1 && z0==z1) {
                List<Integer> ids=cells.get(new BlockPos(x0,y0,z0));
                if(ids==null) return Collections.emptyList();
                List<AxisAlignedBB> result=new ArrayList<>();
                for(int id:ids) {AxisAlignedBB b=all.get(id);if(b.intersects(sweep)) result.add(b);}
                return result;
            }
            BitSet selected = new BitSet();
            for (int x=cell(sweep.minX);x<=cell(sweep.maxX);x++)
                for (int y=cell(sweep.minY);y<=cell(sweep.maxY);y++)
                    for (int z=cell(sweep.minZ);z<=cell(sweep.maxZ);z++) {
                        List<Integer> ids=cells.get(new BlockPos(x,y,z));
                        if (ids!=null) for (int id:ids) selected.set(id);
                    }
            List<AxisAlignedBB> result=new ArrayList<>();
            for (int i=selected.nextSetBit(0);i>=0;i=selected.nextSetBit(i+1)) {
                AxisAlignedBB b=all.get(i);
                if (b.intersects(sweep)) result.add(b);
            }
            return result;
        }
    }
    private static final Map<String, List<Control>> RECENT_CONTROLS = new LinkedHashMap<String, List<Control>>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Control>> entry) {
            return size() > 32;
        }
    };
    private static final Map<String,List<List<Control>>> RECENT_FLOWS =
            new LinkedHashMap<String,List<List<Control>>>(32,.75F,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,List<List<Control>>> entry) {
                    return size()>32;
                }
            };

    private static String routeKey(double gx, double gy, double gz) {
        return gx + ":" + gy + ":" + gz;
    }

    /** Whether solved input sequences may be reused for the same goal. */
    private static boolean inputCacheEnabled() {
        return Baritone.settings().parkourInputCache.value;
    }

    private static List<Frame> remember(List<Frame> frames, double gx, double gy, double gz) {
        if (!inputCacheEnabled()) return frames;
        List<Control> controls = new ArrayList<>();
        for (Frame frame : frames) controls.add(frame.control);
        synchronized (RECENT_CONTROLS) { RECENT_CONTROLS.put(routeKey(gx,gy,gz), controls); }
        return frames;
    }

    private static List<Frame> replay(Frame start, List<AxisAlignedBB> boxes, double gx, double gy, double gz,
            boolean sprintAllowed, int maxTicks, long deadline, Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived) {
        if (!inputCacheEnabled()) return Collections.emptyList();
        List<Control> controls;
        synchronized (RECENT_CONTROLS) { controls = RECENT_CONTROLS.get(routeKey(gx,gy,gz)); }
        if (controls == null || controls.size() > maxTicks) return Collections.emptyList();
        Frame from = start;
        int used = 0;
        for (Control control : controls) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline
                    || (control.sprint && !sprintAllowed) || (from.immediateLaunch && !control.jump)) break;
            // Re-simulate inputs against CURRENT collisions, effects and hazards.
            // Never reuse old positions or assume an unchanged world.
            Frame next = step(from,control,boxes);
            if (returnsToChainLaunch(start, next)) break;
            if (!safe.test(next.box()) || fragileSlowContact(next)
                    || next.y < Math.min(start.y,gy)-1.3 || next.y > start.searchCeiling(gy)
                    || Math.hypot(next.x-start.x,next.z-start.z)>12
                    || (!from.ground && next.ground && next.y<=from.y+.001
                        && !stableContact(from,next.y,boxes))) break;
            if (arrived.test(next)) {
                ParkourDebugLog.INSTANCE.event("trajectory_replay");
                return unwind(next);
            }
            from = next;
            used++;
        }
        // An input replay can remain safe yet finish a few centimetres from
        // its original stopping point. Refine that endpoint before abandoning
        // the entire route. All refinement frames use the same safety checks.
        if (used == controls.size() && used < maxTicks && from.ground
                && Math.abs(from.y-gy)<.03 && Math.hypot(from.x-gx,from.z-gz)<.75) {
            List<Frame> corrected = directRoute(from,boxes,gx,gy,gz,sprintAllowed,
                    maxTicks-used,Math.min(deadline,System.nanoTime()+12_000_000L),safe,arrived);
            if (!corrected.isEmpty()) {
                ParkourDebugLog.INSTANCE.event("trajectory_replay_refined");
                return corrected;
            }
        }
        return Collections.emptyList();
    }
    public static final class Control {
        public final float yaw;
        public final boolean forward, sprint, jump, sneak;
        public final int strafe;
        public final boolean resetHorizontal;
        public Control(double angle, boolean forward, boolean sprint, boolean jump) {
            this(angle, forward, sprint, jump, 0);
        }
        Control(double angle, boolean forward, boolean sprint, boolean jump, int strafe) {
            this(angle, forward, sprint, jump, false, strafe);
        }
        Control(double angle, boolean forward, boolean sprint, boolean jump, boolean sneak, int strafe) {
            this(angle,forward,sprint,jump,sneak,strafe,false);
        }
        Control(double angle, boolean forward, boolean sprint, boolean jump, boolean sneak, int strafe,
                boolean resetHorizontal) {
            this.yaw = (float) Math.toDegrees(angle) - 90;
            this.forward = forward; this.sprint = sprint; this.jump = jump;
            this.sneak = sneak;
            this.strafe = strafe;
            this.resetHorizontal = resetHorizontal;
        }
    }
    public static final class Frame {
        public double x,y,z,vx,vy,vz;
        double bodyHeight = HEIGHT;
        public boolean ground;
        public boolean collidedHorizontally;
        public boolean immediateLaunch;
        // EntityPlayer updates jumpMovementFactor after travel, so airborne
        // acceleration uses the previous tick's sprint state.
        public float airAcceleration = .02F;
        private double walkingSpeed = .1;
        private double jumpBoost;
        private int jumpBoostTicks;
        private Map<BlockPos, Float> slipperiness = Collections.emptyMap();
        private Set<BlockPos> soulSand = Collections.emptySet();
        private Set<BlockPos> ice = Collections.emptySet();
        private Set<BlockPos> slimeBlocks = Collections.emptySet();
        private Set<BlockPos> climbable = Collections.emptySet();
        private Set<BlockPos> water = Collections.emptySet();
        private Set<BlockPos> lava = Collections.emptySet();
        private Map<BlockPos, net.minecraft.util.EnumFacing> ladders = Collections.emptyMap();
        Frame parent;
        public Control control;
        double score;
        double turnCost;
        Frame() {}
        public Frame(EntityPlayerSP p) {
            x=p.posX; y=p.posY; z=p.posZ; vx=p.motionX; vy=p.motionY; vz=p.motionZ; ground=p.onGround;
            bodyHeight=p.getEntityBoundingBox().maxY-p.getEntityBoundingBox().minY;
            airAcceleration = p.jumpMovementFactor;
            // Snapshot potion/equipment modifiers on the client thread. The
            // sprint modifier is applied separately for each simulated input.
            walkingSpeed = p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED)
                    .getAttributeValue() / (p.isSprinting() ? 1.300000011920929 : 1.0);
            collidedHorizontally = p.collidedHorizontally;
            net.minecraft.potion.PotionEffect jump = p.getActivePotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST);
            jumpBoost = jump == null ? 0 : .1 * (jump.getAmplifier() + 1);
            jumpBoostTicks = jump == null ? 0 : jump.getDuration();
        }
        public Frame(EntityPlayerSP p, AxisAlignedBB region) {
            this(p);
            snapshotEnvironment(p.world, region);
        }
        void snapshotMovement(double speed, boolean sprinting, float air, int jumpAmplifier, int jumpTicks) {
            walkingSpeed=speed/(sprinting?1.300000011920929:1.0);
            airAcceleration=air;
            jumpBoost=jumpAmplifier<0?0:.1*(jumpAmplifier+1);
            jumpBoostTicks=jumpTicks;
        }
        void snapshotJumpBoost(int amplifier, int ticks) {
            jumpBoost=amplifier<0?0:.1*(amplifier+1);
            jumpBoostTicks=ticks;
        }
        /** Capture block behaviour as well as collisions; geometry alone cannot identify a ladder. */
        void snapshotEnvironment(net.minecraft.world.IBlockAccess world, AxisAlignedBB region) {
            snapshotEnvironment(world,BlockPos.getAllInBox(new BlockPos(region.minX,region.minY,region.minZ),
                    new BlockPos(region.maxX,region.maxY,region.maxZ)));
        }
        void snapshotEnvironment(net.minecraft.world.IBlockAccess world, Iterable<BlockPos> positions) {
            Map<BlockPos, Float> snapshot = new HashMap<>();
            Set<BlockPos> slowBlocks = new HashSet<>();
            Set<BlockPos> fragileBlocks = new HashSet<>();
            Set<BlockPos> bounceBlocks = new HashSet<>();
            Set<BlockPos> climbing = new HashSet<>();
            Set<BlockPos> swimming = new HashSet<>();
            Set<BlockPos> burning = new HashSet<>();
            Map<BlockPos, net.minecraft.util.EnumFacing> faces = new HashMap<>();
            for (BlockPos pos : positions) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                net.minecraft.block.Block block = state.getBlock();
                if(state.getMaterial()==net.minecraft.block.material.Material.WATER) swimming.add(pos.toImmutable());
                if(state.getMaterial()==net.minecraft.block.material.Material.LAVA) burning.add(pos.toImmutable());
                if(block.slipperiness!=.6F) snapshot.put(pos.toImmutable(), block.slipperiness);
                if (block==net.minecraft.init.Blocks.ICE) fragileBlocks.add(pos.toImmutable());
                if (block == net.minecraft.init.Blocks.SOUL_SAND) {
                    slowBlocks.add(pos.toImmutable());
                }
                if (block == net.minecraft.init.Blocks.SLIME_BLOCK) {
                    bounceBlocks.add(pos.toImmutable());
                }
                if (block == net.minecraft.init.Blocks.LADDER || block == net.minecraft.init.Blocks.VINE) {
                    climbing.add(pos.toImmutable());
                    if (block == net.minecraft.init.Blocks.LADDER)
                        faces.put(pos.toImmutable(), state.getValue(net.minecraft.block.BlockLadder.FACING));
                    else for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.HORIZONTALS) {
                        if (state.getValue(net.minecraft.block.BlockVine.getPropertyFor(side))) {
                            faces.put(pos.toImmutable(), side.getOpposite());
                            break;
                        }
                    }
                } else if (block instanceof net.minecraft.block.BlockTrapDoor
                        && state.getValue(net.minecraft.block.BlockTrapDoor.OPEN)) {
                    net.minecraft.block.state.IBlockState below = world.getBlockState(pos.down());
                    if (below.getBlock() == net.minecraft.init.Blocks.LADDER
                            && below.getValue(net.minecraft.block.BlockLadder.FACING)
                                == state.getValue(net.minecraft.block.BlockTrapDoor.FACING)) climbing.add(pos.toImmutable());
                }
            }
            slipperiness = Collections.unmodifiableMap(snapshot);
            soulSand = Collections.unmodifiableSet(slowBlocks);
            ice = Collections.unmodifiableSet(fragileBlocks);
            slimeBlocks = Collections.unmodifiableSet(bounceBlocks);
            climbable = Collections.unmodifiableSet(climbing);
            water = Collections.unmodifiableSet(swimming);
            lava = Collections.unmodifiableSet(burning);
            ladders = Collections.unmodifiableMap(faces);
        }
        public AxisAlignedBB box() { return new AxisAlignedBB(x-HALF,y,z-HALF,x+HALF,y+bodyHeight,z+HALF); }
        public Frame restart(boolean launch) {
            Frame copy = new Frame();
            copy.x=x; copy.y=y; copy.z=z; copy.vx=vx; copy.vy=vy; copy.vz=vz;
            copy.bodyHeight=bodyHeight;
            copy.ground=ground; copy.collidedHorizontally=collidedHorizontally;
            copy.airAcceleration=airAcceleration; copy.slipperiness=slipperiness; copy.soulSand=soulSand;
            copy.ice=ice;
            copy.slimeBlocks=slimeBlocks;
            copy.climbable=climbable; copy.ladders=ladders;
            copy.water=water;
            copy.lava=lava;
            copy.immediateLaunch=launch;
            copy.walkingSpeed=walkingSpeed;
            copy.jumpBoost=jumpBoost;
            copy.jumpBoostTicks=jumpBoostTicks;
            return copy;
        }
        public double jumpHeight() {
            double height = 0, velocity = .41999998688697815 + (jumpBoostTicks > 0 ? jumpBoost : 0);
            for (int tick = 0; tick < 256 && velocity > 0; tick++) {
                height += velocity;
                velocity = (velocity - .08) * .98;
            }
            return height;
        }
        private double searchCeiling(double targetY) {
            // A slime rebound can already carry more upward momentum than a
            // normal jump. Do not prune that unavoidable ballistic arc merely
            // because the destination is below its apex.
            double rise = 0, velocity = vy;
            for (int tick = 0; tick < 256 && velocity > 0; tick++) {
                rise += velocity;
                velocity = (velocity - .08) * .98;
            }
            return Math.max(Math.max(y, targetY) + Math.max(2, jumpHeight() + .1), y + rise + .1);
        }
        public double error(EntityPlayerSP p) {
            return Math.abs(x-p.posX)+Math.abs(y-p.posY)+Math.abs(z-p.posZ);
        }
        public double motionError(double motionX, double motionY, double motionZ) {
            return Math.max(Math.abs(vy-motionY),Math.abs(vx-motionX)+Math.abs(vz-motionZ));
        }
        public boolean inLava() {
            if(lava.isEmpty()) return false;
            AxisAlignedBB body=box().grow(-.10000000149011612,-.4000000059604645,-.10000000149011612);
            for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(body.minX,body.minY,body.minZ),
                    new BlockPos(body.maxX,body.maxY,body.maxZ))) if(lava.contains(pos)) return true;
            return false;
        }
        public boolean onLadder() { return climbable.contains(new BlockPos(x, y, z)); }
        public boolean inWater() {
            return !water.isEmpty() && touchesWater(box().grow(0,-.4,0).shrink(.001));
        }
        private boolean touchesWater(AxisAlignedBB body) {
            for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(body.minX,body.minY,body.minZ),
                    new BlockPos(body.maxX,body.maxY,body.maxZ))) if(water.contains(pos)) return true;
            return false;
        }
    }
    private static List<Frame> directRoute(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed, int maxTicks,
            long deadline, Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived) {
        boolean airborne = !start.ground && !start.inWater();
        if(start.inWater()) {
            // A rising isolated pool needs buoyancy before horizontal departure;
            // forward-only search drops out of the source before catching it.
            for(int wait=0;wait<=24;wait++) for(int rise=0;rise<=24;rise+=2) {
                Frame from=start;
                for(int tick=0;tick<Math.min(100,maxTicks);tick++) {
                    if(System.nanoTime()>=deadline || Thread.currentThread().isInterrupted()) return Collections.emptyList();
                    double dx=gx-from.x-from.vx*4,dz=gz-from.z-from.vz*4;
                    boolean forward=tick>=wait && Math.hypot(dx,dz)>.025;
                    double desiredY=tick<rise?start.y:gy+.05;
                    Frame next=step(from,new Control(Math.atan2(dz,dx),forward,sprintAllowed,
                            desiredY-from.y>from.vy*3),boxes);
                    if(!safe.test(next.box()) || next.y<Math.min(start.y,gy)-1.3) break;
                    if(arrived.test(next)) return unwind(next);
                    from=next;
                }
            }
        }
        double heading = Math.atan2(gz-start.z, gx-start.x);
        double ceiling = start.searchCeiling(gy);
        double[] ladderHeadings = airborne ? new double[0] : ladderHeadings(start, boxes);
        // Fully simulated short candidates; keep the broad search as fallback.
        boolean risingRebound = start.vy > .42;
        // Velocity corrections change the flight clock. Re-simulate steering
        // and braking from the actual airborne state, without a new takeoff.
        int ladderCandidates = risingRebound || airborne ? 0 : ladderHeadings.length * 14;
        int candidates = risingRebound || airborne ? 7 : 98 + ladderCandidates;
        for (int candidate = 0; candidate < candidates; candidate++) {
            int shifted = candidate < ladderCandidates ? 98 + candidate : candidate - ladderCandidates;
            int launch = shifted >= 98 ? 6 : shifted/7-1;
            int mode = shifted%7;
            // A thin wall can require approaching its end perpendicular to the
            // goal before turning in; shallow arcs alone keep hitting its face.
            int curve = mode == 5 ? 4 : mode == 6 ? -4
                    : mode == 0 ? 0 : mode % 2 == 1 ? (mode+1)/2 : -mode/2;
            if (shifted >= 98) curve = 8 + (shifted - 98) % Math.max(1, ladderHeadings.length);
            if (start.immediateLaunch && launch != 0) continue;
            // A high rebound has a much longer flight than a normal jump.
            // Keep forward drive available through its landing window instead
            // of braking after 24 ticks and discarding the remaining range.
            for (int drive = 4; drive <= (risingRebound ? maxTicks : 24); drive += 2) {
                Frame from = start;
                for (int tick = 0; tick < Math.min(maxTicks, 50); tick++) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
                        return Collections.emptyList();
                    double angle = curve == 0 ? heading : tick < drive/2
                             ? heading + curve*Math.PI/8 : Math.atan2(gz-from.z,gx-from.x);
                    boolean onLadder = curve >= 8 && from.onLadder();
                    if (curve >= 8 && !onLadder) angle = ladderHeadings[curve - 8];
                    boolean forward = true;
                    boolean sneak = onLadder && from.y >= gy - .03;
                    boolean swimming=from.inWater();
                    if ((tick >= drive || swimming) && !onLadder) {
                        double lead = from.ground ? 1.2 : 4;
                        double dx = gx-from.x-from.vx*lead, dz = gz-from.z-from.vz*lead;
                        angle = Math.atan2(dz, dx);
                        forward = Math.hypot(dx,dz) > .025;
                    }
                    Frame next = step(from, new Control(angle, forward,
                            sprintAllowed && tick < drive && !onLadder && !swimming,
                            swimming ? gy+.05-from.y>from.vy*3 : tick == launch && from.ground, sneak, 0), boxes);
                    if (returnsToChainLaunch(start, next)) break;
                    if (!safe.test(next.box()) || fragileSlowContact(next)
                            || next.y < Math.min(start.y,gy)-1.3
                            || next.y > ceiling
                            || Math.hypot(next.x-start.x,next.z-start.z)>12
                            || (!from.ground && next.ground && next.y<=from.y+.001
                                && !stableContact(from,next.y,boxes))) break;
                    if (arrived.test(next)) {
                        ParkourDebugLog.INSTANCE.event("trajectory_direct frames=" + (tick+1)
                                + " candidate=" + candidate + " drive=" + drive);
                        return unwind(next);
                    }
                    from = next;
                }
            }
        }
        return Collections.emptyList();
    }

    /** Fully simulate a short retreat onto the landing after a late velocity correction.
     * No new jump, no swimming across a gap, and no success while still in lava. */
    public static List<Frame> searchSupportedRecovery(Frame start,List<AxisAlignedBB> boxes,
            AxisAlignedBB support,double gx,double gz,Predicate<AxisAlignedBB> recoverySafe,
            Predicate<AxisAlignedBB> safe,long deadline) {
        if(start.y<support.maxY-1e-7 || start.y>support.maxY+.75
                || Math.hypot(start.x-gx,start.z-gz)>1.25) return Collections.emptyList();
        if(boxes.size()>32 && !(boxes instanceof CollisionIndex)) boxes=new CollisionIndex(boxes);
        Predicate<Frame> settled=settledArrival(boxes,gx,support.maxY,gz,.18,safe);
        Frame from=start;
        // ponytail: at most one second of supported rim recovery; longer escapes need a new plan.
        for(int tick=0;tick<20;tick++) {
            if(System.nanoTime()>=deadline || Thread.currentThread().isInterrupted()) break;
            double dx=gx-from.x-from.vx*(from.ground?1.2:4),dz=gz-from.z-from.vz*(from.ground?1.2:4);
            Frame next=step(from,new Control(Math.atan2(dz,dx),Math.hypot(dx,dz)>.025,false,false),boxes);
            AxisAlignedBB body=next.box();
            if(next.y<support.maxY-1e-7
                    || Math.min(body.maxX,support.maxX)-Math.max(body.minX,support.minX)<.1
                    || Math.min(body.maxZ,support.maxZ)-Math.max(body.minZ,support.minZ)<.1
                    || !recoverySafe.test(body) || hasSupport(body.shrink(1e-7),boxes)) break;
            if(!next.inLava() && settled.test(next)) return unwind(next);
            from=next;
        }
        return Collections.emptyList();
    }

    /** Replay a prepared chain from actual contact, rejecting a lost launch support. */
    public static List<Frame> replaySupported(Frame start,List<Frame> prepared,List<AxisAlignedBB> boxes,
            Predicate<AxisAlignedBB> safe) {
        if(boxes.size()>32 && !(boxes instanceof CollisionIndex)) boxes=new CollisionIndex(boxes);
        List<Frame> result=new ArrayList<>();
        Frame from=start;
        for(Frame expected:prepared) {
            Frame next=step(from,expected.control,boxes);
            if(next.ground!=expected.ground || !safe.test(next.box()) || hasSupport(next.box(),boxes))
                return Collections.emptyList();
            result.add(next);from=next;
        }
        return result;
    }

    /** Stay facing the backing wall across adjacent climb cells. */
    public static Control climbControl(Frame frame,double gx,double gz) {
        net.minecraft.util.EnumFacing face=frame.ladders.get(new BlockPos(frame.x,frame.y,frame.z));
        if(face==null) return null;
        double sx=face.getFrontOffsetX(),sz=face.getFrontOffsetZ();
        double lateral=(gx-frame.x-frame.vx*4)*(-sz)+(gz-frame.z-frame.vz*4)*sx;
        return new Control(Math.atan2(-sz+sx*lateral,-sx-sz*lateral),true,false,false,true,0);
    }

    /** Search approach, wall contact, climb and landing as one simulated control sequence. */
    private static List<Frame> ladderRoute(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed, int maxTicks,
            long deadline, Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived) {
        // A nearby ladder is not the destination when the goal cell itself is
        // not climbable. Grabbing it collides with the landing skull/wall.
        if (!start.ladders.containsKey(new BlockPos(gx, gy, gz))) return Collections.emptyList();
        List<BlockPos> targets = new ArrayList<>(start.ladders.keySet());
        targets.sort(Comparator.comparingDouble(p -> Math.hypot(p.getX()+.5-gx,p.getZ()+.5-gz)
                + Math.abs(p.getY()+1-gy)));
        for (BlockPos pos : targets) {
            if (Math.hypot(pos.getX()+.5-gx,pos.getZ()+.5-gz)>1.5
                    || Math.abs(pos.getY()+1-gy)>1.1) continue;
            net.minecraft.util.EnumFacing face = start.ladders.get(pos);
            double nx=face.getFrontOffsetX(), nz=face.getFrontOffsetZ();
            for (int launch : new int[]{0,2,4,6,8,10,12,1,3,5,7,9,11,-1})
                for (int aim=0;aim<13;aim++) for (int turn=4;turn<=12;turn+=4) {
                if (start.immediateLaunch && launch!=0) continue;
                double offset=(aim==0?0:(aim+1)/2*(aim%2==1?1:-1))*Math.PI/12;
                // Approach outside the 3/16 slab. Aiming at cell centre catches
                // its side with the head before the feet can enter the ladder.
                double tx=pos.getX()+.5+nx*.06, tz=pos.getZ()+.5+nz*.06;
                double heading=Math.atan2(tz-start.z,tx-start.x)+offset;
                Frame from=start;
                boolean caught=false;
                for (int tick=0;tick<maxTicks;tick++) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline)
                        return Collections.emptyList();
                    boolean here=from.onLadder() && MathHelper.floor(from.x)==pos.getX()
                            && MathHelper.floor(from.z)==pos.getZ();
                    caught |= here;
                    if (!caught && tick>30) break;
                    double dx, dz;
                    boolean sneak=false, sprint=false, forward=true;
                    double angle=tick<turn?heading:Math.atan2(tz-from.z,tx-from.x);
                    boolean goalCell=start.ladders.containsKey(new BlockPos(gx,gy,gz))
                            && from.onLadder() && MathHelper.floor(from.x)==MathHelper.floor(gx)
                            && MathHelper.floor(from.y)==MathHelper.floor(gy)
                            && MathHelper.floor(from.z)==MathHelper.floor(gz);
                    net.minecraft.util.EnumFacing sourceFace=from.ladders.get(new BlockPos(from.x,from.y,from.z));
                    if (sourceFace!=null && from.onLadder() && gy>from.y+.15) {
                        double sx=sourceFace.getFrontOffsetX(),sz=sourceFace.getFrontOffsetZ();
                        // Slide along the wall toward the next climbable cell.
                        // Climbing only at the source centre deadlocks below a
                        // canopy when the higher vine is one cell to the side.
                        double lateral=(gx-from.x-from.vx*4)*(-sz)
                                +(gz-from.z-from.vz*4)*sx;
                        angle=Math.atan2(-sz+sx*lateral,-sx-sz*lateral);
                        sneak=true;
                    } else if (goalCell) {
                        // Stop inside the requested ladder cell. Continuing to
                        // press its face climbs past it; releasing sneak while
                        // changing between adjacent ladder cells drops the catch.
                        dx=gx-from.x-from.vx*5; dz=gz-from.z-from.vz*5;
                        angle=Math.atan2(dz,dx);
                        forward=Math.hypot(dx,dz)>.018;
                        sneak=true;
                    } else if (here && gy > from.y) {
                        // Catch on the descending half of a jump first. Without
                        // sneak, feet fall out of the ladder before the steering
                        // input reaches its face and starts the climb.
                        sneak=true;
                        // Push into the physical ladder face to obtain vanilla's
                        // horizontal-collision climb impulse. Brake along its wall.
                        double lateral=(tx-from.x-from.vx*5)*(-nz)+(tz-from.z-from.vz*5)*nx;
                        dx=-nx-nz*lateral*4; dz=-nz+nx*lateral*4;
                        angle=Math.atan2(dz,dx);
                    } else if (caught || tick>24) {
                        double lead=from.ground?1.5:5;
                        dx=gx-from.x-from.vx*lead; dz=gz-from.z-from.vz*lead;
                        angle=Math.atan2(dz,dx);
                        forward=Math.hypot(dx,dz)>.018;
                        sneak=here || from.ground;
                    } else sprint=sprintAllowed;
                    Frame next=step(from,new Control(angle,forward,sprint,
                            tick==launch && from.ground,sneak,0),boxes);
                    if (!safe.test(next.box()) || next.y<Math.min(start.y,gy)-1.3
                            || next.y>start.searchCeiling(gy)) break;
                    if (arrived.test(next)) {
                        ParkourDebugLog.INSTANCE.event("trajectory_ladder launch="+launch+" aim="+aim+" turn="+turn+" frames="+(tick+1));
                        return unwind(next);
                    }
                    from=next;
                }
            }
        }
        return Collections.emptyList();
    }

    public static List<Frame> search(EntityPlayerSP player, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed) {
        return search(player, boxes, gx, gy, gz, sprintAllowed, .55, 120);
    }

    public static List<Frame> search(EntityPlayerSP player, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            double goalRadius, int maxTicks) {
        return search(new Frame(player), boxes, gx, gy, gz, sprintAllowed, goalRadius, maxTicks);
    }

    /** Move around a post on its base lip before stepping down to the next ledge. */
    private static List<Frame> ledgeRoute(Frame start,List<AxisAlignedBB> boxes,
            double gx,double gy,double gz,int maxTicks,long deadline,
            Predicate<AxisAlignedBB> safe,Predicate<Frame> arrived) {
        if(!start.ground || start.vy>.42 || start.immediateLaunch || gy>start.y+.01
                || Math.hypot(gx-start.x,gz-start.z)>3 || !lineBlocked(start,boxes,gx,gz))
            return Collections.emptyList();
        double heading=Math.atan2(gz-start.z,gx-start.x);
        long firstLimit=System.nanoTime()+Math.max(0L,(deadline-System.nanoTime())/2);
        first:
        for(int side:new int[]{-1,1}) {
            Frame setup=start;
            for(int preparation=1;preparation<=30;preparation++) {
                setup=step(setup,new Control(heading+side*Math.PI/2,true,false,false,true,0),boxes);
                if(!setup.ground || !safe.test(setup.box())) break;
                if(preparation%2!=0) continue;
                if(lineBlocked(setup,boxes,setup.x+Math.cos(heading)*2,setup.z+Math.sin(heading)*2)) continue;
                for(int straightTicks:new int[]{0,3,6,9}) {
                    Frame from=setup;
                    for(int tick=0;tick<Math.min(60,maxTicks-preparation);tick++) {
                        if(Thread.currentThread().isInterrupted()) return Collections.emptyList();
                        if(System.nanoTime()>=firstLimit) break first;
                        double dx=gx-from.x-from.vx*(from.ground?1.5:4);
                        double dz=gz-from.z-from.vz*(from.ground?1.5:4);
                        double angle=tick<straightTicks?heading:Math.atan2(dz,dx);
                        Frame next=step(from,new Control(angle,Math.hypot(dx,dz)>.02,false,false),boxes);
                        if(!safe.test(next.box()) || next.y<gy-1) break;
                        if(arrived.test(next)) {
                            ParkourDebugLog.INSTANCE.event("trajectory_ledge setup="+preparation+" straight="+straightTicks);
                            return unwind(next);
                        }
                        from=next;
                    }
                }
            }
        }
        // The goal line stays blocked. Slide to the end of the lip, then jump.
        // A diagonal sneak loses the lip; safe-walk stops both axes together.
        double bestGap=Double.POSITIVE_INFINITY;
        Frame best=start;
        for(int dir=0;dir<4;dir++) {
            Frame setup=start;
            int stopped=0;
            for(int preparation=1;preparation<=24 && stopped<3;preparation++) {
                Frame next=step(setup,new Control(dir*Math.PI/2,true,false,false,true,0),boxes);
                if(!next.ground || Math.abs(next.y-start.y)>.03 || !safe.test(next.box())) break;
                if(Math.hypot(next.x-setup.x,next.z-setup.z)<.012) stopped++;
                else stopped=0;
                setup=next;
                if(preparation%2!=0) continue;
                for(int into:new int[]{0,-1,1}) for(int along:new int[]{0,2,4,8,12}) {
                    Frame from=setup;
                    for(int tick=0;tick<Math.min(40,maxTicks-preparation);tick++) {
                        if(Thread.currentThread().isInterrupted()) return Collections.emptyList();
                        if(System.nanoTime()>=deadline) {
                            ParkourDebugLog.INSTANCE.event("trajectory_lip_best gap="+bestGap+" pos="+best.x+","+best.y+","+best.z);
                            return Collections.emptyList();
                        }
                        boolean hug=tick<along;
                        double aim=hug?dir*Math.PI/2:Math.atan2(gz-from.z,gx-from.x);
                        Frame jumped=step(from,new Control(aim,true,true,tick==0&&from.ground,false,hug?into:0),boxes);
                        if(!safe.test(jumped.box()) || jumped.y<Math.min(start.y,gy)-1.3) break;
                        double gap=Math.hypot(jumped.x-gx,jumped.z-gz)+Math.abs(jumped.y-gy);
                        if(gap<bestGap) {bestGap=gap;best=jumped;}
                        if(arrived.test(jumped)) {
                            ParkourDebugLog.INSTANCE.event("trajectory_lip setup="+preparation+" dir="+dir+" into="+into+" along="+along);
                            return unwind(jumped);
                        }
                        from=jumped;
                    }
                }
            }
        }
        ParkourDebugLog.INSTANCE.event("trajectory_lip_best gap="+bestGap+" pos="+best.x+","+best.y+","+best.z);
        return Collections.emptyList();
    }

    /** Preserve a supported edge setup before searching the airborne turn. */
    private static List<Frame> cornerRoute(Frame start,List<AxisAlignedBB> boxes,
            double gx,double gy,double gz,boolean sprintAllowed,int maxTicks,long deadline,
            Predicate<AxisAlignedBB> safe,Predicate<Frame> arrived) {
        if(!start.ground || start.vy>.42 || start.immediateLaunch) return Collections.emptyList();
        double heading=Math.atan2(gz-start.z,gx-start.x);
        double bestDistance=Double.POSITIVE_INFINITY;
        Frame best=start;
        List<Frame> setups=new ArrayList<>();List<Integer> sides=new ArrayList<>();
        for(int side : new int[]{-1,1}) for(int setupDir : new int[]{4,3,6,8,5,7,9}) {
            Frame setup=start;
            for(int preparation=0;preparation<=24;preparation++) {
                if(preparation%3==0) {setups.add(setup);sides.add(side);}
                setup=step(setup,new Control(heading+side*setupDir*Math.PI/12,true,false,false,true,0),boxes);
                if(!setup.ground || !safe.test(setup.box())) break;
            }
        }
        // Interleave geometrically different setups. Exhausting every control
        // combination at the first stance starves all other sides of the pad.
        for(int turn:new int[]{4,6,8,2,10,12,14,16}) for(int launch:new int[]{0,2,4})
            for(int aim:new int[]{6,9,3,0,8,4,2,7,1,5}) for(int setupIndex=0;setupIndex<setups.size();setupIndex++) {
                    Frame from=setups.get(setupIndex);
                    double angle=heading+sides.get(setupIndex)*aim*Math.PI/36;
                    for(int tick=0;tick<Math.min(50,maxTicks-24);tick++) {
                        if(System.nanoTime()>=deadline || Thread.currentThread().isInterrupted()) {
                            ParkourDebugLog.INSTANCE.event("trajectory_corner_best gap="+bestDistance+" pos="+best.x+","+best.y+","+best.z);
                            return Collections.emptyList();
                        }
                        double desired=tick<turn?angle:Math.atan2(gz-from.z,gx-from.x);
                        boolean forward=true;
                        if(tick>=turn+4) {
                            double lead=from.ground?1.5:4;
                            double dx=gx-from.x-from.vx*lead,dz=gz-from.z-from.vz*lead;
                            desired=Math.atan2(dz,dx);forward=Math.hypot(dx,dz)>.025;
                        }
                        Frame next=step(from,new Control(desired,forward,sprintAllowed&&tick<turn+4,
                                tick==launch&&from.ground),boxes);
                        if(!safe.test(next.box()) || next.y<Math.min(start.y,gy)-1.3
                                || !from.ground && next.ground && next.y<=from.y+.001 && !stableContact(from,next.y,boxes)) break;
                        double distance=Math.hypot(next.x-gx,next.z-gz)+Math.abs(next.y-gy);
                        if(distance<bestDistance) {bestDistance=distance;best=next;}
                        if(arrived.test(next)) {
                            ParkourDebugLog.INSTANCE.event("trajectory_corner setupIndex="+setupIndex+" launch="+launch+" aim="+aim+" turn="+turn);
                            return unwind(next);
                        }
                        from=next;
                    }
        }
        return Collections.emptyList();
    }

    public static List<Frame> search(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            double goalRadius, int maxTicks) {
        return search(start, boxes, gx, gy, gz, sprintAllowed, goalRadius, maxTicks, 2_000_000_000L);
    }

    public static List<Frame> search(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            double goalRadius, int maxTicks, long budgetNanos) {
        return search(start, boxes, gx, gy, gz, sprintAllowed, goalRadius, maxTicks, budgetNanos, box -> true);
    }

    public static List<Frame> search(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            double goalRadius, int maxTicks, long budgetNanos, Predicate<AxisAlignedBB> safe) {
        return search(start, boxes, gx, gy, gz, sprintAllowed, goalRadius, maxTicks, budgetNanos, safe,
                f -> (f.ground && Math.abs(f.y-gy)<.03 && Math.hypot(f.vx,f.vz)<.04
                        || f.onLadder() && f.control != null && f.control.sneak
                            && Math.abs(f.vy + .0784000015258789) < .001 && Math.hypot(f.vx,f.vz)<.01
                            && f.y >= gy && f.y < gy + 1)
                        && Math.hypot(f.x-gx,f.z-gz)<goalRadius);
    }

    public static List<Frame> search(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            double goalRadius, int maxTicks, long budgetNanos, Predicate<AxisAlignedBB> safe,
            Predicate<Frame> arrived) {
        return search(start,boxes,gx,gy,gz,sprintAllowed,goalRadius,maxTicks,budgetNanos,safe,arrived,new BeamSearch());
    }

    /** One invocation's unfinished beam; never retained across world snapshots or jobs. */
    static final class BeamSearch {
        List<Frame> beam;
        Map<String,Frame> unique;
        int tick, nextFrame;
    }

    static List<Frame> search(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            double goalRadius, int maxTicks, long budgetNanos, Predicate<AxisAlignedBB> safe,
            Predicate<Frame> arrived, BeamSearch pending) {
        long deadline = System.nanoTime() + budgetNanos;
        if (budgetNanos > 100_000_000L && !ParkourDebugLog.suppressed()) {
            ParkourDebugLog.INSTANCE.event("trajectory_search_start pos="
                    + start.x + "," + start.y + "," + start.z + " velocity=" + start.vx + "," + start.vy + "," + start.vz
                    + " ground=" + start.ground + " boxes=" + boxes.size() + " goal=" + gx + "," + gy + "," + gz);
            dumpSearchBoxes(start, boxes, gx, gy, gz);
        }
        if (boxes.size() > 32 && !(boxes instanceof CollisionIndex)) boxes = new CollisionIndex(boxes);
        // Axis offset clipping only handles a body starting outside solids.
        // Never turn an invalid snapshot into a route through its ceiling.
        if(hasSupport(start.box().shrink(1e-7),boxes)) return Collections.emptyList();
        List<Frame> reused = replay(start, boxes, gx, gy, gz, sprintAllowed, maxTicks, deadline, safe, arrived);
        if (!reused.isEmpty()) return reused;
        List<Frame> ledge=ledgeRoute(start,boxes,gx,gy,gz,maxTicks,
                Math.min(deadline,System.nanoTime()+budgetNanos/8),safe,arrived);
        if(!ledge.isEmpty()) return remember(ledge,gx,gy,gz);
        List<Frame> ladder = ladderRoute(start, boxes, gx, gy, gz, sprintAllowed,
                maxTicks, Math.min(deadline,System.nanoTime()+budgetNanos/2),safe,arrived);
        if (!ladder.isEmpty()) return remember(ladder,gx,gy,gz);
        // Midair corrections have one tick to solve; do not limit their direct
        // steering to a quarter of that budget before the broad beam search.
        List<Frame> directRoute = directRoute(start, boxes, gx, gy, gz, sprintAllowed,
                maxTicks, !start.ground && !start.inWater() ? deadline
                        : Math.min(deadline,System.nanoTime()+budgetNanos/4), safe, arrived);
        if (!directRoute.isEmpty()) return remember(directRoute,gx,gy,gz);
        List<Frame> corner=cornerRoute(start,boxes,gx,gy,gz,sprintAllowed,maxTicks,
                Math.min(deadline,System.nanoTime()+Math.min(budgetNanos/3,1_000_000_000L)),safe,arrived);
        if(!corner.isEmpty()) return remember(corner,gx,gy,gz);
        if (start.ground && gy < start.y-.5 && !start.immediateLaunch && lineBlocked(start,boxes,gx,gz)) {
            // The graph's launch cell can be too close to the obstacle. Search a
            // supported retreat before the edge setup, retaining every input.
            double away=Math.atan2(start.z-gz,start.x-gx);
            for (int retreatTicks : new int[]{6,9,12}) {
                Frame retreat=start;
                boolean supported=true;
                for (int t=0;t<retreatTicks+6;t++) {
                    retreat=step(retreat,new Control(away,t<retreatTicks,false,false),boxes);
                    if (!retreat.ground || Math.abs(retreat.y-start.y)>.03 || !safe.test(retreat.box())) {
                        supported=false;break;
                    }
                }
                if (!supported || Math.hypot(retreat.x-start.x,retreat.z-start.z)<.6) continue;
                corner=cornerRoute(retreat,boxes,gx,gy,gz,sprintAllowed,maxTicks-retreatTicks-6,
                        Math.min(deadline,System.nanoTime()+budgetNanos/5),safe,arrived);
                if (!corner.isEmpty()) return remember(corner,gx,gy,gz);
            }
        }
        List<Frame> beam = new ArrayList<>();
        beam.add(start);
        if (start.ground && start.vy<=.42 && !start.immediateLaunch) {
            // Seed complete short ground runups before distance-based pruning.
            // A small support requires backing up first, then accelerating to
            // its forward edge; intermediate states all move away from the goal.
            double heading = Math.atan2(gz-start.z,gx-start.x);
            boolean slippery = start.slipperiness.getOrDefault(new BlockPos(start.x,start.y-1,start.z),.6F) > .9F;
            // A sneaking player can stand with the centre just beyond the ice
            // edge. Include the blocks under the body, not only its centre.
            for (double dx : new double[]{-.3,.3}) for (double dz : new double[]{-.3,.3})
                slippery |= start.slipperiness.getOrDefault(new BlockPos(start.x+dx,start.y-1,start.z+dz),.6F) > .9F;
            if (slippery && sprintAllowed) {
                Frame retreat = start;
                double furthestRetreat = 0, maxRunupSpeed = 0;
                int maximumHops = 0, runupStates = 0;
                for (int backTicks=0; backTicks<=64; backTicks++) {
                    // Preserve the entire retreat/head-banger runup as a parent
                    // chain. Distance pruning otherwise removes the backwards
                    // setup before repeated ceiling-limited jumps gain speed.
                    // Sample every retreat tick: on ice a three-tick stride
                    // skips over half a block and can miss the ceiling exit.
                    for (int preRun=0; preRun<=3; preRun++) {
                        Frame hop = retreat;
                        for (int run=0; run<preRun && hop.ground; run++)
                            hop = step(hop,new Control(heading,true,true,false),boxes);
                        if (!hop.ground || !safe.test(hop.box())) continue;
                        int hops = 0;
                        for (int t=0; t<65; t++) {
                            // The final launch must clear the ceiling edge. Keep
                            // short grounded coasts instead of forcing the next
                            // jump to have the same cadence as the tunnel hops.
                            Frame coast = hop;
                            for (int delay=1; delay<=3 && coast.ground; delay++) {
                                coast = step(coast,new Control(heading,true,true,false),boxes);
                                if (!coast.ground || !safe.test(coast.box())) break;
                                Frame launch = step(coast,new Control(heading,true,true,true),boxes);
                                if (safe.test(launch.box())
                                        && (launch.x-start.x)*Math.cos(heading)
                                            +(launch.z-start.z)*Math.sin(heading) > 0) beam.add(launch);
                            }
                            Frame next = step(hop, new Control(heading,true,true,hop.ground), boxes);
                            if (!safe.test(next.box()) || next.y < Math.min(start.y,gy)-1.3
                                    || (!hop.ground && next.ground && next.y <= hop.y+.001 && !stableContact(hop,next.y,boxes))) break;
                            if (hop.ground) hops++;
                            maximumHops = Math.max(maximumHops, hops);
                            maxRunupSpeed = Math.max(maxRunupSpeed,Math.hypot(next.vx,next.vz));
                            runupStates++;
                            // Release jump while airborne so vanilla's jump
                            // cooldown resets before the next ground contact.
                            // Include the accelerating approach and airborne
                            // exit, not only grounded states beyond the original
                            // (often overhanging) standing position.
                            if ((next.x-start.x)*Math.cos(heading)+(next.z-start.z)*Math.sin(heading) > -2)
                                beam.add(next);
                            hop = next;
                        }
                    }
                    Frame back = step(retreat,new Control(heading+Math.PI,true,false,false),boxes);
                    if (!back.ground || !safe.test(back.box())) break;
                    retreat = back;
                    furthestRetreat = Math.max(furthestRetreat, Math.hypot(retreat.x-start.x,retreat.z-start.z));
                }
                ParkourDebugLog.INSTANCE.event("trajectory_head_runup start=" + start.x + "," + start.y + "," + start.z
                        + " retreat=" + furthestRetreat + " hops=" + maximumHops + " maxSpeed=" + maxRunupSpeed
                        + " states=" + runupStates + " seeds=" + beam.size());
            }
            for (int side=-2; side<=2; side++) {
                double angle=heading+side*Math.PI/16;
                Frame back=start;
                for (int retreat=0; retreat<=(slippery?16:5); retreat++) {
                    Frame forward=back;
                    for (int run=0; run<(slippery?24:7); run++) {
                        if (run>0) forward=step(forward,new Control(angle,true,sprintAllowed,false),boxes);
                        if (!forward.ground || !safe.test(forward.box()) || fragileSlowContact(forward)) break;
                        beam.add(forward);
                    }
                    back=step(back,new Control(angle+Math.PI,true,false,false),boxes);
                    if (!back.ground || !safe.test(back.box()) || fragileSlowContact(back)) break;
                }
            }
        }
        pending.beam=beam;
        return resumeBeam(start,boxes,gx,gy,gz,sprintAllowed,maxTicks,deadline,budgetNanos,safe,arrived,pending);
    }

    static List<Frame> resumeBeam(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed, int maxTicks,
            long deadline, long budgetNanos, Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived, BeamSearch pending) {
        double floor = Math.min(start.y, gy) - 1.3;
        double ceiling = start.searchCeiling(gy);
        double gap = Math.hypot(gx-start.x, gz-start.z);
        double radius = Math.max(12, gap+2);
        List<Frame> beam=pending.beam;
        for (int tick=pending.tick; tick<maxTicks; tick++) {
            pending.tick=tick;
            if(pending.unique==null) pending.unique=new HashMap<>();
            Map<String,Frame> unique = pending.unique;
            for (; pending.nextFrame<beam.size(); pending.nextFrame++) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() - deadline >= 0) return Collections.emptyList();
                Frame from=beam.get(pending.nextFrame);
                double direct = Math.atan2(gz-from.z,gx-from.x);
                int modes=(from.ground && from.vy<=.42) || from.inWater()?4:2;
                for (int dir=-1; dir<16; dir++) {
                    double angle=dir<0?direct:dir*Math.PI/8;
                    for (int mode=0;mode<modes;mode++) {
                        if (from.immediateLaunch && mode < 2) continue;
                        boolean sprint=(mode&1)!=0 && sprintAllowed;
                        boolean jump=mode>=2;
                        Control control=new Control(angle,true,sprint,jump);
                        Frame next=step(from,control,boxes);
                        if (fragileSlowContact(next)) continue;
                        if (returnsToChainLaunch(start, next)) continue;
                        if (!from.ground && next.ground && next.y <= from.y+.001 && !stableContact(from, next.y, boxes)) continue;
                        // Vanilla checks liquid occupancy after movement. The union
                        // includes space below an ascending player that was never
                        // occupied, rejecting takeoffs from slabs surrounded by lava.
                        if (!safe.test(next.box())) continue;
                        if (next.y<floor || next.y>ceiling
                                || Math.hypot(next.x-start.x,next.z-start.z)>radius) continue;
                        if (arrived.test(next)) return remember(unwind(next),gx,gy,gz);
                        next.score=score(next,gx,gy,gz,tick);
                        String key=key(next);
                    Frame old=unique.get(key);
                    if(old==null || next.score<old.score
                            || Math.hypot(next.vx,next.vz)>Math.hypot(old.vx,old.vz)+.02) unique.put(key,next);
                    }
                }
                // Back-off runup: move away from goal to gather sprint momentum.
                // Also step sideways. A fence turnaround has a pillar on the
                // line to the next cell, so the launch has to leave toward
                // the open side before turning back in the air.
                if (from.ground && !from.immediateLaunch) {
                    double away = Math.atan2(from.z-gz,from.x-gx);
                    double side = Math.atan2(gz-from.z,gx-from.x) + Math.PI/2;
                    boolean centreBlocked = false;
                    if (gap < 6) {
                        // A .3 probe stops before the pillar between two fence
                        // posts, so the sideways launch was never even tried.
                        double heading = Math.atan2(gz-from.z,gx-from.x);
                        for (double distance=.3; distance<=2.2 && !centreBlocked; distance+=.3) {
                            AxisAlignedBB probe = from.box().offset(
                                    Math.cos(heading)*distance, 0, Math.sin(heading)*distance);
                            centreBlocked = hasSupport(probe, boxes);
                        }
                    }
                    int headings = centreBlocked ? 8 : 3;
                    double toward = Math.atan2(gz-from.z, gx-from.x);
                    if (gap >= 4) headings = 11;
                    for (int bdir=0; bdir<headings; bdir++) {
                        double bAngle = bdir < 3 ? away + (bdir-1)*Math.PI/4
                                : bdir < 8 ? side + (bdir-5)*Math.PI/4
                                : toward + (bdir-9)*Math.PI/8;
                        Control backControl = new Control(bAngle,true,sprintAllowed,false);
                        Frame back = step(from,backControl,boxes);
                        if (!safe.test(back.box()) || fragileSlowContact(back)) continue;
                        if (back.y<floor || back.y>ceiling
                                || Math.hypot(back.x-start.x,back.z-start.z)>radius) continue;
                        if (arrived.test(back)) return unwind(back);
                        back.score=score(back,gx,gy,gz,tick);
                        String bKey=key(back);
                    Frame bOld=unique.get(bKey);
                    if(bOld==null || back.score<bOld.score
                            || Math.hypot(back.vx,back.vz)>Math.hypot(bOld.vx,bOld.vz)+.02) unique.put(bKey,back);
                    }
                }
                if (from.immediateLaunch) continue;
                double idleAngle = from.control == null ? direct : Math.toRadians(from.control.yaw + 90);
                Frame idle=step(from,new Control(idleAngle,false,false,false),boxes);
                if (returnsToChainLaunch(start, idle)) continue;
                if (!from.ground && idle.ground && idle.y <= from.y+.001 && !stableContact(from, idle.y, boxes)) continue;
                if (!safe.test(idle.box()) || fragileSlowContact(idle)) continue;
                idle.score=score(idle,gx,gy,gz,tick);
                if(arrived.test(idle)) return unwind(idle);
                if(idle.y>=floor && Math.hypot(idle.x-start.x,idle.z-start.z)<=12) unique.putIfAbsent(key(idle),idle);
            }
            beam=new ArrayList<>(unique.values());
            beam.sort(Comparator.comparingDouble(f->f.score));
            if (budgetNanos > 100_000_000L && tick % 10 == 0 && !beam.isEmpty()
                    && !ParkourDebugLog.suppressed()) {
                Frame best = beam.get(0);
                ParkourDebugLog.INSTANCE.event("trajectory_search_progress tick=" + tick + " states=" + beam.size()
                        + " ground=" + best.ground
                        + " best=" + best.x + "," + best.y + "," + best.z
                        + " velocity=" + best.vx + "," + best.vy + "," + best.vz
                        + " leftMs=" + Math.max(0, (deadline - System.nanoTime()) / 1_000_000L));
            }
            if(beam.size()>380) {
                // Keep distinct runway positions as well as the closest
                // airborne states. A pure distance beam discards every
                // back-off/pre-hop before it can build useful momentum.
                List<Frame> selected = new ArrayList<>(beam.subList(0, 100));
                // Reserve space for positions around an obstacle before filling
                // the beam with many velocities at the same blocked corner.
                Set<String> positions = new HashSet<>();
                for (Frame frame : beam) {
                    String position = Math.round(frame.x*4)+":"+Math.round(frame.z*4)+":"
                            +Math.round(frame.y*2)+":"+frame.ground;
                    if (positions.add(position) && !selected.contains(frame)) selected.add(frame);
                    if (selected.size() >= 240) break;
                }
                Set<String> runway = new HashSet<>();
                for (Frame frame : beam) {
                    String sector = Math.round(frame.x*2)+":"+Math.round(frame.z*2)+":"
                            +Math.round(frame.y*2)+":"+Math.round(frame.vx*8)+":"
                            +Math.round(frame.vy*4)+":"+Math.round(frame.vz*8)+":"
                            +Math.round(frame.control == null ? 0 : frame.control.yaw / 22.5);
                    if (runway.add(sector) && !selected.contains(frame)) selected.add(frame);
                    if (selected.size() >= 380) break;
                }
                beam = selected;
            }
            pending.beam=beam;
            pending.unique=null;
            pending.nextFrame=0;
            pending.tick=tick+1;
            if(beam.isEmpty()) break;
        }
        pending.beam=null;
        return Collections.emptyList();
    }
    private static String key(Frame f) {
        return Math.round(f.x*8)+":"+Math.round(f.y*10)+":"+Math.round(f.z*8)+":"
                +Math.round(f.vx*16)+":"+Math.round(f.vy*10)+":"+Math.round(f.vz*16)+":"+f.ground+":"+f.airAcceleration+":"+f.collidedHorizontally
                +":"+Math.round(Math.atan2(f.vz,f.vx)*4);
    }
    /** Search a supported retreat and sprint runup, retaining speed at first contact. */
    public static List<Frame> searchSprintLanding(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived) {
        return searchSprintLanding(start, boxes, gx, gy, gz, safe, arrived, Double.NaN);
    }
    public static List<Frame> searchSprintLanding(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived,
            double strafeHeading) {
        if(boxes.size()>32 && !(boxes instanceof CollisionIndex)) boxes=new CollisionIndex(boxes);
        if (!start.ground) return Collections.emptyList();
        long deadline = System.nanoTime() + 1_500_000_000L;
        boolean diagonal = !Double.isNaN(strafeHeading);
        double heading = diagonal ? strafeHeading : Math.atan2(gz-start.z,gx-start.x);
        Control runControl = new Control(heading - (diagonal ? Math.PI/4 : 0), true, true, false, diagonal ? -1 : 0);
        Control jumpControl = new Control(heading - (diagonal ? Math.PI/4 : 0), true, true, true, diagonal ? -1 : 0);
        boolean slowRunway=start.soulSand.contains(new BlockPos(start.x,start.y,start.z));
        Frame retreat = start;
        for (int back=0; back<=24; back++) {
            Frame settled = retreat;
            // Include coasting after retreat. Reversing immediately quantizes
            // the launch positions and can miss a narrow trigger landing.
            for (int coast=0; coast<=8; coast++) {
            Frame run = settled;
            for (int stride=0; stride<=(slowRunway?120:24); stride++) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline)
                    return Collections.emptyList();
                // Approach near the ordinary ground sprint plateau before
                // jumping. A fast-looking airborne landing alone can still
                // originate from an under-accelerated, badly phased takeoff.
                boolean accelerated = run.vx*Math.cos(heading)+run.vz*Math.sin(heading)
                         >= run.walkingSpeed*(slowRunway?.08:1.50);
                Frame air = step(run,jumpControl,boxes);
                for (int tick=0; accelerated && tick<50; tick++) {
                    if (!safe.test(air.box()) || air.collidedHorizontally
                            || air.y<Math.min(start.y,gy)-.1 || air.y>start.searchCeiling(gy)
                            || Math.hypot(air.x-start.x,air.z-start.z)>12) break;
                    if (air.ground) {
                        if (arrived.test(air)) {
                            ParkourDebugLog.INSTANCE.event("trajectory_sprint_runup retreat="+back
                                    +" strides="+stride+" landingVelocity="+air.vx+","+air.vz);
                            return unwind(air);
                        }
                        break;
                    }
                    Frame next=step(air,runControl,boxes);
                    if (next.ground && !stableContact(air,next.y,boxes)) break;
                    air=next;
                }
                Frame next=step(run,runControl,boxes);
                // Jump uses vanilla's onGround flag from vertical resolution,
                // which remains true on the tick horizontal motion leaves an
                // edge. Requiring overlap after that motion discards a legal
                // final takeoff tick and shortens ascending trigger approaches.
                if (!next.ground || next.collidedHorizontally || Math.abs(next.y-start.y)>.03
                        || !safe.test(next.box())) break;
                run=next;
            }
            Frame coastNext=step(settled,new Control(heading,false,false,false),boxes);
            if (!coastNext.ground || Math.abs(coastNext.y-start.y)>.03
                    || !safe.test(coastNext.box()) || !stableContact(coastNext,coastNext.y,boxes)) break;
            settled=coastNext;
            }
            Frame next=step(retreat,new Control(heading+Math.PI,true,false,false),boxes);
            if (!next.ground || next.collidedHorizontally || Math.abs(next.y-start.y)>.03
                    || !safe.test(next.box()) || !stableContact(next,next.y,boxes)) break;
            retreat=next;
        }
        return Collections.emptyList();
    }

    /** A stopped landing must remain inside its target after residual motion decays. */
    public static Predicate<Frame> settledArrival(List<AxisAlignedBB> boxes, double gx, double gy, double gz,
            double radius, Predicate<AxisAlignedBB> safe) {
        final List<AxisAlignedBB> indexed=boxes.size()>32 && !(boxes instanceof CollisionIndex) ? new CollisionIndex(boxes):boxes;
        return frame -> {
            if (!frame.ground || Math.abs(frame.y-gy)>.001 || Math.hypot(frame.x-gx,frame.z-gz)>=radius
                    || Math.hypot(frame.vx,frame.vz)>=.025) return false;
            Frame rest=frame;
            for(int tick=0;tick<20;tick++) {
                rest=step(rest,new Control(0,false,false,false,true,0),indexed);
                if(!rest.ground || Math.abs(rest.y-gy)>.001 || Math.hypot(rest.x-gx,rest.z-gz)>=radius
                        || !safe.test(rest.box())) return false;
            }
            return true;
        };
    }

    /** Fast contact-to-contact control. Reset is horizontal only: gravity, jump
     * height, collisions and support are still simulated without alteration. */
    public static List<Frame> searchFlowLanding(Frame start, List<AxisAlignedBB> boxes,
            net.minecraft.util.math.Vec3d goal, boolean sprintAllowed, Predicate<AxisAlignedBB> safe,
            boolean immediate) {
        List<List<Frame>> routes=flowLandingCandidates(start,boxes,goal,sprintAllowed,safe,immediate,false);
        return routes.isEmpty()?Collections.emptyList():routes.get(0);
    }

    private static List<List<Frame>> flowLandingCandidates(Frame start, List<AxisAlignedBB> boxes,
            net.minecraft.util.math.Vec3d goal, boolean sprintAllowed, Predicate<AxisAlignedBB> safe,
            boolean immediate, boolean transit) {
        if (!start.ground || start.onLadder()) return Collections.emptyList();
        final List<AxisAlignedBB> indexed=boxes.size()>32 && !(boxes instanceof CollisionIndex)
                ? new CollisionIndex(boxes):boxes;
        List<List<Frame>> routes=new ArrayList<>();
        Set<String> contacts=new HashSet<>();
        Frame root=start.restart(immediate);
        Map<Frame,Frame[]> transitions=new java.util.IdentityHashMap<>();
        Map<Frame,Boolean> stableContacts=new java.util.IdentityHashMap<>();
        Set<Frame> unsafe=Collections.newSetFromMap(new java.util.IdentityHashMap<Frame,Boolean>());
        int latestLaunch=Math.hypot(goal.x-start.x,goal.z-start.z)<2?3:12;
        double floor=Math.min(start.y,goal.y)-1.3, ceiling=start.searchCeiling(goal.y);
        // First preserve useful incoming speed. A sharp turn can instead cancel
        // it on the launch tick, without a separate braking/waiting tick.
        for(boolean reset:new boolean[]{false,true}) {
            for(int launch=immediate?0:-1;launch<=(immediate?0:latestLaunch);launch++) {
                for(int brake=0;brake<3;brake++) {
                for(int drive=1;drive<=30;drive++) {
                    Frame from=root;
                    for(int tick=0;tick<60;tick++) {
                        if(Thread.currentThread().isInterrupted()) return Collections.emptyList();
                        double dx=goal.x-from.x,dz=goal.z-from.z;
                        boolean hold=brake!=2 && tick>=drive || Math.hypot(dx,dz)<.075;
                        boolean jump=tick==launch && from.ground;
                        boolean resetHorizontal=hold && brake!=1 || tick==0 && reset || brake==2 && tick==drive;
                        int action=(!hold?1:0)|(jump?2:0)|(resetHorizontal?4:0);
                        Frame[] cached=transitions.computeIfAbsent(from,f->new Frame[8]);
                        Frame next=cached[action];
                        if(next==null) {
                            Control control=new Control(Math.atan2(dz,dx),!hold,sprintAllowed && !hold,
                                    jump,false,0,resetHorizontal);
                            next=step(from,control,indexed);cached[action]=next;
                            if(!safe.test(next.box()) || next.y<floor
                                    || next.y>ceiling || fragileSlowContact(next)
                                    || !from.ground && next.ground && !robustContact(from,next,indexed)) unsafe.add(next);
                        }
                        if(unsafe.contains(next)) break;
                        if(next.ground && Math.abs(next.y-goal.y)<.001
                                && (transit || sameHorizontalCell(next,goal))
                                && Math.hypot(next.x-goal.x,next.z-goal.z)<(transit?.65:.16)) {
                            Boolean stable=stableContacts.get(next);
                            if(stable==null) {
                                Frame rest=step(next,new Control(0,false,false,false,false,0,true),indexed);
                                stable=rest.ground && Math.abs(rest.y-next.y)<.001 && safe.test(rest.box());
                                stableContacts.put(next,stable);
                                if(stable && contacts.add(key(next))) routes.add(unwind(next));
                            }
                            if(stable) break;
                        }
                        // A launch on a disappearing block must be the very next
                        // physical tick; never generate a runup after that contact.
                        if(immediate && tick==0 && next.ground) break;
                        from=next;
                    }
                }
                }
            }
        }
        routes.sort(Comparator.comparingInt(List::size));
        return routes;
    }

    private static boolean sameHorizontalCell(Frame frame, net.minecraft.util.math.Vec3d goal) {
        return MathHelper.floor(frame.x)==MathHelper.floor(goal.x)
                && MathHelper.floor(frame.z)==MathHelper.floor(goal.z);
    }

    private static boolean robustContact(Frame before, Frame contact, List<AxisAlignedBB> boxes) {
        // Vertical collision is resolved before horizontal movement. A contact
        // with only millimetres of overlap fails under harmless client drift.
        AxisAlignedBB feet=new AxisAlignedBB(before.x-.3,contact.y-.002,before.z-.3,
                before.x+.3,contact.y+.002,before.z+.3);
        List<AxisAlignedBB> nearby=boxes instanceof CollisionIndex?((CollisionIndex)boxes).nearby(feet):boxes;
        for(AxisAlignedBB box:nearby) {
            if(Math.abs(box.maxY-contact.y)>.001) continue;
            if(Math.min(feet.maxX,box.maxX)-Math.max(feet.minX,box.minX)>=.06
                    && Math.min(feet.maxZ,box.maxZ)-Math.max(feet.minZ,box.minZ)>=.06) return true;
        }
        return false;
    }

    /** Precompute the whole ordinary-jump stretch before touching its first pad.
     * Every intermediate result ends on actual contact, not a virtual waypoint. */
    public static List<List<Frame>> searchFlow(Frame start, List<AxisAlignedBB> boxes,
            List<net.minecraft.util.math.Vec3d> goals, boolean sprintAllowed, Predicate<AxisAlignedBB> safe) {
        final List<AxisAlignedBB> indexed=boxes.size()>32 && !(boxes instanceof CollisionIndex)
                ? new CollisionIndex(boxes):boxes;
        StringBuilder cacheKey=new StringBuilder();
        if(inputCacheEnabled()) {
            cacheKey.append(new BlockPos(start.x,start.y,start.z));
            for(net.minecraft.util.math.Vec3d goal:goals) cacheKey.append('|').append(routeKey(goal.x,goal.y,goal.z));
            List<List<Control>> cached;
            synchronized(RECENT_FLOWS) {cached=RECENT_FLOWS.get(cacheKey.toString());}
            if(cached!=null) {
                List<List<Frame>> replayed=replayFlow(start,indexed,goals,sprintAllowed,safe,cached);
                if(!replayed.isEmpty()) {
                    ParkourDebugLog.INSTANCE.event("flow_replay segments="+replayed.size());
                    return replayed;
                }
            }
        }
        List<List<List<Frame>>> beam=new ArrayList<>();
        beam.add(new ArrayList<>());
        List<List<Frame>> prefix=Collections.emptyList();
        for(int i=0;i<goals.size();i++) {
            net.minecraft.util.math.Vec3d goal=goals.get(i);
            List<List<List<Frame>>> expanded=new ArrayList<>();
            boolean transit=i+1<goals.size();
            final net.minecraft.util.math.Vec3d exit=transit?goals.get(i+1):goal;
            for(List<List<Frame>> path:beam) {
                Frame from=path.isEmpty()?start:path.get(path.size()-1).get(path.get(path.size()-1).size()-1).restart(true);
                boolean fragile=from.ice.contains(new BlockPos(from.x,from.y-.01,from.z));
                int ticks=0;for(List<Frame> segment:path) ticks+=segment.size();
                for(List<Frame> frames:flowLandingCandidates(from,indexed,goal,sprintAllowed,safe,fragile,transit)) {
                    Frame last=frames.get(frames.size()-1);
                    double dx=exit.x-last.x,dz=exit.z-last.z,length=Math.max(.01,Math.hypot(dx,dz));
                    last.score=ticks+frames.size()+(transit?4*length-16*(last.vx*dx+last.vz*dz)/length:0);
                    List<List<Frame>> extension=new ArrayList<>(path);extension.add(frames);expanded.add(extension);
                }
            }
            if(expanded.isEmpty()) {
                ParkourDebugLog.INSTANCE.event("flow_blocked index="+i+" goal="+goal+" beam="+beam.size());
                // Transit contacts can lie at the back edge of a pad. If the
                // continuation fails, finish the previous leg as a real stop
                // instead of dropping its inherited velocity at that edge.
                for(int end=i-1;end>=0;end--) {
                    net.minecraft.util.math.Vec3d stop=goals.get(end);
                    if(start.ice.contains(new BlockPos(stop.x,stop.y-.01,stop.z))) continue;
                    for(List<List<Frame>> path:beam) {
                        Frame from=end==0?start:path.get(end-1).get(path.get(end-1).size()-1).restart(true);
                        List<Frame> settled=searchFlowLanding(from,indexed,stop,sprintAllowed,safe,
                                from.ice.contains(new BlockPos(from.x,from.y-.01,from.z)));
                        if(!settled.isEmpty()) {
                            List<List<Frame>> stopped=new ArrayList<>(path.subList(0,end));
                            stopped.add(settled);
                            return stopped;
                        }
                    }
                }
                return prefix;
            }
            expanded.sort(Comparator.comparingDouble(path->{
                List<Frame> tail=path.get(path.size()-1);Frame last=tail.get(tail.size()-1);
                return last.score;
            }));
            beam=new ArrayList<>();Set<String> states=new HashSet<>();
            for(List<List<Frame>> path:expanded) {
                List<Frame> tail=path.get(path.size()-1);
                if(states.add(key(tail.get(tail.size()-1)))) beam.add(path);
                if(beam.size()==8) break;
            }
            Frame last=beam.get(0).get(i).get(beam.get(0).get(i).size()-1);
            ParkourDebugLog.INSTANCE.event("flow_contact index="+i+" candidates="+expanded.size()+" pos="+last.x+","+last.y+","+last.z+" speed="+Math.hypot(last.vx,last.vz));
            // Never leave a searched prefix ending on a disappearing ice pad.
            if((!last.ice.contains(new BlockPos(last.x,last.y-.01,last.z)) || !transit)
                    && sameHorizontalCell(last,goal) && Math.hypot(last.x-goal.x,last.z-goal.z)<.16)
                prefix=beam.get(0);
        }
        List<List<Frame>> result=beam.get(0);
        if(inputCacheEnabled() && cacheKey.length()>0) {
            List<List<Control>> controls=new ArrayList<>();
            for(List<Frame> segment:result) {
                List<Control> inputs=new ArrayList<>();
                for(Frame frame:segment) inputs.add(frame.control);
                controls.add(inputs);
            }
            synchronized(RECENT_FLOWS) {RECENT_FLOWS.put(cacheKey.toString(),controls);}
        }
        return result;
    }

    /** Reuse inputs, never cached positions or terrain. A changed landing, ice
     * pad or effect invalidates the replay before any control reaches the game. */
    private static List<List<Frame>> replayFlow(Frame start,List<AxisAlignedBB> boxes,
            List<net.minecraft.util.math.Vec3d> goals,boolean sprintAllowed,Predicate<AxisAlignedBB> safe,
            List<List<Control>> cached) {
        if(cached.size()!=goals.size() || !start.ground || start.onLadder()) return Collections.emptyList();
        List<List<Frame>> result=new ArrayList<>();
        Frame from=start.restart(false);
        for(int i=0;i<cached.size();i++) {
            net.minecraft.util.math.Vec3d goal=goals.get(i);
            Frame legStart=from;
            boolean ice=from.ice.contains(new BlockPos(from.x,from.y-.01,from.z));
            int tick=0;
            for(Control control:cached.get(i)) {
                if(Thread.currentThread().isInterrupted() || control.sprint&&!sprintAllowed
                        || ice&&tick==0&&!control.jump) return Collections.emptyList();
                Frame next=step(from,control,boxes);
                if(!safe.test(next.box()) || fragileSlowContact(next)
                        || next.y<Math.min(legStart.y,goal.y)-1.3 || next.y>legStart.searchCeiling(goal.y)
                        || !from.ground&&next.ground&&!robustContact(from,next,boxes)) return Collections.emptyList();
                from=next;tick++;
            }
            boolean transit=i+1<goals.size();
            Frame rest=step(from,new Control(0,false,false,false,false,0,true),boxes);
            if(!from.ground || Math.abs(from.y-goal.y)>.001
                    || Math.hypot(from.x-goal.x,from.z-goal.z)>=(transit?.65:.16)
                    || !transit&&!sameHorizontalCell(from,goal) || !rest.ground
                    || Math.abs(rest.y-from.y)>.001 || !safe.test(rest.box()) || fragileSlowContact(rest)) return Collections.emptyList();
            result.add(unwind(from));
            from=from.restart(true);
        }
        return result;
    }

    /** Two real landings; never aim past the intermediate pad and call it a handoff. */
    public static List<List<Frame>> searchConnection(Frame start, List<AxisAlignedBB> boxes,
            net.minecraft.util.math.Vec3d middle, net.minecraft.util.math.Vec3d end,
            boolean sprintAllowed, Predicate<AxisAlignedBB> safe) {
        Predicate<Frame> finish=settledArrival(boxes,end.x,end.y,end.z,.18,safe);
        List<Frame> first=searchWithRunup(start,boxes,middle.x,middle.y,middle.z,sprintAllowed,safe,
                settledArrival(boxes,middle.x,middle.y,middle.z,.18,safe));
        if(!first.isEmpty()) {
            Frame settled=first.get(first.size()-1).restart(false);
            List<Frame> second=searchWithRunup(settled,boxes,end.x,end.y,end.z,sprintAllowed,safe,finish);
            if(!second.isEmpty()) return Collections.singletonList(first);
        }
        return searchMomentumConnection(start,boxes,middle,end,sprintAllowed,safe);
    }

    /** Preserve the first landing's velocity; a settled intermediate pad is not a valid result. */
    public static List<List<Frame>> searchMomentumConnection(Frame start, List<AxisAlignedBB> boxes,
            net.minecraft.util.math.Vec3d middle, net.minecraft.util.math.Vec3d end,
            boolean sprintAllowed, Predicate<AxisAlignedBB> safe) {
        if (!sprintAllowed) return Collections.emptyList();
        Predicate<Frame> finish=settledArrival(boxes,end.x,end.y,end.z,.18,safe);
        long deadline=System.nanoTime()+6_000_000_000L;
        List<List<Frame>> found=new ArrayList<>();
        Set<String> tried=new HashSet<>();
        Frame[] nearest={null};
        Predicate<Frame> contact=f->{
            Frame before=f.parent==null?f:f.parent;
            // Vertical contact precedes horizontal displacement in Entity.move.
            // A real last-edge landing may finish beyond the pad and can jump
            // immediately using that tick's onGround flag.
            if(!f.ground || Math.abs(f.y-middle.y)>.001 || Math.abs(before.x-middle.x)>.799 || Math.abs(before.z-middle.z)>.799
                    || (f.vx*(end.x-middle.x)+f.vz*(end.z-middle.z))<=.02
                    || System.nanoTime()>=deadline || !tried.add(key(f))) return false;
            if(nearest[0]==null || Math.hypot(f.x-end.x,f.z-end.z)<Math.hypot(nearest[0].x-end.x,nearest[0].z-end.z)) nearest[0]=f;
            Frame launch=f.restart(true);
            List<Frame> second=search(launch,boxes,end.x,end.y,end.z,sprintAllowed,.18,100,
                    Math.min(80_000_000L,deadline-System.nanoTime()),safe,finish);
            if(second.isEmpty()) return false;
            found.add(second);
            return true;
        };
        // Generate the runways before spending time on continuation searches.
        // An 80 ms continuation inside every early stride exhausts the runway
        // budget before slow ground reaches the late launch positions at all.
        List<Frame> contacts=new ArrayList<>();
        Set<String> contactKeys=new HashSet<>();
        Predicate<Frame> collect=f->{
            Frame before=f.parent==null?f:f.parent;
            if(f.ground && Math.abs(f.y-middle.y)<.001 && Math.abs(before.x-middle.x)<.799
                    && Math.abs(before.z-middle.z)<.799 && contactKeys.add(key(f))) contacts.add(f);
            return false;
        };
        // The incoming heading and outgoing jump impulse need not aim at cell
        // centres. Explore both sides of the pad without changing its contact
        // predicate; otherwise a useful runway can be missed by a few degrees.
        double approachAngle=Math.atan2(middle.z-start.z,middle.x-start.x);
        for(double lateral:new double[]{0,.35,-.35,.65,-.65}) {
            if(System.nanoTime()>=deadline-500_000_000L) break;
            searchSprintLanding(start,boxes,middle.x-Math.sin(approachAngle)*lateral,middle.y,
                    middle.z+Math.cos(approachAngle)*lateral,safe,collect);
        }
        contacts.sort(Comparator.comparingDouble(f->Math.hypot(f.x-end.x,f.z-end.z)-6*Math.hypot(f.vx,f.vz)));
        List<Frame> approach=Collections.emptyList();
        for(Frame candidate:contacts) {
            if(System.nanoTime()>=deadline) break;
            if(contact.test(candidate)) { approach=unwind(candidate); break; }
        }
        if(approach.isEmpty() && System.nanoTime()<deadline)
            approach=search(start,boxes,middle.x+.5*Math.signum(end.x-middle.x),middle.y,
                    middle.z+.5*Math.signum(end.z-middle.z),sprintAllowed,1,120,
                    deadline-System.nanoTime(),safe,contact);
        if(approach.isEmpty() || found.isEmpty()) {
            Frame n=nearest[0];
            ParkourDebugLog.INSTANCE.event("trajectory_connection_failed middle="+middle+" attempts="+tried.size()+" nearest="
                    +(n==null?"none":n.x+","+n.y+","+n.z+" motion="+n.vx+","+n.vz));
            return Collections.emptyList();
        }
        return java.util.Arrays.asList(approach,found.get(found.size()-1));
    }

    public static List<Frame> searchMomentumRoute(Frame start, List<AxisAlignedBB> boxes,
            net.minecraft.util.math.Vec3d middle, net.minecraft.util.math.Vec3d end,
            boolean sprintAllowed, Predicate<AxisAlignedBB> safe) {
        List<List<Frame>> parts=searchMomentumConnection(start,boxes,middle,end,sprintAllowed,safe);
        if(parts.size()!=2) return Collections.emptyList();
        List<Frame> route=new ArrayList<>(parts.get(0));
        route.addAll(parts.get(1));
        return route;
    }

    /** Keep the landing momentum when using a neighbouring support as a runup. */
    public static List<Frame> searchWithRunup(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, boolean sprintAllowed,
            Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived) {
        // All runway candidates share one budget; a separate multi-second
        // budget per support can otherwise leave the player waiting for minutes.
        long deadline = System.nanoTime() + 8_000_000_000L;
        if(boxes.size()>32 && !(boxes instanceof CollisionIndex)) boxes=new CollisionIndex(boxes);
        // A same-height gap can be geometrically reachable while still being
        // impossible from a standing jump.  Do not accept that cheap direct
        // candidate before looking for a supported runway: otherwise the
        // executor starts braking/turning at the gap and reaches the apex
        // without enough horizontal speed for the real landing.
        double targetDistance = Math.hypot(gx - start.x, gz - start.z);
        boolean needsRunup = start.ground && sprintAllowed && !start.immediateLaunch
                && gy <= start.y + .5 && targetDistance > 3.0;
        BeamSearch pending = new BeamSearch();
        List<Frame> direct = needsRunup ? Collections.emptyList() : search(start, boxes, gx, gy, gz,
                sprintAllowed, .18, 150, 2_000_000_000L, safe, arrived, pending);
        if (!direct.isEmpty()) return direct;
        if (gy <= start.y + .5 && lineBlocked(start, boxes, gx, gz)) {
            // The centre line hits a wall. cornerRoute stays on that line, so
            // search() caps it at one second and leaves the rest for the beam.
            return search(start, boxes, gx, gy, gz, sprintAllowed, .18, 150,
                    Math.min(deadline, System.nanoTime()+6_000_000_000L), safe, arrived);
        }
        if (start.ground && sprintAllowed && !start.immediateLaunch) {
            // Search the entire supported runway before pruning by distance.
            // A long descending jump may need a late launch after more than
            // the beam's seven seeded ground strides. Settle only after making
            // actual contact with the intended elevated destination.
            final List<AxisAlignedBB> runupBoxes = boxes;
            List<List<Frame>> finishes = new ArrayList<>();
            Set<String> triedLandings = new HashSet<>();
            searchSprintLanding(start, boxes, gx, gy, gz, safe, landing -> {
                if (Math.abs(landing.y-gy)>.03 || Math.hypot(landing.x-gx,landing.z-gz)>.85
                        || !triedLandings.add(key(landing))) return false;
                List<Frame> finish = arrived.test(landing) ? unwind(landing)
                        : directRoute(landing,runupBoxes,gx,gy,gz,true,45,
                                Math.min(deadline,System.nanoTime()+30_000_000L),safe,arrived);
                if (finish.isEmpty()) return false;
                finishes.add(finish);
                return true;
            });
            if (!finishes.isEmpty()) return finishes.get(0);
            List<Frame> hopping = searchPreHop(start, boxes, gx, gy, gz,
                    Math.min(deadline, System.nanoTime() + 1_000_000_000L), safe, arrived);
            if (!hopping.isEmpty()) return hopping;
        }
        // A same-height fence gap can be blocked on the centre line.
        // The straight pre-hop never steps off that line; the beam search
        // can leave sideways and turn back before landing.
        if (gy <= start.y + .5) {
            if (!direct.isEmpty()) return direct;
            return search(start, boxes, gx, gy, gz, sprintAllowed, .55, 80,
                    4_000_000_000L, safe, arrived);
        }
        for (AxisAlignedBB support : boxes) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) break;
            double x = (support.minX + support.maxX) / 2;
            double z = (support.minZ + support.maxZ) / 2;
            double distance = Math.hypot(x-start.x,z-start.z);
            if (Math.abs(support.maxY-start.y) > .03 || distance < 2 || distance > 5
                    || support.maxX-support.minX < .8 || support.maxZ-support.minZ < .8
                    || Math.hypot(x-gx,z-gz) > 5.5) continue;
            AxisAlignedBB body = new AxisAlignedBB(x-.3,support.maxY+.001,z-.3,
                    x+.3,support.maxY+1.8,z+.3);
            if (!safe.test(body) || boxes.stream().anyMatch(box -> box.intersects(body))) continue;
            ParkourDebugLog.INSTANCE.event("trajectory_runup_try support=" + x + "," + support.maxY + "," + z);
            List<Frame> approach = search(start, boxes, x, support.maxY, z, sprintAllowed,
                    .4, 80, Math.min(1_000_000_000L, Math.max(0L, deadline-System.nanoTime())), safe,
                    f -> f.ground && Math.abs(f.y-support.maxY)<.03 && Math.hypot(f.x-x,f.z-z)<.4);
            if (approach.isEmpty()) continue;
            Frame landing = approach.get(approach.size()-1);
            if (System.nanoTime() >= deadline) break;
            List<Frame> combined = search(landing, boxes, gx, gy, gz, sprintAllowed,
                    .18, 100, Math.min(2_000_000_000L, deadline-System.nanoTime()), safe, arrived);
            ParkourDebugLog.INSTANCE.event("trajectory_runup_result frames=" + combined.size()
                    + " landingVelocity=" + landing.vx + "," + landing.vz);
            // Parent links include the approach, so the result is one uninterrupted route.
            if (!combined.isEmpty()) return combined;
        }
        if(pending.beam!=null && !Thread.currentThread().isInterrupted() && System.nanoTime()<deadline) {
            // Exhausting the direct sub-budget is not an unreachable edge. Continue
            // its actual frontier after alternatives, within the original total budget.
            ParkourDebugLog.INSTANCE.event("trajectory_search_resume tick="+pending.tick+" nextFrame="+pending.nextFrame
                    +" frontier="+pending.beam.size()+" remainingMs="+(deadline-System.nanoTime())/1_000_000L);
            return resumeBeam(start,boxes,gx,gy,gz,sprintAllowed,150,deadline,2_000_000_000L,safe,arrived,pending);
        }
        return Collections.emptyList();
    }
    /** True when the body hits something while walking the line to the goal. */
    private static boolean lineBlocked(Frame start, List<AxisAlignedBB> boxes, double gx, double gz) {
        double heading = Math.atan2(gz - start.z, gx - start.x);
        for (double distance = .3; distance <= 2.2; distance += .3) {
            AxisAlignedBB probe = start.box().offset(
                    Math.cos(heading) * distance, 0, Math.sin(heading) * distance);
            if (hasSupport(probe, boxes)) return true;
        }
        return false;
    }
    /** Build momentum with a jump on the runway, then jump again on contact. */
    private static List<Frame> searchPreHop(Frame start, List<AxisAlignedBB> boxes,
            double gx, double gy, double gz, long deadline,
            Predicate<AxisAlignedBB> safe, Predicate<Frame> arrived) {
        if (boxes.size() > 32 && !(boxes instanceof CollisionIndex)) boxes = new CollisionIndex(boxes);
        double heading = Math.atan2(gz-start.z, gx-start.x);
        if (Math.hypot(start.vx,start.vz) < .03) {
            // A speculative immediate jump must not mutate the input of the
            // later runup/beam search when this attempt fails.
            Frame launch = start.restart(true);
            launch.parent = start.parent;
            launch.control = start.control;
            List<Frame> standing = directRoute(launch,boxes,gx,gy,gz,true,70,
                    Math.min(deadline,System.nanoTime()+2_000_000_000L),safe,arrived);
            if (!standing.isEmpty()) {
                ParkourDebugLog.INSTANCE.event("trajectory_pre_hop retreat=0 coast=0 strides=0 contacts=0"
                        +" landing="+start.x+","+start.y+","+start.z+" speed=0");
                return standing;
            }
        }
        Set<String> tried = new HashSet<>();
        int contacts = 0;
        for (int side : new int[]{0,-1,1,-2,2,-3,3}) {
            double angle = heading + side*Math.PI/32;
            Frame retreat = start;
            for (int back=0; back<=30; back++) {
                Frame settled = retreat;
                for (int coast=0; coast<=4; coast++) {
                    Frame run = settled;
                    for (int stride=0; stride<=12; stride++) {
                        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
                            return Collections.emptyList();
                        Frame air = step(run,new Control(angle,true,true,true),boxes);
                        for (int tick=0; tick<24; tick++) {
if (!safe.test(air.box()) || air.collidedHorizontally || fragileSlowContact(air)
                                    || air.y < start.y-.2 || air.y > start.searchCeiling(gy)) break;
                            if (air.ground) {
                                // Soul sand stands 0.125 below the feet block. A contact
                                // that low still carries the runway into the next jump.
                                if (air.y<=start.y+.03 && air.y>=start.y-.2 && Math.hypot(air.vx,air.vz)>.23
                                        && Math.hypot(air.x-gx,air.z-gz)<Math.hypot(start.x-gx,start.z-gz)
                                        && tried.add(key(air))) {
                                    contacts++;
                                    air.immediateLaunch = true;
                                    List<Frame> result = directRoute(air,boxes,gx,gy,gz,true,70,
                                            Math.min(deadline,System.nanoTime()+20_000_000L),safe,arrived);
                                    if (!result.isEmpty()) {
                                        ParkourDebugLog.INSTANCE.event("trajectory_pre_hop retreat="+back
                                                +" coast="+coast+" strides="+stride+" contacts="+contacts
                                                +" landing="+air.x+","+air.y+","+air.z
                                                +" speed="+Math.hypot(air.vx,air.vz));
                                        return result;
                                    }
                                }
                                break;
                            }
                            Frame next=step(air,new Control(angle,true,true,false),boxes);
                            if (next.ground && !stableContact(air,next.y,boxes)) break;
                            air=next;
                        }
                        Frame next=step(run,new Control(angle,true,true,false),boxes);
                        if (!next.ground || next.collidedHorizontally || Math.abs(next.y-start.y)>.03
                                || !safe.test(next.box()) || fragileSlowContact(next)) break;
                        run=next;
                    }
                    Frame next=step(settled,new Control(angle,false,false,false),boxes);
                    if (!next.ground || !safe.test(next.box()) || !stableContact(next,next.y,boxes)) break;
                    settled=next;
                }
                Frame next=step(retreat,new Control(angle+Math.PI,true,false,false),boxes);
                if (!next.ground || next.collidedHorizontally || Math.abs(next.y-start.y)>.03
                        || !safe.test(next.box()) || !stableContact(next,next.y,boxes)) break;
                retreat=next;
            }
        }
        ParkourDebugLog.INSTANCE.event("trajectory_pre_hop_failed contacts="+contacts);
        return Collections.emptyList();
    }
    private static boolean returnsToChainLaunch(Frame start, Frame next) {
        // A continuous ice handoff must leave its pad in one jump. The map
        // may remove that pad after contact, so a second landing is not a runway.
        return start.immediateLaunch && next.ground && Math.abs(next.y-start.y)<.03
                && Math.hypot(next.x-start.x,next.z-start.z)<1.5;
    }

    private static boolean stableContact(Frame frame, double landingY, List<AxisAlignedBB> boxes) {
        AxisAlignedBB body = frame.box();
        if (boxes instanceof CollisionIndex) boxes = ((CollisionIndex)boxes).nearby(new AxisAlignedBB(
                body.minX, landingY-.001, body.minZ, body.maxX, landingY+.001, body.maxZ));
        for (AxisAlignedBB box : boxes) {
            // Vanilla resolves vertical collisions before moving horizontally.
            // Require overlap at the start of the landing tick, not after it.
            if (Math.abs(box.maxY - landingY) < .001
                    && Math.min(body.maxX, box.maxX) - Math.max(body.minX, box.minX) > .025
                    && Math.min(body.maxZ, box.maxZ) - Math.max(body.minZ, box.minZ) > .025) return true;
        }
        return false;
    }

    /** Simulate all contacts and the final collision-aware rebound. */
    public static List<Frame> searchSlimeChain(Frame start, List<AxisAlignedBB> boxes,
            BlockPos launchBlock, BlockPos first, BlockPos second, BlockPos destination, Predicate<AxisAlignedBB> safe) {
        if (!start.ground) return Collections.emptyList();
        // These routes never sneak on contact. Keep the material response on
        // every subsequent frame, including tiny bounces and the final search.
        final int requiredContacts = second == null ? 1 : 2;
        if (boxes.size()>32 && !(boxes instanceof CollisionIndex)) boxes=new CollisionIndex(boxes);
        final Predicate<Frame> settled = settledArrival(boxes,destination.getX()+.5,
                destination.getY(),destination.getZ()+.5,.18,safe);
        double best=Double.POSITIVE_INFINITY;
        List<Frame> result=Collections.emptyList();
        int firstContacts=0,secondContacts=0;
        double strongestSecondImpact=0, closestFinish=Double.POSITIVE_INFINITY;
        long deadline=System.nanoTime()+8_000_000_000L;
        Set<String> reboundStates=new HashSet<>();
        for (double launch : new double[]{.5,.65,.8,.95})
            for (double offsetX : new double[]{0,-.3,.3})
                for (double offsetZ : new double[]{0,-.3,.3})
                    for (int turn=0;turn<=12;turn+=2)
                         for (double lead : new double[]{1,3,5,7}) {
            if(Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline) break;
            Frame from=start;
            int contacts=0;
            boolean airborne=false;
            for(int tick=0;tick<150;tick++) {
                BlockPos pad=contacts==0 || second==null?first:second;
                BlockPos following=contacts==0 && second!=null?second:destination;
                double tx=pad.getX()+.5+(contacts==0?offsetX:0);
                double tz=pad.getZ()+.5+(contacts==0?offsetZ:0);
                boolean steerNext=contacts>=requiredContacts;
                if(contacts<requiredContacts && from.vy<0) {
                    double y=from.y,vy=from.vy;
                    int remaining=0;
                    while(y>pad.getY()+1 && remaining<=turn) {
                        y+=vy;vy=(vy-.08)*.98;remaining++;
                    }
                    steerNext=remaining<=turn;
                }
                if(steerNext) {tx=following.getX()+.5;tz=following.getZ()+.5;}
                double gain=contacts>=requiredContacts?5:lead;
                double ax=tx-from.x-from.vx*gain,az=tz-from.z-from.vz*gain;
                double progress=Math.abs(first.getX()-start.x)>=Math.abs(first.getZ()-start.z)
                        ? Math.signum(first.getX()-start.x)*(from.x-launchBlock.getX()-.5)
                        : Math.signum(first.getZ()-start.z)*(from.z-launchBlock.getZ()-.5);
                Control control=new Control(Math.atan2(az,ax),Math.hypot(ax,az)>.025,true,
                        !airborne && from.ground && progress>=launch);
                Frame next=step(from,control,boxes);
                if(!next.ground) airborne=true;
                if(next.ground && from.vy<0 && contacts<requiredContacts
                        && Math.abs(next.y-pad.getY()-1)<.001
                        && Math.floor(next.x)==pad.getX() && Math.floor(next.z)==pad.getZ()) {
                    // The shared physics step already applies the material's
                    // bounce and low-impact horizontal damping.
                    contacts++;
                    if(contacts==1) firstContacts++; else secondContacts++;
                    if(contacts==2) strongestSecondImpact=Math.max(strongestSecondImpact,-from.vy);
                    // The second landing fixes both outgoing speed and heading.
                    // A fixed centre-seeking brake can discard a reachable final
                    // platform, so search its remaining airborne inputs separately.
                    if(contacts==requiredContacts && safe.test(next.box()) && !next.collidedHorizontally
                            && reboundStates.add(key(next))) {
                        List<Frame> finish=search(next,boxes,destination.getX()+.5,
                                destination.getY(),destination.getZ()+.5,true,.2,45,
                                Math.min(100_000_000L,Math.max(0,deadline-System.nanoTime())),safe,
                                settled);
                        if(!finish.isEmpty()) {
                            ParkourDebugLog.INSTANCE.event("slime_chain_refined frames="+finish.size());
                            return finish;
                        }
                    }
                }
                if(!safe.test(next.box()) || next.collidedHorizontally
                          || next.y<Math.min(first.getY(),second==null?first.getY():second.getY())+.5
                          || next.y>Math.max(start.y,destination.getY())+3) break;
                if(contacts>=requiredContacts && next.y>=destination.getY())
                    closestFinish=Math.min(closestFinish,Math.hypot(next.x-destination.getX()-.5,next.z-destination.getZ()-.5));
                if(contacts>=requiredContacts && settled.test(next)) {
                    double quality=Math.hypot(next.x-destination.getX()-.5,next.z-destination.getZ()-.5)
                            + Math.hypot(next.vx,next.vz)*2 + tick*.001;
                    if(quality<best) {best=quality;result=unwind(next);}
                    break;
                }
                from=next;
            }
        }
        ParkourDebugLog.INSTANCE.event("slime_chain_search first="+firstContacts+" second="+secondContacts+" frames="+result.size()
                +" secondImpact="+strongestSecondImpact+" closestFinish="+closestFinish);
        return result;
    }
    private static double score(Frame f,double gx,double gy,double gz,int tick) {
        double dx=gx-f.x,dz=gz-f.z,dist=Math.hypot(dx,dz);
        double closing=dist>.01?(dx*f.vx+dz*f.vz)/dist:0;
        // Reward horizontal speed whether or not it is closing: a back-off runup
        // first moves away to gather sprint momentum, so raw speed is as useful
        // as proximity while the frame is still on solid ground.
        double speed=Math.hypot(f.vx,f.vz);
        // On the ground a fence turnaround must leave the goal line to clear
        // the pillar. Penalising that retreat leaves only the blocked step.
        double ground = f.ground ? speed*1.5 + Math.max(0, closing)*2 : closing*2;
        return dist - ground + Math.max(0,gy-f.y)*3
                + (dist<.7?speed*2:0) + tick*.008 + f.turnCost;
    }
    private static List<Frame> unwind(Frame end) {
        LinkedList<Frame> result=new LinkedList<>();
        for(Frame f=end;f.parent!=null;f=f.parent) result.addFirst(f);
        return result;
    }
    private static boolean fragileSlowContact(Frame frame) {
        // A millimetre of position error must not change soul-sand damping by
        // 2.5x. Choose an earlier launch or a clearly separated contact instead
        // of planning a grounded runup on that discontinuity.
        if (!frame.ground) return false;
        AxisAlignedBB body = frame.box();
        for (BlockPos block : frame.soulSand) {
            if (block.getY()+1 <= body.minY+.001 || block.getY() >= body.maxY-.001) continue;
            double x = Math.min(body.maxX-.001,block.getX()+1)-Math.max(body.minX+.001,block.getX());
            double z = Math.min(body.maxZ-.001,block.getZ()+1)-Math.max(body.minZ+.001,block.getZ());
            if (x > -.015 && z > -.015 && (Math.abs(x) < .015 || Math.abs(z) < .015)) return true;
        }
        return false;
    }

    /** Revalidate remaining inputs after a server correction; never move the player to a prediction. */
    public static List<Frame> replaySlimeRemainder(Frame start, List<Frame> plan, int from,
            List<AxisAlignedBB> boxes, BlockPos destination, Predicate<AxisAlignedBB> safe) {
        if (from < 0 || from >= plan.size() || !safe.test(start.box())) return Collections.emptyList();
        if (boxes.size()>32 && !(boxes instanceof CollisionIndex)) boxes=new CollisionIndex(boxes);
        Frame frame=start.restart(false);
        Frame corrected=frame.restart(false);
        List<Frame> result=new ArrayList<>();
        for (int i=from;i<plan.size();i++) {
            frame=step(frame,plan.get(i).control,boxes);
            if (!safe.test(frame.box())) {result.clear();break;}
            result.add(frame);
        }
        Predicate<Frame> settled=settledArrival(boxes,destination.getX()+.5,destination.getY(),destination.getZ()+.5,.18,safe);
        if (!result.isEmpty() && settled.test(frame)) return result;
        List<Frame> recovery=slimeRecovery(corrected,boxes,destination,safe,settled);
        if(!recovery.isEmpty()) return recovery;
        // Repeated server corrections can change the required landing timing.
        // Search from the observed airborne state within one client-tick budget.
        return search(corrected,boxes,destination.getX()+.5,destination.getY(),destination.getZ()+.5,
                true,.18,100,35_000_000L,safe,settled);
    }

    private static List<Frame> slimeRecovery(Frame start,List<AxisAlignedBB> boxes,BlockPos dest,
            Predicate<AxisAlignedBB> safe,Predicate<Frame> settled) {
        double dx=dest.getX()+.5-start.x,dz=dest.getZ()+.5-start.z;
        double length=Math.hypot(dx,dz);
        List<BlockPos> pads=new ArrayList<>();
        for(BlockPos pad:start.slimeBlocks) {
            double x=pad.getX()+.5-start.x,z=pad.getZ()+.5-start.z;
            double along=(x*dx+z*dz)/Math.max(.001,length);
            if(pad.getY()+1==dest.getY() && along>(start.vy<0?-.75:.15) && along<length
                    && Math.abs(x*dz-z*dx)/Math.max(.001,length)<.75
                    && !((start.ground || start.vy>0) && Math.floor(start.x)==pad.getX()
                        && Math.floor(start.z)==pad.getZ())) pads.add(pad);
        }
        pads.sort(java.util.Comparator.comparingDouble(p -> (p.getX()+.5-start.x)*dx+(p.getZ()+.5-start.z)*dz));
        long deadline=System.nanoTime()+30_000_000L;
        for(double lead:new double[]{2,3,4,5,6,8}) {
            if(System.nanoTime()>=deadline) break;
            Frame f=start.restart(false);
            List<Frame> route=new ArrayList<>();
            int padIndex=0;
            for(int tick=0;tick<120;tick++) {
                if(f.ground && new BlockPos(f.x,f.y,f.z).equals(dest)) {
                    List<Frame> finish=search(f.restart(false),boxes,dest.getX()+.5,dest.getY(),dest.getZ()+.5,
                            true,.18,50,Math.max(1,deadline-System.nanoTime()),safe,settled);
                    if(!finish.isEmpty()) {route.addAll(finish);return route;}
                    break;
                }
                BlockPos target=padIndex<pads.size()?pads.get(padIndex).up():dest;
                double tx=target.getX()+.5-f.x,tz=target.getZ()+.5-f.z;
                double heading=Math.atan2(tz-f.vz*lead,tx-f.vx*lead);
                boolean jump=f.ground && f.vy>0 && f.vy<.42;
                Frame next=step(f,new Control(heading,Math.hypot(tx,tz)>.035 || Math.hypot(f.vx,f.vz)>.01,true,jump),boxes);
                if(!safe.test(next.box()) || next.y<dest.getY()-.5) break;
                if(padIndex<pads.size() && next.ground && f.vy<0
                        && new BlockPos(next.x,next.y,next.z).equals(target)) padIndex++;
                route.add(next);f=next;
            }
        }
        return Collections.emptyList();
    }

    public static List<Frame> searchLevelSlimeChain(Frame start,List<AxisAlignedBB> boxes,
            BlockPos src,BlockPos dest,Predicate<AxisAlignedBB> safe) {
        double length=Math.hypot(dest.getX()-src.getX(),dest.getZ()-src.getZ());
        double heading=Math.atan2(dest.getZ()-src.getZ(),dest.getX()-src.getX());
        double furthest=0;
        for(int retreat=0;retreat<=14;retreat++) for(double launch:new double[]{.8,.7,.9,.6,.5}) {
        Frame frame=start.restart(false);
        List<Frame> frames=new ArrayList<>();
        boolean setup=true;
        for(int tick=0;tick<retreat+8;tick++) {
            frame=step(frame,new Control(heading+Math.PI,tick<retreat,false,false),boxes);
            if(!frame.ground || !safe.test(frame.box())) {setup=false;break;}
            frames.add(frame);
        }
        if(!setup) continue;
        boolean departed=false;
        for(int tick=0;tick<160;tick++) {
            double progress=((frame.x-src.getX()-.5)*(dest.getX()-src.getX())
                    +(frame.z-src.getZ()-.5)*(dest.getZ()-src.getZ()))/length;
            furthest=Math.max(furthest,progress);
            if(!frame.ground && progress>.1) departed=true;
            if(departed && frame.ground && Math.floor(frame.x)==dest.getX() && Math.floor(frame.z)==dest.getZ()) {
                List<Frame> finish=searchWithRunup(frame.restart(false),boxes,
                        dest.getX()+.5,dest.getY(),dest.getZ()+.5,true,safe,
                        settledArrival(boxes,dest.getX()+.5,dest.getY(),dest.getZ()+.5,.18,safe));
                if(finish.isEmpty()) break;
                frames.addAll(finish);
                ParkourDebugLog.INSTANCE.event("slime_level retreat="+retreat+" launch="+launch+" frames="+frames.size());
                return frames;
            }
            boolean jump=frame.ground && (!departed && progress>=launch || departed && frame.vy>0 && frame.vy<.42);
            frame=step(frame,new Control(Math.atan2(dest.getZ()+.5-frame.z,dest.getX()+.5-frame.x),true,true,jump),boxes);
            if(frame.y<src.getY()-.5 || !safe.test(frame.box())) break;
            frames.add(frame);
        }
        }
        ParkourDebugLog.INSTANCE.event("slime_level_failed furthest="+furthest+" length="+length);
        return Collections.emptyList();
    }

    public static Frame step(Frame f,Control c,List<AxisAlignedBB> boxes) {
        Frame n=new Frame(); n.parent=f;n.control=c;
        n.bodyHeight=f.bodyHeight;
        List<AxisAlignedBB> allBoxes=boxes;
        n.slipperiness = f.slipperiness;
        n.ice = f.ice;
        n.soulSand = f.soulSand;
        n.slimeBlocks = f.slimeBlocks;
        n.climbable = f.climbable;
        n.water = f.water;
        n.lava = f.lava;
        n.ladders = f.ladders;
        n.walkingSpeed = f.walkingSpeed;
        n.jumpBoost = f.jumpBoost;
        n.jumpBoostTicks = Math.max(0, f.jumpBoostTicks - 1);
        n.turnCost = f.turnCost;
        if (f.control != null) {
            double turn = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(c.yaw - f.control.yaw));
            n.turnCost += .04 * turn / 90.0;
        }
        // Use vanilla's float angles and lookup table: tiny drift changes narrow-edge collisions.
        float angle=c.yaw*.017453292F;
        float sin=MathHelper.sin(angle),cos=MathHelper.cos(angle);
        double vx=c.resetHorizontal?0:f.vx,vy=f.vy,vz=c.resetHorizontal?0:f.vz;
        // onLivingUpdate cancels a requested sprint using the previous
        // movement's collision flag, before this tick's jump and travel.
        boolean sprint = c.sprint && c.forward && !c.sneak && !f.collidedHorizontally;
        if(Math.abs(vx)<.003) vx=0;
        if(Math.abs(vy)<.003) vy=0;
        if(Math.abs(vz)<.003) vz=0;
        boolean swimming=f.inWater();
        boolean inLava=!swimming && f.inLava();
        boolean inLiquid=swimming || inLava;
        if(inLiquid && c.jump) vy+=.03999999910593033;
        else if(f.ground && c.jump) {
            vy=.41999998688697815 + (f.jumpBoostTicks > 0 ? f.jumpBoost : 0);
            if(sprint) {vx-=sin*.2F;vz+=cos*.2F;}
        }
        float drag = f.ground ? f.slipperiness.getOrDefault(new BlockPos(f.x, f.y - 1, f.z), .6F) * .91F : .91F;
        float groundFactor = .16277136F / (drag * drag * drag);
        double accel=f.ground?(float)(f.walkingSpeed*(sprint?1.300000011920929:1.0))*groundFactor: f.airAcceleration;
        if(inLiquid) {drag=inLava?.5F:.8F;accel=.02F;}
        // Vanilla damps both input axes before normalizing their combined
        // length. Sprint-jump impulse follows yaw, whereas travel follows the
        // forward/strafe vector; these directions differ for diagonal input.
        float forward = c.forward ? (c.sneak ? .3F : 1) * .98F : 0;
        float strafe = c.strafe * (c.sneak ? .3F : 1) * .98F;
        float inputLength = strafe*strafe + forward*forward;
        if(inputLength>=1.0E-4F) {
            float scale=(float)accel/Math.max(1F,MathHelper.sqrt(inputLength));
            strafe*=scale;forward*=scale;
            vx += strafe*cos - forward*sin;
            vz += forward*cos + strafe*sin;
        }
        if (f.ground && f.soulSand.contains(new BlockPos(f.x, f.y - 1, f.z))) {
            vx *= .4;
            vz *= .4;
        }
        AxisAlignedBB box=f.box();
        // EntityLivingBase.travel clamps BEFORE moving. Sneak arrests falling,
        // never an ascending jump. Only feet-centre block membership counts.
        if (!inLiquid && f.onLadder()) {
            vx = Math.max(-.15000000596046448, Math.min(.15000000596046448, vx));
            vz = Math.max(-.15000000596046448, Math.min(.15000000596046448, vz));
            vy = Math.max(-.15, vy);
            if (c.sneak && vy < 0) vy = 0;
        }
        // Safe-walk limits Entity.move's displacement, not the motion fields
        // carried into the next tick. A real wall collision still zeros motion.
        double motionX = vx, motionZ = vz;
        if (c.sneak && f.ground) {
            while (vx != 0 && !hasSupport(box.offset(vx, -.6, 0), boxes)) vx = towardZero(vx, .05);
            while (vz != 0 && !hasSupport(box.offset(0, -.6, vz), boxes)) vz = towardZero(vz, .05);
            while (vx != 0 && vz != 0 && !hasSupport(box.offset(vx, -.6, vz), boxes)) {
                vx = towardZero(vx, .05); vz = towardZero(vz, .05);
            }
        }
        if (boxes.size() > 32) {
            // Broad phase includes the ordinary swept body AND both .6-step
            // retries. Preserve input order for identical axis resolution.
            AxisAlignedBB sweep = box.expand(vx, Math.max(vy, .6F), vz)
                    .union(box.expand(vx, Math.min(vy, 0), vz)).grow(1.0E-7);
            if (boxes instanceof CollisionIndex) {
                boxes = ((CollisionIndex)boxes).nearby(sweep);
            } else {
                List<AxisAlignedBB> nearby = new ArrayList<>();
                for (AxisAlignedBB obstacle : boxes) {
                    if (obstacle.intersects(sweep)) nearby.add(obstacle);
                }
                boxes = nearby;
            }
        }
        double dy=vy,dx=vx,dz=vz;
        List<AxisAlignedBB> solid = boxes;
        for(AxisAlignedBB b:solid) dy=b.calculateYOffset(box,dy);
        box=box.offset(0,dy,0);
        for(AxisAlignedBB b:solid) dx=b.calculateXOffset(box,dx);
        box=box.offset(dx,0,0);
        for(AxisAlignedBB b:solid) dz=b.calculateZOffset(box,dz);
        box=box.offset(0,0,dz);
        // Entity.move retries a horizontal collision with stepHeight, including
        // a collision on the landing tick. Heads/slabs can be mounted this way
        // without issuing a second jump. Compare both vanilla clearance orders.
        if ((dx != vx || dz != vz) && (f.ground || vy < 0 && dy != vy)) {
            AxisAlignedBB original = f.box();
            double bestDistance = dx*dx+dz*dz;
            for (int order=0; order<2; order++) {
                double up = .6F;
                AxisAlignedBB clearance = order == 0 ? original.expand(vx,0,vz) : original;
                for (AxisAlignedBB b : solid) up = b.calculateYOffset(clearance,up);
                AxisAlignedBB stepped = original.offset(0,up,0);
                double sx=vx, sz=vz;
                for (AxisAlignedBB b : solid) sx=b.calculateXOffset(stepped,sx);
                stepped=stepped.offset(sx,0,0);
                for (AxisAlignedBB b : solid) sz=b.calculateZOffset(stepped,sz);
                stepped=stepped.offset(0,0,sz);
                double down=-up;
                for (AxisAlignedBB b : solid) down=b.calculateYOffset(stepped,down);
                stepped=stepped.offset(0,down,0);
                if (sx*sx+sz*sz > bestDistance) {
                    bestDistance=sx*sx+sz*sz;
                    box=stepped; dx=sx; dz=sz; dy=up+down;
                }
            }
        }
        n.x=(box.minX+box.maxX)/2;n.y=box.minY;n.z=(box.minZ+box.maxZ)/2;
        n.ground=vy<0 && dy!=vy;
        n.collidedHorizontally = dx!=vx || dz!=vz;
        n.vx=dx==vx?motionX*drag:0;n.vz=dz==vz?motionZ*drag:0;
        // Entity.doBlockCollisions visits the contracted body after moving.
        // Soul sand reduces motion there, including the tick of landing.
        for (BlockPos slow : f.soulSand) {
            if (slow.getX() + 1 > box.minX + .001 && slow.getX() < box.maxX - .001
                    && slow.getY() + 1 > box.minY + .001 && slow.getY() < box.maxY - .001
                    && slow.getZ() + 1 > box.minZ + .001 && slow.getZ() < box.maxZ - .001) {
                n.vx *= .4;
                n.vz *= .4;
            }
        }
        double landedVelocity = dy == vy ? vy : 0;
        // Slime also rebounds ordinary movement. Even a tiny resting bounce
        // changes next tick's onGround flag and whether a jump can fire.
        if (!c.sneak && f.slimeBlocks.contains(new BlockPos(n.x, n.y - .2, n.z))) {
            if (dy != vy && vy < 0) landedVelocity = -vy;
            if (n.ground && Math.abs(landedVelocity) < .1) {
                double damping = .4 + Math.abs(landedVelocity) * .2;
                n.vx *= damping;
                n.vz *= damping;
            }
        }
        if (!inLiquid && n.collidedHorizontally && n.onLadder()) landedVelocity = .2;
        n.vy=inLiquid?landedVelocity*(inLava?.5:.800000011920929)-.02:(landedVelocity-.08)*.9800000190734863;
        if(inLiquid && n.collidedHorizontally) {
            AxisAlignedBB exit=n.box().offset(n.vx,n.vy+.6000000238418579-n.y+f.y,n.vz);
            boolean liquid=n.touchesWater(exit);
            for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(exit.minX,exit.minY,exit.minZ),
                    new BlockPos(exit.maxX,exit.maxY,exit.maxZ))) if(n.lava.contains(pos)) {liquid=true;break;}
            if(!hasSupport(exit,boxes) && !liquid) n.vy=.30000001192092896;
        }
        n.airAcceleration = sprint ? .026F : .02F;
        // EntityPlayer.updateSize runs AFTER travel. Movement uses the previous
        // pose; releasing sneak under a ceiling cannot expand into that block.
        double requestedHeight=c.sneak?(double)1.65F:HEIGHT;
        if(requestedHeight!=n.bodyHeight) {
            AxisAlignedBB resized=new AxisAlignedBB(box.minX,box.minY,box.minZ,
                    box.maxX,box.minY+requestedHeight,box.maxZ);
            if(!hasSupport(resized,allBoxes)) n.bodyHeight=requestedHeight;
        }
        return n;
    }

    /** Headings that run straight at a nearby ladder slab. */
    private static double[] ladderHeadings(Frame start, List<AxisAlignedBB> boxes) {
        List<Double> headings = new ArrayList<>();
        for (BlockPos pos : start.ladders.keySet()) {
            double cx = pos.getX() + .5, cz = pos.getZ() + .5;
            if (Math.hypot(cx - start.x, cz - start.z) > 8 || pos.getY() + 1 < start.y) continue;
            headings.add(Math.atan2(cz - start.z, cx - start.x));
            if (headings.size() == 4) break;
        }
        double[] result = new double[headings.size()];
        for (int i = 0; i < result.length; i++) result[i] = headings.get(i);
        return result;
    }

    private static double towardZero(double value, double amount) {
        return Math.abs(value) <= amount ? 0 : value - Math.copySign(amount, value);
    }
    private static boolean hasSupport(AxisAlignedBB body, List<AxisAlignedBB> boxes) {
        if(boxes instanceof CollisionIndex) return ((CollisionIndex)boxes).intersects(body);
        for (AxisAlignedBB box : boxes) {
            if (body.intersects(box)) return true;
        }
        return false;
    }

    /** One captured search, so an offline test can replay the live collision set. */
    private static void dumpSearchBoxes(Frame start, List<AxisAlignedBB> boxes, double gx, double gy, double gz) {
        try {
            java.nio.file.Path dir = net.minecraft.client.Minecraft.getMinecraft().mcDataDir.toPath()
                    .resolve("logs").resolve("parkour");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder out = new StringBuilder();
            out.append(start.x).append(' ').append(start.y).append(' ').append(start.z).append(' ')
                    .append(start.vx).append(' ').append(start.vy).append(' ').append(start.vz).append(' ')
                    .append(start.ground).append(' ').append(gx).append(' ').append(gy).append(' ').append(gz).append('\n');
            for (AxisAlignedBB box : boxes) out.append(box.minX).append(' ').append(box.minY).append(' ').append(box.minZ)
                    .append(' ').append(box.maxX).append(' ').append(box.maxY).append(' ').append(box.maxZ).append('\n');
            String name = "live-search-" + Math.round(start.x*10) + "_" + Math.round(start.z*10)
                    + "-to-" + Math.round(gx*10) + "_" + Math.round(gz*10) + ".txt";
            java.nio.file.Files.write(dir.resolve(name), out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {}
    }
}
