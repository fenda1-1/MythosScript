package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LWJGL stubs alone still execute mod world renderers and load their textures. */
@Mixin(EntityRenderer.class)
public abstract class MixinHeadlessWorldRender {
    // Explicit SRG name also works in the incremental build without a generated refmap.
    // Keep updateCameraAndRender, GUI drawing, input and client ticks running.
    @Inject(method = "func_175068_a(IFJ)V", at = @At("HEAD"), cancellable = true,
            require = 1, remap = false)
    private void mythos$skipHeadlessWorld(int pass, float partialTicks, long deadline, CallbackInfo ci) {
        if (Boolean.getBoolean("mythosscript.headless.stub")) ci.cancel();
    }
}
