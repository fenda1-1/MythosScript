package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;

final class AntiKnockbackFeatureHandler {

    private AntiKnockbackFeatureHandler() {
    }

    static void onKnockback(LivingKnockBackEvent event) {
        if (event == null || !MovementFeatureManager.isEnabled("anti_knockback")) {
            return;
        }
        event.setCanceled(true);
    }

    static void apply(EntityPlayerSP player) {
        if (player == null || !MovementFeatureManager.isEnabled("anti_knockback") || player.hurtTime <= 0) {
            return;
        }
        player.motionX = 0.0D;
        player.motionZ = 0.0D;
        if (player.motionY > 0.0D) {
            player.motionY = 0.0D;
        }
        player.velocityChanged = true;
    }
}
