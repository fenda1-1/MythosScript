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
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.IWaypoint;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.ForWaypoints;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FarmCommand extends Command {

    public FarmCommand(IBaritone baritone) {
        super(baritone, "farm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        int range = 0;
        BetterBlockPos origin = null;
        // range
        if (args.has(1)) {
            range = args.getAs(Integer.class);
        }
        // waypoint
        if (args.has(1)) {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            switch (waypoints.length) {
                case 0:
                    throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.farm.error.no_waypoints"));
                case 1:
                    waypoint = waypoints[0];
                    break;
                default:
                    throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.farm.error.multiple_waypoints"));
            }
            origin = waypoint.getLocation();
        }

        baritone.getFarmProcess().farm(range, origin);
        logDirect(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.farm.status.farming"));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.farm.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.farm.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.farm.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.farm.long_desc.example.default"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.farm.long_desc.example.range"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.farm.long_desc.example.waypoint"));
    }
}