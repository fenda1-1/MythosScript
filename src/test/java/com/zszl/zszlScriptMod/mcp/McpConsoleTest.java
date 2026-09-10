package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Path;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public class McpConsoleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void recordsPendingArgumentsAndCompleteResponseImmutably() {
        McpCallHistory history = new McpCallHistory(100, 100000);
        JsonObject args = object("text", "完整参数", "nested", object("x", 3));
        long id = history.begin(new JsonPrimitive("request-1"), "mythos_run", args);
        args.addProperty("text", "changed later");
        McpCallHistory.Entry pending = history.snapshot().get(0);
        assertTrue(pending.pending());
        assertTrue(pending.arguments.contains("完整参数"));
        JsonObject response = object("isError", false, "data", "完整返回");
        history.finish(id, response, false);
        McpCallHistory.Entry done = history.snapshot().get(0);
        assertFalse(done.pending()); assertFalse(done.failed);
        assertTrue(done.response.contains("完整返回"));
        assertTrue(pending.pending()); // UI snapshot remains immutable.
    }
    @Test public void evictionKeepsLatestFullBodyAndClearDoesNotResurrectPendingCalls() {
        McpCallHistory history = new McpCallHistory(2, 20);
        history.begin(JsonNull.INSTANCE, "old", object("old", 1));
        long id = history.begin(new JsonPrimitive(2), "new", new JsonObject());
        String text = new String(new char[1000]).replace('\0', 'x');
        history.finish(id, object("text", text), true);
        assertEquals(1, history.snapshot().size());
        assertTrue(history.snapshot().get(0).response.contains(text));
        assertEquals(1, history.failures());
        long pending = history.begin(new JsonPrimitive(3), "pending", new JsonObject());
        history.clear();
        history.finish(pending, object("ok", true), false);
        assertTrue(history.snapshot().isEmpty());
    }
    @Test public void persistentSettingsOverrideBootstrapDefaults() throws Exception {
        Path file = temporary.newFolder().toPath().resolve("mcp_server.json");
        McpServerSettings defaults = new McpServerSettings(true, 8765);
        assertTrue(McpServerSettings.read(file, defaults).enabled);
        new McpServerSettings(false, 18999).write(file);
        McpServerSettings read = McpServerSettings.read(file, defaults);
        assertFalse(read.enabled); assertEquals(18999, read.port);
        try { new McpServerSettings(true, 65536).write(file); fail(); } catch (IllegalArgumentException expected) {}
        assertEquals(18999, McpServerSettings.read(file, defaults).port);
    }
    @Test public void protocolRecordsToolErrorsAndExactParameters() {
        McpCallHistory.INSTANCE.clear();
        McpProtocol protocol = new McpProtocol(new McpProtocol.Backend() {
            public JsonArray tools() { return new JsonArray(); }
            public JsonObject call(String n, JsonObject a) { throw new IllegalArgumentException("test failure"); }
        });
        JsonObject request = object("jsonrpc", "2.0", "id", 9, "method", "tools/call",
                "params", object("name", "test_tool", "arguments", object("count", 5)));
        JsonObject result = protocol.receive(request.toString()).getAsJsonObject("result");
        McpCallHistory.Entry entry = McpCallHistory.INSTANCE.snapshot().get(0);
        assertEquals("test_tool", entry.tool); assertEquals("9", entry.requestId);
        assertEquals(object("count", 5), new JsonParser().parse(entry.arguments));
        assertTrue(entry.failed); assertEquals(result, new JsonParser().parse(entry.response));
        McpCallHistory.INSTANCE.clear();
    }
}
