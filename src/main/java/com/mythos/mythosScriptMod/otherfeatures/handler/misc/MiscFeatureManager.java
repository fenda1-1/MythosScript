package com.mythos.mythosScriptMod.otherfeatures.handler.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.otherfeatures.handler.misc.runtime.AutoReconnectRuntime;
import com.mythos.mythosScriptMod.otherfeatures.handler.misc.runtime.AutoRespawnRuntime;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MiscFeatureManager {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        // 每个功能的数值和状态 HUD 开关是配置，按会话分开存；FeatureState 本身是共享的注册表项。
        private final Map<String, Float> FEATURE_VALUES = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Boolean> FEATURE_STATUS_HUD = new java.util.concurrent.ConcurrentHashMap<>();
        private int autoReconnectDelayTicks = DEFAULT_AUTO_RECONNECT_DELAY_TICKS;
        private int autoReconnectMaxAttempts = DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS;
        private boolean autoReconnectInfiniteAttempts = DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS;
        private int autoRespawnDelayTicks = DEFAULT_AUTO_RESPAWN_DELAY_TICKS;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) MiscFeatureManager::loadConfig);
            return config;
        }
    }


    public static final MiscFeatureManager INSTANCE = new MiscFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_misc.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();
    private static final int DEFAULT_AUTO_RECONNECT_DELAY_TICKS = 40;
    private static final int DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS = 3;
    private static final boolean DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS = false;
    private static final int DEFAULT_AUTO_RESPAWN_DELAY_TICKS = 20;


    private static final java.util.concurrent.ConcurrentHashMap<String, RuntimeState> STATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static String featureOwner() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner();
    }

    private static final class RuntimeState {
        final AutoRespawnRuntime autoRespawnRuntime = new AutoRespawnRuntime();
        final AutoReconnectRuntime autoReconnectRuntime = new AutoReconnectRuntime();
    }

    static {
        register(new FeatureState("auto_reconnect", "自动重连",
                "被踢出服务器后自动尝试重新连接到上一个服务器。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("auto_respawn", "死亡自动复活",
                "死亡界面出现后自动发送复活请求并关闭死亡界面。",
                null, 0.0F, 0.0F, 0.0F, true));
        loadConfig();
    }

    private MiscFeatureManager() {
    }

    public static final class FeatureState {
        public final String id;
        public final String name;
        public final String description;
        public final String valueLabel;
        public final float defaultValue;
        public final float minValue;
        public final float maxValue;
        public final boolean behaviorImplemented;

        private final java.util.concurrent.ConcurrentHashMap<String, Boolean> enabledByOwner = new java.util.concurrent.ConcurrentHashMap<>();

        private FeatureState(String id, String name, String description, String valueLabel,
                float defaultValue, float minValue, float maxValue, boolean behaviorImplemented) {
            this.id = safe(id);
            this.name = safe(name);
            this.description = safe(description);
            this.valueLabel = valueLabel == null ? "" : valueLabel.trim();
            this.defaultValue = defaultValue;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.behaviorImplemented = behaviorImplemented;
        }

        public boolean supportsValue() {
            return !valueLabel.isEmpty() && maxValue > minValue;
        }

        public boolean isEnabled() {
            return enabledByOwner.getOrDefault(featureOwner(), Boolean.FALSE);
        }

        public float getValue() {
            return cfg().FEATURE_VALUES.getOrDefault(id, defaultValue);
        }

        public boolean isStatusHudEnabled() {
            return cfg().FEATURE_STATUS_HUD.getOrDefault(id, Boolean.TRUE);
        }

        private void setEnabledInternal(boolean enabled) {
            enabledByOwner.put(featureOwner(), enabled);
        }

        private void setEnabledForOwner(String owner, boolean enabled) {
            enabledByOwner.put(owner == null ? "" : owner, enabled);
        }

        private java.util.Map<String, Boolean> snapshotEnabledByOwner() {
            return new java.util.LinkedHashMap<>(enabledByOwner);
        }

        private void setValueInternal(float value) {
            if (!supportsValue()) {
                cfg().FEATURE_VALUES.put(id, defaultValue);
                return;
            }
            cfg().FEATURE_VALUES.put(id, MathHelper.clamp(value, minValue, maxValue));
        }

        private void setStatusHudEnabledInternal(boolean statusHudEnabled) {
            cfg().FEATURE_STATUS_HUD.put(id, statusHudEnabled);
        }

        private void resetToDefaultInternal() {
            enabledByOwner.put(featureOwner(), false);
            cfg().FEATURE_VALUES.put(id, defaultValue);
            cfg().FEATURE_STATUS_HUD.put(id, true);
        }
    }

    private static void register(FeatureState state) {
        FEATURES.put(state.id, state);
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static String normalizeId(String featureId) {
        return safe(featureId).toLowerCase(Locale.ROOT);
    }

    public static List<FeatureState> getFeatures() {
        return new ArrayList<>(FEATURES.values());
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile(CONFIG_FILE_NAME).toFile();
    }

    public static FeatureState getFeature(String featureId) {
        return FEATURES.get(normalizeId(featureId));
    }

    public static boolean isManagedFeature(String featureId) {
        return FEATURES.containsKey(normalizeId(featureId));
    }

    public static boolean isEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isEnabled();
    }

    public static void toggleFeature(String featureId) {
        setEnabled(featureId, !isEnabled(featureId));
    }

    public static void setEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setEnabledInternal(enabled);
        if ("auto_reconnect".equals(normalizeId(featureId)) && !enabled) {
            rt().autoReconnectRuntime.clearState();
        }
        saveConfig();
    }

    public static boolean isFeatureStatusHudEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isStatusHudEnabled();
    }

    public static boolean shouldDisplayFeatureStatusHud(String featureId) {
        return MovementFeatureManager.isMasterStatusHudEnabled() && isFeatureStatusHudEnabled(featureId);
    }

    public static int getAutoReconnectDelayTicks() {
        return cfg().autoReconnectDelayTicks;
    }

    public static void setAutoReconnectDelayTicks(int ticks) {
        cfg().autoReconnectDelayTicks = MathHelper.clamp(ticks, 5, 200);
        saveConfig();
    }

    public static int getAutoReconnectMaxAttempts() {
        return cfg().autoReconnectMaxAttempts;
    }

    public static void setAutoReconnectMaxAttempts(int attempts) {
        cfg().autoReconnectMaxAttempts = MathHelper.clamp(attempts, 1, 10);
        saveConfig();
    }

    public static boolean isAutoReconnectInfiniteAttempts() {
        return cfg().autoReconnectInfiniteAttempts;
    }

    public static void setAutoReconnectInfiniteAttempts(boolean infiniteAttempts) {
        cfg().autoReconnectInfiniteAttempts = infiniteAttempts;
        saveConfig();
    }

    public static int getAutoRespawnDelayTicks() {
        return cfg().autoRespawnDelayTicks;
    }

    public static void setAutoRespawnDelayTicks(int ticks) {
        cfg().autoRespawnDelayTicks = MathHelper.clamp(ticks, 1, 100);
        saveConfig();
    }

    public static void setFeatureStatusHudEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setStatusHudEnabledInternal(enabled);
        saveConfig();
    }

    public static void resetFeature(String featureId) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.resetToDefaultInternal();
        String normalizedId = normalizeId(featureId);
        if ("auto_reconnect".equals(normalizedId)) {
            cfg().autoReconnectDelayTicks = DEFAULT_AUTO_RECONNECT_DELAY_TICKS;
            cfg().autoReconnectMaxAttempts = DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS;
            cfg().autoReconnectInfiniteAttempts = DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS;
        } else if ("auto_respawn".equals(normalizedId)) {
            cfg().autoRespawnDelayTicks = DEFAULT_AUTO_RESPAWN_DELAY_TICKS;
        }
        saveConfig();
    }

    public static void loadConfig() {
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        cfg().autoReconnectDelayTicks = DEFAULT_AUTO_RECONNECT_DELAY_TICKS;
        cfg().autoReconnectMaxAttempts = DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS;
        cfg().autoReconnectInfiniteAttempts = DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS;
        cfg().autoRespawnDelayTicks = DEFAULT_AUTO_RESPAWN_DELAY_TICKS;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                saveConfig();
                return;
            }

            JsonObject root;

            try (FileReader reader = new FileReader(configFile)) {

                root = new JsonParser().parse(reader).getAsJsonObject();

            }
            JsonObject featuresObject = root.has("features") && root.get("features").isJsonObject()
                    ? root.getAsJsonObject("features")
                    : new JsonObject();

            for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                if (!featuresObject.has(entry.getKey()) || !featuresObject.get(entry.getKey()).isJsonObject()) {
                    continue;
                }
                JsonObject item = featuresObject.getAsJsonObject(entry.getKey());
                FeatureState state = entry.getValue();
                if (item.has("statusHudEnabled")) {
                    state.setStatusHudEnabledInternal(item.get("statusHudEnabled").getAsBoolean());
                }
                if (item.has("value")) {
                    state.setValueInternal(item.get("value").getAsFloat());
                }
            }

            File runtimeFile = getRuntimeConfigFile();
            if (runtimeFile.exists()) {
                try (FileReader rr = new FileReader(runtimeFile)) {
                    JsonObject rroot = new JsonParser().parse(rr).getAsJsonObject();
                    if (rroot.has("features") && rroot.get("features").isJsonObject()) {
                        JsonObject rfeatures = rroot.getAsJsonObject("features");
                        for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                            if (!rfeatures.has(entry.getKey()) || !rfeatures.get(entry.getKey()).isJsonObject()) {
                                continue;
                            }
                            JsonObject item = rfeatures.getAsJsonObject(entry.getKey());
                            if (item.has("enabledByOwner") && item.get("enabledByOwner").isJsonObject()) {
                                JsonObject owners = item.getAsJsonObject("enabledByOwner");
                                for (Map.Entry<String, com.google.gson.JsonElement> oe : owners.entrySet()) {
                                    if (oe.getValue().isJsonPrimitive()) {
                                        entry.getValue().setEnabledForOwner(oe.getKey(), oe.getValue().getAsBoolean());
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                    if (!featuresObject.has(entry.getKey()) || !featuresObject.get(entry.getKey()).isJsonObject()) {
                        continue;
                    }
                    JsonObject item = featuresObject.getAsJsonObject(entry.getKey());
                    if (item.has("enabled")) {
                        entry.getValue().setEnabledForOwner(featureOwner(), item.get("enabled").getAsBoolean());
                    }
                }
            }
            if (root.has("autoReconnectDelayTicks")) {
                cfg().autoReconnectDelayTicks = MathHelper.clamp(root.get("autoReconnectDelayTicks").getAsInt(), 5, 200);
            }
            if (root.has("autoReconnectMaxAttempts")) {
                cfg().autoReconnectMaxAttempts = MathHelper.clamp(root.get("autoReconnectMaxAttempts").getAsInt(), 1, 10);
            }
            if (root.has("autoReconnectInfiniteAttempts")) {
                cfg().autoReconnectInfiniteAttempts = root.get("autoReconnectInfiniteAttempts").getAsBoolean();
            }
            if (root.has("autoRespawnDelayTicks")) {
                cfg().autoRespawnDelayTicks = MathHelper.clamp(root.get("autoRespawnDelayTicks").getAsInt(), 1, 100);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载杂项功能配置失败", e);
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            JsonObject featuresObject = new JsonObject();
            for (FeatureState state : FEATURES.values()) {
                JsonObject item = new JsonObject();
                item.addProperty("statusHudEnabled", state.isStatusHudEnabled());
                if (state.supportsValue()) {
                    item.addProperty("value", state.getValue());
                }
                featuresObject.add(state.id, item);
            }
            root.add("features", featuresObject);
            root.addProperty("autoReconnectDelayTicks", cfg().autoReconnectDelayTicks);
            root.addProperty("autoReconnectMaxAttempts", cfg().autoReconnectMaxAttempts);
            root.addProperty("autoReconnectInfiniteAttempts", cfg().autoReconnectInfiniteAttempts);
            root.addProperty("autoRespawnDelayTicks", cfg().autoRespawnDelayTicks);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }

            File runtimeFile = getRuntimeConfigFile();
            if (!runtimeFile.getParentFile().exists()) {
                runtimeFile.getParentFile().mkdirs();
            }
            JsonObject rroot = new JsonObject();
            JsonObject rfeatures = new JsonObject();
            for (FeatureState state : FEATURES.values()) {
                JsonObject owners = new JsonObject();
                for (java.util.Map.Entry<String, Boolean> e : state.snapshotEnabledByOwner().entrySet()) {
                    owners.addProperty(e.getKey() == null ? "" : e.getKey(), e.getValue());
                }
                JsonObject item = new JsonObject();
                item.add("enabledByOwner", owners);
                rfeatures.add(state.id, item);
            }
            rroot.add("features", rfeatures);
            try (FileWriter rw = new FileWriter(runtimeFile)) {
                rw.write(rroot.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存杂项功能配置失败", e);
        }
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve(CONFIG_FILE_NAME).toFile();
    }

    public void onClientDisconnect() {
        rt().autoRespawnRuntime.onClientDisconnect();
        rt().autoReconnectRuntime.onClientDisconnectReset(isEnabled("auto_reconnect"));
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        rt().autoReconnectRuntime.onClientDisconnected(mc, isEnabled("auto_reconnect"), cfg().autoReconnectDelayTicks);
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        rt().autoReconnectRuntime.onClientConnected();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        rt().autoRespawnRuntime.tick(isEnabled("auto_respawn"), cfg().autoRespawnDelayTicks);
        rt().autoReconnectRuntime.rememberCurrentServer(mc);
        rt().autoReconnectRuntime.tick(isEnabled("auto_reconnect"), cfg().autoReconnectInfiniteAttempts,
                cfg().autoReconnectMaxAttempts, cfg().autoReconnectDelayTicks, mc);
    }

    public static List<String> getStatusLines() {
        return getStatusLines(false);
    }

    public static List<String> getStatusLines(boolean forcePreview) {
        if (!forcePreview && !MovementFeatureManager.isMasterStatusHudEnabled()) {
            return new ArrayList<>();
        }

        List<String> activeNames = new ArrayList<>();
        for (FeatureState state : FEATURES.values()) {
            if (state != null && state.isEnabled() && state.isStatusHudEnabled()) {
                activeNames.add(state.name);
            }
        }

        List<String> lines = new ArrayList<>();
        if (activeNames.isEmpty()) {
            return lines;
        }

        lines.add("§a[杂项] §f" + activeNames.size() + " 项开启");
        StringBuilder builder = new StringBuilder("§7");
        for (int i = 0; i < activeNames.size() && i < 4; i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(activeNames.get(i));
        }
        lines.add(builder.toString());

        String runtime = INSTANCE.getRuntimeHudLine();
        if (!runtime.isEmpty()) {
            lines.add(runtime);
        }
        return lines;
    }

    public static String getFeatureRuntimeSummary(String featureId) {
        return INSTANCE.buildFeatureRuntimeSummary(normalizeId(featureId));
    }

    private String getRuntimeHudLine() {
        List<String> parts = new ArrayList<>();
        if (isEnabled("auto_reconnect") && shouldDisplayFeatureStatusHud("auto_reconnect")) {
            if (rt().autoReconnectRuntime.getPendingReconnectServer() != null) {
                parts.add((cfg().autoReconnectInfiniteAttempts ? "§b无限重连倒计时: " : "§b重连倒计时: ")
                        + rt().autoReconnectRuntime.getReconnectDelayTicks());
            }
        }
        if (parts.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("§7");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append("  ");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private String buildFeatureRuntimeSummary(String featureId) {
        if (featureId == null || featureId.isEmpty()) {
            return "待机";
        }
        if (!isManagedFeature(featureId)) {
            return "未找到功能";
        }

        switch (featureId) {
        case "auto_reconnect":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (rt().autoReconnectRuntime.getPendingReconnectServer() != null) {
                return "准备重连 " + rt().autoReconnectRuntime.getPendingReconnectServer().serverIP
                        + (cfg().autoReconnectInfiniteAttempts
                                ? "，无限重试，倒计时 " + rt().autoReconnectRuntime.getReconnectDelayTicks() + " tick"
                                : "，倒计时 " + rt().autoReconnectRuntime.getReconnectDelayTicks() + " tick");
            }
            return rt().autoReconnectRuntime.getReconnectAttemptCount() > 0
                    ? (cfg().autoReconnectInfiniteAttempts
                            ? "已发起第 " + rt().autoReconnectRuntime.getReconnectAttemptCount() + " 次重连尝试（无限模式）"
                            : "已发起第 " + rt().autoReconnectRuntime.getReconnectAttemptCount() + "/" + cfg().autoReconnectMaxAttempts + " 次重连尝试")
                    : (cfg().autoReconnectInfiniteAttempts ? "等待断线界面（无限重试）" : "等待断线界面");
        case "auto_respawn":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            return rt().autoRespawnRuntime.getAutoRespawnCooldownTicks() > 0
                    ? "复活冷却剩余 " + rt().autoRespawnRuntime.getAutoRespawnCooldownTicks() + " tick"
                    : "检测到死亡界面时自动复活";
        default:
            return "基础逻辑已接入";
        }
    }
}
