package com.zszl.zszlScriptMod.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

/**
 * Serialization and correlation helpers for packets attached to recorded input
 * actions. Keeping this format beside the path model lets recordings remain
 * self-contained after the live packet buffer has been cleared.
 */
public final class RecordingPacketSupport {
    public static final String PACKETS_KEY = "_recordedPackets";
    public static final String INPUT_TIMESTAMP_KEY = "_recordingInputTimestamp";
    public static final String WINDOW_SECONDS_KEY = "_recordingPacketWindowSeconds";
    /** Legacy count-based key retained so older recordings can still be read. */
    public static final String RANGE_KEY = "_recordingPacketRange";
    public static final int DEFAULT_WINDOW_SECONDS = 2;
    public static final String ORIGINAL_TYPE_KEY = "_recordingOriginalType";
    public static final String ORIGINAL_PARAMS_KEY = "_recordingOriginalParams";

    public static final String C2S = "C2S";
    public static final String S2C = "S2C";

    private RecordingPacketSupport() {
    }

    /** A captured packet plus the direction in which it travelled. */
    public static final class PacketEntry {
        public final String direction;
        public final PacketCaptureHandler.CapturedPacketData packet;

        public PacketEntry(String direction, PacketCaptureHandler.CapturedPacketData packet) {
            this.direction = normalizeDirection(direction);
            this.packet = packet;
        }
    }

    /** Returns packets whose capture interval overlaps the time window around an input event. */
    public static List<PacketEntry> snapshotAround(long inputTimestamp, int windowSeconds, long minimumTimestamp) {
        return snapshotBetween(inputTimestamp - clampWindowSeconds(windowSeconds) * 1000L,
                inputTimestamp + clampWindowSeconds(windowSeconds) * 1000L, minimumTimestamp);
    }

    /** Reads the original count-based format used by recordings created before the time-window setting. */
    public static List<PacketEntry> snapshotAroundCount(long inputTimestamp, int range, long minimumTimestamp) {
        int count = clampRange(range);
        List<PacketEntry> all = snapshotBetween(Long.MIN_VALUE, Long.MAX_VALUE, minimumTimestamp);
        if (all.isEmpty()) {
            return all;
        }
        int insertion = 0;
        while (insertion < all.size() && packetTime(all.get(insertion).packet) < inputTimestamp) {
            insertion++;
        }
        int start = Math.max(0, insertion - count);
        int end = Math.min(all.size(), insertion + count);
        return new ArrayList<PacketEntry>(all.subList(start, end));
    }

    private static List<PacketEntry> snapshotBetween(long windowStart, long windowEnd, long minimumTimestamp) {
        List<PacketEntry> all = new ArrayList<PacketEntry>();
        synchronized (PacketCaptureHandler.capturedPackets) {
            for (PacketCaptureHandler.CapturedPacketData packet : PacketCaptureHandler.capturedPackets) {
                if (overlapsWindow(packet, windowStart, windowEnd, minimumTimestamp)) {
                    all.add(new PacketEntry(C2S, packet));
                }
            }
        }
        synchronized (PacketCaptureHandler.capturedReceivedPackets) {
            for (PacketCaptureHandler.CapturedPacketData packet : PacketCaptureHandler.capturedReceivedPackets) {
                if (overlapsWindow(packet, windowStart, windowEnd, minimumTimestamp)) {
                    all.add(new PacketEntry(S2C, packet));
                }
            }
        }
        Collections.sort(all, new Comparator<PacketEntry>() {
            @Override
            public int compare(PacketEntry left, PacketEntry right) {
                long leftTime = packetTime(left.packet);
                long rightTime = packetTime(right.packet);
                if (leftTime < rightTime) return -1;
                if (leftTime > rightTime) return 1;
                if (left.direction.equals(right.direction)) return 0;
                return C2S.equals(left.direction) ? -1 : 1;
            }
        });
        return all;
    }

    public static JsonArray toJson(List<PacketEntry> entries) {
        JsonArray result = new JsonArray();
        if (entries == null) {
            return result;
        }
        for (PacketEntry entry : entries) {
            if (entry == null || entry.packet == null) {
                continue;
            }
            result.add(toJson(entry.direction, entry.packet));
        }
        return result;
    }

    public static JsonObject toJson(String direction, PacketCaptureHandler.CapturedPacketData packet) {
        JsonObject result = new JsonObject();
        if (packet == null) {
            return result;
        }
        result.addProperty("direction", normalizeDirection(direction));
        result.addProperty("timestamp", packet.timestamp);
        result.addProperty("lastTimestamp", packet.getLastTimestamp());
        result.addProperty("packetClassName", safe(packet.packetClassName));
        result.addProperty("isFmlPacket", packet.isFmlPacket);
        if (packet.packetId != null) {
            result.addProperty("packetId", packet.packetId.intValue());
        }
        result.addProperty("channel", safe(packet.channel));
        result.addProperty("hex", packet.getHexData());
        result.addProperty("occurrenceCount", packet.getOccurrenceCount());
        result.addProperty("totalPayloadBytes", packet.getTotalPayloadBytes());
        return result;
    }

    public static List<PacketEntry> fromJson(JsonObject params) {
        List<PacketEntry> result = new ArrayList<PacketEntry>();
        if (params == null || !params.has(PACKETS_KEY) || !params.get(PACKETS_KEY).isJsonArray()) {
            return result;
        }
        for (JsonElement element : params.getAsJsonArray(PACKETS_KEY)) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject value = element.getAsJsonObject();
            PacketCaptureHandler.CapturedPacketData packet = fromJsonPacket(value);
            if (packet != null) {
                result.add(new PacketEntry(value.has("direction") ? value.get("direction").getAsString() : C2S,
                        packet));
            }
        }
        return result;
    }

    public static List<PacketCaptureHandler.CapturedPacketData> packetsForDirection(JsonObject params,
            String direction) {
        String normalized = normalizeDirection(direction);
        List<PacketCaptureHandler.CapturedPacketData> result = new ArrayList<PacketCaptureHandler.CapturedPacketData>();
        for (PacketEntry entry : fromJson(params)) {
            if (normalized.equals(entry.direction) && entry.packet != null) {
                result.add(entry.packet);
            }
        }
        return result;
    }

    public static PacketCaptureHandler.CapturedPacketData fromJsonPacket(JsonObject value) {
        if (value == null) {
            return null;
        }
        try {
            long timestamp = value.has("timestamp") ? value.get("timestamp").getAsLong() : System.currentTimeMillis();
            String className = value.has("packetClassName") ? value.get("packetClassName").getAsString() : "";
            boolean fml = value.has("isFmlPacket") && value.get("isFmlPacket").getAsBoolean();
            Integer packetId = value.has("packetId") && !value.get("packetId").isJsonNull()
                    ? Integer.valueOf(value.get("packetId").getAsInt()) : null;
            String channel = value.has("channel") ? value.get("channel").getAsString() : "";
            byte[] raw = parseHex(value.has("hex") ? value.get("hex").getAsString() : "");
            String decoded = value.has("decoded") ? value.get("decoded").getAsString() : "";
            PacketCaptureHandler.CapturedPacketData packet = new PacketCaptureHandler.CapturedPacketData(timestamp,
                    className, fml, packetId, channel, raw, decoded);
            if (value.has("lastTimestamp") || value.has("occurrenceCount") || value.has("totalPayloadBytes")) {
                long lastTimestamp = value.has("lastTimestamp") ? value.get("lastTimestamp").getAsLong() : timestamp;
                int occurrences = value.has("occurrenceCount") ? value.get("occurrenceCount").getAsInt() : 1;
                int totalBytes = value.has("totalPayloadBytes") ? value.get("totalPayloadBytes").getAsInt() : raw.length;
                packet.restoreAggregateState(lastTimestamp, occurrences, totalBytes);
            }
            return packet;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JsonObject copyObject(JsonObject value) {
        if (value == null) {
            return new JsonObject();
        }
        try {
            return new JsonParser().parse(value.toString()).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    public static JsonObject sendPacketParams(PacketCaptureHandler.CapturedPacketData packet, String direction) {
        JsonObject result = new JsonObject();
        if (packet == null) {
            return result;
        }
        result.addProperty("direction", normalizeDirection(direction));
        if (packet.isFmlPacket) {
            result.addProperty("channel", safe(packet.channel));
        } else if (packet.packetId != null) {
            result.addProperty("packetId", packet.packetId.intValue());
        }
        result.addProperty("hex", packet.getHexData());
        return result;
    }

    public static boolean isInputAction(String type) {
        if (type == null) return false;
        String normalized = type.toLowerCase(Locale.ROOT);
        return "key".equals(normalized) || "click".equals(normalized) || "window_click".equals(normalized)
                || "conditional_window_click".equals(normalized) || normalized.startsWith("rightclick")
                || "use_hotbar_item".equals(normalized) || "move_inventory_items_to_chest_slots".equals(normalized)
                || "hunt".equals(normalized);
    }

    public static boolean hasPacketAssociation(JsonObject params) {
        return params != null && params.has(PACKETS_KEY);
    }

    public static boolean hasOriginalAction(JsonObject params) {
        return params != null && params.has(ORIGINAL_TYPE_KEY) && params.has(ORIGINAL_PARAMS_KEY)
                && params.get(ORIGINAL_PARAMS_KEY).isJsonObject();
    }

    public static String normalizeDirection(String direction) {
        return S2C.equalsIgnoreCase(direction) || "INBOUND".equalsIgnoreCase(direction) ? S2C : C2S;
    }

    public static int clampWindowSeconds(int seconds) {
        return Math.max(0, Math.min(60, seconds));
    }

    /** @deprecated use {@link #clampWindowSeconds(int)}. */
    @Deprecated
    public static int clampRange(int range) {
        return Math.max(0, Math.min(100, range));
    }

    private static boolean overlapsWindow(PacketCaptureHandler.CapturedPacketData packet, long windowStart,
            long windowEnd, long minimumTimestamp) {
        if (packet == null) {
            return false;
        }
        long packetStart = packet.timestamp;
        long packetEnd = packet.getLastTimestamp();
        return (minimumTimestamp <= 0L || packetEnd >= minimumTimestamp)
                && packetEnd >= windowStart && packetStart <= windowEnd;
    }

    private static long packetTime(PacketCaptureHandler.CapturedPacketData packet) {
        return packet == null ? Long.MIN_VALUE : packet.getLastTimestamp();
    }

    private static byte[] parseHex(String value) {
        String source = value == null ? "" : value.replaceAll("[^0-9A-Fa-f]", "");
        if ((source.length() & 1) != 0) source = source.substring(0, source.length() - 1);
        byte[] result = new byte[source.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(source.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
