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

import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeCoordinate;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeGoal;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.TabCompleteHelper;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.ICustomGoalProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GoalCommand extends Command {

    public GoalCommand(IBaritone baritone) {
        super(baritone, "goal");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        ICustomGoalProcess goalProcess = baritone.getCustomGoalProcess();
        if (args.hasAny() && Arrays.asList("reset", "clear", "none").contains(args.peekString())) {
            args.requireMax(1);
            if (goalProcess.getGoal() != null) {
                goalProcess.setGoal(null);
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.status.cleared"));
            } else {
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.status.no_goal_to_clear"));
            }
        } else {
            args.requireMax(3);
            BetterBlockPos origin = ctx.playerFeet();
            Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            goalProcess.setGoal(goal);
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.goal.status.goal",
                    goal.toString()));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "clear", "none", "~");
        } else {
            if (args.hasAtMost(3)) {
                while (args.has(2)) {
                    if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
                        break;
                    }
                    args.get();
                    if (!args.has(2)) {
                        helper.append("~");
                    }
                }
            }
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.goal.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.example.default"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.example.clear"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.example.y"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.example.xz"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.goal.long_desc.example.xyz"));
    }
}