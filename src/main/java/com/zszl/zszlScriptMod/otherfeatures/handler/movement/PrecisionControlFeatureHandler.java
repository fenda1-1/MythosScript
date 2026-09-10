package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class PrecisionControlFeatureHandler {

    private PrecisionControlFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (!MovementFeatureManager.isEnabled("precision_control")
                || !player.isSneaking()
                || !MovementFeatureSupport.isMoving(player)) {
            return;
        }
        double limited = MovementFeatureSupport.getBaseMoveSpeed()
                * MovementFeatureManager.getConfiguredValue("precision_control", 0.35F);
        MovementFeatureSupport.capHorizontalSpeed(player, limited);
    }
}
