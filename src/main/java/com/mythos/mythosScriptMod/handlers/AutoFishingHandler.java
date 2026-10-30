package com.mythos.mythosScriptMod.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.util.EnumParticleTypes;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class AutoFishingHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        // 基础控制
        public boolean requireFishingRod = true;
        public boolean autoSwitchToRod = false;
        public int preferredRodSlot = 0; // 0 = 自动, 1-9 = 指定槽位
        public boolean disableWhenGuiOpen = true;
        public boolean allowWhilePlayerMoving = false;
        public boolean sendStatusMessage = true;
        // 出杆设置
        public boolean enableAutoCastOnStart = true;
        public int initialCastDelayTicks = 8;
        public boolean autoRecastAfterCatch = true;
        public int recastDelayMinTicks = 10;
        public int recastDelayMaxTicks = 16;
        public boolean retryCastWhenBobberMissing = true;
        public int retryCastDelayTicks = 20;
        public boolean timeoutRecastEnabled = true;
        public int maxFishingWaitTicks = 1200;
        // 咬钩判定
        public String biteDetectMode = BITE_MODE_SERVER_SIGNAL;
        public int ignoreInitialBobberSettleTicks = 8;
        public int reelDelayTicks = 0;
        public float minVerticalDropThreshold = 0.04F;
        public float minHorizontalMoveThreshold = 0.10F;
        public int confirmBiteTicks = 1;
        public boolean debugBiteInfo = false;
        // 收杆 / 补杆
        public int postReelPauseTicks = 6;
        public int preventDoubleReelTicks = 6;
        public boolean recastOnlyIfLootSuccess = false;
        public boolean resetStateWhenHookGone = true;
        public boolean autoRecoverFromInterruptedCast = true;
        // 安全限制
        public boolean stopWhenRodDurabilityLow = true;
        public int minRodDurability = 5;
        public boolean stopWhenNoRodFound = true;
        public boolean pauseWhenHookedEntity = true;
        public boolean stopOnWorldChange = true;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) AutoFishingHandler::loadConfig);
            return config;
        }
    }


    public static final AutoFishingHandler INSTANCE = new AutoFishingHandler();

    public static final String BITE_MODE_SERVER_SIGNAL = "SERVER_SIGNAL";
    // Legacy fields remain readable for existing profiles/actions, but client
    // motion is no longer used as bite evidence.
    public static final String BITE_MODE_SMART = "SMART";
    public static final String BITE_MODE_MOTION_ONLY = "MOTION_ONLY";
    public static final String BITE_MODE_STRICT = "STRICT";






    private enum FishingState {
        IDLE,
        WAITING_CAST,
        WAITING_BOBBER,
        FISHING,
        WAITING_REEL,
        POST_REEL_DELAY
    }

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static final class RuntimeState {
        private final ConcurrentLinkedQueue<AutoFishingBiteSignal> biteSignals = new ConcurrentLinkedQueue<>();
        private Channel observedChannel;
        private volatile int fishingHookId = -1;
        private long fishingStartedMillis;
        private long lastFishingUpdateMillis;
        private boolean enabled = false;
        private FishingState state = FishingState.IDLE;
        private int actionDelayTicks = 0;
        private int reelCooldownTicks = 0;
        /** Wall-clock deadlines — throttled bot sessions tick slower, so a
         *  tick-count delay would stretch by 1/rate. 0 = inactive. */
        private long actionDelayUntilMillis = 0L;
        private long reelCooldownUntilMillis = 0L;
        private int fishingTicks = 0;
        private int biteConfirmCounter = 0;
        private int bobberWaterStableTicks = 0;
        private boolean lastReelLikelyCatch = false;
        private int reelTargetEntityId = -1;
        private boolean lastBobberInWater = false;
        private double lastBobberX = 0.0D;
        private double lastBobberY = 0.0D;
        private double lastBobberZ = 0.0D;
    }

    private static void observeBites(EntityPlayerSP player, RuntimeState rt) {
        if (player.connection == null) return;
        NetworkManager connection = player.connection.getNetworkManager();
        Channel channel = connection.channel();
        if (channel == null || channel == rt.observedChannel) return;
        rt.biteSignals.clear();
        rt.observedChannel = channel;
        // Before packet_handler: Nexus deliberately consumes background sound
        // packets there. Reading the data must NOT invoke vanilla audio code.
        channel.eventLoop().execute(() -> {
            String name = "mythos_auto_fishing_bites";
            if (!channel.isOpen() || channel.pipeline().get(name) != null) return;
            channel.pipeline().addBefore("packet_handler", name, new ChannelInboundHandlerAdapter() {
                private AutoFishingBiteSignal pendingBubbleBurst;

                private void offerBite(AutoFishingBiteSignal signal) {
                    while (rt.biteSignals.size() >= 8) rt.biteSignals.poll();
                    rt.biteSignals.offer(signal);
                }

                @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    if (message instanceof SPacketSoundEffect) {
                        SPacketSoundEffect sound = (SPacketSoundEffect) message;
                        if (sound.getSound() == SoundEvents.ENTITY_BOBBER_SPLASH) {
                            offerBite(new AutoFishingBiteSignal(rt.fishingHookId, System.currentTimeMillis(),
                                    sound.getX(), sound.getY(), sound.getZ()));
                        }
                    }
                    if (message instanceof SPacketParticles) {
                        SPacketParticles packet = (SPacketParticles) message;
                        // Sound reaches only 16 blocks; these server bite bursts
                        // reach 32. Observe data before Nexus drops visual packets;
                        // never dispatch particle rendering from this IO callback.
                        if (AutoFishingBiteSignal.isBiteBurst(packet.getParticleCount(),
                                packet.getXOffset(), packet.getYOffset(), packet.getZOffset(),
                                packet.getParticleSpeed())) {
                            AutoFishingBiteSignal signal = new AutoFishingBiteSignal(
                                    rt.fishingHookId, System.currentTimeMillis(),
                                    packet.getXCoordinate(), packet.getYCoordinate(), packet.getZCoordinate());
                            if (packet.getParticleType() == EnumParticleTypes.WATER_BUBBLE) {
                                pendingBubbleBurst = signal;
                            } else if (packet.getParticleType() == EnumParticleTypes.WATER_WAKE) {
                                if (pendingBubbleBurst != null && pendingBubbleBurst.pairsWith(signal)) {
                                    offerBite(pendingBubbleBurst);
                                }
                                pendingBubbleBurst = null;
                            }
                        }
                    }
                    super.channelRead(context, message);
                }
            });
        });
    }

    /** Called by Nexus's unthrottled owner-thread hook as well as Forge END. */
    public void tickFishingFast() {
        onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
    }

    private AutoFishingHandler() {
    }

    static {
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("keycommand_auto_fishing.json").toFile();
    }

    /** Runtime-state file: per-owner (bot) under
     *  profiles/<profile>/_runtime/<owner>/. Shared config stays in the
     *  profile dir; 'enabled' is live state and must not leak across bots. */
    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("keycommand_auto_fishing.json").toFile();
    }

    public static void loadConfig() {
        applyDefaultSettings();

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                return;
            }

            JsonObject json;

            try (FileReader reader = new FileReader(configFile)) {

                json = new JsonParser().parse(reader).getAsJsonObject();

            }

            // 'enabled' comes from the per-owner runtime file; fall back to the
            // shared file only when the runtime file doesn't exist yet.
            File runtimeFile = getRuntimeConfigFile();
            if (runtimeFile.exists()) {
                try (FileReader rr = new FileReader(runtimeFile)) {
                    JsonObject rj = new JsonParser().parse(rr).getAsJsonObject();
                    if (rj.has("enabled")) {
                        rt().enabled = rj.get("enabled").getAsBoolean();
                    }
                }
            } else if (json.has("enabled")) {
                rt().enabled = json.get("enabled").getAsBoolean();
            }

            if (json.has("requireFishingRod")) {
                cfg().requireFishingRod = json.get("requireFishingRod").getAsBoolean();
            }
            if (json.has("autoSwitchToRod")) {
                cfg().autoSwitchToRod = json.get("autoSwitchToRod").getAsBoolean();
            }
            if (json.has("preferredRodSlot")) {
                cfg().preferredRodSlot = json.get("preferredRodSlot").getAsInt();
            }
            if (json.has("disableWhenGuiOpen")) {
                cfg().disableWhenGuiOpen = json.get("disableWhenGuiOpen").getAsBoolean();
            }
            if (json.has("allowWhilePlayerMoving")) {
                cfg().allowWhilePlayerMoving = json.get("allowWhilePlayerMoving").getAsBoolean();
            }
            if (json.has("sendStatusMessage")) {
                cfg().sendStatusMessage = json.get("sendStatusMessage").getAsBoolean();
            }

            if (json.has("enableAutoCastOnStart")) {
                cfg().enableAutoCastOnStart = json.get("enableAutoCastOnStart").getAsBoolean();
            }
            if (json.has("initialCastDelayTicks")) {
                cfg().initialCastDelayTicks = json.get("initialCastDelayTicks").getAsInt();
            }
            if (json.has("autoRecastAfterCatch")) {
                cfg().autoRecastAfterCatch = json.get("autoRecastAfterCatch").getAsBoolean();
            }
            if (json.has("recastDelayMinTicks")) {
                cfg().recastDelayMinTicks = json.get("recastDelayMinTicks").getAsInt();
            }
            if (json.has("recastDelayMaxTicks")) {
                cfg().recastDelayMaxTicks = json.get("recastDelayMaxTicks").getAsInt();
            }
            if (json.has("retryCastWhenBobberMissing")) {
                cfg().retryCastWhenBobberMissing = json.get("retryCastWhenBobberMissing").getAsBoolean();
            }
            if (json.has("retryCastDelayTicks")) {
                cfg().retryCastDelayTicks = json.get("retryCastDelayTicks").getAsInt();
            }
            if (json.has("timeoutRecastEnabled")) {
                cfg().timeoutRecastEnabled = json.get("timeoutRecastEnabled").getAsBoolean();
            }
            if (json.has("maxFishingWaitTicks")) {
                cfg().maxFishingWaitTicks = json.get("maxFishingWaitTicks").getAsInt();
            }

            if (json.has("biteDetectMode")) {
                cfg().biteDetectMode = json.get("biteDetectMode").getAsString();
            }
            if (json.has("ignoreInitialBobberSettleTicks")) {
                cfg().ignoreInitialBobberSettleTicks = json.get("ignoreInitialBobberSettleTicks").getAsInt();
            }
            if (json.has("reelDelayTicks")) {
                cfg().reelDelayTicks = json.get("reelDelayTicks").getAsInt();
            }
            if (json.has("minVerticalDropThreshold")) {
                cfg().minVerticalDropThreshold = json.get("minVerticalDropThreshold").getAsFloat();
            }
            if (json.has("minHorizontalMoveThreshold")) {
                cfg().minHorizontalMoveThreshold = json.get("minHorizontalMoveThreshold").getAsFloat();
            }
            if (json.has("confirmBiteTicks")) {
                cfg().confirmBiteTicks = json.get("confirmBiteTicks").getAsInt();
            }
            if (json.has("debugBiteInfo")) {
                cfg().debugBiteInfo = json.get("debugBiteInfo").getAsBoolean();
            }

            if (json.has("postReelPauseTicks")) {
                cfg().postReelPauseTicks = json.get("postReelPauseTicks").getAsInt();
            }
            if (json.has("preventDoubleReelTicks")) {
                cfg().preventDoubleReelTicks = json.get("preventDoubleReelTicks").getAsInt();
            }
            if (json.has("recastOnlyIfLootSuccess")) {
                cfg().recastOnlyIfLootSuccess = json.get("recastOnlyIfLootSuccess").getAsBoolean();
            }
            if (json.has("resetStateWhenHookGone")) {
                cfg().resetStateWhenHookGone = json.get("resetStateWhenHookGone").getAsBoolean();
            }
            if (json.has("autoRecoverFromInterruptedCast")) {
                cfg().autoRecoverFromInterruptedCast = json.get("autoRecoverFromInterruptedCast").getAsBoolean();
            }

            if (json.has("stopWhenRodDurabilityLow")) {
                cfg().stopWhenRodDurabilityLow = json.get("stopWhenRodDurabilityLow").getAsBoolean();
            }
            if (json.has("minRodDurability")) {
                cfg().minRodDurability = json.get("minRodDurability").getAsInt();
            }
            if (json.has("stopWhenNoRodFound")) {
                cfg().stopWhenNoRodFound = json.get("stopWhenNoRodFound").getAsBoolean();
            }
            if (json.has("pauseWhenHookedEntity")) {
                cfg().pauseWhenHookedEntity = json.get("pauseWhenHookedEntity").getAsBoolean();
            }
            if (json.has("stopOnWorldChange")) {
                cfg().stopOnWorldChange = json.get("stopOnWorldChange").getAsBoolean();
            }

            normalizeConfig();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载自动钓鱼配置失败", e);
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

            json.addProperty("requireFishingRod", cfg().requireFishingRod);
            json.addProperty("autoSwitchToRod", cfg().autoSwitchToRod);
            json.addProperty("preferredRodSlot", cfg().preferredRodSlot);
            json.addProperty("disableWhenGuiOpen", cfg().disableWhenGuiOpen);
            json.addProperty("allowWhilePlayerMoving", cfg().allowWhilePlayerMoving);
            json.addProperty("sendStatusMessage", cfg().sendStatusMessage);

            json.addProperty("enableAutoCastOnStart", cfg().enableAutoCastOnStart);
            json.addProperty("initialCastDelayTicks", cfg().initialCastDelayTicks);
            json.addProperty("autoRecastAfterCatch", cfg().autoRecastAfterCatch);
            json.addProperty("recastDelayMinTicks", cfg().recastDelayMinTicks);
            json.addProperty("recastDelayMaxTicks", cfg().recastDelayMaxTicks);
            json.addProperty("retryCastWhenBobberMissing", cfg().retryCastWhenBobberMissing);
            json.addProperty("retryCastDelayTicks", cfg().retryCastDelayTicks);
            json.addProperty("timeoutRecastEnabled", cfg().timeoutRecastEnabled);
            json.addProperty("maxFishingWaitTicks", cfg().maxFishingWaitTicks);

            json.addProperty("biteDetectMode", cfg().biteDetectMode);
            json.addProperty("ignoreInitialBobberSettleTicks", cfg().ignoreInitialBobberSettleTicks);
            json.addProperty("reelDelayTicks", cfg().reelDelayTicks);
            json.addProperty("minVerticalDropThreshold", cfg().minVerticalDropThreshold);
            json.addProperty("minHorizontalMoveThreshold", cfg().minHorizontalMoveThreshold);
            json.addProperty("confirmBiteTicks", cfg().confirmBiteTicks);
            json.addProperty("debugBiteInfo", cfg().debugBiteInfo);

            json.addProperty("postReelPauseTicks", cfg().postReelPauseTicks);
            json.addProperty("preventDoubleReelTicks", cfg().preventDoubleReelTicks);
            json.addProperty("recastOnlyIfLootSuccess", cfg().recastOnlyIfLootSuccess);
            json.addProperty("resetStateWhenHookGone", cfg().resetStateWhenHookGone);
            json.addProperty("autoRecoverFromInterruptedCast", cfg().autoRecoverFromInterruptedCast);

            json.addProperty("stopWhenRodDurabilityLow", cfg().stopWhenRodDurabilityLow);
            json.addProperty("minRodDurability", cfg().minRodDurability);
            json.addProperty("stopWhenNoRodFound", cfg().stopWhenNoRodFound);
            json.addProperty("pauseWhenHookedEntity", cfg().pauseWhenHookedEntity);
            json.addProperty("stopOnWorldChange", cfg().stopOnWorldChange);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }

            // 'enabled' is per-owner live state — write it to the runtime
            // file so one bot's flag can't stomp another's.
            File runtimeFile = getRuntimeConfigFile();
            if (!runtimeFile.getParentFile().exists()) {
                runtimeFile.getParentFile().mkdirs();
            }
            JsonObject rjson = new JsonObject();
            rjson.addProperty("enabled", rt().enabled);
            try (FileWriter writer = new FileWriter(runtimeFile)) {
                writer.write(rjson.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存自动钓鱼配置失败", e);
        }
    }

    private static void applyDefaultSettings() {
        rt().enabled = false;

        cfg().requireFishingRod = true;
        cfg().autoSwitchToRod = false;
        cfg().preferredRodSlot = 0;
        cfg().disableWhenGuiOpen = true;
        cfg().allowWhilePlayerMoving = false;
        cfg().sendStatusMessage = true;

        cfg().enableAutoCastOnStart = true;
        cfg().initialCastDelayTicks = 8;
        cfg().autoRecastAfterCatch = true;
        cfg().recastDelayMinTicks = 10;
        cfg().recastDelayMaxTicks = 16;
        cfg().retryCastWhenBobberMissing = true;
        cfg().retryCastDelayTicks = 20;
        cfg().timeoutRecastEnabled = true;
        cfg().maxFishingWaitTicks = 1200;

        cfg().biteDetectMode = BITE_MODE_SERVER_SIGNAL;
        cfg().ignoreInitialBobberSettleTicks = 8;
        cfg().reelDelayTicks = 0;
        cfg().minVerticalDropThreshold = 0.04F;
        cfg().minHorizontalMoveThreshold = 0.10F;
        cfg().confirmBiteTicks = 1;
        cfg().debugBiteInfo = false;

        cfg().postReelPauseTicks = 6;
        cfg().preventDoubleReelTicks = 6;
        cfg().recastOnlyIfLootSuccess = false;
        cfg().resetStateWhenHookGone = true;
        cfg().autoRecoverFromInterruptedCast = true;

        cfg().stopWhenRodDurabilityLow = true;
        cfg().minRodDurability = 5;
        cfg().stopWhenNoRodFound = true;
        cfg().pauseWhenHookedEntity = true;
        cfg().stopOnWorldChange = true;
    }

    private static void normalizeConfig() {
        cfg().preferredRodSlot = MathHelper.clamp(cfg().preferredRodSlot, 0, 9);

        cfg().initialCastDelayTicks = MathHelper.clamp(cfg().initialCastDelayTicks, 0, 100);
        cfg().recastDelayMinTicks = MathHelper.clamp(cfg().recastDelayMinTicks, 0, 100);
        cfg().recastDelayMaxTicks = MathHelper.clamp(cfg().recastDelayMaxTicks, cfg().recastDelayMinTicks, 100);
        cfg().retryCastDelayTicks = MathHelper.clamp(cfg().retryCastDelayTicks, 5, 100);
        cfg().maxFishingWaitTicks = MathHelper.clamp(cfg().maxFishingWaitTicks, 40, 2400);

        cfg().ignoreInitialBobberSettleTicks = MathHelper.clamp(cfg().ignoreInitialBobberSettleTicks, 0, 40);
        cfg().reelDelayTicks = MathHelper.clamp(cfg().reelDelayTicks, 0, 20);
        cfg().minVerticalDropThreshold = MathHelper.clamp(cfg().minVerticalDropThreshold, 0.01F, 1.0F);
        cfg().minHorizontalMoveThreshold = MathHelper.clamp(cfg().minHorizontalMoveThreshold, 0.0F, 1.0F);
        cfg().confirmBiteTicks = MathHelper.clamp(cfg().confirmBiteTicks, 1, 5);

        cfg().postReelPauseTicks = MathHelper.clamp(cfg().postReelPauseTicks, 0, 40);
        cfg().preventDoubleReelTicks = MathHelper.clamp(cfg().preventDoubleReelTicks, 0, 20);

        cfg().minRodDurability = MathHelper.clamp(cfg().minRodDurability, 1, 64);

        // Migrate old motion-based profiles without silently retaining unsafe detection.
        cfg().biteDetectMode = BITE_MODE_SERVER_SIGNAL;
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
        Minecraft mc = Minecraft.getMinecraft();
        normalizeConfig();

        if (rt().enabled == targetEnabled) {
            saveConfig();
            return;
        }

        applyEnabledState(targetEnabled);

        saveConfig();

        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() != null && cfg().sendStatusMessage) {
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString(I18n.format(
                    rt().enabled ? "msg.auto_fishing.enabled" : "msg.auto_fishing.disabled")));
        }
    }

    /**
     * Applies the runtime part of an enable-state change without persistence
     * or chat output. Modern configuration tabs use this while editing a
     * draft, then save the resulting state explicitly.
     */
    public void setEnabledForSettingsPreview(boolean targetEnabled) {
        normalizeConfig();
        if (rt().enabled != targetEnabled) {
            applyEnabledState(targetEnabled);
        }
    }

    private void applyEnabledState(boolean targetEnabled) {
        rt().enabled = targetEnabled;
        resetRuntimeState();

        if (rt().enabled && cfg().enableAutoCastOnStart) {
            rt().state = FishingState.WAITING_CAST;
            rt().actionDelayTicks = cfg().initialCastDelayTicks;
            rt().actionDelayUntilMillis = System.currentTimeMillis() + cfg().initialCastDelayTicks * 50L;
        }
    }

    public void onClientDisconnect() {
        if (cfg().stopOnWorldChange && rt().enabled) {
            rt().enabled = false;
            saveConfig();
        }
        resetRuntimeState();
    }

    public void resetRuntimeState() {
        RuntimeState rt = rt();
        rt.biteSignals.clear();
        rt.fishingHookId = -1;
        rt.fishingStartedMillis = 0L;
        rt.lastFishingUpdateMillis = 0L;
        rt.state = FishingState.IDLE;
        rt.actionDelayTicks = 0;
        rt.actionDelayUntilMillis = 0L;
        rt.reelCooldownTicks = 0;
        rt.reelCooldownUntilMillis = 0L;
        rt.fishingTicks = 0;
        rt.biteConfirmCounter = 0;
        rt.bobberWaterStableTicks = 0;
        rt.lastReelLikelyCatch = false;
        rt.reelTargetEntityId = -1;
        rt.lastBobberInWater = false;
        rt.lastBobberX = 0.0D;
        rt.lastBobberY = 0.0D;
        rt.lastBobberZ = 0.0D;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (player == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null) {
            return;
        }

        RuntimeState rt = rt();
        long nowMillis = System.currentTimeMillis();
        observeBites(player, rt);
        // FAST_HOOKS and normal tick relay can coincide. Advance this FSM at
        // most once per 50ms, regardless of adaptive world tick percentage.
        if (nowMillis - rt.lastFishingUpdateMillis < 45L) return;
        rt.lastFishingUpdateMillis = nowMillis;
        if (rt.actionDelayTicks > 0) {
            rt.actionDelayTicks = rt.actionDelayUntilMillis > 0L
                    ? (int) Math.max(0L, (rt.actionDelayUntilMillis - nowMillis + 49L) / 50L)
                    : rt.actionDelayTicks - 1;
        }
        if (rt.reelCooldownTicks > 0) {
            rt.reelCooldownTicks = rt.reelCooldownUntilMillis > 0L
                    ? (int) Math.max(0L, (rt.reelCooldownUntilMillis - nowMillis + 49L) / 50L)
                    : rt.reelCooldownTicks - 1;
        }

        if (!rt().enabled) {
            if (rt.state != FishingState.IDLE) {
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

        boolean playerMoving = isPlayerActivelyMoving(player);
        // Keep watching an already-cast bobber while moving. A bite can be a
        // short server catch window; pausing here loses it until timeout.
        // Reel promptly even while moving; only starting a new cast is paused.
        if (!cfg().allowWhilePlayerMoving && playerMoving
                && rt.state != FishingState.FISHING
                && rt.state != FishingState.WAITING_REEL) {
            return;
        }

        if (!ensureRodReady(player)) {
            return;
        }

        EntityFishHook bobber = player.fishEntity;

        if (bobber != null && rt.state != FishingState.WAITING_REEL && rt.state != FishingState.POST_REEL_DELAY) {
            if (rt.state != FishingState.FISHING || rt.fishingHookId != bobber.getEntityId()) {
                beginFishing(bobber);
            }
        }

        switch (rt.state) {
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
                if (rt.actionDelayTicks <= 0 && performRodUse(player)) {
                    rt.state = FishingState.WAITING_BOBBER;
                    rt.actionDelayTicks = cfg().retryCastDelayTicks;
                    rt.actionDelayUntilMillis = nowMillis + cfg().retryCastDelayTicks * 50L;
                }
                break;

            case WAITING_BOBBER:
                if (bobber != null) {
                    beginFishing(bobber);
                    return;
                }
                if (cfg().retryCastWhenBobberMissing && rt.actionDelayTicks <= 0) {
                    if (performRodUse(player)) {
                        rt.actionDelayTicks = cfg().retryCastDelayTicks;
                    }
                } else if (!cfg().retryCastWhenBobberMissing && rt.actionDelayTicks <= 0) {
                    rt.state = FishingState.IDLE;
                }
                break;

            case FISHING:
                if (bobber == null) {
                    handleUnexpectedHookLoss();
                    return;
                }

                if (cfg().pauseWhenHookedEntity && bobber.caughtEntity != null) {
                    return;
                }

                rt.fishingTicks++;

                if (shouldTriggerBite(bobber)) {
                    rt.biteConfirmCounter++;
                    if (rt.reelCooldownTicks <= 0) {
                        rt.state = FishingState.WAITING_REEL;
                        rt.actionDelayTicks = cfg().reelDelayTicks;
                        rt.actionDelayUntilMillis = nowMillis + cfg().reelDelayTicks * 50L;
                        rt.lastReelLikelyCatch = true;
                        if (cfg().debugBiteInfo && player != null) {
                            player.sendMessage(new TextComponentString(I18n.format("msg.auto_fishing.debug_bite")));
                        }
                    }
                } else {
                    rt.biteConfirmCounter = 0;
                }

                if (cfg().timeoutRecastEnabled && rt.fishingTicks >= cfg().maxFishingWaitTicks) {
                    if (performRodUse(player)) {
                        rt.reelTargetEntityId = bobber.getEntityId();
                        rt.state = FishingState.POST_REEL_DELAY;
                        rt.actionDelayTicks = cfg().postReelPauseTicks;
                        rt.actionDelayUntilMillis = nowMillis + cfg().postReelPauseTicks * 50L;
                        rt.reelCooldownTicks = cfg().preventDoubleReelTicks;
                        rt.reelCooldownUntilMillis = nowMillis + cfg().preventDoubleReelTicks * 50L;
                        rt.lastReelLikelyCatch = false;
                    }
                }
                break;

            case WAITING_REEL:
                if (bobber == null) {
                    rt.state = FishingState.POST_REEL_DELAY;
                    rt.actionDelayTicks = Math.max(rt.actionDelayTicks, cfg().postReelPauseTicks);
                    rt.actionDelayUntilMillis = nowMillis + rt.actionDelayTicks * 50L;
                    return;
                }

                if (rt.actionDelayTicks <= 0 && rt.reelCooldownTicks <= 0) {
                    if (performRodUse(player)) {
                        rt.reelTargetEntityId = bobber.getEntityId();
                        rt.state = FishingState.POST_REEL_DELAY;
                        rt.actionDelayTicks = cfg().postReelPauseTicks;
                        rt.actionDelayUntilMillis = nowMillis + cfg().postReelPauseTicks * 50L;
                        rt.reelCooldownTicks = cfg().preventDoubleReelTicks;
                        rt.reelCooldownUntilMillis = nowMillis + cfg().preventDoubleReelTicks * 50L;
                    } else {
                        rt.actionDelayTicks = cfg().retryCastDelayTicks;
                        rt.actionDelayUntilMillis = nowMillis + cfg().retryCastDelayTicks * 50L;
                    }
                }
                break;

            case POST_REEL_DELAY:
                if (bobber != null) {
                    if (cfg().autoRecoverFromInterruptedCast && rt.actionDelayTicks <= 0) {
                        if (bobber.getEntityId() == rt.reelTargetEntityId) {
                            rt.state = FishingState.WAITING_REEL;
                            rt.actionDelayTicks = 0;
                            rt.actionDelayUntilMillis = 0L;
                            // Keep the existing anti-double-reel deadline.
                            // A local SUCCESS is not server acknowledgement.
                        } else {
                            beginFishing(bobber);
                        }
                    }
                    return;
                }

                if (rt.actionDelayTicks <= 0) {
                    if (cfg().autoRecastAfterCatch && (!cfg().recastOnlyIfLootSuccess || rt.lastReelLikelyCatch)) {
                        scheduleRandomRecast();
                    } else {
                        rt.state = FishingState.IDLE;
                    }
                }
                break;

            default:
                break;
        }
    }

    private void beginFishing(EntityFishHook bobber) {
        RuntimeState rt = rt();
        rt.state = FishingState.FISHING;
        rt.fishingHookId = bobber.getEntityId();
        rt.fishingStartedMillis = System.currentTimeMillis();
        rt.biteSignals.clear();
        rt.lastReelLikelyCatch = false;
        rt.reelTargetEntityId = -1;
        rt.fishingTicks = 0;
        rt.biteConfirmCounter = 0;
        rt.lastBobberInWater = isBobberInFishingWater(bobber);
        rt.bobberWaterStableTicks = rt.lastBobberInWater ? 1 : 0;
        syncBobberPosition(bobber);
    }

    private void handleUnexpectedHookLoss() {
        if (!cfg().resetStateWhenHookGone) {
            rt().state = FishingState.IDLE;
            return;
        }

        if (cfg().autoRecoverFromInterruptedCast && cfg().autoRecastAfterCatch) {
            scheduleRandomRecast();
        } else {
            rt().state = FishingState.IDLE;
        }
    }

    private void scheduleRandomRecast() {
        RuntimeState rt = rt();
        rt.state = FishingState.WAITING_CAST;
        rt.actionDelayTicks = ThreadLocalRandom.current()
                .nextInt(cfg().recastDelayMinTicks, cfg().recastDelayMaxTicks + 1);
        rt.actionDelayUntilMillis = System.currentTimeMillis() + rt.actionDelayTicks * 50L;
        rt.biteConfirmCounter = 0;
        rt.fishingTicks = 0;
    }

    private boolean shouldTriggerBite(EntityFishHook bobber) {
        if (bobber == null) {
            return false;
        }

        RuntimeState rt = rt();
        long now = System.currentTimeMillis();
        AutoFishingBiteSignal signal;
        while ((signal = rt.biteSignals.poll()) != null) {
            // Match server tracking, never independently simulated local motion.
            if (rt.fishingHookId == bobber.getEntityId()
                    && signal.matches(bobber.getEntityId(), rt.fishingStartedMillis, now,
                            bobber.serverPosX, bobber.serverPosY, bobber.serverPosZ)) {
                syncBobberPosition(bobber);
                return true;
            }
        }
        // Client bobber oscillations are NOT authoritative bite evidence.
        // In 1.12.2 ticksCatchable is server-only; motion-only detection both
        // misses real bites and reels repeatedly without loot after mounting.
        syncBobberPosition(bobber);
        return false;
    }

    private boolean isBobberInFishingWater(EntityFishHook bobber) {
        if (bobber == null) {
            return false;
        }
        if (bobber.isInWater()) {
            return true;
        }

        int waterY = MathHelper.floor(bobber.getEntityBoundingBox().minY) - 1;
        BlockPos waterPos = new BlockPos(bobber.posX, waterY, bobber.posZ);
        return bobber.world.getBlockState(waterPos).getMaterial() == Material.WATER;
    }

    private void syncBobberPosition(EntityFishHook bobber) {
        if (bobber == null) {
            return;
        }
        RuntimeState rt = rt();
        rt.lastBobberX = bobber.posX;
        rt.lastBobberY = bobber.posY;
        rt.lastBobberZ = bobber.posZ;
    }

    private boolean ensureRodReady(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }

        ItemStack held = player.getHeldItemMainhand();
        if (isFishingRod(held)) {
            return validateRodDurability(held);
        }

        boolean shouldTrySwitch = cfg().autoSwitchToRod || !cfg().requireFishingRod;
        if (!shouldTrySwitch) {
            return false;
        }

        int rodSlot = findRodHotbarSlot(player);
        if (rodSlot < 0) {
            if (cfg().stopWhenNoRodFound) {
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
        if (!cfg().stopWhenRodDurabilityLow || !stack.isItemStackDamageable()) {
            return true;
        }

        int remain = stack.getMaxDamage() - stack.getItemDamage();
        if (remain <= cfg().minRodDurability) {
            disableWithMessage("msg.auto_fishing.stop_low_durability");
            return false;
        }
        return true;
    }

    private int findRodHotbarSlot(EntityPlayerSP player) {
        if (player == null) {
            return -1;
        }

        int preferredIndex = cfg().preferredRodSlot - 1;
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
        if (player == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundController() == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null) {
            return false;
        }

        ItemStack held = player.getHeldItemMainhand();
        if (!isFishingRod(held)) {
            return false;
        }

        com.mythos.mythosScriptMod.mcp.NexusSessions.boundController().processRightClick(player, com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld(), EnumHand.MAIN_HAND);
        player.swingArm(EnumHand.MAIN_HAND);
        return true;
    }

    private boolean shouldPauseForCurrentScreen(Minecraft mc) {
        if (!cfg().disableWhenGuiOpen || mc == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundScreen() == null) {
            return false;
        }

        String screenClassName = com.mythos.mythosScriptMod.mcp.NexusSessions.boundScreen().getClass().getName();
        return screenClassName.startsWith("com.mythos.mythosScriptMod.gui");
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
        rt().enabled = false;
        saveConfig();
        resetRuntimeState();

        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() != null && cfg().sendStatusMessage) {
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString(I18n.format(key)));
        }
    }
}
