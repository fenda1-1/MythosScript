package com.zszl.zszlScriptMod.shadowbaritone.pathing.portal;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

public final class EdgePortalDetector {

    private static final double MIN_NARROW_DIMENSION = 0.95D;
    private static final double MIN_VALID_GAP_WIDTH = 0.05D;

    private EdgePortalDetector() {
    }

    public static EdgePortal detect(CalculationContext context, BetterBlockPos barrierA, IBlockState stateA,
            BetterBlockPos barrierB, IBlockState stateB, EnumFacing travelFacing, int y) {
        AxisAlignedBB localBoxA = getLocalCollisionBox(context.bsi.access, barrierA, stateA);
        AxisAlignedBB localBoxB = getLocalCollisionBox(context.bsi.access, barrierB, stateB);
        return detect(barrierA, stateA, localBoxA, barrierB, stateB, localBoxB, travelFacing, y);
    }

    public static EdgePortal detect(IBlockAccess access, BetterBlockPos barrierA, IBlockState stateA,
            BetterBlockPos barrierB, IBlockState stateB, EnumFacing travelFacing, int y) {
        AxisAlignedBB localBoxA = getLocalCollisionBox(access, barrierA, stateA);
        AxisAlignedBB localBoxB = getLocalCollisionBox(access, barrierB, stateB);
        return detect(barrierA, stateA, localBoxA, barrierB, stateB, localBoxB, travelFacing, y);
    }

    public static EdgePortal detect(BetterBlockPos barrierA, IBlockState stateA, AxisAlignedBB localBoxA,
            BetterBlockPos barrierB, IBlockState stateB, AxisAlignedBB localBoxB, EnumFacing travelFacing, int y) {
        if (barrierA == null || barrierB == null || stateA == null || stateB == null
                || localBoxA == null || localBoxB == null || travelFacing == null
                || travelFacing.getAxis() == EnumFacing.Axis.Y) {
            return null;
        }
        if (!isNarrowBarrier(stateA, localBoxA) || !isNarrowBarrier(stateB, localBoxB)) {
            return null;
        }

        AxisAlignedBB boxA = localBoxA.offset(barrierA);
        AxisAlignedBB boxB = localBoxB.offset(barrierB);

        if (barrierA.z == barrierB.z && Math.abs(barrierA.x - barrierB.x) == 1
                && travelFacing.getAxis() == EnumFacing.Axis.Z) {
            AxisAlignedBB left = boxA.minX <= boxB.minX ? boxA : boxB;
            AxisAlignedBB right = left == boxA ? boxB : boxA;
            double gapWidth = right.minX - left.maxX;
            if (gapWidth <= MIN_VALID_GAP_WIDTH) {
                return null;
            }
            double gapX = (left.maxX + right.minX) * 0.5D;
            double gapZ = ((boxA.minZ + boxA.maxZ) * 0.5D + (boxB.minZ + boxB.maxZ) * 0.5D) * 0.5D;
            BetterBlockPos anchor = boxA.minX <= boxB.minX ? barrierA : barrierB;
            return new EdgePortal(
                    barrierA,
                    barrierB,
                    anchor,
                    EnumFacing.EAST,
                    travelFacing,
                    boxA,
                    boxB,
                    gapWidth,
                    Math.max(boxA.maxY - boxA.minY, boxB.maxY - boxB.minY),
                    new Vec3d(gapX, y + 0.5D, gapZ));
        }

        if (barrierA.x == barrierB.x && Math.abs(barrierA.z - barrierB.z) == 1
                && travelFacing.getAxis() == EnumFacing.Axis.X) {
            AxisAlignedBB top = boxA.minZ <= boxB.minZ ? boxA : boxB;
            AxisAlignedBB bottom = top == boxA ? boxB : boxA;
            double gapWidth = bottom.minZ - top.maxZ;
            if (gapWidth <= MIN_VALID_GAP_WIDTH) {
                return null;
            }
            double gapX = ((boxA.minX + boxA.maxX) * 0.5D + (boxB.minX + boxB.maxX) * 0.5D) * 0.5D;
            double gapZ = (top.maxZ + bottom.minZ) * 0.5D;
            BetterBlockPos anchor = boxA.minZ <= boxB.minZ ? barrierA : barrierB;
            return new EdgePortal(
                    barrierA,
                    barrierB,
                    anchor,
                    EnumFacing.SOUTH,
                    travelFacing,
                    boxA,
                    boxB,
                    gapWidth,
                    Math.max(boxA.maxY - boxA.minY, boxB.maxY - boxB.minY),
                    new Vec3d(gapX, y + 0.5D, gapZ));
        }

        return null;
    }

    private static boolean isNarrowBarrier(IBlockState state, AxisAlignedBB localBox) {
        if (state == null || localBox == null) {
            return false;
        }
        Block block = state.getBlock();
        if (MovementHelper.avoidWalkingInto(block)
                || block == Blocks.LADDER
                || block == Blocks.VINE) {
            return false;
        }
        double widthX = localBox.maxX - localBox.minX;
        double widthZ = localBox.maxZ - localBox.minZ;
        return widthX < MIN_NARROW_DIMENSION || widthZ < MIN_NARROW_DIMENSION;
    }

    private static AxisAlignedBB getLocalCollisionBox(IBlockAccess access, BetterBlockPos pos, IBlockState state) {
        try {
            return state.getBoundingBox(access, new BlockPos(pos.x, pos.y, pos.z));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
