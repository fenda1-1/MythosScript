// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/WarehouseEventHandler.java
package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.system.dungeon.ChestData;
import com.zszl.zszlScriptMod.system.dungeon.Warehouse;
import com.zszl.zszlScriptMod.utils.ModUtils;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
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
    private static BlockPos autoDepositCurrentTarget = null;
    private static int autoDepositOpenWaitTicks = 0;

    private static ChestData currentOpenChestData = null;
    private static boolean isStandardChestGui = false;
    private static int autoDepositCooldown = 0;
    private static final int AUTO_DEPOSIT_INTERVAL_TICKS = 2;

    // --- 滚动条和选择状态 ---
    private static int designatedItemScrollOffset = 0;
    private static int maxDesignatedItemScroll = 0;
    private static boolean isDraggingDesignatedScrollbar = false;

    // --- 按钮引用 ---
    private static GuiButton autoDepositToggleButton;

    private int scrollClickY;

    private WarehouseEventHandler() {
    }

    public static void startAutoDepositByHighlights() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (autoDepositRouteRunning) {
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
        autoDepositRouteQueue.addAll(sorted);
        autoDepositRouteRunning = true;
        autoDepositCurrentTarget = null;
        autoDepositOpenWaitTicks = 0;
        mc.player.sendMessage(new TextComponentString("§a[仓库] 已启动自动存入流程，目标箱子数: " + sorted.size()));
        INSTANCE.startNextAutoDepositTarget();
    }

    public static boolean isAutoDepositRouteRunning() {
        return autoDepositRouteRunning;
    }

    private void stopAutoDepositRoute(String reason) {
        if (mc.player != null && reason != null && !reason.isEmpty()) {
            mc.player.sendMessage(new TextComponentString(reason));
        }
        autoDepositRouteRunning = false;
        autoDepositCurrentTarget = null;
        autoDepositOpenWaitTicks = 0;
        autoDepositRouteQueue.clear();
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
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        if (event.phase != TickEvent.Phase.END || mc.player == null)
            return;

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
                && mc.player.openContainer instanceof ContainerChest) {
            if (autoDepositCooldown > 0) {
                autoDepositCooldown--;
            } else {
                executeAutoDeposit((ContainerChest) mc.player.openContainer);
                // 需求：固定每 2 tick 执行一次存入
                autoDepositCooldown = Math.max(0, AUTO_DEPOSIT_INTERVAL_TICKS - 1);
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
        currentOpenChestData = null;
        designatedItemScrollOffset = 0;

        if (!(event.getGui() instanceof GuiChest))
            return;

        GuiChest gui = (GuiChest) event.getGui();
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory chestInventory = container.getLowerChestInventory();
        String title = chestInventory.getDisplayName().getUnformattedText();

        if (title.equals("箱子") || title.equals("大型箱子")) {
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
                    updateDesignatedItems(currentOpenChestData, container);
                    if (oneClickDepositMode || autoDepositRouteRunning) {
                        currentOpenChestData.autoDepositEnabled = true;
                    }
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

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        if (!(event.getGui() instanceof GuiChest) || !isStandardChestGui || currentOpenChestData == null) {
            return;
        }

        GuiChest gui = (GuiChest) event.getGui();
        int guiLeft = (gui.width - 176) / 2;

        int panelWidth = 120;
        int panelX = guiLeft - panelWidth - 5;
        int panelY = 0;
        int panelHeight = gui.height;

        drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xC0000000);

        int itemHeight = 15;

        // --- 上半部分：指定存放物品 ---
        int designatedListY = panelY + 5;
        int designatedListHeight = panelHeight / 2 - 10;
        drawString(mc.fontRenderer, "§e指定存放物品:", panelX + 5, designatedListY, 0xFFFFFF);
        drawRect(panelX + 5, designatedListY + 15, panelX + panelWidth - 5, designatedListY + 15 + designatedListHeight,
                0x80000000);

        List<String> items = new ArrayList<>(currentOpenChestData.designatedItems);
        int visibleDesignatedItems = designatedListHeight / itemHeight;
        maxDesignatedItemScroll = Math.max(0, items.size() - visibleDesignatedItems);

        for (int i = 0; i < visibleDesignatedItems; i++) {
            int index = i + designatedItemScrollOffset;
            if (index >= items.size())
                break;
            drawString(mc.fontRenderer, "§f- " + items.get(index), panelX + 8, designatedListY + 17 + i * itemHeight,
                    0xFFFFFF);
        }
        if (maxDesignatedItemScroll > 0) {
            int scrollbarX = panelX + panelWidth - 9;
            int listTop = designatedListY + 15;
            drawRect(scrollbarX, listTop, scrollbarX + 4, listTop + designatedListHeight, 0xFF101010);
            int thumbHeight = Math.max(10,
                    (int) ((float) visibleDesignatedItems / items.size() * designatedListHeight));
            int thumbY = listTop + (int) ((float) designatedItemScrollOffset / maxDesignatedItemScroll
                    * (designatedListHeight - thumbHeight));
            drawRect(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        // --- 下半部分：自动存入功能 ---
        int sortPanelY = designatedListY + designatedListHeight + 20;
        int sortPanelHeight = panelHeight - sortPanelY - 5;
        drawString(mc.fontRenderer, "§e自动存入功能:", panelX + 5, sortPanelY, 0xFFFFFF);

        int sortListY = sortPanelY + 15;
        drawRect(panelX + 5, sortListY, panelX + panelWidth - 5, sortListY + sortPanelHeight - 20, 0x80000000);

        int funcY = sortListY + 45;
        initializeAndDrawButtons(panelX, funcY, panelWidth, event.getMouseX(), event.getMouseY(),
                event.getRenderPartialTicks());

        // 状态固定显示在面板最底部
        String status = currentOpenChestData.autoDepositEnabled ? "§a开" : "§c关";
        String route = autoDepositRouteRunning ? "§a运行中" : "§7未运行";
        drawString(mc.fontRenderer, "§f状态: 自动存入 " + status, panelX + 8, panelY + panelHeight - 22, 0xFFFFFF);
        drawString(mc.fontRenderer, "§f流程: " + route, panelX + 8, panelY + panelHeight - 10, 0xFFFFFF);

        // 提示改为悬浮显示：仅在鼠标停留“自动存入：开/关”按钮时出现
        if (autoDepositToggleButton != null && autoDepositToggleButton.isMouseOver()) {
            String tooltip = "打开此箱子后自动存入\n匹配“指定存放物品”的背包物品。";
            int iconX = Math.max(4, Math.min(gui.width - 16, autoDepositToggleButton.x
                    + autoDepositToggleButton.width + 4));
            int iconY = Math.max(4, Math.min(gui.height - 16,
                    autoDepositToggleButton.y + (autoDepositToggleButton.height - 11) / 2));
            // Publish the feature-specific explanation through the shared
            // anchor pass. The global renderer supplies the common delay.
            ModernTooltipSupport.registerInfoIcon(gui, iconX, iconY, 11, 11, tooltip);
            ModernUiRenderer.drawInfoIcon(iconX, iconY, ModernUiRenderer.SUBTLE_TEXT);
        }
        } finally {
            timer.stop();
        }
    }

    private void initializeAndDrawButtons(int panelX, int funcY, int panelWidth, int mouseX, int mouseY,
            float partialTicks) {
        int btnWidth = panelWidth - 10;

        autoDepositToggleButton = new GuiButton(9010, panelX + 5, funcY, btnWidth, 20,
                "自动存入: " + (currentOpenChestData.autoDepositEnabled ? "§a开" : "§c关"));

        autoDepositToggleButton.drawButton(mc, mouseX, mouseY, partialTicks);
    }

    @SubscribeEvent
    public void onMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) throws IOException {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        if (!(event.getGui() instanceof GuiChest) || !isStandardChestGui || currentOpenChestData == null)
            return;

        GuiChest gui = (GuiChest) event.getGui();
        int mouseX = Mouse.getEventX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;

        if (Mouse.getEventButtonState() && Mouse.getEventButton() == 0) {
            int guiLeft = (gui.width - 176) / 2;
            int panelWidth = 120;
            int panelX = guiLeft - panelWidth - 5;

            // 检查按钮点击
            if (autoDepositToggleButton != null && autoDepositToggleButton.mousePressed(mc, mouseX, mouseY)) {
                currentOpenChestData.autoDepositEnabled = !currentOpenChestData.autoDepositEnabled;
                WarehouseManager.saveWarehouses();
                event.setCanceled(true);
                return;
            }
        }
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onKeyboardInputPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        // 自动存入频率已固定为 2 tick，不处理频率输入
    }

    // !! 核心修复：添加完整的鼠标输入处理，包括滚轮和拖拽 !!
    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Post event) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
        try {
        if (!(event.getGui() instanceof GuiChest) || !isStandardChestGui)
            return;

        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            handleMouseWheel(event.getGui(), dWheel);
        }

        if (Mouse.getEventButton() == 0) {
            if (Mouse.getEventButtonState()) {
                // 检查是否点击了滚动条
                handleScrollbarClick(event.getGui());
            } else {
                // 释放鼠标
                isDraggingDesignatedScrollbar = false;
            }
        }

        if (isDraggingDesignatedScrollbar) {
            handleMouseDrag(event.getGui());
        }
        } finally {
            timer.stop();
        }
    }

    private void handleScrollbarClick(GuiScreen gui) {
        int mouseX = Mouse.getX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
        int guiLeft = (gui.width - 176) / 2;
        int panelWidth = 120;
        int panelX = guiLeft - panelWidth - 5;
        int panelY = 0;
        int panelHeight = gui.height;

        int designatedListY = panelY + 5;
        int designatedListHeight = panelHeight / 2 - 10;
        int designatedListTop = designatedListY + 15;
        int designatedScrollbarX = panelX + panelWidth - 9;
        if (mouseX >= designatedScrollbarX && mouseX < designatedScrollbarX + 4 && mouseY >= designatedListTop
                && mouseY < designatedListTop + designatedListHeight) {
            isDraggingDesignatedScrollbar = true;
            scrollClickY = mouseY;
        }

    }

    private void handleMouseWheel(GuiScreen gui, int dWheel) {
        int mouseX = Mouse.getX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;

        int guiLeft = (gui.width - 176) / 2;
        int panelWidth = 120;
        int panelX = guiLeft - panelWidth - 5;
        int panelY = 0;
        int panelHeight = gui.height;

        int designatedListY = panelY + 5;
        int designatedListHeight = panelHeight / 2 - 10;
        int designatedListTop = designatedListY + 15;

        if (mouseX >= panelX && mouseX < panelX + panelWidth) {
            if (mouseY >= designatedListTop && mouseY < designatedListTop + designatedListHeight) {
                if (dWheel > 0)
                    designatedItemScrollOffset = Math.max(0, designatedItemScrollOffset - 1);
                else
                    designatedItemScrollOffset = Math.min(maxDesignatedItemScroll, designatedItemScrollOffset + 1);
            }
        }
    }

    private void handleMouseDrag(GuiScreen gui) {
        int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
        int panelHeight = gui.height;

        if (isDraggingDesignatedScrollbar) {
            int designatedListHeight = panelHeight / 2 - 10;
            int listTop = 5 + 15;
            float percent = (float) (mouseY - listTop) / designatedListHeight;
            designatedItemScrollOffset = (int) (percent * (maxDesignatedItemScroll + 1));
            designatedItemScrollOffset = Math.max(0, Math.min(maxDesignatedItemScroll, designatedItemScrollOffset));
        }

        // 仅保留“指定存放物品”滚动拖拽
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
            int slotToClick = slotsToClick.get(0);
            mc.playerController.windowClick(container.windowId, slotToClick, 0, ClickType.QUICK_MOVE, mc.player);
            if (ModConfig.isDebugFlagEnabled(DebugModule.WAREHOUSE_ANALYSIS) && mc.player != null) {
                mc.player.sendMessage(new TextComponentString(String.format("§d[调试] §b执行存入操作，点击槽位: %d", slotToClick)));
            }
        }
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

