// 文件路径: src/main/java/com/mythos/mythosScriptMod/config/KeybindActionConfig.java
package com.mythos.mythosScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.system.ProfileManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 存储与快捷键动作相关的特定配置，例如要执行的数据包序列名称。
 */
public class KeybindActionConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static KeybindActionConfig INSTANCE = new KeybindActionConfig();

    // 要通过快捷键执行的数据包序列的文件名 (不含.json)
    public String packetSequenceToExecute = "";

    private KeybindActionConfig() {}

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("keybind_actions.json");
    }

    public static void save() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("无法保存快捷键动作配置", e);
        }
    }

    public static void load() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, KeybindActionConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new KeybindActionConfig();
                }
            } catch (Exception e) {
                mythosScriptMod.LOGGER.error("无法加载快捷键动作配置", e);
                INSTANCE = new KeybindActionConfig();
            }
        } else {
            INSTANCE = new KeybindActionConfig();
        }
    }
}
