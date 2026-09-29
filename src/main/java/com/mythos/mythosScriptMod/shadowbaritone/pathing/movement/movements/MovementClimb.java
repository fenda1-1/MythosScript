package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.*;
import net.minecraft.block.BlockVine;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import java.util.Set;

/** A supported vine transfer, including brief airborne corner crossings. */
public final class MovementClimb extends Movement {
    private boolean cornerReady;
    public MovementClimb(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, new BetterBlockPos[0]);
    }

    public static double cost(CalculationContext context, BetterBlockPos src, BetterBlockPos dest) {
        if (!context.allowParkour || !com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().allowVines.value
                || context.getBlock(src.x, src.y, src.z) != Blocks.VINE
                || !MovementHelper.canWalkThrough(context, dest.x, dest.y, dest.z)
                || !MovementHelper.canWalkThrough(context, dest.x, dest.y + 1, dest.z)) return COST_INF;
        boolean vine = context.getBlock(dest.x, dest.y, dest.z) == Blocks.VINE;
        if (!vine && (dest.y != src.y + 1
                || Math.abs(dest.x-src.x)+Math.abs(dest.z-src.z) != 1
                || !context.get(dest.x, dest.y-1, dest.z).isFullCube())) return COST_INF;
        if (dest.y > src.y && wall(context, src) == null) return COST_INF;
        if (src.x != dest.x && src.z != dest.z
                && (dest.y != src.y || context.getBlock(dest.x, dest.y, dest.z) != Blocks.VINE
                || wall(context, src) == null)) return COST_INF;
        return dest.y > src.y ? 12 : 8 * Math.hypot(dest.x-src.x, dest.z-src.z);
    }

    private static EnumFacing wall(CalculationContext context, BetterBlockPos pos) {
        net.minecraft.block.state.IBlockState state = context.get(pos.x, pos.y, pos.z);
        if (state.getBlock() == Blocks.VINE) {
            for (EnumFacing face : EnumFacing.HORIZONTALS)
                if (state.getValue(BlockVine.getPropertyFor(face))
                        && (context.get(pos.offset(face)).isFullCube()
                        || context.get(pos.offset(face).up()).isFullCube())) return face;
        }
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            if (context.get(pos.x + face.getFrontOffsetX(), pos.y, pos.z + face.getFrontOffsetZ()).isFullCube()
                    || context.get(pos.x + face.getFrontOffsetX(), pos.y + 1, pos.z + face.getFrontOffsetZ()).isFullCube())
                return face;
        }
        return null;
    }

    @Override public double calculateCost(CalculationContext context) { return cost(context, src, dest); }
    @Override protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> positions = ImmutableSet.builder();
        // A corner transfer briefly crosses an air cell and rises above the
        // logical vine node. Neither is a path deviation while catching the vine.
        for (int x = Math.min(src.x, dest.x); x <= Math.max(src.x, dest.x); x++)
            for (int z = Math.min(src.z, dest.z); z <= Math.max(src.z, dest.z); z++)
                for (int y = src.y; y <= Math.max(src.y, dest.y) + 1; y++)
                    positions.add(new BetterBlockPos(x, y, z));
        return positions.build();
    }

    @Override public void reset() {
        super.reset();
        cornerReady = false;
    }

    @Override public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) return state;
        state.setInput(Input.SNEAK, true);
        state.setInput(Input.SPRINT, false);
        double dx = dest.x + .5 - ctx.player().posX;
        double dz = dest.z + .5 - ctx.player().posZ;
        boolean exit = ctx.world().getBlockState(dest).getBlock() != Blocks.VINE;
        if (exit && ctx.player().onGround && ctx.playerFeet().equals(dest))
            return state.setStatus(MovementStatus.SUCCESS);
        // Height alone is not arrival. A vine climb only finishes after the body
        // is centered; otherwise the next move starts while still hanging on the source.
        boolean inDestColumn = ctx.playerFeet().x == dest.x && ctx.playerFeet().z == dest.z;
        boolean inSourceColumn = ctx.playerFeet().x == src.x && ctx.playerFeet().z == src.z;
        if (!exit && ctx.player().isOnLadder() && inDestColumn
                && ctx.player().posY >= dest.y
                && Math.hypot(dx, dz) < .2 && Math.hypot(ctx.player().motionX, ctx.player().motionZ) < .03)
            return state.setStatus(MovementStatus.SUCCESS);
        if (ctx.player().posY < src.y - .6) return state.setStatus(MovementStatus.UNREACHABLE);
        if (src.x != dest.x && src.z != dest.z && !exit) {
            int sx = dest.x > src.x ? 1 : -1;
            int sz = dest.z > src.z ? 1 : -1;
            double edgeX = sx > 0 ? src.x + 1 : src.x;
            double edgeZ = sz > 0 ? src.z + 1 : src.z;
            boolean inDest = inDestColumn;
            double aimX = inDest ? dest.x + .5 : edgeX - sx * .08;
            double aimZ = inDest ? dest.z + .5 : edgeZ - sz * .08;
            boolean atCorner = Math.abs(ctx.player().posX - (edgeX - sx * .08)) < .16
                    && Math.abs(ctx.player().posZ - (edgeZ - sz * .08)) < .16;
            if (!inDest && inSourceColumn && !cornerReady) {
                EnumFacing face = wall(new CalculationContext(baritone, false), src);
                if (face == null) return state.setStatus(MovementStatus.UNREACHABLE);
                if (ctx.player().posY < src.y + .65) {
                    state.setInput(Input.JUMP, true);
                    MovementHelper.moveForwardWithRotation(ctx, state,
                            new Vec3d(face.getFrontOffsetX(), 0, face.getFrontOffsetZ()));
                } else if (!atCorner) {
                    MovementHelper.moveForwardWithRotation(ctx, state, new Vec3d(
                            aimX - ctx.player().posX - ctx.player().motionX * 4,
                            0, aimZ - ctx.player().posZ - ctx.player().motionZ * 4));
                } else {
                    cornerReady = true;
                }
                return state;
            }
            dx = (inDest ? dest.x + .5 : edgeX + sx * .25) - ctx.player().posX;
            dz = (inDest ? dest.z + .5 : edgeZ + sz * .25) - ctx.player().posZ;
        }
        // Once the destination's height is reached, transfer horizontally.
        // Testing !onGround here keeps a hanging player pushing the source wall
        // forever, even after climbing past the destination.
        boolean climbing = (dest.y > src.y || exit) && ctx.player().posY < dest.y + .15;
        if (climbing && inSourceColumn) {
            EnumFacing face = wall(new CalculationContext(baritone, false), src);
            if (face == null) return state.setStatus(MovementStatus.UNREACHABLE);
            state.setInput(Input.JUMP, true);
            MovementHelper.moveForwardWithRotation(ctx, state,
                    new Vec3d(face.getFrontOffsetX() + (src.x + .5 - ctx.player().posX
                            - ctx.player().motionX * 4) * (1 - Math.abs(face.getFrontOffsetX())), 0,
                            face.getFrontOffsetZ() + (src.z + .5 - ctx.player().posZ
                            - ctx.player().motionZ * 4) * (1 - Math.abs(face.getFrontOffsetZ()))));
            return state;
        }
        if (climbing) {
            EnumFacing face = wall(new CalculationContext(baritone, false), ctx.playerFeet());
            if (exit) face = EnumFacing.getFacingFromVector(dest.x-src.x, 0, dest.z-src.z);
            if (face != null) {
                dx = face.getFrontOffsetX();
                dz = face.getFrontOffsetZ();
            }
            state.setInput(Input.JUMP, true);
        } else {
            dx -= ctx.player().motionX * 4;
            dz -= ctx.player().motionZ * 4;
        }
        if (Math.hypot(dx, dz) > .035)
            MovementHelper.moveForwardWithRotation(ctx, state, new Vec3d(dx, 0, dz));
        return state;
    }
}
