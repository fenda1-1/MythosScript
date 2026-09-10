package com.zszl.zszlScriptMod.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemSpreadHandler {
    public static final String SOURCE_SCOPE_INVENTORY = "INVENTORY";
    public static final String SOURCE_SCOPE_MAIN = "MAIN";
    public static final String SOURCE_SCOPE_HOTBAR = "HOTBAR";
    public static final String SOURCE_SCOPE_CONTAINER = "CONTAINER";

    public static final String TARGET_SCOPE_INVENTORY = "INVENTORY";
    public static final String TARGET_SCOPE_MAIN = "MAIN";
    public static final String TARGET_SCOPE_HOTBAR = "HOTBAR";

    public static final String MODE_ONE_PER_SLOT = "ONE_PER_SLOT";
    public static final String MODE_EVEN_SPLIT = "EVEN_SPLIT";
    public static final String MODE_FIXED_PER_SLOT = "FIXED_PER_SLOT";

    public static final String REMAINDER_RETURN_SOURCE = "RETURN_SOURCE";
    public static final String REMAINDER_FIRST_EMPTY = "FIRST_EMPTY";
    public static final String REMAINDER_KEEP_CURSOR = "KEEP_CURSOR";

    private static volatile boolean spreadInProgress = false;
    private static volatile int pendingSpreadClicks = 0;
    private static volatile boolean stackInProgress = false;
    private static volatile int pendingStackClicks = 0;

    private ItemSpreadHandler() {
    }

    public static boolean isSpreadInProgress() {
        return spreadInProgress && pendingSpreadClicks > 0;
    }

    public static boolean isStackInProgress() {
        return stackInProgress && pendingStackClicks > 0;
    }

    public static void spreadInventoryItem(JsonObject params) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || mc.playerController == null) {
            return;
        }
        if (isSpreadInProgress()) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 上一次平摊动作仍在执行，本次请求已忽略。");
            return;
        }
        if (player.inventory.getItemStack() != null && !player.inventory.getItemStack().isEmpty()) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 鼠标光标上已有物品，平摊动作取消。");
            return;
        }

        Container container = player.openContainer != null ? player.openContainer : player.inventoryContainer;
        if (container == null) {
            return;
        }

        List<String> expressions = readItemFilterExpressions(params);
        if (expressions.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 缺少物品过滤表达式，平摊动作取消。");
            return;
        }

        SlotMaps slotMaps = resolveSlotMaps(container, player);
        String sourceScope = readString(params, "sourceScope", SOURCE_SCOPE_INVENTORY);
        String targetScope = readString(params, "targetScope", TARGET_SCOPE_INVENTORY);
        String spreadMode = readString(params, "spreadMode", MODE_ONE_PER_SLOT);
        String remainderMode = readString(params, "remainderMode", REMAINDER_RETURN_SOURCE);
        boolean onlyEmptySlots = readBoolean(params, "onlyEmptySlots", true);
        boolean preserveSourceSlot = readBoolean(params, "preserveSourceSlot", true);
        boolean continueOnInsufficient = readBoolean(params, "continueOnInsufficient", false);
        int perSlotCount = Math.max(1, readInt(params, "perSlotCount", 1));
        int delayTicks = Math.max(0, readInt(params, "delayTicks", 1));
        boolean normalizeDelayTo20Tps = readBoolean(params, "normalizeDelayTo20Tps", true);

        List<SourceSlot> sources = collectSources(container, player, slotMaps, expressions, sourceScope, params);
        if (sources.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 未找到符合表达式的来源物品。");
            return;
        }

        ItemStack seedStack = sources.get(0).stack.copy();
        sources = filterSourcesBySameStackType(sources, seedStack);
        int availableCount = sumSourceCount(sources);
        if (availableCount <= 0) {
            return;
        }

        List<TargetSlot> targets = collectTargets(container, slotMaps, seedStack, targetScope, params,
                onlyEmptySlots, preserveSourceSlot ? collectSourceInventorySlots(sources) : Collections.<Integer>emptySet());
        if (targets.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 未找到可写入的目标背包槽位。");
            return;
        }

        List<TargetDemand> demands = buildTargetDemands(targets, seedStack, spreadMode, perSlotCount, availableCount,
                continueOnInsufficient);
        int demandCount = sumDemandCount(demands);
        if (demandCount <= 0) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 目标槽位没有可放入空间。");
            return;
        }
        if (availableCount < demandCount && !continueOnInsufficient) {
            zszlScriptMod.LOGGER.warn("[spread_inventory_item] 物品数量不足，需要 {} 个，实际 {} 个。", demandCount,
                    availableCount);
            return;
        }
        if (availableCount < demandCount) {
            trimDemandsToAvailable(demands, availableCount);
            demandCount = sumDemandCount(demands);
        }

        Integer fallbackRemainderSlot = resolveRemainderSlot(slotMaps, demands, sources, remainderMode);
        List<ClickStep> clickPlan = buildClickPlan(sources, demands, remainderMode, fallbackRemainderSlot);
        if (clickPlan.isEmpty()) {
            return;
        }

        scheduleClickPlan(container.windowId, clickPlan, delayTicks, normalizeDelayTo20Tps);
    }

    public static void stackInventoryItems(JsonObject params) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || mc.playerController == null) {
            return;
        }
        if (isStackInProgress()) {
            zszlScriptMod.LOGGER.warn("[stack_inventory_item] 上一次叠加动作仍在执行，本次请求已忽略。");
            return;
        }
        if (player.inventory.getItemStack() != null && !player.inventory.getItemStack().isEmpty()) {
            zszlScriptMod.LOGGER.warn("[stack_inventory_item] 鼠标光标上已有物品，叠加动作取消。");
            return;
        }

        Container container = player.openContainer != null ? player.openContainer : player.inventoryContainer;
        if (container == null) {
            return;
        }

        List<String> expressions = readItemFilterExpressions(params);
        if (expressions.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[stack_inventory_item] 缺少物品过滤表达式，叠加动作取消。");
            return;
        }

        SlotMaps slotMaps = resolveSlotMaps(container, player);
        String sourceScope = readString(params, "sourceScope", SOURCE_SCOPE_INVENTORY);
        String targetScope = readString(params, "targetScope", TARGET_SCOPE_INVENTORY);
        int delayTicks = Math.max(0, readInt(params, "delayTicks", 1));
        boolean normalizeDelayTo20Tps = readBoolean(params, "normalizeDelayTo20Tps", true);

        List<SourceSlot> sources = collectSources(container, player, slotMaps, expressions, sourceScope, params);
        if (sources.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[stack_inventory_item] 未找到符合表达式的来源物品。");
            return;
        }

        List<ClickStep> clickPlan = buildStackClickPlan(container, slotMaps, player, sources, targetScope, params);
        if (clickPlan.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[stack_inventory_item] 未找到可叠加的目标物品堆。");
            return;
        }

        scheduleStackClickPlan(container.windowId, clickPlan, delayTicks, normalizeDelayTo20Tps);
    }

    private static List<String> readItemFilterExpressions(JsonObject params) {
        List<String> expressions = InventoryItemFilterExpressionEngine.readExpressions(params);
        if (!expressions.isEmpty()) {
            return expressions;
        }

        String itemName = readString(params, "itemName", "");
        if (itemName.trim().isEmpty()) {
            return expressions;
        }
        String matchMode = readString(params, "matchMode", "CONTAINS");
        String expression = InventoryItemFilterExpressionEngine.buildLegacyCompatibleExpression(
                itemName,
                matchMode,
                ItemFilterHandler.readTagFilters(params, "requiredNbtTags", "requiredNbtTagsText"),
                ItemFilterHandler.readRequiredNbtTagMatchMode(params));
        if (!expression.trim().isEmpty()) {
            expressions.add(expression);
        }
        return expressions;
    }

    private static SlotMaps resolveSlotMaps(Container container, EntityPlayerSP player) {
        Map<Integer, Integer> inventorySlotToContainerSlot = new LinkedHashMap<Integer, Integer>();
        List<Integer> containerSlots = new ArrayList<Integer>();
        if (container == null || player == null) {
            return new SlotMaps(inventorySlotToContainerSlot, containerSlots);
        }
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot == null) {
                continue;
            }
            if (slot.inventory == player.inventory && slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 36) {
                inventorySlotToContainerSlot.put(slot.getSlotIndex(), i);
            } else if (slot.inventory != player.inventory) {
                containerSlots.add(i);
            }
        }
        return new SlotMaps(inventorySlotToContainerSlot, containerSlots);
    }

    private static List<SourceSlot> collectSources(Container container, EntityPlayerSP player, SlotMaps slotMaps,
            List<String> expressions, String sourceScope, JsonObject params) {
        List<SourceSlot> result = new ArrayList<SourceSlot>();
        Set<Integer> selectedSourceSlots = readIndexSet(params, "sourceSlots", "sourceSlotsText");
        String normalizedScope = normalize(sourceScope);

        if (SOURCE_SCOPE_CONTAINER.equals(normalizedScope)) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.currentScreen == null || container == player.inventoryContainer) {
                return result;
            }
            for (Integer containerSlotIndex : slotMaps.containerSlots) {
                if (selectedSourceSlots != null && !selectedSourceSlots.isEmpty()
                        && !selectedSourceSlots.contains(containerSlotIndex)) {
                    continue;
                }
                Slot slot = getSlot(container, containerSlotIndex);
                if (slot == null || !slot.getHasStack()) {
                    continue;
                }
                ItemStack stack = slot.getStack();
                if (matchesAny(stack, slot.getSlotIndex(), expressions)) {
                    result.add(new SourceSlot(containerSlotIndex, -1, stack.copy()));
                }
            }
            return result;
        }

        for (int actualSlot = 0; actualSlot < player.inventory.mainInventory.size() && actualSlot < 36; actualSlot++) {
            if (!isInventorySlotInScope(actualSlot, normalizedScope)) {
                continue;
            }
            if (selectedSourceSlots != null && !selectedSourceSlots.isEmpty()
                    && !selectedSourceSlots.contains(actualSlot)) {
                continue;
            }
            Integer containerSlotIndex = slotMaps.inventorySlotToContainerSlot.get(actualSlot);
            if (containerSlotIndex == null) {
                continue;
            }
            ItemStack stack = player.inventory.mainInventory.get(actualSlot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (matchesAny(stack, actualSlot, expressions)) {
                result.add(new SourceSlot(containerSlotIndex, actualSlot, stack.copy()));
            }
        }
        return result;
    }

    private static List<SourceSlot> filterSourcesBySameStackType(List<SourceSlot> sources, ItemStack seedStack) {
        List<SourceSlot> result = new ArrayList<SourceSlot>();
        for (SourceSlot source : sources) {
            if (source != null && isSameStackType(source.stack, seedStack)) {
                result.add(source);
            }
        }
        return result;
    }

    private static List<TargetSlot> collectTargets(Container container, SlotMaps slotMaps, ItemStack seedStack,
            String targetScope, JsonObject params, boolean onlyEmptySlots, Set<Integer> excludedInventorySlots) {
        List<TargetSlot> result = new ArrayList<TargetSlot>();
        Set<Integer> selectedTargetSlots = readIndexSet(params, "targetSlots", "targetSlotsText");
        String normalizedScope = normalize(targetScope);

        for (Map.Entry<Integer, Integer> entry : slotMaps.inventorySlotToContainerSlot.entrySet()) {
            int actualSlot = entry.getKey().intValue();
            if (!isInventorySlotInScope(actualSlot, normalizedScope)) {
                continue;
            }
            if (excludedInventorySlots != null && excludedInventorySlots.contains(actualSlot)) {
                continue;
            }
            if (selectedTargetSlots != null && !selectedTargetSlots.isEmpty()
                    && !selectedTargetSlots.contains(actualSlot)) {
                continue;
            }
            Slot slot = getSlot(container, entry.getValue());
            if (slot == null || !slot.isItemValid(seedStack)) {
                continue;
            }
            ItemStack current = slot.getStack();
            if (current != null && !current.isEmpty()) {
                if (onlyEmptySlots || !isSameStackType(current, seedStack)) {
                    continue;
                }
            }
            int maxStackSize = getTargetStackLimit(slot, seedStack);
            int currentCount = current == null || current.isEmpty() ? 0 : current.getCount();
            int capacity = Math.max(0, maxStackSize - currentCount);
            if (capacity <= 0) {
                continue;
            }
            result.add(new TargetSlot(entry.getValue().intValue(), actualSlot, currentCount, capacity));
        }
        return result;
    }

    private static List<TargetDemand> buildTargetDemands(List<TargetSlot> targets, ItemStack seedStack,
            String spreadMode, int perSlotCount, int availableCount, boolean continueOnInsufficient) {
        List<TargetDemand> demands = new ArrayList<TargetDemand>();
        String mode = normalize(spreadMode);

        if (MODE_EVEN_SPLIT.equals(mode)) {
            if (!continueOnInsufficient && availableCount < targets.size()) {
                for (TargetSlot target : targets) {
                    demands.add(new TargetDemand(target, 1));
                }
                return demands;
            }
            int remaining = availableCount;
            int remainingTargets = targets.size();
            for (TargetSlot target : targets) {
                if (remaining <= 0 || remainingTargets <= 0) {
                    break;
                }
                int share = (int) Math.ceil(remaining / (double) remainingTargets);
                int addCount = Math.min(target.capacity, Math.max(0, share));
                if (addCount > 0) {
                    demands.add(new TargetDemand(target, addCount));
                    remaining -= addCount;
                }
                remainingTargets--;
            }
            return demands;
        }

        for (TargetSlot target : targets) {
            int addCount;
            if (MODE_FIXED_PER_SLOT.equals(mode)) {
                addCount = Math.max(0, Math.min(perSlotCount, getTargetMaxStackSize(seedStack)) - target.currentCount);
            } else {
                addCount = 1;
            }
            addCount = Math.min(addCount, target.capacity);
            if (addCount > 0) {
                demands.add(new TargetDemand(target, addCount));
            }
        }
        return demands;
    }

    private static List<ClickStep> buildClickPlan(List<SourceSlot> sources, List<TargetDemand> demands,
            String remainderMode, Integer fallbackRemainderSlot) {
        List<ClickStep> clickPlan = new ArrayList<ClickStep>();
        int demandIndex = 0;
        String normalizedRemainderMode = normalize(remainderMode);

        for (SourceSlot source : sources) {
            while (demandIndex < demands.size() && demands.get(demandIndex).remaining <= 0) {
                demandIndex++;
            }
            if (demandIndex >= demands.size()) {
                break;
            }

            int cursorCount = source.stack.getCount();
            clickPlan.add(new ClickStep(source.containerSlot, 0));

            while (cursorCount > 0 && demandIndex < demands.size()) {
                TargetDemand demand = demands.get(demandIndex);
                if (demand.remaining <= 0) {
                    demandIndex++;
                    continue;
                }
                clickPlan.add(new ClickStep(demand.target.containerSlot, 1));
                demand.remaining--;
                cursorCount--;
                if (demand.remaining <= 0) {
                    demandIndex++;
                }
            }

            if (cursorCount > 0 && !REMAINDER_KEEP_CURSOR.equals(normalizedRemainderMode)) {
                int returnSlot = source.containerSlot;
                if (REMAINDER_FIRST_EMPTY.equals(normalizedRemainderMode) && fallbackRemainderSlot != null) {
                    returnSlot = fallbackRemainderSlot.intValue();
                }
                clickPlan.add(new ClickStep(returnSlot, 0));
            }
        }
        return clickPlan;
    }

    private static List<ClickStep> buildStackClickPlan(Container container, SlotMaps slotMaps, EntityPlayerSP player,
            List<SourceSlot> sources, String targetScope, JsonObject params) {
        List<ClickStep> clickPlan = new ArrayList<ClickStep>();
        List<MergeGroup> groups = buildMergeGroups(sources);
        for (MergeGroup group : groups) {
            if (group == null || group.seedStack == null || group.seedStack.isEmpty()) {
                continue;
            }
            List<TargetSlot> targets = collectTargets(container, slotMaps, group.seedStack, targetScope, params,
                    false, Collections.<Integer>emptySet());
            if (targets.isEmpty()) {
                continue;
            }
            List<TargetSlot> partialTargets = new ArrayList<TargetSlot>();
            for (TargetSlot target : targets) {
                if (target != null && target.currentCount > 0 && target.capacity > target.currentCount) {
                    partialTargets.add(target);
                }
            }
            if (partialTargets.isEmpty()) {
                continue;
            }
            partialTargets.sort((left, right) -> {
                int countCompare = Integer.compare(right.currentCount, left.currentCount);
                if (countCompare != 0) {
                    return countCompare;
                }
                return Integer.compare(left.containerSlot, right.containerSlot);
            });

            List<MergeSourceState> sourceStates = new ArrayList<MergeSourceState>();
            for (SourceSlot source : group.sources) {
                if (source != null && source.stack != null && !source.stack.isEmpty()) {
                    sourceStates.add(new MergeSourceState(source.containerSlot, source.actualInventorySlot,
                            source.stack.getCount()));
                }
            }
            sourceStates.sort((left, right) -> {
                int countCompare = Integer.compare(left.remainingCount, right.remainingCount);
                if (countCompare != 0) {
                    return countCompare;
                }
                return Integer.compare(left.containerSlot, right.containerSlot);
            });

            for (TargetSlot target : partialTargets) {
                int room = Math.max(0, target.capacity - target.currentCount);
                if (room <= 0) {
                    continue;
                }
                for (MergeSourceState sourceState : sourceStates) {
                    if (sourceState == null || sourceState.remainingCount <= 0
                            || sourceState.containerSlot == target.containerSlot
                            || room <= 0) {
                        continue;
                    }
                    int transfer = Math.min(room, sourceState.remainingCount);
                    if (transfer <= 0) {
                        continue;
                    }
                    clickPlan.add(new ClickStep(sourceState.containerSlot, 0));
                    clickPlan.add(new ClickStep(target.containerSlot, 0));
                    sourceState.remainingCount -= transfer;
                    room -= transfer;
                    if (sourceState.remainingCount > 0) {
                        clickPlan.add(new ClickStep(sourceState.containerSlot, 0));
                    }
                }
            }
        }
        return clickPlan;
    }

    private static List<MergeGroup> buildMergeGroups(List<SourceSlot> sources) {
        List<MergeGroup> groups = new ArrayList<MergeGroup>();
        if (sources == null) {
            return groups;
        }
        for (SourceSlot source : sources) {
            if (source == null || source.stack == null || source.stack.isEmpty() || source.stack.getCount() <= 0) {
                continue;
            }
            MergeGroup matched = null;
            for (MergeGroup group : groups) {
                if (group != null && isSameStackType(group.seedStack, source.stack)) {
                    matched = group;
                    break;
                }
            }
            if (matched == null) {
                matched = new MergeGroup(source.stack.copy());
                groups.add(matched);
            }
            matched.sources.add(source);
        }
        return groups;
    }

    private static Integer resolveRemainderSlot(SlotMaps slotMaps, List<TargetDemand> demands, List<SourceSlot> sources,
            String remainderMode) {
        if (!REMAINDER_FIRST_EMPTY.equals(normalize(remainderMode))) {
            return null;
        }
        Set<Integer> usedTargetSlots = new LinkedHashSet<Integer>();
        for (TargetDemand demand : demands) {
            if (demand != null && demand.target != null) {
                usedTargetSlots.add(demand.target.actualInventorySlot);
            }
        }
        Set<Integer> sourceSlots = collectSourceInventorySlots(sources);
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return null;
        }
        for (Map.Entry<Integer, Integer> entry : slotMaps.inventorySlotToContainerSlot.entrySet()) {
            int actualSlot = entry.getKey().intValue();
            if (usedTargetSlots.contains(actualSlot) || sourceSlots.contains(actualSlot)) {
                continue;
            }
            ItemStack stack = player.inventory.mainInventory.get(actualSlot);
            if (stack == null || stack.isEmpty()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void scheduleClickPlan(final int windowId, List<ClickStep> clickPlan, int delayTicks,
            boolean normalizeDelayTo20Tps) {
        spreadInProgress = true;
        pendingSpreadClicks = clickPlan.size();
        if (ModUtils.DelayScheduler.instance == null) {
            for (ClickStep step : clickPlan) {
                performClick(windowId, step);
                markSpreadClickFinished();
            }
            return;
        }
        for (int i = 0; i < clickPlan.size(); i++) {
            final ClickStep step = clickPlan.get(i);
            ModUtils.DelayScheduler.instance.schedule(new Runnable() {
                @Override
                public void run() {
                    performClick(windowId, step);
                    markSpreadClickFinished();
                }
            }, i * delayTicks, normalizeDelayTo20Tps, "spread_inventory_item");
        }
    }

    private static void scheduleStackClickPlan(final int windowId, List<ClickStep> clickPlan, int delayTicks,
            boolean normalizeDelayTo20Tps) {
        stackInProgress = true;
        pendingStackClicks = clickPlan.size();
        if (ModUtils.DelayScheduler.instance == null) {
            for (ClickStep step : clickPlan) {
                performClick(windowId, step);
                markStackClickFinished();
            }
            return;
        }
        for (int i = 0; i < clickPlan.size(); i++) {
            final ClickStep step = clickPlan.get(i);
            ModUtils.DelayScheduler.instance.schedule(new Runnable() {
                @Override
                public void run() {
                    performClick(windowId, step);
                    markStackClickFinished();
                }
            }, i * delayTicks, normalizeDelayTo20Tps, "stack_inventory_item");
        }
    }

    private static void performClick(int windowId, ClickStep step) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || mc.playerController == null || player.openContainer == null
                || player.openContainer.windowId != windowId) {
            return;
        }
        if (step.slot < 0 || step.slot >= player.openContainer.inventorySlots.size()) {
            return;
        }
        mc.playerController.windowClick(windowId, step.slot, step.button, ClickType.PICKUP, player);
    }

    private static void markSpreadClickFinished() {
        pendingSpreadClicks = Math.max(0, pendingSpreadClicks - 1);
        if (pendingSpreadClicks <= 0) {
            spreadInProgress = false;
        }
    }

    private static void markStackClickFinished() {
        pendingStackClicks = Math.max(0, pendingStackClicks - 1);
        if (pendingStackClicks <= 0) {
            stackInProgress = false;
        }
    }

    private static boolean matchesAny(ItemStack stack, int slotIndex, List<String> expressions) {
        if (stack == null || stack.isEmpty() || expressions == null || expressions.isEmpty()) {
            return false;
        }
        for (String expression : expressions) {
            if (expression == null || expression.trim().isEmpty()) {
                continue;
            }
            try {
                if (InventoryItemFilterExpressionEngine.matches(stack, slotIndex, expression)) {
                    return true;
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("[spread_inventory_item] 物品过滤表达式解析失败: {}", expression, e);
            }
        }
        return false;
    }

    private static Set<Integer> collectSourceInventorySlots(List<SourceSlot> sources) {
        Set<Integer> result = new LinkedHashSet<Integer>();
        if (sources == null) {
            return result;
        }
        for (SourceSlot source : sources) {
            if (source != null && source.actualInventorySlot >= 0) {
                result.add(source.actualInventorySlot);
            }
        }
        return result;
    }

    private static int sumSourceCount(List<SourceSlot> sources) {
        int total = 0;
        for (SourceSlot source : sources) {
            if (source != null && source.stack != null && !source.stack.isEmpty()) {
                total += Math.max(0, source.stack.getCount());
            }
        }
        return total;
    }

    private static int sumDemandCount(List<TargetDemand> demands) {
        int total = 0;
        for (TargetDemand demand : demands) {
            if (demand != null) {
                total += Math.max(0, demand.remaining);
            }
        }
        return total;
    }

    private static void trimDemandsToAvailable(List<TargetDemand> demands, int availableCount) {
        int remaining = Math.max(0, availableCount);
        for (TargetDemand demand : demands) {
            if (demand == null) {
                continue;
            }
            if (remaining <= 0) {
                demand.remaining = 0;
                continue;
            }
            if (demand.remaining > remaining) {
                demand.remaining = remaining;
            }
            remaining -= demand.remaining;
        }
    }

    private static boolean isInventorySlotInScope(int actualSlot, String scope) {
        String normalized = normalize(scope);
        if (SOURCE_SCOPE_HOTBAR.equals(normalized) || TARGET_SCOPE_HOTBAR.equals(normalized)) {
            return actualSlot >= 0 && actualSlot < 9;
        }
        if (SOURCE_SCOPE_MAIN.equals(normalized) || TARGET_SCOPE_MAIN.equals(normalized)) {
            return actualSlot >= 9 && actualSlot < 36;
        }
        return actualSlot >= 0 && actualSlot < 36;
    }

    private static boolean isSameStackType(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return false;
        }
        return ItemStack.areItemsEqual(first, second) && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static int getTargetStackLimit(Slot slot, ItemStack stack) {
        if (slot == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.min(getTargetMaxStackSize(stack), slot.getItemStackLimit(stack)));
    }

    private static int getTargetMaxStackSize(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 64 : Math.max(1, stack.getMaxStackSize());
    }

    private static Slot getSlot(Container container, Integer slotIndex) {
        if (container == null || slotIndex == null || slotIndex.intValue() < 0
                || slotIndex.intValue() >= container.inventorySlots.size()) {
            return null;
        }
        return container.inventorySlots.get(slotIndex.intValue());
    }

    private static Set<Integer> readIndexSet(JsonObject params, String arrayKey, String textKey) {
        LinkedHashSet<Integer> result = new LinkedHashSet<Integer>();
        if (params == null) {
            return result;
        }
        if (params.has(arrayKey) && params.get(arrayKey).isJsonArray()) {
            JsonArray array = params.getAsJsonArray(arrayKey);
            for (JsonElement element : array) {
                try {
                    result.add(Math.max(0, element.getAsInt()));
                } catch (Exception ignored) {
                }
            }
        }
        if (params.has(textKey) && params.get(textKey).isJsonPrimitive()) {
            parseIndexText(params.get(textKey).getAsString(), result);
        } else if (params.has(arrayKey) && params.get(arrayKey).isJsonPrimitive()) {
            parseIndexText(params.get(arrayKey).getAsString(), result);
        }
        return result;
    }

    private static void parseIndexText(String text, Set<Integer> output) {
        if (text == null || output == null) {
            return;
        }
        for (String token : text.split("[,，;；\\r\\n\\s]+")) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty()) {
                continue;
            }
            int dash = part.indexOf('-');
            if (dash > 0 && dash < part.length() - 1) {
                try {
                    int start = Integer.parseInt(part.substring(0, dash).trim());
                    int end = Integer.parseInt(part.substring(dash + 1).trim());
                    int min = Math.min(start, end);
                    int max = Math.max(start, end);
                    for (int i = min; i <= max; i++) {
                        output.add(Math.max(0, i));
                    }
                    continue;
                } catch (Exception ignored) {
                }
            }
            try {
                output.add(Math.max(0, Integer.parseInt(part)));
            } catch (Exception ignored) {
            }
        }
    }

    private static String readString(JsonObject params, String key, String defaultValue) {
        if (params == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            String value = params.get(key).getAsString();
            return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static boolean readBoolean(JsonObject params, String key, boolean defaultValue) {
        if (params == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int readInt(JsonObject params, String key, int defaultValue) {
        if (params == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class SlotMaps {
        private final Map<Integer, Integer> inventorySlotToContainerSlot;
        private final List<Integer> containerSlots;

        private SlotMaps(Map<Integer, Integer> inventorySlotToContainerSlot, List<Integer> containerSlots) {
            this.inventorySlotToContainerSlot = inventorySlotToContainerSlot;
            this.containerSlots = containerSlots;
        }
    }

    private static final class SourceSlot {
        private final int containerSlot;
        private final int actualInventorySlot;
        private final ItemStack stack;

        private SourceSlot(int containerSlot, int actualInventorySlot, ItemStack stack) {
            this.containerSlot = containerSlot;
            this.actualInventorySlot = actualInventorySlot;
            this.stack = stack;
        }
    }

    private static final class TargetSlot {
        private final int containerSlot;
        private final int actualInventorySlot;
        private final int currentCount;
        private final int capacity;

        private TargetSlot(int containerSlot, int actualInventorySlot, int currentCount, int capacity) {
            this.containerSlot = containerSlot;
            this.actualInventorySlot = actualInventorySlot;
            this.currentCount = currentCount;
            this.capacity = capacity;
        }
    }

    private static final class TargetDemand {
        private final TargetSlot target;
        private int remaining;

        private TargetDemand(TargetSlot target, int remaining) {
            this.target = target;
            this.remaining = remaining;
        }
    }

    private static final class ClickStep {
        private final int slot;
        private final int button;

        private ClickStep(int slot, int button) {
            this.slot = slot;
            this.button = button;
        }
    }

    private static final class MergeGroup {
        private final ItemStack seedStack;
        private final List<SourceSlot> sources = new ArrayList<SourceSlot>();

        private MergeGroup(ItemStack seedStack) {
            this.seedStack = seedStack;
        }
    }

    private static final class MergeSourceState {
        private final int containerSlot;
        private final int actualInventorySlot;
        private int remainingCount;

        private MergeSourceState(int containerSlot, int actualInventorySlot, int remainingCount) {
            this.containerSlot = containerSlot;
            this.actualInventorySlot = actualInventorySlot;
            this.remainingCount = remainingCount;
        }
    }
}
