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

package com.zszl.zszlScriptMod.shadowbaritone.behavior;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.IWaypoint;
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.Waypoint;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.BlockInteractEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.Set;

import static com.zszl.zszlScriptMod.shadowbaritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class WaypointBehavior extends Behavior {

    public WaypointBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        if (!Baritone.settings().doBedWaypoints.value)
            return;
        if (event.getType() == BlockInteractEvent.Type.USE) {
            BetterBlockPos pos = BetterBlockPos.from(event.getPos());
            IBlockState state = BlockStateInterface.get(ctx, pos);
            if (state.getBlock() instanceof BlockBed) {
                if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                    pos = pos.offset(state.getValue(BlockBed.FACING));
                }
                Set<IWaypoint> waypoints = baritone.getWorldProvider().getCurrentWorld().getWaypoints()
                        .getByTag(IWaypoint.Tag.BED);
                boolean exists = waypoints.stream().map(IWaypoint::getLocation).filter(pos::equals).findFirst()
                        .isPresent();
                if (!exists) {
                    baritone.getWorldProvider().getCurrentWorld().getWaypoints()
                            .addWaypoint(new Waypoint("bed", Waypoint.Tag.BED, pos));
                }
            }
        }
    }

    @Override
    public void onPlayerDeath() {
        if (!Baritone.settings().doDeathWaypoints.value)
            return;
        Waypoint deathWaypoint = new Waypoint("death", Waypoint.Tag.DEATH, ctx.playerFeet());
        baritone.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(deathWaypoint);
        ITextComponent component = new TextComponentString(ShadowBaritoneI18n.trKey(
                "shadowbaritone.waypoint.death.saved"));
        component.getStyle()
                .setColor(TextFormatting.WHITE)
                .setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(ShadowBaritoneI18n.trKey(
                                "shadowbaritone.waypoint.death.hover.goto"))))
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        String.format(
                                "%s%s goto %s @ %d",
                                FORCE_COMMAND_PREFIX,
                                "wp",
                                deathWaypoint.getTag().getName(),
                                deathWaypoint.getCreationTimestamp())));
        Helper.HELPER.logDirect(component);
    }

}