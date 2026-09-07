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

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.TabCompleteHelper;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.ICustomGoalProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IElytraProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.zszl.zszlScriptMod.shadowbaritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class ElytraCommand extends Command {

    public ElytraCommand(IBaritone baritone) {
        super(baritone, "elytra");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
        final IElytraProcess elytra = baritone.getElytraProcess();
        if (args.hasExactlyOne() && args.peekString().equals("supported")) {
            logDirect(elytra.isLoaded()
                    ? ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.elytra.value.supported_yes")
                    : unsupportedSystemMessage());
            return;
        }
        if (!elytra.isLoaded()) {
            throw new CommandInvalidStateException(unsupportedSystemMessage());
        }

        if (!args.hasAny()) {
            if (Baritone.settings().elytraTermsAccepted.value) {
                if (detectOn2b2t()) {
                    warn2b2t();
                }
            } else {
                gatekeep();
            }
            Goal iGoal = customGoalProcess.mostRecentGoal();
            if (iGoal == null) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.error.no_goal"));
            }
            if (ctx.player().dimension != -1) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.error.only_nether"));
            }
            try {
                elytra.pathTo(iGoal);
            } catch (IllegalArgumentException ex) {
                throw new CommandInvalidStateException(ex.getMessage());
            }
            return;
        }

        final String action = args.getString();
        switch (action) {
            case "reset": {
                elytra.resetState();
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.status.reset_same_goal"));
                break;
            }
            case "repack": {
                elytra.repackChunks();
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.status.repack_queued"));
                break;
            }
            default: {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.error.invalid_action"));
            }
        }
    }

    private void warn2b2t() {
        if (Baritone.settings().elytraPredictTerrain.value) {
            long seed = Baritone.settings().elytraNetherSeed.value;
            if (seed != NEW_2B2T_SEED && seed != OLD_2B2T_SEED) {
                logDirect(new TextComponentString(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.warning.2b2t.seed_incorrect")));
                logDirect(suggest2b2tSeeds());
            }
        }
    }

    private ITextComponent suggest2b2tSeeds() {
        TextComponentString clippy = new TextComponentString("");
        clippy.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.warning.2b2t.terrain_intro"));
        clippy.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.warning.2b2t.try_older_seed"));
        TextComponentString olderSeed = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.action.older_seed"));
        olderSeed.getStyle().setUnderlined(true).setBold(true)
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(
                                Baritone.settings().prefix.value + "set elytraNetherSeed " + OLD_2B2T_SEED)))
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        FORCE_COMMAND_PREFIX + "set elytraNetherSeed " + OLD_2B2T_SEED));
        clippy.appendSibling(olderSeed);
        clippy.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.warning.2b2t.try_newer_seed"));
        TextComponentString newerSeed = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.action.newer_seed"));
        newerSeed.getStyle().setUnderlined(true).setBold(true)
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(
                                Baritone.settings().prefix.value + "set elytraNetherSeed " + NEW_2B2T_SEED)))
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        FORCE_COMMAND_PREFIX + "set elytraNetherSeed " + NEW_2B2T_SEED));
        clippy.appendSibling(newerSeed);
        clippy.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.warning.2b2t.terrain_outro"));
        return clippy;
    }

    private void gatekeep() {
        TextComponentString gatekeep = new TextComponentString("");
        gatekeep.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.disable_message"));
        gatekeep.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.experimental"));
        TextComponentString gatekeep2 = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.autojump"));
        gatekeep2.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponentString(Baritone.settings().prefix.value + "set elytraAutoJump true")));
        gatekeep.appendSibling(gatekeep2);
        TextComponentString gatekeep3 = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.conserve_fireworks"));
        gatekeep3.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponentString(Baritone.settings().prefix.value + "set elytraConserveFireworks true\n"
                        + Baritone.settings().prefix.value
                        + "set elytraFireworkSpeed 0.6\n(the 0.6 number is just an example, tweak to your liking)")));
        gatekeep.appendSibling(gatekeep3);
        TextComponentString gatekeep4 = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.seed_title_prefix"));
        TextComponentString red = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.seed_title_highlight"));
        red.getStyle().setColor(TextFormatting.RED).setUnderlined(true).setBold(true);
        gatekeep4.appendSibling(red);
        gatekeep4.appendText(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.gatekeep.seed_title_suffix"));
        gatekeep.appendSibling(gatekeep4);
        gatekeep.appendText("\n");
        if (detectOn2b2t()) {
            TextComponentString gatekeep5 = new TextComponentString(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.elytra.gatekeep.server_detected_2b2t"));
            gatekeep5.appendSibling(suggest2b2tSeeds());
            if (!Baritone.settings().elytraPredictTerrain.value) {
                gatekeep5.appendText(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.gatekeep.predict_disabled",
                        Baritone.settings().prefix.value));
            } else {
                if (Baritone.settings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                    gatekeep5.appendText(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.elytra.gatekeep.using_new_seed"));
                } else if (Baritone.settings().elytraNetherSeed.value == OLD_2B2T_SEED) {
                    gatekeep5.appendText(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.elytra.gatekeep.using_old_seed"));
                } else {
                    gatekeep5.appendText(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.elytra.gatekeep.default_new_seed"));
                    Baritone.settings().elytraNetherSeed.value = NEW_2B2T_SEED;
                }
            }
            gatekeep.appendSibling(gatekeep5);
        } else {
            if (Baritone.settings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                TextComponentString gatekeep5 = new TextComponentString(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.gatekeep.seed_unknown",
                        Baritone.settings().prefix.value));
                gatekeep5.appendText(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.gatekeep.seed_unknown_default"));
                gatekeep.appendSibling(gatekeep5);
                Baritone.settings().elytraPredictTerrain.value = false;
            } else {
                if (Baritone.settings().elytraPredictTerrain.value) {
                    TextComponentString gatekeep5 = new TextComponentString(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.elytra.gatekeep.predicting_with_seed",
                            Baritone.settings().elytraNetherSeed.value,
                            Baritone.settings().prefix.value,
                            Baritone.settings().prefix.value));
                    gatekeep.appendSibling(gatekeep5);
                } else {
                    TextComponentString gatekeep5 = new TextComponentString(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.elytra.gatekeep.not_predicting",
                            Baritone.settings().prefix.value,
                            Baritone.settings().prefix.value));
                    gatekeep.appendSibling(gatekeep5);
                }
            }
        }
        logDirect(gatekeep);
    }

    private boolean detectOn2b2t() {
        ServerData data = ctx.minecraft().getCurrentServerData();
        return data != null && data.serverIP.toLowerCase().contains("2b2t.org");
    }

    private static final long OLD_2B2T_SEED = -4100785268875389365L;
    private static final long NEW_2B2T_SEED = 146008555100680L;

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "repack", "supported");
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.long_desc.example.default"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.long_desc.example.reset"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.long_desc.example.repack"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.elytra.long_desc.example.supported"));
    }

    private static String unsupportedSystemMessage() {
        final String osArch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name");
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.elytra.error.unsupported_system",
                osArch, osName);
    }
}