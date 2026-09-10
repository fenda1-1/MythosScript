package com.zszl.zszlScriptMod.gui.modern.rules;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.handlers.BlockReplacementHandler;
import com.zszl.zszlScriptMod.system.BlockReplacementRule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Native two-pane workbench for block replacement rules. */
public final class ModernBlockReplacementWorkbenchTab implements ModernSettingsTab {
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("block_replacement");

    private static final String CATEGORY_ALL = "__all__";
    private static final String CATEGORY_DEFAULT = "默认";

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 30;
    private static final int CATEGORY_ROW_HEIGHT = ModernTreeGuide.GROUP_HEIGHT;
    private static final int RULE_ROW_HEIGHT = ModernTreeGuide.ITEM_HEIGHT;
    private static final int ENTRY_ROW_HEIGHT = 23;
    private static final int ENTRY_HEADER_HEIGHT = 28;
    private static final int FULL_NAV_CONTROLS_HEIGHT = 116;
    private static final Gson STATE_GSON = new Gson();

    private final WorkbenchState state;
    private final Set<String> collapsedCategories = new HashSet<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<EntryHit> entryHits = new ArrayList<>();
    private final RuleTreeToggle ruleToggle = new RuleTreeToggle();

    private final java.util.Map<Object, com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState> editorPositions = new java.util.WeakHashMap<>();
    private ModernSettingsTab editor;
    private ModernFormSettingsTab<BlockReplacementRule> formEditor;
    private BlockReplacementRule editorRule;
    private String corner1Draft = "";
    private String corner2Draft = "";

    private GuiTextField searchField;
    private GuiTextField categoryNameField;
    private GuiTextField sourceField;
    private GuiTextField targetField;

    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect navigationBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect configurationBounds;
    private ModernMainLayout.Rect entriesBounds;
    private ModernMainLayout.Rect navigationDividerBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect navigationContentBounds;
    private ModernMainLayout.Rect categoryNameBounds;
    private ModernMainLayout.Rect entryListBounds;
    private ModernMainLayout.Rect entryEditBounds;
    private ModernMainLayout.Rect sourceBounds;
    private ModernMainLayout.Rect targetBounds;
    private ModernMainLayout.Rect pickSourceBounds;
    private ModernMainLayout.Rect pickTargetBounds;
    private ModernMainLayout.Rect entryEnabledBounds;
    private ModernMainLayout.Rect entryAddBounds;
    private ModernMainLayout.Rect entryDuplicateBounds;
    private ModernMainLayout.Rect entryDeleteBounds;
    private ModernMainLayout.Rect entryApplyBounds;
    private ModernMainLayout.Rect entryScrollbarBounds;
    private ModernMainLayout.Rect entryScrollbarThumbBounds;

    private ModernMainLayout.Rect addCategoryBounds;
    private ModernMainLayout.Rect renameCategoryBounds;
    private ModernMainLayout.Rect deleteCategoryBounds;
    private ModernMainLayout.Rect moveCategoryUpBounds;
    private ModernMainLayout.Rect moveCategoryDownBounds;
    private ModernMainLayout.Rect copyCategoryNameBounds;
    private ModernMainLayout.Rect addRuleBounds;
    private ModernMainLayout.Rect duplicateRuleBounds;
    private ModernMainLayout.Rect deleteRuleBounds;
    private ModernMainLayout.Rect moveRuleUpBounds;
    private ModernMainLayout.Rect moveRuleDownBounds;
    private ModernMainLayout.Rect copyRuleNameBounds;
    private ModernMainLayout.Rect reloadBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;

    private double navigationRatio = 0.28D;
    private boolean layoutPreferencesLoaded;
    private int navigationScroll;
    private int navigationMaxScroll;
    private int draggedEntry = -1;
    private int entryScroll;
    private int entryMaxScroll;
    private int selectedEntryIndex = -1;
    private boolean draggingNavigationDivider;
    private boolean draggingEntryScrollbar;
    private BlockReplacementRule pendingDelete;
    private String pendingCategoryDelete;
    private BlockReplacementRule.BlockReplacementEntry pendingEntryDelete;
    private boolean pendingReload;
    private String status = "";
    private String entryValidation = "";

    public ModernBlockReplacementWorkbenchTab() {
        state = new WorkbenchState(BlockReplacementHandler.rules, BlockReplacementHandler.getCategoriesSnapshot());
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("rules.block_replacement.navigation", navigationRatio);
            layoutPreferencesLoaded = true;
        }
        if (searchField == null) {
            searchField = createField(fontRenderer, 4101, 120);
        }
        if (categoryNameField == null) {
            categoryNameField = createField(fontRenderer, 4102, 120);
            categoryNameField.setText(isConcreteCategory(state.selectedCategory())
                    ? state.selectedCategory() : "");
        }
        if (sourceField == null) {
            sourceField = createField(fontRenderer, 4103, 256);
        }
        if (targetField == null) {
            targetField = createField(fontRenderer, 4104, 256);
        }
        ensureEditor(fontRenderer);
        editor.ensureInitialized(fontRenderer);
    }

    @Override
    public void updateScreen() {
        updateCursor(searchField);
        updateCursor(categoryNameField);
        updateCursor(sourceField);
        updateCursor(targetField);
        ensureEditor(null);
        if (editor != null) {
            editor.updateScreen();
        }
        if (categoryNameField != null && !categoryNameField.isFocused()) {
            setTextIfDifferent(categoryNameField, isConcreteCategory(state.selectedCategory())
                    ? state.selectedCategory() : "");
        }
        BlockReplacementRule.BlockReplacementEntry entry = selectedEntry();
        if (entry != null) {
            if (sourceField != null && !sourceField.isFocused()) {
                setTextIfDifferent(sourceField, safe(entry.sourceBlockId));
            }
            if (targetField != null && !targetField.isFocused()) {
                setTextIfDifferent(targetField, safe(entry.targetBlockId));
            }
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requested, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        bounds = inset(requested, 7);
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        int splitTotal = Math.max(2, bounds.width - 20);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, navigationRatio, 150, 260, 120, 180);
        navigationRatio = split.ratio;

        int railWidth = split.firstWidth;
        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + TOP_INSET, railWidth,
                Math.max(1, bounds.height - TOP_INSET - FOOTER_HEIGHT - 6));
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 0,
                navigationBounds.y, navigationBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationDividerBounds.right(), navigationBounds.y,
                Math.max(1, bounds.right() - navigationDividerBounds.right() - 8), navigationBounds.height);
        configurationBounds = editorBounds;
        entriesBounds = null;
        entryListBounds = null;
        entryHits.clear();
        sourceField.setVisible(false);
        targetField.setVisible(false);

        drawNavigation(fontRenderer, mouseX, mouseY);
        if (editor != null) {
            editor.draw(fontRenderer, configurationBounds, mouseX, mouseY);
        }
        drawFooter(fontRenderer, mouseX, mouseY);
        navigationActions.drawOverlay(mouseX, mouseY);
    }


    private void configureNavigationActions() {
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> state.selected() != null,
                () -> { if (syncRuleDraft() && state.selected() != null) { syncEntryDraft();
                    navigationActions.choose("移动到分类", state.categories(), state.selected().category, value -> {
                        state.selected().category = value;
                        state.ensureCategory(value);
                        state.setSelectedCategory(value);
                        categoryNameField.setText(value);
                        rebuildEditor();
                    });
                } });
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_add", "", value -> { categoryNameField.setText(value); addCategory(); }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", state.selectedCategory(), value -> { categoryNameField.setText(value); renameCategory(); }));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !CATEGORY_ALL.equals(state.selectedCategory()), this::deleteCategory);
        navigationActions.action("category_up", "gui.modern.nav.category_up", false, false, () -> true, () -> moveCategory(-1));
        navigationActions.action("category_down", "gui.modern.nav.category_down", false, false, () -> true, () -> moveCategory(1));
        navigationActions.action("category_copy", "gui.modern.nav.category_copy", false, false, () -> true, this::copyCategoryName);
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::addRule);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> state.selected() != null, this::duplicateRule);
        navigationActions.action("delete", "gui.modern.nav.delete", true, true, () -> state.selected() != null, this::deleteRule);
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> state.selected() != null, () -> moveRule(-1));
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> state.selected() != null, () -> moveRule(1));
        navigationActions.action("copy_name", "gui.modern.nav.copy_name", false, false, () -> state.selected() != null, this::copyRuleName);
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> state.selected() != null, () -> { if (syncRuleDraft() && state.selected() != null) { syncEntryDraft(); navigationActions.prompt("gui.modern.nav.rename", state.selected().name, value -> { state.selected().name = value; rebuildEditor(); }); } });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> state.selected() != null, () -> { if (syncRuleDraft() && state.selected() != null) { syncEntryDraft(); state.selected().enabled = !state.selected().enabled; rebuildEditor(); } });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedCategories.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> collapsedCategories.addAll(state.categories()));
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationContext(int mouseX, int mouseY) {
        if (!navigationActions.inTree(mouseX, mouseY)) return false;
        if (!syncRuleDraft()) return true;
        syncEntryDraft();
        searchField.setFocused(false);
        for (RuleHit hit : ruleHits) if (hit.bounds.contains(mouseX, mouseY)) {
            if (state.selected() != hit.rule) selectRule(hit.rule);
            navigationActions.context(mouseX, mouseY, "add", "copy", "rename", "move", "copy_name", "toggle", "up", "down", "delete");
            return true;
        }
        for (CategoryHit hit : categoryHits) if (hit.bounds.contains(mouseX, mouseY)) {
            state.setSelectedCategory(hit.category);
            categoryNameField.setText(hit.category);
            navigationActions.action("fold", "gui.modern.nav.fold", false, false, () -> true,
                    () -> { if (!collapsedCategories.add(hit.category)) collapsedCategories.remove(hit.category); });
            navigationActions.context(mouseX, mouseY, "add", "category_add", "category_rename", "category_copy", "category_up", "category_down", "fold", "category_delete");
            return true;
        }
        navigationActions.context(mouseX, mouseY, "add", "category_add", "expand", "collapse");
        return true;
    }

    private void drawNavigation(FontRenderer fontRenderer, int mouseX, int mouseY) {
        configureNavigationActions();
        navigationActions.begin(fontRenderer, navigationBounds);
        ModernUiRenderer.drawSubtlePanel(navigationBounds.x, navigationBounds.y, navigationBounds.width,
                navigationBounds.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);

        searchBounds = new ModernMainLayout.Rect(navigationBounds.x + 8, navigationBounds.y + 30,
                Math.max(1, navigationBounds.width - 16), 20);
        searchField.x = searchBounds.x + 21;
        searchField.y = searchBounds.y + 4;
        searchField.width = Math.max(1, searchBounds.width - 27);
        searchField.height = 13;
        searchField.setVisible(true);
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, searchField.isFocused() ? ModernUiRenderer.ACCENT
                        : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 5, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        drawCustomTextField(fontRenderer, searchField, new ModernMainLayout.Rect(searchField.x, searchField.y,
                searchField.width, searchField.height), false);
        if (safe(searchField.getText()).trim().isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u005", searchField.x, searchField.y + 1,
                    ModernUiRenderer.MUTED_TEXT, searchField.width);
        }

        int controlsHeight = navigationBounds.bottom() - navigationActions.contentBottom();
        int contentTop = searchBounds.bottom() + 6;
        int contentBottom = Math.max(contentTop + 1, navigationBounds.bottom() - controlsHeight);
        navigationContentBounds = new ModernMainLayout.Rect(navigationBounds.x + 5, contentTop,
                Math.max(1, navigationBounds.width - 10), Math.max(1, contentBottom - contentTop));

        categoryHits.clear();
        ruleHits.clear();
        ModernUiRenderer.beginClip(navigationContentBounds);
        int y = navigationContentBounds.y - navigationScroll;
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);

        ModernMainLayout.Rect allRow = new ModernMainLayout.Rect(navigationContentBounds.x + 2, y,
                Math.max(1, navigationContentBounds.width - 18), CATEGORY_ROW_HEIGHT);
        drawNavigationRow(fontRenderer, allRow, "gui.modern.replace_wb.u006", state.selectedCategory().equals(CATEGORY_ALL),
                mouseX, mouseY, false, state.rules().size());
        if (intersects(allRow, navigationContentBounds)) {
            categoryHits.add(new CategoryHit(CATEGORY_ALL, allRow));
        }
        y = ModernTreeGuide.nextY(y, CATEGORY_ROW_HEIGHT);

        for (String category : state.categories()) {
            boolean categoryMatches = category.toLowerCase(Locale.ROOT).contains(query);
            List<BlockReplacementRule> matching = matchingRules(category, categoryMatches ? "" : query);
            if (!query.isEmpty() && !categoryMatches && matching.isEmpty()) {
                continue;
            }

            ModernMainLayout.Rect categoryRow = new ModernMainLayout.Rect(navigationContentBounds.x + 2, y,
                    Math.max(1, navigationContentBounds.width - 18), CATEGORY_ROW_HEIGHT);
            boolean collapsed = collapsedCategories.contains(category) && query.isEmpty();
            drawNavigationRow(fontRenderer, categoryRow, category, category.equalsIgnoreCase(state.selectedCategory()),
                    mouseX, mouseY, true, matching.size());
            if (intersects(categoryRow, navigationContentBounds)) {
                categoryHits.add(new CategoryHit(category, categoryRow));
            }
            y = ModernTreeGuide.nextY(y, CATEGORY_ROW_HEIGHT);

            if (!collapsed) {
                for (BlockReplacementRule rule : matching) {
                    ModernMainLayout.Rect ruleRow = ModernTreeGuide.row(navigationContentBounds.x,
                            Math.max(1, navigationContentBounds.width - 8), y, RULE_ROW_HEIGHT, 1);
                    ModernTreeGuide.drawChild(navigationContentBounds.x, 0, categoryRow, ruleRow);
                    drawRuleRow(fontRenderer, ruleRow, rule, mouseX, mouseY);
                    if (intersects(ruleRow, navigationContentBounds)) {
                        ruleHits.add(new RuleHit(rule, ruleRow));
                    }
                    y = ModernTreeGuide.nextY(y, RULE_ROW_HEIGHT);
                }
            }
        }
        ModernUiRenderer.endClip();

        navigationMaxScroll = Math.max(0, y + navigationScroll - navigationContentBounds.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        if (navigationMaxScroll > 0) {
            int trackX = navigationBounds.right() - 6;
            int trackHeight = navigationContentBounds.height;
            int contentHeight = trackHeight + navigationMaxScroll;
            int thumbHeight = Math.max(16, trackHeight * trackHeight / Math.max(1, contentHeight));
            int thumbY = navigationContentBounds.y
                    + (trackHeight - thumbHeight) * navigationScroll / Math.max(1, navigationMaxScroll);
            ModernRuleEditorUi.drawScrollbar(trackX, navigationContentBounds.y, trackHeight, thumbY, thumbHeight);
        }
        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);
        navigationActions.draw(mouseX, mouseY);
    }

    private void drawNavigationRow(FontRenderer fontRenderer, ModernMainLayout.Rect row, String label,
            boolean selected, int mouseX, int mouseY, boolean category, int count) {
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int textX = row.x + 9;
        if (category) {
            boolean collapsed = collapsedCategories.contains(label) && safe(searchField.getText()).trim().isEmpty();
            ModernUiRenderer.drawChevron(row.x + 8, row.y + 7, collapsed,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
            textX += 12;
        } else {
            ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 8,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
            textX += 12;
        }
        ModernUiRenderer.drawText(fontRenderer, label, textX, row.y + 7,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, row.width - (textX - row.x) - 34));
        ModernUiRenderer.drawText(fontRenderer, String.valueOf(count), row.right() - 24, row.y + 7,
                ModernUiRenderer.MUTED_TEXT, 18);
    }

    private void drawRuleRow(FontRenderer fontRenderer, ModernMainLayout.Rect row,
            BlockReplacementRule rule, int mouseX, int mouseY) {
        boolean selected = rule == state.selected();
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 9,
                rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawText(fontRenderer, safe(rule.name), row.x + 20, row.y + 4,
                rule.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(20, row.width - 28));
        String details = ModernFormI18n.tr(rule.hasValidRegion() ? "gui.modern.replace_wb.u007" : "gui.modern.replace_wb.u008") + " · "
                + (rule.replacements == null ? 0 : rule.replacements.size()) + " 条目";
        ModernUiRenderer.drawText(fontRenderer, details, row.x + 20, row.y + 16,
                ModernUiRenderer.MUTED_TEXT, Math.max(20, row.width - 28));
    }



    private void drawFooter(FontRenderer fontRenderer, int mouseX, int mouseY) {
        reloadBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.bottom() - 25,
                Math.max(1, Math.min(80, navigationBounds.width - 16)), 20);
        drawButton(fontRenderer, reloadBounds, "gui.modern.replace_wb.u004", false, true, false, mouseX, mouseY);
        int available = Math.max(1, editorBounds.width - 16);
        int gap = Math.min(5, Math.max(0, (available - 2) / 2));
        int buttonWidth = Math.max(1, (available - gap) / 2);
        saveBounds = new ModernMainLayout.Rect(editorBounds.right() - 8 - buttonWidth, bounds.bottom() - 25,
                buttonWidth, 20);
        revertBounds = new ModernMainLayout.Rect(saveBounds.x - gap - buttonWidth, bounds.bottom() - 25,
                buttonWidth, 20);
        drawButton(fontRenderer, revertBounds, "gui.modern.replace_wb.u020", false, true, false, mouseX, mouseY);
        drawButton(fontRenderer, saveBounds, "gui.modern.replace_wb.u021", true, true, false, mouseX, mouseY);
        String message = status.isEmpty()
                ? ModernFormI18n.tr(isDirty() ? "gui.modern.wb.fmt.rules_dirty" : "gui.modern.wb.fmt.rules_synced",
                        String.valueOf(state.rules().size()))
                : status;
        ModernUiRenderer.drawText(fontRenderer, message, editorBounds.x + 8, bounds.bottom() - 19,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, revertBounds.x - editorBounds.x - 16));
    }

    private void drawEntries(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(entriesBounds.x, entriesBounds.y, entriesBounds.width, entriesBounds.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        String title = editorRule == null ? "gui.modern.replace_wb.u022"
                : ModernFormI18n.tr("gui.modern.replace_wb.fmt.entries", String.valueOf(entryCount(editorRule)));
        ModernUiRenderer.drawText(fontRenderer, title, entriesBounds.x + 9, entriesBounds.y + 7,
                ModernUiRenderer.TEXT, Math.max(30, entriesBounds.width - 80));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u023",
                entriesBounds.x + 9, entriesBounds.y + 18, ModernUiRenderer.MUTED_TEXT,
                Math.max(30, entriesBounds.width - 18));
        ModernUiRenderer.drawDivider(entriesBounds.x + 8, entriesBounds.y + ENTRY_HEADER_HEIGHT - 2,
                Math.max(1, entriesBounds.width - 16), ModernUiRenderer.BORDER_SUBTLE);

        int editHeight = Math.min(104, Math.max(80, entriesBounds.height / 2));
        editHeight = Math.min(editHeight, Math.max(1, entriesBounds.height - ENTRY_HEADER_HEIGHT - 4));
        int listHeight = Math.max(1, entriesBounds.height - ENTRY_HEADER_HEIGHT - editHeight - 4);
        entryListBounds = new ModernMainLayout.Rect(entriesBounds.x + 6, entriesBounds.y + ENTRY_HEADER_HEIGHT,
                Math.max(1, entriesBounds.width - 12), listHeight);
        entryEditBounds = new ModernMainLayout.Rect(entriesBounds.x + 6, entryListBounds.bottom() + 4,
                Math.max(1, entriesBounds.width - 12), editHeight);

        drawEntryList(fontRenderer, mouseX, mouseY);
        drawEntryEditor(fontRenderer, mouseX, mouseY);
    }

    private void drawEntryList(FontRenderer fontRenderer, int mouseX, int mouseY) {
        entryHits.clear();
        List<BlockReplacementRule.BlockReplacementEntry> entries = editorRule == null ? null : editorRule.replacements;
        int size = entries == null ? 0 : entries.size();
        int visible = Math.max(1, entryListBounds.height / ENTRY_ROW_HEIGHT);
        entryMaxScroll = Math.max(0, size - visible);
        entryScroll = clamp(entryScroll, 0, entryMaxScroll);

        ModernUiRenderer.beginClip(entryListBounds);
        if (size == 0) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u024", entryListBounds.x + 7,
                    entryListBounds.y + 8, ModernUiRenderer.MUTED_TEXT, Math.max(20, entryListBounds.width - 14));
        } else {
            for (int i = 0; i < visible; i++) {
                int index = i + entryScroll;
                if (index >= size) {
                    break;
                }
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(entryListBounds.x + 2,
                        entryListBounds.y + i * ENTRY_ROW_HEIGHT, Math.max(1, entryListBounds.width - 20), 20);
                BlockReplacementRule.BlockReplacementEntry entry = entries.get(index);
                boolean selected = index == selectedEntryIndex;
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                                : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(fontRenderer, "≡", row.x + 5, row.y + 6,
                        entry != null && entry.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, 12);
                String source = entry == null || isBlank(entry.sourceBlockId) ? "?" : entry.sourceBlockId;
                String target = entry == null || isBlank(entry.targetBlockId) ? "?" : entry.targetBlockId;
                ModernUiRenderer.drawText(fontRenderer, (index + 1) + ". " + source + " -> " + target, row.x + 18,
                        row.y + 6, selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(20, row.width - 24));
                if (intersects(row, entryListBounds)) {
                    entryHits.add(new EntryHit(index, row));
                }
            }
        }
        ModernUiRenderer.endClip();

        if (entryMaxScroll > 0) {
            int trackX = entryListBounds.right() - 4;
            int trackHeight = entryListBounds.height;
            int contentHeight = trackHeight + entryMaxScroll * ENTRY_ROW_HEIGHT;
            int thumbHeight = Math.max(14, trackHeight * trackHeight / Math.max(1, contentHeight));
            int thumbY = entryListBounds.y
                    + (trackHeight - thumbHeight) * entryScroll / Math.max(1, entryMaxScroll);
            entryScrollbarBounds = new ModernMainLayout.Rect(trackX - 5, entryListBounds.y, 10, trackHeight);
            entryScrollbarThumbBounds = new ModernMainLayout.Rect(trackX - 4, thumbY, 8, thumbHeight);
            ModernRuleEditorUi.drawScrollbar(trackX, entryListBounds.y, trackHeight, thumbY, thumbHeight);
        } else {
            entryScrollbarBounds = null;
            entryScrollbarThumbBounds = null;
        }
    }

    private void drawEntryEditor(FontRenderer fontRenderer, int mouseX, int mouseY) {
        sourceBounds = null;
        targetBounds = null;
        pickSourceBounds = null;
        pickTargetBounds = null;
        entryEnabledBounds = null;
        entryAddBounds = null;
        entryDuplicateBounds = null;
        entryDeleteBounds = null;
        entryApplyBounds = null;

        BlockReplacementRule.BlockReplacementEntry entry = selectedEntry();
        if (entry == null) {
            hideField(sourceField);
            hideField(targetField);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u025", entryEditBounds.x + 8,
                    entryEditBounds.y + 8, ModernUiRenderer.MUTED_TEXT, Math.max(20, entryEditBounds.width - 16));
            if (entryEditBounds.height < 40) {
                return;
            }
            int actionY = Math.max(entryEditBounds.y + 24, entryEditBounds.bottom() - 22);
            ModernMainLayout.Rect[] actions = actionRow(entryEditBounds.x + 8, entryEditBounds.right() - 8, actionY, 1,
                    18);
            entryAddBounds = actions[0];
            drawButton(fontRenderer, entryAddBounds, "gui.modern.replace_wb.u017", true, editorRule != null, false, mouseX, mouseY);
            return;
        }

        if (entryEditBounds.height < 76) {
            hideField(sourceField);
            hideField(targetField);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u026", entryEditBounds.x + 8,
                    entryEditBounds.y + 8, ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, entryEditBounds.width - 16));
            return;
        }

        int titleWidth = Math.max(24, entryEditBounds.width - 60);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u027", entryEditBounds.x + 8, entryEditBounds.y + 6,
                ModernUiRenderer.SUBTLE_TEXT, titleWidth);
        entryEnabledBounds = new ModernMainLayout.Rect(entryEditBounds.right() - 38, entryEditBounds.y + 5, 30, 14);
        ModernUiRenderer.drawToggle(entryEnabledBounds.x, entryEnabledBounds.y, entryEnabledBounds.width,
                entryEnabledBounds.height, entry.enabled, entryEnabledBounds.contains(mouseX, mouseY));

        int sourceY = entryEditBounds.y + 17;
        int targetY = sourceY + 21;
        int inputRight = entryEditBounds.right() - 8;
        int pickerWidth = Math.min(42, Math.max(26, entryEditBounds.width / 4));
        int fieldX = entryEditBounds.x + 27;
        int fieldWidth = Math.max(1, inputRight - pickerWidth - 4 - fieldX);
        sourceBounds = new ModernMainLayout.Rect(fieldX, sourceY, fieldWidth, 18);
        pickSourceBounds = new ModernMainLayout.Rect(inputRight - pickerWidth, sourceY, pickerWidth, 18);
        targetBounds = new ModernMainLayout.Rect(fieldX, targetY, fieldWidth, 18);
        pickTargetBounds = new ModernMainLayout.Rect(inputRight - pickerWidth, targetY, pickerWidth, 18);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u028", entryEditBounds.x + 8, sourceY + 5,
                ModernUiRenderer.MUTED_TEXT, 16);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.replace_wb.u029", entryEditBounds.x + 8, targetY + 5,
                ModernUiRenderer.MUTED_TEXT, 19);
        drawCustomTextField(fontRenderer, sourceField, sourceBounds,
                !entryValidation.isEmpty() && isBlank(safe(sourceField.getText())));
        drawCustomTextField(fontRenderer, targetField, targetBounds,
                !entryValidation.isEmpty() && isBlank(safe(targetField.getText())));
        drawButton(fontRenderer, pickSourceBounds, "gui.modern.replace_wb.u030", false, true, false, mouseX, mouseY);
        drawButton(fontRenderer, pickTargetBounds, "gui.modern.replace_wb.u030", false, true, false, mouseX, mouseY);

        int actionY = Math.max(targetBounds.bottom() + 2, entryEditBounds.bottom() - 20);
        ModernMainLayout.Rect[] actions = actionRow(entryEditBounds.x + 8, entryEditBounds.right() - 8, actionY, 4,
                18);
        entryAddBounds = actions[0];
        entryDuplicateBounds = actions[1];
        entryDeleteBounds = actions[2];
        entryApplyBounds = actions[3];
        drawButton(fontRenderer, entryAddBounds, "gui.modern.replace_wb.u017", true, true, false, mouseX, mouseY);
        drawButton(fontRenderer, entryDuplicateBounds, "gui.modern.replace_wb.u018", false, true, false, mouseX, mouseY);
        drawButton(fontRenderer, entryDeleteBounds,
                pendingEntryDelete == entry ? "gui.modern.replace_wb.u012" : "gui.modern.replace_wb.u031", false, true, pendingEntryDelete == entry,
                mouseX, mouseY);
        drawButton(fontRenderer, entryApplyBounds, "gui.modern.replace_wb.u032", false, true, false, mouseX, mouseY);
    }

    private void drawCustomTextField(FontRenderer fontRenderer, GuiTextField field, ModernMainLayout.Rect rect,
            boolean invalid) {
        if (field == null || rect == null || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        field.x = rect.x;
        field.y = rect.y;
        field.width = rect.width;
        field.height = rect.height;
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawSubtlePanel(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, 4,
                0xFF101820, invalid ? 0xFFE06A78
                        : field.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void hideField(GuiTextField field) {
        if (field != null) {
            field.setVisible(false);
            field.setFocused(false);
        }
    }

    private void drawButton(FontRenderer fontRenderer, ModernMainLayout.Rect rect, String label, boolean primary,
            boolean enabled, boolean danger, int mouseX, int mouseY) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = 0xFF141D25;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.MUTED_TEXT;
        } else if (danger) {
            fill = hovered ? 0xFFF29A78 : 0xFFD96A52;
            border = hovered ? 0xFFFFC0A7 : 0xFFD96A52;
            text = ModernUiRenderer.SHELL;
        } else if (primary) {
            fill = hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT;
            border = hovered ? 0xFFFFB0C4 : ModernUiRenderer.ACCENT;
            text = ModernUiRenderer.SHELL;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
            text = enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, rect.x + 5,
                rect.y + Math.max(3, (rect.height - fontRenderer.FONT_HEIGHT) / 2), text,
                Math.max(1, rect.width - 10));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (navigationActions.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (mouseButton == 1 && navigationContext(mouseX, mouseY)) return true;
        if (mouseButton != 0) {
            return editor != null && contains(configurationBounds, mouseX, mouseY)
                    && editor.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }
        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            clearPendingDeletes();
            clearCustomFocus();
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (categoryNameBounds != null && categoryNameBounds.contains(mouseX, mouseY)) {
            clearCustomFocus();
            categoryNameField.setFocused(true);
            categoryNameField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (handleNavigationAction(mouseX, mouseY)) {
            return true;
        }
        for (CategoryHit hit : categoryHits) {
            if (navigationContentBounds != null && navigationContentBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                selectCategory(hit.category);
                return true;
            }
        }
        for (RuleHit hit : ruleHits) {
            if (navigationContentBounds != null && navigationContentBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                if (ruleToggle.doubleClicked(hit.rule)) {
                    if (!syncRuleDraft()) {
                        return true;
                    }
                    syncEntryDraft();
                    state.select(hit.rule);
                    hit.rule.enabled = !hit.rule.enabled;
                    rebuildEditor();
                    status = ModernFormI18n.tr(hit.rule.enabled ? "gui.modern.wb.fmt.enabled" : "gui.modern.wb.fmt.disabled",
                            safe(hit.rule.name));
                    return true;
                }
                selectRule(hit.rule);
                return true;
            }
        }
        if (configurationBounds != null && configurationBounds.contains(mouseX, mouseY)) {
            clearCustomFocus();
            return editor != null && editor.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (entriesBounds != null && entriesBounds.contains(mouseX, mouseY)) {
            return handleEntryClick(mouseX, mouseY);
        }
        return true;
    }

    private boolean handleNavigationAction(int mouseX, int mouseY) {
        if (contains(addCategoryBounds, mouseX, mouseY)) {
            addCategory();
            return true;
        }
        if (contains(renameCategoryBounds, mouseX, mouseY)) {
            renameCategory();
            return true;
        }
        if (contains(deleteCategoryBounds, mouseX, mouseY)) {
            deleteCategory();
            return true;
        }
        if (contains(moveCategoryUpBounds, mouseX, mouseY)) {
            moveCategory(-1);
            return true;
        }
        if (contains(moveCategoryDownBounds, mouseX, mouseY)) {
            moveCategory(1);
            return true;
        }
        if (contains(copyCategoryNameBounds, mouseX, mouseY)) {
            copyCategoryName();
            return true;
        }
        if (contains(addRuleBounds, mouseX, mouseY)) {
            addRule();
            return true;
        }
        if (contains(duplicateRuleBounds, mouseX, mouseY)) {
            duplicateRule();
            return true;
        }
        if (contains(deleteRuleBounds, mouseX, mouseY)) {
            deleteRule();
            return true;
        }
        if (contains(moveRuleUpBounds, mouseX, mouseY)) {
            moveRule(-1);
            return true;
        }
        if (contains(moveRuleDownBounds, mouseX, mouseY)) {
            moveRule(1);
            return true;
        }
        if (contains(copyRuleNameBounds, mouseX, mouseY)) {
            copyRuleName();
            return true;
        }
        if (contains(reloadBounds, mouseX, mouseY)) {
            reloadFromSource();
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            save();
            return true;
        }
        if (contains(revertBounds, mouseX, mouseY)) {
            discardDraft();
            status = "gui.modern.replace_wb.u033";
            return true;
        }
        return false;
    }

    private boolean handleEntryClick(int mouseX, int mouseY) {
        if (entryScrollbarBounds != null && entryScrollbarBounds.contains(mouseX, mouseY)) {
            draggingEntryScrollbar = entryMaxScroll > 0;
            if (draggingEntryScrollbar) {
                updateEntryScrollFromMouse(mouseY);
            }
            return true;
        }
        if (contains(sourceBounds, mouseX, mouseY)) {
            focusEntryField(sourceField, targetField, mouseX, mouseY);
            entryValidation = "";
            return true;
        }
        if (contains(targetBounds, mouseX, mouseY)) {
            focusEntryField(targetField, sourceField, mouseX, mouseY);
            entryValidation = "";
            return true;
        }
        if (contains(pickSourceBounds, mouseX, mouseY)) {
            pickSourceBlock();
            return true;
        }
        if (contains(pickTargetBounds, mouseX, mouseY)) {
            pickTargetBlock();
            return true;
        }
        if (contains(entryEnabledBounds, mouseX, mouseY)) {
            if (selectedEntry() != null) {
                syncEntryDraft();
                selectedEntry().enabled = !selectedEntry().enabled;
                BlockReplacementHandler.markRuleDirty(editorRule);
            }
            return true;
        }
        if (contains(entryAddBounds, mouseX, mouseY)) {
            addEntry();
            return true;
        }
        if (contains(entryDuplicateBounds, mouseX, mouseY)) {
            duplicateEntry();
            return true;
        }
        if (contains(entryDeleteBounds, mouseX, mouseY)) {
            deleteEntry();
            return true;
        }
        if (contains(entryApplyBounds, mouseX, mouseY)) {
            applyEntry();
            return true;
        }
        for (EntryHit hit : entryHits) {
            if (contains(entryListBounds, mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                selectEntry(hit.index);
                return true;
            }
        }
        clearCustomFocus();
        return true;
    }

    private void focusEntryField(GuiTextField field, GuiTextField other, int mouseX, int mouseY) {
        if (other != null) {
            other.setFocused(false);
        }
        if (field != null) {
            field.setFocused(true);
            field.mouseClicked(mouseX, mouseY, 0);
        }
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return handleEscape();
        }
        if (keyCode == Keyboard.KEY_F
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            if (searchField != null) {
                clearCustomFocus();
                searchField.setFocused(true);
            }
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            boolean handled = searchField.textboxKeyTyped(typedChar, keyCode);
            if (handled) {
                navigationScroll = 0;
            }
            return true;
        }
        if (categoryNameField != null && categoryNameField.isFocused()) {
            categoryNameField.textboxKeyTyped(typedChar, keyCode);
            return true;
        }
        if (sourceField != null && sourceField.getVisible() && sourceField.isFocused()) {
            sourceField.textboxKeyTyped(typedChar, keyCode);
            entryValidation = "";
            return true;
        }
        if (targetField != null && targetField.getVisible() && targetField.isFocused()) {
            targetField.textboxKeyTyped(typedChar, keyCode);
            entryValidation = "";
            return true;
        }
        return editor != null && editor.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingNavigationDivider && clickedMouseButton == 0 && bounds != null) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, 150, 260, 120, 180);
            navigationRatio = split.ratio;
            return true;
        }
        if (draggingEntryScrollbar && clickedMouseButton == 0) {
            updateEntryScrollFromMouse(mouseY);
            return true;
        }
        return editor != null && editor.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0 && draggingNavigationDivider) {
            MainUiLayoutManager.setModernSplitRatio("rules.block_replacement.navigation", navigationRatio);
            draggingNavigationDivider = false;
            return true;
        }
        if (button == 0 && draggingEntryScrollbar) {
            draggingEntryScrollbar = false;
            return true;
        }
        return editor != null && editor.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (wheel == 0) {
            return false;
        }
        int before = navigationScroll;
        navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll);
        return before != navigationScroll;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (navigationActions.wheel(wheel)) return true;
        if (wheel == 0) {
            return false;
        }
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            int before = navigationScroll;
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll);
            return before != navigationScroll;
        }
        if (entryListBounds != null && entryListBounds.contains(mouseX, mouseY)) {
            int before = entryScroll;
            entryScroll = clamp(entryScroll + (wheel > 0 ? -1 : 1), 0, entryMaxScroll);
            return before != entryScroll;
        }
        if (configurationBounds != null && configurationBounds.contains(mouseX, mouseY)) {
            return editor != null && editor.handleMouseWheel(wheel, mouseX, mouseY);
        }
        return false;
    }

    private void updateEntryScrollFromMouse(int mouseY) {
        if (!draggingEntryScrollbar || entryScrollbarBounds == null || entryScrollbarThumbBounds == null
                || entryMaxScroll <= 0) {
            return;
        }
        int travel = Math.max(1, entryScrollbarBounds.height - entryScrollbarThumbBounds.height);
        int target = clamp(mouseY - entryScrollbarThumbBounds.height / 2, entryScrollbarBounds.y,
                entryScrollbarBounds.y + travel);
        entryScroll = Math.round((target - entryScrollbarBounds.y) * entryMaxScroll / (float) travel);
        entryScroll = clamp(entryScroll, 0, entryMaxScroll);
    }

    @Override
    public boolean handleEscape() {
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (draggingNavigationDivider) {
            draggingNavigationDivider = false;
            return true;
        }
        if (draggingEntryScrollbar) {
            draggingEntryScrollbar = false;
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
            return true;
        }
        if (categoryNameField != null && categoryNameField.isFocused()) {
            categoryNameField.setFocused(false);
            return true;
        }
        if (sourceField != null && sourceField.getVisible() && sourceField.isFocused() || targetField != null && targetField.getVisible() && targetField.isFocused()) {
            clearCustomFocus();
            return true;
        }
        return editor != null && editor.handleEscape();
    }

    @Override
    public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true;
        return searchField != null && searchField.isFocused()
                || categoryNameField != null && categoryNameField.isFocused()
                || sourceField != null && sourceField.getVisible() && sourceField.isFocused()
                || targetField != null && targetField.getVisible() && targetField.isFocused()
                || editor != null && editor.isTextInputFocused();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return editor == null ? "" : editor.getHoveredTooltip(mouseX, mouseY);
    }

    @Override
    public boolean isDirty() {
        return state.isDirty() || editor != null && editor.isDirty() || hasPendingEntryEdits()
                || hasPendingCategoryName();
    }

    @Override
    public void save() {
        if (!syncRuleDraft()) {
            status = "gui.modern.replace_wb.u034";
            return;
        }
        syncEntryDraft();
        String validation = validateRulesForSave();
        if (!isBlank(validation)) {
            status = validation;
            return;
        }
        try {
            if (formEditor != null) {
                formEditor.save();
            }
            state.ensureAllCategories();
            persistDraftToHandler();
            state.markCommitted();
            clearCustomFocus();
            setTextIfDifferent(categoryNameField, isConcreteCategory(state.selectedCategory())
                    ? state.selectedCategory() : "");
            status = "gui.modern.replace_wb.u035";
            pendingDelete = null;
            pendingCategoryDelete = null;
            pendingEntryDelete = null;
            pendingReload = false;
        } catch (RuntimeException exception) {
            status = ModernFormI18n.tr("gui.modern.replace_wb.u036") + safeMessage(exception);
        }
    }

    private String validateRulesForSave() {
        for (int ruleIndex = 0; ruleIndex < state.rules().size(); ruleIndex++) {
            BlockReplacementRule rule = state.rules().get(ruleIndex);
            if (isBlank(rule.name)) {
                return "第 " + (ruleIndex + 1) + " 条规则名称不能为空";
            }
            if (rule.replacements == null) {
                continue;
            }
            for (int entryIndex = 0; entryIndex < rule.replacements.size(); entryIndex++) {
                BlockReplacementRule.BlockReplacementEntry entry = rule.replacements.get(entryIndex);
                if (entry == null || isBlank(entry.sourceBlockId) || isBlank(entry.targetBlockId)) {
                    return ModernFormI18n.tr("gui.modern.replace_wb.u037") + safe(rule.name) + "”的第 " + (entryIndex + 1)
                            + " 条目需要填写源方块和目标方块";
                }
            }
        }
        return "";
    }

    private void persistDraftToHandler() {
        List<String> existingCategories = BlockReplacementHandler.getCategoriesSnapshot();
        BlockReplacementHandler.rules.clear();
        for (BlockReplacementRule rule : state.rules()) {
            BlockReplacementRule copy = copyRule(rule);
            copy.dirty = true;
            BlockReplacementHandler.rules.add(copy);
        }

        // replaceCategoryOrder intentionally retains categories owned by the
        // handler. Remove stale names first so a draft deletion is persistent.
        for (String category : existingCategories) {
            if (!state.hasCategory(category)) {
                BlockReplacementHandler.deleteCategory(category);
            }
        }
        BlockReplacementHandler.replaceCategoryOrder(new ArrayList<String>(state.categories()));
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        state.discard();
        selectedEntryIndex = -1;
        entryScroll = 0;
        entryValidation = "";
        clearPendingDeletes();
        pendingReload = false;
        clearCustomFocus();
        if (state.selected() == null) {
            state.setSelectedCategory(CATEGORY_ALL);
        } else {
            state.setSelectedCategory(normalizeCategory(state.selected().category));
        }
        setTextIfDifferent(categoryNameField, isConcreteCategory(state.selectedCategory())
                ? state.selectedCategory() : "");
        rebuildEditor();
    }

    private void reloadFromSource() {
        if (isDirty() && !pendingReload) {
            pendingReload = true;
            status = "gui.modern.replace_wb.u038";
            return;
        }
        try {
            BlockReplacementHandler.loadConfig();
            state.reload(BlockReplacementHandler.rules, BlockReplacementHandler.getCategoriesSnapshot());
            selectedEntryIndex = -1;
            entryScroll = 0;
            entryValidation = "";
            clearPendingDeletes();
            pendingReload = false;
            rebuildEditor();
            status = "gui.modern.replace_wb.u039";
        } catch (RuntimeException exception) {
            pendingReload = false;
            status = ModernFormI18n.tr("gui.modern.replace_wb.u040") + safeMessage(exception);
        }
    }

    private void ensureEditor(FontRenderer fontRenderer) {
        if (editorRule == state.selected() && editor != null) {
            return;
        }
        rebuildEditor();
    }

    private void rebuildEditor() {
        editorRule = state.selected();
        selectedEntryIndex = editorRule == null || editorRule.replacements == null || editorRule.replacements.isEmpty()
                ? -1
                : clamp(selectedEntryIndex, 0, editorRule.replacements.size() - 1);
        if (editorRule != null && selectedEntryIndex < 0 && editorRule.replacements != null
                && !editorRule.replacements.isEmpty()) {
            selectedEntryIndex = 0;
        }
        corner1Draft = formatCorner(editorRule, true);
        corner2Draft = formatCorner(editorRule, false);
        entryValidation = "";
        if (editorRule == null) {
            formEditor = null;
            editor = ModernFormSettingsTab.builder("gui.modern.replace_wb.u041", "gui.modern.replace_wb.u042", "")
                    .footerVisible(false)
                    .section("gui.modern.replace_wb.u043", "gui.modern.replace_wb.u044")
                    .readOnly("gui.modern.replace_wb.u045", "gui.modern.replace_wb.u046", "gui.modern.replace_wb.u047")
                    .build();
        } else {
            formEditor = buildEditor(editorRule);
            editor = formEditor;
        }
        loadEntryFields();
    }

    private ModernFormSettingsTab<BlockReplacementRule> buildEditor(final BlockReplacementRule rule) {
        return ModernFormSettingsTab.builder(safe(rule.name), "gui.modern.replace_wb.u048",
                "gui.modern.replace_wb.u049", adapter(rule))
                .footerVisible(false)
                .headerToggle(bool(() -> rule.enabled, value -> rule.enabled = value), "gui.modern.replace_wb.u062")
                .section("gui.modern.replace_wb.u050", "gui.modern.replace_wb.u051")
                .text("gui.modern.replace_wb.u052", "gui.modern.replace_wb.u053",
                        text(() -> rule.name, value -> rule.name = value), "gui.modern.replace_wb.u052", 120)
                .choice("gui.modern.replace_wb.u054", "gui.modern.replace_wb.u055",
                        new ModernFormSettingsTab.ChoiceValue<String>() {
                            @Override public String get() { return rule.category; }
                            @Override public void set(String value) { rule.category = value; }
                        }, ModernFormSettingsTab.stringOptions(state.categories()))
                .readOnly("gui.modern.replace_wb.u056", "gui.modern.replace_wb.u057",
                        new ModernFormSettingsTab.ReadOnlyValue() {
                            @Override
                            public String get() {
                                int index = state.indexOf(rule);
                                return index < 0 ? "gui.modern.replace_wb.u058" : "第 " + (index + 1) + " 项（列表顺序）";
                            }
                        })
                .section("gui.modern.replace_wb.u059", "gui.modern.replace_wb.u060")
                .readOnly("gui.modern.replace_wb.u063", "gui.modern.replace_wb.u064",
                        new ModernFormSettingsTab.ReadOnlyValue() {
                            @Override
                            public String get() {
                                return conditionSummary(rule);
                            }
                        })
                .section("gui.modern.replace_wb.u065", "gui.modern.replace_wb.u066")
                .text("gui.modern.replace_wb.u067", "gui.modern.replace_wb.u068",
                        text(() -> corner1Draft, value -> corner1Draft = safe(value)), "x, y, z", 80)
                .text("gui.modern.replace_wb.u069", "gui.modern.replace_wb.u068",
                        text(() -> corner2Draft, value -> corner2Draft = safe(value)), "x, y, z", 80)
                .readOnly("gui.modern.replace_wb.u070", "gui.modern.replace_wb.u071",
                        new ModernFormSettingsTab.ReadOnlyValue() {
                            @Override
                            public String get() {
                                return regionSummary(rule);
                            }
                        })
                .action("gui.modern.replace_wb.u072", "gui.modern.replace_wb.u073", "gui.modern.replace_wb.u074",
                        ModernFormSettingsTab.ActionStyle.SECONDARY, tab -> pickRegion())
                .action("gui.modern.replace_wb.u075", "gui.modern.replace_wb.u076", "gui.modern.replace_wb.u077",
                        ModernFormSettingsTab.ActionStyle.SECONDARY, "gui.modern.replace_wb.u078", tab -> scanAvailableBlocks())
                .readOnly("gui.modern.replace_wb.u079", "gui.modern.replace_wb.u080",
                        new ModernFormSettingsTab.ReadOnlyValue() {
                            @Override
                            public String get() {
                                return availableBlocksSummary(rule);
                            }
                        })
                .section("gui.modern.replace_wb.u081", "gui.modern.replace_wb.u082")
                .toggle("gui.modern.replace_wb.u083", "gui.modern.replace_wb.u084",
                        bool(() -> rule.highlightReplacedBlocks, value -> rule.highlightReplacedBlocks = value))
                .toggle("gui.modern.replace_wb.u085", "gui.modern.replace_wb.u086",
                        bool(() -> rule.useSolidCollision, value -> rule.useSolidCollision = value))
                .section("替换条目", "在卡片列表中管理原方块和目标方块。")
                .custom(new com.zszl.zszlScriptMod.gui.modern.form.ModernFormWidget() {
                    public int height(int width, int available) { return Math.max(220, available - 32); }
                    public void draw(FontRenderer font, ModernMainLayout.Rect b, int x, int y) {
                        entriesBounds = b;
                        drawEntries(font, x, y);
                    }
                    public boolean mouseClicked(int x, int y, int button) {
                        if (button == 0 && contains(entryListBounds, x, y)) {
                            for (EntryHit hit : entryHits) if (hit.bounds.contains(x, y) && x < hit.bounds.x + 18) {
                                syncEntryDraft(); draggedEntry = hit.index; selectedEntryIndex = hit.index;
                                loadEntryFields(); return true;
                            }
                        }
                        if (button == 1 && contains(entryListBounds, x, y)) {
                            for (EntryHit hit : entryHits) if (hit.bounds.contains(x, y)) {
                                syncEntryDraft(); selectedEntryIndex = hit.index; loadEntryFields();
                                navigationActions.action("entry_delete", "删除条目", true, true,
                                        () -> selectedEntry() != null, () -> { pendingEntryDelete = selectedEntry(); deleteEntry(); });
                                navigationActions.context(x, y, "entry_delete");
                                break;
                            }
                            return true;
                        }
                        return button == 0 && handleEntryClick(x, y);
                    }
                    public boolean mouseClickMove(int x, int y, int button, long elapsed) {
                        if (button != 0 || draggedEntry < 0 || entryListBounds == null) return false;
                        if (y < entryListBounds.y + 8) entryScroll = Math.max(0, entryScroll - 1);
                        if (y > entryListBounds.bottom() - 8) entryScroll = Math.min(entryMaxScroll, entryScroll + 1);
                        int target = clamp(entryScroll + (y - entryListBounds.y) / ENTRY_ROW_HEIGHT, 0, rule.replacements.size()-1);
                        if (target != draggedEntry && draggedEntry < rule.replacements.size()) {
                            rule.replacements.add(target, rule.replacements.remove(draggedEntry));
                            draggedEntry = target; selectedEntryIndex = target;
                            BlockReplacementHandler.markRuleDirty(rule);
                        }
                        return true;
                    }
                    public boolean mouseReleased(int x, int y, int button) {
                        boolean handled = draggedEntry >= 0; draggedEntry = -1; return handled;
                    }
                    public boolean commit() { syncEntryDraft(); return entryValidation.isEmpty(); }
                    public void blur() { clearCustomFocus(); draggedEntry = -1; }
                    public Object snapshot() { return STATE_GSON.toJson(rule.replacements); }
                })
                .build().sectionPages(editorPositions.computeIfAbsent(rule,
                        key -> new com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState()));
    }

    private ModernFormSettingsTab.StateAdapter<BlockReplacementRule> adapter(final BlockReplacementRule rule) {
        return new ModernFormSettingsTab.StateAdapter<BlockReplacementRule>() {
            @Override
            public void load() {
            }

            @Override
            public BlockReplacementRule capture() {
                return copyRule(rule);
            }

            @Override
            public BlockReplacementRule copy(BlockReplacementRule value) {
                return copyRule(value);
            }

            @Override
            public void restore(BlockReplacementRule value) {
                copyRuleValues(rule, value);
            }

            @Override
            public void save() {
                normalizeRule(rule);
                state.ensureCategory(rule.category);
            }

            @Override
            public void restoreDefaults() {
                copyRuleValues(rule, new BlockReplacementRule());
                corner1Draft = "";
                corner2Draft = "";
            }

            @Override
            public BlockReplacementRule createDefaults() {
                return new BlockReplacementRule();
            }
        };
    }

    private boolean syncRuleDraft() {
        if (formEditor == null || editorRule == null) {
            return true;
        }
        if (!formEditor.tryApplyDraftValues()) {
            return false;
        }
        if (!parseCorner(corner1Draft, editorRule, true)) {
            formEditor.showStatus("gui.modern.replace_wb.u087");
            return false;
        }
        if (!parseCorner(corner2Draft, editorRule, false)) {
            formEditor.showStatus("gui.modern.replace_wb.u088");
            return false;
        }
        editorRule.name = safe(editorRule.name).trim();
        editorRule.category = normalizeCategory(editorRule.category);
        state.ensureCategory(editorRule.category);
        state.setSelectedCategory(editorRule.category);
        if (categoryNameField != null && !categoryNameField.isFocused()) {
            setTextIfDifferent(categoryNameField, editorRule.category);
        }
        BlockReplacementHandler.markRuleDirty(editorRule);
        return true;
    }

    private void syncEntryDraft() {
        BlockReplacementRule.BlockReplacementEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        String source = sourceField == null ? safe(entry.sourceBlockId) : safe(sourceField.getText()).trim();
        String target = targetField == null ? safe(entry.targetBlockId) : safe(targetField.getText()).trim();
        if (!source.equals(safe(entry.sourceBlockId)) || !target.equals(safe(entry.targetBlockId))) {
            entry.sourceBlockId = source;
            entry.targetBlockId = target;
            BlockReplacementHandler.markRuleDirty(editorRule);
        }
    }

    private boolean parseCorner(String value, BlockReplacementRule rule, boolean first) {
        if (isBlank(value)) {
            if (first) {
                rule.corner1X = null;
                rule.corner1Y = null;
                rule.corner1Z = null;
                corner1Draft = "";
            } else {
                rule.corner2X = null;
                rule.corner2Y = null;
                rule.corner2Z = null;
                corner2Draft = "";
            }
            return true;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return false;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            if (first) {
                rule.setCorner1(x, y, z);
                corner1Draft = formatCorner(rule, true);
            } else {
                rule.setCorner2(x, y, z);
                corner2Draft = formatCorner(rule, false);
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void selectCategory(String category) {
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        clearPendingDeletes();
        String normalized = normalizeCategory(category);
        if (CATEGORY_ALL.equals(category)) {
            state.setSelectedCategory(CATEGORY_ALL);
            setTextIfDifferent(categoryNameField, "");
            return;
        }
        if (!collapsedCategories.add(normalized)) {
            collapsedCategories.remove(normalized);
        }
        state.setSelectedCategory(normalized);
        setTextIfDifferent(categoryNameField, normalized);
        if (state.selected() == null || !normalized.equalsIgnoreCase(normalizeCategory(state.selected().category))) {
            BlockReplacementRule first = state.firstInCategory(normalized);
            if (first != null) {
                state.select(first);
                rebuildEditor();
            } else if (state.selected() != null) {
                state.clearSelection();
                rebuildEditor();
            }
        }
    }

    private void selectRule(BlockReplacementRule rule) {
        if (rule == null) {
            return;
        }
        if (rule == state.selected()) {
            state.setSelectedCategory(normalizeCategory(rule.category));
            setTextIfDifferent(categoryNameField, normalizeCategory(rule.category));
            clearPendingDeletes();
            return;
        }
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        state.select(rule);
        state.setSelectedCategory(normalizeCategory(rule.category));
        setTextIfDifferent(categoryNameField, normalizeCategory(rule.category));
        clearPendingDeletes();
        entryScroll = 0;
        rebuildEditor();
        status = ModernFormI18n.tr("gui.modern.wb.fmt.selected", safe(rule.name));
    }

    private void addRule() {
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        String category = isConcreteCategory(state.selectedCategory()) ? state.selectedCategory() : CATEGORY_DEFAULT;
        BlockReplacementRule rule = state.addRule(category);
        state.setSelectedCategory(category);
        collapsedCategories.remove(category);
        clearPendingDeletes();
        rebuildEditor();
        status = ModernFormI18n.tr("gui.modern.replace_wb.u089") + safe(rule.name);
    }

    private void duplicateRule() {
        if (state.selected() == null) {
            status = "gui.modern.replace_wb.u090";
            return;
        }
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        BlockReplacementRule copy = state.duplicateSelected();
        if (copy == null) {
            status = "gui.modern.replace_wb.u091";
            return;
        }
        state.setSelectedCategory(normalizeCategory(copy.category));
        clearPendingDeletes();
        rebuildEditor();
        status = ModernFormI18n.tr("gui.modern.replace_wb.u092") + safe(copy.name);
    }

    private void deleteRule() {
        BlockReplacementRule selected = state.selected();
        if (selected == null) {
            status = "gui.modern.replace_wb.u090";
            return;
        }
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        if (pendingDelete != selected) {
            pendingDelete = selected;
            status = "gui.modern.replace_wb.u093";
            return;
        }
        state.deleteSelected();
        pendingDelete = null;
        selectedEntryIndex = -1;
        if (state.selected() == null) {
            state.setSelectedCategory(CATEGORY_ALL);
        } else {
            state.setSelectedCategory(normalizeCategory(state.selected().category));
        }
        rebuildEditor();
        status = "gui.modern.replace_wb.u094";
    }

    private void moveRule(int direction) {
        if (state.selected() == null || !syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        if (state.moveSelectedRule(direction)) {
            status = "gui.modern.replace_wb.u095";
            rebuildEditor();
        }
    }

    private void copyRuleName() {
        BlockReplacementRule selected = state.selected();
        if (selected == null || isBlank(selected.name)) {
            status = "gui.modern.replace_wb.u096";
            return;
        }
        status = copyToClipboard(selected.name) ? "gui.modern.replace_wb.u097" : "gui.modern.replace_wb.u098";
    }

    private void addCategory() {
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        String name = categoryNameField == null ? "" : safe(categoryNameField.getText()).trim();
        if (name.isEmpty()) {
            status = "gui.modern.replace_wb.u099";
            return;
        }
        if (state.addCategory(name)) {
            state.setSelectedCategory(name);
            collapsedCategories.remove(name);
            state.clearSelection();
            selectedEntryIndex = -1;
            rebuildEditor();
            setTextIfDifferent(categoryNameField, name);
            status = ModernFormI18n.tr("gui.modern.replace_wb.u100") + name;
        } else {
            status = "gui.modern.replace_wb.u101";
        }
    }

    private void renameCategory() {
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        String oldName = state.selectedCategory();
        String newName = categoryNameField == null ? "" : safe(categoryNameField.getText()).trim();
        if (!isConcreteCategory(oldName)) {
            status = "gui.modern.replace_wb.u102";
            return;
        }
        if (newName.isEmpty()) {
            status = "gui.modern.replace_wb.u103";
            return;
        }
        if (state.renameCategory(oldName, newName)) {
            collapsedCategories.remove(oldName);
            state.setSelectedCategory(newName);
            setTextIfDifferent(categoryNameField, newName);
            if (state.selected() != null) {
                rebuildEditor();
            }
            status = "gui.modern.replace_wb.u104";
        } else {
            status = "gui.modern.replace_wb.u105";
        }
    }

    private void deleteCategory() {
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        String category = state.selectedCategory();
        if (!isConcreteCategory(category)) {
            status = "gui.modern.replace_wb.u102";
            return;
        }
        if (pendingCategoryDelete == null || !pendingCategoryDelete.equalsIgnoreCase(category)) {
            pendingCategoryDelete = category;
            status = "gui.modern.replace_wb.u106";
            return;
        }
        if (state.deleteCategory(category)) {
            pendingCategoryDelete = null;
            collapsedCategories.remove(category);
            state.setSelectedCategory(CATEGORY_ALL);
            setTextIfDifferent(categoryNameField, "");
            if (state.selected() != null) {
                rebuildEditor();
            }
            status = "gui.modern.replace_wb.u107";
        } else {
            status = "gui.modern.replace_wb.u108";
        }
    }

    private void moveCategory(int direction) {
        if (!syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        String category = state.selectedCategory();
        if (!isConcreteCategory(category)) {
            return;
        }
        if (state.moveCategory(category, direction)) {
            status = "gui.modern.replace_wb.u109";
        }
    }

    private void copyCategoryName() {
        String category = state.selectedCategory();
        if (!isConcreteCategory(category)) {
            status = "gui.modern.replace_wb.u110";
            return;
        }
        status = copyToClipboard(category) ? "gui.modern.replace_wb.u111" : "gui.modern.replace_wb.u098";
    }

    private void selectEntry(int index) {
        if (editorRule == null || editorRule.replacements == null || index < 0
                || index >= editorRule.replacements.size()) {
            return;
        }
        syncEntryDraft();
        selectedEntryIndex = index;
        entryValidation = "";
        pendingEntryDelete = null;
        loadEntryFields();
    }

    private void addEntry() {
        if (editorRule == null) {
            return;
        }
        syncEntryDraft();
        if (editorRule.replacements == null) {
            editorRule.replacements = new ArrayList<BlockReplacementRule.BlockReplacementEntry>();
        }
        editorRule.replacements.add(new BlockReplacementRule.BlockReplacementEntry());
        selectedEntryIndex = editorRule.replacements.size() - 1;
        entryValidation = "";
        pendingEntryDelete = null;
        BlockReplacementHandler.markRuleDirty(editorRule);
        loadEntryFields();
        status = "gui.modern.replace_wb.u112";
    }

    private void duplicateEntry() {
        BlockReplacementRule.BlockReplacementEntry selected = selectedEntry();
        if (selected == null || editorRule == null) {
            return;
        }
        syncEntryDraft();
        BlockReplacementRule.BlockReplacementEntry copy = copyEntry(selected);
        editorRule.replacements.add(selectedEntryIndex + 1, copy);
        selectedEntryIndex++;
        pendingEntryDelete = null;
        BlockReplacementHandler.markRuleDirty(editorRule);
        loadEntryFields();
        status = "gui.modern.replace_wb.u113";
    }

    private void deleteEntry() {
        BlockReplacementRule.BlockReplacementEntry selected = selectedEntry();
        if (selected == null || editorRule == null) {
            return;
        }
        syncEntryDraft();
        if (pendingEntryDelete != selected) {
            pendingEntryDelete = selected;
            status = "gui.modern.replace_wb.u114";
            return;
        }
        editorRule.replacements.remove(selectedEntryIndex);
        selectedEntryIndex = editorRule.replacements.isEmpty()
                ? -1 : Math.min(selectedEntryIndex, editorRule.replacements.size() - 1);
        pendingEntryDelete = null;
        entryValidation = "";
        BlockReplacementHandler.markRuleDirty(editorRule);
        loadEntryFields();
        status = "gui.modern.replace_wb.u115";
    }

    private void applyEntry() {
        BlockReplacementRule.BlockReplacementEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        syncEntryDraft();
        if (isBlank(selected.sourceBlockId) || isBlank(selected.targetBlockId)) {
            entryValidation = "gui.modern.replace_wb.u116";
            status = entryValidation;
            return;
        }
        entryValidation = "";
        BlockReplacementHandler.markRuleDirty(editorRule);
        status = "gui.modern.replace_wb.u117";
    }

    private void pickRegion() {
        if (editorRule == null || !syncRuleDraft()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        BlockReplacementHandler.startRegionSelection(editorRule, minecraft.currentScreen);
        status = "gui.modern.replace_wb.u118";
    }

    private void pickSourceBlock() {
        BlockReplacementRule.BlockReplacementEntry selected = selectedEntry();
        if (selected == null || !syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        Minecraft minecraft = Minecraft.getMinecraft();
        BlockReplacementHandler.startSourceBlockSelection(selected, minecraft.currentScreen);
        status = "gui.modern.replace_wb.u119";
    }

    private void pickTargetBlock() {
        BlockReplacementRule.BlockReplacementEntry selected = selectedEntry();
        if (selected == null || !syncRuleDraft()) {
            return;
        }
        syncEntryDraft();
        Minecraft minecraft = Minecraft.getMinecraft();
        BlockReplacementHandler.startTargetBlockSelection(selected, minecraft.currentScreen);
        status = "gui.modern.replace_wb.u120";
    }

    private void scanAvailableBlocks() {
        if (editorRule == null || !syncRuleDraft()) {
            return;
        }
        BlockReplacementHandler.markRuleDirty(editorRule);
        status = "gui.modern.replace_wb.u078";
    }

    private BlockReplacementRule.BlockReplacementEntry selectedEntry() {
        if (editorRule == null || editorRule.replacements == null || selectedEntryIndex < 0
                || selectedEntryIndex >= editorRule.replacements.size()) {
            return null;
        }
        return editorRule.replacements.get(selectedEntryIndex);
    }

    private String conditionSummary(BlockReplacementRule rule) {
        if (!rule.enabled) {
            return "gui.modern.replace_wb.u121";
        }
        if (!rule.hasValidRegion()) {
            return "gui.modern.replace_wb.u122";
        }
        int enabledEntries = 0;
        if (rule.replacements != null) {
            for (BlockReplacementRule.BlockReplacementEntry entry : rule.replacements) {
                if (entry != null && entry.enabled && !isBlank(entry.sourceBlockId) && !isBlank(entry.targetBlockId)) {
                    enabledEntries++;
                }
            }
        }
        return enabledEntries == 0 ? "gui.modern.replace_wb.u123" : "满足：启用 · 有效区域 · " + enabledEntries + " 个可执行条目";
    }

    private String regionSummary(BlockReplacementRule rule) {
        if (rule == null || !rule.hasValidRegion()) {
            return "gui.modern.replace_wb.u124";
        }
        return "[" + rule.getMinX() + "," + rule.getMinY() + "," + rule.getMinZ() + "] ~ ["
                + rule.getMaxX() + "," + rule.getMaxY() + "," + rule.getMaxZ() + "] · "
                + rule.getRegionBlockCount() + " 个方块";
    }

    private String availableBlocksSummary(BlockReplacementRule rule) {
        if (rule == null || !rule.hasValidRegion()) {
            return "gui.modern.replace_wb.u125";
        }
        try {
            List<BlockReplacementHandler.BlockCountEntry> available = BlockReplacementHandler.getAvailableBlocks(rule);
            if (available == null || available.isEmpty()) {
                return "gui.modern.replace_wb.u126";
            }
            StringBuilder result = new StringBuilder();
            int count = Math.min(4, available.size());
            for (int i = 0; i < count; i++) {
                BlockReplacementHandler.BlockCountEntry entry = available.get(i);
                if (i > 0) {
                    result.append("; ");
                }
                result.append(entry.blockId).append(" x ").append(entry.count);
            }
            if (available.size() > count) {
                result.append("; ...");
            }
            return result.toString();
        } catch (RuntimeException exception) {
            return "gui.modern.replace_wb.u127";
        }
    }

    private List<BlockReplacementRule> matchingRules(String category, String query) {
        List<BlockReplacementRule> result = new ArrayList<>();
        for (BlockReplacementRule rule : state.rules()) {
            if (!normalizeCategory(rule.category).equalsIgnoreCase(category)) {
                continue;
            }
            if (query.isEmpty() || matches(rule, query)) {
                result.add(rule);
            }
        }
        return result;
    }

    private boolean matches(BlockReplacementRule rule, String query) {
        if (safe(rule.name).toLowerCase(Locale.ROOT).contains(query)
                || safe(rule.category).toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (rule.replacements != null) {
            for (BlockReplacementRule.BlockReplacementEntry entry : rule.replacements) {
                if (entry != null && (safe(entry.sourceBlockId).toLowerCase(Locale.ROOT).contains(query)
                        || safe(entry.targetBlockId).toLowerCase(Locale.ROOT).contains(query))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasPendingEntryEdits() {
        BlockReplacementRule.BlockReplacementEntry entry = selectedEntry();
        if (entry == null) {
            return false;
        }
        return sourceField != null && !safe(sourceField.getText()).trim().equals(safe(entry.sourceBlockId))
                || targetField != null && !safe(targetField.getText()).trim().equals(safe(entry.targetBlockId));
    }

    private boolean hasPendingCategoryName() {
        if (categoryNameField == null || !categoryNameField.isFocused()) {
            return false;
        }
        return !safe(categoryNameField.getText()).trim().equals(isConcreteCategory(state.selectedCategory())
                ? state.selectedCategory() : "");
    }

    private void loadEntryFields() {
        BlockReplacementRule.BlockReplacementEntry entry = selectedEntry();
        if (entry == null) {
            hideField(sourceField);
            hideField(targetField);
            return;
        }
        setTextIfDifferent(sourceField, safe(entry.sourceBlockId));
        setTextIfDifferent(targetField, safe(entry.targetBlockId));
        clearCustomFocus();
    }

    private void clearCustomFocus() {
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (categoryNameField != null) {
            categoryNameField.setFocused(false);
        }
        if (sourceField != null) {
            sourceField.setFocused(false);
        }
        if (targetField != null) {
            targetField.setFocused(false);
        }
    }

    private void clearPendingDeletes() {
        pendingDelete = null;
        pendingCategoryDelete = null;
        pendingEntryDelete = null;
    }

    private static GuiTextField createField(FontRenderer fontRenderer, int id, int maxLength) {
        GuiTextField field = new GuiTextField(id, fontRenderer, 0, 0, 1, 13);
        field.setMaxStringLength(maxLength);
        field.setCanLoseFocus(true);
        field.setEnableBackgroundDrawing(false);
        return field;
    }

    private static void updateCursor(GuiTextField field) {
        if (field != null) {
            field.updateCursorCounter();
        }
    }

    private static void setTextIfDifferent(GuiTextField field, String value) {
        if (field != null && !safe(field.getText()).equals(safe(value))) {
            field.setText(safe(value));
        }
    }

    private ModernMainLayout.Rect[] actionRow(int y, int count) {
        return actionRow(navigationBounds.x + 8, navigationBounds.right() - 8, y, count);
    }

    private ModernMainLayout.Rect[] actionRow(int left, int right, int y, int count) {
        return actionRow(left, right, y, count, 20);
    }

    private ModernMainLayout.Rect[] actionRow(int left, int right, int y, int count, int height) {
        int safeCount = Math.max(1, count);
        int gap = Math.min(3, Math.max(0, (right - left - safeCount) / Math.max(1, safeCount - 1)));
        int width = Math.max(1, (right - left - gap * (safeCount - 1)) / safeCount);
        ModernMainLayout.Rect[] result = new ModernMainLayout.Rect[safeCount];
        int x = left;
        for (int i = 0; i < safeCount; i++) {
            int itemWidth = i == safeCount - 1 ? Math.max(1, right - x) : width;
            result[i] = new ModernMainLayout.Rect(x, y, itemWidth, Math.max(1, height));
            x += itemWidth + gap;
        }
        return result;
    }

    private int navigationControlsHeight() {
        if (navigationBounds.height >= 190) {
            return FULL_NAV_CONTROLS_HEIGHT;
        }
        if (navigationBounds.height >= 130) {
            return 88;
        }
        if (navigationBounds.height >= 100) {
            return 60;
        }
        return 38;
    }

    private static ModernFormSettingsTab.TextValue text(final StringGetter getter, final StringSetter setter) {
        return new ModernFormSettingsTab.TextValue() {
            @Override
            public String get() {
                return safe(getter.get());
            }

            @Override
            public void set(String value) {
                setter.set(safe(value));
            }
        };
    }

    private static ModernFormSettingsTab.BooleanValue bool(final BooleanGetter getter, final BooleanSetter setter) {
        return new ModernFormSettingsTab.BooleanValue() {
            @Override
            public boolean get() {
                return getter.get();
            }

            @Override
            public void set(boolean value) {
                setter.set(value);
            }
        };
    }

    private static String formatCorner(BlockReplacementRule rule, boolean first) {
        if (rule == null || first && !rule.hasCorner1() || !first && !rule.hasCorner2()) {
            return "";
        }
        return first ? rule.corner1X + ", " + rule.corner1Y + ", " + rule.corner1Z
                : rule.corner2X + ", " + rule.corner2Y + ", " + rule.corner2Z;
    }

    private static BlockReplacementRule copyRule(BlockReplacementRule source) {
        BlockReplacementRule copy = new BlockReplacementRule();
        if (source == null) {
            return copy;
        }
        copyRuleValues(copy, source);
        return copy;
    }

    private static void copyRuleValues(BlockReplacementRule target, BlockReplacementRule source) {
        if (target == null || source == null) {
            return;
        }
        target.name = source.name;
        target.category = source.category;
        target.enabled = source.enabled;
        target.highlightReplacedBlocks = source.highlightReplacedBlocks;
        target.useSolidCollision = source.useSolidCollision;
        target.corner1X = source.corner1X;
        target.corner1Y = source.corner1Y;
        target.corner1Z = source.corner1Z;
        target.corner2X = source.corner2X;
        target.corner2Y = source.corner2Y;
        target.corner2Z = source.corner2Z;
        target.replacements = new ArrayList<BlockReplacementRule.BlockReplacementEntry>();
        if (source.replacements != null) {
            for (BlockReplacementRule.BlockReplacementEntry entry : source.replacements) {
                target.replacements.add(copyEntry(entry));
            }
        }
        target.dirty = source.dirty;
    }

    private static BlockReplacementRule.BlockReplacementEntry copyEntry(
            BlockReplacementRule.BlockReplacementEntry source) {
        BlockReplacementRule.BlockReplacementEntry copy = new BlockReplacementRule.BlockReplacementEntry();
        if (source != null) {
            copy.sourceBlockId = source.sourceBlockId;
            copy.targetBlockId = source.targetBlockId;
            copy.enabled = source.enabled;
        }
        return copy;
    }

    private static void normalizeRule(BlockReplacementRule rule) {
        if (rule == null) {
            return;
        }
        rule.name = safe(rule.name).trim();
        rule.category = normalizeCategory(rule.category);
        if (rule.replacements == null) {
            rule.replacements = new ArrayList<BlockReplacementRule.BlockReplacementEntry>();
        }
        rule.dirty = true;
    }

    private static String normalizeCategory(String category) {
        String normalized = safe(category).trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static boolean isConcreteCategory(String category) {
        return !isBlank(category) && !CATEGORY_ALL.equals(category);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(RuntimeException exception) {
        return exception == null || isBlank(exception.getMessage()) ? ModernFormI18n.tr("gui.modern.replace_wb.u128")
                : exception.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    private static int entryCount(BlockReplacementRule rule) {
        return rule == null || rule.replacements == null ? 0 : rule.replacements.size();
    }

    private static boolean contains(ModernMainLayout.Rect rect, int x, int y) {
        return rect != null && rect.contains(x, y);
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.bottom() > second.y && first.y < second.bottom();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect rect, int amount) {
        if (rect == null) {
            return new ModernMainLayout.Rect(0, 0, 1, 1);
        }
        return new ModernMainLayout.Rect(rect.x + amount, rect.y + amount,
                Math.max(1, rect.width - amount * 2), Math.max(1, rect.height - amount * 2));
    }

    private static boolean copyToClipboard(String value) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(safe(value)), null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private interface StringGetter {
        String get();
    }

    private interface StringSetter {
        void set(String value);
    }

    private interface BooleanGetter {
        boolean get();
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private static final class CategoryHit {
        private final String category;
        private final ModernMainLayout.Rect bounds;

        private CategoryHit(String category, ModernMainLayout.Rect bounds) {
            this.category = category;
            this.bounds = bounds;
        }
    }

    private static final class RuleHit {
        private final BlockReplacementRule rule;
        private final ModernMainLayout.Rect bounds;

        private RuleHit(BlockReplacementRule rule, ModernMainLayout.Rect bounds) {
            this.rule = rule;
            this.bounds = bounds;
        }
    }

    private static final class EntryHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private EntryHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class WorkbenchState {
        private final List<BlockReplacementRule> rules = new ArrayList<>();
        private final List<String> categories = new ArrayList<>();
        private List<BlockReplacementRule> originalRules;
        private List<String> originalCategories;
        private BlockReplacementRule selected;
        private String selectedCategory = CATEGORY_ALL;

        private WorkbenchState(List<BlockReplacementRule> sourceRules, List<String> sourceCategories) {
            if (sourceCategories != null) {
                for (String category : sourceCategories) {
                    addCategoryValue(category);
                }
            }
            if (sourceRules != null) {
                for (BlockReplacementRule rule : sourceRules) {
                    if (rule != null) {
                        BlockReplacementRule copy = copyRule(rule);
                        normalizeRule(copy);
                        rules.add(copy);
                        addCategoryValue(copy.category);
                    }
                }
            }
            ensureAllCategories();
            selected = rules.isEmpty() ? null : rules.get(0);
            if (selected != null) {
                selectedCategory = normalizeCategory(selected.category);
            }
            originalRules = copyRules(rules);
            originalCategories = new ArrayList<>(categories);
        }

        private List<BlockReplacementRule> rules() {
            return rules;
        }

        private List<String> categories() {
            return categories;
        }

        private BlockReplacementRule selected() {
            return selected;
        }

        private String selectedCategory() {
            return selectedCategory;
        }

        private void setSelectedCategory(String value) {
            selectedCategory = value == null ? CATEGORY_ALL : value;
        }

        private void select(BlockReplacementRule rule) {
            if (rules.contains(rule)) {
                selected = rule;
                selectedCategory = normalizeCategory(rule.category);
            }
        }

        private void clearSelection() {
            selected = null;
        }

        private int indexOf(BlockReplacementRule rule) {
            return rule == null ? -1 : rules.indexOf(rule);
        }

        private BlockReplacementRule firstInCategory(String category) {
            for (BlockReplacementRule rule : rules) {
                if (normalizeCategory(rule.category).equalsIgnoreCase(normalizeCategory(category))) {
                    return rule;
                }
            }
            return null;
        }

        private BlockReplacementRule addRule(String category) {
            BlockReplacementRule rule = new BlockReplacementRule();
            rule.category = normalizeCategory(category);
            rule.name = uniqueName("gui.modern.replace_wb.u129");
            rules.add(rule);
            selected = rule;
            selectedCategory = rule.category;
            addCategoryValue(rule.category);
            return rule;
        }

        private BlockReplacementRule duplicateSelected() {
            if (selected == null) {
                return null;
            }
            BlockReplacementRule copy = copyRule(selected);
            copy.name = uniqueName(ModernFormI18n.tr("gui.modern.wb.fmt.copy", safe(selected.name).trim()));
            copy.dirty = true;
            int index = rules.indexOf(selected);
            rules.add(Math.min(rules.size(), index + 1), copy);
            selected = copy;
            return copy;
        }

        private void deleteSelected() {
            if (selected == null) {
                return;
            }
            int index = rules.indexOf(selected);
            rules.remove(selected);
            if (rules.isEmpty()) {
                selected = null;
                return;
            }
            selected = rules.get(Math.min(Math.max(0, index), rules.size() - 1));
            selectedCategory = normalizeCategory(selected.category);
        }

        private boolean moveSelectedRule(int direction) {
            if (selected == null || direction == 0) {
                return false;
            }
            int index = rules.indexOf(selected);
            int cursor = index + (direction < 0 ? -1 : 1);
            String category = normalizeCategory(selected.category);
            while (cursor >= 0 && cursor < rules.size()
                    && !category.equalsIgnoreCase(normalizeCategory(rules.get(cursor).category))) {
                cursor += direction < 0 ? -1 : 1;
            }
            if (cursor < 0 || cursor >= rules.size()) {
                return false;
            }
            rules.set(index, rules.get(cursor));
            rules.set(cursor, selected);
            return true;
        }

        private boolean addCategory(String category) {
            String normalized = normalizeCategory(category);
            if (hasCategory(normalized)) {
                return false;
            }
            categories.add(normalized);
            return true;
        }

        private boolean renameCategory(String oldCategory, String newCategory) {
            String oldValue = normalizeCategory(oldCategory);
            String newValue = normalizeCategory(newCategory);
            if (oldValue.equalsIgnoreCase(newValue)) {
                return true;
            }
            if (hasCategory(newValue)) {
                return false;
            }
            boolean changed = false;
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(oldValue)) {
                    categories.set(i, newValue);
                    changed = true;
                    break;
                }
            }
            for (BlockReplacementRule rule : rules) {
                if (normalizeCategory(rule.category).equalsIgnoreCase(oldValue)) {
                    rule.category = newValue;
                    rule.dirty = true;
                    changed = true;
                }
            }
            ensureAllCategories();
            return changed;
        }

        private boolean deleteCategory(String category) {
            String normalized = normalizeCategory(category);
            boolean changed = false;
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(normalized)) {
                    categories.remove(i);
                    changed = true;
                    break;
                }
            }
            for (BlockReplacementRule rule : rules) {
                if (normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                    rule.category = CATEGORY_DEFAULT;
                    rule.dirty = true;
                    changed = true;
                }
            }
            ensureAllCategories();
            return changed;
        }

        private boolean moveCategory(String category, int direction) {
            int index = categoryIndex(category);
            int target = index + (direction < 0 ? -1 : 1);
            if (index < 0 || target < 0 || target >= categories.size()) {
                return false;
            }
            String value = categories.remove(index);
            categories.add(target, value);
            return true;
        }

        private int categoryIndex(String category) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(normalizeCategory(category))) {
                    return i;
                }
            }
            return -1;
        }

        private void ensureCategory(String category) {
            addCategoryValue(category);
        }

        private void ensureAllCategories() {
            if (categories.isEmpty()) {
                categories.add(CATEGORY_DEFAULT);
            }
            for (BlockReplacementRule rule : rules) {
                rule.category = normalizeCategory(rule.category);
                addCategoryValue(rule.category);
            }
        }

        private void reload(List<BlockReplacementRule> sourceRules, List<String> sourceCategories) {
            rules.clear();
            categories.clear();
            if (sourceCategories != null) {
                for (String category : sourceCategories) {
                    addCategoryValue(category);
                }
            }
            if (sourceRules != null) {
                for (BlockReplacementRule rule : sourceRules) {
                    if (rule != null) {
                        BlockReplacementRule copy = copyRule(rule);
                        normalizeRule(copy);
                        rules.add(copy);
                        addCategoryValue(copy.category);
                    }
                }
            }
            ensureAllCategories();
            selected = rules.isEmpty() ? null : rules.get(0);
            selectedCategory = selected == null ? CATEGORY_ALL : normalizeCategory(selected.category);
            originalRules = copyRules(rules);
            originalCategories = new ArrayList<>(categories);
        }

        private boolean hasCategory(String category) {
            return categoryIndex(category) >= 0;
        }

        private void addCategoryValue(String category) {
            String normalized = normalizeCategory(category);
            if (!hasCategory(normalized)) {
                categories.add(normalized);
            }
        }

        private String uniqueName(String base) {
            String original = isBlank(base) ? "gui.modern.replace_wb.u130" : base;
            String candidate = original;
            int suffix = 2;
            while (findByName(candidate) != null) {
                candidate = original + " " + suffix++;
            }
            return candidate;
        }

        private BlockReplacementRule findByName(String name) {
            for (BlockReplacementRule rule : rules) {
                if (safe(rule.name).equalsIgnoreCase(safe(name))) {
                    return rule;
                }
            }
            return null;
        }

        private boolean isDirty() {
            return !STATE_GSON.toJson(originalRules).equals(STATE_GSON.toJson(rules))
                    || !STATE_GSON.toJson(originalCategories).equals(STATE_GSON.toJson(categories));
        }

        private void markCommitted() {
            originalRules = copyRules(rules);
            originalCategories = new ArrayList<>(categories);
        }

        private void discard() {
            String selectedName = selected == null ? "" : safe(selected.name);
            String selectedCategoryValue = selected == null ? "" : normalizeCategory(selected.category);
            rules.clear();
            rules.addAll(copyRules(originalRules));
            categories.clear();
            categories.addAll(originalCategories);
            selected = null;
            for (BlockReplacementRule rule : rules) {
                if (safe(rule.name).equalsIgnoreCase(selectedName)
                        && normalizeCategory(rule.category).equalsIgnoreCase(selectedCategoryValue)) {
                    selected = rule;
                    break;
                }
            }
            if (selected == null && !rules.isEmpty()) {
                selected = rules.get(0);
            }
            selectedCategory = selected == null ? CATEGORY_ALL : normalizeCategory(selected.category);
            ensureAllCategories();
        }

        private static List<BlockReplacementRule> copyRules(List<BlockReplacementRule> source) {
            List<BlockReplacementRule> result = new ArrayList<>();
            if (source != null) {
                for (BlockReplacementRule rule : source) {
                    if (rule != null) {
                        result.add(copyRule(rule));
                    }
                }
            }
            return result;
        }
    }
}
