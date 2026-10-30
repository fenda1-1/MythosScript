package com.mythos.mythosScriptMod.handlers;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import com.mythos.mythosScriptMod.shadowbaritone.api.event.events.type.EventState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * Bridge that replays MythosScript's per-tick logic inside each bot's
 * session context.  Registered by Mythos Nexus via reflection:
 *   NexusHooks.registerTickHook(NexusTickBridge::tickAll)
 *
 * All MythosScript handlers read mc.player/mc.world at call time —
 * pushing a different session's fields into the Minecraft singleton then
 * invoking the same tick methods runs that bot's features on that bot's
 * world, without any edits to the feature classes.
 *
 * Order is the same as vanilla client tick: PlayerTickEvent first (per
 * player), then ClientTickEvent (per session).
 */
public final class NexusTickBridge {

    private NexusTickBridge() {}

    /** Per-baritone "already logged this state" marker so the tick logger
     *  only prints when player identity / alive / inWorld actually flips —
     *  not every tick. Keyed on baritone (one per session). */
    private static final java.util.Map<Object, int[]> LAST_TICK_LOG =
            new java.util.WeakHashMap<>();

    // Cached baritone-tick handles — driveBaritoneTick ran ~12 reflective
    // Class.forName / getMethod / invoke calls per bot per tick (30 bots × 20tps).
    // All are stable once Baritone is on the classpath, so resolve them once.
    private static Class<?> tickEvCls, evStateCls, playerUpdCls;
    private static Method providerGetPrimaryBaritoneM, baritoneGetPlayerContextM,
            baritoneGetGameEventHandlerM, ctxPlayerM, createNextProviderM,
            handlerOnTickM, handlerOnPlayerUpdateM, handlerOnPostTickM;
    private static Constructor<?> playerUpdCtor;
    private static Object preEnum, postEnum, inWorldEnum, outWorldEnum;
    private static boolean btTried;

    private static synchronized boolean initBaritoneHandles() {
        if (btTried) return tickEvCls != null;
        btTried = true;
        try {
            tickEvCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.api.event.events.TickEvent");
            evStateCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.api.event.events.type.EventState");
            playerUpdCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.api.event.events.PlayerUpdateEvent");

            Class<?> providerCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.api.IBaritoneProvider");
            providerGetPrimaryBaritoneM = providerCls.getMethod("getPrimaryBaritone");
            Class<?> baritoneCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone");
            baritoneGetPlayerContextM = baritoneCls.getMethod("getPlayerContext");
            baritoneGetGameEventHandlerM = baritoneCls.getMethod("getGameEventHandler");
            Class<?> ctxCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.api.utils.IPlayerContext");
            ctxPlayerM = ctxCls.getMethod("player");

            createNextProviderM = tickEvCls.getMethod("createNextProvider");
            Class<?> handlerCls = Class.forName(
                    "com.mythos.mythosScriptMod.shadowbaritone.event.GameEventHandler");
            handlerOnTickM = handlerCls.getMethod("onTick", tickEvCls);
            handlerOnPlayerUpdateM = handlerCls.getMethod("onPlayerUpdate", playerUpdCls);
            handlerOnPostTickM = handlerCls.getMethod("onPostTick", tickEvCls);

            preEnum = Enum.valueOf((Class<Enum>) evStateCls, "PRE");
            postEnum = Enum.valueOf((Class<Enum>) evStateCls, "POST");
            inWorldEnum = Enum.valueOf((Class<Enum>)
                    Class.forName("com.mythos.mythosScriptMod.shadowbaritone.api.event.events.TickEvent$Type"), "IN");
            outWorldEnum = Enum.valueOf((Class<Enum>)
                    Class.forName("com.mythos.mythosScriptMod.shadowbaritone.api.event.events.TickEvent$Type"), "OUT");
        } catch (Throwable t) {
            tickEvCls = null;
        }
        return tickEvCls != null;
    }

    /**
     * Called by NexusHooks.runSessionTicks for each online, non-mounted
     * bot, inside that session's withContext.  We fire the same Forge
     * events the mounted session would get — but only to MythosScript's
     * own listeners (not the whole bus, which would re-run Forge internals
     * and other mods).
     */
    public static void tickAll() {
        // Resolve the thread-bound session's player — this class is NOT in
        // the field-rewrite list, so mc.player is the mounted player.  The
        // bound BotSession owns the real player for this tick thread.
        Object bound = boundSession();
        net.minecraft.client.entity.EntityPlayerSP boundPlayer = sessionPlayer(bound);
        // Forge emits both phases.  Background bots used to receive only END,
        // which skipped START-phase handlers such as conditional execution,
        // KillAura, movement and path-sequence listeners.
        TickEvent.PlayerTickEvent playerStart =
                new TickEvent.PlayerTickEvent(TickEvent.Phase.START, boundPlayer);
        TickEvent.PlayerTickEvent playerEnd =
                new TickEvent.PlayerTickEvent(TickEvent.Phase.END, boundPlayer);
        TickEvent.ClientTickEvent clientStart =
                new TickEvent.ClientTickEvent(TickEvent.Phase.START);
        TickEvent.ClientTickEvent clientEnd =
                new TickEvent.ClientTickEvent(TickEvent.Phase.END);

        // Iterate MythosScript's own registered listeners only.
        // The singletons are looked up by name — same classes registered
        // on MinecraftForge.EVENT_BUS in mythosScriptMod.init().
        // Skip when this session is the mounted one: the real Forge bus
        // already delivers PlayerTick/ClientTick for the mounted player —
        // firing here too would double-drive every feature.
        String mountedName = null;
        try { mountedName = com.mythos.mythosScriptMod.mcp.NexusSessions.mountedUsername(); }
        catch (Throwable ignored) {}
        String boundName = sessionUsername(bound);
        // "The mounted session" is really "the FOREGROUND owner" — the real
        // Forge bus already ticks it, so tickAll must not. When mounted==null
        // the human pseudo-session is the foreground: mountedUsername() is
        // null, so boundName.equals(mountedName) never matched and the human's
        // listeners fired here too — a non-primary baritone's
        // InputOverrideHandler then installed PlayerMovementInput on mc.player
        // and the real keyboard (WASD/jump) went dead. isForeground resolves
        // mounted==null -> human AND a mounted bot in one call.
        boolean isMounted = isForeground(bound);
        if (!isMounted) {
            dispatchForgePhase(bound, playerStart, clientStart);
            dispatchForgePhase(bound, playerEnd, clientEnd);
        }

        // Drive the per-owner script runners for this session. The mounted
        // session is driven by the real Forge bus; background bots are only
        // ticked here (inside withContext, so mc.player is this bot).
        try {
            net.minecraft.client.entity.EntityPlayerSP bridgePlayer = boundPlayer;
            if (!isForeground(bound) && bridgePlayer != null) {
                com.mythos.mythosScriptMod.path.PathSequenceEventListener
                        .dispatchTick(bridgePlayer);
            }
        } catch (Throwable t) {
            com.mythos.mythosScriptMod.mythosScriptMod.LOGGER
                    .warn("nexus script tick failed: {}", t.toString());
        }

        // Drive Baritone's own game-event bus the same way MixinMinecraft does
        // for the mounted session — but only on a bot thread.  When there is
        // no bound session (client thread / mounted), MixinMinecraft.runTick
        // already drives the mounted baritone; driving here would double-tick.
        if (bound != null && !isForeground(bound)) {
            driveBaritoneTick();
        }
    }

    private static void dispatchForgePhase(Object bound, TickEvent.PlayerTickEvent playerEvent,
            TickEvent.ClientTickEvent clientEvent) {
        for (Object l : mythosScriptModListeners()) {
            for (java.lang.reflect.Method m : l.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                boolean playerTick = p == TickEvent.PlayerTickEvent.class && playerEvent.player != null;
                if (!playerTick && p != TickEvent.ClientTickEvent.class) continue;
                // The session can be mounted while this tick is in flight; from
                // then on the client thread owns its player, so stop here.
                if (isForeground(bound)) return;
                try {
                    m.invoke(l, playerTick ? playerEvent : clientEvent);
                } catch (Throwable t) {
                    // a background bot's tick must never take the process down
                    logListenerFailure(l, m, t);
                }
            }
        }
    }

    /** Last log time per listener method + cause type; each pair logs at most once every 30s. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_FAILURE_LOG =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long FAILURE_LOG_INTERVAL_NS = 30_000_000_000L;

    private static void logListenerFailure(Object listener, java.lang.reflect.Method m, Throwable t) {
        Throwable cause = t;
        while (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String key = listener.getClass().getName() + "#" + m.getName() + "/" + cause.getClass().getName();
        long now = System.nanoTime();
        Long last = LAST_FAILURE_LOG.get(key);
        if (last != null && now - last < FAILURE_LOG_INTERVAL_NS) return;
        LAST_FAILURE_LOG.put(key, now);
        com.mythos.mythosScriptMod.mythosScriptMod.LOGGER.warn(
                "nexus tick listener {}.{} failed on {}", listener.getClass().getSimpleName(),
                m.getName(), Thread.currentThread().getName(), cause);
    }

    private static void driveBaritoneTick() {
        try {
            // Resolve the thread-bound session via NexusCtx.current().  This
            // class is NOT in the transformer's field-rewrite list, so
            // mc.player reads the *mounted* player — every bot thread would
            // otherwise collapse to the human's baritone (CME on its shared
            // PathingControlManager).  The bound BotSession owns the real
            // player/world/username for this thread.
            Object boundSession = boundSession();
            net.minecraft.client.entity.EntityPlayerSP sessPlayer = sessionPlayer(boundSession);
            net.minecraft.world.World sessWorld = sessionWorld(boundSession);
            String sessName = sessionUsername(boundSession);

            net.minecraft.client.Minecraft mc =
                    net.minecraft.client.Minecraft.getMinecraft();
            Object providerObj = com.mythos.mythosScriptMod.shadowbaritone
                    .api.BaritoneAPI.getProvider();
            Object baritone;
            String branch;
            if (sessName != null && providerObj instanceof com.mythos.mythosScriptMod.shadowbaritone.BaritoneProvider) {
                branch = "session";
                final Object session = boundSession;
                baritone = ((com.mythos.mythosScriptMod.shadowbaritone.BaritoneProvider) providerObj)
                        .sessionBaritone(sessName,
                                () -> sessionPlayer(session),
                                () -> sessionWorld(session));
                // Stash on the session so SessionTicker.hasActivePath can
                // query pathing state without re-doing the provider lookup.
                if (session != null && baritone != null) {
                    try {
                        session.getClass().getField("baritone").set(session, baritone);
                    } catch (Throwable ignored) {}
                }
            } else {
                branch = "primary";
                if (!initBaritoneHandles()) return;
                baritone = providerGetPrimaryBaritoneM.invoke(providerObj);
            }
            if (baritone == null) return;
            if (!initBaritoneHandles()) return;
            Object ctxObj = baritoneGetPlayerContextM.invoke(baritone);
            Object handler = baritoneGetGameEventHandlerM.invoke(baritone);
            if (handler == null) return;

            // One-shot per-baritone diagnostic: which branch fed it and does
            // the bound session actually own a live player.  Printed once per
            // baritone instance (identity) so a session that stops ticking its
            // baritone is still diagnosable from the single boot line.
            try {
                if (!LAST_TICK_LOG.containsKey(baritone) || LAST_TICK_LOG.get(baritone)[0] == -1) {
                    Class<?> nexusLog = Class.forName("com.mythos.nexus.core.NexusLog");
                    nexusLog.getMethod("baritone", String.class, Object[].class)
                            .invoke(null,
                                    String.format("drive sess=%s branch=%s boundSess=%s ctxPlayer=%s sessPlayer=%s",
                                            sessName, branch, boundSession == null ? "null" : boundSession.getClass().getSimpleName(),
                                            ctxObj == null ? "null" : ctxObj.getClass().getSimpleName(),
                                            sessPlayer == null ? "null" : "pos=" + sessPlayer.posX + "," + sessPlayer.posY + "," + sessPlayer.posZ),
                                    new Object[0]);
                }
            } catch (Throwable ignored) {}

            boolean inWorld = sessPlayer != null && sessWorld != null;
            // Only log transitions — player identity swap or alive flag flip.
            // Every-tick logging drowns the signal in 20 lines/s per session.
            try {
                Object pp = ctxPlayerM.invoke(ctxObj);
                net.minecraft.client.entity.EntityPlayerSP pl =
                        pp instanceof net.minecraft.client.entity.EntityPlayerSP
                                ? (net.minecraft.client.entity.EntityPlayerSP) pp : null;
                int pid = pl == null ? 0 : System.identityHashCode(pl);
                boolean alive = pl != null && pl.isEntityAlive();
                int[] last = LAST_TICK_LOG.get(baritone);
                if (last == null) {
                    last = new int[]{-1, -1, -1};
                    LAST_TICK_LOG.put(baritone, last);
                }
                int curAlive = alive ? 1 : 0;
                int curInWorld = inWorld ? 1 : 0;
                if (last[0] != pid || last[1] != curAlive || last[2] != curInWorld) {
                    last[0] = pid; last[1] = curAlive; last[2] = curInWorld;
                    Class<?> nexusLog = Class.forName("com.mythos.nexus.core.NexusLog");
                    nexusLog.getMethod("baritone", String.class, Object[].class)
                            .invoke(null,
                                    String.format(
                                            "tick %s player=%s alive=%s pos=%.1f,%.1f,%.1f",
                                            inWorld ? "IN" : "OUT",
                                            pl == null ? "null" : Integer.toHexString(pid),
                                            alive,
                                            pl == null ? 0 : pl.posX,
                                            pl == null ? 0 : pl.posY,
                                            pl == null ? 0 : pl.posZ),
                                    new Object[0]);
                }
            } catch (Throwable ignored) {}
            Object type = inWorld ? inWorldEnum : outWorldEnum;

            // Build the same BiFunction the mixin uses (increments tick count).
            java.util.function.BiFunction provider = (java.util.function.BiFunction)
                    createNextProviderM.invoke(null);
            Object preTick = provider.apply(preEnum, type);
            Object postTick = provider.apply(postEnum, type);

            handlerOnTickM.invoke(handler, preTick);
            if (playerUpdCtor == null) {
                playerUpdCtor = playerUpdCls.getDeclaredConstructor(evStateCls);
                playerUpdCtor.setAccessible(true);
            }
            handlerOnPlayerUpdateM.invoke(handler, playerUpdCtor.newInstance(preEnum));
            handlerOnPlayerUpdateM.invoke(handler, playerUpdCtor.newInstance(postEnum));
            handlerOnPostTickM.invoke(handler, postTick);
        } catch (Throwable t) {
            com.mythos.mythosScriptMod.mythosScriptMod.LOGGER
                    .warn("nexus baritone tick failed: {}", t.toString());
        }
    }

    /** The BotSession bound to the calling thread, via NexusCtx.current().
     *  Reflective because MythosScript and Mythos-Nexus are separate jars. */
    private static Object boundSession() {
        try {
            Class<?> c = Class.forName("com.mythos.nexus.session.NexusCtx");
            return c.getMethod("current").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** SessionManager.isForeground(session) via reflection — the given
     *  BotSession is the mounted bot, or the human pseudo-session while
     *  nothing is mounted. Foreground sessions are ticked by the real Forge
     *  bus (and MixinMinecraft for baritone); tickAll must skip them. */
    private static volatile Method mgrGetM, mgrIsForegroundM;

    private static boolean isForeground(Object session) {
        if (session == null) return true; // no bound session = client thread = foreground
        try {
            Method get = mgrGetM, isFg = mgrIsForegroundM;
            if (get == null || isFg == null) {
                Class<?> c = Class.forName("com.mythos.nexus.session.SessionManager");
                isFg = c.getMethod("isForeground",
                        Class.forName("com.mythos.nexus.session.BotSession"));
                get = c.getMethod("get");
                mgrIsForegroundM = isFg;
                mgrGetM = get;
            }
            Object r = isFg.invoke(get.invoke(null), session);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String sessionUsername(Object session) {
        if (session == null) return null;
        try {
            Object v = session.getClass().getField("username").get(session);
            return v instanceof String ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static net.minecraft.client.entity.EntityPlayerSP sessionPlayer(Object session) {
        if (session == null) return net.minecraft.client.Minecraft.getMinecraft().player;
        try {
            Object v = session.getClass().getField("player").get(session);
            return v instanceof net.minecraft.client.entity.EntityPlayerSP
                    ? (net.minecraft.client.entity.EntityPlayerSP) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static net.minecraft.world.World sessionWorld(Object session) {
        if (session == null) return net.minecraft.client.Minecraft.getMinecraft().world;
        try {
            Object v = session.getClass().getField("world").get(session);
            return v instanceof net.minecraft.world.World ? (net.minecraft.world.World) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The listener instances registered by mythosScriptMod.init(). */
    private static java.util.List<Object> mythosScriptModListeners() {
        // Reflection-free: the mod class exposes them via a public accessor
        // (added in this patch).  Fall back to the event bus if absent.
        try {
            java.lang.reflect.Method m =
                    com.mythos.mythosScriptMod.mythosScriptMod.class
                            .getMethod("nexusListeners");
            Object v = m.invoke(null);
            if (v instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> l = (java.util.List<Object>) v;
                return l;
            }
        } catch (Throwable ignored) {}
        return java.util.Collections.emptyList();
    }
}
