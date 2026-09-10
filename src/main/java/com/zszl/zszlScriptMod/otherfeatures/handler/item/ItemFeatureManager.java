package com.zszl.zszlScriptMod.otherfeatures.handler.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.FoodStats;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemFeatureManager {

    public static final ItemFeatureManager INSTANCE = new ItemFeatureManager();

    private static final String CONFIG_FILE_NAME = "other_features_item.json";
    private static final Map<String, FeatureState> FEATURES = new LinkedHashMap<>();
    private static final int DEFAULT_CHEST_STEAL_DELAY_TICKS = 2;
    private static final int DEFAULT_AUTO_EQUIP_INTERVAL_TICKS = 4;
    private static final int DEFAULT_DROP_ALL_DELAY_TICKS = 6;
    private static final int DEFAULT_SHULKER_PREVIEW_BG = 0xFF101822;
    private static final int DEFAULT_SHULKER_PREVIEW_HEADER_BG = 0xFF172331;
    private static final int DEFAULT_SHULKER_PREVIEW_BORDER = 0xFF3A556E;
    private static final int DEFAULT_SHULKER_PREVIEW_SLOT_BG = 0xFF1A2532;
    private static final int DEFAULT_SHULKER_PREVIEW_SLOT_BORDER = 0xFF31475D;
    private static final double CRITICAL_PACKET_STEP_Y = 0.0625D;
    private static final double CRITICAL_PACKET_EPSILON_Y = 1.1E-5D;
    private static final float CRITICAL_MIN_ATTACK_STRENGTH = 0.9F;

    private static int chestStealDelayTicks = DEFAULT_CHEST_STEAL_DELAY_TICKS;
    private static int autoEquipIntervalTicks = DEFAULT_AUTO_EQUIP_INTERVAL_TICKS;
    private static int dropAllDelayTicks = DEFAULT_DROP_ALL_DELAY_TICKS;
    private static String dropAllKeywordsText = "";
    private static final List<String> dropAllItemFilterExpressions = new ArrayList<>();

    private int inventorySortCooldownTicks = 0;
    private int chestStealCooldownTicks = 0;
    private int autoEquipCooldownTicks = 0;
    private int dropAllCooldownTicks = 0;
    private int criticalSprintRestoreTicks = 0;
    private boolean forceNoHungerReflectionFailed = false;
    private boolean pendingCriticalSprintRestore = false;

    static {
        register(new FeatureState("inventory_sort", "自动整理",
                "打开背包时自动整理主背包（不动热栏），将装备、工具、食物、方块等按类别排序。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("chest_steal", "箱子窃取",
                "打开箱子后按间隔自动把物品快速搬到背包。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("auto_equip", "自动装备",
                "如果身上没穿或有更差的装备，会自动从背包里穿上更好的装备。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("always_critical", "刀刀暴击",
                "攻击实体前补发短暂伪下落数据，尽量让近战命中按原版暴击条件结算。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("force_no_hunger", "强制不饥饿",
                "持续锁定客户端饥饿值、饱和度和消耗值，尽量让角色保持满饱食状态。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("drop_all", "丢弃所有",
                "按物品过滤表达式自动丢弃指定物品，支持多条表达式。",
                null, 0.0F, 0.0F, 0.0F, true));
        register(new FeatureState("shulker_preview", "潜影盒预览",
                "鼠标悬停在潜影盒物品上时，直接显示其内部内容预览。",
                null, 0.0F, 0.0F, 0.0F, true));
        loadConfig();
    }

    private ItemFeatureManager() {
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
        setEnabled(featureId, !isEnabled(featureId));
    }

    public static void setEnabled(String featureId, boolean enabled) {
        FeatureState state = getFeature(featureId);
        if (state == null) {
            return;
        }
        state.setEnabledInternal(enabled);
        saveConfig();
    }

    public static boolean isFeatureStatusHudEnabled(String featureId) {
        FeatureState state = getFeature(featureId);
        return state != null && state.isStatusHudEnabled();
    }

    public static boolean shouldDisplayFeatureStatusHud(String featureId) {
        return MovementFeatureManager.isMasterStatusHudEnabled() && isFeatureStatusHudEnabled(featureId);
    }

    public static int getChestStealDelayTicks() {
        return chestStealDelayTicks;
    }

    public static void setChestStealDelayTicks(int ticks) {
        chestStealDelayTicks = MathHelper.clamp(ticks, 0, 20);
        saveConfig();
    }

    public static int getAutoEquipIntervalTicks() {
        return autoEquipIntervalTicks;
    }

    public static void setAutoEquipIntervalTicks(int ticks) {
        autoEquipIntervalTicks = MathHelper.clamp(ticks, 1, 40);
        saveConfig();
    }

    public static int getDropAllDelayTicks() {
        return dropAllDelayTicks;
    }

    public static void setDropAllDelayTicks(int ticks) {
        dropAllDelayTicks = MathHelper.clamp(ticks, 0, 20);
        saveConfig();
    }

    public static String getDropAllKeywordsText() {
        return dropAllKeywordsText;
    }

    public static void setDropAllKeywordsText(String text) {
        dropAllKeywordsText = text == null ? "" : text.trim();
        saveConfig();
    }

    public static List<String> getDropAllItemFilterExpressions() {
        List<String> expressions = normalizeDropAllExpressions(dropAllItemFilterExpressions);
        if (!expressions.isEmpty()) {
            return expressions;
        }
        return buildLegacyDropAllKeywordExpressions();
    }

    public static void setDropAllItemFilterExpressions(List<String> expressions) {
        dropAllItemFilterExpressions.clear();
        dropAllItemFilterExpressions.addAll(normalizeDropAllExpressions(expressions));
        dropAllKeywordsText = "";
        saveConfig();
    }

    public static String getDropAllExpressionSummary() {
        List<String> expressions = getDropAllItemFilterExpressions();
        return expressions.isEmpty() ? "" : InventoryItemFilterExpressionEngine.summarizeExpressions(expressions);
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
        String normalizedId = normalizeId(featureId);
        if ("chest_steal".equals(normalizedId)) {
            chestStealDelayTicks = DEFAULT_CHEST_STEAL_DELAY_TICKS;
        } else if ("auto_equip".equals(normalizedId)) {
            autoEquipIntervalTicks = DEFAULT_AUTO_EQUIP_INTERVAL_TICKS;
        } else if ("drop_all".equals(normalizedId)) {
            dropAllDelayTicks = DEFAULT_DROP_ALL_DELAY_TICKS;
            dropAllKeywordsText = "";
            dropAllItemFilterExpressions.clear();
        }
        saveConfig();
    }

    public static void loadConfig() {
        for (FeatureState state : FEATURES.values()) {
            state.resetToDefaultInternal();
        }
        chestStealDelayTicks = DEFAULT_CHEST_STEAL_DELAY_TICKS;
        autoEquipIntervalTicks = DEFAULT_AUTO_EQUIP_INTERVAL_TICKS;
        dropAllDelayTicks = DEFAULT_DROP_ALL_DELAY_TICKS;
        dropAllKeywordsText = "";
        dropAllItemFilterExpressions.clear();

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
            }
            if (root.has("chestStealDelayTicks")) {
                chestStealDelayTicks = MathHelper.clamp(root.get("chestStealDelayTicks").getAsInt(), 0, 20);
            }
            if (root.has("autoEquipIntervalTicks")) {
                autoEquipIntervalTicks = MathHelper.clamp(root.get("autoEquipIntervalTicks").getAsInt(), 1, 40);
            }
            if (root.has("dropAllDelayTicks")) {
                dropAllDelayTicks = MathHelper.clamp(root.get("dropAllDelayTicks").getAsInt(), 0, 20);
            }
            if (root.has("dropAllKeywordsText")) {
                dropAllKeywordsText = safe(root.get("dropAllKeywordsText").getAsString());
            }
            if (root.has("dropAllItemFilterExpressions") && root.get("dropAllItemFilterExpressions").isJsonArray()) {
                dropAllItemFilterExpressions.clear();
                for (JsonElement element : root.getAsJsonArray("dropAllItemFilterExpressions")) {
                    if (element != null && element.isJsonPrimitive()) {
                        String expression = safe(element.getAsString());
                        if (!expression.isEmpty()) {
                            dropAllItemFilterExpressions.add(expression);
                        }
                    }
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载物品功能配置失败", e);
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
                featuresObject.add(state.id, item);
            }
            root.add("features", featuresObject);
            root.addProperty("chestStealDelayTicks", chestStealDelayTicks);
            root.addProperty("autoEquipIntervalTicks", autoEquipIntervalTicks);
            root.addProperty("dropAllDelayTicks", dropAllDelayTicks);
            root.addProperty("dropAllKeywordsText", dropAllKeywordsText);
            JsonArray dropAllExpressionArray = new JsonArray();
            for (String expression : normalizeDropAllExpressions(dropAllItemFilterExpressions)) {
                dropAllExpressionArray.add(expression);
            }
            root.add("dropAllItemFilterExpressions", dropAllExpressionArray);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(root.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存物品功能配置失败", e);
        }
    }

    public void onClientDisconnect() {
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        this.inventorySortCooldownTicks = 0;
        this.chestStealCooldownTicks = 0;
        this.autoEquipCooldownTicks = 0;
        this.dropAllCooldownTicks = 0;
        this.criticalSprintRestoreTicks = 0;
        this.pendingCriticalSprintRestore = false;
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

        tickCooldowns();
        handlePendingCriticalSprintRestore(player);
        handleForceNoHunger(player);
        if (PerformanceMonitor.isFeatureEnabled("auto_equip")) {
            PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("auto_equip");
            try {
                handleAutoEquip(mc, player);
            } finally {
                timer.stop();
            }
        }
        handleChestSteal(mc, player);
        handleInventorySort(mc, player);
        handleDropAll(mc, player);
    }

    private void tickCooldowns() {
        if (this.inventorySortCooldownTicks > 0) {
            this.inventorySortCooldownTicks--;
        }
        if (this.chestStealCooldownTicks > 0) {
            this.chestStealCooldownTicks--;
        }
        if (this.autoEquipCooldownTicks > 0) {
            this.autoEquipCooldownTicks--;
        }
        if (this.dropAllCooldownTicks > 0) {
            this.dropAllCooldownTicks--;
        }
        if (this.criticalSprintRestoreTicks > 0) {
            this.criticalSprintRestoreTicks--;
        }
    }

    private void handleForceNoHunger(EntityPlayerSP player) {
        if (!isEnabled("force_no_hunger") || player == null) {
            return;
        }

        FoodStats foodStats = player.getFoodStats();
        if (foodStats == null) {
            return;
        }

        foodStats.setFoodLevel(20);
        foodStats.setFoodSaturationLevel(20.0F);

        if (this.forceNoHungerReflectionFailed) {
            return;
        }

        try {
            ReflectionCompat.setPrivateValue(FoodStats.class, foodStats, 0.0F,
                    "foodExhaustionLevel", "field_75126_c");
            ReflectionCompat.setPrivateValue(FoodStats.class, foodStats, 0,
                    "foodTimer", "field_75123_d");
        } catch (RuntimeException e) {
            this.forceNoHungerReflectionFailed = true;
            zszlScriptMod.LOGGER.warn("强制不饥饿无法重置 FoodStats 私有字段，将仅锁定可公开修改的饥饿值。", e);
        }
    }

    private void handleAutoEquip(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("auto_equip") || this.autoEquipCooldownTicks > 0 || mc.playerController == null) {
            return;
        }
        if (mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiInventory)) {
            return;
        }
        if (player.ticksExisted % 2 == 0) {
            return;
        }

        int[] bestArmorSlots = new int[4];
        float[] bestArmorScores = new float[4];
        for (int armorType = 0; armorType < 4; armorType++) {
            ItemStack equipped = player.inventory.armorItemInSlot(armorType);
            bestArmorScores[armorType] = getArmorScore(equipped);
            bestArmorSlots[armorType] = -1;
        }

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) {
                continue;
            }
            ItemArmor armor = (ItemArmor) stack.getItem();
            int armorType = getArmorTypeIndex(armor);
            if (armorType < 0) {
                continue;
            }

            float score = getArmorScore(stack);
            if (score > bestArmorScores[armorType] + 0.01F) {
                bestArmorScores[armorType] = score;
                bestArmorSlots[armorType] = slot;
            }
        }

        for (int armorType = 0; armorType < 4; armorType++) {
            int sourceSlot = bestArmorSlots[armorType];
            if (sourceSlot < 0) {
                continue;
            }

            ItemStack equipped = player.inventory.armorItemInSlot(armorType);
            if (!equipped.isEmpty() && player.inventory.getFirstEmptyStack() == -1) {
                continue;
            }

            int sourceContainerSlot = toPlayerContainerSlot(sourceSlot);
            int armorContainerSlot = 8 - armorType;
            mc.playerController.windowClick(0, armorContainerSlot, 0, ClickType.QUICK_MOVE, player);
            mc.playerController.windowClick(0, sourceContainerSlot, 0, ClickType.QUICK_MOVE, player);
            this.autoEquipCooldownTicks = autoEquipIntervalTicks;
            return;
        }
    }

    private void handleChestSteal(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("chest_steal") || this.chestStealCooldownTicks > 0 || mc.playerController == null) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiChest) || !(player.openContainer instanceof ContainerChest)) {
            return;
        }

        ContainerChest chest = (ContainerChest) player.openContainer;
        int size = chest.getLowerChestInventory().getSizeInventory();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = chest.getLowerChestInventory().getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            mc.playerController.windowClick(chest.windowId, slot, 0, ClickType.QUICK_MOVE, player);
            this.chestStealCooldownTicks = chestStealDelayTicks;
            return;
        }
    }

    private void handleInventorySort(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("inventory_sort") || this.inventorySortCooldownTicks > 0 || mc.playerController == null) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiInventory) || player.openContainer != player.inventoryContainer) {
            return;
        }

        List<ItemStack> sorted = new ArrayList<>();
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                sorted.add(stack.copy());
            }
        }
        sorted.sort(INVENTORY_SORT_COMPARATOR);
        while (sorted.size() < 27) {
            sorted.add(ItemStack.EMPTY);
        }

        for (int index = 0; index < 27; index++) {
            int targetInvSlot = 9 + index;
            ItemStack desired = sorted.get(index);
            ItemStack current = player.inventory.getStackInSlot(targetInvSlot);
            if (sameStackIdentity(current, desired)) {
                continue;
            }

            if (desired.isEmpty()) {
                int emptySlot = findEmptyInventorySlot(player, targetInvSlot + 1, 35);
                if (emptySlot >= 0) {
                    moveInventorySlot(player, targetInvSlot, emptySlot);
                    this.inventorySortCooldownTicks = 4;
                }
                return;
            }

            int sourceInvSlot = findMatchingInventorySlot(player, desired, targetInvSlot + 1, 35);
            if (sourceInvSlot >= 0) {
                moveInventorySlot(player, sourceInvSlot, targetInvSlot);
                this.inventorySortCooldownTicks = 4;
            }
            return;
        }

        this.inventorySortCooldownTicks = 10;
    }

    private void handleDropAll(Minecraft mc, EntityPlayerSP player) {
        if (!isEnabled("drop_all") || this.dropAllCooldownTicks > 0 || mc.playerController == null) {
            return;
        }
        if (player.openContainer != player.inventoryContainer) {
            return;
        }

        List<String> expressions = getDropAllItemFilterExpressions();
        if (expressions.isEmpty()) {
            return;
        }

        for (int invSlot = 9; invSlot <= 44; invSlot++) {
            int actualInvSlot = invSlot <= 35 ? invSlot : invSlot - 36;
            ItemStack stack = player.inventory.getStackInSlot(actualInvSlot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!matchesAnyDropAllExpression(stack, actualInvSlot, expressions)) {
                continue;
            }

            mc.playerController.windowClick(0, toPlayerContainerSlot(actualInvSlot), 1, ClickType.THROW, player);
            this.dropAllCooldownTicks = dropAllDelayTicks;
            return;
        }
    }

    @SubscribeEvent
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled("shulker_preview")
                || event == null
                || mc == null
                || !(mc.currentScreen instanceof GuiContainer)
                || !hasShulkerPreviewContents(event.getStack())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!isEnabled("shulker_preview") || !(event.getGui() instanceof GuiContainer)) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.getGui();
        Slot hoveredSlot = gui.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.getHasStack()) {
            return;
        }

        ItemStack stack = hoveredSlot.getStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemShulkerBox)) {
            return;
        }

        NonNullList<ItemStack> items = getShulkerPreviewItems(stack);
        if (items == null) {
            return;
        }
        renderShulkerPreview(gui, stack, items, event.getMouseX(), event.getMouseY());
    }

    private boolean hasShulkerPreviewContents(ItemStack stack) {
        return getShulkerPreviewItems(stack) != null;
    }

    private NonNullList<ItemStack> getShulkerPreviewItems(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemShulkerBox)) {
            return null;
        }
        NBTTagCompound blockEntityTag = stack.getSubCompound("BlockEntityTag");
        if (blockEntityTag == null || !blockEntityTag.hasKey("Items", 9)) {
            return null;
        }

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(blockEntityTag, items);
        return items;
    }

    private void renderShulkerPreview(GuiContainer gui, ItemStack shulkerStack, NonNullList<ItemStack> items, int mouseX,
            int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        int panelWidth = 162;
        int panelHeight = 78;
        int panelX = Math.min(mouseX + 12, gui.width - panelWidth - 6);
        int panelY = Math.min(mouseY + 12, gui.height - panelHeight - 6);

        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DEFAULT_SHULKER_PREVIEW_BG);
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 15, DEFAULT_SHULKER_PREVIEW_HEADER_BG);
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF5FB8FF);
        Gui.drawRect(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight,
                DEFAULT_SHULKER_PREVIEW_BORDER);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + panelHeight, DEFAULT_SHULKER_PREVIEW_BORDER);
        Gui.drawRect(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight,
                DEFAULT_SHULKER_PREVIEW_BORDER);
        mc.fontRenderer.drawStringWithShadow(shulkerStack.getDisplayName(), panelX + 6, panelY + 5, 0xFFFFFFFF);

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        for (int index = 0; index < items.size(); index++) {
            int slotX = panelX + 6 + index % 9 * 17;
            int slotY = panelY + 18 + index / 9 * 17;
            Gui.drawRect(slotX - 1, slotY - 1, slotX + 17, slotY + 17, DEFAULT_SHULKER_PREVIEW_SLOT_BORDER);
            Gui.drawRect(slotX, slotY, slotX + 16, slotY + 16, DEFAULT_SHULKER_PREVIEW_SLOT_BG);
            ItemStack item = items.get(index);
            if (!item.isEmpty()) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(item, slotX, slotY);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, item, slotX, slotY, null);
            }
        }
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
    }

    private void moveInventorySlot(EntityPlayerSP player, int sourceInvSlot, int targetInvSlot) {
        if (sourceInvSlot == targetInvSlot) {
            return;
        }
        int windowId = player.inventoryContainer.windowId;
        int sourceContainerSlot = toPlayerContainerSlot(sourceInvSlot);
        int targetContainerSlot = toPlayerContainerSlot(targetInvSlot);
        boolean targetEmpty = player.inventory.getStackInSlot(targetInvSlot).isEmpty();
        boolean sourceEmpty = player.inventory.getStackInSlot(sourceInvSlot).isEmpty();
        if (sourceEmpty) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.playerController == null) {
            return;
        }

        if (targetEmpty) {
            mc.playerController.windowClick(windowId, sourceContainerSlot, 0, ClickType.PICKUP, player);
            mc.playerController.windowClick(windowId, targetContainerSlot, 0, ClickType.PICKUP, player);
            return;
        }

        mc.playerController.windowClick(windowId, targetContainerSlot, 0, ClickType.PICKUP, player);
        mc.playerController.windowClick(windowId, sourceContainerSlot, 0, ClickType.PICKUP, player);
        mc.playerController.windowClick(windowId, targetContainerSlot, 0, ClickType.PICKUP, player);
    }

    private int findEmptyInventorySlot(EntityPlayerSP player, int start, int end) {
        for (int slot = Math.max(9, start); slot <= Math.min(35, end); slot++) {
            if (player.inventory.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int findMatchingInventorySlot(EntityPlayerSP player, ItemStack target, int start, int end) {
        for (int slot = Math.max(9, start); slot <= Math.min(35, end); slot++) {
            if (sameStackIdentity(player.inventory.getStackInSlot(slot), target)) {
                return slot;
            }
        }
        return -1;
    }

    private static int toPlayerContainerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private static int getArmorTypeIndex(ItemArmor armor) {
        if (armor == null || armor.armorType == null) {
            return -1;
        }
        switch (armor.armorType) {
        case FEET:
            return 0;
        case LEGS:
            return 1;
        case CHEST:
            return 2;
        case HEAD:
            return 3;
        default:
            return -1;
        }
    }

    private static float getArmorScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) {
            return 0.0F;
        }
        ItemArmor armor = (ItemArmor) stack.getItem();
        float score = armor.damageReduceAmount * 10.0F;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, stack) * 3.0F;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantments.BLAST_PROTECTION, stack) * 1.5F;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantments.FIRE_PROTECTION, stack) * 1.2F;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantments.PROJECTILE_PROTECTION, stack) * 1.2F;
        return score;
    }

    public static void prepareCriticalAttack(EntityPlayer player, Entity target) {
        if (!(player instanceof EntityPlayerSP)) {
            return;
        }
        INSTANCE.prepareCriticalAttackInternal((EntityPlayerSP) player, target);
    }

    private void prepareCriticalAttackInternal(EntityPlayerSP player, Entity target) {
        if (!canInjectCriticalPackets(player, target)) {
            return;
        }

        boolean restoreSprint = player.isSprinting();
        if (restoreSprint) {
            player.connection.sendPacket(
                    new CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SPRINTING));
            player.setSprinting(false);
            this.pendingCriticalSprintRestore = true;
            this.criticalSprintRestoreTicks = 1;
        }

        sendCriticalPackets(player);
    }

    private boolean canInjectCriticalPackets(EntityPlayerSP player, Entity target) {
        if (!isEnabled("always_critical")
                || player == null
                || player.connection == null
                || !(target instanceof EntityLivingBase)
                || !((EntityLivingBase) target).isEntityAlive()) {
            return false;
        }
        if (!player.onGround
                || player.capabilities == null
                || player.capabilities.isFlying
                || player.isElytraFlying()
                || player.isRiding()
                || player.isOnLadder()
                || player.isInWater()
                || player.isInLava()
                || player.isPotionActive(MobEffects.BLINDNESS)) {
            return false;
        }
        return player.getCooledAttackStrength(0.5F) > CRITICAL_MIN_ATTACK_STRENGTH;
    }

    private void sendCriticalPackets(EntityPlayerSP player) {
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        player.connection.sendPacket(new CPacketPlayer.Position(x, y + CRITICAL_PACKET_STEP_Y, z, false));
        player.connection.sendPacket(new CPacketPlayer.Position(x, y, z, false));
        player.connection.sendPacket(new CPacketPlayer.Position(x, y + CRITICAL_PACKET_EPSILON_Y, z, false));
        player.connection.sendPacket(new CPacketPlayer.Position(x, y, z, false));
    }

    private void handlePendingCriticalSprintRestore(EntityPlayerSP player) {
        if (!this.pendingCriticalSprintRestore || this.criticalSprintRestoreTicks > 0
                || player == null || player.connection == null) {
            return;
        }
        player.connection.sendPacket(new CPacketEntityAction(player, CPacketEntityAction.Action.START_SPRINTING));
        player.setSprinting(true);
        this.pendingCriticalSprintRestore = false;
    }

    private static boolean sameStackIdentity(ItemStack a, ItemStack b) {
        if (a == null || a.isEmpty()) {
            return b == null || b.isEmpty();
        }
        if (b == null || b.isEmpty()) {
            return false;
        }
        return ItemStack.areItemsEqual(a, b)
                && ItemStack.areItemStackTagsEqual(a, b)
                && a.getCount() == b.getCount();
    }

    private List<String> getDropAllKeywords() {
        List<String> keywords = new ArrayList<>();
        if (dropAllKeywordsText == null || dropAllKeywordsText.trim().isEmpty()) {
            return keywords;
        }
        String normalized = dropAllKeywordsText.replace('，', ',').replace('\n', ',').replace(';', ',');
        for (String part : normalized.split(",")) {
            String keyword = safe(part);
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private static List<String> normalizeDropAllExpressions(List<String> expressions) {
        List<String> normalized = new ArrayList<>();
        if (expressions == null) {
            return normalized;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String expression : expressions) {
            String text = safe(expression);
            if (text.isEmpty()) {
                continue;
            }
            if (seen.add(text.toLowerCase(Locale.ROOT))) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private static List<String> buildLegacyDropAllKeywordExpressions() {
        List<String> expressions = new ArrayList<>();
        for (String keyword : INSTANCE.getDropAllKeywords()) {
            String expression = InventoryItemFilterExpressionEngine.buildLegacyCompatibleExpression(keyword,
                    "CONTAINS", Collections.emptyList(), "");
            if (!expression.isEmpty()) {
                expressions.add(expression);
            }
        }
        return expressions;
    }

    private static boolean matchesAnyDropAllExpression(ItemStack stack, int actualInvSlot, List<String> expressions) {
        for (String expression : expressions) {
            try {
                if (InventoryItemFilterExpressionEngine.matches(stack, actualInvSlot, expression)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
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

        lines.add("§a[物品] §f" + activeNames.size() + " 项开启");
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

    private String getRuntimeHudLine() {
        List<String> parts = new ArrayList<>();
        if (isEnabled("chest_steal") && shouldDisplayFeatureStatusHud("chest_steal")) {
            parts.add("§e箱窃:" + chestStealDelayTicks + "t");
        }
        if (isEnabled("drop_all") && shouldDisplayFeatureStatusHud("drop_all")) {
            parts.add("§c丢弃式:" + getDropAllItemFilterExpressions().size());
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

        FeatureState state = getFeature(featureId);
        if (state == null) {
            return "未找到功能";
        }

        switch (featureId) {
        case "auto_equip":
            return isEnabled(featureId) ? "自动装备扫描间隔: " + autoEquipIntervalTicks + " tick" : "未启用";
        case "always_critical":
            return isEnabled(featureId) ? "攻击前补发伪下落数据，尽量触发近战原版暴击" : "未启用";
        case "force_no_hunger":
            return isEnabled(featureId) ? "持续锁定客户端饥饿值与饱和度" : "未启用";
        case "inventory_sort":
            return isEnabled(featureId) ? "打开背包界面时自动整理主背包" : "未启用";
        case "chest_steal":
            return isEnabled(featureId) ? "箱子搬运间隔: " + chestStealDelayTicks + " tick" : "未启用";
        case "drop_all":
            if (!isEnabled(featureId)) {
                return "未启用";
            }
            List<String> expressions = getDropAllItemFilterExpressions();
            return expressions.isEmpty()
                    ? "已启用，但尚未配置丢弃表达式"
                    : "丢弃表达式: " + InventoryItemFilterExpressionEngine.summarizeExpressions(expressions);
        case "shulker_preview":
            return isEnabled(featureId) ? "悬停潜影盒时显示内容预览" : "未启用";
        default:
            return state.behaviorImplemented ? "基础逻辑已接入" : "仅配置占位";
        }
    }

    private static final Comparator<ItemStack> INVENTORY_SORT_COMPARATOR = Comparator
            .comparingInt(ItemFeatureManager::getSortCategory)
            .thenComparing(stack -> safe(stack.getDisplayName()).toLowerCase(Locale.ROOT))
            .thenComparing(stack -> {
                Item item = stack.getItem();
                return item == null || Item.REGISTRY.getNameForObject(item) == null
                        ? ""
                        : Item.REGISTRY.getNameForObject(item).toString();
            })
            .thenComparingInt(ItemStack::getMetadata);

    private static int getSortCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return 99;
        }
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            return 0;
        }
        if (item instanceof ItemSword || item instanceof ItemBow || item instanceof ItemTool) {
            return 1;
        }
        if (item instanceof ItemFood) {
            return 2;
        }
        if (item instanceof ItemBlock) {
            return item instanceof ItemShulkerBox ? 4 : 3;
        }
        return 5;
    }
}
