package com.zszl.zszlScriptMod.system.dungeon;

import java.util.*;

/** Inventory-independent calculations shared by warehouse deposit and spread. */
public final class WarehouseDepositPolicy {
    private WarehouseDepositPolicy() { }

    public static List<String> parseNames(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text != null) for (String part : text.split("[,\uFF0C]")) {
            if (!part.trim().isEmpty()) result.add(part.trim());
        }
        return new ArrayList<>(result);
    }

    public static boolean includesSlot(List<Integer> selected, int slot) {
        return slot >= 0 && slot < 36 && (selected == null || selected.isEmpty() || selected.contains(slot));
    }

    public static int excess(int count, int earlierCount, int stackLimit) {
        return Math.max(0, count - Math.max(0, stackLimit - earlierCount));
    }

    public static List<List<Integer>> divideSlots(List<Integer> emptySlots, int itemCount) {
        List<List<Integer>> result = new ArrayList<>();
        if (itemCount <= 0) return result;
        int offset = 0;
        for (int i = 0; i < itemCount; i++) {
            int quota = emptySlots.size() / itemCount + (i < emptySlots.size() % itemCount ? 1 : 0);
            result.add(new ArrayList<>(emptySlots.subList(offset, offset + quota)));
            offset += quota;
        }
        return result;
    }
}
