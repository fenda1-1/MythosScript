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
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import net.minecraft.block.BlockAir;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SurfaceCommand extends Command {

    protected SurfaceCommand(IBaritone baritone) {
        super(baritone, "surface", "top");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final BetterBlockPos playerPos = ctx.playerFeet();
        final int surfaceLevel = ctx.world().getSeaLevel();
        final int worldHeight = ctx.world().getActualHeight();

        // Ensure this command will not run if you are above the surface level and the
        // block above you is air
        // As this would imply that your are already on the open surface
        if (playerPos.getY() > surfaceLevel
                && ctx.world().getBlockState(playerPos.up()).getBlock() instanceof BlockAir) {
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.surface.status.already_at_surface"));
            return;
        }

        final int startingYPos = Math.max(playerPos.getY(), surfaceLevel);

        for (int currentIteratedY = startingYPos; currentIteratedY < worldHeight; currentIteratedY++) {
            final BetterBlockPos newPos = new BetterBlockPos(playerPos.getX(), currentIteratedY, playerPos.getZ());

            if (!(ctx.world().getBlockState(newPos).getBlock() instanceof BlockAir)
                    && newPos.getY() > playerPos.getY()) {
                Goal goal = new GoalBlock(newPos.up());
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.surface.status.going_to",
                        goal.toString()));
                baritone.getCustomGoalProcess().setGoalAndPath(goal);
                return;
            }
        }
        logDirect(ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.surface.status.no_higher_location"));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.surface.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.surface.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.surface.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.surface.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.surface.long_desc.example.surface"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.surface.long_desc.example.top"));
    }
}