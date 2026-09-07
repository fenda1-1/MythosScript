package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.path.PathSequenceManager;

import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;
import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Shared category-aware modal picker for a single path sequence. */
public class ModernPathSequencePicker {
    public interface Selection { void accept(String name); }

    private final Selection selection;
    private final Map<String, List<String>> groups = new LinkedHashMap<>();
    private GuiTextField searchField;
    private ModernMainLayout.Rect categoryBounds;
    private ModernMainLayout.Rect sequenceBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect cancelBounds;
    private String selectedCategory = "";
    private String selectedSequence = "";
    private int categoryScroll;
    private int sequenceScroll;
    private boolean open;

    public ModernPathSequencePicker(Selection selection) { this.selection = selection; }

    public void ensureInitialized(FontRenderer font) {
        if (searchField != null) return;
        searchField = new GuiTextField(0, font, 0, 0, 1, 18);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(96);
    }

    public void updateScreen() { if (searchField != null) searchField.updateCursorCounter(); }

    public void open() { open(""); }

    public void open(String currentSequence) {
        selectedSequence = safe(currentSequence);
        categoryBounds = sequenceBounds = clearBounds = cancelBounds = null;
        groups.clear();
        for (PathSequenceManager.PathSequence sequence : PathSequenceManager.getAllVisibleSequences()) {
            if (sequence == null || sequence.getName() == null || sequence.getName().trim().isEmpty()) continue;
            String category = safe(sequence.getCategory()).trim();
            if (category.isEmpty()) category = "默认";
            List<String> names = groups.get(category);
            if (names == null) { names = new ArrayList<>(); groups.put(category, names); }
            names.add(sequence.getName());
        }
        selectedCategory = groups.isEmpty() ? "" : groups.keySet().iterator().next();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            entry.getValue().sort(String.CASE_INSENSITIVE_ORDER);
            if (entry.getValue().contains(selectedSequence)) selectedCategory = entry.getKey();
        }
        categoryScroll = Math.max(0, new ArrayList<>(groups.keySet()).indexOf(selectedCategory));
        sequenceScroll = Math.max(0, filteredNames(selectedCategory, "").indexOf(selectedSequence));
        open = true;
        if (searchField != null) { searchField.setText(""); searchField.setFocused(false); }
    }

    public boolean isOpen() { return open; }

    public void draw(FontRenderer font, ModernMainLayout.Rect host, String title, int mouseX, int mouseY) {
        if (!open || host == null) return;
        ModernUiRenderer.drawBackdropOverlay(host, 0xB80A1016);
        int width = Math.min(430, Math.max(280, host.width - 20));
        int height = Math.min(330, Math.max(230, host.height - 20));
        ModernMainLayout.Rect picker = new ModernMainLayout.Rect(host.x + (host.width - width) / 2,
                host.y + (host.height - height) / 2, width, height);
        ModernUiRenderer.drawPanel(picker.x, picker.y, width, height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, title, picker.x + 14, picker.y + 12, ModernUiRenderer.TEXT, width - 28);
        ModernUiRenderer.drawText(font, "按分组浏览路径序列，点击名称即可应用", picker.x + 14, picker.y + 27,
                ModernUiRenderer.MUTED_TEXT, width - 28);
        ModernMainLayout.Rect search = new ModernMainLayout.Rect(picker.x + 12, picker.y + 47, width - 24, 20);
        searchField.x = search.x + 7; searchField.y = search.y + 5;
        searchField.width = search.width - 14; searchField.height = 12;
        ModernUiRenderer.drawSubtlePanel(search.x, search.y, search.width, search.height, 4,
                ModernUiRenderer.SURFACE, searchField.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if (searchField.getText().isEmpty() && !searchField.isFocused())
            ModernUiRenderer.drawText(font, "搜索序列、分组或拼音", search.x + 7, search.y + 5,
                    ModernUiRenderer.MUTED_TEXT, search.width - 14);
        ModernUiRenderer.drawTextField(searchField);

        int contentY = search.bottom() + 8;
        int contentHeight = height - 108;
        int categoryWidth = Math.min(112, Math.max(86, width / 3));
        categoryBounds = new ModernMainLayout.Rect(picker.x + 12, contentY, categoryWidth, contentHeight);
        sequenceBounds = new ModernMainLayout.Rect(categoryBounds.right() + 8, contentY,
                width - categoryWidth - 32, contentHeight);
        drawCategories(font, mouseX, mouseY);
        drawSequences(font, mouseX, mouseY);
        int buttonWidth = Math.max(1, (width - 36) / 2);
        clearBounds = new ModernMainLayout.Rect(picker.x + 12, picker.bottom() - 31, buttonWidth, 20);
        cancelBounds = new ModernMainLayout.Rect(clearBounds.right() + 8, picker.bottom() - 31,
                width - buttonWidth - 28, 20);
        drawButton(font, clearBounds, "清除序列", mouseX, mouseY);
        drawButton(font, cancelBounds, "取消", mouseX, mouseY);
    }

    private void drawCategories(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(categoryBounds.x, categoryBounds.y, categoryBounds.width,
                categoryBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, "分组", categoryBounds.x + 9, categoryBounds.y + 9,
                ModernUiRenderer.SUBTLE_TEXT, categoryBounds.width - 18);
        List<String> visible = visibleCategories();
        int rowHeight = 25;
        int rows = Math.max(1, (categoryBounds.height - 29) / rowHeight);
        categoryScroll = clamp(categoryScroll, 0, Math.max(0, visible.size() - rows));
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(categoryBounds.x + 3, categoryBounds.y + 27,
                categoryBounds.width - 6, categoryBounds.height - 30);
        ModernUiRenderer.beginClip(clip);
        for (int i = categoryScroll; i < visible.size() && i < categoryScroll + rows; i++) {
            int y = clip.y + (i - categoryScroll) * rowHeight;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(categoryBounds.x + 6, y,
                    categoryBounds.width - 12, 22);
            boolean selected = visible.get(i).equals(selectedCategory), hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, visible.get(i), row.x + 7, row.y + 6,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 14);
        }
        ModernUiRenderer.endClip();
    }

    private void drawSequences(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(sequenceBounds.x, sequenceBounds.y, sequenceBounds.width,
                sequenceBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        String query = query();
        List<String> names = filteredNames(selectedCategory, query);
        ModernUiRenderer.drawText(font, selectedCategory.isEmpty() ? "路径序列" : selectedCategory,
                sequenceBounds.x + 9, sequenceBounds.y + 9, ModernUiRenderer.SUBTLE_TEXT, sequenceBounds.width - 18);
        int rowHeight = 27;
        int rows = Math.max(1, (sequenceBounds.height - 31) / rowHeight);
        sequenceScroll = clamp(sequenceScroll, 0, Math.max(0, names.size() - rows));
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(sequenceBounds.x + 3, sequenceBounds.y + 27,
                sequenceBounds.width - 6, sequenceBounds.height - 30);
        ModernUiRenderer.beginClip(clip);
        for (int i = sequenceScroll; i < names.size() && i < sequenceScroll + rows; i++) {
            int y = clip.y + (i - sequenceScroll) * rowHeight;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(sequenceBounds.x + 6, y,
                    sequenceBounds.width - 12, 24);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean selected = names.get(i).equals(selectedSequence);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, names.get(i), row.x + 8, row.y + 7,
                    ModernUiRenderer.TEXT, row.width - 16);
        }
        ModernUiRenderer.endClip();
        if (names.isEmpty()) ModernUiRenderer.drawText(font, "该分组没有匹配序列", sequenceBounds.x + 10,
                sequenceBounds.y + 38, ModernUiRenderer.MUTED_TEXT, sequenceBounds.width - 20);
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        if (!open) return false;
        if (clearBounds != null && clearBounds.contains(mouseX, mouseY)) {
            if (selection != null) selection.accept(null);
            close(); return true;
        }
        if (cancelBounds != null && cancelBounds.contains(mouseX, mouseY)) { close(); return true; }
        if (searchField != null && contains(new ModernMainLayout.Rect(searchField.x - 7, searchField.y - 5,
                searchField.width + 14, 20), mouseX, mouseY)) {
            searchField.setFocused(true); searchField.mouseClicked(mouseX, mouseY, 0); return true;
        }
        if (searchField != null) searchField.setFocused(false);
        if (categoryBounds != null && categoryBounds.contains(mouseX, mouseY)) {
            if (mouseY < categoryBounds.y + 27) return true;
            List<String> visible = visibleCategories();
            int rows = Math.max(1, (categoryBounds.height - 29) / 25);
            int index = (mouseY - categoryBounds.y - 27) / 25 + categoryScroll;
            if (index >= 0 && index < visible.size() && index < categoryScroll + rows) {
                selectedCategory = visible.get(index); sequenceScroll = 0;
            }
            return true;
        }
        if (sequenceBounds != null && sequenceBounds.contains(mouseX, mouseY)) {
            if (mouseY < sequenceBounds.y + 27) return true;
            int rows = Math.max(1, (sequenceBounds.height - 31) / 27);
            List<String> names = filteredNames(selectedCategory, query());
            int index = (mouseY - sequenceBounds.y - 27) / 27 + sequenceScroll;
            if (index >= sequenceScroll && index < names.size() && index < sequenceScroll + rows
                    && (mouseY - sequenceBounds.y - 27) % 27 < 24) {
                if (selection != null) selection.accept(names.get(index));
                close();
            }
            return true;
        }
        return true;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!open) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) return handleEscape();
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            categoryScroll = 0;
            sequenceScroll = 0;
            visibleCategories();
        }
        return true;
    }

    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (!open || wheel == 0) return false;
        if (categoryBounds != null && categoryBounds.contains(mouseX, mouseY)) {
            int rows = Math.max(1, (categoryBounds.height - 29) / 25);
            categoryScroll = clamp(categoryScroll + (wheel > 0 ? -1 : 1), 0,
                    Math.max(0, visibleCategories().size() - rows));
            return true;
        }
        if (sequenceBounds != null && sequenceBounds.contains(mouseX, mouseY)) {
            int rows = Math.max(1, (sequenceBounds.height - 31) / 27);
            sequenceScroll = clamp(sequenceScroll + (wheel > 0 ? -1 : 1), 0,
                    Math.max(0, filteredNames(selectedCategory, query()).size() - rows));
            return true;
        }
        return false;
    }

    public boolean handleEscape() { if (!open) return false; close(); return true; }
    public boolean isTextInputFocused() { return open && searchField != null && searchField.isFocused(); }

    private List<String> visibleCategories() {
        String query = query();
        if (query.isEmpty()) return new ArrayList<>(groups.keySet());
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (PinyinSearchHelper.matchesNormalized(entry.getKey(), query) || !filteredNames(entry.getKey(), query).isEmpty()) result.add(entry.getKey());
        }
        if (!result.contains(selectedCategory) && !result.isEmpty()) selectedCategory = result.get(0);
        return result;
    }

    private List<String> filteredNames(String category, String query) {
        List<String> source = groups.get(category);
        if (source == null) return Collections.emptyList();
        if (query.isEmpty() || PinyinSearchHelper.matchesNormalized(category, query)) return source;
        List<String> result = new ArrayList<>();
        for (String name : source) if (PinyinSearchHelper.matchesNormalized(name, query)) result.add(name);
        return result;
    }

    private String query() { return searchField == null ? "" : PinyinSearchHelper.normalizeQuery(searchField.getText()); }
    private void close() { open = false; if (searchField != null) searchField.setFocused(false); }
    private static void drawButton(FontRenderer font, ModernMainLayout.Rect bounds, String label, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, bounds.x + 6, bounds.y + 6, ModernUiRenderer.TEXT, bounds.width - 12);
    }
    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) { return bounds != null && bounds.contains(x, y); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
