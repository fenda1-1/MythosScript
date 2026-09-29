package com.mythos.mythosScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;

import com.mythos.mythosScriptMod.gui.modern.components.ModernTextField;
import com.mythos.mythosScriptMod.gui.modern.form.ModernFormWidget;
import com.mythos.mythosScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BlockDisplayLookup;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BlockUtils;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

/** Visual block picker for the X-ray visible-block list. */
public final class ModernXrayBlockListWidget implements ModernFormWidget {

    private static final int ROW_HEIGHT = 28;
    private static final int MAX_CATALOG_ENTRIES = 4096;

    private final List<BlockEntry> catalog = new ArrayList<BlockEntry>();
    private final List<BlockEntry> filteredCatalog = new ArrayList<BlockEntry>();
    private final List<String> workingBlockIds = new ArrayList<String>();
    private final List<String> openedBlockIds = new ArrayList<String>();
    private final ModernHoverScrollbar catalogScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar selectedScrollbar = new ModernHoverScrollbar();

    private ModernTextField searchField;
    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect hostBounds;
    private ModernMainLayout.Rect catalogBounds;
    private ModernMainLayout.Rect selectedBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect catalogListBounds;
    private ModernMainLayout.Rect selectedListBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect defaultBounds;
    private String searchText = "";
    private int catalogScroll;
    private int catalogMaxScroll;
    private int selectedScroll;
    private int selectedMaxScroll;
    private boolean initialized;

    @Override
    public void ensureInitialized(FontRenderer font) {
        if (initialized) {
            return;
        }
        fontRenderer = font;
        searchField = new ModernTextField(0, font, 0, 0, 1, 18);
        searchField.setMaxStringLength(128);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(ModernUiRenderer.TEXT);
        searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        searchField.setVisible(false);
        loadCatalog();
        workingBlockIds.clear();
        workingBlockIds.addAll(RenderFeatureManager.getXrayVisibleBlockIds());
        openedBlockIds.clear();
        openedBlockIds.addAll(workingBlockIds);
        refreshCatalog();
        initialized = true;
    }

    @Override
    public void draw(FontRenderer font, ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        ensureInitialized(font);
        fontRenderer = font;
        hostBounds = rect;
        if (rect == null || rect.width < 40 || rect.height < 40) {
            return;
        }
        int innerX = rect.x + 8;
        int innerWidth = Math.max(1, rect.width - 16);
        int contentY = rect.y + 8;
        int buttonHeight = 22;
        int contentHeight = Math.max(60, rect.height - buttonHeight - 22);
        int gap = 8;
        int leftWidth = Math.max(1, (innerWidth - gap) * 3 / 5);
        int rightWidth = Math.max(1, innerWidth - gap - leftWidth);
        catalogBounds = new ModernMainLayout.Rect(innerX, contentY, leftWidth, contentHeight);
        selectedBounds = new ModernMainLayout.Rect(catalogBounds.right() + gap, contentY, rightWidth, contentHeight);
        drawCatalog(mouseX, mouseY);
        drawSelected(mouseX, mouseY);
        int buttonY = rect.bottom() - buttonHeight - 6;
        clearBounds = new ModernMainLayout.Rect(innerX, buttonY, 72, buttonHeight);
        defaultBounds = new ModernMainLayout.Rect(clearBounds.right() + 6, buttonY, 72, buttonHeight);
        drawButton(clearBounds, "清空", clearBounds.contains(mouseX, mouseY));
        drawButton(defaultBounds, "默认", defaultBounds.contains(mouseX, mouseY));
        ModernUiRenderer.drawText(font, workingBlockIds.size() + " 项", defaultBounds.right() + 10,
                buttonY + 6, ModernUiRenderer.MUTED_TEXT, Math.max(20, rect.right() - defaultBounds.right() - 18));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || hostBounds == null) {
            return false;
        }
        if (catalogScrollbar.beginDrag(mouseX, mouseY) || selectedScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (contains(searchBounds, mouseX, mouseY)) {
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            workingBlockIds.clear();
            selectedScroll = 0;
            apply();
            return true;
        }
        if (contains(defaultBounds, mouseX, mouseY)) {
            RenderFeatureManager.resetXrayVisibleBlocksWithoutSave();
            workingBlockIds.clear();
            workingBlockIds.addAll(RenderFeatureManager.getXrayVisibleBlockIds());
            selectedScroll = 0;
            return true;
        }
        if (catalogListBounds != null && catalogListBounds.contains(mouseX, mouseY)) {
            int row = (mouseY - catalogListBounds.y) / ROW_HEIGHT + catalogScroll;
            if (row >= 0 && row < filteredCatalog.size()) {
                toggle(filteredCatalog.get(row).id);
            }
            return true;
        }
        if (selectedListBounds != null && selectedListBounds.contains(mouseX, mouseY)) {
            int row = (mouseY - selectedListBounds.y) / ROW_HEIGHT + selectedScroll;
            if (row >= 0 && row < workingBlockIds.size()) {
                workingBlockIds.remove(row);
                selectedScroll = clamp(selectedScroll, 0, Math.max(0, workingBlockIds.size() - 1));
                apply();
            }
            return true;
        }
        return hostBounds.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
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

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (catalogScrollbar.isDragging() || selectedScrollbar.isDragging())) {
            catalogScrollbar.endDrag();
            selectedScrollbar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public void updateScreen() {
        if (searchField == null) {
            return;
        }
        searchField.updateCursorCounter();
        String next = safe(searchField.getText());
        if (!next.equals(searchText)) {
            searchText = next;
            refreshCatalog();
        }
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
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
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        if (catalogListBounds != null && catalogListBounds.contains(mouseX, mouseY)) {
            catalogScroll = clamp(catalogScroll + (wheel > 0 ? -3 : 3), 0, catalogMaxScroll);
            return true;
        }
        if (selectedListBounds != null && selectedListBounds.contains(mouseX, mouseY)) {
            selectedScroll = clamp(selectedScroll + (wheel > 0 ? -3 : 3), 0, selectedMaxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean isTextInputFocused() {
        return searchField != null && searchField.isFocused();
    }

    @Override
    public int height(int width, int availableHeight) {
        return Math.max(220, Math.min(420, availableHeight));
    }

    @Override
    public Object snapshot() {
        return new ArrayList<String>(openedBlockIds);
    }

    @Override
    public boolean commit() {
        apply();
        openedBlockIds.clear();
        openedBlockIds.addAll(workingBlockIds);
        return true;
    }

    @Override
    public void discardDraft() {
        workingBlockIds.clear();
        workingBlockIds.addAll(openedBlockIds);
        RenderFeatureManager.setXrayVisibleBlocks(workingBlockIds);
    }

    private void apply() {
        RenderFeatureManager.setXrayVisibleBlocks(workingBlockIds);
    }

    private void toggle(String id) {
        if (!workingBlockIds.remove(id)) {
            workingBlockIds.add(id);
        }
        apply();
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
                if (id == null || id.trim().isEmpty() || stack.isEmpty()) {
                    continue;
                }
                String displayName = stack.getDisplayName();
                catalog.add(new BlockEntry(id, displayName == null || displayName.isEmpty() ? id : displayName, block));
            } catch (Throwable ignored) {
            }
        }
        catalog.sort((first, second) -> first.id.compareToIgnoreCase(second.id));
    }

    private void refreshCatalog() {
        filteredCatalog.clear();
        String query = normalize(searchText);
        for (BlockEntry entry : catalog) {
            if (query.isEmpty() || normalize(entry.id).contains(query) || normalize(entry.displayName).contains(query)) {
                filteredCatalog.add(entry);
            }
        }
        catalogScroll = 0;
    }

    private void drawCatalog(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(catalogBounds.x, catalogBounds.y, catalogBounds.width, catalogBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int innerX = catalogBounds.x + 8;
        int innerWidth = Math.max(1, catalogBounds.width - 16);
        ModernUiRenderer.drawText(fontRenderer, "全部方块", innerX, catalogBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(40, innerWidth));
        searchBounds = new ModernMainLayout.Rect(innerX, catalogBounds.y + 25, innerWidth, 24);
        drawSearch(mouseX, mouseY);
        catalogListBounds = new ModernMainLayout.Rect(innerX, searchBounds.bottom() + 6, innerWidth,
                Math.max(1, catalogBounds.bottom() - searchBounds.bottom() - 14));
        int visibleRows = Math.max(1, catalogListBounds.height / ROW_HEIGHT);
        catalogMaxScroll = Math.max(0, filteredCatalog.size() - visibleRows);
        catalogScroll = clamp(catalogScroll, 0, catalogMaxScroll);
        ModernUiRenderer.beginClip(catalogListBounds);
        int end = Math.min(filteredCatalog.size(), catalogScroll + visibleRows + 1);
        for (int index = catalogScroll; index < end; index++) {
            BlockEntry entry = filteredCatalog.get(index);
            int rowY = catalogListBounds.y + (index - catalogScroll) * ROW_HEIGHT;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(catalogListBounds.x, rowY,
                    ModernHoverScrollbar.contentWidth(catalogListBounds.width), ROW_HEIGHT - 2);
            boolean selected = containsId(entry.id);
            boolean hovered = row.contains(mouseX, mouseY) && catalogListBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.BORDER : 0x00111111);
            drawIcon(entry.block, row.x + 5, row.y + 5);
            ModernUiRenderer.drawText(fontRenderer, entry.displayName, row.x + 31, row.y + 4, ModernUiRenderer.TEXT,
                    Math.max(30, row.width - 42));
            ModernUiRenderer.drawText(fontRenderer, entry.id, row.x + 31, row.y + 16, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, row.width - 42));
        }
        if (filteredCatalog.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "没有匹配的方块", catalogListBounds.x + 10, catalogListBounds.y + 12,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, catalogListBounds.width - 20));
        }
        ModernUiRenderer.endClip();
        catalogScrollbar.draw(catalogListBounds, catalogScroll, catalogMaxScroll, visibleRows, filteredCatalog.size(),
                mouseX, mouseY, value -> catalogScroll = value);
    }

    private void drawSelected(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(selectedBounds.x, selectedBounds.y, selectedBounds.width, selectedBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int innerX = selectedBounds.x + 8;
        int innerWidth = Math.max(1, selectedBounds.width - 16);
        ModernUiRenderer.drawText(fontRenderer, "透视列表", innerX, selectedBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(40, innerWidth));
        ModernUiRenderer.drawText(fontRenderer, "点击移除", innerX, selectedBounds.y + 24, ModernUiRenderer.MUTED_TEXT,
                Math.max(40, innerWidth));
        selectedListBounds = new ModernMainLayout.Rect(innerX, selectedBounds.y + 44, innerWidth,
                Math.max(1, selectedBounds.bottom() - selectedBounds.y - 52));
        int visibleRows = Math.max(1, selectedListBounds.height / ROW_HEIGHT);
        selectedMaxScroll = Math.max(0, workingBlockIds.size() - visibleRows);
        selectedScroll = clamp(selectedScroll, 0, selectedMaxScroll);
        ModernUiRenderer.beginClip(selectedListBounds);
        int end = Math.min(workingBlockIds.size(), selectedScroll + visibleRows + 1);
        for (int index = selectedScroll; index < end; index++) {
            String id = workingBlockIds.get(index);
            BlockEntry entry = find(id);
            int rowY = selectedListBounds.y + (index - selectedScroll) * ROW_HEIGHT;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(selectedListBounds.x, rowY,
                    ModernHoverScrollbar.contentWidth(selectedListBounds.width), ROW_HEIGHT - 2);
            boolean hovered = row.contains(mouseX, mouseY) && selectedListBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                    hovered ? ModernUiRenderer.BORDER : 0x00111111);
            if (entry != null) {
                drawIcon(entry.block, row.x + 5, row.y + 5);
                ModernUiRenderer.drawText(fontRenderer, entry.displayName, row.x + 31, row.y + 4, ModernUiRenderer.TEXT,
                        Math.max(30, row.width - 42));
            }
            ModernUiRenderer.drawText(fontRenderer, id, row.x + 31, row.y + 16, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, row.width - 42));
        }
        if (workingBlockIds.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "列表为空", selectedListBounds.x + 10, selectedListBounds.y + 14,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, selectedListBounds.width - 20));
        }
        ModernUiRenderer.endClip();
        selectedScrollbar.draw(selectedListBounds, selectedScroll, selectedMaxScroll, visibleRows, workingBlockIds.size(),
                mouseX, mouseY, value -> selectedScroll = value);
    }

    private void drawSearch(int mouseX, int mouseY) {
        boolean focused = searchField.isFocused();
        boolean hovered = searchBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                0xFF101820, focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
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
            ModernUiRenderer.drawText(fontRenderer, "搜索方块", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, searchField.width));
        }
    }

    private void drawIcon(Block block, int x, int y) {
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

    private void drawButton(ModernMainLayout.Rect bounds, String label, boolean hovered) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT,
                Math.max(8, bounds.width - 12));
    }

    private BlockEntry find(String id) {
        for (BlockEntry entry : catalog) {
            if (entry.id.equalsIgnoreCase(id)) {
                return entry;
            }
        }
        Block block = BlockDisplayLookup.findBlockByUserInput(id);
        return block == null ? null : new BlockEntry(id, id, block);
    }

    private boolean containsId(String id) {
        for (String existing : workingBlockIds) {
            if (existing.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String stripped = TextFormatting.getTextWithoutFormattingCodes(safe(value));
        return (stripped == null ? safe(value) : stripped).toLowerCase(Locale.ROOT).replace(" ", "");
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
}
