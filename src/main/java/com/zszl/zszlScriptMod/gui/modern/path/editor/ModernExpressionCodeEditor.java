package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

/**
 * Self-contained multiline editor for the legacy expression language.
 */
public final class ModernExpressionCodeEditor {
    private static final int MAX_TEXT_LENGTH = 32767;
    private static final int MAX_HISTORY = 128;
    private static final int MAX_VISIBLE_COMPLETIONS = 8;
    private static final int COMPLETION_ROW_HEIGHT = 18;
    private static final String TAB_TEXT = "    ";

    private String text = "";
    private int cursor;
    private int selectionAnchor;
    private boolean focused;
    private boolean enabled = true;

    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(0, 0, 0, 0);
    private ModernMainLayout.Rect viewportBounds = new ModernMainLayout.Rect(0, 0, 0, 0);
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect completionPopupBounds;
    private ModernMainLayout.Rect completionScrollbarTrackBounds;
    private ModernMainLayout.Rect completionScrollbarThumbBounds;

    private int lineHeight = 11;
    private int gutterWidth = 28;
    private int visibleLineCount = 1;
    private int verticalScroll;
    private int horizontalScroll;
    private int preferredVerticalX = -1;
    private boolean ensureCaretPending = true;

    private boolean draggingSelection;
    private boolean draggingCompletionScrollbar;
    private int completionScrollbarDragOffset;
    private float completionScrollbarHover;

    private final Deque<Snapshot> undoHistory = new ArrayDeque<Snapshot>();
    private final Deque<Snapshot> redoHistory = new ArrayDeque<Snapshot>();

    private boolean documentCacheDirty = true;
    private List<LineInfo> lines = Collections.singletonList(new LineInfo(0, 0, ""));
    private int revision;
    private FontRenderer widthCacheFont;
    private int widthCacheRevision = -1;
    private int cachedMaximumLineWidth;

    private List<ExpressionLanguageSupport.Completion> dynamicCompletions = Collections.emptyList();
    private int dynamicCompletionRevision;
    private List<ExpressionLanguageSupport.Completion> completions = Collections.emptyList();
    private int selectedCompletion;
    private int firstVisibleCompletion;
    private int visibleCompletionRows;
    private int completionPrefixStart;
    private int hoveredCompletion = -1;
    private String hoveredTooltip = "";

    private boolean completionQueryValid;
    private int completionQueryTextRevision;
    private int completionQueryDynamicRevision;
    private int completionQueryCursor;
    private int completionQueryAnchor;
    private boolean completionQueryFocused;
    private boolean completionQueryEnabled;
    private int suppressedCompletionRevision = -1;
    private int suppressedCompletionCursor = -1;

    public void setText(String value) {
        String prepared = prepareInput(value);
        if (prepared.length() > MAX_TEXT_LENGTH) {
            prepared = safePrefix(prepared, MAX_TEXT_LENGTH);
        }
        if (prepared.equals(text)) {
            clampCursorAndSelection();
            return;
        }

        text = prepared;
        cursor = text.length();
        selectionAnchor = cursor;
        verticalScroll = 0;
        horizontalScroll = 0;
        preferredVerticalX = -1;
        undoHistory.clear();
        redoHistory.clear();
        markDocumentChanged();
    }

    public String getText() {
        return text;
    }

    public void insertText(String value) {
        String prepared = prepareInput(value);
        if (prepared.isEmpty() && !hasSelection()) {
            return;
        }
        replaceSelection(prepared, prepared.length());
    }

    /** Inserts Markdown-style delimiters around the current selection. */
    public void wrapSelection(String prefix, String suffix) {
        String safePrefix = prepareInput(prefix == null ? "" : prefix);
        String safeSuffix = prepareInput(suffix == null ? "" : suffix);
        int start = selectionStart();
        int end = selectionEnd();
        String selected = text.substring(start, end);
        String replacement = safePrefix + selected + safeSuffix;
        int caret = selected.isEmpty() ? safePrefix.length() : replacement.length();
        applyEdit(start, end, replacement, caret, caret);
    }

    public void setDynamicCompletions(Collection<ExpressionLanguageSupport.Completion> candidates) {
        List<ExpressionLanguageSupport.Completion> prepared;
        if (candidates == null || candidates.isEmpty()) {
            prepared = Collections.emptyList();
        } else {
            List<ExpressionLanguageSupport.Completion> copy = new ArrayList<ExpressionLanguageSupport.Completion>();
            for (ExpressionLanguageSupport.Completion candidate : candidates) {
                if (candidate != null) {
                    copy.add(candidate);
                }
            }
            prepared = copy.isEmpty()
                    ? Collections.<ExpressionLanguageSupport.Completion>emptyList()
                    : Collections.unmodifiableList(copy);
        }
        if (dynamicCompletions.equals(prepared)) {
            return;
        }
        dynamicCompletions = prepared;
        dynamicCompletionRevision++;
        invalidateCompletionQuery();
        refreshCompletions();
    }

    public void draw(FontRenderer font, ModernMainLayout.Rect editorBounds, boolean editorEnabled,
            int mouseX, int mouseY) {
        hoveredTooltip = "";
        hoveredCompletion = -1;
        FontRenderer previousFont = fontRenderer;
        fontRenderer = font;

        ModernMainLayout.Rect nextBounds = editorBounds == null
                ? new ModernMainLayout.Rect(0, 0, 0, 0) : editorBounds;
        boolean layoutChanged = !sameRect(bounds, nextBounds) || enabled != editorEnabled;
        bounds = nextBounds;
        if (enabled != editorEnabled) {
            enabled = editorEnabled;
            invalidateCompletionQuery();
        }
        if (!enabled && focused) {
            setFocused(false);
        }
        if (layoutChanged || previousFont != fontRenderer) {
            ensureCaretPending = true;
        }

        if (bounds.width <= 0 || bounds.height <= 0) {
            clearCompletionPopupGeometry();
            return;
        }

        int fill = enabled ? 0xFF101820 : 0xFF0D1318;
        int border = focused && enabled ? ModernUiRenderer.ACCENT
                : bounds.contains(mouseX, mouseY) && enabled ? 0xFF526675 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5, fill, border);

        if (fontRenderer == null) {
            clearCompletionPopupGeometry();
            return;
        }

        ensureDocumentCache();
        updateLayoutMetrics();
        clampScrollOffsets();
        if (ensureCaretPending) {
            ensureCaretVisible();
        }
        refreshCompletions();

        drawEditorContents();
        drawVerticalScrollbar(mouseX, mouseY);
        updateHoveredTokenTooltip(mouseX, mouseY);
        drawCompletionPopup(mouseX, mouseY);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && completionScrollbarTrackBounds != null
                && completionScrollbarTrackBounds.contains(mouseX, mouseY)) {
            draggingCompletionScrollbar = true;
            if (completionScrollbarThumbBounds != null
                    && completionScrollbarThumbBounds.contains(mouseX, mouseY)) {
                completionScrollbarDragOffset = mouseY - completionScrollbarThumbBounds.y;
            } else {
                completionScrollbarDragOffset = completionScrollbarThumbBounds == null
                        ? 0 : completionScrollbarThumbBounds.height / 2;
                updateCompletionScrollFromMouse(mouseY);
            }
            return true;
        }
        if (mouseButton == 0 && completionPopupBounds != null
                && completionPopupBounds.contains(mouseX, mouseY)) {
            int completionIndex = completionIndexAt(mouseX, mouseY);
            if (completionIndex >= 0) {
                selectedCompletion = completionIndex;
                applySelectedCompletion();
            }
            return true;
        }

        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            if (mouseButton == 0 && focused) {
                setFocused(false);
            }
            return false;
        }
        if (!enabled) {
            return true;
        }

        if (mouseButton == 0 && scrollbar.beginDrag(mouseX, mouseY)) {
            setFocused(true);
            suppressCurrentCompletion();
            draggingSelection = false;
            return true;
        }

        if (mouseButton == 0) {
            setFocused(true);
            int position = positionFromMouse(mouseX, mouseY);
            if (GuiScreen.isShiftKeyDown()) {
                cursor = position;
            } else {
                cursor = position;
                selectionAnchor = position;
            }
            draggingSelection = true;
            scrollbar.endDrag();
            preferredVerticalX = -1;
            clearCompletionSuppression();
            invalidateCompletionQuery();
            ensureCaretVisible();
            refreshCompletions();
            return true;
        }

        setFocused(true);
        return true;
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        if (draggingCompletionScrollbar) {
            updateCompletionScrollFromMouse(mouseY);
            return true;
        }
        if (scrollbar.isDragging()) {
            scrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (!draggingSelection || !focused || !enabled) {
            return false;
        }

        autoScrollForSelection(mouseX, mouseY);
        cursor = positionFromMouse(mouseX, mouseY);
        preferredVerticalX = -1;
        clearCompletionSuppression();
        invalidateCompletionQuery();
        ensureCaretVisible();
        refreshCompletions();
        return true;
    }

    public boolean mouseReleased(int mouseX, int mouseY) {
        boolean handled = draggingSelection || scrollbar.isDragging() || draggingCompletionScrollbar;
        draggingSelection = false;
        scrollbar.endDrag();
        draggingCompletionScrollbar = false;
        return handled;
    }

    public boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (completionPopupBounds != null && completionPopupBounds.contains(mouseX, mouseY)) {
            int maximum = Math.max(0, completions.size() - visibleCompletionRows);
            if (maximum > 0) {
                int notches = Math.max(1, Math.abs(wheel) >= 120 ? Math.abs(wheel) / 120 : Math.abs(wheel));
                firstVisibleCompletion = clamp(firstVisibleCompletion + (wheel > 0 ? -notches : notches),
                        0, maximum);
            }
            return true;
        }
        if (!enabled) {
            return true;
        }

        int notches = Math.max(1, Math.abs(wheel) >= 120 ? Math.abs(wheel) / 120 : Math.abs(wheel));
        int direction = wheel > 0 ? -1 : 1;
        if (GuiScreen.isShiftKeyDown()) {
            horizontalScroll += direction * notches * 24;
        } else {
            verticalScroll += direction * notches * 3;
        }
        clampScrollOffsets();
        suppressCurrentCompletion();
        return true;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused || !enabled) {
            return false;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            refreshCompletions();
            if (!completions.isEmpty()) {
                suppressCurrentCompletion();
                return true;
            }
            return false;
        }

        boolean control = GuiScreen.isCtrlKeyDown();
        boolean shift = GuiScreen.isShiftKeyDown();
        if (control) {
            switch (keyCode) {
                case Keyboard.KEY_A:
                    selectionAnchor = 0;
                    cursor = text.length();
                    preferredVerticalX = -1;
                    clearCompletionSuppression();
                    invalidateCompletionQuery();
                    ensureCaretVisible();
                    refreshCompletions();
                    return true;
                case Keyboard.KEY_C:
                    copySelection();
                    return true;
                case Keyboard.KEY_X:
                    cutSelection();
                    return true;
                case Keyboard.KEY_V:
                    pasteClipboard();
                    return true;
                case Keyboard.KEY_Z:
                    if (shift) {
                        redo();
                    } else {
                        undo();
                    }
                    return true;
                case Keyboard.KEY_Y:
                    redo();
                    return true;
                default:
                    break;
            }
        }

        refreshCompletions();
        if (!completions.isEmpty()) {
            if (keyCode == Keyboard.KEY_UP) {
                moveCompletionSelection(-1);
                return true;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                moveCompletionSelection(1);
                return true;
            }
            if (keyCode == Keyboard.KEY_TAB) {
                applySelectedCompletion();
                return true;
            }
        }

        switch (keyCode) {
            case Keyboard.KEY_TAB:
                replaceSelection(TAB_TEXT, TAB_TEXT.length());
                return true;
            case Keyboard.KEY_BACK:
                deleteBackward();
                return true;
            case Keyboard.KEY_DELETE:
                deleteForward();
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                insertNewline();
                return true;
            case Keyboard.KEY_LEFT:
                moveLeft(shift, control);
                return true;
            case Keyboard.KEY_RIGHT:
                moveRight(shift, control);
                return true;
            case Keyboard.KEY_UP:
                moveVertically(-1, shift);
                return true;
            case Keyboard.KEY_DOWN:
                moveVertically(1, shift);
                return true;
            case Keyboard.KEY_HOME:
                moveHome(shift, control);
                return true;
            case Keyboard.KEY_END:
                moveEnd(shift, control);
                return true;
            case Keyboard.KEY_PRIOR:
                moveVertically(-Math.max(1, visibleLineCount - 1), shift);
                return true;
            case Keyboard.KEY_NEXT:
                moveVertically(Math.max(1, visibleLineCount - 1), shift);
                return true;
            default:
                break;
        }

        if (!control && !GuiScreen.isAltKeyDown() && typedChar >= 32 && typedChar != 127
                && typedChar != '\u00A7') {
            typeCharacter(typedChar);
            return true;
        }
        return false;
    }

    public void setFocused(boolean value) {
        boolean next = value && enabled;
        if (focused == next) {
            return;
        }
        focused = next;
        draggingSelection = false;
        scrollbar.endDrag();
        clearCompletionSuppression();
        invalidateCompletionQuery();
        if (focused) {
            ensureCaretPending = true;
            refreshCompletions();
        } else {
            clearCompletions();
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public String getHoveredTooltip() {
        return hoveredTooltip;
    }

    public String getStatusText() {
        ensureDocumentCache();
        refreshCompletions();
        int lineIndex = lineIndexForOffset(cursor);
        LineInfo line = lines.get(lineIndex);
        int column = Math.max(0, cursor - line.start);
        StringBuilder status = new StringBuilder();
        status.append(tr("gui.modern.path.code.fmt.pos", String.valueOf(lineIndex + 1), String.valueOf(column + 1)));
        status.append(tr("gui.modern.path.code.fmt.chars", String.valueOf(text.length()), String.valueOf(MAX_TEXT_LENGTH)));
        if (hasSelection()) {
            status.append(tr("gui.modern.path.code.fmt.selected", String.valueOf(selectionEnd() - selectionStart())));
        }
        if (!enabled) {
            status.append(tr("gui.modern.path.code.fmt.readonly"));
        } else if (!completions.isEmpty()) {
            status.append(tr("gui.modern.path.code.fmt.completions", String.valueOf(completions.size())));
        }
        return status.toString();
    }

    private void drawEditorContents() {
        int firstLine = verticalScroll;
        int lastLine = Math.min(lines.size(), firstLine + visibleLineCount + 1);
        int currentLine = lineIndexForOffset(cursor);

        int gutterRight = Math.min(bounds.right() - 1, bounds.x + gutterWidth);
        if (gutterRight > bounds.x + 1) {
            Gui.drawRect(bounds.x + 1, bounds.y + 1, gutterRight, bounds.bottom() - 1,
                    enabled ? 0xFF121C24 : 0xFF10161B);
            ModernUiRenderer.drawVerticalDivider(gutterRight, bounds.y + 2, Math.max(0, bounds.height - 4),
                    ModernUiRenderer.BORDER_SUBTLE);
        }

        ModernMainLayout.Rect gutterClip = new ModernMainLayout.Rect(bounds.x + 1, bounds.y + 2,
                Math.max(0, gutterRight - bounds.x - 2), Math.max(0, bounds.height - 4));
        ModernUiRenderer.beginClip(gutterClip);
        try {
            for (int lineIndex = firstLine; lineIndex < lastLine; lineIndex++) {
                int y = lineY(lineIndex);
                if (y >= viewportBounds.bottom()) {
                    break;
                }
                String lineNumber = String.valueOf(lineIndex + 1);
                int color = lineIndex == currentLine && focused
                        ? (enabled ? ModernUiRenderer.SUBTLE_TEXT : dimColor(ModernUiRenderer.SUBTLE_TEXT))
                        : dimColor(ModernUiRenderer.MUTED_TEXT);
                int x = gutterRight - 5 - fontRenderer.getStringWidth(lineNumber);
                fontRenderer.drawString(lineNumber, x, y, color);
            }
        } finally {
            ModernUiRenderer.endClip();
        }

        ModernUiRenderer.beginClip(viewportBounds);
        try {
            for (int lineIndex = firstLine; lineIndex < lastLine; lineIndex++) {
                int y = lineY(lineIndex);
                if (y >= viewportBounds.bottom()) {
                    break;
                }
                LineInfo line = lines.get(lineIndex);
                if (lineIndex == currentLine && focused) {
                    Gui.drawRect(viewportBounds.x, y - 1, viewportBounds.right(), y + lineHeight - 1,
                            enabled ? 0x161E91B8 : 0x101E91B8);
                }
                drawSelectionForLine(lineIndex, line, y);
                drawStyledLine(line, y);
            }

            if (focused && enabled && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                LineInfo line = lines.get(currentLine);
                int column = clamp(cursor - line.start, 0, line.text.length());
                int caretX = viewportBounds.x + stringWidth(line.text, 0, column) - horizontalScroll;
                int caretY = lineY(currentLine);
                Gui.drawRect(caretX, caretY - 1, caretX + 1,
                        caretY + Math.max(1, fontRenderer.FONT_HEIGHT + 1), ModernUiRenderer.TEXT);
            }
        } finally {
            ModernUiRenderer.endClip();
        }
    }

    private void drawSelectionForLine(int lineIndex, LineInfo line, int y) {
        if (!hasSelection()) {
            return;
        }
        int selectionStart = selectionStart();
        int selectionEnd = selectionEnd();
        if (selectionEnd < line.start || selectionStart > line.end) {
            return;
        }

        int from = clamp(selectionStart - line.start, 0, line.text.length());
        int to = clamp(selectionEnd - line.start, 0, line.text.length());
        boolean selectsNewline = lineIndex < lines.size() - 1
                && selectionStart <= line.end && selectionEnd > line.end;
        if (from == to && !selectsNewline) {
            return;
        }

        int x1 = viewportBounds.x + stringWidth(line.text, 0, from) - horizontalScroll;
        int x2 = viewportBounds.x + stringWidth(line.text, 0, to) - horizontalScroll;
        if (selectsNewline) {
            x2 = Math.max(x2, x1 + Math.max(3, fontRenderer.getStringWidth(" ")));
        }
        Gui.drawRect(x1, y - 1, Math.max(x1 + 1, x2), y + fontRenderer.FONT_HEIGHT + 1,
                focused ? 0x88466F87 : 0x55405260);
    }

    private void drawStyledLine(LineInfo line, int y) {
        if (line.text.isEmpty()) {
            return;
        }
        if (line.runs.isEmpty()) {
            fontRenderer.drawString(line.text, viewportBounds.x - horizontalScroll, y,
                    enabled ? themedTokenColor(ExpressionLanguageSupport.COLOR_IDENTIFIER)
                            : dimColor(themedTokenColor(ExpressionLanguageSupport.COLOR_IDENTIFIER)));
            return;
        }

        int measuredEnd = 0;
        int measuredWidth = 0;
        for (ColoredRun run : line.runs) {
            if (run.start > measuredEnd) {
                measuredWidth += stringWidth(line.text, measuredEnd, run.start);
            }
            int x = viewportBounds.x + measuredWidth - horizontalScroll;
            int runWidth = stringWidth(line.text, run.start, run.end);
            if (x + runWidth < viewportBounds.x || x > viewportBounds.right()) {
                measuredWidth += runWidth;
                measuredEnd = run.end;
                continue;
            }
            String value = line.text.substring(run.start, run.end);
            int color = themedTokenColor(run.color);
            fontRenderer.drawString(value, x, y, enabled ? color : dimColor(color));
            measuredWidth += runWidth;
            measuredEnd = run.end;
        }
    }

    /** Maps syntax colors onto the active editor surface for light and dark themes. */
    private int themedTokenColor(int preferred) {
        return ModernUiRenderer.readableText(preferred, 0xFF101820);
    }

    private void drawVerticalScrollbar(int mouseX, int mouseY) {
        int maximumScroll = maximumVerticalScroll();
        if (bounds == null || bounds.height <= 0 || maximumScroll <= 0) {
            scrollbar.idle();
            return;
        }
        scrollbar.drawAt(bounds.right(), bounds.y + 3, Math.max(0, bounds.height - 6), verticalScroll, maximumScroll,
                Math.max(1, visibleLineCount), Math.max(visibleLineCount, lines.size()), mouseX, mouseY,
                value -> verticalScroll = value);
    }

    private void drawCompletionPopup(int mouseX, int mouseY) {
        clearCompletionPopupGeometry();
        if (completions.isEmpty() || !focused || !enabled || fontRenderer == null
                || viewportBounds.width <= 0 || viewportBounds.height <= 0) {
            return;
        }

        int cursorLine = lineIndexForOffset(cursor);
        int caretY = lineY(cursorLine);
        if (caretY + fontRenderer.FONT_HEIGHT < viewportBounds.y || caretY >= viewportBounds.bottom()) {
            return;
        }
        LineInfo line = lines.get(cursorLine);
        int column = clamp(cursor - line.start, 0, line.text.length());
        int caretX = viewportBounds.x + stringWidth(line.text, 0, column) - horizontalScroll;

        int innerLeft = bounds.x + 2;
        int innerTop = bounds.y + 2;
        int innerRight = Math.max(innerLeft, bounds.right() - 2);
        int innerBottom = Math.max(innerTop, bounds.bottom() - 2);
        int innerWidth = innerRight - innerLeft;
        int innerHeight = innerBottom - innerTop;
        if (innerWidth < 36 || innerHeight < COMPLETION_ROW_HEIGHT + 2) {
            return;
        }

        int desiredWidth = 120;
        int measured = Math.min(completions.size(), MAX_VISIBLE_COMPLETIONS);
        for (int i = 0; i < measured; i++) {
            ExpressionLanguageSupport.Completion completion = completions.get(i);
            String label = completionLabel(completion);
            desiredWidth = Math.max(desiredWidth,
                    30 + fontRenderer.getStringWidth(label) + Math.min(100, fontRenderer.getStringWidth(completion.detail)));
        }
        int popupWidth = Math.min(innerWidth, desiredWidth);

        int desiredRows = Math.min(MAX_VISIBLE_COMPLETIONS, completions.size());
        int belowY = caretY + lineHeight;
        int availableBelow = innerBottom - belowY;
        int availableAbove = caretY - innerTop;
        boolean drawBelow = availableBelow >= COMPLETION_ROW_HEIGHT + 2
                && (availableBelow >= availableAbove || availableBelow >= desiredRows * COMPLETION_ROW_HEIGHT + 2);
        int availableHeight = drawBelow ? availableBelow : availableAbove;
        int rows = Math.min(desiredRows, Math.max(0, (availableHeight - 2) / COMPLETION_ROW_HEIGHT));
        if (rows <= 0) {
            rows = Math.min(desiredRows, Math.max(0, (innerHeight - 2) / COMPLETION_ROW_HEIGHT));
            if (rows <= 0) {
                return;
            }
            drawBelow = true;
        }

        int popupHeight = rows * COMPLETION_ROW_HEIGHT + 2;
        int popupX = clamp(caretX, innerLeft, Math.max(innerLeft, innerRight - popupWidth));
        int popupY = drawBelow ? belowY : caretY - popupHeight;
        popupY = clamp(popupY, innerTop, Math.max(innerTop, innerBottom - popupHeight));
        completionPopupBounds = new ModernMainLayout.Rect(popupX, popupY, popupWidth, popupHeight);
        visibleCompletionRows = rows;
        firstVisibleCompletion = clamp(firstVisibleCompletion, 0,
                Math.max(0, completions.size() - visibleCompletionRows));

        ModernUiRenderer.beginClip(bounds.inset(1));
        try {
            ModernUiRenderer.drawSubtlePanel(completionPopupBounds.x, completionPopupBounds.y,
                    completionPopupBounds.width, completionPopupBounds.height, 4,
                    0xFF111B23, ModernUiRenderer.BORDER);

            int end = Math.min(completions.size(), firstVisibleCompletion + visibleCompletionRows);
            for (int index = firstVisibleCompletion; index < end; index++) {
                int local = index - firstVisibleCompletion;
                int rowY = completionPopupBounds.y + 1 + local * COMPLETION_ROW_HEIGHT;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(completionPopupBounds.x + 1, rowY,
                        Math.max(0, completionPopupBounds.width - 2), COMPLETION_ROW_HEIGHT);
                boolean hovered = row.contains(mouseX, mouseY);
                boolean selected = index == selectedCompletion;
                if (selected || hovered) {
                    ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 2,
                            selected ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SURFACE_HOVER);
                }

                ExpressionLanguageSupport.Completion completion = completions.get(index);
                String kind = completionKindShort(completion.kind);
                int kindColor = completionKindColor(completion.kind);
                ModernUiRenderer.drawText(fontRenderer, kind, row.x + 5, row.y + 5, kindColor, 18);

                int labelX = row.x + 26;
                int detailWidth = completion.detail.isEmpty() ? 0
                        : Math.min(94, Math.max(0, row.width / 3));
                int labelWidth = Math.max(8, row.width - (labelX - row.x) - detailWidth - 7);
                ModernUiRenderer.drawText(fontRenderer, completionLabel(completion), labelX, row.y + 5,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, labelWidth);
                if (detailWidth > 0) {
                    ModernUiRenderer.drawText(fontRenderer, completion.detail,
                            row.right() - detailWidth - 4, row.y + 5, ModernUiRenderer.MUTED_TEXT, detailWidth);
                }

                if (hovered) {
                    hoveredCompletion = index;
                    hoveredTooltip = completionTooltip(completion);
                }
            }

            if (completions.size() > visibleCompletionRows) {
                int trackHeight = Math.max(1, completionPopupBounds.height - 4);
                int thumbHeight = Math.max(5,
                        Math.round(trackHeight * visibleCompletionRows / (float) completions.size()));
                int maximumFirst = Math.max(1, completions.size() - visibleCompletionRows);
                int travel = Math.max(0, trackHeight - thumbHeight);
                int thumbY = completionPopupBounds.y + 2
                        + Math.round(travel * firstVisibleCompletion / (float) maximumFirst);
                completionScrollbarTrackBounds = new ModernMainLayout.Rect(completionPopupBounds.right() - 14,
                        completionPopupBounds.y + 2, 14, trackHeight);
                completionScrollbarThumbBounds = new ModernMainLayout.Rect(completionPopupBounds.right() - 4,
                        thumbY, 3, thumbHeight);
                boolean hovered = draggingCompletionScrollbar
                        || completionScrollbarTrackBounds.contains(mouseX, mouseY);
                float target = hovered ? 1.0F : 0.0F;
                completionScrollbarHover += (target - completionScrollbarHover) * 0.28F;
                if (Math.abs(target - completionScrollbarHover) < 0.01F) {
                    completionScrollbarHover = target;
                }
                int grown = Math.round(3 + 5 * completionScrollbarHover);
                int trackWidth = Math.max(2, Math.round(1 + 2 * completionScrollbarHover));
                int thumbX = completionPopupBounds.right() - grown - 2;
                int thumbColor = hovered ? ModernUiRenderer.ACCENT
                        : ModernUiRenderer.MUTED_TEXT;
                ModernUiRenderer.drawRoundedRect(completionPopupBounds.right() - trackWidth - 3,
                        completionPopupBounds.y + 2, trackWidth, trackHeight, 1,
                        ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight,
                        Math.max(1, grown / 2), thumbColor);
                completionScrollbarThumbBounds = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
            }
        } finally {
            ModernUiRenderer.endClip();
        }
    }

    private void updateLayoutMetrics() {
        lineHeight = Math.max(11, fontRenderer.FONT_HEIGHT + 3);
        int lineNumberWidth = fontRenderer.getStringWidth(String.valueOf(Math.max(1, lines.size())));
        gutterWidth = Math.max(26, lineNumberWidth + 12);
        gutterWidth = Math.min(gutterWidth, Math.max(0, bounds.width / 3));

        int scrollbarWidth = ModernHoverScrollbar.GUTTER;
        int viewportX = bounds.x + gutterWidth + 5;
        int viewportRight = bounds.right() - scrollbarWidth - 3;
        viewportBounds = new ModernMainLayout.Rect(viewportX, bounds.y + 3,
                Math.max(0, viewportRight - viewportX), Math.max(0, bounds.height - 6));
        visibleLineCount = Math.max(1, viewportBounds.height / lineHeight);
    }

    private void autoScrollForSelection(int mouseX, int mouseY) {
        if (viewportBounds == null) {
            return;
        }
        if (mouseY < viewportBounds.y) {
            verticalScroll--;
        } else if (mouseY >= viewportBounds.bottom()) {
            verticalScroll++;
        }
        if (mouseX < viewportBounds.x) {
            horizontalScroll -= 12;
        } else if (mouseX >= viewportBounds.right()) {
            horizontalScroll += 12;
        }
        clampScrollOffsets();
    }

    private int positionFromMouse(int mouseX, int mouseY) {
        ensureDocumentCache();
        int localLine;
        if (viewportBounds.height <= 0) {
            localLine = 0;
        } else if (mouseY < viewportBounds.y) {
            localLine = 0;
        } else {
            localLine = (mouseY - viewportBounds.y) / Math.max(1, lineHeight);
        }
        int lineIndex = clamp(verticalScroll + localLine, 0, lines.size() - 1);
        LineInfo line = lines.get(lineIndex);
        int targetX = Math.max(0, mouseX - viewportBounds.x + horizontalScroll);
        int column = columnAtPixel(line.text, targetX);
        return line.start + column;
    }

    private int columnAtPixel(String line, int targetX) {
        if (line == null || line.isEmpty() || targetX <= 0) {
            return 0;
        }
        if (fontRenderer == null) {
            return clamp(targetX / 6, 0, line.length());
        }

        int x = 0;
        int index = 0;
        while (index < line.length()) {
            int next = nextOffset(line, index);
            int width = fontRenderer.getStringWidth(line.substring(index, next));
            if (targetX < x + Math.max(1, width) / 2) {
                return index;
            }
            x += width;
            index = next;
        }
        return line.length();
    }

    private void typeCharacter(char value) {
        if (value == '(' || value == '[') {
            char close = value == '(' ? ')' : ']';
            if (hasSelection()) {
                wrapSelection(value, close);
            } else if (isInsideStringOrComment(cursor)) {
                replaceSelection(String.valueOf(value), 1);
            } else {
                replaceSelection(new String(new char[] { value, close }), 1);
            }
            return;
        }

        if (value == ')' || value == ']') {
            if (!hasSelection() && !isInsideStringOrComment(cursor)
                    && cursor < text.length() && text.charAt(cursor) == value) {
                setCursorPosition(cursor + 1, false, false);
            } else {
                replaceSelection(String.valueOf(value), 1);
            }
            return;
        }

        if (value == '\'' || value == '"') {
            if (!hasSelection() && cursor < text.length() && text.charAt(cursor) == value) {
                setCursorPosition(cursor + 1, false, false);
                return;
            }
            if (hasSelection()) {
                wrapSelection(value, value);
                return;
            }
            if (shouldPairQuote(value)) {
                replaceSelection(new String(new char[] { value, value }), 1);
            } else {
                replaceSelection(String.valueOf(value), 1);
            }
            return;
        }

        replaceSelection(String.valueOf(value), 1);
    }

    private void wrapSelection(char open, char close) {
        int start = selectionStart();
        int end = selectionEnd();
        String selected = text.substring(start, end);
        if (text.length() - selected.length() + selected.length() + 2 > MAX_TEXT_LENGTH) {
            return;
        }
        boolean forward = cursor >= selectionAnchor;
        String replacement = String.valueOf(open) + selected + close;
        int newAnchor = forward ? 1 : selected.length() + 1;
        int newCursor = forward ? selected.length() + 1 : 1;
        applyEdit(start, end, replacement, newCursor, newAnchor);
    }

    private boolean shouldPairQuote(char quote) {
        if (isInsideStringOrComment(cursor)) {
            return false;
        }
        char previous = cursor > 0 ? text.charAt(cursor - 1) : 0;
        char next = cursor < text.length() ? text.charAt(cursor) : 0;
        if (previous == '\\') {
            return false;
        }
        return !isIdentifierCharacter(previous) && !isIdentifierCharacter(next) && next != quote;
    }

    private void insertNewline() {
        ensureDocumentCache();
        int lineIndex = lineIndexForOffset(cursor);
        LineInfo line = lines.get(lineIndex);
        int leading = 0;
        while (leading < line.text.length()) {
            char value = line.text.charAt(leading);
            if (value != ' ' && value != '\t') {
                break;
            }
            leading++;
        }
        String indent = line.text.substring(0, leading).replace("\t", TAB_TEXT);
        int selectionStart = selectionStart();
        char previous = selectionStart > 0 ? text.charAt(selectionStart - 1) : 0;
        if (previous == '(' || previous == '[') {
            indent += TAB_TEXT;
        }
        String insertion = "\n" + indent;
        replaceSelection(insertion, insertion.length());
    }

    private void deleteBackward() {
        if (hasSelection()) {
            replaceSelection("", 0);
            return;
        }
        if (cursor <= 0) {
            return;
        }
        if (cursor < text.length() && isMatchingPair(text.charAt(cursor - 1), text.charAt(cursor))) {
            applyEdit(cursor - 1, cursor + 1, "", 0, 0);
            return;
        }
        int previous = previousOffset(text, cursor);
        applyEdit(previous, cursor, "", 0, 0);
    }

    private void deleteForward() {
        if (hasSelection()) {
            replaceSelection("", 0);
            return;
        }
        if (cursor >= text.length()) {
            return;
        }
        if (cursor > 0 && isMatchingPair(text.charAt(cursor - 1), text.charAt(cursor))) {
            applyEdit(cursor - 1, cursor + 1, "", 0, 0);
            return;
        }
        int next = nextOffset(text, cursor);
        applyEdit(cursor, next, "", 0, 0);
    }

    private void moveLeft(boolean selecting, boolean byWord) {
        if (hasSelection() && !selecting) {
            setCursorPosition(selectionStart(), false, false);
            return;
        }
        int target = byWord ? previousWordOffset(cursor) : previousOffset(text, cursor);
        setCursorPosition(target, selecting, false);
    }

    private void moveRight(boolean selecting, boolean byWord) {
        if (hasSelection() && !selecting) {
            setCursorPosition(selectionEnd(), false, false);
            return;
        }
        int target = byWord ? nextWordOffset(cursor) : nextOffset(text, cursor);
        setCursorPosition(target, selecting, false);
    }

    private void moveHome(boolean selecting, boolean document) {
        int target;
        if (document) {
            target = 0;
        } else {
            ensureDocumentCache();
            target = lines.get(lineIndexForOffset(cursor)).start;
        }
        setCursorPosition(target, selecting, false);
    }

    private void moveEnd(boolean selecting, boolean document) {
        int target;
        if (document) {
            target = text.length();
        } else {
            ensureDocumentCache();
            target = lines.get(lineIndexForOffset(cursor)).end;
        }
        setCursorPosition(target, selecting, false);
    }

    private void moveVertically(int lineDelta, boolean selecting) {
        ensureDocumentCache();
        int currentLineIndex = lineIndexForOffset(cursor);
        LineInfo currentLine = lines.get(currentLineIndex);
        int currentColumn = clamp(cursor - currentLine.start, 0, currentLine.text.length());
        if (preferredVerticalX < 0) {
            preferredVerticalX = fontRenderer == null ? currentColumn * 6
                    : stringWidth(currentLine.text, 0, currentColumn);
        }
        int targetLineIndex = clamp(currentLineIndex + lineDelta, 0, lines.size() - 1);
        LineInfo targetLine = lines.get(targetLineIndex);
        int targetColumn = columnAtPixel(targetLine.text, preferredVerticalX);
        setCursorPosition(targetLine.start + targetColumn, selecting, true);
    }

    private void setCursorPosition(int position, boolean selecting, boolean preserveVerticalX) {
        cursor = clamp(position, 0, text.length());
        if (!selecting) {
            selectionAnchor = cursor;
        }
        if (!preserveVerticalX) {
            preferredVerticalX = -1;
        }
        clearCompletionSuppression();
        invalidateCompletionQuery();
        ensureCaretVisible();
        refreshCompletions();
    }

    private int previousWordOffset(int position) {
        int result = clamp(position, 0, text.length());
        while (result > 0 && Character.isWhitespace(text.charAt(result - 1))) {
            result = previousOffset(text, result);
        }
        if (result <= 0) {
            return 0;
        }
        boolean identifier = isIdentifierCharacter(text.charAt(result - 1));
        while (result > 0) {
            char value = text.charAt(result - 1);
            if (Character.isWhitespace(value) || isIdentifierCharacter(value) != identifier) {
                break;
            }
            result = previousOffset(text, result);
        }
        return result;
    }

    private int nextWordOffset(int position) {
        int result = clamp(position, 0, text.length());
        while (result < text.length() && Character.isWhitespace(text.charAt(result))) {
            result = nextOffset(text, result);
        }
        if (result >= text.length()) {
            return text.length();
        }
        boolean identifier = isIdentifierCharacter(text.charAt(result));
        while (result < text.length()) {
            char value = text.charAt(result);
            if (Character.isWhitespace(value) || isIdentifierCharacter(value) != identifier) {
                break;
            }
            result = nextOffset(text, result);
        }
        return result;
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        try {
            GuiScreen.setClipboardString(text.substring(selectionStart(), selectionEnd()));
        } catch (RuntimeException ignored) {
        }
    }

    private void cutSelection() {
        if (!hasSelection()) {
            return;
        }
        copySelection();
        replaceSelection("", 0);
    }

    private void pasteClipboard() {
        try {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null && !clipboard.isEmpty()) {
                insertText(clipboard);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void undo() {
        if (undoHistory.isEmpty()) {
            return;
        }
        pushSnapshot(redoHistory, snapshot());
        restoreSnapshot(undoHistory.pop());
    }

    private void redo() {
        if (redoHistory.isEmpty()) {
            return;
        }
        pushSnapshot(undoHistory, snapshot());
        restoreSnapshot(redoHistory.pop());
    }

    private Snapshot snapshot() {
        return new Snapshot(text, cursor, selectionAnchor, verticalScroll, horizontalScroll);
    }

    private void restoreSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        text = snapshot.text;
        cursor = snapshot.cursor;
        selectionAnchor = snapshot.selectionAnchor;
        verticalScroll = snapshot.verticalScroll;
        horizontalScroll = snapshot.horizontalScroll;
        preferredVerticalX = -1;
        markDocumentChanged();
    }

    private void pushUndoSnapshot() {
        pushSnapshot(undoHistory, snapshot());
        redoHistory.clear();
    }

    private static void pushSnapshot(Deque<Snapshot> history, Snapshot snapshot) {
        history.push(snapshot);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    private void replaceSelection(String replacement, int caretWithinReplacement) {
        applyEdit(selectionStart(), selectionEnd(), replacement, caretWithinReplacement, caretWithinReplacement);
    }

    private void applyEdit(int start, int end, String replacement, int cursorWithinReplacement,
            int anchorWithinReplacement) {
        int safeStart = clamp(Math.min(start, end), 0, text.length());
        int safeEnd = clamp(Math.max(start, end), safeStart, text.length());
        String prepared = prepareInput(replacement);
        int capacity = MAX_TEXT_LENGTH - (text.length() - (safeEnd - safeStart));
        if (capacity < prepared.length()) {
            prepared = safePrefix(prepared, Math.max(0, capacity));
        }
        if (safeStart == safeEnd && prepared.isEmpty()) {
            return;
        }

        String nextText = text.substring(0, safeStart) + prepared + text.substring(safeEnd);
        if (nextText.equals(text)) {
            return;
        }
        pushUndoSnapshot();
        text = nextText;
        cursor = safeStart + clamp(cursorWithinReplacement, 0, prepared.length());
        selectionAnchor = safeStart + clamp(anchorWithinReplacement, 0, prepared.length());
        preferredVerticalX = -1;
        markDocumentChanged();
    }

    private void markDocumentChanged() {
        revision++;
        documentCacheDirty = true;
        widthCacheRevision = -1;
        ensureCaretPending = true;
        clearCompletionSuppression();
        invalidateCompletionQuery();
        clampCursorAndSelection();
        ensureCaretVisible();
        refreshCompletions();
    }

    private void ensureDocumentCache() {
        if (!documentCacheDirty) {
            return;
        }

        List<LineInfo> rebuilt = new ArrayList<LineInfo>();
        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                rebuilt.add(new LineInfo(lineStart, index, text.substring(lineStart, index)));
                lineStart = index + 1;
            }
        }
        rebuilt.add(new LineInfo(lineStart, text.length(), text.substring(lineStart)));

        List<ExpressionLanguageSupport.Span> spans;
        try {
            spans = ExpressionLanguageSupport.tokenize(text);
        } catch (RuntimeException ignored) {
            spans = Collections.emptyList();
        }
        Map<Integer, Integer> delimiterColors = bracketColors(spans);

        int firstSpan = 0;
        for (LineInfo line : rebuilt) {
            while (firstSpan < spans.size() && spans.get(firstSpan).end <= line.start) {
                firstSpan++;
            }
            int spanIndex = firstSpan;
            while (spanIndex < spans.size()) {
                ExpressionLanguageSupport.Span span = spans.get(spanIndex);
                if (span.start >= line.end) {
                    break;
                }
                int start = Math.max(line.start, span.start);
                int end = Math.min(line.end, span.end);
                if (end > start) {
                    Integer delimiterColor = delimiterColors.get(Integer.valueOf(span.start));
                    line.runs.add(new ColoredRun(start - line.start, end - line.start,
                            delimiterColor == null ? ExpressionLanguageSupport.colorFor(span.kind)
                                    : delimiterColor.intValue()));
                }
                spanIndex++;
            }
            if (!line.text.isEmpty() && line.runs.isEmpty()) {
                line.runs.add(new ColoredRun(0, line.text.length(),
                        ExpressionLanguageSupport.COLOR_IDENTIFIER));
            }
        }

        lines = Collections.unmodifiableList(rebuilt);
        documentCacheDirty = false;
        clampCursorAndSelection();
    }

    private int lineIndexForOffset(int offset) {
        ensureDocumentCache();
        int safeOffset = clamp(offset, 0, text.length());
        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            LineInfo line = lines.get(middle);
            if (safeOffset < line.start) {
                high = middle - 1;
            } else if (safeOffset > line.end) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return clamp(low, 0, lines.size() - 1);
    }

    private int lineY(int lineIndex) {
        return viewportBounds.y + (lineIndex - verticalScroll) * lineHeight;
    }

    private void ensureCaretVisible() {
        ensureDocumentCache();
        if (viewportBounds == null || viewportBounds.width <= 0 || viewportBounds.height <= 0
                || fontRenderer == null) {
            ensureCaretPending = true;
            return;
        }

        int lineIndex = lineIndexForOffset(cursor);
        if (lineIndex < verticalScroll) {
            verticalScroll = lineIndex;
        } else if (lineIndex >= verticalScroll + visibleLineCount) {
            verticalScroll = lineIndex - visibleLineCount + 1;
        }

        LineInfo line = lines.get(lineIndex);
        int column = clamp(cursor - line.start, 0, line.text.length());
        int caretPixel = stringWidth(line.text, 0, column);
        int rightPadding = Math.max(2, Math.min(10, viewportBounds.width / 4));
        if (caretPixel < horizontalScroll) {
            horizontalScroll = caretPixel;
        } else if (caretPixel > horizontalScroll + viewportBounds.width - rightPadding) {
            horizontalScroll = caretPixel - viewportBounds.width + rightPadding;
        }
        clampScrollOffsets();
        ensureCaretPending = false;
    }

    private void clampScrollOffsets() {
        ensureDocumentCache();
        verticalScroll = clamp(verticalScroll, 0, maximumVerticalScroll());
        horizontalScroll = clamp(horizontalScroll, 0, maximumHorizontalScroll());
    }

    private int maximumVerticalScroll() {
        return Math.max(0, lines.size() - Math.max(1, visibleLineCount));
    }

    private int maximumHorizontalScroll() {
        if (fontRenderer == null || viewportBounds == null || viewportBounds.width <= 0) {
            return Math.max(0, horizontalScroll);
        }
        return Math.max(0, maximumLineWidth() - viewportBounds.width + 4);
    }

    private int maximumLineWidth() {
        if (fontRenderer == null) {
            return 0;
        }
        if (widthCacheFont == fontRenderer && widthCacheRevision == revision) {
            return cachedMaximumLineWidth;
        }
        int maximum = 0;
        for (LineInfo line : lines) {
            maximum = Math.max(maximum, fontRenderer.getStringWidth(line.text));
        }
        widthCacheFont = fontRenderer;
        widthCacheRevision = revision;
        cachedMaximumLineWidth = maximum;
        return maximum;
    }

    private int stringWidth(String value, int start, int end) {
        if (fontRenderer == null || value == null || value.isEmpty()) {
            return 0;
        }
        int safeStart = clamp(start, 0, value.length());
        int safeEnd = clamp(end, safeStart, value.length());
        return fontRenderer.getStringWidth(value.substring(safeStart, safeEnd));
    }

    private void refreshCompletions() {
        if (completionQueryValid
                && completionQueryTextRevision == revision
                && completionQueryDynamicRevision == dynamicCompletionRevision
                && completionQueryCursor == cursor
                && completionQueryAnchor == selectionAnchor
                && completionQueryFocused == focused
                && completionQueryEnabled == enabled) {
            return;
        }

        completionQueryValid = true;
        completionQueryTextRevision = revision;
        completionQueryDynamicRevision = dynamicCompletionRevision;
        completionQueryCursor = cursor;
        completionQueryAnchor = selectionAnchor;
        completionQueryFocused = focused;
        completionQueryEnabled = enabled;

        if (!focused || !enabled || hasSelection()
                || suppressedCompletionRevision == revision && suppressedCompletionCursor == cursor) {
            clearCompletions();
            return;
        }

        String prefix = ExpressionLanguageSupport.currentTokenPrefix(text, cursor);
        if (!isCompletionPrefix(prefix)) {
            clearCompletions();
            return;
        }

        String selectedKey = completions.isEmpty() || selectedCompletion < 0
                || selectedCompletion >= completions.size()
                        ? "" : completionKey(completions.get(selectedCompletion));
        List<ExpressionLanguageSupport.Completion> next = ExpressionLanguageSupport.complete(
                text, cursor, dynamicCompletions);
        if (next == null || next.isEmpty()) {
            clearCompletions();
            return;
        }

        completions = next;
        completionPrefixStart = cursor - prefix.length();
        selectedCompletion = 0;
        if (!selectedKey.isEmpty()) {
            for (int index = 0; index < completions.size(); index++) {
                if (selectedKey.equals(completionKey(completions.get(index)))) {
                    selectedCompletion = index;
                    break;
                }
            }
        }
        firstVisibleCompletion = clamp(firstVisibleCompletion, 0, Math.max(0, completions.size() - 1));
        adjustVisibleCompletionWindow();
    }

    private void moveCompletionSelection(int delta) {
        if (completions.isEmpty()) {
            return;
        }
        int size = completions.size();
        selectedCompletion = (selectedCompletion + delta) % size;
        if (selectedCompletion < 0) {
            selectedCompletion += size;
        }
        adjustVisibleCompletionWindow();
    }

    private void adjustVisibleCompletionWindow() {
        if (completions.isEmpty()) {
            firstVisibleCompletion = 0;
            return;
        }
        int rows = visibleCompletionRows > 0 ? visibleCompletionRows : MAX_VISIBLE_COMPLETIONS;
        rows = Math.max(1, Math.min(rows, completions.size()));
        if (selectedCompletion < firstVisibleCompletion) {
            firstVisibleCompletion = selectedCompletion;
        } else if (selectedCompletion >= firstVisibleCompletion + rows) {
            firstVisibleCompletion = selectedCompletion - rows + 1;
        }
        firstVisibleCompletion = clamp(firstVisibleCompletion, 0, Math.max(0, completions.size() - rows));
    }

    private void applySelectedCompletion() {
        refreshCompletions();
        if (completions.isEmpty()) {
            return;
        }
        selectedCompletion = clamp(selectedCompletion, 0, completions.size() - 1);
        ExpressionLanguageSupport.Completion completion = completions.get(selectedCompletion);
        String insertion = completion.insertText.isEmpty() ? completion.label : completion.insertText;
        int replaceStart = clamp(completionPrefixStart, 0, cursor);

        if (completion.kind == ExpressionLanguageSupport.CompletionKind.FUNCTION) {
            String functionName = functionName(insertion);
            boolean existingParenthesis = cursor < text.length() && text.charAt(cursor) == '(';
            String replacement = functionName + (existingParenthesis ? "" : "()");
            if (!canReplaceFully(replaceStart, cursor, replacement)) {
                suppressCurrentCompletion();
                return;
            }
            int caret = functionName.length() + 1;
            applyEdit(replaceStart, cursor, replacement, existingParenthesis ? replacement.length() : caret,
                    existingParenthesis ? replacement.length() : caret);
            if (existingParenthesis && cursor < text.length() && text.charAt(cursor) == '(') {
                cursor++;
                selectionAnchor = cursor;
                ensureCaretVisible();
            }
        } else {
            if (!canReplaceFully(replaceStart, cursor, insertion)) {
                suppressCurrentCompletion();
                return;
            }
            applyEdit(replaceStart, cursor, insertion, insertion.length(), insertion.length());
        }

        suppressedCompletionRevision = revision;
        suppressedCompletionCursor = cursor;
        invalidateCompletionQuery();
        clearCompletions();
    }

    private void updateHoveredTokenTooltip(int mouseX, int mouseY) {
        if (fontRenderer == null || viewportBounds == null || !viewportBounds.contains(mouseX, mouseY)
                || dynamicCompletions.isEmpty()) {
            return;
        }
        int offset = positionFromMouse(mouseX, mouseY);
        if (offset == text.length() && offset > 0) {
            offset--;
        }
        int start = offset;
        int end = offset;
        while (start > 0 && isReferencePathCharacter(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && isReferencePathCharacter(text.charAt(end))) {
            end++;
        }
        if (start >= end) {
            return;
        }
        String hovered = text.substring(start, end);
        String normalizedHovered = hovered.startsWith("$") ? hovered.substring(1) : hovered;
        for (ExpressionLanguageSupport.Completion completion : dynamicCompletions) {
            if (completion == null || completion.kind != ExpressionLanguageSupport.CompletionKind.VARIABLE) {
                continue;
            }
            String candidate = completion.insertText.startsWith("$")
                    ? completion.insertText.substring(1) : completion.insertText;
            if (candidate.equalsIgnoreCase(normalizedHovered)) {
                hoveredTooltip = completionTooltip(completion);
                return;
            }
        }
    }

    private static boolean isReferencePathCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '.'
                || value == '[' || value == ']';
    }

    private int completionIndexAt(int mouseX, int mouseY) {
        if (completionPopupBounds == null || !completionPopupBounds.contains(mouseX, mouseY)) {
            return -1;
        }
        int localY = mouseY - completionPopupBounds.y - 1;
        if (localY < 0) {
            return -1;
        }
        int localRow = localY / COMPLETION_ROW_HEIGHT;
        if (localRow < 0 || localRow >= visibleCompletionRows) {
            return -1;
        }
        int index = firstVisibleCompletion + localRow;
        return index >= 0 && index < completions.size() ? index : -1;
    }

    private void suppressCurrentCompletion() {
        suppressedCompletionRevision = revision;
        suppressedCompletionCursor = cursor;
        invalidateCompletionQuery();
        clearCompletions();
    }

    private void clearCompletionSuppression() {
        suppressedCompletionRevision = -1;
        suppressedCompletionCursor = -1;
    }

    private void invalidateCompletionQuery() {
        completionQueryValid = false;
    }

    private void clearCompletions() {
        completions = Collections.emptyList();
        selectedCompletion = 0;
        firstVisibleCompletion = 0;
        completionPrefixStart = cursor;
        draggingCompletionScrollbar = false;
        clearCompletionPopupGeometry();
    }

    private void clearCompletionPopupGeometry() {
        completionPopupBounds = null;
        completionScrollbarTrackBounds = null;
        completionScrollbarThumbBounds = null;
        visibleCompletionRows = 0;
        hoveredCompletion = -1;
    }

    private void updateCompletionScrollFromMouse(int mouseY) {
        if (completionScrollbarTrackBounds == null || completionScrollbarThumbBounds == null) {
            return;
        }
        int maximum = Math.max(0, completions.size() - visibleCompletionRows);
        if (maximum <= 0) {
            firstVisibleCompletion = 0;
            return;
        }
        int travel = Math.max(1, completionScrollbarTrackBounds.height
                - completionScrollbarThumbBounds.height);
        int targetY = clamp(mouseY - completionScrollbarDragOffset, completionScrollbarTrackBounds.y,
                completionScrollbarTrackBounds.bottom() - completionScrollbarThumbBounds.height);
        firstVisibleCompletion = clamp(Math.round((targetY - completionScrollbarTrackBounds.y)
                * maximum / (float) travel), 0, maximum);
    }

    private static boolean isCompletionPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }
        boolean identifier = false;
        for (int index = 0; index < prefix.length(); index++) {
            char value = prefix.charAt(index);
            if (Character.isLetter(value) || value == '_' || value == '$') {
                identifier = true;
            } else if (!Character.isDigit(value) && value != '.' && value != '[' && value != ']'
                    && value != '+' && value != '-') {
                return false;
            }
        }
        return identifier;
    }

    private static String completionKey(ExpressionLanguageSupport.Completion completion) {
        if (completion == null) {
            return "";
        }
        String value = completion.insertText.isEmpty() ? completion.label : completion.insertText;
        return value.trim().toLowerCase(Locale.ROOT) + "\u0000" + completion.kind.name();
    }

    private static String completionLabel(ExpressionLanguageSupport.Completion completion) {
        if (completion == null) {
            return "";
        }
        return completion.label.isEmpty() ? completion.insertText : completion.label;
    }

    private static String completionTooltip(ExpressionLanguageSupport.Completion completion) {
        if (completion == null) {
            return "";
        }
        String detail = completion.detail.isEmpty() ? "-" : completion.detail;
        String description = completion.description.isEmpty() ? "-" : completion.description;
        return tr("gui.modern.path.code.fmt.tooltip", tr(completionKindName(completion.kind)), tr(detail), tr(description));
    }

    private static String completionKindName(ExpressionLanguageSupport.CompletionKind kind) {
        if (kind == null) return "gui.modern.path.code.u001";
        switch (kind) {
            case FUNCTION: return "gui.modern.path.code.u002";
            case VARIABLE: return "gui.modern.path.code.u003";
            case KEYWORD: return "gui.modern.path.code.u004";
            case LITERAL: return "gui.modern.path.code.u005";
            case OPERATOR: return "gui.modern.path.code.u006";
            case DYNAMIC:
            default: return "gui.modern.path.code.u001";
        }
    }

    private static Map<Integer, Integer> bracketColors(List<ExpressionLanguageSupport.Span> spans) {
        Map<Integer, Integer> colors = new HashMap<Integer, Integer>();
        int[] palette = new int[] { 0xFF79C7FF, 0xFFC792EA, 0xFFF0B55E, 0xFF5FD39A };
        int depth = 0;
        for (ExpressionLanguageSupport.Span span : spans) {
            if (span == null || span.kind != ExpressionLanguageSupport.TokenKind.DELIMITER
                    || span.text.length() != 1) {
                continue;
            }
            char c = span.text.charAt(0);
            if (c == '(' || c == '[' || c == '{') {
                colors.put(Integer.valueOf(span.start), Integer.valueOf(palette[depth % palette.length]));
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
                colors.put(Integer.valueOf(span.start), Integer.valueOf(palette[depth % palette.length]));
            }
        }
        return colors;
    }

    private static String completionKindShort(ExpressionLanguageSupport.CompletionKind kind) {
        if (kind == null) {
            return "dyn";
        }
        switch (kind) {
            case FUNCTION:
                return "fn";
            case VARIABLE:
                return "var";
            case KEYWORD:
                return "key";
            case LITERAL:
                return "lit";
            case OPERATOR:
                return "op";
            case DYNAMIC:
            default:
                return "dyn";
        }
    }

    private static int completionKindColor(ExpressionLanguageSupport.CompletionKind kind) {
        if (kind == null) {
            return ModernUiRenderer.SUCCESS;
        }
        switch (kind) {
            case FUNCTION:
                return ExpressionLanguageSupport.COLOR_FUNCTION;
            case VARIABLE:
                return ExpressionLanguageSupport.COLOR_IDENTIFIER;
            case KEYWORD:
                return ExpressionLanguageSupport.COLOR_KEYWORD;
            case LITERAL:
                return ModernUiRenderer.WARNING;
            case OPERATOR:
                return ExpressionLanguageSupport.COLOR_OPERATOR;
            case DYNAMIC:
            default:
                return ModernUiRenderer.SUCCESS;
        }
    }

    private static String functionName(String value) {
        String result = value == null ? "" : value.trim();
        int parenthesis = result.indexOf('(');
        if (parenthesis >= 0) {
            result = result.substring(0, parenthesis).trim();
        }
        return result;
    }

    private boolean isInsideStringOrComment(int offset) {
        char quote = 0;
        boolean escaped = false;
        boolean comment = false;
        int limit = clamp(offset, 0, text.length());
        for (int index = 0; index < limit; index++) {
            char value = text.charAt(index);
            if (comment) {
                if (value == '\n') {
                    comment = false;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == quote) {
                    quote = 0;
                }
                continue;
            }
            if (value == '#') {
                comment = true;
            } else if (value == '/' && index + 1 < limit && text.charAt(index + 1) == '/') {
                comment = true;
                index++;
            } else if (value == '\'' || value == '"') {
                quote = value;
            }
        }
        return quote != 0 || comment;
    }

    private void clampCursorAndSelection() {
        cursor = clamp(cursor, 0, text.length());
        selectionAnchor = clamp(selectionAnchor, 0, text.length());
    }

    private boolean canReplaceFully(int start, int end, String replacement) {
        int safeStart = clamp(Math.min(start, end), 0, text.length());
        int safeEnd = clamp(Math.max(start, end), safeStart, text.length());
        int replacementLength = prepareInput(replacement).length();
        return text.length() - (safeEnd - safeStart) + replacementLength <= MAX_TEXT_LENGTH;
    }

    private boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    private int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    private int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    private static boolean isMatchingPair(char open, char close) {
        return open == '(' && close == ')' || open == '[' && close == ']'
                || open == '\'' && close == '\'' || open == '"' && close == '"';
    }

    private static boolean isIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static int previousOffset(String value, int offset) {
        int safe = clamp(offset, 0, value.length());
        if (safe <= 0) {
            return 0;
        }
        int result = safe - 1;
        if (result > 0 && Character.isLowSurrogate(value.charAt(result))
                && Character.isHighSurrogate(value.charAt(result - 1))) {
            result--;
        }
        return result;
    }

    private static int nextOffset(String value, int offset) {
        int safe = clamp(offset, 0, value.length());
        if (safe >= value.length()) {
            return value.length();
        }
        int result = safe + 1;
        if (Character.isHighSurrogate(value.charAt(safe)) && result < value.length()
                && Character.isLowSurrogate(value.charAt(result))) {
            result++;
        }
        return result;
    }

    private static String prepareInput(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '\t') {
                result.append(TAB_TEXT);
            } else if (character == '\n' || character >= 32 && character != 127 && character != '\u00A7') {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String safePrefix(String value, int maximumLength) {
        int length = clamp(maximumLength, 0, value.length());
        if (length > 0 && length < value.length() && Character.isHighSurrogate(value.charAt(length - 1))) {
            length--;
        }
        return value.substring(0, length);
    }

    private static int dimColor(int color) {
        int alpha = color >>> 24 & 0xFF;
        int red = color >>> 16 & 0xFF;
        int green = color >>> 8 & 0xFF;
        int blue = color & 0xFF;
        red = (red * 2 + 72) / 3;
        green = (green * 2 + 78) / 3;
        blue = (blue * 2 + 84) / 3;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static boolean sameRect(ModernMainLayout.Rect left, ModernMainLayout.Rect right) {
        return left != null && right != null && left.x == right.x && left.y == right.y
                && left.width == right.width && left.height == right.height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Snapshot {
        private final String text;
        private final int cursor;
        private final int selectionAnchor;
        private final int verticalScroll;
        private final int horizontalScroll;

        private Snapshot(String text, int cursor, int selectionAnchor, int verticalScroll, int horizontalScroll) {
            this.text = text;
            this.cursor = cursor;
            this.selectionAnchor = selectionAnchor;
            this.verticalScroll = verticalScroll;
            this.horizontalScroll = horizontalScroll;
        }
    }

    private static final class LineInfo {
        private final int start;
        private final int end;
        private final String text;
        private final List<ColoredRun> runs = new ArrayList<ColoredRun>();

        private LineInfo(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text == null ? "" : text;
        }
    }

    private static final class ColoredRun {
        private final int start;
        private final int end;
        private final int color;

        private ColoredRun(int start, int end, int color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }
    private static String tr(String key) {
        return com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(key, args);
    }

}
