package com.zszl.zszlScriptMod.gui.packet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PacketSnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class SavedPacket {
        public long timestamp;
        public String packetClassName;
        public boolean isFmlPacket;
        public Integer packetId;
        public String channel;
        public String rawHex;
        public String decodedData;
        public long lastTimestamp;
        public int occurrenceCount = 1;
        public int totalPayloadBytes = 0;

        public static SavedPacket fromCaptured(PacketCaptureHandler.CapturedPacketData packet) {
            SavedPacket sp = new SavedPacket();
            sp.timestamp = packet.timestamp;
            sp.packetClassName = packet.packetClassName;
            sp.isFmlPacket = packet.isFmlPacket;
            sp.packetId = packet.packetId;
            sp.channel = packet.channel;
            sp.rawHex = packet.getHexData();
            sp.decodedData = packet.getDecodedFullData();
            sp.lastTimestamp = packet.getLastTimestamp();
            sp.occurrenceCount = packet.getOccurrenceCount();
            sp.totalPayloadBytes = packet.getTotalPayloadBytes();
            return sp;
        }

        public PacketCaptureHandler.CapturedPacketData toCaptured() {
            byte[] raw = hexToBytes(rawHex);
            PacketCaptureHandler.CapturedPacketData packet = new PacketCaptureHandler.CapturedPacketData(
                    timestamp,
                    packetClassName == null ? "UnknownPacket" : packetClassName,
                    isFmlPacket,
                    packetId,
                    channel == null ? "" : channel,
                    raw,
                    decodedData == null ? "" : decodedData);
            packet.restoreAggregateState(lastTimestamp <= 0 ? timestamp : lastTimestamp,
                    occurrenceCount <= 0 ? 1 : occurrenceCount,
                    totalPayloadBytes <= 0 ? raw.length : totalPayloadBytes);
            return packet;
        }
    }

    public static class SavedSnapshot {
        public String name;
        public long createdAt;
        public String captureMode;
        public List<SavedPacket> packets = new ArrayList<>();
    }

    public static class SnapshotMeta {
        public final String name;
        public final long createdAt;
        public final int packetCount;
        public final String captureMode;

        public SnapshotMeta(String name, long createdAt, int packetCount, String captureMode) {
            this.name = name;
            this.createdAt = createdAt;
            this.packetCount = packetCount;
            this.captureMode = captureMode;
        }

        public String getDisplayTime() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(createdAt));
        }
    }

    private static Path getSnapshotDir() {
        Path dir = Paths.get(ModConfig.CONFIG_DIR, "PacketSnapshots");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                zszlScriptMod.LOGGER.error("Failed to create packet snapshot directory", e);
            }
        }
        return dir;
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "snapshot";
        }
        String cleaned = name.trim().replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
        return cleaned.isEmpty() ? "snapshot" : cleaned;
    }

    private static Path resolveSnapshotPath(String name) {
        return getSnapshotDir().resolve(sanitizeName(name) + ".json");
    }

    public static boolean saveSnapshot(String name, List<PacketCaptureHandler.CapturedPacketData> packets,
            String mode) {
        if (packets == null || packets.isEmpty()) {
            return false;
        }

        SavedSnapshot snapshot = new SavedSnapshot();
        snapshot.name = sanitizeName(name);
        snapshot.createdAt = System.currentTimeMillis();
        snapshot.captureMode = (mode == null || mode.trim().isEmpty()) ? "UNKNOWN" : mode;
        snapshot.packets = packets.stream()
                .map(SavedPacket::fromCaptured)
                .collect(Collectors.toList());

        Path filePath = resolveSnapshotPath(snapshot.name);
        try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to save packet snapshot: {}", snapshot.name, e);
            return false;
        }
    }

    public static List<SnapshotMeta> listSnapshots() {
        try (Stream<Path> paths = Files.walk(getSnapshotDir())) {
            List<SnapshotMeta> result = new ArrayList<>();
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path file : files) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    SavedSnapshot snapshot = GSON.fromJson(reader, SavedSnapshot.class);
                    if (snapshot == null) {
                        continue;
                    }
                    String name = snapshot.name;
                    if (name == null || name.trim().isEmpty()) {
                        String fn = file.getFileName().toString();
                        name = fn.substring(0, fn.length() - 5);
                    }
                    int count = snapshot.packets == null ? 0 : snapshot.packets.size();
                    result.add(new SnapshotMeta(name, snapshot.createdAt, count,
                            snapshot.captureMode == null ? "UNKNOWN" : snapshot.captureMode));
                } catch (Exception ex) {
                    zszlScriptMod.LOGGER.warn("Skip invalid packet snapshot file: {}", file, ex);
                }
            }

            result.sort(Comparator.comparingLong((SnapshotMeta m) -> m.createdAt).reversed());
            return result;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to list packet snapshots", e);
            return Collections.emptyList();
        }
    }

    public static SavedSnapshot loadSnapshot(String name) {
        Path filePath = resolveSnapshotPath(name);
        if (!Files.exists(filePath)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            SavedSnapshot snapshot = GSON.fromJson(reader, SavedSnapshot.class);
            if (snapshot == null) {
                return null;
            }
            if (snapshot.packets == null) {
                snapshot.packets = new ArrayList<>();
            }
            if (snapshot.name == null || snapshot.name.trim().isEmpty()) {
                snapshot.name = sanitizeName(name);
            }
            if (snapshot.captureMode == null) {
                snapshot.captureMode = "UNKNOWN";
            }
            return snapshot;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to load packet snapshot: {}", name, e);
            return null;
        }
    }

    public static boolean deleteSnapshot(String name) {
        try {
            return Files.deleteIfExists(resolveSnapshotPath(name));
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to delete packet snapshot: {}", name, e);
            return false;
        }
    }

    public static boolean renameSnapshot(String oldName, String newName) {
        String oldSanitized = sanitizeName(oldName);
        String newSanitized = sanitizeName(newName);
        if (oldSanitized.equals(newSanitized)) {
            return true;
        }

        Path oldPath = resolveSnapshotPath(oldSanitized);
        Path newPath = resolveSnapshotPath(newSanitized);
        if (!Files.exists(oldPath) || Files.exists(newPath)) {
            return false;
        }

        SavedSnapshot snapshot = loadSnapshot(oldSanitized);
        if (snapshot == null) {
            return false;
        }
        snapshot.name = newSanitized;

        try (Writer writer = Files.newBufferedWriter(newPath, StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to write renamed packet snapshot: {} -> {}", oldSanitized,
                    newSanitized, e);
            return false;
        }

        try {
            Files.deleteIfExists(oldPath);
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to remove old packet snapshot after rename: {}", oldSanitized, e);
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return new byte[0];
        }
        String cleaned = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (cleaned.length() % 2 != 0) {
            cleaned = "0" + cleaned;
        }
        byte[] data = new byte[cleaned.length() / 2];
        for (int i = 0; i < cleaned.length(); i += 2) {
            data[i / 2] = (byte) Integer.parseInt(cleaned.substring(i, i + 2), 16);
        }
        return data;
    }
}
