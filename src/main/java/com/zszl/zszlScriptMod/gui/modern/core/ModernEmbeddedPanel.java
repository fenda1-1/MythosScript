package com.zszl.zszlScriptMod.gui.modern.core;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import net.minecraft.client.gui.FontRenderer;

/** Base panel for embedded content; it owns only shared geometry and lifecycle defaults. */
public abstract class ModernEmbeddedPanel implements ModernPanel {

    private final String title;
    private ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(0, 0, 1, 1);
    private boolean initialized;

    protected ModernEmbeddedPanel(String title) {
        this.title = title == null ? "" : title;
    }

    public final String getTitle() {
        return title;
    }

    public final ModernMainLayout.Rect getBounds() {
        return bounds;
    }

    public final void setBounds(ModernMainLayout.Rect bounds) {
        if (bounds != null) {
            this.bounds = bounds;
        }
    }

    public final ModernMainLayout.Rect getContentBounds() {
        return bounds.inset(Math.min(12, Math.min(bounds.width, bounds.height) / 2));
    }

    @Override
    public final void ensureInitialized(FontRenderer fontRenderer) {
        if (!initialized) {
            initialized = true;
            onInitialize(fontRenderer);
        }
    }

    protected void onInitialize(FontRenderer fontRenderer) {
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return "";
    }

    @Override
    public void discardDraft() {
    }
}
