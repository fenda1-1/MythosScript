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

import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Moves;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementRouteTraverse;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.CutoffPath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementNarrowGapTraverse;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortal;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortalDetector;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.PortalNodeRef;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.PathBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A node based implementation of IPath
 *
 * @author leijurv
 */
class Path extends PathBase {

    /**
     * The start position of this path
     */
    private final BetterBlockPos start;

    /**
     * The end position of this path
     */
    private final BetterBlockPos end;

    /**
     * The blocks on the path. Guaranteed that path.get(0) equals start and
     * path.get(path.size()-1) equals end
     */
    private final List<BetterBlockPos> path;
    private final List<BetterBlockPos> rawPath;

    private final List<Movement> movements;

    private final List<PathNode> nodes;

    private final Goal goal;

    private final int numNodes;

    private final CalculationContext context;

    private volatile boolean verified;

    Path(PathNode start, PathNode end, int numNodes, Goal goal, CalculationContext context) {
        this.start = new BetterBlockPos(start.x, start.y, start.z);
        this.end = new BetterBlockPos(end.x, end.y, end.z);
        this.numNodes = numNodes;
        this.movements = new ArrayList<>();
        this.path = new ArrayList<>();
        this.goal = goal;
        this.context = context;
        PathNode current = end;
        LinkedList<BetterBlockPos> tempPath = new LinkedList<>();
        LinkedList<PathNode> tempNodes = new LinkedList<>();
        // Repeatedly inserting to the beginning of an arraylist is O(n^2)
        // Instead, do it into a linked list, then convert at the end
        while (current != null) {
            tempNodes.addFirst(current);
            tempPath.addFirst(new BetterBlockPos(current.x, current.y, current.z));
            current = current.previous;
        }
        // Can't directly convert from the PathNode pseudo linked list to an array
        // because we don't know how long it is
        // inserting into a LinkedList<E> keeps track of length, then when we addall
        // (which calls .toArray) it's able
        // to performantly do that conversion since it knows the length.
        this.rawPath = new ArrayList<>(tempPath);
        this.nodes = new ArrayList<>(tempNodes);
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    private boolean assembleMovements() {
        if (rawPath.isEmpty() || !movements.isEmpty()) {
            throw new IllegalStateException();
        }
        path.clear();
        path.add(new BetterBlockPos(nodes.get(0).x, nodes.get(0).y, nodes.get(0).z));
        for (int i = 0; i < nodes.size() - 1;) {
            PathNode srcNode = nodes.get(i);
            PathNode nextNode = nodes.get(i + 1);
            if (srcNode.isCenter() && nextNode.isPortal()) {
                int firstCenterAfterPortal = i + 2;
                while (firstCenterAfterPortal < nodes.size() && nodes.get(firstCenterAfterPortal).isPortal()) {
                    firstCenterAfterPortal++;
                }
                if (firstCenterAfterPortal >= nodes.size() || !nodes.get(firstCenterAfterPortal).isCenter()) {
                    return true;
                }
                PathNode destNode = nodes.get(firstCenterAfterPortal);
                double portalCost = destNode.cost - srcNode.cost;
                Movement portalMove;
                if (firstCenterAfterPortal == i + 2) {
                    portalMove = buildPortalMovement(srcNode, nextNode, destNode, portalCost);
                } else {
                    portalMove = buildPortalChainMovement(srcNode, nodes.subList(i + 1, firstCenterAfterPortal),
                            destNode, portalCost);
                }
                if (portalMove == null) {
                    return true;
                }
                movements.add(portalMove);
                path.add(new BetterBlockPos(destNode.x, destNode.y, destNode.z));
                i = firstCenterAfterPortal;
                continue;
            }
            if (srcNode.isPortal()) {
                return true;
            }
            BetterBlockPos srcPos = new BetterBlockPos(srcNode.x, srcNode.y, srcNode.z);
            BetterBlockPos destPos = new BetterBlockPos(nextNode.x, nextNode.y, nextNode.z);
            double cost = nextNode.cost - srcNode.cost;
            Movement move = runBackwards(srcPos, destPos, nextNode.previousMove, cost);
            if (move == null) {
                return true;
            } else {
                movements.add(move);
                path.add(destPos);
            }
            i++;
        }
        return false;
    }

    private Movement buildPortalMovement(PathNode srcNode, PathNode portalNode, PathNode destNode, double cost) {
        PortalNodeRef portalRef = portalNode.portalRef;
        if (portalRef == null) {
            return null;
        }
        BetterBlockPos srcPos = new BetterBlockPos(srcNode.x, srcNode.y, srcNode.z);
        BetterBlockPos destPos = new BetterBlockPos(destNode.x, destNode.y, destNode.z);
        Movement move = new MovementNarrowGapTraverse(context.getBaritone(), srcPos, destPos,
                portalRef.getBarrierA(), portalRef.getBarrierB());
        move.override(Math.min(move.calculateCost(context), cost));
        return move;
    }

    private Movement buildPortalChainMovement(PathNode srcNode, List<PathNode> portalNodes, PathNode destNode, double cost) {
        List<Vec3d> routePoints = new ArrayList<>();
        Vec3d srcCenter = blockCenter(srcNode.x, srcNode.y, srcNode.z);
        Vec3d destCenter = blockCenter(destNode.x, destNode.y, destNode.z);
        appendRoute(routePoints, srcCenter);
        for (int i = 0; i < portalNodes.size(); i++) {
            EdgePortal portal = resolvePortal(portalNodes.get(i));
            if (portal == null) {
                return null;
            }
            if (i == 0) {
                appendRoute(routePoints, portal.routeFromCenterToGap(srcCenter));
            } else {
                appendRoute(routePoints, portal.getGapCenter());
            }
        }
        EdgePortal lastPortal = resolvePortal(portalNodes.get(portalNodes.size() - 1));
        if (lastPortal == null) {
            return null;
        }
        appendRoute(routePoints, lastPortal.routeFromGapToCenter(destCenter));
        Movement move = new MovementRouteTraverse(context.getBaritone(),
                new BetterBlockPos(srcNode.x, srcNode.y, srcNode.z),
                new BetterBlockPos(destNode.x, destNode.y, destNode.z),
                true,
                routePoints.toArray(new Vec3d[0]));
        move.override(Math.min(move.calculateCost(context), cost));
        return move;
    }

    private EdgePortal resolvePortal(PathNode portalNode) {
        PortalNodeRef portalRef = portalNode.portalRef;
        if (portalRef == null) {
            return null;
        }
        IBlockState stateA = context.get(portalRef.getBarrierA().x, portalRef.getBarrierA().y, portalRef.getBarrierA().z);
        IBlockState stateB = context.get(portalRef.getBarrierB().x, portalRef.getBarrierB().y, portalRef.getBarrierB().z);
        return EdgePortalDetector.detect(context, portalRef.getBarrierA(), stateA,
                portalRef.getBarrierB(), stateB, portalRef.getTravelFacing(), portalNode.y);
    }

    private static void appendRoute(List<Vec3d> points, Vec3d... segment) {
        if (segment == null) {
            return;
        }
        for (Vec3d point : segment) {
            if (point == null) {
                continue;
            }
            if (points.isEmpty() || points.get(points.size() - 1).squareDistanceTo(point) > 1.0E-4D) {
                points.add(point);
            }
        }
    }

    private static Vec3d blockCenter(int x, int y, int z) {
        return new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D);
    }

    private Movement runBackwards(BetterBlockPos src, BetterBlockPos dest, Moves moveUsed, double cost) {
        if (moveUsed != null) {
            Movement move = moveUsed.apply0(context, src);
            if (move.getDest().equals(dest)) {
                move.override(Math.min(move.calculateCost(context), cost));
                return move;
            }
        }
        for (Moves moves : Moves.values()) {
            Movement move = moves.apply0(context, src);
            if (move.getDest().equals(dest)) {
                // have to calculate the cost at calculation time so we can accurately judge
                // whether a cost increase happened between cached calculation and real
                // execution
                // however, taking into account possible favoring that could skew the node cost,
                // we really want the stricter limit of the two
                // so we take the minimum of the path node cost difference, and the calculated
                // cost
                move.override(Math.min(move.calculateCost(context), cost));
                return move;
            }
        }
        // this is no longer called from bestPathSoFar, now it's in postprocessing
        Helper.HELPER.logDebug(
                "Movement became impossible during calculation " + src + " " + dest + " " + dest.subtract(src));
        return null;
    }

    @Override
    public IPath postProcess() {
        if (verified) {
            throw new IllegalStateException();
        }
        verified = true;
        boolean failed = assembleMovements();
        movements.forEach(m -> m.checkLoadedChunk(context));

        if (failed) { // at least one movement became impossible during calculation
            CutoffPath res = new CutoffPath(this, movements().size());
            if (res.movements().size() != movements.size()) {
                throw new IllegalStateException();
            }
            return res;
        }
        // more post processing here
        sanityCheck();
        return this;
    }

    @Override
    public List<IMovement> movements() {
        if (!verified) {
            throw new IllegalStateException();
        }
        return Collections.unmodifiableList(movements);
    }

    @Override
    public List<BetterBlockPos> positions() {
        return Collections.unmodifiableList(verified ? path : rawPath);
    }

    @Override
    public int getNumNodesConsidered() {
        return numNodes;
    }

    @Override
    public BetterBlockPos getSrc() {
        if (verified && !path.isEmpty()) {
            return path.get(0);
        }
        return start;
    }

    @Override
    public BetterBlockPos getDest() {
        if (verified && !path.isEmpty()) {
            return path.get(path.size() - 1);
        }
        return end;
    }
}
