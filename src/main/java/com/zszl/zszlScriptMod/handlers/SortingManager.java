// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/SortingManager.java
package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.system.dungeon.SortingRule;
import com.zszl.zszlScriptMod.utils.ModUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

public class SortingManager {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * 智能整理（无规则版）：
     * 1) 指定存放物品优先靠前
     * 2) 其余物品按名称排序
     */
    public static void sortSmart(ContainerChest container, Set<String> designatedItems) {
        if (mc.player == null || container == null)
            return;

        mc.player.sendMessage(new TextComponentString("§b[仓库整理] §f开始智能整理（无规则模式）..."));

        List<ItemStack> designated = new ArrayList<>();
        List<ItemStack> others = new ArrayList<>();

        for (int i = 0; i < 54; i++) {
            Slot slot = container.getSlot(i);
            if (slot == null || !slot.getHasStack()) {
                continue;
            }
            ItemStack stack = slot.getStack().copy();
            String name = stack.getDisplayName();
            if (designatedItems != null && designatedItems.contains(name)) {
                designated.add(stack);
            } else {
                others.add(stack);
            }
        }

        Comparator<ItemStack> byName = Comparator.comparing(s -> s.getDisplayName().toLowerCase(Locale.ROOT));
        designated.sort(byName);
        others.sort(byName);

        Map<Integer, ItemStack> plan = new HashMap<>();
        int slot = 0;
        for (ItemStack s : designated) {
            if (slot >= 54)
                break;
            plan.put(slot++, s);
        }
        for (ItemStack s : others) {
            if (slot >= 54)
                break;
            plan.put(slot++, s);
        }

        List<ModUtils.Click> moves = generateMoveSequence(container, plan);
        if (moves.isEmpty()) {
            mc.player.sendMessage(new TextComponentString("§b[仓库整理] §a当前已是目标顺序，无需整理。"));
            return;
        }

        mc.player.sendMessage(new TextComponentString("§b[仓库整理] §a方案计算完毕，共需 " + moves.size() + " 步操作。开始执行..."));
        executeMoves(container.windowId, moves);
    }

    public static void sort(ContainerChest container, List<SortingRule> rules) {
        if (mc.player == null || container == null)
            return;
        mc.player.sendMessage(new TextComponentString(I18n.format("msg.sorting.start_plan")));

        Map<Integer, ItemStack> currentItems = new HashMap<>();
        for (int i = 0; i < 54; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                currentItems.put(i, slot.getStack().copy());
            }
        }

        Map<Integer, ItemStack> plan = new HashMap<>();
        Set<Integer> usedOriginalSlots = new HashSet<>();

        for (SortingRule rule : rules) {
            if (!rule.enabled)
                continue;

            List<ItemStack> matchingItems = new ArrayList<>();
            List<Integer> matchingSlots = new ArrayList<>();

            for (Map.Entry<Integer, ItemStack> entry : currentItems.entrySet()) {
                if (!usedOriginalSlots.contains(entry.getKey()) && matchesRule(entry.getValue(), rule)) {
                    matchingItems.add(entry.getValue());
                    matchingSlots.add(entry.getKey());
                }
            }

            matchingItems.sort(Comparator.comparing(ItemStack::getDisplayName));

            for (int i = 0; i < matchingItems.size(); i++) {
                ItemStack itemToPlace = matchingItems.get(i);
                int originalSlot = matchingSlots.get(i);

                for (int targetSlot : rule.targetSlots) {
                    if (!plan.containsKey(targetSlot)) {
                        plan.put(targetSlot, itemToPlace);
                        usedOriginalSlots.add(originalSlot);
                        break;
                    }
                }
            }
        }

        List<Integer> remainingEmptySlots = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (!plan.containsKey(i)) {
                remainingEmptySlots.add(i);
            }
        }
        int emptySlotIndex = 0;
        for (Map.Entry<Integer, ItemStack> entry : currentItems.entrySet()) {
            if (!usedOriginalSlots.contains(entry.getKey())) {
                if (emptySlotIndex < remainingEmptySlots.size()) {
                    plan.put(remainingEmptySlots.get(emptySlotIndex++), entry.getValue());
                }
            }
        }

        List<ModUtils.Click> moves = generateMoveSequence(container, plan);
        if (moves.isEmpty()) {
            mc.player.sendMessage(new TextComponentString(I18n.format("msg.sorting.no_need")));
            return;
        }
        mc.player.sendMessage(new TextComponentString(I18n.format("msg.sorting.plan_done", moves.size())));

        executeMoves(container.windowId, moves);
    }

    private static boolean matchesRule(ItemStack stack, SortingRule rule) {
        boolean isShulker = stack.getItem() instanceof ItemShulkerBox;
        if (rule.itemType == SortingRule.ItemType.SHULKER_ONLY && !isShulker)
            return false;
        if (rule.itemType == SortingRule.ItemType.NON_SHULKER_ONLY && isShulker)
            return false;

        if (rule.itemKeywords == null || rule.itemKeywords.isEmpty()) {
            return true;
        }

        String itemName = stack.getDisplayName().toLowerCase();
        if (rule.matchMode == SortingRule.MatchMode.ANY) {
            return rule.itemKeywords.stream().anyMatch(keyword -> itemName.contains(keyword.toLowerCase()));
        } else {
            return rule.itemKeywords.stream().allMatch(keyword -> itemName.contains(keyword.toLowerCase()));
        }
    }

    private static List<ModUtils.Click> generateMoveSequence(ContainerChest container, Map<Integer, ItemStack> plan) {
        List<ModUtils.Click> moves = new ArrayList<>();
        Map<Integer, ItemStack> currentState = new HashMap<>();
        for (int i = 0; i < 54; i++) {
            if (container.getSlot(i).getHasStack()) {
                currentState.put(i, container.getSlot(i).getStack());
            }
        }

        int tempSlot = -1;
        for (int i = 54; i < container.inventorySlots.size(); i++) {
            if (!container.getSlot(i).getHasStack()) {
                tempSlot = i;
                break;
            }
        }
        if (tempSlot == -1) {
            mc.player.sendMessage(new TextComponentString(I18n.format("msg.sorting.no_temp_slot")));
            return moves;
        }

        for (int i = 0; i < 54; i++) {
            ItemStack currentStack = currentState.get(i);
            ItemStack plannedStack = plan.get(i);

            if (currentStack == null && plannedStack == null)
                continue;
            if (currentStack != null && plannedStack != null
                    && ItemStack.areItemStacksEqual(currentStack, plannedStack))
                continue;

            if (currentStack != null) {
                moves.add(new ModUtils.Click(i, 0, ClickType.PICKUP));
                moves.add(new ModUtils.Click(tempSlot, 0, ClickType.PICKUP));
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : plan.entrySet()) {
            int targetSlot = entry.getKey();
            ItemStack targetStack = entry.getValue();

            moves.add(new ModUtils.Click(tempSlot, 0, ClickType.PICKUP));
            moves.add(new ModUtils.Click(targetSlot, 0, ClickType.PICKUP));
        }

        return moves;
    }

    private static void executeMoves(int windowId, List<ModUtils.Click> moves) {
        int delay = DungeonWarehouseHandler.settings.clickIntervalMs / 50;
        for (int i = 0; i < moves.size(); i++) {
            final ModUtils.Click move = moves.get(i);
            ModUtils.DelayScheduler.instance.schedule(() -> {
                if (mc.player != null && mc.player.openContainer != null
                        && mc.player.openContainer.windowId == windowId) {
                    mc.playerController.windowClick(windowId, move.slot, move.button, move.type, mc.player);
                }
            }, i * delay);
        }
    }
}

