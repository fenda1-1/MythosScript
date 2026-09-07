package com.zszl.zszlScriptMod.path;

import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionParameterVariableResolver {
    private static final Pattern EXPLICIT_REFERENCE_PATTERN = Pattern
            .compile("(?i)^(global|sequence|seq|local|temp|tmp)(?:\\.|:).+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    public enum Status {
        NOT_REFERENCE,
        RESOLVED,
        DYNAMIC,
        MISSING
    }

    public static final class ReferenceInfo {
        private final String rawText;
        private final String normalizedReference;
        private final Status status;
        private final Object resolvedValue;

        private ReferenceInfo(String rawText, String normalizedReference, Status status, Object resolvedValue) {
            this.rawText = safe(rawText).trim();
            this.normalizedReference = safe(normalizedReference).trim();
            this.status = status == null ? Status.NOT_REFERENCE : status;
            this.resolvedValue = resolvedValue;
        }

        public String getRawText() {
            return rawText;
        }

        public String getNormalizedReference() {
            return normalizedReference;
        }

        public Status getStatus() {
            return status;
        }

        public Object getResolvedValue() {
            return resolvedValue;
        }

        public boolean isReference() {
            return status != Status.NOT_REFERENCE;
        }

        public boolean isResolved() {
            return status == Status.RESOLVED;
        }

        public boolean isDynamic() {
            return status == Status.DYNAMIC;
        }

        public boolean isMissing() {
            return status == Status.MISSING;
        }

        public String getPreviewText() {
            if (!isResolved()) {
                return "";
            }
            return LegacyActionRuntime.stringifyValue(resolvedValue);
        }
    }

    public static final class Context {
        private final String currentSequenceName;
        private final Map<String, Object> runtimeValues;
        private final Map<String, VariableState> variableStates;

        private Context(String currentSequenceName, Map<String, Object> runtimeValues,
                Map<String, VariableState> variableStates) {
            this.currentSequenceName = safe(currentSequenceName).trim();
            this.runtimeValues = runtimeValues == null ? Collections.<String, Object>emptyMap() : runtimeValues;
            this.variableStates = variableStates == null ? Collections.<String, VariableState>emptyMap() : variableStates;
        }

        public String getCurrentSequenceName() {
            return currentSequenceName;
        }
    }

    private static final class VariableState {
        private final String normalizedReference;
        private boolean defined;
        private boolean hasStaticValue;
        private Object staticValue;

        private VariableState(String normalizedReference) {
            this.normalizedReference = safe(normalizedReference).trim();
        }
    }

    private ActionParameterVariableResolver() {
    }

    public static Context buildContext(String currentSequenceName, Collection<PathSequence> sequences) {
        LinkedHashMap<String, Object> runtimeValues = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> globalScope = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> sequenceScope = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> localScope = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> tempScope = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, VariableState> variableStates = new LinkedHashMap<String, VariableState>();

        runtimeValues.put("global", globalScope);
        runtimeValues.put("sequence", sequenceScope);
        runtimeValues.put("seq", sequenceScope);
        runtimeValues.put("local", localScope);
        runtimeValues.put("temp", tempScope);
        runtimeValues.put("tmp", tempScope);

        if (sequences == null || sequences.isEmpty()) {
            return new Context(currentSequenceName, runtimeValues, variableStates);
        }

        PathSequence currentSequence = null;
        String normalizedCurrentSequenceName = safe(currentSequenceName).trim();
        for (PathSequence sequence : sequences) {
            if (sequence == null) {
                continue;
            }
            String sequenceName = safe(sequence.getName()).trim();
            if (!normalizedCurrentSequenceName.isEmpty()
                    && normalizedCurrentSequenceName.equalsIgnoreCase(sequenceName)) {
                currentSequence = sequence;
            }
            scanSequence(sequence, true, false, runtimeValues, globalScope, sequenceScope, localScope, tempScope,
                    variableStates);
        }

        if (currentSequence != null) {
            scanSequence(currentSequence, false, true, runtimeValues, globalScope, sequenceScope, localScope, tempScope,
                    variableStates);
        }

        return new Context(currentSequenceName, runtimeValues, variableStates);
    }

    public static ReferenceInfo inspect(Context context, String rawText) {
        String normalizedReference = normalizeReference(rawText);
        if (normalizedReference.isEmpty()) {
            return new ReferenceInfo(rawText, "", Status.NOT_REFERENCE, null);
        }

        Map<String, Object> runtimeValues = context == null
                ? Collections.<String, Object>emptyMap()
                : context.runtimeValues;
        Object resolvedValue = LegacyActionRuntime.getRuntimeValue(normalizedReference, runtimeValues, null, null, -1, -1);
        if (resolvedValue != null) {
            return new ReferenceInfo(rawText, normalizedReference, Status.RESOLVED, resolvedValue);
        }

        VariableState exactState = context == null ? null : context.variableStates.get(normalizedReference);
        if (exactState != null && exactState.defined) {
            if (exactState.hasStaticValue) {
                return new ReferenceInfo(rawText, normalizedReference, Status.RESOLVED, exactState.staticValue);
            }
            return new ReferenceInfo(rawText, normalizedReference, Status.DYNAMIC, null);
        }

        VariableState state = findVariableState(context, normalizedReference);
        if (state != null && state.defined) {
            if (state.hasStaticValue) {
                return new ReferenceInfo(rawText, normalizedReference, Status.MISSING, null);
            }
            return new ReferenceInfo(rawText, normalizedReference, Status.DYNAMIC, null);
        }
        return new ReferenceInfo(rawText, normalizedReference, Status.MISSING, null);
    }

    public static Double resolveStaticDouble(Context context, String rawText) {
        ReferenceInfo info = inspect(context, rawText);
        if (!info.isResolved()) {
            return null;
        }
        return toNumber(info.getResolvedValue());
    }

    public static Integer resolveStaticInt(Context context, String rawText) {
        Double number = resolveStaticDouble(context, rawText);
        return number == null ? null : Integer.valueOf((int) Math.round(number.doubleValue()));
    }

    public static boolean looksLikeVariableReference(String rawText) {
        return !normalizeReference(rawText).isEmpty();
    }

    public static String normalizeReference(String rawText) {
        String text = safe(rawText).trim();
        if (text.isEmpty()) {
            return "";
        }
        Matcher matcher = EXPLICIT_REFERENCE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return "";
        }

        int separatorIndex = text.indexOf('.');
        int colonIndex = text.indexOf(':');
        if (separatorIndex < 0 || (colonIndex >= 0 && colonIndex < separatorIndex)) {
            separatorIndex = colonIndex;
        }
        if (separatorIndex <= 0 || separatorIndex >= text.length() - 1) {
            return "";
        }

        String scopeToken = normalizeScopeToken(text.substring(0, separatorIndex));
        String tail = safe(text.substring(separatorIndex + 1)).trim();
        if (scopeToken.isEmpty() || tail.isEmpty()) {
            return "";
        }
        return scopeToken + "." + tail;
    }

    public static String buildFieldStatusSuffix(ReferenceInfo info) {
        if (info == null || !info.isReference()) {
            return "";
        }
        if (info.isResolved()) {
            String preview = trimPreview(info.getPreviewText(), 14);
            return preview.isEmpty() ? " §a(已解析为空)" : " §a= " + preview;
        }
        if (info.isDynamic()) {
            return " §e(运行时读取)";
        }
        if (info.isMissing()) {
            return " §c(变量未定义)";
        }
        return "";
    }

    public static String appendHelpWithReference(String baseHelpText, ReferenceInfo info) {
        String base = safe(baseHelpText).trim();
        if (info == null || !info.isReference()) {
            return base;
        }
        StringBuilder builder = new StringBuilder(base);
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append("变量引用: ").append(info.getNormalizedReference());
        if (info.isResolved()) {
            builder.append("\n当前解析预览: ").append(trimPreview(info.getPreviewText(), 80));
        } else if (info.isDynamic()) {
            builder.append("\n变量已定义，但当前无法静态预览；运行时会读取实时值。");
        } else if (info.isMissing()) {
            builder.append("\n未找到已定义变量；保存时会在检查报告里提示。");
        }
        return builder.toString();
    }

    public static Double toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? 1D : 0D;
        }
        String text = LegacyActionRuntime.stringifyValue(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void scanSequence(PathSequence sequence, boolean globalOnly, boolean includeNonGlobal,
            Map<String, Object> runtimeValues,
            Map<String, Object> globalScope,
            Map<String, Object> sequenceScope,
            Map<String, Object> localScope,
            Map<String, Object> tempScope,
            Map<String, VariableState> variableStates) {
        if (sequence == null || sequence.getSteps() == null) {
            return;
        }
        List<PathStep> steps = sequence.getSteps();
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            PathStep step = steps.get(stepIndex);
            if (step == null || step.getActions() == null) {
                continue;
            }
            List<ActionData> actions = step.getActions();
            for (int actionIndex = 0; actionIndex < actions.size(); actionIndex++) {
                ActionData action = actions.get(actionIndex);
                if (action == null || action.params == null) {
                    continue;
                }
                String variableParamKey = ActionVariableRegistry.resolveVariableParamKey(action);
                if (variableParamKey == null || !action.params.has(variableParamKey)
                        || !action.params.get(variableParamKey).isJsonPrimitive()) {
                    continue;
                }
                String variableName = safe(action.params.get(variableParamKey).getAsString()).trim();
                if (variableName.isEmpty()) {
                    continue;
                }
                String scopeKey = ActionVariableRegistry.extractScopeKey(variableName);
                boolean trackScope = "global".equals(scopeKey) ? globalOnly : includeNonGlobal;
                if (!trackScope) {
                    continue;
                }

                List<String> producedNames = ActionVariableRegistry.collectProducedVariableNames(variableName, action.type);
                for (String producedName : producedNames) {
                    registerDefinedVariable(variableStates, producedName);
                }

                if (!"set_var".equalsIgnoreCase(safe(action.type).trim())) {
                    continue;
                }

                String canonicalVariableName = ActionVariableRegistry
                        .buildCanonicalVariableName(scopeKey, ActionVariableRegistry.extractBaseName(variableName));
                if (canonicalVariableName.isEmpty()) {
                    continue;
                }

                Object resolvedValue = tryResolveAssignedValue(action.params, runtimeValues, sequence, stepIndex,
                        actionIndex);
                if (resolvedValue == null) {
                    registerDefinedVariable(variableStates, canonicalVariableName);
                    continue;
                }

                writeStaticValue(canonicalVariableName, resolvedValue, runtimeValues, globalScope, sequenceScope,
                        localScope, tempScope);
                registerResolvedVariable(variableStates, canonicalVariableName, resolvedValue);
            }
        }
    }

    private static Object tryResolveAssignedValue(com.google.gson.JsonObject params,
            Map<String, Object> runtimeValues,
            PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        try {
            return LegacyActionRuntime.resolveAssignedValue(params, runtimeValues, null, sequence, stepIndex, actionIndex);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeStaticValue(String canonicalVariableName, Object value,
            Map<String, Object> runtimeValues,
            Map<String, Object> globalScope,
            Map<String, Object> sequenceScope,
            Map<String, Object> localScope,
            Map<String, Object> tempScope) {
        String normalizedScope = ActionVariableRegistry.extractScopeKey(canonicalVariableName);
        String normalizedBaseName = ActionVariableRegistry.extractBaseName(canonicalVariableName);
        if (normalizedBaseName.isEmpty()) {
            return;
        }
        if ("global".equals(normalizedScope)) {
            globalScope.put(normalizedBaseName, value);
        } else if ("local".equals(normalizedScope)) {
            localScope.put(normalizedBaseName, value);
        } else if ("temp".equals(normalizedScope)) {
            tempScope.put(normalizedBaseName, value);
        } else {
            sequenceScope.put(normalizedBaseName, value);
            runtimeValues.put(normalizedBaseName, value);
        }
    }

    private static void registerDefinedVariable(Map<String, VariableState> variableStates, String canonicalReference) {
        VariableState state = variableStates.computeIfAbsent(safe(canonicalReference).trim(),
                VariableState::new);
        state.defined = true;
    }

    private static void registerResolvedVariable(Map<String, VariableState> variableStates, String canonicalReference,
            Object value) {
        VariableState state = variableStates.computeIfAbsent(safe(canonicalReference).trim(),
                VariableState::new);
        state.defined = true;
        state.hasStaticValue = true;
        state.staticValue = value;
    }

    private static VariableState findVariableState(Context context, String normalizedReference) {
        if (context == null || normalizedReference == null || normalizedReference.trim().isEmpty()) {
            return null;
        }
        VariableState exact = context.variableStates.get(normalizedReference);
        if (exact != null) {
            return exact;
        }
        String baseReference = extractBaseReference(normalizedReference);
        if (baseReference.isEmpty()) {
            return null;
        }
        return context.variableStates.get(baseReference);
    }

    private static String extractBaseReference(String normalizedReference) {
        String normalized = safe(normalizedReference).trim();
        if (normalized.isEmpty()) {
            return "";
        }
        int firstDot = normalized.indexOf('.');
        if (firstDot <= 0 || firstDot >= normalized.length() - 1) {
            return normalized;
        }
        String scope = normalized.substring(0, firstDot);
        String tail = normalized.substring(firstDot + 1);
        int nextDot = tail.indexOf('.');
        int bracketIndex = tail.indexOf('[');
        int end = tail.length();
        if (nextDot >= 0) {
            end = Math.min(end, nextDot);
        }
        if (bracketIndex >= 0) {
            end = Math.min(end, bracketIndex);
        }
        return scope + "." + tail.substring(0, end);
    }

    private static String buildCanonicalReference(String scopeKey, String baseVariableName) {
        String normalizedBaseName = safe(baseVariableName).trim();
        if (normalizedBaseName.isEmpty()) {
            return "";
        }
        return normalizeScopeToken(scopeKey) + "." + normalizedBaseName;
    }

    private static String normalizeScopeToken(String rawScope) {
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
        return "sequence";
    }

    private static String trimPreview(String text, int maxLength) {
        String preview = safe(text).trim();
        if (preview.length() <= Math.max(8, maxLength)) {
            return preview;
        }
        return preview.substring(0, Math.max(5, maxLength - 3)) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
