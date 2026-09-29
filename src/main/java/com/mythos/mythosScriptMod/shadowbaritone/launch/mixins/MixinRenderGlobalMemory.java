package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobalMemory {
    @Redirect(method = "loadRenderers", at = @At(value = "NEW",
            target = "net/minecraft/client/renderer/chunk/ChunkRenderDispatcher"), require = 1)
    private ChunkRenderDispatcher mythos$createRenderDispatcher() {
        return new ChunkRenderDispatcher(Boolean.getBoolean("mythosscript.headless.lowMemory") ? 1 : -1);
    }
}
