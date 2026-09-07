package com.zszl.zszlScriptMod.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.concurrent.ThreadLocalRandom;

public class AutoFishingHandler {

    public static final AutoFishingHandler INSTANCE = new AutoFishingHandler();

    public static final String BITE_MODE_SMART = "SMART";
    public static final String BITE_MODE_MOTION_ONLY = "MOTION_ONLY";
    public static final String BITE_MODE_STRICT = "STRICT";

    public static boolean enabled = false;

    // 基础控制
    public static boolean requireFishingRod = true;
    public static boolean autoSwitchToRod = false;
    public static int preferredRodSlot = 0; // 0 = 自动, 1-9 = 指定槽位
    public static boolean disableWhenGuiOpen = true;
    public static boolean allowWhilePlayerMoving = false;
    public static boolean sendStatusMessage = true;

    // 出杆设置
    public static boolean enableAutoCastOnStart = true;
    public static int initialCastDelayTicks = 8;
    public static boolean autoRecastAfterCatch = true;
    public static int recastDelayMinTicks = 10;
    public static int recastDelayMaxTicks = 16;
    public static boolean retryCastWhenBobberMissing = true;
    public static int retryCastDelayTicks = 20;
    public static boolean timeoutRecastEnabled = true;
    public static int maxFishingWaitTicks = 600;

    // 咬钩判定
    public static String biteDetectMode = BITE_MODE_SMART;
    public static int ignoreInitialBobberSettleTicks = 8;
    public static int reelDelayTicks = 2;
    public static float minVerticalDropThreshold = 0.08F;
    public static float minHorizontalMoveThreshold = 0.03F;
    public static int confirmBiteTicks = 1;
    public static boolean debugBiteInfo = false;

    // 收杆 / 补杆
    public static int postReelPauseTicks = 6;
    public static int preventDoubleReelTicks = 6;
    public static boolean recastOnlyIfLootSuccess = false;
    public static boolean resetStateWhenHookGone = true;
    public static boolean autoRecoverFromInterruptedCast = true;

    // 安全限制
    public static boolean stopWhenRodDurabilityLow = true;
    public static int minRodDurability = 5;
    public static boolean stopWhenNoRodFound = true;
    public static boolean pauseWhenHookedEntity = true;
    public static boolean stopOnWorldChange = true;

    private enum FishingState {
        IDLE,
        WAITING_CAST,
        WAITING_BOBBER,
        FISHING,
        WAITING_REEL,
        POST_REEL_DELAY
    }

    private FishingState state = FishingState.IDLE;
    private int actionDelayTicks = 0;
    private int reelCooldownTicks = 0;
    private int fishingTicks = 0;
    private int biteConfirmCounter = 0;
    private int bobberWaterStableTicks = 0;
    private boolean lastReelLikelyCatch = false;
    private boolean lastBobberInWater = false;
    private double lastBobberX = 0.0D;
    private double lastBobberY = 0.0D;
    private double lastBobberZ = 0.0D;

    private AutoFishingHandler() {
    }

    static {
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("keycommand_auto_fishing.json").toFile();
    }

    public static void loadConfig() {
        applyDefaultSettings();

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                return;
            }

            JsonObject json = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();

            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }

            if (json.has("requireFishingRod")) {
                requireFishingRod = json.get("requireFishingRod").getAsBoolean();
            }
            if (json.has("autoSwitchToRod")) {
                autoSwitchToRod = json.get("autoSwitchToRod").getAsBoolean();
            }
            if (json.has("preferredRodSlot")) {
                preferredRodSlot = json.get("preferredRodSlot").getAsInt();
            }
            if (json.has("disableWhenGuiOpen")) {
                disableWhenGuiOpen = json.get("disableWhenGuiOpen").getAsBoolean();
            }
            if (json.has("allowWhilePlayerMoving")) {
                allowWhilePlayerMoving = json.get("allowWhilePlayerMoving").getAsBoolean();
            }
            if (json.has("sendStatusMessage")) {
                sendStatusMessage = json.get("sendStatusMessage").getAsBoolean();
            }

            if (json.has("enableAutoCastOnStart")) {
                enableAutoCastOnStart = json.get("enableAutoCastOnStart").getAsBoolean();
            }
            if (json.has("initialCastDelayTicks")) {
                initialCastDelayTicks = json.get("initialCastDelayTicks").getAsInt();
            }
            if (json.has("autoRecastAfterCatch")) {
                autoRecastAfterCatch = json.get("autoRecastAfterCatch").getAsBoolean();
            }
            if (json.has("recastDelayMinTicks")) {
                recastDelayMinTicks = json.get("recastDelayMinTicks").getAsInt();
            }
            if (json.has("recastDelayMaxTicks")) {
                recastDelayMaxTicks = json.get("recastDelayMaxTicks").getAsInt();
            }
            if (json.has("retryCastWhenBobberMissing")) {
                retryCastWhenBobberMissing = json.get("retryCastWhenBobberMissing").getAsBoolean();
            }
            if (json.has("retryCastDelayTicks")) {
                retryCastDelayTicks = json.get("retryCastDelayTicks").getAsInt();
            }
            if (json.has("timeoutRecastEnabled")) {
                timeoutRecastEnabled = json.get("timeoutRecastEnabled").getAsBoolean();
            }
            if (json.has("maxFishingWaitTicks")) {
                maxFishingWaitTicks = json.get("maxFishingWaitTicks").getAsInt();
            }

            if (json.has("biteDetectMode")) {
                biteDetectMode = json.get("biteDetectMode").getAsString();
            }
            if (json.has("ignoreInitialBobberSettleTicks")) {
                ignoreInitialBobberSettleTicks = json.get("ignoreInitialBobberSettleTicks").getAsInt();
            }
            if (json.has("reelDelayTicks")) {
                reelDelayTicks = json.get("reelDelayTicks").getAsInt();
            }
            if (json.has("minVerticalDropThreshold")) {
                minVerticalDropThreshold = json.get("minVerticalDropThreshold").getAsFloat();
            }
            if (json.has("minHorizontalMoveThreshold")) {
                minHorizontalMoveThreshold = json.get("minHorizontalMoveThreshold").getAsFloat();
            }
            if (json.has("confirmBiteTicks")) {
                confirmBiteTicks = json.get("confirmBiteTicks").getAsInt();
            }
            if (json.has("debugBiteInfo")) {
                debugBiteInfo = json.get("debugBiteInfo").getAsBoolean();
            }

            if (json.has("postReelPauseTicks")) {
                postReelPauseTicks = json.get("postReelPauseTicks").getAsInt();
            }
            if (json.has("preventDoubleReelTicks")) {
                preventDoubleReelTicks = json.get("preventDoubleReelTicks").getAsInt();
            }
            if (json.has("recastOnlyIfLootSuccess")) {
                recastOnlyIfLootSuccess = json.get("recastOnlyIfLootSuccess").getAsBoolean();
            }
            if (json.has("resetStateWhenHookGone")) {
                resetStateWhenHookGone = json.get("resetStateWhenHookGone").getAsBoolean();
            }
            if (json.has("autoRecoverFromInterruptedCast")) {
                autoRecoverFromInterruptedCast = json.get("autoRecoverFromInterruptedCast").getAsBoolean();
            }

            if (json.has("stopWhenRodDurabilityLow")) {
                stopWhenRodDurabilityLow = json.get("stopWhenRodDurabilityLow").getAsBoolean();
            }
            if (json.has("minRodDurability")) {
                minRodDurability = json.get("minRodDurability").getAsInt();
            }
            if (json.has("stopWhenNoRodFound")) {
                stopWhenNoRodFound = json.get("stopWhenNoRodFound").getAsBoolean();
            }
            if (json.has("pauseWhenHookedEntity")) {
                pauseWhenHookedEntity = json.get("pauseWhenHookedEntity").getAsBoolean();
            }
            if (json.has("stopOnWorldChange")) {
                stopOnWorldChange = json.get("stopOnWorldChange").getAsBoolean();
            }

            normalizeConfig();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载自动钓鱼配置失败", e);
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

            json.addProperty("requireFishingRod", requireFishingRod);
            json.addProperty("autoSwitchToRod", autoSwitchToRod);
            json.addProperty("preferredRodSlot", preferredRodSlot);
            json.addProperty("disableWhenGuiOpen", disableWhenGuiOpen);
            json.addProperty("allowWhilePlayerMoving", allowWhilePlayerMoving);
            json.addProperty("sendStatusMessage", sendStatusMessage);

            json.addProperty("enableAutoCastOnStart", enableAutoCastOnStart);
            json.addProperty("initialCastDelayTicks", initialCastDelayTicks);
            json.addProperty("autoRecastAfterCatch", autoRecastAfterCatch);
            json.addProperty("recastDelayMinTicks", recastDelayMinTicks);
            json.addProperty("recastDelayMaxTicks", recastDelayMaxTicks);
            json.addProperty("retryCastWhenBobberMissing", retryCastWhenBobberMissing);
            json.addProperty("retryCastDelayTicks", retryCastDelayTicks);
            json.addProperty("timeoutRecastEnabled", timeoutRecastEnabled);
            json.addProperty("maxFishingWaitTicks", maxFishingWaitTicks);

            json.addProperty("biteDetectMode", biteDetectMode);
            json.addProperty("ignoreInitialBobberSettleTicks", ignoreInitialBobberSettleTicks);
            json.addProperty("reelDelayTicks", reelDelayTicks);
            json.addProperty("minVerticalDropThreshold", minVerticalDropThreshold);
            json.addProperty("minHorizontalMoveThreshold", minHorizontalMoveThreshold);
            json.addProperty("confirmBiteTicks", confirmBiteTicks);
            json.addProperty("debugBiteInfo", debugBiteInfo);

            json.addProperty("postReelPauseTicks", postReelPauseTicks);
            json.addProperty("preventDoubleReelTicks", preventDoubleReelTicks);
            json.addProperty("recastOnlyIfLootSuccess", recastOnlyIfLootSuccess);
            json.addProperty("resetStateWhenHookGone", resetStateWhenHookGone);
            json.addProperty("autoRecoverFromInterruptedCast", autoRecoverFromInterruptedCast);

            json.addProperty("stopWhenRodDurabilityLow", stopWhenRodDurabilityLow);
            json.addProperty("minRodDurability", minRodDurability);
            json.addProperty("stopWhenNoRodFound", stopWhenNoRodFound);
            json.addProperty("pauseWhenHookedEntity", pauseWhenHookedEntity);
            json.addProperty("stopOnWorldChange", stopOnWorldChange);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存自动钓鱼配置失败", e);
        }
    }

    private static void applyDefaultSettings() {
        enabled = false;

        requireFishingRod = true;
        autoSwitchToRod = false;
        preferredRodSlot = 0;
        disableWhenGuiOpen = true;
        allowWhilePlayerMoving = false;
        sendStatusMessage = true;

        enableAutoCastOnStart = true;
        initialCastDelayTicks = 8;
        autoRecastAfterCatch = true;
        recastDelayMinTicks = 10;
        recastDelayMaxTicks = 16;
        retryCastWhenBobberMissing = true;
        retryCastDelayTicks = 20;
        timeoutRecastEnabled = true;
        maxFishingWaitTicks = 600;

        biteDetectMode = BITE_MODE_SMART;
        ignoreInitialBobberSettleTicks = 8;
        reelDelayTicks = 2;
        minVerticalDropThreshold = 0.08F;
        minHorizontalMoveThreshold = 0.03F;
        confirmBiteTicks = 1;
        debugBiteInfo = false;

        postReelPauseTicks = 6;
        preventDoubleReelTicks = 6;
        recastOnlyIfLootSuccess = false;
        resetStateWhenHookGone = true;
        autoRecoverFromInterruptedCast = true;

        stopWhenRodDurabilityLow = true;
        minRodDurability = 5;
        stopWhenNoRodFound = true;
        pauseWhenHookedEntity = true;
        stopOnWorldChange = true;
    }

    private static void normalizeConfig() {
        preferredRodSlot = MathHelper.clamp(preferredRodSlot, 0, 9);

        initialCastDelayTicks = MathHelper.clamp(initialCastDelayTicks, 0, 100);
        recastDelayMinTicks = MathHelper.clamp(recastDelayMinTicks, 0, 100);
        recastDelayMaxTicks = MathHelper.clamp(recastDelayMaxTicks, recastDelayMinTicks, 100);
        retryCastDelayTicks = MathHelper.clamp(retryCastDelayTicks, 5, 100);
        maxFishingWaitTicks = MathHelper.clamp(maxFishingWaitTicks, 40, 2400);

        ignoreInitialBobberSettleTicks = MathHelper.clamp(ignoreInitialBobberSettleTicks, 0, 40);
        reelDelayTicks = MathHelper.clamp(reelDelayTicks, 0, 20);
        minVerticalDropThreshold = MathHelper.clamp(minVerticalDropThreshold, 0.01F, 1.0F);
        minHorizontalMoveThreshold = MathHelper.clamp(minHorizontalMoveThreshold, 0.0F, 1.0F);
        confirmBiteTicks = MathHelper.clamp(confirmBiteTicks, 1, 5);

        postReelPauseTicks = MathHelper.clamp(postReelPauseTicks, 0, 40);
        preventDoubleReelTicks = MathHelper.clamp(preventDoubleReelTicks, 0, 20);

        minRodDurability = MathHelper.clamp(minRodDurability, 1, 64);

        if (!BITE_MODE_MOTION_ONLY.equalsIgnoreCase(biteDetectMode)
                && !BITE_MODE_STRICT.equalsIgnoreCase(biteDetectMode)) {
            biteDetectMode = BITE_MODE_SMART;
        } else if (BITE_MODE_MOTION_ONLY.equalsIgnoreCase(biteDetectMode)) {
            biteDetectMode = BITE_MODE_MOTION_ONLY;
        } else {
            biteDetectMode = BITE_MODE_STRICT;
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

        applyEnabledState(targetEnabled);

        saveConfig();

        if (mc.player != null && sendStatusMessage) {
            mc.player.sendMessage(new TextComponentString(I18n.format(
                    enabled ? "msg.auto_fishing.enabled" : "msg.auto_fishing.disabled")));
        }
    }

    /**
     * Applies the runtime part of an enable-state change without persistence
     * or chat output. Modern configuration tabs use this while editing a
     * draft, then save the resulting state explicitly.
     */
    public void setEnabledForSettingsPreview(boolean targetEnabled) {
        normalizeConfig();
        if (enabled != targetEnabled) {
            applyEnabledState(targetEnabled);
        }
    }

    private void applyEnabledState(boolean targetEnabled) {
        enabled = targetEnabled;
        resetRuntimeState();

        if (enabled && enableAutoCastOnStart) {
            this.state = FishingState.WAITING_CAST;
            this.actionDelayTicks = initialCastDelayTicks;
        }
    }

    public void onClientDisconnect() {
        if (stopOnWorldChange && enabled) {
            enabled = false;
            saveConfig();
        }
        resetRuntimeState();
    }

    public void resetRuntimeState() {
        this.state = FishingState.IDLE;
        this.actionDelayTicks = 0;
        this.reelCooldownTicks = 0;
        this.fishingTicks = 0;
        this.biteConfirmCounter = 0;
        this.bobberWaterStableTicks = 0;
        this.lastReelLikelyCatch = false;
        this.lastBobberInWater = false;
        this.lastBobberX = 0.0D;
        this.lastBobberY = 0.0D;
        this.lastBobberZ = 0.0D;
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

        if (this.actionDelayTicks > 0) {
            this.actionDelayTicks--;
        }
        if (this.reelCooldownTicks > 0) {
            this.reelCooldownTicks--;
        }

        if (!enabled) {
            if (this.state != FishingState.IDLE) {
                resetRuntimeState();
            }
            return;
        }

        if (player.isDead || player.getHealth() <= 0.0F || player.isSpectator()) {
            return;
        }

        if (shouldPauseForCurrentScreen(mc)) {
            return;
        }

        if (!allowWhilePlayerMoving && isPlayerActivelyMoving(player)) {
            return;
        }

        if (!ensureRodReady(player)) {
            return;
        }

        EntityFishHook bobber = player.fishEntity;

        if (bobber != null && this.state != FishingState.WAITING_REEL && this.state != FishingState.POST_REEL_DELAY) {
            if (this.state != FishingState.FISHING) {
                beginFishing(bobber);
            }
        }

        switch (this.state) {
            case IDLE:
                if (bobber != null) {
                    beginFishing(bobber);
                }
                break;

            case WAITING_CAST:
                if (bobber != null) {
                    beginFishing(bobber);
                    return;
                }
                if (this.actionDelayTicks <= 0 && performRodUse(player)) {
                    this.state = FishingState.WAITING_BOBBER;
                    this.actionDelayTicks = retryCastDelayTicks;
                }
                break;

            case WAITING_BOBBER:
                if (bobber != null) {
                    beginFishing(bobber);
                    return;
                }
                if (retryCastWhenBobberMissing && this.actionDelayTicks <= 0) {
                    if (performRodUse(player)) {
                        this.actionDelayTicks = retryCastDelayTicks;
                    }
                } else if (!retryCastWhenBobberMissing && this.actionDelayTicks <= 0) {
                    this.state = FishingState.IDLE;
                }
                break;

            case FISHING:
                if (bobber == null) {
                    handleUnexpectedHookLoss();
                    return;
                }

                if (pauseWhenHookedEntity && bobber.caughtEntity != null) {
                    return;
                }

                this.fishingTicks++;

                if (shouldTriggerBite(bobber)) {
                    this.biteConfirmCounter++;
                    if (this.biteConfirmCounter >= confirmBiteTicks && this.reelCooldownTicks <= 0) {
                        this.state = FishingState.WAITING_REEL;
                        this.actionDelayTicks = reelDelayTicks;
                        this.lastReelLikelyCatch = true;
                        if (debugBiteInfo && player != null) {
                            player.sendMessage(new TextComponentString(I18n.format("msg.auto_fishing.debug_bite")));
                        }
                    }
                } else {
                    this.biteConfirmCounter = 0;
                }

                if (timeoutRecastEnabled && this.fishingTicks >= maxFishingWaitTicks) {
                    if (performRodUse(player)) {
                        this.state = FishingState.POST_REEL_DELAY;
                        this.actionDelayTicks = postReelPauseTicks;
                        this.reelCooldownTicks = preventDoubleReelTicks;
                        this.lastReelLikelyCatch = false;
                    }
                }
                break;

            case WAITING_REEL:
                if (bobber == null) {
                    this.state = FishingState.POST_REEL_DELAY;
                    this.actionDelayTicks = Math.max(this.actionDelayTicks, postReelPauseTicks);
                    return;
                }

                if (this.actionDelayTicks <= 0 && this.reelCooldownTicks <= 0) {
                    if (performRodUse(player)) {
                        this.state = FishingState.POST_REEL_DELAY;
                        this.actionDelayTicks = postReelPauseTicks;
                        this.reelCooldownTicks = preventDoubleReelTicks;
                    }
                }
                break;

            case POST_REEL_DELAY:
                if (bobber != null) {
                    if (autoRecoverFromInterruptedCast && this.actionDelayTicks <= 0) {
                        beginFishing(bobber);
                    }
                    return;
                }

                if (this.actionDelayTicks <= 0) {
                    if (autoRecastAfterCatch && (!recastOnlyIfLootSuccess || this.lastReelLikelyCatch)) {
                        scheduleRandomRecast();
                    } else {
                        this.state = FishingState.IDLE;
                    }
                }
                break;

            default:
                break;
        }
    }

    private void beginFishing(EntityFishHook bobber) {
        this.state = FishingState.FISHING;
        this.fishingTicks = 0;
        this.biteConfirmCounter = 0;
        this.lastBobberInWater = bobber != null && bobber.isInWater();
        this.bobberWaterStableTicks = this.lastBobberInWater ? 1 : 0;
        syncBobberPosition(bobber);
    }

    private void handleUnexpectedHookLoss() {
        if (!resetStateWhenHookGone) {
            this.state = FishingState.IDLE;
            return;
        }

        if (autoRecoverFromInterruptedCast && autoRecastAfterCatch) {
            scheduleRandomRecast();
        } else {
            this.state = FishingState.IDLE;
        }
    }

    private void scheduleRandomRecast() {
        this.state = FishingState.WAITING_CAST;
        this.actionDelayTicks = ThreadLocalRandom.current()
                .nextInt(recastDelayMinTicks, recastDelayMaxTicks + 1);
        this.biteConfirmCounter = 0;
        this.fishingTicks = 0;
    }

    private boolean shouldTriggerBite(EntityFishHook bobber) {
        if (bobber == null) {
            return false;
        }

        double horizontalMove = Math.sqrt(
                Math.pow(bobber.posX - this.lastBobberX, 2)
                        + Math.pow(bobber.posZ - this.lastBobberZ, 2));
        double verticalDrop = this.lastBobberY - bobber.posY;

        boolean downward = verticalDrop >= minVerticalDropThreshold
                || bobber.motionY <= -minVerticalDropThreshold;
        boolean horizontal = horizontalMove >= minHorizontalMoveThreshold;
        boolean inWater = bobber.isInWater();

        if (inWater) {
            this.bobberWaterStableTicks = this.lastBobberInWater ? this.bobberWaterStableTicks + 1 : 1;
        } else {
            this.bobberWaterStableTicks = 0;
        }
        this.lastBobberInWater = inWater;

        int requiredWaterStableTicks = Math.max(8, ignoreInitialBobberSettleTicks);
        if (!inWater || this.bobberWaterStableTicks <= requiredWaterStableTicks) {
            syncBobberPosition(bobber);
            return false;
        }

        boolean result;
        if (BITE_MODE_MOTION_ONLY.equalsIgnoreCase(biteDetectMode)) {
            result = downward || horizontal;
        } else if (BITE_MODE_STRICT.equalsIgnoreCase(biteDetectMode)) {
            result = downward && horizontal;
        } else {
            result = downward || (horizontal && bobber.motionY < -0.02D);
        }

        syncBobberPosition(bobber);
        return result;
    }

    private void syncBobberPosition(EntityFishHook bobber) {
        if (bobber == null) {
            return;
        }
        this.lastBobberX = bobber.posX;
        this.lastBobberY = bobber.posY;
        this.lastBobberZ = bobber.posZ;
    }

    private boolean ensureRodReady(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }

        ItemStack held = player.getHeldItemMainhand();
        if (isFishingRod(held)) {
            return validateRodDurability(held);
        }

        boolean shouldTrySwitch = autoSwitchToRod || !requireFishingRod;
        if (!shouldTrySwitch) {
            return false;
        }

        int rodSlot = findRodHotbarSlot(player);
        if (rodSlot < 0) {
            if (stopWhenNoRodFound) {
                disableWithMessage("msg.auto_fishing.stop_no_rod");
            }
            return false;
        }

        player.inventory.currentItem = rodSlot;
        ItemStack switched = player.getHeldItemMainhand();
        return isFishingRod(switched) && validateRodDurability(switched);
    }

    private boolean validateRodDurability(ItemStack stack) {
        if (!isFishingRod(stack)) {
            return false;
        }
        if (!stopWhenRodDurabilityLow || !stack.isItemStackDamageable()) {
            return true;
        }

        int remain = stack.getMaxDamage() - stack.getItemDamage();
        if (remain <= minRodDurability) {
            disableWithMessage("msg.auto_fishing.stop_low_durability");
            return false;
        }
        return true;
    }

    private int findRodHotbarSlot(EntityPlayerSP player) {
        if (player == null) {
            return -1;
        }

        int preferredIndex = preferredRodSlot - 1;
        if (preferredIndex >= 0 && preferredIndex < 9) {
            ItemStack preferred = player.inventory.getStackInSlot(preferredIndex);
            if (isFishingRod(preferred)) {
                return preferredIndex;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (isFishingRod(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFishingRod(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemFishingRod;
    }

    private boolean performRodUse(EntityPlayerSP player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player == null || mc.playerController == null || mc.world == null) {
            return false;
        }

        ItemStack held = player.getHeldItemMainhand();
        if (!isFishingRod(held)) {
            return false;
        }

        mc.playerController.processRightClick(player, mc.world, EnumHand.MAIN_HAND);
        player.swingArm(EnumHand.MAIN_HAND);
        return true;
    }

    private boolean shouldPauseForCurrentScreen(Minecraft mc) {
        if (!disableWhenGuiOpen || mc == null || mc.currentScreen == null) {
            return false;
        }

        String screenClassName = mc.currentScreen.getClass().getName();
        return screenClassName.startsWith("com.zszl.zszlScriptMod.gui");
    }

    private boolean isPlayerActivelyMoving(EntityPlayerSP player) {
        return player != null
                && player.movementInput != null
                && (Math.abs(player.movementInput.moveForward) > 0.01F
                || Math.abs(player.movementInput.moveStrafe) > 0.01F
                || player.movementInput.jump
                || player.movementInput.sneak);
    }

    private void disableWithMessage(String key) {
        Minecraft mc = Minecraft.getMinecraft();
        enabled = false;
        saveConfig();
        resetRuntimeState();

        if (mc.player != null && sendStatusMessage) {
            mc.player.sendMessage(new TextComponentString(I18n.format(key)));
        }
    }
}
