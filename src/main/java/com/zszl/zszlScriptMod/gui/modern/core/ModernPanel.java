package com.zszl.zszlScriptMod.gui.modern.core;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import net.minecraft.client.gui.FontRenderer;

/** Lifecycle contract for a screen fragment that stays inside the modern shell. */
public interface ModernPanel {

    void ensureInitialized(FontRenderer fontRenderer);

    void updateScreen();

    void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY);

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton);

    /** Optional drag hook for embedded editors (for example HEX selection). */
    default boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return false;
    }

    /** Optional release hook paired with {@link #mouseClickMove}. */
    default boolean mouseReleased(int mouseX, int mouseY, int state) {
        return false;
    }

    boolean keyTyped(char typedChar, int keyCode);

    boolean handleMouseWheel(int wheel);

    /** Saves the current panel draft without forcing navigation. */
    default void save() {
    }

    boolean contains(int mouseX, int mouseY);

    String getHoveredTooltip(int mouseX, int mouseY);

    void discardDraft();

    default boolean isDirty() {
        return false;
    }
}
