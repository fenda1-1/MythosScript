package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Stateless MCP transport-independent dispatcher. Notifications return null. */
public final class McpProtocol {
    public interface Backend {
        JsonArray tools();
        JsonObject call(String name, JsonObject arguments) throws Exception;
    }
    private final Backend backend;
    public McpProtocol(Backend backend) { this.backend = backend; }

    public JsonObject receive(String body) {
        JsonObject request;
        try {
            JsonElement parsed = new JsonParser().parse(body);
            if (!parsed.isJsonObject()) return error(JsonNull.INSTANCE, -32600, "Expected JSON-RPC object");
            request = parsed.getAsJsonObject();
        } catch (RuntimeException e) { return error(JsonNull.INSTANCE, -32700, "Parse error"); }
        JsonElement id = request.has("id") ? request.get("id") : JsonNull.INSTANCE;
        if (!request.has("jsonrpc") || !request.get("jsonrpc").isJsonPrimitive()
                || !request.getAsJsonPrimitive("jsonrpc").isString()
                || !"2.0".equals(request.get("jsonrpc").getAsString())
                || !request.has("method") || !request.get("method").isJsonPrimitive()
                || !request.getAsJsonPrimitive("method").isString()
                || (id.isJsonPrimitive() && id.getAsJsonPrimitive().isBoolean()) || id.isJsonObject() || id.isJsonArray())
            return error(id, -32600, "Invalid Request");
        String method = request.get("method").getAsString();
        if (!request.has("id")) return null;
        try {
            JsonObject p = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
            JsonObject result;
            switch (method) {
                case "initialize":
                    String requested = p.has("protocolVersion") ? string(p, "protocolVersion") : "";
                    String version = ("2025-06-18".equals(requested) || "2025-03-26".equals(requested)
                            || "2024-11-05".equals(requested)) ? requested : "2025-03-26";
                    result = object("protocolVersion", version, "capabilities", object("tools", object("listChanged", false)),
                            "serverInfo", object("name", "MythosScript", "version", "1.0.0"),
                            "instructions", "Call mythos_clients first and read players (in-world usernames). If the user named a player, pass player on later tools. If players has exactly one name, use it and do not ask. If several names and the user did not specify, list them and ask; do not guess. pid still selects a process; player=all or pid=-1 broadcasts. Then mythos_discover. For a saved or reusable sequence, call mythos_preflight before mythos_run. Inspect action schemas before execution. Query mythos_templates for wait-then-click and teleport-retry recipes; never click GUI after a blind delay, and confirm teleports by area within ~3s with cooldown/failure retries. Game operations run on the client thread. mythos_run returns an executionSessionId and event baseline; call mythos_wait or inspect mythos_status/mythos_logs, then correlate interaction events with GUI/inventory snapshots. Accepted execution is not completion.");
                    break;
                case "ping": result = new JsonObject(); break;
                case "tools/list": result = object("tools", backend.tools()); break;
                case "tools/call":
                    String name = string(p, "name");
                    JsonObject args = p.has("arguments") ? p.getAsJsonObject("arguments") : new JsonObject();
                    long callId = McpCallHistory.INSTANCE.begin(id, name, args);
                    try {
                        JsonObject data = backend.call(name, args);
                        JsonArray content = new JsonArray();
                        content.add(object("type", "text", "text", GSON.toJson(data)));
                        result = object("content", content, "structuredContent", data, "isError", false);
                    } catch (Exception e) {
                        JsonArray content = new JsonArray();
                        content.add(object("type", "text", "text", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                        result = object("content", content, "isError", true);
                    }
                    McpCallHistory.INSTANCE.finish(callId, result, result.get("isError").getAsBoolean());
                    break;
                default: return error(id, -32601, "Method not found: " + method);
            }
            return object("jsonrpc", "2.0", "id", id, "result", result);
        } catch (RuntimeException e) { return error(id, -32602, "Invalid params: " + e.getMessage()); }
    }
    private static JsonObject error(JsonElement id, int code, String message) {
        return object("jsonrpc", "2.0", "id", id, "error", object("code", code, "message", message));
    }
}
