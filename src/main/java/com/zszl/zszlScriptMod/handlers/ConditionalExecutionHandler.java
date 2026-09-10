package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.system.ConditionalRule;
import com.zszl.zszlScriptMod.system.ProfileManager;

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
    public static final ConditionalExecutionHandler INSTANCE = new ConditionalExecutionHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEBUG_LINES_LIMIT = 5;

    // --- 核心修改：使用规则列表 ---
    public static boolean globalEnabled = true;
    public static final List<ConditionalRule> rules = new CopyOnWriteArrayList<>();
    private static final List<String> categories = new CopyOnWriteArrayList<>();

    private static final String CATEGORY_DEFAULT = "默认";
    private static final String BUILTIN_RULE_NAME = "初始化界面";
    private static final String BUILTIN_SEQUENCE_NAME = "初始化界面";
    private static final double BUILTIN_CENTER_X = 185.51D;
    private static final double BUILTIN_CENTER_Y = 14.0D;
    private static final double BUILTIN_CENTER_Z = -587.42D;
    private static final double BUILTIN_RANGE = 10.0D;

    // --- 运行时状态 ---
    private static ConditionalRule activeRule = null; // 记录当前是由哪个规则启动的序列
    private static final List<String> debugLines = new ArrayList<>();
    private static ConditionalRule antiStuckMonitorRule = null;
    private static int antiStuckStationaryTicks = 0;
    private static boolean antiStuckWarnSent = false;
    private static double antiStuckLastPosX = Double.NaN;
    private static double antiStuckLastPosY = Double.NaN;
    private static double antiStuckLastPosZ = Double.NaN;
    private static ConditionalRule antiStuckEntryGraceRule = null;
    private static int antiStuckEntryGraceTicks = 0;

    private ConditionalExecutionHandler() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("conditional_rules.json");
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
            root.addProperty("globalEnabled", globalEnabled);
            root.add("categories", GSON.toJsonTree(new ArrayList<>(categories)));
            root.add("rules", GSON.toJsonTree(snapshotRules()));

            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存条件执行规则", e);
        }
    }

    public static synchronized void loadConfig() {
        Path configFile = getConfigFile();
        rules.clear();
        categories.clear();
        globalEnabled = true;

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
                if (root.has("globalEnabled")) {
                    globalEnabled = root.get("globalEnabled").getAsBoolean();
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
                rules.addAll(loadedRules);
            }
            categories.addAll(loadedCategories);

            ensureBuiltinRules();
            ensureCategoriesSynced();
            sanitizeRules();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法加载条件执行规则", e);
            rules.clear();
            categories.clear();
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
        for (ConditionalRule rule : new ArrayList<>(rules)) {
            if (isBuiltinRule(rule)) {
                rules.remove(rule);
                removed = true;
            }
        }
        if (activeRule != null && isBuiltinRule(activeRule)) {
            INSTANCE.stopActiveSequence();
            activeRule = null;
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
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        for (ConditionalRule rule : rules) {
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
        categories.clear();
        categories.addAll(normalized);
    }

    private static void sanitizeRules() {
        for (ConditionalRule rule : rules) {
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
        for (String existing : categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalizeCategory(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeCategoryIgnoreCase(String category) {
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizeCategory(category))) {
                categories.remove(i);
                return true;
            }
        }
        return false;
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureBuiltinRules();
        ensureCategoriesSynced();
        return new ArrayList<>(categories);
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
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        categories.clear();
        categories.addAll(normalized);
        saveConfig();
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureBuiltinRules();
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        categories.add(normalized);
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
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizedOld)) {
                categories.set(i, normalizedNew);
                changed = true;
                break;
            }
        }

        for (ConditionalRule rule : rules) {
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
        for (ConditionalRule rule : rules) {
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
        synchronized (debugLines) {
            return new ArrayList<>(debugLines);
        }
    }

    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
        if (!enabled) {
            INSTANCE.stopRuntimeActivity();
        }
        saveConfig();
    }

    public static boolean isGloballyEnabled() {
        return globalEnabled;
    }

    public static boolean shouldRenderDebugOverlay() {
        return ModConfig.isDebugFlagEnabled(DebugModule.CONDITIONAL_EXECUTION);
    }

    private static void updateDebugLines(List<String> lines) {
        synchronized (debugLines) {
            debugLines.clear();
            if (lines == null || lines.isEmpty()) {
                return;
            }
            int count = Math.min(DEBUG_LINES_LIMIT, lines.size());
            for (int i = 0; i < count; i++) {
                debugLines.add(lines.get(i));
            }
        }
    }

    private static String formatRuleStatus(ConditionalRule rule, boolean inRange, boolean enteredThisTick,
            boolean onCooldown) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule == activeRule ? "§a> " : "§7- ");
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
                || mc.player == null
                || mc.world == null
                || event.player != mc.player) {
            return;
        }

        if (!globalEnabled) {
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
                + (activeRule != null ? "§a" + activeRule.name : "§7无"));

        ConditionalRule highestPriorityRuleInRange = null;
        ConditionalRule highestPriorityFreshEntryRule = null;
        boolean playerIsInRangeOfAnyEnabledRule = false;

        for (ConditionalRule rule : rulesSnapshot) {
            if (rule == null) {
                continue;
            }
            boolean inRange = rule.enabled && rule.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ);
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

        if (activeRule != null) {
            if (!activeRule.enabled) {
                stopActiveSequence();
                activeRule = null;
            } else if (!PathSequenceEventListener.instance.isTracking()
                    && !activeRule.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ)) {
                stopActiveSequence();
                activeRule = null;
            }
        }

        boolean isSequenceActive = PathSequenceEventListener.instance.isTracking() && activeRule != null;

        if (highestPriorityRuleInRange == null && activeRule == null) {
            updateAntiStuckState(false, null);
            return;
        }

        // --- 核心逻辑 ---

        // 1. 处理离开区域的情况
        if (isSequenceActive && activeRule.stopOnExit) {
            // 检查玩家是否已经离开了 *当前活动规则* 的区域
            if (!activeRule.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ)) {
                zszlScriptMod.LOGGER.info("[条件执行] 玩家离开规则 '{}' 的区域，终止序列。", activeRule.name);
                stopActiveSequence();
                activeRule.startCooldown(); // 离开后也启动冷却
                activeRule = null;
                clearAntiStuckState();
                return;
            }
        }

        // 2. 处理进入区域的情况
        if (highestPriorityRuleInRange != null) {
            // 如果没有序列在运行，或者当前运行的序列优先级低于新发现的规则
            if (!isSequenceActive
                    || (activeRule != null
                            && getRulePriorityIndex(rulesSnapshot, highestPriorityRuleInRange) < getRulePriorityIndex(
                                    rulesSnapshot, activeRule))) {

                // 如果有低优先级的在运行，先停止它
                if (isSequenceActive) {
                    zszlScriptMod.LOGGER.info("[条件执行] 发现更高优先级的规则 '{}'，停止当前规则 '{}'。", highestPriorityRuleInRange.name,
                            activeRule.name);
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
                zszlScriptMod.LOGGER.info("[条件执行] 玩家进入规则 '{}' 的区域，开始执行序列: {}", candidateRule.name,
                        candidateRule.sequenceName);
                activeRule = candidateRule;
                activeRule.hasBeenTriggered = true; // 标记已触发

                runRuleSequence(activeRule, false);
            }
        }

        // 3. 如果玩家不在任何启用规则区域内，重置所有规则的 "Run Once" 状态
        if (!playerIsInRangeOfAnyEnabledRule) {
            for (ConditionalRule rule : rulesSnapshot) {
                rule.resetTrigger();
            }
            if (!PathSequenceEventListener.instance.isTracking()) {
                activeRule = null;
            }
        }

        updateAntiStuckState(PathSequenceEventListener.instance.isTracking() && activeRule != null,
                highestPriorityRuleInRange);
        } finally {
            timer.stop();
        }
    }

    private static List<ConditionalRule> snapshotRules() {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(rules);
    }

    private static int getRulePriorityIndex(List<ConditionalRule> snapshot, ConditionalRule rule) {
        int idx = snapshot.indexOf(rule);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    private void stopActiveSequence() {
        EmbeddedNavigationHandler.INSTANCE.stop();
        PathSequenceEventListener.instance.stopTracking();
        GuiInventory.isLooping = false;
    }

    private void stopRuntimeActivity() {
        if (activeRule != null) {
            stopActiveSequence();
        }
        activeRule = null;
        clearAntiStuckState();
    }

    private void primeAntiStuckEntryGrace(ConditionalRule rule) {
        if (rule == null) {
            return;
        }
        antiStuckEntryGraceRule = rule;
        antiStuckEntryGraceTicks = Math.max(1, rule.getAntiStuckTimeoutTicks());
        resetAntiStuckTracking(rule);
    }

    private void updateAntiStuckState(boolean isSequenceActive, ConditionalRule highestPriorityRuleInRange) {
        if (mc.player == null) {
            clearAntiStuckState();
            return;
        }

        if (antiStuckEntryGraceRule != null) {
            if (antiStuckEntryGraceTicks <= 0
                    || !antiStuckEntryGraceRule.enabled
                    || !antiStuckEntryGraceRule.antiStuckEnabled
                    || !antiStuckEntryGraceRule.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ)) {
                antiStuckEntryGraceRule = null;
                antiStuckEntryGraceTicks = 0;
            } else {
                antiStuckEntryGraceTicks--;
            }
        }

        ConditionalRule monitorRule = null;
        if (activeRule != null
                && activeRule.enabled
                && activeRule.antiStuckEnabled
                && activeRule.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ)
                && (isSequenceActive
                        || (antiStuckEntryGraceRule == activeRule && antiStuckEntryGraceTicks > 0))) {
            monitorRule = activeRule;
        }
        if (monitorRule == null
                && antiStuckEntryGraceRule != null
                && antiStuckEntryGraceTicks > 0
                && antiStuckEntryGraceRule.enabled
                && antiStuckEntryGraceRule.antiStuckEnabled
                && antiStuckEntryGraceRule.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ)) {
            monitorRule = antiStuckEntryGraceRule;
        }
        if (monitorRule == null
                && highestPriorityRuleInRange != null
                && highestPriorityRuleInRange == activeRule
                && highestPriorityRuleInRange.antiStuckEnabled
                && highestPriorityRuleInRange.isInRange(mc.player.posX, mc.player.posY, mc.player.posZ)
                && isSequenceActive) {
            monitorRule = highestPriorityRuleInRange;
        }

        if (monitorRule == null) {
            clearAntiStuckState();
            return;
        }

        if (antiStuckMonitorRule != monitorRule) {
            resetAntiStuckTracking(monitorRule);
            return;
        }

        if (Double.isNaN(antiStuckLastPosX) || hasPlayerMoved()) {
            antiStuckStationaryTicks = 0;
            antiStuckWarnSent = false;
            captureAntiStuckPosition();
            return;
        }

        antiStuckStationaryTicks++;
        captureAntiStuckPosition();

        int timeoutTicks = Math.max(20, monitorRule.getAntiStuckTimeoutTicks());
        int warningTicks = timeoutTicks - 20;

        if (warningTicks >= 20 && !antiStuckWarnSent && antiStuckStationaryTicks >= warningTicks) {
            sendAntiStuckWarning(monitorRule, warningTicks / 20, 1);
            antiStuckWarnSent = true;
        }

        if (antiStuckStationaryTicks < timeoutTicks) {
            return;
        }

        sendAntiStuckRestartMessage(monitorRule, timeoutTicks / 20);
        restartRuleSequence(monitorRule);
        primeAntiStuckEntryGrace(monitorRule);
    }

    private void restartRuleSequence(ConditionalRule rule) {
        if (rule == null || mc.player == null) {
            return;
        }

        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        if (sequenceName.isEmpty()) {
            return;
        }
        if (!PathSequenceManager.hasSequence(sequenceName)) {
            mc.player.sendMessage(new TextComponentString("§c[条件执行] 未找到序列: " + sequenceName));
            return;
        }

        runOnClientThread(() -> {
            if (mc.player == null || mc.world == null) {
                return;
            }
            stopActiveSequence();
            activeRule = rule;
            activeRule.hasBeenTriggered = true;
            runRuleSequence(activeRule, true);
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
            if (mc.player == null || mc.world == null) {
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
        if (mc.isCallingFromMinecraftThread()) {
            task.run();
            return;
        }
        mc.addScheduledTask(task);
    }

    private void sendAntiStuckWarning(ConditionalRule rule, int elapsedSeconds, int remainSeconds) {
        if (mc.player == null || rule == null) {
            return;
        }
        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        mc.player.sendMessage(new TextComponentString(
                "§e[条件执行] 已在" + sequenceName + "序列中停留超过" + Math.max(1, elapsedSeconds)
                        + "s，将在" + Math.max(1, remainSeconds) + "s后重启序列。"));
    }

    private void sendAntiStuckRestartMessage(ConditionalRule rule, int elapsedSeconds) {
        if (mc.player == null || rule == null) {
            return;
        }
        String sequenceName = rule.sequenceName == null ? "" : rule.sequenceName.trim();
        mc.player.sendMessage(new TextComponentString(
                "§c[条件执行] 已在" + sequenceName + "序列中停留超过" + Math.max(1, elapsedSeconds)
                        + "s，正在重启序列。"));
    }

    private boolean hasPlayerMoved() {
        double dx = mc.player.posX - antiStuckLastPosX;
        double dy = mc.player.posY - antiStuckLastPosY;
        double dz = mc.player.posZ - antiStuckLastPosZ;
        return dx * dx + dy * dy + dz * dz > 0.0025D;
    }

    private void captureAntiStuckPosition() {
        antiStuckLastPosX = mc.player.posX;
        antiStuckLastPosY = mc.player.posY;
        antiStuckLastPosZ = mc.player.posZ;
    }

    private void resetAntiStuckTracking(ConditionalRule rule) {
        antiStuckMonitorRule = rule;
        antiStuckStationaryTicks = 0;
        antiStuckWarnSent = false;
        captureAntiStuckPosition();
    }

    private void clearAntiStuckState() {
        antiStuckMonitorRule = null;
        antiStuckStationaryTicks = 0;
        antiStuckWarnSent = false;
        antiStuckLastPosX = Double.NaN;
        antiStuckLastPosY = Double.NaN;
        antiStuckLastPosZ = Double.NaN;
        antiStuckEntryGraceRule = null;
        antiStuckEntryGraceTicks = 0;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc == null || mc.world == null) {
            return;
        }
        if (!globalEnabled) {
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
        float fillAlpha = rule == activeRule ? 0.18F : 0.10F;
        float lineAlpha = rule == activeRule ? 0.95F : 0.75F;

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
