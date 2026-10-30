package com.mythos.mythosScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mythos.mythosScriptMod.PerformanceMonitor;
import com.mythos.mythosScriptMod.config.DebugModule;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.gui.GuiInventory;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.system.ConditionalRule;
import com.mythos.mythosScriptMod.system.ProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConditionalExecutionHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        // --- 核心修改：使用规则列表 ---
        public final List<ConditionalRule> rules = new CopyOnWriteArrayList<>();
        private final List<String> categories = new CopyOnWriteArrayList<>();
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) ConditionalExecutionHandler::loadConfig);
            return config;
        }
    }

    public static final ConditionalExecutionHandler INSTANCE = new ConditionalExecutionHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEBUG_LINES_LIMIT = 5;


    private static final String CATEGORY_DEFAULT = "默认";
    private static final String BUILTIN_RULE_NAME = "初始化界面";
    private static final String BUILTIN_SEQUENCE_NAME = "初始化界面";
    private static final double BUILTIN_CENTER_X = 185.51D;
    private static final double BUILTIN_CENTER_Y = 14.0D;
    private static final double BUILTIN_CENTER_Z = -587.42D;

    // --- 运行时状态（per-session，按 NexusSessions.currentTickOwner() 键控）---
    private static final java.util.concurrent.ConcurrentHashMap<String, RuntimeState> STATES =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(
            com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
            k -> new RuntimeState());
    }

    private static final class RuntimeState {
        boolean globalEnabled = true;
        ConditionalRule activeRule = null;
        final List<String> debugLines = new ArrayList<>();
        ConditionalRule antiStuckMonitorRule = null;
        int antiStuckStationaryTicks = 0;
        boolean antiStuckWarnSent = false;
        double antiStuckLastPosX = Double.NaN;
        double antiStuckLastPosY = Double.NaN;
        double antiStuckLastPosZ = Double.NaN;
        ConditionalRule antiStuckEntryGraceRule = null;
        int antiStuckEntryGraceTicks = 0;
    }

    private ConditionalExecutionHandler() {
    }

    private static Path getConfigFile() {
        return ProfileManager.currentConfigFile("conditional_rules.json");
    }

    private static Path getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("conditional_rules.json");
    }

    public static synchronized void saveConfig() {
        try {
            // Older profiles may still contain the retired initialization rule.
            // Remove it before serializing so it cannot return on the next load.
            ensureBuiltinRules();
            ensureCategoriesSynced();
            sanitizeRules();

            Path configFile = getConfigFile();
            Files.createDirectories(configFile.getParent());

            JsonObject root = new JsonObject();
            root.add("categories", GSON.toJsonTree(new ArrayList<>(cfg().categories)));
            root.add("rules", GSON.toJsonTree(snapshotRules()));

            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            Path runtimeFile = getRuntimeConfigFile();
            Files.createDirectories(runtimeFile.getParent());
            JsonObject rjson = new JsonObject();
            rjson.addProperty("globalEnabled", rt().globalEnabled);
            try (BufferedWriter rw = Files.newBufferedWriter(runtimeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(rjson, rw);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("无法保存条件执行规则", e);
        }
    }

    public static synchronized void loadConfig() {
        Path configFile = getConfigFile();
        cfg().rules.clear();
        cfg().categories.clear();
        rt().globalEnabled = true;

        if (!Files.exists(configFile)) {
            ensureBuiltinRules();
            ensureCategoriesSynced();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            List<ConditionalRule> loadedRules = null;
            List<String> loadedCategories = new ArrayList<>();

            if (parsed != null && parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                Path runtimeFile = getRuntimeConfigFile();
                if (Files.exists(runtimeFile)) {
                    try (BufferedReader rr = Files.newBufferedReader(runtimeFile, StandardCharsets.UTF_8)) {
                        JsonElement rp = new JsonParser().parse(rr);
                        if (rp != null && rp.isJsonObject()) {
                            JsonObject rj = rp.getAsJsonObject();
                            if (rj.has("globalEnabled")) {
                                rt().globalEnabled = rj.get("globalEnabled").getAsBoolean();
                            }
                        }
                    }
                } else if (root.has("globalEnabled")) {
                    rt().globalEnabled = root.get("globalEnabled").getAsBoolean();
                }
                if (root.has("categories") && root.get("categories").isJsonArray()) {
                    JsonArray categoryArray = root.getAsJsonArray("categories");
                    for (JsonElement element : categoryArray) {
                        if (element != null && element.isJsonPrimitive()) {
                            loadedCategories.add(element.getAsString());
                        }
                    }
                }
                if (root.has("rules") && root.get("rules").isJsonArray()) {
                    Type listType = new TypeToken<ArrayList<ConditionalRule>>() {
                    }.getType();
                    loadedRules = GSON.fromJson(root.get("rules"), listType);
                }
            } else if (parsed != null && parsed.isJsonArray()) {
                Type listType = new TypeToken<ArrayList<ConditionalRule>>() {
                }.getType();
                loadedRules = GSON.fromJson(parsed, listType);
            }

            if (loadedRules != null) {
                cfg().rules.addAll(loadedRules);
            }
            cfg().categories.addAll(loadedCategories);

            ensureBuiltinRules();
            ensureCategoriesSynced();
            sanitizeRules();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("无法加载条件执行规则", e);
            cfg().rules.clear();
            cfg().categories.clear();
            ensureBuiltinRules();
            ensureCategoriesSynced();
            sanitizeRules();
        }
    }

    /**
     * Removes the initialization rule written by pre-modern versions. The
     * method is idempotent and deliberately public so GUI adapters can clean a
     * legacy in-memory list before presenting it.
     */
    public static synchronized void removeBuiltinRules() {
        boolean removed = false;
        for (ConditionalRule rule : new ArrayList<>(cfg().rules)) {
            if (isBuiltinRule(rule)) {
                cfg().rules.remove(rule);
                removed = true;
            }
        }
        if (rt().activeRule != null && isBuiltinRule(rt().activeRule)) {
            INSTANCE.stopActiveSequence();
            rt().activeRule = null;
            removed = true;
        }
        if (removed) {
            INSTANCE.clearAntiStuckState();
        }
    }

    /** Kept as a source-compatible hook for older callers; it now only purges. */
    private static void ensureBuiltinRules() {
        removeBuiltinRules();
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : cfg().categories) {
            normalized.add(normalizeCategory(category));
        }
        for (ConditionalRule rule : cfg().rules) {
            if (rule == null || isBuiltinRule(rule)) {
                continue;
            }
            rule.normalize();
            rule.category = normalizeCategory(rule.category);
            normalized.add(rule.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        cfg().categories.clear();
        cfg().categories.addAll(normalized);
    }

    private static void sanitizeRules() {
        for (ConditionalRule rule : cfg().rules) {
            if (rule != null) {
                rule.normalize();
            }
        }
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static boolean containsCategoryIgnoreCase(String category) {
        for (String existing : cfg().categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalizeCategory(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeCategoryIgnoreCase(String category) {
        for (int i = 0; i < cfg().categories.size(); i++) {
            if (normalizeCategory(cfg().categories.get(i)).equalsIgnoreCase(normalizeCategory(category))) {
                cfg().categories.remove(i);
                return true;
            }
        }
        return false;
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureBuiltinRules();
        ensureCategoriesSynced();
        return new ArrayList<>(cfg().categories);
    }

    public static synchronized void replaceCategoryOrder(List<String> orderedCategories) {
        ensureBuiltinRules();
        ensureCategoriesSynced();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (orderedCategories != null) {
            for (String category : orderedCategories) {
                normalized.add(normalizeCategory(category));
            }
        }
        for (String category : cfg().categories) {
            normalized.add(normalizeCategory(category));
        }
        cfg().categories.clear();
        cfg().categories.addAll(normalized);
        saveConfig();
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureBuiltinRules();
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        cfg().categories.add(normalized);
        saveConfig();
        return true;
    }

    public static synchronized boolean renameCategory(String oldCategory, String newCategory) {
        String normalizedOld = normalizeCategory(oldCategory);
        String normalizedNew = normalizeCategory(newCategory);
        ensureBuiltinRules();
        ensureCategoriesSynced();

        if (normalizedOld.equalsIgnoreCase(normalizedNew)) {
            return true;
        }
        if (containsCategoryIgnoreCase(normalizedNew)) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < cfg().categories.size(); i++) {
            if (normalizeCategory(cfg().categories.get(i)).equalsIgnoreCase(normalizedOld)) {
                cfg().categories.set(i, normalizedNew);
                changed = true;
                break;
            }
        }

        for (ConditionalRule rule : cfg().rules) {
            if (rule != null && !isBuiltinRule(rule)
                    && normalizeCategory(rule.category).equalsIgnoreCase(normalizedOld)) {
                rule.category = normalizedNew;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveConfig();
        return true;
    }

    public static synchronized boolean deleteCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureBuiltinRules();
        ensureCategoriesSynced();

        boolean changed = removeCategoryIgnoreCase(normalized);
        for (ConditionalRule rule : cfg().rules) {
            if (rule != null && !isBuiltinRule(rule)
                    && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                rule.category = CATEGORY_DEFAULT;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveConfig();
        return true;
    }

    public static boolean isBuiltinRule(ConditionalRule rule) {
        if (rule == null) {
            return false;
        }
        return Objects.equals(rule.name, BUILTIN_RULE_NAME)
                && Objects.equals(rule.sequenceName, BUILTIN_SEQUENCE_NAME)
                && Math.abs(rule.centerX - BUILTIN_CENTER_X) < 0.0001D
                && Math.abs(rule.centerY - BUILTIN_CENTER_Y) < 0.0001D
                && Math.abs(rule.centerZ - BUILTIN_CENTER_Z) < 0.0001D;
    }

    public static List<String> getDebugLinesSnapshot() {
        synchronized (rt().debugLines) {
            return new ArrayList<>(rt().debugLines);
        }
    }

    public static void setGlobalEnabled(boolean enabled) {
        rt().globalEnabled = enabled;
        if (!enabled) {
            INSTANCE.stopRuntimeActivity();
        }
        saveConfig();
    }

    // ------------------------------------------------------------------
    // Runtime-switch accessors (boxed RuntimeState fields)
    // ------------------------------------------------------------------

    public static boolean globalEnabled() { return rt().globalEnabled; }

    public static void setGlobalEnabledSilently(boolean value) { rt().globalEnabled = value; }

    public static boolean isGloballyEnabled() {
        return rt().globalEnabled;
    }

    public static boolean shouldRenderDebugOverlay() {
        return ModConfig.isDebugFlagEnabled(DebugModule.CONDITIONAL_EXECUTION);
    }

    private static void updateDebugLines(List<String> lines) {
        synchronized (rt().debugLines) {
            rt().debugLines.clear();
            if (lines == null || lines.isEmpty()) {
                return;
            }
            int count = Math.min(DEBUG_LINES_LIMIT, lines.size());
            for (int i = 0; i < count; i++) {
                rt().debugLines.add(lines.get(i));
            }
        }
    }

    private static String formatRuleStatus(ConditionalRule rule, boolean inRange, boolean enteredThisTick,
            boolean onCooldown) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule == rt().activeRule ? "§a> " : "§7- ");
        sb.append(rule.name == null || rule.name.trim().isEmpty() ? "<未命名规则>" : rule.name);
        sb.append(" §7[");
        sb.append(inRange ? "§a区域内" : "§c区域外");
        if (enteredThisTick) {
            sb.append("§7, §b刚进入");
        }
        if (rule.runOncePerEntry && rule.hasBeenTriggered) {
            sb.append("§7, §e本次已触发");
        }
        if (onCooldown) {
            sb.append("§7, §6冷却 ").append(String.format("%.1fs", rule.getCooldownRemainingSeconds()));
        } else {
            sb.append("§7, §a就绪");
        }
        sb.append("§7]");
        return sb.toString();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("conditional_execution")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("conditional_execution");
        try {
        if (event == null
                || event.phase != TickEvent.Phase.START
                || event.side != Side.CLIENT
                || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null
                || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null
                || event.player != com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer()) {
            return;
        }

        if (!rt().globalEnabled) {
            stopRuntimeActivity();
            updateDebugLines(new ArrayList<String>() {
                {
                    add("§b[条件执行] §7总开关关闭");
                }
            });
            return;
        }

        if (AutoEscapeHandler.isEmergencyLockActive()) {
            clearAntiStuckState();
            updateDebugLines(new ArrayList<String>() {
                {
                    add("§b[条件执行] §e已被自动逃离临时接管");
                }
            });
            return;
        }

        ensureBuiltinRules();
        sanitizeRules();
        List<ConditionalRule> rulesSnapshot = snapshotRules();
        List<String> runtimeDebugLines = new ArrayList<>();
        runtimeDebugLines.add("§b[条件执行] §f当前活动: "
                + (rt().activeRule != null ? "§a" + rt().activeRule.name : "§7无"));

        ConditionalRule highestPriorityRuleInRange = null;
        ConditionalRule highestPriorityFreshEntryRule = null;
        boolean playerIsInRangeOfAnyEnabledRule = false;

        for (ConditionalRule rule : rulesSnapshot) {
            if (rule == null) {
                continue;
            }
            boolean inRange = rule.enabled && rule.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ);
            boolean enteredThisTick = inRange && !rule.wasPlayerInRangeLastTick;
            boolean onCooldown = rule.isOnCooldown();

            if (rule.enabled) {
                runtimeDebugLines.add(formatRuleStatus(rule, inRange, enteredThisTick, onCooldown));
            }

            if (inRange) {
                playerIsInRangeOfAnyEnabledRule = true;
                if (highestPriorityRuleInRange == null && !onCooldown) {
                    highestPriorityRuleInRange = rule;
                }
                if (enteredThisTick && highestPriorityFreshEntryRule == null && !onCooldown) {
                    highestPriorityFreshEntryRule = rule;
                }
            } else if (rule.wasPlayerInRangeLastTick) {
                rule.resetTrigger();
            }

            rule.wasPlayerInRangeLastTick = inRange;
        }

        updateDebugLines(runtimeDebugLines);

        if (highestPriorityFreshEntryRule != null && highestPriorityFreshEntryRule.antiStuckEnabled) {
            primeAntiStuckEntryGrace(highestPriorityFreshEntryRule);
        }

        if (rt().activeRule != null) {
            if (!rt().activeRule.enabled) {
                stopActiveSequence();
                rt().activeRule = null;
            } else if (!isOwnerSequenceActive()
                    && !rt().activeRule.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ)) {
                stopActiveSequence();
                rt().activeRule = null;
            }
        }

        boolean isSequenceActive = isOwnerSequenceActive() && rt().activeRule != null;

        if (highestPriorityRuleInRange == null && rt().activeRule == null) {
            updateAntiStuckState(false, null);
            return;
        }

        // --- 核心逻辑 ---

        // 1. 处理离开区域的情况
        if (isSequenceActive && rt().activeRule.stopOnExit) {
            // 检查玩家是否已经离开了 *当前活动规则* 的区域
            if (!rt().activeRule.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ)) {
                mythosScriptMod.LOGGER.info("[条件执行] 玩家离开规则 '{}' 的区域，终止序列。", rt().activeRule.name);
                stopActiveSequence();
                rt().activeRule.startCooldown(); // 离开后也启动冷却
                rt().activeRule = null;
                clearAntiStuckState();
                return;
            }
        }

        // 2. 处理进入区域的情况
        if (highestPriorityRuleInRange != null) {
            // 如果没有序列在运行，或者当前运行的序列优先级低于新发现的规则
            if (!isSequenceActive
                    || (rt().activeRule != null
                            && getRulePriorityIndex(rulesSnapshot, highestPriorityRuleInRange) < getRulePriorityIndex(
                                    rulesSnapshot, rt().activeRule))) {

                // 如果有低优先级的在运行，先停止它
                if (isSequenceActive) {
                    mythosScriptMod.LOGGER.info("[条件执行] 发现更高优先级的规则 '{}'，停止当前规则 '{}'。", highestPriorityRuleInRange.name,
                            rt().activeRule.name);
                    stopActiveSequence();
                }

                // 检查 "Run Once" 逻辑
                ConditionalRule candidateRule = highestPriorityFreshEntryRule != null
                        ? highestPriorityFreshEntryRule
                        : highestPriorityRuleInRange;

                if (candidateRule.runOncePerEntry && candidateRule.hasBeenTriggered) {
                    return; // 本次进入已经触发过，跳过
                }

                // 启动新规则
                mythosScriptMod.LOGGER.info("[条件执行] 玩家进入规则 '{}' 的区域，开始执行序列: {}", candidateRule.name,
                        candidateRule.sequenceName);
                rt().activeRule = candidateRule;
                rt().activeRule.hasBeenTriggered = true; // 标记已触发

                runRuleSequence(rt().activeRule, false);
            }
        }

        // 3. 如果玩家不在任何启用规则区域内，重置所有规则的 "Run Once" 状态
        if (!playerIsInRangeOfAnyEnabledRule) {
            for (ConditionalRule rule : rulesSnapshot) {
                rule.resetTrigger();
            }
            if (!isOwnerSequenceActive()) {
                rt().activeRule = null;
            }
        }

        updateAntiStuckState(isOwnerSequenceActive() && rt().activeRule != null,
                highestPriorityRuleInRange);
        } finally {
            timer.stop();
        }
    }

    private static List<ConditionalRule> snapshotRules() {
        if (cfg().rules == null || cfg().rules.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(cfg().rules);
    }

    private static int getRulePriorityIndex(List<ConditionalRule> snapshot, ConditionalRule rule) {
        int idx = snapshot.indexOf(rule);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    private void stopActiveSequence() {
        EmbeddedNavigationHandler.INSTANCE.stop();
        PathSequenceEventListener runner = PathSequenceEventListener.activeRunner();
        if (runner != null) {
            runner.stopTracking();
        }
        GuiInventory.isLooping = false;
    }

    /**
     * Conditional execution owns either the foreground runner or a
     * non-interrupting background runner for the current Nexus session.
     * Looking only at foregroundRunner() makes a background sequence appear
     * idle on the next tick, so it is stopped and restarted continuously.
     */
    private boolean isOwnerSequenceActive() {
        PathSequenceEventListener runner = PathSequenceEventListener.activeRunner();
        return runner != null && runner.isTracking();
    }

    private void stopRuntimeActivity() {
        if (rt().activeRule != null) {
            stopActiveSequence();
        }
        rt().activeRule = null;
        clearAntiStuckState();
    }

    private void primeAntiStuckEntryGrace(ConditionalRule rule) {
        if (rule == null) {
            return;
        }
        rt().antiStuckEntryGraceRule = rule;
        rt().antiStuckEntryGraceTicks = Math.max(1, rule.getAntiStuckTimeoutTicks());
        resetAntiStuckTracking(rule);
    }

    private void updateAntiStuckState(boolean isSequenceActive, ConditionalRule highestPriorityRuleInRange) {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null) {
            clearAntiStuckState();
            return;
        }

        if (rt().antiStuckEntryGraceRule != null) {
            if (rt().antiStuckEntryGraceTicks <= 0
                    || !rt().antiStuckEntryGraceRule.enabled
                    || !rt().antiStuckEntryGraceRule.antiStuckEnabled
                    || !rt().antiStuckEntryGraceRule.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ)) {
                rt().antiStuckEntryGraceRule = null;
                rt().antiStuckEntryGraceTicks = 0;
            } else {
                rt().antiStuckEntryGraceTicks--;
            }
        }

        ConditionalRule monitorRule = null;
        if (rt().activeRule != null
                && rt().activeRule.enabled
                && rt().activeRule.antiStuckEnabled
                && rt().activeRule.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ)
                && (isSequenceActive
                        || (rt().antiStuckEntryGraceRule == rt().activeRule && rt().antiStuckEntryGraceTicks > 0))) {
            monitorRule = rt().activeRule;
        }
        if (monitorRule == null
                && rt().antiStuckEntryGraceRule != null
                && rt().antiStuckEntryGraceTicks > 0
                && rt().antiStuckEntryGraceRule.enabled
                && rt().antiStuckEntryGraceRule.antiStuckEnabled
                && rt().antiStuckEntryGraceRule.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ)) {
            monitorRule = rt().antiStuckEntryGraceRule;
        }
        if (monitorRule == null
                && highestPriorityRuleInRange != null
                && highestPriorityRuleInRange == rt().activeRule
                && highestPriorityRuleInRange.antiStuckEnabled
                && highestPriorityRuleInRange.isInRange(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ)
                && isSequenceActive) {
            monitorRule = highestPriorityRuleInRange;
        }

        if (monitorRule == null) {
            clearAntiStuckState();
            return;
        }

        if (rt().antiStuckMonitorRule != monitorRule) {
            resetAntiStuckTracking(monitorRule);
            return;
        }

        if (Double.isNaN(rt().antiStuckLastPosX) || hasPlayerMoved()) {
            rt().antiStuckStationaryTicks = 0;
            rt().antiStuckWarnSent = false;
            captureAntiStuckPosition();
            return;
        }

        rt().antiStuckStationaryTicks++;
        captureAntiStuckPosition();

        int timeoutTicks = Math.max(20, monitorRule.getAntiStuckTimeoutTicks());
        int warningTicks = timeoutTicks - 20;

        if (warningTicks >= 20 && !rt().antiStuckWarnSent && rt().antiStuckStationaryTicks >= warningTicks) {
            sendAntiStuckWarning(monitorRule, warningTicks / 20, 1);
            rt().antiStuckWarnSent = true;
        }

        if (rt().antiStuckStationaryTicks < timeoutTicks) {
            return;
        }

        sendAntiStuckRestartMessage(monitorRule, timeoutTicks / 20);
        restartRuleSequence(monitorRule);
        primeAntiStuckEntryGrace(monitorRule);
    }

    private void restartRuleSequence(ConditionalRule rule) {
        if (rule == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null) {
            return;
        }

        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        if (sequenceName.isEmpty()) {
            return;
        }
        if (!PathSequenceManager.hasSequence(sequenceName)) {
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString("§c[条件执行] 未找到序列: " + sequenceName));
            return;
        }

        runOnClientThread(() -> {
            if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null) {
                return;
            }
            stopActiveSequence();
            rt().activeRule = rule;
            rt().activeRule.hasBeenTriggered = true;
            runRuleSequence(rt().activeRule, true);
        });
    }

    private void runRuleSequence(ConditionalRule rule, boolean forceSingleExecution) {
        if (rule == null) {
            return;
        }
        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        if (sequenceName.isEmpty()) {
            return;
        }
        runOnClientThread(() -> {
            if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null) {
                return;
            }
            if (forceSingleExecution) {
                PathSequenceManager.runPathSequenceOnce(sequenceName);
                return;
            }

            int explicitLoopCount = rule.loopCount;
            if (explicitLoopCount == 0) {
                return;
            }
            PathSequenceManager.runPathSequenceWithLoopCount(sequenceName, explicitLoopCount);
        });
    }

    private void runOnClientThread(Runnable task) {
        if (task == null) {
            return;
        }
        // Nexus drives background bots from their own bound tick thread. Do
        // not move a bot-owned sequence start onto the Minecraft thread: the
        // queued Runnable would lose currentTickOwner() and bind the run to
        // the human/mounted session instead.
        if (!com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner().isEmpty()) {
            task.run();
            return;
        }
        if (mc.isCallingFromMinecraftThread()) {
            task.run();
            return;
        }
        mc.addScheduledTask(task);
    }

    private void sendAntiStuckWarning(ConditionalRule rule, int elapsedSeconds, int remainSeconds) {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || rule == null) {
            return;
        }
        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString(
                "§e[条件执行] 已在" + sequenceName + "序列中停留超过" + Math.max(1, elapsedSeconds)
                        + "s，将在" + Math.max(1, remainSeconds) + "s后重启序列。"));
    }

    private void sendAntiStuckRestartMessage(ConditionalRule rule, int elapsedSeconds) {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || rule == null) {
            return;
        }
        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString(
                "§c[条件执行] 已在" + sequenceName + "序列中停留超过" + Math.max(1, elapsedSeconds)
                        + "s，正在重启序列。"));
    }

    private boolean hasPlayerMoved() {
        double dx = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX - rt().antiStuckLastPosX;
        double dy = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY - rt().antiStuckLastPosY;
        double dz = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ - rt().antiStuckLastPosZ;
        return dx * dx + dy * dy + dz * dz > 0.0025D;
    }

    private void captureAntiStuckPosition() {
        rt().antiStuckLastPosX = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posX;
        rt().antiStuckLastPosY = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posY;
        rt().antiStuckLastPosZ = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().posZ;
    }

    private void resetAntiStuckTracking(ConditionalRule rule) {
        rt().antiStuckMonitorRule = rule;
        rt().antiStuckStationaryTicks = 0;
        rt().antiStuckWarnSent = false;
        captureAntiStuckPosition();
    }

    private void clearAntiStuckState() {
        rt().antiStuckMonitorRule = null;
        rt().antiStuckStationaryTicks = 0;
        rt().antiStuckWarnSent = false;
        rt().antiStuckLastPosX = Double.NaN;
        rt().antiStuckLastPosY = Double.NaN;
        rt().antiStuckLastPosZ = Double.NaN;
        rt().antiStuckEntryGraceRule = null;
        rt().antiStuckEntryGraceTicks = 0;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null) {
            return;
        }
        if (!rt().globalEnabled) {
            return;
        }

        List<ConditionalRule> rulesSnapshot = snapshotRules();
        if (rulesSnapshot.isEmpty()) {
            return;
        }

        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }

        float partialTicks = event.getPartialTicks();
        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        for (ConditionalRule rule : rulesSnapshot) {
            if (rule == null || !rule.visualizeRange || rule.range <= 0.05D) {
                continue;
            }
            drawRuleRange(rule, viewerX, viewerY, viewerZ);
        }
    }

    private void drawRuleRange(ConditionalRule rule, double viewerX, double viewerY, double viewerZ) {
        if (Double.isNaN(rule.centerX) || Double.isNaN(rule.centerY) || Double.isNaN(rule.centerZ)) {
            return;
        }

        int rgb = rule.getVisualizeBorderColorRgb();
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float fillAlpha = rule == rt().activeRule ? 0.18F : 0.10F;
        float lineAlpha = rule == rt().activeRule ? 0.95F : 0.75F;

        AxisAlignedBB renderBox = new AxisAlignedBB(
                rule.centerX - rule.range,
                rule.centerY - 0.05D,
                rule.centerZ - rule.range,
                rule.centerX + rule.range,
                rule.centerY + 0.05D,
                rule.centerZ + rule.range).offset(-viewerX, -viewerY, -viewerZ).grow(0.001D);
        AxisAlignedBB centerMarker = new AxisAlignedBB(
                rule.centerX - 0.08D,
                rule.centerY - 0.05D,
                rule.centerZ - 0.08D,
                rule.centerX + 0.08D,
                rule.centerY + Math.max(1.2D, Math.min(2.0D, rule.range)),
                rule.centerZ + 0.08D).offset(-viewerX, -viewerY, -viewerZ).grow(0.001D);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(1.8F);

        RenderGlobal.renderFilledBox(renderBox, r, g, b, fillAlpha);
        RenderGlobal.drawSelectionBoundingBox(renderBox, r, g, b, lineAlpha);
        RenderGlobal.renderFilledBox(centerMarker, r, g, b, Math.min(0.35F, fillAlpha + 0.08F));
        RenderGlobal.drawSelectionBoundingBox(centerMarker, r, g, b, Math.min(1.0F, lineAlpha + 0.1F));

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
