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

import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHelper.class)
public class MixinMouseHelper {

    @Shadow(remap = false)
    public int field_74377_a;

    @Shadow(remap = false)
    public int field_74375_b;

    @Inject(method = "mouseXYChange", at = @At("RETURN"))
    private void freezeLookWhileOverlayOpen(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        // 主界面是覆盖层，不会进入 currentScreen；这里清空本帧鼠标位移来阻止视角转动。
        if (zszlScriptMod.isGuiVisible && !DetachedSwingWindowManager.isDetached()
                && mc.currentScreen == null) {
            this.field_74377_a = 0;
            this.field_74375_b = 0;
        }
    }

    @Inject(method = "grabMouseCursor", at = @At("HEAD"), cancellable = true)
    private void keepMouseDetachedDuringForcedGrab(CallbackInfo ci) {
        if (!ModConfig.isMouseDetached) {
            return;
        }

        this.field_74377_a = 0;
        this.field_74375_b = 0;
        Mouse.setGrabbed(false);
        ci.cancel();
    }

    @Redirect(method = "ungrabMouseCursor", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;setCursorPosition(II)V", remap = false))
    private void preserveCursorPositionWhenDetached(int newX, int newY) {
        if (!ModConfig.isMouseDetached) {
            Mouse.setCursorPosition(newX, newY);
        }
    }
}
