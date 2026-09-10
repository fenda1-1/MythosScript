package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcherForXray {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    private void zszl$skipHiddenTileEntitiesDuringXray(TileEntity tileentityIn, double x, double y, double z,
            float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (!RenderFeatureManager.isEnabled("xray")) {
            return;
        }
        if (tileentityIn == null || !RenderFeatureManager.isXrayBlockVisible(tileentityIn.getBlockType())) {
            ci.cancel();
        }
    }
}
