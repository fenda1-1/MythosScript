package com.zszl.zszlScriptMod.gui.modern.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Stack for BASE, DETAIL and MODAL panels with deterministic draft cleanup. */
public final class ModernPanelStack {

    private final Deque<ModernPanel> panels = new ArrayDeque<>();

    public void push(ModernPanel panel) {
        if (panel == null) {
            throw new IllegalArgumentException("panel must not be null");
        }
        panels.push(panel);
    }

    public ModernPanel pop() {
        if (panels.isEmpty()) {
            return null;
        }
        ModernPanel panel = panels.pop();
        panel.discardDraft();
        return panel;
    }

    public ModernPanel current() {
        return panels.peek();
    }

    public int depth() {
        return panels.size();
    }

    public void clearAndDiscard() {
        while (!panels.isEmpty()) {
            panels.pop().discardDraft();
        }
    }

    /**
     * Discards nested panels while keeping the root panel available for a
     * later tick or for reopening the workbench inside the same tab.
     */
    public void discardChildren() {
        while (panels.size() > 1) {
            panels.pop().discardDraft();
        }
    }

    public boolean isDirty() {
        for (ModernPanel panel : panels) {
            if (panel != null && panel.isDirty()) {
                return true;
            }
        }
        return false;
    }
}
