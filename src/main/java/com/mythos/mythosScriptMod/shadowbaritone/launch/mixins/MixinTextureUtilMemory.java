package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TextureUtil.class)
public class MixinTextureUtilMemory {
    @ModifyConstant(method = {"<clinit>", "uploadTextureSub", "uploadTextureImageSubImpl"},
            constant = @Constant(intValue = 4194304), require = 3)
    private static int mythos$textureBatchPixels(int original) {
        return Boolean.getBoolean("mythosscript.headless.lowMemory") ? 65536 : original;
    }
}
