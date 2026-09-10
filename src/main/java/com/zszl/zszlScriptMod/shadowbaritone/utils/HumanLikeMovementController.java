package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.config.HumanLikeMovementConfig;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * 模拟真人移动控制器。
 *
 * 目标：
 * 1. 视角不瞬间锁定，而是渐进逼近目标角度
 * 2. 移动输入不瞬间满速，而是有加减速过程
 * 3. 转弯时自动减速
 * 4. 注入轻微横移扰动和偶发短暂停顿
 * 5. 起步前优先转头，减少“边横着身子边瞬间起跑”的脚本感
 * 6. 靠近目标时减小扰动并主动减速，增强末段收敛感
 * 7. 检测疑似卡住时注入短时随机恢复动作
 * 8. 狭窄区域自动放慢并抑制扰动
 * 9. 长直线路段带少量“呼吸感”节奏变化
 *
 * 说明：
 * - 不直接替换 Baritone 的寻路结果，而是对其“最后一层表现”做人类化修饰
 * - 这样能最大程度保留原有路径正确性，同时改善视觉观感
 */
public final class HumanLikeMovementController {

    public static final HumanLikeMovementController INSTANCE = new HumanLikeMovementController();

    private static final double VANILLA_REFERENCE_HORIZONTAL_SPEED = 0.2873D;

    private final Random random = new Random();

    private float smoothedForward;
    private float smoothedStrafe;
    private int microPauseTicksRemaining;
    private int strafeJitterTicksRemaining;
    private float currentStrafeJitter;

    private boolean hasLastPosition;
    private double lastPosX;
    private double lastPosY;
    private double lastPosZ;
    private int stuckTicks;
    private int recoveryTicksRemaining;
    private float recoveryStrafe;
    private float recoveryForwardScale = 1.0F;
    private float rhythmPhase;
    private int corridorBiasDirection;
    private int corridorBiasTicks;
    private int lightHopCooldownRemaining;
    private int cautiousCorrectionTicksRemaining;
    private float rotationCadencePhase;
    private int startupCommitTicksRemaining;
    private boolean wasMovingLastTick;

    private HumanLikeMovementController() {
    }

    public boolean isEnabled() {
        return HumanLikeMovementConfig.INSTANCE != null && HumanLikeMovementConfig.INSTANCE.enabled;
    }

    public void reset() {
        smoothedForward = 0.0F;
        smoothedStrafe = 0.0F;
        microPauseTicksRemaining = 0;
        strafeJitterTicksRemaining = 0;
        currentStrafeJitter = 0.0F;

        hasLastPosition = false;
        lastPosX = 0.0D;
        lastPosY = 0.0D;
        lastPosZ = 0.0D;
        stuckTicks = 0;
        recoveryTicksRemaining = 0;
        recoveryStrafe = 0.0F;
        recoveryForwardScale = 1.0F;
        rhythmPhase = 0.0F;
        corridorBiasDirection = 0;
        corridorBiasTicks = 0;
        lightHopCooldownRemaining = 0;
        cautiousCorrectionTicksRemaining = 0;
        rotationCadencePhase = 0.0F;
        startupCommitTicksRemaining = 0;
        wasMovingLastTick = false;
    }

    public MovementState applyMovement(float desiredForward, float desiredStrafe, boolean jump, boolean sneak,
            float yawDifferenceDeg, double playerX, double playerY, double playerZ, boolean onGround,
            float finalApproachProgress, float narrowPassageFactor, float straightPathFactor,
            float obstacleEdgeBias) {
        if (!isEnabled()) {
            reset();
            return new MovementState(desiredForward, desiredStrafe, jump, sneak);
        }

        HumanLikeMovementConfig config = HumanLikeMovementConfig.INSTANCE;
        config.normalize();

        finalApproachProgress = MathHelper.clamp(finalApproachProgress, 0.0F, 1.0F);
        narrowPassageFactor = MathHelper.clamp(narrowPassageFactor, 0.0F, 1.0F);
        straightPathFactor = MathHelper.clamp(straightPathFactor, 0.0F, 1.0F);
        obstacleEdgeBias = MathHelper.clamp(obstacleEdgeBias, -1.0F, 1.0F);

        boolean hasMovementIntent = Math.abs(desiredForward) > 0.001F || Math.abs(desiredStrafe) > 0.001F;
        float adaptiveLateralScale = computeAdaptiveLateralScale(
                hasMovementIntent,
                desiredForward,
                desiredStrafe,
                playerX,
                playerZ,
                finalApproachProgress,
                narrowPassageFactor);

        updateStartupCommit(hasMovementIntent, desiredForward, desiredStrafe, finalApproachProgress,
                narrowPassageFactor);

        updateStuckRecovery(config, hasMovementIntent, playerX, playerY, playerZ, finalApproachProgress);
        updateMicroPause(config, hasMovementIntent, finalApproachProgress);
        updateStrafeJitter(config, hasMovementIntent, finalApproachProgress, narrowPassageFactor);
        updateRhythm(hasMovementIntent, straightPathFactor, narrowPassageFactor, finalApproachProgress);
        updateCorridorBias(config, hasMovementIntent, straightPathFactor, narrowPassageFactor, finalApproachProgress);
        updateLightHopCooldown();
        float cautiousCorrectionFactor = consumeCautiousCorrectionFactor();

        float targetForward = desiredForward;
        float targetStrafe = desiredStrafe;

        if (!hasMovementIntent) {
            clearRecoveryIfIdle();
        }

        if (microPauseTicksRemaining > 0) {
            targetForward = 0.0F;
            targetStrafe = 0.0F;
        }

        float absYawDifference = Math.abs(yawDifferenceDeg);
        if (hasMovementIntent && absYawDifference > config.startTurnThreshold) {
            float threshold = Math.max(1.0F, config.startTurnThreshold);
            float overshoot = MathHelper.clamp((absYawDifference - threshold) / threshold, 0.0F, 1.0F);

            targetForward *= 1.0F - overshoot;
            targetStrafe *= 1.0F - overshoot * 0.55F;

            if (absYawDifference > threshold * 1.45F) {
                if (Math.abs(desiredForward) > 0.001F) {
                    float minimumTurnWalk = MathHelper.clamp(0.42F + Math.abs(desiredForward) * 0.20F, 0.42F, 0.62F);
                    targetForward = Math.copySign(Math.max(Math.abs(targetForward), minimumTurnWalk), desiredForward);
                }
                targetStrafe *= 0.45F;
            }
        }

        float turnFactor = 1.0F
                - MathHelper.clamp(absYawDifference / 90.0F, 0.0F, 1.0F) * config.turnSlowdown;
        targetForward *= turnFactor;

        if (startupCommitTicksRemaining > 0 && hasMovementIntent) {
            float startupProgress = 1.0F - startupCommitTicksRemaining / 6.0F;
            float startupForwardScale = MathHelper.clamp(0.82F + startupProgress * 0.18F, 0.82F, 1.0F);
            float startupStrafeScale = MathHelper.clamp(0.35F + startupProgress * 0.35F, 0.35F, 0.85F);
            startupStrafeScale = Math.max(0.18F, startupStrafeScale * (0.55F + adaptiveLateralScale * 0.45F));
            targetForward *= startupForwardScale;
            targetStrafe *= startupStrafeScale;
        }

        float narrowFactor = 1.0F - narrowPassageFactor * config.narrowSlowdown;
        targetForward *= MathHelper.clamp(narrowFactor, 0.18F, 1.0F);
        targetStrafe *= MathHelper.clamp(1.0F - narrowPassageFactor * Math.min(0.85F, config.narrowSlowdown + 0.15F),
                0.12F, 1.0F);

        float finalApproachForwardScale = 1.0F - finalApproachProgress * config.finalApproachSlowdown;
        float finalApproachStrafeScale = 1.0F
                - finalApproachProgress * Math.min(0.75F, config.finalApproachSlowdown * 0.75F);

        targetForward *= MathHelper.clamp(finalApproachForwardScale, 0.15F, 1.0F);
        targetStrafe *= MathHelper.clamp(finalApproachStrafeScale, 0.20F, 1.0F);

        float rhythmFactor = computeRhythmFactor(config, straightPathFactor, narrowPassageFactor, finalApproachProgress,
                absYawDifference);
        targetForward *= rhythmFactor;

        if (Math.abs(targetForward) > 0.001F && corridorBiasDirection != 0) {
            float corridorBiasScale = config.corridorBiasStrength
                    * straightPathFactor
                    * (1.0F - finalApproachProgress * 0.85F)
                    * (1.0F - narrowPassageFactor * 0.55F)
                    * (1.0F - cautiousCorrectionFactor * 0.80F)
                    * adaptiveLateralScale;
            targetStrafe += corridorBiasDirection * corridorBiasScale;
        }

        if (Math.abs(targetForward) > 0.08F && Math.abs(obstacleEdgeBias) > 0.001F) {
            float edgeCorrectionStrength = (0.08F + narrowPassageFactor * 0.20F)
                    * (1.0F - finalApproachProgress * 0.75F)
                    * (1.0F - straightPathFactor * 0.25F)
                    * (0.35F + adaptiveLateralScale * 0.65F);
            targetStrafe += obstacleEdgeBias * edgeCorrectionStrength;
            targetForward *= 1.0F - Math.abs(obstacleEdgeBias) * 0.06F;
        }

        float jitterScale = (1.0F - finalApproachProgress)
                * (1.0F - narrowPassageFactor * 0.9F)
                * (1.0F - cautiousCorrectionFactor * 0.85F)
                * adaptiveLateralScale;
        if (Math.abs(targetForward) > 0.001F && Math.abs(currentStrafeJitter) > 0.001F) {
            targetStrafe += currentStrafeJitter * jitterScale;
        }

        if (recoveryTicksRemaining > 0) {
            targetStrafe += recoveryStrafe * adaptiveLateralScale;
            targetForward *= recoveryForwardScale;
            recoveryTicksRemaining--;
            if (recoveryTicksRemaining <= 0) {
                recoveryStrafe = 0.0F;
                recoveryForwardScale = 1.0F;
            }
        }

        if (cautiousCorrectionFactor > 0.001F) {
            float desiredStrafeCap = 0.12F + (1.0F - cautiousCorrectionFactor) * 0.20F;
            float guardedStrafeTarget = MathHelper.clamp(desiredStrafe, -desiredStrafeCap, desiredStrafeCap);
            targetStrafe = approach(targetStrafe, guardedStrafeTarget, 0.16F + cautiousCorrectionFactor * 0.22F);

            if (Math.abs(desiredForward) > 0.001F) {
                float guardedForwardTarget = Math.copySign(
                        Math.max(Math.abs(targetForward), Math.min(1.0F, Math.abs(desiredForward) + 0.08F)),
                        desiredForward);
                targetForward = approach(targetForward, guardedForwardTarget, 0.12F + cautiousCorrectionFactor * 0.18F);
            }
        }

        if (Math.abs(desiredForward) > 0.001F
                && !sneak
                && microPauseTicksRemaining <= 0
                && recoveryTicksRemaining <= 0) {
            float minimumForward = 0.84F
                    * (1.0F - finalApproachProgress * 0.35F)
                    * (1.0F - narrowPassageFactor * 0.25F)
                    * (1.0F - MathHelper.clamp((absYawDifference - 75.0F) / 35.0F, 0.0F, 0.55F));
            minimumForward = MathHelper.clamp(minimumForward, 0.45F, 0.92F);
            if (Math.abs(targetForward) < minimumForward) {
                targetForward = Math.copySign(minimumForward, desiredForward);
            }
        }

        float strafePerturbationCap = computeStrafePerturbationCap(
                desiredStrafe,
                finalApproachProgress,
                narrowPassageFactor,
                straightPathFactor,
                recoveryTicksRemaining > 0,
                sneak,
                adaptiveLateralScale);
        targetStrafe = MathHelper.clamp(targetStrafe, -strafePerturbationCap, strafePerturbationCap);

        smoothedForward = approach(smoothedForward, targetForward,
                Math.abs(targetForward) > Math.abs(smoothedForward) ? config.acceleration : config.deceleration);
        smoothedStrafe = approach(smoothedStrafe, targetStrafe,
                Math.abs(targetStrafe) > Math.abs(smoothedStrafe) ? config.acceleration : config.deceleration);

        smoothedForward = MathHelper.clamp(smoothedForward, -1.0F, 1.0F);
        smoothedStrafe = MathHelper.clamp(smoothedStrafe, -1.0F, 1.0F);

        if (sneak) {
            smoothedForward *= 0.3F;
            smoothedStrafe *= 0.3F;
        }

        boolean outputJump = jump;
        if (!outputJump && shouldTriggerLightHop(
                config,
                hasMovementIntent,
                sneak,
                onGround,
                absYawDifference,
                finalApproachProgress,
                narrowPassageFactor,
                straightPathFactor)) {
            outputJump = true;
            lightHopCooldownRemaining = config.lightHopCooldownTicks;
        }

        wasMovingLastTick = hasMovementIntent;
        return new MovementState(smoothedForward, smoothedStrafe, outputJump, sneak);
    }

    public RotationState smoothRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        if (!isEnabled()) {
            return new RotationState(targetYaw, targetPitch);
        }

        HumanLikeMovementConfig config = HumanLikeMovementConfig.INSTANCE;
        config.normalize();

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = clampPitchDelta(targetPitch - currentPitch);

        float yawSpeed = computeTurnSpeed(Math.abs(yawDelta), config);
        float pitchSpeed = Math.max(config.minTurnSpeed * 0.6F, yawSpeed * 0.7F);

        rotationCadencePhase += 0.16F + Math.min(Math.abs(yawDelta), 60.0F) * 0.0022F;
        if (rotationCadencePhase > Math.PI * 2.0F) {
            rotationCadencePhase -= (float) (Math.PI * 2.0F);
        }

        float largeTurnFactor = MathHelper.clamp(Math.abs(yawDelta) / 90.0F, 0.0F, 1.0F);
        float cadenceFactor = 0.985F + (float) Math.sin(rotationCadencePhase) * 0.025F * largeTurnFactor;
        float turnVariance = 0.992F + random.nextFloat() * 0.016F * largeTurnFactor;
        float largeTurnBias = 0.985F + largeTurnFactor * 0.035F;

        yawSpeed *= cadenceFactor * turnVariance * largeTurnBias;
        pitchSpeed *= (0.992F + random.nextFloat() * 0.015F * largeTurnFactor)
                * (0.992F + (cadenceFactor - 0.985F) * 0.18F);

        float jitterReduction = MathHelper.clamp((Math.abs(yawDelta) - 6.0F) / 20.0F, 0.0F, 1.0F);
        float jitterYaw = randomCentered() * config.viewJitter * 0.18F * jitterReduction;
        float jitterPitch = randomCentered() * config.viewJitter * 0.08F * jitterReduction;

        float yawOvershoot = 0.0F;
        if (Math.abs(yawDelta) > 18.0F) {
            yawOvershoot = Math.signum(yawDelta)
                    * Math.min(Math.abs(yawDelta) * 0.03F, config.turnOvershoot * 2.0F);
        }

        float nextYaw = currentYaw + clampSigned(yawDelta + yawOvershoot + jitterYaw, yawSpeed);
        float nextPitch = currentPitch + clampSigned(pitchDelta + jitterPitch, pitchSpeed);

        return new RotationState(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private void updateStartupCommit(boolean hasMovementIntent, float desiredForward, float desiredStrafe,
            float finalApproachProgress, float narrowPassageFactor) {
        boolean strongIntent = Math.abs(desiredForward) > 0.08F || Math.abs(desiredStrafe) > 0.08F;
        if (!hasMovementIntent || !strongIntent) {
            startupCommitTicksRemaining = 0;
            if (!hasMovementIntent) {
                wasMovingLastTick = false;
            }
            return;
        }

        if (!wasMovingLastTick) {
            int baseTicks = 3 + random.nextInt(3);
            if (finalApproachProgress > 0.55F) {
                baseTicks = Math.max(2, baseTicks - 1);
            }
            if (narrowPassageFactor > 0.65F) {
                baseTicks = Math.min(baseTicks, 3);
            }
            startupCommitTicksRemaining = baseTicks;
        } else if (startupCommitTicksRemaining > 0) {
            startupCommitTicksRemaining--;
        }
    }

    private void updateMicroPause(HumanLikeMovementConfig config, boolean hasMovementIntent,
            float finalApproachProgress) {
        if (!hasMovementIntent) {
            microPauseTicksRemaining = 0;
            return;
        }
        if (microPauseTicksRemaining > 0) {
            microPauseTicksRemaining--;
            return;
        }

        float effectiveChance = config.microPauseChance * (1.0F - finalApproachProgress * 0.85F);
        if (effectiveChance <= 0.0F) {
            return;
        }

        if (random.nextFloat() < effectiveChance) {
            int min = Math.max(0, config.microPauseMinTicks);
            int max = Math.max(min, config.microPauseMaxTicks);
            microPauseTicksRemaining = min + (max > min ? random.nextInt(max - min + 1) : 0);
        }
    }

    private void updateStrafeJitter(HumanLikeMovementConfig config, boolean hasMovementIntent,
            float finalApproachProgress, float narrowPassageFactor) {
        if (!hasMovementIntent) {
            strafeJitterTicksRemaining = 0;
            currentStrafeJitter = 0.0F;
            return;
        }

        if (strafeJitterTicksRemaining > 0) {
            strafeJitterTicksRemaining--;
            if (strafeJitterTicksRemaining <= 0) {
                currentStrafeJitter = 0.0F;
            }
            return;
        }

        float effectiveChance = config.strafeJitterChance
                * (1.0F - finalApproachProgress * 0.9F)
                * (1.0F - narrowPassageFactor * 0.85F);
        if (random.nextFloat() < effectiveChance) {
            strafeJitterTicksRemaining = 4 + random.nextInt(8);
            currentStrafeJitter = randomCentered()
                    * config.strafeJitterStrength
                    * (1.0F - finalApproachProgress)
                    * (1.0F - narrowPassageFactor * 0.9F);
        }
    }

    private void updateRhythm(boolean hasMovementIntent, float straightPathFactor, float narrowPassageFactor,
            float finalApproachProgress) {
        if (!hasMovementIntent) {
            rhythmPhase = 0.0F;
            return;
        }

        float phaseSpeed = 0.075F + straightPathFactor * 0.055F;
        phaseSpeed *= 1.0F - narrowPassageFactor * 0.45F;
        phaseSpeed *= 1.0F - finalApproachProgress * 0.6F;
        rhythmPhase += phaseSpeed;

        if (rhythmPhase > Math.PI * 2.0F) {
            rhythmPhase -= (float) (Math.PI * 2.0F);
        }
    }

    private float computeRhythmFactor(HumanLikeMovementConfig config, float straightPathFactor,
            float narrowPassageFactor, float finalApproachProgress, float absYawDifference) {
        float strength = config.rhythmVariation;
        strength *= straightPathFactor;
        strength *= 1.0F - narrowPassageFactor * 0.8F;
        strength *= 1.0F - finalApproachProgress * 0.75F;
        strength *= 1.0F - MathHelper.clamp(absYawDifference / 85.0F, 0.0F, 1.0F) * 0.7F;

        if (strength <= 0.001F) {
            return 1.0F;
        }

        float wave = (float) Math.sin(rhythmPhase);
        return MathHelper.clamp(1.0F + wave * strength, 0.82F, 1.12F);
    }

    private void updateCorridorBias(HumanLikeMovementConfig config, boolean hasMovementIntent, float straightPathFactor,
            float narrowPassageFactor, float finalApproachProgress) {
        if (!hasMovementIntent || config.corridorBiasStrength <= 0.001F) {
            corridorBiasDirection = 0;
            corridorBiasTicks = 0;
            return;
        }

        if (straightPathFactor < 0.45F || finalApproachProgress > 0.78F || narrowPassageFactor > 0.92F) {
            corridorBiasTicks = 0;
            corridorBiasDirection = 0;
            return;
        }

        corridorBiasTicks++;
        if (corridorBiasDirection == 0 || corridorBiasTicks > 90 + random.nextInt(80)) {
            corridorBiasDirection = random.nextBoolean() ? 1 : -1;
            corridorBiasTicks = 0;
        }
    }

    private void updateLightHopCooldown() {
        if (lightHopCooldownRemaining > 0) {
            lightHopCooldownRemaining--;
        }
    }

    private boolean shouldTriggerLightHop(HumanLikeMovementConfig config, boolean hasMovementIntent, boolean sneak,
            boolean onGround, float absYawDifference, float finalApproachProgress, float narrowPassageFactor,
            float straightPathFactor) {
        if (!hasMovementIntent || sneak || !onGround) {
            return false;
        }
        if (lightHopCooldownRemaining > 0
                || microPauseTicksRemaining > 0
                || recoveryTicksRemaining > 0
                || corridorBiasDirection == 0) {
            return false;
        }
        if (config.lightHopChance <= 0.0F
                || straightPathFactor < 0.72F
                || narrowPassageFactor > 0.35F
                || finalApproachProgress > 0.55F
                || absYawDifference > 16.0F) {
            return false;
        }
        return random.nextFloat() < config.lightHopChance;
    }

    private void updateStuckRecovery(HumanLikeMovementConfig config, boolean hasMovementIntent, double playerX,
            double playerY, double playerZ, float finalApproachProgress) {
        double horizontalDelta = 0.0D;
        if (hasLastPosition) {
            double dx = playerX - lastPosX;
            double dz = playerZ - lastPosZ;
            horizontalDelta = Math.sqrt(dx * dx + dz * dz);
        }

        if (!hasMovementIntent) {
            stuckTicks = 0;
            recoveryTicksRemaining = 0;
            recoveryStrafe = 0.0F;
            recoveryForwardScale = 1.0F;
            cautiousCorrectionTicksRemaining = 0;
            rememberPosition(playerX, playerY, playerZ);
            return;
        }

        if (recoveryTicksRemaining > 0 && horizontalDelta > 0.045D) {
            recoveryTicksRemaining = 0;
            recoveryStrafe = 0.0F;
            recoveryForwardScale = 1.0F;
        }

        if (!config.enableStuckRecovery || finalApproachProgress >= 0.98F || microPauseTicksRemaining > 0) {
            stuckTicks = 0;
            rememberPosition(playerX, playerY, playerZ);
            return;
        }

        if (hasLastPosition) {
            if (horizontalDelta < 0.010D) {
                stuckTicks++;
            } else if (stuckTicks > 0) {
                stuckTicks = Math.max(0, stuckTicks - 2);
            }
        }

        if (recoveryTicksRemaining <= 0 && stuckTicks >= config.stuckRecoveryTicks) {
            int min = Math.max(1, config.stuckRecoveryMinTicks);
            int max = Math.max(min, config.stuckRecoveryMaxTicks);
            recoveryTicksRemaining = min + (max > min ? random.nextInt(max - min + 1) : 0);
            recoveryStrafe = (random.nextBoolean() ? 1.0F : -1.0F) * config.stuckRecoveryStrafeStrength;
            recoveryForwardScale = 0.35F + random.nextFloat() * 0.35F;
            cautiousCorrectionTicksRemaining = Math.max(cautiousCorrectionTicksRemaining, recoveryTicksRemaining + 10);
            stuckTicks = 0;
        }

        rememberPosition(playerX, playerY, playerZ);
    }

    private void clearRecoveryIfIdle() {
        stuckTicks = 0;
        recoveryTicksRemaining = 0;
        recoveryStrafe = 0.0F;
        recoveryForwardScale = 1.0F;
        corridorBiasDirection = 0;
        corridorBiasTicks = 0;
        cautiousCorrectionTicksRemaining = 0;
    }

    private void rememberPosition(double playerX, double playerY, double playerZ) {
        hasLastPosition = true;
        lastPosX = playerX;
        lastPosY = playerY;
        lastPosZ = playerZ;
    }

    private float computeTurnSpeed(float absDelta, HumanLikeMovementConfig config) {
        float normalized = MathHelper.clamp(absDelta / 90.0F, 0.0F, 1.0F);
        return config.minTurnSpeed + (config.maxTurnSpeed - config.minTurnSpeed) * normalized;
    }

    private float consumeCautiousCorrectionFactor() {
        if (cautiousCorrectionTicksRemaining <= 0) {
            return 0.0F;
        }
        float factor = MathHelper.clamp(cautiousCorrectionTicksRemaining / 16.0F, 0.0F, 1.0F);
        cautiousCorrectionTicksRemaining--;
        return factor;
    }

    private float computeStrafePerturbationCap(float desiredStrafe, float finalApproachProgress,
            float narrowPassageFactor, float straightPathFactor, boolean recovering, boolean sneak,
            float adaptiveLateralScale) {
        float desiredAbs = Math.abs(desiredStrafe);
        float perturbationAllowance = desiredAbs > 0.08F ? 0.32F : 0.18F;
        perturbationAllowance += straightPathFactor * 0.08F;
        perturbationAllowance -= narrowPassageFactor * 0.16F;
        perturbationAllowance -= finalApproachProgress * 0.14F;
        perturbationAllowance *= MathHelper.clamp(adaptiveLateralScale, 0.12F, 1.0F);

        if (recovering) {
            perturbationAllowance += 0.10F * Math.max(0.45F, adaptiveLateralScale);
        }
        if (sneak) {
            perturbationAllowance *= 0.55F;
        }

        perturbationAllowance = MathHelper.clamp(perturbationAllowance, 0.04F, 0.42F);
        return MathHelper.clamp(Math.max(desiredAbs + perturbationAllowance, 0.18F), 0.18F, 1.0F);
    }

    private float computeAdaptiveLateralScale(boolean hasMovementIntent, float desiredForward, float desiredStrafe,
            double playerX, double playerZ, float finalApproachProgress, float narrowPassageFactor) {
        if (!hasMovementIntent || !hasLastPosition) {
            return 1.0F;
        }

        double dx = playerX - lastPosX;
        double dz = playerZ - lastPosZ;
        double horizontalDelta = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDelta <= 1.0E-4D) {
            return 1.0F;
        }

        double intendedMagnitude = Math.sqrt(desiredForward * desiredForward + desiredStrafe * desiredStrafe);
        double expectedSpeed = VANILLA_REFERENCE_HORIZONTAL_SPEED * Math.max(0.35D, Math.min(1.0D, intendedMagnitude));
        double speedRatio = horizontalDelta / Math.max(0.08D, expectedSpeed);

        float scale = (float) (1.0D / Math.max(1.0D, speedRatio));
        scale = MathHelper.clamp(scale, 0.12F, 1.0F);
        scale += finalApproachProgress * 0.08F;
        scale += narrowPassageFactor * 0.05F;
        return MathHelper.clamp(scale, 0.12F, 1.0F);
    }

    private float approach(float current, float target, float factor) {
        factor = MathHelper.clamp(factor, 0.01F, 1.0F);
        return current + (target - current) * factor;
    }

    private float randomCentered() {
        return (random.nextFloat() - 0.5F) * 2.0F;
    }

    private float clampSigned(float value, float maxMagnitude) {
        return Math.copySign(Math.min(Math.abs(value), Math.max(0.01F, maxMagnitude)), value);
    }

    private float clampPitchDelta(float delta) {
        return MathHelper.clamp(delta, -90.0F, 90.0F);
    }

    public static final class MovementState {
        public final float moveForward;
        public final float moveStrafe;
        public final boolean jump;
        public final boolean sneak;

        public MovementState(float moveForward, float moveStrafe, boolean jump, boolean sneak) {
            this.moveForward = moveForward;
            this.moveStrafe = moveStrafe;
            this.jump = jump;
            this.sneak = sneak;
        }
    }

    public static final class RotationState {
        public final float yaw;
        public final float pitch;

        public RotationState(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}