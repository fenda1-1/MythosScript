package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.mcp.McpDragonCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional, version-gated integration; no DragonCore compile-time dependency. */
@Pseudo
@Mixin(targets = "eos.moe.dragoncore.sl", remap = false)
public abstract class MixinDragonScreen {
    @Inject(method = "func_73863_a(IIF)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void mythos$softwareScreen(int x, int y, float partial, CallbackInfo ci) {
        if (McpDragonCore.skipScreenDraw()) ci.cancel();
    }
}
