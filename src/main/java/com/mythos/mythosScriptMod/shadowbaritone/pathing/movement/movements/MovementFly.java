package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.Movement;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.mythos.mythosScriptMod.handlers.FlyHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class MovementFly extends Movement {
    private static final int PREFERRED_GROUND_CLEARANCE = 5;
    private static final double ASCENT_PENALTY = 0.15D;
    private static final double ABOVE_CRUISE_ALTITUDE_PENALTY = 0.01D;
    private static final double LOW_CLEARANCE_PENALTY = 12.0D;
    private static final double OVERHEAD_PENALTY = 18.0D;
    private static final double ROUTE_CORRIDOR_RADIUS_SQ = 1.44D;

    public MovementFly(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, new BetterBlockPos[] { dest, dest.up() });
    }

    @Override public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.y, dest.z);
    }

    public static double cost(CalculationContext context, int x, int y, int z, int dx, int dy, int dz) {
        // Blink pathing teleports along these axis moves without flying, so it
        // skips the flight-capability and cruise-altitude gates; collision
        // checks still apply. Plain distance keeps routes flat near ground.
        boolean blink = Baritone.settings().allowBlinkPathing.value;
        if (!blink && !FlyHandler.enabled
                && !context.getBaritone().getPlayerContext().player().capabilities.allowFlying) {
            // The script's movement fly feature supplies flight by controlling motion;
            // it does not grant Minecraft's creative-mode allowFlying capability.
            // Check FlyHandler first: headless contexts have no player() to query.
            return COST_INF;
        }
        if (!blink) {
            int minimumAltitude = Math.max(1, Math.min(356, Baritone.settings().flightMinAltitude.value));
            if (dy < minimumAltitude && (dx != x || dz != z)) {
                return COST_INF;
            }
        }
        // Blink nodes are feet positions the player's 0.6x1.8 box gets
        // teleported through. The feet cell may legitimately hold a thin
        // standable surface (carpet, half slab, snow layer) whose collision
        // top the player stands on, so it must NOT be required fly-through —
        // blinkBoxFits checks the real collision shape instead. Only the
        // head cell needs to be passable.
        int clearance = blink ? 1 : Math.max(0, Baritone.settings().flightClearance.value);
        for (int cy = blink ? 1 : 0; cy <= clearance; cy++) {
            if (!MovementHelper.canFlyThrough(context, dx, dy + cy, dz)) return COST_INF;
        }
        double distance = Math.sqrt((dx - x) * (dx - x) + (dy - y) * (dy - y) + (dz - z) * (dz - z));
        if (blink) {
            return blinkBoxFits(context, dx, dy, dz) ? distance : COST_INF;
        }
        int ascent = Math.max(0, dy - y);
        int altitudeAboveStart = Math.max(0, dy - context.preferredFlightY);
        double clearancePenalty = 0.0D;
        if (dy == y && (dx != x || dz != z)) {
            for (int offset = 1; offset <= PREFERRED_GROUND_CLEARANCE; offset++) {
                if (dy - offset < 0 || !MovementHelper.canFlyThrough(context, dx, dy - offset, dz)) {
                    clearancePenalty += LOW_CLEARANCE_PENALTY;
                }
                if (!MovementHelper.canFlyThrough(context, dx, dy + clearance + offset, dz)) {
                    clearancePenalty += OVERHEAD_PENALTY;
                }
            }
        }
        return distance + ascent * ASCENT_PENALTY
                + altitudeAboveStart * ABOVE_CRUISE_ALTITUDE_PENALTY + clearancePenalty;
    }

    /**
     * Highest collision-box top inside this column the player's feet can rest
     * on. A node is a feet CELL, but the actual feet Y is the top of a thin
     * floor: carpet 0.0625, half slab 0.5, snow layers, soul sand 0.875, and
     * a fence/wall post reaching up from the cell below (top dy+0.5 when the
     * feet cell sits above the post). Any collision top above dy+1 belongs to
     * a wall occupying this cell, not a floor. Returned Y is in [dy, dy+1].
     */
    public static double blinkSurfaceY(CalculationContext context, int dx, int dy, int dz) {
        return blinkSurfaceY(context == null ? null : context.world, context, dx, dy, dz);
    }

    /**
     * Live-world variant for the executor: identical rule but the collision
     * boxes come from addCollisionBoxToList straight off the world, so the
     * route builder can land hops on the real surface Y (carpet 5.0625, slab
     * 5.5, fence top 6.5) instead of the integer cell bottom that wedges the
     * box into the block and makes collision physics kick the player back.
     */
    public static double blinkSurfaceY(World world, int dx, int dy, int dz) {
        return blinkSurfaceY(world, null, dx, dy, dz);
    }

    private static double blinkSurfaceY(World world, CalculationContext context, int dx, int dy, int dz) {
        // Query only this column's own cells: the box the node produces is
        // centred in the cell (0.201..0.799), so support outside the column
        // can't carry it anyway.
        AxisAlignedBB foot = new AxisAlignedBB(dx + 0.2, dy, dz + 0.2, dx + 0.8, dy + 0.05, dz + 0.8);
        double surface = dy;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (int cy = dy - 1; cy <= dy; cy++) {
            pos.setPos(dx, cy, dz);
            if (context != null) {
                for (AxisAlignedBB box : context.collisionBoxes(pos, foot)) {
                    if (box.maxY <= dy + 1.0 && box.maxY > surface) {
                        surface = box.maxY;
                    }
                }
            } else if (world != null) {
                world.getBlockState(pos).addCollisionBoxToList(world, pos, foot, boxes, null, false);
                for (AxisAlignedBB box : boxes) {
                    if (box.maxY <= dy + 1.0 && box.maxY > surface) {
                        surface = box.maxY;
                    }
                }
                boxes.clear();
            }
        }
        return surface;
    }

    /**
     * Teleporting drops the 0.6x1.8 player box at the node's surface Y (see
     * {@link #blinkSurfaceY}), not the integer cell bottom: a carpet or slab
     * top the player genuinely stands on must not count as an intersection,
     * while a fence post poking through the cell still rejects it. A node
     * fits only when the head cell is passable and no neighbouring collision
     * box clips the raised player box.
     */
    public static boolean blinkBoxFits(CalculationContext context, int dx, int dy, int dz) {
        if (!MovementHelper.canFlyThrough(context, dx, dy + 1, dz)) {
            return false;
        }
        double surfaceY = blinkSurfaceY(context, dx, dy, dz);
        AxisAlignedBB playerBox = new AxisAlignedBB(
                dx + 0.5 - RouteCollisionSampler.DEFAULT_PLAYER_HALF_WIDTH, surfaceY,
                dz + 0.5 - RouteCollisionSampler.DEFAULT_PLAYER_HALF_WIDTH,
                dx + 0.5 + RouteCollisionSampler.DEFAULT_PLAYER_HALF_WIDTH, surfaceY + 1.8,
                dz + 0.5 + RouteCollisionSampler.DEFAULT_PLAYER_HALF_WIDTH);
        // collisionBoxes() goes through addCollisionBoxToList, which is the only
        // source of the real collision shape: fences/walls return a 1.5-tall
        // post here while getCollisionBoundingBox only exposes the 1.0-tall
        // selection box and lets the planner route feet through the post top.
        for (int cx = dx - 1; cx <= dx + 1; cx++) {
            for (int cy = dy - 1; cy <= dy + 2; cy++) {
                for (int cz = dz - 1; cz <= dz + 1; cz++) {
                    for (AxisAlignedBB box : context.collisionBoxes(new BlockPos(cx, cy, cz), playerBox)) {
                        if (playerBox.intersects(box)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> positions = ImmutableSet.builder();
        addArrivalArea(positions, src);
        addArrivalArea(positions, dest);
        return positions.build();
    }

    @Override
    protected boolean shouldAutoSwimInLiquid() {
        return false;
    }

    @Override
    protected List<BetterBlockPos> preparationBreakPositions() {
        // Ascents thread narrow shafts (e.g. a 2x2 hole in a floor slab). The
        // base implementation also probes src.up(2) and, when the feet cell is
        // merely inside the arrival area, feet.up(2) -- one block beside the
        // shaft that cell is the floor slab itself, so the movement was
        // declared UNREACHABLE on its first tick. The planner already
        // validated dest..dest+flightClearance via canFlyThrough, so the
        // static positionsToBreak list (dest, dest.up) is sufficient.
        return new ArrayList<>(Arrays.asList(positionsToBreak));
    }

    @Override public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) return state;
        if (hasReachedDestination()) return state.setStatus(MovementStatus.SUCCESS);
        double targetX = dest.x + 0.5D;
        double targetZ = dest.z + 0.5D;
        int targetY = dest.y;
        if (!isInsideRouteCorridor()) {
            // Player drifted off this segment (flight momentum, knockback): steer
            // back to the nearest point on the src->dest corridor instead of
            // failing. Only give up past 5 blocks, where a replan is cheaper.
            double segX = dest.x - src.x;
            double segZ = dest.z - src.z;
            double lenSq = segX * segX + segZ * segZ;
            double sx = src.x + 0.5D;
            double sz = src.z + 0.5D;
            double t = lenSq < 1.0E-6D ? 0.0D
                    : ((ctx.player().posX - sx) * segX + (ctx.player().posZ - sz) * segZ) / lenSq;
            t = Math.max(0.0D, Math.min(1.0D, t));
            targetX = sx + segX * t;
            targetZ = sz + segZ * t;
            int loY = Math.min(src.y, dest.y);
            int hiY = Math.max(src.y, dest.y);
            int feetY = ctx.playerFeet().y;
            targetY = feetY < loY ? loY : Math.min(feetY, hiY);
            double offX = ctx.player().posX - targetX;
            double offZ = ctx.player().posZ - targetZ;
            if (offX * offX + offZ * offZ > 25.0D) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }
        }
        double dx = targetX - ctx.player().posX;
        double dz = targetZ - ctx.player().posZ;
        int verticalDelta = targetY - ctx.playerFeet().y;
        // Cap per-tick speed by remaining distance so fly momentum cannot overshoot
        // the single-block target and trip the corridor check above.
        FlyHandler.INSTANCE.setPathingSpeedCaps(
                Math.min(FlyHandler.horizontalSpeed, Math.max(0.18D, Math.hypot(dx, dz) * 0.65D)),
                Math.min(FlyHandler.verticalSpeed, Math.max(0.05D, Math.abs(targetY - ctx.player().posY) * 0.65D)));
        MovementHelper.moveForwardWithRotation(ctx, state, new net.minecraft.util.math.Vec3d(dx, 0.0D, dz));
        // Flight must keep its forward momentum through adjacent route nodes. Turning
        // is handled by the target rotation, not by alternating strafe and back inputs.
        state.setInput(Input.MOVE_FORWARD, true);
        state.setInput(Input.MOVE_BACK, false);
        state.setInput(Input.MOVE_LEFT, false);
        state.setInput(Input.MOVE_RIGHT, false);
        state.setInput(Input.JUMP, verticalDelta > 0);
        state.setInput(Input.SNEAK, verticalDelta < 0);
        return state;
    }

    private boolean isInsideRouteCorridor() {
        int feetY = ctx.playerFeet().y;
        if (feetY < Math.min(src.y, dest.y) - 1 || feetY > Math.max(src.y, dest.y) + 1) {
            return false;
        }
        double startX = src.x + 0.5D;
        double startZ = src.z + 0.5D;
        double endX = dest.x + 0.5D;
        double endZ = dest.z + 0.5D;
        double segmentX = endX - startX;
        double segmentZ = endZ - startZ;
        double lengthSq = segmentX * segmentX + segmentZ * segmentZ;
        if (lengthSq < 1.0E-6D) {
            return Math.abs(ctx.player().posX - startX) <= 1.2D && Math.abs(ctx.player().posZ - startZ) <= 1.2D;
        }
        double projection = ((ctx.player().posX - startX) * segmentX + (ctx.player().posZ - startZ) * segmentZ)
                / lengthSq;
        projection = Math.max(-0.25D, Math.min(1.25D, projection));
        double nearestX = startX + segmentX * projection;
        double nearestZ = startZ + segmentZ * projection;
        double offsetX = ctx.player().posX - nearestX;
        double offsetZ = ctx.player().posZ - nearestZ;
        return offsetX * offsetX + offsetZ * offsetZ <= ROUTE_CORRIDOR_RADIUS_SQ;
    }

    private boolean hasReachedDestination() {
        if (ctx.playerFeet().equals(dest)) {
            return true;
        }
        if (src.y != dest.y || ctx.playerFeet().y != dest.y) {
            return false;
        }
        double segmentX = dest.x - src.x;
        double segmentZ = dest.z - src.z;
        double lengthSq = segmentX * segmentX + segmentZ * segmentZ;
        if (lengthSq < 1.0E-6D) {
            return false;
        }
        double progress = ((ctx.player().posX - (src.x + 0.5D)) * segmentX
                + (ctx.player().posZ - (src.z + 0.5D)) * segmentZ) / lengthSq;
        return progress >= 0.85D;
    }

    private static void addArrivalArea(ImmutableSet.Builder<BetterBlockPos> positions, BetterBlockPos center) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                positions.add(new BetterBlockPos(center.x + xOffset, center.y, center.z + zOffset));
            }
        }
    }
}
