package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.*;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.zszlScriptMod;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import java.util.concurrent.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Loopback-only Streamable HTTP. First process binds the port; later processes join it by pid. */
public final class MythosScriptMcpServer {
    public enum Role { STOPPED, HUB, WORKER }

    private static HttpServer server;
    private static ExecutorService executor;
    private static McpGameService backend;
    private static McpClientHub hub;
    private static volatile McpServerSettings settings = new McpServerSettings(false, 8765);
    private static volatile String lastError = "";
    private static volatile Role role = Role.STOPPED;
    private static volatile int localPid;
    private static volatile String token = "";
    private static volatile boolean workerRunning;
    private static Thread workerThread;
    private static boolean shutdownHookRegistered;
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private static final String DISCOVERY_DIRECTORY = "MythosScript";
    private static final String DISCOVERY_FILE = "mcp-endpoints.json";
    private MythosScriptMcpServer() {}

    public static Path settingsPath() { return Paths.get(ModConfig.CONFIG_DIR, "mcp_server.json"); }
    public static McpServerSettings settings() { return settings; }
    public static synchronized boolean isRunning() { return role == Role.HUB || role == Role.WORKER; }
    public static synchronized int activePort() {
        if (server != null) return server.getAddress().getPort();
        return role == Role.WORKER ? settings.port : 0;
    }
    public static String lastError() { return lastError; }
    public static String endpoint() { return "http://127.0.0.1:" + (activePort() == 0 ? settings.port : activePort()) + "/mcp"; }
    public static Role role() { return role; }
    public static int localPid() { return localPid == 0 ? McpClientHub.currentPid() : localPid; }
    public static synchronized JsonObject clients() { return hub == null ? object("clients", new com.google.gson.JsonArray()) : hub.list(); }

    /**
     * Publishes a machine-local discovery record for the Codex bridge. The
     * bridge still authenticates with the normal per-install token; this
     * registry only removes the need to hard-code a game directory in the
     * Codex configuration.
     */
    private static Path discoveryPath() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.trim().isEmpty()
                ? Paths.get(System.getProperty("user.home"), ".mythosscript")
                : Paths.get(localAppData, DISCOVERY_DIRECTORY);
        return root.resolve(DISCOVERY_FILE);
    }

    private static void publishDiscovery() {
        if (!isRunning()) return;
        JsonObject entry = object("pid", localPid(), "port", activePort(), "endpoint", endpoint(),
                "role", role.name(), "tokenFile", Paths.get(ModConfig.CONFIG_DIR, "mcp.token")
                        .toAbsolutePath().toString(), "updatedAt", System.currentTimeMillis());
        updateDiscovery(entry, false);
    }

    private static void removeDiscovery() {
        updateDiscovery(null, true);
    }

    private static void updateDiscovery(JsonObject replacement, boolean removeCurrent) {
        Path registry = discoveryPath();
        Path lockPath = registry.resolveSibling(DISCOVERY_FILE + ".lock");
        try {
            Files.createDirectories(registry.getParent());
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE); FileLock ignored = lockChannel.lock()) {
                JsonArray entries = new JsonArray();
                if (Files.exists(registry)) {
                    try {
                        JsonObject root = new JsonParser().parse(
                                new String(Files.readAllBytes(registry), StandardCharsets.UTF_8)).getAsJsonObject();
                        if (root.has("entries") && root.get("entries").isJsonArray()) {
                            entries = root.getAsJsonArray("entries");
                        }
                    } catch (Exception ignoredRead) {
                        // A torn/invalid discovery file is recoverable; the
                        // next running client rewrites the registry.
                    }
                }
                JsonArray next = new JsonArray();
                int pid = localPid();
                for (int i = 0; i < entries.size(); i++) {
                    JsonObject old = entries.get(i).isJsonObject() ? entries.get(i).getAsJsonObject() : null;
                    if (old == null || (old.has("pid") && old.get("pid").getAsInt() == pid)) continue;
                    next.add(old);
                }
                if (!removeCurrent && replacement != null) next.add(replacement);
                JsonObject root = object("version", 1, "entries", next);
                Path temp = registry.resolveSibling(DISCOVERY_FILE + "." + pid + ".tmp");
                Files.write(temp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try {
                    Files.move(temp, registry, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, registry, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.debug("MCP discovery registry update failed", e);
        }
    }

    /** Workers authenticate to the current hub, even when each installation
     * has its own local token file. This keeps same-port multi-client routing
     * independent of which game directory created the hub. */
    private static String workerToken() {
        Path registry = discoveryPath();
        try {
            if (Files.exists(registry)) {
                JsonObject root = new JsonParser().parse(
                        new String(Files.readAllBytes(registry), StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("entries") && root.get("entries").isJsonArray()) {
                    for (int i = 0; i < root.getAsJsonArray("entries").size(); i++) {
                        JsonObject entry = root.getAsJsonArray("entries").get(i).getAsJsonObject();
                        if (!"HUB".equalsIgnoreCase(string(entry, "role"))
                                || integer(entry, "port", -1, 0, 65535) != settings.port
                                || !entry.has("tokenFile")) continue;
                        Path tokenPath = Paths.get(string(entry, "tokenFile"));
                        if (Files.exists(tokenPath)) {
                            String candidate = new String(Files.readAllBytes(tokenPath), StandardCharsets.UTF_8).trim();
                            if (candidate.length() >= 32) return candidate;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to this installation's token while the hub registry
            // is being rotated or a previous hub is shutting down.
        }
        return token;
    }

    public static synchronized void start() {
        if (isRunning()) return;
        try {
            McpServerSettings defaults = new McpServerSettings(true, 8765);
            if (!Files.exists(settingsPath())) {
                defaults = new McpServerSettings(!Boolean.getBoolean("mythosscript.mcp.disabled"),
                        Integer.parseInt(System.getProperty("mythosscript.mcp.port",
                                System.getenv().getOrDefault("MYTHOSSCRIPT_MCP_PORT", "8765"))));
            }
            settings = McpServerSettings.read(settingsPath(), defaults);
            configure(settings, false);
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            zszlScriptMod.LOGGER.error("MCP startup failed", e);
        }
    }

    /** Client thread only. Bind first; if the port is taken, join the existing hub. */
    public static synchronized void configure(McpServerSettings requested, boolean persist) throws Exception {
        HttpServer candidate = null;
        ExecutorService candidateExecutor = null;
        McpClientHub candidateHub = null;
        try {
            localPid = McpClientHub.currentPid();
            boolean alreadyHub = role == Role.HUB && server != null && activePort() == requested.port;
            boolean alreadyWorker = role == Role.WORKER && settings.port == requested.port && workerRunning;
            if (requested.enabled && !alreadyHub && !alreadyWorker) {
                Path tokenPath = Paths.get(ModConfig.CONFIG_DIR, "mcp.token");
                Files.createDirectories(tokenPath.getParent());
                if (!Files.exists(tokenPath)) {
                    byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
                    Files.write(tokenPath, Base64.getUrlEncoder().withoutPadding().encode(bytes), StandardOpenOption.CREATE_NEW);
                }
                token = new String(Files.readAllBytes(tokenPath), StandardCharsets.UTF_8).trim();
                if (token.length() < 32) throw new IOException("mcp.token must contain at least 32 characters");
                if (backend == null) backend = new McpGameService();
                try {
                    candidateHub = new McpClientHub(localPid, backend);
                    candidate = HttpServer.create(new InetSocketAddress("127.0.0.1", requested.port), 16);
                    McpProtocol protocol = new McpProtocol(candidateHub.routingBackend());
                    candidateExecutor = new ThreadPoolExecutor(8, 32, 60, TimeUnit.SECONDS,
                            new SynchronousQueue<Runnable>(),
                            r -> { Thread t = new Thread(r, "MythosScript-MCP"); t.setDaemon(true); return t; },
                            new ThreadPoolExecutor.CallerRunsPolicy());
                    candidate.setExecutor(candidateExecutor);
                    final McpClientHub boundHub = candidateHub;
                    candidate.createContext("/mcp", exchange -> handle(exchange, token, protocol, boundHub));
                    final HttpServer toStart = candidate;
                    FutureTask<Void> startTask = new FutureTask<Void>(() -> { toStart.start(); return null; });
                    Thread starter = new Thread(startTask, "MythosScript-MCP-start");
                    starter.setDaemon(true); starter.start(); startTask.get();
                } catch (BindException bind) {
                    if (candidate != null) { candidate.stop(0); candidate = null; }
                    if (candidateExecutor != null) { candidateExecutor.shutdownNow(); candidateExecutor = null; }
                    if (candidateHub != null) { candidateHub.close(); candidateHub = null; }
                    if (persist) requested.write(settingsPath());
                    stopLocked();
                    settings = requested;
                    startWorkerLocked();
                    lastError = "";
                    if (!shutdownHookRegistered) {
                        Runtime.getRuntime().addShutdownHook(new Thread(MythosScriptMcpServer::stop, "MythosScript-MCP-shutdown"));
                        shutdownHookRegistered = true;
                    }
                    publishDiscovery();
                    return;
                }
            }
            if (persist) requested.write(settingsPath());
            if (!requested.enabled || candidate != null) {
                stopLocked();
                server = candidate;
                executor = candidateExecutor;
                hub = candidateHub;
                role = candidate != null ? Role.HUB : Role.STOPPED;
                if (backend != null) backend.setAccepting(requested.enabled && role == Role.HUB);
            }
            settings = requested;
            lastError = "";
            publishDiscovery();
            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(MythosScriptMcpServer::stop, "MythosScript-MCP-shutdown"));
                shutdownHookRegistered = true;
            }
        } catch (Exception e) {
            if (candidate != null) candidate.stop(0);
            if (candidateExecutor != null) candidateExecutor.shutdownNow();
            if (candidateHub != null) candidateHub.close();
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        }
    }
    public static synchronized void stop() { stopLocked(); }
    private static void stopLocked() {
        removeDiscovery();
        workerRunning = false;
        if (workerThread != null) workerThread.interrupt();
        workerThread = null;
        if (backend != null) backend.setAccepting(false);
        if (hub != null) hub.close();
        hub = null;
        if (server != null) server.stop(0);
        server = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
        role = Role.STOPPED;
    }
    private static void startWorkerLocked() {
        workerRunning = true;
        role = Role.WORKER;
        if (backend != null) backend.setAccepting(true);
        publishDiscovery();
        workerThread = new Thread(MythosScriptMcpServer::runWorker, "MythosScript-MCP-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }
    private static void runWorker() {
        while (workerRunning) {
            try {
                workerPost(object("op", "register", "pid", localPid(), "info", clientInfo()));
                while (workerRunning) {
                    JsonObject poll = workerPost(object("op", "poll", "pid", localPid(), "waitMs", 25000, "info", clientInfo()));
                    if (!poll.has("job") || poll.get("job").isJsonNull() || !poll.get("job").isJsonObject()) continue;
                    JsonObject job = poll.getAsJsonObject("job");
                    try {
                        JsonObject data = backend.call(string(job, "name"),
                                job.has("arguments") && job.get("arguments").isJsonObject()
                                        ? job.getAsJsonObject("arguments") : new JsonObject());
                        workerPost(object("op", "result", "pid", localPid(), "jobId", string(job, "id"),
                                "ok", true, "data", data));
                    } catch (Exception e) {
                        workerPost(object("op", "result", "pid", localPid(), "jobId", string(job, "id"),
                                "ok", false, "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    }
                }
            } catch (Exception e) {
                if (!workerRunning) return;
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (tryPromote()) return;
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        try { workerPost(object("op", "unregister", "pid", localPid())); } catch (Exception ignored) {}
    }
    private static synchronized boolean tryPromote() {
        if (!workerRunning || !settings.enabled) return false;
        HttpServer candidate = null;
        ExecutorService candidateExecutor = null;
        McpClientHub candidateHub = null;
        try {
            candidateHub = new McpClientHub(localPid(), backend);
            candidate = HttpServer.create(new InetSocketAddress("127.0.0.1", settings.port), 16);
            McpProtocol protocol = new McpProtocol(candidateHub.routingBackend());
            candidateExecutor = new ThreadPoolExecutor(8, 32, 60, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    r -> { Thread t = new Thread(r, "MythosScript-MCP"); t.setDaemon(true); return t; },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            candidate.setExecutor(candidateExecutor);
            final McpClientHub boundHub = candidateHub;
            candidate.createContext("/mcp", exchange -> handle(exchange, token, protocol, boundHub));
            final HttpServer toStart = candidate;
            FutureTask<Void> startTask = new FutureTask<Void>(() -> { toStart.start(); return null; });
            Thread starter = new Thread(startTask, "MythosScript-MCP-start");
            starter.setDaemon(true); starter.start(); startTask.get();
            workerRunning = false;
            if (server != null) server.stop(0);
            if (executor != null) executor.shutdownNow();
            if (hub != null) hub.close();
            server = candidate;
            executor = candidateExecutor;
            hub = candidateHub;
            role = Role.HUB;
            if (backend != null) backend.setAccepting(true);
            publishDiscovery();
            lastError = "";
            return true;
        } catch (BindException e) {
            if (candidate != null) candidate.stop(0);
            if (candidateExecutor != null) candidateExecutor.shutdownNow();
            if (candidateHub != null) candidateHub.close();
            return false;
        } catch (Exception e) {
            if (candidate != null) candidate.stop(0);
            if (candidateExecutor != null) candidateExecutor.shutdownNow();
            if (candidateHub != null) candidateHub.close();
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return false;
        }
    }
    private static JsonObject clientInfo() {
        return backend == null ? new JsonObject() : backend.clientInfo();
    }
    private static JsonObject workerPost(JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        URL url = new URL("http://127.0.0.1:" + settings.port + "/mcp/worker");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        try {
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + workerToken());
            connection.getOutputStream().write(bytes);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = "";
            if (stream != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048]; int n;
                while ((n = stream.read(buffer)) >= 0) out.write(buffer, 0, n);
                text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            if (status != 200) throw new IOException("Hub HTTP " + status);
            return new JsonParser().parse(text).getAsJsonObject();
        } finally { connection.disconnect(); }
    }
    static void handle(HttpExchange ex, String token, McpProtocol protocol) throws IOException {
        handle(ex, token, protocol, null);
    }
    static void handle(HttpExchange ex, String token, McpProtocol protocol, McpClientHub hub) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/mcp/worker".equals(path)) {
                handleWorker(ex, token, hub);
                return;
            }
            if (!"/mcp".equals(path)) { reply(ex, 404, null); return; }
            if (ex.getRequestHeaders().getFirst("Origin") != null) { reply(ex, 403, null); return; }
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                    auth.getBytes(StandardCharsets.UTF_8))) { reply(ex, 401, null); return; }
            if (!"POST".equals(ex.getRequestMethod())) { ex.getResponseHeaders().set("Allow", "POST"); reply(ex, 405, null); return; }
            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                reply(ex, 415, null); return;
            }
            String body = readBody(ex);
            if (body == null) return;
            if (hub != null && backend != null) hub.refreshLocal(backend.clientInfo());
            JsonObject response = protocol.receive(body);
            reply(ex, response == null ? 202 : 200, response);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("MCP request failed", e);
            reply(ex, 500, object("error", "Internal transport error"));
        } finally { ex.close(); }
    }
    private static void handleWorker(HttpExchange ex, String token, McpClientHub hub) throws IOException {
        if (hub == null) { reply(ex, 404, null); return; }
        if (ex.getRequestHeaders().getFirst("Origin") != null) { reply(ex, 403, null); return; }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                auth.getBytes(StandardCharsets.UTF_8))) { reply(ex, 401, null); return; }
        if (!"POST".equals(ex.getRequestMethod())) { ex.getResponseHeaders().set("Allow", "POST"); reply(ex, 405, null); return; }
        try {
            String raw = readBody(ex);
            if (raw == null) return;
            JsonObject body = new JsonParser().parse(raw).getAsJsonObject();
            reply(ex, 200, hub.worker(body));
        } catch (Exception e) {
            reply(ex, 400, object("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
    private static String readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = ex.getRequestBody().read(buffer)) != -1) {
            if (out.size() + count > MAX_BODY) { reply(ex, 413, null); return null; }
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private static void reply(HttpExchange ex, int status, JsonObject body) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (body == null) { ex.sendResponseHeaders(status, -1); return; }
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }
}
