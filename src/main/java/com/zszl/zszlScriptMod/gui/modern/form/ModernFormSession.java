package com.zszl.zszlScriptMod.gui.modern.form;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;

/** Coordinates adapter state and binding snapshots without owning UI objects. */
final class ModernFormSession<S> {

    private final ModernFormDefinition<S> definition;
    private final ModernFormState<S> state;

    ModernFormSession(ModernFormDefinition<S> definition) {
        this.definition = definition;
        state = new ModernFormState<>(new ModernFormState.Adapter<S>() {
            @Override
            public void load() {
                definition.getStateAdapter().load();
            }

            @Override
            public S capture() {
                return definition.getStateAdapter().capture();
            }

            @Override
            public S copy(S value) {
                return definition.getStateAdapter().copy(value);
            }

            @Override
            public void restore(S value) {
                definition.getStateAdapter().restore(value);
            }

            @Override
            public void save() {
                definition.getStateAdapter().save();
            }

            @Override
            public void restoreDefaults() {
                if (definition.getStateAdapter().supportsDefaults()) {
                    definition.getStateAdapter().restoreDefaults();
                }
            }

            @Override
            public S createDefaults() {
                return definition.getStateAdapter().supportsDefaults()
                        ? definition.getStateAdapter().createDefaults() : null;
            }
        });
        addBindingValues();
    }

    void load() {
        state.load();
        syncLiveToggle();
        state.checkpoint();
    }

    void save() {
        syncLiveToggle();
        state.save();
    }

    boolean isDirty() {
        return state.isDirty();
    }

    void updateLiveToggle() {
        syncLiveToggle();
    }

    void revert() {
        state.revert();
        restoreLiveToggle();
    }

    void restoreDefaults() {
        if (!definition.getStateAdapter().supportsDefaults()) {
            return;
        }
        state.restoreDefaults();
        ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
        Boolean defaultValue = definition.getHeaderDefaultValue();
        if (toggle != null && defaultValue != null) {
            toggle.set(defaultValue.booleanValue());
        }
        syncLiveToggle();
    }

    private void addBindingValues() {
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (final ModernFormDefinition.Item item : section.getItems()) {
                if (item.getValue() == null || item.getType() == ModernFormDefinition.ItemType.ACTION
                        || item.getType() == ModernFormDefinition.ItemType.READ_ONLY) {
                    continue;
                }
                state.add(new ModernFormState.Value() {
                    @Override
                    public Object capture() {
                        return captureValue(item);
                    }

                    @Override
                    public void restore(Object value) {
                        restoreValue(item, value);
                    }

                    @Override
                    public void restoreDefault() {
                        if (item.hasDefaultValue()) {
                            restoreValue(item, item.getDefaultValue());
                        }
                    }
                });
            }
        }
    }

    private void syncLiveToggle() {
        ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
        state.setLiveToggle(toggle == null ? null : Boolean.valueOf(toggle.get()));
    }

    private void restoreLiveToggle() {
        ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
        Boolean value = state.getLiveToggle();
        if (toggle != null && value != null) {
            toggle.set(value.booleanValue());
        }
    }

    private static Object captureValue(ModernFormDefinition.Item item) {
        switch (item.getType()) {
        case CUSTOM:
            return ((ModernFormWidget) item.getValue()).snapshot();
        case TOGGLE:
            return Boolean.valueOf(((ModernFormSettingsTab.BooleanValue) item.getValue()).get());
        case TEXT:
            return ((ModernFormSettingsTab.TextValue) item.getValue()).get();
        case INTEGER:
            return Integer.valueOf(((ModernFormSettingsTab.IntValue) item.getValue()).get());
        case DECIMAL:
            return Float.valueOf(((ModernFormSettingsTab.FloatValue) item.getValue()).get());
        case CHOICE:
            return ((ModernFormSettingsTab.ChoiceValue<?>) item.getValue()).get();
        case READ_ONLY:
        case ACTION:
            return null;
        default:
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreValue(ModernFormDefinition.Item item, Object value) {
        switch (item.getType()) {
        case CUSTOM:
            return;
        case TOGGLE:
            if (value instanceof Boolean) {
                ((ModernFormSettingsTab.BooleanValue) item.getValue()).set(((Boolean) value).booleanValue());
            }
            return;
        case TEXT:
            ((ModernFormSettingsTab.TextValue) item.getValue()).set(value == null ? "" : String.valueOf(value));
            return;
        case INTEGER:
            if (value instanceof Number) {
                ((ModernFormSettingsTab.IntValue) item.getValue()).set(((Number) value).intValue());
            }
            return;
        case DECIMAL:
            if (value instanceof Number) {
                ((ModernFormSettingsTab.FloatValue) item.getValue()).set(((Number) value).floatValue());
            }
            return;
        case CHOICE:
            ((ModernFormSettingsTab.ChoiceValue<Object>) item.getValue()).set(value);
            return;
        case READ_ONLY:
        case ACTION:
            return;
        default:
            return;
        }
    }
}
