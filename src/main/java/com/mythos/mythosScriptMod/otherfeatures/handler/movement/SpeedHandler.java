package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.PathingSpeedController;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.ReflectionCompat;
import com.mythos.mythosScriptMod.mythosScriptMod;
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
import java.util.concurrent.ConcurrentHashMap;

public class SpeedHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public String speedMode = MODE_BHOP;
        public String presetId = PRESET_BALANCED;
        public boolean useTimerBoost = true;
        public boolean showStatusHud = true;
        public float timerSpeed = 1.08F;
        public float jumpHeight = 0.41F;
        public float vanillaSpeed = 0.90F;
    }

    /** 当前线程所属会话的配置；bot 线程取自己的，客户端线程取挂载中的会话。 */
    public static OwnerConfig cfg() {
        String owner = com.mythos.mythosScriptMod.system.ProfileManager.currentConfigOwner();
        OwnerConfig config = CONFIGS.get(owner);
        return config != null ? config : loadOwnerConfig(owner);
    }

    // 不能用 computeIfAbsent：读盘过程中会反复调用 cfg()，ConcurrentHashMap 不允许递归更新。
    private static OwnerConfig loadOwnerConfig(String owner) {
        synchronized (com.mythos.mythosScriptMod.system.ProfileManager.CONFIG_LOAD_LOCK) {
            OwnerConfig existing = CONFIGS.get(owner);
            if (existing != null) {
                return existing;
            }
            OwnerConfig config = new OwnerConfig();
            CONFIGS.put(owner, config);
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) SpeedHandler::loadConfig);
            return config;
        }
    }


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

    /** Enabled is per-owner runtime state — keep it in RuntimeState so one
     *  bot's toggle can't stomp another's. Legacy: use rt().enabled. */
    private static boolean isEnabled() { return rt().enabled; }
    private static void setEnabledInternal(boolean v) { rt().enabled = v; }
    public boolean isEnabledNow() { return rt().enabled; }

    private static Field timerTickLengthField;
    private static final int ORBIT_DIRECTION_GRACE_TICKS = 3;
    private static final double ORBIT_PATH_LOCK_MAX_LATERAL_RATIO = 0.035D;
    private static final double ORBIT_PATH_LOCK_MAX_LATERAL_STEP = 0.22D;
    private static final double ORBIT_PATH_LOCK_PARALLEL_STEP_RATIO = 0.32D;
    private static final double ORBIT_PATH_LOCK_MIN_PARALLEL_STEP = 0.055D;
    private static final double ORBIT_PATH_RECOVERY_REFERENCE = 0.18D;
    private static final double ORBIT_PATH_RECOVERY_MAX_PENALTY = 0.72D;

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static final class RuntimeState {
        private boolean enabled = false;
        private boolean slowDown = false;
        private double playerSpeed = 0.2873D;
        private int airTicks = 0;
        private int groundTicks = 0;
        private double lastOrbitDirectionX = Double.NaN;
        private double lastOrbitDirectionZ = Double.NaN;
        private int orbitDirectionGraceTicks = 0;
        private PathingSpeedController.MotionPlan activePathingMotionPlan;
    }

    private SpeedHandler() {
    }

    static {
        applyPresetInternal(PRESET_BALANCED, true);
        setEnabledInternal(false);
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("keycommand_speed.json").toFile();
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("keycommand_speed.json").toFile();
    }

    public static void loadConfig() {
        setEnabledInternal(false);
        applyPresetInternal(PRESET_BALANCED, true);
        cfg().showStatusHud = true;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                normalizeConfig();
                return;
            }

            JsonObject json;

            try (FileReader reader = new FileReader(configFile)) {

                json = new JsonParser().parse(reader).getAsJsonObject();

            }
            File runtimeFile = getRuntimeConfigFile();
            if (runtimeFile.exists()) {
                try (FileReader rr = new FileReader(runtimeFile)) {
                    JsonObject rj = new JsonParser().parse(rr).getAsJsonObject();
                    if (rj.has("enabled")) {
                        setEnabledInternal(rj.get("enabled").getAsBoolean());
                    }
                }
            } else if (json.has("enabled")) {
                setEnabledInternal(json.get("enabled").getAsBoolean());
            }
            if (json.has("speedMode")) {
                cfg().speedMode = json.get("speedMode").getAsString();
            }
            if (json.has("presetId")) {
                cfg().presetId = json.get("presetId").getAsString();
            }
            if (json.has("useTimerBoost")) {
                cfg().useTimerBoost = json.get("useTimerBoost").getAsBoolean();
            }
            if (json.has("showStatusHud")) {
                cfg().showStatusHud = json.get("showStatusHud").getAsBoolean();
            }
            if (json.has("timerSpeed")) {
                cfg().timerSpeed = json.get("timerSpeed").getAsFloat();
            }
            if (json.has("jumpHeight")) {
                cfg().jumpHeight = json.get("jumpHeight").getAsFloat();
            }
            if (json.has("vanillaSpeed")) {
                cfg().vanillaSpeed = json.get("vanillaSpeed").getAsFloat();
            }

            normalizeConfig();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载加速配置失败", e);
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
            json.addProperty("speedMode", cfg().speedMode);
            json.addProperty("presetId", cfg().presetId);
            json.addProperty("useTimerBoost", cfg().useTimerBoost);
            json.addProperty("showStatusHud", cfg().showStatusHud);
            json.addProperty("timerSpeed", cfg().timerSpeed);
            json.addProperty("jumpHeight", cfg().jumpHeight);
            json.addProperty("vanillaSpeed", cfg().vanillaSpeed);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }

            File runtimeFile = getRuntimeConfigFile();
            if (!runtimeFile.getParentFile().exists()) {
                runtimeFile.getParentFile().mkdirs();
            }
            JsonObject rjson = new JsonObject();
            rjson.addProperty("enabled", isEnabled());
            try (FileWriter rw = new FileWriter(runtimeFile)) {
                rw.write(rjson.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存加速配置失败", e);
        }
    }

    public static void applyPreset(String preset) {
        applyPresetInternal(preset, false);
        normalizeConfig();
    }

    public static void markCustomPreset() {
        cfg().presetId = PRESET_CUSTOM;
        normalizeConfig();
    }

    public void toggleEnabled() {
        setEnabled(!isEnabled());
    }

    public void setEnabled(boolean targetEnabled) {
        if (isEnabled() == targetEnabled) {
            saveConfig();
            return;
        }

        setEnabledInternal(targetEnabled);
        resetRuntimeState(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer());

        if (!isEnabled()) {
            resetTimer();
        }

        saveConfig();
        EntityPlayerSP bp = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (bp != null) {
            bp.sendMessage(new TextComponentString(isEnabled()
                    ? "§a[移动] 加速已开启"
                    : "§c[移动] 加速已关闭"));
        }
    }

    public void onClientDisconnect() {
        resetRuntimeState(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer());
        resetTimer();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayerSP player = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (player == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null) {
            return;
        }

        if (!isEnabled()) {
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
        rt().activePathingMotionPlan = PathingSpeedController.plan(player, getRawConfiguredHorizontalSpeed(player));
        if (rt().activePathingMotionPlan.isBraking()) {
            applyPathingControlledSpeed(player);
            return;
        }
        switch (cfg().speedMode) {
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
        RuntimeState rt = rt();
        if (player.onGround) {
            if (player.movementInput != null && !player.movementInput.jump && player.motionY < 0.0D) {
                player.motionY = 0.0D;
            }
            rt.playerSpeed = targetSpeed;
            setHorizontalSpeed(player, targetSpeed);
            return;
        }

        double carrySpeed = Math.max(baseSpeed, Math.min(getHorizontalSpeed(player), targetSpeed * 0.98D));
        rt.playerSpeed = carrySpeed;
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

        RuntimeState rt = rt();
        if (player.onGround) {
            player.motionY = jumpBoost;
            player.velocityChanged = true;
            rt.playerSpeed = Math.max(targetSpeed * 0.98D, baseSpeed * 1.10D);
            setHorizontalSpeed(player, rt.playerSpeed);
            return;
        }

        double currentSpeed = Math.max(getHorizontalSpeed(player), rt.playerSpeed);
        double accelerationScale = 0.018D + Math.min(rt.airTicks, 10) * 0.002D;
        double acceleration = Math.max(baseSpeed * 0.04D, targetSpeed * accelerationScale);
        double cap = Math.max(targetSpeed * 1.45D, baseSpeed * 1.40D);
        rt.playerSpeed = Math.min(cap, currentSpeed + acceleration);
        if (player.motionY < 0.0D) {
            player.motionY *= 0.985D;
        }
        setHorizontalSpeed(player, rt.playerSpeed);
    }

    private void applyBhopSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        RuntimeState rt = rt();
        if (player.onGround) {
            player.motionY = getEffectiveJumpHeight(player);
            player.velocityChanged = true;
            rt.playerSpeed = Math.max(targetSpeed * 1.12D, baseSpeed * 1.85D);
            rt.slowDown = true;
        } else {
            if (player.collidedHorizontally) {
                rt.playerSpeed = targetSpeed;
                rt.slowDown = false;
            } else if (rt.slowDown) {
                rt.playerSpeed = Math.max(targetSpeed, rt.playerSpeed * 0.84D);
                rt.slowDown = false;
            } else {
                rt.playerSpeed = Math.max(targetSpeed, rt.playerSpeed - rt.playerSpeed / 159.0D);
            }
        }

        double cap = Math.max(targetSpeed * 1.28D, baseSpeed * 2.05D);
        rt.playerSpeed = MathHelper.clamp(rt.playerSpeed, targetSpeed, cap);
        setHorizontalSpeed(player, rt.playerSpeed);
    }

    private void applyLowHopSpeed(EntityPlayerSP player) {
        applyTimerState();
        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        double lowHopHeight = MathHelper.clamp(getEffectiveJumpHeight(player) * 0.58D, 0.12D, 0.24D);

        RuntimeState rt = rt();
        if (player.onGround) {
            player.motionY = lowHopHeight;
            player.velocityChanged = true;
            rt.playerSpeed = Math.max(targetSpeed * 1.08D, baseSpeed * 1.35D);
            rt.slowDown = true;
        } else {
            if (player.collidedHorizontally) {
                rt.playerSpeed = targetSpeed;
                rt.slowDown = false;
            } else if (player.motionY < 0.0D) {
                player.motionY *= 0.92D;
                rt.playerSpeed = Math.max(targetSpeed, rt.playerSpeed * 0.992D);
            } else if (rt.slowDown) {
                rt.playerSpeed = Math.max(targetSpeed, rt.playerSpeed * 0.88D);
                rt.slowDown = false;
            } else {
                rt.playerSpeed = Math.max(targetSpeed, rt.playerSpeed - rt.playerSpeed / 220.0D);
            }
        }

        double cap = Math.max(targetSpeed * 1.18D, baseSpeed * 1.65D);
        rt.playerSpeed = MathHelper.clamp(rt.playerSpeed, targetSpeed, cap);
        setHorizontalSpeed(player, rt.playerSpeed);
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

        RuntimeState rt = rt();
        double onGroundMultiplier = rt.groundTicks > 1 ? 1.05D : 1.03D;
        rt.playerSpeed = Math.max(targetSpeed * onGroundMultiplier, baseSpeed * 1.10D);
        setHorizontalSpeed(player, rt.playerSpeed);
    }



    private void rememberOrbitDirection(double[] direction) {
        if (direction == null) {
            return;
        }
        RuntimeState rt = rt();
        rt.lastOrbitDirectionX = direction[0];
        rt.lastOrbitDirectionZ = direction[1];
        rt.orbitDirectionGraceTicks = ORBIT_DIRECTION_GRACE_TICKS;
    }


    private double getConfiguredHorizontalSpeed(EntityPlayerSP player) {
        double baseSpeed = getBaseMoveSpeed(player);
        double configuredSpeed = getRawConfiguredHorizontalSpeed(player);
        RuntimeState rt = rt();
        if (rt.activePathingMotionPlan != null && rt.activePathingMotionPlan.isActive()) {
            configuredSpeed = Math.min(configuredSpeed, rt.activePathingMotionPlan.getTargetSpeed());
        }
        double minimumSpeed = rt.activePathingMotionPlan != null && rt.activePathingMotionPlan.isBraking()
                ? baseSpeed * 0.20D
                : baseSpeed;
        return Math.max(minimumSpeed, configuredSpeed);
    }

    private double getRawConfiguredHorizontalSpeed(EntityPlayerSP player) {
        double configuredSpeed = cfg().vanillaSpeed;
        return Math.max(getBaseMoveSpeed(player), configuredSpeed);
    }

    private void applyPathingControlledSpeed(EntityPlayerSP player) {
        resetTimer();
        RuntimeState rt = rt();
        rt.slowDown = false;

        double baseSpeed = getBaseMoveSpeed(player);
        double targetSpeed = getConfiguredHorizontalSpeed(player);
        double currentSpeed = getHorizontalSpeed(player);
        double trackedSpeed = Math.max(currentSpeed, rt.playerSpeed > 1.0E-4D ? rt.playerSpeed : baseSpeed);
        double deceleration = rt.activePathingMotionPlan != null && rt.activePathingMotionPlan.isHardBrake()
                ? Math.max(0.10D, trackedSpeed * 0.42D)
                : Math.max(0.06D, trackedSpeed * 0.24D);
        rt.playerSpeed = approachDouble(trackedSpeed, targetSpeed, deceleration);

        if (player.onGround && player.movementInput != null && !player.movementInput.jump && player.motionY < 0.0D) {
            player.motionY = 0.0D;
        }
        player.setSprinting(false);
        setHorizontalSpeed(player, rt.playerSpeed);
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


    private void applyTimerState() {
        if (rt().activePathingMotionPlan != null && rt().activePathingMotionPlan.isBraking()) {
            resetTimer();
            return;
        }
        if (cfg().useTimerBoost) {
            setTimer(cfg().timerSpeed);
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

        RuntimeState rt = rt();
        double minimumTargetSpeed = rt.activePathingMotionPlan != null && rt.activePathingMotionPlan.isBraking()
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
        double value = cfg().jumpHeight;
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


    public static List<String> getStatusLines() {
        return getStatusLines(false);
    }

    public static List<String> getStatusLines(boolean forcePreview) {
        List<String> lines = new ArrayList<>();
        if (!shouldRenderStatusHud(forcePreview)) {
            return lines;
        }

        lines.add("§b[加速] §f" + getModeDisplayName() + "  §7| 预设: §f" + getPresetDisplayName());
        String timerText = cfg().useTimerBoost ? ("§a开 §f" + formatFloat(cfg().timerSpeed) + "x") : "§c关";
        lines.add("§7Timer: " + timerText + "  §7| 跳高: §f" + formatFloat(cfg().jumpHeight) + "  §7| 速度: §f"
                + formatFloat(cfg().vanillaSpeed));
        return lines;
    }

    public static boolean shouldRenderStatusHud() {
        return shouldRenderStatusHud(false);
    }

    public static boolean shouldRenderStatusHud(boolean forcePreview) {
        return isEnabled() && cfg().showStatusHud && (forcePreview || MovementFeatureManager.isMasterStatusHudEnabled());
    }

    public static String getPresetDisplayName() {
        if (PRESET_SAFE.equalsIgnoreCase(cfg().presetId)) {
            return "稳妥";
        }
        if (PRESET_AGGRESSIVE.equalsIgnoreCase(cfg().presetId)) {
            return "激进";
        }
        if (PRESET_CUSTOM.equalsIgnoreCase(cfg().presetId)) {
            return "自定义";
        }
        return "平衡";
    }

    public static String getModeDisplayName() {
        switch (normalizeSpeedMode(cfg().speedMode)) {
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
        return usesJumpHeight(cfg().speedMode);
    }

    public static boolean usesJumpHeight(String mode) {
        String normalizedMode = normalizeSpeedMode(mode);
        return MODE_AIR.equals(normalizedMode) || MODE_BHOP.equals(normalizedMode) || MODE_LOWHOP.equals(normalizedMode);
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static boolean isTimerManagedBySpeed() {
        return isEnabled() && cfg().useTimerBoost;
    }

    public static float getCurrentTimerSpeedMultiplier() {
        if (isEnabled() && cfg().useTimerBoost) {
            return Math.max(1.0F, cfg().timerSpeed);
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
        cfg().presetId = normalizedPreset;
        if (PRESET_CUSTOM.equals(normalizedPreset) && keepCustomValuesForCustom) {
            return;
        }

        if (PRESET_SAFE.equals(normalizedPreset)) {
            cfg().useTimerBoost = false;
            cfg().timerSpeed = 1.00F;
            cfg().jumpHeight = 0.40F;
            cfg().vanillaSpeed = 0.72F;
            return;
        }
        if (PRESET_AGGRESSIVE.equals(normalizedPreset)) {
            cfg().useTimerBoost = true;
            cfg().timerSpeed = 1.16F;
            cfg().jumpHeight = 0.42F;
            cfg().vanillaSpeed = 1.05F;
            return;
        }
        if (PRESET_BALANCED.equals(normalizedPreset)) {
            cfg().useTimerBoost = true;
            cfg().timerSpeed = 1.08F;
            cfg().jumpHeight = 0.41F;
            cfg().vanillaSpeed = 0.90F;
            return;
        }
    }

    private void resetRuntimeState(EntityPlayerSP player) {
        RuntimeState rt = rt();
        rt.slowDown = false;
        rt.airTicks = 0;
        rt.groundTicks = 0;
        rt.playerSpeed = getBaseMoveSpeed(player);
        rt.lastOrbitDirectionX = Double.NaN;
        rt.lastOrbitDirectionZ = Double.NaN;
        rt.orbitDirectionGraceTicks = 0;
        rt.activePathingMotionPlan = null;
    }

    private void updateGroundAirState(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player != null && player.onGround) {
            rt.groundTicks++;
            rt.airTicks = 0;
        } else {
            rt.airTicks++;
            rt.groundTicks = 0;
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
        cfg().speedMode = normalizeSpeedMode(cfg().speedMode);
        cfg().presetId = normalizePresetId(cfg().presetId);
        cfg().timerSpeed = MathHelper.clamp(cfg().timerSpeed, 1.00F, 15.00F);
        cfg().jumpHeight = MathHelper.clamp(cfg().jumpHeight, 0.00F, 1.00F);
        cfg().vanillaSpeed = MathHelper.clamp(cfg().vanillaSpeed, 0.10F, 15.00F);
    }
}

