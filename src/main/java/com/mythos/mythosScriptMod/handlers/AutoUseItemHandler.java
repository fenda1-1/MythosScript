package com.mythos.mythosScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mythos.mythosScriptMod.system.AutoUseItemRule;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.ModUtils;
import com.mythos.mythosScriptMod.mythosScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoUseItemHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public final List<AutoUseItemRule> rules = new CopyOnWriteArrayList<>();
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) AutoUseItemHandler::loadConfig);
            return config;
        }
    }

    public static final AutoUseItemHandler INSTANCE = new AutoUseItemHandler();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CATEGORY_DEFAULT = "默认";

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    // Per-session runtime state. DelayScheduler pins scheduled tasks to the
    // owner that scheduled them, so these flags never cross sessions.
    private static final class RuntimeState {
        private boolean globalEnabled = false;
        private long nextCheckAtMs = 0L;
        private boolean hotbarUseActionPending = false;
        private int hotbarUseActionToken = 0;
        private boolean hotbarUseRestorePending = false;
        private int hotbarUseRestoreToken = 0;
    }

    private AutoUseItemHandler() {
    }

    // ------------------------------------------------------------------
    // Runtime-switch accessors (boxed RuntimeState fields)
    // ------------------------------------------------------------------

    public static boolean globalEnabled() { return rt().globalEnabled; }

    public static void setGlobalEnabledSilently(boolean value) { rt().globalEnabled = value; }

    private static Path getConfigFile() {
        return ProfileManager.currentConfigFile("auto_use_item_rules.json");
    }

    private static Path getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("auto_use_item_rules.json");
    }

    public static synchronized void loadConfig() {
        Path configFile = getConfigFile();
        cfg().categories.clear();
        if (!Files.exists(configFile)) {
            cfg().rules.clear();
            ensureCategoriesSynced();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            Path runtimeFile = getRuntimeConfigFile();
            if (Files.exists(runtimeFile)) {
                try (BufferedReader rr = Files.newBufferedReader(runtimeFile, StandardCharsets.UTF_8)) {
                    JsonObject rj = new JsonParser().parse(rr).getAsJsonObject();
                    if (rj.has("globalEnabled")) {
                        rt().globalEnabled = rj.get("globalEnabled").getAsBoolean();
                    }
                }
            } else if (root.has("globalEnabled")) {
                rt().globalEnabled = root.get("globalEnabled").getAsBoolean();
            }

            if (root.has("categories") && root.get("categories").isJsonArray()) {
                for (com.google.gson.JsonElement element : root.getAsJsonArray("categories")) {
                    if (element != null && element.isJsonPrimitive()) {
                        cfg().categories.add(element.getAsString());
                    }
                }
            }

            cfg().rules.clear();
            if (root.has("rules")) {
                Type listType = new TypeToken<ArrayList<AutoUseItemRule>>() {
                }.getType();
                List<AutoUseItemRule> loaded = GSON.fromJson(root.get("rules"), listType);
                if (loaded != null) {
                    for (AutoUseItemRule rule : loaded) {
                        if (rule == null) {
                            continue;
                        }
                        if (rule.intervalMs <= 0) {
                            rule.intervalMs = 250;
                        }
                        if (rule.matchMode == null) {
                            rule.matchMode = AutoUseItemRule.MatchMode.CONTAINS;
                        }
                        if (rule.useMode == null) {
                            rule.useMode = AutoUseItemRule.UseMode.RIGHT_CLICK;
                        }
                        rule.switchItemDelayTicks = Math.max(0, rule.switchItemDelayTicks);
                        rule.switchDelayTicks = Math.max(0, rule.switchDelayTicks);
                        rule.restoreDelayTicks = Math.max(0, rule.restoreDelayTicks);
                        rule.category = normalizeCategory(rule.category);
                        rule.lastUseAtMs = 0L;
                        cfg().rules.add(rule);
                    }
                }
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("无法加载静默使用物品配置", e);
            cfg().rules.clear();
        }

        ensureCategoriesSynced();
        INSTANCE.resetSchedule();
    }

    public static synchronized void saveConfig() {
        try {
            ensureCategoriesSynced();
            Path configFile = getConfigFile();
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.add("categories", GSON.toJsonTree(new ArrayList<>(cfg().categories)));
                root.add("rules", GSON.toJsonTree(cfg().rules));
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
            mythosScriptMod.LOGGER.error("无法保存静默使用物品配置", e);
        }
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : cfg().categories) {
            normalized.add(normalizeCategory(category));
        }
        for (AutoUseItemRule rule : cfg().rules) {
            if (rule == null) {
                continue;
            }
            rule.category = normalizeCategory(rule.category);
            normalized.add(rule.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        cfg().categories.clear();
        cfg().categories.addAll(normalized);
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
        ensureCategoriesSynced();
        return new ArrayList<>(cfg().categories);
    }

    public static synchronized void replaceCategoryOrder(List<String> orderedCategories) {
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

        for (AutoUseItemRule rule : cfg().rules) {
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
        for (AutoUseItemRule rule : cfg().rules) {
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

    public void tick() {
        RuntimeState rt = rt();
        if (!rt.globalEnabled || mc.player == null || mc.world == null || mc.player.connection == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (isHotbarUseBusy(rt)) {
            rt.nextCheckAtMs = now + 50L;
            return;
        }
        if (now < rt.nextCheckAtMs) {
            return;
        }

        int minWaitMs = Integer.MAX_VALUE;
        for (AutoUseItemRule rule : cfg().rules) {
            if (rule == null || !rule.enabled || rule.name == null || rule.name.trim().isEmpty()) {
                continue;
            }

            int interval = Math.max(10, rule.intervalMs);
            long elapsed = now - rule.lastUseAtMs;
            if (elapsed < interval) {
                int remain = (int) (interval - elapsed);
                if (remain < minWaitMs) {
                    minWaitMs = remain;
                }
                continue;
            }

            int slot = findMatchedHotbarSlot(rule, rt);
            if (slot < 0) {
                if (interval < minWaitMs) {
                    minWaitMs = interval;
                }
                continue;
            }

            if (useHotbarItemSilently(mc.player, slot, rule.useMode, rule.changeLocalSlot,
                    rule.switchItemDelayTicks, rule.switchDelayTicks, rule.restoreDelayTicks, rt)) {
                rule.lastUseAtMs = now;
                if (interval < minWaitMs) {
                    minWaitMs = interval;
                }
            }
        }

        if (minWaitMs == Integer.MAX_VALUE) {
            rt.nextCheckAtMs = now + 100L;
        } else {
            rt.nextCheckAtMs = now + Math.max(10, minWaitMs);
        }
    }

    public void resetSchedule() {
        RuntimeState rt = rt();
        rt.nextCheckAtMs = 0L;
        long now = System.currentTimeMillis();
        for (AutoUseItemRule rule : cfg().rules) {
            if (rule != null) {
                rule.lastUseAtMs = Math.min(rule.lastUseAtMs, now);
            }
        }
        clearPendingHotbarUseAction(rt);
        clearPendingHotbarUseRestore(rt);
    }

    public int findMatchedHotbarSlotByName(String itemName, AutoUseItemRule.MatchMode matchMode) {
        if (mc.player == null) {
            return -1;
        }

        String target = normalizeName(itemName);
        if (target.isEmpty()) {
            return -1;
        }

        AutoUseItemRule.MatchMode safeMatchMode = matchMode == null
                ? AutoUseItemRule.MatchMode.CONTAINS
                : matchMode;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String currentName = normalizeName(stack.getDisplayName());
            boolean matched = safeMatchMode == AutoUseItemRule.MatchMode.EXACT
                    ? currentName.equals(target)
                    : currentName.contains(target);

            if (matched) {
                return slot;
            }
        }
        return -1;
    }

    public boolean useMatchingHotbarItem(EntityPlayerSP player, String itemName,
            AutoUseItemRule.MatchMode matchMode, AutoUseItemRule.UseMode useMode) {
        return useMatchingHotbarItem(player, itemName, matchMode, useMode, false, 0, 0, 0);
    }

    public boolean useMatchingHotbarItem(EntityPlayerSP player, String itemName,
            AutoUseItemRule.MatchMode matchMode, AutoUseItemRule.UseMode useMode,
            int switchDelayTicks, int restoreDelayTicks) {
        return useMatchingHotbarItem(player, itemName, matchMode, useMode, false, 0, switchDelayTicks,
                restoreDelayTicks);
    }

    public boolean useMatchingHotbarItem(EntityPlayerSP player, String itemName,
            AutoUseItemRule.MatchMode matchMode, AutoUseItemRule.UseMode useMode,
            boolean changeLocalSlot, int switchItemDelayTicks, int switchDelayTicks, int restoreDelayTicks) {
        if (player == null || player.connection == null) {
            return false;
        }

        int slot = findMatchedHotbarSlotByName(itemName, matchMode);
        if (slot < 0) {
            return false;
        }

        AutoUseItemRule.UseMode safeUseMode = useMode == null
                ? AutoUseItemRule.UseMode.RIGHT_CLICK
                : useMode;
        return useHotbarItemSilently(player, slot, safeUseMode,
                changeLocalSlot, switchItemDelayTicks, switchDelayTicks, restoreDelayTicks, rt());
    }

    public boolean useMatchingHotbarItem(EntityPlayerSP player, String itemName,
            AutoUseItemRule.MatchMode matchMode, AutoUseItemRule.UseMode useMode,
            int switchItemDelayTicks, int switchDelayTicks, int restoreDelayTicks) {
        return useMatchingHotbarItem(player, itemName, matchMode, useMode, false,
                switchItemDelayTicks, switchDelayTicks, restoreDelayTicks);
    }

    private int findMatchedHotbarSlot(AutoUseItemRule rule, RuntimeState rt) {
        String target = normalizeName(rule.name);
        if (target.isEmpty()) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String itemName = normalizeName(stack.getDisplayName());
            boolean matched = rule.matchMode == AutoUseItemRule.MatchMode.EXACT
                    ? itemName.equals(target)
                    : itemName.contains(target);

            if (matched) {
                return slot;
            }
        }
        return -1;
    }

    private boolean useHotbarItemSilently(EntityPlayerSP player, int targetSlot, AutoUseItemRule.UseMode useMode,
            boolean changeLocalSlot, int switchItemDelayTicks, int switchDelayTicks, int restoreDelayTicks,
            RuntimeState rt) {
        if (player == null || player.connection == null || targetSlot < 0 || targetSlot >= 9) {
            return false;
        }
        if (isHotbarUseBusy(rt)) {
            return false;
        }

        ItemStack targetStack = player.inventory.getStackInSlot(targetSlot);
        if (targetStack == null || targetStack.isEmpty()) {
            return false;
        }

        int originalSlot = player.inventory.currentItem;
        try {
            AutoUseItemRule.UseMode safeUseMode = useMode == null
                    ? AutoUseItemRule.UseMode.RIGHT_CLICK
                    : useMode;
            int safeSwitchItemDelayTicks = Math.max(0, switchItemDelayTicks);
            int safeSwitchDelayTicks = Math.max(0, switchDelayTicks);
            int safeRestoreDelayTicks = Math.max(0, restoreDelayTicks);

            if (originalSlot != targetSlot && safeSwitchItemDelayTicks > 0) {
                beginHotbarSlotSwitch(player, originalSlot, targetSlot, safeUseMode, changeLocalSlot,
                        safeSwitchDelayTicks, safeRestoreDelayTicks, safeSwitchItemDelayTicks, rt);
                return true;
            }

            if (originalSlot != targetSlot) {
                if (changeLocalSlot) {
                    player.inventory.currentItem = targetSlot;
                }
                player.connection.sendPacket(new CPacketHeldItemChange(targetSlot));
            }

            if (safeSwitchDelayTicks > 0) {
                beginHotbarUseAction(player, originalSlot, targetSlot, safeUseMode, changeLocalSlot,
                        safeSwitchDelayTicks, safeRestoreDelayTicks, rt);
                return true;
            }

            return executeHotbarUseAction(player, originalSlot, targetSlot, safeUseMode, changeLocalSlot,
                    safeRestoreDelayTicks, rt);
        } catch (Exception e) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            mythosScriptMod.LOGGER.debug("静默使用物品发包失败: {}", e.getMessage());
            clearPendingHotbarUseAction(rt);
            return false;
        }
    }

    private void beginHotbarSlotSwitch(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot,
            int switchDelayTicks, int restoreDelayTicks, int switchItemDelayTicks, RuntimeState rt) {
        rt.hotbarUseActionPending = true;
        final int token = ++rt.hotbarUseActionToken;

        if (ModUtils.DelayScheduler.instance == null) {
            executeDelayedHotbarSlotSwitch(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                    switchDelayTicks, restoreDelayTicks, token, rt);
            return;
        }

        ModUtils.DelayScheduler.instance.schedule(
                () -> executeDelayedHotbarSlotSwitch(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                        switchDelayTicks, restoreDelayTicks, token, rt),
                switchItemDelayTicks);
    }

    private void executeDelayedHotbarSlotSwitch(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot,
            int switchDelayTicks, int restoreDelayTicks, int token, RuntimeState rt) {
        if (!isHotbarUseActionTokenActive(token, rt)) {
            return;
        }
        clearPendingHotbarUseAction(rt);

        if (player == null || mc.player != player || player.connection == null) {
            clearPendingHotbarUseRestore(rt);
            return;
        }

        if (originalSlot != targetSlot) {
            if (changeLocalSlot) {
                player.inventory.currentItem = targetSlot;
            }
            player.connection.sendPacket(new CPacketHeldItemChange(targetSlot));
        }

        if (switchDelayTicks > 0) {
            beginHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                    switchDelayTicks, restoreDelayTicks, rt);
            return;
        }

        if (!executeHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot, restoreDelayTicks,
                rt)) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            if (originalSlot != targetSlot && player.connection != null) {
                player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
            }
        }
    }

    private void beginHotbarUseAction(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot, int switchDelayTicks, int restoreDelayTicks,
            RuntimeState rt) {
        rt.hotbarUseActionPending = true;
        final int token = ++rt.hotbarUseActionToken;

        if (ModUtils.DelayScheduler.instance == null) {
            executeDelayedHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                    restoreDelayTicks, token, rt);
            return;
        }

        ModUtils.DelayScheduler.instance.schedule(
                () -> executeDelayedHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                        restoreDelayTicks, token, rt),
                switchDelayTicks);
    }

    private void executeDelayedHotbarUseAction(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot, int restoreDelayTicks, int token,
            RuntimeState rt) {
        if (!isHotbarUseActionTokenActive(token, rt)) {
            return;
        }
        clearPendingHotbarUseAction(rt);

        if (player == null || mc.player != player || player.connection == null) {
            clearPendingHotbarUseRestore(rt);
            return;
        }

        if (originalSlot != targetSlot) {
            if (changeLocalSlot) {
                player.inventory.currentItem = targetSlot;
            }
            player.connection.sendPacket(new CPacketHeldItemChange(targetSlot));
        }

        if (!executeHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot, restoreDelayTicks,
                rt)) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            if (originalSlot != targetSlot && player.connection != null) {
                player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
            }
        }
    }

    private boolean executeHotbarUseAction(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot, int restoreDelayTicks, RuntimeState rt) {
        ItemStack targetStack = player.inventory.getStackInSlot(targetSlot);
        if (targetStack == null || targetStack.isEmpty()) {
            return false;
        }

        AutoUseItemRule.UseMode safeUseMode = useMode == null
                ? AutoUseItemRule.UseMode.RIGHT_CLICK
                : useMode;

        if (safeUseMode == AutoUseItemRule.UseMode.LEFT_CLICK) {
            if (changeLocalSlot) {
                player.swingArm(EnumHand.MAIN_HAND);
            }
            player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
            restoreHotbarSlotNow(player, originalSlot, targetSlot, changeLocalSlot, 0, rt);
            return true;
        }

        boolean triggered = false;
        if ((changeLocalSlot || originalSlot == targetSlot) && mc.playerController != null && player.world != null) {
            EnumActionResult result = mc.playerController.processRightClick(player, player.world, EnumHand.MAIN_HAND);
            triggered = result != EnumActionResult.FAIL;
        }

        if (!triggered) {
            player.connection.sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
            triggered = true;
        }

        if (!triggered) {
            return false;
        }

        beginHotbarUseRestore(player, originalSlot, targetSlot, changeLocalSlot,
                getHotbarUseRestoreTimeoutTicks(targetStack, safeUseMode, restoreDelayTicks), rt);
        return true;
    }

    private int getHotbarUseRestoreTimeoutTicks(ItemStack stack, AutoUseItemRule.UseMode useMode,
            int restoreDelayTicks) {
        int autoTimeoutTicks = getAutoHotbarUseRestoreTimeoutTicks(stack, useMode);
        return Math.max(autoTimeoutTicks, Math.max(0, restoreDelayTicks));
    }

    private int getAutoHotbarUseRestoreTimeoutTicks(ItemStack stack, AutoUseItemRule.UseMode useMode) {
        if (useMode != AutoUseItemRule.UseMode.RIGHT_CLICK || stack == null || stack.isEmpty()) {
            return 0;
        }

        EnumAction action = stack.getItemUseAction();
        if (action == EnumAction.EAT || action == EnumAction.DRINK) {
            return Math.min(40, Math.max(2, stack.getMaxItemUseDuration() + 2));
        }
        if (action == EnumAction.BOW || action == EnumAction.BLOCK) {
            return 8;
        }
        return 1;
    }

    private void beginHotbarUseRestore(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int restoreTimeoutTicks, RuntimeState rt) {
        rt.hotbarUseRestorePending = true;
        final int token = ++rt.hotbarUseRestoreToken;

        if (ModUtils.DelayScheduler.instance == null) {
            restoreHotbarSlotNow(player, originalSlot, targetSlot, changeLocalSlot, token, rt);
            return;
        }

        scheduleHotbarRestorePoll(player, originalSlot, targetSlot, changeLocalSlot,
                Math.max(0, restoreTimeoutTicks), token, rt);
    }

    private void scheduleHotbarRestorePoll(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int remainingTicks, int token, RuntimeState rt) {
        ModUtils.DelayScheduler.instance.schedule(
                () -> pollHotbarRestore(player, originalSlot, targetSlot, changeLocalSlot, remainingTicks, token, rt),
                1);
    }

    private void pollHotbarRestore(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int remainingTicks, int token, RuntimeState rt) {
        if (!isHotbarRestoreTokenActive(token, rt)) {
            return;
        }
        if (player == null || mc.player != player || player.connection == null) {
            clearPendingHotbarUseRestore(rt);
            return;
        }

        boolean stillUsing = player.isHandActive() && player.inventory.currentItem == targetSlot;
        if (stillUsing || remainingTicks > 0) {
            scheduleHotbarRestorePoll(player, originalSlot, targetSlot, changeLocalSlot,
                    Math.max(0, remainingTicks - 1), token, rt);
            return;
        }

        restoreHotbarSlotNow(player, originalSlot, targetSlot, changeLocalSlot, token, rt);
    }

    private void restoreHotbarSlotNow(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int token, RuntimeState rt) {
        if (token != 0 && !isHotbarRestoreTokenActive(token, rt)) {
            return;
        }
        if (player != null && originalSlot != targetSlot && player.connection != null) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
        }
        clearPendingHotbarUseRestore(rt);
    }

    private boolean isHotbarRestoreTokenActive(int token, RuntimeState rt) {
        return rt.hotbarUseRestorePending && token == rt.hotbarUseRestoreToken;
    }

    private boolean isHotbarUseActionTokenActive(int token, RuntimeState rt) {
        return rt.hotbarUseActionPending && token == rt.hotbarUseActionToken;
    }

    private boolean isHotbarUseBusy(RuntimeState rt) {
        return rt.hotbarUseActionPending || rt.hotbarUseRestorePending;
    }

    private void clearPendingHotbarUseAction(RuntimeState rt) {
        rt.hotbarUseActionPending = false;
    }

    private void clearPendingHotbarUseRestore(RuntimeState rt) {
        rt.hotbarUseRestorePending = false;
    }

    private String normalizeName(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)§[0-9A-FK-OR]", "")
                .replace('\u00A0', ' ')
                .trim()
                .toLowerCase();
    }
}
