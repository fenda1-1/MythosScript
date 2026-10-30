package com.mythos.mythosScriptMod.gui.modern.plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

/**
 * Reads MythosScript plugin manifests out of the mods directory.
 *
 * <p>A plugin is an ordinary Forge mod jar that additionally carries
 * {@code META-INF/mythos-plugin.json}, so installing one is the same gesture as
 * installing any other mod. Scanning is cached because the centre redraws on
 * every frame and reopening a jar per frame would stall the client on slow
 * disks; the user refreshes the cache explicitly.</p>
 */
public final class PluginCatalog {

    /** Entry a jar must contain to be treated as a MythosScript plugin. */
    public static final String MANIFEST_ENTRY = "META-INF/mythos-plugin.json";

    private static final String UNNAMED = "未命名插件";
    private static final String UNKNOWN = "未知";

    /** One parsed manifest. Immutable once published to the cache. */
    public static final class Plugin {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final String entrypoint;
        public final String fileName;
        public final List<String> features;
        /** API version the plugin was built against; 1 when the field is absent. */
        public final int apiVersion;
        /** Plugin ids that must load first. */
        public final List<String> requires;

        Plugin(String id, String name, String version, String author, String description,
                String entrypoint, String fileName, List<String> features, int apiVersion,
                List<String> requires) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.entrypoint = entrypoint;
            this.fileName = fileName;
            this.features = features;
            this.apiVersion = apiVersion;
            this.requires = requires;
        }

        public int featureCount() {
            return features.size();
        }
    }

    private static volatile List<Plugin> cache;
    private static volatile String lastError = "";

    private PluginCatalog() {
    }

    /** Returns the cached manifest list, scanning once on first use. */
    public static List<Plugin> plugins() {
        List<Plugin> snapshot = cache;
        return snapshot == null ? rescan() : snapshot;
    }

    public static String lastError() {
        return lastError;
    }

    public static File modsDirectory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.mcDataDir == null) {
            return null;
        }
        return new File(minecraft.mcDataDir, "mods");
    }

    /** Drops the cache and re-reads every jar; used by the refresh button. */
    public static synchronized List<Plugin> rescan() {
        List<Plugin> found = new ArrayList<>();
        lastError = "";
        File directory = modsDirectory();
        if (directory == null || !directory.isDirectory()) {
            lastError = "找不到 mods 目录";
            cache = Collections.emptyList();
            return cache;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            lastError = "无法读取 mods 目录";
            cache = Collections.emptyList();
            return cache;
        }
        for (File file : files) {
            if (file == null || !file.isFile() || !isJarName(file.getName())) {
                continue;
            }
            Plugin plugin = readManifest(file);
            if (plugin != null) {
                found.add(plugin);
            }
        }
        Collections.sort(found, new Comparator<Plugin>() {
            @Override
            public int compare(Plugin left, Plugin right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        cache = Collections.unmodifiableList(found);
        return cache;
    }

    private static boolean isJarName(String name) {
        return name != null && name.length() > 4 && name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar");
    }

    private static Plugin readManifest(File file) {
        JarFile jar = null;
        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry(MANIFEST_ENTRY);
            if (entry == null) {
                return null;
            }
            Reader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8);
            try {
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    return null;
                }
                JsonObject root = parsed.getAsJsonObject();
                return new Plugin(
                        text(root, "id", file.getName()),
                        text(root, "name", UNNAMED),
                        text(root, "version", UNKNOWN),
                        text(root, "author", UNKNOWN),
                        text(root, "description", ""),
                        text(root, "entrypoint", ""),
                        file.getName(),
                        stringList(root, "features"),
                        intValue(root, "apiVersion", 1),
                        stringList(root, "requires"));
            } finally {
                reader.close();
            }
        } catch (Throwable broken) {
            // One unreadable jar must not hide every other installed plugin.
            return null;
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (Throwable ignored) {
                    // Closing a read-only jar cannot invalidate the list.
                }
            }
        }
    }

    private static String text(JsonObject root, String key, String fallback) {        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        String value = element.getAsString();
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int intValue(JsonObject root, String key, int fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException wrongType) {
            return fallback;
        }
    }

    private static List<String> stringList(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || item.isJsonNull() || !item.isJsonPrimitive()) {
                continue;
            }
            String value = item.getAsString();
            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }
        return Collections.unmodifiableList(values);
    }

    /** Sample manifest shown in the empty state so the format is discoverable. */
    public static String manifestExample() {
        return "{\n"
                + "  \"id\": \"example_plugin\",\n"
                + "  \"name\": \"示例插件\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"apiVersion\": 1,\n"
                + "  \"author\": \"你的名字\",\n"
                + "  \"description\": \"这个插件做了什么\",\n"
                + "  \"entrypoint\": \"com.example.MyPlugin\",\n"
                + "  \"requires\": [],\n"
                + "  \"features\": [\"命令\", \"tick\", \"mcp 工具\"]\n"
                + "}";
    }

    /** Exports the current cache as JSON so users can share a plugin list. */
    public static String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        List<Plugin> plugins = plugins();
        for (int i = 0; i < plugins.size(); i++) {
            Plugin plugin = plugins.get(i);
            builder.append("  {\"id\":\"").append(escape(plugin.id))
                    .append("\",\"name\":\"").append(escape(plugin.name))
                    .append("\",\"version\":\"").append(escape(plugin.version))
                    .append("\",\"author\":\"").append(escape(plugin.author))
                    .append("\",\"file\":\"").append(escape(plugin.fileName))
                    .append("\",\"features\":").append(plugin.featureCount())
                    .append('}');
            builder.append(i + 1 < plugins.size() ? ",\n" : "\n");
        }
        builder.append("]");
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
