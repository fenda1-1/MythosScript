// 文件路径: src/main/java/com/zszl/zszlScriptMod/path/PathSequenceManager.java
package com.zszl.zszlScriptMod.path;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.config.HumanLikeMovementConfig;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.handlers.AutoEatHandler;
import com.zszl.zszlScriptMod.handlers.AutoEscapeHandler;
import com.zszl.zszlScriptMod.handlers.AutoFishingHandler;
import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;
import com.zszl.zszlScriptMod.handlers.AutoPickupHandler;
import com.zszl.zszlScriptMod.handlers.ConditionalExecutionHandler;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.handlers.GuiBlockerHandler;
import com.zszl.zszlScriptMod.handlers.GuiVisibilityHandler;
import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import com.zszl.zszlScriptMod.handlers.ItemSpreadHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.handlers.NearbyItemPickupActionHandler;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;
import com.zszl.zszlScriptMod.shadowbaritone.utils.HumanLikeMovementController;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.path.template.LegacyActionTemplateManager;
import com.zszl.zszlScriptMod.path.trigger.PlayerListTriggerSupport;
import com.zszl.zszlScriptMod.utils.HudTextScanner;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.TickRangeSpec;
import com.zszl.zszlScriptMod.utils.locator.ActionTargetLocator;
import com.zszl.zszlScriptMod.handlers.AutoUseItemHandler;
import com.zszl.zszlScriptMod.system.AutoUseItemRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PathSequenceManager {
    private static final int MIN_DELAY_TICKS = 0;
    private static final int MAX_DELAY_TICKS = Integer.MAX_VALUE - 1;

    private static class SequenceCallContext {
        private final String sequenceName;
        private final PathSequence callerSequence;
        private final PathSequenceEventListener.ProgressSnapshot snapshot;
        private final int loopCount;
        private final int loopCounter;
        private final boolean isLooping;

        private SequenceCallContext(PathSequence callerSequence,
                PathSequenceEventListener.ProgressSnapshot snapshot,
                int loopCount,
                int loopCounter,
                boolean isLooping) {
            this.sequenceName = callerSequence.getName();
            this.callerSequence = callerSequence;
            this.snapshot = snapshot;
            this.loopCount = loopCount;
            this.loopCounter = loopCounter;
            this.isLooping = isLooping;
        }
    }

    private static final Map<String, PathSequence> sequences = new HashMap<>();
    private static final Map<String, PathSequence> customSequences = new LinkedHashMap<>();
    // Runtime-only registry is deliberately excluded from editor/persistence lists.
    private static final Map<String, PathSequence> transientSequences = new LinkedHashMap<>();

    public static void runTransientSequence(PathSequence sequence, int loops,
            Map<String, Object> variables, int stepIndex, int actionIndex) {
        if (hasSequence(sequence.getName())) throw new IllegalArgumentException("Sequence name already exists");
        transientSequences.put(sequence.getName(), sequence);
        try {
            runPathSequenceInternal(sequence.getName(), false, loops, variables, stepIndex, actionIndex);
        } catch (RuntimeException e) {
            transientSequences.remove(sequence.getName());
            throw e;
        }
    }

    public static void cleanupTransientSequences() {
        transientSequences.entrySet().removeIf(entry ->
                !PathSequenceEventListener.isSequenceActiveForMcp(entry.getKey())
                && !hasPendingCaller(entry.getValue()));
    }

    public static void runSequenceForMcp(String name, int loops, Map<String, Object> variables,
            int stepIndex, int actionIndex) {
        runPathSequenceInternal(name, false, loops, variables, stepIndex, actionIndex);
    }
    private static final Deque<SequenceCallContext> runSequenceCallStack = new ArrayDeque<>();
    private static final Path BUILTIN_SEQUENCES_PATH = Paths.get(ModConfig.CONFIG_DIR, "pathsequences.json");
    private static final Path WINDOW_CLICK_UUID_MAP_PATH = Paths.get(ModConfig.CONFIG_DIR,
            "window_click_uuid_map.json");
    private static final Path RUN_SEQUENCE_ACTION_STATE_PATH = Paths.get(ModConfig.CONFIG_DIR,
            "run_sequence_action_state.json");
    private static final String STEP_RETRY_DEFAULTS_FILE_NAME = "path_step_retry_defaults.json";
    private static final int BUILTIN_STEP_RETRY_COUNT = 3;
    private static final int BUILTIN_STEP_PATH_RETRY_TIMEOUT_SECONDS = 5;
    private static final int BUILTIN_STEP_ARRIVAL_TOLERANCE_BLOCKS = 1;

    private static final Gson GSON = new GsonBuilder()
            .serializeSpecialFloatingPointValues()
            .setPrettyPrinting()
            .create();

    private static final Map<String, Integer> windowClickLastSlotByUuid = new ConcurrentHashMap<>();
    private static volatile boolean windowClickMapLoaded = false;
    private static final Map<String, Integer> runSequenceActionCountByUuid = new ConcurrentHashMap<>();
    private static volatile boolean runSequenceActionStateLoaded = false;
    private static volatile boolean stepRetryDefaultsLoaded = false;
    private static volatile Path stepRetryDefaultsProfileDir = null;
    private static volatile int defaultStepRetryCount = BUILTIN_STEP_RETRY_COUNT;
    private static volatile int defaultStepPathRetryTimeoutSeconds = BUILTIN_STEP_PATH_RETRY_TIMEOUT_SECONDS;
    private static volatile int defaultStepArrivalToleranceBlocks = BUILTIN_STEP_ARRIVAL_TOLERANCE_BLOCKS;

    private static final String BUILTIN_SEQUENCES_RESOURCE = "pathsequences.json";
    private static List<String> categories = new ArrayList<>(Arrays.asList(
            formatI18nOrFallback("path.category.default", "默认")));
    private static Set<String> hiddenCategories = new HashSet<>();

    private static String defaultCategoryName() {
        return formatI18nOrFallback("path.category.default", "默认");
    }

    private static String builtinCategoryName() {
        return formatI18nOrFallback("path.category.builtin", "内置");
    }

    /** I18n is initialized by Minecraft after this class can be loaded by unit tests. */
    private static String formatI18nOrFallback(String key, String fallback) {
        try {
            String value = I18n.format(key);
            return value == null || value.trim().isEmpty() || value.equals(key) ? fallback : value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalizeClickLocatorMode(String locatorMode) {
        if (ActionTargetLocator.TARGET_MODE_POSITION.equalsIgnoreCase(locatorMode)) {
            return ActionTargetLocator.CLICK_MODE_COORDINATE;
        }
        return locatorMode == null || locatorMode.trim().isEmpty()
                ? ActionTargetLocator.CLICK_MODE_COORDINATE
                : locatorMode;
    }

    private static boolean isClickCoordinateMode(String locatorMode) {
        return ActionTargetLocator.CLICK_MODE_COORDINATE
                .equalsIgnoreCase(normalizeClickLocatorMode(locatorMode));
    }

    private static int getIntParam(JsonObject params, String key, int fallback) {
        if (params == null || key == null || !params.has(key)) {
            return fallback;
        }
        try {
            return params.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String getStringParam(JsonObject params, String key, String fallback) {
        if (params == null || key == null || !params.has(key)) {
            return fallback;
        }
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static BlockPos tryParseTargetPos(JsonObject params) {
        if (params == null || !params.has("pos")) {
            return null;
        }
        try {
            JsonElement posElement = params.get("pos");
            if (posElement == null || !posElement.isJsonArray()) {
                return null;
            }
            JsonArray posArray = posElement.getAsJsonArray();
            if (posArray.size() < 3) {
                return null;
            }
            return new BlockPos(posArray.get(0).getAsInt(), posArray.get(1).getAsInt(), posArray.get(2).getAsInt());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getClickMouseButton(JsonObject params) {
        if (params == null || !params.has("left")) {
            return "left";
        }
        try {
            JsonElement buttonElement = params.get("left");
            if (buttonElement != null
                    && buttonElement.isJsonPrimitive()
                    && buttonElement.getAsJsonPrimitive().isBoolean()) {
                return buttonElement.getAsBoolean() ? "left" : "right";
            }
            String raw = buttonElement == null ? "" : buttonElement.getAsString();
            if ("shift_left".equalsIgnoreCase(raw) || "shift-left".equalsIgnoreCase(raw)) {
                return "shift_left";
            }
            if ("shift_right".equalsIgnoreCase(raw) || "shift-right".equalsIgnoreCase(raw)) {
                return "shift_right";
            }
            if ("middle".equalsIgnoreCase(raw)) {
                return "middle";
            }
            if ("right".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                return "right";
            }
        } catch (Exception ignored) {
        }
        return "left";
    }

    private static boolean isShiftModifiedMouseButton(String mouseButton) {
        return "shift_left".equalsIgnoreCase(mouseButton) || "shift-right".equalsIgnoreCase(mouseButton)
                || "shift_right".equalsIgnoreCase(mouseButton) || "shift-left".equalsIgnoreCase(mouseButton);
    }

    private static String describeEntityTypeDisplay(String entityType) {
        String normalized = entityType == null ? "" : entityType.trim();
        if ("player".equalsIgnoreCase(normalized) || "玩家".equalsIgnoreCase(normalized)) {
            return "玩家";
        }
        if ("hostile".equalsIgnoreCase(normalized)
                || "monster".equalsIgnoreCase(normalized)
                || "mob".equalsIgnoreCase(normalized)
                || "敌对生物".equalsIgnoreCase(normalized)
                || "怪物".equalsIgnoreCase(normalized)) {
            return "敌对生物";
        }
        if ("passive".equalsIgnoreCase(normalized)
                || "animal".equalsIgnoreCase(normalized)
                || "被动生物".equalsIgnoreCase(normalized)
                || "动物".equalsIgnoreCase(normalized)) {
            return "被动生物";
        }
        if ("all".equalsIgnoreCase(normalized)
                || "entity".equalsIgnoreCase(normalized)
                || "所有实体".equalsIgnoreCase(normalized)) {
            return "所有实体";
        }
        return "";
    }

    private static String describeNearbyEntityFilter(JsonObject params, boolean includeMinCount) {
        if (params == null) {
            return "未配置";
        }
        List<String> parts = new ArrayList<>();
        String typeDisplay = describeEntityTypeDisplay(params.has("entityType") ? params.get("entityType").getAsString() : "");
        String entityName = params.has("entityName") ? params.get("entityName").getAsString().trim() : "";
        if (!typeDisplay.isEmpty()) {
            parts.add(typeDisplay);
        }
        if (!entityName.isEmpty()) {
            parts.add("名称 " + entityName);
        }
        if (includeMinCount) {
            int minCount = params.has("minCount")
                    ? params.get("minCount").getAsInt()
                    : (params.has("count") ? params.get("count").getAsInt() : 1);
            parts.add("至少" + Math.max(1, minCount) + "个");
        }
        return parts.isEmpty() ? "未配置" : String.join(" / ", parts);
    }

    private static String describePlayerListFilter(JsonObject params) {
        if (params == null) {
            return "未配置";
        }
        String summary = PlayerListTriggerSupport.buildEntriesSummary(PlayerListTriggerSupport.readEntries(params));
        return summary == null || summary.trim().isEmpty() ? "未配置" : summary.trim();
    }

    private static String getClickMouseButtonText(JsonObject params) {
        String mouseButton = getClickMouseButton(params);
        if ("shift_left".equalsIgnoreCase(mouseButton)) {
            return "Shift左键";
        }
        if ("shift_right".equalsIgnoreCase(mouseButton)) {
            return "Shift右键";
        }
        if ("middle".equalsIgnoreCase(mouseButton)) {
            return "中键";
        }
        return "right".equalsIgnoreCase(mouseButton) ? "右键" : "左键";
    }

    private static synchronized void ensureWindowClickMapLoaded() {
        if (windowClickMapLoaded) {
            return;
        }
        windowClickMapLoaded = true;
        if (!Files.exists(WINDOW_CLICK_UUID_MAP_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(WINDOW_CLICK_UUID_MAP_PATH, StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, Integer>>() {
            }.getType();
            Map<String, Integer> loaded = GSON.fromJson(reader, mapType);
            if (loaded != null) {
                windowClickLastSlotByUuid.clear();
                windowClickLastSlotByUuid.putAll(loaded);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[window_click] 读取UUID映射失败: {}", WINDOW_CLICK_UUID_MAP_PATH, e);
        }
    }

    private static synchronized void saveWindowClickMap() {
        try {
            Files.createDirectories(WINDOW_CLICK_UUID_MAP_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(WINDOW_CLICK_UUID_MAP_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(windowClickLastSlotByUuid, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[window_click] 保存UUID映射失败: {}", WINDOW_CLICK_UUID_MAP_PATH, e);
        }
    }

    public static void removeWindowClickRecord(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return;
        }
        ensureWindowClickMapLoaded();
        if (windowClickLastSlotByUuid.remove(uuid) != null) {
            saveWindowClickMap();
        }
    }

    private static synchronized void ensureRunSequenceActionStateLoaded() {
        if (runSequenceActionStateLoaded) {
            return;
        }
        runSequenceActionStateLoaded = true;
        if (!Files.exists(RUN_SEQUENCE_ACTION_STATE_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(RUN_SEQUENCE_ACTION_STATE_PATH, StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, Integer>>() {
            }.getType();
            Map<String, Integer> loaded = GSON.fromJson(reader, mapType);
            if (loaded != null) {
                runSequenceActionCountByUuid.clear();
                runSequenceActionCountByUuid.putAll(loaded);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 读取动作计数失败: {}", RUN_SEQUENCE_ACTION_STATE_PATH, e);
        }
    }

    private static synchronized void saveRunSequenceActionState() {
        try {
            Files.createDirectories(RUN_SEQUENCE_ACTION_STATE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(RUN_SEQUENCE_ACTION_STATE_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(runSequenceActionCountByUuid, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 保存动作计数失败: {}", RUN_SEQUENCE_ACTION_STATE_PATH, e);
        }
    }

    public static void removeRunSequenceActionRecord(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return;
        }
        ensureRunSequenceActionStateLoaded();
        if (runSequenceActionCountByUuid.remove(uuid) != null) {
            saveRunSequenceActionState();
        }
    }

    private static Path getStepRetryDefaultsPath() {
        Path profileDir = ProfileManager.getCurrentProfileDir();
        return profileDir == null
                ? Paths.get(ModConfig.CONFIG_DIR, STEP_RETRY_DEFAULTS_FILE_NAME)
                : profileDir.resolve(STEP_RETRY_DEFAULTS_FILE_NAME);
    }

    private static void resetStepRetryDefaultsToBuiltinInternal() {
        defaultStepRetryCount = BUILTIN_STEP_RETRY_COUNT;
        defaultStepPathRetryTimeoutSeconds = BUILTIN_STEP_PATH_RETRY_TIMEOUT_SECONDS;
        defaultStepArrivalToleranceBlocks = BUILTIN_STEP_ARRIVAL_TOLERANCE_BLOCKS;
    }

    private static synchronized void ensureStepRetryDefaultsLoaded() {
        Path currentProfileDir = ProfileManager.getCurrentProfileDir();
        if (stepRetryDefaultsLoaded && Objects.equals(stepRetryDefaultsProfileDir, currentProfileDir)) {
            return;
        }
        stepRetryDefaultsLoaded = true;
        stepRetryDefaultsProfileDir = currentProfileDir;
        resetStepRetryDefaultsToBuiltinInternal();

        Path defaultsPath = getStepRetryDefaultsPath();
        if (!Files.exists(defaultsPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(defaultsPath, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.has("retryCount")) {
                defaultStepRetryCount = Math.max(0, root.get("retryCount").getAsInt());
            }
            if (root.has("pathRetryTimeoutSeconds")) {
                defaultStepPathRetryTimeoutSeconds = Math.max(0, root.get("pathRetryTimeoutSeconds").getAsInt());
            }
            if (root.has("arrivalToleranceBlocks")) {
                defaultStepArrivalToleranceBlocks = Math.max(0, root.get("arrivalToleranceBlocks").getAsInt());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[path_retry] 读取步骤寻路重试默认值失败: {}", defaultsPath, e);
            resetStepRetryDefaultsToBuiltinInternal();
        }
    }

    private static synchronized void saveStepRetryDefaults() {
        Path defaultsPath = getStepRetryDefaultsPath();
        try {
            Files.createDirectories(defaultsPath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("retryCount", Math.max(0, defaultStepRetryCount));
            root.addProperty("pathRetryTimeoutSeconds", Math.max(0, defaultStepPathRetryTimeoutSeconds));
            root.addProperty("arrivalToleranceBlocks", Math.max(0, defaultStepArrivalToleranceBlocks));
            try (Writer writer = Files.newBufferedWriter(defaultsPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[path_retry] 保存步骤寻路重试默认值失败: {}", defaultsPath, e);
        }
    }

    public static int getDefaultStepRetryCount() {
        ensureStepRetryDefaultsLoaded();
        return Math.max(0, defaultStepRetryCount);
    }

    public static int getDefaultStepPathRetryTimeoutSeconds() {
        ensureStepRetryDefaultsLoaded();
        return Math.max(0, defaultStepPathRetryTimeoutSeconds);
    }

    public static int getBuiltinStepRetryCount() {
        return BUILTIN_STEP_RETRY_COUNT;
    }

    public static int getBuiltinStepPathRetryTimeoutSeconds() {
        return BUILTIN_STEP_PATH_RETRY_TIMEOUT_SECONDS;
    }

    public static int getDefaultStepArrivalToleranceBlocks() {
        ensureStepRetryDefaultsLoaded();
        return Math.max(0, defaultStepArrivalToleranceBlocks);
    }

    public static int getBuiltinStepArrivalToleranceBlocks() {
        return BUILTIN_STEP_ARRIVAL_TOLERANCE_BLOCKS;
    }

    public static synchronized void updateDefaultStepRetrySettings(int retryCount, int timeoutSeconds,
            int arrivalToleranceBlocks) {
        ensureStepRetryDefaultsLoaded();
        defaultStepRetryCount = Math.max(0, retryCount);
        defaultStepPathRetryTimeoutSeconds = Math.max(0, timeoutSeconds);
        defaultStepArrivalToleranceBlocks = Math.max(0, arrivalToleranceBlocks);
        saveStepRetryDefaults();
    }

    public static synchronized void resetDefaultStepRetrySettings() {
        ensureStepRetryDefaultsLoaded();
        resetStepRetryDefaultsToBuiltinInternal();
        saveStepRetryDefaults();
    }

    public static String getPersistentActionUuid(ActionData actionData) {
        if (actionData == null || actionData.type == null || actionData.params == null) {
            return "";
        }
        if (!supportsPersistentActionUuid(actionData.type) || !actionData.params.has("uuid")) {
            return "";
        }
        try {
            String uuid = actionData.params.get("uuid").getAsString();
            return uuid == null ? "" : uuid.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void removePersistentActionRecord(ActionData actionData) {
        if (actionData == null || actionData.type == null) {
            return;
        }
        String uuid = getPersistentActionUuid(actionData);
        if (uuid.isEmpty()) {
            return;
        }
        if ("window_click".equalsIgnoreCase(actionData.type)) {
            removeWindowClickRecord(uuid);
        } else if ("run_sequence".equalsIgnoreCase(actionData.type)) {
            removeRunSequenceActionRecord(uuid);
        }
    }

    private static String getOtherFeatureDisplayName(String featureId, String fallbackName) {
        String normalizedId = featureId == null ? "" : featureId.trim();
        if (!normalizedId.isEmpty()) {
            OtherFeatureGroupManager.reload();
            for (OtherFeatureGroupManager.GroupDef group : OtherFeatureGroupManager.getGroups()) {
                if (group == null || group.features == null) {
                    continue;
                }
                for (OtherFeatureGroupManager.FeatureDef feature : group.features) {
                    if (feature != null && normalizedId.equalsIgnoreCase(feature.id)) {
                        if (feature.name != null && !feature.name.trim().isEmpty()) {
                            return feature.name.trim();
                        }
                        return normalizedId;
                    }
                }
            }
            return normalizedId;
        }
        return fallbackName == null ? "" : fallbackName.trim();
    }

    private static void applyOtherFeatureToggle(String featureId, boolean enabled) {
        String normalizedId = featureId == null ? "" : featureId.trim();
        if (normalizedId.isEmpty()) {
            return;
        }

        if ("speed".equalsIgnoreCase(normalizedId)) {
            SpeedHandler.INSTANCE.setEnabled(enabled);
            return;
        }

        if (MovementFeatureManager.isManagedFeature(normalizedId)) {
            MovementFeatureManager.setEnabled(normalizedId, enabled);
            return;
        }

        if (RenderFeatureManager.isManagedFeature(normalizedId)) {
            RenderFeatureManager.setEnabled(normalizedId, enabled);
        }
    }

    private static void applyBaritoneFreeLookToggle(boolean enabled) {
        Settings settings = BaritoneAPI.getSettings();
        if (settings == null) {
            return;
        }
        try {
            SettingsUtil.parseAndApply(settings, "freelook", String.valueOf(enabled));
            SettingsUtil.save(settings);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("设置 Baritone 自由视角失败: enabled={}", enabled, e);
        }
    }

    private static void applyBaritoneHumanLikeToggle(boolean enabled) {
        HumanLikeMovementConfig.INSTANCE.enabled = enabled;
        HumanLikeMovementConfig.save();
        if (!enabled) {
            HumanLikeMovementController.INSTANCE.reset();
        }
    }

    private static void applyBaritoneFlightToggle(boolean enabled) {
        Settings settings = BaritoneAPI.getSettings();
        if (settings == null) {
            return;
        }
        try {
            settings.allowFlightPathing.value = enabled;
            SettingsUtil.save(settings);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("设置 Baritone 飞行寻路失败: enabled={}", enabled, e);
        }
    }

    private static boolean ensureHuntVerticalRangeDefaults(String actionType, JsonObject params) {
        if (!"hunt".equalsIgnoreCase(actionType) || params == null) {
            return false;
        }
        boolean changed = false;
        if (!params.has("huntUpRange") || params.get("huntUpRange").isJsonNull()) {
            params.addProperty("huntUpRange", 3.0D);
            changed = true;
        }
        if (!params.has("huntDownRange") || params.get("huntDownRange").isJsonNull()) {
            params.addProperty("huntDownRange", 3.0D);
            changed = true;
        }
        if (!params.has("enableAreaSweep") || params.get("enableAreaSweep").isJsonNull()) {
            params.addProperty("enableAreaSweep", true);
            changed = true;
        }
        if (!params.has("areaSweepCellSize") || params.get("areaSweepCellSize").isJsonNull()) {
            params.addProperty("areaSweepCellSize", 8);
            changed = true;
        }
        if (!params.has("waitForWhitelistRespawn") || params.get("waitForWhitelistRespawn").isJsonNull()) {
            params.addProperty("waitForWhitelistRespawn", false);
            changed = true;
        }
        if (!params.has("whitelistRespawnTimeoutSeconds")
                || params.get("whitelistRespawnTimeoutSeconds").isJsonNull()) {
            params.addProperty("whitelistRespawnTimeoutSeconds", 120);
            changed = true;
        }
        if (!params.has("waitForWhitelistRespawnAfterCompletion")
                || params.get("waitForWhitelistRespawnAfterCompletion").isJsonNull()) {
            params.addProperty("waitForWhitelistRespawnAfterCompletion", false);
            changed = true;
        }
        if (!params.has("confirmKillCompletion") || params.get("confirmKillCompletion").isJsonNull()) {
            params.addProperty("confirmKillCompletion", false);
            changed = true;
        }
        if (!params.has("killCompletionConfirmSeconds")
                || params.get("killCompletionConfirmSeconds").isJsonNull()) {
            params.addProperty("killCompletionConfirmSeconds", 3);
            changed = true;
        }
        if (!params.has("forceEndHunt") || params.get("forceEndHunt").isJsonNull()) {
            params.addProperty("forceEndHunt", false);
            changed = true;
        }
        if (!params.has("forceEndHuntTimeoutSeconds") || params.get("forceEndHuntTimeoutSeconds").isJsonNull()) {
            params.addProperty("forceEndHuntTimeoutSeconds", 60);
            changed = true;
        }
        return changed;
    }

    public static class ActionData {
        public String type;
        public JsonObject params;

        public ActionData(String type, JsonObject params) {
            this.type = type;
            this.params = params == null ? new JsonObject() : params;
            ensureHuntVerticalRangeDefaults(this.type, this.params);
        }

        public ActionData(ActionData other) {
            this(other, false);
        }

        /**
         * Copies an action while optionally retaining its persistent runtime
         * identity. Normal user-created copies must receive a new identity;
         * editor drafts and undo snapshots must not silently do so.
         */
        public ActionData(ActionData other, boolean preservePersistentUuid) {
            this.type = other.type;
            this.params = new JsonParser().parse(other.params.toString()).getAsJsonObject();
            ensureHuntVerticalRangeDefaults(this.type, this.params);
            if (!preservePersistentUuid && supportsPersistentActionUuid(this.type)) {
                this.params.addProperty("uuid", java.util.UUID.randomUUID().toString());
            }
        }

        public String getDescription() {
            try {
                switch (type.toLowerCase()) {
                    case "command":
                        return I18n.format("path.action.desc.command", params.get("command").getAsString());
                    case "system_message":
                        return I18n.format("path.action.desc.system_message",
                                params.has("message") ? params.get("message").getAsString() : "");
                    case "paste_text":
                        return "粘贴文字: " + (params.has("text") ? params.get("text").getAsString() : "");
                    case "disconnect":
                        return I18n.format("path.action.desc.disconnect");
                    case "delay":
                        return I18n.format("path.action.desc.delay", formatTickDelaySpec(params, "ticks", 0))
                                + getDelayNormalizationDescriptionSuffix(params);
                    case "key":
                        String keyState = params.has("state") ? params.get("state").getAsString() : "Press";
                        String keyStateText = "Up".equalsIgnoreCase(keyState)
                                ? "抬起"
                                : ("Press".equalsIgnoreCase(keyState) ? "单击" : "按下");
                        return I18n.format("path.action.desc.key", params.get("key").getAsString(), keyStateText);
                    case "jump":
                        int jumpCountDesc = params.has("count") ? params.get("count").getAsInt() : 1;
                        int jumpIntervalDesc = params.has("intervalTicks") ? params.get("intervalTicks").getAsInt()
                                : 0;
                        return I18n.format("path.action.desc.jump", Math.max(1, jumpCountDesc),
                                Math.max(0, jumpIntervalDesc));
                    case "click":
                        String clickLocatorModeDesc = params.has("locatorMode")
                                ? params.get("locatorMode").getAsString()
                                : ActionTargetLocator.CLICK_MODE_COORDINATE;
                        if (!isClickCoordinateMode(clickLocatorModeDesc)) {
                            return "点击元素: "
                                    + (params.has("locatorText") ? params.get("locatorText").getAsString() : "")
                                    + " / " + getClickMouseButtonText(params)
                                    + " / "
                                    + (ActionTargetLocator.CLICK_MODE_BUTTON_TEXT
                                            .equalsIgnoreCase(clickLocatorModeDesc)
                                                    ? "按钮文本"
                                                    : (ActionTargetLocator.CLICK_MODE_SLOT_TEXT
                                                            .equalsIgnoreCase(clickLocatorModeDesc)
                                                                    ? "槽位文本"
                                                                    : "元素路径"));
                        }
                        return I18n.format("path.action.desc.click", getClickMouseButtonText(params),
                                params.get("x").getAsInt(),
                                params.get("y").getAsInt());
                    case "window_click":
                        String containsText = params.has("contains") ? params.get("contains").getAsString() : "";
                        if (params.has("locatorMode")
                                && !ActionTargetLocator.SLOT_MODE_DIRECT
                                        .equalsIgnoreCase(params.get("locatorMode").getAsString())) {
                            String slotLocatorMode = params.get("locatorMode").getAsString();
                            return "容器内槽位点击: "
                                    + ("EMPTY_SLOT".equalsIgnoreCase(slotLocatorMode)
                                            ? "第一个空槽位"
                                            : (ActionTargetLocator.SLOT_MODE_PATH.equalsIgnoreCase(slotLocatorMode)
                                                    ? "按路径 " + (params.has("locatorText")
                                                            ? params.get("locatorText").getAsString()
                                                            : "")
                                                    : "按物品文本 " + (params.has("locatorText")
                                                            ? params.get("locatorText").getAsString()
                                                            : "")))
                                    + (containsText.trim().isEmpty() ? "" : " / 匹配: " + containsText)
                                    + " / 类型: "
                                    + ModUtils.clickTypeToDisplayName(
                                            params.has("clickType") ? params.get("clickType").getAsString() : "PICKUP");
                        }
                        String widRaw = params.has("windowId") ? params.get("windowId").getAsString() : "-1";
                        int wid;
                        try {
                            wid = ModUtils.parseWindowIdSpec(widRaw);
                        } catch (Exception ex) {
                            wid = -1;
                        }
                        String slotRaw = params.has("slot") ? params.get("slot").getAsString() : "-1";
                        String slotBase = params.has("slotBase") ? params.get("slotBase").getAsString() : "DEC";
                        int slot;
                        try {
                            slot = ModUtils.parseNumericSpec(slotRaw, slotBase);
                        } catch (Exception ex) {
                            slot = -1;
                        }
                        int button = params.has("button") ? params.get("button").getAsInt() : 0;
                        String clickType = params.has("clickType") ? params.get("clickType").getAsString() : "PICKUP";
                        String desc = I18n.format("path.action.desc.window_click", wid, slot, button,
                                ModUtils.clickTypeToDisplayName(clickType));
                        if (!containsText.trim().isEmpty()) {
                            desc += " / 匹配: " + containsText;
                        }
                        return desc;
                    case "conditional_window_click":
                        if (params.has("locatorMode")
                                && !ActionTargetLocator.SLOT_MODE_DIRECT
                                        .equalsIgnoreCase(params.get("locatorMode").getAsString())) {
                            return "容器内槽位点击: "
                                    + ("EMPTY_SLOT".equalsIgnoreCase(params.get("locatorMode").getAsString())
                                            ? "第一个空槽位"
                                            : (ActionTargetLocator.SLOT_MODE_PATH
                                                    .equalsIgnoreCase(params.get("locatorMode").getAsString())
                                                            ? "按路径 " + (params.has("locatorText")
                                                                    ? params.get("locatorText").getAsString()
                                                                    : "")
                                                            : "按物品文本 " + (params.has("locatorText")
                                                                    ? params.get("locatorText").getAsString()
                                                                    : "")))
                                    + " / 匹配: "
                                    + (params.has("contains") ? params.get("contains").getAsString() : "");
                        }
                        return "容器内槽位点击: "
                                + (params.has("slot") ? params.get("slot").getAsString() : "-1")
                                + " / 匹配: "
                                + (params.has("contains") ? params.get("contains").getAsString() : "");
                    case "take_all_items_safe":
                        boolean shiftQuickMoveSafe = !params.has("shiftQuickMove")
                                || params.get("shiftQuickMove").getAsBoolean();
                        return "安全取出全部物品"
                                + (shiftQuickMoveSafe ? " (Shift一键拾取)" : " (逐格拾取)");
                    case "hud_text_check":
                        return "HUD 文本识别: "
                                + (params.has("contains") ? params.get("contains").getAsString() : "");
                    case "condition_inventory_item":
                        List<String> condInvExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
                        String condInvItem = params.has("itemName") ? params.get("itemName").getAsString() : "";
                        String condInvMatch = params.has("matchMode") ? params.get("matchMode").getAsString()
                                : "CONTAINS";
                        int condInvCount = params.has("count") ? params.get("count").getAsInt() : 1;
                        int condInvSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        int condInvNbtCount = countStringListParam(params, "requiredNbtTags", "requiredNbtTagsText");
                        int condInvSlotCount = countIntListParam(params, "inventorySlots", "inventorySlotsText");
                        String condInvNbtMode = params.has("requiredNbtTagsMode")
                                ? params.get("requiredNbtTagsMode").getAsString()
                                : ItemFilterHandler.NBT_TAG_MATCH_MODE_CONTAINS;
                        return "条件分支-背包物品: "
                                + (!condInvExpressions.isEmpty()
                                        ? InventoryItemFilterExpressionEngine.summarizeExpressions(condInvExpressions)
                                        : (condInvItem == null || condInvItem.trim().isEmpty() ? "任意物品" : condInvItem)
                                                + " / " + ("EXACT".equalsIgnoreCase(condInvMatch) ? "完全相同" : "包含"))
                                + " / 数量>=" + Math.max(1, condInvCount)
                                + (condInvExpressions.isEmpty() && condInvNbtCount > 0
                                        ? " / NBT"
                                                + (ItemFilterHandler.NBT_TAG_MATCH_MODE_NOT_CONTAINS
                                                        .equalsIgnoreCase(condInvNbtMode) ? "不包含" : "包含")
                                                + condInvNbtCount + "项"
                                        : "")
                                + (condInvSlotCount > 0 ? " / 指定槽位" + condInvSlotCount + "个" : " / 全背包")
                                + " / 失败跳过" + Math.max(0, condInvSkip) + "个动作";
                    case "condition_gui_title":
                        String condGuiTitle = params.has("title") ? params.get("title").getAsString() : "";
                        int condGuiSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-GUI标题: 包含 '" + condGuiTitle + "' / 失败跳过"
                                + Math.max(0, condGuiSkip) + "个动作";
                    case "condition_player_in_area":
                        String condAreaCenter = params.has("center") ? params.get("center").toString() : "[0,0,0]";
                        double condAreaRadius = params.has("radius") ? params.get("radius").getAsDouble() : 3.0;
                        int condAreaSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-玩家区域: 中心 " + condAreaCenter + " 半径 " + condAreaRadius
                                + " / 失败跳过" + Math.max(0, condAreaSkip) + "个动作";
                    case "condition_player_list":
                        int condPlayerListSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-玩家列表: " + describePlayerListFilter(params)
                                + " / 失败跳过" + Math.max(0, condPlayerListSkip) + "个动作";
                    case "condition_entity_nearby":
                        double condEntityRadius = params.has("radius") ? params.get("radius").getAsDouble() : 6.0;
                        int condEntitySkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-附近实体: " + describeNearbyEntityFilter(params, true) + " / 半径 " + condEntityRadius
                                + " / 失败跳过" + Math.max(0, condEntitySkip) + "个动作";
                    case "condition_expression":
                        List<String> conditionExpressions = readBooleanExpressionList(params, "expressions",
                                "expression");
                        int exprSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-表达式: " + describeBooleanExpressionSummary(conditionExpressions)
                                + " / 失败跳过" + Math.max(0, exprSkip) + "个动作";
                    case "wait_until_inventory_item":
                        List<String> waitInvExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
                        int waitInvNbtCount = countStringListParam(params, "requiredNbtTags", "requiredNbtTagsText");
                        int waitInvSlotCount = countIntListParam(params, "inventorySlots", "inventorySlotsText");
                        String waitInvNbtMode = params.has("requiredNbtTagsMode")
                                ? params.get("requiredNbtTagsMode").getAsString()
                                : ItemFilterHandler.NBT_TAG_MATCH_MODE_CONTAINS;
                        return "等待背包物品: "
                                + (!waitInvExpressions.isEmpty()
                                        ? InventoryItemFilterExpressionEngine.summarizeExpressions(waitInvExpressions)
                                        : (params.has("itemName") && !params.get("itemName").getAsString().trim().isEmpty()
                                                ? params.get("itemName").getAsString()
                                                : "任意物品"))
                                + (waitInvExpressions.isEmpty() && waitInvNbtCount > 0
                                        ? " / NBT"
                                                + (ItemFilterHandler.NBT_TAG_MATCH_MODE_NOT_CONTAINS
                                                        .equalsIgnoreCase(waitInvNbtMode) ? "不包含" : "包含")
                                                + waitInvNbtCount + "项"
                                        : "")
                                + (waitInvSlotCount > 0 ? " / 指定槽位" + waitInvSlotCount + "个" : " / 全背包")
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_gui_title":
                        return "等待GUI标题: "
                                + (params.has("title") ? params.get("title").getAsString() : "")
                                + waitTimeoutSkipSuffix(params);
                    case "condition_scoreboard":
                        int condScoreboardSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-记分板: "
                                + (params.has("text") ? params.get("text").getAsString() : "")
                                + " / 失败跳过" + Math.max(0, condScoreboardSkip) + "个动作";
                    case "condition_packet_field":
                        int condPacketFieldSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        String condPacketFieldKey = params.has("fieldKey") ? params.get("fieldKey").getAsString() : "";
                        String condPacketFieldExpected = params.has("expectedValue")
                                ? params.get("expectedValue").getAsString()
                                : "";
                        return "条件分支-包字段: "
                                + (condPacketFieldKey.trim().isEmpty() ? "最近字段" : condPacketFieldKey)
                                + " / "
                                + (condPacketFieldExpected.trim().isEmpty() ? "存在" : condPacketFieldExpected)
                                + " / 失败跳过" + Math.max(0, condPacketFieldSkip) + "个动作";
                    case "wait_until_scoreboard":
                        return "等待记分板: "
                                + (params.has("text") ? params.get("text").getAsString() : "")
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_packet_field":
                        String waitPacketFieldKey = params.has("fieldKey") ? params.get("fieldKey").getAsString() : "";
                        String waitPacketFieldExpected = params.has("expectedValue")
                                ? params.get("expectedValue").getAsString()
                                : "";
                        int waitPacketFieldPreExecuteCount = params.has("preExecuteCount")
                                ? params.get("preExecuteCount").getAsInt()
                                : 0;
                        return "等待包字段: "
                                + (waitPacketFieldKey.trim().isEmpty() ? "最近字段" : waitPacketFieldKey)
                                + " / "
                                + (waitPacketFieldExpected.trim().isEmpty() ? "存在" : waitPacketFieldExpected)
                                + " / 先执行" + Math.max(0, waitPacketFieldPreExecuteCount) + "个动作"
                                + waitTimeoutSkipSuffix(params);
                    case "condition_packet_text":
                        int condPacketTextSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-数据包文本: "
                                + (params.has("packetText") ? params.get("packetText").getAsString() : "")
                                + " / 失败跳过" + Math.max(0, condPacketTextSkip) + "个动作";
                    case "condition_bossbar":
                        int condBossbarSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-Boss血条: "
                                + (params.has("text") ? params.get("text").getAsString() : "")
                                + " / 失败跳过" + Math.max(0, condBossbarSkip) + "个动作";
                    case "condition_gui_element":
                        int condGuiElementSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-GUI元素: "
                                + (params.has("locatorText") ? params.get("locatorText").getAsString() : "")
                                + " / 失败跳过" + Math.max(0, condGuiElementSkip) + "个动作";
                    case "condition_screen_region":
                        int condScreenSkip = params.has("skipCount") ? params.get("skipCount").getAsInt() : 1;
                        return "条件分支-屏幕区域: "
                                + (params.has("regionRect") ? params.get("regionRect").toString() : "[0,0,50,50]")
                                + " / 失败跳过" + Math.max(0, condScreenSkip) + "个动作";
                    case "wait_until_player_in_area":
                        return "等待进入区域: "
                                + (params.has("center") ? params.get("center").toString() : "[0,0,0]")
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_player_list":
                        return "等待玩家列表: "
                                + describePlayerListFilter(params)
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_gui_element":
                        return "等待GUI元素: "
                                + (params.has("locatorText") ? params.get("locatorText").getAsString() : "")
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_entity_nearby":
                        return "等待附近实体: "
                                + describeNearbyEntityFilter(params, true)
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_hud_text":
                        return "等待HUD文本: "
                                + (params.has("contains") ? params.get("contains").getAsString() : "")
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_expression":
                        return "等待表达式: "
                                + describeBooleanExpressionSummary(
                                        readBooleanExpressionList(params, "expressions", "expression"))
                                + waitTimeoutSkipSuffix(params);
                    case "wait_combined":
                        String combinedMode = params.has("combinedMode") ? params.get("combinedMode").getAsString()
                                : "ANY";
                        String conditionsText = params.has("conditionsText")
                                ? params.get("conditionsText").getAsString()
                                : "";
                        int conditionCount = countSeparatedValues(conditionsText);
                        String cancelExpression = params.has("cancelExpression")
                                ? params.get("cancelExpression").getAsString()
                                : "";
                        int combinedPreExecuteCount = params.has("preExecuteCount")
                                ? params.get("preExecuteCount").getAsInt()
                                : 0;
                        return "组合等待: "
                                + ("ALL".equalsIgnoreCase(combinedMode) ? "全部满足" : "任意满足")
                                + " / 条件" + conditionCount + "项"
                                + (!cancelExpression.trim().isEmpty() ? " / 取消条件" : "")
                                + " / 先执行" + Math.max(0, combinedPreExecuteCount) + "个动作"
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_captured_id":
                        String capturedIdName = params.has("capturedId") ? params.get("capturedId").getAsString() : "";
                        String waitMode = params.has("waitMode") ? params.get("waitMode").getAsString() : "update";
                        int preExecuteCount = params.has("preExecuteCount") ? params.get("preExecuteCount").getAsInt()
                                : 0;
                        return "等待数据包: "
                                + capturedIdName
                                + " / "
                                + ("recapture".equalsIgnoreCase(waitMode) ? "等待重新捕获" : "等待更新")
                                + " / 先执行" + Math.max(0, preExecuteCount) + "个动作"
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_packet_text":
                        String packetText = params.has("packetText") ? params.get("packetText").getAsString() : "";
                        int packetPreExecuteCount = params.has("preExecuteCount")
                                ? params.get("preExecuteCount").getAsInt()
                                : 0;
                        return "等待数据包文本: "
                                + packetText
                                + " / 先执行" + Math.max(0, packetPreExecuteCount) + "个动作"
                                + waitTimeoutSkipSuffix(params);
                    case "wait_until_screen_region":
                        String visionMode = params.has("visionCompareMode")
                                ? params.get("visionCompareMode").getAsString()
                                : "AVERAGE_COLOR";
                        return "等待屏幕区域: "
                                + (params.has("regionRect") ? params.get("regionRect").toString() : "[0,0,50,50]")
                                + " / "
                                + ("TEMPLATE".equalsIgnoreCase(visionMode)
                                        ? "模板 "
                                                + (params.has("imagePath") ? params.get("imagePath").getAsString() : "")
                                        : ("EDGE_DENSITY".equalsIgnoreCase(visionMode)
                                                ? "边缘阈值 "
                                                        + (params.has("edgeThreshold")
                                                                ? params.get("edgeThreshold").getAsString()
                                                                : "0.12")
                                                : "颜色 "
                                                        + (params.has("targetColor")
                                                                ? params.get("targetColor").getAsString()
                                                                : "#FFFFFF")))
                                + waitTimeoutSkipSuffix(params);
                    case "set_var":
                        return "设置变量: "
                                + (params.has("name") ? params.get("name").getAsString() : "")
                                + " = "
                                + (params.has("expression")
                                        ? (params.get("expression").isJsonPrimitive()
                                                ? params.get("expression").getAsString()
                                                : params.get("expression").toString())
                                        : (params.has("value") ? params.get("value").toString() : ""));
                    case "capture_nearby_entity":
                        return "捕获附近实体 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "entity")
                                + " / "
                                + describeNearbyEntityFilter(params, false)
                                + " / 半径"
                                + (params.has("radius") ? params.get("radius").getAsString() : "6");
                    case "capture_gui_title":
                        return "捕获GUI标题 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "gui_title");
                    case "capture_inventory_slot":
                        String captureSlotArea = params.has("slotArea") ? params.get("slotArea").getAsString() : "MAIN";
                        String captureSlotPrefix = "捕获背包槽位 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "slot")
                                + " / " + captureSlotArea;
                        if (params.has("slotIndices") && params.get("slotIndices").isJsonArray()
                                && params.getAsJsonArray("slotIndices").size() > 0) {
                            return captureSlotPrefix + "[多选 " + params.getAsJsonArray("slotIndices").size() + " 个]";
                        }
                        return captureSlotPrefix + "["
                                + (params.has("slotIndex") ? params.get("slotIndex").getAsString() : "0")
                                + "]";
                    case "capture_hotbar":
                        return "捕获快捷栏 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "hotbar");
                    case "capture_entity_list":
                        return "捕获实体列表 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "entities")
                                + " / "
                                + describeNearbyEntityFilter(params, false)
                                + " / 半径"
                                + (params.has("radius") ? params.get("radius").getAsString() : "8")
                                + " / 最多"
                                + (params.has("maxCount") ? params.get("maxCount").getAsString() : "16");
                    case "capture_packet_field":
                        return "捕获包字段 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "packet_field")
                                + " / "
                                + (params.has("lookupMode")
                                        && "VARIABLE".equalsIgnoreCase(params.get("lookupMode").getAsString())
                                                ? "变量 "
                                                        + (params.has("fieldKey")
                                                                ? params.get("fieldKey").getAsString()
                                                                : "")
                                                : "最近字段 "
                                                        + (params.has("fieldKey")
                                                                ? params.get("fieldKey").getAsString()
                                                                : "(最新)"));
                    case "capture_gui_element":
                        return "捕获GUI元素 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "gui_element")
                                + " / "
                                + (params.has("guiElementLocatorMode")
                                        && "PATH".equalsIgnoreCase(params.get("guiElementLocatorMode").getAsString())
                                                ? "按路径 "
                                                        + (params.has("locatorText")
                                                                ? params.get("locatorText").getAsString()
                                                                : "")
                                                : "按文本 "
                                                        + (params.has("locatorText")
                                                                ? params.get("locatorText").getAsString()
                                                                : ""));
                    case "capture_block_at":
                        return "捕获方块信息 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "block");
                    case "label":
                        return "标签: "
                                + (params.has("labelName") ? params.get("labelName").getAsString() : "");
                    case "capture_scoreboard":
                        return "捕获记分板 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "scoreboard")
                                + (params.has("lineIndex")
                                        ? " / 行" + params.get("lineIndex").getAsString()
                                        : "");
                    case "capture_screen_region":
                        return "捕获屏幕区域 -> "
                                + (params.has("varName") ? params.get("varName").getAsString() : "vision_region")
                                + " / "
                                + (params.has("regionRect") ? params.get("regionRect").toString() : "[0,0,50,50]");
                    case "goto_action":
                        return "跳转到动作序号: "
                                + (params.has("targetActionIndex") ? params.get("targetActionIndex").getAsString()
                                        : "0");
                    case "goto_label":
                        return "跳转到标签: "
                                + (params.has("targetLabel") ? params.get("targetLabel").getAsString() : "");
                    case "if_else":
                        return "条件块分支: 条件"
                                + countSeparatedValues(params.has("conditionsText")
                                        ? params.get("conditionsText").getAsString()
                                        : "")
                                + "项 / true块"
                                + (params.has("thenCount") ? params.get("thenCount").getAsString() : "1")
                                + " / false块"
                                + (params.has("elseCount") ? params.get("elseCount").getAsString() : "0");
                    case "switch_var":
                        return "变量分支表: "
                                + (params.has("sourceVar") ? params.get("sourceVar").getAsString() : "")
                                + " / 分支"
                                + countSeparatedValues(params.has("casesText")
                                        ? params.get("casesText").getAsString()
                                        : "")
                                + "项";
                    case "branch_table":
                        return "表达式分支表: 分支"
                                + countSeparatedValues(params.has("casesText")
                                        ? params.get("casesText").getAsString()
                                        : "")
                                + "项";
                    case "while_condition":
                        return "条件循环: 条件"
                                + countSeparatedValues(params.has("conditionsText")
                                        ? params.get("conditionsText").getAsString()
                                        : "")
                                + "项 / 循环体"
                                + (params.has("bodyCount") ? params.get("bodyCount").getAsString() : "1")
                                + " / 最大"
                                + (params.has("maxLoops") ? params.get("maxLoops").getAsString() : "0");
                    case "for_each_point":
                        return "遍历点列表: 点"
                                + countSeparatedValues(params.has("pointsText")
                                        ? params.get("pointsText").getAsString()
                                        : "")
                                + "个 / 循环体"
                                + (params.has("bodyCount") ? params.get("bodyCount").getAsString() : "1");
                    case "for_each_list":
                        return "遍历列表: "
                                + (params.has("sourceVar") ? params.get("sourceVar").getAsString() : "")
                                + " / 循环体"
                                + (params.has("bodyCount") ? params.get("bodyCount").getAsString() : "1");
                    case "retry_block":
                        return "重试动作块: 条件"
                                + countSeparatedValues(params.has("conditionsText")
                                        ? params.get("conditionsText").getAsString()
                                        : "")
                                + "项 / 块"
                                + (params.has("bodyCount") ? params.get("bodyCount").getAsString() : "1")
                                + " / 重试"
                                + (params.has("retryCount") ? params.get("retryCount").getAsString() : "3");
                    case "debug_print_var":
                        return "调试打印变量: "
                                + (params.has("varName") ? params.get("varName").getAsString() : "");
                    case "debug_print_nearby_entities":
                        return "调试打印附近实体: "
                                + describeNearbyEntityFilter(params, false)
                                + " / 半径"
                                + (params.has("radius") ? params.get("radius").getAsString() : "8");
                    case "debug_print_gui_summary":
                        return "调试打印当前GUI摘要";
                    case "skip_actions":
                        return "跳过后续动作: "
                                + (params.has("count") ? params.get("count").getAsString() : "1");
                    case "skip_steps":
                        return "跳过后续步骤: "
                                + (params.has("count") ? params.get("count").getAsString() : "1");
                    case "repeat_actions":
                        return "循环动作块: 次数="
                                + (params.has("count") ? params.get("count").getAsString() : "1")
                                + " / 块长度="
                                + (params.has("bodyCount") ? params.get("bodyCount").getAsString() : "1");
                    case "restart_sequence":
                        return I18n.format("path.action.desc.restart_sequence");
                    case "no_stop_navigation":
                        return I18n.format("path.action.desc.no_stop_navigation");
                    case "setview":
                        return I18n.format("path.action.desc.setview", params.get("yaw").getAsFloat(),
                                params.get("pitch").getAsFloat());
                    case "rightclickblock":
                        if (params.has("locatorMode")
                                && ActionTargetLocator.TARGET_MODE_NAME
                                        .equalsIgnoreCase(params.get("locatorMode").getAsString())) {
                            return "右键点击方块: 按名称 "
                                    + (params.has("locatorText") ? params.get("locatorText").getAsString() : "")
                                    + getPreserveViewDescriptionSuffix(params);
                        }
                        return I18n.format("path.action.desc.right_click_block", params.get("pos").toString())
                                + getPreserveViewDescriptionSuffix(params);
                    case "rightclickentity":
                        if (params.has("locatorMode")
                                && ActionTargetLocator.TARGET_MODE_NAME
                                        .equalsIgnoreCase(params.get("locatorMode").getAsString())) {
                            return "右键点击实体: 按名称 "
                                    + (params.has("locatorText") ? params.get("locatorText").getAsString() : "")
                                    + getPreserveViewDescriptionSuffix(params);
                        }
                        return I18n.format("path.action.desc.right_click_entity", params.get("pos").toString())
                                + getPreserveViewDescriptionSuffix(params);
                    case "takeallitems":
                        return I18n.format("path.action.desc.take_all_items");
                    case "dropfiltereditems":
                        return I18n.format("path.action.desc.drop_filtered_items");
                    case "autochestclick":
                        int chestDelayTicks = params.has("delayTicks")
                                ? Math.max(0, params.get("delayTicks").getAsInt())
                                : 1;
                        String chestClickType = params.has("clickType") ? params.get("clickType").getAsString()
                                : "PICKUP";
                        int chestButton = params.has("button") ? params.get("button").getAsInt() : 0;
                        if (params.has("locatorMode")
                                && !ActionTargetLocator.SLOT_MODE_DIRECT
                                        .equalsIgnoreCase(params.get("locatorMode").getAsString())) {
                            return "点击容器槽位: "
                                    + ("EMPTY_SLOT".equalsIgnoreCase(params.get("locatorMode").getAsString())
                                            ? "第一个空槽位"
                                            : (ActionTargetLocator.SLOT_MODE_PATH
                                                    .equalsIgnoreCase(params.get("locatorMode").getAsString())
                                                            ? "按路径 " + (params.has("locatorText")
                                                                    ? params.get("locatorText").getAsString()
                                                                    : "")
                                                            : "按物品文本 " + (params.has("locatorText")
                                                                    ? params.get("locatorText").getAsString()
                                                                    : "")))
                                    + " / 按键: " + (chestButton == 1 ? "右键" : "左键")
                                    + " / 类型: " + ModUtils.clickTypeToDisplayName(chestClickType)
                                    + " / 延迟: " + chestDelayTicks + " tick";
                        }
                        return "点击容器槽位: " + params.get("slot").getAsInt()
                                + " / 按键: " + (chestButton == 1 ? "右键" : "左键")
                                + " / 类型: " + ModUtils.clickTypeToDisplayName(chestClickType)
                                + " / 延迟: " + chestDelayTicks + " tick";
                    case "move_inventory_items_to_chest_slots":
                        int chestSlotCount = countIntListParam(params, "chestSlots", "chestSlotsText");
                        int inventorySlotCount = countIntListParam(params, "inventorySlots", "inventorySlotsText");
                        List<String> moveChestExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
                        List<ItemFilterHandler.MoveChestFilterRule> moveChestRules = ItemFilterHandler
                                .readMoveChestFilterRules(params);
                        String nbtTagMode = params.has("requiredNbtTagsMode")
                                ? params.get("requiredNbtTagsMode").getAsString()
                                : "CONTAINS";
                        int moveChestDelayTicks = params.has("delayTicks")
                                ? Math.max(0, params.get("delayTicks").getAsInt())
                                : 2;
                        String moveDirection = ItemFilterHandler.readMoveChestDirection(params);
                        String moveClickType = ItemFilterHandler.readMoveChestClickType(params);
                        int moveClickButton = ItemFilterHandler.readMoveChestClickButton(params);
                        boolean chestToInventory = ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY
                                .equalsIgnoreCase(moveDirection);
                        String sourceLabel = chestToInventory ? "容器" : "背包";
                        int sourceSlotCount = chestToInventory ? chestSlotCount : inventorySlotCount;
                        String targetLabel = chestToInventory ? "背包" : "容器";
                        int targetSlotCount = chestToInventory ? inventorySlotCount : chestSlotCount;
                        StringBuilder moveChestFilter = new StringBuilder();
                        if (!moveChestExpressions.isEmpty()) {
                            moveChestFilter.append("表达式")
                                    .append(moveChestExpressions.size())
                                    .append("条");
                            String summary = InventoryItemFilterExpressionEngine
                                    .summarizeExpressions(moveChestExpressions);
                            if (!summary.isEmpty()) {
                                moveChestFilter.append(" (首条: ").append(summary).append(")");
                            }
                        } else if (moveChestRules.isEmpty()) {
                            moveChestFilter.append("未设置");
                        } else {
                            moveChestFilter.append("规则").append(moveChestRules.size()).append("条");
                            ItemFilterHandler.MoveChestFilterRule firstRule = moveChestRules.get(0);
                            String firstItem = firstRule.getItemName();
                            int firstNbtCount = firstRule.getRequiredNbtTags() == null ? 0
                                    : firstRule.getRequiredNbtTags().size();
                            if ((firstItem != null && !firstItem.trim().isEmpty()) || firstNbtCount > 0) {
                                moveChestFilter.append(" (首条: ");
                                if (firstItem != null && !firstItem.trim().isEmpty()) {
                                    moveChestFilter.append("物品=").append(firstItem.trim());
                                }
                                if (firstNbtCount > 0) {
                                    if (firstItem != null && !firstItem.trim().isEmpty()) {
                                        moveChestFilter.append(", ");
                                    }
                                    moveChestFilter.append("NBT")
                                            .append("NOT_CONTAINS".equalsIgnoreCase(nbtTagMode) ? "不包含" : "包含")
                                            .append(firstNbtCount)
                                            .append("项");
                                }
                                moveChestFilter.append(")");
                            }
                        }
                        return "容器物品批量移动: "
                                + sourceLabel
                                + sourceSlotCount
                                + "格 -> "
                                + targetLabel
                                + targetSlotCount
                                + "格 / 按键"
                                + moveClickButton
                                + " / 类型: "
                                + ModUtils.clickTypeToDisplayName(moveClickType)
                                + " / 过滤: "
                                + moveChestFilter
                                + " / 延迟"
                                + moveChestDelayTicks
                                + " tick"
                                + getDelayNormalizationDescriptionSuffix(params);
                    case "spread_inventory_item":
                        List<String> spreadExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
                        String spreadSummary = spreadExpressions.isEmpty()
                                ? (params.has("itemName") ? params.get("itemName").getAsString() : "未设置")
                                : InventoryItemFilterExpressionEngine.summarizeExpressions(spreadExpressions);
                        int spreadTargetSlots = countIntListParam(params, "targetSlots", "targetSlotsText");
                        String spreadMode = params.has("spreadMode")
                                ? params.get("spreadMode").getAsString()
                                : ItemSpreadHandler.MODE_ONE_PER_SLOT;
                        String spreadModeText = ItemSpreadHandler.MODE_EVEN_SPLIT.equalsIgnoreCase(spreadMode)
                                ? "平均分配"
                                : (ItemSpreadHandler.MODE_FIXED_PER_SLOT.equalsIgnoreCase(spreadMode)
                                        ? "每槽指定数量"
                                        : "每槽1个");
                        return "平摊物品到背包: "
                                + spreadSummary
                                + " / "
                                + spreadModeText
                                + " / 目标"
                                + (spreadTargetSlots > 0 ? spreadTargetSlots + "格" : "按范围")
                                + " / 延迟"
                                + (params.has("delayTicks") ? Math.max(0, params.get("delayTicks").getAsInt()) : 1)
                                + " tick";
                    case "stack_inventory_item":
                        List<String> stackExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
                        String stackSummary = stackExpressions.isEmpty()
                                ? (params.has("itemName") ? params.get("itemName").getAsString() : "未设置")
                                : InventoryItemFilterExpressionEngine.summarizeExpressions(stackExpressions);
                        int stackTargetSlots = countIntListParam(params, "targetSlots", "targetSlotsText");
                        return "叠加物品: "
                                + stackSummary
                                + " / 目标"
                                + (stackTargetSlots > 0 ? stackTargetSlots + "格" : "按范围")
                                + " / 延迟"
                                + (params.has("delayTicks") ? Math.max(0, params.get("delayTicks").getAsInt()) : 1)
                                + " tick";
                    case "pickup_nearby_items":
                        List<String> pickupExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
                        String pickupSummary = NearbyItemPickupActionHandler.usesKillAuraPickupRules(params)
                                ? "沿用杀戮光环规则"
                                : pickupExpressions.isEmpty()
                                        ? "未设置"
                                        : InventoryItemFilterExpressionEngine.summarizeExpressions(pickupExpressions);
                        return "拾取附近掉落物: "
                                + pickupSummary
                                + " / 半径"
                                + (params.has("searchRadius") ? params.get("searchRadius").getAsDouble() : 16.0D)
                                + " / 最多"
                                + (params.has("maxItems") && params.get("maxItems").getAsInt() > 0
                                        ? params.get("maxItems").getAsInt() + "个"
                                        : "不限");
                    case "warehouse_auto_deposit":
                        return I18n.format("path.action.desc.warehouse_auto_deposit");
                    case "blocknextgui":
                        int count = params.has("count") ? params.get("count").getAsInt() : 1;
                        boolean blockCurrentGui = params.has("blockCurrentGui")
                                && params.get("blockCurrentGui").getAsBoolean();
                        return I18n.format("path.action.desc.block_next_gui", Math.max(1, count),
                                blockCurrentGui ? I18n.format("path.common.yes") : I18n.format("path.common.no"));
                    case "hidecurrentgui":
                        return I18n.format("path.action.desc.hide_current_gui");
                    case "showhiddengui":
                        return I18n.format("path.action.desc.show_hidden_gui");
                    case "close_container_window":
                        return I18n.format("path.action.desc.close_container_window");
                    case "autoeat":
                        return (params.has("enabled") && !params.get("enabled").getAsBoolean())
                                ? "关闭自动吃食物"
                                : "启用自动吃食物";
                    case "autoequip":
                        return (params.has("enabled") && !params.get("enabled").getAsBoolean())
                                ? "关闭自动装备"
                                : "启用自动装备";
                    case "autopickup":
                        return (params.has("enabled") && !params.get("enabled").getAsBoolean())
                                ? "关闭自动拾取"
                                : "启用自动拾取";
                    case "toggle_autoeat":
                        return "自动进食: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_autofishing":
                        return "自动钓鱼: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_auto_pickup":
                        return "自动拾取掉落物: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_kill_aura":
                        return "杀戮光环: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_auto_follow":
                        return "自动追怪: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_fly":
                        return "飞行: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_conditional_execution":
                        return "条件执行: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_auto_escape":
                        return "自动逃离: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_baritone_free_look":
                        return "Baritone自由视角: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_baritone_human_like":
                        return "Baritone模拟真人: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_baritone_flight":
                        return "Baritone飞行寻路: "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "toggle_other_feature":
                        String otherFeatureName = getOtherFeatureDisplayName(
                                params.has("featureId") ? params.get("featureId").getAsString() : "",
                                params.has("featureName") ? params.get("featureName").getAsString() : "");
                        return "其他功能: "
                                + (otherFeatureName.isEmpty() ? "未选择功能" : otherFeatureName)
                                + " / "
                                + ((params.has("enabled") && !params.get("enabled").getAsBoolean())
                                        ? I18n.format("path.common.off")
                                        : I18n.format("path.common.on"));
                    case "hunt":
                        double radius = params.has("radius") ? params.get("radius").getAsDouble() : 3.0;
                        double huntUpRange = params.has("huntUpRange") ? params.get("huntUpRange").getAsDouble()
                                : 3.0D;
                        double huntDownRange = params.has("huntDownRange") ? params.get("huntDownRange").getAsDouble()
                                : 3.0D;
                        String huntEntityType = params.has("entityType") ? params.get("entityType").getAsString() : "";
                        int noTargetSkipCount = params.has("noTargetSkipCount")
                                ? Math.max(0, params.get("noTargetSkipCount").getAsInt())
                                : 0;
                        boolean enableWhitelist = params.has("enableNameWhitelist")
                                && params.get("enableNameWhitelist").getAsBoolean();
                        boolean waitForWhitelist = enableWhitelist && params.has("waitForWhitelistRespawn")
                                && params.get("waitForWhitelistRespawn").getAsBoolean();
                        boolean waitAfterCompletion = enableWhitelist
                                && params.has("waitForWhitelistRespawnAfterCompletion")
                                && params.get("waitForWhitelistRespawnAfterCompletion").getAsBoolean();
                        boolean confirmKillCompletion = enableWhitelist
                                && params.has("confirmKillCompletion")
                                && params.get("confirmKillCompletion").getAsBoolean();
                        int killCompletionConfirmSeconds = params.has("killCompletionConfirmSeconds")
                                ? Math.max(0, params.get("killCompletionConfirmSeconds").getAsInt()) : 3;
                        boolean forceEndHunt = params.has("forceEndHunt")
                                && params.get("forceEndHunt").getAsBoolean();
                        boolean enableBlacklist = params.has("enableNameBlacklist")
                                && params.get("enableNameBlacklist").getAsBoolean();
                        int whitelistCount = countHuntWhitelistParam(params);
                        int blacklistCount = countStringListParam(params, "nameBlacklist", "nameBlacklistText");
                        if (blacklistCount <= 0) {
                            blacklistCount = countStringListParam(params, "nameBlacklistEntries", "nameBlacklistText");
                        }
                        boolean showRange = params.has("showHuntRange") && params.get("showHuntRange").getAsBoolean();
                        StringBuilder huntDesc = new StringBuilder("中心搜怪: 半径 ")
                                .append(String.format(Locale.ROOT, "%.1f", Math.max(0.0D, radius)))
                                .append("，战斗细节沿用杀戮光环");
                        huntDesc.append(", 垂直 +")
                                .append(String.format(Locale.ROOT, "%.1f", Math.max(0.0D, huntUpRange)))
                                .append("/-")
                                .append(String.format(Locale.ROOT, "%.1f", Math.max(0.0D, huntDownRange)));
                        if (huntEntityType == null || huntEntityType.trim().isEmpty()) {
                            huntDesc.append(", 实体类型沿用杀戮光环");
                        } else {
                            huntDesc.append(", 实体类型 ").append(describeEntityTypeDisplay(huntEntityType));
                        }
                        if (noTargetSkipCount > 0) {
                            huntDesc.append(", 无目标跳过 ").append(noTargetSkipCount).append("个");
                        }
                        if (enableWhitelist) {
                            huntDesc.append(", 白名单").append(whitelistCount).append("项");
                            if (waitForWhitelist) {
                                huntDesc.append(", 无目标等待复活");
                            }
                            if (waitAfterCompletion) {
                                huntDesc.append(", 击杀后等待复活");
                            }
                            if (confirmKillCompletion) {
                                huntDesc.append(", 击杀确认 ").append(killCompletionConfirmSeconds).append("秒");
                            }
                        }
                        if (forceEndHunt) {
                            int forceEndSeconds = params.has("forceEndHuntTimeoutSeconds")
                                    ? Math.max(0, params.get("forceEndHuntTimeoutSeconds").getAsInt()) : 60;
                            huntDesc.append(", 强制结束 ").append(forceEndSeconds).append("秒");
                        }
                        if (enableBlacklist) {
                            huntDesc.append(", 黑名单").append(blacklistCount).append("项");
                        }
                        if (showRange) {
                            huntDesc.append(", 显示范围");
                        }
                        return huntDesc.toString();
                    case "follow_entity":
                        String entityType = params.has("entityType") ? params.get("entityType").getAsString()
                                : "player";
                        String targetName = params.has("targetName") ? params.get("targetName").getAsString().trim()
                                : "";
                        double searchRadius = params.has("searchRadius") ? params.get("searchRadius").getAsDouble()
                                : 16.0;
                        double followDist = params.has("followDistance") ? params.get("followDistance").getAsDouble()
                                : 3.0;
                        int timeout = params.has("timeout") ? params.get("timeout").getAsInt() : 0;

                        String entityTypeDisplay = describeEntityTypeDisplay(entityType);
                        if (entityTypeDisplay.isEmpty()) {
                            entityTypeDisplay = "玩家";
                        }

                        StringBuilder followDesc = new StringBuilder("跟随");
                        followDesc.append(entityTypeDisplay);
                        if (!targetName.isEmpty()) {
                            followDesc.append(" [").append(targetName).append("]");
                        }
                        followDesc.append(", 距离 ").append(followDist).append("格");
                        if (timeout > 0) {
                            followDesc.append(", 超时 ").append(timeout).append("秒");
                        }
                        return followDesc.toString();
                    case "use_hotbar_item":
                        String itemName = getStringParam(params, "itemName", "");
                        String matchMode = getStringParam(params, "matchMode", "CONTAINS");
                        String useMode = getStringParam(params, "useMode", "RIGHT_CLICK");
                        boolean changeLocalSlot = readBooleanParam(params, "changeLocalSlot", false);
                        int useCount = getIntParam(params, "count", 1);
                        int useInterval = getIntParam(params, "intervalTicks", 0);
                        int switchItemDelay = Math.max(0, getIntParam(params, "switchItemDelayTicks", 0));
                        int switchDelay = Math.max(0, getIntParam(params, "switchDelayTicks", 0));
                        int switchBackDelay = Math.max(0, getIntParam(params, "switchBackDelayTicks", 0));
                        String matchText = "EXACT".equalsIgnoreCase(matchMode)
                                ? I18n.format("gui.autouseitem.match.exact")
                                : I18n.format("gui.autouseitem.match.contains");
                        String useText = "LEFT_CLICK".equalsIgnoreCase(useMode)
                                ? I18n.format("gui.autouseitem.mode.left")
                                : I18n.format("gui.autouseitem.mode.right");
                        String localSlotText = changeLocalSlot
                                ? I18n.format("path.common.on")
                                : I18n.format("path.common.off");
                        return I18n.format("path.action.desc.use_hotbar_item", itemName, matchText, useText,
                                localSlotText, Math.max(1, useCount), Math.max(0, useInterval),
                                formatTickDelayText(switchItemDelay), formatTickDelayText(switchDelay),
                                formatTickDelayText(switchBackDelay));
                    case "move_inventory_item_to_hotbar":
                        String inventoryItemName = params.has("itemName") ? params.get("itemName").getAsString() : "";
                        String inventoryMatchMode = params.has("matchMode")
                                ? params.get("matchMode").getAsString()
                                : "CONTAINS";
                        int targetHotbarSlot = params.has("targetHotbarSlot")
                                ? params.get("targetHotbarSlot").getAsInt()
                                : 1;
                        int inventoryMoveCount = params.has("count")
                                ? Math.max(0, params.get("count").getAsInt())
                                : 0;
                        boolean skipIfTargetOccupied = !params.has("skipIfTargetOccupied")
                                || params.get("skipIfTargetOccupied").getAsBoolean();
                        String inventoryMatchText = "EXACT".equalsIgnoreCase(inventoryMatchMode)
                                ? I18n.format("gui.autouseitem.match.exact")
                                : I18n.format("gui.autouseitem.match.contains");
                        return I18n.format("path.action.desc.move_inventory_item_to_hotbar",
                                inventoryItemName,
                                inventoryMatchText,
                                Math.max(1, Math.min(9, targetHotbarSlot)),
                                inventoryMoveCount <= 0 ? I18n.format("path.common.all") : inventoryMoveCount,
                                skipIfTargetOccupied ? I18n.format("path.common.on") : I18n.format("path.common.off"));
                    case "silentuse":
                        String silentItemName = params.has("item") ? params.get("item").getAsString() : "";
                        int silentTempSlot = params.has("tempslot")
                                ? Math.max(0, Math.min(8, params.get("tempslot").getAsInt()))
                                : 0;
                        return I18n.format("path.action.desc.silentuse",
                                silentItemName,
                                silentTempSlot,
                                formatOptionalTickDelay(params, "switchDelayTicks", "默认"),
                                formatOptionalTickDelay(params, "useDelayTicks", "默认"),
                                formatOptionalTickDelay(params, "switchBackDelayTicks", "自动"));
                    case "switch_hotbar_slot":
                        int switchHotbarSlot = params.has("targetHotbarSlot")
                                ? params.get("targetHotbarSlot").getAsInt()
                                : 1;
                        boolean useAfterSwitch = params.has("useAfterSwitch")
                                && params.get("useAfterSwitch").getAsBoolean();
                        int useAfterSwitchDelayTicks = params.has("useAfterSwitchDelayTicks")
                                ? Math.max(0, params.get("useAfterSwitchDelayTicks").getAsInt())
                                : 0;
                        return I18n.format("path.action.desc.switch_hotbar_slot",
                                Math.max(1, Math.min(9, switchHotbarSlot)),
                                useAfterSwitch ? I18n.format("path.common.on") : I18n.format("path.common.off"),
                                formatTickDelayText(useAfterSwitchDelayTicks));
                    case "use_held_item":
                        int useHeldItemDelayTicks = params.has("delayTicks")
                                ? Math.max(0, params.get("delayTicks").getAsInt())
                                : 0;
                        return I18n.format("path.action.desc.use_held_item",
                                formatTickDelayText(useHeldItemDelayTicks));
                    case "run_sequence":
                        String sequenceName = params.has("sequenceName") ? params.get("sequenceName").getAsString()
                                : I18n.format("msg.common.unknown");
                        return I18n.format("path.action.desc.run_sequence", sequenceName)
                                + getRunSequenceActionDescriptionSuffix(params);
                    case "stop_current_sequence":
                        return I18n.format("path.action.desc.stop_current_sequence",
                                getStopCurrentSequenceScopeText(params));
                    case "sequence_control":
                        return I18n.format("path.action.desc.sequence_control",
                                getSequenceControlScopeText(params),
                                getSequenceControlOperationText(params));
                    case "run_template":
                        String templateName = params.has("templateName") ? params.get("templateName").getAsString()
                                : I18n.format("msg.common.unknown");
                        String templateTarget = LegacyActionTemplateManager.resolveTemplateTargetSequence(templateName);
                        return "执行模板: " + templateName
                                + (templateTarget == null || templateTarget.trim().isEmpty() ? ""
                                        : " -> " + templateTarget)
                                + getRunSequenceActionDescriptionSuffix(params);
                    // --- 核心修改：更新 send_packet 的描述 ---
                    case "send_packet":
                        String direction = params.has("direction") ? params.get("direction").getAsString() : "C2S";
                        String directionText = "S2C".equalsIgnoreCase(direction)
                                ? I18n.format("path.action.desc.send_packet.direction.s2c")
                                : I18n.format("path.action.desc.send_packet.direction.c2s");
                        if (params.has("channel") && !params.get("channel").getAsString().isEmpty()) {
                            return I18n.format("path.action.desc.send_packet.channel",
                                    params.get("channel").getAsString(), directionText);
                        } else if (params.has("packetId")) {
                            return I18n.format("path.action.desc.send_packet.id", params.get("packetId").toString(),
                                    directionText);
                        } else {
                            return I18n.format("path.action.desc.send_packet.unconfigured");
                        }
                        // --- 修改结束 ---
                    default:
                        return I18n.format("path.action.desc.unknown_action", type);
                }
            } catch (Exception e) {
                return I18n.format("path.action.desc.error_action", type);
            }
        }
    }

    private static String waitTimeoutSkipSuffix(JsonObject params) {
        int timeoutSkipCount = params.has("timeoutSkipCount") ? params.get("timeoutSkipCount").getAsInt() : 0;
        return " / 超时跳过" + Math.max(0, timeoutSkipCount) + "个动作";
    }

    private static int countSeparatedValues(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String part : text.split("\\r?\\n|;|；")) {
            if (part != null && !part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static List<String> readBooleanExpressionList(JsonObject params, String arrayKey, String legacyKey) {
        List<String> expressions = new ArrayList<>();
        if (params == null) {
            return expressions;
        }
        if (params.has(arrayKey) && params.get(arrayKey).isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray(arrayKey)) {
                if (element != null && element.isJsonPrimitive()) {
                    String value = element.getAsString();
                    if (value != null && !value.trim().isEmpty()) {
                        expressions.add(value.trim());
                    }
                }
            }
            if (!expressions.isEmpty()) {
                return expressions;
            }
        }
        if (params.has(legacyKey) && params.get(legacyKey).isJsonPrimitive()) {
            String value = params.get(legacyKey).getAsString();
            if (value != null && !value.trim().isEmpty()) {
                expressions.add(value.trim());
            }
        }
        return expressions;
    }

    private static String describeBooleanExpressionSummary(List<String> expressions) {
        if (expressions == null || expressions.isEmpty()) {
            return "(未填写)";
        }
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        return "全部满足 / 条件" + expressions.size() + "项";
    }

    private static int countIntListParam(JsonObject params, String arrayKey, String textKey) {
        if (params == null) {
            return 0;
        }
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        if (params.has(arrayKey) && params.get(arrayKey).isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray(arrayKey)) {
                try {
                    values.add(element.getAsInt());
                } catch (Exception ignored) {
                }
            }
        } else if (params.has(arrayKey)) {
            values.addAll(parseIntegerListText(params.get(arrayKey).getAsString()));
        }
        if (values.isEmpty() && params.has(textKey)) {
            values.addAll(parseIntegerListText(params.get(textKey).getAsString()));
        }
        return values.size();
    }

    private static int countHuntWhitelistParam(JsonObject params) {
        if (params == null) {
            return 0;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (params.has("nameWhitelistEntries") && params.get("nameWhitelistEntries").isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray("nameWhitelistEntries")) {
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                String value = "";
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    if (object.has("name") && object.get("name").isJsonPrimitive()) {
                        value = object.get("name").getAsString();
                    } else if (object.has("keyword") && object.get("keyword").isJsonPrimitive()) {
                        value = object.get("keyword").getAsString();
                    } else if (object.has("target") && object.get("target").isJsonPrimitive()) {
                        value = object.get("target").getAsString();
                    }
                } else if (element.isJsonPrimitive()) {
                    value = element.getAsString();
                }
                value = value == null ? "" : value.trim();
                if (!value.isEmpty()) {
                    values.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!values.isEmpty()) {
            return values.size();
        }
        return countStringListParam(params, "nameWhitelist", "nameWhitelistText");
    }

    private static int countStringListParam(JsonObject params, String arrayKey, String textKey) {
        if (params == null) {
            return 0;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (params.has(arrayKey) && params.get(arrayKey).isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray(arrayKey)) {
                if (element != null && element.isJsonPrimitive()) {
                    String value = element.getAsString().trim();
                    if (!value.isEmpty()) {
                        values.add(value.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } else if (params.has(arrayKey)) {
            values.addAll(parseStringListText(params.get(arrayKey).getAsString()));
        }
        if (values.isEmpty() && params.has(textKey)) {
            values.addAll(parseStringListText(params.get(textKey).getAsString()));
        }
        return values.size();
    }

    private static List<Integer> parseIntegerListText(String text) {
        List<Integer> values = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return values;
        }
        for (String token : text.split("[,\\r\\n\\s]+")) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(token.trim()));
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private static List<String> parseStringListText(String text) {
        List<String> values = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return values;
        }
        for (String token : text.split("\\r?\\n|,")) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            values.add(token.trim().toLowerCase(Locale.ROOT));
        }
        return values;
    }

    private static boolean supportsPersistentActionUuid(String type) {
        return "window_click".equalsIgnoreCase(type)
                || "run_sequence".equalsIgnoreCase(type)
                || "run_template".equalsIgnoreCase(type);
    }

    private static String ensurePersistentActionUuid(JsonObject params, String type) {
        if (params == null || !supportsPersistentActionUuid(type)) {
            return "";
        }
        String uuidValue = params.has("uuid") ? params.get("uuid").getAsString() : null;
        if (uuidValue == null || uuidValue.trim().isEmpty()) {
            uuidValue = java.util.UUID.randomUUID().toString();
            params.addProperty("uuid", uuidValue);
        }
        return uuidValue;
    }

    private static boolean isRunSequenceExecuteAlways(JsonObject params) {
        String mode = params != null && params.has("executeMode") ? params.get("executeMode").getAsString() : "always";
        return !"interval".equalsIgnoreCase(mode) || getRunSequenceExecuteEveryCount(params) <= 1;
    }

    private static boolean isDelayNormalizedTo20Tps(JsonObject params) {
        return params == null
                || !params.has("normalizeDelayTo20Tps")
                || !params.get("normalizeDelayTo20Tps").isJsonPrimitive()
                || params.get("normalizeDelayTo20Tps").getAsBoolean();
    }

    private static String getDelayNormalizationDescriptionSuffix(JsonObject params) {
        return isDelayNormalizedTo20Tps(params) ? " / 20TPS基准" : "";
    }

    private static boolean isPreserveViewEnabled(JsonObject params) {
        return readBooleanParam(params, "preserveView", readBooleanParam(params, "noMoveView", false));
    }

    private static boolean readBooleanParam(JsonObject params, String key, boolean defaultValue) {
        if (params == null || key == null || !params.has(key) || !params.get(key).isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String getPreserveViewDescriptionSuffix(JsonObject params) {
        return isPreserveViewEnabled(params) ? " / 不移动视角" : "";
    }

    private static boolean isRunSequenceBackgroundExecution(JsonObject params) {
        return params != null
                && params.has("backgroundExecution")
                && params.get("backgroundExecution").isJsonPrimitive()
                && params.get("backgroundExecution").getAsBoolean();
    }

    private static int getRunSequenceExecuteEveryCount(JsonObject params) {
        if (params == null || !params.has("executeEveryCount")) {
            return 1;
        }
        try {
            return Math.max(1, params.get("executeEveryCount").getAsInt());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static int getRunSequenceCurrentCount(String uuid, int executeEveryCount) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return 0;
        }
        ensureRunSequenceActionStateLoaded();
        Integer stored = runSequenceActionCountByUuid.get(uuid.trim());
        if (stored == null) {
            return 0;
        }
        int maxCount = Math.max(0, executeEveryCount - 1);
        return Math.max(0, Math.min(stored.intValue(), maxCount));
    }

    private static void setRunSequenceCurrentCount(String uuid, int count) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return;
        }
        ensureRunSequenceActionStateLoaded();
        String key = uuid.trim();
        if (count <= 0) {
            if (runSequenceActionCountByUuid.remove(key) != null) {
                saveRunSequenceActionState();
            }
            return;
        }
        Integer previous = runSequenceActionCountByUuid.put(key, count);
        if (previous == null || previous.intValue() != count) {
            saveRunSequenceActionState();
        }
    }

    private static String getRunSequenceActionDescriptionSuffix(JsonObject params) {
        String executionTarget = isRunSequenceBackgroundExecution(params) ? " / 后台执行" : " / 前台执行";
        if (isRunSequenceExecuteAlways(params)) {
            return executionTarget + " / 每次执行";
        }
        int executeEveryCount = getRunSequenceExecuteEveryCount(params);
        String uuid = params != null ? ensurePersistentActionUuid(params, "run_sequence") : "";
        int currentCount = getRunSequenceCurrentCount(uuid, executeEveryCount);
        return executionTarget + " / 每" + executeEveryCount + "次执行1次 / 当前第" + currentCount + "次";
    }

    public static String getRunSequenceActionStatusText(JsonObject params) {
        String executionText = isRunSequenceBackgroundExecution(params) ? "在后台执行目标序列一次" : "执行目标序列并等待其结束";
        if (isRunSequenceExecuteAlways(params)) {
            return "每次到达此动作都会" + executionText;
        }
        int executeEveryCount = getRunSequenceExecuteEveryCount(params);
        String uuid = params != null ? ensurePersistentActionUuid(params, "run_sequence") : "";
        int currentCount = getRunSequenceCurrentCount(uuid, executeEveryCount);
        return "第" + currentCount + "次（将跳过，在第" + executeEveryCount + "次" + executionText + "）";
    }

    private static String getStopCurrentSequenceTargetScope(JsonObject params) {
        if (params == null || !params.has("targetScope")) {
            return "foreground";
        }
        try {
            String scope = params.get("targetScope").getAsString();
            return "background".equalsIgnoreCase(scope) ? "background" : "foreground";
        } catch (Exception ignored) {
            return "foreground";
        }
    }

    private static String getStopCurrentSequenceScopeText(JsonObject params) {
        return "background".equalsIgnoreCase(getStopCurrentSequenceTargetScope(params))
                ? I18n.format("gui.path.action_editor.option.stop_sequence_scope_background")
                : I18n.format("gui.path.action_editor.option.stop_sequence_scope_foreground");
    }

    private static String getSequenceControlTargetScope(JsonObject params) {
        return getStopCurrentSequenceTargetScope(params);
    }

    private static String getSequenceControlOperation(JsonObject params) {
        if (params == null || !params.has("operation")) {
            return "pause";
        }
        try {
            String operation = params.get("operation").getAsString();
            return "resume".equalsIgnoreCase(operation) ? "resume" : "pause";
        } catch (Exception ignored) {
            return "pause";
        }
    }

    private static String getSequenceControlScopeText(JsonObject params) {
        return "background".equalsIgnoreCase(getSequenceControlTargetScope(params))
                ? I18n.format("gui.path.action_editor.option.stop_sequence_scope_background")
                : I18n.format("gui.path.action_editor.option.stop_sequence_scope_foreground");
    }

    private static String getSequenceControlOperationText(JsonObject params) {
        return "resume".equalsIgnoreCase(getSequenceControlOperation(params))
                ? I18n.format("gui.path.action_editor.option.sequence_control_operation.resume")
                : I18n.format("gui.path.action_editor.option.sequence_control_operation.pause");
    }

    private static String formatTickDelayText(int delayTicks) {
        return Math.max(0, delayTicks) + " ticks";
    }

    private static String formatTickDelaySpec(JsonObject params, String key, int fallback) {
        if (params == null || key == null || !params.has(key)) {
            return String.valueOf(Math.max(0, fallback));
        }
        return TickRangeSpec.normalize(params.get(key), fallback, MIN_DELAY_TICKS, MAX_DELAY_TICKS);
    }

    private static String formatOptionalTickDelay(JsonObject params, String key, String defaultText) {
        if (params == null || key == null || !params.has(key)) {
            return defaultText;
        }
        return formatTickDelayText(params.get(key).getAsInt());
    }

    // ... (PathStep 和 PathSequence 类保持不变) ...
    public static class PathStep {
        private double[] gotoPoint;
        private final List<ActionData> actions = new ArrayList<>();
        private String note = "";
        private int retryCount = getDefaultStepRetryCount();
        private int pathRetryTimeoutSeconds = getDefaultStepPathRetryTimeoutSeconds();
        private int arrivalToleranceBlocks = getDefaultStepArrivalToleranceBlocks();
        private String retryExhaustedPolicy = "END_SEQUENCE";
        private String retryExhaustedSequenceName = "";
        private int retryExhaustedStepIndex;
        private int retryExhaustedActionIndex;

        public PathStep(double[] gotoPoint) {
            this.gotoPoint = gotoPoint;
        }

        public PathStep(PathStep other) {
            this(other, false);
        }

        public PathStep(PathStep other, boolean preservePersistentUuids) {
            this.gotoPoint = Arrays.copyOf(other.gotoPoint, other.gotoPoint.length);
            this.note = other.note;
            this.retryCount = other.retryCount;
            this.pathRetryTimeoutSeconds = other.pathRetryTimeoutSeconds;
            this.arrivalToleranceBlocks = other.arrivalToleranceBlocks;
            this.retryExhaustedPolicy = other.retryExhaustedPolicy;
            this.retryExhaustedSequenceName = other.retryExhaustedSequenceName;
            this.retryExhaustedStepIndex = other.retryExhaustedStepIndex;
            this.retryExhaustedActionIndex = other.retryExhaustedActionIndex;
            for (ActionData action : other.actions) {
                this.actions.add(new ActionData(action, preservePersistentUuids));
            }
        }

        public void addAction(ActionData action) {
            actions.add(action);
        }

        public double[] getGotoPoint() {
            return gotoPoint;
        }

        public void setGotoPoint(double[] point) {
            this.gotoPoint = point;
        }

        public List<ActionData> getActions() {
            return actions;
        }

        public String getNote() {
            return note == null ? "" : note;
        }

        public void setNote(String note) {
            this.note = note == null ? "" : note;
        }

        public int getRetryCount() {
            return Math.max(0, retryCount);
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = Math.max(0, retryCount);
        }

        public int getPathRetryTimeoutSeconds() {
            return Math.max(0, pathRetryTimeoutSeconds);
        }

        public void setPathRetryTimeoutSeconds(int pathRetryTimeoutSeconds) {
            this.pathRetryTimeoutSeconds = Math.max(0, pathRetryTimeoutSeconds);
        }

        public int getArrivalToleranceBlocks() {
            return Math.max(0, arrivalToleranceBlocks);
        }

        public void setArrivalToleranceBlocks(int arrivalToleranceBlocks) {
            this.arrivalToleranceBlocks = Math.max(0, arrivalToleranceBlocks);
        }

        public String getRetryExhaustedPolicy() {
            return normalizeRetryExhaustedPolicy(retryExhaustedPolicy);
        }

        public void setRetryExhaustedPolicy(String retryExhaustedPolicy) {
            this.retryExhaustedPolicy = normalizeRetryExhaustedPolicy(retryExhaustedPolicy);
        }

        public String getRetryExhaustedSequenceName() {
            return retryExhaustedSequenceName == null ? "" : retryExhaustedSequenceName.trim();
        }

        public void setRetryExhaustedSequenceName(String retryExhaustedSequenceName) {
            this.retryExhaustedSequenceName = retryExhaustedSequenceName == null ? "" : retryExhaustedSequenceName.trim();
        }

        public int getRetryExhaustedStepIndex() {
            return Math.max(0, retryExhaustedStepIndex);
        }

        public void setRetryExhaustedStepIndex(int retryExhaustedStepIndex) {
            this.retryExhaustedStepIndex = Math.max(0, retryExhaustedStepIndex);
        }

        public int getRetryExhaustedActionIndex() {
            return Math.max(0, retryExhaustedActionIndex);
        }

        public void setRetryExhaustedActionIndex(int retryExhaustedActionIndex) {
            this.retryExhaustedActionIndex = Math.max(0, retryExhaustedActionIndex);
        }

        public boolean hasGotoTarget() {
            return gotoPoint != null && gotoPoint.length >= 3 && !Double.isNaN(gotoPoint[0]);
        }

        private String normalizeRetryExhaustedPolicy(String policy) {
            String normalized = policy == null ? "" : policy.trim().toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "RESTART_SEQUENCE":
                case "RUN_SEQUENCE":
                    return normalized;
                default:
                    return "END_SEQUENCE";
            }
        }
    }

    public static class PathSequence {
        private String name;
        private final List<PathStep> steps = new ArrayList<>();
        private boolean isCustom = false;
        private boolean closeGuiAfterStart = false;
        private boolean singleExecution = false;
        private boolean nonInterruptingExecution = false;
        private String lockConflictPolicy = "WAIT";
        private String category = I18n.format("path.category.default");
        private String subCategory = "";
        private String note = "";
        /** Optional server address guard; empty keeps legacy cross-server behavior. */
        private String targetServer = "";

        private int loopDelayTicks = 20;

        public PathSequence(String name) {
            this.name = name;
        }

        public PathSequence(PathSequence other) {
            this(other, false);
        }

        public PathSequence(PathSequence other, boolean preservePersistentUuids) {
            this.name = other.name;
            this.isCustom = other.isCustom;
            this.closeGuiAfterStart = other.closeGuiAfterStart;
            this.singleExecution = other.singleExecution;
            this.nonInterruptingExecution = other.nonInterruptingExecution;
            this.lockConflictPolicy = other.lockConflictPolicy;
            this.category = other.category;
            this.subCategory = other.subCategory;
            this.note = other.note;
            this.targetServer = other.targetServer;
            this.loopDelayTicks = other.loopDelayTicks;
            for (PathStep step : other.steps) {
                this.steps.add(new PathStep(step, preservePersistentUuids));
            }
        }

        public int getLoopDelayTicks() {
            return loopDelayTicks;
        }

        public void setLoopDelayTicks(int ticks) {
            this.loopDelayTicks = ticks;
        }

        public void addStep(PathStep step) {
            steps.add(step);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<PathStep> getSteps() {
            return steps;
        }

        public boolean isCustom() {
            return isCustom;
        }

        public void setCustom(boolean custom) {
            isCustom = custom;
        }

        public boolean shouldCloseGuiAfterStart() {
            return closeGuiAfterStart;
        }

        public void setCloseGuiAfterStart(boolean value) {
            this.closeGuiAfterStart = value;
        }

        public boolean isSingleExecution() {
            return singleExecution;
        }

        public void setSingleExecution(boolean value) {
            this.singleExecution = value;
        }

        public boolean isNonInterruptingExecution() {
            return nonInterruptingExecution;
        }

        public void setNonInterruptingExecution(boolean value) {
            this.nonInterruptingExecution = value;
        }

        public String getLockConflictPolicy() {
            return lockConflictPolicy == null || lockConflictPolicy.trim().isEmpty()
                    ? "WAIT"
                    : lockConflictPolicy;
        }

        public void setLockConflictPolicy(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!"FAIL".equals(normalized) && !"PREEMPT_BACKGROUND".equals(normalized)) {
                normalized = "WAIT";
            }
            this.lockConflictPolicy = normalized;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getNote() {
            return note == null ? "" : note;
        }

        public void setNote(String note) {
            this.note = note == null ? "" : note;
        }

        public String getSubCategory() {
            return subCategory;
        }

        public void setSubCategory(String subCategory) {
            this.subCategory = subCategory == null ? "" : subCategory;
        }

        public String getTargetServer() {
            return targetServer == null ? "" : targetServer.trim();
        }

        public void setTargetServer(String targetServer) {
            this.targetServer = targetServer == null ? "" : targetServer.trim();
        }
    }

    public static void addSequence(PathSequence sequence) {
        if (sequence.isCustom()) {
            customSequences.put(sequence.getName(), sequence);
        } else {
            sequences.put(sequence.getName(), sequence);
        }
    }

    public static PathSequence getSequence(String name) {
        if (transientSequences.containsKey(name)) return transientSequences.get(name);
        if (sequences.containsKey(name)) {
            return sequences.get(name);
        }
        return customSequences.get(name);
    }

    public static boolean hasSequence(String name) {
        return transientSequences.containsKey(name) || sequences.containsKey(name) || customSequences.containsKey(name);
    }

    private static Path getCustomPathsFile() {
        return ProfileManager.getCurrentProfileDir().resolve("custom_paths.json");
    }

    private static Path getCategoriesFile() {
        return ProfileManager.getCurrentProfileDir().resolve("path_categories.json");
    }

    public static void initializePathSequences() {
        zszlScriptMod.LOGGER.info(I18n.format("log.path.init_loading"));
        sequences.clear();
        customSequences.clear();
        try {
            syncBuiltinSequencesFromResource(BUILTIN_SEQUENCES_RESOURCE, BUILTIN_SEQUENCES_PATH);
            loadSequencesFromFile(BUILTIN_SEQUENCES_PATH, false);

            loadCategories();
            loadCustomSequences();
            normalizeLoadedCategoryReferences();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.init_failed"), e);
        }
    }

    private static void syncBuiltinSequencesFromResource(String resourceName, Path targetPath) throws IOException {
        try (InputStream in = PathSequenceManager.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null)
                throw new FileNotFoundException(I18n.format("log.path.resource_not_found", resourceName));

            Files.createDirectories(targetPath.getParent());
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            zszlScriptMod.LOGGER.info(I18n.format("log.path.builtin_sync_done"), targetPath);
        }
    }

    private static void loadSequencesFromFile(Path path, boolean isCustom) {
        if (!Files.exists(path)) {
            zszlScriptMod.LOGGER.warn(I18n.format("log.path.file_missing_skip"), path);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonArray sequencesArray = root.getAsJsonArray("sequences");
            int loadedCount = 0;
            boolean migratedHuntVerticalDefaults = false;
            for (JsonElement seqElement : sequencesArray) {
                try {
                    JsonObject seqObj = seqElement.getAsJsonObject();
                    String name = seqObj.get("name").getAsString();
                    PathSequence sequence = new PathSequence(name);
                    // All path sequences are user-editable; the old bundled/custom split is no longer used.
                    sequence.setCustom(true);
                    if (seqObj.has("closeGuiAfterStart"))
                        sequence.setCloseGuiAfterStart(seqObj.get("closeGuiAfterStart").getAsBoolean());
                    if (seqObj.has("singleExecution"))
                        sequence.setSingleExecution(seqObj.get("singleExecution").getAsBoolean());
                    if (seqObj.has("nonInterruptingExecution"))
                        sequence.setNonInterruptingExecution(seqObj.get("nonInterruptingExecution").getAsBoolean());
                    if (seqObj.has("lockConflictPolicy"))
                        sequence.setLockConflictPolicy(seqObj.get("lockConflictPolicy").getAsString());
                    if (seqObj.has("category")) {
                        sequence.setCategory(seqObj.get("category").getAsString());
                    } else {
                        sequence.setCategory(isCustom ? defaultCategoryName() : builtinCategoryName());
                    }
                    if (seqObj.has("subCategory")) {
                        sequence.setSubCategory(seqObj.get("subCategory").getAsString());
                    }
                    if (seqObj.has("targetServer")) {
                        sequence.setTargetServer(seqObj.get("targetServer").getAsString());
                    }
                    sequence.setNote(jsonText(seqObj, "note"));
                    sequence.setLoopDelayTicks(jsonInt(seqObj, 20, "loopDelayTicks", "loopDelay"));

                    JsonArray stepsArray = seqObj.getAsJsonArray("steps");
                    for (JsonElement stepElement : stepsArray) {
                        JsonObject stepObj = stepElement.getAsJsonObject();
                        JsonArray gotoArray = stepObj.getAsJsonArray("goto");
                        double[] gotoPoint = new double[3];
                        for (int i = 0; i < 3; i++)
                            gotoPoint[i] = gotoArray.get(i).isJsonNull() ? Double.NaN : gotoArray.get(i).getAsDouble();
                        PathStep step = new PathStep(gotoPoint);
                        boolean hasLegacyStepSchedulingFields = stepObj.has("retryDelayTicks")
                                || stepObj.has("failurePolicy")
                                || stepObj.has("failureStepIndex")
                                || stepObj.has("preconditionExpression")
                                || stepObj.has("preconditionPolicy")
                                || stepObj.has("preconditionStepIndex");
                        if (stepObj.has("retryCount")) {
                            int retryCount = stepObj.get("retryCount").getAsInt();
                            if (retryCount <= 0 && hasLegacyStepSchedulingFields
                                    && !stepObj.has("pathRetryTimeoutSeconds")) {
                                step.setRetryCount(getDefaultStepRetryCount());
                            } else {
                                step.setRetryCount(retryCount);
                            }
                        }
                        step.setNote(jsonText(stepObj, "note"));
                        if (stepObj.has("pathRetryTimeoutSeconds")) {
                            step.setPathRetryTimeoutSeconds(stepObj.get("pathRetryTimeoutSeconds").getAsInt());
                        }
                        if (stepObj.has("arrivalToleranceBlocks")) {
                            step.setArrivalToleranceBlocks(stepObj.get("arrivalToleranceBlocks").getAsInt());
                        }
                        if (stepObj.has("retryExhaustedPolicy")) {
                            step.setRetryExhaustedPolicy(stepObj.get("retryExhaustedPolicy").getAsString());
                        }
                        if (stepObj.has("retryExhaustedSequenceName")) {
                            step.setRetryExhaustedSequenceName(stepObj.get("retryExhaustedSequenceName").getAsString());
                        }
                        if (stepObj.has("retryExhaustedStepIndex")) {
                            step.setRetryExhaustedStepIndex(stepObj.get("retryExhaustedStepIndex").getAsInt());
                        }
                        if (stepObj.has("retryExhaustedActionIndex")) {
                            step.setRetryExhaustedActionIndex(stepObj.get("retryExhaustedActionIndex").getAsInt());
                        }
                        if (stepObj.has("actions")) {
                            JsonArray actionsArray = stepObj.getAsJsonArray("actions");
                            for (JsonElement actionElement : actionsArray) {
                                JsonObject actionObj = actionElement.getAsJsonObject();
                                String type = actionObj.get("type").getAsString();
                                JsonObject params = actionObj.has("params") ? actionObj.getAsJsonObject("params")
                                        : new JsonObject();
                                migratedHuntVerticalDefaults |= ensureHuntVerticalRangeDefaults(type, params);
                                step.addAction(new ActionData(type, params));
                            }
                        }
                        sequence.addStep(step);
                    }
                    addSequence(sequence);
                    loadedCount++;
                } catch (Exception e) {
                    zszlScriptMod.LOGGER.error(I18n.format("log.path.parse_sequence_failed"), path.getFileName(),
                            seqElement.getAsJsonObject().get("name").getAsString(), e);
                }
            }
            zszlScriptMod.LOGGER.info(I18n.format("log.path.loaded_count"), path.getFileName(), loadedCount);
            if (isCustom && migratedHuntVerticalDefaults) {
                saveSequencesToFile(path, new ArrayList<>(customSequences.values()));
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.load_file_failed"), path, e);
        }
    }

    public static Consumer<EntityPlayerSP> parseAction(String type, JsonObject params) {
        try {
            switch (type.toLowerCase()) {
                case "command":
                    return player -> ModUtils.sendChatCommand(params.get("command").getAsString());
                case "system_message":
                    String rawMessage = params.has("message") ? params.get("message").getAsString() : "";
                    return player -> player.sendMessage(new TextComponentString(rawMessage));
                case "paste_text":
                    final String pasteText = params.has("text") ? params.get("text").getAsString() : "";
                    return player -> ModUtils.simulatePasteText(pasteText);
                case "disconnect":
                    return player -> ModUtils.disconnectFromCurrentWorld();
                case "delay":
                    return new ModUtils.DelayAction(
                            TickRangeSpec.sample(params.get("ticks"), 0, MIN_DELAY_TICKS, MAX_DELAY_TICKS),
                            isDelayNormalizedTo20Tps(params));
                case "key":
                    final String keyName = params.has("key") ? params.get("key").getAsString() : "";
                    final String keyState = params.has("state") ? params.get("state").getAsString() : "Press";
                    final boolean customPressDuration = params.has("customPressDuration")
                            && params.get("customPressDuration").getAsBoolean();
                    final int pressDurationTicks = customPressDuration && params.has("pressDurationTicks")
                            ? Math.max(1, params.get("pressDurationTicks").getAsInt()) : 10;
                    return player -> {
                        PathSequenceEventListener actionRunner = PathSequenceEventListener.getActionExecutionTarget();
                        ModUtils.simulateActionKey(keyName, keyState, pressDurationTicks,
                                actionRunner.isBackgroundRunner());
                    };
                case "skip_actions":
                case "skip_steps":
                case "no_stop_navigation":
                    return player -> {
                    };
                case "jump":
                    final int jumpCount = Math.max(1, params.has("count") ? params.get("count").getAsInt() : 1);
                    final int intervalTicks = Math.max(0,
                            params.has("intervalTicks") ? params.get("intervalTicks").getAsInt() : 0);
                    return player -> {
                        if (intervalTicks <= 0 || ModUtils.DelayScheduler.instance == null) {
                            for (int i = 0; i < jumpCount; i++) {
                                ModUtils.sendJumpPacket(player);
                            }
                            return;
                        }

                        for (int i = 0; i < jumpCount; i++) {
                            final int delay = i * intervalTicks;
                            ModUtils.DelayScheduler.instance.schedule(() -> ModUtils.sendJumpPacket(player), delay);
                        }
                    };
                case "click":
                    final int x = getIntParam(params, "x", 0);
                    final int y = getIntParam(params, "y", 0);
                    final String clickMouseButton = getClickMouseButton(params);
                    final String clickLocatorMode = normalizeClickLocatorMode(params.has("locatorMode")
                            ? params.get("locatorMode").getAsString()
                            : ActionTargetLocator.CLICK_MODE_COORDINATE);
                    final String clickLocatorText = params.has("locatorText")
                            ? params.get("locatorText").getAsString()
                            : "";
                    final String clickLocatorMatchMode = params.has("locatorMatchMode")
                            ? params.get("locatorMatchMode").getAsString()
                            : ActionTargetLocator.MATCH_MODE_CONTAINS;
                    final int originalWidth = getIntParam(params, "originalWidth", 2560);
                    final int originalHeight = getIntParam(params, "originalHeight", 1334);

                    return player -> {
                        if (!isClickCoordinateMode(clickLocatorMode)) {
                            if (!isShiftModifiedMouseButton(clickMouseButton)
                                    && ActionTargetLocator.tryInvokeCurrentScreenClick(clickLocatorMode,
                                            clickLocatorText, clickLocatorMatchMode, clickMouseButton)) {
                                return;
                            }
                            ActionTargetLocator.ClickPoint point = ActionTargetLocator.resolveScreenClickPoint(
                                    clickLocatorMode,
                                    clickLocatorText,
                                    clickLocatorMatchMode);
                            if (point == null) {
                                zszlScriptMod.LOGGER.warn("[legacy_path] click 定位失败: mode={}, text={}",
                                        clickLocatorMode, clickLocatorText);
                                return;
                            }
                            Minecraft mc = Minecraft.getMinecraft();
                            ModUtils.simulateMouseClick(point.getX(), point.getY(), clickMouseButton,
                                    mc.displayWidth, mc.displayHeight);
                            return;
                        }
                        ModUtils.simulateMouseClick(x, y, clickMouseButton, originalWidth, originalHeight);
                    };

                case "window_click":
                    final String windowId = params.has("windowId") ? params.get("windowId").getAsString() : "-1";
                    final String slotIdRaw = params.has("slot") ? params.get("slot").getAsString() : "-1";
                    final String slotBase = params.has("slotBase") ? params.get("slotBase").getAsString() : "DEC";
                    int parsedSlotId;
                    try {
                        parsedSlotId = ModUtils.parseNumericSpec(slotIdRaw, slotBase);
                    } catch (Exception ex) {
                        parsedSlotId = -1;
                    }
                    final int directSlotId = parsedSlotId;
                    final String windowSlotLocatorMode = params.has("locatorMode")
                            ? params.get("locatorMode").getAsString()
                            : ActionTargetLocator.SLOT_MODE_DIRECT;
                    final String windowSlotLocatorText = params.has("locatorText")
                            ? params.get("locatorText").getAsString()
                            : "";
                    final String windowSlotLocatorMatchMode = params.has("locatorMatchMode")
                            ? params.get("locatorMatchMode").getAsString()
                            : ActionTargetLocator.MATCH_MODE_CONTAINS;
                    final String windowContainsText = params.has("contains") ? params.get("contains").getAsString()
                            : "";
                    final int button = params.has("button") ? params.get("button").getAsInt() : 0;
                    final String clickType = params.has("clickType") ? params.get("clickType").getAsString() : "PICKUP";
                    final boolean onlyOnSlotChange = params.has("onlyOnSlotChange")
                            && params.get("onlyOnSlotChange").getAsBoolean();
                    String uuidValue = ensurePersistentActionUuid(params, "window_click");
                    final String actionUuid = uuidValue;
                    return player -> {
                        int resolvedSlotId = directSlotId;
                        if (!ActionTargetLocator.SLOT_MODE_DIRECT.equalsIgnoreCase(windowSlotLocatorMode)) {
                            ActionTargetLocator.SlotResult slotResult = ActionTargetLocator.resolveContainerSlot(
                                    windowSlotLocatorMode,
                                    windowSlotLocatorText,
                                    windowSlotLocatorMatchMode);
                            if (slotResult == null) {
                                zszlScriptMod.LOGGER.warn("[legacy_path] window_click 定位失败: mode={}, text={}",
                                        windowSlotLocatorMode, windowSlotLocatorText);
                                return;
                            }
                            resolvedSlotId = slotResult.getSlotIndex();
                        }
                        if (!windowContainsText.trim().isEmpty()) {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.player == null || mc.player.openContainer == null
                                    || resolvedSlotId < 0
                                    || resolvedSlotId >= mc.player.openContainer.inventorySlots.size()) {
                                return;
                            }
                            Slot slotObj = mc.player.openContainer.getSlot(resolvedSlotId);
                            if (slotObj == null || !slotObj.getHasStack()
                                    || !slotObj.getStack().getDisplayName().contains(windowContainsText.trim())) {
                                return;
                            }
                        }
                        if (onlyOnSlotChange) {
                            ensureWindowClickMapLoaded();
                            Integer lastSlot = windowClickLastSlotByUuid.get(actionUuid);
                            if (lastSlot != null && lastSlot.intValue() == resolvedSlotId) {
                                return;
                            }
                            windowClickLastSlotByUuid.put(actionUuid, resolvedSlotId);
                            saveWindowClickMap();
                        }
                        ModUtils.performWindowClick(windowId, resolvedSlotId, button, clickType);
                    };
                case "conditional_window_click":
                    final String conditionalWindowId = params.has("windowId") ? params.get("windowId").getAsString()
                            : "-1";
                    final String conditionalSlotRaw = params.has("slot") ? params.get("slot").getAsString() : "-1";
                    final String conditionalSlotBase = params.has("slotBase") ? params.get("slotBase").getAsString()
                            : "DEC";
                    final String conditionalSlotLocatorMode = params.has("locatorMode")
                            ? params.get("locatorMode").getAsString()
                            : ActionTargetLocator.SLOT_MODE_DIRECT;
                    final String conditionalSlotLocatorText = params.has("locatorText")
                            ? params.get("locatorText").getAsString()
                            : "";
                    final String conditionalSlotLocatorMatchMode = params.has("locatorMatchMode")
                            ? params.get("locatorMatchMode").getAsString()
                            : ActionTargetLocator.MATCH_MODE_CONTAINS;
                    final int conditionalButton = params.has("button") ? params.get("button").getAsInt() : 0;
                    final String conditionalClickType = params.has("clickType") ? params.get("clickType").getAsString()
                            : "PICKUP";
                    final String containsText = params.has("contains") ? params.get("contains").getAsString() : "";
                    return player -> {
                        try {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.player == null || mc.player.openContainer == null) {
                                return;
                            }
                            int conditionalSlot;
                            if (ActionTargetLocator.SLOT_MODE_DIRECT.equalsIgnoreCase(conditionalSlotLocatorMode)) {
                                conditionalSlot = ModUtils.parseNumericSpec(conditionalSlotRaw, conditionalSlotBase);
                            } else {
                                ActionTargetLocator.SlotResult slotResult = ActionTargetLocator.resolveContainerSlot(
                                        conditionalSlotLocatorMode,
                                        conditionalSlotLocatorText,
                                        conditionalSlotLocatorMatchMode);
                                if (slotResult == null) {
                                    zszlScriptMod.LOGGER.warn(
                                            "[legacy_path] conditional_window_click 定位失败: mode={}, text={}",
                                            conditionalSlotLocatorMode, conditionalSlotLocatorText);
                                    return;
                                }
                                conditionalSlot = slotResult.getSlotIndex();
                            }
                            if (conditionalSlot < 0
                                    || conditionalSlot >= mc.player.openContainer.inventorySlots.size()) {
                                return;
                            }
                            Slot slotObj = mc.player.openContainer.getSlot(conditionalSlot);
                            if (slotObj == null || !slotObj.getHasStack()) {
                                return;
                            }
                            if (!containsText.trim().isEmpty()
                                    && !slotObj.getStack().getDisplayName().contains(containsText.trim())) {
                                return;
                            }
                            ModUtils.performWindowClick(conditionalWindowId, conditionalSlot, conditionalButton,
                                    conditionalClickType);
                        } catch (Exception ignored) {
                        }
                    };
                case "take_all_items_safe":
                    final boolean safeShiftQuickMove = !params.has("shiftQuickMove")
                            || params.get("shiftQuickMove").getAsBoolean();
                    return player -> ModUtils.takeAllItemsFromChest(safeShiftQuickMove);
                case "hud_text_check":
                    final String contains = params.has("contains") ? params.get("contains").getAsString() : "";
                    final boolean matchBlock = params.has("matchBlock") && params.get("matchBlock").getAsBoolean();
                    final String separator = params.has("separator") ? params.get("separator").getAsString() : " | ";
                    return player -> {
                        List<String> matches = new ArrayList<>();
                        if (matchBlock) {
                            for (HudTextScanner.TextBlock block : HudTextScanner.INSTANCE.getProcessedTextBlocks()) {
                                String text = block.getJoinedText(separator);
                                if (contains.trim().isEmpty() || text.contains(contains.trim())) {
                                    matches.add(text);
                                }
                            }
                        } else {
                            for (HudTextScanner.CapturedText text : HudTextScanner.INSTANCE.getCurrentHudText()) {
                                if (contains.trim().isEmpty() || text.text.contains(contains.trim())) {
                                    matches.add(text.text);
                                }
                            }
                        }
                        if (player != null) {
                            if (matches.isEmpty()) {
                                player.sendMessage(new TextComponentString("§e[HUD识别] 未找到匹配文本"));
                            } else {
                                player.sendMessage(new TextComponentString("§a[HUD识别] " + matches.get(0)));
                            }
                        }
                    };

                case "setview":
                    float yaw = params.get("yaw").getAsFloat();
                    float pitch = params.get("pitch").getAsFloat();
                    return player -> ModUtils.setPlayerViewAngles(player, yaw, pitch);
                case "rightclickblock":
                    final BlockPos pos = tryParseTargetPos(params);
                    final double blockRange = params.has("range") ? params.get("range").getAsDouble() : 10.0D;
                    final String blockLocatorMode = params.has("locatorMode")
                            ? params.get("locatorMode").getAsString()
                            : ActionTargetLocator.TARGET_MODE_POSITION;
                    final String blockLocatorText = params.has("locatorText")
                            ? params.get("locatorText").getAsString()
                            : "";
                    final String blockLocatorMatchMode = params.has("locatorMatchMode")
                            ? params.get("locatorMatchMode").getAsString()
                            : ActionTargetLocator.MATCH_MODE_CONTAINS;
                    final boolean blockPreserveView = isPreserveViewEnabled(params);
                    return player -> {
                        BlockPos resolvedPos = pos;
                        if (ActionTargetLocator.TARGET_MODE_NAME.equalsIgnoreCase(blockLocatorMode)) {
                            resolvedPos = ActionTargetLocator.findNearbyInteractableBlock(blockLocatorText,
                                    blockLocatorMatchMode, blockRange);
                            if (resolvedPos == null) {
                                zszlScriptMod.LOGGER.warn("[legacy_path] rightclickblock 定位失败: text={}",
                                        blockLocatorText);
                                return;
                            }
                        } else if (resolvedPos == null) {
                            zszlScriptMod.LOGGER.warn("[legacy_path] rightclickblock 坐标无效或缺失: pos={}",
                                    params.has("pos") ? params.get("pos") : null);
                            return;
                        }
                        ModUtils.rightClickOnBlock(player, resolvedPos, blockPreserveView);
                    };
                case "rightclickentity":
                    final BlockPos entityPos = tryParseTargetPos(params);
                    final double range = params.get("range").getAsDouble();
                    final String entityLocatorMode = params.has("locatorMode")
                            ? params.get("locatorMode").getAsString()
                            : ActionTargetLocator.TARGET_MODE_POSITION;
                    final String entityLocatorText = params.has("locatorText")
                            ? params.get("locatorText").getAsString()
                            : "";
                    final String entityLocatorMatchMode = params.has("locatorMatchMode")
                            ? params.get("locatorMatchMode").getAsString()
                            : ActionTargetLocator.MATCH_MODE_CONTAINS;
                    final boolean entityPreserveView = isPreserveViewEnabled(params);
                    return player -> {
                        if (ActionTargetLocator.TARGET_MODE_NAME.equalsIgnoreCase(entityLocatorMode)) {
                            Entity targetEntity = ActionTargetLocator.findNearbyEntity(entityLocatorText,
                                    entityLocatorMatchMode, range);
                            if (targetEntity == null) {
                                zszlScriptMod.LOGGER.warn("[legacy_path] rightclickentity 定位失败: text={}",
                                        entityLocatorText);
                                return;
                            }
                            Minecraft.getMinecraft().playerController.interactWithEntity(player, targetEntity,
                                    EnumHand.MAIN_HAND);
                            return;
                        }
                        if (entityPos == null) {
                            zszlScriptMod.LOGGER.warn("[legacy_path] rightclickentity 坐标无效或缺失: pos={}",
                                    params.has("pos") ? params.get("pos") : null);
                            return;
                        }
                        ModUtils.rightClickOnNearestEntity(player, entityPos, range, entityPreserveView);
                    };
                case "takeallitems":
                    final boolean shiftQuickMove = !params.has("shiftQuickMove")
                            || params.get("shiftQuickMove").getAsBoolean();
                    return player -> ModUtils.takeAllItemsFromChest(shiftQuickMove);
                case "dropfiltereditems":
                    return player -> ItemFilterHandler.dropItemsByNameFilter();
                case "autochestclick":
                    final int slot = params.get("slot").getAsInt();
                    final String chestSlotLocatorMode = params.has("locatorMode")
                            ? params.get("locatorMode").getAsString()
                            : ActionTargetLocator.SLOT_MODE_DIRECT;
                    final String chestSlotLocatorText = params.has("locatorText")
                            ? params.get("locatorText").getAsString()
                            : "";
                    final String chestSlotLocatorMatchMode = params.has("locatorMatchMode")
                            ? params.get("locatorMatchMode").getAsString()
                            : ActionTargetLocator.MATCH_MODE_CONTAINS;
                    final int chestClickDelayTicks = params.has("delayTicks")
                            ? Math.max(0, params.get("delayTicks").getAsInt())
                            : 1;
                    final String chestClickType = params.has("clickType") ? params.get("clickType").getAsString()
                            : "PICKUP";
                    final int chestButton = params.has("button") ? params.get("button").getAsInt() : 0;
                    return new ModUtils.DelayAction(chestClickDelayTicks, () -> {
                        int resolvedSlot = slot;
                        if (!ActionTargetLocator.SLOT_MODE_DIRECT.equalsIgnoreCase(chestSlotLocatorMode)) {
                            ActionTargetLocator.SlotResult slotResult = ActionTargetLocator.resolveContainerSlot(
                                    chestSlotLocatorMode,
                                    chestSlotLocatorText,
                                    chestSlotLocatorMatchMode);
                            if (slotResult == null) {
                                zszlScriptMod.LOGGER.warn("[legacy_path] autochestclick 定位失败: mode={}, text={}",
                                        chestSlotLocatorMode, chestSlotLocatorText);
                                return;
                            }
                            resolvedSlot = slotResult.getSlotIndex();
                        }
                        ModUtils.clickChestSlotNow(resolvedSlot, chestButton, chestClickType);
                    });
                case "move_inventory_items_to_chest_slots":
                    return player -> ItemFilterHandler.moveInventoryItemsToChestSlots(params);
                case "spread_inventory_item":
                    return player -> ItemSpreadHandler.spreadInventoryItem(params);
                case "stack_inventory_item":
                    return player -> ItemSpreadHandler.stackInventoryItems(params);
                case "pickup_nearby_items":
                    return player -> NearbyItemPickupActionHandler.start(player, params);
                case "warehouse_auto_deposit":
                    return player -> WarehouseEventHandler.startAutoDepositByHighlights();
                case "blocknextgui":
                    int blockCount = params.has("count") ? params.get("count").getAsInt() : 1;
                    boolean blockCurrentGui = params.has("blockCurrentGui")
                            && params.get("blockCurrentGui").getAsBoolean();
                    return player -> GuiBlockerHandler.blockGui(blockCount, blockCurrentGui);
                case "hidecurrentgui":
                    return player -> GuiVisibilityHandler.hideCurrentGui();
                case "showhiddengui":
                    String hiddenGuiId = params.has("hiddenGuiId") && !params.get("hiddenGuiId").isJsonNull()
                            ? params.get("hiddenGuiId").getAsString() : "";
                    return player -> GuiVisibilityHandler.showHiddenGui(hiddenGuiId);
                case "close_container_window":
                    return player -> {
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc != null && mc.currentScreen != null) {
                            mc.displayGuiScreen(null);
                        }
                        if (player != null && player.openContainer != null) {
                            player.closeScreen();
                            if (player.inventoryContainer != null) {
                                player.openContainer = player.inventoryContainer;
                            }
                        }
                    };
                case "autoeat":
                    final boolean autoEatEnabled = !params.has("enabled") || params.get("enabled").getAsBoolean();
                    final int foodThreshold = params.has("foodLevelThreshold")
                            ? Math.max(0, Math.min(20, params.get("foodLevelThreshold").getAsInt()))
                            : AutoEatHandler.foodLevelThreshold;
                    final boolean autoMoveFoodEnabled = !params.has("autoMoveFoodEnabled")
                            || params.get("autoMoveFoodEnabled").getAsBoolean();
                    final boolean eatWithLookDown = params.has("eatWithLookDown")
                            && params.get("eatWithLookDown").getAsBoolean();
                    final int targetHotbarSlot = params.has("targetHotbarSlot")
                            ? Math.max(1, Math.min(9, params.get("targetHotbarSlot").getAsInt()))
                            : AutoEatHandler.targetHotbarSlot;
                    final String foodKeywordsText = params.has("foodKeywordsText")
                            ? params.get("foodKeywordsText").getAsString()
                            : "";
                    return player -> {
                        AutoEatHandler.autoEatEnabled = autoEatEnabled;
                        AutoEatHandler.foodLevelThreshold = foodThreshold;
                        AutoEatHandler.autoMoveFoodEnabled = autoMoveFoodEnabled;
                        AutoEatHandler.eatWithLookDown = eatWithLookDown;
                        AutoEatHandler.targetHotbarSlot = targetHotbarSlot;
                        if (foodKeywordsText != null && !foodKeywordsText.trim().isEmpty()) {
                            List<String> keywords = Arrays.stream(foodKeywordsText.split("\\r?\\n|,"))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList());
                            if (!keywords.isEmpty()) {
                                AutoEatHandler.foodKeywords = new ArrayList<>(keywords);
                            }
                        }
                        AutoEatHandler.saveAutoEatConfig();
                    };
                case "autoequip":
                    final boolean autoEquipEnabled = !params.has("enabled") || params.get("enabled").getAsBoolean();
                    return player -> ItemFeatureManager.setEnabled("auto_equip", autoEquipEnabled);
                case "autopickup":
                    final boolean autoPickupEnabled = !params.has("enabled") || params.get("enabled").getAsBoolean();
                    return player -> {
                        AutoPickupHandler.globalEnabled = autoPickupEnabled;
                        AutoPickupHandler.saveConfig();
                    };
                case "toggle_autoeat":
                    final boolean toggleAutoEatEnabled = !params.has("enabled") || params.get("enabled").getAsBoolean();
                    return player -> {
                        AutoEatHandler.autoEatEnabled = toggleAutoEatEnabled;
                        AutoEatHandler.saveAutoEatConfig();
                    };
                case "toggle_autofishing":
                    final boolean toggleAutoFishingEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> AutoFishingHandler.INSTANCE.setEnabled(toggleAutoFishingEnabled);
                case "toggle_auto_pickup":
                    final boolean toggleAutoPickupEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> {
                        AutoPickupHandler.globalEnabled = toggleAutoPickupEnabled;
                        AutoPickupHandler.saveConfig();
                    };
                case "toggle_kill_aura":
                    final boolean toggleKillAuraEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    final String killAuraPresetName = params.has("presetName") && !params.get("presetName").isJsonNull()
                            ? params.get("presetName").getAsString().trim() : "";
                    return player -> {
                        if (toggleKillAuraEnabled && !killAuraPresetName.isEmpty()) {
                            KillAuraHandler.applyPresetByName(killAuraPresetName);
                        }
                        KillAuraHandler.INSTANCE.setEnabled(toggleKillAuraEnabled);
                    };
                case "toggle_auto_follow":
                    final boolean toggleAutoFollowEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> AutoFollowHandler.setEnabled(toggleAutoFollowEnabled);
                case "toggle_fly":
                    final boolean toggleFlyEnabled = !params.has("enabled") || params.get("enabled").getAsBoolean();
                    return player -> FlyHandler.INSTANCE.setEnabled(toggleFlyEnabled);
                case "toggle_conditional_execution":
                    final boolean toggleConditionalExecutionEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> ConditionalExecutionHandler.setGlobalEnabled(toggleConditionalExecutionEnabled);
                case "toggle_auto_escape":
                    final boolean toggleAutoEscapeEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> AutoEscapeHandler.setMasterEnabled(toggleAutoEscapeEnabled);
                case "toggle_baritone_free_look":
                    final boolean toggleBaritoneFreeLookEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> applyBaritoneFreeLookToggle(toggleBaritoneFreeLookEnabled);
                case "toggle_baritone_human_like":
                    final boolean toggleBaritoneHumanLikeEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> applyBaritoneHumanLikeToggle(toggleBaritoneHumanLikeEnabled);
                case "toggle_baritone_flight":
                    final boolean toggleBaritoneFlightEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> applyBaritoneFlightToggle(toggleBaritoneFlightEnabled);
                case "toggle_other_feature":
                    final String targetOtherFeatureId = params.has("featureId")
                            ? params.get("featureId").getAsString()
                            : "";
                    final boolean toggleOtherFeatureEnabled = !params.has("enabled")
                            || params.get("enabled").getAsBoolean();
                    return player -> applyOtherFeatureToggle(targetOtherFeatureId, toggleOtherFeatureEnabled);
                case "run_sequence":
                    String targetSequenceName = params.has("sequenceName")
                            ? params.get("sequenceName").getAsString()
                            : "";
                    final String runSequenceActionUuid = ensurePersistentActionUuid(params, "run_sequence");
                    final boolean intervalExecutionMode = "interval".equalsIgnoreCase(
                            params.has("executeMode") ? params.get("executeMode").getAsString() : "always");
                    final int executeEveryCount = getRunSequenceExecuteEveryCount(params);
                    final boolean backgroundExecution = isRunSequenceBackgroundExecution(params);
                    return player -> runSequenceFromAction(targetSequenceName, runSequenceActionUuid,
                            intervalExecutionMode, executeEveryCount, backgroundExecution, player);
                case "stop_current_sequence":
                    final String stopTargetScope = getStopCurrentSequenceTargetScope(params);
                    return player -> stopCurrentSequenceFromAction(stopTargetScope);
                case "sequence_control":
                    final String sequenceControlTargetScope = getSequenceControlTargetScope(params);
                    final String sequenceControlOperation = getSequenceControlOperation(params);
                    return player -> controlSequenceFromAction(sequenceControlTargetScope, sequenceControlOperation);
                case "run_template":
                    final String templateNameValue = params.has("templateName")
                            ? params.get("templateName").getAsString()
                            : "";
                    final String templateParamsText = params.has("paramsText") ? params.get("paramsText").getAsString()
                            : "";
                    final String runTemplateActionUuid = ensurePersistentActionUuid(params, "run_template");
                    final boolean templateIntervalExecutionMode = "interval".equalsIgnoreCase(
                            params.has("executeMode") ? params.get("executeMode").getAsString() : "always");
                    final int templateExecuteEveryCount = getRunSequenceExecuteEveryCount(params);
                    final boolean templateBackgroundExecution = isRunSequenceBackgroundExecution(params);
                    return player -> runTemplateFromAction(templateNameValue, runTemplateActionUuid, templateParamsText,
                            templateIntervalExecutionMode, templateExecuteEveryCount, templateBackgroundExecution,
                            player);
                case "silentuse":
                    String itemName = params.has("item") ? params.get("item").getAsString() : "";
                    int tempSlot = params.has("tempslot")
                            ? Math.max(0, Math.min(8, params.get("tempslot").getAsInt()))
                            : 0;
                    int switchDelayTicks = params.has("switchDelayTicks")
                            ? Math.max(0, params.get("switchDelayTicks").getAsInt())
                            : -1;
                    int useDelayTicks = params.has("useDelayTicks")
                            ? Math.max(0, params.get("useDelayTicks").getAsInt())
                            : -1;
                    int switchBackDelayTicks = params.has("switchBackDelayTicks")
                            ? Math.max(0, params.get("switchBackDelayTicks").getAsInt())
                            : -1;
                    return player -> ModUtils.useItemFromInventory(itemName, tempSlot, switchDelayTicks,
                            useDelayTicks, switchBackDelayTicks);
                case "hunt":
                    return player -> PathSequenceEventListener.getActionExecutionTarget().startHunting(params);
                case "follow_entity":
                    return player -> PathSequenceEventListener.getActionExecutionTarget().startFollowingEntity(params);
                case "use_hotbar_item":
                    final String hotbarItemName = getStringParam(params, "itemName", "");
                    final AutoUseItemRule.MatchMode hotbarMatchMode = "EXACT".equalsIgnoreCase(
                            getStringParam(params, "matchMode", "CONTAINS"))
                            ? AutoUseItemRule.MatchMode.EXACT
                            : AutoUseItemRule.MatchMode.CONTAINS;
                    final AutoUseItemRule.UseMode hotbarUseMode = "LEFT_CLICK".equalsIgnoreCase(
                            getStringParam(params, "useMode", "RIGHT_CLICK"))
                            ? AutoUseItemRule.UseMode.LEFT_CLICK
                            : AutoUseItemRule.UseMode.RIGHT_CLICK;
                    final boolean hotbarChangeLocalSlot = readBooleanParam(params, "changeLocalSlot", false);
                    final int hotbarSwitchItemDelayTicks = Math.max(0,
                            getIntParam(params, "switchItemDelayTicks", 0));
                    final int hotbarSwitchDelayTicks = Math.max(0,
                            getIntParam(params, "switchDelayTicks", 0));
                    final int hotbarSwitchBackDelayTicks = Math.max(0,
                            getIntParam(params, "switchBackDelayTicks", 0));
                    final int hotbarUseCount = Math.max(1, getIntParam(params, "count", 1));
                    final int hotbarIntervalTicks = Math.max(0,
                            getIntParam(params, "intervalTicks", 0));
                    return player -> {
                        if (player == null || hotbarItemName == null || hotbarItemName.trim().isEmpty()) {
                            return;
                        }

                        if (hotbarIntervalTicks <= 0 || ModUtils.DelayScheduler.instance == null) {
                            for (int i = 0; i < hotbarUseCount; i++) {
                                AutoUseItemHandler.INSTANCE.useMatchingHotbarItem(player, hotbarItemName,
                                        hotbarMatchMode, hotbarUseMode, hotbarChangeLocalSlot,
                                        hotbarSwitchItemDelayTicks, hotbarSwitchDelayTicks, hotbarSwitchBackDelayTicks);
                            }
                            return;
                        }

                        for (int i = 0; i < hotbarUseCount; i++) {
                            final int delay = i * hotbarIntervalTicks;
                            ModUtils.DelayScheduler.instance.schedule(() -> AutoUseItemHandler.INSTANCE
                                    .useMatchingHotbarItem(player, hotbarItemName, hotbarMatchMode, hotbarUseMode,
                                            hotbarChangeLocalSlot,
                                            hotbarSwitchItemDelayTicks, hotbarSwitchDelayTicks,
                                            hotbarSwitchBackDelayTicks),
                                    delay);
                        }
                    };
                case "move_inventory_item_to_hotbar":
                    final String inventoryItemName = params.has("itemName") ? params.get("itemName").getAsString() : "";
                    final AutoUseItemRule.MatchMode inventoryMatchMode = params.has("matchMode")
                            && "EXACT".equalsIgnoreCase(params.get("matchMode").getAsString())
                                    ? AutoUseItemRule.MatchMode.EXACT
                                    : AutoUseItemRule.MatchMode.CONTAINS;
                    final int inventoryTargetHotbarSlot = params.has("targetHotbarSlot")
                            ? Math.max(1, Math.min(9, params.get("targetHotbarSlot").getAsInt()))
                            : 1;
                    final int inventoryMoveCount = Math.max(0,
                            params.has("count") ? params.get("count").getAsInt() : 0);
                    final boolean inventorySkipIfTargetOccupied = !params.has("skipIfTargetOccupied")
                            || params.get("skipIfTargetOccupied").getAsBoolean();
                    return player -> {
                        if (player == null || inventoryItemName == null || inventoryItemName.trim().isEmpty()) {
                            return;
                        }
                        ModUtils.moveInventoryItemToHotbar(inventoryItemName, inventoryMatchMode,
                                inventoryTargetHotbarSlot, inventoryMoveCount, inventorySkipIfTargetOccupied);
                    };
                case "switch_hotbar_slot":
                    final int switchHotbarSlot = params.has("targetHotbarSlot")
                            ? Math.max(1, Math.min(9, params.get("targetHotbarSlot").getAsInt()))
                            : 1;
                    final boolean useAfterSwitch = params.has("useAfterSwitch")
                            && params.get("useAfterSwitch").getAsBoolean();
                    final int useAfterSwitchDelayTicks = Math.max(0,
                            params.has("useAfterSwitchDelayTicks")
                                    ? params.get("useAfterSwitchDelayTicks").getAsInt()
                                    : 0);
                    return player -> {
                        if (!ModUtils.switchToHotbarSlot(switchHotbarSlot) || !useAfterSwitch) {
                            return;
                        }
                        ModUtils.useHeldItem(player, useAfterSwitchDelayTicks);
                    };
                case "use_held_item":
                    final int heldItemDelayTicks = Math.max(0,
                            params.has("delayTicks") ? params.get("delayTicks").getAsInt() : 0);
                    return player -> ModUtils.useHeldItem(player, heldItemDelayTicks);
                // --- 核心修改：更新 send_packet 的解析逻辑 ---
                case "send_packet":
                    String channel = params.has("channel") ? params.get("channel").getAsString() : null;
                    String hexData = params.get("hex").getAsString();
                    String direction = params.has("direction") ? params.get("direction").getAsString() : "C2S";
                    boolean inbound = "S2C".equalsIgnoreCase(direction) || "INBOUND".equalsIgnoreCase(direction);

                    if (channel != null && !channel.isEmpty()) {
                        // 优先使用频道发送 FML 包
                        if (inbound) {
                            return player -> ModUtils.mockReceiveFmlPacket(channel, hexData);
                        }
                        return player -> ModUtils.sendFmlPacket(channel, hexData);
                    } else if (params.has("packetId")) {
                        // 如果没有频道，则尝试使用 Packet ID 发送标准包
                        int packetId = params.get("packetId").getAsInt();
                        if (inbound) {
                            return player -> ModUtils.mockReceiveStandardPacketById(packetId, hexData);
                        }
                        return player -> ModUtils.sendStandardPacketById(packetId, hexData);
                    } else {
                        // 如果两者都未提供，则记录错误
                        zszlScriptMod.LOGGER.error(I18n.format("log.path.send_packet_missing_target"));
                        return player -> player.sendMessage(
                                new TextComponentString(I18n.format("msg.path.send_packet.invalid_config")));
                    }
                    // --- 修改结束 ---
                default:
                    zszlScriptMod.LOGGER.warn(I18n.format("log.path.unknown_action_type"), type);
                    return null;
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.parse_action_failed"), type, params, e);
            return null;
        }
    }

    // ... (所有其他方法保持不变) ...
    private static void loadCustomSequences() {
        Path customPathsFile = getCustomPathsFile();
        if (!Files.exists(customPathsFile)) {
            return;
        }
        loadSequencesFromFile(customPathsFile, true);
    }

    public static void saveAllSequences(List<PathSequence> allSequences) {
        if (allSequences == null) {
            return;
        }
        List<PathSequence> sequencesToSave = new ArrayList<>();
        for (PathSequence seq : allSequences) {
            if (seq != null && seq.getName() != null && !seq.getName().trim().isEmpty()) {
                seq.setCustom(true);
                sequencesToSave.add(seq);
            }
        }
        // The editor passes an isolated draft list. Commit that list to the
        // runtime registry only when the user explicitly saves.
        customSequences.clear();
        sequences.clear();
        for (PathSequence sequence : sequencesToSave) {
            customSequences.put(sequence.getName(), sequence);
        }
        saveSequencesToFile(getCustomPathsFile(), sequencesToSave);
        zszlScriptMod.LOGGER.info(I18n.format("log.path.saved_all_from_editor"));
    }

    public static boolean renameCustomSequence(String oldName, String newName) {
        String name = newName == null ? "" : newName.trim();
        PathSequence sequence = customSequences.get(oldName);
        if (sequence == null || name.isEmpty() || name.length() > 128
                || (!oldName.equals(name) && hasSequence(name))) return false;
        if (oldName.equals(name)) return true;
        sequence.setName(name);
        notifyCustomSequenceRenamed(oldName, name, sequence);
        saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        return true;
    }

    public static void notifyCustomSequenceRenamed(String oldName, String newName, PathSequence targetSequence) {
        String normalizedOld = oldName == null ? "" : oldName.trim();
        String normalizedNew = newName == null ? "" : newName.trim();
        if (targetSequence == null || !targetSequence.isCustom() || normalizedOld.isEmpty() || normalizedNew.isEmpty()
                || normalizedOld.equals(normalizedNew)) {
            return;
        }

        LinkedHashMap<String, PathSequence> reordered = new LinkedHashMap<>();
        boolean inserted = false;
        for (Map.Entry<String, PathSequence> entry : customSequences.entrySet()) {
            PathSequence current = entry.getValue();
            boolean isTarget = current == targetSequence || normalizedOld.equals(entry.getKey());
            if (isTarget) {
                if (!inserted) {
                    reordered.put(normalizedNew, targetSequence);
                    inserted = true;
                }
                continue;
            }
            reordered.put(entry.getKey(), current);
        }

        if (!inserted) {
            return;
        }

        customSequences.clear();
        customSequences.putAll(reordered);
        MainUiLayoutManager.renameSequenceStats(normalizedOld, normalizedNew);
    }

    private static String jsonText(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            JsonElement element = obj.get(key);
            return element.isJsonPrimitive() ? element.getAsString() : element.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int jsonInt(JsonObject obj, int fallback, String... keys) {
        if (obj == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
                continue;
            }
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    /** MCP commits only after a successful atomic disk write. */
    public static void saveAllSequencesChecked(List<PathSequence> all) throws IOException {
        List<PathSequence> copies = new ArrayList<>();
        for (PathSequence source : all) {
            PathSequence copy = new PathSequence(source, true);
            copy.setCustom(true);
            copies.add(copy);
        }
        if (!saveSequencesToFile(getCustomPathsFile(), copies)) throw new IOException("Path sequence save failed");
        customSequences.clear();
        sequences.clear();
        for (PathSequence copy : copies) customSequences.put(copy.getName(), copy);
        // Main navigation reads the category registry, while the path editor
        // also derives categories from sequences. Keep both views in sync.
        for (PathSequence copy : copies) addCategory(copy.getCategory());
        GuiInventory.refreshGuiLists();
    }

    private static boolean saveSequencesToFile(Path path, List<PathSequence> sequenceList) {
        JsonObject root = new JsonObject();
        JsonArray sequencesArray = new JsonArray();
        for (PathSequence seq : sequenceList) {
            JsonObject seqObj = new JsonObject();
            seqObj.addProperty("name", seq.getName());
            seqObj.addProperty("closeGuiAfterStart", seq.shouldCloseGuiAfterStart());
            seqObj.addProperty("singleExecution", seq.isSingleExecution());
            seqObj.addProperty("nonInterruptingExecution", seq.isNonInterruptingExecution());
            seqObj.addProperty("lockConflictPolicy", seq.getLockConflictPolicy());
            seqObj.addProperty("category", seq.getCategory());
            if (seq.getSubCategory() != null && !seq.getSubCategory().trim().isEmpty()) {
                seqObj.addProperty("subCategory", seq.getSubCategory());
            }
            if (!seq.getTargetServer().isEmpty()) {
                seqObj.addProperty("targetServer", seq.getTargetServer());
            }
            seqObj.addProperty("note", seq.getNote());
            seqObj.addProperty("loopDelayTicks", seq.getLoopDelayTicks());

            JsonArray stepsArray = new JsonArray();
            for (PathStep step : seq.getSteps()) {
                JsonObject stepObj = new JsonObject();
                stepObj.add("goto", GSON.toJsonTree(step.getGotoPoint()));
                stepObj.addProperty("note", step.getNote());
                stepObj.addProperty("retryCount", step.getRetryCount());
                stepObj.addProperty("pathRetryTimeoutSeconds", step.getPathRetryTimeoutSeconds());
                stepObj.addProperty("arrivalToleranceBlocks", step.getArrivalToleranceBlocks());
                stepObj.addProperty("retryExhaustedPolicy", step.getRetryExhaustedPolicy());
                if (!step.getRetryExhaustedSequenceName().isEmpty()) {
                    stepObj.addProperty("retryExhaustedSequenceName", step.getRetryExhaustedSequenceName());
                    if (step.getRetryExhaustedStepIndex() > 0) {
                        stepObj.addProperty("retryExhaustedStepIndex", step.getRetryExhaustedStepIndex());
                    }
                    if (step.getRetryExhaustedActionIndex() > 0) {
                        stepObj.addProperty("retryExhaustedActionIndex", step.getRetryExhaustedActionIndex());
                    }
                }
                JsonArray actionsArray = new JsonArray();
                for (ActionData action : step.getActions()) {
                    JsonObject actionObj = new JsonObject();
                    actionObj.addProperty("type", action.type);
                    actionObj.add("params", action.params);
                    actionsArray.add(actionObj);
                }
                stepObj.add("actions", actionsArray);
                stepsArray.add(stepObj);
            }
            seqObj.add("steps", stepsArray);
            sequencesArray.add(seqObj);
        }
        root.add("sequences", sequencesArray);
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), ".paths-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            zszlScriptMod.LOGGER.info(I18n.format("log.path.save_count_to_file"), sequenceList.size(),
                    path.getFileName());
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.save_to_file_failed"), path, e);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    public static void deleteCustomSequence(String name) {
        boolean removed = customSequences.remove(name) != null;
        removed |= sequences.remove(name) != null;
        if (removed) {
            MainUiLayoutManager.removeSequenceStats(name);
            saveSequencesToFile(getCustomPathsFile(), getAllSequences());
            zszlScriptMod.LOGGER.info(I18n.format("log.path.deleted_custom"), name);
        }
    }

    public static int deleteCustomSequencesInCategory(String category) {
        String normalizedCategory = category == null ? "" : category.trim();
        if (normalizedCategory.isEmpty()) {
            return 0;
        }

        List<String> toRemove = new ArrayList<>();
        for (PathSequence sequence : customSequences.values()) {
            if (sequence != null && normalizedCategory.equals(sequence.getCategory())) {
                toRemove.add(sequence.getName());
            }
        }

        for (String name : toRemove) {
            customSequences.remove(name);
            MainUiLayoutManager.removeSequenceStats(name);
        }

        if (!toRemove.isEmpty()) {
            saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        }
        return toRemove.size();
    }

    public static int deleteCustomSequencesInSubCategory(String category, String subCategory) {
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedSubCategory = subCategory == null ? "" : subCategory.trim();
        if (normalizedCategory.isEmpty() || normalizedSubCategory.isEmpty()) {
            return 0;
        }

        List<String> toRemove = new ArrayList<>();
        for (PathSequence sequence : customSequences.values()) {
            if (sequence == null) {
                continue;
            }
            String sequenceSubCategory = sequence.getSubCategory() == null ? "" : sequence.getSubCategory().trim();
            if (normalizedCategory.equals(sequence.getCategory())
                    && normalizedSubCategory.equalsIgnoreCase(sequenceSubCategory)) {
                toRemove.add(sequence.getName());
            }
        }

        for (String name : toRemove) {
            customSequences.remove(name);
            MainUiLayoutManager.removeSequenceStats(name);
        }

        if (!toRemove.isEmpty()) {
            saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        }
        return toRemove.size();
    }

    public static boolean moveCustomSequenceTo(String sequenceName, String category, String subCategory) {
        PathSequence sequence = customSequences.get(sequenceName);
        String normalizedCategory = category == null ? "" : category.trim();
        if (sequence == null || normalizedCategory.isEmpty()) {
            return false;
        }

        addCategory(normalizedCategory);
        sequence.setCategory(normalizedCategory);
        sequence.setSubCategory(subCategory == null ? "" : subCategory.trim());
        saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        return true;
    }

    public static boolean moveCustomSequenceRelative(String sequenceName, String anchorSequenceName,
            boolean placeAfter) {
        String normalizedMove = sequenceName == null ? "" : sequenceName.trim();
        String normalizedAnchor = anchorSequenceName == null ? "" : anchorSequenceName.trim();
        if (normalizedMove.isEmpty() || normalizedAnchor.isEmpty() || normalizedMove.equals(normalizedAnchor)) {
            return false;
        }

        PathSequence movingSequence = customSequences.get(normalizedMove);
        PathSequence anchorSequence = customSequences.get(normalizedAnchor);
        if (movingSequence == null || anchorSequence == null) {
            return false;
        }

        LinkedHashMap<String, PathSequence> reordered = new LinkedHashMap<>();
        boolean inserted = false;
        for (Map.Entry<String, PathSequence> entry : customSequences.entrySet()) {
            String currentName = entry.getKey();
            if (normalizedMove.equals(currentName)) {
                continue;
            }

            if (!inserted && normalizedAnchor.equals(currentName) && !placeAfter) {
                reordered.put(normalizedMove, movingSequence);
                inserted = true;
            }

            reordered.put(currentName, entry.getValue());

            if (!inserted && normalizedAnchor.equals(currentName) && placeAfter) {
                reordered.put(normalizedMove, movingSequence);
                inserted = true;
            }
        }

        if (!inserted) {
            return false;
        }

        List<String> originalOrder = new ArrayList<>(customSequences.keySet());
        List<String> newOrder = new ArrayList<>(reordered.keySet());
        if (originalOrder.equals(newOrder)) {
            return false;
        }

        customSequences.clear();
        customSequences.putAll(reordered);
        saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        return true;
    }

    public static String copyCustomSequenceTo(String sequenceName, String category, String subCategory) {
        PathSequence source = customSequences.get(sequenceName);
        String normalizedCategory = category == null ? "" : category.trim();
        if (source == null || normalizedCategory.isEmpty()) {
            return "";
        }

        String baseName = source.getName();
        String copyName = baseName + " - 副本";
        int counter = 2;
        while (hasSequence(copyName)) {
            copyName = baseName + " - 副本" + counter++;
        }

        PathSequence copied = new PathSequence(source);
        copied.setName(copyName);
        copied.setCustom(true);
        copied.setCategory(normalizedCategory);
        copied.setSubCategory(subCategory == null ? "" : subCategory.trim());

        addCategory(normalizedCategory);
        customSequences.put(copyName, copied);
        saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        return copyName;
    }

    public static boolean createEmptyCustomSequence(String sequenceName, String category, String subCategory) {
        String normalizedName = sequenceName == null ? "" : sequenceName.trim();
        String normalizedCategory = category == null ? "" : category.trim();
        if (normalizedName.isEmpty() || normalizedCategory.isEmpty() || hasSequence(normalizedName)) {
            return false;
        }

        PathSequence sequence = new PathSequence(normalizedName);
        sequence.setCustom(true);
        sequence.setCategory(normalizedCategory);
        sequence.setSubCategory(subCategory == null ? "" : subCategory.trim());
        addCategory(normalizedCategory);
        customSequences.put(normalizedName, sequence);
        saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        return true;
    }

    public static Path exportCustomSequences(List<String> sequenceNames) {
        if (sequenceNames == null || sequenceNames.isEmpty()) {
            return null;
        }

        List<PathSequence> exportList = new ArrayList<>();
        for (String sequenceName : sequenceNames) {
            PathSequence sequence = customSequences.get(sequenceName);
            if (sequence != null) {
                exportList.add(new PathSequence(sequence));
            }
        }
        if (exportList.isEmpty()) {
            return null;
        }

        Path exportDir = ProfileManager.getCurrentProfileDir().resolve("exports");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path exportFile = exportDir.resolve("custom_sequences_export_" + timestamp + ".json");
        try {
            Files.createDirectories(exportDir);
            saveSequencesToFile(exportFile, exportList);
            return exportFile;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to export custom sequences to {}", exportFile, e);
            return null;
        }
    }

    public static void createSequenceFromRecording(String name, List<PathRecordingManager.RecordedStep> recordedSteps,
            String category) {
        if (name == null || name.trim().isEmpty() || recordedSteps == null || recordedSteps.isEmpty()) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.create_invalid_input"));
            return;
        }

        PathSequence sequence = new PathSequence(name);
        sequence.setCustom(true);
        sequence.setCategory(category);

        for (PathRecordingManager.RecordedStep recordedStep : recordedSteps) {
            if (recordedStep == null || recordedStep.playerPos == null) {
                continue;
            }
            PathStep step = new PathStep(new double[] {
                    recordedStep.playerPos.x,
                    recordedStep.playerPos.y,
                    recordedStep.playerPos.z
            });

            if (!recordedStep.getActions().isEmpty()) {
                for (ActionData action : recordedStep.getActions()) {
                    if (action != null) {
                        step.addAction(action);
                    }
                }
                sequence.addStep(step);
                continue;
            }

            JsonObject setViewParams = new JsonObject();
            setViewParams.addProperty("yaw", recordedStep.playerYaw);
            setViewParams.addProperty("pitch", recordedStep.playerPitch);
            step.addAction(new ActionData("setview", setViewParams));

            JsonObject delayParams1 = new JsonObject();
            delayParams1.addProperty("ticks", 16);
            step.addAction(new ActionData("delay", delayParams1));

            if (recordedStep.chestPos == null) {
                continue;
            }
            JsonObject rcbParams = new JsonObject();
            JsonArray posArray = new JsonArray();
            posArray.add(recordedStep.chestPos.getX());
            posArray.add(recordedStep.chestPos.getY());
            posArray.add(recordedStep.chestPos.getZ());
            rcbParams.add("pos", posArray);
            step.addAction(new ActionData("rightClickBlock", rcbParams));

            step.addAction(new ActionData("takeAllItems", new JsonObject()));

            JsonObject delayParams2 = new JsonObject();
            delayParams2.addProperty("ticks", 60);
            step.addAction(new ActionData("delay", delayParams2));

            sequence.addStep(step);
        }

        PathStep finalStep = new PathStep(new double[] { Double.NaN, Double.NaN, Double.NaN });
        finalStep.addAction(new ActionData("dropFilteredItems", new JsonObject()));

        JsonObject finalDelayParams = new JsonObject();
        finalDelayParams.addProperty("ticks", 180);
        finalStep.addAction(new ActionData("delay", finalDelayParams));

        sequence.addStep(finalStep);

        customSequences.put(name, sequence);
        saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        zszlScriptMod.LOGGER.info(I18n.format("log.path.created_from_recording"), name);
    }

    public static void runPathSequence(String sequenceName) {
        runPathSequenceInternal(sequenceName, false, null, null);
    }

    public static void runPathSequenceFromStep(String sequenceName, int startStepIndex) {
        runPathSequenceInternal(sequenceName, false, null, null, startStepIndex);
    }

    public static void runPathSequenceSingleStep(String sequenceName, int stepIndex) {
        runPathSequenceInternal(sequenceName, false, 1, null, stepIndex, 0, true);
    }

    public static void runPathSequenceFromAction(String sequenceName, int startStepIndex, int startActionIndex) {
        runPathSequenceInternal(sequenceName, false, null, null, startStepIndex, startActionIndex);
    }

    public static void runPathSequenceWithLoopCount(String sequenceName, int loopCount) {
        runPathSequenceInternal(sequenceName, false, loopCount, null);
    }

    public static void runPathSequenceOnce(String sequenceName) {
        int preservedLoopCount = GuiInventory.loopCount;
        try {
            runPathSequenceInternal(sequenceName, false, 1, null);
        } finally {
            GuiInventory.loopCount = preservedLoopCount;
        }
    }

    private static void runPathSequenceInternal(String sequenceName, boolean preserveCallerStack,
            Integer explicitLoopCount) {
        runPathSequenceInternal(sequenceName, preserveCallerStack, explicitLoopCount, null);
    }

    private static void runPathSequenceInternal(String sequenceName, boolean preserveCallerStack,
            Integer explicitLoopCount, Map<String, Object> initialSequenceVariables) {
        runPathSequenceInternal(sequenceName, preserveCallerStack, explicitLoopCount, initialSequenceVariables, 0);
    }

    private static void runPathSequenceInternal(String sequenceName, boolean preserveCallerStack,
            Integer explicitLoopCount, Map<String, Object> initialSequenceVariables, int startStepIndex) {
        runPathSequenceInternal(sequenceName, preserveCallerStack, explicitLoopCount, initialSequenceVariables,
                startStepIndex, 0);
    }

    private static void runPathSequenceInternal(String sequenceName, boolean preserveCallerStack,
            Integer explicitLoopCount, Map<String, Object> initialSequenceVariables, int startStepIndex,
            int startActionIndex) {
        runPathSequenceInternal(sequenceName, preserveCallerStack, explicitLoopCount, initialSequenceVariables,
                startStepIndex, startActionIndex, false);
    }

    private static void runPathSequenceInternal(String sequenceName, boolean preserveCallerStack,
            Integer explicitLoopCount, Map<String, Object> initialSequenceVariables, int startStepIndex,
            int startActionIndex, boolean stopAfterCurrentStep) {

        if (!preserveCallerStack) {
            clearRunSequenceCallStack();
        }

        if (!hasSequence(sequenceName)) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.unknown_sequence") + sequenceName);
            return;
        }

        PathSequence sequence = getSequence(sequenceName);
        if (sequence == null || sequence.getSteps().isEmpty()) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.invalid_or_empty_sequence") + sequenceName);
            return;
        }

        int effectiveStartStepIndex = clampStartStepIndex(sequence, startStepIndex);
        int effectiveStartActionIndex = clampStartActionIndex(sequence, effectiveStartStepIndex, startActionIndex);
        int effectiveLoopCount = explicitLoopCount != null
                ? explicitLoopCount
                : (sequence.isSingleExecution() ? 1 : GuiInventory.loopCount);

        if (!preserveCallerStack && sequence.isNonInterruptingExecution()) {
            if (effectiveLoopCount != 0) {
                PathSequenceEventListener.startBackgroundSequence(sequence,
                        effectiveLoopCount < 0 ? -1 : effectiveLoopCount,
                        initialSequenceVariables,
                        effectiveStartStepIndex,
                        effectiveStartActionIndex,
                        stopAfterCurrentStep);
            }
            return;
        }

        GuiInventory.loopCounter = 0;
        GuiInventory.isLooping = true;

        if (effectiveLoopCount != 0) {
            startNextLoopInternal(sequenceName, explicitLoopCount, initialSequenceVariables, effectiveStartStepIndex,
                    effectiveStartActionIndex, stopAfterCurrentStep);
        }
    }

    private static int clampStartStepIndex(PathSequence sequence, int startStepIndex) {
        if (sequence == null || sequence.getSteps() == null || sequence.getSteps().isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(startStepIndex, sequence.getSteps().size() - 1));
    }

    private static int clampStartActionIndex(PathSequence sequence, int startStepIndex, int startActionIndex) {
        if (sequence == null || sequence.getSteps() == null || sequence.getSteps().isEmpty()) {
            return 0;
        }
        PathStep step = sequence.getSteps().get(clampStartStepIndex(sequence, startStepIndex));
        int actionCount = step == null || step.getActions() == null ? 0 : step.getActions().size();
        return Math.max(0, Math.min(startActionIndex, Math.max(0, actionCount - 1)));
    }

    public static synchronized void clearRunSequenceCallStack() {
        runSequenceCallStack.clear();
    }

    static synchronized boolean hasPendingCaller(PathSequence sequence) {
        for (SequenceCallContext context : runSequenceCallStack) {
            if (context.callerSequence == sequence) return true;
        }
        return false;
    }

    public static boolean resumeCallerSequenceAfterAction(EntityPlayerSP player) {
        return resumeCallerSequenceAfterAction(player, false);
    }

    public static boolean resumeCallerSequenceAfterAction(EntityPlayerSP player, boolean preserveCurrentNavigation) {
        SequenceCallContext context;
        synchronized (PathSequenceManager.class) {
            if (runSequenceCallStack.isEmpty()) {
                return false;
            }
            context = runSequenceCallStack.pop();
        }

        PathSequence sequence = getSequence(context.sequenceName);
        // Unsaved recording previews have no registry entry to resolve after a child sequence finishes.
        if (sequence == null && PathRecordingPlayback.isSnapshot(context.callerSequence)) {
            sequence = context.callerSequence;
        }
        if (sequence == null || sequence.getSteps().isEmpty() || context.snapshot == null) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 无法恢复调用方序列: {}", context.sequenceName);
            return false;
        }

        GuiInventory.loopCount = context.loopCount;
        GuiInventory.loopCounter = context.loopCounter;
        GuiInventory.isLooping = context.isLooping;

        boolean resumed = PathSequenceEventListener.instance.resumeFromSnapshot(sequence, context.snapshot,
                preserveCurrentNavigation);
        if (!resumed) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 恢复调用方序列失败: {}", context.sequenceName);
            return false;
        }

        zszlScriptMod.LOGGER.info("[run_sequence] 已恢复调用方序列: {}", context.sequenceName);
        return true;
    }

    private static void runSequenceFromAction(String targetSequenceName, String actionUuid,
            boolean intervalExecutionMode, int executeEveryCount, boolean backgroundExecution,
            EntityPlayerSP player) {
        if (!intervalExecutionMode || executeEveryCount <= 1) {
            setRunSequenceCurrentCount(actionUuid, 0);
            executeRunSequenceFromAction(targetSequenceName, player, backgroundExecution);
            return;
        }

        int currentCount = getRunSequenceCurrentCount(actionUuid, executeEveryCount);
        int nextCount = currentCount + 1;
        if (nextCount < executeEveryCount) {
            setRunSequenceCurrentCount(actionUuid, nextCount);
            return;
        }

        boolean executed = executeRunSequenceFromAction(targetSequenceName, player, backgroundExecution);
        if (executed) {
            setRunSequenceCurrentCount(actionUuid, 0);
        } else {
            setRunSequenceCurrentCount(actionUuid, Math.max(0, executeEveryCount - 1));
        }
    }

    private static void runTemplateFromAction(String templateName, String actionUuid,
            String paramsText, boolean intervalExecutionMode, int executeEveryCount, boolean backgroundExecution,
            EntityPlayerSP player) {
        if (!intervalExecutionMode || executeEveryCount <= 1) {
            setRunSequenceCurrentCount(actionUuid, 0);
            executeTemplateFromAction(templateName, paramsText, player, backgroundExecution);
            return;
        }

        int currentCount = getRunSequenceCurrentCount(actionUuid, executeEveryCount);
        int nextCount = currentCount + 1;
        if (nextCount < executeEveryCount) {
            setRunSequenceCurrentCount(actionUuid, nextCount);
            return;
        }

        boolean executed = executeTemplateFromAction(templateName, paramsText, player, backgroundExecution);
        if (executed) {
            setRunSequenceCurrentCount(actionUuid, 0);
        } else {
            setRunSequenceCurrentCount(actionUuid, Math.max(0, executeEveryCount - 1));
        }
    }

    private static boolean executeRunSequenceFromAction(String targetSequenceName, EntityPlayerSP player,
            boolean backgroundExecution) {
        return executeRunSequenceFromAction(targetSequenceName, player, backgroundExecution, null);
    }

    private static boolean executeRunSequenceFromAction(String targetSequenceName, EntityPlayerSP player,
            boolean backgroundExecution, Map<String, Object> initialSequenceVariables) {
        if (targetSequenceName == null || targetSequenceName.trim().isEmpty()) {
            return false;
        }

        String target = targetSequenceName.trim();
        if (!hasSequence(target)) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 目标序列不存在: {}", target);
            if (player != null) {
                player.sendMessage(
                        new TextComponentString(I18n.format("msg.path.run_sequence.target_not_found", target)));
            }
            return false;
        }

        PathSequence targetSequence = getSequence(target);
        if (targetSequence == null || targetSequence.getSteps().isEmpty()) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 目标序列为空或无效: {}", target);
            return false;
        }

        String caller = null;
        PathSequenceEventListener listener = PathSequenceEventListener.getCurrentExecutionContext();
        if ((listener == null || !listener.isTracking() || listener.currentSequence == null)
                && PathSequenceEventListener.instance.isTracking()
                && PathSequenceEventListener.instance.currentSequence != null) {
            listener = PathSequenceEventListener.instance;
        }
        if (listener != null && listener.currentSequence != null) {
            caller = listener.currentSequence.getName();
        }

        boolean effectiveBackgroundExecution = backgroundExecution
                || (listener != null && listener.isBackgroundRunner())
                || (targetSequence != null && targetSequence.isNonInterruptingExecution());

        if (caller != null && !canInvokeSequence(caller, target)) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 拒绝循环调用: {} -> {}", caller, target);
            if (player != null) {
                player.sendMessage(
                        new TextComponentString(I18n.format("msg.path.run_sequence.cycle_blocked", caller, target)));
            }
            return false;
        }

        if (effectiveBackgroundExecution) {
            return PathSequenceEventListener.startBackgroundSequence(targetSequence, 0, initialSequenceVariables);
        }

        if (listener == PathSequenceEventListener.instance
                && listener.isTracking()
                && listener.currentSequence != null) {
            PathSequenceEventListener.ProgressSnapshot snapshot = listener.captureProgressSnapshot();
            if (snapshot != null) {
                PathSequenceEventListener.ProgressSnapshot resumeSnapshot = new PathSequenceEventListener.ProgressSnapshot(
                        snapshot.getSequenceName(),
                        snapshot.getStepIndex(),
                        Math.max(0, snapshot.getActionIndex() + 1),
                        snapshot.isAtTarget(),
                        snapshot.getRemainingLoops(),
                        0,
                        false,
                        snapshot.getStatus(),
                        snapshot.getVariableSnapshot(),
                        snapshot.getStepRetryUsed(),
                        snapshot.isNoStopNavigationOnCompletion());

                synchronized (PathSequenceManager.class) {
                    runSequenceCallStack.push(new SequenceCallContext(
                            listener.currentSequence,
                            resumeSnapshot,
                            GuiInventory.loopCount,
                            GuiInventory.loopCounter,
                            GuiInventory.isLooping));
                }
            }
        }

        runPathSequenceInternal(target, true, null, initialSequenceVariables);
        return true;
    }

    public static boolean executeSequenceByConfiguredMode(String targetSequenceName, EntityPlayerSP player,
            String callerSequenceName, Map<String, Object> initialSequenceVariables) {
        return executeSequenceByConfiguredMode(targetSequenceName, player, callerSequenceName,
                initialSequenceVariables, 0, 0);
    }

    public static boolean executeSequenceByConfiguredMode(String targetSequenceName, EntityPlayerSP player,
            String callerSequenceName, Map<String, Object> initialSequenceVariables, int startStepIndex,
            int startActionIndex) {
        if (targetSequenceName == null || targetSequenceName.trim().isEmpty()) {
            if (player != null) {
                player.sendMessage(new TextComponentString("§c失败后执行序列未设置目标序列。"));
            }
            return false;
        }

        String target = targetSequenceName.trim();
        if (!hasSequence(target)) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 目标序列不存在: {}", target);
            if (player != null) {
                player.sendMessage(
                        new TextComponentString(I18n.format("msg.path.run_sequence.target_not_found", target)));
            }
            return false;
        }

        PathSequence targetSequence = getSequence(target);
        if (targetSequence == null || targetSequence.getSteps().isEmpty()) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 目标序列为空或无效: {}", target);
            if (player != null) {
                player.sendMessage(new TextComponentString("§c目标序列为空或无有效步骤: " + target));
            }
            return false;
        }

        String caller = callerSequenceName == null ? "" : callerSequenceName.trim();
        if (!caller.isEmpty() && !canInvokeSequence(caller, target)) {
            zszlScriptMod.LOGGER.warn("[run_sequence] 拒绝循环调用: {} -> {}", caller, target);
            if (player != null) {
                player.sendMessage(
                        new TextComponentString(I18n.format("msg.path.run_sequence.cycle_blocked", caller, target)));
            }
            return false;
        }

        if (targetSequence.isNonInterruptingExecution()) {
            return PathSequenceEventListener.startBackgroundSequence(targetSequence, 0, initialSequenceVariables,
                    startStepIndex, startActionIndex);
        }

        runPathSequenceInternal(target, false, 1, initialSequenceVariables, startStepIndex, startActionIndex);
        return true;
    }

    private static boolean executeTemplateFromAction(String templateName, String paramsText, EntityPlayerSP player,
            boolean backgroundExecution) {
        LegacyActionTemplateManager.ResolvedTemplateCall call = LegacyActionTemplateManager.resolveCall(templateName,
                paramsText);
        if (call == null || call.getSequenceName().trim().isEmpty()) {
            if (player != null) {
                player.sendMessage(new TextComponentString("§c模板不存在或模板目标序列无效: " + templateName));
            }
            return false;
        }
        return executeRunSequenceFromAction(call.getSequenceName(), player, backgroundExecution, call.getVariables());
    }

    private static void stopCurrentSequenceFromAction(String targetScope) {
        if ("background".equalsIgnoreCase(targetScope)) {
            PathSequenceEventListener.stopBackgroundSequencesByAction();
        } else {
            PathSequenceEventListener.stopForegroundSequenceByAction();
        }
    }

    private static void controlSequenceFromAction(String targetScope, String operation) {
        boolean resume = "resume".equalsIgnoreCase(operation);
        if ("background".equalsIgnoreCase(targetScope)) {
            if (resume) {
                PathSequenceEventListener.resumeBackgroundSequencesByAction();
            } else {
                PathSequenceEventListener.pauseBackgroundSequencesByAction();
            }
            return;
        }
        if (resume) {
            PathSequenceEventListener.resumeForegroundSequenceByAction();
        } else {
            PathSequenceEventListener.pauseForegroundSequenceByAction();
        }
    }

    private static boolean canInvokeSequence(String caller, String target) {
        if (caller == null || target == null) {
            return true;
        }
        if (caller.equalsIgnoreCase(target)) {
            return false;
        }
        // 若 target 已能到达 caller，则 caller -> target 会形成环
        return !canReachSequence(target, caller, new HashSet<String>());
    }

    private static boolean canReachSequence(String from, String target, Set<String> visited) {
        if (from == null || target == null) {
            return false;
        }
        if (from.equalsIgnoreCase(target)) {
            return true;
        }
        String key = from.toLowerCase(Locale.ROOT);
        if (!visited.add(key)) {
            return false;
        }

        PathSequence sequence = getSequence(from);
        if (sequence == null) {
            return false;
        }

        for (PathStep step : sequence.getSteps()) {
            if (step == null || step.getActions() == null) {
                continue;
            }
            for (ActionData action : step.getActions()) {
                if (action == null || action.type == null || action.params == null) {
                    continue;
                }
                if (!"run_sequence".equalsIgnoreCase(action.type) || !action.params.has("sequenceName")) {
                    if ("run_template".equalsIgnoreCase(action.type) && action.params.has("templateName")) {
                        String templateTarget = LegacyActionTemplateManager
                                .resolveTemplateTargetSequence(action.params.get("templateName").getAsString());
                        if (templateTarget != null
                                && !templateTarget.trim().isEmpty()
                                && canReachSequence(templateTarget.trim(), target, visited)) {
                            return true;
                        }
                    }
                    continue;
                }
                String next = action.params.get("sequenceName").getAsString();
                if (next == null || next.trim().isEmpty()) {
                    continue;
                }
                if (canReachSequence(next.trim(), target, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void startNextLoop(String sequenceName) {
        startNextLoopInternal(sequenceName, null, null);
    }

    public static void startNextLoopWithVariables(String sequenceName, Map<String, Object> initialSequenceVariables) {
        startNextLoopInternal(sequenceName, null, initialSequenceVariables);
    }

    public static void startNextLoopWithVariables(String sequenceName, Map<String, Object> initialSequenceVariables,
            int loopCount) {
        startNextLoopInternal(sequenceName, loopCount, initialSequenceVariables);
    }

    private static void startNextLoopInternal(String sequenceName, Integer explicitLoopCount) {
        startNextLoopInternal(sequenceName, explicitLoopCount, null);
    }

    private static void startNextLoopInternal(String sequenceName, Integer explicitLoopCount,
            Map<String, Object> initialSequenceVariables) {
        startNextLoopInternal(sequenceName, explicitLoopCount, initialSequenceVariables, 0);
    }

    private static void startNextLoopInternal(String sequenceName, Integer explicitLoopCount,
            Map<String, Object> initialSequenceVariables, int startStepIndex) {
        startNextLoopInternal(sequenceName, explicitLoopCount, initialSequenceVariables, startStepIndex, 0);
    }

    private static void startNextLoopInternal(String sequenceName, Integer explicitLoopCount,
            Map<String, Object> initialSequenceVariables, int startStepIndex, int startActionIndex) {
        startNextLoopInternal(sequenceName, explicitLoopCount, initialSequenceVariables, startStepIndex,
                startActionIndex, false);
    }

    private static void startNextLoopInternal(String sequenceName, Integer explicitLoopCount,
            Map<String, Object> initialSequenceVariables, int startStepIndex, int startActionIndex,
            boolean stopAfterCurrentStep) {
        if (!GuiInventory.isLooping) {
            zszlScriptMod.LOGGER.info(I18n.format("log.path.looping_false_skip"));
            return;
        }

        PathSequence sequence = getSequence(sequenceName);

        int effectiveStartStepIndex = clampStartStepIndex(sequence, startStepIndex);
        int effectiveStartActionIndex = clampStartActionIndex(sequence, effectiveStartStepIndex, startActionIndex);
        double[] firstTarget = sequence.getSteps().get(effectiveStartStepIndex).getGotoPoint();

        if (!Double.isNaN(firstTarget[0])) {
            PathSequenceEventListener.instance.prepareForSequenceStartDispatch("startNextLoopInternal:" + sequenceName);
            EmbeddedNavigationHandler.INSTANCE.startGoto(firstTarget[0], firstTarget[1], firstTarget[2], true);
        } else {
            zszlScriptMod.LOGGER.warn(I18n.format("log.path.first_step_no_target"), sequenceName);
        }

        GuiInventory.loopCounter++;

        String loopInfo = I18n.format("path.loop.info", GuiInventory.loopCounter);
        int effectiveLoopCount = explicitLoopCount != null
                ? explicitLoopCount
                : ((sequence != null && sequence.isSingleExecution()) ? 1 : GuiInventory.loopCount);

        if (effectiveLoopCount > 0) {
            loopInfo += "/" + effectiveLoopCount;
        } else if (effectiveLoopCount < 0) {
            loopInfo += "/" + I18n.format("path.loop.infinite");
        }

        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    I18n.format("msg.path.sequence_start_loop", sequenceName, GuiInventory.loopCounter)));
        }
        PathSequenceEventListener.instance.setStatus(sequenceName + " - " + loopInfo);

        PathSequenceEventListener.instance.startTracking(sequence,
                effectiveLoopCount < 0 ? -1 : effectiveLoopCount + 1 - GuiInventory.loopCounter,
                initialSequenceVariables,
                effectiveStartStepIndex,
                effectiveStartActionIndex,
                stopAfterCurrentStep);

        PathSequenceEventListener.instance.resume();
        zszlScriptMod.LOGGER.info(I18n.format("log.path.start_running") + sequenceName);
    }

    public static List<String> getAllCategories() {
        return new ArrayList<>(categories);
    }

    public static List<String> getVisibleCategories() {
        LinkedHashMap<String, String> visibleCategories = new LinkedHashMap<>();
        for (String category : categories) {
            String normalized = normalizeCategoryName(category);
            if (normalized.isEmpty() || isBuiltinCategory(normalized) || isCategoryHiddenNormalized(normalized)) {
                continue;
            }
            visibleCategories.putIfAbsent(categoryKey(normalized), normalized);
        }
        return new ArrayList<>(visibleCategories.values());
    }

    public static List<String> getHiddenCategories() {
        return categories.stream()
                .filter(cat -> !builtinCategoryName().equals(cat) && hiddenCategories.contains(cat))
                .collect(Collectors.toList());
    }

    public static boolean isCategoryHidden(String category) {
        return category != null && hiddenCategories.contains(category);
    }

    public static void setCategoryHidden(String category, boolean hidden) {
        if (category == null || category.trim().isEmpty()) {
            return;
        }
        if (hidden) {
            hiddenCategories.add(category);
        } else {
            hiddenCategories.remove(category);
        }
        saveCategories();
    }

    public static List<PathSequence> getAllVisibleSequences() {
        return getAllSequences().stream()
                .filter(seq -> seq != null && !hiddenCategories.contains(seq.getCategory()))
                .collect(Collectors.toList());
    }

    public static synchronized void addCategory(String name) {
        String normalized = normalizeCategoryName(name);
        if (normalized.isEmpty() || isBuiltinCategory(normalized)) {
            return;
        }
        String reserved = reservedCategoryName(normalized);
        if (!reserved.isEmpty()) {
            normalized = reserved;
        }
        if (findCategoryIgnoreCase(normalized).isEmpty()) {
            categories.add(normalized);
            saveCategories();
        }
    }

    public static void deleteCategory(String name) {
        if (categories.contains(name) && !defaultCategoryName().equals(name) && !builtinCategoryName().equals(name)) {
            categories.remove(name);
            hiddenCategories.remove(name);
            deleteCustomSequencesInCategory(name);
            MainUiLayoutManager.removeCategory(name);
            saveCategories();
        }
    }

    public static void renameCategory(String oldName, String newName) {
        if (categories.contains(oldName) && newName != null && !newName.trim().isEmpty()
                && !categories.contains(newName)) {
            int index = categories.indexOf(oldName);
            categories.set(index, newName);
            if (hiddenCategories.remove(oldName)) {
                hiddenCategories.add(newName);
            }
            getAllSequences().forEach(seq -> {
                if (oldName.equals(seq.getCategory())) {
                    seq.setCategory(newName);
                }
            });
            saveAllSequences(getAllSequences());
            MainUiLayoutManager.renameCategory(oldName, newName);
            saveCategories();
        }
    }

    public static boolean moveCategory(String categoryToMove, String anchorCategory, boolean placeAfter) {
        String normalizedMove = categoryToMove == null ? "" : categoryToMove.trim();
        String normalizedAnchor = anchorCategory == null ? "" : anchorCategory.trim();
        if (normalizedMove.isEmpty() || normalizedAnchor.isEmpty()
                || normalizedMove.equals(normalizedAnchor)
                || defaultCategoryName().equals(normalizedMove)
                || builtinCategoryName().equals(normalizedMove)
                || defaultCategoryName().equals(normalizedAnchor)
                || builtinCategoryName().equals(normalizedAnchor)) {
            return false;
        }

        int moveIndex = categories.indexOf(normalizedMove);
        int anchorIndex = categories.indexOf(normalizedAnchor);
        if (moveIndex < 0 || anchorIndex < 0 || moveIndex == anchorIndex) {
            return false;
        }

        categories.remove(moveIndex);
        if (moveIndex < anchorIndex) {
            anchorIndex--;
        }
        int insertIndex = placeAfter ? anchorIndex + 1 : anchorIndex;
        insertIndex = Math.max(0, Math.min(insertIndex, categories.size()));
        categories.add(insertIndex, normalizedMove);
        saveCategories();
        return true;
    }

    public static boolean restoreCustomCategoryOrder() {
        List<String> originalOrder = new ArrayList<>(categories);
        LinkedHashSet<String> orderedCategories = new LinkedHashSet<>();
        orderedCategories.add(defaultCategoryName());
        orderedCategories.add(builtinCategoryName());

        for (PathSequence sequence : getAllSequences()) {
            if (sequence == null) {
                continue;
            }
            String category = sequence.getCategory() == null ? "" : sequence.getCategory().trim();
            if (!category.isEmpty()) {
                orderedCategories.add(category);
            }
        }

        for (String category : originalOrder) {
            if (category != null && !category.trim().isEmpty()) {
                orderedCategories.add(category);
            }
        }

        List<String> restoredOrder = new ArrayList<>(orderedCategories);
        if (restoredOrder.equals(originalOrder)) {
            return false;
        }
        categories.clear();
        categories.addAll(restoredOrder);
        saveCategories();
        return true;
    }

    public static boolean sortCustomCategoriesAlphabetically() {
        List<String> originalOrder = new ArrayList<>(categories);
        List<String> sortedCustomCategories = new ArrayList<>();
        for (String category : categories) {
            if (!defaultCategoryName().equals(category) && !builtinCategoryName().equals(category)) {
                sortedCustomCategories.add(category);
            }
        }

        sortedCustomCategories.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> reorderedCategories = new ArrayList<>();
        if (categories.contains(defaultCategoryName())) {
            reorderedCategories.add(defaultCategoryName());
        }
        if (categories.contains(builtinCategoryName())) {
            reorderedCategories.add(builtinCategoryName());
        }
        reorderedCategories.addAll(sortedCustomCategories);

        if (reorderedCategories.equals(originalOrder)) {
            return false;
        }
        categories.clear();
        categories.addAll(reorderedCategories);
        saveCategories();
        return true;
    }

    private static void loadCategories() {
        categories = new ArrayList<>(Collections.singletonList(normalizeCategoryName(defaultCategoryName())));
        hiddenCategories = new HashSet<>();
        boolean changed = false;
        Path categoriesFile = getCategoriesFile();
        if (Files.exists(categoriesFile)) {
            try (BufferedReader reader = Files.newBufferedReader(categoriesFile, StandardCharsets.UTF_8)) {
                JsonElement root = new JsonParser().parse(reader);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();

                    if (obj.has("categories") && obj.get("categories").isJsonArray()) {
                        Type listType = new TypeToken<ArrayList<String>>() {
                        }.getType();
                        List<String> loaded = GSON.fromJson(obj.get("categories"), listType);
                        if (loaded != null) {
                            Set<String> loadedKeys = new HashSet<>();
                            for (String cat : loaded) {
                                changed |= addLoadedCategory(cat, loadedKeys);
                            }
                        }
                    }

                    if (obj.has("hiddenCategories") && obj.get("hiddenCategories").isJsonArray()) {
                        Type listType = new TypeToken<ArrayList<String>>() {
                        }.getType();
                        List<String> hidden = GSON.fromJson(obj.get("hiddenCategories"), listType);
                        if (hidden != null) {
                            for (String category : hidden) {
                                changed |= addLoadedHiddenCategory(category);
                            }
                        }
                    }
                } else if (root != null && root.isJsonArray()) {
                    Type listType = new TypeToken<ArrayList<String>>() {
                    }.getType();
                    List<String> loaded = GSON.fromJson(root, listType);
                    if (loaded != null) {
                        Set<String> loadedKeys = new HashSet<>();
                        for (String cat : loaded) {
                            changed |= addLoadedCategory(cat, loadedKeys);
                        }
                    }
                }
            } catch (IOException e) {
                zszlScriptMod.LOGGER.error(I18n.format("log.path.load_categories_failed"), e);
            }
        }
        if (changed) {
            saveCategories();
        }
    }

    private static String normalizeCategoryName(String category) {
        return category == null ? "" : category.trim();
    }

    private static String categoryKey(String category) {
        return normalizeCategoryName(category).toLowerCase(Locale.ROOT);
    }

    private static boolean isBuiltinCategory(String category) {
        return builtinCategoryName().equalsIgnoreCase(normalizeCategoryName(category))
                || "path.category.builtin".equalsIgnoreCase(normalizeCategoryName(category));
    }

    private static String reservedCategoryName(String category) {
        String normalized = normalizeCategoryName(category);
        if (defaultCategoryName().equalsIgnoreCase(normalized)
                || "path.category.default".equalsIgnoreCase(normalized)) {
            return normalizeCategoryName(defaultCategoryName());
        }
        if (isBuiltinCategory(normalized)) {
            return normalizeCategoryName(builtinCategoryName());
        }
        return "";
    }

    private static String findCategoryIgnoreCase(String category) {
        String normalized = normalizeCategoryName(category);
        if (normalized.isEmpty()) {
            return "";
        }
        for (String existing : categories) {
            if (normalized.equalsIgnoreCase(normalizeCategoryName(existing))) {
                return existing;
            }
        }
        return "";
    }

    private static boolean addLoadedCategory(String rawCategory, Set<String> loadedKeys) {
        String normalized = normalizeCategoryName(rawCategory);
        if (normalized.isEmpty() || isBuiltinCategory(normalized)) {
            return rawCategory != null && !normalized.isEmpty();
        }

        String reserved = reservedCategoryName(normalized);
        if (!reserved.isEmpty()) {
            normalized = reserved;
        }
        boolean duplicateInFile = !loadedKeys.add(categoryKey(normalized));
        String existing = findCategoryIgnoreCase(normalized);
        if (existing.isEmpty()) {
            categories.add(normalized);
        }
        return duplicateInFile || !normalized.equals(rawCategory) || (!existing.isEmpty() && !normalized.equals(existing));
    }

    private static boolean addLoadedHiddenCategory(String rawCategory) {
        String normalized = normalizeCategoryName(rawCategory);
        if (normalized.isEmpty() || isBuiltinCategory(normalized)) {
            return rawCategory != null && !normalized.isEmpty();
        }
        String reserved = reservedCategoryName(normalized);
        if (!reserved.isEmpty()) {
            normalized = reserved;
        }
        String existing = findCategoryIgnoreCase(normalized);
        String canonical = existing.isEmpty() ? normalized : existing;
        boolean duplicate = !hiddenCategories.add(canonical);
        return duplicate || !canonical.equals(rawCategory);
    }

    private static boolean isCategoryHiddenNormalized(String category) {
        String normalized = normalizeCategoryName(category);
        for (String hidden : hiddenCategories) {
            if (normalized.equalsIgnoreCase(normalizeCategoryName(hidden))) {
                return true;
            }
        }
        return false;
    }

    private static void normalizeLoadedCategoryReferences() {
        boolean categoryChanged = false;
        boolean sequenceChanged = false;
        for (PathSequence sequence : getAllSequences()) {
            if (sequence == null) {
                continue;
            }
            String rawCategory = sequence.getCategory();
            String normalized = normalizeCategoryName(rawCategory);
            if (normalized.isEmpty()) {
                normalized = normalizeCategoryName(defaultCategoryName());
            }

            String reserved = reservedCategoryName(normalized);
            String canonical = !reserved.isEmpty() ? reserved : findCategoryIgnoreCase(normalized);
            if (canonical.isEmpty()) {
                canonical = normalized;
                categories.add(canonical);
                categoryChanged = true;
            }
            if (!canonical.equals(rawCategory)) {
                sequence.setCategory(canonical);
                sequenceChanged = true;
            }
        }

        if (categoryChanged) {
            saveCategories();
        }
        if (sequenceChanged) {
            saveSequencesToFile(getCustomPathsFile(), new ArrayList<>(customSequences.values()));
        }
    }

    public static void saveCategories() {
        try {
            Path categoriesFile = getCategoriesFile();
            Files.createDirectories(categoriesFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(categoriesFile, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.add("categories", GSON.toJsonTree(categories));
                root.add("hiddenCategories", GSON.toJsonTree(hiddenCategories));
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.path.save_categories_failed"), e);
        }
    }

    public static List<PathSequence> getAllSequences() {
        List<PathSequence> all = new ArrayList<>();
        all.addAll(customSequences.values());
        all.addAll(sequences.values());
        return all;
    }
}
