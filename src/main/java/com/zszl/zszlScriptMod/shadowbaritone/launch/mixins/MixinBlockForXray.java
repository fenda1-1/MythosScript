package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class MixinBlockForXray {

    @Inject(method = "isOpaqueCube", at = @At("HEAD"), cancellable = true)
    private void zszl$disableOpaqueCubeForXray(IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (RenderFeatureManager.isEnabled("xray")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getAmbientOcclusionLightValue", at = @At("HEAD"), cancellable = true)
    private void zszl$brightenXrayBlocks(IBlockState state, CallbackInfoReturnable<Float> cir) {
        if (RenderFeatureManager.isEnabled("xray")) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void zszl$renderVisibleXraySides(IBlockState state, IBlockAccess access, BlockPos pos, EnumFacing side,
            CallbackInfoReturnable<Boolean> cir) {
        if (!RenderFeatureManager.isEnabled("xray")) {
            return;
        }
        cir.setReturnValue(RenderFeatureManager.isXrayBlockVisible(state == null ? null : state.getBlock()));
    }
}
