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
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.TabCompleteEvent;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.TabCompleter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabCompleter.class)
public abstract class MixinTabCompleter {

    @Shadow(remap = false)
    @Final
    protected GuiTextField field_186844_a;

    @Shadow(remap = false)
    protected boolean field_186847_d;

    @Shadow(remap = false)
    public abstract void func_186840_a(String... newCompl);

    @Unique
    protected boolean dontComplete = false;

    @Inject(method = "requestCompletions", at = @At("HEAD"), cancellable = true)
    private void onRequestCompletions(String prefix, CallbackInfo ci) {
        if (!((Object) this instanceof GuiChat.ChatTabCompleter)) {
            return;
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        TabCompleteEvent event = new TabCompleteEvent(prefix);
        baritone.getGameEventHandler().onPreTabComplete(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.completions != null) {
            ci.cancel();

            this.dontComplete = true;

            try {
                this.field_186847_d = true;
                func_186840_a(event.completions);
            } finally {
                this.dontComplete = false;
            }
        }
    }
}
