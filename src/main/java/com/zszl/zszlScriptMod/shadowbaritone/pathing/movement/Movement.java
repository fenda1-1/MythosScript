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

package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.*;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public abstract class Movement implements IMovement, MovementHelper {

    public static final EnumFacing[] HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP = { EnumFacing.NORTH,
            EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.DOWN };

    protected final IBaritone baritone;
    protected final IPlayerContext ctx;

    private MovementState currentState = new MovementState().setStatus(MovementStatus.PREPPING);

    protected final BetterBlockPos src;

    protected final BetterBlockPos dest;

    /**
     * The positions that need to be broken before this movement can ensue
     */
    protected final BetterBlockPos[] positionsToBreak;

    /**
     * The position where we need to place a block before this movement can ensue
     */
    protected final BetterBlockPos positionToPlace;

    private Double cost;

    public List<BlockPos> toBreakCached = null;
    public List<BlockPos> toPlaceCached = null;
    public List<BlockPos> toWalkIntoCached = null;

    private Set<BetterBlockPos> validPositionsCached = null;

    private Boolean calculatedWhileLoaded;

    protected Movement(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos[] toBreak,
            BetterBlockPos toPlace) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        this.src = src;
        this.dest = dest;
        this.positionsToBreak = toBreak;
        this.positionToPlace = toPlace;
    }

    protected Movement(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos[] toBreak) {
        this(baritone, src, dest, toBreak, null);
    }

    public double getCost() throws NullPointerException {
        return cost;
    }

    public double getCost(CalculationContext context) {
        if (cost == null) {
            cost = calculateCost(context);
        }
        return cost;
    }

    public abstract double calculateCost(CalculationContext context);

    public double recalculateCost(CalculationContext context) {
        cost = null;
        return getCost(context);
    }

    public void override(double cost) {
        this.cost = cost;
    }

    protected abstract Set<BetterBlockPos> calculateValidPositions();

    public Set<BetterBlockPos> getValidPositions() {
        if (validPositionsCached == null) {
            validPositionsCached = calculateValidPositions();
            Objects.requireNonNull(validPositionsCached);
        }
        return validPositionsCached;
    }

    protected boolean playerInValidPosition() {
        BlockPos feet = logicalPlayerFeet();
        return getValidPositions().contains(feet)
                || getValidPositions().contains(((PathingBehavior) baritone.getPathingBehavior()).pathStart());
    }

    /**
     * Six or seven snow layers are treated as a full block by the planner, while
     * Minecraft still reports the player's physical feet inside the snow block
     * because its collision top is below the next integer Y.  Align the runtime
     * coordinate with the logical path node when that shifted position is valid.
     */
    protected BlockPos logicalPlayerFeet() {
        BlockPos feet = ctx.playerFeet();
        if (getValidPositions().contains(feet)) {
            return feet;
        }
        IBlockState state = BlockStateInterface.get(ctx, feet);
        if (state.getBlock() instanceof BlockSnow
                && state.getValue(BlockSnow.LAYERS) >= 6
                && MovementHelper.canWalkOn(ctx, feet)
                && getValidPositions().contains(feet.up())) {
            return feet.up();
        }
        return feet;
    }

    /**
     * Handles the execution of the latest Movement
     * State, and offers a Status to the calling class.
     *
     * @return Status
     */
    @Override
    public MovementStatus update() {
        boolean flightPathing = Baritone.settings().allowFlightPathing.value;
        if (!flightPathing) {
            ctx.player().capabilities.isFlying = false;
        }
        currentState = updateState(currentState);
        if (shouldAutoSwimInLiquid() && MovementHelper.isLiquid(ctx, ctx.playerFeet())) {
            currentState.setInput(Input.JUMP, true);
        }
        if (ctx.player().isEntityInsideOpaqueBlock()) {
            ctx.getSelectedBlock()
                    .ifPresent(pos -> MovementHelper.switchToBestToolFor(ctx, BlockStateInterface.get(ctx, pos)));
            currentState.setInput(Input.CLICK_LEFT, true);
        }

        // If the movement target has to force the new rotations, or we aren't using
        // silent move, then force the rotations
        currentState.getTarget().getRotation().ifPresent(rotation -> baritone.getLookBehavior().updateTarget(
                rotation,
                currentState.getTarget().hasToForceRotations()));
        baritone.getInputOverrideHandler().clearAllKeys();
        currentState.getInputStates().forEach((input, forced) -> {
            baritone.getInputOverrideHandler().setInputForceState(input, forced);
        });
        currentState.getInputStates().clear();

        // If the current status indicates a completed movement
        if (currentState.getStatus().isComplete()) {
            baritone.getInputOverrideHandler().clearAllKeys();
        }

        return currentState.getStatus();
    }

    protected boolean shouldAutoSwimInLiquid() {
        return true;
    }

    protected boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING) {
            return true;
        }
        boolean somethingInTheWay = false;
        for (BetterBlockPos blockPos : preparationBreakPositions()) {
            if (!ctx.world()
                    .getEntitiesWithinAABB(EntityFallingBlock.class,
                            new AxisAlignedBB(0, 0, 0, 1, 1.1, 1).offset(blockPos))
                    .isEmpty() && Baritone.settings().pauseMiningForFallingBlocks.value) {
                return false;
            }
            if (!MovementHelper.canWalkThrough(ctx, blockPos)
                    && !(BlockStateInterface.getBlock(ctx, blockPos) instanceof BlockLiquid)) { // can't break liquid,
                                                                                                // so don't try
                IBlockState blockState = BlockStateInterface.get(ctx, blockPos);
                if (!Baritone.settings().allowBreak.value
                        && !Baritone.settings().allowBreakAnyway.value.contains(blockState.getBlock())) {
                    if (isPassableFenceClearance(blockPos, blockState)) {
                        // This client permits the player to move under a fence hanging
                        // above a route block. Do not turn that clearance probe into
                        // a mandatory break when mining is disabled.
                        continue;
                    }
                    // Head-clearance checks run while executing a movement. They must
                    // obey the same no-break policy used while calculating the path.
                    state.setStatus(MovementStatus.UNREACHABLE);
                    return true;
                }
                somethingInTheWay = true;
                MovementHelper.switchToBestToolFor(ctx, blockState);
                Optional<Rotation> reachable = RotationUtils.reachable(ctx, blockPos,
                        ctx.playerController().getBlockReachDistance());
                if (reachable.isPresent()) {
                    Rotation rotTowardsBlock = reachable.get();
                    state.setTarget(new MovementState.MovementTarget(rotTowardsBlock, true));
                    if (ctx.isLookingAt(blockPos) || ctx.playerRotations().isReallyCloseTo(rotTowardsBlock)) {
                        state.setInput(Input.CLICK_LEFT, true);
                    }
                    return false;
                }
                // get rekt minecraft
                // i'm doing it anyway
                // i dont care if theres snow in the way!!!!!!!
                // you dont own me!!!!
                state.setTarget(new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.playerHead(),
                        VecUtils.getBlockPosCenter(blockPos), ctx.playerRotations()), true));
                // don't check selectedblock on this one, this is a fallback when we can't see
                // any face directly, it's intended to be breaking the "incorrect" block
                state.setInput(Input.CLICK_LEFT, true);
                return false;
            }
        }
        if (somethingInTheWay) {
            // There's a block or blocks that we can't walk through, but we have no target
            // rotation to reach any
            // So don't return true, actually set state to unreachable
            state.setStatus(MovementStatus.UNREACHABLE);
            return true;
        }
        return true;
    }

    /**
     * Returns whether a fence is only occupying a head-clearance cell for this
     * movement.  Fences are not full cubes, and the normal client movement can
     * reach the next block/jump while leaving that fence in place.  Keeping this
     * test in the movement base class makes preparation and the cached
     * to-break list agree with the cost calculation.
     */
    protected final boolean isPassableFenceClearance(BetterBlockPos blockPos, IBlockState blockState) {
        if (!(blockState.getBlock() instanceof BlockFence)) {
            return false;
        }
        if (dest.y > src.y && (blockPos.equals(src.up(2)) || blockPos.equals(dest.up()))) {
            return true;
        }
        if (dest.y > src.y) {
            BetterBlockPos feet = null;
            try {
                feet = ctx.playerFeet();
            } catch (Throwable ignored) {
            }
            if (getValidPositions().contains(feet) && blockPos.equals(feet.up(2))) {
                // MovementAscend can begin one route node before src while
                // preserving the same straight line. The runtime clearance
                // probe uses the player's actual feet position in that case,
                // so it must receive the same fence exception as src.up(2).
                return true;
            }
        }
        // A flat traverse can move under the overhead fence before the next
        // movement ascends. Treat only the head-clearance position as passable;
        // a fence in the destination feet block remains an obstacle.
        return dest.y == src.y && blockPos.equals(dest.up());
    }

    @Override
    public boolean safeToCancel() {
        return safeToCancel(currentState);
    }

    protected boolean safeToCancel(MovementState currentState) {
        return true;
    }

    @Override
    public BetterBlockPos getSrc() {
        return src;
    }

    @Override
    public BetterBlockPos getDest() {
        return dest;
    }

    @Override
    public void reset() {
        currentState = new MovementState().setStatus(MovementStatus.PREPPING);
    }

    /**
     * Calculate latest movement state. Gets called once a tick.
     *
     * @param state The current state
     * @return The new state
     */
    public MovementState updateState(MovementState state) {
        if (!prepared(state)) {
            return state.setStatus(MovementStatus.PREPPING);
        } else if (state.getStatus() == MovementStatus.PREPPING) {
            state.setStatus(MovementStatus.WAITING);
        }

        if (state.getStatus() == MovementStatus.WAITING) {
            state.setStatus(MovementStatus.RUNNING);
        }

        return state;
    }

    @Override
    public BlockPos getDirection() {
        return getDest().subtract(getSrc());
    }

    public void checkLoadedChunk(CalculationContext context) {
        calculatedWhileLoaded = context.bsi.worldContainsLoadedChunk(dest.x, dest.z);
    }

    @Override
    public boolean calculatedWhileLoaded() {
        return calculatedWhileLoaded;
    }

    @Override
    public void resetBlockCache() {
        toBreakCached = null;
        toPlaceCached = null;
        toWalkIntoCached = null;
    }

    public List<BlockPos> toBreak(BlockStateInterface bsi) {
        if (toBreakCached != null) {
            return toBreakCached;
        }
        List<BlockPos> result = new ArrayList<>();
        for (BetterBlockPos positionToBreak : preparationBreakPositions()) {
            if (!MovementHelper.canWalkThrough(bsi, positionToBreak.x, positionToBreak.y, positionToBreak.z)) {
                IBlockState state = bsi.get0(positionToBreak);
                if (!Baritone.settings().allowBreak.value && isPassableFenceClearance(positionToBreak, state)) {
                    // This is clearance for the jump, not a block that the
                    // executor is expected to mine.  In particular, leaving it
                    // in toBreakCached prevents the straight traverse -> ascend
                    // hand-off and makes the player stop below the fence.
                    continue;
                }
                result.add(positionToBreak);
            }
        }
        toBreakCached = result;
        return result;
    }

    public List<BlockPos> toPlace(BlockStateInterface bsi) {
        if (toPlaceCached != null) {
            return toPlaceCached;
        }
        List<BlockPos> result = new ArrayList<>();
        if (positionToPlace != null
                && !MovementHelper.canWalkOn(bsi, positionToPlace.x, positionToPlace.y, positionToPlace.z)) {
            result.add(positionToPlace);
        }
        toPlaceCached = result;
        return result;
    }

    public List<BlockPos> toWalkInto(BlockStateInterface bsi) { // overridden by movementdiagonal
        if (toWalkIntoCached == null) {
            toWalkIntoCached = new ArrayList<>();
        }
        return toWalkIntoCached;
    }

    public BlockPos[] toBreakAll() {
        return positionsToBreak;
    }

    protected List<BetterBlockPos> preparationBreakPositions() {
        LinkedHashSet<BetterBlockPos> result = new LinkedHashSet<>();
        addUpwardHeadClearance(result);
        Collections.addAll(result, positionsToBreak);
        return new ArrayList<>(result);
    }

    private void addUpwardHeadClearance(Set<BetterBlockPos> result) {
        if (dest.y <= src.y) {
            return;
        }

        result.add(src.up(2));
        result.add(dest.up());

        BetterBlockPos feet = null;
        try {
            feet = ctx.playerFeet();
        } catch (Throwable ignored) {
        }
        if (feet != null && getValidPositions().contains(feet) && dest.y > feet.y) {
            result.add(feet.up(2));
        }
    }
}
