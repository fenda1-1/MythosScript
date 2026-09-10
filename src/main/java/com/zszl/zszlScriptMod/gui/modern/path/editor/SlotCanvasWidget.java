package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

/**
 * Physical-area relative-index slot canvas plus a one-gesture rectangle
 * selection machine. Persistence is always a flat index array.
 */
public final class SlotCanvasWidget {
    public static final int SOURCE_FILL = 0xFF1F6F73;
    public static final int SOURCE_FILL_HOVER = 0xFF2A8A8F;
    public static final int TARGET_FILL = 0xFF5A3D86;
    public static final int TARGET_FILL_HOVER = 0xFF6F4EA4;
    public static final int RECT_FILL = 0x66F0B55E;
    public static final int RECT_BORDER = 0xFFF0B55E;
    public static final int DISABLED_FILL = 0xFF1A2228;
    public static final int LIMIT_BADGE = 0xFFF0B55E;
    public static final int FORBIDDEN_BADGE = 0xFFD96A52;
    public static final int LIMIT_RECT_FILL = 0x665D4A9E;
    public static final int LIMIT_RECT_BORDER = 0xFFE0B8FF;

    public enum Role {
        SOURCE, TARGET, NEUTRAL, DISABLED
    }

    public static final class Layout {
        public final ModernMainLayout.Rect bounds;
        public final ModernMainLayout.Rect titleBounds;
        public final ModernMainLayout.Rect sizeBounds;
        public final int rows;
        public final int cols;
        public final int cell;
        public final int gap;
        public final int gridX;
        public final int gridY;
        public final int liveSlots;

        Layout(ModernMainLayout.Rect bounds, ModernMainLayout.Rect titleBounds, ModernMainLayout.Rect sizeBounds,
                int rows, int cols, int cell, int gap, int gridX, int gridY, int liveSlots) {
            this.bounds = bounds;
            this.titleBounds = titleBounds;
            this.sizeBounds = sizeBounds;
            this.rows = rows;
            this.cols = cols;
            this.cell = cell;
            this.gap = gap;
            this.gridX = gridX;
            this.gridY = gridY;
            this.liveSlots = liveSlots;
        }

        public int capacity() {
            return Math.max(0, rows * cols);
        }

        public ModernMainLayout.Rect cellAt(int index) {
            if (index < 0 || index >= capacity() || cols <= 0) {
                return null;
            }
            int row = index / cols;
            int col = index % cols;
            return new ModernMainLayout.Rect(gridX + col * (cell + gap), gridY + row * (cell + gap), cell, cell);
        }

        public int indexAt(int mouseX, int mouseY) {
            if (cols <= 0 || rows <= 0) {
                return -1;
            }
            int stride = cell + gap;
            if (stride <= 0) {
                return -1;
            }
            int col = (mouseX - gridX) / stride;
            int row = (mouseY - gridY) / stride;
            if (col < 0 || col >= cols || row < 0 || row >= rows) {
                return -1;
            }
            ModernMainLayout.Rect cellBounds = cellAt(row * cols + col);
            if (cellBounds == null || !cellBounds.contains(mouseX, mouseY)) {
                return -1;
            }
            return row * cols + col;
        }

        public boolean isLive(int index) {
            // The canvas describes a reusable relative-slot mapping. The
            // current container size is informational; runtime filters slots
            // that do not exist in the container opened at execution time.
            return index >= 0 && index < capacity();
        }
    }

    public static final class Machine {
        public boolean dragging;
        public boolean removeMode;
        public int anchor = -1;
        public int current = -1;
        public int focusIndex;
        public final Set<Integer> snapshot = new LinkedHashSet<Integer>();

        public void reset() {
            dragging = false;
            removeMode = false;
            anchor = -1;
            current = -1;
            snapshot.clear();
        }

        public boolean hasActiveRect() {
            return dragging && anchor >= 0 && current >= 0;
        }

        public Set<Integer> preview(Layout layout) {
            Set<Integer> result = new LinkedHashSet<Integer>(snapshot);
            if (!hasActiveRect() || layout == null) {
                return result;
            }
            Set<Integer> rect = rectangle(layout, anchor, current);
            if (removeMode) {
                result.removeAll(rect);
            } else {
                result.addAll(rect);
            }
            return result;
        }
    }

    private SlotCanvasWidget() {
    }

    public static Layout layout(ModernMainLayout.Rect bounds, int rows, int cols, int liveSlots) {
        int safeRows = Math.max(1, rows);
        int safeCols = Math.max(1, cols);
        int gap = 2;
        int titleHeight = 18;
        int availableWidth = Math.max(1, bounds.width - 16);
        int availableHeight = Math.max(18, bounds.height - titleHeight - 10);
        int cellByWidth = Math.max(18, (availableWidth - Math.max(0, safeCols - 1) * gap) / safeCols);
        int cellByHeight = Math.max(18, (availableHeight - Math.max(0, safeRows - 1) * gap) / safeRows);
        int cell = Math.max(18, Math.min(24, Math.min(cellByWidth, cellByHeight)));
        int gridWidth = safeCols * cell + Math.max(0, safeCols - 1) * gap;
        int gridX = bounds.x + Math.max(8, (bounds.width - gridWidth) / 2);
        int gridY = bounds.y + titleHeight + 4;
        int sizeWidth = 72;
        int sizeX = bounds.right() - sizeWidth - 8;
        int countX = sizeX - 94;
        ModernMainLayout.Rect title = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + 2,
                Math.max(40, countX - bounds.x - 12), 16);
        ModernMainLayout.Rect size = new ModernMainLayout.Rect(sizeX, bounds.y + 2, sizeWidth, 16);
        return new Layout(bounds, title, size, safeRows, safeCols, cell, gap, gridX, gridY, liveSlots);
    }

    public static int height(int rows, int width) {
        int safeRows = Math.max(1, rows);
        int cell = 20;
        return 22 + safeRows * (cell + 2) + 8;
    }

    public static Set<Integer> rectangle(Layout layout, int from, int to) {
        Set<Integer> result = new LinkedHashSet<Integer>();
        if (layout == null || from < 0 || to < 0) {
            return result;
        }
        int start = Math.min(from, to);
        int end = Math.max(from, to);
        int startRow = start / layout.cols;
        int startCol = start % layout.cols;
        int endRow = end / layout.cols;
        int endCol = end % layout.cols;
        int minRow = Math.min(startRow, endRow);
        int maxRow = Math.max(startRow, endRow);
        int minCol = Math.min(startCol, endCol);
        int maxCol = Math.max(startCol, endCol);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                int index = row * layout.cols + col;
                if (index >= 0 && index < layout.capacity() && layout.isLive(index)) {
                    result.add(Integer.valueOf(index));
                }
            }
        }
        return result;
    }

    public static void draw(FontRenderer font, Layout layout, String title, int selectedCount, Set<Integer> selected,
            Machine machine, Role role, int mouseX, int mouseY, boolean enabled, Integer focusedIndex,
            LimitLookup limits) {
        draw(font, layout, title, selectedCount, selected, machine, role, mouseX, mouseY, enabled, focusedIndex,
                limits, null);
    }

    public static void draw(FontRenderer font, Layout layout, String title, int selectedCount, Set<Integer> selected,
            Machine machine, Role role, int mouseX, int mouseY, boolean enabled, Integer focusedIndex,
            LimitLookup limits, Set<Integer> limitSelection) {
        if (layout == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(layout.bounds.x, layout.bounds.y, layout.bounds.width, layout.bounds.height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, title, layout.titleBounds.x, layout.titleBounds.y + 3, ModernUiRenderer.TEXT,
                layout.titleBounds.width);
        String sizeLabel = layout.rows + " x " + layout.cols;
        boolean sizeHover = layout.sizeBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(layout.sizeBounds.x, layout.sizeBounds.y, layout.sizeBounds.width,
                layout.sizeBounds.height, 3, sizeHover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                sizeHover ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        drawCentered(font, sizeLabel, layout.sizeBounds, sizeHover ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT);
        String count = selectedCount <= 0
                ? I18n.format("gui.path.action_editor.move_chest.none_selected")
                : I18n.format("gui.path.action_editor.move_chest.selected", Integer.valueOf(selectedCount));
        ModernUiRenderer.drawText(font, count, layout.sizeBounds.x - 94, layout.bounds.y + 5,
                ModernUiRenderer.ACCENT, 88);
        Set<Integer> preview = machine != null && machine.hasActiveRect() ? machine.preview(layout) : selected;
        Set<Integer> rect = machine != null && machine.hasActiveRect()
                ? rectangle(layout, machine.anchor, machine.current) : null;
        for (int index = 0; index < layout.capacity(); index++) {
            ModernMainLayout.Rect cell = layout.cellAt(index);
            if (cell == null) {
                continue;
            }
            boolean live = layout.isLive(index);
            boolean hovered = enabled && live && cell.contains(mouseX, mouseY);
            boolean active = preview != null && preview.contains(Integer.valueOf(index));
            boolean inRect = rect != null && rect.contains(Integer.valueOf(index));
            boolean focused = focusedIndex != null && focusedIndex.intValue() == index;
            int fill;
            int border;
            if (!live) {
                fill = DISABLED_FILL;
                border = ModernUiRenderer.BORDER_SUBTLE;
            } else if (role == Role.SOURCE && active) {
                fill = hovered ? SOURCE_FILL_HOVER : SOURCE_FILL;
                border = ModernUiRenderer.SUCCESS;
            } else if (role == Role.TARGET && active) {
                fill = hovered ? TARGET_FILL_HOVER : TARGET_FILL;
                border = 0xFFB08CFF;
            } else if (active) {
                fill = hovered ? 0xFF4B8BB2 : 0xFF2F6F95;
                border = ModernUiRenderer.ACCENT;
            } else {
                fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
                border = hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE;
            }
            if (focused) {
                border = ModernUiRenderer.ACCENT;
            }
            ModernUiRenderer.drawSubtlePanel(cell.x, cell.y, cell.width, cell.height, 3, fill, border);
            if (inRect) {
                ModernUiRenderer.drawSubtlePanel(cell.x, cell.y, cell.width, cell.height, 3, RECT_FILL, RECT_BORDER);
            }
            if (limitSelection != null && limitSelection.contains(Integer.valueOf(index))) {
                ModernUiRenderer.drawSubtlePanel(cell.x + 1, cell.y + 1, Math.max(1, cell.width - 2),
                        Math.max(1, cell.height - 2), 3, LIMIT_RECT_FILL, LIMIT_RECT_BORDER);
            }
            if (!live) {
                ModernUiRenderer.drawDivider(cell.x + 2, cell.y + cell.height / 2, cell.width - 4, 0x66FFFFFF);
            }
            String label = index < 10 ? "0" + index : String.valueOf(index);
            drawCentered(font, label, cell, !live ? ModernUiRenderer.MUTED_TEXT
                    : active ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
            if (live && role != Role.NEUTRAL && active) {
                String mark = role == Role.SOURCE ? "S" : "T";
                font.drawString(mark, cell.x + 2, cell.y + 1, role == Role.SOURCE ? ModernUiRenderer.SUCCESS : 0xFFB08CFF);
            }
            if (limits != null && live) {
                Integer take = limits.maxTake(index);
                Integer put = limits.maxPut(index);
                if (take != null || put != null) {
                    String badge;
                    int badgeColor;
                    if ((take != null && take.intValue() == 0) || (put != null && put.intValue() == 0)) {
                        badge = "0";
                        badgeColor = FORBIDDEN_BADGE;
                    } else if (take != null) {
                        badge = I18n.format("gui.path.action_editor.move_chest.badge_take", take);
                        badgeColor = LIMIT_BADGE;
                    } else {
                        badge = I18n.format("gui.path.action_editor.move_chest.badge_put", put);
                        badgeColor = LIMIT_BADGE;
                    }
                    font.drawString(badge, cell.right() - font.getStringWidth(badge) - 1, cell.bottom() - 9, badgeColor);
                }
            }
        }
    }

    public static String tooltip(Layout layout, int index, boolean inventory) {
        if (layout == null || index < 0) {
            return "";
        }
        return I18n.format(inventory
                ? "gui.path.action_editor.move_chest.tooltip.inventory_slot"
                : "gui.path.action_editor.move_chest.tooltip.chest_slot", Integer.valueOf(index));
    }

    public static int moveFocus(Layout layout, int current, int keyCode) {
        if (layout == null || layout.capacity() <= 0) {
            return 0;
        }
        int index = ActionEditorJson.clamp(current, 0, layout.capacity() - 1);
        int row = index / layout.cols;
        int col = index % layout.cols;
        if (keyCode == Keyboard.KEY_LEFT) {
            col = Math.max(0, col - 1);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            col = Math.min(layout.cols - 1, col + 1);
        } else if (keyCode == Keyboard.KEY_UP) {
            row = Math.max(0, row - 1);
        } else if (keyCode == Keyboard.KEY_DOWN) {
            row = Math.min(layout.rows - 1, row + 1);
        }
        return row * layout.cols + col;
    }

    public static Set<Integer> allLive(Layout layout) {
        Set<Integer> result = new LinkedHashSet<Integer>();
        if (layout == null) {
            return result;
        }
        int max = layout.capacity();
        for (int i = 0; i < max; i++) {
            result.add(Integer.valueOf(i));
        }
        return result;
    }

    private static void drawCentered(FontRenderer font, String label, ModernMainLayout.Rect rect, int color) {
        if (font == null || rect == null) {
            return;
        }
        String text = label == null ? "" : label;
        int textWidth = font.getStringWidth(text);
        if (textWidth <= 0) {
            return;
        }
        int available = Math.max(4, rect.width - 4);
        float scale = textWidth > available ? (float) available / (float) textWidth : 1.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(rect.x + rect.width / 2.0F, rect.y + rect.height / 2.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        font.drawStringWithShadow(text, -textWidth / 2.0F, -font.FONT_HEIGHT / 2.0F, color);
        GlStateManager.popMatrix();
    }

    public interface LimitLookup {
        Integer maxTake(int index);

        Integer maxPut(int index);
    }
}
