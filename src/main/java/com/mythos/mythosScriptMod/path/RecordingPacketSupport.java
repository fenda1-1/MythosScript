package com.mythos.mythosScriptMod.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mythos.mythosScriptMod.utils.JsonCopy;
import com.mythos.mythosScriptMod.utils.PacketCaptureHandler;

/**
 * Serialization and correlation helpers for packets attached to recorded input
 * actions. Keeping this format beside the path model lets recordings remain
 * self-contained after the live packet buffer has been cleared.
 */
public final class RecordingPacketSupport {
    public static final String PACKETS_KEY = "_recordedPackets";
    public static final String INPUT_TIMESTAMP_KEY = "_recordingInputTimestamp";
    public static final String COUNT_KEY = "_recordingPacketCount";
    /** Legacy keys retained only so older in-memory objects read cleanly. */
    @Deprecated
    public static final String WINDOW_SECONDS_KEY = "_recordingPacketWindowSeconds";
    @Deprecated
    public static final String RANGE_KEY = "_recordingPacketRange";
    /** Packets captured at or after the input timestamp to attach to each action. */
    public static final int DEFAULT_PACKET_COUNT = 5;
    public static final int MAX_PACKET_COUNT = 50;
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

    /**
     * Returns up to {@code count} packets captured at or after the input
     * timestamp. A server cannot respond before the action is sent, so only the
     * post-input side carries response data.
     */
    public static List<PacketEntry> snapshotAfter(long inputTimestamp, int count, long minimumTimestamp) {
        int limit = clampPacketCount(count);
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<PacketEntry> result = new ArrayList<PacketEntry>(limit);
        synchronized (PacketCaptureHandler.capturedPackets) {
            collectAfter(PacketCaptureHandler.capturedPackets, inputTimestamp, minimumTimestamp, limit, result, C2S);
        }
        synchronized (PacketCaptureHandler.capturedReceivedPackets) {
            collectAfter(PacketCaptureHandler.capturedReceivedPackets, inputTimestamp, minimumTimestamp, limit, result, S2C);
        }
        Collections.sort(result, new Comparator<PacketEntry>() {
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
        if (result.size() > limit) {
            return new ArrayList<PacketEntry>(result.subList(0, limit));
        }
        return result;
    }

    /** Packets in {@code source} at or after {@code inputTimestamp}, appending at most {@code limit} to {@code out}. */
    private static void collectAfter(List<PacketCaptureHandler.CapturedPacketData> source, long inputTimestamp,
            long minimumTimestamp, int limit, List<PacketEntry> out, String direction) {
        for (PacketCaptureHandler.CapturedPacketData packet : source) {
            if (out.size() >= limit) {
                break;
            }
            if (packet == null || packet.getLastTimestamp() < inputTimestamp) {
                continue;
            }
            if (minimumTimestamp > 0L && packet.getLastTimestamp() < minimumTimestamp) {
                continue;
            }
            out.add(new PacketEntry(direction, packet));
        }
    }

    /** How many captured packets fall at or after {@code inputTimestamp} (used by readiness checks). */
    public static int countAfter(long inputTimestamp, long minimumTimestamp) {
        int total = 0;
        synchronized (PacketCaptureHandler.capturedPackets) {
            for (PacketCaptureHandler.CapturedPacketData packet : PacketCaptureHandler.capturedPackets) {
                if (packet != null && packet.getLastTimestamp() >= inputTimestamp
                        && (minimumTimestamp <= 0L || packet.getLastTimestamp() >= minimumTimestamp)) {
                    total++;
                }
            }
        }
        synchronized (PacketCaptureHandler.capturedReceivedPackets) {
            for (PacketCaptureHandler.CapturedPacketData packet : PacketCaptureHandler.capturedReceivedPackets) {
                if (packet != null && packet.getLastTimestamp() >= inputTimestamp
                        && (minimumTimestamp <= 0L || packet.getLastTimestamp() >= minimumTimestamp)) {
                    total++;
                }
            }
        }
        return total;
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
            return JsonCopy.deepCopyObject(value);
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
            result.addProperty("targetType", "channel");
            result.addProperty("channel", safe(packet.channel));
        } else if (packet.packetId != null) {
            result.addProperty("targetType", "packetId");
            // Store the id as hex text so the hex-only parser and the UI agree on "0x14".
            result.addProperty("packetId", String.format("0x%02X", packet.packetId.intValue()));
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

    /**
     * Removes the recording-scratch keys (packet payload, timestamps, window
     * settings) from a params object. These exist only to correlate live
     * captures during a recording session and must never reach disk — including
     * the copy embedded in {@link #ORIGINAL_PARAMS_KEY}. Mutates {@code params}
     * in place.
     */
    public static void stripRecordingKeys(JsonObject params) {
        if (params == null) {
            return;
        }
        params.remove(PACKETS_KEY);
        params.remove(INPUT_TIMESTAMP_KEY);
        params.remove(COUNT_KEY);
        params.remove(WINDOW_SECONDS_KEY);
        params.remove(RANGE_KEY);
        if (params.has(ORIGINAL_PARAMS_KEY) && params.get(ORIGINAL_PARAMS_KEY).isJsonObject()) {
            stripRecordingKeys(params.getAsJsonObject(ORIGINAL_PARAMS_KEY));
        }
    }

    /**
     * Shallow copy of {@code params} with the recording-scratch keys omitted.
     * Used at serialization time so live packet data never reaches disk while
     * the in-memory object stays intact for the open review UI.
     */
    public static JsonObject paramsForSave(JsonObject params) {
        if (params == null) {
            return new JsonObject();
        }
        JsonObject copy = new JsonObject();
        for (java.util.Map.Entry<String, JsonElement> e : params.entrySet()) {
            String key = e.getKey();
            if (PACKETS_KEY.equals(key) || INPUT_TIMESTAMP_KEY.equals(key) || COUNT_KEY.equals(key)
                    || WINDOW_SECONDS_KEY.equals(key) || RANGE_KEY.equals(key)) {
                continue;
            }
            JsonElement value = e.getValue();
            if (ORIGINAL_PARAMS_KEY.equals(key) && value != null && value.isJsonObject()) {
                value = paramsForSave(value.getAsJsonObject());
            }
            copy.add(key, value);
        }
        return copy;
    }

    public static String normalizeDirection(String direction) {
        return S2C.equalsIgnoreCase(direction) || "INBOUND".equalsIgnoreCase(direction) ? S2C : C2S;
    }

    public static int clampPacketCount(int count) {
        return Math.max(0, Math.min(MAX_PACKET_COUNT, count));
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
