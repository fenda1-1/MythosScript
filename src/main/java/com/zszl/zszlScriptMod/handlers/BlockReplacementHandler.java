package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.system.BlockReplacementRule;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockReplacementHandler {
    public static final BlockReplacementHandler INSTANCE = new BlockReplacementHandler();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long CACHE_REFRESH_MS = 1000L;
    private static final double MAX_RENDER_DISTANCE_SQ = 96.0D * 96.0D;

    public static final List<BlockReplacementRule> rules = new CopyOnWriteArrayList<>();
    private static final List<String> categories = new CopyOnWriteArrayList<>();
    private static final String CATEGORY_DEFAULT = "默认";

    private enum SelectionMode {
        NONE,
        REGION,
        SOURCE_BLOCK,
        TARGET_BLOCK
    }

    private final Map<BlockReplacementRule, RegionCache> regionCacheMap = new HashMap<>();
    private static volatile SelectionMode selectionMode = SelectionMode.NONE;
    private static volatile BlockReplacementRule selectionRule = null;
    private static volatile BlockReplacementRule.BlockReplacementEntry selectionEntry = null;
    private static volatile GuiScreen selectionReturnScreen = null;
    private static volatile boolean regionCorner1SelectedThisSession = false;
    private static volatile boolean regionCorner2SelectedThisSession = false;

    public static class BlockCountEntry {
        public final String blockId;
        public final int count;

        public BlockCountEntry(String blockId, int count) {
            this.blockId = blockId;
            this.count = count;
        }
    }

    private static class RegionCache {
        long lastRefreshAt;
        int dimension;
        List<BlockCountEntry> blockCounts = new ArrayList<>();
        Map<String, List<BlockPos>> positionsBySource = new HashMap<>();
    }

    private BlockReplacementHandler() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("block_replacement_rules.json");
    }

    public static synchronized void loadConfig() {
        rules.clear();
        categories.clear();
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) {
            ensureCategoriesSynced();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.has("categories") && root.get("categories").isJsonArray()) {
                for (com.google.gson.JsonElement element : root.getAsJsonArray("categories")) {
                    if (element != null && element.isJsonPrimitive()) {
                        categories.add(element.getAsString());
                    }
                }
            }
            if (root.has("rules")) {
                Type listType = new TypeToken<ArrayList<BlockReplacementRule>>() {
                }.getType();
                List<BlockReplacementRule> loaded = GSON.fromJson(root.get("rules"), listType);
                if (loaded != null) {
                    for (BlockReplacementRule rule : loaded) {
                        if (rule == null) {
                            continue;
                        }
                        if (rule.replacements == null) {
                            rule.replacements = new ArrayList<>();
                        }
                        rule.category = normalizeCategory(rule.category);
                        rule.dirty = true;
                        rules.add(rule);
                    }
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法加载区域方块替换规则", e);
            rules.clear();
        }
        ensureCategoriesSynced();
    }

    public static synchronized void saveConfig() {
        try {
            ensureCategoriesSynced();
            Path configFile = getConfigFile();
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.add("categories", GSON.toJsonTree(new ArrayList<>(categories)));
                root.add("rules", GSON.toJsonTree(rules));
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存区域方块替换规则", e);
        }
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        for (BlockReplacementRule rule : rules) {
            if (rule == null) {
                continue;
            }
            rule.category = normalizeCategory(rule.category);
            normalized.add(rule.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        categories.clear();
        categories.addAll(normalized);
    }

    private static boolean containsCategoryIgnoreCase(String category) {
        for (String existing : categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalizeCategory(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeCategoryIgnoreCase(String category) {
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizeCategory(category))) {
                categories.remove(i);
                return true;
            }
        }
        return false;
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureCategoriesSynced();
        return new ArrayList<>(categories);
    }

    public static synchronized void replaceCategoryOrder(List<String> orderedCategories) {
        ensureCategoriesSynced();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (orderedCategories != null) {
            for (String category : orderedCategories) {
                normalized.add(normalizeCategory(category));
            }
        }
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        categories.clear();
        categories.addAll(normalized);
        saveConfig();
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        categories.add(normalized);
        saveConfig();
        return true;
    }

    public static synchronized boolean renameCategory(String oldCategory, String newCategory) {
        String normalizedOld = normalizeCategory(oldCategory);
        String normalizedNew = normalizeCategory(newCategory);
        ensureCategoriesSynced();

        if (normalizedOld.equalsIgnoreCase(normalizedNew)) {
            return true;
        }
        if (containsCategoryIgnoreCase(normalizedNew)) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizedOld)) {
                categories.set(i, normalizedNew);
                changed = true;
                break;
            }
        }

        for (BlockReplacementRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalizedOld)) {
                rule.category = normalizedNew;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveConfig();
        return true;
    }

    public static synchronized boolean deleteCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();

        boolean changed = removeCategoryIgnoreCase(normalized);
        for (BlockReplacementRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                rule.category = CATEGORY_DEFAULT;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveConfig();
        return true;
    }

    public static List<BlockCountEntry> getAvailableBlocks(BlockReplacementRule rule) {
        return INSTANCE.getOrBuildCache(rule).blockCounts;
    }

    public static void startRegionSelection(BlockReplacementRule rule, GuiScreen returnScreen) {
        selectionMode = SelectionMode.REGION;
        selectionRule = rule;
        selectionEntry = null;
        selectionReturnScreen = returnScreen;
        regionCorner1SelectedThisSession = false;
        regionCorner2SelectedThisSession = false;
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "§b[区域方块替换] §f可视化选择已开启：§a左键选择角1§f，§e右键选择角2§f。"));
        }
        mc.displayGuiScreen(null);
    }

    public static void startSourceBlockSelection(BlockReplacementRule.BlockReplacementEntry entry,
            GuiScreen returnScreen) {
        selectionMode = SelectionMode.SOURCE_BLOCK;
        selectionRule = null;
        selectionEntry = entry;
        selectionReturnScreen = returnScreen;
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "§b[区域方块替换] §f请左键或右键点击一个方块，作为§a被替换方块§f。"));
        }
        mc.displayGuiScreen(null);
    }

    public static void startTargetBlockSelection(BlockReplacementRule.BlockReplacementEntry entry,
            GuiScreen returnScreen) {
        selectionMode = SelectionMode.TARGET_BLOCK;
        selectionRule = null;
        selectionEntry = entry;
        selectionReturnScreen = returnScreen;
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "§b[区域方块替换] §f请左键或右键点击一个方块，作为§a替换后显示方块§f。"));
        }
        mc.displayGuiScreen(null);
    }

    public static void markRuleDirty(BlockReplacementRule rule) {
        if (rule != null) {
            rule.dirty = true;
        }
    }

    private static void finishSelectionIfPossible() {
        if (selectionMode == SelectionMode.REGION) {
            if (selectionRule != null && selectionRule.hasValidRegion()
                    && (regionCorner1SelectedThisSession || selectionRule.hasCorner1())
                    && (regionCorner2SelectedThisSession || selectionRule.hasCorner2())) {
                GuiScreen returnScreen = selectionReturnScreen;
                selectionMode = SelectionMode.NONE;
                selectionRule = null;
                selectionEntry = null;
                selectionReturnScreen = null;
                if (returnScreen != null) {
                    mc.addScheduledTask(() -> mc.displayGuiScreen(returnScreen));
                }
            }
            return;
        }

        GuiScreen returnScreen = selectionReturnScreen;
        selectionMode = SelectionMode.NONE;
        selectionRule = null;
        selectionEntry = null;
        selectionReturnScreen = null;
        if (returnScreen != null) {
            mc.addScheduledTask(() -> mc.displayGuiScreen(returnScreen));
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getWorld().isRemote || selectionMode == SelectionMode.NONE) {
            return;
        }
        handleSelectionClick(event.getPos(), true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote || selectionMode == SelectionMode.NONE
                || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        handleSelectionClick(event.getPos(), false);
        event.setCanceled(true);
    }

    private void handleSelectionClick(BlockPos pos, boolean leftClick) {
        if (mc.world == null || pos == null) {
            return;
        }
        switch (selectionMode) {
            case REGION:
                if (selectionRule == null) {
                    return;
                }
                if (leftClick) {
                    selectionRule.setCorner1(pos.getX(), pos.getY(), pos.getZ());
                    regionCorner1SelectedThisSession = true;
                    if (mc.player != null) {
                        mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "§a[区域方块替换] 已设置角1: " + pos.toString()));
                    }
                } else {
                    selectionRule.setCorner2(pos.getX(), pos.getY(), pos.getZ());
                    regionCorner2SelectedThisSession = true;
                    if (mc.player != null) {
                        mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "§e[区域方块替换] 已设置角2: " + pos.toString()));
                    }
                }
                finishSelectionIfPossible();
                break;
            case SOURCE_BLOCK:
            case TARGET_BLOCK:
                if (selectionEntry == null) {
                    return;
                }
                IBlockState state = mc.world.getBlockState(pos);
                ResourceLocation name = Block.REGISTRY.getNameForObject(state.getBlock());
                String blockId = name == null ? "minecraft:air" : name.toString();
                if (selectionMode == SelectionMode.SOURCE_BLOCK) {
                    selectionEntry.sourceBlockId = blockId;
                } else {
                    selectionEntry.targetBlockId = blockId;
                }
                if (mc.player != null) {
                    mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "§b[区域方块替换] 已选择方块: §a" + blockId));
                }
                finishSelectionIfPossible();
                break;
            default:
                break;
        }
    }

    private synchronized RegionCache getOrBuildCache(BlockReplacementRule rule) {
        RegionCache cache = regionCacheMap.get(rule);
        int currentDimension = mc.world == null ? 0 : mc.world.provider.getDimension();
        long now = System.currentTimeMillis();
        if (rule == null || !rule.hasValidRegion() || mc.world == null) {
            return new RegionCache();
        }
        if (cache == null || rule.dirty || cache.dimension != currentDimension
                || now - cache.lastRefreshAt > CACHE_REFRESH_MS) {
            cache = rebuildCache(rule, currentDimension, now);
            regionCacheMap.put(rule, cache);
            rule.dirty = false;
        }
        return cache;
    }

    private RegionCache rebuildCache(BlockReplacementRule rule, int currentDimension, long now) {
        RegionCache cache = new RegionCache();
        cache.dimension = currentDimension;
        cache.lastRefreshAt = now;

        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<BlockPos>> positions = new HashMap<>();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = rule.getMinX(); x <= rule.getMaxX(); x++) {
            for (int y = rule.getMinY(); y <= rule.getMaxY(); y++) {
                for (int z = rule.getMinZ(); z <= rule.getMaxZ(); z++) {
                    mutable.setPos(x, y, z);
                    IBlockState state = mc.world.getBlockState(mutable);
                    Block block = state.getBlock();
                    ResourceLocation name = Block.REGISTRY.getNameForObject(block);
                    String blockId = name == null ? "minecraft:air" : name.toString();
                    counts.put(blockId, counts.getOrDefault(blockId, 0) + 1);
                    positions.computeIfAbsent(blockId, key -> new ArrayList<>()).add(mutable.toImmutable());
                }
            }
        }

        List<BlockCountEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(new BlockCountEntry(entry.getKey(), entry.getValue()));
        }
        entries.sort(Comparator.comparing((BlockCountEntry e) -> e.blockId));
        cache.blockCounts = entries;
        cache.positionsBySource = positions;
        return cache;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("block_replacement")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("block_replacement");
        try {
        if (mc.player == null || mc.world == null) {
            return;
        }

        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        if (dispatcher == null) {
            return;
        }

        double viewerX = mc.getRenderViewEntity().lastTickPosX
                + (mc.getRenderViewEntity().posX - mc.getRenderViewEntity().lastTickPosX) * event.getPartialTicks();
        double viewerY = mc.getRenderViewEntity().lastTickPosY
                + (mc.getRenderViewEntity().posY - mc.getRenderViewEntity().lastTickPosY) * event.getPartialTicks();
        double viewerZ = mc.getRenderViewEntity().lastTickPosZ
                + (mc.getRenderViewEntity().posZ - mc.getRenderViewEntity().lastTickPosZ) * event.getPartialTicks();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        boolean began = false;
        List<AxisAlignedBB> highlightBoxes = new ArrayList<>();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableLighting();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        for (BlockReplacementRule rule : rules) {
            if (rule == null || !rule.enabled || !rule.hasValidRegion() || rule.replacements == null
                    || rule.replacements.isEmpty()) {
                continue;
            }

            RegionCache cache = getOrBuildCache(rule);
            for (BlockReplacementRule.BlockReplacementEntry entry : rule.replacements) {
                if (entry == null || !entry.enabled || entry.sourceBlockId == null || entry.targetBlockId == null) {
                    continue;
                }
                List<BlockPos> positions = cache.positionsBySource.get(entry.sourceBlockId);
                if (positions == null || positions.isEmpty()) {
                    continue;
                }

                Block targetBlock = Block.getBlockFromName(entry.targetBlockId);
                if (targetBlock == null) {
                    continue;
                }
                IBlockState targetState = targetBlock.getDefaultState();

                if (!began) {
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                    buffer.setTranslation(-viewerX, -viewerY, -viewerZ);
                    began = true;
                }

                for (BlockPos pos : positions) {
                    if (mc.player.getDistanceSqToCenter(pos) > MAX_RENDER_DISTANCE_SQ) {
                        continue;
                    }
                    dispatcher.renderBlock(targetState, pos, mc.world, buffer);
                    if (rule.highlightReplacedBlocks) {
                        AxisAlignedBB box = mc.world.getBlockState(pos).getSelectedBoundingBox(mc.world, pos)
                                .offset(-viewerX, -viewerY, -viewerZ)
                                .grow(0.002D);
                        highlightBoxes.add(box);
                    }
                }
            }
        }

        if (began) {
            tessellator.draw();
            buffer.setTranslation(0, 0, 0);
        }

        if (!highlightBoxes.isEmpty()) {
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.glLineWidth(2.0F);
            for (AxisAlignedBB box : highlightBoxes) {
                RenderGlobal.drawSelectionBoundingBox(box, 0.2F, 1.0F, 0.3F, 0.7F);
            }
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
        }

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        if (mc.world == null || event.getWorld() == null
                || event.getWorld().provider.getDimension() != mc.world.provider.getDimension()) {
            return;
        }

        EntityPlayer entity = mc.player;
        if (entity == null) {
            return;
        }

        if (event.getEntity() != null && event.getEntity() != entity) {
            return;
        }

        AxisAlignedBB queryBox = event.getAabb();
        if (queryBox == null) {
            return;
        }

        for (BlockReplacementRule rule : rules) {
            if (rule == null || !rule.enabled || !rule.useSolidCollision || !rule.hasValidRegion()
                    || rule.replacements == null || rule.replacements.isEmpty()) {
                continue;
            }

            RegionCache cache = getOrBuildCache(rule);
            for (BlockReplacementRule.BlockReplacementEntry entry : rule.replacements) {
                if (entry == null || !entry.enabled || entry.sourceBlockId == null || entry.targetBlockId == null) {
                    continue;
                }

                Block targetBlock = Block.getBlockFromName(entry.targetBlockId);
                if (targetBlock == null) {
                    continue;
                }
                IBlockState targetState = targetBlock.getDefaultState();
                List<BlockPos> positions = cache.positionsBySource.get(entry.sourceBlockId);
                if (positions == null || positions.isEmpty()) {
                    continue;
                }

                for (BlockPos pos : positions) {
                    targetState.addCollisionBoxToList(mc.world, pos, queryBox, event.getCollisionBoxesList(),
                            event.getEntity(), false);
                }
            }
        }
    }
}

