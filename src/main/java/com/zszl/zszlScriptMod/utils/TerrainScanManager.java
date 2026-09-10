// (这是一个全新的文件)
package com.zszl.zszlScriptMod.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.ProfileManager;
import net.minecraft.client.resources.I18n;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TerrainScanManager {

    private static final Gson GSON = new Gson();

    // 获取当前配置方案下的 'terrain' 目录
    private static Path getScanDirectory() {
        return ProfileManager.getCurrentProfileDir().resolve("terrain");
    }

    // 获取当前配置方案下的扫描仪设置文件
    private static Path getSettingsFile() {
        return ProfileManager.getCurrentProfileDir().resolve("scanner_settings.json");
    }

    // 获取所有扫描文件的名称列表，按名称排序
    public static List<String> getAllScanNames() {
        try {
            Path scanDir = getScanDirectory();
            if (!Files.exists(scanDir)) {
                return Collections.emptyList();
            }
            try (Stream<Path> stream = Files.list(scanDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .map(path -> path.getFileName().toString())
                        .sorted(Comparator.reverseOrder()) // 按时间戳倒序
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to list terrain scan files", e);
            return Collections.emptyList();
        }
    }

    // 删除指定的扫描文件
    public static boolean deleteScan(String fileName) {
        if (!isSafeScanFileName(fileName)) {
            return false;
        }
        try {
            Path filePath = getScanDirectory().resolve(fileName);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to delete terrain scan file '{}'", fileName, e);
            return false;
        }
    }

    // 重命名扫描文件
    public static boolean renameScan(String oldName, String newName) {
        if (!isSafeScanFileName(oldName) || newName == null || newName.trim().isEmpty()) {
            return false;
        }
        try {
            Path oldPath = getScanDirectory().resolve(oldName);
            // 确保新文件名以 .json 结尾
            newName = newName.trim();
            if (!newName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                newName += ".json";
            }
            if (!isSafeScanFileName(newName)) {
                return false;
            }
            Path newPath = getScanDirectory().resolve(newName);
            if (Files.exists(newPath)) {
                return false; // 文件名已存在
            }
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to rename terrain scan file '{}'", oldName, e);
            return false;
        }
    }

    // 删除所有扫描文件
    public static void deleteAllScans() {
        try {
            Path scanDir = getScanDirectory();
            if (Files.exists(scanDir)) {
                try (Stream<Path> walk = Files.walk(scanDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(scanDir)) // 不要删除目录本身
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    zszlScriptMod.LOGGER.error("Failed to delete file: {}", path, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to clear terrain scan directory", e);
        }
    }

    // 读取指定扫描文件的内容
    public static String readScanContent(String fileName) {
        if (!isSafeScanFileName(fileName)) {
            return I18n.format("msg.terrain_scan.read_failed");
        }
        try {
            Path filePath = getScanDirectory().resolve(fileName);
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to read terrain scan file '{}'", fileName, e);
            return I18n.format("msg.terrain_scan.read_failed");
        }
    }

    // 保存最后使用的半径
    public static void saveLastRadius(int radius) {
        try {
            Path settingsFile = getSettingsFile();
            JsonObject settings = new JsonObject();
            settings.addProperty("lastRadius", radius);
            try (BufferedWriter writer = Files.newBufferedWriter(settingsFile, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to save terrain scan radius", e);
        }
    }

    // 加载最后使用的半径
    public static int loadLastRadius() {
        try {
            Path settingsFile = getSettingsFile();
            if (Files.exists(settingsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
                    JsonObject settings = new JsonParser().parse(reader).getAsJsonObject();
                    if (settings.has("lastRadius")) {
                        return settings.get("lastRadius").getAsInt();
                    }
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to load terrain scan radius", e);
        }
        return 10; // 默认值
    }

    private static boolean isSafeScanFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty() || !fileName.equals(fileName.trim())) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return !".".equals(fileName) && !"..".equals(fileName)
                && fileName.indexOf('/') < 0 && fileName.indexOf('\\') < 0
                && lowerName.endsWith(".json");
    }
}
