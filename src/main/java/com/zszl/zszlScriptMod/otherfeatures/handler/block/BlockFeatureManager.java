package com.zszl.zszlScriptMod.otherfeatures.handler.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockFeatureManager {

    public static final BlockFeatureManager INSTANCE = new BlockFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_block.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();

    private static final EnumFacing[] PLACE_SEARCH_ORDER = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP
    };

    private static Field rightClickDelayTimerField;
    private static Field blockHitDelayField;

    private int autoToolCooldownTicks = 0;
    private int placeAssistCooldownTicks = 0;
    private int blockSwapLockTicks = 0;
    private int lastAutoToolHotbarSlot = -1;
    private int lockedHotbarSlot = -1;
    private BlockPos lastAssistPlacePos = null;
    
    // auto_light 运行时状态
    private int autoLightCooldownTicks = 0;
    private BlockPos lastTorchPlacePos = null;
    
    // block_refill 运行时状态
    private int blockRefillCooldownTicks = 0;
    private int lastRefillSlot = -1;
    
    // ghost_hand_block 运行时状态
    private int ghostHandCooldownTicks = 0;
    private BlockPos lastGhostInteractPos = null;
    
    // surround 运行时状态
    private int surroundCooldownTicks = 0;
    private List<BlockPos> surroundPositions = new ArrayList<>();

    static {
        register(new FeatureState("auto_tool", "自动切工具",
                "挖方块时自动切到热栏中更适合当前方块的工具。优先保证稳定，不做激进切换。左键快速开关，右键打开方块设置。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("fast_place", "快速放置",
                "仅在手持方块时缩短右键放置延迟，不影响吃东西、喝药水和投掷物使用。左键快速开关，右键打开方块设置。",
                "放置延迟", 1.0F, 0.0F, 4.0F, true));
        register(new FeatureState("place_assist", "精准放置辅助",
                "当边缘放置或点到空气导致放不上时，尝试自动补一个合法支撑面与朝向，减少空点失败。左键快速开关，右键打开方块设置。",
                "搜索半径", 1.0F, 1.0F, 3.0F, true));
        register(new FeatureState("fast_break", "基础快速挖掘",
                "保守降低客户端挖掘打击延迟，让连挖和打击手感更顺，不做高风险秒挖。左键快速开关，右键打开方块设置。",
                "挖掘延迟", 1.0F, 0.0F, 4.0F, true));
        register(new FeatureState("block_swap_lock", "方块热栏锁定",
                "放置建筑方块时短暂锁定当前热栏槽位，防止自动切工具在搭建过程中打乱手感。左键快速开关，右键打开方块设置。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("auto_light", "自动补光",
                "移动或挖掘时自动在周围放置火把，适合洞穴探索和挂机路线。左键快速开关，右键打开方块设置。",
                "光照阈值", 7.0F, 0.0F, 15.0F, true));
        register(new FeatureState("block_refill", "方块自动补栏",
                "热栏里的建筑方块快没了时，从背包自动补到原槽位。左键快速开关，右键打开方块设置。",
                "补充阈值", 16.0F, 1.0F, 64.0F, true));
        register(new FeatureState("ghost_hand_block", "穿墙方块交互",
                "隔墙拆/放/开容器等高风险功能，可能被服务器检测。左键快速开关，右键打开方块设置。",
                "交互距离", 6.0F, 4.5F, 10.0F, true));
        register(new FeatureState("surround", "自动围身",
                "自动在脚边放方块保护自身，防止被攻击。左键快速开关，右键打开方块设置。",
                null, 0.0F, 0.0F, 0.0F, true));

        loadConfig();
    }

    private BlockFeatureManager() {
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

        private boolean enabled;
        private float value;
        private boolean statusHudEnabled;

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
            this.enabled = false;
            this.value = defaultValue;
            this.statusHudEnabled = true;
        }

        public boolean supportsValue() {
            return !valueLabel.isEmpty() && maxValue > minValue;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public float getValue() {
            return value;
        }

        public boolean isStatusHudEnabled() {
            return statusHudEnabled;
        }

        private void setEnabledInternal(boolean enabled) {
            this.enabled = enabled;
        }

        private void setValueInternal(float value) {
            if (!supportsValue()) {
                this.value = defaultValue;
                return;
            }
            this.value = MathHelper.clamp(value, minValue, maxValue);
        }

        private void setStatusHudEnabledInternal(boolean statusHudEnabled) {
            this.statusHudEnabled = statusHudEnabled;
        }

        private void resetToDefaultInternal() {
            this.enabled = false;
            this.value = defaultValue;
            this.statusHudEnabled = true;
        }
    }

    private static final class PlacementTarget {
        private final BlockPos placePos;
        private final BlockPos supportPos;
        private final EnumFacing supportFace;
        private final Vec3d hitVec;

        private PlacementTarget(BlockPos placePos, BlockPos supportPos, EnumFacing supportFace, Vec3d hitVec) {
            this.placePos = placePos;
            this.supportPos = supportPos;
            this.supportFace = supportFace;
            this.hitVec = hitVec;
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

    public static boolean isFeatureStatusHudEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isStatusHudEnabled();
    }

    public static boolean shouldDisplayFeatureStatusHud(String featureId) {
        return MovementFeatureManager.isMasterStatusHudEnabled() && isFeatureStatusHudEnabled(featureId);
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
        saveConfig();
    }

    public static void resetAllToDefaults() {
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        saveConfig();
    }

    public static float getConfiguredValue(String featureId, float fallback) {
        FeatureState state = getFeature(featureId);
        if (state == null || !state.supportsValue()) {
            return fallback;
        }
        return MathHelper.clamp(state.getValue(), state.minValue, state.maxValue);
    }

    public static List<String> getStatusLines() {
        return getStatusLines(false);
    }

    public static List<String> getStatusLines(boolean forcePreview) {
        if (!forcePreview && !MovementFeatureManager.isMasterStatusHudEnabled()) {
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

        lines.add("§a[方块交互] §f" + activeNames.size() + " 项开启");
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

    public static void loadConfig() {
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                saveConfig();
                return;
            }

            JsonObject root = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();
            JsonObject featuresObject = root.has("features") && root.get("features").isJsonObject()
                    ? root.getAsJsonObject("features")
                    : new JsonObject();

            for (Map.Entry<String, FeatureState> entry : FEATURES.entrySet()) {
                if (!featuresObject.has(entry.getKey()) || !featuresObject.get(entry.getKey()).isJsonObject()) {
                    continue;
                }
                JsonObject item = featuresObject.getAsJsonObject(entry.getKey());
                FeatureState state = entry.getValue();
                if (item.has("enabled")) {
                    state.setEnabledInternal(item.get("enabled").getAsBoolean());
                }
                if (item.has("statusHudEnabled")) {
                    state.setStatusHudEnabledInternal(item.get("statusHudEnabled").getAsBoolean());
                }
                if (item.has("value")) {
                    state.setValueInternal(item.get("value").getAsFloat());
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载方块功能配置失败", e);
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            JsonObject featuresObject = new JsonObject();
            for (FeatureState state : FEATURES.values()) {
                JsonObject item = new JsonObject();
                item.addProperty("enabled", state.isEnabled());
                item.addProperty("statusHudEnabled", state.isStatusHudEnabled());
                if (state.supportsValue()) {
                    item.addProperty("value", state.getValue());
                }
                featuresObject.add(state.id, item);
            }
            root.add("features", featuresObject);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存方块功能配置失败", e);
        }
    }

    public void onClientDisconnect() {
        resetRuntimeState();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (mc.world == null || player == null) {
            resetRuntimeState();
            return;
        }

        tickRuntimeState();

        if (player.isDead || player.getHealth() <= 0.0F) {
            resetRuntimeState();
            return;
        }

        refreshBlockSwapLock(mc, player);
        applyAutoTool(mc, player);
        applyFastBreak(mc);
        applyFastPlace(mc, player);
        applyPlaceAssist(mc, player);
        applyAutoLight(mc, player);
        applyBlockRefill(mc, player);
        applyGhostHandBlock(mc, player);
        applySurround(mc, player);
    }

    private void tickRuntimeState() {
        if (autoToolCooldownTicks > 0) {
            autoToolCooldownTicks--;
        }
        if (placeAssistCooldownTicks > 0) {
            placeAssistCooldownTicks--;
        }
        if (blockSwapLockTicks > 0) {
            blockSwapLockTicks--;
        } else {
            lockedHotbarSlot = -1;
        }
        if (autoLightCooldownTicks > 0) {
            autoLightCooldownTicks--;
        }
        if (blockRefillCooldownTicks > 0) {
            blockRefillCooldownTicks--;
        }
        if (ghostHandCooldownTicks > 0) {
            ghostHandCooldownTicks--;
        }
        if (surroundCooldownTicks > 0) {
            surroundCooldownTicks--;
        }
    }

    private void resetRuntimeState() {
        autoToolCooldownTicks = 0;
        placeAssistCooldownTicks = 0;
        blockSwapLockTicks = 0;
        lastAutoToolHotbarSlot = -1;
        lockedHotbarSlot = -1;
        lastAssistPlacePos = null;
        autoLightCooldownTicks = 0;
        lastTorchPlacePos = null;
        blockRefillCooldownTicks = 0;
        lastRefillSlot = -1;
        ghostHandCooldownTicks = 0;
        lastGhostInteractPos = null;
        surroundCooldownTicks = 0;
        surroundPositions.clear();
    }

    private void refreshBlockSwapLock(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("block_swap_lock")) {
            blockSwapLockTicks = 0;
            lockedHotbarSlot = -1;
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (Mouse.isButtonDown(1) && isHoldingPlaceableBlock(player)) {
            lockedHotbarSlot = player.inventory.currentItem;
            blockSwapLockTicks = 8;
        }
    }

    private void applyAutoTool(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("auto_tool")
                || autoToolCooldownTicks > 0
                || mc.currentScreen != null
                || mc.world == null
                || mc.playerController == null
                || player.capabilities.isCreativeMode
                || !Mouse.isButtonDown(0)) {
            return;
        }

        if (blockSwapLockTicks > 0 && lockedHotbarSlot >= 0) {
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null) {
            return;
        }

        IBlockState state = mc.world.getBlockState(hit.getBlockPos());
        if (state == null || state.getMaterial().isLiquid() || state.getBlock().isAir(state, mc.world, hit.getBlockPos())) {
            return;
        }

        int currentSlot = player.inventory.currentItem;
        int bestSlot = findBestToolHotbarSlot(player, state);
        if (bestSlot < 0 || bestSlot == currentSlot) {
            return;
        }

        double currentScore = getToolScore(player.inventory.getStackInSlot(currentSlot), state);
        double bestScore = getToolScore(player.inventory.getStackInSlot(bestSlot), state);
        if (bestScore <= Math.max(1.0D, currentScore + 0.15D)) {
            return;
        }

        if (ModUtils.switchToHotbarSlot(bestSlot + 1)) {
            autoToolCooldownTicks = 2;
            lastAutoToolHotbarSlot = bestSlot;
        }
    }

    private int findBestToolHotbarSlot(EntityPlayerSP player, IBlockState state) {
        int bestSlot = -1;
        double bestScore = 0.0D;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            double score = getToolScore(stack, state);
            if (score > bestScore + 1.0E-4D) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private double getToolScore(ItemStack stack, IBlockState state) {
        if (stack == null || stack.isEmpty() || state == null) {
            return 0.0D;
        }

        float destroySpeed = stack.getDestroySpeed(state);
        boolean canHarvest = false;
        try {
            canHarvest = stack.canHarvestBlock(state);
        } catch (Exception ignored) {
        }

        if (destroySpeed <= 1.0F && !canHarvest) {
            return 0.0D;
        }

        double score = destroySpeed;
        if (canHarvest) {
            score += 32.0D;
        }
        if (stack.getItem() instanceof ItemBlock) {
            score -= 4.0D;
        }
        return score;
    }

    private void applyFastBreak(Minecraft mc) {
        if (!isEnabled("fast_break")
                || mc.currentScreen != null
                || mc.playerController == null
                || !Mouse.isButtonDown(0)) {
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null) {
            return;
        }

        int targetDelay = MathHelper.clamp(Math.round(getConfiguredValue("fast_break", 1.0F)), 0, 4);
        setBlockHitDelayAtMost(mc.playerController, targetDelay);
    }

    private void applyFastPlace(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("fast_place")
                || mc.currentScreen != null
                || !Mouse.isButtonDown(1)
                || !isHoldingPlaceableBlock(player)) {
            return;
        }

        int targetDelay = MathHelper.clamp(Math.round(getConfiguredValue("fast_place", 1.0F)), 0, 4);
        setRightClickDelayTimerAtMost(mc, targetDelay);
    }

    private void applyPlaceAssist(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("place_assist")
                || placeAssistCooldownTicks > 0
                || mc.currentScreen != null
                || mc.playerController == null
                || !Mouse.isButtonDown(1)
                || !isHoldingPlaceableBlock(player)) {
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY) {
            return;
        }

        if (isVanillaPlacementLikelyEnough(mc, hit)) {
            return;
        }

        int searchRadius = MathHelper.clamp(Math.round(getConfiguredValue("place_assist", 1.0F)), 1, 3);
        PlacementTarget target = findAssistPlacement(player, hit, searchRadius);
        if (target == null) {
            return;
        }

        EnumActionResult result = mc.playerController.processRightClickBlock(player, mc.world, target.supportPos,
                target.supportFace, target.hitVec, EnumHand.MAIN_HAND);
        if (result == EnumActionResult.SUCCESS) {
            player.swingArm(EnumHand.MAIN_HAND);
            placeAssistCooldownTicks = 2;
            lastAssistPlacePos = target.placePos;
            if (isEnabled("block_swap_lock")) {
                lockedHotbarSlot = player.inventory.currentItem;
                blockSwapLockTicks = 10;
            }
            if (isEnabled("fast_place")) {
                int targetDelay = MathHelper.clamp(Math.round(getConfiguredValue("fast_place", 1.0F)), 0, 4);
                setRightClickDelayTimerAtMost(mc, targetDelay);
            }
        }
    }

    private boolean isVanillaPlacementLikelyEnough(Minecraft mc, RayTraceResult hit) {
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null || hit.sideHit == null) {
            return false;
        }

        BlockPos primary = resolvePrimaryPlacePos(mc.world, hit);
        if (primary == null) {
            return false;
        }
        if (!isReplaceable(mc.world, primary)) {
            return false;
        }
        return buildPlacementTarget(mc.player, primary) != null;
    }

    private PlacementTarget findAssistPlacement(EntityPlayerSP player, RayTraceResult hit, int searchRadius) {
        World world = player.world;
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d referencePoint;
        BlockPos anchor;

        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && hit.getBlockPos() != null) {
            anchor = resolvePrimaryPlacePos(world, hit);
            referencePoint = hit.hitVec != null
                    ? hit.hitVec
                    : new Vec3d(anchor).addVector(0.5D, 0.5D, 0.5D);
        } else {
            referencePoint = eyePos.add(player.getLookVec().scale(4.0D));
            anchor = new BlockPos(referencePoint);
        }

        PlacementTarget best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos candidate = anchor.add(dx, dy, dz);
                    if (!isReplaceable(world, candidate)) {
                        continue;
                    }

                    PlacementTarget target = buildPlacementTarget(player, candidate);
                    if (target == null) {
                        continue;
                    }

                    double score = referencePoint.squareDistanceTo(
                            new Vec3d(candidate).addVector(0.5D, 0.5D, 0.5D));
                    if (best == null || score < bestScore) {
                        best = target;
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private PlacementTarget buildPlacementTarget(EntityPlayerSP player, BlockPos placePos) {
        if (player == null || player.world == null || placePos == null || !isReplaceable(player.world, placePos)) {
            return null;
        }

        Vec3d eyePos = player.getPositionEyes(1.0F);
        for (EnumFacing facing : PLACE_SEARCH_ORDER) {
            BlockPos supportPos = placePos.offset(facing);
            if (!canPlaceAgainst(player.world, supportPos)) {
                continue;
            }

            EnumFacing supportFace = facing.getOpposite();
            Vec3d hitVec = new Vec3d(
                    supportPos.getX() + 0.5D + supportFace.getFrontOffsetX() * 0.5D,
                    supportPos.getY() + 0.5D + supportFace.getFrontOffsetY() * 0.5D,
                    supportPos.getZ() + 0.5D + supportFace.getFrontOffsetZ() * 0.5D);

            if (eyePos.squareDistanceTo(hitVec) > 36.0D) {
                continue;
            }

            RayTraceResult rayTrace = player.world.rayTraceBlocks(eyePos, hitVec, false, true, false);
            if (rayTrace != null && rayTrace.typeOfHit == RayTraceResult.Type.BLOCK && rayTrace.getBlockPos() != null) {
                BlockPos hitPos = rayTrace.getBlockPos();
                if (!supportPos.equals(hitPos) && !placePos.equals(hitPos)) {
                    continue;
                }
            }

            return new PlacementTarget(placePos, supportPos, supportFace, hitVec);
        }

        return null;
    }

    private BlockPos resolvePrimaryPlacePos(World world, RayTraceResult hit) {
        if (world == null || hit == null || hit.getBlockPos() == null || hit.sideHit == null) {
            return null;
        }

        BlockPos hitPos = hit.getBlockPos();
        IBlockState hitState = world.getBlockState(hitPos);
        if (hitState != null && hitState.getMaterial().isReplaceable()) {
            return hitPos;
        }
        return hitPos.offset(hit.sideHit);
    }

    private boolean isHoldingPlaceableBlock(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }

        ItemStack stack = player.getHeldItemMainhand();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }

        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != null && block.getDefaultState() != null && !block.getDefaultState().getMaterial().isLiquid();
    }

    private boolean isReplaceable(World world, BlockPos pos) {
        return world != null && pos != null && world.getBlockState(pos).getMaterial().isReplaceable();
    }

    private boolean canPlaceAgainst(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        if (state == null || state.getMaterial().isReplaceable()) {
            return false;
        }
        if (state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB) {
            return false;
        }
        TileEntity tileEntity = world.getTileEntity(pos);
        return tileEntity == null;
    }

    private void setRightClickDelayTimerAtMost(Minecraft mc, int targetDelay) {
        try {
            Field field = resolveRightClickDelayTimerField();
            if (field == null) {
                return;
            }
            int current = field.getInt(mc);
            if (current > targetDelay) {
                field.setInt(mc, targetDelay);
            }
        } catch (Exception ignored) {
        }
    }

    private void setBlockHitDelayAtMost(PlayerControllerMP controller, int targetDelay) {
        try {
            Field field = resolveBlockHitDelayField();
            if (field == null) {
                return;
            }
            int current = field.getInt(controller);
            if (current > targetDelay) {
                field.setInt(controller, targetDelay);
            }
        } catch (Exception ignored) {
        }
    }

    private Field resolveRightClickDelayTimerField() {
        if (rightClickDelayTimerField != null) {
            return rightClickDelayTimerField;
        }
        rightClickDelayTimerField = findIntField(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
        return rightClickDelayTimerField;
    }

    private Field resolveBlockHitDelayField() {
        if (blockHitDelayField != null) {
            return blockHitDelayField;
        }
        blockHitDelayField = findIntField(PlayerControllerMP.class, "blockHitDelay", "field_78781_i");
        return blockHitDelayField;
    }

    private Field findIntField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String getRuntimeHudLine() {
        List<String> parts = new ArrayList<>();
        if (isEnabled("auto_tool") && shouldDisplayFeatureStatusHud("auto_tool") && lastAutoToolHotbarSlot >= 0
                && autoToolCooldownTicks > 0) {
            parts.add("§e切到槽 " + (lastAutoToolHotbarSlot + 1));
        }
        if (isEnabled("place_assist") && shouldDisplayFeatureStatusHud("place_assist") && placeAssistCooldownTicks > 0
                && lastAssistPlacePos != null) {
            parts.add("§b辅助放置");
        }
        if (isEnabled("block_swap_lock") && shouldDisplayFeatureStatusHud("block_swap_lock") && blockSwapLockTicks > 0
                && lockedHotbarSlot >= 0) {
            parts.add("§a建筑锁 " + (lockedHotbarSlot + 1));
        }
        if (isEnabled("auto_light") && shouldDisplayFeatureStatusHud("auto_light") && autoLightCooldownTicks > 0
                && lastTorchPlacePos != null) {
            parts.add("§6补光");
        }
        if (isEnabled("block_refill") && shouldDisplayFeatureStatusHud("block_refill") && blockRefillCooldownTicks > 0
                && lastRefillSlot >= 0) {
            parts.add("§d补栏 " + (lastRefillSlot + 1));
        }
        if (isEnabled("ghost_hand_block") && shouldDisplayFeatureStatusHud("ghost_hand_block") && ghostHandCooldownTicks > 0
                && lastGhostInteractPos != null) {
            parts.add("§c穿墙交互");
        }
        if (isEnabled("surround") && shouldDisplayFeatureStatusHud("surround") && !surroundPositions.isEmpty()) {
            parts.add("§9围身 " + surroundPositions.size());
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

        switch (featureId) {
        case "auto_tool":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            return lastAutoToolHotbarSlot >= 0 && autoToolCooldownTicks > 0
                    ? "最近切换到热栏 " + (lastAutoToolHotbarSlot + 1)
                    : "挖方块时自动切换到更合适的工具";
        case "fast_place":
            return isEnabled(featureId)
                    ? "方块放置延迟上限 " + formatInt(getConfiguredValue(featureId, 1.0F)) + " tick"
                    : "未启用";
        case "place_assist":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (placeAssistCooldownTicks > 0 && lastAssistPlacePos != null) {
                return "刚辅助放置到 " + formatBlockPos(lastAssistPlacePos);
            }
            return "待机，点空气或边缘失败时尝试补合法支撑面";
        case "fast_break":
            return isEnabled(featureId)
                    ? "挖掘延迟上限 " + formatInt(getConfiguredValue(featureId, 1.0F)) + " tick"
                    : "未启用";
        case "block_swap_lock":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (blockSwapLockTicks > 0 && lockedHotbarSlot >= 0) {
                return "当前锁定热栏 " + (lockedHotbarSlot + 1) + "，剩余 " + blockSwapLockTicks + " tick";
            }
            return "待机，放置建筑方块时会短暂保持当前热栏";
        case "auto_light":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (autoLightCooldownTicks > 0 && lastTorchPlacePos != null) {
                return "刚在 " + formatBlockPos(lastTorchPlacePos) + " 放置火把";
            }
            return "待机，光照低于 " + formatInt(getConfiguredValue(featureId, 7.0F)) + " 时自动放火把";
        case "block_refill":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (blockRefillCooldownTicks > 0 && lastRefillSlot >= 0) {
                return "刚补充热栏 " + (lastRefillSlot + 1);
            }
            return "待机，方块少于 " + formatInt(getConfiguredValue(featureId, 16.0F)) + " 时自动补充";
        case "ghost_hand_block":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (ghostHandCooldownTicks > 0 && lastGhostInteractPos != null) {
                return "刚与 " + formatBlockPos(lastGhostInteractPos) + " 交互";
            }
            return "待机，可在 " + getConfiguredValue(featureId, 6.0F) + " 格距离内穿墙交互";
        case "surround":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            if (!surroundPositions.isEmpty()) {
                return "已放置 " + surroundPositions.size() + " 个保护方块";
            }
            return "待机，自动在脚边放置保护方块";
        default:
            FeatureState state = getFeature(featureId);
            if (state == null) {
                return "未找到功能";
            }
            return state.behaviorImplemented ? "基础逻辑已接入" : "仅配置占位";
        }
    }

    private String formatInt(float value) {
        return String.valueOf(Math.round(value));
    }

    private String formatBlockPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== 自动补光功能 ====================
    private void applyAutoLight(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("auto_light")
                || autoLightCooldownTicks > 0
                || mc.currentScreen != null
                || mc.world == null
                || player.capabilities.isCreativeMode) {
            return;
        }

        int lightThreshold = MathHelper.clamp(Math.round(getConfiguredValue("auto_light", 7.0F)), 0, 15);
        BlockPos playerPos = player.getPosition();
        
        // 检查玩家脚下光照
        int currentLight = mc.world.getLight(playerPos);
        if (currentLight >= lightThreshold) {
            return;
        }

        // 查找火把
        int torchSlot = findTorchInInventory(player);
        if (torchSlot < 0) {
            return;
        }

        // 寻找合适的放置位置
        BlockPos placePos = findTorchPlacePosition(mc.world, playerPos);
        if (placePos == null || placePos.equals(lastTorchPlacePos)) {
            return;
        }

        // 切换到火把并放置
        int originalSlot = player.inventory.currentItem;
        if (ModUtils.switchToHotbarSlot(torchSlot + 1)) {
            PlacementTarget target = buildPlacementTarget(player, placePos);
            if (target != null) {
                EnumActionResult result = mc.playerController.processRightClickBlock(
                        player, mc.world, target.supportPos, target.supportFace, target.hitVec, EnumHand.MAIN_HAND);
                if (result == EnumActionResult.SUCCESS) {
                    lastTorchPlacePos = placePos;
                    autoLightCooldownTicks = 20;
                }
            }
            ModUtils.switchToHotbarSlot(originalSlot + 1);
        }
    }

    private int findTorchInInventory(EntityPlayerSP player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block != null && block.getUnlocalizedName().toLowerCase().contains("torch")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private BlockPos findTorchPlacePosition(World world, BlockPos center) {
        // 优先在地面放置
        BlockPos ground = center.down();
        if (canPlaceAgainst(world, ground) && isReplaceable(world, center)) {
            return center;
        }

        // 尝试周围墙壁
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos wall = center.offset(facing);
            BlockPos placePos = center;
            if (canPlaceAgainst(world, wall) && isReplaceable(world, placePos)) {
                return placePos;
            }
        }

        return null;
    }

    // ==================== 方块自动补栏功能 ====================
    private void applyBlockRefill(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("block_refill")
                || blockRefillCooldownTicks > 0
                || mc.currentScreen != null
                || player.capabilities.isCreativeMode
                || mc.playerController == null) {
            return;
        }

        int refillThreshold = MathHelper.clamp(Math.round(getConfiguredValue("block_refill", 16.0F)), 1, 64);
        Container activeContainer = player.openContainer != null ? player.openContainer : player.inventoryContainer;
        if (activeContainer == null) {
            return;
        }

        // 检查热栏中的建筑方块
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack hotbarStack = player.inventory.getStackInSlot(hotbarSlot);
            if (hotbarStack == null || hotbarStack.isEmpty()) {
                continue;
            }

            if (!(hotbarStack.getItem() instanceof ItemBlock)) {
                continue;
            }

            if (hotbarStack.getCount() >= refillThreshold) {
                continue;
            }

            // 在背包中查找相同方块
            int sourceInventorySlot = -1;
            int sourceContainerSlot = -1;
            int bestSourceCount = hotbarStack.getCount();
            for (int invSlot = 9; invSlot < 36; invSlot++) {
                ItemStack invStack = player.inventory.getStackInSlot(invSlot);
                if (invStack == null || invStack.isEmpty()) {
                    continue;
                }

                if (ItemStack.areItemsEqual(hotbarStack, invStack) 
                        && ItemStack.areItemStackTagsEqual(hotbarStack, invStack)) {
                    int containerSlot = findContainerSlotForPlayerInventoryIndex(activeContainer, player, invSlot);
                    if (containerSlot < 0 || invStack.getCount() <= bestSourceCount) {
                        continue;
                    }
                    sourceInventorySlot = invSlot;
                    sourceContainerSlot = containerSlot;
                    bestSourceCount = invStack.getCount();
                }
            }

            if (sourceInventorySlot < 0 || sourceContainerSlot < 0) {
                continue;
            }

            try {
                mc.playerController.windowClick(
                        activeContainer.windowId,
                        sourceContainerSlot,
                        hotbarSlot,
                        ClickType.SWAP,
                        player);
                lastRefillSlot = hotbarSlot;
                blockRefillCooldownTicks = 10;
                return;
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("方块补栏失败", e);
                return;
            }
        }
    }

    private int findContainerSlotForPlayerInventoryIndex(Container container, EntityPlayerSP player,
            int inventorySlotIndex) {
        if (container == null || player == null) {
            return -1;
        }

        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot == null || slot.inventory != player.inventory) {
                continue;
            }
            if (slot.getSlotIndex() == inventorySlotIndex) {
                return i;
            }
        }
        return -1;
    }

    // ==================== 穿墙方块交互功能 ====================
    private void applyGhostHandBlock(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("ghost_hand_block")
                || ghostHandCooldownTicks > 0
                || mc.currentScreen != null
                || mc.playerController == null) {
            return;
        }

        float maxDistance = getConfiguredValue("ghost_hand_block", 6.0F);
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLookVec();
        Vec3d endPos = eyePos.add(lookVec.scale(maxDistance));

        // 执行扩展距离的射线追踪
        RayTraceResult hit = mc.world.rayTraceBlocks(eyePos, endPos, false, false, true);
        
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && hit.getBlockPos() != null) {
            BlockPos pos = hit.getBlockPos();
            
            // 左键破坏方块
            if (Mouse.isButtonDown(0)) {
                if (mc.playerController.onPlayerDamageBlock(pos, hit.sideHit)) {
                    player.swingArm(EnumHand.MAIN_HAND);
                    ghostHandCooldownTicks = 2;
                    lastGhostInteractPos = pos;
                }
            }
            
            // 右键交互
            if (Mouse.isButtonDown(1)) {
                EnumActionResult result = mc.playerController.processRightClickBlock(
                        player, mc.world, pos, hit.sideHit, hit.hitVec, EnumHand.MAIN_HAND);
                if (result == EnumActionResult.SUCCESS) {
                    player.swingArm(EnumHand.MAIN_HAND);
                    ghostHandCooldownTicks = 2;
                    lastGhostInteractPos = pos;
                }
            }
        }
    }

    // ==================== 自动围身功能 ====================
    private void applySurround(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("surround")
                || surroundCooldownTicks > 0
                || mc.currentScreen != null
                || mc.playerController == null
                || player.capabilities.isCreativeMode) {
            return;
        }

        // 查找黑曜石或其他坚固方块
        int blockSlot = findSurroundBlockInHotbar(player);
        if (blockSlot < 0) {
            return;
        }

        BlockPos playerPos = player.getPosition();
        List<BlockPos> targetPositions = new ArrayList<>();

        // 计算需要放置方块的位置（脚边四周）
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos pos = playerPos.offset(facing);
            if (isReplaceable(mc.world, pos) && canPlaceAgainst(mc.world, pos.down())) {
                targetPositions.add(pos);
            }
        }

        if (targetPositions.isEmpty()) {
            return;
        }

        int originalSlot = player.inventory.currentItem;
        if (ModUtils.switchToHotbarSlot(blockSlot + 1)) {
            for (BlockPos pos : targetPositions) {
                PlacementTarget target = buildPlacementTarget(player, pos);
                if (target != null) {
                    EnumActionResult result = mc.playerController.processRightClickBlock(
                            player, mc.world, target.supportPos, target.supportFace, target.hitVec, EnumHand.MAIN_HAND);
                    if (result == EnumActionResult.SUCCESS) {
                        player.swingArm(EnumHand.MAIN_HAND);
                        surroundPositions.add(pos);
                        surroundCooldownTicks = 5;
                        break; // 一次只放一个方块
                    }
                }
            }
            ModUtils.switchToHotbarSlot(originalSlot + 1);
        }
    }

    private int findSurroundBlockInHotbar(EntityPlayerSP player) {
        // 优先查找黑曜石
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block != null) {
                    String name = block.getUnlocalizedName().toLowerCase();
                    if (name.contains("obsidian")) {
                        return i;
                    }
                }
            }
        }

        // 其次查找其他坚固方块
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block != null) {
                    String name = block.getUnlocalizedName().toLowerCase();
                    if (name.contains("stone") || name.contains("cobblestone") || name.contains("brick")) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }
}
