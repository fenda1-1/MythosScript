package com.zszl.zszlScriptMod.system;

import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

public class AutoEscapeRule {
    public static final double DEFAULT_DETECTION_RANGE = 8.0D;
    public static final int DEFAULT_RESTART_DELAY_SECONDS = 10;
    public static final String PLAYER_GAME_MODE_ALL = "all";
    public static final String PLAYER_GAME_MODE_SURVIVAL = "survival";
    public static final String PLAYER_GAME_MODE_CREATIVE = "creative";
    public static final String PLAYER_GAME_MODE_SPECTATOR = "spectator";
    public static final String PLAYER_GAME_MODE_UNKNOWN = "unknown";

    public String name;
    public String category;
    public boolean enabled;
    public List<String> entityTypes;
    public double detectionRange;

    public boolean enableNameWhitelist;
    public List<String> nameWhitelist;

    public boolean enableNameBlacklist;
    public List<String> nameBlacklist;

    public boolean enableAreaBlacklist;
    public List<AreaBlacklistEntry> areaBlacklist;

    public boolean enablePlayerGameModeFilter;
    public String playerGameModeFilter;

    public String escapeSequenceName;

    public boolean restartEnabled;
    public int restartDelaySeconds;
    public String restartSequenceName;
    public boolean ignoreTargetsUntilRestartComplete;

    // 运行时状态（不持久化）
    public transient boolean triggerLatched = false;

    public AutoEscapeRule() {
        this.name = defaultName();
        this.category = "默认";
        this.enabled = true;

        this.entityTypes = new ArrayList<>();
        this.entityTypes.add("player");
        this.entityTypes.add("monster");

        this.detectionRange = DEFAULT_DETECTION_RANGE;

        this.enableNameWhitelist = false;
        this.nameWhitelist = new ArrayList<>();

        this.enableNameBlacklist = false;
        this.nameBlacklist = new ArrayList<>();

        this.enableAreaBlacklist = false;
        this.areaBlacklist = new ArrayList<>();

        this.enablePlayerGameModeFilter = false;
        this.playerGameModeFilter = PLAYER_GAME_MODE_ALL;

        this.escapeSequenceName = "";

        this.restartEnabled = false;
        this.restartDelaySeconds = DEFAULT_RESTART_DELAY_SECONDS;
        this.restartSequenceName = "";
        this.ignoreTargetsUntilRestartComplete = false;
    }

    public void ensureLists() {
        if (entityTypes == null) {
            entityTypes = new ArrayList<>();
        }
        if (nameWhitelist == null) {
            nameWhitelist = new ArrayList<>();
        }
        if (nameBlacklist == null) {
            nameBlacklist = new ArrayList<>();
        }
        if (areaBlacklist == null) {
            areaBlacklist = new ArrayList<>();
        }
    }

    public void normalize() {
        if (name == null || name.trim().isEmpty()) {
            name = defaultName();
        } else {
            name = name.trim();
        }

        category = category == null || category.trim().isEmpty() ? "默认" : category.trim();
        detectionRange = detectionRange <= 0 ? DEFAULT_DETECTION_RANGE : detectionRange;
        restartDelaySeconds = Math.max(0, restartDelaySeconds);

        escapeSequenceName = escapeSequenceName == null ? "" : escapeSequenceName.trim();
        restartSequenceName = restartSequenceName == null ? "" : restartSequenceName.trim();
        playerGameModeFilter = normalizePlayerGameModeFilter(playerGameModeFilter);

        ensureLists();
        entityTypes = sanitizeStringList(entityTypes);
        nameWhitelist = sanitizeStringList(nameWhitelist);
        nameBlacklist = sanitizeStringList(nameBlacklist);
        areaBlacklist = sanitizeAreaBlacklist(areaBlacklist);
    }

    private static String defaultName() {
        try {
            return I18n.format("rule.auto_escape.default_name");
        } catch (RuntimeException ignored) {
            return "自动逃离规则";
        }
    }

    public void resetRuntimeState() {
        this.triggerLatched = false;
    }

    public AutoEscapeRule copy() {
        AutoEscapeRule copy = new AutoEscapeRule();
        copy.name = this.name;
        copy.category = this.category;
        copy.enabled = this.enabled;
        copy.entityTypes = new ArrayList<>(this.entityTypes == null ? new ArrayList<String>() : this.entityTypes);
        copy.detectionRange = this.detectionRange;
        copy.enableNameWhitelist = this.enableNameWhitelist;
        copy.nameWhitelist = new ArrayList<>(this.nameWhitelist == null ? new ArrayList<String>() : this.nameWhitelist);
        copy.enableNameBlacklist = this.enableNameBlacklist;
        copy.nameBlacklist = new ArrayList<>(this.nameBlacklist == null ? new ArrayList<String>() : this.nameBlacklist);
        copy.enableAreaBlacklist = this.enableAreaBlacklist;
        copy.areaBlacklist = copyAreaBlacklist(this.areaBlacklist);
        copy.enablePlayerGameModeFilter = this.enablePlayerGameModeFilter;
        copy.playerGameModeFilter = this.playerGameModeFilter;
        copy.escapeSequenceName = this.escapeSequenceName;
        copy.restartEnabled = this.restartEnabled;
        copy.restartDelaySeconds = this.restartDelaySeconds;
        copy.restartSequenceName = this.restartSequenceName;
        copy.ignoreTargetsUntilRestartComplete = this.ignoreTargetsUntilRestartComplete;
        copy.triggerLatched = this.triggerLatched;
        copy.normalize();
        return copy;
    }

    public static String normalizePlayerGameModeFilter(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        switch (normalized) {
            case "生存":
            case "冒险":
            case "adventure":
            case PLAYER_GAME_MODE_SURVIVAL:
                return PLAYER_GAME_MODE_SURVIVAL;
            case "创造":
            case PLAYER_GAME_MODE_CREATIVE:
                return PLAYER_GAME_MODE_CREATIVE;
            case "旁观":
            case PLAYER_GAME_MODE_SPECTATOR:
                return PLAYER_GAME_MODE_SPECTATOR;
            case "未知":
            case PLAYER_GAME_MODE_UNKNOWN:
                return PLAYER_GAME_MODE_UNKNOWN;
            default:
                return PLAYER_GAME_MODE_ALL;
        }
    }

    private static List<String> sanitizeStringList(List<String> source) {
        List<String> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (String value : source) {
            String trimmed = value == null ? "" : value.trim();
            if (!trimmed.isEmpty() && !containsIgnoreCase(result, trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<AreaBlacklistEntry> sanitizeAreaBlacklist(List<AreaBlacklistEntry> source) {
        List<AreaBlacklistEntry> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (AreaBlacklistEntry entry : source) {
            if (entry == null) {
                continue;
            }
            AreaBlacklistEntry normalized = entry.copy();
            if (normalized.areaKey.isEmpty()) {
                continue;
            }
            if (!containsAreaEntry(result, normalized.areaKey, normalized.chunkRadius)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static List<AreaBlacklistEntry> copyAreaBlacklist(List<AreaBlacklistEntry> source) {
        List<AreaBlacklistEntry> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (AreaBlacklistEntry entry : source) {
            if (entry != null) {
                copy.add(entry.copy());
            }
        }
        return copy;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAreaEntry(List<AreaBlacklistEntry> values, String areaKey, int chunkRadius) {
        if (values == null) {
            return false;
        }
        for (AreaBlacklistEntry value : values) {
            if (value != null
                    && value.chunkRadius == chunkRadius
                    && value.areaKey.equalsIgnoreCase(areaKey)) {
                return true;
            }
        }
        return false;
    }

    public static class AreaBlacklistEntry {
        public String areaKey;
        public int chunkRadius;

        public AreaBlacklistEntry() {
            this("", 0);
        }

        public AreaBlacklistEntry(String areaKey, int chunkRadius) {
            this.areaKey = areaKey == null ? "" : areaKey;
            this.chunkRadius = chunkRadius;
            normalize();
        }

        public void normalize() {
            areaKey = normalizeAreaKey(areaKey);
            chunkRadius = Math.max(0, chunkRadius);
        }

        public AreaBlacklistEntry copy() {
            return new AreaBlacklistEntry(areaKey, chunkRadius);
        }
    }

    public static String normalizeAreaKey(String value) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replace('，', ',').replace('：', ':');
        normalized = normalized.replace(" ", "");
        return normalized;
    }

    public static String areaKey(int dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    /** Converts a world pick (block center + block radius) into the stored chunk exclusion. */
    public static AreaBlacklistEntry fromBlockRadius(int dimension, int blockX, int blockZ, double radiusBlocks) {
        int chunkRadius = (int) Math.round(Math.max(0.0D, radiusBlocks) / 16.0D);
        return new AreaBlacklistEntry(areaKey(dimension, blockX >> 4, blockZ >> 4), Math.max(0, chunkRadius));
    }
}
