package com.zszl.zszlScriptMod.system;

import net.minecraft.client.Minecraft;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能监控系统
 * 用于监控各种功能模块的性能表现
 */
public class PerformanceMonitor {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // 功能开关控制
    public static final Map<String, Boolean> featureEnabled = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> performanceAnalysisEnabled = new ConcurrentHashMap<>();

    // 性能统计数据
    public static final Map<String, PerformanceStats> performanceStats = new ConcurrentHashMap<>();

    // 可能导致卡顿的功能列表
    public static final String[] PERFORMANCE_FEATURES = {
            "auto_follow", // 自动追怪
            "auto_equip", // 自动装备
            "auto_pickup", // 自动拾取
            "path_execution", // 路径执行
            "debuff_detector", // debuff检测
            "conditional_execution", // 条件执行
            "goto_handler", // 前往处理器
            "warehouse", // 仓库管理
            "render_overlays" // 渲染覆盖层
    };

    static {
        // 初始化所有功能为启用状态
        for (String feature : PERFORMANCE_FEATURES) {
            featureEnabled.put(feature, true);
            performanceAnalysisEnabled.put(feature, false);
            performanceStats.put(feature, new PerformanceStats());
        }
    }

    /**
     * 性能统计数据类
     */
    public static class PerformanceStats {
        public long totalExecutionTime = 0;
        public long executionCount = 0;
        public long maxExecutionTime = 0;
        public long minExecutionTime = Long.MAX_VALUE;
        public long lastExecutionTime = 0;
        public long averageExecutionTime = 0;

        public void recordExecution(long executionTime) {
            totalExecutionTime += executionTime;
            executionCount++;
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            minExecutionTime = Math.min(minExecutionTime, executionTime);
            lastExecutionTime = executionTime;
            averageExecutionTime = totalExecutionTime / executionCount;
        }

        public void reset() {
            totalExecutionTime = 0;
            executionCount = 0;
            maxExecutionTime = 0;
            minExecutionTime = Long.MAX_VALUE;
            lastExecutionTime = 0;
            averageExecutionTime = 0;
        }

        public String getFormattedStats() {
            if (executionCount == 0) {
                return "暂无数据";
            }
            return String.format("平均: %.2fms, 最大: %dms, 最小: %dms, 次数: %d",
                    averageExecutionTime / 1000000.0, maxExecutionTime / 1000000, minExecutionTime / 1000000,
                    executionCount);
        }
    }

    /**
     * 性能监控工具类
     */
    public static class PerformanceTimer {
        private final String featureName;
        private long startTime;

        public PerformanceTimer(String featureName) {
            this.featureName = featureName;
        }

        public void start() {
            if (performanceAnalysisEnabled.getOrDefault(featureName, false)) {
                startTime = System.nanoTime();
            }
        }

        public void stop() {
            if (performanceAnalysisEnabled.getOrDefault(featureName, false)) {
                long executionTime = System.nanoTime() - startTime;
                PerformanceStats stats = performanceStats.get(featureName);
                if (stats != null) {
                    stats.recordExecution(executionTime);
                }
            }
        }
    }

    /**
     * 检查功能是否启用
     */
    public static boolean isFeatureEnabled(String featureName) {
        return featureEnabled.getOrDefault(featureName, true);
    }

    /**
     * 启用/禁用功能
     */
    public static void setFeatureEnabled(String featureName, boolean enabled) {
        featureEnabled.put(featureName, enabled);
    }

    /**
     * 检查性能分析是否启用
     */
    public static boolean isPerformanceAnalysisEnabled(String featureName) {
        return performanceAnalysisEnabled.getOrDefault(featureName, false);
    }

    /**
     * 启用/禁用性能分析
     */
    public static void setPerformanceAnalysisEnabled(String featureName, boolean enabled) {
        performanceAnalysisEnabled.put(featureName, enabled);
        if (!enabled) {
            // 禁用时重置统计数据
            PerformanceStats stats = performanceStats.get(featureName);
            if (stats != null) {
                stats.reset();
            }
        }
    }

    /**
     * 重置所有性能统计数据
     */
    public static void resetAllStats() {
        for (PerformanceStats stats : performanceStats.values()) {
            stats.reset();
        }
    }

    /**
     * 获取功能显示名称
     */
    public static String getFeatureDisplayName(String featureName) {
        switch (featureName) {
            case "auto_follow":
                return "自动追怪";
            case "auto_equip":
                return "自动装备";
            case "auto_pickup":
                return "自动拾取";
            case "path_execution":
                return "路径执行";
            case "debuff_detector":
                return "Debuff检测";
            case "conditional_execution":
                return "条件执行";
            case "goto_handler":
                return "前往处理器";
            case "warehouse":
                return "仓库管理";
            case "render_overlays":
                return "渲染覆盖层";
            default:
                return featureName;
        }
    }

    /**
     * 获取功能描述
     */
    public static String getFeatureDescription(String featureName) {
        switch (featureName) {
            case "auto_follow":
                return "自动追怪和导航功能，每tick检查怪物位置并发送导航命令";
            case "auto_equip":
                return "自动装备更换功能，监控装备耐久并自动更换";
            case "auto_pickup":
                return "自动拾取物品功能，监控掉落物并自动拾取";
            case "path_execution":
                return "路径序列执行功能，执行预定义的动作序列";
            case "debuff_detector":
                return "Debuff状态检测功能，监控玩家状态效果";
            case "conditional_execution":
                return "条件执行功能，根据条件执行特定动作";
            case "goto_handler":
                return "前往处理器功能，处理导航和移动命令";
            case "warehouse":
                return "仓库管理功能，管理物品存储和整理";
            case "render_overlays":
                return "渲染覆盖层功能，显示UI覆盖和视觉效果";
            default:
                return "未知功能";
        }
    }
}
