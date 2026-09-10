package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public class McpChatHistoryTest {
    private void add(McpChatHistory h, String stream, String text) {
        h.append(stream, "received".equals(stream) ? "SYSTEM" : "DISPLAYED", text, text,
                object("text", text, "clickEvent", object("action", "suggest_command", "value", "/help")).toString(), 0, "example:123");
    }
    @Test public void defaultReceivedStreamDoesNotConfuseLocalSuccessWithServerReplies() {
        McpChatHistory h = new McpChatHistory(10, 100000);
        h.connect(new Object(), "example:123");
        add(h, "displayed", "local action succeeded");
        add(h, "received", "registered");
        add(h, "displayed", "registered");
        JsonObject result = h.read(new JsonObject());
        assertEquals(1, result.getAsJsonArray("messages").size());
        assertEquals("registered", result.getAsJsonArray("messages").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals(2, h.read(object("stream", "displayed")).getAsJsonArray("messages").size());
    }
    @Test public void cursorPagesWithoutDuplicatesAndAdvancesPastNonMatchingMessages() {
        McpChatHistory h = new McpChatHistory(20, 100000);
        for (int i = 0; i < 5; i++) add(h, "received", "reply " + i);
        JsonObject first = h.read(object("afterId", 0, "limit", 2));
        assertTrue(first.get("hasMore").getAsBoolean());
        assertEquals(2L, first.get("nextAfterId").getAsLong());
        JsonObject second = h.read(object("afterId", 2, "limit", 2));
        assertEquals(3L, second.getAsJsonArray("messages").get(0).getAsJsonObject().get("id").getAsLong());
        JsonObject last = h.read(object("afterId", 4, "query", "REPLY 4"));
        assertEquals(1, last.getAsJsonArray("messages").size());
        assertFalse(last.get("hasMore").getAsBoolean());
        assertEquals(0, h.read(object("afterId", 5)).getAsJsonArray("messages").size());
        assertEquals(5L, h.read(object("afterId", 0, "query", "absent")).get("nextAfterId").getAsLong());
    }
    @Test public void latestWindowAndOptionalRichComponentPreserveContents() {
        McpChatHistory h = new McpChatHistory(20, 100000);
        add(h, "received", "old");
        add(h, "received", "multi\nline");
        JsonObject msg = h.read(object("limit", 1, "includeFormatted", true, "includeComponent", true))
                .getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals("multi\nline", msg.get("text").getAsString());
        assertEquals("/help", msg.getAsJsonObject("component").getAsJsonObject("clickEvent").get("value").getAsString());
        assertFalse(h.read(object("limit", 1)).getAsJsonArray("messages").get(0).getAsJsonObject().has("component"));
    }
    @Test public void newConnectionClearsOldMessagesAndIgnoresStaleDisconnect() {
        McpChatHistory h = new McpChatHistory(20, 100000);
        Object old = new Object(), next = new Object();
        h.connect(old, "old"); add(h, "received", "old login success");
        JsonObject cursor = h.read(new JsonObject());
        h.connect(next, "new"); h.disconnect(old); add(h, "received", "new login needed");
        JsonObject result = h.read(object("connectionId", cursor.get("connectionId"), "afterId", cursor.get("nextAfterId")));
        assertTrue(result.get("connectionChanged").getAsBoolean());
        assertTrue(result.get("connected").getAsBoolean());
        assertEquals(1, result.getAsJsonArray("messages").size());
        h.disconnect(next);
        assertFalse(h.read(new JsonObject()).get("connected").getAsBoolean());
    }
    @Test public void evictionIsReportedAndRepeatedMessagesAreNotMerged() {
        McpChatHistory h = new McpChatHistory(2, 100000);
        add(h, "received", "same"); add(h, "received", "same"); add(h, "received", "same");
        JsonObject r = h.read(object("afterId", 0));
        assertTrue(r.get("cursorExpired").getAsBoolean());
        assertEquals(2, r.getAsJsonArray("messages").size());
        assertEquals(2L, r.get("oldestRetainedId").getAsLong());
    }
    @Test public void invalidCursorsAndFiltersAreRejected() {
        McpChatHistory h = new McpChatHistory(10, 10000);
        for (JsonObject p : new JsonObject[]{object("afterId", -1), object("afterId", 1.2),
                object("limit", 0), object("stream", "sent"), object("type", "invalid")}) {
            try { h.read(p); fail(); } catch (IllegalArgumentException expected) {}
        }
    }
}
