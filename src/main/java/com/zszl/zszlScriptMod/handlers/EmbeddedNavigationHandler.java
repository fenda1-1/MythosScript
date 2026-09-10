package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.config.BaritoneSettingsConfig;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalXZ;
import com.zszl.zszlScriptMod.zszlScriptMod;

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

    private long lastAnyCommandAt = 0L;
    private long lastStopCommandAt = 0L;
    private long lastGotoCommandAt = 0L;
    private String lastCommandSent = "";
    private volatile NavigationDispatchTrace lastDispatchTrace = new NavigationDispatchTrace(false, "", "",
            "never_dispatched", false, BaritoneSettingsConfig.isUseBuiltinBaritone());

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

        return false;
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

    public NavigationDispatchTrace getLastDispatchTrace() {
        return lastDispatchTrace;
    }

    private NavigationDispatchTrace dispatchNavigationCommand(String rawCommand, boolean bypassGotoThrottle) {
        String normalized = normalizeCommand(rawCommand);
        boolean directBuiltinMode = BaritoneSettingsConfig.isUseBuiltinBaritone();
        if (mc.player == null || mc.player.isSpectator()) {
            return recordDispatchTrace(false, rawCommand, normalized, "player_null_or_spectator", bypassGotoThrottle,
                    directBuiltinMode);
        }

        String blockReason = getThrottleReason(normalized, bypassGotoThrottle);
        if (!blockReason.isEmpty()) {
            return recordDispatchTrace(false, rawCommand, normalized, blockReason, bypassGotoThrottle,
                    directBuiltinMode);
        }

        boolean executed = directBuiltinMode
                ? executeBuiltinNavigationCommand(rawCommand, bypassGotoThrottle)
                : InternalBaritoneBridge.executeRawChatLikeCommand(rawCommand);
        if (!executed) {
            return recordDispatchTrace(false, rawCommand, normalized,
                    directBuiltinMode ? "builtin_execute_failed" : "bridge_execute_failed",
                    bypassGotoThrottle, directBuiltinMode);
        }

        rememberCommand(rawCommand);
        NavigationDispatchTrace trace = recordDispatchTrace(true, rawCommand, normalized, "dispatched",
                bypassGotoThrottle, directBuiltinMode);
        if (ModConfig.isDebugModeEnabled && mc.player != null) {
            String route = directBuiltinMode ? "内置直调" : "命令桥接";
            mc.player.sendMessage(new TextComponentString("§d[DEBUG] §7发送导航命令(" + route + "): §f" + rawCommand));
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
        Goal goal = Double.isNaN(y)
                ? new GoalXZ(MathHelper.floor(x), MathHelper.floor(z))
                : new GoalBlock(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        if (forceRestart && baritone.getPathingBehavior() != null) {
            baritone.getPathingBehavior().cancelEverything();
            if (shouldLogDispatchDebug()) {
                zszlScriptMod.LOGGER.info("[navigation] 强制 goto 前已清理旧路径状态: goal={}", goal);
            }
        }
        baritone.getCustomGoalProcess().setGoalAndPath(goal);
        return true;
    }

    private boolean executeBuiltinFollowEntities() {
        IBaritone baritone = getPrimaryBaritone();
        baritone.getFollowProcess().follow(this::shouldFollowEntity);
        return true;
    }

    private boolean shouldFollowEntity(Entity entity) {
        return entity != null && entity != mc.player && entity.isEntityAlive();
    }

    private boolean executeBuiltinStop() {
        IBaritone baritone = getPrimaryBaritone();
        baritone.getPathingBehavior().cancelEverything();
        return true;
    }

    private IBaritone getPrimaryBaritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    private String getThrottleReason(String normalizedCommand, boolean bypassGotoThrottle) {
        long now = System.currentTimeMillis();
        boolean gotoCommand = isGotoCommand(normalizedCommand);
        boolean bypassSameCommandThrottle = bypassGotoThrottle && gotoCommand;

        if (!bypassSameCommandThrottle
                && normalizedCommand.equals(lastCommandSent)
                && (now - lastAnyCommandAt) < SAME_COMMAND_MIN_INTERVAL_MS) {
            return "same_command_throttle";
        }

        if (normalizedCommand.endsWith(" stop") && (now - lastStopCommandAt) < STOP_COMMAND_MIN_INTERVAL_MS) {
            return "stop_command_throttle";
        }

        if (gotoCommand && !bypassGotoThrottle && (now - lastGotoCommandAt) < GOTO_COMMAND_MIN_INTERVAL_MS) {
            return "goto_cooldown_throttle";
        }

        return "";
    }

    private void rememberCommand(String cmd) {
        long now = System.currentTimeMillis();
        String normalized = normalizeCommand(cmd);
        lastCommandSent = normalized;
        lastAnyCommandAt = now;

        if (normalized.endsWith(" stop")) {
            lastStopCommandAt = now;
        }
        if (normalized.contains(" goto ")) {
            lastGotoCommandAt = now;
        }
    }

    private String normalizeCommand(String cmd) {
        return cmd == null ? "" : cmd.trim().toLowerCase();
    }

    private boolean isGotoCommand(String normalizedCommand) {
        return normalizedCommand.startsWith("!goto ") || normalizedCommand.startsWith(".goto ");
    }

    private NavigationDispatchTrace recordDispatchTrace(boolean dispatched, String command, String normalizedCommand,
            String reasonKey, boolean bypassGotoThrottle, boolean directBuiltinMode) {
        NavigationDispatchTrace trace = new NavigationDispatchTrace(dispatched,
                command == null ? "" : command.trim(),
                normalizedCommand == null ? "" : normalizedCommand,
                reasonKey == null || reasonKey.trim().isEmpty() ? "unknown" : reasonKey.trim(),
                bypassGotoThrottle,
                directBuiltinMode);
        lastDispatchTrace = trace;
        if (!dispatched && shouldLogDispatchDebug()) {
            zszlScriptMod.LOGGER.info(
                    "[navigation] 命令未发送: reason={}, command={}, bypassGotoThrottle={}, route={}",
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
}
