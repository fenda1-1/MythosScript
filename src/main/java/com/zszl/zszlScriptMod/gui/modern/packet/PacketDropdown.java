package com.zszl.zszlScriptMod.gui.modern.packet;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.gui.FontRenderer;

/** Click-to-open option menu used instead of cycling buttons. */
final class PacketDropdown {
    private final String[] options;
    private int selected;
    private boolean open;
    private ModernMainLayout.Rect button = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect menu = new ModernMainLayout.Rect(0, 0, 1, 1);

    PacketDropdown(String... options) {
        this.options = options == null || options.length == 0 ? new String[] { "" } : options;
    }

    int selected() { return selected; }
    String value() { return options[Math.max(0, Math.min(selected, options.length - 1))]; }
    boolean isOpen() { return open; }
    void close() { open = false; }

    void setSelected(int index) {
        selected = Math.max(0, Math.min(index, options.length - 1));
    }

    void setValue(String value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                selected = i;
                return;
            }
        }
    }

    void drawButton(FontRenderer font, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        if (bounds == null) return;
        button = bounds;
        boolean hover = bounds.contains(mouseX, mouseY) || open;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                open ? ModernUiRenderer.ACCENT : hover ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        String text = ModernFormI18n.tr(value());
        int textWidth = font == null ? 0 : font.getStringWidth(text);
        int available = Math.max(4, bounds.width - 24);
        float scale = textWidth > available && textWidth > 0 ? (float) available / textWidth : 1.0F;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(bounds.x + bounds.width / 2.0F - 6.0F,
                bounds.y + bounds.height / 2.0F, 0.0F);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1.0F);
        if (font != null) font.drawString(text, -textWidth / 2.0F, -font.FONT_HEIGHT / 2.0F, ModernUiRenderer.TEXT, false);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        ModernUiRenderer.drawChevron(bounds.right() - 14, bounds.y + 7, false, ModernUiRenderer.MUTED_TEXT);
    }

    void drawMenu(FontRenderer font, ModernMainLayout.Rect host, int mouseX, int mouseY) {
        if (!open || host == null) return;
        int row = 20;
        int height = options.length * row + 6;
        int y = button.bottom() + 2;
        if (y + height > host.bottom()) y = Math.max(host.y, button.y - height - 2);
        menu = new ModernMainLayout.Rect(button.x, y, Math.max(button.width, 80), height);
        ModernUiRenderer.drawPanel(menu.x, menu.y, menu.width, menu.height, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        int rowY = menu.y + 3;
        for (int i = 0; i < options.length; i++, rowY += row) {
            boolean hover = mouseX >= menu.x + 3 && mouseX < menu.right() - 3 && mouseY >= rowY && mouseY < rowY + row - 2;
            boolean chosen = i == selected;
            ModernUiRenderer.drawSubtlePanel(menu.x + 3, rowY, menu.width - 6, row - 2, 3,
                    chosen ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    chosen ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, ModernFormI18n.tr(options[i]), menu.x + 10, rowY + 4, ModernUiRenderer.TEXT,
                    menu.width - 20);
        }
    }

    boolean click(int x, int y) {
        if (open && menu != null && menu.contains(x, y)) {
            int index = (y - menu.y - 3) / 20;
            if (index >= 0 && index < options.length) selected = index;
            open = false;
            return true;
        }
        if (button != null && button.contains(x, y)) {
            open = !open;
            return true;
        }
        if (open) {
            open = false;
        }
        return false;
    }
}
