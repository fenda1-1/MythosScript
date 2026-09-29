package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourTrajectory;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import java.util.Set;

/** Trigger a pressure-plate launcher and steer its observed piston impulse. */
public final class MovementPistonLaunch extends Movement {
    private final BetterBlockPos launcher;
    private boolean launched;

    private MovementPistonLaunch(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest,
                                 BetterBlockPos launcher) {
        super(baritone, src, dest, new BetterBlockPos[0]);
        this.launcher = launcher;
    }

    public static MovementPistonLaunch find(CalculationContext c, BetterBlockPos src, int dx, int dz) {
        if (!c.parkourMode || !c.allowParkour || !c.canSprint
                || c.getBlock(src.x,src.y,src.z) != Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) return null;
        BetterBlockPos pad = new BetterBlockPos(src.x+dx,src.y-1,src.z+dz);
        // A pressed plate may already have extended the pad by one block.
        // Recognize both stable states so replanning does not lose this edge.
        boolean retracted = c.getBlock(pad.x,pad.y,pad.z) == Blocks.SLIME_BLOCK;
        boolean extended = c.getBlock(pad.x,pad.y,pad.z) == Blocks.PISTON_HEAD
                && c.getBlock(pad.x,pad.y+1,pad.z) == Blocks.SLIME_BLOCK;
        if (!retracted && !extended) return null;
        net.minecraft.block.state.IBlockState piston = c.get(pad.x,pad.y-1,pad.z);
        if (!(piston.getBlock() instanceof BlockPistonBase)
                || piston.getValue(BlockPistonBase.FACING) != EnumFacing.UP) return null;
        for (int distance=3; distance<=8; distance++) {
            int x=src.x+dx*distance,z=src.z+dz*distance;
            if (!c.isLoaded(x,z)) continue;
            for (int rise=2;rise<=6;rise++) {
                BetterBlockPos target=new BetterBlockPos(x,src.y+rise,z);
                if (ParkourSurface.support(c,target)==null
                        || !MovementHelper.canWalkThrough(c,x,target.y,z)
                        || !MovementHelper.canWalkThrough(c,x,target.y+1,z)) continue;
                return new MovementPistonLaunch(c.getBaritone(),src,target,pad);
            }
        }
        return null;
    }

    @Override public double calculateCost(CalculationContext c) { return 120; }

    /** A triggered moving pad is not a stationary landing/waiting node. */
    public static boolean isLaunchPad(CalculationContext context, BetterBlockPos feet) {
        if (context.getBlock(feet.x, feet.y-1, feet.z) != Blocks.SLIME_BLOCK) return false;
        for (int depth=2; depth<=3; depth++) {
            int y=feet.y-depth;
            net.minecraft.block.state.IBlockState piston=context.get(feet.x,y,feet.z);
            if (!(piston.getBlock() instanceof BlockPistonBase)
                    || piston.getValue(BlockPistonBase.FACING) != EnumFacing.UP) continue;
            if (depth==3 && context.getBlock(feet.x,y+1,feet.z) != Blocks.PISTON_HEAD) continue;
            for (EnumFacing face : EnumFacing.HORIZONTALS) {
                if (context.getBlock(feet.x+face.getFrontOffsetX(),y+2,feet.z+face.getFrontOffsetZ())
                        == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) return true;
            }
        }
        return false;
    }

    public static boolean hasLaunch(CalculationContext context, BetterBlockPos source) {
        if (context.getBlock(source.x, source.y, source.z) != Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) return false;
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            if (find(context, source, face.getFrontOffsetX(), face.getFrontOffsetZ()) != null) return true;
        }
        return false;
    }

    public static boolean canApproach(CalculationContext context, BetterBlockPos from, BetterBlockPos trigger) {
        if (context.getBlock(trigger.x,trigger.y,trigger.z) != Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) return true;
        boolean launcher=false;
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            if (find(context,trigger,face.getFrontOffsetX(),face.getFrontOffsetZ()) == null) continue;
            launcher=true;
            if ((trigger.x-from.x)*face.getFrontOffsetX()+(trigger.z-from.z)*face.getFrontOffsetZ()>0) return true;
        }
        // Rejoin behind the trigger; the trajectory search still validates the
        // precise seam contact and forward speed before executing this edge.
        return !launcher;
    }

    @Override protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> result=ImmutableSet.builder();
        for(int x=Math.min(src.x,dest.x)-1;x<=Math.max(src.x,dest.x)+1;x++)
            for(int z=Math.min(src.z,dest.z)-1;z<=Math.max(src.z,dest.z)+1;z++)
                for(int y=src.y-1;y<=Math.max(src.y+8,dest.y+2);y++)
                    result.add(new BetterBlockPos(x,y,z));
        return result.build();
    }

    @Override public void reset() { super.reset(); launched=false; }

    public Vec3d launchDirection() {
        // Keep the launch axis straight.  The sideways correction used here
        // previously reduced the useful forward velocity before the piston
        // pulse and made the second platform collision worse.
        if (launcher.x != src.x) {
            return new Vec3d(launcher.x-src.x, 0, 0);
        }
        return new Vec3d(0, 0, launcher.z-src.z);
    }

    /** Furthest trigger contact, leaving a small overlap rather than aiming at its centre. */
    public Vec3d triggerLanding() {
        Vec3d direction = launchDirection();
        // Keep the centre on the plate's supporting block, .025 before the
        // slime boundary; the body still overlaps Vanilla PRESSURE_AABB.
        double offset = .475;
        return new Vec3d(src.x + .5 + direction.x * offset, src.y,
                src.z + .5 + direction.z * offset);
    }

    public AxisAlignedBB triggerVolume() {
        return new AxisAlignedBB(src.x+.125,src.y,src.z+.125,src.x+.875,src.y+.25,src.z+.875);
    }

    /** The first contact must land on the plate, before touching the slime.
     * An airborne overlap followed by a slime landing rebounds too early and
     * misses the later piston pulse, especially on descending approaches. */
    public boolean safeApproach(AxisAlignedBB body) {
        if(!body.intersects(triggerVolume())) return true;
        Vec3d axis=launchDirection();
        double along=((body.minX+body.maxX)*.5-src.x-.5)*axis.x
                +((body.minZ+body.maxZ)*.5-src.z-.5)*axis.z;
        return body.minY>=src.y-.03 && body.minY<=src.y+.03 && along>=.45 && along<.5;
    }

    public boolean readyToLaunch(ParkourTrajectory.Frame frame) {
        Vec3d axis=launchDirection();
        return frame.ground && frame.vy<=0 && frame.box().intersects(triggerVolume())
                && safeApproach(frame.box()) && frame.vx*axis.x+frame.vz*axis.z>.12;
    }

    @Override public MovementState updateState(MovementState state) {
        super.updateState(state);
        if(state.getStatus()!=MovementStatus.RUNNING) return state;
        ParkourTrajectory.Frame frame=new ParkourTrajectory.Frame(ctx.player());
        MovementStatus status=observe(frame,ctx.world().getBlockState(dest).getBlock()==Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
        if(status!=MovementStatus.RUNNING) return state.setStatus(status);
        ParkourTrajectory.Control input=control(frame);
        state.setTarget(new MovementState.MovementTarget(
                new com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation(
                        input.yaw,ctx.playerRotations().getPitch()),true));
        state.setInput(Input.MOVE_RIGHT,false);
        state.setInput(Input.JUMP,input.jump);
        state.setInput(Input.SNEAK,input.sneak);
        state.setInput(Input.SPRINT,input.sprint);
        state.setInput(Input.MOVE_FORWARD,input.forward);
        return state;
    }

    /** Shared with recorded dynamic-world replay; the environment supplies the pulse. */
    public MovementStatus observe(ParkourTrajectory.Frame frame, boolean destinationTrigger) {
        if(frame.vy>.6 && frame.y>src.y+.1) launched=true;
        if(frame.y<src.y-.6) return MovementStatus.UNREACHABLE;
        // Consecutive launchers can fire before an onGround sample is exposed.
        // Transfer control on descending trigger contact, while preserving the
        // current input frame, instead of waiting for a stationary landing.
        if (destinationTrigger
                && frame.vy <= 0 && frame.y >= dest.y-.03
                && frame.y < dest.y+.25
                && frame.box().intersects(new net.minecraft.util.math.AxisAlignedBB(
                         dest.x+.125, dest.y, dest.z+.125, dest.x+.875, dest.y+.25, dest.z+.875)))
            return MovementStatus.SUCCESS;
        // Destination contact is authoritative even when a network correction
        // consumed the upward velocity before this controller sampled it.
        if(frame.ground && Math.abs(frame.y-dest.y)<.03
                && frame.box().maxX>dest.x && frame.box().minX<dest.x+1
                && frame.box().maxZ>dest.z && frame.box().minZ<dest.z+1)
            return MovementStatus.SUCCESS;
        // A client-side piston impulse can be corrected back onto the trigger.
        // It is no longer an airborne launch once we are grounded here again.
        // Otherwise the stale flag suppresses the step-up jump indefinitely.
        if (launched && frame.ground && frame.y < src.y + .1) {
            launched = false;
        }
        return MovementStatus.RUNNING;
    }

    public ParkourTrajectory.Control control(ParkourTrajectory.Frame frame) {
        // Retain forward momentum across the trigger and airborne landing edge.
        Vec3d direction=launchDirection();
        // Keep the same travel heading through the trigger. Cancelling the
        // jump's lateral momentum here changes the moving-pad contact phase.
        // Keep sprint armed before the piston pulse so the first airborne
        // acceleration uses sprint speed instead of spending ticks ramping up.
        return new ParkourTrajectory.Control(Math.atan2(direction.z,direction.x),true,true,
                !launched && frame.ground && frame.collidedHorizontally && frame.y<src.y+.1);
    }
}
