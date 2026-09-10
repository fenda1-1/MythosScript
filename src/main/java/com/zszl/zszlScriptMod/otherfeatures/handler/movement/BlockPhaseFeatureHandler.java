package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.Vec3d;

final class BlockPhaseFeatureHandler {

    private BlockPhaseFeatureHandler() {
    }

    static void apply(MovementFeatureManager manager, EntityPlayerSP player) {
        if (!MovementFeatureManager.isEnabled("block_phase")
                || player == null
                || player.world == null
                || player.capabilities.isFlying
                || player.isInWater()
                || player.isInLava()
                || player.isRiding()) {
            manager.blockPhaseStuckTicks = 0;
            return;
        }

        boolean moving = MovementFeatureSupport.isMoving(player);
        boolean insideOpaque = player.isEntityInsideOpaqueBlock();
        boolean horizontallyBlocked = moving && player.collidedHorizontally;
        if (insideOpaque || horizontallyBlocked) {
            manager.blockPhaseStuckTicks++;
        } else {
            manager.blockPhaseStuckTicks = 0;
            return;
        }

        if (!insideOpaque && manager.blockPhaseStuckTicks < 4) {
            return;
        }

        double configuredStrength = MovementFeatureManager.getConfiguredValue("block_phase", 0.12F);
        double searchRadius = 0.55D + configuredStrength * 2.4D;
        Vec3d preferred = horizontallyBlocked ? MovementFeatureSupport.getMovementHeading(player) : null;
        Vec3d safePos = MovementFeatureSupport.findNearestSafePosition(player, searchRadius, preferred);
        if (safePos == null) {
            return;
        }

        double originX = player.posX;
        double originY = player.posY;
        double originZ = player.posZ;
        Vec3d escapeDir = new Vec3d(safePos.x - originX, 0.0D, safePos.z - originZ);
        double blend = insideOpaque ? 1.0D : Math.min(0.85D, 0.32D + configuredStrength * 1.4D);
        double targetX = player.posX + (safePos.x - player.posX) * blend;
        double targetY = player.posY + (safePos.y - player.posY) * blend;
        double targetZ = player.posZ + (safePos.z - player.posZ) * blend;
        if (!MovementFeatureSupport.canOccupy(player, targetX, targetY, targetZ)) {
            targetX = safePos.x;
            targetY = safePos.y;
            targetZ = safePos.z;
        }

        player.setPosition(targetX, targetY, targetZ);
        if (player.connection != null) {
            player.connection.sendPacket(new CPacketPlayer.Position(targetX, targetY, targetZ, player.onGround));
        }

        if (escapeDir.lengthSquared() > 1.0E-4D) {
            Vec3d horizontal = MovementFeatureSupport.blendHorizontal(MovementFeatureSupport.getMovementHeading(player), 0.35D,
                    escapeDir, 0.85D);
            double speed = Math.max(0.12D, MovementFeatureSupport.getHorizontalSpeed(player));
            player.motionX = horizontal.x * speed;
            player.motionZ = horizontal.z * speed;
        } else {
            player.motionX *= 0.5D;
            player.motionZ *= 0.5D;
        }
        player.fallDistance = 0.0F;
        player.velocityChanged = true;
        manager.blockPhaseStuckTicks = 0;
    }
}
