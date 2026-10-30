package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.handlers.FlyHandler;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MovementFeatureManager {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        private final LiquidWalkSettings LIQUID_WALK_SETTINGS = new LiquidWalkSettings();
        // 每个功能的数值和状态 HUD 开关是配置，按会话分开存；FeatureState 本身是共享的注册表项。
        private final Map<String, Float> FEATURE_VALUES = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Boolean> FEATURE_STATUS_HUD = new java.util.concurrent.ConcurrentHashMap<>();
        private boolean masterStatusHudEnabled = true;
        private int masterStatusHudX = 6;
        private int masterStatusHudY = 6;
        private boolean wallClimbWebEnabled = true;
        private float wallClimbWebSpeed = 1.0F;
    }

    /** 当前线程所属会话的配置；bot 线程取自己的，客户端线程取挂载中的会话。 */
    public static OwnerConfig cfg() {
        String owner = com.mythos.mythosScriptMod.system.ProfileManager.currentConfigOwner();
        OwnerConfig config = CONFIGS.get(owner);
        return config != null ? config : loadOwnerConfig(owner);
    }

    // 不能用 computeIfAbsent：读盘过程中会反复调用 cfg()，ConcurrentHashMap 不允许递归更新。
    private static OwnerConfig loadOwnerConfig(String owner) {
        synchronized (com.mythos.mythosScriptMod.system.ProfileManager.CONFIG_LOAD_LOCK) {
            OwnerConfig existing = CONFIGS.get(owner);
            if (existing != null) {
                return existing;
            }
            OwnerConfig config = new OwnerConfig();
            CONFIGS.put(owner, config);
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) MovementFeatureManager::loadConfig);
            return config;
        }
    }


    public static final MovementFeatureManager INSTANCE = new MovementFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_movement.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();

    private static String featureOwner() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner();
    }


    public static boolean isWallClimbWebEnabled() { return cfg().wallClimbWebEnabled; }
    public static float getWallClimbWebSpeed() { return cfg().wallClimbWebSpeed; }

    public static void setWallClimbWebEnabled(boolean enabled) {
        cfg().wallClimbWebEnabled = enabled;
        if (!enabled) WallClimbFeatureHandler.cancelWeb();
        saveConfig();
    }

    public static void setWallClimbWebSpeed(float speed) {
        cfg().wallClimbWebSpeed = Float.isFinite(speed) ? MathHelper.clamp(speed, 0.1F, 5.0F) : 1.0F;
        saveConfig();
    }

    private static void resetWallClimbSettings() {
        cfg().wallClimbWebEnabled = true;
        cfg().wallClimbWebSpeed = 1.0F;
        WallClimbFeatureHandler.reset();
    }

    static final float DEFAULT_STEP_HEIGHT = 0.6F;
    static final float DEFAULT_COLLISION_REDUCTION = 0.0F;
    static final float DEFAULT_TIMER_SPEED = 1.00F;

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    static RuntimeState rt() {
        return STATES.computeIfAbsent(
                com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    static final class RuntimeState {
        boolean externalTimerApplied = false;
        float lastTimerSpeed = DEFAULT_TIMER_SPEED;
        int blockPhaseStuckTicks = 0;
        int longJumpChargeTicks = 0;
        int longJumpCooldownTicks = 0;
        int longJumpBoostTicks = 0;
        double longJumpBoostSpeed = 0.0D;
        boolean wasLongJumpSneakDown = false;
        int blinkCooldownTicks = 0;
        boolean wasBlinkTriggerDown = false;
        int scaffoldPlaceCooldownTicks = 0;
        int obstacleAvoidDirection = 0;
        int obstacleAvoidTicks = 0;
    }

    static {
        register(new FeatureState("no_slow", "不受减速",
                "尽量维持使用物品、拉弓或进食时的基础移动速度，减少明显拖慢。左键快速开关，右键打开移动设置。",
                "保速系数", 0.85F, 0.40F, 1.20F, true));
        register(new FeatureState("force_sprint", "强制疾跑",
                "只要有前进输入就持续尝试进入疾跑状态，减少手动补按疾跑键。左键快速开关，右键打开移动设置。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("anti_knockback", "防击退",
                "抵消收到的水平和垂直击退；防击退系数 1 为完全抵消，0 为不抵消。",
                "防击退系数", 1.0F, 0.0F, 1.0F, true));
        register(new FeatureState("gui_move", "GUI界面下移动",
                "在大多数 GUI 界面保持 WASD、跳跃和下蹲输入可用。聊天输入框默认不接管。左键快速开关，右键打开移动设置。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("auto_step", "自动台阶",
                "自动抬升 1 到 100 格台阶，减少卡在小坡、半砖或高差障碍上的情况。仅支持整数格。左键快速开关，右键打开移动设置。",
                "台阶高度", 1.00F, 1.00F, 100.00F, true));
        register(new FeatureState("wall_climb", "爬墙",
                "贴墙前进即可吸附，天花板 WASD 跟随视角，可爬上高一格的天花板。中键吐丝牵引至准星表面，再按中键取消；空格蹬离，潜行松开。右键配置行走速度、吐丝开关和吸附速度。",
                "行走速度", 0.20F, 0.05F, 0.50F, true));
        register(new FeatureState("freecam", "灵魂出窍",
                "独立幽灵视角，可查看本体与世界，不中断本体寻路。WASD 移动镜头，空格上升，潜行下降，疾跑键加速；关闭后回到本体。仅查看已加载区块，出窍期间禁止鼠标交互。",
                "飞行速度", 0.60F, 0.10F, 5.00F, true));
        register(new FeatureState("block_phase", "方块穿透",
                "卡进实体方块边缘或持续顶墙时，自动寻找最近安全偏移并轻量脱困，目标是减少卡住而不是粗暴穿墙。左键快速开关，右键打开移动设置。",
                "脱困强度", 0.12F, 0.05F, 0.40F, true));
        register(new FeatureState("no_collision", "无碰撞",
                "取消客户端实体阻挡与推挤，保留方块、墙壁和地板碰撞；服务器仍可校正位置。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("long_jump", "长距离跳跃",
                "地面移动时按住潜行蓄力，松开潜行后释放一次长跳。配置值决定蓄满所需时间，蓄力越久跳得越远。左键快速开关，右键打开移动设置。",
                "蓄力秒数", 1.20F, 0.20F, 3.00F, true));
        register(new FeatureState("timer_accel", "定时器",
                "独立调整客户端游戏时钟速度，加快整体 tick 节奏。若加速功能已接管 Timer，则优先使用加速模块的 Timer。左键快速开关，右键打开移动设置。",
                "Timer倍率", 1.10F, 1.00F, 12.50F, true));
        register(new FeatureState("blink_move", "闪烁移动",
                "按住潜行并轻点疾跑键，沿当前移动方向执行一次短距闪步。会自动寻找安全落点并附带很轻的前冲惯性。左键快速开关，右键打开移动设置。",
                "闪烁距离", 3.00F, 1.00F, 8.00F, true));
        register(new FeatureState("safe_walk", "安全行走",
                "地面移动时模拟原版按住潜行时的贴边保护，但不压低正常走路速度，尽量避免直接踏出无支撑边缘。按跳跃时不会拦截，方便主动冲刺跳。左键快速开关，右键打开移动设置。",
                "边缘预留", 0.35F, 0.10F, 1.00F, true));
        register(new FeatureState("scaffold", "脚手架",
                "边走边自动在脚下与前方空隙补放热栏中的完整方块，用于过桥、补坑和短距搭路。左键快速开关，右键打开移动设置。",
                "铺路距离", 1.00F, 1.00F, 4.00F, true));
        register(new FeatureState("low_gravity", "低重力模式",
                "降低下落加速度，让空中下落更慢、落点更容易控制。左键快速开关，右键打开移动设置。",
                "下落系数", 0.72F, 0.45F, 0.98F, true));
        register(new FeatureState("ice_boost", "冰面加速",
                "站在冰、浮冰或蓝冰上时额外补一点水平速度。左键快速开关，右键打开移动设置。",
                "冰面倍率", 1.25F, 1.00F, 2.20F, true));
        register(new FeatureState("lava_walk", "液体行走",
                "将水、岩浆等液体表面临时当作可踩踏平台，并在贴近表面时自动托举，尽量保持不会沉入各种液体。左键快速开关，右键打开移动设置。",
                "托举力度", 0.90F, 0.40F, 1.50F, true));
        register(new FeatureState("auto_obstacle_avoid", "自动避障",
                "前方短距离出现障碍时自动轻微侧移绕开，减少直冲墙角、门框和单格柱体时的卡顿。左键快速开关，右键打开移动设置。",
                "探测距离", 1.50F, 0.50F, 4.00F, true));
        register(new FeatureState("hover_mode", "悬停模式",
                "空中时在不按跳跃和下蹲的情况下尝试保持原地悬停，适合短暂停留与观察。左键快速开关，右键打开移动设置。",
                "垂直漂移", 0.00F, -0.08F, 0.08F, true));
        register(new FeatureState("fall_cushion", "下落缓冲",
                "快速下落接近地面时自动削弱下坠速度，减少落地过猛的手感。左键快速开关，右键打开移动设置。",
                "缓冲速度", 0.24F, 0.10F, 0.60F, true));
        register(new FeatureState("no_fall", "无摔伤",
                "开启后持续发送落地状态并清除本地摔落距离；服务器独立校验时可能仍受伤。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("anti_arrow_knockback", "反击飞",
                "抵消箭矢、受击和爆炸的水平与垂直速度；1 为完全抵消，0 为不抵消。",
                "抵消强度", 1.0F, 0.0F, 1.0F, true));

        loadConfig();
    }

    private MovementFeatureManager() {
    }

    public static final class FeatureState {
        public final String id;
        public final String name;
        public final String description;
        public final String valueLabel;
        public final float defaultValue;
        public final float minValue;
        public final float maxValue;
        public final boolean behaviorImplemented;

        private final ConcurrentHashMap<String, Boolean> enabledByOwner = new ConcurrentHashMap<>();

        private FeatureState(String id, String name, String description, String valueLabel,
                float defaultValue, float minValue, float maxValue, boolean behaviorImplemented) {
            this.id = safe(id);
            this.name = safe(name);
            this.description = safe(description);
            this.valueLabel = valueLabel == null ? "" : valueLabel.trim();
            this.defaultValue = defaultValue;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.behaviorImplemented = behaviorImplemented;
        }

        public boolean supportsValue() {
            return !valueLabel.isEmpty() && maxValue > minValue;
        }

        public boolean usesIntegerValue() {
            return "auto_step".equals(id);
        }

        public boolean isEnabled() {
            return enabledByOwner.getOrDefault(featureOwner(), Boolean.FALSE);
        }

        public float getValue() {
            return cfg().FEATURE_VALUES.getOrDefault(id, defaultValue);
        }

        public boolean isStatusHudEnabled() {
            return cfg().FEATURE_STATUS_HUD.getOrDefault(id, Boolean.TRUE);
        }

        private void setEnabledInternal(boolean enabled) {
            enabledByOwner.put(featureOwner(), enabled);
            if (!enabled && "freecam".equals(id)) {
                FreecamFeatureHandler.stop();
            }
            if (!enabled && "wall_climb".equals(id)) {
                WallClimbFeatureHandler.reset();
            }
        }

        /** Load-time only: seed the per-owner map from the runtime file —
         *  unlike setEnabledInternal this does not touch feature state. */
        private void setEnabledForOwner(String owner, boolean enabled) {
            enabledByOwner.put(owner == null ? "" : owner, enabled);
        }

        private java.util.Map<String, Boolean> snapshotEnabledByOwner() {
            return new java.util.LinkedHashMap<>(enabledByOwner);
        }

        private void setValueInternal(float value) {
            if (!supportsValue()) {
                cfg().FEATURE_VALUES.put(id, defaultValue);
                return;
            }
            float clamped = Float.isFinite(value) ? MathHelper.clamp(value, minValue, maxValue) : defaultValue;
            cfg().FEATURE_VALUES.put(id, usesIntegerValue() ? Math.round(clamped) : clamped);
        }

        private void setStatusHudEnabledInternal(boolean statusHudEnabled) {
            cfg().FEATURE_STATUS_HUD.put(id, statusHudEnabled);
        }

        private void resetToDefaultInternal() {
            setEnabledInternal(false);
            cfg().FEATURE_VALUES.put(id, defaultValue);
            cfg().FEATURE_STATUS_HUD.put(id, true);
        }
    }

    public static final class LiquidWalkSettings {
        public static final boolean DEFAULT_WALK_ON_WATER = true;
        public static final boolean DEFAULT_DANGEROUS_ONLY = false;
        public static final boolean DEFAULT_SNEAK_TO_DESCEND = false;

        private boolean walkOnWater;
        private boolean dangerousOnly;
        private boolean sneakToDescend;

        private LiquidWalkSettings() {
            resetToDefaultInternal();
        }

        private LiquidWalkSettings(LiquidWalkSettings other) {
            if (other == null) {
                resetToDefaultInternal();
                return;
            }
            this.walkOnWater = other.walkOnWater;
            this.dangerousOnly = other.dangerousOnly;
            this.sneakToDescend = other.sneakToDescend;
        }

        public boolean isWalkOnWater() {
            return walkOnWater;
        }

        public boolean isDangerousOnly() {
            return dangerousOnly;
        }

        public boolean isSneakToDescend() {
            return sneakToDescend;
        }

        private void setInternal(boolean walkOnWater, boolean dangerousOnly, boolean sneakToDescend) {
            this.walkOnWater = walkOnWater;
            this.dangerousOnly = dangerousOnly;
            this.sneakToDescend = sneakToDescend;
        }

        private void resetToDefaultInternal() {
            this.walkOnWater = DEFAULT_WALK_ON_WATER;
            this.dangerousOnly = DEFAULT_DANGEROUS_ONLY;
            this.sneakToDescend = DEFAULT_SNEAK_TO_DESCEND;
        }
    }

    public static List<FeatureState> getFeatures() {
        return new ArrayList<>(FEATURES.values());
    }

    public static FeatureState getFeature(String featureId) {
        return FEATURES.get(normalizeId(featureId));
    }

    public static boolean isManagedFeature(String featureId) {
        return FEATURES.containsKey(normalizeId(featureId));
    }

    public static boolean isEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isEnabled();
    }

    public static void toggleFeature(String featureId) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setEnabledInternal(!state.isEnabled());
        saveConfig();
    }

    public static void setEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setEnabledInternal(enabled);
        saveConfig();
    }

    public static void setValue(String featureId, float value) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setValueInternal(value);
        saveConfig();
    }

    public static boolean isMasterStatusHudEnabled() {
        return cfg().masterStatusHudEnabled;
    }

    public static void setMasterStatusHudEnabled(boolean enabled) {
        cfg().masterStatusHudEnabled = enabled;
        saveConfig();
    }

    public static int getMasterStatusHudX() {
        return cfg().masterStatusHudX;
    }

    public static int getMasterStatusHudY() {
        return cfg().masterStatusHudY;
    }

    public static void setMasterStatusHudPositionTransient(int x, int y) {
        cfg().masterStatusHudX = Math.max(0, x);
        cfg().masterStatusHudY = Math.max(0, y);
    }


    public static void persistMasterStatusHudPosition() {
        saveConfig();
    }

    public static boolean isFeatureStatusHudEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isStatusHudEnabled();
    }

    public static boolean shouldDisplayFeatureStatusHud(String featureId) {
        return isMasterStatusHudEnabled() && isFeatureStatusHudEnabled(featureId);
    }

    public static void setFeatureStatusHudEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setStatusHudEnabledInternal(enabled);
        saveConfig();
    }

    public static void resetFeature(String featureId) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.resetToDefaultInternal();
        if ("wall_climb".equals(normalizeId(featureId))) {
            resetWallClimbSettings();
        }
        if ("lava_walk".equals(normalizeId(featureId))) {
            cfg().LIQUID_WALK_SETTINGS.resetToDefaultInternal();
        }
        saveConfig();
    }

    public static void resetAllToDefaults() {
        resetWallClimbSettings();
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        cfg().LIQUID_WALK_SETTINGS.resetToDefaultInternal();
        cfg().masterStatusHudEnabled = true;
        cfg().masterStatusHudX = 6;
        cfg().masterStatusHudY = 6;
        saveConfig();
    }

    public static LiquidWalkSettings getLiquidWalkSettings() {
        return new LiquidWalkSettings(cfg().LIQUID_WALK_SETTINGS);
    }

    public static boolean shouldLiquidWalkOnWater() {
        return cfg().LIQUID_WALK_SETTINGS.isWalkOnWater();
    }

    public static boolean isLiquidWalkDangerousOnly() {
        return cfg().LIQUID_WALK_SETTINGS.isDangerousOnly();
    }

    public static boolean shouldLiquidWalkSneakToDescend() {
        return cfg().LIQUID_WALK_SETTINGS.isSneakToDescend();
    }

    public static void setLiquidWalkSettings(boolean walkOnWater, boolean dangerousOnly, boolean sneakToDescend) {
        cfg().LIQUID_WALK_SETTINGS.setInternal(walkOnWater, dangerousOnly, sneakToDescend);
        saveConfig();
    }

    public static List<String> getStatusLines() {
        return getStatusLines(false);
    }

    public static List<String> getStatusLines(boolean forcePreview) {
        if (!forcePreview && !cfg().masterStatusHudEnabled) {
            return new ArrayList<>();
        }

        List<String> activeNames = new ArrayList<>();
        for (FeatureState state : FEATURES.values()) {
            if (state != null && state.isEnabled() && state.isStatusHudEnabled()) {
                activeNames.add(state.name);
            }
        }

        List<String> lines = new ArrayList<>();
        if (activeNames.isEmpty()) {
            return lines;
        }

        lines.add("§d[移动增强] §f" + activeNames.size() + " 项开启");
        StringBuilder builder = new StringBuilder("§7");
        for (int i = 0; i < activeNames.size() && i < 4; i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(activeNames.get(i));
        }
        if (activeNames.size() > 4) {
            builder.append(" §8+").append(activeNames.size() - 4);
        }
        lines.add(builder.toString());
        String runtime = INSTANCE.getRuntimeHudLine();
        if (!runtime.isEmpty()) {
            lines.add(runtime);
        }
        return lines;
    }

    public static String getFeatureRuntimeSummary(String featureId) {
        return INSTANCE.buildFeatureRuntimeSummary(normalizeId(featureId));
    }

    public static boolean shouldApplyVanillaSafeWalk(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(entity instanceof EntityPlayerSP) || mc == null || mc.player == null || entity != mc.player) {
            return false;
        }

        EntityPlayerSP player = (EntityPlayerSP) entity;
        return isEnabled("safe_walk")
                && player.world != null
                && !player.noClip
                && player.onGround
                && !player.capabilities.isFlying
                && !player.isRiding()
                && (player.movementInput == null || !player.movementInput.jump)
                && (mc.gameSettings == null || !mc.gameSettings.keyBindJump.isKeyDown());
    }

    public static void loadConfig() {
        resetWallClimbSettings();
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        cfg().LIQUID_WALK_SETTINGS.resetToDefaultInternal();
        cfg().masterStatusHudEnabled = true;
        cfg().masterStatusHudX = 6;
        cfg().masterStatusHudY = 6;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                saveConfig();
                return;
            }

            JsonObject root;
            try (FileReader reader = new FileReader(configFile)) {
                root = new JsonParser().parse(reader).getAsJsonObject();
            }
            if (root.has("wallClimbSettings") && root.get("wallClimbSettings").isJsonObject()) {
                JsonObject web = root.getAsJsonObject("wallClimbSettings");
                if (web.has("webEnabled")) cfg().wallClimbWebEnabled = web.get("webEnabled").getAsBoolean();
                if (web.has("webSpeed")) {
                    float speed = web.get("webSpeed").getAsFloat();
                    cfg().wallClimbWebSpeed = Float.isFinite(speed) ? MathHelper.clamp(speed, 0.1F, 5.0F) : 1.0F;
                }
            }
            JsonObject featuresObject = root.has("features") && root.get("features").isJsonObject()
                    ? root.getAsJsonObject("features")
                    : new JsonObject();
            JsonObject liquidWalkObject = root.has("liquidWalkSettings")
                    && root.get("liquidWalkSettings").isJsonObject()
                            ? root.getAsJsonObject("liquidWalkSettings")
                            : new JsonObject();
            if (root.has("masterStatusHudEnabled")) {
                cfg().masterStatusHudEnabled = root.get("masterStatusHudEnabled").getAsBoolean();
            }
            if (root.has("masterStatusHudX")) {
                cfg().masterStatusHudX = Math.max(0, root.get("masterStatusHudX").getAsInt());
            }
            if (root.has("masterStatusHudY")) {
                cfg().masterStatusHudY = Math.max(0, root.get("masterStatusHudY").getAsInt());
            }

            for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                if (!featuresObject.has(entry.getKey()) || !featuresObject.get(entry.getKey()).isJsonObject()) {
                    continue;
                }
                JsonObject item = featuresObject.getAsJsonObject(entry.getKey());
                FeatureState state = entry.getValue();
                if (item.has("value")) {
                    state.setValueInternal(item.get("value").getAsFloat());
                }
                if (item.has("statusHudEnabled")) {
                    state.setStatusHudEnabledInternal(item.get("statusHudEnabled").getAsBoolean());
                }
            }

            // Per-owner enabled flags live in the runtime file keyed by owner.
            File runtimeFile = getRuntimeConfigFile();
            if (runtimeFile.exists()) {
                try (FileReader rr = new FileReader(runtimeFile)) {
                    JsonObject rroot = new JsonParser().parse(rr).getAsJsonObject();
                    if (rroot.has("features") && rroot.get("features").isJsonObject()) {
                        JsonObject rfeatures = rroot.getAsJsonObject("features");
                        for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                            if (!rfeatures.has(entry.getKey()) || !rfeatures.get(entry.getKey()).isJsonObject()) {
                                continue;
                            }
                            JsonObject item = rfeatures.getAsJsonObject(entry.getKey());
                            if (item.has("enabledByOwner") && item.get("enabledByOwner").isJsonObject()) {
                                JsonObject owners = item.getAsJsonObject("enabledByOwner");
                                for (Map.Entry<String, com.google.gson.JsonElement> oe : owners.entrySet()) {
                                    if (oe.getValue().isJsonPrimitive()) {
                                        entry.getValue().setEnabledForOwner(oe.getKey(), oe.getValue().getAsBoolean());
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Back-compat: shared file has a single "enabled" — apply to current owner only.
                for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                    if (!featuresObject.has(entry.getKey()) || !featuresObject.get(entry.getKey()).isJsonObject()) {
                        continue;
                    }
                    JsonObject item = featuresObject.getAsJsonObject(entry.getKey());
                    if (item.has("enabled")) {
                        entry.getValue().setEnabledForOwner(featureOwner(), item.get("enabled").getAsBoolean());
                    }
                }
            }

            if (liquidWalkObject.has("walkOnWater")) {
                cfg().LIQUID_WALK_SETTINGS.walkOnWater = liquidWalkObject.get("walkOnWater").getAsBoolean();
            }
            if (liquidWalkObject.has("dangerousOnly")) {
                cfg().LIQUID_WALK_SETTINGS.dangerousOnly = liquidWalkObject.get("dangerousOnly").getAsBoolean();
            }
            if (liquidWalkObject.has("sneakToDescend")) {
                cfg().LIQUID_WALK_SETTINGS.sneakToDescend = liquidWalkObject.get("sneakToDescend").getAsBoolean();
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载移动增强配置失败", e);
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            JsonObject web = new JsonObject();
            web.addProperty("webEnabled", cfg().wallClimbWebEnabled);
            web.addProperty("webSpeed", cfg().wallClimbWebSpeed);
            root.add("wallClimbSettings", web);
            root.addProperty("masterStatusHudEnabled", cfg().masterStatusHudEnabled);
            root.addProperty("masterStatusHudX", cfg().masterStatusHudX);
            root.addProperty("masterStatusHudY", cfg().masterStatusHudY);
            JsonObject featuresObject = new JsonObject();
            for (FeatureState state : FEATURES.values()) {
                JsonObject item = new JsonObject();
                item.addProperty("statusHudEnabled", state.isStatusHudEnabled());
                if (state.supportsValue()) {
                    item.addProperty("value", state.getValue());
                }
                featuresObject.add(state.id, item);
            }
            root.add("features", featuresObject);
            JsonObject liquidWalkObject = new JsonObject();
            liquidWalkObject.addProperty("walkOnWater", cfg().LIQUID_WALK_SETTINGS.isWalkOnWater());
            liquidWalkObject.addProperty("dangerousOnly", cfg().LIQUID_WALK_SETTINGS.isDangerousOnly());
            liquidWalkObject.addProperty("sneakToDescend", cfg().LIQUID_WALK_SETTINGS.isSneakToDescend());
            root.add("liquidWalkSettings", liquidWalkObject);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }

            // Per-owner enabled flags — runtime file, one object per owner
            // key under "features.<id>.enabledByOwner".
            File runtimeFile = getRuntimeConfigFile();
            if (!runtimeFile.getParentFile().exists()) {
                runtimeFile.getParentFile().mkdirs();
            }
            JsonObject rroot = new JsonObject();
            JsonObject rfeatures = new JsonObject();
            for (FeatureState state : FEATURES.values()) {
                JsonObject owners = new JsonObject();
                for (java.util.Map.Entry<String, Boolean> e : state.snapshotEnabledByOwner().entrySet()) {
                    owners.addProperty(e.getKey() == null ? "" : e.getKey(), e.getValue());
                }
                JsonObject item = new JsonObject();
                item.add("enabledByOwner", owners);
                rfeatures.add(state.id, item);
            }
            rroot.add("features", rfeatures);
            try (FileWriter rw = new FileWriter(runtimeFile)) {
                rw.write(rroot.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存移动增强配置失败", e);
        }
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("other_features_movement.json").toFile();
    }

    public void onClientDisconnect() {
        FreecamFeatureHandler.stop();
        resetRuntimeState();
        GuiMoveFeatureHandler.onClientDisconnect();
        applyMovementFeatureProtection(Minecraft.getMinecraft().player, false, false, false);
        MovementTimerFeatureHandler.reset(this, true);
        MovementPersistentAttributeHandler.reset(Minecraft.getMinecraft().player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != mc.player || mc.player == null || mc.world == null) {
            return;
        }

        boolean movementProtectionActive = MovementFeatureManager.isEnabled("no_collision")
                || MovementFeatureManager.isEnabled("anti_knockback");
        applyMovementFeatureProtection(mc.player, movementProtectionActive,
                MovementFeatureManager.isEnabled("no_collision"),
                MovementFeatureManager.isEnabled("anti_knockback"));
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (Boolean.getBoolean("mythos.runtime.movementHooks")) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getButton() != 2 || !isEnabled("wall_climb") || !cfg().wallClimbWebEnabled
                || mc.player == null || mc.world == null || mc.currentScreen != null
                || !mc.inGameHasFocus || FreecamFeatureHandler.isActive()) return;
        // Consume both edges to prevent vanilla pick-block from sharing the gesture.
        event.setCanceled(true);
        if (event.isButtonstate()) WallClimbFeatureHandler.shootWeb(mc.player);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            FreecamFeatureHandler.tick();
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player != null && !com.mythos.mythosScriptMod.mixin.SessionFieldAccess.ownsPlayer(player)) {
            // Another session's player (Nexus bot thread vs. mounted player):
            // never drive its movement from this thread.
            return;
        }
        if (mc.world == null || player == null) {
            resetRuntimeState();
            GuiMoveFeatureHandler.onClientDisconnect();
            applyMovementFeatureProtection(player, false, false, false);
            MovementTimerFeatureHandler.reset(this, false);
            return;
        }

        tickRuntimeState();

        if (player.isDead || player.getHealth() <= 0.0F) {
            resetRuntimeState();
            GuiMoveFeatureHandler.onClientDisconnect();
            applyMovementFeatureProtection(player, false, false, false);
            MovementPersistentAttributeHandler.reset(player);
            MovementTimerFeatureHandler.reset(this, false);
            return;
        }

        if (!FreecamFeatureHandler.isActive()) GuiMoveFeatureHandler.apply(mc);
        MovementPersistentAttributeHandler.apply(player);
        MovementTimerFeatureHandler.apply(this);
        applyMovementFeatureProtection(player,
                MovementFeatureManager.isEnabled("no_collision") || MovementFeatureManager.isEnabled("anti_knockback"),
                MovementFeatureManager.isEnabled("no_collision"),
                MovementFeatureManager.isEnabled("anti_knockback"));
        NoFallFeatureHandler.apply(player);
        if (WallClimbFeatureHandler.isAttached(player)) {
            return;
        }
        BlockPhaseFeatureHandler.apply(this, player);
        ForceSprintFeatureHandler.apply(player);
        AutoObstacleAvoidFeatureHandler.apply(this, player);
        LongJumpFeatureHandler.apply(this, player);
        BlinkMoveFeatureHandler.apply(this, player);
        NoSlowFeatureHandler.apply(player);
        IceBoostFeatureHandler.apply(player);
        ScaffoldFeatureHandler.apply(this, player);
        AirMotionFeatureHandler.apply(player);
        SafeWalkFeatureHandler.apply(player);
    }

    public static boolean shouldAllowMovementDuringGui(Minecraft mc) {
        return GuiMoveFeatureHandler.shouldAllowMovementDuringGui(mc);
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (event == null || player == null || mc.world == null || event.getEntityPlayer() != player) {
            return;
        }
        GuiMoveFeatureHandler.applyMovementInput(mc, event.getMovementInput());
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void onFreecamInput(InputUpdateEvent event) {
        if (FreecamFeatureHandler.isBody(event.getEntityPlayer())) {
            FreecamFeatureHandler.clearInput(event.getMovementInput());
        } else if (com.mythos.mythosScriptMod.handlers.TeleportCompanionHandler
                .isBody(event.getEntityPlayer())) {
            com.mythos.mythosScriptMod.handlers.TeleportCompanionHandler
                    .clearInput(event.getMovementInput());
        }
    }

    @SubscribeEvent
    public void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (event == null || player == null || mc.world == null || event.getWorld() == null) {
            return;
        }
        if (event.getWorld().provider.getDimension() != mc.world.provider.getDimension()) {
            return;
        }
        if (event.getEntity() != null && event.getEntity() != player) {
            return;
        }
        LavaWalkFeatureHandler.addCollisionBoxes(event, player);
    }

    private void applyMovementFeatureProtection(EntityPlayerSP player, boolean active, boolean applyNoCollision,
            boolean applyAntiKnockback) {
        if (player == null) {
            return;
        }

        if (!active) {
            if (!KillAuraHandler.enabled() && !FlyHandler.enabled()) {
                player.entityCollisionReduction = 0.0F;
                player.noClip = false;
            }
            return;
        }

        if (applyNoCollision) {
            player.entityCollisionReduction = 1.0F;
            player.noClip = true;
        } else if (!KillAuraHandler.enabled() && !FlyHandler.enabled()) {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
        }

    }

    public static double getKnockbackResistance() {
        double melee = isEnabled("anti_knockback") ? getConfiguredValue("anti_knockback", 1.0F) : 0.0D;
        double ranged = isEnabled("anti_arrow_knockback") ? getConfiguredValue("anti_arrow_knockback", 1.0F) : 0.0D;
        return Math.max(melee, ranged);
    }

    public static boolean hasNoCollision(Entity entity) {
        if (entity == null || !isEnabled("no_collision")) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return false;
        }
        if (entity == player) {
            return true;
        }
        // The integrated server owns a different player object and simulates animal pushing.
        return entity instanceof net.minecraft.entity.player.EntityPlayerMP
                && mc.getIntegratedServer() != null
                && entity.getServer() == mc.getIntegratedServer()
                && player.getUniqueID().equals(entity.getUniqueID());
    }

    @SubscribeEvent
    public void onLivingFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (isEnabled("no_fall") && mc.player != null
                && (event.getEntityLiving() == mc.player
                    || (mc.isIntegratedServerRunning()
                        && event.getEntityLiving().getUniqueID().equals(mc.player.getUniqueID())))) {
            event.setCanceled(true);
            event.getEntityLiving().fallDistance = 0.0F;
        }
    }


    private static void register(FeatureState state) {
        FEATURES.put(state.id, state);
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile(CONFIG_FILE_NAME).toFile();
    }

    private static String normalizeId(String featureId) {
        return safe(featureId).toLowerCase(Locale.ROOT);
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }

    static float getConfiguredValue(String featureId, float fallback) {
        FeatureState state = getFeature(featureId);
        if (state == null || !state.supportsValue()) {
            return fallback;
        }
        return MathHelper.clamp(state.getValue(), state.minValue, state.maxValue);
    }

    private void tickRuntimeState() {
        RuntimeState rt = rt();
        if (rt.longJumpCooldownTicks > 0) {
            rt.longJumpCooldownTicks--;
        }
        if (rt.longJumpBoostTicks > 0) {
            rt.longJumpBoostTicks--;
        }
        if (rt.blinkCooldownTicks > 0) {
            rt.blinkCooldownTicks--;
        }
        if (rt.scaffoldPlaceCooldownTicks > 0) {
            rt.scaffoldPlaceCooldownTicks--;
        }
        if (rt.obstacleAvoidTicks > 0) {
            rt.obstacleAvoidTicks--;
        } else {
            rt.obstacleAvoidDirection = 0;
        }
    }

    private void resetRuntimeState() {
        WallClimbFeatureHandler.reset();
        RuntimeState rt = rt();
        rt.blockPhaseStuckTicks = 0;
        rt.longJumpChargeTicks = 0;
        rt.longJumpCooldownTicks = 0;
        rt.longJumpBoostTicks = 0;
        rt.longJumpBoostSpeed = 0.0D;
        rt.wasLongJumpSneakDown = false;
        rt.blinkCooldownTicks = 0;
        rt.wasBlinkTriggerDown = false;
        rt.scaffoldPlaceCooldownTicks = 0;
        rt.obstacleAvoidDirection = 0;
        rt.obstacleAvoidTicks = 0;
    }

    private String getRuntimeHudLine() {
        RuntimeState rt = rt();
        List<String> parts = new ArrayList<>();
        if (isEnabled("long_jump") && shouldDisplayFeatureStatusHud("long_jump") && rt.longJumpChargeTicks > 0) {
            parts.add("§6长跳 " + Math.min(100, Math.round(rt.longJumpChargeTicks * 100.0F
                    / Math.max(1.0F, getConfiguredValue("long_jump", 1.20F) * 20.0F))) + "%");
        } else if (isEnabled("long_jump") && shouldDisplayFeatureStatusHud("long_jump") && rt.longJumpCooldownTicks > 0) {
            parts.add("§6长跳CD " + rt.longJumpCooldownTicks + "t");
        }
        if (isEnabled("blink_move") && shouldDisplayFeatureStatusHud("blink_move") && rt.blinkCooldownTicks > 0) {
            parts.add("§b闪步CD " + rt.blinkCooldownTicks + "t");
        }
        if (isEnabled("auto_obstacle_avoid")
                && shouldDisplayFeatureStatusHud("auto_obstacle_avoid")
                && rt.obstacleAvoidTicks > 0) {
            parts.add("§a绕障修正");
        }
        if (isEnabled("scaffold") && shouldDisplayFeatureStatusHud("scaffold") && rt.scaffoldPlaceCooldownTicks > 0) {
            parts.add("§e铺路待机");
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("§7");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append("  ");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private String buildFeatureRuntimeSummary(String featureId) {
        if (featureId == null || featureId.isEmpty()) {
            return "待机";
        }
        RuntimeState rt = rt();
        switch (featureId) {
            case "block_phase":
                return rt.blockPhaseStuckTicks > 0 ? "检测到卡墙/夹角，准备脱困" : "未检测到卡墙";
            case "long_jump":
                if (rt.longJumpChargeTicks > 0) {
                    int percent = Math.min(100, Math.round(rt.longJumpChargeTicks * 100.0F
                            / Math.max(1.0F, getConfiguredValue("long_jump", 1.20F) * 20.0F)));
                    return "蓄力中 " + percent + "%";
                }
                if (rt.longJumpCooldownTicks > 0) {
                    return "冷却中 " + rt.longJumpCooldownTicks + " tick";
                }
                if (rt.longJumpBoostTicks > 0) {
                    return "起跳推进中";
                }
                return "待机";
            case "blink_move":
                return rt.blinkCooldownTicks > 0 ? "冷却中 " + rt.blinkCooldownTicks + " tick" : "待机，按住潜行轻点疾跑触发";
            case "timer_accel":
                if (!isEnabled("timer_accel")) {
                    return "未启用";
                }
                if (SpeedHandler.isTimerManagedBySpeed()) {
                    return "已被加速模块的 Timer 接管，当前不生效";
                }
                return rt.externalTimerApplied
                        ? "已接管客户端 Timer，当前倍率 " + formatFloat(rt.lastTimerSpeed) + "x"
                        : "待机，开启后会接管客户端 Timer";
            case "safe_walk":
                return isEnabled("safe_walk") ? "已按贴边保护与位移裁切双重接管，不额外降低移动速度" : "未启用";
            case "lava_walk":
                if (!isEnabled("lava_walk")) {
                    return "未启用";
                }
                StringBuilder liquidSummary = new StringBuilder("液体表面已平台化");
                if (isLiquidWalkDangerousOnly()) {
                    liquidSummary.append("，仅危险液体");
                } else if (!shouldLiquidWalkOnWater()) {
                    liquidSummary.append("，已忽略水面");
                }
                if (shouldLiquidWalkSneakToDescend()) {
                    liquidSummary.append("，潜行可下沉");
                }
                return liquidSummary.toString();
            case "scaffold":
                return rt.scaffoldPlaceCooldownTicks > 0 ? "刚完成一次铺路" : "待机，遇到脚下空隙会自动补方块";
            case "auto_obstacle_avoid":
                return rt.obstacleAvoidTicks > 0 ? "正在沿安全侧修正路径" : "待机，前方遇障时自动侧移";
            default:
                FeatureState state = getFeature(featureId);
                if (state == null) {
                    return "未找到功能";
                }
                return state.behaviorImplemented ? "基础逻辑已接入" : "仅配置占位";
        }
    }

    private String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

}
