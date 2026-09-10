package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

import net.minecraft.client.gui.FontRenderer;

/** Thin lifecycle coordinator joining the form definition, session, renderer, and input. */
public final class ModernFormPanel<S> implements ModernSettingsTab {

    private final ModernFormDefinition<S> definition;
    private final ModernFormSession<S> session;
    private final ModernFormRenderer renderer;
    private final ModernFormInput<S> input;
    private final ModernFormSettingsTab<S> owner;
    private com.zszl.zszlScriptMod.gui.modern.RuleSectionNavigation sectionNavigation;
    private RuleSectionState sectionState;
    private boolean initialized;
    private int lastMouseX, lastMouseY;

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

    public void setSectionPages(RuleSectionState state) {
        sectionState = state;
        renderer.setSectionPages(state);
        String[] labels = new String[definition.getSections().size()];
        for (int i=0;i<labels.length;i++) labels[i]=definition.getSections().get(i).getTitle();
        sectionNavigation = new com.zszl.zszlScriptMod.gui.modern.RuleSectionNavigation(labels, null);
    }

    public void rememberScroll(RuleSectionState state) {
        renderer.setSectionPages(state);
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
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        ModernMainLayout.Rect form = sectionNavigation == null ? contentBounds : sectionNavigation.layout(fontRenderer, contentBounds);
        renderer.draw(fontRenderer, form, mouseX, mouseY);
        if (sectionNavigation != null) sectionNavigation.draw(fontRenderer, sectionState.selected(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (sectionNavigation != null && sectionNavigation.contains(mouseX, mouseY)) {
            if (mouseButton == 0) {
                int index = sectionNavigation.click(mouseX, mouseY);
                if (index >= 0 && index != sectionState.selected() && input.applyDraftValues()) {
                    for (ModernFormWidget widget : renderer.widgets()) widget.blur();
                    renderer.selectSection(index);
                }
            }
            return true;
        }
        ModernFormWidget target = renderer.widgetAt(mouseX, mouseY);
        for (ModernFormWidget widget : renderer.widgets()) if (widget != target) widget.blur();
        if (target != null) {
            renderer.clearTextFieldFocus();
            return target.mouseClicked(mouseX, mouseY, mouseButton);
        }
        return input.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (sectionNavigation != null && clickedMouseButton == 0 && sectionNavigation.drag(mouseX, mouseY)) return true;
        for (ModernFormWidget widget : renderer.activeWidgets())
            if (widget.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) return true;
        return input.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (sectionNavigation != null) sectionNavigation.release();
        boolean handled = false;
        for (ModernFormWidget widget : renderer.widgets()) handled |= widget.mouseReleased(mouseX, mouseY, state);
        return input.mouseReleased(mouseX, mouseY, state) || handled;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        for (ModernFormWidget widget : renderer.activeWidgets()) if (widget.keyTyped(typedChar, keyCode)) return true;
        return input.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        return handleMouseWheel(wheel);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (sectionNavigation != null && sectionNavigation.wheel(wheel, lastMouseX, lastMouseY)) return true;
        ModernFormWidget target = renderer.widgetAt(lastMouseX, lastMouseY);
        if (target != null && target.handleMouseWheel(wheel, lastMouseX, lastMouseY)) return true;
        return input.handleMouseWheel(wheel);
    }

    @Override
    public boolean handleEscape() {
        for (ModernFormWidget widget : renderer.activeWidgets()) if (widget.handleEscape()) return true;
        return panelClearConfirmation();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = renderer.getContentClipBounds();
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        String navigationTip = sectionNavigation == null ? "" : sectionNavigation.tooltip(mouseX, mouseY);
        return navigationTip.isEmpty() ? renderer.getHoveredTooltip() : navigationTip;
    }

    public List<GuiElementInspector.GuiElementInfo> getMcpGuiElements(String pathPrefix) {
        List<GuiElementInspector.GuiElementInfo> result = new ArrayList<>();
        if (sectionNavigation != null) {
            result.addAll(sectionNavigation.getMcpGuiElements(pathPrefix,
                    sectionState == null ? -1 : sectionState.selected()));
        }
        result.addAll(renderer.getMcpGuiElements(pathPrefix));
        return result;
    }

    public boolean setMcpText(String target, String text, boolean append) {
        return input.setMcpText(target, text, append);
    }

    public boolean setMcpValue(String target, String value) {
        return input.setMcpValue(target, value);
    }

    public boolean revealMcpTarget(String target) {
        return input.revealMcpTarget(target);
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
