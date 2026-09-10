package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure draft state used by form integrations that do not need Minecraft UI objects. */
public final class ModernFormState<S> {

    public interface Adapter<S> {
        void load();
        S capture();
        S copy(S state);
        void restore(S state);
        void save();
        void restoreDefaults();
        S createDefaults();
    }

    private final Adapter<S> adapter;
    private final List<Value> values = new ArrayList<>();
    private Boolean liveToggle;
    private S savedState;
    private List<Object> savedValues;
    private Boolean savedToggle;

    public ModernFormState(Adapter<S> adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        this.adapter = adapter;
    }

    public void add(Value value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        values.add(value);
    }

    public void setLiveToggle(Boolean toggle) {
        liveToggle = toggle;
    }

    public Boolean getLiveToggle() {
        return liveToggle;
    }

    public void load() {
        adapter.load();
        captureSaved();
    }

    public void save() {
        adapter.save();
        captureSaved();
    }

    /** Records the current draft without invoking the persistence adapter. */
    public void checkpoint() {
        captureSaved();
    }

    public boolean isDirty() {
        // Adapter snapshots are intentionally opaque. Many adapters expose a
        // mutable state object or a freshly-created value without value
        // equality, so comparing S would report false positives (or miss the
        // null snapshot used by lightweight feature adapters). Bound values
        // and the live header toggle are the draft contract for this form.
        if (savedValues == null || savedValues.size() != values.size()) {
            return true;
        }
        for (int i = 0; i < values.size(); i++) {
            if (!Objects.equals(values.get(i).capture(), savedValues.get(i))) {
                return true;
            }
        }
        return !Objects.equals(liveToggle, savedToggle);
    }

    public void revert() {
        restoreSaved();
    }

    public void restoreDefaults() {
        adapter.restoreDefaults();
        S defaults = adapter.createDefaults();
        if (defaults != null) {
            adapter.restore(adapter.copy(defaults));
        }
        for (Value value : values) {
            value.restoreDefault();
        }
    }

    private void captureSaved() {
        savedState = adapter.copy(adapter.capture());
        savedValues = new ArrayList<>();
        for (Value value : values) {
            savedValues.add(value.capture());
        }
        savedToggle = liveToggle;
    }

    private void restoreSaved() {
        if (savedState != null) {
            adapter.restore(adapter.copy(savedState));
        }
        if (savedValues != null) {
            for (int i = 0; i < values.size() && i < savedValues.size(); i++) {
                values.get(i).restore(savedValues.get(i));
            }
        }
        liveToggle = savedToggle;
    }

    public interface Value {
        Object capture();
        void restore(Object value);
        void restoreDefault();
    }
}
