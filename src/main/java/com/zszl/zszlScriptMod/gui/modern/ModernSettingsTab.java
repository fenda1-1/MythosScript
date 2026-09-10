package com.zszl.zszlScriptMod.gui.modern;

import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

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
     * Publishes semantic controls for the MCP GUI inspector. Existing tabs
     * may use the reflective fallback until they expose their own custom hit
     * regions. The fallback only reads already-laid-out UI objects.
     */
    default List<GuiElementInspector.GuiElementInfo> getMcpGuiElements() {
        return GuiElementInspector.collectMcpElements(this, "custom");
    }

    /**
     * Route-aware variant used by the modern shell. Explicit publishers may
     * return absolute paths; the default publisher returns relative paths and
     * the shell qualifies them with the active tab command.
     */
    default List<GuiElementInspector.GuiElementInfo> getMcpGuiElements(String pathPrefix) {
        return getMcpGuiElements();
    }

    /**
     * Replaces or appends text in a semantic input owned by this tab. The
     * default keeps older tabs source-compatible while allowing richer tabs
     * to provide direct MCP editing without synthetic mouse coordinates.
     */
    default boolean setMcpText(String target, String text, boolean append) {
        return GuiElementInspector.setMcpText(this, target, text, append);
    }

    /** Replaces a reflected text/dropdown value when the page has no richer adapter. */
    default boolean setMcpValue(String target, String value) {
        return GuiElementInspector.setMcpValue(this, target, value);
    }

    /** Brings an off-screen semantic control into the active viewport when possible. */
    default boolean revealMcpTarget(String target) {
        return false;
    }

    /**
     * Abandons unsaved work after an explicit user discard/close action.
     * Hiding the persistent main screen must not call this method.
     */
    void discardDraft();

    /** Returns true when closing this tab would discard user edits. */
    default boolean isDirty() {
        return false;
    }

    /**
     * Gives a composite workbench a chance to rebuild transient editor
     * instances after persisted navigation state has been restored.
     */
    default void refreshAfterStateRestore() {
    }
}
