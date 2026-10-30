package com.mythos.mythosScriptMod.otherfeatures.handler.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.PerformanceMonitor;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BlockUtils;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RenderFeatureManager {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public boolean brightnessSoftMode = false;
        public float brightnessGamma = 10.0F;
        public boolean noFogRemoveLiquid = true;
        public boolean noFogBrightenColor = true;
        public boolean entityVisualPlayers = true;
        public boolean entityVisualMonsters = true;
        public boolean entityVisualAnimals = false;
        public boolean entityVisualThroughWalls = true;
        public boolean entityVisualFilledBox = true;
        public float entityVisualMaxDistance = 48.0F;
        public boolean tracerPlayers = true;
        public boolean tracerMonsters = true;
        public boolean tracerAnimals = false;
        public boolean tracerThroughWalls = true;
        public float tracerMaxDistance = 64.0F;
        public float tracerLineWidth = 1.6F;
        public boolean entityTagPlayers = true;
        public boolean entityTagMonsters = true;
        public boolean entityTagAnimals = false;
        public boolean entityTagShowHealth = true;
        public boolean entityTagShowDistance = true;
        public boolean entityTagShowHeldItem = false;
        public float entityTagMaxDistance = ENTITY_TAG_DISTANCE_DEFAULT;
        public boolean blockHighlightStorages = true;
        public boolean blockHighlightSpawners = true;
        public boolean blockHighlightOres = true;
        public boolean blockHighlightThroughWalls = true;
        public boolean blockHighlightFilledBox = true;
        public float blockHighlightMaxDistance = 24.0F;
        private final Set<String> XRAY_VISIBLE_BLOCK_IDS = new LinkedHashSet<>();
        public boolean itemEspShowName = true;
        public boolean itemEspShowDistance = true;
        public boolean itemEspThroughWalls = true;
        public float itemEspMaxDistance = 24.0F;
        public boolean trajectoryBows = true;
        public boolean trajectoryPearls = true;
        public boolean trajectoryThrowables = true;
        public boolean trajectoryPotions = true;
        public int trajectoryMaxSteps = 120;
        public boolean crosshairDynamicGap = true;
        public int crosshairColorRgb = 0x55FFFF;
        public float crosshairSize = 6.0F;
        public float crosshairThickness = 2.0F;
        public boolean antiBobRemoveViewBobbing = true;
        public boolean antiBobRemoveHurtShake = true;
        public boolean groundSpeedShowTitle = true;
        public boolean groundSpeedShowUnit = true;
        public int groundSpeedDecimalPlaces = 1;
        public boolean radarPlayers = true;
        public boolean radarMonsters = true;
        public boolean radarAnimals = false;
        public boolean radarRotateWithView = true;
        public float radarMaxDistance = 48.0F;
        public int radarSize = 90;
        public boolean skeletonThroughWalls = true;
        public float skeletonMaxDistance = 48.0F;
        public float skeletonLineWidth = 1.7F;
        public boolean blockOutlineFilledBox = false;
        public float blockOutlineLineWidth = 2.0F;
        public boolean entityInfoShowHealth = true;
        public boolean entityInfoShowDistance = true;
        public boolean entityInfoShowPosition = true;
        public boolean entityInfoShowHeldItem = true;
        public float entityInfoMaxDistance = 48.0F;
        // Baseline for shared gameSettings mutations (gamma / viewBobbing). Single
        // physical display => single baseline, NOT per-owner — otherwise a second
        // bot's snapshot would capture the already-modified gamma as 'previous'.
        private boolean brightnessApplied = false;
        private float previousGammaSetting = 1.0F;
        private boolean antiBobApplied = false;
        private boolean previousViewBobbing = false;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) RenderFeatureManager::loadConfig);
            return config;
        }
    }


    public static final RenderFeatureManager INSTANCE = new RenderFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_render.json";
    public static final int ENTITY_DISTANCE_MIN = 1;
    public static final int ENTITY_DISTANCE_MAX = 100;
    public static final int ENTITY_DISTANCE_DEFAULT = 48;
    public static final int ENTITY_TAG_DISTANCE_DEFAULT = 10;
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();






    private static final String[] DEFAULT_XRAY_BLOCK_IDS = new String[] {
            "minecraft:coal_ore",
            "minecraft:coal_block",
            "minecraft:iron_ore",
            "minecraft:iron_block",
            "minecraft:gold_ore",
            "minecraft:gold_block",
            "minecraft:lapis_ore",
            "minecraft:lapis_block",
            "minecraft:redstone_ore",
            "minecraft:lit_redstone_ore",
            "minecraft:redstone_block",
            "minecraft:diamond_ore",
            "minecraft:diamond_block",
            "minecraft:emerald_ore",
            "minecraft:emerald_block",
            "minecraft:quartz_ore",
            "minecraft:lava",
            "minecraft:mob_spawner",
            "minecraft:portal",
            "minecraft:end_portal",
            "minecraft:end_portal_frame"
    };










    private static String featureOwner() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner();
    }

    /** Owner key of the session currently on the shared display. Render-
     *  affecting features (gamma, view bobbing, fog, ESP overlays) write to
     *  the single mc.gameSettings / shared framebuffer, so the switch that
     *  matters is the VIEWED session's, not whichever bot thread happened to
     *  tick last. Human on screen -> "" (the shared unmounted key).
     *  mountedBotUsername() returns null for the human and for no-mount. */
    private static String viewOwner() {
        String m = com.mythos.mythosScriptMod.mcp.NexusSessions.mountedBotUsername();
        return m == null ? "" : m;
    }

    private static boolean viewEnabled(String id) {
        FeatureState s = FEATURES.get(normalizeId(id));
        return s != null && s.enabledByOwner.getOrDefault(viewOwner(), Boolean.FALSE);
    }


    static {
        register(new FeatureState("brightness_boost", "亮度增强",
                "通过 Gamma 提升整体画面亮度，减少夜晚、洞穴和阴影区域的昏暗感。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("no_fog", "迷雾移除",
                "尽量移除世界与液体中的雾气遮挡，让远处视野更通透。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("entity_visual", "实体视觉",
                "高亮玩家、怪物和动物轮廓，可选择穿墙显示与填充方框。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("tracer_line", "Tracer线",
                "从屏幕中心向附近目标绘制引导线，帮助快速定位实体。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("entity_tags", "实体标签",
                "在实体头顶显示名称、血量、距离与手持物等信息。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("block_highlight", "方块高亮",
                "高亮矿石、箱子、潜影盒、末影箱和刷怪笼等重要方块。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("xray", "X光",
                "只渲染选中的矿石或特殊方块，并自动提亮画面，方便隔墙找矿。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("item_esp", "物品ESP",
                "高亮地面掉落物，并可显示名称和距离。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("trajectory_line", "轨迹线",
                "在持弓或投掷物时预估飞行轨迹和落点。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("custom_crosshair", "自定义十字准星",
                "使用可调颜色、大小和动态间距的十字准星替换原版准星。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("anti_bob", "防抖动",
                "减少走路视角晃动和受伤镜头抖动，让画面更稳定。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("ground_speed", "地速显示",
                "在屏幕右上角显示当前水平地速，便于观察移动状态和速度变化。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("radar", "雷达",
                "在屏幕角落显示附近玩家、怪物和动物的相对位置，方便快速判断周边目标分布。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("player_skeleton", "玩家骨骼",
                "用简化骨架线条高亮附近玩家的身体朝向和站姿，便于隔远识别动作。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("block_outline", "方块轮廓",
                "为当前准星指向的方块显示更清晰、更精确的边框和可选填充。左键快速开关，右键打开渲染设置。"));
        register(new FeatureState("entity_info", "实体信息",
                "准星指向实体时显示名称、血量、距离、坐标和手持物等详细信息面板。左键快速开关，右键打开渲染设置。"));
        loadConfig();
    }

    private RenderFeatureManager() {
    }

    public static final class FeatureState {
        public final String id;
        public final String name;
        public final String description;
        private final java.util.concurrent.ConcurrentHashMap<String, Boolean> enabledByOwner = new java.util.concurrent.ConcurrentHashMap<>();

        private FeatureState(String id, String name, String description) {
            this.id = safe(id);
            this.name = safe(name);
            this.description = safe(description);
        }

        public boolean isEnabled() {
            return enabledByOwner.getOrDefault(featureOwner(), Boolean.FALSE);
        }

        private void setEnabledInternal(boolean enabled) {
            enabledByOwner.put(featureOwner(), enabled);
        }

        private void setEnabledForOwner(String owner, boolean enabled) {
            enabledByOwner.put(owner == null ? "" : owner, enabled);
        }

        private java.util.Map<String, Boolean> snapshotEnabledByOwner() {
            return new java.util.LinkedHashMap<>(enabledByOwner);
        }
    }

    private static void register(FeatureState state) {
        FEATURES.put(state.id, state);
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static String normalizeId(String featureId) {
        return safe(featureId).toLowerCase(Locale.ROOT);
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile(CONFIG_FILE_NAME).toFile();
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
        boolean wasEnabled = state.isEnabled();
        boolean enabled = !wasEnabled;
        state.setEnabledInternal(enabled);
        handleRuntimeSideEffectsAfterToggle(featureId, wasEnabled, enabled);
        saveConfig();
    }

    public static void setEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        boolean wasEnabled = state.isEnabled();
        state.setEnabledInternal(enabled);
        handleRuntimeSideEffectsAfterToggle(featureId, wasEnabled, enabled);
        saveConfig();
    }


    public static void resetFeature(String featureId) {
        String normalizedId = normalizeId(featureId);
        FeatureState state = getFeature(normalizedId);
        if (state == null) {
            return;
        }
        boolean wasEnabled = state.isEnabled();
        state.setEnabledInternal(false);
        applyFeatureDefaultsWithoutSave(normalizedId);
        handleRuntimeSideEffectsAfterToggle(normalizedId, wasEnabled, false);
        saveConfig();
    }

    private static void handleRuntimeSideEffectsAfterToggle(String featureId, boolean wasEnabled, boolean enabled) {
        String normalizedId = normalizeId(featureId);
        if (wasEnabled == enabled) {
            return;
        }
        if ("xray".equals(normalizedId)) {
            requestXrayRendererReload();
            if (!enabled && !isEnabled("brightness_boost")) {
                INSTANCE.restoreBrightness();
            }
            return;
        }
        if ("brightness_boost".equals(normalizedId) && !enabled && !isEnabled("xray")) {
            INSTANCE.restoreBrightness();
        } else if ("anti_bob".equals(normalizedId) && !enabled) {
            INSTANCE.restoreAntiBob();
        }
    }

    public static void applyFeatureDefaultsWithoutSave(String featureId) {
        String normalizedId = normalizeId(featureId);
        switch (normalizedId) {
        case "brightness_boost":
            cfg().brightnessSoftMode = false;
            cfg().brightnessGamma = 10.0F;
            break;
        case "no_fog":
            cfg().noFogRemoveLiquid = true;
            cfg().noFogBrightenColor = true;
            break;
        case "entity_visual":
            cfg().entityVisualPlayers = true;
            cfg().entityVisualMonsters = true;
            cfg().entityVisualAnimals = false;
            cfg().entityVisualThroughWalls = true;
            cfg().entityVisualFilledBox = true;
            cfg().entityVisualMaxDistance = 48.0F;
            break;
        case "tracer_line":
            cfg().tracerPlayers = true;
            cfg().tracerMonsters = true;
            cfg().tracerAnimals = false;
            cfg().tracerThroughWalls = true;
            cfg().tracerMaxDistance = 64.0F;
            cfg().tracerLineWidth = 1.6F;
            break;
        case "entity_tags":
            cfg().entityTagPlayers = true;
            cfg().entityTagMonsters = true;
            cfg().entityTagAnimals = false;
            cfg().entityTagShowHealth = true;
            cfg().entityTagShowDistance = true;
            cfg().entityTagShowHeldItem = false;
            cfg().entityTagMaxDistance = ENTITY_TAG_DISTANCE_DEFAULT;
            break;
        case "block_highlight":
            cfg().blockHighlightStorages = true;
            cfg().blockHighlightSpawners = true;
            cfg().blockHighlightOres = true;
            cfg().blockHighlightThroughWalls = true;
            cfg().blockHighlightFilledBox = true;
            cfg().blockHighlightMaxDistance = 24.0F;
            break;
        case "xray":
            resetXrayVisibleBlocksToDefaultInternal();
            requestXrayRendererReloadIfEnabled();
            break;
        case "item_esp":
            cfg().itemEspShowName = true;
            cfg().itemEspShowDistance = true;
            cfg().itemEspThroughWalls = true;
            cfg().itemEspMaxDistance = 24.0F;
            break;
        case "trajectory_line":
            cfg().trajectoryBows = true;
            cfg().trajectoryPearls = true;
            cfg().trajectoryThrowables = true;
            cfg().trajectoryPotions = true;
            cfg().trajectoryMaxSteps = 120;
            break;
        case "custom_crosshair":
            cfg().crosshairDynamicGap = true;
            cfg().crosshairColorRgb = 0x55FFFF;
            cfg().crosshairSize = 6.0F;
            cfg().crosshairThickness = 2.0F;
            break;
        case "anti_bob":
            cfg().antiBobRemoveViewBobbing = true;
            cfg().antiBobRemoveHurtShake = true;
            break;
        case "ground_speed":
            cfg().groundSpeedShowTitle = true;
            cfg().groundSpeedShowUnit = true;
            cfg().groundSpeedDecimalPlaces = 1;
            break;
        case "radar":
            cfg().radarPlayers = true;
            cfg().radarMonsters = true;
            cfg().radarAnimals = false;
            cfg().radarRotateWithView = true;
            cfg().radarMaxDistance = 48.0F;
            cfg().radarSize = 90;
            break;
        case "player_skeleton":
            cfg().skeletonThroughWalls = true;
            cfg().skeletonMaxDistance = 48.0F;
            cfg().skeletonLineWidth = 1.7F;
            break;
        case "block_outline":
            cfg().blockOutlineFilledBox = false;
            cfg().blockOutlineLineWidth = 2.0F;
            break;
        case "entity_info":
            cfg().entityInfoShowHealth = true;
            cfg().entityInfoShowDistance = true;
            cfg().entityInfoShowPosition = true;
            cfg().entityInfoShowHeldItem = true;
            cfg().entityInfoMaxDistance = 48.0F;
            break;
        default:
            break;
        }
    }

    public static String getFeatureRuntimeSummary(String featureId) {
        String normalizedId = normalizeId(featureId);
        switch (normalizedId) {
        case "brightness_boost":
            return isEnabled(normalizedId) ? "Gamma增强中，当前值 " + formatFloat(cfg().brightnessGamma) : "未启用";
        case "no_fog":
            return isEnabled(normalizedId)
                    ? "世界雾已压低" + (cfg().noFogRemoveLiquid ? "，液体雾同步移除" : "")
                    : "未启用";
        case "entity_visual":
            return isEnabled(normalizedId) ? "实体轮廓范围 " + formatFloat(cfg().entityVisualMaxDistance) + " 格" : "未启用";
        case "tracer_line":
            return isEnabled(normalizedId) ? "引导线范围 " + formatFloat(cfg().tracerMaxDistance) + " 格" : "未启用";
        case "entity_tags":
            return isEnabled(normalizedId) ? "头顶信息范围 " + getEntityTagMaxDistance() + " 格" : "未启用";
        case "block_highlight":
            return isEnabled(normalizedId) ? "高亮重要方块，矿石按类型自动配色，范围 " + formatFloat(cfg().blockHighlightMaxDistance) + " 格"
                    : "未启用";
        case "xray":
            return isEnabled(normalizedId) ? "仅渲染已选透视方块，当前 " + cfg().XRAY_VISIBLE_BLOCK_IDS.size() + " 项"
                    : "未启用";
        case "item_esp":
            return isEnabled(normalizedId) ? "掉落物高亮范围 " + formatFloat(cfg().itemEspMaxDistance) + " 格" : "未启用";
        case "trajectory_line":
            return isEnabled(normalizedId) ? "支持弓/珍珠/雪球/药水轨迹预估" : "未启用";
        case "custom_crosshair":
            return isEnabled(normalizedId)
                    ? "准星颜色 #" + String.format(Locale.ROOT, "%06X", cfg().crosshairColorRgb & 0xFFFFFF)
                    : "未启用";
        case "anti_bob":
            return isEnabled(normalizedId) ? "已抑制镜头晃动" : "未启用";
        case "ground_speed":
            return isEnabled(normalizedId)
                    ? "当前地速 " + formatGroundSpeed(getCurrentHorizontalSpeedBlocksPerSecond())
                            + (cfg().groundSpeedShowUnit ? " 格/秒" : "")
                    : "未启用";
        case "radar":
            return isEnabled(normalizedId) ? "雷达范围 " + formatFloat(cfg().radarMaxDistance) + " 格" : "未启用";
        case "player_skeleton":
            return isEnabled(normalizedId) ? "骨架范围 " + formatFloat(cfg().skeletonMaxDistance) + " 格" : "未启用";
        case "block_outline":
            return isEnabled(normalizedId) ? "高亮当前选中方块边框" : "未启用";
        case "entity_info":
            return isEnabled(normalizedId) ? "实体信息范围 " + getEntityInfoMaxDistance() + " 格" : "未启用";
        default:
            return "待机";
        }
    }

    public static boolean shouldSuppressViewBobbing() {
        return isEnabled("anti_bob") && cfg().antiBobRemoveViewBobbing;
    }

    public static boolean shouldSuppressHurtCamera() {
        return isEnabled("anti_bob") && cfg().antiBobRemoveHurtShake;
    }

    public static boolean isXrayBlockVisible(Block block) {
        String blockId = normalizeBlockId(block);
        return !blockId.isEmpty() && cfg().XRAY_VISIBLE_BLOCK_IDS.contains(blockId);
    }


    public static List<String> getXrayVisibleBlockIds() {
        return sortBlockIds(cfg().XRAY_VISIBLE_BLOCK_IDS);
    }



    public static String normalizeXrayBlockId(String rawBlockId) {
        return normalizeBlockId(rawBlockId);
    }



    public static void resetXrayVisibleBlocksWithoutSave() {
        resetXrayVisibleBlocksToDefaultInternal();
        requestXrayRendererReloadIfEnabled();
    }

    public static void setXrayVisibleBlocks(java.util.Collection<String> rawBlockIds) {
        cfg().XRAY_VISIBLE_BLOCK_IDS.clear();
        if (rawBlockIds != null) {
            for (String rawBlockId : rawBlockIds) {
                String blockId = normalizeXrayBlockId(rawBlockId);
                if (!blockId.isEmpty()) {
                    cfg().XRAY_VISIBLE_BLOCK_IDS.add(blockId);
                }
            }
        }
        requestXrayRendererReloadIfEnabled();
    }

    public static void requestXrayRendererReload() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }
        // 重建区块渲染器只能在客户端线程做：会话 tick 线程里重载配置时排队过去，否则会把客户端卡死。
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(RenderFeatureManager::requestXrayRendererReload);
            return;
        }
        mc.renderGlobal.loadRenderers();
    }

    public void onClientDisconnect() {
        resetRuntimeState();
        RenderFeatureSupport.clearRuntimeCaches();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("render_features_tick")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("render_features_tick");
        try {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (mc.world == null || player == null) {
            resetRuntimeState();
            RenderFeatureSupport.clearRuntimeCaches();
            return;
        }

        // Display-affecting mutations (gamma/viewBobbing/block-highlight scan)
        // write shared gameSettings / shared highlight lists — only the session
        // currently on screen may drive them, else every background bot's tick
        // fights over the single display. featureOwner()==viewOwner() is true
        // only on the viewed session's tick thread (or client thread for human).
        // isOnMainThread pins it to the ONE client thread: a mounted bot's
        // BotTicker thread also resolves featureOwner()==viewOwner() ("bot2"),
        // and letting it write would make its restoreBrightness() stomp the
        // shared gamma with its own baseline.
        boolean applyGate = mc.isCallingFromMinecraftThread() && featureOwner().equals(viewOwner());
        if (applyGate) {
            applyBrightness();
            applyAntiBob();
            RenderFeatureSupport.onClientTick(mc, player);
        }
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("render_features_world")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("render_features_world");
        try {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return;
        }
        RenderFeatureSupport.renderWorld(mc, event.getPartialTicks());
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null || !isEnabled("custom_crosshair")) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderOverlayPost(RenderGameOverlayEvent.Post event) {
        if (!PerformanceMonitor.isFeatureEnabled("render_features_overlay")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("render_features_overlay");
        try {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        if (isEnabled("custom_crosshair")) {
            RenderFeatureSupport.renderCrosshair(mc);
        }
        if (isEnabled("ground_speed")) {
            RenderFeatureSupport.renderGroundSpeed(mc);
        }
        if (isEnabled("radar")) {
            RenderFeatureSupport.renderRadar(mc);
        }
        if (isEnabled("entity_info")) {
            RenderFeatureSupport.renderEntityInfo(mc);
        }
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (!isEnabled("no_fog") || event == null) {
            return;
        }
        if (event.getState() != null
                && event.getState().getMaterial() != null
                && event.getState().getMaterial().isLiquid()
                && !cfg().noFogRemoveLiquid) {
            return;
        }
        event.setDensity(0.0F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (!isEnabled("no_fog") || event == null || !cfg().noFogBrightenColor) {
            return;
        }
        event.setRed(Math.max(event.getRed(), 0.95F));
        event.setGreen(Math.max(event.getGreen(), 0.96F));
        event.setBlue(Math.max(event.getBlue(), 0.98F));
    }

    private void resetRuntimeState() {
        restoreBrightness();
        restoreAntiBob();
    }

    public static boolean isOreBlock(Block block) {
        return block == Blocks.COAL_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.LIT_REDSTONE_ORE
                || block == Blocks.LAPIS_ORE
                || block == Blocks.DIAMOND_ORE
                || block == Blocks.EMERALD_ORE
                || block == Blocks.QUARTZ_ORE;
    }

    public static float[] getBlockHighlightColor(IBlockState state) {
        Block block = state == null ? null : state.getBlock();
        if (block == Blocks.COAL_ORE) {
            return new float[] { 0.25F, 0.25F, 0.25F };
        }
        if (block == Blocks.IRON_ORE) {
            return new float[] { 0.82F, 0.66F, 0.46F };
        }
        if (block == Blocks.GOLD_ORE) {
            return new float[] { 0.98F, 0.84F, 0.22F };
        }
        if (block == Blocks.REDSTONE_ORE || block == Blocks.LIT_REDSTONE_ORE) {
            return new float[] { 0.96F, 0.18F, 0.18F };
        }
        if (block == Blocks.LAPIS_ORE) {
            return new float[] { 0.20F, 0.42F, 0.98F };
        }
        if (block == Blocks.DIAMOND_ORE) {
            return new float[] { 0.18F, 0.98F, 0.90F };
        }
        if (block == Blocks.EMERALD_ORE) {
            return new float[] { 0.12F, 0.90F, 0.28F };
        }
        if (block == Blocks.QUARTZ_ORE) {
            return new float[] { 0.95F, 0.95F, 0.95F };
        }
        return new float[] { 0.18F, 0.98F, 0.90F };
    }

    private void applyBrightness() {
        // Single shared gammaSetting — key off the VIEWED session so mount
        // switches follow whichever bot is on screen; background bots never write.
        Minecraft mc = Minecraft.getMinecraft();
        boolean brightnessEnabled = viewEnabled("brightness_boost");
        boolean xrayEnabled = viewEnabled("xray");
        if (!brightnessEnabled && !xrayEnabled) {
            restoreBrightness();
            return;
        }
        if (!cfg().brightnessApplied) {
            // Vanilla options clamp gamma to [0,1]; values above are another
            // feature's boost (e.g. KillAura fullbright at 1000). Capturing a
            // boosted value would make restoreBrightness() write it back when
            // the toggle goes OFF — the "off = bright" inversion.
            cfg().previousGammaSetting = Math.min(1.0F, Math.max(0.0F, mc.gameSettings.gammaSetting));
            cfg().brightnessApplied = true;
        }
        float targetGamma = 1.0F;
        if (brightnessEnabled) {
            targetGamma = cfg().brightnessSoftMode ? Math.max(4.0F, cfg().brightnessGamma * 0.55F)
                    : Math.max(1.0F, cfg().brightnessGamma);
        }
        if (xrayEnabled) {
            targetGamma = Math.max(targetGamma, 16.0F);
        }
        mc.gameSettings.gammaSetting = targetGamma;
    }

    private void restoreBrightness() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!cfg().brightnessApplied || mc == null || mc.gameSettings == null) {
            return;
        }
        mc.gameSettings.gammaSetting = cfg().previousGammaSetting;
        cfg().brightnessApplied = false;
    }

    private void applyAntiBob() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!viewEnabled("anti_bob") || !cfg().antiBobRemoveViewBobbing) {
            restoreAntiBob();
            return;
        }
        if (!cfg().antiBobApplied) {
            cfg().previousViewBobbing = mc.gameSettings.viewBobbing;
            cfg().antiBobApplied = true;
        }
        mc.gameSettings.viewBobbing = false;
    }

    private void restoreAntiBob() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!cfg().antiBobApplied || mc == null || mc.gameSettings == null) {
            return;
        }
        mc.gameSettings.viewBobbing = cfg().previousViewBobbing;
        cfg().antiBobApplied = false;
    }

    private static void applyDefaultValues() {
        cfg().brightnessSoftMode = false;
        cfg().brightnessGamma = 10.0F;
        cfg().noFogRemoveLiquid = true;
        cfg().noFogBrightenColor = true;
        cfg().entityVisualPlayers = true;
        cfg().entityVisualMonsters = true;
        cfg().entityVisualAnimals = false;
        cfg().entityVisualThroughWalls = true;
        cfg().entityVisualFilledBox = true;
        cfg().entityVisualMaxDistance = 48.0F;
        cfg().tracerPlayers = true;
        cfg().tracerMonsters = true;
        cfg().tracerAnimals = false;
        cfg().tracerThroughWalls = true;
        cfg().tracerMaxDistance = 64.0F;
        cfg().tracerLineWidth = 1.6F;
        cfg().entityTagPlayers = true;
        cfg().entityTagMonsters = true;
        cfg().entityTagAnimals = false;
        cfg().entityTagShowHealth = true;
        cfg().entityTagShowDistance = true;
        cfg().entityTagShowHeldItem = false;
        cfg().entityTagMaxDistance = ENTITY_TAG_DISTANCE_DEFAULT;
        cfg().blockHighlightStorages = true;
        cfg().blockHighlightSpawners = true;
        cfg().blockHighlightOres = true;
        cfg().blockHighlightThroughWalls = true;
        cfg().blockHighlightFilledBox = true;
        cfg().blockHighlightMaxDistance = 24.0F;
        resetXrayVisibleBlocksToDefaultInternal();
        cfg().itemEspShowName = true;
        cfg().itemEspShowDistance = true;
        cfg().itemEspThroughWalls = true;
        cfg().itemEspMaxDistance = 24.0F;
        cfg().trajectoryBows = true;
        cfg().trajectoryPearls = true;
        cfg().trajectoryThrowables = true;
        cfg().trajectoryPotions = true;
        cfg().trajectoryMaxSteps = 120;
        cfg().crosshairDynamicGap = true;
        cfg().crosshairColorRgb = 0x55FFFF;
        cfg().crosshairSize = 6.0F;
        cfg().crosshairThickness = 2.0F;
        cfg().antiBobRemoveViewBobbing = true;
        cfg().antiBobRemoveHurtShake = true;
        cfg().groundSpeedShowTitle = true;
        cfg().groundSpeedShowUnit = true;
        cfg().groundSpeedDecimalPlaces = 1;
        cfg().radarPlayers = true;
        cfg().radarMonsters = true;
        cfg().radarAnimals = false;
        cfg().radarRotateWithView = true;
        cfg().radarMaxDistance = 48.0F;
        cfg().radarSize = 90;
        cfg().skeletonThroughWalls = true;
        cfg().skeletonMaxDistance = 48.0F;
        cfg().skeletonLineWidth = 1.7F;
        cfg().blockOutlineFilledBox = false;
        cfg().blockOutlineLineWidth = 2.0F;
        cfg().entityInfoShowHealth = true;
        cfg().entityInfoShowDistance = true;
        cfg().entityInfoShowPosition = true;
        cfg().entityInfoShowHeldItem = true;
        cfg().entityInfoMaxDistance = 48.0F;
    }

    public static void loadConfig() {
        for (FeatureState state : FEATURES.values()) {
            state.setEnabledInternal(false);
        }
        applyDefaultValues();

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
                for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                    if (root.has(entry.getKey() + "_enabled")) {
                        entry.getValue().setEnabledForOwner(featureOwner(),
                                root.get(entry.getKey() + "_enabled").getAsBoolean());
                    }
                }
            }

            cfg().brightnessSoftMode = root.has("brightness_soft_mode") && root.get("brightness_soft_mode").getAsBoolean();
            cfg().brightnessGamma = root.has("brightness_gamma") ? root.get("brightness_gamma").getAsFloat() : 10.0F;
            if (!(cfg().brightnessGamma > 1.0F) || cfg().brightnessGamma > 1000.0F || Float.isNaN(cfg().brightnessGamma)) {
                // A zeroed/corrupt config (all-defaults save) parks gamma at 0,
                // making the toggle a no-op — fall back to the 10x default.
                cfg().brightnessGamma = 10.0F;
            }
            cfg().noFogRemoveLiquid = !root.has("no_fog_remove_liquid") || root.get("no_fog_remove_liquid").getAsBoolean();
            cfg().noFogBrightenColor = !root.has("no_fog_brighten_color") || root.get("no_fog_brighten_color").getAsBoolean();
            cfg().entityVisualPlayers = !root.has("entity_visual_players") || root.get("entity_visual_players").getAsBoolean();
            cfg().entityVisualMonsters = !root.has("entity_visual_monsters") || root.get("entity_visual_monsters").getAsBoolean();
            cfg().entityVisualAnimals = root.has("entity_visual_animals") && root.get("entity_visual_animals").getAsBoolean();
            cfg().entityVisualThroughWalls = !root.has("entity_visual_through_walls") || root.get("entity_visual_through_walls").getAsBoolean();
            cfg().entityVisualFilledBox = !root.has("entity_visual_filled_box") || root.get("entity_visual_filled_box").getAsBoolean();
            cfg().entityVisualMaxDistance = positiveFloatOr(root, "entity_visual_max_distance", 48.0F);
            cfg().tracerPlayers = !root.has("tracer_players") || root.get("tracer_players").getAsBoolean();
            cfg().tracerMonsters = !root.has("tracer_monsters") || root.get("tracer_monsters").getAsBoolean();
            cfg().tracerAnimals = root.has("tracer_animals") && root.get("tracer_animals").getAsBoolean();
            cfg().tracerThroughWalls = !root.has("tracer_through_walls") || root.get("tracer_through_walls").getAsBoolean();
            cfg().tracerMaxDistance = positiveFloatOr(root, "tracer_max_distance", 64.0F);
            cfg().tracerLineWidth = positiveFloatOr(root, "tracer_line_width", 1.6F);
            cfg().entityTagPlayers = !root.has("entity_tag_players") || root.get("entity_tag_players").getAsBoolean();
            cfg().entityTagMonsters = !root.has("entity_tag_monsters") || root.get("entity_tag_monsters").getAsBoolean();
            cfg().entityTagAnimals = root.has("entity_tag_animals") && root.get("entity_tag_animals").getAsBoolean();
            cfg().entityTagShowHealth = !root.has("entity_tag_show_health") || root.get("entity_tag_show_health").getAsBoolean();
            cfg().entityTagShowDistance = !root.has("entity_tag_show_distance") || root.get("entity_tag_show_distance").getAsBoolean();
            cfg().entityTagShowHeldItem = root.has("entity_tag_show_held_item") && root.get("entity_tag_show_held_item").getAsBoolean();
            cfg().entityTagMaxDistance = root.has("entity_tag_max_distance")
                    ? clampEntityDistance(root.get("entity_tag_max_distance").getAsFloat())
                    : ENTITY_TAG_DISTANCE_DEFAULT;
            cfg().blockHighlightStorages = !root.has("block_highlight_storages") || root.get("block_highlight_storages").getAsBoolean();
            cfg().blockHighlightSpawners = !root.has("block_highlight_spawners") || root.get("block_highlight_spawners").getAsBoolean();
            cfg().blockHighlightOres = !root.has("block_highlight_ores") || root.get("block_highlight_ores").getAsBoolean();
            cfg().blockHighlightThroughWalls = !root.has("block_highlight_through_walls") || root.get("block_highlight_through_walls").getAsBoolean();
            cfg().blockHighlightFilledBox = !root.has("block_highlight_filled_box") || root.get("block_highlight_filled_box").getAsBoolean();
            cfg().blockHighlightMaxDistance = positiveFloatOr(root, "block_highlight_max_distance", 24.0F);
            loadXrayVisibleBlocks(root);
            cfg().itemEspShowName = !root.has("item_esp_show_name") || root.get("item_esp_show_name").getAsBoolean();
            cfg().itemEspShowDistance = !root.has("item_esp_show_distance") || root.get("item_esp_show_distance").getAsBoolean();
            cfg().itemEspThroughWalls = !root.has("item_esp_through_walls") || root.get("item_esp_through_walls").getAsBoolean();
            cfg().itemEspMaxDistance = positiveFloatOr(root, "item_esp_max_distance", 24.0F);
            cfg().trajectoryBows = !root.has("trajectory_bows") || root.get("trajectory_bows").getAsBoolean();
            cfg().trajectoryPearls = !root.has("trajectory_pearls") || root.get("trajectory_pearls").getAsBoolean();
            cfg().trajectoryThrowables = !root.has("trajectory_throwables") || root.get("trajectory_throwables").getAsBoolean();
            cfg().trajectoryPotions = !root.has("trajectory_potions") || root.get("trajectory_potions").getAsBoolean();
            cfg().trajectoryMaxSteps = root.has("trajectory_max_steps")
                    ? Math.max(1, root.get("trajectory_max_steps").getAsInt()) : 120;
            cfg().crosshairDynamicGap = !root.has("crosshair_dynamic_gap") || root.get("crosshair_dynamic_gap").getAsBoolean();
            cfg().crosshairColorRgb = root.has("crosshair_color_rgb") ? root.get("crosshair_color_rgb").getAsInt() : 0x55FFFF;
            cfg().crosshairSize = positiveFloatOr(root, "crosshair_size", 6.0F);
            cfg().crosshairThickness = positiveFloatOr(root, "crosshair_thickness", 2.0F);
            cfg().antiBobRemoveViewBobbing = !root.has("anti_bob_remove_view_bobbing") || root.get("anti_bob_remove_view_bobbing").getAsBoolean();
            cfg().antiBobRemoveHurtShake = !root.has("anti_bob_remove_hurt_shake") || root.get("anti_bob_remove_hurt_shake").getAsBoolean();
            cfg().groundSpeedShowTitle = !root.has("ground_speed_show_title") || root.get("ground_speed_show_title").getAsBoolean();
            cfg().groundSpeedShowUnit = !root.has("ground_speed_show_unit") || root.get("ground_speed_show_unit").getAsBoolean();
            cfg().groundSpeedDecimalPlaces = root.has("ground_speed_decimal_places")
                    ? MathHelper.clamp(root.get("ground_speed_decimal_places").getAsInt(), 0, 3)
                    : 1;
            cfg().radarPlayers = !root.has("radar_players") || root.get("radar_players").getAsBoolean();
            cfg().radarMonsters = !root.has("radar_monsters") || root.get("radar_monsters").getAsBoolean();
            cfg().radarAnimals = root.has("radar_animals") && root.get("radar_animals").getAsBoolean();
            cfg().radarRotateWithView = !root.has("radar_rotate_with_view") || root.get("radar_rotate_with_view").getAsBoolean();
            cfg().radarMaxDistance = positiveFloatOr(root, "radar_max_distance", 48.0F);
            cfg().radarSize = root.has("radar_size") ? Math.max(1, root.get("radar_size").getAsInt()) : 90;
            cfg().skeletonThroughWalls = !root.has("skeleton_through_walls") || root.get("skeleton_through_walls").getAsBoolean();
            cfg().skeletonMaxDistance = positiveFloatOr(root, "skeleton_max_distance", 48.0F);
            cfg().skeletonLineWidth = positiveFloatOr(root, "skeleton_line_width", 1.7F);
            cfg().blockOutlineFilledBox = root.has("block_outline_filled_box") && root.get("block_outline_filled_box").getAsBoolean();
            cfg().blockOutlineLineWidth = positiveFloatOr(root, "block_outline_line_width", 2.0F);
            cfg().entityInfoShowHealth = !root.has("entity_info_show_health") || root.get("entity_info_show_health").getAsBoolean();
            cfg().entityInfoShowDistance = !root.has("entity_info_show_distance") || root.get("entity_info_show_distance").getAsBoolean();
            cfg().entityInfoShowPosition = !root.has("entity_info_show_position") || root.get("entity_info_show_position").getAsBoolean();
            cfg().entityInfoShowHeldItem = !root.has("entity_info_show_held_item") || root.get("entity_info_show_held_item").getAsBoolean();
            cfg().entityInfoMaxDistance = root.has("entity_info_max_distance")
                    ? clampEntityDistance(root.get("entity_info_max_distance").getAsFloat())
                    : ENTITY_DISTANCE_DEFAULT;
            requestXrayRendererReload();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载渲染功能配置失败", e);
        }
    }

    /** Config-zero guard: a corrupt save parks every numeric at 0, which turns
     *  distance/size caps into "render nothing". Treat non-positive/NaN as
     *  missing and fall back to the field's default. */
    private static float positiveFloatOr(JsonObject root, String key, float fallback) {
        if (root == null || !root.has(key)) {
            return fallback;
        }
        try {
            float v = root.get(key).getAsFloat();
            return (v > 0.0F && !Float.isNaN(v) && Float.isFinite(v)) ? v : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            JsonObject root = new JsonObject();
            root.addProperty("brightness_soft_mode", cfg().brightnessSoftMode);
            root.addProperty("brightness_gamma", cfg().brightnessGamma);
            root.addProperty("no_fog_remove_liquid", cfg().noFogRemoveLiquid);
            root.addProperty("no_fog_brighten_color", cfg().noFogBrightenColor);
            root.addProperty("entity_visual_players", cfg().entityVisualPlayers);
            root.addProperty("entity_visual_monsters", cfg().entityVisualMonsters);
            root.addProperty("entity_visual_animals", cfg().entityVisualAnimals);
            root.addProperty("entity_visual_through_walls", cfg().entityVisualThroughWalls);
            root.addProperty("entity_visual_filled_box", cfg().entityVisualFilledBox);
            root.addProperty("entity_visual_max_distance", cfg().entityVisualMaxDistance);
            root.addProperty("tracer_players", cfg().tracerPlayers);
            root.addProperty("tracer_monsters", cfg().tracerMonsters);
            root.addProperty("tracer_animals", cfg().tracerAnimals);
            root.addProperty("tracer_through_walls", cfg().tracerThroughWalls);
            root.addProperty("tracer_max_distance", cfg().tracerMaxDistance);
            root.addProperty("tracer_line_width", cfg().tracerLineWidth);
            root.addProperty("entity_tag_players", cfg().entityTagPlayers);
            root.addProperty("entity_tag_monsters", cfg().entityTagMonsters);
            root.addProperty("entity_tag_animals", cfg().entityTagAnimals);
            root.addProperty("entity_tag_show_health", cfg().entityTagShowHealth);
            root.addProperty("entity_tag_show_distance", cfg().entityTagShowDistance);
            root.addProperty("entity_tag_show_held_item", cfg().entityTagShowHeldItem);
            root.addProperty("entity_tag_max_distance", clampEntityDistance(cfg().entityTagMaxDistance));
            root.addProperty("block_highlight_storages", cfg().blockHighlightStorages);
            root.addProperty("block_highlight_spawners", cfg().blockHighlightSpawners);
            root.addProperty("block_highlight_ores", cfg().blockHighlightOres);
            root.addProperty("block_highlight_through_walls", cfg().blockHighlightThroughWalls);
            root.addProperty("block_highlight_filled_box", cfg().blockHighlightFilledBox);
            root.addProperty("block_highlight_max_distance", cfg().blockHighlightMaxDistance);
            JsonArray xrayBlocks = new JsonArray();
            for (String blockId : getXrayVisibleBlockIds()) {
                xrayBlocks.add(blockId);
            }
            root.add("xray_visible_blocks", xrayBlocks);
            root.addProperty("item_esp_show_name", cfg().itemEspShowName);
            root.addProperty("item_esp_show_distance", cfg().itemEspShowDistance);
            root.addProperty("item_esp_through_walls", cfg().itemEspThroughWalls);
            root.addProperty("item_esp_max_distance", cfg().itemEspMaxDistance);
            root.addProperty("trajectory_bows", cfg().trajectoryBows);
            root.addProperty("trajectory_pearls", cfg().trajectoryPearls);
            root.addProperty("trajectory_throwables", cfg().trajectoryThrowables);
            root.addProperty("trajectory_potions", cfg().trajectoryPotions);
            root.addProperty("trajectory_max_steps", cfg().trajectoryMaxSteps);
            root.addProperty("crosshair_dynamic_gap", cfg().crosshairDynamicGap);
            root.addProperty("crosshair_color_rgb", cfg().crosshairColorRgb);
            root.addProperty("crosshair_size", cfg().crosshairSize);
            root.addProperty("crosshair_thickness", cfg().crosshairThickness);
            root.addProperty("anti_bob_remove_view_bobbing", cfg().antiBobRemoveViewBobbing);
            root.addProperty("anti_bob_remove_hurt_shake", cfg().antiBobRemoveHurtShake);
            root.addProperty("ground_speed_show_title", cfg().groundSpeedShowTitle);
            root.addProperty("ground_speed_show_unit", cfg().groundSpeedShowUnit);
            root.addProperty("ground_speed_decimal_places", MathHelper.clamp(cfg().groundSpeedDecimalPlaces, 0, 3));
            root.addProperty("radar_players", cfg().radarPlayers);
            root.addProperty("radar_monsters", cfg().radarMonsters);
            root.addProperty("radar_animals", cfg().radarAnimals);
            root.addProperty("radar_rotate_with_view", cfg().radarRotateWithView);
            root.addProperty("radar_max_distance", cfg().radarMaxDistance);
            root.addProperty("radar_size", cfg().radarSize);
            root.addProperty("skeleton_through_walls", cfg().skeletonThroughWalls);
            root.addProperty("skeleton_max_distance", cfg().skeletonMaxDistance);
            root.addProperty("skeleton_line_width", cfg().skeletonLineWidth);
            root.addProperty("block_outline_filled_box", cfg().blockOutlineFilledBox);
            root.addProperty("block_outline_line_width", cfg().blockOutlineLineWidth);
            root.addProperty("entity_info_show_health", cfg().entityInfoShowHealth);
            root.addProperty("entity_info_show_distance", cfg().entityInfoShowDistance);
            root.addProperty("entity_info_show_position", cfg().entityInfoShowPosition);
            root.addProperty("entity_info_show_held_item", cfg().entityInfoShowHeldItem);
            root.addProperty("entity_info_max_distance", clampEntityDistance(cfg().entityInfoMaxDistance));
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }

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
            mythosScriptMod.LOGGER.error("保存渲染功能配置失败", e);
        }
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve(CONFIG_FILE_NAME).toFile();
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static int getEntityInfoMaxDistance() {
        return Math.round(clampEntityDistance(cfg().entityInfoMaxDistance));
    }

    public static void setEntityInfoMaxDistance(int distance) {
        cfg().entityInfoMaxDistance = clampEntityDistance(distance);
    }

    public static int getEntityTagMaxDistance() {
        return Math.round(clampEntityDistance(cfg().entityTagMaxDistance));
    }

    public static void setEntityTagMaxDistance(int distance) {
        cfg().entityTagMaxDistance = clampEntityDistance(distance);
    }

    private static float clampEntityDistance(float distance) {
        return MathHelper.clamp(distance, (float) ENTITY_DISTANCE_MIN, (float) ENTITY_DISTANCE_MAX);
    }

    public static String formatGroundSpeed(float value) {
        int decimals = MathHelper.clamp(cfg().groundSpeedDecimalPlaces, 0, 3);
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static float getCurrentHorizontalSpeedBlocksPerSecond() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null) {
            return 0.0F;
        }
        double deltaX = player.posX - player.lastTickPosX;
        double deltaZ = player.posZ - player.lastTickPosZ;
        return (float) (Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20.0D);
    }

    private static void resetXrayVisibleBlocksToDefaultInternal() {
        cfg().XRAY_VISIBLE_BLOCK_IDS.clear();
        for (String blockId : DEFAULT_XRAY_BLOCK_IDS) {
            String normalized = normalizeBlockId(blockId);
            if (!normalized.isEmpty()) {
                cfg().XRAY_VISIBLE_BLOCK_IDS.add(normalized);
            }
        }
    }

    private static void loadXrayVisibleBlocks(JsonObject root) {
        resetXrayVisibleBlocksToDefaultInternal();
        if (root == null || !root.has("xray_visible_blocks") || !root.get("xray_visible_blocks").isJsonArray()) {
            return;
        }

        cfg().XRAY_VISIBLE_BLOCK_IDS.clear();
        for (JsonElement element : root.getAsJsonArray("xray_visible_blocks")) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            String blockId = normalizeBlockId(element.getAsString());
            if (!blockId.isEmpty()) {
                cfg().XRAY_VISIBLE_BLOCK_IDS.add(blockId);
            }
        }

        if (cfg().XRAY_VISIBLE_BLOCK_IDS.isEmpty()) {
            resetXrayVisibleBlocksToDefaultInternal();
        }
    }

    private static void requestXrayRendererReloadIfEnabled() {
        if (isEnabled("xray")) {
            requestXrayRendererReload();
        }
    }

    private static String normalizeBlockId(String rawBlockId) {
        String normalized = safe(rawBlockId).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        try {
            new ResourceLocation(normalized);
            return normalized;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String normalizeBlockId(Block block) {
        if (block == null || block == Blocks.AIR) {
            return "";
        }
        ResourceLocation registryName = block.getRegistryName();
        return registryName == null ? "" : registryName.toString();
    }

    private static List<String> sortBlockIds(Iterable<String> blockIds) {
        List<String> sorted = new ArrayList<>();
        if (blockIds != null) {
            for (String blockId : blockIds) {
                String safeId = safe(blockId);
                if (!safeId.isEmpty()) {
                    sorted.add(safeId);
                }
            }
        }
        Collections.sort(sorted);
        return sorted;
    }
}
