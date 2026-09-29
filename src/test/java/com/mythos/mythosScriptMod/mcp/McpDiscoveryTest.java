package com.mythos.mythosScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

public class McpDiscoveryTest {
    @Test public void selectsCurrentHubAfterOldHubCrashesAndWorkerTakesOver() {
        JsonArray entries = new JsonArray();
        entries.add(object("pid", 10, "role", "HUB", "port", 8765, "updatedAt", 100));
        entries.add(object("pid", 20, "role", "hub", "port", 8765, "updatedAt", 300));
        entries.add(object("pid", 30, "role", "WORKER", "port", 8765, "updatedAt", 500));
        entries.add(object("pid", 40, "role", "HUB", "port", 8766, "updatedAt", 600));
        entries.add(object("pid", 50, "role", "HUB", "port", 8765, "updatedAt", 200));
        entries.add("invalid");
        List<JsonObject> hubs = MythosScriptMcpServer.newestHubs(entries, 8765);
        assertEquals(3, hubs.size());
        assertEquals(20, hubs.get(0).get("pid").getAsInt());
        assertEquals(50, hubs.get(1).get("pid").getAsInt());
        assertEquals(10, hubs.get(2).get("pid").getAsInt());
    }
}
