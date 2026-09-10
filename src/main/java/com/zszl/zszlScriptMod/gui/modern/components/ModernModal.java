package com.zszl.zszlScriptMod.gui.modern.components;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Modal shell with backdrop, title and a child component host. */
public final class ModernModal implements ModernComponent {

    private final ModernComponentHost body = new ModernComponentHost(null);
    private ModernMainLayout.Rect screenBounds;
    private ModernMainLayout.Rect bounds;
    private String title = "";
    private String subtitle = "";
    private boolean open;
    private boolean dismissOnOutsideClick = true;

    public ModernModal setScreenBounds(ModernMainLayout.Rect screenBounds) {
        this.screenBounds = screenBounds;
        return this;
    }

    public ModernModal setTitle(String title) { this.title = title == null ? "" : title; return this; }
    public ModernModal setSubtitle(String subtitle) { this.subtitle = subtitle == null ? "" : subtitle; return this; }
    public ModernModal setDismissOnOutsideClick(boolean value) { dismissOnOutsideClick = value; return this; }
    public ModernModal open() { open = true; return this; }
    public void close() { open = false; body.clearFocus(); }
    public boolean isOpen() { return open; }
    public ModernComponentHost body() { return body; }

    @Override
    public void layout(ModernMainLayout.Rect bounds) {
        this.bounds = bounds;
        if (bounds != null) {
            body.setBounds(new ModernMainLayout.Rect(bounds.x + 12, bounds.y + 48,
                    Math.max(1, bounds.width - 24), Math.max(1, bounds.height - 60)));
        }
    }

    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!open || bounds == null) return;
        ModernMainLayout.Rect backdrop = screenBounds == null ? bounds : screenBounds;
        ModernUiRenderer.drawBackdropOverlay(backdrop, ModernUiRenderer.BACKDROP);
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, title, bounds.x + 13, bounds.y + 10,
                ModernUiRenderer.TEXT, Math.max(1, bounds.width - 26));
        if (!subtitle.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, subtitle, bounds.x + 13, bounds.y + 27,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, bounds.width - 26));
        }
        body.draw(fontRenderer, mouseX, mouseY);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (!open || bounds == null) return false;
        if (!bounds.contains(mouseX, mouseY)) {
            if (dismissOnOutsideClick && mouseButton == 0) close();
            return true;
        }
        if (body.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        return true;
    }

    @Override public boolean keyTyped(char typedChar, int keyCode) { return open && body.keyTyped(typedChar, keyCode); }
    @Override public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) { return open && body.handleMouseWheel(wheel, mouseX, mouseY); }
    @Override public boolean isVisible() { return open; }
    @Override public void setVisible(boolean visible) { if (visible) open(); else close(); }
}
