package com.mythos.mythosScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/**
 * Optional bridge into Mythos-Nexus. The Nexus jar may or may not be present —
 * every lookup is reflective so MythosScript still loads standalone.
 *
 * Exposes:
 *   - present()                        → is the coremod loaded?
 *   - sessions()                       → JsonArray of {username,state,mounted}
 *   - resolve(name)                    → BotSession for a username, or null
 *   - withContext(session, runnable)   → run inside that session's context
 *   - mountedUsername()                → name of the currently mounted session
 */
public final class NexusSessions {
    private NexusSessions() {}

    private static Class<?> hooks;       // com.mythos.nexus.core.NexusHooks
    private static Class<?> mgrCls;      // com.mythos.nexus.session.SessionManager
    private static Method mgrGet, mgrSessions, mgrMounted, mgrHuman, hooksInContext;
    private static Method botMountedM, sessionWithContextM, swapWithStaticsM, onlineSecondsM;
    private static Method runOnTickM;
    private static Class<?> botSessionCls, stateSwapCls;
    private static volatile boolean inited;

    // NexusCtx.current() resolved exactly once: boundSession() sits on
    // per-block / per-entity call sites, where a Class.forName + getMethod +
    // invoke per call dominated long frames (measured: 65% of Client-thread
    // leaf samples inside blink pathing). Cached Method.invoke is ~10ns.
    private static volatile Method ctxCurrentM;
    // BotSession's hottest fields (player/world/username) resolved once so the
    // bound* accessors skip the per-class map.
    private static volatile java.lang.reflect.Field playerF, worldF, usernameF;

    // Per-class field caches: BotSession's fields are fixed once loaded, so a
    // one-time lookup replaces the per-tick getField() scan. Nested map
    // (Class -> name -> Field) avoids the string-concat key allocation on
    // every call.
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.Map<String, Object>> FIELDS_BY_CLASS
            = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.lang.Object FIELD_MISS = new java.lang.Object(); // sentinel for absent field

    /** Lock-free fast path: once resolved, every caller only pays a volatile
     *  read. The synchronized monitor used to be entered on EVERY boundSession
     *  call site — under 4 bot threads + the client thread that alone cost
     *  microseconds per acquisition. */
    private static boolean init() {
        if (inited) return hooks != null;
        return initSlow();
    }

    private static synchronized boolean initSlow() {
        if (inited) return hooks != null;
        try {
            hooks  = Class.forName("com.mythos.nexus.core.NexusHooks");
            mgrCls = Class.forName("com.mythos.nexus.session.SessionManager");
            botSessionCls = Class.forName("com.mythos.nexus.session.BotSession");
            stateSwapCls  = Class.forName("com.mythos.nexus.session.MythosStateSwap");
            mgrGet      = mgrCls.getMethod("get");
            mgrSessions = mgrCls.getMethod("sessions");
            mgrMounted  = mgrCls.getMethod("mounted");
            mgrHuman    = mgrCls.getMethod("humanSession");
            botMountedM = mgrCls.getMethod("botMounted");
            hooksInContext = hooks.getMethod("inContext");
            sessionWithContextM = botSessionCls.getMethod("withContext", Runnable.class);
            try {
                runOnTickM = botSessionCls.getMethod("runOnTick", Runnable.class);
            } catch (Throwable ignore) { runOnTickM = null; }
            swapWithStaticsM    = stateSwapCls.getMethod("withStatics", botSessionCls, Runnable.class);
            onlineSecondsM      = botSessionCls.getMethod("onlineSeconds");
        } catch (Throwable t) {
            hooks = null; mgrCls = null;
            mgrGet = mgrSessions = mgrMounted = mgrHuman = hooksInContext = null;
            botMountedM = sessionWithContextM = swapWithStaticsM = onlineSecondsM = null;
            botSessionCls = stateSwapCls = null;
        }
        // Resolve the hot-path handles here, once. boundSession() and the
        // bound* accessors must never touch a monitor or a map lookup again.
        if (hooks != null) {
            try { ctxCurrentM = Class.forName("com.mythos.nexus.session.NexusCtx").getMethod("current"); }
            catch (Throwable t) { ctxCurrentM = null; }
            try { playerF   = botSessionCls.getField("player"); }   catch (Throwable t) { playerF = null; }
            try { worldF    = botSessionCls.getField("world"); }    catch (Throwable t) { worldF = null; }
            try { usernameF = botSessionCls.getField("username"); } catch (Throwable t) { usernameF = null; }
        }
        inited = true;
        return hooks != null;
    }

    public static boolean present() { return init(); }

    /** All sessions (bots + human). Empty JsonArray when Nexus absent. */
    public static JsonArray sessions() {
        JsonArray out = new JsonArray();
        if (!init()) return out;
        try {
            Object mgr = mgrGet.invoke(null);
            Object mounted = mgrMounted.invoke(mgr);
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) mgrSessions.invoke(mgr);
            for (Object s : list) {
                JsonObject row = new JsonObject();
                row.addProperty("username", String.valueOf(field(s, "username")));
                row.addProperty("state",    String.valueOf(field(s, "state")));
                row.addProperty("mounted",  s == mounted);
                row.addProperty("inWorld",  field(s, "world") != null);
                // Live stats for monitoring — bytes, online time, position,
                // health. All reflective: fields are public on BotSession.
                try { row.addProperty("bytesRx",   ((Number) field(s, "bytesReceived")).longValue()); } catch (Throwable t) {}
                try { row.addProperty("bytesTx",   ((Number) field(s, "bytesSent")).longValue()); } catch (Throwable t) {}
                try { row.addProperty("onlineSec", ((Number) onlineSecondsM.invoke(s)).longValue()); } catch (Throwable t) {}
                net.minecraft.client.entity.EntityPlayerSP p = player(s);
                if (p != null) {
                    row.addProperty("phash", Integer.toHexString(System.identityHashCode(p)));
                    row.addProperty("x", p.posX);
                    row.addProperty("y", p.posY);
                    row.addProperty("z", p.posZ);
                    row.addProperty("hp", p.getHealth());
                    row.addProperty("food", p.getFoodStats().getFoodLevel());
                }
                // Chat backlog — last 20 lines per session.
                try {
                    Object cl = field(s, "chatLog");
                    if (cl instanceof java.util.Deque) {
                        JsonArray chat = new JsonArray();
                        int n = 0;
                        for (Object line : (java.util.Deque<?>) cl) {
                            if (++n > 20) break;
                            chat.add(String.valueOf(line));
                        }
                        if (chat.size() > 0) row.add("chat", chat);
                    }
                } catch (Throwable t) {}
                out.add(row);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Session whose username matches (case-insensitive), else null. */
    public static Object resolve(String username) {
        if (!init() || username == null) return null;
        try {
            Object mgr = mgrGet.invoke(null);
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) mgrSessions.invoke(mgr);
            for (Object s : list) {
                Object u = field(s, "username");
                if (u != null && String.valueOf(u).equalsIgnoreCase(username)) return s;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Resolve the Nexus session that owns a concrete client player. The
     * human pseudo-session's display username is populated from the Minecraft
     * session and can briefly lag the player object, so identity is the
     * authoritative lookup whenever it is available.
     */
    public static Object resolveForPlayer(net.minecraft.client.entity.EntityPlayerSP player) {
        if (!init() || player == null) return null;
        try {
            Object mgr = mgrGet.invoke(null);
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) mgrSessions.invoke(mgr);
            for (Object s : list) {
                if (player(s) == player) return s;
            }
        } catch (Throwable ignored) {}
        return resolve(player.getName());
    }

    /** The FOREGROUND player — mounted session's pinned player when a bot is
     *  mounted, else the human pseudo-session's pinned player (the real live
     *  EntityPlayerSP the keyboard drives). Null when Nexus absent. */
    public static net.minecraft.client.entity.EntityPlayerSP foregroundPlayer() {
        if (!init()) return null;
        try {
            Object mgr = mgrGet.invoke(null);
            Object mounted = mgrMounted.invoke(mgr);
            Object target = mounted != null ? mounted : mgrHuman.invoke(mgr);
            return player(target);
        } catch (Throwable ignored) { return null; }
    }

    /** Name of the mounted session ("<you>" for the human), or null. */
    public static String mountedUsername() {
        if (!init()) return null;
        try {
            Object mounted = mgrMounted.invoke(mgrGet.invoke(null));
            Object u = mounted == null ? null : field(mounted, "username");
            return u == null ? null : String.valueOf(u);
        } catch (Throwable ignored) { return null; }
    }

    /** Username of the mounted session when it is a BOT (not the human),
     *  else null. Scripts started while a bot is mounted bind to it. */
    public static String mountedBotUsername() {
        if (!init()) return null;
        try {
            Object mgr = mgrGet.invoke(null);
            if (!Boolean.TRUE.equals(botMountedM.invoke(mgr))) return null;
            Object mounted = mgrMounted.invoke(mgr);
            Object u = mounted == null ? null : field(mounted, "username");
            return u == null ? null : String.valueOf(u);
        } catch (Throwable ignored) { return null; }
    }

    /** True when the given session object is the human pseudo-session. */
    private static boolean isHuman(Object session) {
        if (session == null) return false;
        try {
            Object human = mgrHuman.invoke(mgrGet.invoke(null));
            return session == human;
        } catch (Throwable t) { return false; }
    }

    /** The session's pinned player (its own EntityPlayerSP), or null. */
    public static net.minecraft.client.entity.EntityPlayerSP player(Object session) {
        if (session == null) return null;
        java.lang.reflect.Field f = playerF;
        if (f != null) {
            try {
                Object v = f.get(session);
                return v instanceof net.minecraft.client.entity.EntityPlayerSP
                        ? (net.minecraft.client.entity.EntityPlayerSP) v : null;
            } catch (Throwable ignored) { }
        }
        try {
            Object v = field(session, "player");
            return v instanceof net.minecraft.client.entity.EntityPlayerSP
                    ? (net.minecraft.client.entity.EntityPlayerSP) v : null;
        } catch (Throwable t) { return null; }
    }

    /** The session's pinned world (its own WorldClient), or null. */
    public static net.minecraft.world.World world(Object session) {
        if (session == null) return null;
        java.lang.reflect.Field f = worldF;
        if (f != null) {
            try {
                Object v = f.get(session);
                return v instanceof net.minecraft.world.World
                        ? (net.minecraft.world.World) v : null;
            } catch (Throwable ignored) { }
        }
        try {
            Object v = field(session, "world");
            return v instanceof net.minecraft.world.World
                    ? (net.minecraft.world.World) v : null;
        } catch (Throwable t) { return null; }
    }

    /** The session's username through the pinned Field. BotSession.username is
     *  fixed at creation, so this is a plain field read instead of two map
     *  lookups — currentTickOwner() runs once per script runner per tick. */
    private static Object username(Object session) {
        if (session == null) return null;
        java.lang.reflect.Field f = usernameF;
        if (f != null && f.getDeclaringClass().isInstance(session)) {
            try { return f.get(session); } catch (Throwable ignored) { }
        }
        try { return field(session, "username"); } catch (Throwable t) { return null; }
    }

    /**
     * The username of the bot whose player the current tick/action is running
     * for, or "" for the human / standalone case (Nexus absent, or no bot
     * active).  Used to bind a script to its owner and to let each runner
     * tick only for its own bot.
     */
    public static String currentTickOwner() {
        if (!init()) return "";
        try {
            // A dedicated bot tick thread is permanently bound via
            // NexusCtx.bind() — that binding is the authoritative owner for the
            // whole tick (BotTicker does NOT call NexusHooks.enterContext, so
            // inContext() is null on its thread). Consult the binding first so
            // a bot thread resolves its OWN username, never the mounted one.
            Object bound = boundSession();
            Object s;
            if (bound != null) {
                s = bound;
            } else {
                s = hooksInContext.invoke(null);
                if (s == null) {
                    Object mgr = mgrGet.invoke(null);
                    if (!Boolean.TRUE.equals(botMountedM.invoke(mgr))) return "";
                    s = mgrMounted.invoke(mgr);
                }
            }
            if (s == null || isHuman(s)) return "";
            Object u = username(s);
            return u == null ? "" : String.valueOf(u);
        } catch (Throwable t) {
            return "";
        }
    }


    /**
     * Username of the session currently dispatching on this thread (the
     * NexusHooks.inContext() session), or null when running on the client
     * thread / with Nexus absent. This is how a script started inside a
     * bot's withContext learns which bot owns it.
     */
    public static String currentUsername() {
        if (!init()) return null;
        try {
            // A dedicated bot thread's bound session is authoritative for the
            // whole tick — inContext() is only set inside withContext, which
            // BotTicker's own loop (and drainTasks) never calls. Without this
            // a runner injected via runOnTick binds its owner to "" instead
            // of its bot.
            Object ctx = boundSession();
            if (ctx == null) {
                if (hooksInContext == null) return null;
                ctx = hooksInContext.invoke(null);
            }
            if (ctx == null) return null;
            Object u = username(ctx);
            return u == null ? null : String.valueOf(u);
        } catch (Throwable ignored) { return null; }
    }

    /** True when the session currently dispatching on this thread is the
     *  mounted one (or the thread is outside any session context). */
    /**
     * The raw BotSession bound to the calling thread (NexusCtx.current()),
     * or null on the client thread / a packet thread with no bound session.
     * This is DIFFERENT from NexusHooks.inContext() — inContext is only set
     * while a session is actively dispatching inside withContext, whereas a
     * dedicated bot tick thread (BotTicker) is permanently bound via
     * NexusCtx.bind() for its whole life. Script handlers that read
     * mc.player/mc.world directly (they are not in the transformer's
     * FIELD_CTX_CLASSES rewrite list) must resolve their player through
     * {@link #boundPlayer()} so a bot thread sees ITS OWN entity.
     */
    public static Object boundSession() {
        Method m = ctxCurrentM;
        if (m == null && !init()) return null;
        m = ctxCurrentM;
        if (m == null) return null;
        try { return m.invoke(null); } catch (Throwable t) { return null; }
    }

    /**
     * The player this call site should operate on. On a dedicated bot tick
     * thread that's the bound session's own EntityPlayerSP; everywhere else
     * (client thread, mounted player, Nexus absent) it's the real
     * mc.player. Drop-in replacement for `Minecraft.getMinecraft().player`
     * in any code path a bot's tick thread can reach.
     */
    public static net.minecraft.client.entity.EntityPlayerSP boundPlayer() {
        Object s = boundSession();
        if (s == null)
            return net.minecraft.client.Minecraft.getMinecraft().player;
        net.minecraft.client.entity.EntityPlayerSP p = player(s);
        return p != null ? p
                : net.minecraft.client.Minecraft.getMinecraft().player;
    }

    /**
     * The world this call site should operate on — the bound bot's own
     * WorldClient on its tick thread, else the real mc.world. Drop-in for
     * `Minecraft.getMinecraft().world`.
     */
    public static net.minecraft.world.World boundWorld() {
        Object s = boundSession();
        if (s == null)
            return net.minecraft.client.Minecraft.getMinecraft().world;
        net.minecraft.world.World w = world(s);
        return w != null ? w
                : net.minecraft.client.Minecraft.getMinecraft().world;
    }

    /**
     * The player controller for the bound session — its own PlayerControllerMP
     * on a bot thread, else mc.playerController.
     */
    public static net.minecraft.client.multiplayer.PlayerControllerMP boundController() {
        Object s = boundSession();
        if (s == null)
            return net.minecraft.client.Minecraft.getMinecraft().playerController;
        try {
            Object v = field(s, "playerController");
            if (v instanceof net.minecraft.client.multiplayer.PlayerControllerMP)
                return (net.minecraft.client.multiplayer.PlayerControllerMP) v;
        } catch (Throwable ignored) {}
        return net.minecraft.client.Minecraft.getMinecraft().playerController;
    }

    /**
     * The screen for the bound session — its own savedScreen on a bot thread,
     * else mc.currentScreen.
     */
    public static net.minecraft.client.gui.GuiScreen boundScreen() {
        Object s = boundSession();
        if (s == null)
            return net.minecraft.client.Minecraft.getMinecraft().currentScreen;
        try {
            Object v = field(s, "savedScreen");
            if (v == null || v instanceof net.minecraft.client.gui.GuiScreen)
                return (net.minecraft.client.gui.GuiScreen) v;
        } catch (Throwable ignored) {}
        return net.minecraft.client.Minecraft.getMinecraft().currentScreen;
    }


    /** Run r inside the given session's context AND with its MythosScript
     *  statics live (no-op when Nexus absent). The statics swap is scoped —
     *  previous live statics are restored on exit. */
    public static void withContext(Object session, Runnable r) {
        if (session == null) { r.run(); return; }
        try {
            // Wrap the session-context runnable inside a statics swap so
            // per-bot state (kill aura, features, etc.) is what the tool sees.
            Runnable body = (Runnable) () -> withStatics(session, r);
            // Run the tool on the session's OWN tick thread when it has a live
            // background ticker. Executing a command (e.g. Baritone goto) on an
            // MCP pool thread mutated PathingControlManager.processes while the
            // bot's tick iterated them -> ConcurrentModificationException.
            // runOnTick serializes the call onto the tick thread; for mounted/
            // human sessions (no ticker) it falls back to inline withContext on
            // the client thread — already correct.
            if (runOnTickM != null) {
                java.util.concurrent.Future<?> fut =
                        (java.util.concurrent.Future<?>) runOnTickM.invoke(session, body);
                if (fut != null) {
                    try { fut.get(10, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (java.util.concurrent.TimeoutException te) {
                        throw new RuntimeException("session tick thread did not run task within 10s", te);
                    }
                    return;
                }
            }
            sessionWithContextM.invoke(session, body);
        } catch (Throwable t) {
            r.run(); // fall back to mounted context rather than drop the call
        }
    }

    private static void withStatics(Object session, Runnable r) {
        try {
            swapWithStaticsM.invoke(null, session, r);
        } catch (Throwable t) {
            r.run();
        }
    }

    private static Object field(Object o, String name) throws Exception {
        Class<?> cls = o.getClass();
        // Per-class nested map: avoids the string-concat key allocation on
        // every call (30 bots × ~10 field reads/tick was showing up in nps).
        java.util.Map<String, Object> byName = FIELDS_BY_CLASS.get(cls);
        if (byName == null) {
            java.util.Map<String, Object> fresh = new java.util.HashMap<>();
            java.util.Map<String, Object> prev = FIELDS_BY_CLASS.putIfAbsent(cls, fresh);
            byName = prev != null ? prev : fresh;
        }
        Object cached = byName.get(name);
        if (cached == null) {
            try {
                java.lang.reflect.Field fld = cls.getField(name);
                fld.setAccessible(true);
                cached = fld;
            } catch (Exception e) {
                cached = FIELD_MISS;
            }
            Object prev = byName.putIfAbsent(name, cached);
            if (prev != null) cached = prev;
        }
        return cached == FIELD_MISS ? null : ((java.lang.reflect.Field) cached).get(o);
    }
}
