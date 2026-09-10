package com.zszl.zszlScriptMod.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.config.FlightPathingConfig;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
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

    public static final FlyHandler INSTANCE = new FlyHandler();

    public static final String MODE_MOTION = "MOTION";
    public static final String MODE_GLIDE = "GLIDE";
    public static final String MODE_PULSE = "PULSE";

    public static boolean enabled = false;
    public static String flightMode = MODE_MOTION;

    public static boolean autoTakeoff = true;
    public static boolean stopMotionOnDisable = true;
    public static boolean enableNoCollision = true;
    public static boolean enableAntiKnockback = true;
    public static boolean enableAntiKick = false;

    public static float horizontalSpeed = 0.85F;
    public static float verticalSpeed = 0.42F;
    public static float glideFallSpeed = 0.04F;
    public static float sprintMultiplier = 1.25F;
    public static float pulseBoost = 0.28F;
    public static int pulseIntervalTicks = 4;
    public static int antiKickIntervalTicks = 16;
    public static float antiKickDistance = 0.04F;

    private int tickCounter = 0;
    private int pendingTakeoffTicks = 0;
    private volatile double pathingHorizontalSpeedCap = Double.POSITIVE_INFINITY;
    private volatile double pathingVerticalSpeedCap = Double.POSITIVE_INFINITY;
    private volatile double pathingHoldY = Double.NaN;

    private FlyHandler() {
    }

    static {
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("keycommand_fly.json").toFile();
    }

    public static void loadConfig() {
        enabled = false;
        flightMode = MODE_MOTION;
        autoTakeoff = true;
        stopMotionOnDisable = true;
        enableNoCollision = true;
        enableAntiKnockback = true;
        enableAntiKick = false;
        horizontalSpeed = 0.85F;
        verticalSpeed = 0.42F;
        glideFallSpeed = 0.04F;
        sprintMultiplier = 1.25F;
        pulseBoost = 0.28F;
        pulseIntervalTicks = 4;
        antiKickIntervalTicks = 16;
        antiKickDistance = 0.04F;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                return;
            }

            JsonObject json = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();
            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }
            if (json.has("flightMode")) {
                flightMode = json.get("flightMode").getAsString();
            }
            if (json.has("autoTakeoff")) {
                autoTakeoff = json.get("autoTakeoff").getAsBoolean();
            }
            if (json.has("stopMotionOnDisable")) {
                stopMotionOnDisable = json.get("stopMotionOnDisable").getAsBoolean();
            }
            if (json.has("enableNoCollision")) {
                enableNoCollision = json.get("enableNoCollision").getAsBoolean();
            }
            if (json.has("enableAntiKnockback")) {
                enableAntiKnockback = json.get("enableAntiKnockback").getAsBoolean();
            }
            if (json.has("enableAntiKick")) {
                enableAntiKick = json.get("enableAntiKick").getAsBoolean();
            }
            if (json.has("horizontalSpeed")) {
                horizontalSpeed = json.get("horizontalSpeed").getAsFloat();
            }
            if (json.has("verticalSpeed")) {
                verticalSpeed = json.get("verticalSpeed").getAsFloat();
            }
            if (json.has("glideFallSpeed")) {
                glideFallSpeed = json.get("glideFallSpeed").getAsFloat();
            }
            if (json.has("sprintMultiplier")) {
                sprintMultiplier = json.get("sprintMultiplier").getAsFloat();
            }
            if (json.has("pulseBoost")) {
                pulseBoost = json.get("pulseBoost").getAsFloat();
            }
            if (json.has("pulseIntervalTicks")) {
                pulseIntervalTicks = json.get("pulseIntervalTicks").getAsInt();
            }
            if (json.has("antiKickIntervalTicks")) {
                antiKickIntervalTicks = json.get("antiKickIntervalTicks").getAsInt();
            }
            if (json.has("antiKickDistance")) {
                antiKickDistance = json.get("antiKickDistance").getAsFloat();
            }

            normalizeConfig();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.fly.load_failed"), e);
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
            json.addProperty("flightMode", flightMode);
            json.addProperty("autoTakeoff", autoTakeoff);
            json.addProperty("stopMotionOnDisable", stopMotionOnDisable);
            json.addProperty("enableNoCollision", enableNoCollision);
            json.addProperty("enableAntiKnockback", enableAntiKnockback);
            json.addProperty("enableAntiKick", enableAntiKick);
            json.addProperty("horizontalSpeed", horizontalSpeed);
            json.addProperty("verticalSpeed", verticalSpeed);
            json.addProperty("glideFallSpeed", glideFallSpeed);
            json.addProperty("sprintMultiplier", sprintMultiplier);
            json.addProperty("pulseBoost", pulseBoost);
            json.addProperty("pulseIntervalTicks", pulseIntervalTicks);
            json.addProperty("antiKickIntervalTicks", antiKickIntervalTicks);
            json.addProperty("antiKickDistance", antiKickDistance);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.fly.save_failed"), e);
        }
    }

    public void toggleEnabled() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean targetEnabled) {
        Minecraft mc = Minecraft.getMinecraft();
        normalizeConfig();
        if (enabled == targetEnabled) {
            saveConfig();
            return;
        }

        applyEnabledState(mc, targetEnabled);

        saveConfig();

        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    I18n.format(enabled ? "msg.fly.enabled" : "msg.fly.disabled")));
        }
    }

    /**
     * Applies an enable-state change for an unsaved settings draft. Persistence
     * and user-facing chat messages remain owned by {@link #setEnabled}.
     */
    public void setEnabledForSettingsPreview(boolean targetEnabled) {
        Minecraft mc = Minecraft.getMinecraft();
        normalizeConfig();
        if (enabled != targetEnabled) {
            applyEnabledState(mc, targetEnabled);
        }
    }

    private void applyEnabledState(Minecraft mc, boolean targetEnabled) {
        enabled = targetEnabled;
        this.tickCounter = 0;
        this.pendingTakeoffTicks = enabled && autoTakeoff ? 6 : 0;

        if (!enabled) {
            clearPathingHoldAltitude();
            stopFlight(mc == null ? null : mc.player, stopMotionOnDisable);
        }
    }

    public void onClientDisconnect() {
        if (!FlightPathingConfig.keepFlyEnabledOnDisconnect) {
            enabled = false;
        }
        this.tickCounter = 0;
        this.pendingTakeoffTicks = 0;
        this.pathingHorizontalSpeedCap = Double.POSITIVE_INFINITY;
        this.pathingVerticalSpeedCap = Double.POSITIVE_INFINITY;
        clearPathingHoldAltitude();
        stopFlight(Minecraft.getMinecraft().player, true);
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
            return;
        }

        normalizeConfig();
        this.tickCounter++;
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
        if (Double.isFinite(this.pathingHoldY)) {
            motionX = 0.0D;
            motionZ = 0.0D;
            double yError = this.pathingHoldY - player.posY;
            motionY = Math.max(-verticalSpeed, Math.min(verticalSpeed, yError * 0.45D));
            if (Math.abs(yError) < 0.03D) {
                motionY = 0.0D;
            }
        }
        this.pathingHorizontalSpeedCap = Double.POSITIVE_INFINITY;
        this.pathingVerticalSpeedCap = Double.POSITIVE_INFINITY;

        if (enableAntiKick && !jump && !sneak && shouldApplyAntiKickPulse()) {
            motionY = Math.min(motionY, -antiKickDistance);
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

        double speed = horizontalSpeed;
        if (player.isSprinting()) {
            speed *= sprintMultiplier;
        }
        speed = Math.min(speed, this.pathingHorizontalSpeedCap);

        double yawRad = Math.toRadians(player.rotationYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double motionX = (-sin * forward + cos * strafe) * speed;
        double motionZ = (cos * forward + sin * strafe) * speed;
        return new double[] { motionX, motionZ };
    }

    private double computeVerticalMotion(EntityPlayerSP player, boolean jump, boolean sneak) {
        if (this.pendingTakeoffTicks > 0 && autoTakeoff && player.onGround && !jump && !sneak) {
            this.pendingTakeoffTicks--;
            return Math.max(0.24D, pulseBoost);
        }

        if (!player.onGround) {
            this.pendingTakeoffTicks = 0;
        }

        if (jump) {
            return Math.min(verticalSpeed, this.pathingVerticalSpeedCap);
        }
        if (sneak) {
            return -Math.min(verticalSpeed, this.pathingVerticalSpeedCap);
        }

        if (MODE_GLIDE.equalsIgnoreCase(flightMode)) {
            return -glideFallSpeed;
        }
        if (MODE_PULSE.equalsIgnoreCase(flightMode)) {
            return (this.tickCounter % pulseIntervalTicks == 0) ? pulseBoost : -glideFallSpeed;
        }
        return 0.0D;
    }

    public void setPathingSpeedCaps(double horizontalCap, double verticalCap) {
        this.pathingHorizontalSpeedCap = Math.max(0.05D, horizontalCap);
        this.pathingVerticalSpeedCap = Math.max(0.05D, verticalCap);
    }

    public void setPathingHoldAltitude(double y) {
        this.pathingHoldY = y;
    }

    public void clearPathingHoldAltitude() {
        this.pathingHoldY = Double.NaN;
    }

    private boolean shouldApplyAntiKickPulse() {
        return antiKickIntervalTicks > 0 && this.tickCounter % antiKickIntervalTicks == 0;
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

            if (!KillAuraHandler.enabled) {
                KillAuraHandler.INSTANCE.applyMovementProtection(player, false, false, false);
            }
        }
    }

    private static void normalizeConfig() {
        if (!MODE_GLIDE.equalsIgnoreCase(flightMode) && !MODE_PULSE.equalsIgnoreCase(flightMode)) {
            flightMode = MODE_MOTION;
        } else if (MODE_GLIDE.equalsIgnoreCase(flightMode)) {
            flightMode = MODE_GLIDE;
        } else {
            flightMode = MODE_PULSE;
        }

        horizontalSpeed = MathHelper.clamp(horizontalSpeed, 0.05F, 10.00F);
        verticalSpeed = MathHelper.clamp(verticalSpeed, 0.05F, 10.00F);
        glideFallSpeed = MathHelper.clamp(glideFallSpeed, 0.00F, 0.50F);
        sprintMultiplier = MathHelper.clamp(sprintMultiplier, 1.00F, 10.00F);
        pulseBoost = MathHelper.clamp(pulseBoost, 0.05F, 10F);
        pulseIntervalTicks = MathHelper.clamp(pulseIntervalTicks, 1, 40);
        antiKickIntervalTicks = MathHelper.clamp(antiKickIntervalTicks, 4, 80);
        antiKickDistance = MathHelper.clamp(antiKickDistance, 0.01F, 0.20F);
    }
}
