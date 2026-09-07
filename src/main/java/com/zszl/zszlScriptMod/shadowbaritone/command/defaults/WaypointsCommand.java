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
 * along with Baritone.  If not, see https://www.gnu.org/licenses/.
 */

package com.zszl.zszlScriptMod.shadowbaritone.command.defaults;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.IWaypoint;
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.IWorldData;
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.Waypoint;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.ForWaypoints;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidTypeException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.Paginator;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.TabCompleteHelper;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.zszl.zszlScriptMod.shadowbaritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class WaypointsCommand extends Command {

    private Map<IWorldData, List<IWaypoint>> deletedWaypoints = new HashMap<>();

    public WaypointsCommand(IBaritone baritone) {
        super(baritone, "waypoints", "waypoint", "wp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = args.hasAny() ? Action.getByName(args.getString()) : Action.LIST;
        if (action == null) {
            throw new CommandInvalidTypeException(args.consumed(), ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.waypoints.error.action"));
        }
        BiFunction<IWaypoint, Action, ITextComponent> toComponent = (waypoint, _action) -> {
            ITextComponent component = new TextComponentString("");
            ITextComponent tagComponent = new TextComponentString(waypoint.getTag().name() + " ");
            tagComponent.getStyle().setColor(TextFormatting.GRAY);
            String name = waypoint.getName();
            ITextComponent nameComponent = new TextComponentString(!name.isEmpty()
                    ? name
                    : ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.value.empty_name"));
            nameComponent.getStyle().setColor(!name.isEmpty() ? TextFormatting.GRAY : TextFormatting.DARK_GRAY);
            ITextComponent timestamp = new TextComponentString(" @ " + new Date(waypoint.getCreationTimestamp()));
            timestamp.getStyle().setColor(TextFormatting.DARK_GRAY);
            component.appendSibling(tagComponent);
            component.appendSibling(nameComponent);
            component.appendSibling(timestamp);
            component.getStyle()
                    .setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new TextComponentString(ShadowBaritoneI18n.trKey(
                                    "shadowbaritone.command.waypoints.hover.select"))))
                    .setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s %s %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    _action.names[0],
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp())));
            return component;
        };
        Function<IWaypoint, ITextComponent> transform = waypoint -> toComponent.apply(waypoint,
                action == Action.LIST ? Action.INFO : action);
        if (action == Action.LIST) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
                args.get();
            }
            IWaypoint[] waypoints = tag != null
                    ? ForWaypoints.getWaypointsByTag(this.baritone, tag)
                    : ForWaypoints.getWaypoints(this.baritone);
            if (waypoints.length > 0) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect(
                                tag != null
                                        ? ShadowBaritoneI18n.trKey(
                                                "shadowbaritone.command.waypoints.header.by_tag",
                                                tag.name())
                                        : ShadowBaritoneI18n.trKey(
                                                "shadowbaritone.command.waypoints.header.all")),
                        transform,
                        String.format(
                                "%s%s %s%s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                tag != null ? " " + tag.getName() : ""));
            } else {
                args.requireMax(0);
                throw new CommandInvalidStateException(
                        tag != null
                                ? ShadowBaritoneI18n.trKey(
                                        "shadowbaritone.command.waypoints.error.no_waypoints_by_tag")
                                : ShadowBaritoneI18n.trKey(
                                        "shadowbaritone.command.waypoints.error.no_waypoints"));
            }
        } else if (action == Action.SAVE) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag == null) {
                tag = IWaypoint.Tag.USER;
            } else {
                args.get();
            }
            String name = (args.hasExactlyOne() || args.hasExactly(4)) ? args.getString() : "";
            BetterBlockPos pos = args.hasAny()
                    ? args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet())
                    : ctx.playerFeet();
            args.requireMax(0);
            IWaypoint waypoint = new Waypoint(name, tag, pos);
            ForWaypoints.waypoints(this.baritone).addWaypoint(waypoint);
            ITextComponent component = new TextComponentString(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.waypoints.status.added"));
            component.getStyle().setColor(TextFormatting.GRAY);
            component.appendSibling(toComponent.apply(waypoint, Action.INFO));
            logDirect(component);
        } else if (action == Action.CLEAR) {
            args.requireMax(1);
            String name = args.getString();
            IWaypoint.Tag tag = IWaypoint.Tag.getByName(name);
            if (tag == null) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.error.invalid_tag",
                        name));
            }
            IWaypoint[] waypoints = ForWaypoints.getWaypointsByTag(this.baritone, tag);
            for (IWaypoint waypoint : waypoints) {
                ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
            }
            deletedWaypoints.computeIfAbsent(baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>())
                    .addAll(Arrays.<IWaypoint>asList(waypoints));
            ITextComponent textComponent = new TextComponentString(
                    ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.status.cleared_restore",
                            waypoints.length));
            textComponent.getStyle().setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    String.format(
                            "%s%s restore @ %s",
                            FORCE_COMMAND_PREFIX,
                            label,
                            Stream.of(waypoints).map(wp -> Long.toString(wp.getCreationTimestamp()))
                                    .collect(Collectors.joining(" ")))));
            logDirect(textComponent);
        } else if (action == Action.RESTORE) {
            List<IWaypoint> waypoints = new ArrayList<>();
            List<IWaypoint> deletedWaypoints = this.deletedWaypoints
                    .getOrDefault(baritone.getWorldProvider().getCurrentWorld(), Collections.emptyList());
            if (args.peekString().equals("@")) {
                args.get();
                while (args.hasAny()) {
                    long timestamp = args.getAs(Long.class);
                    for (IWaypoint waypoint : deletedWaypoints) {
                        if (waypoint.getCreationTimestamp() == timestamp) {
                            waypoints.add(waypoint);
                            break;
                        }
                    }
                }
            } else {
                args.requireExactly(1);
                int size = deletedWaypoints.size();
                int amount = Math.min(size, args.getAs(Integer.class));
                waypoints = new ArrayList<>(deletedWaypoints.subList(size - amount, size));
            }
            waypoints.forEach(ForWaypoints.waypoints(this.baritone)::addWaypoint);
            deletedWaypoints.removeIf(waypoints::contains);
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.waypoints.status.restored",
                    waypoints.size()));
        } else {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            if (args.hasAny() && args.peekString().equals("@")) {
                args.requireExactly(2);
                args.get();
                long timestamp = args.getAs(Long.class);
                for (IWaypoint iWaypoint : waypoints) {
                    if (iWaypoint.getCreationTimestamp() == timestamp) {
                        waypoint = iWaypoint;
                        break;
                    }
                }
                if (waypoint == null) {
                    throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.error.timestamp_not_found"));
                }
            } else {
                switch (waypoints.length) {
                    case 0:
                        throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.waypoints.error.no_waypoints"));
                    case 1:
                        waypoint = waypoints[0];
                        break;
                    default:
                        break;
                }
            }
            if (waypoint == null) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect(ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.waypoints.header.multiple_found")),
                        transform,
                        String.format(
                                "%s%s %s %s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                args.consumedString()));
            } else {
                if (action == Action.INFO) {
                    logDirect(transform.apply(waypoint));
                    logDirect(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.status.position",
                            waypoint.getLocation()));
                    ITextComponent deleteComponent = new TextComponentString(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.action.delete"));
                    deleteComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s delete %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp())));
                    ITextComponent goalComponent = new TextComponentString(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.action.goal"));
                    goalComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s goal %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp())));
                    ITextComponent recreateComponent = new TextComponentString(
                            ShadowBaritoneI18n.trKey(
                                    "shadowbaritone.command.waypoints.action.recreate"));
                    recreateComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            String.format(
                                    "%s%s save %s %s %s %s %s",
                                    Baritone.settings().prefix.value,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getName(),
                                    waypoint.getLocation().x,
                                    waypoint.getLocation().y,
                                    waypoint.getLocation().z)));
                    ITextComponent backComponent = new TextComponentString(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.action.back"));
                    backComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s list",
                                    FORCE_COMMAND_PREFIX,
                                    label)));
                    logDirect(deleteComponent);
                    logDirect(goalComponent);
                    logDirect(recreateComponent);
                    logDirect(backComponent);
                } else if (action == Action.DELETE) {
                    ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
                    deletedWaypoints
                            .computeIfAbsent(baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>())
                            .add(waypoint);
                    ITextComponent textComponent = new TextComponentString(
                            ShadowBaritoneI18n.trKey(
                                    "shadowbaritone.command.waypoints.status.deleted_restore"));
                    textComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s restore @ %s",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getCreationTimestamp())));
                    logDirect(textComponent);
                } else if (action == Action.GOAL) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoal(goal);
                    logDirect(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.status.goal",
                            goal));
                } else if (action == Action.GOTO) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoalAndPath(goal);
                    logDirect(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.waypoints.status.going_to",
                            goal));
                }
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            if (args.hasExactlyOne()) {
                return new TabCompleteHelper()
                        .append(Action.getAllNames())
                        .sortAlphabetically()
                        .filterPrefix(args.getString())
                        .stream();
            } else {
                Action action = Action.getByName(args.getString());
                if (args.hasExactlyOne()) {
                    if (action == Action.LIST || action == Action.SAVE || action == Action.CLEAR) {
                        return new TabCompleteHelper()
                                .append(IWaypoint.Tag.getAllNames())
                                .sortAlphabetically()
                                .filterPrefix(args.getString())
                                .stream();
                    } else if (action == Action.RESTORE) {
                        return Stream.empty();
                    } else {
                        return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
                    }
                } else if (args.has(3) && action == Action.SAVE) {
                    args.get();
                    args.get();
                    return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.waypoints.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.3"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.4"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.list"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.list_tag"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.save_default"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.save"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.info"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.delete"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.restore"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.clear"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.goal"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.waypoints.long_desc.example.goto"));
    }

    private enum Action {
        LIST("list", "get", "l"),
        CLEAR("clear", "c"),
        SAVE("save", "s"),
        INFO("info", "show", "i"),
        DELETE("delete", "d"),
        RESTORE("restore"),
        GOAL("goal", "g"),
        GOTO("goto");

        private final String[] names;

        Action(String... names) {
            this.names = names;
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(Arrays.asList(action.names));
            }
            return names.toArray(new String[0]);
        }
    }
}