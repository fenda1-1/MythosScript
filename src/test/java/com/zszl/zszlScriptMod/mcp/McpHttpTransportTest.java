package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public class McpHttpTransportTest {
    @Test public void authenticatedHttpRoundTripAndRejections() throws Exception {
        McpProtocol protocol = new McpProtocol(new McpProtocol.Backend() {
            public JsonArray tools() { return new JsonArray(); }
            public JsonObject call(String n, JsonObject p) { return p; }
        });
        String token = "test-token-never-used-in-a-real-game";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", ex -> MythosScriptMcpServer.handle(ex, token, protocol));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
            assertEquals(401, request(url, null, null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"));
            assertEquals(403, request(url, token, "https://example.org", "{}"));
            assertEquals(202, request(url, token, null, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
            assertEquals(200, request(url, token, null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"));
        } finally { server.stop(0); }
    }
    private static int request(String url, String token, String origin, String body) throws Exception {
        URL target = new URL(url);
        // HttpURLConnection silently removes Origin by default; send the actual wire headers.
        try (Socket socket = new Socket(target.getHost(), target.getPort())) {
            socket.setSoTimeout(5000);
            String headers = "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\nContent-Type: application/json\r\n"
                    + (token == null ? "" : "Authorization: Bearer " + token + "\r\n")
                    + (origin == null ? "" : "Origin: " + origin + "\r\n")
                    + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n";
            socket.getOutputStream().write((headers + body).getBytes(StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            int status = Integer.parseInt(in.readLine().split(" ")[1]);
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) { }
            if (status == 200) {
                JsonObject response = new JsonParser().parse(in).getAsJsonObject();
                assertEquals("2.0", response.get("jsonrpc").getAsString());
                assertTrue(response.has("result"));
            }
            return status;
        }
    }
}
