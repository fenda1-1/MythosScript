package com.zszl.zszlScriptMod.gui.modern.path;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selection uses source indices; range selection follows the filtered display order. */
final class ExecutionLogSelection {
    private final Set<Integer> selected = new LinkedHashSet<>();
    private int anchor = -1;

    void click(int index, List<Integer> visible, boolean control, boolean shift) {
        if (!visible.contains(index)) return;
        int from = visible.indexOf(anchor);
        if (shift && from >= 0) {
            if (!control) selected.clear();
            int to = visible.indexOf(index);
            for (int i = Math.min(from, to); i <= Math.max(from, to); i++) selected.add(visible.get(i));
        } else {
            if (!control) selected.clear();
            if (!selected.add(index) && control) selected.remove(index);
            anchor = index;
        }
    }

    void retainVisible(List<Integer> visible) {
        selected.retainAll(visible);
        if (!visible.contains(anchor)) anchor = -1;
    }

    void selectAll(List<Integer> visible) {
        selected.clear();
        selected.addAll(visible);
        anchor = visible.isEmpty() ? -1 : visible.get(0);
    }

    boolean contains(int index) { return selected.contains(index); }
    int size() { return selected.size(); }
    void clear() { selected.clear(); anchor = -1; }
}
