package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Collision surfaces, including the half block above a fence's occupied cell. */
public final class ParkourSurface {
    // EntityPlayer's width is a float. Exact 0.3 puts fence-edge poses a few
    // nanometres inside the real body and disconnects their outgoing corridors.
    private static final double HALF_WIDTH = .30000001192092896;
    private ParkourSurface() {}

    public static final class Cache {
        private final java.util.Map<BetterBlockPos,java.util.Optional<AxisAlignedBB>> supports = new java.util.HashMap<>();
        private final java.util.Map<BetterBlockPos,java.util.Map<AxisAlignedBB,java.util.Optional<Vec3d>>> poses = new java.util.HashMap<>();
        private final java.util.Map<BetterBlockPos,java.util.Map<AxisAlignedBB,List<Vec3d>>> stances = new java.util.HashMap<>();
    }

    public static AxisAlignedBB support(CalculationContext context, BetterBlockPos feet) {
        if (!context.safeForThreadedUse) return findSupport(context,feet);
        return context.parkourSurfaces.get().supports.computeIfAbsent(feet,
                pos -> java.util.Optional.ofNullable(findSupport(context,pos))).orElse(null);
    }

    /** A supported stance may overhang its graph cell, e.g. an anvil's exposed base. */
    public static BetterBlockPos supportedFeet(CalculationContext context, Vec3d position) {
        BetterBlockPos feet=new BetterBlockPos(position.x,position.y+.1251,position.z);
        AxisAlignedBB body=new AxisAlignedBB(position.x-HALF_WIDTH,position.y,position.z-HALF_WIDTH,
                position.x+HALF_WIDTH,position.y+1.8,position.z+HALF_WIDTH);
        BetterBlockPos best=feet;
        double nearest=Double.POSITIVE_INFINITY;
        for(int dx:new int[]{0,-1,1}) for(int dz:new int[]{0,-1,1}) {
            BetterBlockPos candidate=new BetterBlockPos(feet.x+dx,feet.y,feet.z+dz);
            AxisAlignedBB support=support(context,candidate);
            if(support==null || Math.abs(support.maxY-position.y)>.02
                    || Math.min(body.maxX,support.maxX)-Math.max(body.minX,support.minX)<=.025
                    || Math.min(body.maxZ,support.maxZ)-Math.max(body.minZ,support.minZ)<=.025) continue;
            if(dx==0 && dz==0) return feet;
            double distance=position.squareDistanceTo(candidate.x+.5,position.y,candidate.z+.5);
            if(distance<nearest) {best=candidate;nearest=distance;}
        }
        return best;
    }

    private static AxisAlignedBB findSupport(CalculationContext context, BetterBlockPos feet) {
        List<AxisAlignedBB> candidates = new ArrayList<>();
        for (int y = feet.y - 2; y <= feet.y + 2; y++) {
            BlockPos pos = new BlockPos(feet.x, y, feet.z);
            IBlockState state = context.get(feet.x, y, feet.z);
            if (state.getBlock() == net.minecraft.init.Blocks.VINE) continue;
            if (state.getBlock() != net.minecraft.init.Blocks.NETHER_WART
                    && MovementHelper.avoidWalkingInto(state.getBlock())) continue;
            List<AxisAlignedBB> boxes = context.collisionBoxes(pos,
                    new AxisAlignedBB(feet.x - 1, feet.y - 2, feet.z - 1, feet.x + 2, feet.y + 1, feet.z + 2));
            for (AxisAlignedBB box : boxes) {
                // Use the same logical-feet convention as IPlayerContext:
                // floor(posY + .1251). Half slabs, stairs and fence tops are
                // valid fractional surfaces, not missing graph nodes.
                if (MathHelper.floor(box.maxY + .1251) != feet.y) continue;
                candidates.add(box);
            }
        }
        // Soul sand is only 0.875 tall and sits on a full block in the same
        // feet cell. The lower top is covered, so it has no standing pose;
        // use the lowest surface that a body can actually occupy. An exposed
        // post or skull ledge remains preferred when its pose is clear.
        candidates.sort((a, b) -> Double.compare(a.maxY, b.maxY));
        for (AxisAlignedBB box : candidates) {
            if (standPoint(context, feet, box) != null) return box;
        }
        return null;
    }

    /** Find a clear standing pose on the support, including exposed corners below posts. */
    public static Vec3d standPoint(CalculationContext context, BetterBlockPos feet, AxisAlignedBB support) {
        if (!context.safeForThreadedUse) return findStandPoint(context,feet,support);
        return context.parkourSurfaces.get().poses.computeIfAbsent(feet,pos -> new java.util.HashMap<>())
                .computeIfAbsent(support,box -> java.util.Optional.ofNullable(findStandPoint(context,feet,box))).orElse(null);
    }

    private static Vec3d findStandPoint(CalculationContext context, BetterBlockPos feet, AxisAlignedBB support) {
        // The body can overhang a support while its feet still overlap it.
        // Inset posts can leave a usable ledge outside the support's centre cell.
        // Ladder tops are thin ledges against a wall. Their centre is not the
        // cell centre; a .325/.675 pose overlaps the ledge and clears the wall.
        // A fence post is .25 wide. A body on its outer edge has its centre
        // about .425 past the cell centre, which the coarser offsets miss.
        // Fence posts sit on the south edge of their cell. The proven stance
        // is on the open north side (negative z), not the wall side.
        double[] offsets = {0, -.425, .425, -.175, .175, -.44, .44, -.765, .765};
        for (double dx : offsets) {
            double[] zOrder = support.maxZ - support.minZ < .8
                    ? new double[]{-.425, -.44, -.765, -.175, 0, .175, .425, .44, .765}
                    : offsets;
            for (double dz : zOrder) {
                double x = feet.x + .5 + dx, z = feet.z + .5 + dz;
                if (x + .3 <= support.minX || x - .3 >= support.maxX
                        || z + .3 <= support.minZ || z - .3 >= support.maxZ) continue;
                AxisAlignedBB body = new AxisAlignedBB(x - HALF_WIDTH, support.maxY + .001, z - HALF_WIDTH,
                        x + HALF_WIDTH, support.maxY + 1.8, z + HALF_WIDTH);
                if (!hasCollisionIgnoringNetherWart(context, body) && safeBody(context, body)) {
                    return new Vec3d(x, support.maxY, z);
                }
            }
        }
        // Fixed offsets can miss a narrow exposed rim. Partition the supported
        // centre range at expanded obstacle edges and test each open rectangle.
        // Keep more than the trajectory controller's .025 landing overlap.
        TreeSet<Double> xs=new TreeSet<>(), zs=new TreeSet<>();
        xs.add(support.minX-HALF_WIDTH+.0251);
        xs.add(support.maxX+HALF_WIDTH-.0251);
        zs.add(support.minZ-HALF_WIDTH+.0251);
        zs.add(support.maxZ+HALF_WIDTH-.0251);
        AxisAlignedBB area=new AxisAlignedBB(xs.first()-HALF_WIDTH,support.maxY+.001,zs.first()-HALF_WIDTH,
                xs.last()+HALF_WIDTH,support.maxY+1.8,zs.last()+HALF_WIDTH);
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(area.minX,area.minY-1,area.minZ),
                new BlockPos(area.maxX,area.maxY,area.maxZ))) {
            if(context.getBlock(pos.getX(),pos.getY(),pos.getZ())==net.minecraft.init.Blocks.NETHER_WART) continue;
            for(AxisAlignedBB box:context.collisionBoxes(pos,area)) {
                for(double x:new double[]{box.minX-HALF_WIDTH,box.maxX+HALF_WIDTH})
                    if(x>xs.first() && x<xs.last()) xs.add(x);
                for(double z:new double[]{box.minZ-HALF_WIDTH,box.maxZ+HALF_WIDTH})
                    if(z>zs.first() && z<zs.last()) zs.add(z);
            }
        }
        Double[] xCuts=xs.toArray(new Double[0]), zCuts=zs.toArray(new Double[0]);
        for(int i=1;i<xCuts.length;i++) for(int j=1;j<zCuts.length;j++) {
            double x=(xCuts[i-1]+xCuts[i])/2, z=(zCuts[j-1]+zCuts[j])/2;
            AxisAlignedBB body=new AxisAlignedBB(x-HALF_WIDTH,support.maxY+.001,z-HALF_WIDTH,
                    x+HALF_WIDTH,support.maxY+1.8,z+HALF_WIDTH);
            if(!hasCollisionIgnoringNetherWart(context,body) && safeBody(context,body))
                return new Vec3d(x,support.maxY,z);
        }
        return null;
    }

    /** Ordinary walking assumes a centred body, unlike an exposed parkour ledge. */
    public static List<Vec3d> standPoints(CalculationContext context, BetterBlockPos feet, AxisAlignedBB support) {
        if (!context.safeForThreadedUse) return findStandPoints(context,feet,support);
        return context.parkourSurfaces.get().stances.computeIfAbsent(feet,pos -> new java.util.HashMap<>())
                .computeIfAbsent(support,box -> java.util.Collections.unmodifiableList(findStandPoints(context,feet,box)));
    }

    private static List<Vec3d> findStandPoints(CalculationContext context, BetterBlockPos feet, AxisAlignedBB support) {
        Vec3d preferred=standPoint(context,feet,support);
        if(preferred==null) return java.util.Collections.emptyList();
        if(support.maxX-support.minX<.8 || support.maxZ-support.minZ<.8
                || clearWalkingSource(context,feet.x,feet.y,feet.z))
            return java.util.Collections.singletonList(preferred);
        // A fence or anvil can split a base into disconnected sides; anvil
        // ledges can require the same overhanging poses as findStandPoint.
        // Keep distinct, bounded standing poses rather than selecting one side
        // for every incoming and outgoing edge of that block.
        List<Vec3d> result=new ArrayList<>();
        result.add(preferred);
        // Add opposite overhangs only on axes that already require one.
        // Wider poses on ordinary post/ladder bases can select an isolated rim.
        double[] xOffsets=Math.abs(preferred.x-feet.x-.5)>.5
                ? new double[]{0,-.44,.44,-.765,.765} : new double[]{0,-.44,.44};
        double[] zOffsets=Math.abs(preferred.z-feet.z-.5)>.5
                ? new double[]{0,-.44,.44,-.765,.765} : new double[]{0,-.44,.44};
        for(double dx:xOffsets) for(double dz:zOffsets) {
            Vec3d p=new Vec3d(feet.x+.5+dx,support.maxY,feet.z+.5+dz);
            if(result.contains(p) || p.x+.3<=support.minX || p.x-.3>=support.maxX
                    || p.z+.3<=support.minZ || p.z-.3>=support.maxZ) continue;
            AxisAlignedBB body=new AxisAlignedBB(p.x-HALF_WIDTH,p.y+.001,p.z-HALF_WIDTH,
                    p.x+HALF_WIDTH,p.y+1.8,p.z+HALF_WIDTH);
            if(!hasCollisionIgnoringNetherWart(context,body) && safeBody(context,body)) result.add(p);
        }
        return result;
    }

    /** Ordinary walking assumes a centred body, unlike an exposed parkour ledge. */
    public static boolean clearWalkingSource(CalculationContext context, int x, int y, int z) {
        Block block = context.getBlock(x, y, z);
        // Hanging is not grounded walking. Keep climb exits under the trajectory
        // controller until the player actually reaches the neighbouring support.
        if ((block == net.minecraft.init.Blocks.VINE || block == net.minecraft.init.Blocks.LADDER)
                && support(context, new BetterBlockPos(x, y, z)) == null) return false;
        return !hasCollisionIgnoringNetherWart(context,
                new AxisAlignedBB(x+.2,y+.001,z+.2,x+.8,y+1.8,z+.8));
    }

    public static boolean hasCollisionIgnoringNetherWart(CalculationContext context, AxisAlignedBB body) {
        // Fences/walls extend above their owning block, into the feet cell.
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(body.minX, body.minY-1, body.minZ),
                new BlockPos(body.maxX, body.maxY, body.maxZ))) {
            IBlockState state = context.get(pos.getX(), pos.getY(), pos.getZ());
            if (state.getBlock() == net.minecraft.init.Blocks.NETHER_WART) continue;
            List<AxisAlignedBB> boxes = context.collisionBoxes(pos,body);
            if (!boxes.isEmpty()) return true;
        }
        return false;
    }

    /** Shared live/offline chord filter, including thin obstacles beside takeoff. */
    public static boolean jumpChordClear(Vec3d start, Vec3d goal, Predicate<AxisAlignedBB> collides) {
        double distance=Math.hypot(goal.x-start.x,goal.z-start.z),rise=goal.y-start.y;
        // Keep spacing below the body width, otherwise a fence can fall entirely
        // between the starting pose and the first sample.
        int samples=Math.max(2,(int)Math.ceil(distance*3));
        double arc=rise<0 ? Math.pow(Math.sqrt(1.25)+Math.sqrt(1.25-rise),2) : 4;
        for(int sample=1;sample<samples;sample++) {
            double t=sample/(double)samples;
            double x=start.x+(goal.x-start.x)*t,z=start.z+(goal.z-start.z)*t;
            double y=start.y+rise*t+arc*t*(1-t);
            if(collides.test(new AxisAlignedBB(x-.3,y+.001,z-.3,x+.3,y+1.8,z+.3))) return false;
        }
        return true;
    }

    /** Short head-bump jumps and walk-off drops need not follow the free-flight jump arc. */
    public static boolean lowCeilingCorridor(CalculationContext context, Vec3d start, Vec3d goal) {
        return lowCeilingCorridor(context,start,goal,false);
    }

    public static boolean lowCeilingCorridor(CalculationContext context, Vec3d start, Vec3d goal, boolean swimming) {
        double distance=Math.hypot(goal.x-start.x,goal.z-start.z);
        boolean drop=goal.y<start.y-.6;
        boolean ice=context.getBlock(MathHelper.floor(start.x),MathHelper.floor(start.y-1),MathHelper.floor(start.z)).slipperiness>.9F;
        // A low doorway at the landing does not limit an open-air sprint jump
        // to three blocks. Keep the ordinary candidate range; timed physics
        // still has to prove the runup, head clearance and stable landing.
        // The low ceiling may be only over a raised landing. Use jump height,
        // not walking step height; timed physics still proves the ascent.
        if(distance>(ice?10:drop?4:context.canSprint?5.5:3) || goal.y-start.y>(swimming?1:context.maxJumpHeight) || start.y-goal.y>4) return false;
        // Check the ascent too: a boosted landing can be above the source roof.
        if(goal.y>start.y) for(double y=start.y;y<goal.y;y+=.1) {
            AxisAlignedBB body=new AxisAlignedBB(start.x-HALF_WIDTH,y+.001,start.z-HALF_WIDTH,
                    start.x+HALF_WIDTH,y+1.8,start.z+HALF_WIDTH);
            if(hasCollisionIgnoringNetherWart(context,body) || !safeBody(context,body,swimming)) return false;
        }
        int samples=Math.max(2,(int)Math.ceil(distance*10));
        for(int i=0;i<=samples;i++) {
            double t=i/(double)samples;
            double x=start.x+(goal.x-start.x)*t,z=start.z+(goal.z-start.z)*t;
            double y=Math.max(start.y,goal.y);
            AxisAlignedBB body=new AxisAlignedBB(x-.3,y+.001,z-.3,x+.3,y+1.8,z+.3);
            if(hasCollisionIgnoringNetherWart(context,body) || !safeBody(context,body,swimming)) return false;
        }
        // A lower destination also needs a clear descent column. This only
        // exposes the candidate; execution still verifies its timed trajectory.
        if(drop) for(double y=start.y;y>goal.y;y-=.1) {
            AxisAlignedBB body=new AxisAlignedBB(goal.x-HALF_WIDTH,y+.001,goal.z-HALF_WIDTH,
                    goal.x+HALF_WIDTH,y+1.8,goal.z+HALF_WIDTH);
            if(hasCollisionIgnoringNetherWart(context,body) || !safeBody(context,body)) return false;
        }
        return true;
    }

    /** Clear corridor for a curved candidate; the executor still validates the timed jump. */
    public static Vec3d[] climbRoute(CalculationContext context, Vec3d start, Vec3d goal) {
        // A vine can slide below an overhang and then rise alongside it. It
        // cannot waive solid collisions just because the source is climbable.
        for(Vec3d corner:new Vec3d[]{new Vec3d(goal.x,start.y,goal.z),
                new Vec3d(start.x,goal.y,start.z)}) {
            Vec3d[] points={start,corner,goal};
            if(clearCorridor(context,points)) return points;
        }
        return null;
    }

    public static boolean clearCorridor(CalculationContext context, Vec3d[] points) {
        for(int edge=1;edge<points.length;edge++) {
            Vec3d a=points[edge-1],b=points[edge];
            int samples=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));
            for(int i=0;i<=samples;i++) {
                Vec3d p=a.add(b.subtract(a).scale((double)i/samples));
                AxisAlignedBB body=new AxisAlignedBB(p.x-HALF_WIDTH,p.y+.001,p.z-HALF_WIDTH,
                        p.x+HALF_WIDTH,p.y+1.7999999523162842,p.z+HALF_WIDTH);
                if(hasCollisionIgnoringNetherWart(context,body) || !safeBody(context,body)) return false;
            }
        }
        return true;
    }

    /** A fixed parabola misses jumps whose runup and landing brake change their
     * horizontal timing. Expose their clear volume; the trajectory search must
     * still prove every physical tick before execution, including inherited speed. */
    public static Vec3d[] jumpClearanceRoute(CalculationContext context, Vec3d start, Vec3d goal) {
        double distance=Math.hypot(goal.x-start.x,goal.z-start.z);
        double peak=start.y+context.maxJumpHeight;
        if(goal.y>peak) return null;
        // A low ceiling can require walking out before takeoff. The fixed arc
        // rises too early; test the escape volume without allowing solid clips.
        // This is only a graph candidate: the executor must prove its runup,
        // last supported takeoff tick and landing with the actual physics.
        if(goal.y>start.y && distance>.1 && distance<=3
                && hasCollisionIgnoringNetherWart(context,new AxisAlignedBB(
                        start.x-HALF_WIDTH,start.y+.42,start.z-HALF_WIDTH,
                        start.x+HALF_WIDTH,start.y+2.22,start.z+HALF_WIDTH))) {
            for(double runup=.1;runup<Math.min(distance,1.5);runup+=.1) {
                Vec3d exit=start.addVector((goal.x-start.x)*runup/distance,0,(goal.z-start.z)*runup/distance);
                Vec3d[] points={start,exit,new Vec3d(exit.x,peak,exit.z),new Vec3d(goal.x,peak,goal.z),goal};
                if(clearCorridor(context,points)) return points;
            }
        }
        // Only reconsider hazard clearance here; solid-wall detours have their
        // own candidates and must not turn into speculative vertical shortcuts.
        if(!jumpChordClear(start,goal,body->hasCollisionIgnoringNetherWart(context,body))) return null;
        Vec3d[] points={start,new Vec3d(start.x,peak,start.z),new Vec3d(goal.x,peak,goal.z),goal};
        return clearCorridor(context,points)?points:null;
    }

    public static Vec3d[] turnRoute(CalculationContext context, Vec3d start, Vec3d goal) {
        double distance=Math.hypot(goal.x-start.x,goal.z-start.z);
        if(goal.y<start.y-.6 && distance<=6) {
            // Leave a narrow doorway along its axis before turning toward a
            // lower platform. A diagonal chord can clip the door jamb.
            for(Vec3d corner:new Vec3d[]{new Vec3d(goal.x,start.y,start.z),
                    new Vec3d(start.x,start.y,goal.z)}) {
                if(lowCeilingCorridor(context,start,corner) && lowCeilingCorridor(context,corner,goal))
                    return new Vec3d[]{start,corner,goal};
            }
            // With low ceilings over both ledges, descend in the open gap,
            // after leaving the source and before entering the landing roof.
            if(distance>2) {
                Vec3d step=new Vec3d((goal.x-start.x)/distance,0,(goal.z-start.z)/distance);
                Vec3d[] points={start,start.add(step),goal.subtract(step),goal};
                if(clearCorridor(context,points)) return points;
            }
        }
        boolean boosted=goal.y-start.y>1.25 && goal.y-start.y<=context.maxJumpHeight;
        if((distance<1 && !boosted) || distance>6 || (!boosted && Math.abs(goal.y-start.y)>1.25)) return null;
        // A spiral stair turns around an inside corner. Parallel doglegs miss
        // that corridor; check both axis orders before the wider detours.
        if(Math.abs(goal.x-start.x)>.1 && Math.abs(goal.z-start.z)>.1) {
            for(Vec3d corner:new Vec3d[]{new Vec3d(start.x,start.y,goal.z),new Vec3d(goal.x,start.y,start.z)}) {
                Vec3d[] points={start,corner,new Vec3d(corner.x,goal.y,corner.z),goal};
                if(clearCorridor(context,points)) return points;
            }
        }
        double nx=distance<.001?1:-(goal.z-start.z)/distance,nz=distance<.001?0:(goal.x-start.x)/distance;
        for(double offset:new double[]{-1,1,-1.5,1.5,-2,2}) {
            Vec3d first=start.addVector(nx*offset,0,nz*offset);
            Vec3d second=new Vec3d(goal.x+nx*offset,Math.max(start.y,goal.y),goal.z+nz*offset);
            Vec3d[] points={start,first,second,goal};
            boolean clear=true;
            for(int edge=1;edge<points.length && clear;edge++) {
                Vec3d a=points[edge-1],b=points[edge];
                int samples=(int)Math.ceil(a.distanceTo(b)*10);
                for(int i=0;i<=samples;i++) {
                    double t=samples==0?0:(double)i/samples;
                    Vec3d p=a.add(b.subtract(a).scale(t));
                    AxisAlignedBB body=new AxisAlignedBB(p.x-.30000001192092896,p.y+.001,p.z-.30000001192092896,
                            p.x+.30000001192092896,p.y+1.7999999523162842,p.z+.30000001192092896);
                    if(hasCollisionIgnoringNetherWart(context,body) || !safeBody(context,body)) { clear=false;break; }
                }
            }
            if(clear) return points;
        }
        // Opposing block corners can leave a lane narrower than the fixed
        // doglegs. Try lanes at block-face body clearance in both axis orders.
        // This is geometric admission only; timed physics still proves the jump.
        double peak=start.y+context.maxJumpHeight;
        Vec3d raisedStart=new Vec3d(start.x,peak,start.z),raisedGoal=new Vec3d(goal.x,peak,goal.z);
        for(boolean alongX:new boolean[]{true,false}) {
            double a=alongX?start.z:start.x,b=alongX?goal.z:goal.x;
            for(int cell=MathHelper.floor(Math.min(a,b));cell<=MathHelper.floor(Math.max(a,b));cell++) {
                for(double inset:new double[]{HALF_WIDTH+.001,1-HALF_WIDTH-.001}) {
                    double lane=cell+inset;
                    if(lane<Math.min(a,b) || lane>Math.max(a,b)) continue;
                    Vec3d first=alongX?new Vec3d(start.x,peak,lane):new Vec3d(lane,peak,start.z);
                    Vec3d second=alongX?new Vec3d(goal.x,peak,lane):new Vec3d(lane,peak,goal.z);
                    Vec3d[] points={start,raisedStart,first,second,raisedGoal,goal};
                    if(clearCorridor(context,points)) return points;
                }
            }
        }
        return null;
    }

    /** Liquids and fire can occupy a valid support without contributing collision boxes. */
    public static boolean safeBody(CalculationContext context, AxisAlignedBB body) {
        return safeBody(context,body,false);
    }

    public static boolean safeBody(CalculationContext context, AxisAlignedBB body, boolean swimming) {
        for (int x = MathHelper.floor(body.minX); x <= MathHelper.floor(body.maxX); x++) {
            for (int y = MathHelper.floor(body.minY); y <= MathHelper.floor(body.maxY); y++) {
                for (int z = MathHelper.floor(body.minZ); z <= MathHelper.floor(body.maxZ); z++) {
                    IBlockState state = context.get(x, y, z);
                    if(swimming && MovementHelper.isWater(state.getBlock())) continue;
                    if (context.allowFireContact && state.getBlock()==net.minecraft.init.Blocks.FIRE) continue;
                    if(state.getMaterial().isLiquid()) {
                        // Lava rim fire damage is allowed; entering the vanilla
                        // isInLava volume (and its different physics) is not.
                        AxisAlignedBB contact = state.getMaterial()==net.minecraft.block.material.Material.LAVA
                                ? body.grow(-.1,-.4,-.1).grow(1e-7) : body;
                        if (contact.intersects(new AxisAlignedBB(x,y,z,x+1,y+1,z+1))) return false;
                        continue;
                    }
                    if (state.getBlock()==net.minecraft.init.Blocks.FIRE
                            || state.getBlock()==net.minecraft.init.Blocks.WEB
                            || state.getBlock() != net.minecraft.init.Blocks.NETHER_WART
                                    && MovementHelper.avoidWalkingInto(state.getBlock())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Immutable hazard snapshot; trajectory workers must never query the live world. */
    public static Predicate<AxisAlignedBB> safetySnapshot(CalculationContext context, AxisAlignedBB region) {
        return safetySnapshot(context, region, null);
    }

    public static Predicate<AxisAlignedBB> safetySnapshot(CalculationContext context, AxisAlignedBB region, BlockPos waterCatch) {
        return safetySnapshot(context,region,waterCatch,false);
    }

    public static Predicate<AxisAlignedBB> safetySnapshot(CalculationContext context, AxisAlignedBB region,
            BlockPos waterCatch, boolean swimming) {
        return safetySnapshot(context,region,waterCatch,swimming,null);
    }

    /** A recovery may retreat from a lava rim only over this real landing support. */
    public static Predicate<AxisAlignedBB> safetySnapshot(CalculationContext context, AxisAlignedBB region,
            BlockPos waterCatch, boolean swimming, AxisAlignedBB recoverySupport) {
        Set<BlockPos> hazards = new HashSet<>();
        Map<BlockPos, Boolean> liquids = new HashMap<>();
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(region.minX, region.minY, region.minZ),
                new BlockPos(region.maxX, region.maxY, region.maxZ))) {
            IBlockState state = context.get(pos.getX(), pos.getY(), pos.getZ());
            if(swimming && MovementHelper.isWater(state.getBlock())) continue;
            if (context.allowFireContact && state.getBlock()==net.minecraft.init.Blocks.FIRE) continue;
            if (state.getMaterial().isLiquid()) {
                if (waterCatch != null && MovementHelper.isWater(state.getBlock())
                        && pos.getX() == waterCatch.getX() && pos.getZ() == waterCatch.getZ()
                        && pos.getY() >= waterCatch.getY() && pos.getY() <= waterCatch.getY() + 1) continue;
                liquids.put(pos.toImmutable(),state.getMaterial()==net.minecraft.block.material.Material.LAVA);
            } else if (state.getBlock()==net.minecraft.init.Blocks.FIRE || state.getBlock()==net.minecraft.init.Blocks.WEB
                    || MovementHelper.avoidWalkingInto(state.getBlock())) {
                hazards.add(pos.toImmutable());
            }
        }
        return body -> {
            if (!hazards.isEmpty()) {
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(body.minX + .001, body.minY + .001, body.minZ + .001),
                        new BlockPos(body.maxX - .001, body.maxY - .001, body.maxZ - .001))) {
                    if (hazards.contains(pos)) return false;
                }
            }
            if (!liquids.isEmpty()) {
                AxisAlignedBB lavaBody = body.grow(-.1,-.4,-.1).grow(1e-7);
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(body.minX, body.minY, body.minZ),
                         new BlockPos(body.maxX, body.maxY, body.maxZ))) {
                    Boolean lava = liquids.get(pos);
                    if (lava != null && (lava ? lavaBody : body).intersects(new AxisAlignedBB(pos))) {
                        if (lava && recoverySupport != null && body.minY >= recoverySupport.maxY - 1e-7
                                && Math.min(body.maxX,recoverySupport.maxX)-Math.max(body.minX,recoverySupport.minX) >= .1
                                && Math.min(body.maxZ,recoverySupport.maxZ)-Math.max(body.minZ,recoverySupport.minZ) >= .1) continue;
                        return false;
                    }
                }
            }
            return true;
        };
    }
}
