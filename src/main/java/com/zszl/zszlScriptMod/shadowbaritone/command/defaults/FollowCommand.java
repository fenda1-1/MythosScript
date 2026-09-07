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

import com.zszl.zszlScriptMod.shadowbaritone.KeepName;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.EntityClassById;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.IDatatypeFor;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.NearbyPlayer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandErrorMessageException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.TabCompleteHelper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FollowCommand extends Command {

    public FollowCommand(IBaritone baritone) {
        super(baritone, "follow");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        FollowGroup group;
        FollowList list;
        List<Entity> entities = new ArrayList<>();
        List<Class<? extends Entity>> classes = new ArrayList<>();
        if (args.hasExactlyOne()) {
            baritone.getFollowProcess().follow((group = args.getEnum(FollowGroup.class)).filter);
        } else {
            args.requireMin(2);
            group = null;
            list = args.getEnum(FollowList.class);
            while (args.hasAny()) {
                Object gotten = args.getDatatypeFor(list.datatype);
                if (gotten instanceof Class) {
                    // noinspection unchecked
                    classes.add((Class<? extends Entity>) gotten);
                } else if (gotten != null) {
                    entities.add((Entity) gotten);
                }
            }
            baritone.getFollowProcess().follow(
                    classes.isEmpty()
                            ? entities::contains
                            : e -> classes.stream().anyMatch(c -> c.isInstance(e)));
        }
        if (group != null) {
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.follow.status.following_all",
                    getFollowGroupDisplayName(group)));
        } else {
            if (classes.isEmpty()) {
                if (entities.isEmpty())
                    throw new NoEntitiesException();
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.status.following_entities"));
                entities.stream()
                        .map(Entity::toString)
                        .forEach(this::logDirect);
            } else {
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.status.following_entity_types"));
                classes.stream()
                        .map(EntityList::getKey)
                        .map(Objects::requireNonNull)
                        .map(ResourceLocation::toString)
                        .forEach(this::logDirect);
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(FollowGroup.class)
                    .append(FollowList.class)
                    .filterPrefix(args.getString())
                    .stream();
        } else {
            IDatatypeFor followType;
            try {
                followType = args.getEnum(FollowList.class).datatype;
            } catch (NullPointerException e) {
                return Stream.empty();
            }
            while (args.has(2)) {
                if (args.peekDatatypeOrNull(followType) == null) {
                    return Stream.empty();
                }
                args.get();
            }
            return args.tabCompleteDatatype(followType);
        }
    }

    private String getFollowGroupDisplayName(FollowGroup group) {
        switch (group) {
            case ENTITIES:
                return ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.value.group.entities");
            case PLAYERS:
                return ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.value.group.players");
            default:
                return group.name().toLowerCase(Locale.US);
        }
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.follow.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.long_desc.example.entities"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.long_desc.example.entity"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.long_desc.example.players"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.follow.long_desc.example.player"));
    }

    @KeepName
    private enum FollowGroup {
        ENTITIES(EntityLiving.class::isInstance),
        PLAYERS(EntityPlayer.class::isInstance); /*
                                                  * ,
                                                  * FRIENDLY(entity -> entity.getAttackTarget() != HELPER.mc.player),
                                                  * HOSTILE(FRIENDLY.filter.negate());
                                                  */

        final Predicate<Entity> filter;

        FollowGroup(Predicate<Entity> filter) {
            this.filter = filter;
        }
    }

    @KeepName
    private enum FollowList {
        ENTITY(EntityClassById.INSTANCE),
        PLAYER(NearbyPlayer.INSTANCE);

        final IDatatypeFor datatype;

        FollowList(IDatatypeFor datatype) {
            this.datatype = datatype;
        }
    }

    public static class NoEntitiesException extends CommandErrorMessageException {

        protected NoEntitiesException() {
            super(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.follow.error.no_valid_entities"));
        }

    }
}