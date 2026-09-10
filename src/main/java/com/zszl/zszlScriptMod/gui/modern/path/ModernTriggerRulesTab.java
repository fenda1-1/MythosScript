package com.zszl.zszlScriptMod.gui.modern.path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.rules.RuleTreeToggle;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.path.trigger.LegacyTriggerEventItem;
import com.zszl.zszlScriptMod.gui.path.trigger.LegacyTriggerEventLibrary;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.zszl.zszlScriptMod.path.trigger.PlayerListTriggerSupport;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiInspectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Modern three-column workbench backed by the existing trigger-rule model. */
public final class ModernTriggerRulesTab implements ModernSettingsTab {
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("trigger_rules");

    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 31;
    private static final int FIELD_H = 19;
    private static final int TREE_ROW_H = 38;
    private static final int EVENT_ROW_H = 31;
    private static final List<LegacyTriggerEventItem> EVENTS = LegacyTriggerEventLibrary.createDefaultItems();

    private static final class TreeRow {
        final String category;
        final int ruleIndex;
        final boolean group;

        TreeRow(String category, int ruleIndex, boolean group) {
            this.category = category;
            this.ruleIndex = ruleIndex;
            this.group = group;
        }
    }

    private static final class ParamField {
        final String key;
        final String label;
        final String hint;

        ParamField(String key, String label, String hint) {
            this.key = key;
            this.label = label;
            this.hint = hint;
        }
    }

    private final Minecraft minecraft;
    private final List<LegacySequenceTriggerManager.RuleEditModel> rules = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<TreeRow> treeRows = new ArrayList<>();
    private final List<LegacyTriggerEventItem> eventRows = new ArrayList<>();
    private final List<ModernMainLayout.Rect> treeHits = new ArrayList<>();
    private final List<ModernMainLayout.Rect> eventHits = new ArrayList<>();
    private final List<ModernMainLayout.Rect> tabHits = new ArrayList<>();
    private final Set<String> collapsedGroups = new HashSet<>();
    private final Set<String> collapsedEventGroups = new HashSet<>();
    private final RuleTreeToggle ruleToggle = new RuleTreeToggle();
    private final Map<String, GuiTextField> fields = new LinkedHashMap<>();
    private final Map<String, ModernMainLayout.Rect> fieldBounds = new HashMap<>();

    private FontRenderer font;
    private ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect header;
    private ModernMainLayout.Rect footer;
    private ModernMainLayout.Rect treePane;
    private ModernMainLayout.Rect eventPane;
    private ModernMainLayout.Rect editorPane;
    private ModernMainLayout.Rect treeDivider;
    private ModernMainLayout.Rect eventDivider;
    private ModernMainLayout.Rect editorViewport;
    private ModernMainLayout.Rect addRule;
    private ModernMainLayout.Rect deleteRule;
    private ModernMainLayout.Rect moveUp;
    private ModernMainLayout.Rect moveDown;
    private ModernMainLayout.Rect addGroup;
    private ModernMainLayout.Rect renameGroup;
    private ModernMainLayout.Rect deleteGroup;
    private ModernMainLayout.Rect enabledToggle;
    private ModernMainLayout.Rect backgroundToggle;
    private ModernMainLayout.Rect sequencePicker;
    private final ModernPathSequencePicker sequenceSelector = new ModernPathSequencePicker(this::selectSequence);
    private ModernMainLayout.Rect optionToggle;
    private ModernMainLayout.Rect secondaryOptionToggle;
    private ModernMainLayout.Rect importButton;
    private ModernMainLayout.Rect validateButton;
    private ModernMainLayout.Rect reloadButton;
    private ModernMainLayout.Rect saveButton;

    private int selectedRule = -1;
    private int selectedTab;
    private int treeScroll;
    private int treeMaxScroll;
    private int eventScroll;
    private int eventMaxScroll;
    private int editorScroll;
    private int editorMaxScroll;
    private double treeRatio = 0.22D;
    private double eventRatio = 0.25D;
    private boolean draggingTreeDivider;
    private boolean draggingEventDivider;
    private final ModernHoverScrollbar treeScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar eventScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar editorScrollBar = new ModernHoverScrollbar();
    private boolean initialized;
    private boolean dirty;
    private boolean returnRequested;
    private boolean editingEnabled = true;
    private boolean editingBackground;
    private boolean idleExcludePath = true;
    private boolean idleIgnoreDamage;
    private String packetDirection = "";
    private String entityType = "";
    private String selectedGroup = LegacySequenceTriggerManager.CATEGORY_UNGROUPED;
    private String focusedField;
    private String hoveredTooltip = "";
    private String status = "gui.modern.path.trigger.u001";

    public ModernTriggerRulesTab(Minecraft minecraft, ModernScreenContext context) {
        this.minecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        font = fontRenderer;
        sequenceSelector.ensureInitialized(fontRenderer);
        ensureField("rule.search", 128);
        ensureField("event.search", 128);
        ensureField("group.name", 128);
        for (String key : Arrays.asList("name", "category", "sequence", "count", "cooldown", "contains", "note",
                "guiTitle", "guiClass", "chatText", "packetText", "channel", "text", "keyName", "idleMs",
                "intervalSeconds", "hpThreshold", "foodThreshold", "damageSource", "minDamage", "fromText",
                "toText", "inventoryText", "minFilledSlots", "itemText", "minCount", "entityText", "players")) {
            ensureField("trigger." + key, "note".equals(key) || "players".equals(key) ? 2048 : 256);
        }
        if (!initialized) {
            treeRatio = MainUiLayoutManager.getModernSplitRatio("path.trigger.tree", treeRatio);
            eventRatio = MainUiLayoutManager.getModernSplitRatio("path.trigger.event", eventRatio);
            initialized = true;
            reloadDraft();
        }
    }

    @Override
    public void updateScreen() {
        sequenceSelector.updateScreen();
        for (GuiTextField field : fields.values()) field.updateCursorCounter();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        bounds = contentBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : contentBounds;
        idleTriggerScrollBars();
        hoveredTooltip = "";
        fieldBounds.clear();
        hideFieldsAndOptions();
        header = new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, HEADER_H);
        footer = new ModernMainLayout.Rect(bounds.x, bounds.bottom() - FOOTER_H, bounds.width, FOOTER_H);
        drawHeader(mouseX, mouseY);
        layoutPanes(mouseX, mouseY);
        drawTree(mouseX, mouseY);
        drawEvents(mouseX, mouseY);
        drawEditor(mouseX, mouseY);
        drawFooter(mouseX, mouseY);
        if (sequenceSelector.isOpen()) {
            hoveredTooltip = "";
            sequenceSelector.draw(font, bounds, tr("gui.modern.path.trigger.u030"), mouseX, mouseY);
        }
        navigationActions.drawOverlay(mouseX, mouseY);
    }

    private void drawHeader(int mouseX, int mouseY) {
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, "gui.modern.path.trigger.u002", header.x + 12, header.y + 8,
                ModernUiRenderer.TEXT, Math.max(80, header.width - 24));
        ModernUiRenderer.drawText(font, "gui.modern.path.trigger.u003",
                header.x + 12, header.y + 23, ModernUiRenderer.MUTED_TEXT, Math.max(80, header.width - 24));
        ModernUiRenderer.drawDivider(header.x + 10, header.bottom(), Math.max(1, header.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void layoutPanes(int mouseX, int mouseY) {
        int gap = 8;
        int top = header.bottom() + 8;
        int height = Math.max(1, footer.y - top - 8);
        int total = Math.max(3, bounds.width - 20 - gap * 2);
        ModernSplitPane.Split first = ModernSplitPane.calculate(total, treeRatio, 175, 470, 125, 300);
        treeRatio = first.ratio;
        ModernSplitPane.Split second = ModernSplitPane.calculate(first.secondWidth, eventRatio, 190, 270, 135, 190);
        eventRatio = second.ratio;
        treePane = new ModernMainLayout.Rect(bounds.x + 10, top, first.firstWidth, height);
        eventPane = new ModernMainLayout.Rect(treePane.right() + gap, top, second.firstWidth, height);
        editorPane = new ModernMainLayout.Rect(eventPane.right() + gap, top,
                Math.max(1, bounds.right() - 10 - eventPane.right() - gap), height);
        treeDivider = ModernSplitPane.verticalDividerBounds(treePane.x, treePane.width, gap, top, height);
        eventDivider = ModernSplitPane.verticalDividerBounds(eventPane.x, eventPane.width, gap, top, height);
        ModernSplitPane.drawVerticalDivider(treeDivider, mouseX, mouseY, draggingTreeDivider);
        ModernSplitPane.drawVerticalDivider(eventDivider, mouseX, mouseY, draggingEventDivider);
        if (treeDivider.contains(mouseX, mouseY)) hoveredTooltip = "gui.modern.path.trigger.u004";
        if (eventDivider.contains(mouseX, mouseY)) hoveredTooltip = "gui.modern.path.trigger.u005";
    }

    private void configureNavigationActions() {
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_add", "", value -> { set("group.name", value); addGroup(); }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", selectedGroup, value -> { set("group.name", value); renameGroup(); }));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !LegacySequenceTriggerManager.CATEGORY_UNGROUPED.equalsIgnoreCase(selectedGroup), this::deleteGroup);
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::addRule);
        navigationActions.action("delete", "gui.modern.nav.delete", true, true, () -> selectedRule >= 0, this::deleteRule);
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> selectedRule > 0, () -> moveRule(-1));
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> selectedRule >= 0 && selectedRule + 1 < rules.size(), () -> moveRule(1));
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedGroups.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> collapsedGroups.addAll(categories));
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> selectedRule >= 0, () -> { flushSelected(); navigationActions.prompt("gui.modern.nav.rename", rules.get(selectedRule).name, value -> { rules.get(selectedRule).name = value; bindSelected(); markDirty(); }); });
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> selectedRule >= 0, () -> {
            flushSelected();
            navigationActions.choose("移动到分类", categories, selectedGroup, value -> {
                rules.get(selectedRule).category = value;
                selectedGroup = value;
                bindSelected();
                markDirty();
            });
        });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> selectedRule >= 0, () -> { editingEnabled = !editingEnabled; flushSelected(); markDirty(); });
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> selectedRule >= 0, () -> { flushSelected(); LegacySequenceTriggerManager.RuleEditModel copy = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(rules.get(selectedRule)), LegacySequenceTriggerManager.RuleEditModel.class); copy.name += " (copy)"; rules.add(++selectedRule, copy); bindSelected(); markDirty(); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationContext(int x, int y) {
        if (!navigationActions.inTree(x, y)) return false;
        clearFocus();
        flushSelected();
        for (int i = 0; i < treeHits.size(); i++) if (treeHits.get(i).contains(x, y)) {
            TreeRow row = treeRows.get(treeScroll + i);
            if (row.group) {
                selectedGroup = row.category;
                navigationActions.action("fold", "gui.modern.nav.fold", false, false, () -> true,
                        () -> { if (!collapsedGroups.add(row.category)) collapsedGroups.remove(row.category); });
                navigationActions.context(x, y, "add", "category_add", "category_rename", "fold", "category_delete");
            } else {
                selectedRule = row.ruleIndex;
                selectedGroup = normalizeCategory(rules.get(selectedRule).category);
                bindSelected();
                navigationActions.context(x, y, "add", "copy", "rename", "move", "toggle", "up", "down", "delete");
            }
            return true;
        }
        navigationActions.context(x, y, "add", "category_add", "expand", "collapse");
        return true;
    }

    private void drawTree(int mouseX, int mouseY) {
        configureNavigationActions();
        navigationActions.begin(font, treePane);
        ModernUiRenderer.drawSubtlePanel(treePane.x, treePane.y, treePane.width, treePane.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernMainLayout.Rect search = new ModernMainLayout.Rect(treePane.x + 7, treePane.y + 30,
                Math.max(1, treePane.width - 14), FIELD_H);
        drawField("rule.search", search, "gui.modern.path.trigger.u007");
        rebuildTreeRows();
        int controlsTop = navigationActions.contentBottom();
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(treePane.x + 5, search.bottom() + 7,
                Math.max(1, treePane.width - 10), Math.max(1, controlsTop - search.bottom() - 10));
        treeHits.clear();
        int visible = Math.max(1, list.height / TREE_ROW_H);
        treeMaxScroll = Math.max(0, treeRows.size() - visible);
        treeScroll = clamp(treeScroll, 0, treeMaxScroll);
        ModernUiRenderer.beginClip(list);
        int y = list.y;
        ModernMainLayout.Rect parentRow = new ModernMainLayout.Rect(list.x, list.y - TREE_ROW_H, 1, TREE_ROW_H);
        for (int i = treeScroll; i < treeRows.size() && i < treeScroll + visible; i++) {
            TreeRow row = treeRows.get(i);
            ModernMainLayout.Rect hit = ModernTreeGuide.row(list.x, ModernHoverScrollbar.contentWidth(list.width), y, TREE_ROW_H - ModernTreeGuide.GAP, row.group ? 0 : 1);
            if (row.group) parentRow = hit; else ModernTreeGuide.drawChild(list.x, 0, parentRow, hit);
            treeHits.add(hit);
            boolean selected = row.group ? row.category.equalsIgnoreCase(selectedGroup) : row.ruleIndex == selectedRule;
            drawRow(hit, selected, hit.contains(mouseX, mouseY));
            if (row.group) {
                ModernUiRenderer.drawText(font, (collapsedGroups.contains(row.category) ? ">  " : "v  ") + row.category,
                        hit.x + 7, hit.y + 8, selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(20, hit.width - 42));
                ModernUiRenderer.drawText(font, String.valueOf(countRules(row.category)), hit.right() - 21, hit.y + 8,
                        ModernUiRenderer.MUTED_TEXT, 16);
            } else {
                LegacySequenceTriggerManager.RuleEditModel rule = rules.get(row.ruleIndex);
                ModernUiRenderer.drawRoundedRect(hit.x + 7, hit.y + 7, 5, 5, 2,
                        rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING);
                ModernUiRenderer.drawText(font, displayRule(rule), hit.x + 18, hit.y + 4,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(20, hit.width - 24));
                ModernUiRenderer.drawText(font, safe(rule.sequenceName), hit.x + 18, hit.y + 15,
                        ModernUiRenderer.MUTED_TEXT, Math.max(20, hit.width - 24));
            }
            y += TREE_ROW_H;
        }
        ModernUiRenderer.endClip();
        treeScrollBar.draw(list, treeScroll, treeMaxScroll, visible, treeRows.size(), mouseX, mouseY,
                value -> treeScroll = value);

        navigationActions.draw(mouseX, mouseY);
    }

    private void drawEvents(int mouseX, int mouseY) {
        drawPane(eventPane, "gui.modern.path.trigger.u016", tr("gui.modern.path.trigger.fmt.event_count", String.valueOf(eventCount())));
        ModernMainLayout.Rect search = new ModernMainLayout.Rect(eventPane.x + 7, eventPane.y + 39,
                Math.max(1, eventPane.width - 14), FIELD_H);
        drawField("event.search", search, "gui.modern.path.trigger.u017");
        rebuildEventRows();
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(eventPane.x + 5, search.bottom() + 7,
                Math.max(1, eventPane.width - 10), Math.max(1, eventPane.bottom() - search.bottom() - 12));
        eventHits.clear();
        int visible = Math.max(1, list.height / EVENT_ROW_H);
        eventMaxScroll = Math.max(0, eventRows.size() - visible);
        eventScroll = clamp(eventScroll, 0, eventMaxScroll);
        ModernUiRenderer.beginClip(list);
        int y = list.y;
        for (int i = eventScroll; i < eventRows.size() && i < eventScroll + visible; i++) {
            LegacyTriggerEventItem item = eventRows.get(i);
            ModernMainLayout.Rect hit = new ModernMainLayout.Rect(list.x + 1, y, ModernHoverScrollbar.contentWidth(list.width - 1), EVENT_ROW_H - 3);
            eventHits.add(hit);
            if (item.header) {
                ModernUiRenderer.drawText(font, (collapsedEventGroups.contains(item.label) ? "> " : "v ") + item.label,
                        hit.x + 7, hit.y + 9, ModernUiRenderer.ACCENT, Math.max(20, hit.width - 14));
                ModernUiRenderer.drawDivider(hit.x + 5, hit.bottom() - 2, Math.max(1, hit.width - 10),
                        ModernUiRenderer.BORDER_SUBTLE);
            } else {
                boolean selected = item.type.equals(selectedType());
                drawRow(hit, selected, hit.contains(mouseX, mouseY));
                ModernUiRenderer.drawText(font, item.label, hit.x + 8, hit.y + 5,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(20, hit.width - 16));
                ModernUiRenderer.drawText(font, item.type, hit.x + 8, hit.y + 17,
                        ModernUiRenderer.MUTED_TEXT, Math.max(20, hit.width - 16));
                if (hit.contains(mouseX, mouseY)) hoveredTooltip = item.help;
            }
            y += EVENT_ROW_H;
        }
        ModernUiRenderer.endClip();
        eventScrollBar.draw(list, eventScroll, eventMaxScroll, visible, eventRows.size(), mouseX, mouseY,
                value -> eventScroll = value);
    }

    private void drawEditor(int mouseX, int mouseY) {
        drawPane(editorPane, "gui.modern.path.trigger.u018", selectedRule >= 0 ? eventLabel(selectedType()) : "gui.modern.path.trigger.u019");
        String[] labels = { "gui.modern.path.trigger.u020", "gui.modern.path.trigger.u021", "gui.modern.path.trigger.u022" };
        tabHits.clear();
        int tabY = editorPane.y + 38;
        int tabW = Math.max(40, (editorPane.width - 14) / labels.length);
        for (int i = 0; i < labels.length; i++) {
            int x = editorPane.x + 7 + i * tabW;
            ModernMainLayout.Rect tab = new ModernMainLayout.Rect(x, tabY,
                    i == labels.length - 1 ? Math.max(1, editorPane.right() - x - 7) : tabW, 21);
            tabHits.add(tab);
            drawButton(tab, labels[i], i == selectedTab, false, selectedRule >= 0, mouseX, mouseY);
        }
        editorViewport = new ModernMainLayout.Rect(editorPane.x + 7, tabY + 27,
                Math.max(1, editorPane.width - 14), Math.max(1, editorPane.bottom() - tabY - 34));
        ModernUiRenderer.drawSubtlePanel(editorViewport.x, editorViewport.y, editorViewport.width,
                editorViewport.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        if (selectedRule < 0 || selectedRule >= rules.size()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.trigger.u023", editorViewport.x + 12, editorViewport.y + 16,
                    ModernUiRenderer.TEXT, Math.max(30, editorViewport.width - 24));
            ModernUiRenderer.drawText(font, "gui.modern.path.trigger.u024",
                    editorViewport.x + 12, editorViewport.y + 32, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, editorViewport.width - 24));
            editorMaxScroll = 0;
            return;
        }
        ModernUiRenderer.beginClip(editorViewport);
        int y = editorViewport.y + 8 - editorScroll;
        if (selectedTab == 0) y = drawBaseEditor(y, mouseX, mouseY);
        else if (selectedTab == 1) y = drawEventEditor(y, mouseX, mouseY);
        else y = drawDebugEditor(y);
        ModernUiRenderer.endClip();
        editorMaxScroll = Math.max(0, y + editorScroll + 5 - editorViewport.bottom());
        editorScroll = clamp(editorScroll, 0, editorMaxScroll);
        editorScrollBar.draw(editorViewport, editorScroll, editorMaxScroll, editorViewport.height,
                editorViewport.height + editorMaxScroll, mouseX, mouseY, value -> editorScroll = value);
    }

    private int drawBaseEditor(int y, int mouseX, int mouseY) {
        y = drawInfo(y, "gui.modern.path.trigger.u020", "gui.modern.path.trigger.u025", ModernUiRenderer.ACCENT);
        y = drawEditorField("trigger.name", "gui.modern.path.trigger.u026", "gui.modern.path.trigger.u027", y);
        y = drawEditorField("trigger.category", "gui.modern.path.trigger.u028", "gui.modern.path.trigger.u029", y);
        int available = Math.max(1, editorViewport.width - labelWidth() - 21 - ModernHoverScrollbar.GUTTER);
        int inputWidth = Math.max(50, available - 74);
        drawEditorFieldAt("trigger.sequence", "gui.modern.path.trigger.u030", "gui.modern.path.trigger.u031", y, inputWidth);
        sequencePicker = new ModernMainLayout.Rect(editorViewport.x + labelWidth() + 13 + inputWidth + 5, y, 68, FIELD_H);
        drawButton(sequencePicker, "gui.modern.path.trigger.u032", false, false, true, mouseX, mouseY);
        y += 29;
        y = drawEditorField("trigger.count", "gui.modern.path.trigger.u033", "gui.modern.path.trigger.u034", y);
        ModernUiRenderer.drawText(font, "gui.modern.path.trigger.u035", editorViewport.x + 8, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth());
        int toggleWidth = Math.max(46, (available - 5) / 2);
        enabledToggle = new ModernMainLayout.Rect(editorViewport.x + labelWidth() + 13, y, toggleWidth, 20);
        backgroundToggle = new ModernMainLayout.Rect(enabledToggle.right() + 5, y,
                Math.max(1, editorViewport.right() - enabledToggle.right() - 13), 20);
        drawButton(enabledToggle, editingEnabled ? "gui.modern.path.trigger.u036" : "gui.modern.path.trigger.u037", editingEnabled, !editingEnabled,
                true, mouseX, mouseY);
        drawButton(backgroundToggle, editingBackground ? "gui.modern.path.trigger.u038" : "gui.modern.path.trigger.u039", editingBackground, false,
                true, mouseX, mouseY);
        y += 29;
        y = drawEditorField("trigger.cooldown", "gui.modern.path.trigger.u040", "gui.modern.path.trigger.u041", y);
        y = drawEditorField("trigger.contains", "gui.modern.path.trigger.u042", "gui.modern.path.trigger.u043", y);
        y = drawEditorField("trigger.note", "gui.modern.path.trigger.u044", "gui.modern.path.trigger.u045", y);
        return y;
    }

    private int drawEventEditor(int y, int mouseX, int mouseY) {
        String type = selectedType();
        y = drawInfo(y, eventLabel(type) + " · " + type, eventHelp(type), ModernUiRenderer.ACCENT);
        List<ParamField> specs = eventFields(type);
        for (ParamField spec : specs) y = drawEditorField("trigger." + spec.key, spec.label, spec.hint, y);
        if (LegacySequenceTriggerManager.TRIGGER_PACKET.equals(type)) {
            y = drawOption(y, "gui.modern.path.trigger.u046", packetDirectionLabel(), true, mouseX, mouseY);
            importButton = drawWideButton(y, "gui.modern.path.trigger.u047", mouseX, mouseY);
            y += 29;
        } else if (isGuiType(type)) {
            importButton = drawWideButton(y, "gui.modern.path.trigger.u048", mouseX, mouseY);
            y += 29;
        } else if (LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE.equals(type)) {
            y = drawOption(y, "gui.modern.path.trigger.u049", idleExcludePath ? "gui.modern.path.trigger.u050" : "gui.modern.path.trigger.u051", true, mouseX, mouseY);
            y = drawOption(y, "gui.modern.path.trigger.u052", idleIgnoreDamage ? "gui.modern.path.trigger.u050" : "gui.modern.path.trigger.u051", false, mouseX, mouseY);
        } else if (LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY.equals(type)) {
            y = drawOption(y, "gui.modern.path.trigger.u053", entityTypeLabel(), true, mouseX, mouseY);
        }
        if (specs.isEmpty() && !LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE.equals(type)) {
            y = drawInfo(y, "gui.modern.path.trigger.u054", "gui.modern.path.trigger.u055",
                    ModernUiRenderer.MUTED_TEXT);
        }
        return y;
    }

    private int drawDebugEditor(int y) {
        LegacySequenceTriggerManager.RuleEditModel rule = rules.get(selectedRule);
        y = drawInfo(y, "gui.modern.path.trigger.u056", tr("gui.modern.path.trigger.fmt.debug_info",
                displayRule(rule), normalizeCategory(rule.category), safe(rule.sequenceName),
                tr(editingEnabled ? "gui.modern.path.trigger.u057" : "gui.modern.path.trigger.u058"),
                tr(editingBackground ? "gui.modern.path.trigger.u059" : "gui.modern.path.trigger.u060")), ModernUiRenderer.SUBTLE_TEXT);
        y = drawInfo(y, "gui.modern.path.trigger.u061", latestGui(), ModernUiRenderer.SUBTLE_TEXT);
        y = drawInfo(y, "gui.modern.path.trigger.u062", latestPacket(), ModernUiRenderer.SUBTLE_TEXT);
        Object trigger = ScopedRuntimeVariables.getGlobalValue("trigger");
        return drawInfo(y, "global.trigger", trigger == null ? "gui.modern.path.trigger.u063" : String.valueOf(trigger),
                ModernUiRenderer.SUBTLE_TEXT);
    }

    private void drawFooter(int mouseX, int mouseY) {
        ModernUiRenderer.drawDivider(footer.x + 10, footer.y, Math.max(1, footer.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        int buttonWidth = 62;
        saveButton = new ModernMainLayout.Rect(footer.right() - 72, footer.y + 6, buttonWidth, 19);
        reloadButton = new ModernMainLayout.Rect(saveButton.x - 68, footer.y + 6, buttonWidth, 19);
        validateButton = new ModernMainLayout.Rect(reloadButton.x - 68, footer.y + 6, buttonWidth, 19);
        ModernUiRenderer.drawText(font, status, footer.x + 12, footer.y + 11, statusColor(),
                Math.max(30, validateButton.x - footer.x - 20));
        drawButton(validateButton, "gui.modern.path.trigger.u064", false, false, true, mouseX, mouseY);
        drawButton(reloadButton, "gui.modern.path.trigger.u065", false, false, true, mouseX, mouseY);
        drawButton(saveButton, dirty ? "gui.modern.path.trigger.u066" : "gui.modern.path.trigger.u067", true, false, dirty, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (sequenceSelector.isOpen()) {
            if (mouseButton == 0) sequenceSelector.mouseClicked(mouseX, mouseY);
            return true;
        }
        if (navigationActions.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (mouseButton == 1 && navigationContext(mouseX, mouseY)) return true;
        if (!bounds.contains(mouseX, mouseY)) return false;
        if (mouseButton == 0 && (treeScrollBar.beginDrag(mouseX, mouseY) || eventScrollBar.beginDrag(mouseX, mouseY)
                || editorScrollBar.beginDrag(mouseX, mouseY))) {
            return true;
        }
        if (mouseButton == 0 && treeDivider != null && treeDivider.contains(mouseX, mouseY)) {
            draggingTreeDivider = true;
            return true;
        }
        if (mouseButton == 0 && eventDivider != null && eventDivider.contains(mouseX, mouseY)) {
            draggingEventDivider = true;
            return true;
        }
        String field = fieldAt(mouseX, mouseY);
        if (field != null) {
            if ("trigger.category".equals(field)) {
                flushSelected();
                clearFocus();
                navigationActions.choose("选择分组", categories, selectedGroup, value -> {
                    set("trigger.category", value);
                    selectedGroup = value;
                    markDirty();
                });
                return true;
            }
            focusField(field, mouseX);
            return true;
        }
        clearFocus();
        if (mouseButton != 0) return true;
        if (clickTree(mouseX, mouseY) || clickEvent(mouseX, mouseY)) return true;
        for (int i = 0; i < tabHits.size(); i++) {
            if (tabHits.get(i).contains(mouseX, mouseY)) {
                flushSelected();
                selectedTab = i;
                editorScroll = 0;
                return true;
            }
        }
        if (contains(addRule, mouseX, mouseY)) addRule();
        else if (contains(deleteRule, mouseX, mouseY)) deleteRule();
        else if (contains(moveUp, mouseX, mouseY)) moveRule(-1);
        else if (contains(moveDown, mouseX, mouseY)) moveRule(1);
        else if (contains(addGroup, mouseX, mouseY)) addGroup();
        else if (contains(renameGroup, mouseX, mouseY)) renameGroup();
        else if (contains(deleteGroup, mouseX, mouseY)) deleteGroup();
        else if (contains(enabledToggle, mouseX, mouseY)) { editingEnabled = !editingEnabled; markDirty(); }
        else if (contains(backgroundToggle, mouseX, mouseY)) { editingBackground = !editingBackground; markDirty(); }
        else if (contains(sequencePicker, mouseX, mouseY)) openSequenceSelector();
        else if (contains(optionToggle, mouseX, mouseY)) cyclePrimaryOption();
        else if (contains(secondaryOptionToggle, mouseX, mouseY)) { idleIgnoreDamage = !idleIgnoreDamage; markDirty(); }
        else if (contains(importButton, mouseX, mouseY)) importRecent();
        else if (contains(validateButton, mouseX, mouseY)) validateDraft();
        else if (contains(reloadButton, mouseX, mouseY)) { reloadDraft(); status = "gui.modern.path.trigger.u068"; }
        else if (contains(saveButton, mouseX, mouseY)) save();
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (sequenceSelector.isOpen()) {
            return sequenceSelector.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (clickedMouseButton != 0 || bounds == null) return false;
        if (treeScrollBar.isDragging()) {
            return treeScrollBar.applyDrag(mouseX, mouseY);
        }
        if (eventScrollBar.isDragging()) {
            return eventScrollBar.applyDrag(mouseX, mouseY);
        }
        if (editorScrollBar.isDragging()) {
            return editorScrollBar.applyDrag(mouseX, mouseY);
        }
        int gapTotal = 36;
        int total = Math.max(3, bounds.width - gapTotal);
        if (draggingTreeDivider) {
            treeRatio = ModernSplitPane.calculateFromPointer(total, mouseX - bounds.x - 10,
                    175, 470, 125, 300).ratio;
            return true;
        }
        if (draggingEventDivider && treePane != null) {
            int remaining = Math.max(2, bounds.right() - 10 - treePane.right() - 16);
            eventRatio = ModernSplitPane.calculateFromPointer(remaining, mouseX - treePane.right() - 8,
                    190, 270, 135, 190).ratio;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (sequenceSelector.isOpen()) {
            return sequenceSelector.mouseReleased(mouseX, mouseY, state);
        }
        if (state == 0 && (treeScrollBar.isDragging() || eventScrollBar.isDragging() || editorScrollBar.isDragging())) {
            treeScrollBar.endDrag();
            eventScrollBar.endDrag();
            editorScrollBar.endDrag();
            return true;
        }
        if (state == 0 && (draggingTreeDivider || draggingEventDivider)) {
            if (draggingTreeDivider) {
                MainUiLayoutManager.setModernSplitRatio("path.trigger.tree", treeRatio);
            }
            if (draggingEventDivider) {
                MainUiLayoutManager.setModernSplitRatio("path.trigger.event", eventRatio);
            }
            draggingTreeDivider = false;
            draggingEventDivider = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
        if (sequenceSelector.isOpen()) return sequenceSelector.keyTyped(typedChar, keyCode);
        if (isControlDown() && keyCode == Keyboard.KEY_S) {
            save();
            return true;
        }
        if (isControlDown() && keyCode == Keyboard.KEY_F) {
            ModernMainLayout.Rect rect = fieldBounds.get("rule.search");
            focusField("rule.search", rect == null ? 0 : rect.x);
            return true;
        }
        if (focusedField == null || "trigger.category".equals(focusedField)) return false;
        GuiTextField field = fields.get(focusedField);
        if (field == null) return false;
        boolean handled = field.textboxKeyTyped(typedChar, keyCode);
        if (handled) {
            if ("rule.search".equals(focusedField)) treeScroll = 0;
            else if ("event.search".equals(focusedField)) eventScroll = 0;
            else if (!"group.name".equals(focusedField)) markDirty();
        }
        return handled;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, -1, -1);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (navigationActions.wheel(wheel)) return true;
        if (sequenceSelector.isOpen()) {
            sequenceSelector.handleMouseWheel(wheel, mouseX, mouseY);
            return true;
        }
        if (wheel == 0) return false;
        if (treePane != null && treePane.contains(mouseX, mouseY)) {
            treeScroll = clamp(treeScroll + (wheel > 0 ? -1 : 1), 0, treeMaxScroll);
            return true;
        }
        if (eventPane != null && eventPane.contains(mouseX, mouseY)) {
            eventScroll = clamp(eventScroll + (wheel > 0 ? -1 : 1), 0, eventMaxScroll);
            return true;
        }
        if (editorViewport != null && editorViewport.contains(mouseX, mouseY)) {
            editorScroll = clamp(editorScroll + (wheel > 0 ? -24 : 24), 0, editorMaxScroll);
            clearFocus();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (sequenceSelector.isOpen()) return sequenceSelector.handleEscape();
        if (isTextInputFocused()) {
            clearFocus();
            return true;
        }
        if (dirty) {
            status = "gui.modern.path.trigger.u069";
            return true;
        }
        returnRequested = true;
        return true;
    }

    @Override
    public boolean consumeReturnRequest() {
        boolean result = returnRequested;
        returnRequested = false;
        return result;
    }

    @Override
    public void save() {
        flushSelected();
        String error = validationError();
        if (error != null) {
            status = tr("gui.modern.path.trigger.fmt.save_fail", error);
            return;
        }
        syncCategories();
        LegacySequenceTriggerManager.saveRuleModels(rules, categories);
        dirty = false;
        status = "gui.modern.path.trigger.u070";
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true;
        if (sequenceSelector.isTextInputFocused()) return true;
        GuiTextField field = focusedField == null ? null : fields.get(focusedField);
        return field != null && field.isFocused() && field.getVisible();
    }

    @Override
    public void clearTextInputFocusOutside(int mouseX, int mouseY) {
        if (sequenceSelector.isOpen()) return;
        if (fieldAt(mouseX, mouseY) == null) clearFocus();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        reloadDraft();
    }

    private boolean clickTree(int mouseX, int mouseY) {
        for (int i = 0; i < treeHits.size(); i++) {
            if (!treeHits.get(i).contains(mouseX, mouseY)) continue;
            TreeRow row = treeRows.get(treeScroll + i);
            if (row.group) {
                selectedGroup = row.category;
                set("group.name", row.category);
                if (!collapsedGroups.add(row.category)) collapsedGroups.remove(row.category);
            } else {
                LegacySequenceTriggerManager.RuleEditModel rule = rules.get(row.ruleIndex);
                boolean toggle = ruleToggle.doubleClicked(rule);
                flushSelected();
                selectedRule = row.ruleIndex;
                selectedGroup = normalizeCategory(rule.category);
                bindSelected();
                editorScroll = 0;
                if (toggle) {
                    editingEnabled = !editingEnabled;
                    flushSelected();
                    markDirty();
                    status = tr(editingEnabled ? "gui.modern.wb.fmt.enabled" : "gui.modern.wb.fmt.disabled",
                            safe(rule.name));
                }
            }
            return true;
        }
        return false;
    }

    private boolean clickEvent(int mouseX, int mouseY) {
        for (int i = 0; i < eventHits.size(); i++) {
            if (!eventHits.get(i).contains(mouseX, mouseY)) continue;
            LegacyTriggerEventItem item = eventRows.get(eventScroll + i);
            if (item.header) {
                if (!collapsedEventGroups.add(item.label)) collapsedEventGroups.remove(item.label);
            } else if (selectedRule >= 0) {
                flushSelected();
                rules.get(selectedRule).triggerType = item.type;
                rules.get(selectedRule).params = new JsonObject();
                selectedTab = 1;
                bindSelected();
                markDirty();
                editorScroll = 0;
                status = tr("gui.modern.path.trigger.fmt.switched", tr(item.label));
            }
            return true;
        }
        return false;
    }

    private void reloadDraft() {
        sequenceSelector.handleEscape();
        rules.clear();
        rules.addAll(LegacySequenceTriggerManager.getRuleModels());
        categories.clear();
        categories.addAll(LegacySequenceTriggerManager.getCategoriesSnapshot());
        syncCategories();
        selectedRule = rules.isEmpty() ? -1 : clamp(selectedRule < 0 ? 0 : selectedRule, 0, rules.size() - 1);
        selectedGroup = selectedRule < 0 ? LegacySequenceTriggerManager.CATEGORY_UNGROUPED
                : normalizeCategory(rules.get(selectedRule).category);
        bindSelected();
        dirty = false;
        treeScroll = eventScroll = editorScroll = 0;
    }

    private void addRule() {
        flushSelected();
        LegacySequenceTriggerManager.RuleEditModel rule = new LegacySequenceTriggerManager.RuleEditModel();
        rule.name = "trigger_rule_" + (rules.size() + 1);
        rule.category = normalizeCategory(selectedGroup);
        rules.add(rule);
        selectedRule = rules.size() - 1;
        bindSelected();
        markDirty();
        status = "gui.modern.path.trigger.u071";
    }

    private void deleteRule() {
        if (selectedRule < 0 || selectedRule >= rules.size()) return;
        rules.remove(selectedRule);
        selectedRule = rules.isEmpty() ? -1 : Math.min(selectedRule, rules.size() - 1);
        bindSelected();
        markDirty();
        status = "gui.modern.path.trigger.u072";
    }

    private void moveRule(int delta) {
        if (selectedRule < 0) return;
        flushSelected();
        int target = selectedRule + delta;
        if (target < 0 || target >= rules.size()) return;
        Collections.swap(rules, selectedRule, target);
        selectedRule = target;
        markDirty();
        status = "gui.modern.path.trigger.u073";
    }

    private void addGroup() {
        String name = text("group.name").trim();
        if (name.isEmpty()) { status = "gui.modern.path.trigger.u074"; return; }
        if (categoryIndex(name) >= 0) { status = tr("gui.modern.path.trigger.fmt.group_exists", name); return; }
        categories.add(name);
        selectedGroup = name;
        set("group.name", "");
        dirty = true;
        status = tr("gui.modern.path.trigger.fmt.group_created", name);
    }

    private void renameGroup() {
        String name = text("group.name").trim();
        int index = categoryIndex(selectedGroup);
        if (name.isEmpty()) { status = "gui.modern.path.trigger.u075"; return; }
        if (index < 0 || categoryIndex(name) >= 0) { status = "gui.modern.path.trigger.u076"; return; }
        String old = categories.get(index);
        categories.set(index, name);
        for (LegacySequenceTriggerManager.RuleEditModel rule : rules) {
            if (normalizeCategory(rule.category).equalsIgnoreCase(old)) rule.category = name;
        }
        selectedGroup = name;
        bindSelected();
        dirty = true;
        status = tr("gui.modern.path.trigger.fmt.group_renamed", old, name);
    }

    private void deleteGroup() {
        if (LegacySequenceTriggerManager.CATEGORY_UNGROUPED.equalsIgnoreCase(selectedGroup)) return;
        String old = selectedGroup;
        categories.removeIf(value -> value.equalsIgnoreCase(old));
        for (LegacySequenceTriggerManager.RuleEditModel rule : rules) {
            if (normalizeCategory(rule.category).equalsIgnoreCase(old)) {
                rule.category = LegacySequenceTriggerManager.CATEGORY_UNGROUPED;
            }
        }
        selectedGroup = LegacySequenceTriggerManager.CATEGORY_UNGROUPED;
        bindSelected();
        dirty = true;
        status = "gui.modern.path.trigger.u077";
    }

    private void validateDraft() {
        flushSelected();
        String error = validationError();
        status = error == null ? tr("gui.modern.path.trigger.fmt.validate_ok", String.valueOf(rules.size()))
                : tr("gui.modern.path.trigger.fmt.validate_fail", error);
    }

    private String validationError() {
        for (int i = 0; i < rules.size(); i++) {
            LegacySequenceTriggerManager.RuleEditModel rule = rules.get(i);
            if (safe(rule.sequenceName).trim().isEmpty()) return tr("gui.modern.path.trigger.fmt.rule_no_seq", String.valueOf(i + 1));
            if (PathSequenceManager.getSequence(rule.sequenceName) == null) return tr("gui.modern.path.trigger.fmt.seq_missing", rule.sequenceName);
        }
        return null;
    }

    private void bindSelected() {
        if (fields.isEmpty()) return;
        if (selectedRule < 0 || selectedRule >= rules.size()) {
            for (Map.Entry<String, GuiTextField> entry : fields.entrySet()) {
                if (entry.getKey().startsWith("trigger.")) entry.getValue().setText("");
            }
            return;
        }
        LegacySequenceTriggerManager.RuleEditModel rule = rules.get(selectedRule);
        set("trigger.name", rule.name);
        set("trigger.category", normalizeCategory(rule.category));
        set("trigger.sequence", rule.sequenceName);
        set("trigger.count", String.valueOf(rule.executionCount));
        set("trigger.cooldown", String.valueOf(Math.max(0, rule.cooldownMs)));
        set("trigger.contains", rule.contains);
        set("trigger.note", rule.note);
        editingEnabled = rule.enabled;
        editingBackground = rule.backgroundExecution;
        JsonObject params = copyJson(rule.params);
        for (String key : Arrays.asList("guiTitle", "guiClass", "chatText", "packetText", "channel", "text",
                "keyName", "damageSource", "fromText", "toText", "inventoryText", "itemText", "entityText")) {
            set("trigger." + key, stringParam(params, key));
        }
        set("trigger.idleMs", String.valueOf(intParam(params, "idleMs", 1000)));
        set("trigger.intervalSeconds", String.valueOf(intParam(params, "intervalSeconds", 1)));
        set("trigger.hpThreshold", numberParam(params, "hpThreshold", 6));
        set("trigger.foodThreshold", numberParam(params, "foodThreshold", 12));
        set("trigger.minDamage", numberParam(params, "minDamage", 0));
        set("trigger.minFilledSlots", String.valueOf(intParam(params, "minFilledSlots", 0)));
        set("trigger.minCount", String.valueOf(intParam(params, "minCount", 1)));
        packetDirection = stringParam(params, "direction");
        entityType = stringParam(params, "entityType");
        idleExcludePath = booleanParam(params, "excludePathTracking", true);
        idleIgnoreDamage = booleanParam(params, "ignoreDamageReset", false);
        set("trigger.players", playerEntriesText(PlayerListTriggerSupport.readEntries(params)));
    }

    private void flushSelected() {
        if (selectedRule < 0 || selectedRule >= rules.size() || fields.isEmpty()) return;
        LegacySequenceTriggerManager.RuleEditModel rule = rules.get(selectedRule);
        rule.name = text("trigger.name").trim();
        rule.category = normalizeCategory(text("trigger.category"));
        rule.sequenceName = text("trigger.sequence").trim();
        rule.executionCount = parseExecutionCount(text("trigger.count"));
        rule.cooldownMs = parseInt(text("trigger.cooldown"), 1000, 0);
        rule.contains = text("trigger.contains").trim();
        rule.note = text("trigger.note").trim();
        rule.enabled = editingEnabled;
        rule.backgroundExecution = editingBackground;
        JsonObject params = new JsonObject();
        String type = selectedType();
        for (ParamField spec : eventFields(type)) {
            String value = text("trigger." + spec.key).trim();
            if (isIntegerParam(spec.key)) {
                params.addProperty(spec.key, parseInt(value, integerDefault(spec.key), integerMinimum(spec.key)));
            }
            else if (isDecimalParam(spec.key)) params.addProperty(spec.key, parseDouble(value, decimalDefault(spec.key), 0));
            else if ("players".equals(spec.key)) PlayerListTriggerSupport.writeEntries(params, parsePlayerEntries(value));
            else if (!value.isEmpty()) params.addProperty(spec.key, value);
        }
        if (LegacySequenceTriggerManager.TRIGGER_PACKET.equals(type) && !packetDirection.isEmpty()) {
            params.addProperty("direction", packetDirection);
        }
        if (LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE.equals(type)) {
            params.addProperty("excludePathTracking", idleExcludePath);
            params.addProperty("ignoreDamageReset", idleIgnoreDamage);
        }
        if (LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY.equals(type) && !entityType.isEmpty()) {
            params.addProperty("entityType", entityType);
        }
        rule.params = params;
        if (categoryIndex(rule.category) < 0) categories.add(rule.category);
        selectedGroup = rule.category;
    }

    private void markDirty() {
        dirty = true;
        flushSelected();
    }

    private void openSequenceSelector() {
        if (selectedRule < 0) return;
        clearFocus();
        sequenceSelector.open(text("trigger.sequence"));
    }

    private void selectSequence(String name) {
        String selected = safe(name);
        if (!selected.equals(text("trigger.sequence"))) {
            set("trigger.sequence", selected);
            markDirty();
        }
        status = tr("gui.modern.path.trigger.fmt.selected_seq", selected, "");
    }

    private void cyclePrimaryOption() {
        String type = selectedType();
        if (LegacySequenceTriggerManager.TRIGGER_PACKET.equals(type)) {
            packetDirection = packetDirection.isEmpty() ? "inbound"
                    : "inbound".equals(packetDirection) ? "outbound" : "";
        } else if (LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE.equals(type)) {
            idleExcludePath = !idleExcludePath;
        } else if (LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY.equals(type)) {
            entityType = entityType.isEmpty() ? "player" : "player".equals(entityType) ? "hostile"
                    : "hostile".equals(entityType) ? "passive" : "";
        }
        markDirty();
    }

    private void importRecent() {
        String type = selectedType();
        if (isGuiType(type)) {
            List<GuiInspectionManager.CapturedGuiSnapshot> history = GuiInspectionManager.getHistory();
            if (history.isEmpty()) { status = "gui.modern.path.trigger.u080"; return; }
            GuiInspectionManager.CapturedGuiSnapshot snapshot = history.get(0);
            set("trigger.guiTitle", snapshot.getTitle());
            set("trigger.guiClass", snapshot.getScreenClassName());
            status = "gui.modern.path.trigger.u081";
        } else if (LegacySequenceTriggerManager.TRIGGER_PACKET.equals(type)) {
            List<String> packets = PacketCaptureHandler.getRecentPacketTextsSnapshot();
            if (packets.isEmpty()) { status = "gui.modern.path.trigger.u082"; return; }
            set("trigger.packetText", packets.get(packets.size() - 1));
            status = "gui.modern.path.trigger.u083";
        }
        markDirty();
    }

    private List<ParamField> eventFields(String type) {
        if (isGuiType(type)) return params(new ParamField("guiTitle", "gui.modern.path.trigger.u084", "gui.modern.path.trigger.u085"), new ParamField("guiClass", "gui.modern.path.trigger.u086", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_CHAT.equals(type)) return params(new ParamField("chatText", "gui.modern.path.trigger.u087", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_PACKET.equals(type)) return params(new ParamField("packetText", "gui.modern.path.trigger.u088", "gui.modern.path.trigger.u085"), new ParamField("channel", "gui.modern.path.trigger.u089", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_TITLE.equals(type) || LegacySequenceTriggerManager.TRIGGER_ACTIONBAR.equals(type)
                || LegacySequenceTriggerManager.TRIGGER_SCOREBOARD_CHANGED.equals(type) || LegacySequenceTriggerManager.TRIGGER_BOSSBAR.equals(type)) return params(new ParamField("text", "gui.modern.path.trigger.u090", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_KEY_INPUT.equals(type)) return params(new ParamField("keyName", "gui.modern.path.trigger.u091", "gui.modern.path.trigger.u092"));
        if (LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE.equals(type)) return params(new ParamField("idleMs", "gui.modern.path.trigger.u093", "gui.modern.path.trigger.u094"));
        if (LegacySequenceTriggerManager.TRIGGER_TIMER.equals(type)) return params(new ParamField("intervalSeconds", "gui.modern.path.trigger.u095", "gui.modern.path.trigger.u096"));
        if (LegacySequenceTriggerManager.TRIGGER_HP_LOW.equals(type)) return params(new ParamField("hpThreshold", "gui.modern.path.trigger.u097", "gui.modern.path.trigger.u098"));
        if (LegacySequenceTriggerManager.TRIGGER_FOOD_LOW.equals(type)) return params(new ParamField("foodThreshold", "gui.modern.path.trigger.u099", "gui.modern.path.trigger.u100"));
        if (LegacySequenceTriggerManager.TRIGGER_PLAYER_HURT.equals(type)) return params(new ParamField("damageSource", "gui.modern.path.trigger.u101", "gui.modern.path.trigger.u085"), new ParamField("minDamage", "gui.modern.path.trigger.u102", "gui.modern.path.trigger.u103"));
        if (LegacySequenceTriggerManager.TRIGGER_WORLD_CHANGED.equals(type) || LegacySequenceTriggerManager.TRIGGER_AREA_CHANGED.equals(type)) return params(new ParamField("fromText", "gui.modern.path.trigger.u104", "gui.modern.path.trigger.u085"), new ParamField("toText", "gui.modern.path.trigger.u105", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_INVENTORY_CHANGED.equals(type)) return params(new ParamField("inventoryText", "gui.modern.path.trigger.u106", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_INVENTORY_FULL.equals(type)) return params(new ParamField("minFilledSlots", "gui.modern.path.trigger.u107", "gui.modern.path.trigger.u103"));
        if (LegacySequenceTriggerManager.TRIGGER_ITEM_PICKUP.equals(type)) return params(new ParamField("itemText", "gui.modern.path.trigger.u108", "gui.modern.path.trigger.u085"), new ParamField("minCount", "gui.modern.path.trigger.u109", "gui.modern.path.trigger.u110"));
        if (LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY.equals(type)) return params(new ParamField("entityText", "gui.modern.path.trigger.u111", "gui.modern.path.trigger.u085"), new ParamField("minCount", "gui.modern.path.trigger.u112", "gui.modern.path.trigger.u110"));
        if (LegacySequenceTriggerManager.TRIGGER_ATTACK_ENTITY.equals(type) || LegacySequenceTriggerManager.TRIGGER_TARGET_KILL.equals(type)) return params(new ParamField("entityText", "gui.modern.path.trigger.u111", "gui.modern.path.trigger.u085"));
        if (LegacySequenceTriggerManager.TRIGGER_PLAYER_LIST.equals(type)) return params(new ParamField("players", "gui.modern.path.trigger.u113", "gui.modern.path.trigger.u114"));
        return Collections.emptyList();
    }

    private List<ParamField> params(ParamField... values) {
        return Arrays.asList(values);
    }

    private void rebuildTreeRows() {
        treeRows.clear();
        syncCategories();
        String query = PinyinSearchHelper.normalizeQuery(text("rule.search"));
        for (String category : categories) {
            List<Integer> matching = new ArrayList<>();
            for (int i = 0; i < rules.size(); i++) {
                LegacySequenceTriggerManager.RuleEditModel rule = rules.get(i);
                if (!normalizeCategory(rule.category).equalsIgnoreCase(category)) continue;
                String searchable = rule.name + " " + rule.sequenceName + " " + rule.triggerType + " " + rule.note + " " + category;
                if (query.isEmpty() || PinyinSearchHelper.matchesNormalized(searchable, query)) matching.add(i);
            }
            if (!query.isEmpty() && !PinyinSearchHelper.matchesNormalized(category, query) && matching.isEmpty()) continue;
            treeRows.add(new TreeRow(category, -1, true));
            if (!collapsedGroups.contains(category) || !query.isEmpty()) {
                for (Integer index : matching) treeRows.add(new TreeRow(category, index, false));
            }
        }
    }

    private void rebuildEventRows() {
        eventRows.clear();
        String query = PinyinSearchHelper.normalizeQuery(text("event.search"));
        LegacyTriggerEventItem header = null;
        List<LegacyTriggerEventItem> children = new ArrayList<>();
        for (LegacyTriggerEventItem item : EVENTS) {
            if (item.header) {
                appendEventGroup(header, children, query);
                header = item;
                children = new ArrayList<>();
            } else if (query.isEmpty() || PinyinSearchHelper.matchesNormalized(item.label + " " + item.type + " " + item.help, query)) {
                children.add(item);
            }
        }
        appendEventGroup(header, children, query);
    }

    private void appendEventGroup(LegacyTriggerEventItem header, List<LegacyTriggerEventItem> children, String query) {
        if (header == null || (!query.isEmpty() && children.isEmpty())) return;
        eventRows.add(header);
        if (!collapsedEventGroups.contains(header.label) || !query.isEmpty()) eventRows.addAll(children);
    }

    private void syncCategories() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        normalized.add(LegacySequenceTriggerManager.CATEGORY_UNGROUPED);
        for (String category : categories) normalized.add(normalizeCategory(category));
        for (LegacySequenceTriggerManager.RuleEditModel rule : rules) normalized.add(normalizeCategory(rule.category));
        categories.clear();
        categories.addAll(normalized);
    }

    private int drawEditorField(String key, String label, String hint, int y) {
        drawEditorFieldAt(key, label, hint, y, Math.max(1, editorViewport.width - labelWidth() - 21 - ModernHoverScrollbar.GUTTER));
        return y + 29;
    }

    private void drawEditorFieldAt(String key, String label, String hint, int y, int width) {
        if (y + FIELD_H <= editorViewport.y || y >= editorViewport.bottom()) {
            return;
        }
        ModernUiRenderer.drawText(font, label, editorViewport.x + 8, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth());
        ModernMainLayout.Rect rect = new ModernMainLayout.Rect(editorViewport.x + labelWidth() + 13, y,
                Math.max(1, width), FIELD_H);
        if ("trigger.category".equals(key)) {
            drawCategoryChoice(key, rect);
        } else {
            drawField(key, rect, hint);
        }
    }

    private void drawCategoryChoice(String key, ModernMainLayout.Rect rect) {
        GuiTextField field = ensureField(key, 32767);
        field.setVisible(false);
        field.setFocused(false);
        fieldBounds.put(key, rect);
        ModernUiRenderer.drawSubtlePanel(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, 4,
                0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, text(key), rect.x + 6, rect.y + 6,
                ModernUiRenderer.TEXT, Math.max(10, rect.width - 12));
    }

    private int drawOption(int y, String label, String value, boolean primary, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(font, label, editorViewport.x + 8, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth());
        ModernMainLayout.Rect rect = new ModernMainLayout.Rect(editorViewport.x + labelWidth() + 13, y,
                Math.max(1, editorViewport.width - labelWidth() - 21 - ModernHoverScrollbar.GUTTER), 20);
        if (primary) optionToggle = rect; else secondaryOptionToggle = rect;
        drawButton(rect, value, false, false, true, mouseX, mouseY);
        return y + 29;
    }

    private ModernMainLayout.Rect drawWideButton(int y, String label, int mouseX, int mouseY) {
        ModernMainLayout.Rect rect = new ModernMainLayout.Rect(editorViewport.x + labelWidth() + 13, y,
                Math.max(1, editorViewport.width - labelWidth() - 21 - ModernHoverScrollbar.GUTTER), 20);
        drawButton(rect, label, false, false, true, mouseX, mouseY);
        return rect;
    }

    private int drawInfo(int y, String title, String body, int titleColor) {
        int width = Math.max(1, editorViewport.width - 16 - ModernHoverScrollbar.GUTTER);
        List<String> lines = font.listFormattedStringToWidth(safe(body), Math.max(30, width - 14));
        int height = Math.max(39, 25 + lines.size() * 10);
        ModernUiRenderer.drawSubtlePanel(editorViewport.x + 8, y, width, height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, title, editorViewport.x + 15, y + 7, titleColor, Math.max(30, width - 14));
        int lineY = y + 20;
        for (String line : lines) {
            ModernUiRenderer.drawText(font, line, editorViewport.x + 15, lineY,
                    ModernUiRenderer.MUTED_TEXT, Math.max(30, width - 14));
            lineY += 10;
        }
        return y + height + 9;
    }

    private void drawPane(ModernMainLayout.Rect pane, String title, String subtitle) {
        ModernUiRenderer.drawSubtlePanel(pane.x, pane.y, pane.width, pane.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, title, pane.x + 9, pane.y + 8,
                ModernUiRenderer.TEXT, Math.max(30, pane.width - 18));
        ModernUiRenderer.drawText(font, subtitle, pane.x + 9, pane.y + 22,
                ModernUiRenderer.MUTED_TEXT, Math.max(30, pane.width - 18));
    }

    private void drawRow(ModernMainLayout.Rect rect, boolean selected, boolean hovered) {
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if (selected) ModernUiRenderer.drawRoundedRect(rect.x, rect.y, 3, rect.height, 2, ModernUiRenderer.ACCENT);
    }

    private void drawButton(ModernMainLayout.Rect rect, String label, boolean primary, boolean danger,
            boolean enabled, int mouseX, int mouseY) {
        if (rect == null) return;
        GuiButton button = new GuiButton(0, rect.x, rect.y, rect.width, rect.height, tr(label));
        button.enabled = enabled;
        ModernRuleEditorUi.drawButton(font, button, mouseX, mouseY,
                danger ? ModernRuleEditorUi.ButtonTone.DANGER
                        : primary ? ModernRuleEditorUi.ButtonTone.PRIMARY
                                : ModernRuleEditorUi.ButtonTone.DEFAULT);
    }

    private void drawField(String key, ModernMainLayout.Rect rect, String hint) {
        GuiTextField field = ensureField(key, 32767);
        field.x = rect.x;
        field.y = rect.y;
        field.width = rect.width;
        field.height = rect.height;
        field.setVisible(true);
        field.setEnabled(true);
        field.setFocused(key.equals(focusedField));
        fieldBounds.put(key, rect);
        ModernUiRenderer.drawSubtlePanel(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, 4,
                0xFF101820, key.equals(focusedField) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (field.getText().isEmpty() && !field.isFocused()) {
            ModernUiRenderer.drawText(font, hint, rect.x + 5, rect.y + 5,
                    ModernUiRenderer.MUTED_TEXT, Math.max(10, rect.width - 10));
        }
    }

    private void idleTriggerScrollBars() {
        treeScrollBar.idle();
        eventScrollBar.idle();
        editorScrollBar.idle();
    }

    private GuiTextField ensureField(String key, int maxLength) {
        GuiTextField field = fields.get(key);
        if (field == null) {
            field = new GuiTextField(9400 + fields.size(), font, 0, 0, 20, FIELD_H);
            field.setEnableBackgroundDrawing(false);
            field.setMaxStringLength(maxLength);
            field.setCanLoseFocus(false);
            fields.put(key, field);
        }
        return field;
    }

    private void hideFieldsAndOptions() {
        for (GuiTextField field : fields.values()) field.setVisible(false);
        enabledToggle = backgroundToggle = sequencePicker = optionToggle = secondaryOptionToggle = importButton = null;
    }

    private String fieldAt(int mouseX, int mouseY) {
        for (Map.Entry<String, ModernMainLayout.Rect> entry : fieldBounds.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) return entry.getKey();
        }
        return null;
    }

    private void focusField(String key, int mouseX) {
        for (Map.Entry<String, GuiTextField> entry : fields.entrySet()) {
            entry.getValue().setFocused(entry.getKey().equals(key));
        }
        focusedField = key;
        GuiTextField field = fields.get(key);
        if (field != null) ModernUiRenderer.moveTextFieldCursorTo(field, mouseX);
    }

    private void clearFocus() {
        for (GuiTextField field : fields.values()) field.setFocused(false);
        focusedField = null;
    }

    private List<PlayerListTriggerSupport.RuleEntry> parsePlayerEntries(String value) {
        List<PlayerListTriggerSupport.RuleEntry> result = new ArrayList<>();
        for (String token : safe(value).split("[;；\\n]+")) {
            String text = token.trim();
            if (text.isEmpty()) continue;
            int colon = text.indexOf(':');
            if (colon < 0) colon = text.indexOf('：');
            String mode = colon > 0 ? text.substring(0, colon).trim() : PlayerListTriggerSupport.MODE_EXACT;
            String name = colon > 0 ? text.substring(colon + 1).trim() : text;
            result.add(new PlayerListTriggerSupport.RuleEntry(name,
                    mode.toLowerCase(Locale.ROOT).contains("contain") || mode.contains("gui.modern.path.trigger.u115")
                            ? PlayerListTriggerSupport.MODE_CONTAINS : PlayerListTriggerSupport.MODE_EXACT));
        }
        return PlayerListTriggerSupport.copyEntries(result);
    }

    private String playerEntriesText(List<PlayerListTriggerSupport.RuleEntry> entries) {
        List<String> values = new ArrayList<>();
        for (PlayerListTriggerSupport.RuleEntry entry : PlayerListTriggerSupport.copyEntries(entries)) {
            values.add(entry.mode + ":" + entry.name);
        }
        return String.join("; ", values);
    }

    private String latestGui() {
        List<GuiInspectionManager.CapturedGuiSnapshot> history = GuiInspectionManager.getHistory();
        if (history.isEmpty()) return "gui.modern.path.trigger.u116";
        GuiInspectionManager.CapturedGuiSnapshot snapshot = history.get(0);
        return safe(snapshot.getTitle()) + " | " + safe(snapshot.getScreenSimpleName()) + " | "
                + safe(snapshot.getScreenClassName());
    }

    private String latestPacket() {
        List<String> packets = PacketCaptureHandler.getRecentPacketTextsSnapshot();
        return packets.isEmpty() ? "gui.modern.path.trigger.u116" : packets.get(packets.size() - 1);
    }

    private String selectedType() {
        return selectedRule < 0 || selectedRule >= rules.size() ? LegacySequenceTriggerManager.TRIGGER_GUI_OPEN
                : safe(rules.get(selectedRule).triggerType).trim().toLowerCase(Locale.ROOT);
    }

    private String eventLabel(String type) {
        for (LegacyTriggerEventItem item : EVENTS) if (!item.header && item.type.equals(type)) return item.label;
        return safe(type);
    }

    private String eventHelp(String type) {
        for (LegacyTriggerEventItem item : EVENTS) if (!item.header && item.type.equals(type)) return item.help;
        return "gui.modern.path.trigger.u117";
    }

    private int eventCount() {
        int count = 0;
        for (LegacyTriggerEventItem item : EVENTS) if (!item.header) count++;
        return count;
    }

    private int countRules(String category) {
        int count = 0;
        for (LegacySequenceTriggerManager.RuleEditModel rule : rules) {
            if (normalizeCategory(rule.category).equalsIgnoreCase(category)) count++;
        }
        return count;
    }

    private int categoryIndex(String category) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equalsIgnoreCase(normalizeCategory(category))) return i;
        }
        return -1;
    }

    private String packetDirectionLabel() {
        return "inbound".equals(packetDirection) ? "gui.modern.path.trigger.u118"
                : "outbound".equals(packetDirection) ? "gui.modern.path.trigger.u119" : "gui.modern.path.trigger.u120";
    }

    private String entityTypeLabel() {
        return "player".equals(entityType) ? "gui.modern.path.trigger.u121" : "hostile".equals(entityType) ? "gui.modern.path.trigger.u122"
                : "passive".equals(entityType) ? "gui.modern.path.trigger.u123" : "gui.modern.path.trigger.u124";
    }

    private boolean isGuiType(String type) {
        return LegacySequenceTriggerManager.TRIGGER_GUI_OPEN.equals(type)
                || LegacySequenceTriggerManager.TRIGGER_GUI_CLOSE.equals(type);
    }

    private boolean isIntegerParam(String key) {
        return "idleMs".equals(key) || "intervalSeconds".equals(key)
                || "minFilledSlots".equals(key) || "minCount".equals(key);
    }

    private boolean isDecimalParam(String key) {
        return "hpThreshold".equals(key) || "foodThreshold".equals(key) || "minDamage".equals(key);
    }

    private int integerDefault(String key) {
        return "idleMs".equals(key) ? 1000 : "minFilledSlots".equals(key) ? 0 : 1;
    }

    private int integerMinimum(String key) {
        return "intervalSeconds".equals(key) ? 1 : 0;
    }

    private double decimalDefault(String key) {
        return "hpThreshold".equals(key) ? 6 : "foodThreshold".equals(key) ? 12 : 0;
    }

    private String displayRule(LegacySequenceTriggerManager.RuleEditModel rule) {
        String name = safe(rule == null ? "" : rule.name).trim();
        return name.isEmpty() ? "gui.modern.path.trigger.u125" : name;
    }

    private int statusColor() {
        return status.contains("gui.modern.path.trigger.u126") || status.contains("gui.modern.path.trigger.u127") ? ModernUiRenderer.WARNING
                : status.contains("gui.modern.path.trigger.u128") || status.contains("gui.modern.path.trigger.u129") ? ModernUiRenderer.SUCCESS
                : ModernUiRenderer.MUTED_TEXT;
    }

    private int labelWidth() {
        return Math.max(72, Math.min(108, editorViewport.width / 4));
    }

    private String normalizeCategory(String category) {
        String normalized = safe(category).trim();
        return normalized.isEmpty() ? LegacySequenceTriggerManager.CATEGORY_UNGROUPED : normalized;
    }

    private int parseExecutionCount(String text) {
        try {
            int value = Integer.parseInt(safe(text).trim());
            return value < -1 ? 1 : value;
        } catch (Exception ignored) {
            return 1;
        }
    }

    private int parseInt(String text, int fallback, int min) {
        try { return Math.max(min, Integer.parseInt(safe(text).trim())); }
        catch (Exception ignored) { return Math.max(min, fallback); }
    }

    private double parseDouble(String text, double fallback, double min) {
        try { return Math.max(min, Double.parseDouble(safe(text).trim())); }
        catch (Exception ignored) { return Math.max(min, fallback); }
    }

    private JsonObject copyJson(JsonObject source) {
        try { return source == null ? new JsonObject() : new JsonParser().parse(source.toString()).getAsJsonObject(); }
        catch (Exception ignored) { return new JsonObject(); }
    }

    private String stringParam(JsonObject params, String key) {
        try { return params.has(key) ? params.get(key).getAsString() : ""; }
        catch (Exception ignored) { return ""; }
    }

    private int intParam(JsonObject params, String key, int fallback) {
        try { return params.has(key) ? params.get(key).getAsInt() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private String numberParam(JsonObject params, String key, double fallback) {
        try {
            double value = params.has(key) ? params.get(key).getAsDouble() : fallback;
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        } catch (Exception ignored) {
            return String.valueOf(fallback);
        }
    }

    private boolean booleanParam(JsonObject params, String key, boolean fallback) {
        try { return params.has(key) ? params.get(key).getAsBoolean() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private void set(String key, String value) {
        GuiTextField field = fields.get(key);
        if (field != null) field.setText(safe(value));
    }

    private String text(String key) {
        GuiTextField field = fields.get(key);
        return field == null ? "" : safe(field.getText());
    }

    private boolean contains(ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        return rect != null && rect.contains(mouseX, mouseY);
    }

    private boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
