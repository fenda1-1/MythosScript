// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/AutoPickupHandler.java
package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.system.AutoPickupRule;
import com.zszl.zszlScriptMod.system.ProfileManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoPickupHandler {
    public static final AutoPickupHandler INSTANCE = new AutoPickupHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PICKUP_GOTO_INTERVAL_TICKS = 5;
    private static final int MAX_SCANNED_LOADED_ENTITIES_PER_TICK = 128;
    private static final int ITEM_SEARCH_SCAN_INTERVAL_TICKS = 3;
    private static final int ITEM_MATCH_CACHE_PRUNE_INTERVAL_TICKS = 40;
    private static final int MAX_ITEM_MATCH_CACHE_SIZE = 1024;
    private static final int NAVIGATION_START_CHECK_TICKS = 12;
    private static final int NAVIGATION_MOVEMENT_TIMEOUT_TICKS = 40;
    private static final double NAVIGATION_MOVEMENT_THRESHOLD_SQ = 0.09D;
    private static final double ITEM_POSITION_QUANTIZATION = 4.0D;

    public static boolean globalEnabled = false;
    public static final List<AutoPickupRule> rules = new CopyOnWriteArrayList<>();
    private static final List<String> categories = new CopyOnWriteArrayList<>();
    private static final String CATEGORY_DEFAULT = "默认";

    private enum State {
        IDLE, SEARCHING, MOVING_TO_ITEM, WAITING_POST_PICKUP, SEQUENCE_RUNNING
    }

    private State currentState = State.IDLE;
    private AutoPickupRule activeRule = null;
    private EntityItem currentTargetItem = null;
    private int postPickupDelayTicks = 0;
    private int lastEnterMessageTick = -99999;
    private int lastGotoTick = -99999;
    private int lastGotoTargetEntityId = Integer.MIN_VALUE;
    private ItemStack currentTargetStackSnapshot = ItemStack.EMPTY;

    // --- 核心修改 1: 添加新的状态标志 ---
    private boolean hasPickedUpAtLeastOneItem = false;
    // --- 修改结束 ---
    private final List<PendingPickupSequenceAction> pendingPickupActions = new ArrayList<>();
    private final Map<String, Map<ItemLocationKey, Integer>> failedPickupAttemptsByRule = new HashMap<>();
    private int antiStuckStationaryTicks = 0;
    private double antiStuckLastPosX = Double.NaN;
    private double antiStuckLastPosY = Double.NaN;
    private double antiStuckLastPosZ = Double.NaN;
    private int lastItemSearchTick = -99999;
    private int lastItemSearchRuleKey = 0;
    private int lastNearestItemEntityId = Integer.MIN_VALUE;
    private boolean lastItemSearchFoundMatch = false;
    private int itemSearchBudgetTick = Integer.MIN_VALUE;
    private int itemSearchEntriesScannedThisTick = 0;
    private int itemSearchRoundRuleKey = 0;
    private int itemSearchRoundCursor = 0;
    private int itemSearchRoundScanned = 0;
    private int itemSearchRoundTargetCount = 0;
    private int itemSearchRoundBestEntityId = Integer.MIN_VALUE;
    private double itemSearchRoundBestDistanceSq = Double.MAX_VALUE;
    private boolean itemSearchRoundInProgress = false;
    private int lastItemMatchCachePruneTick = -99999;
    private final Map<Integer, ItemMatchCacheEntry> itemMatchCache = new HashMap<>();
    private final Map<String, Integer> lastInventoryCounts = new HashMap<>();
    private final Map<String, Integer> suppressedInventoryValidationUntilTick = new HashMap<>();
    private String pendingNavigationRuleKey = "";
    private ItemLocationKey pendingNavigationItemLocation = null;
    private int pendingNavigationEntityId = Integer.MIN_VALUE;
    private int pendingNavigationStartTick = -99999;
    private double pendingNavigationPlayerX = Double.NaN;
    private double pendingNavigationPlayerY = Double.NaN;
    private double pendingNavigationPlayerZ = Double.NaN;

    private AutoPickupHandler() {
    }

    private static final class PendingPickupSequenceAction {
        private final String sequenceName;
        private int remainingTicks;

        private PendingPickupSequenceAction(String sequenceName, int remainingTicks) {
            this.sequenceName = sequenceName == null ? "" : sequenceName.trim();
            this.remainingTicks = Math.max(0, remainingTicks);
        }
    }

    private static final class ItemMatchCacheEntry {
        private final String fingerprint;
        private final String itemName;
        private final String searchableText;

        private ItemMatchCacheEntry(String fingerprint, String itemName, String searchableText) {
            this.fingerprint = fingerprint;
            this.itemName = itemName;
            this.searchableText = searchableText;
        }
    }

    private static final class InventoryCountEntry {
        private final ItemStack sampleStack;
        private int count;

        private InventoryCountEntry(ItemStack sampleStack, int count) {
            this.sampleStack = sampleStack == null ? ItemStack.EMPTY : sampleStack.copy();
            this.count = Math.max(0, count);
        }
    }

    private static final class ItemLocationKey {
        private final int x;
        private final int y;
        private final int z;

        private ItemLocationKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ItemLocationKey)) {
                return false;
            }
            ItemLocationKey other = (ItemLocationKey) obj;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("auto_pickup_rules.json");
    }

    public static synchronized void saveConfig() {
        try {
            ensureCategoriesSynced();
            Path configFile = getConfigFile();
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.addProperty("globalEnabled", globalEnabled);
                root.add("categories", GSON.toJsonTree(new ArrayList<>(categories)));
                root.add("rules", GSON.toJsonTree(rules));
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存自动拾取规则", e);
        }
    }

    public static synchronized void loadConfig() {
        Path configFile = getConfigFile();
        categories.clear();
        if (!Files.exists(configFile)) {
            rules.clear();
            ensureCategoriesSynced();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.has("globalEnabled")) {
                globalEnabled = root.get("globalEnabled").getAsBoolean();
            }
            if (root.has("categories") && root.get("categories").isJsonArray()) {
                for (com.google.gson.JsonElement element : root.getAsJsonArray("categories")) {
                    if (element != null && element.isJsonPrimitive()) {
                        categories.add(element.getAsString());
                    }
                }
            }
            if (root.has("rules")) {
                Type listType = new TypeToken<ArrayList<AutoPickupRule>>() {
                }.getType();
                List<AutoPickupRule> loaded = GSON.fromJson(root.get("rules"), listType);
                rules.clear();
                if (loaded != null) {
                    for (AutoPickupRule rule : loaded) {
                        if (rule == null) {
                            continue;
                        }
                        normalizeRule(rule);
                        rules.add(rule);
                    }
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法加载自动拾取规则", e);
            rules.clear();
        }
        ensureCategoriesSynced();
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        for (AutoPickupRule rule : rules) {
            if (rule == null) {
                continue;
            }
            normalizeRule(rule);
            normalized.add(rule.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        categories.clear();
        categories.addAll(normalized);
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
        ensureCategoriesSynced();
        return new ArrayList<>(categories);
    }

    public static synchronized void replaceCategoryOrder(List<String> orderedCategories) {
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

        for (AutoPickupRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalizedOld)) {
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
        ensureCategoriesSynced();

        boolean changed = removeCategoryIgnoreCase(normalized);
        for (AutoPickupRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
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

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("auto_pickup")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("auto_pickup");
        try {
        if (event.phase != TickEvent.Phase.END
                || event.side != Side.CLIENT
                || event.player != mc.player
                || mc.player == null
                || mc.world == null
                || !globalEnabled) {
            return;
        }
        final int nowTick = mc.player.ticksExisted;
        pruneItemMatchCacheIfNeeded(nowTick);

        if (currentState == State.SEQUENCE_RUNNING) {
            if (!PathSequenceEventListener.instance.isTracking()) {
                currentState = activeRule != null ? State.SEARCHING : State.IDLE;
            }
            if (activeRule != null && !activeRule.isPlayerInside(mc.player.posX, mc.player.posY, mc.player.posZ)) {
                if (activeRule.stopOnExit && PathSequenceEventListener.instance.isTracking()) {
                    PathSequenceEventListener.instance.stopTracking();
                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[自动拾取] 已离开区域，后续序列已停止。"));
                }
                clearFailedAttemptsForRule(activeRule);
                resetState();
            }
            if (currentState == State.SEQUENCE_RUNNING) {
                return;
            }
        }

        AutoPickupRule rulePlayerIsIn = resolveRuleForPlayer(mc.player, rules);

        if (rulePlayerIsIn == null) {
            if (activeRule != null) {
                if (activeRule.stopOnExit && PathSequenceEventListener.instance.isTracking()) {
                    PathSequenceEventListener.instance.stopTracking();
                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[自动拾取] 已离开区域，后续序列已停止。"));
                }
                clearFailedAttemptsForRule(activeRule);
                resetState();
            }
            return;
        }

        if (activeRule != rulePlayerIsIn) {
            if (activeRule != null) {
                clearFailedAttemptsForRule(activeRule);
            }
            resetState();
            activeRule = rulePlayerIsIn;
            // --- 核心修改 2: 进入新区域时，重置拾取标志 ---
            hasPickedUpAtLeastOneItem = false;
            // --- 修改结束 ---
            captureEntryInventorySnapshot(mc.player);
            if (nowTick - lastEnterMessageTick > 60) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.AQUA + "[自动拾取] " + TextFormatting.GREEN + "进入区域: " + activeRule.name));
                lastEnterMessageTick = nowTick;
            }
            currentState = State.SEARCHING;
        }

        processPendingPickupActions();
        updateAntiStuckState();
        updatePendingNavigationAttempt(nowTick);

        switch (currentState) {
        case SEARCHING:
            findAndSetNextTarget(nowTick);
            break;
        case MOVING_TO_ITEM:
            checkIfTargetIsReachedOrGone(nowTick);
            break;
        case WAITING_POST_PICKUP:
            handlePostPickupDelay(nowTick);
            break;
        case IDLE:
            if (activeRule != null) {
                currentState = State.SEARCHING;
            }
            break;
        }

        syncInventoryPickupValidation(nowTick);
        } finally {
            timer.stop();
        }
    }

    public boolean shouldPrioritizeNavigation(EntityPlayerSP player) {
        if (player == null || mc.world == null || !globalEnabled || currentState == State.SEQUENCE_RUNNING) {
            return false;
        }

        AutoPickupRule rule = resolveRuleForPlayer(player, rules);
        if (rule == null) {
            return false;
        }

        if (isItemEligibleForRule(currentTargetItem, rule)) {
            return true;
        }

        int nowTick = player.ticksExisted;
        return findNearestItemInRule(rule, player, nowTick, true) != null
                || isItemSearchRoundInProgress(rule);
    }

    public boolean isPlayerInsideEnabledRule(EntityPlayerSP player) {
        if (player == null || !globalEnabled) {
            return false;
        }
        return resolveRuleForPlayer(player, rules) != null;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc == null || mc.world == null) {
            return;
        }

        if (rules.isEmpty()) {
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

        for (AutoPickupRule rule : rules) {
            if (rule == null || !rule.enabled || !rule.visualizeRange || rule.radius <= 0.05D) {
                continue;
            }
            drawPickupRadiusAura(rule.centerX, rule.centerY, rule.centerZ, viewerX, viewerY, viewerZ, rule.radius);
        }
    }

    private void findAndSetNextTarget(int nowTick) {
        if (activeRule == null || mc.player == null) {
            resetState();
            return;
        }

        EntityItem nearest = findNearestItemInRule(activeRule, mc.player, nowTick, true);
        if (nearest == null && isItemSearchRoundInProgress(activeRule)) {
            return;
        }

        if (nearest != null) {
            boolean targetChanged = currentTargetItem == null
                    || currentTargetItem.isDead
                    || currentTargetItem.getEntityId() != nearest.getEntityId();
            if (targetChanged) {
                clearPendingNavigationAttempt();
            }
            currentTargetItem = nearest;
            currentTargetStackSnapshot = nearest.getItem().copy();
            currentState = State.MOVING_TO_ITEM;
            ensureNavigationToCurrentTarget(nowTick);
            if (targetChanged && ModConfig.isDebugFlagEnabled(DebugModule.AUTO_PICKUP)) {
                mc.player.sendMessage(new TextComponentString(String.format("§d[调试] §7找到目标掉落物: %s @ (%.1f, %.1f, %.1f)",
                currentTargetItem.getItem().getDisplayName(), currentTargetItem.posX, currentTargetItem.posY,
                        currentTargetItem.posZ)));
            }
        } else {
            currentTargetItem = null;
            currentTargetStackSnapshot = ItemStack.EMPTY;
            lastGotoTargetEntityId = Integer.MIN_VALUE;
            lastGotoTick = -99999;
            clearPendingNavigationAttempt();
            if (hasPickedUpAtLeastOneItem) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.AQUA + "[自动拾取] " + TextFormatting.GREEN + "区域内所有物品拾取完毕。"));
                currentState = State.WAITING_POST_PICKUP;
                postPickupDelayTicks = activeRule.postPickupDelaySeconds * 20;
                EmbeddedNavigationHandler.INSTANCE.stop();
            } else {
                currentState = State.SEARCHING;
                if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_PICKUP)) {
                    zszlScriptMod.LOGGER.info("[自动拾取] 区域内未发现掉落物，将持续搜索...");
                }
            }
        }
    }

    private void checkIfTargetIsReachedOrGone(int nowTick) {
        boolean targetGone = (currentTargetItem == null || currentTargetItem.isDead);
        boolean targetReached = !targetGone
                && (mc.player.getDistanceSq(currentTargetItem) < getPickupReachedDistanceSq(activeRule));

        if (targetGone) {
            ItemStack pickedStack = currentTargetStackSnapshot == null ? ItemStack.EMPTY
                    : currentTargetStackSnapshot.copy();
            boolean waitForInventoryValidation = hasRestrictedInventoryDetectionSlots(activeRule);
            if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_PICKUP)) {
                mc.player.sendMessage(new TextComponentString("§d[调试] §7目标已拾取/消失，重新搜索..."));
            }
            hasPickedUpAtLeastOneItem = true;
            if (!waitForInventoryValidation) {
                suppressInventoryValidationForStack(pickedStack, nowTick + 4);
            }
            currentTargetItem = null;
            currentTargetStackSnapshot = ItemStack.EMPTY;
            lastGotoTargetEntityId = Integer.MIN_VALUE;
            lastGotoTick = -99999;
            clearPendingNavigationAttempt();
            if (!waitForInventoryValidation && tryTriggerPickupActionSequence(pickedStack)) {
                return;
            }
            currentState = State.SEARCHING;
        } else if (targetReached) {
            if (lastGotoTargetEntityId == currentTargetItem.getEntityId()) {
                EmbeddedNavigationHandler.INSTANCE.stop();
                lastGotoTargetEntityId = Integer.MIN_VALUE;
                lastGotoTick = -99999;
            }
            clearPendingNavigationAttempt();
        } else {
            ensureNavigationToCurrentTarget(nowTick);
        }
    }

    private void handlePostPickupDelay(int nowTick) {
        if (activeRule != null && mc.player != null) {
            EntityItem nextItem = findNearestItemInRule(activeRule, mc.player, nowTick, true);
            if (nextItem != null) {
                currentTargetItem = nextItem;
                currentState = State.MOVING_TO_ITEM;
                postPickupDelayTicks = 0;
                ensureNavigationToCurrentTarget(nowTick);
                return;
            }
            if (isItemSearchRoundInProgress(activeRule)) {
                return;
            }
        }

        if (postPickupDelayTicks > 0) {
            postPickupDelayTicks--;
            if (postPickupDelayTicks == 0) {
                if (activeRule != null && activeRule.postPickupSequence != null
                        && !activeRule.postPickupSequence.isEmpty()) {
                    mc.player.sendMessage(new TextComponentString(TextFormatting.AQUA + "[自动拾取] "
                            + TextFormatting.YELLOW + "延迟结束，开始执行后续序列: " + activeRule.postPickupSequence));
                    PathSequenceManager.runPathSequence(activeRule.postPickupSequence);
                    currentState = State.SEQUENCE_RUNNING;
                } else {
                    resetState();
                }
            }
        }
    }

    private void resetState() {
        if (currentState == State.MOVING_TO_ITEM || currentState == State.WAITING_POST_PICKUP) {
            EmbeddedNavigationHandler.INSTANCE.stop();
        }
        currentState = State.IDLE;
        activeRule = null;
        currentTargetItem = null;
        currentTargetStackSnapshot = ItemStack.EMPTY;
        postPickupDelayTicks = 0;
        lastGotoTargetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
        clearPendingNavigationAttempt();
        hasPickedUpAtLeastOneItem = false;
        pendingPickupActions.clear();
        antiStuckStationaryTicks = 0;
        antiStuckLastPosX = Double.NaN;
        antiStuckLastPosY = Double.NaN;
        antiStuckLastPosZ = Double.NaN;
        lastItemSearchTick = -99999;
        lastItemSearchRuleKey = 0;
        lastNearestItemEntityId = Integer.MIN_VALUE;
        lastItemSearchFoundMatch = false;
        resetItemSearchRound();
        itemSearchBudgetTick = Integer.MIN_VALUE;
        itemSearchEntriesScannedThisTick = 0;
        itemMatchCache.clear();
        lastInventoryCounts.clear();
        suppressedInventoryValidationUntilTick.clear();
    }

    private void captureEntryInventorySnapshot(EntityPlayerSP player) {
        lastInventoryCounts.clear();
        suppressedInventoryValidationUntilTick.clear();
        if (player == null) {
            return;
        }
        replaceLastInventorySnapshot(captureInventoryCounts(player, activeRule));
    }

    private void syncInventoryPickupValidation(int nowTick) {
        if (activeRule == null || mc.player == null) {
            return;
        }
        pruneSuppressedInventoryValidation(nowTick);
        Map<String, InventoryCountEntry> currentCounts = captureInventoryCounts(mc.player, activeRule);
        if (!hasInventoryCountChanged(currentCounts)) {
            return;
        }
        for (Map.Entry<String, InventoryCountEntry> entry : currentCounts.entrySet()) {
            String fingerprint = entry.getKey();
            InventoryCountEntry current = entry.getValue();
            if (current == null || current.count <= 0 || current.sampleStack == null || current.sampleStack.isEmpty()) {
                continue;
            }

            int previousCount = Math.max(0, lastInventoryCounts.getOrDefault(fingerprint, 0));
            int gained = current.count - previousCount;
            if (gained <= 0) {
                continue;
            }

            if (isInventoryValidationSuppressed(fingerprint, nowTick)) {
                continue;
            }

            handleValidatedInventoryGain(current.sampleStack, gained);
        }
        replaceLastInventorySnapshot(currentCounts);
    }

    private void handleValidatedInventoryGain(ItemStack stack, int gainedCount) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!isStackEligibleForRule(stack, activeRule)) {
            return;
        }
        hasPickedUpAtLeastOneItem = true;
        if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_PICKUP) && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.LIGHT_PURPLE + "[调试] "
                            + TextFormatting.GRAY + "背包新增校验命中: "
                            + TextFormatting.WHITE + stack.getDisplayName()
                            + TextFormatting.GRAY + " x"
                            + TextFormatting.WHITE + gainedCount));
        }
        tryTriggerPickupActionSequence(stack.copy());
    }

    private Map<String, InventoryCountEntry> captureInventoryCounts(EntityPlayerSP player, AutoPickupRule rule) {
        Map<String, InventoryCountEntry> counts = new HashMap<>();
        if (player == null || player.inventory == null || player.inventory.mainInventory == null) {
            return counts;
        }
        List<ItemStack> mainInventory = player.inventory.mainInventory;
        for (int rawSlotIndex = 0; rawSlotIndex < mainInventory.size(); rawSlotIndex++) {
            if (!isInventoryDetectionSlotIncluded(rule, rawSlotIndex)) {
                continue;
            }
            ItemStack stack = mainInventory.get(rawSlotIndex);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String fingerprint = buildItemFingerprint(stack);
            if (fingerprint.isEmpty()) {
                continue;
            }
            InventoryCountEntry entry = counts.get(fingerprint);
            if (entry == null) {
                counts.put(fingerprint, new InventoryCountEntry(stack, stack.getCount()));
            } else {
                entry.count += Math.max(0, stack.getCount());
            }
        }
        return counts;
    }

    private void suppressInventoryValidationForStack(ItemStack stack, int expireTick) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String fingerprint = buildItemFingerprint(stack);
        if (fingerprint.isEmpty()) {
            return;
        }
        suppressedInventoryValidationUntilTick.put(fingerprint, Math.max(0, expireTick));
    }

    private boolean isInventoryValidationSuppressed(String fingerprint, int nowTick) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return false;
        }
        Integer expireTick = suppressedInventoryValidationUntilTick.get(fingerprint);
        return expireTick != null && expireTick >= nowTick;
    }

    private void pruneSuppressedInventoryValidation(int nowTick) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : suppressedInventoryValidationUntilTick.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < nowTick) {
                expired.add(entry.getKey());
            }
        }
        for (String fingerprint : expired) {
            suppressedInventoryValidationUntilTick.remove(fingerprint);
        }
    }

    private boolean hasInventoryCountChanged(Map<String, InventoryCountEntry> currentCounts) {
        if (currentCounts.size() != lastInventoryCounts.size()) {
            return true;
        }
        for (Map.Entry<String, InventoryCountEntry> entry : currentCounts.entrySet()) {
            InventoryCountEntry current = entry.getValue();
            int currentCount = current == null ? 0 : Math.max(0, current.count);
            if (Math.max(0, lastInventoryCounts.getOrDefault(entry.getKey(), 0)) != currentCount) {
                return true;
            }
        }
        return false;
    }

    private void replaceLastInventorySnapshot(Map<String, InventoryCountEntry> snapshot) {
        lastInventoryCounts.clear();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Map.Entry<String, InventoryCountEntry> entry : snapshot.entrySet()) {
            if (entry.getValue() != null && entry.getValue().count > 0) {
                lastInventoryCounts.put(entry.getKey(), entry.getValue().count);
            }
        }
    }

    private AutoPickupRule resolveRuleForPlayer(EntityPlayerSP player, List<AutoPickupRule> rulesSnapshot) {
        if (player == null) {
            return null;
        }

        if (activeRule != null && activeRule.enabled
                && activeRule.isPlayerInside(player.posX, player.posY, player.posZ)) {
            return activeRule;
        }

        for (AutoPickupRule rule : rulesSnapshot) {
            if (rule != null && rule.enabled && rule.isPlayerInside(player.posX, player.posY, player.posZ)) {
                return rule;
            }
        }
        return null;
    }

    private EntityItem findNearestItemInRule(AutoPickupRule rule, EntityPlayerSP player, int nowTick,
            boolean allowCachedResult) {
        if (rule == null || player == null || mc.world == null) {
            return null;
        }

        int ruleKey = buildRuleSearchKey(rule);
        if (itemSearchRoundInProgress && itemSearchRoundRuleKey != ruleKey) {
            resetItemSearchRound();
        }
        if (!itemSearchRoundInProgress && allowCachedResult && ruleKey == lastItemSearchRuleKey
                && nowTick - lastItemSearchTick < ITEM_SEARCH_SCAN_INTERVAL_TICKS) {
            EntityItem cached = resolveCachedNearestItem(rule);
            if (cached != null) {
                return cached;
            }
            if (!lastItemSearchFoundMatch) {
                return null;
            }
        }

        List<Entity> loadedEntities = mc.world.loadedEntityList;
        int loadedCount = loadedEntities == null ? 0 : loadedEntities.size();
        if (itemSearchRoundInProgress && loadedCount != itemSearchRoundTargetCount) {
            resetItemSearchRound();
        }
        if (!itemSearchRoundInProgress) {
            beginItemSearchRound(ruleKey, loadedCount);
        }
        if (loadedCount <= 0) {
            return completeItemSearchRound(rule, nowTick);
        }

        if (itemSearchBudgetTick != nowTick) {
            itemSearchBudgetTick = nowTick;
            itemSearchEntriesScannedThisTick = 0;
        }
        int remainingTickBudget = MAX_SCANNED_LOADED_ENTITIES_PER_TICK - itemSearchEntriesScannedThisTick;
        int remainingRoundEntries = itemSearchRoundTargetCount - itemSearchRoundScanned;
        int scanCount = Math.min(Math.max(0, remainingTickBudget), Math.max(0, remainingRoundEntries));
        int cursor = Math.floorMod(itemSearchRoundCursor, loadedCount);
        for (int scanned = 0; scanned < scanCount; scanned++) {
            Entity entity = loadedEntities.get((cursor + scanned) % loadedCount);
            if (entity instanceof EntityItem) {
                EntityItem item = (EntityItem) entity;
                if (isItemEligibleForRule(item, rule)) {
                    double toPlayerSq = player.getDistanceSq(item);
                    if (toPlayerSq < itemSearchRoundBestDistanceSq) {
                        itemSearchRoundBestDistanceSq = toPlayerSq;
                        itemSearchRoundBestEntityId = item.getEntityId();
                    }
                }
            }
        }
        itemSearchRoundCursor = (cursor + scanCount) % loadedCount;
        itemSearchRoundScanned += scanCount;
        itemSearchEntriesScannedThisTick += scanCount;

        if (itemSearchRoundScanned < itemSearchRoundTargetCount) {
            return null;
        }
        return completeItemSearchRound(rule, nowTick);
    }

    private EntityItem completeItemSearchRound(AutoPickupRule rule, int nowTick) {
        int completedRuleKey = itemSearchRoundRuleKey;
        int nearestEntityId = itemSearchRoundBestEntityId;
        resetItemSearchRound();

        EntityItem nearest = null;
        if (nearestEntityId != Integer.MIN_VALUE && mc.world != null) {
            Entity entity = mc.world.getEntityByID(nearestEntityId);
            if (entity instanceof EntityItem && isItemEligibleForRule((EntityItem) entity, rule)) {
                nearest = (EntityItem) entity;
            }
        }
        if (nearest == null && nearestEntityId != Integer.MIN_VALUE && mc.world != null) {
            beginItemSearchRound(buildRuleSearchKey(rule), mc.world.loadedEntityList.size());
            return null;
        }
        lastItemSearchTick = nowTick;
        lastItemSearchRuleKey = completedRuleKey;
        lastNearestItemEntityId = nearest == null ? Integer.MIN_VALUE : nearest.getEntityId();
        lastItemSearchFoundMatch = nearest != null;
        return nearest;
    }

    private boolean isItemSearchRoundInProgress(AutoPickupRule rule) {
        return itemSearchRoundInProgress && itemSearchRoundRuleKey == buildRuleSearchKey(rule);
    }

    private void beginItemSearchRound(int ruleKey, int loadedCount) {
        itemSearchRoundInProgress = true;
        itemSearchRoundRuleKey = ruleKey;
        itemSearchRoundScanned = 0;
        itemSearchRoundTargetCount = Math.max(0, loadedCount);
        itemSearchRoundBestEntityId = Integer.MIN_VALUE;
        itemSearchRoundBestDistanceSq = Double.MAX_VALUE;
    }

    private void resetItemSearchRound() {
        itemSearchRoundRuleKey = 0;
        itemSearchRoundScanned = 0;
        itemSearchRoundTargetCount = 0;
        itemSearchRoundBestEntityId = Integer.MIN_VALUE;
        itemSearchRoundBestDistanceSq = Double.MAX_VALUE;
        itemSearchRoundInProgress = false;
    }

    private EntityItem resolveCachedNearestItem(AutoPickupRule rule) {
        if (mc.world == null || lastNearestItemEntityId == Integer.MIN_VALUE) {
            return null;
        }
        Entity entity = mc.world.getEntityByID(lastNearestItemEntityId);
        if (!(entity instanceof EntityItem)) {
            return null;
        }
        EntityItem item = (EntityItem) entity;
        return isItemEligibleForRule(item, rule) ? item : null;
    }

    private int buildRuleSearchKey(AutoPickupRule rule) {
        if (rule == null) {
            return 0;
        }
        int result = 17;
        result = 31 * result + Double.valueOf(rule.centerX).hashCode();
        result = 31 * result + Double.valueOf(rule.centerY).hashCode();
        result = 31 * result + Double.valueOf(rule.centerZ).hashCode();
        result = 31 * result + Double.valueOf(rule.radius).hashCode();
        result = 31 * result + Boolean.valueOf(rule.enableItemWhitelist).hashCode();
        result = 31 * result + Boolean.valueOf(rule.enableItemBlacklist).hashCode();
        result = 31 * result + (rule.itemWhitelistEntries == null ? 0 : rule.itemWhitelistEntries.hashCode());
        result = 31 * result + (rule.itemBlacklistEntries == null ? 0 : rule.itemBlacklistEntries.hashCode());
        return result;
    }

    private boolean isItemEligibleForRule(EntityItem item, AutoPickupRule rule) {
        if (item == null || rule == null || item.isDead || !item.onGround) {
            return false;
        }

        double dx = item.posX - rule.centerX;
        double dy = item.posY - rule.centerY;
        double dz = item.posZ - rule.centerZ;
        if (dx * dx + dy * dy + dz * dz > rule.radius * rule.radius) {
            return false;
        }
        if (hasReachedMaxPickupAttempts(item, rule)) {
            return false;
        }

        ItemMatchCacheEntry cacheEntry = getItemMatchCacheEntry(item);
        return matchesRuleFilters(rule, cacheEntry.itemName, cacheEntry.searchableText);
    }

    private boolean isStackEligibleForRule(ItemStack stack, AutoPickupRule rule) {
        if (stack == null || stack.isEmpty() || rule == null) {
            return false;
        }
        return matchesRuleFilters(rule, getFilterableItemName(stack), ItemFilterHandler.buildItemSearchableText(stack));
    }

    private boolean matchesRuleFilters(AutoPickupRule rule, String itemName, String searchableText) {
        if (rule == null) {
            return false;
        }
        if (rule.enableItemBlacklist && matchesRuleEntryList(itemName, searchableText, rule.itemBlacklistEntries)) {
            return false;
        }
        if (rule.enableItemWhitelist) {
            return matchesRuleEntryList(itemName, searchableText, rule.itemWhitelistEntries);
        }
        return true;
    }

    private ItemMatchCacheEntry getItemMatchCacheEntry(EntityItem item) {
        if (item == null || item.getItem() == null || item.getItem().isEmpty()) {
            return new ItemMatchCacheEntry("", "", "");
        }

        int entityId = item.getEntityId();
        ItemStack stack = item.getItem();
        String fingerprint = buildItemFingerprint(stack);
        ItemMatchCacheEntry cached = itemMatchCache.get(entityId);
        if (cached != null && cached.fingerprint.equals(fingerprint)) {
            return cached;
        }

        ItemMatchCacheEntry rebuilt = new ItemMatchCacheEntry(fingerprint, getFilterableItemName(item),
                ItemFilterHandler.buildItemSearchableText(stack));
        itemMatchCache.put(entityId, rebuilt);
        return rebuilt;
    }

    private String buildItemFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String tagText = stack.getTagCompound() == null ? "" : stack.getTagCompound().toString();
        return stack.getItem().getRegistryName() + "|"
                + stack.getMetadata() + "|"
                + stack.getDisplayName() + "|"
                + tagText;
    }

    private void pruneItemMatchCacheIfNeeded(int nowTick) {
        if (nowTick - lastItemMatchCachePruneTick < ITEM_MATCH_CACHE_PRUNE_INTERVAL_TICKS
                && itemMatchCache.size() < MAX_ITEM_MATCH_CACHE_SIZE) {
            return;
        }
        lastItemMatchCachePruneTick = nowTick;
        if (mc.world == null) {
            itemMatchCache.clear();
            return;
        }

        List<Integer> toRemove = new ArrayList<>();
        for (Integer entityId : itemMatchCache.keySet()) {
            Entity entity = mc.world.getEntityByID(entityId);
            if (!(entity instanceof EntityItem) || entity.isDead) {
                toRemove.add(entityId);
            }
        }
        for (Integer entityId : toRemove) {
            itemMatchCache.remove(entityId);
        }
        if (itemMatchCache.size() <= MAX_ITEM_MATCH_CACHE_SIZE) {
            return;
        }

        itemMatchCache.clear();
    }

    private void ensureNavigationToCurrentTarget(int nowTick) {
        if (mc.player == null || currentTargetItem == null || currentTargetItem.isDead) {
            return;
        }
        if (mc.player.getDistanceSq(currentTargetItem) <= getPickupReachedDistanceSq(activeRule)) {
            clearPendingNavigationAttempt();
            return;
        }

        int targetId = currentTargetItem.getEntityId();
        boolean needSendGoto = targetId != lastGotoTargetEntityId
                || (nowTick - lastGotoTick) >= PICKUP_GOTO_INTERVAL_TICKS;
        if (!needSendGoto) {
            return;
        }

        BlockPos pickupGoal = resolveLocalPickupGoal(currentTargetItem);
        if (pickupGoal == null) {
            // Do not let GoalTargetNormalizer climb through every layer in this X/Z column.
            // The normal pickup retry path will mark the item as unreachable if this remains true.
            EmbeddedNavigationHandler.INSTANCE.stop();
            lastGotoTick = nowTick;
            lastGotoTargetEntityId = targetId;
            armPendingNavigationAttempt(nowTick);
            if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_PICKUP)) {
                zszlScriptMod.LOGGER.info(
                        "[自动拾取] 掉落物所在方块上方无本层可用目标，跳过跨层修正: {} @ ({}, {}, {})",
                        currentTargetItem.getItem().getDisplayName(), currentTargetItem.posX,
                        currentTargetItem.posY, currentTargetItem.posZ);
            }
            return;
        }

        startNavigationToPickupGoal(pickupGoal);
        lastGotoTick = nowTick;
        lastGotoTargetEntityId = targetId;
        armPendingNavigationAttempt(nowTick);
    }

    /**
     * Resolves the same local, standable pickup goal used by the rule manager.
     * It avoids scanning through a whole snow column: when the item is inside
     * a non-passable cell, only the immediately adjacent air cell is used.
     */
    public BlockPos resolveLocalPickupGoal(EntityItem item) {
        if (item == null || mc.world == null) {
            return null;
        }

        BlockPos itemBlock = new BlockPos(item.posX, item.posY, item.posZ);
        if (isPickupGoalPassable(itemBlock)) {
            return itemBlock;
        }

        BlockPos directlyAbove = itemBlock.up();
        if (directlyAbove.getY() >= 255) {
            return null;
        }
        return isPickupGoalPassable(directlyAbove) ? directlyAbove : null;
    }

    /**
     * Starts navigation with the rule manager's exact grounded-item goal
     * normalization and normal command throttling.
     */
    public boolean startNavigationToPickupItem(EntityItem item) {
        BlockPos pickupGoal = resolveLocalPickupGoal(item);
        if (pickupGoal == null) {
            EmbeddedNavigationHandler.INSTANCE.stop();
            return false;
        }
        return startNavigationToPickupGoal(pickupGoal);
    }

    private boolean startNavigationToPickupGoal(BlockPos pickupGoal) {
        if (pickupGoal == null) {
            return false;
        }
        return EmbeddedNavigationHandler.INSTANCE.startGoto(pickupGoal.getX() + 0.5D, pickupGoal.getY(),
                pickupGoal.getZ() + 0.5D);
    }

    private boolean isPickupGoalPassable(BlockPos pos) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != null && baritone.getPlayerContext() != null) {
            return MovementHelper.canWalkThrough(baritone.getPlayerContext(), new BetterBlockPos(pos));
        }
        IBlockState state = mc.world.getBlockState(pos);
        return state.getBlock().isPassable(mc.world, pos);
    }

    private void armPendingNavigationAttempt(int nowTick) {
        if (activeRule == null || currentTargetItem == null || mc.player == null) {
            return;
        }
        String ruleKey = buildRuleAttemptKey(activeRule);
        ItemLocationKey locationKey = buildItemLocationKey(currentTargetItem);
        if (locationKey == null) {
            return;
        }
        if (ruleKey.equals(pendingNavigationRuleKey)
                && locationKey.equals(pendingNavigationItemLocation)
                && pendingNavigationEntityId == currentTargetItem.getEntityId()) {
            return;
        }
        pendingNavigationRuleKey = ruleKey;
        pendingNavigationItemLocation = locationKey;
        pendingNavigationEntityId = currentTargetItem.getEntityId();
        pendingNavigationStartTick = nowTick;
        pendingNavigationPlayerX = mc.player.posX;
        pendingNavigationPlayerY = mc.player.posY;
        pendingNavigationPlayerZ = mc.player.posZ;
    }

    private void updatePendingNavigationAttempt(int nowTick) {
        if (pendingNavigationItemLocation == null || mc.player == null) {
            return;
        }
        if (activeRule == null || currentTargetItem == null || currentTargetItem.isDead) {
            clearPendingNavigationAttempt();
            return;
        }

        String currentRuleKey = buildRuleAttemptKey(activeRule);
        ItemLocationKey currentLocation = buildItemLocationKey(currentTargetItem);
        if (!currentRuleKey.equals(pendingNavigationRuleKey)
                || currentLocation == null
                || !currentLocation.equals(pendingNavigationItemLocation)
                || currentTargetItem.getEntityId() != pendingNavigationEntityId) {
            clearPendingNavigationAttempt();
            return;
        }

        if (hasPlayerMovedSincePendingNavigation()) {
            clearPendingNavigationAttempt();
            return;
        }

        boolean navigationEngaged = EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating();
        int elapsedTicks = nowTick - pendingNavigationStartTick;
        if (!navigationEngaged && elapsedTicks < NAVIGATION_START_CHECK_TICKS) {
            return;
        }
        if (navigationEngaged && elapsedTicks < NAVIGATION_MOVEMENT_TIMEOUT_TICKS) {
            return;
        }

        handleNavigationAttemptFailure();
    }

    private void handleNavigationAttemptFailure() {
        if (activeRule == null || currentTargetItem == null) {
            clearPendingNavigationAttempt();
            return;
        }

        EntityItem failedItem = currentTargetItem;
        ItemLocationKey locationKey = pendingNavigationItemLocation;
        int maxAttempts = getMaxPickupAttempts(activeRule);
        int attempts = incrementFailedAttempts(activeRule, locationKey);

        EmbeddedNavigationHandler.INSTANCE.stop();
        currentTargetItem = null;
        currentTargetStackSnapshot = ItemStack.EMPTY;
        lastGotoTargetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
        clearPendingNavigationAttempt();
        currentState = State.SEARCHING;

        if (mc.player != null && ModConfig.isDebugFlagEnabled(DebugModule.AUTO_PICKUP)) {
            mc.player.sendMessage(new TextComponentString(String.format(
                    "§d[调试] §7掉落物未开始寻路，记录尝试 %d/%d: %s @ (%.2f, %.2f, %.2f)",
                    attempts,
                    maxAttempts,
                    failedItem.getItem().getDisplayName(),
                    failedItem.posX,
                    failedItem.posY,
                    failedItem.posZ)));
        }
        if (mc.player != null && attempts >= maxAttempts) {
            mc.player.sendMessage(new TextComponentString(String.format(
                    "%s[自动拾取] %s多次寻路无效，已暂时跳过: %s%s %s@ (%.1f, %.1f, %.1f)%s，离开并重新进入区域后会重试。",
                    TextFormatting.YELLOW,
                    TextFormatting.RED,
                    TextFormatting.WHITE,
                    failedItem.getItem().getDisplayName(),
                    TextFormatting.GRAY,
                    failedItem.posX,
                    failedItem.posY,
                    failedItem.posZ,
                    TextFormatting.YELLOW)));
        }
    }

    private boolean hasPlayerMovedSincePendingNavigation() {
        double dx = mc.player.posX - pendingNavigationPlayerX;
        double dy = mc.player.posY - pendingNavigationPlayerY;
        double dz = mc.player.posZ - pendingNavigationPlayerZ;
        return dx * dx + dy * dy + dz * dz > NAVIGATION_MOVEMENT_THRESHOLD_SQ;
    }

    private void clearPendingNavigationAttempt() {
        pendingNavigationRuleKey = "";
        pendingNavigationItemLocation = null;
        pendingNavigationEntityId = Integer.MIN_VALUE;
        pendingNavigationStartTick = -99999;
        pendingNavigationPlayerX = Double.NaN;
        pendingNavigationPlayerY = Double.NaN;
        pendingNavigationPlayerZ = Double.NaN;
    }

    private int incrementFailedAttempts(AutoPickupRule rule, ItemLocationKey locationKey) {
        if (rule == null || locationKey == null) {
            return 0;
        }
        String ruleKey = buildRuleAttemptKey(rule);
        Map<ItemLocationKey, Integer> attempts = failedPickupAttemptsByRule.get(ruleKey);
        if (attempts == null) {
            attempts = new HashMap<>();
            failedPickupAttemptsByRule.put(ruleKey, attempts);
        }
        int next = attempts.containsKey(locationKey) ? attempts.get(locationKey) + 1 : 1;
        attempts.put(locationKey, next);
        return next;
    }

    private boolean hasReachedMaxPickupAttempts(EntityItem item, AutoPickupRule rule) {
        if (item == null || rule == null) {
            return false;
        }
        ItemLocationKey locationKey = buildItemLocationKey(item);
        if (locationKey == null) {
            return false;
        }
        Map<ItemLocationKey, Integer> attempts = failedPickupAttemptsByRule.get(buildRuleAttemptKey(rule));
        if (attempts == null) {
            return false;
        }
        Integer count = attempts.get(locationKey);
        return count != null && count >= getMaxPickupAttempts(rule);
    }

    private void clearFailedAttemptsForRule(AutoPickupRule rule) {
        if (rule == null) {
            return;
        }
        failedPickupAttemptsByRule.remove(buildRuleAttemptKey(rule));
        if (buildRuleAttemptKey(rule).equals(pendingNavigationRuleKey)) {
            clearPendingNavigationAttempt();
        }
    }

    private int getMaxPickupAttempts(AutoPickupRule rule) {
        if (rule == null) {
            return AutoPickupRule.DEFAULT_MAX_PICKUP_ATTEMPTS;
        }
        return Math.max(1, rule.maxPickupAttempts);
    }

    private String buildRuleAttemptKey(AutoPickupRule rule) {
        if (rule == null) {
            return "";
        }
        return normalizeCategory(rule.category)
                + "|" + safeRuleName(rule.name)
                + "|" + roundAttemptKeyValue(rule.centerX)
                + "|" + roundAttemptKeyValue(rule.centerY)
                + "|" + roundAttemptKeyValue(rule.centerZ)
                + "|" + roundAttemptKeyValue(rule.radius);
    }

    private static String safeRuleName(String name) {
        return name == null ? "" : name.trim();
    }

    private static long roundAttemptKeyValue(double value) {
        return Math.round(value * 1000.0D);
    }

    private ItemLocationKey buildItemLocationKey(EntityItem item) {
        if (item == null) {
            return null;
        }
        return new ItemLocationKey(
                (int) Math.round(item.posX * ITEM_POSITION_QUANTIZATION),
                (int) Math.round(item.posY * ITEM_POSITION_QUANTIZATION),
                (int) Math.round(item.posZ * ITEM_POSITION_QUANTIZATION));
    }

    private static void normalizeRule(AutoPickupRule rule) {
        if (rule == null) {
            return;
        }
        rule.category = normalizeCategory(rule.category);
        if (rule.targetReachDistance <= 0.0D) {
            rule.targetReachDistance = AutoPickupRule.DEFAULT_TARGET_REACH_DISTANCE;
        }
        if (rule.maxPickupAttempts < 1) {
            rule.maxPickupAttempts = AutoPickupRule.DEFAULT_MAX_PICKUP_ATTEMPTS;
        }
        rule.itemWhitelist = normalizeRuleNameList(rule.itemWhitelist);
        rule.itemBlacklist = normalizeRuleNameList(rule.itemBlacklist);
        rule.itemWhitelistEntries = normalizeRuleEntryList(rule.itemWhitelistEntries, rule.itemWhitelist);
        rule.itemBlacklistEntries = normalizeRuleEntryList(rule.itemBlacklistEntries, rule.itemBlacklist);
        rule.pickupActionEntries = normalizePickupActionEntryList(rule.pickupActionEntries);
        rule.inventoryDetectionSlots = normalizeInventoryDetectionSlots(rule.inventoryDetectionSlots);
        rule.itemWhitelist = flattenEntryKeywords(rule.itemWhitelistEntries);
        rule.itemBlacklist = flattenEntryKeywords(rule.itemBlacklistEntries);
        if (rule.postPickupSequence == null) {
            rule.postPickupSequence = "";
        }
        if (rule.antiStuckRestartSequence == null) {
            rule.antiStuckRestartSequence = "";
        }
        rule.postPickupDelaySeconds = Math.max(0, rule.postPickupDelaySeconds);
        rule.antiStuckTimeoutSeconds = Math.max(1, rule.antiStuckTimeoutSeconds);
    }

    private static List<Integer> normalizeInventoryDetectionSlots(List<Integer> source) {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        if (source != null) {
            for (Integer slot : source) {
                if (slot == null) {
                    continue;
                }
                int slotIndex = slot.intValue();
                if (slotIndex >= 0 && slotIndex < AutoPickupRule.INVENTORY_SLOT_COUNT) {
                    normalized.add(slotIndex);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean hasRestrictedInventoryDetectionSlots(AutoPickupRule rule) {
        return rule != null && rule.inventoryDetectionSlots != null && !rule.inventoryDetectionSlots.isEmpty();
    }

    private boolean isInventoryDetectionSlotIncluded(AutoPickupRule rule, int rawSlotIndex) {
        if (!hasRestrictedInventoryDetectionSlots(rule)) {
            return true;
        }
        int visibleSlotIndex = toVisibleInventorySlotIndex(rawSlotIndex);
        return visibleSlotIndex >= 0 && rule.inventoryDetectionSlots.contains(visibleSlotIndex);
    }

    private int toVisibleInventorySlotIndex(int rawSlotIndex) {
        if (rawSlotIndex < 0 || rawSlotIndex >= AutoPickupRule.INVENTORY_SLOT_COUNT) {
            return -1;
        }
        if (rawSlotIndex < 9) {
            return 27 + rawSlotIndex;
        }
        return rawSlotIndex - 9;
    }

    private double getPickupReachedDistanceSq(AutoPickupRule rule) {
        double distance = getPickupReachedDistance(rule);
        return distance * distance;
    }

    private double getPickupReachedDistance(AutoPickupRule rule) {
        if (rule == null || rule.targetReachDistance <= 0.0D) {
            return AutoPickupRule.DEFAULT_TARGET_REACH_DISTANCE;
        }
        return rule.targetReachDistance;
    }

    private static List<String> normalizeRuleNameList(List<String> source) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (source != null) {
            for (String entry : source) {
                String normalized = KillAuraHandler.normalizeFilterName(entry);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private static boolean matchesRuleNameList(String itemName, List<String> filters) {
        return KillAuraHandler.getNameListMatchIndex(itemName, filters) != Integer.MAX_VALUE;
    }

    private static List<AutoPickupRule.ItemMatchEntry> normalizeRuleEntryList(List<AutoPickupRule.ItemMatchEntry> source,
            List<String> legacyKeywords) {
        List<AutoPickupRule.ItemMatchEntry> normalized = new ArrayList<>();
        if (source != null) {
            for (AutoPickupRule.ItemMatchEntry entry : source) {
                AutoPickupRule.ItemMatchEntry normalizedEntry = normalizeRuleEntry(entry);
                if (normalizedEntry != null) {
                    normalized.add(normalizedEntry);
                }
            }
        }
        if (normalized.isEmpty() && legacyKeywords != null) {
            for (String keyword : legacyKeywords) {
                String normalizedKeyword = KillAuraHandler.normalizeFilterName(keyword);
                if (normalizedKeyword.isEmpty()) {
                    continue;
                }
                AutoPickupRule.ItemMatchEntry entry = new AutoPickupRule.ItemMatchEntry();
                entry.keyword = normalizedKeyword;
                normalized.add(entry);
            }
        }
        return normalized;
    }

    private static AutoPickupRule.ItemMatchEntry normalizeRuleEntry(AutoPickupRule.ItemMatchEntry source) {
        if (source == null) {
            return null;
        }
        String normalizedKeyword = KillAuraHandler.normalizeFilterName(source.keyword);
        List<String> normalizedTags = normalizeRuleNameList(source.requiredNbtTags);
        if (normalizedKeyword.isEmpty() && normalizedTags.isEmpty()) {
            return null;
        }

        AutoPickupRule.ItemMatchEntry entry = new AutoPickupRule.ItemMatchEntry();
        entry.keyword = normalizedKeyword;
        entry.requiredNbtTags = normalizedTags;
        return entry;
    }

    private static List<String> flattenEntryKeywords(List<AutoPickupRule.ItemMatchEntry> entries) {
        List<String> keywords = new ArrayList<>();
        if (entries == null) {
            return keywords;
        }
        for (AutoPickupRule.ItemMatchEntry entry : entries) {
            String keyword = entry == null ? "" : KillAuraHandler.normalizeFilterName(entry.keyword);
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return normalizeRuleNameList(keywords);
    }

    private static List<AutoPickupRule.PickupActionEntry> normalizePickupActionEntryList(
            List<AutoPickupRule.PickupActionEntry> source) {
        List<AutoPickupRule.PickupActionEntry> normalized = new ArrayList<>();
        if (source == null) {
            return normalized;
        }
        for (AutoPickupRule.PickupActionEntry entry : source) {
            AutoPickupRule.PickupActionEntry normalizedEntry = normalizePickupActionEntry(entry);
            if (normalizedEntry != null) {
                normalized.add(normalizedEntry);
            }
        }
        return normalized;
    }

    private static AutoPickupRule.PickupActionEntry normalizePickupActionEntry(AutoPickupRule.PickupActionEntry source) {
        if (source == null) {
            return null;
        }
        String normalizedKeyword = KillAuraHandler.normalizeFilterName(source.keyword);
        List<String> normalizedTags = normalizeRuleNameList(source.requiredNbtTags);
        String sequenceName = source.sequenceName == null ? "" : source.sequenceName.trim();
        if (sequenceName.isEmpty()) {
            return null;
        }

        AutoPickupRule.PickupActionEntry entry = new AutoPickupRule.PickupActionEntry();
        entry.keyword = normalizedKeyword;
        entry.requiredNbtTags = normalizedTags;
        entry.sequenceName = sequenceName;
        entry.executeDelaySeconds = Math.max(0, source.executeDelaySeconds);
        return entry;
    }

    private static boolean matchesRuleEntryList(String itemName, String searchableText,
            List<AutoPickupRule.ItemMatchEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        String safeItemName = itemName == null ? "" : itemName;
        String safeSearchableText = searchableText == null ? "" : searchableText;
        for (AutoPickupRule.ItemMatchEntry entry : entries) {
            if (matchesRuleEntry(safeItemName, safeSearchableText, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRuleEntry(String itemName, String searchableText, AutoPickupRule.ItemMatchEntry entry) {
        if (entry == null) {
            return false;
        }

        String keyword = KillAuraHandler.normalizeFilterName(entry.keyword);
        List<String> nbtTags = normalizeRuleNameList(entry.requiredNbtTags);
        if (keyword.isEmpty() && nbtTags.isEmpty()) {
            return false;
        }

        if (!keyword.isEmpty() && itemName.contains(keyword)) {
            return true;
        }

        if (!nbtTags.isEmpty()) {
            for (String tag : nbtTags) {
                if (!tag.isEmpty() && safeContainsIgnoreCase(searchableText, tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryTriggerPickupActionSequence(ItemStack pickedStack) {
        if (activeRule == null || pickedStack == null || pickedStack.isEmpty()) {
            return false;
        }
        AutoPickupRule.PickupActionEntry matchedEntry = findMatchingPickupActionEntry(activeRule, pickedStack);
        if (matchedEntry == null) {
            return false;
        }

        String sequenceName = matchedEntry.sequenceName == null ? "" : matchedEntry.sequenceName.trim();
        if (sequenceName.isEmpty() || !PathSequenceManager.hasSequence(sequenceName)) {
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "[自动拾取] 拾取执行序列不存在: " + TextFormatting.WHITE + sequenceName));
            }
            currentState = State.SEARCHING;
            return false;
        }

        queuePendingPickupAction(sequenceName, Math.max(0, matchedEntry.executeDelaySeconds) * 20);
        if (mc.player != null) {
            String delayText = matchedEntry.executeDelaySeconds > 0
                    ? TextFormatting.GRAY + "，" + TextFormatting.YELLOW + matchedEntry.executeDelaySeconds
                            + TextFormatting.GRAY + " 秒后执行"
                    : "";
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.AQUA + "[自动拾取] "
                            + TextFormatting.YELLOW + "拾取到匹配物品，已排入执行序列: "
                            + TextFormatting.WHITE + sequenceName
                            + delayText));
        }
        return true;
    }

    private void queuePendingPickupAction(String sequenceName, int delayTicks) {
        String normalizedSequenceName = sequenceName == null ? "" : sequenceName.trim();
        if (normalizedSequenceName.isEmpty()) {
            return;
        }
        pendingPickupActions.add(new PendingPickupSequenceAction(normalizedSequenceName, delayTicks));
    }

    private void processPendingPickupActions() {
        if (currentState == State.SEQUENCE_RUNNING || pendingPickupActions.isEmpty()) {
            return;
        }

        for (int i = 0; i < pendingPickupActions.size(); i++) {
            PendingPickupSequenceAction action = pendingPickupActions.get(i);
            if (action == null) {
                continue;
            }
            if (action.remainingTicks > 0) {
                action.remainingTicks--;
            }
            if (action.remainingTicks > 0) {
                continue;
            }

            EmbeddedNavigationHandler.INSTANCE.stop();
            currentState = State.SEQUENCE_RUNNING;
            PathSequenceManager.runPathSequenceOnce(action.sequenceName);
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.AQUA + "[自动拾取] "
                                + TextFormatting.YELLOW + "开始执行拾取序列: "
                                + TextFormatting.WHITE + action.sequenceName));
            }
            pendingPickupActions.remove(i);
            return;
        }
    }

    private void updateAntiStuckState() {
        if (mc.player == null || activeRule == null || currentState == State.SEQUENCE_RUNNING
                || !activeRule.antiStuckEnabled) {
            antiStuckStationaryTicks = 0;
            antiStuckLastPosX = Double.NaN;
            antiStuckLastPosY = Double.NaN;
            antiStuckLastPosZ = Double.NaN;
            return;
        }

        if (Double.isNaN(antiStuckLastPosX)) {
            captureAntiStuckPosition();
            antiStuckStationaryTicks = 0;
            return;
        }

        if (hasPlayerMoved()) {
            antiStuckStationaryTicks = 0;
            captureAntiStuckPosition();
            return;
        }

        antiStuckStationaryTicks++;
        captureAntiStuckPosition();
        int timeoutTicks = Math.max(20, activeRule.antiStuckTimeoutSeconds * 20);
        if (antiStuckStationaryTicks < timeoutTicks) {
            return;
        }

        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + "[自动拾取] 检测到停留超过 "
                            + TextFormatting.WHITE + activeRule.antiStuckTimeoutSeconds
                            + TextFormatting.YELLOW + " 秒，正在重启当前规则。"));
        }
        EmbeddedNavigationHandler.INSTANCE.stop();
        currentTargetItem = null;
        currentTargetStackSnapshot = ItemStack.EMPTY;
        lastGotoTargetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
        clearPendingNavigationAttempt();
        antiStuckStationaryTicks = 0;
        String restartSequence = activeRule == null ? "" : safeSequenceName(activeRule.antiStuckRestartSequence);
        if (!restartSequence.isEmpty() && PathSequenceManager.hasSequence(restartSequence)) {
            currentState = State.SEQUENCE_RUNNING;
            PathSequenceManager.runPathSequenceOnce(restartSequence);
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.AQUA + "[自动拾取] "
                                + TextFormatting.YELLOW + "开始执行防卡重启序列: "
                                + TextFormatting.WHITE + restartSequence));
            }
            return;
        }
        currentState = State.SEARCHING;
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

    private AutoPickupRule.PickupActionEntry findMatchingPickupActionEntry(AutoPickupRule rule, ItemStack stack) {
        if (rule == null || stack == null || stack.isEmpty()) {
            return null;
        }
        List<AutoPickupRule.PickupActionEntry> entries = normalizePickupActionEntryList(rule.pickupActionEntries);
        if (entries.isEmpty()) {
            return null;
        }

        String itemName = KillAuraHandler.normalizeFilterName(stack.getDisplayName());
        String searchableText = ItemFilterHandler.buildItemSearchableText(stack);
        for (AutoPickupRule.PickupActionEntry entry : entries) {
            if (matchesPickupActionEntry(itemName, searchableText, entry)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean matchesPickupActionEntry(String itemName, String searchableText,
            AutoPickupRule.PickupActionEntry entry) {
        if (entry == null) {
            return false;
        }

        String keyword = KillAuraHandler.normalizeFilterName(entry.keyword);
        List<String> nbtTags = normalizeRuleNameList(entry.requiredNbtTags);
        boolean keywordMatched = keyword.isEmpty() || itemName.contains(keyword);
        if (!keywordMatched) {
            return false;
        }
        if (nbtTags.isEmpty()) {
            return true;
        }
        for (String tag : nbtTags) {
            if (!tag.isEmpty() && safeContainsIgnoreCase(searchableText, tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeContainsIgnoreCase(String source, String token) {
        if (source == null || token == null) {
            return false;
        }
        String normalizedToken = token.trim().toLowerCase(Locale.ROOT);
        if (normalizedToken.isEmpty()) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(normalizedToken);
    }

    private static String safeSequenceName(String value) {
        return value == null ? "" : value.trim();
    }

    private static String getFilterableItemName(EntityItem item) {
        if (item == null) {
            return "";
        }
        return getFilterableItemName(item.getItem());
    }

    private static String getFilterableItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String normalized = KillAuraHandler.normalizeFilterName(stack.getDisplayName());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (stack.getItem() != null && stack.getItem().getRegistryName() != null) {
            return KillAuraHandler.normalizeFilterName(stack.getItem().getRegistryName().toString());
        }
        return "";
    }

    private void drawPickupRadiusAura(double centerX, double centerY, double centerZ, double viewerX, double viewerY,
            double viewerZ, double radius) {
        double safeRadius = Math.max(0.5D, radius);
        int segments = Math.max(36, (int) Math.round(safeRadius * 10.0D));

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(1.8F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(centerX - viewerX, centerY - viewerY, centerZ - viewerZ).color(0.30F, 1.0F, 0.55F, 0.08F)
                .endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double ringX = centerX + Math.cos(angle) * safeRadius;
            double ringZ = centerZ + Math.sin(angle) * safeRadius;
            buffer.pos(ringX - viewerX, centerY - viewerY, ringZ - viewerZ).color(0.20F, 0.95F, 0.50F, 0.03F)
                    .endVertex();
        }
        tessellator.draw();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double ringX = centerX + Math.cos(angle) * safeRadius;
            double ringZ = centerZ + Math.sin(angle) * safeRadius;
            buffer.pos(ringX - viewerX, centerY - viewerY, ringZ - viewerZ).color(0.32F, 1.0F, 0.58F, 0.88F)
                    .endVertex();
        }
        tessellator.draw();

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

