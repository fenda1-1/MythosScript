package com.mythos.mythosScriptMod.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.math.RayTraceResult;

/**
 * Thread/session-aware replacements for the Minecraft singleton fields.
 *
 * {@link SessionFieldTransformer} rewrites every GETFIELD/PUTFIELD of
 * Minecraft.player / world / playerController / currentScreen /
 * objectMouseOver, GameSettings.thirdPersonView and every
 * INVOKEVIRTUAL Minecraft.getConnection() inside MythosScript classes into
 * INVOKESTATIC calls here. The receiver stays on the stack, so the rewritten
 * call site has exactly the stack shape of the original instruction.
 *
 * Resolution mirrors Mythos-Nexus' NexusCtx:
 *   - On a Nexus bot tick thread (NexusCtx.current() != null) the values come
 *     from that thread's own BotSession. A bot thread NEVER sees the mounted
 *     player/world/connection, so it can never mutate them concurrently with
 *     the client thread.
 *   - Everywhere else (client thread, Netty, worker pools, Nexus absent) the
 *     real Minecraft field is returned unchanged.
 *
 * This class lives in the excluded .mixin package: the field reads below are
 * the real fields and must never be rewritten (that would recurse).
 */
public final class SessionFieldAccess {

    private SessionFieldAccess() {}

    /** NexusCtx.current() as ()Object, or null when Nexus is absent. */
    private static final MethodHandle CURRENT;
    /** BotSession public volatile field getters/setters, (Object)Object / (Object,Object)void. */
    private static final MethodHandle S_PLAYER, S_WORLD, S_CONTROLLER, S_SCREEN, S_HANDLER;
    private static final MethodHandle SET_PLAYER, SET_WORLD, SET_CONTROLLER, SET_SCREEN;
    /** savedThirdPersonView as (Object)int / (Object,int)void. */
    private static final MethodHandle S_THIRD_PERSON, SET_THIRD_PERSON;

    static {
        MethodHandle current = null;
        MethodHandle sPlayer = null, sWorld = null, sController = null, sScreen = null, sHandler = null;
        MethodHandle setPlayer = null, setWorld = null, setController = null, setScreen = null;
        MethodHandle sThird = null, setThird = null;
        try {
            ClassLoader loader = SessionFieldAccess.class.getClassLoader();
            Class<?> ctx = Class.forName("com.mythos.nexus.session.NexusCtx", true, loader);
            Class<?> session = Class.forName("com.mythos.nexus.session.BotSession", true, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType objGetter = MethodType.methodType(Object.class, Object.class);
            MethodType objSetter = MethodType.methodType(void.class, Object.class, Object.class);

            sPlayer = getter(lookup, session, "player", objGetter);
            sWorld = getter(lookup, session, "world", objGetter);
            sController = getter(lookup, session, "playerController", objGetter);
            sScreen = getter(lookup, session, "savedScreen", objGetter);
            sHandler = getter(lookup, session, "handler", objGetter);
            setPlayer = setter(lookup, session, "player", objSetter);
            setWorld = setter(lookup, session, "world", objSetter);
            setController = setter(lookup, session, "playerController", objSetter);
            setScreen = setter(lookup, session, "savedScreen", objSetter);
            sThird = getter(lookup, session, "savedThirdPersonView",
                    MethodType.methodType(int.class, Object.class));
            setThird = setter(lookup, session, "savedThirdPersonView",
                    MethodType.methodType(void.class, Object.class, int.class));

            // Resolve current() last: if any BotSession field is missing the
            // whole bridge stays disabled instead of half-working.
            current = lookup.findStatic(ctx, "current", MethodType.methodType(session))
                    .asType(MethodType.methodType(Object.class));
        } catch (Throwable ignored) {
            // Nexus absent (single player / no multibox) or incompatible:
            // every accessor below degrades to the real Minecraft field.
            current = null;
        }
        CURRENT = current;
        S_PLAYER = sPlayer;
        S_WORLD = sWorld;
        S_CONTROLLER = sController;
        S_SCREEN = sScreen;
        S_HANDLER = sHandler;
        SET_PLAYER = setPlayer;
        SET_WORLD = setWorld;
        SET_CONTROLLER = setController;
        SET_SCREEN = setScreen;
        S_THIRD_PERSON = sThird;
        SET_THIRD_PERSON = setThird;
    }

    private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String name,
            MethodType type) throws ReflectiveOperationException {
        Field f = owner.getField(name);
        return lookup.unreflectGetter(f).asType(type);
    }

    private static MethodHandle setter(MethodHandles.Lookup lookup, Class<?> owner, String name,
            MethodType type) throws ReflectiveOperationException {
        Field f = owner.getField(name);
        return lookup.unreflectSetter(f).asType(type);
    }

    /** True when Mythos-Nexus' NexusCtx was found and the session bridge is live. */
    public static boolean nexusPresent() {
        return CURRENT != null;
    }

    /**
     * The BotSession bound to the calling thread, or null on the client thread,
     * on any unbound worker thread, and whenever Nexus is absent.
     */
    public static Object boundSession() {
        MethodHandle h = CURRENT;
        if (h == null) {
            return null;
        }
        try {
            return (Object) h.invokeExact();
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when the calling thread is a Nexus bot tick thread. */
    public static boolean onBotThread() {
        return boundSession() != null;
    }

    private static Object read(MethodHandle h, Object session) {
        try {
            return (Object) h.invokeExact(session);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void write(MethodHandle h, Object session, Object value) {
        try {
            h.invokeExact(session, value);
        } catch (Throwable ignored) {
            // A failed write is dropped; it must never land on the real field.
        }
    }

    /**
     * True when {@code player} is the player owned by the calling thread: the
     * bound session's player on a bot thread, the real mc.player elsewhere.
     * Code that mutates player state uses this as a last guard, so even a call
     * path that escaped the rewrite cannot touch another session's entity.
     */
    public static boolean ownsPlayer(EntityPlayerSP player) {
        return player != null && player == getPlayer(Minecraft.getMinecraft());
    }

    // ---- Minecraft.player ------------------------------------------------

    public static EntityPlayerSP getPlayer(Minecraft mc) {
        Object s = boundSession();
        if (s == null) {
            return mc.player;
        }
        Object v = read(S_PLAYER, s);
        return v instanceof EntityPlayerSP ? (EntityPlayerSP) v : null;
    }

    public static void setPlayer(Minecraft mc, EntityPlayerSP value) {
        Object s = boundSession();
        if (s == null) {
            mc.player = value;
        } else {
            write(SET_PLAYER, s, value);
        }
    }

    // ---- Minecraft.world -------------------------------------------------

    public static WorldClient getWorld(Minecraft mc) {
        Object s = boundSession();
        if (s == null) {
            return mc.world;
        }
        Object v = read(S_WORLD, s);
        return v instanceof WorldClient ? (WorldClient) v : null;
    }

    public static void setWorld(Minecraft mc, WorldClient value) {
        Object s = boundSession();
        if (s == null) {
            mc.world = value;
        } else {
            write(SET_WORLD, s, value);
        }
    }

    // ---- Minecraft.playerController --------------------------------------

    public static PlayerControllerMP getPlayerController(Minecraft mc) {
        Object s = boundSession();
        if (s == null) {
            return mc.playerController;
        }
        Object v = read(S_CONTROLLER, s);
        return v instanceof PlayerControllerMP ? (PlayerControllerMP) v : null;
    }

    public static void setPlayerController(Minecraft mc, PlayerControllerMP value) {
        Object s = boundSession();
        if (s == null) {
            mc.playerController = value;
        } else {
            write(SET_CONTROLLER, s, value);
        }
    }

    // ---- Minecraft.currentScreen -----------------------------------------

    public static GuiScreen getCurrentScreen(Minecraft mc) {
        Object s = boundSession();
        if (s == null) {
            return mc.currentScreen;
        }
        Object v = read(S_SCREEN, s);
        return v instanceof GuiScreen ? (GuiScreen) v : null;
    }

    public static void setCurrentScreen(Minecraft mc, GuiScreen value) {
        Object s = boundSession();
        if (s == null) {
            mc.currentScreen = value;
        } else {
            write(SET_SCREEN, s, value);
        }
    }

    // ---- Minecraft.objectMouseOver ---------------------------------------

    /** The crosshair hit belongs to the mounted camera; a background bot has none. */
    public static RayTraceResult getObjectMouseOver(Minecraft mc) {
        return boundSession() == null ? mc.objectMouseOver : null;
    }

    /** Bot-thread writes are dropped: the shared crosshair is client-thread state. */
    public static void setObjectMouseOver(Minecraft mc, RayTraceResult value) {
        if (boundSession() == null) {
            mc.objectMouseOver = value;
        }
    }

    // ---- Minecraft.getConnection() ---------------------------------------

    /**
     * Replaces INVOKEVIRTUAL Minecraft.getConnection(). On a bot thread this is
     * the session's own NetHandlerPlayClient (or its player's connection), never
     * the mounted session's.
     */
    public static NetHandlerPlayClient getConnection(Minecraft mc) {
        Object s = boundSession();
        if (s == null) {
            return mc.getConnection();
        }
        Object v = read(S_HANDLER, s);
        if (v instanceof NetHandlerPlayClient) {
            return (NetHandlerPlayClient) v;
        }
        Object p = read(S_PLAYER, s);
        return p instanceof EntityPlayerSP ? ((EntityPlayerSP) p).connection : null;
    }

    // ---- GameSettings.thirdPersonView ------------------------------------

    public static int getThirdPersonView(GameSettings settings) {
        Object s = boundSession();
        if (s == null) {
            return settings.thirdPersonView;
        }
        try {
            return (int) S_THIRD_PERSON.invokeExact(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setThirdPersonView(GameSettings settings, int value) {
        Object s = boundSession();
        if (s == null) {
            settings.thirdPersonView = value;
            return;
        }
        try {
            SET_THIRD_PERSON.invokeExact(s, value);
        } catch (Throwable ignored) {
            // dropped; never touches the shared GameSettings from a bot thread
        }
    }
}
