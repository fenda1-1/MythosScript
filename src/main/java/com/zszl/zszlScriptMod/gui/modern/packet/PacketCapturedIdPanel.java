package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;

import net.minecraft.client.gui.FontRenderer;

/** Captured-ID categories, rule CRUD, share/import and captured-value copy. */
final class PacketCapturedIdPanel extends PacketPanelBase {
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("packet_capturedid");

    private static final String ALL = "__all__";
    private static final String UNGROUPED = "未分组";
    private final PacketTextField name = new PacketTextField(7401, 128), display = new PacketTextField(7402, 128);
    private final PacketTextField note = new PacketTextField(7409, 1024), aliases = new PacketTextField(7410, 2048);
    private final PacketTextField category = new PacketTextField(7403, 128), channel = new PacketTextField(7404, 128);
    private final PacketTextField pattern = new PacketTextField(7405, 32767), offset = new PacketTextField(7406, 128);
    private final PacketTextField group = new PacketTextField(7412, 8);
    private final PacketTextField search = new PacketTextField(7413, 128);
    private final PacketTextField preview = new PacketTextField(7414, 32767);
    private final PacketTextField liveValue = new PacketTextField(7415, 32767);
    private ModernMainLayout.Rect previewBounds;
    private ModernMainLayout.Rect liveValueBounds;
    private static final java.util.concurrent.ExecutorService PREVIEW_WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "captured-id-preview"); thread.setDaemon(true); return thread;
    });
    private java.util.concurrent.CompletableFuture<String> pendingPreview;
    private String pendingPreviewKey = "", lastPreviewKey = "";
    private String pendingPreviewDraft = "";
    private long nextPreviewCheck;
    private final PacketTextField bytes = new PacketTextField(7407, 8), sequence = new PacketTextField(7408, 128), cooldown = new PacketTextField(7411, 8);
    private List<CapturedIdRuleManager.RuleCard> cards = new ArrayList<>();
    private List<String> categories = new ArrayList<>();
    private String selectedCategory = ALL;
    private int selected = -1;
    private int editorScroll;
    private int editorMaxScroll;
    private int treeScroll;
    private int cardScroll;
    private boolean enabled = true, saved;
    private String savedSignature = "";
    private String direction = "both", target = "hex", valueType = "hex", updateMode = "always";
    private ModernMainLayout.Rect treeBounds, listBounds, editorBounds, addCategoryBounds, newRuleBounds, deleteBounds, copyBounds, exportBounds, importBounds, saveBounds, cancelBounds, generatorBounds, refreshBounds, sequenceSelectBounds;
    private final ModernMainLayout.Rect[] choiceBounds = new ModernMainLayout.Rect[4];
    private int lastMouseX, lastMouseY;
    private ModernMainLayout.Rect mobileViewport, mobileScrollbarBounds, mobileScrollbarThumbBounds;
    private int mobileScroll, mobileMaxScroll;
    private boolean draggingMobileScrollbar;
    private final ModernHoverScrollbar mobileScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar treeScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar cardScrollbar = new ModernHoverScrollbar();
    private final PacketDropdown directionDrop = new PacketDropdown("both", "inbound", "outbound");
    private final PacketDropdown targetDrop = new PacketDropdown("hex", "decoded");
    private final PacketDropdown valueTypeDrop = new PacketDropdown("hex", "decimal-int");
    private final PacketDropdown updateModeDrop = new PacketDropdown("always", "first", "cooldown", "recapture");
    private double splitRatio = 0.38D;
    private boolean draggingSplit, draggingTreeBar, draggingCardBar;
    private ModernMainLayout.Rect splitBounds;
    private ModernMainLayout.Rect categorySplitBounds, categoryEditorBounds;
    private ModernMainLayout.Rect searchBounds;
    private boolean draggingCategorySplit;
    private double categoryRatio = 0.33D;
    private final java.util.Set<String> collapsedCategories = new java.util.LinkedHashSet<String>();
    private final List<TreeHit> treeHits = new ArrayList<TreeHit>();

    private static final class TreeHit {
        final int cardIndex;
        final String category;
        final boolean group;
        final ModernMainLayout.Rect bounds;

        TreeHit(int cardIndex, String category, boolean group, ModernMainLayout.Rect bounds) {
            this.cardIndex = cardIndex;
            this.category = category;
            this.group = group;
            this.bounds = bounds;
        }
    }

    PacketCapturedIdPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktid.u001"); }
    @Override protected void initializePanel() {
        splitRatio = MainUiLayoutManager.getModernSplitRatio("packet.captured_id.editor", splitRatio);
        categoryRatio = MainUiLayoutManager.getModernSplitRatio("packet.captured_id.category", categoryRatio);
        name.ensure(font); display.ensure(font); note.ensure(font); aliases.ensure(font); category.ensure(font); channel.ensure(font); pattern.ensure(font); offset.ensure(font); group.ensure(font); bytes.ensure(font); sequence.ensure(font); cooldown.ensure(font); search.ensure(font);
        registerDropdown(directionDrop); registerDropdown(targetDrop); registerDropdown(valueTypeDrop); registerDropdown(updateModeDrop);
        directionDrop.setValue(direction); targetDrop.setValue(target); valueTypeDrop.setValue(valueType); updateModeDrop.setValue(updateMode);
        refresh();
        preview.ensure(font); preview.setReadOnly(true); preview.setText("预览：等待检查现有抓包");
        liveValue.ensure(font); liveValue.setReadOnly(true);
    }
    @Override public void updateScreen() { name.update(); display.update(); note.update(); aliases.update(); category.update(); channel.update(); pattern.update(); offset.update(); group.update(); bytes.update(); sequence.update(); cooldown.update(); search.update(); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        refreshPreview();
        int footer = area.bottom() - 30, x = area.x + 12, y = area.y + 42;
        lastMouseX = mx;
        lastMouseY = my;
        if (area.width < 600) {
            drawCompactBody(font, area, mx, my, x, y, footer);
            return;
        }
        mobileViewport = null;
        int available = Math.max(2, area.width - 24);
        ModernSplitPane.Split split = ModernSplitPane.calculate(available, splitRatio, 280, 260, 220, 200);
        splitRatio = split.ratio;
        int leftWidth = split.firstWidth;
        int height = Math.max(40, footer - y - 8);
        treeBounds = new ModernMainLayout.Rect(x, y, leftWidth, height);
        listBounds = treeBounds;
        categorySplitBounds = null;
        splitBounds = ModernSplitPane.verticalDividerBounds(x, leftWidth, 8, y, height);
        editorBounds = new ModernMainLayout.Rect(x + leftWidth + 8, y, Math.max(1, split.secondWidth - 8), height);
        ModernSplitPane.drawVerticalDivider(splitBounds, mx, my, draggingSplit);
        drawTree(font, mx, my); drawEditor(font, mx, my);
        int gap = 5, bw = Math.max(52, (treeBounds.width - gap * 2) / 3);
        addCategoryBounds = null; generatorBounds = null; copyBounds = null; exportBounds = null;
        newRuleBounds = null;
        deleteBounds = null;
        refreshBounds = null;
        int editorGap = 5, half = Math.max(60, (editorBounds.width - editorGap) / 2); importBounds = new ModernMainLayout.Rect(editorBounds.x, footer, half, 22); saveBounds = new ModernMainLayout.Rect(importBounds.right() + editorGap, footer, half, 22); cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + editorGap, footer, Math.max(50, editorBounds.right() - saveBounds.right() - editorGap), 22);
        drawButton(font, importBounds, "gui.modern.pktid.u009", mx, my); drawButton(font, saveBounds, "gui.modern.pktid.u010", mx, my, true, true); drawButton(font, cancelBounds, "gui.modern.pktid.u011", mx, my);
    }

    private void drawCompactBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my, int x, int top,
            int footer) {
        splitBounds = null;
        categorySplitBounds = null;
        int width = Math.max(1, area.width - 24);
        int treeHeight = 260;
        int listHeight = 160;
        int editorHeight = 430;
        int gap = 8;
        int contentHeight = treeHeight + editorHeight + gap + 66;
        mobileViewport = new ModernMainLayout.Rect(x, top, width, Math.max(1, footer - top - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        mobileMaxScroll = Math.max(0, contentHeight - mobileViewport.height);
        mobileScroll = Math.max(0, Math.min(mobileScroll, mobileMaxScroll));
        int contentY = mobileViewport.y - mobileScroll;
        treeBounds = new ModernMainLayout.Rect(x, contentY, width, treeHeight);
        int treeFooter = treeBounds.bottom() + 4;
        listBounds = treeBounds;
        int listFooter = treeFooter;
        editorBounds = new ModernMainLayout.Rect(x, listFooter + 26, width, editorHeight);

        ModernUiRenderer.beginClip(mobileViewport);
        drawTree(font, mx, my);
        int actionWidth = Math.max(1, (width - 10) / 3);
        addCategoryBounds = null; generatorBounds = null; copyBounds = null; exportBounds = null;
        newRuleBounds = null;
        deleteBounds = null;
        refreshBounds = null;

        drawEditor(font, mx, my);
        int editorFooter = editorBounds.bottom() + 4;
        int editorActionWidth = Math.max(1, (width - 10) / 3);
        importBounds = new ModernMainLayout.Rect(x, editorFooter, editorActionWidth, 22);
        saveBounds = new ModernMainLayout.Rect(importBounds.right() + 5, editorFooter, editorActionWidth, 22);
        cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + 5, editorFooter,
                Math.max(1, x + width - saveBounds.right() - 5), 22);
        drawButton(font, importBounds, "gui.modern.pktid.u009", mx, my);
        drawButton(font, saveBounds, "gui.modern.pktid.u010", mx, my, true, true);
        drawButton(font, cancelBounds, "gui.modern.pktid.u011", mx, my);
        ModernUiRenderer.endClip();
        drawMobileScrollbar(mx, my);
    }

    @Override protected void drawNavigationOverlay(int x, int y) { navigationActions.drawOverlay(x, y); }
    @Override protected boolean navigationOverlayOpen() { return navigationActions.isOpen(); }

    private void configureNavigationActions() {
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> selected >= 0,
                () -> { selected = -1; name.setText(name.text() + " (copy)"); });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true,
                () -> collapsedCategories.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true,
                () -> collapsedCategories.addAll(capturedTreeCategories()));
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("delete", "gui.modern.nav.delete", true, true, () -> selected >= 0, this::navigationDelete);
        navigationActions.action("reload", "gui.modern.nav.reload", false, false, () -> true, this::navigationReload);
        navigationActions.action("copy_value", "gui.modern.nav.copy_value", false, false, () -> selected >= 0, this::navigationCopyValue);
        navigationActions.action("export", "gui.modern.nav.export", false, false, () -> selected >= 0, this::navigationExport);
        navigationActions.action("import", "gui.modern.nav.import", false, false, () -> true, this::navigationImport);
        navigationActions.action("generator", "gui.modern.nav.generator", false, false, () -> true, this::navigationGenerator);
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> prompt("gui.modern.nav.category_add", "gui.modern.nav.category_add", value -> { if (CapturedIdRuleManager.addCategory(value)) { selectedCategory = value.trim(); refresh(); } }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> !ALL.equals(selectedCategory) && !UNGROUPED.equals(selectedCategory), () -> prompt("gui.modern.nav.category_rename", "gui.modern.nav.category_rename", selectedCategory, value -> { if (CapturedIdRuleManager.renameCategory(selectedCategory, value)) { selectedCategory = value.trim(); refresh(); } }));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !ALL.equals(selectedCategory) && !UNGROUPED.equals(selectedCategory), () -> { if (CapturedIdRuleManager.deleteCategory(selectedCategory)) { selectedCategory = ALL; refresh(); } });
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> selected >= 0, () -> navigationActions.prompt("gui.modern.nav.rename", name.text(), value -> name.setText(value)));
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> selected >= 0, () -> navigationActions.choose(
                "移动到分类", categories, category.text(), value -> category.setText(value)));
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> selected >= 0, () -> enabled = !enabled);
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationAdd() { selected = -1; clearEditor(); savedSignature = draftSignature(); return true; }

    private boolean navigationDelete() { if (CapturedIdRuleManager.deleteRule(cards.get(selected).index)) { refresh(); selected = -1; } return true; }

    private boolean navigationReload() { CapturedIdRuleManager.reloadRules(); refresh(); return true; }

    private boolean navigationCopyValue() { PacketClipboard.copy(safe(cards.get(selected).capturedHex)); owner.status("gui.modern.pktid.u038"); return true; }

    private boolean navigationExport() { PacketClipboard.copy(CapturedIdRuleManager.exportRuleShareCode(cards.get(selected).model, cards.get(selected).capturedHex)); owner.status("gui.modern.pktid.u039"); return true; }

    private boolean navigationImport() { prompt("gui.modern.pktid.u040", "gui.modern.pktid.u041", value -> { try { CapturedIdRuleManager.importRuleShareCode(value, selectedCategory.equals(ALL) ? "" : selectedCategory); refresh(); owner.status("gui.modern.pktid.u042"); } catch (RuntimeException e) { owner.status("导入失败: " + e.getMessage()); } }); return true; }

    private boolean navigationGenerator() { owner.openCapturedIdGenerator(); return true; }

    private void drawTree(FontRenderer font, int mx, int my) {
        configureNavigationActions();
        navigationActions.begin(font, treeBounds);
        ModernUiRenderer.drawSubtlePanel(treeBounds.x, treeBounds.y, treeBounds.width, treeBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        searchBounds = new ModernMainLayout.Rect(treeBounds.x + 8, treeBounds.y + 30,
                Math.max(1, treeBounds.width - 16), 20);
        search.setBounds(new ModernMainLayout.Rect(searchBounds.x + 22, searchBounds.y + 4,
                Math.max(1, searchBounds.width - 28), 12));
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, search.focused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (search.text().trim().isEmpty() && !search.focused()) text(font, "gui.modern.pktid.u046",
                searchBounds.x + 22, searchBounds.y + 5, ModernUiRenderer.MUTED_TEXT, searchBounds.width - 28);
        search.draw();
        ModernMainLayout.Rect tree = new ModernMainLayout.Rect(treeBounds.x + 6, searchBounds.bottom() + 8,
                Math.max(1, treeBounds.width - 12), Math.max(1, navigationActions.contentBottom() - searchBounds.bottom() - 8));
        String query = search.text().trim().toLowerCase(java.util.Locale.ROOT);
        treeHits.clear();
        ModernUiRenderer.beginClip(tree);
        int y = tree.y - treeScroll;
        for (String groupName : capturedTreeCategories()) {
            List<Integer> indices = matchingCapturedCards(groupName, query);
            if (indices.isEmpty() && !query.isEmpty() && !groupName.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            ModernMainLayout.Rect groupRow = ModernTreeGuide.groupRow(tree, y);
            boolean collapsed = collapsedCategories.contains(groupName) && query.isEmpty();
            boolean selectedGroup = selectedCategory.equals(groupName);
            boolean hovered = groupRow.contains(mx, my);
            ModernUiRenderer.drawSubtlePanel(groupRow.x, groupRow.y, groupRow.width, groupRow.height, 4,
                    selectedGroup ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selectedGroup ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawChevron(groupRow.x + 8, groupRow.y + 7, collapsed,
                    selectedGroup ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
            text(font, ALL.equals(groupName) ? "gui.modern.pktid.u013" : groupName, groupRow.x + 21, groupRow.y + 7,
                    ModernUiRenderer.TEXT, groupRow.width - 48);
            text(font, String.valueOf(indices.size()), groupRow.right() - 25, groupRow.y + 7,
                    ModernUiRenderer.MUTED_TEXT, 18);
            treeHits.add(new TreeHit(-1, groupName, true, groupRow));
            y = ModernTreeGuide.nextY(y, ModernTreeGuide.GROUP_HEIGHT);
            if (!collapsed) for (Integer index : indices) {
                ModernMainLayout.Rect item = ModernTreeGuide.itemRow(tree, y);
                ModernTreeGuide.drawChild(tree.x, 0, groupRow, item);
                CapturedIdRuleManager.RuleCard card = cards.get(index.intValue());
                boolean selectedItem = index.intValue() == selected;
                boolean itemHovered = item.contains(mx, my);
                ModernUiRenderer.drawSubtlePanel(item.x, item.y, item.width, item.height, 4,
                        selectedItem ? ModernUiRenderer.SURFACE_PRESSED : itemHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                        selectedItem ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(item.x + 9, item.y + 13,
                        card.model.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
                text(font, safe(card.model.displayName, card.model.name), item.x + 20, item.y + 5,
                        ModernUiRenderer.TEXT, item.width - 30);
                text(font, safe(card.model.direction, "both"), item.x + 20, item.y + 18,
                        ModernUiRenderer.MUTED_TEXT, item.width - 30);
                treeHits.add(new TreeHit(index.intValue(), groupName, false, item));
                y = ModernTreeGuide.nextY(y, ModernTreeGuide.ITEM_HEIGHT);
            }
        }
        int treeMax = Math.max(0, y + treeScroll - tree.bottom());
        if (treeMax > 0) treeScrollbar.draw(tree, treeScroll, treeMax, tree.height, tree.height + treeMax, mx, my,
                value -> treeScroll = value); else treeScrollbar.idle();
        ModernUiRenderer.endClip();
        navigationActions.draw(mx, my);
    }
    private void drawCards(FontRenderer font, int mx, int my) {
        cardScrollbar.idle();
    }

    private List<String> capturedTreeCategories() {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<String>();
        result.add(ALL);
        for (String category : categories) {
            String value = safe(category).trim();
            if (!value.isEmpty()) result.add(value);
        }
        for (CapturedIdRuleManager.RuleCard card : cards) {
            String value = safe(card.model.category).trim(); if (!value.isEmpty()) result.add(value);
        }
        for (CapturedIdRuleManager.RuleCard card : cards) {
            if (safe(card.model.category).trim().isEmpty()) {
                result.add(UNGROUPED);
                break;
            }
        }
        return new ArrayList<String>(result);
    }

    private List<Integer> matchingCapturedCards(String groupName, String query) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < cards.size(); i++) {
            CapturedIdRuleManager.RuleCard card = cards.get(i);
            String cardCategory = safe(card.model.category).trim();
            if (!ALL.equals(groupName) && (UNGROUPED.equals(groupName) ? !cardCategory.isEmpty() : !cardCategory.equals(groupName))) continue;
            String value = (safe(card.model.name) + " " + safe(card.model.displayName) + " "
                    + safe(card.model.note) + " " + safe(card.model.pattern) + " " + safe(card.model.category)).toLowerCase(java.util.Locale.ROOT);
            if (query.isEmpty() || value.contains(query)) result.add(Integer.valueOf(i));
        }
        return result;
    }

    private boolean selectTreeHit(int x, int y) {
        if (!navigationActions.inTree(x, y)) return false;
        for (TreeHit hit : treeHits) if (hit.bounds.contains(x, y)) {
            if (hit.group) {
                selectedCategory = hit.category;
                if (!collapsedCategories.add(hit.category)) collapsedCategories.remove(hit.category);
                treeScroll = 0;
            } else select(hit.cardIndex);
            return true;
        }
        return false;
    }

    private int treeContentHeight() {
        if (treeHits.isEmpty()) return 0;
        int top = treeBounds.y + 36;
        int bottom = top;
        for (TreeHit hit : treeHits) bottom = Math.max(bottom, hit.bounds.bottom());
        return Math.max(0, bottom + treeScroll - top);
    }
    private void drawEditor(FontRenderer font, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE); text(font, selected < 0 ? "gui.modern.pktid.u017" : "gui.modern.pktid.u018", editorBounds.x + 9, editorBounds.y + 9, ModernUiRenderer.TEXT, editorBounds.width - 18);
        ModernMainLayout.Rect viewport = editorContentBounds();
        int contentHeight = 12 * 26;
        editorMaxScroll = Math.max(0, contentHeight - viewport.height);
        editorScroll = clamp(editorScroll, 0, editorMaxScroll);
        ModernUiRenderer.beginClip(viewport);
        int y = viewport.y - editorScroll, fw = Math.max(80, editorBounds.width - 102);
        y = row(font, "gui.modern.pktid.u019", name, y, fw); y = row(font, "gui.modern.pktid.u020", display, y, fw); y = row(font, "gui.modern.pktid.u021", note, y, fw); y = row(font, "gui.modern.pktid.u022", aliases, y, fw);
        categoryEditorBounds = new ModernMainLayout.Rect(editorBounds.x + 88, y, fw, 20);
        text(font, "gui.modern.pktid.u012", editorBounds.x + 10, y + 6, ModernUiRenderer.MUTED_TEXT, 82);
        drawFlag(font, categoryEditorBounds, category.text(), mx, my, false);
        y += 26;
        y = row(font, "gui.modern.pktid.u023", channel, y, fw); y = row(font, "Pattern", pattern, y, fw); y = row(font, "Offset", offset, y, fw); y = row(font, "Group", group, y, fw); y = row(font, "gui.modern.pktid.u024", bytes, y, fw);
        int sequenceY = y;
        text(font, "gui.modern.pktid.u025", editorBounds.x + 10, sequenceY + 6, ModernUiRenderer.MUTED_TEXT, 82);
        int sequenceWidth = Math.max(60, fw - 88);
        this.field(sequence, new ModernMainLayout.Rect(editorBounds.x + 88, sequenceY, sequenceWidth, 20), null);
        sequenceSelectBounds = new ModernMainLayout.Rect(editorBounds.right() - 82, sequenceY, 76, 20);
        drawButton(font, sequenceSelectBounds, "gui.modern.pktid.u026", mx, my);
        y = sequenceY + 26;
        y = row(font, "gui.modern.pktid.u027", cooldown, y, fw);
        ModernUiRenderer.endClip();
         ModernMainLayout.Rect flags = new ModernMainLayout.Rect(editorBounds.x + 10,
                 editorBounds.bottom() - (editorBounds.width < 360 ? 134 : 106), editorBounds.width - 20, 22);
         drawFlag(font, flags, tr("gui.modern.pktid.fmt.enabled", tr(enabled ? "gui.modern.pktid.u028" : "gui.modern.pktid.u029")), mx, my, enabled);
         if (editorBounds.width < 360) {
             int flagWidth = Math.max(1, (editorBounds.width - 24) / 2);
             int rowY = editorBounds.bottom() - 106;
             choiceBounds[0] = new ModernMainLayout.Rect(editorBounds.x + 10, rowY, flagWidth, 22);
             choiceBounds[1] = new ModernMainLayout.Rect(choiceBounds[0].right() + 4, rowY, flagWidth, 22);
             choiceBounds[2] = new ModernMainLayout.Rect(editorBounds.x + 10, rowY + 28, flagWidth, 22);
             choiceBounds[3] = new ModernMainLayout.Rect(choiceBounds[2].right() + 4, rowY + 28, flagWidth, 22);
         } else {
             choiceBounds[0] = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.bottom() - 78,
                     Math.max(70, editorBounds.width / 4 - 4), 22);
             choiceBounds[1] = new ModernMainLayout.Rect(choiceBounds[0].right() + 4, choiceBounds[0].y,
                     choiceBounds[0].width, 22);
             choiceBounds[2] = new ModernMainLayout.Rect(choiceBounds[1].right() + 4, choiceBounds[0].y,
                     choiceBounds[0].width, 22);
             choiceBounds[3] = new ModernMainLayout.Rect(choiceBounds[2].right() + 4, choiceBounds[0].y,
                     Math.max(70, editorBounds.right() - choiceBounds[2].right() - 10), 22);
         }
         direction = directionDrop.value();
         target = targetDrop.value();
         valueType = valueTypeDrop.value();
         updateMode = updateModeDrop.value();
         directionDrop.drawButton(font, choiceBounds[0], mx, my);
         targetDrop.drawButton(font, choiceBounds[1], mx, my);
         valueTypeDrop.drawButton(font, choiceBounds[2], mx, my);
         updateModeDrop.drawButton(font, choiceBounds[3], mx, my);
         liveValueBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.bottom() - 51,
                 Math.max(1, editorBounds.width - 20), 20);
         this.field(liveValue, liveValueBounds, null);
         previewBounds = new ModernMainLayout.Rect(editorBounds.x + 10, editorBounds.bottom() - 27,
                 Math.max(1, editorBounds.width - 20), 20);
         this.field(preview, previewBounds, null);
    }

    private void refreshPreview() {
        long now = System.currentTimeMillis();
        if (now < nextPreviewCheck) return;
        nextPreviewCheck = now + 200;
        String actual = CapturedIdRuleManager.getCapturedIdHex(name.text().trim());
        String actualText = "实际保存值：" + (actual == null ? "未捕获" : actual);
        if (actual != null && actual.replace(" ", "").length() <= 16)
            actualText += " | 数值=" + new java.math.BigInteger(actual.replace(" ", ""), 16);
        actualText += com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig.INSTANCE.enableBusinessPacketProcessing
                ? " | 业务处理：开启" : " | 业务处理：关闭";
        if (isDirty()) actualText += " | 草稿未保存";
        actualText += " | " + CapturedIdRuleManager.getRuntimeStatus(name.text().trim());
        if (!actualText.equals(liveValue.text())) liveValue.setText(actualText);
        List<com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData> received;
        List<com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData> sent;
        synchronized (com.zszl.zszlScriptMod.utils.PacketCaptureHandler.capturedReceivedPackets) {
            received = new ArrayList<>(com.zszl.zszlScriptMod.utils.PacketCaptureHandler.capturedReceivedPackets);
        }
        synchronized (com.zszl.zszlScriptMod.utils.PacketCaptureHandler.capturedPackets) {
            sent = new ArrayList<>(com.zszl.zszlScriptMod.utils.PacketCaptureHandler.capturedPackets);
        }
        String key = draftSignature() + "|" + packetFingerprint(received) + "|" + packetFingerprint(sent);
        if (pendingPreview != null) {
            if (!pendingPreview.isDone()) return;
            if (draftSignature().equals(pendingPreviewDraft) && (!received.isEmpty() || !sent.isEmpty()
                    || key.equals(pendingPreviewKey))) {
                String result;
                try { result = pendingPreview.join(); } catch (RuntimeException e) { result = "预览错误：" + e.getMessage(); }
                if (!result.equals(preview.text())) preview.setText(result);
                lastPreviewKey = pendingPreviewKey;
            }
            pendingPreview = null;
        }
        if (key.equals(lastPreviewKey)) return;
        CapturedIdRuleManager.RuleEditModel model = new CapturedIdRuleManager.RuleEditModel();
        model.pattern = pattern.text(); model.channel = channel.text().trim(); model.direction = directionDrop.value();
        model.target = targetDrop.value(); model.valueType = valueTypeDrop.value(); model.offset = offset.text();
        model.updateSequenceMode = updateModeDrop.value(); model.enabled = enabled;
        try {
            model.group = Integer.parseInt(group.text().trim());
            model.byteLength = Integer.parseInt(bytes.text().trim());
        } catch (NumberFormatException e) {
            preview.setText("预览错误：分组和字节数需要填写整数"); lastPreviewKey = key; return;
        }
        if (!draftSignature().equals(pendingPreviewDraft)) preview.setText("预览：正在检查现有抓包…");
        pendingPreviewKey = key;
        pendingPreviewDraft = draftSignature();
        pendingPreview = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> com.zszl.zszlScriptMod.utils.CapturedIdRulePreview.evaluate(model, received, sent), PREVIEW_WORKER);
    }

    private long packetFingerprint(List<com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData> packets) {
        long fingerprint = packets.size();
        for (com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData packet : packets)
            fingerprint = fingerprint * 31 + System.identityHashCode(packet) + packet.getLastTimestamp();
        return fingerprint;
    }
    private int row(FontRenderer font, String label, PacketTextField field, int y, int width) { text(font, label, editorBounds.x + 10, y + 6, ModernUiRenderer.MUTED_TEXT, 82); this.field(field, new ModernMainLayout.Rect(editorBounds.x + 88, y, width, 20), null); return y + 26; }
    private void drawFlag(FontRenderer font, ModernMainLayout.Rect r, String value, int mx, int my, boolean selected) { boolean hover = r.contains(mx, my); ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3, selected ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); text(font, value, r.x + 8, r.y + 6, selected ? ModernUiRenderer.SELECTED_TEXT : ModernUiRenderer.TEXT, r.width - 16); }

    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (hit(liveValueBounds, x, y) && (mobileViewport == null || mobileViewport.contains(x, y))) {
            if (button == 1) {
                openContextMenu(x, y, java.util.Collections.singletonList(
                        new PacketContextMenu.Item("复制实际保存值", () -> PacketClipboard.copy(liveValue.text()))));
                return true;
            }
            liveValue.click(x, y, button); return true;
        }
        if (hit(previewBounds, x, y) && (mobileViewport == null || mobileViewport.contains(x, y))) {
            if (button == 1) {
                openContextMenu(x, y, java.util.Collections.singletonList(
                        new PacketContextMenu.Item("复制预览", () -> PacketClipboard.copy(preview.text()))));
                return true;
            }
            preview.click(x, y, button); return true;
        }
        if (navigationActions.mouseClicked(x, y, button)) return true;
        if (button == 1 && navigationActions.inTree(x, y)) {
            selectTreeContextHit(x, y);
            PacketTextField.clearActiveFocus();
            boolean group = false;
            for (TreeHit hit : treeHits) if (hit.bounds.contains(x, y)) { group = hit.group; break; }
            if (group) navigationActions.context(x, y, "add", "category_add", "category_rename",
                    "category_delete", "expand", "collapse");
            else navigationActions.context(x, y, "add", "copy", "rename", "move", "toggle", "delete",
                    "copy_value", "export", "import", "reload");
            return true;
        }


        if (button == 0 && categorySplitBounds != null && categorySplitBounds.contains(x, y)) { draggingCategorySplit = true; return true; }
        if (button == 0 && splitBounds != null && splitBounds.contains(x, y)) { draggingSplit = true; return true; }
        if (button == 0 && treeScrollbar.beginDrag(x, y)) { draggingTreeBar = true; return true; }
        if (button == 0 && cardScrollbar.beginDrag(x, y)) { draggingCardBar = true; return true; }
        if (button == 0 && mobileScrollbar.beginDrag(x, y)) {
            draggingMobileScrollbar = true;
            return true;
        }
        if (button != 0) return true;
        if (searchBounds != null && searchBounds.contains(x, y)) { search.click(x, y, button); return true; }
        if (treeBounds != null && treeBounds.contains(x, y)) { selectTreeHit(x, y); return true; }
        ModernMainLayout.Rect viewport = editorContentBounds();
        if (categoryEditorBounds != null && categoryEditorBounds.contains(x, y)) {
            PacketTextField.clearActiveFocus();
            navigationActions.choose("选择分组", categories, category.text(), value -> category.setText(value));
            return true;
        }
        if (viewport.contains(x, y) && (fieldClick(name, x, y, button) || fieldClick(display, x, y, button) || fieldClick(note, x, y, button) || fieldClick(aliases, x, y, button) || fieldClick(channel, x, y, button) || fieldClick(pattern, x, y, button) || fieldClick(offset, x, y, button) || fieldClick(group, x, y, button) || fieldClick(bytes, x, y, button) || fieldClick(sequence, x, y, button) || fieldClick(cooldown, x, y, button))) return true;
        if (hit(sequenceSelectBounds, x, y)) { owner.openPathSequenceSelector(value -> sequence.setText(value)); return true; }
        if (hit(addCategoryBounds, x, y)) { prompt("gui.modern.pktid.u036", "gui.modern.pktid.u037", value -> { if (CapturedIdRuleManager.addCategory(value)) { selectedCategory = value.trim(); refresh(); } }); return true; }
        if (hit(refreshBounds, x, y)) { return navigationReload(); }
        if (hit(generatorBounds, x, y)) { return navigationGenerator(); }
         if (hit(newRuleBounds, x, y)) { return navigationAdd(); }
        if (hit(deleteBounds, x, y) && selected >= 0 && selected < cards.size()) { return navigationDelete(); }
        if (hit(copyBounds, x, y) && selected >= 0) { return navigationCopyValue(); }
        if (hit(exportBounds, x, y) && selected >= 0) { return navigationExport(); }
        if (hit(importBounds, x, y)) { return navigationImport(); }
         if (hit(saveBounds, x, y)) { saveCurrent(); return true; }
         if (hit(cancelBounds, x, y)) { owner.requestBack(); return true; }
        return true;
    }

     private void prompt(String title, String message, PacketModalPanel.Result result) { prompt(title, message, "", result); }
     private void prompt(String title, String message, String initial, PacketModalPanel.Result result) { owner.panels().push(new PacketModalPanel(owner, title, message, initial, true, result)); }
    private void select(int index) { selected = index; CapturedIdRuleManager.RuleEditModel m = cards.get(index).model; name.setText(m.name); display.setText(m.displayName); note.setText(m.note); aliases.setText(m.aliasesCsv); category.setText(m.category); channel.setText(m.channel); pattern.setText(m.pattern); offset.setText(m.offset); group.setText(String.valueOf(m.group)); bytes.setText(String.valueOf(m.byteLength)); sequence.setText(m.updateSequenceName); cooldown.setText(String.valueOf(m.updateSequenceCooldownMs)); enabled = m.enabled; direction = safe(m.direction, "both"); target = safe(m.target, "hex"); valueType = safe(m.valueType, "hex"); updateMode = safe(m.updateSequenceMode, "always"); directionDrop.setValue(direction); targetDrop.setValue(target); valueTypeDrop.setValue(valueType); updateModeDrop.setValue(updateMode); savedSignature = draftSignature(); }
    private void clearEditor() { name.setText("cid_rule"); display.setText(""); note.setText(""); aliases.setText(""); category.setText(selectedCategory.equals(ALL) ? "" : selectedCategory); channel.setText(""); pattern.setText(""); offset.setText(""); group.setText("1"); bytes.setText("4"); sequence.setText(""); cooldown.setText("1000"); enabled = true; direction = "both"; target = "hex"; valueType = "hex"; updateMode = "always"; directionDrop.setValue(direction); targetDrop.setValue(target); valueTypeDrop.setValue(valueType); updateModeDrop.setValue(updateMode); }
    private boolean saveCurrent() {
        CapturedIdRuleManager.RuleEditModel m = new CapturedIdRuleManager.RuleEditModel();
        m.name = name.text().trim(); m.displayName = display.text().trim(); m.note = note.text(); m.aliasesCsv = aliases.text();
        m.category = category.text().trim(); m.channel = channel.text().trim(); m.pattern = pattern.text().trim(); m.offset = offset.text().trim();
        m.group = parseInt(group.text(), 1, 1, 999); m.byteLength = parseInt(bytes.text(), 4, 1, 4096);
        m.updateSequenceName = sequence.text().trim(); m.updateSequenceCooldownMs = parseInt(cooldown.text(), 1000, 0, 86400000);
        m.enabled = enabled; m.direction = direction; m.target = target; m.valueType = valueType; m.updateSequenceMode = updateMode;
         if (m.name.isEmpty() || m.pattern.isEmpty()) { owner.status("gui.modern.pktid.u043"); saved = false; return false; }
         boolean ok = selected >= 0 && selected < cards.size() && cards.get(selected).index >= 0
                 ? CapturedIdRuleManager.updateRule(cards.get(selected).index, m) : CapturedIdRuleManager.addRule(m);
          owner.status(ok ? "gui.modern.pktid.u044" : "gui.modern.pktid.u045"); saved = ok;
          if (ok) {
              String savedName = m.name;
              refresh();
              if (selected < 0) for (int i = 0; i < cards.size(); i++) {
                  if (savedName.equals(cards.get(i).model.name)) { select(i); break; }
              }
          }
          return ok;
    }
     private void refresh() { String selectedName = selected >= 0 && selected < cards.size() ? safe(cards.get(selected).model.name) : ""; categories = new ArrayList<>(CapturedIdRuleManager.getAllCategories()); cards = new ArrayList<>(CapturedIdRuleManager.getRuleCards()); cardScroll = 0; selected = -1; if (!selectedName.isEmpty()) for (int i = 0; i < cards.size(); i++) if (selectedName.equals(cards.get(i).model.name)) { select(i); break; } if (selected < 0) clearEditor(); savedSignature = draftSignature(); }
    private CapturedIdRuleManager.RuleEditModel safeModel() { return selected >= 0 && selected < cards.size() ? cards.get(selected).model : new CapturedIdRuleManager.RuleEditModel(); }
     @Override public boolean keyTyped(char c, int code) {
         if (liveValue.focused()) return liveValue.key(c, code);
         if (preview.focused()) return preview.key(c, code);
         if (navigationActions.keyTyped(c, code)) return true; if (code == org.lwjgl.input.Keyboard.KEY_DELETE && !ALL.equals(selectedCategory) && !UNGROUPED.equals(selectedCategory)) { if (CapturedIdRuleManager.deleteCategory(selectedCategory)) { selectedCategory = ALL; refresh(); } return true; } return fieldKey(search, c, code) || fieldKey(name, c, code) || fieldKey(display, c, code) || fieldKey(note, c, code) || fieldKey(aliases, c, code) || fieldKey(channel, c, code) || fieldKey(pattern, c, code) || fieldKey(offset, c, code) || fieldKey(group, c, code) || fieldKey(bytes, c, code) || fieldKey(sequence, c, code) || fieldKey(cooldown, c, code); }
    @Override public void discardDraft() {
        navigationActions.close(); PacketTextField.clearActiveFocus(); draggingMobileScrollbar = false; mobileScroll = 0; }
    @Override public void save() { if (isDirty()) saveCurrent(); }
    @Override public boolean isDirty() { return !draftSignature().equals(savedSignature); }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) {
        if (navigationActions.wheel(wheel)) return true;
        if (wheel == 0) return false;
        if (mobileViewport != null && mobileViewport.contains(lastMouseX, lastMouseY)) { int before = mobileScroll; mobileScroll = clamp(mobileScroll + (wheel > 0 ? -30 : 30), 0, mobileMaxScroll); return before != mobileScroll; }
        if (treeBounds != null && treeBounds.contains(lastMouseX, lastMouseY)) { int treeHeight = navigationActions.treeHeight(); treeScroll = clamp(treeScroll + (wheel > 0 ? -30 : 30), 0, Math.max(0, treeContentHeight() - treeHeight)); return true; }
        if (editorContentBounds().contains(lastMouseX, lastMouseY)) { editorScroll = clamp(editorScroll + (wheel > 0 ? -24 : 24), 0, editorMaxScroll); return true; }
        return false;
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (liveValue.dragSelection(x, button)) return true;
        if (preview.dragSelection(x, button)) return true;
        if (navigationActions.isOpen()) return true;
        if (draggingCategorySplit && button == 0) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(
                    Math.max(2, area.width - 24), x - (area.x + 12), 120, 180, 90, 120);
            categoryRatio = split.ratio;
            return true;
        }
        if (draggingSplit && button == 0) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(Math.max(2, area.width - 24),
                    x - (area.x + 12), 280, 260, 220, 200);
            splitRatio = split.ratio;
            return true;
        }
        if (draggingTreeBar && button == 0) { treeScrollbar.applyDrag(x, y); return true; }
        if (draggingCardBar && button == 0) { cardScrollbar.applyDrag(x, y); return true; }
        if (draggingMobileScrollbar && button == 0) { mobileScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
         if (draggingCategorySplit) { MainUiLayoutManager.setModernSplitRatio("packet.captured_id.category", categoryRatio); draggingCategorySplit = false; return true; }
         if (draggingSplit) { MainUiLayoutManager.setModernSplitRatio("packet.captured_id.editor", splitRatio); draggingSplit = false; return true; }
        if (draggingTreeBar) { draggingTreeBar = false; treeScrollbar.endDrag(); return true; }
        if (draggingCardBar) { draggingCardBar = false; cardScrollbar.endDrag(); return true; }
        if (draggingMobileScrollbar) { draggingMobileScrollbar = false; mobileScrollbar.endDrag(); return true; }
        return false;
    }
    private String draftSignature() { return name.text() + "\u0001" + display.text() + "\u0001" + note.text() + "\u0001" + aliases.text() + "\u0001" + category.text() + "\u0001" + channel.text() + "\u0001" + pattern.text() + "\u0001" + offset.text() + "\u0001" + group.text() + "\u0001" + bytes.text() + "\u0001" + sequence.text() + "\u0001" + cooldown.text() + "\u0001" + enabled + "\u0001" + direction + "\u0001" + target + "\u0001" + valueType + "\u0001" + updateMode; }
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
    private ModernMainLayout.Rect editorContentBounds() { return editorBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : new ModernMainLayout.Rect(editorBounds.x + 1, editorBounds.y + 30, Math.max(1, editorBounds.width - 2), Math.max(1, editorBounds.height - (editorBounds.width < 360 ? 178 : 150))); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private void openCapturedIdContext(int x, int y) {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        String category = selectTreeContextHit(x, y);
        if (category != null && !ALL.equals(category) && !UNGROUPED.equals(category)) {
            final String target = category;
            items.add(new PacketContextMenu.Item("gui.modern.pktid.u031", () -> prompt("gui.modern.pktid.u031", "gui.modern.pktid.u032", target, value -> {
                if (CapturedIdRuleManager.renameCategory(target, value)) { selectedCategory = value.trim(); refresh(); }
                else owner.status("分组重命名失败");
            })));
            items.add(new PacketContextMenu.Item("gui.modern.pktid.u006", () -> {
                if (CapturedIdRuleManager.deleteCategory(target)) { selectedCategory = ALL; refresh(); }
                else owner.status("分组删除失败");
            }));
        }
        items.add(new PacketContextMenu.Item("gui.modern.pktid.u036", () -> prompt("gui.modern.pktid.u036", "gui.modern.pktid.u037", "", value -> {
            if (CapturedIdRuleManager.addCategory(value)) { selectedCategory = value.trim(); refresh(); }
            else owner.status("分组创建失败");
        })));
        items.add(new PacketContextMenu.Item("gui.modern.pktid.u005", () -> beginNewRule()));
        if (selected >= 0) {
            items.add(new PacketContextMenu.Item("gui.modern.pktid.u006", () -> {
                if (selected < cards.size() && CapturedIdRuleManager.deleteRule(cards.get(selected).index)) { refresh(); selected = -1; }
            }));
            if (category != null && !ALL.equals(category) && selected < cards.size()) {
                items.add(new PacketContextMenu.Item("移动到分类", () -> navigationActions.choose(
                        "移动到分类", categories, cards.get(selected).model.category,
                        value -> CapturedIdRuleManager.moveRuleToCategory(cards.get(selected).index, value))));
            }
        }
        openContextMenu(x, y, items);
    }
    private String selectTreeContextHit(int x, int y) {
        if (treeBounds == null || !treeBounds.contains(x, y)) return null;
        for (TreeHit hit : treeHits) if (hit.bounds.contains(x, y)) {
            if (hit.group) {
                selectedCategory = hit.category;
                return hit.category;
            }
            select(hit.cardIndex);
            selectedCategory = hit.category;
            return hit.category;
        }
        return null;
    }
    private void beginNewRule() { selected = -1; clearEditor(); savedSignature = draftSignature(); }
    @Override protected List<PacketContextMenu.Item> moreItems() {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("gui.modern.pktid.u002", () -> prompt("gui.modern.pktid.u036", "gui.modern.pktid.u037", "", value -> { if (CapturedIdRuleManager.addCategory(value)) { selectedCategory = value.trim(); refresh(); } else owner.status("分组创建失败"); })));
        items.add(new PacketContextMenu.Item("gui.modern.pktid.u005", () -> beginNewRule()));
        items.add(new PacketContextMenu.Item("gui.modern.pktid.u009", () -> { }));
        items.add(new PacketContextMenu.Item("gui.modern.pktid.u010", () -> saveCurrent()));
        return items;
    }
}
