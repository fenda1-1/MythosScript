package com.zszl.zszlScriptMod.shadowbaritone.pathing.path;

import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.PathBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrbitRoutePath extends PathBase {

    private final Goal goal;
    private final List<BetterBlockPos> positions;
    private final List<IMovement> movements;
    private final int numNodesConsidered;
    private final String routeKey;
    private final int startNodeIndex;
    private final int endNodeIndex;

    public OrbitRoutePath(Goal goal, List<BetterBlockPos> positions, List<IMovement> movements, int numNodesConsidered,
            String routeKey, int startNodeIndex, int endNodeIndex) {
        this.goal = goal;
        this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
        this.movements = Collections.unmodifiableList(new ArrayList<>(movements));
        this.numNodesConsidered = numNodesConsidered;
        this.routeKey = routeKey == null ? "" : routeKey;
        this.startNodeIndex = startNodeIndex;
        this.endNodeIndex = endNodeIndex;
        sanityCheck();
    }

    @Override
    public Goal getGoal() {
        return goal;
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
        return this;
    }

    @Override
    public int getNumNodesConsidered() {
        return numNodesConsidered;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public int getStartNodeIndex() {
        return startNodeIndex;
    }

    public int getEndNodeIndex() {
        return endNodeIndex;
    }
}
