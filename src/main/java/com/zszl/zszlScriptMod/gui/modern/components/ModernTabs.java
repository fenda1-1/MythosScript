package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.IntConsumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Compact tab strip with shared hit testing and selection callback. */
public final class ModernTabs implements ModernComponent {

    private String[] labels = { "" };
    private int selected;
    private ModernMainLayout.Rect bounds;
    private boolean visible = true;
    private boolean enabled = true;
    private IntConsumer onSelected;

    public ModernTabs(String[] labels, IntConsumer onSelected) {
        setLabels(labels);
        this.onSelected = onSelected;
    }

    public void setLabels(String[] labels) {
        this.labels = labels == null || labels.length == 0 ? new String[] { "" } : labels.clone();
        selected = Math.max(0, Math.min(selected, this.labels.length - 1));
    }

    public int selected() { return selected; }
    public void setSelected(int index) { selected = Math.max(0, Math.min(labels.length - 1, index)); }
    public ModernTabs setOnSelected(IntConsumer onSelected) { this.onSelected = onSelected; return this; }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        int width = Math.max(1, bounds.width / Math.max(1, labels.length));
        for (int i = 0; i < labels.length; i++) {
            int x = bounds.x + i * width;
            int w = i == labels.length - 1 ? bounds.right() - x : width;
            ModernMainLayout.Rect tab = new ModernMainLayout.Rect(x, bounds.y, Math.max(1, w), bounds.height);
            boolean hovered = enabled && tab.contains(mouseX, mouseY);
            boolean chosen = i == selected;
            ModernUiRenderer.drawSubtlePanel(tab.x, tab.y, tab.width, tab.height, 4,
                    chosen ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    chosen ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, labels[i], tab.x + 6,
                    tab.y + Math.max(3, (tab.height - fontRenderer.FONT_HEIGHT) / 2),
                    chosen || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(1, tab.width - 12));
        }
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || bounds == null || !bounds.contains(mouseX, mouseY)) return false;
        int width = Math.max(1, bounds.width / Math.max(1, labels.length));
        int index = Math.max(0, Math.min(labels.length - 1, (mouseX - bounds.x) / width));
        if (index != selected) {
            selected = index;
            if (onSelected != null) onSelected.accept(index);
        }
        return true;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
