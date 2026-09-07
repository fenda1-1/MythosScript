package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.zszlScriptMod;

import static com.zszl.zszlScriptMod.shadowbaritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public final class InternalBaritoneBridge {

    private InternalBaritoneBridge() {
    }

    public static boolean executeRawChatLikeCommand(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        String normalized = normalizeToBaritoneCommand(trimmed);
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }

        try {
            return BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCommandManager()
                    .execute(normalized);
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.error("内置导航命令执行失败: {}", raw, t);
            return false;
        }
    }

    private static String normalizeToBaritoneCommand(String raw) {
        if (raw.startsWith(FORCE_COMMAND_PREFIX)) {
            String cmd = raw.substring(FORCE_COMMAND_PREFIX.length()).trim();
            return cmd.isEmpty() ? null : cmd;
        }

        if (raw.startsWith(".b")) {
            String cmd = raw.substring(2).trim();
            return cmd.isEmpty() ? null : cmd;
        }

        if (raw.startsWith("!")) {
            String cmd = raw.substring(1).trim();
            return cmd.isEmpty() ? null : cmd;
        }

        if (raw.startsWith(".goto")) {
            String[] parts = raw.split("\\s+");
            if (parts.length == 3) {
                // .goto x z
                return "goto " + parts[1] + " " + parts[2];
            }
            if (parts.length == 4) {
                // .goto x z y -> goto x y z
                return "goto " + parts[1] + " " + parts[3] + " " + parts[2];
            }
            return null;
        }

        return null;
    }
}
