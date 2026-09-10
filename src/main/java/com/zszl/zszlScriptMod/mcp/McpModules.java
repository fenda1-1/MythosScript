package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public final class McpModules {
    private static final JsonArray MODULES = read("/mcp/modules.json").getAsJsonArray();
    private static final JsonObject ACTIONS = read("/mcp/actions.json").getAsJsonObject();
    private McpModules() {}
    private static JsonElement read(String path) {
        try (InputStream in = McpModules.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing packaged MCP catalogue: " + path);
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static JsonArray list() { return MODULES; }
    public static JsonElement actionReference(String type, boolean includeSource) {
        JsonArray result = new JsonArray();
        JsonArray source = ACTIONS.getAsJsonArray(type);
        if (source != null) for (JsonElement value : source) {
            JsonObject copy = new JsonParser().parse(value.toString()).getAsJsonObject();
            if (!includeSource) copy.remove("implementation");
            result.add(copy);
        }
        return result;
    }
    public static JsonObject call(JsonObject p) throws Exception {
        String op = string(p, "operation");
        if ("list".equals(op)) return object("modules", MODULES);
        String id = string(p, "module");
        JsonObject module = null;
        for (JsonElement entry : MODULES) if (id.equals(entry.getAsJsonObject().get("id").getAsString())) module = entry.getAsJsonObject();
        if (module == null) throw new IllegalArgumentException("Unknown module: " + id);
        Class<?> type = Class.forName(module.get("class").getAsString());
        if ("describe".equals(op)) return object("module", module, "fields", McpFields.describe(type));
        if ("reload".equals(op)) {
            if (module.getAsJsonArray("reload").size() == 0) throw new IllegalArgumentException("Module has no reload method");
            invoke(type, module.getAsJsonArray("reload"));
        } else if ("set".equals(op)) {
            // Generic writes are explicit persistent edits. Runtime leases need a dedicated feature adapter.
            if (!bool(p, "persist", false)) throw new IllegalArgumentException("set requires persist=true; use mythos_kill_aura for temporary parameters");
            if (module.getAsJsonArray("save").size() == 0) throw new IllegalArgumentException("Module has no save method");
            Map<java.lang.reflect.Field, Object> values = McpFields.decode(type, p.getAsJsonObject("fields"));
            if (type == KillAuraHandler.class) KillAuraHandler.endMcpRuntime();
            JsonObject before = McpFields.snapshot(type);
            try { McpFields.apply(values); invoke(type, module.getAsJsonArray("save")); }
            catch (Exception e) { McpFields.apply(McpFields.decode(type, before)); throw e; }
            if (module.getAsJsonArray("reload").size() > 0) invoke(type, module.getAsJsonArray("reload"));
        } else throw new IllegalArgumentException("Expected list/describe/reload/set");
        return object("module", id, "operation", op, "fields", McpFields.snapshot(type));
    }
    private static void invoke(Class<?> type, JsonArray methods) throws Exception {
        for (JsonElement method : methods) type.getMethod(method.getAsString()).invoke(null);
    }
}
