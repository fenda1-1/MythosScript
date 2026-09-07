// 文件路径: src/main/java/com/zszl/zszlScriptMod/gui/OverlayGuiHandler.java
package com.zszl.zszlScriptMod.gui;

import java.awt.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui; // 确保导入 Gui 类
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.components.ToggleGuiButton;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.InputTimelineManager;
import com.zszl.zszlScriptMod.handlers.DungeonWarehouseHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class OverlayGuiHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    // --- 快速兑换控件 ---

    // --- 副本仓库控件 ---
    private static ToggleGuiButton dungeonWarehouseShiftClickButton;
    private static ToggleGuiButton dungeonWarehouseCtrlClickButton;
    private static GuiTextField dungeonWarehouseIntervalField;
    private static GuiTextField dungeonWarehouseAmountField;
    private static int dungeonWarehouseAmount = 1;

    private static IInventory lastCheckedChestInventory = null;
    private static ItemStack ghostClipboardStack = ItemStack.EMPTY;
    private static List<String> hoverInfoLines = new ArrayList<>();
    private static int hoverInfoAnchorX = -1;
    private static int hoverInfoAnchorY = -1;
    private static int hoverInfoScreenWidth = -1;
    private static int hoverInfoScreenHeight = -1;
    private static GuiScreen hoverInfoScreen;

    public static void resetLastCheckedChest() {
        lastCheckedChestInventory = null;
        hoverInfoLines.clear();
        hoverInfoAnchorX = -1;
        hoverInfoAnchorY = -1;
        hoverInfoScreen = null;
    }

    public static boolean isTextInputFocused(GuiScreen screen) {
        if (!(screen instanceof GuiChest) || !DungeonWarehouseHandler.isDungeonWarehouseGui((GuiChest) screen)) {
            return false;
        }
        return dungeonWarehouseIntervalField != null && dungeonWarehouseIntervalField.isFocused()
                || dungeonWarehouseAmountField != null && dungeonWarehouseAmountField.isFocused();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        if (mc.currentScreen == null && !zszlScriptMod.isGuiVisible) {
            drawMasterStatusHud(false);
        }

        if (mc.currentScreen == null) {
            drawMouseCoordinateDebugHud();
        }

        if (mc.currentScreen == null) {
            ScaledResolution resolution = new ScaledResolution(mc);
            RunningStatusHud.render(mc.fontRenderer, resolution.getScaledWidth(),
                    resolution.getScaledHeight(), -1, -1);
            updateRunningStatusHudInteraction(resolution.getScaledWidth(), resolution.getScaledHeight());
        }
    }

    public static void renderMasterStatusHudPreview() {
        drawMasterStatusHud(true);
    }

    private static void drawMasterStatusHud(boolean editingPreview) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null) {
            GuiInventory.updateMasterStatusHudEditorBounds(null, null);
            return;
        }
        List<String> lines = buildMasterStatusHudLines(editingPreview);
        if (lines.isEmpty()) {
            GuiInventory.updateMasterStatusHudEditorBounds(null, null);
            return;
        }

        int baseX = Math.max(0, MovementFeatureManager.getMasterStatusHudX());
        int baseY = Math.max(0, MovementFeatureManager.getMasterStatusHudY());
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }
        int lineHeight = 10;
        int panelX = Math.max(0, baseX - 4);
        int panelY = Math.max(0, baseY - 4);
        int panelWidth = Math.max(120, maxWidth + 8);
        if (editingPreview) {
            panelWidth = Math.max(panelWidth,
                    mc.fontRenderer.getStringWidth(I18n.format("gui.other_features.hud.drag_hint")) + 10);
        }
        int panelHeight = lines.size() * lineHeight + 8;
        Rectangle hudBounds = new Rectangle(panelX, panelY, panelWidth, panelHeight);
        Rectangle exitBounds = null;

        if (editingPreview) {
            panelHeight += 34;
            ScaledResolution screen = new ScaledResolution(mc);
            panelX = Math.max(0, Math.min(panelX, screen.getScaledWidth() - panelWidth));
            panelY = Math.max(0, Math.min(panelY, screen.getScaledHeight() - panelHeight));
            baseX = panelX + 4;
            baseY = panelY + 4;
            MovementFeatureManager.setMasterStatusHudPositionTransient(baseX, baseY);
            hudBounds = new Rectangle(panelX, panelY, panelWidth, panelHeight);
            Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x7A0F1720);
            Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF63C7FF);
            Gui.drawRect(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF35536C);
            mc.fontRenderer.drawStringWithShadow(I18n.format("gui.other_features.hud.drag_hint"), panelX + 5, panelY + panelHeight - 30,
                    0xFFEAF6FF);
            int exitWidth = 44;
            int exitHeight = 14;
            int exitX = panelX + panelWidth - exitWidth - 4;
            int exitY = panelY + panelHeight - exitHeight - 4;
            exitBounds = new Rectangle(exitX, exitY, exitWidth, exitHeight);
            ScaledResolution scaledResolution = new ScaledResolution(mc);
            int hoverMouseX = Mouse.getX() * scaledResolution.getScaledWidth() / mc.displayWidth;
            int hoverMouseY = scaledResolution.getScaledHeight()
                    - Mouse.getY() * scaledResolution.getScaledHeight() / mc.displayHeight - 1;
            boolean hovered = exitBounds.contains(hoverMouseX, hoverMouseY);
            GuiTheme.drawButtonFrameSafe(exitX, exitY, exitWidth, exitHeight,
                    hovered ? GuiTheme.UiState.HOVER : GuiTheme.UiState.NORMAL);
            mc.fontRenderer.drawStringWithShadow("退出编辑", exitX + 6, exitY + 3, 0xFFFFFFFF);
        }

        int drawY = baseY;
        for (String line : lines) {
            mc.fontRenderer.drawStringWithShadow(line, baseX, drawY, 0xFFFFFF);
            drawY += lineHeight;
        }

        GuiInventory.updateMasterStatusHudEditorBounds(hudBounds, exitBounds);
    }

    private static List<String> buildMasterStatusHudLines(boolean editingPreview) {
        List<String> lines = new ArrayList<>();
        lines.addAll(editingPreview ? SpeedHandler.getStatusLines(true) : SpeedHandler.getStatusLines());
        lines.addAll(editingPreview ? MovementFeatureManager.getStatusLines(true) : MovementFeatureManager.getStatusLines());
        lines.addAll(editingPreview ? BlockFeatureManager.getStatusLines(true) : BlockFeatureManager.getStatusLines());
        lines.addAll(editingPreview ? WorldFeatureManager.getStatusLines(true) : WorldFeatureManager.getStatusLines());
        lines.addAll(editingPreview ? ItemFeatureManager.getStatusLines(true) : ItemFeatureManager.getStatusLines());
        lines.addAll(editingPreview ? MiscFeatureManager.getStatusLines(true) : MiscFeatureManager.getStatusLines());
        if (!editingPreview || !lines.isEmpty()) {
            return lines;
        }
        lines.add("§a[总状态HUD] §f位置预览");
        lines.add("§7当前没有可显示的状态行");
        lines.add("§7拖动后将保存新的 HUD 位置");
        return lines;
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (gui == null) {
            return;
        }

        if (ModConfig.showHoverInfo && !zszlScriptMod.isGuiVisible) {
            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();
            renderHoverInfoAnchor(gui, mouseX, mouseY);
        }

        if (event.getGui() instanceof GuiChest) {
            GuiChest chestGui = (GuiChest) event.getGui();

            int guiYSize = 166;
            try {
                if (chestGui.inventorySlots instanceof ContainerChest) {
                    ContainerChest containerChest = (ContainerChest) chestGui.inventorySlots;
                    IInventory lower = containerChest.getLowerChestInventory();
                    int rows = Math.max(1, lower.getSizeInventory() / 9);
                    guiYSize = 114 + rows * 18;
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("Failed to calc chest guiYSize, using default", e);
            }
            int guiLeft = (chestGui.width - 176) / 2;
            int guiTop = (chestGui.height - guiYSize) / 2;

            if (DungeonWarehouseHandler.isDungeonWarehouseGui(chestGui)) {
                int panelWidth = 130;
                int panelX = guiLeft - panelWidth - 5;
                int panelY = guiTop;
                int panelHeight = 140;

                // !! 修复：使用 Gui.drawRect 静态方法
                Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xC0000000);
                mc.fontRenderer.drawStringWithShadow(I18n.format("gui.dungeon_warehouse.title"), panelX + 7,
                        panelY + 8, 0xFFFFFF);

                int currentY = panelY + 25;
                int controlWidth = panelWidth - 14;
                int controlX = panelX + 7;

                if (dungeonWarehouseShiftClickButton == null) {
                    dungeonWarehouseShiftClickButton = new ToggleGuiButton(9005, 0, 0, 0, 0, "",
                            DungeonWarehouseHandler.settings.shiftClickEnabled);
                }
                dungeonWarehouseShiftClickButton.x = controlX;
                dungeonWarehouseShiftClickButton.y = currentY;
                dungeonWarehouseShiftClickButton.width = controlWidth;
                dungeonWarehouseShiftClickButton.height = 20;
                dungeonWarehouseShiftClickButton.displayString = I18n.format("gui.dungeon_warehouse.shift_click")
                        + ": "
                        + (DungeonWarehouseHandler.settings.shiftClickEnabled ? I18n.format("gui.common.enabled")
                                : I18n.format("gui.common.disabled"));
                dungeonWarehouseShiftClickButton.setEnabledState(DungeonWarehouseHandler.settings.shiftClickEnabled);
                dungeonWarehouseShiftClickButton.drawButton(mc, event.getMouseX(), event.getMouseY(),
                        event.getRenderPartialTicks());
                currentY += 22;

                if (dungeonWarehouseCtrlClickButton == null) {
                    dungeonWarehouseCtrlClickButton = new ToggleGuiButton(9006, 0, 0, 0, 0, "",
                            DungeonWarehouseHandler.settings.ctrlClickEnabled);
                }
                dungeonWarehouseCtrlClickButton.x = controlX;
                dungeonWarehouseCtrlClickButton.y = currentY;
                dungeonWarehouseCtrlClickButton.width = controlWidth;
                dungeonWarehouseCtrlClickButton.height = 20;
                dungeonWarehouseCtrlClickButton.displayString = I18n.format("gui.dungeon_warehouse.ctrl_click")
                        + ": "
                        + (DungeonWarehouseHandler.settings.ctrlClickEnabled ? I18n.format("gui.common.enabled")
                                : I18n.format("gui.common.disabled"));
                dungeonWarehouseCtrlClickButton.setEnabledState(DungeonWarehouseHandler.settings.ctrlClickEnabled);
                dungeonWarehouseCtrlClickButton.drawButton(mc, event.getMouseX(), event.getMouseY(),
                        event.getRenderPartialTicks());
                currentY += 22;

                mc.fontRenderer.drawStringWithShadow(I18n.format("gui.dungeon_warehouse.click_interval"), controlX,
                        currentY + 6, 0xFFFFFF);
                if (dungeonWarehouseIntervalField == null) {
                    dungeonWarehouseIntervalField = new GuiTextField(9007, mc.fontRenderer, 0, 0, 50, 20);
                    dungeonWarehouseIntervalField
                            .setText(String.valueOf(DungeonWarehouseHandler.settings.clickIntervalMs));
                    dungeonWarehouseIntervalField.setEnableBackgroundDrawing(false);
                    dungeonWarehouseIntervalField.setMaxStringLength(6);
                }
                dungeonWarehouseIntervalField.x = controlX + controlWidth - 50;
                dungeonWarehouseIntervalField.y = currentY;
                GuiTheme.drawInputFrame(dungeonWarehouseIntervalField.x - 1, dungeonWarehouseIntervalField.y - 1,
                        dungeonWarehouseIntervalField.width + 2, dungeonWarehouseIntervalField.height + 2,
                        dungeonWarehouseIntervalField.isFocused(), true);
                ModernUiRenderer.reflowTextField(dungeonWarehouseIntervalField);
                dungeonWarehouseIntervalField.drawTextBox();
                currentY += 22;

                mc.fontRenderer.drawStringWithShadow(I18n.format("gui.dungeon_warehouse.ctrl_click_number"), controlX,
                        currentY + 6, 0xFFFFFF);
                if (dungeonWarehouseAmountField == null) {
                    dungeonWarehouseAmountField = new GuiTextField(9008, mc.fontRenderer, 0, 0, 50, 20);
                    dungeonWarehouseAmountField.setText(String.valueOf(dungeonWarehouseAmount));
                    dungeonWarehouseAmountField.setEnableBackgroundDrawing(false);
                    dungeonWarehouseAmountField.setMaxStringLength(2);
                }
                dungeonWarehouseAmountField.x = controlX + controlWidth - 50;
                dungeonWarehouseAmountField.y = currentY;
                GuiTheme.drawInputFrame(dungeonWarehouseAmountField.x - 1, dungeonWarehouseAmountField.y - 1,
                        dungeonWarehouseAmountField.width + 2, dungeonWarehouseAmountField.height + 2,
                        dungeonWarehouseAmountField.isFocused(), true);
                ModernUiRenderer.reflowTextField(dungeonWarehouseAmountField);
                dungeonWarehouseAmountField.drawTextBox();
            }
        }

        if (ModConfig.isDebugFlagEnabled(DebugModule.CHEST_ANALYSIS) && event.getGui() instanceof GuiChest) {
            GuiChest chestGui = (GuiChest) event.getGui();
            if (chestGui.inventorySlots instanceof ContainerChest) {
                ContainerChest container = (ContainerChest) chestGui.inventorySlots;
                IInventory currentChestInventory = container.getLowerChestInventory();

                if (currentChestInventory != lastCheckedChestInventory) {
                    performChestAnalysis(chestGui);
                    lastCheckedChestInventory = currentChestInventory;
                }
            }
        }

        drawMouseCoordinateDebugHud();

        // Draw after every screen-specific overlay so the runtime HUD stays
        // visible above inventories, dialogs, and text input controls.
        if (!(gui instanceof GuiModernMainScreen) && !DetachedSwingWindowManager.isDetachedScreen(gui)) {
            RunningStatusHud.render(mc.fontRenderer, gui.width, gui.height, event.getMouseX(), event.getMouseY());
        }
        if (!DetachedSwingWindowManager.isDetachedScreen(gui)) {
            updateRunningStatusHudInteraction(gui.width, gui.height);
        }
    }

    private void drawMouseCoordinateDebugHud() {
        if (!ModConfig.showMouseCoordinates || mc == null || mc.fontRenderer == null) {
            return;
        }
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int rawMouseX = Mouse.getX();
        int rawMouseY = mc.displayHeight - Mouse.getY() - 1;
        int scaledMouseX = rawMouseX * scaledResolution.getScaledWidth() / mc.displayWidth;
        int scaledMouseY = rawMouseY * scaledResolution.getScaledHeight() / mc.displayHeight;
        String coordText = I18n.format("gui.overlay.debug.coords",
                rawMouseX, rawMouseY, scaledMouseX, scaledMouseY);
        int screenWidth = scaledResolution.getScaledWidth();
        int textWidth = mc.fontRenderer.getStringWidth(coordText);
        mc.fontRenderer.drawStringWithShadow(coordText, (screenWidth - textWidth) / 2, 5, 0xFFFFFF);
    }

    private void performChestAnalysis(GuiChest gui) {
        if (mc.player == null)
            return;

        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory inventory = container.getLowerChestInventory();
        String title = inventory.getDisplayName().getUnformattedText();

        mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "--- Chest Check Debug ---"));
        mc.player.sendMessage(
                new TextComponentString(
                        TextFormatting.YELLOW + "Detected chest title: " + TextFormatting.WHITE + title));


        String expectedNameSlot0 = I18n.format("gui.overlay.coin_coupon_60");
        String expectedNameSlot1 = I18n.format("gui.overlay.coin_coupon_360");

        ItemStack stackSlot0 = container.getSlot(0).getStack();
        ItemStack stackSlot1 = container.getSlot(1).getStack();
        String actualNameSlot0 = stackSlot0.isEmpty() ? I18n.format("gui.overlay.empty_slot")
                : stackSlot0.getDisplayName();
        String actualNameSlot1 = stackSlot1.isEmpty() ? I18n.format("gui.overlay.empty_slot")
                : stackSlot1.getDisplayName();

        mc.player.sendMessage(new TextComponentString(TextFormatting.GRAY + "--- Slot 0 Check ---"));
        mc.player.sendMessage(
                new TextComponentString(
                        TextFormatting.AQUA + "  Expected name contains: " + TextFormatting.WHITE + expectedNameSlot0));
        mc.player.sendMessage(
                new TextComponentString(
                        TextFormatting.AQUA + "  Actual item name: " + TextFormatting.WHITE + actualNameSlot0));
        boolean match0 = !stackSlot0.isEmpty() && actualNameSlot0.contains(expectedNameSlot0);
        mc.player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + "  Match: "
                        + (match0 ? TextFormatting.GREEN + "YES" : TextFormatting.RED + "NO")));

        mc.player.sendMessage(new TextComponentString(TextFormatting.GRAY + "--- Slot 1 Check ---"));
        mc.player.sendMessage(
                new TextComponentString(
                        TextFormatting.AQUA + "  Expected name contains: " + TextFormatting.WHITE + expectedNameSlot1));
        mc.player.sendMessage(
                new TextComponentString(
                        TextFormatting.AQUA + "  Actual item name: " + TextFormatting.WHITE + actualNameSlot1));
        boolean match1 = !stackSlot1.isEmpty() && actualNameSlot1.contains(expectedNameSlot1);
        mc.player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + "  Match: "
                        + (match1 ? TextFormatting.GREEN + "YES" : TextFormatting.RED + "NO")));

        mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "--------------------"));
    }

    @SubscribeEvent
    public void onMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen currentGui = event.getGui();
        if (currentGui == null) {
            return;
        }

        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()
                && !(currentGui instanceof GuiModernMainScreen)
                && !DetachedSwingWindowManager.isDetachedScreen(currentGui)) {
            int mouseX = Mouse.getEventX() * currentGui.width / mc.displayWidth;
            int mouseY = currentGui.height - Mouse.getEventY() * currentGui.height / mc.displayHeight - 1;
            if (RunningStatusHud.mouseClicked(mouseX, mouseY, currentGui.width, currentGui.height)) {
                event.setCanceled(true);
                return;
            }
        }

        if (Mouse.getEventButtonState() && Mouse.getEventButton() >= 0 && Mouse.getEventButton() <= 2) {
            InputTimelineManager.recordMouseClick(Mouse.getEventButton());
        }

        if (ModConfig.enableGhostItemCopy && currentGui instanceof GuiContainer
                && Mouse.getEventButton() == 2 && Mouse.getEventButtonState()) {
            handleGuiGhostCopy((GuiContainer) currentGui);
            event.setCanceled(true);
            return;
        }

        if (currentGui instanceof GuiChest && Mouse.getEventButtonState()) {
            GuiChest gui = (GuiChest) currentGui;
            int mouseX = Mouse.getEventX() * gui.width / mc.displayWidth;
            int mouseY = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;



            if (DungeonWarehouseHandler.isDungeonWarehouseGui(gui)) {
                if (dungeonWarehouseShiftClickButton != null
                        && dungeonWarehouseShiftClickButton.mousePressed(mc, mouseX, mouseY)) {
                    DungeonWarehouseHandler.settings.shiftClickEnabled = !DungeonWarehouseHandler.settings.shiftClickEnabled;
                    DungeonWarehouseHandler.saveConfig();
                    event.setCanceled(true);
                    return;
                }
                if (dungeonWarehouseCtrlClickButton != null
                        && dungeonWarehouseCtrlClickButton.mousePressed(mc, mouseX, mouseY)) {
                    DungeonWarehouseHandler.settings.ctrlClickEnabled = !DungeonWarehouseHandler.settings.ctrlClickEnabled;
                    DungeonWarehouseHandler.saveConfig();
                    event.setCanceled(true);
                    return;
                }
                if (dungeonWarehouseIntervalField != null)
                    dungeonWarehouseIntervalField.mouseClicked(mouseX, mouseY, Mouse.getEventButton());
                if (dungeonWarehouseAmountField != null)
                    dungeonWarehouseAmountField.mouseClicked(mouseX, mouseY, Mouse.getEventButton());

                Slot slot = gui.getSlotUnderMouse();
                if (slot != null && slot.getHasStack()) {
                    boolean isShift = GuiScreen.isShiftKeyDown();
                    boolean isCtrl = GuiScreen.isCtrlKeyDown();

                    boolean shouldHandle = (isCtrl && DungeonWarehouseHandler.settings.ctrlClickEnabled) ||
                            (isShift && DungeonWarehouseHandler.settings.shiftClickEnabled
                                    && slot.inventory == mc.player.inventory);

                    if (shouldHandle) {
                        DungeonWarehouseHandler.handleClick(slot, isShift, isCtrl, dungeonWarehouseAmount);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    /**
     * Draws one contextual information label for the currently inspected
     * container slot or button. The text is cached while the pointer moves to
     * the label itself, so no legacy full-row hover popup is needed.
     */
    private static void renderHoverInfoAnchor(GuiScreen gui, int mouseX, int mouseY) {
        if (gui == null) {
            return;
        }
        List<String> currentLines = new ArrayList<>();
        int anchorX = -1;
        int anchorY = -1;

        if (gui instanceof GuiContainer) {
            GuiContainer container = (GuiContainer) gui;
            Slot slot = container.getSlotUnderMouse();
            if (slot != null && slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                currentLines.add(stack.getDisplayName());
                Object registryName = Item.REGISTRY.getNameForObject(stack.getItem());
                currentLines.add(TextFormatting.DARK_GRAY
                        + (registryName == null ? "unknown:item" : registryName.toString()));
                currentLines.add("---");
                currentLines.add(TextFormatting.AQUA + I18n.format("gui.overlay.container_slot_id")
                        + TextFormatting.WHITE + slot.slotNumber);
                currentLines.add(TextFormatting.YELLOW + I18n.format("gui.overlay.inventory_slot_id")
                        + TextFormatting.WHITE + slot.getSlotIndex());
                if (stack.hasTagCompound()) {
                    currentLines.add(TextFormatting.GOLD + "NBT: " + TextFormatting.WHITE
                            + String.valueOf(stack.getTagCompound()));
                }
                int left = container.getGuiLeft();
                int top = container.getGuiTop();
                anchorX = left + slot.xPos + 16;
                anchorY = top + slot.yPos + 2;
            }
        }

        if (currentLines.isEmpty()) {
            GuiButton hoveredButton = findHoveredButton(gui);
            if (hoveredButton != null) {
                currentLines.add(I18n.format("gui.overlay.button_label", hoveredButton.displayString));
                currentLines.add("---");
                currentLines.add(I18n.format("gui.overlay.button_id", hoveredButton.id));
                anchorX = hoveredButton.x + hoveredButton.width + 4;
                anchorY = hoveredButton.y + (hoveredButton.height - 11) / 2;
            }
        }

        if (!currentLines.isEmpty()) {
            hoverInfoLines = new ArrayList<>(currentLines);
            hoverInfoAnchorX = anchorX;
            hoverInfoAnchorY = anchorY;
            hoverInfoScreenWidth = gui.width;
            hoverInfoScreenHeight = gui.height;
            hoverInfoScreen = gui;
        } else if (hoverInfoScreen != gui || hoverInfoScreenWidth != gui.width
                || hoverInfoScreenHeight != gui.height) {
            hoverInfoLines.clear();
            hoverInfoAnchorX = -1;
            hoverInfoAnchorY = -1;
            hoverInfoScreen = gui;
        }

        if (hoverInfoLines.isEmpty() || hoverInfoAnchorX < 0 || hoverInfoAnchorY < 0) {
            return;
        }
        int iconX = Math.max(4, Math.min(gui.width - 16, hoverInfoAnchorX));
        int iconY = Math.max(4, Math.min(gui.height - 16, hoverInfoAnchorY));
        boolean hovered = mouseX >= iconX && mouseX < iconX + 11 && mouseY >= iconY && mouseY < iconY + 11;
        String tooltip = String.join("\n", hoverInfoLines);
        // Register the anchor for the shared renderer. This keeps container
        // help consistent with modern screens and gives it the same hover
        // delay instead of painting a popup immediately in this event pass.
        ModernTooltipSupport.registerInfoIcon(gui, iconX, iconY, 11, 11, tooltip);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(iconX - 2, iconY - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
        }
        ModernUiRenderer.drawInfoIcon(iconX, iconY, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
    }

    @SubscribeEvent
    public void onKeyboardInputPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() != Keyboard.KEY_NONE) {
            InputTimelineManager.recordKeyPress(Keyboard.getEventKey());
        }
        if (mc.currentScreen instanceof GuiChest) {
            GuiChest gui = (GuiChest) mc.currentScreen;
            char typedChar = Keyboard.getEventCharacter();
            int keyCode = Keyboard.getEventKey();

            if (DungeonWarehouseHandler.isDungeonWarehouseGui(gui)) {
                if (dungeonWarehouseIntervalField != null && dungeonWarehouseIntervalField.isFocused()) {
                    if (dungeonWarehouseIntervalField.textboxKeyTyped(typedChar, keyCode)) {
                        try {
                            int interval = Integer.parseInt(dungeonWarehouseIntervalField.getText());
                            DungeonWarehouseHandler.settings.clickIntervalMs = Math.max(100, interval);
                            DungeonWarehouseHandler.saveConfig();
                        } catch (NumberFormatException ignored) {
                        }
                        event.setCanceled(true);
                    }
                }
                if (dungeonWarehouseAmountField != null && dungeonWarehouseAmountField.isFocused()) {
                    if (dungeonWarehouseAmountField.textboxKeyTyped(typedChar, keyCode)) {
                        try {
                            dungeonWarehouseAmount = Math.max(1,
                                    Math.min(64, Integer.parseInt(dungeonWarehouseAmountField.getText())));
                            dungeonWarehouseAmountField.setText(String.valueOf(dungeonWarehouseAmount));
                        } catch (NumberFormatException ignored) {
                        }
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    private static GuiButton findHoveredButton(GuiScreen gui) {
        try {
            for (Class<?> clazz = gui.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(gui);
                    if (fieldValue == null)
                        continue;

                    if (fieldValue instanceof GuiButton) {
                        GuiButton button = (GuiButton) fieldValue;
                        if (button.visible && button.isMouseOver())
                            return button;
                    } else if (fieldValue instanceof List) {
                        for (Object element : (List<?>) fieldValue) {
                            if (element instanceof GuiButton) {
                                GuiButton button = (GuiButton) element;
                                if (button.visible && button.isMouseOver())
                                    return button;
                            }
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            // 静默处理反射异常
        }
        return null;
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (mc.currentScreen == null && Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
            ScaledResolution resolution = new ScaledResolution(mc);
            RunningStatusHud.mouseClicked(Mouse.getEventX() * resolution.getScaledWidth() / mc.displayWidth,
                    resolution.getScaledHeight() - Mouse.getEventY() * resolution.getScaledHeight() / mc.displayHeight - 1,
                    resolution.getScaledWidth(), resolution.getScaledHeight());
        }
        if (mc.currentScreen == null && Mouse.getEventButtonState() && Mouse.getEventButton() >= 0 && Mouse.getEventButton() <= 2) {
            InputTimelineManager.recordMouseClick(Mouse.getEventButton());
        }
        if (ModConfig.enableGhostItemCopy && mc.currentScreen == null
                && Mouse.getEventButton() == 2 && Mouse.getEventButtonState()) {
            handleWorldGhostCopy();
        }
    }

    private void updateRunningStatusHudInteraction(int screenWidth, int screenHeight) {
        if (!RunningStatusHud.isInteractionActive() || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            RunningStatusHud.mouseReleased();
            return;
        }
        int mouseX = Mouse.getX() * screenWidth / mc.displayWidth;
        int mouseY = screenHeight - Mouse.getY() * screenHeight / mc.displayHeight - 1;
        RunningStatusHud.mouseDragged(mouseX, mouseY, screenWidth, screenHeight);
    }

    private void handleGuiGhostCopy(GuiContainer gui) {
        if (mc.player == null)
            return;

        Slot slot = gui.getSlotUnderMouse();
        if (slot == null) {
            sendGhostCopyMessage("未指向有效槽位");
            return;
        }

        boolean isPlayerSlot = slot.inventory == mc.player.inventory;
        boolean forcePaste = GuiScreen.isCtrlKeyDown();

        if (!forcePaste && slot.getHasStack()) {
            ghostClipboardStack = slot.getStack().copy();
            sendGhostCopyMessage(
                    "已复制: " + ghostClipboardStack.getDisplayName() + " x" + ghostClipboardStack.getCount());
            return;
        }

        if (!isPlayerSlot) {
            sendGhostCopyMessage("仅可粘贴到玩家背包槽位");
            return;
        }

        if (ghostClipboardStack.isEmpty()) {
            sendGhostCopyMessage("幽灵剪贴板为空，请先中键复制物品");
            return;
        }

        ItemStack copy = ghostClipboardStack.copy();
        int stackLimit = Math.min(copy.getMaxStackSize(), slot.getItemStackLimit(copy));
        if (stackLimit <= 0 || !slot.isItemValid(copy)) {
            sendGhostCopyMessage("该槽位不接受此物品");
            return;
        }
        copy.setCount(Math.min(copy.getCount(), stackLimit));

        slot.putStack(copy);
        slot.onSlotChanged();
        mc.player.inventory.markDirty();
        sendGhostCopyMessage("已粘贴到槽位 " + slot.slotNumber + "（本地幽灵物品）");
    }

    private void handleWorldGhostCopy() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit == RayTraceResult.Type.MISS) {
            sendGhostCopyMessage("未命中可复制目标");
            return;
        }

        ItemStack picked = ItemStack.EMPTY;
        if (hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (pos != null && !mc.world.isAirBlock(pos)) {
                IBlockState state = mc.world.getBlockState(pos);
                picked = state.getBlock().getPickBlock(state, hit, mc.world, pos, mc.player);
            }
        } else if (hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            picked = hit.entityHit.getPickedResult(hit);
        }

        if (picked == null || picked.isEmpty()) {
            sendGhostCopyMessage("该目标不可复制");
            return;
        }

        ghostClipboardStack = picked.copy();
        int currentSlot = mc.player.inventory.currentItem;
        mc.player.inventory.setInventorySlotContents(currentSlot, ghostClipboardStack.copy());
        mc.player.inventory.markDirty();

        sendGhostCopyMessage("已复制到当前快捷栏（本地幽灵物品）: " + ghostClipboardStack.getDisplayName());
    }

    private void sendGhostCopyMessage(String text) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§d[复制幽灵] §f" + text));
        }
    }
}
