package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.modern.path.ModernActionEditorSchema;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.ActionDisplayCatalog;
import com.zszl.zszlScriptMod.gui.path.template.ActionTemplateCatalog;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.*;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.validation.PathConfigValidator;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public final class McpGameService implements McpProtocol.Backend {
    private final McpConfigStore configs = new McpConfigStore(Paths.get(ModConfig.CONFIG_DIR));
    private volatile long generation;
    private volatile boolean accepting = true;
    private final Semaphore eventWaiters = new Semaphore(2);
    private long lastObservationError;
    public void setAccepting(boolean accepting) { this.accepting = accepting; generation++; McpObservation.INSTANCE.setActive(accepting); }
    public JsonObject clientInfo() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean inWorld = mc != null && mc.player != null && mc.world != null;
        String player = mc != null && mc.player != null ? mc.player.getName() : "";
        String server = "offline";
        if (mc != null && mc.getCurrentServerData() != null) {
            server = mc.getCurrentServerData().serverIP;
        } else if (inWorld) {
            server = "singleplayer";
        }
        return object("pid", McpClientHub.currentPid(), "player", player, "server", server, "inWorld", inWorld);
    }
    public McpGameService() { MinecraftForge.EVENT_BUS.register(this); McpObservation.INSTANCE.register(); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PathSequenceManager.cleanupTransientSequences();
            try { McpObservation.INSTANCE.tick(); }
            catch (RuntimeException e) {
                if (System.currentTimeMillis()-lastObservationError>5000) {
                    lastObservationError=System.currentTimeMillis();
                    McpEventJournal.INSTANCE.append("session","capture_error",0,object("error",e.getClass().getSimpleName(),"message",e.getMessage()));
                }
            }
        }
    }

    @Override public JsonArray tools() {
        JsonArray tools = new JsonArray();
        add(tools, "mythos_clients", "List in-world usernames in players and each client in clients. Optional player filters by username substring. Other tools take player or pid; omit both when only one in-world username exists; player=all or pid=-1 broadcasts. If several usernames and the user did not name one, list players and ask; do not guess.", "player:string", "");
        add(tools, "mythos_discover", "Discover configuration root, active profile, modules and supported operations. Includes localPid and the hub client list.", "", "");
        add(tools, "mythos_status", "Read current server, player position, nearby entities, active runs with execution session IDs, variables and temporary aura state.", "", "");
        add(tools, "mythos_preflight", "Validate a saved or inline sequence and check that the current client is in a compatible world/server before execution.", "name:string,sequence:object", "");
        add(tools, "mythos_events", "Grouped event timeline. operation=groups discovers IDs and limits; configure selects captured groups/sampleTicks/size; watch registers a filter and returns watchId; unwatch/list_watches manage it. read selects any groups/types/entityIds/watchIds plus inclusive fromMs/toMs and absolute min/max bounds. afterId=nextAfterId with sessionId resumes without duplicates. waitMs<=25000 long polls off the game thread. Watch tags apply only to future events. Session changes reset watches. Attack intents, death evidence and killer attribution are distinct.", "operation:string,groups:array,types:array,entityIds:array,watchIds:array,fromMs:integer,toMs:integer,afterId:integer,sessionId:string,limit:integer,waitMs:integer,min:array,max:array,query:string,sampleTicks:integer,size:array,watchId:string,filter:object", "");
        add(tools, "mythos_snapshot", "Read current server, player/inventory/entities/gui/world snapshots. groups selects sections. Default 10x10x10 block volume centered at floor(player); size axes 1..32. origin optionally specifies absolute block center. GUI and inventory snapshots include fingerprints for change detection; container click events provide interaction IDs and best-effort correlation. No block mutation. includeNbt expands GUI slot items.", "groups:array,size:array,origin:array,entityIds:array,includeNbt:boolean", "");
        add(tools, "mythos_gui", "Inspect and control Minecraft GUI screens. Use inspect for semantic element paths, open for the modern control center or a settings route, select_tab, click, input, set, key, scroll, save and close. inspect returns controlType, value, enabled, editable, actions and choices. Custom screens publish stable paths so callers do not need to calculate pixels.", "operation:string,target:string,path:string,command:string,text:string,value:any,append:boolean,button:string,matchMode:string,x:integer,y:integer,key:string,keyCode:integer,character:string,state:string,pressDurationTicks:integer,wheel:integer,scope:string", "operation");
        add(tools, "mythos_chat", "Read chat directly from memory, without log files. Default: latest 50 received server messages, oldest first. stream=received/displayed/all; type=CHAT/SYSTEM/GAME_INFO/DISPLAYED; query is case-insensitive substring. Pass afterId=nextAfterId and connectionId to read only new replies. Check connected, connectionChanged and cursorExpired. Optional formatted text and full text component JSON (hover/click data). Received stream precedes client filtering; displayed stream includes local mod messages and may duplicate received messages.", "afterId:integer,connectionId:string,stream:string,type:string,query:string,limit:integer,includeFormatted:boolean,includeComponent:boolean", "");
        add(tools, "mythos_notes", "Read or write the Tools-menu Markdown notebook. One file per server under notes/; singleplayer when offline or in an integrated world. Default operation is read of the current server. write replaces text; append adds to the end; list enumerates notebooks. server selects another notebook. expectedHash from the previous read prevents lost updates. Open GUI drafts are visible to read and updated by write/append. Not the path/step note field.", "operation:string,server:string,text:string,expectedHash:string", "");
        add(tools, "mythos_actions", "List all actual action-library IDs or describe one action's fields, choices, defaults and source parameter reference. query filters list; includeSource=true expands implementation excerpts.", "type:string,query:string,includeSource:boolean", "");
        add(tools, "mythos_templates", "List or get reusable action-template recipes. These are starting patterns, not copy-paste-perfect scripts: observe real GUI titles, slot text, chat/cooldown wording and coordinates with mythos_snapshot/mythos_chat first. Never click after a blind delay; wait_until_gui_title, settle 5-10 ticks, then click. After teleport, wait_until_player_in_area for ~60 ticks and retry the whole open-click flow if not arrived; detect cooldown/failure chat and retry. id returns one template with actions; otherwise list. query/category filter the list. includeActions=true adds the action JSON.", "id:string,query:string,category:string,includeActions:boolean", "");
        add(tools, "mythos_config", "Read/write/delete any mod configuration file. patch supports JSON Pointer add/replace/remove/test. Persistence only; reload owning module afterward. expectedHash prevents lost updates.", "operation:string,path:string,value:any,text:string,patch:array,expectedHash:string", "operation");
        add(tools, "mythos_modules", "List reloadable config modules, inspect writable runtime fields, reload from disk, or set fields with explicit persist=true. Temporary aura uses mythos_kill_aura.", "operation:string,module:string,fields:object,persist:boolean", "operation");
        add(tools, "mythos_paths", "CRUD complete path sequences, steps and arbitrary action params. Operations: list,get,put,delete,step_put,step_delete,action_put,action_delete. put accepts name,steps and optional targetServer; step/action writes insert when insert=true otherwise replace. Indices are zero based.", "operation:string,name:string,sequence:object,step:object,action:object,stepIndex:integer,actionIndex:integer,insert:boolean", "operation");
        add(tools, "mythos_run", "Run saved name or inline sequence or actions. Returns an executionSessionId and event baseline for deterministic follow-up. Inline execution never adds a saved path. loops=1 default, -1 infinite. startStep/startAction zero based; variables object. Actions themselves can change game state or save settings; inspect action first. Existing run requires replace=true.", "name:string,sequence:object,actions:array,loops:integer,startStep:integer,startAction:integer,variables:object,replace:boolean", "");
        add(tools, "mythos_control", "Pause, resume or stop foreground/background/all paths; stop_all also restores temporary aura.", "operation:string,scope:string", "operation");
        add(tools, "mythos_kill_aura", "describe/start/stop temporary aura. start accepts complete writable fields without changing saved config; durationTicks=0 until stopped, positive ticks auto-restore. stop restores previous fields. GUI saves are suspended during override.", "operation:string,fields:object,enabled:boolean,durationTicks:integer", "operation");
        add(tools, "mythos_packets", "capture_start/capture_stop/list/clear captured packets. list uses direction C2S or S2C and offset/limit; includes packet ID, channel, raw hex and decoded data. Send packets with mythos_run send_packet action.", "operation:string,direction:string,offset:integer,limit:integer", "operation");
        add(tools, "mythos_packet_trace", "Correlate one or more packet-capture input timeline events with nearby captured packets. Filter exact keyboard keys or mouse buttons and time ranges; directions accepts C2S, S2C or BOTH. windowMs selects a time window around each input, before/after adds nearest packets by count, and packetLimit/maxTotalPackets bound the result. Returns timestamps, signed/absolute deltas, direction, occurrence counts, HEX and decoded data.", "inputs:array,directions:array,direction:string,types:array,keys:array,keyCodes:array,buttons:array,eventIds:array,sessionIds:array,actions:array,guiTitles:array,screenNames:array,fromMs:integer,toMs:integer,query:string,inputLimit:integer,windowMs:integer,before:integer,after:integer,packetLimit:integer,maxTotalPackets:integer,includeHex:boolean,includeDecoded:boolean", "");
        add(tools, "mythos_validate", "Validate an inline sequence or all saved paths without executing. Returns field/action locations and errors/warnings.", "sequence:object", "");
        add(tools, "mythos_logs", "Read recent execution session summaries, or a selected session with structured events and full text.", "sessionId:string,limit:integer,includeEvents:boolean", "");
        add(tools, "mythos_wait", "Wait for an execution session to finish without blocking the Minecraft client thread. Returns running, success/failure, reason, status and optional events.", "sessionId:string,waitMs:integer,includeEvents:boolean", "sessionId");
        add(tools, "mythos_navigation", "List built-in navigation commands with full usage, or execute command without the # prefix (goto/mine/build/farm/cancel etc). This invokes the existing navigation command engine.", "command:string", "");
        return tools;
    }
    private static void add(JsonArray all, String name, String description, String fields, String required) {
        JsonObject properties = new JsonObject();
        if (!fields.isEmpty()) for (String field : fields.split(",")) {
            String[] pair = field.split(":");
            JsonObject schema = new JsonObject();
            if (!"any".equals(pair[1])) schema.addProperty("type", pair[1]);
            if ("array".equals(pair[1])) schema.add("items", new JsonObject());
            properties.add(pair[0], schema);
        }
        JsonArray req = new JsonArray();
        if (!required.isEmpty()) for (String r : required.split(",")) req.add(r);
        enrichToolSchema(name, properties);
        JsonObject pidSchema = new JsonObject();
        pidSchema.addProperty("type", "integer");
        properties.add("pid", pidSchema);
        JsonObject playerSchema = new JsonObject();
        playerSchema.addProperty("type", "string");
        properties.add("player", playerSchema);
        boolean read = name.equals("mythos_discover") || name.equals("mythos_status") || name.equals("mythos_actions")
                || name.equals("mythos_validate") || name.equals("mythos_logs") || name.equals("mythos_chat")
                || name.equals("mythos_snapshot") || name.equals("mythos_clients") || name.equals("mythos_templates")
                || name.equals("mythos_preflight") || name.equals("mythos_wait") || name.equals("mythos_packet_trace");
        all.add(object("name", name, "description", description,
                "inputSchema", object("type", "object", "properties", properties, "required", req, "additionalProperties", false),
                "annotations", object("readOnlyHint", read, "destructiveHint", !read && !name.equals("mythos_events"), "openWorldHint", true)));
    }

    /** Adds useful nested schemas while keeping the legacy permissive fields. */
    private static void enrichToolSchema(String name, JsonObject properties) {
        if ("mythos_run".equals(name)) {
            properties.getAsJsonObject("actions").add("items", actionInputSchema());
            properties.getAsJsonObject("sequence").add("properties", sequenceSchemaProperties());
            properties.getAsJsonObject("sequence").addProperty("additionalProperties", false);
        } else if ("mythos_paths".equals(name)) {
            setEnum(properties, "operation", "list", "get", "put", "delete", "step_put", "step_delete",
                    "action_put", "action_delete");
            properties.getAsJsonObject("sequence").add("properties", sequenceSchemaProperties());
            properties.getAsJsonObject("sequence").addProperty("additionalProperties", false);
            properties.getAsJsonObject("action").add("properties", object("type", object("type", "string"),
                    "params", object("type", "object", "additionalProperties", true)));
            properties.getAsJsonObject("action").addProperty("additionalProperties", false);
        } else if ("mythos_events".equals(name)) {
            setEnum(properties, "operation", "groups", "configure", "watch", "unwatch", "list_watches", "read");
        } else if ("mythos_packets".equals(name)) {
            setEnum(properties, "operation", "capture_start", "capture_stop", "list", "clear");
            setEnum(properties, "direction", "C2S", "S2C");
        } else if ("mythos_packet_trace".equals(name)) {
            setArrayEnum(properties, "directions", "C2S", "S2C", "BOTH");
            setEnum(properties, "direction", "C2S", "S2C", "BOTH");
            setArrayEnum(properties, "types", "key", "mouse");
            properties.getAsJsonObject("inputs").add("items", packetTraceInputSchema());
            addMinimum(properties, "fromMs", 0); addMinimum(properties, "toMs", 0);
            addMinimumMaximum(properties, "windowMs", 0, McpPacketTrace.MAX_WINDOW_MS);
            addMinimumMaximum(properties, "before", 0, McpPacketTrace.MAX_PACKETS_PER_INPUT);
            addMinimumMaximum(properties, "after", 0, McpPacketTrace.MAX_PACKETS_PER_INPUT);
            addMinimumMaximum(properties, "packetLimit", 1, McpPacketTrace.MAX_PACKETS_PER_INPUT);
            addMinimumMaximum(properties, "maxTotalPackets", 1, McpPacketTrace.MAX_TOTAL_PACKETS);
            addMinimumMaximum(properties, "inputLimit", 1, McpPacketTrace.MAX_INPUTS);
        } else if ("mythos_gui".equals(name)) {
            setEnum(properties, "operation", "inspect", "open", "select_tab", "click", "input", "set", "key", "scroll", "save", "close");
            setEnum(properties, "button", "LEFT", "RIGHT", "MIDDLE");
            setEnum(properties, "matchMode", "CONTAINS", "EXACT");
            properties.getAsJsonObject("pressDurationTicks").addProperty("minimum", 1);
            properties.getAsJsonObject("pressDurationTicks").addProperty("maximum", 2000);
        } else if ("mythos_wait".equals(name)) {
            properties.getAsJsonObject("waitMs").addProperty("minimum", 0);
            properties.getAsJsonObject("waitMs").addProperty("maximum", 25000);
        }
    }

    private static JsonObject sequenceSchemaProperties() {
        JsonObject position = new JsonObject();
        JsonArray oneOf = new JsonArray();
        oneOf.add(object("type", "null"));
        oneOf.add(object("type", "array", "items", object("type", "number"), "minItems", 3, "maxItems", 3));
        position.add("oneOf", oneOf);
        JsonObject step = object("type", "object",
                "properties", object("pos", position,
                        "actions", object("type", "array", "items", actionInputSchema()),
                        "note", object("type", "string"),
                        "retryCount", object("type", "integer", "minimum", 0),
                        "pathRetryTimeoutSeconds", object("type", "integer", "minimum", 0),
                        "arrivalToleranceBlocks", object("type", "integer", "minimum", 0)),
                "additionalProperties", true);
        JsonObject steps = object("type", "array", "items", step);
        return object("name", object("type", "string"), "category", object("type", "string"),
                "subCategory", object("type", "string"), "targetServer", object("type", "string"),
                "note", object("type", "string"), "loopDelayTicks", object("type", "integer", "minimum", 0),
                "singleExecution", object("type", "boolean"),
                "nonInterruptingExecution", object("type", "boolean"),
                "closeGuiAfterStart", object("type", "boolean"),
                "lockConflictPolicy", object("type", "string"), "steps", steps);
    }

    private static JsonObject actionInputSchema() {
        return object("type", "object",
                "properties", object("type", object("type", "string"),
                        "params", object("type", "object", "additionalProperties", true)),
                "required", Arrays.asList("type"), "additionalProperties", false);
    }

    private static void setEnum(JsonObject properties, String key, String... values) {
        if (!properties.has(key)) return;
        JsonArray choices = new JsonArray();
        for (String value : values) choices.add(value);
        properties.getAsJsonObject(key).add("enum", choices);
    }

    private static void setArrayEnum(JsonObject properties, String key, String... values) {
        if (!properties.has(key)) return;
        JsonObject item = new JsonObject(); item.addProperty("type", "string");
        JsonArray choices = new JsonArray(); for (String value : values) choices.add(value);
        item.add("enum", choices); properties.getAsJsonObject(key).add("items", item);
    }

    private static void addMinimum(JsonObject properties, String key, long minimum) {
        if (properties.has(key)) properties.getAsJsonObject(key).addProperty("minimum", minimum);
    }

    private static void addMinimumMaximum(JsonObject properties, String key, long minimum, long maximum) {
        if (!properties.has(key)) return;
        JsonObject schema = properties.getAsJsonObject(key);
        schema.addProperty("minimum", minimum); schema.addProperty("maximum", maximum);
    }

    private static JsonObject packetTraceInputSchema() {
        JsonObject stringArray = object("type", "array", "items", object("type", "string"));
        JsonObject integerArray = object("type", "array", "items", object("type", "integer"));
        JsonObject buttonItem = object("oneOf", Arrays.asList(object("type", "integer"), object("type", "string")));
        JsonObject buttons = object("type", "array", "items", buttonItem);
        return object("type", "object", "properties", object(
                "eventIds", integerArray, "sessionIds", integerArray,
                "types", object("type", "array", "items", object("type", "string", "enum", Arrays.asList("key", "mouse"))),
                "keys", stringArray, "keyCodes", integerArray, "buttons", buttons,
                "actions", stringArray, "guiTitles", stringArray, "screenNames", stringArray,
                "fromMs", object("type", "integer", "minimum", 0),
                "toMs", object("type", "integer", "minimum", 0), "query", object("type", "string")),
                "additionalProperties", false);
    }

    @Override public JsonObject call(String name, JsonObject arguments) throws Exception {
        final long requestedGeneration = generation;
        if (name.equals("mythos_events") && arguments.has("operation") && string(arguments,"operation").equals("read")) {
            int wait = integer(arguments,"waitMs",0,0,25000);
            if (wait>0 && !arguments.has("afterId")) throw new IllegalArgumentException("waitMs requires afterId; read a baseline first");
            boolean permit = wait==0 || eventWaiters.tryAcquire();
            if (!permit) throw new IllegalStateException("At most two concurrent event waits; reuse one cursor stream");
            try { return McpEventJournal.INSTANCE.await(arguments,wait,()->accepting && generation==requestedGeneration); }
            finally { if(wait>0) eventWaiters.release(); }
        }
        if (name.equals("mythos_wait")) {
            return waitForExecution(arguments);
        }
        // Cancel tasks still queued when the client thread is stalled; no late surprise mutation.
        AtomicInteger phase = new AtomicInteger(0); // 0 queued, 1 executing, 2 cancelled
        FutureTask<JsonObject> task = new FutureTask<>(() -> {
            if (!phase.compareAndSet(0, 1)) throw new CancellationException("Request cancelled before execution");
            if (!accepting || requestedGeneration != generation) throw new CancellationException("MCP service stopped or restarted");
            return invoke(name, arguments);
        });
        Minecraft.getMinecraft().addScheduledTask((Runnable) task);
        try { return task.get(30, TimeUnit.SECONDS); }
        catch (TimeoutException e) {
            if (phase.compareAndSet(0, 2)) { task.cancel(false); throw new IllegalStateException("Client thread unavailable; request cancelled before execution"); }
            throw new IllegalStateException("Operation started but response timed out; inspect state before retrying");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }
    private JsonObject invoke(String name, JsonObject p) throws Exception {
        switch (name) {
            case "mythos_clients":
                return MythosScriptMcpServer.clients();
            case "mythos_discover":
                return object("configRoot", Paths.get(ModConfig.CONFIG_DIR).toAbsolutePath().toString(),
                        "profile", ProfileManager.getActiveProfileName(), "profileDirectory", ProfileManager.getCurrentProfileDir().toString(),
                        "localPid", McpClientHub.currentPid(),
                        "actionCount", ActionDisplayCatalog.getActionDisplayKeys().size(),
                        "templateCount", ActionTemplateCatalog.getTemplates().size(),
                        "observationGroups", McpEventJournal.GROUPS,
                        "modules", McpModules.list(), "temporaryFeatures", Arrays.asList("kill_aura", "inline_sequences"),
                        "guiControl", object("tool", "mythos_gui", "operations",
                                Arrays.asList("inspect", "open", "select_tab", "click", "input", "set", "key", "scroll", "save", "close"),
                                "semanticTargeting", "Use element paths/text returned by mythos_gui inspect or mythos_snapshot gui. inspect returns controlType/value/enabled/editable/actions/choices/visible."),
                        "notesDirectory", Paths.get(ModConfig.CONFIG_DIR, "notes").toAbsolutePath().toString(),
                        "stepFormat", "pos:[x,y,z] or null (no navigation), actions:[{type,params}], note, retryCount, pathRetryTimeoutSeconds, arrivalToleranceBlocks",
                        "configPersistence", "Config files support arbitrary JSON fields including deletion. Reload explicitly; runtime fields and disk files are distinct.");
            case "mythos_status": return status();
            case "mythos_preflight": return preflight(p);
            case "mythos_events": return McpObservation.INSTANCE.control(p);
            case "mythos_snapshot": return McpObservation.INSTANCE.snapshot(p);
            case "mythos_gui": return McpGuiController.call(p);
            case "mythos_chat": return McpChatHistory.INSTANCE.read(p);
            case "mythos_notes": return McpNotes.INSTANCE.call(p);
            case "mythos_actions": return actions(p);
            case "mythos_templates": return templates(p);
            case "mythos_config": return configs.call(p);
            case "mythos_modules": return McpModules.call(p);
            case "mythos_paths": return paths(p);
            case "mythos_run": return run(p);
            case "mythos_control": return control(p);
            case "mythos_kill_aura": return aura(p);
            case "mythos_packets": return packets(p);
            case "mythos_packet_trace": return McpPacketTrace.call(p);
            case "mythos_validate":
                return object("issues", PathConfigValidator.validateSequences(p.has("sequence")
                        ? Arrays.asList(decode(p.getAsJsonObject("sequence"))) : PathSequenceManager.getAllSequences()));
            case "mythos_logs": return logs(p);
            case "mythos_navigation": return navigation(p);
            default: throw new IllegalArgumentException("Unknown tool: " + name);
        }
    }
    private static JsonObject status() {
        Minecraft mc = Minecraft.getMinecraft();
        JsonObject result = object("inWorld", mc.player != null && mc.world != null,
                "localPid", McpClientHub.currentPid(),
                "profile", ProfileManager.getActiveProfileName(),
                "server", currentServerAddress(),
                "navigation", object("active", EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()),
                "runs", PathSequenceEventListener.getActiveProgressSnapshots(),
                "variables", PathSequenceEventListener.instance.getDebugSnapshot().getVariablePreview(),
                "killAuraEnabled", KillAuraHandler.enabled, "temporaryAura", KillAuraHandler.isMcpRuntimeActive(),
                "capture", PacketCaptureHandler.isCapturing);
        if (mc.player != null) {
            result.add("player", object("name", mc.player.getName(), "x", mc.player.posX, "y", mc.player.posY,
                    "z", mc.player.posZ, "yaw", mc.player.rotationYaw, "pitch", mc.player.rotationPitch, "health", mc.player.getHealth()));
            JsonArray entities = new JsonArray();
            if (mc.world != null) for (Entity entity : mc.world.loadedEntityList)
                if (entities.size() < 100 && entity.getDistanceSq(mc.player) <= 64 * 64)
                    {
                        JsonObject e = McpObservation.entity(entity);
                        e.addProperty("id",entity.getEntityId());e.addProperty("type",entity.getClass().getSimpleName());
                        e.addProperty("x",entity.posX);e.addProperty("y",entity.posY);e.addProperty("z",entity.posZ); entities.add(e);
                    }
            result.add("nearbyEntities", entities);
        }
        return result;
    }
    private static JsonObject actions(JsonObject p) {
        Map<String, String> catalog = ActionDisplayCatalog.getActionDisplayKeys();
        if (p.has("type")) {
            String type = string(p, "type");
            if (!catalog.containsKey(type)) throw new IllegalArgumentException("Unknown action: " + type);
            JsonObject result = ModernActionEditorSchema.describeForMcp(type);
            result.addProperty("name", I18n.format(catalog.get(type)));
            result.add("sourceParameters", McpModules.actionReference(type, bool(p, "includeSource", false)));
            result.add("paramsSchema", actionParamsSchema(result));
            result.addProperty("parameterFormat", "params uses the editor's JSON representation. Synthetic editor keys such as conditionsText represent structured JSON; see sourceParameters for runtime keys. Additional params are preserved.");
            return result;
        }
        String query = p.has("query") ? string(p, "query").toLowerCase(Locale.ROOT) : "";
        JsonArray entries = new JsonArray();
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            String label = I18n.format(entry.getValue());
            if ((entry.getKey() + " " + label).toLowerCase(Locale.ROOT).contains(query))
                entries.add(object("type", entry.getKey(), "name", label));
        }
        return object("actions", entries);
    }

    /** Converts the editor catalogue into a permissive JSON Schema for AI clients. */
    private static JsonObject actionParamsSchema(JsonObject description) {
        JsonObject properties = new JsonObject();
        JsonObject defaults = description.has("defaults") && description.get("defaults").isJsonObject()
                ? description.getAsJsonObject("defaults") : new JsonObject();
        if (description.has("fields") && description.get("fields").isJsonArray()) {
            for (JsonElement element : description.getAsJsonArray("fields")) {
                if (!element.isJsonObject()) continue;
                JsonObject field = element.getAsJsonObject();
                String key = field.has("key") ? field.get("key").getAsString().trim() : "";
                if (key.isEmpty() || properties.has(key)) continue;
                JsonObject property = new JsonObject();
                if (field.has("help") && !field.get("help").getAsString().trim().isEmpty())
                    property.addProperty("description", field.get("help").getAsString());
                JsonArray choices = field.has("choices") && field.get("choices").isJsonArray()
                        ? field.getAsJsonArray("choices") : new JsonArray();
                if (choices.size() > 0) {
                    property.add("enum", copyArray(choices));
                } else {
                    String kind = field.has("kind") ? field.get("kind").getAsString() : "TEXT";
                    JsonElement defaultValue = defaults.get(key);
                    addSchemaType(property, kind, defaultValue);
                }
                if (defaults.has(key)) property.add("default", copyElement(defaults.get(key)));
                properties.add(key, property);
            }
        }
        return object("type", "object", "properties", properties, "additionalProperties", true);
    }

    private static void addSchemaType(JsonObject property, String kind, JsonElement defaultValue) {
        if ("TOGGLE".equalsIgnoreCase(kind)) {
            property.addProperty("type", "boolean");
            return;
        }
        if ("TEXT".equalsIgnoreCase(kind) || "KEYBOARD_PICKER".equalsIgnoreCase(kind)
                || "EXPRESSION".equalsIgnoreCase(kind)) {
            property.addProperty("type", "string");
            return;
        }
        if ("SPINNER".equalsIgnoreCase(kind) || "HOTBAR".equalsIgnoreCase(kind)) {
            property.addProperty("type", "number");
            return;
        }
        if (defaultValue != null && defaultValue.isJsonPrimitive()) {
            JsonPrimitive primitive = defaultValue.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                property.addProperty("type", "boolean");
                return;
            }
            if (primitive.isNumber()) {
                property.addProperty("type", "number");
                return;
            }
        }
        if (defaultValue != null && defaultValue.isJsonArray()) {
            property.addProperty("type", "array");
            return;
        }
        // Text fields intentionally accept the editor's legacy string form and
        // numeric/structured forms used by older saved actions.
        JsonArray types = new JsonArray();
        types.add("string"); types.add("number"); types.add("boolean"); types.add("array"); types.add("object");
        property.add("type", types);
    }

    private static JsonArray copyArray(JsonArray source) {
        return new JsonParser().parse(source.toString()).getAsJsonArray();
    }

    private static JsonElement copyElement(JsonElement source) {
        return source == null || source.isJsonNull() ? JsonNull.INSTANCE : new JsonParser().parse(source.toString());
    }
    private static JsonObject templates(JsonObject p) {
        String id = p.has("id") ? string(p, "id").trim() : "";
        boolean includeActions = bool(p, "includeActions", !id.isEmpty());
        if (!id.isEmpty()) {
            ActionTemplateCatalog.ActionTemplate template = ActionTemplateCatalog.getTemplateById(id);
            if (template == null) throw new IllegalArgumentException("Unknown template: " + id);
            JsonObject result = ActionTemplateCatalog.describeForMcp(template, true);
            result.add("principles", authoringPrinciples());
            return result;
        }
        String query = p.has("query") ? string(p, "query").toLowerCase(Locale.ROOT) : "";
        String category = p.has("category") ? string(p, "category").trim() : "";
        JsonArray entries = new JsonArray();
        LinkedHashSet<String> categoryNames = new LinkedHashSet<String>();
        for (ActionTemplateCatalog.ActionTemplate template : ActionTemplateCatalog.getTemplates()) {
            categoryNames.add(template.getCategory());
            if (!category.isEmpty() && !category.equals(template.getCategory())) continue;
            String haystack = (template.getSearchText() + " " + template.getId() + " " + template.getName())
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            entries.add(ActionTemplateCatalog.describeForMcp(template, includeActions));
        }
        JsonArray categories = new JsonArray();
        for (String name : categoryNames) categories.add(name);
        return object("principles", authoringPrinciplesList(), "categories", categories,
                "templates", entries, "count", entries.size());
    }
    private static JsonArray authoringPrinciples() {
        JsonArray principles = new JsonArray();
        for (String line : authoringPrinciplesList()) principles.add(line);
        return principles;
    }
    private static List<String> authoringPrinciplesList() {
        return Arrays.asList(
                "Never click a GUI after a blind delay. Wait for the title, settle 5-10 ticks, then click.",
                "Never assume teleport or a click succeeded. Confirm with wait_until_player_in_area / inventory / packet / HUD, and retry the whole open-click flow.",
                "If chat or packet text says cooldown/busy/failure, wait and retry the same flow. Observe the real text with mythos_chat first.",
                "Templates are recipes. Replace titles, slot text, coordinates and cooldown keywords from live snapshot/chat. Keep the waits and retries.");
    }
    private static void world() {
        if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().world == null)
            throw new IllegalStateException("Join a world before executing game functions");
    }

    private static String currentServerAddress() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getCurrentServerData() != null
                && mc.getCurrentServerData().serverIP != null
                && !mc.getCurrentServerData().serverIP.trim().isEmpty()) {
            return mc.getCurrentServerData().serverIP.trim();
        }
        return mc != null && mc.world != null ? "singleplayer" : "offline";
    }

    private static String normalizeServerAddress(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String serverHost(String value) {
        String normalized = normalizeServerAddress(value);
        if (normalized.startsWith("[")) {
            int end = normalized.indexOf(']');
            return end > 0 ? normalized.substring(1, end) : normalized;
        }
        int first = normalized.indexOf(':');
        int last = normalized.lastIndexOf(':');
        return first >= 0 && first == last ? normalized.substring(0, first) : normalized;
    }

    private static boolean serverMatches(String expected, String actual) {
        String wanted = normalizeServerAddress(expected);
        String current = normalizeServerAddress(actual);
        if (wanted.isEmpty()) return true;
        if (wanted.equals(current)) return true;
        // A host-only target is intentionally accepted for convenience; a
        // target containing a port remains an exact host:port guard.
        return !wanted.contains(":") && serverHost(wanted).equals(serverHost(current));
    }

    private static void ensureTargetServer(PathSequence sequence) {
        String expected = sequence == null ? "" : sequence.getTargetServer();
        if (!expected.isEmpty() && !serverMatches(expected, currentServerAddress())) {
            throw new IllegalStateException("Sequence targetServer '" + expected + "' does not match current server '"
                    + currentServerAddress() + "'");
        }
    }

    private static JsonObject preflight(JsonObject p) {
        int supplied = (p.has("name") ? 1 : 0) + (p.has("sequence") ? 1 : 0);
        if (supplied != 1) throw new IllegalArgumentException("Supply exactly one of name or sequence");
        PathSequence sequence = p.has("name")
                ? requireSequence(string(p, "name")) : decode(p.getAsJsonObject("sequence"));
        List<PathSequence> validationSet = new ArrayList<>(PathSequenceManager.getAllSequences());
        validationSet.removeIf(existing -> existing.getName().equals(sequence.getName()));
        validationSet.add(sequence);
        List<PathConfigValidator.Issue> issues = PathConfigValidator.validateSequences(validationSet);
        JsonArray issueList = new JsonArray();
        boolean syntaxValid = true;
        for (PathConfigValidator.Issue issue : issues) {
            if (!sequence.getName().equals(issue.getSequenceName())) continue;
            if (issue.getSeverity() == PathConfigValidator.Severity.ERROR) syntaxValid = false;
            issueList.add(object("severity", issue.getSeverity().name(), "code", issue.getCode(),
                    "sequence", issue.getSequenceName(), "stepIndex", issue.getStepIndex(),
                    "actionIndex", issue.getActionIndex(), "summary", issue.getSummary(),
                    "detail", issue.getDetail(), "text", issue.toCompactText()));
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean inWorld = mc != null && mc.player != null && mc.world != null;
        String server = currentServerAddress();
        boolean serverMatch = serverMatches(sequence.getTargetServer(), server);
        return object("ok", syntaxValid && inWorld && serverMatch, "syntaxValid", syntaxValid,
                "inWorld", inWorld, "serverMatch", serverMatch, "server", server,
                "targetServer", sequence.getTargetServer(), "sequence", sequence.getName(), "issues", issueList);
    }
    private static PathSequence requireSequence(String name) {
        PathSequence sequence = PathSequenceManager.getSequence(name);
        if (sequence == null) throw new IllegalArgumentException("Sequence not found: " + name);
        return sequence;
    }
    private static JsonObject encode(PathSequence s) {
        JsonArray steps = new JsonArray();
        for (PathStep step : s.getSteps()) {
            steps.add(encodeStep(step));
        }
        return object("name", s.getName(), "category", s.getCategory(), "subCategory", s.getSubCategory(),
                "targetServer", s.getTargetServer(),
                "note", s.getNote(), "loopDelayTicks", s.getLoopDelayTicks(), "steps", steps,
                "singleExecution", s.isSingleExecution(), "nonInterruptingExecution", s.isNonInterruptingExecution(),
                "closeGuiAfterStart", s.shouldCloseGuiAfterStart(), "lockConflictPolicy", s.getLockConflictPolicy());
    }
    private static JsonObject encodeStep(PathStep s) {
        return object("pos", s.hasGotoTarget() ? s.getGotoPoint() : null, "actions", s.getActions(), "note", s.getNote(),
                "retryCount", s.getRetryCount(), "pathRetryTimeoutSeconds", s.getPathRetryTimeoutSeconds(),
                "arrivalToleranceBlocks", s.getArrivalToleranceBlocks(), "retryExhaustedPolicy", s.getRetryExhaustedPolicy(),
                "retryExhaustedSequenceName", s.getRetryExhaustedSequenceName(),
                "retryExhaustedStepIndex", s.getRetryExhaustedStepIndex(), "retryExhaustedActionIndex", s.getRetryExhaustedActionIndex());
    }
    private static PathSequence decode(JsonObject p) {
        String name = string(p, "name").trim();
        if (name.isEmpty() || name.length() > 128) throw new IllegalArgumentException("Name must contain 1..128 characters");
        PathSequence s = new PathSequence(name);
        s.setCustom(true);
        if (p.has("category")) s.setCategory(string(p, "category"));
        if (p.has("subCategory")) s.setSubCategory(string(p, "subCategory"));
        if (p.has("targetServer")) s.setTargetServer(string(p, "targetServer"));
        if (p.has("note")) s.setNote(string(p, "note"));
        s.setLoopDelayTicks(integer(p, "loopDelayTicks", 0, 0, Integer.MAX_VALUE - 1));
        s.setSingleExecution(bool(p, "singleExecution", false));
        s.setNonInterruptingExecution(bool(p, "nonInterruptingExecution", false));
        s.setCloseGuiAfterStart(bool(p, "closeGuiAfterStart", false));
        if (p.has("lockConflictPolicy")) s.setLockConflictPolicy(string(p, "lockConflictPolicy"));
        if (p.has("steps")) for (JsonElement step : p.getAsJsonArray("steps")) s.addStep(decodeStep(step.getAsJsonObject()));
        return s;
    }
    private static PathStep decodeStep(JsonObject p) {
        double[] pos = {Double.NaN, Double.NaN, Double.NaN};
        if (p.has("pos") && !p.get("pos").isJsonNull()) {
            JsonArray a = p.getAsJsonArray("pos");
            if (a.size() != 3) throw new IllegalArgumentException("pos must contain x,y,z");
            for (int i = 0; i < 3; i++) {
                pos[i] = a.get(i).getAsDouble();
                if (!Double.isFinite(pos[i])) throw new IllegalArgumentException("Coordinates must be finite");
            }
        }
        PathStep step = new PathStep(pos);
        if (p.has("actions")) for (JsonElement action : p.getAsJsonArray("actions")) step.addAction(decodeAction(action.getAsJsonObject()));
        if (p.has("note")) step.setNote(string(p, "note"));
        if (p.has("retryCount")) step.setRetryCount(integer(p, "retryCount", 3, 0, Integer.MAX_VALUE));
        if (p.has("pathRetryTimeoutSeconds")) step.setPathRetryTimeoutSeconds(integer(p, "pathRetryTimeoutSeconds", 5, 0, Integer.MAX_VALUE));
        if (p.has("arrivalToleranceBlocks")) step.setArrivalToleranceBlocks(integer(p, "arrivalToleranceBlocks", 1, 0, Integer.MAX_VALUE));
        if (p.has("retryExhaustedPolicy")) step.setRetryExhaustedPolicy(string(p, "retryExhaustedPolicy"));
        if (p.has("retryExhaustedSequenceName")) step.setRetryExhaustedSequenceName(string(p, "retryExhaustedSequenceName"));
        if (p.has("retryExhaustedStepIndex")) step.setRetryExhaustedStepIndex(integer(p, "retryExhaustedStepIndex", 0, 0, Integer.MAX_VALUE));
        if (p.has("retryExhaustedActionIndex")) step.setRetryExhaustedActionIndex(integer(p, "retryExhaustedActionIndex", 0, 0, Integer.MAX_VALUE));
        return step;
    }
    private static ActionData decodeAction(JsonObject p) {
        String type = string(p, "type");
        if (!ActionDisplayCatalog.getActionDisplayKeys().containsKey(type)) throw new IllegalArgumentException("Unknown action: " + type);
        JsonObject params = p.has("params")
                ? new JsonParser().parse(p.getAsJsonObject("params").toString()).getAsJsonObject()
                : new JsonObject();
        ensurePersistentActionUuid(type, params);
        return new ActionData(type, new JsonParser().parse(params.toString()).getAsJsonObject());
    }

    /**
     * MCP-authored actions need the same stable identity that the GUI editor
     * assigns to stateful actions. Without it, a saved window click or nested
     * sequence emits a validator warning and loses its per-action state after
     * a reload.
     */
    private static void ensurePersistentActionUuid(String type, JsonObject params) {
        if (params == null || type == null) {
            return;
        }
        boolean stateful = "window_click".equalsIgnoreCase(type)
                || "run_sequence".equalsIgnoreCase(type)
                || "run_template".equalsIgnoreCase(type);
        boolean hasUuid = params.has("uuid") && params.get("uuid").isJsonPrimitive()
                && !params.get("uuid").isJsonNull()
                && !params.get("uuid").getAsString().trim().isEmpty();
        if (!stateful || hasUuid) {
            return;
        }
        params.addProperty("uuid", UUID.randomUUID().toString());
    }
    private static void idleForEdit(String name) {
        if (PathSequenceEventListener.isSequenceActiveForMcp(name))
            throw new IllegalStateException("Stop the running sequence before editing it");
    }
    private static JsonObject paths(JsonObject p) throws Exception {
        String op = string(p, "operation");
        if ("list".equals(op)) {
            JsonArray list = new JsonArray();
            for (PathSequence s : PathSequenceManager.getAllSequences())
                list.add(object("name", s.getName(), "category", s.getCategory(), "subCategory", s.getSubCategory(),
                        "targetServer", s.getTargetServer(), "steps", s.getSteps().size(), "note", s.getNote()));
            return object("sequences", list);
        }
        if ("get".equals(op)) return object("sequence", encode(requireSequence(string(p, "name"))));
        PathSequence draft;
        if ("put".equals(op)) draft = decode(p.getAsJsonObject("sequence"));
        else draft = new PathSequence(requireSequence(string(p, "name")), true);
        idleForEdit(draft.getName());
        List<PathSequence> all = new ArrayList<>(PathSequenceManager.getAllSequences());
        if ("delete".equals(op)) {
            all.removeIf(s -> s.getName().equals(draft.getName()));
            PathSequenceManager.saveAllSequencesChecked(all);
            return object("deleted", draft.getName());
        }
        if (!"put".equals(op)) {
            boolean insert = bool(p, "insert", false);
            int stepIndex = integer(p, "stepIndex", -1, 0, draft.getSteps().size());
            if (stepIndex < 0) throw new IllegalArgumentException("stepIndex required");
            if ("step_put".equals(op)) {
                PathStep step = decodeStep(p.getAsJsonObject("step"));
                if (insert) draft.getSteps().add(stepIndex, step); else draft.getSteps().set(stepIndex, step);
            } else if ("step_delete".equals(op)) draft.getSteps().remove(stepIndex);
            else {
                List<ActionData> actions = draft.getSteps().get(stepIndex).getActions();
                int index = integer(p, "actionIndex", -1, 0, actions.size());
                if (index < 0) throw new IllegalArgumentException("actionIndex required");
                if ("action_delete".equals(op)) actions.remove(index);
                else if ("action_put".equals(op)) {
                    ActionData action = decodeAction(p.getAsJsonObject("action"));
                    if (insert) actions.add(index, action); else actions.set(index, action);
                } else throw new IllegalArgumentException("Unknown path operation: " + op);
            }
        }
        all.removeIf(s -> s.getName().equals(draft.getName()));
        all.add(draft);
        PathSequenceManager.saveAllSequencesChecked(all);
        return object("saved", draft.getName(), "sequence", encode(draft));
    }
    private static JsonObject run(JsonObject p) {
        world();
        int supplied = (p.has("name") ? 1 : 0) + (p.has("sequence") ? 1 : 0) + (p.has("actions") ? 1 : 0);
        if (supplied != 1) throw new IllegalArgumentException("Supply exactly one of name, sequence, actions");
        PathSequence s;
        boolean temporary = !p.has("name");
        if (!temporary) s = requireSequence(string(p, "name"));
        else {
            JsonObject definition = p.has("sequence") ? new JsonParser().parse(p.get("sequence").toString()).getAsJsonObject()
                    : object("steps", Arrays.asList(object("pos", null, "actions", p.get("actions"))));
            definition.addProperty("name", "__mcp_" + UUID.randomUUID().toString());
            s = decode(definition);
        }
        ensureTargetServer(s);
        if (s.getSteps().isEmpty()) throw new IllegalArgumentException("Cannot execute empty sequence");
        int loops = integer(p, "loops", 1, -1, Integer.MAX_VALUE);
        if (loops == 0) throw new IllegalArgumentException("loops must be positive or -1");
        int step = integer(p, "startStep", 0, 0, s.getSteps().size() - 1);
        int action = integer(p, "startAction", 0, 0, Math.max(0, s.getSteps().get(step).getActions().size() - 1));
        if (PathSequenceEventListener.isSequenceActiveForMcp(null) && !bool(p, "replace", false))
            throw new IllegalStateException("A sequence is running. Pass replace=true or stop it first");
        Map<String, Object> variables = p.has("variables") ? GSON.fromJson(p.get("variables"), Map.class) : null;
        List<PathSequence> validationSet = new ArrayList<>(PathSequenceManager.getAllSequences());
        validationSet.removeIf(existing -> existing.getName().equals(s.getName()));
        validationSet.add(s);
        List<PathConfigValidator.Issue> issues = PathConfigValidator.validateSequences(validationSet);
        for (PathConfigValidator.Issue issue : issues) {
            if (issue.getSequenceName().equals(s.getName()) && issue.getSeverity() == PathConfigValidator.Severity.ERROR)
                throw new IllegalArgumentException("Validation failed: " + issue.toCompactText());
        }
        long eventAfterId = McpEventJournal.INSTANCE.version();
        String eventSessionId = McpEventJournal.INSTANCE.sessionId();
        if (temporary) PathSequenceManager.runTransientSequence(s, loops, variables, step, action);
        else PathSequenceManager.runSequenceForMcp(s.getName(), loops, variables, step, action);
        String executionSessionId = PathSequenceEventListener.getActiveExecutionLogSessionId(s.getName());
        return object("accepted", true, "runId", s.getName(), "executionSessionId",
                executionSessionId.isEmpty() ? null : executionSessionId, "temporary", temporary, "loops", loops,
                "eventSessionId", eventSessionId, "eventAfterId", eventAfterId,
                "next", "Call mythos_wait with executionSessionId, or inspect mythos_status/mythos_logs and read events after eventAfterId");
    }
    private JsonObject control(JsonObject p) {
        String op = string(p, "operation");
        String scope = p.has("scope") ? string(p, "scope") : "all";
        if (!Arrays.asList("foreground", "background", "all").contains(scope)) throw new IllegalArgumentException("Invalid scope");
        boolean foreground = !"background".equals(scope), background = !"foreground".equals(scope);
        switch (op) {
            case "stop_all": KillAuraHandler.endMcpRuntime(); foreground = true; background = true;
            case "stop":
                boolean navigationBeforeStop = foreground && EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating();
                if (foreground) {
                    PathSequenceEventListener.stopForegroundSequenceByAction(); GuiInventory.isLooping = false;
                    PathSequenceManager.clearRunSequenceCallStack(); EmbeddedNavigationHandler.INSTANCE.stop();
                }
                if (background) PathSequenceEventListener.stopAllBackgroundRunners();
                PathSequenceManager.cleanupTransientSequences();
                JsonObject stopped = status();
                stopped.addProperty("navigationStopped", navigationBeforeStop);
                return stopped;
            case "pause":
                if (foreground) PathSequenceEventListener.pauseForegroundSequenceByAction();
                if (background) PathSequenceEventListener.pauseBackgroundSequencesByAction(); break;
            case "resume":
                if (foreground) PathSequenceEventListener.resumeForegroundSequenceByAction();
                if (background) PathSequenceEventListener.resumeBackgroundSequencesByAction(); break;
            default: throw new IllegalArgumentException("Unknown control operation");
        }
        return status();
    }

    private static JsonObject waitForExecution(JsonObject p) throws InterruptedException {
        String sessionId = string(p, "sessionId").trim();
        int waitMs = integer(p, "waitMs", 0, 0, 25000);
        ExecutionLogManager.SessionSnapshot snapshot = ExecutionLogManager.awaitFinished(sessionId, waitMs);
        if (snapshot == null) throw new IllegalArgumentException("Unknown execution session: " + sessionId);
        return sessionResult(snapshot, bool(p, "includeEvents", false), waitMs > 0 && !snapshot.isFinished());
    }

    private static JsonObject sessionResult(ExecutionLogManager.SessionSnapshot session, boolean includeEvents,
            boolean timedOut) {
        JsonObject result = object("found", true, "sessionId", session.getSessionId(),
                "sequence", session.getSequenceName(), "background", session.isBackground(),
                "finished", session.isFinished(), "success", session.isFinished() ? session.isSuccess() : null,
                "outcome", !session.isFinished() ? "running" : session.isSuccess() ? "success" : "failure",
                "reason", session.getFinishReason(), "status", session.getFinalStatus(),
                "startTimeMs", session.getStartTime(), "endTimeMs", session.getEndTime() <= 0L ? null : session.getEndTime(),
                "durationMs", session.getDurationMs(), "timedOut", timedOut);
        if (includeEvents) {
            JsonArray events = new JsonArray();
            for (ExecutionLogManager.ExecutionEvent event : session.getEvents()) {
                events.add(object("timestampMs", event.getTimestamp(), "type", event.getType(),
                        "stepIndex", event.getStepIndex(), "actionIndex", event.getActionIndex(),
                        "message", event.getMessage(), "status", event.getStatus(),
                        "variables", event.getVariablePreview()));
            }
            result.add("events", events);
        }
        return result;
    }

    private static JsonObject logs(JsonObject p) {
        boolean includeEvents = bool(p, "includeEvents", false);
        if (p.has("sessionId")) {
            String sessionId = string(p, "sessionId");
            ExecutionLogManager.SessionSnapshot session = ExecutionLogManager.getSessionSnapshot(sessionId);
            if (session == null) return object("found", false, "sessionId", sessionId, "text", "");
            JsonObject result = sessionResult(session, includeEvents, false);
            result.addProperty("text", ExecutionLogManager.getSessionText(sessionId));
            return result;
        }
        List<ExecutionLogManager.SessionSnapshot> sessions = ExecutionLogManager.getSessionsSnapshot();
        JsonArray list = new JsonArray();
        int limit = integer(p, "limit", 10, 1, 100);
        for (int i = 0; i < Math.min(sessions.size(), limit); i++) {
            ExecutionLogManager.SessionSnapshot s = sessions.get(i);
            list.add(sessionResult(s, includeEvents, false));
        }
        return object("sessions", list);
    }

    private static JsonObject navigation(JsonObject p) {
        com.zszl.zszlScriptMod.shadowbaritone.api.command.manager.ICommandManager manager =
                com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
        if (p.has("command")) {
            world();
            boolean activeBefore = EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating();
            boolean dispatched = manager.execute(string(p, "command"));
            return object("dispatched", dispatched, "navigationActiveBefore", activeBefore,
                    "navigationActive", EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating());
        }
        JsonArray commands = new JsonArray();
        for (com.zszl.zszlScriptMod.shadowbaritone.api.command.ICommand command : manager.getRegistry().entries)
            commands.add(object("names", command.getNames(), "description", command.getShortDesc(), "usage", command.getLongDesc()));
        return object("commands", commands);
    }
    private static JsonObject aura(JsonObject p) {
        String op = string(p, "operation");
        if ("describe".equals(op)) return object("fields", McpFields.describe(KillAuraHandler.class));
        if ("stop".equals(op)) KillAuraHandler.endMcpRuntime();
        else if ("start".equals(op)) {
            world();
            KillAuraHandler.beginMcpRuntime(p.has("fields") ? p.getAsJsonObject("fields") : new JsonObject(),
                    bool(p, "enabled", true), integer(p, "durationTicks", 0, 0, Integer.MAX_VALUE));
        } else throw new IllegalArgumentException("Expected describe/start/stop");
        return object("temporary", KillAuraHandler.isMcpRuntimeActive(), "enabled", KillAuraHandler.enabled,
                "effectiveFields", McpFields.snapshot(KillAuraHandler.class));
    }
    private static JsonObject packets(JsonObject p) {
        String op = string(p, "operation");
        if ("capture_start".equals(op)) { world(); PacketCaptureHandler.isCapturing = true; }
        else if ("capture_stop".equals(op)) PacketCaptureHandler.isCapturing = false;
        else if ("clear".equals(op)) PacketCaptureHandler.clearAllPackets();
        else if ("list".equals(op)) {
            String direction = p.has("direction") ? string(p, "direction") : "C2S";
            if (!"C2S".equals(direction) && !"S2C".equals(direction)) throw new IllegalArgumentException("Expected C2S or S2C");
            List<PacketCaptureHandler.CapturedPacketData> source = "S2C".equals(direction)
                    ? PacketCaptureHandler.capturedReceivedPackets : PacketCaptureHandler.capturedPackets;
            int offset = integer(p, "offset", 0, 0, Integer.MAX_VALUE), limit = integer(p, "limit", 20, 1, 100);
            JsonArray items = new JsonArray(); int total;
            synchronized (source) {
                total = source.size();
                for (int i = offset; i < source.size() && items.size() < limit; i++) {
                    PacketCaptureHandler.CapturedPacketData data = source.get(i);
                    items.add(object("index", i, "timestamp", data.timestamp, "class", data.packetClassName,
                            "packetId", data.packetId, "channel", data.channel, "hex", data.getHexData(), "decoded", data.getDecodedData()));
                }
            }
            return object("packets", items, "total", total, "nextOffset", offset + items.size());
        } else throw new IllegalArgumentException("Unknown packet operation");
        return object("capturing", PacketCaptureHandler.isCapturing);
    }
}
