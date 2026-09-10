package com.zszl.zszlScriptMod.gui.modern.world;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Native modern editor for an isolated snapshot of the current main-hand item. */
public final class ModernNbtEditorSettingsTab implements ModernSettingsTab {

    private static final int ROW_GAP = 4;
    private static final int WIDE_ROW_HEIGHT = 45;
    private static final int COMPACT_ROW_HEIGHT = 68;

    private static final class EntryDraft {
        private String key;
        private String value;
        private final String type;
        private ModernTextField keyField;
        private ModernTextField valueField;

        private EntryDraft(String key, String value, String type) {
            this.key = key == null ? "" : key;
            this.value = value == null ? "" : value;
            this.type = type == null ? "gui.modern.nbt.u001" : type;
        }
    }

    private static final class RowHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private RowHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private final Minecraft minecraft;
    private final ItemStack originalStack;
    private ItemStack workingStack;
    private NBTTagCompound workingTag;
    private final List<EntryDraft> entries = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();

    private FontRenderer fontRenderer;
    private ModernTextField itemNameField;
    private ModernTextField itemCountField;
    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect itemBounds;
    private ModernMainLayout.Rect listBounds;
    private ModernMainLayout.Rect listClipBounds;
    private ModernMainLayout.Rect itemNameBounds;
    private ModernMainLayout.Rect itemCountBounds;
    private ModernMainLayout.Rect addBounds;
    private ModernMainLayout.Rect resetBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect cancelBounds;
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();
    private int listScrollOffset;
    private int listMaxScrollOffset;
    private ModernMainLayout.Rect compactViewport;
    private final ModernHoverScrollbar compactScrollbar = new ModernHoverScrollbar();
    private int compactScroll;
    private int compactMaxScroll;
    private boolean initialized;
    private boolean dirty;
    private String statusMessage = "";
    private long statusMessageUntil;
    private int lastMouseX;
    private int lastMouseY;

    public ModernNbtEditorSettingsTab(Minecraft minecraft) {
        this.minecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
        this.originalStack = ModernNbtSupport.copyMainHand(this.minecraft);
        this.workingStack = ModernNbtSupport.copyStack(this.originalStack);
        this.workingTag = ModernNbtSupport.copyTag(this.workingStack);
        loadEntries();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        this.fontRenderer = fontRenderer;
        itemNameField = createField(96);
        itemCountField = createField(8);
        rebuildFields();
        bindItemFields();
        initialized = true;
    }

    private ModernTextField createField(int maxLength) {
        ModernTextField field = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(maxLength);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    private void loadEntries() {
        entries.clear();
        if (workingTag == null) {
            return;
        }
        List<String> keys = new ArrayList<>(workingTag.getKeySet());
        java.util.Collections.sort(keys);
        for (String key : keys) {
            NBTBase tag = workingTag.getTag(key);
            entries.add(new EntryDraft(key, ModernNbtSupport.tagToString(tag), ModernNbtSupport.typeName(tag)));
        }
    }

    private void rebuildFields() {
        for (EntryDraft entry : entries) {
            entry.keyField = createField(256);
            entry.valueField = createField(32767);
            entry.keyField.setText(entry.key);
            entry.valueField.setText(entry.value);
        }
    }

    private void bindItemFields() {
        if (itemNameField == null || itemCountField == null) {
            return;
        }
        itemNameField.setText(workingStack == null || workingStack.isEmpty() ? "" : workingStack.getDisplayName());
        itemCountField.setText(String.valueOf(workingStack == null ? 1 : workingStack.getCount()));
        clearFieldFocus();
    }

    @Override
    public void updateScreen() {
        if (itemNameField != null && itemNameField.getVisible()) {
            itemNameField.updateCursorCounter();
        }
        if (itemCountField != null && itemCountField.getVisible()) {
            itemCountField.updateCursorCounter();
        }
        for (EntryDraft entry : entries) {
            if (entry.keyField != null && entry.keyField.getVisible()) {
                entry.keyField.updateCursorCounter();
            }
            if (entry.valueField != null && entry.valueField.getVisible()) {
                entry.valueField.updateCursorCounter();
            }
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = bounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : bounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        panelBounds = safePanel(contentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        drawHeader(mouseX, mouseY);
        boolean compact = panelBounds.height < 260 || panelBounds.width < 520;
        if (compact) {
            layoutCompactBody();
            ModernUiRenderer.beginClip(compactViewport);
            drawItemCard(mouseX, mouseY);
            drawActionButton(addBounds, "gui.modern.nbt.u002", false, true, mouseX, mouseY);
            drawActionButton(resetBounds, "gui.modern.nbt.u003", false, true, mouseX, mouseY);
            drawList(mouseX, mouseY);
            drawFooter(mouseX, mouseY);
            ModernUiRenderer.endClip();
            drawCompactScrollbar(mouseX, mouseY);
        } else {
            layoutBody();
            drawItemCard(mouseX, mouseY);
            drawActionButton(addBounds, "gui.modern.nbt.u002", false, true, mouseX, mouseY);
            drawActionButton(resetBounds, "gui.modern.nbt.u003", false, true, mouseX, mouseY);
            drawList(mouseX, mouseY);
            drawFooter(mouseX, mouseY);
            compactViewport = null;
            compactMaxScroll = 0;
            compactScrollbar.idle();
            compactScrollbar.endDrag();
        }
    }

    private void drawHeader(int mouseX, int mouseY) {
        int iconX = panelBounds.x + 13;
        int iconY = panelBounds.y + 11;
        if (!workingStack.isEmpty()) {
            PlayerEquipmentRenderSupport.drawStack(minecraft, fontRenderer, workingStack, iconX, iconY);
        } else {
            ModernUiRenderer.drawStatusDot(iconX + 5, iconY + 5, ModernUiRenderer.MUTED_TEXT);
        }
        int textX = panelBounds.x + 38;
        int textWidth = Math.max(40, panelBounds.width - 54);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u004", textX, panelBounds.y + 9, ModernUiRenderer.TEXT,
                textWidth);
        ModernUiRenderer.drawText(fontRenderer, workingStack.isEmpty() ? "gui.modern.nbt.u005"
                : tr("gui.modern.nbt.fmt.edit_copy", workingStack.getDisplayName()), textX, panelBounds.y + 25,
                workingStack.isEmpty() ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUBTLE_TEXT, textWidth);

        String status = dirty ? "gui.modern.nbt.u006" : statusMessage != null && !statusMessage.isEmpty()
                && System.currentTimeMillis() < statusMessageUntil ? statusMessage : "gui.modern.nbt.u007";
        int statusWidth = Math.min(142, Math.max(84, panelBounds.width / 3));
        ModernMainLayout.Rect statusBounds = new ModernMainLayout.Rect(panelBounds.right() - statusWidth - 14,
                panelBounds.y + 13, statusWidth, 19);
        ModernUiRenderer.drawSubtlePanel(statusBounds.x, statusBounds.y, statusBounds.width, statusBounds.height, 5,
                dirty ? 0xFF40352A : ModernUiRenderer.SURFACE,
                dirty ? ModernUiRenderer.WARNING : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, status, statusBounds.x + 7,
                statusBounds.y + (statusBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                dirty ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT, statusBounds.width - 14);
        ModernUiRenderer.drawDivider(panelBounds.x + 10, panelBounds.y + 44, Math.max(1, panelBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void layoutBody() {
        int x = panelBounds.x + 10;
        int width = Math.max(1, panelBounds.width - 20);
        itemBounds = new ModernMainLayout.Rect(x, panelBounds.y + 54, width, 62);
        int toolbarY = itemBounds.bottom() + 8;
        int footerY = panelBounds.bottom() - 32;
        listBounds = new ModernMainLayout.Rect(x, toolbarY + 28, width,
                Math.max(1, footerY - toolbarY - 36));

        int actionWidth = Math.min(88, Math.max(58, width / 4));
        addBounds = new ModernMainLayout.Rect(x + width - actionWidth, toolbarY, actionWidth, 22);
        resetBounds = new ModernMainLayout.Rect(x + Math.max(0, width - actionWidth * 2 - 5), toolbarY,
                actionWidth, 22);
        saveBounds = new ModernMainLayout.Rect(x, footerY, Math.min(108, Math.max(64, width / 3)), 22);
        cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + 6, footerY,
                Math.min(90, Math.max(58, width / 4)), 22);
    }

    private void layoutCompactBody() {
        int x = panelBounds.x + 10;
        int width = Math.max(1, panelBounds.width - 20);
        int viewportY = panelBounds.y + 54;
        compactViewport = new ModernMainLayout.Rect(x, viewportY, width,
                Math.max(1, panelBounds.bottom() - viewportY - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        int itemHeight = 62;
        int listHeight = 180;
        int contentHeight = itemHeight + 8 + 28 + listHeight + 8 + 22;
        compactMaxScroll = Math.max(0, contentHeight - compactViewport.height);
        compactScroll = Math.max(0, Math.min(compactScroll, compactMaxScroll));
        int contentY = viewportY - compactScroll;
        itemBounds = new ModernMainLayout.Rect(x, contentY, width, itemHeight);
        int toolbarY = itemBounds.bottom() + 8;
        int actionWidth = Math.max(1, (width - 5) / 2);
        resetBounds = new ModernMainLayout.Rect(x, toolbarY, actionWidth, 22);
        addBounds = new ModernMainLayout.Rect(resetBounds.right() + 5, toolbarY,
                Math.max(1, x + width - resetBounds.right() - 5), 22);
        listBounds = new ModernMainLayout.Rect(x, toolbarY + 28, width, listHeight);
        int footerY = listBounds.bottom() + 8;
        saveBounds = new ModernMainLayout.Rect(x, footerY, Math.min(108, Math.max(64, width / 2)), 22);
        cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + 6, footerY,
                Math.max(1, x + width - saveBounds.right() - 6), 22);
    }

    private void drawItemCard(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(itemBounds.x, itemBounds.y, itemBounds.width, itemBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        if (!workingStack.isEmpty()) {
            PlayerEquipmentRenderSupport.drawStack(minecraft, fontRenderer, workingStack, itemBounds.x + 10,
                    itemBounds.y + 22);
        }
        int textX = itemBounds.x + 38;
        int textWidth = Math.max(20, itemBounds.width - 48);
        boolean compact = textWidth < 260;
        int countWidth = Math.min(82, Math.max(58, textWidth / 4));
        int countX = itemBounds.right() - countWidth;
        int nameWidth = Math.max(24, countX - textX - 8);

        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u008", textX, itemBounds.y + 7, ModernUiRenderer.MUTED_TEXT,
                nameWidth);
        itemNameBounds = new ModernMainLayout.Rect(textX, itemBounds.y + 21, nameWidth, 22);
        drawTextField(itemNameField, itemNameBounds, "gui.modern.nbt.u008", mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u009", countX, itemBounds.y + 7, ModernUiRenderer.MUTED_TEXT,
                countWidth);
        itemCountBounds = new ModernMainLayout.Rect(countX, itemBounds.y + 21, countWidth, 22);
        drawTextField(itemCountField, itemCountBounds, "1", mouseX, mouseY);
    }

    private void drawList(int mouseX, int mouseY) {
        int titleX = listBounds.x;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u010", titleX, listBounds.y - 21, ModernUiRenderer.TEXT,
                Math.max(40, listBounds.width - 100));
        ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.nbt.fmt.items", String.valueOf(entries.size())), listBounds.right() - 48, listBounds.y - 21,
                ModernUiRenderer.MUTED_TEXT, 42);
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);

        int rowHeight = listBounds.width < 470 ? COMPACT_ROW_HEIGHT : WIDE_ROW_HEIGHT;
        listClipBounds = new ModernMainLayout.Rect(listBounds.x + 7, listBounds.y + 7,
                Math.max(1, listBounds.width - 14), Math.max(1, listBounds.height - 14));
        int totalHeight = entries.isEmpty() ? 0 : entries.size() * (rowHeight + ROW_GAP) - ROW_GAP;
        listMaxScrollOffset = Math.max(0, totalHeight - listClipBounds.height);
        listScrollOffset = clamp(listScrollOffset, 0, listMaxScrollOffset);
        rowHits.clear();

        ModernUiRenderer.beginClip(listClipBounds);
        if (entries.isEmpty()) {
            String empty = workingTag == null ? "gui.modern.nbt.u011" : "gui.modern.nbt.u012";
            ModernUiRenderer.drawText(fontRenderer, empty, listClipBounds.x + 10, listClipBounds.y + 16,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, listClipBounds.width - 20));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                EntryDraft entry = entries.get(i);
                int y = listClipBounds.y + i * (rowHeight + ROW_GAP) - listScrollOffset;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(listClipBounds.x, y, ModernHoverScrollbar.contentWidth(listClipBounds.width),
                        rowHeight);
                if (y + rowHeight < listClipBounds.y || y > listClipBounds.bottom()) {
                    entry.keyField.setVisible(false);
                    entry.valueField.setVisible(false);
                    continue;
                }
                boolean hovered = listClipBounds.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : 0xFF151F28,
                        hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(fontRenderer, entry.type, row.x + 9, row.y + 5,
                        ModernUiRenderer.MUTED_TEXT, Math.max(40, row.width - 68));
                ModernMainLayout.Rect removeBounds = new ModernMainLayout.Rect(row.right() - 52, row.y + 3, 44, 18);
                drawSmallButton(removeBounds, "gui.modern.nbt.u013", hovered, mouseX, mouseY);
                if (rowHeight == COMPACT_ROW_HEIGHT) {
                    ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u014", row.x + 8, row.y + 22,
                            ModernUiRenderer.MUTED_TEXT, 30);
                    ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u015", row.x + 8, row.y + 47,
                            ModernUiRenderer.MUTED_TEXT, 20);
                    drawTextField(entry.keyField, new ModernMainLayout.Rect(row.x + 36, row.y + 18,
                            Math.max(20, row.width - 44), 21), "gui.modern.nbt.u014", mouseX, mouseY);
                    drawTextField(entry.valueField, new ModernMainLayout.Rect(row.x + 36, row.y + 43,
                            Math.max(20, row.width - 44), 21), "gui.modern.nbt.u015", mouseX, mouseY);
                } else {
                    int keyWidth = Math.max(80, Math.min(190, (row.width - 72) / 3));
                    int fieldY = row.y + 20;
                    drawTextField(entry.keyField, new ModernMainLayout.Rect(row.x + 8, fieldY, keyWidth, 21), "gui.modern.nbt.u014",
                            mouseX, mouseY);
                    drawTextField(entry.valueField, new ModernMainLayout.Rect(row.x + 16 + keyWidth, fieldY,
                            Math.max(20, row.width - keyWidth - 72), 21), "gui.modern.nbt.u015", mouseX, mouseY);
                }
                rowHits.add(new RowHit(i, row));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(listClipBounds, totalHeight, listScrollOffset, listMaxScrollOffset, mouseX, mouseY);
    }

    private void drawFooter(int mouseX, int mouseY) {
        drawActionButton(saveBounds, "gui.modern.nbt.u016", true, true, mouseX, mouseY);
        drawActionButton(cancelBounds, "gui.modern.nbt.u017", false, true, mouseX, mouseY);
        if (dirty) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.nbt.u018", cancelBounds.right() + 8, cancelBounds.y + 7,
                    ModernUiRenderer.WARNING, Math.max(20, panelBounds.right() - cancelBounds.right() - 18));
        }
    }

    private void drawTextField(ModernTextField field, ModernMainLayout.Rect bounds, String placeholder, int mouseX,
            int mouseY) {
        if (field == null || bounds == null) {
            return;
        }
        boolean visible = listClipBounds == null || intersects(bounds, listClipBounds) || bounds == itemNameBounds
                || bounds == itemCountBounds;
        field.setVisible(visible);
        if (!visible) {
            return;
        }
        boolean focused = field.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        field.x = bounds.x + 6;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 12);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (!focused && field.getText().trim().isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, field.x, field.y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, field.width));
        }
    }

    private void drawSmallButton(ModernMainLayout.Rect bounds, String label, boolean rowHovered, int mouseX,
            int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? 0xFF3D2932 : rowHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                hovered ? 0xFFB95A73 : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 7,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                hovered ? 0xFFFF9BB4 : ModernUiRenderer.SUBTLE_TEXT, bounds.width - 14);
    }

    private void drawActionButton(ModernMainLayout.Rect bounds, String label, boolean primary, boolean enabled,
            int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D24 : primary ? hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE : primary ? ModernUiRenderer.ACCENT
                : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                !enabled ? ModernUiRenderer.MUTED_TEXT : primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT,
                Math.max(12, bounds.width - 12));
    }

    private void drawScrollbar(ModernMainLayout.Rect clipBounds, int contentHeight, int scrollOffset, int maxScroll,
            int mouseX, int mouseY) {
        if (clipBounds == null || maxScroll <= 0) {
            listScrollbar.idle();
            return;
        }
        listScrollbar.draw(clipBounds, scrollOffset, maxScroll, clipBounds.height,
                Math.max(clipBounds.height, contentHeight), mouseX, mouseY, value -> listScrollOffset = value);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (compactScrollbar.beginDrag(mouseX, mouseY) || listScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (contains(itemNameBounds, mouseX, mouseY)) {
            focusField(itemNameField, mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(itemCountBounds, mouseX, mouseY)) {
            focusField(itemCountField, mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(addBounds, mouseX, mouseY)) {
            addEntry();
            return true;
        }
        if (contains(resetBounds, mouseX, mouseY)) {
            resetDraft();
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            saveDraft();
            return true;
        }
        if (contains(cancelBounds, mouseX, mouseY)) {
            resetDraft();
            return true;
        }
        for (int i = rowHits.size() - 1; i >= 0; i--) {
            RowHit hit = rowHits.get(i);
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            int rowHeight = listBounds.width < 470 ? COMPACT_ROW_HEIGHT : WIDE_ROW_HEIGHT;
            int removeX = hit.bounds.right() - 52;
            if (mouseX >= removeX && mouseX < hit.bounds.right() - 8 && mouseY < hit.bounds.y + 22) {
                removeEntry(hit.index);
                return true;
            }
            EntryDraft entry = entries.get(hit.index);
            if (rowHeight == COMPACT_ROW_HEIGHT) {
                if (hit.bounds.y + 18 <= mouseY && mouseY < hit.bounds.y + 41) {
                    focusField(entry.keyField, mouseX, mouseY, mouseButton);
                    return true;
                }
                if (mouseY >= hit.bounds.y + 43 && mouseY < hit.bounds.y + 66) {
                    focusField(entry.valueField, mouseX, mouseY, mouseButton);
                    return true;
                }
            } else {
                int keyWidth = Math.max(80, Math.min(190, (hit.bounds.width - 72) / 3));
                if (mouseX >= hit.bounds.x + 8 && mouseX < hit.bounds.x + 8 + keyWidth
                        && mouseY >= hit.bounds.y + 20 && mouseY < hit.bounds.y + 41) {
                    focusField(entry.keyField, mouseX, mouseY, mouseButton);
                    return true;
                }
                if (mouseX >= hit.bounds.x + 16 + keyWidth && mouseY >= hit.bounds.y + 20
                        && mouseY < hit.bounds.y + 41) {
                    focusField(entry.valueField, mouseX, mouseY, mouseButton);
                    return true;
                }
            }
        }
        clearFieldFocus();
        return true;
    }

    private void focusField(ModernTextField field, int mouseX, int mouseY, int mouseButton) {
        if (field == null || !field.getVisible()) {
            return;
        }
        clearFieldFocus();
        field.setFocused(true);
        field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void addEntry() {
        syncFields();
        if (workingTag == null) {
            workingTag = new NBTTagCompound();
        }
        entries.add(new EntryDraft("new_key", "\"new_value\"", "String"));
        rebuildFields();
        dirty = true;
        listScrollOffset = Integer.MAX_VALUE;
        showStatus("gui.modern.nbt.u019");
    }

    private void removeEntry(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        syncFields();
        entries.remove(index);
        rebuildFields();
        dirty = true;
        showStatus("gui.modern.nbt.u020");
    }

    private void resetDraft() {
        workingStack = ModernNbtSupport.copyStack(originalStack);
        workingTag = ModernNbtSupport.copyTag(workingStack);
        loadEntries();
        rebuildFields();
        bindItemFields();
        dirty = false;
        listScrollOffset = 0;
        listScrollbar.endDrag();
        compactScroll = 0;
        compactScrollbar.endDrag();
        showStatus("gui.modern.nbt.u021");
    }

    @Override
    public void save() {
        saveDraft();
    }

    private void saveDraft() {
        if (workingStack == null || workingStack.isEmpty()) {
            showStatus("gui.modern.nbt.u005");
            return;
        }
        syncFields();
        NBTTagCompound rebuilt = new NBTTagCompound();
        for (EntryDraft entry : entries) {
            String key = entry.key == null ? "" : entry.key.trim();
            if (key.isEmpty()) {
                continue;
            }
            String value = entry.value == null ? "" : entry.value;
            try {
                rebuilt.setTag(key, JsonToNBT.getTagFromJson(value));
            } catch (Exception ignored) {
                rebuilt.setTag(key, new NBTTagString(value));
            }
        }
        workingTag = rebuilt;
        workingStack.setTagCompound(workingTag);
        workingStack.setStackDisplayName(itemNameField == null ? workingStack.getDisplayName()
                : itemNameField.getText());
        Integer count = parseCount();
        if (count == null) {
            showStatus("gui.modern.nbt.u022");
            return;
        }
        workingStack.setCount(count.intValue());
        dirty = false;
        showStatus("gui.modern.nbt.u023");
    }

    private Integer parseCount() {
        try {
            int value = Integer.parseInt(itemCountField == null ? "1" : itemCountField.getText().trim());
            return value < 1 || value > 64 ? null : Integer.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void syncFields() {
        if (itemNameField != null && workingStack != null && itemNameField.getText() != null
                && !itemNameField.getText().trim().isEmpty()) {
            workingStack.setStackDisplayName(itemNameField.getText());
        }
        for (EntryDraft entry : entries) {
            if (entry.keyField != null) {
                entry.key = entry.keyField.getText();
            }
            if (entry.valueField != null) {
                entry.value = entry.valueField.getText();
            }
        }
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && listScrollbar.isDragging()) {
            listScrollbar.endDrag();
            return true;
        }
        for (ModernTextField field : allFields()) {
            if (field != null && field.getVisible() && field.isFocused() && field.textboxKeyTyped(typedChar, keyCode)) {
                dirty = true;
                return true;
            }
        }
        if (keyCode == Keyboard.KEY_RETURN && hasFocusedField()) {
            saveDraft();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (compactScrollbar.isDragging()) {
            compactScrollbar.endDrag();
            return true;
        }
        if (listScrollbar.isDragging()) {
            listScrollbar.endDrag();
            return true;
        }
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
        if (listClipBounds != null && listClipBounds.contains(mouseX, mouseY) && listMaxScrollOffset > 0) {
            int previous = listScrollOffset;
            listScrollOffset = clamp(listScrollOffset + (wheel > 0 ? -45 : 45), 0, listMaxScrollOffset);
            return previous != listScrollOffset;
        }
        if (compactViewport != null && compactViewport.contains(mouseX, mouseY) && compactMaxScroll > 0) {
            int previous = compactScroll;
            compactScroll = clamp(compactScroll + (wheel > 0 ? -32 : 32), 0, compactMaxScroll);
            return previous != compactScroll;
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && compactScrollbar.isDragging()) {
            compactScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && listScrollbar.isDragging()) {
            listScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && compactScrollbar.isDragging()) {
            compactScrollbar.endDrag();
            return true;
        }
        if (state == 0 && listScrollbar.isDragging()) {
            listScrollbar.endDrag();
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
        resetDraft();
        statusMessage = "";
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    private boolean hasFocusedField() {
        for (ModernTextField field : allFields()) {
            if (field != null && field.isFocused()) {
                return true;
            }
        }
        return false;
    }

    private void clearFieldFocus() {
        for (ModernTextField field : allFields()) {
            if (field != null) {
                field.setFocused(false);
            }
        }
    }

    private List<ModernTextField> allFields() {
        List<ModernTextField> fields = new ArrayList<>();
        fields.add(itemNameField);
        fields.add(itemCountField);
        for (EntryDraft entry : entries) {
            fields.add(entry.keyField);
            fields.add(entry.valueField);
        }
        return fields;
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2200L;
    }

    private static ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        int inset = Math.min(12, Math.max(3, Math.min(bounds.width, bounds.height) / 10));
        return new ModernMainLayout.Rect(bounds.x + inset, bounds.y + inset,
                Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private void drawCompactScrollbar(int mouseX, int mouseY) {
        if (compactViewport == null || compactMaxScroll <= 0) {
            compactScrollbar.idle();
            return;
        }
        compactScrollbar.draw(compactViewport, compactScroll, compactMaxScroll, compactViewport.height,
                compactViewport.height + compactMaxScroll, mouseX, mouseY, value -> compactScroll = value);
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
