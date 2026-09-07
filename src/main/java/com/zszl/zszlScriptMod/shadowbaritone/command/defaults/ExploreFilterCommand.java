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
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeFile;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidTypeException;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ExploreFilterCommand extends Command {

    public ExploreFilterCommand(IBaritone baritone) {
        super(baritone, "explorefilter");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        File file = args.getDatatypePost(RelativeFile.INSTANCE,
                ctx.minecraft().mcDataDir.getAbsoluteFile().getParentFile());
        boolean invert = false;
        if (args.hasAny()) {
            if (args.getString().equalsIgnoreCase("invert")) {
                invert = true;
            } else {
                throw new CommandInvalidTypeException(
                        args.consumed(),
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.explorefilter.error.invert_or_nothing"));
            }
        }
        try {
            baritone.getExploreProcess().applyJsonFilter(file.toPath().toAbsolutePath(), invert);
        } catch (NoSuchFileException e) {
            throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.explorefilter.error.file_not_found"));
        } catch (JsonSyntaxException e) {
            throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.explorefilter.error.invalid_json"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        logDirect(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.explorefilter.status.applied",
                Boolean.toString(invert)));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, RelativeFile.gameDir(ctx.minecraft()));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.explorefilter.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.explorefilter.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.explorefilter.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.explorefilter.long_desc.3"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.explorefilter.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.explorefilter.long_desc.example.default"));
    }
}