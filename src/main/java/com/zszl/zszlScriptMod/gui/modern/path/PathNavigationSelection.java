package com.zszl.zszlScriptMod.gui.modern.path;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selection follows the displayed tree order, excluding filtered or collapsed rows. */
final class PathNavigationSelection {
    private final Set<String> selected = new LinkedHashSet<>();
    private String anchor;

    void reset(String name) {
        selected.clear();
        if (name != null && !name.isEmpty()) selected.add(name);
        anchor = name;
    }

    boolean contains(String name) {
        return selected.contains(name);
    }

    String click(String name, List<String> visible, boolean ctrl, boolean shift) {
        int start = visible.indexOf(anchor);
        int end = visible.indexOf(name);
        if (shift && start >= 0 && end >= 0) {
            if (!ctrl) selected.clear();
            selected.addAll(visible.subList(Math.min(start, end), Math.max(start, end) + 1));
        } else if (ctrl) {
            if (!selected.add(name)) selected.remove(name);
            anchor = name;
        } else {
            reset(name);
        }
        if (selected.contains(name)) return name;
        return selected.isEmpty() ? null : selected.iterator().next();
    }
}
