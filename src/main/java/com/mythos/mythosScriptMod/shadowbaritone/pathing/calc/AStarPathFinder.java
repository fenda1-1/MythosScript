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

package com.mythos.mythosScriptMod.shadowbaritone.pathing.calc;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.openset.BinaryHeapOpenSet;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.Moves;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementFly;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpCandidate;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementNarrowGapTraverse;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.portal.EdgePortal;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.portal.EdgePortalDetector;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.portal.PortalNodeRef;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.portal.PortalRoute;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.BetterWorldBorder;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.MutableMoveResult;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * The actual A* pathfinding
 *
 * @author leijurv
 */
public final class AStarPathFinder extends AbstractNodeCostSearch {
    private static final double FLIGHT_TURN_PENALTY = 5.0D;
    private final Favoring favoring;
    private final CalculationContext calcContext;
    private volatile AStarPathFinder capabilityProbe;

    @Override
    public void cancel() {
        super.cancel();
        AStarPathFinder probe = capabilityProbe;
        if (probe != null) probe.cancel();
    }

    public AStarPathFinder(int startX, int startY, int startZ, Goal goal, Favoring favoring,
            CalculationContext context) {
        super(startX, startY, startZ, goal, context);
        this.favoring = favoring;
        this.calcContext = context;
    }

    @Override
    protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
        // A context can survive replans; a geometry cache cannot survive world
        // changes, nor be shared with another concurrent path calculation.
        calcContext.parkourSurfaces.remove();
        // Blink pathing reuses the axis-aligned flight move set: teleports
        // move exactly like FLIGHT_* neighbours (up/down/N/E/S/W), never a
        // walking traverse that has to route around a wall on foot.
        boolean flightPathing = Baritone.settings().allowFlightPathing.value
                || Baritone.settings().allowBlinkPathing.value;
        int seedX = startX, seedY = startY, seedZ = startZ;
        boolean blink = Baritone.settings().allowBlinkPathing.value;
        if (flightPathing && !(blink
                ? MovementFly.blinkBoxFits(calcContext, seedX, seedY, seedZ)
                : MovementHelper.canFlyThrough(calcContext, seedX, seedY, seedZ))) {
            // Player feet can snap inside a solid block (tp into a wall corner,
            // corridor overshoot). Seeding A* inside the wall makes the search
            // escape through the maze exterior; snap to an adjacent flyable cell.
            // Under blink the seed must also hold the whole player box: standing
            // on a fence/wall top leaves feet at y+0.5, so the integer cell the
            // box model needs is ABOVE the occupied one — probe upward escapes
            // first, then sideways, and down last (down is usually the post
            // itself).
            int[][] snapOffsets = { { 0, 1, 0 }, { 0, 2, 0 }, { 0, 3, 0 },
                    { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
                    { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 },
                    { 0, -1, 0 } };
            for (int[] o : snapOffsets) {
                int nx = startX + o[0], ny = startY + o[1], nz = startZ + o[2];
                if (blink
                        ? MovementFly.blinkBoxFits(calcContext, nx, ny, nz)
                        : MovementHelper.canFlyThrough(calcContext, nx, ny, nz)) {
                    seedX = nx; seedY = ny; seedZ = nz;
                    break;
                }
            }
        }
        startNode = getNodeAtPosition(seedX, seedY, seedZ, BetterBlockPos.longHash(seedX, seedY, seedZ));
        // When the seed snapped away from the real start (e.g. feet on a fence
        // top at y+0.5), PathingBehavior discards any path whose positions don't
        // contain the requested start cell — so prepend a node chain from the
        // real start to the seed. Y is walked first so the entry hop climbs off
        // the fence post before moving sideways. Each hop carries its FLIGHT_*
        // move so assembleMovements rebuilds MovementFly between the cells;
        // blinkAlongPath walks positions() only, so execution is unaffected.
        if (blink && (seedX != startX || seedY != startY || seedZ != startZ)) {
            java.util.List<int[]> cells = new java.util.ArrayList<>();
            int px = startX, py = startY, pz = startZ;
            cells.add(new int[] { px, py, pz });
            while (px != seedX || py != seedY || pz != seedZ) {
                if (py != seedY) py += Integer.compare(seedY, py);
                else if (px != seedX) px += Integer.compare(seedX, px);
                else pz += Integer.compare(seedZ, pz);
                cells.add(new int[] { px, py, pz });
            }
            PathNode head = null, prev = null;
            for (int[] c : cells) {
                // last cell is the seed — reuse the node already fetched above
                PathNode n = (c == cells.get(cells.size() - 1)) ? startNode
                        : new PathNode(c[0], c[1], c[2], goal);
                n.previous = prev;
                if (prev == null) {
                    n.cost = 0;
                } else {
                    int dx = c[0] - prev.x, dy = c[1] - prev.y, dz = c[2] - prev.z;
                    n.previousMove = flightMove(dx, dy, dz);
                    n.cost = prev.cost + Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
                if (head == null) head = n;
                prev = n;
            }
            startNode = head;
        }
        Vec3d initial=calcContext.playerPosition;
        if(calcContext.parkourMode && initial!=null
                && ParkourSurface.supportedFeet(calcContext,initial).equals(new BetterBlockPos(startX,startY,startZ)))
            startNode=getStandingNode(startX,startY,startZ,initial);
        startNode.cost = 0;
        startNode.combinedCost = startNode.estimatedCostToGoal;
        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
        openSet.insert(startNode);
        double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];// keep track of the best node by the metric of
                                                                      // (estimatedCostToGoal + cost / COEFFICIENTS[i])
        for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = startNode.estimatedCostToGoal;
            bestSoFar[i] = startNode;
        }
        MutableMoveResult res = new MutableMoveResult();
        BetterWorldBorder worldBorder = calcContext.worldBorder;
        long startTime = System.currentTimeMillis();
        boolean slowPath = Baritone.settings().slowPath.value;
        if (slowPath) {
            logDebug("slowPath is on, path timeout will be " + Baritone.settings().slowPathTimeoutMS.value
                    + "ms instead of " + primaryTimeout + "ms");
        }
        long primaryTimeoutTime = startTime + (slowPath ? Baritone.settings().slowPathTimeoutMS.value : primaryTimeout);
        // Collision/stance expansion is substantially more expensive than a
        // walking node. Keep a bounded fallback budget when no segment exists;
        // the primary deadline still returns useful paths promptly.
        long failureTimeoutTime = startTime + (slowPath ? Baritone.settings().slowPathTimeoutMS.value
                : calcContext.parkourMode ? Math.max(5000L, failureTimeout) : failureTimeout);
        boolean failing = true;
        int numNodes = 0;
        int numMovementsConsidered = 0;
        int numEmptyChunk = 0;
        boolean isFavoring = !favoring.isEmpty();
        int timeCheckInterval = 1 << 6;
        int pathingMaxChunkBorderFetch = Baritone.settings().pathingMaxChunkBorderFetch.value; // grab all settings
                                                                                               // beforehand so that
                                                                                               // changing settings
                                                                                               // during pathing doesn't
                                                                                               // cause a crash or
                                                                                               // unpredictable behavior
        // A heuristic prefix can commit a parkour run to an irreversible drop.
        // For a loaded destination, keep this background search until the full
        // route is found, the loaded graph is exhausted, or the caller cancels.
        net.minecraft.util.math.BlockPos goalPos = goal instanceof IGoalRenderPos
                ? ((IGoalRenderPos) goal).getGoalPos() : null;
        boolean completeParkour = calcContext.parkourMode && !Baritone.settings().allowFlightPathing.value && goalPos != null
                && calcContext.bsi.worldContainsLoadedChunk(goalPos.getX(), goalPos.getZ());
        double minimumImprovement = Baritone.settings().minimumImprovementRepropagation.value ? MIN_IMPROVEMENT : 0;
        boolean allowFlightDescent = goal instanceof GoalFlightXZ ? false
                : Baritone.settings().flightAutoDescend.value || goal instanceof GoalFlightExact;
        int flightMinY = Math.max(1, Math.min(356, Baritone.settings().flightMinAltitude.value));
        int flightMaxY = Math.max(flightMinY,
                Math.max(Math.max(1, Math.min(356, Baritone.settings().flightMaxAltitude.value)),
                        calcContext.preferredFlightY));
        Moves[] allMoves = java.util.Arrays.stream(Moves.values())
                .filter(m -> m.flight == flightPathing)
                .filter(m -> !flightPathing
                        || Math.abs(m.xOffset) + Math.abs(m.yOffset) + Math.abs(m.zOffset) == 1)
                .filter(m -> !flightPathing || allowFlightDescent || m.yOffset >= 0)
                .toArray(Moves[]::new);
        while (!openSet.isEmpty() && (completeParkour || numEmptyChunk < pathingMaxChunkBorderFetch) && !cancelRequested) {
            if (!completeParkour && (numNodes & (timeCheckInterval - 1)) == 0) { // only call this once every 64 nodes (about half a
                                                             // millisecond)
                long now = System.currentTimeMillis(); // since nanoTime is slow on windows (takes many microseconds)
                if (now - failureTimeoutTime >= 0 || (!failing && now - primaryTimeoutTime >= 0
                        && (!calcContext.parkourMode || hasReturnableSegment()))) {
                    break;
                }
            }
            if (slowPath) {
                try {
                    Thread.sleep(Baritone.settings().slowPathTimeDelayMS.value);
                } catch (InterruptedException ignored) {
                }
            }
            PathNode currentNode = openSet.removeLowest();
            mostRecentConsidered = currentNode;
            numNodes++;
            boolean flightDebug = flightPathing
                    && (com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.enabled()
                            || com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.suppressed());
            if (flightDebug && (numNodes <= 12 || numNodes % 64 == 0)) {
                com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                        .event("flight_pop node=" + currentNode.x + "," + currentNode.y + "," + currentNode.z
                                + " cost=" + currentNode.cost + " heuristic=" + currentNode.estimatedCostToGoal
                                + " inGoal=" + goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)
                                + " goal=" + goal + " open=" + openSet.size());
            }
            if (numNodes < 8 && !flightPathing && com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.enabled()) {
                com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                        .event("astar_pop node=" + currentNode.x + "," + currentNode.y + "," + currentNode.z
                                + " inGoal=" + goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)
                                + " goal=" + goal + " open=" + openSet.size());
            }
            if (currentNode.isCenter() && goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
                if (flightDebug) {
                    com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                            .event("flight_reached node=" + currentNode.x + "," + currentNode.y + "," + currentNode.z
                                    + " nodes=" + numNodes + " considered=" + numMovementsConsidered);
                }
                logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered
                        + " movements considered");
                return Optional.of(new Path(startNode, currentNode, numNodes, goal, calcContext));
            }
            if (currentNode.isCenter()) {
                if (!flightPathing && calcContext.parkourMode && calcContext.allowParkour) {
                    BetterBlockPos source = new BetterBlockPos(currentNode.x, currentNode.y, currentNode.z);
                    java.util.List<com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpCandidate> parkour =
                            MovementParkour.surfaceCandidates(calcContext, source,currentNode.stance);
                    if (numNodes < 3) {
                        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                                .event("surface_candidates start=" + source.x + "," + source.y + "," + source.z
                                        + " count=" + parkour.size());
                    }
                    for (ParkourJumpCandidate candidate : parkour) {
                        BetterBlockPos target = candidate.getDest();
                        if (numNodes < 3 && com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.enabled()) {
                            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                                    .event("surface_edge from=" + source.x + "," + source.y + "," + source.z
                                            + " to=" + target.x + "," + target.y + "," + target.z
                                            + " cost=" + candidate.getCost());
                        }
                        if (target.y < 0 || target.y > 256 || !calcContext.isLoaded(target.x, target.z)
                                || !worldBorder.entirelyContains(target.x, target.z)) continue;
                        double actionCost = candidate.getCost();
                        if (actionCost <= 0 || !Double.isFinite(actionCost) || actionCost >= ActionCosts.COST_INF) continue;
                        numMovementsConsidered++;
                        Vec3d[] points=candidate.getRoutePoints();
                        PathNode neighbor = getStandingNode(target.x,target.y,target.z,points[points.length-1]);
                        boolean accepted = considerNeighbor(currentNode, neighbor, actionCost, null, isFavoring, minimumImprovement,
                                bestHeuristicSoFar, openSet);
                        if(accepted) neighbor.previousParkour=candidate;
                        if (numNodes < 3 && target.x == 8 && target.z == source.z) {
                            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                                    .event("surface_accept to=" + target.x + "," + target.y + "," + target.z
                                            + " accepted=" + accepted + " neighborCost=" + neighbor.cost);
                        }
                        if (accepted && failing
                                && getDistFromStartSq(neighbor) > MIN_DIST_PATH * MIN_DIST_PATH) failing = false;
                    }
                }
                if(!flightPathing && calcContext.parkourMode
                        && !com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface
                                .clearWalkingSource(calcContext,currentNode.x,currentNode.y,currentNode.z)
                        && calcContext.getBlock(currentNode.x,currentNode.y,currentNode.z)!=net.minecraft.init.Blocks.LADDER
                        && calcContext.getBlock(currentNode.x,currentNode.y,currentNode.z)!=net.minecraft.init.Blocks.VINE) continue;
                for (Moves moves : allMoves) {
                    if (!flightPathing && calcContext.parkourMode && moves.name().startsWith("PARKOUR_")) continue;
                    int newX = currentNode.x + moves.xOffset;
                    int newZ = currentNode.z + moves.zOffset;
                    if ((newX >> 4 != currentNode.x >> 4 || newZ >> 4 != currentNode.z >> 4)
                            && !calcContext.isLoaded(newX, newZ)) {
                        if (!moves.dynamicXZ) {
                            numEmptyChunk++;
                        }
                        continue;
                    }
                    if (!moves.dynamicXZ && !worldBorder.entirelyContains(newX, newZ)) {
                        continue;
                    }
                    int newY = currentNode.y + moves.yOffset;
                    if (newY < 0 || (!flightPathing && newY > 256)) {
                        continue;
                    }
                    if (flightPathing && newY > flightMaxY) {
                        continue;
                    }
                    if (flightPathing && newY < flightMinY
                            && (moves.xOffset != 0 || moves.zOffset != 0)) {
                        continue;
                    }
                    res.reset();
                    try {
                        moves.apply(calcContext, currentNode.x, currentNode.y, currentNode.z, res);
                    } catch (Throwable t) {
                        if ((numNodes <= 3 && com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.enabled())
                                || (flightDebug && numNodes <= 64)) {
                            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                                    .event("astar_move_threw move=" + moves + " from=" + currentNode.x + "," + currentNode.y
                                            + "," + currentNode.z + " err=" + t);
                        }
                        continue;
                    }
                    numMovementsConsidered++;
                    double actionCost = res.cost;
                    if (flightDebug && (numNodes <= 12 || numNodes % 64 == 0) && actionCost >= ActionCosts.COST_INF) {
                        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                                .event("flight_move_blocked move=" + moves + " from=" + currentNode.x + "," + currentNode.y
                                        + "," + currentNode.z + " to=" + res.x + "," + res.y + "," + res.z);
                    }
                    if ((numNodes <= 3 && com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.enabled())
                            || (flightDebug && numNodes <= 12)) {
                        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                                .event("astar_move move=" + moves + " from=" + currentNode.x + "," + currentNode.y
                                        + "," + currentNode.z + " res=" + res.x + "," + res.y + "," + res.z
                                        + " cost=" + actionCost);
                    }
                    if (actionCost >= ActionCosts.COST_INF) {
                        continue;
                    }
                    if (!flightPathing && calcContext.parkourMode) {
                        net.minecraft.block.Block feetBlock=calcContext.getBlock(res.x,res.y,res.z);
                        net.minecraft.block.Block headBlock=calcContext.getBlock(res.x,res.y+1,res.z);
                        if ((!calcContext.allowFireContact && (feetBlock==net.minecraft.init.Blocks.FIRE || headBlock==net.minecraft.init.Blocks.FIRE))
                                || feetBlock==net.minecraft.init.Blocks.WEB || headBlock==net.minecraft.init.Blocks.WEB) continue;
                        // Only the precise parkour approach (above), or an
                        // existing launch, can preserve the trigger's entry speed.
                        if (!moves.name().startsWith("PISTON_") && MovementPistonLaunch.hasLaunch(
                                calcContext,new BetterBlockPos(res.x,res.y,res.z))) continue;
                    }
                    if (actionCost <= 0 || Double.isNaN(actionCost)) {
                        throw new IllegalStateException(moves + " calculated implausible cost " + actionCost);
                    }
                    if (flightPathing && changesHorizontalDirection(currentNode.previousMove, moves)) {
                        actionCost += FLIGHT_TURN_PENALTY;
                    }
                    if (moves.dynamicXZ && !worldBorder.entirelyContains(res.x, res.z)) {
                        continue;
                    }
                    if (!moves.dynamicXZ && (res.x != newX || res.z != newZ)) {
                        throw new IllegalStateException(moves + " " + res.x + " " + newX + " " + res.z + " " + newZ);
                    }
                    if (!moves.dynamicY && res.y != currentNode.y + moves.yOffset) {
                        throw new IllegalStateException(moves + " " + res.y + " " + (currentNode.y + moves.yOffset));
                    }
                    PathNode neighbor = getNodeAtPosition(res.x, res.y, res.z, BetterBlockPos.longHash(res.x, res.y, res.z));
                    if (considerNeighbor(currentNode, neighbor, actionCost, moves, isFavoring, minimumImprovement,
                            bestHeuristicSoFar, openSet) && failing
                            && getDistFromStartSq(neighbor) > MIN_DIST_PATH * MIN_DIST_PATH) {
                        failing = false;
                    }
                }
                if (!flightPathing) {
                    numMovementsConsidered += considerPortalEntries(currentNode, worldBorder, isFavoring,
                            minimumImprovement, bestHeuristicSoFar, openSet);
                }
            } else if (currentNode.isPortal()) {
                numMovementsConsidered += considerPortalTransitions(currentNode, worldBorder, isFavoring,
                        minimumImprovement, bestHeuristicSoFar, openSet);
                numMovementsConsidered += considerPortalExit(currentNode, worldBorder, isFavoring, minimumImprovement,
                        bestHeuristicSoFar, openSet);
            }
        }
        if (cancelRequested) {
            return Optional.empty();
        }
        if (completeParkour && openSet.isEmpty()) {
            if (calcContext.maxJumpHeight < 6) {
                Optional<IPath> frontier = capabilityFrontier(numNodes);
                if (frontier.isPresent()) return frontier;
            }
            // Only reject a complete loaded-goal search. With an unloaded goal,
            // a supported prefix can reveal more chunks or actual map effects.
            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                    .event("parkour_exhausted_no_route start=" + startX + "," + startY + "," + startZ
                            + " nodes=" + numNodes);
            return Optional.empty();
        }
        if (flightPathing && (com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.enabled()
                || com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.suppressed())) {
            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                    .event("flight_exhausted nodes=" + numNodes + " considered=" + numMovementsConsidered
                            + " open=" + openSet.size() + " start=" + startX + "," + startY + "," + startZ
                            + " goal=" + goal);
        }
        System.out.println(numMovementsConsidered + " movements considered");
        System.out.println("Open set size: " + openSet.size());
        System.out.println("PathNode map size: " + mapSize());
        System.out.println(
                (int) (numNodes * 1.0 / ((System.currentTimeMillis() - startTime) / 1000F)) + " nodes per second");
        Optional<IPath> result = bestSoFar(true, numNodes);
        if (result.isPresent()) {
            logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered
                    + " movements considered");
        }
        return result;
    }

    private static Moves flightMove(int dx, int dy, int dz) {
        for (Moves m : Moves.values()) {
            if (m.flight && m.xOffset == dx && m.yOffset == dy && m.zOffset == dz) {
                return m;
            }
        }
        return null;
    }

    private Optional<IPath> capabilityFrontier(int numNodes) {
        // A map may grant effects only after entering the next platform. Explore
        // geometry to locate that frontier, but execute ONLY original graph edges.
        // ponytail: six-block probe ceiling; model observed effect regions if maps need more.
        BetterBlockPos target = new BetterBlockPos(((IGoalRenderPos) goal).getGoalPos());
        if (com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface
                .support(calcContext, target) == null) return Optional.empty();
        CalculationContext geometry = new CalculationContext(calcContext.getBaritone(), goal, calcContext.bsi,
                calcContext::collisionBoxes, 6, calcContext.playerPosition, calcContext.allowFireContact,
                calcContext.failureScope);
        capabilityProbe = new AStarPathFinder(startX, startY, startZ, goal, new Favoring(null, geometry), geometry);
        if (cancelRequested) return Optional.empty();
        Optional<IPath> hint = capabilityProbe.calculate(500, 5000).getPath();
        if (cancelRequested || !hint.isPresent() || !goal.isInGoal(hint.get().getDest())) return Optional.empty();
        java.util.Map<BetterBlockPos, PathNode> reached = new java.util.HashMap<>();
        nodes().filter(n -> n.isCenter() && n.cost < ActionCosts.COST_INF).forEach(n ->
                reached.merge(new BetterBlockPos(n.x, n.y, n.z), n, (a, b) -> a.cost <= b.cost ? a : b));
        java.util.List<BetterBlockPos> positions = hint.get().positions();
        int frontierIndex = positions.size();
        // A boosted shortcut can rejoin the ordinary graph later. Only the
        // suffix beyond its last reachable position requires a new ability.
        while (frontierIndex > 0 && !reached.containsKey(positions.get(frontierIndex - 1))) frontierIndex--;
        for (BetterBlockPos pos : positions.subList(frontierIndex, positions.size())) {
            PathNode nearest = null;
            double distance = new BetterBlockPos(startX, startY, startZ).distanceSq(pos) - 1;
            for (PathNode node : reached.values()) {
                double candidateDistance = new BetterBlockPos(node.x, node.y, node.z).distanceSq(pos);
                // Don't shuffle around the same barrier while no new ability is observed.
                if (getDistFromStartSq(node) < MIN_DIST_PATH * MIN_DIST_PATH || candidateDistance > distance) continue;
                if (nearest != null && candidateDistance == distance && node.cost >= nearest.cost) continue;
                net.minecraft.util.math.AxisAlignedBB support =
                        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface
                                .support(calcContext, new BetterBlockPos(node.x, node.y, node.z));
                if (support == null || support.maxX - support.minX < .8 || support.maxZ - support.minZ < .8) continue;
                if (!com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface
                        .clearWalkingSource(calcContext, node.x, node.y, node.z)) continue;
                nearest = node;
                distance = candidateDistance;
            }
            if (nearest == null) return Optional.empty();
            preserveSegmentEnd = true;
            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE
                    .event("parkour_capability_frontier from=" + startNode + " destination="
                            + new BetterBlockPos(nearest.x, nearest.y, nearest.z) + " blocked=" + pos);
            return Optional.of(new Path(startNode, nearest, numNodes, goal, calcContext));
        }
        return Optional.empty();
    }

    private boolean hasReturnableSegment() {
        // A distant discovered neighbour need not improve any best-so-far
        // coefficient. Stopping then returns no path and repeats the same search.
        for (PathNode node : bestSoFar) {
            if (node != null && getDistFromStartSq(node) > MIN_DIST_PATH * MIN_DIST_PATH) return true;
        }
        return false;
    }

    private int considerPortalEntries(PathNode currentNode, BetterWorldBorder worldBorder, boolean isFavoring,
            double minimumImprovement, double[] bestHeuristicSoFar, BinaryHeapOpenSet openSet) {
        int considered = 0;
        for (EnumFacing travelFacing : EnumFacing.HORIZONTALS) {
            considered += tryPortalEntry(currentNode, travelFacing, true, currentNode.y, worldBorder, isFavoring,
                    minimumImprovement, bestHeuristicSoFar, openSet);
            considered += tryPortalEntry(currentNode, travelFacing, false, currentNode.y, worldBorder, isFavoring,
                    minimumImprovement, bestHeuristicSoFar, openSet);
            considered += tryPortalEntry(currentNode, travelFacing, true, currentNode.y + 1, worldBorder, isFavoring,
                    minimumImprovement, bestHeuristicSoFar, openSet);
            considered += tryPortalEntry(currentNode, travelFacing, false, currentNode.y + 1, worldBorder, isFavoring,
                    minimumImprovement, bestHeuristicSoFar, openSet);
        }
        return considered;
    }

    private int tryPortalEntry(PathNode currentNode, EnumFacing travelFacing, boolean positiveSide, int portalPlaneY,
            BetterWorldBorder worldBorder, boolean isFavoring, double minimumImprovement, double[] bestHeuristicSoFar,
            BinaryHeapOpenSet openSet) {
        if (portalPlaneY < 0 || portalPlaneY > 256) {
            return 1;
        }
        BetterBlockPos portalCell;
        BetterBlockPos barrierA;
        BetterBlockPos barrierB;
        int destX = currentNode.x + travelFacing.getFrontOffsetX() * 2;
        int destZ = currentNode.z + travelFacing.getFrontOffsetZ() * 2;
        if (!worldBorder.entirelyContains(destX, destZ)) {
            return 1;
        }
        if (!calcContext.isLoaded(destX, destZ)) {
            return 1;
        }

        if (travelFacing.getAxis() == EnumFacing.Axis.Z) {
            portalCell = new BetterBlockPos(currentNode.x, portalPlaneY, currentNode.z + travelFacing.getFrontOffsetZ());
            barrierA = portalCell;
            barrierB = positiveSide ? portalCell.east() : portalCell.west();
        } else {
            portalCell = new BetterBlockPos(currentNode.x + travelFacing.getFrontOffsetX(), portalPlaneY, currentNode.z);
            barrierA = portalCell;
            barrierB = positiveSide ? portalCell.south() : portalCell.north();
        }

        PortalTransitionCandidate candidate = resolvePortalTransitionCandidate(currentNode.x, currentNode.y, currentNode.z,
                destX, destZ, travelFacing, barrierA, barrierB, portalPlaneY);
        if (candidate == null) {
            return 1;
        }

        double actionCost = candidate.route.getCostToGapCenter();
        if (actionCost <= 0 || Double.isNaN(actionCost)) {
            return 1;
        }

        PortalNodeRef portalRef = new PortalNodeRef(
                candidate.portal.getBoundaryAnchor(),
                candidate.portal.getBoundaryFacing(),
                candidate.portal.getTravelFacing(),
                candidate.portal.getBarrierA(),
                candidate.portal.getBarrierB(),
                currentNode.y);
        PathNode portalNode = getPortalNodeAtPosition(candidate.portalCell.x, candidate.portalCell.y, candidate.portalCell.z,
                portalRef);
        considerNeighbor(currentNode, portalNode, actionCost, null, isFavoring, minimumImprovement, bestHeuristicSoFar,
                openSet);
        return 1;
    }

    private int considerPortalExit(PathNode currentNode, BetterWorldBorder worldBorder, boolean isFavoring,
            double minimumImprovement, double[] bestHeuristicSoFar, BinaryHeapOpenSet openSet) {
        PortalNodeRef portalRef = currentNode.portalRef;
        if (portalRef == null) {
            return 0;
        }
        EnumFacing travelFacing = portalRef.getTravelFacing();
        int srcX = currentNode.x - travelFacing.getFrontOffsetX();
        int srcZ = currentNode.z - travelFacing.getFrontOffsetZ();
        int destX = currentNode.x + travelFacing.getFrontOffsetX();
        int destZ = currentNode.z + travelFacing.getFrontOffsetZ();
        if (!worldBorder.entirelyContains(destX, destZ) || !calcContext.isLoaded(destX, destZ)) {
            return 1;
        }

        PortalTransitionCandidate candidate = resolvePortalTransitionCandidate(srcX, portalRef.getEntrySourceY(), srcZ,
                destX, destZ, travelFacing, portalRef.getBarrierA(), portalRef.getBarrierB(), currentNode.y);
        if (candidate == null) {
            return 1;
        }
        double entryCost = candidate.route.getCostToGapCenter();
        double actionCost = candidate.result.cost - entryCost;
        if (actionCost <= 0 || Double.isNaN(actionCost)) {
            return 1;
        }
        PathNode centerNode = getNodeAtPosition(candidate.result.x, candidate.result.y, candidate.result.z,
                BetterBlockPos.longHash(candidate.result.x, candidate.result.y, candidate.result.z));
        considerNeighbor(currentNode, centerNode, actionCost, null, isFavoring, minimumImprovement, bestHeuristicSoFar,
                openSet);
        return 1;
    }

    private int considerPortalTransitions(PathNode currentNode, BetterWorldBorder worldBorder, boolean isFavoring,
            double minimumImprovement, double[] bestHeuristicSoFar, BinaryHeapOpenSet openSet) {
        PortalNodeRef portalRef = currentNode.portalRef;
        if (portalRef == null) {
            return 0;
        }
        EdgePortal currentPortal = resolvePortal(portalRef, currentNode.y);
        if (currentPortal == null) {
            return 0;
        }
        int considered = 0;
        for (EnumFacing step : new EnumFacing[] { portalRef.getTravelFacing(), portalRef.getTravelFacing().getOpposite() }) {
            considered++;
            int nextPortalX = currentNode.x + step.getFrontOffsetX();
            int nextPortalZ = currentNode.z + step.getFrontOffsetZ();
            if (!worldBorder.entirelyContains(nextPortalX, nextPortalZ) || !calcContext.isLoaded(nextPortalX, nextPortalZ)) {
                continue;
            }
            BetterBlockPos nextBarrierA = portalRef.getBarrierA().offset(step);
            BetterBlockPos nextBarrierB = portalRef.getBarrierB().offset(step);
            IBlockState nextStateA = calcContext.get(nextBarrierA.x, nextBarrierA.y, nextBarrierA.z);
            IBlockState nextStateB = calcContext.get(nextBarrierB.x, nextBarrierB.y, nextBarrierB.z);
            EdgePortal nextPortal = EdgePortalDetector.detect(calcContext, nextBarrierA, nextStateA, nextBarrierB,
                    nextStateB, portalRef.getTravelFacing(), currentNode.y);
            if (nextPortal == null) {
                continue;
            }
            Vec3d[] routePoints = new Vec3d[] { currentPortal.getGapCenter(), nextPortal.getGapCenter() };
            if (!RouteCollisionSampler.isRouteClear(calcContext, routePoints, currentNode.y, currentNode.y + 1.799D,
                    true)) {
                continue;
            }
            double actionCost = ActionCosts.WALK_ONE_BLOCK_COST * currentPortal.getGapCenter().distanceTo(nextPortal.getGapCenter());
            if (actionCost <= 0 || Double.isNaN(actionCost)) {
                continue;
            }
            PortalNodeRef nextPortalRef = new PortalNodeRef(
                    nextPortal.getBoundaryAnchor(),
                    nextPortal.getBoundaryFacing(),
                    nextPortal.getTravelFacing(),
                    nextPortal.getBarrierA(),
                    nextPortal.getBarrierB(),
                    portalRef.getEntrySourceY());
            if (nextPortalRef.equals(portalRef)) {
                continue;
            }
            PathNode neighbor = getPortalNodeAtPosition(nextPortalX, currentNode.y, nextPortalZ, nextPortalRef);
            considerNeighbor(currentNode, neighbor, actionCost, null, isFavoring, minimumImprovement, bestHeuristicSoFar,
                    openSet);
        }
        return considered;
    }

    private EdgePortal resolvePortal(PortalNodeRef portalRef, int y) {
        IBlockState stateA = calcContext.get(portalRef.getBarrierA().x, portalRef.getBarrierA().y, portalRef.getBarrierA().z);
        IBlockState stateB = calcContext.get(portalRef.getBarrierB().x, portalRef.getBarrierB().y, portalRef.getBarrierB().z);
        return EdgePortalDetector.detect(calcContext, portalRef.getBarrierA(), stateA,
                portalRef.getBarrierB(), stateB, portalRef.getTravelFacing(), y);
    }

    private PortalTransitionCandidate resolvePortalTransitionCandidate(int srcX, int srcY, int srcZ, int destX, int destZ,
            EnumFacing travelFacing, BetterBlockPos barrierA, BetterBlockPos barrierB, int portalPlaneY) {
        IBlockState stateA = calcContext.get(barrierA.x, barrierA.y, barrierA.z);
        IBlockState stateB = calcContext.get(barrierB.x, barrierB.y, barrierB.z);
        EdgePortal portal = EdgePortalDetector.detect(calcContext, barrierA, stateA, barrierB, stateB, travelFacing,
                portalPlaneY);
        if (portal == null) {
            return null;
        }

        MutableMoveResult result = new MutableMoveResult();
        MovementNarrowGapTraverse.cost(calcContext, srcX, srcY, srcZ, destX, destZ, barrierA, barrierB, result);
        if (result.cost >= ActionCosts.COST_INF) {
            return null;
        }

        PortalRoute route = portal.createRoute(
                blockCenter(srcX, srcY, srcZ),
                blockCenter(result.x, result.y, result.z));
        return new PortalTransitionCandidate(portal, barrierA, barrierB, portalPlaneY, result, route);
    }

    private boolean considerNeighbor(PathNode currentNode, PathNode neighbor, double actionCost, Moves moveUsed,
            boolean isFavoring, double minimumImprovement, double[] bestHeuristicSoFar, BinaryHeapOpenSet openSet) {
        // Every ordinary edge must enter a launcher through its trigger and
        // dedicated controller, never land on its moving slime as static terrain.
        if (calcContext.parkourMode && !Baritone.settings().allowFlightPathing.value && neighbor.isCenter()
                && com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch
                        .isLaunchPad(calcContext, new BetterBlockPos(neighbor.x, neighbor.y, neighbor.z))) return false;
        long hashCode = neighbor.isPortal() && neighbor.portalRef != null
                ? neighbor.portalRef.longHash()
                : BetterBlockPos.longHash(neighbor.x, neighbor.y, neighbor.z);
        if (isFavoring) {
            actionCost *= favoring.calculate(neighbor.x, neighbor.y, neighbor.z, hashCode);
        }
        double tentativeCost = currentNode.cost + actionCost;
        if (neighbor.cost - tentativeCost > minimumImprovement) {
            // Different standing offsets share a block in the executable path.
            // Do not evade its no-loop invariant by returning to an ancestor
            // through a second stance node after a rejected jump.
            if (calcContext.parkourMode && neighbor.isCenter()) {
                for (PathNode ancestor = currentNode; ancestor != null; ancestor = ancestor.previous) {
                    if (ancestor.isCenter() && ancestor.x == neighbor.x
                            && ancestor.y == neighbor.y && ancestor.z == neighbor.z) return false;
                }
            }
            neighbor.previous = currentNode;
            neighbor.previousMove = moveUsed;
            neighbor.previousParkour = null;
            neighbor.cost = tentativeCost;
            neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;
            if (neighbor.isOpen()) {
                openSet.update(neighbor);
            } else {
                openSet.insert(neighbor);
            }
            for (int i = 0; i < COEFFICIENTS.length; i++) {
                double heuristic = neighbor.estimatedCostToGoal + neighbor.cost / COEFFICIENTS[i];
                if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
                    bestHeuristicSoFar[i] = heuristic;
                    bestSoFar[i] = neighbor;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean changesHorizontalDirection(Moves previous, Moves current) {
        if (previous == null || current == null || previous.yOffset != 0 || current.yOffset != 0) {
            return false;
        }
        return previous.xOffset != current.xOffset || previous.zOffset != current.zOffset;
    }

    private static Vec3d blockCenter(int x, int y, int z) {
        return new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D);
    }

    private static final class PortalTransitionCandidate {
        private final EdgePortal portal;
        private final BetterBlockPos portalCell;
        private final int portalPlaneY;
        private final MutableMoveResult result;
        private final PortalRoute route;

        private PortalTransitionCandidate(EdgePortal portal, BetterBlockPos barrierA, BetterBlockPos barrierB,
                int portalPlaneY, MutableMoveResult result, PortalRoute route) {
            this.portal = portal;
            this.portalCell = barrierA;
            this.portalPlaneY = portalPlaneY;
            this.result = result;
            this.route = route;
        }
    }
}
