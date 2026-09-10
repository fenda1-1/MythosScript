package com.zszl.zszlScriptMod.gui.modern;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Captures the small amount of view state that should survive reopening a
 * modern page. Configuration values and drafts deliberately stay out of this
 * snapshot: only scroll positions and grouped page selection/expansion are
 * eligible.
 */
public final class ModernPageStateRecorder {

    private static final int MAX_DEPTH = 6;
    private static final String GUI_PACKAGE = "com.zszl.zszlScriptMod.gui";

    private ModernPageStateRecorder() {
    }

    public static JsonObject capture(Object root) {
        JsonObject result = new JsonObject();
        visitCapture(root, "", 0, identitySet(), result);
        return result;
    }

    public static void restore(Object root, JsonObject state) {
        if (root == null || state == null) {
            return;
        }
        visitRestore(root, "", 0, identitySet(), state);
    }

    private static void visitCapture(Object value, String prefix, int depth, Set<Object> visited,
            JsonObject result) {
        if (!canVisit(value, depth) || !visited.add(value)) {
            return;
        }
        for (Field field : fields(value.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            Object child = read(field, value);
            if (child == null) {
                continue;
            }
            String path = path(prefix, field.getName());
            if (writeScalar(result, path, field.getName(), child)
                    || writeCollection(result, path, field.getName(), child)
                    || writeArray(result, path, field.getName(), child)
                    || writeMap(result, path, field.getName(), child)) {
                continue;
            }
            if (shouldRecurse(child, field.getName())) {
                visitCapture(child, path, depth + 1, visited, result);
            }
        }
    }

    private static void visitRestore(Object value, String prefix, int depth, Set<Object> visited,
            JsonObject state) {
        if (!canVisit(value, depth) || !visited.add(value)) {
            return;
        }
        for (Field field : fields(value.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            Object child = read(field, value);
            String path = path(prefix, field.getName());
            JsonElement stored = state.get(path);
            if (stored != null && !Modifier.isFinal(field.getModifiers())) {
                restoreValue(field, value, stored);
            } else if (stored != null && child != null) {
                restoreCollectionOrMap(child, stored);
            }
            if (child != null && shouldRecurse(child, field.getName())) {
                visitRestore(child, path, depth + 1, visited, state);
            }
        }
    }

    private static boolean writeScalar(JsonObject result, String path, String name, Object value) {
        if (!isStateName(name) || !isScalar(value)) {
            return false;
        }
        if (value instanceof Enum) {
            result.addProperty(path, ((Enum<?>) value).name());
        } else if (value instanceof Character) {
            result.addProperty(path, String.valueOf(value));
        } else if (value instanceof String) {
            result.addProperty(path, (String) value);
        } else if (value instanceof Boolean) {
            result.addProperty(path, (Boolean) value);
        } else if (value instanceof Number) {
            result.addProperty(path, (Number) value);
        }
        return true;
    }

    private static boolean writeCollection(JsonObject result, String path, String name, Object value) {
        if (!isCollectionStateName(name) || !(value instanceof Collection)) {
            return false;
        }
        JsonArray array = new JsonArray();
        for (Object entry : (Collection<?>) value) {
            if (entry == null || !(entry instanceof String || entry instanceof Enum)) {
                return false;
            }
            addPrimitive(array, entry);
        }
        result.add(path, array);
        return true;
    }

    private static boolean writeArray(JsonObject result, String path, String name, Object value) {
        if (!isCollectionStateName(name) || value == null || !value.getClass().isArray()) {
            return false;
        }
        JsonArray array = new JsonArray();
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            Object entry = Array.get(value, i);
            if (entry == null || !isScalar(entry)) {
                return false;
            }
            addPrimitive(array, entry);
        }
        result.add(path, array);
        return true;
    }

    private static boolean writeMap(JsonObject result, String path, String name, Object value) {
        if (!isCollectionStateName(name) || !(value instanceof Map)) {
            return false;
        }
        JsonObject object = new JsonObject();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !isScalar(entry.getKey()) || !isScalar(entry.getValue())) {
                return false;
            }
            String key = String.valueOf(entry.getKey());
            addPrimitive(object, key, entry.getValue());
        }
        result.add(path, object);
        return true;
    }

    private static void restoreValue(Field field, Object owner, JsonElement stored) {
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)
                    || type.isArray()) {
                Object current = field.get(owner);
                restoreCollectionOrMap(current, stored);
                return;
            }
            if (type == boolean.class || type == Boolean.class) {
                field.set(owner, Boolean.valueOf(stored.getAsBoolean()));
            } else if (type == int.class || type == Integer.class) {
                field.set(owner, Integer.valueOf(stored.getAsInt()));
            } else if (type == long.class || type == Long.class) {
                field.set(owner, Long.valueOf(stored.getAsLong()));
            } else if (type == float.class || type == Float.class) {
                field.set(owner, Float.valueOf(stored.getAsFloat()));
            } else if (type == double.class || type == Double.class) {
                field.set(owner, Double.valueOf(stored.getAsDouble()));
            } else if (type == String.class) {
                field.set(owner, stored.getAsString());
            } else if (type.isEnum()) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Object constant = Enum.valueOf((Class<? extends Enum>) type, stored.getAsString());
                field.set(owner, constant);
            }
        } catch (Exception ignored) {
            // A page can evolve independently; an old snapshot must never make
            // the page fail to open.
        }
    }

    private static void restoreCollectionOrMap(Object current, JsonElement stored) {
        if (current != null && current.getClass().isArray() && stored.isJsonArray()) {
            try {
                int length = Math.min(Array.getLength(current), stored.getAsJsonArray().size());
                for (int i = 0; i < length; i++) {
                    Array.set(current, i, convertForType(primitiveValue(stored.getAsJsonArray().get(i).getAsJsonPrimitive()),
                            current.getClass().getComponentType()));
                }
            } catch (Exception ignored) {
            }
        } else if (current instanceof Collection && stored.isJsonArray()) {
            Collection<Object> collection = castCollection(current);
            try {
                collection.clear();
                for (JsonElement element : stored.getAsJsonArray()) {
                    collection.add(element.isJsonPrimitive() ? primitiveValue(element.getAsJsonPrimitive()) : null);
                }
            } catch (Exception ignored) {
            }
        } else if (current instanceof Map && stored.isJsonObject()) {
            Map<Object, Object> map = castMap(current);
            try {
                map.clear();
                for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject().entrySet()) {
                    map.put(parseMapKey(entry.getKey()), entry.getValue().isJsonPrimitive()
                            ? primitiveValue(entry.getValue().getAsJsonPrimitive()) : null);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Object convertForType(Object value, Class<?> type) {
        if (value == null || type == null || !type.isPrimitive()) return value;
        if (type == boolean.class) return Boolean.valueOf(String.valueOf(value));
        if (type == int.class) return Integer.valueOf(((Number) value).intValue());
        if (type == long.class) return Long.valueOf(((Number) value).longValue());
        if (type == float.class) return Float.valueOf(((Number) value).floatValue());
        if (type == double.class) return Double.valueOf(((Number) value).doubleValue());
        if (type == byte.class) return Byte.valueOf(((Number) value).byteValue());
        if (type == short.class) return Short.valueOf(((Number) value).shortValue());
        if (type == char.class) return Character.valueOf(String.valueOf(value).charAt(0));
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> castCollection(Object value) {
        return (Collection<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(Object value) {
        return (Map<Object, Object>) value;
    }

    private static Object parseMapKey(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static Object primitiveValue(JsonPrimitive primitive) {
        if (primitive.isBoolean()) return Boolean.valueOf(primitive.getAsBoolean());
        if (primitive.isNumber()) {
            String value = primitive.getAsString();
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ignored) {
                try {
                    return Double.valueOf(value);
                } catch (NumberFormatException ignoredAgain) {
                    return value;
                }
            }
        }
        return primitive.getAsString();
    }

    private static void addPrimitive(JsonArray array, Object value) {
        if (value instanceof Enum) array.add(((Enum<?>) value).name());
        else if (value instanceof Boolean) array.add((Boolean) value);
        else if (value instanceof Number) array.add((Number) value);
        else array.add(String.valueOf(value));
    }

    private static void addPrimitive(JsonObject object, String key, Object value) {
        if (value instanceof Enum) object.addProperty(key, ((Enum<?>) value).name());
        else if (value instanceof Boolean) object.addProperty(key, (Boolean) value);
        else if (value instanceof Number) object.addProperty(key, (Number) value);
        else object.addProperty(key, String.valueOf(value));
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum;
    }

    private static boolean isStateName(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("max") || lower.startsWith("min") || lower.startsWith("pending")
                || lower.startsWith("last") || lower.contains("drag") || lower.contains("bounds")
                || lower.contains("count") || lower.contains("dirty") || lower.contains("initialized")
                || lower.equals("staterecordingexpanded") || lower.equals("editorsection")) {
            return false;
        }
        if (lower.contains("scroll") || lower.contains("offset") || lower.contains("collapsed")
                || lower.contains("expanded")) {
            return true;
        }
        if ("selected".equals(lower) || lower.contains("selectedsection")
                || lower.contains("selectedcategory") || lower.contains("selectedsubcategory")
                || lower.contains("selectedgroup") || lower.contains("selectedtab")
                || lower.contains("selectedpage") || lower.contains("selectedsession")) {
            return true;
        }
        if (lower.contains("active") || lower.contains("current")) {
            return lower.contains("group") || lower.contains("category") || lower.contains("subcategory")
                    || lower.contains("section") || lower.contains("page") || lower.contains("tab");
        }
        return "page".equals(lower) || lower.endsWith("page") || "column".equals(lower);
    }

    private static boolean isCollectionStateName(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("scroll") || lower.contains("offset") || lower.contains("collapsed")
                || lower.contains("expanded");
    }

    private static boolean shouldRecurse(Object value, String fieldName) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Enum || value instanceof Collection || value instanceof Map
                || value instanceof Class || value.getClass().isArray()) {
            return false;
        }
        String typeName = value.getClass().getName();
        if (!typeName.startsWith(GUI_PACKAGE)) {
            return false;
        }
        String simple = value.getClass().getSimpleName();
        if ("ModernHoverScrollbar".equals(simple) || "ModernDropdown".equals(simple)
                || "GuiTextField".equals(simple) || "ModernTextField".equals(simple)) {
            return false;
        }
        String lower = fieldName == null ? "" : fieldName.toLowerCase(java.util.Locale.ROOT);
        return !lower.equals("definition") && !lower.equals("session") && !lower.equals("input")
                && !lower.equals("views") && !lower.equals("items") && !lower.equals("sections")
                && !lower.equals("modernhost");
    }

    private static boolean canVisit(Object value, int depth) {
        return value != null && depth <= MAX_DEPTH;
    }

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Collections.addAll(result, current.getDeclaredFields());
        }
        return result;
    }

    private static Set<Object> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    }

    private static String path(String prefix, String field) {
        return prefix == null || prefix.isEmpty() ? field : prefix + "." + field;
    }
}
