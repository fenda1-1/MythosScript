package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourTrajectory;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3d;
import java.util.Set;

/** Swimming, including head-deep entry and diagonal rising water connections. */
public final class MovementSwim extends Movement {
    public MovementSwim(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, new BetterBlockPos[0]);
    }

    @Override public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.y, dest.z);
    }

    public static double cost(CalculationContext context, int x, int y, int z, int dx, int dy, int dz) {
        boolean gap = dy == y && Math.abs(dx-x) + Math.abs(dz-z) == 2
                && (dx == x || dz == z);
        if (context.assumeWalkOnWater || y < 0 || dy < 0 || y > 254 || dy > 254
                || Math.abs(dx - x) > (gap?2:1) || Math.abs(dy - y) > 1 || Math.abs(dz - z) > (gap?2:1)
                || (dx == x && dy == y && dz == z)) return COST_INF;
        // Vanilla checks the submerged body, not only the feet block. A water
        // block at head height can support ascent between suspended pools.
        if (!wet(context, dx + .5, dy, dz + .5)) return COST_INF;
        boolean entering = !wet(context, x + .5, y, z + .5);
        if (gap && (entering || !context.allowParkour)) return COST_INF;
        if (entering && (dy < y || !MovementHelper.canWalkOn(context.bsi, x, y - 1, z))) return COST_INF;
        for (int sample = 0; sample <= 8; sample++) {
            double t = sample / 8.0;
            double px = x + .5 + (dx - x) * t, py = y + (dy - y) * t;
            double pz = z + .5 + (dz - z) * t;
            if (!clearBody(context, px, py, pz)) return COST_INF;
            // A grounded entry may jump into water. Once swimming, don't
            // invent airborne bridges between disconnected pools.
            // Across two isolated water blocks the body briefly leaves both
            // volumes. Only permit the short central gap at equal feet height;
            // all samples still require a clear, non-hazardous body corridor.
            if (!entering && !wet(context, px, py, pz)
                    && !(gap && sample >= 3 && sample <= 5)) return COST_INF;
        }
        // Passive sinking is slower than horizontal swimming or jumping upward.
        return dy < y ? 24.0D : Math.max(12.0D, context.waterWalkSpeed)
                * Math.sqrt((dx - x) * (dx - x) + (dy - y) * (dy - y) + (dz - z) * (dz - z));
    }

    private static boolean wet(CalculationContext context, double x, double y, double z) {
        for (int bx = (int) Math.floor(x - .299); bx <= Math.floor(x + .299); bx++)
            for (int by = (int) Math.floor(y + .401); by <= Math.floor(y + 1.399); by++)
                for (int bz = (int) Math.floor(z - .299); bz <= Math.floor(z + .299); bz++)
                    if (MovementHelper.isWater(context.get(bx, by, bz).getBlock())) return true;
        return false;
    }

    private static boolean clearBody(CalculationContext context, double x, double y, double z) {
        for (int bx = (int) Math.floor(x - .299); bx <= Math.floor(x + .299); bx++)
            for (int by = (int) Math.floor(y + .001); by <= Math.floor(y + 1.799); by++)
                for (int bz = (int) Math.floor(z - .299); bz <= Math.floor(z + .299); bz++)
                    if (!clear(context, bx, by, bz)) return false;
        return true;
    }

    private static boolean clear(CalculationContext context, int x, int y, int z) {
        Block block = context.get(x, y, z).getBlock();
        return !MovementHelper.isConfiguredDangerousBlock(block)
                && !MovementHelper.isConfiguredBlockingBlock(block)
                && (MovementHelper.isWater(block) || MovementHelper.canWalkThrough(context, x, y, z));
    }

    @Override protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> positions = ImmutableSet.builder();
        for (int x=Math.min(src.x,dest.x); x<=Math.max(src.x,dest.x); x++)
            for (int z=Math.min(src.z,dest.z); z<=Math.max(src.z,dest.z); z++)
                for (int y=Math.min(src.y,dest.y)-1; y<=Math.max(src.y,dest.y)+1; y++)
                    positions.add(new BetterBlockPos(x,y,z));
        return positions.build();
    }

    @Override protected boolean shouldAutoSwimInLiquid() {
        return false;
    }

    @Override public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) return state;
        ParkourTrajectory.Frame frame=new ParkourTrajectory.Frame(ctx.player());
        if(arrived(frame,src,dest)) return state.setStatus(MovementStatus.SUCCESS);
        ParkourTrajectory.Control control=control(frame,src,dest);
        state.setTarget(new MovementState.MovementTarget(
                new com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation(control.yaw,ctx.player().rotationPitch),true));
        state.setInput(Input.MOVE_FORWARD,control.forward).setInput(Input.JUMP,control.jump)
                .setInput(Input.SNEAK,false).setInput(Input.SPRINT,control.sprint);
        return state;
    }

    public static boolean arrived(ParkourTrajectory.Frame frame, BetterBlockPos src, BetterBlockPos dest) {
        return Math.hypot(dest.x+.5-frame.x,dest.z+.5-frame.z)<.3 && Math.abs(dest.y+.05-frame.y)<.18;
    }

    /** Shared reactive controller: a pool handoff remains buoyant, never a neutral standing stop. */
    public static ParkourTrajectory.Control control(ParkourTrajectory.Frame frame, BetterBlockPos src, BetterBlockPos dest) {
        double dx=dest.x+.5-frame.x,dz=dest.z+.5-frame.z,dy=dest.y+.05-frame.y;
        boolean gap = Math.abs(dest.x-src.x) + Math.abs(dest.z-src.z) == 2
                && dest.y == src.y && (dest.x == src.x || dest.z == src.z);
        boolean atDest=(int)Math.floor(frame.x)==dest.x && (int)Math.floor(frame.z)==dest.z;
        // Build horizontal momentum while still immersed. Rising immediately
        // leaves the source pool through its surface before reaching its edge,
        // wasting the short airborne window needed to catch the next pool.
        double progress=((frame.x-src.x-.5)*(dest.x-src.x)+(frame.z-src.z-.5)*(dest.z-src.z))/2;
        boolean crossing=gap && progress>=.25 && !atDest;
        if(gap && !atDest) dy=src.y+(crossing?.4:.05)-frame.y;
        boolean rising=dest.y>src.y && !atDest;
        // Build buoyancy before leaving a lower isolated pool. Moving sideways
        // immediately exits its volume before the head reaches the upper pool.
        boolean forward=dx*dx+dz*dz>.04 && (!rising || frame.y>=src.y+.25);
        return new ParkourTrajectory.Control(Math.atan2(dz,dx),forward,gap,
                crossing || dy>frame.vy*3);
    }
}
