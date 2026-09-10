package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.zszl.zszlScriptMod.mcp.McpJson.object;
import static com.zszl.zszlScriptMod.mcp.McpJson.string;

/**
 * Server-scoped Markdown notebook shared by the Tools menu and MCP.
 * Singleplayer / integrated worlds use {@code singleplayer.md}; multiplayer uses a sanitized address.
 */
public final class McpNotes {
    public static final McpNotes INSTANCE = new McpNotes();
    public static final int MAX_TEXT_LENGTH = 32767;
    public static final String SINGLEPLAYER = "singleplayer";

    public interface Live {
        String serverKey();
        String text();
        void replace(String text);
    }

    private final Path directory;
    private final Supplier<String> currentServer;
    private Live live;

    public McpNotes() {
        this(Paths.get(ModConfig.CONFIG_DIR, "notes"), McpNotes::detectCurrentServer);
    }

    McpNotes(Path directory, Supplier<String> currentServer) {
        this.directory = directory.toAbsolutePath().normalize();
        this.currentServer = currentServer;
    }

    public synchronized void attach(Live session) {
        live = session;
    }

    public synchronized void detach(Live session) {
        if (live == session) live = null;
    }

    public static String detectCurrentServer() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.isIntegratedServerRunning()) return SINGLEPLAYER;
            ServerData data = mc.getCurrentServerData();
            if (data != null && data.serverIP != null && !data.serverIP.trim().isEmpty()) {
                return sanitizeServerKey(data.serverIP);
            }
        } catch (Throwable ignored) {
            // Headless tests and an uninitialized client fall back to the singleplayer notebook.
        }
        return SINGLEPLAYER;
    }

    public static String sanitizeServerKey(String raw) {
        if (raw == null) return SINGLEPLAYER;
        String key = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        return key.isEmpty() ? SINGLEPLAYER : key;
    }

    public String currentServer() {
        return sanitizeServerKey(currentServer.get());
    }

    public Path fileFor(String server) {
        String key = sanitizeServerKey(server);
        Path file = directory.resolve(key + ".md").normalize();
        if (!file.startsWith(directory) || !file.getFileName().toString().equals(key + ".md")) {
            throw new IllegalArgumentException("Invalid notebook server: " + server);
        }
        return file;
    }

    public synchronized String diskText(String server) throws IOException {
        Path file = fileFor(server);
        if (!Files.exists(file)) return "";
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    public synchronized JsonObject call(JsonObject p) throws Exception {
        String operation = p.has("operation") ? string(p, "operation") : "read";
        if ("list".equals(operation)) return list();
        String server = p.has("server") ? sanitizeServerKey(string(p, "server")) : currentServer();
        if ("read".equals(operation)) return read(server);
        if ("delete".equals(operation)) {
            JsonObject previous = read(server);
            return delete(server, p.has("expectedHash") ? string(p, "expectedHash") : null, previous);
        }
        if ("rename".equals(operation)) {
            if (!p.has("newServer")) throw new IllegalArgumentException("newServer required");
            String newServer = sanitizeServerKey(string(p, "newServer"));
            JsonObject previous = read(server);
            return rename(server, newServer, p.has("expectedHash") ? string(p, "expectedHash") : null, previous);
        }
        if ("write".equals(operation) || "append".equals(operation)) {
            if (!p.has("text")) throw new IllegalArgumentException("text required");
            String incoming = string(p, "text");
            JsonObject previous = read(server);
            String next = "append".equals(operation) ? appendTo(string(previous, "text"), incoming) : incoming;
            return write(server, next, p.has("expectedHash") ? string(p, "expectedHash") : null, previous);
        }
        throw new IllegalArgumentException("Unknown notes operation: " + operation);
    }

    private JsonObject list() throws IOException {
        JsonArray notes = new JsonArray();
        List<String> servers = new ArrayList<String>();
        if (Files.exists(directory)) {
            List<Path> files = new ArrayList<Path>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.md")) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) files.add(path);
                }
            }
            Collections.sort(files);
            for (Path path : files) {
                String name = path.getFileName().toString();
                String server = name.substring(0, name.length() - 3);
                servers.add(server);
                notes.add(object("server", server, "path", relativePath(server),
                        "bytes", Files.size(path), "openInEditor", isLive(server)));
            }
        }
        // Keep the active group discoverable before its first save. This also
        // makes the GUI useful immediately after connecting to a new server.
        String current = currentServer();
        if (!servers.contains(current)) {
            notes.add(object("server", current, "path", relativePath(current),
                    "bytes", 0, "openInEditor", isLive(current)));
        }
        return object("current", currentServer(), "notes", notes);
    }

    private JsonObject read(String server) throws Exception {
        boolean fromEditor = isLive(server);
        String text = fromEditor ? nullToEmpty(live.text()) : diskText(server);
        boolean exists = Files.exists(fileFor(server)) || (fromEditor && !text.isEmpty());
        return document(server, text, exists, fromEditor);
    }

    private JsonObject write(String server, String text, String expectedHash, JsonObject previous) throws Exception {
        if (text == null) text = "";
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Notebook exceeds " + MAX_TEXT_LENGTH + " characters");
        }
        if (expectedHash != null && !expectedHash.equals(string(previous, "hash"))) {
            throw new IllegalStateException("Notebook changed; read again before retrying");
        }
        Path file = fileFor(server);
        Files.createDirectories(directory);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Path tmp = Files.createTempFile(directory, ".notes-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        if (isLive(server) && !text.equals(nullToEmpty(live.text()))) live.replace(text);
        return document(server, text, true, isLive(server));
    }

    private JsonObject delete(String server, String expectedHash, JsonObject previous) throws Exception {
        if (expectedHash != null && !expectedHash.equals(string(previous, "hash"))) {
            throw new IllegalStateException("Notebook changed; read again before retrying");
        }
        Files.deleteIfExists(fileFor(server));
        if (isLive(server)) live.replace("");
        return document(server, "", false, isLive(server));
    }

    private JsonObject rename(String server, String newServer, String expectedHash, JsonObject previous) throws Exception {
        if (newServer.isEmpty()) throw new IllegalArgumentException("newServer required");
        if (server.equals(newServer)) return previous;
        if (expectedHash != null && !expectedHash.equals(string(previous, "hash"))) {
            throw new IllegalStateException("Notebook changed; read again before retrying");
        }
        Path source = fileFor(server);
        Path target = fileFor(newServer);
        if (Files.exists(target)) throw new IllegalArgumentException("Notebook group already exists: " + newServer);
        if (Files.exists(source)) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, target);
            }
        } else {
            Files.createDirectories(directory);
            Files.write(target, string(previous, "text").getBytes(StandardCharsets.UTF_8));
        }
        if (isLive(server)) live.replace(string(previous, "text"));
        return document(newServer, string(previous, "text"), true, false);
    }

    private static String appendTo(String existing, String incoming) {
        if (incoming == null) incoming = "";
        if (existing == null || existing.isEmpty()) return incoming;
        if (existing.endsWith("\n") || incoming.isEmpty()) return existing + incoming;
        return existing + "\n" + incoming;
    }

    private boolean isLive(String server) {
        return live != null && server.equals(live.serverKey());
    }

    private JsonObject document(String server, String text, boolean exists, boolean openInEditor) throws Exception {
        if (text == null) text = "";
        String hash = !exists && text.isEmpty()
                ? "missing"
                : McpConfigStore.hash(text.getBytes(StandardCharsets.UTF_8));
        return object("server", server, "path", relativePath(server), "text", text,
                "exists", exists, "openInEditor", openInEditor, "hash", hash);
    }

    private String relativePath(String server) {
        return "notes/" + sanitizeServerKey(server) + ".md";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
