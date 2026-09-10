package com.zszl.zszlScriptMod.otherfeatures.handler.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.runtime.AutoReconnectRuntime;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.runtime.AutoRespawnRuntime;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
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

    public static final MiscFeatureManager INSTANCE = new MiscFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_misc.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();
    private static final int DEFAULT_AUTO_RECONNECT_DELAY_TICKS = 40;
    private static final int DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS = 3;
    private static final boolean DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS = false;
    private static final int DEFAULT_AUTO_RESPAWN_DELAY_TICKS = 20;

    private static int autoReconnectDelayTicks = DEFAULT_AUTO_RECONNECT_DELAY_TICKS;
    private static int autoReconnectMaxAttempts = DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS;
    private static boolean autoReconnectInfiniteAttempts = DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS;
    private static int autoRespawnDelayTicks = DEFAULT_AUTO_RESPAWN_DELAY_TICKS;

    private final AutoRespawnRuntime autoRespawnRuntime = new AutoRespawnRuntime();
    private final AutoReconnectRuntime autoReconnectRuntime = new AutoReconnectRuntime();

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

        private boolean enabled;
        private float value;
        private boolean statusHudEnabled;

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
            this.enabled = false;
            this.value = defaultValue;
            this.statusHudEnabled = true;
        }

        public boolean supportsValue() {
            return !valueLabel.isEmpty() && maxValue > minValue;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public float getValue() {
            return value;
        }

        public boolean isStatusHudEnabled() {
            return statusHudEnabled;
        }

        private void setEnabledInternal(boolean enabled) {
            this.enabled = enabled;
        }

        private void setValueInternal(float value) {
            if (!supportsValue()) {
                this.value = defaultValue;
                return;
            }
            this.value = MathHelper.clamp(value, minValue, maxValue);
        }

        private void setStatusHudEnabledInternal(boolean statusHudEnabled) {
            this.statusHudEnabled = statusHudEnabled;
        }

        private void resetToDefaultInternal() {
            this.enabled = false;
            this.value = defaultValue;
            this.statusHudEnabled = true;
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
        return ProfileManager.getCurrentProfileDir().resolve(CONFIG_FILE_NAME).toFile();
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
            INSTANCE.autoReconnectRuntime.clearState();
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
        return autoReconnectDelayTicks;
    }

    public static void setAutoReconnectDelayTicks(int ticks) {
        autoReconnectDelayTicks = MathHelper.clamp(ticks, 5, 200);
        saveConfig();
    }

    public static int getAutoReconnectMaxAttempts() {
        return autoReconnectMaxAttempts;
    }

    public static void setAutoReconnectMaxAttempts(int attempts) {
        autoReconnectMaxAttempts = MathHelper.clamp(attempts, 1, 10);
        saveConfig();
    }

    public static boolean isAutoReconnectInfiniteAttempts() {
        return autoReconnectInfiniteAttempts;
    }

    public static void setAutoReconnectInfiniteAttempts(boolean infiniteAttempts) {
        autoReconnectInfiniteAttempts = infiniteAttempts;
        saveConfig();
    }

    public static int getAutoRespawnDelayTicks() {
        return autoRespawnDelayTicks;
    }

    public static void setAutoRespawnDelayTicks(int ticks) {
        autoRespawnDelayTicks = MathHelper.clamp(ticks, 1, 100);
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
            autoReconnectDelayTicks = DEFAULT_AUTO_RECONNECT_DELAY_TICKS;
            autoReconnectMaxAttempts = DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS;
            autoReconnectInfiniteAttempts = DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS;
        } else if ("auto_respawn".equals(normalizedId)) {
            autoRespawnDelayTicks = DEFAULT_AUTO_RESPAWN_DELAY_TICKS;
        }
        saveConfig();
    }

    public static void loadConfig() {
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        autoReconnectDelayTicks = DEFAULT_AUTO_RECONNECT_DELAY_TICKS;
        autoReconnectMaxAttempts = DEFAULT_AUTO_RECONNECT_MAX_ATTEMPTS;
        autoReconnectInfiniteAttempts = DEFAULT_AUTO_RECONNECT_INFINITE_ATTEMPTS;
        autoRespawnDelayTicks = DEFAULT_AUTO_RESPAWN_DELAY_TICKS;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                saveConfig();
                return;
            }

            JsonObject root = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();
            JsonObject featuresObject = root.has("features") && root.get("features").isJsonObject()
                    ? root.getAsJsonObject("features")
                    : new JsonObject();

            for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                if (!featuresObject.has(entry.getKey()) || !featuresObject.get(entry.getKey()).isJsonObject()) {
                    continue;
                }
                JsonObject item = featuresObject.getAsJsonObject(entry.getKey());
                FeatureState state = entry.getValue();
                if (item.has("enabled")) {
                    state.setEnabledInternal(item.get("enabled").getAsBoolean());
                }
                if (item.has("statusHudEnabled")) {
                    state.setStatusHudEnabledInternal(item.get("statusHudEnabled").getAsBoolean());
                }
                if (item.has("value")) {
                    state.setValueInternal(item.get("value").getAsFloat());
                }
            }
            if (root.has("autoReconnectDelayTicks")) {
                autoReconnectDelayTicks = MathHelper.clamp(root.get("autoReconnectDelayTicks").getAsInt(), 5, 200);
            }
            if (root.has("autoReconnectMaxAttempts")) {
                autoReconnectMaxAttempts = MathHelper.clamp(root.get("autoReconnectMaxAttempts").getAsInt(), 1, 10);
            }
            if (root.has("autoReconnectInfiniteAttempts")) {
                autoReconnectInfiniteAttempts = root.get("autoReconnectInfiniteAttempts").getAsBoolean();
            }
            if (root.has("autoRespawnDelayTicks")) {
                autoRespawnDelayTicks = MathHelper.clamp(root.get("autoRespawnDelayTicks").getAsInt(), 1, 100);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载杂项功能配置失败", e);
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
                item.addProperty("enabled", state.isEnabled());
                item.addProperty("statusHudEnabled", state.isStatusHudEnabled());
                if (state.supportsValue()) {
                    item.addProperty("value", state.getValue());
                }
                featuresObject.add(state.id, item);
            }
            root.add("features", featuresObject);
            root.addProperty("autoReconnectDelayTicks", autoReconnectDelayTicks);
            root.addProperty("autoReconnectMaxAttempts", autoReconnectMaxAttempts);
            root.addProperty("autoReconnectInfiniteAttempts", autoReconnectInfiniteAttempts);
            root.addProperty("autoRespawnDelayTicks", autoRespawnDelayTicks);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存杂项功能配置失败", e);
        }
    }

    public void onClientDisconnect() {
        this.autoRespawnRuntime.onClientDisconnect();
        this.autoReconnectRuntime.onClientDisconnectReset(isEnabled("auto_reconnect"));
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        this.autoReconnectRuntime.onClientDisconnected(mc, isEnabled("auto_reconnect"), autoReconnectDelayTicks);
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        this.autoReconnectRuntime.onClientConnected();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        this.autoRespawnRuntime.tick(isEnabled("auto_respawn"), autoRespawnDelayTicks);
        this.autoReconnectRuntime.rememberCurrentServer(mc);
        this.autoReconnectRuntime.tick(isEnabled("auto_reconnect"), autoReconnectInfiniteAttempts,
                autoReconnectMaxAttempts, autoReconnectDelayTicks, mc);
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
            if (this.autoReconnectRuntime.getPendingReconnectServer() != null) {
                parts.add((autoReconnectInfiniteAttempts ? "§b无限重连倒计时: " : "§b重连倒计时: ")
                        + this.autoReconnectRuntime.getReconnectDelayTicks());
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
            if (this.autoReconnectRuntime.getPendingReconnectServer() != null) {
                return "准备重连 " + this.autoReconnectRuntime.getPendingReconnectServer().serverIP
                        + (autoReconnectInfiniteAttempts
                                ? "，无限重试，倒计时 " + this.autoReconnectRuntime.getReconnectDelayTicks() + " tick"
                                : "，倒计时 " + this.autoReconnectRuntime.getReconnectDelayTicks() + " tick");
            }
            return this.autoReconnectRuntime.getReconnectAttemptCount() > 0
                    ? (autoReconnectInfiniteAttempts
                            ? "已发起第 " + this.autoReconnectRuntime.getReconnectAttemptCount() + " 次重连尝试（无限模式）"
                            : "已发起第 " + this.autoReconnectRuntime.getReconnectAttemptCount() + "/" + autoReconnectMaxAttempts + " 次重连尝试")
                    : (autoReconnectInfiniteAttempts ? "等待断线界面（无限重试）" : "等待断线界面");
        case "auto_respawn":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            return this.autoRespawnRuntime.getAutoRespawnCooldownTicks() > 0
                    ? "复活冷却剩余 " + this.autoRespawnRuntime.getAutoRespawnCooldownTicks() + " tick"
                    : "检测到死亡界面时自动复活";
        default:
            return "基础逻辑已接入";
        }
    }
}
