package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketInterceptConfig;
import com.zszl.zszlScriptMod.utils.PacketInterceptManager;

import net.minecraft.client.gui.FontRenderer;

/** Inbound intercept-rule editor. */
final class PacketInterceptRulesPanel extends PacketPanelBase {
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("packet_interceptrules");

    private final PacketWorkbenchState.InterceptRules state = new PacketWorkbenchState.InterceptRules();
    private final PacketTextField name = new PacketTextField(7301, 128), filter = new PacketTextField(7302, 256);
    private final PacketTextField channel = new PacketTextField(7303, 128), match = new PacketTextField(7304, 32767);
    private final PacketTextField replace = new PacketTextField(7305, 32767);
    private final PacketTextField search = new PacketTextField(7306, 128);
    private int selected = -1;
    private boolean editingEnabled = true, regex, replaceAll = true, saved;
    private ModernMainLayout.Rect listBounds, editorBounds, enabledBounds, ruleEnabledBounds, regexBounds, allBounds, addBounds, deleteBounds, reloadBounds, validateBounds, saveBounds, cancelBounds;
    private String validation = "";
    private String selectedCategory = "__all__";
    private final Set<String> collapsedCategories = new LinkedHashSet<String>();
    private final List<TreeHit> treeHits = new ArrayList<TreeHit>();
    private int listScroll;
    private int editorScroll;
    private int editorMaxScroll;
    private int lastMouseX, lastMouseY;
    private ModernMainLayout.Rect mobileViewport, mobileScrollbarBounds, mobileScrollbarThumbBounds;
    private int mobileScroll, mobileMaxScroll;
    private boolean draggingMobileScrollbar, draggingSplit, draggingListBar;
    private final ModernHoverScrollbar mobileScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();
    private double splitRatio = 0.34D;
    private ModernMainLayout.Rect splitBounds;
    private ModernMainLayout.Rect searchBounds;

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

    PacketInterceptRulesPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktint.u001"); }
    @Override protected void initializePanel() {
        splitRatio = MainUiLayoutManager.getModernSplitRatio("packet.intercept_rules.editor", splitRatio);
        name.ensure(font); filter.ensure(font); channel.ensure(font); match.ensure(font); replace.ensure(font); search.ensure(font);
        if (!state.rules().isEmpty()) select(0);
    }
    @Override public void updateScreen() { name.update(); filter.update(); channel.update(); match.update(); replace.update(); search.update(); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        int footer = area.bottom() - 30, x = area.x + 12, y = area.y + 42;
        if (area.width < 560) {
            splitBounds = null;
            drawCompactBody(font, area, mx, my, x, y, footer);
            return;
        }
        int available = Math.max(2, area.width - 24);
        ModernSplitPane.Split split = ModernSplitPane.calculate(available, splitRatio, 180, 280, 150, 220);
        splitRatio = split.ratio;
        int listWidth = split.firstWidth;
        listBounds = new ModernMainLayout.Rect(x, y, listWidth, Math.max(40, footer - y - 8));
        splitBounds = ModernSplitPane.verticalDividerBounds(listBounds.x, listBounds.width, 10, y, listBounds.height);
        editorBounds = new ModernMainLayout.Rect(listBounds.right() + 10, y, Math.max(1, split.secondWidth - 10), listBounds.height);
        ModernSplitPane.drawVerticalDivider(splitBounds, mx, my, draggingSplit);
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawInterceptTree(font, mx, my);
        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, selected < 0 ? "gui.modern.pktint.u002" : "gui.modern.pktint.u003", editorBounds.x + 10, editorBounds.y + 9, ModernUiRenderer.TEXT, editorBounds.width - 20);
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(editorBounds.x + 1, editorBounds.y + 30,
                Math.max(1, editorBounds.width - 2), Math.max(1, editorBounds.height - 62));
        editorMaxScroll = Math.max(0, 5 * 28 + 260 - viewport.height);
        editorScroll = Math.max(0, Math.min(editorScroll, editorMaxScroll));
        ModernUiRenderer.beginClip(viewport);
        int y0 = viewport.y - editorScroll + 4, fw = Math.max(1, editorBounds.width - 118);
        y0 = row(font, "gui.modern.pktint.u004", name, y0, fw, safeRule().name); y0 = row(font, "gui.modern.pktint.u005", filter, y0, fw, safeRule().packetFilter); y0 = row(font, "gui.modern.pktint.u006", channel, y0, fw, safeRule().channel); y0 = row(font, "gui.modern.pktint.u007", match, y0, fw, safeRule().matchHex); row(font, "gui.modern.pktint.u008", replace, y0, fw, safeRule().replaceHex);
        ModernUiRenderer.endClip();
        enabledBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.y + 174, editorBounds.width - 20, 22);
        ruleEnabledBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.y + 202, editorBounds.width / 2 - 14, 22);
        regexBounds = new ModernMainLayout.Rect(ruleEnabledBounds.right() + 8, ruleEnabledBounds.y, editorBounds.width - editorBounds.width / 2 - 18, 22);
        allBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.y + 230, editorBounds.width - 20, 22);
        drawFlag(font, enabledBounds, tr("gui.modern.pktint.fmt.intercept", tr(state.enabled() ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, state.enabled());
        drawFlag(font, ruleEnabledBounds, tr("gui.modern.pktint.fmt.rule_on", tr(editingEnabled ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, editingEnabled);
        drawFlag(font, regexBounds, tr("gui.modern.pktint.fmt.regex", tr(regex ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, regex);
        drawFlag(font, allBounds, tr("gui.modern.pktint.fmt.replace_all", tr(replaceAll ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, replaceAll);
        int bw = Math.max(58, (listWidth - 12) / 3); addBounds = null; deleteBounds = null; reloadBounds = null;
        text(font, validation, editorBounds.x + 10, editorBounds.bottom() - 18, ModernUiRenderer.WARNING, Math.max(1, editorBounds.width - 20));
        int gap = 6, third = Math.max(58, (editorBounds.width - gap * 2) / 3);
        validateBounds = new ModernMainLayout.Rect(editorBounds.x, footer, third, 22);
        saveBounds = new ModernMainLayout.Rect(validateBounds.right() + gap, footer, third, 22);
        cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + gap, footer, Math.max(1, editorBounds.right() - saveBounds.right() - gap), 22);
        drawButton(font, validateBounds, "gui.modern.pktint.u014", mx, my); drawButton(font, saveBounds, "gui.modern.pktint.u015", mx, my, true, true); drawButton(font, cancelBounds, "gui.modern.pktint.u016", mx, my);
    }

    private void drawCategoryNavigation(FontRenderer font, int mx, int my) {
        int y = listBounds.y + 32;
        ModernUiRenderer.drawSubtlePanel(listBounds.x + 6, y, 48, 20, 3,
                "__all__".equals(selectedCategory) ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SURFACE,
                "__all__".equals(selectedCategory) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        centeredLabel(font, "全部", new ModernMainLayout.Rect(listBounds.x + 6, y, 48, 20), ModernUiRenderer.TEXT);
        int x = listBounds.x + 58;
        for (String value : state.categories()) {
            int width = Math.min(110, Math.max(48, font.getStringWidth(value) + 18));
            ModernMainLayout.Rect r = new ModernMainLayout.Rect(x, y, width, 20);
            ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3,
                    value.equals(selectedCategory) ? ModernUiRenderer.SELECTED_SURFACE : r.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    value.equals(selectedCategory) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            centeredLabel(font, value, r, ModernUiRenderer.TEXT);
            x += width + 4;
            if (x >= listBounds.right() - 20) break;
        }
    }

    private List<Integer> visibleRuleIndices() {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < state.rules().size(); i++) {
            if ("__all__".equals(selectedCategory) || selectedCategory.equals(state.rules().get(i).category)) result.add(i);
        }
        return result;
    }

    private void drawCompactBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my, int x, int top,
            int footer) {
        int width = Math.max(1, area.width - 24);
        int listHeight = 260;
        int editorHeight = 330;
        int gap = 8;
        int contentHeight = listHeight + gap + editorHeight + 30;
        mobileViewport = new ModernMainLayout.Rect(x, top, width, Math.max(1, footer - top - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        mobileMaxScroll = Math.max(0, contentHeight - mobileViewport.height);
        mobileScroll = Math.max(0, Math.min(mobileScroll, mobileMaxScroll));
        int contentY = mobileViewport.y - mobileScroll;
        listBounds = new ModernMainLayout.Rect(x, contentY, width, listHeight);
        editorBounds = new ModernMainLayout.Rect(x, listBounds.bottom() + gap, width, editorHeight);

        ModernUiRenderer.beginClip(mobileViewport);
        drawCompactList(font, mx, my);
        drawEditorPanel(font, mx, my);
        int actionWidth = Math.max(1, (width - 12) / 3);
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
        drawButton(font, validateBounds, "gui.modern.pktint.u014", mx, my);
        drawButton(font, saveBounds, "gui.modern.pktint.u015", mx, my, true, true);
        drawButton(font, cancelBounds, "gui.modern.pktint.u016", mx, my);
        ModernUiRenderer.endClip();
        drawMobileScrollbar(mx, my);
    }

    private void drawCompactList(FontRenderer font, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawInterceptTree(font, mx, my);
    }

    @Override protected void drawNavigationOverlay(int x, int y) { navigationActions.drawOverlay(x, y); }
    @Override protected boolean navigationOverlayOpen() { return navigationActions.isOpen(); }

    private void configureNavigationActions() {
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("delete", "gui.modern.nav.delete", true, true, () -> selected >= 0, this::navigationDelete);
        navigationActions.action("reload", "gui.modern.nav.reload", false, false, () -> true, this::navigationReload);
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> promptCategory(false));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> !"__all__".equals(selectedCategory), () -> promptCategory(true));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !"__all__".equals(selectedCategory), () -> { state.deleteCategory(selectedCategory); selectedCategory = "__all__"; });
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> selected >= 0, () -> navigationActions.prompt("gui.modern.nav.rename", name.text(), value -> { name.setText(value); sync(); }));
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> selected >= 0, () -> { sync(); PacketInterceptConfig.InterceptRule copy = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(state.rules().get(selected)), PacketInterceptConfig.InterceptRule.class); copy.name += " (copy)"; state.rules().add(selected + 1, copy); select(selected + 1); });
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> selected >= 0, () -> navigationActions.choose(
                "移动到分类", state.categories(), state.rules().get(selected).category,
                value -> { sync(); state.rules().get(selected).category = value; selectedCategory = value; select(selected); }));
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> selected >= 0, () -> { editingEnabled = !editingEnabled; sync(); });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedCategories.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (TreeHit hit : treeHits) if (hit.group) collapsedCategories.add(hit.category); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationAdd() { sync(); state.add(); if (!"__all__".equals(selectedCategory)) state.rules().get(state.rules().size() - 1).category = selectedCategory; select(state.rules().size() - 1); validation = ""; return true; }

    private boolean navigationDelete() { state.remove(selected); selected = Math.min(selected, state.rules().size() - 1); if (selected >= 0) select(selected); else { selected = -1; } return true; }

    private boolean navigationReload() { state.reload(); validation = ""; selected = state.rules().isEmpty() ? -1 : 0; if (selected >= 0) select(selected); return true; }

    private void drawInterceptTree(FontRenderer font, int mx, int my) {
        configureNavigationActions();
        navigationActions.begin(font, listBounds);
        searchBounds = new ModernMainLayout.Rect(listBounds.x + 8, listBounds.y + 30,
                Math.max(1, listBounds.width - 16), 20);
        search.setBounds(new ModernMainLayout.Rect(searchBounds.x + 22, searchBounds.y + 4,
                Math.max(1, searchBounds.width - 28), 12));
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, search.focused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (search.text().trim().isEmpty() && !search.focused()) text(font, "gui.modern.pktint.u018",
                searchBounds.x + 22, searchBounds.y + 5, ModernUiRenderer.MUTED_TEXT, searchBounds.width - 28);
        search.draw();
        ModernMainLayout.Rect tree = new ModernMainLayout.Rect(listBounds.x + 6, searchBounds.bottom() + 8,
                Math.max(1, listBounds.width - 12), Math.max(1, navigationActions.contentBottom() - searchBounds.bottom() - 8));
        String query = search.text().trim().toLowerCase(java.util.Locale.ROOT);
        treeHits.clear();
        ModernUiRenderer.beginClip(tree);
        int y = tree.y - listScroll;
        for (String group : interceptTreeCategories()) {
            List<Integer> indices = matchingInterceptIndices(group, query);
            if (indices.isEmpty() && !query.isEmpty() && !group.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            ModernMainLayout.Rect groupRow = ModernTreeGuide.groupRow(tree, y);
            boolean collapsed = collapsedCategories.contains(group) && query.isEmpty();
            drawInterceptTreeGroup(font, groupRow, group, indices.size(), collapsed, mx, my);
            treeHits.add(new TreeHit(-1, group, true, groupRow));
            y = ModernTreeGuide.nextY(y, ModernTreeGuide.GROUP_HEIGHT);
            if (!collapsed) for (Integer index : indices) {
                ModernMainLayout.Rect item = ModernTreeGuide.itemRow(tree, y);
                ModernTreeGuide.drawChild(tree.x, 0, groupRow, item);
                PacketInterceptConfig.InterceptRule rule = state.rules().get(index.intValue());
                boolean selectedItem = index.intValue() == selected;
                boolean hovered = item.contains(mx, my);
                ModernUiRenderer.drawSubtlePanel(item.x, item.y, item.width, item.height, 4,
                        selectedItem ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                        selectedItem ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(item.x + 9, item.y + 13,
                        rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
                text(font, safe(rule.name), item.x + 20, item.y + 5, ModernUiRenderer.TEXT, item.width - 30);
                text(font, safe(rule.packetFilter), item.x + 20, item.y + 18, ModernUiRenderer.MUTED_TEXT, item.width - 30);
                treeHits.add(new TreeHit(index.intValue(), group, false, item));
                y = ModernTreeGuide.nextY(y, ModernTreeGuide.ITEM_HEIGHT);
            }
        }
        int treeMax = Math.max(0, y + listScroll - tree.bottom());
        listScroll = Math.max(0, Math.min(listScroll, treeMax));
        if (treeMax > 0) listScrollbar.draw(tree, listScroll, treeMax, tree.height, tree.height + treeMax, mx, my,
                value -> listScroll = value); else listScrollbar.idle();
        ModernUiRenderer.endClip();
        navigationActions.draw(mx, my);
    }

    private void drawInterceptTreeGroup(FontRenderer font, ModernMainLayout.Rect row, String group, int count,
            boolean collapsed, int mx, int my) {
        boolean selectedGroup = group.equals(selectedCategory);
        boolean hovered = row.contains(mx, my);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selectedGroup ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selectedGroup ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(row.x + 8, row.y + 7, collapsed,
                selectedGroup ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
        text(font, "__all__".equals(group) ? "gui.modern.pktint.u019" : group, row.x + 21, row.y + 7,
                ModernUiRenderer.TEXT, row.width - 48);
        text(font, String.valueOf(count), row.right() - 25, row.y + 7, ModernUiRenderer.MUTED_TEXT, 18);
    }

    private List<String> interceptTreeCategories() {
        LinkedHashSet<String> result = new LinkedHashSet<String>(); result.add("__all__");
        result.addAll(state.categories());
        for (PacketInterceptConfig.InterceptRule rule : state.rules()) {
            String value = safe(rule.category).trim(); if (!value.isEmpty()) result.add(value);
        }
        return new ArrayList<String>(result);
    }

    private List<Integer> matchingInterceptIndices(String group, String query) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < state.rules().size(); i++) {
            PacketInterceptConfig.InterceptRule rule = state.rules().get(i);
            if (!"__all__".equals(group) && !safe(rule.category).trim().equals(group)) continue;
            String searchable = (safe(rule.name) + " " + safe(rule.packetFilter) + " " + safe(rule.channel)
                    + " " + safe(rule.category)).toLowerCase(java.util.Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) result.add(Integer.valueOf(i));
        }
        return result;
    }

    private boolean selectTreeHit(int x, int y) {
        if (!navigationActions.inTree(x, y)) return false;
        for (TreeHit hit : treeHits) if (hit.bounds.contains(x, y)) {
            if (hit.group) {
                selectedCategory = hit.category;
                if (!collapsedCategories.add(hit.category)) collapsedCategories.remove(hit.category);
                listScroll = 0;
            } else {
                sync(); selectedCategory = hit.category; select(hit.ruleIndex);
            }
            return true;
        }
        return false;
    }

    private int treeContentHeight() {
        if (treeHits.isEmpty()) return 0;
        int top = listBounds.y + 36;
        int bottom = top;
        for (TreeHit hit : treeHits) bottom = Math.max(bottom, hit.bounds.bottom());
        return Math.max(0, bottom + listScroll - top);
    }

    private void drawEditorPanel(FontRenderer font, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, selected < 0 ? "gui.modern.pktint.u002" : "gui.modern.pktint.u003", editorBounds.x + 10, editorBounds.y + 9,
                ModernUiRenderer.TEXT, editorBounds.width - 20);
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(editorBounds.x + 1, editorBounds.y + 30,
                Math.max(1, editorBounds.width - 2), Math.max(1, editorBounds.height - 62));
        editorMaxScroll = Math.max(0, 5 * 28 + 260 - viewport.height);
        editorScroll = Math.max(0, Math.min(editorScroll, editorMaxScroll));
        ModernUiRenderer.beginClip(viewport);
        int y0 = viewport.y - editorScroll + 4;
        int fw = Math.max(1, editorBounds.width - 118);
        y0 = row(font, "gui.modern.pktint.u004", name, y0, fw, safeRule().name);
        y0 = row(font, "gui.modern.pktint.u005", filter, y0, fw, safeRule().packetFilter);
        y0 = row(font, "gui.modern.pktint.u006", channel, y0, fw, safeRule().channel);
        y0 = row(font, "gui.modern.pktint.u007", match, y0, fw, safeRule().matchHex);
        row(font, "gui.modern.pktint.u008", replace, y0, fw, safeRule().replaceHex);
        ModernUiRenderer.endClip();
        enabledBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.y + 174,
                editorBounds.width - 20, 22);
        ruleEnabledBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.y + 202,
                editorBounds.width / 2 - 14, 22);
        regexBounds = new ModernMainLayout.Rect(ruleEnabledBounds.right() + 8, ruleEnabledBounds.y,
                editorBounds.width - editorBounds.width / 2 - 18, 22);
        allBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.y + 230,
                editorBounds.width - 20, 22);
        drawFlag(font, enabledBounds, tr("gui.modern.pktint.fmt.intercept", tr(state.enabled() ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, state.enabled());
        drawFlag(font, ruleEnabledBounds, tr("gui.modern.pktint.fmt.rule_on", tr(editingEnabled ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, editingEnabled);
        drawFlag(font, regexBounds, tr("gui.modern.pktint.fmt.regex", tr(regex ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, regex);
        drawFlag(font, allBounds, tr("gui.modern.pktint.fmt.replace_all", tr(replaceAll ? "gui.modern.pktint.u009" : "gui.modern.pktint.u010")), mx, my, replaceAll);
        text(font, validation, editorBounds.x + 10, editorBounds.bottom() - 18, ModernUiRenderer.WARNING,
                Math.max(1, editorBounds.width - 20));
    }

    private int row(FontRenderer font, String label, PacketTextField field, int y, int width, String value) { text(font, label, editorBounds.x + 10, y + 6, ModernUiRenderer.MUTED_TEXT, 92); this.field(field, new ModernMainLayout.Rect(editorBounds.x + 98, y, width, 20), value); return y + 28; }
    private void drawFlag(FontRenderer font, ModernMainLayout.Rect r, String label, int mx, int my, boolean selected) { boolean hover = r.contains(mx, my); ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3, selected ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); centeredLabel(font, label, r, selected ? ModernUiRenderer.SELECTED_TEXT : ModernUiRenderer.TEXT); }
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
        if (splitBounds != null && splitBounds.contains(x, y)) { draggingSplit = true; return true; }
        if (listScrollbar.beginDrag(x, y)) { draggingListBar = true; return true; }
        if (mobileScrollbar.beginDrag(x, y)) {
            draggingMobileScrollbar = true;
            return true;
        }
        if (listBounds != null && listBounds.contains(x, y)) {
            if (searchBounds != null && searchBounds.contains(x, y)) {
                search.click(x, y, button);
            } else {
                selectTreeHit(x, y);
            }
            return true;
        }
        if (fieldClick(search, x, y, button) || fieldClick(name, x, y, button) || fieldClick(filter, x, y, button) || fieldClick(channel, x, y, button) || fieldClick(match, x, y, button) || fieldClick(replace, x, y, button)) return true;
        if (hit(enabledBounds, x, y)) { state.toggleEnabled(); return true; } if (hit(ruleEnabledBounds, x, y)) { editingEnabled = !editingEnabled; return true; } if (hit(regexBounds, x, y)) { regex = !regex; return true; } if (hit(allBounds, x, y)) { replaceAll = !replaceAll; return true; }
        if (hit(addBounds, x, y)) { return navigationAdd(); }
        if (hit(deleteBounds, x, y) && selected >= 0) { return navigationDelete(); }
        if (hit(reloadBounds, x, y)) { return navigationReload(); }
        if (hit(validateBounds, x, y)) { sync(); validation = validate(); return true; }
        if (hit(saveBounds, x, y)) { if (saveDraft()) owner.back(); return true; }
        if (hit(cancelBounds, x, y)) { owner.requestBack(); return true; }
        return true;
    }
    private void openInterceptContextMenu(int x, int y) {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("创建分组", () -> promptCategory(false)));
        if (!"__all__".equals(selectedCategory)) {
            items.add(new PacketContextMenu.Item("重命名分组", () -> promptCategory(true)));
            items.add(new PacketContextMenu.Item("删除分组", () -> { state.deleteCategory(selectedCategory); selectedCategory = "__all__"; }));
        }
        items.add(new PacketContextMenu.Item("gui.modern.pktint.u011", () -> { sync(); state.add(); if (!"__all__".equals(selectedCategory)) state.rules().get(state.rules().size() - 1).category = selectedCategory; select(state.rules().size() - 1); }));
        if (selected >= 0 && !"__all__".equals(selectedCategory)) {
            items.add(new PacketContextMenu.Item("移动到分类", () -> navigationActions.choose(
                    "移动到分类", state.categories(), state.rules().get(selected).category,
                    value -> { sync(); state.rules().get(selected).category = value; selectedCategory = value; select(selected); })));
        }
        items.add(new PacketContextMenu.Item("gui.modern.pktint.u012", () -> {
            if (selected >= 0) { state.remove(selected); selected = Math.min(selected, state.rules().size() - 1); if (selected >= 0) select(selected); else selected = -1; }
        }));
        openContextMenu(x, y, items);
    }
    private void promptCategory(boolean rename) {
        final String old = selectedCategory;
        owner.panels().push(new PacketModalPanel(owner, rename ? "重命名分组" : "创建分组", "分组名称", rename ? old : "", true, value -> {
            String next = value == null ? "" : value.trim();
            if (rename) state.renameCategory(old, next); else state.addCategory(next);
            if (!next.isEmpty()) selectedCategory = next;
        }));
    }
    @Override protected List<PacketContextMenu.Item> moreItems() {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("gui.modern.pktint.u011", () -> { sync(); state.add(); if (!"__all__".equals(selectedCategory)) state.rules().get(state.rules().size() - 1).category = selectedCategory; select(state.rules().size() - 1); }));
        items.add(new PacketContextMenu.Item("gui.modern.pktint.u014", () -> { sync(); validation = validate(); }));
        items.add(new PacketContextMenu.Item("gui.modern.pktint.u015", () -> saveDraft()));
        return items;
    }
    private void select(int index) { selected = index; validation = ""; PacketInterceptConfig.InterceptRule r = state.rules().get(index); name.setText(r.name); filter.setText(r.packetFilter); channel.setText(r.channel); match.setText(r.matchHex); replace.setText(r.replaceHex); editingEnabled = r.enabled; regex = r.regexEnabled; replaceAll = r.replaceAll; }
    private void sync() { if (selected < 0 || selected >= state.rules().size()) return; PacketInterceptConfig.InterceptRule r = state.rules().get(selected); r.name = name.text().trim(); r.packetFilter = filter.text().trim(); r.channel = channel.text().trim(); r.matchHex = match.text(); r.replaceHex = replace.text(); r.enabled = editingEnabled; r.regexEnabled = regex; r.replaceAll = replaceAll; }
    private String validate() { List<String> errors = PacketInterceptManager.validateRules(state.rules()); return errors == null || errors.isEmpty() ? "" : errors.get(0); }
    private PacketInterceptConfig.InterceptRule safeRule() { return selected >= 0 && selected < state.rules().size() ? state.rules().get(selected) : new PacketInterceptConfig.InterceptRule(); }
    private boolean saveDraft() { sync(); validation = validate(); if (!validation.isEmpty()) { owner.status(validation); return false; } state.save(); saved = true; owner.status("gui.modern.pktint.u017"); return true; }
    @Override public void save() { saveDraft(); }
    @Override public boolean isDirty() { sync(); return state.isDirty(); }
    @Override public boolean keyTyped(char c, int code) {
        if (navigationActions.keyTyped(c, code)) return true; return fieldKey(search, c, code) || fieldKey(name, c, code) || fieldKey(filter, c, code) || fieldKey(channel, c, code) || fieldKey(match, c, code) || fieldKey(replace, c, code) || code == Keyboard.KEY_RETURN; }
    @Override public void discardDraft() {
        navigationActions.close(); if (!saved) state.cancel(); if (!state.rules().isEmpty()) select(0); else selected = -1; validation = ""; PacketTextField.clearActiveFocus(); draggingMobileScrollbar = false; mobileScroll = 0; }
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
        if (draggingSplit && button == 0) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(Math.max(2, area.width - 24),
                    x - (area.x + 12), 180, 280, 150, 220);
            splitRatio = split.ratio;
            return true;
        }
        if (draggingListBar && button == 0) { listScrollbar.applyDrag(x, y); return true; }
        if (draggingMobileScrollbar && button == 0) { mobileScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        if (draggingSplit) { MainUiLayoutManager.setModernSplitRatio("packet.intercept_rules.editor", splitRatio); draggingSplit = false; return true; }
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
    private void updateMobileScroll(int mouseY) { if (mobileScrollbarBounds == null || mobileScrollbarThumbBounds == null || mobileMaxScroll <= 0) return; int travel = Math.max(1, mobileScrollbarBounds.height - mobileScrollbarThumbBounds.height); int target = Math.max(0, Math.min(travel, mouseY - mobileScrollbarBounds.y - mobileScrollbarThumbBounds.height / 2)); mobileScroll = Math.max(0, Math.min(mobileMaxScroll, Math.round(target * (float) mobileMaxScroll / travel))); }
    private static String safe(String value) { return value == null ? "" : value; }
}
