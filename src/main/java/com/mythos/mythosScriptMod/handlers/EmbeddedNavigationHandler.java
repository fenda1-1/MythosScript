package com.mythos.mythosScriptMod.handlers;

import com.mythos.mythosScriptMod.config.BaritoneSettingsConfig;
import com.mythos.mythosScriptMod.config.DebugModule;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalFlightExact;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalXZ;
import com.mythos.mythosScriptMod.mythosScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;

/**
 * 导航适配层。
 *
 * 保留统一 API（startGoto / stop / pause / resume / follow），
 * 根据“使用内置 Baritone”设置决定：
 * 1. 直接调用内置 shadowbaritone process/pathing API
 * 2. 继续走命令桥接模式
 */
public class EmbeddedNavigationHandler {
    public static class NavigationDispatchTrace {
        private final boolean dispatched;
        private final String command;
        private final String normalizedCommand;
        private final String reasonKey;
        private final boolean bypassGotoThrottle;
        private final boolean directBuiltinMode;

        private NavigationDispatchTrace(boolean dispatched, String command, String normalizedCommand, String reasonKey,
                boolean bypassGotoThrottle, boolean directBuiltinMode) {
            this.dispatched = dispatched;
            this.command = command;
            this.normalizedCommand = normalizedCommand;
            this.reasonKey = reasonKey;
            this.bypassGotoThrottle = bypassGotoThrottle;
            this.directBuiltinMode = directBuiltinMode;
        }

        public boolean isDispatched() {
            return dispatched;
        }

        public String getCommand() {
            return command;
        }

        public String getNormalizedCommand() {
            return normalizedCommand;
        }

        public String getReasonKey() {
            return reasonKey;
        }

        public boolean isBypassGotoThrottle() {
            return bypassGotoThrottle;
        }

        public boolean isDirectBuiltinMode() {
            return directBuiltinMode;
        }
    }

    public static final EmbeddedNavigationHandler INSTANCE = new EmbeddedNavigationHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long SAME_COMMAND_MIN_INTERVAL_MS = 250L;
    private static final long STOP_COMMAND_MIN_INTERVAL_MS = 350L;
    private static final long GOTO_COMMAND_MIN_INTERVAL_MS = 1000L;

    /**
     * Per-session throttle state. A single shared INSTANCE previously kept one
     * global lastGotoCommandAt, so bot A's goto within GOTO_COMMAND_MIN_INTERVAL
     * of bot B's was throttled as a "duplicate" and sendChatCommand fell back to
     * mc.player.sendChatMessage — leaking the bot's goto into the foreground
     * (human) chat box. Keyed by the bound session's username.
     */
    private static final class ThrottleState {
        long lastAnyCommandAt = 0L;
        long lastStopCommandAt = 0L;
        long lastGotoCommandAt = 0L;
        String lastCommandSent = "";
    }

    private final java.util.Map<String, ThrottleState> throttles = new java.util.HashMap<>();

    private EmbeddedNavigationHandler() {
    }

    public boolean handleInternalCommand(String command) {
        if (command == null) {
            return false;
        }
        String c = command.trim();
        if (c.isEmpty()) {
            return false;
        }

        if (isNavigationCommand(c)) {
            return dispatchNavigationCommand(c, false).isDispatched();
        }
        return InternalBaritoneBridge.executeRawChatLikeCommand(c);
    }

    private boolean isNavigationCommand(String cmd) {
        String lower = normalizeCommand(cmd);
        return lower.equals("!stop")
                || lower.equals("!pause")
                || lower.equals("!resume")
                || lower.equals("!follow entities")
                || lower.startsWith("!goto ")
                || lower.startsWith(".goto ");
    }

    private NavigationDispatchTrace dispatchNavigationCommand(String rawCommand, boolean bypassGotoThrottle) {
        String normalized = normalizeCommand(rawCommand);
        boolean directBuiltinMode = BaritoneSettingsConfig.isUseBuiltinBaritone();
        net.minecraft.client.entity.EntityPlayerSP actingPlayer =
                com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (actingPlayer == null || actingPlayer.isSpectator()) {
            return recordDispatchTrace(sessionKey(), false, rawCommand, normalized,
                    "player_null_or_spectator", bypassGotoThrottle, directBuiltinMode);
        }

        String key = sessionKey();
        String blockReason = getThrottleReason(key, normalized, bypassGotoThrottle);
        if (!blockReason.isEmpty()) {
            return recordDispatchTrace(key, false, rawCommand, normalized, blockReason, bypassGotoThrottle,
                    directBuiltinMode);
        }

        boolean executed = directBuiltinMode
                ? executeBuiltinNavigationCommand(rawCommand, bypassGotoThrottle)
                : InternalBaritoneBridge.executeRawChatLikeCommand(rawCommand);
        if (!executed) {
            return recordDispatchTrace(key, false, rawCommand, normalized,
                    directBuiltinMode ? "builtin_execute_failed" : "bridge_execute_failed",
                    bypassGotoThrottle, directBuiltinMode);
        }

        rememberCommand(key, rawCommand);
        NavigationDispatchTrace trace = recordDispatchTrace(key, true, rawCommand, normalized, "dispatched",
                bypassGotoThrottle, directBuiltinMode);
        if (ModConfig.isDebugModeEnabled && actingPlayer != null) {
            String route = directBuiltinMode ? "内置直调" : "命令桥接";
            actingPlayer.sendMessage(new TextComponentString("§d[DEBUG] §7发送导航命令(" + route + "): §f" + rawCommand));
        }
        return trace;
    }

    private boolean executeBuiltinNavigationCommand(String rawCommand, boolean bypassGotoThrottle) {
        String normalized = normalizeCommand(rawCommand);

        try {
            if (normalized.equals("!stop")) {
                return executeBuiltinStop();
            }

            if (normalized.equals("!follow entities")) {
                return executeBuiltinFollowEntities();
            }

            if (normalized.startsWith("!goto ") || normalized.startsWith(".goto ")) {
                return executeBuiltinGotoCommand(normalized, bypassGotoThrottle);
            }

            if (normalized.equals("!pause") || normalized.equals("!resume")) {
                // pause/resume 没有稳定的公开直调 API，这里保留命令桥接兼容
                return InternalBaritoneBridge.executeRawChatLikeCommand(rawCommand);
            }
        } catch (Throwable t) {
            return false;
        }

        return false;
    }

    private boolean executeBuiltinGotoCommand(String normalizedCommand, boolean forceRestart) {
        String[] parts = normalizedCommand.split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        if ("!goto".equals(parts[0])) {
            if (parts.length == 3) {
                double x = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                return executeBuiltinGoto(x, Double.NaN, z, forceRestart);
            }
            if (parts.length == 4) {
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                return executeBuiltinGoto(x, y, z, forceRestart);
            }
            return false;
        }

        if (".goto".equals(parts[0])) {
            if (parts.length == 3) {
                double x = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                return executeBuiltinGoto(x, Double.NaN, z, forceRestart);
            }
            if (parts.length == 4) {
                double x = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                double y = Double.parseDouble(parts[3]);
                return executeBuiltinGoto(x, y, z, forceRestart);
            }
            return false;
        }

        return false;
    }

    private boolean executeBuiltinGoto(double x, double y, double z, boolean forceRestart) {
        IBaritone baritone = getPrimaryBaritone();
        nav$dbg("goto x=%.1f y=%.1f z=%.1f baritone=%s bound=%s", x, y, z,
                baritone == null ? "null" : baritone.getClass().getSimpleName(),
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentUsername());
        if (baritone == null) {
            return false;
        }
        // Only use a flight goal when the player actually enabled flight/blink
        // pathing. A 3-arg goto issued while flight is off should be a normal
        // ground GoalBlock, not a GoalFlightExact that routes through the far
        // more expensive flight pathing branch.
        boolean flightPathing = BaritoneAPI.getSettings().allowFlightPathing.value
                || BaritoneAPI.getSettings().allowBlinkPathing.value;
        Goal goal = Double.isNaN(y)
                ? new GoalXZ(MathHelper.floor(x), MathHelper.floor(z))
                : (flightPathing
                        ? new GoalFlightExact(x, y, z)
                        : new GoalBlock(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)));
        if (forceRestart && baritone.getPathingBehavior() != null) {
            baritone.getPathingBehavior().cancelEverything();
            if (shouldLogDispatchDebug()) {
                mythosScriptMod.LOGGER.info("[navigation] 强制 goto 前已清理旧路径状态: goal={}", goal);
            }
        }
        try {
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            nav$dbg("setGoalAndPath ok goal=%s", goal);
        } catch (Throwable t) {
            nav$dbg("setGoalAndPath fail goal=%s err=%s", goal, t);
            throw t;
        }
        return true;
    }

    private static void nav$dbg(String fmt, Object... a) {
        try {
            if (!navLogTried) {
                navLogTried = true;
                navLogBaritoneM = Class.forName("com.mythos.nexus.core.NexusLog")
                        .getMethod("baritone", String.class, Object[].class);
            }
            if (navLogBaritoneM != null) {
                navLogBaritoneM.invoke(null, "[nav] " + String.format(fmt, a), new Object[0]);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean navLogTried;
    private static java.lang.reflect.Method navLogBaritoneM;

    private boolean executeBuiltinFollowEntities() {
        IBaritone baritone = getPrimaryBaritone();
        baritone.getFollowProcess().follow(this::shouldFollowEntity);
        return true;
    }

    private boolean shouldFollowEntity(Entity entity) {
        return entity != null && entity != com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer()
                && entity.isEntityAlive();
    }

    private boolean executeBuiltinStop() {
        // Also stop any active path sequence so its step-idle retry doesn't re-fire "!goto".
        try {
            // Only the mounted session's runners — the shared "" foreground when
            // human is mounted, or the mounted bot's own runner. A blind
            // stopAllBackgroundRunners() was killing every bot on a "stop
            // foreground" click.
            String mounted = com.mythos.mythosScriptMod.mcp.NexusSessions.mountedBotUsername();
            String owner = mounted == null ? "" : mounted;
            com.mythos.mythosScriptMod.path.PathSequenceEventListener
                    .stopForegroundSequenceByActionForOwner(owner);
            com.mythos.mythosScriptMod.path.PathSequenceEventListener
                    .stopBackgroundRunnersForOwner(owner);
        } catch (Throwable ignored) {
        }
        IBaritone baritone = getPrimaryBaritone();
        baritone.getPathingBehavior().cancelEverything();
        return true;
    }

    private IBaritone getPrimaryBaritone() {
        // On a bound bot tick thread this is NOT the mounted player — this
        // class is outside FIELD_CTX_CLASSES, so mc.player would resolve the
        // mounted session's baritone and goto would drive the wrong bot.
        // boundPlayer() returns the thread-bound session's own player (and
        // falls back to mc.player when unbound).
        net.minecraft.client.entity.EntityPlayerSP p =
                com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (p != null) {
            IBaritone b = BaritoneAPI.getProvider().getBaritoneForPlayer(p);
            if (b != null) return b;
        }
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    /** Stable key for the thread-bound session; empty = foreground/unbound. */
    private String sessionKey() {
        String u = com.mythos.mythosScriptMod.mcp.NexusSessions.currentUsername();
        return u == null ? "" : u;
    }

    private ThrottleState throttleFor(String key) {
        synchronized (throttles) {
            ThrottleState t = throttles.get(key);
            if (t == null) {
                t = new ThrottleState();
                throttles.put(key, t);
            }
            return t;
        }
    }

    private String getThrottleReason(String key, String normalizedCommand, boolean bypassGotoThrottle) {
        ThrottleState t = throttleFor(key);
        synchronized (t) {
            long now = System.currentTimeMillis();
            boolean gotoCommand = isGotoCommand(normalizedCommand);
            boolean bypassSameCommandThrottle = bypassGotoThrottle && gotoCommand;

            if (!bypassSameCommandThrottle
                    && normalizedCommand.equals(t.lastCommandSent)
                    && (now - t.lastAnyCommandAt) < SAME_COMMAND_MIN_INTERVAL_MS) {
                return "same_command_throttle";
            }

            if (normalizedCommand.endsWith(" stop") && (now - t.lastStopCommandAt) < STOP_COMMAND_MIN_INTERVAL_MS) {
                return "stop_command_throttle";
            }

            if (gotoCommand && !bypassGotoThrottle && (now - t.lastGotoCommandAt) < GOTO_COMMAND_MIN_INTERVAL_MS) {
                return "goto_cooldown_throttle";
            }

            return "";
        }
    }

    private void rememberCommand(String key, String cmd) {
        ThrottleState t = throttleFor(key);
        synchronized (t) {
            long now = System.currentTimeMillis();
            String normalized = normalizeCommand(cmd);
            t.lastCommandSent = normalized;
            t.lastAnyCommandAt = now;

            if (normalized.endsWith(" stop")) {
                t.lastStopCommandAt = now;
            }
            if (normalized.contains(" goto ")) {
                t.lastGotoCommandAt = now;
            }
        }
    }

    private String normalizeCommand(String cmd) {
        return cmd == null ? "" : cmd.trim().toLowerCase();
    }

    private boolean isGotoCommand(String normalizedCommand) {
        return normalizedCommand.startsWith("!goto ") || normalizedCommand.startsWith(".goto ");
    }

    private NavigationDispatchTrace recordDispatchTrace(String key, boolean dispatched, String command,
            String normalizedCommand,
            String reasonKey, boolean bypassGotoThrottle, boolean directBuiltinMode) {
        NavigationDispatchTrace trace = new NavigationDispatchTrace(dispatched,
                command == null ? "" : command.trim(),
                normalizedCommand == null ? "" : normalizedCommand,
                reasonKey == null || reasonKey.trim().isEmpty() ? "unknown" : reasonKey.trim(),
                bypassGotoThrottle,
                directBuiltinMode);
        if (!dispatched && shouldLogDispatchDebug()) {
            mythosScriptMod.LOGGER.info(
                    "[navigation] 命令未发送: session={}, reason={}, command={}, bypassGotoThrottle={}, route={}",
                    key,
                    trace.getReasonKey(),
                    trace.getCommand(),
                    trace.isBypassGotoThrottle(),
                    trace.isDirectBuiltinMode() ? "builtin" : "bridge");
        }
        return trace;
    }

    private boolean shouldLogDispatchDebug() {
        return ModConfig.isDebugModeEnabled
                || ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)
                || ModConfig.isDebugFlagEnabled(DebugModule.CONDITIONAL_EXECUTION)
                || ModConfig.isDebugFlagEnabled(DebugModule.BARITONE);
    }

    public boolean startGoto(double x, double y, double z) {
        return startGoto(x, y, z, false);
    }

    public boolean startGoto(double x, double y, double z, boolean bypassGotoThrottle) {
        if (Double.isNaN(y)) {
            return dispatchNavigationCommand(String.format("!goto %.2f %.2f", x, z), bypassGotoThrottle)
                    .isDispatched();
        }
        return dispatchNavigationCommand(String.format("!goto %.2f %.2f %.2f", x, y, z), bypassGotoThrottle)
                .isDispatched();
    }

    public boolean startGotoXZ(double x, double z) {
        return startGotoXZ(x, z, false);
    }

    public boolean startGotoXZ(double x, double z, boolean bypassGotoThrottle) {
        return dispatchNavigationCommand(String.format("!goto %.2f %.2f", x, z), bypassGotoThrottle)
                .isDispatched();
    }

    public boolean startFollowEntities() {
        return dispatchNavigationCommand("!follow entities", false).isDispatched();
    }

    public boolean stop() {
        return dispatchNavigationCommand("!stop", false).isDispatched();
    }

    public boolean pause() {
        return dispatchNavigationCommand("!pause", false).isDispatched();
    }

    public boolean resume() {
        return dispatchNavigationCommand("!resume", false).isDispatched();
    }

    public boolean isPathingOrCalculating() {
        try {
            IBaritone baritone = getPrimaryBaritone();
            if (baritone == null || baritone.getPathingBehavior() == null) {
                return false;
            }
            return baritone.getPathingBehavior().isPathing()
                    || baritone.getPathingBehavior().hasPath()
                    || baritone.getPathingBehavior().getInProgress().isPresent()
                    || baritone.getPathingBehavior().isFlightWaitingForContinuation()
                    || baritone.getPathingBehavior().isFlightTerminalReached();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Cancel baritone pathing ONLY — must NOT touch the running path sequence.
     * {@link #stop()} routes through {@code executeBuiltinStop} which, in
     * builtin-baritone mode, also calls
     * {@code PathSequenceEventListener.stopForegroundSequenceByAction()} /
     * {@code stopAllBackgroundRunners()}. That coupling is correct for a
     * user-facing "!stop" but deadly for callers that use "stop navigation" as
     * a tactical cancel inside a still-running action — the hunt action calls
     * {@code stopHuntNavigation()} every time it re-solves or drops a target,
     * so a single "stop" was silently killing the whole sequence one tick
     * after it started (the "打了两下就停" report).
     */
    public boolean cancelPathingOnly() {
        try {
            IBaritone baritone = getPrimaryBaritone();
            if (baritone == null || baritone.getPathingBehavior() == null) {
                return false;
            }
            baritone.getPathingBehavior().cancelEverything();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
