package com.zszl.zszlScriptMod.system;

import com.google.gson.*;
import java.io.*;
import java.util.*;

/** Optional lossless JSON token stream with a dictionary shared across all files. */
final class ProfileSharePacking {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> strings = new ArrayList<>();
    private int budget = ProfileShareWire.LIMIT;

    static byte[] transform(byte[] source, boolean encode) throws IOException {
        return new ProfileSharePacking().files(source, encode);
    }

    private byte[] files(byte[] source, boolean encode) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(source);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int version = in.read();
        if (version != 2 && version != 3) throw new IOException("Unsupported payload");
        out.write(version);
        int count = ProfileShareWire.number(in);
        if (count < 1 || count > 4096) throw new IOException("Invalid file count");
        ProfileShareWire.number(out, count);
        for (int i = 0; i < count; i++) {
            int id = ProfileShareWire.number(in);
            ProfileShareWire.number(out, id);
            if (id == 0) ProfileShareWire.string(out, ProfileShareWire.string(in));
            if (version == 3) ProfileShareWire.number(out, ProfileShareWire.number(in));
            if (encode) {
                String content = ProfileShareWire.string(in);
                JsonElement json = null;
                try {
                    JsonElement parsed = new JsonParser().parse(content);
                    if (GSON.toJson(parsed).equals(content)) json = parsed;
                } catch (JsonParseException ignored) { }
                if (json == null) {
                    out.write(0);
                    ProfileShareWire.string(out, content);
                } else {
                    out.write(1);
                    writeJson(out, json, 0);
                }
            } else {
                int mode = in.read();
                if (mode == 0) ProfileShareWire.string(out, ProfileShareWire.string(in));
                else if (mode == 1) ProfileShareWire.string(out, GSON.toJson(readJson(in, 0)));
                else throw new IOException("Invalid content mode");
            }
            if (out.size() > ProfileShareWire.LIMIT) throw new IOException("Payload too large");
        }
        if (in.available() != 0) throw new IOException("Unexpected payload suffix");
        return out.toByteArray();
    }

    private void writeString(OutputStream out, String text) throws IOException {
        Integer id = ids.get(text);
        if (id != null) {
            ProfileShareWire.number(out, id + 1);
        } else {
            ProfileShareWire.number(out, 0);
            ProfileShareWire.string(out, text);
            if (ids.size() < 65536) ids.put(text, ids.size());
        }
    }

    private String readString(InputStream in) throws IOException {
        int id = ProfileShareWire.number(in);
        String value;
        if (id == 0) {
            value = ProfileShareWire.string(in);
            if (strings.size() < 65536) strings.add(value);
        } else {
            if (id > strings.size()) throw new IOException("Invalid dictionary reference");
            value = strings.get(id - 1);
        }
        budget -= value.length();
        if (budget < 0) throw new IOException("Expanded JSON too large");
        return value;
    }

    private void writeJson(OutputStream out, JsonElement value, int depth) throws IOException {
        if (depth > 128) throw new IOException("JSON too deep");
        if (value.isJsonNull()) out.write(0);
        else if (value.isJsonObject()) {
            out.write(3);
            ProfileShareWire.number(out, value.getAsJsonObject().size());
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                writeString(out, entry.getKey());
                writeJson(out, entry.getValue(), depth + 1);
            }
        } else if (value.isJsonArray()) {
            out.write(4);
            ProfileShareWire.number(out, value.getAsJsonArray().size());
            for (JsonElement child : value.getAsJsonArray()) writeJson(out, child, depth + 1);
        } else {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) out.write(primitive.getAsBoolean() ? 2 : 1);
            else {
                out.write(primitive.isNumber() ? 6 : 5);
                writeString(out, primitive.getAsString());
            }
        }
    }

    private JsonElement readJson(InputStream in, int depth) throws IOException {
        if (depth > 128 || --budget < 0) throw new IOException("Expanded JSON too large");
        int tag = in.read();
        if (tag == 0) return JsonNull.INSTANCE;
        if (tag == 1 || tag == 2) return new JsonPrimitive(tag == 2);
        if (tag == 5) return new JsonPrimitive(readString(in));
        if (tag == 6) {
            JsonElement number = new JsonParser().parse(readString(in));
            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber())
                throw new IOException("Invalid number");
            return number;
        }
        if (tag != 3 && tag != 4) throw new IOException("Invalid JSON tag");
        int size = ProfileShareWire.number(in);
        if (size > budget) throw new IOException("Expanded JSON too large");
        if (tag == 3) {
            JsonObject object = new JsonObject();
            for (int i = 0; i < size; i++) {
                String key = readString(in);
                if (object.has(key)) throw new IOException("Duplicate JSON key");
                object.add(key, readJson(in, depth + 1));
            }
            return object;
        }
        JsonArray array = new JsonArray();
        for (int i = 0; i < size; i++) array.add(readJson(in, depth + 1));
        return array;
    }
}
