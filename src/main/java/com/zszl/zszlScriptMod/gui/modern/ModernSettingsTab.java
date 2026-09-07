package com.zszl.zszlScriptMod.gui.modern;

import net.minecraft.client.gui.FontRenderer;

/**
 * Lifecycle shared by configuration pages embedded in the modern main-window
 * tab strip. Implementations deliberately own their controls and hit testing,
 * keeping the main screen independent from feature-specific settings.
 */
public interface ModernSettingsTab {

    void ensureInitialized(FontRenderer fontRenderer);

    void updateScreen();

    void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY);

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton);

    /**
     * Receives a drag with the same screen-space coordinates used by draw.
     * Existing tabs do not need to implement this unless they own draggable UI.
     */
    default boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return false;
    }

    /** Receives mouse release without forcing the main shell to own tab state. */
    default boolean mouseReleased(int mouseX, int mouseY, int state) {
        return false;
    }

    boolean keyTyped(char typedChar, int keyCode);

    /** Returns true while a text field owned by this tab has keyboard focus. */
    default boolean isTextInputFocused() {
        return ModernTextInputFocus.isFocused(this);
    }

    /** Clears text focus after a click outside an input, using tab coordinates. */
    default void clearTextInputFocusOutside(int mouseX, int mouseY) {
        ModernTextInputFocus.clearFocusOutside(this, mouseX, mouseY);
    }

    boolean handleMouseWheel(int wheel);

    /**
     * Coordinate-aware wheel routing for scaled embedded workbenches. Existing
     * tabs retain their previous wheel behavior through this default method.
     */
    default boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        return handleMouseWheel(wheel);
    }

    /** Returns true when Escape was consumed by a nested panel or modal. */
    default boolean handleEscape() {
        return false;
    }

    /** Lets an external command launcher select a concrete editable row. */
    default void focusCommand(String command) {
    }

    /**
     * Lets a tab request that the shell closes it after a local return action.
     * The request is consumable so reopening the same cached tab is safe.
     */
    default boolean consumeReturnRequest() {
        return false;
    }

    /** Saves the active tab draft when the global save shortcut is used. */
    default void save() {
    }

    boolean containsContent(int mouseX, int mouseY);

    /**
     * Returns a tooltip only while one of this tab's information icons is
     * hovered. The main screen owns drawing the shared tooltip panel.
     */
    String getHoveredTooltip(int mouseX, int mouseY);

    /**
     * Abandons unsaved work after an explicit user discard/close action.
     * Hiding the persistent main screen must not call this method.
     */
    void discardDraft();

    /** Returns true when closing this tab would discard user edits. */
    default boolean isDirty() {
        return false;
    }
}
