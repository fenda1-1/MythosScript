package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightLanding;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalComposite;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementFlightCorridor;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.PathBase;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** A fixed rise, straight cruise, and descent route for direct flight pathing. */
public final class FlightDirectPath extends PathBase {

    private static final int OPEN_COLUMN_SEARCH_RADIUS = 32;
    private static final int DETOUR_MARGIN = 20;
    private static final int MAX_DETOUR_NODES = 20000;
    private static final int MAX_RELAY_SCAN_DISTANCE = 512;
    private static final int MIN_CRUISE_GROUND_CLEARANCE = 5;
    private static final int MAX_CONFIGURED_FLIGHT_ALTITUDE = 356;
    private static final double DETOUR_TURN_PENALTY = 6.0D;
    private static final int[][] HORIZONTAL_DIRECTIONS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };

    private final Goal goal;
    private final List<BetterBlockPos> positions;
    private final List<IMovement> movements;
    private final boolean finalSegment;
    private final boolean terminalSegment;
    private final boolean safeLandingTerminal;

    private FlightDirectPath(IBaritone baritone, CalculationContext context, Goal goal, List<BetterBlockPos> positions,
            boolean finalSegment, boolean terminalSegment, boolean safeLandingTerminal) {
        this.goal = goal;
        this.positions = Collections.unmodifiableList(positions);
        this.finalSegment = finalSegment;
        this.terminalSegment = terminalSegment;
        this.safeLandingTerminal = safeLandingTerminal;
        List<IMovement> built = new ArrayList<>();
        for (int index = 0; index < positions.size() - 1; index++) {
            MovementFlightCorridor movement = new MovementFlightCorridor(baritone, positions.get(index),
                    positions.get(index + 1));
            // Direct paths bypass Path.postProcess(), so preserve its loaded-chunk
            // verification and cost initialization before PathExecutor's first tick.
            movement.checkLoadedChunk(context);
            movement.override(movement.calculateCost(context));
            built.add(movement);
        }
        this.movements = Collections.unmodifiableList(built);
    }

    public static IPath create(IBaritone baritone, Goal goal, BetterBlockPos start) {
        return plan(baritone, goal, start).getPath();
    }

    public static PlanResult plan(IBaritone baritone, Goal goal, BetterBlockPos start) {
        if (baritone == null || start == null || goal == null) {
            return PlanResult.failure(PlanStatus.UNSUPPORTED_GOAL);
        }
        if (!baritone.getPlayerContext().player().capabilities.allowFlying && !FlyHandler.enabled) {
            return PlanResult.failure(PlanStatus.NO_FLIGHT_CAPABILITY);
        }
        BlockPos target = resolveTarget(goal, start);
        if (target == null) {
            return PlanResult.failure(PlanStatus.UNSUPPORTED_GOAL);
        }
        IPath path = create(baritone, goal, start, true, null, false, false);
        if (path instanceof FlightDirectPath && isExecutable(baritone, (FlightDirectPath) path)) {
            return PlanResult.success((FlightDirectPath) path);
        }
        CalculationContext context = new CalculationContext(baritone, false);
        boolean targetLoaded = context.bsi.worldContainsLoadedChunk(target.getX(), target.getZ());
        boolean hasUsableRelay = targetLoaded || findRelayTarget(context, start, target) != null;
        return PlanResult.failure(!hasUsableRelay && hasUnloadedChunkAlongRoute(context, start, target)
                ? PlanStatus.WAITING_FOR_CHUNKS : PlanStatus.NO_ROUTE);
    }

    public static PlanResult planNearestSafeLanding(IBaritone baritone, Goal goal, BetterBlockPos start) {
        if (baritone == null || goal == null || start == null) {
            return PlanResult.failure(PlanStatus.UNSUPPORTED_GOAL);
        }
        if (!baritone.getPlayerContext().player().capabilities.allowFlying && !FlyHandler.enabled) {
            return PlanResult.failure(PlanStatus.NO_FLIGHT_CAPABILITY);
        }
        CalculationContext context = new CalculationContext(baritone, false);
        for (int radius = 0; radius <= 64; radius++) {
            List<BetterBlockPos> candidates = new ArrayList<>();
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                        continue;
                    }
                    int x = start.x + xOffset;
                    int z = start.z + zOffset;
                    if (!context.bsi.worldContainsLoadedChunk(x, z)) {
                        continue;
                    }
                    for (int y = Math.min(254, start.y); y >= 1; y--) {
                        if (isSafeLandingCell(context, x, y, z)
                                && isColumnClear(context, x, y, z, start.y, false)) {
                            candidates.add(new BetterBlockPos(x, y, z));
                            break;
                        }
                    }
                }
            }
            candidates.sort(Comparator
                    .comparingDouble((BetterBlockPos pos) -> goal.heuristic(pos))
                    .thenComparingDouble(pos -> pos.distanceSq(start)));
            int attempts = 0;
            for (BetterBlockPos candidate : candidates) {
                if (attempts++ >= 16) {
                    break;
                }
                IPath path = create(baritone, goal, start, true, candidate, true, true);
                if (path instanceof FlightDirectPath && isExecutable(baritone, (FlightDirectPath) path)) {
                    return PlanResult.success((FlightDirectPath) path);
                }
            }
        }
        return PlanResult.failure(PlanStatus.NO_ROUTE);
    }

    public static FlightDirectPath fromRecoveryPath(IBaritone baritone, CalculationContext context, Goal goal,
            IPath recoveryPath, boolean reachesGoal) {
        if (baritone == null || context == null || goal == null || recoveryPath == null || recoveryPath.length() < 2) {
            return null;
        }
        List<BetterBlockPos> raw = recoveryPath.positions();
        List<BetterBlockPos> points = new ArrayList<>();
        addPoint(points, raw.get(0));
        int lastDx = 0;
        int lastDy = 0;
        int lastDz = 0;
        for (int index = 1; index < raw.size(); index++) {
            BetterBlockPos previous = raw.get(index - 1);
            BetterBlockPos current = raw.get(index);
            int dx = Integer.signum(current.x - previous.x);
            int dy = Integer.signum(current.y - previous.y);
            int dz = Integer.signum(current.z - previous.z);
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                return null;
            }
            if (index > 1 && (dx != lastDx || dy != lastDy || dz != lastDz)) {
                addPoint(points, previous);
            }
            lastDx = dx;
            lastDy = dy;
            lastDz = dz;
        }
        addPoint(points, raw.get(raw.size() - 1));
        if (reachesGoal && goal instanceof GoalFlightExact) {
            BlockPos target = resolveTarget(goal, points.get(points.size() - 1));
            BetterBlockPos recoveryEnd = points.get(points.size() - 1);
            if (target == null || recoveryEnd.y != target.getY()) {
                return null;
            }
            if (recoveryEnd.x != target.getX() || recoveryEnd.z != target.getZ()) {
                List<BetterBlockPos> targetConnection = findOrthogonalRoute(context,
                        recoveryEnd.x, recoveryEnd.z, target.getX(), target.getZ(), recoveryEnd.y, false);
                if (targetConnection == null) {
                    return null;
                }
                appendCompressedHorizontalRoute(points, targetConnection, context, false);
            }
        }
        boolean safeLanding = reachesGoal && isSafeLanding(context, points.get(points.size() - 1));
        if (reachesGoal && Baritone.settings().flightAutoDescend.value && !safeLanding
                && !(goal instanceof GoalFlightExact) && !(goal instanceof GoalFlightXZ)) {
            return null;
        }
        FlightDirectPath path = new FlightDirectPath(baritone, context, goal, points, reachesGoal, reachesGoal,
                safeLanding);
        return isExecutable(path, context) ? path : null;
    }

    private static IPath create(IBaritone baritone, Goal goal, BetterBlockPos start, boolean allowFinalSegment,
            BlockPos forcedTarget, boolean forcedTerminalSegment, boolean safeLandingTerminal) {
        BlockPos target = forcedTarget == null ? resolveTarget(goal, start) : forcedTarget;
        if (target == null) {
            return null;
        }
        int clearance = Math.max(0, Baritone.settings().flightClearance.value);
        int configuredMinAltitude = Math.max(1, Math.min(MAX_CONFIGURED_FLIGHT_ALTITUDE,
                Baritone.settings().flightMinAltitude.value));
        int configuredMaxAltitude = Math.max(configuredMinAltitude,
                Math.max(1, Math.min(MAX_CONFIGURED_FLIGHT_ALTITUDE,
                        Baritone.settings().flightMaxAltitude.value)));
        boolean aerialTerminalTarget = goal instanceof GoalFlightExact || goal instanceof GoalFlightXZ;
        int requestedAltitude = goal instanceof GoalFlightExact ? target.getY() : start.y;
        int maxAltitude = Math.max(configuredMaxAltitude,
                Math.max(start.y, requestedAltitude));
        CalculationContext context = new CalculationContext(baritone, false);
        boolean finalSegment = forcedTarget != null
                || allowFinalSegment && context.bsi.worldContainsLoadedChunk(target.getX(), target.getZ());
        boolean terminalSegment = forcedTerminalSegment || finalSegment
                && (aerialTerminalTarget || !Baritone.settings().flightAutoDescend.value);
        if (finalSegment && forcedTarget == null && Baritone.settings().flightAutoDescend.value
                && !aerialTerminalTarget) {
            BlockPos safeTarget = findGoalEndpoint(context, goal, target);
            if (safeTarget == null) {
                return create(baritone, goal, start, false, null, false, false);
            }
            target = safeTarget;
        } else if (finalSegment && !Baritone.settings().flightAutoDescend.value && !aerialTerminalTarget) {
            target = new BlockPos(target.getX(), start.y, target.getZ());
        }
        BlockPos planningTarget = finalSegment ? target : findRelayTarget(context, start, target);
        if (planningTarget == null) {
            return null;
        }
        int baseAltitude = Math.max(configuredMinAltitude, Math.max(start.y, planningTarget.getY()));
        if (baseAltitude > maxAltitude) {
            return finalSegment && forcedTarget == null
                    ? create(baritone, goal, start, false, null, false, false) : null;
        }

        FlightDirectPath verticalFirst = createVerticalFirstPath(baritone, context, goal, start, planningTarget,
                baseAltitude, maxAltitude, finalSegment, terminalSegment, safeLandingTerminal);
        if (verticalFirst != null) {
            return verticalFirst;
        }

        List<BetterBlockPos> launchRoute = findOpenColumnRoute(context, start, maxAltitude, true);
        if (launchRoute == null) {
            launchRoute = findOpenColumnRoute(context, start, maxAltitude, false);
        }
        List<BetterBlockPos> targetToLandingRoute;
        if (terminalSegment && !safeLandingTerminal && !(goal instanceof GoalFlightExact)) {
            targetToLandingRoute = Collections.singletonList(new BetterBlockPos(planningTarget));
        } else {
            targetToLandingRoute = findOpenColumnRoute(context, new BetterBlockPos(planningTarget), maxAltitude, true);
            if (targetToLandingRoute == null) {
                targetToLandingRoute = findOpenColumnRoute(context, new BetterBlockPos(planningTarget), maxAltitude,
                        false);
            }
        }
        if (launchRoute == null || targetToLandingRoute == null) {
            return finalSegment && forcedTarget == null
                    ? create(baritone, goal, start, false, null, false, false) : null;
        }
        BetterBlockPos launch = launchRoute.get(launchRoute.size() - 1);
        BetterBlockPos landing = targetToLandingRoute.get(targetToLandingRoute.size() - 1);

        // First require the entire configured corridor cross-section to be clear.
        // This keeps trees and roofs outside the visible channel whenever possible.
        for (int cruiseY = baseAltitude; cruiseY <= maxAltitude; cruiseY++) {
            List<BetterBlockPos> cruiseRoute = findOrthogonalRoute(context, launch.x, launch.z, landing.x, landing.z,
                    cruiseY, true, maxAltitude);
            if (cruiseRoute != null) {
                return buildPath(baritone, context, goal, planningTarget, launchRoute, targetToLandingRoute, cruiseRoute,
                        cruiseY, true, maxAltitude, finalSegment, terminalSegment, safeLandingTerminal);
            }
        }

        // Then route the full-width channel around obstacles at a suitable altitude.
        for (int cruiseY : detourAltitudes(baseAltitude, maxAltitude)) {
            List<BetterBlockPos> detour = findHorizontalDetour(context,
                    new BetterBlockPos(launch.x, cruiseY, launch.z),
                    new BetterBlockPos(landing.x, cruiseY, landing.z), true, maxAltitude);
            if (detour != null) {
                return buildPath(baritone, context, goal, planningTarget, launchRoute, targetToLandingRoute, detour,
                        cruiseY, true, maxAltitude, finalSegment, terminalSegment, safeLandingTerminal);
            }
        }
        // A narrow passage such as a door cannot fit the configured visualization
        // width. Only after all full-width options fail, guarantee player-sized
        // clearance and align the route with that opening.
        for (int cruiseY = baseAltitude; cruiseY <= maxAltitude; cruiseY++) {
            List<BetterBlockPos> cruiseRoute = findOrthogonalRoute(context, launch.x, launch.z, landing.x, landing.z,
                    cruiseY, false, maxAltitude);
            if (cruiseRoute != null) {
                return buildPath(baritone, context, goal, planningTarget, launchRoute, targetToLandingRoute, cruiseRoute,
                        cruiseY, false, maxAltitude, finalSegment, terminalSegment, safeLandingTerminal);
            }
        }
        for (int cruiseY : detourAltitudes(baseAltitude, maxAltitude)) {
            List<BetterBlockPos> detour = findHorizontalDetour(context,
                    new BetterBlockPos(launch.x, cruiseY, launch.z),
                    new BetterBlockPos(landing.x, cruiseY, landing.z), false, maxAltitude);
            if (detour != null) {
                return buildPath(baritone, context, goal, planningTarget, launchRoute, targetToLandingRoute, detour,
                        cruiseY, false, maxAltitude, finalSegment, terminalSegment, safeLandingTerminal);
            }
        }
        return finalSegment && forcedTarget == null
                ? create(baritone, goal, start, false, null, false, false) : null;
    }

    private static FlightDirectPath createVerticalFirstPath(IBaritone baritone, CalculationContext context, Goal goal,
            BetterBlockPos start, BlockPos planningTarget, int baseAltitude, int maxAltitude, boolean finalSegment,
            boolean terminalSegment, boolean safeLandingTerminal) {
        List<BetterBlockPos> launchRoute = Collections.singletonList(start);
        List<BetterBlockPos> targetRoute = Collections.singletonList(new BetterBlockPos(planningTarget));
        boolean requiresTargetVertical = finalSegment
                && !(terminalSegment && !safeLandingTerminal && !(goal instanceof GoalFlightExact));

        for (int cruiseY = baseAltitude; cruiseY <= maxAltitude; cruiseY++) {
            if (!isColumnClear(context, start.x, start.y, start.z, cruiseY, false)
                    || requiresTargetVertical && !isColumnClear(context, planningTarget.getX(), planningTarget.getY(),
                            planningTarget.getZ(), cruiseY, false)) {
                continue;
            }
            List<BetterBlockPos> route = findOrthogonalRoute(context, start.x, start.z,
                    planningTarget.getX(), planningTarget.getZ(), cruiseY, true, cruiseY);
            boolean fullCorridor = true;
            if (route == null) {
                route = findOrthogonalRoute(context, start.x, start.z,
                        planningTarget.getX(), planningTarget.getZ(), cruiseY, false, cruiseY);
                fullCorridor = false;
            }
            if (route == null) {
                continue;
            }
            return (FlightDirectPath) buildPath(baritone, context, goal, planningTarget, launchRoute, targetRoute,
                    route, cruiseY, fullCorridor, cruiseY, finalSegment, terminalSegment, safeLandingTerminal);
        }
        return null;
    }

    private static BlockPos resolveTarget(Goal goal, BetterBlockPos start) {
        if (goal instanceof IGoalRenderPos) {
            return ((IGoalRenderPos) goal).getGoalPos();
        }
        if (goal instanceof GoalXZ) {
            GoalXZ xz = (GoalXZ) goal;
            return new BlockPos(xz.getX(), start.y, xz.getZ());
        }
        if (goal instanceof GoalComposite) {
            BlockPos best = null;
            double bestHeuristic = Double.POSITIVE_INFINITY;
            for (Goal child : ((GoalComposite) goal).goals()) {
                BlockPos candidate = resolveTarget(child, start);
                double heuristic = child.heuristic(start.x, start.y, start.z);
                if (candidate != null && Double.isFinite(heuristic) && heuristic < bestHeuristic) {
                    best = candidate;
                    bestHeuristic = heuristic;
                }
            }
            return best;
        }
        return null;
    }

    private static IPath buildPath(IBaritone baritone, CalculationContext context, Goal goal, BlockPos target,
            List<BetterBlockPos> launchRoute, List<BetterBlockPos> targetToLandingRoute,
            List<BetterBlockPos> cruiseRoute, int cruiseY, boolean fullCorridorClearance, int cruiseClearanceTop,
            boolean finalSegment, boolean terminalSegment, boolean safeLandingTerminal) {
        List<BetterBlockPos> points = new ArrayList<>();
        appendCompressedHorizontalRoute(points, launchRoute, context, false);

        BetterBlockPos launch = launchRoute.get(launchRoute.size() - 1);
        BetterBlockPos landing = targetToLandingRoute.get(targetToLandingRoute.size() - 1);
        addPoint(points, new BetterBlockPos(launch.x, cruiseY, launch.z));
        appendCompressedHorizontalRoute(points, cruiseRoute, context, fullCorridorClearance, cruiseClearanceTop);
        if (!finalSegment) {
            addPoint(points, new BetterBlockPos(landing.x, cruiseY, landing.z));
            return points.size() >= 2
                    ? new FlightDirectPath(baritone, context, goal, points, false, false, false) : null;
        }
        if (terminalSegment && !safeLandingTerminal && !(goal instanceof GoalFlightExact)) {
            addPoint(points, new BetterBlockPos(target.getX(), cruiseY, target.getZ()));
            return points.size() >= 2
                    ? new FlightDirectPath(baritone, context, goal, points, true, true, false) : null;
        }
        addPoint(points, new BetterBlockPos(landing.x, target.getY(), landing.z));

        List<BetterBlockPos> landingToTarget = new ArrayList<>(targetToLandingRoute);
        Collections.reverse(landingToTarget);
        appendCompressedHorizontalRoute(points, landingToTarget, context, false);
        addPoint(points, new BetterBlockPos(target));
        return points.size() >= 2
                ? new FlightDirectPath(baritone, context, goal, points, true, terminalSegment, safeLandingTerminal)
                : null;
    }

    private static BlockPos findGoalEndpoint(CalculationContext context, Goal goal, BlockPos renderTarget) {
        if (goal instanceof GoalXZ) {
            for (int y = 254; y >= 1; y--) {
                if (goal.isInGoal(renderTarget.getX(), y, renderTarget.getZ())
                        && isSafeLandingCell(context, renderTarget.getX(), y, renderTarget.getZ())) {
                    return new BlockPos(renderTarget.getX(), y, renderTarget.getZ());
                }
            }
            return null;
        }
        int horizontalRadius = goal instanceof GoalFlightLanding
                ? Math.max(0, com.zszl.zszlScriptMod.config.FlightPathingConfig.arrivalRange) : 4;
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int xOffset = -horizontalRadius; xOffset <= horizontalRadius; xOffset++) {
            for (int zOffset = -horizontalRadius; zOffset <= horizontalRadius; zOffset++) {
                int x = renderTarget.getX() + xOffset;
                int z = renderTarget.getZ() + zOffset;
                if (!context.bsi.worldContainsLoadedChunk(x, z)) {
                    continue;
                }
                for (int yOffset = -4; yOffset <= 4; yOffset++) {
                    int y = renderTarget.getY() + yOffset;
                    if (y < 1 || y > 254 || !goal.isInGoal(x, y, z) || !isSafeLandingCell(context, x, y, z)) {
                        continue;
                    }
                    double distance = new BlockPos(x, y, z).distanceSq(renderTarget);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new BlockPos(x, y, z);
                    }
                }
            }
        }
        return best;
    }

    private static boolean isSafeLandingCell(CalculationContext context, int x, int y, int z) {
        return context.bsi.worldContainsLoadedChunk(x, z)
                && isPlayerCellClear(context, x, y, z, false)
                && MovementHelper.canWalkOn(context, x, y - 1, z);
    }

    private static boolean hasUnloadedChunkAlongRoute(CalculationContext context, BetterBlockPos start,
            BlockPos target) {
        double dx = target.getX() - start.x;
        double dz = target.getZ() - start.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, Math.min(MAX_RELAY_SCAN_DISTANCE, (int) Math.ceil(distance)));
        for (int step = 0; step <= steps; step++) {
            double progress = distance <= 1.0E-6D ? 0.0D : step / distance;
            int x = (int) Math.round(start.x + dx * Math.min(1.0D, progress));
            int z = (int) Math.round(start.z + dz * Math.min(1.0D, progress));
            if (!context.bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSafeLanding(IBaritone baritone, BetterBlockPos pos) {
        if (baritone == null || pos == null) {
            return false;
        }
        return isSafeLanding(new CalculationContext(baritone, false), pos);
    }

    public static boolean isSegmentLoadedAndClear(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        if (baritone == null || src == null || dest == null) {
            return false;
        }
        return isSegmentLoadedAndClear(new CalculationContext(baritone, false), src, dest);
    }

    public static boolean isSegmentLoadedAndClear(CalculationContext context, BetterBlockPos src, BetterBlockPos dest) {
        if (context == null || src == null || dest == null) {
            return false;
        }
        if (src.x == dest.x && src.z == dest.z) {
            return isColumnClear(context, src.x, src.y, src.z, dest.y, false);
        }
        if (src.y != dest.y || src.x != dest.x && src.z != dest.z) {
            return false;
        }
        return isHorizontalLineClear(context, src.x, src.z, dest.x, dest.z, src.y, false);
    }

    private static boolean isExecutable(IBaritone baritone, FlightDirectPath path) {
        return baritone != null && isExecutable(path, new CalculationContext(baritone, false));
    }

    private static boolean isExecutable(FlightDirectPath path, CalculationContext context) {
        if (path == null || path.length() < 2 || path.movements.size() != path.positions.size() - 1) {
            return false;
        }
        try {
            path.sanityCheck();
        } catch (RuntimeException ignored) {
            return false;
        }
        for (IMovement movement : path.movements) {
            double cost = movement.getCost();
            if (!Double.isFinite(cost) || cost <= 0.0D || cost >= ActionCosts.COST_INF
                    || !isSegmentLoadedAndClear(context, movement.getSrc(), movement.getDest())) {
                return false;
            }
        }
        return !path.safeLandingTerminal || isSafeLanding(context, path.getDest());
    }

    private static boolean isSafeLanding(CalculationContext context, BetterBlockPos pos) {
        return context != null && pos != null && isSafeLandingCell(context, pos.x, pos.y, pos.z);
    }

    private static BlockPos findRelayTarget(CalculationContext context, BetterBlockPos start, BlockPos target) {
        double dx = target.getX() - start.x;
        double dz = target.getZ() - start.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0D) {
            return null;
        }
        double unitX = dx / distance;
        double unitZ = dz / distance;
        int scanDistance = Math.min(MAX_RELAY_SCAN_DISTANCE, (int) Math.floor(distance));
        int lastLoadedStep = 0;
        for (int step = 1; step <= scanDistance; step++) {
            int x = (int) Math.round(start.x + unitX * step);
            int z = (int) Math.round(start.z + unitZ * step);
            if (!context.bsi.worldContainsLoadedChunk(x, z)) {
                break;
            }
            lastLoadedStep = step;
        }
        int speedMargin = (int) Math.ceil(Math.max(16.0D,
                FlyHandler.horizontalSpeed * 3.0D));
        int boundaryMargin = Math.min(speedMargin, Math.max(8, lastLoadedStep / 2));
        int relayStep = lastLoadedStep - boundaryMargin;
        if (relayStep < 2) {
            return null;
        }
        while (relayStep > 0) {
            int x = (int) Math.round(start.x + unitX * relayStep);
            int z = (int) Math.round(start.z + unitZ * relayStep);
            if (context.bsi.worldContainsLoadedChunk(x, z)) {
                return new BlockPos(x, start.y, z);
            }
            relayStep--;
        }
        return null;
    }

    private static List<BetterBlockPos> findOpenColumnRoute(CalculationContext context, BetterBlockPos origin,
            int maxAltitude, boolean fullCorridorClearance) {
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        Map<Long, SearchNode> visited = new HashMap<>();
        SearchNode start = new SearchNode(origin.x, origin.z, 0.0D, null);
        queue.add(start);
        visited.put(horizontalKey(start.x, start.z), start);

        while (!queue.isEmpty()) {
            SearchNode current = queue.removeFirst();
            if (isColumnClear(context, current.x, origin.y, current.z, maxAltitude, fullCorridorClearance)) {
                return reconstructRoute(current, origin.y);
            }
            for (int[] direction : HORIZONTAL_DIRECTIONS) {
                int nextX = current.x + direction[0];
                int nextZ = current.z + direction[1];
                if (Math.max(Math.abs(nextX - origin.x), Math.abs(nextZ - origin.z)) > OPEN_COLUMN_SEARCH_RADIUS
                        || visited.containsKey(horizontalKey(nextX, nextZ))
                        || !isHorizontalStepClear(context, current.x, current.z, nextX, nextZ, origin.y)) {
                    continue;
                }
                SearchNode next = new SearchNode(nextX, nextZ, current.cost + 1.0D, current);
                visited.put(horizontalKey(nextX, nextZ), next);
                queue.addLast(next);
            }
        }
        return null;
    }

    private static List<BetterBlockPos> findHorizontalDetour(CalculationContext context, BetterBlockPos start,
            BetterBlockPos end, boolean fullCorridorClearance, int cruiseClearanceTop) {
        if (!isCruiseCellClear(context, start.x, start.y, start.z, fullCorridorClearance, cruiseClearanceTop)
                || !isCruiseCellClear(context, end.x, end.y, end.z, fullCorridorClearance, cruiseClearanceTop)) {
            return null;
        }
        int minX = Math.min(start.x, end.x) - DETOUR_MARGIN;
        int maxX = Math.max(start.x, end.x) + DETOUR_MARGIN;
        int minZ = Math.min(start.z, end.z) - DETOUR_MARGIN;
        int maxZ = Math.max(start.z, end.z) + DETOUR_MARGIN;

        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.priority));
        Map<Long, Double> bestCost = new HashMap<>();
        SearchNode first = new SearchNode(start.x, start.z, 0.0D, null);
        first.priority = horizontalDistance(start.x, start.z, end.x, end.z);
        open.add(first);
        bestCost.put(horizontalKey(first.x, first.z), 0.0D);

        int considered = 0;
        while (!open.isEmpty() && considered++ < MAX_DETOUR_NODES) {
            SearchNode current = open.poll();
            Double knownCost = bestCost.get(horizontalKey(current.x, current.z));
            if (knownCost == null || current.cost > knownCost + 1.0E-6D) {
                continue;
            }
            if (current.x == end.x && current.z == end.z) {
                return reconstructRoute(current, start.y);
            }
            for (int[] direction : HORIZONTAL_DIRECTIONS) {
                int nextX = current.x + direction[0];
                int nextZ = current.z + direction[1];
                if (nextX < minX || nextX > maxX || nextZ < minZ || nextZ > maxZ
                        || !isHorizontalStepClear(context, current.x, current.z, nextX, nextZ, start.y,
                                fullCorridorClearance, cruiseClearanceTop)) {
                    continue;
                }
                double turnPenalty = current.parent != null
                        && (current.x - current.parent.x != direction[0]
                                || current.z - current.parent.z != direction[1])
                                        ? DETOUR_TURN_PENALTY : 0.0D;
                double candidateCost = current.cost + 1.0D + turnPenalty;
                long key = horizontalKey(nextX, nextZ);
                if (candidateCost >= bestCost.getOrDefault(key, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                SearchNode next = new SearchNode(nextX, nextZ, candidateCost, current);
                next.priority = candidateCost + horizontalDistance(nextX, nextZ, end.x, end.z);
                bestCost.put(key, candidateCost);
                open.add(next);
            }
        }
        return null;
    }

    private static List<BetterBlockPos> findOrthogonalRoute(CalculationContext context, int startX, int startZ,
            int endX, int endZ, int y, boolean fullCorridorClearance) {
        return findOrthogonalRoute(context, startX, startZ, endX, endZ, y, fullCorridorClearance, -1);
    }

    private static List<BetterBlockPos> findOrthogonalRoute(CalculationContext context, int startX, int startZ,
            int endX, int endZ, int y, boolean fullCorridorClearance, int cruiseClearanceTop) {
        BetterBlockPos start = new BetterBlockPos(startX, y, startZ);
        BetterBlockPos end = new BetterBlockPos(endX, y, endZ);
        if (startX == endX || startZ == endZ) {
            return isHorizontalLineClear(context, startX, startZ, endX, endZ, y, fullCorridorClearance)
                    && (cruiseClearanceTop < y || isHorizontalLineCruiseClear(context, startX, startZ, endX, endZ, y,
                            fullCorridorClearance, cruiseClearanceTop)) ? Arrays.asList(start, end) : null;
        }

        BetterBlockPos xFirstCorner = new BetterBlockPos(endX, y, startZ);
        boolean xFirstClear = isHorizontalLineCruiseClear(context, startX, startZ, endX, startZ, y,
                fullCorridorClearance, cruiseClearanceTop)
                && isHorizontalLineCruiseClear(context, endX, startZ, endX, endZ, y, fullCorridorClearance,
                        cruiseClearanceTop);
        BetterBlockPos zFirstCorner = new BetterBlockPos(startX, y, endZ);
        boolean zFirstClear = isHorizontalLineCruiseClear(context, startX, startZ, startX, endZ, y,
                fullCorridorClearance, cruiseClearanceTop)
                && isHorizontalLineCruiseClear(context, startX, endZ, endX, endZ, y, fullCorridorClearance,
                        cruiseClearanceTop);

        if (xFirstClear && zFirstClear) {
            // Prefer the elbow that starts along the larger remaining axis, reducing
            // early turns while keeping an exactly orthogonal pipe shape.
            return Math.abs(endX - startX) >= Math.abs(endZ - startZ)
                    ? Arrays.asList(start, xFirstCorner, end)
                    : Arrays.asList(start, zFirstCorner, end);
        }
        if (xFirstClear) {
            return Arrays.asList(start, xFirstCorner, end);
        }
        if (zFirstClear) {
            return Arrays.asList(start, zFirstCorner, end);
        }
        return null;
    }

    private static boolean isColumnClear(CalculationContext context, int x, int fromY, int z, int toY) {
        return isColumnClear(context, x, fromY, z, toY, false);
    }

    private static boolean isColumnClear(CalculationContext context, int x, int fromY, int z, int toY,
            boolean fullCorridorClearance) {
        int minY = Math.min(fromY, toY);
        int maxY = Math.max(fromY, toY);
        for (int y = minY; y <= maxY; y++) {
            if (!isFlightCellClear(context, x, y, z, fullCorridorClearance, false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHorizontalLineClear(CalculationContext context, int startX, int startZ, int endX, int endZ,
            int y) {
        return isHorizontalLineClear(context, startX, startZ, endX, endZ, y, false);
    }

    private static boolean isHorizontalLineClear(CalculationContext context, int startX, int startZ, int endX, int endZ,
            int y, boolean fullCorridorClearance) {
        int steps = Math.max(1, (int) Math.ceil(horizontalDistance(startX, startZ, endX, endZ) * 2.0D));
        for (int step = 0; step <= steps; step++) {
            int x = Math.round(startX + (endX - startX) * (step / (float) Math.max(1, steps)));
            int z = Math.round(startZ + (endZ - startZ) * (step / (float) Math.max(1, steps)));
            if (!isFlightCellClear(context, x, y, z, fullCorridorClearance, true)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHorizontalLineCruiseClear(CalculationContext context, int startX, int startZ, int endX,
            int endZ, int y, boolean fullCorridorClearance, int cruiseClearanceTop) {
        if (cruiseClearanceTop < y) {
            return isHorizontalLineClear(context, startX, startZ, endX, endZ, y, fullCorridorClearance);
        }
        int steps = Math.max(1, (int) Math.ceil(horizontalDistance(startX, startZ, endX, endZ) * 2.0D));
        for (int step = 0; step <= steps; step++) {
            int x = Math.round(startX + (endX - startX) * (step / (float) Math.max(1, steps)));
            int z = Math.round(startZ + (endZ - startZ) * (step / (float) Math.max(1, steps)));
            if (!isCruiseCellClear(context, x, y, z, fullCorridorClearance, cruiseClearanceTop)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCruiseCellClear(CalculationContext context, int x, int y, int z,
            boolean fullCorridorClearance, int cruiseClearanceTop) {
        if (!isFlightCellClear(context, x, y, z, fullCorridorClearance)
                || !isColumnClear(context, x, y, z, cruiseClearanceTop, false)) {
            return false;
        }
        for (int distance = 1; distance <= MIN_CRUISE_GROUND_CLEARANCE; distance++) {
            if (y - distance < 0 || !MovementHelper.canFlyThrough(context, x, y - distance, z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFlightCellClear(CalculationContext context, int x, int y, int z) {
        return isFlightCellClear(context, x, y, z, false);
    }

    private static boolean isFlightCellClear(CalculationContext context, int x, int y, int z,
            boolean fullCorridorClearance) {
        return isFlightCellClear(context, x, y, z, fullCorridorClearance, true);
    }

    private static boolean isFlightCellClear(CalculationContext context, int x, int y, int z,
            boolean fullCorridorClearance, boolean allowInteractions) {
        if (!fullCorridorClearance) {
            return isPlayerCellClear(context, x, y, z, allowInteractions);
        }
        int width = Math.max(1, Math.min(9, Baritone.settings().flightCorridorWidth.value));
        int minOffset = -((width - 1) / 2);
        int maxOffset = width / 2;
        for (int offsetX = minOffset; offsetX <= maxOffset; offsetX++) {
            for (int offsetZ = minOffset; offsetZ <= maxOffset; offsetZ++) {
                if (!isPlayerCellClear(context, x + offsetX, y, z + offsetZ, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isPlayerCellClear(CalculationContext context, int x, int y, int z,
            boolean allowInteractions) {
        if (!context.bsi.worldContainsLoadedChunk(x, z)) {
            return false;
        }
        int clearance = Math.max(0, Baritone.settings().flightClearance.value);
        for (int offset = 0; offset <= clearance; offset++) {
            IBlockState state = context.get(x, y + offset, z);
            if (!MovementHelper.canFlyThrough(context, x, y + offset, z, state)
                    && (!allowInteractions || !isOpenableInteraction(context, x, y + offset, z, state))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpenableInteraction(CalculationContext context, int x, int y, int z, IBlockState state) {
        if (state.getBlock() instanceof BlockDoor) {
            return state.getBlock() != Blocks.IRON_DOOR
                    || MovementHelper.canOpenIronDoorWithButton(context.bsi, x, y, z, state);
        }
        if (state.getBlock() instanceof BlockFenceGate) {
            return true;
        }
        return state.getBlock() instanceof BlockTrapDoor && state.getBlock() != Blocks.IRON_TRAPDOOR;
    }

    private static boolean isHorizontalStepClear(CalculationContext context, int fromX, int fromZ, int toX, int toZ,
            int y) {
        return isHorizontalStepClear(context, fromX, fromZ, toX, toZ, y, false);
    }

    private static boolean isHorizontalStepClear(CalculationContext context, int fromX, int fromZ, int toX, int toZ,
            int y, boolean fullCorridorClearance) {
        if (!isFlightCellClear(context, toX, y, toZ, fullCorridorClearance)) {
            return false;
        }
        if (fromX != toX && fromZ != toZ) {
            return isFlightCellClear(context, fromX, y, toZ, fullCorridorClearance)
                    && isFlightCellClear(context, toX, y, fromZ, fullCorridorClearance);
        }
        return true;
    }

    private static boolean isHorizontalStepClear(CalculationContext context, int fromX, int fromZ, int toX, int toZ,
            int y, boolean fullCorridorClearance, int cruiseClearanceTop) {
        if (!isCruiseCellClear(context, toX, y, toZ, fullCorridorClearance, cruiseClearanceTop)) {
            return false;
        }
        if (fromX != toX && fromZ != toZ) {
            return isCruiseCellClear(context, fromX, y, toZ, fullCorridorClearance, cruiseClearanceTop)
                    && isCruiseCellClear(context, toX, y, fromZ, fullCorridorClearance, cruiseClearanceTop);
        }
        return true;
    }

    private static void appendCompressedHorizontalRoute(List<BetterBlockPos> points, List<BetterBlockPos> route,
            CalculationContext context, boolean fullCorridorClearance) {
        appendCompressedHorizontalRoute(points, route, context, fullCorridorClearance, -1);
    }

    private static void appendCompressedHorizontalRoute(List<BetterBlockPos> points, List<BetterBlockPos> route,
            CalculationContext context, boolean fullCorridorClearance, int cruiseClearanceTop) {
        if (route == null || route.isEmpty()) {
            return;
        }
        addPoint(points, route.get(0));
        int current = 0;
        while (current < route.size() - 1) {
            int farthest = route.size() - 1;
            BetterBlockPos start = route.get(current);
            while (farthest > current + 1) {
                BetterBlockPos candidate = route.get(farthest);
                if (start.y == candidate.y && (start.x == candidate.x || start.z == candidate.z)
                        && isHorizontalLineCruiseClear(context, start.x, start.z, candidate.x, candidate.z, start.y,
                                fullCorridorClearance, cruiseClearanceTop)) {
                    break;
                }
                farthest--;
            }
            addPoint(points, route.get(farthest));
            current = farthest;
        }
    }

    private static List<BetterBlockPos> reconstructRoute(SearchNode end, int y) {
        List<BetterBlockPos> route = new ArrayList<>();
        for (SearchNode current = end; current != null; current = current.parent) {
            route.add(new BetterBlockPos(current.x, y, current.z));
        }
        Collections.reverse(route);
        return route;
    }

    private static long horizontalKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static double horizontalDistance(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<Integer> detourAltitudes(int baseAltitude, int maxAltitude) {
        LinkedHashSet<Integer> levels = new LinkedHashSet<>();
        for (int y = baseAltitude; y <= maxAltitude; y += 12) {
            levels.add(y);
        }
        levels.add(maxAltitude);
        return new ArrayList<>(levels);
    }

    private static void addPoint(List<BetterBlockPos> points, BetterBlockPos point) {
        if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
            points.add(point);
        }
    }

    private static final class SearchNode {
        private final int x;
        private final int z;
        private final double cost;
        private final SearchNode parent;
        private double priority;

        private SearchNode(int x, int z, double cost, SearchNode parent) {
            this.x = x;
            this.z = z;
            this.cost = cost;
            this.parent = parent;
        }
    }

    @Override
    public List<IMovement> movements() {
        return movements;
    }

    @Override
    public List<BetterBlockPos> positions() {
        return positions;
    }

    @Override
    public IPath postProcess() {
        sanityCheck();
        return this;
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    @Override
    public int getNumNodesConsidered() {
        return 0;
    }

    public boolean isFinalSegment() {
        return finalSegment;
    }

    public boolean isTerminalSegment() {
        return terminalSegment;
    }

    public boolean isSafeLandingTerminal() {
        return safeLandingTerminal;
    }

    public enum PlanStatus {
        SUCCESS,
        WAITING_FOR_CHUNKS,
        NO_ROUTE,
        NO_FLIGHT_CAPABILITY,
        UNSUPPORTED_GOAL
    }

    public static final class PlanResult {
        private final PlanStatus status;
        private final FlightDirectPath path;

        private PlanResult(PlanStatus status, FlightDirectPath path) {
            this.status = status;
            this.path = path;
        }

        public static PlanResult success(FlightDirectPath path) {
            return new PlanResult(PlanStatus.SUCCESS, path);
        }

        public static PlanResult failure(PlanStatus status) {
            return new PlanResult(status, null);
        }

        public PlanStatus getStatus() {
            return status;
        }

        public FlightDirectPath getPath() {
            return path;
        }

        public boolean isSuccess() {
            return status == PlanStatus.SUCCESS && path != null;
        }
    }
}
