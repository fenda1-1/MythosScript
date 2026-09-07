package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class AntiArrowKnockbackFeatureHandler {

    private AntiArrowKnockbackFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (!MovementFeatureManager.isEnabled("anti_arrow_knockback") || player.hurtTime <= 0) {
            return;
        }
        double keepRatio = 1.0D - MovementFeatureManager.getConfiguredValue("anti_arrow_knockback", 0.72F);
        player.motionX *= keepRatio;
        player.motionZ *= keepRatio;
    }
}
