package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.handlers.ConditionalExecutionHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.system.ConditionalRule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Native list-detail workbench for conditional execution rules. */
public final class ModernConditionalExecutionWorkbenchTab implements ModernSettingsTab {
    private final com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar navigationScrollbar = new com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar();
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("conditionalexecution");

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 30;
    private static final int GROUP_HEIGHT = ModernTreeGuide.GROUP_HEIGHT;
    private static final int RULE_HEIGHT = 43;
    private static final int SECTION_NAV_WIDTH = 112;
    private static final int SECTION_ROW_HEIGHT = 27;
    private static final int CATEGORY_TOOLBAR_HEIGHT = 24;
    private static final int RULE_ACTION_RESERVE = 51;
    private static final int MAX_ACTION_PREVIEW = 8;
    private static final String CATEGORY_DEFAULT = "默认";
    private static final String CATEGORY_BUILTIN = "gui.modern.conditional.u002";
    private static final String[] EDITOR_SECTIONS = {
            "gui.modern.conditional.u003", "gui.modern.conditional.u004", "gui.modern.conditional.u005", "gui.modern.conditional.u006", "gui.modern.conditional.u007", "gui.modern.conditional.u008"
    };

    private final ConditionalWorkbenchState state = new ConditionalWorkbenchState(
            readSourceRules(), ConditionalExecutionHandler.getCategoriesSnapshot(),
            ConditionalExecutionHandler.isGloballyEnabled());
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final List<SectionHit> sectionHits = new ArrayList<>();

    private ModernSettingsTab editor;
    private ConditionalRule editorRule;
    private GuiTextField searchField;
    private GuiTextField categoryField;
    private SequencePicker sequencePicker;

    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect navigationBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect sectionNavBounds;
    private ModernMainLayout.Rect formBounds;
    private ModernMainLayout.Rect navigationDividerBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect addBounds;
    private ModernMainLayout.Rect duplicateBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect categoryNameBounds;
    private ModernMainLayout.Rect categoryAddBounds;
    private ModernMainLayout.Rect categoryRenameBounds;
    private ModernMainLayout.Rect categoryDeleteBounds;
    private ModernMainLayout.Rect categoryUpBounds;
    private ModernMainLayout.Rect categoryDownBounds;
    private ModernMainLayout.Rect moveUpBounds;
    private ModernMainLayout.Rect moveDownBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private ModernMainLayout.Rect masterBounds;

    private int navigationScroll;
    private int navigationMaxScroll;
    private double navigationRatio = 0.28D;
    private boolean layoutPreferencesLoaded;
    private int selectedSection;
    private int sectionScroll;
    private int sectionMaxScroll;
    private int headerHeight = TOP_INSET;
    private int footerHeight = FOOTER_HEIGHT;
    private boolean restoreSectionAfterRebuild;
    private boolean draggingNavigationDivider;
    private ConditionalRule pendingDelete;
    private String pendingDeleteCategory;
    private String editorInputError = "";
    private String editorInputErrorKey = "";
    private String status = "";

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("rules.conditional_execution.navigation", navigationRatio);
            layoutPreferencesLoaded = true;
        }
        if (searchField == null) {
            searchField = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
            searchField.setEnableBackgroundDrawing(false);
            searchField.setMaxStringLength(96);
            searchField.setTextColor(ModernUiRenderer.TEXT);
            searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        }
        if (categoryField == null) {
            categoryField = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
            categoryField.setEnableBackgroundDrawing(false);
            categoryField.setMaxStringLength(80);
            categoryField.setTextColor(ModernUiRenderer.TEXT);
            categoryField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
            categoryField.setText(state.selectedCategory());
        }
        if (sequencePicker == null) {
            sequencePicker = new SequencePicker(this::selectSequence);
        }
        sequencePicker.ensureInitialized(fontRenderer);
        ensureEditor();
        editor.ensureInitialized(fontRenderer);
    }

    @Override
    public void updateScreen() {
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
        if (categoryField != null) {
            categoryField.updateCursorCounter();
        }
        if (sequencePicker != null) {
            sequencePicker.updateScreen();
        }
        ensureEditor();
        if (editor != null) {
            editor.updateScreen();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requested, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        bounds = inset(requested, 7);
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        headerHeight = TOP_INSET;
        footerHeight = Math.min(FOOTER_HEIGHT, Math.max(24, bounds.height / 8));
        int splitTotal = Math.max(2, bounds.width - 20);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, navigationRatio, 150, 260, 96, 120);
        navigationRatio = split.ratio;

        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + headerHeight,
                Math.max(1, split.firstWidth), Math.max(1, bounds.height - headerHeight - footerHeight - 6));
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 0,
                navigationBounds.y, navigationBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationDividerBounds.right(), navigationBounds.y,
                Math.max(1, bounds.right() - navigationDividerBounds.right() - 8), navigationBounds.height);

        int sectionWidth = editorBounds.width >= 260
                ? SECTION_NAV_WIDTH
                : editorBounds.width >= 210 ? Math.min(88, SECTION_NAV_WIDTH) : 0;
        if (sectionWidth > 0) {
            sectionNavBounds = new ModernMainLayout.Rect(editorBounds.x + 6, editorBounds.y + 6,
                    Math.min(sectionWidth, Math.max(1, editorBounds.width - 8)),
                    Math.max(1, editorBounds.height - 12));
            formBounds = new ModernMainLayout.Rect(sectionNavBounds.right() + 7, editorBounds.y,
                    Math.max(1, editorBounds.right() - sectionNavBounds.right() - 7), editorBounds.height);
        } else {
            sectionNavBounds = null;
            formBounds = new ModernMainLayout.Rect(editorBounds.x + 4, editorBounds.y,
                    Math.max(1, editorBounds.width - 4), editorBounds.height);
        }

        drawNavigation(fontRenderer, mouseX, mouseY);
        drawSectionNavigation(fontRenderer, mouseX, mouseY);
        editor.draw(fontRenderer, formBounds, mouseX, mouseY);
        if (restoreSectionAfterRebuild && editor instanceof ModernFormSettingsTab) {
            ((ModernFormSettingsTab<?>) editor).scrollToSection(selectedSection);
            restoreSectionAfterRebuild = false;
        }
        if (sequencePicker != null) {
            sequencePicker.draw(fontRenderer, formBounds, "gui.modern.conditional.u009", mouseX, mouseY);
        }
        drawFooter(fontRenderer, mouseX, mouseY);
        navigationActions.drawOverlay(mouseX, mouseY);
    }


    private void configureNavigationActions() {
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.move", state.selected().category, value -> { state.selected().category = value; state.selectCategory(value); categoryField.setText(value); rebuildEditor(); }); });

        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_add", "", value -> { categoryField.setText(value); navigationCategoryAdd(); }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", state.selectedCategory(), value -> { categoryField.setText(value); navigationCategoryRename(); }));
        navigationActions.action("category_delete", state.selectedCategory().equalsIgnoreCase(pendingDeleteCategory) ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.category_delete", false, true, () -> !isDefaultCategory(state.selectedCategory()), this::navigationCategoryDelete);
        navigationActions.action("category_up", "gui.modern.nav.category_up", false, false, () -> state.canMoveCategory(-1), this::navigationCategoryUp);
        navigationActions.action("category_down", "gui.modern.nav.category_down", false, false, () -> state.canMoveCategory(1), this::navigationCategoryDown);
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> state.selected() != null, this::navigationCopy);
        navigationActions.action("delete", state.selected() != null && pendingDelete == state.selected() ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.delete", true, true, () -> state.selected() != null, this::navigationDelete);
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> state.canMove(-1), this::navigationUp);
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> state.canMove(1), this::navigationDown);
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.rename", state.selected().name, value -> { state.selected().name = value; rebuildEditor(); }); });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) { state.selected().enabled = !state.selected().enabled; rebuildEditor(); } });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedGroups.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (ConditionalWorkbenchState.Group group : state.groups()) collapsedGroups.add(group.name()); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationContext(int mouseX, int mouseY) {
        if (!navigationActions.inTree(mouseX, mouseY)) return false;
        if (!syncEditorDraft()) return true;
        searchField.setFocused(false);
        for (RuleHit hit : ruleHits) if (hit.bounds.contains(mouseX, mouseY)) {
            if (state.selected() != hit.rule) { state.select(hit.rule); rebuildEditor(); }
            categoryField.setText(state.selectedCategory());
            navigationActions.context(mouseX, mouseY, "add", "copy", "rename", "move", "toggle", "up", "down", "delete");
            return true;
        }
        for (GroupHit hit : groupHits) if (hit.bounds.contains(mouseX, mouseY)) {
            state.selectCategory(hit.name); categoryField.setText(hit.name);
            navigationActions.action("fold", "gui.modern.nav.fold", false, false, () -> true,
                    () -> { if (!collapsedGroups.add(hit.name)) collapsedGroups.remove(hit.name); });
            navigationActions.context(mouseX, mouseY, "add", "category_add", "category_rename", "category_up", "category_down", "fold", "category_delete");
            return true;
        }
        navigationActions.context(mouseX, mouseY, "add", "category_add", "expand", "collapse");
        return true;
    }

    private boolean navigationAdd() {
            if (syncEditorDraft()) {
                clearPendingDelete();
                state.addRule(selectedCategory());
                categoryField.setText(state.selectedCategory());
                rebuildEditor();
                status = "gui.modern.conditional.u033";
            }
            return true;
        }

    private boolean navigationCopy() {
            if (syncEditorDraft()) {
                clearPendingDelete();
                if (state.duplicateSelected()) {
                    categoryField.setText(state.selectedCategory());
                    rebuildEditor();
                    status = "gui.modern.conditional.u034";
                } else {
                    status = "gui.modern.conditional.u035";
                }
            }
            return true;
        }

    private boolean navigationDelete() {
            if (!syncEditorDraft()) {
                return true;
            }
            ConditionalRule selected = state.selected();
            if (selected == null) {
                status = "gui.modern.conditional.u035";
                return true;
            }
            if (pendingDelete != selected) {
                pendingDeleteCategory = null;
                pendingDelete = selected;
                status = "gui.modern.conditional.u036";
                return true;
            }
            pendingDelete = null;
            state.deleteSelected();
            categoryField.setText(state.selectedCategory());
            rebuildEditor();
            status = "gui.modern.conditional.u037";
            return true;
        }

    private boolean navigationUp() {
            if (syncEditorDraft()) {
                clearPendingDelete();
                status = state.moveSelected(-1) ? "gui.modern.conditional.u038" : "gui.modern.conditional.u039";
            }
            return true;
        }

    private boolean navigationDown() {
            if (syncEditorDraft()) {
                clearPendingDelete();
                status = state.moveSelected(1) ? "gui.modern.conditional.u040" : "gui.modern.conditional.u041";
            }
            return true;
        }

    private boolean navigationCategoryAdd() {
            clearPendingDelete();
            if (syncEditorDraft()) {
                addCategoryFromInput();
            }
            return true;
        }

    private boolean navigationCategoryRename() {
            clearPendingDelete();
            if (syncEditorDraft()) {
                renameSelectedCategoryFromInput();
            }
            return true;
        }

    private boolean navigationCategoryDelete() {
            if (syncEditorDraft()) {
                deleteSelectedCategoryWithConfirmation();
            }
            return true;
        }

    private boolean navigationCategoryUp() {
            clearPendingDelete();
            if (syncEditorDraft() && state.moveCategory(-1)) {
                categoryField.setText(state.selectedCategory());
                status = "gui.modern.conditional.u032";
            }
            return true;
        }

    private boolean navigationCategoryDown() {
            clearPendingDelete();
            if (syncEditorDraft() && state.moveCategory(1)) {
                categoryField.setText(state.selectedCategory());
                status = "gui.modern.conditional.u032";
            }
            return true;
        }

    private void drawNavigation(FontRenderer font, int mouseX, int mouseY) {
        configureNavigationActions();
        navigationActions.begin(font, navigationBounds);
        ModernUiRenderer.drawSubtlePanel(navigationBounds.x, navigationBounds.y, navigationBounds.width,
                navigationBounds.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        searchBounds = new ModernMainLayout.Rect(navigationBounds.x + 8, navigationBounds.y + 30,
                Math.max(1, navigationBounds.width - 16), 20);
        searchField.x = searchBounds.x + 22;
        searchField.y = searchBounds.y + 5;
        searchField.width = Math.max(1, searchBounds.width - 28);
        searchField.height = 12;
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE,
                searchField.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(font, "gui.modern.conditional.u013", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, searchField.width);
        }
        ModernUiRenderer.drawTextField(searchField);

        ruleHits.clear();
        groupHits.clear();
        int contentTop = searchBounds.bottom() + 6;
        int contentBottom = Math.max(contentTop + 1, navigationActions.contentBottom());
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(navigationBounds.x + 5, contentTop,
                Math.max(1, navigationBounds.width - 10), Math.max(1, contentBottom - contentTop));
        ModernUiRenderer.beginClip(clip);
        int y = clip.y - navigationScroll;
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        boolean hasRules = false;
        for (ConditionalWorkbenchState.Group group : state.groups()) {
            List<ConditionalRule> visible = matching(group.rules(), query);
            boolean groupMatches = safe(group.name()).toLowerCase(Locale.ROOT).contains(query);
            if (!query.isEmpty() && visible.isEmpty() && !groupMatches) {
                continue;
            }
            ModernMainLayout.Rect groupRect = ModernTreeGuide.groupRow(clip, y);
            boolean collapsed = collapsedGroups.contains(group.name()) && query.isEmpty();
            drawGroup(font, groupRect, group.name(), visible.size(), collapsed, mouseX, mouseY);
            if (intersectsVertically(groupRect, clip)) {
                groupHits.add(new GroupHit(group.name(), groupRect));
            }
            y = ModernTreeGuide.nextY(y, GROUP_HEIGHT);
            if (!collapsed) {
                for (ConditionalRule rule : visible) {
                    hasRules = true;
                    ModernMainLayout.Rect row = ModernTreeGuide.row(clip.x,
                            com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar.contentWidth(clip.width), y, RULE_HEIGHT, 1);
                    ModernTreeGuide.drawChild(clip.x, 0, groupRect, row);
                    drawRule(font, row, rule, mouseX, mouseY);
                    if (intersectsVertically(row, clip)) {
                        ruleHits.add(new RuleHit(rule, row));
                    }
                    y = ModernTreeGuide.nextY(y, RULE_HEIGHT);
                }
            } else if (!visible.isEmpty()) {
                hasRules = true;
            }
        }
        if (!hasRules) {
            ModernUiRenderer.drawText(font, query.isEmpty() ? "gui.modern.conditional.u018" : "gui.modern.conditional.u019",
                    clip.x + 7, y + 5, ModernUiRenderer.MUTED_TEXT, Math.max(1, clip.width - 14));
            y += 22;
        }
        ModernUiRenderer.endClip();
        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);
        navigationMaxScroll = Math.max(0, y + navigationScroll - clip.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        navigationScrollbar.draw(clip, navigationScroll, navigationMaxScroll, clip.height, clip.height + navigationMaxScroll, mouseX, mouseY, value -> navigationScroll = value);

        navigationActions.draw(mouseX, mouseY);
    }

    private void drawSectionNavigation(FontRenderer font, int mouseX, int mouseY) {
        sectionHits.clear();
        if (sectionNavBounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(sectionNavBounds.x, sectionNavBounds.y, sectionNavBounds.width,
                sectionNavBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, "gui.modern.conditional.u027", sectionNavBounds.x + 9, sectionNavBounds.y + 10,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(1, sectionNavBounds.width - 18));
        int contentTop = sectionNavBounds.y + 32;
        int contentHeight = Math.max(1, sectionNavBounds.height - 40);
        int rowHeight = Math.min(SECTION_ROW_HEIGHT,
                Math.max(20, (contentHeight - (EDITOR_SECTIONS.length - 1) * 3) / EDITOR_SECTIONS.length));
        int contentTotal = EDITOR_SECTIONS.length * rowHeight + (EDITOR_SECTIONS.length - 1) * 3;
        sectionMaxScroll = Math.max(0, contentTotal - contentHeight);
        sectionScroll = clamp(sectionScroll, 0, sectionMaxScroll);
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(sectionNavBounds.x + 3, contentTop,
                Math.max(1, sectionNavBounds.width - 6), contentHeight);
        ModernUiRenderer.beginClip(clip);
        int y = contentTop - sectionScroll;
        for (int i = 0; i < EDITOR_SECTIONS.length; i++) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(sectionNavBounds.x + 5, y,
                    Math.max(1, sectionNavBounds.width - 20), rowHeight);
            boolean selected = i == selectedSection;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, EDITOR_SECTIONS[i], row.x + 8,
                    row.y + Math.max(4, (row.height - font.FONT_HEIGHT) / 2),
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(1, row.width - 16));
            if (intersectsVertically(row, clip)) {
                sectionHits.add(new SectionHit(i, row));
            }
            y += rowHeight + 3;
        }
        ModernUiRenderer.endClip();
        if (sectionMaxScroll > 0) {
            int trackX = sectionNavBounds.right() - 6;
            int thumbHeight = Math.max(12, contentHeight * contentHeight / Math.max(1, contentTotal));
            int thumbY = contentTop + (contentHeight - thumbHeight) * sectionScroll
                    / Math.max(1, sectionMaxScroll);
            ModernRuleEditorUi.drawScrollbar(trackX, contentTop, contentHeight, thumbY, thumbHeight);
        }
    }

    private void drawFooter(FontRenderer font, int mouseX, int mouseY) {
        int y = Math.max(bounds.y, bounds.bottom() - 25);
        masterBounds = new ModernMainLayout.Rect(bounds.x + 8, y,
                Math.max(1, Math.min(130, navigationBounds.width - 8)), 20);
        drawToggle(font, masterBounds, "gui.modern.conditional.u012", state.masterEnabled(), mouseX, mouseY);
        int left = editorBounds == null ? bounds.x + 8 : editorBounds.x + 8;
        int right = editorBounds == null ? bounds.right() - 8 : editorBounds.right() - 8;
        int available = Math.max(1, right - left);
        int gap = 6;
        int actionWidth = Math.max(1, Math.min(78, (available - gap) / 2));
        revertBounds = new ModernMainLayout.Rect(Math.max(bounds.x, right - actionWidth * 2 - gap), y,
                actionWidth, 20);
        saveBounds = new ModernMainLayout.Rect(revertBounds.right() + gap, y,
                Math.max(1, right - revertBounds.right() - gap), 20);
        drawButton(font, revertBounds, "gui.modern.conditional.u028", false, mouseX, mouseY);
        drawButton(font, saveBounds, isDirty() ? "gui.modern.conditional.u029" : "gui.modern.conditional.u030", true, mouseX, mouseY);
        int textRight = Math.max(bounds.x + 1, revertBounds.x - 10);
        String message = status.isEmpty()
                ? ModernFormI18n.tr(isDirty() ? "gui.modern.wb.fmt.rules_cats_dirty"
                        : "gui.modern.wb.fmt.rules_cats_synced",
                        String.valueOf(state.rules().size()), String.valueOf(state.categories().size()))
                : status;
        ModernUiRenderer.drawText(font, message, masterBounds.right() + 8, y + 6,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, textRight - masterBounds.right() - 8));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.mouseClicked(mouseX, mouseY);
        }
        if (navigationActions.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (mouseButton == 1 && navigationContext(mouseX, mouseY)) return true;
        if (mouseButton != 0) {
            return true;
        }

        if (navigationScrollbar.beginDrag(mouseX, mouseY)) return true;
        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }
        if (contains(masterBounds, mouseX, mouseY)) {
            clearPendingDelete();
            state.setMasterEnabled(!state.masterEnabled());
            status = "gui.modern.conditional.u031";
            return true;
        }
        if (contains(searchBounds, mouseX, mouseY)) {
            clearPendingDelete();
            categoryField.setFocused(false);
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(categoryNameBounds, mouseX, mouseY)) {
            clearPendingDelete();
            searchField.setFocused(false);
            categoryField.setFocused(true);
            categoryField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        searchField.setFocused(false);
        categoryField.setFocused(false);

        if (contains(categoryAddBounds, mouseX, mouseY)) { return navigationCategoryAdd(); }
        if (contains(categoryRenameBounds, mouseX, mouseY)) { return navigationCategoryRename(); }
        if (contains(categoryDeleteBounds, mouseX, mouseY)) { return navigationCategoryDelete(); }
        if (contains(categoryUpBounds, mouseX, mouseY)) { return navigationCategoryUp(); }
        if (contains(categoryDownBounds, mouseX, mouseY)) { return navigationCategoryDown(); }

        for (GroupHit hit : groupHits) {
            if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                if (!syncEditorDraft()) {
                    return true;
                }
                clearPendingDelete();
                state.selectCategory(hit.name);
                categoryField.setText(state.selectedCategory());
                if (!collapsedGroups.add(hit.name)) {
                    collapsedGroups.remove(hit.name);
                }
                return true;
            }
        }
        for (RuleHit hit : ruleHits) {
            if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                if (!syncEditorDraft()) {
                    return true;
                }
                clearPendingDelete();
                state.select(hit.rule);
                categoryField.setText(state.selectedCategory());
                rebuildEditor();
                status = ModernFormI18n.tr("gui.modern.wb.fmt.selected", safe(hit.rule.name));
                return true;
            }
        }
        if (contains(addBounds, mouseX, mouseY)) { return navigationAdd(); }
        if (contains(duplicateBounds, mouseX, mouseY)) { return navigationCopy(); }
        if (contains(deleteBounds, mouseX, mouseY)) { return navigationDelete(); }
        if (contains(moveUpBounds, mouseX, mouseY)) { return navigationUp(); }
        if (contains(moveDownBounds, mouseX, mouseY)) { return navigationDown(); }
        if (contains(saveBounds, mouseX, mouseY)) {
            save();
            return true;
        }
        if (contains(revertBounds, mouseX, mouseY)) {
            discardDraft();
            status = "gui.modern.conditional.u042";
            return true;
        }
        if (sectionNavBounds != null && sectionNavBounds.contains(mouseX, mouseY)) {
            for (SectionHit hit : sectionHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    selectedSection = hit.index;
                    if (editor instanceof ModernFormSettingsTab) {
                        ((ModernFormSettingsTab<?>) editor).scrollToSection(hit.index);
                    }
                    break;
                }
            }
            return true;
        }
        return formBounds != null && formBounds.contains(mouseX, mouseY)
                ? editor.mouseClicked(mouseX, mouseY, mouseButton)
                : true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
        if (sequencePicker != null && sequencePicker.isOpen()) {
            sequencePicker.keyTyped(typedChar, keyCode);
            return true;
        }
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            navigationScroll = 0;
            return true;
        }
        if (categoryField != null && categoryField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_F
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            categoryField.setFocused(false);
            searchField.setFocused(true);
            return true;
        }
        return editor != null && editor.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (navigationActions.isOpen()) return true;
        if (navigationScrollbar.isDragging()) { navigationScrollbar.applyDrag(mouseX, mouseY); return true; }
        if (draggingNavigationDivider && clickedMouseButton == 0 && bounds != null) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, 150, 260, 96, 120);
            navigationRatio = split.ratio;
            return true;
        }
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return true;
        }
        return editor != null && editor.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (navigationScrollbar.isDragging()) { navigationScrollbar.endDrag(); return true; }
        if (state == 0 && draggingNavigationDivider) {
            MainUiLayoutManager.setModernSplitRatio("rules.conditional_execution.navigation", navigationRatio);
            draggingNavigationDivider = false;
            return true;
        }
        return editor != null && editor.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (navigationActions.wheel(wheel)) return true;
        if (wheel == 0) {
            return false;
        }
        if (sequencePicker != null && sequencePicker.isOpen()) {
            sequencePicker.handleMouseWheel(wheel, mouseX, mouseY);
            return true;
        }
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll);
            return true;
        }
        if (sectionNavBounds != null && sectionNavBounds.contains(mouseX, mouseY)) {
            sectionScroll = clamp(sectionScroll + (wheel > 0 ? -24 : 24), 0, sectionMaxScroll);
            return true;
        }
        return editor != null && editor.handleMouseWheel(wheel, mouseX, mouseY);
    }

    @Override
    public boolean handleEscape() {
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.handleEscape();
        }
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
            return true;
        }
        if (categoryField != null && categoryField.isFocused()) {
            categoryField.setFocused(false);
            return true;
        }
        if (draggingNavigationDivider) {
            draggingNavigationDivider = false;
            return true;
        }
        if (editor != null && editor.handleEscape()) {
            return true;
        }
        if (pendingDelete != null || pendingDeleteCategory != null) {
            clearPendingDelete();
            status = "gui.modern.conditional.u043";
            return true;
        }
        return false;
    }

    @Override
    public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true;
        return searchField != null && searchField.isFocused()
                || categoryField != null && categoryField.isFocused()
                || sequencePicker != null && sequencePicker.isTextInputFocused()
                || editor != null && editor.isTextInputFocused();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return sequencePicker != null && sequencePicker.isOpen()
                ? ""
                : editor == null ? "" : editor.getHoveredTooltip(mouseX, mouseY);
    }

    @Override
    public boolean isDirty() {
        return state.isDirty() || editor != null && editor.isDirty();
    }

    @Override
    public void save() {
        clearPendingDelete();
        if (!syncEditorDraft()) {
            status = editorInputError.isEmpty() ? "gui.modern.conditional.u044" : editorInputError;
            return;
        }
        if (editor instanceof ModernFormSettingsTab) {
            editor.save();
        }

        String validationError = validateAllRules();
        if (!validationError.isEmpty()) {
            status = validationError;
            return;
        }
        state.normalizeDrafts();
        List<ConditionalRule> liveRules = state.commitToLive();
        ConditionalExecutionHandler.removeBuiltinRules();
        ConditionalExecutionHandler.rules.clear();
        ConditionalExecutionHandler.rules.addAll(liveRules);
        synchronizeCategories();
        if (ConditionalExecutionHandler.isGloballyEnabled() != state.masterEnabled()) {
            ConditionalExecutionHandler.setGlobalEnabled(state.masterEnabled());
        } else {
            ConditionalExecutionHandler.saveConfig();
        }
        state.markCommitted();
        editorInputError = "";
        editorInputErrorKey = "";
        String warning = pathWarningSummary();
        status = warning.isEmpty() ? "gui.modern.conditional.u045"
                : ModernFormI18n.tr("gui.modern.conditional.fmt.saved_warning", warning);
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        clearPendingDelete();
        editorInputError = "";
        editorInputErrorKey = "";
        state.discard();
        rebuildEditor();
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (categoryField != null) {
            categoryField.setFocused(false);
            categoryField.setText(state.selectedCategory());
        }
    }

    private void ensureEditor() {
        if (editor == null || editorRule != state.selected()) {
            rebuildEditor();
        }
    }

    private void rebuildEditor() {
        editorRule = state.selected();
        editorInputError = "";
        editorInputErrorKey = "";
        editor = editorRule == null ? emptyEditor() : buildEditor(editorRule);
    }

    private boolean syncEditorDraft() {
        if (!(editor instanceof ModernFormSettingsTab)) {
            return true;
        }
        String editorCategoryBefore = editorRule == null ? "" : normalizeCategory(editorRule.category);
        if (!((ModernFormSettingsTab<?>) editor).tryApplyDraftValues()) {
            return false;
        }
        if (!editorInputError.isEmpty()) {
            ((ModernFormSettingsTab<?>) editor).showStatus(editorInputError);
            return false;
        }
        if (editorRule == null) {
            return true;
        }
        String category = state.ensureCategoryFor(editorRule);
        state.selectCategory(category);
        if (!editorCategoryBefore.equalsIgnoreCase(category) && categoryField != null && !categoryField.isFocused()) {
            categoryField.setText(category);
        }
        String error = validateRule(editorRule);
        if (!error.isEmpty()) {
            ((ModernFormSettingsTab<?>) editor).showStatus(error);
            return false;
        }
        return true;
    }

    private ModernSettingsTab buildEditor(final ConditionalRule rule) {
        ModernFormSettingsTab.Builder<ConditionalRule> builder = ModernFormSettingsTab
                .builder(safe(rule.name), "gui.modern.conditional.u046", "gui.modern.conditional.u047", adapter(rule))
                .footerVisible(false)
                .section("gui.modern.conditional.u003", "gui.modern.conditional.u048")
                .text("gui.modern.conditional.u049", "gui.modern.conditional.u050",
                        text(() -> rule.name, value -> rule.name = value), "gui.modern.conditional.u051", 96)
                .text("gui.modern.conditional.u052", "gui.modern.conditional.u053",
                        text(() -> rule.category, value -> {
                            rule.category = value;
                            state.ensureCategory(value);
                        }), CATEGORY_DEFAULT, 64)
                .toggle("gui.modern.conditional.u054", "gui.modern.conditional.u055",
                        bool(() -> rule.enabled, value -> rule.enabled = value))
                .section("gui.modern.conditional.u004", "gui.modern.conditional.u056")
                .readOnly("gui.modern.conditional.u057", "gui.modern.conditional.u058",
                        () -> "gui.modern.conditional.u059")
                .text("gui.modern.conditional.u060", "gui.modern.conditional.u061",
                        coordinateText(rule, "centerX", "gui.modern.conditional.u060", () -> rule.centerX, value -> rule.centerX = value),
                        "gui.modern.conditional.u062", 48)
                .text("gui.modern.conditional.u063", "gui.modern.conditional.u064",
                        coordinateText(rule, "centerY", "gui.modern.conditional.u063", () -> rule.centerY, value -> rule.centerY = value),
                        "gui.modern.conditional.u065", 48)
                .text("gui.modern.conditional.u066", "gui.modern.conditional.u067",
                        coordinateText(rule, "centerZ", "gui.modern.conditional.u066", () -> rule.centerZ, value -> rule.centerZ = value),
                        "gui.modern.conditional.u068", 48)
                .text("gui.modern.conditional.u069", "gui.modern.conditional.u070",
                        coordinateText(rule, "range", "gui.modern.conditional.u069", () -> rule.range, value -> rule.range = value),
                        "gui.modern.conditional.u071", 32)
                .action("gui.modern.conditional.u072", "gui.modern.conditional.u073", "gui.modern.conditional.u074",
                        ModernFormSettingsTab.ActionStyle.SECONDARY, tab -> fillPlayerCoordinates(rule))
                .section("gui.modern.conditional.u005", "gui.modern.conditional.u075")
                .integer("gui.modern.conditional.u076", "gui.modern.conditional.u077",
                        integer(() -> rule.loopCount, value -> rule.loopCount = value), -1, Integer.MAX_VALUE)
                .integer("gui.modern.conditional.u078", "gui.modern.conditional.u079",
                        integer(() -> rule.cooldownSeconds, value -> rule.cooldownSeconds = value), 0,
                        Integer.MAX_VALUE)
                .toggle("gui.modern.conditional.u080", "gui.modern.conditional.u081",
                        bool(() -> rule.stopOnExit, value -> rule.stopOnExit = value))
                .toggle("gui.modern.conditional.u082", "gui.modern.conditional.u083",
                        bool(() -> rule.runOncePerEntry, value -> rule.runOncePerEntry = value))
                .section("gui.modern.conditional.u006", "gui.modern.conditional.u084")
                .action("gui.modern.conditional.u085", "gui.modern.conditional.u086",
                        sequenceButtonLabel(rule.sequenceName), ModernFormSettingsTab.ActionStyle.SECONDARY,
                        tab -> openSequencePicker())
                .readOnly("gui.modern.conditional.u087", "gui.modern.conditional.u088",
                        () -> pathValidation(rule))
                .readOnly("gui.modern.conditional.u089", "gui.modern.conditional.u090", () -> sequenceStructure(rule));

        List<String> actionDescriptions = sequenceActionDescriptions(rule);
        if (actionDescriptions.isEmpty()) {
            builder.readOnly("gui.modern.conditional.u091", "gui.modern.conditional.u092", "gui.modern.conditional.u093");
        } else {
            for (int i = 0; i < actionDescriptions.size(); i++) {
                final String description = actionDescriptions.get(i);
                builder.readOnly("动作 " + (i + 1), "gui.modern.conditional.u094", description);
            }
            PathSequenceManager.PathSequence sequence = selectedSequence(rule);
            int totalActions = countActions(sequence);
            if (totalActions > actionDescriptions.size()) {
                builder.readOnly("gui.modern.conditional.u095", "gui.modern.conditional.u096",
                        "还有 " + (totalActions - actionDescriptions.size()) + " 个动作");
            }
        }

        builder.section("gui.modern.conditional.u007", "gui.modern.conditional.u097")
                .toggle("gui.modern.conditional.u098", "gui.modern.conditional.u099",
                        bool(() -> rule.antiStuckEnabled, value -> rule.antiStuckEnabled = value))
                .integer("gui.modern.conditional.u100", "gui.modern.conditional.u101",
                        integer(() -> rule.antiStuckTimeoutSeconds, value -> rule.antiStuckTimeoutSeconds = value), 1,
                        Integer.MAX_VALUE)
                .toggle("gui.modern.conditional.u102", "gui.modern.conditional.u103",
                        bool(() -> rule.visualizeRange, value -> rule.visualizeRange = value))
                .text("gui.modern.conditional.u104", "gui.modern.conditional.u105",
                        text(() -> ConditionalRule.normalizeColor(rule.visualizeBorderColor),
                                value -> rule.visualizeBorderColor = ConditionalRule.normalizeColor(value)),
                        "#4AA3FF", 16)
                .readOnly("gui.modern.conditional.u106", "gui.modern.conditional.u107",
                        () -> ConditionalRule.normalizeColor(rule.visualizeBorderColor))
                .section("gui.modern.conditional.u008", "gui.modern.conditional.u108")
                .readOnly("gui.modern.conditional.u109", "gui.modern.conditional.u110", () -> "X = " + displayCoordinate(rule.centerX)
                        + " · Y = " + displayCoordinate(rule.centerY))
                .readOnly("gui.modern.conditional.u111", "gui.modern.conditional.u112", () -> "Z = " + displayCoordinate(rule.centerZ)
                        + " · 半径 = " + displayCoordinate(rule.range))
                .readOnly("gui.modern.conditional.u113", "gui.modern.conditional.u114", () -> "循环 " + rule.loopCount + " · 冷却 "
                        + Math.max(0, rule.cooldownSeconds) + "s · 离开停止 " + yesNo(rule.stopOnExit)
                        + " · 入场一次 " + yesNo(rule.runOncePerEntry))
                .readOnly("gui.modern.conditional.u115", "gui.modern.conditional.u116", () -> validationSummary(rule));
        return builder.build();
    }

    private ModernFormSettingsTab.StateAdapter<ConditionalRule> adapter(final ConditionalRule rule) {
        return new ModernFormSettingsTab.StateAdapter<ConditionalRule>() {
            @Override
            public void load() {
            }

            @Override
            public ConditionalRule capture() {
                return copyRule(rule);
            }

            @Override
            public ConditionalRule copy(ConditionalRule value) {
                return copyRule(value);
            }

            @Override
            public void restore(ConditionalRule value) {
                if (value != null) {
                    applyRule(value, rule);
                }
            }

            @Override
            public void save() {
                rule.category = normalizeCategory(rule.category);
                rule.normalize();
            }

            @Override
            public void restoreDefaults() {
                applyRule(new ConditionalRule(), rule);
            }

            @Override
            public ConditionalRule createDefaults() {
                return new ConditionalRule();
            }
        };
    }

    private ModernSettingsTab emptyEditor() {
        return ModernFormSettingsTab.builder("gui.modern.conditional.u117", "gui.modern.conditional.u118", "gui.modern.conditional.u119").build();
    }

    private void openSequencePicker() {
        if (sequencePicker != null) {
            sequencePicker.open();
        }
    }

    private void selectSequence(String name) {
        if (editorRule == null) {
            return;
        }
        editorRule.sequenceName = safe(name).trim();
        restoreSectionAfterRebuild = true;
        rebuildEditor();
        status = editorRule.sequenceName.isEmpty() ? "gui.modern.conditional.u120"
                : ModernFormI18n.tr("gui.modern.conditional.fmt.path_selected", editorRule.sequenceName);
    }

    private void fillPlayerCoordinates(ConditionalRule rule) {
        if (rule == null) {
            return;
        }
        if (Minecraft.getMinecraft().player == null) {
            status = "gui.modern.conditional.u121";
            return;
        }
        rule.centerX = Minecraft.getMinecraft().player.posX;
        rule.centerY = Minecraft.getMinecraft().player.posY;
        rule.centerZ = Minecraft.getMinecraft().player.posZ;
        restoreSectionAfterRebuild = true;
        rebuildEditor();
        status = "gui.modern.conditional.u122";
    }

    private ModernFormSettingsTab.TextValue coordinateText(final ConditionalRule rule, final String key,
            final String label, final DoubleGet getter, final DoubleSet setter) {
        return new ModernFormSettingsTab.TextValue() {
            @Override
            public String get() {
                return inputNumber(getter.get());
            }

            @Override
            public void set(String value) {
                Double parsed = parseCoordinate(value, "range".equals(key));
                if (parsed == null) {
                    editorInputErrorKey = key;
                    editorInputError = ModernFormI18n.tr(label) + ModernFormI18n.tr("gui.modern.conditional.u123")
                            + ModernFormI18n.tr("range".equals(key) ? "gui.modern.conditional.u124" : "gui.modern.conditional.u125");
                    return;
                }
                setter.set(parsed.doubleValue());
                if (key.equals(editorInputErrorKey)) {
                    editorInputErrorKey = "";
                    editorInputError = "";
                }
            }
        };
    }

    private String validateAllRules() {
        int index = 1;
        for (ConditionalRule rule : state.rules()) {
            String error = validateRule(rule);
            if (!error.isEmpty()) {
                return "第 " + index + " 条规则：" + error;
            }
            index++;
        }
        return "";
    }

    private static String validateRule(ConditionalRule rule) {
        if (rule == null) {
            return "gui.modern.conditional.u126";
        }
        if (safe(rule.name).trim().isEmpty()) {
            return "gui.modern.conditional.u127";
        }
        if (!validCoordinate(rule.centerX) || !validCoordinate(rule.centerY) || !validCoordinate(rule.centerZ)) {
            return "gui.modern.conditional.u128";
        }
        if (Double.isNaN(rule.range) || Double.isInfinite(rule.range) || rule.range <= 0.0D) {
            return "gui.modern.conditional.u129";
        }
        if (rule.loopCount < -1) {
            return "gui.modern.conditional.u130";
        }
        if (rule.cooldownSeconds < 0) {
            return "gui.modern.conditional.u131";
        }
        if (rule.antiStuckTimeoutSeconds < 1) {
            return "gui.modern.conditional.u132";
        }
        return "";
    }

    private static boolean validCoordinate(double value) {
        return Double.isNaN(value) || !Double.isInfinite(value);
    }

    private String pathWarningSummary() {
        int empty = 0;
        int missing = 0;
        for (ConditionalRule rule : state.rules()) {
            if (safe(rule.sequenceName).trim().isEmpty()) {
                empty++;
            } else if (selectedSequence(rule) == null) {
                missing++;
            }
        }
        if (missing > 0 && empty > 0) {
            return missing + " 条路径不存在，" + empty + " 条规则未选择路径";
        }
        if (missing > 0) {
            return missing + " 条规则的路径不存在";
        }
        return empty > 0 ? empty + " 条规则未选择路径" : "";
    }

    private static String validationSummary(ConditionalRule rule) {
        String error = validateRule(rule);
        if (!error.isEmpty()) {
            return "错误 · " + error;
        }
        String path = pathValidation(rule);
        return path.startsWith("gui.modern.conditional.u133") ? path : "gui.modern.conditional.u134";
    }

    private static String pathValidation(ConditionalRule rule) {
        String name = safe(rule == null ? "" : rule.sequenceName).trim();
        if (name.isEmpty()) {
            return "gui.modern.conditional.u135";
        }
        return selectedSequence(rule) == null ? "gui.modern.conditional.u136" : "gui.modern.conditional.u137";
    }

    private static String sequenceStructure(ConditionalRule rule) {
        PathSequenceManager.PathSequence sequence = selectedSequence(rule);
        if (sequence == null) {
            return safe(rule == null ? "" : rule.sequenceName).trim().isEmpty() ? "gui.modern.conditional.u138"
                    : "gui.modern.conditional.u139";
        }
        int steps = sequence.getSteps() == null ? 0 : sequence.getSteps().size();
        int actions = countActions(sequence);
        int commands = countCommands(sequence);
        return steps + " 步 · " + actions + " 动作 · " + commands + " 条命令";
    }

    private static String executionSummary(ConditionalRule rule) {
        if (rule == null || safe(rule.sequenceName).trim().isEmpty()) {
            return "gui.modern.conditional.u138";
        }
        return safe(rule.sequenceName).trim() + " · " + sequenceStructure(rule);
    }

    private static List<String> sequenceActionDescriptions(ConditionalRule rule) {
        PathSequenceManager.PathSequence sequence = selectedSequence(rule);
        if (sequence == null || sequence.getSteps() == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (PathSequenceManager.PathStep step : sequence.getSteps()) {
            if (step == null || step.getActions() == null) {
                continue;
            }
            for (PathSequenceManager.ActionData action : step.getActions()) {
                if (action == null) {
                    continue;
                }
                String description;
                try {
                    description = action.getDescription();
                } catch (RuntimeException ignored) {
                    description = safe(action.type);
                }
                if (safe(description).trim().isEmpty()) {
                    description = safe(action.type).trim().isEmpty() ? "gui.modern.conditional.u140" : action.type;
                }
                result.add("步骤 " + (sequence.getSteps().indexOf(step) + 1) + " · " + description);
                if (result.size() >= MAX_ACTION_PREVIEW) {
                    return result;
                }
            }
        }
        return result;
    }

    private static PathSequenceManager.PathSequence selectedSequence(ConditionalRule rule) {
        String name = safe(rule == null ? "" : rule.sequenceName).trim();
        return name.isEmpty() ? null : PathSequenceManager.getSequence(name);
    }

    private static int countActions(PathSequenceManager.PathSequence sequence) {
        if (sequence == null || sequence.getSteps() == null) {
            return 0;
        }
        int count = 0;
        for (PathSequenceManager.PathStep step : sequence.getSteps()) {
            if (step != null && step.getActions() != null) {
                count += step.getActions().size();
            }
        }
        return count;
    }

    private static int countCommands(PathSequenceManager.PathSequence sequence) {
        if (sequence == null || sequence.getSteps() == null) {
            return 0;
        }
        int count = 0;
        for (PathSequenceManager.PathStep step : sequence.getSteps()) {
            if (step == null || step.getActions() == null) {
                continue;
            }
            for (PathSequenceManager.ActionData action : step.getActions()) {
                if (action != null && "command".equalsIgnoreCase(safe(action.type).trim())) {
                    count++;
                }
            }
        }
        return count;
    }

    private void addCategoryFromInput() {
        String value = safe(categoryField == null ? "" : categoryField.getText()).trim();
        if (value.isEmpty()) {
            status = "gui.modern.conditional.u141";
            return;
        }
        String added = state.addCategory(value);
        if (added == null) {
            status = "gui.modern.conditional.u142";
            return;
        }
        categoryField.setText(added);
        status = "gui.modern.conditional.u143";
    }

    private void renameSelectedCategoryFromInput() {
        String oldCategory = state.selectedCategory();
        String value = safe(categoryField == null ? "" : categoryField.getText()).trim();
        if (value.isEmpty()) {
            status = "gui.modern.conditional.u141";
            return;
        }
        if (!state.renameCategory(oldCategory, value)) {
            status = "gui.modern.conditional.u144";
            return;
        }
        categoryField.setText(state.selectedCategory());
        rebuildEditor();
        status = "gui.modern.conditional.u145";
    }

    private void deleteSelectedCategoryWithConfirmation() {
        String category = state.selectedCategory();
        if (isDefaultCategory(category)) {
            clearPendingDelete();
            status = "gui.modern.conditional.u146";
            return;
        }
        if (pendingDeleteCategory == null || !pendingDeleteCategory.equalsIgnoreCase(category)) {
            pendingDeleteCategory = category;
            pendingDelete = null;
            status = "gui.modern.conditional.u147";
            return;
        }
        if (!state.deleteCategory(category)) {
            clearPendingDelete();
            status = "gui.modern.conditional.u148";
            return;
        }
        clearPendingDelete();
        categoryField.setText(state.selectedCategory());
        rebuildEditor();
        status = "gui.modern.conditional.u149";
    }

    private void clearPendingDelete() {
        pendingDelete = null;
        pendingDeleteCategory = null;
    }

    private void synchronizeCategories() {
        List<String> sourceCategories = ConditionalExecutionHandler.getCategoriesSnapshot();
        for (String category : sourceCategories) {
            if (!containsCategory(state.categories(), category) && !isDefaultCategory(category)) {
                ConditionalExecutionHandler.deleteCategory(category);
            }
        }
        ConditionalExecutionHandler.replaceCategoryOrder(state.categories());
    }

    private List<ConditionalRule> matching(ConditionalWorkbenchState.Group group, String query) {
        return matching(group.rules(), query);
    }

    private static List<ConditionalRule> matching(List<ConditionalRule> rules, String query) {
        if (query.isEmpty()) {
            return rules;
        }
        List<ConditionalRule> result = new ArrayList<>();
        for (ConditionalRule rule : rules) {
            String name = safe(rule == null ? "" : rule.name).toLowerCase(Locale.ROOT);
            String sequence = safe(rule == null ? "" : rule.sequenceName).toLowerCase(Locale.ROOT);
            if (name.contains(query) || sequence.contains(query)) {
                result.add(rule);
            }
        }
        return result;
    }

    private String selectedCategory() {
        return state.selectedCategory();
    }

    private void drawGroup(FontRenderer font, ModernMainLayout.Rect rect, String name, int count, boolean collapsed,
            int mouseX, int mouseY) {
        boolean selected = state.selectedCategory().equalsIgnoreCase(name);
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(rect.x + 8, rect.y + 7, collapsed, ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawText(font, name, rect.x + 20, rect.y + 7, ModernUiRenderer.TEXT,
                Math.max(1, rect.width - 54));
        ModernUiRenderer.drawText(font, String.valueOf(count), rect.right() - 23, rect.y + 7,
                ModernUiRenderer.MUTED_TEXT, 18);
    }

    private void drawRule(FontRenderer font, ModernMainLayout.Rect rect, ConditionalRule rule, int mouseX, int mouseY) {
        boolean selected = rule == state.selected();
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 5,
                selected ? ModernUiRenderer.SURFACE_PRESSED
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(rect.x + 8, rect.y + 8,
                rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        String name = safe(rule.name).trim().isEmpty() ? ModernFormI18n.tr("gui.modern.conditional.u150") : rule.name;
        ModernUiRenderer.drawText(font, name, rect.x + 20, rect.y + 5,
                rule.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, rect.width - 28));
        ModernUiRenderer.drawText(font,
                "条件 · X " + displayCoordinate(rule.centerX) + " / Y " + displayCoordinate(rule.centerY)
                        + " / Z " + displayCoordinate(rule.centerZ) + " / r " + displayCoordinate(rule.range),
                rect.x + 8, rect.y + 18, ModernUiRenderer.SUBTLE_TEXT, Math.max(1, rect.width - 16));
        ModernUiRenderer.drawText(font,
                "路径 · " + (safe(rule.sequenceName).trim().isEmpty() ? ModernFormI18n.tr("gui.modern.conditional.u151") : rule.sequenceName)
                        + " · 冷却 " + Math.max(0, rule.cooldownSeconds) + "s · 循环 " + rule.loopCount,
                rect.x + 8, rect.y + 31, ModernUiRenderer.MUTED_TEXT, Math.max(1, rect.width - 16));
    }

    private void drawToggle(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean enabled,
            int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 5, ModernUiRenderer.SURFACE,
                rect.contains(mouseX, mouseY) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int trackWidth = Math.max(20, Math.min(28, Math.max(20, rect.width - 12)));
        int trackX = Math.max(rect.x + 3, rect.right() - trackWidth - 5);
        ModernUiRenderer.drawText(font, label, rect.x + 7, rect.y + 6, ModernUiRenderer.TEXT,
                Math.max(1, trackX - rect.x - 10));
        ModernUiRenderer.drawToggle(trackX, rect.y + 4, trackWidth, 12, enabled,
                rect.contains(mouseX, mouseY));
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary,
            int mouseX, int mouseY) {
        drawButton(font, rect, label, primary, mouseX, mouseY, true);
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary,
            int mouseX, int mouseY, boolean enabled) {
        if (rect == null) {
            return;
        }
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D25
                : primary ? (hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT)
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill,
                primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int textWidth = font.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, rect.x + Math.max(3, (rect.width - textWidth) / 2), rect.y + 6,
                !enabled ? ModernUiRenderer.MUTED_TEXT : primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT,
                Math.max(1, rect.width - 6));
    }

    private static List<ConditionalRule> readSourceRules() {
        ConditionalExecutionHandler.removeBuiltinRules();
        List<ConditionalRule> result = new ArrayList<>();
        for (ConditionalRule rule : ConditionalExecutionHandler.rules) {
            if (rule != null && !ConditionalExecutionHandler.isBuiltinRule(rule)) {
                result.add(rule);
            }
        }
        return result;
    }

    private static ConditionalRule copyRule(ConditionalRule source) {
        ConditionalRule copy = new ConditionalRule();
        if (source == null) {
            return copy;
        }
        copy.name = source.name;
        copy.category = source.category;
        copy.enabled = source.enabled;
        copy.centerX = source.centerX;
        copy.centerY = source.centerY;
        copy.centerZ = source.centerZ;
        copy.range = source.range;
        copy.sequenceName = source.sequenceName;
        copy.stopOnExit = source.stopOnExit;
        copy.loopCount = source.loopCount;
        copy.cooldownSeconds = source.cooldownSeconds;
        copy.runOncePerEntry = source.runOncePerEntry;
        copy.antiStuckEnabled = source.antiStuckEnabled;
        copy.antiStuckTimeoutSeconds = source.antiStuckTimeoutSeconds;
        copy.visualizeRange = source.visualizeRange;
        copy.visualizeBorderColor = source.visualizeBorderColor;
        return copy;
    }

    private static void applyRule(ConditionalRule source, ConditionalRule target) {
        if (source == null || target == null) {
            return;
        }
        target.name = source.name;
        target.category = normalizeCategory(source.category);
        target.enabled = source.enabled;
        target.centerX = source.centerX;
        target.centerY = source.centerY;
        target.centerZ = source.centerZ;
        target.range = source.range;
        target.sequenceName = source.sequenceName;
        target.stopOnExit = source.stopOnExit;
        target.loopCount = source.loopCount;
        target.cooldownSeconds = source.cooldownSeconds;
        target.runOncePerEntry = source.runOncePerEntry;
        target.antiStuckEnabled = source.antiStuckEnabled;
        target.antiStuckTimeoutSeconds = source.antiStuckTimeoutSeconds;
        target.visualizeRange = source.visualizeRange;
        target.visualizeBorderColor = source.visualizeBorderColor;
        target.normalize();
    }

    private static ModernFormSettingsTab.TextValue text(final StringGet getter, final StringSet setter) {
        return new ModernFormSettingsTab.TextValue() {
            @Override
            public String get() {
                return safe(getter.get());
            }

            @Override
            public void set(String value) {
                setter.set(value == null ? "" : value);
            }
        };
    }

    private static ModernFormSettingsTab.BooleanValue bool(final BoolGet getter, final BoolSet setter) {
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

    private static ModernFormSettingsTab.IntValue integer(final IntGet getter, final IntSet setter) {
        return new ModernFormSettingsTab.IntValue() {
            @Override
            public int get() {
                return getter.get();
            }

            @Override
            public void set(int value) {
                setter.set(value);
            }
        };
    }

    private static String sequenceButtonLabel(String value) {
        return safe(value).trim().isEmpty() ? "gui.modern.conditional.u152" : value.trim();
    }

    private static Double parseCoordinate(String raw, boolean range) {
        try {
            double value = Double.parseDouble(safe(raw).trim().replace(',', '.'));
            if (Double.isInfinite(value) || range && (Double.isNaN(value) || value <= 0.0D)) {
                return null;
            }
            return Double.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String inputNumber(double value) {
        return Double.isNaN(value) ? "NaN" : Double.toString(value);
    }

    private static String displayCoordinate(double value) {
        if (Double.isNaN(value)) {
            return "gui.modern.conditional.u153";
        }
        if (Double.isInfinite(value)) {
            return "gui.modern.conditional.u154";
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.indexOf('.') >= 0 && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String yesNo(boolean value) {
        return value ? "gui.modern.conditional.u155" : "gui.modern.conditional.u156";
    }

    private static String normalizeCategory(String category) {
        String normalized = safe(category).trim();
        if (normalized.isEmpty() || CATEGORY_BUILTIN.equalsIgnoreCase(normalized)) {
            return CATEGORY_DEFAULT;
        }
        return normalized;
    }

    private static boolean isDefaultCategory(String category) {
        return CATEGORY_DEFAULT.equalsIgnoreCase(normalizeCategory(category));
    }

    private static boolean containsCategory(List<String> categories, String category) {
        String normalized = normalizeCategory(category);
        if (categories == null) {
            return false;
        }
        for (String existing : categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect rect, int amount) {
        if (rect == null) {
            return new ModernMainLayout.Rect(0, 0, 1, 1);
        }
        return new ModernMainLayout.Rect(rect.x + amount, rect.y + amount,
                Math.max(1, rect.width - amount * 2), Math.max(1, rect.height - amount * 2));
    }

    private static boolean contains(ModernMainLayout.Rect rect, int x, int y) {
        return rect != null && rect.contains(x, y);
    }

    private static boolean intersectsVertically(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.bottom() > second.y && first.y < second.bottom();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface StringGet {
        String get();
    }

    private interface StringSet {
        void set(String value);
    }

    private interface BoolGet {
        boolean get();
    }

    private interface BoolSet {
        void set(boolean value);
    }

    private interface IntGet {
        int get();
    }

    private interface IntSet {
        void set(int value);
    }

    private interface DoubleGet {
        double get();
    }

    private interface DoubleSet {
        void set(double value);
    }

    private static final class RuleHit {
        private final ConditionalRule rule;
        private final ModernMainLayout.Rect bounds;

        private RuleHit(ConditionalRule rule, ModernMainLayout.Rect bounds) {
            this.rule = rule;
            this.bounds = bounds;
        }
    }

    private static final class GroupHit {
        private final String name;
        private final ModernMainLayout.Rect bounds;

        private GroupHit(String name, ModernMainLayout.Rect bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }

    private static final class SectionHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private SectionHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class ConditionalWorkbenchState {
        private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

        private final List<ConditionalRule> rules = new ArrayList<>();
        private final List<String> categoryOrder = new ArrayList<>();
        private final IdentityHashMap<ConditionalRule, ConditionalRule> liveSources = new IdentityHashMap<>();
        private List<ConditionalRule> original = new ArrayList<>();
        private List<ConditionalRule> originalLiveSources = new ArrayList<>();
        private List<String> originalCategories = new ArrayList<>();
        private ConditionalRule selected;
        private String selectedCategory = CATEGORY_DEFAULT;
        private boolean masterEnabled;
        private boolean originalMasterEnabled;

        private ConditionalWorkbenchState(List<ConditionalRule> source, List<String> categories,
                boolean masterEnabled) {
            this.masterEnabled = masterEnabled;
            this.originalMasterEnabled = masterEnabled;
            if (categories != null) {
                for (String category : categories) {
                    ensureCategory(category);
                }
            }
            if (source != null) {
                for (ConditionalRule sourceRule : source) {
                    if (sourceRule == null || ConditionalExecutionHandler.isBuiltinRule(sourceRule)) {
                        continue;
                    }
                    ConditionalRule draft = copyRule(sourceRule);
                    draft.category = normalizeCategory(draft.category);
                    draft.normalize();
                    rules.add(draft);
                    liveSources.put(draft, sourceRule);
                    ensureCategory(draft.category);
                }
            }
            if (categoryOrder.isEmpty()) {
                categoryOrder.add(CATEGORY_DEFAULT);
            }
            selected = rules.isEmpty() ? null : rules.get(0);
            selectedCategory = selected == null ? categoryOrder.get(0) : normalizeCategory(selected.category);
            markCommitted();
        }

        private List<ConditionalRule> rules() {
            return Collections.unmodifiableList(rules);
        }

        private List<String> categories() {
            return Collections.unmodifiableList(categoryOrder);
        }

        private ConditionalRule selected() {
            return selected;
        }

        private boolean masterEnabled() {
            return masterEnabled;
        }

        private void setMasterEnabled(boolean value) {
            masterEnabled = value;
        }

        private void select(ConditionalRule rule) {
            if (rules.contains(rule)) {
                selected = rule;
                selectCategory(rule.category);
            }
        }

        private String selectedCategory() {
            return normalizeCategory(selectedCategory);
        }

        private List<Group> groups() {
            for (ConditionalRule rule : rules) {
                ensureCategory(rule == null ? "" : rule.category);
            }
            LinkedHashMap<String, List<ConditionalRule>> grouped = new LinkedHashMap<>();
            for (String category : categoryOrder) {
                grouped.put(category, new ArrayList<ConditionalRule>());
            }
            for (ConditionalRule rule : rules) {
                String category = normalizeCategory(rule == null ? "" : rule.category);
                List<ConditionalRule> group = grouped.get(category);
                if (group == null) {
                    group = new ArrayList<>();
                    grouped.put(category, group);
                }
                group.add(rule);
            }
            List<Group> result = new ArrayList<>();
            for (Map.Entry<String, List<ConditionalRule>> entry : grouped.entrySet()) {
                result.add(new Group(entry.getKey(), entry.getValue()));
            }
            return result;
        }

        private void ensureCategory(String category) {
            String normalized = normalizeCategory(category);
            for (String existing : categoryOrder) {
                if (existing.equalsIgnoreCase(normalized)) {
                    return;
                }
            }
            categoryOrder.add(normalized);
        }

        private String ensureCategoryFor(ConditionalRule rule) {
            if (rule == null) {
                return selectedCategory();
            }
            String normalized = normalizeCategory(rule.category);
            String actual = normalized;
            for (String existing : categoryOrder) {
                if (existing.equalsIgnoreCase(normalized)) {
                    actual = existing;
                    break;
                }
            }
            if (!containsCategory(categoryOrder, actual)) {
                categoryOrder.add(actual);
            }
            rule.category = actual;
            return actual;
        }

        private void selectCategory(String category) {
            String normalized = normalizeCategory(category);
            for (String existing : categoryOrder) {
                if (existing.equalsIgnoreCase(normalized)) {
                    selectedCategory = existing;
                    return;
                }
            }
            categoryOrder.add(normalized);
            selectedCategory = normalized;
        }

        private String addCategory(String category) {
            String normalized = normalizeCategory(category);
            if (containsCategory(categoryOrder, normalized)) {
                return null;
            }
            categoryOrder.add(normalized);
            selectedCategory = normalized;
            return normalized;
        }

        private boolean renameCategory(String oldCategory, String newCategory) {
            String oldValue = normalizeCategory(oldCategory);
            String newValue = normalizeCategory(newCategory);
            int index = indexOfCategory(oldValue);
            if (index < 0) {
                return false;
            }
            if (oldValue.equalsIgnoreCase(newValue)) {
                selectedCategory = categoryOrder.get(index);
                return true;
            }
            if (containsCategory(categoryOrder, newValue)) {
                return false;
            }
            categoryOrder.set(index, newValue);
            for (ConditionalRule rule : rules) {
                if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(oldValue)) {
                    rule.category = newValue;
                }
            }
            selectedCategory = newValue;
            return true;
        }

        private boolean deleteCategory(String category) {
            String normalized = normalizeCategory(category);
            if (isDefaultCategory(normalized)) {
                return false;
            }
            int index = indexOfCategory(normalized);
            if (index < 0) {
                return false;
            }
            categoryOrder.remove(index);
            for (ConditionalRule rule : rules) {
                if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                    rule.category = CATEGORY_DEFAULT;
                }
            }
            ensureCategory(CATEGORY_DEFAULT);
            selectedCategory = CATEGORY_DEFAULT;
            return true;
        }

        private boolean moveCategory(int offset) {
            int index = indexOfCategory(selectedCategory);
            int target = index + offset;
            if (index < 0 || target < 0 || target >= categoryOrder.size()) {
                return false;
            }
            Collections.swap(categoryOrder, index, target);
            selectedCategory = categoryOrder.get(target);
            return true;
        }

        private boolean canMoveCategory(int offset) {
            int index = indexOfCategory(selectedCategory);
            int target = index + offset;
            return index >= 0 && target >= 0 && target < categoryOrder.size();
        }

        private int indexOfCategory(String category) {
            String normalized = normalizeCategory(category);
            for (int i = 0; i < categoryOrder.size(); i++) {
                if (categoryOrder.get(i).equalsIgnoreCase(normalized)) {
                    return i;
                }
            }
            return -1;
        }

        private void addRule(String category) {
            ConditionalRule rule = new ConditionalRule();
            rule.category = normalizeCategory(category);
            rule.name = uniqueName("gui.modern.conditional.u157");
            rules.add(rule);
            selected = rule;
            selectedCategory = rule.category;
            ensureCategory(rule.category);
        }

        private boolean duplicateSelected() {
            if (selected == null) {
                return false;
            }
            ConditionalRule copy = copyRule(selected);
            copy.name = uniqueName(ModernFormI18n.tr("gui.modern.wb.fmt.copy", safe(selected.name)));
            int index = rules.indexOf(selected);
            rules.add(Math.min(rules.size(), index + 1), copy);
            selected = copy;
            selectedCategory = normalizeCategory(copy.category);
            ensureCategory(copy.category);
            return true;
        }

        private boolean moveSelected(int offset) {
            if (!canMove(offset)) {
                return false;
            }
            int current = rules.indexOf(selected);
            Collections.swap(rules, current, current + offset);
            return true;
        }

        private boolean canMove(int offset) {
            if (selected == null) {
                return false;
            }
            int current = rules.indexOf(selected);
            return current >= 0 && current + offset >= 0 && current + offset < rules.size();
        }

        private void deleteSelected() {
            if (selected == null) {
                return;
            }
            int index = rules.indexOf(selected);
            liveSources.remove(selected);
            rules.remove(selected);
            if (rules.isEmpty()) {
                selected = null;
                selectedCategory = categoryOrder.isEmpty() ? CATEGORY_DEFAULT : categoryOrder.get(0);
            } else {
                selected = rules.get(Math.min(Math.max(0, index), rules.size() - 1));
                selectedCategory = normalizeCategory(selected.category);
            }
        }

        private String uniqueName(String base) {
            String normalizedBase = safe(base).trim();
            if (normalizedBase.isEmpty()) {
                normalizedBase = "gui.modern.conditional.u158";
            }
            String candidate = normalizedBase;
            int suffix = 2;
            while (findByName(candidate) != null) {
                candidate = normalizedBase + " " + suffix++;
            }
            return candidate;
        }

        private ConditionalRule findByName(String name) {
            for (ConditionalRule rule : rules) {
                if (safe(rule == null ? "" : rule.name).equalsIgnoreCase(safe(name))) {
                    return rule;
                }
            }
            return null;
        }

        private void normalizeDrafts() {
            for (ConditionalRule rule : rules) {
                if (rule != null) {
                    rule.category = normalizeCategory(rule.category);
                    rule.normalize();
                }
            }
        }

        private List<ConditionalRule> commitToLive() {
            List<ConditionalRule> result = new ArrayList<>();
            for (ConditionalRule draft : rules) {
                ConditionalRule live = liveSources.get(draft);
                if (live == null) {
                    live = new ConditionalRule();
                    liveSources.put(draft, live);
                }
                applyRule(draft, live);
                result.add(live);
            }
            return result;
        }

        private boolean isDirty() {
            return masterEnabled != originalMasterEnabled
                    || !GSON.toJson(original).equals(GSON.toJson(rules))
                    || !originalCategories.equals(categoryOrder);
        }

        private void markCommitted() {
            original = copyRules(rules);
            originalCategories = new ArrayList<>(categoryOrder);
            originalLiveSources = new ArrayList<>();
            for (ConditionalRule rule : rules) {
                originalLiveSources.add(liveSources.get(rule));
            }
            originalMasterEnabled = masterEnabled;
        }

        private void discard() {
            String selectedName = selected == null ? "" : safe(selected.name);
            rules.clear();
            liveSources.clear();
            categoryOrder.clear();
            categoryOrder.addAll(originalCategories);
            for (int i = 0; i < original.size(); i++) {
                ConditionalRule draft = copyRule(original.get(i));
                rules.add(draft);
                ConditionalRule live = i < originalLiveSources.size() ? originalLiveSources.get(i) : null;
                if (live != null) {
                    liveSources.put(draft, live);
                }
            }
            selected = null;
            for (ConditionalRule rule : rules) {
                if (!selectedName.isEmpty() && selectedName.equalsIgnoreCase(safe(rule.name))) {
                    selected = rule;
                    break;
                }
            }
            if (selected == null && !rules.isEmpty()) {
                selected = rules.get(0);
            }
            selectedCategory = selected == null
                    ? (categoryOrder.isEmpty() ? CATEGORY_DEFAULT : categoryOrder.get(0))
                    : normalizeCategory(selected.category);
            masterEnabled = originalMasterEnabled;
        }

        private static List<ConditionalRule> copyRules(List<ConditionalRule> source) {
            List<ConditionalRule> result = new ArrayList<>();
            if (source != null) {
                for (ConditionalRule rule : source) {
                    if (rule != null) {
                        result.add(copyRule(rule));
                    }
                }
            }
            return result;
        }

        private static final class Group {
            private final String name;
            private final List<ConditionalRule> rules;

            private Group(String name, List<ConditionalRule> rules) {
                this.name = name;
                this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            }

            private String name() {
                return name;
            }

            private List<ConditionalRule> rules() {
                return rules;
            }
        }
    }

    /** In-place path picker; it never leaves the modern tab or creates a GUI screen. */
    private static final class SequencePicker {
        private interface Selection {
            void accept(String name);
        }

        private final Selection selection;
        private final Map<String, List<String>> groups = new LinkedHashMap<>();
        private GuiTextField searchField;
        private ModernMainLayout.Rect pickerBounds;
        private ModernMainLayout.Rect searchBounds;
        private ModernMainLayout.Rect categoryBounds;
        private ModernMainLayout.Rect sequenceBounds;
        private ModernMainLayout.Rect clearBounds;
        private ModernMainLayout.Rect cancelBounds;
        private String selectedCategory = "";
        private int categoryScroll;
        private int sequenceScroll;
        private boolean open;

        private SequencePicker(Selection selection) {
            this.selection = selection;
        }

        private void ensureInitialized(FontRenderer fontRenderer) {
            if (searchField != null) {
                return;
            }
            searchField = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
            searchField.setEnableBackgroundDrawing(false);
            searchField.setMaxStringLength(96);
            searchField.setTextColor(ModernUiRenderer.TEXT);
            searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        }

        private void updateScreen() {
            if (searchField != null) {
                searchField.updateCursorCounter();
            }
        }

        private void open() {
            groups.clear();
            for (PathSequenceManager.PathSequence sequence : PathSequenceManager.getAllVisibleSequences()) {
                if (sequence == null || safe(sequence.getName()).trim().isEmpty()) {
                    continue;
                }
                String category = safe(sequence.getCategory()).trim();
                if (category.isEmpty()) {
                    category = CATEGORY_DEFAULT;
                }
                List<String> names = groups.get(category);
                if (names == null) {
                    names = new ArrayList<>();
                    groups.put(category, names);
                }
                names.add(sequence.getName());
            }
            selectedCategory = groups.isEmpty() ? "" : groups.keySet().iterator().next();
            categoryScroll = 0;
            sequenceScroll = 0;
            open = true;
            if (searchField != null) {
                searchField.setText("");
                searchField.setFocused(false);
            }
        }

        private boolean isOpen() {
            return open;
        }

        private void draw(FontRenderer font, ModernMainLayout.Rect host, String title, int mouseX, int mouseY) {
            if (!open || host == null) {
                return;
            }
            ModernUiRenderer.drawBackdropOverlay(host, 0xB80A1016);
            int width = Math.max(1, Math.min(430, host.width - 12));
            int height = Math.max(1, Math.min(330, host.height - 12));
            pickerBounds = new ModernMainLayout.Rect(host.x + (host.width - width) / 2,
                    host.y + (host.height - height) / 2, width, height);
            ModernUiRenderer.drawPanel(pickerBounds.x, pickerBounds.y, width, height, 7,
                    ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
            ModernUiRenderer.drawText(font, title, pickerBounds.x + 12, pickerBounds.y + 10,
                    ModernUiRenderer.TEXT, Math.max(1, width - 24));
            int searchY = height >= 215 ? pickerBounds.y + 43 : pickerBounds.y + 27;
            searchBounds = new ModernMainLayout.Rect(pickerBounds.x + 10, searchY,
                    Math.max(1, width - 20), 20);
            searchField.x = searchBounds.x + 7;
            searchField.y = searchBounds.y + 5;
            searchField.width = Math.max(1, searchBounds.width - 14);
            searchField.height = 12;
            ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height,
                    4, ModernUiRenderer.SURFACE,
                    searchField.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            if (searchField.getText().isEmpty() && !searchField.isFocused()) {
                ModernUiRenderer.drawText(font, "gui.modern.conditional.u159", searchField.x, searchField.y,
                        ModernUiRenderer.MUTED_TEXT, Math.max(1, searchField.width));
            }
            ModernUiRenderer.drawTextField(searchField);

            int footerY = pickerBounds.bottom() - 27;
            int contentTop = searchBounds.bottom() + 6;
            int contentHeight = Math.max(1, footerY - contentTop - 6);
            boolean compact = width < 250;
            if (compact) {
                categoryBounds = null;
                sequenceBounds = new ModernMainLayout.Rect(pickerBounds.x + 10, contentTop,
                        Math.max(1, width - 20), contentHeight);
                drawCompactSequences(font, mouseX, mouseY);
            } else {
                int categoryWidth = Math.min(116, Math.max(74, width / 3));
                categoryBounds = new ModernMainLayout.Rect(pickerBounds.x + 10, contentTop, categoryWidth,
                        contentHeight);
                sequenceBounds = new ModernMainLayout.Rect(categoryBounds.right() + 8, contentTop,
                        Math.max(1, pickerBounds.right() - categoryBounds.right() - 18), contentHeight);
                drawCategories(font, mouseX, mouseY);
                drawSequences(font, mouseX, mouseY);
            }
            int buttonGap = 8;
            int buttonWidth = Math.max(1, (width - 20 - buttonGap) / 2);
            clearBounds = new ModernMainLayout.Rect(pickerBounds.x + 10, footerY, buttonWidth, 20);
            cancelBounds = new ModernMainLayout.Rect(clearBounds.right() + buttonGap, footerY,
                    Math.max(1, pickerBounds.right() - clearBounds.right() - buttonGap - 10), 20);
            drawButton(font, clearBounds, "gui.modern.conditional.u160", mouseX, mouseY);
            drawButton(font, cancelBounds, "gui.modern.conditional.u161", mouseX, mouseY);
        }

        private void drawCategories(FontRenderer font, int mouseX, int mouseY) {
            ModernUiRenderer.drawSubtlePanel(categoryBounds.x, categoryBounds.y, categoryBounds.width,
                    categoryBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, "gui.modern.conditional.u162", categoryBounds.x + 8, categoryBounds.y + 8,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(1, categoryBounds.width - 16));
            List<String> visible = visibleCategories();
            int rowHeight = 25;
            int rows = Math.max(1, (categoryBounds.height - 28) / rowHeight);
            categoryScroll = clamp(categoryScroll, 0, Math.max(0, visible.size() - rows));
            ModernMainLayout.Rect clip = new ModernMainLayout.Rect(categoryBounds.x + 3, categoryBounds.y + 26,
                    Math.max(1, categoryBounds.width - 6), Math.max(1, categoryBounds.height - 29));
            ModernUiRenderer.beginClip(clip);
            for (int i = categoryScroll; i < visible.size() && i < categoryScroll + rows; i++) {
                int y = clip.y + (i - categoryScroll) * rowHeight;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(categoryBounds.x + 6, y,
                        Math.max(1, categoryBounds.width - 12), 22);
                boolean selected = visible.get(i).equals(selectedCategory);
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        selected ? ModernUiRenderer.ACCENT_DIM
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(font, visible.get(i), row.x + 7, row.y + 6,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(1, row.width - 14));
            }
            ModernUiRenderer.endClip();
        }

        private void drawSequences(FontRenderer font, int mouseX, int mouseY) {
            ModernUiRenderer.drawSubtlePanel(sequenceBounds.x, sequenceBounds.y, sequenceBounds.width,
                    sequenceBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            List<String> names = filteredNames(selectedCategory, query());
            ModernUiRenderer.drawText(font, selectedCategory.isEmpty() ? "gui.modern.conditional.u163" : selectedCategory,
                    sequenceBounds.x + 8, sequenceBounds.y + 8, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(1, sequenceBounds.width - 16));
            int rowHeight = 27;
            int rows = Math.max(1, (sequenceBounds.height - 30) / rowHeight);
            sequenceScroll = clamp(sequenceScroll, 0, Math.max(0, names.size() - rows));
            ModernMainLayout.Rect clip = new ModernMainLayout.Rect(sequenceBounds.x + 3, sequenceBounds.y + 26,
                    Math.max(1, sequenceBounds.width - 6), Math.max(1, sequenceBounds.height - 29));
            ModernUiRenderer.beginClip(clip);
            for (int i = sequenceScroll; i < names.size() && i < sequenceScroll + rows; i++) {
                int y = clip.y + (i - sequenceScroll) * rowHeight;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(sequenceBounds.x + 6, y,
                        Math.max(1, sequenceBounds.width - 12), 24);
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(font, names.get(i), row.x + 8, row.y + 7, ModernUiRenderer.TEXT,
                        Math.max(1, row.width - 16));
            }
            ModernUiRenderer.endClip();
            if (names.isEmpty()) {
                ModernUiRenderer.drawText(font, "gui.modern.conditional.u164", sequenceBounds.x + 8,
                        sequenceBounds.y + 36, ModernUiRenderer.MUTED_TEXT,
                        Math.max(1, sequenceBounds.width - 16));
            }
        }

        private void drawCompactSequences(FontRenderer font, int mouseX, int mouseY) {
            ModernUiRenderer.drawSubtlePanel(sequenceBounds.x, sequenceBounds.y, sequenceBounds.width,
                    sequenceBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            List<SequenceRef> options = compactOptions(query());
            int rowHeight = 27;
            int rows = Math.max(1, (sequenceBounds.height - 10) / rowHeight);
            sequenceScroll = clamp(sequenceScroll, 0, Math.max(0, options.size() - rows));
            ModernMainLayout.Rect clip = new ModernMainLayout.Rect(sequenceBounds.x + 3, sequenceBounds.y + 4,
                    Math.max(1, sequenceBounds.width - 6), Math.max(1, sequenceBounds.height - 8));
            ModernUiRenderer.beginClip(clip);
            for (int i = sequenceScroll; i < options.size() && i < sequenceScroll + rows; i++) {
                int y = clip.y + (i - sequenceScroll) * rowHeight;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(sequenceBounds.x + 6, y,
                        Math.max(1, sequenceBounds.width - 12), 24);
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(font, options.get(i).category + " · " + options.get(i).name,
                        row.x + 7, row.y + 7, ModernUiRenderer.TEXT, Math.max(1, row.width - 14));
            }
            ModernUiRenderer.endClip();
            if (options.isEmpty()) {
                ModernUiRenderer.drawText(font, "gui.modern.conditional.u165", sequenceBounds.x + 8, sequenceBounds.y + 12,
                        ModernUiRenderer.MUTED_TEXT, Math.max(1, sequenceBounds.width - 16));
            }
        }

        private boolean mouseClicked(int mouseX, int mouseY) {
            if (!open) {
                return false;
            }
            if (contains(clearBounds, mouseX, mouseY)) {
                if (selection != null) {
                    selection.accept("");
                }
                close();
                return true;
            }
            if (contains(cancelBounds, mouseX, mouseY)) {
                close();
                return true;
            }
            if (contains(searchBounds, mouseX, mouseY)) {
                searchField.setFocused(true);
                searchField.mouseClicked(mouseX, mouseY, 0);
                return true;
            }
            if (categoryBounds != null && categoryBounds.contains(mouseX, mouseY)) {
                List<String> visible = visibleCategories();
                int rows = Math.max(1, (categoryBounds.height - 28) / 25);
                int index = (mouseY - categoryBounds.y - 26) / 25 + categoryScroll;
                if (index >= 0 && index < visible.size() && index < categoryScroll + rows) {
                    selectedCategory = visible.get(index);
                    sequenceScroll = 0;
                }
                return true;
            }
            if (sequenceBounds != null && sequenceBounds.contains(mouseX, mouseY)) {
                if (categoryBounds == null) {
                    List<SequenceRef> options = compactOptions(query());
                    int index = (mouseY - sequenceBounds.y - 4) / 27 + sequenceScroll;
                    if (index >= 0 && index < options.size()) {
                        if (selection != null) {
                            selection.accept(options.get(index).name);
                        }
                        close();
                    }
                } else {
                    List<String> names = filteredNames(selectedCategory, query());
                    int index = (mouseY - sequenceBounds.y - 26) / 27 + sequenceScroll;
                    if (index >= 0 && index < names.size()) {
                        if (selection != null) {
                            selection.accept(names.get(index));
                        }
                        close();
                    }
                }
                return true;
            }
            return true;
        }

        private boolean keyTyped(char typedChar, int keyCode) {
            if (!open) {
                return false;
            }
            if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
                sequenceScroll = 0;
            }
            return true;
        }

        private boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
            if (!open || wheel == 0) {
                return false;
            }
            if (categoryBounds != null && categoryBounds.contains(mouseX, mouseY)) {
                int rows = Math.max(1, (categoryBounds.height - 28) / 25);
                categoryScroll = clamp(categoryScroll + (wheel > 0 ? -1 : 1), 0,
                        Math.max(0, visibleCategories().size() - rows));
                return true;
            }
            if (sequenceBounds != null && sequenceBounds.contains(mouseX, mouseY)) {
                int rows = Math.max(1, (sequenceBounds.height - 30) / 27);
                int count = categoryBounds == null ? compactOptions(query()).size()
                        : filteredNames(selectedCategory, query()).size();
                sequenceScroll = clamp(sequenceScroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, count - rows));
                return true;
            }
            return true;
        }

        private boolean handleEscape() {
            if (!open) {
                return false;
            }
            close();
            return true;
        }

        private boolean isTextInputFocused() {
            return open && searchField != null && searchField.isFocused();
        }

        private void close() {
            open = false;
            if (searchField != null) {
                searchField.setFocused(false);
            }
        }

        private List<String> visibleCategories() {
            String query = query();
            if (query.isEmpty()) {
                return new ArrayList<>(groups.keySet());
            }
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).contains(query)
                        || !filteredNames(entry.getKey(), query).isEmpty()) {
                    result.add(entry.getKey());
                }
            }
            if (!result.contains(selectedCategory) && !result.isEmpty()) {
                selectedCategory = result.get(0);
            }
            return result;
        }

        private List<String> filteredNames(String category, String query) {
            List<String> source = groups.get(category);
            if (source == null) {
                return Collections.emptyList();
            }
            if (query.isEmpty()) {
                return source;
            }
            List<String> result = new ArrayList<>();
            for (String name : source) {
                if (safe(name).toLowerCase(Locale.ROOT).contains(query)) {
                    result.add(name);
                }
            }
            return result;
        }

        private List<SequenceRef> compactOptions(String query) {
            List<SequenceRef> result = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                for (String name : entry.getValue()) {
                    if (query.isEmpty() || safe(name).toLowerCase(Locale.ROOT).contains(query)
                            || entry.getKey().toLowerCase(Locale.ROOT).contains(query)) {
                        result.add(new SequenceRef(entry.getKey(), name));
                    }
                }
            }
            return result;
        }

        private String query() {
            return searchField == null ? "" : safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        }

        private static void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, int mouseX,
                int mouseY) {
            boolean hovered = rect.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, rect.x + 6, rect.y + 6, ModernUiRenderer.TEXT,
                    Math.max(1, rect.width - 12));
        }

        private static final class SequenceRef {
            private final String category;
            private final String name;

            private SequenceRef(String category, String name) {
                this.category = category;
                this.name = name;
            }
        }
    }
}
