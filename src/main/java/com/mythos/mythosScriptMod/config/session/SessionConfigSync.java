package com.mythos.mythosScriptMod.config.session;

import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.mcp.NexusSessions;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.system.ProfileManager;

import java.lang.reflect.Method;

/**
 * 前台会话切换时把该会话自己的配置文件灌进各 handler 的静态配置字段。
 *
 * <p>静态字段只有一份，而配置读点遍布全 mod；每次读写都改造成 per-owner 取值的代价太大，
 * 所以采用「按会话重载」：挂载的会话变了，就用新会话的配置目录重载一遍静态字段，
 * 再把结果推进该会话在 Nexus {@code SESSION_STASH} 里的槽位（见
 * {@link SessionConfigBinder}），这样 Nexus 的 mount/unmount 快照不会把它换成别人的值。</p>
 */
public final class SessionConfigSync {

    /** null = 尚未建立基线（启动时已经加载过一次，首帧不当成切换）。 */
    private static String lastOwner;
    /** 每完成一次会话重载加一；界面拿它判断静态字段是否已换成别的会话，比逐帧比较 owner 更不容易误判挂载中途的状态。 */
    private static volatile int generation;

    private SessionConfigSync() {
    }

    /** 客户端线程每 tick 末尾调用；会话自己的 tick 线程要跳过，否则 owner 会在几个 bot 之间每 tick 翻转。 */
    public static void tick() {
        // currentTickOwner() 在客户端线程上也非 null（人类是 ""、挂载 bot 时是 bot 名），
        // 用它判断会让这里永远提前返回、切换会话从不重载配置；只有 bot 线程或会话分发上下文里
        // currentUsername() 才非 null。
        if (NexusSessions.currentUsername() != null) {
            return;
        }
        String owner = currentForegroundOwner();
        if (lastOwner == null) {
            lastOwner = owner;
            return;
        }
        if (owner.equals(lastOwner)) {
            return;
        }
        lastOwner = owner;
        reloadFor(owner);
    }

    private static void markReloaded() {
        generation++;
    }

    /** 静态配置字段当前装的是哪个会话的配置；还没发生过切换时就是前台会话。 */
    public static String loadedOwner() {
        String owner = lastOwner;
        return owner == null ? currentForegroundOwner() : owner;
    }

    public static int generation() {
        return generation;
    }

    /** 前台会话 = 当前被挂载观看的那个会话；没有挂载时是人类自己。 */
    public static String currentForegroundOwner() {
        String mounted = NexusSessions.mountedUsername();
        return mounted == null ? "" : mounted;
    }

    /**
     * 用 owner 自己的配置目录重载静态配置字段，然后把 live 值推进该会话的 stash。
     * 目录以 owner 命名，所以重载期间必须显式指定 owner —— 此时线程上下文里的
     * owner 还是切换前的那个。
     */
    public static void reloadFor(String owner) {
        String label = owner.isEmpty() ? "<human>" : owner;
        try {
            ProfileManager.withConfigOwner(owner, () -> {
                ModConfig.loadSessionConfigs();
                mythosScriptMod.LOGGER.info("Session config switched to {} ({})",
                        label, ProfileManager.getCurrentConfigDir().toAbsolutePath());
            });
            pushLiveIntoSessionStash();
            markReloaded();
        } catch (Throwable t) {
            // 重载失败时静态字段可能已经半换，也要让界面重新对齐
            markReloaded();
            mythosScriptMod.LOGGER.error("Failed to switch session config to " + label, t);
        }
    }

    /**
     * {@code swapTo(session, session)} 会把 live 的静态值写进该 session 的 stash 再读回来，
     * 相当于「以 live 为准刷新该会话的快照」。不能走 {@code withStatics}：目标等于
     * live 会话时它会直接短路，不写 stash。
     */
    private static void pushLiveIntoSessionStash() {
        try {
            Object session = NexusSessions.resolveForPlayer(NexusSessions.foregroundPlayer());
            if (session == null) {
                return;
            }
            Class<?> sessionType = session.getClass();
            Method swapTo = Class.forName("com.mythos.nexus.session.MythosStateSwap")
                    .getMethod("swapTo", sessionType, sessionType);
            swapTo.invoke(null, session, session);
        } catch (ClassNotFoundException e) {
            // 单客户端：没有 Nexus 就没有 stash，静态字段本身就是唯一副本
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.warn("Failed to refresh session config stash", t);
        }
    }
}
