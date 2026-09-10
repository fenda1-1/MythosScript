package com.zszl.zszlScriptMod.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.gui.FontRenderer;

/**
 * A modern renderer and interaction adapter for the existing overlay context
 * menu model. Business actions remain owned by GuiInventory's legacy helpers.
 */
final class ModernContextMenu {

    private static final int ITEM_HEIGHT = 23;
    private static final int SEPARATOR_HEIGHT = 9;
    private static final int PADDING = 7;
    private static final int MIN_WIDTH = 158;
    private static final int MAX_WIDTH = 320;
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
                if (item.separator) {
                    ModernUiRenderer.drawDivider(row.x + 4, row.y + row.height / 2, Math.max(1, row.width - 8),
                            ModernUiRenderer.BORDER_SUBTLE);
                    continue;
                }
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
                int shortcutWidth = item.shortcut == null || item.shortcut.isEmpty() ? 0
                        : fontRenderer.getStringWidth(item.shortcut) + 10;
                int trailing = (item.hasChildren() ? 18 : 8) + shortcutWidth;
                ModernUiRenderer.drawText(fontRenderer, ModernFormI18n.tr(item.label), textLeft,
                        row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, textColor,
                        Math.max(12, row.width - (textLeft - row.x) - trailing));
                if (shortcutWidth > 0) {
                    ModernUiRenderer.drawText(fontRenderer, item.shortcut,
                            row.right() - shortcutWidth + 2 - (item.hasChildren() ? 12 : 0),
                            row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                            ModernUiRenderer.readableText(ModernUiRenderer.MUTED_TEXT, fill), shortcutWidth);
                }
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
        drawHoverPanel(fontRenderer, screenWidth, screenHeight, mouseX, mouseY);
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
                for (int itemIndex = 0; itemIndex < layer.itemBounds.size(); itemIndex++) {
                    if (!layer.itemBounds.get(itemIndex).contains(mouseX, mouseY)) {
                        continue;
                    }
                    GuiInventoryBase.ContextMenuItem item = layer.items.get(itemIndex);
                    if (!item.separator && item.enabled && !item.hasChildren() && item.secondaryAction != null) {
                        Runnable action = item.secondaryAction;
                        close();
                        action.run();
                        return true;
                    }
                    break;
                }
                close();
                return true;
            }
            for (int itemIndex = 0; itemIndex < layer.itemBounds.size(); itemIndex++) {
                if (!layer.itemBounds.get(itemIndex).contains(mouseX, mouseY)) {
                    continue;
                }
                GuiInventoryBase.ContextMenuItem item = layer.items.get(itemIndex);
                if (item.separator) {
                    return true;
                }
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
            int menuHeight = menuHeight(currentItems);
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
            int rowY = bounds.y + PADDING;
            for (int itemIndex = 0; itemIndex < currentItems.size(); itemIndex++) {
                GuiInventoryBase.ContextMenuItem item = currentItems.get(itemIndex);
                int rowHeight = item != null && item.separator ? SEPARATOR_HEIGHT : ITEM_HEIGHT;
                itemBounds.add(new ModernMainLayout.Rect(bounds.x + 3, rowY, bounds.width - 6, rowHeight));
                rowY += rowHeight;
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

    private static int menuHeight(List<GuiInventoryBase.ContextMenuItem> items) {
        int height = PADDING * 2;
        if (items == null) {
            return height;
        }
        for (int i = 0; i < items.size(); i++) {
            GuiInventoryBase.ContextMenuItem item = items.get(i);
            height += item != null && item.separator ? SEPARATOR_HEIGHT : ITEM_HEIGHT;
        }
        return height;
    }

    private static int calculateWidth(FontRenderer fontRenderer, List<GuiInventoryBase.ContextMenuItem> items) {
        int width = MIN_WIDTH;
        for (GuiInventoryBase.ContextMenuItem item : items) {
            if (item == null || item.separator) {
                continue;
            }
            String label = ModernFormI18n.tr(item.label == null ? "" : item.label);
            int labelWidth = fontRenderer.getStringWidth(label);
            int shortcutWidth = item.shortcut == null || item.shortcut.isEmpty() ? 0
                    : fontRenderer.getStringWidth(item.shortcut) + 12;
            width = Math.max(width, labelWidth + shortcutWidth + (item.hasChildren() ? 40 : 28));
        }
        return Math.min(MAX_WIDTH, width);
    }

    private void drawHoverPanel(FontRenderer fontRenderer, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        GuiInventoryBase.ContextMenuItem hoveredItem = null;
        ModernMainLayout.Rect hoveredBounds = null;
        for (int layerIndex = layers.size() - 1; layerIndex >= 0 && hoveredItem == null; layerIndex--) {
            Layer layer = layers.get(layerIndex);
            for (int itemIndex = 0; itemIndex < layer.itemBounds.size(); itemIndex++) {
                ModernMainLayout.Rect itemBounds = layer.itemBounds.get(itemIndex);
                GuiInventoryBase.ContextMenuItem item = layer.items.get(itemIndex);
                if (item != null && item.enabled && item.hasHoverPanel() && itemBounds.contains(mouseX, mouseY)) {
                    hoveredItem = item;
                    hoveredBounds = itemBounds;
                    break;
                }
            }
        }
        if (hoveredItem == null || hoveredBounds == null) {
            return;
        }

        String title = ModernFormI18n.tr(hoveredItem.hoverTitle);
        List<String> bodyLines = wrapPanelLines(fontRenderer, hoveredItem.hoverDescription, 250);
        String leftAction = ModernFormI18n.tr(hoveredItem.hoverLeftAction);
        String rightAction = ModernFormI18n.tr(hoveredItem.hoverRightAction);
        int lineHeight = Math.max(9, fontRenderer.FONT_HEIGHT);
        int widest = Math.max(fontRenderer.getStringWidth(title), fontRenderer.getStringWidth(leftAction));
        widest = Math.max(widest, fontRenderer.getStringWidth(rightAction));
        for (String line : bodyLines) {
            widest = Math.max(widest, fontRenderer.getStringWidth(line));
        }
        int panelWidth = Math.min(290, Math.max(190, widest + 20));
        panelWidth = Math.min(panelWidth, Math.max(140, screenWidth - 10));
        int bodyHeight = bodyLines.size() * lineHeight;
        int actionHeight = (leftAction.isEmpty() ? 0 : lineHeight) + (rightAction.isEmpty() ? 0 : lineHeight);
        int panelHeight = Math.max(42, 30 + bodyHeight + actionHeight + (actionHeight > 0 ? 8 : 0));
        int panelX = hoveredBounds.right() + 8;
        if (panelX + panelWidth > screenWidth - SCREEN_MARGIN) {
            panelX = hoveredBounds.x - panelWidth - 8;
        }
        panelX = clamp(panelX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth - panelWidth - SCREEN_MARGIN));
        int panelY = clamp(hoveredBounds.y, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight - panelHeight - SCREEN_MARGIN));

        ModernUiRenderer.drawPanel(panelX, panelY, panelWidth, panelHeight, 6,
                ModernUiRenderer.TOOLTIP_SURFACE, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawInfoIcon(panelX + 9, panelY + 7,
                ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, ModernUiRenderer.TOOLTIP_SURFACE));
        ModernUiRenderer.drawText(fontRenderer, title, panelX + 25, panelY + 8,
                ModernUiRenderer.TOOLTIP_TEXT, panelWidth - 35);
        ModernUiRenderer.drawDivider(panelX + 9, panelY + 21, panelWidth - 18, ModernUiRenderer.BORDER_SUBTLE);

        int textY = panelY + 26;
        for (String line : bodyLines) {
            ModernUiRenderer.drawText(fontRenderer, line, panelX + 10, textY,
                    ModernUiRenderer.TOOLTIP_SUBTLE_TEXT, panelWidth - 20);
            textY += lineHeight;
        }
        if (actionHeight > 0) {
            textY += 4;
            if (!leftAction.isEmpty()) {
                ModernUiRenderer.drawText(fontRenderer, leftAction, panelX + 10, textY,
                        ModernUiRenderer.readableText(ModernUiRenderer.SUCCESS, ModernUiRenderer.TOOLTIP_SURFACE),
                        panelWidth - 20);
                textY += lineHeight;
            }
            if (!rightAction.isEmpty()) {
                ModernUiRenderer.drawText(fontRenderer, rightAction, panelX + 10, textY,
                        ModernUiRenderer.readableText(ModernUiRenderer.WARNING, ModernUiRenderer.TOOLTIP_SURFACE),
                        panelWidth - 20);
            }
        }
    }

    private static List<String> wrapPanelLines(FontRenderer fontRenderer, String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        for (String line : text.split("\\n", -1)) {
            List<String> wrapped = fontRenderer.listFormattedStringToWidth(line, Math.max(20, width));
            if (wrapped.isEmpty()) {
                result.add("");
            } else {
                result.addAll(wrapped);
            }
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
