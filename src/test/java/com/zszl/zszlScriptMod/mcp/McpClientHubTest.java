package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public class McpClientHubTest {
    private static McpProtocol.Backend echo(final int owner) {
        return new McpProtocol.Backend() {
            @Override public JsonArray tools() { return new JsonArray(); }
            @Override public JsonObject call(String name, JsonObject arguments) {
                JsonObject result = copy(arguments);
                result.addProperty("owner", owner);
                result.addProperty("tool", name);
                return result;
            }
        };
    }

    @Test public void omittedPidUsesTheOnlyClient() throws Exception {
        McpClientHub hub = new McpClientHub(11, echo(11));
        JsonObject result = hub.dispatch("echo", object("x", 1));
        assertEquals(11, result.get("owner").getAsInt());
        assertEquals(1, result.get("x").getAsInt());
        assertFalse(result.has("pid"));
    }

    @Test public void omittedTargetRejectedWhenMultipleUsernames() throws Exception {
        McpClientHub hub = new McpClientHub(11, echo(11));
        hub.refreshLocal(object("player", "Alice", "inWorld", true));
        hub.worker(object("op", "register", "pid", 22, "info", object("player", "Bob", "inWorld", true)));
        try {
            hub.dispatch("echo", new JsonObject());
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("player required"));
            assertTrue(e.getMessage().contains("Alice"));
            assertTrue(e.getMessage().contains("Bob"));
        }
    }

    @Test public void singleInWorldUsernameDoesNotNeedAChoice() throws Exception {
        McpClientHub hub = new McpClientHub(11, echo(11));
        hub.refreshLocal(object("player", "Alice", "inWorld", true));
        hub.worker(object("op", "register", "pid", 22, "info", object("player", "Bob", "inWorld", false)));
        JsonObject result = hub.dispatch("echo", object("x", 1));
        assertEquals(11, result.get("owner").getAsInt());
    }

    @Test public void playerNameSelectsTheMatchingClient() throws Exception {
        final McpClientHub hub = new McpClientHub(11, echo(11));
        hub.refreshLocal(object("player", "Alice", "inWorld", true));
        hub.worker(object("op", "register", "pid", 22, "info", object("player", "Bob", "inWorld", true)));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> poller = pool.submit(new Callable<Void>() {
                @Override public Void call() throws Exception {
                    JsonObject poll = hub.worker(object("op", "poll", "pid", 22, "waitMs", 5000));
                    JsonObject job = poll.getAsJsonObject("job");
                    hub.worker(object("op", "result", "pid", 22, "jobId", string(job, "id"),
                            "ok", true, "data", object("owner", 22, "tool", string(job, "name"))));
                    return null;
                }
            });
            JsonObject result = hub.dispatch("echo", object("player", "Bob"));
            assertEquals(22, result.get("owner").getAsInt());
            poller.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    @Test public void routesAndBroadcastsByPid() throws Exception {
        final McpClientHub hub = new McpClientHub(11, echo(11));
        hub.worker(object("op", "register", "pid", 22));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> poller = pool.submit(new Callable<Void>() {
                @Override public Void call() throws Exception {
                    for (int i = 0; i < 2; i++) {
                        JsonObject poll = hub.worker(object("op", "poll", "pid", 22, "waitMs", 5000));
                        assertTrue(poll.get("job").isJsonObject());
                        JsonObject job = poll.getAsJsonObject("job");
                        hub.worker(object("op", "result", "pid", 22, "jobId", string(job, "id"),
                                "ok", true, "data", object("owner", 22, "tool", string(job, "name"))));
                    }
                    return null;
                }
            });
            JsonObject one = hub.dispatch("echo", object("pid", 22, "x", 3));
            assertEquals(22, one.get("owner").getAsInt());
            JsonObject all = hub.dispatch("echo", object("pid", -1));
            assertEquals(-1, all.get("pid").getAsInt());
            assertEquals(2, all.getAsJsonArray("results").size());
            poller.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    @Test public void mythosClientsListsHubAndWorker() throws Exception {
        McpClientHub hub = new McpClientHub(11, echo(11));
        hub.refreshLocal(object("player", "hub-player", "inWorld", true));
        hub.worker(object("op", "register", "pid", 22, "info", object("player", "worker-player", "inWorld", false)));
        JsonObject list = hub.dispatch("mythos_clients", new JsonObject());
        assertEquals(11, list.get("localPid").getAsInt());
        assertEquals(2, list.getAsJsonArray("clients").size());
        assertEquals(1, list.getAsJsonArray("players").size());
        assertEquals("hub-player", list.getAsJsonArray("players").get(0).getAsString());
        JsonObject filtered = hub.dispatch("mythos_clients", object("player", "worker"));
        assertEquals(1, filtered.getAsJsonArray("clients").size());
        assertEquals(22, filtered.getAsJsonArray("clients").get(0).getAsJsonObject().get("pid").getAsInt());
    }

    @Test public void secondListenerJoinsExistingPortByPid() throws Exception {
        McpProtocol.Backend local = echo(1);
        McpClientHub hub = new McpClientHub(1, local);
        McpProtocol protocol = new McpProtocol(hub.routingBackend());
        String token = "test-token-never-used-in-a-real-game";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService httpPool = Executors.newCachedThreadPool();
        server.setExecutor(httpPool);
        server.createContext("/mcp", ex -> MythosScriptMcpServer.handle(ex, token, protocol, hub));
        server.start();
        int port = server.getAddress().getPort();
        try {
            JsonObject joined = post(port, token, "/mcp/worker",
                    object("op", "register", "pid", 99, "info", object("player", "second")));
            assertTrue(joined.get("ok").getAsBoolean());
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                pool.submit(new Callable<Void>() {
                    @Override public Void call() throws Exception {
                        JsonObject poll = post(port, token, "/mcp/worker", object("op", "poll", "pid", 99, "waitMs", 5000));
                        JsonObject job = poll.getAsJsonObject("job");
                        post(port, token, "/mcp/worker", object("op", "result", "pid", 99, "jobId",
                                string(job, "id"), "ok", true, "data", object("owner", 99)));
                        return null;
                    }
                });
                JsonObject rpc = object("jsonrpc", "2.0", "id", 1, "method", "tools/call",
                        "params", object("name", "echo", "arguments", object("pid", 99)));
                JsonObject response = post(port, token, "/mcp", rpc);
                assertEquals(99, response.getAsJsonObject("result").getAsJsonObject("structuredContent").get("owner").getAsInt());
            } finally { pool.shutdownNow(); }
        } finally {
            server.stop(0);
            httpPool.shutdownNow();
        }
    }

    private static JsonObject post(int port, String token, String path, JsonObject body) throws Exception {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        try {
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.getOutputStream().write(bytes);
            assertEquals(200, connection.getResponseCode());
            return new JsonParser().parse(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
        } finally { connection.disconnect(); }
    }
}
