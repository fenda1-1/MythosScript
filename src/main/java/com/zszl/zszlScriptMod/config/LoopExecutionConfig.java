package com.zszl.zszlScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 路径循环执行配置（按 Profile 保存）。
 */
public class LoopExecutionConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static LoopExecutionConfig INSTANCE = new LoopExecutionConfig();

    // 默认 1 次；可被用户改为 0/-1/正整数
    public int loopCount = 1;

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("loop_execution.json");
    }

    public static void load() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, LoopExecutionConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new LoopExecutionConfig();
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("无法加载循环执行配置", e);
                INSTANCE = new LoopExecutionConfig();
            }
        } else {
            INSTANCE = new LoopExecutionConfig();
            save();
        }
    }

    public static void save() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存循环执行配置", e);
        }
    }
}
