package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import com.zszl.zszlScriptMod.utils.ModUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

final class MovementFeatureSupport {

    private static final double SAFE_WALK_CLIP_STEP = 0.05D;

    static final class PlacementTarget {
        final BlockPos placePos;
        final BlockPos supportPos;
        final EnumFacing supportFace;
        final Vec3d hitVec;
        final int hotbarSlot;

        private PlacementTarget(BlockPos placePos, BlockPos supportPos, EnumFacing supportFace, Vec3d hitVec,
                int hotbarSlot) {
            this.placePos = placePos;
            this.supportPos = supportPos;
            this.supportFace = supportFace;
            this.hitVec = hitVec;
            this.hotbarSlot = hotbarSlot;
        }
    }

    private static final EnumFacing[] PLACE_SEARCH_ORDER = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP
    };

    private MovementFeatureSupport() {
    }

    static boolean isMoving(EntityPlayerSP player) {
        return player != null && player.movementInput != null
                && (Math.abs(player.movementInput.moveForward) > 0.01F
                || Math.abs(player.movementInput.moveStrafe) > 0.01F);
    }

    static double getBaseMoveSpeed() {
        return 0.2873D;
    }

    static double getHorizontalSpeed(EntityPlayerSP player) {
        return player == null ? 0.0D : Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    static void ensureHorizontalSpeed(EntityPlayerSP player, double minimumSpeed) {
        if (player == null || !isMoving(player)) {
            return;
        }
        double current = getHorizontalSpeed(player);
        if (current + 1.0E-4D >= minimumSpeed) {
            return;
        }
        setHorizontalSpeed(player, minimumSpeed);
    }

    static void capHorizontalSpeed(EntityPlayerSP player, double maxSpeed) {
        if (player == null || !isMoving(player)) {
            return;
        }
        double current = getHorizontalSpeed(player);
        if (current <= maxSpeed) {
            return;
        }
        setHorizontalSpeed(player, maxSpeed);
    }

    static void setHorizontalSpeed(EntityPlayerSP player, double speed) {
        double[] dir = forward(player, speed);
        player.motionX = dir[0];
        player.motionZ = dir[1];
        player.velocityChanged = true;
    }

    static boolean hasGroundBelow(EntityPlayerSP player, double distance) {
        return player != null && player.world != null
                && !player.world.getCollisionBoxes(player, player.getEntityBoundingBox().offset(0.0D, -distance, 0.0D))
                .isEmpty();
    }

    static boolean isStandingOnIce(EntityPlayerSP player) {
        if (player == null || player.world == null) {
            return false;
        }
        BlockPos underPos = new BlockPos(player.posX, player.getEntityBoundingBox().minY - 0.05D, player.posZ);
        Block block = player.world.getBlockState(underPos).getBlock();
        return block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.FROSTED_ICE;
    }

    static Vec3d getMovementHeading(EntityPlayerSP player) {
        if (player == null) {
            return new Vec3d(0.0D, 0.0D, 0.0D);
        }

        double horizontalSpeed = getHorizontalSpeed(player);
        if (horizontalSpeed > 0.05D) {
            return normalizeHorizontal(new Vec3d(player.motionX, 0.0D, player.motionZ));
        }

        return normalizeHorizontal(getInputVector(player));
    }

    static Vec3d rotateLeft(Vec3d direction) {
        return normalizeHorizontal(new Vec3d(-direction.z, 0.0D, direction.x));
    }

    static Vec3d rotateRight(Vec3d direction) {
        return normalizeHorizontal(new Vec3d(direction.z, 0.0D, -direction.x));
    }

    static Vec3d blendHorizontal(Vec3d primary, double primaryWeight, Vec3d secondary, double secondaryWeight) {
        return normalizeHorizontal(primary.scale(primaryWeight).add(secondary.scale(secondaryWeight)));
    }

    static boolean canOccupy(EntityPlayerSP player, double x, double y, double z) {
        if (player == null || player.world == null) {
            return false;
        }
        AxisAlignedBB bb = getBoundingBoxAt(player, x, y, z).grow(-0.001D, 0.0D, -0.001D);
        return !hasBlockingCollisionIgnoringCarpet(player, bb);
    }

    static boolean canStandAt(EntityPlayerSP player, double x, double y, double z) {
        if (player == null || player.world == null) {
            return false;
        }
        AxisAlignedBB bb = getBoundingBoxAt(player, x, y, z);
        if (hasBlockingCollisionIgnoringCarpet(player, bb.grow(-0.001D, 0.0D, -0.001D))) {
            return false;
        }
        return hasSupportBelow(player, shrinkHorizontal(bb, 0.08D), 0.60D)
                && !isHazardousBelow(player, bb);
    }

    static boolean canStepUp(EntityPlayerSP player, Vec3d direction) {
        if (player == null || player.world == null || direction == null || direction.lengthSquared() < 1.0E-4D
                || player.stepHeight < 0.9F) {
            return false;
        }

        double probe = 0.55D;
        double targetX = player.posX + direction.x * probe;
        double targetY = player.posY + 1.0D;
        double targetZ = player.posZ + direction.z * probe;
        return canStandAt(player, targetX, targetY, targetZ);
    }

    static boolean hasObstacleAhead(EntityPlayerSP player, Vec3d direction, double distance) {
        if (player == null || player.world == null || direction == null || direction.lengthSquared() < 1.0E-4D) {
            return false;
        }

        Vec3d normalized = normalizeHorizontal(direction);
        for (double step = 0.25D; step <= distance; step += 0.25D) {
            AxisAlignedBB moved = player.getEntityBoundingBox().offset(normalized.x * step, 0.0D, normalized.z * step)
                    .grow(-0.02D, 0.0D, -0.02D);
            if (hasBlockingCollisionIgnoringCarpet(player, moved)) {
                return true;
            }
        }
        return false;
    }

    static void clampHorizontalMotionToSafeWalk(EntityPlayerSP player, double edgeMargin) {
        if (player == null || player.world == null) {
            return;
        }

        double motionX = player.motionX;
        double motionZ = player.motionZ;
        if (Math.abs(motionX) < 1.0E-4D && Math.abs(motionZ) < 1.0E-4D) {
            return;
        }

        double clippedX = motionX;
        double clippedZ = motionZ;
        while (Math.abs(clippedX) > 1.0E-4D && isSafeWalkOffsetUnsafe(player, clippedX, 0.0D, edgeMargin)) {
            clippedX = approachZero(clippedX, SAFE_WALK_CLIP_STEP);
        }
        while (Math.abs(clippedZ) > 1.0E-4D && isSafeWalkOffsetUnsafe(player, 0.0D, clippedZ, edgeMargin)) {
            clippedZ = approachZero(clippedZ, SAFE_WALK_CLIP_STEP);
        }
        while (Math.abs(clippedX) > 1.0E-4D
                && Math.abs(clippedZ) > 1.0E-4D
                && isSafeWalkOffsetUnsafe(player, clippedX, clippedZ, edgeMargin)) {
            clippedX = approachZero(clippedX, SAFE_WALK_CLIP_STEP);
            clippedZ = approachZero(clippedZ, SAFE_WALK_CLIP_STEP);
        }

        if (Math.abs(clippedX - motionX) < 1.0E-4D && Math.abs(clippedZ - motionZ) < 1.0E-4D) {
            return;
        }

        player.motionX = clippedX;
        player.motionZ = clippedZ;
        player.fallDistance = 0.0F;
        player.velocityChanged = true;
    }

    static Vec3d findNearestSafePosition(EntityPlayerSP player, double maxRadius, Vec3d preferredDirection) {
        if (player == null || player.world == null) {
            return null;
        }

        Vec3d preferred = normalizeHorizontal(preferredDirection);
        Vec3d best = null;
        double bestScore = Double.MAX_VALUE;

        for (double radius = 0.15D; radius <= maxRadius + 1.0E-4D; radius += 0.15D) {
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2.0D * i) / 16.0D;
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;
                double score = radius;

                if (preferred.lengthSquared() > 0.0D) {
                    Vec3d candidateDir = normalizeHorizontal(new Vec3d(offsetX, 0.0D, offsetZ));
                    score -= preferred.dotProduct(candidateDir) * 0.18D;
                }

                for (double yOffset : new double[] { 0.0D, 1.0D, -1.0D, 0.5D, -0.5D }) {
                    double targetX = player.posX + offsetX;
                    double targetY = player.posY + yOffset;
                    double targetZ = player.posZ + offsetZ;
                    if (!canStandAt(player, targetX, targetY, targetZ)) {
                        continue;
                    }
                    double adjustedScore = score + Math.abs(yOffset) * 0.12D;
                    if (best == null || adjustedScore < bestScore) {
                        best = new Vec3d(targetX, targetY, targetZ);
                        bestScore = adjustedScore;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }

        return best;
    }

    static Vec3d findBestBlinkDestination(EntityPlayerSP player, double maxDistance) {
        Vec3d heading = getMovementHeading(player);
        if (player == null || player.world == null || heading.lengthSquared() < 1.0E-4D) {
            return null;
        }

        Vec3d best = null;
        for (double step = 0.50D; step <= maxDistance + 1.0E-4D; step += 0.25D) {
            double targetX = player.posX + heading.x * step;
            double targetY = player.posY;
            double targetZ = player.posZ + heading.z * step;
            if (canStandAt(player, targetX, targetY, targetZ)) {
                best = new Vec3d(targetX, targetY, targetZ);
            } else if (canStandAt(player, targetX, targetY + 1.0D, targetZ)) {
                best = new Vec3d(targetX, targetY + 1.0D, targetZ);
            } else if (canStandAt(player, targetX, targetY - 1.0D, targetZ)) {
                best = new Vec3d(targetX, targetY - 1.0D, targetZ);
            } else {
                break;
            }
        }
        return best;
    }

    static PlacementTarget findScaffoldPlacement(EntityPlayerSP player, BlockPos targetPos) {
        if (player == null || player.world == null || targetPos == null || !isReplaceable(player.world, targetPos)) {
            return null;
        }

        int hotbarSlot = findHotbarPlaceableBlockSlot(player);
        if (hotbarSlot < 0) {
            return null;
        }

        for (EnumFacing facing : PLACE_SEARCH_ORDER) {
            BlockPos supportPos = targetPos.offset(facing);
            if (!canPlaceAgainst(player.world, supportPos)) {
                continue;
            }
            EnumFacing supportFace = facing.getOpposite();
            Vec3d hitVec = new Vec3d(supportPos.getX() + 0.5D + supportFace.getFrontOffsetX() * 0.5D,
                    supportPos.getY() + 0.5D + supportFace.getFrontOffsetY() * 0.5D,
                    supportPos.getZ() + 0.5D + supportFace.getFrontOffsetZ() * 0.5D);
            return new PlacementTarget(targetPos, supportPos, supportFace, hitVec, hotbarSlot);
        }

        return null;
    }

    static boolean placeFromScaffold(EntityPlayerSP player, PlacementTarget target) {
        if (player == null || target == null || player.world == null || player.connection == null) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.playerController == null || mc.world == null) {
            return false;
        }

        int originalSlot = player.inventory.currentItem;
        if (!ModUtils.switchToHotbarSlot(target.hotbarSlot + 1)) {
            return false;
        }

        EnumActionResult result = mc.playerController.processRightClickBlock(player, mc.world, target.supportPos,
                target.supportFace, target.hitVec, EnumHand.MAIN_HAND);
        player.swingArm(EnumHand.MAIN_HAND);

        if (originalSlot != target.hotbarSlot) {
            ModUtils.switchToHotbarSlot(originalSlot + 1);
        }

        return result == EnumActionResult.SUCCESS;
    }

    private static int findHotbarPlaceableBlockSlot(EntityPlayerSP player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            IBlockState state = block.getDefaultState();
            if (block == Blocks.AIR
                    || block instanceof BlockFalling
                    || !state.isFullBlock()
                    || !state.isFullCube()
                    || block.hasTileEntity(state)
                    || state.getMaterial().isLiquid()) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean canPlaceAgainst(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state == null || state.getMaterial().isReplaceable() || state.getBlock() == Blocks.AIR) {
            return false;
        }
        if (state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB) {
            return false;
        }
        TileEntity tileEntity = world.getTileEntity(pos);
        return tileEntity == null;
    }

    private static boolean isReplaceable(World world, BlockPos pos) {
        return world.getBlockState(pos).getMaterial().isReplaceable();
    }

    private static AxisAlignedBB getBoundingBoxAt(EntityPlayerSP player, double x, double y, double z) {
        return player.getEntityBoundingBox().offset(x - player.posX, y - player.posY, z - player.posZ);
    }

    static boolean hasBlockingCollisionIgnoringCarpet(EntityPlayerSP player, AxisAlignedBB box) {
        return !collectCollisionBoxesIgnoringCarpet(player, box).isEmpty();
    }

    private static boolean hasSupportBelow(EntityPlayerSP player, AxisAlignedBB box, double depth) {
        return hasBlockingCollisionIgnoringCarpet(player, box.offset(0.0D, -depth, 0.0D));
    }

    private static boolean isHazardousBelow(EntityPlayerSP player, AxisAlignedBB box) {
        World world = player.world;
        int minX = MathHelper.floor(box.minX + 0.01D);
        int maxX = MathHelper.floor(box.maxX - 0.01D);
        int minZ = MathHelper.floor(box.minZ + 0.01D);
        int maxZ = MathHelper.floor(box.maxZ - 0.01D);
        int y = MathHelper.floor(box.minY - 0.08D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
                if (block == Blocks.LAVA
                        || block == Blocks.FLOWING_LAVA
                        || block == Blocks.FIRE
                        || block == Blocks.CACTUS
                        || block == Blocks.MAGMA) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AxisAlignedBB shrinkHorizontal(AxisAlignedBB box, double amount) {
        double maxShrinkX = Math.min(amount, Math.max(0.0D, (box.maxX - box.minX) * 0.49D));
        double maxShrinkZ = Math.min(amount, Math.max(0.0D, (box.maxZ - box.minZ) * 0.49D));
        return new AxisAlignedBB(box.minX + maxShrinkX, box.minY, box.minZ + maxShrinkZ,
                box.maxX - maxShrinkX, box.maxY, box.maxZ - maxShrinkZ);
    }

    private static boolean isSafeWalkOffsetUnsafe(EntityPlayerSP player, double motionX, double motionZ,
            double edgeMargin) {
        AxisAlignedBB moved = player.getEntityBoundingBox().offset(motionX, -1.0D, motionZ);
        double supportInset = MathHelper.clamp(Math.max(0.0D, edgeMargin - 0.35D) * 0.22D, 0.0D, 0.16D);
        AxisAlignedBB supportBox = supportInset <= 1.0E-4D ? moved : shrinkHorizontal(moved, supportInset);
        return !hasBlockingCollisionIgnoringCarpet(player, supportBox);
    }

    private static List<AxisAlignedBB> collectCollisionBoxesIgnoringCarpet(EntityPlayerSP player, AxisAlignedBB box) {
        List<AxisAlignedBB> collisions = new ArrayList<>();
        if (player == null || player.world == null || box == null) {
            return collisions;
        }

        int minX = MathHelper.floor(box.minX) - 1;
        int maxX = MathHelper.floor(box.maxX) + 1;
        int minY = MathHelper.floor(box.minY) - 1;
        int maxY = MathHelper.floor(box.maxY) + 1;
        int minZ = MathHelper.floor(box.minZ) - 1;
        int maxZ = MathHelper.floor(box.maxZ) + 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.setPos(x, y, z);
                    IBlockState state = player.world.getBlockState(cursor);
                    if (state == null) {
                        continue;
                    }

                    Block block = state.getBlock();
                    if (block == null || block == Blocks.AIR || block == Blocks.CARPET) {
                        continue;
                    }
                    if (state.getCollisionBoundingBox(player.world, cursor) == Block.NULL_AABB) {
                        continue;
                    }

                    state.addCollisionBoxToList(player.world, cursor, box, collisions, player, false);
                    if (!collisions.isEmpty()) {
                        return collisions;
                    }
                }
            }
        }
        return collisions;
    }

    private static double approachZero(double value, double amount) {
        if (value > 0.0D) {
            return Math.max(0.0D, value - amount);
        }
        if (value < 0.0D) {
            return Math.min(0.0D, value + amount);
        }
        return 0.0D;
    }

    private static Vec3d getInputVector(EntityPlayerSP player) {
        float forward = player == null || player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        float strafe = player == null || player.movementInput == null ? 0.0F : player.movementInput.moveStrafe;
        if (Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) {
            return new Vec3d(0.0D, 0.0D, 0.0D);
        }

        float yaw = player.rotationYaw;
        if (forward != 0.0F) {
            if (strafe > 0.0F) {
                yaw += forward > 0.0F ? -45.0F : 45.0F;
            } else if (strafe < 0.0F) {
                yaw += forward > 0.0F ? 45.0F : -45.0F;
            }
            strafe = 0.0F;
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }

        if (strafe > 0.0F) {
            strafe = 1.0F;
        } else if (strafe < 0.0F) {
            strafe = -1.0F;
        }

        double rad = Math.toRadians(yaw + 90.0F);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Vec3d(forward * cos + strafe * sin, 0.0D, forward * sin - strafe * cos);
    }

    private static Vec3d normalizeHorizontal(Vec3d vector) {
        if (vector == null) {
            return new Vec3d(0.0D, 0.0D, 0.0D);
        }
        double length = Math.sqrt(vector.x * vector.x + vector.z * vector.z);
        if (length < 1.0E-6D) {
            return new Vec3d(0.0D, 0.0D, 0.0D);
        }
        return new Vec3d(vector.x / length, 0.0D, vector.z / length);
    }

    private static double[] forward(EntityPlayerSP player, double speed) {
        Vec3d vector = getInputVector(player);
        Vec3d direction = normalizeHorizontal(vector);
        if (direction.lengthSquared() < 1.0E-4D) {
            direction = getMovementHeading(player);
        }
        return new double[] { direction.x * speed, direction.z * speed };
    }
}

