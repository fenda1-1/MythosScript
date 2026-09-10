package com.zszl.zszlScriptMod.path.runtime;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ScopedRuntimeVariables extends AbstractMap<String, Object> {

    public enum Scope {
        GLOBAL,
        SEQUENCE,
        LOCAL,
        TEMP
    }

    public static final class ScopeSnapshot {
        private final Map<String, Object> sequenceValues;
        private final Map<String, Object> localValues;
        private final Map<String, Object> tempValues;

        public ScopeSnapshot(Map<String, Object> sequenceValues, Map<String, Object> localValues,
                Map<String, Object> tempValues) {
            this.sequenceValues = sequenceValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sequenceValues);
            this.localValues = localValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(localValues);
            this.tempValues = tempValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tempValues);
        }

        public Map<String, Object> getSequenceValues() {
            return new LinkedHashMap<>(sequenceValues);
        }

        public Map<String, Object> getLocalValues() {
            return new LinkedHashMap<>(localValues);
        }

        public Map<String, Object> getTempValues() {
            return new LinkedHashMap<>(tempValues);
        }
    }

    private static final Object GLOBAL_LOCK = new Object();
    private static final Map<String, Object> GLOBAL_SCOPE = new LinkedHashMap<>();

    private final Map<String, Object> sequenceScope = new LinkedHashMap<>();
    private final Map<String, Object> localScope = new LinkedHashMap<>();
    private final Map<String, Object> tempScope = new LinkedHashMap<>();

    private int currentStepIndex = Integer.MIN_VALUE;
    private String currentActionKey = "";

    public static void clearGlobalScope() {
        synchronized (GLOBAL_LOCK) {
            GLOBAL_SCOPE.clear();
        }
    }

    public static void setGlobalValue(String key, Object value) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isEmpty()) {
            return;
        }
        synchronized (GLOBAL_LOCK) {
            GLOBAL_SCOPE.put(normalizedKey, value);
        }
    }

    public static Object getGlobalValue(String key) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isEmpty()) {
            return null;
        }
        synchronized (GLOBAL_LOCK) {
            return GLOBAL_SCOPE.get(normalizedKey);
        }
    }

    public static Map<String, Object> getGlobalScopeSnapshot() {
        synchronized (GLOBAL_LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(GLOBAL_SCOPE));
        }
    }

    public void enterStep(int stepIndex) {
        if (this.currentStepIndex != stepIndex) {
            this.currentStepIndex = stepIndex;
            this.currentActionKey = "";
            this.localScope.clear();
            this.tempScope.clear();
        }
    }

    public void beginAction(int stepIndex, int actionIndex) {
        enterStep(stepIndex);
        String actionKey = stepIndex + ":" + actionIndex;
        if (!actionKey.equals(this.currentActionKey)) {
            this.currentActionKey = actionKey;
            this.tempScope.clear();
        }
    }

    public void clearNonGlobal() {
        this.sequenceScope.clear();
        this.localScope.clear();
        this.tempScope.clear();
        this.currentStepIndex = Integer.MIN_VALUE;
        this.currentActionKey = "";
    }

    public ScopeSnapshot captureSnapshot() {
        return new ScopeSnapshot(this.sequenceScope, this.localScope, this.tempScope);
    }

    public void restoreSnapshot(ScopeSnapshot snapshot) {
        this.sequenceScope.clear();
        this.localScope.clear();
        this.tempScope.clear();
        if (snapshot == null) {
            return;
        }
        this.sequenceScope.putAll(snapshot.getSequenceValues());
        this.localScope.putAll(snapshot.getLocalValues());
        this.tempScope.putAll(snapshot.getTempValues());
    }

    public Map<String, Object> getScopeView(String scopeName) {
        Scope scope = parseScopeName(scopeName);
        if (scope == null) {
            return Collections.emptyMap();
        }
        switch (scope) {
            case GLOBAL:
                synchronized (GLOBAL_LOCK) {
                    return Collections.unmodifiableMap(new LinkedHashMap<>(GLOBAL_SCOPE));
                }
            case SEQUENCE:
                return Collections.unmodifiableMap(new LinkedHashMap<>(sequenceScope));
            case LOCAL:
                return Collections.unmodifiableMap(new LinkedHashMap<>(localScope));
            case TEMP:
                return Collections.unmodifiableMap(new LinkedHashMap<>(tempScope));
            default:
                return Collections.emptyMap();
        }
    }

    public Map<String, Object> getCanonicalSnapshot() {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        synchronized (GLOBAL_LOCK) {
            appendCanonicalEntries(snapshot, Scope.GLOBAL, GLOBAL_SCOPE);
        }
        appendCanonicalEntries(snapshot, Scope.SEQUENCE, sequenceScope);
        appendCanonicalEntries(snapshot, Scope.LOCAL, localScope);
        appendCanonicalEntries(snapshot, Scope.TEMP, tempScope);
        return Collections.unmodifiableMap(snapshot);
    }

    public boolean containsInDeclaredScope(String variableName) {
        ScopedKey scopedKey = parseScopedKey(variableName);
        String normalizedKey = normalizeVariableKey(scopedKey.key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        switch (scopedKey.scope) {
            case GLOBAL:
                synchronized (GLOBAL_LOCK) {
                    return GLOBAL_SCOPE.containsKey(normalizedKey);
                }
            case LOCAL:
                return localScope.containsKey(normalizedKey);
            case TEMP:
                return tempScope.containsKey(normalizedKey);
            case SEQUENCE:
            default:
                return sequenceScope.containsKey(normalizedKey);
        }
    }

    public Object putGlobal(String key, Object value) {
        return putInScope(Scope.GLOBAL, key, value);
    }

    public Object putSequence(String key, Object value) {
        return putInScope(Scope.SEQUENCE, key, value);
    }

    public Object putLocal(String key, Object value) {
        return putInScope(Scope.LOCAL, key, value);
    }

    public Object putTemp(String key, Object value) {
        return putInScope(Scope.TEMP, key, value);
    }

    public Object putInScope(Scope scope, String key, Object value) {
        String normalizedKey = normalizeVariableKey(key);
        if (normalizedKey.isEmpty()) {
            return null;
        }
        Scope target = scope == null ? Scope.SEQUENCE : scope;
        switch (target) {
            case GLOBAL:
                synchronized (GLOBAL_LOCK) {
                    return GLOBAL_SCOPE.put(normalizedKey, value);
                }
            case LOCAL:
                return localScope.put(normalizedKey, value);
            case TEMP:
                return tempScope.put(normalizedKey, value);
            case SEQUENCE:
            default:
                return sequenceScope.put(normalizedKey, value);
        }
    }

    @Override
    public Object put(String key, Object value) {
        ScopedKey scopedKey = parseScopedKey(key);
        return putInScope(scopedKey.scope, scopedKey.key, value);
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String)) {
            return null;
        }
        String lookupKey = ((String) key).trim();
        if (lookupKey.isEmpty()) {
            return null;
        }

        Scope directScope = parseScopeName(lookupKey);
        if (directScope != null) {
            return getScopeView(lookupKey);
        }

        String normalizedKey = normalizeVariableKey(lookupKey);
        if (normalizedKey.isEmpty()) {
            return null;
        }

        if (tempScope.containsKey(normalizedKey)) {
            return tempScope.get(normalizedKey);
        }
        if (localScope.containsKey(normalizedKey)) {
            return localScope.get(normalizedKey);
        }
        if (sequenceScope.containsKey(normalizedKey)) {
            return sequenceScope.get(normalizedKey);
        }
        synchronized (GLOBAL_LOCK) {
            return GLOBAL_SCOPE.get(normalizedKey);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String)) {
            return false;
        }
        String lookupKey = ((String) key).trim();
        if (lookupKey.isEmpty()) {
            return false;
        }

        if (parseScopeName(lookupKey) != null) {
            return true;
        }

        String normalizedKey = normalizeVariableKey(lookupKey);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        if (tempScope.containsKey(normalizedKey) || localScope.containsKey(normalizedKey)
                || sequenceScope.containsKey(normalizedKey)) {
            return true;
        }
        synchronized (GLOBAL_LOCK) {
            return GLOBAL_SCOPE.containsKey(normalizedKey);
        }
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String)) {
            return null;
        }
        String lookupKey = ((String) key).trim();
        if (lookupKey.isEmpty()) {
            return null;
        }

        ScopedKey scopedKey = parseScopedKey(lookupKey);
        String normalizedKey = normalizeVariableKey(scopedKey.key);
        if (normalizedKey.isEmpty()) {
            return null;
        }

        if (scopedKey.explicitScope) {
            switch (scopedKey.scope) {
                case GLOBAL:
                    synchronized (GLOBAL_LOCK) {
                        return GLOBAL_SCOPE.remove(normalizedKey);
                    }
                case LOCAL:
                    return localScope.remove(normalizedKey);
                case TEMP:
                    return tempScope.remove(normalizedKey);
                case SEQUENCE:
                default:
                    return sequenceScope.remove(normalizedKey);
            }
        }

        Object removed = tempScope.remove(normalizedKey);
        if (removed != null) {
            return removed;
        }
        removed = localScope.remove(normalizedKey);
        if (removed != null) {
            return removed;
        }
        removed = sequenceScope.remove(normalizedKey);
        if (removed != null) {
            return removed;
        }
        synchronized (GLOBAL_LOCK) {
            return GLOBAL_SCOPE.remove(normalizedKey);
        }
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (m == null) {
            return;
        }
        for (Map.Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        clearNonGlobal();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return buildMergedView().entrySet();
    }

    @Override
    public int size() {
        return buildMergedView().size();
    }

    @Override
    public boolean isEmpty() {
        return tempScope.isEmpty() && localScope.isEmpty() && sequenceScope.isEmpty() && globalSize() == 0;
    }

    @Override
    public boolean containsValue(Object value) {
        return buildMergedView().containsValue(value);
    }

    private int globalSize() {
        synchronized (GLOBAL_LOCK) {
            return GLOBAL_SCOPE.size();
        }
    }

    private void appendCanonicalEntries(Map<String, Object> target, Scope scope, Map<String, Object> values) {
        if (target == null || scope == null || values == null || values.isEmpty()) {
            return;
        }
        String prefix = scope.name().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                continue;
            }
            target.put(prefix + "." + entry.getKey().trim(), entry.getValue());
        }
    }

    private Map<String, Object> buildMergedView() {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        synchronized (GLOBAL_LOCK) {
            merged.putAll(GLOBAL_SCOPE);
        }
        merged.putAll(sequenceScope);
        merged.putAll(localScope);
        merged.putAll(tempScope);
        return merged;
    }

    private Scope parseScopeName(String scopeName) {
        if (scopeName == null) {
            return null;
        }
        String normalized = scopeName.trim().toLowerCase();
        if ("global".equals(normalized)) {
            return Scope.GLOBAL;
        }
        if ("sequence".equals(normalized) || "seq".equals(normalized)) {
            return Scope.SEQUENCE;
        }
        if ("local".equals(normalized)) {
            return Scope.LOCAL;
        }
        if ("temp".equals(normalized) || "tmp".equals(normalized)) {
            return Scope.TEMP;
        }
        return null;
    }

    private ScopedKey parseScopedKey(String rawKey) {
        String safeKey = rawKey == null ? "" : rawKey.trim();
        int colonIndex = safeKey.indexOf(':');
        if (colonIndex > 0) {
            Scope scope = parseScopeName(safeKey.substring(0, colonIndex));
            if (scope != null) {
                return new ScopedKey(scope, safeKey.substring(colonIndex + 1), true);
            }
        }

        int dotIndex = safeKey.indexOf('.');
        if (dotIndex > 0) {
            Scope scope = parseScopeName(safeKey.substring(0, dotIndex));
            if (scope != null) {
                return new ScopedKey(scope, safeKey.substring(dotIndex + 1), true);
            }
        }
        return new ScopedKey(Scope.SEQUENCE, safeKey, false);
    }

    private String normalizeVariableKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static final class ScopedKey {
        private final Scope scope;
        private final String key;
        private final boolean explicitScope;

        private ScopedKey(Scope scope, String key, boolean explicitScope) {
            this.scope = scope == null ? Scope.SEQUENCE : scope;
            this.key = key == null ? "" : key;
            this.explicitScope = explicitScope;
        }
    }
}

