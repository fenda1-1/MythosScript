package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups related tabs and moves them as one block. Consecutive tabs that share
 * a dependency root stay together, so child tabs cannot be reordered inside
 * their parent chain.
 */
public final class ModernTabOrder {

    public interface RootOf<T> {
        Object of(T item);
    }

    private ModernTabOrder() {
    }

    public static <T> List<List<T>> grouped(List<T> tabs, RootOf<T> roots) {
        List<List<T>> groups = new ArrayList<>();
        if (tabs == null || tabs.isEmpty()) {
            return groups;
        }
        Object currentRoot = null;
        List<T> current = null;
        for (int i = 0; i < tabs.size(); i++) {
            T tab = tabs.get(i);
            Object root = roots == null ? tab : roots.of(tab);
            if (root == null) {
                root = tab;
            }
            if (current == null || !root.equals(currentRoot)) {
                current = new ArrayList<>();
                groups.add(current);
                currentRoot = root;
            }
            current.add(tab);
        }
        return groups;
    }

    public static <T> int groupIndexOf(List<List<T>> groups, T tab) {
        if (groups == null || tab == null) {
            return -1;
        }
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(tab)) {
                return i;
            }
        }
        return -1;
    }

    public static <T> List<T> flatten(List<List<T>> groups) {
        List<T> out = new ArrayList<>();
        if (groups == null) {
            return out;
        }
        for (int i = 0; i < groups.size(); i++) {
            out.addAll(groups.get(i));
        }
        return out;
    }

    public static <T> boolean moveGroup(List<List<T>> groups, int from, int insertBefore) {
        if (groups == null || from < 0 || from >= groups.size()) {
            return false;
        }
        int dest = insertBefore;
        if (dest < 0) {
            dest = 0;
        }
        if (dest > groups.size()) {
            dest = groups.size();
        }
        if (from == dest || from + 1 == dest) {
            return false;
        }
        List<T> moved = groups.remove(from);
        if (dest > from) {
            dest--;
        }
        groups.add(dest, moved);
        return true;
    }

    public static int insertBeforeFromPointer(int[] mids, int mouse, int minInsert) {
        int count = mids == null ? 0 : mids.length;
        int min = Math.max(0, Math.min(minInsert, count));
        for (int i = 0; i < count; i++) {
            if (mouse < mids[i]) {
                return Math.max(min, i);
            }
        }
        return Math.max(min, count);
    }
}
