package com.zszl.zszlScriptMod.otherfeatures.handler.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BlockUtils;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
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

    public static final RenderFeatureManager INSTANCE = new RenderFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_render.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();

    public static boolean brightnessSoftMode = false;
    public static float brightnessGamma = 10.0F;

    public static boolean noFogRemoveLiquid = true;
    public static boolean noFogBrightenColor = true;

    public static boolean entityVisualPlayers = true;
    public static boolean entityVisualMonsters = true;
    public static boolean entityVisualAnimals = false;
    public static boolean entityVisualThroughWalls = true;
    public static boolean entityVisualFilledBox = true;
    public static float entityVisualMaxDistance = 48.0F;

    public static boolean tracerPlayers = true;
    public static boolean tracerMonsters = true;
    public static boolean tracerAnimals = false;
    public static boolean tracerThroughWalls = true;
    public static float tracerMaxDistance = 64.0F;
    public static float tracerLineWidth = 1.6F;

    public static boolean entityTagPlayers = true;
    public static boolean entityTagMonsters = true;
    public static boolean entityTagAnimals = false;
    public static boolean entityTagShowHealth = true;
    public static boolean entityTagShowDistance = true;
    public static boolean entityTagShowHeldItem = false;
    public static float entityTagMaxDistance = 32.0F;

    public static boolean blockHighlightStorages = true;
    public static boolean blockHighlightSpawners = true;
    public static boolean blockHighlightOres = true;
    public static boolean blockHighlightThroughWalls = true;
    public static boolean blockHighlightFilledBox = true;
    public static float blockHighlightMaxDistance = 24.0F;
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
    private static final Set<String> XRAY_VISIBLE_BLOCK_IDS = new LinkedHashSet<>();

    public static boolean itemEspShowName = true;
    public static boolean itemEspShowDistance = true;
    public static boolean itemEspThroughWalls = true;
    public static float itemEspMaxDistance = 24.0F;

    public static boolean trajectoryBows = true;
    public static boolean trajectoryPearls = true;
    public static boolean trajectoryThrowables = true;
    public static boolean trajectoryPotions = true;
    public static int trajectoryMaxSteps = 120;

    public static boolean crosshairDynamicGap = true;
    public static int crosshairColorRgb = 0x55FFFF;
    public static float crosshairSize = 6.0F;
    public static float crosshairThickness = 2.0F;

    public static boolean antiBobRemoveViewBobbing = true;
    public static boolean antiBobRemoveHurtShake = true;

    public static boolean groundSpeedShowTitle = true;
    public static boolean groundSpeedShowUnit = true;
    public static int groundSpeedDecimalPlaces = 1;

    public static boolean radarPlayers = true;
    public static boolean radarMonsters = true;
    public static boolean radarAnimals = false;
    public static boolean radarRotateWithView = true;
    public static float radarMaxDistance = 48.0F;
    public static int radarSize = 90;

    public static boolean skeletonThroughWalls = true;
    public static float skeletonMaxDistance = 48.0F;
    public static float skeletonLineWidth = 1.7F;

    public static boolean blockOutlineFilledBox = false;
    public static float blockOutlineLineWidth = 2.0F;

    public static boolean entityInfoShowHealth = true;
    public static boolean entityInfoShowDistance = true;
    public static boolean entityInfoShowPosition = true;
    public static boolean entityInfoShowHeldItem = true;
    public static float entityInfoMaxDistance = 48.0F;

    private boolean brightnessApplied = false;
    private float previousGammaSetting = 1.0F;
    private boolean antiBobApplied = false;
    private boolean previousViewBobbing = false;

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
        private boolean enabled;

        private FeatureState(String id, String name, String description) {
            this.id = safe(id);
            this.name = safe(name);
            this.description = safe(description);
        }

        public boolean isEnabled() {
            return enabled;
        }

        private void setEnabledInternal(boolean enabled) {
            this.enabled = enabled;
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
        return ProfileManager.getCurrentProfileDir().resolve(CONFIG_FILE_NAME).toFile();
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

    public static void setEnabledTransient(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }

        boolean wasEnabled = state.isEnabled();
        state.setEnabledInternal(enabled);

        handleRuntimeSideEffectsAfterToggle(featureId, wasEnabled, enabled);
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
            brightnessSoftMode = false;
            brightnessGamma = 10.0F;
            break;
        case "no_fog":
            noFogRemoveLiquid = true;
            noFogBrightenColor = true;
            break;
        case "entity_visual":
            entityVisualPlayers = true;
            entityVisualMonsters = true;
            entityVisualAnimals = false;
            entityVisualThroughWalls = true;
            entityVisualFilledBox = true;
            entityVisualMaxDistance = 48.0F;
            break;
        case "tracer_line":
            tracerPlayers = true;
            tracerMonsters = true;
            tracerAnimals = false;
            tracerThroughWalls = true;
            tracerMaxDistance = 64.0F;
            tracerLineWidth = 1.6F;
            break;
        case "entity_tags":
            entityTagPlayers = true;
            entityTagMonsters = true;
            entityTagAnimals = false;
            entityTagShowHealth = true;
            entityTagShowDistance = true;
            entityTagShowHeldItem = false;
            entityTagMaxDistance = 32.0F;
            break;
        case "block_highlight":
            blockHighlightStorages = true;
            blockHighlightSpawners = true;
            blockHighlightOres = true;
            blockHighlightThroughWalls = true;
            blockHighlightFilledBox = true;
            blockHighlightMaxDistance = 24.0F;
            break;
        case "xray":
            resetXrayVisibleBlocksToDefaultInternal();
            requestXrayRendererReloadIfEnabled();
            break;
        case "item_esp":
            itemEspShowName = true;
            itemEspShowDistance = true;
            itemEspThroughWalls = true;
            itemEspMaxDistance = 24.0F;
            break;
        case "trajectory_line":
            trajectoryBows = true;
            trajectoryPearls = true;
            trajectoryThrowables = true;
            trajectoryPotions = true;
            trajectoryMaxSteps = 120;
            break;
        case "custom_crosshair":
            crosshairDynamicGap = true;
            crosshairColorRgb = 0x55FFFF;
            crosshairSize = 6.0F;
            crosshairThickness = 2.0F;
            break;
        case "anti_bob":
            antiBobRemoveViewBobbing = true;
            antiBobRemoveHurtShake = true;
            break;
        case "ground_speed":
            groundSpeedShowTitle = true;
            groundSpeedShowUnit = true;
            groundSpeedDecimalPlaces = 1;
            break;
        case "radar":
            radarPlayers = true;
            radarMonsters = true;
            radarAnimals = false;
            radarRotateWithView = true;
            radarMaxDistance = 48.0F;
            radarSize = 90;
            break;
        case "player_skeleton":
            skeletonThroughWalls = true;
            skeletonMaxDistance = 48.0F;
            skeletonLineWidth = 1.7F;
            break;
        case "block_outline":
            blockOutlineFilledBox = false;
            blockOutlineLineWidth = 2.0F;
            break;
        case "entity_info":
            entityInfoShowHealth = true;
            entityInfoShowDistance = true;
            entityInfoShowPosition = true;
            entityInfoShowHeldItem = true;
            entityInfoMaxDistance = 48.0F;
            break;
        default:
            break;
        }
    }

    public static String getFeatureRuntimeSummary(String featureId) {
        String normalizedId = normalizeId(featureId);
        switch (normalizedId) {
        case "brightness_boost":
            return isEnabled(normalizedId) ? "Gamma增强中，当前值 " + formatFloat(brightnessGamma) : "未启用";
        case "no_fog":
            return isEnabled(normalizedId)
                    ? "世界雾已压低" + (noFogRemoveLiquid ? "，液体雾同步移除" : "")
                    : "未启用";
        case "entity_visual":
            return isEnabled(normalizedId) ? "实体轮廓范围 " + formatFloat(entityVisualMaxDistance) + " 格" : "未启用";
        case "tracer_line":
            return isEnabled(normalizedId) ? "引导线范围 " + formatFloat(tracerMaxDistance) + " 格" : "未启用";
        case "entity_tags":
            return isEnabled(normalizedId) ? "头顶信息范围 " + formatFloat(entityTagMaxDistance) + " 格" : "未启用";
        case "block_highlight":
            return isEnabled(normalizedId) ? "高亮重要方块，矿石按类型自动配色，范围 " + formatFloat(blockHighlightMaxDistance) + " 格"
                    : "未启用";
        case "xray":
            return isEnabled(normalizedId) ? "仅渲染已选透视方块，当前 " + XRAY_VISIBLE_BLOCK_IDS.size() + " 项"
                    : "未启用";
        case "item_esp":
            return isEnabled(normalizedId) ? "掉落物高亮范围 " + formatFloat(itemEspMaxDistance) + " 格" : "未启用";
        case "trajectory_line":
            return isEnabled(normalizedId) ? "支持弓/珍珠/雪球/药水轨迹预估" : "未启用";
        case "custom_crosshair":
            return isEnabled(normalizedId)
                    ? "准星颜色 #" + String.format(Locale.ROOT, "%06X", crosshairColorRgb & 0xFFFFFF)
                    : "未启用";
        case "anti_bob":
            return isEnabled(normalizedId) ? "已抑制镜头晃动" : "未启用";
        case "ground_speed":
            return isEnabled(normalizedId)
                    ? "当前地速 " + formatGroundSpeed(getCurrentHorizontalSpeedBlocksPerSecond())
                            + (groundSpeedShowUnit ? " 格/秒" : "")
                    : "未启用";
        case "radar":
            return isEnabled(normalizedId) ? "雷达范围 " + formatFloat(radarMaxDistance) + " 格" : "未启用";
        case "player_skeleton":
            return isEnabled(normalizedId) ? "骨架范围 " + formatFloat(skeletonMaxDistance) + " 格" : "未启用";
        case "block_outline":
            return isEnabled(normalizedId) ? "高亮当前选中方块边框" : "未启用";
        case "entity_info":
            return isEnabled(normalizedId) ? "实体信息范围 " + formatFloat(entityInfoMaxDistance) + " 格" : "未启用";
        default:
            return "待机";
        }
    }

    public static boolean shouldSuppressViewBobbing() {
        return isEnabled("anti_bob") && antiBobRemoveViewBobbing;
    }

    public static boolean shouldSuppressHurtCamera() {
        return isEnabled("anti_bob") && antiBobRemoveHurtShake;
    }

    public static boolean isXrayBlockVisible(Block block) {
        String blockId = normalizeBlockId(block);
        return !blockId.isEmpty() && XRAY_VISIBLE_BLOCK_IDS.contains(blockId);
    }

    public static boolean isXrayBlockIdVisible(String rawBlockId) {
        String blockId = normalizeBlockId(rawBlockId);
        return !blockId.isEmpty() && XRAY_VISIBLE_BLOCK_IDS.contains(blockId);
    }

    public static List<String> getXrayVisibleBlockIds() {
        return sortBlockIds(XRAY_VISIBLE_BLOCK_IDS);
    }

    public static List<String> getXrayEditableBlockIds() {
        LinkedHashSet<String> editableIds = new LinkedHashSet<>();
        Collections.addAll(editableIds, DEFAULT_XRAY_BLOCK_IDS);
        editableIds.addAll(XRAY_VISIBLE_BLOCK_IDS);
        return sortBlockIds(editableIds);
    }

    public static Block resolveBlock(String rawBlockId) {
        String normalized = safe(rawBlockId).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        Block block = BlockUtils.stringToBlockNullable(normalized);
        return block == null || block == Blocks.AIR ? null : block;
    }

    public static String normalizeXrayBlockId(String rawBlockId) {
        return normalizeBlockId(rawBlockId);
    }

    public static boolean setXrayBlockVisible(String rawBlockId, boolean visible) {
        String blockId = normalizeBlockId(rawBlockId);
        if (blockId.isEmpty()) {
            return false;
        }

        boolean changed = visible ? XRAY_VISIBLE_BLOCK_IDS.add(blockId) : XRAY_VISIBLE_BLOCK_IDS.remove(blockId);
        if (changed) {
            requestXrayRendererReloadIfEnabled();
        }
        return changed;
    }

    public static void clearXrayVisibleBlocksWithoutSave() {
        if (XRAY_VISIBLE_BLOCK_IDS.isEmpty()) {
            return;
        }
        XRAY_VISIBLE_BLOCK_IDS.clear();
        requestXrayRendererReloadIfEnabled();
    }

    public static void resetXrayVisibleBlocksWithoutSave() {
        resetXrayVisibleBlocksToDefaultInternal();
        requestXrayRendererReloadIfEnabled();
    }

    public static void requestXrayRendererReload() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
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

        applyBrightness();
        applyAntiBob();
        RenderFeatureSupport.onClientTick(mc, player);
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
                && !noFogRemoveLiquid) {
            return;
        }
        event.setDensity(0.0F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (!isEnabled("no_fog") || event == null || !noFogBrightenColor) {
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
        Minecraft mc = Minecraft.getMinecraft();
        boolean brightnessEnabled = isEnabled("brightness_boost");
        boolean xrayEnabled = isEnabled("xray");
        if (!brightnessEnabled && !xrayEnabled) {
            restoreBrightness();
            return;
        }
        if (!brightnessApplied) {
            previousGammaSetting = mc.gameSettings.gammaSetting;
            brightnessApplied = true;
        }
        float targetGamma = 1.0F;
        if (brightnessEnabled) {
            targetGamma = brightnessSoftMode ? Math.max(4.0F, brightnessGamma * 0.55F)
                    : Math.max(1.0F, brightnessGamma);
        }
        if (xrayEnabled) {
            targetGamma = Math.max(targetGamma, 16.0F);
        }
        mc.gameSettings.gammaSetting = targetGamma;
    }

    private void restoreBrightness() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!brightnessApplied || mc == null || mc.gameSettings == null) {
            return;
        }
        mc.gameSettings.gammaSetting = previousGammaSetting;
        brightnessApplied = false;
    }

    private void applyAntiBob() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled("anti_bob") || !antiBobRemoveViewBobbing) {
            restoreAntiBob();
            return;
        }
        if (!antiBobApplied) {
            previousViewBobbing = mc.gameSettings.viewBobbing;
            antiBobApplied = true;
        }
        mc.gameSettings.viewBobbing = false;
    }

    private void restoreAntiBob() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!antiBobApplied || mc == null || mc.gameSettings == null) {
            return;
        }
        mc.gameSettings.viewBobbing = previousViewBobbing;
        antiBobApplied = false;
    }

    private static void applyDefaultValues() {
        brightnessSoftMode = false;
        brightnessGamma = 10.0F;
        noFogRemoveLiquid = true;
        noFogBrightenColor = true;
        entityVisualPlayers = true;
        entityVisualMonsters = true;
        entityVisualAnimals = false;
        entityVisualThroughWalls = true;
        entityVisualFilledBox = true;
        entityVisualMaxDistance = 48.0F;
        tracerPlayers = true;
        tracerMonsters = true;
        tracerAnimals = false;
        tracerThroughWalls = true;
        tracerMaxDistance = 64.0F;
        tracerLineWidth = 1.6F;
        entityTagPlayers = true;
        entityTagMonsters = true;
        entityTagAnimals = false;
        entityTagShowHealth = true;
        entityTagShowDistance = true;
        entityTagShowHeldItem = false;
        entityTagMaxDistance = 32.0F;
        blockHighlightStorages = true;
        blockHighlightSpawners = true;
        blockHighlightOres = true;
        blockHighlightThroughWalls = true;
        blockHighlightFilledBox = true;
        blockHighlightMaxDistance = 24.0F;
        resetXrayVisibleBlocksToDefaultInternal();
        itemEspShowName = true;
        itemEspShowDistance = true;
        itemEspThroughWalls = true;
        itemEspMaxDistance = 24.0F;
        trajectoryBows = true;
        trajectoryPearls = true;
        trajectoryThrowables = true;
        trajectoryPotions = true;
        trajectoryMaxSteps = 120;
        crosshairDynamicGap = true;
        crosshairColorRgb = 0x55FFFF;
        crosshairSize = 6.0F;
        crosshairThickness = 2.0F;
        antiBobRemoveViewBobbing = true;
        antiBobRemoveHurtShake = true;
        groundSpeedShowTitle = true;
        groundSpeedShowUnit = true;
        groundSpeedDecimalPlaces = 1;
        radarPlayers = true;
        radarMonsters = true;
        radarAnimals = false;
        radarRotateWithView = true;
        radarMaxDistance = 48.0F;
        radarSize = 90;
        skeletonThroughWalls = true;
        skeletonMaxDistance = 48.0F;
        skeletonLineWidth = 1.7F;
        blockOutlineFilledBox = false;
        blockOutlineLineWidth = 2.0F;
        entityInfoShowHealth = true;
        entityInfoShowDistance = true;
        entityInfoShowPosition = true;
        entityInfoShowHeldItem = true;
        entityInfoMaxDistance = 48.0F;
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
            JsonObject root = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();
            for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                entry.getValue().setEnabledInternal(root.has(entry.getKey() + "_enabled")
                        && root.get(entry.getKey() + "_enabled").getAsBoolean());
            }

            brightnessSoftMode = root.has("brightness_soft_mode") && root.get("brightness_soft_mode").getAsBoolean();
            brightnessGamma = root.has("brightness_gamma") ? root.get("brightness_gamma").getAsFloat() : 10.0F;
            noFogRemoveLiquid = !root.has("no_fog_remove_liquid") || root.get("no_fog_remove_liquid").getAsBoolean();
            noFogBrightenColor = !root.has("no_fog_brighten_color") || root.get("no_fog_brighten_color").getAsBoolean();
            entityVisualPlayers = !root.has("entity_visual_players") || root.get("entity_visual_players").getAsBoolean();
            entityVisualMonsters = !root.has("entity_visual_monsters") || root.get("entity_visual_monsters").getAsBoolean();
            entityVisualAnimals = root.has("entity_visual_animals") && root.get("entity_visual_animals").getAsBoolean();
            entityVisualThroughWalls = !root.has("entity_visual_through_walls") || root.get("entity_visual_through_walls").getAsBoolean();
            entityVisualFilledBox = !root.has("entity_visual_filled_box") || root.get("entity_visual_filled_box").getAsBoolean();
            entityVisualMaxDistance = root.has("entity_visual_max_distance") ? root.get("entity_visual_max_distance").getAsFloat() : 48.0F;
            tracerPlayers = !root.has("tracer_players") || root.get("tracer_players").getAsBoolean();
            tracerMonsters = !root.has("tracer_monsters") || root.get("tracer_monsters").getAsBoolean();
            tracerAnimals = root.has("tracer_animals") && root.get("tracer_animals").getAsBoolean();
            tracerThroughWalls = !root.has("tracer_through_walls") || root.get("tracer_through_walls").getAsBoolean();
            tracerMaxDistance = root.has("tracer_max_distance") ? root.get("tracer_max_distance").getAsFloat() : 64.0F;
            tracerLineWidth = root.has("tracer_line_width") ? root.get("tracer_line_width").getAsFloat() : 1.6F;
            entityTagPlayers = !root.has("entity_tag_players") || root.get("entity_tag_players").getAsBoolean();
            entityTagMonsters = !root.has("entity_tag_monsters") || root.get("entity_tag_monsters").getAsBoolean();
            entityTagAnimals = root.has("entity_tag_animals") && root.get("entity_tag_animals").getAsBoolean();
            entityTagShowHealth = !root.has("entity_tag_show_health") || root.get("entity_tag_show_health").getAsBoolean();
            entityTagShowDistance = !root.has("entity_tag_show_distance") || root.get("entity_tag_show_distance").getAsBoolean();
            entityTagShowHeldItem = root.has("entity_tag_show_held_item") && root.get("entity_tag_show_held_item").getAsBoolean();
            entityTagMaxDistance = root.has("entity_tag_max_distance") ? root.get("entity_tag_max_distance").getAsFloat() : 32.0F;
            blockHighlightStorages = !root.has("block_highlight_storages") || root.get("block_highlight_storages").getAsBoolean();
            blockHighlightSpawners = !root.has("block_highlight_spawners") || root.get("block_highlight_spawners").getAsBoolean();
            blockHighlightOres = !root.has("block_highlight_ores") || root.get("block_highlight_ores").getAsBoolean();
            blockHighlightThroughWalls = !root.has("block_highlight_through_walls") || root.get("block_highlight_through_walls").getAsBoolean();
            blockHighlightFilledBox = !root.has("block_highlight_filled_box") || root.get("block_highlight_filled_box").getAsBoolean();
            blockHighlightMaxDistance = root.has("block_highlight_max_distance") ? root.get("block_highlight_max_distance").getAsFloat() : 24.0F;
            loadXrayVisibleBlocks(root);
            itemEspShowName = !root.has("item_esp_show_name") || root.get("item_esp_show_name").getAsBoolean();
            itemEspShowDistance = !root.has("item_esp_show_distance") || root.get("item_esp_show_distance").getAsBoolean();
            itemEspThroughWalls = !root.has("item_esp_through_walls") || root.get("item_esp_through_walls").getAsBoolean();
            itemEspMaxDistance = root.has("item_esp_max_distance") ? root.get("item_esp_max_distance").getAsFloat() : 24.0F;
            trajectoryBows = !root.has("trajectory_bows") || root.get("trajectory_bows").getAsBoolean();
            trajectoryPearls = !root.has("trajectory_pearls") || root.get("trajectory_pearls").getAsBoolean();
            trajectoryThrowables = !root.has("trajectory_throwables") || root.get("trajectory_throwables").getAsBoolean();
            trajectoryPotions = !root.has("trajectory_potions") || root.get("trajectory_potions").getAsBoolean();
            trajectoryMaxSteps = root.has("trajectory_max_steps") ? root.get("trajectory_max_steps").getAsInt() : 120;
            crosshairDynamicGap = !root.has("crosshair_dynamic_gap") || root.get("crosshair_dynamic_gap").getAsBoolean();
            crosshairColorRgb = root.has("crosshair_color_rgb") ? root.get("crosshair_color_rgb").getAsInt() : 0x55FFFF;
            crosshairSize = root.has("crosshair_size") ? root.get("crosshair_size").getAsFloat() : 6.0F;
            crosshairThickness = root.has("crosshair_thickness") ? root.get("crosshair_thickness").getAsFloat() : 2.0F;
            antiBobRemoveViewBobbing = !root.has("anti_bob_remove_view_bobbing") || root.get("anti_bob_remove_view_bobbing").getAsBoolean();
            antiBobRemoveHurtShake = !root.has("anti_bob_remove_hurt_shake") || root.get("anti_bob_remove_hurt_shake").getAsBoolean();
            groundSpeedShowTitle = !root.has("ground_speed_show_title") || root.get("ground_speed_show_title").getAsBoolean();
            groundSpeedShowUnit = !root.has("ground_speed_show_unit") || root.get("ground_speed_show_unit").getAsBoolean();
            groundSpeedDecimalPlaces = root.has("ground_speed_decimal_places")
                    ? MathHelper.clamp(root.get("ground_speed_decimal_places").getAsInt(), 0, 3)
                    : 1;
            radarPlayers = !root.has("radar_players") || root.get("radar_players").getAsBoolean();
            radarMonsters = !root.has("radar_monsters") || root.get("radar_monsters").getAsBoolean();
            radarAnimals = root.has("radar_animals") && root.get("radar_animals").getAsBoolean();
            radarRotateWithView = !root.has("radar_rotate_with_view") || root.get("radar_rotate_with_view").getAsBoolean();
            radarMaxDistance = root.has("radar_max_distance") ? root.get("radar_max_distance").getAsFloat() : 48.0F;
            radarSize = root.has("radar_size") ? root.get("radar_size").getAsInt() : 90;
            skeletonThroughWalls = !root.has("skeleton_through_walls") || root.get("skeleton_through_walls").getAsBoolean();
            skeletonMaxDistance = root.has("skeleton_max_distance") ? root.get("skeleton_max_distance").getAsFloat() : 48.0F;
            skeletonLineWidth = root.has("skeleton_line_width") ? root.get("skeleton_line_width").getAsFloat() : 1.7F;
            blockOutlineFilledBox = root.has("block_outline_filled_box") && root.get("block_outline_filled_box").getAsBoolean();
            blockOutlineLineWidth = root.has("block_outline_line_width") ? root.get("block_outline_line_width").getAsFloat() : 2.0F;
            entityInfoShowHealth = !root.has("entity_info_show_health") || root.get("entity_info_show_health").getAsBoolean();
            entityInfoShowDistance = !root.has("entity_info_show_distance") || root.get("entity_info_show_distance").getAsBoolean();
            entityInfoShowPosition = !root.has("entity_info_show_position") || root.get("entity_info_show_position").getAsBoolean();
            entityInfoShowHeldItem = !root.has("entity_info_show_held_item") || root.get("entity_info_show_held_item").getAsBoolean();
            entityInfoMaxDistance = root.has("entity_info_max_distance") ? root.get("entity_info_max_distance").getAsFloat() : 48.0F;
            requestXrayRendererReload();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载渲染功能配置失败", e);
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            JsonObject root = new JsonObject();
            for (FeatureState state : FEATURES.values()) {
                root.addProperty(state.id + "_enabled", state.isEnabled());
            }
            root.addProperty("brightness_soft_mode", brightnessSoftMode);
            root.addProperty("brightness_gamma", brightnessGamma);
            root.addProperty("no_fog_remove_liquid", noFogRemoveLiquid);
            root.addProperty("no_fog_brighten_color", noFogBrightenColor);
            root.addProperty("entity_visual_players", entityVisualPlayers);
            root.addProperty("entity_visual_monsters", entityVisualMonsters);
            root.addProperty("entity_visual_animals", entityVisualAnimals);
            root.addProperty("entity_visual_through_walls", entityVisualThroughWalls);
            root.addProperty("entity_visual_filled_box", entityVisualFilledBox);
            root.addProperty("entity_visual_max_distance", entityVisualMaxDistance);
            root.addProperty("tracer_players", tracerPlayers);
            root.addProperty("tracer_monsters", tracerMonsters);
            root.addProperty("tracer_animals", tracerAnimals);
            root.addProperty("tracer_through_walls", tracerThroughWalls);
            root.addProperty("tracer_max_distance", tracerMaxDistance);
            root.addProperty("tracer_line_width", tracerLineWidth);
            root.addProperty("entity_tag_players", entityTagPlayers);
            root.addProperty("entity_tag_monsters", entityTagMonsters);
            root.addProperty("entity_tag_animals", entityTagAnimals);
            root.addProperty("entity_tag_show_health", entityTagShowHealth);
            root.addProperty("entity_tag_show_distance", entityTagShowDistance);
            root.addProperty("entity_tag_show_held_item", entityTagShowHeldItem);
            root.addProperty("entity_tag_max_distance", entityTagMaxDistance);
            root.addProperty("block_highlight_storages", blockHighlightStorages);
            root.addProperty("block_highlight_spawners", blockHighlightSpawners);
            root.addProperty("block_highlight_ores", blockHighlightOres);
            root.addProperty("block_highlight_through_walls", blockHighlightThroughWalls);
            root.addProperty("block_highlight_filled_box", blockHighlightFilledBox);
            root.addProperty("block_highlight_max_distance", blockHighlightMaxDistance);
            JsonArray xrayBlocks = new JsonArray();
            for (String blockId : getXrayVisibleBlockIds()) {
                xrayBlocks.add(blockId);
            }
            root.add("xray_visible_blocks", xrayBlocks);
            root.addProperty("item_esp_show_name", itemEspShowName);
            root.addProperty("item_esp_show_distance", itemEspShowDistance);
            root.addProperty("item_esp_through_walls", itemEspThroughWalls);
            root.addProperty("item_esp_max_distance", itemEspMaxDistance);
            root.addProperty("trajectory_bows", trajectoryBows);
            root.addProperty("trajectory_pearls", trajectoryPearls);
            root.addProperty("trajectory_throwables", trajectoryThrowables);
            root.addProperty("trajectory_potions", trajectoryPotions);
            root.addProperty("trajectory_max_steps", trajectoryMaxSteps);
            root.addProperty("crosshair_dynamic_gap", crosshairDynamicGap);
            root.addProperty("crosshair_color_rgb", crosshairColorRgb);
            root.addProperty("crosshair_size", crosshairSize);
            root.addProperty("crosshair_thickness", crosshairThickness);
            root.addProperty("anti_bob_remove_view_bobbing", antiBobRemoveViewBobbing);
            root.addProperty("anti_bob_remove_hurt_shake", antiBobRemoveHurtShake);
            root.addProperty("ground_speed_show_title", groundSpeedShowTitle);
            root.addProperty("ground_speed_show_unit", groundSpeedShowUnit);
            root.addProperty("ground_speed_decimal_places", MathHelper.clamp(groundSpeedDecimalPlaces, 0, 3));
            root.addProperty("radar_players", radarPlayers);
            root.addProperty("radar_monsters", radarMonsters);
            root.addProperty("radar_animals", radarAnimals);
            root.addProperty("radar_rotate_with_view", radarRotateWithView);
            root.addProperty("radar_max_distance", radarMaxDistance);
            root.addProperty("radar_size", radarSize);
            root.addProperty("skeleton_through_walls", skeletonThroughWalls);
            root.addProperty("skeleton_max_distance", skeletonMaxDistance);
            root.addProperty("skeleton_line_width", skeletonLineWidth);
            root.addProperty("block_outline_filled_box", blockOutlineFilledBox);
            root.addProperty("block_outline_line_width", blockOutlineLineWidth);
            root.addProperty("entity_info_show_health", entityInfoShowHealth);
            root.addProperty("entity_info_show_distance", entityInfoShowDistance);
            root.addProperty("entity_info_show_position", entityInfoShowPosition);
            root.addProperty("entity_info_show_held_item", entityInfoShowHeldItem);
            root.addProperty("entity_info_max_distance", entityInfoMaxDistance);
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存渲染功能配置失败", e);
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String formatGroundSpeed(float value) {
        int decimals = MathHelper.clamp(groundSpeedDecimalPlaces, 0, 3);
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
        XRAY_VISIBLE_BLOCK_IDS.clear();
        for (String blockId : DEFAULT_XRAY_BLOCK_IDS) {
            String normalized = normalizeBlockId(blockId);
            if (!normalized.isEmpty()) {
                XRAY_VISIBLE_BLOCK_IDS.add(normalized);
            }
        }
    }

    private static void loadXrayVisibleBlocks(JsonObject root) {
        resetXrayVisibleBlocksToDefaultInternal();
        if (root == null || !root.has("xray_visible_blocks") || !root.get("xray_visible_blocks").isJsonArray()) {
            return;
        }

        XRAY_VISIBLE_BLOCK_IDS.clear();
        for (JsonElement element : root.getAsJsonArray("xray_visible_blocks")) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            String blockId = normalizeBlockId(element.getAsString());
            if (!blockId.isEmpty()) {
                XRAY_VISIBLE_BLOCK_IDS.add(blockId);
            }
        }

        if (XRAY_VISIBLE_BLOCK_IDS.isEmpty()) {
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
