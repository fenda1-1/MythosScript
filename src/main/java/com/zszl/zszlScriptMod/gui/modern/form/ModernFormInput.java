package com.zszl.zszlScriptMod.gui.modern.form;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

/** Handles form clicks, keyboard input, scrolling, and draft application. */
final class ModernFormInput<S> {

    private final ModernFormDefinition<S> definition;
    private final ModernFormSession<S> session;
    private final ModernFormRenderer renderer;
    private final ModernFormSettingsTab<S> owner;
    private boolean draggingScrollbar;

    ModernFormInput(ModernFormDefinition<S> definition, ModernFormSession<S> session, ModernFormRenderer renderer,
            ModernFormSettingsTab<S> owner) {
        this.definition = definition;
        this.session = session;
        this.renderer = renderer;
        this.owner = owner;
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !contains(renderer.getPanelBounds(), mouseX, mouseY)) {
            return false;
        }
        if (renderer.scrollbarContains(mouseX, mouseY) && renderer.beginScrollbarDrag(mouseX, mouseY)) {
            draggingScrollbar = true;
            return true;
        }
        if (contains(renderer.getHeaderToggleBounds(), mouseX, mouseY) && definition.getHeaderToggle() != null) {
            ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
            toggle.set(!toggle.get());
            session.updateLiveToggle();
            return true;
        }
        if (contains(renderer.getSaveBounds(), mouseX, mouseY)) {
            save();
            return true;
        }
        if (contains(renderer.getRevertBounds(), mouseX, mouseY)) {
            revert();
            return true;
        }
        if (contains(renderer.getDefaultsBounds(), mouseX, mouseY)) {
            restoreDefaults();
            return true;
        }
        ModernMainLayout.Rect content = renderer.getContentClipBounds();
        if (!contains(content, mouseX, mouseY)) {
            renderer.clearTextFieldFocus();
            return true;
        }
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (ModernFormDefinition.Item item : section.getItems()) {
                ModernFormRenderer.ItemView view = renderer.view(item);
                if (!item.isVisible() || !contains(view.rowBounds, mouseX, mouseY)) {
                    continue;
                }
                if (item.getType() != ModernFormDefinition.ItemType.ACTION
                        || !renderer.isConfirmationPending(item)) {
                    renderer.clearConfirmation();
                }
                if (contains(content, mouseX, mouseY) && contains(view.infoBounds, mouseX, mouseY)) {
                    return true;
                }
                if (!item.isEnabled()) {
                    return true;
                }
                if (item.getType() == ModernFormDefinition.ItemType.TOGGLE) {
                    ModernFormSettingsTab.BooleanValue value = (ModernFormSettingsTab.BooleanValue) item.getValue();
                    if (value != null) {
                        value.set(!value.get());
                    }
                    return true;
                }
                if (item.getType() == ModernFormDefinition.ItemType.CHOICE) {
                    ModernFormRenderer.ChoiceHit hit = findChoiceHit(view, mouseX, mouseY);
                    if (hit != null) {
                        setChoiceValue(item, hit.option);
                    }
                    return true;
                }
                if (item.getType() == ModernFormDefinition.ItemType.TEXT
                        || item.getType() == ModernFormDefinition.ItemType.INTEGER
                        || item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                    if (contains(view.controlBounds, mouseX, mouseY) && view.textField != null) {
                        renderer.clearTextFieldFocus();
                        view.textField.setCanLoseFocus(false);
                        view.textField.setFocused(true);
                        ModernUiRenderer.moveTextFieldCursorTo(view.textField, mouseX);
                    } else {
                        renderer.clearTextFieldFocus();
                    }
                    return true;
                }
                if (item.getType() == ModernFormDefinition.ItemType.ACTION
                        && contains(view.controlBounds, mouseX, mouseY)) {
                    executeAction(item);
                    return true;
                }
                return true;
            }
        }
        renderer.clearTextFieldFocus();
        return true;
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)
                && renderer.hasFocusedTextField()) {
            if (applyDraftValues()) {
                renderer.showStatus(ModernFormI18n.tr("gui.modern.form.status.applied"));
            }
            return true;
        }
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (ModernFormDefinition.Item item : section.getItems()) {
                ModernFormRenderer.ItemView view = renderer.view(item);
                if (item.isVisible() && item.isEnabled() && view.textField != null) {
                    if (item.getType() == ModernFormDefinition.ItemType.INTEGER
                            || item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                        boolean handled = ModernUiRenderer.typeNumericField(view.textField, typedChar, keyCode,
                                item.getType() == ModernFormDefinition.ItemType.DECIMAL);
                        if (handled) {
                            return true;
                        }
                    } else if (view.textField.textboxKeyTyped(typedChar, keyCode)) {
                        return true;
                    }
                }
            }
        }
        if (!renderer.hasFocusedTextField()) {
            ModernMainLayout.Rect content = renderer.getContentClipBounds();
            int pageStep = content == null ? 24 : Math.max(24, content.height - 20);
            if (keyCode == Keyboard.KEY_HOME) {
                renderer.scrollBy(-renderer.getMaxScrollOffset());
                return true;
            }
            if (keyCode == Keyboard.KEY_END) {
                renderer.scrollBy(renderer.getMaxScrollOffset());
                return true;
            }
            if (keyCode == Keyboard.KEY_PRIOR) {
                renderer.scrollBy(-pageStep);
                return true;
            }
            if (keyCode == Keyboard.KEY_NEXT) {
                renderer.scrollBy(pageStep);
                return true;
            }
        }
        return false;
    }

    boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!draggingScrollbar || clickedMouseButton != 0) {
            return false;
        }
        renderer.dragScrollbar(mouseX, mouseY);
        return true;
    }

    boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!draggingScrollbar || state != 0) {
            return false;
        }
        draggingScrollbar = false;
        renderer.endScrollbarDrag();
        return true;
    }

    boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || renderer.getContentClipBounds() == null || renderer.getMaxScrollOffset() <= 0) {
            return false;
        }
        int before = renderer.getScrollOffset();
        renderer.scrollBy(wheel > 0 ? -30 : 30);
        return before != renderer.getScrollOffset();
    }

    boolean applyDraftValues() {
        boolean valid = true;
        String firstError = null;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (ModernFormDefinition.Item item : section.getItems()) {
                ModernFormRenderer.ItemView view = renderer.view(item);
                if (!item.isVisible() || !item.isEnabled() || view.textField == null) {
                    continue;
                }
                if (item.getType() == ModernFormDefinition.ItemType.TEXT) {
                    ModernFormSettingsTab.TextValue value = (ModernFormSettingsTab.TextValue) item.getValue();
                    if (value != null) {
                        value.set(view.textField.getText());
                    }
                } else if (item.getType() == ModernFormDefinition.ItemType.INTEGER) {
                    ModernFormSettingsTab.IntValue value = (ModernFormSettingsTab.IntValue) item.getValue();
                    if (value != null) {
                        String raw = view.textField.getText() == null ? "" : view.textField.getText().trim();
                        try {
                            int parsed = Integer.parseInt(raw);
                            if (parsed < item.getIntMin() || parsed > item.getIntMax()) {
                                throw new NumberFormatException();
                            }
                            value.set(parsed);
                            view.textField.setText(String.valueOf(parsed));
                            renderer.markInvalid(item, false);
                        } catch (NumberFormatException ignored) {
                            valid = false;
                            renderer.markInvalid(item, true);
                            view.textField.setFocused(true);
                            if (firstError == null) {
                                firstError = ModernFormI18n.tr("gui.modern.form.error.integer",
                                        ModernFormI18n.tr(item.getTitle()),
                                        String.valueOf(item.getIntMin()), String.valueOf(item.getIntMax()));
                            }
                        }
                    }
                } else if (item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                    ModernFormSettingsTab.FloatValue value = (ModernFormSettingsTab.FloatValue) item.getValue();
                    if (value != null) {
                        String raw = view.textField.getText() == null ? "" : view.textField.getText().trim()
                                .replace(',', '.');
                        try {
                            float parsed = Float.parseFloat(raw);
                            if (Float.isNaN(parsed) || Float.isInfinite(parsed)
                                    || parsed < item.getFloatMin() || parsed > item.getFloatMax()) {
                                throw new NumberFormatException();
                            }
                            value.set(parsed);
                            view.textField.setText(formatFloat(parsed));
                            renderer.markInvalid(item, false);
                        } catch (NumberFormatException ignored) {
                            valid = false;
                            renderer.markInvalid(item, true);
                            view.textField.setFocused(true);
                            if (firstError == null) {
                                firstError = ModernFormI18n.tr("gui.modern.form.error.decimal",
                                        ModernFormI18n.tr(item.getTitle()), formatFloat(item.getFloatMin()),
                                        formatFloat(item.getFloatMax()));
                            }
                        }
                    }
                }
            }
        }
        if (!valid) {
            renderer.showError(firstError == null ? ModernFormI18n.tr("gui.modern.form.status.check_input") : firstError);
            return false;
        }
        renderer.clearInvalidItems();
        renderer.clearTextFieldFocus();
        return true;
    }

    boolean hasPendingEdits() {
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (ModernFormDefinition.Item item : section.getItems()) {
                ModernFormRenderer.ItemView view = renderer.view(item);
                if (view == null || view.textField == null) {
                    continue;
                }
                String text = view.textField.getText() == null ? "" : view.textField.getText();
                if (item.getType() == ModernFormDefinition.ItemType.TEXT) {
                    ModernFormSettingsTab.TextValue value = (ModernFormSettingsTab.TextValue) item.getValue();
                    if (value != null && !text.equals(value.get() == null ? "" : value.get())) {
                        return true;
                    }
                } else if (item.getType() == ModernFormDefinition.ItemType.INTEGER) {
                    ModernFormSettingsTab.IntValue value = (ModernFormSettingsTab.IntValue) item.getValue();
                    if (value != null && !text.trim().equals(String.valueOf(value.get()))) {
                        return true;
                    }
                } else if (item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                    ModernFormSettingsTab.FloatValue value = (ModernFormSettingsTab.FloatValue) item.getValue();
                    if (value != null && !text.trim().equals(formatFloat(value.get()))) {
                        return true;
                    }
                }
            }
        }
        return session.isDirty();
    }

    void save() {
        if (!applyDraftValues()) {
            return;
        }
        try {
            session.save();
            renderer.syncInputFields();
            renderer.showStatus(ModernFormI18n.tr("gui.modern.form.status.saved"));
        } catch (RuntimeException exception) {
            renderer.showError(ModernFormI18n.tr("gui.modern.form.status.save_failed", safeMessage(exception)));
        }
    }

    void revert() {
        session.revert();
        renderer.clearInvalidItems();
        renderer.clearConfirmation();
        renderer.syncInputFields();
        renderer.showStatus(ModernFormI18n.tr("gui.modern.form.status.reverted"));
    }

    void restoreDefaults() {
        if (!definition.getStateAdapter().supportsDefaults()) {
            return;
        }
        session.restoreDefaults();
        renderer.clearInvalidItems();
        renderer.clearConfirmation();
        renderer.syncInputFields();
        renderer.showStatus(ModernFormI18n.tr("gui.modern.form.status.defaults"));
    }

    private void executeAction(ModernFormDefinition.Item item) {
        if (item.getAction() == null) {
            return;
        }
        if (!applyDraftValues()) {
            return;
        }
        if (item.requiresConfirmation() && !renderer.isConfirmationPending(item)) {
            renderer.armConfirmation(item);
            renderer.showStatus(ModernFormI18n.tr("gui.modern.form.status.confirm_click",
                    ModernFormI18n.tr(item.getButtonLabel())));
            return;
        }
        renderer.clearConfirmation();
        @SuppressWarnings("unchecked")
        ModernFormSettingsTab.FormAction<S> action = (ModernFormSettingsTab.FormAction<S>) item.getAction();
        long statusVersion = renderer.getStatusVersion();
        try {
            action.execute(owner);
        } catch (RuntimeException exception) {
            renderer.showError(ModernFormI18n.tr("gui.modern.form.status.action_failed", safeMessage(exception)));
            return;
        }
        if (!item.getActionMessage().isEmpty() && renderer.getStatusVersion() == statusVersion) {
            renderer.showStatus(ModernFormI18n.tr(item.getActionMessage()));
        }
    }

    private static ModernFormRenderer.ChoiceHit findChoiceHit(ModernFormRenderer.ItemView view, int mouseX,
            int mouseY) {
        for (ModernFormRenderer.ChoiceHit hit : view.choiceHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                return hit;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void setChoiceValue(ModernFormDefinition.Item item,
            ModernFormSettingsTab.ChoiceOption<?> option) {
        ModernFormSettingsTab.ChoiceValue<Object> value =
                (ModernFormSettingsTab.ChoiceValue<Object>) item.getValue();
        if (value != null) {
            value.set(option.getValue());
        }
    }

    private static String formatFloat(float value) {
        String text = String.format(java.util.Locale.ROOT, "%.3f", value);
        while (text.indexOf('.') >= 0 && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String safeMessage(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().trim().isEmpty()) {
            return "未知错误";
        }
        return exception.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

}
