package com.zszl.zszlScriptMod.path;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ActionVariableRegistry {

    public static final class VariableSource {
        private final String variableName;
        private final String category;
        private final String subCategory;
        private final String sequenceName;
        private final int stepIndex;
        private final int actionIndex;
        private final String actionType;
        private final String paramKey;
        private final boolean customSequence;

        private VariableSource(String variableName, String category, String subCategory, String sequenceName,
                int stepIndex, int actionIndex, String actionType, String paramKey, boolean customSequence) {
            this.variableName = variableName == null ? "" : variableName;
            this.category = category == null ? "" : category;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.sequenceName = sequenceName == null ? "" : sequenceName;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.actionType = actionType == null ? "" : actionType;
            this.paramKey = paramKey == null ? "" : paramKey;
            this.customSequence = customSequence;
        }

        public String getVariableName() {
            return variableName;
        }

        public String getCategory() {
            return category;
        }

        public String getSubCategory() {
            return subCategory;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        public int getActionIndex() {
            return actionIndex;
        }

        public String getActionType() {
            return actionType;
        }

        public String getParamKey() {
            return paramKey;
        }

        public boolean isCustomSequence() {
            return customSequence;
        }
    }

    public static final class VariableEntry {
        private final String variableName;
        private final List<VariableSource> sources = new ArrayList<>();

        private VariableEntry(String variableName) {
            this.variableName = variableName == null ? "" : variableName;
        }

        public String getVariableName() {
            return variableName;
        }

        public List<VariableSource> getSources() {
            return new ArrayList<>(sources);
        }

        public String getScopeKey() {
            return extractScopeKey(variableName);
        }

        public String getBaseVariableName() {
            return extractBaseName(variableName);
        }

        public int getCustomSourceCount() {
            int count = 0;
            for (VariableSource source : sources) {
                if (source != null && source.isCustomSequence()) {
                    count++;
                }
            }
            return count;
        }
    }

    private ActionVariableRegistry() {
    }

    public static List<VariableEntry> collectVariables(List<PathSequenceManager.PathSequence> sequences) {
        Map<String, VariableEntry> entries = new LinkedHashMap<>();
        if (sequences == null) {
            return new ArrayList<>();
        }
        for (PathSequenceManager.PathSequence sequence : sequences) {
            if (sequence == null || sequence.getSteps() == null) {
                continue;
            }
            for (int stepIndex = 0; stepIndex < sequence.getSteps().size(); stepIndex++) {
                PathSequenceManager.PathStep step = sequence.getSteps().get(stepIndex);
                if (step == null || step.getActions() == null) {
                    continue;
                }
                for (int actionIndex = 0; actionIndex < step.getActions().size(); actionIndex++) {
                    PathSequenceManager.ActionData action = step.getActions().get(actionIndex);
                    String paramKey = resolveVariableParamKey(action);
                    if (paramKey == null) {
                        continue;
                    }
                    JsonObject params = action.params;
                    if (params == null || !params.has(paramKey)) {
                        continue;
                    }
                    String variableName = jsonString(params.get(paramKey)).trim();
                    if (variableName.isEmpty()) {
                        continue;
                    }
                    VariableEntry entry = entries.computeIfAbsent(variableName, VariableEntry::new);
                    entry.sources.add(new VariableSource(variableName,
                            sequence.getCategory(),
                            sequence.getSubCategory(),
                            sequence.getName(),
                            stepIndex,
                            actionIndex,
                            action.type,
                            paramKey,
                            sequence.isCustom()));
                }
            }
        }
        return new ArrayList<>(entries.values());
    }

    public static int renameVariable(List<PathSequenceManager.PathSequence> sequences, String oldName, String newName) {
        String from = safe(oldName).trim();
        String to = safe(newName).trim();
        if (from.isEmpty() || to.isEmpty() || from.equals(to) || sequences == null) {
            return 0;
        }
        int updated = 0;
        for (PathSequenceManager.PathSequence sequence : sequences) {
            if (sequence == null || !sequence.isCustom() || sequence.getSteps() == null) {
                continue;
            }
            for (PathSequenceManager.PathStep step : sequence.getSteps()) {
                if (step == null || step.getActions() == null) {
                    continue;
                }
                for (PathSequenceManager.ActionData action : step.getActions()) {
                    String paramKey = resolveVariableParamKey(action);
                    if (paramKey == null || action.params == null || !action.params.has(paramKey)) {
                        continue;
                    }
                    String current = jsonString(action.params.get(paramKey)).trim();
                    if (!from.equals(current)) {
                        continue;
                    }
                    action.params.addProperty(paramKey, to);
                    updated++;
                }
            }
        }
        return updated;
    }

    public static String normalizeScopeKey(String rawScope) {
        String normalized = safe(rawScope).trim().toLowerCase(Locale.ROOT);
        if ("global".equals(normalized)) {
            return "global";
        }
        if ("local".equals(normalized)) {
            return "local";
        }
        if ("temp".equals(normalized) || "tmp".equals(normalized)) {
            return "temp";
        }
        if ("sequence".equals(normalized) || "seq".equals(normalized)) {
            return "sequence";
        }
        return "sequence";
    }

    public static String extractScopeKey(String variableName) {
        String text = safe(variableName).trim();
        if (text.isEmpty()) {
            return "sequence";
        }
        int colonIndex = text.indexOf(':');
        if (colonIndex > 0) {
            String scope = normalizeScopeKey(text.substring(0, colonIndex));
            if (scope != null) {
                return scope;
            }
        }
        int dotIndex = text.indexOf('.');
        if (dotIndex > 0) {
            String maybeScope = text.substring(0, dotIndex).trim().toLowerCase(Locale.ROOT);
            if ("global".equals(maybeScope) || "sequence".equals(maybeScope) || "seq".equals(maybeScope)
                    || "local".equals(maybeScope) || "temp".equals(maybeScope) || "tmp".equals(maybeScope)) {
                return normalizeScopeKey(maybeScope);
            }
        }
        return "sequence";
    }

    public static String extractBaseName(String variableName) {
        String text = safe(variableName).trim();
        if (text.isEmpty()) {
            return "";
        }
        int colonIndex = text.indexOf(':');
        if (colonIndex > 0) {
            String scope = text.substring(0, colonIndex).trim();
            if (isExplicitScopeToken(scope)) {
                return safe(text.substring(colonIndex + 1)).trim();
            }
        }
        int dotIndex = text.indexOf('.');
        if (dotIndex > 0) {
            String scope = text.substring(0, dotIndex).trim();
            if (isExplicitScopeToken(scope)) {
                return safe(text.substring(dotIndex + 1)).trim();
            }
        }
        return text;
    }

    public static String buildScopedVariableName(String scopeKey, String baseName) {
        String normalizedBase = safe(baseName).trim();
        if (normalizedBase.isEmpty()) {
            return "";
        }
        String normalizedScope = normalizeScopeKey(scopeKey);
        if ("sequence".equals(normalizedScope)) {
            return normalizedBase;
        }
        return normalizedScope + "." + normalizedBase;
    }

    public static String buildCanonicalVariableName(String scopeKey, String baseName) {
        String normalizedBase = safe(baseName).trim();
        if (normalizedBase.isEmpty()) {
            return "";
        }
        return normalizeScopeKey(scopeKey) + "." + normalizedBase;
    }

    public static String scopeKeyToDisplay(String scopeKey) {
        String normalized = normalizeScopeKey(scopeKey);
        if ("global".equals(normalized)) {
            return "全局变量(global)";
        }
        if ("local".equals(normalized)) {
            return "局部变量(local)";
        }
        if ("temp".equals(normalized)) {
            return "临时变量(temp)";
        }
        return "序列变量(sequence)";
    }

    public static ScopedRuntimeVariables.Scope scopeKeyToRuntimeScope(String scopeKey) {
        String normalized = normalizeScopeKey(scopeKey);
        if ("global".equals(normalized)) {
            return ScopedRuntimeVariables.Scope.GLOBAL;
        }
        if ("local".equals(normalized)) {
            return ScopedRuntimeVariables.Scope.LOCAL;
        }
        if ("temp".equals(normalized)) {
            return ScopedRuntimeVariables.Scope.TEMP;
        }
        return ScopedRuntimeVariables.Scope.SEQUENCE;
    }

    public static boolean isCaptureVariable(VariableEntry entry) {
        if (entry == null) {
            return false;
        }
        for (VariableSource source : entry.sources) {
            if (source != null && source.getActionType() != null
                    && source.getActionType().trim().toLowerCase(Locale.ROOT).startsWith("capture_")) {
                return true;
            }
        }
        return false;
    }

    public static int clearVariables(List<PathSequenceManager.PathSequence> sequences, List<String> variableNames) {
        if (sequences == null || variableNames == null || variableNames.isEmpty()) {
            return 0;
        }
        List<String> normalizedTargets = new ArrayList<>();
        for (String variableName : variableNames) {
            String normalized = safe(variableName).trim();
            if (!normalized.isEmpty()) {
                normalizedTargets.add(normalized);
            }
        }
        if (normalizedTargets.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (PathSequenceManager.PathSequence sequence : sequences) {
            if (sequence == null || !sequence.isCustom() || sequence.getSteps() == null) {
                continue;
            }
            for (PathSequenceManager.PathStep step : sequence.getSteps()) {
                if (step == null || step.getActions() == null) {
                    continue;
                }
                for (PathSequenceManager.ActionData action : step.getActions()) {
                    String paramKey = resolveVariableParamKey(action);
                    if (paramKey == null || action.params == null || !action.params.has(paramKey)) {
                        continue;
                    }
                    String current = jsonString(action.params.get(paramKey)).trim();
                    if (!normalizedTargets.contains(current)) {
                        continue;
                    }
                    action.params.addProperty(paramKey, "");
                    updated++;
                }
            }
        }
        return updated;
    }

    public static List<String> collectProducedVariableNames(VariableSource source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<String> names = collectProducedVariableNames(source.getVariableName(), source.getActionType());
        if ("capture_inventory_slot".equalsIgnoreCase(source.getActionType())) {
            String prefix = buildCanonicalVariableName(extractScopeKey(source.getVariableName()),
                    extractBaseName(source.getVariableName()));
            addSelectedInventorySlotVariables(names, prefix, source);
        }
        return names;
    }

    public static List<String> collectProducedVariableNames(String variableName, String actionType) {
        String canonicalPrefix = buildCanonicalVariableName(extractScopeKey(variableName), extractBaseName(variableName));
        if (canonicalPrefix.isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedType = safe(actionType).trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        switch (normalizedType) {
            case "set_var":
            case "capture_gui_title":
                names.add(canonicalPrefix);
                break;
            case "capture_nearby_entity":
                addSuffixes(names, canonicalPrefix,
                        "_found", "_name", "_id", "_x", "_y", "_z",
                        "_block_x", "_block_y", "_block_z");
                break;
            case "capture_block_at":
                addSuffixes(names, canonicalPrefix,
                        "_found", "_name", "_registry", "_x", "_y", "_z");
                break;
            case "capture_inventory_slot":
                names.add(canonicalPrefix);
                addCapturedStackVariables(names, canonicalPrefix);
                addSuffixes(names, canonicalPrefix, "_slot_index", "_slot_area", "_slot_count", "_filled_count",
                        "_slot_indices", "_slots", "_names", "_counts", "_registries", "_nbts");
                break;
            case "capture_hotbar":
                names.add(canonicalPrefix);
                for (int i = 0; i < 9; i++) {
                    addCapturedStackVariables(names, canonicalPrefix + "_" + i);
                }
                addSuffixes(names, canonicalPrefix, "_found", "_count", "_selected_index", "_selected_slot",
                        "_filled_count", "_slot_indices", "_slots", "_names", "_counts", "_registries", "_nbts",
                        "_selected_data");
                addCapturedStackVariables(names, canonicalPrefix + "_selected");
                break;
            case "capture_entity_list":
                addSuffixes(names, canonicalPrefix,
                        "_found", "_count", "_list", "_names", "_ids", "_types",
                        "_categories", "_distances", "_radius", "_max_count",
                        "_player_count", "_hostile_count", "_passive_count",
                        "_nearest_name", "_nearest_id", "_nearest_type",
                        "_nearest_category", "_nearest_distance",
                        "_nearest_player_name", "_nearest_player_distance",
                        "_nearest_hostile_name", "_nearest_hostile_distance");
                break;
            case "capture_packet_field":
                names.add(canonicalPrefix);
                addSuffixes(names, canonicalPrefix,
                        "_found", "_lookup_mode", "_field_key", "_value", "_value_text",
                        "_rule_name", "_variable_name", "_scope", "_channel",
                        "_direction", "_source", "_packet_class", "_raw",
                        "_timestamp", "_snapshot");
                break;
            case "capture_scoreboard":
                addSuffixes(names, canonicalPrefix,
                        "_found", "_title", "_lines", "_line_count", "_joined",
                        "_selected_index", "_selected_found", "_selected_line");
                break;
            case "capture_screen_region":
                addSuffixes(names, canonicalPrefix,
                        "_found", "_x", "_y", "_width", "_height",
                        "_avg_r", "_avg_g", "_avg_b", "_avg_hex",
                        "_center_r", "_center_g", "_center_b", "_center_hex",
                        "_brightness", "_edge_density");
                break;
            case "capture_gui_element":
                addSuffixes(names, canonicalPrefix,
                        "_found", "_screen", "_screen_class", "_title",
                        "_type", "_path", "_text", "_x", "_y",
                        "_width", "_height", "_slot", "_button_id");
                break;
            case "retry_block":
                addSuffixes(names, canonicalPrefix,
                        "", "_success", "_exhausted", "_remaining");
                break;
            default:
                names.add(canonicalPrefix);
                break;
        }
        return new ArrayList<>(names);
    }

    public static String resolveVariableParamKey(PathSequenceManager.ActionData action) {
        if (action == null || action.type == null) {
            return null;
        }
        String type = action.type.trim().toLowerCase(Locale.ROOT);
        if ("set_var".equals(type)) {
            return "name";
        }
        if ("capture_nearby_entity".equals(type)
                || "capture_gui_title".equals(type)
                || "capture_inventory_slot".equals(type)
                || "capture_hotbar".equals(type)
                || "capture_entity_list".equals(type)
                || "capture_packet_field".equals(type)
                || "capture_gui_element".equals(type)
                || "capture_scoreboard".equals(type)
                || "capture_screen_region".equals(type)
                || "capture_block_at".equals(type)) {
            return "varName";
        }
        if ("retry_block".equals(type)) {
            return "attemptVar";
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String jsonString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return "";
        }
        try {
            return safe(element.getAsString());
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static void addCapturedStackVariables(Collection<String> names, String prefix) {
        addSuffixes(names, prefix, "_found", "_name", "_count", "_registry", "_damage", "_has_nbt", "_nbt");
    }

    private static void addSelectedInventorySlotVariables(Collection<String> names, String prefix, VariableSource source) {
        if (names == null || prefix == null || prefix.trim().isEmpty() || source == null) {
            return;
        }
        PathSequenceManager.PathSequence sequence = PathSequenceManager.getSequence(source.getSequenceName());
        if (sequence == null || source.getStepIndex() < 0 || source.getStepIndex() >= sequence.getSteps().size()) {
            return;
        }
        PathSequenceManager.PathStep step = sequence.getSteps().get(source.getStepIndex());
        if (step == null || source.getActionIndex() < 0 || source.getActionIndex() >= step.getActions().size()) {
            return;
        }
        PathSequenceManager.ActionData action = step.getActions().get(source.getActionIndex());
        if (action == null || action.params == null || !action.params.has("slotIndices")
                || !action.params.get("slotIndices").isJsonArray()) {
            return;
        }
        for (com.google.gson.JsonElement element : action.params.getAsJsonArray("slotIndices")) {
            try {
                int slotIndex = element.getAsInt();
                if (slotIndex >= 0 && slotIndex < 36) {
                    addCapturedStackVariables(names, prefix + "_" + slotIndex);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void addSuffixes(Collection<String> names, String prefix, String... suffixes) {
        if (names == null || suffixes == null || prefix == null || prefix.trim().isEmpty()) {
            return;
        }
        for (String suffix : suffixes) {
            names.add(prefix + safe(suffix));
        }
    }

    private static boolean isExplicitScopeToken(String token) {
        String normalized = safe(token).trim().toLowerCase(Locale.ROOT);
        return "global".equals(normalized)
                || "sequence".equals(normalized)
                || "seq".equals(normalized)
                || "local".equals(normalized)
                || "temp".equals(normalized)
                || "tmp".equals(normalized);
    }
}
