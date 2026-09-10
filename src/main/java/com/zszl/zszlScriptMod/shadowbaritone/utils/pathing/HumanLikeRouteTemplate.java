package com.zszl.zszlScriptMod.shadowbaritone.utils.pathing;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟真人路线模板数据结构。
 *
 * 这不是最终执行器，而是为后续“模板读取 / 锚点组合 / 关键点约束 / 区域化起终点”
 * 提供统一的数据模型。
 */
public class HumanLikeRouteTemplate {

    /**
     * 模板唯一标识。
     */
    public String id = "";

    /**
     * 模板显示名称。
     */
    public String name = "";

    /**
     * 起点区域定义。
     */
    public Region startRegion = new Region();

    /**
     * 终点区域定义。
     */
    public Region endRegion = new Region();

    /**
     * 候选锚点组。
     *
     * 每组可表示“一组选一”或“可多选”的候选中途点。
     */
    public List<AnchorGroup> anchorGroups = new ArrayList<>();

    /**
     * 必经关键点。
     */
    public List<KeyPoint> keyPoints = new ArrayList<>();

    /**
     * 模板级说明。
     */
    public String note = "";

    public void normalize() {
        if (id == null) {
            id = "";
        }
        if (name == null) {
            name = "";
        }
        if (note == null) {
            note = "";
        }
        if (startRegion == null) {
            startRegion = new Region();
        }
        if (endRegion == null) {
            endRegion = new Region();
        }
        if (anchorGroups == null) {
            anchorGroups = new ArrayList<>();
        }
        if (keyPoints == null) {
            keyPoints = new ArrayList<>();
        }

        startRegion.normalize();
        endRegion.normalize();

        for (AnchorGroup group : anchorGroups) {
            if (group != null) {
                group.normalize();
            }
        }
        for (KeyPoint point : keyPoints) {
            if (point != null) {
                point.normalize();
            }
        }
    }

    /**
     * 三维区域定义。
     */
    public static class Region {
        public double centerX = 0.0D;
        public double centerY = 0.0D;
        public double centerZ = 0.0D;

        /**
         * 水平方向允许半径。
         */
        public double horizontalRadius = 0.0D;

        /**
         * 垂直方向允许半径。
         */
        public double verticalRadius = 0.0D;

        public void normalize() {
            horizontalRadius = Math.max(0.0D, horizontalRadius);
            verticalRadius = Math.max(0.0D, verticalRadius);
        }
    }

    /**
     * 锚点组。
     */
    public static class AnchorGroup {
        /**
         * 组标识。
         */
        public String id = "";

        /**
         * 该组最少命中几个锚点。
         */
        public int minSelect = 1;

        /**
         * 该组最多命中几个锚点。
         */
        public int maxSelect = 1;

        /**
         * 候选锚点列表。
         */
        public List<AnchorPoint> anchors = new ArrayList<>();

        public void normalize() {
            if (id == null) {
                id = "";
            }
            if (anchors == null) {
                anchors = new ArrayList<>();
            }
            minSelect = Math.max(0, minSelect);
            maxSelect = Math.max(minSelect, maxSelect);
            for (AnchorPoint anchor : anchors) {
                if (anchor != null) {
                    anchor.normalize();
                }
            }
        }
    }

    /**
     * 单个候选锚点。
     */
    public static class AnchorPoint {
        public double x = 0.0D;
        public double y = 0.0D;
        public double z = 0.0D;

        /**
         * 锚点随机偏移半径。
         */
        public double randomRadius = 0.0D;

        /**
         * 相对权重，越大越容易被选中。
         */
        public double weight = 1.0D;

        /**
         * 是否更偏向路径前段。
         */
        public boolean preferEarly = false;

        /**
         * 是否更偏向路径后段。
         */
        public boolean preferLate = false;

        public void normalize() {
            randomRadius = Math.max(0.0D, randomRadius);
            weight = Math.max(0.0D, weight);
        }
    }

    /**
     * 必经关键点。
     */
    public static class KeyPoint {
        public double x = 0.0D;
        public double y = 0.0D;
        public double z = 0.0D;

        /**
         * 容差半径。
         */
        public double tolerance = 1.5D;

        /**
         * 越大表示约束越强。
         */
        public double priority = 1.0D;

        public void normalize() {
            tolerance = Math.max(0.0D, tolerance);
            priority = Math.max(0.0D, priority);
        }
    }
}