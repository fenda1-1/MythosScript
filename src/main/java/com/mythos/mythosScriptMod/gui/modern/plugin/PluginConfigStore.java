package com.mythos.mythosScriptMod.gui.modern.plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mythos.mythosScriptMod.api.IPluginConfig;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.mythosScriptMod;

/**
 * One plugin's JSON config file, loaded on first use and written only on demand.
 *
 * <p>A malformed file is not an error the plugin should die from: the fields that
 * cannot be read fall back to the caller's default and a single warning is logged,
 * so a hand-edited typo costs the user that one setting, not the whole plugin.</p>
 */
final class PluginConfigStore implements IPluginConfig {

    private final String pluginId;
    private final Path file;
    private JsonObject data;
    private boolean loadAttempted;

    PluginConfigStore(String pluginId) {
        this.pluginId = pluginId;
        this.file = Paths.get(ModConfig.CONFIG_DIR, "plugins", safeFileName(pluginId) + ".json");
    }

    Path file() {
        return file;
    }

    @Override
    public String getString(String key, String fallback) {
        JsonPrimitive primitive = primitive(key);
        if (primitive == null) {
            return fallback;
        }
        try {
            String value = primitive.getAsString();
            return value == null ? fallback : value;
        } catch (RuntimeException wrongType) {
            return fallback;
        }
    }

    @Override
    public int getInt(String key, int fallback) {
        JsonPrimitive primitive = primitive(key);
        if (primitive == null) {
            return fallback;
        }
        try {
            return primitive.getAsInt();
        } catch (RuntimeException wrongType) {
            return fallback;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        JsonPrimitive primitive = primitive(key);
        if (primitive == null) {
            return fallback;
        }
        try {
            return primitive.getAsBoolean();
        } catch (RuntimeException wrongType) {
            return fallback;
        }
    }

    @Override
    public void set(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        JsonObject target = data();
        if (value == null) {
            target.remove(key);
        } else if (value instanceof Number) {
            target.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            target.addProperty(key, (Boolean) value);
        } else {
            target.addProperty(key, String.valueOf(value));
        }
    }

    @Override
    public boolean save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Write through a sibling temp file so an interrupted save cannot
            // leave a half-written config that fails to parse on next launch.
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            byte[] bytes = data().toString().getBytes(StandardCharsets.UTF_8);
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception broken) {
            mythosScriptMod.LOGGER.warn("[plugin:{}] 配置写入失败: {}", pluginId, file, broken);
            return false;
        }
    }

    private JsonPrimitive primitive(String key) {
        if (key == null) {
            return null;
        }
        JsonElement element = data().get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsJsonPrimitive();
    }

    private JsonObject data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    private JsonObject load() {
        if (loadAttempted) {
            return new JsonObject();
        }
        loadAttempted = true;
        File disk = file.toFile();
        if (!disk.isFile()) {
            return new JsonObject();
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(text);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            mythosScriptMod.LOGGER.warn("[plugin:{}] 配置不是 JSON 对象，已按空配置运行: {}", pluginId, file);
        } catch (Exception broken) {
            mythosScriptMod.LOGGER.warn("[plugin:{}] 配置解析失败，已按空配置运行: {}", pluginId, file, broken);
        }
        return new JsonObject();
    }

    /** Plugin ids may carry characters Windows rejects in file names. */
    private static String safeFileName(String id) {
        if (id == null || id.trim().isEmpty()) {
            return "plugin";
        }
        return id.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
