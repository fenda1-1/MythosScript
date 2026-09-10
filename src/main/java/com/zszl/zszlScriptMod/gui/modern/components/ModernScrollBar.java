package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.IntConsumer;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import net.minecraft.client.gui.FontRenderer;

/** Component wrapper for the shared hover scrollbar. */
public final class ModernScrollBar implements ModernComponent {

    private final ModernHoverScrollbar scrollbar;
    private ModernMainLayout.Rect bounds;
    private int scroll;
    private int maxScroll;
    private int visibleExtent = 1;
    private int totalExtent = 1;
    private IntConsumer onScroll;
    private boolean visible = true;
    private boolean enabled = true;

    public ModernScrollBar(ModernHoverScrollbar.Axis axis) {
        scrollbar = new ModernHoverScrollbar(axis);
    }

    public ModernScrollBar setMetrics(int scroll, int maxScroll, int visibleExtent, int totalExtent) {
        this.scroll = Math.max(0, Math.min(Math.max(0, maxScroll), scroll));
        this.maxScroll = Math.max(0, maxScroll);
        this.visibleExtent = Math.max(1, visibleExtent);
        this.totalExtent = Math.max(this.visibleExtent, totalExtent);
        return this;
    }

    public ModernScrollBar setOnScroll(IntConsumer onScroll) { this.onScroll = onScroll; return this; }
    public int scroll() { return scroll; }
    public boolean isDragging() { return scrollbar.isDragging(); }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || !enabled || bounds == null) return;
        scrollbar.draw(bounds, scroll, maxScroll, visibleExtent, totalExtent, mouseX, mouseY, value -> {
            scroll = value;
            if (onScroll != null) onScroll.accept(value);
        });
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || bounds == null) return false;
        return scrollbar.beginDrag(mouseX, mouseY);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        return mouseButton == 0 && scrollbar.applyDrag(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !scrollbar.isDragging()) return false;
        scrollbar.endDrag();
        return true;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; if (!visible) scrollbar.endDrag(); }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) scrollbar.endDrag(); }
}
