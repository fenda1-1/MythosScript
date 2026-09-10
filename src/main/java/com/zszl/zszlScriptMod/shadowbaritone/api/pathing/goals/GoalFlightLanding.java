package com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals;

import com.zszl.zszlScriptMod.config.FlightPathingConfig;
import net.minecraft.util.math.BlockPos;

/**
 * A flight landing target accepts a small horizontal arrival area while retaining
 * the requested Y level, so fast continuous flight does not overshoot one block.
 */
public class GoalFlightLanding extends GoalBlock {

    public GoalFlightLanding(BlockPos pos) {
        super(pos);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        long xDiff = (long) x - this.x;
        long zDiff = (long) z - this.z;
        long range = FlightPathingConfig.arrivalRange;
        return y == this.y && xDiff * xDiff + zDiff * zDiff <= range * range;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double xDiff = (double) x - this.x;
        double zDiff = (double) z - this.z;
        double horizontalDistance = Math.max(0.0D,
                Math.sqrt(xDiff * xDiff + zDiff * zDiff) - FlightPathingConfig.arrivalRange);
        return GoalYLevel.calculate(this.y, y) + GoalXZ.calculate(horizontalDistance, 0.0D);
    }
}
