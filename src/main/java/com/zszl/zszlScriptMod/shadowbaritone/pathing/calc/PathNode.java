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

import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Moves;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.PortalNodeRef;

/**
 * A node in the path, containing the cost and steps to get to it.
 *
 * @author leijurv
 */
public final class PathNode {

    /**
     * The position of this node
     */
    public final int x;
    public final int y;
    public final int z;
    public final PathNodeKind kind;
    public final PortalNodeRef portalRef;

    /**
     * Cached, should always be equal to goal.heuristic(pos)
     */
    public final double estimatedCostToGoal;

    /**
     * Total cost of getting from start to here
     * Mutable and changed by PathFinder
     */
    public double cost;

    /**
     * Should always be equal to estimatedCosttoGoal + cost
     * Mutable and changed by PathFinder
     */
    public double combinedCost;

    /**
     * In the graph search, what previous node contributed to the cost
     * Mutable and changed by PathFinder
     */
    public PathNode previous;

    /**
     * The concrete movement that led from {@link #previous} to this node.
     */
    public Moves previousMove;

    /**
     * Where is this node in the array flattenization of the binary heap? Needed for
     * decrease-key operations.
     */
    public int heapPosition;

    public PathNode(int x, int y, int z, Goal goal) {
        this(x, y, z, PathNodeKind.CENTER, null, goal);
    }

    public PathNode(int x, int y, int z, PortalNodeRef portalRef, Goal goal) {
        this(x, y, z, PathNodeKind.PORTAL, portalRef, goal);
    }

    private PathNode(int x, int y, int z, PathNodeKind kind, PortalNodeRef portalRef, Goal goal) {
        this.previous = null;
        this.previousMove = null;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = x;
        this.y = y;
        this.z = z;
        this.kind = kind;
        this.portalRef = portalRef;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    public boolean isCenter() {
        return kind == PathNodeKind.CENTER;
    }

    public boolean isPortal() {
        return kind == PathNodeKind.PORTAL;
    }

    /**
     * TODO: Possibly reimplement hashCode and equals. They are necessary for this
     * class to function but they could be done better
     *
     * @return The hash code value for this {@link PathNode}
     */
    @Override
    public int hashCode() {
        long hash = BetterBlockPos.longHash(x, y, z);
        hash = 1099511628211L * hash + kind.ordinal();
        if (portalRef != null) {
            hash = 1099511628211L * hash + portalRef.longHash();
        }
        return (int) hash;
    }

    @Override
    public boolean equals(Object obj) {
        // GOTTA GO FAST
        // ALL THESE CHECKS ARE FOR PEOPLE WHO WANT SLOW CODE
        // SKRT SKRT
        // if (obj == null || !(obj instanceof PathNode)) {
        // return false;
        // }

        final PathNode other = (PathNode) obj;
        // return Objects.equals(this.pos, other.pos) && Objects.equals(this.goal,
        // other.goal);

        if (x != other.x || y != other.y || z != other.z || kind != other.kind) {
            return false;
        }
        if (portalRef == null) {
            return other.portalRef == null;
        }
        return portalRef.equals(other.portalRef);
    }
}
