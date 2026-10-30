package com.mythos.mythosScriptMod.handlers;

/** Monotonic owner-local action gate. Re-entrant ticks cannot burst a whole route. */
final class KillAuraTeleportRouteClock {
    private static final long STALE_REFILL_NANOS = 40_000_000L;

    /** 一个 bot tick 里允许推进几个动作，由操作者在 GUI 里给。
     *
     *  原来按墙上时间限流（45ms → 25ms），但 bot 线程本身就是 50ms 一拍，
     *  一次调用里连调两次 tryAdvance，第二次必然落在同一个 50ms 窗口内被挡掉，
     *  所以「闸门设多小」从来没生效过，实际永远是「一拍一个动作」。
     *
     *  额度也不能由时钟自己写死：它必须和「一个动作挪几格」一起算，
     *  因为服务端 NetHandlerPlayServer.processPlayer 用 `d11 - d10 > 100 * i`
     *  判 moved too quickly，d11 是从本 tick 起点算的累积位移平方，i 是本 tick
     *  收到的移动包数（i > 5 会被强制按 1 算，阈值跌回 100 = 10 格）。
     *  客户端 bot tick 和服务端 tick 一旦错相，两拍的包会挤进同一个服务端 tick，
     *  所以安全线是「一拍累计位移不超过 10 格」：动作数 × 每动作位移 ≤ 10。
     *  这两个数都由操作者给，这里只负责按额度放行。 */
    private int limit = 1;
    private int used;
    private long lastRefillNanos;

    /** 每个 bot tick 开头调用一次，按操作者配置重新发放额度。 */
    void beginTick(int actions) {
        refill(actions);
    }

    private void refill(int actions) {
        this.limit = Math.max(1, actions);
        this.used = 0;
        this.lastRefillNanos = System.nanoTime();
    }

    /** 额度本来只由 fast hook 每个 bot tick 发一次。但 fast hook 只挂在
     *  BotTicker 上：后台 bot 有自己的 ticker 线程，本地玩家（主客户端
     *  Client 线程）没有，那一次补发永远等不到，额度就停在初值 1——巡游
     *  推进一次后再也推不动，而主 tick 又被「tour 非空」提前 return，
     *  整条 TP 攻击就此永久冻结（实测 sends=8 之后 18 秒全零）。
     *  所以额度用完时补一条兜底：距上次发额度超过一个 tick 就按操作者
     *  配置自己再发一次。同一个 tick 内 fast hook 刚发过，时间差是微秒
     *  级，不会叠加成两倍位移。 */
    boolean tryAdvance(int actions, long now) {
        if (used >= limit && now - lastRefillNanos >= STALE_REFILL_NANOS) refill(actions);
        if (used >= limit) return false;
        used++;
        return true;
    }
}