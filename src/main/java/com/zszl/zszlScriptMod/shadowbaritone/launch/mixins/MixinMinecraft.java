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

package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.FreecamFeatureHandler;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.BlockInteractEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.PlayerUpdateEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.TickEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.WorldEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.type.EventState;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GuiPathingPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.BiFunction;

/**
 * @author Brady
 * @since 7/31/2018
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

        @Inject(method = {"clickMouse", "rightClickMouse", "middleClickMouse"}, at = @At("HEAD"), cancellable = true)
        private void zszl$freecamBlockClicks(CallbackInfo ci) {
                if (FreecamFeatureHandler.isActive()) ci.cancel();
        }

        @Inject(method = "sendClickBlockToController", at = @At("HEAD"), cancellable = true)
        private void zszl$freecamBlockMining(boolean leftClick, CallbackInfo ci) {
                if (FreecamFeatureHandler.isActive()) ci.cancel();
        }

        @Unique
        private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;

        @Unique
        private Minecraft zszl$getMinecraft() {
                return (Minecraft) (Object) this;
        }

        @Unique
        private EntityPlayerSP zszl$getPlayer() {
                return zszl$getMinecraft().player;
        }

        @Unique
        private WorldClient zszl$getWorld() {
                return zszl$getMinecraft().world;
        }

        @Inject(method = "init", at = @At("RETURN"))
        private void postInit(CallbackInfo ci) {
                BaritoneAPI.getProvider().getPrimaryBaritone();
        }

        @Inject(method = "runTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "net/minecraft/client/Minecraft.currentScreen:Lnet/minecraft/client/gui/GuiScreen;", ordinal = 0, shift = At.Shift.BEFORE), slice = @Slice(from = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "net/minecraft/client/Minecraft.leftClickCounter:I")))
        private void runTick(CallbackInfo ci) {
                this.tickProvider = TickEvent.createNextProvider();

                for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
                        TickEvent.Type type = baritone.getPlayerContext().player() != null
                                        && baritone.getPlayerContext().world() != null
                                                        ? TickEvent.Type.IN
                                                        : TickEvent.Type.OUT;
                        baritone.getGameEventHandler().onTick(this.tickProvider.apply(EventState.PRE, type));
                }
        }

        @Inject(method = "runTick", at = @At("RETURN"))
        private void postRunTick(CallbackInfo ci) {
                if (this.tickProvider == null) {
                        return;
                }

                for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
                        TickEvent.Type type = baritone.getPlayerContext().player() != null
                                        && baritone.getPlayerContext().world() != null
                                                        ? TickEvent.Type.IN
                                                        : TickEvent.Type.OUT;
                        baritone.getGameEventHandler().onPostTick(this.tickProvider.apply(EventState.POST, type));
                }

                this.tickProvider = null;
        }

        @Inject(method = "runTick", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/WorldClient.updateEntities()V", shift = At.Shift.AFTER))
        private void postUpdateEntities(CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(zszl$getPlayer());
                if (baritone != null) {
                        // Intentionally call this after all entities have been updated. That way, any
                        // modification to rotations
                        // can be recognized by other entity code. (Fireworks and Pigs, for example)
                        baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
                }
        }

        @Inject(method = "runTick", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/WorldClient.updateEntities()V", shift = At.Shift.BEFORE))
        private void preUpdateEntities(CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(zszl$getPlayer());
                if (baritone != null) {
                        baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
                }
        }

        @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
        private void preLoadWorld(WorldClient world, String loadingMessage, CallbackInfo ci) {
                // If we're unloading the world but one doesn't exist, ignore it
                if (zszl$getWorld() == null && world == null) {
                        return;
                }

                // mc.world changing is only the primary baritone

                BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                                new WorldEvent(
                                                world,
                                                EventState.PRE));
        }

        @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("RETURN"))
        private void postLoadWorld(WorldClient world, String loadingMessage, CallbackInfo ci) {
                // still fire event for both null, as that means we've just finished exiting a
                // world

                // mc.world changing is only the primary baritone
                BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                                new WorldEvent(
                                                world,
                                                EventState.POST));
        }

        @Redirect(method = "runTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "net/minecraft/client/gui/GuiScreen.allowUserInput:Z"))
        private boolean isAllowUserInput(GuiScreen screen) {
                return GuiPathingPolicy.shouldKeepPathingDuringGui(zszl$getMinecraft())
                                || MovementFeatureManager.shouldAllowMovementDuringGui(zszl$getMinecraft())
                                || screen.allowUserInput;
        }

        @Inject(method = "clickMouse", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/PlayerControllerMP.clickBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"), locals = LocalCapture.CAPTURE_FAILHARD)
        private void onBlockBreak(CallbackInfo ci, BlockPos pos) {
                // clickMouse is only for the main player
                BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler()
                                .onBlockInteract(new BlockInteractEvent(pos, BlockInteractEvent.Type.START_BREAK));
        }

        @Inject(method = "rightClickMouse", at = @At(value = "INVOKE", target = "net/minecraft/client/entity/EntityPlayerSP.swingArm(Lnet/minecraft/util/EnumHand;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
        private void onBlockUse(CallbackInfo ci, EnumHand var1[], int var2, int var3, EnumHand enumhand,
                        ItemStack itemstack, BlockPos blockpos, int i, EnumActionResult enumactionresult) {
                // rightClickMouse is only for the main player
                BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler()
                                .onBlockInteract(new BlockInteractEvent(blockpos, BlockInteractEvent.Type.USE));
        }
}
