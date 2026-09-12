package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EntityBoat.class, EntityMinecart.class})
public class MixinVehicleCollision {
    @Inject(method = "applyEntityCollision", at = @At("HEAD"), cancellable = true, require = 1)
    private void zszl$ignorePlayerPush(Entity other, CallbackInfo ci) {
        if (MovementFeatureManager.hasNoCollision(other)) ci.cancel();
    }
}
