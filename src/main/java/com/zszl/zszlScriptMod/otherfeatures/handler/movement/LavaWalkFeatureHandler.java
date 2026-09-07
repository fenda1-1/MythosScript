package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;

import java.util.List;

final class LavaWalkFeatureHandler {

    private static final double COLLISION_TRIGGER_OFFSET = 0.45D;
    private static final double SURFACE_SNAP_RANGE = 0.35D;
    private static final double FOOTPRINT_SHRINK = 0.12D;

    private LavaWalkFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (!isAvailable(player)) {
            return;
        }

        double surfaceY = findSupportingSurfaceY(player);
        if (Double.isNaN(surfaceY)) {
            return;
        }

        boolean pressingJump = player.movementInput != null && player.movementInput.jump;
        double liftStrength = 0.06D + 0.05D * MovementFeatureManager.getConfiguredValue("lava_walk", 0.90F);
        double distanceToSurface = surfaceY - player.posY;

        if (isInsideWalkableLiquid(player)) {
            player.motionY = Math.max(player.motionY, liftStrength);
            if (!pressingJump && distanceToSurface >= 0.0D && distanceToSurface <= SURFACE_SNAP_RANGE) {
                player.setPosition(player.posX, surfaceY, player.posZ);
                player.motionY = Math.max(player.motionY, 0.0D);
            }
            player.fallDistance = 0.0F;
            player.velocityChanged = true;
            return;
        }

        if (!pressingJump && player.motionY < 0.0D && distanceToSurface >= -0.05D && distanceToSurface <= 0.20D) {
            player.motionY = Math.max(player.motionY, -0.02D);
            player.fallDistance = 0.0F;
            player.velocityChanged = true;
        }
    }

    static void addCollisionBoxes(GetCollisionBoxesEvent event, EntityPlayerSP player) {
        if (!isAvailable(player) || event == null || event.getWorld() == null) {
            return;
        }

        AxisAlignedBB queryBox = event.getAabb();
        if (queryBox == null) {
            return;
        }

        int minX = MathHelper.floor(queryBox.minX) - 1;
        int maxX = MathHelper.floor(queryBox.maxX + 1.0D);
        int minY = MathHelper.floor(queryBox.minY) - 1;
        int maxY = MathHelper.floor(queryBox.maxY + 1.0D);
        int minZ = MathHelper.floor(queryBox.minZ) - 1;
        int maxZ = MathHelper.floor(queryBox.maxZ + 1.0D);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<AxisAlignedBB> collisionBoxes = event.getCollisionBoxesList();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.setPos(x, y, z);
                    IBlockState state = event.getWorld().getBlockState(cursor);
                    if (!isWalkableLiquid(state)) {
                        continue;
                    }
                    if (cursor.getY() >= player.posY - COLLISION_TRIGGER_OFFSET) {
                        continue;
                    }

                    AxisAlignedBB collision = Block.FULL_BLOCK_AABB.offset(cursor);
                    if (collision.intersects(queryBox)) {
                        collisionBoxes.add(collision);
                    }
                }
            }
        }
    }

    private static boolean isAvailable(EntityPlayerSP player) {
        return MovementFeatureManager.isEnabled("lava_walk")
                && player != null
                && player.world != null
                && !player.capabilities.isFlying
                && !player.isRiding()
                && !isSneakBypassActive(player);
    }

    private static double findSupportingSurfaceY(EntityPlayerSP player) {
        AxisAlignedBB footprint = player.getEntityBoundingBox().grow(-FOOTPRINT_SHRINK, 0.0D, -FOOTPRINT_SHRINK);
        int minX = MathHelper.floor(footprint.minX);
        int maxX = MathHelper.floor(footprint.maxX + 0.999D);
        int minY = MathHelper.floor(footprint.minY - 1.20D);
        int maxY = MathHelper.floor(footprint.minY + 0.20D);
        int minZ = MathHelper.floor(footprint.minZ);
        int maxZ = MathHelper.floor(footprint.maxZ + 0.999D);

        double bestSurfaceY = Double.NaN;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.setPos(x, y, z);
                    if (!isWalkableLiquid(player.world.getBlockState(cursor))) {
                        continue;
                    }
                    double candidateSurfaceY = cursor.getY() + 1.0D;
                    if (Double.isNaN(bestSurfaceY) || candidateSurfaceY > bestSurfaceY) {
                        bestSurfaceY = candidateSurfaceY;
                    }
                }
            }
        }
        return bestSurfaceY;
    }

    private static boolean isWalkableLiquid(IBlockState state) {
        if (state == null) {
            return false;
        }
        Material material = state.getMaterial();
        if (material == null || !material.isLiquid()) {
            return false;
        }
        if (MovementFeatureManager.isLiquidWalkDangerousOnly()) {
            return material == Material.LAVA;
        }
        if (material == Material.WATER && !MovementFeatureManager.shouldLiquidWalkOnWater()) {
            return false;
        }
        return true;
    }

    private static boolean isInsideWalkableLiquid(EntityPlayerSP player) {
        if (player == null || player.world == null) {
            return false;
        }

        AxisAlignedBB bounds = player.getEntityBoundingBox().grow(-0.001D, 0.0D, -0.001D);
        int minX = MathHelper.floor(bounds.minX);
        int maxX = MathHelper.floor(bounds.maxX + 0.999D);
        int minY = MathHelper.floor(bounds.minY);
        int maxY = MathHelper.floor(bounds.maxY + 0.999D);
        int minZ = MathHelper.floor(bounds.minZ);
        int maxZ = MathHelper.floor(bounds.maxZ + 0.999D);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.setPos(x, y, z);
                    if (isWalkableLiquid(player.world.getBlockState(cursor))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSneakBypassActive(EntityPlayerSP player) {
        return player != null
                && MovementFeatureManager.shouldLiquidWalkSneakToDescend()
                && player.movementInput != null
                && player.movementInput.sneak;
    }
}
