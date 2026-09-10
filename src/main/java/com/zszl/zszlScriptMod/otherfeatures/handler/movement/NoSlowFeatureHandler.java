package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class NoSlowFeatureHandler {

    private NoSlowFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (!MovementFeatureManager.isEnabled("no_slow")
                || !player.isHandActive()
                || !MovementFeatureSupport.isMoving(player)) {
            return;
        }
        double minSpeed = MovementFeatureSupport.getBaseMoveSpeed()
                * MovementFeatureManager.getConfiguredValue("no_slow", 0.85F);
        MovementFeatureSupport.ensureHorizontalSpeed(player, minSpeed);
    }
}
