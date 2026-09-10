package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class AirMotionFeatureHandler {

    private AirMotionFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        applyHoverOrGravity(player);
        applyFallCushion(player);
        LavaWalkFeatureHandler.apply(player);
    }

    private static void applyHoverOrGravity(EntityPlayerSP player) {
        if (MovementFeatureManager.isEnabled("hover_mode")
                && !player.onGround
                && !player.capabilities.isFlying
                && player.movementInput != null
                && !player.movementInput.jump
                && !player.movementInput.sneak
                && !player.isInWater()
                && !player.isInLava()) {
            player.motionY = MovementFeatureManager.getConfiguredValue("hover_mode", 0.0F);
            player.fallDistance = 0.0F;
            player.velocityChanged = true;
            return;
        }

        if (MovementFeatureManager.isEnabled("low_gravity")
                && !player.onGround
                && player.motionY < 0.0D
                && !player.capabilities.isFlying
                && !player.isInWater()
                && !player.isInLava()) {
            player.motionY *= MovementFeatureManager.getConfiguredValue("low_gravity", 0.72F);
            player.velocityChanged = true;
        }
    }

    private static void applyFallCushion(EntityPlayerSP player) {
        if (!MovementFeatureManager.isEnabled("fall_cushion")
                || player.motionY >= -0.12D
                || player.fallDistance <= 2.0F
                || !MovementFeatureSupport.hasGroundBelow(player, 1.75D)) {
            return;
        }
        double buffer = MovementFeatureManager.getConfiguredValue("fall_cushion", 0.24F);
        player.motionY = Math.max(player.motionY, -buffer);
        player.fallDistance *= 0.4F;
        player.velocityChanged = true;
    }
}
