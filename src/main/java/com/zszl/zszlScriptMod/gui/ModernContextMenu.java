package com.zszl.zszlScriptMod.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/**
 * A modern renderer and interaction adapter for the existing overlay context
 * menu model. Business actions remain owned by GuiInventory's legacy helpers.
 */
final class ModernContextMenu {

    private static final int ITEM_HEIGHT = 23;
    private static final int PADDING = 7;
    private static final int MIN_WIDTH = 138;
    private static final int MAX_WIDTH = 244;
    private static final int SCREEN_MARGIN = 5;

    private static final class Layer {
        private final List<GuiInventoryBase.ContextMenuItem> items;
        private final ModernMainLayout.Rect bounds;
        private final List<ModernMainLayout.Rect> itemBounds;

        private Layer(List<GuiInventoryBase.ContextMenuItem> items, ModernMainLayout.Rect bounds,
                List<ModernMainLayout.Rect> itemBounds) {
            this.items = items;
            this.bounds = bounds;
            this.itemBounds = itemBounds;
        }
    }

    private final List<Integer> openPath = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();
    private List<GuiInventoryBase.ContextMenuItem> rootItems = Collections.emptyList();
    private int anchorX;
    private int anchorY;

    public void open(int mouseX, int mouseY, List<GuiInventoryBase.ContextMenuItem> items) {
        close();
        if (items == null || items.isEmpty()) {
            return;
        }
        rootItems = new ArrayList<>(items);
        anchorX = mouseX;
        anchorY = mouseY;
    }

    public boolean isOpen() {
        return !rootItems.isEmpty();
    }

    public void close() {
        rootItems = Collections.emptyList();
        openPath.clear();
        layers.clear();
    }

    public void draw(FontRenderer fontRenderer, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isOpen() || fontRenderer == null) {
            return;
        }
        buildLayers(fontRenderer, screenWidth, screenHeight);
        boolean changed = false;
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            Layer layer = layers.get(layerIndex);
            ModernUiRenderer.drawPanel(layer.bounds.x, layer.bounds.y, layer.bounds.width, layer.bounds.height, 5,
                    ModernUiRenderer.TOOLTIP_SURFACE, ModernUiRenderer.BORDER);
            for (int itemIndex = 0; itemIndex < layer.items.size(); itemIndex++) {
                GuiInventoryBase.ContextMenuItem item = layer.items.get(itemIndex);
                ModernMainLayout.Rect row = layer.itemBounds.get(itemIndex);
                boolean hovered = row.contains(mouseX, mouseY);
                int fill = hovered && item.enabled ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.TOOLTIP_SURFACE;
                if (hovered && item.enabled) {
                    ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 3,
                            fill);
                }
                int textColor = ModernUiRenderer.readableText(
                        item.enabled ? hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT
                                : ModernUiRenderer.MUTED_TEXT, fill);
                int textLeft = row.x + PADDING;
                if (item.selected) {
                    ModernUiRenderer.drawStatusDot(textLeft, row.y + (row.height - 7) / 2,
                            ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, fill));
                    textLeft += 12;
                }
                ModernUiRenderer.drawText(fontRenderer, item.label, textLeft,
                        row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, textColor,
                        row.width - (textLeft - row.x) - (item.hasChildren() ? 18 : 8));
                if (item.hasChildren()) {
                    ModernUiRenderer.drawChevron(row.right() - 10, row.y + (row.height - 8) / 2, true,
                            ModernUiRenderer.readableText(item.enabled && hovered ? ModernUiRenderer.TEXT
                                    : ModernUiRenderer.MUTED_TEXT, fill));
                    if (hovered && item.enabled && selectOpenChild(layerIndex, itemIndex)) {
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            buildLayers(fontRenderer, screenWidth, screenHeight);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isOpen()) {
            return false;
        }
        for (int layerIndex = layers.size() - 1; layerIndex >= 0; layerIndex--) {
            Layer layer = layers.get(layerIndex);
            if (!layer.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (mouseButton != 0) {
                close();
                return true;
            }
            for (int itemIndex = 0; itemIndex < layer.itemBounds.size(); itemIndex++) {
                if (!layer.itemBounds.get(itemIndex).contains(mouseX, mouseY)) {
                    continue;
                }
                GuiInventoryBase.ContextMenuItem item = layer.items.get(itemIndex);
                if (!item.enabled) {
                    close();
                    return true;
                }
                if (item.hasChildren()) {
                    selectOpenChild(layerIndex, itemIndex);
                    return true;
                }
                Runnable action = item.action;
                close();
                if (action != null) {
                    action.run();
                }
                return true;
            }
            close();
            return true;
        }
        close();
        return true;
    }

    public boolean contains(int mouseX, int mouseY) {
        for (Layer layer : layers) {
            if (layer.bounds.contains(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private boolean selectOpenChild(int layerIndex, int itemIndex) {
        boolean changed = openPath.size() <= layerIndex || openPath.get(layerIndex) != itemIndex
                || openPath.size() > layerIndex + 1;
        while (openPath.size() <= layerIndex) {
            openPath.add(-1);
        }
        openPath.set(layerIndex, itemIndex);
        while (openPath.size() > layerIndex + 1) {
            openPath.remove(openPath.size() - 1);
        }
        return changed;
    }

    private void buildLayers(FontRenderer fontRenderer, int screenWidth, int screenHeight) {
        layers.clear();
        List<GuiInventoryBase.ContextMenuItem> currentItems = rootItems;
        int requestedX = anchorX;
        int requestedY = anchorY;
        for (int layerIndex = 0; currentItems != null && !currentItems.isEmpty() && layerIndex < 6; layerIndex++) {
            int menuWidth = calculateWidth(fontRenderer, currentItems);
            int menuHeight = currentItems.size() * ITEM_HEIGHT + PADDING * 2;
            int x;
            int y;
            if (layerIndex == 0) {
                x = clamp(requestedX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth - menuWidth - SCREEN_MARGIN));
                y = clamp(requestedY, SCREEN_MARGIN,
                        Math.max(SCREEN_MARGIN, screenHeight - menuHeight - SCREEN_MARGIN));
            } else {
                Layer parent = layers.get(layerIndex - 1);
                int parentIndex = layerIndex - 1 < openPath.size() ? openPath.get(layerIndex - 1) : -1;
                ModernMainLayout.Rect sourceRow = parentIndex >= 0 && parentIndex < parent.itemBounds.size()
                        ? parent.itemBounds.get(parentIndex)
                        : parent.bounds;
                x = sourceRow.right() - 2;
                if (x + menuWidth > screenWidth - SCREEN_MARGIN) {
                    x = Math.max(SCREEN_MARGIN, parent.bounds.x - menuWidth + 2);
                }
                y = clamp(sourceRow.y, SCREEN_MARGIN,
                        Math.max(SCREEN_MARGIN, screenHeight - menuHeight - SCREEN_MARGIN));
            }
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, menuWidth, menuHeight);
            List<ModernMainLayout.Rect> itemBounds = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < currentItems.size(); itemIndex++) {
                itemBounds.add(new ModernMainLayout.Rect(bounds.x + 3, bounds.y + PADDING + itemIndex * ITEM_HEIGHT,
                        bounds.width - 6, ITEM_HEIGHT));
            }
            layers.add(new Layer(currentItems, bounds, itemBounds));

            int nextIndex = layerIndex < openPath.size() ? openPath.get(layerIndex) : -1;
            if (nextIndex < 0 || nextIndex >= currentItems.size()) {
                break;
            }
            GuiInventoryBase.ContextMenuItem selected = currentItems.get(nextIndex);
            if (!selected.enabled || !selected.hasChildren()) {
                break;
            }
            currentItems = selected.children;
        }
    }

    private static int calculateWidth(FontRenderer fontRenderer, List<GuiInventoryBase.ContextMenuItem> items) {
        int width = MIN_WIDTH;
        for (GuiInventoryBase.ContextMenuItem item : items) {
            int labelWidth = fontRenderer.getStringWidth(item == null || item.label == null ? "" : item.label);
            width = Math.max(width, labelWidth + 28);
        }
        return Math.min(MAX_WIDTH, width);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
