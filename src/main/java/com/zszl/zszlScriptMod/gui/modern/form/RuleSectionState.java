package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.HashMap;
import java.util.Map;

/** View state belongs to a rule draft, and survives rebuilding its form. */
public final class RuleSectionState {
    private int selected;
    private final Map<Integer, Integer> offsets = new HashMap<>();

    public int selected() { return selected; }
    public int scroll() { return offsets.getOrDefault(selected, 0); }
    public void remember(int offset) { offsets.put(selected, Math.max(0, offset)); }
    public void select(int index) { selected = Math.max(0, index); }
}
