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

package com.zszl.zszlScriptMod.shadowbaritone.behavior;

import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.behavior.ILookBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.api.behavior.look.IAimProcessor;
import com.zszl.zszlScriptMod.shadowbaritone.api.behavior.look.ITickableAimProcessor;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.*;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.path.IPathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.IPlayerContext;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.RotationUtils;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.look.ForkableRandom;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourRuntimeHelper;
import com.zszl.zszlScriptMod.shadowbaritone.utils.HumanLikeMovementController;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class LookBehavior extends Behavior implements ILookBehavior {

    private static final float MIN_SMOOTH_LOOK_MAX_TURN_STEP = 0.5F;
    private static final float MAX_SMOOTH_LOOK_MAX_TURN_STEP = 60.0F;
    private static final float DEFAULT_SMOOTH_LOOK_MAX_TURN_STEP = 24.0F;
    private static final float MIN_SMOOTH_LOOK_PITCH_SPEED = 0.6F;
    private static final float SMOOTH_LOOK_PITCH_SPEED_RATIO = 0.62F;
    private static final float SMOOTH_LOOK_OVERSHOOT_MAX_YAW = 2.15F;
    private static final float SMOOTH_LOOK_OVERSHOOT_MAX_PITCH = 0.95F;

    /**
     * The current look target, may be {@code null}.
     */
    private Target target;

    /**
     * The rotation known to the server. Returned by {@link #getEffectiveRotation()}
     * for use in {@link IPlayerContext}.
     */
    private Rotation serverRotation;

    /**
     * The rotation Baritone wants movement/pathing math to use.
     *
     * This must stay independent from the player's visual camera direction, otherwise
     * movement input selection (W/A/S/D) will drift as soon as the player turns the
     * screen away from the planned path.
     */
    private Rotation movementRotation;

    /**
     * The last player rotation. Used to restore the player's angle when using free
     * look.
     *
     * @see Settings#freeLook
     */
    private Rotation prevRotation;
    private Rotation visualTargetThisTick;
    private boolean killAuraVisualControlThisTick;

    private final AimProcessor processor;
    private int junctionGlanceTicksRemaining;
    private float junctionGlanceYawOffset;
    private long lastJunctionGlanceKey;

    public LookBehavior(Baritone baritone) {
        super(baritone);
        this.processor = new AimProcessor(baritone.getPlayerContext());
        this.junctionGlanceTicksRemaining = 0;
        this.junctionGlanceYawOffset = 0.0F;
        this.lastJunctionGlanceKey = Long.MIN_VALUE;
    }

    @Override
    public void updateTarget(Rotation rotation, boolean blockInteract) {
        Target.Mode resolvedMode = Target.Mode.resolve(ctx, blockInteract);
        this.target = new Target(rotation, resolvedMode, blockInteract);
        this.movementRotation = rotation;
        if (blockInteract
                && resolvedMode == Target.Mode.CLIENT
                && ctx.player() != null) {
            Rotation actual = this.processor.peekRotation(rotation);
            ctx.player().rotationYaw = actual.getYaw();
            ctx.player().rotationPitch = actual.getPitch();
        }
    }

    @Override
    public IAimProcessor getAimProcessor() {
        return this.processor;
    }

    @Override
    public boolean shouldDecoupleMovementFromVisualYaw() {
        if (ctx.player() == null || ctx.player().isElytraFlying()) {
            return false;
        }
        if (!baritone.getPathingBehavior().isPathing() || !Baritone.settings().freeLook.value) {
            return false;
        }
        if (ParkourRuntimeHelper.isPrecisionCriticalParkourPhase(baritone)) {
            // Precision parkour relies on the physics yaw override, so free-look input
            // remapping must stay out of the way for this window.
            return false;
        }
        if (this.target != null) {
            return shouldDecoupleMovementFromVisualYaw(this.target.mode, this.target.blockInteract);
        }
        return this.movementRotation != null;
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            this.processor.tick();
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.target == null) {
            return;
        }
        switch (event.getState()) {
            case PRE: {
                Optional<Rotation> killAuraVisualTarget = getKillAuraVisualTarget();
                this.killAuraVisualControlThisTick = killAuraVisualTarget.isPresent();
                boolean precisionCriticalParkour = ParkourRuntimeHelper.isPrecisionCriticalParkourPhase(baritone);
                boolean humanLikeVisualControl = !this.killAuraVisualControlThisTick
                        && HumanLikeMovementController.INSTANCE.isEnabled()
                        && baritone.getPathingBehavior().isPathing()
                        && !precisionCriticalParkour;
                boolean smoothLookVisualControl = !this.killAuraVisualControlThisTick
                        && !humanLikeVisualControl
                        && isSmoothLookEnabled();

                if (this.target.mode == Target.Mode.NONE
                        && !humanLikeVisualControl
                        && !this.killAuraVisualControlThisTick
                        && !smoothLookVisualControl) {
                    // Just return for PRE, we still want to set target to null on POST
                    return;
                }

                boolean temporaryBlockInteractRotation = this.target.mode == Target.Mode.SERVER
                        && this.target.blockInteract;
                if (this.target.mode == Target.Mode.CLIENT
                        || temporaryBlockInteractRotation
                        || humanLikeVisualControl
                        || this.killAuraVisualControlThisTick
                        || smoothLookVisualControl) {
                    this.prevRotation = new Rotation(ctx.player().rotationYaw, ctx.player().rotationPitch);
                    if (this.killAuraVisualControlThisTick) {
                        this.visualTargetThisTick = killAuraVisualTarget.orElse(this.prevRotation);
                        ctx.player().rotationYaw = this.visualTargetThisTick.getYaw();
                        ctx.player().rotationPitch = this.visualTargetThisTick.getPitch();
                    } else if (humanLikeVisualControl) {
                        this.visualTargetThisTick = getHumanLikeVisualTarget(this.target.rotation);
                        final Rotation actual = this.processor.peekRotation(this.visualTargetThisTick);
                        HumanLikeMovementController.RotationState smoothed = HumanLikeMovementController.INSTANCE
                                .smoothRotation(
                                        this.prevRotation.getYaw(),
                                        this.prevRotation.getPitch(),
                                        actual.getYaw(),
                                        actual.getPitch());
                        ctx.player().rotationYaw = smoothed.yaw;
                        ctx.player().rotationPitch = smoothed.pitch;
                    } else if (temporaryBlockInteractRotation) {
                        final Rotation actual = this.processor.peekRotation(this.target.rotation);
                        this.visualTargetThisTick = actual;
                        ctx.player().rotationYaw = actual.getYaw();
                        ctx.player().rotationPitch = actual.getPitch();
                    } else if (this.target.mode == Target.Mode.CLIENT) {
                        final Rotation actual = this.processor.peekRotation(this.target.rotation);
                        this.visualTargetThisTick = actual;
                        ctx.player().rotationYaw = actual.getYaw();
                        ctx.player().rotationPitch = actual.getPitch();
                    } else {
                        this.visualTargetThisTick = this.target.rotation.normalizeAndClamp();
                    }
                }
                break;
            }
            case POST: {
                // Reset the player's rotations back to their original values
                if (this.prevRotation != null) {
                    boolean precisionCriticalParkour = ParkourRuntimeHelper.isPrecisionCriticalParkourPhase(baritone);
                    boolean humanLikeVisualControl = !this.killAuraVisualControlThisTick
                            && HumanLikeMovementController.INSTANCE.isEnabled()
                            && baritone.getPathingBehavior().isPathing()
                            && !precisionCriticalParkour;

                    if (humanLikeVisualControl || this.killAuraVisualControlThisTick) {
                        // PRE 阶段已经把本地视角平滑到当前拟真目标，这里不要再叠一层
                        // 额外平滑，否则会出现同一 tick 内来回改角度的抖动感。
                    } else if (ctx.player().isElytraFlying() ? Baritone.settings().elytraSmoothLook.value
                            : Baritone.settings().smoothLook.value) {
                        Rotation visualTarget = this.visualTargetThisTick != null ? this.visualTargetThisTick
                                : this.target.rotation;
                        Rotation smoothedRotation = smoothLookLikeKillAura(this.prevRotation, visualTarget);
                        ctx.player().rotationYaw = smoothedRotation.getYaw();
                        ctx.player().rotationPitch = smoothedRotation.getPitch();
                    } else if (this.target.mode == Target.Mode.SERVER) {
                        ctx.player().rotationYaw = this.prevRotation.getYaw();
                        ctx.player().rotationPitch = this.prevRotation.getPitch();
                    }

                    this.prevRotation = null;
                    this.visualTargetThisTick = null;
                    this.killAuraVisualControlThisTick = false;
                }
                // The target is done being used for this game tick, so it can be invalidated.
                // Keep movementRotation while pathing so movement stays decoupled from the
                // camera even between look updates. When Baritone is idle, clear it to avoid
                // leaking a stale steering frame into normal player control.
                this.target = null;
                if (!baritone.getPathingBehavior().isPathing()) {
                    this.movementRotation = null;
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onSendPacket(PacketEvent event) {
        if (!(event.getPacket() instanceof CPacketPlayer)) {
            return;
        }

        final CPacketPlayer packet = (CPacketPlayer) event.getPacket();
        if (packet instanceof CPacketPlayer.Rotation || packet instanceof CPacketPlayer.PositionRotation) {
            this.serverRotation = new Rotation(packet.getYaw(0.0f), packet.getPitch(0.0f));
        }
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        this.serverRotation = null;
        this.movementRotation = null;
        this.target = null;
        this.visualTargetThisTick = null;
        this.killAuraVisualControlThisTick = false;
        this.prevRotation = null;
        this.junctionGlanceTicksRemaining = 0;
        this.junctionGlanceYawOffset = 0.0F;
        this.lastJunctionGlanceKey = Long.MIN_VALUE;
    }

    public void pig() {
        if (this.target != null) {
            final Rotation actual = this.processor.peekRotation(this.target.rotation);
            ctx.player().rotationYaw = actual.getYaw();
        }
    }

    public void clearPathingRotationState() {
        this.target = null;
        this.serverRotation = null;
        this.movementRotation = null;
        this.prevRotation = null;
        this.visualTargetThisTick = null;
        this.killAuraVisualControlThisTick = false;
    }

    public Optional<Rotation> getEffectiveRotation() {
        // Pathing math must prefer the dedicated movement frame of reference.
        // serverRotation can be overwritten by normal outgoing player packets that still
        // reflect the user's camera, which would re-couple movement to the screen
        // direction and make pathing immediately drift when the player looks elsewhere.
        if (this.movementRotation != null) {
            return Optional.of(this.movementRotation);
        }
        return Optional.ofNullable(this.serverRotation);
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        final Rotation movement = this.target != null ? this.target.rotation : this.movementRotation;
        if (movement != null) {
            // Pathing math keeps a dedicated movement frame, but when freeLook is active the
            // actual physics/moveRelative code must not apply another yaw override on top of the
            // input remapping. Otherwise the same steering frame gets compensated twice.
            event.setYaw(movement.getYaw());
            event.setPitch(movement.getPitch());

            // Keep all pathing math in the same frame of reference for the rest of the tick.
            this.movementRotation = movement;
            this.serverRotation = movement;
        }
    }

    private Optional<Rotation> getKillAuraVisualTarget() {
        if (ctx == null || ctx.player() == null) {
            return Optional.empty();
        }
        return KillAuraHandler.INSTANCE.getVisualTargetRotation(ctx.player());
    }

    private boolean isSmoothLookEnabled() {
        return ctx.player() != null
                && (ctx.player().isElytraFlying() ? Baritone.settings().elytraSmoothLook.value
                        : Baritone.settings().smoothLook.value);
    }

    private Rotation smoothLookLikeKillAura(Rotation currentRotation, Rotation targetRotation) {
        if (currentRotation == null || targetRotation == null) {
            return targetRotation == null ? new Rotation(0.0F, 0.0F) : targetRotation.normalizeAndClamp();
        }

        Rotation desiredRotation = applySmoothLookOvershootCorrection(currentRotation, targetRotation.normalizeAndClamp());
        float yawDelta = MathHelper.wrapDegrees(desiredRotation.getYaw() - currentRotation.getYaw());
        float pitchDelta = desiredRotation.getPitch() - currentRotation.getPitch();
        float yawSpeed = computeSmoothLookTurnSpeed(Math.abs(yawDelta));
        float pitchSpeed = Math.max(MIN_SMOOTH_LOOK_PITCH_SPEED, yawSpeed * SMOOTH_LOOK_PITCH_SPEED_RATIO);
        float turnLimit = sampleSmoothLookMaxTurnStep();

        float maxYawStep = Math.min(yawSpeed, turnLimit);
        float maxPitchStep = Math.min(pitchSpeed, turnLimit);
        float yawStep = clampSigned(yawDelta, maxYawStep);
        float pitchStep = clampSigned(pitchDelta, maxPitchStep);
        float gcdStep = getMouseGcdStep();
        yawStep = quantizeRotationStepForGcd(yawStep, maxYawStep, gcdStep);
        pitchStep = quantizeRotationStepForGcd(pitchStep, maxPitchStep, gcdStep);

        return new Rotation(
                Rotation.normalizeYaw(currentRotation.getYaw() + yawStep),
                MathHelper.clamp(currentRotation.getPitch() + pitchStep, -90.0F, 90.0F));
    }

    private Rotation applySmoothLookOvershootCorrection(Rotation currentRotation, Rotation targetRotation) {
        float yawDelta = MathHelper.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
        float pitchDelta = targetRotation.getPitch() - currentRotation.getPitch();
        float absYaw = Math.abs(yawDelta);
        float absPitch = Math.abs(pitchDelta);

        float yawFade = MathHelper.clamp((absYaw - 1.35F) / 24.0F, 0.0F, 1.0F);
        float pitchFade = MathHelper.clamp((absPitch - 1.0F) / 22.0F, 0.0F, 1.0F);
        float yawOvershoot = MathHelper.clamp(absYaw * 0.045F, 0.0F, SMOOTH_LOOK_OVERSHOOT_MAX_YAW) * yawFade;
        float pitchOvershoot = MathHelper.clamp(absPitch * 0.030F, 0.0F, SMOOTH_LOOK_OVERSHOOT_MAX_PITCH)
                * pitchFade;

        float correctedYaw = targetRotation.getYaw() + Math.copySign(yawOvershoot, yawDelta);
        float correctedPitch = targetRotation.getPitch() + Math.copySign(pitchOvershoot, pitchDelta);
        return new Rotation(
                Rotation.normalizeYaw(correctedYaw),
                MathHelper.clamp(correctedPitch, -90.0F, 90.0F));
    }

    private float computeSmoothLookTurnSpeed(float yawDeltaAbs) {
        float configuredMin = MathHelper.clamp(Baritone.settings().smoothLookMinTurnSpeed.value, 0.05F, 180.0F);
        float configuredMax = MathHelper.clamp(
                Baritone.settings().smoothLookMaxTurnSpeed.value,
                configuredMin,
                180.0F);
        float normalized = MathHelper.clamp(yawDeltaAbs / 120.0F, 0.0F, 1.0F);
        float eased = normalized * normalized;
        float effectiveMin = Math.max(0.65F, configuredMin * 0.58F);
        float effectiveMax = Math.max(effectiveMin, configuredMax * 0.72F);
        return effectiveMin + (effectiveMax - effectiveMin) * eased;
    }

    private float sampleSmoothLookMaxTurnStep() {
        SmoothLookTurnStepRange range = parseSmoothLookMaxTurnStepRange(Baritone.settings().smoothLookMaxTurnStep.value);
        if (!range.isRandom()) {
            return range.min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(range.min, range.max);
    }

    private SmoothLookTurnStepRange parseSmoothLookMaxTurnStepRange(String spec) {
        String normalized = spec == null ? "" : spec.trim();
        if (normalized.isEmpty()) {
            return new SmoothLookTurnStepRange(
                    DEFAULT_SMOOTH_LOOK_MAX_TURN_STEP,
                    DEFAULT_SMOOTH_LOOK_MAX_TURN_STEP);
        }

        normalized = normalized.replace("°", "")
                .replace("，", ",")
                .replace("－", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("~", "-")
                .replace("～", "-")
                .replace("至", "-")
                .replace("到", "-")
                .replaceAll("\\s+", "");

        String[] parts = normalized.split("-", -1);
        try {
            if (parts.length == 1) {
                float value = clampSmoothLookTurnStep(Float.parseFloat(parts[0]));
                return new SmoothLookTurnStepRange(value, value);
            }
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                float min = clampSmoothLookTurnStep(Float.parseFloat(parts[0]));
                float max = clampSmoothLookTurnStep(Float.parseFloat(parts[1]));
                if (max < min) {
                    float swap = min;
                    min = max;
                    max = swap;
                }
                return new SmoothLookTurnStepRange(min, max);
            }
        } catch (NumberFormatException ignored) {
        }

        return new SmoothLookTurnStepRange(
                DEFAULT_SMOOTH_LOOK_MAX_TURN_STEP,
                DEFAULT_SMOOTH_LOOK_MAX_TURN_STEP);
    }

    private float clampSmoothLookTurnStep(float value) {
        return MathHelper.clamp(value, MIN_SMOOTH_LOOK_MAX_TURN_STEP, MAX_SMOOTH_LOOK_MAX_TURN_STEP);
    }

    private float getMouseGcdStep() {
        float sensitivity = MathHelper.clamp(ctx.minecraft().gameSettings.mouseSensitivity, 0.0F, 1.0F);
        float factor = sensitivity * 0.6F + 0.2F;
        return factor * factor * factor * 8.0F * 0.15F;
    }

    private float quantizeRotationStepForGcd(float step, float maxMagnitude, float gcdStep) {
        if (step == 0.0F || gcdStep <= 1.0E-5F || maxMagnitude <= 0.0F) {
            return step == 0.0F ? 0.0F : clampSigned(step, maxMagnitude);
        }
        float maxStep = Math.abs(maxMagnitude);
        float absStep = Math.min(Math.abs(step), maxStep);
        if (absStep <= 1.0E-5F) {
            return 0.0F;
        }
        if (maxStep + 1.0E-5F < gcdStep) {
            return Math.copySign(absStep, step);
        }

        float quantized = Math.round(absStep / gcdStep) * gcdStep;
        if (quantized <= 1.0E-5F && absStep >= gcdStep * 0.45F) {
            quantized = gcdStep;
        }
        if (quantized > maxStep) {
            quantized = (float) Math.floor(maxStep / gcdStep) * gcdStep;
        }
        if (quantized <= 1.0E-5F) {
            return 0.0F;
        }
        return Math.copySign(Math.min(quantized, maxStep), step);
    }

    private float clampSigned(float value, float maxMagnitude) {
        if (maxMagnitude <= 0.0F) {
            return 0.0F;
        }
        return Math.copySign(Math.min(Math.abs(value), maxMagnitude), value);
    }

    private static boolean shouldDecoupleMovementFromVisualYaw(Target.Mode mode, boolean blockInteract) {
        if (blockInteract) {
            return false;
        }
        return mode != Target.Mode.CLIENT;
    }

    private Rotation getHumanLikeVisualTarget(Rotation baseTarget) {
        if (!HumanLikeMovementController.INSTANCE.isEnabled()
                || ctx.player() == null
                || !baritone.getPathingBehavior().isPathing()) {
            return baseTarget;
        }

        Rotation adjusted = baseTarget;
        float finalApproachProgress = computeFinalApproachProgress();

        UpcomingTurnLook turnLook = findUpcomingTurnLook();
        if (turnLook != null) {
            adjusted = blendRotation(adjusted, turnLook.rotation,
                    turnLook.weight * (1.0F - finalApproachProgress * 0.55F));
        } else {
            IPathExecutor current = baritone.getPathingBehavior().getCurrent();
            if (countStraightStepsAhead(current) >= 3 && finalApproachProgress < 0.7F) {
                adjusted = new Rotation(
                        adjusted.getYaw() + computeSweepYawOffset(finalApproachProgress),
                        adjusted.getPitch());
            }
        }

        Rotation goalLook = computeGoalLookRotation();
        if (goalLook != null && finalApproachProgress > 0.35F) {
            adjusted = blendRotation(adjusted, goalLook, 0.20F + finalApproachProgress * 0.45F);
        }

        adjusted = applyObstacleAwareLook(adjusted, finalApproachProgress);
        adjusted = applyJunctionGlance(adjusted, finalApproachProgress);
        adjusted = stabilizeVisualPitch(adjusted, finalApproachProgress);
        return adjusted.normalizeAndClamp();
    }

    private Rotation computeGoalLookRotation() {
        Goal goal = baritone.getPathingBehavior().getGoal();
        if (!(goal instanceof IGoalRenderPos) || ctx.player() == null) {
            return null;
        }

        BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
        if (goalPos == null) {
            return null;
        }

        Vec3d eyePos = ctx.player().getPositionEyes(1.0F);
        Vec3d lookPos = new Vec3d(goalPos.getX() + 0.5D, goalPos.getY() + 0.85D, goalPos.getZ() + 0.5D);
        return RotationUtils.calcRotationFromVec3d(eyePos, lookPos, ctx.playerRotations());
    }

    private UpcomingTurnLook findUpcomingTurnLook() {
        IPathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current == null || current.getPath() == null) {
            return null;
        }

        List<BetterBlockPos> positions = current.getPath().positions();
        int index = current.getPosition();
        if (index < 0 || index + 2 >= positions.size()) {
            return null;
        }

        int baseDirX = directionX(positions.get(index), positions.get(index + 1));
        int baseDirZ = directionZ(positions.get(index), positions.get(index + 1));
        if (baseDirX == 0 && baseDirZ == 0) {
            return null;
        }

        int maxLookahead = Math.min(positions.size() - 2, index + 6);
        for (int i = index + 1; i <= maxLookahead; i++) {
            int nextDirX = directionX(positions.get(i), positions.get(i + 1));
            int nextDirZ = directionZ(positions.get(i), positions.get(i + 1));
            if (nextDirX == 0 && nextDirZ == 0) {
                continue;
            }
            if (nextDirX != baseDirX || nextDirZ != baseDirZ) {
                Rotation rotation = computeLookRotation(positions.get(i + 1));
                int stepsAhead = i - index;
                float weight = MathHelper.clamp(0.58F - (stepsAhead - 1) * 0.10F, 0.20F, 0.58F);
                return new UpcomingTurnLook(rotation, weight);
            }
        }

        return null;
    }

    private int countStraightStepsAhead(IPathExecutor current) {
        if (current == null || current.getPath() == null) {
            return 0;
        }

        List<BetterBlockPos> positions = current.getPath().positions();
        int index = current.getPosition();
        if (index < 0 || index + 1 >= positions.size()) {
            return 0;
        }

        int baseDirX = directionX(positions.get(index), positions.get(index + 1));
        int baseDirZ = directionZ(positions.get(index), positions.get(index + 1));
        int count = 0;

        int maxLookahead = Math.min(positions.size() - 2, index + 6);
        for (int i = index; i <= maxLookahead; i++) {
            int nextDirX = directionX(positions.get(i), positions.get(i + 1));
            int nextDirZ = directionZ(positions.get(i), positions.get(i + 1));
            if (nextDirX == baseDirX && nextDirZ == baseDirZ) {
                count++;
            } else {
                break;
            }
        }

        return count;
    }

    private Rotation computeLookRotation(BetterBlockPos pos) {
        Vec3d eyePos = ctx.player().getPositionEyes(1.0F);
        Vec3d lookPos = new Vec3d(pos.x + 0.5D, pos.y + 0.85D, pos.z + 0.5D);
        return RotationUtils.calcRotationFromVec3d(eyePos, lookPos, ctx.playerRotations());
    }

    private float computeFinalApproachProgress() {
        Goal goal = baritone.getPathingBehavior().getGoal();
        if (!(goal instanceof IGoalRenderPos) || ctx.player() == null) {
            return 0.0F;
        }

        BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
        if (goalPos == null) {
            return 0.0F;
        }

        double dx = ctx.player().posX - (goalPos.getX() + 0.5D);
        double dz = ctx.player().posZ - (goalPos.getZ() + 0.5D);
        double dy = ctx.player().posY - goalPos.getY();
        double distance = Math.sqrt(dx * dx + dz * dz + dy * dy * 0.35D);
        float normalized = 1.0F - (float) (distance / 2.6F);
        return MathHelper.clamp(normalized, 0.0F, 1.0F);
    }

    private float computeSweepYawOffset(float finalApproachProgress) {
        double phase = (ctx.player().ticksExisted % 72) / 72.0D * Math.PI * 2.0D;
        float amplitude = 1.85F * (1.0F - finalApproachProgress);
        return (float) Math.sin(phase) * amplitude;
    }

    private Rotation blendRotation(Rotation from, Rotation to, float weight) {
        weight = MathHelper.clamp(weight, 0.0F, 1.0F);
        float blendedYaw = from.getYaw() + Rotation.normalizeYaw(to.getYaw() - from.getYaw()) * weight;
        float blendedPitch = from.getPitch() + (to.getPitch() - from.getPitch()) * weight;
        return new Rotation(blendedYaw, blendedPitch);
    }

    private Rotation applyObstacleAwareLook(Rotation base, float finalApproachProgress) {
        if (finalApproachProgress >= 0.82F) {
            return base;
        }

        IPathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current == null || current.getPath() == null) {
            return base;
        }

        List<BetterBlockPos> positions = current.getPath().positions();
        int index = current.getPosition();
        if (index < 0 || index + 1 >= positions.size()) {
            return base;
        }

        BetterBlockPos nextPos = positions.get(index + 1);
        int dirX = directionX(positions.get(index), nextPos);
        int dirZ = directionZ(positions.get(index), nextPos);
        if (dirX == 0 && dirZ == 0) {
            return base;
        }

        int leftX = dirZ;
        int leftZ = -dirX;
        int rightX = -dirZ;
        int rightZ = dirX;

        float leftBlocked = sampleDirectionalBlockedness(nextPos, leftX, leftZ, dirX, dirZ);
        float rightBlocked = sampleDirectionalBlockedness(nextPos, rightX, rightZ, dirX, dirZ);
        float blockedDelta = leftBlocked - rightBlocked;
        if (Math.abs(blockedDelta) < 0.08F) {
            return base;
        }

        float yawOffset = MathHelper.clamp(
                blockedDelta * 6.0F * (1.0F - finalApproachProgress * 0.75F),
                -7.0F,
                7.0F);
        return new Rotation(base.getYaw() + yawOffset, base.getPitch());
    }

    private Rotation applyJunctionGlance(Rotation base, float finalApproachProgress) {
        if (finalApproachProgress >= 0.7F || ctx.player() == null) {
            junctionGlanceTicksRemaining = 0;
            return base;
        }

        if (junctionGlanceTicksRemaining <= 0) {
            tryStartJunctionGlance();
        }

        if (junctionGlanceTicksRemaining <= 0) {
            return base;
        }

        float weight = MathHelper.clamp(junctionGlanceTicksRemaining / 9.0F, 0.0F, 1.0F)
                * (1.0F - finalApproachProgress * 0.65F);
        junctionGlanceTicksRemaining--;
        return new Rotation(base.getYaw() + junctionGlanceYawOffset * weight, base.getPitch());
    }

    private Rotation stabilizeVisualPitch(Rotation base, float finalApproachProgress) {
        float preferredPitch = 6.0F + finalApproachProgress * 3.0F;
        float maxComfortPitch = 12.0F + finalApproachProgress * 2.0F;

        float pitch = base.getPitch();
        if (pitch > preferredPitch) {
            pitch = preferredPitch + (pitch - preferredPitch) * 0.18F;
        } else if (pitch < -18.0F) {
            pitch = -18.0F + (pitch + 18.0F) * 0.25F;
        }

        pitch = MathHelper.clamp(pitch, -18.0F, maxComfortPitch);
        return new Rotation(base.getYaw(), pitch);
    }

    private void tryStartJunctionGlance() {
        IPathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current == null || current.getPath() == null) {
            return;
        }

        List<BetterBlockPos> positions = current.getPath().positions();
        int index = current.getPosition();
        if (index < 0 || index + 1 >= positions.size()) {
            return;
        }

        BetterBlockPos currentPos = positions.get(index);
        BetterBlockPos nextPos = positions.get(index + 1);
        int dirX = directionX(currentPos, nextPos);
        int dirZ = directionZ(currentPos, nextPos);
        if (dirX == 0 && dirZ == 0) {
            return;
        }

        int leftX = dirZ;
        int leftZ = -dirX;
        int rightX = -dirZ;
        int rightZ = dirX;

        boolean leftOpen = isWalkableDirection(nextPos, leftX, leftZ);
        boolean rightOpen = isWalkableDirection(nextPos, rightX, rightZ);
        if (!leftOpen && !rightOpen) {
            return;
        }

        long junctionKey = toJunctionKey(nextPos);
        if (junctionKey == lastJunctionGlanceKey) {
            return;
        }

        float offset;
        if (leftOpen && rightOpen) {
            offset = ctx.player().ticksExisted % 2 == 0 ? -13.0F : 13.0F;
        } else if (leftOpen) {
            offset = -13.0F;
        } else {
            offset = 13.0F;
        }

        junctionGlanceYawOffset = offset;
        junctionGlanceTicksRemaining = 6;
        lastJunctionGlanceKey = junctionKey;
    }

    private float sampleDirectionalBlockedness(BetterBlockPos origin, int sideX, int sideZ, int forwardX, int forwardZ) {
        float blocked = 0.0F;
        if (!isWalkableColumn(origin.x + sideX, origin.y, origin.z + sideZ)) {
            blocked += 0.65F;
        }
        if (!isWalkableColumn(origin.x + sideX + forwardX, origin.y, origin.z + sideZ + forwardZ)) {
            blocked += 0.35F;
        }
        return blocked;
    }

    private boolean isWalkableDirection(BetterBlockPos origin, int dirX, int dirZ) {
        if (dirX == 0 && dirZ == 0) {
            return false;
        }

        return isWalkableColumn(origin.x + dirX, origin.y, origin.z + dirZ);
    }

    private boolean isWalkableColumn(int x, int y, int z) {
        return MovementHelper.canWalkOn(ctx, new BlockPos(x, y - 1, z))
                && MovementHelper.canWalkThrough(ctx, new BetterBlockPos(x, y, z))
                && MovementHelper.canWalkThrough(ctx, new BetterBlockPos(x, y + 1, z));
    }

    private long toJunctionKey(BetterBlockPos pos) {
        return (((long) pos.x) << 38) ^ (((long) pos.y) << 26) ^ (pos.z & 0x3FFFFFFL);
    }

    private int directionX(BetterBlockPos from, BetterBlockPos to) {
        return Integer.compare(to.x - from.x, 0);
    }

    private int directionZ(BetterBlockPos from, BetterBlockPos to) {
        return Integer.compare(to.z - from.z, 0);
    }

    private static final class UpcomingTurnLook {
        private final Rotation rotation;
        private final float weight;

        private UpcomingTurnLook(Rotation rotation, float weight) {
            this.rotation = rotation;
            this.weight = weight;
        }
    }

    private static final class SmoothLookTurnStepRange {
        private final float min;
        private final float max;

        private SmoothLookTurnStepRange(float min, float max) {
            this.min = min;
            this.max = max;
        }

        private boolean isRandom() {
            return this.max > this.min + 1.0E-5F;
        }
    }

    private static final class AimProcessor extends AbstractAimProcessor {

        public AimProcessor(final IPlayerContext ctx) {
            super(ctx);
        }

        @Override
        protected Rotation getPrevRotation() {
            // Implementation will use LookBehavior.serverRotation / movementRotation
            return ctx.playerRotations();
        }
    }

    private static abstract class AbstractAimProcessor implements ITickableAimProcessor {

        protected final IPlayerContext ctx;
        private final ForkableRandom rand;
        private double randomYawOffset;
        private double randomPitchOffset;

        public AbstractAimProcessor(IPlayerContext ctx) {
            this.ctx = ctx;
            this.rand = new ForkableRandom();
        }

        private AbstractAimProcessor(final AbstractAimProcessor source) {
            this.ctx = source.ctx;
            this.rand = source.rand.fork();
            this.randomYawOffset = source.randomYawOffset;
            this.randomPitchOffset = source.randomPitchOffset;
        }

        @Override
        public final Rotation peekRotation(final Rotation rotation) {
            final Rotation prev = this.getPrevRotation();

            float desiredYaw = rotation.getYaw();
            float desiredPitch = rotation.getPitch();

            // In other words, the target doesn't care about the pitch, so it used
            // playerRotations().getPitch()
            // and it's safe to adjust it to a normal level
            if (desiredPitch == prev.getPitch()) {
                desiredPitch = nudgeToLevel(desiredPitch);
            }

            desiredYaw += this.randomYawOffset;
            desiredPitch += this.randomPitchOffset;

            return new Rotation(
                    this.calculateMouseMove(prev.getYaw(), desiredYaw),
                    this.calculateMouseMove(prev.getPitch(), desiredPitch)).clamp();
        }

        @Override
        public final void tick() {
            this.randomYawOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;
            this.randomPitchOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;
        }

        @Override
        public final void advance(int ticks) {
            for (int i = 0; i < ticks; i++) {
                this.tick();
            }
        }

        @Override
        public Rotation nextRotation(final Rotation rotation) {
            final Rotation actual = this.peekRotation(rotation);
            this.tick();
            return actual;
        }

        @Override
        public final ITickableAimProcessor fork() {
            return new AbstractAimProcessor(this) {

                private Rotation prev = AbstractAimProcessor.this.getPrevRotation();

                @Override
                public Rotation nextRotation(final Rotation rotation) {
                    return (this.prev = super.nextRotation(rotation));
                }

                @Override
                protected Rotation getPrevRotation() {
                    return this.prev;
                }
            };
        }

        protected abstract Rotation getPrevRotation();

        /**
         * Nudges the player's pitch to a regular level. (Between {@code -20} and
         * {@code 10}, increments are by {@code 1})
         */
        private float nudgeToLevel(float pitch) {
            if (pitch < -20) {
                return pitch + 1;
            } else if (pitch > 10) {
                return pitch - 1;
            }
            return pitch;
        }

        private float calculateMouseMove(float current, float target) {
            final float delta = target - current;
            final int deltaPx = angleToMouse(delta);
            return current + mouseToAngle(deltaPx);
        }

        private int angleToMouse(float angleDelta) {
            final float minAngleChange = mouseToAngle(1);
            return Math.round(angleDelta / minAngleChange);
        }

        private float mouseToAngle(int mouseDelta) {
            final float f = ctx.minecraft().gameSettings.mouseSensitivity * 0.6f + 0.2f;
            return mouseDelta * f * f * f * 8.0f * 0.15f;
        }
    }

    private static class Target {

        public final Rotation rotation;
        public final Mode mode;
        public final boolean blockInteract;

        public Target(Rotation rotation, Mode mode, boolean blockInteract) {
            this.rotation = rotation;
            this.mode = mode;
            this.blockInteract = blockInteract;
        }

        enum Mode {
            /**
             * Rotation will be set client-side and is visual to the player
             */
            CLIENT,

            /**
             * Rotation will be set server-side and is silent to the player
             */
            SERVER,

            /**
             * Rotation will remain unaffected on both the client and server
             */
            NONE;

            static Mode resolve(IPlayerContext ctx, boolean blockInteract) {
                final Settings settings = Baritone.settings();
                final boolean antiCheat = settings.antiCheatCompatibility.value;
                final boolean blockFreeLook = settings.blockFreeLook.value;
                final boolean killAuraOwnsLook = ctx != null
                        && ctx.player() != null
                        && KillAuraHandler.INSTANCE.getVisualTargetRotation(ctx.player()).isPresent();
                final Mode resolvedMode;

                if (ctx.player().isElytraFlying()) {
                    // always need to set angles while flying
                    resolvedMode = settings.elytraFreeLook.value ? SERVER : CLIENT;
                } else if (settings.freeLook.value) {
                    // Regardless of if antiCheatCompatibility is enabled, if a blockInteract is
                    // requested then the player
                    // rotation needs to be set somehow, otherwise Baritone will halt since
                    // objectMouseOver() will just be
                    // whatever the player is mousing over visually. Let's just settle for setting
                    // it silently.
                    if (blockInteract) {
                        resolvedMode = blockFreeLook ? SERVER : CLIENT;
                    } else {
                        resolvedMode = antiCheat ? SERVER : NONE;
                    }
                } else {
                    // all freeLook settings are disabled so set the angles
                    resolvedMode = CLIENT;
                }

                if (killAuraOwnsLook && resolvedMode == CLIENT) {
                    return SERVER;
                }
                return resolvedMode;
            }
        }
    }
}
