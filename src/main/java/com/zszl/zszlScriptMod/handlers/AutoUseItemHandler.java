package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.system.AutoUseItemRule;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.zszlScriptMod;

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
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoUseItemHandler {
    public static final AutoUseItemHandler INSTANCE = new AutoUseItemHandler();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean globalEnabled = false;
    public static final List<AutoUseItemRule> rules = new CopyOnWriteArrayList<>();
    private static final List<String> categories = new CopyOnWriteArrayList<>();
    private static final String CATEGORY_DEFAULT = "默认";
    private long nextCheckAtMs = 0L;
    private boolean hotbarUseActionPending = false;
    private int hotbarUseActionToken = 0;
    private boolean hotbarUseRestorePending = false;
    private int hotbarUseRestoreToken = 0;

    private AutoUseItemHandler() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("auto_use_item_rules.json");
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

            rules.clear();
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
                        rules.add(rule);
                    }
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法加载静默使用物品配置", e);
            rules.clear();
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
                root.addProperty("globalEnabled", globalEnabled);
                root.add("categories", GSON.toJsonTree(new ArrayList<>(categories)));
                root.add("rules", GSON.toJsonTree(rules));
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存静默使用物品配置", e);
        }
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
        for (AutoUseItemRule rule : rules) {
            if (rule == null) {
                continue;
            }
            rule.category = normalizeCategory(rule.category);
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

        for (AutoUseItemRule rule : rules) {
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
        for (AutoUseItemRule rule : rules) {
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
        if (!globalEnabled || mc.player == null || mc.world == null || mc.player.connection == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (isHotbarUseBusy()) {
            nextCheckAtMs = now + 50L;
            return;
        }
        if (now < nextCheckAtMs) {
            return;
        }

        int minWaitMs = Integer.MAX_VALUE;
        for (AutoUseItemRule rule : rules) {
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

            int slot = findMatchedHotbarSlot(rule);
            if (slot < 0) {
                if (interval < minWaitMs) {
                    minWaitMs = interval;
                }
                continue;
            }

            if (useHotbarItemSilently(mc.player, slot, rule.useMode, rule.changeLocalSlot,
                    rule.switchItemDelayTicks, rule.switchDelayTicks, rule.restoreDelayTicks)) {
                rule.lastUseAtMs = now;
                if (interval < minWaitMs) {
                    minWaitMs = interval;
                }
            }
        }

        if (minWaitMs == Integer.MAX_VALUE) {
            nextCheckAtMs = now + 100L;
        } else {
            nextCheckAtMs = now + Math.max(10, minWaitMs);
        }
    }

    public void resetSchedule() {
        nextCheckAtMs = 0L;
        long now = System.currentTimeMillis();
        for (AutoUseItemRule rule : rules) {
            if (rule != null) {
                rule.lastUseAtMs = Math.min(rule.lastUseAtMs, now);
            }
        }
        clearPendingHotbarUseAction();
        clearPendingHotbarUseRestore();
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
                changeLocalSlot, switchItemDelayTicks, switchDelayTicks, restoreDelayTicks);
    }

    public boolean useMatchingHotbarItem(EntityPlayerSP player, String itemName,
            AutoUseItemRule.MatchMode matchMode, AutoUseItemRule.UseMode useMode,
            int switchItemDelayTicks, int switchDelayTicks, int restoreDelayTicks) {
        return useMatchingHotbarItem(player, itemName, matchMode, useMode, false,
                switchItemDelayTicks, switchDelayTicks, restoreDelayTicks);
    }

    private int findMatchedHotbarSlot(AutoUseItemRule rule) {
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
            boolean changeLocalSlot, int switchItemDelayTicks, int switchDelayTicks, int restoreDelayTicks) {
        if (player == null || player.connection == null || targetSlot < 0 || targetSlot >= 9) {
            return false;
        }
        if (isHotbarUseBusy()) {
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
                        safeSwitchDelayTicks, safeRestoreDelayTicks, safeSwitchItemDelayTicks);
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
                        safeSwitchDelayTicks, safeRestoreDelayTicks);
                return true;
            }

            return executeHotbarUseAction(player, originalSlot, targetSlot, safeUseMode, changeLocalSlot,
                    safeRestoreDelayTicks);
        } catch (Exception e) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            zszlScriptMod.LOGGER.debug("静默使用物品发包失败: {}", e.getMessage());
            clearPendingHotbarUseAction();
            return false;
        }
    }

    private void beginHotbarSlotSwitch(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot,
            int switchDelayTicks, int restoreDelayTicks, int switchItemDelayTicks) {
        this.hotbarUseActionPending = true;
        final int token = ++this.hotbarUseActionToken;

        if (ModUtils.DelayScheduler.instance == null) {
            executeDelayedHotbarSlotSwitch(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                    switchDelayTicks, restoreDelayTicks, token);
            return;
        }

        ModUtils.DelayScheduler.instance.schedule(
                () -> executeDelayedHotbarSlotSwitch(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                        switchDelayTicks, restoreDelayTicks, token),
                switchItemDelayTicks);
    }

    private void executeDelayedHotbarSlotSwitch(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot,
            int switchDelayTicks, int restoreDelayTicks, int token) {
        if (!isHotbarUseActionTokenActive(token)) {
            return;
        }
        clearPendingHotbarUseAction();

        if (player == null || mc.player != player || player.connection == null) {
            clearPendingHotbarUseRestore();
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
                    switchDelayTicks, restoreDelayTicks);
            return;
        }

        if (!executeHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot, restoreDelayTicks)) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            if (originalSlot != targetSlot && player.connection != null) {
                player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
            }
        }
    }

    private void beginHotbarUseAction(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot, int switchDelayTicks, int restoreDelayTicks) {
        this.hotbarUseActionPending = true;
        final int token = ++this.hotbarUseActionToken;

        if (ModUtils.DelayScheduler.instance == null) {
            executeDelayedHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                    restoreDelayTicks, token);
            return;
        }

        ModUtils.DelayScheduler.instance.schedule(
                () -> executeDelayedHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot,
                        restoreDelayTicks, token),
                switchDelayTicks);
    }

    private void executeDelayedHotbarUseAction(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot, int restoreDelayTicks, int token) {
        if (!isHotbarUseActionTokenActive(token)) {
            return;
        }
        clearPendingHotbarUseAction();

        if (player == null || mc.player != player || player.connection == null) {
            clearPendingHotbarUseRestore();
            return;
        }

        if (originalSlot != targetSlot) {
            if (changeLocalSlot) {
                player.inventory.currentItem = targetSlot;
            }
            player.connection.sendPacket(new CPacketHeldItemChange(targetSlot));
        }

        if (!executeHotbarUseAction(player, originalSlot, targetSlot, useMode, changeLocalSlot, restoreDelayTicks)) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            if (originalSlot != targetSlot && player.connection != null) {
                player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
            }
        }
    }

    private boolean executeHotbarUseAction(EntityPlayerSP player, int originalSlot, int targetSlot,
            AutoUseItemRule.UseMode useMode, boolean changeLocalSlot, int restoreDelayTicks) {
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
            restoreHotbarSlotNow(player, originalSlot, targetSlot, changeLocalSlot, 0);
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
                getHotbarUseRestoreTimeoutTicks(targetStack, safeUseMode, restoreDelayTicks));
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
            boolean changeLocalSlot, int restoreTimeoutTicks) {
        this.hotbarUseRestorePending = true;
        final int token = ++this.hotbarUseRestoreToken;

        if (ModUtils.DelayScheduler.instance == null) {
            restoreHotbarSlotNow(player, originalSlot, targetSlot, changeLocalSlot, token);
            return;
        }

        scheduleHotbarRestorePoll(player, originalSlot, targetSlot, changeLocalSlot,
                Math.max(0, restoreTimeoutTicks), token);
    }

    private void scheduleHotbarRestorePoll(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int remainingTicks, int token) {
        ModUtils.DelayScheduler.instance.schedule(
                () -> pollHotbarRestore(player, originalSlot, targetSlot, changeLocalSlot, remainingTicks, token), 1);
    }

    private void pollHotbarRestore(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int remainingTicks, int token) {
        if (!isHotbarRestoreTokenActive(token)) {
            return;
        }
        if (player == null || mc.player != player || player.connection == null) {
            clearPendingHotbarUseRestore();
            return;
        }

        boolean stillUsing = player.isHandActive() && player.inventory.currentItem == targetSlot;
        if (stillUsing || remainingTicks > 0) {
            scheduleHotbarRestorePoll(player, originalSlot, targetSlot, changeLocalSlot,
                    Math.max(0, remainingTicks - 1), token);
            return;
        }

        restoreHotbarSlotNow(player, originalSlot, targetSlot, changeLocalSlot, token);
    }

    private void restoreHotbarSlotNow(EntityPlayerSP player, int originalSlot, int targetSlot,
            boolean changeLocalSlot, int token) {
        if (token != 0 && !isHotbarRestoreTokenActive(token)) {
            return;
        }
        if (player != null && originalSlot != targetSlot && player.connection != null) {
            if (changeLocalSlot) {
                player.inventory.currentItem = originalSlot;
            }
            player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
        }
        clearPendingHotbarUseRestore();
    }

    private boolean isHotbarRestoreTokenActive(int token) {
        return this.hotbarUseRestorePending && token == this.hotbarUseRestoreToken;
    }

    private boolean isHotbarUseActionTokenActive(int token) {
        return this.hotbarUseActionPending && token == this.hotbarUseActionToken;
    }

    private boolean isHotbarUseBusy() {
        return this.hotbarUseActionPending || this.hotbarUseRestorePending;
    }

    private void clearPendingHotbarUseAction() {
        this.hotbarUseActionPending = false;
    }

    private void clearPendingHotbarUseRestore() {
        this.hotbarUseRestorePending = false;
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
