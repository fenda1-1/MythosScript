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

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.FreecamFeatureHandler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.ChatEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.SprintStateEvent;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.LookBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 8/1/2018
 */
@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    // RenderPlayer hides the local model when it is not the view entity unless isUser is false.
    @Inject(method = "isUser", at = @At("HEAD"), cancellable = true)
    private void zszl$freecamShowBody(CallbackInfoReturnable<Boolean> ci) {
        if (FreecamFeatureHandler.isBody((EntityPlayerSP) (Object) this)) {
            ci.setReturnValue(false);
        }
    }

    // Vanilla otherwise stops sending walking updates when another entity owns the camera.
    @Inject(method = "isCurrentViewEntity", at = @At("HEAD"), cancellable = true)
    private void zszl$freecamKeepBodyUpdates(CallbackInfoReturnable<Boolean> ci) {
        if (FreecamFeatureHandler.isBody((EntityPlayerSP) (Object) this)) {
            ci.setReturnValue(true);
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void sendChatMessage(String msg, CallbackInfo ci) {
        ChatEvent event = new ChatEvent(msg);
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this);
        if (baritone == null) {
            return;
        }
        baritone.getGameEventHandler().onSendChatMessage(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "FIELD", target = "net/minecraft/entity/player/PlayerCapabilities.allowFlying:Z"))
    private boolean isAllowFlying(PlayerCapabilities capabilities) {
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this);
        if (baritone == null) {
            return capabilities.allowFlying;
        }
        return capabilities.allowFlying
                && (!baritone.getPathingBehavior().isPathing() || Baritone.settings().allowFlightPathing.value);
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "net/minecraft/client/settings/KeyBinding.isKeyDown()Z"))
    private boolean isKeyDown(KeyBinding keyBinding) {
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this);
        if (baritone == null) {
            return keyBinding.isKeyDown();
        }
        SprintStateEvent event = new SprintStateEvent();
        baritone.getGameEventHandler().onPlayerSprintState(event);
        if (event.getState() != null) {
            return event.getState();
        }
        if (baritone != BaritoneAPI.getProvider().getPrimaryBaritone()) {
            // hitting control shouldn't make all bots sprint
            return false;
        }
        return keyBinding.isKeyDown();
    }

    @Inject(method = "updateRidden", at = @At(value = "HEAD"))
    private void updateRidden(CallbackInfo cb) {
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this);
        if (baritone != null) {
            ((LookBehavior) baritone.getLookBehavior()).pig();
        }
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "net/minecraft/item/ItemElytra.isUsable(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean isElytraUsable(ItemStack stack) {
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this);
        if (baritone != null && baritone.getPathingBehavior().isPathing()) {
            return false;
        }
        return ItemElytra.isUsable(stack);
    }
}
