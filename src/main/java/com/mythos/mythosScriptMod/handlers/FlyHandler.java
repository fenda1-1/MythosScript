package com.mythos.mythosScriptMod.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.config.FlightPathingConfig;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class FlyHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public String flightMode = MODE_MOTION;
        public boolean autoTakeoff = false;
        public boolean stopMotionOnDisable = true;
        public boolean enableNoCollision = true;
        public boolean enableAntiKnockback = true;
        public boolean enableAntiKick = true;
        public float horizontalSpeed = 0.85F;
        public float verticalSpeed = 0.42F;
        public float glideFallSpeed = 0.04F;
        public float sprintMultiplier = 1.25F;
        public float pulseBoost = 0.28F;
        public int pulseIntervalTicks = 4;
        public int antiKickIntervalTicks = 16;
        public float antiKickDistance = 0.04F;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) FlyHandler::loadConfig);
            return config;
        }
    }


    public static final FlyHandler INSTANCE = new FlyHandler();

    public static final String MODE_MOTION = "MOTION";
    public static final String MODE_GLIDE = "GLIDE";
    public static final String MODE_PULSE = "PULSE";




    private static final java.util.concurrent.ConcurrentHashMap<String, RuntimeState> STATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static final class RuntimeState {
        boolean enabled = false;
        int tickCounter = 0;
        int pendingTakeoffTicks = 0;
        volatile double pathingHorizontalSpeedCap = Double.POSITIVE_INFINITY;
        volatile double pathingVerticalSpeedCap = Double.POSITIVE_INFINITY;
        volatile double pathingHoldY = Double.NaN;
    }

    private FlyHandler() {
    }

    static {
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("keycommand_fly.json").toFile();
    }

    public static void loadConfig() {
        rt().enabled = false;
        cfg().flightMode = MODE_MOTION;
        cfg().autoTakeoff = true;
        cfg().stopMotionOnDisable = true;
        cfg().enableNoCollision = true;
        cfg().enableAntiKnockback = true;
        cfg().enableAntiKick = true;
        cfg().horizontalSpeed = 0.85F;
        cfg().verticalSpeed = 0.42F;
        cfg().glideFallSpeed = 0.04F;
        cfg().sprintMultiplier = 1.25F;
        cfg().pulseBoost = 0.28F;
        cfg().pulseIntervalTicks = 4;
        cfg().antiKickIntervalTicks = 16;
        cfg().antiKickDistance = 0.04F;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                return;
            }

            JsonObject json;

            try (FileReader reader = new FileReader(configFile)) {

                json = new JsonParser().parse(reader).getAsJsonObject();

            }
            // `enabled` is runtime state, not persisted config — a saved
            // "enabled":true from bot2 would bleed into every other session
            // owner via rt() and make all bots fly simultaneously.
            if (json.has("enabled")) {
                rt().enabled = false;
            }
            if (json.has("flightMode")) {
                cfg().flightMode = json.get("flightMode").getAsString();
            }
            if (json.has("autoTakeoff")) {
                cfg().autoTakeoff = json.get("autoTakeoff").getAsBoolean();
            }
            if (json.has("stopMotionOnDisable")) {
                cfg().stopMotionOnDisable = json.get("stopMotionOnDisable").getAsBoolean();
            }
            if (json.has("enableNoCollision")) {
                cfg().enableNoCollision = json.get("enableNoCollision").getAsBoolean();
            }
            if (json.has("enableAntiKnockback")) {
                cfg().enableAntiKnockback = json.get("enableAntiKnockback").getAsBoolean();
            }
            if (json.has("enableAntiKick")) {
                cfg().enableAntiKick = json.get("enableAntiKick").getAsBoolean();
            }
            if (json.has("horizontalSpeed")) {
                cfg().horizontalSpeed = json.get("horizontalSpeed").getAsFloat();
            }
            if (json.has("verticalSpeed")) {
                cfg().verticalSpeed = json.get("verticalSpeed").getAsFloat();
            }
            if (json.has("glideFallSpeed")) {
                cfg().glideFallSpeed = json.get("glideFallSpeed").getAsFloat();
            }
            if (json.has("sprintMultiplier")) {
                cfg().sprintMultiplier = json.get("sprintMultiplier").getAsFloat();
            }
            if (json.has("pulseBoost")) {
                cfg().pulseBoost = json.get("pulseBoost").getAsFloat();
            }
            if (json.has("pulseIntervalTicks")) {
                cfg().pulseIntervalTicks = json.get("pulseIntervalTicks").getAsInt();
            }
            if (json.has("antiKickIntervalTicks")) {
                cfg().antiKickIntervalTicks = json.get("antiKickIntervalTicks").getAsInt();
            }
            if (json.has("antiKickDistance")) {
                cfg().antiKickDistance = json.get("antiKickDistance").getAsFloat();
            }

            normalizeConfig();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error(I18n.format("log.fly.load_failed"), e);
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
            json.addProperty("flightMode", cfg().flightMode);
            json.addProperty("autoTakeoff", cfg().autoTakeoff);
            json.addProperty("stopMotionOnDisable", cfg().stopMotionOnDisable);
            json.addProperty("enableNoCollision", cfg().enableNoCollision);
            json.addProperty("enableAntiKnockback", cfg().enableAntiKnockback);
            json.addProperty("enableAntiKick", cfg().enableAntiKick);
            json.addProperty("horizontalSpeed", cfg().horizontalSpeed);
            json.addProperty("verticalSpeed", cfg().verticalSpeed);
            json.addProperty("glideFallSpeed", cfg().glideFallSpeed);
            json.addProperty("sprintMultiplier", cfg().sprintMultiplier);
            json.addProperty("pulseBoost", cfg().pulseBoost);
            json.addProperty("pulseIntervalTicks", cfg().pulseIntervalTicks);
            json.addProperty("antiKickIntervalTicks", cfg().antiKickIntervalTicks);
            json.addProperty("antiKickDistance", cfg().antiKickDistance);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error(I18n.format("log.fly.save_failed"), e);
        }
    }

    public void toggleEnabled() {
        setEnabled(!rt().enabled);
    }

    // ------------------------------------------------------------------
    // Runtime-switch accessors (boxed RuntimeState fields)
    // ------------------------------------------------------------------

    public static boolean enabled() { return rt().enabled; }

    public static void setEnabledSilently(boolean targetEnabled) { rt().enabled = targetEnabled; }

    public void setEnabled(boolean targetEnabled) {
        normalizeConfig();
        if (rt().enabled == targetEnabled) {
            saveConfig();
            return;
        }

        applyEnabledState(targetEnabled);

        saveConfig();

        EntityPlayerSP bp = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (bp != null) {
            bp.sendMessage(new TextComponentString(
                    I18n.format(rt().enabled ? "msg.fly.enabled" : "msg.fly.disabled")));
        }
    }

    /**
     * Applies an enable-state change for an unsaved settings draft. Persistence
     * and user-facing chat messages remain owned by {@link #setEnabled}.
     */
    public void setEnabledForSettingsPreview(boolean targetEnabled) {
        normalizeConfig();
        if (rt().enabled != targetEnabled) {
            applyEnabledState(targetEnabled);
        }
    }

    private void applyEnabledState(boolean targetEnabled) {
        rt().enabled = targetEnabled;
        rt().tickCounter = 0;
        rt().pendingTakeoffTicks = rt().enabled && cfg().autoTakeoff ? 6 : 0;

        if (!rt().enabled) {
            clearPathingHoldAltitude();
            stopFlight(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer(), cfg().stopMotionOnDisable);
        }
    }

    public void onClientDisconnect() {
        if (!FlightPathingConfig.cfg().keepFlyEnabledOnDisconnect) {
            rt().enabled = false;
        }
        rt().tickCounter = 0;
        rt().pendingTakeoffTicks = 0;
        rt().pathingHorizontalSpeedCap = Double.POSITIVE_INFINITY;
        rt().pathingVerticalSpeedCap = Double.POSITIVE_INFINITY;
        clearPathingHoldAltitude();
        stopFlight(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer(), true);
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

        if (!rt().enabled) {
            return;
        }

        if (player.isDead || player.getHealth() <= 0.0F) {
            return;
        }

        normalizeConfig();
        rt().tickCounter++;
        applyFlight(player);
    }

    private void applyFlight(EntityPlayerSP player) {
        if (player == null) {
            return;
        }

        boolean jump = player.movementInput != null && player.movementInput.jump;
        boolean sneak = player.movementInput != null && player.movementInput.sneak;

        double[] horizontalMotion = computeHorizontalMotion(player);
        double motionX = horizontalMotion[0];
        double motionZ = horizontalMotion[1];
        double motionY = computeVerticalMotion(player, jump, sneak);
        if (Double.isFinite(rt().pathingHoldY)) {
            motionX = 0.0D;
            motionZ = 0.0D;
            double yError = rt().pathingHoldY - player.posY;
            motionY = Math.max(-cfg().verticalSpeed, Math.min(cfg().verticalSpeed, yError * 0.45D));
            if (Math.abs(yError) < 0.03D) {
                motionY = 0.0D;
            }
        }
        rt().pathingHorizontalSpeedCap = Double.POSITIVE_INFINITY;
        rt().pathingVerticalSpeedCap = Double.POSITIVE_INFINITY;

        if (cfg().enableAntiKick && !jump && !sneak && shouldApplyAntiKickPulse()) {
            motionY = Math.min(motionY, -cfg().antiKickDistance);
        }

        player.motionX = motionX;
        player.motionY = motionY;
        player.motionZ = motionZ;
        player.fallDistance = 0.0F;
        player.onGround = false;
        player.velocityChanged = true;
    }

    private double[] computeHorizontalMotion(EntityPlayerSP player) {
        float forward = player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        float strafe = player.movementInput == null ? 0.0F : player.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);

        if (length < 0.01D) {
            return new double[] { 0.0D, 0.0D };
        }

        forward /= (float) length;
        strafe /= (float) length;

        double speed = cfg().horizontalSpeed;
        if (player.isSprinting()) {
            speed *= cfg().sprintMultiplier;
        }
        speed = Math.min(speed, rt().pathingHorizontalSpeedCap);

        double yawRad = Math.toRadians(player.rotationYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double motionX = (-sin * forward + cos * strafe) * speed;
        double motionZ = (cos * forward + sin * strafe) * speed;
        return new double[] { motionX, motionZ };
    }

    private double computeVerticalMotion(EntityPlayerSP player, boolean jump, boolean sneak) {
        if (rt().pendingTakeoffTicks > 0 && cfg().autoTakeoff && player.onGround && !jump && !sneak) {
            rt().pendingTakeoffTicks--;
            return Math.max(0.24D, cfg().pulseBoost);
        }

        if (!player.onGround) {
            rt().pendingTakeoffTicks = 0;
        }

        if (jump) {
            return Math.min(cfg().verticalSpeed, rt().pathingVerticalSpeedCap);
        }
        if (sneak) {
            return -Math.min(cfg().verticalSpeed, rt().pathingVerticalSpeedCap);
        }

        if (MODE_GLIDE.equalsIgnoreCase(cfg().flightMode)) {
            return -cfg().glideFallSpeed;
        }
        if (MODE_PULSE.equalsIgnoreCase(cfg().flightMode)) {
            return (rt().tickCounter % cfg().pulseIntervalTicks == 0) ? cfg().pulseBoost : -cfg().glideFallSpeed;
        }
        return 0.0D;
    }

    public void setPathingSpeedCaps(double horizontalCap, double verticalCap) {
        rt().pathingHorizontalSpeedCap = Math.max(0.05D, horizontalCap);
        rt().pathingVerticalSpeedCap = Math.max(0.05D, verticalCap);
    }

    public void setPathingHoldAltitude(double y) {
        rt().pathingHoldY = y;
    }

    public void clearPathingHoldAltitude() {
        rt().pathingHoldY = Double.NaN;
    }

    private boolean shouldApplyAntiKickPulse() {
        return cfg().antiKickIntervalTicks > 0 && rt().tickCounter % cfg().antiKickIntervalTicks == 0;
    }

    private void stopFlight(EntityPlayerSP player, boolean stopMotion) {
        if (player != null) {
            player.fallDistance = 0.0F;
            if (stopMotion) {
                player.motionX = 0.0D;
                player.motionY = Math.min(0.0D, player.motionY);
                player.motionZ = 0.0D;
                player.velocityChanged = true;
            }

            if (!KillAuraHandler.enabled()) {
                KillAuraHandler.INSTANCE.applyMovementProtection(player, false, false, false);
            }
        }
    }

    private static void normalizeConfig() {
        if (!MODE_GLIDE.equalsIgnoreCase(cfg().flightMode) && !MODE_PULSE.equalsIgnoreCase(cfg().flightMode)) {
            cfg().flightMode = MODE_MOTION;
        } else if (MODE_GLIDE.equalsIgnoreCase(cfg().flightMode)) {
            cfg().flightMode = MODE_GLIDE;
        } else {
            cfg().flightMode = MODE_PULSE;
        }

        cfg().horizontalSpeed = MathHelper.clamp(cfg().horizontalSpeed, 0.05F, 10.00F);
        cfg().verticalSpeed = MathHelper.clamp(cfg().verticalSpeed, 0.05F, 10.00F);
        cfg().glideFallSpeed = MathHelper.clamp(cfg().glideFallSpeed, 0.00F, 0.50F);
        cfg().sprintMultiplier = MathHelper.clamp(cfg().sprintMultiplier, 1.00F, 10.00F);
        cfg().pulseBoost = MathHelper.clamp(cfg().pulseBoost, 0.05F, 10F);
        cfg().pulseIntervalTicks = MathHelper.clamp(cfg().pulseIntervalTicks, 1, 40);
        cfg().antiKickIntervalTicks = MathHelper.clamp(cfg().antiKickIntervalTicks, 4, 80);
        cfg().antiKickDistance = MathHelper.clamp(cfg().antiKickDistance, 0.01F, 0.20F);
    }
}
