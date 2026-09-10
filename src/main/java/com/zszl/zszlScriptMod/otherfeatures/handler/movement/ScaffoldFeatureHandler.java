package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class ScaffoldFeatureHandler {

    private ScaffoldFeatureHandler() {
    }

    static void apply(MovementFeatureManager manager, EntityPlayerSP player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!MovementFeatureManager.isEnabled("scaffold")
                || player == null
                || player.world == null
                || player.capabilities.isFlying
                || player.isInWater()
                || player.isInLava()
                || player.isRiding()
                || player.isHandActive()
                || mc == null
                || mc.currentScreen != null
                || manager.scaffoldPlaceCooldownTicks > 0
                || (!player.onGround && (player.fallDistance > 2.0F || player.motionY > 0.35D))
                || !MovementFeatureSupport.isMoving(player)) {
            return;
        }

        Vec3d heading = MovementFeatureSupport.getMovementHeading(player);
        if (heading.lengthSquared() < 1.0E-4D) {
            return;
        }

        double probeDistance = Math.min(3.0D, Math.max(0.50D, MovementFeatureManager.getConfiguredValue("scaffold", 1.00F)));
        for (double step = 0.0D; step <= probeDistance + 1.0E-4D; step += 0.45D) {
            AxisAlignedBB futureBox = player.getEntityBoundingBox().offset(heading.x * step, 0.0D, heading.z * step);
            AxisAlignedBB footBox = futureBox.grow(-0.10D, 0.0D, -0.10D);
            if (MovementFeatureSupport.hasGroundBelow(player, 0.55D)
                    && !player.world.getCollisionBoxes(player, footBox.offset(0.0D, -0.55D, 0.0D)).isEmpty()) {
                continue;
            }

            BlockPos targetPos = new BlockPos((futureBox.minX + futureBox.maxX) * 0.5D,
                    futureBox.minY - 0.80D,
                    (futureBox.minZ + futureBox.maxZ) * 0.5D);
            MovementFeatureSupport.PlacementTarget placement = MovementFeatureSupport.findScaffoldPlacement(player, targetPos);
            if (placement == null) {
                continue;
            }

            if (MovementFeatureSupport.placeFromScaffold(player, placement)) {
                player.fallDistance = 0.0F;
                manager.scaffoldPlaceCooldownTicks = 2;
                return;
            }
        }
    }
}
