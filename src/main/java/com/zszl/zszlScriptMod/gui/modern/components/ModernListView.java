package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Basic selectable, scrollable list for common modern panels. */
public final class ModernListView implements ModernComponent {

    private static final int ROW_HEIGHT = 22;
    private final List<String> items = new ArrayList<>();
    private ModernMainLayout.Rect bounds;
    private int selected = -1;
    private int scroll;
    private boolean visible = true;
    private boolean enabled = true;
    private BiConsumer<Integer, String> onSelected;

    public ModernListView setItems(List<String> values) {
        items.clear();
        if (values != null) items.addAll(values);
        selected = selected >= items.size() ? items.size() - 1 : selected;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        return this;
    }

    public List<String> items() { return Collections.unmodifiableList(items); }
    public int selectedIndex() { return selected; }
    public String selectedValue() { return selected >= 0 && selected < items.size() ? items.get(selected) : ""; }
    public ModernListView setOnSelected(BiConsumer<Integer, String> onSelected) { this.onSelected = onSelected; return this; }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; scroll = Math.min(scroll, maxScroll()); }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5,
                ModernUiRenderer.INPUT_SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int visibleRows = Math.max(1, (bounds.height - 6) / ROW_HEIGHT);
        ModernUiRenderer.beginClip(new ModernMainLayout.Rect(bounds.x + 3, bounds.y + 3,
                Math.max(1, bounds.width - 6), Math.max(1, bounds.height - 6)));
        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= items.size()) break;
            int y = bounds.y + 4 + row * ROW_HEIGHT;
            ModernMainLayout.Rect rowBounds = new ModernMainLayout.Rect(bounds.x + 5, y,
                    Math.max(1, bounds.width - 10), ROW_HEIGHT - 3);
            boolean hovered = rowBounds.contains(mouseX, mouseY);
            boolean chosen = index == selected;
            ModernUiRenderer.drawSubtlePanel(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, 3,
                    chosen ? ModernUiRenderer.SELECTED_SURFACE
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    chosen ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, items.get(index), rowBounds.x + 7, rowBounds.y + 5,
                    ModernUiRenderer.TEXT, Math.max(1, rowBounds.width - 14));
        }
        ModernUiRenderer.endClip();
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || bounds == null || !bounds.contains(mouseX, mouseY)) return false;
        int visibleRows = Math.max(1, (bounds.height - 6) / ROW_HEIGHT);
        int row = (mouseY - bounds.y - 4) / ROW_HEIGHT;
        int index = scroll + row;
        if (row >= 0 && row < visibleRows && index >= 0 && index < items.size()) {
            selected = index;
            if (onSelected != null) onSelected.accept(index, items.get(index));
        }
        return true;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (!visible || bounds == null || !bounds.contains(mouseX, mouseY) || wheel == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel > 0 ? -1 : 1)));
        return true;
    }

    private int maxScroll() {
        int visibleRows = bounds == null ? 1 : Math.max(1, (bounds.height - 6) / ROW_HEIGHT);
        return Math.max(0, items.size() - visibleRows);
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
