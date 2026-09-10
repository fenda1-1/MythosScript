package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.Vec3d;

final class BlinkMoveFeatureHandler {

    private BlinkMoveFeatureHandler() {
    }

    static void apply(MovementFeatureManager manager, EntityPlayerSP player) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean sneakDown = player != null && player.movementInput != null && player.movementInput.sneak;
        boolean sprintTrigger = mc != null && mc.gameSettings != null && mc.gameSettings.keyBindSprint.isKeyDown();
        boolean triggerDown = sneakDown && sprintTrigger;

        if (!MovementFeatureManager.isEnabled("blink_move")
                || player == null
                || player.world == null
                || player.capabilities.isFlying
                || player.isInWater()
                || player.isInLava()
                || player.isRiding()
                || mc == null
                || mc.currentScreen != null) {
            manager.wasBlinkTriggerDown = triggerDown;
            return;
        }

        if (!triggerDown || manager.wasBlinkTriggerDown || manager.blinkCooldownTicks > 0 || !player.onGround
                || !MovementFeatureSupport.isMoving(player)) {
            manager.wasBlinkTriggerDown = triggerDown;
            return;
        }

        double maxDistance = MovementFeatureManager.getConfiguredValue("blink_move", 3.00F);
        Vec3d destination = MovementFeatureSupport.findBestBlinkDestination(player, maxDistance);
        if (destination == null) {
            manager.wasBlinkTriggerDown = triggerDown;
            manager.blinkCooldownTicks = 6;
            return;
        }

        Vec3d direction = MovementFeatureSupport.getMovementHeading(player);
        double carrySpeed = Math.max(MovementFeatureSupport.getBaseMoveSpeed() * 0.70D,
                MovementFeatureSupport.getHorizontalSpeed(player) * 0.60D);

        player.setPosition(destination.x, destination.y, destination.z);
        if (player.connection != null) {
            player.connection.sendPacket(new CPacketPlayer.Position(destination.x, destination.y, destination.z, player.onGround));
        }

        if (direction.lengthSquared() > 1.0E-4D) {
            player.motionX = direction.x * carrySpeed;
            player.motionZ = direction.z * carrySpeed;
        } else {
            player.motionX *= 0.3D;
            player.motionZ *= 0.3D;
        }
        player.fallDistance = 0.0F;
        player.velocityChanged = true;

        manager.blinkCooldownTicks = 16;
        manager.longJumpChargeTicks = 0;
        manager.wasLongJumpSneakDown = false;
        manager.wasBlinkTriggerDown = triggerDown;
    }
}
