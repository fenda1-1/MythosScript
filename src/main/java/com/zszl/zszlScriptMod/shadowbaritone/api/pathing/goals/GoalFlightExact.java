package com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals;

import net.minecraft.util.math.BlockPos;

/** An explicit XYZ flight target whose route must connect to the requested coordinate. */
public final class GoalFlightExact extends GoalFlightLanding {

    public GoalFlightExact(BlockPos pos) {
        super(pos);
    }
}
