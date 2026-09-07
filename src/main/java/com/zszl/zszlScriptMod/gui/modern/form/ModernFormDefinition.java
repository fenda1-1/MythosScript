package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;

/** Immutable form definition shared by the form session, renderer, and input controller. */
public final class ModernFormDefinition<S> {

    enum ItemType {
        TOGGLE,
        TEXT,
        INTEGER,
        DECIMAL,
        CHOICE,
        READ_ONLY,
        ACTION
    }

    static final class Section {
        private final String title;
        private final String tooltip;
        private final List<Item> items;

        Section(String title, String tooltip, List<Item> items) {
            this.title = safe(title);
            this.tooltip = safe(tooltip);
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }

        String getTitle() {
            return title;
        }

        String getTooltip() {
            return tooltip;
        }

        List<Item> getItems() {
            return items;
        }
    }

    static final class SectionBuilder {
        private final String title;
        private final String tooltip;
        private final List<Item> items = new ArrayList<>();

        SectionBuilder(String title, String tooltip) {
            this.title = title;
            this.tooltip = tooltip;
        }

        void add(Item item) {
            items.add(item);
        }

        Section build() {
            return new Section(title, tooltip, items);
        }
    }

    static final class Item {
        private final ItemType type;
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
        private final ModernFormSettingsTab.Condition visibleWhen;
        private final ModernFormSettingsTab.Condition enabledWhen;
        private final Object defaultValue;
        private final boolean hasDefaultValue;
        private final boolean requiresConfirmation;

        Item(ItemType type, String title, String tooltip, Object value, String placeholder, String buttonLabel,
                String actionMessage, int maxLength, int intMin, int intMax, float floatMin, float floatMax,
                ModernFormSettingsTab.ActionStyle actionStyle,
                List<ModernFormSettingsTab.ChoiceOption<?>> options, ModernFormSettingsTab.FormAction<?> action,
                ModernFormSettingsTab.Condition visibleWhen, ModernFormSettingsTab.Condition enabledWhen,
                Object defaultValue, boolean hasDefaultValue, boolean requiresConfirmation) {
            this.type = type;
            this.title = safe(title);
            this.tooltip = safe(tooltip);
            this.value = value;
            this.placeholder = safe(placeholder);
            this.buttonLabel = safe(buttonLabel);
            this.actionMessage = safe(actionMessage);
            this.maxLength = Math.max(1, maxLength);
            this.intMin = intMin;
            this.intMax = Math.max(intMin, intMax);
            this.floatMin = floatMin;
            this.floatMax = Math.max(floatMin, floatMax);
            this.actionStyle = actionStyle == null ? ModernFormSettingsTab.ActionStyle.SECONDARY : actionStyle;
            this.options = options == null ? Collections.<ModernFormSettingsTab.ChoiceOption<?>>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(options));
            this.action = action;
            this.visibleWhen = visibleWhen == null ? ALWAYS : visibleWhen;
            this.enabledWhen = enabledWhen == null ? ALWAYS : enabledWhen;
            this.defaultValue = defaultValue;
            this.hasDefaultValue = hasDefaultValue;
            this.requiresConfirmation = requiresConfirmation;
        }

        ItemType getType() {
            return type;
        }

        String getTitle() {
            return title;
        }

        String getTooltip() {
            return tooltip;
        }

        Object getValue() {
            return value;
        }

        String getPlaceholder() {
            return placeholder;
        }

        String getButtonLabel() {
            return buttonLabel;
        }

        String getActionMessage() {
            return actionMessage;
        }

        int getMaxLength() {
            return maxLength;
        }

        int getIntMin() {
            return intMin;
        }

        int getIntMax() {
            return intMax;
        }

        float getFloatMin() {
            return floatMin;
        }

        float getFloatMax() {
            return floatMax;
        }

        ModernFormSettingsTab.ActionStyle getActionStyle() {
            return actionStyle;
        }

        List<ModernFormSettingsTab.ChoiceOption<?>> getOptions() {
            return options;
        }

        ModernFormSettingsTab.FormAction<?> getAction() {
            return action;
        }

        Object getDefaultValue() {
            return defaultValue;
        }

        boolean hasDefaultValue() {
            return hasDefaultValue;
        }

        boolean requiresConfirmation() {
            return requiresConfirmation;
        }

        boolean isVisible() {
            return visibleWhen.matches();
        }

        boolean isEnabled() {
            return enabledWhen.matches();
        }
    }

    private static final ModernFormSettingsTab.Condition ALWAYS = new ModernFormSettingsTab.Condition() {
        @Override
        public boolean matches() {
            return true;
        }
    };

    private final String title;
    private final String subtitle;
    private final String headerTooltip;
    private final ModernFormSettingsTab.StateAdapter<S> stateAdapter;
    private final List<Section> sections;
    private final ModernFormSettingsTab.BooleanValue headerToggle;
    private final String headerToggleTooltip;
    private final Boolean headerDefaultValue;
    private final String saveLabel;
    private final String revertLabel;
    private final String defaultsLabel;
    private final boolean footerVisible;

    ModernFormDefinition(ModernFormBuilder<S> builder) {
        title = builder.getTitle();
        subtitle = builder.getSubtitle();
        headerTooltip = builder.getHeaderTooltip();
        stateAdapter = builder.getStateAdapter();
        sections = Collections.unmodifiableList(new ArrayList<>(builder.getSections()));
        headerToggle = builder.getHeaderToggle();
        headerToggleTooltip = builder.getHeaderToggleTooltip();
        headerDefaultValue = builder.getHeaderDefaultValue();
        saveLabel = builder.getSaveLabel();
        revertLabel = builder.getRevertLabel();
        defaultsLabel = builder.getDefaultsLabel();
        footerVisible = builder.isFooterVisible();
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

    List<Section> getSections() {
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
