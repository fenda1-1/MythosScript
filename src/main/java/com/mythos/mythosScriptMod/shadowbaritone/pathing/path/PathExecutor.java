/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mythos.mythosScriptMod.shadowbaritone.pathing.path;

import com.mythos.mythosScriptMod.config.BlinkPathingConfig;
import com.mythos.mythosScriptMod.config.DebugModule;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.*;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.behavior.PathingBehavior;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AbstractNodeCostSearch;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.Movement;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.PathingSpeedController;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.*;
import com.mythos.mythosScriptMod.shadowbaritone.utils.BlockStateInterface;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.*;

import static com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus.*;

/**
 * Behavior to execute a precomputed path
 *
 * @author leijurv
 */
public class PathExecutor implements IPathExecutor, Helper {

    private static final double MAX_MAX_DIST_FROM_PATH = 3;
    private static final double MAX_DIST_FROM_PATH = 2;
    private static final double ROUTE_PROGRESS_LENIENCY = 0.9D;
    private static final double ROUTE_PAUSE_LENIENCY = 0.85D;
    private static final double NARROW_GAP_ROUTE_LENIENCY = 1.25D;
    private static final int MAX_PATH_REEVALUATIONS_PER_TICK = 96;
    private static final int MAX_ROUTE_PROGRESS_LOOKAHEAD = 4;

    /**
     * Default value is equal to 10 seconds. It's find to decrease it, but it must
     * be at least 5.5s (110 ticks).
     * For more information, see issue #102.
     *
     * @see <a href="https://github.com/cabaletta/baritone/issues/102">Issue
     *      #102</a>
     * @see <a href="https://i.imgur.com/5s5GLnI.png">Anime</a>
     */
    private static final double MAX_TICKS_AWAY = 200;

    private final IPath path;
    private int pathPosition;
    private int ticksAway;
    private int ticksOnCurrent;
    private Double currentMovementOriginalCostEstimate;
    private Integer costEstimateIndex;
    private boolean failed;
    private boolean recalcBP = true;
    private double blinkLift;
    private int blinkStuckTicks;
    private double blinkHoldY = Double.NaN;
    private HashSet<BlockPos> toBreak = new HashSet<>();
    private HashSet<BlockPos> toPlace = new HashSet<>();
    private HashSet<BlockPos> toWalkInto = new HashSet<>();

    private final PathingBehavior behavior;
    private final IPlayerContext ctx;

    private boolean sprintNextTick;
    private int blinkTicksUntilStep;
    private boolean restartRequested;
    private boolean replanAtSafeBoundary;
    private final Map<String, Long> diagnosticTimes = new HashMap<>();

    @Override
    public void logDebug(String message) {
        long now = System.currentTimeMillis();
        if (ParkourDebugLog.enabled() && now - diagnosticTimes.getOrDefault(message, 0L) >= 1000) {
            ParkourDebugLog.INSTANCE.event("path_executor position=" + pathPosition + " feet="
                    + ctx.playerFeet() + " reason=" + message);
            if (diagnosticTimes.size() >= 32) diagnosticTimes.clear();
            diagnosticTimes.put(message, now);
        }
        Helper.super.logDebug(message);
    }

    public PathExecutor(PathingBehavior behavior, IPath path) {
        this.behavior = behavior;
        this.ctx = behavior.ctx;
        this.path = path;
        this.pathPosition = 0;
    }

    /**
     * Tick this executor
     *
     * @return True if a movement just finished (and the player is therefore in a
     *         "stable" state, like,
     *         not sneaking out over lava), false otherwise
     */
    public boolean onTick() {
        if (Baritone.settings().allowBlinkPathing.value) {
            return blinkAlongPath();
        }
        for (int reevaluations = 0; reevaluations < MAX_PATH_REEVALUATIONS_PER_TICK; reevaluations++) {
            this.restartRequested = false;

            if (pathPosition == path.length() - 1) {
                pathPosition++;
            }
            if (pathPosition >= path.length()) {
                return true; // stop bugging me, I'm done
            }
            Movement movement = (Movement) path.movements().get(pathPosition);
            // Finish a triggered launch, including consecutive piston pads, before
            // replacing the stale exploration tail. A pad contact is not a rest point.
            if (replanAtSafeBoundary && movement.safeToCancel()
                    && !(movement instanceof MovementPistonLaunch || movement instanceof MovementSlimeBounce)
                    && (ctx.player().onGround || ctx.player().isOnLadder() || ctx.player().isInWater())) {
                logDebug("Replanning at safe movement boundary after world/capability change");
                cancel();
                return true;
            }
            BetterBlockPos whereAmI = ctx.playerFeet();
            boolean protectedByNarrowGapRoute = (movement instanceof com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSlimeBounce
                    || movement instanceof com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch)
                    && movement.getValidPositions().contains(whereAmI)
                    || isProtectedNarrowGapMovement(movement, whereAmI)
                    || movement instanceof MovementParkour && (((MovementParkour) movement).isFollowingTrajectory()
                    || ((MovementParkour) movement).isStandingOnSourceSupport());

            boolean groundedHandoff = !Baritone.settings().parkourMode.value || ctx.player().onGround
                    || ctx.player().isOnLadder() || ctx.player().isInWater();
            if (groundedHandoff && tryAdvanceToCurrentPathPosition(whereAmI, movement, protectedByNarrowGapRoute)) {
                continue;
            }

            if (!movement.getValidPositions().contains(whereAmI) && !protectedByNarrowGapRoute) {
                for (int i = 0; i < pathPosition && i < path.length(); i++) {// this happens for example when you lag out
                                                                             // and get teleported back a couple blocks
                    if (matchesProgressPosition((Movement) path.movements().get(i), whereAmI)) {
                        int previousPos = pathPosition;
                        pathPosition = i;
                        for (int j = pathPosition; j <= previousPos; j++) {
                            path.movements().get(j).reset();
                        }
                        onChangeInPathPosition();
                        logOrbitDebug("positionRewind previous=%d new=%d feet=%s", previousPos, pathPosition,
                                formatPos(whereAmI));
                        this.restartRequested = true;
                        break;
                    }
                }
                if (this.restartRequested) {
                    continue;
                }
                for (int i = pathPosition + 3; i < path.length() - 1; i++) { // dont check pathPosition+1. the movement
                                                                             // tells us when it's done (e.g. sneak placing)
                    // also don't check pathPosition+2 because reasons
                    if (matchesProgressPosition((Movement) path.movements().get(i), whereAmI)) {
                        if (i - pathPosition > 2) {
                            logDebug("Skipping forward " + (i - pathPosition) + " steps, to " + i);
                        }
                        int previousPos = pathPosition;
                        pathPosition = i - 1;
                        onChangeInPathPosition();
                        logOrbitDebug("positionSkip previous=%d matched=%d new=%d feet=%s", previousPos, i, pathPosition,
                                formatPos(whereAmI));
                        this.restartRequested = true;
                        break;
                    }
                }
                if (this.restartRequested) {
                    continue;
                }
            }
            Tuple<Double, BlockPos> status = closestPathPos(path);
            if (!protectedByNarrowGapRoute && possiblyOffPath(status, MAX_DIST_FROM_PATH)) {
                ticksAway++;
                System.out.println("FAR AWAY FROM PATH FOR " + ticksAway + " TICKS. Current distance: " + status.getFirst()
                        + ". Threshold: " + MAX_DIST_FROM_PATH);
                if (ticksAway > MAX_TICKS_AWAY) {
                    logDebug("Too far away from path for too long, cancelling path");
                    cancel();
                    return false;
                }
            } else {
                ticksAway = 0;
            }
            if (!protectedByNarrowGapRoute && possiblyOffPath(status, MAX_MAX_DIST_FROM_PATH)) { // ok, stop right away, we're way too far.
                logDebug("too far from path");
                cancel();
                return false;
            }
            BlockStateInterface bsi = new BlockStateInterface(ctx);
            for (int i = pathPosition - 10; i < pathPosition + 10; i++) {
                if (i < 0 || i >= path.movements().size()) {
                    continue;
                }
                Movement m = (Movement) path.movements().get(i);
                List<BlockPos> prevBreak = m.toBreak(bsi);
                List<BlockPos> prevPlace = m.toPlace(bsi);
                List<BlockPos> prevWalkInto = m.toWalkInto(bsi);
                m.resetBlockCache();
                if (!prevBreak.equals(m.toBreak(bsi))) {
                    recalcBP = true;
                }
                if (!prevPlace.equals(m.toPlace(bsi))) {
                    recalcBP = true;
                }
                if (!prevWalkInto.equals(m.toWalkInto(bsi))) {
                    recalcBP = true;
                }
            }
            if (recalcBP) {
                HashSet<BlockPos> newBreak = new HashSet<>();
                HashSet<BlockPos> newPlace = new HashSet<>();
                HashSet<BlockPos> newWalkInto = new HashSet<>();
                for (int i = pathPosition; i < path.movements().size(); i++) {
                    Movement m = (Movement) path.movements().get(i);
                    newBreak.addAll(m.toBreak(bsi));
                    newPlace.addAll(m.toPlace(bsi));
                    newWalkInto.addAll(m.toWalkInto(bsi));
                }
                toBreak = newBreak;
                toPlace = newPlace;
                toWalkInto = newWalkInto;
                recalcBP = false;
            }
            if (pathPosition < path.movements().size() - 1) {
                IMovement next = path.movements().get(pathPosition + 1);
                if (!behavior.baritone.bsi.worldContainsLoadedChunk(next.getDest().x, next.getDest().z)) {
                    logDebug("Pausing since destination is at edge of loaded chunks");
                    clearKeys();
                    return true;
                }
            }
            boolean canCancel = movement.safeToCancel();
            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext verification =
                    new com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext(behavior.baritone, false);
            if (costEstimateIndex == null || costEstimateIndex != pathPosition) {
                costEstimateIndex = pathPosition;
                currentMovementOriginalCostEstimate = movement.getCost();
                for (int i = 1; i < Baritone.settings().costVerificationLookahead.value
                        && pathPosition + i < path.length() - 1; i++) {
                    if (((Movement) path.movements().get(pathPosition + i))
                            .calculateCost(verification) >= ActionCosts.COST_INF
                            && canCancel) {
                        logDebug(
                                "Something has changed in the world and a future movement has become impossible. Cancelling.");
                        cancel();
                        return true;
                    }
                }
            }
            double currentCost = movement.recalculateCost(verification);
            if (currentCost >= ActionCosts.COST_INF && canCancel) {
                logDebug("Something has changed in the world and this movement has become impossible. Cancelling.");
                cancel();
                return true;
            }
            if (!movement.calculatedWhileLoaded()
                    && currentCost - currentMovementOriginalCostEstimate > Baritone.settings().maxCostIncrease.value
                    && canCancel) {
                logDebug("Original cost " + currentMovementOriginalCostEstimate + " current cost " + currentCost
                        + ". Cancelling.");
                cancel();
                return true;
            }
            if (shouldPause()) {
                logDebug("Pausing since current best path is a backtrack");
                clearKeys();
                return true;
            }
            MovementStatus movementStatus = movement.update();
            if (movementStatus == UNREACHABLE || movementStatus == FAILED) {
                logOrbitDebug("movementStatus status=%s pathPos=%d feet=%s movement=%s", movementStatus, pathPosition,
                        formatPos(whereAmI), movement.getClass().getSimpleName());
                logDebug("Movement returns status " + movementStatus);
                cancel();
                return true;
            }
            if (movementStatus == SUCCESS) {
                logOrbitDebug("movementStatus status=SUCCESS pathPos=%d feet=%s movement=%s", pathPosition,
                        formatPos(whereAmI), movement.getClass().getSimpleName());
                if (ParkourDebugLog.enabled()
                        && (movement instanceof MovementAscend || movement instanceof MovementDescend)) {
                    ParkourDebugLog.INSTANCE.event("ordinary_handoff type=" + movement.getClass().getSimpleName()
                            + " source=" + formatPos(movement.getSrc()) + " destination=" + formatPos(movement.getDest())
                            + " ground=" + ctx.player().onGround + " ladder=" + ctx.player().isOnLadder()
                            + " water=" + ctx.player().isInWater() + " vy=" + ctx.player().motionY);
                }
                pathPosition++;
                onChangeInPathPosition();
                if (movement instanceof MovementParkour && !((MovementParkour) movement).hasImmediateContinuation()) {
                    return true;
                }
                continue;
            }

            sprintNextTick = shouldSprintNextTick();
            if (this.restartRequested) {
                continue;
            }

            ctx.player().setSprinting(sprintNextTick);
            if (!sprintNextTick) {
                ctx.player().setSprinting(false); // letting go of control doesn't make you stop sprinting actually
            }
            // Trajectory search has its own wall-clock deadline. Charging its
            // wait against movement time cancels valid low-ceiling runups before
            // the worker returns and repeatedly queues the same search.
            if (!(movement instanceof MovementParkour)
                    || !((MovementParkour) movement).isPlanningTrajectory()) {
                ticksOnCurrent++;
            }
            int movementTimeout = Baritone.settings().movementTimeoutTicks.value
                    + (movement instanceof MovementClimb ? 200 : 0);
            if (ticksOnCurrent > currentMovementOriginalCostEstimate + movementTimeout) {
                logDebug("This movement has taken too long (" + ticksOnCurrent + " ticks, expected "
                        + currentMovementOriginalCostEstimate + ", movement " + movement.getClass().getSimpleName() + "). Cancelling.");
                cancel();
                return true;
            }
            return canCancel; // movement is in progress, but if it reports cancellable, PathingBehavior is
                              // good to cut onto the next path
        }

        logDebug("Exceeded per-tick path reevaluation limit while resolving route progress. Cancelling current path.");
        cancel();
        return false;
    }

    private boolean tryAdvanceToCurrentPathPosition(BetterBlockPos whereAmI, Movement currentMovement,
            boolean protectedByNarrowGapRoute) {
        // A vine cell can be crossed with enough sideways momentum to fall out
        // again. Let the catch/climb controller confirm alignment and braking.
        if (currentMovement != null && (currentMovement instanceof MovementParkour
                || currentMovement instanceof MovementPillar)) {
            net.minecraft.block.Block targetBlock = ctx.world().getBlockState(currentMovement.getDest()).getBlock();
            if (targetBlock == net.minecraft.init.Blocks.VINE
                    || targetBlock == net.minecraft.init.Blocks.LADDER) return false;
        }
        // Entering a block's edge is not a completed swim: depth and alignment
        // are still controlled by the movement, especially before a turn.
        // Feet rounding reaches the next layer before a ladder-top exit has
        // acquired support. Pillar must finish the climb/placement itself.
        if (currentMovement instanceof MovementSwim || currentMovement instanceof MovementClimb
                || currentMovement instanceof MovementPillar
                || currentMovement instanceof MovementAscend && pathPosition == path.movements().size() - 1) {
            return false;
        }
        if (whereAmI == null || currentMovement == null) {
            return false;
        }
        if (protectedByNarrowGapRoute && !whereAmI.equals(currentMovement.getDest())) {
            return false;
        }

        AdvanceResolution advance = resolveAdvanceIndex(whereAmI);
        if (!advance.hasAdvance() || advance.index <= pathPosition) {
            return false;
        }

        if (!currentMovement.safeToCancel()) {
            return false;
        }

        if (!ctx.player().onGround
                && !(ctx.world().getBlockState(ctx.playerFeet()).getBlock() instanceof BlockLiquid)
                && ctx.player().motionY < -0.1D) {
            return false;
        }

        int previous = pathPosition;
        pathPosition = advance.index;
        onChangeInPathPosition();

        if (advance.index - previous > 0) {
            logDebug("Auto-advanced path position from " + previous + " to " + advance.index
                    + " due to high-speed progression / overshoot");
            logOrbitDebug(
                    "autoAdvance previous=%d new=%d mode=%s exactIndex=%d movementIndex=%d distance=%.3f feet=%s",
                    previous, advance.index, advance.mode, advance.exactIndex, advance.movementIndex, advance.distance,
                    formatPos(whereAmI));
        }
        return true;
    }

    private boolean isProtectedNarrowGapMovement(Movement movement, BetterBlockPos whereAmI) {
        if (!(movement instanceof MovementNarrowGapTraverse) || !(movement instanceof IRoutePointMovement)) {
            return false;
        }
        if (whereAmI != null && movement.getValidPositions().contains(whereAmI)) {
            return true;
        }
        if (ctx.player() == null) {
            return false;
        }
        return distanceToRoute((IRoutePointMovement) movement, ctx.player().getPositionVector()) <= NARROW_GAP_ROUTE_LENIENCY;
    }

    private AdvanceResolution resolveAdvanceIndex(BetterBlockPos whereAmI) {
        int exactIndex = path.positions().indexOf(whereAmI);
        if (exactIndex > pathPosition) {
            return AdvanceResolution.exact(exactIndex);
        }
        if (!allowRouteProgressMatching(path)) {
            if (exactIndex == pathPosition) {
                logOrbitDebug("routeProgressSuppressed pathPos=%d exactIndex=%d feet=%s", pathPosition, exactIndex,
                        formatPos(whereAmI));
            }
            return AdvanceResolution.none();
        }
        PathProgressMatch match = matchPathProgress(path, ROUTE_PROGRESS_LENIENCY, pathPosition + 1,
                Math.min(path.movements().size(), pathPosition + 1 + MAX_ROUTE_PROGRESS_LOOKAHEAD));
        if (match.hasMovementMatch() && match.getMovementIndex() > pathPosition) {
            return AdvanceResolution.route(match.getMovementIndex(), match.getExactPositionIndex(), match.getDistance());
        }
        return AdvanceResolution.none();
    }

    private Tuple<Double, BlockPos> closestPathPos(IPath path) {
        double best = -1;
        BlockPos bestPos = null;
        for (IMovement movement : path.movements()) {
            if (movement instanceof IRoutePointMovement) {
                Tuple<Double, BlockPos> routeStatus = closestRoutePoint((IRoutePointMovement) movement);
                if (routeStatus != null && (best < 0 || routeStatus.getFirst() < best)) {
                    best = routeStatus.getFirst();
                    bestPos = routeStatus.getSecond();
                }
            }
            for (BlockPos pos : ((Movement) movement).getValidPositions()) {
                double dist = VecUtils.entityDistanceToCenter(ctx.player(), pos);
                if (dist < best || best == -1) {
                    best = dist;
                    bestPos = pos;
                }
            }
        }
        return new Tuple<>(best, bestPos);
    }

    private Tuple<Double, BlockPos> closestRoutePoint(IRoutePointMovement movement) {
        Vec3d[] points = movement.getRoutePoints();
        if (points == null || points.length == 0) {
            return null;
        }
        Vec3d playerPos = ctx.player().getPositionVector();
        double best = -1.0D;
        Vec3d bestPoint = null;
        for (int i = 0; i < points.length - 1; i++) {
            Vec3d nearest = nearestPointOnSegment(playerPos, points[i], points[i + 1]);
            double distance = playerPos.distanceTo(nearest);
            if (best < 0 || distance < best) {
                best = distance;
                bestPoint = nearest;
            }
        }
        if (bestPoint == null) {
            bestPoint = points[0];
            best = playerPos.distanceTo(bestPoint);
        }
        return new Tuple<>(best, new BlockPos(bestPoint.x, bestPoint.y, bestPoint.z));
    }

    private static Vec3d nearestPointOnSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSq = segment.lengthSquared();
        if (lengthSq <= 1.0E-6D) {
            return start;
        }
        double t = point.subtract(start).dotProduct(segment) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return start.add(segment.scale(t));
    }

    private boolean shouldPause() {
        Optional<AbstractNodeCostSearch> current = behavior.getInProgress();
        if (!current.isPresent()) {
            return false;
        }
        if (!ctx.player().onGround) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, ctx.playerFeet().down())) {
            // we're in some kind of sketchy situation, maybe parkouring
            return false;
        }
        if (!MovementHelper.canWalkThrough(ctx, ctx.playerFeet())
                || !MovementHelper.canWalkThrough(ctx, ctx.playerFeet().up())) {
            // suffocating?
            return false;
        }
        if (!path.movements().get(pathPosition).safeToCancel()) {
            return false;
        }
        Optional<IPath> currentBest = current.get().bestPathSoFar();
        if (!currentBest.isPresent()) {
            return false;
        }
        IPath bestPath = currentBest.get();
        try {
            bestPath = bestPath.postProcess();
        } catch (Throwable ignored) {
        }
        if (bestPath.length() < 3) {
            return false; // not long enough yet to justify pausing, its far from certain we'll actually
                          // take this route
        }
        PathProgressMatch match = matchPathProgress(bestPath, ROUTE_PAUSE_LENIENCY, 1);
        if (match.hasExactPositionMatch()) {
            return match.getExactPositionIndex() > 1;
        }
        return match.hasMovementMatch() && match.getMovementIndex() > 0;
    }

    private boolean possiblyOffPath(Tuple<Double, BlockPos> status, double leniency) {
        double distanceFromPath = status.getFirst();
        if (pathPosition >= 0 && pathPosition < path.movements().size()
                && path.movements().get(pathPosition) instanceof MovementFlightCorridor) {
            // The movement itself owns corridor recovery and controlled replanning.
            // Generic path-distance cancellation would discard the visible corridor
            // as soon as a fast player crossed its boundary.
            return false;
        }
        if (distanceFromPath > leniency) {
            // when we're midair in the middle of a fall, we're very far from both the
            // beginning and the end, but we aren't actually off path
            if (path.movements().get(pathPosition) instanceof MovementFall) {
                BlockPos fallDest = path.positions().get(pathPosition + 1); // .get(pathPosition) is the block we fell
                                                                            // off of
                return VecUtils.entityFlatDistanceToCenter(ctx.player(), fallDest) >= leniency; // ignore Y by using
                                                                                                // flat distance
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Regardless of current path position, snap to the current player feet if
     * possible
     *
     * @return Whether or not it was possible to snap to the current player feet
     */
    public boolean snipsnapifpossible() {
        if (!ctx.player().onGround
                && !(ctx.world().getBlockState(ctx.playerFeet()).getBlock() instanceof BlockLiquid)) {
            // if we're falling in the air, and not in water, don't splice
            return false;
        } else {
            // we are either onGround or in liquid
            if (ctx.player().motionY < -0.1) {
                // if we are strictly moving downwards (not stationary)
                // we could be falling through water, which could be unsafe to splice
                return false; // so don't
            }
        }
        int index = resolveSnipIndex(ctx.playerFeet());
        if (index == -1) {
            return false;
        }
        pathPosition = index; // jump directly to current position
        clearKeys();
        return true;
    }

    private int resolveSnipIndex(BetterBlockPos whereAmI) {
        int exactIndex = path.positions().indexOf(whereAmI);
        if (exactIndex != -1) {
            return exactIndex;
        }
        if (!allowRouteProgressMatching(path)) {
            return -1;
        }
        PathProgressMatch match = matchPathProgress(path, ROUTE_PROGRESS_LENIENCY, 0);
        if (match.hasMovementMatch()) {
            return match.getMovementIndex();
        }
        return -1;
    }

    private boolean allowRouteProgressMatching(IPath candidatePath) {
        return !(candidatePath instanceof OrbitRoutePath);
    }

    private static boolean matchesProgressPosition(Movement movement, BetterBlockPos feet) {
        // A launch's airborne envelope protects its active controller, but is
        // not an entry point: joining there would skip the trigger/takeoff.
        if (movement instanceof MovementPistonLaunch || movement instanceof MovementSlimeBounce) {
            return movement.getSrc().equals(feet);
        }
        return movement.getValidPositions().contains(feet);
    }

    private PathProgressMatch matchPathProgress(IPath candidatePath, double routeLeniency, int movementStartIndex) {
        return matchPathProgress(candidatePath, routeLeniency, movementStartIndex, Integer.MAX_VALUE);
    }

    private PathProgressMatch matchPathProgress(IPath candidatePath, double routeLeniency, int movementStartIndex,
            int movementEndExclusive) {
        int exactPositionIndex = candidatePath.positions().indexOf(ctx.playerFeet());
        Vec3d playerPos = ctx.player().getPositionVector();
        List<IMovement> candidateMovements;
        try {
            candidateMovements = candidatePath.movements();
        } catch (IllegalStateException ignored) {
            return new PathProgressMatch(exactPositionIndex, -1, Double.POSITIVE_INFINITY);
        }
        int endExclusive = Math.min(candidateMovements.size(), Math.max(0, movementEndExclusive));
        for (int i = Math.max(0, movementStartIndex); i < endExclusive; i++) {
            IMovement movement = candidateMovements.get(i);
            if (movement instanceof Movement && matchesProgressPosition((Movement) movement, ctx.playerFeet())) {
                return new PathProgressMatch(exactPositionIndex, i, 0.0D);
            }
            // Parkour route points describe a jump, not a walkable corridor.
            // Proximity must not skip its approach: the next tick would rewind
            // to that approach because the player's feet are still outside the
            // jump's valid positions, causing an advance/rewind loop.
            if (movement instanceof IRoutePointMovement && !(movement instanceof MovementParkour)) {
                double distance = distanceToRoute((IRoutePointMovement) movement, playerPos);
                if (distance <= routeLeniency) {
                    return new PathProgressMatch(exactPositionIndex, i, distance);
                }
            }
        }
        return new PathProgressMatch(exactPositionIndex, -1, Double.POSITIVE_INFINITY);
    }

    private double distanceToRoute(IRoutePointMovement movement, Vec3d playerPos) {
        Vec3d[] points = movement.getRoutePoints();
        if (points == null || points.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.length - 1; i++) {
            Vec3d nearest = nearestPointOnSegment(playerPos, points[i], points[i + 1]);
            best = Math.min(best, playerPos.distanceTo(nearest));
        }
        return best == Double.POSITIVE_INFINITY ? playerPos.distanceTo(points[0]) : best;
    }

    private static final class PathProgressMatch {

        private final int exactPositionIndex;
        private final int movementIndex;
        private final double distance;

        private PathProgressMatch(int exactPositionIndex, int movementIndex, double distance) {
            this.exactPositionIndex = exactPositionIndex;
            this.movementIndex = movementIndex;
            this.distance = distance;
        }

        private boolean hasExactPositionMatch() {
            return exactPositionIndex != -1;
        }

        private int getExactPositionIndex() {
            return exactPositionIndex;
        }

        private boolean hasMovementMatch() {
            return movementIndex != -1 && distance < Double.POSITIVE_INFINITY;
        }

        private int getMovementIndex() {
            return movementIndex;
        }

        private double getDistance() {
            return distance;
        }
    }

    private static final class AdvanceResolution {
        private static final AdvanceResolution NONE = new AdvanceResolution(-1, "none", -1, -1,
                Double.POSITIVE_INFINITY);

        private final int index;
        private final String mode;
        private final int exactIndex;
        private final int movementIndex;
        private final double distance;

        private AdvanceResolution(int index, String mode, int exactIndex, int movementIndex, double distance) {
            this.index = index;
            this.mode = mode;
            this.exactIndex = exactIndex;
            this.movementIndex = movementIndex;
            this.distance = distance;
        }

        private boolean hasAdvance() {
            return index >= 0;
        }

        private static AdvanceResolution none() {
            return NONE;
        }

        private static AdvanceResolution exact(int index) {
            return new AdvanceResolution(index, "exact", index, -1, 0.0D);
        }

        private static AdvanceResolution route(int index, int exactIndex, double distance) {
            return new AdvanceResolution(index, "route", exactIndex, index, distance);
        }
    }

    private boolean shouldSprintNextTick() {
        boolean requested = behavior.baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT);

        // we'll take it from here, no need for minecraft to see we're holding down
        // control and sprint for us
        behavior.baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);

        IMovement current = path.movements().get(pathPosition);

        // This is a movement-safety requirement, not a sprint optimization.
        // Keep the active traverse in control so it continues to hold forward;
        // switching to MovementAscend immediately is what previously left the
        // player standing still until W was pressed manually.
        if (current instanceof MovementTraverse && pathPosition + 1 < path.movements().size()) {
            IMovement next = path.movements().get(pathPosition + 1);
            if (next instanceof MovementAscend
                    && current.getDirection().equals(next.getDirection().down())
                    && fenceBlocksStraightAscend(ctx, (MovementTraverse) current, (MovementAscend) next)) {
                ((MovementTraverse) current).requestAscendJump();
            }
        }

        // first and foremost, if allowSprint is off, or if we don't have enough hunger,
        // don't try and sprint
        if (!new CalculationContext(behavior.baritone, false).canSprint) {
            return false;
        }
        if (current instanceof MovementParkour
                || current instanceof com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch
                || current instanceof com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSlimeBounce) {
            // The trajectory explicitly chooses sprint versus braking each
            // tick. Generic traversal sprinting must not override that choice.
            return requested;
        }
        if (PathingSpeedController.shouldHoldSprint(ctx.player())) {
            return false;
        }
        // traverse requests sprinting, so we need to do this check first
        if (current instanceof MovementTraverse && pathPosition < path.length() - 3) {
            IMovement next = path.movements().get(pathPosition + 1);
            boolean straightAscend = next instanceof MovementAscend
                    && current.getDirection().equals(next.getDirection().down());
            boolean sprintableAscend = straightAscend && sprintableAscend(ctx, (MovementTraverse) current,
                    (MovementAscend) next, path.movements().get(pathPosition + 2));
            if (sprintableAscend) {
                if (skipNow(ctx, current)) {
                    logDebug("Skipping traverse to straight ascend");
                    pathPosition++;
                    onChangeInPathPosition();
                    this.restartRequested = true;
                    behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    return true;
                } else {
                    logDebug("Too far to the side to safely sprint ascend");
                }
            }
        }

        // if the movement requested sprinting, then we're done
        if (requested) {
            return true;
        }

        // however, descend and ascend don't request sprinting, because they don't know
        // the context of what movement comes after it
        if (current instanceof MovementDescend) {

            if (pathPosition < path.length() - 2) {
                // keep this out of onTick, even if that means a tick of delay before it has an
                // effect
                IMovement next = path.movements().get(pathPosition + 1);
                if (MovementHelper.canUseFrostWalker(ctx, next.getDest().down())) {
                    // frostwalker only works if you cross the edge of the block on ground so in
                    // some cases we may not overshoot
                    // Since MovementDescend can't know the next movement we have to tell it
                    if (next instanceof MovementTraverse || next instanceof MovementParkour) {
                        boolean couldPlaceInstead = Baritone.settings().allowPlace.value
                                && behavior.baritone.getInventoryBehavior().hasGenericThrowaway()
                                && next instanceof MovementParkour; // traverse doesn't react fast enough
                        // this is true if the next movement does not ascend or descends and goes into
                        // the same cardinal direction (N-NE-E-SE-S-SW-W-NW) as the descend
                        // in that case current.getDirection() is e.g. (0, -1, 1) and
                        // next.getDirection() is e.g. (0, 0, 3) so the cross product of (0, 0, 1) and
                        // (0, 0, 3) is taken, which is (0, 0, 0) because the vectors are colinear
                        // (don't form a plane)
                        // since movements in exactly the opposite direction (e.g. descend (0, -1, 1)
                        // and traverse (0, 0, -1)) would also pass this check we also have to rule out
                        // that case
                        // we can do that by adding the directions because traverse is always 1 long
                        // like descend and parkour can't jump through current.getSrc().down()
                        boolean sameFlatDirection = !current.getDirection().up().add(next.getDirection())
                                .equals(BlockPos.ORIGIN)
                                && current.getDirection().up().crossProduct(next.getDirection())
                                        .equals(BlockPos.ORIGIN); // here's why you learn maths in school
                        if (sameFlatDirection && !couldPlaceInstead) {
                            ((MovementDescend) current).forceSafeMode();
                        }
                    }
                }
            }
            if (((MovementDescend) current).safeMode() && !((MovementDescend) current).skipToAscend()) {
                logDebug("Sprinting would be unsafe");
                return false;
            }

            if (pathPosition < path.length() - 2) {
                IMovement next = path.movements().get(pathPosition + 1);
                if (next instanceof MovementAscend && current.getDirection().up().equals(next.getDirection().down())) {
                    // a descend then an ascend in the same direction
                    pathPosition++;
                    onChangeInPathPosition();
                    this.restartRequested = true;
                    // okay to skip clearKeys and / or onChangeInPathPosition here since this isn't
                    // possible to repeat, since it's asymmetric
                    logDebug("Skipping descend to straight ascend");
                    return true;
                }
                if (canSprintFromDescendInto(ctx, current, next)) {

                    if (next instanceof MovementDescend && pathPosition < path.length() - 3) {
                        IMovement next_next = path.movements().get(pathPosition + 2);
                        if (next_next instanceof MovementDescend && !canSprintFromDescendInto(ctx, next, next_next)) {
                            return false;
                        }

                    }
                    if (ctx.playerFeet().equals(current.getDest())) {
                        pathPosition++;
                        onChangeInPathPosition();
                        this.restartRequested = true;
                    }

                    return true;
                }
                // logDebug("Turning off sprinting " + movement + " " + next + " " +
                // movement.getDirection() + " " + next.getDirection().down() + " " +
                // next.getDirection().down().equals(movement.getDirection()));
            }
        }
        if (current instanceof MovementAscend && pathPosition != 0) {
            IMovement prev = path.movements().get(pathPosition - 1);
            if (prev instanceof MovementDescend && prev.getDirection().up().equals(current.getDirection().down())) {
                BlockPos center = current.getSrc().up();
                // playerFeet adds 0.1251 to account for soul sand
                // farmland is 0.9375
                // 0.07 is to account for farmland
                if (ctx.player().posY >= center.getY() - 0.07) {
                    behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                    return true;
                }
            }
            if (pathPosition < path.length() - 2 && prev instanceof MovementTraverse && sprintableAscend(ctx,
                    (MovementTraverse) prev, (MovementAscend) current, path.movements().get(pathPosition + 1))) {
                return true;
            }
        }
        if (current instanceof MovementFall) {
            Tuple<Vec3d, BlockPos> data = overrideFall((MovementFall) current);
            if (data != null) {
                BetterBlockPos fallDest = new BetterBlockPos(data.getSecond());
                if (!path.positions().contains(fallDest)) {
                    throw new IllegalStateException();
                }
                if (ctx.playerFeet().equals(fallDest)) {
                    pathPosition = path.positions().indexOf(fallDest);
                    onChangeInPathPosition();
                    this.restartRequested = true;
                    return true;
                }
                clearKeys();
                behavior.baritone.getLookBehavior().updateTarget(
                        RotationUtils.calcRotationFromVec3d(ctx.playerHead(), data.getFirst(), ctx.playerRotations()),
                        false);
                behavior.baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                return true;
            }
        }

        if (current instanceof MovementTraverse
                || current instanceof MovementRouteTraverse
                || current instanceof MovementDiagonal
                || current instanceof MovementParkour) {
            return true;
        }

        return false;
    }

    private Tuple<Vec3d, BlockPos> overrideFall(MovementFall movement) {
        Vec3i dir = movement.getDirection();
        if (dir.getY() < -3) {
            return null;
        }
        if (!movement.toBreakCached.isEmpty()) {
            return null; // it's breaking
        }
        Vec3i flatDir = new Vec3i(dir.getX(), 0, dir.getZ());
        int i;
        outer: for (i = pathPosition + 1; i < path.length() - 1 && i < pathPosition + 3; i++) {
            IMovement next = path.movements().get(i);
            if (!(next instanceof MovementTraverse)) {
                break;
            }
            if (!flatDir.equals(next.getDirection())) {
                break;
            }
            for (int y = next.getDest().y; y <= movement.getSrc().y + 1; y++) {
                BlockPos chk = new BlockPos(next.getDest().x, y, next.getDest().z);
                if (!MovementHelper.fullyPassable(ctx, chk)) {
                    break outer;
                }
            }
            if (!MovementHelper.canWalkOn(ctx, next.getDest().down())) {
                break;
            }
        }
        i--;
        if (i == pathPosition) {
            return null; // no valid extension exists
        }
        double len = i - pathPosition - 0.4;
        return new Tuple<>(
                new Vec3d(flatDir.getX() * len + movement.getDest().x + 0.5, movement.getDest().y,
                        flatDir.getZ() * len + movement.getDest().z + 0.5),
                movement.getDest().add(flatDir.getX() * (i - pathPosition), 0, flatDir.getZ() * (i - pathPosition)));
    }

    private static boolean skipNow(IPlayerContext ctx, IMovement current) {
        double offTarget = Math.abs(current.getDirection().getX() * (current.getSrc().z + 0.5D - ctx.player().posZ))
                + Math.abs(current.getDirection().getZ() * (current.getSrc().x + 0.5D - ctx.player().posX));
        if (offTarget > 0.1) {
            return false;
        }
        // we are centered
        BlockPos headBonk = current.getSrc().subtract(current.getDirection()).up(2);
        if (MovementHelper.fullyPassable(ctx, headBonk)) {
            return true;
        }
        // wait 0.3
        double flatDist = Math.abs(current.getDirection().getX() * (headBonk.getX() + 0.5D - ctx.player().posX))
                + Math.abs(current.getDirection().getZ() * (headBonk.getZ() + 0.5 - ctx.player().posZ));
        return flatDist > 0.8;
    }

    private static boolean sprintableAscend(IPlayerContext ctx, MovementTraverse current, MovementAscend next,
            IMovement nextnext) {
        if (!Baritone.settings().sprintAscends.value) {
            return false;
        }
        if (!current.getDirection().equals(next.getDirection().down())) {
            return false;
        }
        if (nextnext.getDirection().getX() != next.getDirection().getX()
                || nextnext.getDirection().getZ() != next.getDirection().getZ()) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, current.getDest().down())) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, next.getDest().down())) {
            return false;
        }
        if (!next.toBreakCached.isEmpty()) {
            return false; // it's breaking
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos chk = current.getSrc().up(y);
                if (x == 1) {
                    chk = chk.add(current.getDirection());
                }
                // A fence in a head-clearance cell is intentionally handled by
                // the jump movement. It must not disable the straight
                // traverse-to-ascend hand-off (which would leave the player
                // pressed against the fence). Feet-level fences remain blocking.
                if (!isAscendClearancePassable(ctx, chk, y)) {
                    return false;
                }
            }
        }
        if (MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().up(3)).getBlock())) {
            return false;
        }
        return !MovementHelper.avoidWalkingInto(ctx.world().getBlockState(next.getDest().up(2)).getBlock()); // codacy
                                                                                                             // smh my
                                                                                                             // head
    }

    private static boolean isAscendClearancePassable(IPlayerContext ctx, BlockPos pos, int relativeY) {
        if (relativeY > 0 && MovementHelper.isFenceLike(ctx.world().getBlockState(pos).getBlock())) {
            return true;
        }
        return MovementHelper.fullyPassable(ctx, pos);
    }

    /**
     * With sprintAscends disabled Baritone normally completes the horizontal
     * traverse before starting MovementAscend.  That is not possible when the
     * traverse's head cell contains a fence: the player can jump from the
     * preceding block, but cannot enter the fence's collision cell first.  Use
     * the same hand-off as sprint ascends, without enabling sprinting.
     */
    private static boolean fenceBlocksStraightAscend(IPlayerContext ctx, MovementTraverse current,
            MovementAscend next) {
        if (Baritone.settings().allowBreak.value) {
            return false;
        }
        BlockPos src = current.getSrc();
        BlockPos dest = current.getDest();
        BlockPos nextDest = next.getDest();
        return isOverheadFence(ctx, src.up())
                || isOverheadFence(ctx, dest.up())
                || isOverheadFence(ctx, src.up(2))
                || isOverheadFence(ctx, nextDest.up())
                || isOverheadFence(ctx, nextDest.up(2));
    }

    private static boolean isOverheadFence(IPlayerContext ctx, BlockPos pos) {
        return MovementHelper.isFenceLike(ctx.world().getBlockState(pos).getBlock());
    }

    private static boolean canSprintFromDescendInto(IPlayerContext ctx, IMovement current, IMovement next) {
        if (next instanceof MovementDescend && next.getDirection().equals(current.getDirection())) {
            return true;
        }
        if (!MovementHelper.canWalkOn(ctx, current.getDest().add(current.getDirection()))) {
            return false;
        }
        if (next instanceof MovementTraverse && next.getDirection().down().equals(current.getDirection())) {
            return true;
        }
        return next instanceof MovementDiagonal && Baritone.settings().allowOvershootDiagonalDescend.value;
    }

    /**
     * Blink pathing: advance along the ordinary ground path's node polyline via
     * teleports instead of driving movement inputs. Path selection is unchanged;
     * only execution differs.
     */
    private boolean blinkAlongPath() {
        List<BetterBlockPos> positions = path.positions();
        if (pathPosition >= path.length()) {
            return true;
        }
        if (positions.size() < 2) {
            pathPosition = path.length();
            return true;
        }
        EntityPlayerSP player = ctx.player();
        if (player == null) {
            cancel();
            return false;
        }
        clearKeys();
        if (--blinkTicksUntilStep > 0) {
            return true;
        }
        blinkTicksUntilStep = Math.max(1, BlinkPathingConfig.tickInterval);

        // Fast-forward past nodes already behind the player, so a fresh path
        // (replan/splice) never snaps the player back to its first node.
        while (pathPosition + 1 < positions.size()
                && distSqToNode(player, positions.get(pathPosition + 1))
                        <= distSqToNode(player, positions.get(pathPosition))) {
            pathPosition++;
        }
        if (pathPosition + 1 >= positions.size()) {
            pathPosition = path.length();
            return true;
        }

        // Escape wedged-inside-a-block first (server pullbacks and corner
        // clips can embed the feet box in a wall face): probe straight up in
        // small steps up to the per-packet limit — a 5-block wall needs ~6 —
        // then probe every horizontal direction for the nearest free spot.
        if (player.world != null && player.world.collidesWithAnyBlock(player.getEntityBoundingBox())) {
            double liftCap = Math.min(BlinkPathingConfig.maxVerticalStep, 9.0D);
            for (double lift = 0.25D; lift <= liftCap + 1.0E-4D; lift += 0.25D) {
                if (!player.world.collidesWithAnyBlock(player.getEntityBoundingBox().offset(0, lift, 0))) {
                    blinkTeleport(player, player.posX, player.posY + lift, player.posZ);
                    blinkHoldY = player.posY;
                    return true;
                }
            }
            // Fully sealed — find the closest collision-free offset, any
            // direction, instead of shoving deeper into the wall.
            double[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
                    { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
            AxisAlignedBB box = player.getEntityBoundingBox();
            for (double reach = 0.5D; reach <= 4.0D; reach += 0.5D) {
                for (double[] d : dirs) {
                    double nl = Math.sqrt(d[0] * d[0] + d[1] * d[1]);
                    double mx = d[0] / nl * reach;
                    double mz = d[1] / nl * reach;
                    for (double dy = 1.0D; dy >= -1.0D; dy -= 0.5D) {
                        if (!player.world.collidesWithAnyBlock(box.offset(mx, dy, mz))) {
                            blinkTeleport(player, player.posX + mx, player.posY + dy, player.posZ + mz);
                            blinkHoldY = player.posY;
                            return true;
                        }
                    }
                }
            }
            return true;
        }

        // Rectilinear route: every path segment is flattened into
        // axis-aligned legs — climb first when the node rises, drop last when
        // it falls, and the horizontal corner picks the side that sweeps
        // farther — so no hop ever aims along a diagonal that can clip a
        // fence corner or wall edge.
        List<Vec3d> route = blinkRoute(positions, pathPosition,
                new Vec3d(player.posX, player.posY, player.posZ), player);
        if (ParkourDebugLog.enabled()) {
            StringBuilder sb = new StringBuilder("blink pos=")
                    .append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", player.posX, player.posY, player.posZ))
                    .append(" pathPos=").append(pathPosition)
                    .append(" node=").append(positions.get(Math.min(pathPosition, positions.size() - 1)))
                    .append(" route=");
            for (int i = 0; i < Math.min(route.size(), 8); i++) {
                Vec3d v = route.get(i);
                sb.append(i == 0 ? "" : " > ").append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", v.x, v.y, v.z));
            }
            ParkourDebugLog.INSTANCE.event(sb.toString());
        }

        // Budget this hop's travel along the route and find where it ends.
        double travel = Math.max(0.1D, BlinkPathingConfig.stepDistance);
        double remaining = travel;
        int prefixEnd = 0;
        Vec3d cursor = route.get(0);
        while (remaining > 1.0E-4D && prefixEnd + 1 < route.size()) {
            Vec3d next = route.get(prefixEnd + 1);
            double leg = Math.abs(next.x - cursor.x) + Math.abs(next.y - cursor.y)
                    + Math.abs(next.z - cursor.z);
            if (leg <= remaining) {
                remaining -= leg;
                cursor = next;
                prefixEnd++;
            } else {
                double f = remaining / leg;
                cursor = new Vec3d(cursor.x + (next.x - cursor.x) * f,
                        cursor.y + (next.y - cursor.y) * f,
                        cursor.z + (next.z - cursor.z) * f);
                remaining = 0;
            }
        }
        List<Vec3d> hopRoute = new ArrayList<>(route.subList(0, prefixEnd + 1));
        hopRoute.add(cursor);

        // Collision margin is clearance above the GROUND under the route, not
        // an extra lift stacked on the route's own height. Standing on a 2-block
        // path already uses most of that gap, so the hover collapses toward 0.
        double margin = BlinkPathingConfig.collisionMargin;
        if (margin > 0.0D) {
            double groundY = groundUnder(player, cursor.x, cursor.z, cursor.y);
            double wanted = groundY < -1000 ? margin : Math.max(0.0D, groundY + margin - cursor.y);
            double fitted = safeRouteLift(player, hopRoute, wanted);
            blinkLift = fitted < blinkLift ? fitted : Math.min(fitted, blinkLift + 0.5D);
        } else {
            blinkLift = 0.0D;
        }
        if (blinkLift != 0.0D) {
            for (int i = 1; i < route.size(); i++) {
                route.set(i, route.get(i).addVector(0.0D, blinkLift, 0.0D));
            }
        }

        // Walk the route leg by leg. Every leg is single-axis, and each leg
        // only takes min(travel, leg length, sweep room): a short leg turns
        // the corner and keeps going, a blocked leg stops at the last
        // collision-free point — so the hop auto-adapts from 0.1 up to
        // stepDistance without ever overrunning into a wall.
        double vCap = Math.min(BlinkPathingConfig.maxVerticalStep, 9.0D);
        double hCap = Math.min(BlinkPathingConfig.maxHorizontalStep, 9.0D);
        boolean moved = false;
        int ri = 0;
        while (travel > 1.0E-4D && ri + 1 < route.size()) {
            Vec3d from = route.get(ri);
            Vec3d target = route.get(ri + 1);
            // Lock each leg to its own axis. The target point assumes earlier
            // legs completed; when a horizontal leg stalls short of the
            // corner, letting a later vertical leg use target-pos deltas adds
            // a horizontal component and turns the drop diagonal — the sweep
            // can pass where the landing box cannot, producing a teleport
            // into a wall face and the ±1 escape-lift oscillation.
            double lx = target.x != from.x ? target.x - player.posX : 0.0D;
            double ly = target.y != from.y ? target.y - player.posY : 0.0D;
            double lz = target.z != from.z ? target.z - player.posZ : 0.0D;
            // Already past this leg's axis coordinate (overshot or skipped
            // corner): moving would go backwards — skip the leg instead.
            if ((lx != 0.0D && lx * (target.x - from.x) <= 0.0D)
                    || (ly != 0.0D && ly * (target.y - from.y) <= 0.0D)
                    || (lz != 0.0D && lz * (target.z - from.z) <= 0.0D)) {
                ri++;
                continue;
            }
            double leg = Math.abs(lx) + Math.abs(ly) + Math.abs(lz);
            if (leg < 1.0E-4D) {
                ri++;
                continue;
            }
            double cap = Math.abs(ly) > 1.0E-4D ? vCap : hCap;
            double hop = Math.min(Math.min(travel, leg), cap);
            Vec3d legFrom = new Vec3d(player.posX, player.posY, player.posZ);
            double movedDist = blinkTeleportAxis(player, lx * (hop / leg), ly * (hop / leg),
                    lz * (hop / leg));
            Vec3d legTo = new Vec3d(player.posX, player.posY, player.posZ);
            // Credit nodes swept through mid-hop. A dipping route (down under
            // an overhang and back up the far side) can pass several node
            // cells inside one hop and still end near where it started; the
            // end-of-hop credit alone then leaves pathPosition stuck and the
            // player loops the same detour every tick.
            while (pathPosition + 1 < positions.size()
                    && distSqNodeToSeg(player, positions.get(pathPosition + 1),
                            legFrom, legTo) < 1.3D) {
                pathPosition++;
            }
            if (movedDist > 1.0E-4D) {
                moved = true;
                travel -= movedDist;
            }
            ri++; // blocked leg is skipped — later legs (e.g. sliding off a
                    // fence top when the drop leg stalls) still get tried
        }
        if (!moved) {
            moved = blinkEscapeNudge(player, route);
        }
        if (ParkourDebugLog.enabled()) {
            ParkourDebugLog.INSTANCE.event(String.format(java.util.Locale.ROOT,
                    "blink move=%b lift=%.2f holdY=%.2f -> %.2f,%.2f,%.2f",
                    moved, blinkLift, blinkHoldY, player.posX, player.posY, player.posZ));
        }

        // Stall escape: when every leg collided (wedged against terrain the
        // route couldn't clear), advance along the route anyway so the path
        // keeps moving instead of freezing against the block.
        if (!moved) {
            blinkStuckTicks++;
            if (blinkStuckTicks >= 5 && pathPosition + 1 < positions.size()) {
                pathPosition++;
                blinkStuckTicks = 0;
            }
        } else {
            blinkStuckTicks = 0;
        }
        blinkHoldY = player.posY;

        // Credit path nodes the teleport actually reached.
        while (pathPosition + 1 < positions.size()
                && distSqToNode(player, positions.get(pathPosition + 1)) < 0.75D) {
            pathPosition++;
        }
        if (pathPosition + 1 >= positions.size()) {
            pathPosition = path.length();
        }
        onChangeInPathPosition();
        return true;
    }

    /**
     * Called after the entity tick, which applies gravity after pathing and
     * would otherwise drop the player every frame until the next hop lifts
     * them back. Re-pin the last teleported height.
     */
    public void afterPhysics() {
        if (!Baritone.settings().allowBlinkPathing.value || Double.isNaN(blinkHoldY)) {
            return;
        }
        EntityPlayerSP player = ctx.player();
        if (player == null) {
            return;
        }
        player.motionY = 0.0D;
        player.fallDistance = 0.0F;
        // Only undo gravity. A real descent updates blinkHoldY downward, so
        // pinning upward here would yank the player back to the ledge.
        if (player.posY < blinkHoldY - 1.0E-3D && player.posY > blinkHoldY - 1.0D
                && player.world != null) {
            AxisAlignedBB box = player.getEntityBoundingBox()
                    .offset(0, blinkHoldY - player.posY, 0);
            if (!player.world.collidesWithAnyBlock(box)) {
                player.setPosition(player.posX, blinkHoldY, player.posZ);
                if (player.connection != null) {
                    player.connection.sendPacket(new CPacketPlayer.Position(
                            player.posX, blinkHoldY, player.posZ, false));
                }
            }
        }
    }

    /** Top of the solid block under (x,z), or a sentinel when none is nearby. */
    private static double groundUnder(EntityPlayerSP player, double x, double z, double fromY) {
        if (player.world == null) {
            return Double.NEGATIVE_INFINITY;
        }
        int ix = net.minecraft.util.math.MathHelper.floor(x);
        int iz = net.minecraft.util.math.MathHelper.floor(z);
        int iy = net.minecraft.util.math.MathHelper.floor(fromY);
        for (int dy = 0; dy <= 4; dy++) {
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(ix, iy - dy, iz);
            net.minecraft.block.state.IBlockState state = player.world.getBlockState(pos);
            if (state.getMaterial().blocksMovement() && !state.getBlock().isPassable(player.world, pos)) {
                return pos.getY() + state.getBoundingBox(player.world, pos).maxY;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * Largest lift in [0, margin] under which the player's box stays
     * collision-free at every sampled point of the hop route. Binary-searches
     * down from the full margin so a low ceiling anywhere along the hop lowers
     * the whole hop in advance instead of at the last moment.
     */
    private static double safeRouteLift(EntityPlayerSP player, List<Vec3d> route, double margin) {
        if (player.world == null || route.isEmpty()) {
            return margin;
        }
        AxisAlignedBB base = player.getEntityBoundingBox();
        if (routeLiftFits(player, base, route, margin)) {
            return margin;
        }
        double lo = 0.0D;
        double hi = margin;
        for (int i = 0; i < 8; i++) {
            double mid = (lo + hi) * 0.5D;
            if (routeLiftFits(player, base, route, mid)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static boolean routeLiftFits(EntityPlayerSP player, AxisAlignedBB base, List<Vec3d> route,
            double lift) {
        Vec3d previous = null;
        for (Vec3d point : route) {
            if (previous != null && routePointCollides(player, base,
                    (previous.x + point.x) * 0.5D, (previous.y + point.y) * 0.5D,
                    (previous.z + point.z) * 0.5D, lift)) {
                return false;
            }
            if (routePointCollides(player, base, point.x, point.y, point.z, lift)) {
                return false;
            }
            previous = point;
        }
        return true;
    }

    private static boolean routePointCollides(EntityPlayerSP player, AxisAlignedBB base, double x,
            double y, double z, double lift) {
        return player.world.collidesWithAnyBlock(
                base.offset(x - player.posX, y + lift - player.posY, z - player.posZ));
    }

    /**
     * Flatten the upcoming path into an axis-aligned polyline starting at the
     * player. Each node pair contributes a vertical leg plus one horizontal
     * leg per changed horizontal axis — climbing first when the node rises,
     * dropping last when it falls, and ordering the X/Z corner by which side
     * sweeps farther from the corner point.
     */
    public static List<Vec3d> blinkRoute(List<BetterBlockPos> positions, int fromIndex, Vec3d start,
            EntityPlayerSP player) {
        List<Vec3d> route = new ArrayList<>();
        route.add(start);
        double px = start.x;
        double py = start.y;
        double pz = start.z;
        // Include the current target node: the fast-forward in blinkAlongPath
        // already advanced pathPosition past nodes the player passed, so
        // positions[fromIndex] is the node the player is heading toward.
        for (int i = Math.min(fromIndex, positions.size() - 1); i < positions.size(); i++) {
            BetterBlockPos node = positions.get(i);
            double nx = node.x + 0.5D;
            // Feet lands on the node's real surface (carpet 5.0625, slab 5.5,
            // fence top 6.5) — the same rule the planner used in blinkBoxFits.
            // Landing at the integer cell bottom buries the box in the thin
            // floor and collision physics kicks the player back = the
            // alternating ±3.5 teleport oscillation seen on slabs/fences.
            double ny = MovementFly.blinkSurfaceY(player.world, node.x, node.y, node.z);
            double nz = node.z + 0.5D;
            double dy = ny - py;
            if (dy > 1.0E-4D) {
                route.add(new Vec3d(px, ny, pz));
                py = ny;
            }
            double dx = nx - px;
            double dz = nz - pz;
            if (Math.abs(dx) > 1.0E-4D || Math.abs(dz) > 1.0E-4D) {
                // The L corner: pick the order whose FIRST leg sweeps farther
                // from the corner point, so the second leg doesn't start
                // inside a wall.
                boolean xFirst = Math.abs(dx) >= Math.abs(dz);
                if (Math.abs(dx) > 1.0E-4D && Math.abs(dz) > 1.0E-4D) {
                    double xRoom = freeSweepFrom(player, px, py, pz, dx, 0, 0);
                    double zRoom = freeSweepFrom(player, px, py, pz, 0, 0, dz);
                    xFirst = xRoom >= zRoom;
                }
                double vCap = Math.min(BlinkPathingConfig.maxVerticalStep, 9.0D);
                if (xFirst) {
                    py = appendHorizontalLeg(route, player, px, py, pz, dx, 0, vCap);
                    py = appendHorizontalLeg(route, player, nx, py, pz, 0, dz, vCap);
                } else {
                    py = appendHorizontalLeg(route, player, px, py, pz, 0, dz, vCap);
                    py = appendHorizontalLeg(route, player, px, py, nz, dx, 0, vCap);
                }
                px = nx;
                pz = nz;
            }
            if (Math.abs(ny - py) > 1.0E-4D) {
                // Hover case: the player can stand on a fence/wall top at a
                // fractional Y (feet 6.5, node cell 6). Dropping into that
                // cell wedges the box against the post — keep the route at
                // hover height; the next horizontal leg slides off and the
                // post leg drops there instead.
                double drop = ny - py;
                if (drop > 0.0D || freeSweepFrom(player, px, py, pz, 0, drop, 0) >= -drop - 1.0E-4D) {
                    route.add(new Vec3d(px, ny, pz));
                    py = ny;
                }
            }
        }
        return route;
    }

    /**
     * Every route leg stalled — take any small step that is collision-free so
     * the player never sits frozen on a fence top or wedged edge. Prefers
     * horizontal steps toward the next route point, then sideways, then
     * down, then up.
     */
    private static boolean blinkEscapeNudge(EntityPlayerSP player, List<Vec3d> route) {
        if (player.world == null) {
            return false;
        }
        Vec3d target = route.size() > 1 ? route.get(1) : null;
        double tx = target == null ? 0.0D : target.x - player.posX;
        double tz = target == null ? 0.0D : target.z - player.posZ;
        double[][] tries = {
                { tx, 0, tz }, { tz, 0, -tx }, { -tz, 0, tx }, { -tx, 0, -tz },
                { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
                { 0, -1, 0 }, { 0, 1, 0 } };
        for (double[] d : tries) {
            double len = Math.sqrt(d[0] * d[0] + d[2] * d[2]) + Math.abs(d[1]);
            if (len < 1.0E-4D) {
                continue;
            }
            double hop = Math.abs(d[1]) > 0 ? 0.5D : 0.4D;
            double mx = d[0] / len * hop;
            double my = d[1] < 0 ? -hop : (d[1] > 0 ? hop : 0.0D);
            double mz = d[2] / len * hop;
            if (blinkTeleportAxis(player, mx, my, mz) > 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    /**
     * Append one horizontal axis leg to the route. If the straight leg can't
     * sweep its full length (fence, wall edge), insert the smallest rise that
     * lets it pass: up → across at that height → back down to the route's Y,
     * all axis-aligned. Returns the Y the route continues at.
     */
    private static double appendHorizontalLeg(List<Vec3d> route, EntityPlayerSP player,
            double px, double py, double pz, double dx, double dz, double vCap) {
        double len = Math.abs(dx) + Math.abs(dz);
        if (len < 1.0E-4D) {
            return py;
        }
        if (freeSweepFrom(player, px, py, pz, dx, 0, dz) >= len - 1.0E-4D) {
            route.add(new Vec3d(px + dx, py, pz + dz));
            return py;
        }
        // Find the smallest lift that clears the whole horizontal leg. The
        // rise itself must also be clear (a ceiling above means no lift
        // helps — stop probing then).
        for (double lift = 0.25D; lift <= vCap + 1.0E-4D; lift += 0.25D) {
            if (freeSweepFrom(player, px, py, pz, 0, lift, 0) < lift - 1.0E-4D) {
                break;
            }
            if (freeSweepFrom(player, px, py + lift, pz, dx, 0, dz) >= len - 1.0E-4D) {
                route.add(new Vec3d(px, py + lift, pz));
                route.add(new Vec3d(px + dx, py + lift, pz + dz));
                route.add(new Vec3d(px + dx, py, pz + dz));
                return py;
            }
        }
        // No lift up to vCap clears it — a real wall. Still queue the leg;
        // the sweep stops at the obstruction and the stall escape advances.
        route.add(new Vec3d(px + dx, py, pz + dz));
        return py;
    }

    /** Like {@link #freeSweepDistance} but from an arbitrary feet position. */
    private static double freeSweepFrom(EntityPlayerSP player, double x, double y, double z,
            double dx, double dy, double dz) {
        double length = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (length < 1.0E-4D || player.world == null) {
            return length;
        }
        AxisAlignedBB base = player.getEntityBoundingBox()
                .offset(x - player.posX, y - player.posY, z - player.posZ);
        double step = Math.min(0.25D, length);
        double reached = 0.0D;
        while (reached < length) {
            double next = Math.min(length, reached + step);
            double scale = next / length;
            if (player.world.collidesWithAnyBlock(base.offset(dx * scale, dy * scale, dz * scale))) {
                break;
            }
            reached = next;
        }
        return reached;
    }

    /**
     * Farthest distance along this single axis the player's box can sweep
     * without intersecting terrain, in [0, |component|]. Marches in small
     * steps so thin walls and corner edges the destination check would miss
     * still stop the leg.
     */
    private static double freeSweepDistance(EntityPlayerSP player, double dx, double dy, double dz) {
        double length = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (length < 1.0E-4D || player.world == null) {
            return length;
        }
        AxisAlignedBB base = player.getEntityBoundingBox();
        double step = Math.min(0.25D, length);
        double reached = 0.0D;
        while (reached < length) {
            double next = Math.min(length, reached + step);
            double scale = next / length;
            AxisAlignedBB box = base.offset(dx * scale, dy * scale, dz * scale);
            if (player.world.collidesWithAnyBlock(box)) {
                break;
            }
            reached = next;
        }
        return reached;
    }

    /**
     * Teleport along one axis by up to {@code length}; sweep first and stop at
     * the last collision-free point so a leg never tunnels through a wall or
     * corner. Returns the distance actually moved.
     */
    private static double blinkTeleportAxis(EntityPlayerSP player, double dx, double dy, double dz) {
        double length = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (length < 1.0E-4D) {
            return 0.0D;
        }
        if (player.world == null) {
            blinkTeleport(player, player.posX + dx, player.posY + dy, player.posZ + dz);
            return length;
        }
        double reached = freeSweepDistance(player, dx, dy, dz);
        if (reached < 1.0E-4D) {
            return 0.0D;
        }
        double scale = reached / length;
        blinkTeleport(player, player.posX + dx * scale, player.posY + dy * scale,
                player.posZ + dz * scale);
        return reached;
    }

    private static void blinkTeleport(EntityPlayerSP player, double x, double y, double z) {
        player.setPosition(x, y, z);
        if (player.connection != null) {
            player.connection.sendPacket(new CPacketPlayer.Position(x, y, z, player.onGround));
        }
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        player.fallDistance = 0.0F;
        player.velocityChanged = true;
    }

    private static double distSqToNode(EntityPlayerSP player, BetterBlockPos node) {
        double dx = node.x + 0.5D - player.posX;
        double dy = MovementFly.blinkSurfaceY(player.world, node.x, node.y, node.z) - player.posY;
        double dz = node.z + 0.5D - player.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Squared distance from a node's surface point to a swept leg segment —
     * used to credit nodes the teleport passed through, not just the point it
     * ended at. Compares against the same threshold as distSqToNode.
     */
    private static double distSqNodeToSeg(EntityPlayerSP player, BetterBlockPos node,
            Vec3d a, Vec3d b) {
        // Compare against the cell CENTER, not the standable surface: route
        // legs ride a collision-margin lift above the surface, so a node the
        // hop passes directly over/under sits ~0.5-1.0 off the leg axis even
        // though it was genuinely traversed.
        double nx = node.x + 0.5D;
        double ny = node.y + 0.5D;
        double nz = node.z + 0.5D;
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 < 1.0E-8D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D,
                        ((nx - a.x) * dx + (ny - a.y) * dy + (nz - a.z) * dz) / len2));
        double px = a.x + t * dx - nx;
        double py = a.y + t * dy - ny;
        double pz = a.z + t * dz - nz;
        return px * px + py * py + pz * pz;
    }

    private void onChangeInPathPosition() {
        clearKeys();
        ticksOnCurrent = 0;
    }

    private void clearKeys() {
        // i'm just sick and tired of this snippet being everywhere lol
        behavior.baritone.getInputOverrideHandler().clearAllKeys();
    }

    private void cancel() {
        clearKeys();
        behavior.baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
        logOrbitDebug("cancel pathPos=%d finished=%s failed=%s feet=%s", pathPosition, finished(), failed,
                formatPos(ctx.playerFeet()));
        pathPosition = path.length() + 3;
        failed = true;
    }

    private boolean isOrbitDebugEnabled() {
        return path instanceof OrbitRoutePath && ModConfig.isDebugFlagEnabled(DebugModule.KILL_AURA_ORBIT);
    }

    private void logOrbitDebug(String format, Object... args) {
        if (!isOrbitDebugEnabled()) {
            return;
        }
        ModConfig.debugLog(DebugModule.KILL_AURA_ORBIT, String.format(Locale.ROOT, "pathExecutor " + format, args));
    }

    private String formatPos(BetterBlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%d,%d,%d)", pos.x, pos.y, pos.z);
    }

    @Override
    public int getPosition() {
        return pathPosition;
    }

    public PathExecutor trySplice(PathExecutor next) {
        if (replanAtSafeBoundary) return this;
        if (next == null) {
            return cutIfTooLong();
        }
        return SplicedPath.trySplice(path, next.path, false).map(path -> {
            if (!path.getDest().equals(next.getPath().getDest())) {
                throw new IllegalStateException();
            }
            PathExecutor ret = new PathExecutor(behavior, path);
            ret.pathPosition = pathPosition;
            ret.currentMovementOriginalCostEstimate = currentMovementOriginalCostEstimate;
            ret.costEstimateIndex = costEstimateIndex;
            ret.ticksOnCurrent = ticksOnCurrent;
            return ret;
        }).orElseGet(this::cutIfTooLong); // dont actually call cutIfTooLong every tick if we won't actually use it, use
                                          // a method reference
    }

    private PathExecutor cutIfTooLong() {
        if (pathPosition > Baritone.settings().maxPathHistoryLength.value) {
            int cutoffAmt = Baritone.settings().pathHistoryCutoffAmount.value;
            CutoffPath newPath = new CutoffPath(path, cutoffAmt, path.length() - 1);
            if (!newPath.getDest().equals(path.getDest())) {
                throw new IllegalStateException();
            }
            logDebug("Discarding earliest segment movements, length cut from " + path.length() + " to "
                    + newPath.length());
            PathExecutor ret = new PathExecutor(behavior, newPath);
            ret.pathPosition = pathPosition - cutoffAmt;
            ret.currentMovementOriginalCostEstimate = currentMovementOriginalCostEstimate;
            if (costEstimateIndex != null) {
                ret.costEstimateIndex = costEstimateIndex - cutoffAmt;
            }
            ret.ticksOnCurrent = ticksOnCurrent;
            return ret;
        }
        return this;
    }

    public void requestReplanAtSafeBoundary() {
        replanAtSafeBoundary = true;
    }

    @Override
    public IPath getPath() {
        return path;
    }

    public boolean failed() {
        return failed;
    }

    public boolean finished() {
        return pathPosition >= path.length();
    }

    public Set<BlockPos> toBreak() {
        return Collections.unmodifiableSet(toBreak);
    }

    public Set<BlockPos> toPlace() {
        return Collections.unmodifiableSet(toPlace);
    }

    public Set<BlockPos> toWalkInto() {
        return Collections.unmodifiableSet(toWalkInto);
    }

    public boolean isSprinting() {
        return sprintNextTick;
    }
}
