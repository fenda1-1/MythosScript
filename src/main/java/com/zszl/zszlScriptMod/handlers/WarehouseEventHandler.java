// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/WarehouseEventHandler.java
package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.system.dungeon.ChestData;
import com.zszl.zszlScriptMod.system.dungeon.WarehouseDepositPolicy;
import com.zszl.zszlScriptMod.system.dungeon.Warehouse;
import com.zszl.zszlScriptMod.utils.ModUtils;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen; // !! 修复：添加缺失的导入
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class WarehouseEventHandler extends Gui {
    public static final WarehouseEventHandler INSTANCE = new WarehouseEventHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean oneClickDepositMode = false;
    private static final List<BlockPos> chestsToHighlight = new CopyOnWriteArrayList<>();
    private static Set<String> playerItemKeys = new HashSet<>();

    // 自动按高亮箱子逐个存入流程
    private static final Deque<BlockPos> autoDepositRouteQueue = new ArrayDeque<>();
    private static boolean autoDepositRouteRunning = false;
    private static final Deque<BlockPos> scanRouteQueue = new ArrayDeque<>();
    private static boolean scanRouteRunning = false;
    private static BlockPos scanRouteCurrentTarget = null;
    private static int scanRouteWaitTicks = 0;
    private static final java.util.LinkedHashSet<ChestData> completedDepositPolicies = new java.util.LinkedHashSet<>();
    private static final Deque<com.google.gson.JsonObject> postDepositSpreads = new ArrayDeque<>();
    private static final Deque<String> postDepositSequences = new ArrayDeque<>();
    private static boolean postDepositRunning;
    private static final Set<String> routeSpreadNames = new LinkedHashSet<>();
    private static int spreadWindowId = -1;
    private static boolean openChestPolicyCompleted;
    private static String activePostSequence = "";
    private static Map<String, Integer> completedInventory = Collections.emptyMap();
    private static int depositIdleTicks;

    private static Map<String, Integer> inventoryCounts() {
        Map<String, Integer> counts = new HashMap<>();
        if (mc.player != null) for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (!stack.isEmpty()) counts.merge(INSTANCE.getUniqueItemKey(stack) + ":" + stack.getMetadata(), stack.getCount(), Integer::sum);
        }
        return counts;
    }
    private static final Deque<com.google.gson.JsonObject> postDepositStacks = new ArrayDeque<>();
    private static final java.util.LinkedHashSet<String> pendingSpreadNames = new java.util.LinkedHashSet<>();

    public static java.util.List<String> spreadNames(ChestData chest) {
        return WarehouseDepositPolicy.parseNames(chest != null && chest.spreadAfterDeposit ? chest.spreadItemNames : null);
    }

    public static java.util.List<String> orderedDepositNames(ChestData chest) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (chest.depositItemOrder != null) {
            for (String name : chest.depositItemOrder) {
                if (chest.designatedItems != null && chest.designatedItems.contains(name)) names.add(name);
            }
        }
        if (chest.designatedItems != null) {
            java.util.List<String> remaining = new ArrayList<>(chest.designatedItems);
            java.util.Collections.sort(remaining);
            names.addAll(remaining);
        }
        return new ArrayList<>(names);
    }

    private int depositableCount(ChestData chest, Slot slot) {
        int raw = slot.getSlotIndex();
        if (raw < 0 || raw >= 36 || !slot.getHasStack()) return 0;
        if (!WarehouseDepositPolicy.includesSlot(chest.depositInventorySlots, raw)) return 0;
        ItemStack stack = slot.getStack();
        Set<String> reservedNames = new HashSet<>(spreadNames(chest));
        if (autoDepositRouteRunning) reservedNames.addAll(routeSpreadNames);
        if (!reservedNames.contains(net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(stack.getDisplayName()))
                && !reservedNames.contains(stack.getDisplayName())) return stack.getCount();
        int reserved = stack.getMaxStackSize();
        for (int i = 0; i < raw && reserved > 0; i++) {
            ItemStack previous = mc.player.inventory.getStackInSlot(i);
            if (!previous.isEmpty() && previous.getDisplayName().equals(stack.getDisplayName())) {
                reserved -= previous.getCount();
            }
        }
        return WarehouseDepositPolicy.excess(stack.getCount(), stack.getMaxStackSize() - reserved, stack.getMaxStackSize());
    }

    private static com.google.gson.JsonObject spreadParams(String name) {
        com.google.gson.JsonObject params = new com.google.gson.JsonObject();
        params.addProperty("itemName", name);
        params.addProperty("matchMode", "EXACT");
        params.addProperty("sourceScope", "INVENTORY");
        params.addProperty("targetScope", "INVENTORY");
        params.addProperty("spreadMode", "ONE_PER_SLOT");
        params.addProperty("remainderMode", "RETURN_SOURCE");
        params.addProperty("onlyEmptySlots", true);
        params.addProperty("preserveSourceSlot", true);
        params.addProperty("continueOnInsufficient", true);
        return params;
    }

    private void beginPostDeposit() {
        for (ChestData chest : completedDepositPolicies) {
            pendingSpreadNames.addAll(spreadNames(chest));
            if (chest.postDepositSequence != null && !chest.postDepositSequence.trim().isEmpty()
                    && !postDepositSequences.contains(chest.postDepositSequence.trim())) {
                postDepositSequences.add(chest.postDepositSequence.trim());
            }
        }
        completedDepositPolicies.clear();
        for (String name : pendingSpreadNames) postDepositStacks.add(spreadParams(name));
        postDepositRunning = !postDepositStacks.isEmpty() || !postDepositSequences.isEmpty();
        spreadWindowId = mc.player.openContainer.windowId;
        completedInventory = inventoryCounts();
    }

    private void tickPostDeposit() {
        if (!activePostSequence.isEmpty()) {
            if (com.zszl.zszlScriptMod.path.PathSequenceEventListener.isSequenceActiveForMcp(activePostSequence)) return;
            activePostSequence = "";
        }
        if (ItemSpreadHandler.isSpreadInProgress() || ItemSpreadHandler.isStackInProgress()) return;
        if (!mc.player.inventory.getItemStack().isEmpty()
                || (!postDepositStacks.isEmpty() || !postDepositSpreads.isEmpty() || !pendingSpreadNames.isEmpty())
                && mc.player.openContainer.windowId != spreadWindowId) {
            postDepositStacks.clear();
            postDepositSpreads.clear();
            pendingSpreadNames.clear();
            postDepositSequences.clear();
            postDepositRunning = false;
            return;
        }
        if (!postDepositStacks.isEmpty()) {
            ItemSpreadHandler.stackInventoryItems(postDepositStacks.removeFirst());
            return;
        }
        if (!pendingSpreadNames.isEmpty()) {
            java.util.List<Integer> empty = new ArrayList<>();
            for (int i = 0; i < 36; i++) if (mc.player.inventory.getStackInSlot(i).isEmpty()) empty.add(i);
            int index = 0;
            List<List<Integer>> groups = WarehouseDepositPolicy.divideSlots(empty, pendingSpreadNames.size());
            for (String name : pendingSpreadNames) {
                List<Integer> group = groups.get(index++);
                com.google.gson.JsonObject params = spreadParams(name);
                com.google.gson.JsonArray targets = new com.google.gson.JsonArray();
                for (Integer slot : group) targets.add(slot);
                if (!group.isEmpty()) {
                    params.add("targetSlots", targets);
                    postDepositSpreads.add(params);
                }
            }
            pendingSpreadNames.clear();
        }
        if (!postDepositSpreads.isEmpty()) {
            ItemSpreadHandler.spreadInventoryItem(postDepositSpreads.removeFirst());
            return;
        }
        if (!postDepositSequences.isEmpty()) {
            String sequence = postDepositSequences.removeFirst();
            if (com.zszl.zszlScriptMod.path.PathSequenceManager.hasSequence(sequence)
                    && !com.zszl.zszlScriptMod.path.PathSequenceEventListener.isSequenceActiveForMcp(sequence)) {
                activePostSequence = sequence;
                com.google.gson.JsonObject params = new com.google.gson.JsonObject();
                params.addProperty("sequenceName", sequence);
                com.zszl.zszlScriptMod.path.PathSequenceManager.parseAction("run_sequence", params).accept(mc.player);
            }
            return;
        }
        postDepositRunning = false;
        completedInventory = inventoryCounts();
    }
    private static BlockPos autoDepositCurrentTarget = null;
    private static int autoDepositOpenWaitTicks = 0;

    private static ChestData currentOpenChestData = null;
    private static boolean isStandardChestGui = false;
    private static int autoDepositCooldown = 0;
    private static final int AUTO_DEPOSIT_INTERVAL_TICKS = 2;

    // --- 滚动条和选择状态 ---

    // --- 按钮引用 ---


    private WarehouseEventHandler() {
    }

    /** Starts a one-click route that opens every recorded unscanned chest. */
    public static boolean startScanUnscannedChests(Warehouse warehouse) {
        if (mc.player == null || mc.world == null || warehouse == null) {
            return false;
        }
        if (scanRouteRunning || autoDepositRouteRunning || postDepositRunning) {
            mc.player.sendMessage(new TextComponentString("§e[仓库] 已有自动仓库流程正在运行。"));
            return false;
        }

        // Capture any records whose chunks are already loaded before creating
        // the navigation queue. Remaining records are opened through the
        // existing GoToAndOpenHandler so the server sends a fresh inventory.
        WarehouseManager.scanUnscannedChestsInWarehouse(warehouse);
        scanRouteQueue.clear();
        for (ChestData chest : warehouse.chests) {
            if (chest != null && chest.pos != null && !chest.hasBeenScanned) {
                scanRouteQueue.addLast(chest.pos);
            }
        }
        if (scanRouteQueue.isEmpty()) {
            return false;
        }
        scanRouteRunning = true;
        scanRouteCurrentTarget = null;
        scanRouteWaitTicks = 0;
        startNextScanRouteChest();
        return true;
    }

    private static void startNextScanRouteChest() {
        if (!scanRouteRunning || mc.player == null) {
            return;
        }
        while (!scanRouteQueue.isEmpty()) {
            BlockPos next = scanRouteQueue.removeFirst();
            Warehouse current = WarehouseManager.findWarehouseForPos(next);
            ChestData chest = current == null ? null : current.getChestAt(next);
            if (chest == null || chest.hasBeenScanned) {
                continue;
            }
            if (!GoToAndOpenHandler.start(next)) {
                continue;
            }
            scanRouteCurrentTarget = next;
            scanRouteWaitTicks = 0;
            mc.player.sendMessage(new TextComponentString("§b[仓库] 前往扫描箱子: " + next));
            return;
        }
        scanRouteRunning = false;
        scanRouteCurrentTarget = null;
        mc.player.sendMessage(new TextComponentString("§a[仓库] 未扫描箱子处理完成。"));
    }

    public static void startAutoDepositByHighlights() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (isAutoDepositRouteRunning()) {
            mc.player.sendMessage(new TextComponentString("§e[仓库] 自动存入流程已在运行中。"));
            return;
        }

        WarehouseManager.updateCurrentWarehouse();
        if (WarehouseManager.currentWarehouse == null) {
            mc.player.sendMessage(new TextComponentString("§c[仓库] 当前不在激活仓库区域内。"));
            return;
        }

        // 流程依赖高亮箱子，强制开启一键存入模式
        oneClickDepositMode = true;
        INSTANCE.updatePlayerItemKeys();
        INSTANCE.updateHighlightList();

        if (playerItemKeys.isEmpty()) {
            mc.player.sendMessage(new TextComponentString("§e[仓库] 背包中没有可自动存入的目标物品。"));
            return;
        }
        if (chestsToHighlight.isEmpty()) {
            mc.player.sendMessage(new TextComponentString("§e[仓库] 没有可用的高亮箱子。"));
            return;
        }

        List<BlockPos> sorted = new ArrayList<>(chestsToHighlight);
        sorted.sort(Comparator.comparingDouble(pos -> {
            Vec3d p = mc.player.getPositionVector();
            double dx = (pos.getX() + 0.5) - p.x;
            double dz = (pos.getZ() + 0.5) - p.z;
            return dx * dx + dz * dz;
        }));

        autoDepositRouteQueue.clear();
        completedDepositPolicies.clear();
        routeSpreadNames.clear();
        for (BlockPos pos : sorted) {
            ChestData policy = WarehouseManager.currentWarehouse.getChestAt(pos);
            if (policy != null) routeSpreadNames.addAll(spreadNames(policy));
        }
        autoDepositRouteQueue.addAll(sorted);
        autoDepositRouteRunning = true;
        autoDepositCurrentTarget = null;
        autoDepositOpenWaitTicks = 0;
        mc.player.sendMessage(new TextComponentString("§a[仓库] 已启动自动存入流程，目标箱子数: " + sorted.size()));
        INSTANCE.startNextAutoDepositTarget();
    }

    public static boolean isAutoDepositRouteRunning() {
        return autoDepositRouteRunning || postDepositRunning || scanRouteRunning;
    }

    private void stopAutoDepositRoute(String reason) {
        if (mc.player != null && reason != null && !reason.isEmpty()) {
            mc.player.sendMessage(new TextComponentString(reason));
        }
        autoDepositRouteRunning = false;
        autoDepositCurrentTarget = null;
        autoDepositOpenWaitTicks = 0;
        autoDepositRouteQueue.clear();
        beginPostDeposit();
    }

    private void startNextAutoDepositTarget() {
        if (!autoDepositRouteRunning || mc.player == null) {
            return;
        }

        updatePlayerItemKeys();
        if (playerItemKeys.isEmpty()) {
            stopAutoDepositRoute("§a[仓库] 背包目标物品已全部存入，流程结束。");
            return;
        }

        while (!autoDepositRouteQueue.isEmpty()) {
            BlockPos next = autoDepositRouteQueue.pollFirst();
            Warehouse wh = WarehouseManager.findWarehouseForPos(next);
            ChestData cd = wh == null ? null : wh.getChestAt(next);
            if (cd == null || !cd.hasBeenScanned) {
                continue;
            }
            if (!hasAnyDepositableForChest(cd)) {
                continue;
            }

            if (!GoToAndOpenHandler.start(next)) {
                continue;
            }
            autoDepositCurrentTarget = next;
            autoDepositOpenWaitTicks = 0;
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§b[仓库] 前往箱子: " + next));
            }
            return;
        }

        stopAutoDepositRoute("§a[仓库] 已遍历所有高亮箱子，自动存入流程结束。");
    }

    private boolean hasAnyDepositableForChest(ChestData chestData) {
        if (chestData == null || playerItemKeys.isEmpty()) {
            return false;
        }
        if (chestData.depositItemsConfigured || chestData.designatedItems != null && !chestData.designatedItems.isEmpty()) {
            List<String> names = orderedDepositNames(chestData);
            for (Slot slot : mc.player.inventoryContainer.inventorySlots) {
                if (slot.inventory == mc.player.inventory && slot.getHasStack()
                        && slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 36
                        && (depositableCount(chestData, slot) > 0 || spreadNames(chestData).contains(slot.getStack().getDisplayName()))
                        && depositPriority(slot.getStack(), names) != Integer.MAX_VALUE) return true;
            }
            return false;
        }

        // 与“高亮箱子”使用同一判定口径：玩家背包物品Key 与 箱子快照物品Key 交集
        // 避免因 designatedItems 文本匹配失败，导致路线在首个目标就被误判为“可存入物品为空”。
        NonNullList<ItemStack> chestItems = chestData.getSnapshotContents(54);
        for (ItemStack chestItem : chestItems) {
            if (chestItem.isEmpty()) {
                continue;
            }
            String chestItemKey = getUniqueItemKey(chestItem);
            if (playerItemKeys.contains(chestItemKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDepositableItemsInOpenContainer(ContainerChest container) {
        if (mc.player == null || currentOpenChestData == null || container == null) {
            return false;
        }
        if (currentOpenChestData.designatedItems == null || currentOpenChestData.designatedItems.isEmpty()) {
            return false;
        }

        for (Slot slot : container.inventorySlots) {
            if (slot.inventory != mc.player.inventory || !slot.getHasStack()) {
                continue;
            }
            if (depositableCount(currentOpenChestData, slot) <= 0) continue;
            ItemStack playerStack = slot.getStack();
            String playerItemName = playerStack.getDisplayName();

            if (playerStack.getItem() instanceof ItemShulkerBox) {
                NBTTagCompound nbt = playerStack.getSubCompound("BlockEntityTag");
                if (nbt != null && nbt.hasKey("Items", 9)) {
                    NonNullList<ItemStack> shulkerItems = NonNullList.withSize(27, ItemStack.EMPTY);
                    ItemStackHelper.loadAllItems(nbt, shulkerItems);
                    for (ItemStack shulkerItem : shulkerItems) {
                        if (!shulkerItem.isEmpty()
                                && currentOpenChestData.designatedItems.contains(shulkerItem.getDisplayName())) {
                            return true;
                        }
                    }
                }
            } else if (currentOpenChestData.designatedItems.contains(playerItemName)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        autoDepositRouteRunning = postDepositRunning = false;
        scanRouteRunning = false;
        autoDepositCurrentTarget = null;
        scanRouteCurrentTarget = null;
        autoDepositRouteQueue.clear();
        scanRouteQueue.clear();
        completedDepositPolicies.clear();
        postDepositStacks.clear();
        postDepositSpreads.clear();
        postDepositSequences.clear();
        pendingSpreadNames.clear();
        routeSpreadNames.clear();
        activePostSequence = "";
        chestPanel = null;
        currentOpenChestData = null;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        if (event.phase != TickEvent.Phase.END || mc.player == null || event.player != mc.player)
            return;
        if (scanRouteRunning && scanRouteCurrentTarget != null) {
            scanRouteWaitTicks++;
            if (scanRouteWaitTicks > 700) {
                mc.player.sendMessage(new TextComponentString("§e[仓库] 扫描箱子超时，尝试下一个目标。"));
                scanRouteCurrentTarget = null;
                scanRouteWaitTicks = 0;
                startNextScanRouteChest();
            }
        }
        if (postDepositRunning) {
            tickPostDeposit();
            return;
        }
        if (openChestPolicyCompleted && !completedInventory.equals(inventoryCounts())) {
            openChestPolicyCompleted = false;
        }

        if (mc.player.ticksExisted % 20 == 0) {
            WarehouseManager.updateCurrentWarehouse();
        }

        if (oneClickDepositMode && WarehouseManager.currentWarehouse != null) {
            if (mc.player.ticksExisted % 5 == 0) {
                updatePlayerItemKeys();
                updateHighlightList();
            }
        } else {
            chestsToHighlight.clear();
        }

        if (isStandardChestGui && currentOpenChestData != null && currentOpenChestData.autoDepositEnabled
                && !openChestPolicyCompleted
                && mc.player.openContainer instanceof ContainerChest) {
            if (autoDepositCooldown > 0) {
                autoDepositCooldown--;
            } else {
                Map<String, Integer> before = inventoryCounts();
                executeAutoDeposit((ContainerChest) mc.player.openContainer);
                depositIdleTicks = before.equals(inventoryCounts()) ? depositIdleTicks + AUTO_DEPOSIT_INTERVAL_TICKS : 0;
                // 需求：固定每 2 tick 执行一次存入
                autoDepositCooldown = Math.max(0, AUTO_DEPOSIT_INTERVAL_TICKS - 1);
            }
            if (!hasDepositableItemsInOpenContainer((ContainerChest) mc.player.openContainer)) {
                openChestPolicyCompleted = true;
                completedDepositPolicies.add(currentOpenChestData);
                if (!autoDepositRouteRunning) beginPostDeposit();
                completedInventory = inventoryCounts();
            } else if (depositIdleTicks >= 200) {
                currentOpenChestData.autoDepositEnabled = false;
                autoDepositRouteRunning = false;
                autoDepositCurrentTarget = null;
                autoDepositRouteQueue.clear();
                completedDepositPolicies.clear();
                mc.player.sendMessage(new TextComponentString(net.minecraft.client.resources.I18n.format("gui.modern.warehouse.deposit_stalled")));
            }
        }

        if (autoDepositRouteRunning) {
            // 等待目标箱子打开
            if (autoDepositCurrentTarget != null && !(mc.currentScreen instanceof GuiChest)) {
                autoDepositOpenWaitTicks++;
                if (autoDepositOpenWaitTicks > 200) {
                    if (mc.player != null) {
                        mc.player.sendMessage(new TextComponentString("§e[仓库] 打开箱子超时，尝试下一个目标。"));
                    }
                    startNextAutoDepositTarget();
                }
            }

            // 目标箱子已打开并处理完毕后，关闭并前往下一个
            if (mc.currentScreen instanceof GuiChest && mc.player.openContainer instanceof ContainerChest
                    && autoDepositCurrentTarget != null) {
                // 仅在当前箱子数据已就绪后再判断“是否处理完毕”，避免刚开箱即被误判跳过
                if (currentOpenChestData != null
                        && !hasDepositableItemsInOpenContainer((ContainerChest) mc.player.openContainer)) {
                    mc.displayGuiScreen(null);
                    autoDepositCurrentTarget = null;
                    autoDepositOpenWaitTicks = 0;
                    ModUtils.DelayScheduler.instance.schedule(this::startNextAutoDepositTarget, 6);
                }
            }
        }
        } finally {
            timer.stop();
        }
    }

    public void onGuiOpen(GuiOpenEvent event) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        isStandardChestGui = false;
        openChestPolicyCompleted = false;
        depositIdleTicks = 0;
        currentOpenChestData = null;
        chestPanel = null;
        panelOwner = null;

        if (!(event.getGui() instanceof GuiChest))
            return;

        GuiChest gui = (GuiChest) event.getGui();
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory chestInventory = container.getLowerChestInventory();
        String title = chestInventory.getDisplayName().getUnformattedText();

        if (title.equals("箱子") || title.equals("大型箱子")
                || title.equals(net.minecraft.client.resources.I18n.format("container.chest"))
                || title.equals(net.minecraft.client.resources.I18n.format("container.chestDouble"))) {
            isStandardChestGui = true;
        }
        if (title.contains("副本仓库:")) {
            return;
        }

        BlockPos chestPos = null;
        if (chestInventory instanceof TileEntityChest) {
            chestPos = ((TileEntityChest) chestInventory).getPos();
        }
        if (chestPos == null) {
            RayTraceResult rayTrace = mc.objectMouseOver;
            if (rayTrace != null && rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos hitPos = rayTrace.getBlockPos();
                if (mc.world.getBlockState(hitPos).getBlock() instanceof BlockChest) {
                    chestPos = hitPos;
                }
            }
        }

        if (chestPos == null) {
            zszlScriptMod.LOGGER.warn("无法确定打开的箱子的位置。");
            return;
        }

        final BlockPos finalChestPos = chestPos;

        ModUtils.DelayScheduler.instance.schedule(() -> {
            PerformanceMonitor.PerformanceTimer delayedTimer = PerformanceMonitor.startTimer("warehouse");
            try {
            if (!(mc.currentScreen instanceof GuiChest) || mc.player == null || mc.player.openContainer != container)
                return;

            Warehouse targetWarehouse = WarehouseManager.findWarehouseForPos(finalChestPos);
            if (targetWarehouse == null) {
                WarehouseManager.updateCurrentWarehouse();
                targetWarehouse = WarehouseManager.currentWarehouse;
            }

            if (targetWarehouse != null) {
                WarehouseManager.scanChest(chestInventory, finalChestPos);
                currentOpenChestData = targetWarehouse.getChestAt(finalChestPos);

                if (isStandardChestGui && currentOpenChestData != null) {
                    if (!currentOpenChestData.depositItemsConfigured
                            && (currentOpenChestData.designatedItems == null || currentOpenChestData.designatedItems.isEmpty())) {
                        updateDesignatedItems(currentOpenChestData, container);
                    }
                    currentOpenChestData.depositItemsConfigured = true;
                    if (oneClickDepositMode || autoDepositRouteRunning) {
                        currentOpenChestData.autoDepositEnabled = true;
                    }
                }
                if (scanRouteRunning && finalChestPos.equals(scanRouteCurrentTarget)) {
                    scanRouteCurrentTarget = null;
                    scanRouteWaitTicks = 0;
                    ModUtils.DelayScheduler.instance.schedule(() -> {
                        if (mc.currentScreen instanceof GuiChest) {
                            mc.displayGuiScreen(null);
                        }
                        startNextScanRouteChest();
                    }, 8);
                }
            }
            } finally {
                delayedTimer.stop();
            }
        }, 10);
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        if (!chestsToHighlight.isEmpty()) {
            for (BlockPos pos : chestsToHighlight) {
                renderHighlightBox(pos, event.getPartialTicks());
            }
        }
        } finally {
            timer.stop();
        }
    }

    private void updatePlayerItemKeys() {
        playerItemKeys.clear();
        if (mc.player == null)
            return;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack.isEmpty())
                continue;

            if (stack.getItem() instanceof ItemShulkerBox) {
                NBTTagCompound nbt = stack.getSubCompound("BlockEntityTag");
                if (nbt != null && nbt.hasKey("Items", 9)) {
                    NonNullList<ItemStack> shulkerItems = NonNullList.withSize(27, ItemStack.EMPTY);
                    ItemStackHelper.loadAllItems(nbt, shulkerItems);
                    for (ItemStack shulkerItem : shulkerItems) {
                        if (!shulkerItem.isEmpty()) {
                            playerItemKeys.add(getUniqueItemKey(shulkerItem));
                        }
                    }
                }
            } else {
                playerItemKeys.add(getUniqueItemKey(stack));
            }
        }
    }

    private void updateHighlightList() {
        if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS)) {
            if (WarehouseManager.currentWarehouse == null)
                return;
            if (playerItemKeys.isEmpty()) {
                zszlScriptMod.LOGGER.info("[高亮调试] 退出：玩家背包中未检测到可识别的物品。");
                return;
            }
            zszlScriptMod.LOGGER.info("[高亮调试] 开始更新高亮列表，玩家物品Key数量: {}", playerItemKeys.size());
        }

        chestsToHighlight.clear();
        if (WarehouseManager.currentWarehouse == null || playerItemKeys.isEmpty())
            return;

        for (ChestData chestData : WarehouseManager.currentWarehouse.chests) {
            if (chestData.depositItemsConfigured || chestData.designatedItems != null && !chestData.designatedItems.isEmpty()) {
                if (hasAnyDepositableForChest(chestData)) chestsToHighlight.add(chestData.pos);
                continue;
            }
            if (!chestData.hasBeenScanned) {
                if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS)) {
                    zszlScriptMod.LOGGER.info("[高亮调试] 跳过箱子 @ {}: 未被扫描过。", chestData.pos);
                }
                continue;
            }

            NonNullList<ItemStack> chestItems = chestData.getSnapshotContents(54);
            boolean foundMatch = false;
            for (ItemStack chestItem : chestItems) {
                if (chestItem.isEmpty())
                    continue;

                String chestItemKey = getUniqueItemKey(chestItem);
                if (playerItemKeys.contains(chestItemKey)) {
                    chestsToHighlight.add(chestData.pos);
                    foundMatch = true;
                    if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS)) {
                        zszlScriptMod.LOGGER.info("[高亮调试] 匹配成功！箱子 @ {} 将被高亮，因为玩家背包和箱子中都有物品 '{}'。", chestData.pos,
                                chestItem.getDisplayName());
                    }
                    break;
                }
            }
            if (!foundMatch && ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS)) {
                zszlScriptMod.LOGGER.info("[高亮调试] 箱子 @ {} 未找到匹配物品，不进行高亮。", chestData.pos);
            }
        }
    }

    private com.zszl.zszlScriptMod.gui.modern.WarehouseChestPanel chestPanel;
    private ChestData panelOwner;

    private com.zszl.zszlScriptMod.gui.modern.WarehouseChestPanel chestPanel() {
        if (panelOwner != currentOpenChestData || chestPanel == null) {
            panelOwner = currentOpenChestData;
            chestPanel = new com.zszl.zszlScriptMod.gui.modern.WarehouseChestPanel(currentOpenChestData);
        }
        return chestPanel;
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof GuiChest && isStandardChestGui && currentOpenChestData != null) {
            chestPanel().draw(event.getGui(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public void onMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) throws IOException {
        if (!(event.getGui() instanceof GuiChest) || !isStandardChestGui || currentOpenChestData == null) return;
        GuiScreen gui = event.getGui();
        int x = Mouse.getEventX() * gui.width / mc.displayWidth;
        int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        if (chestPanel().mouse(x, y, Mouse.getEventButton(), Mouse.getEventButtonState(), Mouse.getEventDWheel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onKeyboardInputPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (event.getGui() instanceof GuiChest && isStandardChestGui && currentOpenChestData != null
                && org.lwjgl.input.Keyboard.getEventKeyState()
                && chestPanel().key(org.lwjgl.input.Keyboard.getEventCharacter(), org.lwjgl.input.Keyboard.getEventKey())) {
            event.setCanceled(true);
        }
    }

    private void updateDesignatedItems(ChestData chest, ContainerChest container) {
        if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§d[调试] §7开始更新箱子指定物品列表..."));
        }

        Set<String> foundItems = new HashSet<>();
        IInventory chestInventory = container.getLowerChestInventory();
        int inventorySize = chestInventory.getSizeInventory();

        if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(String.format("§d[调试] §7检测到箱子大小为: %d 格", inventorySize)));
        }

        for (int i = 0; i < inventorySize; i++) {
            ItemStack stack = chestInventory.getStackInSlot(i);
            if (stack.isEmpty())
                continue;

            if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
                mc.player.sendMessage(new TextComponentString(String.format("§d[调试] §7 -> 在槽位 %d 找到物品: %s * %d", i,
                        stack.getDisplayName(), stack.getCount())));
            }

            if (stack.getItem() instanceof ItemShulkerBox) {
                NBTTagCompound nbt = stack.getSubCompound("BlockEntityTag");
                if (nbt != null && nbt.hasKey("Items", 9)) {
                    NonNullList<ItemStack> shulkerItems = NonNullList.withSize(27, ItemStack.EMPTY);
                    ItemStackHelper.loadAllItems(nbt, shulkerItems);
                    for (ItemStack shulkerItem : shulkerItems) {
                        if (!shulkerItem.isEmpty()) {
                            foundItems.add(shulkerItem.getDisplayName());
                        }
                    }
                }
            } else {
                foundItems.add(stack.getDisplayName());
            }
        }
        chest.designatedItems = foundItems;

        if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§d[调试] §7检测到箱子中储存物品: " + String.join(", ", foundItems)));
        }

        WarehouseManager.saveWarehouses();
    }

    private void executeAutoDeposit(ContainerChest container) {
        if (mc.player == null || currentOpenChestData == null || currentOpenChestData.designatedItems.isEmpty()) {
            return;
        }

        List<Integer> slotsToClick = new ArrayList<>();

        for (Slot slot : container.inventorySlots) {
            if (slot.inventory == mc.player.inventory && slot.getHasStack()) {
                if (depositableCount(currentOpenChestData, slot) <= 0) continue;
                ItemStack playerStack = slot.getStack();
                String playerItemName = playerStack.getDisplayName();

                boolean shouldDeposit = false;

                if (playerStack.getItem() instanceof ItemShulkerBox) {
                    NBTTagCompound nbt = playerStack.getSubCompound("BlockEntityTag");
                    if (nbt != null && nbt.hasKey("Items", 9)) {
                        NonNullList<ItemStack> shulkerItems = NonNullList.withSize(27, ItemStack.EMPTY);
                        ItemStackHelper.loadAllItems(nbt, shulkerItems);

                        for (ItemStack shulkerItem : shulkerItems) {
                            if (!shulkerItem.isEmpty()
                                    && currentOpenChestData.designatedItems.contains(shulkerItem.getDisplayName())) {
                                shouldDeposit = true;
                                if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
                                    mc.player.sendMessage(new TextComponentString(
                                            String.format("§d[调试] §a潜影盒匹配！§7因其内部含有 [%s]，将存入潜影盒 [%s]。",
                                                    shulkerItem.getDisplayName(), playerItemName)));
                                }
                                break;
                            }
                        }
                    }
                } else {
                    if (currentOpenChestData.designatedItems.contains(playerItemName)) {
                        shouldDeposit = true;
                        if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
                            mc.player.sendMessage(new TextComponentString(
                                    String.format("§d[调试] §a散件匹配！§7将存入物品 [%s]。", playerItemName)));
                        }
                    }
                }

                if (shouldDeposit) {
                    slotsToClick.add(slot.slotNumber);
                }
            }
        }

        if (!slotsToClick.isEmpty()) {
            final List<String> priority = orderedDepositNames(currentOpenChestData);
            slotsToClick.sort(Comparator.comparingInt(index -> depositPriority(container.getSlot(index).getStack(), priority)));
            int slotToClick = slotsToClick.get(0);
            Slot source = container.getSlot(slotToClick);
            int count = depositableCount(currentOpenChestData, source);
            if (count >= source.getStack().getCount()) {
                mc.playerController.windowClick(container.windowId, slotToClick, 0, ClickType.QUICK_MOVE, mc.player);
            } else if (mc.player.inventory.getItemStack().isEmpty()) {
                // Keep the reserved part in its original slot while moving only the excess.
                int reserve = source.getStack().getCount() - count;
                mc.playerController.windowClick(container.windowId, slotToClick, 0, ClickType.PICKUP, mc.player);
                for (int i = 0; i < reserve; i++) {
                    mc.playerController.windowClick(container.windowId, slotToClick, 1, ClickType.PICKUP, mc.player);
                }
                for (Slot target : container.inventorySlots) {
                    if (target.inventory == mc.player.inventory || mc.player.inventory.getItemStack().isEmpty()) continue;
                    ItemStack cursor = mc.player.inventory.getItemStack();
                    if (target.isItemValid(cursor) && (!target.getHasStack()
                            || ItemStack.areItemsEqual(target.getStack(), cursor)
                            && ItemStack.areItemStackTagsEqual(target.getStack(), cursor))) {
                        mc.playerController.windowClick(container.windowId, target.slotNumber, 0, ClickType.PICKUP, mc.player);
                    }
                }
                if (!mc.player.inventory.getItemStack().isEmpty()) {
                    mc.playerController.windowClick(container.windowId, slotToClick, 0, ClickType.PICKUP, mc.player);
                }
            }
            if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
                mc.player.sendMessage(new TextComponentString(String.format("§d[调试] §b执行存入操作，点击槽位: %d", slotToClick)));
            }
        }
    }

    private int depositPriority(ItemStack stack, List<String> names) {
        int rank = names.indexOf(stack.getDisplayName());
        if (stack.getItem() instanceof ItemShulkerBox) {
            NBTTagCompound nbt = stack.getSubCompound("BlockEntityTag");
            if (nbt != null) {
                NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
                ItemStackHelper.loadAllItems(nbt, items);
                for (ItemStack item : items) {
                    int nestedRank = item.isEmpty() ? -1 : names.indexOf(item.getDisplayName());
                    if (nestedRank >= 0 && (rank < 0 || nestedRank < rank)) rank = nestedRank;
                }
            }
        }
        return rank < 0 ? Integer.MAX_VALUE : rank;
    }

    public static void restartOpenChestDeposit() {
        openChestPolicyCompleted = false;
        depositIdleTicks = 0;
        if (currentOpenChestData != null) currentOpenChestData.autoDepositEnabled = true;
    }

    private void renderHighlightBox(BlockPos pos, float partialTicks) {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null)
            return;

        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        AxisAlignedBB boundingBox = new AxisAlignedBB(pos).grow(0.002).offset(-viewerX, -viewerY, -viewerZ);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        RenderGlobal.renderFilledBox(boundingBox, 1.0F, 0.8F, 0.2F, 0.25F);
        RenderGlobal.drawSelectionBoundingBox(boundingBox, 1.0F, 0.8F, 0.2F, 1.0F);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private String getUniqueItemKey(ItemStack stack) {
        String key = stack.getItem().getRegistryName().toString();
        if (stack.hasTagCompound()) {
            key += stack.getTagCompound().toString();
        }
        return key;
    }
}

