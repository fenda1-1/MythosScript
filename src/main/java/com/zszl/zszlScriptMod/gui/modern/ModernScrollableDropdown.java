package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import net.minecraft.client.gui.FontRenderer;

/** A compact dropdown with a clipped, vertically scrollable option menu. */
public final class ModernScrollableDropdown {

    private static final int ROW_HEIGHT = 20;
    private static final int MAX_VISIBLE_ROWS = 8;

    private String[] values = { "" };
    private String[] labels = { "" };
    private int selected;
    private int scroll;
    private int maxScroll;
    private int visibleRows = 1;
    private boolean open;
    private ModernMainLayout.Rect button;
    private ModernMainLayout.Rect menu;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();

    public void setOptions(String[] values, String[] labels) {
        String previous = value();
        this.values = normalize(values);
        this.labels = normalizeLabels(labels, this.values);
        selected = indexOf(previous);
        if (selected < 0) selected = 0;
        scroll = clamp(scroll, 0, Math.max(0, this.values.length - 1));
    }

    public String value() {
        return values[Math.max(0, Math.min(selected, values.length - 1))];
    }

    public void setValue(String value) {
        if (value == null) return;
        int index = indexOf(value);
        if (index >= 0) selected = index;
    }

    public boolean isOpen() { return open; }

    public boolean isDragging() { return scrollbar.isDragging(); }

    public void close() {
        open = false;
        menu = null;
        scrollbar.endDrag();
        scrollbar.idle();
    }

    public void endDrag() { scrollbar.endDrag(); }

    public void drawButton(FontRenderer font, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        if (bounds == null) return;
        button = bounds;
        menu = null;
        if (!open) scrollbar.idle();
        boolean hovered = bounds.contains(mouseX, mouseY) || open;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                open ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        String label = ModernFormI18n.tr(labels[Math.max(0, Math.min(selected, labels.length - 1))]);
        ModernUiRenderer.drawText(font, label, bounds.x + 7,
                bounds.y + (bounds.height - font.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT,
                Math.max(8, bounds.width - 25));
        ModernUiRenderer.drawDropdownChevron(bounds.right() - 15,
                bounds.y + Math.max(5, (bounds.height - 8) / 2),
                hovered || open ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    /** Draws the open menu above the rest of the page content. */
    public void drawMenu(FontRenderer font, ModernMainLayout.Rect host, int mouseX, int mouseY) {
        if (!open || button == null || host == null || host.width <= 0 || host.height <= 0) {
            scrollbar.idle();
            return;
        }
        int menuWidth = Math.min(Math.max(button.width, preferredMenuWidth(font)), Math.max(1, host.width));
        int menuX = clamp(button.x, host.x, Math.max(host.x, host.right() - menuWidth));
        int desiredRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, values.length));
        int menuHeight = desiredRows * ROW_HEIGHT + 6;
        int menuY = button.bottom() + 3;
        if (menuY + menuHeight > host.bottom()) menuY = button.y - menuHeight - 3;
        if (menuY < host.y) menuY = host.y;
        int availableRows = Math.max(1, (host.bottom() - menuY - 6) / ROW_HEIGHT);
        visibleRows = Math.max(1, Math.min(desiredRows, availableRows));
        menuHeight = visibleRows * ROW_HEIGHT + 6;
        menu = new ModernMainLayout.Rect(menuX, menuY, menuWidth, menuHeight);
        maxScroll = Math.max(0, values.length - visibleRows);
        scroll = clamp(scroll, 0, maxScroll);

        ModernUiRenderer.beginClip(host);
        ModernUiRenderer.drawPanel(menu.x, menu.y, menu.width, menu.height, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        int contentWidth = Math.max(1, menu.width - ModernHoverScrollbar.GUTTER);
        ModernMainLayout.Rect content = new ModernMainLayout.Rect(menu.x + 3, menu.y + 3,
                Math.max(1, contentWidth - 3), Math.max(1, menu.height - 6));
        ModernUiRenderer.beginClip(content);
        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= values.length) break;
            int rowY = menu.y + 3 + row * ROW_HEIGHT;
            boolean hovered = mouseX >= content.x && mouseX < content.right()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            boolean chosen = index == selected;
            ModernUiRenderer.drawSubtlePanel(content.x, rowY, content.width, ROW_HEIGHT - 2, 3,
                    chosen ? ModernUiRenderer.SELECTED_SURFACE
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    chosen ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, ModernFormI18n.tr(labels[index]), content.x + 7, rowY + 4,
                    chosen || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(8, content.width - 14));
        }
        ModernUiRenderer.endClip();
        scrollbar.draw(menu, scroll, maxScroll, visibleRows, values.length, mouseX, mouseY,
                value -> scroll = value);
        ModernUiRenderer.endClip();
    }

    /** Handles option selection, scrollbar dragging, toggling, and click-away closing. */
    public boolean mouseClicked(int mouseX, int mouseY) {
        if (open) {
            if (scrollbar.beginDrag(mouseX, mouseY)) return true;
            if (menu != null && menu.contains(mouseX, mouseY)) {
                int contentRight = menu.right() - ModernHoverScrollbar.GUTTER;
                if (mouseX < contentRight) {
                    int row = (mouseY - menu.y - 3) / ROW_HEIGHT;
                    int index = scroll + row;
                    if (row >= 0 && row < visibleRows && index >= 0 && index < values.length) selected = index;
                }
                close();
                return true;
            }
            if (button != null && button.contains(mouseX, mouseY)) {
                close();
                return true;
            }
            close();
            return true;
        }
        if (button != null && button.contains(mouseX, mouseY)) {
            open = true;
            scroll = 0;
            return true;
        }
        return false;
    }

    public boolean mouseClickMove(int mouseX, int mouseY) {
        return scrollbar.applyDrag(mouseX, mouseY);
    }

    public boolean wheel(int amount, int mouseX, int mouseY) {
        if (!open) return false;
        if (menu != null && menu.contains(mouseX, mouseY) && maxScroll > 0)
            scroll = clamp(scroll + (amount > 0 ? -1 : 1), 0, maxScroll);
        return true;
    }

    private int indexOf(String value) {
        if (value == null) return -1;
        for (int i = 0; i < values.length; i++) if (value.equals(values[i])) return i;
        return -1;
    }

    private int preferredMenuWidth(FontRenderer font) {
        if (font == null) return 96;
        int width = 0;
        for (String label : labels) width = Math.max(width, font.getStringWidth(ModernFormI18n.tr(label)));
        return Math.max(96, width + ModernHoverScrollbar.GUTTER + 14);
    }

    private static String[] normalize(String[] source) {
        if (source == null || source.length == 0) return new String[] { "" };
        String[] result = source.clone();
        for (int i = 0; i < result.length; i++) if (result[i] == null) result[i] = "";
        return result;
    }

    private static String[] normalizeLabels(String[] source, String[] fallback) {
        String[] result = source == null || source.length != fallback.length ? fallback.clone() : source.clone();
        for (int i = 0; i < result.length; i++) if (result[i] == null) result[i] = fallback[i];
        return result;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
