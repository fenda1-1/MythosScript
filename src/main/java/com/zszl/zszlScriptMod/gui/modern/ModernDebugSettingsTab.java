package com.zszl.zszlScriptMod.gui.modern;

import net.minecraft.client.gui.FontRenderer;

/** Entry point for the native terminal-style debug workbench. */
public final class ModernDebugSettingsTab implements ModernSettingsTab {

    private final ModernDebugLogView view = new ModernDebugLogView();

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        view.ensureInitialized(fontRenderer);
    }

    @Override
    public void updateScreen() {
        view.updateScreen();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        view.draw(fontRenderer, contentBounds, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return view.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return view.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        return view.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return view.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return view.handleMouseWheel(wheel);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        return view.handleMouseWheel(wheel, mouseX, mouseY);
    }

    @Override
    public boolean handleEscape() {
        return view.handleEscape();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return view.containsContent(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return view.getHoveredTooltip(mouseX, mouseY);
    }

    @Override
    public void discardDraft() {
        view.discardDraft();
    }

    @Override
    public boolean isDirty() {
        return view.isDirty();
    }
}
