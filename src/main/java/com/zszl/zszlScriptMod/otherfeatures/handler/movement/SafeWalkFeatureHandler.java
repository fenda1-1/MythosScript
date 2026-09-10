package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class SafeWalkFeatureHandler {

    private SafeWalkFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (!MovementFeatureManager.isEnabled("safe_walk")
                || player == null
                || player.world == null
                || player.noClip
                || player.capabilities.isFlying
                || !player.onGround
                || player.isRiding()
                || (player.movementInput != null && player.movementInput.jump)) {
            return;
        }

        double edgeMargin = MovementFeatureManager.getConfiguredValue("safe_walk", 0.35F);
        MovementFeatureSupport.clampHorizontalMotionToSafeWalk(player, edgeMargin);
    }
}
