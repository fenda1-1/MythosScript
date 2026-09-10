package com.zszl.zszlScriptMod.gui.modern.form;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;

/** Embedded control. Its model must be included in the owning form's StateAdapter. */
public interface ModernFormWidget extends ModernSettingsTab {
    default void ensureInitialized(net.minecraft.client.gui.FontRenderer font) { }
    default void updateScreen() { }
    default void discardDraft() { }
    default boolean keyTyped(char c, int key) { return false; }
    default boolean handleMouseWheel(int wheel) { return false; }
    default boolean containsContent(int x, int y) { return false; }
    default String getHoveredTooltip(int x, int y) { return ""; }
    default boolean isTextInputFocused() { return false; }
    int height(int width, int availableHeight);
    default void setViewport(com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect viewport) { }
    default Object snapshot() { return null; }
    default boolean commit() { return true; }
    default void blur() { }
}
