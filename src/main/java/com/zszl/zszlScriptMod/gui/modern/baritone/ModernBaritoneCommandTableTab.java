package com.zszl.zszlScriptMod.gui.modern.baritone;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernDropdown;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.ICommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.manager.ICommandManager;
import com.zszl.zszlScriptMod.system.ProfileManager;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;

/**
 * Native modern command workbench for the embedded Baritone command registry.
 *
 * <p>This replaces the old standalone three-pane GuiScreen. The command
 * registry remains the source of truth, while this tab owns only presentation,
 * search/filter state, command execution and the small persistent favorite/
 * history index.</p>
 */
public final class ModernBaritoneCommandTableTab implements ModernSettingsTab {

    private static final int SIDEBAR_MIN_WIDTH = 210;
    private static final int SIDEBAR_FLOOR_WIDTH = 170;
    private static final int DETAIL_MIN_WIDTH = 360;
    private static final int DETAIL_FLOOR_WIDTH = 260;
    private static final int PANE_GAP = 8;
    private static final int COMMAND_ROW_HEIGHT = 42;
    private static final int HISTORY_LIMIT = 40;
    private static final int RECENT_LIMIT = 20;
    private static final int MAX_VISIBLE_DETAIL_LINES = 80;
    private static final String STATE_FILE_NAME = "baritone_command_table_state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private enum ListMode {
        ALL("gui.modern.baritone_cmd.u001"),
        FAVORITES("gui.modern.baritone_cmd.u002"),
        RECENT("gui.modern.baritone_cmd.u003");

        private final String label;

        ListMode(String label) {
            this.label = label;
        }
    }

    private enum CommandCategory {
        ALL("gui.modern.baritone_cmd.u001"),
        NAVIGATION("gui.modern.baritone_cmd.u004"),
        WORLD("gui.modern.baritone_cmd.u005"),
        CONTROL("gui.modern.baritone_cmd.u006"),
        INFO("gui.modern.baritone_cmd.u007"),
        OTHER("gui.modern.baritone_cmd.u008");

        private final String label;

        CommandCategory(String label) {
            this.label = label;
        }
    }

    private enum DetailTab {
        OVERVIEW("gui.modern.baritone_cmd.u009"),
        PARAMETERS("gui.modern.baritone_cmd.u010"),
        EXAMPLES("gui.modern.baritone_cmd.u011"),
        HISTORY("gui.modern.baritone_cmd.u012");

        private final String label;

        DetailTab(String label) {
            this.label = label;
        }
    }

    private static final class CommandEntry {
        private final String primaryName;
        private final List<String> aliases;
        private final String shortDesc;
        private final List<String> longDesc;
        private final List<ExampleEntry> examples;
        private final String searchText;
        private final CommandCategory category;

        private CommandEntry(String primaryName, List<String> aliases, String shortDesc, List<String> longDesc,
                CommandCategory category) {
            this.primaryName = safe(primaryName);
            this.aliases = aliases == null ? Collections.<String>emptyList() : new ArrayList<>(aliases);
            this.shortDesc = safe(shortDesc);
            this.longDesc = longDesc == null ? Collections.<String>emptyList() : new ArrayList<>(longDesc);
            this.examples = extractExamples(this.longDesc);
            this.category = category == null ? CommandCategory.OTHER : category;
            this.searchText = buildSearchText();
        }

        private String buildSearchText() {
            StringBuilder builder = new StringBuilder(primaryName).append(' ').append(shortDesc);
            for (String alias : aliases) {
                builder.append(' ').append(safe(alias));
            }
            for (String line : longDesc) {
                builder.append(' ').append(safe(line));
            }
            for (ExampleEntry example : examples) {
                builder.append(' ').append(example.commandText).append(' ').append(example.description);
            }
            return builder.toString().toLowerCase(Locale.ROOT);
        }

        private static List<ExampleEntry> extractExamples(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return Collections.emptyList();
            }
            List<ExampleEntry> result = new ArrayList<>();
            for (String line : lines) {
                if (line == null || !line.trim().startsWith(">")) {
                    continue;
                }
                String value = line.trim().substring(1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                int separator = value.indexOf(" - ");
                if (separator > 0) {
                    result.add(new ExampleEntry(value.substring(0, separator).trim(),
                            value.substring(separator + 3).trim()));
                } else {
                    result.add(new ExampleEntry(value, ""));
                }
            }
            return result;
        }
    }

    private static final class ExampleEntry {
        private final String commandText;
        private final String description;

        private ExampleEntry(String commandText, String description) {
            this.commandText = safe(commandText);
            this.description = safe(description);
        }
    }

    private static final class HistoryEntry {
        private String time;
        private String command;

        private HistoryEntry() {
        }

        private HistoryEntry(String command) {
            Date now = new Date();
            this.time = String.format(Locale.ROOT, "%tH:%tM:%tS", now);
            this.command = safe(command);
        }
    }

    private static final class StateData {
        private List<String> favorites = new ArrayList<>();
        private List<String> recent = new ArrayList<>();
        private List<HistoryEntry> history = new ArrayList<>();
    }

    private static final class CommandHit {
        private final CommandEntry entry;
        private final ModernMainLayout.Rect bounds;

        private CommandHit(CommandEntry entry, ModernMainLayout.Rect bounds) {
            this.entry = entry;
            this.bounds = bounds;
        }
    }

    private static final class ExampleHit {
        private final ExampleEntry entry;
        private final ModernMainLayout.Rect bounds;

        private ExampleHit(ExampleEntry entry, ModernMainLayout.Rect bounds) {
            this.entry = entry;
            this.bounds = bounds;
        }
    }

    private static final class HistoryHit {
        private final HistoryEntry entry;
        private final ModernMainLayout.Rect bounds;

        private HistoryHit(HistoryEntry entry, ModernMainLayout.Rect bounds) {
            this.entry = entry;
            this.bounds = bounds;
        }
    }

    private final List<CommandEntry> allCommands = new ArrayList<>();
    private final List<CommandEntry> filteredCommands = new ArrayList<>();
    private final List<CommandHit> commandHits = new ArrayList<>();
    private final List<ExampleHit> exampleHits = new ArrayList<>();
    private final List<HistoryHit> historyHits = new ArrayList<>();
    private final List<ModernMainLayout.Rect> detailTabHits = new ArrayList<>();

    private final Set<String> favorites = new LinkedHashSet<>();
    private final List<String> recentCommands = new ArrayList<>();
    private final List<HistoryEntry> executionHistory = new ArrayList<>();

    private FontRenderer fontRenderer;
    private GuiTextField searchField;
    private GuiTextField commandField;
    private final ModernDropdown listModeDropdown = new ModernDropdown(
            new String[] { "all", "favorites", "recent" },
            new String[] { "gui.modern.baritone_cmd.u001", "gui.modern.baritone_cmd.u002",
                    "gui.modern.baritone_cmd.u003" });
    private final ModernDropdown categoryDropdown = new ModernDropdown(
            new String[] { "all", "navigation", "world", "control", "info", "other" },
            new String[] { "gui.modern.baritone_cmd.u001", "gui.modern.baritone_cmd.u004",
                    "gui.modern.baritone_cmd.u005", "gui.modern.baritone_cmd.u006",
                    "gui.modern.baritone_cmd.u007", "gui.modern.baritone_cmd.u008" });

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect sidebarBounds;
    private ModernMainLayout.Rect detailBounds;
    private ModernMainLayout.Rect sidebarClipBounds;
    private ModernMainLayout.Rect detailClipBounds;
    private ModernMainLayout.Rect dividerBounds;
    private ModernMainLayout.Rect favoriteBounds;
    private ModernMainLayout.Rect executeBounds;
    private ModernMainLayout.Rect copyBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect reloadBounds;
    private ModernMainLayout.Rect commandInputBounds;
    private ModernMainLayout.Rect listModeFilterBounds;
    private ModernMainLayout.Rect categoryFilterBounds;
    private final ModernHoverScrollbar sidebarScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar detailScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar compactScrollbar = new ModernHoverScrollbar();

    private ListMode listMode = ListMode.ALL;
    private CommandCategory activeCategory = CommandCategory.ALL;
    private DetailTab detailTab = DetailTab.OVERVIEW;
    private String selectedCommandName;
    private String searchText = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private double sidebarRatio = 0.31D;
    private boolean draggingSidebar;
    private int sidebarScroll;
    private int sidebarMaxScroll;
    private int detailScroll;
    private int detailMaxScroll;
    private int lastMouseX;
    private int lastMouseY;
    private boolean compactLayout;
    private ModernMainLayout.Rect compactViewport;
    private int compactScroll;
    private int compactMaxScroll;
    private boolean initialized;

    public static ModernSettingsTab create() {
        return new ModernBaritoneCommandTableTab();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        this.fontRenderer = fontRenderer;
        sidebarRatio = MainUiLayoutManager.getModernSplitRatio("baritone.command.sidebar", sidebarRatio);
        loadPersistentState();
        reloadCommands();
        searchField = createField(128);
        commandField = createField(512);
        initialized = true;
        ensureSelection();
    }

    private GuiTextField createField(int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setMaxStringLength(maxLength);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    @Override
    public void updateScreen() {
        if (!initialized) {
            return;
        }
        if (searchField != null) {
            searchField.updateCursorCounter();
            String next = safe(searchField.getText());
            if (!next.equals(searchText)) {
                searchText = next;
                refreshFilteredCommands();
            }
        }
        if (commandField != null) {
            commandField.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        this.fontRenderer = fontRenderer;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        contentBounds = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requestedBounds;
        panelBounds = safePanel(contentBounds);

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        int bodyY = panelBounds.y + 10;
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 38);
        drawFooter(mouseX, mouseY);
        compactLayout = panelBounds.width < 620;
        if (compactLayout) {
            drawCompactBody(mouseX, mouseY, bodyY, bodyHeight);
            drawFilterDropdownMenus(mouseX, mouseY);
            return;
        }
        compactViewport = null;
        compactMaxScroll = 0;
        compactScrollbar.idle();
        compactScrollbar.endDrag();
        int paneX = panelBounds.x + 10;
        int paneWidth = Math.max(2, panelBounds.width - 20);
        int gap = Math.min(PANE_GAP, Math.max(0, paneWidth - 2));
        int splitWidth = Math.max(2, paneWidth - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitWidth, sidebarRatio, SIDEBAR_MIN_WIDTH,
                DETAIL_MIN_WIDTH, SIDEBAR_FLOOR_WIDTH, DETAIL_FLOOR_WIDTH);
        sidebarBounds = new ModernMainLayout.Rect(paneX, bodyY, split.firstWidth, bodyHeight);
        detailBounds = new ModernMainLayout.Rect(sidebarBounds.right() + gap, bodyY, split.secondWidth, bodyHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(sidebarBounds.x, sidebarBounds.width, gap, bodyY,
                bodyHeight);

        drawSidebar(mouseX, mouseY);
        drawDetail(mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingSidebar);
        drawFilterDropdownMenus(mouseX, mouseY);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = panelBounds.bottom() - 28;
        String count = ModernFormI18n.tr("gui.modern.baritone_cmd.fmt.count",
                filteredCommands.size() + "/" + allCommands.size());
        reloadBounds = new ModernMainLayout.Rect(panelBounds.right() - 92, y, 78, 22);
        ModernUiRenderer.drawText(fontRenderer, count, panelBounds.x + 14, y + 6,
                ModernUiRenderer.MUTED_TEXT, Math.max(1, reloadBounds.x - panelBounds.x - 28));
        drawButton(reloadBounds, "gui.modern.baritone_cmd.u015", false, reloadBounds.contains(mouseX, mouseY));
    }

    private void drawCompactBody(int mouseX, int mouseY, int bodyY, int bodyHeight) {
        int width = Math.max(1, panelBounds.width - 20);
        int sidebarHeight = 190;
        int detailHeight = 410;
        int gap = 8;
        compactViewport = new ModernMainLayout.Rect(panelBounds.x + 10, bodyY, width, bodyHeight);
        width = ModernHoverScrollbar.contentWidth(width);
        compactMaxScroll = Math.max(0, sidebarHeight + gap + detailHeight - bodyHeight);
        compactScroll = clamp(compactScroll, 0, compactMaxScroll);
        int y = bodyY - compactScroll;
        sidebarBounds = new ModernMainLayout.Rect(panelBounds.x + 10, y, width, sidebarHeight);
        detailBounds = new ModernMainLayout.Rect(panelBounds.x + 10, sidebarBounds.bottom() + gap, width, detailHeight);
        dividerBounds = null;
        ModernUiRenderer.beginClip(compactViewport);
        drawSidebar(mouseX, mouseY);
        drawDetail(mouseX, mouseY);
        ModernUiRenderer.endClip();
        drawCompactScrollbar(mouseX, mouseY);
    }

    private void drawCompactScrollbar(int mouseX, int mouseY) {
        if (compactViewport == null || compactMaxScroll <= 0) {
            compactScrollbar.idle();
            return;
        }
        compactScrollbar.draw(compactViewport, compactScroll, compactMaxScroll, compactViewport.height,
                compactViewport.height + compactMaxScroll, mouseX, mouseY, value -> compactScroll = value);
    }

    private void drawSidebar(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(sidebarBounds.x, sidebarBounds.y, sidebarBounds.width, sidebarBounds.height,
                6, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = sidebarBounds.x + 10;
        int width = Math.max(1, sidebarBounds.width - 20);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u017", x, sidebarBounds.y + 9, ModernUiRenderer.TEXT,
                Math.max(40, width - 20));

        ModernMainLayout.Rect searchBounds = new ModernMainLayout.Rect(x, sidebarBounds.y + 28, width, 25);
        drawSearchField(searchBounds, mouseX, mouseY);

        int filterY = searchBounds.bottom() + 6;
        int filterGap = 6;
        int filterWidth = Math.max(1, (width - filterGap) / 2);
        listModeFilterBounds = new ModernMainLayout.Rect(x, filterY, filterWidth, 23);
        categoryFilterBounds = new ModernMainLayout.Rect(listModeFilterBounds.right() + filterGap, filterY,
                Math.max(1, x + width - listModeFilterBounds.right() - filterGap), 23);
        listModeDropdown.drawButton(fontRenderer, listModeFilterBounds, mouseX, mouseY);
        categoryDropdown.drawButton(fontRenderer, categoryFilterBounds, mouseX, mouseY);

        int listY = filterY + 30;
        sidebarClipBounds = new ModernMainLayout.Rect(sidebarBounds.x + 7, listY,
                Math.max(1, sidebarBounds.width - 14), Math.max(1, sidebarBounds.bottom() - listY - 8));
        int totalHeight = filteredCommands.size() * COMMAND_ROW_HEIGHT;
        sidebarMaxScroll = Math.max(0, totalHeight - sidebarClipBounds.height);
        sidebarScroll = clamp(sidebarScroll, 0, sidebarMaxScroll);

        commandHits.clear();
        ModernUiRenderer.beginClip(sidebarClipBounds);
        int start = Math.max(0, sidebarScroll / COMMAND_ROW_HEIGHT - 1);
        int end = Math.min(filteredCommands.size(), start + sidebarClipBounds.height / COMMAND_ROW_HEIGHT + 3);
        for (int index = start; index < end; index++) {
            CommandEntry entry = filteredCommands.get(index);
            int y = sidebarClipBounds.y + index * COMMAND_ROW_HEIGHT - sidebarScroll;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(sidebarClipBounds.x, y, ModernHoverScrollbar.contentWidth(sidebarClipBounds.width),
                    COMMAND_ROW_HEIGHT - 3);
            boolean hovered = row.contains(mouseX, mouseY) && sidebarClipBounds.contains(mouseX, mouseY);
            boolean selected = entry.primaryName.equalsIgnoreCase(selectedCommandName);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawStatusDot(row.x + 9, row.y + 10, categoryColor(entry.category));
            ModernUiRenderer.drawText(fontRenderer, "/" + entry.primaryName, row.x + 22, row.y + 7,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(30, row.width - 78));
            String description = entry.shortDesc.isEmpty() ? "gui.modern.baritone_cmd.u018" : entry.shortDesc;
            ModernUiRenderer.drawText(fontRenderer, description, row.x + 22, row.y + 23,
                    ModernUiRenderer.MUTED_TEXT, Math.max(30, row.width - 52));
            if (isFavorite(entry.primaryName)) {
                ModernUiRenderer.drawText(fontRenderer, "★", row.right() - 28, row.y + 8, ModernUiRenderer.WARNING, 18);
            }
            commandHits.add(new CommandHit(entry, row));
        }
        if (filteredCommands.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u019", sidebarClipBounds.x + 12, sidebarClipBounds.y + 14,
                    ModernUiRenderer.MUTED_TEXT, Math.max(30, sidebarClipBounds.width - 24));
        }
        ModernUiRenderer.endClip();
        drawScrollbar(sidebarScrollbar, sidebarClipBounds, totalHeight, sidebarScroll, sidebarMaxScroll, mouseX, mouseY,
                value -> sidebarScroll = value);
    }

    private void drawDetail(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(detailBounds.x, detailBounds.y, detailBounds.width, detailBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        CommandEntry selected = getSelectedCommand();
        if (selected == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u020", detailBounds.x + 16, detailBounds.y + 18,
                    ModernUiRenderer.TEXT, Math.max(40, detailBounds.width - 32));
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u021", detailBounds.x + 16,
                    detailBounds.y + 40, ModernUiRenderer.MUTED_TEXT, Math.max(40, detailBounds.width - 32));
            return;
        }

        int x = detailBounds.x + 12;
        int width = Math.max(1, detailBounds.width - 24);
        ModernUiRenderer.drawText(fontRenderer, "COMMAND", x, detailBounds.y + 9, ModernUiRenderer.MUTED_TEXT,
                Math.max(50, width - 100));
        ModernUiRenderer.drawText(fontRenderer, "/" + selected.primaryName, x, detailBounds.y + 22,
                ModernUiRenderer.TEXT, Math.max(50, width - 110));
        ModernMainLayout.Rect categoryBounds = new ModernMainLayout.Rect(detailBounds.right() - 86,
                detailBounds.y + 14, 74, 21);
        drawChip(categoryBounds, selected.category.label, false, false);
        favoriteBounds = new ModernMainLayout.Rect(detailBounds.right() - 100, detailBounds.y + 42, 88, 21);
        drawButton(favoriteBounds, isFavorite(selected.primaryName) ? "gui.modern.baritone_cmd.u022" : "gui.modern.baritone_cmd.u023", false,
                favoriteBounds.contains(mouseX, mouseY));

        int inputY = detailBounds.y + 71;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u024", x, inputY - 13, ModernUiRenderer.MUTED_TEXT,
                Math.max(42, width - 20));
        commandInputBounds = new ModernMainLayout.Rect(x, inputY, width, 24);
        drawCommandField(commandInputBounds, mouseX, mouseY);

        int actionsY = inputY + 30;
        int actionGap = 5;
        int executeWidth = Math.min(88, Math.max(66, width / 4));
        executeBounds = new ModernMainLayout.Rect(x, actionsY, executeWidth, 22);
        copyBounds = new ModernMainLayout.Rect(executeBounds.right() + actionGap, actionsY, 54, 22);
        clearBounds = new ModernMainLayout.Rect(copyBounds.right() + actionGap, actionsY,
                Math.max(1, x + width - (copyBounds.right() + actionGap)), 22);
        drawButton(executeBounds, "gui.modern.baritone_cmd.u025", true, executeBounds.contains(mouseX, mouseY));
        drawButton(copyBounds, "gui.modern.baritone_cmd.u026", false, copyBounds.contains(mouseX, mouseY));
        drawButton(clearBounds, "gui.modern.baritone_cmd.u027", false, clearBounds.contains(mouseX, mouseY));

        int tabsY = actionsY + 31;
        int tabGap = 4;
        int tabWidth = Math.max(1, (width - tabGap * 3) / 4);
        detailTabHits.clear();
        for (int i = 0; i < DetailTab.values().length; i++) {
            DetailTab tab = DetailTab.values()[i];
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x + i * (tabWidth + tabGap), tabsY,
                    i == DetailTab.values().length - 1 ? Math.max(1, x + width - (x + i * (tabWidth + tabGap)))
                            : tabWidth,
                    22);
            detailTabHits.add(bounds);
            drawChip(bounds, tab.label, detailTab == tab, bounds.contains(mouseX, mouseY));
        }

        int clipY = tabsY + 29;
        detailClipBounds = new ModernMainLayout.Rect(detailBounds.x + 7, clipY,
                Math.max(1, detailBounds.width - 14), Math.max(1, detailBounds.bottom() - clipY - 8));
        int contentWidth = Math.max(30, detailClipBounds.width - 20);
        detailMaxScroll = Math.max(0, measureDetailHeight(selected, contentWidth) - detailClipBounds.height);
        detailScroll = clamp(detailScroll, 0, detailMaxScroll);
        ModernUiRenderer.beginClip(detailClipBounds);
        int contentX = detailClipBounds.x + 9;
        int contentY = detailClipBounds.y + 8 - detailScroll;
        switch (detailTab) {
        case PARAMETERS:
            drawParameters(selected, contentX, contentY, contentWidth, mouseX, mouseY);
            break;
        case EXAMPLES:
            drawExamples(selected, contentX, contentY, contentWidth, mouseX, mouseY);
            break;
        case HISTORY:
            drawHistory(contentX, contentY, contentWidth, mouseX, mouseY);
            break;
        case OVERVIEW:
        default:
            drawOverview(selected, contentX, contentY, contentWidth);
            break;
        }
        ModernUiRenderer.endClip();
        drawScrollbar(detailScrollbar, detailClipBounds, measureDetailHeight(selected, contentWidth), detailScroll,
                detailMaxScroll, mouseX, mouseY, value -> detailScroll = value);
        if (statusVisible()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, x, detailBounds.bottom() - 18,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(30, width - 8));
        }
    }

    private void drawOverview(CommandEntry selected, int x, int y, int width) {
        int currentY = y;
        currentY = drawInfoLine("gui.modern.baritone_cmd.u028", "/" + selected.primaryName, x, currentY, width);
        currentY = drawInfoLine("gui.modern.baritone_cmd.u029", selected.category.label, x, currentY, width);
        currentY = drawInfoLine("gui.modern.baritone_cmd.u030", selected.aliases.isEmpty() ? "gui.modern.baritone_cmd.u031" : String.join("、", selected.aliases), x,
                currentY, width);
        currentY += 8;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u032", x, currentY, ModernUiRenderer.ACCENT, Math.max(30, width - 4));
        currentY += 15;
        currentY += drawWrapped(selected.shortDesc.isEmpty() ? "gui.modern.baritone_cmd.u033" : selected.shortDesc, x, currentY,
                width, ModernUiRenderer.TEXT, MAX_VISIBLE_DETAIL_LINES);
        currentY += 10;
        if (!selected.longDesc.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u034", x, currentY, ModernUiRenderer.ACCENT,
                    Math.max(30, width - 4));
            currentY += 15;
            for (String line : selected.longDesc) {
                currentY += drawWrapped(line, x, currentY, width, ModernUiRenderer.SUBTLE_TEXT,
                        MAX_VISIBLE_DETAIL_LINES);
            }
        }
    }

    private int drawInfoLine(String label, String value, int x, int y, int width) {
        ModernUiRenderer.drawText(fontRenderer, label, x, y, ModernUiRenderer.MUTED_TEXT, 42);
        ModernUiRenderer.drawText(fontRenderer, value, x + 48, y, ModernUiRenderer.TEXT, Math.max(30, width - 48));
        return y + 16;
    }

    private void drawParameters(CommandEntry selected, int x, int y, int width, int mouseX, int mouseY) {
        int currentY = y;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u035", x, currentY, ModernUiRenderer.ACCENT,
                Math.max(30, width - 4));
        currentY += 17;
        currentY += drawWrapped("gui.modern.baritone_cmd.u036",
                x, currentY, width, ModernUiRenderer.TEXT, MAX_VISIBLE_DETAIL_LINES);
        currentY += 12;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u037", x, currentY, ModernUiRenderer.ACCENT,
                Math.max(30, width - 4));
        currentY += 17;
        boolean found = false;
        for (String line : selected.longDesc) {
            String normalized = strip(line).toLowerCase(Locale.ROOT);
            if (normalized.contains("gui.modern.baritone_cmd.u038") || normalized.contains("usage") || normalized.startsWith(">")) {
                currentY += drawWrapped(line, x, currentY, width, ModernUiRenderer.SUBTLE_TEXT,
                        MAX_VISIBLE_DETAIL_LINES);
                found = true;
            }
        }
        if (!found) {
            drawWrapped("gui.modern.baritone_cmd.u039", x, currentY, width,
                    ModernUiRenderer.MUTED_TEXT, MAX_VISIBLE_DETAIL_LINES);
        }
    }

    private void drawExamples(CommandEntry selected, int x, int y, int width, int mouseX, int mouseY) {
        exampleHits.clear();
        if (selected.examples.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u040", x, y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, width - 4));
            return;
        }
        int currentY = y;
        for (ExampleEntry example : selected.examples) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(x, currentY, width, 43);
            boolean hovered = row.contains(mouseX, mouseY) && detailClipBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    hovered ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, "> " + example.commandText, row.x + 8, row.y + 7,
                    ModernUiRenderer.TEXT, Math.max(30, row.width - 78));
            ModernUiRenderer.drawText(fontRenderer, example.description.isEmpty() ? "gui.modern.baritone_cmd.u041" : example.description,
                    row.x + 8, row.y + 24, ModernUiRenderer.MUTED_TEXT, Math.max(30, row.width - 78));
            ModernMainLayout.Rect fill = new ModernMainLayout.Rect(row.right() - 62, row.y + 11, 53, 20);
            drawButton(fill, "gui.modern.baritone_cmd.u042", true, fill.contains(mouseX, mouseY));
            exampleHits.add(new ExampleHit(example, row));
            currentY += 49;
        }
    }

    private void drawHistory(int x, int y, int width, int mouseX, int mouseY) {
        historyHits.clear();
        if (executionHistory.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u043", x, y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, width - 4));
            return;
        }
        int currentY = y;
        for (HistoryEntry entry : executionHistory) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(x, currentY, width, 28);
            boolean hovered = row.contains(mouseX, mouseY) && detailClipBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, safe(entry.time), row.x + 8, row.y + 8,
                    ModernUiRenderer.MUTED_TEXT, 50);
            ModernUiRenderer.drawText(fontRenderer, "/" + safe(entry.command), row.x + 64, row.y + 8,
                    ModernUiRenderer.TEXT, Math.max(30, row.width - 72));
            historyHits.add(new HistoryHit(entry, row));
            currentY += 34;
        }
    }

    private int measureDetailHeight(CommandEntry selected, int width) {
        if (selected == null) {
            return 100;
        }
        switch (detailTab) {
        case PARAMETERS:
            return 105 + selected.longDesc.size() * 16;
        case EXAMPLES:
            return Math.max(70, selected.examples.size() * 49 + 8);
        case HISTORY:
            return Math.max(70, executionHistory.size() * 34 + 8);
        case OVERVIEW:
        default:
            int lines = Math.max(1, wrapLines(selected.shortDesc, width).size());
            for (String line : selected.longDesc) {
                lines += Math.max(1, wrapLines(line, width).size());
            }
            return 120 + lines * 12;
        }
    }

    private int drawWrapped(String text, int x, int y, int width, int color, int maxLines) {
        List<String> lines = wrapLines(text, width);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            fontRenderer.drawString(lines.get(i), x, y + i * 12,
                    ModernUiRenderer.readableText(color, ModernUiRenderer.SURFACE));
        }
        return Math.max(1, count) * 12;
    }

    private List<String> wrapLines(String text, int width) {
        String plain = strip(text).replace("\r", "");
        List<String> result = new ArrayList<>();
        for (String line : plain.split("\n", -1)) {
            List<String> wrapped = fontRenderer.listFormattedStringToWidth(line, Math.max(20, width));
            if (wrapped == null || wrapped.isEmpty()) {
                result.add("");
            } else {
                result.addAll(wrapped);
            }
        }
        return result.isEmpty() ? Collections.singletonList("") : result;
    }

    private void drawSearchField(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        boolean focused = searchField != null && searchField.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        if (bounds.width >= 23) {
            ModernUiRenderer.drawSearchIcon(bounds.x + 7, bounds.y + 7, ModernUiRenderer.SUBTLE_TEXT);
        }
        if (searchField == null) {
            return;
        }
        searchField.setVisible(true);
        searchField.setEnabled(true);
        searchField.x = bounds.x + 23;
        searchField.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        searchField.width = Math.max(1, bounds.width - 29);
        searchField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);
        if (safe(searchField.getText()).isEmpty() && !focused) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_cmd.u044", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, searchField.width));
        }
    }

    private void drawCommandField(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        boolean focused = commandField != null && commandField.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, ">", bounds.x + 7,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.ACCENT, 12);
        if (commandField == null) {
            return;
        }
        commandField.setVisible(true);
        commandField.setEnabled(true);
        commandField.x = bounds.x + 20;
        commandField.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        commandField.width = Math.max(1, bounds.width - 27);
        commandField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(commandField);
        ModernUiRenderer.drawTextField(commandField);
    }

    private void drawChip(ModernMainLayout.Rect bounds, String label, boolean selected, boolean hovered) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(10, bounds.width - 12));
    }

    private void drawButton(ModernMainLayout.Rect bounds, String label, boolean primary, boolean hovered) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                primary ? (hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT)
                        : (hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED),
                primary ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, Math.max(8, bounds.width - 12));
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clip, int contentHeight, int offset,
            int maxOffset, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (clip == null || maxOffset <= 0) {
            bar.idle();
            return;
        }
        bar.draw(clip, offset, maxOffset, clip.height, Math.max(clip.height, contentHeight), mouseX, mouseY, setter);
    }

    private void reloadCommands() {
        String previousSelection = selectedCommandName;
        allCommands.clear();
        try {
            ICommandManager manager = commandManager();
            if (manager == null || manager.getRegistry() == null) {
                statusMessage = "gui.modern.baritone_cmd.u045";
                statusMessageUntil = System.currentTimeMillis() + 2500L;
                refreshFilteredCommands();
                return;
            }
            for (ICommand command : manager.getRegistry().descendingStream().toArray(ICommand[]::new)) {
                if (command == null || command.hiddenFromHelp() || command.getNames() == null
                        || command.getNames().isEmpty()) {
                    continue;
                }
                List<String> names = command.getNames();
                String primary = names.get(0);
                List<String> aliases = names.size() > 1 ? names.subList(1, names.size())
                        : Collections.<String>emptyList();
                List<String> longDesc = command.getLongDesc() == null ? Collections.<String>emptyList()
                        : command.getLongDesc();
                allCommands.add(new CommandEntry(primary, aliases, command.getShortDesc(), longDesc,
                        classify(primary, aliases, command.getShortDesc(), longDesc)));
            }
            allCommands.sort(Comparator.comparing(entry -> entry.primaryName.toLowerCase(Locale.ROOT)));
            statusMessage = ModernFormI18n.tr("gui.modern.baritone_cmd.fmt.loaded", String.valueOf(allCommands.size()));
            statusMessageUntil = System.currentTimeMillis() + 2200L;
        } catch (Throwable throwable) {
            statusMessage = ModernFormI18n.tr("gui.modern.baritone_cmd.fmt.load_fail", throwable.getClass().getSimpleName());
            statusMessageUntil = System.currentTimeMillis() + 3000L;
        }
        refreshFilteredCommands();
        if (previousSelection != null && findCommand(previousSelection) != null) {
            selectedCommandName = previousSelection;
            syncCommandField();
        }
        ensureSelection();
    }

    private void refreshFilteredCommands() {
        filteredCommands.clear();
        String query = safe(searchText).trim().toLowerCase(Locale.ROOT);
        for (CommandEntry entry : allCommands) {
            if (listMode == ListMode.FAVORITES && !isFavorite(entry.primaryName)) {
                continue;
            }
            if (listMode == ListMode.RECENT && !containsRecent(entry.primaryName)) {
                continue;
            }
            if (activeCategory != CommandCategory.ALL && entry.category != activeCategory) {
                continue;
            }
            if (!query.isEmpty() && !entry.searchText.contains(query)) {
                continue;
            }
            filteredCommands.add(entry);
        }
        sidebarScroll = 0;
        ensureSelection();
    }

    private void ensureSelection() {
        if (filteredCommands.isEmpty()) {
            selectedCommandName = null;
            if (commandField != null) {
                commandField.setText("");
            }
            return;
        }
        if (selectedCommandName != null && findIn(filteredCommands, selectedCommandName) != null) {
            syncCommandField();
            return;
        }
        selectedCommandName = filteredCommands.get(0).primaryName;
        syncCommandField();
        detailScroll = 0;
    }

    private void selectCommand(CommandEntry entry) {
        if (entry == null) {
            return;
        }
        selectedCommandName = entry.primaryName;
        syncCommandField();
        detailTab = DetailTab.OVERVIEW;
        detailScroll = 0;
        clearFieldFocus();
    }

    private void syncCommandField() {
        if (commandField != null) {
            commandField.setText(selectedCommandName == null ? "" : selectedCommandName);
            commandField.setCursorPositionEnd();
        }
    }

    private CommandEntry getSelectedCommand() {
        return findCommand(selectedCommandName);
    }

    private CommandEntry findCommand(String name) {
        return findIn(allCommands, name);
    }

    private CommandEntry findIn(List<CommandEntry> entries, String name) {
        if (name == null) {
            return null;
        }
        for (CommandEntry entry : entries) {
            if (entry.primaryName.equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    private ICommandManager commandManager() {
        try {
            if (BaritoneAPI.getProvider() == null || BaritoneAPI.getProvider().getPrimaryBaritone() == null) {
                return null;
            }
            return BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void executeCommand() {
        if (commandField == null) {
            return;
        }
        String command = safe(commandField.getText()).trim();
        if (command.isEmpty()) {
            showStatus("gui.modern.baritone_cmd.u047", true);
            return;
        }
        try {
            ICommandManager manager = commandManager();
            if (manager == null) {
                showStatus("gui.modern.baritone_cmd.u045", true);
                return;
            }
            if (manager.execute(command)) {
                String primary = command.split("\\s+", 2)[0];
                addRecent(primary);
                executionHistory.add(0, new HistoryEntry(command));
                while (executionHistory.size() > HISTORY_LIMIT) {
                    executionHistory.remove(executionHistory.size() - 1);
                }
                savePersistentState();
                showStatus(ModernFormI18n.tr("gui.modern.baritone_cmd.u048") + command, false);
            } else {
                showStatus("gui.modern.baritone_cmd.u049", true);
            }
        } catch (Throwable throwable) {
            showStatus(ModernFormI18n.tr("gui.modern.baritone_cmd.u050") + throwable.getClass().getSimpleName(), true);
        }
    }

    private void addRecent(String command) {
        String normalized = safe(command).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }
        recentCommands.removeIf(value -> normalized.equalsIgnoreCase(value));
        recentCommands.add(0, normalized);
        while (recentCommands.size() > RECENT_LIMIT) {
            recentCommands.remove(recentCommands.size() - 1);
        }
    }

    private boolean containsRecent(String command) {
        for (String recent : recentCommands) {
            if (safe(recent).equalsIgnoreCase(command)) {
                return true;
            }
        }
        return false;
    }

    private void toggleFavorite() {
        CommandEntry selected = getSelectedCommand();
        if (selected == null) {
            return;
        }
        String normalized = selected.primaryName.toLowerCase(Locale.ROOT);
        if (!favorites.add(normalized)) {
            favorites.remove(normalized);
            showStatus(ModernFormI18n.tr("gui.modern.baritone_cmd.u051") + selected.primaryName, false);
        } else {
            showStatus(ModernFormI18n.tr("gui.modern.baritone_cmd.u052") + selected.primaryName, false);
        }
        savePersistentState();
        refreshFilteredCommands();
    }

    private boolean isFavorite(String command) {
        return favorites.contains(safe(command).toLowerCase(Locale.ROOT));
    }

    private void fillCommand(String command) {
        if (commandField == null || command == null) {
            return;
        }
        commandField.setText(command.trim());
        commandField.setCursorPositionEnd();
        clearFieldFocus();
        showStatus("gui.modern.baritone_cmd.u053", false);
    }

    private void clearCommandArguments() {
        CommandEntry selected = getSelectedCommand();
        syncCommandField();
        if (selected != null) {
            showStatus("gui.modern.baritone_cmd.u054", false);
        }
    }

    private void copyCommand() {
        if (commandField != null) {
            GuiScreen.setClipboardString(safe(commandField.getText()));
            showStatus("gui.modern.baritone_cmd.u055", false);
        }
    }

    private void showStatus(String message, boolean error) {
        statusMessage = safe(message);
        statusMessageUntil = System.currentTimeMillis() + (error ? 3200L : 2200L);
    }

    private boolean statusVisible() {
        return !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
    }

    private void loadPersistentState() {
        try {
            Path file = ProfileManager.getCurrentProfileDir().resolve(STATE_FILE_NAME);
            if (!Files.exists(file)) {
                return;
            }
            StateData data;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(reader, StateData.class);
            }
            if (data == null) {
                return;
            }
            if (data.favorites != null) {
                for (String favorite : data.favorites) {
                    if (!safe(favorite).trim().isEmpty()) {
                        favorites.add(favorite.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (data.recent != null) {
                for (String recent : data.recent) {
                    addRecent(recent);
                }
            }
            if (data.history != null) {
                for (HistoryEntry entry : data.history) {
                    if (entry != null && !safe(entry.command).trim().isEmpty()) {
                        executionHistory.add(entry);
                    }
                }
            }
            while (executionHistory.size() > HISTORY_LIMIT) {
                executionHistory.remove(executionHistory.size() - 1);
            }
        } catch (Exception ignored) {
            // A broken optional history file must not prevent the command tab
            // from opening; the command registry remains fully usable.
        }
    }

    @Override
    public void save() {
        savePersistentState();
    }

    private void savePersistentState() {
        try {
            Path file = ProfileManager.getCurrentProfileDir().resolve(STATE_FILE_NAME);
            Files.createDirectories(file.getParent());
            StateData data = new StateData();
            data.favorites = new ArrayList<>(favorites);
            data.recent = new ArrayList<>(recentCommands);
            data.history = new ArrayList<>(executionHistory);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
            // Persistence is best effort; command execution must remain usable.
        }
    }

    private static CommandCategory classify(String primary, List<String> aliases, String shortDesc,
            List<String> longDesc) {
        StringBuilder builder = new StringBuilder(safe(primary)).append(' ').append(safe(shortDesc));
        if (aliases != null) {
            for (String alias : aliases) {
                builder.append(' ').append(safe(alias));
            }
        }
        if (longDesc != null) {
            for (String line : longDesc) {
                builder.append(' ').append(safe(line));
            }
        }
        String text = builder.toString().toLowerCase(Locale.ROOT);
        if (containsAny(text, "goto", "goal", "follow", "come", "path", "explore", "axis", "tunnel", "farm",
                "highway")) {
            return CommandCategory.NAVIGATION;
        }
        if (containsAny(text, "mine", "build", "schem", "sel", "surface", "waypoint", "click", "place")) {
            return CommandCategory.WORLD;
        }
        if (containsAny(text, "set", "modified", "reload", "reset", "pause", "resume", "stop", "cancel",
                "invert", "proc")) {
            return CommandCategory.CONTROL;
        }
        if (containsAny(text, "help", "list", "version", "eta", "wp")) {
            return CommandCategory.INFO;
        }
        return CommandCategory.OTHER;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int categoryColor(CommandCategory category) {
        switch (category) {
        case NAVIGATION:
            return 0xFF6AA9FF;
        case WORLD:
            return 0xFF5FD39A;
        case CONTROL:
            return 0xFFF0B55E;
        case INFO:
            return 0xFF69D4CC;
        case OTHER:
        default:
            return 0xFFC58CFF;
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (handleFilterDropdownClick(mouseX, mouseY)) {
            return true;
        }
        if (compactScrollbar.beginDrag(mouseX, mouseY) || sidebarScrollbar.beginDrag(mouseX, mouseY)
                || detailScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (contains(dividerBounds, mouseX, mouseY)) {
            draggingSidebar = true;
            clearFieldFocus();
            return true;
        }
        if (contains(searchFieldBounds(), mouseX, mouseY)) {
            clearFieldFocus();
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(commandInputBounds, mouseX, mouseY)) {
            clearFieldFocus();
            commandField.setFocused(true);
            commandField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(reloadBounds, mouseX, mouseY)) {
            reloadCommands();
            return true;
        }
        if (contains(favoriteBounds, mouseX, mouseY)) {
            toggleFavorite();
            return true;
        }
        if (contains(executeBounds, mouseX, mouseY)) {
            executeCommand();
            return true;
        }
        if (contains(copyBounds, mouseX, mouseY)) {
            copyCommand();
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            clearCommandArguments();
            return true;
        }
        for (int i = commandHits.size() - 1; i >= 0; i--) {
            CommandHit hit = commandHits.get(i);
            if (sidebarClipBounds != null && sidebarClipBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                selectCommand(hit.entry);
                return true;
            }
        }
        for (int i = 0; i < detailTabHits.size(); i++) {
            if (detailTabHits.get(i).contains(mouseX, mouseY)) {
                detailTab = DetailTab.values()[i];
                detailScroll = 0;
                return true;
            }
        }
        for (ExampleHit hit : exampleHits) {
            if (detailClipBounds != null && detailClipBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                fillCommand(hit.entry.commandText);
                return true;
            }
        }
        for (HistoryHit hit : historyHits) {
            if (detailClipBounds != null && detailClipBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                fillCommand(hit.entry.command);
                return true;
            }
        }
        clearFieldFocus();
        return true;
    }

    private boolean handleFilterDropdownClick(int mouseX, int mouseY) {
        if (listModeDropdown.isOpen()) {
            boolean handled = listModeDropdown.mouseClicked(mouseX, mouseY);
            applyFilterDropdowns();
            return handled;
        }
        if (categoryDropdown.isOpen()) {
            boolean handled = categoryDropdown.mouseClicked(mouseX, mouseY);
            applyFilterDropdowns();
            return handled;
        }
        if (contains(listModeFilterBounds, mouseX, mouseY)) {
            listModeDropdown.mouseClicked(mouseX, mouseY);
            return true;
        }
        if (contains(categoryFilterBounds, mouseX, mouseY)) {
            categoryDropdown.mouseClicked(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void applyFilterDropdowns() {
        ListMode nextListMode = listModeForValue(listModeDropdown.value());
        CommandCategory nextCategory = categoryForValue(categoryDropdown.value());
        if (listMode != nextListMode || activeCategory != nextCategory) {
            listMode = nextListMode;
            activeCategory = nextCategory;
            refreshFilteredCommands();
        }
    }

    private ListMode listModeForValue(String value) {
        if ("favorites".equalsIgnoreCase(value)) {
            return ListMode.FAVORITES;
        }
        if ("recent".equalsIgnoreCase(value)) {
            return ListMode.RECENT;
        }
        return ListMode.ALL;
    }

    private CommandCategory categoryForValue(String value) {
        for (CommandCategory category : CommandCategory.values()) {
            if (category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        return CommandCategory.ALL;
    }

    private void drawFilterDropdownMenus(int mouseX, int mouseY) {
        listModeDropdown.drawMenu(fontRenderer, panelBounds, mouseX, mouseY);
        categoryDropdown.drawMenu(fontRenderer, panelBounds, mouseX, mouseY);
    }

    private ModernMainLayout.Rect searchFieldBounds() {
        if (sidebarBounds == null) {
            return null;
        }
        return new ModernMainLayout.Rect(sidebarBounds.x + 10, sidebarBounds.y + 28,
                Math.max(1, sidebarBounds.width - 20), 25);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && compactScrollbar.isDragging()) {
            compactScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && sidebarScrollbar.isDragging()) {
            sidebarScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && detailScrollbar.isDragging()) {
            detailScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && draggingSidebar && panelBounds != null) {
            int paneX = panelBounds.x + 10;
            int paneWidth = Math.max(2, panelBounds.width - 20);
            int gap = Math.min(PANE_GAP, Math.max(0, paneWidth - 2));
            int splitWidth = Math.max(2, paneWidth - gap);
            int pointerOffset = mouseX - paneX - gap / 2;
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitWidth, pointerOffset,
                    SIDEBAR_MIN_WIDTH, DETAIL_MIN_WIDTH, SIDEBAR_FLOOR_WIDTH, DETAIL_FLOOR_WIDTH);
            sidebarRatio = split.ratio;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (compactScrollbar.isDragging() || sidebarScrollbar.isDragging()
                || detailScrollbar.isDragging())) {
            compactScrollbar.endDrag();
            sidebarScrollbar.endDrag();
            detailScrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingSidebar) {
            draggingSidebar = false;
            MainUiLayoutManager.setModernSplitRatio("baritone.command.sidebar", sidebarRatio);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (searchField != null && searchField.isFocused()
                && searchField.textboxKeyTyped(typedChar, keyCode)) {
            searchText = safe(searchField.getText());
            refreshFilteredCommands();
            return true;
        }
        if (commandField != null && commandField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                executeCommand();
                return true;
            }
            if (commandField.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
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
        if (compactLayout && sidebarBounds != null && sidebarBounds.contains(mouseX, mouseY)) {
            int before = sidebarScroll;
            sidebarScroll = clamp(sidebarScroll + (wheel > 0 ? -36 : 36), 0, sidebarMaxScroll);
            if (before != sidebarScroll) return true;
            return scrollCompact(wheel);
        }
        if (compactLayout && detailBounds != null && detailBounds.contains(mouseX, mouseY)) {
            int before = detailScroll;
            detailScroll = clamp(detailScroll + (wheel > 0 ? -36 : 36), 0, detailMaxScroll);
            if (before != detailScroll) return true;
            return scrollCompact(wheel);
        }
        if (compactLayout && compactViewport != null && compactViewport.contains(mouseX, mouseY)) {
            return scrollCompact(wheel);
        }
        if (sidebarClipBounds != null && sidebarClipBounds.contains(mouseX, mouseY) && sidebarMaxScroll > 0) {
            int before = sidebarScroll;
            sidebarScroll = clamp(sidebarScroll + (wheel > 0 ? -36 : 36), 0, sidebarMaxScroll);
            return before != sidebarScroll;
        }
        if (detailClipBounds != null && detailClipBounds.contains(mouseX, mouseY) && detailMaxScroll > 0) {
            int before = detailScroll;
            detailScroll = clamp(detailScroll + (wheel > 0 ? -36 : 36), 0, detailMaxScroll);
            return before != detailScroll;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (compactScrollbar.isDragging() || sidebarScrollbar.isDragging() || detailScrollbar.isDragging()) {
            compactScrollbar.endDrag();
            sidebarScrollbar.endDrag();
            detailScrollbar.endDrag();
            return true;
        }
        if (listModeDropdown.isOpen() || categoryDropdown.isOpen()) {
            listModeDropdown.close();
            categoryDropdown.close();
            return true;
        }
        if (searchField != null && !safe(searchField.getText()).isEmpty()) {
            searchField.setText("");
            searchText = "";
            refreshFilteredCommands();
            clearFieldFocus();
            return true;
        }
        if (searchField != null && searchField.isFocused() || commandField != null && commandField.isFocused()) {
            clearFieldFocus();
            return true;
        }
        return false;
    }

    private boolean scrollCompact(int wheel) {
        int before = compactScroll;
        compactScroll = clamp(compactScroll + (wheel > 0 ? -36 : 36), 0, compactMaxScroll);
        return before != compactScroll;
    }

    private void clearFieldFocus() {
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (commandField != null) {
            commandField.setFocused(false);
        }
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contentBounds != null && contentBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return contains(dividerBounds, mouseX, mouseY) ? "gui.modern.baritone_cmd.u056" : "";
    }

    @Override
    public void discardDraft() {
        clearFieldFocus();
        draggingSidebar = false;
        compactScrollbar.endDrag();
        sidebarScrollbar.endDrag();
        detailScrollbar.endDrag();
        listModeDropdown.close();
        categoryDropdown.close();
        compactScroll = 0;
    }

    private static ModernMainLayout.Rect safePanel(ModernMainLayout.Rect source) {
        int inset = Math.min(12, Math.max(4, Math.min(source.width, source.height) / 10));
        return new ModernMainLayout.Rect(source.x + inset, source.y + inset,
                Math.max(1, source.width - inset * 2), Math.max(1, source.height - inset * 2));
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String strip(String value) {
        String stripped = TextFormatting.getTextWithoutFormattingCodes(safe(value));
        return stripped == null ? safe(value) : stripped;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
