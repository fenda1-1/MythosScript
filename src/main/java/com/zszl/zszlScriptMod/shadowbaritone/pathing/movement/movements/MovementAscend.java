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

package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

public class MovementAscend extends Movement {

    // At this distance the player's front edge is close to the raised block,
    // while still leaving enough room for the jump to begin before collision.
    private static final double NORMAL_ASCEND_JUMP_START_DISTANCE = 0.85D;

    private int ticksWithoutPlacement = 0;

    public MovementAscend(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, new BetterBlockPos[] { src.up(2), dest, dest.up() }, dest.down());
    }

    @Override
    public void reset() {
        super.reset();
        ticksWithoutPlacement = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.y, dest.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        BetterBlockPos prior = new BetterBlockPos(src.subtract(getDirection()).up()); // sometimes we back up to place
                                                                                      // the block, also sprint ascends,
                                                                                      // also skip descend to straight
                                                                                      // ascend
        return ImmutableSet.of(src,
                src.up(),
                dest,
                prior,
                prior.up());
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
        return cost(context, x, y, z, destX, y + 1, destZ);
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destY, int destZ) {
        int height = destY - y;
        if (height < 1 || height > context.routeHeightRange) {
            return COST_INF;
        }
        if (height > 1 && !hasContinuousStepSupport(context, destX, y, destZ, destY - 1)) {
            return COST_INF;
        }
        IBlockState toPlace = context.get(destX, destY - 1, destZ);
        double additionalPlacementCost = 0;
        if (!MovementHelper.canWalkOn(context, destX, destY - 1, destZ, toPlace)) {
            additionalPlacementCost = context.costOfPlacingAt(destX, destY - 1, destZ, toPlace);
            if (additionalPlacementCost >= COST_INF) {
                return COST_INF;
            }
            if (!MovementHelper.isReplaceable(destX, destY - 1, destZ, toPlace, context.bsi)) {
                return COST_INF;
            }
            boolean foundPlaceOption = false;
            for (int i = 0; i < 5; i++) {
                int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getFrontOffsetX();
                int againstY = destY - 1
                        + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getFrontOffsetY();
                int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getFrontOffsetZ();
                if (againstX == x && againstZ == z) { // we might be able to backplace now, but it doesn't matter
                                                      // because it will have been broken by the time we'd need to use
                                                      // it
                    continue;
                }
                if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                    foundPlaceOption = true;
                    break;
                }
            }
            if (!foundPlaceOption) { // didn't find a valid place =(
                return COST_INF;
            }
        }
        IBlockState srcUp2 = context.get(x, y + 2, z); // used lower down anyway
        if (context.get(x, y + 3, z).getBlock() instanceof BlockFalling
                && (MovementHelper.canWalkThrough(context, x, y + 1, z)
                        || !(srcUp2.getBlock() instanceof BlockFalling))) {// it would fall on us and possibly suffocate
                                                                           // us
            // HOWEVER, we assume that we're standing in the start position
            // that means that src and src.up(1) are both air
            // maybe they aren't now, but they will be by the time this starts
            // if the lower one is can't walk through and the upper one is falling, that
            // means that by standing on src
            // (the presupposition of this Movement)
            // we have necessarily already cleared the entire BlockFalling stack
            // on top of our head

            // as in, if we have a block, then two BlockFallings on top of it
            // and that block is x, y+1, z, and we'd have to clear it to even start this
            // movement
            // we don't need to worry about those BlockFallings because we've already
            // cleared them
            return COST_INF;
            // you may think we only need to check srcUp2, not srcUp
            // however, in the scenario where glitchy world gen where unsupported sand /
            // gravel generates
            // it's possible srcUp is AIR from the start, and srcUp2 is falling
            // and in that scenario, when we arrive and break srcUp2, that lets srcUp3 fall
            // on us and suffocate us
        }
        IBlockState srcDown = context.get(x, y - 1, z);
        if (srcDown.getBlock() == Blocks.LADDER || srcDown.getBlock() == Blocks.VINE) {
            return COST_INF;
        }
        IBlockState destFeet = context.get(destX, destY, destZ);
        if (destFeet.getBlock() instanceof BlockSnow
                && context.bsi.worldContainsLoadedChunk(destX, destZ)
                && destFeet.getValue(BlockSnow.LAYERS) >= 6) {
            return COST_INF;
        }
        // we can jump from soul sand, but not from a bottom slab
        boolean jumpingFromBottomSlab = MovementHelper.isBottomSlab(srcDown);
        boolean jumpingToBottomSlab = MovementHelper.isBottomSlab(toPlace);
        if (jumpingFromBottomSlab && !jumpingToBottomSlab) {
            return COST_INF;// the only thing we can ascend onto from a bottom slab is another bottom slab
        }
        double walk;
        if (jumpingToBottomSlab) {
            if (jumpingFromBottomSlab) {
                walk = Math.max(JUMP_ONE_BLOCK_COST, WALK_ONE_BLOCK_COST); // we hit space immediately on entering this
                                                                           // action
                walk += context.jumpPenalty;
            } else {
                walk = WALK_ONE_BLOCK_COST; // we don't hit space we just walk into the slab
            }
        } else {
            // jumpingFromBottomSlab must be false
            if (toPlace.getBlock() == Blocks.SOUL_SAND) {
                walk = WALK_ONE_OVER_SOUL_SAND_COST;
            } else {
                walk = Math.max(JUMP_ONE_BLOCK_COST, WALK_ONE_BLOCK_COST);
            }
            walk += context.jumpPenalty;
        }

        if (height > 1) {
            walk *= height;
        }

        double totalCost = walk + additionalPlacementCost;
        // start with srcUp2 since we already have its state
        // includeFalling isn't needed because of the falling check above -- if srcUp3
        // is falling we will have already exited with COST_INF if we'd actually have to
        // break it
        // The target client allows movement beneath a fence above the launch block.
        // Treat it as overhead clearance rather than a block that must be mined.
        if (!(srcUp2.getBlock() instanceof BlockFence)) {
            totalCost += MovementHelper.getMiningDurationTicks(context, x, y + 2, z, srcUp2, false);
        }
        if (totalCost >= COST_INF) {
            return COST_INF;
        }
        totalCost += MovementHelper.getMiningDurationTicks(context, destX, destY, destZ, false);
        if (totalCost >= COST_INF) {
            return COST_INF;
        }
        IBlockState destHead = context.get(destX, destY + 1, destZ);
        if (!(destHead.getBlock() instanceof BlockFence)) {
            totalCost += MovementHelper.getMiningDurationTicks(context, destX, destY + 1, destZ, destHead, true);
        }
        return totalCost;
    }

    /**
     * Extended ascends model the player's step height, rather than a jump to an
     * isolated platform. Require a continuous supporting column between the
     * source feet and the destination floor so floating platforms are not
     * selected as high-step routes. The one-block case intentionally skips this
     * check and retains Baritone's normal jump/step behavior.
     */
    private static boolean hasContinuousStepSupport(CalculationContext context, int x, int fromY, int z,
            int supportYExclusive) {
        for (int supportY = fromY; supportY < supportYExclusive; supportY++) {
            IBlockState support = context.get(x, supportY, z);
            if (support == null
                    || support.getMaterial().isLiquid()
                    || support.getBlock() == Blocks.LADDER
                    || support.getBlock() == Blocks.VINE
                    || !MovementHelper.canWalkOn(context, x, supportY, z, support)
                    || MovementHelper.canWalkThrough(context, x, supportY, z, support)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public MovementState updateState(MovementState state) {
        BlockPos pathFeet = logicalPlayerFeet();
        if (pathFeet.getY() < src.y) {
            // this check should run even when in preparing state (breaking blocks)
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        super.updateState(state);
        // TODO incorporate some behavior from ActionClimb (specifically how it waited
        // until it was at most 1.2 blocks away before starting to jump
        // for efficiency in ascending minimal height staircases, which is just repeated
        // MovementAscend, so that it doesn't bonk its head on the ceiling repeatedly)
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (pathFeet.equals(dest) || pathFeet.equals(dest.add(getDirection().down()))) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        IBlockState jumpingOnto = BlockStateInterface.get(ctx, positionToPlace);
        if (!MovementHelper.canWalkOn(ctx, positionToPlace, jumpingOnto)) {
            ticksWithoutPlacement++;
            if (MovementHelper.attemptToPlaceABlock(state, baritone, dest.down(), false,
                    true) == PlaceResult.READY_TO_PLACE) {
                state.setInput(Input.SNEAK, true);
                if (ctx.player().isSneaking()) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
            if (ticksWithoutPlacement > 10) {
                // After 10 ticks without placement, we might be standing in the way, move back
                state.setInput(Input.MOVE_BACK, true);
            }

            return state;
        }
        MovementHelper.moveTowards(ctx, state, dest);
        if (MovementHelper.isBottomSlab(jumpingOnto)
                && !MovementHelper.isBottomSlab(BlockStateInterface.get(ctx, src.down()))) {
            return state; // don't jump while walking from a non double slab into a bottom slab
        }

        if (Baritone.settings().assumeStep.value || pathFeet.equals(src.up())) {
            // no need to hit space if we're already jumping
            return state;
        }

        int xAxis = Math.abs(src.getX() - dest.getX()); // either 0 or 1
        int zAxis = Math.abs(src.getZ() - dest.getZ()); // either 0 or 1
        double flatDistToNext = xAxis * Math.abs((dest.getX() + 0.5D) - ctx.player().posX)
                + zAxis * Math.abs((dest.getZ() + 0.5D) - ctx.player().posZ);
        double sideDist = zAxis * Math.abs((dest.getX() + 0.5D) - ctx.player().posX)
                + xAxis * Math.abs((dest.getZ() + 0.5D) - ctx.player().posZ);

        double lateralMotion = xAxis * ctx.player().motionZ + zAxis * ctx.player().motionX;
        if (Math.abs(lateralMotion) > 0.1) {
            return state;
        }

        // The executor's sprint-ascend shortcut already observes this setting.
        // Keep the movement-level early jump behind the same setting; otherwise
        // disabling sprintAscends still makes the player jump one block early.
        if (Baritone.settings().sprintAscends.value && headBonkClear()) {
            return state.setInput(Input.JUMP, true);
        }

        if (flatDistToNext > NORMAL_ASCEND_JUMP_START_DISTANCE || sideDist > 0.2) {
            return state;
        }

        // Once we are pointing the right way and moving, start jumping
        // This is slightly more efficient because otherwise we might start jumping
        // before moving, and fall down without moving onto the block we want to jump
        // onto
        // Also wait until we are close enough, because we might jump and hit our head
        // on an adjacent block
        return state.setInput(Input.JUMP, true);
    }

    public boolean headBonkClear() {
        BetterBlockPos startUp = src.up(2);
        for (int i = 0; i < 4; i++) {
            BetterBlockPos check = startUp.offset(EnumFacing.getHorizontal(i));
            if (!MovementHelper.canWalkThrough(ctx, check)
                    && !(BlockStateInterface.getBlock(ctx, check) instanceof BlockFence)) {
                // We might bonk our head
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we had to place, don't allow pause
        return state.getStatus() != MovementStatus.RUNNING || ticksWithoutPlacement == 0;
    }
}
