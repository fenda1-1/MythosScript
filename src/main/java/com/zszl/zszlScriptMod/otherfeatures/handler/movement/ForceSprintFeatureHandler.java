package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class ForceSprintFeatureHandler {

    private ForceSprintFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (MovementFeatureManager.isEnabled("force_sprint")
                && MovementFeatureSupport.isMoving(player)
                && !player.isSneaking()
                && !player.collidedHorizontally) {
            player.setSprinting(true);
        }
    }
}
