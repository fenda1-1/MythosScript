package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TextComponentString;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ItemFilterHandler {
    public static final File FILTER_CONFIG_FILE = new File(ModConfig.CONFIG_DIR, "filter_config.json");
    private static final int DEFAULT_MOVE_TO_CHEST_DELAY_TICKS = 2;
    private static final String DEFAULT_MOVE_CHEST_CLICK_TYPE = "PICKUP";
    private static final int DEFAULT_MOVE_CHEST_BUTTON = 0;
    public static final String NBT_TAG_MATCH_MODE_CONTAINS = "CONTAINS";
    public static final String NBT_TAG_MATCH_MODE_NOT_CONTAINS = "NOT_CONTAINS";
    public static final String MOVE_DIRECTION_INVENTORY_TO_CHEST = "INVENTORY_TO_CHEST";
    public static final String MOVE_DIRECTION_CHEST_TO_INVENTORY = "CHEST_TO_INVENTORY";

    private static final class ContainerSlotGroups {
        private final List<Integer> containerSlots;
        private final List<Integer> playerInventorySlots;

        private ContainerSlotGroups(List<Integer> containerSlots, List<Integer> playerInventorySlots) {
            this.containerSlots = containerSlots;
            this.playerInventorySlots = playerInventorySlots;
        }
    }

    public static final class MoveChestFilterRule {
        private final String itemName;
        private final List<String> requiredNbtTags;

        private MoveChestFilterRule(String itemName, List<String> requiredNbtTags) {
            this.itemName = itemName == null ? "" : itemName.trim();
            this.requiredNbtTags = requiredNbtTags == null ? Collections.<String>emptyList() : requiredNbtTags;
        }

        public String getItemName() {
            return itemName;
        }

        public List<String> getRequiredNbtTags() {
            return requiredNbtTags;
        }

        public boolean isEmpty() {
            return itemName.isEmpty() && (requiredNbtTags == null || requiredNbtTags.isEmpty());
        }
    }

    private static volatile int pendingWarehouseTransferClicks = 0;
    private static volatile boolean warehouseTransferInProgress = false;
    private static final String MOVE_CHEST_TASK_TAG = "move-chest-transfer";
    private static final int MAX_MOVE_CHEST_CLICKS_PER_TICK = 8;
    private static volatile MoveChestTask currentMoveChestTask;
    private static int moveChestTaskGeneration;

    private static final class MoveChestTask {
        final int windowId;
        final int generation;
        volatile boolean cancelled;
        volatile int pending;
        volatile String failReason = "";
        volatile int clicksThisTick;
        volatile int lastTick = Integer.MIN_VALUE;

        MoveChestTask(int windowId, int generation, int pending) {
            this.windowId = windowId;
            this.generation = generation;
            this.pending = pending;
        }
    }

    private static final class PlannedClick {
        final int slot;
        final int button;
        final ClickType type;

        PlannedClick(int slot, int button, ClickType type) {
            this.slot = slot;
            this.button = button;
            this.type = type == null ? ClickType.PICKUP : type;
        }
    }

    public static List<String> blacklistFilters = new ArrayList<>();
    public static List<String> whitelistFilters = new ArrayList<>();

    static {
        loadFilterConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("filter_config.json").toFile();
    }

    /**
     * 丢弃配置保存方法
     */
    public static void saveFilterConfig() {
        try {
            File configFile = getConfigFile();
            JsonObject json = new JsonObject();
            json.add("blacklist", new Gson().toJsonTree(blacklistFilters));
            json.add("whitelist", new Gson().toJsonTree(whitelistFilters));

            // 确保父目录存在
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            Files.write(configFile.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存过滤配置失败", e);
        }
    }

    /**
     * 丢弃配置加载方法
     */
    public static void loadFilterConfig() {
        try {
            File configFile = getConfigFile();
            if (configFile.exists()) {
                String jsonContent = new String(
                        Files.readAllBytes(configFile.toPath()),
                        StandardCharsets.UTF_8);
                JsonObject json = new JsonParser().parse(jsonContent).getAsJsonObject();

                blacklistFilters = new Gson().fromJson(
                        json.get("blacklist"), new TypeToken<List<String>>() {
                        }.getType());
                whitelistFilters = new Gson().fromJson(
                        json.get("whitelist"), new TypeToken<List<String>>() {
                        }.getType());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载过滤配置失败", e);
        }
    }

    /**
     * 根据名称过滤丢弃物品（黑名单+白名单机制）
     */
    public static void dropItemsByNameFilter() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null)
            return;

        // 捕获当前容器和其窗口ID，以便在延迟任务中进行验证
        Container initialContainer = player.openContainer;
        if (initialContainer == null) {
            System.err.println("警告: 没有打开的容器，无法执行丢弃操作。");
            return;
        }
        int initialWindowId = initialContainer.windowId;

        List<Integer> slotsToDrop = new ArrayList<>();

        // 增强安全性的容器操作
        try {
            int containerSize = initialContainer.inventorySlots.size();
            // 检查容器是否为空或无效
            if (containerSize <= 0) {
                System.err.println("警告: 容器大小无效 " + containerSize);
                return;
            }

            // 遍历当前容器的所有槽位
            for (int i = 0; i < containerSize; i++) {
                try {
                    Slot slot = initialContainer.getSlot(i);
                    if (slot == null || !slot.canTakeStack(player)) {
                        continue;
                    }

                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty() || stack.getCount() <= 0) {
                        continue;
                    }

                    String itemName = stack.getDisplayName().trim();
                    if (itemName.isEmpty()) {
                        continue;
                    }

                    boolean shouldDrop = blacklistFilters.stream().anyMatch(itemName::contains);
                    boolean shouldKeep = whitelistFilters.stream().anyMatch(itemName::contains);

                    if (shouldDrop && !shouldKeep) {
                        slotsToDrop.add(i);
                        if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)
                                && Minecraft.getMinecraft().player != null) {
                            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                                    String.format("§d[调试] §7物品 [%s] 被标记为待丢弃 (黑名单匹配)。", itemName)));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("警告: 处理槽位 " + i + " 时出错: " + e.getMessage());
                    // 跳过问题槽位，继续处理其他槽位
                }
            }
        } catch (Exception e) {
            System.err.println("严重错误: 处理物品栏时出错: " + e.getMessage());
            e.printStackTrace();
            return; // 如果在初始扫描时出现严重错误，则停止
        }

        // 如果没有物品需要丢弃，则直接返回
        if (slotsToDrop.isEmpty()) {
            return;
        }

        // 优化后的并行丢弃逻辑
        Minecraft.getMinecraft().addScheduledTask(() -> {
            // 在执行任务前再次检查容器是否仍然打开且未改变
            if (player.openContainer == null || player.openContainer.windowId != initialWindowId) {
                System.err.println("警告: 容器已更改或关闭，取消丢弃操作。");
                return; // 如果容器已更改或关闭，则中止操作
            }

            // 将槽位分成3组并行处理
            int batchSize = 3;
            for (int batch = 0; batch < slotsToDrop.size(); batch += batchSize) {
                final int currentBatch = batch;
                new ModUtils.DelayAction(batch * 3, () -> {
                    // 在每个延迟批次执行前再次检查容器状态
                    if (Minecraft.getMinecraft().player == null)
                        return;
                    if (Minecraft.getMinecraft().player.openContainer == null
                            || Minecraft.getMinecraft().player.openContainer.windowId != initialWindowId) {
                        System.err.println("警告: 容器在延迟操作中途更改或关闭，取消剩余丢弃操作。");
                        return; // 如果容器已更改或关闭，则中止剩余操作
                    }

                    // 并行处理当前批次
                    for (int i = 0; i < batchSize; i++) {
                        int globalIndex = currentBatch + i;
                        if (globalIndex >= slotsToDrop.size()) {
                            return; // 当前批次所有槽位已处理
                        }

                        int containerSlot = slotsToDrop.get(globalIndex);

                        // 关键检查：确保槽位索引对于当前打开的容器仍然有效
                        if (containerSlot < 0 || containerSlot >= player.openContainer.inventorySlots.size()) {
                            System.err.println(
                                    "警告: 尝试丢弃的槽位 " + containerSlot + " 对于当前容器 (ID: " + player.openContainer.windowId
                                            + ", Size: " + player.openContainer.inventorySlots.size() + ") 无效，跳过。");
                            continue; // 跳过无效槽位
                        }

                        // 执行丢弃流程
                        Minecraft.getMinecraft().playerController.windowClick(
                                player.openContainer.windowId,
                                containerSlot, 0, ClickType.PICKUP, player);
                        // 丢弃到地面（点击容器外）
                        Minecraft.getMinecraft().playerController.windowClick(
                                player.openContainer.windowId,
                                -999, 0, ClickType.PICKUP, player);
                    }
                }).accept(player);
            }
        });
    }

    /**
     * 根据名称过滤点击物品（黑名单+白名单机制）
     */
    public static void ClickItemsByNameFilter() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null)
            return;

        if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
            player.sendMessage(new TextComponentString("§d[调试] §7开始执行 ClickItemsByNameFilter..."));
        }

        Container initialContainer = player.openContainer;
        if (initialContainer == null) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §c错误: 容器未打开，操作取消。"));
            }
            return;
        }
        int initialWindowId = initialContainer.windowId;

        List<Integer> slotsToKeep = new ArrayList<>();

        try {
            int totalSlots = initialContainer.inventorySlots.size();
            if (totalSlots <= 36)
                return; // 如果没有容器部分，则直接返回

            // --- 核心修复: 只扫描玩家背包的槽位 ---
            // 玩家的背包槽位总是在容器的最后36个。
            int playerInventoryStartIndex = totalSlots - 36;

            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString(String.format("§d[调试] §7(旧方法安全修复) 扫描玩家背包槽位 %d -> %d...",
                        playerInventoryStartIndex, totalSlots - 1)));
            }

            for (int i = playerInventoryStartIndex; i < totalSlots; i++) {
                Slot slot = initialContainer.getSlot(i);
                if (slot == null || !slot.canTakeStack(player) || slot.getStack().isEmpty()) {
                    continue;
                }

                String itemName = slot.getStack().getDisplayName().trim();
                boolean shouldKeep = whitelistFilters.stream().anyMatch(itemName::contains);

                if (shouldKeep) {
                    slotsToKeep.add(i);
                    if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                        player.sendMessage(new TextComponentString(
                                String.format("§d[调试] §a匹配成功: §f物品 [%s] 在槽位 %d, 将被点击。", itemName, i)));
                    }
                }
            }
            // --- 修复结束 ---

        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("处理物品栏时出错", e);
            return;
        }

        if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
            if (slotsToKeep.isEmpty()) {
                player.sendMessage(new TextComponentString("§d[调试] §e总结: 未找到任何符合白名单的物品进行点击。"));
            } else {
                player.sendMessage(
                        new TextComponentString(String.format("§d[调试] §a总结: 共找到 %d 个物品将被点击。", slotsToKeep.size())));
            }
        }

        if (slotsToKeep.isEmpty()) {
            return;
        }

        // 点击逻辑保持不变，但现在只点击背包里的物品
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (player.openContainer == null || player.openContainer.windowId != initialWindowId) {
                return;
            }
            int batchSize = 3;
            for (int batch = 0; batch < slotsToKeep.size(); batch += batchSize) {
                final int currentBatch = batch;
                new ModUtils.DelayAction(batch * 3, () -> {
                    if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().player.openContainer == null
                            || Minecraft.getMinecraft().player.openContainer.windowId != initialWindowId) {
                        return;
                    }
                    for (int i = 0; i < batchSize; i++) {
                        int globalIndex = currentBatch + i;
                        if (globalIndex >= slotsToKeep.size()) {
                            return;
                        }
                        int containerSlot = slotsToKeep.get(globalIndex);
                        if (containerSlot >= 0 && containerSlot < player.openContainer.inventorySlots.size()) {
                            Minecraft.getMinecraft().playerController.windowClick(
                                    player.openContainer.windowId,
                                    containerSlot, 0, ClickType.PICKUP, player);
                        }
                    }
                }).accept(player);
            }
        });
    }

    public static boolean isWarehouseTransferInProgress() {
        MoveChestTask task = currentMoveChestTask;
        if (task != null && !task.cancelled && task.pending > 0) {
            return true;
        }
        return warehouseTransferInProgress;
    }

    public static void cancelMoveChestTransfer() {
        MoveChestTask task = currentMoveChestTask;
        if (task != null) {
            task.cancelled = true;
            task.pending = 0;
            if (task.failReason == null || task.failReason.isEmpty()) {
                task.failReason = "cancelled";
            }
        }
        warehouseTransferInProgress = false;
        pendingWarehouseTransferClicks = 0;
        currentMoveChestTask = null;
        if (ModUtils.DelayScheduler.instance != null) {
            ModUtils.DelayScheduler.instance.cancelTasks(new java.util.function.Predicate<ModUtils.DelayScheduler.DelayTask>() {
                @Override
                public boolean test(ModUtils.DelayScheduler.DelayTask delayTask) {
                    String tag = delayTask == null ? null : delayTask.getTag();
                    return tag != null && tag.startsWith(MOVE_CHEST_TASK_TAG);
                }
            });
        }
    }

    public static int[] livePhysicalSlotCounts() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.openContainer == null) {
            return new int[] { 0, 0 };
        }
        ContainerSlotGroups groups = resolveContainerSlotGroups(player.openContainer, player);
        return new int[] { groups.playerInventorySlots.size(), groups.containerSlots.size() };
    }

    public static Integer readSlotLimit(JsonObject params, String region, int relativeIndex, String property) {
        if (params == null || region == null || property == null || !params.has("slotLimits")
                || !params.get("slotLimits").isJsonObject()) {
            return null;
        }
        JsonObject slotLimits = params.getAsJsonObject("slotLimits");
        if (!slotLimits.has(region) || !slotLimits.get(region).isJsonObject()) {
            return null;
        }
        JsonObject regionObject = slotLimits.getAsJsonObject(region);
        String key = String.valueOf(relativeIndex);
        if (!regionObject.has(key) || !regionObject.get(key).isJsonObject()) {
            return null;
        }
        JsonObject entry = regionObject.getAsJsonObject(key);
        if (!entry.has(property) || entry.get(property).isJsonNull() || !entry.get(property).isJsonPrimitive()) {
            return null;
        }
        try {
            return Integer.valueOf(entry.get(property).getAsInt());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void markWarehouseTransferTaskFinished() {
        MoveChestTask task = currentMoveChestTask;
        if (task != null) {
            int remain = task.pending - 1;
            task.pending = Math.max(0, remain);
            pendingWarehouseTransferClicks = task.pending;
            if (task.pending <= 0 || task.cancelled) {
                warehouseTransferInProgress = false;
                if (currentMoveChestTask == task) {
                    currentMoveChestTask = null;
                }
            }
            return;
        }
        int remain = pendingWarehouseTransferClicks - 1;
        pendingWarehouseTransferClicks = Math.max(0, remain);
        if (pendingWarehouseTransferClicks <= 0) {
            warehouseTransferInProgress = false;
        }
    }

    public static void moveInventoryItemsToChestSlots(JsonObject params) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }

        if (warehouseTransferInProgress) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §e物品槽位转移仍在进行中，本次请求已忽略。"));
            }
            return;
        }

        warehouseTransferInProgress = false;
        pendingWarehouseTransferClicks = 0;

        Container container = player.openContainer;
        if (Minecraft.getMinecraft().currentScreen == null || container == null) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §c错误: 未检测到已打开的容器界面，槽位转移取消。"));
            }
            return;
        }
        if (player.inventory.getItemStack() != null && !player.inventory.getItemStack().isEmpty()) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §e鼠标光标上已有物品，槽位转移取消。"));
            }
            return;
        }

        ContainerSlotGroups slotGroups = resolveContainerSlotGroups(container, player);
        int containerSlotCount = slotGroups.containerSlots.size();
        int playerInventoryVisibleSlots = slotGroups.playerInventorySlots.size();
        if (containerSlotCount <= 0 || playerInventoryVisibleSlots <= 0) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §e当前容器未识别出可写入容器槽位或可用背包槽位，操作取消。"));
            }
            return;
        }

        List<Integer> selectedContainerSlots = readSlotList(params, "chestSlots", "chestSlotsText", containerSlotCount);
        List<Integer> selectedInventorySlots = readSlotList(params, "inventorySlots", "inventorySlotsText",
                playerInventoryVisibleSlots);
        List<String> itemFilterExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
        List<MoveChestFilterRule> filterRules = readMoveChestFilterRules(params);
        String requiredNbtTagMatchMode = readRequiredNbtTagMatchMode(params);
        String moveDirection = readMoveChestDirection(params);
        String moveClickTypeName = readMoveChestClickType(params);
        ClickType moveClickType = ModUtils.resolveClickType(moveClickTypeName);
        int moveClickButton = readMoveChestClickButton(params);
        boolean cursorTransferMode = moveClickType == ClickType.PICKUP && moveClickButton == 0;

        if (itemFilterExpressions.isEmpty() && filterRules.isEmpty()) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §e至少需要添加一条有效的物品过滤表达式或兼容旧版规则，槽位转移取消。"));
            }
            return;
        }

        List<Integer> sourceSlotIndices = MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(moveDirection)
                ? slotGroups.containerSlots
                : slotGroups.playerInventorySlots;
        List<Integer> selectedSourceSlots = MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(moveDirection)
                ? selectedContainerSlots
                : selectedInventorySlots;
        List<Integer> targetSlotIndices = MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(moveDirection)
                ? slotGroups.playerInventorySlots
                : slotGroups.containerSlots;
        List<Integer> selectedTargetSlots = MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(moveDirection)
                ? selectedInventorySlots
                : selectedContainerSlots;

        if (selectedSourceSlots.isEmpty() || (cursorTransferMode && selectedTargetSlots.isEmpty())) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §e未选择有效的来源槽位"
                        + (cursorTransferMode ? "或目标槽位" : "") + "，操作取消。"));
            }
            return;
        }

        int windowId = container.windowId;
        boolean hasSlotLimits = params != null && params.has("slotLimits") && params.get("slotLimits").isJsonObject()
                && !params.getAsJsonObject("slotLimits").entrySet().isEmpty();
        if (hasSlotLimits && !cursorTransferMode) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString("§d[调试] §e每槽位数量上限只支持普通左键搬运，本次请求已取消。"));
            }
            return;
        }
        String sourceRegion = MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(moveDirection) ? "chest" : "inventory";
        String targetRegion = MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(moveDirection) ? "inventory" : "chest";
        List<PlannedClick> clickPlan = cursorTransferMode
                ? buildBudgetedPickupClickPlan(container,
                        sourceSlotIndices,
                        selectedSourceSlots,
                        targetSlotIndices,
                        selectedTargetSlots,
                        itemFilterExpressions,
                        filterRules,
                        requiredNbtTagMatchMode,
                        params,
                        sourceRegion,
                        targetRegion)
                : buildDirectSourceClickPlan(container,
                        sourceSlotIndices,
                        selectedSourceSlots,
                        itemFilterExpressions,
                        filterRules,
                        requiredNbtTagMatchMode,
                        moveClickType,
                        moveClickButton);
        if (clickPlan.isEmpty()) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.ITEM_FILTER)) {
                player.sendMessage(new TextComponentString(cursorTransferMode
                        ? "§d[调试] §e未找到符合任意转移规则且可放入目标槽位的物品。"
                        : "§d[调试] §e未找到符合任意转移规则的来源物品槽位。"));
            }
            return;
        }

        int moveDelayTicks = DEFAULT_MOVE_TO_CHEST_DELAY_TICKS;
        boolean normalizeDelayTo20Tps = true;
        if (params != null && params.has("delayTicks")) {
            try {
                moveDelayTicks = Math.max(0, params.get("delayTicks").getAsInt());
            } catch (Exception ignored) {
                moveDelayTicks = DEFAULT_MOVE_TO_CHEST_DELAY_TICKS;
            }
        }
        if (params != null && params.has("normalizeDelayTo20Tps")) {
            try {
                normalizeDelayTo20Tps = params.get("normalizeDelayTo20Tps").getAsBoolean();
            } catch (Exception ignored) {
                normalizeDelayTo20Tps = true;
            }
        }

        moveChestTaskGeneration++;
        MoveChestTask task = new MoveChestTask(windowId, moveChestTaskGeneration, clickPlan.size());
        currentMoveChestTask = task;
        warehouseTransferInProgress = true;
        pendingWarehouseTransferClicks = clickPlan.size();
        scheduleMoveChestClick(clickPlan, 0, task, moveDelayTicks, normalizeDelayTo20Tps, !cursorTransferMode, 0);
    }

    private static void scheduleMoveChestClick(final List<PlannedClick> plan, final int index, final MoveChestTask task,
            final int delayTicks, final boolean normalizeDelayTo20Tps, final boolean directClickMode,
            int initialDelay) {
        if (plan == null || task == null || index < 0 || index >= plan.size() || ModUtils.DelayScheduler.instance == null) {
            return;
        }
        final String tag = MOVE_CHEST_TASK_TAG + "-" + task.generation;
        ModUtils.DelayScheduler.instance.schedule(new Runnable() {
            @Override
            public void run() {
                if (task.cancelled || currentMoveChestTask != task) {
                    return;
                }
                EntityPlayerSP player = Minecraft.getMinecraft().player;
                if (player == null || Minecraft.getMinecraft().currentScreen == null
                        || player.openContainer == null || player.openContainer.windowId != task.windowId) {
                    task.failReason = "window-changed";
                    cancelMoveChestTransfer();
                    return;
                }
                int tick = player.ticksExisted;
                if (task.lastTick != tick) {
                    task.lastTick = tick;
                    task.clicksThisTick = 0;
                }
                if (delayTicks <= 0 && task.clicksThisTick >= MAX_MOVE_CHEST_CLICKS_PER_TICK) {
                    scheduleMoveChestClick(plan, index, task, delayTicks, normalizeDelayTo20Tps, directClickMode, 1);
                    return;
                }
                if (directClickMode && player.inventory.getItemStack() != null
                        && !player.inventory.getItemStack().isEmpty()) {
                    task.failReason = "unexpected-cursor";
                    cancelMoveChestTransfer();
                    return;
                }
                PlannedClick click = plan.get(index);
                Slot slot = click.slot >= 0 && click.slot < player.openContainer.inventorySlots.size()
                        ? player.openContainer.getSlot(click.slot) : null;
                ItemStack beforeSlot = slot == null || !slot.getHasStack() ? ItemStack.EMPTY : slot.getStack().copy();
                ItemStack beforeCursor = player.inventory.getItemStack() == null ? ItemStack.EMPTY
                        : player.inventory.getItemStack().copy();
                Minecraft.getMinecraft().playerController.windowClick(task.windowId, click.slot, click.button,
                        click.type, player);
                task.clicksThisTick++;
                ItemStack afterSlot = slot == null || !slot.getHasStack() ? ItemStack.EMPTY : slot.getStack();
                ItemStack afterCursor = player.inventory.getItemStack() == null ? ItemStack.EMPTY
                        : player.inventory.getItemStack();
                if (click.type == ClickType.PICKUP && itemStacksVisuallyEqual(beforeSlot, afterSlot)
                        && itemStacksVisuallyEqual(beforeCursor, afterCursor)) {
                    task.failReason = "click-no-change";
                    cancelMoveChestTransfer();
                    return;
                }
                markWarehouseTransferTaskFinished();
                if (!task.cancelled && index + 1 < plan.size()) {
                    scheduleMoveChestClick(plan, index + 1, task, delayTicks, normalizeDelayTo20Tps, directClickMode,
                            delayTicks);
                }
            }
        }, Math.max(0, initialDelay), normalizeDelayTo20Tps, tag);
    }

    private static boolean itemStacksVisuallyEqual(ItemStack left, ItemStack right) {
        ItemStack a = left == null ? ItemStack.EMPTY : left;
        ItemStack b = right == null ? ItemStack.EMPTY : right;
        if (a.isEmpty() && b.isEmpty()) {
            return true;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b) && a.getCount() == b.getCount();
    }

    private static ContainerSlotGroups resolveContainerSlotGroups(Container container, EntityPlayerSP player) {
        if (container == null || player == null) {
            return new ContainerSlotGroups(Collections.<Integer>emptyList(), Collections.<Integer>emptyList());
        }

        List<Integer> containerSlots = new ArrayList<>();
        List<Integer> playerInventorySlots = new ArrayList<>();
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot == null) {
                continue;
            }
            if (slot.inventory == player.inventory && slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 36) {
                playerInventorySlots.add(i);
            } else if (slot.inventory != player.inventory) {
                containerSlots.add(i);
            }
        }
        return new ContainerSlotGroups(containerSlots, playerInventorySlots);
    }

    private static List<PlannedClick> buildBudgetedPickupClickPlan(Container container,
            List<Integer> sourceSlotIndices,
            List<Integer> selectedSourceSlots,
            List<Integer> targetSlotIndices,
            List<Integer> selectedTargetSlots,
            List<String> itemFilterExpressions,
            List<MoveChestFilterRule> filterRules,
            String requiredNbtTagMatchMode,
            JsonObject params,
            String sourceRegion,
            String targetRegion) {
        if (container == null) {
            return Collections.emptyList();
        }

        List<PlannedClick> clickPlan = new ArrayList<PlannedClick>();
        ItemStack[] simulatedStacks = new ItemStack[container.inventorySlots.size()];
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            simulatedStacks[i] = slot == null || !slot.getHasStack() ? ItemStack.EMPTY : slot.getStack().copy();
        }
        Map<Integer, Integer> putRemaining = new HashMap<Integer, Integer>();
        for (int selectedTargetIndex : selectedTargetSlots) {
            Integer maxPut = readSlotLimit(params, targetRegion, selectedTargetIndex, "maxPut");
            putRemaining.put(Integer.valueOf(selectedTargetIndex),
                    maxPut == null ? Integer.valueOf(Integer.MAX_VALUE) : maxPut);
        }
        int totalTransferBudget = Integer.MAX_VALUE;
        if (params != null && params.has("maxTransferCount")) {
            try {
                int configuredBudget = params.get("maxTransferCount").getAsInt();
                totalTransferBudget = configuredBudget <= 0 ? Integer.MAX_VALUE : configuredBudget;
            } catch (Exception ignored) {
                totalTransferBudget = Integer.MAX_VALUE;
            }
        }

        for (int sourceIndex : selectedSourceSlots) {
            if (totalTransferBudget <= 0) {
                break;
            }
            if (sourceIndex < 0 || sourceIndex >= sourceSlotIndices.size()) {
                continue;
            }
            Integer maxTake = readSlotLimit(params, sourceRegion, sourceIndex, "maxTake");
            if (maxTake != null && maxTake.intValue() == 0) {
                continue;
            }

            int sourceContainerSlot = sourceSlotIndices.get(sourceIndex);
            if (sourceContainerSlot < 0 || sourceContainerSlot >= simulatedStacks.length) {
                continue;
            }

            ItemStack sourceStack = simulatedStacks[sourceContainerSlot];
            if (sourceStack == null || sourceStack.isEmpty()) {
                continue;
            }
            if (!matchesAnyMoveChestRule(sourceStack, sourceIndex, itemFilterExpressions, filterRules,
                    requiredNbtTagMatchMode)) {
                continue;
            }

            int budget = maxTake == null ? sourceStack.getCount()
                    : Math.min(sourceStack.getCount(), Math.max(0, maxTake.intValue()));
            budget = Math.min(budget, totalTransferBudget);
            if (budget <= 0) {
                continue;
            }
            totalTransferBudget -= budget;

            List<int[]> placements = new ArrayList<int[]>();
            int remaining = budget;

            for (int selectedTargetIndex : selectedTargetSlots) {
                if (remaining <= 0) {
                    break;
                }
                if (selectedTargetIndex < 0 || selectedTargetIndex >= targetSlotIndices.size()) {
                    continue;
                }
                Integer putLeft = putRemaining.get(Integer.valueOf(selectedTargetIndex));
                if (putLeft != null && putLeft.intValue() <= 0) {
                    continue;
                }
                int targetSlot = targetSlotIndices.get(selectedTargetIndex);
                if (targetSlot < 0 || targetSlot >= simulatedStacks.length) {
                    continue;
                }
                Slot targetSlotObj = container.getSlot(targetSlot);
                ItemStack targetStack = simulatedStacks[targetSlot];
                if (targetStack == null || targetStack.isEmpty()) {
                    int nativeSpace = targetCapacity(targetSlotObj, sourceStack, ItemStack.EMPTY);
                    int allowed = putLeft == null ? nativeSpace : Math.min(nativeSpace, putLeft.intValue());
                    if (allowed > 0) {
                        int moved = Math.min(remaining, allowed);
                        ItemStack placed = sourceStack.copy();
                        placed.setCount(moved);
                        simulatedStacks[targetSlot] = placed;
                        remaining -= moved;
                        if (putLeft != null && putLeft.intValue() != Integer.MAX_VALUE) {
                            putRemaining.put(Integer.valueOf(selectedTargetIndex),
                                    Integer.valueOf(putLeft.intValue() - moved));
                        }
                        placements.add(new int[] { targetSlot, moved });
                    }
                    if (remaining <= 0) {
                        break;
                    }
                    continue;
                }
                if (!canMergeItemStacks(sourceStack, targetStack)) {
                    continue;
                }
                int nativeSpace = targetCapacity(targetSlotObj, sourceStack, targetStack);
                int allowed = putLeft == null ? nativeSpace : Math.min(nativeSpace, putLeft.intValue());
                if (allowed <= 0) {
                    continue;
                }
                int moved = Math.min(remaining, allowed);
                targetStack.setCount(targetStack.getCount() + moved);
                remaining -= moved;
                if (putLeft != null && putLeft.intValue() != Integer.MAX_VALUE) {
                    putRemaining.put(Integer.valueOf(selectedTargetIndex), Integer.valueOf(putLeft.intValue() - moved));
                }
                placements.add(new int[] { targetSlot, moved });
            }

            if (placements.isEmpty()) {
                continue;
            }

            int sourceCount = sourceStack.getCount();
            clickPlan.add(new PlannedClick(sourceContainerSlot, 0, ClickType.PICKUP));
            int cursor = sourceCount;
            for (int[] placement : placements) {
                int targetSlot = placement[0];
                int amount = placement[1];
                if (amount <= 0) {
                    continue;
                }
                if (amount == cursor) {
                    clickPlan.add(new PlannedClick(targetSlot, 0, ClickType.PICKUP));
                    cursor = 0;
                } else {
                    for (int i = 0; i < amount && cursor > 0; i++) {
                        clickPlan.add(new PlannedClick(targetSlot, 1, ClickType.PICKUP));
                        cursor--;
                    }
                }
            }
            int leftover = sourceCount - budget + remaining;
            if (cursor > 0) {
                clickPlan.add(new PlannedClick(sourceContainerSlot, 0, ClickType.PICKUP));
                ItemStack restored = sourceStack.copy();
                restored.setCount(cursor);
                simulatedStacks[sourceContainerSlot] = restored;
            } else if (leftover > 0) {
                ItemStack restored = sourceStack.copy();
                restored.setCount(leftover);
                simulatedStacks[sourceContainerSlot] = restored;
            } else {
                simulatedStacks[sourceContainerSlot] = ItemStack.EMPTY;
            }
        }

        return clickPlan;
    }

    private static int targetCapacity(Slot slot, ItemStack sourceStack, ItemStack targetStack) {
        if (sourceStack == null || sourceStack.isEmpty()) {
            return 0;
        }
        if (slot != null && !slot.isItemValid(sourceStack)) {
            return 0;
        }
        int max = sourceStack.getMaxStackSize();
        if (slot != null) {
            max = Math.min(max, slot.getItemStackLimit(sourceStack));
        }
        int current = targetStack == null || targetStack.isEmpty() ? 0 : targetStack.getCount();
        return Math.max(0, max - current);
    }

    /**
     * Builds a plan for click types that operate directly on each matching source
     * slot (for example QUICK_MOVE, THROW, or SWAP). These modes do not use the
     * cursor-based source/target sequence above, so target slot selections are
     * intentionally ignored.
     */
    private static List<PlannedClick> buildDirectSourceClickPlan(Container container,
            List<Integer> sourceSlotIndices,
            List<Integer> selectedSourceSlots,
            List<String> itemFilterExpressions,
            List<MoveChestFilterRule> filterRules,
            String requiredNbtTagMatchMode,
            ClickType clickType,
            int button) {
        if (container == null || sourceSlotIndices == null || selectedSourceSlots == null) {
            return Collections.emptyList();
        }

        List<PlannedClick> clickPlan = new ArrayList<PlannedClick>();
        for (int sourceIndex : selectedSourceSlots) {
            if (sourceIndex < 0 || sourceIndex >= sourceSlotIndices.size()) {
                continue;
            }

            int sourceContainerSlot = sourceSlotIndices.get(sourceIndex);
            if (sourceContainerSlot < 0 || sourceContainerSlot >= container.inventorySlots.size()) {
                continue;
            }

            Slot slot = container.getSlot(sourceContainerSlot);
            if (slot == null || !slot.getHasStack()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (matchesAnyMoveChestRule(stack, sourceIndex, itemFilterExpressions, filterRules,
                    requiredNbtTagMatchMode)) {
                clickPlan.add(new PlannedClick(sourceContainerSlot, button, clickType));
            }
        }
        return clickPlan;
    }

    private static boolean canMergeItemStacks(ItemStack sourceStack, ItemStack targetStack) {
        if (sourceStack == null || targetStack == null || sourceStack.isEmpty() || targetStack.isEmpty()) {
            return false;
        }
        return ItemStack.areItemsEqual(sourceStack, targetStack)
                && ItemStack.areItemStackTagsEqual(sourceStack, targetStack)
                && targetStack.isStackable();
    }

    public static boolean matchesRequiredNbtTags(ItemStack stack, List<String> requiredNbtTags,
            String requiredNbtTagMatchMode) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (requiredNbtTags == null || requiredNbtTags.isEmpty()) {
            return true;
        }

        boolean excludeMatches = NBT_TAG_MATCH_MODE_NOT_CONTAINS.equalsIgnoreCase(requiredNbtTagMatchMode);

        String searchableText = buildMoveChestMatchText(stack);
        if (searchableText.isEmpty()) {
            return excludeMatches;
        }

        for (String filter : requiredNbtTags) {
            String normalized = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty() && searchableText.contains(normalized)) {
                return !excludeMatches;
            }
        }
        return excludeMatches;
    }

    private static boolean matchesMoveChestItemName(ItemStack stack, String requiredItemName) {
        String expected = normalizeMoveChestItemName(requiredItemName);
        if (expected.isEmpty()) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return normalizeMoveChestItemName(stack.getDisplayName()).contains(expected);
    }

    private static boolean matchesAnyMoveChestRule(ItemStack stack, int sourceSlotIndex,
            List<String> expressions,
            List<MoveChestFilterRule> rules,
            String requiredNbtTagMatchMode) {
        if (expressions != null && !expressions.isEmpty()) {
            for (String expression : expressions) {
                if (expression == null || expression.trim().isEmpty()) {
                    continue;
                }
                try {
                    if (InventoryItemFilterExpressionEngine.matches(stack, sourceSlotIndex, expression)) {
                        return true;
                    }
                } catch (Exception e) {
                    zszlScriptMod.LOGGER.warn("[move_chest] 物品过滤表达式解析失败: {}", expression, e);
                }
            }
            return false;
        }
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (MoveChestFilterRule rule : rules) {
            if (rule == null || rule.isEmpty()) {
                continue;
            }
            if (!matchesMoveChestItemName(stack, rule.getItemName())) {
                continue;
            }
            if (!matchesRequiredNbtTags(stack, rule.getRequiredNbtTags(), requiredNbtTagMatchMode)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static String buildItemSearchableText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(128);
        appendMoveChestMatchText(builder, stack.getDisplayName());

        Item item = stack.getItem();
        if (item != null) {
            appendMoveChestMatchText(builder, item.getItemStackDisplayName(stack));
            ResourceLocation registryName = item.getRegistryName();
            if (registryName != null) {
                appendMoveChestMatchText(builder, registryName.toString());
            }
        }

        try {
            List<String> tooltip = stack.getTooltip(Minecraft.getMinecraft().player, ITooltipFlag.TooltipFlags.NORMAL);
            for (String line : tooltip) {
                appendMoveChestMatchText(builder, line);
            }
        } catch (Exception ignored) {
        }

        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound != null) {
            appendMoveChestMatchText(builder, tagCompound.toString());
        }

        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static String buildMoveChestMatchText(ItemStack stack) {
        return buildItemSearchableText(stack);
    }

    private static void appendMoveChestMatchText(StringBuilder builder, String text) {
        if (builder == null || text == null) {
            return;
        }

        String normalized = TextFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            normalized = text;
        }
        normalized = normalized.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(normalized);
    }

    private static String normalizeMoveChestItemName(String text) {
        if (text == null) {
            return "";
        }
        String normalized = TextFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            normalized = text;
        }
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static List<Integer> readSlotList(JsonObject params, String arrayKey, String textKey, int maxSlots) {
        Set<Integer> unique = new LinkedHashSet<>();
        if (params == null) {
            return new ArrayList<>();
        }

        if (params.has(arrayKey)) {
            JsonElement arrayElement = params.get(arrayKey);
            if (arrayElement != null && arrayElement.isJsonArray()) {
                JsonArray array = arrayElement.getAsJsonArray();
                for (JsonElement element : array) {
                    if (element == null || !element.isJsonPrimitive()) {
                        continue;
                    }
                    try {
                        int value = element.getAsInt();
                        if (value >= 0 && value < maxSlots) {
                            unique.add(value);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } else if (arrayElement != null && arrayElement.isJsonPrimitive()) {
                unique.addAll(parseIntegerText(arrayElement.getAsString(), maxSlots));
            }
        }

        if (unique.isEmpty() && params.has(textKey) && params.get(textKey).isJsonPrimitive()) {
            unique.addAll(parseIntegerText(params.get(textKey).getAsString(), maxSlots));
        }

        List<Integer> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    private static List<Integer> parseIntegerText(String text, int maxSlots) {
        Set<Integer> unique = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] tokens = text.split("[,\\r\\n\\s]+");
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(token.trim());
                if (value >= 0 && value < maxSlots) {
                    unique.add(value);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(unique);
    }

    public static List<String> readTagFilters(JsonObject params, String arrayKey, String textKey) {
        List<String> values = new ArrayList<>();
        if (params == null) {
            return values;
        }

        if (params.has(arrayKey)) {
            JsonElement arrayElement = params.get(arrayKey);
            if (arrayElement != null && arrayElement.isJsonArray()) {
                for (JsonElement element : arrayElement.getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive()) {
                        addTagFilter(values, element.getAsString());
                    }
                }
            } else if (arrayElement != null && arrayElement.isJsonPrimitive()) {
                for (String token : arrayElement.getAsString().split("\\r?\\n|,")) {
                    addTagFilter(values, token);
                }
            }
        }

        if (values.isEmpty() && params.has(textKey) && params.get(textKey).isJsonPrimitive()) {
            for (String token : params.get(textKey).getAsString().split("\\r?\\n|,")) {
                addTagFilter(values, token);
            }
        }

        return values;
    }

    public static String readRequiredNbtTagMatchMode(JsonObject params) {
        if (params == null || !params.has("requiredNbtTagsMode") || !params.get("requiredNbtTagsMode").isJsonPrimitive()) {
            return NBT_TAG_MATCH_MODE_CONTAINS;
        }

        try {
            String mode = params.get("requiredNbtTagsMode").getAsString();
            return NBT_TAG_MATCH_MODE_NOT_CONTAINS.equalsIgnoreCase(mode)
                    ? NBT_TAG_MATCH_MODE_NOT_CONTAINS
                    : NBT_TAG_MATCH_MODE_CONTAINS;
        } catch (Exception ignored) {
            return NBT_TAG_MATCH_MODE_CONTAINS;
        }
    }

    public static String readMoveChestDirection(JsonObject params) {
        if (params == null || !params.has("moveDirection") || !params.get("moveDirection").isJsonPrimitive()) {
            return MOVE_DIRECTION_INVENTORY_TO_CHEST;
        }

        try {
            String direction = params.get("moveDirection").getAsString();
            return MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(direction)
                    ? MOVE_DIRECTION_CHEST_TO_INVENTORY
                    : MOVE_DIRECTION_INVENTORY_TO_CHEST;
        } catch (Exception ignored) {
            return MOVE_DIRECTION_INVENTORY_TO_CHEST;
        }
    }

    /**
     * Reads the optional click type for batch movement. Missing or invalid values
     * retain the historical PICKUP behavior.
     */
    public static String readMoveChestClickType(JsonObject params) {
        if (params == null || !params.has("clickType") || !params.get("clickType").isJsonPrimitive()) {
            return DEFAULT_MOVE_CHEST_CLICK_TYPE;
        }
        try {
            return ModUtils.normalizeClickTypeName(params.get("clickType").getAsString());
        } catch (Exception ignored) {
            return DEFAULT_MOVE_CHEST_CLICK_TYPE;
        }
    }

    /** Reads the optional windowClick button, retaining button 0 for old configs. */
    public static int readMoveChestClickButton(JsonObject params) {
        if (params == null || !params.has("button") || !params.get("button").isJsonPrimitive()) {
            return DEFAULT_MOVE_CHEST_BUTTON;
        }
        try {
            return Math.max(0, params.get("button").getAsInt());
        } catch (Exception ignored) {
            return DEFAULT_MOVE_CHEST_BUTTON;
        }
    }

    public static String readMoveChestItemName(JsonObject params) {
        if (params == null || !params.has("itemName") || !params.get("itemName").isJsonPrimitive()) {
            return "";
        }
        try {
            String value = params.get("itemName").getAsString();
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static List<MoveChestFilterRule> readMoveChestFilterRules(JsonObject params) {
        List<MoveChestFilterRule> rules = new ArrayList<>();
        if (params == null) {
            return rules;
        }

        if (params.has("moveChestRules") && params.get("moveChestRules").isJsonArray()) {
            JsonArray array = params.getAsJsonArray("moveChestRules");
            for (JsonElement element : array) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject ruleObject = element.getAsJsonObject();
                String itemName = "";
                if (ruleObject.has("itemName") && ruleObject.get("itemName").isJsonPrimitive()) {
                    itemName = ruleObject.get("itemName").getAsString();
                }
                List<String> requiredNbtTags = readTagFilters(ruleObject, "requiredNbtTags", "requiredNbtTagsText");
                MoveChestFilterRule rule = new MoveChestFilterRule(itemName, requiredNbtTags);
                if (!rule.isEmpty()) {
                    rules.add(rule);
                }
            }
            if (!rules.isEmpty()) {
                return rules;
            }
        }

        String itemName = readMoveChestItemName(params);
        List<String> requiredNbtTags = readTagFilters(params, "requiredNbtTags", "requiredNbtTagsText");
        MoveChestFilterRule legacyRule = new MoveChestFilterRule(itemName, requiredNbtTags);
        if (!legacyRule.isEmpty()) {
            rules.add(legacyRule);
        }
        return rules;
    }

    private static void addTagFilter(List<String> values, String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            return;
        }
        for (String existing : values) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        values.add(normalized);
    }
}

