package com.mythos.mythosScriptMod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * Gson 2.8.0 keeps JsonElement.deepCopy() package-private, so deep copies in
 * this codebase used to go through {@code parse(toString())} — which builds a
 * full JSON string on the heap and caused OutOfMemoryError when actions carry
 * large recorded-packet payloads. This helper copies recursively in-place with
 * no serialization. ponytail: JsonElement.deepCopy() exists but is invisible;
 * delete this if the Gson dep is ever >= 2.8.2 where it became public.
 */
public final class JsonCopy {

    private JsonCopy() {
    }

    public static JsonElement deepCopy(JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonObject()) {
            return deepCopyObject(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            return deepCopyArray(element.getAsJsonArray());
        }
        return element; // JsonPrimitive and JsonNull are immutable
    }

    public static JsonObject deepCopyObject(JsonObject source) {
        JsonObject result = new JsonObject();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            result.add(entry.getKey(), deepCopy(entry.getValue()));
        }
        return result;
    }

    public static JsonArray deepCopyArray(JsonArray source) {
        JsonArray result = new JsonArray();
        if (source == null) {
            return result;
        }
        for (JsonElement element : source) {
            result.add(deepCopy(element));
        }
        return result;
    }
}
