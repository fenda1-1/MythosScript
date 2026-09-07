// 文件路径: src/main/java/com/zszl/zszlScriptMod/gui/packet/PacketSequenceManager.java
package com.zszl.zszlScriptMod.gui.packet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PacketSequenceManager {

    private static Path getPacketDir() {
        // ... (此方法保持不变)
        Path dir = Paths.get(ModConfig.CONFIG_DIR, "PacketSequences");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                zszlScriptMod.LOGGER.error("Failed to create packet sequence directory", e);
            }
        }
        return dir;
    }

    public static List<String> getAllSequenceNames() {
        // ... (此方法保持不变)
        try (Stream<Path> paths = Files.walk(getPacketDir())) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.toLowerCase().endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to read packet sequence list", e);
            return Collections.emptyList();
        }
    }

    public static boolean saveSequence(PacketSequence sequence) {
        // ... (此方法保持不变)
        if (sequence == null || sequence.name == null || sequence.name.trim().isEmpty()) {
            return false;
        }
        Path packetDir = getPacketDir();
        String fileName = sequence.name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
        Path filePath = packetDir.resolve(fileName + ".json");

        try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(sequence, writer);
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to save packet sequence: " + sequence.name, e);
            return false;
        }
    }

    public static PacketSequence loadSequence(String name) {
        Path filePath = getPacketDir().resolve(name + ".json");
        if (!Files.exists(filePath)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            PacketSequence sequence = gson.fromJson(reader, PacketSequence.class);

            // --- 核心修复：增加健壮性检查 ---
            // 如果加载的序列不为null (文件不是空的或格式完全错误)
            if (sequence != null) {
                // 检查name字段是否为null (可能由旧的空{}文件导致)
                if (sequence.name == null) {
                    sequence.name = name; // 使用文件名作为备用名称
                }
                // 检查packets列表是否为null，如果是，则初始化为空列表以防止崩溃
                if (sequence.packets == null) {
                    sequence.packets = new ArrayList<>();
                } else {
                    for (PacketSequence.PacketToSend p : sequence.packets) {
                        if (p != null && (p.direction == null || p.direction.trim().isEmpty())) {
                            p.direction = "C2S";
                        }
                    }
                }
            }
            // --- 修复结束 ---

            return sequence;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to load packet sequence: " + name, e);
            return null;
        }
    }

    public static boolean deleteSequence(String name) {
        // ... (此方法保持不变)
        try {
            Path filePath = getPacketDir().resolve(name + ".json");
            Files.deleteIfExists(filePath);
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to delete packet sequence: " + name, e);
            return false;
        }
    }

    public static boolean renameSequence(String oldName, String newName) {
        // ... (此方法保持不变)
        Path oldFile = getPacketDir().resolve(oldName + ".json");
        Path newFile = getPacketDir().resolve(newName + ".json");
        if (Files.exists(oldFile) && !Files.exists(newFile)) {
            try {
                PacketSequence seq = loadSequence(oldName);
                if (seq != null) {
                    seq.name = newName;
                    if (saveSequence(seq)) {
                        Files.delete(oldFile);
                        return true;
                    }
                }
            } catch (IOException e) {
                zszlScriptMod.LOGGER.error("Failed to rename packet sequence: " + oldName + " -> " + newName, e);
            }
        }
        return false;
    }
}
