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

package com.zszl.zszlScriptMod.shadowbaritone.command.defaults;

import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.ForBlockOptionalMeta;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeCoordinate;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeGoal;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BlockOptionalMeta;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GoalTargetNormalizer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GotoCommand extends Command {

    protected GotoCommand(IBaritone baritone) {
        super(baritone, "goto");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // If we have a numeric first argument, then parse arguments as coordinates.
        // Note: There is no reason to want to go where you're already at so there
        // is no need to handle the case of empty arguments.
        if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
            args.requireMax(3);
            boolean explicitXYZ = args.hasExactly(3);
            BetterBlockPos origin = ctx.playerFeet();
            Goal rawGoal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            if (explicitXYZ && rawGoal instanceof GoalBlock) {
                rawGoal = new GoalFlightExact(((GoalBlock) rawGoal).getGoalPos());
            }
            Goal goal = GoalTargetNormalizer.normalize(baritone, rawGoal);
            resumeIfPaused();
            logPathingSnapshot("before-goto-coord", goal);
            if (goal != rawGoal) {
                logGotoDebug(String.format("[normalize] adjusted coordinate goal from %s to %s", rawGoal, goal));
            }
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.goto.status.going_to",
                    goal.toString()));
            baritone.getCustomGoalProcess().setGoal(goal);
            baritone.getCustomGoalProcess().path();
            logPathingSnapshot("after-goto-coord", goal);
            return;
        }
        args.requireMax(1);
        resumeIfPaused();
        BlockOptionalMeta destination = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        logPathingSnapshot("before-goto-block", null);
        baritone.getGetToBlockProcess().getToBlock(destination);
        logPathingSnapshot("after-goto-block", null);
    }

    private void resumeIfPaused() {
        boolean pausedByPauseCommand = baritone.getPathingControlManager().mostRecentCommand()
                .map(command -> command.commandType == PathingCommandType.REQUEST_PAUSE)
                .orElse(false)
                && baritone.getPathingControlManager().mostRecentInControl()
                        .map(process -> "Pause/Resume Commands".equals(process.displayName()))
                        .orElse(false);
        if (pausedByPauseCommand) {
            logGotoDebug("Detected paused state from Pause/Resume Commands, auto resume now");
            baritone.getCommandManager().execute("resume");
        }
    }

    private void logPathingSnapshot(String stage, Goal goal) {
        String inControl = baritone.getPathingControlManager().mostRecentInControl()
                .map(proc -> {
                    try {
                        return proc.displayName();
                    } catch (Throwable ignored) {
                        return proc.getClass().getSimpleName();
                    }
                })
                .orElse("<none>");
        String lastCommand = baritone.getPathingControlManager().mostRecentCommand()
                .map(cmd -> cmd.commandType + " " + cmd.goal)
                .orElse("<none>");
        boolean isPathing = baritone.getPathingBehavior().isPathing();
        boolean hasCurrent = baritone.getPathingBehavior().getCurrent() != null;
        boolean hasInProgress = baritone.getPathingBehavior().getInProgress().isPresent();
        Goal activeGoal = baritone.getPathingBehavior().getGoal();
        logGotoDebug(String.format(
                "[%s] inputGoal=%s activeGoal=%s inControl=%s lastCommand=%s isPathing=%s hasCurrentPath=%s hasCalc=%s",
                stage,
                goal,
                activeGoal,
                inControl,
                lastCommand,
                isPathing,
                hasCurrent,
                hasInProgress));
    }

    private void logGotoDebug(String message) {
        if (ModConfig.isDebugFlagEnabled(DebugModule.BARITONE)) {
            logDirect("[DBG][goto] " + message);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        // since it's either a goal or a block, I don't think we can tab complete
        // properly?
        // so just tab complete for the block variant
        args.requireMax(1);
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.goto.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.example.block"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.example.y"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.example.xz"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goto.long_desc.example.xyz"));
    }
}
