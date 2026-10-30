// --- Full Java Content (src/main/java/com/mythos/mythosScriptMod/config/ModConfig.java) ---
package com.mythos.mythosScriptMod.config;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.handlers.*;
import com.mythos.mythosScriptMod.handlers.BlockReplacementHandler;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.utils.CapturedIdRuleManager;
import com.mythos.mythosScriptMod.PerformanceMonitor;
import com.mythos.mythosScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.mythos.mythosScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.world.WorldFeatureManager;

import net.minecraft.client.Minecraft;
import com.mythos.mythosScriptMod.gui.DetachedSwingWindowManager;
import net.minecraft.util.text.TextFormatting;
import java.util.EnumMap;
import java.util.Map;
import java.io.*;
import com.mythos.mythosScriptMod.handlers.AutoPickupHandler; // 新增导入

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
     * 向日志文件打印调试信息，不输出到聊天框。
     *
     * @param module  调试信息所属的模块
     * @param message 要打印的消息
     */
    public static void debugLog(DebugModule module, String message) {
        if (isDebugFlagEnabled(module)) {
            DebugLogManager.append(module, DebugLogManager.Level.DEBUG, safeDebugMessage(message));
            mythosScriptMod.LOGGER.info("[DEBUG:{}|{}] {}", module.name(), module.getDisplayName(), message);
        }
    }


    /**
     * 按会话重载「非序列」配置。与 {@link #loadAllConfigs()} 的区别：序列相关
     * （动作/路径序列、捕获 ID 规则）是跨会话共享的，不随会话切换重载；其余
     * handler 与功能模块的配置各会话一份，切会话时整份换掉。
     */
    public static void loadSessionConfigs() {
        AutoEatHandler.loadAutoEatConfig();
        AutoFishingHandler.loadConfig();
        AutoEscapeHandler.loadConfig();
        AutoFollowHandler.loadFollowConfig();
        AutoPickupHandler.loadConfig();
        AutoUseItemHandler.loadConfig();
        BlockReplacementHandler.loadConfig();
        ConditionalExecutionHandler.loadConfig();
        DungeonWarehouseHandler.loadConfig();
        WarehouseManager.loadWarehouses();
        FlyHandler.loadConfig();
        ItemFilterHandler.loadFilterConfig();
        SpeedHandler.loadConfig();
        BlockFeatureManager.loadConfig();
        ItemFeatureManager.loadConfig();
        MiscFeatureManager.loadConfig();
        MovementFeatureManager.loadConfig();
        RenderFeatureManager.loadConfig();
        WorldFeatureManager.loadConfig();
        BaritoneSettingsConfig.load();
        BlinkPathingConfig.load();
        FlightPathingConfig.load();
        ChatOptimizationConfig.load();
        HumanLikeMovementConfig.load();
        KillAuraHandler.reloadConfigValues();
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

