package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import java.util.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Received and displayed chat are independent streams, scoped to a connection. */
public final class McpChatHistory {
    public static final McpChatHistory INSTANCE = new McpChatHistory(1000, 8 * 1024 * 1024);
    private static final class Entry {
        final long id, timestamp;
        final String stream, type, plainText, formattedText, component;
        final int chatLineId;
        Entry(long id, String stream, String type, String plain, String formatted, String component, int lineId) {
            this.id = id; this.timestamp = System.currentTimeMillis(); this.stream = stream; this.type = type;
            this.plainText = plain; this.formattedText = formatted; this.component = component; this.chatLineId = lineId;
        }
        long bytes() { return 2L * (plainText.length() + formattedText.length() + component.length()); }
    }
    private final int capacity;
    private final long budget;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private long nextId, bytes, evictedThrough;
    private Object connection;
    private String connectionId = UUID.randomUUID().toString(), server = "";
    private boolean connected;

    public McpChatHistory(int capacity, long budget) { this.capacity = Math.max(1, capacity); this.budget = Math.max(1, budget); }
    public synchronized void connect(Object identity, String address) {
        connection = identity; connected = true; server = address == null ? "" : address;
        connectionId = UUID.randomUUID().toString();
        entries.clear(); bytes = 0; evictedThrough = nextId;
    }
    public synchronized void disconnect(Object identity) {
        if (identity == connection) { connected = false; connection = null; }
    }
    public synchronized void append(String stream, String type, String plain, String formatted, String component, int lineId, String address) {
        if (address != null && !address.isEmpty()) server = address;
        Entry entry = new Entry(++nextId, stream, type, plain == null ? "" : plain,
                formatted == null ? "" : formatted, component == null ? "{}" : component, lineId);
        entries.addLast(entry); bytes += entry.bytes();
        while (entries.size() > 1 && (entries.size() > capacity || bytes > budget)) {
            Entry removed = entries.removeFirst(); bytes -= removed.bytes(); evictedThrough = removed.id;
        }
    }
    public synchronized JsonObject read(JsonObject p) {
        String stream = p.has("stream") ? string(p, "stream") : "received";
        if (!Arrays.asList("received", "displayed", "all").contains(stream)) throw new IllegalArgumentException("stream must be received/displayed/all");
        String type = p.has("type") ? string(p, "type").toUpperCase(Locale.ROOT) : "";
        if (!type.isEmpty() && !Arrays.asList("CHAT", "SYSTEM", "GAME_INFO", "DISPLAYED").contains(type))
            throw new IllegalArgumentException("type must be CHAT/SYSTEM/GAME_INFO/DISPLAYED");
        String query = p.has("query") ? string(p, "query").toLowerCase(Locale.ROOT) : "";
        int limit = integer(p, "limit", 50, 1, 200);
        boolean includeFormatted = bool(p, "includeFormatted", false);
        boolean includeComponent = bool(p, "includeComponent", false);
        long after = 0;
        if (p.has("afterId")) {
            try { after = p.get("afterId").getAsBigDecimal().longValueExact(); }
            catch (Exception e) { throw new IllegalArgumentException("afterId must be a nonnegative integer"); }
            if (after < 0) throw new IllegalArgumentException("afterId must be nonnegative");
        }
        boolean changed = p.has("connectionId") && !connectionId.equals(string(p, "connectionId"));
        boolean incremental = p.has("afterId") || changed;
        if (changed) after = 0;
        boolean expired = p.has("afterId") && after < evictedThrough;
        boolean ahead = p.has("afterId") && after > nextId;
        List<Entry> matching = new ArrayList<>();
        for (Entry e : entries)
            if ((!incremental || e.id > after) && ("all".equals(stream) || stream.equals(e.stream))
                    && (type.isEmpty() || type.equals(e.type)) && e.plainText.toLowerCase(Locale.ROOT).contains(query)) matching.add(e);
        int start = incremental ? 0 : Math.max(0, matching.size() - limit);
        int end = Math.min(matching.size(), start + limit);
        boolean more = end < matching.size();
        JsonArray messages = new JsonArray();
        for (int i = start; i < end; i++) {
            Entry e = matching.get(i);
            JsonObject message = object("id", e.id, "timestamp", e.timestamp, "stream", e.stream, "type", e.type,
                    "text", e.plainText, "chatLineId", e.chatLineId);
            if (includeFormatted) message.addProperty("formattedText", e.formattedText);
            if (includeComponent) message.add("component", new JsonParser().parse(e.component));
            messages.add(message);
        }
        long cursor = more ? matching.get(end - 1).id : nextId;
        return object("connectionId", connectionId, "server", server, "connected", connected,
                "messages", messages, "nextAfterId", cursor, "latestId", nextId, "hasMore", more,
                "connectionChanged", changed, "cursorExpired", expired, "cursorAhead", ahead,
                "oldestRetainedId", entries.isEmpty() ? null : entries.peekFirst().id,
                "retainedCount", entries.size(), "capacity", capacity);
    }
}
