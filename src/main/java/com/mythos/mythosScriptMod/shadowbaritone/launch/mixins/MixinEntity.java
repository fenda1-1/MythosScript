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

package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.gui.DetachedSwingWindowManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.FreecamFeatureHandler;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "applyEntityCollision", at = @At("HEAD"), cancellable = true, require = 1)
    private void mythos$ignoreEntityPush(Entity other, CallbackInfo ci) {
        if (MovementFeatureManager.hasNoCollision((Entity) (Object) this)
                || MovementFeatureManager.hasNoCollision(other)) {
            ci.cancel();
        }
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void cancelTurnWhileOverlayOpen(float yaw, float pitch, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null) {
            return;
        }

        if (mythosScriptMod.isGuiVisible
                && !DetachedSwingWindowManager.isDetached()
                && mc.currentScreen == null
                && (Object) this == mc.player) {
            ci.cancel();
            return;
        }
        if (FreecamFeatureHandler.turn((Entity) (Object) this, yaw, pitch)) {
            ci.cancel();
        }
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z"))
    private boolean applySafeWalkSneakState(Entity entity) {
        return entity.isSneaking() || MovementFeatureManager.shouldApplyVanillaSafeWalk(entity);
    }
}
