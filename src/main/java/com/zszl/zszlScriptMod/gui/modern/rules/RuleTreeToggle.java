package com.zszl.zszlScriptMod.gui.modern.rules;

/** Detects a second left-click on the same rule row so workbenches can toggle quickly. */
public final class RuleTreeToggle {
    static final long WINDOW_MS = 300L;

    private Object last;
    private long at;

    public boolean doubleClicked(Object rule) {
        long now = System.currentTimeMillis();
        boolean twice = rule != null && rule == last && now - at <= WINDOW_MS;
        last = twice ? null : rule;
        at = now;
        return twice;
    }
}
