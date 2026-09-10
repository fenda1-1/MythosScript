package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ExpressionTemplateCard;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.template.ExpressionTemplateCatalog;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;

/**
 * Standalone expression editor used by the modern action editor and feature
 * settings that need the same template, code and preview workflow.
 *
 * <p>The host owns the value being edited. This panel only owns the transient
 * editor state and reports a committed value through {@link CommitListener}.
 * Keeping the model outside the panel lets a rule editor use the exact same
 * independent expression surface without pretending that its value is an
 * action parameter.</p>
 */
public final class ModernExpressionEditorPanel {
    public interface CommitListener {
        void accept(String expression);
    }

    public interface CancelListener {
        void cancel();
    }

    private final ModernExpressionCodeEditor codeEditor = new ModernExpressionCodeEditor();
    private final List<ExpressionTemplateHit> templateHits = new ArrayList<ExpressionTemplateHit>();

    private ModernTextField searchField;
    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect dividerBounds;
    private ModernMainLayout.Rect templateListBounds;
    private ModernMainLayout.Rect previewBounds;
    private ModernMainLayout.Rect codeBounds;
    private ModernMainLayout.Rect cancelBounds;
    private ModernMainLayout.Rect confirmBounds;
    private ModernMainLayout.Rect expandBounds;
    private ModernMainLayout.Rect templateScrollTrack;
    private ModernMainLayout.Rect templateScrollThumb;
    private ModernMainLayout.Rect previewScrollTrack;
    private ModernMainLayout.Rect previewScrollThumb;

    private Collection<PathSequence> sequences = Collections.emptyList();
    private PathSequence currentSequence;
    private int stepIndex = -1;
    private int actionIndex = -1;
    private ExpressionEditorPreview.Mode mode = ExpressionEditorPreview.Mode.VALUE;
    private String title = "gui.modern.path.wb.u007";
    private boolean open;
    private boolean expanded;
    private int templateScroll;
    private int templateMaxScroll;
    private int previewScroll;
    private int previewMaxScroll;
    private boolean draggingTemplateScroll;
    private boolean draggingPreviewScroll;
    private boolean draggingDivider;
    private int templateScrollDragOffset;
    private int previewScrollDragOffset;
    private double splitRatio = 0.38D;
    private long completionRefreshAt;
    private long lastDividerClickAt;
    private int lastDividerClickX;
    private float templateScrollHover;
    private float previewScrollHover;
    private ExpressionTemplateCard selectedTemplate;
    private String errorKey = "";
    private CommitListener commitListener;
    private CancelListener cancelListener;

    public boolean isOpen() {
        return open;
    }

    public void open(FontRenderer renderer, String initialValue, String editorTitle,
            ExpressionEditorPreview.Mode editorMode, Collection<PathSequence> availableSequences,
            PathSequence sequence, int selectedStepIndex, int selectedActionIndex,
            CommitListener onCommit, CancelListener onCancel) {
        fontRenderer = renderer;
        ensureSearchField(renderer);
        title = editorTitle == null || editorTitle.isEmpty() ? "gui.modern.path.wb.u007" : editorTitle;
        mode = editorMode == null ? ExpressionEditorPreview.Mode.VALUE : editorMode;
        sequences = availableSequences == null ? Collections.<PathSequence>emptyList() : availableSequences;
        currentSequence = sequence;
        stepIndex = selectedStepIndex;
        actionIndex = selectedActionIndex;
        commitListener = onCommit;
        cancelListener = onCancel;
        open = true;
        expanded = false;
        templateScroll = 0;
        previewScroll = 0;
        selectedTemplate = null;
        errorKey = "";
        draggingTemplateScroll = false;
        draggingPreviewScroll = false;
        draggingDivider = false;
        searchField.setText("");
        searchField.setFocused(false);
        codeEditor.setText(initialValue == null ? "" : initialValue);
        codeEditor.setFocused(true);
        completionRefreshAt = 0L;
    }

    public void updateScreen() {
        if (!open || searchField == null) {
            return;
        }
        searchField.updateCursorCounter();
    }

    public void draw(FontRenderer renderer, ModernMainLayout.Rect editorBounds, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        fontRenderer = renderer;
        bounds = editorBounds;
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        int gap = 8;
        int splitTotal = Math.max(2, bounds.width - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, splitRatio,
                210, 240, 160, 160);
        splitRatio = split.ratio;
        ModernMainLayout.Rect templates = new ModernMainLayout.Rect(bounds.x, bounds.y,
                split.firstWidth, bounds.height);
        ModernMainLayout.Rect editor = new ModernMainLayout.Rect(templates.right() + gap, bounds.y,
                Math.max(1, split.secondWidth), bounds.height);
        ModernMainLayout.Rect divider = ModernSplitPane.verticalDividerBounds(templates.x, templates.width, gap,
                bounds.y, bounds.height);
        dividerBounds = divider;

        ModernUiRenderer.drawSubtlePanel(templates.x, templates.y, templates.width, templates.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(editor.x, editor.y, editor.width, editor.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernSplitPane.drawVerticalDivider(divider, mouseX, mouseY, draggingDivider);

        ModernUiRenderer.drawText(renderer, "gui.modern.path.wb.u260", templates.x + 12,
                templates.y + 10, ModernUiRenderer.TEXT, templates.width - 24);
        searchField.x = templates.x + 10;
        searchField.y = templates.y + 29;
        searchField.width = Math.max(1, templates.width - 20);
        searchField.height = 22;
        searchField.setVisible(true);
        searchField.setCanLoseFocus(false);
        searchField.setEnableBackgroundDrawing(false);
        ModernUiRenderer.drawSubtlePanel(searchField.x - 1, searchField.y - 1,
                searchField.width + 2, searchField.height + 2, 4,
                ModernUiRenderer.INPUT_SURFACE,
                searchField.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(renderer, "gui.modern.path.wb.u261", searchField.x + 5,
                    searchField.y + 6, ModernUiRenderer.MUTED_TEXT, Math.max(12, searchField.width - 10));
        }

        templateListBounds = new ModernMainLayout.Rect(templates.x + 8, templates.y + 59,
                Math.max(1, templates.width - 16), Math.max(1, templates.height - 67));
        List<ExpressionTemplateCard> cards = filteredTemplates();
        int rowHeight = 48;
        templateMaxScroll = Math.max(0, cards.size() * rowHeight - templateListBounds.height);
        templateScroll = clamp(templateScroll, 0, templateMaxScroll);
        templateHits.clear();
        ModernUiRenderer.beginClip(templateListBounds);
        int cardY = templateListBounds.y - templateScroll;
        for (ExpressionTemplateCard card : cards) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(templateListBounds.x + 2, cardY,
                    ModernHoverScrollbar.contentWidth(templateListBounds.width - 3), 43);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean selected = selectedTemplate != null
                    && selectedTemplate.name.equals(card.name)
                    && selectedTemplate.example.equals(card.example);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                    hovered || selected ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(renderer, card.name, row.x + 9, row.y + 7,
                    hovered || selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 18);
            ModernUiRenderer.drawText(renderer, card.example, row.x + 9, row.y + 23,
                    ModernUiRenderer.ACCENT, row.width - 18);
            if (row.bottom() > templateListBounds.y && row.y < templateListBounds.bottom()) {
                templateHits.add(new ExpressionTemplateHit(card, row));
            }
            cardY += rowHeight;
        }
        ModernUiRenderer.endClip();
        drawScrollbar(templateListBounds, templateScroll, templateMaxScroll, true, mouseX, mouseY);

        int x = editor.x + 16;
        int width = Math.max(1, editor.width - 32);
        ModernUiRenderer.drawText(renderer, title, x, editor.y + 13, ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(renderer, mode == ExpressionEditorPreview.Mode.ITEM_FILTER
                ? "gui.modern.path.wb.u262" : "gui.modern.path.wb.u263", x, editor.y + 31,
                ModernUiRenderer.MUTED_TEXT, width);
        ModernUiRenderer.drawText(renderer, "gui.modern.path.wb.u264", x, editor.y + 61,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(40, width - 88));
        expandBounds = new ModernMainLayout.Rect(editor.right() - 88, editor.y + 54, 72, 20);
        drawButton(expandBounds, expanded ? "gui.modern.path.wb.u265" : "gui.modern.path.wb.u266",
                false, false, mouseX, mouseY);

        int available = Math.max(120, editor.height - 140);
        int codeHeight = expanded ? Math.max(120, available * 62 / 100)
                : clamp(available * 40 / 100, 108, 220);
        int maxCodeHeight = Math.max(80, editor.height - 190);
        codeHeight = Math.min(codeHeight, maxCodeHeight);
        codeBounds = new ModernMainLayout.Rect(x, editor.y + 78, width, codeHeight);
        long now = System.currentTimeMillis();
        if (now >= completionRefreshAt) {
            codeEditor.setDynamicCompletions(ExpressionEditorPreview.buildCompletions(sequences,
                    currentSequence, stepIndex, actionIndex));
            completionRefreshAt = now + 250L;
        }
        codeEditor.draw(renderer, codeBounds, true, mouseX, mouseY);
        if (!safe(codeEditor.getHoveredTooltip()).isEmpty()) {
            // The host reads this through getHoveredTooltip after drawing.
            hoveredTooltip = codeEditor.getHoveredTooltip();
        } else {
            hoveredTooltip = "";
        }
        ModernUiRenderer.drawText(renderer, codeEditor.getStatusText(), x,
                codeBounds.bottom() + 4, ModernUiRenderer.MUTED_TEXT, width);

        int helpTop = codeBounds.bottom() + 19;
        int helpHeight = Math.max(54, editor.bottom() - 40 - helpTop);
        previewBounds = new ModernMainLayout.Rect(x, helpTop, width, helpHeight);
        ModernUiRenderer.drawSubtlePanel(previewBounds.x, previewBounds.y, previewBounds.width,
                previewBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        drawPreview(renderer, mouseX, mouseY);

        cancelBounds = new ModernMainLayout.Rect(editor.right() - 132, editor.bottom() - 30, 56, 22);
        confirmBounds = new ModernMainLayout.Rect(editor.right() - 68, editor.bottom() - 30, 56, 22);
        if (!errorKey.isEmpty()) {
            ModernUiRenderer.drawText(renderer, errorKey, x, editor.bottom() - 27,
                    ModernUiRenderer.WARNING, Math.max(40, width - 132));
        }
        drawButton(cancelBounds, "gui.modern.path.wb.u084", false, false, mouseX, mouseY);
        drawButton(confirmBounds, "gui.modern.path.wb.u267", true, false, mouseX, mouseY);
    }

    private String hoveredTooltip = "";

    private void drawPreview(FontRenderer renderer, int mouseX, int mouseY) {
        String expression = codeEditor.getText();
        List<ExpressionEditorPreview.Line> report = ExpressionEditorPreview.build(expression, mode,
                sequences, currentSequence, stepIndex, actionIndex, selectedTemplate);
        int lineHeight = 13;
        int contentHeight = 12;
        for (ExpressionEditorPreview.Line line : report) {
            contentHeight += line.text.isEmpty() ? 8 : lineHeight;
        }
        previewMaxScroll = Math.max(0, contentHeight - previewBounds.height);
        previewScroll = clamp(previewScroll, 0, previewMaxScroll);
        ModernUiRenderer.beginClip(previewBounds);
        int y = previewBounds.y + 8 - previewScroll;
        int textWidth = Math.max(20, previewBounds.width - 28);
        for (ExpressionEditorPreview.Line line : report) {
            if (y + lineHeight >= previewBounds.y && y <= previewBounds.bottom()) {
                ModernUiRenderer.drawText(renderer, line.text,
                        previewBounds.x + 10 + line.indent * 10, y, line.color,
                        textWidth - line.indent * 10);
            }
            y += line.text.isEmpty() ? 8 : lineHeight;
        }
        ModernUiRenderer.endClip();
        drawScrollbar(previewBounds, previewScroll, previewMaxScroll, false, mouseX, mouseY);
    }

    private List<ExpressionTemplateCard> filteredTemplates() {
        List<ExpressionTemplateCard> source = mode == ExpressionEditorPreview.Mode.ITEM_FILTER
                ? ExpressionTemplateCatalog.buildItemFilterCards()
                : mode == ExpressionEditorPreview.Mode.BOOLEAN
                        ? ExpressionTemplateCatalog.buildBooleanCards()
                        : ExpressionTemplateCatalog.buildSetVarCards();
        String query = PinyinSearchHelper.normalizeQuery(searchField == null ? "" : searchField.getText());
        if (query.isEmpty()) {
            return source;
        }
        List<ExpressionTemplateCard> result = new ArrayList<ExpressionTemplateCard>();
        for (ExpressionTemplateCard card : source) {
            StringBuilder searchable = new StringBuilder(card.name).append(' ')
                    .append(card.example).append(' ').append(card.description).append(' ')
                    .append(card.format);
            for (String keyword : card.keywords) {
                searchable.append(' ').append(keyword);
            }
            if (PinyinSearchHelper.matchesNormalized(searchable.toString(), query)) {
                result.add(card);
            }
        }
        return result;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!open) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (codeEditor.mouseClicked(mouseX, mouseY, mouseButton)) {
            if (searchField != null) searchField.setFocused(false);
            return true;
        }
        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            startDividerDrag(mouseX);
            return true;
        }
        if (templateScrollTrack != null && templateScrollTrack.contains(mouseX, mouseY)) {
            draggingTemplateScroll = true;
            templateScrollDragOffset = templateScrollThumb == null ? 0
                    : templateScrollThumb.height / 2;
            applyTemplateScroll(mouseY);
            return true;
        }
        if (previewScrollTrack != null && previewScrollTrack.contains(mouseX, mouseY)) {
            draggingPreviewScroll = true;
            previewScrollDragOffset = previewScrollThumb == null ? 0
                    : previewScrollThumb.height / 2;
            applyPreviewScroll(mouseY);
            return true;
        }
        if (expandBounds != null && expandBounds.contains(mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }
        if (searchField != null && searchField.getVisible()
                && searchField.x <= mouseX && mouseX < searchField.x + searchField.width
                && searchField.y <= mouseY && mouseY < searchField.y + searchField.height) {
            codeEditor.setFocused(false);
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (cancelBounds != null && cancelBounds.contains(mouseX, mouseY)) {
            cancel();
            return true;
        }
        if (confirmBounds != null && confirmBounds.contains(mouseX, mouseY)) {
            commit();
            return true;
        }
        for (ExpressionTemplateHit hit : templateHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                selectedTemplate = hit.card;
                insertTemplate(hit.card.example);
                return true;
            }
        }
        clearFocus();
        return true;
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!open || clickedMouseButton != 0) {
            return false;
        }
        if (codeEditor.mouseClickMove(mouseX, mouseY, clickedMouseButton)) {
            return true;
        }
        if (draggingDivider && bounds != null) {
            int total = Math.max(2, bounds.width - 8);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - bounds.x, 210, 240, 160, 160);
            splitRatio = split.ratio;
            return true;
        }
        if (draggingTemplateScroll) {
            applyTemplateScroll(mouseY);
            return true;
        }
        if (draggingPreviewScroll) {
            applyPreviewScroll(mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!open) {
            return false;
        }
        if (state == 0 && codeEditor.mouseReleased(mouseX, mouseY)) {
            return true;
        }
        if (state == 0) {
            draggingTemplateScroll = false;
            draggingPreviewScroll = false;
            if (draggingDivider) {
                draggingDivider = false;
                return true;
            }
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!open) {
            return false;
        }
        if (codeEditor.isFocused() && codeEditor.keyTyped(typedChar, keyCode)) {
            errorKey = "";
            return true;
        }
        if (searchField != null && searchField.isFocused()
                && searchField.textboxKeyTyped(typedChar, keyCode)) {
            templateScroll = 0;
            return true;
        }
        return false;
    }

    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (!open || wheel == 0) {
            return false;
        }
        if (codeEditor.mouseWheel(mouseX, mouseY, wheel)) {
            return true;
        }
        if (templateListBounds != null && templateListBounds.contains(mouseX, mouseY)
                && templateMaxScroll > 0) {
            templateScroll = clamp(templateScroll + (wheel > 0 ? -36 : 36), 0, templateMaxScroll);
            return true;
        }
        if (previewBounds != null && previewBounds.contains(mouseX, mouseY)
                && previewMaxScroll > 0) {
            previewScroll = clamp(previewScroll + (wheel > 0 ? -26 : 26), 0, previewMaxScroll);
            return true;
        }
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    public boolean handleEscape() {
        if (!open) {
            return false;
        }
        if (codeEditor.isFocused() && codeEditor.keyTyped('\0', Keyboard.KEY_ESCAPE)) {
            return true;
        }
        cancel();
        return true;
    }

    public boolean isTextInputFocused() {
        return open && (codeEditor.isFocused() || searchField != null && searchField.isFocused());
    }

    public void clearTextInputFocusOutside(int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        if (codeBounds != null && codeBounds.contains(mouseX, mouseY)) {
            return;
        }
        clearFocus();
    }

    public String getHoveredTooltip() {
        return hoveredTooltip == null ? "" : hoveredTooltip;
    }

    private void commit() {
        String value = codeEditor.getText() == null ? "" : codeEditor.getText().trim();
        if (value.isEmpty()) {
            errorKey = "gui.modern.path.wb.u269";
            return;
        }
        CommitListener listener = commitListener;
        closeTransientState();
        if (listener != null) {
            listener.accept(value);
        }
    }

    private void cancel() {
        CancelListener listener = cancelListener;
        closeTransientState();
        if (listener != null) {
            listener.cancel();
        }
    }

    private void closeTransientState() {
        open = false;
        codeEditor.setFocused(false);
        if (searchField != null) {
            searchField.setFocused(false);
            searchField.setVisible(false);
        }
        draggingTemplateScroll = false;
        draggingPreviewScroll = false;
        draggingDivider = false;
        hoveredTooltip = "";
        commitListener = null;
        cancelListener = null;
    }

    private void clearFocus() {
        codeEditor.setFocused(false);
        if (searchField != null) searchField.setFocused(false);
    }

    private void insertTemplate(String example) {
        String snippet = example == null ? "" : example;
        if (codeEditor.getText().trim().isEmpty()) {
            codeEditor.setText(snippet);
        } else {
            codeEditor.insertText(snippet);
        }
        codeEditor.setFocused(true);
        if (searchField != null) searchField.setFocused(false);
    }

    private void ensureSearchField(FontRenderer renderer) {
        if (searchField != null) {
            return;
        }
        searchField = new ModernTextField(0, renderer, 0, 0, 1, 18);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(256);
        searchField.setTextColor(ModernUiRenderer.TEXT);
        searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        searchField.setVisible(false);
    }

    private void drawButton(ModernMainLayout.Rect rect, String label, boolean primary, boolean danger,
            int mouseX, int mouseY) {
        if (rect == null) return;
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (primary) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.ACCENT;
            text = ModernUiRenderer.TEXT;
        } else if (danger) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.DANGER;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.DANGER;
            text = ModernUiRenderer.TEXT;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        String value = ModernFormI18n.tr(label);
        int width = fontRenderer == null ? 0 : fontRenderer.getStringWidth(value);
        int x = rect.x + Math.max(4, (rect.width - width) / 2);
        ModernUiRenderer.drawText(fontRenderer, label, x, rect.y + (rect.height - fontRenderer.FONT_HEIGHT) / 2,
                ModernUiRenderer.readableText(text, fill), Math.max(1, rect.width - 8));
    }

    private void drawScrollbar(ModernMainLayout.Rect list, int scroll, int maximum, boolean template,
            int mouseX, int mouseY) {
        if (maximum <= 0 || list == null || list.height <= 0) {
            if (template) {
                templateScrollTrack = null;
                templateScrollThumb = null;
            } else {
                previewScrollTrack = null;
                previewScrollThumb = null;
            }
            return;
        }
        int unit = template ? 48 : 13;
        int visible = Math.max(1, list.height / unit);
        int total = Math.max(visible, visible + maximum / unit);
        int thumbHeight = Math.min(list.height, Math.max(18, list.height * visible / Math.max(visible, total)));
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * scroll / Math.max(1, maximum);
        ModernMainLayout.Rect track = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = (template ? draggingTemplateScroll : draggingPreviewScroll) || track.contains(mouseX, mouseY);
        float hover = template ? templateScrollHover : previewScrollHover;
        hover += ((hovered ? 1.0F : 0.0F) - hover) * 0.28F;
        if (Math.abs((hovered ? 1.0F : 0.0F) - hover) < 0.01F) hover = hovered ? 1.0F : 0.0F;
        int grown = Math.round(4 + 6 * hover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * hover));
        int thumbX = list.right() - grown - 2;
        int trackX = list.right() - trackWidth - 3;
        int thumbColor = hovered ? ModernUiRenderer.ACCENT
                : ModernUiRenderer.lerpRgb(ModernUiRenderer.SUBTLE_TEXT, ModernUiRenderer.ACCENT, hover);
        ModernUiRenderer.drawRoundedRect(trackX, list.y, trackWidth, list.height, 2, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2), thumbColor);
        ModernMainLayout.Rect thumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
        if (template) {
            templateScrollTrack = track;
            templateScrollThumb = thumb;
            templateScrollHover = hover;
        } else {
            previewScrollTrack = track;
            previewScrollThumb = thumb;
            previewScrollHover = hover;
        }
    }

    private void applyTemplateScroll(int mouseY) {
        if (templateScrollTrack == null || templateScrollThumb == null || templateMaxScroll <= 0) return;
        int travel = Math.max(1, templateScrollTrack.height - templateScrollThumb.height);
        int target = clamp(mouseY - templateScrollDragOffset, templateScrollTrack.y,
                templateScrollTrack.y + travel);
        templateScroll = clamp(Math.round((target - templateScrollTrack.y) * templateMaxScroll / (float) travel),
                0, templateMaxScroll);
    }

    private void applyPreviewScroll(int mouseY) {
        if (previewScrollTrack == null || previewScrollThumb == null || previewMaxScroll <= 0) return;
        int travel = Math.max(1, previewScrollTrack.height - previewScrollThumb.height);
        int target = clamp(mouseY - previewScrollDragOffset, previewScrollTrack.y,
                previewScrollTrack.y + travel);
        previewScroll = clamp(Math.round((target - previewScrollTrack.y) * previewMaxScroll / (float) travel),
                0, previewMaxScroll);
    }

    private void startDividerDrag(int mouseX) {
        long now = System.currentTimeMillis();
        if (now - lastDividerClickAt <= 350L && Math.abs(mouseX - lastDividerClickX) <= 4) {
            splitRatio = 0.38D;
            lastDividerClickAt = 0L;
            draggingDivider = false;
            return;
        }
        lastDividerClickAt = now;
        lastDividerClickX = mouseX;
        draggingDivider = true;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class ExpressionTemplateHit {
        private final ExpressionTemplateCard card;
        private final ModernMainLayout.Rect bounds;

        private ExpressionTemplateHit(ExpressionTemplateCard card, ModernMainLayout.Rect bounds) {
            this.card = card;
            this.bounds = bounds;
        }
    }
}
