package com.zszl.zszlScriptMod.mcp;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;
public class McpProtocolTest {
    private final McpProtocol protocol = new McpProtocol(new McpProtocol.Backend() {
        public JsonArray tools() { JsonArray a = new JsonArray(); a.add(object("name","echo","inputSchema",object("type","object"))); return a; }
        public JsonObject call(String name,JsonObject p) {
            if (!"echo".equals(name)) throw new IllegalArgumentException("Unknown tool"); return p;
        }
    });
    @Test public void initializeNegotiatesAndDeclaresTools() {
        JsonObject r=protocol.receive(object("jsonrpc","2.0","id",7,"method","initialize","params",object("protocolVersion","2025-03-26")).toString());
        assertEquals(7,r.get("id").getAsInt());
        assertEquals("2025-03-26",r.getAsJsonObject("result").get("protocolVersion").getAsString());
        assertTrue(r.getAsJsonObject("result").getAsJsonObject("capabilities").has("tools"));
    }
    @Test public void toolsCallHasMcpContentAndStructuredResult() {
        JsonObject r=protocol.receive(object("jsonrpc","2.0","id","a","method","tools/call","params",object("name","echo","arguments",object("x",3))).toString()).getAsJsonObject("result");
        assertFalse(r.get("isError").getAsBoolean());
        assertEquals(3,r.getAsJsonObject("structuredContent").get("x").getAsInt());
        assertEquals("text",r.getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString());
    }
    @Test public void executionErrorIsToolError() {
        JsonObject r=protocol.receive("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"missing\"}}");
        assertTrue(r.getAsJsonObject("result").get("isError").getAsBoolean());
    }
    @Test public void notificationsDoNotReturnOrExecute() {
        assertNull(protocol.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        assertNull(protocol.receive("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"missing\"}}"));
    }
    @Test public void malformedEnvelopesAreProtocolErrors() {
        for(String body:new String[]{"[]","null","{\"jsonrpc\":{},\"method\":\"ping\"}","{\"jsonrpc\":null,\"method\":\"ping\"}","{\"jsonrpc\":\"2.0\",\"method\":true}"})
            assertEquals(body,-32600,protocol.receive(body).getAsJsonObject("error").get("code").getAsInt());
        assertEquals(-32700,protocol.receive("{").getAsJsonObject("error").get("code").getAsInt());
    }
    @Test public void unknownMethodPreservesId() {
        JsonObject r=protocol.receive("{\"jsonrpc\":\"2.0\",\"id\":\"id-9\",\"method\":\"invalid\"}");
        assertEquals("id-9",r.get("id").getAsString());
        assertEquals(-32601,r.getAsJsonObject("error").get("code").getAsInt());
    }
}
