package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.packet.InputTimelineManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static com.zszl.zszlScriptMod.mcp.McpJson.bool;
import static com.zszl.zszlScriptMod.mcp.McpJson.integer;
import static com.zszl.zszlScriptMod.mcp.McpJson.object;
import static com.zszl.zszlScriptMod.mcp.McpJson.string;

/**
 * Read-only correlation between the packet workbench input timeline and the
 * bounded captured packet buffers. This is intentionally kept separate from
 * the packet CRUD tool so the existing list/capture contract remains stable.
 * Calls are made on the Minecraft client thread by {@link McpGameService}.
 */
public final class McpPacketTrace {
    static final int MAX_SELECTORS = 32;
    static final int MAX_INPUTS = 400;
    static final int MAX_PACKETS_PER_INPUT = 500;
    static final int MAX_TOTAL_PACKETS = 5000;
    static final long MAX_WINDOW_MS = 60_000L;

    private McpPacketTrace() {
    }

    public static JsonObject call(JsonObject arguments) {
        JsonObject p = arguments == null ? new JsonObject() : arguments;
        Options options = Options.from(p);
        InputSelection inputSelection = selectInputs(p, options.inputLimit);
        List<PacketSnapshot> packetPool = snapshotPackets(options.directions);
        JsonArray traces = new JsonArray();
        int returnedPackets = 0;
        boolean packetTruncated = false;

        for (InputSnapshot input : inputSelection.events) {
            Correlation correlation = correlate(input.timestamp, packetPool, options.windowMs,
                    options.before, options.after, options.packetLimit);
            int remaining = Math.max(0, options.maxTotalPackets - returnedPackets);
            List<PacketMatch> returned = correlation.matches;
            if (returned.size() > remaining) {
                returned = new ArrayList<PacketMatch>(returned.subList(0, remaining));
                packetTruncated = true;
            }
            if (correlation.truncated) packetTruncated = true;
            returnedPackets += returned.size();

            JsonArray packets = new JsonArray();
            for (PacketMatch match : returned) {
                packets.add(match.toJson(input.timestamp, options.includeHex, options.includeDecoded));
            }
            JsonObject row = object("input", input.toJson(), "packets", packets,
                    "candidatePacketCount", correlation.candidateCount,
                    "packetCount", returned.size(),
                    "packetTruncated", correlation.truncated || returned.size() < correlation.matches.size());
            traces.add(row);
        }

        int c2s = packetCount(PacketCaptureHandler.capturedPackets);
        int s2c = packetCount(PacketCaptureHandler.capturedReceivedPackets);
        return object("inputSource", "packet_capture_timeline",
                "capturing", PacketCaptureHandler.isCapturing,
                "directions", new ArrayList<String>(options.directions),
                "windowMs", options.windowMs, "before", options.before, "after", options.after,
                "packetLimit", options.packetLimit, "maxTotalPackets", options.maxTotalPackets,
                "includeHex", options.includeHex, "includeDecoded", options.includeDecoded,
                "inputs", traces, "inputCount", traces.size(), "candidateInputCount", inputSelection.candidateCount,
                "inputTruncated", inputSelection.truncated,
                "packetCount", returnedPackets, "packetTruncated", packetTruncated,
                "availablePackets", object("C2S", c2s, "S2C", s2c),
                "limits", object("maxSelectors", MAX_SELECTORS, "maxInputs", MAX_INPUTS,
                        "maxPacketsPerInput", MAX_PACKETS_PER_INPUT, "maxTotalPackets", MAX_TOTAL_PACKETS,
                        "maxWindowMs", MAX_WINDOW_MS),
                "next", "Use eventIds or narrower time/key/button filters when inputTruncated or packetTruncated is true");
    }

    private static InputSelection selectInputs(JsonObject p, int inputLimit) {
        List<InputSelector> selectors = new ArrayList<InputSelector>();
        if (p.has("inputs")) {
            if (!p.get("inputs").isJsonArray()) throw new IllegalArgumentException("inputs must be an array");
            JsonArray values = p.getAsJsonArray("inputs");
            if (values.size() == 0 || values.size() > MAX_SELECTORS)
                throw new IllegalArgumentException("inputs must contain 1.." + MAX_SELECTORS + " selectors");
            for (JsonElement value : values) {
                if (!value.isJsonObject()) throw new IllegalArgumentException("Each inputs entry must be an object");
                selectors.add(InputSelector.from(value.getAsJsonObject()));
            }
        } else {
            selectors.add(InputSelector.from(p));
        }

        List<InputSnapshot> source = new ArrayList<InputSnapshot>();
        for (InputTimelineManager.InputEventRecord record : InputTimelineManager.getEventsSnapshot())
            source.add(new InputSnapshot(record));
        Map<Long, InputSnapshot> matched = new LinkedHashMap<Long, InputSnapshot>();
        for (InputSelector selector : selectors) {
            for (InputSnapshot input : source) if (selector.matches(input)) matched.put(input.eventId, input);
        }
        List<InputSnapshot> events = new ArrayList<InputSnapshot>(matched.values());
        int candidateCount = events.size();
        Collections.sort(events, INPUT_NEWEST_FIRST);
        boolean truncated = candidateCount > inputLimit;
        if (truncated) events = new ArrayList<InputSnapshot>(events.subList(0, inputLimit));
        Collections.sort(events, INPUT_CHRONOLOGICAL);
        return new InputSelection(events, candidateCount, truncated);
    }

    private static List<PacketSnapshot> snapshotPackets(Set<String> directions) {
        List<PacketSnapshot> result = new ArrayList<PacketSnapshot>();
        if (directions.contains("C2S")) snapshotDirection(result, PacketCaptureHandler.capturedPackets, "C2S");
        if (directions.contains("S2C")) snapshotDirection(result, PacketCaptureHandler.capturedReceivedPackets, "S2C");
        Collections.sort(result, PACKET_CHRONOLOGICAL);
        return result;
    }

    private static void snapshotDirection(List<PacketSnapshot> result,
            List<PacketCaptureHandler.CapturedPacketData> source, String direction) {
        synchronized (source) {
            for (int i = 0; i < source.size(); i++) {
                PacketCaptureHandler.CapturedPacketData packet = source.get(i);
                if (packet == null) continue;
                result.add(new PacketSnapshot(direction, i, packet, packet.timestamp,
                        packet.getLastTimestamp(), packet.getOccurrenceCount(), packet.getTotalPayloadBytes()));
            }
        }
    }

    private static int packetCount(List<PacketCaptureHandler.CapturedPacketData> source) {
        synchronized (source) { return source.size(); }
    }

    static Correlation correlate(long inputTimestamp, List<PacketSnapshot> packetPool,
            long windowMs, int before, int after, int packetLimit) {
        long lower = subtract(inputTimestamp, windowMs);
        long upper = add(inputTimestamp, windowMs);
        LinkedHashMap<PacketSnapshot, LinkedHashSet<String>> selected =
                new LinkedHashMap<PacketSnapshot, LinkedHashSet<String>>();

        int firstWindowIndex = lowerBound(packetPool, lower);
        while (firstWindowIndex > 0 && packetPool.get(firstWindowIndex - 1).lastTimestamp >= lower)
            firstWindowIndex--;
        for (int i = firstWindowIndex; i < packetPool.size(); i++) {
            PacketSnapshot packet = packetPool.get(i);
            if (packet.timestamp > upper) break;
            if (packet.lastTimestamp >= lower) add(selected, packet, "timeWindow");
        }

        int pivot = lowerBound(packetPool, inputTimestamp);
        int beforeAdded = 0;
        for (int i = pivot - 1; i >= 0 && beforeAdded < before; i--) {
            PacketSnapshot packet = packetPool.get(i);
            if (packet.lastTimestamp < inputTimestamp) {
                add(selected, packet, "beforeCount");
                beforeAdded++;
            }
        }
        int afterAdded = 0;
        for (int i = pivot; i < packetPool.size() && afterAdded < after; i++) {
            PacketSnapshot packet = packetPool.get(i);
            if (packet.timestamp > inputTimestamp) {
                add(selected, packet, "afterCount");
                afterAdded++;
            }
        }

        List<PacketMatch> matches = new ArrayList<PacketMatch>();
        for (Map.Entry<PacketSnapshot, LinkedHashSet<String>> entry : selected.entrySet())
            matches.add(new PacketMatch(entry.getKey(), entry.getValue()));
        boolean truncated = matches.size() > packetLimit;
        if (truncated) {
            matches = closest(matches, inputTimestamp, packetLimit);
        }
        Collections.sort(matches, new Comparator<PacketMatch>() {
            @Override public int compare(PacketMatch left, PacketMatch right) {
                return PACKET_CHRONOLOGICAL.compare(left.packet, right.packet);
            }
        });
        return new Correlation(matches, selected.size(), truncated);
    }

    private static int lowerBound(List<PacketSnapshot> packets, long timestamp) {
        int low = 0, high = packets.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (packets.get(middle).timestamp < timestamp) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static List<PacketMatch> closest(List<PacketMatch> source, final long inputTimestamp, int limit) {
        PriorityQueue<PacketMatch> queue = new PriorityQueue<PacketMatch>(limit, new Comparator<PacketMatch>() {
            @Override public int compare(PacketMatch left, PacketMatch right) {
                int result = Long.compare(right.absoluteDelta(inputTimestamp), left.absoluteDelta(inputTimestamp));
                return result != 0 ? result : PACKET_CHRONOLOGICAL.compare(right.packet, left.packet);
            }
        });
        for (PacketMatch match : source) {
            queue.offer(match);
            if (queue.size() > limit) queue.poll();
        }
        return new ArrayList<PacketMatch>(queue);
    }

    private static void add(Map<PacketSnapshot, LinkedHashSet<String>> selected,
            PacketSnapshot packet, String reason) {
        LinkedHashSet<String> reasons = selected.get(packet);
        if (reasons == null) {
            reasons = new LinkedHashSet<String>();
            selected.put(packet, reasons);
        }
        reasons.add(reason);
    }

    private static long subtract(long value, long amount) {
        return value < Long.MIN_VALUE + amount ? Long.MIN_VALUE : value - amount;
    }

    private static long add(long value, long amount) {
        return value > Long.MAX_VALUE - amount ? Long.MAX_VALUE : value + amount;
    }

    private static final Comparator<InputSnapshot> INPUT_CHRONOLOGICAL = new Comparator<InputSnapshot>() {
        @Override public int compare(InputSnapshot left, InputSnapshot right) {
            int result = Long.compare(left.timestamp, right.timestamp);
            return result != 0 ? result : Long.compare(left.eventId, right.eventId);
        }
    };

    private static final Comparator<InputSnapshot> INPUT_NEWEST_FIRST = new Comparator<InputSnapshot>() {
        @Override public int compare(InputSnapshot left, InputSnapshot right) {
            int result = Long.compare(right.timestamp, left.timestamp);
            return result != 0 ? result : Long.compare(right.eventId, left.eventId);
        }
    };

    private static final Comparator<PacketSnapshot> PACKET_CHRONOLOGICAL = new Comparator<PacketSnapshot>() {
        @Override public int compare(PacketSnapshot left, PacketSnapshot right) {
            int result = Long.compare(left.timestamp, right.timestamp);
            if (result != 0) return result;
            result = Long.compare(left.lastTimestamp, right.lastTimestamp);
            if (result != 0) return result;
            result = left.direction.compareTo(right.direction);
            return result != 0 ? result : Integer.compare(left.index, right.index);
        }
    };

    private static final class Options {
        final LinkedHashSet<String> directions;
        final long windowMs;
        final int before, after, packetLimit, maxTotalPackets, inputLimit;
        final boolean includeHex, includeDecoded;

        private Options(LinkedHashSet<String> directions, long windowMs, int before, int after,
                int packetLimit, int maxTotalPackets, int inputLimit, boolean includeHex, boolean includeDecoded) {
            this.directions = directions;
            this.windowMs = windowMs;
            this.before = before;
            this.after = after;
            this.packetLimit = packetLimit;
            this.maxTotalPackets = maxTotalPackets;
            this.inputLimit = inputLimit;
            this.includeHex = includeHex;
            this.includeDecoded = includeDecoded;
        }

        static Options from(JsonObject p) {
            LinkedHashSet<String> directions = new LinkedHashSet<String>();
            if (p.has("directions")) {
                if (!p.get("directions").isJsonArray()) throw new IllegalArgumentException("directions must be an array");
                for (JsonElement value : p.getAsJsonArray("directions")) addDirection(directions, value.getAsString());
            } else if (p.has("direction")) addDirection(directions, string(p, "direction"));
            else { directions.add("C2S"); directions.add("S2C"); }
            if (directions.isEmpty()) throw new IllegalArgumentException("directions cannot be empty");
            long window = McpEventJournal.number(p, "windowMs", 200L);
            if (window > MAX_WINDOW_MS) throw new IllegalArgumentException("windowMs must be 0.." + MAX_WINDOW_MS);
            return new Options(directions, window,
                    integer(p, "before", 0, 0, MAX_PACKETS_PER_INPUT),
                    integer(p, "after", 0, 0, MAX_PACKETS_PER_INPUT),
                    integer(p, "packetLimit", 100, 1, MAX_PACKETS_PER_INPUT),
                    integer(p, "maxTotalPackets", MAX_TOTAL_PACKETS, 1, MAX_TOTAL_PACKETS),
                    integer(p, "inputLimit", 100, 1, MAX_INPUTS),
                    bool(p, "includeHex", true), bool(p, "includeDecoded", true));
        }

        private static void addDirection(Set<String> directions, String raw) {
            String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if ("BOTH".equals(value) || "ALL".equals(value)) {
                directions.add("C2S"); directions.add("S2C");
            } else if ("C2S".equals(value) || "S2C".equals(value)) directions.add(value);
            else throw new IllegalArgumentException("directions must contain C2S, S2C or BOTH");
        }
    }

    private static final class InputSelection {
        final List<InputSnapshot> events;
        final int candidateCount;
        final boolean truncated;
        InputSelection(List<InputSnapshot> events, int candidateCount, boolean truncated) {
            this.events = events; this.candidateCount = candidateCount; this.truncated = truncated;
        }
    }

    private static final class InputSelector {
        final Set<Long> eventIds;
        final Set<Integer> sessionIds, keyCodes, buttons;
        final Set<String> types, keys, actions, guiTitles, screenNames;
        final long fromMs, toMs;
        final String query;

        static InputSelector from(JsonObject p) {
            Set<String> rawTypes = strings(p, "types");
            Set<String> types = new LinkedHashSet<String>();
            for (String type : rawTypes) {
                if ("keyboard".equals(type) || "键盘".equals(type)) type = "key";
                else if ("鼠标".equals(type)) type = "mouse";
                if (!("key".equals(type) || "mouse".equals(type)))
                    throw new IllegalArgumentException("types must contain key or mouse");
                types.add(type);
            }
            long fromMs = McpEventJournal.number(p, "fromMs", Long.MIN_VALUE);
            long toMs = McpEventJournal.number(p, "toMs", Long.MAX_VALUE);
            if (fromMs > toMs) throw new IllegalArgumentException("fromMs exceeds toMs");
            return new InputSelector(longSet(p, "eventIds"), intSet(p, "sessionIds"), intSet(p, "keyCodes"),
                    buttonSet(p, "buttons"), types, strings(p, "keys"), strings(p, "actions"),
                    strings(p, "guiTitles"), strings(p, "screenNames"),
                    fromMs, toMs,
                    p.has("query") ? string(p, "query").toLowerCase(Locale.ROOT) : "");
        }

        private InputSelector(Set<Long> eventIds, Set<Integer> sessionIds, Set<Integer> keyCodes,
                Set<Integer> buttons, Set<String> types, Set<String> keys, Set<String> actions,
                Set<String> guiTitles, Set<String> screenNames, long fromMs, long toMs, String query) {
            this.eventIds = eventIds; this.sessionIds = sessionIds; this.keyCodes = keyCodes; this.buttons = buttons;
            this.types = types; this.keys = keys; this.actions = actions; this.guiTitles = guiTitles;
            this.screenNames = screenNames; this.fromMs = fromMs; this.toMs = toMs; this.query = query;
        }

        boolean matches(InputSnapshot input) {
            if (!eventIds.isEmpty() && !eventIds.contains(input.eventId)) return false;
            if (!sessionIds.isEmpty() && !sessionIds.contains(input.sessionId)) return false;
            if (!types.isEmpty() && !types.contains(input.type)) return false;
            if (!keys.isEmpty() && !containsIgnoreCase(keys, input.detail)) return false;
            if (!keyCodes.isEmpty() && !keyCodes.contains(input.keyCode)) return false;
            if (!buttons.isEmpty() && !buttons.contains(input.mouseButton)) return false;
            if (!actions.isEmpty() && !containsIgnoreCase(actions, input.action)) return false;
            if (!guiTitles.isEmpty() && !containsIgnoreCase(guiTitles, input.guiTitle)) return false;
            if (!screenNames.isEmpty() && !containsIgnoreCase(screenNames, input.screenSimpleName)) return false;
            if (input.timestamp < fromMs || input.timestamp > toMs) return false;
            if (!query.isEmpty() && !input.searchText.contains(query)) return false;
            return true;
        }

        private static boolean containsIgnoreCase(Set<String> values, String candidate) {
            String normalized = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
            for (String value : values) if (value.equals(normalized)) return true;
            return false;
        }
    }

    static final class InputSnapshot {
        final long eventId, timestamp;
        final int sessionId, keyCode, mouseButton;
        final String type, category, action, detail, guiTitle, screenSimpleName, searchText;

        InputSnapshot(InputTimelineManager.InputEventRecord record) {
            eventId = record.getEventId(); timestamp = record.getTimestamp(); sessionId = record.getSessionId();
            category = record.getCategory(); action = record.getAction(); detail = record.getDetail();
            guiTitle = record.getGuiTitle(); screenSimpleName = record.getScreenSimpleName();
            keyCode = record.getKeyCode(); mouseButton = record.getMouseButton();
            type = "鼠标".equals(category) ? "mouse" : "key";
            searchText = (type + " " + category + " " + action + " " + detail + " "
                    + guiTitle + " " + screenSimpleName).toLowerCase(Locale.ROOT);
        }

        JsonObject toJson() {
            return object("eventId", eventId, "timestampMs", timestamp, "sessionId", sessionId,
                    "type", type, "category", category, "action", action, "detail", detail,
                    "keyCode", keyCode < 0 ? null : keyCode,
                    "keyName", "key".equals(type) ? detail : null,
                    "mouseButton", mouseButton < 0 ? null : mouseButton,
                    "mouseButtonName", mouseButton < 0 ? null : buttonName(mouseButton),
                    "guiTitle", guiTitle, "screenSimpleName", screenSimpleName);
        }
    }

    static final class PacketSnapshot {
        final String direction;
        final int index;
        final PacketCaptureHandler.CapturedPacketData packet;
        final long timestamp, lastTimestamp;
        final int occurrenceCount, totalPayloadBytes;

        PacketSnapshot(String direction, int index, PacketCaptureHandler.CapturedPacketData packet,
                long timestamp, long lastTimestamp, int occurrenceCount, int totalPayloadBytes) {
            this.direction = direction; this.index = index; this.packet = packet; this.timestamp = timestamp;
            this.lastTimestamp = lastTimestamp; this.occurrenceCount = occurrenceCount; this.totalPayloadBytes = totalPayloadBytes;
        }
    }

    static final class PacketMatch {
        final PacketSnapshot packet;
        final Set<String> reasons;
        PacketMatch(PacketSnapshot packet, Set<String> reasons) { this.packet = packet; this.reasons = reasons; }

        long absoluteDelta(long inputTimestamp) { return Math.abs(delta(inputTimestamp)); }

        JsonObject toJson(long inputTimestamp, boolean includeHex, boolean includeDecoded) {
            long delta = delta(inputTimestamp);
            JsonObject result = object("index", packet.index, "direction", packet.direction,
                    "timestamp", packet.timestamp, "lastTimestamp", packet.lastTimestamp,
                    "deltaMs", delta, "absDeltaMs", Math.abs(delta),
                    "relation", relation(inputTimestamp), "matchReasons", reasons,
                    "class", packet.packet.packetClassName, "isFmlPacket", packet.packet.isFmlPacket,
                    "packetId", packet.packet.packetId, "channel", packet.packet.channel,
                    "payloadSize", packet.packet.getPayloadSize(), "occurrenceCount", packet.occurrenceCount,
                    "totalPayloadBytes", packet.totalPayloadBytes);
            if (includeHex) result.addProperty("hex", safe(packet.packet.getHexData()));
            if (includeDecoded) result.addProperty("decoded", safe(packet.packet.getDecodedData()));
            return result;
        }

        private long delta(long inputTimestamp) {
            if (inputTimestamp < packet.timestamp) return packet.timestamp - inputTimestamp;
            if (inputTimestamp > packet.lastTimestamp) return packet.lastTimestamp - inputTimestamp;
            return 0L;
        }

        private String relation(long inputTimestamp) {
            if (packet.lastTimestamp < inputTimestamp) return "before";
            if (packet.timestamp > inputTimestamp) return "after";
            return "around";
        }
    }

    static final class Correlation {
        final List<PacketMatch> matches;
        final int candidateCount;
        final boolean truncated;
        Correlation(List<PacketMatch> matches, int candidateCount, boolean truncated) {
            this.matches = matches; this.candidateCount = candidateCount; this.truncated = truncated;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static String buttonName(int button) {
        switch (button) {
        case 0: return "LEFT";
        case 1: return "RIGHT";
        case 2: return "MIDDLE";
        default: return "BUTTON_" + button;
        }
    }

    private static Set<String> strings(JsonObject p, String key) {
        Set<String> result = new LinkedHashSet<String>();
        if (!p.has(key)) return result;
        if (!p.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        if (p.getAsJsonArray(key).size() > 256) throw new IllegalArgumentException(key + " has too many entries");
        for (JsonElement value : p.getAsJsonArray(key)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException(key + " must contain strings");
            result.add(value.getAsString().trim().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static Set<Integer> intSet(JsonObject p, String key) {
        Set<Integer> result = new LinkedHashSet<Integer>();
        if (!p.has(key)) return result;
        if (!p.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        if (p.getAsJsonArray(key).size() > 256) throw new IllegalArgumentException(key + " has too many entries");
        for (JsonElement value : p.getAsJsonArray(key)) {
            try { result.add(value.getAsBigDecimal().intValueExact()); }
            catch (Exception e) { throw new IllegalArgumentException(key + " must contain integers"); }
        }
        return result;
    }

    private static Set<Long> longSet(JsonObject p, String key) {
        Set<Long> result = new LinkedHashSet<Long>();
        if (!p.has(key)) return result;
        if (!p.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        if (p.getAsJsonArray(key).size() > 256) throw new IllegalArgumentException(key + " has too many entries");
        for (JsonElement value : p.getAsJsonArray(key)) {
            try { result.add(value.getAsBigDecimal().longValueExact()); }
            catch (Exception e) { throw new IllegalArgumentException(key + " must contain integers"); }
        }
        return result;
    }

    private static Set<Integer> buttonSet(JsonObject p, String key) {
        Set<Integer> result = new LinkedHashSet<Integer>();
        if (!p.has(key)) return result;
        if (!p.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        if (p.getAsJsonArray(key).size() > 32) throw new IllegalArgumentException(key + " has too many entries");
        for (JsonElement value : p.getAsJsonArray(key)) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                try { result.add(value.getAsBigDecimal().intValueExact()); continue; }
                catch (Exception e) { throw new IllegalArgumentException("Invalid mouse button"); }
            }
            String name = value.getAsString().trim().toUpperCase(Locale.ROOT);
            if ("LEFT".equals(name) || "左键".equals(name)) result.add(0);
            else if ("RIGHT".equals(name) || "右键".equals(name)) result.add(1);
            else if ("MIDDLE".equals(name) || "中键".equals(name)) result.add(2);
            else if (name.startsWith("BUTTON_")) {
                try { result.add(Integer.parseInt(name.substring(7))); }
                catch (Exception e) { throw new IllegalArgumentException("Invalid mouse button: " + name); }
            } else throw new IllegalArgumentException("Invalid mouse button: " + name);
        }
        return result;
    }
}
