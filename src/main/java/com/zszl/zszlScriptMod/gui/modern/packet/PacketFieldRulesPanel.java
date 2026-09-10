package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.PacketFieldRuleManager;

import net.minecraft.client.gui.FontRenderer;

/** Field extraction rule list/editor with validation and save/cancel actions. */
final class PacketFieldRulesPanel extends PacketPanelBase {
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("packet_fieldrules");

    private static final String[] DIRECTIONS = { "both", "inbound", "outbound" };
    private static final String[] SOURCES = { "decoded", "hex", "channel", "class" };
    private static final String[] MODES = { "regex", "offset", "key", "length_prefixed" };
    private static final String[] TYPES = { "auto", "string", "number", "boolean", "hex" };
    private static final String[] SCOPES = { "global", "sequence", "local", "temp" };
    private final PacketWorkbenchState.FieldRules state = new PacketWorkbenchState.FieldRules();
    private final PacketTextField name = new PacketTextField(7201, 128), channel = new PacketTextField(7202, 128);
    private final PacketTextField pattern = new PacketTextField(7203, 32767), group = new PacketTextField(7204, 8);
    private final PacketTextField variable = new PacketTextField(7205, 128), defaultValue = new PacketTextField(7206, 1024);
    private final PacketTextField note = new PacketTextField(7207, 32767);
    private final PacketTextField category = new PacketTextField(7208, 128);
    private final PacketTextField search = new PacketTextField(7209, 128);
    private final List<String> categories = new ArrayList<String>();
    private String selectedCategory = "__all__";
    private final Set<String> collapsedCategories = new LinkedHashSet<String>();
    private final List<TreeHit> treeHits = new ArrayList<TreeHit>();
    private ModernMainLayout.Rect categoryBounds, ruleListBounds, categorySplitBounds, categoryEditorBounds;
    private boolean draggingCategorySplit;
    private double categoryRatio = 0.32D;
    private int selected = -1;
    private String direction = "both", source = "decoded", mode = "regex", type = "auto", scope = "global";
    private boolean enabled = true, writeDefault;
    private ModernMainLayout.Rect listBounds, editorBounds, searchBounds, addBounds, deleteBounds, reloadBounds, validateBounds, saveBounds, cancelBounds;
    private ModernMainLayout.Rect[] choiceBounds = new ModernMainLayout.Rect[7];
    private boolean saved;
    private String validation = "";
    private int listScroll;
    private int editorScroll;
    private int editorMaxScroll;
    private int lastMouseX, lastMouseY;
    private ModernMainLayout.Rect mobileViewport, mobileScrollbarBounds, mobileScrollbarThumbBounds;
    private int mobileScroll, mobileMaxScroll;
    private boolean draggingMobileScrollbar, draggingSplit, draggingListBar;
    private final ModernHoverScrollbar mobileScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();
    private final PacketDropdown directionDrop = new PacketDropdown(DIRECTIONS);
    private final PacketDropdown sourceDrop = new PacketDropdown(SOURCES);
    private final PacketDropdown modeDrop = new PacketDropdown(MODES);
    private final PacketDropdown typeDrop = new PacketDropdown(TYPES);
    private final PacketDropdown scopeDrop = new PacketDropdown(SCOPES);
    private double splitRatio = 0.32D;
    private ModernMainLayout.Rect splitBounds;

    private static final class TreeHit {
        final int ruleIndex;
        final String category;
        final boolean group;
        final ModernMainLayout.Rect bounds;

        TreeHit(int ruleIndex, String category, boolean group, ModernMainLayout.Rect bounds) {
            this.ruleIndex = ruleIndex;
            this.category = category;
            this.group = group;
            this.bounds = bounds;
        }
    }

    PacketFieldRulesPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktfield.u001"); }
    @Override protected void initializePanel() {
        splitRatio = MainUiLayoutManager.getModernSplitRatio("packet.field_rules.editor", splitRatio);
        categoryRatio = MainUiLayoutManager.getModernSplitRatio("packet.field_rules.category", categoryRatio);
        name.ensure(font); channel.ensure(font); pattern.ensure(font); group.ensure(font); variable.ensure(font); defaultValue.ensure(font); note.ensure(font); category.ensure(font); search.ensure(font);
        registerDropdown(directionDrop); registerDropdown(sourceDrop); registerDropdown(modeDrop);
        registerDropdown(typeDrop); registerDropdown(scopeDrop);
        if (!state.models().isEmpty()) select(0); else clearEditor();
        refreshCategories();
    }
    @Override public void updateScreen() { name.update(); channel.update(); pattern.update(); group.update(); variable.update(); defaultValue.update(); note.update(); category.update(); search.update(); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        lastMouseX = mx; lastMouseY = my;
        int x = area.x + 12, y = area.y + 42, footer = area.bottom() - 30;
        if (area.width < 560) {
            splitBounds = null;
            syncChoiceDropdowns();
            drawCompactBody(font, area, mx, my, x, y, footer);
            return;
        }
        int available = Math.max(2, area.width - 24);
        ModernSplitPane.Split split = ModernSplitPane.calculate(available, splitRatio, 170, 280, 140, 220);
        splitRatio = split.ratio;
        listBounds = new ModernMainLayout.Rect(x, y, split.firstWidth, Math.max(40, footer - y - 8));
        splitBounds = ModernSplitPane.verticalDividerBounds(listBounds.x, listBounds.width, 10, y, listBounds.height);
        editorBounds = new ModernMainLayout.Rect(listBounds.right() + 10, y, Math.max(1, split.secondWidth - 10), listBounds.height);
        ModernSplitPane.drawVerticalDivider(splitBounds, mx, my, draggingSplit);
        syncChoiceDropdowns();
        drawList(font, mx, my); drawEditor(font, mx, my);
        int bw = Math.max(58, (listBounds.width - 18) / 3);
        addBounds = null; deleteBounds = null; reloadBounds = null;
        int rightX = editorBounds.x, gap = 6, each = Math.max(58, (editorBounds.width - gap * 3) / 4);
        validateBounds = new ModernMainLayout.Rect(rightX, footer, each, 22); saveBounds = new ModernMainLayout.Rect(validateBounds.right() + gap, footer, each, 22); cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + gap, footer, Math.max(58, editorBounds.right() - saveBounds.right() - gap), 22);
        drawButton(font, validateBounds, "gui.modern.pktfield.u005", mx, my, selected >= 0, false); drawButton(font, saveBounds, "gui.modern.pktfield.u006", mx, my, true, true); drawButton(font, cancelBounds, "gui.modern.pktfield.u007", mx, my, true, false);
    }

    private void drawCompactBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my, int x, int top,
            int footer) {
        int width = Math.max(1, area.width - 24);
        int listHeight = 260;
        int editorHeight = 390;
        int gap = 8;
        int actionGap = 8;
        int contentHeight = listHeight + gap + editorHeight + actionGap + 22;
        mobileViewport = new ModernMainLayout.Rect(x, top, width, Math.max(1, footer - top - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        mobileMaxScroll = Math.max(0, contentHeight - mobileViewport.height);
        mobileScroll = Math.max(0, Math.min(mobileScroll, mobileMaxScroll));
        int contentY = mobileViewport.y - mobileScroll;
        listBounds = new ModernMainLayout.Rect(x, contentY, width, listHeight);
        categorySplitBounds = null;
        ruleListBounds = null;
        editorBounds = new ModernMainLayout.Rect(x, listBounds.bottom() + gap, width, editorHeight);

        ModernUiRenderer.beginClip(mobileViewport);
         drawList(font, mx, my);
        drawEditor(font, mx, my);
        int listButtonWidth = Math.max(1, (width - 12) / 3);
        int listFooter = listBounds.bottom() + 4;
        addBounds = null;
        deleteBounds = null;
        reloadBounds = null;

        int editorFooter = editorBounds.bottom() + 4;
        int editorButtonWidth = Math.max(1, (width - 12) / 3);
        validateBounds = new ModernMainLayout.Rect(x, editorFooter, editorButtonWidth, 22);
        saveBounds = new ModernMainLayout.Rect(validateBounds.right() + 6, editorFooter, editorButtonWidth, 22);
        cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + 6, editorFooter,
                Math.max(1, x + width - saveBounds.right() - 6), 22);
        drawButton(font, validateBounds, "gui.modern.pktfield.u005", mx, my, selected >= 0, false);
        drawButton(font, saveBounds, "gui.modern.pktfield.u006", mx, my, true, true);
        drawButton(font, cancelBounds, "gui.modern.pktfield.u007", mx, my, true, false);
        ModernUiRenderer.endClip();
        drawMobileScrollbar(mx, my);
    }

    private void drawList(FontRenderer font, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawFieldTree(font, mx, my);
    }

    @Override protected void drawNavigationOverlay(int x, int y) { navigationActions.drawOverlay(x, y); }
    @Override protected boolean navigationOverlayOpen() { return navigationActions.isOpen(); }

    private void configureNavigationActions() {
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("delete", "gui.modern.nav.delete", true, true, () -> selected >= 0, this::navigationDelete);
        navigationActions.action("reload", "gui.modern.nav.reload", false, false, () -> true, this::navigationReload);
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> promptCategory(false));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> !"__all__".equals(selectedCategory), () -> promptCategory(true));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !"__all__".equals(selectedCategory), () -> { if (PacketFieldRuleManager.deleteCategory(selectedCategory)) { selectedCategory = "__all__"; refreshCategories(); } });
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> selected >= 0, () -> navigationActions.prompt("gui.modern.nav.rename", name.text(), value -> { name.setText(value); sync(); }));
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> selected >= 0, () -> { sync(); PacketFieldRuleManager.RuleEditModel copy = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(state.models().get(selected)), PacketFieldRuleManager.RuleEditModel.class); copy.name += " (copy)"; state.models().add(selected + 1, copy); select(selected + 1); });
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> selected >= 0, () -> navigationActions.choose(
                "移动到分类", categories, state.models().get(selected).category,
                value -> { sync(); state.models().get(selected).category = value; selectedCategory = value; select(selected); }));
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> selected >= 0, () -> { enabled = !enabled; sync(); });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedCategories.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (TreeHit hit : treeHits) if (hit.group) collapsedCategories.add(hit.category); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationAdd() { sync(); state.add(); if (!"__all__".equals(selectedCategory)) state.models().get(state.models().size() - 1).category = selectedCategory; select(state.models().size() - 1); return true; }

    private boolean navigationDelete() { state.remove(selected); selected = Math.min(selected, state.models().size() - 1); if (selected >= 0) select(selected); else clearEditor(); return true; }

    private boolean navigationReload() { state.reload(); selected = state.models().isEmpty() ? -1 : 0; if (selected >= 0) select(selected); else clearEditor(); validation = ""; return true; }

    private void drawFieldTree(FontRenderer font, int mx, int my) {
        configureNavigationActions();
        navigationActions.begin(font, listBounds);
        searchBounds = new ModernMainLayout.Rect(listBounds.x + 8, listBounds.y + 30,
                Math.max(1, listBounds.width - 16), 20);
        search.setBounds(new ModernMainLayout.Rect(searchBounds.x + 22, searchBounds.y + 4,
                Math.max(1, searchBounds.width - 28), 12));
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, search.focused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (search.text().trim().isEmpty() && !search.focused()) {
            text(font, "gui.modern.pktfield.u028", searchBounds.x + 22, searchBounds.y + 5,
                    ModernUiRenderer.MUTED_TEXT, searchBounds.width - 28);
        }
        search.draw();
        ModernMainLayout.Rect tree = new ModernMainLayout.Rect(listBounds.x + 6, searchBounds.bottom() + 8,
                Math.max(1, listBounds.width - 12), Math.max(1, navigationActions.contentBottom() - searchBounds.bottom() - 8));
        List<String> groups = fieldTreeCategories();
        String query = search.text().trim().toLowerCase(java.util.Locale.ROOT);
        treeHits.clear();
        ModernUiRenderer.beginClip(tree);
        int y = tree.y - listScroll;
        for (String groupName : groups) {
            List<Integer> indices = matchingFieldIndices(groupName, query);
            if (indices.isEmpty() && !query.isEmpty() && !groupName.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            ModernMainLayout.Rect groupRow = ModernTreeGuide.groupRow(tree, y);
            boolean collapsed = collapsedCategories.contains(groupName) && query.isEmpty();
            drawTreeGroup(font, groupRow, groupName, indices.size(), collapsed, mx, my);
            treeHits.add(new TreeHit(-1, groupName, true, groupRow));
            y = ModernTreeGuide.nextY(y, ModernTreeGuide.GROUP_HEIGHT);
            if (!collapsed) {
                for (Integer index : indices) {
                    ModernMainLayout.Rect item = ModernTreeGuide.itemRow(tree, y);
                    ModernTreeGuide.drawChild(tree.x, 0, groupRow, item);
                    drawTreeItem(font, item, state.models().get(index.intValue()), index.intValue(), mx, my);
                    treeHits.add(new TreeHit(index.intValue(), groupName, false, item));
                    y = ModernTreeGuide.nextY(y, ModernTreeGuide.ITEM_HEIGHT);
                }
            }
        }
        if (treeHits.isEmpty()) text(font, "gui.modern.pktfield.u008", tree.x + 8, tree.y + 18,
                ModernUiRenderer.MUTED_TEXT, tree.width - 16);
        ModernUiRenderer.endClip();
        int listMax = Math.max(0, y + listScroll - tree.bottom());
        listScroll = Math.max(0, Math.min(listScroll, listMax));
        if (listMax > 0) {
            listScrollbar.draw(tree, listScroll, listMax, tree.height, tree.height + listMax, mx, my,
                    value -> listScroll = value);
        } else {
            listScrollbar.idle();
        }
        navigationActions.draw(mx, my);
    }

    private void drawTreeGroup(FontRenderer font, ModernMainLayout.Rect row, String name, int count,
            boolean collapsed, int mx, int my) {
        boolean selectedGroup = name.equals(selectedCategory);
        boolean hovered = row.contains(mx, my);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selectedGroup ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selectedGroup ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(row.x + 8, row.y + 7, collapsed,
                selectedGroup ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
        text(font, "__all__".equals(name) ? "gui.modern.pktfield.u029" : name, row.x + 21, row.y + 7,
                ModernUiRenderer.TEXT, Math.max(30, row.width - 48));
        text(font, String.valueOf(count), row.right() - 25, row.y + 7, ModernUiRenderer.MUTED_TEXT, 18);
    }

    private void drawTreeItem(FontRenderer font, ModernMainLayout.Rect row,
            PacketFieldRuleManager.RuleEditModel model, int index, int mx, int my) {
        boolean selectedItem = index == selected;
        boolean hovered = row.contains(mx, my);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selectedItem ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                selectedItem ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(row.x + 9, row.y + 13,
                model.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        text(font, safe(model.name), row.x + 20, row.y + 5, ModernUiRenderer.TEXT, row.width - 30);
        text(font, safe(model.variableName), row.x + 20, row.y + 18, ModernUiRenderer.MUTED_TEXT, row.width - 30);
    }

    private boolean selectTreeHit(int x, int y) {
        if (!navigationActions.inTree(x, y)) return false;
        for (TreeHit hit : treeHits) {
            if (!hit.bounds.contains(x, y)) continue;
            if (hit.group) {
                selectedCategory = hit.category;
                if (!collapsedCategories.add(hit.category)) collapsedCategories.remove(hit.category);
                listScroll = 0;
            } else {
                sync();
                selectedCategory = hit.category;
                select(hit.ruleIndex);
            }
            return true;
        }
        return false;
    }

    private int treeContentHeight() {
        if (treeHits.isEmpty()) return 0;
        int bottom = 0;
        for (TreeHit hit : treeHits) bottom = Math.max(bottom, hit.bounds.bottom());
        return Math.max(0, bottom + listScroll - (listBounds.y + 58));
    }

    private List<String> fieldTreeCategories() {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        result.add("__all__");
        for (PacketFieldRuleManager.RuleEditModel model : state.models()) {
            String value = safe(model.category).trim();
            if (!value.isEmpty()) result.add(value);
        }
        return new ArrayList<String>(result);
    }

    private List<Integer> matchingFieldIndices(String categoryName, String query) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < state.models().size(); i++) {
            PacketFieldRuleManager.RuleEditModel model = state.models().get(i);
            if (!"__all__".equals(categoryName) && !safe(model.category).trim().equals(categoryName)) continue;
            String searchable = (safe(model.name) + " " + safe(model.variableName) + " " + safe(model.channel)
                    + " " + safe(model.note) + " " + safe(model.category)).toLowerCase(java.util.Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) result.add(Integer.valueOf(i));
        }
        return result;
    }

    private void drawCategoryTree(FontRenderer font, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(categoryBounds.x, categoryBounds.y, categoryBounds.width, categoryBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, "分组", categoryBounds.x + 9, categoryBounds.y + 8, ModernUiRenderer.TEXT, categoryBounds.width - 18);
        int y = categoryBounds.y + 29;
        ModernMainLayout.Rect all = new ModernMainLayout.Rect(categoryBounds.x + 6, y, categoryBounds.width - 12, 20);
        ModernUiRenderer.drawSubtlePanel(all.x, all.y, all.width, all.height, 3,
                "__all__".equals(selectedCategory) ? ModernUiRenderer.SELECTED_SURFACE : all.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                "__all__".equals(selectedCategory) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        centeredLabel(font, "全部", all, ModernUiRenderer.TEXT);
        y += 24;
        for (int i = 0; i < categories.size() && y + 20 <= categoryBounds.bottom(); i++, y += 24) {
            String value = categories.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(categoryBounds.x + 6, y, categoryBounds.width - 12, 20);
            boolean selected = value.equals(selectedCategory);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : row.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawChevron(row.x + 9, row.y + 6, true, selected ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
            text(font, value, row.x + 20, row.y + 6, ModernUiRenderer.TEXT, row.width - 28);
        }
    }

    private List<Integer> visibleRuleIndices() {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < state.models().size(); i++) {
            if ("__all__".equals(selectedCategory) || selectedCategory.equals(safe(state.models().get(i).category))) result.add(i);
        }
        return result;
    }

    private void refreshCategories() {
        categories.clear();
        categories.addAll(PacketFieldRuleManager.getCategories());
    }

    private void drawEditor(FontRenderer font, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, selected < 0 ? "gui.modern.pktfield.u009" : "gui.modern.pktfield.u010", editorBounds.x + 10, editorBounds.y + 9, ModernUiRenderer.TEXT, editorBounds.width - 20);
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(editorBounds.x + 1, editorBounds.y + 30,
                Math.max(1, editorBounds.width - 2), Math.max(1, editorBounds.height - 62));
        editorMaxScroll = Math.max(0, 12 * 26 - viewport.height);
        editorScroll = Math.max(0, Math.min(editorScroll, editorMaxScroll));
        int pad = 10, labelW = 86, flagGap = 12;
        boolean stackFlags = editorBounds.width < 560;
        int flagW = stackFlags ? Math.max(1, editorBounds.width - pad * 2)
                : Math.min(200, Math.max(132, editorBounds.width / 4));
        int flagX = stackFlags ? editorBounds.x + pad : editorBounds.right() - pad - flagW;
        int fieldWidth = Math.max(80, (stackFlags ? editorBounds.right() - pad : flagX - flagGap)
                - (editorBounds.x + pad + labelW));
        ModernUiRenderer.beginClip(viewport);
        int y = viewport.y - editorScroll;
        y = fieldRow(font, "gui.modern.pktfield.u011", name, editorBounds.x + pad, y, fieldWidth, safeModel().name); y = fieldRow(font, "gui.modern.pktfield.u012", channel, editorBounds.x + pad, y, fieldWidth, safeModel().channel);
        categoryEditorBounds = new ModernMainLayout.Rect(editorBounds.x + pad + 86, y, fieldWidth, 20);
        text(font, "分类", editorBounds.x + pad, y + 6, ModernUiRenderer.MUTED_TEXT, 82);
        drawChoice(font, categoryEditorBounds, safeModel().category, lastMouseX, lastMouseY, false);
        y += 26;
        y = choiceRow(font, "gui.modern.pktfield.u013", direction, 0, editorBounds.x + pad, y, fieldWidth); y = choiceRow(font, "gui.modern.pktfield.u014", source, 1, editorBounds.x + pad, y, fieldWidth);
        y = choiceRow(font, "gui.modern.pktfield.u015", mode, 2, editorBounds.x + pad, y, fieldWidth); y = choiceRow(font, "gui.modern.pktfield.u016", type, 3, editorBounds.x + pad, y, fieldWidth);
        y = choiceRow(font, "gui.modern.pktfield.u017", scope, 4, editorBounds.x + pad, y, fieldWidth); y = fieldRow(font, "Pattern/Key", pattern, editorBounds.x + pad, y, fieldWidth, safeModel().pattern);
        y = fieldRow(font, "Group", group, editorBounds.x + pad, y, fieldWidth, String.valueOf(safeModel().group)); y = fieldRow(font, "gui.modern.pktfield.u018", variable, editorBounds.x + pad, y, fieldWidth, safeModel().variableName);
        y = fieldRow(font, "gui.modern.pktfield.u019", defaultValue, editorBounds.x + pad, y, fieldWidth, safeModel().defaultValue); fieldRow(font, "gui.modern.pktfield.u020", note, editorBounds.x + pad, y, fieldWidth, safeModel().note);
        ModernUiRenderer.endClip();
        ModernMainLayout.Rect enabledRect;
        ModernMainLayout.Rect defaultRect;
        if (stackFlags) {
            enabledRect = new ModernMainLayout.Rect(flagX, editorBounds.bottom() - 82, flagW, 22);
            defaultRect = new ModernMainLayout.Rect(flagX, editorBounds.bottom() - 54, flagW, 22);
        } else {
            enabledRect = new ModernMainLayout.Rect(flagX, editorBounds.y + 34, flagW, 22);
            defaultRect = new ModernMainLayout.Rect(flagX, editorBounds.y + 62, flagW, 22);
        }
        choiceBounds[5] = enabledRect; drawChoice(font, enabledRect, tr("gui.modern.pktfield.fmt.enabled", tr(enabled ? "gui.modern.pktfield.u021" : "gui.modern.pktfield.u022")), mx, my, enabled);
        choiceBounds[6] = defaultRect; drawChoice(font, defaultRect, tr("gui.modern.pktfield.fmt.default", tr(writeDefault ? "gui.modern.pktfield.u021" : "gui.modern.pktfield.u022")), mx, my, writeDefault);
        if (!validation.isEmpty()) text(font, validation, editorBounds.x + 10, editorBounds.bottom() - 18, ModernUiRenderer.WARNING, editorBounds.width - 20);
    }

    private int fieldRow(FontRenderer font, String label, PacketTextField field, int x, int y, int width, String value) { text(font, label, x, y + 6, ModernUiRenderer.MUTED_TEXT, 82); this.field(field, new ModernMainLayout.Rect(x + 86, y, width, 20), value); return y + 26; }
    private int choiceRow(FontRenderer font, String label, String value, int index, int x, int y, int width) {
        text(font, label, x, y + 6, ModernUiRenderer.MUTED_TEXT, 82);
        ModernMainLayout.Rect r = new ModernMainLayout.Rect(x + 86, y, width, 20);
        choiceBounds[index] = r;
        PacketDropdown drop = dropdownFor(index);
        if (drop != null) {
            drop.setValue(value);
            drop.drawButton(font, r, lastMouseX, lastMouseY);
        } else {
            drawChoice(font, r, value, lastMouseX, lastMouseY, false);
        }
        return y + 26;
    }
    private PacketDropdown dropdownFor(int index) {
        if (index == 0) return directionDrop;
        if (index == 1) return sourceDrop;
        if (index == 2) return modeDrop;
        if (index == 3) return typeDrop;
        if (index == 4) return scopeDrop;
        return null;
    }
    private void syncChoiceDropdowns() {
        direction = directionDrop.value();
        source = sourceDrop.value();
        mode = modeDrop.value();
        type = typeDrop.value();
        scope = scopeDrop.value();
    }
    private void drawChoice(FontRenderer font, ModernMainLayout.Rect r, String value, int mx, int my, boolean selected) { boolean hover = r.contains(mx, my); ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3, selected ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); centeredLabel(font, value, r, selected ? ModernUiRenderer.SELECTED_TEXT : ModernUiRenderer.TEXT); }

    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (navigationActions.mouseClicked(x, y, button)) return true;
        if (button == 1 && navigationActions.inTree(x, y)) {
            sync();
            boolean groupHit = false;
            for (TreeHit hit : treeHits) if (hit.bounds.contains(x, y)) {
                selectedCategory = hit.category;
                groupHit = hit.group;
                if (!hit.group) select(hit.ruleIndex);
                break;
            }
            PacketTextField.clearActiveFocus();
            if (groupHit) navigationActions.context(x, y, "add", "category_add", "category_rename", "category_delete", "expand", "collapse");
            else navigationActions.context(x, y, "add", "copy", "rename", "move", "toggle", "delete", "reload");
            return true;
        }


        if (button != 0) return true;
        if (categorySplitBounds != null && categorySplitBounds.contains(x, y)) { draggingCategorySplit = true; return true; }
        if (splitBounds != null && splitBounds.contains(x, y)) { draggingSplit = true; return true; }
        if (listScrollbar.beginDrag(x, y)) { draggingListBar = true; return true; }
        if (mobileScrollbar.beginDrag(x, y)) {
            draggingMobileScrollbar = true;
            return true;
        }
        if (searchBounds != null && searchBounds.contains(x, y)) {
            search.click(x, y, button);
            return true;
        }
        if (listBounds != null && listBounds.contains(x, y)) {
            selectTreeHit(x, y);
            return true;
        }
        if (categoryEditorBounds != null && categoryEditorBounds.contains(x, y)) {
            PacketTextField.clearActiveFocus();
            navigationActions.choose("选择分组", categories, category.text(), value -> { category.setText(value); sync(); });
            return true;
        }
        if (fieldClick(search, x, y, button) || fieldClick(name, x, y, button) || fieldClick(channel, x, y, button) || fieldClick(pattern, x, y, button) || fieldClick(group, x, y, button) || fieldClick(variable, x, y, button) || fieldClick(defaultValue, x, y, button) || fieldClick(note, x, y, button)) return true;
        if (hit(choiceBounds[5], x, y)) { enabled = !enabled; return true; } if (hit(choiceBounds[6], x, y)) { writeDefault = !writeDefault; return true; }
        if (hit(addBounds, x, y)) { return navigationAdd(); }
        if (hit(deleteBounds, x, y) && selected >= 0) { return navigationDelete(); }
        if (hit(reloadBounds, x, y)) { return navigationReload(); }
        if (hit(validateBounds, x, y)) { sync(); validation = validate(); return true; }
        if (hit(saveBounds, x, y)) { if (saveDraft()) owner.back(); return true; }
        if (hit(cancelBounds, x, y)) { owner.requestBack(); return true; }
        return true;
    }

    private void openFieldContextMenu(int x, int y) {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("创建分组", () -> promptCategory(false)));
        if (!"__all__".equals(selectedCategory)) {
            items.add(new PacketContextMenu.Item("重命名分组", () -> promptCategory(true)));
            items.add(new PacketContextMenu.Item("删除分组", () -> {
                if (PacketFieldRuleManager.deleteCategory(selectedCategory)) { selectedCategory = "__all__"; refreshCategories(); }
            }));
        }
        items.add(new PacketContextMenu.Item("gui.modern.pktfield.u002", () -> { sync(); state.add(); if (!"__all__".equals(selectedCategory)) state.models().get(state.models().size() - 1).category = selectedCategory; select(state.models().size() - 1); }));
        if (selected >= 0 && !"__all__".equals(selectedCategory)) {
            items.add(new PacketContextMenu.Item("移动到分类", () -> navigationActions.choose(
                    "移动到分类", categories, state.models().get(selected).category,
                    value -> { sync(); state.models().get(selected).category = value; selectedCategory = value; select(selected); })));
        }
        items.add(new PacketContextMenu.Item("gui.modern.pktfield.u003", () -> {
            if (selected >= 0) { state.remove(selected); selected = Math.min(selected, state.models().size() - 1); if (selected >= 0) select(selected); else clearEditor(); }
        }));
        openContextMenu(x, y, items);
    }

    private void promptCategory(boolean rename) {
        final String old = selectedCategory;
        owner.panels().push(new PacketModalPanel(owner, rename ? "重命名分组" : "创建分组", "分组名称", rename ? old : "", true, value -> {
            String next = value == null ? "" : value.trim();
            boolean ok = rename ? PacketFieldRuleManager.renameCategory(old, next) : PacketFieldRuleManager.addCategory(next);
            if (ok) { selectedCategory = next; refreshCategories(); }
        }));
    }

    @Override protected List<PacketContextMenu.Item> moreItems() {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("gui.modern.pktfield.u002", () -> { sync(); state.add(); if (!"__all__".equals(selectedCategory)) state.models().get(state.models().size() - 1).category = selectedCategory; select(state.models().size() - 1); }));
        items.add(new PacketContextMenu.Item("gui.modern.pktfield.u005", () -> { sync(); validation = validate(); }));
        items.add(new PacketContextMenu.Item("gui.modern.pktfield.u006", () -> saveDraft()));
        return items;
    }

    private void select(int index) { selected = index; PacketFieldRuleManager.RuleEditModel m = state.models().get(index); name.setText(m.name); category.setText(m.category); channel.setText(m.channel); pattern.setText(m.pattern); group.setText(String.valueOf(m.group)); variable.setText(m.variableName); defaultValue.setText(m.defaultValue); note.setText(m.note); direction = safe(m.direction, "both"); source = safe(m.source, "decoded"); mode = safe(m.extractMode, "regex"); type = safe(m.valueType, "auto"); scope = safe(m.scope, "global"); directionDrop.setValue(direction); sourceDrop.setValue(source); modeDrop.setValue(mode); typeDrop.setValue(type); scopeDrop.setValue(scope); enabled = m.enabled; writeDefault = m.writeDefaultOnFailure; validation = ""; }
    private void clearEditor() { name.setText(""); category.setText(""); channel.setText(""); pattern.setText(""); group.setText("1"); variable.setText(""); defaultValue.setText(""); note.setText(""); direction = "both"; source = "decoded"; mode = "regex"; type = "auto"; scope = "global"; directionDrop.setValue(direction); sourceDrop.setValue(source); modeDrop.setValue(mode); typeDrop.setValue(type); scopeDrop.setValue(scope); enabled = true; writeDefault = false; }
    private void sync() { if (selected < 0 || selected >= state.models().size()) return; PacketFieldRuleManager.RuleEditModel m = state.models().get(selected); m.name = name.text().trim(); m.category = category.text().trim(); m.channel = channel.text().trim(); m.pattern = pattern.text(); m.group = parseInt(group.text(), 1, 1, 999); m.variableName = variable.text().trim(); m.defaultValue = defaultValue.text(); m.note = note.text(); m.direction = direction; m.source = source; m.extractMode = mode; m.valueType = type; m.scope = scope; m.enabled = enabled; m.writeDefaultOnFailure = writeDefault; }

    private boolean saveDraft() {
        sync();
        validation = validate();
        if (!validation.isEmpty()) {
            owner.status(validation);
            return false;
        }
        state.save();
        saved = true;
        owner.status("gui.modern.pktfield.u023");
        return true;
    }

    @Override public void save() { saveDraft(); }

    @Override public boolean isDirty() {
        sync();
        return state.isDirty();
    }
    private String validate() {
        if (state.models().isEmpty()) return "";
        Set<String> names = new HashSet<>();
        for (PacketFieldRuleManager.RuleEditModel item : state.models()) {
            if (item == null) return "gui.modern.pktfield.u024";
            String ruleName = safe(item.name).trim();
            String variableName = safe(item.variableName).trim();
            String rulePattern = safe(item.pattern).trim();
            if (ruleName.isEmpty() || variableName.isEmpty()) return "gui.modern.pktfield.u025";
            if (rulePattern.isEmpty()) return "gui.modern.pktfield.u026";
            if (item.group <= 0) return "gui.modern.pktfield.u027";
            if ("regex".equalsIgnoreCase(item.extractMode)) {
                try { Pattern.compile(rulePattern); }
                catch (PatternSyntaxException e) { return tr("gui.modern.pktfield.fmt.regex", e.getDescription()); }
            }
            String key = ruleName.toLowerCase(java.util.Locale.ROOT);
            if (!names.add(key)) return tr("gui.modern.pktfield.fmt.dup", ruleName);
        }
        return "";
    }
    private PacketFieldRuleManager.RuleEditModel safeModel() { if (selected < 0 || selected >= state.models().size()) return new PacketFieldRuleManager.RuleEditModel(); return state.models().get(selected); }
    @Override public boolean keyTyped(char c, int code) {
        if (navigationActions.keyTyped(c, code)) return true; return fieldKey(search, c, code) || fieldKey(name, c, code) || fieldKey(channel, c, code) || fieldKey(pattern, c, code) || fieldKey(group, c, code) || fieldKey(variable, c, code) || fieldKey(defaultValue, c, code) || fieldKey(note, c, code) || code == Keyboard.KEY_RETURN; }
    @Override public void discardDraft() {
        navigationActions.close(); if (!saved) state.cancel(); if (!state.models().isEmpty()) select(0); else clearEditor(); validation = ""; PacketTextField.clearActiveFocus(); draggingMobileScrollbar = false; mobileScroll = 0; }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) {
        if (navigationActions.wheel(wheel)) return true;
        if (wheel == 0) return false;
        if (mobileViewport != null && mobileViewport.contains(lastMouseX, lastMouseY)) {
            int before = mobileScroll;
            mobileScroll = Math.max(0, Math.min(mobileMaxScroll, mobileScroll + (wheel > 0 ? -30 : 30)));
            return before != mobileScroll;
        }
        if (listBounds != null && listBounds.contains(lastMouseX, lastMouseY)) {
            int treeHeight = navigationActions.treeHeight();
            listScroll = Math.max(0, Math.min(Math.max(0, treeContentHeight() - treeHeight),
                    listScroll + (wheel > 0 ? -30 : 30)));
            return true;
        }
        if (editorBounds != null && editorBounds.contains(lastMouseX, lastMouseY)) {
            editorScroll = Math.max(0, Math.min(editorMaxScroll, editorScroll + (wheel > 0 ? -24 : 24)));
            return true;
        }
        return false;
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (navigationActions.isOpen()) return true;
        if (draggingCategorySplit && button == 0) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(Math.max(2, listBounds.width - 8),
                    x - listBounds.x, 100, 150, 82, 108);
            categoryRatio = split.ratio;
            return true;
        }
        if (draggingSplit && button == 0) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(Math.max(2, area.width - 24),
                    x - (area.x + 12), 170, 280, 140, 220);
            splitRatio = split.ratio;
            return true;
        }
        if (draggingListBar && button == 0) { listScrollbar.applyDrag(x, y); return true; }
        if (draggingMobileScrollbar && button == 0) { mobileScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        if (draggingCategorySplit) { MainUiLayoutManager.setModernSplitRatio("packet.field_rules.category", categoryRatio); draggingCategorySplit = false; return true; }
        if (draggingSplit) { MainUiLayoutManager.setModernSplitRatio("packet.field_rules.editor", splitRatio); draggingSplit = false; return true; }
        if (draggingListBar) { draggingListBar = false; listScrollbar.endDrag(); return true; }
        if (draggingMobileScrollbar) { draggingMobileScrollbar = false; mobileScrollbar.endDrag(); return true; }
        return false;
    }
    private void drawMobileScrollbar(int mouseX, int mouseY) {
        if (mobileViewport == null || mobileMaxScroll <= 0) {
            mobileScrollbar.idle();
            mobileScrollbarBounds = null;
            mobileScrollbarThumbBounds = null;
            return;
        }
        mobileScrollbar.draw(mobileViewport, mobileScroll, mobileMaxScroll, mobileViewport.height,
                mobileViewport.height + mobileMaxScroll, mouseX, mouseY, value -> mobileScroll = value);
        mobileScrollbarBounds = new ModernMainLayout.Rect(mobileViewport.right() - 14, mobileViewport.y, 14,
                mobileViewport.height);
        mobileScrollbarThumbBounds = mobileScrollbarBounds;
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String safe(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }
}
