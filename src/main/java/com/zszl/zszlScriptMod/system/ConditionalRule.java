package com.zszl.zszlScriptMod.system;

import net.minecraft.client.resources.I18n;

public class ConditionalRule {
    public static final String DEFAULT_VISUALIZE_BORDER_COLOR = "#4AA3FF";
    public static final int DEFAULT_ANTI_STUCK_TIMEOUT_SECONDS = 2;

    public String name;
    public String category;
    public boolean enabled;
    public double centerX, centerY, centerZ, range;
    public String sequenceName;
    public boolean stopOnExit;
    public int loopCount; // -1 for infinite, 0 for disabled, >0 for specific count
    public int cooldownSeconds; // Cooldown in seconds after execution
    public boolean runOncePerEntry;
    public boolean antiStuckEnabled;
    public int antiStuckTimeoutSeconds;
    public boolean visualizeRange;
    public String visualizeBorderColor;

    // 运行时状态 (不会被保存到JSON)
    public transient boolean hasBeenTriggered = false;
    public transient long cooldownUntil = 0;
    public transient boolean wasPlayerInRangeLastTick = false;

    public ConditionalRule() {
        // 默认值
        this.name = I18n.format("rule.conditional.default_name");
        this.category = "默认";
        this.enabled = true;
        this.centerX = 0;
        this.centerY = 0;
        this.centerZ = 0;
        this.range = 10.0;
        this.sequenceName = "";
        this.stopOnExit = true;
        this.loopCount = 1; // 默认执行一次，允许用户手动改成其他次数
        this.cooldownSeconds = 30; // 默认30秒冷却
        this.runOncePerEntry = false; // 默认可以重复触发
        this.antiStuckEnabled = false;
        this.antiStuckTimeoutSeconds = DEFAULT_ANTI_STUCK_TIMEOUT_SECONDS;
        this.visualizeRange = false;
        this.visualizeBorderColor = DEFAULT_VISUALIZE_BORDER_COLOR;
    }

    public boolean isInRange(double playerX, double playerY, double playerZ) {
        double distanceSq = 0.0D;
        int coordCount = 0;

        if (!Double.isNaN(centerX)) {
            double dx = playerX - centerX;
            distanceSq += dx * dx;
            coordCount++;
        }
        if (!Double.isNaN(centerY)) {
            double dy = playerY - centerY;
            distanceSq += dy * dy;
            coordCount++;
        }
        if (!Double.isNaN(centerZ)) {
            double dz = playerZ - centerZ;
            distanceSq += dz * dz;
            coordCount++;
        }

        return coordCount == 0 || distanceSq <= range * range;
    }

    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownUntil;
    }

    public long getCooldownRemainingMs() {
        return Math.max(0L, cooldownUntil - System.currentTimeMillis());
    }

    public double getCooldownRemainingSeconds() {
        return getCooldownRemainingMs() / 1000.0D;
    }

    public void startCooldown() {
        this.cooldownUntil = System.currentTimeMillis() + (cooldownSeconds * 1000L);
    }

    public void resetTrigger() {
        this.hasBeenTriggered = false;
    }

    public void normalize() {
        name = name == null ? I18n.format("rule.conditional.default_name") : name.trim();
        category = category == null ? "默认" : category.trim();
        sequenceName = sequenceName == null ? "" : sequenceName.trim();
        range = Math.max(0.0D, range);
        cooldownSeconds = Math.max(0, cooldownSeconds);
        antiStuckTimeoutSeconds = Math.max(1, antiStuckTimeoutSeconds);
        visualizeBorderColor = normalizeColor(visualizeBorderColor);
    }

    public int getAntiStuckTimeoutTicks() {
        return Math.max(1, antiStuckTimeoutSeconds) * 20;
    }

    public static String normalizeColor(String rawColor) {
        String normalized = rawColor == null ? "" : rawColor.trim().toUpperCase();
        if (normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() == 3) {
            normalized = new StringBuilder()
                    .append(normalized.charAt(0)).append(normalized.charAt(0))
                    .append(normalized.charAt(1)).append(normalized.charAt(1))
                    .append(normalized.charAt(2)).append(normalized.charAt(2))
                    .toString();
        }
        if (!normalized.matches("[0-9A-F]{6}")) {
            return DEFAULT_VISUALIZE_BORDER_COLOR;
        }
        return "#" + normalized;
    }

    public int getVisualizeBorderColorRgb() {
        String normalized = normalizeColor(visualizeBorderColor);
        return Integer.parseInt(normalized.substring(1), 16) & 0xFFFFFF;
    }
}
