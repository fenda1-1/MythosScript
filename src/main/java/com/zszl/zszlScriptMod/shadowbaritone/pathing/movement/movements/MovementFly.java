package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.zszl.zszlScriptMod.handlers.FlyHandler;
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
        // The script's movement fly feature supplies flight by controlling motion;
        // it does not grant Minecraft's creative-mode allowFlying capability.
        if (!context.getBaritone().getPlayerContext().player().capabilities.allowFlying && !FlyHandler.enabled) {
            return COST_INF;
        }
        int minimumAltitude = Math.max(1, Math.min(356, Baritone.settings().flightMinAltitude.value));
        if (dy < minimumAltitude && (dx != x || dz != z)) {
            return COST_INF;
        }
        int clearance = Math.max(0, Baritone.settings().flightClearance.value);
        for (int cy = 0; cy <= clearance; cy++) {
            if (!MovementHelper.canFlyThrough(context, dx, dy + cy, dz)) return COST_INF;
        }
        double distance = Math.sqrt((dx - x) * (dx - x) + (dy - y) * (dy - y) + (dz - z) * (dz - z));
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

    @Override public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) return state;
        if (!isInsideRouteCorridor()) return state.setStatus(MovementStatus.UNREACHABLE);
        if (hasReachedDestination()) return state.setStatus(MovementStatus.SUCCESS);
        double dx = dest.x + 0.5D - ctx.player().posX;
        double dz = dest.z + 0.5D - ctx.player().posZ;
        int verticalDelta = dest.y - ctx.playerFeet().y;
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
