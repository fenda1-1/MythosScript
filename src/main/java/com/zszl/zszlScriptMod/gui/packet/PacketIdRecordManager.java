package com.zszl.zszlScriptMod.gui.packet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PacketIdRecordManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type RECORD_LIST_TYPE = new TypeToken<List<PacketIdRecord>>() {
    }.getType();
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zszl-packet-id-record-save");
        t.setDaemon(true);
        return t;
    });

    private static final List<PacketIdRecord> RECORDS = new ArrayList<>();
    private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean(false);

    private static boolean loaded = false;
    private static boolean dirty = false;

    private PacketIdRecordManager() {
    }

    public static final class PacketIdRecord {
        public String direction;
        public boolean isFmlPacket;
        public Integer packetId;
        public String channel;
        public String packetClassName;
        public long firstSeenAt;
        public long lastSeenAt;
        public int occurrenceCount;

        public String getDisplayDirection() {
            return direction == null || direction.trim().isEmpty() ? "?" : direction;
        }

        public String getIdOrChannelText() {
            if (isFmlPacket) {
                String safeChannel = channel == null || channel.trim().isEmpty() ? "N/A" : channel;
                return "频道: " + safeChannel;
            }
            return "ID: " + (packetId == null ? "N/A" : String.format("0x%02X", packetId));
        }
    }

    public static synchronized void recordCapturedPacket(boolean isSent,
            PacketCaptureHandler.CapturedPacketData packet) {
        if (packet == null) {
            return;
        }
        ensureLoaded();

        String direction = isSent ? "C2S" : "S2C";
        long now = System.currentTimeMillis();
        PacketIdRecord existing = findExisting(direction, packet);
        int count = Math.max(1, packet.getOccurrenceCount());

        if (existing != null) {
            existing.lastSeenAt = Math.max(now, packet.getLastTimestamp());
            existing.occurrenceCount += count;
        } else {
            PacketIdRecord created = new PacketIdRecord();
            created.direction = direction;
            created.isFmlPacket = packet.isFmlPacket;
            created.packetId = packet.packetId;
            created.channel = packet.channel == null ? "" : packet.channel;
            created.packetClassName = packet.packetClassName == null ? "UnknownPacket" : packet.packetClassName;
            created.firstSeenAt = packet.timestamp > 0L ? packet.timestamp : now;
            created.lastSeenAt = Math.max(created.firstSeenAt, packet.getLastTimestamp());
            created.occurrenceCount = count;
            RECORDS.add(created);
        }

        dirty = true;
        scheduleSave();
    }

    public static synchronized List<PacketIdRecord> listRecords() {
        ensureLoaded();
        List<PacketIdRecord> copy = new ArrayList<>();
        for (PacketIdRecord record : RECORDS) {
            copy.add(copyOf(record));
        }
        copy.sort(Comparator.comparingLong((PacketIdRecord r) -> r.lastSeenAt).reversed()
                .thenComparing(r -> r.packetClassName == null ? "" : r.packetClassName));
        return copy;
    }

    public static synchronized boolean clearRecords() {
        ensureLoaded();
        RECORDS.clear();
        dirty = true;
        scheduleSave();
        return true;
    }

    private static PacketIdRecord findExisting(String direction, PacketCaptureHandler.CapturedPacketData packet) {
        for (PacketIdRecord record : RECORDS) {
            if (record.isFmlPacket != packet.isFmlPacket) {
                continue;
            }
            if (!safe(direction).equalsIgnoreCase(safe(record.direction))) {
                continue;
            }
            if (!safe(packet.packetClassName).equalsIgnoreCase(safe(record.packetClassName))) {
                continue;
            }
            if (packet.isFmlPacket) {
                if (safe(packet.channel).equalsIgnoreCase(safe(record.channel))) {
                    return record;
                }
            } else {
                if ((packet.packetId == null && record.packetId == null)
                        || (packet.packetId != null && packet.packetId.equals(record.packetId))) {
                    return record;
                }
            }
        }
        return null;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        RECORDS.clear();

        Path filePath = getRecordFile();
        if (!Files.exists(filePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            List<PacketIdRecord> loadedRecords = GSON.fromJson(reader, RECORD_LIST_TYPE);
            if (loadedRecords != null) {
                for (PacketIdRecord record : loadedRecords) {
                    if (record == null) {
                        continue;
                    }
                    if (record.packetClassName == null || record.packetClassName.trim().isEmpty()) {
                        continue;
                    }
                    record.direction = safe(record.direction);
                    record.channel = safe(record.channel);
                    record.occurrenceCount = Math.max(1, record.occurrenceCount);
                    record.lastSeenAt = Math.max(record.firstSeenAt, record.lastSeenAt);
                    RECORDS.add(record);
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to load packet id records", e);
        }
    }

    private static void scheduleSave() {
        if (!SAVE_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        SAVE_EXECUTOR.execute(PacketIdRecordManager::saveNow);
    }

    private static void saveNow() {
        List<PacketIdRecord> snapshot;
        synchronized (PacketIdRecordManager.class) {
            if (!dirty) {
                SAVE_SCHEDULED.set(false);
                return;
            }
            snapshot = new ArrayList<>();
            for (PacketIdRecord record : RECORDS) {
                snapshot.add(copyOf(record));
            }
            dirty = false;
        }

        try {
            Path filePath = getRecordFile();
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
        } catch (Exception e) {
            synchronized (PacketIdRecordManager.class) {
                dirty = true;
            }
            zszlScriptMod.LOGGER.error("Failed to save packet id records", e);
        } finally {
            SAVE_SCHEDULED.set(false);
            synchronized (PacketIdRecordManager.class) {
                if (dirty) {
                    scheduleSave();
                }
            }
        }
    }

    private static Path getRecordFile() {
        return Paths.get(ModConfig.CONFIG_DIR, "PacketSnapshots", "packet_id_records.json");
    }

    private static PacketIdRecord copyOf(PacketIdRecord source) {
        PacketIdRecord copy = new PacketIdRecord();
        copy.direction = safe(source.direction);
        copy.isFmlPacket = source.isFmlPacket;
        copy.packetId = source.packetId;
        copy.channel = safe(source.channel);
        copy.packetClassName = safe(source.packetClassName);
        copy.firstSeenAt = source.firstSeenAt;
        copy.lastSeenAt = source.lastSeenAt;
        copy.occurrenceCount = Math.max(1, source.occurrenceCount);
        return copy;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}