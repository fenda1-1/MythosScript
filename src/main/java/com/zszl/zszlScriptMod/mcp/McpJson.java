package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;

public final class McpJson {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private McpJson() {}
    public static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
    public static JsonObject object(Object... entries) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < entries.length; i += 2)
            result.add((String) entries[i], GSON.toJsonTree(entries[i + 1]));
        return result;
    }
    public static String string(JsonObject p, String key) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive() || !p.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Expected string: " + key);
        return p.get(key).getAsString();
    }
    public static int integer(JsonObject p, String key, int fallback, int min, int max) {
        if (!p.has(key)) return fallback;
        try {
            int n = p.get(key).getAsBigDecimal().intValueExact();
            if (n < min || n > max) throw new ArithmeticException();
            return n;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid integer " + key + " (" + min + ".." + max + ")"); }
    }
    public static boolean bool(JsonObject p, String key, boolean fallback) {
        if (!p.has(key)) return fallback;
        if (!p.get(key).isJsonPrimitive() || !p.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("Expected boolean: " + key);
        return p.get(key).getAsBoolean();
    }
}
