package com.mythos.mythosScriptMod.shadowbaritone.utils;

import com.mythos.mythosScriptMod.config.FlightPathingConfig;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalComposite;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalFlightLanding;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalXZ;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public final class GoalTargetNormalizer {

    private GoalTargetNormalizer() {
    }

    public static Goal normalize(IBaritone baritone, Goal goal) {
        if (goal == null) {
            return null;
        }
        if (goal instanceof GoalComposite) {
            return normalizeGoalComposite(baritone, (GoalComposite) goal);
        }
        if (BaritoneAPI.getSettings().allowFlightPathing.value && goal instanceof GoalXZ && baritone != null) {
            GoalXZ xz = (GoalXZ) goal;
            return goal instanceof GoalFlightXZ ? goal : new GoalFlightXZ(xz.getX(), xz.getZ());
        }
        if (!(goal instanceof GoalBlock)) {
            return goal;
        }
        if (BaritoneAPI.getSettings().allowFlightPathing.value) {
            if (goal instanceof GoalFlightExact && isReachableExactFlightTarget(baritone, (GoalBlock) goal)) {
                return goal;
            }
            GoalBlock normalized = normalizeFlightGoalBlock(baritone, (GoalBlock) goal);
            if (goal instanceof GoalFlightLanding && goal.equals(new GoalFlightLanding(normalized.getGoalPos()))) {
                return goal;
            }
            return new GoalFlightLanding(normalized.getGoalPos());
        }
        // Flight arrival radii must not leak into walking/parkour commands.
        // Explicit XYZ goto creates GoalFlightExact before normalization.
        GoalBlock groundGoal = goal instanceof GoalFlightLanding
                ? ((GoalBlock) goal).exactX == null
                    ? new GoalBlock(((GoalBlock) goal).getGoalPos())
                    : new GoalBlock(((GoalBlock) goal).exactX, ((GoalBlock) goal).exactY, ((GoalBlock) goal).exactZ)
                : (GoalBlock) goal;
        return normalizeGoalBlock(baritone, groundGoal);
    }

    private static boolean isReachableExactFlightTarget(IBaritone baritone, GoalBlock goal) {
        if (baritone == null || goal == null) {
            return false;
        }
        BlockPos pos = goal.getGoalPos();
        if (pos.getY() < 0 || pos.getY() >= 255) {
            return true;
        }
        BlockStateInterface bsi = new BlockStateInterface(baritone.getPlayerContext());
        if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            return true;
        }
        int clearance = Math.max(0, BaritoneAPI.getSettings().flightClearance.value);
        for (int offset = 0; offset <= clearance; offset++) {
            if (!MovementHelper.canFlyThrough(bsi, pos.getX(), pos.getY() + offset, pos.getZ())) {
                return false;
            }
        }
        return true;
    }

    private static Goal normalizeGoalComposite(IBaritone baritone, GoalComposite goal) {
        Goal[] children = goal.goals();
        Goal[] normalized = new Goal[children.length];
        boolean changed = false;
        for (int i = 0; i < children.length; i++) {
            normalized[i] = normalize(baritone, children[i]);
            changed |= normalized[i] != children[i];
        }
        return changed ? new GoalComposite(normalized) : goal;
    }

    public static GoalBlock normalizeGoalBlock(IBaritone baritone, GoalBlock goal) {
        if (baritone == null || goal == null) {
            return goal;
        }
        // A fractional goto is the standing pose itself. Lifting it to the
        // next solid cell puts the marker inside the wall above a fence edge.
        if (goal.exactX != null) return goal;

        BlockPos pos = goal.getGoalPos();
        if (pos.getY() < 0 || pos.getY() >= 255) {
            return goal;
        }

        BlockStateInterface bsi = new BlockStateInterface(baritone.getPlayerContext());
        if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            return goal;
        }

        IBlockState targetState = bsi.get0(pos.getX(), pos.getY(), pos.getZ());
        // A hanging-vine goal identifies the occupied feet cell, not a block
        // whose top is a standing surface. Preserve it across normalization.
        if (targetState.getBlock() == net.minecraft.init.Blocks.VINE
                && BaritoneAPI.getSettings().allowVines.value
                && MovementHelper.canWalkThrough(bsi, pos.getX(), pos.getY(), pos.getZ(), targetState)) {
            return goal;
        }
        if (!BaritoneAPI.getSettings().allowBreak.value
                && !MovementHelper.canWalkThrough(bsi, pos.getX(), pos.getY(), pos.getZ(), targetState)) {
            // A fence post fills its cell for canWalkThrough, but the body
            // stands on its 1.5-tall top. Lifting the goal to the next solid
            // surface puts the marker on the wall above the real ledge.
            if (hasStandableTop(baritone, pos, targetState)) return goal;
            GoalBlock corrected = findFirstStandableGoalAboveTarget(bsi, pos);
            if (corrected != null) {
                return corrected;
            }
            return goal;
        }
        if (!MovementHelper.canWalkOn(bsi, pos.getX(), pos.getY(), pos.getZ(), targetState)) {
            return goal;
        }
        if (!MovementHelper.canWalkThrough(bsi, pos.getX(), pos.getY() + 1, pos.getZ())
                || !MovementHelper.canWalkThrough(bsi, pos.getX(), pos.getY() + 2, pos.getZ())) {
            return goal;
        }

        return new GoalBlock(pos.up());
    }

    private static GoalBlock normalizeFlightGoalBlock(IBaritone baritone, GoalBlock goal) {
        if (baritone == null || goal == null) {
            return goal;
        }
        BlockPos origin = goal.getGoalPos();
        if (origin.getY() < 0 || origin.getY() >= 255) {
            return goal;
        }
        BlockStateInterface bsi = new BlockStateInterface(baritone.getPlayerContext());
        if (!bsi.worldContainsLoadedChunk(origin.getX(), origin.getZ())) {
            return goal;
        }

        int searchRadius = Math.max(8, Math.min(16, FlightPathingConfig.arrivalRange));
        for (int radius = 0; radius <= searchRadius; radius++) {
            GoalBlock best = null;
            int bestVerticalDifference = Integer.MAX_VALUE;
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                        continue;
                    }
                    int x = origin.getX() + xOffset;
                    int z = origin.getZ() + zOffset;
                    if (!bsi.worldContainsLoadedChunk(x, z)) {
                        continue;
                    }
                    GoalBlock candidate = findHighestSkyAccessibleLanding(bsi, x, z, origin.getY());
                    if (candidate == null) {
                        continue;
                    }
                    int verticalDifference = Math.abs(candidate.y - origin.getY());
                    if (best == null || verticalDifference < bestVerticalDifference) {
                        best = candidate;
                        bestVerticalDifference = verticalDifference;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return goal;
    }

    private static GoalBlock findHighestSkyAccessibleLanding(BlockStateInterface bsi, int x, int z, int minimumY) {
        for (int feetY = 254; feetY >= Math.max(1, minimumY); feetY--) {
            if (!MovementHelper.canWalkThrough(bsi, x, feetY, z)
                    || !MovementHelper.canWalkThrough(bsi, x, feetY + 1, z)) {
                return null;
            }
            if (MovementHelper.canWalkOn(bsi, x, feetY - 1, z)) {
                return new GoalBlock(x, feetY, z);
            }
        }
        return null;
    }

    /** True when a body can stand on this cell's collision top, even if the cell is not walk-through. */
    private static boolean hasStandableTop(IBaritone baritone, BlockPos pos, IBlockState state) {
        if (!BaritoneAPI.getSettings().parkourMode.value) return false;
        CalculationContext context = new CalculationContext(baritone, false);
        BetterBlockPos feet = new BetterBlockPos(pos.getX(), pos.getY() + 1, pos.getZ());
        AxisAlignedBB support = ParkourSurface.support(context, feet);
        return support != null && ParkourSurface.standPoint(context, feet, support) != null;
    }

    private static GoalBlock findFirstStandableGoalAboveTarget(BlockStateInterface bsi, BlockPos origin) {
        // A coordinate inside a solid block must resolve from that coordinate upward.
        // Never fall back to a lower or top-down scan, because either can select a
        // different floor in the same X/Z column.
        int firstFeetY = Math.max(1, origin.getY() + 1);
        int maxFeetY = 254;
        for (int y = firstFeetY; y <= maxFeetY; y++) {
            if (!MovementHelper.canWalkThrough(bsi, origin.getX(), y, origin.getZ())
                    || !MovementHelper.canWalkThrough(bsi, origin.getX(), y + 1, origin.getZ())) {
                continue;
            }
            IBlockState supportState = bsi.get0(origin.getX(), y - 1, origin.getZ());
            if (MovementHelper.canWalkOn(bsi, origin.getX(), y - 1, origin.getZ(), supportState)) {
                return new GoalBlock(origin.getX(), y, origin.getZ());
            }
        }
        return null;
    }
}
