package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerController {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void mythosScriptMod$prepareCriticalAttack(EntityPlayer player, Entity target, CallbackInfo ci) {
        ItemFeatureManager.prepareCriticalAttack(player, target);
    }
}
