// 文件路径: src/main/java/com/keycommand2/zszlScriptMod/config/ChatOptimizationConfig.java
package com.zszl.zszlScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.ProfileManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays; // !! 新增导入 !!
import java.util.List;

public class ChatOptimizationConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static ChatOptimizationConfig INSTANCE = new ChatOptimizationConfig();

    public enum ImageQuality {
        ORIGINAL("原始尺寸", "最高内存占用，保留原图分辨率。"),
        HIGH("高 (平滑)", "缩放至最大1920px宽，平滑插值，质量较好。"),
        MEDIUM("中 (像素化)", "缩放至最大854px宽，像素插值，内存占用低。"),
        LOW("低 (像素化)", "缩放至最大426px宽，像素插值，内存占用极低。");

        private final String displayName;
        private final String description;

        ImageQuality(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }

        public ImageQuality next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    // !! 新增：定时消息发送模式 !!
    public enum TimedMessageMode {
        SEQUENTIAL("顺序循环"),
        RANDOM("随机发送");

        private final String displayName;
        TimedMessageMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        public TimedMessageMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    // --- 功能开关 ---
    public boolean enableSmartCopy = true;
    public boolean copyWithFormatting = false;
    public boolean enableAntiSpam = true;
    public boolean enableTimestamp = false;
    public boolean enableBlacklist = false;
    public boolean enableWhitelist = false;
    public boolean antiSpamScrollToBottom = false;
    public boolean enableTimedMessage = false;
    public boolean regexFilter = false; // !! 新增：Regex过滤开关 !!

    // --- 功能参数 ---
    public int antiSpamThresholdSeconds = 15;
    // !! 修改：从单个消息变为消息列表 !!
    public List<String> timedMessages = new ArrayList<>(Arrays.asList(""));
    public int timedMessageIntervalSeconds = 60;
    public TimedMessageMode timedMessageMode = TimedMessageMode.SEQUENTIAL; // !! 新增：发送模式 !!


    // --- 视觉与动画设置 ---
    public boolean smooth = true;
    public int backgroundTransparencyPercent = 50;
    public int xOffset = 0;
    public int yOffset = 0;
    public String backgroundImagePath = "";
    public ImageQuality imageQuality = ImageQuality.MEDIUM;
    public int backgroundImageScale = 100;
    public int backgroundCropX = 0;
    public int backgroundCropY = 0;

    // --- 过滤列表 ---
    public List<String> blacklist = new ArrayList<>();
    public List<String> whitelist = new ArrayList<>();

    private ChatOptimizationConfig() {}

    public static void resetToDefaults() {
        INSTANCE = new ChatOptimizationConfig();
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("chat_optimization.json");
    }

    public static void save() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存聊天优化配置", e);
        }
    }

    public static void load() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, ChatOptimizationConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new ChatOptimizationConfig();
                }
                // 兼容旧配置
                if (INSTANCE.imageQuality == null) {
                    INSTANCE.imageQuality = ImageQuality.MEDIUM;
                }
                if (INSTANCE.backgroundImageScale <= 0) {
                    INSTANCE.backgroundImageScale = 100;
                }
                if (INSTANCE.backgroundCropX < 0) {
                    INSTANCE.backgroundCropX = 0;
                }
                if (INSTANCE.backgroundCropY < 0) {
                    INSTANCE.backgroundCropY = 0;
                }
                // !! 新增：兼容旧配置，为新字段设置默认值 !!
                if (INSTANCE.timedMessages == null) {
                    INSTANCE.timedMessages = new ArrayList<>();
                    // 尝试从旧的 timedMessage 字段迁移
                    try {
                        // 使用反射来安全地访问可能存在的旧字段
                        Object oldMessage = ChatOptimizationConfig.class.getField("timedMessage").get(INSTANCE);
                        if (oldMessage instanceof String && !((String) oldMessage).isEmpty()) {
                            INSTANCE.timedMessages.add((String) oldMessage);
                        }
                    } catch (Exception ignored) {
                        // 忽略错误，说明旧字段不存在
                    }
                }
                if (INSTANCE.timedMessages.isEmpty()) {
                    INSTANCE.timedMessages.add(""); // 确保至少有一个空字符串用于编辑
                }
                if (INSTANCE.timedMessageMode == null) {
                    INSTANCE.timedMessageMode = TimedMessageMode.SEQUENTIAL;
                }

            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("无法加载聊天优化配置", e);
                INSTANCE = new ChatOptimizationConfig();
            }
        } else {
            INSTANCE = new ChatOptimizationConfig();
        }
    }
}
