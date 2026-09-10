package com.zszl.zszlScriptMod.gui.modern.components;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import net.minecraft.client.gui.FontRenderer;

/**
 * Common lifecycle for controls owned by the modern GUI.
 *
 * A component owns one hit rectangle and uses that same rectangle for layout,
 * drawing, focus and pointer handling.  This keeps individual tabs from
 * having to duplicate the same input bookkeeping.
 */
public interface ModernComponent {

    void layout(ModernMainLayout.Rect bounds);

    ModernMainLayout.Rect bounds();

    default void update() {
    }

    void draw(FontRenderer fontRenderer, int mouseX, int mouseY);

    /** Returns true when the component consumed the pointer press. */
    boolean click(int mouseX, int mouseY, int mouseButton);

    default boolean mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        return false;
    }

    default boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    default boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    default boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        return false;
    }

    default boolean contains(int mouseX, int mouseY) {
        ModernMainLayout.Rect rect = bounds();
        return isVisible() && rect != null && rect.contains(mouseX, mouseY);
    }

    default boolean isVisible() {
        return true;
    }

    default void setVisible(boolean visible) {
    }

    default boolean isEnabled() {
        return true;
    }

    default void setEnabled(boolean enabled) {
    }

    default boolean isFocusable() {
        return false;
    }

    default boolean isFocused() {
        return false;
    }

    default void setFocused(boolean focused) {
    }

    default void clearFocus() {
        setFocused(false);
    }
}
