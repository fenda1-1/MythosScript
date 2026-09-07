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

package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.HumanLikeMovementConfig;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.path.IPathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourRuntimeHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class PlayerMovementInput extends MovementInput {

    private final InputOverrideHandler handler;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    public void updatePlayerMoveState() {
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;

        boolean desiredJump = handler.isInputForcedDown(Input.JUMP);
        boolean desiredSneak = handler.isInputForcedDown(Input.SNEAK);

        float desiredForward = 0.0F;
        float desiredStrafe = 0.0F;

        if (this.forwardKeyDown = handler.isInputForcedDown(Input.MOVE_FORWARD)) {
            desiredForward++;
        }

        if (this.backKeyDown = handler.isInputForcedDown(Input.MOVE_BACK)) {
            desiredForward--;
        }

        if (this.leftKeyDown = handler.isInputForcedDown(Input.MOVE_LEFT)) {
            desiredStrafe++;
        }

        if (this.rightKeyDown = handler.isInputForcedDown(Input.MOVE_RIGHT)) {
            desiredStrafe--;
        }

        boolean decoupleMovementFromVisualYaw = handler.baritone.getLookBehavior().shouldDecoupleMovementFromVisualYaw();
        float yawDifferenceDeg = decoupleMovementFromVisualYaw
                ? MathHelper.wrapDegrees(handler.getMovementYaw() - handler.getPlayerYaw())
                : 0.0F;

        if (decoupleMovementFromVisualYaw && (desiredForward != 0.0F || desiredStrafe != 0.0F)) {
            float yawDelta = (float) Math.toRadians(yawDifferenceDeg);
            float sin = MathHelper.sin(yawDelta);
            float cos = MathHelper.cos(yawDelta);
            float rawStrafe = desiredStrafe;
            float rawForward = desiredForward;

            // With freeLook active, path steering and the player's visible camera yaw are
            // intentionally decoupled. Rotate the desired path-space input into the player's
            // current local frame exactly once before handing it to Minecraft.
            desiredStrafe = rawStrafe * cos - rawForward * sin;
            desiredForward = rawStrafe * sin + rawForward * cos;
        }

        HumanLikeMovementController controller = HumanLikeMovementController.INSTANCE;
        HumanLikeMovementController.MovementState movementState;
        if (shouldBypassHumanLikeMovement()) {
            movementState = new HumanLikeMovementController.MovementState(
                    desiredForward,
                    desiredStrafe,
                    desiredJump,
                    desiredSneak);
        } else {
            movementState = controller.applyMovement(
                    desiredForward,
                    desiredStrafe,
                    desiredJump,
                    desiredSneak,
                    yawDifferenceDeg,
                    handler.ctx.player().posX,
                    handler.ctx.player().posY,
                    handler.ctx.player().posZ,
                    handler.ctx.player().onGround,
                    computeFinalApproachProgress(),
                    computeNarrowPassageFactor(),
                    computeStraightPathFactor(),
                    computeObstacleEdgeBias());
        }

        this.moveForward = movementState.moveForward;
        this.moveStrafe = movementState.moveStrafe;
        this.jump = movementState.jump;
        this.sneak = movementState.sneak;

        logParkourInput(desiredForward, desiredStrafe, desiredJump, desiredSneak, movementState);

        if (this.sneak && !controller.isEnabled()) {
            this.moveStrafe *= 0.3D;
            this.moveForward *= 0.3D;
        }
    }

    private void logParkourInput(float desiredForward, float desiredStrafe, boolean desiredJump, boolean desiredSneak,
            HumanLikeMovementController.MovementState movementState) {
        if (!ModConfig.isDebugFlagEnabled(DebugModule.BARITONE)) {
            return;
        }
        MovementParkour parkour = ParkourRuntimeHelper.getActiveParkourMovement(handler.baritone);
        if (parkour == null) {
            return;
        }
        Helper.HELPER.logDirect(String.format(
                "[DBG][parkour-input] phase=%s desiredF=%.3f desiredS=%.3f desiredJump=%s desiredSneak=%s outF=%.3f outS=%.3f outJump=%s outSneak=%s onGround=%s",
                parkour.getExecutionPhaseName(),
                desiredForward,
                desiredStrafe,
                desiredJump,
                desiredSneak,
                movementState.moveForward,
                movementState.moveStrafe,
                movementState.jump,
                movementState.sneak,
                handler.ctx.player() != null && handler.ctx.player().onGround));
    }

    private boolean shouldBypassHumanLikeMovement() {
        return ParkourRuntimeHelper.getActiveParkourMovement(handler.baritone) != null || isPrecisionRouteMovement();
    }

    private boolean isPrecisionRouteMovement() {
        IPathExecutor current = handler.baritone.getPathingBehavior().getCurrent();
        if (current == null || current.getPath() == null) {
            return false;
        }

        int movementIndex = current.getPosition();
        if (movementIndex < 0 || movementIndex >= current.getPath().movements().size()) {
            return false;
        }

        return current.getPath().movements().get(movementIndex) instanceof IRoutePointMovement;
    }

    private float computeFinalApproachProgress() {
        HumanLikeMovementConfig config = HumanLikeMovementConfig.INSTANCE;
        if (config == null) {
            return 0.0F;
        }
        config.normalize();

        if (config.finalApproachDistance <= 0.001F || handler.baritone == null || handler.ctx.player() == null) {
            return 0.0F;
        }

        Goal goal = handler.baritone.getPathingBehavior().getGoal();
        if (goal == null) {
            return 0.0F;
        }

        double distanceMetric;
        if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            if (goalPos == null) {
                return 0.0F;
            }
            double dx = handler.ctx.player().posX - (goalPos.getX() + 0.5D);
            double dz = handler.ctx.player().posZ - (goalPos.getZ() + 0.5D);
            double dy = handler.ctx.player().posY - goalPos.getY();
            distanceMetric = Math.sqrt(dx * dx + dz * dz + dy * dy * 0.35D);
        } else {
            distanceMetric = goal.heuristic(handler.ctx.playerFeet());
        }

        if (distanceMetric <= 0.0D) {
            return 1.0F;
        }

        float normalized = 1.0F - (float) (distanceMetric / Math.max(0.001F, config.finalApproachDistance));
        return MathHelper.clamp(normalized, 0.0F, 1.0F);
    }

    private float computeNarrowPassageFactor() {
        if (handler.ctx == null || handler.ctx.player() == null) {
            return 0.0F;
        }

        BetterBlockPos feet = handler.ctx.playerFeet();
        if (feet == null) {
            return 0.0F;
        }

        int blockedSides = 0;
        if (!isWalkThrough(feet.x + 1, feet.y, feet.z) || !isWalkThrough(feet.x + 1, feet.y + 1, feet.z)) {
            blockedSides++;
        }
        if (!isWalkThrough(feet.x - 1, feet.y, feet.z) || !isWalkThrough(feet.x - 1, feet.y + 1, feet.z)) {
            blockedSides++;
        }
        if (!isWalkThrough(feet.x, feet.y, feet.z + 1) || !isWalkThrough(feet.x, feet.y + 1, feet.z + 1)) {
            blockedSides++;
        }
        if (!isWalkThrough(feet.x, feet.y, feet.z - 1) || !isWalkThrough(feet.x, feet.y + 1, feet.z - 1)) {
            blockedSides++;
        }

        float factor = blockedSides / 4.0F;

        float yaw = handler.getMovementYaw();
        int forwardX = Math.abs(MathHelper.sin((float) Math.toRadians(yaw))) > 0.5F
                ? (MathHelper.sin((float) Math.toRadians(yaw)) > 0 ? -1 : 1)
                : 0;
        int forwardZ = Math.abs(MathHelper.cos((float) Math.toRadians(yaw))) > 0.5F
                ? (MathHelper.cos((float) Math.toRadians(yaw)) > 0 ? 1 : -1)
                : 0;

        if (forwardX != 0 || forwardZ != 0) {
            if (!isWalkThrough(feet.x + forwardX, feet.y, feet.z + forwardZ)
                    || !isWalkThrough(feet.x + forwardX, feet.y + 1, feet.z + forwardZ)) {
                factor = Math.max(factor, 0.9F);
            }
        }

        return MathHelper.clamp(factor, 0.0F, 1.0F);
    }

    private boolean isWalkThrough(int x, int y, int z) {
        return MovementHelper.canWalkThrough(handler.ctx, new BetterBlockPos(x, y, z));
    }

    private float computeStraightPathFactor() {
        IPathExecutor current = handler.baritone.getPathingBehavior().getCurrent();
        if (current == null || current.getPath() == null) {
            return 0.0F;
        }

        List<BetterBlockPos> positions = current.getPath().positions();
        int index = current.getPosition();
        if (index < 0 || index + 1 >= positions.size()) {
            return 0.0F;
        }

        int baseDirX = Integer.compare(positions.get(index + 1).x - positions.get(index).x, 0);
        int baseDirZ = Integer.compare(positions.get(index + 1).z - positions.get(index).z, 0);
        if (baseDirX == 0 && baseDirZ == 0) {
            return 0.0F;
        }

        int straightSteps = 0;
        int maxLookahead = Math.min(positions.size() - 2, index + 8);
        for (int i = index; i <= maxLookahead; i++) {
            int dirX = Integer.compare(positions.get(i + 1).x - positions.get(i).x, 0);
            int dirZ = Integer.compare(positions.get(i + 1).z - positions.get(i).z, 0);
            if (dirX == baseDirX && dirZ == baseDirZ) {
                straightSteps++;
            } else {
                break;
            }
        }

        return MathHelper.clamp((straightSteps - 1) / 4.0F, 0.0F, 1.0F);
    }

    private float computeObstacleEdgeBias() {
        if (handler.ctx == null || handler.ctx.player() == null) {
            return 0.0F;
        }

        BetterBlockPos feet = handler.ctx.playerFeet();
        if (feet == null) {
            return 0.0F;
        }

        float yaw = handler.getMovementYaw();
        float radians = (float) Math.toRadians(yaw);

        int forwardX = Math.abs(MathHelper.sin(radians)) > 0.5F
                ? (MathHelper.sin(radians) > 0 ? -1 : 1)
                : 0;
        int forwardZ = Math.abs(MathHelper.cos(radians)) > 0.5F
                ? (MathHelper.cos(radians) > 0 ? 1 : -1)
                : 0;

        if (forwardX == 0 && forwardZ == 0) {
            return 0.0F;
        }

        int leftX = forwardZ;
        int leftZ = -forwardX;
        int rightX = -forwardZ;
        int rightZ = forwardX;

        float leftBlocked = sampleBlockedScore(feet, leftX, leftZ, forwardX, forwardZ);
        float rightBlocked = sampleBlockedScore(feet, rightX, rightZ, forwardX, forwardZ);

        return MathHelper.clamp(leftBlocked - rightBlocked, -1.0F, 1.0F);
    }

    private float sampleBlockedScore(BetterBlockPos feet, int sideX, int sideZ, int forwardX, int forwardZ) {
        float blocked = 0.0F;
        if (isBlockedColumn(feet.x + sideX, feet.y, feet.z + sideZ)) {
            blocked += 0.65F;
        }
        if (isBlockedColumn(feet.x + sideX + forwardX, feet.y, feet.z + sideZ + forwardZ)) {
            blocked += 0.35F;
        }
        return blocked;
    }

    private boolean isBlockedColumn(int x, int y, int z) {
        return !isWalkThrough(x, y, z) || !isWalkThrough(x, y + 1, z);
    }
}
