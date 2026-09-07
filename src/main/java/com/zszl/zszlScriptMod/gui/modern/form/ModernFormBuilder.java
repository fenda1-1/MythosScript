package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;

/** Fluent definition builder kept outside the public tab facade. */
public final class ModernFormBuilder<S> {

    private final String title;
    private final String subtitle;
    private final String headerTooltip;
    private final ModernFormSettingsTab.StateAdapter<S> stateAdapter;
    private final List<ModernFormDefinition.Section> sections = new ArrayList<>();
    private ModernFormDefinition.SectionBuilder currentSection;
    private ItemBuilder lastItem;
    private ModernFormSettingsTab.BooleanValue headerToggle;
    private String headerToggleTooltip = "gui.modern.form.header_toggle_tip";
    private Boolean headerDefaultValue;
    private String saveLabel = "gui.modern.form.save";
    private String revertLabel = "gui.modern.form.revert";
    private String defaultsLabel = "gui.modern.form.defaults";
    private boolean footerVisible = true;

    public ModernFormBuilder(String title, String subtitle, String headerTooltip,
            ModernFormSettingsTab.StateAdapter<S> stateAdapter) {
        this.title = safe(title);
        this.subtitle = safe(subtitle);
        this.headerTooltip = safe(headerTooltip);
        if (stateAdapter == null) {
            throw new IllegalArgumentException("stateAdapter cannot be null");
        }
        this.stateAdapter = stateAdapter;
    }

    public ModernFormBuilder<S> headerToggle(ModernFormSettingsTab.BooleanValue value, String tooltip) {
        headerToggle = value;
        headerToggleTooltip = safe(tooltip);
        return this;
    }

    public ModernFormBuilder<S> enabledToggle(ModernFormSettingsTab.BooleanValue value, String tooltip) {
        return headerToggle(value, tooltip);
    }

    public ModernFormBuilder<S> headerToggleDefault(boolean value) {
        headerDefaultValue = Boolean.valueOf(value);
        return this;
    }

    public ModernFormBuilder<S> actionLabels(String save, String revert, String defaults) {
        saveLabel = fallback(save, "gui.modern.form.save");
        revertLabel = fallback(revert, "gui.modern.form.revert");
        defaultsLabel = fallback(defaults, "gui.modern.form.defaults");
        return this;
    }

    public ModernFormBuilder<S> footerVisible(boolean visible) {
        footerVisible = visible;
        return this;
    }

    public ModernFormBuilder<S> section(String title, String tooltip) {
        currentSection = new ModernFormDefinition.SectionBuilder(title, tooltip);
        sections.add(currentSection.build());
        lastItem = null;
        return this;
    }

    public ModernFormBuilder<S> toggle(String title, String tooltip, ModernFormSettingsTab.BooleanValue value) {
        return append(item(ModernFormDefinition.ItemType.TOGGLE, title, tooltip, value, "", "", "", 1, 0, 0,
                0.0F, 0.0F, ModernFormSettingsTab.ActionStyle.SECONDARY, null, null));
    }

    public ModernFormBuilder<S> toggle(String title, String detail, String tooltip,
            ModernFormSettingsTab.BooleanValue value) {
        return toggle(title, mergeTooltip(detail, tooltip), value);
    }

    public ModernFormBuilder<S> text(String title, String tooltip, ModernFormSettingsTab.TextValue value,
            String placeholder, int maxLength) {
        return append(item(ModernFormDefinition.ItemType.TEXT, title, tooltip, value, placeholder, "", "", maxLength,
                0, 0, 0.0F, 0.0F, ModernFormSettingsTab.ActionStyle.SECONDARY, null, null));
    }

    public ModernFormBuilder<S> text(String title, String tooltip, ModernFormSettingsTab.TextValue value) {
        return text(title, tooltip, value, "", 256);
    }

    public ModernFormBuilder<S> integer(String title, String tooltip, ModernFormSettingsTab.IntValue value, int min,
            int max) {
        return append(item(ModernFormDefinition.ItemType.INTEGER, title, tooltip, value, "", "", "", 32, min, max,
                0.0F, 0.0F, ModernFormSettingsTab.ActionStyle.SECONDARY, null, null));
    }

    public ModernFormBuilder<S> integer(String title, String detail, String tooltip,
            ModernFormSettingsTab.IntValue value, int min, int max) {
        return integer(title, mergeTooltip(detail, tooltip), value, min, max);
    }

    public ModernFormBuilder<S> decimal(String title, String tooltip, ModernFormSettingsTab.FloatValue value,
            float min, float max) {
        return append(item(ModernFormDefinition.ItemType.DECIMAL, title, tooltip, value, "", "", "", 32, 0, 0, min,
                max, ModernFormSettingsTab.ActionStyle.SECONDARY, null, null));
    }

    public ModernFormBuilder<S> decimal(String title, String detail, String tooltip,
            ModernFormSettingsTab.FloatValue value, float min, float max) {
        return decimal(title, mergeTooltip(detail, tooltip), value, min, max);
    }

    public <T> ModernFormBuilder<S> choice(String title, String tooltip, ModernFormSettingsTab.ChoiceValue<T> value,
            List<ModernFormSettingsTab.ChoiceOption<T>> options) {
        List<ModernFormSettingsTab.ChoiceOption<?>> safeOptions = new ArrayList<>();
        if (options != null) {
            safeOptions.addAll(options);
        }
        return append(item(ModernFormDefinition.ItemType.CHOICE, title, tooltip, value, "", "", "", 1, 0, 0,
                0.0F, 0.0F, ModernFormSettingsTab.ActionStyle.SECONDARY, safeOptions, null));
    }

    public ModernFormBuilder<S> choice(String title, String detail, String tooltip,
            ModernFormSettingsTab.ChoiceValue<String> value, String[] choices, String[] labels) {
        List<ModernFormSettingsTab.ChoiceOption<String>> options = new ArrayList<>();
        if (choices != null) {
            for (int i = 0; i < choices.length; i++) {
                String choice = choices[i];
                String label = labels != null && i < labels.length ? labels[i] : choice;
                options.add(ModernFormSettingsTab.option(choice, label));
            }
        }
        return choice(title, mergeTooltip(detail, tooltip), value, options);
    }

    public ModernFormBuilder<S> readOnly(String title, String tooltip, ModernFormSettingsTab.ReadOnlyValue value) {
        return append(item(ModernFormDefinition.ItemType.READ_ONLY, title, tooltip, value, "", "", "", 1, 0, 0,
                0.0F, 0.0F, ModernFormSettingsTab.ActionStyle.SECONDARY, null, null));
    }

    public ModernFormBuilder<S> readOnly(String title, String tooltip, final String value) {
        return readOnly(title, tooltip, new ModernFormSettingsTab.ReadOnlyValue() {
            @Override
            public String get() {
                return value;
            }
        });
    }

    public ModernFormBuilder<S> action(String title, String tooltip, String buttonLabel,
            ModernFormSettingsTab.ActionStyle style, ModernFormSettingsTab.FormAction<S> action) {
        return action(title, tooltip, buttonLabel, style, "", action);
    }

    public ModernFormBuilder<S> action(String title, String tooltip, String buttonLabel,
            ModernFormSettingsTab.ActionStyle style, String successMessage, ModernFormSettingsTab.FormAction<S> action) {
        return append(item(ModernFormDefinition.ItemType.ACTION, title, tooltip, null, "", buttonLabel, successMessage, 1,
                0, 0, 0.0F, 0.0F, style, null, action));
    }

    public ModernFormBuilder<S> visibleWhen(ModernFormSettingsTab.Condition condition) {
        if (lastItem != null) {
            lastItem.visibleWhen = condition;
            replaceLastItem();
        }
        return this;
    }

    public ModernFormBuilder<S> enabledWhen(ModernFormSettingsTab.Condition condition) {
        if (lastItem != null) {
            lastItem.enabledWhen = condition;
            replaceLastItem();
        }
        return this;
    }

    public ModernFormBuilder<S> enabledWhen(ModernFormSettingsTab.EnabledPredicate condition) {
        return enabledWhen((ModernFormSettingsTab.Condition) condition);
    }

    public ModernFormBuilder<S> defaultValue(Object value) {
        if (lastItem != null) {
            lastItem.defaultValue = value;
            lastItem.hasDefaultValue = true;
            replaceLastItem();
        }
        return this;
    }

    /** Requires a second click before the most recent action is executed. */
    public ModernFormBuilder<S> confirmLastAction() {
        if (lastItem != null) {
            lastItem.requiresConfirmation = true;
            replaceLastItem();
        }
        return this;
    }

    public ModernFormDefinition<S> build() {
        return new ModernFormDefinition<>(this);
    }

    String getTitle() {
        return title;
    }

    String getSubtitle() {
        return subtitle;
    }

    String getHeaderTooltip() {
        return headerTooltip;
    }

    ModernFormSettingsTab.StateAdapter<S> getStateAdapter() {
        return stateAdapter;
    }

    List<ModernFormDefinition.Section> getSections() {
        return sections;
    }

    ModernFormSettingsTab.BooleanValue getHeaderToggle() {
        return headerToggle;
    }

    String getHeaderToggleTooltip() {
        return headerToggleTooltip;
    }

    Boolean getHeaderDefaultValue() {
        return headerDefaultValue;
    }

    String getSaveLabel() {
        return saveLabel;
    }

    String getRevertLabel() {
        return revertLabel;
    }

    String getDefaultsLabel() {
        return defaultsLabel;
    }

    boolean isFooterVisible() {
        return footerVisible;
    }

    private ModernFormBuilder<S> append(ItemBuilder item) {
        ensureSection();
        currentSection.add(item.build());
        lastItem = item;
        replaceLastItem();
        return this;
    }

    private void ensureSection() {
        if (currentSection == null) {
            section("gui.modern.form.section.general", "");
        }
    }

    private void replaceLastItem() {
        if (currentSection == null || sections.isEmpty()) {
            return;
        }
        sections.set(sections.size() - 1, currentSection.build());
    }

    private ItemBuilder item(ModernFormDefinition.ItemType type, String title, String tooltip, Object value,
            String placeholder, String buttonLabel, String actionMessage, int maxLength, int intMin, int intMax,
            float floatMin, float floatMax, ModernFormSettingsTab.ActionStyle actionStyle,
            List<ModernFormSettingsTab.ChoiceOption<?>> options, ModernFormSettingsTab.FormAction<?> action) {
        return new ItemBuilder(type, title, tooltip, value, placeholder, buttonLabel, actionMessage, maxLength, intMin,
                intMax, floatMin, floatMax, actionStyle, options, action);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String mergeTooltip(String detail, String tooltip) {
        String first = safe(detail).trim();
        String second = safe(tooltip).trim();
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first + "\n" + second;
    }

    private static final class ItemBuilder {
        private final ModernFormDefinition.ItemType type;
        private final String title;
        private final String tooltip;
        private final Object value;
        private final String placeholder;
        private final String buttonLabel;
        private final String actionMessage;
        private final int maxLength;
        private final int intMin;
        private final int intMax;
        private final float floatMin;
        private final float floatMax;
        private final ModernFormSettingsTab.ActionStyle actionStyle;
        private final List<ModernFormSettingsTab.ChoiceOption<?>> options;
        private final ModernFormSettingsTab.FormAction<?> action;
        private ModernFormSettingsTab.Condition visibleWhen;
        private ModernFormSettingsTab.Condition enabledWhen;
        private Object defaultValue;
        private boolean hasDefaultValue;
        private boolean requiresConfirmation;

        private ItemBuilder(ModernFormDefinition.ItemType type, String title, String tooltip, Object value,
                String placeholder, String buttonLabel, String actionMessage, int maxLength, int intMin, int intMax,
                float floatMin, float floatMax, ModernFormSettingsTab.ActionStyle actionStyle,
                List<ModernFormSettingsTab.ChoiceOption<?>> options, ModernFormSettingsTab.FormAction<?> action) {
            this.type = type;
            this.title = title;
            this.tooltip = tooltip;
            this.value = value;
            this.placeholder = placeholder;
            this.buttonLabel = buttonLabel;
            this.actionMessage = actionMessage;
            this.maxLength = maxLength;
            this.intMin = intMin;
            this.intMax = intMax;
            this.floatMin = floatMin;
            this.floatMax = floatMax;
            this.actionStyle = actionStyle;
            this.options = options;
            this.action = action;
        }

        private ModernFormDefinition.Item build() {
            return new ModernFormDefinition.Item(type, title, tooltip, value, placeholder, buttonLabel, actionMessage,
                    maxLength, intMin, intMax, floatMin, floatMax, actionStyle, options, action, visibleWhen,
                    enabledWhen, defaultValue, hasDefaultValue, requiresConfirmation);
        }
    }
}
