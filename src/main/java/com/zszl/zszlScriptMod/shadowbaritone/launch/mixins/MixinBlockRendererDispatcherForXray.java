package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRendererDispatcher.class)
public class MixinBlockRendererDispatcherForXray {

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void zszl$skipHiddenBlocksDuringXray(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
            BufferBuilder bufferBuilderIn, CallbackInfoReturnable<Boolean> cir) {
        if (!RenderFeatureManager.isEnabled("xray")) {
            return;
        }
        if (!RenderFeatureManager.isXrayBlockVisible(state == null ? null : state.getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
