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
    private ModernFormDefinition.Item draggingNumeric;

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
        draggingNumeric = null;
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
                        if (item.getType() != ModernFormDefinition.ItemType.TEXT
                                && renderer.numericSliderContains(view, mouseX, mouseY)) {
                            draggingNumeric = item;
                            updateNumericValue(item, view, mouseX);
                            return true;
                        }
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

    boolean setMcpText(String target, String text, boolean append) {
        for (ModernFormWidget widget : renderer.activeWidgets()) {
            if (widget != null && widget.setMcpText(target, text, append)) return true;
        }
        ItemMatch match = findMcpItem(target);
        if (match == null || !isTextItem(match.item) || !match.item.isEnabled()
                || match.view == null || match.view.textField == null) {
            return false;
        }
        renderer.clearTextFieldFocus();
        match.view.textField.setCanLoseFocus(false);
        String incoming = text == null ? "" : text;
        match.view.textField.setText(append ? safe(match.view.textField.getText()) + incoming : incoming);
        match.view.textField.setFocused(true);
        return true;
    }

    boolean setMcpValue(String target, String value) {
        String requested = value == null ? "" : value.trim();
        if (isHeaderToggleTarget(target) && definition.getHeaderToggle() != null) {
            Boolean parsed = parseBoolean(requested);
            if (parsed == null) return false;
            definition.getHeaderToggle().set(parsed.booleanValue());
            session.updateLiveToggle();
            return true;
        }
        for (ModernFormWidget widget : renderer.activeWidgets()) {
            if (widget != null && widget.setMcpValue(target, value)) return true;
        }
        ItemMatch match = findMcpItem(target);
        if (match == null || !match.item.isEnabled() || match.view == null || match.view.rowBounds == null) return false;
        switch (match.item.getType()) {
        case TOGGLE:
            Boolean toggle = parseBoolean(requested);
            if (toggle == null) return false;
            ModernFormSettingsTab.BooleanValue bool = (ModernFormSettingsTab.BooleanValue) match.item.getValue();
            if (bool != null) bool.set(toggle.booleanValue());
            return bool != null;
        case CHOICE:
            return setChoiceByValue(match.item, requested, target);
        case TEXT:
        case INTEGER:
        case DECIMAL:
            return setMcpText(target, value, false);
        default:
            return false;
        }
    }

    boolean revealMcpTarget(String target) {
        for (ModernFormWidget widget : renderer.activeWidgets()) {
            if (widget != null && widget.revealMcpTarget(target)) return true;
        }
        ItemMatch match = findMcpItem(target);
        return match != null && renderer.revealMcpItem(match.item);
    }

    private boolean setChoiceByValue(ModernFormDefinition.Item item, String requested, String target) {
        ModernFormSettingsTab.ChoiceValue<Object> value = choiceValue(item);
        if (value == null) return false;
        for (ModernFormSettingsTab.ChoiceOption<?> option : item.getOptions()) {
            String optionValue = option == null || option.getValue() == null ? "" : String.valueOf(option.getValue());
            String optionLabel = option == null ? "" : ModernFormI18n.tr(option.getLabel());
            boolean targetOption = normalize(target).contains("/choice/" + normalizeSegment(optionValue));
            if (targetOption || requested.equalsIgnoreCase(optionValue)
                    || requested.equalsIgnoreCase(optionLabel)) {
                value.set(option.getValue());
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static ModernFormSettingsTab.ChoiceValue<Object> choiceValue(ModernFormDefinition.Item item) {
        return (ModernFormSettingsTab.ChoiceValue<Object>) item.getValue();
    }

    private ItemMatch findMcpItem(String target) {
        String normalized = normalize(target);
        if (normalized.isEmpty()) return null;
        int sectionIndex = 0;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            int itemIndex = 0;
            for (ModernFormDefinition.Item item : section.getItems()) {
                String segment = normalizeSegment(item.getTitle()) + "-" + itemIndex;
                String titleSegment = normalizeSegment(item.getTitle());
                boolean matches = normalized.endsWith("/field/" + segment)
                        || normalized.contains("/field/" + segment + "/")
                        || normalized.equals(segment) || normalized.equals(titleSegment)
                        || normalized.endsWith("/" + titleSegment);
                if (matches && item.isVisible()) {
                    return new ItemMatch(item, renderer.view(item), sectionIndex, itemIndex);
                }
                itemIndex++;
            }
            sectionIndex++;
        }
        return null;
    }

    private static boolean isHeaderToggleTarget(String target) {
        String normalized = normalize(target);
        return normalized.equals("header/toggle") || normalized.endsWith("/header/toggle")
                || normalized.equals("header_toggle");
    }

    private static boolean isTextItem(ModernFormDefinition.Item item) {
        if (item == null) return false;
        return item.getType() == ModernFormDefinition.ItemType.TEXT
                || item.getType() == ModernFormDefinition.ItemType.INTEGER
                || item.getType() == ModernFormDefinition.ItemType.DECIMAL;
    }

    private static Boolean parseBoolean(String value) {
        if (value == null) return null;
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "1".equals(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value) || "0".equals(value)) return Boolean.FALSE;
        return null;
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.replace('\\', '/').trim().toLowerCase(java.util.Locale.ROOT);
        while (result.startsWith("/")) result = result.substring(1);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String normalizeSegment(String value) {
        String result = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]+", "_").toLowerCase(java.util.Locale.ROOT);
        return result.isEmpty() ? "item" : result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ItemMatch {
        private final ModernFormDefinition.Item item;
        private final ModernFormRenderer.ItemView view;
        @SuppressWarnings("unused")
        private final int sectionIndex;
        @SuppressWarnings("unused")
        private final int itemIndex;

        private ItemMatch(ModernFormDefinition.Item item, ModernFormRenderer.ItemView view,
                int sectionIndex, int itemIndex) {
            this.item = item;
            this.view = view;
            this.sectionIndex = sectionIndex;
            this.itemIndex = itemIndex;
        }
    }

    boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingNumeric != null && clickedMouseButton == 0) {
            ModernFormRenderer.ItemView view = renderer.view(draggingNumeric);
            if (view == null || view.controlBounds == null) {
                draggingNumeric = null;
                return false;
            }
            updateNumericValue(draggingNumeric, view, mouseX);
            return true;
        }
        if (!draggingScrollbar || clickedMouseButton != 0) {
            return false;
        }
        renderer.dragScrollbar(mouseX, mouseY);
        return true;
    }

    boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingNumeric != null && state == 0) { draggingNumeric = null; return true; }
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

    private void updateNumericValue(ModernFormDefinition.Item item, ModernFormRenderer.ItemView view, int mouseX) {
        double min = item.getType() == ModernFormDefinition.ItemType.INTEGER ? item.getIntMin() : item.getFloatMin();
        double max = item.getType() == ModernFormDefinition.ItemType.INTEGER ? item.getIntMax() : item.getFloatMax();
        double fraction = (mouseX - view.controlBounds.x - 8D)
                / Math.max(1D, view.controlBounds.width - 16D);
        double value = min + Math.max(0D, Math.min(1D, fraction)) * (max - min);
        if (item.getType() == ModernFormDefinition.ItemType.INTEGER) {
            ((ModernFormSettingsTab.IntValue) item.getValue()).set((int) Math.round(value));
        } else {
            ((ModernFormSettingsTab.FloatValue) item.getValue()).set((float) value);
        }
        renderer.syncInputFields();
    }

    boolean applyDraftValues() {
        for (ModernFormWidget widget : renderer.widgets()) {
            if (!widget.commit()) {
                renderer.revealWidget(widget);
                renderer.showError("请检查面板中的输入，或按 Esc 取消编辑。");
                return false;
            }
        }
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
        for (ModernFormWidget widget : renderer.widgets()) if (widget.isDirty()) return true;
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
