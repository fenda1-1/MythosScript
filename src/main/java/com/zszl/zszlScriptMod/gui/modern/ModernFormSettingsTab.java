package com.zszl.zszlScriptMod.gui.modern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.form.ModernFormBuilder;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormDefinition;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormPanel;

import net.minecraft.client.gui.FontRenderer;

/** Public compatibility facade for declarative modern settings forms. */
public final class ModernFormSettingsTab<S> implements ModernSettingsTab {

    private int pendingScrollSection = -1;

    private static final StateAdapter<Object> EMPTY_STATE_ADAPTER = new StateAdapter<Object>() {
        @Override
        public void load() {
        }

        @Override
        public Object capture() {
            return null;
        }

        @Override
        public void restore(Object state) {
        }
    };

    public interface StateAdapter<S> {
        void load();

        S capture();

        void restore(S state);

        default S copy(S state) {
            return state;
        }

        default void save() {
        }

        default boolean supportsDefaults() {
            return true;
        }

        default void restoreDefaults() {
        }

        default S createDefaults() {
            return null;
        }
    }

    public interface BooleanValue {
        boolean get();

        void set(boolean value);
    }

    public interface TextValue {
        String get();

        void set(String value);
    }

    public interface StringValue extends TextValue {
    }

    public interface IntValue {
        int get();

        void set(int value);
    }

    public interface FloatValue {
        float get();

        void set(float value);
    }

    public interface ChoiceValue<T> {
        T get();

        void set(T value);
    }

    public interface ReadOnlyValue {
        String get();
    }

    public interface Condition {
        boolean matches();
    }

    public interface EnabledPredicate extends Condition {
    }

    public interface FormAction<S> {
        void execute(ModernFormSettingsTab<S> tab);
    }

    public enum ActionStyle {
        PRIMARY,
        SECONDARY,
        WARNING
    }

    public static final class ChoiceOption<T> {
        private final T value;
        private final String label;

        private ChoiceOption(T value, String label) {
            this.value = value;
            this.label = label == null ? "" : label;
        }

        public T getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        public boolean matches(Object selected) {
            return value == null ? selected == null : value.equals(selected);
        }
    }

    public static final class Builder<S> {
        private final ModernFormBuilder<S> delegate;

        private Builder(ModernFormBuilder<S> delegate) {
            this.delegate = delegate;
        }

        public Builder<S> headerToggle(BooleanValue value, String tooltip) {
            delegate.headerToggle(value, tooltip);
            return this;
        }

        public Builder<S> enabledToggle(BooleanValue value, String tooltip) {
            delegate.enabledToggle(value, tooltip);
            return this;
        }

        public Builder<S> headerToggleDefault(boolean value) {
            delegate.headerToggleDefault(value);
            return this;
        }

        public Builder<S> actionLabels(String save, String revert, String defaults) {
            delegate.actionLabels(save, revert, defaults);
            return this;
        }

        public Builder<S> footerVisible(boolean visible) {
            delegate.footerVisible(visible);
            return this;
        }

        public Builder<S> section(String title, String tooltip) {
            delegate.section(title, tooltip);
            return this;
        }

        public Builder<S> toggle(String title, String tooltip, BooleanValue value) {
            delegate.toggle(title, tooltip, value);
            return this;
        }

        public Builder<S> toggle(String title, String detail, String tooltip, BooleanValue value) {
            delegate.toggle(title, detail, tooltip, value);
            return this;
        }

        public Builder<S> text(String title, String tooltip, TextValue value, String placeholder, int maxLength) {
            delegate.text(title, tooltip, value, placeholder, maxLength);
            return this;
        }

        public Builder<S> text(String title, String tooltip, TextValue value) {
            delegate.text(title, tooltip, value);
            return this;
        }

        public Builder<S> integer(String title, String tooltip, IntValue value, int min, int max) {
            delegate.integer(title, tooltip, value, min, max);
            return this;
        }

        public Builder<S> integer(String title, String detail, String tooltip, IntValue value, int min, int max) {
            delegate.integer(title, detail, tooltip, value, min, max);
            return this;
        }

        public Builder<S> decimal(String title, String tooltip, FloatValue value, float min, float max) {
            delegate.decimal(title, tooltip, value, min, max);
            return this;
        }

        public Builder<S> decimal(String title, String detail, String tooltip, FloatValue value, float min,
                float max) {
            delegate.decimal(title, detail, tooltip, value, min, max);
            return this;
        }

        public <T> Builder<S> choice(String title, String tooltip, ChoiceValue<T> value,
                List<ChoiceOption<T>> options) {
            delegate.choice(title, tooltip, value, options);
            return this;
        }

        public Builder<S> choice(String title, String detail, String tooltip, ChoiceValue<String> value,
                String[] choices, String[] labels) {
            delegate.choice(title, detail, tooltip, value, choices, labels);
            return this;
        }

        public Builder<S> readOnly(String title, String tooltip, ReadOnlyValue value) {
            delegate.readOnly(title, tooltip, value);
            return this;
        }

        public Builder<S> readOnly(String title, String tooltip, final String value) {
            delegate.readOnly(title, tooltip, value);
            return this;
        }

        public Builder<S> action(String title, String tooltip, String buttonLabel, ActionStyle style,
                FormAction<S> action) {
            delegate.action(title, tooltip, buttonLabel, style, action);
            return this;
        }

        public Builder<S> action(String title, String tooltip, String buttonLabel, ActionStyle style,
                String successMessage, FormAction<S> action) {
            delegate.action(title, tooltip, buttonLabel, style, successMessage, action);
            return this;
        }

        public Builder<S> visibleWhen(Condition condition) {
            delegate.visibleWhen(condition);
            return this;
        }

        public Builder<S> enabledWhen(Condition condition) {
            delegate.enabledWhen(condition);
            return this;
        }

        public Builder<S> enabledWhen(EnabledPredicate condition) {
            delegate.enabledWhen(condition);
            return this;
        }

        public Builder<S> defaultValue(Object value) {
            delegate.defaultValue(value);
            return this;
        }

        public Builder<S> confirmLastAction() {
            delegate.confirmLastAction();
            return this;
        }

        public ModernFormSettingsTab<S> build() {
            return new ModernFormSettingsTab<>(delegate.build());
        }
    }

    public ModernFormSettingsTab<S> compactHeader() {
        panel.setCompactHeader(true);
        return this;
    }

    private final ModernFormPanel<S> panel;

    private ModernFormSettingsTab(ModernFormDefinition<S> definition) {
        panel = new ModernFormPanel<>(definition, this);
    }

    public static <S> Builder<S> builder(String title, String headerTooltip, StateAdapter<S> stateAdapter) {
        return new Builder<>(new ModernFormBuilder<>(title, "", headerTooltip, stateAdapter));
    }

    public static <S> Builder<S> builder(String title, String subtitle, String headerTooltip,
            StateAdapter<S> stateAdapter) {
        return new Builder<>(new ModernFormBuilder<>(title, subtitle, headerTooltip, stateAdapter));
    }

    public static <S> Builder<S> builder(String title, StateAdapter<S> stateAdapter) {
        return new Builder<>(new ModernFormBuilder<>(title, "", "", stateAdapter));
    }

    @SuppressWarnings("unchecked")
    public static Builder<Object> builder(String title, String subtitle, String headerTooltip) {
        return new Builder<>(new ModernFormBuilder<>(title, subtitle, headerTooltip,
                (StateAdapter<Object>) EMPTY_STATE_ADAPTER));
    }

    public static Builder<Object> builder(String title) {
        return builder(title, "", "");
    }

    public static <T> ChoiceOption<T> option(T value, String label) {
        return new ChoiceOption<>(value, label);
    }

    @SafeVarargs
    public static <T> List<ChoiceOption<T>> options(ChoiceOption<T>... options) {
        if (options == null || options.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(options);
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        panel.ensureInitialized(fontRenderer);
    }

    @Override
    public void updateScreen() {
        panel.updateScreen();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        panel.draw(fontRenderer, contentBounds, mouseX, mouseY);
        if (pendingScrollSection >= 0) {
            int section = pendingScrollSection;
            pendingScrollSection = -1;
            panel.scrollToSection(section);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return panel.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return panel.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        return panel.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return panel.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return panel.handleMouseWheel(wheel);
    }

    @Override
    public boolean handleEscape() {
        return panel.handleEscape();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return panel.containsContent(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return panel.getHoveredTooltip(mouseX, mouseY);
    }

    @Override
    public void discardDraft() {
        panel.discardDraft();
    }

    public void applyDraftValues() {
        panel.applyDraftValues();
    }

    public boolean tryApplyDraftValues() {
        return panel.tryApplyDraftValues();
    }

    @Override
    public void save() {
        panel.save();
    }

    public void revert() {
        panel.revert();
    }

    public void restoreDefaults() {
        panel.restoreDefaults();
    }

    public void refreshValues() {
        panel.refreshValues();
    }

    public void showStatus(String message) {
        panel.showStatus(message);
    }

    public void scrollToSection(int index) {
        pendingScrollSection = index;
        panel.scrollToSection(index);
    }

    @Override
    public boolean isDirty() {
        return panel.isDirty();
    }
}
