// 文件: src/main/java/com/keycommand2/zszlScriptMod/handlers/ArenaItemHandler.java
// (这是集成了新功能并修复了编译错误的最终完整版本)

package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaItemHandler {
    // 配置文件
    public static final File ARENA_CONFIG_FILE = new File(ModConfig.CONFIG_DIR, "arena_config.json");

    // 防无限循环保护机制
    private static final Map<String, Integer> dropAttempts = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastDropTime = new ConcurrentHashMap<>();
    private static final Map<String, Long> itemCooldowns = new ConcurrentHashMap<>();
    private static final int MAX_DROP_ATTEMPTS_PER_ITEM = 2;
    private static final long DROP_COOLDOWN_MS = 10000;

    // 竞技场范围
    private static final double ARENA_MIN_X = -1894.0;
    private static final double ARENA_MAX_X = -1253.0;
    private static final double ARENA_MIN_Z = -2743.0;
    private static final double ARENA_MAX_Z = -2008.0;
    private static int outsideArenaMessageCooldown = 0;

    private static final String CLICK_PACKET_TEMPLATE = "00 04 56 69 65 77 00 00 {id} 00 00 00 01 00 0C 4F 77 6C 56 69 65 77 5F 73 6C 6F 74 00 00 00 12 4F 77 6C 56 69 65 77 5F 73 6C 6F 74 5F 63 6C 69 63 6B %s 00 00 00 00";
    private static final String PACKET_CHANNEL = "OwlViewChannel";

    // 总开关和调试模式
    public static boolean arenaProcessingEnabled = false;
    public static boolean debugModeEnabled = false;

    private static boolean dropProtectionWarningShown = false;

    // 只拿模式快照
    private static final Set<String> initialInventoryItems = new HashSet<>();
    private static boolean inventorySnapshotTaken = false;

    // ======================= 拿取设置 (由 GuiPickupSettings 控制) =======================
    public static int pickupInitialDelay = 10;
    public static int pickupItemsPerBatch = 1;
    public static int pickupOperationInterval = 2;

    // ======================= 丢弃设置 (由 GuiDropSettings 控制) =======================
    public static int drop_itemsPerOperation = 4;
    public static int drop_operationInterval = 120;
    public static int drop_maxItemsPerTick = 4;
    public static long drop_itemCooldownMs = 100;
    public static boolean drop_enableBatchMode = true;

    // 物品处理模式枚举
    public enum ItemMode {
        BLACKLIST_WHITELIST("黑白名单模式"),
        PICK_ONLY("只拿模式");

        private final String displayName;

        ItemMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 丢弃模式枚举
    public enum DropMode {
        ON_CHEST_OPEN("开箱时丢弃"),
        TIMED("定时丢弃");

        private final String displayName;

        DropMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // [新增] 检测范围枚举
    public enum DetectionRange {
        INVENTORY_ONLY("仅背包"),
        INVENTORY_AND_CHEST("背包+箱子");

        private final String displayName;

        DetectionRange(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 丢弃模式设置
    public static DropMode dropMode = DropMode.ON_CHEST_OPEN;
    public static int timedDropIntervalSeconds = 2;
    // [新增] 检测范围设置
    public static DetectionRange detectionRange = DetectionRange.INVENTORY_AND_CHEST;

    // 当前设置
    public static ItemMode currentMode = ItemMode.PICK_ONLY;
    public static List<String> blacklistFilters = new ArrayList<>();
    public static List<String> whitelistFilters = new ArrayList<>();

    // 预设物品
    public static final List<String> PRESET_ITEMS = Arrays.asList("布料", "线团", "干柴堆", "粗长麻线", "铜粉", "高级布料", "星石",
            "无尽钻块");
    public static Set<String> selectedPresetItems = new HashSet<>();
    public static List<String> customPickItems = new ArrayList<>();

    // 满背包丢弃设置
    public static boolean fullBagDropEnabled = false;
    public static List<String> dropPriority = new ArrayList<>();

    // 物品置顶设置
    public static boolean itemTopEnabled = false;
    public static String itemToTop = "无尽钻块";

    static {
        selectedPresetItems.addAll(Arrays.asList("高级布料", "无尽钻块", "星石"));
        dropPriority = new ArrayList<>(Arrays.asList("布料", "线团", "干柴堆", "铜粉", "粗长麻线", "高级布料", "星石", "无尽钻块"));
        loadArenaConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("arena_config.json").toFile();
    }

    public static void saveArenaConfig() {
        try {
            File configFile = getConfigFile();
            JsonObject config = new JsonObject();
            config.addProperty("arenaProcessingEnabled", arenaProcessingEnabled);
            config.addProperty("debugModeEnabled", debugModeEnabled);

            config.addProperty("currentMode", currentMode.name());
            config.add("blacklistFilters", new Gson().toJsonTree(blacklistFilters));
            config.add("whitelistFilters", new Gson().toJsonTree(whitelistFilters));
            config.add("selectedPresetItems", new Gson().toJsonTree(selectedPresetItems));
            config.add("customPickItems", new Gson().toJsonTree(customPickItems));
            config.addProperty("fullBagDropEnabled", fullBagDropEnabled);
            config.add("dropPriority", new Gson().toJsonTree(dropPriority));
            config.addProperty("itemTopEnabled", itemTopEnabled);
            config.addProperty("itemToTop", itemToTop);

            JsonObject pickupSettings = new JsonObject();
            pickupSettings.addProperty("initialDelay", pickupInitialDelay);
            pickupSettings.addProperty("itemsPerBatch", pickupItemsPerBatch);
            pickupSettings.addProperty("operationInterval", pickupOperationInterval);
            config.add("pickupSettings", pickupSettings);

            JsonObject dropSettings = new JsonObject();
            dropSettings.addProperty("itemsPerOperation", drop_itemsPerOperation);
            dropSettings.addProperty("operationInterval", drop_operationInterval);
            dropSettings.addProperty("maxItemsPerTick", drop_maxItemsPerTick);
            dropSettings.addProperty("itemCooldownMs", drop_itemCooldownMs);
            dropSettings.addProperty("enableBatchMode", drop_enableBatchMode);
            dropSettings.addProperty("dropMode", dropMode.name());
            dropSettings.addProperty("timedDropIntervalSeconds", timedDropIntervalSeconds);
            dropSettings.addProperty("detectionRange", detectionRange.name());
            config.add("dropSettings", dropSettings);

            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            Files.write(configFile.toPath(), config.toString().getBytes(StandardCharsets.UTF_8));
            zszlScriptMod.LOGGER.info("竞技场配置已保存到: {}", ProfileManager.getActiveProfileName());
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存竞技场配置失败", e);
        }
    }

    public static void loadArenaConfig() {
        try {
            File configFile = getConfigFile();
            if (configFile.exists()) {
                String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                JsonObject config = new JsonParser().parse(content).getAsJsonObject();

                if (config.has("arenaProcessingEnabled"))
                    arenaProcessingEnabled = config.get("arenaProcessingEnabled").getAsBoolean();
                if (config.has("debugModeEnabled"))
                    debugModeEnabled = config.get("debugModeEnabled").getAsBoolean();

                if (config.has("currentMode"))
                    currentMode = ItemMode.valueOf(config.get("currentMode").getAsString());
                if (config.has("blacklistFilters"))
                    blacklistFilters = new Gson().fromJson(config.get("blacklistFilters"),
                            new TypeToken<List<String>>() {
                            }.getType());
                if (config.has("whitelistFilters"))
                    whitelistFilters = new Gson().fromJson(config.get("whitelistFilters"),
                            new TypeToken<List<String>>() {
                            }.getType());
                if (config.has("selectedPresetItems"))
                    selectedPresetItems = new Gson().fromJson(config.get("selectedPresetItems"),
                            new TypeToken<Set<String>>() {
                            }.getType());
                if (config.has("customPickItems"))
                    customPickItems = new Gson().fromJson(config.get("customPickItems"), new TypeToken<List<String>>() {
                    }.getType());
                if (config.has("fullBagDropEnabled"))
                    fullBagDropEnabled = config.get("fullBagDropEnabled").getAsBoolean();
                if (config.has("dropPriority"))
                    dropPriority = new Gson().fromJson(config.get("dropPriority"), new TypeToken<List<String>>() {
                    }.getType());
                if (config.has("itemTopEnabled"))
                    itemTopEnabled = config.get("itemTopEnabled").getAsBoolean();
                if (config.has("itemToTop"))
                    itemToTop = config.get("itemToTop").getAsString();

                if (config.has("pickupSettings")) {
                    JsonObject pickupSettings = config.getAsJsonObject("pickupSettings");
                    if (pickupSettings.has("initialDelay"))
                        pickupInitialDelay = pickupSettings.get("initialDelay").getAsInt();
                    if (pickupSettings.has("itemsPerBatch"))
                        pickupItemsPerBatch = pickupSettings.get("itemsPerBatch").getAsInt();
                    if (pickupSettings.has("operationInterval"))
                        pickupOperationInterval = pickupSettings.get("operationInterval").getAsInt();
                }

                if (config.has("dropSettings")) {
                    JsonObject dropSettings = config.getAsJsonObject("dropSettings");
                    if (dropSettings.has("itemsPerOperation"))
                        drop_itemsPerOperation = dropSettings.get("itemsPerOperation").getAsInt();
                    if (dropSettings.has("operationInterval"))
                        drop_operationInterval = dropSettings.get("operationInterval").getAsInt();
                    if (dropSettings.has("maxItemsPerTick"))
                        drop_maxItemsPerTick = dropSettings.get("maxItemsPerTick").getAsInt();
                    if (dropSettings.has("itemCooldownMs"))
                        drop_itemCooldownMs = dropSettings.get("itemCooldownMs").getAsLong();
                    if (dropSettings.has("enableBatchMode"))
                        drop_enableBatchMode = dropSettings.get("enableBatchMode").getAsBoolean();
                    if (dropSettings.has("dropMode"))
                        dropMode = DropMode.valueOf(dropSettings.get("dropMode").getAsString());
                    if (dropSettings.has("timedDropIntervalSeconds"))
                        timedDropIntervalSeconds = dropSettings.get("timedDropIntervalSeconds").getAsInt();
                    if (dropSettings.has("detectionRange"))
                        detectionRange = DetectionRange.valueOf(dropSettings.get("detectionRange").getAsString());
                }

                zszlScriptMod.LOGGER.info("从配置 '{}' 加载竞技场设置完成", ProfileManager.getActiveProfileName());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载竞技场配置失败", e);
        }
    }

    public static boolean isPlayerInArena() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null)
            return false;
        return player.posX >= ARENA_MIN_X && player.posX <= ARENA_MAX_X &&
                player.posZ >= ARENA_MIN_Z && player.posZ <= ARENA_MAX_Z;
    }

    /**
     * !! 核心修复与功能增强：初始化“只拿模式”的物品保护快照 !!
     * - 当“只拿模式”首次启动时，此方法会自动扫描玩家背包，将非预设物品加入自定义列表以进行保护。
     * - 修复了会将预设物品错误添加到自定义列表的问题。
     * - 提供了更清晰的用户反馈。
     */
    public static void initializePickOnlyMode() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }

        // 用于存储需要新加入保护的物品
        Set<String> newItemsToProtect = new HashSet<>();
        // 用于记录在背包中发现的、但属于预设物品的物品
        Set<String> presetItemsFound = new HashSet<>();

        // 遍历整个背包，包括快捷栏 (0-35)，以保护所有带入的物品
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getDisplayName().trim();
                if (itemName.isEmpty()) {
                    continue;
                }

                // 检查1: 是否是预设物品
                if (PRESET_ITEMS.contains(itemName)) {
                    presetItemsFound.add(itemName);
                    continue; // 是预设物品，跳过，不添加到自定义列表
                }

                // 检查2: 是否已存在于自定义列表中
                if (!customPickItems.contains(itemName)) {
                    newItemsToProtect.add(itemName);
                }
            }
        }

        // 执行添加操作
        if (!newItemsToProtect.isEmpty()) {
            customPickItems.addAll(newItemsToProtect);
            // 注意：这里我们不需要保存配置，因为这只是临时的运行时保护
        }

        // 标记快照已完成
        inventorySnapshotTaken = true;

        // !! 功能增强：提供更详细的反馈信息 !!
        player.sendMessage(new TextComponentString("§a[竞技场] “只拿模式”已激活。"));
        if (!newItemsToProtect.isEmpty()) {
            player.sendMessage(new TextComponentString(
                    String.format("§a -> 已自动保护您背包中的 %d 种物品。", newItemsToProtect.size())));
        }
        if (!presetItemsFound.isEmpty()) {
            player.sendMessage(new TextComponentString(
                    String.format("§e -> 您背包中的 %d 种预设物品将根据“预设物品选择”的设置进行处理。", presetItemsFound.size())));
        }
        if (newItemsToProtect.isEmpty() && presetItemsFound.isEmpty()) {
            player.sendMessage(new TextComponentString("§a -> 您的背包是空的，无需额外保护。"));
        }
    }

    public static void resetPickOnlySnapshot() {
        initialInventoryItems.clear();
        inventorySnapshotTaken = false;
        dropProtectionWarningShown = false;
    }

    public static void processItems() {
        if (outsideArenaMessageCooldown > 0)
            outsideArenaMessageCooldown--;
        if (!arenaProcessingEnabled)
            return;

        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null)
            return;

        if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    String.format("§d[调试] §7调用 processItems, 当前丢弃模式: §b%s", dropMode.getDisplayName())));
        }

        if (!isPlayerInArena()) {
            if (outsideArenaMessageCooldown <= 0) {
                player.sendMessage(new TextComponentString("§e[竞技场] §7检测到不在竞技场中，前往竞技场自动启用功能。"));
                outsideArenaMessageCooldown = 100;
            }
            return;
        }

        try {
            cleanupExpiredCooldowns();
            if (currentMode == ItemMode.PICK_ONLY && !inventorySnapshotTaken) {
                initializePickOnlyMode();
            }
            if (itemTopEnabled)
                handleItemTop();
            if (fullBagDropEnabled && isInventoryFull())
                handleFullBagDrop();
            handleItemPickDropSafely();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("处理竞技场物品时出错", e);
        }
    }

    public static void onModeChanged() {
        if (currentMode == ItemMode.PICK_ONLY) {
            resetPickOnlySnapshot();
        } else {
            initialInventoryItems.clear();
            inventorySnapshotTaken = false;
        }
        dropProtectionWarningShown = false;
    }

    /**
     * !! 核心修复：重写物品置顶逻辑，使其兼容所有容器 !!
     * 不再使用硬编码的索引计算，而是动态遍历当前容器来查找正确的槽位。
     * 这确保了在打开箱子或任何其他GUI时，该功能依然能正确地在玩家背包中操作。
     */
    private static void handleItemTop() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || itemToTop == null || itemToTop.trim().isEmpty())
            return;

        // 1. 获取当前实际的容器，无论是玩家背包还是箱子GUI
        Container container = player.openContainer;
        if (container == null) {
            zszlScriptMod.LOGGER.error("无法执行物品置顶：玩家容器为空！");
            return;
        }

        String targetItemName = itemToTop.trim();
        int sourceContainerSlot = -1; // 我们要找的是物品在当前容器中的槽位索引

        // 2. 遍历当前容器的所有槽位，找到目标物品
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            // 必须同时满足：槽位有效、有物品、属于玩家背包（而不是箱子）、物品名匹配
            if (slot != null && slot.getHasStack() && slot.inventory == player.inventory
                    && slot.getStack().getDisplayName().contains(targetItemName)) {
                sourceContainerSlot = i; // 找到了！'i'就是正确的容器槽位索引
                break; // 找到后立即停止搜索
            }
        }

        // 3. 找到快捷栏第一个格子的容器索引，以防止物品已经在那里时还进行无效交换
        int targetHotbarIndex = 0;
        int targetContainerSlot = -1;
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            // slot.getSlotIndex() 是物品在它所属的IInventory（例如player.inventory）中的索引
            if (slot != null && slot.inventory == player.inventory && slot.getSlotIndex() == targetHotbarIndex) {
                targetContainerSlot = i; // 'i' 是这个快捷栏格子在当前容器中的索引
                break;
            }
        }

        // 4. 如果找到了物品，并且它不在目标位置，则执行交换
        if (sourceContainerSlot != -1 && sourceContainerSlot != targetContainerSlot) {
            if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                player.sendMessage(
                        new TextComponentString(String.format("§d[调试] §7检测到置顶物品 [%s] 在容器槽位 §b%d§7, 准备与快捷栏槽位 §b%d§7 交换。",
                                targetItemName, sourceContainerSlot, targetHotbarIndex)));
            }

            // 优先使用标准窗口点击，可靠将物品交换到快捷栏第一格。
            // ClickType.SWAP + button=0 表示与快捷栏第1格交换。
            try {
                Minecraft.getMinecraft().playerController.windowClick(
                        container.windowId,
                        sourceContainerSlot,
                        targetHotbarIndex,
                        ClickType.SWAP,
                        player);
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("[竞技场] 标准置顶交换失败，回退到点击包方案", e);
                moveOrSwapItemByClickPacket(sourceContainerSlot, targetContainerSlot);
            }
        } else if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
            if (sourceContainerSlot == -1) {
                player.sendMessage(new TextComponentString("§d[调试] §7置顶未执行：未在玩家背包中找到匹配物品 -> §f" + targetItemName));
            }
        }
    }

    /**
     * 通过 OwlView_slot_click 包执行物品移动/交换。
     * 固定顺序：源槽位 -> 目标槽位 -> 源槽位。
     */
    private static void moveOrSwapItemByClickPacket(int sourceContainerSlot, int destinationContainerSlot) {
        sendClickPacket(sourceContainerSlot);
        ModUtils.DelayScheduler.instance.schedule(() -> sendClickPacket(destinationContainerSlot), 2);
        ModUtils.DelayScheduler.instance.schedule(() -> sendClickPacket(sourceContainerSlot), 4);
    }

    /**
     * 构建并发送一个 OwlView_slot_click 原始数据包。
     */
    private static void sendClickPacket(int slotIndex) {
        String sessionIdHex = PacketCaptureHandler.getSessionIdAsHex();
        if (sessionIdHex == null || Minecraft.getMinecraft().getConnection() == null) {
            return;
        }

        String formattedSlot = String.format("%02X %02X %02X %02X",
                (slotIndex >> 24) & 0xFF, (slotIndex >> 16) & 0xFF, (slotIndex >> 8) & 0xFF, slotIndex & 0xFF);
        String hexPayload = String.format(CLICK_PACKET_TEMPLATE, formattedSlot).replace("{id}", sessionIdHex);

        try {
            String cleanHex = hexPayload.replaceAll("\\s", "");
            byte[] data = new byte[cleanHex.length() / 2];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) Integer.parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
            }

            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            FMLProxyPacket packet = new FMLProxyPacket(buffer, PACKET_CHANNEL);
            Minecraft.getMinecraft().getConnection().sendPacket(packet);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[竞技场] 发送置顶点击包失败", e);
        }
    }

    private static void handleItemPickDropSafely() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        Container container = player.openContainer;
        if (container == null)
            return;

        long currentTime = System.currentTimeMillis();
        List<DropCandidate> dropCandidates = new ArrayList<>();

        try {
            if (detectionRange == DetectionRange.INVENTORY_AND_CHEST) {
                // 背包+箱子模式：优先只处理背包；仅当背包无可丢弃物时，才处理箱子。
                List<Integer> inventorySlots = getPlayerInventorySlots(container, player);
                List<DropCandidate> inventoryCandidates = collectDropCandidates(container, inventorySlots, currentTime);

                if (!inventoryCandidates.isEmpty()) {
                    dropCandidates = inventoryCandidates;
                } else {
                    List<Integer> nonInventorySlots = getNonPlayerInventorySlots(container, player);
                    dropCandidates = collectDropCandidates(container, nonInventorySlots, currentTime);
                }
            } else {
                // 仅背包模式
                List<Integer> validSlots = getValidInventorySlots(container);
                dropCandidates = collectDropCandidates(container, validSlots, currentTime);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("扫描容器槽位时出错", e);
            return;
        }

        int maxProcessPerTick = drop_maxItemsPerTick;
        int processed = 0;

        for (DropCandidate candidate : dropCandidates) {
            if (processed >= maxProcessPerTick)
                break;
            if (smartDropItemSafely(container, candidate.slotIndex, candidate.itemName, candidate.originalCount)) {
                processed++;
                itemCooldowns.put(candidate.itemName, currentTime);
            } else {
                break;
            }
        }
    }

    private static List<DropCandidate> collectDropCandidates(Container container, List<Integer> slotIndices,
            long currentTime) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        List<DropCandidate> dropCandidates = new ArrayList<>();

        for (int slotIndex : slotIndices) {
            Slot slot = container.getSlot(slotIndex);
            if (slot == null || !slot.getHasStack())
                continue;

            String itemName = slot.getStack().getDisplayName().trim();
            if (itemName.isEmpty())
                continue;

            Long itemCooldown = itemCooldowns.get(itemName);
            if (itemCooldown != null && (currentTime - itemCooldown) < drop_itemCooldownMs) {
                continue;
            }

            Integer attempts = dropAttempts.get(itemName);
            if (attempts != null && attempts >= MAX_DROP_ATTEMPTS_PER_ITEM) {
                Long lastDrop = lastDropTime.get(itemName);
                if (lastDrop != null && (currentTime - lastDrop) < DROP_COOLDOWN_MS) {
                    if (!dropProtectionWarningShown && player != null) {
                        player.sendMessage(
                                new TextComponentString("§e[竞技场] 检测到物品 §c" + itemName + " §e可能受到丢弃保护，已暂停处理。"));
                        dropProtectionWarningShown = true;
                    }
                    continue;
                } else {
                    dropAttempts.remove(itemName);
                    lastDropTime.remove(itemName);
                }
            }

            if (shouldDropItem(itemName, slot)) {
                dropCandidates.add(new DropCandidate(slotIndex, itemName, slot.getStack().getCount()));
            }
        }

        return dropCandidates;
    }

    private static List<Integer> getPlayerInventorySlots(Container container, EntityPlayerSP player) {
        List<Integer> slots = new ArrayList<>();
        if (container == null || player == null)
            return slots;

        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.inventory == player.inventory) {
                slots.add(i);
            }
        }
        return slots;
    }

    private static List<Integer> getNonPlayerInventorySlots(Container container, EntityPlayerSP player) {
        List<Integer> slots = new ArrayList<>();
        if (container == null || player == null)
            return slots;

        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.inventory != player.inventory) {
                slots.add(i);
            }
        }
        return slots;
    }

    public static boolean isInventorySnapshotTaken() {
        return inventorySnapshotTaken;
    }

    public static int getInitialInventoryItemCount() {
        return initialInventoryItems.size();
    }

    private static boolean isInventoryFull() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null)
            return true;
        for (int i = 9; i < 36; i++) {
            if (player.inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void handleFullBagDrop() {
        for (String itemName : dropPriority) {
            if (smartDropItemByName(itemName)) {
                break;
            }
        }
    }

    private static boolean smartDropItemByName(String targetName) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null)
            return false;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getDisplayName().contains(targetName)) {
                Container container = player.openContainer != null ? player.openContainer : player.inventoryContainer;
                int containerSlotIndex = i;
                return smartDropItemSafely(container, containerSlotIndex, stack.getDisplayName(), stack.getCount());
            }
        }
        return false;
    }

    /**
     * [核心修改] 根据新的检测范围设置来决定返回哪些槽位
     */
    private static List<Integer> getValidInventorySlots(Container container) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        List<Integer> validSlots = new ArrayList<>();
        if (container == null || player == null)
            return validSlots;

        int totalSlots = container.inventorySlots.size();

        if (detectionRange == DetectionRange.INVENTORY_ONLY) {
            // 只检查玩家背包
            for (int i = 0; i < totalSlots; i++) {
                Slot slot = container.getSlot(i);
                if (slot != null && slot.inventory == player.inventory) {
                    validSlots.add(i);
                }
            }
        } else { // INVENTORY_AND_CHEST
            // 检查所有槽位
            for (int i = 0; i < totalSlots; i++) {
                validSlots.add(i);
            }
        }
        return validSlots;
    }

    private static boolean shouldDropItem(String itemName, Slot slot) {
        if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    String.format("§d[调试] §7检查槽位 §b%d§7: [%s]", slot.getSlotIndex(), itemName)));
        }

        switch (currentMode) {
            case BLACKLIST_WHITELIST:
                if (whitelistFilters.stream().anyMatch(itemName::contains)) {
                    if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                        Minecraft.getMinecraft().player
                                .sendMessage(new TextComponentString("§d[调试] §f -> §a保留 (白名单匹配)"));
                    }
                    return false;
                }
                if (blacklistFilters.stream().anyMatch(itemName::contains)) {
                    if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                        Minecraft.getMinecraft().player
                                .sendMessage(new TextComponentString("§d[调试] §f -> §c丢弃 (黑名单匹配)"));
                    }
                    return true;
                }
                if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§d[调试] §f -> §a保留 (默认)"));
                }
                return false;

            case PICK_ONLY:
                if (initialInventoryItems.contains(itemName)) {
                    if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                        Minecraft.getMinecraft().player
                                .sendMessage(new TextComponentString("§d[调试] §f -> §a保留 (初始背包物品)"));
                    }
                    return false;
                }
                if (selectedPresetItems.stream().anyMatch(itemName::contains)) {
                    if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                        Minecraft.getMinecraft().player
                                .sendMessage(new TextComponentString("§d[调试] §f -> §a保留 (预设列表匹配)"));
                    }
                    return false;
                }
                if (customPickItems.stream().anyMatch(itemName::contains)) {
                    if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                        Minecraft.getMinecraft().player
                                .sendMessage(new TextComponentString("§d[调试] §f -> §a保留 (自定义列表匹配)"));
                    }
                    return false;
                }
                if (debugModeEnabled && ModConfig.isDebugFlagEnabled(DebugModule.ARENA_HANDLER)) {
                    Minecraft.getMinecraft().player
                            .sendMessage(new TextComponentString("§d[调试] §f -> §c丢弃 (未在任何保留列表中)"));
                }
                return true;

            default:
                return false;
        }
    }

    private static boolean smartDropItemSafely(Container container, int slotIndex, String itemName, int originalCount) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || container == null)
            return false;

        try {
            Slot slot = container.getSlot(slotIndex);
            if (slot == null || slot.getStack().isEmpty())
                return false;

            int countBeforeDrop = slot.getStack().getCount();

            dropAttempts.merge(itemName, 1, Integer::sum);
            lastDropTime.put(itemName, System.currentTimeMillis());

            Minecraft.getMinecraft().playerController.windowClick(container.windowId, slotIndex, 0, ClickType.PICKUP,
                    player);
            Minecraft.getMinecraft().playerController.windowClick(container.windowId, -999, 0, ClickType.PICKUP,
                    player);

            ModUtils.DelayScheduler.instance.schedule(() -> {
                try {
                    Slot checkSlot = container.getSlot(slotIndex);
                    if (checkSlot != null) {
                        int countAfterDrop = checkSlot.getStack().isEmpty() ? 0 : checkSlot.getStack().getCount();
                        if (countAfterDrop >= countBeforeDrop) {
                            zszlScriptMod.LOGGER.warn("物品 {} 丢弃失败 (尝试次数: {})", itemName, dropAttempts.get(itemName));
                            Integer attempts = dropAttempts.get(itemName);
                            if (attempts != null && attempts >= MAX_DROP_ATTEMPTS_PER_ITEM) {
                                player.sendMessage(
                                        new TextComponentString("§c[竞技场] 物品 " + itemName + " 可能受到丢弃保护，已停止尝试。"));
                            }
                        } else {
                            zszlScriptMod.LOGGER.info("成功丢弃物品: {} (剩余: {})", itemName, countAfterDrop);
                            dropAttempts.remove(itemName);
                            lastDropTime.remove(itemName);
                        }
                    }
                } catch (Exception e) {
                    zszlScriptMod.LOGGER.error("检查丢弃结果时出错: {}", e.getMessage());
                }
            }, 3);

            return true;

        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("丢弃物品 {} 时出错: {}", itemName, e.getMessage());
            return false;
        }
    }

    private static void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        itemCooldowns.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > drop_itemCooldownMs);
        lastDropTime.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > DROP_COOLDOWN_MS);
    }

    public static void resetProtectionState() {
        dropAttempts.clear();
        lastDropTime.clear();
        itemCooldowns.clear();
        dropProtectionWarningShown = false;
        zszlScriptMod.LOGGER.info("竞技场物品保护状态已重置");
    }

    private static class DropCandidate {
        final int slotIndex;
        final String itemName;
        final int originalCount;

        DropCandidate(int slotIndex, String itemName, int originalCount) {
            this.slotIndex = slotIndex;
            this.itemName = itemName;
            this.originalCount = originalCount;
        }
    }
}
