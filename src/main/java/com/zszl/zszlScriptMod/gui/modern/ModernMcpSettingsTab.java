package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.mcp.*;
import com.google.gson.*;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import java.text.SimpleDateFormat;
import java.util.*;

/** Live MCP console. UI reads immutable snapshots while HTTP workers record calls. */
public final class ModernMcpSettingsTab implements ModernSettingsTab {
    private static final char EVENT_FILTER_SEPARATOR = 0;
    private ModernMainLayout.Rect bounds, toggle, apply, clear, copyAddress, argsTab, resultTab, copyBody,
            list, detail, callsTab, eventsTab, resetSearch, eventFilterControl, latest, footer, splitDivider;
    private final ModernHoverScrollbar listBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar detailBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar horizontalBar = new ModernHoverScrollbar(ModernHoverScrollbar.Axis.HORIZONTAL);
    private final ModernScrollableDropdown eventFilterDropdown = new ModernScrollableDropdown();
    private GuiTextField port, search;
    private List<McpCallHistory.Entry> visible = Collections.emptyList();
    private McpCallHistory.Entry selected;
    private long selectedId = -1, version = -1;
    private int listScroll, detailScroll, column, listRows = 1, detailRows = 1, textWidth = 1;
    private int maxListScroll, maxDetailScroll, maxColumn;
    private boolean showingResult = true, listFocused;
    private boolean draggingSplit;
    private boolean eventMode;
    private double recordsRatio = ModernSplitPane.DEFAULT_RATIO;
    private static final String RECORDS_SPLIT_KEY = "mcp.records_inspector";
    private String status = "", lastQuery = "", lastEventFilter = "", cachedBody;
    private long eventOptionsVersion = -1;
    private long statusUntil;
    private boolean statusError;
    private String[] lines = new String[0];
    private int longestLine;
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");
    private static String tr(String key, Object... args) { return I18n.format("gui.mcp." + key, args); }

    @Override public void ensureInitialized(FontRenderer font) {
        if (port != null) return;
        recordsRatio = MainUiLayoutManager.getModernSplitRatio(RECORDS_SPLIT_KEY, recordsRatio);
        port = new GuiTextField(7101, font, 0, 0, 62, 20);
        port.setMaxStringLength(5);
        port.setText(String.valueOf(MythosScriptMcpServer.settings().port));
        search = new GuiTextField(7102, font, 0, 0, 100, 20);
        search.setMaxStringLength(512);
    }
    @Override public void updateScreen() {
        if (port != null) { port.updateCursorCounter(); search.updateCursorCounter(); }
    }
    private void refresh() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        long current = eventMode ? McpEventJournal.INSTANCE.version() : McpCallHistory.INSTANCE.version();
        if (eventMode) refreshEventFilterOptions(current);
        String selectedEventFilter = eventMode ? eventFilterDropdown.value() : "";
        if (current == version && query.equals(lastQuery) && selectedEventFilter.equals(lastEventFilter)) return;
        if (!query.equals(lastQuery) || !selectedEventFilter.equals(lastEventFilter)) listScroll = 0;
        version = current; lastQuery = query; lastEventFilter = selectedEventFilter;
        visible = new ArrayList<>();
        if (eventMode) {
            JsonObject p=McpJson.object("limit",500);
            StringBuilder words=new StringBuilder();
            try {
                for(String part:query.split("\\s+")) {
                    if(part.startsWith("g=")) p.add("groups",McpJson.GSON.toJsonTree(part.substring(2).split(",")));
                    else if(part.startsWith("id=")) p.add("entityIds",McpJson.GSON.toJsonTree(part.substring(3).split(",")));
                    else if(part.startsWith("from=")) p.addProperty("fromMs",Long.parseLong(part.substring(5)));
                    else if(part.startsWith("to=")) p.addProperty("toMs",Long.parseLong(part.substring(3)));
                    else words.append(part).append(' ');
                }
                if(words.toString().trim().length()>0) p.addProperty("query",words.toString().trim());
                addEventFilter(p, selectedEventFilter);
                for(JsonElement e:McpEventJournal.INSTANCE.read(p).getAsJsonArray("events")) visible.add(McpCallHistory.Entry.event(e.getAsJsonObject()));
                Collections.reverse(visible);
            } catch(RuntimeException e) { notifyStatus(tr("error", e.getMessage()), true); }
        } else {
        for (McpCallHistory.Entry e : McpCallHistory.INSTANCE.snapshot())
            if ((e.tool + " " + e.requestId + (e.failed ? " error 失败" : "")).toLowerCase(Locale.ROOT).contains(query)) visible.add(e);
        }
        selected = null;
        for (McpCallHistory.Entry e : visible) if (e.sequence == selectedId) selected = e;
        if (selected == null) {
            selectedId = visible.isEmpty() ? -1 : visible.get(0).sequence;
            selected = visible.isEmpty() ? null : visible.get(0);
            cachedBody = null;
        }
    }

    private void refreshEventFilterOptions(long current) {
        if (current == eventOptionsVersion) return;
        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add("");
        String selected = eventFilterDropdown.value();
        if (!selected.isEmpty()) options.add(selected);
        try {
            JsonArray events = McpEventJournal.INSTANCE.read(McpJson.object("limit",500)).getAsJsonArray("events");
            for (JsonElement element : events) {
                JsonObject event = element.getAsJsonObject();
                String group = event.get("group").getAsString();
                String type = event.get("type").getAsString();
                if (!group.isEmpty() && !type.isEmpty()) options.add(eventFilterValue(group, type));
            }
        } catch (RuntimeException ignored) {
            // The event list itself will report any read error; keep the all-events option usable.
        }
        List<String> ordered = new ArrayList<>(options);
        if (ordered.size() > 1) {
            List<String> eventTypes = ordered.subList(1, ordered.size());
            Collections.sort(eventTypes, String.CASE_INSENSITIVE_ORDER);
        }
        String[] values = ordered.toArray(new String[ordered.size()]);
        String[] labels = new String[values.length];
        labels[0] = "gui.mcp.all_events";
        for (int i = 1; i < values.length; i++) labels[i] = eventFilterLabel(values[i]);
        eventFilterDropdown.setOptions(values, labels);
        eventOptionsVersion = current;
    }

    private static void addEventFilter(JsonObject filter, String value) {
        if (value == null || value.isEmpty()) return;
        int separator = value.indexOf(EVENT_FILTER_SEPARATOR);
        if (separator <= 0 || separator >= value.length() - 1) return;
        filter.add("groups", McpJson.GSON.toJsonTree(new String[] {value.substring(0, separator)}));
        filter.add("types", McpJson.GSON.toJsonTree(new String[] {value.substring(separator + 1)}));
    }

    private static String eventFilterValue(String group, String type) { return group + EVENT_FILTER_SEPARATOR + type; }

    private static String eventFilterLabel(String value) {
        int separator = value.indexOf(EVENT_FILTER_SEPARATOR);
        return separator < 0 ? value : value.substring(0, separator) + " / " + value.substring(separator + 1);
    }
    private void loadBody(FontRenderer font) {
        String body = selected == null ? "" : showingResult
                ? (selected.pending() ? tr("pending") : selected.response) : selected.arguments;
        if (body.equals(cachedBody)) return;
        cachedBody = body; lines = selected == null ? new String[0] : body.split("\n", -1); longestLine = 0;
        for (int i = 0; i < lines.length; i++) {
            // Display formatting codes literally, while copying the untouched original body.
            lines[i] = lines[i].replace("\u00a7", "\\u00a7").replace("\t", "    ").replace("\r", "");
            longestLine = Math.max(longestLine, font.getStringWidth(lines[i]));
        }
        detailScroll = 0; column = 0;
    }
    @Override public void draw(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        ensureInitialized(font);
        bounds = area;
        if (!status.isEmpty() && System.currentTimeMillis() > statusUntil) status = "";
        refresh();
        loadBody(font);
        ModernMcpLayout layout = new ModernMcpLayout(area, recordsRatio);
        splitDivider = layout.divider;
        ModernUiRenderer.beginClip(area);
        try {
            drawConnection(font, layout, mx, my);
            int tabWidth = Math.min(100, Math.max(68, (layout.feeds.width - 96) / 2));
            callsTab = rect(layout.feeds.x, layout.feeds.y, tabWidth, 24);
            eventsTab = rect(callsTab.right() + 4, callsTab.y, tabWidth, 24);
            button(font, callsTab, tr("calls"), !eventMode, mx, my);
            button(font, eventsTab, tr("events"), eventMode, mx, my);
            clear = eventMode ? null : rect(layout.feeds.right() - 86, callsTab.y, 86, 24);
            if (clear != null) button(font, clear, tr("clear_records"), false, !visible.isEmpty()
                    || McpCallHistory.INSTANCE.total() > 0, true, mx, my);
            drawRecords(font, layout.sidebar, mx, my);
            drawInspector(font, layout.inspector, mx, my);
            ModernSplitPane.drawVerticalDivider(splitDivider, mx, my, draggingSplit);
            if (eventMode) eventFilterDropdown.drawMenu(font, bounds, mx, my);
        } finally {
            ModernUiRenderer.endClip();
        }
    }

    private void drawConnection(FontRenderer font, ModernMcpLayout layout, int mx, int my) {
        ModernMainLayout.Rect header = layout.header;
        panel(header);
        boolean running = MythosScriptMcpServer.isRunning();
        boolean enabled = MythosScriptMcpServer.settings().enabled;
        String error = MythosScriptMcpServer.lastError();
        int stateColor = !error.isEmpty() ? ModernUiRenderer.DANGER
                : running ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT;
        ModernUiRenderer.drawStatusDot(header.x + 10, header.y + 12, stateColor);
        int infoWidth = layout.inlineControls ? layout.controls.x - header.x - 24 : header.width - 26;
        text(font, "MCP  /  " + tr(running ? "connected" : "stopped"), header.x + 22,
                header.y + 10, infoWidth, ModernUiRenderer.TEXT);
        text(font, endpoint(), header.x + 10, header.y + 24, infoWidth, ModernUiRenderer.SUBTLE_TEXT);
        int controlX = layout.controls.x, controlY = layout.controls.y;
        int unit = Math.max(42, (layout.controls.width - 52) / 4);
        toggle = rect(controlX, controlY, unit, 22);
        button(font, toggle, tr(enabled ? "disable" : "enable"), enabled, mx, my);
        text(font, tr("port"), toggle.right() + 6, controlY + 7, 24, ModernUiRenderer.SUBTLE_TEXT);
        port.x = toggle.right() + 30; port.y = controlY; port.width = unit - 8; port.height = 22;
        drawInput(port);
        apply = rect(port.x + port.width + 6, controlY, unit, 22);
        button(font, apply, tr("apply"), false, isDirty(), false, mx, my);
        copyAddress = rect(apply.right() + 6, controlY, unit + 10, 22);
        button(font, copyAddress, tr("address"), false, mx, my);
        String message = !error.isEmpty() ? error : !status.isEmpty() ? status
                : clientLine() + "   ·   " + tr("summary", McpCallHistory.INSTANCE.total(), McpCallHistory.INSTANCE.failures());
        text(font, message, header.x + 10, header.bottom() - 16, header.width - 20,
                !error.isEmpty() || (!status.isEmpty() && statusError) ? ModernUiRenderer.DANGER : ModernUiRenderer.SUBTLE_TEXT);
    }

    private void drawRecords(FontRenderer font, ModernMainLayout.Rect pane, int mx, int my) {
        panel(pane);
        latest = rect(pane.right() - 56, pane.y + 5, 50, 20);
        text(font, tr("records", visible.size()), pane.x + 9, pane.y + 11, pane.width - 73, ModernUiRenderer.TEXT);
        button(font, latest, tr("latest"), false, !visible.isEmpty(), false, mx, my);
        search.x = pane.x + 7; search.y = pane.y + 31; search.height = 22;
        int searchAndResetWidth = Math.max(1, pane.width - 43);
        int eventFilterWidth = eventMode ? Math.min(108, Math.max(72, pane.width / 4)) : 0;
        if (eventMode) eventFilterWidth = Math.min(eventFilterWidth, Math.max(1, searchAndResetWidth - 52));
        search.width = eventMode ? Math.max(1, searchAndResetWidth - eventFilterWidth - 4) : searchAndResetWidth;
        drawInput(search);
        if (search.getText().isEmpty() && !search.isFocused())
            text(font, tr(eventMode ? "event_search" : "filter"), search.x + 5, search.y + 7,
                    search.width - 10, ModernUiRenderer.MUTED_TEXT);
        if (eventMode) {
            eventFilterControl = rect(search.x + search.width + 4, search.y, eventFilterWidth, 22);
            eventFilterDropdown.drawButton(font, eventFilterControl, mx, my);
            resetSearch = rect(eventFilterControl.right() + 4, search.y, 25, 22);
        } else {
            eventFilterControl = null;
            resetSearch = rect(search.x + search.width + 4, search.y, 25, 22);
        }
        button(font, resetSearch, "×", false,
                !search.getText().isEmpty() || (eventMode && !eventFilterDropdown.value().isEmpty()), false, mx, my);
        list = rect(pane.x + 4, pane.y + 59, pane.width - 8, pane.height - 63);
        listRows = Math.max(1, list.height / 34);
        maxListScroll = Math.max(0, visible.size() - listRows);
        listScroll = clamp(listScroll, maxListScroll);
        ModernUiRenderer.beginClip(list);
        try {
            int rowWidth = list.width - ModernHoverScrollbar.GUTTER;
            for (int i = 0; i < listRows && listScroll + i < visible.size(); i++) {
                McpCallHistory.Entry entry = visible.get(listScroll + i);
                ModernMainLayout.Rect row = rect(list.x, list.y + i * 34, rowWidth, 32);
                boolean active = entry.sequence == selectedId;
                if (active || row.contains(mx, my)) ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width,
                        row.height, 4, active ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SURFACE_HOVER);
                if (active) ModernUiRenderer.drawRoundedRect(row.x, row.y + 5, 3, row.height - 10, 1, ModernUiRenderer.ACCENT);
                ModernUiRenderer.drawStatusDot(row.right() - 10, row.y + 8, entryColor(entry));
                text(font, entry.tool, row.x + 8, row.y + 5, row.width - 24, ModernUiRenderer.TEXT);
                String meta = time.format(new Date(entry.startedAt)) + " · "
                        + (eventMode ? "#" + entry.sequence : entry.pending() ? tr("pending") : entry.durationMillis() + " ms");
                text(font, meta, row.x + 8, row.y + 19, row.width - 16, ModernUiRenderer.SUBTLE_TEXT);
            }
            if (visible.isEmpty()) text(font, emptyMessage(), list.x + 7, list.y + 10,
                    list.width - 14, ModernUiRenderer.MUTED_TEXT);
        } finally {
            ModernUiRenderer.endClip();
        }
        listBar.draw(list, listScroll, maxListScroll, listRows, visible.size(), mx, my, value -> listScroll = value);
    }

    private void drawInspector(FontRenderer font, ModernMainLayout.Rect pane, int mx, int my) {
        panel(pane);
        int tabWidth = Math.min(78, Math.max(52, (pane.width - 98) / 2));
        argsTab = rect(pane.x + 7, pane.y + 7, tabWidth, 22);
        resultTab = rect(argsTab.right() + 4, argsTab.y, tabWidth, 22);
        copyBody = rect(pane.right() - 84, argsTab.y, 77, 22);
        button(font, argsTab, tr(eventMode ? "event_record" : "arguments"), !showingResult, mx, my);
        button(font, resultTab, tr(eventMode ? "event_data" : "response"), showingResult, mx, my);
        button(font, copyBody, tr("copy"), false, selected != null && !(showingResult && selected.pending()), false, mx, my);
        String heading = selected == null ? tr("detail_title") : "#" + selected.sequence + "  " + selected.tool
                + "  ·  " + (eventMode ? tr("events") : tr(selected.pending() ? "pending" : selected.failed ? "failed" : "success"));
        text(font, heading, pane.x + 10, pane.y + 37, pane.width - 20,
                selected == null ? ModernUiRenderer.SUBTLE_TEXT : entryColor(selected));
        ModernUiRenderer.drawDivider(pane.x + 8, pane.y + 51, pane.width - 16, ModernUiRenderer.BORDER_SUBTLE);
        detail = rect(pane.x + 6, pane.y + 57, pane.width - 12, pane.height - 79);
        footer = rect(pane.x + 9, pane.bottom() - 17, pane.width - 18, 12);
        int gutter = Math.max(26, font.getStringWidth(String.valueOf(lines.length)) + 12);
        textWidth = Math.max(1, detail.width - gutter - ModernHoverScrollbar.GUTTER);
        maxColumn = Math.max(0, longestLine - textWidth);
        column = clamp(column, maxColumn);
        int horizontalHeight = maxColumn > 0 ? ModernHoverScrollbar.HORIZONTAL_TRACK_WIDTH : 0;
        int lineHeight = font.FONT_HEIGHT + 4;
        int viewHeight = Math.max(1, detail.height - horizontalHeight);
        detailRows = Math.max(1, viewHeight / lineHeight);
        maxDetailScroll = Math.max(0, lines.length - detailRows);
        detailScroll = clamp(detailScroll, maxDetailScroll);
        ModernMainLayout.Rect viewport = rect(detail.x + gutter, detail.y, textWidth, viewHeight);
        ModernUiRenderer.beginClip(rect(detail.x, detail.y, gutter - 5, viewHeight));
        try {
            for (int i = 0; i < detailRows && detailScroll + i < lines.length; i++) {
                String number = String.valueOf(detailScroll + i + 1);
                text(font, number, detail.x + gutter - 9 - font.getStringWidth(number), detail.y + i * lineHeight,
                        gutter - 8, ModernUiRenderer.MUTED_TEXT);
            }
        } finally { ModernUiRenderer.endClip(); }
        ModernUiRenderer.drawVerticalDivider(viewport.x - 5, detail.y, viewHeight, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(viewport);
        try {
            for (int i = 0; i < detailRows && detailScroll + i < lines.length; i++) {
                String line = lines[detailScroll + i];
                // Measure horizontal scrolling in pixels: Chinese and Latin glyphs have different widths.
                String skipped = font.trimStringToWidth(line, column);
                int remainder = column - font.getStringWidth(skipped);
                String fragment = font.trimStringToWidth(line.substring(skipped.length()), textWidth + remainder + 12);
                font.drawString(fragment, viewport.x - remainder, viewport.y + i * lineHeight,
                        ModernUiRenderer.readableText(ModernUiRenderer.TEXT, ModernUiRenderer.SURFACE));
            }
            if (selected == null) {
                text(font, emptyMessage(), viewport.x + 8, viewport.y + 12, viewport.width - 16, ModernUiRenderer.SUBTLE_TEXT);
                text(font, tr("empty_hint"), viewport.x + 8, viewport.y + 29, viewport.width - 16, ModernUiRenderer.MUTED_TEXT);
            }
        } finally { ModernUiRenderer.endClip(); }
        detailBar.drawAt(detail.right(), detail.y, viewHeight, detailScroll, maxDetailScroll,
                detailRows, lines.length, mx, my, value -> detailScroll = value);
        horizontalBar.drawAt(detail.bottom(), viewport.x, textWidth, column, maxColumn,
                textWidth, longestLine, mx, my, value -> column = value);
        text(font, selected == null ? tr("shortcuts") : tr("position", detailScroll + 1,
                Math.min(lines.length, detailScroll + detailRows), lines.length, column),
                footer.x, footer.y, footer.width, ModernUiRenderer.MUTED_TEXT);
    }
    private void configure(boolean enabled) {
        try {
            int value = Integer.parseInt(port.getText());
            MythosScriptMcpServer.configure(new McpServerSettings(enabled, value), true);
            notifyStatus(tr("saved"), false); version = -1;
        } catch (Exception e) { notifyStatus(tr("error", e.getMessage()), true); }
    }

    @Override public boolean mouseClicked(int x, int y, int button) {
        if (port == null || button != 0 || !containsContent(x, y)) return false;
        port.mouseClicked(x, y, button); search.mouseClicked(x, y, button);
        if (port.isFocused()) ModernUiRenderer.moveTextFieldCursorTo(port, x);
        if (search.isFocused()) ModernUiRenderer.moveTextFieldCursorTo(search, x);
        if (eventMode) {
            String previousEventFilter = eventFilterDropdown.value();
            if (eventFilterDropdown.mouseClicked(x, y)) {
                if (!previousEventFilter.equals(eventFilterDropdown.value())) {
                    version = -1;
                    listScroll = 0;
                }
                return true;
            }
        }
        if (contains(splitDivider, x, y)) {
            draggingSplit = true;
            port.setFocused(false);
            search.setFocused(false);
            listFocused = false;
            return true;
        }
        if (listBar.beginDrag(x, y)) { listFocused = true; return true; }
        if (detailBar.beginDrag(x, y) || horizontalBar.beginDrag(x, y)) { listFocused = false; return true; }
        if (contains(toggle, x, y)) {
            if (MythosScriptMcpServer.settings().enabled) {
                try {
                    MythosScriptMcpServer.configure(new McpServerSettings(false, MythosScriptMcpServer.settings().port), true);
                    notifyStatus(tr("saved"), false);
                } catch (Exception e) { notifyStatus(tr("error", e.getMessage()), true); }
            } else configure(true);
        } else if (contains(apply, x, y) && isDirty()) configure(MythosScriptMcpServer.settings().enabled);
        else if (contains(copyAddress, x, y)) { GuiScreen.setClipboardString(endpoint()); notifyStatus(tr("address_copied"), false); }
        else if (contains(callsTab, x, y)) switchFeed(false);
        else if (contains(eventsTab, x, y)) switchFeed(true);
        else if (contains(resetSearch, x, y)) {
            search.setText("");
            if (eventMode) eventFilterDropdown.setValue("");
            version = -1; listScroll = 0;
        }
        else if (contains(clear, x, y)) {
            McpCallHistory.INSTANCE.clear(); selectedId = -1; version = -1; listScroll = 0; cachedBody = null;
            notifyStatus(tr("cleared"), false);
        } else if (contains(latest, x, y)) { select(0); listScroll = 0; }
        else if (contains(argsTab, x, y)) { showingResult = false; listFocused = false; }
        else if (contains(resultTab, x, y)) { showingResult = true; listFocused = false; }
        else if (contains(copyBody, x, y)) copySelected();
        else if (contains(list, x, y)) {
            listFocused = true;
            if (x < list.right() - ModernHoverScrollbar.GUTTER) {
                int row = (y - list.y) / 34;
                if (row < listRows) select(listScroll + row);
            }
        } else if (contains(detail, x, y)) listFocused = false;
        return true;
    }

    private void switchFeed(boolean events) {
        if (eventMode == events) return;
        eventMode = events; version = -1; selectedId = -1; listScroll = 0;
        search.setText(""); cachedBody = null; status = "";
        eventFilterDropdown.close();
        eventOptionsVersion = -1;
        endDrags();
    }

    private void select(int index) {
        if (index < 0 || index >= visible.size()) return;
        selected = visible.get(index); selectedId = selected.sequence; cachedBody = null;
        detailScroll = column = 0;
        if (index < listScroll) listScroll = index;
        if (index >= listScroll + listRows) listScroll = index - listRows + 1;
    }

    private void copySelected() {
        if (selected == null || (showingResult && selected.pending())) return;
        GuiScreen.setClipboardString(showingResult ? selected.response : selected.arguments);
        notifyStatus(tr("copied"), false);
    }

    @Override public boolean mouseClickMove(int x, int y, int button, long elapsed) {
        if (button != 0) return false;
        if (eventFilterDropdown.mouseClickMove(x, y)) return true;
        if (draggingSplit && bounds != null) {
            int splitWidth = Math.max(2, bounds.width - ModernMcpLayout.PADDING * 2 - ModernMcpLayout.SPLIT_GAP);
            int pointerOffset = x - bounds.x - ModernMcpLayout.PADDING - ModernMcpLayout.SPLIT_GAP / 2;
            recordsRatio = ModernSplitPane.calculateFromPointer(splitWidth, pointerOffset,
                    ModernMcpLayout.SIDEBAR_MIN_WIDTH, ModernMcpLayout.INSPECTOR_MIN_WIDTH,
                    ModernMcpLayout.SIDEBAR_FLOOR_WIDTH, ModernMcpLayout.INSPECTOR_FLOOR_WIDTH).ratio;
            return true;
        }
        return listBar.applyDrag(x, y) || detailBar.applyDrag(x, y) || horizontalBar.applyDrag(x, y);
    }

    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        boolean dragging = draggingSplit || eventFilterDropdown.isDragging() || listBar.isDragging()
                || detailBar.isDragging() || horizontalBar.isDragging();
        if (draggingSplit) MainUiLayoutManager.setModernSplitRatio(RECORDS_SPLIT_KEY, recordsRatio);
        draggingSplit = false;
        endDrags();
        return dragging;
    }

    private void endDrags() {
        draggingSplit = false;
        eventFilterDropdown.endDrag();
        listBar.endDrag(); detailBar.endDrag(); horizontalBar.endDrag();
    }

    @Override public boolean keyTyped(char c, int key) {
        if (port == null) return false;
        if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_F) {
            port.setFocused(false); search.setFocused(true); return true;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && port.isFocused()) { save(); return true; }
        if (isTextInputFocused()) {
            if (key == Keyboard.KEY_TAB) {
                boolean wasPort = port.isFocused(); port.setFocused(!wasPort); search.setFocused(wasPort); return true;
            }
            return port.isFocused() ? ModernUiRenderer.typeNumericField(port, c, key, false) : search.textboxKeyTyped(c, key);
        }
        if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_C) { copySelected(); return true; }
        if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
            column = clamp(column + (key == Keyboard.KEY_LEFT ? -40 : 40), maxColumn); return true;
        }
        int delta = key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1
                : key == Keyboard.KEY_PRIOR ? -(listFocused ? listRows : detailRows)
                : key == Keyboard.KEY_NEXT ? (listFocused ? listRows : detailRows) : 0;
        if (delta == 0 && key != Keyboard.KEY_HOME && key != Keyboard.KEY_END) return false;
        if (listFocused) {
            int index = visible.indexOf(selected);
            select(clamp(key == Keyboard.KEY_HOME ? 0 : key == Keyboard.KEY_END ? visible.size() - 1 : index + delta,
                    Math.max(0, visible.size() - 1)));
        } else {
            detailScroll = clamp(key == Keyboard.KEY_HOME ? 0 : key == Keyboard.KEY_END ? maxDetailScroll
                    : detailScroll + delta, maxDetailScroll);
        }
        return true;
    }
    @Override public boolean handleEscape() {
        if (eventFilterDropdown.isOpen()) { eventFilterDropdown.close(); return true; }
        if (draggingSplit || listBar.isDragging() || detailBar.isDragging() || horizontalBar.isDragging()) { endDrags(); return true; }
        if (isTextInputFocused()) { port.setFocused(false); search.setFocused(false); return true; }
        return false;
    }
    @Override public boolean isTextInputFocused() { return port != null && (port.isFocused() || search.isFocused()); }
    @Override public void clearTextInputFocusOutside(int x, int y) {
        if (port == null) return;
        if (x < port.x || x >= port.x + port.width || y < port.y || y >= port.y + port.height) port.setFocused(false);
        if (x < search.x || x >= search.x + search.width || y < search.y || y >= search.y + search.height) search.setFocused(false);
    }
    @Override public boolean handleMouseWheel(int wheel) { return false; }
    @Override public boolean handleMouseWheel(int wheel, int x, int y) {
        if (wheel == 0) return false;
        if (eventMode && eventFilterDropdown.isOpen()) return eventFilterDropdown.wheel(wheel, x, y);
        int delta = wheel > 0 ? -3 : 3;
        if (contains(list, x, y)) listScroll = clamp(listScroll + delta, maxListScroll);
        else if (contains(detail, x, y)) {
            if (GuiScreen.isShiftKeyDown() || horizontalBar.contains(x, y)) column = clamp(column + delta * 16, maxColumn);
            else detailScroll = clamp(detailScroll + delta, maxDetailScroll);
        } else return false;
        return true;
    }
    @Override public void save() { configure(MythosScriptMcpServer.settings().enabled); }
    @Override public boolean isDirty() { return port != null && !port.getText().equals(String.valueOf(MythosScriptMcpServer.settings().port)); }
    @Override public void discardDraft() {
        if (port != null) port.setText(String.valueOf(MythosScriptMcpServer.settings().port));
        status = ""; endDrags();
    }
    @Override public boolean containsContent(int x, int y) { return contains(bounds, x, y); }
    @Override public String getHoveredTooltip(int x, int y) {
        if (contains(splitDivider, x, y)) return tr("split_hint");
        if (contains(eventFilterControl, x, y)) return tr("event_filter_dropdown_hint");
        if (contains(resetSearch, x, y)) return tr("reset_filter");
        if (contains(clear, x, y)) return tr("clear_hint");
        if (contains(callsTab, x, y)) return tr("retention");
        if (contains(eventsTab, x, y) || (search != null && contains(rect(search.x, search.y, search.width, search.height), x, y)))
            return tr(eventMode ? "event_filter" : "filter_hint");
        if (contains(copyBody, x, y)) return tr("copy_hint");
        if (contains(copyAddress, x, y)) return endpoint();
        if (contains(apply, x, y) || (port != null && contains(rect(port.x, port.y, port.width, port.height), x, y))) return tr("port_hint");
        if (contains(footer, x, y)) return tr("shortcuts");
        if (contains(latest, x, y)) return tr("latest_hint");
        if (contains(list, x, y)) {
            int index = listScroll + (y - list.y) / 34;
            if (index >= 0 && index < visible.size()) {
                McpCallHistory.Entry entry = visible.get(index);
                return entry.tool + " · ID " + entry.requestId + " · " + tr(entry.pending() ? "pending" : entry.failed ? "failed" : "success");
            }
        }
        return "";
    }

    @Override
    public List<com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector.GuiElementInfo> getMcpGuiElements(
            String pathPrefix) {
        List<com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector.GuiElementInfo> result = new ArrayList<>();
        String prefix = pathPrefix == null ? "" : pathPrefix;
        for (com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector.GuiElementInfo element
                : ModernSettingsTab.super.getMcpGuiElements()) {
            if (element == null) continue;
            String path = element.getPath();
            while (path.startsWith("/")) path = path.substring(1);
            if (!path.startsWith("screen/")) path = prefix + path;
            com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector.GuiElementInfo normalized = element.withPath(path);
            if (path.toLowerCase(Locale.ROOT).endsWith("/toggle")) {
                normalized = normalized.withSemantics("toggle",
                        String.valueOf(MythosScriptMcpServer.settings().enabled), true, true,
                        Arrays.asList("click", "set"), Collections.<String>emptyList());
            }
            result.add(normalized);
        }
        return result;
    }

    @Override
    public boolean setMcpValue(String target, String value) {
        String normalized = target == null ? "" : target.toLowerCase(Locale.ROOT);
        Boolean requested = parseMcpBoolean(value);
        if (requested != null && (normalized.endsWith("/toggle") || normalized.equals("toggle"))) {
            try {
                configure(requested.booleanValue());
                return MythosScriptMcpServer.settings().enabled == requested.booleanValue();
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return ModernSettingsTab.super.setMcpValue(target, value);
    }

    private static Boolean parseMcpBoolean(String value) {
        if (value == null) return null;
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "1".equals(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value) || "0".equals(value)) return Boolean.FALSE;
        return null;
    }
    private void notifyStatus(String message, boolean error) {
        status = message; statusError = error; statusUntil = System.currentTimeMillis() + (error ? 12000 : 4000);
    }
    private String emptyMessage() {
        return tr(!search.getText().isEmpty() ? "no_matches" : eventMode ? "empty_events" : "empty");
    }
    private static String endpoint() { return MythosScriptMcpServer.endpoint(); }
    private static int clamp(int value, int max) { return Math.max(0, Math.min(value, max)); }
    private static ModernMainLayout.Rect rect(int x, int y, int width, int height) {
        return new ModernMainLayout.Rect(x, y, Math.max(1, width), Math.max(1, height));
    }
    private static int entryColor(McpCallHistory.Entry entry) {
        return entry.pending() ? ModernUiRenderer.WARNING : entry.failed ? ModernUiRenderer.DANGER : ModernUiRenderer.SUCCESS;
    }
    private static void drawInput(GuiTextField field) {
        flat(rect(field.x, field.y, field.width, field.height), ModernUiRenderer.INPUT_SURFACE,
                field.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawTextField(field);
    }

    private static String clientLine() {
        MythosScriptMcpServer.Role role = MythosScriptMcpServer.role();
        int pid = MythosScriptMcpServer.localPid();
        if (role == MythosScriptMcpServer.Role.HUB) {
            StringBuilder pids = new StringBuilder();
            try {
                for (JsonElement entry : MythosScriptMcpServer.clients().getAsJsonArray("clients")) {
                    if (pids.length() > 0) pids.append(", ");
                    pids.append(entry.getAsJsonObject().get("pid").getAsInt());
                }
            } catch (RuntimeException ignored) {}
            return tr("hub", pid, pids.length() == 0 ? String.valueOf(pid) : pids.toString());
        }
        if (role == MythosScriptMcpServer.Role.WORKER)
            return tr("worker", pid, MythosScriptMcpServer.settings().port);
        return "";
    }
    private static boolean contains(ModernMainLayout.Rect r, int x, int y) { return r != null && r.contains(x, y); }
    private static void text(FontRenderer f, String value, int x, int y, int width, int color) {
        f.drawString(ModernUiRenderer.ellipsize(f, value, Math.max(1, width)), x, y,
                ModernUiRenderer.readableText(color, ModernUiRenderer.SURFACE));
    }
    private static void panel(ModernMainLayout.Rect r) {
        flat(r, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
    }
    private static void flat(ModernMainLayout.Rect r, int fill, int border) {
        ModernUiRenderer.drawRoundedRect(r.x, r.y, r.width, r.height, 5, border);
        ModernUiRenderer.drawRoundedRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 4, fill);
    }
    private static void button(FontRenderer f, ModernMainLayout.Rect r, String label, boolean active, int x, int y) {
        button(f, r, label, active, true, false, x, y);
    }
    private static void button(FontRenderer f, ModernMainLayout.Rect r, String label, boolean active,
            boolean enabled, boolean danger, int x, int y) {
        int fill = !enabled ? ModernUiRenderer.DISABLED_SURFACE : active ? ModernUiRenderer.SELECTED_SURFACE
                : r.contains(x, y) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        flat(r, fill, active ? ModernUiRenderer.ACCENT : enabled && r.contains(x, y)
                ? ModernUiRenderer.HOVER_BORDER : ModernUiRenderer.BORDER_SUBTLE);
        if (active) ModernUiRenderer.drawRoundedRect(r.x + 9, r.bottom() - 3, r.width - 18, 2, 1, ModernUiRenderer.ACCENT);
        String caption = ModernUiRenderer.ellipsize(f, label, r.width - 10);
        f.drawString(caption, r.x + (r.width - f.getStringWidth(caption)) / 2, r.y + (r.height - f.FONT_HEIGHT) / 2,
                ModernUiRenderer.readableText(!enabled ? ModernUiRenderer.DISABLED_TEXT
                        : danger ? ModernUiRenderer.DANGER : ModernUiRenderer.TEXT, fill));
    }
}
