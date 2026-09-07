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
import com.zszl.zszlScriptMod.shadowbaritone.api.behavior.IPathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPathingControlManager;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IBaritoneProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ETACommand extends Command {

    public ETACommand(IBaritone baritone) {
        super(baritone, "eta");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IPathingControlManager pathingControlManager = baritone.getPathingControlManager();
        IBaritoneProcess process = pathingControlManager.mostRecentInControl().orElse(null);
        if (process == null) {
            throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.eta.error.no_process"));
        }
        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();

        double ticksRemainingInSegment = pathingBehavior.ticksRemainingInSegment().orElse(Double.NaN);
        double ticksRemainingInGoal = pathingBehavior.estimatedTicksToGoal().orElse(Double.NaN);

        logDirect(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.eta.status.current",
                ticksRemainingInSegment / 20, // we just assume tps is 20, it isn't worth the effort that is needed to
                                              // calculate it exactly
                ticksRemainingInSegment,
                ticksRemainingInGoal / 20,
                ticksRemainingInGoal));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.eta.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.eta.long_desc.1"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.eta.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.eta.long_desc.3"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.eta.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.eta.long_desc.example.default"));
    }
}