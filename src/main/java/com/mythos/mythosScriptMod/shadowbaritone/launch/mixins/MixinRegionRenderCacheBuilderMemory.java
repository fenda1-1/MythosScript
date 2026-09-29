package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RegionRenderCacheBuilder.class)
public class MixinRegionRenderCacheBuilderMemory {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;<init>(I)V"), index = 0, require = 1)
    private int mythos$initialBufferSize(int original) {
        return Boolean.getBoolean("mythosscript.headless.lowMemory") ? Math.min(original, 16384) : original;
    }
}
