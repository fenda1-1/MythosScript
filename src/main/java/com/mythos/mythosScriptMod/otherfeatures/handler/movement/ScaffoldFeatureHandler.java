package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

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
                || mc.currentScreen != null) {
            return;
        }

        Vec3d heading = MovementFeatureSupport.getMovementHeading(player);
        if (heading.lengthSquared() < 1.0E-4D) {
            heading = new Vec3d(player.motionX, 0.0D, player.motionZ);
        }
        if (heading.lengthSquared() < 1.0E-4D) {
            heading = new Vec3d(0.0D, 0.0D, 0.0D);
        } else {
            heading = heading.normalize();
        }

        double reach = Math.min(4.0D, Math.max(1.25D, MovementFeatureManager.getConfiguredValue("scaffold", 1.00F) + 1.25D));
        double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        double probeDistance = Math.min(reach, Math.max(0.90D, speed * 6.0D + 0.90D));
        for (double step = 0.0D; step <= probeDistance + 1.0E-4D; step += 0.30D) {
            AxisAlignedBB futureBox = player.getEntityBoundingBox().offset(heading.x * step, 0.0D, heading.z * step);
            if (placeUnder(manager, player, futureBox)) {
                return;
            }
        }
    }

    private static boolean placeUnder(MovementFeatureManager manager, EntityPlayerSP player, AxisAlignedBB box) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        int minX = net.minecraft.util.math.MathHelper.floor(box.minX + 0.001D);
        int maxX = net.minecraft.util.math.MathHelper.floor(box.maxX - 0.001D);
        int minZ = net.minecraft.util.math.MathHelper.floor(box.minZ + 0.001D);
        int maxZ = net.minecraft.util.math.MathHelper.floor(box.maxZ - 0.001D);
        int y = net.minecraft.util.math.MathHelper.floor(box.minY - 0.80D);
        BlockPos[] targets = new BlockPos[] {
                new BlockPos(centerX, y, centerZ),
                new BlockPos(minX, y, minZ),
                new BlockPos(maxX, y, minZ),
                new BlockPos(minX, y, maxZ),
                new BlockPos(maxX, y, maxZ)
        };
        for (BlockPos targetPos : targets) {
            if (!player.world.getCollisionBoxes(player, new AxisAlignedBB(targetPos)).isEmpty()) {
                continue;
            }
            MovementFeatureSupport.PlacementTarget placement = MovementFeatureSupport.findScaffoldPlacement(player, targetPos);
            if (placement != null && MovementFeatureSupport.placeFromScaffold(player, placement)) {
                player.fallDistance = 0.0F;
                manager.scaffoldPlaceCooldownTicks = 0;
                return true;
            }
        }
        return false;
    }
}
