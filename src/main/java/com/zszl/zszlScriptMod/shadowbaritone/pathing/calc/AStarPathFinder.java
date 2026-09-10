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

package com.zszl.zszlScriptMod.shadowbaritone.pathing.calc;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.calc.openset.BinaryHeapOpenSet;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Moves;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementNarrowGapTraverse;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortal;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortalDetector;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.PortalNodeRef;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.PortalRoute;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.BetterWorldBorder;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.Favoring;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.MutableMoveResult;
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

    public AStarPathFinder(int startX, int startY, int startZ, Goal goal, Favoring favoring,
            CalculationContext context) {
        super(startX, startY, startZ, goal, context);
        this.favoring = favoring;
        this.calcContext = context;
    }

    @Override
    protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
        startNode = getNodeAtPosition(startX, startY, startZ, BetterBlockPos.longHash(startX, startY, startZ));
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
        BetterWorldBorder worldBorder = new BetterWorldBorder(calcContext.world.getWorldBorder());
        long startTime = System.currentTimeMillis();
        boolean slowPath = Baritone.settings().slowPath.value;
        if (slowPath) {
            logDebug("slowPath is on, path timeout will be " + Baritone.settings().slowPathTimeoutMS.value
                    + "ms instead of " + primaryTimeout + "ms");
        }
        long primaryTimeoutTime = startTime + (slowPath ? Baritone.settings().slowPathTimeoutMS.value : primaryTimeout);
        long failureTimeoutTime = startTime + (slowPath ? Baritone.settings().slowPathTimeoutMS.value : failureTimeout);
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
        double minimumImprovement = Baritone.settings().minimumImprovementRepropagation.value ? MIN_IMPROVEMENT : 0;
        boolean flightPathing = Baritone.settings().allowFlightPathing.value;
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
        while (!openSet.isEmpty() && numEmptyChunk < pathingMaxChunkBorderFetch && !cancelRequested) {
            if ((numNodes & (timeCheckInterval - 1)) == 0) { // only call this once every 64 nodes (about half a
                                                             // millisecond)
                long now = System.currentTimeMillis(); // since nanoTime is slow on windows (takes many microseconds)
                if (now - failureTimeoutTime >= 0 || (!failing && now - primaryTimeoutTime >= 0)) {
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
            if (currentNode.isCenter() && goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
                logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered
                        + " movements considered");
                return Optional.of(new Path(startNode, currentNode, numNodes, goal, calcContext));
            }
            if (currentNode.isCenter()) {
                for (Moves moves : allMoves) {
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
                    moves.apply(calcContext, currentNode.x, currentNode.y, currentNode.z, res);
                    numMovementsConsidered++;
                    double actionCost = res.cost;
                    if (actionCost >= ActionCosts.COST_INF) {
                        continue;
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
        long hashCode = neighbor.isPortal() && neighbor.portalRef != null
                ? neighbor.portalRef.longHash()
                : BetterBlockPos.longHash(neighbor.x, neighbor.y, neighbor.z);
        if (isFavoring) {
            actionCost *= favoring.calculate(neighbor.x, neighbor.y, neighbor.z, hashCode);
        }
        double tentativeCost = currentNode.cost + actionCost;
        if (neighbor.cost - tentativeCost > minimumImprovement) {
            neighbor.previous = currentNode;
            neighbor.previousMove = moveUsed;
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
