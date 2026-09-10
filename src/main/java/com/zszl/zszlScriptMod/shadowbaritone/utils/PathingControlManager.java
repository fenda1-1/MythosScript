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

package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.TickEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.AbstractGameEventListener;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPathingControlManager;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IBaritoneProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.PathExecutor;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PathingControlManager implements IPathingControlManager {

    private final Baritone baritone;
    private final HashSet<IBaritoneProcess> processes; // unGh
    private final List<IBaritoneProcess> active;
    private IBaritoneProcess inControlLastTick;
    private IBaritoneProcess inControlThisTick;
    private PathingCommand command;
    private String lastPreTickDebugKey;
    private String lastPostTickDebugKey;

    public PathingControlManager(Baritone baritone) {
        this.baritone = baritone;
        this.processes = new HashSet<>();
        this.active = new ArrayList<>();
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() { // needs to be after all
                                                                                               // behavior ticks
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() == TickEvent.Type.IN) {
                    postTick();
                }
            }
        });
    }

    @Override
    public void registerProcess(IBaritoneProcess process) {
        process.onLostControl(); // make sure it's reset
        processes.add(process);
    }

    public void cancelEverything() { // called by PathingBehavior on TickEvent Type OUT
        inControlLastTick = null;
        inControlThisTick = null;
        command = null;
        active.clear();
        for (IBaritoneProcess proc : processes) {
            proc.onLostControl();
            if (proc.isActive() && !proc.isTemporary()) { // it's okay only for a temporary thing (like combat pause) to
                                                          // maintain control even if you say to cancel
                throw new IllegalStateException(proc.displayName());
            }
        }
    }

    @Override
    public Optional<IBaritoneProcess> mostRecentInControl() {
        return Optional.ofNullable(inControlThisTick);
    }

    @Override
    public Optional<PathingCommand> mostRecentCommand() {
        return Optional.ofNullable(command);
    }

    private void logBaritoneDebug(String message) {
        if (ModConfig.isDebugFlagEnabled(DebugModule.BARITONE)) {
            Helper.HELPER.logDirect(message);
        }
    }

    public void preTick() {
        inControlLastTick = inControlThisTick;
        inControlThisTick = null;
        PathingBehavior p = baritone.getPathingBehavior();
        command = executeProcesses();
        if (command != null) {
            String procName = inControlThisTick == null ? "<none>" : inControlThisTick.displayName();
            String preTickKey = procName + "|" + command.commandType + "|" + command.goal;
            if (!preTickKey.equals(lastPreTickDebugKey)) {
                lastPreTickDebugKey = preTickKey;
                logBaritoneDebug(String.format(
                        "[DBG][PCM][preTick] inControl=%s command=%s goal=%s isPathing=%s hasCurrent=%s hasCalc=%s",
                        procName,
                        command.commandType,
                        command.goal,
                        p.isPathing(),
                        p.getCurrent() != null,
                        p.getInProgress().isPresent()));
            }
        }
        if (command == null) {
            lastPreTickDebugKey = null;
            lastPostTickDebugKey = null;
            p.cancelSegmentIfSafe();
            p.secretInternalSetGoal(null);
            return;
        }
        if (!Objects.equals(inControlThisTick, inControlLastTick)
                && command.commandType != PathingCommandType.REQUEST_PAUSE && inControlLastTick != null
                && !inControlLastTick.isTemporary()) {
            // if control has changed from a real process to another real process, and the
            // new process wants to do something
            p.cancelSegmentIfSafe();
            // get rid of the in progress stuff from the last process
        }
        switch (command.commandType) {
            case SET_GOAL_AND_PAUSE:
                p.secretInternalSetGoalAndPath(command);
            case REQUEST_PAUSE:
                p.requestPause();
                break;
            case CANCEL_AND_SET_GOAL:
                p.secretInternalSetGoal(command.goal);
                p.cancelSegmentIfSafe();
                break;
            case FORCE_REVALIDATE_GOAL_AND_PATH:
            case REVALIDATE_GOAL_AND_PATH:
                if (!p.isPathing() && !p.getInProgress().isPresent()) {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            case SET_GOAL_AND_PATH:
                // now this i can do
                if (command.goal != null) {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            default:
                throw new IllegalStateException();
        }
    }

    private void postTick() {
        // if we did this in pretick, it would suck
        // we use the time between ticks as calculation time
        // therefore, we only cancel and recalculate after the tick for the current path
        // has executed
        // "it would suck" means it would actually execute a path every other tick
        if (command == null) {
            return;
        }
        PathingBehavior p = baritone.getPathingBehavior();
        String postTickKey = command.commandType + "|" + command.goal;
        if (!postTickKey.equals(lastPostTickDebugKey)) {
            lastPostTickDebugKey = postTickKey;
            logBaritoneDebug(String.format(
                    "[DBG][PCM][postTick] command=%s goal=%s isPathing=%s hasCurrent=%s hasCalc=%s",
                    command.commandType,
                    command.goal,
                    p.isPathing(),
                    p.getCurrent() != null,
                    p.getInProgress().isPresent()));
        }
        switch (command.commandType) {
            case FORCE_REVALIDATE_GOAL_AND_PATH:
                if (command.goal == null || forceRevalidate(command.goal) || revalidateGoal(command.goal)) {
                    // pwnage
                    p.softCancelIfSafe();
                }
                p.secretInternalSetGoalAndPath(command);
                break;
            case REVALIDATE_GOAL_AND_PATH:
                if (Baritone.settings().cancelOnGoalInvalidation.value
                        && (command.goal == null || revalidateGoal(command.goal))) {
                    p.softCancelIfSafe();
                }
                p.secretInternalSetGoalAndPath(command);
                break;
            default:
        }
    }

    public boolean forceRevalidate(Goal newGoal) {
        PathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null) {
            if (newGoal.isInGoal(current.getPath().getDest())) {
                return false;
            }
            return !newGoal.equals(current.getPath().getGoal());
        }
        return false;
    }

    public boolean revalidateGoal(Goal newGoal) {
        PathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null) {
            Goal intended = current.getPath().getGoal();
            BlockPos end = current.getPath().getDest();
            if (intended.isInGoal(end) && !newGoal.isInGoal(end)) {
                // this path used to end in the goal
                // but the goal has changed, so there's no reason to continue...
                return true;
            }
        }
        return false;
    }

    public PathingCommand executeProcesses() {
        for (IBaritoneProcess process : processes) {
            if (process.isActive()) {
                if (!active.contains(process)) {
                    // put a newly active process at the very front of the queue
                    active.add(0, process);
                }
            } else {
                active.remove(process);
            }
        }
        // ties are broken by which was added to the beginning of the list first
        active.sort(Comparator.comparingDouble(IBaritoneProcess::priority).reversed());

        Iterator<IBaritoneProcess> iterator = active.iterator();
        while (iterator.hasNext()) {
            IBaritoneProcess proc = iterator.next();

            PathingCommand exec = proc.onTick(
                    Objects.equals(proc, inControlLastTick) && baritone.getPathingBehavior().calcFailedLastTick(),
                    baritone.getPathingBehavior().isSafeToCancel());
            if (exec == null) {
                if (proc.isActive()) {
                    throw new IllegalStateException(proc.displayName() + " actively returned null PathingCommand");
                }
                // no need to call onLostControl; they are reporting inactive.
            } else if (exec.commandType != PathingCommandType.DEFER) {
                exec = normalizePathingCommand(exec);
                inControlThisTick = proc;
                if (!proc.isTemporary()) {
                    iterator.forEachRemaining(IBaritoneProcess::onLostControl);
                }
                return exec;
            }
        }
        return null;
    }

    private PathingCommand normalizePathingCommand(PathingCommand command) {
        if (command == null || command.goal == null) {
            return command;
        }
        Goal normalizedGoal = GoalTargetNormalizer.normalize(baritone, command.goal);
        if (normalizedGoal == command.goal) {
            return command;
        }
        if (command instanceof PathingCommandContext) {
            PathingCommandContext contextCommand = (PathingCommandContext) command;
            return new PathingCommandContext(normalizedGoal, command.commandType, contextCommand.desiredCalcContext);
        }
        if (command instanceof PathingCommandPath) {
            PathingCommandPath pathCommand = (PathingCommandPath) command;
            return new PathingCommandPath(normalizedGoal, command.commandType, pathCommand.desiredPath);
        }
        return new PathingCommand(normalizedGoal, command.commandType);
    }
}

