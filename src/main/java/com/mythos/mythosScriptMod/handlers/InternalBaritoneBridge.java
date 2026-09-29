package com.mythos.mythosScriptMod.handlers;

import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.mythosScriptMod;

import static com.mythos.mythosScriptMod.shadowbaritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

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
            String settingName = normalized.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (BaritoneAPI.getSettings().byLowerName.containsKey(settingName)) {
                normalized = "set " + normalized;
            }
            return BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCommandManager()
                    .execute(normalized);
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.error("内置导航命令执行失败: {}", raw, t);
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

        String prefix = chatPrefix();
        if (!prefix.isEmpty() && raw.startsWith(prefix)) {
            String cmd = raw.substring(prefix.length()).trim();
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

    /** User-configurable Baritone chat prefix. Default is "!". */
    public static String chatPrefix() {
        try {
            String prefix = BaritoneAPI.getSettings().prefix.value;
            return prefix == null ? "!" : prefix;
        } catch (Throwable ignored) {
            return "!";
        }
    }
}
