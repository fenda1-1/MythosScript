package com.zszl.zszlScriptMod.gui.modern.world;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;

import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.inventory.InventoryViewerManager;

import net.minecraft.item.ItemStack;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Paints the equipment workbench while the tab keeps interaction state. */
final class PlayerEquipmentWorkbenchRenderer {

    private static final int[] EQUIPMENT_SLOTS = { 45, 46, 47, 48, 49 };
    private static final String[] EQUIPMENT_LABELS = { "gui.modern.equipr.u001", "gui.modern.equipr.u002", "gui.modern.equipr.u003", "gui.modern.equipr.u004", "gui.modern.equipr.u005" };
    private static final int GRID_GAP = 4;

    private final PlayerEquipmentWorkbenchTab owner;

    PlayerEquipmentWorkbenchRenderer(PlayerEquipmentWorkbenchTab owner) {
        this.owner = owner;
    }

    void draw(int mouseX, int mouseY) {
        ModernMainLayout.Rect panel = owner.panelBounds;
        boolean compact = panel.width < 500 || panel.height < 190;
        drawHeader(mouseX, mouseY, compact);
        if (compact) {
            int viewportY = panel.y + 90;
            int viewportHeight = Math.max(1, panel.bottom() - viewportY - 8);
            int contentHeight = owner.viewMode == PlayerEquipmentWorkbenchTab.ViewMode.EQUIPMENT ? 340 : 300;
            owner.compactViewport = new ModernMainLayout.Rect(panel.x + 10, viewportY,
                    Math.max(1, panel.width - 20), viewportHeight);
            owner.compactMaxScroll = Math.max(0, contentHeight - viewportHeight);
            owner.compactScroll = Math.max(0, Math.min(owner.compactScroll, owner.compactMaxScroll));
            ModernMainLayout.Rect body = new ModernMainLayout.Rect(owner.compactViewport.x,
                    viewportY - owner.compactScroll, ModernHoverScrollbar.contentWidth(owner.compactViewport.width), contentHeight);
            ModernUiRenderer.beginClip(owner.compactViewport);
            if (!InventoryViewerManager.hasCopiedInventory()) {
                drawEmptyState(body);
            } else if (owner.viewMode == PlayerEquipmentWorkbenchTab.ViewMode.EQUIPMENT) {
                drawEquipmentView(body, mouseX, mouseY);
            } else {
                drawInventoryView(body, mouseX, mouseY);
            }
            ModernUiRenderer.endClip();
            drawCompactScrollbar(mouseX, mouseY);
            return;
        }
        owner.compactViewport = null;
        owner.compactScrollbarBounds = null;
        owner.compactScrollbarThumbBounds = null;
        owner.compactMaxScroll = 0;
        owner.draggingCompactScrollbar = false;
        ModernMainLayout.Rect body = new ModernMainLayout.Rect(panel.x + 10, panel.y + 98,
                Math.max(1, panel.width - 20), Math.max(1, panel.height - 110));
        ModernUiRenderer.beginClip(body);
        if (!InventoryViewerManager.hasCopiedInventory()) {
            drawEmptyState(body);
        } else if (owner.viewMode == PlayerEquipmentWorkbenchTab.ViewMode.EQUIPMENT) {
            drawEquipmentView(body, mouseX, mouseY);
        } else {
            drawInventoryView(body, mouseX, mouseY);
        }
        ModernUiRenderer.endClip();
    }

    private void drawHeader(int mouseX, int mouseY, boolean compact) {
        ModernMainLayout.Rect panel = owner.panelBounds;
        int x = panel.x + 14;
        int width = Math.max(1, panel.width - 28);
        ModernUiRenderer.drawText(owner.fontRenderer, "gui.modern.equipr.u006", x, panel.y + (compact ? 7 : 11), ModernUiRenderer.TEXT,
                Math.max(70, width - 20));
        String target = InventoryViewerManager.hasCopiedInventory()
                ? tr("gui.modern.equipr.fmt.snapshot", InventoryViewerManager.getCopiedTargetName()) : "gui.modern.equipr.u007";
        ModernUiRenderer.drawText(owner.fontRenderer, target, x, panel.y + (compact ? 22 : 28),
                InventoryViewerManager.hasCopiedInventory() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
                Math.max(80, width - 20));

        int buttonY = panel.y + (compact ? 39 : 45);
        int clearWidth = 78;
        int copyWidth = Math.min(118, Math.max(96, width - clearWidth - 8));
        owner.copyBounds = new ModernMainLayout.Rect(x, buttonY, copyWidth, 22);
        owner.clearBounds = new ModernMainLayout.Rect(owner.copyBounds.right() + 8, buttonY, clearWidth, 22);
        drawButton(owner.copyBounds, "gui.modern.equipr.u008", true, mouseX, mouseY);
        drawButton(owner.clearBounds, "gui.modern.equipr.u009", false, mouseX, mouseY);

        int tabY = panel.y + (compact ? 66 : 73);
        int tabWidth = Math.min(104, Math.max(76, width / 4));
        owner.equipmentTabBounds = new ModernMainLayout.Rect(x, tabY, tabWidth, 20);
        owner.inventoryTabBounds = new ModernMainLayout.Rect(x + tabWidth + 5, tabY, tabWidth, 20);
        drawTab(owner.equipmentTabBounds, "gui.modern.equipr.u010", owner.viewMode == PlayerEquipmentWorkbenchTab.ViewMode.EQUIPMENT,
                mouseX, mouseY);
        drawTab(owner.inventoryTabBounds, "gui.modern.equipr.u011", owner.viewMode == PlayerEquipmentWorkbenchTab.ViewMode.INVENTORY,
                mouseX, mouseY);
        if (!compact) {
            ModernUiRenderer.drawText(owner.fontRenderer, "gui.modern.equipr.u012",
                    owner.inventoryTabBounds.right() + 10, tabY + 6, ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, panel.right() - owner.inventoryTabBounds.right() - 24));
        }

        if (!compact && !owner.statusMessage.isEmpty() && System.currentTimeMillis() < owner.statusMessageUntil) {
            ModernUiRenderer.drawText(owner.fontRenderer, owner.statusMessage, x, panel.bottom() - 17,
                    ModernUiRenderer.SUCCESS, Math.max(20, width));
        }
    }

    private void drawCompactScrollbar(int mouseX, int mouseY) {
        if (owner.compactViewport == null || owner.compactMaxScroll <= 0) {
            owner.compactScrollbar.idle();
            owner.compactScrollbarBounds = null;
            owner.compactScrollbarThumbBounds = null;
            return;
        }
        ModernMainLayout.Rect viewport = owner.compactViewport;
        owner.compactScrollbar.draw(viewport, owner.compactScroll, owner.compactMaxScroll, viewport.height,
                viewport.height + owner.compactMaxScroll, mouseX, mouseY, value -> owner.compactScroll = value);
        owner.compactScrollbarBounds = new ModernMainLayout.Rect(viewport.right() - 14, viewport.y, 14, viewport.height);
        owner.compactScrollbarThumbBounds = owner.compactScrollbarBounds;
    }

    private void drawEquipmentView(ModernMainLayout.Rect body, int mouseX, int mouseY) {
        boolean sideBySide = body.width >= 440;
        int leftWidth = sideBySide ? Math.min(196, Math.max(164, body.width / 3)) : body.width;
        ModernMainLayout.Rect equipment = new ModernMainLayout.Rect(body.x, body.y, leftWidth,
                Math.min(228, body.height));
        drawSectionTitle("gui.modern.equipr.u010", equipment);
        int slotSize = Math.min(34, Math.max(24, (equipment.width - 32) / 3));
        int startX = equipment.x + 12;
        int startY = equipment.y + 28;
        for (int i = 0; i < EQUIPMENT_SLOTS.length; i++) {
            int column = i < 4 ? 0 : 1;
            int x = startX + column * (slotSize + 10);
            int y = i < 4 ? startY + i * (slotSize + 7) : startY + 3 * (slotSize + 7);
            drawSlot(EQUIPMENT_SLOTS[i], x, y, slotSize, EQUIPMENT_LABELS[i], mouseX, mouseY);
        }
        ModernUiRenderer.drawText(owner.fontRenderer, "gui.modern.equipr.u013", equipment.x + 12,
                equipment.bottom() - 27, ModernUiRenderer.MUTED_TEXT, Math.max(20, equipment.width - 24));
        owner.detailBounds = sideBySide
                ? new ModernMainLayout.Rect(equipment.right() + 10, body.y,
                        Math.max(1, body.right() - equipment.right() - 10), body.height)
                : new ModernMainLayout.Rect(body.x, equipment.bottom() + 8, body.width,
                        Math.max(1, body.bottom() - equipment.bottom() - 8));
        drawDetail(owner.detailBounds, mouseX, mouseY);
    }

    private void drawInventoryView(ModernMainLayout.Rect body, int mouseX, int mouseY) {
        int slotSize = PlayerEquipmentRenderSupport.gridSlotSize(body.width);
        int gridWidth = slotSize * 9 + GRID_GAP * 8;
        boolean sideBySide = body.width >= gridWidth + 190;
        int gridHeight = slotSize * 4 + GRID_GAP * 3 + 28;
        ModernMainLayout.Rect grid = new ModernMainLayout.Rect(body.x, body.y,
                Math.min(gridWidth + 20, body.width), Math.min(gridHeight, body.height));
        drawSectionTitle("gui.modern.equipr.u011", grid);
        int startX = grid.x + 10;
        int startY = grid.y + 26;
        for (int slot = 0; slot < 36; slot++) {
            drawSlot(slot, startX + (slot % 9) * (slotSize + GRID_GAP),
                    startY + (slot / 9) * (slotSize + GRID_GAP), slotSize, tr("gui.modern.equipr.fmt.slot", String.valueOf(slot + 1)), mouseX, mouseY);
        }
        owner.detailBounds = sideBySide
                ? new ModernMainLayout.Rect(grid.right() + 10, body.y, Math.max(1, body.right() - grid.right() - 10),
                        body.height)
                : new ModernMainLayout.Rect(body.x, grid.bottom() + 8, body.width,
                        Math.max(1, body.bottom() - grid.bottom() - 8));
        drawDetail(owner.detailBounds, mouseX, mouseY);
    }

    private void drawDetail(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        drawSectionTitle(owner.showNbt ? "gui.modern.equipr.u014" : "gui.modern.equipr.u015", bounds);
        ItemStack stack = owner.selectedStack();
        if (stack == null || stack.isEmpty()) {
            ModernUiRenderer.drawText(owner.fontRenderer, "gui.modern.equipr.u016", bounds.x + 12, bounds.y + 32,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, bounds.width - 24));
            owner.nbtBounds = null;
            return;
        }
        int textX = bounds.x + 12;
        int textWidth = Math.max(20, bounds.width - 24);
        ModernUiRenderer.drawText(owner.fontRenderer, stack.getDisplayName(), textX, bounds.y + 30,
                ModernUiRenderer.TEXT, textWidth);
        ModernUiRenderer.drawText(owner.fontRenderer, tr("gui.modern.equipr.fmt.count", String.valueOf(stack.getCount()),
                PlayerEquipmentRenderSupport.itemId(stack)), textX, bounds.y + 46, ModernUiRenderer.SUBTLE_TEXT,
                textWidth);
        owner.nbtBounds = new ModernMainLayout.Rect(bounds.right() - 92, bounds.y + 27, 80, 20);
        drawButton(owner.nbtBounds, owner.showNbt ? "gui.modern.equipr.u017" : "gui.modern.equipr.u018", false, mouseX, mouseY);

        if (!owner.showNbt) {
            PlayerEquipmentRenderSupport.drawLines(owner.fontRenderer,
                    PlayerEquipmentRenderSupport.tooltipLines(owner.minecraft, stack), textX, bounds.y + 67,
                    textWidth, 7, ModernUiRenderer.SUBTLE_TEXT);
            return;
        }
        String rawNbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : "gui.modern.equipr.u019";
        List<String> lines = PlayerEquipmentRenderSupport.wrap(owner.fontRenderer, rawNbt, textWidth);
        int maxVisible = Math.max(1, (bounds.height - 70) / Math.max(1, owner.fontRenderer.FONT_HEIGHT));
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        owner.detailScroll = Math.max(0, Math.min(owner.detailScroll, maxScroll));
        PlayerEquipmentRenderSupport.drawLines(owner.fontRenderer,
                lines.subList(owner.detailScroll, Math.min(lines.size(), owner.detailScroll + maxVisible)), textX,
                bounds.y + 67, textWidth, maxVisible, ModernUiRenderer.SUBTLE_TEXT);
    }

    private void drawEmptyState(ModernMainLayout.Rect body) {
        int centerX = body.x + body.width / 2;
        int y = body.y + Math.max(28, body.height / 3);
        ModernUiRenderer.drawSearchIcon(centerX - 7, y, ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawText(owner.fontRenderer, "gui.modern.equipr.u020", centerX - 54, y + 24,
                ModernUiRenderer.TEXT, Math.max(108, body.width - 20));
        ModernUiRenderer.drawText(owner.fontRenderer, "gui.modern.equipr.u021", centerX - 106, y + 42,
                ModernUiRenderer.MUTED_TEXT, Math.max(212, body.width - 20));
    }

    private void drawSlot(int inventorySlot, int x, int y, int size, String label, int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, size, size);
        boolean hovered = bounds.contains(mouseX, mouseY);
        boolean selected = owner.selectedSlot == inventorySlot;
        ModernUiRenderer.drawSubtlePanel(x, y, size, size, 4,
                selected ? 0xFF283C48 : hovered ? ModernUiRenderer.SURFACE_HOVER : 0xFF111B24,
                selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ItemStack stack = owner.copiedInventory().getStackInSlot(inventorySlot);
        if (stack != null && !stack.isEmpty()) {
            PlayerEquipmentRenderSupport.drawStack(owner.minecraft, owner.fontRenderer, stack,
                    x + Math.max(1, (size - 16) / 2), y + Math.max(1, (size - 16) / 2));
            if (hovered) {
                owner.hoveredSlot = inventorySlot;
                owner.hoveredTooltip = String.join("\n",
                        PlayerEquipmentRenderSupport.tooltipLines(owner.minecraft, stack));
            }
        }
        owner.slotHits.add(new PlayerEquipmentWorkbenchTab.SlotHit(inventorySlot, bounds, label));
    }

    private void drawSectionTitle(String title, ModernMainLayout.Rect bounds) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5, 0xFF151F28,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(owner.fontRenderer, title, bounds.x + 10, bounds.y + 7,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(32, bounds.width - 20));
        ModernUiRenderer.drawDivider(bounds.x + 10, bounds.y + 21, Math.max(1, bounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawButton(ModernMainLayout.Rect bounds, String label, boolean primary, int mouseX, int mouseY) {
        if (bounds == null) return;
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = primary ? (hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT)
                : (hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill,
                primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(owner.fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - owner.fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, bounds.width - 12);
    }

    private void drawTab(ModernMainLayout.Rect bounds, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(owner.fontRenderer, label, bounds.x + 7,
                bounds.y + (bounds.height - owner.fontRenderer.FONT_HEIGHT) / 2,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, bounds.width - 14);
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
