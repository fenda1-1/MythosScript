package com.zszl.zszlScriptMod.gui.modern.baritone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BlockDisplayLookup;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BlockUtils;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

/** Native modal selector for Baritone block-list settings. */
final class ModernBaritoneBlockListEditor {

    private static final int ROW_HEIGHT = 28;
    private static final int MAX_CATALOG_ENTRIES = 4096;

    private final String settingKey;
    private final String settingLabel;
    private final List<String> defaultBlockIds;
    private final List<String> workingBlockIds;
    private final List<BlockEntry> catalog = new ArrayList<>();
    private final List<BlockEntry> filteredCatalog = new ArrayList<>();
    private final Consumer<String> onConfirm;

    private ModernTextField searchField;
    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect modalBounds;
    private ModernMainLayout.Rect catalogBounds;
    private ModernMainLayout.Rect selectedBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect defaultBounds;
    private ModernMainLayout.Rect cancelBounds;
    private ModernMainLayout.Rect confirmBounds;
    private ModernMainLayout.Rect catalogListBounds;
    private ModernMainLayout.Rect selectedListBounds;
    private final ModernHoverScrollbar catalogScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar selectedScrollbar = new ModernHoverScrollbar();
    private String searchText = "";
    private int catalogScroll;
    private int catalogMaxScroll;
    private int selectedScroll;
    private int selectedMaxScroll;

    private boolean initialized;
    private boolean closed;

    private static final class BlockEntry {
        private final String id;
        private final String displayName;
        private final Block block;

        private BlockEntry(String id, String displayName, Block block) {
            this.id = id;
            this.displayName = displayName;
            this.block = block;
        }
    }

    ModernBaritoneBlockListEditor(String settingKey, String settingLabel, String currentValue, String defaultValue,
            Consumer<String> onConfirm) {
        this.settingKey = safe(settingKey);
        this.settingLabel = safe(settingLabel);
        this.defaultBlockIds = parseIds(defaultValue);
        this.workingBlockIds = parseIds(currentValue);
        this.onConfirm = onConfirm;
    }

    static int count(String raw) {
        return parseIds(raw).size();
    }

    void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        this.fontRenderer = fontRenderer;
        this.searchField = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        this.searchField.setMaxStringLength(128);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setTextColor(ModernUiRenderer.TEXT);
        this.searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        this.searchField.setVisible(false);
        loadCatalog();
        refreshCatalog();
        initialized = true;
    }

    private void loadCatalog() {
        catalog.clear();
        for (Block block : Block.REGISTRY) {
            if (block == null || catalog.size() >= MAX_CATALOG_ENTRIES) {
                continue;
            }
            try {
                String id = BlockUtils.blockToString(block);
                ItemStack stack = new ItemStack(block);
                String displayName = stack.isEmpty() ? id : stack.getDisplayName();
                if (id != null && !id.trim().isEmpty() && !stack.isEmpty()) {
                    catalog.add(new BlockEntry(id, displayName, block));
                }
            } catch (Throwable ignored) {
                // A malformed mod block should not make the selector unusable.
            }
        }
        catalog.sort((first, second) -> first.id.compareToIgnoreCase(second.id));
    }

    private void refreshCatalog() {
        filteredCatalog.clear();
        String query = normalize(searchText);
        for (BlockEntry entry : catalog) {
            if (query.isEmpty() || normalize(entry.id).contains(query)
                    || normalize(entry.displayName).contains(query)) {
                filteredCatalog.add(entry);
            }
        }
        catalogScroll = 0;
    }

    void draw(ModernMainLayout.Rect hostBounds, int mouseX, int mouseY) {
        if (closed) {
            return;
        }
        ensureInitialized(fontRenderer);
        if (hostBounds == null) {
            return;
        }
        ModernUiRenderer.drawRoundedRect(hostBounds.x - 4, hostBounds.y - 4, hostBounds.width + 8,
                hostBounds.height + 8, 0, 0xB8000000);

        int width = Math.min(720, Math.max(500, hostBounds.width - 28));
        int height = Math.min(470, Math.max(340, hostBounds.height - 28));
        width = Math.min(width, Math.max(1, hostBounds.width));
        height = Math.min(height, Math.max(1, hostBounds.height));
        int x = hostBounds.x + (hostBounds.width - width) / 2;
        int y = hostBounds.y + (hostBounds.height - height) / 2;
        modalBounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        int innerX = x + 14;
        int innerWidth = Math.max(1, width - 28);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u001", innerX, y + 10, ModernUiRenderer.TEXT,
                Math.max(100, innerWidth - 220));
        ModernUiRenderer.drawText(fontRenderer, settingKey, innerX, y + 27, ModernUiRenderer.ACCENT,
                Math.max(90, innerWidth - 220));
        ModernUiRenderer.drawText(fontRenderer, settingLabel, innerX, y + 42, ModernUiRenderer.MUTED_TEXT,
                Math.max(90, innerWidth - 220));
        ModernUiRenderer.drawText(fontRenderer, ModernFormI18n.tr("gui.modern.baritone_blocks.fmt.selected",
                String.valueOf(workingBlockIds.size())), x + width - 104, y + 20,
                ModernUiRenderer.SUBTLE_TEXT, 90);
        ModernUiRenderer.drawDivider(innerX, y + 61, innerWidth, ModernUiRenderer.BORDER_SUBTLE);

        int contentY = y + 72;
        int contentHeight = Math.max(80, height - 128);
        int columnGap = 8;
        int leftWidth;
        int rightWidth;
        if (width < 500) {
            leftWidth = Math.max(1, (innerWidth - columnGap) / 2);
            rightWidth = Math.max(1, innerWidth - columnGap - leftWidth);
        } else {
            leftWidth = Math.max(150, (innerWidth - columnGap) * 3 / 5);
            rightWidth = Math.max(120, innerWidth - columnGap - leftWidth);
        }
        catalogBounds = new ModernMainLayout.Rect(innerX, contentY, leftWidth, contentHeight);
        selectedBounds = new ModernMainLayout.Rect(catalogBounds.right() + columnGap, contentY, rightWidth, contentHeight);

        drawCatalog(mouseX, mouseY);
        drawSelected(mouseX, mouseY);

        int buttonY = y + height - 39;
        int buttonGap = 6;
        int buttonWidth = width < 400 ? Math.max(1, (width - buttonGap * 3 - 28) / 4) : 76;
        clearBounds = new ModernMainLayout.Rect(innerX, buttonY, buttonWidth, 22);
        defaultBounds = new ModernMainLayout.Rect(clearBounds.right() + buttonGap, buttonY, buttonWidth, 22);
        confirmBounds = new ModernMainLayout.Rect(x + width - buttonWidth, buttonY, buttonWidth, 22);
        cancelBounds = new ModernMainLayout.Rect(confirmBounds.x - buttonGap - buttonWidth, buttonY, buttonWidth, 22);
        drawButton(clearBounds, "gui.modern.baritone_blocks.u002", false, clearBounds.contains(mouseX, mouseY));
        drawButton(defaultBounds, "gui.modern.baritone_blocks.u003", false, defaultBounds.contains(mouseX, mouseY));
        drawButton(cancelBounds, "gui.modern.baritone_blocks.u004", false, cancelBounds.contains(mouseX, mouseY));
        drawButton(confirmBounds, "gui.modern.baritone_blocks.u005", true, confirmBounds.contains(mouseX, mouseY));
    }

    private void drawCatalog(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(catalogBounds.x, catalogBounds.y, catalogBounds.width, catalogBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int innerX = catalogBounds.x + 8;
        int innerWidth = Math.max(1, catalogBounds.width - 16);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u006", innerX, catalogBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(40, innerWidth - 12));
        searchBounds = new ModernMainLayout.Rect(innerX, catalogBounds.y + 25, innerWidth, 24);
        drawSearchField(mouseX, mouseY);
        catalogListBounds = new ModernMainLayout.Rect(innerX, searchBounds.bottom() + 6, innerWidth,
                Math.max(1, catalogBounds.bottom() - searchBounds.bottom() - 14));
        ModernMainLayout.Rect listBounds = catalogListBounds;
        int visibleRows = Math.max(1, listBounds.height / ROW_HEIGHT);
        catalogMaxScroll = Math.max(0, filteredCatalog.size() - visibleRows);
        catalogScroll = clamp(catalogScroll, 0, catalogMaxScroll);
        ModernUiRenderer.beginClip(listBounds);
        int end = Math.min(filteredCatalog.size(), catalogScroll + visibleRows + 1);
        for (int index = catalogScroll; index < end; index++) {
            BlockEntry entry = filteredCatalog.get(index);
            int rowY = listBounds.y + (index - catalogScroll) * ROW_HEIGHT;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(listBounds.x, rowY, ModernHoverScrollbar.contentWidth(listBounds.width), ROW_HEIGHT - 2);
            boolean selected = workingBlockIds.contains(entry.id);
            boolean hovered = row.contains(mouseX, mouseY) && listBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.BORDER : 0x00111111);
            drawBlockIcon(entry.block, row.x + 5, row.y + 5);
            ModernUiRenderer.drawText(fontRenderer, entry.displayName, row.x + 31, row.y + 4,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(30, row.width - 42));
            ModernUiRenderer.drawText(fontRenderer, entry.id, row.x + 31, row.y + 16, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, row.width - 42));
            if (selected) {
                ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u007", row.right() - 35, row.y + 8, ModernUiRenderer.SUCCESS, 30);
            }
        }
        if (filteredCatalog.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u008", listBounds.x + 10, listBounds.y + 12,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, listBounds.width - 20));
        }
        ModernUiRenderer.endClip();
        drawScrollbar(catalogScrollbar, listBounds, catalogScroll, catalogMaxScroll, visibleRows,
                filteredCatalog.size(), mouseX, mouseY, value -> catalogScroll = value);
    }

    private void drawSelected(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(selectedBounds.x, selectedBounds.y, selectedBounds.width, selectedBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int innerX = selectedBounds.x + 8;
        int innerWidth = Math.max(1, selectedBounds.width - 16);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u009", innerX, selectedBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(40, innerWidth - 18));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u010", innerX, selectedBounds.y + 24,
                ModernUiRenderer.MUTED_TEXT, Math.max(40, innerWidth - 8));
        selectedListBounds = new ModernMainLayout.Rect(innerX, selectedBounds.y + 44, innerWidth,
                Math.max(1, selectedBounds.bottom() - selectedBounds.y - 52));
        ModernMainLayout.Rect listBounds = selectedListBounds;
        int visibleRows = Math.max(1, listBounds.height / ROW_HEIGHT);
        selectedMaxScroll = Math.max(0, workingBlockIds.size() - visibleRows);
        selectedScroll = clamp(selectedScroll, 0, selectedMaxScroll);
        ModernUiRenderer.beginClip(listBounds);
        int end = Math.min(workingBlockIds.size(), selectedScroll + visibleRows + 1);
        for (int index = selectedScroll; index < end; index++) {
            String id = workingBlockIds.get(index);
            BlockEntry entry = findCatalogEntry(id);
            int rowY = listBounds.y + (index - selectedScroll) * ROW_HEIGHT;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(listBounds.x, rowY, ModernHoverScrollbar.contentWidth(listBounds.width), ROW_HEIGHT - 2);
            boolean hovered = row.contains(mouseX, mouseY) && listBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                    hovered ? ModernUiRenderer.BORDER : 0x00111111);
            if (entry != null) {
                drawBlockIcon(entry.block, row.x + 5, row.y + 5);
                ModernUiRenderer.drawText(fontRenderer, entry.displayName, row.x + 31, row.y + 4,
                        ModernUiRenderer.TEXT, Math.max(30, row.width - 42));
            }
            ModernUiRenderer.drawText(fontRenderer, id, row.x + 31, row.y + 16, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, row.width - 42));
        }
        if (workingBlockIds.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u011", listBounds.x + 10, listBounds.y + 14,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, listBounds.width - 20));
        }
        ModernUiRenderer.endClip();
        int selectedVisibleRows = Math.max(1, listBounds.height / ROW_HEIGHT);
        drawScrollbar(selectedScrollbar, listBounds, selectedScroll, selectedMaxScroll, selectedVisibleRows,
                workingBlockIds.size(), mouseX, mouseY, value -> selectedScroll = value);
    }

    private void drawSearchField(int mouseX, int mouseY) {
        boolean focused = searchField != null && searchField.isFocused();
        boolean hovered = searchBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                0xFF101820, focused ? ModernUiRenderer.ACCENT
                        : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 7, searchBounds.y + 7, ModernUiRenderer.SUBTLE_TEXT);
        searchField.setVisible(true);
        searchField.setEnabled(true);
        searchField.x = searchBounds.x + 23;
        searchField.y = searchBounds.y + (searchBounds.height - fontRenderer.FONT_HEIGHT) / 2;
        searchField.width = Math.max(1, searchBounds.width - 29);
        searchField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);
        if (safe(searchField.getText()).isEmpty() && !focused) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_blocks.u012", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, searchField.width));
        }
    }

    private void drawBlockIcon(Block block, int x, int y) {
        if (block == null) {
            return;
        }
        ItemStack stack;
        try {
            stack = new ItemStack(block);
        } catch (Throwable ignored) {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (closed) {
            return true;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (catalogScrollbar.beginDrag(mouseX, mouseY) || selectedScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (contains(searchBounds, mouseX, mouseY)) {
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            workingBlockIds.clear();
            selectedScroll = 0;
            return true;
        }
        if (contains(defaultBounds, mouseX, mouseY)) {
            workingBlockIds.clear();
            workingBlockIds.addAll(defaultBlockIds);
            selectedScroll = 0;
            return true;
        }
        if (contains(cancelBounds, mouseX, mouseY)) {
            closed = true;
            return true;
        }
        if (contains(confirmBounds, mouseX, mouseY)) {
            closed = true;
            if (onConfirm != null) {
                onConfirm.accept(String.join(",", workingBlockIds));
            }
            return true;
        }
        if (catalogBounds != null && catalogBounds.contains(mouseX, mouseY)) {
            ModernMainLayout.Rect listBounds = catalogListBounds();
            if (listBounds.contains(mouseX, mouseY)) {
                int row = (mouseY - listBounds.y) / ROW_HEIGHT + catalogScroll;
                if (row >= 0 && row < filteredCatalog.size()) {
                    toggleBlock(filteredCatalog.get(row).id);
                }
                return true;
            }
        }
        if (selectedBounds != null && selectedBounds.contains(mouseX, mouseY)) {
            ModernMainLayout.Rect listBounds = selectedListBounds();
            if (listBounds.contains(mouseX, mouseY)) {
                int row = (mouseY - listBounds.y) / ROW_HEIGHT + selectedScroll;
                if (row >= 0 && row < workingBlockIds.size()) {
                    workingBlockIds.remove(row);
                    selectedScroll = clamp(selectedScroll, 0, Math.max(0, workingBlockIds.size() - 1));
                }
                return true;
            }
        }
        return modalBounds == null || modalBounds.contains(mouseX, mouseY);
    }

    void updateScreen() {
        if (searchField != null) {
            searchField.updateCursorCounter();
            String next = safe(searchField.getText());
            if (!next.equals(searchText)) {
                searchText = next;
                refreshCatalog();
            }
        }
    }

    private void toggleBlock(String id) {
        if (!workingBlockIds.remove(id)) {
            workingBlockIds.add(id);
        }
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchField.setFocused(false);
                return true;
            }
            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                searchText = safe(searchField.getText());
                refreshCatalog();
                return true;
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closed = true;
            return true;
        }
        return true;
    }

    boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return true;
        }
        if (catalogListBounds().contains(mouseX, mouseY)) {
            catalogScroll = clamp(catalogScroll + (wheel > 0 ? -3 : 3), 0, catalogMaxScroll);
            return true;
        }
        if (selectedListBounds().contains(mouseX, mouseY)) {
            selectedScroll = clamp(selectedScroll + (wheel > 0 ? -3 : 3), 0, selectedMaxScroll);
            return true;
        }
        return true;
    }

    boolean isClosed() {
        return closed;
    }

    private ModernMainLayout.Rect catalogListBounds() {
        if (catalogBounds == null || searchBounds == null) {
            return new ModernMainLayout.Rect(0, 0, 0, 0);
        }
        return new ModernMainLayout.Rect(searchBounds.x, searchBounds.bottom() + 6, searchBounds.width,
                Math.max(1, catalogBounds.bottom() - searchBounds.bottom() - 14));
    }

    private ModernMainLayout.Rect selectedListBounds() {
        if (selectedBounds == null) {
            return new ModernMainLayout.Rect(0, 0, 0, 0);
        }
        return new ModernMainLayout.Rect(selectedBounds.x + 8, selectedBounds.y + 44,
                Math.max(1, selectedBounds.width - 16), Math.max(1, selectedBounds.height - 52));
    }

    private BlockEntry findCatalogEntry(String id) {
        for (BlockEntry entry : catalog) {
            if (entry.id.equalsIgnoreCase(id)) {
                return entry;
            }
        }
        return null;
    }

    private static List<String> parseIds(String raw) {
        String value = safe(raw).trim();
        if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String normalized = normalizeBlockId(token);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private static String normalizeBlockId(String raw) {
        String value = safe(raw).trim();
        if (value.isEmpty()) {
            return "";
        }
        Block block = BlockDisplayLookup.findBlockByUserInput(value);
        return block == null ? "" : BlockUtils.blockToString(block);
    }

    private static String normalize(String value) {
        String stripped = TextFormatting.getTextWithoutFormattingCodes(safe(value));
        return (stripped == null ? safe(value) : stripped).toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private void drawButton(ModernMainLayout.Rect bounds, String label, boolean primary, boolean hovered) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                primary ? (hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT)
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                primary ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, Math.max(8, bounds.width - 12));
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clip, int scroll, int maxScroll,
            int visibleItems, int totalItems, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (clip == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(clip, scroll, maxScroll, Math.max(1, visibleItems), Math.max(visibleItems, totalItems), mouseX, mouseY,
                setter);
    }

    boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && catalogScrollbar.isDragging()) {
            catalogScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && selectedScrollbar.isDragging()) {
            selectedScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (catalogScrollbar.isDragging() || selectedScrollbar.isDragging())) {
            catalogScrollbar.endDrag();
            selectedScrollbar.endDrag();
            return true;
        }
        return false;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
