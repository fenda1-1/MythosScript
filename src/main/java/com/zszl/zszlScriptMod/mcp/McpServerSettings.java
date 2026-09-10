package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Installation-wide settings; independent of the selected gameplay profile. */
public final class McpServerSettings {
    public final boolean enabled;
    public final int port;
    public McpServerSettings(boolean enabled, int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
        this.enabled = enabled;
        this.port = port;
    }
    public static McpServerSettings read(Path file, McpServerSettings defaults) throws IOException {
        if (!Files.exists(file)) return defaults;
        JsonObject data = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        return new McpServerSettings(bool(data, "enabled", defaults.enabled),
                integer(data, "port", defaults.port, 1, 65535));
    }
    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), ".mcp-settings-", ".tmp");
        try {
            Files.write(temp, GSON.toJson(object("enabled", enabled, "port", port)).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
