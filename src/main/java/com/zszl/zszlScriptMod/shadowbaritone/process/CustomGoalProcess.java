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

package com.zszl.zszlScriptMod.shadowbaritone.process;

import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.ICustomGoalProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BaritoneProcessHelper;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GoalTargetNormalizer;
import com.zszl.zszlScriptMod.utils.ModUtils;

import java.util.Objects;

/**
 * As set by ExampleBaritoneControl or something idk
 *
 * @author leijurv
 */
public final class CustomGoalProcess extends BaritoneProcessHelper implements ICustomGoalProcess {

    /**
     * The current goal
     */
    private Goal goal;

    /**
     * The most recent goal. Not invalidated upon {@link #onLostControl()}
     */
    private Goal mostRecentGoal;

    /**
     * The current process state.
     *
     * @see State
     */
    private State state;
    private String lastTickDebugKey;

    public CustomGoalProcess(Baritone baritone) {
        super(baritone);
    }

    private void logBaritoneDebug(String message) {
        if (ModConfig.isDebugFlagEnabled(DebugModule.BARITONE)) {
            logDirect(message);
        }
    }

    @Override
    public void setGoal(Goal goal) {
        goal = GoalTargetNormalizer.normalize(baritone, goal);
        this.goal = goal;
        this.mostRecentGoal = goal;
        logBaritoneDebug(String.format("[DBG][CustomGoalProcess] setGoal goal=%s stateBefore=%s", goal, this.state));
        if (baritone.getElytraProcess() != null && baritone.getElytraProcess().isActive()) {
            baritone.getElytraProcess().pathTo(goal);
        }
        if (this.state == State.NONE) {
            this.state = State.GOAL_SET;
        }
        if (this.state == State.EXECUTING) {
            this.state = State.PATH_REQUESTED;
        }
        logBaritoneDebug(String.format("[DBG][CustomGoalProcess] setGoal done stateAfter=%s", this.state));
    }

    @Override
    public void path() {
        logBaritoneDebug(String.format("[DBG][CustomGoalProcess] path() stateBefore=%s goal=%s", this.state, this.goal));
        this.state = State.PATH_REQUESTED;
        logBaritoneDebug(String.format("[DBG][CustomGoalProcess] path() stateAfter=%s", this.state));
    }

    @Override
    public Goal getGoal() {
        return this.goal;
    }

    @Override
    public Goal mostRecentGoal() {
        return this.mostRecentGoal;
    }

    @Override
    public boolean isActive() {
        return this.state != State.NONE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (this.goal != null) {
            Goal normalized = GoalTargetNormalizer.normalize(baritone, this.goal);
            if (!Objects.equals(normalized, this.goal)) {
                this.goal = normalized;
                this.mostRecentGoal = normalized;
                this.state = State.PATH_REQUESTED;
                calcFailed = false;
            }
        }
        if (this.state != State.NONE) {
            String key = this.state + "|" + calcFailed + "|" + isSafeToCancel + "|" + this.goal;
            if (!key.equals(lastTickDebugKey)) {
                lastTickDebugKey = key;
                logBaritoneDebug(
                        String.format("[DBG][CustomGoalProcess] onTick state=%s calcFailed=%s safeToCancel=%s goal=%s",
                                this.state, calcFailed, isSafeToCancel, this.goal));
            }
        }
        switch (this.state) {
            case GOAL_SET:
                return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            case PATH_REQUESTED:
                // return FORCE_REVALIDATE_GOAL_AND_PATH just once
                PathingCommand ret = new PathingCommand(this.goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                this.state = State.EXECUTING;
                return ret;
            case EXECUTING:
                if (calcFailed) {
                    logBaritoneDebug(String.format(
                            "[DBG][CustomGoalProcess] calc failed but retaining goal for retry goal=%s", this.goal));
                    this.state = State.EXECUTING;
                    return new PathingCommand(this.goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                }
                boolean flightTerminal = baritone.getPathingBehavior().isFlightTerminalReached();
                if (this.goal == null || baritone.getPathingBehavior().isFlightTerminalSafeLanding()
                        || (flightTerminal && (this.goal instanceof GoalFlightExact
                        || this.goal instanceof GoalFlightXZ)
                        && this.goal.isInGoal(ctx.playerFeet()))
                        || (!flightTerminal && this.goal.isInGoal(ctx.playerFeet())
                        && this.goal.isInGoal(baritone.getPathingBehavior().pathStart()))) {
                    onLostControl(); // we're there xd
                    if (Baritone.settings().disconnectOnArrival.value) {
                        ModUtils.disconnectFromCurrentWorld();
                    }
                    if (Baritone.settings().notificationOnPathComplete.value) {
                        logNotification(ShadowBaritoneI18n.trKey(
                                "shadowbaritone.process.customgoal.status.pathing_complete"), false);
                    }
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PATH);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void onLostControl() {
        this.lastTickDebugKey = null;
        this.state = State.NONE;
        this.goal = null;
    }

    @Override
    public String displayName0() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.process.customgoal.display.custom_goal",
                this.goal);
    }

    protected enum State {
        NONE,
        GOAL_SET,
        PATH_REQUESTED,
        EXECUTING
    }
}
