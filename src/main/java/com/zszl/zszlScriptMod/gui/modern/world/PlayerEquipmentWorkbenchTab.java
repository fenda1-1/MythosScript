package com.zszl.zszlScriptMod.gui.modern.world;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.inventory.InventoryViewerManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Read-only equipment snapshot viewer hosted directly by the modern shell. */
public final class PlayerEquipmentWorkbenchTab implements ModernSettingsTab {

    enum ViewMode {
        EQUIPMENT,
        INVENTORY
    }

    final Minecraft minecraft;
    final List<SlotHit> slotHits = new ArrayList<>();
    ModernMainLayout.Rect contentBounds;
    ModernMainLayout.Rect panelBounds;
    ModernMainLayout.Rect detailBounds;
    ModernMainLayout.Rect copyBounds;
    ModernMainLayout.Rect clearBounds;
    ModernMainLayout.Rect equipmentTabBounds;
    ModernMainLayout.Rect inventoryTabBounds;
    ModernMainLayout.Rect nbtBounds;
    ModernMainLayout.Rect compactViewport;
    ModernMainLayout.Rect compactScrollbarBounds;
    ModernMainLayout.Rect compactScrollbarThumbBounds;
    FontRenderer fontRenderer;
    ViewMode viewMode = ViewMode.EQUIPMENT;
    int selectedSlot = -1;
    int hoveredSlot = -1;
    int detailScroll;
    int compactScroll;
    int compactMaxScroll;
    int observedCopyVersion = -1;
    boolean showNbt;
    boolean draggingCompactScrollbar;
    final ModernHoverScrollbar compactScrollbar = new ModernHoverScrollbar();
    String hoveredTooltip = "";
    String statusMessage = "";
    long statusMessageUntil;
    private final PlayerEquipmentWorkbenchRenderer renderer = new PlayerEquipmentWorkbenchRenderer(this);

    public PlayerEquipmentWorkbenchTab(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public static PlayerEquipmentWorkbenchTab create(Minecraft minecraft) {
        return new PlayerEquipmentWorkbenchTab(minecraft);
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public String viewMode() {
        return viewMode.name();
    }

    public boolean copyTarget() {
        boolean copied = InventoryViewerManager.copyInventoryFromTarget();
        selectedSlot = -1;
        showNbt = false;
        detailScroll = 0;
        observedCopyVersion = InventoryViewerManager.getCopyVersion();
        showStatus(copied ? InventoryViewerManager.getLastCopyStatus() : "gui.modern.equip.u001");
        return copied;
    }

    public void clearSnapshot() {
        InventoryViewerManager.clearCopiedInventory();
        selectedSlot = -1;
        showNbt = false;
        detailScroll = 0;
        observedCopyVersion = InventoryViewerManager.getCopyVersion();
        showStatus(InventoryViewerManager.getLastCopyStatus());
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (fontRenderer != null) {
            this.fontRenderer = fontRenderer;
        }
        if (observedCopyVersion < 0) {
            observedCopyVersion = InventoryViewerManager.getCopyVersion();
        }
    }

    @Override
    public void updateScreen() {
        int version = InventoryViewerManager.getCopyVersion();
        if (observedCopyVersion != version) {
            observedCopyVersion = version;
            selectedSlot = -1;
            showNbt = false;
            detailScroll = 0;
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requestedBounds;
        hoveredTooltip = "";
        hoveredSlot = -1;
        slotHits.clear();

        panelBounds = contentBounds.inset(Math.min(12, Math.min(contentBounds.width, contentBounds.height) / 8));
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        renderer.draw(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (compactScrollbar.beginDrag(mouseX, mouseY)) {
            draggingCompactScrollbar = true;
            return true;
        }
        if (contains(copyBounds, mouseX, mouseY)) {
            copyTarget();
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            clearSnapshot();
            return true;
        }
        if (contains(equipmentTabBounds, mouseX, mouseY)) {
            viewMode = ViewMode.EQUIPMENT;
            selectedSlot = -1;
            showNbt = false;
            return true;
        }
        if (contains(inventoryTabBounds, mouseX, mouseY)) {
            viewMode = ViewMode.INVENTORY;
            selectedSlot = -1;
            showNbt = false;
            return true;
        }
        if (contains(nbtBounds, mouseX, mouseY) && selectedStack() != null) {
            showNbt = !showNbt;
            detailScroll = 0;
            return true;
        }
        for (SlotHit hit : slotHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                selectedSlot = hit.slot;
                showNbt = false;
                detailScroll = 0;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel != 0 && showNbt && detailBounds != null && detailBounds.contains(mouseX, mouseY)) {
            int before = detailScroll;
            detailScroll = Math.max(0, detailScroll + (wheel > 0 ? -2 : 2));
            return before != detailScroll;
        }
        if (wheel != 0 && compactViewport != null && compactViewport.contains(mouseX, mouseY)
                && compactMaxScroll > 0) {
            int before = compactScroll;
            compactScroll = Math.max(0, Math.min(compactMaxScroll, compactScroll + (wheel > 0 ? -32 : 32)));
            return before != compactScroll;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (draggingCompactScrollbar) {
            draggingCompactScrollbar = false;
            return true;
        }
        if (showNbt) {
            showNbt = false;
            detailScroll = 0;
            return true;
        }
        if (selectedSlot >= 0) {
            selectedSlot = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contentBounds != null && contentBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredSlot >= 0 && hoveredTooltip != null ? hoveredTooltip : "";
    }

    @Override
    public void discardDraft() {
        selectedSlot = -1;
        showNbt = false;
        detailScroll = 0;
        compactScroll = 0;
        draggingCompactScrollbar = false;
        statusMessage = "";
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingCompactScrollbar && clickedMouseButton == 0) {
            compactScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingCompactScrollbar) {
            draggingCompactScrollbar = false;
            compactScrollbar.endDrag();
            return true;
        }
        return false;
    }

    private void updateCompactScroll(int mouseY) {
        if (compactScrollbarBounds == null || compactScrollbarThumbBounds == null || compactMaxScroll <= 0) {
            return;
        }
        int travel = Math.max(1, compactScrollbarBounds.height - compactScrollbarThumbBounds.height);
        int target = Math.max(0, Math.min(travel,
                mouseY - compactScrollbarBounds.y - compactScrollbarThumbBounds.height / 2));
        compactScroll = Math.max(0, Math.min(compactMaxScroll, Math.round(target * (float) compactMaxScroll / travel)));
    }

    ItemStack selectedStack() {
        if (selectedSlot < 0 || selectedSlot >= copiedInventory().getSizeInventory()) return ItemStack.EMPTY;
        return copiedInventory().getStackInSlot(selectedSlot);
    }

    IInventory copiedInventory() {
        return InventoryViewerManager.getCopiedInventory();
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    static final class SlotHit {
        final int slot;
        final ModernMainLayout.Rect bounds;
        @SuppressWarnings("unused")
        final String label;

        SlotHit(int slot, ModernMainLayout.Rect bounds, String label) {
            this.slot = slot;
            this.bounds = bounds;
            this.label = label;
        }
    }
}
