package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** JSON/text file access confined to the mod configuration tree; writes are atomic. */
public final class McpConfigStore {
    private final Path root;
    public McpConfigStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    Path resolve(String name) throws IOException {
        Path relative = Paths.get(name);
        Path target = root.resolve(relative).normalize();
        if (relative.isAbsolute() || target.equals(root) || !target.startsWith(root))
            throw new IllegalArgumentException("Expected a file path relative to the config root");
        Path checked = root;
        if (Files.isSymbolicLink(root)) throw new IOException("Config root is a symbolic link");
        for (Path part : root.relativize(target)) {
            checked = checked.resolve(part);
            if (Files.isSymbolicLink(checked)) throw new IOException("Symbolic links are not allowed");
            if (Files.exists(checked) && Files.exists(root) && !checked.toRealPath().startsWith(root.toRealPath()))
                throw new IOException("Path escapes config root");
        }
        return target;
    }

    public synchronized JsonObject call(JsonObject p) throws Exception {
        String operation = string(p, "operation");
        if ("list".equals(operation)) {
            JsonArray files = new JsonArray();
            if (Files.exists(root)) try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(x -> Files.isRegularFile(x, LinkOption.NOFOLLOW_LINKS))
                    .sorted().forEach(x -> {
                        String name = root.relativize(x).toString().replace('\\', '/');
                        if (!"mcp.token".equals(name)) files.add(name);
                    });
            }
            return object("root", root.toString(), "files", files);
        }
        String name = string(p, "path");
        Path file = resolve(name);
        if (file.equals(root.resolve("mcp.token"))) throw new IllegalArgumentException("Use the local token file directly for connection credentials");
        if ("read".equals(operation)) return read(file);
        if (!Arrays.asList("write", "patch", "delete").contains(operation))
            throw new IllegalArgumentException("Unknown config operation: " + operation);
        byte[] old = Files.exists(file) ? Files.readAllBytes(file) : null;
        if (p.has("expectedHash") && !string(p, "expectedHash").equals(old == null ? "missing" : hash(old)))
            throw new IllegalStateException("Config changed; read again before retrying");
        if ("delete".equals(operation)) {
            Files.delete(file);
            return object("deleted", name, "appliedToRuntime", false);
        }
        byte[] bytes;
        if ("patch".equals(operation)) {
            if (old == null) throw new IOException("Config file not found");
            JsonElement tree = new JsonParser().parse(new String(old, StandardCharsets.UTF_8));
            tree = patch(tree, p.getAsJsonArray("patch"));
            bytes = GSON.toJson(tree).getBytes(StandardCharsets.UTF_8);
        } else if (p.has("text")) {
            bytes = string(p, "text").getBytes(StandardCharsets.UTF_8);
        } else {
            if (!p.has("value")) throw new IllegalArgumentException("write requires value or text");
            bytes = GSON.toJson(p.get("value")).getBytes(StandardCharsets.UTF_8);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".json"))
            new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), ".mcp-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
        return object("path", name, "hash", hash(bytes), "appliedToRuntime", false,
                "next", "Call mythos_modules reload for the owning module to apply persisted changes");
    }
    private JsonObject read(Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        String text = new String(data, StandardCharsets.UTF_8);
        JsonObject result = object("path", root.relativize(file).toString(), "hash", hash(data), "text", text);
        try { result.add("value", new JsonParser().parse(text)); } catch (RuntimeException ignored) {}
        return result;
    }
    static String hash(byte[] data) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) out.append(String.format("%02x", b & 255));
        return out.toString();
    }
    /** RFC 6902 add/remove/replace/test with RFC 6901 pointers; no partial file writes. */
    public static JsonElement patch(JsonElement source, JsonArray operations) {
        if (operations == null) throw new IllegalArgumentException("patch array required");
        JsonElement tree = new JsonParser().parse(source.toString());
        for (JsonElement entry : operations) {
            JsonObject op = entry.getAsJsonObject();
            String action = string(op, "op"), path = string(op, "path");
            if (!Arrays.asList("add", "replace", "remove", "test").contains(action))
                throw new IllegalArgumentException("Supported patch operations: add, replace, remove, test");
            if (!"remove".equals(action) && !op.has("value")) throw new IllegalArgumentException("patch value required");
            if (path.isEmpty()) {
                if ("test".equals(action)) { if (!tree.equals(op.get("value"))) throw new IllegalStateException("Patch test failed"); }
                else tree = "remove".equals(action) ? JsonNull.INSTANCE : op.get("value");
                continue;
            }
            if (!path.startsWith("/")) throw new IllegalArgumentException("Expected JSON pointer");
            String[] keys = path.substring(1).split("/", -1);
            JsonElement parent = tree;
            for (int i = 0; i < keys.length - 1; i++) parent = child(parent, unescape(keys[i]));
            String key = unescape(keys[keys.length - 1]);
            if ("test".equals(action)) {
                if (!child(parent, key).equals(op.get("value"))) throw new IllegalStateException("Patch test failed: " + path);
            } else if (parent.isJsonObject()) {
                if (!"add".equals(action) && !parent.getAsJsonObject().has(key)) throw new IllegalArgumentException("Missing pointer: " + path);
                if ("remove".equals(action)) parent.getAsJsonObject().remove(key);
                else parent.getAsJsonObject().add(key, op.get("value"));
            } else if (parent.isJsonArray()) {
                JsonArray a = parent.getAsJsonArray();
                int index = "-".equals(key) && "add".equals(action) ? a.size() : index(key);
                if (index < 0 || index > a.size() || (!"add".equals(action) && index == a.size())) throw new IllegalArgumentException("Array index out of bounds");
                if ("remove".equals(action)) a.remove(index);
                else if ("replace".equals(action)) a.set(index, op.get("value"));
                else {
                    a.add(JsonNull.INSTANCE);
                    for (int i = a.size() - 1; i > index; i--) a.set(i, a.get(i - 1));
                    a.set(index, op.get("value"));
                }
            } else throw new IllegalArgumentException("Pointer parent is not an object or array");
        }
        return tree;
    }
    private static int index(String key) {
        if (!key.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid array index");
        return Integer.parseInt(key);
    }
    private static JsonElement child(JsonElement parent, String key) {
        JsonElement value = parent.isJsonObject() ? parent.getAsJsonObject().get(key) : parent.getAsJsonArray().get(index(key));
        if (value == null) throw new IllegalArgumentException("Missing pointer component: " + key);
        return value;
    }
    private static String unescape(String key) {
        if (key.matches(".*~(?![01]).*")) throw new IllegalArgumentException("Invalid JSON pointer escape");
        return key.replace("~1", "/").replace("~0", "~");
    }
}
