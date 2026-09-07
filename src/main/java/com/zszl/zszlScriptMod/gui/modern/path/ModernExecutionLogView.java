package com.zszl.zszlScriptMod.gui.modern.path;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager.ExecutionEvent;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager.SessionSnapshot;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Native execution history, with independent clipped panes and a reserved action footer. */
final class ModernExecutionLogView {
    private final Consumer<String> notify;
    private final ExecutionLogSelection selection = new ExecutionLogSelection();
    private final ModernHoverScrollbar sessionBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar eventBar = new ModernHoverScrollbar();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
    private final SimpleDateFormat date = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);
    private List<SessionSnapshot> sessions = new ArrayList<>();
    private List<ExecutionEvent> events = new ArrayList<>();
    private final List<Integer> visible = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<Hit> sessionHits = new ArrayList<>();
    private final List<Hit> eventHits = new ArrayList<>();
    private final List<String> types = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private GuiTextField search;
    private FontRenderer font;
    private Rect bounds, list, detail, divider, filter, variables;
    private int selectedSession = -1, sessionScroll, eventScroll, sessionMax, eventMax;
    private int cachedWidth = -1, contentHeight;
    private String query = "", type = "";
    private boolean dirty = true, dragging, showVariables;
    private double ratio = 0.29D;
    private long lastDividerClick;

    private static final class Card {
        final int index, offset, height;
        final List<String> message, status, variables;
        Card(int index, int offset, List<String> message, List<String> status, List<String> variables) {
            this.index = index;
            this.offset = offset;
            this.message = message;
            this.status = status;
            this.variables = variables;
            height = 46 + (message.size() + status.size() + variables.size()) * 12
                    + (status.isEmpty() ? 0 : 5) + (variables.isEmpty() ? 0 : 5);
        }
    }

    private static final class Hit {
        final int index;
        final Rect bounds, copy;
        Hit(int index, Rect bounds, Rect copy) { this.index = index; this.bounds = bounds; this.copy = copy; }
    }

    private static final class Action {
        final Rect bounds;
        final Runnable run;
        final boolean enabled;
        Action(Rect bounds, Runnable run, boolean enabled) { this.bounds = bounds; this.run = run; this.enabled = enabled; }
    }

    ModernExecutionLogView(Consumer<String> notify) { this.notify = notify; }

    void reload() {
        SessionSnapshot old = session();
        String id = old == null ? "" : old.getSessionId();
        sessions = ExecutionLogManager.getSessionsSnapshot();
        selectedSession = sessions.isEmpty() ? -1 : 0;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(id)) selectedSession = i;
        }
        changeSession(selectedSession);
        release();
    }

    private SessionSnapshot session() {
        return selectedSession < 0 || selectedSession >= sessions.size() ? null : sessions.get(selectedSession);
    }

    private void changeSession(int index) {
        selectedSession = index;
        events = session() == null ? new ArrayList<>() : session().getEvents();
        types.clear();
        types.add("");
        for (ExecutionEvent event : events) if (!types.contains(event.getType())) types.add(event.getType());
        if (!types.contains(type)) type = "";
        selection.clear();
        eventScroll = 0;
        dirty = true;
    }

    void draw(FontRenderer font, Rect area, int mx, int my) {
        this.font = font;
        bounds = area;
        actions.clear();
        sessionHits.clear();
        eventHits.clear();
        if (search == null) {
            search = new GuiTextField(7810, font, 0, 0, 1, 20);
            search.setMaxStringLength(256);
            search.setEnableBackgroundDrawing(false);
        }
        // The 24px gap contains the entire shared divider hit target.
        ModernSplitPane.Split split = ModernSplitPane.calculate(area.width - 24, ratio, 170, 290, 110, 200);
        int bodyHeight = Math.max(1, area.height - 58);
        list = new Rect(area.x, area.y + 22, split.firstWidth, Math.max(1, bodyHeight - 22));
        int dx = list.right() + 24, dw = split.secondWidth;
        divider = ModernSplitPane.verticalDividerBounds(list.x, list.width, 24, area.y, bodyHeight);
        text(t("sessions", sessions.size()), area.x, area.y + 5, ModernUiRenderer.SUBTLE_TEXT, list.width);
        text(t("details"), dx, area.y + 5, ModernUiRenderer.SUBTLE_TEXT, dw);
        panel(list, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        drawSessions(mx, my);

        Rect summary = new Rect(dx, area.y + 22, dw, 64);
        panel(summary, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        SessionSnapshot selected = session();
        if (selected == null) {
            text(t("empty.sessions"), dx + 12, summary.y + 24, ModernUiRenderer.MUTED_TEXT, dw - 24);
        } else {
            int accent = sessionAccent(selected);
            text(selected.getSequenceName(), dx + 12, summary.y + 10, ModernUiRenderer.TEXT, dw - 94);
            badge(result(selected), new Rect(summary.right() - 72, summary.y + 6, 60, 18), accent);
            text(date.format(new Date(selected.getStartTime())) + "  ·  " + selected.getDurationMs() + " ms  ·  "
                    + t(selected.isBackground() ? "background" : "foreground"), dx + 12, summary.y + 28,
                    ModernUiRenderer.SUBTLE_TEXT, dw - 24);
            text(selected.getFinishReason().isEmpty() ? selected.getFinalStatus() : selected.getFinishReason(),
                    dx + 12, summary.y + 46, ModernUiRenderer.MUTED_TEXT, dw - 24);
        }
        Rect searchBox = new Rect(dx, summary.bottom() + 8, Math.max(1, dw - 100), 22);
        panel(searchBox, ModernUiRenderer.SHELL, search.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        search.x = searchBox.x + 7;
        search.y = searchBox.y + 7;
        search.width = Math.max(1, searchBox.width - 14);
        search.height = 12;
        search.setTextColor(ModernUiRenderer.TEXT);
        ModernUiRenderer.drawTextField(search);
        if (search.getText().isEmpty() && !search.isFocused())
            text(t("search"), search.x, search.y, ModernUiRenderer.MUTED_TEXT, search.width);
        filter = new Rect(searchBox.right() + 6, searchBox.y, 94, 22);
        button(filter, type.isEmpty() ? t("all.types") : type.toUpperCase(Locale.ROOT), !type.isEmpty(), true, mx, my,
                () -> { type = types.get((types.indexOf(type) + 1) % types.size()); invalidateFilter(); });
        int vy = searchBox.bottom() + 7;
        variables = new Rect(dx, vy, dw, 18);
        text((showVariables ? "− " : "+ ") + t("variables", selected == null ? 0 : selected.getInitialVariables().size()),
                dx + 2, vy + 4, variables.contains(mx, my) ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT, dw);
        int variableHeight = 0;
        if (showVariables && selected != null) {
            List<String> lines = wrap(selected.getInitialVariables().toString(), dw - 20);
            variableHeight = Math.min(48, lines.size() * 12 + 8);
            Rect variableBody = new Rect(dx, variables.bottom(), dw, variableHeight);
            panel(variableBody, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.beginClip(variableBody);
            drawLines(lines, dx + 8, variableBody.y + 4, ModernUiRenderer.SUBTLE_TEXT, dw - 16);
            ModernUiRenderer.endClip();
        }
        int eventY = variables.bottom() + variableHeight + 6;
        detail = new Rect(dx, eventY, dw, Math.max(1, area.y + bodyHeight - eventY));
        rebuildCards();
        panel(detail, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        drawEvents(mx, my);
        ModernSplitPane.drawVerticalDivider(divider, mx, my, dragging);
        drawFooter(area.y + bodyHeight + 8, mx, my);
    }

    private void drawSessions(int mx, int my) {
        int total = sessions.size() * 68 + 10;
        sessionMax = Math.max(0, total - list.height);
        sessionScroll = clamp(sessionScroll, sessionMax);
        ModernUiRenderer.beginClip(list);
        for (int i = 0; i < sessions.size(); i++) {
            Rect row = new Rect(list.x + 6, list.y + 6 + i * 68 - sessionScroll, list.width - 22, 60);
            if (row.bottom() <= list.y || row.y >= list.bottom()) continue;
            SessionSnapshot item = sessions.get(i);
            boolean active = i == selectedSession;
            int accent = sessionAccent(item);
            panel(row, active ? ModernUiRenderer.SURFACE_PRESSED : row.contains(mx, my)
                    ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    active ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(row.x, row.y + 9, 3, row.height - 18, 1, accent);
            text(item.getSequenceName(), row.x + 10, row.y + 9, ModernUiRenderer.TEXT, row.width - 20);
            text(result(item) + "  ·  " + t(item.isBackground() ? "background" : "foreground"), row.x + 10,
                    row.y + 25, accent, row.width - 20);
            text(date.format(new Date(item.getStartTime())) + "  ·  " + item.getEvents().size(), row.x + 10,
                    row.y + 43, ModernUiRenderer.MUTED_TEXT, row.width - 20);
            sessionHits.add(new Hit(i, row, null));
        }
        ModernUiRenderer.endClip();
        sessionBar.draw(list, sessionScroll, sessionMax, list.height, total, mx, my, value -> sessionScroll = value);
    }

    private void rebuildCards() {
        if (!dirty && cachedWidth == detail.width) return;
        dirty = false;
        cachedWidth = detail.width;
        visible.clear();
        cards.clear();
        int offset = 6;
        for (int i = 0; i < events.size(); i++) {
            ExecutionEvent event = events.get(i);
            if (!type.isEmpty() && !type.equals(event.getType())) continue;
            if (!eventText(event).toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(i);
            List<String> message = wrap(event.getMessage(), detail.width - 44);
            List<String> status = event.getStatus().isEmpty() ? new ArrayList<>()
                    : wrap(t("status", event.getStatus()), detail.width - 44);
            List<String> vars = event.getVariablePreview().isEmpty() ? new ArrayList<>()
                    : wrap(t("snapshot", event.getVariablePreview()), detail.width - 44);
            Card card = new Card(i, offset, message, status, vars);
            cards.add(card);
            offset += card.height + 8;
        }
        selection.retainVisible(visible);
        contentHeight = offset;
    }

    private void drawEvents(int mx, int my) {
        eventMax = Math.max(0, contentHeight - detail.height);
        eventScroll = clamp(eventScroll, eventMax);
        ModernUiRenderer.beginClip(detail);
        if (cards.isEmpty()) {
            text(t("empty.events"), detail.x + 14, detail.y + 20, ModernUiRenderer.TEXT, detail.width - 28);
            text(t("empty.hint"), detail.x + 14, detail.y + 38, ModernUiRenderer.MUTED_TEXT, detail.width - 28);
        }
        for (Card card : cards) {
            Rect row = new Rect(detail.x + 6, detail.y + card.offset - eventScroll, detail.width - 22, card.height);
            if (row.bottom() <= detail.y || row.y >= detail.bottom()) continue;
            ExecutionEvent event = events.get(card.index);
            boolean active = selection.contains(card.index);
            panel(row, active ? ModernUiRenderer.SURFACE_PRESSED : row.contains(mx, my)
                    ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    active ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            int accent = eventAccent(event);
            ModernUiRenderer.drawRoundedRect(row.x, row.y + 9, 3, row.height - 18, 1, accent);
            badge(event.getType().toUpperCase(Locale.ROOT), new Rect(row.x + 10, row.y + 8, 58, 16), accent);
            text(time.format(new Date(event.getTimestamp())), row.x + 76, row.y + 12,
                    ModernUiRenderer.MUTED_TEXT, row.width - 132);
            Rect copy = new Rect(row.right() - 44, row.y + 7, 36, 18);
            panel(copy, copy.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    copy.contains(mx, my) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(t("copy"), copy.x + 5, copy.y + 5, ModernUiRenderer.SUBTLE_TEXT, copy.width - 10);
            text("#" + (card.index + 1) + "  ·  " + t("position", event.getStepIndex(), event.getActionIndex()),
                    row.x + 10, row.y + 31, ModernUiRenderer.MUTED_TEXT, row.width - 20);
            int y = drawLines(card.message, row.x + 10, row.y + 46, ModernUiRenderer.TEXT, row.width - 20);
            if (!card.status.isEmpty()) y = drawLines(card.status, row.x + 10, y + 5, ModernUiRenderer.SUBTLE_TEXT, row.width - 20);
            if (!card.variables.isEmpty()) drawLines(card.variables, row.x + 10, y + 5, ModernUiRenderer.MUTED_TEXT, row.width - 20);
            eventHits.add(new Hit(card.index, row, copy));
        }
        ModernUiRenderer.endClip();
        eventBar.draw(detail, eventScroll, eventMax, detail.height, contentHeight, mx, my, value -> eventScroll = value);
    }

    private void drawFooter(int y, int mx, int my) {
        ModernUiRenderer.drawRoundedRect(bounds.x, y - 3, bounds.width, 1, 0, ModernUiRenderer.BORDER_SUBTLE);
        int gap = 5;
        int width = Math.max(1, (bounds.width - gap * 5) / 6);
        String[] labels = {t("refresh"), t("copy.selected", selection.size()), t("copy.filtered"),
                t("copy.session"), t("export"), t("clear")};
        Runnable[] handlers = {() -> { reload(); notify.accept(t("refreshed")); }, () -> copyEvents(false),
                () -> copyEvents(true), this::copySession, this::exportSession,
                () -> { ExecutionLogManager.clearSessions(); reload(); notify.accept(t("cleared")); }};
        boolean[] enabled = {true, selection.size() > 0, !visible.isEmpty(), session() != null, session() != null, !sessions.isEmpty()};
        for (int i = 0; i < labels.length; i++) {
            button(new Rect(bounds.x + i * (width + gap), y + 3, width, 22), labels[i], i == 1,
                    enabled[i], mx, my, handlers[i]);
        }
        text(t("hint", visible.size(), events.size(), selection.size()), bounds.x + 2, y + 33,
                ModernUiRenderer.MUTED_TEXT, bounds.width - 4);
    }

    boolean click(int mx, int my, int button) {
        if (bounds == null || button != 0) return false;
        if (divider.contains(mx, my)) {
            long now = System.currentTimeMillis();
            dragging = now - lastDividerClick > 350;
            if (!dragging) ratio = 0.29D;
            lastDividerClick = dragging ? now : 0;
            return true;
        }
        search.setFocused(mx >= search.x - 7 && mx < search.x + search.width + 7
                && my >= search.y - 7 && my < search.y + 15);
        if (search.isFocused()) { search.mouseClicked(mx, my, button); return true; }
        if (sessionBar.beginDrag(mx, my) || eventBar.beginDrag(mx, my)) return true;
        for (Action action : actions) if (action.bounds.contains(mx, my)) {
            if (action.enabled) action.run.run();
            return true;
        }
        if (variables.contains(mx, my)) { showVariables = !showVariables; return true; }
        if (list.contains(mx, my)) for (Hit hit : sessionHits) if (hit.bounds.contains(mx, my)) {
            changeSession(hit.index);
            return true;
        }
        if (detail.contains(mx, my)) for (Hit hit : eventHits) if (hit.bounds.contains(mx, my)) {
            if (hit.copy.contains(mx, my)) copy(eventText(events.get(hit.index)), 1);
            else selection.click(hit.index, visible, GuiScreen.isCtrlKeyDown(), GuiScreen.isShiftKeyDown());
            return true;
        }
        return bounds.contains(mx, my);
    }

    boolean drag(int mx, int my) {
        if (dragging) {
            ratio = ModernSplitPane.calculateFromPointer(bounds.width - 24, mx - bounds.x - 12,
                    170, 290, 110, 200).ratio;
            return true;
        }
        return sessionBar.applyDrag(mx, my) || eventBar.applyDrag(mx, my);
    }

    boolean release() {
        boolean handled = dragging || sessionBar.isDragging() || eventBar.isDragging();
        dragging = false;
        sessionBar.endDrag();
        eventBar.endDrag();
        return handled;
    }

    boolean wheel(int amount, int mx, int my) {
        if (list != null && list.contains(mx, my)) sessionScroll = clamp(sessionScroll + (amount > 0 ? -48 : 48), sessionMax);
        else if (detail != null && detail.contains(mx, my)) eventScroll = clamp(eventScroll + (amount > 0 ? -36 : 36), eventMax);
        return true;
    }

    boolean key(char character, int key) {
        if (search == null) return false;
        if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_F) { search.setFocused(true); return true; }
        if (search.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) { search.setFocused(false); return true; }
            if (search.textboxKeyTyped(character, key)) {
                query = search.getText().trim().toLowerCase(Locale.ROOT);
                invalidateFilter();
            }
            return true;
        }
        if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_A) { selection.selectAll(visible); return true; }
        if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_C) { copyEvents(false); return true; }
        if (key == Keyboard.KEY_ESCAPE && selection.size() > 0) { selection.clear(); return true; }
        if (key == Keyboard.KEY_F5) { reload(); notify.accept(t("refreshed")); return true; }
        // Do not leak editor shortcuts into the underlying path editor.
        return key != Keyboard.KEY_ESCAPE;
    }

    private void invalidateFilter() { dirty = true; eventScroll = 0; if (detail != null) rebuildCards(); }

    private void copyEvents(boolean all) {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (int index : visible) if (all || selection.contains(index)) {
            if (count++ > 0) text.append("\n\n");
            text.append(eventText(events.get(index)));
        }
        if (count > 0) copy(text.toString(), count);
        else notify.accept(t("select.first"));
    }

    private String eventText(ExecutionEvent event) {
        return date.format(new Date(event.getTimestamp())) + " [" + event.getType() + "] "
                + t("position", event.getStepIndex(), event.getActionIndex()) + "\n" + event.getMessage()
                + (event.getStatus().isEmpty() ? "" : "\n" + t("status", event.getStatus()))
                + (event.getVariablePreview().isEmpty() ? "" : "\n" + t("snapshot", event.getVariablePreview()));
    }

    private void copySession() {
        if (session() == null) return;
        String text = ExecutionLogManager.getSessionText(session().getSessionId());
        if (text.isEmpty()) { notify.accept(t("unavailable")); return; }
        GuiScreen.setClipboardString(text);
        notify.accept(t("session.copied"));
    }

    private void exportSession() {
        if (session() == null) return;
        Path path = ExecutionLogManager.exportSession(session().getSessionId());
        notify.accept(path == null ? t("export.failed") : t("exported", path.toAbsolutePath()));
    }

    private void copy(String text, int count) { GuiScreen.setClipboardString(text); notify.accept(t("copied", count)); }
    private String result(SessionSnapshot session) { return t(!session.isFinished() ? "running" : session.isSuccess() ? "success" : "stopped"); }
    private int sessionAccent(SessionSnapshot session) { return !session.isFinished() ? ModernUiRenderer.WARNING
            : session.isSuccess() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.DANGER; }
    private int eventAccent(ExecutionEvent event) {
        String type = event.getType().toLowerCase(Locale.ROOT);
        if (type.contains("error") || type.contains("fail")) return ModernUiRenderer.DANGER;
        if (type.contains("warn")) return ModernUiRenderer.WARNING;
        if (type.contains("finish")) return session() != null && session().isSuccess() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING;
        return type.contains("status") ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.ACCENT;
    }
    private List<String> wrap(String text, int width) { return font.listFormattedStringToWidth(text, Math.max(1, width)); }
    private int drawLines(List<String> lines, int x, int y, int color, int width) {
        for (String line : lines) { text(line, x, y, color, width); y += 12; }
        return y;
    }
    private void text(String value, int x, int y, int color, int width) { ModernUiRenderer.drawText(font, value, x, y, color, Math.max(1, width)); }
    private void panel(Rect r, int fill, int border) { ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 5, fill, border); }
    private void badge(String value, Rect r, int color) {
        panel(r, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        text(value, r.x + 6, r.y + 5, color, r.width - 12);
    }
    private void button(Rect r, String label, boolean primary, boolean enabled, int mx, int my, Runnable action) {
        panel(r, enabled && r.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                enabled && (primary || r.contains(mx, my)) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        text(label, r.x + Math.max(5, (r.width - font.getStringWidth(label)) / 2), r.y + 7,
                enabled ? primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, r.width - 10);
        actions.add(new Action(r, action, enabled));
    }
    private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }
    private static String t(String key, Object... args) { return ModernFormI18n.tr("gui.modern.execution." + key, args); }
}
