package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.mcp.McpDragonCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"eos.moe.dragoncore.gd", "eos.moe.dragoncore.wd",
        "eos.moe.dragoncore.oj", "eos.moe.dragoncore.ie", "eos.moe.dragoncore.kf",
        "eos.moe.dragoncore.nh", "eos.moe.dragoncore.cn"}, remap = false)
public abstract class MixinDragonComponent {
    @Inject(method = "render(II)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void mythos$softwareComponent(int x, int y, CallbackInfo ci) {
        if (McpDragonCore.captureComponent(this)) ci.cancel();
    }
}
