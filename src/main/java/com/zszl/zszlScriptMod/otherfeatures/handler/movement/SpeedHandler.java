package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.PathingSpeedController;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Timer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeedHandler {

    public static final SpeedHandler INSTANCE = new SpeedHandler();

    public static final String MODE_GROUND = "GROUND";
    public static final String MODE_AIR = "AIR";
    public static final String MODE_BHOP = "BHOP";
    public static final String MODE_LOWHOP = "LOWHOP";
    public static final String MODE_ONGROUND = "ONGROUND";

    public static final String PRESET_CUSTOM = "CUSTOM";
    public static final String PRESET_SAFE = "SAFE";
    public static final String PRESET_BALANCED = "BALANCED";
    public static final String PRESET_AGGRESSIVE = "AGGRESSIVE";

    public static boolean enabled = false;
    public static String speedMode = MODE_BHOP;
    public static String presetId = PRESET_BALANCED;
    public static boolean useTimerBoost = true;
    public static boolean showStatusHud = true;
    public static float timerSpeed = 1.08F;
    public static float jumpHeight = 0.41F;
    public static float vanillaSpeed = 0.90F;

    private boolean slowDown = false;
    private double playerSpeed = 0.2873D;
    private int airTicks = 0;
    private int groundTicks = 0;
    private double lastOrbitDirectionX = Double.NaN;
    private double lastOrbitDirectionZ = Double.NaN;
    private int orbitDirectionGraceTicks = 0;
    private static Field timerTickLengthField;
    private static final int ORBIT_DIRECTION_GRACE_TICKS = 3;
    private static final double ORBIT_PATH_LOCK_MAX_LATERAL_RATIO = 0.035D;
    private static final double ORBIT_PATH_LOCK_MAX_LATERAL_STEP = 0.22D;
    private static final double ORBIT_PATH_LOCK_PARALLEL_STEP_RATIO = 0.32D;
    private static final double ORBIT_PATH_LOCK_MIN_PARALLEL_STEP = 0.055D;
    private static final double ORBIT_PATH_RECOVERY_REFERENCE = 0.18D;
    private static final double ORBIT_PATH_RECOVERY_MAX_PENALTY = 0.72D;
    private PathingSpeedController.MotionPlan activePathingMotionPlan;

    private SpeedHandler() {
    }

    static {
        applyPresetInternal(PRESET_BALANCED, true);
        enabled = false;
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("keycommand_speed.json").toFile();
    }

    public static void loadConfig() {
        enabled = false;
        applyPresetInternal(PRESET_BALANCED, true);
        showStatusHud = true;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                normalizeConfig();
                return;
            }

            JsonObject json = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();
            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }
            if (json.has("speedMode")) {
                speedMode = json.get("speedMode").getAsString();
            }
            if (json.has("presetId")) {
                presetId = json.get("presetId").getAsString();
            }
            if (json.has("useTimerBoost")) {
                useTimerBoost = json.get("useTimerBoost").getAsBoolean();
            }
            if (json.has("showStatusHud")) {
                showStatusHud = json.get("showStatusHud").getAsBoolean();
            }
            if (json.has("timerSpeed")) {
                timerSpeed = json.get("timerSpeed").getAsFloat();
            }
            if (json.has("jumpHeight")) {
                jumpHeight = json.get("jumpHeight").getAsFloat();
            }
            if (json.has("vanillaSpeed")) {
                vanillaSpeed = json.get("vanillaSpeed").getAsFloat();
            }

            normalizeConfig();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载加速配置失败", e);
        }
    }

    public static void saveConfig() {
        normalizeConfig();
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("speedMode", speedMode);
            json.addProperty("presetId", presetId);
            json.addProperty("useTimerBoost", useTimerBoost);
            json.addProperty("showStatusHud", showStatusHud);
            json.addProperty("timerSpeed", timerSpeed);
            json.addProperty("jumpHeight", jumpHeight);
            json.addProperty("vanillaSpeed", vanillaSpeed);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存加速配置失败", e);
        }
    }

    public static void applyPreset(String preset) {
        applyPresetInternal(preset, false);
        normalizeConfig();
    }

    public static void markCustomPreset() {
        presetId = PRESET_CUSTOM;
        normalizeConfig();
    }

    public void toggleEnabled() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean targetEnabled) {
        Minecraft mc = Minecraft.getMinecraft();
        if (enabled == targetEnabled) {
            saveConfig();
            return;
        }

        enabled = targetEnabled;
        resetRuntimeState(mc.player);

        if (!enabled) {
            resetTimer();
        }

        saveConfig();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(enabled
                    ? "§a[移动] 加速已开启"
                    : "§c[移动] 加速已关闭"));
        }
    }

    public void onClientDisconnect() {
        resetRuntimeState(Minecraft.getMinecraft().player);
        resetTimer();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) {
            return;
        }

        if (!enabled) {
            return;
        }

        if (player.isDead || player.getHealth() <= 0.0F) {
            resetTimer();
            resetRuntimeState(player);
            return;
        }

        if (player.isInWater() || player.isInLava() || player.isOnLadder() || isEntityInWeb(player)
                || player.isRiding()) {
            resetTimer();
            resetRuntimeState(player);
            return;
        }

        normalizeConfig();
        if (!isMoving(player)) {
            resetTimer();
            resetRuntimeState(player);
            return;
        }

        updateGroundAirState(player);
        this.activePathingMotionPlan = PathingSpeedController.plan(player, getRawConfiguredHorizontalSpeed(player));
        if (this.activePathingMotionPlan.isBraking()) {
            applyPathingControlledSpeed(player);
            return;
        }
        switch (speedMode) {
        case MODE_GROUND:
            applyGroundSpeed(player);
            break;
        case MODE_AIR:
            applyAirSpeed(player);
            break;
        case MODE_LOWHOP:
            applyLowHopSpeed(player);
            break;
        case MODE_ONGROUND:
            applyOnGroundSpeed(player);
            break;
        case MODE_BHOP:
        default:
            applyBhopSpeed(player);
            break;
        }
    }

    private void applyGroundSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        if (player.onGround) {
            if (player.movementInput != null && !player.movementInput.jump && player.motionY < 0.0D) {
                player.motionY = 0.0D;
            }
            playerSpeed = targetSpeed;
            setHorizontalSpeed(player, targetSpeed);
            return;
        }

        double carrySpeed = Math.max(baseSpeed, Math.min(getHorizontalSpeed(player), targetSpeed * 0.98D));
        playerSpeed = carrySpeed;
        if (player.motionY < 0.0D) {
            player.motionY *= 0.98D;
        }
        setHorizontalSpeed(player, carrySpeed);
    }

    private void applyAirSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        double jumpBoost = Math.max(0.30D, getEffectiveJumpHeight(player) * 0.85D);

        if (player.onGround) {
            player.motionY = jumpBoost;
            player.velocityChanged = true;
            playerSpeed = Math.max(targetSpeed * 0.98D, baseSpeed * 1.10D);
            setHorizontalSpeed(player, playerSpeed);
            return;
        }

        double currentSpeed = Math.max(getHorizontalSpeed(player), playerSpeed);
        double accelerationScale = 0.018D + Math.min(airTicks, 10) * 0.002D;
        double acceleration = Math.max(baseSpeed * 0.04D, targetSpeed * accelerationScale);
        double cap = Math.max(targetSpeed * 1.45D, baseSpeed * 1.40D);
        playerSpeed = Math.min(cap, currentSpeed + acceleration);
        if (player.motionY < 0.0D) {
            player.motionY *= 0.985D;
        }
        setHorizontalSpeed(player, playerSpeed);
    }

    private void applyBhopSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        if (player.onGround) {
            player.motionY = getEffectiveJumpHeight(player);
            player.velocityChanged = true;
            playerSpeed = Math.max(targetSpeed * 1.12D, baseSpeed * 1.85D);
            slowDown = true;
        } else {
            if (player.collidedHorizontally) {
                playerSpeed = targetSpeed;
                slowDown = false;
            } else if (slowDown) {
                playerSpeed = Math.max(targetSpeed, playerSpeed * 0.84D);
                slowDown = false;
            } else {
                playerSpeed = Math.max(targetSpeed, playerSpeed - playerSpeed / 159.0D);
            }
        }

        double cap = Math.max(targetSpeed * 1.28D, baseSpeed * 2.05D);
        playerSpeed = MathHelper.clamp(playerSpeed, targetSpeed, cap);
        setHorizontalSpeed(player, playerSpeed);
    }

    private void applyLowHopSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        double lowHopHeight = MathHelper.clamp(getEffectiveJumpHeight(player) * 0.58D, 0.12D, 0.24D);

        if (player.onGround) {
            player.motionY = lowHopHeight;
            player.velocityChanged = true;
            playerSpeed = Math.max(targetSpeed * 1.08D, baseSpeed * 1.35D);
            slowDown = true;
        } else {
            if (player.collidedHorizontally) {
                playerSpeed = targetSpeed;
                slowDown = false;
            } else if (player.motionY < 0.0D) {
                player.motionY *= 0.92D;
                playerSpeed = Math.max(targetSpeed, playerSpeed * 0.992D);
            } else if (slowDown) {
                playerSpeed = Math.max(targetSpeed, playerSpeed * 0.88D);
                slowDown = false;
            } else {
                playerSpeed = Math.max(targetSpeed, playerSpeed - playerSpeed / 220.0D);
            }
        }

        double cap = Math.max(targetSpeed * 1.18D, baseSpeed * 1.65D);
        playerSpeed = MathHelper.clamp(playerSpeed, targetSpeed, cap);
        setHorizontalSpeed(player, playerSpeed);
    }

    private void applyOnGroundSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        if (!player.onGround && (player.movementInput == null || !player.movementInput.jump)) {
            if (player.motionY > 0.0D) {
                player.motionY *= 0.65D;
            } else {
                player.motionY = Math.max(player.motionY, -0.08D);
            }
            player.onGround = true;
            player.fallDistance = 0.0F;
        }

        double onGroundMultiplier = groundTicks > 1 ? 1.05D : 1.03D;
        playerSpeed = Math.max(targetSpeed * onGroundMultiplier, baseSpeed * 1.10D);
        setHorizontalSpeed(player, playerSpeed);
    }

    private void applyOrbitRouteSpeed(EntityPlayerSP player) {
        resetTimer();
        slowDown = false;

        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = Math.max(baseSpeed, getConfiguredHorizontalSpeed(player));
        double currentSpeed = getHorizontalSpeed(player);
        double currentOrTrackedSpeed = Math.max(currentSpeed, playerSpeed > 1.0E-4D ? playerSpeed : baseSpeed);
        double[] orbitDirection = resolveOrbitDirection(player, currentSpeed);
        if (orbitDirection == null) {
            playerSpeed = Math.max(baseSpeed, Math.min(currentOrTrackedSpeed, targetSpeed));
            return;
        }

        double accelerationStep = player.onGround
                ? Math.max(0.035D, Math.min(0.160D, targetSpeed * 0.090D))
                : Math.max(0.026D, Math.min(0.120D, targetSpeed * 0.065D));
        double decelerationStep = player.onGround
                ? Math.max(0.045D, Math.min(0.200D, targetSpeed * 0.120D))
                : Math.max(0.032D, Math.min(0.145D, targetSpeed * 0.080D));

        double step = targetSpeed >= currentOrTrackedSpeed ? accelerationStep : decelerationStep;
        playerSpeed = approachDouble(currentOrTrackedSpeed, targetSpeed, step);

        if (player.collidedHorizontally) {
            playerSpeed = Math.max(baseSpeed, playerSpeed * 0.82D);
        }
        if (player.onGround && player.movementInput != null && !player.movementInput.jump && player.motionY < 0.0D) {
            player.motionY = 0.0D;
        }
        if (!player.onGround && player.motionY < -0.08D) {
            player.motionY = Math.max(player.motionY, -0.08D);
        }

        setPathLockedHorizontalSpeed(player, orbitDirection[0], orbitDirection[1], playerSpeed);
    }

    private double[] resolveOrbitDirection(EntityPlayerSP player, double currentSpeed) {
        double[] inputDirection = normalizeDirection(forward(player, 1.0D));
        if (inputDirection != null) {
            rememberOrbitDirection(inputDirection);
            return inputDirection;
        }

        double[] steeringDirection = getOrbitSteeringDirection();
        if (steeringDirection != null) {
            rememberOrbitDirection(steeringDirection);
            return steeringDirection;
        }

        if (currentSpeed > 0.08D) {
            double[] motionDirection = normalizeDirection(new double[] { player.motionX, player.motionZ });
            if (motionDirection != null) {
                rememberOrbitDirection(motionDirection);
                return motionDirection;
            }
        }

        if (orbitDirectionGraceTicks > 0 && !Double.isNaN(lastOrbitDirectionX) && !Double.isNaN(lastOrbitDirectionZ)) {
            orbitDirectionGraceTicks--;
            return new double[] { lastOrbitDirectionX, lastOrbitDirectionZ };
        }
        return null;
    }

    private void rememberOrbitDirection(double[] direction) {
        if (direction == null) {
            return;
        }
        lastOrbitDirectionX = direction[0];
        lastOrbitDirectionZ = direction[1];
        orbitDirectionGraceTicks = ORBIT_DIRECTION_GRACE_TICKS;
    }

    private double[] getOrbitSteeringDirection() {
        try {
            Object primary = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (!(primary instanceof Baritone)) {
                return null;
            }
            Rotation rotation = ((Baritone) primary).getLookBehavior().getEffectiveRotation().orElse(null);
            if (rotation == null) {
                return null;
            }
            double yawRad = Math.toRadians(rotation.getYaw());
            return normalizeDirection(new double[] { -Math.sin(yawRad), Math.cos(yawRad) });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private double getConfiguredHorizontalSpeed(EntityPlayerSP player) {
        double baseSpeed = getBaseMoveSpeed(player);
        double configuredSpeed = getRawConfiguredHorizontalSpeed(player);
        if (this.activePathingMotionPlan != null && this.activePathingMotionPlan.isActive()) {
            configuredSpeed = Math.min(configuredSpeed, this.activePathingMotionPlan.getTargetSpeed());
        }
        double minimumSpeed = this.activePathingMotionPlan != null && this.activePathingMotionPlan.isBraking()
                ? baseSpeed * 0.20D
                : baseSpeed;
        return Math.max(minimumSpeed, configuredSpeed);
    }

    private double getRawConfiguredHorizontalSpeed(EntityPlayerSP player) {
        double configuredSpeed = vanillaSpeed;
        return Math.max(getBaseMoveSpeed(player), configuredSpeed);
    }

    private void applyPathingControlledSpeed(EntityPlayerSP player) {
        resetTimer();
        slowDown = false;

        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        double currentSpeed = getHorizontalSpeed(player);
        double trackedSpeed = Math.max(currentSpeed, playerSpeed > 1.0E-4D ? playerSpeed : baseSpeed);
        double deceleration = this.activePathingMotionPlan != null && this.activePathingMotionPlan.isHardBrake()
                ? Math.max(0.10D, trackedSpeed * 0.42D)
                : Math.max(0.06D, trackedSpeed * 0.24D);
        playerSpeed = approachDouble(trackedSpeed, targetSpeed, deceleration);

        if (player.onGround && player.movementInput != null && !player.movementInput.jump && player.motionY < 0.0D) {
            player.motionY = 0.0D;
        }
        player.setSprinting(false);
        setHorizontalSpeed(player, playerSpeed);
    }

    private void setHorizontalSpeed(EntityPlayerSP player, double speed) {
        if (player == null) {
            return;
        }
        double[] dir = forward(player, speed);
        applyStableHorizontalMotion(player, dir[0], dir[1], speed);
        player.velocityChanged = true;
    }

    private void setHorizontalSpeed(EntityPlayerSP player, double directionX, double directionZ, double speed) {
        if (player == null) {
            return;
        }
        double magnitude = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (magnitude < 1.0E-4D) {
            return;
        }
        double desiredMotionX = directionX / magnitude * speed;
        double desiredMotionZ = directionZ / magnitude * speed;
        applyStableHorizontalMotion(player, desiredMotionX, desiredMotionZ, speed);
        player.velocityChanged = true;
    }

    private void setPathLockedHorizontalSpeed(EntityPlayerSP player, double directionX, double directionZ, double speed) {
        if (player == null) {
            return;
        }
        double magnitude = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (magnitude < 1.0E-4D) {
            return;
        }
        double desiredMotionX = directionX / magnitude * speed;
        double desiredMotionZ = directionZ / magnitude * speed;
        applyPathLockedHorizontalMotion(player, desiredMotionX, desiredMotionZ, speed);
        player.velocityChanged = true;
    }

    private void applyTimerState() {
        if (this.activePathingMotionPlan != null && this.activePathingMotionPlan.isBraking()) {
            resetTimer();
            return;
        }
        if (useTimerBoost) {
            setTimer(timerSpeed);
        } else {
            resetTimer();
        }
    }

    private void applyStableHorizontalMotion(EntityPlayerSP player, double desiredMotionX, double desiredMotionZ,
            double targetSpeed) {
        if (player == null) {
            return;
        }

        double desiredMagnitude = Math.sqrt(desiredMotionX * desiredMotionX + desiredMotionZ * desiredMotionZ);
        if (desiredMagnitude < 1.0E-4D) {
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            return;
        }

        double dirX = desiredMotionX / desiredMagnitude;
        double dirZ = desiredMotionZ / desiredMagnitude;
        double sideX = -dirZ;
        double sideZ = dirX;

        double currentX = player.motionX;
        double currentZ = player.motionZ;
        double currentSpeed = Math.sqrt(currentX * currentX + currentZ * currentZ);

        double currentParallel = currentX * dirX + currentZ * dirZ;
        double currentPerpendicular = currentX * sideX + currentZ * sideZ;

        double angleDifference = 0.0D;
        if (currentSpeed > 1.0E-4D) {
            double dot = MathHelper.clamp(currentParallel / currentSpeed, -1.0D, 1.0D);
            angleDifference = Math.toDegrees(Math.acos(dot));
        }

        double baseSpeed = getBaseMoveSpeed(player);
        double turnSlowdown = 1.0D - MathHelper.clamp((float) ((angleDifference - 12.0D) / 78.0D), 0.0F, 1.0F) * 0.72D;
        if (!player.onGround) {
            turnSlowdown *= player.motionY < -0.05D ? 0.78D : 0.88D;
        }
        if (player.collidedHorizontally) {
            turnSlowdown *= 0.60D;
        }

        double minimumTargetSpeed = this.activePathingMotionPlan != null && this.activePathingMotionPlan.isBraking()
                ? baseSpeed * 0.20D
                : baseSpeed * 0.85D;
        double stabilizedTargetSpeed = Math.max(minimumTargetSpeed, targetSpeed * turnSlowdown);

        double speedRatio = currentSpeed / Math.max(0.05D, baseSpeed);
        double lateralCorrectionFactor = 0.34D;
        double forwardCorrectionFactor = 0.28D;

        if (!player.onGround) {
            lateralCorrectionFactor *= 0.65D;
            forwardCorrectionFactor *= 0.55D;
        }
        if (player.motionY < -0.05D) {
            lateralCorrectionFactor *= 0.70D;
            forwardCorrectionFactor *= 0.75D;
        }
        if (angleDifference > 50.0D) {
            lateralCorrectionFactor *= 1.15D;
            forwardCorrectionFactor *= 0.65D;
        }

        double maxPerpendicularStep = Math.max(0.045D,
                stabilizedTargetSpeed * lateralCorrectionFactor / Math.max(1.0D, speedRatio * 0.85D));
        double maxParallelStep = Math.max(0.045D,
                stabilizedTargetSpeed * forwardCorrectionFactor / Math.max(1.0D, speedRatio * 0.55D));

        double nextPerpendicular = approachDouble(currentPerpendicular, 0.0D, maxPerpendicularStep);
        double nextParallel = approachDouble(currentParallel, stabilizedTargetSpeed, maxParallelStep);

        double nextMotionX = dirX * nextParallel + sideX * nextPerpendicular;
        double nextMotionZ = dirZ * nextParallel + sideZ * nextPerpendicular;

        player.motionX = nextMotionX;
        player.motionZ = nextMotionZ;
    }

    private void applyPathLockedHorizontalMotion(EntityPlayerSP player, double desiredMotionX, double desiredMotionZ,
            double targetSpeed) {
        if (player == null) {
            return;
        }

        double desiredMagnitude = Math.sqrt(desiredMotionX * desiredMotionX + desiredMotionZ * desiredMotionZ);
        if (desiredMagnitude < 1.0E-4D) {
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            return;
        }

        double dirX = desiredMotionX / desiredMagnitude;
        double dirZ = desiredMotionZ / desiredMagnitude;
        double sideX = -dirZ;
        double sideZ = dirX;

        double currentX = player.motionX;
        double currentZ = player.motionZ;
        double currentParallel = currentX * dirX + currentZ * dirZ;
        double currentPerpendicular = currentX * sideX + currentZ * sideZ;

        double baseSpeed = getBaseMoveSpeed(player);
        double desiredParallelSpeed = Math.max(baseSpeed * 0.92D, targetSpeed);
        double pathRecoveryPenalty = MathHelper.clamp((float) (Math.abs(currentPerpendicular) / ORBIT_PATH_RECOVERY_REFERENCE),
                0.0F, (float) ORBIT_PATH_RECOVERY_MAX_PENALTY);
        double recoveredParallelSpeed = Math.max(baseSpeed * 0.70D,
                desiredParallelSpeed * (1.0D - pathRecoveryPenalty));
        double maxParallelStep = Math.max(ORBIT_PATH_LOCK_MIN_PARALLEL_STEP,
                recoveredParallelSpeed * ORBIT_PATH_LOCK_PARALLEL_STEP_RATIO * (1.0D - pathRecoveryPenalty * 0.55D));
        double nextParallel = approachDouble(currentParallel, recoveredParallelSpeed, maxParallelStep);

        double maxPerpendicularStep = Math.max(0.08D, desiredParallelSpeed * ORBIT_PATH_LOCK_MAX_LATERAL_STEP);
        double nextPerpendicular = approachDouble(currentPerpendicular, 0.0D, maxPerpendicularStep);
        double maxAllowedPerpendicular = Math.max(0.012D, desiredParallelSpeed * ORBIT_PATH_LOCK_MAX_LATERAL_RATIO);
        nextPerpendicular = MathHelper.clamp(nextPerpendicular, -maxAllowedPerpendicular, maxAllowedPerpendicular);

        player.motionX = dirX * nextParallel + sideX * nextPerpendicular;
        player.motionZ = dirZ * nextParallel + sideZ * nextPerpendicular;
    }

    private static double getBaseMoveSpeed(EntityPlayerSP player) {
        double baseSpeed = 0.2873D;
        if (player == null) {
            return baseSpeed;
        }

        PotionEffect speedEffect = player.getActivePotionEffect(MobEffects.SPEED);
        if (speedEffect != null) {
            baseSpeed *= 1.0D + 0.2D * (speedEffect.getAmplifier() + 1);
        }

        PotionEffect slowEffect = player.getActivePotionEffect(MobEffects.SLOWNESS);
        if (slowEffect != null) {
            baseSpeed *= 1.0D - 0.15D * (slowEffect.getAmplifier() + 1);
        }

        return Math.max(0.05D, baseSpeed);
    }

    private double getEffectiveJumpHeight(EntityPlayerSP player) {
        double value = jumpHeight;
        if (player != null && player.isPotionActive(MobEffects.JUMP_BOOST)) {
            PotionEffect effect = player.getActivePotionEffect(MobEffects.JUMP_BOOST);
            if (effect != null) {
                value += (effect.getAmplifier() + 1) * 0.1D;
            }
        }
        return value;
    }

    private static boolean isMoving(EntityPlayerSP player) {
        return player != null && player.movementInput != null
                && (Math.abs(player.movementInput.moveForward) > 0.01F
                || Math.abs(player.movementInput.moveStrafe) > 0.01F);
    }

    private static double getHorizontalSpeed(EntityPlayerSP player) {
        if (player == null) {
            return 0.0D;
        }
        return Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    private static double[] normalizeDirection(double[] direction) {
        if (direction == null || direction.length < 2) {
            return null;
        }
        double magnitude = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]);
        if (magnitude < 1.0E-4D) {
            return null;
        }
        return new double[] { direction[0] / magnitude, direction[1] / magnitude };
    }

    private static double approachDouble(double current, double target, double maxStep) {
        if (maxStep <= 0.0D) {
            return current;
        }
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        if (current > target) {
            return Math.max(current - maxStep, target);
        }
        return current;
    }

    private static double[] forward(EntityPlayerSP player, double speed) {
        float forward = player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        float strafe = player.movementInput == null ? 0.0F : player.movementInput.moveStrafe;
        float yaw = player == null ? 0.0F : player.rotationYaw;

        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 1.0E-4D) {
            return new double[] { 0.0D, 0.0D };
        }
        if (length > 1.0D) {
            forward /= (float) length;
            strafe /= (float) length;
        }

        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double motionX = (-sin * forward + cos * strafe) * speed;
        double motionZ = (cos * forward + sin * strafe) * speed;
        return new double[] { motionX, motionZ };
    }

    private static double getDirectionRadians(EntityPlayerSP player) {
        if (player == null) {
            return 0.0D;
        }

        float forward = player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        float strafe = player.movementInput == null ? 0.0F : player.movementInput.moveStrafe;
        if (Math.abs(forward) < 1.0E-4F && Math.abs(strafe) < 1.0E-4F) {
            return Math.toRadians(player.rotationYaw);
        }

        double yawRad = Math.toRadians(player.rotationYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double worldX = -sin * forward + cos * strafe;
        double worldZ = cos * forward + sin * strafe;
        return Math.atan2(-worldX, worldZ);
    }

    public static List<String> getStatusLines() {
        return getStatusLines(false);
    }

    public static List<String> getStatusLines(boolean forcePreview) {
        List<String> lines = new ArrayList<>();
        if (!shouldRenderStatusHud(forcePreview)) {
            return lines;
        }

        lines.add("§b[加速] §f" + getModeDisplayName() + "  §7| 预设: §f" + getPresetDisplayName());
        String timerText = useTimerBoost ? ("§a开 §f" + formatFloat(timerSpeed) + "x") : "§c关";
        lines.add("§7Timer: " + timerText + "  §7| 跳高: §f" + formatFloat(jumpHeight) + "  §7| 速度: §f"
                + formatFloat(vanillaSpeed));
        return lines;
    }

    public static boolean shouldRenderStatusHud() {
        return shouldRenderStatusHud(false);
    }

    public static boolean shouldRenderStatusHud(boolean forcePreview) {
        return enabled && showStatusHud && (forcePreview || MovementFeatureManager.isMasterStatusHudEnabled());
    }

    public static String getPresetDisplayName() {
        if (PRESET_SAFE.equalsIgnoreCase(presetId)) {
            return "稳妥";
        }
        if (PRESET_AGGRESSIVE.equalsIgnoreCase(presetId)) {
            return "激进";
        }
        if (PRESET_CUSTOM.equalsIgnoreCase(presetId)) {
            return "自定义";
        }
        return "平衡";
    }

    public static String getModeDisplayName() {
        switch (normalizeSpeedMode(speedMode)) {
        case MODE_GROUND:
            return "Ground";
        case MODE_AIR:
            return "Air";
        case MODE_LOWHOP:
            return "LowHop";
        case MODE_ONGROUND:
            return "OnGround";
        case MODE_BHOP:
        default:
            return "Bhop";
        }
    }

    public static boolean usesJumpHeight() {
        return usesJumpHeight(speedMode);
    }

    public static boolean usesJumpHeight(String mode) {
        String normalizedMode = normalizeSpeedMode(mode);
        return MODE_AIR.equals(normalizedMode) || MODE_BHOP.equals(normalizedMode) || MODE_LOWHOP.equals(normalizedMode);
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static boolean isTimerManagedBySpeed() {
        return enabled && useTimerBoost;
    }

    public static float getCurrentTimerSpeedMultiplier() {
        if (enabled && useTimerBoost) {
            return Math.max(1.0F, timerSpeed);
        }
        if (MovementFeatureManager.isEnabled("timer_accel")) {
            return Math.max(1.0F,
                    MovementFeatureManager.getConfiguredValue("timer_accel", MovementFeatureManager.DEFAULT_TIMER_SPEED));
        }
        return 1.0F;
    }

    public static void applyTimerBoost(float speed) {
        setTimer(speed);
    }

    public static void restoreVanillaTimer() {
        resetTimer();
    }

    private static void setTimer(float speed) {
        Timer timer = getMinecraftTimer();
        Field tickLength = resolveTimerTickLengthField(timer);
        if (timer != null && tickLength != null) {
            try {
                tickLength.setFloat(timer, 50.0F / Math.max(0.1F, speed));
            } catch (Exception ignored) {
            }
        }
    }

    private static void resetTimer() {
        Timer timer = getMinecraftTimer();
        Field tickLength = resolveTimerTickLengthField(timer);
        if (timer != null && tickLength != null) {
            try {
                tickLength.setFloat(timer, 50.0F);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isEntityInWeb(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        try {
            Boolean inWeb = ReflectionCompat.getPrivateValue(net.minecraft.entity.Entity.class, player, "isInWeb",
                    "field_70134_J");
            return inWeb != null && inWeb;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Timer getMinecraftTimer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return null;
        }
        try {
            return ReflectionCompat.getPrivateValue(Minecraft.class, mc, "timer", "field_71428_T");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field resolveTimerTickLengthField(Timer timer) {
        if (timer == null) {
            return null;
        }
        if (timerTickLengthField != null) {
            return timerTickLengthField;
        }

        try {
            timerTickLengthField = Timer.class.getDeclaredField("tickLength");
            timerTickLengthField.setAccessible(true);
            return timerTickLengthField;
        } catch (Exception ignored) {
        }

        for (Field field : Timer.class.getDeclaredFields()) {
            if (field.getType() != float.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                float current = field.getFloat(timer);
                if (Math.abs(current - 50.0F) < 0.001F) {
                    timerTickLengthField = field;
                    return timerTickLengthField;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static void applyPresetInternal(String preset, boolean keepCustomValuesForCustom) {
        String normalizedPreset = normalizePresetId(preset);
        presetId = normalizedPreset;
        if (PRESET_CUSTOM.equals(normalizedPreset) && keepCustomValuesForCustom) {
            return;
        }

        if (PRESET_SAFE.equals(normalizedPreset)) {
            useTimerBoost = false;
            timerSpeed = 1.00F;
            jumpHeight = 0.40F;
            vanillaSpeed = 0.72F;
            return;
        }
        if (PRESET_AGGRESSIVE.equals(normalizedPreset)) {
            useTimerBoost = true;
            timerSpeed = 1.16F;
            jumpHeight = 0.42F;
            vanillaSpeed = 1.05F;
            return;
        }
        if (PRESET_BALANCED.equals(normalizedPreset)) {
            useTimerBoost = true;
            timerSpeed = 1.08F;
            jumpHeight = 0.41F;
            vanillaSpeed = 0.90F;
            return;
        }
    }

    private void resetRuntimeState(EntityPlayerSP player) {
        slowDown = false;
        airTicks = 0;
        groundTicks = 0;
        playerSpeed = getBaseMoveSpeed(player);
        lastOrbitDirectionX = Double.NaN;
        lastOrbitDirectionZ = Double.NaN;
        orbitDirectionGraceTicks = 0;
        activePathingMotionPlan = null;
    }

    private void updateGroundAirState(EntityPlayerSP player) {
        if (player != null && player.onGround) {
            groundTicks++;
            airTicks = 0;
        } else {
            airTicks++;
            groundTicks = 0;
        }
    }

    private static String normalizeSpeedMode(String mode) {
        if (mode == null) {
            return MODE_BHOP;
        }
        if ("STRAFE".equalsIgnoreCase(mode)) {
            return MODE_BHOP;
        }
        if ("VANILLA".equalsIgnoreCase(mode)) {
            return MODE_GROUND;
        }
        if (MODE_GROUND.equalsIgnoreCase(mode)) {
            return MODE_GROUND;
        }
        if (MODE_AIR.equalsIgnoreCase(mode)) {
            return MODE_AIR;
        }
        if (MODE_LOWHOP.equalsIgnoreCase(mode)) {
            return MODE_LOWHOP;
        }
        if (MODE_ONGROUND.equalsIgnoreCase(mode)) {
            return MODE_ONGROUND;
        }
        return MODE_BHOP;
    }

    private static String normalizePresetId(String preset) {
        if (PRESET_SAFE.equalsIgnoreCase(preset)) {
            return PRESET_SAFE;
        }
        if (PRESET_AGGRESSIVE.equalsIgnoreCase(preset)) {
            return PRESET_AGGRESSIVE;
        }
        if (PRESET_CUSTOM.equalsIgnoreCase(preset)) {
            return PRESET_CUSTOM;
        }
        return PRESET_BALANCED;
    }

    private static void normalizeConfig() {
        speedMode = normalizeSpeedMode(speedMode);
        presetId = normalizePresetId(presetId);
        timerSpeed = MathHelper.clamp(timerSpeed, 1.00F, 2.50F);
        jumpHeight = MathHelper.clamp(jumpHeight, 0.00F, 1.00F);
        vanillaSpeed = MathHelper.clamp(vanillaSpeed, 0.10F, 3.00F);
    }
}

