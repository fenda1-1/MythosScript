package com.zszl.zszlScriptMod.gui.modern.components;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Keyboard-accessible modern button with one canonical hit rectangle. */
public final class ModernButton implements ModernComponent {

    private ModernMainLayout.Rect bounds;
    private String label = "";
    private boolean primary;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focused;
    private Runnable action;

    public ModernButton(String label, boolean primary, Runnable action) {
        this.label = label == null ? "" : label;
        this.primary = primary;
        this.action = action;
    }

    public ModernButton setLabel(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    public ModernButton setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }

    public ModernButton setAction(Runnable action) {
        this.action = action;
        return this;
    }

    @Override
    public void layout(ModernMainLayout.Rect bounds) {
        this.bounds = bounds;
    }

    @Override
    public ModernMainLayout.Rect bounds() {
        return bounds;
    }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = ModernUiRenderer.DISABLED_SURFACE;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.MUTED_TEXT;
        } else if (primary) {
            fill = hovered || focused ? ModernUiRenderer.ACCENT : ModernUiRenderer.ACCENT_DIM;
            border = hovered || focused ? ModernUiRenderer.ACCENT : ModernUiRenderer.ACCENT_DIM;
            text = ModernUiRenderer.SHELL;
        } else {
            fill = hovered || focused ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hovered || focused ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + Math.max(3, (bounds.height - fontRenderer.FONT_HEIGHT) / 2), text,
                Math.max(1, bounds.width - 12));
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || !contains(mouseX, mouseY)) return false;
        if (action != null) action.run();
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused || !enabled) return false;
        if (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN
                || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER
                || keyCode == org.lwjgl.input.Keyboard.KEY_SPACE) {
            if (action != null) action.run();
            return true;
        }
        return false;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; if (!visible) focused = false; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) focused = false; }
    @Override public boolean isFocusable() { return true; }
    @Override public boolean isFocused() { return focused; }
    @Override public void setFocused(boolean focused) { this.focused = focused && visible && enabled; }
}
