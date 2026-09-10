package com.zszl.zszlScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class BaritoneSettingsConfig {

    public static final String KEY_USE_BUILTIN_BARITONE = "useBuiltinBaritone";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private static Path getLegacyConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("baritone_settings.json");
    }

    public static void load() {
        migrateLegacyConfigIfNeeded();
        forceBuiltinBaritone();
        applyRuntimeNavigationMode();
        BaritoneParkourSettingsHelper.syncRuntimeOverrides();
    }

    public static void save() {
        forceBuiltinBaritone();
        try {
            SettingsUtil.save(BaritoneAPI.getSettings());
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.error("无法保存 Baritone 设置到 shadowbaritone/setting.json", t);
        }
    }

    public static String getValue(String key, String defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        if (KEY_USE_BUILTIN_BARITONE.equalsIgnoreCase(key)) {
            return "true";
        }
        return defaultValue;
    }

    public static void setValue(String key, String value) {
        if (key == null) {
            return;
        }
        if (KEY_USE_BUILTIN_BARITONE.equalsIgnoreCase(key)) {
            forceBuiltinBaritone();
        }
    }

    public static void clearValue(String key) {
        if (key == null) {
            return;
        }
        if (KEY_USE_BUILTIN_BARITONE.equalsIgnoreCase(key)) {
            forceBuiltinBaritone();
        }
    }

    public static void clearAll() {
        forceBuiltinBaritone();
    }

    public static boolean isUseBuiltinBaritone() {
        return true;
    }

    public static void setUseBuiltinBaritone(boolean useBuiltinBaritone) {
        forceBuiltinBaritone();
    }

    public static String getNavigationModeDisplayName() {
        return "内置直调";
    }

    public static void applyRuntimeNavigationMode() {
        forceBuiltinBaritone();
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone();
            zszlScriptMod.LOGGER.info("Baritone 导航调用模式已固定为 {}", getNavigationModeDisplayName());
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.error("应用 Baritone 内置导航模式失败", t);
        }
    }

    private static void forceBuiltinBaritone() {
        try {
            BaritoneAPI.getSettings().useBuiltinBaritone.value = true;
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.error("固定使用内置 Baritone 失败", t);
        }
    }

    private static void migrateLegacyConfigIfNeeded() {
        Path legacyConfigFile = getLegacyConfigFile();
        if (!Files.exists(legacyConfigFile)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(legacyConfigFile, StandardCharsets.UTF_8)) {
            GSON.fromJson(reader, MAP_TYPE);
            Files.deleteIfExists(legacyConfigFile);
            zszlScriptMod.LOGGER.info("已清理旧版 baritone_settings.json，导航现已固定为内置模式");
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.error("清理旧版 baritone_settings.json 失败", t);
        }
    }
}
