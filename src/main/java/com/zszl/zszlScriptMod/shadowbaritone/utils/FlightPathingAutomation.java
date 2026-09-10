package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.config.FlightPathingConfig;
import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.FlightDirectPath;

/** Keeps the optional fly-feature lifecycle synchronized with flight pathing. */
public final class FlightPathingAutomation {

    private static Goal activeGoal;
    private static boolean arrivalHandled;

    private FlightPathingAutomation() {
    }

    public static void onPathRequested(Goal goal) {
        if (!Baritone.settings().allowFlightPathing.value || goal == null) {
            clear();
            return;
        }
        boolean newGoal = !goal.equals(activeGoal);
        if (newGoal) {
            activeGoal = goal;
            arrivalHandled = false;
        }
        if (FlightPathingConfig.autoEnableFly && !FlyHandler.enabled) {
            FlyHandler.INSTANCE.setEnabled(true);
        }
    }

    public static void onTick(Baritone baritone, Goal goal, BetterBlockPos playerFeet, boolean terminalSafeLanding) {
        if (!Baritone.settings().allowFlightPathing.value || playerFeet == null) {
            clear();
            return;
        }
        if (goal != null && !goal.equals(activeGoal)) {
            onPathRequested(goal);
        }
        // CustomGoalProcess clears PathingBehavior.goal as soon as it detects arrival,
        // before this end-of-tick hook runs. Retain the requested goal for this final
        // check so auto-disable cannot miss the exact arrival tick.
        Goal arrivalGoal = goal != null ? goal : activeGoal;
        boolean arrived = (arrivalGoal != null && arrivalGoal.isInGoal(playerFeet)) || terminalSafeLanding;
        boolean aerialGoalArrival = arrivalGoal instanceof GoalFlightExact || arrivalGoal instanceof GoalFlightXZ;
        if (arrived && !arrivalHandled && FlightPathingConfig.autoDisableFlyOnArrival
                && (aerialGoalArrival || FlightDirectPath.isSafeLanding(baritone, playerFeet))) {
            arrivalHandled = true;
            if (FlyHandler.enabled) {
                FlyHandler.INSTANCE.setEnabled(false);
            }
        }
        if (goal == null) {
            clear();
        }
    }

    public static void clear() {
        activeGoal = null;
        arrivalHandled = false;
    }
}
