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

package com.zszl.zszlScriptMod.shadowbaritone.behavior;

import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.behavior.IPathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.*;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.calc.AbstractNodeCostSearch;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.FlightDirectPath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementNarrowGapTraverse;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.PathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathRenderer;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathingCommandContext;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathingCommandPath;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GoalTargetNormalizer;
import com.zszl.zszlScriptMod.shadowbaritone.utils.FlightPathingAutomation;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

public final class PathingBehavior extends Behavior implements IPathingBehavior, Helper {

    private PathExecutor current;
    private PathExecutor next;

    private Goal goal;
    private CalculationContext context;

    /* eta */
    private int ticksElapsedSoFar;
    private BetterBlockPos startPosition;

    private boolean safeToCancel;
    private boolean pauseRequestedLastTick;
    private boolean unpausedLastTick;
    private boolean pausedThisTick;
    private boolean cancelRequested;
    private boolean calcFailedLastTick;

    private volatile AbstractNodeCostSearch inProgress;
    private final Object pathCalcLock = new Object();

    private final Object pathPlanLock = new Object();

    private boolean lastAutoJump;

    private BetterBlockPos expectedSegmentStart;
    private int pathReplanTriggerCount;
    private IPath retainedFlightPath;
    private long flightLifecycleTick;
    private long nextFlightRelayPlanTick;
    private boolean flightWaitingForContinuation;
    private double flightHoldY;
    private FlightDirectPath.PlanStatus flightWaitStatus;
    private int flightRetryDelayTicks = 2;
    private boolean flightRecoveryNeedsLanding;
    private boolean flightTerminalReached;
    private boolean flightTerminalSafeLanding;
    private long pathCalculationGeneration;

    private final LinkedBlockingQueue<PathEvent> toDispatch = new LinkedBlockingQueue<>();

    public PathingBehavior(Baritone baritone) {
        super(baritone);
    }

    private void queuePathEvent(PathEvent event) {
        toDispatch.add(event);
    }

    private void dispatchEvents() {
        ArrayList<PathEvent> curr = new ArrayList<>();
        toDispatch.drainTo(curr);
        calcFailedLastTick |= curr.contains(PathEvent.CALC_FAILED);
        for (PathEvent event : curr) {
            baritone.getGameEventHandler().onPathEvent(event);
        }
    }

    @Override
    public void onTick(TickEvent event) {
        dispatchEvents();
        if (event.getType() == TickEvent.Type.OUT) {
            secretInternalSegmentCancel();
            baritone.getPathingControlManager().cancelEverything();
            secretInternalSetGoal(null);
            context = null;
            return;
        }

        expectedSegmentStart = pathStart();
        baritone.getPathingControlManager().preTick();
        calcFailedLastTick = false;
        tickPath();
        FlightPathingAutomation.onTick(baritone, goal, ctx.playerFeet(), flightTerminalSafeLanding);
        ticksElapsedSoFar++;
        flightLifecycleTick++;
        dispatchEvents();
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        if (isPathing()) {
            event.setState(current.isSprinting());
        }
    }

    private void tickPath() {
        pausedThisTick = false;
        if (pauseRequestedLastTick && safeToCancel) {
            pauseRequestedLastTick = false;
            if (unpausedLastTick) {
                baritone.getInputOverrideHandler().clearAllKeys();
                baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
            }
            unpausedLastTick = false;
            pausedThisTick = true;
            return;
        }
        unpausedLastTick = true;
        if (cancelRequested) {
            cancelRequested = false;
            baritone.getInputOverrideHandler().clearAllKeys();
        }
        synchronized (pathPlanLock) {
            synchronized (pathCalcLock) {
                if (inProgress != null && !flightWaitingForContinuation) {
                    // we are calculating
                    // are we calculating the right thing though? 🤔
                    BetterBlockPos calcFrom = inProgress.getStart();
                    Optional<IPath> currentBest = inProgress.bestPathSoFar();
                    if ((current == null || !current.getPath().getDest().equals(calcFrom)) // if current ends in
                                                                                           // inProgress's start, then
                                                                                           // we're ok
                            && !calcFrom.equals(ctx.playerFeet()) && !calcFrom.equals(expectedSegmentStart) // if
                                                                                                            // current
                                                                                                            // starts in
                                                                                                            // our
                                                                                                            // playerFeet
                                                                                                            // or
                                                                                                            // pathStart,
                                                                                                            // then
                                                                                                            // we're ok
                            && (!currentBest.isPresent() || (!currentBest.get().positions().contains(ctx.playerFeet())
                                    && !currentBest.get().positions().contains(expectedSegmentStart))) // if
                    ) {
                        // when it was *just* started, currentBest will be empty so we need to also
                        // check calcFrom since that's always present
                        inProgress.cancel(); // cancellation doesn't dispatch any events
                    }
                }
            }
            if (current == null) {
                if (goal != null && Baritone.settings().allowFlightPathing.value
                        && Baritone.settings().flightDirectLine.value
                        && !flightWaitingForContinuation && !flightTerminalReached && inProgress == null) {
                    enterFlightContinuationWait(FlightDirectPath.PlanStatus.NO_ROUTE);
                }
                tickFlightContinuationRetry();
                return;
            }
            if (isAerialFlightGoal(goal) && current.getPath() instanceof FlightDirectPath
                    && goal.isInGoal(ctx.playerFeet())) {
                baritone.getInputOverrideHandler().clearAllKeys();
                current = null;
                next = null;
                flightTerminalReached = true;
                flightTerminalSafeLanding = FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet());
                flightWaitingForContinuation = false;
                flightWaitStatus = null;
                if (flightTerminalSafeLanding) {
                    FlyHandler.INSTANCE.clearPathingHoldAltitude();
                } else {
                    flightHoldY = ctx.player().posY;
                    FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
                }
                queuePathEvent(PathEvent.AT_GOAL);
                return;
            }
            safeToCancel = current.onTick();
            if (current.failed() || current.finished()) {
                FlightDirectPath completedFlightPath = current.getPath() instanceof FlightDirectPath
                        ? (FlightDirectPath) current.getPath() : null;
                boolean currentPathWasFlight = completedFlightPath != null;
                boolean currentPathFailed = current.failed();
                current = null;
                if (!currentPathFailed && completedFlightPath != null && completedFlightPath.isTerminalSegment()) {
                    if (isAerialFlightGoal(goal) && !goal.isInGoal(ctx.playerFeet())) {
                        enterFlightContinuationWait(FlightDirectPath.PlanStatus.WAITING_FOR_CHUNKS);
                        next = null;
                        return;
                    }
                    flightTerminalReached = true;
                    flightTerminalSafeLanding = FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet())
                            && (completedFlightPath.isSafeLandingTerminal() || goal != null
                                    && goal.isInGoal(ctx.playerFeet()));
                    flightWaitingForContinuation = false;
                    next = null;
                    if (isAerialFlightGoal(goal) && goal.isInGoal(ctx.playerFeet())) {
                        flightHoldY = ctx.player().posY;
                        FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
                        queuePathEvent(PathEvent.AT_GOAL);
                    } else if (!flightTerminalSafeLanding) {
                        flightHoldY = ctx.player().posY;
                        FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
                    } else {
                        queuePathEvent(PathEvent.AT_GOAL);
                    }
                    return;
                }
                if (goal == null) {
                    logDebug("All done. At " + goal);
                    queuePathEvent(PathEvent.AT_GOAL);
                    next = null;
                    if (Baritone.settings().disconnectOnArrival.value) {
                        ModUtils.disconnectFromCurrentWorld();
                    }
                    return;
                }
                if (goal.isInGoal(ctx.playerFeet())) {
                    if (!currentPathWasFlight || isAerialFlightGoal(goal)
                            || FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet())) {
                        logDebug("All done. At " + goal);
                        queuePathEvent(PathEvent.AT_GOAL);
                        next = null;
                        return;
                    }
                    flightTerminalReached = true;
                    flightTerminalSafeLanding = false;
                    flightHoldY = ctx.player().posY;
                    FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
                    next = null;
                    return;
                }
                if (next != null && (currentPathFailed || (!next.getPath().positions().contains(ctx.playerFeet())
                        && !next.getPath().positions().contains(expectedSegmentStart)
                        && !(currentPathWasFlight && next.getPath() instanceof FlightDirectPath)))) { // can contain either one
                    // if the current path failed, we may not actually be on the next one, so make
                    // sure
                    logDebug("Discarding next path as it does not contain current position");
                    // for example if we had a nicely planned ahead path that starts where current
                    // ends
                    // that's all fine and good
                    // but if we fail in the middle of current
                    // we're nowhere close to our planned ahead path
                    // so need to discard it sadly.
                    queuePathEvent(PathEvent.DISCARD_NEXT);
                    next = null;
                }
                if (next != null) {
                    logDebug("Continuing on to planned next path");
                    queuePathEvent(PathEvent.CONTINUING_ONTO_PLANNED_NEXT);
                    current = next;
                    next = null;
                    flightWaitingForContinuation = false;
                    current.onTick(); // don't waste a tick doing nothing, get started right away
                    return;
                }
                // at this point, current just ended, but we aren't in the goal and have no plan
                // for the future
                synchronized (pathCalcLock) {
                    if (inProgress != null) {
                        if (currentPathWasFlight) {
                            enterFlightContinuationWait(FlightDirectPath.PlanStatus.NO_ROUTE);
                        }
                        queuePathEvent(PathEvent.PATH_FINISHED_NEXT_STILL_CALCULATING);
                        return;
                    }
                    if (currentPathWasFlight && Baritone.settings().allowFlightPathing.value
                            && Baritone.settings().flightDirectLine.value) {
                        enterFlightContinuationWait(FlightDirectPath.PlanStatus.NO_ROUTE);
                        if (!flightRecoveryNeedsLanding) {
                            tryInstallFlightContinuation(ctx.playerFeet());
                        }
                        return;
                    }
                    // we aren't calculating
                    pathReplanTriggerCount++;
                    queuePathEvent(PathEvent.CALC_STARTED);
                    findPathInNewThread(expectedSegmentStart, true, context);
                }
                return;
            }
            // at this point, we know current is in progress
            if (safeToCancel && next != null && next.snipsnapifpossible()) {
                // a movement just ended; jump directly onto the next path
                logDebug("Splicing into planned next path early...");
                queuePathEvent(PathEvent.SPLICING_ONTO_NEXT_EARLY);
                current = next;
                next = null;
                current.onTick();
                return;
            }
            if (Baritone.settings().splicePath.value && !(current.getPath() instanceof FlightDirectPath)) {
                current = current.trySplice(next);
            }
            if (next != null && current.getPath().getDest().equals(next.getPath().getDest())) {
                next = null;
            }
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    // if we aren't calculating right now
                    return;
                }
                if (goal == null || goal.isInGoal(current.getPath().getDest())) {
                    // and this path doesn't get us all the way there
                    return;
                }
                if (Baritone.settings().allowFlightPathing.value
                        && Baritone.settings().flightDirectLine.value
                        && current.getPath() instanceof FlightDirectPath) {
                    FlightDirectPath flightPath = (FlightDirectPath) current.getPath();
                    BetterBlockPos relayStart = flightPath.getDest();
                    double dx = ctx.player().posX - (relayStart.x + 0.5D);
                    double dy = ctx.player().posY - relayStart.y;
                    double dz = ctx.player().posZ - (relayStart.z + 0.5D);
                    double distanceToRelay = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double planningDistance = Math.max(48.0D,
                            FlyHandler.horizontalSpeed * Baritone.settings().planningTickLookahead.value);
                    if (!flightPath.isFinalSegment() && distanceToRelay <= planningDistance
                            && flightLifecycleTick >= nextFlightRelayPlanTick) {
                        FlightDirectPath.PlanResult relayResult = FlightDirectPath.plan(baritone, goal, relayStart);
                        IPath relay = relayResult.getPath();
                        boolean improvesNext = relayResult.isSuccess() && relay.getSrc().equals(relayStart)
                                && goal.heuristic(relay.getDest()) + 1.0D < goal.heuristic(relayStart)
                                && (next == null || goal.heuristic(relay.getDest()) + 1.0D
                                        < goal.heuristic(next.getPath().getDest()));
                        if (improvesNext) {
                            next = new PathExecutor(this, relay);
                            retainedFlightPath = relay;
                            flightWaitingForContinuation = false;
                            flightWaitStatus = null;
                            flightRecoveryNeedsLanding = false;
                            nextFlightRelayPlanTick = flightLifecycleTick + 10L;
                            queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_FINISHED);
                        } else {
                            FlightDirectPath.PlanStatus effectiveStatus = relayResult.isSuccess()
                                    ? FlightDirectPath.PlanStatus.NO_ROUTE : relayResult.getStatus();
                            if (next == null && (effectiveStatus == FlightDirectPath.PlanStatus.NO_ROUTE
                                    || effectiveStatus == FlightDirectPath.PlanStatus.UNSUPPORTED_GOAL)) {
                                startFlightRecoveryLocked(relayStart);
                            }
                            nextFlightRelayPlanTick = flightLifecycleTick + 4L;
                        }
                    }
                    // Flight uses only custom relay planning. Never start ground A*.
                    return;
                }
                if (next != null) {
                    // and we have no plan for what to do next
                    return;
                }
                if (ticksRemainingInSegment(false).get() < Baritone.settings().planningTickLookahead.value) {
                    // and this path has 7.5 seconds or less left
                    // don't include the current movement so a very long last movement (e.g.
                    // descend) doesn't trip it up
                    // if we actually included current, it wouldn't start planning ahead until the
                    // last movement was done, if the last movement took more than 7.5 seconds on
                    // its own
                    logDebug("Path almost over. Planning ahead...");
                    queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_STARTED);
                    findPathInNewThread(current.getPath().getDest(), false, context);
                }
            }
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (current != null) {
            switch (event.getState()) {
                case PRE:
                    lastAutoJump = ctx.minecraft().gameSettings.autoJump;
                    ctx.minecraft().gameSettings.autoJump = false;
                    break;
                case POST:
                    ctx.minecraft().gameSettings.autoJump = lastAutoJump;
                    break;
                default:
                    break;
            }
        }
    }

    public void secretInternalSetGoal(Goal goal) {
        Goal normalized = GoalTargetNormalizer.normalize(baritone, goal);
        if (normalized == null || !Objects.equals(this.goal, normalized)) {
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    inProgress.cancel();
                }
                pathCalculationGeneration++;
            }
            retainedFlightPath = null;
            flightWaitingForContinuation = false;
            flightWaitStatus = null;
            flightRetryDelayTicks = 2;
            flightRecoveryNeedsLanding = false;
            flightTerminalReached = false;
            flightTerminalSafeLanding = false;
            nextFlightRelayPlanTick = flightLifecycleTick;
            FlyHandler.INSTANCE.clearPathingHoldAltitude();
        }
        this.goal = normalized;
    }

    public boolean secretInternalSetGoalAndPath(PathingCommand command) {
        secretInternalSetGoal(command.goal);
        FlightPathingAutomation.onPathRequested(goal);
        pathReplanTriggerCount = 0;
        if (command instanceof PathingCommandContext) {
            context = ((PathingCommandContext) command).desiredCalcContext;
        } else {
            context = new CalculationContext(baritone, true);
        }
        if (command instanceof PathingCommandPath) {
            return secretInternalSetCustomPath(((PathingCommandPath) command).desiredPath);
        }
        if (goal == null) {
            return false;
        }
        if (flightTerminalReached) {
            return true;
        }
        if ((goal.isInGoal(ctx.playerFeet()) || goal.isInGoal(expectedSegmentStart))
                && (!Baritone.settings().allowFlightPathing.value
                        || isAerialFlightGoal(goal)
                        || FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet()))) {
            return false;
        }
        if (Baritone.settings().allowFlightPathing.value && Baritone.settings().flightDirectLine.value) {
            if (current != null && current.getPath() instanceof FlightDirectPath) {
                return true;
            }
            if (flightWaitingForContinuation) {
                return true;
            }
            FlightDirectPath.PlanResult directResult = FlightDirectPath.plan(baritone, goal, expectedSegmentStart);
            if (directResult.isSuccess() && isUsefulFlightPath(directResult.getPath(), expectedSegmentStart)) {
                return secretInternalSetCustomPath(directResult.getPath());
            }
            enterFlightContinuationWait(directResult.isSuccess()
                    ? FlightDirectPath.PlanStatus.NO_ROUTE : directResult.getStatus());
            return true;
        }
        synchronized (pathPlanLock) {
            if (current != null) {
                return false;
            }
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    return false;
                }
                queuePathEvent(PathEvent.CALC_STARTED);
                findPathInNewThread(expectedSegmentStart, true, context);
                return true;
            }
        }
    }

    private boolean secretInternalSetCustomPath(IPath desiredPath) {
        if (goal == null || desiredPath == null || desiredPath.length() < 2) {
            return false;
        }
        synchronized (pathPlanLock) {
            boolean desiredFlightPath = desiredPath instanceof FlightDirectPath;
            if (current == null || desiredFlightPath && !(current.getPath() instanceof FlightDirectPath)) {
                if (!customPathCanStartNow(desiredPath)) {
                    return false;
                }
            }
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    inProgress.cancel();
                    inProgress = null;
                    pathCalculationGeneration++;
                }
            }
            if (desiredFlightPath && current != null && !(current.getPath() instanceof FlightDirectPath)) {
                current = null;
                next = null;
                baritone.getInputOverrideHandler().clearAllKeys();
            }
            if (current == null) {
                current = new PathExecutor(this, desiredPath);
                next = null;
                if (desiredFlightPath) {
                    retainedFlightPath = desiredPath;
                }
                flightWaitingForContinuation = false;
                nextFlightRelayPlanTick = flightLifecycleTick;
                FlyHandler.INSTANCE.clearPathingHoldAltitude();
                queuePathEvent(PathEvent.CALC_FINISHED_NOW_EXECUTING);
                resetEstimatedTicksToGoal(desiredPath.getSrc());
                return true;
            }
            if (pathsEquivalent(current.getPath(), desiredPath)) {
                return true;
            }
            if (next != null && pathsEquivalent(next.getPath(), desiredPath)) {
                return true;
            }
            if (!current.getPath().getDest().equals(desiredPath.getSrc())) {
                return false;
            }
            next = new PathExecutor(this, desiredPath);
            if (desiredFlightPath) {
                retainedFlightPath = desiredPath;
            }
            queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_FINISHED);
            return true;
        }
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    @Override
    public boolean isPathing() {
        return hasPath() && !pausedThisTick;
    }

    @Override
    public PathExecutor getCurrent() {
        return current;
    }

    @Override
    public PathExecutor getNext() {
        return next;
    }

    public IPath getRetainedFlightPath() {
        return retainedFlightPath;
    }

    public boolean isFlightWaitingForContinuation() {
        return flightWaitingForContinuation && goal != null;
    }

    @Override
    public boolean isFlightTerminalReached() {
        return flightTerminalReached;
    }

    @Override
    public boolean isFlightTerminalSafeLanding() {
        return flightTerminalReached && flightTerminalSafeLanding;
    }

    @Override
    public Optional<AbstractNodeCostSearch> getInProgress() {
        return Optional.ofNullable(inProgress);
    }

    public boolean isSafeToCancel() {
        if (current == null) {
            return !baritone.getElytraProcess().isActive() || baritone.getElytraProcess().isSafeToCancel();
        }
        return safeToCancel;
    }

    public void requestPause() {
        pauseRequestedLastTick = true;
    }

    public boolean cancelSegmentIfSafe() {
        if (isSafeToCancel()) {
            secretInternalSegmentCancel();
            return true;
        }
        return false;
    }

    @Override
    public boolean cancelEverything() {
        boolean doIt = isSafeToCancel();
        if (doIt) {
            secretInternalSegmentCancel();
        }
        synchronized (pathCalcLock) {
            if (inProgress != null) {
                inProgress.cancel();
                inProgress = null;
                pathCalculationGeneration++;
            }
        }
        synchronized (pathPlanLock) {
            next = null;
        }
        // Clear the retained render goal immediately so GUI-triggered stop behaves the
        // same as a manual "stop/cancel" command even if a pause process had been active.
        secretInternalSetGoal(null);
        context = null;
        baritone.getPathingControlManager().cancelEverything(); // regardless of if we can stop the current segment, we
                                                                // can still stop the processes
        return doIt;
    }

    public boolean calcFailedLastTick() { // NOT exposed on public api
        return calcFailedLastTick;
    }

    public int getPathReplanTriggerCount() {
        return pathReplanTriggerCount;
    }

    public void softCancelIfSafe() {
        synchronized (pathPlanLock) {
            getInProgress().ifPresent(AbstractNodeCostSearch::cancel); // only cancel ours
            if (!isSafeToCancel()) {
                return;
            }
            current = null;
            next = null;
            pathCalculationGeneration++;
            flightWaitingForContinuation = false;
            FlyHandler.INSTANCE.clearPathingHoldAltitude();
        }
        cancelRequested = true;
        // do everything BUT clear keys
    }

    // just cancel the current path
    public void secretInternalSegmentCancel() {
        queuePathEvent(PathEvent.CANCELED);
        synchronized (pathPlanLock) {
            getInProgress().ifPresent(AbstractNodeCostSearch::cancel);
            pathCalculationGeneration++;
            flightWaitingForContinuation = false;
            FlyHandler.INSTANCE.clearPathingHoldAltitude();
            boolean hadPath = current != null || next != null;
            current = null;
            next = null;
            if (hadPath) {
                baritone.getInputOverrideHandler().clearAllKeys();
                baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
            }
        }
    }

    @Override
    public void forceCancel() { // exposed on public api because :sob:
        cancelEverything();
        secretInternalSegmentCancel();
        synchronized (pathCalcLock) {
            inProgress = null;
            pathCalculationGeneration++;
        }
    }

    public CalculationContext secretInternalGetCalculationContext() {
        return context;
    }

    public Optional<Double> estimatedTicksToGoal() {
        BetterBlockPos currentPos = ctx.playerFeet();
        if (goal == null || currentPos == null || startPosition == null) {
            return Optional.empty();
        }
        if (goal.isInGoal(ctx.playerFeet())) {
            resetEstimatedTicksToGoal();
            return Optional.of(0.0);
        }
        if (ticksElapsedSoFar == 0) {
            return Optional.empty();
        }
        double current = goal.heuristic(currentPos.x, currentPos.y, currentPos.z);
        double start = goal.heuristic(startPosition.x, startPosition.y, startPosition.z);
        if (current == start) {// can't check above because current and start can be equal even if currentPos
                               // and startPosition are not
            return Optional.empty();
        }
        double eta = Math.abs(current - goal.heuristic()) * ticksElapsedSoFar / Math.abs(start - current);
        return Optional.of(eta);
    }

    private void resetEstimatedTicksToGoal() {
        resetEstimatedTicksToGoal(expectedSegmentStart);
    }

    private void resetEstimatedTicksToGoal(BlockPos start) {
        resetEstimatedTicksToGoal(new BetterBlockPos(start));
    }

    private void resetEstimatedTicksToGoal(BetterBlockPos start) {
        ticksElapsedSoFar = 0;
        startPosition = start;
    }

    private void enterFlightContinuationWait(FlightDirectPath.PlanStatus status) {
        if (!flightWaitingForContinuation) {
            flightWaitingForContinuation = true;
            flightHoldY = ctx.player().posY;
            baritone.getInputOverrideHandler().clearAllKeys();
        }
        flightWaitStatus = status;
        nextFlightRelayPlanTick = Math.max(nextFlightRelayPlanTick, flightLifecycleTick + 2L);
        FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
    }

    private void tickFlightContinuationRetry() {
        if (flightTerminalReached) {
            if (!flightTerminalSafeLanding) {
                FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
            }
            return;
        }
        if (!flightWaitingForContinuation || goal == null) {
            flightWaitingForContinuation = false;
            FlyHandler.INSTANCE.clearPathingHoldAltitude();
            return;
        }
        if (goal.isInGoal(ctx.playerFeet()) && (isAerialFlightGoal(goal)
                || FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet()))) {
            flightTerminalReached = true;
            flightTerminalSafeLanding = FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet());
            flightWaitingForContinuation = false;
            if (flightTerminalSafeLanding) {
                FlyHandler.INSTANCE.clearPathingHoldAltitude();
            } else {
                flightHoldY = ctx.player().posY;
                FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
            }
            queuePathEvent(PathEvent.AT_GOAL);
            return;
        }
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_BACK, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_LEFT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_RIGHT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
        FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
        if (inProgress != null || flightLifecycleTick < nextFlightRelayPlanTick) {
            return;
        }
        if (flightRecoveryNeedsLanding) {
            if (!Baritone.settings().flightAutoDescend.value) {
                finishFlightAtClosestReachable(false);
                return;
            }
            FlightDirectPath.PlanResult landing = FlightDirectPath.planNearestSafeLanding(baritone, goal,
                    ctx.playerFeet());
            flightRecoveryNeedsLanding = false;
            if (landing.isSuccess() && installFlightPathNow(landing.getPath())) {
                return;
            }
            finishFlightAtClosestReachable(false);
            return;
        }
        if (flightWaitStatus == FlightDirectPath.PlanStatus.NO_ROUTE
                || flightWaitStatus == FlightDirectPath.PlanStatus.UNSUPPORTED_GOAL) {
            synchronized (pathCalcLock) {
                startFlightRecoveryLocked(ctx.playerFeet());
            }
            return;
        }
        tryInstallFlightContinuation(ctx.playerFeet());
    }

    private boolean tryInstallFlightContinuation(BetterBlockPos start) {
        if (goal == null || start == null) {
            flightWaitingForContinuation = false;
            FlyHandler.INSTANCE.clearPathingHoldAltitude();
            return false;
        }
        FlightPathingAutomation.onPathRequested(goal);
        FlightDirectPath.PlanResult result = FlightDirectPath.plan(baritone, goal, start);
        if (result.isSuccess() && isUsefulFlightPath(result.getPath(), start)
                && installFlightPathNow(result.getPath())) {
            return true;
        }
        FlightDirectPath.PlanStatus effectiveStatus = result.isSuccess()
                ? FlightDirectPath.PlanStatus.NO_ROUTE : result.getStatus();
        if (effectiveStatus == FlightDirectPath.PlanStatus.NO_ROUTE
                || effectiveStatus == FlightDirectPath.PlanStatus.UNSUPPORTED_GOAL) {
            flightWaitStatus = effectiveStatus;
            synchronized (pathCalcLock) {
                startFlightRecoveryLocked(start);
            }
        } else {
            scheduleFlightRetry(effectiveStatus);
        }
        return false;
    }

    private boolean installFlightPathNow(FlightDirectPath path) {
        if (!isUsefulFlightPath(path, ctx.playerFeet()) || !customPathCanStartNow(path)) {
            return false;
        }
        current = new PathExecutor(this, path);
        next = null;
        retainedFlightPath = path;
        flightWaitingForContinuation = false;
        flightWaitStatus = null;
        flightRetryDelayTicks = 2;
        flightRecoveryNeedsLanding = false;
        flightTerminalReached = false;
        flightTerminalSafeLanding = false;
        FlyHandler.INSTANCE.clearPathingHoldAltitude();
        nextFlightRelayPlanTick = flightLifecycleTick;
        queuePathEvent(PathEvent.CALC_FINISHED_NOW_EXECUTING);
        return true;
    }

    private void scheduleFlightRetry(FlightDirectPath.PlanStatus status) {
        flightWaitingForContinuation = true;
        flightWaitStatus = status;
        flightRetryDelayTicks = Math.min(100, Math.max(2, flightRetryDelayTicks * 2));
        nextFlightRelayPlanTick = flightLifecycleTick + flightRetryDelayTicks;
    }

    private void finishFlightAtClosestReachable(boolean safeLanding) {
        flightTerminalReached = true;
        flightTerminalSafeLanding = safeLanding && FlightDirectPath.isSafeLanding(baritone, ctx.playerFeet());
        flightWaitingForContinuation = false;
        flightWaitStatus = null;
        flightRecoveryNeedsLanding = false;
        next = null;
        if (!flightTerminalSafeLanding) {
            flightHoldY = ctx.player().posY;
            FlyHandler.INSTANCE.setPathingHoldAltitude(flightHoldY);
        } else {
            FlyHandler.INSTANCE.clearPathingHoldAltitude();
            queuePathEvent(PathEvent.AT_GOAL);
        }
    }

    private static boolean isAerialFlightGoal(Goal goal) {
        return goal instanceof GoalFlightExact || goal instanceof GoalFlightXZ;
    }

    private void startFlightRecoveryLocked(BetterBlockPos start) {
        if (!Thread.holdsLock(pathCalcLock)) {
            throw new IllegalStateException("Must hold pathCalcLock for flight recovery");
        }
        if (inProgress != null || goal == null || start == null) {
            return;
        }
        CalculationContext recoveryContext = new CalculationContext(baritone, true);
        Goal requestedGoal = goal;
        long generation = ++pathCalculationGeneration;
        AbstractNodeCostSearch pathfinder = createPathfinder(start, requestedGoal, null, recoveryContext);
        inProgress = pathfinder;
        flightWaitingForContinuation = true;
        flightWaitStatus = FlightDirectPath.PlanStatus.NO_ROUTE;
        queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_STARTED);
        Baritone.getExecutor().execute(() -> {
            PathCalculationResult result = pathfinder.calculate(
                    Baritone.settings().primaryTimeoutMS.value,
                    Baritone.settings().failureTimeoutMS.value);
            FlightDirectPath recovered;
            try {
                recovered = result.getPath()
                        .map(path -> FlightDirectPath.fromRecoveryPath(baritone, recoveryContext, requestedGoal, path,
                                result.getType() == PathCalculationResult.Type.SUCCESS_TO_GOAL
                                        && requestedGoal.isInGoal(path.getDest())))
                        .orElse(null);
            } catch (Throwable ignored) {
                recovered = null;
            }
            synchronized (pathPlanLock) {
                synchronized (pathCalcLock) {
                    if (generation != pathCalculationGeneration || inProgress != pathfinder
                            || !Objects.equals(goal, requestedGoal)) {
                        if (inProgress == pathfinder) {
                            inProgress = null;
                        }
                        return;
                    }
                    inProgress = null;
                }
                if (recovered != null && isUsefulFlightPath(recovered, start)) {
                    if (current != null && current.getPath() instanceof FlightDirectPath
                            && current.getPath().getDest().equals(start)) {
                        next = new PathExecutor(this, recovered);
                        retainedFlightPath = recovered;
                        flightWaitingForContinuation = false;
                        flightWaitStatus = null;
                        flightRecoveryNeedsLanding = false;
                        queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_FINISHED);
                        return;
                    }
                    if (current == null && customPathCanStartNow(recovered)) {
                        installFlightPathNow(recovered);
                        return;
                    }
                }
                flightWaitingForContinuation = true;
                flightWaitStatus = FlightDirectPath.PlanStatus.NO_ROUTE;
                flightRecoveryNeedsLanding = true;
                nextFlightRelayPlanTick = flightLifecycleTick + 2L;
                queuePathEvent(PathEvent.NEXT_CALC_FAILED);
            }
        });
    }

    private boolean isUsefulFlightPath(IPath path, BetterBlockPos start) {
        if (!(path instanceof FlightDirectPath) || start == null || path.length() < 2) {
            return false;
        }
        FlightDirectPath flightPath = (FlightDirectPath) path;
        if (flightPath.isTerminalSegment()) {
            return true;
        }
        if (flightPath.isFinalSegment()) {
            return goal != null && goal.isInGoal(path.getDest());
        }
        double startHeuristic = goal == null ? Double.NaN : goal.heuristic(start);
        double endHeuristic = goal == null ? Double.NaN : goal.heuristic(path.getDest());
        return Double.isFinite(startHeuristic) && Double.isFinite(endHeuristic)
                && endHeuristic + 1.0D < startHeuristic;
    }

    /**
     * See issue #209
     *
     * @return The starting {@link BlockPos} for a new path
     */
    public BetterBlockPos pathStart() { // TODO move to a helper or util class
        BetterBlockPos feet = ctx.playerFeet();
        // Flight routes begin from the player's actual air position. Ground recovery
        // would otherwise drag an airborne route down to a nearby standing block.
        if (Baritone.settings().allowFlightPathing.value) {
            return feet;
        }
        BetterBlockPos correctedFeet = correctPathStartInsideBlock(feet);
        if (correctedFeet != null) {
            feet = correctedFeet;
        }
        BetterBlockPos narrowGapRecovery = recoverPathStartFromNarrowGap(feet);
        if (narrowGapRecovery != null) {
            return narrowGapRecovery;
        }
        if (!MovementHelper.canWalkOn(ctx, feet.down())) {
            if (ctx.player().onGround) {
                double playerX = ctx.player().posX;
                double playerZ = ctx.player().posZ;
                ArrayList<BetterBlockPos> closest = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        closest.add(new BetterBlockPos(feet.x + dx, feet.y, feet.z + dz));
                    }
                }
                closest.sort(Comparator.comparingDouble(pos -> ((pos.x + 0.5D) - playerX) * ((pos.x + 0.5D) - playerX)
                        + ((pos.z + 0.5D) - playerZ) * ((pos.z + 0.5D) - playerZ)));
                for (int i = 0; i < 4; i++) {
                    BetterBlockPos possibleSupport = closest.get(i);
                    double xDist = Math.abs((possibleSupport.x + 0.5D) - playerX);
                    double zDist = Math.abs((possibleSupport.z + 0.5D) - playerZ);
                    if (xDist > 0.8 && zDist > 0.8) {
                        // can't possibly be sneaking off of this one, we're too far away
                        continue;
                    }
                    if (MovementHelper.canWalkOn(ctx, possibleSupport.down())
                            && MovementHelper.canWalkThrough(ctx, possibleSupport)
                            && MovementHelper.canWalkThrough(ctx, possibleSupport.up())) {
                        // this is plausible
                        // logDebug("Faking path start assuming player is standing off the edge of a
                        // block");
                        return possibleSupport;
                    }
                }

            } else {
                // !onGround
                // we're in the middle of a jump
                if (MovementHelper.canWalkOn(ctx, feet.down().down())) {
                    // logDebug("Faking path start assuming player is midair and falling");
                    return feet.down();
                }
            }
        }
        return feet;
    }

    private BetterBlockPos recoverPathStartFromNarrowGap(BetterBlockPos feet) {
        if (feet == null || ctx.player() == null) {
            return null;
        }
        BetterBlockPos portalPlane = feet.up();
        CalculationContext recoveryContext = new CalculationContext(baritone, false);
        NarrowGapRecoveryCandidate best = null;

        BetterBlockPos north = findRecoverableStandingPos(feet.x, feet.z - 1, feet.y, portalPlane.y);
        BetterBlockPos south = findRecoverableStandingPos(feet.x, feet.z + 1, feet.y, portalPlane.y);
        best = chooseBetterRecoveryCandidate(best,
                buildNarrowGapRecoveryCandidate(recoveryContext, north, south, portalPlane, portalPlane.east()));
        best = chooseBetterRecoveryCandidate(best,
                buildNarrowGapRecoveryCandidate(recoveryContext, north, south, portalPlane.west(), portalPlane));

        BetterBlockPos east = findRecoverableStandingPos(feet.x + 1, feet.z, feet.y, portalPlane.y);
        BetterBlockPos west = findRecoverableStandingPos(feet.x - 1, feet.z, feet.y, portalPlane.y);
        best = chooseBetterRecoveryCandidate(best,
                buildNarrowGapRecoveryCandidate(recoveryContext, west, east, portalPlane, portalPlane.south()));
        best = chooseBetterRecoveryCandidate(best,
                buildNarrowGapRecoveryCandidate(recoveryContext, west, east, portalPlane.north(), portalPlane));

        return best == null ? null : best.pathStart;
    }

    private BetterBlockPos findRecoverableStandingPos(int x, int z, int minY, int maxY) {
        // Resolve the recovery column from the requested feet level upward. A
        // top-down scan can jump over the target floor and select an unrelated
        // platform above it.
        for (int y = minY; y <= maxY; y++) {
            BetterBlockPos candidate = new BetterBlockPos(x, y, z);
            if (!canOccupyRecoveryPosition(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean canOccupyRecoveryPosition(BetterBlockPos pos) {
        return MovementHelper.canWalkThrough(ctx, pos)
                && MovementHelper.canWalkThrough(ctx, pos.up())
                && MovementHelper.canWalkOn(ctx, pos.down());
    }

    private NarrowGapRecoveryCandidate buildNarrowGapRecoveryCandidate(CalculationContext context,
            BetterBlockPos first, BetterBlockPos second, BetterBlockPos barrierA, BetterBlockPos barrierB) {
        if (first == null || second == null) {
            return null;
        }
        double forwardCost = MovementNarrowGapTraverse.cost(context, first.x, first.y, first.z, second.x, second.z,
                barrierA, barrierB);
        double backwardCost = MovementNarrowGapTraverse.cost(context, second.x, second.y, second.z, first.x, first.z,
                barrierA, barrierB);
        if (forwardCost >= ActionCosts.COST_INF && backwardCost >= ActionCosts.COST_INF) {
            return null;
        }
        BetterBlockPos candidateA = scoreRecoveryStart(first);
        BetterBlockPos candidateB = scoreRecoveryStart(second);
        if (candidateA == null) {
            return candidateB == null ? null : new NarrowGapRecoveryCandidate(candidateB,
                    recoveryHeuristic(candidateB));
        }
        if (candidateB == null) {
            return new NarrowGapRecoveryCandidate(candidateA, recoveryHeuristic(candidateA));
        }
        double scoreA = recoveryHeuristic(candidateA);
        double scoreB = recoveryHeuristic(candidateB);
        return scoreA <= scoreB ? new NarrowGapRecoveryCandidate(candidateA, scoreA)
                : new NarrowGapRecoveryCandidate(candidateB, scoreB);
    }

    private BetterBlockPos scoreRecoveryStart(BetterBlockPos candidate) {
        return candidate != null && canOccupyRecoveryPosition(candidate) ? candidate : null;
    }

    private double recoveryHeuristic(BetterBlockPos candidate) {
        if (goal != null) {
            return goal.heuristic(candidate.x, candidate.y, candidate.z);
        }
        double dx = (candidate.x + 0.5D) - ctx.player().posX;
        double dz = (candidate.z + 0.5D) - ctx.player().posZ;
        return dx * dx + dz * dz;
    }

    private static final class NarrowGapRecoveryCandidate {
        private final BetterBlockPos pathStart;
        private final double score;

        private NarrowGapRecoveryCandidate(BetterBlockPos pathStart, double score) {
            this.pathStart = pathStart;
            this.score = score;
        }
    }

    private NarrowGapRecoveryCandidate chooseBetterRecoveryCandidate(NarrowGapRecoveryCandidate currentBest,
            NarrowGapRecoveryCandidate candidate) {
        if (candidate == null) {
            return currentBest;
        }
        if (currentBest == null || candidate.score < currentBest.score) {
            return candidate;
        }
        return currentBest;
    }

    private BetterBlockPos correctPathStartInsideBlock(BetterBlockPos feet) {
        if (feet == null || Baritone.settings().allowBreak.value) {
            return null;
        }
        if (MovementHelper.canWalkThrough(ctx, feet) && MovementHelper.canWalkThrough(ctx, feet.up())) {
            return null;
        }

        BetterBlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double referenceFeetY = feet.y + 0.001D;

        for (int dy = -4; dy <= 4; dy++) {
            BetterBlockPos candidate = new BetterBlockPos(feet.x, feet.y + dy, feet.z);
            if (!MovementHelper.canWalkOn(ctx, candidate.down())) {
                continue;
            }
            if (!MovementHelper.canWalkThrough(ctx, candidate) || !MovementHelper.canWalkThrough(ctx, candidate.up())) {
                continue;
            }

            double distance = Math.abs((candidate.y + 0.001D) - referenceFeetY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private boolean customPathCanStartNow(IPath desiredPath) {
        BetterBlockPos playerFeet = ctx.playerFeet();
        if (playerFeet != null && desiredPath.positions().contains(playerFeet)) {
            return true;
        }
        if (expectedSegmentStart != null && desiredPath.positions().contains(expectedSegmentStart)) {
            return true;
        }
        if (desiredPath.movements().isEmpty()) {
            return false;
        }

        IMovement firstMovement = desiredPath.movements().get(0);
        if (firstMovement instanceof Movement) {
            Movement first = (Movement) firstMovement;
            if (playerFeet != null && first.getValidPositions().contains(playerFeet)) {
                return true;
            }
            if (expectedSegmentStart != null && first.getValidPositions().contains(expectedSegmentStart)) {
                return true;
            }
        }
        if (!(firstMovement instanceof IRoutePointMovement) || ctx.player() == null) {
            return false;
        }

        IRoutePointMovement routeMovement = (IRoutePointMovement) firstMovement;
        if (distanceToRoute(routeMovement, ctx.player().getPositionVector()) <= 1.25D) {
            return true;
        }
        if (expectedSegmentStart != null) {
            Vec3d expectedPos = new Vec3d(expectedSegmentStart.x + 0.5D, expectedSegmentStart.y + 0.5D,
                    expectedSegmentStart.z + 0.5D);
            return distanceToRoute(routeMovement, expectedPos) <= 1.25D;
        }
        return false;
    }

    private boolean pathsEquivalent(IPath left, IPath right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.length() != right.length()) {
            return false;
        }
        return left.positions().equals(right.positions());
    }

    private double distanceToRoute(IRoutePointMovement movement, Vec3d playerPos) {
        Vec3d[] points = movement.getRoutePoints();
        if (points == null || points.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.length - 1; i++) {
            Vec3d nearest = nearestPointOnSegment(playerPos, points[i], points[i + 1]);
            best = Math.min(best, playerPos.distanceTo(nearest));
        }
        return best == Double.POSITIVE_INFINITY ? playerPos.distanceTo(points[0]) : best;
    }

    private Vec3d nearestPointOnSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSq = segment.lengthSquared();
        if (lengthSq <= 1.0E-6D) {
            return start;
        }
        double t = point.subtract(start).dotProduct(segment) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return start.add(segment.scale(t));
    }

    /**
     * In a new thread, pathfind to target blockpos
     *
     * @param start
     * @param talkAboutIt
     */
    private void findPathInNewThread(final BlockPos start, final boolean talkAboutIt, CalculationContext context) {
        // this must be called with synchronization on pathCalcLock!
        // actually, we can check this, muahaha
        if (!Thread.holdsLock(pathCalcLock)) {
            throw new IllegalStateException("Must be called with synchronization on pathCalcLock");
            // why do it this way? it's already indented so much that putting the whole
            // thing in a synchronized(pathCalcLock) was just too much lol
        }
        if (inProgress != null) {
            throw new IllegalStateException("Already doing it"); // should have been checked by caller
        }
        if (!context.safeForThreadedUse) {
            throw new IllegalStateException("Improper context thread safety level");
        }
        Goal goal = this.goal;
        if (goal == null) {
            logDebug("no goal"); // TODO should this be an exception too? definitely should be checked by caller
            return;
        }
        long primaryTimeout;
        long failureTimeout;
        if (current == null) {
            primaryTimeout = Baritone.settings().primaryTimeoutMS.value;
            failureTimeout = Baritone.settings().failureTimeoutMS.value;
        } else {
            primaryTimeout = Baritone.settings().planAheadPrimaryTimeoutMS.value;
            failureTimeout = Baritone.settings().planAheadFailureTimeoutMS.value;
        }
        BetterBlockPos requestedStart = new BetterBlockPos(start);
        Goal requestedGoal = goal;
        long generation = ++pathCalculationGeneration;
        AbstractNodeCostSearch pathfinder = createPathfinder(start, requestedGoal,
                current == null ? null : current.getPath(),
                context);
        if (!Objects.equals(pathfinder.getGoal(), goal)) { // will return the exact same object if simplification didn't
                                                           // happen
            logDebug("Simplifying " + goal.getClass() + " to GoalXZ due to distance");
        }
        inProgress = pathfinder;
        Baritone.getExecutor().execute(() -> {
            if (talkAboutIt) {
                logDebug("Starting to search for path from " + start + " to " + goal);
            }

            PathCalculationResult calcResult = pathfinder.calculate(primaryTimeout, failureTimeout);
            synchronized (pathPlanLock) {
                synchronized (pathCalcLock) {
                    if (generation != pathCalculationGeneration || inProgress != pathfinder
                            || !Objects.equals(this.goal, requestedGoal)) {
                        if (inProgress == pathfinder) {
                            inProgress = null;
                        }
                        return;
                    }
                }
                Optional<PathExecutor> executor = calcResult.getPath()
                        .map(p -> new PathExecutor(PathingBehavior.this, p));
                if (current == null) {
                    if (executor.isPresent()) {
                        if (executor.get().getPath().positions().contains(requestedStart)) {
                            queuePathEvent(PathEvent.CALC_FINISHED_NOW_EXECUTING);
                            current = executor.get();
                            resetEstimatedTicksToGoal(start);
                        } else {
                            logDebug("Warning: discarding orphan path segment with incorrect start");
                        }
                    } else {
                        if (calcResult.getType() != PathCalculationResult.Type.CANCELLATION
                                && calcResult.getType() != PathCalculationResult.Type.EXCEPTION) {
                            // don't dispatch CALC_FAILED on cancellation
                            queuePathEvent(PathEvent.CALC_FAILED);
                        }
                    }
                } else {
                    if (next == null) {
                        if (executor.isPresent()) {
                            if (executor.get().getPath().getSrc().equals(current.getPath().getDest())) {
                                queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_FINISHED);
                                next = executor.get();
                            } else {
                                logDebug("Warning: discarding orphan next segment with incorrect start");
                            }
                        } else {
                            queuePathEvent(PathEvent.NEXT_CALC_FAILED);
                        }
                    } else {
                        // throw new IllegalStateException("I have no idea what to do with this path");
                        // no point in throwing an exception here, and it gets it stuck with inProgress
                        // being not null
                        logDirect(ShadowBaritoneI18n.trKey(
                                "shadowbaritone.pathing.warning.illegal_state_discard_invalid_path"));
                    }
                }
                if (talkAboutIt && current != null && current.getPath() != null) {
                    if (goal.isInGoal(current.getPath().getDest())) {
                        logDebug("Finished finding a path from " + start + " to " + goal + ". "
                                + current.getPath().getNumNodesConsidered() + " nodes considered");
                    } else {
                        logDebug("Found path segment from " + start + " towards " + goal + ". "
                                + current.getPath().getNumNodesConsidered() + " nodes considered");
                    }
                }
                synchronized (pathCalcLock) {
                    if (inProgress == pathfinder) {
                        inProgress = null;
                    }
                }
            }
        });
    }

    private static AbstractNodeCostSearch createPathfinder(BlockPos start, Goal goal, IPath previous,
            CalculationContext context) {
        Goal transformed = goal;
        if (Baritone.settings().simplifyUnloadedYCoord.value && goal instanceof IGoalRenderPos) {
            BlockPos pos = ((IGoalRenderPos) goal).getGoalPos();
            if (!context.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
                transformed = new GoalXZ(pos.getX(), pos.getZ());
            }
        }
        Favoring favoring = new Favoring(
                context.getBaritone().getPlayerContext(),
                new BetterBlockPos(start),
                transformed,
                previous,
                context);
        return new AStarPathFinder(start.getX(), start.getY(), start.getZ(), transformed, favoring, context);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        PathRenderer.render(event, this);
    }
}

