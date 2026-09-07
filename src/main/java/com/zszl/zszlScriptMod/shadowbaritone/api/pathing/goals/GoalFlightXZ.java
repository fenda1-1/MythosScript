package com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals;

import com.zszl.zszlScriptMod.config.FlightPathingConfig;

/** A flight XZ goal satisfied inside an infinite vertical arrival cylinder. */
public final class GoalFlightXZ extends GoalXZ {

    public GoalFlightXZ(int x, int z) {
        super(x, z);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        long xDiff = (long) x - getX();
        long zDiff = (long) z - getZ();
        long range = FlightPathingConfig.arrivalRange;
        return xDiff * xDiff + zDiff * zDiff <= range * range;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double xDiff = (double) x - getX();
        double zDiff = (double) z - getZ();
        double horizontalDistance = Math.max(0.0D,
                Math.sqrt(xDiff * xDiff + zDiff * zDiff) - FlightPathingConfig.arrivalRange);
        return GoalXZ.calculate(horizontalDistance, 0.0D);
    }
}
