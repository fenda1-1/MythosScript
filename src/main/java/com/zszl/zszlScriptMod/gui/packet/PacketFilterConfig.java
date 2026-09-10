// 文件路径: src/main/java/com/zszl/zszlScriptMod/gui/packet/PacketFilterConfig.java
package com.zszl.zszlScriptMod.gui.packet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CaptureMode;
import com.zszl.zszlScriptMod.zszlScriptMod;

public class PacketFilterConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static PacketFilterConfig INSTANCE = new PacketFilterConfig();

    public CaptureMode captureMode = CaptureMode.BLACKLIST;
    public List<String> whitelistFilters = new ArrayList<>();
    public List<String> blacklistFilters = new ArrayList<>();
    public int maxCapturedPackets = 3000;
    public boolean enableBusinessPacketProcessing = true;
    public boolean enableAdaptiveSampling = true;
    public int adaptiveSamplingQueueThreshold = 1500;
    public int adaptiveSamplingModulo = 4;

    private PacketFilterConfig() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("packet_filter_config.json");
    }

    public static void save() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to save packet filter config", e);
        }
    }

    public static void load() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, PacketFilterConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new PacketFilterConfig();
                }
                // 兼容旧配置，防止新字段为null
                if (INSTANCE.captureMode == null)
                    INSTANCE.captureMode = CaptureMode.BLACKLIST;
                if (INSTANCE.whitelistFilters == null)
                    INSTANCE.whitelistFilters = new ArrayList<>();
                if (INSTANCE.blacklistFilters == null)
                    INSTANCE.blacklistFilters = new ArrayList<>();
                if (INSTANCE.maxCapturedPackets <= 0)
                    INSTANCE.maxCapturedPackets = 3000;
                INSTANCE.maxCapturedPackets = Math.max(100, Math.min(10000, INSTANCE.maxCapturedPackets));
                INSTANCE.adaptiveSamplingQueueThreshold = Math.max(200,
                        Math.min(10000, INSTANCE.adaptiveSamplingQueueThreshold));
                INSTANCE.adaptiveSamplingModulo = Math.max(2, Math.min(64, INSTANCE.adaptiveSamplingModulo));
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("Failed to load packet filter config", e);
                INSTANCE = new PacketFilterConfig();
            }
        } else {
            INSTANCE = new PacketFilterConfig();
        }
    }
}
