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

package com.zszl.zszlScriptMod.shadowbaritone.utils.player;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.IPlayerController;
import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;

/**
 * Implementation of {@link IPlayerController} that chains to the primary player
 * controller's methods
 *
 * @author Brady
 * @since 12/14/2018
 */
public final class BaritonePlayerController implements IPlayerController {

    private final Minecraft mc;

    public BaritonePlayerController(Minecraft mc) {
        this.mc = mc;
    }

    @Override
    public void syncHeldItem() {
        try {
            Method method = ObfuscationReflectionHelper.findMethod(
                    PlayerControllerMP.class,
                    "func_78750_j",
                    Void.TYPE);
            method.invoke(mc.playerController);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sync held item via reflection", e);
        }
    }

    @Override
    public boolean hasBrokenBlock() {
        try {
            BlockPos current = ReflectionCompat.getPrivateValue(
                    PlayerControllerMP.class,
                    mc.playerController,
                    "field_178895_c");
            return current != null && current.getY() == -1;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read current block via reflection", e);
        }
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, EnumFacing side) {
        return mc.playerController.onPlayerDamageBlock(pos, side);
    }

    @Override
    public void resetBlockRemoving() {
        mc.playerController.resetBlockRemoving();
    }

    @Override
    public ItemStack windowClick(int windowId, int slotId, int mouseButton, ClickType type, EntityPlayer player) {
        return mc.playerController.windowClick(windowId, slotId, mouseButton, type, player);
    }

    @Override
    public GameType getGameType() {
        return mc.playerController.getCurrentGameType();
    }

    @Override
    public EnumActionResult processRightClickBlock(EntityPlayerSP player, World world, BlockPos pos,
            EnumFacing direction, Vec3d vec, EnumHand hand) {
        return mc.playerController.processRightClickBlock(player, (WorldClient) world, pos, direction, vec, hand);
    }

    @Override
    public EnumActionResult processRightClick(EntityPlayerSP player, World world, EnumHand hand) {
        return mc.playerController.processRightClick(player, world, hand);
    }

    @Override
    public boolean clickBlock(BlockPos loc, EnumFacing face) {
        return mc.playerController.clickBlock(loc, face);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        try {
            ReflectionCompat.setPrivateValue(
                    PlayerControllerMP.class,
                    mc.playerController,
                    hittingBlock,
                    "field_78778_j");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to set hitting block via reflection", e);
        }
    }
}
