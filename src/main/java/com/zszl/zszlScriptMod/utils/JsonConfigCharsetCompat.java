package com.zszl.zszlScriptMod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonConfigCharsetCompat {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset LEGACY_CHINESE_CHARSET = Charset.forName("GB18030");

    private JsonConfigCharsetCompat() {
    }

    public static final class ReadResult {
        private final JsonObject root;
        private final boolean legacyCharsetUsed;

        private ReadResult(JsonObject root, boolean legacyCharsetUsed) {
            this.root = root;
            this.legacyCharsetUsed = legacyCharsetUsed;
        }

        public JsonObject getRoot() {
            return root;
        }

        public boolean usedLegacyCharset() {
            return legacyCharsetUsed;
        }
    }

    public static ReadResult readJsonObject(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        JsonObject utf8Object = tryParse(bytes, UTF8);
        JsonObject legacyObject = tryParse(bytes, LEGACY_CHINESE_CHARSET);
        if (utf8Object == null && legacyObject == null) {
            throw new IOException("无法解析 JSON: " + path);
        }
        if (utf8Object == null) {
            return new ReadResult(legacyObject, true);
        }
        if (legacyObject == null) {
            return new ReadResult(utf8Object, false);
        }

        int utf8Suspicious = countLikelyMojibakeStrings(utf8Object);
        int legacySuspicious = countLikelyMojibakeStrings(legacyObject);
        if (legacySuspicious < utf8Suspicious) {
            return new ReadResult(legacyObject, true);
        }
        return new ReadResult(utf8Object, false);
    }

    public static void writeJsonObject(Path path, JsonObject root) throws IOException {
        Path parent = path == null ? null : path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(path, root.toString().getBytes(UTF8));
    }

    public static boolean looksLikeMojibake(String text) {
        String safeText = text == null ? "" : text.trim();
        if (safeText.isEmpty()) {
            return false;
        }
        if (safeText.indexOf('\uFFFD') >= 0 || safeText.contains("锟")) {
            return true;
        }

        boolean hasCjk = false;
        boolean hasSuspiciousNonAscii = false;
        for (int i = 0; i < safeText.length(); i++) {
            char ch = safeText.charAt(i);
            if (ch <= 0x7F) {
                continue;
            }
            if (isCjk(ch)) {
                hasCjk = true;
                continue;
            }
            hasSuspiciousNonAscii = true;
        }
        return !hasCjk && hasSuspiciousNonAscii;
    }

    private static JsonObject tryParse(byte[] bytes, Charset charset) {
        if (bytes == null || charset == null) {
            return null;
        }
        try {
            String text = new String(bytes, charset);
            JsonElement root = new JsonParser().parse(new StringReader(text));
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int countLikelyMojibakeStrings(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().isString() && looksLikeMojibake(element.getAsString()) ? 1 : 0;
        }
        if (element.isJsonArray()) {
            int count = 0;
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                count += countLikelyMojibakeStrings(child);
            }
            return count;
        }
        if (element.isJsonObject()) {
            int count = 0;
            JsonObject object = element.getAsJsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
                count += countLikelyMojibakeStrings(entry.getValue());
            }
            return count;
        }
        return 0;
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }
}
