// --- Full Java Content (src/main/java/com/zszl/zszlScriptMod/config/ModConfig.java) ---
package com.zszl.zszlScriptMod.config;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.handlers.*;
import com.zszl.zszlScriptMod.handlers.BlockReplacementHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;
import com.zszl.zszlScriptMod.PerformanceMonitor;

import net.minecraft.client.Minecraft;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import net.minecraft.util.text.TextFormatting;
import java.util.EnumMap;
import java.util.Map;
import java.io.*;
import com.zszl.zszlScriptMod.handlers.AutoPickupHandler; // 新增导入

public class ModConfig {

    public static boolean showMouseCoordinates = false;
    public static boolean showHoverInfo = false;
    public static final String CONFIG_DIR = "config/我的世界脚本";
    public static boolean ahkMoveMouseMode = false;

    public static boolean enableGuiListener = false;
    public static boolean enableGhostItemCopy = false;

    // !! 核心修改：新增自动暂停开关 !!
    public static boolean autoPauseOnMenuOpen = true;

    public static boolean isDebugModeEnabled = false; // 全局调试模式开关
    public static final Map<DebugModule, Boolean> debugFlags = new EnumMap<>(DebugModule.class);
    private static final ThreadLocal<Integer> INTERNAL_CHAT_SUPPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);
    public static boolean isMouseDetached = false;
    static {
        for (DebugModule module : DebugModule.values()) {
            debugFlags.put(module, module != DebugModule.KILL_AURA_TELEPORT);
        }
    }

    /**
     * 新的调试检查方法。
     * 只有当总开关和对应模块的开关都打开时，才返回true。
     * 
     * @param module 要检查的调试模块
     * @return 是否应为该模块输出调试日志
     */
    public static boolean isDebugFlagEnabled(DebugModule module) {
        if (!isDebugModeEnabled) {
            return false;
        }
        return debugFlags.getOrDefault(module, false);
    }

    public static void runWithInternalChatSuppressed(Runnable action) {
        if (action == null) {
            return;
        }
        int depth = INTERNAL_CHAT_SUPPRESSION_DEPTH.get();
        INTERNAL_CHAT_SUPPRESSION_DEPTH.set(depth + 1);
        try {
            action.run();
        } finally {
            int newDepth = INTERNAL_CHAT_SUPPRESSION_DEPTH.get() - 1;
            if (newDepth <= 0) {
                INTERNAL_CHAT_SUPPRESSION_DEPTH.remove();
            } else {
                INTERNAL_CHAT_SUPPRESSION_DEPTH.set(newDepth);
            }
        }
    }

    public static boolean isInternalChatSuppressed() {
        return INTERNAL_CHAT_SUPPRESSION_DEPTH.get() > 0;
    }

    public static boolean isInternalDebugChatMessage(String text) {
        String normalized = normalizeInternalDebugChatText(text);
        return normalized.startsWith("[DEBUG:")
                || normalized.startsWith("[FORCE DEBUG:");
    }

    /**
     * 向游戏内调试终端打印调试信息的方法。
     * 
     * @param module  调试信息所属的模块
     * @param message 要打印的消息
     */
    public static void debugPrint(DebugModule module, String message) {
        if (isDebugFlagEnabled(module)) {
            DebugLogManager.append(module, DebugLogManager.Level.INFO, safeDebugMessage(message));
        }
    }

    /**
     * 强制向游戏内调试终端打印调试信息，无视任何开关。
     * 
     * @param module  调试信息所属的模块
     * @param message 要打印的消息
     */
    public static void debugPrintForce(DebugModule module, String message) {
        DebugLogManager.append(module, DebugLogManager.Level.INFO, safeDebugMessage(message), true);
    }

    /**
     * 向日志文件打印调试信息，不输出到聊天框。
     *
     * @param module  调试信息所属的模块
     * @param message 要打印的消息
     */
    public static void debugLog(DebugModule module, String message) {
        if (isDebugFlagEnabled(module)) {
            DebugLogManager.append(module, DebugLogManager.Level.DEBUG, safeDebugMessage(message));
            zszlScriptMod.LOGGER.info("[DEBUG:{}|{}] {}", module.name(), module.getDisplayName(), message);
        }
    }

    /**
     * 强制向日志文件打印调试信息，无视任何开关。
     *
     * @param module  调试信息所属的模块
     * @param message 要打印的消息
     */
    public static void debugLogForce(DebugModule module, String message) {
        DebugLogManager.append(module, DebugLogManager.Level.DEBUG, safeDebugMessage(message), true);
        zszlScriptMod.LOGGER.info("[FORCE DEBUG:{}|{}] {}", module.name(), module.getDisplayName(), message);
    }

    /**
     * 加载所有配置
     */
    public static void loadAllConfigs() {
        AutoEatHandler.loadAutoEatConfig();
        ItemFilterHandler.loadFilterConfig();
        AutoFollowHandler.loadFollowConfig();
        ConditionalExecutionHandler.loadConfig();
        AutoPickupHandler.loadConfig();
        AutoUseItemHandler.loadConfig();
        BlockReplacementHandler.loadConfig();
        PerformanceMonitor.loadConfig();
        PathSequenceManager.initializePathSequences();
        CapturedIdRuleManager.initialize();
    }

    /**
     * 检查是否有GUI打开
     * 
     * @return true if a GUI is open, false otherwise.
     */
    public static boolean isGuiOpen() {
        return Minecraft.getMinecraft().currentScreen != null || DetachedSwingWindowManager.isDetached();
    }

    private static String normalizeInternalDebugChatText(String text) {
        if (text == null) {
            return "";
        }
        String stripped = TextFormatting.getTextWithoutFormattingCodes(text);
        return (stripped == null ? text : stripped).trim();
    }

    private static String safeDebugMessage(String message) {
        return message == null ? "" : message;
    }
}

