package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

final class LongJumpFeatureHandler {

    private static final int MIN_RELEASE_TICKS = 3;

    private LongJumpFeatureHandler() {
    }

    static void apply(MovementFeatureManager manager, EntityPlayerSP player) {
        boolean enabled = MovementFeatureManager.isEnabled("long_jump");
        boolean moving = MovementFeatureSupport.isMoving(player);
        boolean sneakDown = player != null && player.movementInput != null && player.movementInput.sneak;

        MovementFeatureManager.RuntimeState rt = MovementFeatureManager.rt();
        if (!enabled || player == null || player.world == null || player.capabilities.isFlying
                || player.isInWater() || player.isInLava() || player.isRiding()) {
            rt.longJumpChargeTicks = 0;
            rt.longJumpBoostTicks = 0;
            rt.longJumpBoostSpeed = 0.0D;
            rt.wasLongJumpSneakDown = sneakDown;
            return;
        }

        if (rt.longJumpBoostTicks > 0) {
            rt.longJumpBoostSpeed = Math.max(MovementFeatureSupport.getBaseMoveSpeed(),
                    rt.longJumpBoostSpeed * 0.94D);
            MovementFeatureSupport.ensureHorizontalSpeed(player, rt.longJumpBoostSpeed);
        }

        int fullChargeTicks = Math.max(4, Math.round(MovementFeatureManager.getConfiguredValue("long_jump", 1.20F) * 20.0F));
        boolean released = rt.wasLongJumpSneakDown && !sneakDown;

        if (player.onGround && moving && sneakDown && rt.longJumpCooldownTicks <= 0) {
            rt.longJumpChargeTicks = Math.min(fullChargeTicks, rt.longJumpChargeTicks + 1);
            player.motionX *= 0.35D;
            player.motionZ *= 0.35D;
            player.setSprinting(false);
            player.velocityChanged = true;
        } else if (!sneakDown) {
            rt.longJumpChargeTicks = Math.max(0, rt.longJumpChargeTicks - 2);
        }

        if (released
                && player.onGround
                && moving
                && rt.longJumpChargeTicks >= MIN_RELEASE_TICKS
                && rt.longJumpCooldownTicks <= 0) {
            double chargeRatio = Math.max(0.25D, Math.min(1.0D, rt.longJumpChargeTicks / (double) fullChargeTicks));
            double jumpBoost = 0.42D + chargeRatio * 0.28D;
            double launchSpeed = MovementFeatureSupport.getBaseMoveSpeed() * (1.55D + chargeRatio * 2.05D);

            player.motionY = Math.max(player.motionY, jumpBoost);
            MovementFeatureSupport.setHorizontalSpeed(player, launchSpeed);
            player.fallDistance = 0.0F;
            player.velocityChanged = true;

            rt.longJumpBoostTicks = 7 + (int) Math.round(chargeRatio * 5.0D);
            rt.longJumpBoostSpeed = launchSpeed * 0.94D;
            rt.longJumpCooldownTicks = Math.max(12, Math.round(fullChargeTicks * 0.60F));
            rt.longJumpChargeTicks = 0;
        }

        if (!player.onGround && rt.longJumpChargeTicks > 0 && !sneakDown) {
            rt.longJumpChargeTicks = 0;
        }

        rt.wasLongJumpSneakDown = sneakDown;
    }
}
