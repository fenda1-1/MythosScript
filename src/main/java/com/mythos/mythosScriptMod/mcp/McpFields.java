package com.mythos.mythosScriptMod.mcp;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/** Explicit class allowlists use this codec; no client-supplied class loading or method invocation. */
public final class McpFields {
    private McpFields() {}
    public static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        Class<?> holder = holderType(type);
        for (Field f : type.getFields()) if (Modifier.isStatic(f.getModifiers()) && writable(f)) result.add(f);
        if (holder != null) for (Field f : holder.getFields()) if (!Modifier.isStatic(f.getModifiers()) && writable(f)) result.add(f);
        result.sort(Comparator.comparing(Field::getName));
        return result;
    }
    private static boolean writable(Field f) {
        return !Modifier.isFinal(f.getModifiers()) && !f.isSynthetic()
                && (f.getType().isPrimitive() || f.getType() == String.class
                    || f.getType().isEnum() || Collection.class.isAssignableFrom(f.getType())
                    || Map.class.isAssignableFrom(f.getType()));
    }
    public static JsonObject snapshot(Class<?> type) {
        JsonObject values = new JsonObject();
        for (Field f : fields(type)) try { values.add(f.getName(), GSON.toJsonTree(f.get(target(f)), f.getGenericType())); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        return values;
    }
    public static Map<Field, Object> decode(Class<?> type, JsonObject values) {
        Map<String, Field> allowed = new HashMap<>();
        for (Field f : fields(type)) allowed.put(f.getName(), f);
        Map<Field, Object> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : values.entrySet()) {
            Field f = allowed.get(e.getKey());
            if (f == null) throw new IllegalArgumentException("Unknown writable field: " + e.getKey());
            JsonElement v = e.getValue();
            Class<?> t = f.getType();
            if (v.isJsonNull()) {
                // snapshot() may store null for uninitialized String/collection fields; restore them as null.
                if (t.isPrimitive()) throw new IllegalArgumentException("Null is not supported: " + f.getName());
                decoded.put(f, null);
                continue;
            }
            if ((t == boolean.class && (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()))
                    || (t == String.class && (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())))
                throw new IllegalArgumentException("Wrong type for " + f.getName());
            if (t.isPrimitive() && t != boolean.class) {
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(v.getAsDouble())) throw new IllegalArgumentException("Expected finite number: " + f.getName());
                if (t == int.class) v.getAsBigDecimal().intValueExact();
                if (t == long.class) v.getAsBigDecimal().longValueExact();
            }
            Object value = GSON.fromJson(v, f.getGenericType());
            if ((value instanceof Float && !Float.isFinite((Float)value))
                    || (value instanceof Double && !Double.isFinite((Double)value)))
                throw new IllegalArgumentException("Number overflow: " + f.getName());
            decoded.put(f, value);
        }
        return decoded;
    }
    public static void apply(Map<Field, Object> values) {
        for (Map.Entry<Field, Object> e : values.entrySet()) try { e.getKey().set(target(e.getKey()), e.getValue()); }
        catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
    }
    // 配置改成按会话（owner）存的实例字段后，模块名仍是外层类，字段读写落到当前会话的配置对象上：
    // 杀戮光环是 KillAuraHandler.AuraConfig，其余模块是各自的 OwnerConfig，都由外层的 static cfg() 取得。
    private static Class<?> holderType(Class<?> type) {
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("AuraConfig") || nested.getSimpleName().equals("OwnerConfig")) return nested;
        }
        return null;
    }
    private static Object target(Field f) {
        if (Modifier.isStatic(f.getModifiers())) return null;
        try {
            return f.getDeclaringClass().getEnclosingClass().getMethod("cfg").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
    public static JsonArray describe(Class<?> type) {
        JsonArray result = new JsonArray();
        JsonObject current = snapshot(type);
        for (Field f : fields(type))
            result.add(object("name", f.getName(), "javaType", f.getGenericType().getTypeName(),
                    "value", current.get(f.getName()), "schema", schema(f.getGenericType(), new HashSet<Type>())));
        return result;
    }
    private static JsonObject schema(Type type, Set<Type> seen) {
        if (!seen.add(type)) return object("type", "object");
        if (type instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) type;
            if (Collection.class.isAssignableFrom((Class<?>)p.getRawType()))
                return object("type", "array", "items", schema(p.getActualTypeArguments()[0], seen));
            return object("type", "object");
        }
        if (!(type instanceof Class)) return object("type", "object");
        Class<?> c = (Class<?>)type;
        if (c == String.class || c == char.class) return object("type", "string");
        if (c == boolean.class || c == Boolean.class) return object("type", "boolean");
        if (c.isPrimitive() || Number.class.isAssignableFrom(c))
            return object("type", c == float.class || c == double.class || c == Float.class || c == Double.class ? "number" : "integer");
        if (c.isEnum()) return object("type", "string", "enum", c.getEnumConstants());
        JsonObject properties = new JsonObject();
        for (Field f : c.getFields()) if (!Modifier.isStatic(f.getModifiers()))
            properties.add(f.getName(), schema(f.getGenericType(), new HashSet<Type>(seen)));
        return object("type", "object", "properties", properties);
    }
}
