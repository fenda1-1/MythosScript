package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider {

    @Shadow(remap = false)
    protected World field_76579_a;

    @Inject(method = "calculateCelestialAngle", at = @At("HEAD"), cancellable = true)
    private void zszl_overrideVisualTime(long worldTime, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (!WorldFeatureManager.shouldOverrideVisualTime(this.field_76579_a)) {
            return;
        }
        cir.setReturnValue(calculateCelestialAngleForTime(WorldFeatureManager.getVisualWorldTime(), partialTicks));
    }

    private float calculateCelestialAngleForTime(long worldTime, float partialTicks) {
        float timeFraction = ((worldTime % 24000L) + partialTicks) / 24000.0F - 0.25F;
        if (timeFraction < 0.0F) {
            timeFraction += 1.0F;
        }
        if (timeFraction > 1.0F) {
            timeFraction -= 1.0F;
        }

        float base = timeFraction;
        timeFraction = 1.0F - (float) ((Math.cos(timeFraction * Math.PI) + 1.0D) / 2.0D);
        return base + (timeFraction - base) / 3.0F;
    }
}
