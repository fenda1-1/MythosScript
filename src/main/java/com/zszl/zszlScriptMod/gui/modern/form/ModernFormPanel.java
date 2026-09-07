package com.zszl.zszlScriptMod.gui.modern.form;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;

import net.minecraft.client.gui.FontRenderer;

/** Thin lifecycle coordinator joining the form definition, session, renderer, and input. */
public final class ModernFormPanel<S> implements ModernSettingsTab {

    private final ModernFormDefinition<S> definition;
    private final ModernFormSession<S> session;
    private final ModernFormRenderer renderer;
    private final ModernFormInput<S> input;
    private final ModernFormSettingsTab<S> owner;
    private boolean initialized;

    public ModernFormPanel(ModernFormDefinition<S> definition, ModernFormSettingsTab<S> owner) {
        if (definition == null || owner == null) {
            throw new IllegalArgumentException("definition and owner are required");
        }
        this.definition = definition;
        this.owner = owner;
        session = new ModernFormSession<>(definition);
        renderer = new ModernFormRenderer(definition);
        input = new ModernFormInput<>(definition, session, renderer, owner);
    }

    public void setCompactHeader(boolean compact) {
        renderer.setCompactHeader(compact);
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        session.load();
        renderer.initialize(fontRenderer);
        renderer.syncInputFields();
        initialized = true;
    }

    @Override
    public void updateScreen() {
        renderer.updateScreen();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        renderer.draw(fontRenderer, contentBounds, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return input.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return input.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        return input.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return input.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return input.handleMouseWheel(wheel);
    }

    @Override
    public boolean handleEscape() {
        return panelClearConfirmation();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = renderer.getContentClipBounds();
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return renderer.getHoveredTooltip();
    }

    @Override
    public void discardDraft() {
        if (!initialized) {
            return;
        }
        session.revert();
        renderer.clearInvalidItems();
        renderer.clearConfirmation();
        renderer.syncInputFields();
    }

    public void applyDraftValues() {
        input.applyDraftValues();
    }

    public boolean tryApplyDraftValues() {
        return input.applyDraftValues();
    }

    public void save() {
        input.save();
    }

    public void revert() {
        input.revert();
    }

    public void restoreDefaults() {
        input.restoreDefaults();
    }

    public void refreshValues() {
        renderer.syncInputFields();
    }

    public void showStatus(String message) {
        renderer.showStatus(message);
    }

    public void scrollToSection(int index) {
        renderer.scrollToSection(index);
    }

    @Override
    public boolean isDirty() {
        return input.hasPendingEdits() || session.isDirty();
    }

    private boolean panelClearConfirmation() {
        return renderer.clearConfirmation();
    }
}
