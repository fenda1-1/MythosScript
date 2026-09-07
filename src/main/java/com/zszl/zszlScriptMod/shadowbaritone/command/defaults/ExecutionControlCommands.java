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
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IBaritoneProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Contains the pause, resume, and paused commands.
 * <p>
 * This thing is scoped to hell, private so far you can't even access it using
 * reflection, because you AREN'T SUPPOSED
 * TO USE THIS to pause and resume Baritone. Make your own process that returns
 * {@link PathingCommandType#REQUEST_PAUSE
 * REQUEST_PAUSE} as needed.
 */
public class ExecutionControlCommands {

    Command pauseCommand;
    Command resumeCommand;
    Command pausedCommand;
    Command cancelCommand;

    public ExecutionControlCommands(IBaritone baritone) {
        // array for mutability, non-field so reflection can't touch it
        final boolean[] paused = { false };
        baritone.getPathingControlManager().registerProcess(
                new IBaritoneProcess() {
                    @Override
                    public boolean isActive() {
                        return paused[0];
                    }

                    @Override
                    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
                        baritone.getInputOverrideHandler().clearAllKeys();
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }

                    @Override
                    public boolean isTemporary() {
                        return true;
                    }

                    @Override
                    public void onLostControl() {
                        paused[0] = false;
                    }

                    @Override
                    public double priority() {
                        return DEFAULT_PRIORITY + 1;
                    }

                    @Override
                    public String displayName0() {
                        return "Pause/Resume Commands";
                    }
                });
        pauseCommand = new Command(baritone, "pause", "p", "paws") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                if (paused[0]) {
                    throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.pause.error.already_paused"));
                }
                paused[0] = true;
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.pause.status.paused"));
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.pause.short_desc");
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.pause.long_desc.1"),
                        "",
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.pause.long_desc.2"),
                        "",
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.pause.long_desc.usage"),
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.pause.long_desc.example.default"));
            }
        };
        resumeCommand = new Command(baritone, "resume", "r", "unpause", "unpaws") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                baritone.getBuilderProcess().resume();
                if (!paused[0]) {
                    throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.resume.error.not_paused"));
                }
                paused[0] = false;
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.resume.status.resumed"));
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.resume.short_desc");
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.resume.long_desc.1"),
                        "",
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.resume.long_desc.usage"),
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.resume.long_desc.example.default"));
            }
        };
        pausedCommand = new Command(baritone, "paused") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.paused.status.current_state",
                        paused[0] ? "" : ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.paused.value.not_paused_prefix")));
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.paused.short_desc");
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.paused.long_desc.1"),
                        "",
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.paused.long_desc.usage"),
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.paused.long_desc.example.default"));
            }
        };
        cancelCommand = new Command(baritone, "cancel", "c", "stop") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                if (paused[0]) {
                    paused[0] = false;
                }
                // A direct-flight movement may be midair, but stop must still clear
                // its executor and rendered route immediately.
                baritone.getPathingBehavior().forceCancel();
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.cancel.status.canceled"));
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.cancel.short_desc");
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.cancel.long_desc.1"),
                        "",
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.cancel.long_desc.usage"),
                        ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.cancel.long_desc.example.default"));
            }
        };
    }
}
