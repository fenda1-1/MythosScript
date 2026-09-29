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

package com.mythos.mythosScriptMod.shadowbaritone.api.command.datatypes;

import com.mythos.mythosScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.mythos.mythosScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.stream.Stream;

public enum RelativeGoalBlock implements IDatatypePost<GoalBlock, BetterBlockPos> {
    INSTANCE;

    @Override
    public GoalBlock apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();
        double x = consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.x);
        double y = consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.y);
        double z = consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.z);
        // A fractional coordinate is a standing pose, not a block index.
        // Flooring 8.7 to 8 puts a fence-edge goal inside the wall.
        if (x != Math.rint(x) || y != Math.rint(y) || z != Math.rint(z)) {
            return new GoalBlock(x, y, z);
        }
        return new GoalBlock(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAtMost(3)) {
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
