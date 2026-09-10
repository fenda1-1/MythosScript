// --- Full Java Content (src/main/java/com/zszl/zszlScriptMod/config/DebugModule.java) ---
package com.zszl.zszlScriptMod.config;

/**
 * 定义所有可进行独立调试日志开关的模块。
 */
public enum DebugModule {
    PATH_SEQUENCE("路径序列"),
    EVACUATION("撤离逻辑"),
    AUTO_EAT("自动进食"),
    ITEM_FILTER("物品过滤"),
    AHK_EXECUTION("AHK脚本调用"),
    ARENA_HANDLER("竞技场处理"),
    CHEST_ANALYSIS("箱子判断"),
    WAREHOUSE_ANALYSIS("仓库判断"),
    CONDITIONAL_EXECUTION("条件执行"),
    AUTO_PICKUP("自动拾取"),
    AUTO_EQUIP("自动穿戴"),
    TRIGGER_RULES("触发器"),
    BARITONE("调试Baritone"),
    KILL_AURA_TELEPORT("TP攻击调试"),
    KILL_AURA_ORBIT("杀戮绕圈摘要"),
    KILL_AURA_ORBIT_TRACE("杀戮绕圈深度跟踪");

    private final String displayName;

    DebugModule(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
