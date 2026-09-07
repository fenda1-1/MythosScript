// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/AutoPickupRule.java
package com.zszl.zszlScriptMod.system;

import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

public class AutoPickupRule {
    public static final double DEFAULT_TARGET_REACH_DISTANCE = 0.5D;
    public static final int DEFAULT_MAX_PICKUP_ATTEMPTS = 3;
    public static final int INVENTORY_SLOT_COUNT = 36;
    public static final int INVENTORY_SLOT_COLUMNS = 9;
    public static final int INVENTORY_SLOT_ROWS = 4;

    public static class ItemMatchEntry {
        public String keyword;
        public List<String> requiredNbtTags;

        public ItemMatchEntry() {
            this.keyword = "";
            this.requiredNbtTags = new ArrayList<>();
        }

        public ItemMatchEntry(ItemMatchEntry other) {
            this();
            if (other == null) {
                return;
            }
            this.keyword = other.keyword == null ? "" : other.keyword;
            if (other.requiredNbtTags != null) {
                this.requiredNbtTags.addAll(other.requiredNbtTags);
            }
        }
    }

    public static class PickupActionEntry {
        public String keyword;
        public List<String> requiredNbtTags;
        public String sequenceName;
        public int executeDelaySeconds;

        public PickupActionEntry() {
            this.keyword = "";
            this.requiredNbtTags = new ArrayList<>();
            this.sequenceName = "";
            this.executeDelaySeconds = 0;
        }

        public PickupActionEntry(PickupActionEntry other) {
            this();
            if (other == null) {
                return;
            }
            this.keyword = other.keyword == null ? "" : other.keyword;
            if (other.requiredNbtTags != null) {
                this.requiredNbtTags.addAll(other.requiredNbtTags);
            }
            this.sequenceName = other.sequenceName == null ? "" : other.sequenceName;
            this.executeDelaySeconds = Math.max(0, other.executeDelaySeconds);
        }
    }

    public String name;
    public String category;
    public boolean enabled; // 此规则是否激活
    public double centerX, centerY, centerZ, radius;
    public double targetReachDistance;
    public int maxPickupAttempts;
    public boolean visualizeRange;
    public boolean enableItemWhitelist;
    public boolean enableItemBlacklist;
    public List<String> itemWhitelist;
    public List<String> itemBlacklist;
    public List<ItemMatchEntry> itemWhitelistEntries;
    public List<ItemMatchEntry> itemBlacklistEntries;
    public List<PickupActionEntry> pickupActionEntries;
    public List<Integer> inventoryDetectionSlots;
    public String postPickupSequence; // 拾取完毕后执行的序列名称
    public int postPickupDelaySeconds; // 拾取完毕后的延迟时间（秒）
    public boolean stopOnExit; // 离开区域后是否停止后续序列
    public boolean antiStuckEnabled; // 是否开启防卡重启
    public int antiStuckTimeoutSeconds; // 停留多久后重启
    public String antiStuckRestartSequence; // 防卡时执行的自定义重启序列

    public AutoPickupRule() {
        // 默认值
        this.name = I18n.format("rule.auto_pickup.default_name");
        this.category = "默认";
        this.enabled = true;
        this.centerX = 0;
        this.centerY = 0;
        this.centerZ = 0;
        this.radius = 20.0;
        this.targetReachDistance = DEFAULT_TARGET_REACH_DISTANCE;
        this.maxPickupAttempts = DEFAULT_MAX_PICKUP_ATTEMPTS;
        this.visualizeRange = false;
        this.enableItemWhitelist = false;
        this.enableItemBlacklist = false;
        this.itemWhitelist = new ArrayList<>();
        this.itemBlacklist = new ArrayList<>();
        this.itemWhitelistEntries = new ArrayList<>();
        this.itemBlacklistEntries = new ArrayList<>();
        this.pickupActionEntries = new ArrayList<>();
        this.inventoryDetectionSlots = new ArrayList<>();
        this.postPickupSequence = "";
        this.postPickupDelaySeconds = 10;
        this.stopOnExit = true;
        this.antiStuckEnabled = false;
        this.antiStuckTimeoutSeconds = 8;
        this.antiStuckRestartSequence = "";
    }

    public boolean isPlayerInside(double playerX, double playerY, double playerZ) {
        // 使用 zszlScriptMod 中的通用方法进行距离检查
        return zszlScriptMod.ArriveAt(centerX, centerY, centerZ, radius);
    }
}
