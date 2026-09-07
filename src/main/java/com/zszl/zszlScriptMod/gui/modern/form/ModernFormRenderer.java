package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/** Draws a form definition and owns only transient Minecraft widget geometry. */
final class ModernFormRenderer {

    static final class ChoiceHit {
        final ModernMainLayout.Rect bounds;
        final ModernFormSettingsTab.ChoiceOption<?> option;

        ChoiceHit(ModernMainLayout.Rect bounds, ModernFormSettingsTab.ChoiceOption<?> option) {
            this.bounds = bounds;
            this.option = option;
        }
    }

    static final class ItemView {
        GuiTextField textField;
        ModernMainLayout.Rect rowBounds;
        ModernMainLayout.Rect controlBounds;
        ModernMainLayout.Rect infoBounds;
        final List<ChoiceHit> choiceHits = new ArrayList<>();

        void clear() {
            rowBounds = null;
            controlBounds = null;
            infoBounds = null;
            choiceHits.clear();
            if (textField != null) {
                textField.setVisible(false);
                textField.setFocused(false);
            }
        }
    }

    private final ModernFormDefinition<?> definition;
    private final Map<ModernFormDefinition.Item, ItemView> views = new IdentityHashMap<>();
    private final List<Integer> sectionOffsets = new ArrayList<>();
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect contentClipBounds;
    private boolean compactHeader;

    void setCompactHeader(boolean compact) {
        compactHeader = compact;
    }

    private ModernMainLayout.Rect headerToggleBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private ModernMainLayout.Rect defaultsBounds;
    private int scrollOffset;
    private int maxScrollOffset;
    private boolean drawingContent;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private boolean statusError;
    private boolean statusWarning;
    private long statusVersion;
    private ModernMainLayout.Rect scrollbarTrackBounds;
    private ModernMainLayout.Rect scrollbarThumbBounds;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final Set<ModernFormDefinition.Item> invalidItems = Collections
            .newSetFromMap(new IdentityHashMap<ModernFormDefinition.Item, Boolean>());
    private ModernFormDefinition.Item pendingConfirmation;
    private long pendingConfirmationUntil;

    ModernFormRenderer(ModernFormDefinition<?> definition) {
        this.definition = definition;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (ModernFormDefinition.Item item : section.getItems()) {
                views.put(item, new ItemView());
            }
        }
    }

    void initialize(FontRenderer fontRenderer) {
        for (ModernFormDefinition.Item item : items()) {
            if (item.getType() == ModernFormDefinition.ItemType.TEXT
                    || item.getType() == ModernFormDefinition.ItemType.INTEGER
                    || item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                ItemView view = view(item);
                if (view.textField == null) {
                    view.textField = createTextField(fontRenderer, item.getMaxLength());
                }
            }
        }
    }

    void updateScreen() {
        for (ItemView view : views.values()) {
            if (view.textField != null) {
                view.textField.updateCursorCounter();
            }
        }
    }

    void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        hoveredTooltip = "";
        panelBounds = buildPanelBounds(contentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        int headerHeight = compactHeader ? 2 : translated(definition.getSubtitle()).isEmpty()
                ? Math.min(38, Math.max(30, panelBounds.height / 7))
                : Math.min(44, Math.max(35, panelBounds.height / 5));
        if (!compactHeader) {
            drawHeader(fontRenderer, headerHeight, mouseX, mouseY);
            ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + headerHeight,
                    Math.max(1, panelBounds.width - 24), ModernUiRenderer.BORDER_SUBTLE);
        }
        int footerHeight = definition.isFooterVisible() ? 33 : 8;
        if (compactHeader && statusIsVisible()) footerHeight += 20;
        contentClipBounds = new ModernMainLayout.Rect(panelBounds.x + 8, panelBounds.y + headerHeight + 6,
                Math.max(1, panelBounds.width - 16), Math.max(1, panelBounds.height - headerHeight - footerHeight - 8));
        int formX = contentClipBounds.x + 7;
        int formWidth = ModernHoverScrollbar.contentWidth(contentClipBounds.width - 7);
        int formHeight = computeFormHeight(fontRenderer, formWidth);
        maxScrollOffset = Math.max(0, formHeight - contentClipBounds.height);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);
        drawingContent = true;
        sectionOffsets.clear();
        ModernUiRenderer.beginClip(contentClipBounds);
        int y = contentClipBounds.y + 2 - scrollOffset;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            sectionOffsets.add(y + scrollOffset);
            y = drawSection(fontRenderer, section, formX, y, formWidth, mouseX, mouseY);
        }
        ModernUiRenderer.endClip();
        drawingContent = false;
        drawScrollbar(mouseX, mouseY);
        if (definition.isFooterVisible()) {
            drawFooter(fontRenderer, mouseX, mouseY);
        }
    }

    ModernFormDefinition<?> getDefinition() {
        return definition;
    }

    List<ModernFormDefinition.Item> items() {
        List<ModernFormDefinition.Item> result = new ArrayList<>();
        for (ModernFormDefinition.Section section : definition.getSections()) {
            result.addAll(section.getItems());
        }
        return result;
    }

    ItemView view(ModernFormDefinition.Item item) {
        return views.get(item);
    }

    ModernMainLayout.Rect getPanelBounds() {
        return panelBounds;
    }

    ModernMainLayout.Rect getContentClipBounds() {
        return contentClipBounds;
    }

    ModernMainLayout.Rect getHeaderToggleBounds() {
        return headerToggleBounds;
    }

    ModernMainLayout.Rect getSaveBounds() {
        return saveBounds;
    }

    ModernMainLayout.Rect getRevertBounds() {
        return revertBounds;
    }

    ModernMainLayout.Rect getDefaultsBounds() {
        return defaultsBounds;
    }

    int getScrollOffset() {
        return scrollOffset;
    }

    int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    String getHoveredTooltip() {
        return hoveredTooltip;
    }

    void showStatus(String message) {
        statusMessage = safe(message);
        statusError = looksLikeError(statusMessage);
        statusWarning = !statusError && looksLikeWarning(statusMessage);
        statusMessageUntil = System.currentTimeMillis() + 2600L;
        statusVersion++;
    }

    void showError(String message) {
        statusMessage = safe(message);
        statusError = true;
        statusWarning = false;
        statusMessageUntil = System.currentTimeMillis() + 3600L;
        statusVersion++;
    }

    boolean hasVisibleStatus() {
        return statusIsVisible();
    }

    long getStatusVersion() {
        return statusVersion;
    }

    ModernMainLayout.Rect getScrollbarTrackBounds() {
        return scrollbarTrackBounds;
    }

    boolean scrollbarContains(int mouseX, int mouseY) {
        return scrollbar.contains(mouseX, mouseY);
    }

    boolean beginScrollbarDrag(int mouseX, int mouseY) {
        return scrollbar.beginDrag(mouseX, mouseY);
    }

    void dragScrollbar(int mouseX, int mouseY) {
        scrollbar.applyDrag(mouseX, mouseY);
    }

    void endScrollbarDrag() {
        scrollbar.endDrag();
    }

    void beginScrollbarDrag(int mouseY) {
        scrollbar.beginDrag(contentClipBounds == null ? 0 : contentClipBounds.right() - 2, mouseY);
    }

    void scrollTo(int mouseY) {
        scrollbar.beginDrag(contentClipBounds == null ? 0 : contentClipBounds.right() - 2, mouseY);
        scrollbar.endDrag();
    }

    void markInvalid(ModernFormDefinition.Item item, boolean invalid) {
        if (item == null) {
            return;
        }
        if (invalid) {
            invalidItems.add(item);
        } else {
            invalidItems.remove(item);
        }
    }

    void clearInvalidItems() {
        invalidItems.clear();
    }

    boolean isInvalid(ModernFormDefinition.Item item) {
        return invalidItems.contains(item);
    }

    void armConfirmation(ModernFormDefinition.Item item) {
        pendingConfirmation = item;
        pendingConfirmationUntil = System.currentTimeMillis() + 4200L;
    }

    boolean isConfirmationPending(ModernFormDefinition.Item item) {
        if (pendingConfirmation != null && System.currentTimeMillis() > pendingConfirmationUntil) {
            pendingConfirmation = null;
        }
        return pendingConfirmation == item;
    }

    boolean clearConfirmation() {
        if (pendingConfirmation == null) {
            return false;
        }
        pendingConfirmation = null;
        pendingConfirmationUntil = 0L;
        return true;
    }

    void syncInputFields() {
        for (ModernFormDefinition.Item item : items()) {
            ItemView view = view(item);
            if (view.textField == null) {
                continue;
            }
            if (item.getType() == ModernFormDefinition.ItemType.TEXT) {
                ModernFormSettingsTab.TextValue value = (ModernFormSettingsTab.TextValue) item.getValue();
                view.textField.setText(value == null ? "" : safe(value.get()));
            } else if (item.getType() == ModernFormDefinition.ItemType.INTEGER) {
                ModernFormSettingsTab.IntValue value = (ModernFormSettingsTab.IntValue) item.getValue();
                view.textField.setText(value == null ? "" : String.valueOf(value.get()));
            } else if (item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                ModernFormSettingsTab.FloatValue value = (ModernFormSettingsTab.FloatValue) item.getValue();
                view.textField.setText(value == null ? "" : formatFloat(value.get()));
            }
        }
        clearTextFieldFocus();
        scrollOffset = 0;
    }

    void clearTextFieldFocus() {
        for (ItemView view : views.values()) {
            if (view.textField != null) {
                view.textField.setFocused(false);
            }
        }
    }

    boolean hasFocusedTextField() {
        for (ItemView view : views.values()) {
            if (view.textField != null && view.textField.isFocused()) {
                return true;
            }
        }
        return false;
    }

    void scrollBy(int amount) {
        scrollOffset = clamp(scrollOffset + amount, 0, maxScrollOffset);
    }

    void scrollToSection(int index) {
        if (contentClipBounds == null || index < 0 || index >= sectionOffsets.size()) {
            return;
        }
        scrollOffset = clamp(sectionOffsets.get(index) - contentClipBounds.y - 2, 0, maxScrollOffset);
    }

    private void drawHeader(FontRenderer fontRenderer, int headerHeight, int mouseX, int mouseY) {
        String title = translated(definition.getTitle());
        String subtitle = translated(definition.getSubtitle());
        int titleX = panelBounds.x + 15;
        int titleY = subtitle.isEmpty() ? panelBounds.y + (headerHeight - fontRenderer.FONT_HEIGHT) / 2
                : panelBounds.y + 9;
        int rightReserve = definition.getHeaderToggle() == null ? 20 : 123;
        int statusReserve = statusIsVisible() ? Math.min(112, fontRenderer.getStringWidth(statusMessage) + 5) : 0;
        int titleWidth = Math.max(32, panelBounds.width - rightReserve - statusReserve - 25);
        ModernUiRenderer.drawText(fontRenderer, title, titleX, titleY, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
                titleY - 1, definition.getHeaderTooltip(), mouseX, mouseY);
        if (!subtitle.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, subtitle, titleX, titleY + 13,
                    ModernUiRenderer.MUTED_TEXT, titleWidth);
        }
        ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
        if (toggle != null) {
            int toggleWidth = 44;
            headerToggleBounds = new ModernMainLayout.Rect(panelBounds.right() - toggleWidth - 15,
                    panelBounds.y + (headerHeight - 16) / 2, toggleWidth, 16);
            boolean hovered = headerToggleBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawToggle(headerToggleBounds.x, headerToggleBounds.y, headerToggleBounds.width,
                    headerToggleBounds.height, toggle.get(), hovered);
            String label = toggle.get() ? translated("gui.modern.form.running")
                    : translated("gui.modern.form.stopped");
            int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
            ModernUiRenderer.drawText(fontRenderer, label, headerToggleBounds.x - 8 - labelWidth,
                    panelBounds.y + (headerHeight - fontRenderer.FONT_HEIGHT) / 2,
                    toggle.get() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, labelWidth);
            drawInfoIcon(headerToggleBounds.x - 15, headerToggleBounds.y + 2,
                    definition.getHeaderToggleTooltip(), mouseX, mouseY);
        } else {
            headerToggleBounds = null;
        }
        if (statusIsVisible()) {
            int maxStatusWidth = Math.max(40, panelBounds.width / 3);
            int textWidth = Math.min(maxStatusWidth - 16, fontRenderer.getStringWidth(statusMessage));
            int width = Math.max(42, textWidth + 23);
            int x = toggle == null ? panelBounds.right() - width - 14 : titleX + titleWidth + 5;
            int y = titleY - 3;
            ModernUiRenderer.drawSubtlePanel(x, y, width, 18, 4,
                    statusError ? 0xFF3A252D : statusWarning ? 0xFF3A3323 : 0xFF1D3A31,
                    statusError ? 0xFF9A5361 : statusWarning ? 0xFF9A7B3E : 0xFF3C8A6B);
            ModernUiRenderer.drawStatusDot(x + 6, y + 6,
                    statusError ? 0xFFE06A78 : statusWarning ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS);
            ModernUiRenderer.drawText(fontRenderer, statusMessage, x + 17, y + 5,
                    statusError ? 0xFFFFB7BF : statusWarning ? 0xFFFFD68A : ModernUiRenderer.SUCCESS,
                    Math.max(20, width - 21));
        }
    }

    private int drawSection(FontRenderer fontRenderer, ModernFormDefinition.Section section, int x, int y, int width,
            int mouseX, int mouseY) {
        if (!sectionHasVisibleItems(section)) {
            clearSectionBounds(section);
            return y;
        }
        String sectionTitle = translated(section.getTitle());
        ModernUiRenderer.drawText(fontRenderer, sectionTitle, x, y, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(32, width - 12));
        drawInfoIcon(x + Math.min(width - 11, fontRenderer.getStringWidth(sectionTitle) + 5), y - 1,
                section.getTooltip(), mouseX, mouseY);
        y += 17;
        for (ModernFormDefinition.Item item : section.getItems()) {
            if (!item.isVisible()) {
                view(item).clear();
                continue;
            }
            int height = itemHeight(fontRenderer, item, width);
            ItemView view = view(item);
            view.rowBounds = new ModernMainLayout.Rect(x, y, width, height);
            drawItem(fontRenderer, item, view, mouseX, mouseY);
            y += height + 5;
        }
        return y + 5;
    }

    private void drawItem(FontRenderer fontRenderer, ModernFormDefinition.Item item, ItemView view, int mouseX,
            int mouseY) {
        ModernMainLayout.Rect row = view.rowBounds;
        boolean enabled = item.isEnabled();
        boolean hovered = row.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D25 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int rowBorder = isInvalid(item) ? 0xFFE06A78 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5, fill, rowBorder);
        if (item.getType() == ModernFormDefinition.ItemType.CHOICE) {
            drawChoiceItem(fontRenderer, item, view, enabled, mouseX, mouseY);
            return;
        }
        boolean compact = usesStackedLayout(item, row.width);
        String itemTitle = translated(item.getTitle());
        int titleY = row.y + (compact ? 5 : (row.height - fontRenderer.FONT_HEIGHT) / 2);
        int titleWidth = compact ? row.width - 20 : labelWidthFor(row.width);
        ModernUiRenderer.drawText(fontRenderer, itemTitle, row.x + 9, titleY,
                enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(24, titleWidth));
        view.infoBounds = drawInfoIcon(row.x + 9 + Math.min(Math.max(0, titleWidth - 12),
                fontRenderer.getStringWidth(itemTitle) + 5), titleY - 1, item.getTooltip(), mouseX, mouseY);
        if (item.getType() == ModernFormDefinition.ItemType.TOGGLE) {
            int toggleWidth = Math.max(28, Math.min(38, row.width / 5));
            view.controlBounds = new ModernMainLayout.Rect(row.right() - toggleWidth - 9,
                    row.y + (row.height - 16) / 2, toggleWidth, 16);
            ModernFormSettingsTab.BooleanValue value = (ModernFormSettingsTab.BooleanValue) item.getValue();
            ModernUiRenderer.drawToggle(view.controlBounds.x, view.controlBounds.y, view.controlBounds.width,
                    view.controlBounds.height, value != null && value.get(), hovered && enabled);
            return;
        }
        view.controlBounds = controlBoundsFor(item, row, compact);
        if (item.getType() == ModernFormDefinition.ItemType.READ_ONLY) {
            drawReadOnlyValue(fontRenderer, item, view.controlBounds, enabled);
        } else if (item.getType() == ModernFormDefinition.ItemType.ACTION) {
            String actionLabel = isConfirmationPending(item) ? translated("gui.modern.form.confirm_again")
                    : translated(item.getButtonLabel());
            drawActionButton(fontRenderer, view.controlBounds, actionLabel, item.getActionStyle(), enabled,
                    mouseX, mouseY);
        } else {
            drawTextInput(fontRenderer, item, view, enabled, mouseX, mouseY);
        }
    }

    private void drawChoiceItem(FontRenderer fontRenderer, ModernFormDefinition.Item item, ItemView view,
            boolean enabled, int mouseX, int mouseY) {
        ModernMainLayout.Rect row = view.rowBounds;
        String itemTitle = translated(item.getTitle());
        ModernUiRenderer.drawText(fontRenderer, itemTitle, row.x + 9, row.y + 5,
                enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(24, row.width - 20));
        view.infoBounds = drawInfoIcon(row.x + 9 + Math.min(Math.max(0, row.width - 32),
                fontRenderer.getStringWidth(itemTitle) + 5), row.y + 4, item.getTooltip(), mouseX, mouseY);
        view.choiceHits.clear();
        if (item.getOptions().isEmpty()) {
            view.controlBounds = null;
            return;
        }
        ModernFormSettingsTab.ChoiceValue<?> value = (ModernFormSettingsTab.ChoiceValue<?>) item.getValue();
        Object selected = value == null ? null : value.get();
        int x = row.x + 9;
        int y = row.y + 18;
        int available = Math.max(1, row.width - 18);
        for (ModernFormSettingsTab.ChoiceOption<?> option : item.getOptions()) {
            String optionLabel = translated(option.getLabel());
            int desired = Math.max(42, fontRenderer.getStringWidth(optionLabel) + 16);
            int optionWidth = Math.min(available, desired);
            if (x > row.x + 9 && x + optionWidth > row.right() - 9) {
                x = row.x + 9;
                y += 21;
            }
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, optionWidth, 18);
            view.choiceHits.add(new ChoiceHit(bounds, option));
            boolean selectedOption = option.matches(selected);
            boolean hovered = bounds.contains(mouseX, mouseY);
            int fill = selectedOption ? ModernUiRenderer.ACCENT_DIM
                    : hovered && enabled ? ModernUiRenderer.SURFACE_PRESSED : 0xFF111A22;
            int border = selectedOption ? ModernUiRenderer.ACCENT : hovered && enabled ? 0xFF617581
                    : ModernUiRenderer.BORDER_SUBTLE;
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
            int color = selectedOption ? ModernUiRenderer.TEXT
                    : enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT;
            ModernUiRenderer.drawText(fontRenderer, optionLabel, bounds.x + 7,
                    bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, color, bounds.width - 12);
            x += optionWidth + 4;
        }
        view.controlBounds = new ModernMainLayout.Rect(row.x + 9, row.y + 18, Math.max(1, row.width - 18),
                Math.max(18, row.bottom() - row.y - 23));
    }

    private void drawTextInput(FontRenderer fontRenderer, ModernFormDefinition.Item item, ItemView view, boolean enabled,
            int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = view.controlBounds;
        boolean focused = view.textField != null && view.textField.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        int border = isInvalid(item) ? 0xFFE06A78 : focused ? ModernUiRenderer.ACCENT
                : hovered && enabled ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820, border);
        if (view.textField == null) {
            return;
        }
        view.textField.setVisible(true);
        view.textField.setEnabled(enabled);
        view.textField.x = bounds.x + 7;
        view.textField.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        view.textField.width = Math.max(1, bounds.width - 14);
        view.textField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(view.textField);
        ModernUiRenderer.drawTextField(view.textField);
        String placeholder = translated(item.getPlaceholder());
        if (!focused && view.textField.getText().trim().isEmpty() && !placeholder.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, bounds.x + 7,
                    bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, bounds.width - 14));
        }
    }

    private void drawReadOnlyValue(FontRenderer fontRenderer, ModernFormDefinition.Item item,
            ModernMainLayout.Rect bounds, boolean enabled) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF111A22,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernFormSettingsTab.ReadOnlyValue value = (ModernFormSettingsTab.ReadOnlyValue) item.getValue();
        String text = value == null ? "" : safe(value.get());
        ModernUiRenderer.drawText(fontRenderer, text, bounds.x + 7,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(1, bounds.width - 14));
    }

    private void drawFooter(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int y = panelBounds.bottom() - 28;
        int count = definition.getStateAdapter().supportsDefaults() ? 3 : 2;
        boolean footerToggle = compactHeader && definition.getHeaderToggle() != null;
        if (footerToggle) count++;
        int gap = 6;
        int available = Math.max(1, panelBounds.width - 28 - gap * (count - 1));
        int baseWidth = Math.max(1, available / count);
        int x = panelBounds.x + 14;
        if (footerToggle) {
            int toggleWidth = Math.min(44, Math.max(1, baseWidth - 8));
            headerToggleBounds = new ModernMainLayout.Rect(x + baseWidth - toggleWidth - 4, y + 3, toggleWidth, 16);
            ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
            ModernUiRenderer.drawToggle(headerToggleBounds.x, headerToggleBounds.y, toggleWidth, 16,
                    toggle.get(), headerToggleBounds.contains(mouseX, mouseY));
            ModernUiRenderer.drawText(fontRenderer, translated(toggle.get() ? "gui.modern.form.running"
                    : "gui.modern.form.stopped"), x + 4, y + 7,
                    toggle.get() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, baseWidth - toggleWidth - 24));
            drawInfoIcon(headerToggleBounds.x - 15, y + 5,
                    definition.getHeaderToggleTooltip(), mouseX, mouseY);
            x += baseWidth + gap;
        }
        if (compactHeader && statusIsVisible()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, panelBounds.x + 14, y - 16,
                    statusError ? ModernUiRenderer.DANGER : statusWarning ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS,
                    Math.max(1, panelBounds.width - 28));
        }
        saveBounds = new ModernMainLayout.Rect(x, y, Math.max(baseWidth, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(fontRenderer, definition.getSaveLabel(), 16)), 22);
        x = saveBounds.right() + gap;
        if (definition.getStateAdapter().supportsDefaults()) {
            defaultsBounds = new ModernMainLayout.Rect(x, y, baseWidth, 22);
            x = defaultsBounds.right() + gap;
        } else {
            defaultsBounds = null;
        }
        revertBounds = new ModernMainLayout.Rect(x, y, Math.max(1, panelBounds.right() - 14 - x), 22);
        drawActionButton(fontRenderer, saveBounds, translated(definition.getSaveLabel()),
                ModernFormSettingsTab.ActionStyle.PRIMARY, true, mouseX, mouseY);
        if (defaultsBounds != null) {
            drawActionButton(fontRenderer, defaultsBounds, translated(definition.getDefaultsLabel()),
                    ModernFormSettingsTab.ActionStyle.SECONDARY, true, mouseX, mouseY);
        }
        drawActionButton(fontRenderer, revertBounds, translated(definition.getRevertLabel()),
                ModernFormSettingsTab.ActionStyle.SECONDARY, true, mouseX, mouseY);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label,
            ModernFormSettingsTab.ActionStyle style, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = ModernUiRenderer.DISABLED_SURFACE;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.DISABLED_TEXT;
        } else if (style == ModernFormSettingsTab.ActionStyle.PRIMARY) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT;
            border = ModernUiRenderer.BORDER;
            text = ModernUiRenderer.TEXT;
        } else if (style == ModernFormSettingsTab.ActionStyle.WARNING) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.WARNING;
            border = ModernUiRenderer.BORDER;
            text = ModernUiRenderer.TEXT;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = ModernUiRenderer.BORDER;
            text = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                ModernUiRenderer.readableText(text, fill), Math.max(1, bounds.width - 8));
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (maxScrollOffset <= 0 || contentClipBounds == null) {
            scrollbar.idle();
            scrollbarTrackBounds = null;
            scrollbarThumbBounds = null;
            return;
        }
        scrollbar.draw(contentClipBounds, scrollOffset, maxScrollOffset, contentClipBounds.height,
                contentClipBounds.height + maxScrollOffset, mouseX, mouseY, value -> scrollOffset = value);
        scrollbarTrackBounds = new ModernMainLayout.Rect(contentClipBounds.right() - 14, contentClipBounds.y, 14,
                contentClipBounds.height);
        scrollbarThumbBounds = scrollbarTrackBounds;
    }

    private int itemHeight(FontRenderer fontRenderer, ModernFormDefinition.Item item, int width) {
        if (item.getType() == ModernFormDefinition.ItemType.CHOICE) {
            int available = Math.max(1, width - 18);
            int x = 0;
            int rows = 1;
            for (ModernFormSettingsTab.ChoiceOption<?> option : item.getOptions()) {
                int optionWidth = Math.min(available,
                        Math.max(42, fontRenderer.getStringWidth(translated(option.getLabel())) + 16));
                if (x > 0 && x + optionWidth > available) {
                    rows++;
                    x = 0;
                }
                x += optionWidth + 4;
            }
            return 23 + rows * 21;
        }
        return usesStackedLayout(item, width) ? 48 : 32;
    }

    private int computeFormHeight(FontRenderer fontRenderer, int width) {
        int height = 2;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            if (!sectionHasVisibleItems(section)) {
                continue;
            }
            height += 17;
            for (ModernFormDefinition.Item item : section.getItems()) {
                if (item.isVisible()) {
                    height += itemHeight(fontRenderer, item, width) + 5;
                }
            }
            height += 5;
        }
        return height;
    }

    private boolean usesStackedLayout(ModernFormDefinition.Item item, int width) {
        return item.getType() != ModernFormDefinition.ItemType.TOGGLE
                && item.getType() != ModernFormDefinition.ItemType.CHOICE && width < 260;
    }

    private int labelWidthFor(int rowWidth) {
        int controlWidth = Math.max(72, Math.min(170, rowWidth / 2));
        return Math.max(24, rowWidth - controlWidth - 28);
    }

    private ModernMainLayout.Rect controlBoundsFor(ModernFormDefinition.Item item, ModernMainLayout.Rect row,
            boolean compact) {
        if (compact) {
            return new ModernMainLayout.Rect(row.x + 9, row.bottom() - 25, Math.max(1, row.width - 18), 21);
        }
        int controlWidth = Math.max(72, Math.min(170, row.width / 2));
        return new ModernMainLayout.Rect(row.right() - controlWidth - 8, row.y + 5, controlWidth, 22);
    }

    private ModernMainLayout.Rect drawInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        tooltip = translated(tooltip);
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return null;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean inViewport = !drawingContent || contentClipBounds == null
                || contains(contentClipBounds, bounds);
        if (!inViewport) {
            return null;
        }
        ModernTooltipSupport.registerInfoIcon(Minecraft.getMinecraft().currentScreen, x, y, 11, 11, tooltip);
        boolean active = bounds.contains(mouseX, mouseY);
        if (active) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, active ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        return bounds;
    }

    private static boolean contains(ModernMainLayout.Rect outer, ModernMainLayout.Rect inner) {
        return outer != null && inner != null && inner.x >= outer.x && inner.y >= outer.y
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect contentBounds) {
        ModernMainLayout.Rect source = contentBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : contentBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 16));
        int insetY = Math.max(6, Math.min(9, source.height / 14));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private boolean sectionHasVisibleItems(ModernFormDefinition.Section section) {
        for (ModernFormDefinition.Item item : section.getItems()) {
            if (item.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void clearSectionBounds(ModernFormDefinition.Section section) {
        for (ModernFormDefinition.Item item : section.getItems()) {
            view(item).clear();
        }
    }

    private boolean statusIsVisible() {
        return !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
    }

    private static boolean looksLikeError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return message.contains("失败") || message.contains("错误") || message.contains("无效")
                || message.contains("不能") || message.contains("不存在") || lower.contains("fail")
                || lower.contains("error") || lower.contains("invalid") || lower.contains("cannot")
                || lower.contains("missing");
    }

    private static boolean looksLikeWarning(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return message.contains("再次") || message.contains("请先") || message.contains("未保存")
                || lower.contains("again") || lower.contains("unsaved") || lower.contains("first");
    }

    private static String translated(String value) {
        return ModernFormI18n.tr(value);
    }

    private static GuiTextField createTextField(FontRenderer fontRenderer, int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(Math.max(1, maxLength));
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        return field;
    }

    private static String formatFloat(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.indexOf('.') >= 0 && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
