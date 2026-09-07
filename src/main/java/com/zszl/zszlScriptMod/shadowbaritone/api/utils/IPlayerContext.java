/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.zszl.zszlScriptMod.shadowbaritone.api.utils;

import com.zszl.zszlScriptMod.shadowbaritone.api.cache.IWorldData;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * @author Brady
 * @since 11/12/2018
 */
public interface IPlayerContext {

    Minecraft minecraft();

    EntityPlayerSP player();

    IPlayerController playerController();

    World world();

    IWorldData worldData();

    RayTraceResult objectMouseOver();

    default BetterBlockPos playerFeet() {
        // TODO find a better way to deal with soul sand!!!!!
        BetterBlockPos feet = new BetterBlockPos(player().posX, player().posY + 0.1251, player().posZ);
        BetterBlockPos liquidFeet = resolveLiquidFootprintPos();
        if (liquidFeet != null) {
            feet = liquidFeet;
        } else {
            BetterBlockPos footprintFeet = resolveFootprintStandingPos(feet);
            if (footprintFeet != null) {
                feet = footprintFeet;
            }
        }

        // sometimes when calling this from another thread or while world is null, it'll
        // throw a NullPointerException
        // that causes the game to immediately crash
        //
        // so of course crashing on 2b is horribly bad due to queue times and logout
        // spot
        // catch the NPE and ignore it if it does happen
        //
        // this does not impact performance at all since we're not null checking
        // constantly
        // if there is an exception, the only overhead is Java generating the exception
        // object... so we can ignore it
        try {
            IBlockState standingState = world().getBlockState(feet);
            if (standingState.getBlock() instanceof BlockSlab
                    || standingState.getBlock() instanceof BlockStairs) {
                return feet.up();
            }
        } catch (NullPointerException ignored) {
        }

        return feet;
    }

    default BetterBlockPos resolveLiquidFootprintPos() {
        if (player() == null || world() == null) {
            return null;
        }
        AxisAlignedBB boundingBox = player().getEntityBoundingBox();
        if (boundingBox == null) {
            return null;
        }

        double epsilon = 1.0E-4D;
        double minX = boundingBox.minX + epsilon;
        double maxX = boundingBox.maxX - epsilon;
        double minZ = boundingBox.minZ + epsilon;
        double maxZ = boundingBox.maxZ - epsilon;
        if (maxX < minX) {
            minX = maxX = player().posX;
        }
        if (maxZ < minZ) {
            minZ = maxZ = player().posZ;
        }

        int minBlockX = MathHelper.floor(minX);
        int maxBlockX = MathHelper.floor(maxX);
        int minBlockZ = MathHelper.floor(minZ);
        int maxBlockZ = MathHelper.floor(maxZ);
        int liquidY = MathHelper.floor(boundingBox.minY + epsilon);

        BetterBlockPos best = null;
        double bestOverlap = -1.0D;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                BetterBlockPos candidate = new BetterBlockPos(x, liquidY, z);
                if (!isLiquidFootCandidate(candidate)) {
                    continue;
                }

                double overlapX = overlapLength(minX, maxX, x, x + 1.0D);
                double overlapZ = overlapLength(minZ, maxZ, z, z + 1.0D);
                double overlapArea = overlapX * overlapZ;
                double dx = (candidate.x + 0.5D) - player().posX;
                double dz = (candidate.z + 0.5D) - player().posZ;
                double distanceSq = dx * dx + dz * dz;

                if (best == null
                        || overlapArea > bestOverlap + 1.0E-6D
                        || (Math.abs(overlapArea - bestOverlap) <= 1.0E-6D
                                && distanceSq < bestDistanceSq - 1.0E-6D)) {
                    best = candidate;
                    bestOverlap = overlapArea;
                    bestDistanceSq = distanceSq;
                }
            }
        }

        return best;
    }

    default BetterBlockPos resolveFootprintStandingPos(BetterBlockPos defaultFeet) {
        if (player() == null) {
            return defaultFeet;
        }
        AxisAlignedBB boundingBox = player().getEntityBoundingBox();
        if (boundingBox == null) {
            return defaultFeet;
        }

        double epsilon = 1.0E-4D;
        double minX = boundingBox.minX + epsilon;
        double maxX = boundingBox.maxX - epsilon;
        double minZ = boundingBox.minZ + epsilon;
        double maxZ = boundingBox.maxZ - epsilon;
        if (maxX < minX) {
            minX = maxX = player().posX;
        }
        if (maxZ < minZ) {
            minZ = maxZ = player().posZ;
        }

        int minBlockX = MathHelper.floor(minX);
        int maxBlockX = MathHelper.floor(maxX);
        int minBlockZ = MathHelper.floor(minZ);
        int maxBlockZ = MathHelper.floor(maxZ);

        BetterBlockPos best = null;
        double bestOverlap = -1.0D;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        boolean bestMatchesDefault = false;

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                BetterBlockPos candidate = adjustStandingCandidate(new BetterBlockPos(x, defaultFeet.y, z));
                if (!isUsableStandingCandidate(candidate)) {
                    continue;
                }

                double overlapX = overlapLength(minX, maxX, x, x + 1.0D);
                double overlapZ = overlapLength(minZ, maxZ, z, z + 1.0D);
                double overlapArea = overlapX * overlapZ;
                double dx = (candidate.x + 0.5D) - player().posX;
                double dz = (candidate.z + 0.5D) - player().posZ;
                double distanceSq = dx * dx + dz * dz;
                boolean matchesDefault = candidate.equals(defaultFeet);

                if (best == null
                        || overlapArea > bestOverlap + 1.0E-6D
                        || (Math.abs(overlapArea - bestOverlap) <= 1.0E-6D
                                && (matchesDefault && !bestMatchesDefault
                                        || (matchesDefault == bestMatchesDefault
                                                && distanceSq < bestDistanceSq - 1.0E-6D)))) {
                    best = candidate;
                    bestOverlap = overlapArea;
                    bestDistanceSq = distanceSq;
                    bestMatchesDefault = matchesDefault;
                }
            }
        }

        return best;
    }

    static double overlapLength(double minA, double maxA, double minB, double maxB) {
        return Math.max(0.0D, Math.min(maxA, maxB) - Math.max(minA, minB));
    }

    default BetterBlockPos adjustStandingCandidate(BetterBlockPos candidate) {
        if (candidate == null) {
            return null;
        }
        try {
            IBlockState standingState = world().getBlockState(candidate);
            if (standingState.getBlock() instanceof BlockSlab
                    || standingState.getBlock() instanceof BlockStairs) {
                return candidate.up();
            }
        } catch (NullPointerException ignored) {
        }
        return candidate;
    }

    default boolean isUsableStandingCandidate(BetterBlockPos candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            IBlockState feetState = world().getBlockState(candidate);
            IBlockState headState = world().getBlockState(candidate.up());
            IBlockState groundState = world().getBlockState(candidate.down());
            boolean feetPassable = !feetState.getMaterial().blocksMovement();
            boolean headPassable = !headState.getMaterial().blocksMovement();
            boolean hasGround = groundState.getMaterial().blocksMovement()
                    || groundState.getBlock() instanceof BlockSlab
                    || groundState.getBlock() instanceof BlockStairs;
            return feetPassable && headPassable && hasGround;
        } catch (NullPointerException ignored) {
            return false;
        }
    }

    default boolean isLiquidFootCandidate(BetterBlockPos candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            return world().getBlockState(candidate).getBlock() instanceof BlockLiquid;
        } catch (NullPointerException ignored) {
            return false;
        }
    }

    default Vec3d playerFeetAsVec() {
        return new Vec3d(player().posX, player().posY, player().posZ);
    }

    default Vec3d playerHead() {
        return new Vec3d(player().posX, player().posY + player().getEyeHeight(), player().posZ);
    }

    default Vec3d playerMotion() {
        return new Vec3d(player().motionX, player().motionY, player().motionZ);
    }

    BetterBlockPos viewerPos();

    default Rotation playerRotations() {
        return new Rotation(player().rotationYaw, player().rotationPitch);
    }

    static double eyeHeight(boolean ifSneaking) {
        return ifSneaking ? 1.54 : 1.62;
    }

    /**
     * Returns the block that the crosshair is currently placed over. Updated once
     * per tick.
     *
     * @return The position of the highlighted block
     */
    default Optional<BlockPos> getSelectedBlock() {
        RayTraceResult result = objectMouseOver();
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            return Optional.of(result.getBlockPos());
        }
        return Optional.empty();
    }

    default boolean isLookingAt(BlockPos pos) {
        return getSelectedBlock().equals(Optional.of(pos));
    }
}
