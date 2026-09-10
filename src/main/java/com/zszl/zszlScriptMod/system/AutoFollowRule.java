// !! NEW FILE !!
package com.zszl.zszlScriptMod.system;

import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AutoFollowRule {
    public static final int DEFAULT_RETURN_STAY_MILLIS = 3000;
    public static final int DEFAULT_PATROL_STUCK_RESTART_SECONDS = 2;
    public static final double DEFAULT_RETURN_ARRIVE_DISTANCE = 1.0;
    public static final double DEFAULT_MONSTER_VERTICAL_RANGE = 5.0;
    public static final double DEFAULT_MONSTER_UPWARD_RANGE = 3.0;
    public static final double DEFAULT_MONSTER_DOWNWARD_RANGE = 8.0;
    public static final int DEFAULT_MONSTER_CHASE_Y_LIMIT = 2;
    public static final double DEFAULT_MONSTER_STOP_DISTANCE = 1.1;
    public static final double DEFAULT_MONSTER_FIXED_DISTANCE = 3.0;
    public static final double DEFAULT_LOCK_CHASE_OUT_OF_BOUNDS_DISTANCE = 10.0;
    public static final String PATROL_MODE_ORDER = "ORDER";
    public static final String PATROL_MODE_RANDOM = "RANDOM";
    public static final String MONSTER_CHASE_MODE_APPROACH = "APPROACH";
    public static final String MONSTER_CHASE_MODE_FIXED_DISTANCE = "FIXED_DISTANCE";
    public static final String ENTITY_TYPE_MONSTER = "monster";
    public static final String ENTITY_TYPE_BOSS = "boss";
    public static final String ENTITY_TYPE_GOLEM = "golem";
    public static final String ENTITY_TYPE_NEUTRAL = "neutral";
    public static final String ENTITY_TYPE_ANIMAL = "animal";
    public static final String ENTITY_TYPE_WATER = "water";
    public static final String ENTITY_TYPE_AMBIENT = "ambient";
    public static final String ENTITY_TYPE_VILLAGER = "villager";
    public static final String ENTITY_TYPE_TAMEABLE = "tameable";
    public static final String ENTITY_TYPE_PLAYER = "player";
    public static final String ENTITY_TYPE_LIVING = "living";
    public static final String ENTITY_TYPE_ANY = "any";
    private static final List<String> KNOWN_ENTITY_TYPES = Arrays.asList(
            ENTITY_TYPE_MONSTER,
            ENTITY_TYPE_BOSS,
            ENTITY_TYPE_GOLEM,
            ENTITY_TYPE_NEUTRAL,
            ENTITY_TYPE_ANIMAL,
            ENTITY_TYPE_WATER,
            ENTITY_TYPE_AMBIENT,
            ENTITY_TYPE_VILLAGER,
            ENTITY_TYPE_TAMEABLE,
            ENTITY_TYPE_PLAYER,
            ENTITY_TYPE_LIVING,
            ENTITY_TYPE_ANY);

    public String name;
    public String category;
    public boolean enabled; // 标记此规则是否为当前激活的规则
    public AutoFollowHandler.Point point1;
    public AutoFollowHandler.Point point2;
    public AutoFollowHandler.Point point3;
    public List<AutoFollowHandler.Point> returnPoints;
    public int returnStayMillis;
    public int patrolStuckRestartSeconds;
    public double returnArriveDistance;
    public String patrolMode;
    public double monsterVerticalRange; // 旧版兼容字段
    public double monsterUpwardRange;
    public double monsterDownwardRange;
    /**
     * The maximum number of whole blocks a chase destination may differ from the
     * target's feet Y. Zero keeps destination selection on the target's layer.
     */
    public Integer monsterChaseYLimit;
    public String monsterChaseMode;
    public double monsterStopDistance;
    public double monsterFixedDistance;
    public List<String> entityTypes;
    public boolean enableMonsterNameList;
    public List<String> monsterWhitelistNames;
    public List<String> monsterBlacklistNames;
    public boolean targetInvisibleMonsters;
    public boolean targetSpecialMobs;
    public double lockChaseOutOfBoundsDistance;
    public double maxRecoveryDistance;
    public boolean runSequenceWhenOutOfRecoveryRange;
    public String outOfRangeSequenceName;
    public boolean visualizeRange;
    public boolean visualizeLockChaseRadius;

    // transient 关键字确保这些字段不会被GSON保存到JSON文件中，它们是运行时计算的
    public transient double minX, maxX, minZ, maxZ;

    public AutoFollowRule() {
        // 为新规则提供默认值
        this.name = I18n.format("rule.auto_follow.default_name");
        this.category = "默认";
        this.enabled = false;
        this.point1 = new AutoFollowHandler.Point(0, 0);
        this.point2 = new AutoFollowHandler.Point(0, 0);
        this.point3 = new AutoFollowHandler.Point(0, 0);
        this.returnPoints = new ArrayList<>();
        this.returnStayMillis = DEFAULT_RETURN_STAY_MILLIS;
        this.patrolStuckRestartSeconds = DEFAULT_PATROL_STUCK_RESTART_SECONDS;
        this.returnArriveDistance = DEFAULT_RETURN_ARRIVE_DISTANCE;
        this.patrolMode = PATROL_MODE_ORDER;
        this.monsterVerticalRange = DEFAULT_MONSTER_VERTICAL_RANGE;
        this.monsterUpwardRange = DEFAULT_MONSTER_UPWARD_RANGE;
        this.monsterDownwardRange = DEFAULT_MONSTER_DOWNWARD_RANGE;
        this.monsterChaseYLimit = DEFAULT_MONSTER_CHASE_Y_LIMIT;
        this.monsterChaseMode = MONSTER_CHASE_MODE_APPROACH;
        this.monsterStopDistance = DEFAULT_MONSTER_STOP_DISTANCE;
        this.monsterFixedDistance = DEFAULT_MONSTER_FIXED_DISTANCE;
        this.enableMonsterNameList = false;
        this.monsterWhitelistNames = new ArrayList<>();
        this.monsterBlacklistNames = new ArrayList<>();
        this.targetInvisibleMonsters = false;
        this.targetSpecialMobs = true;
        this.entityTypes = createDefaultEntityTypes(this.targetSpecialMobs);
        this.lockChaseOutOfBoundsDistance = DEFAULT_LOCK_CHASE_OUT_OF_BOUNDS_DISTANCE;
        this.maxRecoveryDistance = 500.0;
        this.runSequenceWhenOutOfRecoveryRange = false;
        this.outOfRangeSequenceName = "";
        this.visualizeRange = false;
        this.visualizeLockChaseRadius = false;
        updateBounds();
    }

    /**
     * 根据点1和点2计算边界。
     */
    public void updateBounds() {
        if (point1 == null) {
            point1 = new AutoFollowHandler.Point(0, 0);
        }
        if (point2 == null) {
            point2 = new AutoFollowHandler.Point(0, 0);
        }
        this.minX = Math.min(point1.x, point2.x);
        this.maxX = Math.max(point1.x, point2.x);
        this.minZ = Math.min(point1.z, point2.z);
        this.maxZ = Math.max(point1.z, point2.z);
        ensureReturnPoints();
    }

    public void ensureReturnPoints() {
        if (point1 == null) {
            point1 = new AutoFollowHandler.Point(0, 0);
        }
        if (point2 == null) {
            point2 = new AutoFollowHandler.Point(0, 0);
        }
        if (point3 == null) {
            point3 = new AutoFollowHandler.Point(point1.x, point1.z);
        }
        boolean legacyPointOnlyRule = returnPoints == null;
        if (returnPoints == null) {
            returnPoints = new ArrayList<>();
        }

        List<AutoFollowHandler.Point> sanitized = new ArrayList<>();
        for (AutoFollowHandler.Point point : returnPoints) {
            if (point != null && isPointWithinBounds(point)) {
                sanitized.add(copyPoint(point));
            }
        }

        if (legacyPointOnlyRule && sanitized.isEmpty() && point3 != null && isPointWithinBounds(point3)
                && (Math.abs(point3.x - point1.x) > 0.01 || Math.abs(point3.z - point1.z) > 0.01)) {
            sanitized.add(copyPoint(point3));
        }

        returnPoints.clear();
        returnPoints.addAll(sanitized);

        if (returnStayMillis <= 0) {
            returnStayMillis = DEFAULT_RETURN_STAY_MILLIS;
        }
        if (patrolStuckRestartSeconds <= 0) {
            patrolStuckRestartSeconds = DEFAULT_PATROL_STUCK_RESTART_SECONDS;
        }
        if (returnArriveDistance <= 0) {
            returnArriveDistance = DEFAULT_RETURN_ARRIVE_DISTANCE;
        }
        if (!PATROL_MODE_RANDOM.equalsIgnoreCase(patrolMode)) {
            patrolMode = PATROL_MODE_ORDER;
        } else {
            patrolMode = PATROL_MODE_RANDOM;
        }
        if (monsterVerticalRange <= 0) {
            monsterVerticalRange = DEFAULT_MONSTER_VERTICAL_RANGE;
        }
        if (monsterUpwardRange <= 0) {
            monsterUpwardRange = monsterVerticalRange > 0
                    ? Math.min(DEFAULT_MONSTER_DOWNWARD_RANGE, monsterVerticalRange)
                    : DEFAULT_MONSTER_UPWARD_RANGE;
        }
        if (monsterDownwardRange <= 0) {
            monsterDownwardRange = monsterVerticalRange > 0
                    ? Math.max(DEFAULT_MONSTER_UPWARD_RANGE, monsterVerticalRange)
                    : DEFAULT_MONSTER_DOWNWARD_RANGE;
        }
        if (monsterChaseYLimit == null || monsterChaseYLimit < 0) {
            monsterChaseYLimit = DEFAULT_MONSTER_CHASE_Y_LIMIT;
        }
        if (monsterStopDistance <= 0) {
            monsterStopDistance = DEFAULT_MONSTER_STOP_DISTANCE;
        }
        if (!MONSTER_CHASE_MODE_FIXED_DISTANCE.equalsIgnoreCase(monsterChaseMode)) {
            monsterChaseMode = MONSTER_CHASE_MODE_APPROACH;
        } else {
            monsterChaseMode = MONSTER_CHASE_MODE_FIXED_DISTANCE;
        }
        if (monsterFixedDistance <= 0) {
            monsterFixedDistance = DEFAULT_MONSTER_FIXED_DISTANCE;
        }
        if (entityTypes == null || entityTypes.isEmpty()) {
            entityTypes = createDefaultEntityTypes(targetSpecialMobs);
        } else {
            entityTypes = sanitizeEntityTypes(entityTypes);
            if (entityTypes.isEmpty()) {
                entityTypes = createDefaultEntityTypes(targetSpecialMobs);
            }
        }
        if (monsterWhitelistNames == null) {
            monsterWhitelistNames = new ArrayList<>();
        }
        if (monsterBlacklistNames == null) {
            monsterBlacklistNames = new ArrayList<>();
        }
        if (lockChaseOutOfBoundsDistance <= 0) {
            lockChaseOutOfBoundsDistance = DEFAULT_LOCK_CHASE_OUT_OF_BOUNDS_DISTANCE;
        }
        sanitizeNameList(monsterWhitelistNames);
        sanitizeNameList(monsterBlacklistNames);

        syncPrimaryReturnPoint();
    }

    private static List<String> createDefaultEntityTypes(boolean includeSpecial) {
        List<String> defaults = new ArrayList<>();
        defaults.add(ENTITY_TYPE_MONSTER);
        if (includeSpecial) {
            defaults.add(ENTITY_TYPE_BOSS);
            defaults.add(ENTITY_TYPE_GOLEM);
        }
        return defaults;
    }

    private static List<String> sanitizeEntityTypes(List<String> values) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return cleaned;
        }
        for (String value : values) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty() && KNOWN_ENTITY_TYPES.contains(normalized) && !containsIgnoreCase(cleaned, normalized)) {
                cleaned.add(normalized);
            }
        }
        return cleaned;
    }

    public boolean isPointWithinBounds(AutoFollowHandler.Point point) {
        if (point == null) {
            return false;
        }
        return point.x >= minX && point.x <= maxX
                && point.z >= minZ && point.z <= maxZ;
    }

    public AutoFollowHandler.Point getPrimaryReturnPoint() {
        ensureReturnPoints();
        return returnPoints.isEmpty() ? null : copyPoint(returnPoints.get(0));
    }

    public void syncPrimaryReturnPoint() {
        if (returnPoints == null || returnPoints.isEmpty()) {
            return;
        }
        point3 = copyPoint(returnPoints.get(0));
    }

    private void sanitizeNameList(List<String> values) {
        if (values == null) {
            return;
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String normalized = KillAuraHandler.normalizeFilterName(value);
            if (!normalized.isEmpty() && !containsIgnoreCase(cleaned, normalized)) {
                cleaned.add(normalized);
            }
        }
        values.clear();
        values.addAll(cleaned);
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

    public static AutoFollowHandler.Point copyPoint(AutoFollowHandler.Point source) {
        if (source == null) {
            return new AutoFollowHandler.Point(0, 0);
        }
        return new AutoFollowHandler.Point(source.x, source.y, source.z);
    }
}
