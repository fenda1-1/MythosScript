package com.zszl.zszlScriptMod.gui.modern.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Native modern read-only NBT tree viewer for the current main-hand item. */
public final class ModernNbtDetailSettingsTab implements ModernSettingsTab {

    private static final int ROW_HEIGHT = 23;
    private static final int ROW_GAP = 3;

    private static final class NodeHit {
        private final ModernNbtSupport.Node node;
        private final ModernMainLayout.Rect bounds;

        private NodeHit(ModernNbtSupport.Node node, ModernMainLayout.Rect bounds) {
            this.node = node;
            this.bounds = bounds;
        }
    }

    private final Minecraft minecraft;
    private final ItemStack stack;
    private final NBTTagCompound tag;
    private final Set<String> collapsedPaths = new HashSet<>();
    private final List<NodeHit> nodeHits = new ArrayList<>();

    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect treeBounds;
    private ModernMainLayout.Rect treeClipBounds;
    private ModernMainLayout.Rect detailBounds;
    private ModernMainLayout.Rect detailClipBounds;
    private ModernMainLayout.Rect copyValueBounds;
    private ModernMainLayout.Rect copyAllBounds;
    private final ModernHoverScrollbar treeScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar detailScrollbar = new ModernHoverScrollbar();
    private List<ModernNbtSupport.Node> nodes = new ArrayList<>();
    private ModernNbtSupport.Node selectedNode;
    private int treeScrollOffset;
    private int treeMaxScrollOffset;
    private int detailScrollOffset;
    private int detailMaxScrollOffset;

    private String statusMessage = "";
    private long statusMessageUntil;
    private int lastMouseX;
    private int lastMouseY;

    public ModernNbtDetailSettingsTab(Minecraft minecraft) {
        this.minecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
        this.stack = ModernNbtSupport.copyMainHand(this.minecraft);
        this.tag = ModernNbtSupport.copyTag(this.stack);
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (this.fontRenderer == null) {
            this.fontRenderer = fontRenderer;
        }
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = bounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : bounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        panelBounds = safePanel(contentBounds);
        nodes = ModernNbtSupport.flatten(tag, collapsedPaths);
        if (selectedNode != null && !containsNode(selectedNode.path)) {
            selectedNode = null;
        }

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(mouseX, mouseY);

        int bodyX = panelBounds.x + 10;
        int bodyY = panelBounds.y + 55;
        int bodyWidth = Math.max(1, panelBounds.width - 20);
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 10);
        boolean wide = bodyWidth >= 520 && bodyHeight >= 150;
        if (wide) {
            int gap = 8;
            int treeWidth = Math.max(220, Math.min(390, Math.round(bodyWidth * 0.56F)));
            treeBounds = new ModernMainLayout.Rect(bodyX, bodyY, treeWidth, bodyHeight);
            detailBounds = new ModernMainLayout.Rect(treeBounds.right() + gap, bodyY,
                    Math.max(1, bodyWidth - treeWidth - gap), bodyHeight);
        } else {
            int treeHeight = Math.min(Math.max(150, Math.round(bodyHeight * 0.58F)), Math.max(1, bodyHeight));
            treeBounds = new ModernMainLayout.Rect(bodyX, bodyY, bodyWidth, treeHeight);
            int detailY = treeBounds.bottom() + 8;
            detailBounds = detailY < bodyY + bodyHeight
                    ? new ModernMainLayout.Rect(bodyX, detailY, bodyWidth,
                            Math.max(1, bodyY + bodyHeight - detailY))
                    : null;
        }

        drawTree(mouseX, mouseY);
        if (detailBounds != null) {
            drawDetails(mouseX, mouseY);
        }
    }

    private void drawHeader(int mouseX, int mouseY) {
        int iconX = panelBounds.x + 13;
        int iconY = panelBounds.y + 12;
        if (!stack.isEmpty()) {
            PlayerEquipmentRenderSupport.drawStack(minecraft, fontRenderer, stack, iconX, iconY);
        } else {
            ModernUiRenderer.drawStatusDot(iconX + 5, iconY + 5, ModernUiRenderer.MUTED_TEXT);
        }

        int textX = panelBounds.x + 38;
        int textWidth = Math.max(40, panelBounds.width - 54);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbtd.u001", textX, panelBounds.y + 10, ModernUiRenderer.TEXT,
                textWidth);
        String itemText = stack.isEmpty() ? "gui.modern.nbtd.u002"
                : tr("gui.modern.nbtd.fmt.count", stack.getDisplayName(), String.valueOf(stack.getCount()));
        ModernUiRenderer.drawText(fontRenderer, itemText, textX, panelBounds.y + 26,
                stack.isEmpty() ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUBTLE_TEXT, textWidth);
        boolean statusVisible = statusMessage != null && !statusMessage.isEmpty()
                && System.currentTimeMillis() < statusMessageUntil;
        String summary = statusVisible ? statusMessage : tag == null ? "gui.modern.nbtd.u003"
                : tr("gui.modern.nbtd.fmt.tags", String.valueOf(tag.getKeySet().size()));
        int summaryWidth = Math.min(150, Math.max(82, panelBounds.width / 3));
        ModernMainLayout.Rect summaryBounds = new ModernMainLayout.Rect(panelBounds.right() - summaryWidth - 14,
                panelBounds.y + 13, summaryWidth, 19);
        ModernUiRenderer.drawSubtlePanel(summaryBounds.x, summaryBounds.y, summaryBounds.width, summaryBounds.height, 5,
                statusVisible ? 0xFF20353A : tag == null ? ModernUiRenderer.SURFACE : 0xFF20353A,
                statusVisible ? ModernUiRenderer.SUCCESS : tag == null ? ModernUiRenderer.BORDER_SUBTLE
                        : ModernUiRenderer.SUCCESS);
        ModernUiRenderer.drawText(fontRenderer, summary, summaryBounds.x + 7,
                summaryBounds.y + (summaryBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                statusVisible ? ModernUiRenderer.SUCCESS
                        : tag == null ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUCCESS,
                summaryBounds.width - 14);
        ModernUiRenderer.drawDivider(panelBounds.x + 10, panelBounds.y + 45, Math.max(1, panelBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawTree(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(treeBounds.x, treeBounds.y, treeBounds.width, treeBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int titleX = treeBounds.x + 10;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbtd.u004", titleX, treeBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(30, treeBounds.width - 20));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbtd.u005", titleX, treeBounds.y + 23,
                ModernUiRenderer.MUTED_TEXT, Math.max(40, treeBounds.width - 20));
        ModernUiRenderer.drawDivider(titleX, treeBounds.y + 39, Math.max(1, treeBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);

        treeClipBounds = new ModernMainLayout.Rect(treeBounds.x + 7, treeBounds.y + 45,
                Math.max(1, treeBounds.width - 14), Math.max(1, treeBounds.height - 52));
        int totalHeight = nodes.isEmpty() ? 0 : nodes.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        treeMaxScrollOffset = Math.max(0, totalHeight - treeClipBounds.height);
        treeScrollOffset = clamp(treeScrollOffset, 0, treeMaxScrollOffset);
        nodeHits.clear();

        ModernUiRenderer.beginClip(treeClipBounds);
        if (nodes.isEmpty()) {
            String empty = tag == null ? "gui.modern.nbtd.u006" : "gui.modern.nbtd.u007";
            ModernUiRenderer.drawText(fontRenderer, empty, treeClipBounds.x + 10, treeClipBounds.y + 15,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, treeClipBounds.width - 20));
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                ModernNbtSupport.Node node = nodes.get(i);
                int y = treeClipBounds.y + i * (ROW_HEIGHT + ROW_GAP) - treeScrollOffset;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(treeClipBounds.x, y, ModernHoverScrollbar.contentWidth(treeClipBounds.width),
                        ROW_HEIGHT);
                if (y + ROW_HEIGHT < treeClipBounds.y || y > treeClipBounds.bottom()) {
                    continue;
                }
                boolean selected = selectedNode != null && selectedNode.path.equals(node.path);
                boolean hovered = treeClipBounds.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        selected ? 0xFF293D48 : hovered ? ModernUiRenderer.SURFACE_HOVER : 0xFF151F28,
                        selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);

                int indent = Math.min(110, node.depth * 14);
                int markerX = row.x + 8 + indent;
                if (node.isCompound()) {
                    boolean collapsed = collapsedPaths.contains(node.path);
                    ModernUiRenderer.drawChevron(markerX, row.y + 7, collapsed,
                            selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
                } else {
                    ModernUiRenderer.drawStatusDot(markerX + 1, row.y + 8,
                            selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
                }
                int keyX = markerX + 16;
                int typeWidth = Math.min(86, Math.max(46, row.width / 5));
                ModernUiRenderer.drawText(fontRenderer, node.key, keyX, row.y + 5,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(28, row.right() - keyX - typeWidth - 12));
                ModernUiRenderer.drawText(fontRenderer, ModernNbtSupport.typeName(node.tag), row.right() - typeWidth - 8,
                        row.y + 5, selected ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, typeWidth);
                nodeHits.add(new NodeHit(node, row));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(treeScrollbar, treeClipBounds, totalHeight, treeScrollOffset, treeMaxScrollOffset, mouseX, mouseY,
                value -> treeScrollOffset = value);
    }

    private void drawDetails(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(detailBounds.x, detailBounds.y, detailBounds.width, detailBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = detailBounds.x + 10;
        int width = Math.max(20, detailBounds.width - 20);
        String title = selectedNode == null ? "gui.modern.nbtd.u008" : selectedNode.key;
        ModernUiRenderer.drawText(fontRenderer, title, x, detailBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(30, width - 80));
        String type = selectedNode == null ? "" : ModernNbtSupport.typeName(selectedNode.tag);
        if (!type.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, type, detailBounds.right() - 72, detailBounds.y + 8,
                    ModernUiRenderer.SUCCESS, 62);
        }
        ModernUiRenderer.drawDivider(x, detailBounds.y + 26, width, ModernUiRenderer.BORDER_SUBTLE);

        int buttonY = detailBounds.y + 34;
        int buttonWidth = Math.min(96, Math.max(64, (width - 5) / 2));
        copyValueBounds = new ModernMainLayout.Rect(x, buttonY, buttonWidth, 21);
        copyAllBounds = new ModernMainLayout.Rect(copyValueBounds.right() + 5, buttonY,
                Math.max(1, width - buttonWidth - 5), 21);
        drawActionButton(copyValueBounds, "gui.modern.nbtd.u009", selectedNode != null, mouseX, mouseY);
        drawActionButton(copyAllBounds, "gui.modern.nbtd.u010", tag != null, mouseX, mouseY);

        detailClipBounds = new ModernMainLayout.Rect(x, buttonY + 31, width,
                Math.max(1, detailBounds.bottom() - buttonY - 43));
        String value = selectedNode == null ? "gui.modern.nbtd.u011"
                : ModernNbtSupport.tagToString(selectedNode.tag);
        List<String> lines = PlayerEquipmentRenderSupport.wrap(fontRenderer, value, Math.max(20, width - 8));
        int lineHeight = fontRenderer.FONT_HEIGHT + 2;
        int totalHeight = lines.size() * lineHeight;
        detailMaxScrollOffset = Math.max(0, totalHeight - detailClipBounds.height);
        detailScrollOffset = clamp(detailScrollOffset, 0, detailMaxScrollOffset);
        ModernUiRenderer.beginClip(detailClipBounds);
        int start = detailClipBounds.y + 4 - detailScrollOffset;
        for (int i = 0; i < lines.size(); i++) {
            int lineY = start + i * lineHeight;
            if (lineY + fontRenderer.FONT_HEIGHT < detailClipBounds.y || lineY > detailClipBounds.bottom()) {
                continue;
            }
            ModernUiRenderer.drawText(fontRenderer, lines.get(i), detailClipBounds.x + 4, lineY,
                    selectedNode == null ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    ModernHoverScrollbar.contentWidth(detailClipBounds.width - 8));
        }
        ModernUiRenderer.endClip();
        drawScrollbar(detailScrollbar, detailClipBounds, totalHeight, detailScrollOffset, detailMaxScrollOffset, mouseX,
                mouseY, next -> detailScrollOffset = next);
    }

    private void drawActionButton(ModernMainLayout.Rect bounds, String label, boolean enabled, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D24 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE : hovered ? ModernUiRenderer.ACCENT
                : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(12, bounds.width - 12));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (treeScrollbar.beginDrag(mouseX, mouseY) || detailScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (copyValueBounds != null && copyValueBounds.contains(mouseX, mouseY) && selectedNode != null) {
            GuiScreen.setClipboardString(ModernNbtSupport.tagToString(selectedNode.tag));
            showStatus("gui.modern.nbtd.u012");
            return true;
        }
        if (copyAllBounds != null && copyAllBounds.contains(mouseX, mouseY) && tag != null) {
            GuiScreen.setClipboardString(ModernNbtSupport.tagToString(tag));
            showStatus("gui.modern.nbtd.u013");
            return true;
        }
        for (int i = nodeHits.size() - 1; i >= 0; i--) {
            NodeHit hit = nodeHits.get(i);
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            selectedNode = hit.node;
            detailScrollOffset = 0;
            if (hit.node.isCompound()) {
                if (!collapsedPaths.add(hit.node.path)) {
                    collapsedPaths.remove(hit.node.path);
                }
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        if (treeClipBounds != null && treeClipBounds.contains(mouseX, mouseY) && treeMaxScrollOffset > 0) {
            int previous = treeScrollOffset;
            treeScrollOffset = clamp(treeScrollOffset + (wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT), 0,
                    treeMaxScrollOffset);
            return previous != treeScrollOffset;
        }
        if (detailClipBounds != null && detailClipBounds.contains(mouseX, mouseY) && detailMaxScrollOffset > 0) {
            int previous = detailScrollOffset;
            detailScrollOffset = clamp(detailScrollOffset + (wheel > 0 ? -16 : 16), 0, detailMaxScrollOffset);
            return previous != detailScrollOffset;
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && treeScrollbar.isDragging()) {
            treeScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && detailScrollbar.isDragging()) {
            detailScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (treeScrollbar.isDragging() || detailScrollbar.isDragging())) {
            treeScrollbar.endDrag();
            detailScrollbar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (treeScrollbar.isDragging() || detailScrollbar.isDragging()) {
            treeScrollbar.endDrag();
            detailScrollbar.endDrag();
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
        return "";
    }

    @Override
    public void discardDraft() {
        selectedNode = null;
        collapsedPaths.clear();
        treeScrollOffset = 0;
        detailScrollOffset = 0;
        treeScrollbar.endDrag();
        detailScrollbar.endDrag();
        statusMessage = "";
    }

    private boolean containsNode(String path) {
        for (ModernNbtSupport.Node node : nodes) {
            if (node.path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2200L;
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clipBounds, int contentHeight,
            int scrollOffset, int maxScroll, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (clipBounds == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(clipBounds, scrollOffset, maxScroll, clipBounds.height, Math.max(clipBounds.height, contentHeight),
                mouseX, mouseY, setter);
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        int inset = Math.min(12, Math.max(3, Math.min(bounds.width, bounds.height) / 10));
        return new ModernMainLayout.Rect(bounds.x + inset, bounds.y + inset,
                Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
