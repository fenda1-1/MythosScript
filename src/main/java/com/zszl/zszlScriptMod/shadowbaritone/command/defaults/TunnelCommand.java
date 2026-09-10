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
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalStrictDirection;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class TunnelCommand extends Command {

    public TunnelCommand(IBaritone baritone) {
        super(baritone, "tunnel");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(3);
        if (args.hasExactly(3)) {
            boolean cont = true;
            int height = Integer.parseInt(args.getArgs().get(0).getValue());
            int width = Integer.parseInt(args.getArgs().get(1).getValue());
            int depth = Integer.parseInt(args.getArgs().get(2).getValue());

            if (width < 1 || height < 2 || depth < 1 || height > 255) {
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.tunnel.error.invalid_dimensions"));
                cont = false;
            }

            if (cont) {
                height--;
                width--;
                BlockPos corner1;
                BlockPos corner2;
                EnumFacing enumFacing = ctx.player().getHorizontalFacing();
                int addition = ((width % 2 == 0) ? 0 : 1);
                switch (enumFacing) {
                    case EAST:
                        corner1 = new BlockPos(ctx.playerFeet().x, ctx.playerFeet().y, ctx.playerFeet().z - width / 2);
                        corner2 = new BlockPos(ctx.playerFeet().x + depth, ctx.playerFeet().y + height,
                                ctx.playerFeet().z + width / 2 + addition);
                        break;
                    case WEST:
                        corner1 = new BlockPos(ctx.playerFeet().x, ctx.playerFeet().y,
                                ctx.playerFeet().z + width / 2 + addition);
                        corner2 = new BlockPos(ctx.playerFeet().x - depth, ctx.playerFeet().y + height,
                                ctx.playerFeet().z - width / 2);
                        break;
                    case NORTH:
                        corner1 = new BlockPos(ctx.playerFeet().x - width / 2, ctx.playerFeet().y, ctx.playerFeet().z);
                        corner2 = new BlockPos(ctx.playerFeet().x + width / 2 + addition, ctx.playerFeet().y + height,
                                ctx.playerFeet().z - depth);
                        break;
                    case SOUTH:
                        corner1 = new BlockPos(ctx.playerFeet().x + width / 2 + addition, ctx.playerFeet().y,
                                ctx.playerFeet().z);
                        corner2 = new BlockPos(ctx.playerFeet().x - width / 2, ctx.playerFeet().y + height,
                                ctx.playerFeet().z + depth);
                        break;
                    default:
                        throw new IllegalStateException(ShadowBaritoneI18n.trKey(
                                "shadowbaritone.command.tunnel.error.unexpected_direction",
                                enumFacing));
                }
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.tunnel.status.creating",
                        height + 1, width + 1, depth));
                baritone.getBuilderProcess().clearArea(corner1, corner2);
            }
        } else {
            Goal goal = new GoalStrictDirection(
                    ctx.playerFeet(),
                    ctx.player().getHorizontalFacing());
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.tunnel.status.goal",
                    goal.toString()));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.tunnel.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.tunnel.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.tunnel.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.tunnel.long_desc.example.default"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.tunnel.long_desc.example.custom"));
    }
}