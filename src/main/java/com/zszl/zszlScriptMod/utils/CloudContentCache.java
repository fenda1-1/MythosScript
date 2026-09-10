package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CloudContentCache {

    private CloudContentCache() {
    }

    private static Path getCacheDir() {
        return ProfileManager.getCurrentProfileDir().resolve("cloud_cache");
    }

    public static String readText(String fileName) {
        try {
            Path file = getCacheDir().resolve(fileName);
            if (!Files.exists(file)) {
                return "";
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("读取云缓存失败: {}", fileName, e);
            return "";
        }
    }

    public static void writeText(String fileName, String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        try {
            Path dir = getCacheDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("写入云缓存失败: {}", fileName, e);
        }
    }
}
