package com.mythos.mythosScriptMod.otherfeatures.handler.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldFeatureManager {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        // 每个功能的数值和状态 HUD 开关是配置，按会话分开存；FeatureState 本身是共享的注册表项。
        private final Map<String, Float> FEATURE_VALUES = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Boolean> FEATURE_STATUS_HUD = new java.util.concurrent.ConcurrentHashMap<>();
        public boolean coordDisplayShowX = true;
        public boolean coordDisplayShowY = true;
        public boolean coordDisplayShowZ = true;
        public boolean coordDisplayShowFacing = true;
        public boolean coordDisplayShowBiome = true;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) WorldFeatureManager::loadConfig);
            return config;
        }
    }


    public static final WorldFeatureManager INSTANCE = new WorldFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_world.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static String featureOwner() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner();
    }

    private static final class RuntimeState {
        private long customWorldTime = 6000L;
        private long cachedNaturalWorldTime = 6000L;
        private float customRainStrength = 0.0F;
        private float customThunderStrength = 0.0F;
        private float cachedNaturalRainStrength = 0.0F;
        private float cachedNaturalThunderStrength = 0.0F;
        private boolean naturalTimeCaptured = false;
        private boolean timeOverrideActive = false;
        private boolean naturalWeatherCaptured = false;
        private boolean weatherOverrideActive = false;
        private BlockPos lastPlayerPos = BlockPos.ORIGIN;
        private String lastBiomeName = "";
        private List<StructureInfo> nearbyStructures = new ArrayList<>();
        private int structureScanCooldown = 0;
    }

    static {
        register(new FeatureState("time_modifier", "时间修改",
                "自定义世界时间，可设置为白天、黑夜或任意时刻。左键快速开关，右键打开世界设置。",
                "时间值", 6000.0F, 0.0F, 24000.0F, true));
        register(new FeatureState("weather_control", "天气控制",
                "控制雨雪天气强度，可完全关闭或自定义。左键快速开关，右键打开世界设置。",
                "雨强度", 0.0F, 0.0F, 1.0F, true));
        register(new FeatureState("coord_display", "坐标显示",
                "增强版坐标HUD，显示XYZ坐标、朝向、生物群系等信息。左键快速开关，右键打开世界设置。",
                null, 0.0F, 0.0F, 0.0F, true));
        loadConfig();
    }

    private WorldFeatureManager() {
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

        private final ConcurrentHashMap<String, Boolean> enabledByOwner = new ConcurrentHashMap<>();

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

    public static final class StructureInfo {
        public final String type;
        public final BlockPos position;
        public final double distance;

        public StructureInfo(String type, BlockPos position, double distance) {
            this.type = type;
            this.position = position;
            this.distance = distance;
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

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile(CONFIG_FILE_NAME).toFile();
    }

    public static List<FeatureState> getFeatures() {
        return new ArrayList<>(FEATURES.values());
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
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        boolean wasEnabled = state.isEnabled();
        boolean enabled = !wasEnabled;
        state.setEnabledInternal(enabled);
        INSTANCE.handleRuntimeSideEffectsAfterToggle(featureId, wasEnabled, enabled);
        saveConfig();
    }

    public static void setEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        boolean wasEnabled = state.isEnabled();
        state.setEnabledInternal(enabled);
        INSTANCE.handleRuntimeSideEffectsAfterToggle(featureId, wasEnabled, enabled);
        saveConfig();
    }

    public static void setValue(String featureId, float value) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setValueInternal(value);
        saveConfig();
    }

    public static boolean isFeatureStatusHudEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isStatusHudEnabled();
    }

    public static boolean shouldDisplayFeatureStatusHud(String featureId) {
        return MovementFeatureManager.isMasterStatusHudEnabled() && isFeatureStatusHudEnabled(featureId);
    }

    public static boolean shouldOverrideVisualTime(World world) {
        return world instanceof WorldClient && isEnabled("time_modifier");
    }

    public static long getVisualWorldTime() {
        return Math.round(getConfiguredValue("time_modifier", 6000.0F));
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
        boolean wasEnabled = state.isEnabled();
        state.resetToDefaultInternal();
        if ("coord_display".equals(normalizeId(featureId))) {
            resetCoordDisplayOptions();
        }
        INSTANCE.handleRuntimeSideEffectsAfterToggle(featureId, wasEnabled, false);
        saveConfig();
    }

    public static void resetAllToDefaults() {
        boolean hadWeatherOverride = INSTANCE.isWeatherOverrideEnabled();
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        resetCoordDisplayOptions();
        if (hadWeatherOverride) {
            INSTANCE.restoreNaturalWeather(Minecraft.getMinecraft());
        }
        saveConfig();
    }

    public static float getConfiguredValue(String featureId, float fallback) {
        FeatureState state = getFeature(featureId);
        if (state == null || !state.supportsValue()) {
            return fallback;
        }
        return MathHelper.clamp(state.getValue(), state.minValue, state.maxValue);
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

        lines.add("§a[世界] §f" + activeNames.size() + " 项开启");
        StringBuilder builder = new StringBuilder("§7");
        for (int i = 0; i < activeNames.size() && i < 4; i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(activeNames.get(i));
        }
        if (activeNames.size() > 4) {
            builder.append(" §8+").append(activeNames.size() - 4);
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



    public static void resetCoordDisplayOptions() {
        cfg().coordDisplayShowX = true;
        cfg().coordDisplayShowY = true;
        cfg().coordDisplayShowZ = true;
        cfg().coordDisplayShowFacing = true;
        cfg().coordDisplayShowBiome = true;
    }

    public static void loadConfig() {
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        resetCoordDisplayOptions();

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
                if ("coord_display".equals(entry.getKey()) && item.has("options") && item.get("options").isJsonObject()) {
                    JsonObject options = item.getAsJsonObject("options");
                    if (options.has("showX")) {
                        cfg().coordDisplayShowX = options.get("showX").getAsBoolean();
                    }
                    if (options.has("showY")) {
                        cfg().coordDisplayShowY = options.get("showY").getAsBoolean();
                    }
                    if (options.has("showZ")) {
                        cfg().coordDisplayShowZ = options.get("showZ").getAsBoolean();
                    }
                    if (options.has("showFacing")) {
                        cfg().coordDisplayShowFacing = options.get("showFacing").getAsBoolean();
                    }
                    if (options.has("showBiome")) {
                        cfg().coordDisplayShowBiome = options.get("showBiome").getAsBoolean();
                    }
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
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载世界功能配置失败", e);
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
                if ("coord_display".equals(state.id)) {
                    JsonObject options = new JsonObject();
                    options.addProperty("showX", cfg().coordDisplayShowX);
                    options.addProperty("showY", cfg().coordDisplayShowY);
                    options.addProperty("showZ", cfg().coordDisplayShowZ);
                    options.addProperty("showFacing", cfg().coordDisplayShowFacing);
                    options.addProperty("showBiome", cfg().coordDisplayShowBiome);
                    item.add("options", options);
                }
                featuresObject.add(state.id, item);
            }
            root.add("features", featuresObject);

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
            mythosScriptMod.LOGGER.error("保存世界功能配置失败", e);
        }
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve(CONFIG_FILE_NAME).toFile();
    }

    public void onClientDisconnect() {
        resetRuntimeState();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (mc.world == null || player == null) {
            resetRuntimeState();
            return;
        }

        tickRuntimeState();

        if (player.isDead || player.getHealth() <= 0.0F) {
            resetRuntimeState();
            return;
        }

        updateNaturalTimeCache(mc.world);
        applyTimeModifier(mc);
        updateNaturalWeatherCache(mc.world);
        applyWeatherControl(mc);
        updateCoordDisplay(mc, player);
    }

    private void tickRuntimeState() {
        if (rt().structureScanCooldown > 0) {
            rt().structureScanCooldown--;
        }
    }

    private void resetRuntimeState() {
        RuntimeState rt = rt();
        rt.customWorldTime = 6000L;
        rt.cachedNaturalWorldTime = 6000L;
        rt.customRainStrength = 0.0F;
        rt.customThunderStrength = 0.0F;
        rt.cachedNaturalRainStrength = 0.0F;
        rt.cachedNaturalThunderStrength = 0.0F;
        rt.naturalTimeCaptured = false;
        rt.timeOverrideActive = false;
        rt.naturalWeatherCaptured = false;
        rt.weatherOverrideActive = false;
        rt.lastPlayerPos = BlockPos.ORIGIN;
        rt.lastBiomeName = "";
        rt.nearbyStructures.clear();
        rt.structureScanCooldown = 0;
    }

    // ==================== 时间修改功能 ====================
    private void applyTimeModifier(Minecraft mc) {
        if (!isEnabled("time_modifier") || mc.world == null) {
            return;
        }

        long targetTime = Math.round(getConfiguredValue("time_modifier", 6000.0F));
        rt().customWorldTime = targetTime;
        if (!rt().timeOverrideActive) {
            cacheNaturalWorldTime(mc.world);
        }
        try {
            mc.world.setWorldTime(targetTime);
            rt().timeOverrideActive = true;
        } catch (Exception ignored) {
        }
    }

    private void updateNaturalTimeCache(World world) {
        if (world == null || isEnabled("time_modifier")) {
            return;
        }
        cacheNaturalWorldTime(world);
    }

    private void cacheNaturalWorldTime(World world) {
        if (world == null) {
            return;
        }
        rt().cachedNaturalWorldTime = world.getWorldTime();
        rt().naturalTimeCaptured = true;
    }

    private void restoreNaturalTime(Minecraft mc) {
        RuntimeState rt = rt();
        if (mc == null || mc.world == null || !rt.naturalTimeCaptured) {
            rt.timeOverrideActive = false;
            return;
        }
        try {
            mc.world.setWorldTime(rt.cachedNaturalWorldTime);
        } catch (Exception ignored) {
        }
        rt.timeOverrideActive = false;
    }

    // ==================== 天气控制功能 ====================
    private void applyWeatherControl(Minecraft mc) {
        if (!isEnabled("weather_control") || mc.world == null) {
            return;
        }

        if (!rt().weatherOverrideActive) {
            cacheNaturalWeather(mc.world);
        }
        float rainStrength = getConfiguredValue("weather_control", 0.0F);
        rt().customRainStrength = rainStrength;
        rt().customThunderStrength = rainStrength * 0.5F;
        
        try {
            mc.world.setRainStrength(rainStrength);
            mc.world.setThunderStrength(rt().customThunderStrength);
            rt().weatherOverrideActive = true;
        } catch (Exception e) {
            // 忽略错误
        }
    }

    private void updateNaturalWeatherCache(World world) {
        if (world == null || isWeatherOverrideEnabled()) {
            return;
        }
        cacheNaturalWeather(world);
    }

    private void cacheNaturalWeather(World world) {
        if (world == null) {
            return;
        }
        rt().cachedNaturalRainStrength = world.getRainStrength(1.0F);
        rt().cachedNaturalThunderStrength = world.getThunderStrength(1.0F);
        rt().naturalWeatherCaptured = true;
    }

    private boolean isWeatherOverrideEnabled() {
        return isEnabled("weather_control");
    }

    private void restoreNaturalWeather(Minecraft mc) {
        RuntimeState rt = rt();
        if (mc == null || mc.world == null || !rt.naturalWeatherCaptured) {
            rt.weatherOverrideActive = false;
            return;
        }
        try {
            mc.world.setRainStrength(rt.cachedNaturalRainStrength);
            mc.world.setThunderStrength(rt.cachedNaturalThunderStrength);
        } catch (Exception ignored) {
        }
        rt.weatherOverrideActive = false;
    }

    private void handleRuntimeSideEffectsAfterToggle(String featureId, boolean wasEnabled, boolean enabled) {
        String normalizedId = normalizeId(featureId);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }

        if ("time_modifier".equals(normalizedId)) {
            if (!wasEnabled && enabled) {
                if (!rt().timeOverrideActive) {
                    cacheNaturalWorldTime(mc.world);
                }
                applyTimeModifier(mc);
                return;
            }

            if (wasEnabled && !enabled) {
                restoreNaturalTime(mc);
            }
            return;
        }

        if (!"weather_control".equals(normalizedId)) {
            return;
        }

        if (!wasEnabled && enabled) {
            if (!rt().weatherOverrideActive) {
                cacheNaturalWeather(mc.world);
            }
            applyWeatherControl(mc);
            return;
        }

        if (wasEnabled && !enabled) {
            if (isWeatherOverrideEnabled()) {
                applyWeatherControl(mc);
            } else {
                restoreNaturalWeather(mc);
            }
        }
    }

    // ==================== 坐标显示功能 ====================
    private void updateCoordDisplay(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("coord_display")) {
            return;
        }

        BlockPos pos = player.getPosition();
        rt().lastPlayerPos = pos;
        
        try {
            Biome biome = mc.world.getBiome(pos);
            if (biome != null) {
                rt().lastBiomeName = biome.getBiomeName();
            }
        } catch (Exception e) {
            rt().lastBiomeName = "未知";
        }
    }

    // ==================== 获取坐标信息 ====================
    public static String getCoordInfo() {
        if (!isEnabled("coord_display")) {
            return "";
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return "";
        }

        BlockPos pos = rt().lastPlayerPos;
        String facing = getCardinalDirection(player.rotationYaw);

        List<String> parts = new ArrayList<>();
        if (cfg().coordDisplayShowX) {
            parts.add("§fX: §a" + pos.getX());
        }
        if (cfg().coordDisplayShowY) {
            parts.add("§fY: §a" + pos.getY());
        }
        if (cfg().coordDisplayShowZ) {
            parts.add("§fZ: §a" + pos.getZ());
        }
        if (cfg().coordDisplayShowFacing) {
            parts.add("§7[§f" + facing + "§7]");
        }
        if (cfg().coordDisplayShowBiome && !rt().lastBiomeName.isEmpty()) {
            parts.add("§7生物群系: §b" + rt().lastBiomeName);
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join(" §7| ", parts);
    }

    private static String getCardinalDirection(float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw < -157.5F) return "北";
        if (yaw < -112.5F) return "东北";
        if (yaw < -67.5F) return "东";
        if (yaw < -22.5F) return "东南";
        if (yaw < 22.5F) return "南";
        if (yaw < 67.5F) return "西南";
        if (yaw < 112.5F) return "西";
        if (yaw < 157.5F) return "西北";
        return "北";
    }


    private String getRuntimeHudLine() {
        List<String> parts = new ArrayList<>();
        
        if (isEnabled("time_modifier") && shouldDisplayFeatureStatusHud("time_modifier")) {
            String timeStr = formatWorldTime(rt().customWorldTime);
            parts.add("§e时间: " + timeStr);
        }
        
        if (isEnabled("weather_control") && shouldDisplayFeatureStatusHud("weather_control")) {
            parts.add(String.format("§b雨强: %.1f", rt().customRainStrength));
        }

        if (isEnabled("coord_display") && shouldDisplayFeatureStatusHud("coord_display")) {
            String coordInfo = getCoordInfo();
            if (!coordInfo.isEmpty()) {
                parts.add(coordInfo);
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

        switch (featureId) {
        case "time_modifier":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            return "当前时间: " + formatWorldTime(rt().customWorldTime);
        case "weather_control":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            return String.format("雨强度: %.2f", rt().customRainStrength);
        case "coord_display":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            String coordInfo = getCoordInfo();
            return coordInfo.isEmpty() ? "未选择任何 HUD 字段" : coordInfo;
        default:
            FeatureState state = getFeature(featureId);
            if (state == null) {
                return "未找到功能";
            }
            return state.behaviorImplemented ? "基础逻辑已接入" : "仅配置占位";
        }
    }

    private String formatWorldTime(long time) {
        time = time % 24000L;
        int hours = (int) ((time / 1000L + 6L) % 24L);
        int minutes = (int) ((time % 1000L) * 60L / 1000L);
        return String.format("%02d:%02d", hours, minutes);
    }

    private String formatBlockPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
