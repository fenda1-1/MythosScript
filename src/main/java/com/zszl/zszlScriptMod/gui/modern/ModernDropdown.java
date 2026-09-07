package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.gui.FontRenderer;

/** Shared click-to-open option control for compact modern settings forms. */
public final class ModernDropdown {

    private static final int ROW_HEIGHT = 20;

    private final String[] values;
    private final String[] labels;
    private int selected;
    private boolean open;
    private ModernMainLayout.Rect button;
    private ModernMainLayout.Rect menu;
    private int menuColumns = 1;

    public ModernDropdown(String[] values, String[] labels) {
        this.values = normalize(values);
        this.labels = normalizeLabels(labels, this.values);
    }

    public int selectedIndex() {
        return selected;
    }

    public String value() {
        return values[selected];
    }

    public void setSelectedIndex(int index) {
        selected = clamp(index, 0, values.length - 1);
    }

    public void setValue(String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (value.equalsIgnoreCase(values[i])) {
                selected = i;
                return;
            }
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        menu = null;
    }

    public void drawButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        button = bounds;
        menu = null;
        boolean hovered = bounds.contains(mouseX, mouseY) || open;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                open ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        String label = labelAt(selected);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT,
                Math.max(8, bounds.width - 28));
        ModernUiRenderer.drawDropdownChevron(bounds.right() - 16,
                bounds.y + Math.max(5, (bounds.height - 8) / 2),
                hovered || open ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    /** Draws the open menu after the rest of the form so it stays on top. */
    public void drawMenu(FontRenderer fontRenderer, ModernMainLayout.Rect host, int mouseX, int mouseY) {
        if (!open || button == null || host == null || host.width <= 0 || host.height <= 0) {
            return;
        }
        menuColumns = host.height < optionsHeight() ? 2 : 1;
        int menuRows = (values.length + menuColumns - 1) / menuColumns;
        int menuHeight = menuRows * ROW_HEIGHT + 6;
        int menuWidth = Math.max(button.width, preferredMenuWidth(fontRenderer));
        menuWidth = Math.min(menuWidth, Math.max(1, host.width));
        int menuX = clamp(button.x, host.x, Math.max(host.x, host.right() - menuWidth));
        int menuY = button.bottom() + 3;
        if (menuY + menuHeight > host.bottom()) {
            menuY = button.y - menuHeight - 3;
        }
        if (menuY < host.y) {
            menuY = host.y;
        }
        if (menuY + menuHeight > host.bottom()) {
            menuHeight = Math.max(1, host.bottom() - menuY);
        }
        menu = new ModernMainLayout.Rect(menuX, menuY, menuWidth, menuHeight);

        ModernUiRenderer.beginClip(host);
        ModernUiRenderer.drawPanel(menu.x, menu.y, menu.width, menu.height, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        int visibleRows = Math.max(0, (menu.height - 6) / ROW_HEIGHT);
        int columnWidth = Math.max(1, (menu.width - 6) / menuColumns);
        int rowY = menu.y + 3;
        for (int row = 0; row < visibleRows; row++, rowY += ROW_HEIGHT) {
            for (int column = 0; column < menuColumns; column++) {
                int index = row * menuColumns + column;
                if (index >= values.length) continue;
                int cellX = menu.x + 3 + column * columnWidth;
                boolean hovered = mouseX >= cellX && mouseX < cellX + columnWidth
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                boolean chosen = index == selected;
                ModernUiRenderer.drawSubtlePanel(cellX, rowY, Math.max(1, columnWidth - 2), ROW_HEIGHT - 2, 3,
                        chosen ? 0xFF2C3D49 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        chosen ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(fontRenderer, labelAt(index), cellX + 7, rowY + 4,
                        chosen || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(8, columnWidth - 14));
            }
        }
        ModernUiRenderer.endClip();
    }

    /** Handles both option selection and closing when the user clicks away. */
    public boolean mouseClicked(int mouseX, int mouseY) {
        boolean wasOpen = open;
        if (open && menu != null && menu.contains(mouseX, mouseY)) {
            int row = (mouseY - menu.y - 3) / ROW_HEIGHT;
            int columnWidth = Math.max(1, (menu.width - 6) / menuColumns);
            int column = (mouseX - menu.x - 3) / columnWidth;
            int index = row * menuColumns + column;
            int visibleRows = Math.max(0, (menu.height - 6) / ROW_HEIGHT);
            if (row >= 0 && row < visibleRows && column >= 0 && column < menuColumns
                    && index < values.length) {
                selected = index;
            }
            close();
            return true;
        }
        if (button != null && button.contains(mouseX, mouseY)) {
            open = !open;
            if (!open) {
                menu = null;
            }
            return true;
        }
        if (wasOpen) {
            close();
            return true;
        }
        return false;
    }

    private String labelAt(int index) {
        return ModernFormI18n.tr(labels[clamp(index, 0, labels.length - 1)]);
    }

    private int optionsHeight() {
        return values.length * ROW_HEIGHT + 6;
    }

    private int preferredMenuWidth(FontRenderer fontRenderer) {
        if (fontRenderer == null) {
            return 96;
        }
        int width = 0;
        for (String label : labels) {
            width = Math.max(width, fontRenderer.getStringWidth(ModernFormI18n.tr(label)));
        }
        return Math.max(96, width + 20);
    }

    private static String[] normalize(String[] source) {
        if (source == null || source.length == 0) {
            return new String[] { "" };
        }
        String[] result = source.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                result[i] = "";
            }
        }
        return result;
    }

    private static String[] normalizeLabels(String[] source, String[] fallback) {
        String[] result = source == null || source.length != fallback.length ? fallback.clone() : source.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                result[i] = fallback[i];
            }
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
