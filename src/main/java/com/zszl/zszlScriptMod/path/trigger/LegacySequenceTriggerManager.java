package com.zszl.zszlScriptMod.path.trigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LegacySequenceTriggerManager {
    public static final String CATEGORY_UNGROUPED = "未分组";

    public static final String TRIGGER_GUI_OPEN = "gui_open";
    public static final String TRIGGER_GUI_CLOSE = "gui_close";
    public static final String TRIGGER_CHAT = "chat";
    public static final String TRIGGER_PACKET = "packet";
    public static final String TRIGGER_TITLE = "title";
    public static final String TRIGGER_ACTIONBAR = "actionbar";
    public static final String TRIGGER_SCOREBOARD_CHANGED = "scoreboard_changed";
    public static final String TRIGGER_BOSSBAR = "bossbar";
    public static final String TRIGGER_KEY_INPUT = "key_input";
    public static final String TRIGGER_PLAYER_IDLE = "player_idle";
    public static final String TRIGGER_TIMER = "timer";
    public static final String TRIGGER_HP_LOW = "hp_low";
    public static final String TRIGGER_FOOD_LOW = "food_low";
    public static final String TRIGGER_DEATH = "death";
    public static final String TRIGGER_RESPAWN = "respawn";
    public static final String TRIGGER_PLAYER_HURT = "player_hurt";
    public static final String TRIGGER_ATTACK_ENTITY = "attack_entity";
    public static final String TRIGGER_TARGET_KILL = "target_kill";
    public static final String TRIGGER_WORLD_CHANGED = "world_changed";
    public static final String TRIGGER_AREA_CHANGED = "area_changed";
    public static final String TRIGGER_INVENTORY_CHANGED = "inventory_changed";
    public static final String TRIGGER_INVENTORY_FULL = "inventory_full";
    public static final String TRIGGER_ENTITY_NEARBY = "entity_nearby";
    public static final String TRIGGER_PLAYER_LIST = "player_list";
    public static final String TRIGGER_ITEM_PICKUP = "item_pickup";
    public static final String TRIGGER_SERVER_CONNECT = "server_connect";
    public static final String TRIGGER_SERVER_DISCONNECT = "server_disconnect";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<TriggerRule> RULES = new CopyOnWriteArrayList<>();
    private static final List<String> CATEGORIES = new CopyOnWriteArrayList<>();
    private static final Map<String, Long> LAST_TRIGGER_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> ENABLED_RULE_COUNT_BY_TRIGGER = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_DEBUG_EVENT_STATE = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_DEBUG_RULE_STATE = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    public static final class RuleEditModel {
        public String name = "";
        public String category = CATEGORY_UNGROUPED;
        public boolean enabled = true;
        public String triggerType = TRIGGER_GUI_OPEN;
        public String contains = "";
        public JsonObject params = new JsonObject();
        public String sequenceName = "";
        public int executionCount = 1;
        public boolean backgroundExecution = false;
        public int cooldownMs = 1000;
        public String note = "";
    }

    private static final class ConfigRoot {
        private List<String> categories = new ArrayList<>();
        private List<TriggerRule> rules = new ArrayList<>();
    }

    private static final class TriggerRule {
        private String name = "";
        private String category = CATEGORY_UNGROUPED;
        private boolean enabled = true;
        private String triggerType = TRIGGER_GUI_OPEN;
        private String contains = "";
        private JsonObject params = new JsonObject();
        private String sequenceName = "";
        private int executionCount = 1;
        private boolean backgroundExecution = false;
        private int cooldownMs = 1000;
        private String note = "";
    }

    private static final class RuleEvaluation {
        private final boolean matched;
        private final String detail;
        private final String debugStateKey;

        private RuleEvaluation(boolean matched, String detail, String debugStateKey) {
            this.matched = matched;
            this.detail = detail == null ? "" : detail;
            this.debugStateKey = debugStateKey == null ? "" : debugStateKey;
        }

        private static RuleEvaluation matched(String detail, String debugStateKey) {
            return new RuleEvaluation(true, detail, debugStateKey);
        }

        private static RuleEvaluation missed(String detail, String debugStateKey) {
            return new RuleEvaluation(false, detail, debugStateKey);
        }
    }

    private LegacySequenceTriggerManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        reloadRules();
        initialized = true;
    }

    public static synchronized void reloadRules() {
        RULES.clear();
        CATEGORIES.clear();
        LAST_TRIGGER_TIMES.clear();
        ENABLED_RULE_COUNT_BY_TRIGGER.clear();
        LAST_DEBUG_EVENT_STATE.clear();
        LAST_DEBUG_RULE_STATE.clear();
        Path path = getConfigPath();
        ensureConfigExists(path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
            if (root == null || root.rules == null) {
                ensureCategoriesSynced();
                return;
            }
            if (root.categories != null) {
                for (String category : root.categories) {
                    CATEGORIES.add(normalizeCategory(category));
                }
            }
            for (TriggerRule rule : root.rules) {
                if (rule == null || isBlank(rule.sequenceName)) {
                    continue;
                }
                rule.category = normalizeCategory(rule.category);
                rule.triggerType = normalizeTriggerType(rule.triggerType);
                rule.executionCount = normalizeExecutionCount(rule.executionCount);
                rule.cooldownMs = Math.max(0, rule.cooldownMs);
                RULES.add(rule);
                if (rule.enabled) {
                    ENABLED_RULE_COUNT_BY_TRIGGER.merge(rule.triggerType, 1, Integer::sum);
                }
            }
            ensureCategoriesSynced();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTrigger] 加载规则失败", e);
            ensureCategoriesSynced();
        }
    }

    public static boolean hasRulesForTrigger(String triggerType) {
        initialize();
        String normalizedType = normalizeTriggerType(triggerType);
        if (normalizedType.isEmpty()) {
            return false;
        }
        return ENABLED_RULE_COUNT_BY_TRIGGER.getOrDefault(normalizedType, 0) > 0;
    }

    public static boolean isDebugEnabled() {
        return ModConfig.isDebugFlagEnabled(DebugModule.TRIGGER_RULES);
    }

    public static synchronized List<RuleEditModel> getRuleModels() {
        initialize();
        List<RuleEditModel> models = new ArrayList<>();
        for (TriggerRule rule : RULES) {
            if (rule == null) {
                continue;
            }
            RuleEditModel model = new RuleEditModel();
            model.name = safe(rule.name);
            model.category = normalizeCategory(rule.category);
            model.enabled = rule.enabled;
            model.triggerType = safe(rule.triggerType);
            model.contains = safe(rule.contains);
            model.params = copyJson(rule.params);
            model.sequenceName = safe(rule.sequenceName);
            model.executionCount = normalizeExecutionCount(rule.executionCount);
            model.backgroundExecution = rule.backgroundExecution;
            model.cooldownMs = Math.max(0, rule.cooldownMs);
            model.note = safe(rule.note);
            models.add(model);
        }
        return models;
    }

    public static synchronized void saveRuleModels(List<RuleEditModel> models) {
        saveRuleModels(models, null);
    }

    public static synchronized void saveRuleModels(List<RuleEditModel> models, List<String> categories) {
        Path path = getConfigPath();
        ensureConfigExists(path);
        ConfigRoot root = new ConfigRoot();
        root.categories = normalizeCategoryList(categories);
        root.rules = new ArrayList<>();
        if (models != null) {
            for (RuleEditModel model : models) {
                if (model == null || isBlank(model.sequenceName)) {
                    continue;
                }
                TriggerRule rule = new TriggerRule();
                rule.name = safe(model.name).trim();
                rule.category = normalizeCategory(model.category);
                rule.enabled = model.enabled;
                rule.triggerType = normalizeTriggerType(model.triggerType);
                rule.contains = safe(model.contains).trim();
                rule.params = sanitizeParams(rule.triggerType, model.params);
                rule.sequenceName = safe(model.sequenceName).trim();
                rule.executionCount = normalizeExecutionCount(model.executionCount);
                rule.backgroundExecution = model.backgroundExecution;
                rule.cooldownMs = Math.max(0, model.cooldownMs);
                rule.note = safe(model.note).trim();
                root.rules.add(rule);
                if (!containsIgnoreCase(root.categories, rule.category)) {
                    root.categories.add(rule.category);
                }
            }
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTrigger] 保存规则失败", e);
        }
        reloadRules();
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        initialize();
        ensureCategoriesSynced();
        return new ArrayList<>(CATEGORIES);
    }

    private static void ensureCategoriesSynced() {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            String safeCategory = normalizeCategory(category);
            normalized.put(safeCategory.toLowerCase(Locale.ROOT), safeCategory);
        }
        for (TriggerRule rule : RULES) {
            if (rule == null) {
                continue;
            }
            String safeCategory = normalizeCategory(rule.category);
            rule.category = safeCategory;
            normalized.put(safeCategory.toLowerCase(Locale.ROOT), safeCategory);
        }
        if (normalized.isEmpty()) {
            normalized.put(CATEGORY_UNGROUPED.toLowerCase(Locale.ROOT), CATEGORY_UNGROUPED);
        }
        CATEGORIES.clear();
        CATEGORIES.addAll(normalized.values());
    }

    private static List<String> normalizeCategoryList(List<String> categories) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (categories != null) {
            for (String category : categories) {
                String safeCategory = normalizeCategory(category);
                normalized.put(safeCategory.toLowerCase(Locale.ROOT), safeCategory);
            }
        }
        if (!normalized.containsKey(CATEGORY_UNGROUPED.toLowerCase(Locale.ROOT))) {
            normalized.put(CATEGORY_UNGROUPED.toLowerCase(Locale.ROOT), CATEGORY_UNGROUPED);
        }
        return new ArrayList<>(normalized.values());
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        String normalizedTarget = normalizeCategory(target);
        for (String value : values) {
            if (normalizeCategory(value).equalsIgnoreCase(normalizedTarget)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeCategory(String category) {
        String normalized = safe(category).trim();
        return normalized.isEmpty() ? CATEGORY_UNGROUPED : normalized;
    }

    public static void triggerEvent(String triggerType, JsonObject eventData) {
        initialize();
        String normalizedType = normalizeTriggerType(triggerType);
        if (normalizedType.isEmpty()) {
            return;
        }
        if (TRIGGER_CHAT.equals(normalizedType) && isInternalDebugChatEvent(eventData)) {
            return;
        }
        int enabledRuleCount = ENABLED_RULE_COUNT_BY_TRIGGER.getOrDefault(normalizedType, 0);
        if (enabledRuleCount <= 0) {
            return;
        }

        boolean debugEnabled = ModConfig.isDebugFlagEnabled(DebugModule.TRIGGER_RULES);
        String searchText = buildSearchText(eventData);
        long now = System.currentTimeMillis();
        if (debugEnabled) {
            emitTriggerEventDebug(normalizedType, eventData);
        }

        for (TriggerRule rule : RULES) {
            if (rule == null || !rule.enabled) {
                continue;
            }
            if (!normalizedType.equals(rule.triggerType)) {
                continue;
            }

            RuleEvaluation evaluation = evaluateRule(rule, eventData, searchText);
            if (!evaluation.matched) {
                if (debugEnabled) {
                    emitTriggerRuleDebug(normalizedType, rule, "未命中", evaluation.detail,
                            "miss|" + evaluation.debugStateKey);
                }
                continue;
            }

            String ruleKey = normalizedType + "|" + safe(rule.name) + "|" + safe(rule.sequenceName);
            long lastTime = LAST_TRIGGER_TIMES.containsKey(ruleKey) ? LAST_TRIGGER_TIMES.get(ruleKey) : 0L;
            if (rule.cooldownMs > 0 && now - lastTime < rule.cooldownMs) {
                if (debugEnabled) {
                    long remainMs = Math.max(0L, rule.cooldownMs - (now - lastTime));
                    emitTriggerRuleDebug(normalizedType, rule, "冷却中",
                            evaluation.detail + " | 剩余冷却 " + formatDebugDuration(remainMs),
                            "cooldown|" + evaluation.debugStateKey + "|" + bucketDurationSeconds(remainMs));
                }
                continue;
            }

            PathSequence sequence = PathSequenceManager.getSequence(rule.sequenceName);
            if (sequence == null || sequence.getSteps().isEmpty()) {
                if (debugEnabled) {
                    emitTriggerRuleDebug(normalizedType, rule, "序列缺失",
                            evaluation.detail + " | 目标序列不存在或为空: " + safe(rule.sequenceName),
                            "missing_sequence|" + safe(rule.sequenceName));
                }
                continue;
            }

            int executionCount = normalizeExecutionCount(rule.executionCount);
            if (executionCount == 0) {
                continue;
            }

            writeTriggerContext(normalizedType, eventData, rule);
            LAST_TRIGGER_TIMES.put(ruleKey, now);

            if (rule.backgroundExecution) {
                if (debugEnabled) {
                    emitTriggerRuleDebug(normalizedType, rule, "已执行",
                            evaluation.detail + " | 已执行后台序列: " + safe(rule.sequenceName),
                            "executed_bg|" + now, true);
                }
                PathSequenceEventListener.startBackgroundSequence(sequence, executionCount);
            } else {
                if (debugEnabled) {
                    emitTriggerRuleDebug(normalizedType, rule, "已执行",
                            evaluation.detail + " | 已执行前台序列: " + safe(rule.sequenceName),
                            "executed_fg|" + now, true);
                }
                PathSequenceManager.runPathSequenceWithLoopCount(rule.sequenceName, executionCount);
            }
        }
    }

    private static int normalizeExecutionCount(int executionCount) {
        return executionCount < -1 ? 1 : executionCount;
    }

    private static boolean matchesRule(TriggerRule rule, JsonObject eventData, String searchText) {
        return evaluateRule(rule, eventData, searchText).matched;
    }

    private static RuleEvaluation evaluateRule(TriggerRule rule, JsonObject eventData, String searchText) {
        if (rule == null) {
            return RuleEvaluation.missed("规则为空。", "null_rule");
        }

        String contains = safe(rule.contains).trim();
        String containsPrefix = "";
        if (!contains.isEmpty()) {
            boolean containsMatched = matchesContains(rule.contains, searchText);
            if (!containsMatched) {
                return RuleEvaluation.missed(
                        "通用过滤未命中: 需要包含 \"" + contains + "\"，实际事件文本 "
                                + quote(shortenDebugText(searchText, 72)),
                        "contains_miss|" + contains.toLowerCase(Locale.ROOT));
            }
            containsPrefix = "通用过滤命中 \"" + contains + "\" | ";
        }

        JsonObject params = sanitizeParams(rule.triggerType, rule.params);
        switch (rule.triggerType) {
            case TRIGGER_GUI_OPEN:
            case TRIGGER_GUI_CLOSE:
                return evaluateGuiOpen(rule.triggerType, params, eventData, containsPrefix);
            case TRIGGER_CHAT:
                return evaluateChat(params, eventData, containsPrefix);
            case TRIGGER_PACKET:
                return evaluatePacket(params, eventData, containsPrefix);
            case TRIGGER_TITLE:
            case TRIGGER_ACTIONBAR:
            case TRIGGER_SCOREBOARD_CHANGED:
            case TRIGGER_BOSSBAR:
                return evaluateTextParam(params, "text", eventData, "text", "文本", containsPrefix);
            case TRIGGER_KEY_INPUT:
                return evaluateTextParam(params, "keyName", eventData, "keyName", "按键", containsPrefix);
            case TRIGGER_PLAYER_IDLE:
                return evaluatePlayerIdle(params, eventData, containsPrefix);
            case TRIGGER_TIMER:
                return evaluateTimer(params, eventData, containsPrefix);
            case TRIGGER_HP_LOW:
                return evaluateHpLow(params, eventData, containsPrefix);
            case TRIGGER_FOOD_LOW:
                return evaluateFoodLow(params, eventData, containsPrefix);
            case TRIGGER_PLAYER_HURT:
                return evaluateDamageEvent(params, eventData, containsPrefix);
            case TRIGGER_ATTACK_ENTITY:
            case TRIGGER_TARGET_KILL:
                return evaluateTextParam(params, "entityText", eventData, "entityName", "目标实体", containsPrefix);
            case TRIGGER_ENTITY_NEARBY:
                return evaluateEntityNearby(params, eventData, containsPrefix);
            case TRIGGER_PLAYER_LIST:
                return evaluatePlayerList(params, eventData, containsPrefix);
            case TRIGGER_ITEM_PICKUP:
                return evaluateItemPickup(params, eventData, containsPrefix);
            case TRIGGER_WORLD_CHANGED:
            case TRIGGER_AREA_CHANGED:
                return evaluateAreaChanged(params, eventData, containsPrefix);
            case TRIGGER_INVENTORY_CHANGED:
                return evaluateTextParam(params, "inventoryText", eventData, "after", "背包文本", containsPrefix);
            case TRIGGER_INVENTORY_FULL:
                return evaluateInventoryFull(params, eventData, containsPrefix);
            case TRIGGER_DEATH:
            case TRIGGER_RESPAWN:
            case TRIGGER_SERVER_CONNECT:
            case TRIGGER_SERVER_DISCONNECT:
            default:
                return RuleEvaluation.matched(containsPrefix + "事件满足默认条件。", "default");
        }
    }

    private static RuleEvaluation evaluateGuiOpen(String triggerType, JsonObject params, JsonObject eventData,
            String prefix) {
        String expectedTitle = getStringParam(params, "guiTitle");
        String expectedClass = getStringParam(params, "guiClass");
        String actualTitle = getStringValue(eventData, "title");
        String actualGui = getStringValue(eventData, "gui");
        if (!expectedTitle.isEmpty() && !safe(actualTitle).toLowerCase(Locale.ROOT)
                .contains(expectedTitle.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "标题未命中: 需要包含 " + quote(expectedTitle) + "，实际 "
                    + quote(shortenDebugText(actualTitle, 60)),
                    "gui_title_miss|" + expectedTitle.toLowerCase(Locale.ROOT));
        }
        if (!expectedClass.isEmpty() && !safe(actualGui).toLowerCase(Locale.ROOT)
                .contains(expectedClass.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "界面类未命中: 需要包含 " + quote(expectedClass) + "，实际 "
                    + quote(shortenDebugText(actualGui, 60)),
                    "gui_class_miss|" + expectedClass.toLowerCase(Locale.ROOT));
        }
        String detail = prefix + (TRIGGER_GUI_CLOSE.equals(triggerType) ? "界面关闭条件命中" : "界面打开条件命中")
                + " | 标题 " + quote(shortenDebugText(actualTitle, 40))
                + " | GUI " + quote(shortenDebugText(actualGui, 52));
        return RuleEvaluation.matched(detail, "gui_match|" + safe(actualTitle) + "|" + safe(actualGui));
    }

    private static RuleEvaluation evaluatePacket(JsonObject params, JsonObject eventData, String prefix) {
        String packetText = getStringParam(params, "packetText");
        String channel = getStringParam(params, "channel");
        String direction = getStringParam(params, "direction");
        String actualChannel = getStringValue(eventData, "channel");
        String actualDirection = getStringValue(eventData, "direction");
        String packetClass = getStringValue(eventData, "packetClass");
        String packetCombined = (getStringValue(eventData, "packet") + " | "
                + getStringValue(eventData, "decoded") + " | "
                + packetClass).toLowerCase(Locale.ROOT);
        if (!packetText.isEmpty() && !packetCombined.contains(packetText.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "包文本未命中: 需要包含 " + quote(packetText) + "，实际 "
                    + quote(shortenDebugText(packetCombined, 72)),
                    "packet_text_miss|" + packetText.toLowerCase(Locale.ROOT));
        }
        if (!channel.isEmpty() && !actualChannel.toLowerCase(Locale.ROOT).contains(channel.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "频道未命中: 需要包含 " + quote(channel) + "，实际 "
                    + quote(shortenDebugText(actualChannel, 40)),
                    "packet_channel_miss|" + channel.toLowerCase(Locale.ROOT));
        }
        if (!direction.isEmpty() && !direction.equalsIgnoreCase(actualDirection)) {
            return RuleEvaluation.missed(prefix + "方向未命中: 需要 " + quote(direction) + "，实际 "
                    + quote(actualDirection),
                    "packet_direction_miss|" + direction.toLowerCase(Locale.ROOT));
        }
        return RuleEvaluation.matched(prefix + "数据包条件命中 | 方向=" + quote(actualDirection)
                + " | 频道=" + quote(shortenDebugText(actualChannel, 32))
                + " | 包=" + quote(shortenDebugText(packetClass, 40)),
                "packet_match|" + actualDirection + "|" + actualChannel + "|" + packetClass);
    }

    private static RuleEvaluation evaluateTimer(JsonObject params, JsonObject eventData, String prefix) {
        int intervalSeconds = Math.max(1, getIntParam(params, "intervalSeconds", 1));
        long tick = getLongParam(eventData, "tick", 0L);
        boolean matched = intervalSeconds <= 1 || tick % (intervalSeconds * 20L) == 0L;
        String detail = prefix + "定时器检查: 间隔=" + intervalSeconds + "s | 当前Tick=" + tick;
        return matched
                ? RuleEvaluation.matched(detail + " | 已命中。", "timer_match|" + intervalSeconds + "|" + tick)
                : RuleEvaluation.missed(detail + " | 未到触发时机。", "timer_miss|" + intervalSeconds + "|" + tick);
    }

    private static RuleEvaluation evaluatePlayerIdle(JsonObject params, JsonObject eventData, String prefix) {
        long requiredIdleMs = Math.max(0L, getLongParam(params, "idleMs", 1000L));
        boolean excludePathTracking = getBooleanParam(params, "excludePathTracking", true);
        boolean ignoreDamageReset = getBooleanParam(params, "ignoreDamageReset", false);
        long actualIdleMs;
        if (excludePathTracking) {
            actualIdleMs = ignoreDamageReset
                    ? Math.max(0L, getLongParam(eventData, "idleMsExcludingPathTrackingIgnoringDamage", 0L))
                    : Math.max(0L, getLongParam(eventData, "idleMsExcludingPathTracking", 0L));
        } else {
            actualIdleMs = ignoreDamageReset
                    ? Math.max(0L, getLongParam(eventData, "idleMsIgnoringDamage", 0L))
                    : Math.max(0L, getLongParam(eventData, "idleMs", 0L));
        }
        boolean matched = actualIdleMs >= requiredIdleMs;
        String detail = prefix + "站立不动检查: 需要>=" + formatDebugDuration(requiredIdleMs)
                + " | 实际=" + formatDebugDuration(actualIdleMs)
                + " | 排除路径=" + onOffText(excludePathTracking)
                + " | 忽略受伤=" + onOffText(ignoreDamageReset);
        String stateKey = "player_idle|" + bucketDurationSeconds(requiredIdleMs) + "|"
                + bucketDurationSeconds(actualIdleMs) + "|" + excludePathTracking + "|" + ignoreDamageReset;
        return matched
                ? RuleEvaluation.matched(detail + " | 已命中。", stateKey + "|match")
                : RuleEvaluation.missed(detail + " | 未达到要求。", stateKey + "|miss");
    }

    private static RuleEvaluation evaluateHpLow(JsonObject params, JsonObject eventData, String prefix) {
        double threshold = getDoubleParam(params, "hpThreshold", 6.0D);
        double hp = getDoubleParam(eventData, "hp", Double.MAX_VALUE);
        double maxHp = getDoubleParam(eventData, "maxHp", 0.0D);
        boolean matched = hp <= threshold;
        String detail = prefix + "低血量检查: 阈值<=" + formatDecimal(threshold)
                + " | 当前=" + formatDecimal(hp) + "/" + formatDecimal(maxHp);
        String stateKey = "hp_low|" + bucketHalf(hp) + "|" + bucketHalf(maxHp) + "|" + bucketHalf(threshold);
        return matched
                ? RuleEvaluation.matched(detail + " | 已命中。", stateKey + "|match")
                : RuleEvaluation.missed(detail + " | 未达到阈值。", stateKey + "|miss");
    }

    private static RuleEvaluation evaluateFoodLow(JsonObject params, JsonObject eventData, String prefix) {
        double threshold = getDoubleParam(params, "foodThreshold", 12.0D);
        double food = getDoubleParam(eventData, "food", Double.MAX_VALUE);
        double maxFood = getDoubleParam(eventData, "maxFood", 20.0D);
        boolean matched = food <= threshold;
        String detail = prefix + "低饱食度检查: 阈值<=" + formatDecimal(threshold)
                + " | 当前=" + formatDecimal(food) + "/" + formatDecimal(maxFood);
        String stateKey = "food_low|" + bucketHalf(food) + "|" + bucketHalf(maxFood) + "|" + bucketHalf(threshold);
        return matched
                ? RuleEvaluation.matched(detail + " | 已命中。", stateKey + "|match")
                : RuleEvaluation.missed(detail + " | 未达到阈值。", stateKey + "|miss");
    }

    private static RuleEvaluation evaluateEntityNearby(JsonObject params, JsonObject eventData, String prefix) {
        String entityText = getStringParam(params, "entityText");
        String entityType = getStringParam(params, "entityType");
        int minCount = Math.max(0, getIntParam(params, "minCount", 1));
        String typeLabel = "全部";
        String actual = getStringValue(eventData, "after");
        long count = getLongParam(eventData, "count", 0L);
        if ("player".equalsIgnoreCase(entityType) || "玩家".equalsIgnoreCase(entityType)) {
            typeLabel = "玩家";
            actual = getStringValue(eventData, "afterPlayer");
            count = getLongParam(eventData, "afterPlayerCount", 0L);
        } else if ("hostile".equalsIgnoreCase(entityType)
                || "monster".equalsIgnoreCase(entityType)
                || "mob".equalsIgnoreCase(entityType)
                || "敌对生物".equalsIgnoreCase(entityType)
                || "怪物".equalsIgnoreCase(entityType)) {
            typeLabel = "敌对生物";
            actual = getStringValue(eventData, "afterHostile");
            count = getLongParam(eventData, "afterHostileCount", 0L);
        } else if ("passive".equalsIgnoreCase(entityType)
                || "animal".equalsIgnoreCase(entityType)
                || "被动生物".equalsIgnoreCase(entityType)
                || "动物".equalsIgnoreCase(entityType)) {
            typeLabel = "被动生物";
            actual = getStringValue(eventData, "afterPassive");
            count = getLongParam(eventData, "afterPassiveCount", 0L);
        }
        if (count < minCount) {
            return RuleEvaluation.missed(prefix + "附近实体数量不足(" + typeLabel + "): 需要>=" + minCount + "，实际=" + count,
                    "entity_count_miss|" + typeLabel + "|" + minCount + "|" + count);
        }
        if (entityText.isEmpty()) {
            return RuleEvaluation.matched(prefix + "附近实体数量命中(" + typeLabel + "): " + count + " 个。",
                    "entity_count_match|" + typeLabel + "|" + count);
        }
        boolean matched = actual.toLowerCase(Locale.ROOT).contains(entityText.toLowerCase(Locale.ROOT));
        return matched
                ? RuleEvaluation.matched(prefix + "附近实体文本命中(" + typeLabel + "): " + quote(entityText) + " | 当前 "
                        + quote(shortenDebugText(actual, 68)),
                        "entity_text_match|" + typeLabel + "|" + entityText.toLowerCase(Locale.ROOT) + "|" + count)
                : RuleEvaluation.missed(prefix + "附近实体文本未命中(" + typeLabel + "): 需要包含 " + quote(entityText) + "，实际 "
                        + quote(shortenDebugText(actual, 68)),
                        "entity_text_miss|" + typeLabel + "|" + entityText.toLowerCase(Locale.ROOT) + "|" + count);
    }

    private static RuleEvaluation evaluateItemPickup(JsonObject params, JsonObject eventData, String prefix) {
        String itemText = getStringParam(params, "itemText");
        int minCount = Math.max(0, getIntParam(params, "minCount", 1));
        long count = getLongParam(eventData, "count", 0L);
        if (count < minCount) {
            return RuleEvaluation.missed(prefix + "拾取数量不足: 需要>=" + minCount + "，实际=" + count,
                    "pickup_count_miss|" + minCount + "|" + count);
        }
        String itemName = getStringValue(eventData, "itemName");
        String registryName = getStringValue(eventData, "registryName");
        if (itemText.isEmpty()) {
            return RuleEvaluation.matched(prefix + "拾取数量命中: "
                    + quote(shortenDebugText(itemName, 40)) + " x" + count,
                    "pickup_match|" + count + "|" + itemName);
        }
        String combined = (itemName + " | " + registryName).toLowerCase(Locale.ROOT);
        boolean matched = combined.contains(itemText.toLowerCase(Locale.ROOT));
        return matched
                ? RuleEvaluation.matched(prefix + "拾取物品文本命中: 需要 " + quote(itemText)
                        + " | 实际 " + quote(shortenDebugText(itemName, 40)) + " x" + count,
                        "pickup_text_match|" + itemText.toLowerCase(Locale.ROOT) + "|" + count)
                : RuleEvaluation.missed(prefix + "拾取物品文本未命中: 需要 " + quote(itemText) + "，实际 "
                        + quote(shortenDebugText(itemName + " | " + registryName, 68)),
                        "pickup_text_miss|" + itemText.toLowerCase(Locale.ROOT) + "|" + count);
    }

    private static RuleEvaluation evaluatePlayerList(JsonObject params, JsonObject eventData, String prefix) {
        List<PlayerListTriggerSupport.RuleEntry> entries = PlayerListTriggerSupport.readEntries(params);
        if (entries.isEmpty()) {
            return RuleEvaluation.matched(prefix + "未配置玩家名称卡片，任意玩家列表变化都可触发。",
                    "player_list_any");
        }
        boolean matched = PlayerListTriggerSupport.matchesConfiguredPlayers(params, eventData);
        String summary = PlayerListTriggerSupport.buildEntriesSummary(entries);
        String joined = getStringValue(eventData, "joined");
        String left = getStringValue(eventData, "left");
        String actual = !joined.isEmpty() || !left.isEmpty()
                ? "加入=" + quote(shortenDebugText(joined, 48)) + " | 离开=" + quote(shortenDebugText(left, 48))
                : quote(shortenDebugText(getStringValue(eventData, "playersText"), 72));
        return matched
                ? RuleEvaluation.matched(prefix + "玩家列表卡片命中: " + quote(shortenDebugText(summary, 72))
                        + " | 当前 " + actual,
                        "player_list_match|" + summary.toLowerCase(Locale.ROOT))
                : RuleEvaluation.missed(prefix + "玩家列表卡片未命中: " + quote(shortenDebugText(summary, 72))
                        + " | 当前 " + actual,
                        "player_list_miss|" + summary.toLowerCase(Locale.ROOT));
    }

    private static RuleEvaluation evaluateDamageEvent(JsonObject params, JsonObject eventData, String prefix) {
        double minDamage = getDoubleParam(params, "minDamage", 0.0D);
        double damage = getDoubleParam(eventData, "damage", 0.0D);
        String actualSource = getStringValue(eventData, "damageSource");
        if (damage < minDamage) {
            return RuleEvaluation.missed(prefix + "伤害不足: 需要>=" + formatDecimal(minDamage) + "，实际="
                    + formatDecimal(damage),
                    "damage_amount_miss|" + bucketHalf(minDamage) + "|" + bucketHalf(damage));
        }
        String expectedSource = getStringParam(params, "damageSource");
        if (!expectedSource.isEmpty()
                && !actualSource.toLowerCase(Locale.ROOT).contains(expectedSource.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "伤害来源未命中: 需要包含 " + quote(expectedSource) + "，实际 "
                    + quote(shortenDebugText(actualSource, 40)),
                    "damage_source_miss|" + expectedSource.toLowerCase(Locale.ROOT));
        }
        return RuleEvaluation.matched(prefix + "伤害条件命中: 伤害=" + formatDecimal(damage)
                + " | 来源=" + quote(shortenDebugText(actualSource, 40)),
                "damage_match|" + bucketHalf(damage) + "|" + actualSource);
    }

    private static RuleEvaluation evaluateAreaChanged(JsonObject params, JsonObject eventData, String prefix) {
        String fromText = getStringParam(params, "fromText");
        String toText = getStringParam(params, "toText");
        String actualFrom = getStringValue(eventData, "from");
        String actualTo = getStringValue(eventData, "to");
        if (!fromText.isEmpty() && !actualFrom.toLowerCase(Locale.ROOT).contains(fromText.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "来源未命中: 需要包含 " + quote(fromText) + "，实际 "
                    + quote(shortenDebugText(actualFrom, 48)),
                    "area_from_miss|" + fromText.toLowerCase(Locale.ROOT));
        }
        if (!toText.isEmpty() && !actualTo.toLowerCase(Locale.ROOT).contains(toText.toLowerCase(Locale.ROOT))) {
            return RuleEvaluation.missed(prefix + "目标未命中: 需要包含 " + quote(toText) + "，实际 "
                    + quote(shortenDebugText(actualTo, 48)),
                    "area_to_miss|" + toText.toLowerCase(Locale.ROOT));
        }
        return RuleEvaluation.matched(prefix + "区域/世界变化命中: " + quote(shortenDebugText(actualFrom, 24))
                + " -> " + quote(shortenDebugText(actualTo, 24)),
                "area_match|" + actualFrom + "|" + actualTo);
    }

    private static RuleEvaluation evaluateInventoryFull(JsonObject params, JsonObject eventData, String prefix) {
        int minFilledSlots = Math.max(0, getIntParam(params, "minFilledSlots", 0));
        long filledSlots = getLongParam(eventData, "filledSlots", 0L);
        if (filledSlots < minFilledSlots) {
            return RuleEvaluation.missed(prefix + "背包占用槽位不足: 需要>=" + minFilledSlots + "，实际=" + filledSlots,
                    "inventory_full_miss|" + minFilledSlots + "|" + filledSlots);
        }
        long totalSlots = getLongParam(eventData, "totalSlots", 0L);
        return RuleEvaluation.matched(prefix + "背包已满条件命中: 已占 " + filledSlots
                + (totalSlots > 0L ? ("/" + totalSlots) : "") + " 格。",
                "inventory_full_match|" + filledSlots + "|" + totalSlots);
    }

    private static RuleEvaluation evaluateTextParam(JsonObject params, String paramKey, JsonObject eventData,
            String dataKey, String label, String prefix) {
        String expected = getStringParam(params, paramKey);
        String actual = getStringValue(eventData, dataKey);
        if (expected.isEmpty()) {
            return RuleEvaluation.matched(prefix + label + "未设置，任意内容均可。", "text_any|" + dataKey);
        }
        boolean matched = actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
        return matched
                ? RuleEvaluation.matched(prefix + label + "命中: 需要 " + quote(expected) + "，实际 "
                        + quote(shortenDebugText(actual, 68)),
                        "text_match|" + expected.toLowerCase(Locale.ROOT))
                : RuleEvaluation.missed(prefix + label + "未命中: 需要 " + quote(expected) + "，实际 "
                        + quote(shortenDebugText(actual, 68)),
                        "text_miss|" + expected.toLowerCase(Locale.ROOT));
    }

    private static RuleEvaluation evaluateChat(JsonObject params, JsonObject eventData, String prefix) {
        String expected = getStringParam(params, "chatText");
        String rawMessage = getStringValue(eventData, "message");
        String displayedMessage = getStringValue(eventData, "displayedMessage");
        if (expected.isEmpty()) {
            return RuleEvaluation.matched(prefix + "聊天文本未设置，任意消息均可。", "chat_any");
        }
        String normalizedExpected = expected.toLowerCase(Locale.ROOT);
        boolean matched = rawMessage.toLowerCase(Locale.ROOT).contains(normalizedExpected)
                || displayedMessage.toLowerCase(Locale.ROOT).contains(normalizedExpected);
        String actual = rawMessage.isEmpty() ? displayedMessage : rawMessage;
        return matched
                ? RuleEvaluation.matched(prefix + "聊天文本命中: 需要 " + quote(expected) + "，实际 "
                        + quote(shortenDebugText(actual, 72)),
                        "chat_match|" + normalizedExpected)
                : RuleEvaluation.missed(prefix + "聊天文本未命中: 需要 " + quote(expected) + "，实际 "
                        + quote(shortenDebugText(actual, 72)),
                        "chat_miss|" + normalizedExpected);
    }

    private static boolean matchesGuiOpen(JsonObject params, JsonObject eventData) {
        String expectedTitle = getStringParam(params, "guiTitle");
        String expectedClass = getStringParam(params, "guiClass");
        String actualTitle = getStringValue(eventData, "title");
        String actualGui = getStringValue(eventData, "gui");
        if (!expectedTitle.isEmpty() && !safe(actualTitle).toLowerCase(Locale.ROOT)
                .contains(expectedTitle.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!expectedClass.isEmpty() && !safe(actualGui).toLowerCase(Locale.ROOT)
                .contains(expectedClass.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    private static boolean matchesPacket(JsonObject params, JsonObject eventData) {
        String packetText = getStringParam(params, "packetText");
        String channel = getStringParam(params, "channel");
        String direction = getStringParam(params, "direction");
        if (!packetText.isEmpty()) {
            String combined = (getStringValue(eventData, "packet") + " | "
                    + getStringValue(eventData, "decoded") + " | "
                    + getStringValue(eventData, "packetClass")).toLowerCase(Locale.ROOT);
            if (!combined.contains(packetText.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!channel.isEmpty() && !getStringValue(eventData, "channel").toLowerCase(Locale.ROOT)
                .contains(channel.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!direction.isEmpty() && !direction.equalsIgnoreCase(getStringValue(eventData, "direction"))) {
            return false;
        }
        return true;
    }

    private static boolean matchesTimer(JsonObject params, JsonObject eventData) {
        int intervalSeconds = Math.max(1, getIntParam(params, "intervalSeconds", 1));
        long tick = getLongParam(eventData, "tick", 0L);
        return intervalSeconds <= 1 || tick % (intervalSeconds * 20L) == 0L;
    }

    private static boolean matchesPlayerIdle(JsonObject params, JsonObject eventData) {
        long requiredIdleMs = Math.max(0L, getLongParam(params, "idleMs", 1000L));
        boolean excludePathTracking = getBooleanParam(params, "excludePathTracking", true);
        boolean ignoreDamageReset = getBooleanParam(params, "ignoreDamageReset", false);
        long actualIdleMs;
        if (excludePathTracking) {
            actualIdleMs = ignoreDamageReset
                    ? Math.max(0L, getLongParam(eventData, "idleMsExcludingPathTrackingIgnoringDamage", 0L))
                    : Math.max(0L, getLongParam(eventData, "idleMsExcludingPathTracking", 0L));
        } else {
            actualIdleMs = ignoreDamageReset
                    ? Math.max(0L, getLongParam(eventData, "idleMsIgnoringDamage", 0L))
                    : Math.max(0L, getLongParam(eventData, "idleMs", 0L));
        }
        return actualIdleMs >= requiredIdleMs;
    }

    private static boolean matchesHpLow(JsonObject params, JsonObject eventData) {
        double threshold = getDoubleParam(params, "hpThreshold", 6.0D);
        double hp = getDoubleParam(eventData, "hp", Double.MAX_VALUE);
        return hp <= threshold;
    }

    private static boolean matchesFoodLow(JsonObject params, JsonObject eventData) {
        double threshold = getDoubleParam(params, "foodThreshold", 12.0D);
        double food = getDoubleParam(eventData, "food", Double.MAX_VALUE);
        return food <= threshold;
    }

    private static boolean matchesEntityNearby(JsonObject params, JsonObject eventData) {
        String entityText = getStringParam(params, "entityText");
        int minCount = Math.max(0, getIntParam(params, "minCount", 1));
        long count = getLongParam(eventData, "count", 0L);
        if (count < minCount) {
            return false;
        }
        if (entityText.isEmpty()) {
            return true;
        }
        return getStringValue(eventData, "after").toLowerCase(Locale.ROOT)
                .contains(entityText.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesItemPickup(JsonObject params, JsonObject eventData) {
        String itemText = getStringParam(params, "itemText");
        int minCount = Math.max(0, getIntParam(params, "minCount", 1));
        long count = getLongParam(eventData, "count", 0L);
        if (count < minCount) {
            return false;
        }
        if (itemText.isEmpty()) {
            return true;
        }
        String itemName = getStringValue(eventData, "itemName");
        String registryName = getStringValue(eventData, "registryName");
        String combined = (itemName + " | " + registryName).toLowerCase(Locale.ROOT);
        return combined.contains(itemText.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesDamageEvent(JsonObject params, JsonObject eventData) {
        double minDamage = getDoubleParam(params, "minDamage", 0.0D);
        double damage = getDoubleParam(eventData, "damage", 0.0D);
        if (damage < minDamage) {
            return false;
        }
        String expectedSource = getStringParam(params, "damageSource");
        if (expectedSource.isEmpty()) {
            return true;
        }
        String actualSource = getStringValue(eventData, "damageSource");
        return actualSource.toLowerCase(Locale.ROOT).contains(expectedSource.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesAreaChanged(JsonObject params, JsonObject eventData) {
        String fromText = getStringParam(params, "fromText");
        String toText = getStringParam(params, "toText");
        if (!fromText.isEmpty() && !getStringValue(eventData, "from").toLowerCase(Locale.ROOT)
                .contains(fromText.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!toText.isEmpty() && !getStringValue(eventData, "to").toLowerCase(Locale.ROOT)
                .contains(toText.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    private static boolean matchesInventoryFull(JsonObject params, JsonObject eventData) {
        int minFilledSlots = Math.max(0, getIntParam(params, "minFilledSlots", 0));
        long filledSlots = getLongParam(eventData, "filledSlots", 0L);
        return filledSlots >= minFilledSlots;
    }

    private static boolean matchesTextParam(JsonObject params, String paramKey, JsonObject eventData, String dataKey) {
        String expected = getStringParam(params, paramKey);
        if (expected.isEmpty()) {
            return true;
        }
        return getStringValue(eventData, dataKey).toLowerCase(Locale.ROOT)
                .contains(expected.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesChat(JsonObject params, JsonObject eventData) {
        String expected = getStringParam(params, "chatText");
        if (expected.isEmpty()) {
            return true;
        }
        String normalizedExpected = expected.toLowerCase(Locale.ROOT);
        return getStringValue(eventData, "message").toLowerCase(Locale.ROOT).contains(normalizedExpected)
                || getStringValue(eventData, "displayedMessage").toLowerCase(Locale.ROOT)
                        .contains(normalizedExpected);
    }

    private static void writeTriggerContext(String triggerType, JsonObject eventData, TriggerRule rule) {
        Map<String, Object> triggerMap = new LinkedHashMap<>();
        triggerMap.put("type", triggerType);
        triggerMap.put("timestamp", System.currentTimeMillis());
        triggerMap.put("ruleName", safe(rule == null ? "" : rule.name));
        triggerMap.put("sequenceName", safe(rule == null ? "" : rule.sequenceName));
        if (eventData != null) {
            for (Map.Entry<String, JsonElement> entry : eventData.entrySet()) {
                triggerMap.put(entry.getKey(), toJavaValue(entry.getValue()));
            }
        }
        ScopedRuntimeVariables.setGlobalValue("trigger", triggerMap);
        ScopedRuntimeVariables.setGlobalValue("triggerType", triggerType);
    }

    private static boolean isInternalDebugChatEvent(JsonObject eventData) {
        return ModConfig.isInternalDebugChatMessage(getStringValue(eventData, "message"))
                || ModConfig.isInternalDebugChatMessage(getStringValue(eventData, "displayedMessage"));
    }

    private static void emitTriggerEventDebug(String triggerType, JsonObject eventData) {
        String message = buildTriggerEventDebugMessage(triggerType, eventData);
        String stateKey = buildTriggerEventStateKey(triggerType, eventData);
        if (!shouldEmitTriggerEventDebug(triggerType, stateKey)) {
            return;
        }
        ModConfig.debugPrint(DebugModule.TRIGGER_RULES,
                "[" + getTriggerDisplayName(triggerType) + "] " + message);
    }

    private static boolean shouldEmitTriggerEventDebug(String triggerType, String stateKey) {
        String safeType = safe(triggerType);
        if (safeType.isEmpty()) {
            return false;
        }
        if (stateKey == null || stateKey.isEmpty()) {
            return true;
        }
        String previous = LAST_DEBUG_EVENT_STATE.put(safeType, stateKey);
        return !stateKey.equals(previous);
    }

    private static void emitTriggerRuleDebug(String triggerType, TriggerRule rule, String status, String detail,
            String stateKey) {
        emitTriggerRuleDebug(triggerType, rule, status, detail, stateKey, false);
    }

    private static void emitTriggerRuleDebug(String triggerType, TriggerRule rule, String status, String detail,
            String stateKey, boolean alwaysLog) {
        String baseRuleKey = safe(triggerType) + "|" + safe(rule == null ? "" : rule.name)
                + "|" + safe(rule == null ? "" : rule.sequenceName) + "|" + safe(status);
        if (!alwaysLog && !shouldEmitTriggerRuleDebug(baseRuleKey, stateKey)) {
            return;
        }
        String ruleName = getRuleDisplayName(rule);
        StringBuilder message = new StringBuilder();
        message.append('[').append(getTriggerDisplayName(triggerType)).append(']');
        message.append('[').append(ruleName).append(']');
        message.append(' ').append(status);
        if (!safe(detail).isEmpty()) {
            message.append(" | ").append(detail);
        }
        ModConfig.debugPrint(DebugModule.TRIGGER_RULES, message.toString());
    }

    private static boolean shouldEmitTriggerRuleDebug(String ruleKey, String stateKey) {
        String safeKey = safe(ruleKey);
        if (safeKey.isEmpty()) {
            return true;
        }
        String actualStateKey = safe(stateKey);
        String previous = LAST_DEBUG_RULE_STATE.put(safeKey, actualStateKey);
        return !actualStateKey.equals(previous);
    }

    private static String buildTriggerEventDebugMessage(String triggerType, JsonObject eventData) {
        switch (safe(triggerType)) {
            case TRIGGER_GUI_OPEN:
                return "打开界面 | 标题=" + quote(shortenDebugText(getStringValue(eventData, "title"), 40))
                        + " | GUI=" + quote(shortenDebugText(getStringValue(eventData, "gui"), 52));
            case TRIGGER_GUI_CLOSE:
                return "关闭界面 | 标题=" + quote(shortenDebugText(getStringValue(eventData, "title"), 40))
                        + " | GUI=" + quote(shortenDebugText(getStringValue(eventData, "gui"), 52));
            case TRIGGER_CHAT:
                return "收到聊天 | source=" + quote(getStringValue(eventData, "source"))
                        + " | 内容=" + quote(shortenDebugText(firstNonBlank(
                                getStringValue(eventData, "message"),
                                getStringValue(eventData, "displayedMessage")), 80));
            case TRIGGER_PACKET:
                return "收到数据包事件 | 方向=" + quote(getStringValue(eventData, "direction"))
                        + " | 频道=" + quote(shortenDebugText(getStringValue(eventData, "channel"), 32))
                        + " | 包=" + quote(shortenDebugText(getStringValue(eventData, "packetClass"), 40));
            case TRIGGER_TITLE:
            case TRIGGER_ACTIONBAR:
            case TRIGGER_SCOREBOARD_CHANGED:
            case TRIGGER_BOSSBAR:
                return "文本事件 | 内容=" + quote(shortenDebugText(getStringValue(eventData, "text"), 80));
            case TRIGGER_KEY_INPUT:
                return "检测到按键 " + quote(getStringValue(eventData, "keyName"))
                        + " (" + getLongParam(eventData, "keyCode", 0L) + ")";
            case TRIGGER_PLAYER_IDLE:
                return "已不动 " + formatDebugDuration(getLongParam(eventData, "idleMs", 0L))
                        + " | 排除路径 " + formatDebugDuration(getLongParam(eventData, "idleMsExcludingPathTracking", 0L))
                        + " | 忽略受伤 " + formatDebugDuration(getLongParam(eventData, "idleMsIgnoringDamage", 0L))
                        + " | 路径中=" + onOffText(getBooleanParam(eventData, "pathTrackingActive", false))
                        + " | 近期受伤=" + onOffText(getBooleanParam(eventData, "recentlyHurt", false));
            case TRIGGER_TIMER:
                return "定时器到点 | 客户端Tick=" + getLongParam(eventData, "tick", 0L);
            case TRIGGER_HP_LOW:
                return "当前血量 " + formatDecimal(getDoubleParam(eventData, "hp", 0.0D))
                        + "/" + formatDecimal(getDoubleParam(eventData, "maxHp", 0.0D));
            case TRIGGER_FOOD_LOW:
                return "当前饱食度 " + formatDecimal(getDoubleParam(eventData, "food", 0.0D))
                        + "/" + formatDecimal(getDoubleParam(eventData, "maxFood", 20.0D));
            case TRIGGER_DEATH:
                return "检测到玩家死亡。";
            case TRIGGER_RESPAWN:
                return "检测到玩家重生。";
            case TRIGGER_PLAYER_HURT:
                return "受到伤害 " + formatDecimal(getDoubleParam(eventData, "damage", 0.0D))
                        + " | 来源=" + quote(shortenDebugText(getStringValue(eventData, "damageSource"), 40));
            case TRIGGER_ATTACK_ENTITY:
                return "攻击实体 " + quote(shortenDebugText(getStringValue(eventData, "entityName"), 40))
                        + " | 类=" + quote(shortenDebugText(getStringValue(eventData, "entityClass"), 48));
            case TRIGGER_TARGET_KILL:
                return "击杀目标 " + quote(shortenDebugText(getStringValue(eventData, "entityName"), 40))
                        + " | 类=" + quote(shortenDebugText(getStringValue(eventData, "entityClass"), 48));
            case TRIGGER_WORLD_CHANGED:
                return "世界切换 " + quote(shortenDebugText(getStringValue(eventData, "from"), 18))
                        + " -> " + quote(shortenDebugText(getStringValue(eventData, "to"), 18));
            case TRIGGER_AREA_CHANGED:
                return "区域变化 " + quote(shortenDebugText(getStringValue(eventData, "from"), 20))
                        + " -> " + quote(shortenDebugText(getStringValue(eventData, "to"), 20))
                        + " | chunk=(" + getLongParam(eventData, "chunkX", 0L) + ","
                        + getLongParam(eventData, "chunkZ", 0L) + ")";
            case TRIGGER_INVENTORY_CHANGED:
                return "背包已变化 | 已占槽位=" + getLongParam(eventData, "filledSlots", 0L)
                        + " | 当前=" + quote(shortenDebugText(getStringValue(eventData, "after"), 84));
            case TRIGGER_INVENTORY_FULL:
                return "背包已满 | 已占=" + getLongParam(eventData, "filledSlots", 0L)
                        + "/" + getLongParam(eventData, "totalSlots", 0L)
                        + " | 空槽=" + getLongParam(eventData, "emptySlots", 0L);
            case TRIGGER_ENTITY_NEARBY:
                return "附近实体变化 | 数量=" + getLongParam(eventData, "count", 0L)
                        + " | 当前=" + quote(shortenDebugText(getStringValue(eventData, "after"), 84));
            case TRIGGER_PLAYER_LIST:
                return "玩家列表变化 | 加入=" + quote(shortenDebugText(getStringValue(eventData, "joined"), 48))
                        + " | 离开=" + quote(shortenDebugText(getStringValue(eventData, "left"), 48))
                        + " | 当前=" + quote(shortenDebugText(getStringValue(eventData, "playersText"), 84));
            case TRIGGER_ITEM_PICKUP:
                return "拾取物品 " + quote(shortenDebugText(getStringValue(eventData, "itemName"), 40))
                        + " x" + getLongParam(eventData, "count", 0L);
            case TRIGGER_SERVER_CONNECT:
                return "客户端已连接服务器。";
            case TRIGGER_SERVER_DISCONNECT:
                return "客户端已断开服务器。";
            default:
                return "收到事件 | " + shortenDebugText(buildSearchText(eventData), 120);
        }
    }

    private static String buildTriggerEventStateKey(String triggerType, JsonObject eventData) {
        switch (safe(triggerType)) {
            case TRIGGER_PLAYER_IDLE:
                return bucketDurationSeconds(getLongParam(eventData, "idleMs", 0L))
                        + "|" + bucketDurationSeconds(getLongParam(eventData, "idleMsExcludingPathTracking", 0L))
                        + "|" + bucketDurationSeconds(getLongParam(eventData, "idleMsIgnoringDamage", 0L))
                        + "|" + getBooleanParam(eventData, "pathTrackingActive", false)
                        + "|" + getBooleanParam(eventData, "recentlyHurt", false);
            case TRIGGER_HP_LOW:
                return bucketHalf(getDoubleParam(eventData, "hp", 0.0D))
                        + "|" + bucketHalf(getDoubleParam(eventData, "maxHp", 0.0D));
            case TRIGGER_FOOD_LOW:
                return bucketHalf(getDoubleParam(eventData, "food", 0.0D))
                        + "|" + bucketHalf(getDoubleParam(eventData, "maxFood", 20.0D));
            case TRIGGER_INVENTORY_CHANGED:
            case TRIGGER_ENTITY_NEARBY:
            case TRIGGER_PLAYER_LIST:
            case TRIGGER_SCOREBOARD_CHANGED:
                return buildSearchText(eventData);
            default:
                return "";
        }
    }

    private static Object toJavaValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) {
                return value.getAsBoolean();
            }
            if (value.getAsJsonPrimitive().isNumber()) {
                try {
                    double number = value.getAsDouble();
                    long whole = (long) number;
                    return number == whole ? whole : number;
                } catch (Exception ignored) {
                    return value.getAsString();
                }
            }
            return value.getAsString();
        }
        if (value.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : value.getAsJsonArray()) {
                list.add(toJavaValue(child));
            }
            return list;
        }
        if (value.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), toJavaValue(entry.getValue()));
            }
            return map;
        }
        return value.toString();
    }

    private static boolean matchesContains(String contains, String searchText) {
        String expected = safe(contains).trim().toLowerCase(Locale.ROOT);
        if (expected.isEmpty()) {
            return true;
        }
        return safe(searchText).toLowerCase(Locale.ROOT).contains(expected);
    }

    private static JsonObject copyJson(JsonObject source) {
        if (source == null) {
            return new JsonObject();
        }
        try {
            return new JsonParser().parse(source.toString()).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static JsonObject sanitizeParams(String triggerType, JsonObject source) {
        JsonObject sanitized = copyJson(source);
        if (sanitized == null) {
            sanitized = new JsonObject();
        }
        normalizeStringParam(sanitized, "guiTitle");
        normalizeStringParam(sanitized, "guiClass");
        normalizeStringParam(sanitized, "chatText");
        normalizeStringParam(sanitized, "packetText");
        normalizeStringParam(sanitized, "channel");
        normalizeStringParam(sanitized, "direction");
        normalizeStringParam(sanitized, "text");
        normalizeStringParam(sanitized, "keyName");
        normalizeStringParam(sanitized, "damageSource");
        normalizeStringParam(sanitized, "serverText");
        normalizeStringParam(sanitized, "fromText");
        normalizeStringParam(sanitized, "toText");
        normalizeStringParam(sanitized, "inventoryText");
        normalizeStringParam(sanitized, "entityText");
        normalizeStringParam(sanitized, "itemText");
        sanitizeBooleanParam(sanitized, "excludePathTracking", true);
        sanitizeBooleanParam(sanitized, "ignoreDamageReset", false);
        sanitizeIntParam(sanitized, "idleMs", 1000, 0);
        sanitizeIntParam(sanitized, "intervalSeconds", 1, 1);
        sanitizeIntParam(sanitized, "minCount", 1, 0);
        sanitizeIntParam(sanitized, "minFilledSlots", 0, 0);
        sanitizeDoubleParam(sanitized, "minDamage", 0.0D, 0.0D);
        sanitizeDoubleParam(sanitized, "hpThreshold", 6.0D, 0.0D);
        sanitizeDoubleParam(sanitized, "foodThreshold", 12.0D, 0.0D);
        if (TRIGGER_PLAYER_LIST.equals(triggerType)) {
            PlayerListTriggerSupport.sanitizeParams(sanitized);
        }
        return sanitized;
    }

    private static void normalizeStringParam(JsonObject params, String key) {
        String value = getStringParam(params, key);
        if (value.isEmpty()) {
            params.remove(key);
        } else {
            params.addProperty(key, value);
        }
    }

    private static void sanitizeIntParam(JsonObject params, String key, int fallback, int minValue) {
        int value = Math.max(minValue, getIntParam(params, key, fallback));
        params.addProperty(key, value);
    }

    private static void sanitizeBooleanParam(JsonObject params, String key, boolean fallback) {
        params.addProperty(key, getBooleanParam(params, key, fallback));
    }

    private static void sanitizeDoubleParam(JsonObject params, String key, double fallback, double minValue) {
        double value = Math.max(minValue, getDoubleParam(params, key, fallback));
        params.addProperty(key, value);
    }

    private static String getStringParam(JsonObject params, String key) {
        if (params == null || key == null || !params.has(key)) {
            return "";
        }
        try {
            return safe(params.get(key).getAsString()).trim();
        } catch (Exception ignored) {
            return "";
        }
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

    private static double getDoubleParam(JsonObject params, String key, double fallback) {
        if (params == null || key == null || !params.has(key)) {
            return fallback;
        }
        try {
            return params.get(key).getAsDouble();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long getLongParam(JsonObject params, String key, long fallback) {
        if (params == null || key == null || !params.has(key)) {
            return fallback;
        }
        try {
            return params.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getBooleanParam(JsonObject params, String key, boolean fallback) {
        if (params == null || key == null || !params.has(key)) {
            return fallback;
        }
        try {
            return params.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String getStringValue(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return "";
        }
        try {
            return safe(object.get(key).getAsString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String buildSearchText(JsonObject eventData) {
        if (eventData == null || eventData.entrySet().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, JsonElement> entry : eventData.entrySet()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static synchronized Path getConfigPath() {
        return ProfileManager.getCurrentProfileDir().resolve("legacy_sequence_trigger_rules.json");
    }

    private static void ensureConfigExists(Path path) {
        if (path == null || Files.exists(path)) {
            return;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("{\"rules\":[]}");
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTrigger] 创建规则文件失败", e);
        }
    }

    private static String normalizeTriggerType(String triggerType) {
        String value = safe(triggerType).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        if (TRIGGER_GUI_OPEN.equals(value)
                || TRIGGER_GUI_CLOSE.equals(value)
                || TRIGGER_CHAT.equals(value)
                || TRIGGER_PACKET.equals(value)
                || TRIGGER_TITLE.equals(value)
                || TRIGGER_ACTIONBAR.equals(value)
                || TRIGGER_SCOREBOARD_CHANGED.equals(value)
                || TRIGGER_BOSSBAR.equals(value)
                || TRIGGER_KEY_INPUT.equals(value)
                || TRIGGER_PLAYER_IDLE.equals(value)
                || TRIGGER_TIMER.equals(value)
                || TRIGGER_HP_LOW.equals(value)
                || TRIGGER_FOOD_LOW.equals(value)
                || TRIGGER_DEATH.equals(value)
                || TRIGGER_RESPAWN.equals(value)
                || TRIGGER_PLAYER_HURT.equals(value)
                || TRIGGER_ATTACK_ENTITY.equals(value)
                || TRIGGER_TARGET_KILL.equals(value)
                || TRIGGER_WORLD_CHANGED.equals(value)
                || TRIGGER_AREA_CHANGED.equals(value)
                || TRIGGER_INVENTORY_CHANGED.equals(value)
                || TRIGGER_INVENTORY_FULL.equals(value)
                || TRIGGER_ENTITY_NEARBY.equals(value)
                || TRIGGER_PLAYER_LIST.equals(value)
                || TRIGGER_ITEM_PICKUP.equals(value)
                || TRIGGER_SERVER_CONNECT.equals(value)
                || TRIGGER_SERVER_DISCONNECT.equals(value)) {
            return value;
        }
        return value;
    }

    private static String getRuleDisplayName(TriggerRule rule) {
        if (rule == null) {
            return "规则";
        }
        if (!isBlank(rule.name)) {
            return safe(rule.name).trim();
        }
        if (!isBlank(rule.sequenceName)) {
            return safe(rule.sequenceName).trim();
        }
        return "未命名规则";
    }

    private static String getTriggerDisplayName(String triggerType) {
        switch (safe(triggerType)) {
            case TRIGGER_GUI_OPEN:
                return "界面打开";
            case TRIGGER_GUI_CLOSE:
                return "界面关闭";
            case TRIGGER_CHAT:
                return "聊天消息";
            case TRIGGER_PACKET:
                return "数据包";
            case TRIGGER_TITLE:
                return "标题文本";
            case TRIGGER_ACTIONBAR:
                return "动作栏提示";
            case TRIGGER_SCOREBOARD_CHANGED:
                return "Scoreboard变化";
            case TRIGGER_BOSSBAR:
                return "Boss血条";
            case TRIGGER_KEY_INPUT:
                return "按键触发";
            case TRIGGER_PLAYER_IDLE:
                return "站立不动";
            case TRIGGER_TIMER:
                return "定时器";
            case TRIGGER_HP_LOW:
                return "低血量";
            case TRIGGER_FOOD_LOW:
                return "低饱食度";
            case TRIGGER_DEATH:
                return "死亡";
            case TRIGGER_RESPAWN:
                return "重生";
            case TRIGGER_PLAYER_HURT:
                return "受到伤害";
            case TRIGGER_ATTACK_ENTITY:
                return "攻击实体";
            case TRIGGER_TARGET_KILL:
                return "击杀目标";
            case TRIGGER_WORLD_CHANGED:
                return "世界切换";
            case TRIGGER_AREA_CHANGED:
                return "区域变化";
            case TRIGGER_INVENTORY_CHANGED:
                return "背包变化";
            case TRIGGER_INVENTORY_FULL:
                return "背包已满";
            case TRIGGER_ENTITY_NEARBY:
                return "附近实体";
            case TRIGGER_PLAYER_LIST:
                return "玩家列表";
            case TRIGGER_ITEM_PICKUP:
                return "拾取物品";
            case TRIGGER_SERVER_CONNECT:
                return "连接服务器";
            case TRIGGER_SERVER_DISCONNECT:
                return "断开服务器";
            default:
                return safe(triggerType).isEmpty() ? "触发器" : safe(triggerType);
        }
    }

    private static String formatDebugDuration(long ms) {
        long safeMs = Math.max(0L, ms);
        if (safeMs < 1000L) {
            return safeMs + "ms";
        }
        if (safeMs < 60_000L) {
            return (safeMs / 1000L) + "s";
        }
        long minutes = safeMs / 60_000L;
        long seconds = (safeMs % 60_000L) / 1000L;
        return minutes + "m" + seconds + "s";
    }

    private static long bucketDurationSeconds(long ms) {
        return Math.max(0L, ms) / 1000L;
    }

    private static long bucketHalf(double value) {
        return Math.round(Math.max(0.0D, value) * 2.0D);
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String onOffText(boolean value) {
        return value ? "开" : "关";
    }

    private static String quote(String text) {
        return "\"" + safe(text) + "\"";
    }

    private static String shortenDebugText(String text, int maxLength) {
        String safeText = safe(text).replace('\n', ' ').replace('\r', ' ').trim();
        if (safeText.length() <= Math.max(0, maxLength)) {
            return safeText;
        }
        return safeText.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? safe(second) : safe(first);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

