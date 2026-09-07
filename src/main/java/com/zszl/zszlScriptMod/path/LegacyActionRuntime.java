package com.zszl.zszlScriptMod.path;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyActionRuntime {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile(
            "\\$\\{\\s*([a-zA-Z0-9_\\.\\[\\]+:\\-]+)\\s*\\}");
    private static final Map<String, ExpressionFunction> CUSTOM_FUNCTIONS = new ConcurrentHashMap<>();

    public interface ExpressionFunction {
        Object apply(List<Object> args);
    }

    private LegacyActionRuntime() {
    }

    public static void registerExpressionFunction(String name, ExpressionFunction function) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || function == null) {
            return;
        }
        CUSTOM_FUNCTIONS.put(normalized, function);
    }

    public static void unregisterExpressionFunction(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }
        CUSTOM_FUNCTIONS.remove(normalized);
    }

    public static Set<String> getRegisteredExpressionFunctionNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(CUSTOM_FUNCTIONS.keySet()));
    }

    public static JsonObject resolveParams(JsonObject params,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        return resolveParams(params, runtimeVars, player, sequence, stepIndex, actionIndex,
                Collections.<String>emptySet());
    }

    public static JsonObject resolveParams(JsonObject params,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex,
            Collection<String> literalParamKeys) {
        JsonObject resolved = new JsonObject();
        if (params == null) {
            return resolved;
        }
        LinkedHashMap<String, Boolean> literalKeys = new LinkedHashMap<>();
        if (literalParamKeys != null) {
            for (String key : literalParamKeys) {
                String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    literalKeys.put(normalized, Boolean.TRUE);
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : params.entrySet()) {
            String key = entry == null || entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && literalKeys.containsKey(key)) {
                resolved.add(entry.getKey(), copyElement(entry.getValue()));
                continue;
            }
            resolved.add(entry.getKey(),
                    resolveElement(entry.getValue(), runtimeVars, player, sequence, stepIndex, actionIndex));
        }
        return resolved;
    }

    public static Object resolveAssignedValue(JsonObject data,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        if (data == null) {
            return null;
        }

        String expression = readString(data, "expression").trim();
        if (!expression.isEmpty()) {
            return evaluateValueExpression(expression, data, runtimeVars, player, sequence, stepIndex, actionIndex);
        }

        String explicitType = readString(data, "valueType", "typeHint", "dataType");
        if (data.has("value")) {
            return parseValueByType(data.get("value"), explicitType);
        }
        if (data.has("number")) {
            return parseValueByType(data.get("number"), "number");
        }
        if (data.has("boolean")) {
            return parseValueByType(data.get("boolean"), "boolean");
        }
        if (data.has("string")) {
            return parseValueByType(data.get("string"), "string");
        }
        if (data.has("fromVar")) {
            return getRuntimeValue(readString(data, "fromVar"), runtimeVars, player, sequence, stepIndex,
                    actionIndex);
        }
        return null;
    }

    public static Object evaluateValueExpression(String expression,
            JsonObject data,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        String normalizedExpression = stripExpressionComments(preprocessAssignmentShorthand(expression, data));
        return new ExpressionParser(normalizedExpression, data, runtimeVars, player, sequence, stepIndex, actionIndex,
                false).parseValueExpression();
    }

    public static boolean evaluateExpression(String expression,
            JsonObject data,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        String normalizedExpression = stripExpressionComments(preprocessAssignmentShorthand(expression, data));
        return new ExpressionParser(normalizedExpression, data, runtimeVars, player, sequence, stepIndex, actionIndex,
                false)
                .parseExpression();
    }

    public static void validateValueExpressionSyntax(String expression, JsonObject data) {
        String normalizedExpression = stripExpressionComments(preprocessAssignmentShorthand(expression, data));
        new ExpressionParser(normalizedExpression, data, Collections.<String, Object>emptyMap(),
                null, null, -1, -1, true).parseValueExpression();
    }

    public static void validateBooleanExpressionSyntax(String expression, JsonObject data) {
        String normalizedExpression = stripExpressionComments(preprocessAssignmentShorthand(expression, data));
        new ExpressionParser(normalizedExpression, data, Collections.<String, Object>emptyMap(),
                null, null, -1, -1, true).parseExpression();
    }

    public static Object inferAssignedValueDefault(JsonObject data) {
        if (data == null) {
            return Integer.valueOf(0);
        }

        String expression = readString(data, "expression").trim();
        if (!expression.isEmpty()) {
            return inferExpressionResultDefault(expression, data);
        }

        String explicitType = readString(data, "valueType", "typeHint", "dataType").trim().toLowerCase(Locale.ROOT);
        if ("string".equals(explicitType)) {
            return "待获取文本";
        }
        if ("number".equals(explicitType) || "int".equals(explicitType) || "double".equals(explicitType)) {
            return Integer.valueOf(0);
        }
        if ("boolean".equals(explicitType) || "bool".equals(explicitType)) {
            return Boolean.TRUE;
        }

        if (data.has("boolean")) {
            return Boolean.TRUE;
        }
        if (data.has("number")) {
            return Integer.valueOf(0);
        }
        if (data.has("string")) {
            return "待获取文本";
        }
        if (data.has("value")) {
            JsonElement value = data.get("value");
            if (value != null && value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isBoolean()) {
                    return Boolean.TRUE;
                }
                if (value.getAsJsonPrimitive().isNumber()) {
                    return Integer.valueOf(0);
                }
                return "待获取文本";
            }
        }
        return Integer.valueOf(0);
    }

    private static Object inferExpressionResultDefault(String expression, JsonObject data) {
        String normalized = preprocessAssignmentShorthand(expression, data).trim();
        if (normalized.isEmpty()) {
            return Integer.valueOf(0);
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return new ArrayList<Object>();
        }
        if (normalized.startsWith("\"") || normalized.startsWith("'")) {
            return "待获取文本";
        }
        if ("true".equals(lower) || "false".equals(lower)) {
            return Boolean.TRUE;
        }
        if (looksLikeBooleanExpression(lower)) {
            return Boolean.TRUE;
        }
        if (looksLikeListExpression(lower)) {
            return new ArrayList<Object>();
        }
        if (looksLikeStringExpression(normalized, lower)) {
            return "待获取文本";
        }
        if (looksLikeNumericExpression(lower)) {
            return Integer.valueOf(0);
        }
        return Integer.valueOf(0);
    }

    private static boolean looksLikeBooleanExpression(String lowerExpression) {
        if (lowerExpression == null || lowerExpression.isEmpty()) {
            return false;
        }
        if (lowerExpression.contains("&&")
                || lowerExpression.contains("||")
                || lowerExpression.contains("==")
                || lowerExpression.contains("!=")
                || lowerExpression.contains(">=")
                || lowerExpression.contains("<=")
                || lowerExpression.contains(" > ")
                || lowerExpression.contains(" < ")
                || lowerExpression.startsWith("!")) {
            return true;
        }
        return startsWithAnyFunction(lowerExpression,
                "contains",
                "containsignorecase",
                "startswith",
                "startswithignorecase",
                "endswith",
                "endswithignorecase",
                "equalsignorecase",
                "regex",
                "matches",
                "empty",
                "exists",
                "any",
                "all",
                "eq",
                "ne",
                "gt",
                "lt",
                "gte",
                "lte",
                "between",
                "betweeninc",
                "betweeninclusive",
                "toboolean",
                "bool",
                "boolean");
    }

    private static boolean looksLikeListExpression(String lowerExpression) {
        return lowerExpression != null
                && startsWithAnyFunction(lowerExpression, "split");
    }

    private static boolean looksLikeStringExpression(String expression, String lowerExpression) {
        if (expression == null || lowerExpression == null) {
            return false;
        }
        if ((expression.contains("\"") || expression.contains("'")) && expression.contains("+")) {
            return true;
        }
        return startsWithAnyFunction(lowerExpression,
                "trim",
                "lower",
                "lowercase",
                "upper",
                "uppercase",
                "regexgroup",
                "regexcapture",
                "capturegroup",
                "regexextract",
                "replace",
                "substring",
                "substr",
                "join",
                "coalesce",
                "if",
                "tostring",
                "string");
    }

    private static boolean looksLikeNumericExpression(String lowerExpression) {
        if (lowerExpression == null || lowerExpression.isEmpty()) {
            return false;
        }
        if (lowerExpression.startsWith("+=")
                || lowerExpression.startsWith("-=")
                || lowerExpression.startsWith("*=")
                || lowerExpression.startsWith("/=")
                || lowerExpression.startsWith("%=")
                || lowerExpression.indexOf('+') >= 0
                || lowerExpression.indexOf('*') >= 0
                || lowerExpression.indexOf('/') >= 0
                || lowerExpression.indexOf('%') >= 0
                || lowerExpression.contains(" + ")
                || lowerExpression.contains(" - ")
                || lowerExpression.contains(" * ")
                || lowerExpression.contains(" / ")
                || lowerExpression.contains(" % ")) {
            return true;
        }
        if (lowerExpression.indexOf('-') > 0) {
            return true;
        }
        return startsWithAnyFunction(lowerExpression,
                "pow",
                "min",
                "max",
                "sum",
                "avg",
                "average",
                "abs",
                "clamp",
                "tonumber",
                "number",
                "int",
                "toint",
                "hextodec",
                "capdec",
                "random",
                "rand",
                "randomint",
                "randomfloat",
                "round",
                "floor",
                "ceil",
                "len",
                "size",
                "count",
                "indexof",
                "lastindexof");
    }

    private static boolean startsWithAnyFunction(String lowerExpression, String... functionNames) {
        if (lowerExpression == null || functionNames == null) {
            return false;
        }
        for (String functionName : functionNames) {
            if (functionName == null) {
                continue;
            }
            if (lowerExpression.startsWith(functionName + "(")) {
                return true;
            }
        }
        return false;
    }

    public static Object getRuntimeValue(String name,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        if (name == null) {
            return null;
        }

        String trimmed = normalizeScopedReference(name.trim());
        if (trimmed.isEmpty()) {
            return null;
        }

        if (runtimeVars != null && runtimeVars.containsKey(trimmed)) {
            return runtimeVars.get(trimmed);
        }

        if (runtimeVars instanceof ScopedRuntimeVariables && (trimmed.contains(".") || trimmed.contains("["))) {
            Object scopedValue = resolveNamedValueWithPath(trimmed, runtimeVars, Collections.<String, Object>emptyMap());
            if (scopedValue != null) {
                return scopedValue;
            }
        }

        Map<String, Object> builtins = collectBuiltinValues(player, sequence, stepIndex, actionIndex);
        if (builtins.containsKey(trimmed)) {
            return builtins.get(trimmed);
        }

        if (runtimeVars instanceof ScopedRuntimeVariables) {
            ScopedRuntimeVariables scoped = (ScopedRuntimeVariables) runtimeVars;
            Map<String, Object> scopeView = scoped.getScopeView(trimmed);
            if (!scopeView.isEmpty() || "global".equalsIgnoreCase(trimmed) || "sequence".equalsIgnoreCase(trimmed)
                    || "seq".equalsIgnoreCase(trimmed) || "local".equalsIgnoreCase(trimmed)
                    || "temp".equalsIgnoreCase(trimmed) || "tmp".equalsIgnoreCase(trimmed)) {
                return scopeView;
            }
        }

        if (trimmed.contains(".") || trimmed.contains("[")) {
            Object resolved = resolveNamedValueWithPath(trimmed, runtimeVars, builtins);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    public static String resolveTemplate(String raw,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = getRuntimeValue(name, runtimeVars, player, sequence, stepIndex, actionIndex);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(stringifyValue(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String stringifyValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonElement) {
            JsonElement element = (JsonElement) value;
            if (element.isJsonNull()) {
                return "";
            }
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
            return element.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            long longValue = (long) number;
            if (Double.compare(number, (double) longValue) == 0) {
                return String.valueOf(longValue);
            }
            return String.valueOf(number);
        }
        if (value instanceof Collection) {
            StringBuilder builder = new StringBuilder();
            for (Object entry : (Collection<?>) value) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(stringifyValue(entry));
            }
            return builder.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(stringifyValue(Array.get(value, i)));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }

    private static Object resolveNamedValueWithPath(String path,
            Map<String, Object> runtimeVars,
            Map<String, Object> builtins) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        String normalized = path.trim();
        int splitIndex = findFirstPathSeparator(normalized);
        if (splitIndex <= 0) {
            return null;
        }

        String root = normalized.substring(0, splitIndex);
        String tail = normalized.substring(splitIndex);
        Object rootValue = null;
        if (runtimeVars instanceof ScopedRuntimeVariables) {
            ScopedRuntimeVariables scoped = (ScopedRuntimeVariables) runtimeVars;
            Map<String, Object> scopeView = scoped.getScopeView(root);
            if (!scopeView.isEmpty() || "global".equalsIgnoreCase(root) || "sequence".equalsIgnoreCase(root)
                    || "seq".equalsIgnoreCase(root) || "local".equalsIgnoreCase(root)
                    || "temp".equalsIgnoreCase(root) || "tmp".equalsIgnoreCase(root)) {
                rootValue = scopeView;
            }
        }
        if (rootValue == null && runtimeVars != null && runtimeVars.containsKey(root)) {
            rootValue = runtimeVars.get(root);
        } else if (builtins != null && builtins.containsKey(root)) {
            rootValue = builtins.get(root);
        }

        if (rootValue == null) {
            return null;
        }
        return resolvePathValue(rootValue, tail);
    }

    private static int findFirstPathSeparator(String value) {
        if (value == null) {
            return -1;
        }
        int dot = value.indexOf('.');
        int bracket = value.indexOf('[');
        if (dot < 0) {
            return bracket;
        }
        if (bracket < 0) {
            return dot;
        }
        return Math.min(dot, bracket);
    }

    private static Object resolvePathValue(Object rootValue, String tail) {
        Object current = unwrapValue(rootValue);
        int cursor = 0;
        while (current != null && tail != null && cursor < tail.length()) {
            char c = tail.charAt(cursor);
            if (c == '.') {
                cursor++;
                int next = cursor;
                while (next < tail.length() && tail.charAt(next) != '.' && tail.charAt(next) != '[') {
                    next++;
                }
                String key = tail.substring(cursor, next).trim();
                if (key.isEmpty()) {
                    return null;
                }
                current = resolveObjectMember(current, key);
                cursor = next;
            } else if (c == '[') {
                int end = tail.indexOf(']', cursor);
                if (end < 0) {
                    return null;
                }
                String rawIndex = tail.substring(cursor + 1, end).trim();
                if (rawIndex.isEmpty()) {
                    return null;
                }
                current = resolveIndexedValue(current, rawIndex);
                cursor = end + 1;
            } else {
                return null;
            }
        }
        return unwrapValue(current);
    }

    private static Object resolveObjectMember(Object source, String key) {
        Object normalizedSource = unwrapValue(source);
        if (normalizedSource == null || key == null || key.trim().isEmpty()) {
            return null;
        }

        if (normalizedSource instanceof JsonObject) {
            JsonObject object = (JsonObject) normalizedSource;
            return object.has(key) ? unwrapValue(object.get(key)) : null;
        }
        if (normalizedSource instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) normalizedSource;
            if (map.containsKey(key)) {
                return unwrapValue(map.get(key));
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                    return unwrapValue(entry.getValue());
                }
            }
        }
        return null;
    }

    private static Object resolveIndexedValue(Object source, String rawIndex) {
        Object normalizedSource = unwrapValue(source);
        Integer index = tryParseIndex(rawIndex);
        if (normalizedSource == null || index == null) {
            return null;
        }

        if (normalizedSource instanceof JsonArray) {
            JsonArray array = (JsonArray) normalizedSource;
            int safeIndex = normalizeCollectionIndex(index.intValue(), array.size());
            return safeIndex < array.size() ? unwrapValue(array.get(safeIndex)) : null;
        }
        if (normalizedSource instanceof List) {
            List<?> list = (List<?>) normalizedSource;
            int safeIndex = normalizeCollectionIndex(index.intValue(), list.size());
            return safeIndex < list.size() ? unwrapValue(list.get(safeIndex)) : null;
        }
        if (normalizedSource.getClass().isArray()) {
            int safeIndex = normalizeCollectionIndex(index.intValue(), Array.getLength(normalizedSource));
            return safeIndex < Array.getLength(normalizedSource) ? unwrapValue(Array.get(normalizedSource, safeIndex))
                    : null;
        }
        return null;
    }

    private static int normalizeCollectionIndex(int rawIndex, int length) {
        if (length <= 0) {
            return Integer.MAX_VALUE;
        }
        int normalized = rawIndex < 0 ? length + rawIndex : rawIndex;
        if (normalized < 0) {
            return Integer.MAX_VALUE;
        }
        return normalized;
    }

    private static Integer tryParseIndex(String rawIndex) {
        if (rawIndex == null || rawIndex.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(rawIndex.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Object unwrapValue(Object value) {
        if (value instanceof JsonElement) {
            JsonElement element = (JsonElement) value;
            if (element.isJsonNull()) {
                return null;
            }
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isBoolean()) {
                    return element.getAsBoolean();
                }
                if (element.getAsJsonPrimitive().isNumber()) {
                    return element.getAsDouble();
                }
                return element.getAsString();
            }
        }
        return value;
    }

    private static JsonElement resolveElement(JsonElement element,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }

        if (element.isJsonObject()) {
            JsonObject resolved = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                resolved.add(entry.getKey(),
                        resolveElement(entry.getValue(), runtimeVars, player, sequence, stepIndex, actionIndex));
            }
            return resolved;
        }

        if (element.isJsonArray()) {
            JsonArray resolved = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                resolved.add(resolveElement(child, runtimeVars, player, sequence, stepIndex, actionIndex));
            }
            return resolved;
        }

        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                String rawText = element.getAsString();
                String normalizedReference = ActionParameterVariableResolver.normalizeReference(rawText);
                if (!normalizedReference.isEmpty()) {
                    Object resolvedValue = getRuntimeValue(normalizedReference, runtimeVars, player, sequence, stepIndex,
                            actionIndex);
                    if (resolvedValue != null) {
                        return toJsonElement(resolvedValue);
                    }
                }
                String resolvedText = resolveTemplate(rawText, runtimeVars, player, sequence, stepIndex, actionIndex);
                if (resolvedText.trim().startsWith("[") && resolvedText.trim().endsWith("]")) {
                    return resolveArrayLiteral(resolvedText, runtimeVars, player, sequence, stepIndex, actionIndex);
                }
                return toJsonElementFromString(resolvedText);
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return new com.google.gson.JsonPrimitive(element.getAsBoolean());
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return new com.google.gson.JsonPrimitive(element.getAsNumber());
            }
        }

        return JsonNull.INSTANCE;
    }

    private static JsonArray resolveArrayLiteral(String text,
            Map<String, Object> runtimeVars,
            EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        JsonArray resolved = new JsonArray();
        String trimmed = text == null ? "" : text.trim();
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) {
            return resolved;
        }
        for (String part : inner.split(",")) {
            resolved.add(resolveElement(new com.google.gson.JsonPrimitive(part.trim()), runtimeVars, player, sequence,
                    stepIndex, actionIndex));
        }
        return resolved;
    }

    private static JsonElement copyElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        return new JsonParser().parse(element.toString());
    }

    private static JsonElement toJsonElementFromString(String text) {
        if (text == null) {
            return JsonNull.INSTANCE;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new com.google.gson.JsonPrimitive("");
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            JsonArray array = new JsonArray();
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                return array;
            }
            String[] parts = inner.split(",");
            for (String part : parts) {
                array.add(toJsonElementFromString(part.trim()));
            }
            return array;
        }

        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return new com.google.gson.JsonPrimitive(Boolean.parseBoolean(trimmed));
        }

        try {
            if (trimmed.matches("[-+]?\\d+")) {
                long value = Long.parseLong(trimmed);
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return new com.google.gson.JsonPrimitive((int) value);
                }
                return new com.google.gson.JsonPrimitive(value);
            }
            if (trimmed.matches("[-+]?\\d*\\.\\d+")) {
                return new com.google.gson.JsonPrimitive(Double.parseDouble(trimmed));
            }
        } catch (Exception ignored) {
        }

        return new com.google.gson.JsonPrimitive(text);
    }

    private static JsonElement toJsonElement(Object value) {
        Object normalized = unwrapValue(value);
        if (normalized == null) {
            return JsonNull.INSTANCE;
        }
        if (normalized instanceof JsonElement) {
            JsonElement element = (JsonElement) normalized;
            return new JsonParser().parse(element.toString());
        }
        if (normalized instanceof Boolean) {
            return new com.google.gson.JsonPrimitive((Boolean) normalized);
        }
        if (normalized instanceof Number) {
            return new com.google.gson.JsonPrimitive((Number) normalized);
        }
        if (normalized instanceof String) {
            return toJsonElementFromString((String) normalized);
        }
        if (normalized instanceof Map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) normalized).entrySet()) {
                String key = entry == null || entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                if (key.trim().isEmpty()) {
                    continue;
                }
                object.add(key, toJsonElement(entry.getValue()));
            }
            return object;
        }
        if (normalized instanceof Collection) {
            JsonArray array = new JsonArray();
            for (Object entry : (Collection<?>) normalized) {
                array.add(toJsonElement(entry));
            }
            return array;
        }
        if (normalized.getClass().isArray()) {
            JsonArray array = new JsonArray();
            int length = Array.getLength(normalized);
            for (int i = 0; i < length; i++) {
                array.add(toJsonElement(Array.get(normalized, i)));
            }
            return array;
        }
        return new com.google.gson.JsonPrimitive(String.valueOf(normalized));
    }

    public static Map<String, Object> getBuiltinValues(EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                collectBuiltinValues(player, sequence, stepIndex, actionIndex)));
    }

    private static Map<String, Object> collectBuiltinValues(EntityPlayerSP player,
            PathSequenceManager.PathSequence sequence,
            int stepIndex,
            int actionIndex) {
        Map<String, Object> values = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getMinecraft();

        values.put("sequence_name", sequence == null ? "" : sequence.getName());
        values.put("step_index", stepIndex);
        values.put("action_index", actionIndex);
        String guiTitle = getCurrentGuiTitle(mc);
        values.put("gui_title", guiTitle);

        if (mc != null && mc.currentScreen != null) {
            values.put("current_screen", mc.currentScreen.getClass().getSimpleName());
        } else {
            values.put("current_screen", "");
        }

        Map<String, Object> guiValues = new LinkedHashMap<String, Object>();
        guiValues.put("title", guiTitle);
        guiValues.put("screen", values.get("current_screen"));
        values.put("gui", guiValues);

        if (player != null) {
            values.put("player_x", player.posX);
            values.put("player_y", player.posY);
            values.put("player_z", player.posZ);
            values.put("player_block_x", player.getPosition().getX());
            values.put("player_block_y", player.getPosition().getY());
            values.put("player_block_z", player.getPosition().getZ());
            values.put("player_yaw", player.rotationYaw);
            values.put("player_pitch", player.rotationPitch);
            values.put("player_name", player.getName());
            values.put("player_health", player.getHealth());
            values.put("player_food", player.getFoodStats().getFoodLevel());

            Map<String, Object> playerValues = new LinkedHashMap<String, Object>();
            playerValues.put("x", player.posX);
            playerValues.put("y", player.posY);
            playerValues.put("z", player.posZ);
            playerValues.put("block_x", player.getPosition().getX());
            playerValues.put("block_y", player.getPosition().getY());
            playerValues.put("block_z", player.getPosition().getZ());
            playerValues.put("yaw", player.rotationYaw);
            playerValues.put("pitch", player.rotationPitch);
            playerValues.put("name", player.getName());
            playerValues.put("uuid", String.valueOf(player.getUniqueID()));
            playerValues.put("health", player.getHealth());
            playerValues.put("max_health", player.getMaxHealth());
            playerValues.put("food", player.getFoodStats().getFoodLevel());
            playerValues.put("saturation", player.getFoodStats().getSaturationLevel());
            playerValues.put("armor", player.getTotalArmorValue());
            playerValues.put("air", player.getAir());
            playerValues.put("experience", player.experience);
            playerValues.put("experience_level", player.experienceLevel);
            playerValues.put("selected_slot", player.inventory.currentItem);
            playerValues.put("dimension", player.dimension);
            playerValues.put("on_ground", player.onGround);
            playerValues.put("sneaking", player.isSneaking());
            playerValues.put("sprinting", player.isSprinting());
            playerValues.put("burning", player.isBurning());
            values.put("player", playerValues);
        }

        return values;
    }

    private static String stripExpressionComments(String expression) {
        String source = expression == null ? "" : expression;
        StringBuilder result = new StringBuilder(source.length());
        char quote = 0;
        boolean escaped = false;
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (comment) {
                if (c == '\n' || c == '\r') {
                    comment = false;
                    result.append(c);
                } else {
                    result.append(' ');
                }
                continue;
            }
            if (quote != 0) {
                result.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                result.append(c);
                continue;
            }
            if (c == '#' || c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                comment = true;
                result.append(' ');
                if (c == '/') {
                    result.append(' ');
                    i++;
                }
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private static String normalizeScopedReference(String value) {
        String text = value == null ? "" : value.trim();
        int colon = text.indexOf(':');
        if (colon <= 0) {
            return text;
        }
        String scope = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        if ("global".equals(scope) || "sequence".equals(scope) || "seq".equals(scope)
                || "local".equals(scope) || "temp".equals(scope) || "tmp".equals(scope)) {
            return scope + "." + text.substring(colon + 1).trim();
        }
        return text;
    }

    private static String getCurrentGuiTitle(Minecraft mc) {
        if (mc == null) {
            return "";
        }
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return "";
        }
        if (screen instanceof GuiChest && mc.player != null && mc.player.openContainer instanceof ContainerChest) {
            try {
                IInventory inv = ((ContainerChest) mc.player.openContainer).getLowerChestInventory();
                if (inv != null && inv.getDisplayName() != null) {
                    return inv.getDisplayName().getUnformattedText();
                }
            } catch (Exception ignored) {
            }
        }
        if (screen instanceof GuiMerchant) {
            return "Merchant";
        }
        return screen.getClass().getSimpleName();
    }

    private static String readString(JsonObject data, String... keys) {
        if (data == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && data.has(key) && !data.get(key).isJsonNull()) {
                JsonElement element = data.get(key);
                if (element.isJsonPrimitive()) {
                    return element.getAsString();
                }
                return element.toString();
            }
        }
        return "";
    }

    private static String preprocessAssignmentShorthand(String expression, JsonObject data) {
        String text = expression == null ? "" : expression.trim();
        if (text.isEmpty()) {
            return text;
        }

        Matcher fullAssignment = Pattern.compile("^([A-Za-z0-9_.$:\\[\\]]+)\\s*([+\\-*/%])=\\s*(.+)$")
                .matcher(text);
        if (fullAssignment.matches()) {
            String left = fullAssignment.group(1).trim();
            String operator = fullAssignment.group(2).trim();
            String right = fullAssignment.group(3).trim();
            return "(" + left + ") " + operator + " (" + right + ")";
        }

        Matcher shortAssignment = Pattern.compile("^([+\\-*/%])=\\s*(.+)$").matcher(text);
        if (shortAssignment.matches()) {
            String currentName = readString(data, "name").trim();
            if (!currentName.isEmpty()) {
                String operator = shortAssignment.group(1).trim();
                String right = shortAssignment.group(2).trim();
                return "(" + currentName + ") " + operator + " (" + right + ")";
            }
        }

        return text;
    }

    private static Object parseValueByType(JsonElement element, String typeHint) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        String normalizedType = typeHint == null ? "" : typeHint.trim().toLowerCase(Locale.ROOT);
        if ("string".equals(normalizedType)) {
            return safeString(element);
        }
        if ("number".equals(normalizedType) || "int".equals(normalizedType) || "double".equals(normalizedType)) {
            return element.getAsDouble();
        }
        if ("boolean".equals(normalizedType) || "bool".equals(normalizedType)) {
            return parseBoolean(element);
        }

        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                double value = element.getAsDouble();
                long longValue = (long) value;
                if (Double.compare(value, (double) longValue) == 0) {
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return (int) longValue;
                    }
                    return longValue;
                }
                return value;
            }
            return element.getAsString();
        }

        if (element.isJsonArray() || element.isJsonObject()) {
            return copyElement(element);
        }

        return safeString(element);
    }

    private static boolean parseBoolean(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return Boolean.parseBoolean(safeString(element));
    }

    private static Object normalizeNumericResult(double value) {
        long longValue = Math.round(value);
        if (Double.compare(value, (double) longValue) == 0) {
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
            return longValue;
        }
        return value;
    }

    private static String safeString(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return value.toString();
    }

    private static final class ExpressionParser {
        private final String expression;
        private final JsonObject data;
        private final Map<String, Object> runtimeVars;
        private final EntityPlayerSP player;
        private final PathSequenceManager.PathSequence sequence;
        private final int stepIndex;
        private final int actionIndex;
        private final boolean validationMode;
        private int index = 0;

        private ExpressionParser(String expression,
                JsonObject data,
                Map<String, Object> runtimeVars,
                EntityPlayerSP player,
                PathSequenceManager.PathSequence sequence,
                int stepIndex,
                int actionIndex,
                boolean validationMode) {
            this.expression = expression == null ? "" : expression;
            this.data = data == null ? new JsonObject() : data;
            this.runtimeVars = runtimeVars;
            this.player = player;
            this.sequence = sequence;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.validationMode = validationMode;
        }

        private boolean parseExpression() {
            return toBoolean(parseValueExpression());
        }

        private Object parseValueExpression() {
            try {
                Object result = parseConditional();
                skipWhitespace();
                if (index < expression.length()) {
                    throw error("表达式存在未解析内容: " + expression.substring(index));
                }
                return result;
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().contains("位置 ")) {
                    throw e;
                }
                throw error(e.getMessage() == null ? "表达式解析失败" : e.getMessage());
            }
        }

        private Object parseConditional() {
            Object value = parseNullCoalesce();
            skipWhitespace();
            if (match("?")) {
                Object whenTrue = parseConditional();
                skipWhitespace();
                if (!match(":")) {
                    throw error("条件表达式缺少冒号");
                }
                Object whenFalse = parseConditional();
                return toBoolean(value) ? whenTrue : whenFalse;
            }
            return value;
        }

        private Object parseNullCoalesce() {
            Object value = parseOr();
            while (true) {
                skipWhitespace();
                if (match("??")) {
                    Object fallback = parseOr();
                    value = isNullish(value) ? fallback : value;
                } else {
                    return value;
                }
            }
        }

        private Object parseOr() {
            Object value = parseAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    Object right = parseAnd();
                    value = toBoolean(value) || toBoolean(right);
                } else {
                    return value;
                }
            }
        }

        private Object parseAnd() {
            Object value = parseComparison();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    Object right = parseComparison();
                    value = toBoolean(value) && toBoolean(right);
                } else {
                    return value;
                }
            }
        }

        private Object parseComparison() {
            Object left = parseAdditive();
            skipWhitespace();
            String operator = tryParseOperator();
            if (operator == null) {
                return left;
            }
            skipWhitespace();
            Object right = parseAdditive();
            return evaluateComparison(left, operator, right);
        }

        private Object parseAdditive() {
            Object value = parseMultiplicative();
            while (true) {
                skipWhitespace();
                if (match("+")) {
                    value = applyAddition(value, parseMultiplicative());
                } else if (match("-")) {
                    value = applySubtraction(value, parseMultiplicative());
                } else {
                    return value;
                }
            }
        }

        private Object parseMultiplicative() {
            Object value = parseUnary();
            while (true) {
                skipWhitespace();
                if (match("*")) {
                    value = applyMultiplication(value, parseUnary());
                } else if (match("/")) {
                    value = applyDivision(value, parseUnary());
                } else if (match("%")) {
                    value = applyModulo(value, parseUnary());
                } else {
                    return value;
                }
            }
        }

        private Object parseUnary() {
            skipWhitespace();
            if (match("!")) {
                return !toBoolean(parseUnary());
            }
            if (match("+")) {
                return normalizeNumericResult(requireNumber("一元正号", parseUnary()));
            }
            if (match("-")) {
                return normalizeNumericResult(-requireNumber("一元负号", parseUnary()));
            }
            return parsePrimary();
        }

        private Object parsePrimary() {
            skipWhitespace();
            if (match("(")) {
                Object value = parseConditional();
                skipWhitespace();
                if (!match(")")) {
                    throw error("缺少右括号");
                }
                return value;
            }
            return parseValueToken();
        }

        private Object parseValueToken() {
            skipWhitespace();
            if (index >= expression.length()) {
                throw error("表达式缺少值");
            }

            char c = expression.charAt(index);
            if (c == '\'' || c == '"') {
                return parseQuotedString();
            }
            if (c == '[') {
                return parseListLiteral();
            }

            int tokenStart = index;
            String token = parseToken();
            if (token.isEmpty()) {
                throw error("表达式值为空");
            }

            skipWhitespace();
            if (!token.startsWith("$") && match("(")) {
                return parseFunctionCall(token);
            }

            if ("true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
                return Boolean.parseBoolean(token);
            }
            if ("null".equalsIgnoreCase(token) || "nil".equalsIgnoreCase(token)) {
                return null;
            }

            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException ignored) {
            }

            if (token.startsWith("$")) {
                return getRuntimeValue(token.substring(1), runtimeVars, player, sequence, stepIndex, actionIndex);
            }

            if (data.has(token + "Var")) {
                return getRuntimeValue(readString(data, token + "Var"), runtimeVars, player, sequence, stepIndex,
                        actionIndex);
            }
            if (data.has(token)) {
                return parseValueByType(data.get(token), readString(data, token + "Type"));
            }

            Object resolved = getRuntimeValue(token, runtimeVars, player, sequence, stepIndex, actionIndex);
            if (resolved != null || tokenStart < expression.length()) {
                return resolved;
            }
            return null;
        }

        private List<Object> parseListLiteral() {
            if (!match("[")) {
                throw error("列表字面量必须以 [ 开头");
            }
            List<Object> values = new ArrayList<>();
            while (true) {
                skipWhitespace();
                if (match("]")) {
                    return values;
                }
                values.add(parseConditional());
                skipWhitespace();
                if (match("]")) {
                    return values;
                }
                if (!match(",")) {
                    throw error("列表字面量缺少逗号或右括号");
                }
            }
        }

        private Object parseFunctionCall(String functionName) {
            String normalizedName = functionName == null ? "" : functionName.trim().toLowerCase(Locale.ROOT);
            if (isCapturedIdNameFunction(normalizedName)) {
                return parseCapturedIdNameFunction(normalizedName);
            }
            List<Object> args = new ArrayList<>();
            while (true) {
                skipWhitespace();
                if (match(")")) {
                    break;
                }
                args.add(parseConditional());
                skipWhitespace();
                if (match(")")) {
                    break;
                }
                if (!match(",")) {
                    throw error("函数调用缺少逗号或右括号: " + functionName);
                }
            }
            return evaluateFunction(functionName, args);
        }

        private boolean isCapturedIdNameFunction(String name) {
            return "cap".equals(name)
                    || "captured".equals(name)
                    || "capturedid".equals(name)
                    || "capdec".equals(name)
                    || "captureddec".equals(name)
                    || "capturediddec".equals(name);
        }

        private Object parseCapturedIdNameFunction(String functionName) {
            skipWhitespace();
            if (match(")")) {
                throw error("函数 " + functionName + " 需要捕获ID名称参数");
            }

            String key;
            if (index < expression.length()
                    && (expression.charAt(index) == '\'' || expression.charAt(index) == '"')) {
                key = parseQuotedString();
            } else {
                key = parseToken();
                if (key.startsWith("$")) {
                    Object resolved = getRuntimeValue(key.substring(1), runtimeVars, player, sequence, stepIndex,
                            actionIndex);
                    key = asString(resolved);
                }
            }
            skipWhitespace();
            if (!match(")")) {
                throw error("函数 " + functionName + " 只支持一个捕获ID名称参数");
            }

            String hex = getCapturedIdHexValue(functionName, key);
            if ("capdec".equals(functionName) || "captureddec".equals(functionName)
                    || "capturediddec".equals(functionName)) {
                return normalizeNumericResult(parseHexDecimalValue(functionName, hex));
            }
            return hex;
        }

        private String parseQuotedString() {
            char quote = expression.charAt(index++);
            StringBuilder sb = new StringBuilder();
            while (index < expression.length()) {
                char c = expression.charAt(index++);
                if (c == '\\' && index < expression.length()) {
                    char escaped = expression.charAt(index++);
                    switch (escaped) {
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case '\\':
                        case '\'':
                        case '"':
                            sb.append(escaped);
                            break;
                        default:
                            sb.append(escaped);
                            break;
                    }
                    continue;
                }
                if (c == quote) {
                    return sb.toString();
                }
                sb.append(c);
            }
            throw error("字符串未闭合");
        }

        private String parseOperator() {
            String[] operators = new String[] { "==", "!=", ">=", "<=", ">", "<" };
            for (String operator : operators) {
                if (match(operator)) {
                    return operator;
                }
            }
            throw error("缺少比较操作符");
        }

        private String tryParseOperator() {
            String[] operators = new String[] { "==", "!=", ">=", "<=", ">", "<" };
            for (String operator : operators) {
                if (expression.regionMatches(index, operator, 0, operator.length())) {
                    index += operator.length();
                    return operator;
                }
            }
            return null;
        }

        private String parseToken() {
            StringBuilder sb = new StringBuilder();
            while (index < expression.length()) {
                char c = expression.charAt(index);
                if (c == '[' && sb.length() > 0) {
                    int end = expression.indexOf(']', index + 1);
                    if (end > index + 1) {
                        String rawIndex = expression.substring(index + 1, end).trim();
                        if (rawIndex.matches("[-+]?\\d+")) {
                            sb.append(expression, index, end + 1);
                            index = end + 1;
                            continue;
                        }
                    }
                    break;
                }
                if (c == ':' && isScopeToken(sb.toString()) && index + 1 < expression.length()
                        && (Character.isLetterOrDigit(expression.charAt(index + 1))
                                || expression.charAt(index + 1) == '_')) {
                    sb.append('.');
                    index++;
                    continue;
                }
                if (Character.isWhitespace(c) || c == '(' || c == ')' || c == '&' || c == '|' || c == '=' || c == '!'
                        || c == '>' || c == '<' || c == ',' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
                        || c == '?' || c == ':'
                        || c == '[' || c == ']') {
                    break;
                }
                sb.append(c);
                index++;
            }
            return sb.toString().trim();
        }

        private boolean isScopeToken(String token) {
            String scope = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (scope.startsWith("$")) {
                scope = scope.substring(1);
            }
            return "global".equals(scope) || "sequence".equals(scope) || "seq".equals(scope)
                    || "local".equals(scope) || "temp".equals(scope) || "tmp".equals(scope);
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean match(String token) {
            if (expression.regionMatches(index, token, 0, token.length())) {
                index += token.length();
                return true;
            }
            return false;
        }

        private boolean evaluateComparison(Object left, String operator, Object right) {
            switch (operator) {
                case "==":
                    return compareEquals(left, right);
                case "!=":
                    return !compareEquals(left, right);
                case ">":
                    return compareNumbers(left, right) > 0;
                case "<":
                    return compareNumbers(left, right) < 0;
                case ">=":
                    return compareNumbers(left, right) >= 0;
                case "<=":
                    return compareNumbers(left, right) <= 0;
                default:
                    throw error("不支持的比较操作符: " + operator);
            }
        }

        private Object applyAddition(Object left, Object right) {
            Double leftNumber = toNumber(left);
            Double rightNumber = toNumber(right);
            if ((leftNumber != null || isNullish(left)) && (rightNumber != null || isNullish(right))) {
                double safeLeft = leftNumber == null ? 0.0D : leftNumber;
                double safeRight = rightNumber == null ? 0.0D : rightNumber;
                return normalizeNumericResult(safeLeft + safeRight);
            }
            if (leftNumber != null && rightNumber != null) {
                return normalizeNumericResult(leftNumber + rightNumber);
            }
            return asString(left) + asString(right);
        }

        private Object applySubtraction(Object left, Object right) {
            return normalizeNumericResult(requireNumber("减法", left) - requireNumber("减法", right));
        }

        private Object applyMultiplication(Object left, Object right) {
            return normalizeNumericResult(requireNumber("乘法", left) * requireNumber("乘法", right));
        }

        private Object applyDivision(Object left, Object right) {
            double divisor = requireNumber("除法", right);
            if (Math.abs(divisor) < 1.0E-9D) {
                if (validationMode) {
                    return 0.0D;
                }
                throw error("除数不能为 0");
            }
            return normalizeNumericResult(requireNumber("除法", left) / divisor);
        }

        private Object applyModulo(Object left, Object right) {
            double divisor = requireNumber("取模", right);
            if (Math.abs(divisor) < 1.0E-9D) {
                if (validationMode) {
                    return 0.0D;
                }
                throw error("取模除数不能为 0");
            }
            return normalizeNumericResult(requireNumber("取模", left) % divisor);
        }

        private Object evaluateFunction(String functionName, List<Object> args) {
            String name = functionName == null ? "" : functionName.trim().toLowerCase(Locale.ROOT);
            ExpressionFunction customFunction = CUSTOM_FUNCTIONS.get(name);
            if (customFunction != null) {
                return customFunction.apply(args == null ? new ArrayList<Object>() : args);
            }
            switch (name) {
                case "contains":
                    requireArgCountAtLeast(name, args, 2);
                    return containsValue(args.get(0), args.get(1), false);
                case "containsignorecase":
                    requireArgCountAtLeast(name, args, 2);
                    return containsValue(args.get(0), args.get(1), true);
                case "startswith":
                    requireArgCountAtLeast(name, args, 2);
                    return asString(args.get(0)).startsWith(asString(args.get(1)));
                case "startswithignorecase":
                    requireArgCountAtLeast(name, args, 2);
                    return asString(args.get(0)).toLowerCase(Locale.ROOT)
                            .startsWith(asString(args.get(1)).toLowerCase(Locale.ROOT));
                case "endswith":
                    requireArgCountAtLeast(name, args, 2);
                    return asString(args.get(0)).endsWith(asString(args.get(1)));
                case "endswithignorecase":
                    requireArgCountAtLeast(name, args, 2);
                    return asString(args.get(0)).toLowerCase(Locale.ROOT)
                            .endsWith(asString(args.get(1)).toLowerCase(Locale.ROOT));
                case "equalsignorecase":
                    requireArgCountAtLeast(name, args, 2);
                    return asString(args.get(0)).equalsIgnoreCase(asString(args.get(1)));
                case "regex":
                case "matches":
                    requireArgCountAtLeast(name, args, 2);
                    return Pattern.compile(asString(args.get(1))).matcher(asString(args.get(0))).find();
                case "regexgroup":
                case "regexcapture":
                case "capturegroup":
                case "regexextract":
                    requireArgCountAtLeast(name, args, 3);
                    return regexGroupValue(name, asString(args.get(0)), asString(args.get(1)), args.get(2));
                case "trim":
                    requireArgCountAtLeast(name, args, 1);
                    return asString(args.get(0)).trim();
                case "lower":
                case "lowercase":
                    requireArgCountAtLeast(name, args, 1);
                    return asString(args.get(0)).toLowerCase(Locale.ROOT);
                case "upper":
                case "uppercase":
                    requireArgCountAtLeast(name, args, 1);
                    return asString(args.get(0)).toUpperCase(Locale.ROOT);
                case "replace":
                    requireArgCountAtLeast(name, args, 3);
                    return asString(args.get(0)).replace(asString(args.get(1)), asString(args.get(2)));
                case "substring":
                case "substr":
                    requireArgCountAtLeast(name, args, 2);
                    return substringValue(args.get(0), args.get(1), args.size() >= 3 ? args.get(2) : null);
                case "indexof":
                    requireArgCountAtLeast(name, args, 2);
                    return (double) asString(args.get(0)).indexOf(asString(args.get(1)));
                case "lastindexof":
                    requireArgCountAtLeast(name, args, 2);
                    return (double) asString(args.get(0)).lastIndexOf(asString(args.get(1)));
                case "split":
                    requireArgCountAtLeast(name, args, 2);
                    return splitToList(asString(args.get(0)), asString(args.get(1)));
                case "join":
                    requireArgCountAtLeast(name, args, 2);
                    return joinValues(args.get(0), asString(args.get(1)));
                case "coalesce":
                    requireArgCountAtLeast(name, args, 1);
                    for (Object arg : args) {
                        if (!isNullish(arg)) {
                            return arg;
                        }
                    }
                    return null;
                case "if":
                    requireArgCountAtLeast(name, args, 3);
                    return toBoolean(args.get(0)) ? args.get(1) : args.get(2);
                case "empty":
                    requireArgCountAtLeast(name, args, 1);
                    return isNullish(args.get(0));
                case "exists":
                    requireArgCountAtLeast(name, args, 1);
                    return !isNullish(args.get(0));
                case "len":
                case "size":
                case "count":
                    requireArgCountAtLeast(name, args, 1);
                    return sizeOf(args.get(0));
                case "first":
                    requireArgCountAtLeast(name, args, 1);
                    return firstOf(args.get(0));
                case "last":
                    requireArgCountAtLeast(name, args, 1);
                    return lastOf(args.get(0));
                case "any":
                    requireArgCountAtLeast(name, args, 1);
                    for (Object arg : args) {
                        if (toBoolean(arg)) {
                            return true;
                        }
                    }
                    return false;
                case "all":
                    requireArgCountAtLeast(name, args, 1);
                    for (Object arg : args) {
                        if (!toBoolean(arg)) {
                            return false;
                        }
                    }
                    return true;
                case "eq":
                    requireArgCountAtLeast(name, args, 2);
                    return compareEquals(args.get(0), args.get(1));
                case "ne":
                    requireArgCountAtLeast(name, args, 2);
                    return !compareEquals(args.get(0), args.get(1));
                case "gt":
                    requireArgCountAtLeast(name, args, 2);
                    return compareNumbers(args.get(0), args.get(1)) > 0;
                case "lt":
                    requireArgCountAtLeast(name, args, 2);
                    return compareNumbers(args.get(0), args.get(1)) < 0;
                case "gte":
                    requireArgCountAtLeast(name, args, 2);
                    return compareNumbers(args.get(0), args.get(1)) >= 0;
                case "lte":
                    requireArgCountAtLeast(name, args, 2);
                    return compareNumbers(args.get(0), args.get(1)) <= 0;
                case "min":
                    requireArgCountAtLeast(name, args, 1);
                    return minOf(args);
                case "max":
                    requireArgCountAtLeast(name, args, 1);
                    return maxOf(args);
                case "sum":
                    requireArgCountAtLeast(name, args, 1);
                    return sumOf(args);
                case "avg":
                case "average":
                    requireArgCountAtLeast(name, args, 1);
                    return averageOf(args);
                case "abs":
                    requireArgCountAtLeast(name, args, 1);
                    return Math.abs(requireNumber(name, args.get(0)));
                case "pow":
                    requireArgCountAtLeast(name, args, 2);
                    return Math.pow(requireNumber(name, args.get(0)), requireNumber(name, args.get(1)));
                case "random":
                case "rand":
                    return evaluateRandomFunction(name, args);
                case "randomint":
                    return evaluateRandomIntFunction(name, args);
                case "randomfloat":
                    return evaluateRandomFloatFunction(name, args);
                case "clamp":
                    requireArgCountAtLeast(name, args, 3);
                    double value = requireNumber(name, args.get(0));
                    double min = requireNumber(name, args.get(1));
                    double max = requireNumber(name, args.get(2));
                    return Math.max(min, Math.min(max, value));
                case "between":
                    requireArgCountAtLeast(name, args, 3);
                    return requireNumber(name, args.get(0)) > requireNumber(name, args.get(1))
                            && requireNumber(name, args.get(0)) < requireNumber(name, args.get(2));
                case "betweeninc":
                case "betweeninclusive":
                    requireArgCountAtLeast(name, args, 3);
                    return requireNumber(name, args.get(0)) >= requireNumber(name, args.get(1))
                            && requireNumber(name, args.get(0)) <= requireNumber(name, args.get(2));
                case "tonumber":
                case "number":
                    requireArgCountAtLeast(name, args, 1);
                    return normalizeNumericResult(requireNumber(name, args.get(0)));
                case "int":
                case "toint":
                    requireArgCountAtLeast(name, args, 1);
                    return normalizeNumericResult((long) requireNumber(name, args.get(0)));
                case "hextodec":
                case "hexdec":
                    requireArgCountAtLeast(name, args, 1);
                    return normalizeNumericResult(parseHexDecimalValue(name, asString(args.get(0))));
                case "toboolean":
                case "bool":
                case "boolean":
                    requireArgCountAtLeast(name, args, 1);
                    return toBoolean(args.get(0));
                case "tostring":
                case "string":
                    requireArgCountAtLeast(name, args, 1);
                    return asString(args.get(0));
                case "round":
                    requireArgCountAtLeast(name, args, 1);
                    return (double) Math.round(requireNumber(name, args.get(0)));
                case "floor":
                    requireArgCountAtLeast(name, args, 1);
                    return Math.floor(requireNumber(name, args.get(0)));
                case "ceil":
                    requireArgCountAtLeast(name, args, 1);
                    return Math.ceil(requireNumber(name, args.get(0)));
                default:
                    throw error("不支持的表达式函数: " + functionName);
            }
        }

        private String regexGroupValue(String functionName, String source, String regex, Object groupValue) {
            double groupNumber = requireNumber(functionName, groupValue);
            if (groupNumber < 0 || groupNumber != Math.rint(groupNumber) || regex.trim().isEmpty()) {
                return "";
            }

            int group = (int) groupNumber;
            Matcher matcher = Pattern.compile(regex).matcher(source);
            if (!matcher.find() || group > matcher.groupCount()) {
                return "";
            }

            String value = matcher.group(group);
            return value == null ? "" : value;
        }

        private int compareNumbers(Object left, Object right) {
            return Double.compare(requireNumber("比较", left), requireNumber("比较", right));
        }

        private boolean compareEquals(Object left, Object right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }

            Double leftNumber = toNumber(left);
            Double rightNumber = toNumber(right);
            if (leftNumber != null && rightNumber != null) {
                return Double.compare(leftNumber, rightNumber) == 0;
            }

            if (left instanceof Boolean || right instanceof Boolean) {
                return toBoolean(left) == toBoolean(right);
            }

            return String.valueOf(left).equals(String.valueOf(right));
        }

        private Double toNumber(Object value) {
            if (value == null) {
                return validationMode ? 0.0D : null;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue() ? 1.0D : 0.0D;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return validationMode ? 0.0D : null;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private boolean toBoolean(Object value) {
            Object normalized = unwrapValue(value);
            if (normalized instanceof Boolean) {
                return ((Boolean) normalized).booleanValue();
            }
            if (normalized instanceof Number) {
                return Double.compare(((Number) normalized).doubleValue(), 0.0D) != 0;
            }
            if (normalized instanceof Collection) {
                return !((Collection<?>) normalized).isEmpty();
            }
            if (normalized instanceof JsonArray) {
                return ((JsonArray) normalized).size() > 0;
            }
            if (normalized instanceof JsonObject) {
                return ((JsonObject) normalized).entrySet().iterator().hasNext();
            }
            if (normalized != null && normalized.getClass().isArray()) {
                return Array.getLength(normalized) > 0;
            }
            String text = normalized == null ? "" : String.valueOf(normalized).trim();
            if (text.isEmpty()) {
                return false;
            }
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
            return true;
        }

        private void requireArgCountAtLeast(String functionName, List<Object> args, int expected) {
            if (args == null || args.size() < expected) {
                throw error("函数 " + functionName + " 至少需要 " + expected + " 个参数");
            }
        }

        private void requireExactArgCount(String functionName, List<Object> args, int expected) {
            int size = args == null ? 0 : args.size();
            if (size != expected) {
                throw error("函数 " + functionName + " 需要 " + expected + " 个参数");
            }
        }

        private Object evaluateRandomFunction(String functionName, List<Object> args) {
            int size = args == null ? 0 : args.size();
            if (size == 0) {
                return ThreadLocalRandom.current().nextDouble();
            }
            if (size != 2) {
                throw error("函数 " + functionName + " 只支持 0 或 2 个参数");
            }
            double min = requireNumber(functionName, args.get(0));
            double max = requireNumber(functionName, args.get(1));
            validateRandomRange(functionName, min, max);
            if (Double.compare(min, max) == 0) {
                return normalizeNumericResult(min);
            }
            if (isWholeNumberValue(min) && isWholeNumberValue(max)) {
                long minValue = Math.round(min);
                long maxValue = Math.round(max);
                return normalizeNumericResult(nextInclusiveLong(minValue, maxValue));
            }
            return ThreadLocalRandom.current().nextDouble(min, max);
        }

        private Object evaluateRandomIntFunction(String functionName, List<Object> args) {
            requireExactArgCount(functionName, args, 2);
            double min = requireNumber(functionName, args.get(0));
            double max = requireNumber(functionName, args.get(1));
            if (!isWholeNumberValue(min) || !isWholeNumberValue(max)) {
                throw error("函数 " + functionName + " 需要整数范围参数");
            }
            long minValue = Math.round(min);
            long maxValue = Math.round(max);
            if (minValue > maxValue) {
                throw error("函数 " + functionName + " 的最小值不能大于最大值");
            }
            return normalizeNumericResult(nextInclusiveLong(minValue, maxValue));
        }

        private Object evaluateRandomFloatFunction(String functionName, List<Object> args) {
            requireExactArgCount(functionName, args, 2);
            double min = requireNumber(functionName, args.get(0));
            double max = requireNumber(functionName, args.get(1));
            validateRandomRange(functionName, min, max);
            if (Double.compare(min, max) == 0) {
                return min;
            }
            return ThreadLocalRandom.current().nextDouble(min, max);
        }

        private void validateRandomRange(String functionName, double min, double max) {
            if (!Double.isFinite(min) || !Double.isFinite(max)) {
                throw error("函数 " + functionName + " 的范围参数必须是有限数字");
            }
            if (min > max) {
                throw error("函数 " + functionName + " 的最小值不能大于最大值");
            }
        }

        private boolean isWholeNumberValue(double value) {
            return Double.isFinite(value) && Math.rint(value) == value;
        }

        private long nextInclusiveLong(long minInclusive, long maxInclusive) {
            if (minInclusive == maxInclusive) {
                return minInclusive;
            }
            double range = (double) maxInclusive - (double) minInclusive + 1D;
            long sampled = minInclusive + (long) Math.floor(ThreadLocalRandom.current().nextDouble() * range);
            if (sampled > maxInclusive) {
                return maxInclusive;
            }
            return sampled;
        }

        private double requireNumber(String functionName, Object value) {
            Double number = toNumber(value);
            if (number == null) {
                if (validationMode && isNullish(value)) {
                    return 0.0D;
                }
                throw error("函数 " + functionName + " 需要数字参数");
            }
            return number.doubleValue();
        }

        private String getCapturedIdHexValue(String functionName, String key) {
            String normalizedKey = key == null ? "" : key.trim();
            if (normalizedKey.isEmpty()) {
                throw error("函数 " + functionName + " 需要非空捕获ID名称");
            }
            if (validationMode) {
                return "00 00 00 00";
            }
            String hex = CapturedIdRuleManager.getCapturedIdHex(normalizedKey);
            if (hex == null || hex.trim().isEmpty()) {
                throw error("函数 " + functionName + " 找不到已捕获ID: " + normalizedKey);
            }
            return hex;
        }

        private double parseHexDecimalValue(String functionName, String hexText) {
            String normalized = hexText == null ? "" : hexText.replaceAll("[^0-9A-Fa-f]", "");
            if (normalized.isEmpty()) {
                if (validationMode) {
                    return 0.0D;
                }
                throw error("函数 " + functionName + " 需要HEX文本");
            }
            if (normalized.length() % 2 != 0) {
                normalized = "0" + normalized;
            }
            if (normalized.length() > 16) {
                throw error("函数 " + functionName + " 最多支持8字节HEX值");
            }

            long value = 0L;
            for (int i = 0; i < normalized.length(); i += 2) {
                value = (value << 8) | Integer.parseInt(normalized.substring(i, i + 2), 16);
            }
            return (double) value;
        }

        private IllegalArgumentException error(String message) {
            int safeIndex = Math.max(0, Math.min(index, expression.length()));
            int start = Math.max(0, safeIndex - 10);
            int end = Math.min(expression.length(), safeIndex + 10);
            String snippet = expression.substring(start, end);
            return new IllegalArgumentException((message == null ? "表达式解析失败" : message)
                    + " @ 位置 " + safeIndex
                    + " 附近: `"
                    + snippet
                    + "`");
        }

        private String asString(Object value) {
            return stringifyValue(unwrapValue(value));
        }

        private boolean isNullish(Object value) {
            Object normalized = unwrapValue(value);
            if (normalized == null) {
                return true;
            }
            if (normalized instanceof String) {
                return ((String) normalized).trim().isEmpty();
            }
            if (normalized instanceof Collection) {
                return ((Collection<?>) normalized).isEmpty();
            }
            if (normalized instanceof JsonArray) {
                return ((JsonArray) normalized).size() == 0;
            }
            if (normalized instanceof JsonObject) {
                return !((JsonObject) normalized).entrySet().iterator().hasNext();
            }
            if (normalized.getClass().isArray()) {
                return Array.getLength(normalized) == 0;
            }
            return false;
        }

        private double sizeOf(Object value) {
            Object normalized = unwrapValue(value);
            if (normalized == null) {
                return 0;
            }
            if (normalized instanceof String) {
                return ((String) normalized).length();
            }
            if (normalized instanceof Collection) {
                return ((Collection<?>) normalized).size();
            }
            if (normalized instanceof Map) {
                return ((Map<?, ?>) normalized).size();
            }
            if (normalized instanceof JsonArray) {
                return ((JsonArray) normalized).size();
            }
            if (normalized instanceof JsonObject) {
                int count = 0;
                for (Map.Entry<String, JsonElement> ignored : ((JsonObject) normalized).entrySet()) {
                    count++;
                }
                return count;
            }
            if (normalized.getClass().isArray()) {
                return Array.getLength(normalized);
            }
            return asString(normalized).length();
        }

        private Object firstOf(Object value) {
            Object normalized = unwrapValue(value);
            if (normalized instanceof List && !((List<?>) normalized).isEmpty()) {
                return ((List<?>) normalized).get(0);
            }
            if (normalized instanceof Collection && !((Collection<?>) normalized).isEmpty()) {
                return ((Collection<?>) normalized).iterator().next();
            }
            if (normalized instanceof JsonArray && ((JsonArray) normalized).size() > 0) {
                return unwrapValue(((JsonArray) normalized).get(0));
            }
            if (normalized != null && normalized.getClass().isArray() && Array.getLength(normalized) > 0) {
                return Array.get(normalized, 0);
            }
            return null;
        }

        private Object lastOf(Object value) {
            Object normalized = unwrapValue(value);
            if (normalized instanceof List && !((List<?>) normalized).isEmpty()) {
                List<?> list = (List<?>) normalized;
                return list.get(list.size() - 1);
            }
            if (normalized instanceof JsonArray && ((JsonArray) normalized).size() > 0) {
                JsonArray array = (JsonArray) normalized;
                return unwrapValue(array.get(array.size() - 1));
            }
            if (normalized != null && normalized.getClass().isArray()) {
                int length = Array.getLength(normalized);
                if (length > 0) {
                    return Array.get(normalized, length - 1);
                }
            }
            if (normalized instanceof Collection && !((Collection<?>) normalized).isEmpty()) {
                Object last = null;
                for (Object entry : (Collection<?>) normalized) {
                    last = entry;
                }
                return last;
            }
            return null;
        }

        private List<Object> splitToList(String text, String delimiter) {
            List<Object> values = new ArrayList<>();
            String source = text == null ? "" : text;
            String splitBy = delimiter == null ? "" : delimiter;
            if (splitBy.isEmpty()) {
                for (int i = 0; i < source.length(); i++) {
                    values.add(String.valueOf(source.charAt(i)));
                }
                return values;
            }
            String[] parts = source.split(Pattern.quote(splitBy), -1);
            for (String part : parts) {
                values.add(part);
            }
            return values;
        }

        private String joinValues(Object value, String delimiter) {
            String joiner = delimiter == null ? "" : delimiter;
            Object normalized = unwrapValue(value);
            if (normalized instanceof Collection) {
                StringBuilder builder = new StringBuilder();
                for (Object entry : (Collection<?>) normalized) {
                    if (builder.length() > 0) {
                        builder.append(joiner);
                    }
                    builder.append(asString(entry));
                }
                return builder.toString();
            }
            if (normalized instanceof JsonArray) {
                StringBuilder builder = new StringBuilder();
                JsonArray array = (JsonArray) normalized;
                for (int i = 0; i < array.size(); i++) {
                    if (builder.length() > 0) {
                        builder.append(joiner);
                    }
                    builder.append(asString(array.get(i)));
                }
                return builder.toString();
            }
            if (normalized != null && normalized.getClass().isArray()) {
                StringBuilder builder = new StringBuilder();
                int length = Array.getLength(normalized);
                for (int i = 0; i < length; i++) {
                    if (builder.length() > 0) {
                        builder.append(joiner);
                    }
                    builder.append(asString(Array.get(normalized, i)));
                }
                return builder.toString();
            }
            return asString(normalized);
        }

        private boolean containsValue(Object container, Object needle, boolean ignoreCase) {
            Object normalized = unwrapValue(container);
            Object target = unwrapValue(needle);
            if (normalized instanceof Collection) {
                for (Object entry : (Collection<?>) normalized) {
                    if (compareDynamicValue(entry, target, ignoreCase)) {
                        return true;
                    }
                }
                return false;
            }
            if (normalized instanceof JsonArray) {
                JsonArray array = (JsonArray) normalized;
                for (int i = 0; i < array.size(); i++) {
                    if (compareDynamicValue(array.get(i), target, ignoreCase)) {
                        return true;
                    }
                }
                return false;
            }
            if (normalized != null && normalized.getClass().isArray()) {
                int length = Array.getLength(normalized);
                for (int i = 0; i < length; i++) {
                    if (compareDynamicValue(Array.get(normalized, i), target, ignoreCase)) {
                        return true;
                    }
                }
                return false;
            }
            String source = asString(normalized);
            String text = asString(target);
            if (ignoreCase) {
                return source.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
            }
            return source.contains(text);
        }

        private boolean compareDynamicValue(Object left, Object right, boolean ignoreCase) {
            Object normalizedLeft = unwrapValue(left);
            Object normalizedRight = unwrapValue(right);
            if (ignoreCase) {
                return asString(normalizedLeft).equalsIgnoreCase(asString(normalizedRight));
            }
            return compareEquals(normalizedLeft, normalizedRight);
        }

        private String substringValue(Object sourceValue, Object startValue, Object endValue) {
            String source = asString(sourceValue);
            int length = source.length();
            int start = normalizeCollectionIndex((int) Math.round(requireNumber("substring", startValue)), length);
            if (start == Integer.MAX_VALUE) {
                return "";
            }
            int end = length;
            if (endValue != null) {
                end = normalizeCollectionIndex((int) Math.round(requireNumber("substring", endValue)), length);
                if (end == Integer.MAX_VALUE) {
                    end = 0;
                }
            }
            start = Math.max(0, Math.min(start, length));
            end = Math.max(0, Math.min(end, length));
            if (end < start) {
                int temp = start;
                start = end;
                end = temp;
            }
            return source.substring(start, end);
        }

        private double sumOf(List<Object> args) {
            double total = 0.0D;
            int count = 0;
            for (Object arg : flattenArgs(args)) {
                total += requireNumber("sum", arg);
                count++;
            }
            return count <= 0 ? 0.0D : total;
        }

        private double averageOf(List<Object> args) {
            double total = 0.0D;
            int count = 0;
            for (Object arg : flattenArgs(args)) {
                total += requireNumber("avg", arg);
                count++;
            }
            return count <= 0 ? 0.0D : total / count;
        }

        private List<Object> flattenArgs(List<Object> args) {
            List<Object> flat = new ArrayList<>();
            if (args == null) {
                return flat;
            }
            for (Object arg : args) {
                Object normalized = unwrapValue(arg);
                if (normalized instanceof Collection) {
                    flat.addAll((Collection<?>) normalized);
                } else if (normalized instanceof JsonArray) {
                    JsonArray array = (JsonArray) normalized;
                    for (int i = 0; i < array.size(); i++) {
                        flat.add(unwrapValue(array.get(i)));
                    }
                } else if (normalized != null && normalized.getClass().isArray()) {
                    int length = Array.getLength(normalized);
                    for (int i = 0; i < length; i++) {
                        flat.add(unwrapValue(Array.get(normalized, i)));
                    }
                } else {
                    flat.add(normalized);
                }
            }
            return flat;
        }

        private double minOf(List<Object> args) {
            double best = Double.POSITIVE_INFINITY;
            for (Object arg : args) {
                best = Math.min(best, requireNumber("min", arg));
            }
            return best == Double.POSITIVE_INFINITY ? 0.0D : best;
        }

        private double maxOf(List<Object> args) {
            double best = Double.NEGATIVE_INFINITY;
            for (Object arg : args) {
                best = Math.max(best, requireNumber("max", arg));
            }
            return best == Double.NEGATIVE_INFINITY ? 0.0D : best;
        }
    }
}
