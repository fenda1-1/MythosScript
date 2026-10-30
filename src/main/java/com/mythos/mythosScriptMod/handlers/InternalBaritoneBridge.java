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
            com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone baritone = resolveBaritone();
            if (baritone == null) {
                return false;
            }
            return baritone.getCommandManager().execute(normalized);
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.error("内置导航命令执行失败: {}", raw, t);
            return false;
        }
    }

    /**
     * Session-aware baritone for chat commands. When a Nexus bot is mounted,
     * mc.player is that bot and its per-session baritone (keyed by username)
     * is the one MixinMinecraft ticks — so commands must land there, not on
     * the primary baritone which would sit in PATH_REQUESTED untouched.
     */
    private static com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone resolveBaritone() {
        try {
            net.minecraft.client.entity.EntityPlayerSP p =
                    com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
            com.mythos.mythosScriptMod.shadowbaritone.api.IBaritoneProvider provider =
                    BaritoneAPI.getProvider();
            if (p != null) {
                com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone b =
                        provider.getBaritoneForPlayer(p);
                if (b != null) {
                    return b;
                }
            }
            return provider.getPrimaryBaritone();
        } catch (Throwable t) {
            return null;
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
