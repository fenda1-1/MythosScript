package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** In-process registry: one listening hub plus workers that join the same port. */
public final class McpClientHub {
    public static final int ALL_PIDS = -1;
    static final long STALE_MS = 45_000L;
    private static final int INVOKE_TIMEOUT_SECONDS = 35;
    private final int hubPid;
    private final McpProtocol.Backend local;
    private final ConcurrentHashMap<Integer, Slot> slots = new ConcurrentHashMap<Integer, Slot>();

    public McpClientHub(int hubPid, McpProtocol.Backend local) {
        this.hubPid = hubPid;
        this.local = local;
        slots.put(hubPid, new Slot(hubPid, true));
    }

    public static int currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return Integer.parseInt(at < 0 ? name : name.substring(0, at));
    }

    public int hubPid() { return hubPid; }

    public McpProtocol.Backend routingBackend() {
        return new McpProtocol.Backend() {
            @Override public JsonArray tools() { return local.tools(); }
            @Override public JsonObject call(String name, JsonObject arguments) throws Exception {
                return dispatch(name, arguments);
            }
        };
    }

    public JsonObject dispatch(String name, JsonObject arguments) throws Exception {
        expireStale();
        JsonObject stripped = arguments == null ? new JsonObject() : copy(arguments);
        if ("mythos_clients".equals(name)) {
            takePid(stripped);
            return list(takePlayer(stripped));
        }
        Integer pid = takePid(stripped);
        String player = takePlayer(stripped);
        JsonObject data = route(pid, player, name, stripped);
        if ("mythos_discover".equals(name) && (pid == null || pid.intValue() != ALL_PIDS)) {
            JsonObject listed = list(null);
            data.add("clients", listed.get("clients"));
            data.add("players", listed.get("players"));
            if (!data.has("localPid")) data.addProperty("localPid", pid == null ? hubPid : pid);
        }
        return data;
    }

    public void refreshLocal(JsonObject info) {
        Slot slot = slots.get(hubPid);
        if (slot != null && slot.local && info != null) slot.info = copy(info);
    }

    public JsonObject list() { return list(null); }

    public JsonObject list(String playerQuery) {
        expireStale();
        JsonArray clients = new JsonArray();
        Set<String> names = new LinkedHashSet<String>();
        String needle = playerQuery == null ? "" : playerQuery.trim().toLowerCase(Locale.ROOT);
        for (Slot slot : slots.values()) {
            JsonObject row = describe(slot);
            String player = playerName(slot);
            if (player != null && inWorld(slot)) names.add(player);
            if (!needle.isEmpty()) {
                String listed = player == null ? "" : player.toLowerCase(Locale.ROOT);
                if (!listed.contains(needle) && !String.valueOf(slot.pid).equals(needle)) continue;
            }
            clients.add(row);
        }
        JsonArray players = new JsonArray();
        for (String name : names) {
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            players.add(name);
        }
        return object("localPid", hubPid, "role", "hub", "players", players, "clients", clients);
    }

    public JsonObject worker(JsonObject body) throws Exception {
        String op = string(body, "op");
        int pid = integer(body, "pid", 0, 1, Integer.MAX_VALUE);
        if ("register".equals(op)) {
            Slot slot = slots.get(pid);
            if (slot == null || slot.local) slot = new Slot(pid, false);
            if (body.has("info") && body.get("info").isJsonObject()) slot.info = copy(body.getAsJsonObject("info"));
            slot.touch();
            slots.put(pid, slot);
            return object("ok", true, "hubPid", hubPid);
        }
        if ("unregister".equals(op)) {
            Slot removed = slots.remove(pid);
            if (removed != null) removed.failAll("Client unregistered");
            return object("ok", true);
        }
        Slot slot = requireRemote(pid);
        if ("poll".equals(op)) {
            if (body.has("info") && body.get("info").isJsonObject()) slot.info = copy(body.getAsJsonObject("info"));
            slot.touch();
            int wait = integer(body, "waitMs", 0, 0, 25000);
            Job job = slot.take(wait);
            if (job == null) return object("job", null);
            return object("job", object("id", job.id, "name", job.name, "arguments", job.args));
        }
        if ("result".equals(op)) {
            slot.touch();
            String jobId = string(body, "jobId");
            Job job = slot.inflight.remove(jobId);
            if (job == null) return object("ok", false);
            if (bool(body, "ok", false)) {
                JsonObject data = body.has("data") && body.get("data").isJsonObject()
                        ? copy(body.getAsJsonObject("data")) : new JsonObject();
                job.future.complete(data);
            } else {
                String error = body.has("error") ? body.get("error").getAsString() : "Worker failed";
                job.future.completeExceptionally(new IllegalStateException(error));
            }
            return object("ok", true);
        }
        throw new IllegalArgumentException("Unknown worker op: " + op);
    }

    public void close() {
        for (Slot slot : slots.values()) slot.failAll("MCP hub stopped");
        slots.clear();
    }

    JsonObject describe(Slot slot) {
        JsonObject row = object("pid", slot.pid, "role", slot.local ? "hub" : "worker",
                "local", slot.local, "lastSeenMs", slot.local ? 0L : Math.max(0L, System.currentTimeMillis() - slot.lastSeen));
        if (slot.info != null) {
            if (slot.info.has("player")) row.add("player", slot.info.get("player"));
            if (slot.info.has("server")) row.add("server", slot.info.get("server"));
            if (slot.info.has("inWorld")) row.add("inWorld", slot.info.get("inWorld"));
        }
        return row;
    }

    static Integer takePid(JsonObject args) {
        if (args == null || !args.has("pid")) return null;
        int pid = integer(args, "pid", 0, ALL_PIDS, Integer.MAX_VALUE);
        args.remove("pid");
        return pid;
    }

    static String takePlayer(JsonObject args) {
        if (args == null) return null;
        String player = optionalString(args, "player");
        String username = optionalString(args, "username");
        args.remove("player");
        args.remove("username");
        if (player != null && username != null && !player.equalsIgnoreCase(username))
            throw new IllegalArgumentException("player and username disagree");
        String value = player != null ? player : username;
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String optionalString(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        if (!args.get(key).isJsonPrimitive() || !args.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: " + key);
        return args.get(key).getAsString();
    }

    private JsonObject route(Integer pid, String player, String name, JsonObject args) throws Exception {
        List<Integer> ids = livePids();
        if (pid == null && player != null) pid = resolvePlayer(player);
        if (pid == null) pid = uniquePlayingPid();
        if (pid == null) {
            if (ids.size() == 1) return invoke(ids.get(0), name, args);
            List<String> names = playingNames();
            if (names.isEmpty())
                throw new IllegalArgumentException("pid required when multiple clients are connected and none are in a world: "
                        + ids + " (pass pid, or player=\"all\" / pid=-1 to broadcast). Do not guess.");
            throw new IllegalArgumentException("player required when multiple in-world usernames are connected: "
                    + names + " (pass player=\"Name\", or pid; player=\"all\" / pid=-1 broadcasts). Do not guess.");
        }
        if (pid.intValue() == ALL_PIDS) return broadcast(name, args, ids);
        return invoke(pid, name, args);
    }

    private Integer resolvePlayer(String player) {
        if ("all".equalsIgnoreCase(player) || "*".equals(player)) return ALL_PIDS;
        List<Integer> matches = new ArrayList<Integer>();
        String needle = player.toLowerCase(Locale.ROOT);
        for (Slot slot : slots.values()) {
            String name = playerName(slot);
            if (name != null && name.toLowerCase(Locale.ROOT).equals(needle)) matches.add(slot.pid);
        }
        if (matches.size() == 1) return matches.get(0);
        if (matches.isEmpty())
            throw new IllegalArgumentException("Unknown player \"" + player + "\"; in-world usernames: " + playingNames());
        throw new IllegalArgumentException("Player \"" + player + "\" matches multiple clients " + matches + "; pass pid");
    }

    private Integer uniquePlayingPid() {
        List<Integer> playing = new ArrayList<Integer>();
        Set<String> names = new LinkedHashSet<String>();
        for (Slot slot : slots.values()) {
            String name = playerName(slot);
            if (name == null || !inWorld(slot)) continue;
            playing.add(slot.pid);
            names.add(name.toLowerCase(Locale.ROOT));
        }
        if (names.size() == 1 && playing.size() == 1) return playing.get(0);
        return null;
    }

    private List<String> playingNames() {
        Set<String> names = new LinkedHashSet<String>();
        for (Slot slot : slots.values()) {
            String name = playerName(slot);
            if (name != null && inWorld(slot)) names.add(name);
        }
        return new ArrayList<String>(names);
    }

    static String playerName(Slot slot) {
        if (slot == null || slot.info == null || !slot.info.has("player") || slot.info.get("player").isJsonNull())
            return null;
        if (!slot.info.get("player").isJsonPrimitive() || !slot.info.get("player").getAsJsonPrimitive().isString())
            return null;
        String name = slot.info.get("player").getAsString().trim();
        return name.isEmpty() ? null : name;
    }

    static boolean inWorld(Slot slot) {
        return slot != null && slot.info != null && slot.info.has("inWorld")
                && slot.info.get("inWorld").isJsonPrimitive() && slot.info.get("inWorld").getAsBoolean();
    }

    private JsonObject broadcast(String name, JsonObject args, List<Integer> ids) throws Exception {
        JsonArray results = new JsonArray();
        for (Integer id : ids) {
            try {
                results.add(object("pid", id, "ok", true, "data", invoke(id, name, args)));
            } catch (Exception e) {
                results.add(object("pid", id, "ok", false, "error",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        return object("pid", ALL_PIDS, "results", results);
    }

    private JsonObject invoke(int pid, String name, JsonObject args) throws Exception {
        Slot slot = slots.get(pid);
        if (slot == null) throw new IllegalArgumentException("Unknown client pid: " + pid + "; live=" + livePids());
        if (slot.local) return local.call(name, args);
        Job job = new Job(name, args);
        slot.inflight.put(job.id, job);
        synchronized (slot) {
            slot.jobs.add(job);
            slot.notifyAll();
        }
        try {
            return job.future.get(INVOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            slot.inflight.remove(job.id);
            throw new IllegalStateException("Timed out waiting for client pid " + pid);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw cause instanceof Exception ? (Exception) cause : new IllegalStateException(cause.getMessage(), cause);
        }
    }

    private Slot requireRemote(int pid) {
        Slot slot = slots.get(pid);
        if (slot == null || slot.local) throw new IllegalArgumentException("Unknown worker pid: " + pid);
        return slot;
    }

    private List<Integer> livePids() {
        expireStale();
        return new ArrayList<Integer>(slots.keySet());
    }

    private void expireStale() {
        long now = System.currentTimeMillis();
        for (Slot slot : slots.values()) {
            if (slot.local) continue;
            if (now - slot.lastSeen > STALE_MS) {
                slots.remove(slot.pid, slot);
                slot.failAll("Client timed out");
            }
        }
    }

    static final class Job {
        final String id = UUID.randomUUID().toString();
        final String name;
        final JsonObject args;
        final CompletableFuture<JsonObject> future = new CompletableFuture<JsonObject>();
        Job(String name, JsonObject args) {
            this.name = name;
            this.args = args;
        }
    }

    static final class Slot {
        final int pid;
        final boolean local;
        final List<Job> jobs = new ArrayList<Job>();
        final ConcurrentHashMap<String, Job> inflight = new ConcurrentHashMap<String, Job>();
        volatile long lastSeen = System.currentTimeMillis();
        volatile JsonObject info;
        Slot(int pid, boolean local) { this.pid = pid; this.local = local; }
        void touch() { lastSeen = System.currentTimeMillis(); }
        Job take(int waitMs) throws InterruptedException {
            synchronized (this) {
                long deadline = System.currentTimeMillis() + waitMs;
                while (jobs.isEmpty()) {
                    long left = deadline - System.currentTimeMillis();
                    if (waitMs <= 0 || left <= 0) return null;
                    wait(left);
                }
                return jobs.remove(0);
            }
        }
        void failAll(String message) {
            List<Job> pending;
            synchronized (this) {
                pending = new ArrayList<Job>(jobs);
                jobs.clear();
            }
            pending.addAll(inflight.values());
            inflight.clear();
            for (Job job : pending) job.future.completeExceptionally(new IllegalStateException(message));
        }
    }
}
