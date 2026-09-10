package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.input.Keyboard;

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
import com.zszl.zszlScriptMod.handlers.AutoPickupHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.system.AutoPickupRule;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Native list-detail editor for automatic pickup rules. */
public final class ModernAutoPickupWorkbenchTab implements ModernSettingsTab {
    private final com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar navigationScrollbar = new com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar();
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("autopickup");

    private ModernMainLayout.Rect masterBounds;

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 30;
    private static final int GROUP_HEIGHT = ModernTreeGuide.GROUP_HEIGHT;
    private static final int RULE_HEIGHT = ModernTreeGuide.ITEM_HEIGHT;
    private static final int CATEGORY_TOOLBAR_HEIGHT = 24;
    private static final int RULE_ACTION_RESERVE = 51;
    private static final int INVENTORY_SLOT_COUNT = AutoPickupRule.INVENTORY_SLOT_COUNT;
    private static final int INVENTORY_SLOT_COLUMNS = AutoPickupRule.INVENTORY_SLOT_COLUMNS;
    private static final int INVENTORY_SLOT_ROWS = AutoPickupRule.INVENTORY_SLOT_ROWS;
    private static final int ENTRY_ROW_HEIGHT = 24;
    private static final int ENTRY_VISIBLE_ROWS = 7;
    private static final int TAG_ROW_HEIGHT = 19;
    private static final int TEXT_FIELD_HEIGHT = 18;

    private enum Overlay {
        NONE, FILTER_LIST, FILTER_EDITOR, ACTION_LIST, ACTION_EDITOR, SLOTS
    }

    private enum SequenceTarget {
        NONE, POST_PICKUP, ANTI_STUCK, ACTION
    }

    private final AutoPickupWorkbenchState state = new AutoPickupWorkbenchState(
            new ArrayList<AutoPickupRule>(AutoPickupHandler.rules), AutoPickupHandler.getCategoriesSnapshot(),
            AutoPickupHandler.globalEnabled);
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final RuleTreeToggle ruleToggle = new RuleTreeToggle();
    private final AutoEscapeSequencePicker sequencePicker = new AutoEscapeSequencePicker(this::selectSequence);

    private final java.util.Map<Object, com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState> editorPositions = new java.util.WeakHashMap<>();
    private ModernSettingsTab editor;
    private AutoPickupRule editorRule;
    private GuiTextField searchField;
    private GuiTextField categoryField;

    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect navigationBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect formBounds;
    private ModernMainLayout.Rect navigationDividerBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect categoryNameBounds;
    private ModernMainLayout.Rect categoryAddBounds;
    private ModernMainLayout.Rect categoryRenameBounds;
    private ModernMainLayout.Rect categoryDeleteBounds;
    private ModernMainLayout.Rect categoryUpBounds;
    private ModernMainLayout.Rect categoryDownBounds;
    private ModernMainLayout.Rect moveUpBounds;
    private ModernMainLayout.Rect moveDownBounds;
    private ModernMainLayout.Rect addBounds;
    private ModernMainLayout.Rect duplicateBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;

    private double navigationRatio = 0.28D;
    private boolean layoutPreferencesLoaded;
    private int navigationScroll;
    private int navigationMaxScroll;
    private boolean draggingNavigationDivider;
    private AutoPickupRule pendingDeleteRule;
    private String pendingDeleteCategory;
    private String status = "";

    private boolean inlineEntryEditor;
    private Overlay overlay = Overlay.NONE;
    private boolean overlayWhitelist;
    private int overlayEntryIndex = -1;
    private int overlaySelectedEntryIndex = -1;
    private int overlayScroll;
    private int overlayMaxScroll;
    private int overlayPendingDeleteIndex = -1;
    private boolean overlayEntryDragging;
    private int overlayDragIndex = -1;
    private AutoPickupRule.ItemMatchEntry overlayFilter;
    private AutoPickupRule.PickupActionEntry overlayAction;
    private AutoPickupRule.PickupActionEntry overlayActionOriginal;
    private final List<String> overlayNbtTags = new ArrayList<>();
    private final List<GuiTextField> overlayFields = new ArrayList<>();
    private GuiTextField overlayKeywordField;
    private GuiTextField overlayNbtInputField;
    private GuiTextField overlayDelayField;
    private String overlayValidation = "";
    private SequenceTarget sequenceTarget = SequenceTarget.NONE;

    private ModernMainLayout.Rect overlayBounds;
    private ModernMainLayout.Rect overlayListBounds;
    private ModernMainLayout.Rect overlaySaveBounds;
    private ModernMainLayout.Rect overlayCancelBounds;
    private ModernMainLayout.Rect overlayAddBounds;
    private ModernMainLayout.Rect overlayEditBounds;
    private ModernMainLayout.Rect overlayDeleteBounds;
    private ModernMainLayout.Rect overlayUpBounds;
    private ModernMainLayout.Rect overlayDownBounds;
    private ModernMainLayout.Rect overlaySequenceBounds;
    private ModernMainLayout.Rect overlayNbtListBounds;
    private ModernMainLayout.Rect overlayNbtAddBounds;
    private ModernMainLayout.Rect overlaySlotGridBounds;
    private ModernMainLayout.Rect overlaySlotsDoneBounds;
    private ModernMainLayout.Rect overlaySlotsCancelBounds;
    private ModernMainLayout.Rect overlaySlotsClearBounds;
    private ModernMainLayout.Rect overlaySlotsAllBounds;
    private final List<TagHit> tagHits = new ArrayList<>();
    private final List<SlotHit> slotHits = new ArrayList<>();
    private final LinkedHashSet<Integer> slotDraft = new LinkedHashSet<>();
    private final LinkedHashSet<Integer> slotDragSnapshot = new LinkedHashSet<>();
    private boolean slotDragging;
    private boolean slotDragAddMode;
    private int slotDragAnchor = -1;
    private int slotDragCurrent = -1;
    private String overlayTitle = "";
    private String editorInputError = "";

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("rules.auto_pickup.navigation", navigationRatio);
            layoutPreferencesLoaded = true;
        }
        if (searchField == null) {
            searchField = createField(fontRenderer, 120);
        }
        if (categoryField == null) {
            categoryField = createField(fontRenderer, 80);
            categoryField.setText(state.selectedCategory());
        }
        sequencePicker.ensureInitialized(fontRenderer);
        ensureEditor();
        editor.ensureInitialized(fontRenderer);
        ensureOverlayFields(fontRenderer);
    }

    @Override
    public void updateScreen() {
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
        if (categoryField != null) {
            categoryField.updateCursorCounter();
        }
        sequencePicker.updateScreen();
        if (editor != null) {
            editor.updateScreen();
        }
        for (GuiTextField field : overlayFields) {
            if (field != null) {
                field.updateCursorCounter();
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
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, navigationRatio, 150, 270, 110, 160);
        navigationRatio = split.ratio;
        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + TOP_INSET,
                split.firstWidth, Math.max(1, bounds.height - TOP_INSET - FOOTER_HEIGHT - 6));
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 0,
                navigationBounds.y, navigationBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationDividerBounds.right(), navigationBounds.y,
                Math.max(1, bounds.right() - navigationDividerBounds.right() - 8), navigationBounds.height);

        formBounds = editorBounds;

        drawNavigation(fontRenderer, mouseX, mouseY);
        if (editor != null) {
            editor.draw(fontRenderer, formBounds, mouseX, mouseY);
        }

        drawFooter(fontRenderer, mouseX, mouseY);
        drawOverlay(fontRenderer, mouseX, mouseY);
        if (sequencePicker.isOpen()) {
            ModernUiRenderer.beginClip(formBounds);
            sequencePicker.draw(fontRenderer, formBounds, sequencePickerTitle(), mouseX, mouseY);
            ModernUiRenderer.endClip();
        }
        navigationActions.drawOverlay(mouseX, mouseY);
    }


    private void configureNavigationActions() {
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> state.selected() != null, () -> {
            if (prepareDraft() && state.selected() != null) {
                navigationActions.choose("移动到分类", state.categories(), state.selected().category, value -> {
                    state.selected().category = value;
                    state.selectCategory(value);
                    categoryField.setText(value);
                    rebuildEditor();
                });
            }
        });

        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true,
                () -> navigationActions.prompt("gui.modern.nav.category_add", "", this::addCategoryValue));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", state.selectedCategory(), value -> { categoryField.setText(value); navigationCategoryRename(); }));
        navigationActions.action("category_delete", state.selectedCategory().equalsIgnoreCase(pendingDeleteCategory) ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.category_delete", false, true, () -> !isDefaultCategory(state.selectedCategory()), this::navigationCategoryDelete);
        navigationActions.action("category_up", "gui.modern.nav.category_up", false, false, () -> canMoveCategory(-1), this::navigationCategoryUp);
        navigationActions.action("category_down", "gui.modern.nav.category_down", false, false, () -> canMoveCategory(1), this::navigationCategoryDown);
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> state.selected() != null, this::navigationCopy);
        navigationActions.action("delete", state.selected() != null && pendingDeleteRule == state.selected() ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.delete", true, true, () -> state.selected() != null, this::navigationDelete);
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> state.canMoveSelectedRule(-1), this::navigationUp);
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> state.canMoveSelectedRule(1), this::navigationDown);
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> state.selected() != null, () -> { if (prepareDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.rename", state.selected().name, value -> { state.selected().name = value; rebuildEditor(); }); });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> state.selected() != null, () -> { if (prepareDraft() && state.selected() != null) { state.selected().enabled = !state.selected().enabled; rebuildEditor(); } });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedGroups.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (AutoPickupWorkbenchState.Group group : state.groups()) collapsedGroups.add(group.name()); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationContext(int mouseX, int mouseY) {
        if (!navigationActions.inTree(mouseX, mouseY)) return false;
        if (!prepareDraft()) return true;
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
            if (prepareDraft()) {
                clearPendingDelete();
                state.addRule(state.selectedCategory());
                categoryField.setText(state.selectedCategory());
                rebuildEditor();
                status = "gui.modern.pickup_wb.u030";
            }
            return true;
        }

    private boolean navigationCopy() {
            if (prepareDraft() && state.selected() != null) {
                clearPendingDelete();
                state.duplicateSelected();
                categoryField.setText(state.selectedCategory());
                rebuildEditor();
                status = "gui.modern.pickup_wb.u031";
            }
            return true;
        }

    private boolean navigationDelete() {
            if (!prepareDraft() || state.selected() == null) {
                return true;
            }
            if (pendingDeleteRule != state.selected()) {
                pendingDeleteRule = state.selected();
                pendingDeleteCategory = null;
                status = "gui.modern.pickup_wb.u032";
                return true;
            }
            state.deleteSelected();
            clearPendingDelete();
            categoryField.setText(state.selectedCategory());
            rebuildEditor();
            status = "gui.modern.pickup_wb.u033";
            return true;
        }

    private boolean navigationUp() {
            if (prepareDraft() && state.moveSelectedRule(-1)) {
                status = "gui.modern.pickup_wb.u029";
            }
            return true;
        }

    private boolean navigationDown() {
            if (prepareDraft() && state.moveSelectedRule(1)) {
                status = "gui.modern.pickup_wb.u029";
            }
            return true;
        }

    private boolean navigationCategoryAdd() {
            if (prepareDraft()) {
                addCategoryFromInput();
            }
            return true;
        }

    private boolean navigationCategoryRename() {
            if (prepareDraft()) {
                renameSelectedCategoryFromInput();
            }
            return true;
        }

    private boolean navigationCategoryDelete() {
            if (prepareDraft()) {
                deleteSelectedCategoryWithConfirmation();
            }
            return true;
        }

    private boolean navigationCategoryUp() {
            if (prepareDraft() && moveCategory(-1)) {
                status = "gui.modern.pickup_wb.u028";
            }
            return true;
        }

    private boolean navigationCategoryDown() {
            if (prepareDraft() && moveCategory(1)) {
                status = "gui.modern.pickup_wb.u028";
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
                ModernUiRenderer.SURFACE, searchField.isFocused() ? ModernUiRenderer.ACCENT
                        : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (safe(searchField.getText()).trim().isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(font, "gui.modern.pickup_wb.u010", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, searchField.width);
        }
        drawTextField(searchField);

        int contentTop = searchBounds.bottom() + 6;
        int contentBottom = Math.max(contentTop + 1, navigationActions.contentBottom());
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(navigationBounds.x + 5, contentTop,
                Math.max(1, navigationBounds.width - 10), Math.max(1, contentBottom - contentTop));
        ruleHits.clear();
        groupHits.clear();
        ModernUiRenderer.beginClip(clip);
        int y = clip.y - navigationScroll;
        String query = normalizedSearch();
        for (AutoPickupWorkbenchState.Group group : state.groups()) {
            List<AutoPickupRule> visible = matching(group, query);
            boolean groupMatches = PinyinSearchHelper.matchesNormalized(group.name(), query);
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
                for (AutoPickupRule rule : visible) {
                    ModernMainLayout.Rect row = ModernTreeGuide.row(clip.x,
                            com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar.contentWidth(clip.width), y, RULE_HEIGHT, 1);
                    ModernTreeGuide.drawChild(clip.x, 0, groupRect, row);
                    drawRule(font, row, rule, mouseX, mouseY);
                    if (intersectsVertically(row, clip)) {
                        ruleHits.add(new RuleHit(rule, row));
                    }
                    y = ModernTreeGuide.nextY(y, RULE_HEIGHT);
                }
            }
        }
        if (ruleHits.isEmpty() && groupHits.isEmpty()) {
            ModernUiRenderer.drawText(font, query.isEmpty() ? "gui.modern.pickup_wb.u015" : "gui.modern.pickup_wb.u016",
                    clip.x + 7, y + 5, ModernUiRenderer.MUTED_TEXT, Math.max(1, clip.width - 14));
            y += 22;
        }
        ModernUiRenderer.endClip();
        navigationMaxScroll = Math.max(0, y + navigationScroll - clip.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        navigationScrollbar.draw(clip, navigationScroll, navigationMaxScroll, clip.height, clip.height + navigationMaxScroll, mouseX, mouseY, value -> navigationScroll = value);
        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);

        navigationActions.draw(mouseX, mouseY);
    }



    private void drawFooter(FontRenderer font, int mouseX, int mouseY) {
        int y = Math.max(bounds.y, bounds.bottom() - 25);
        masterBounds = new ModernMainLayout.Rect(bounds.x + 8, y,
                Math.max(1, Math.min(130, navigationBounds.width - 8)), 20);
        drawToggle(font, masterBounds, "gui.modern.pickup_wb.u009", state.masterEnabled(), mouseX, mouseY);
        int saveWidth = Math.max(70, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(
                net.minecraft.client.Minecraft.getMinecraft().fontRenderer, "保存修改", 24));
        saveBounds = new ModernMainLayout.Rect(editorBounds.right() - saveWidth - 8, y, saveWidth, 20);
        revertBounds = new ModernMainLayout.Rect(saveBounds.x - 76, y, 70, 20);
        drawButton(font, revertBounds, "gui.modern.pickup_wb.u025", false, true, mouseX, mouseY);
        drawButton(font, saveBounds, isDirty() ? "gui.modern.pickup_wb.u026" : "gui.modern.pickup_wb.u027", true, true, mouseX, mouseY);
        String message = status.isEmpty()
                ? ModernFormI18n.tr(isDirty() ? "gui.modern.wb.fmt.rules_cats_dirty_pipe"
                        : "gui.modern.wb.fmt.rules_cats_synced_pipe",
                        String.valueOf(state.rules().size()), String.valueOf(state.categories().size()))
                : status;
        ModernUiRenderer.drawText(font, message, editorBounds.x + 8, y + 6,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, revertBounds.x - editorBounds.x - 16));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (sequencePicker.isOpen()) {
            return formBounds == null || formBounds.contains(mouseX, mouseY)
                    ? sequencePicker.mouseClicked(mouseX, mouseY) : true;
        }
        if (overlay != Overlay.NONE) {
            return handleOverlayClick(mouseX, mouseY, button);
        }
        if (navigationActions.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1 && navigationContext(mouseX, mouseY)) return true;
        if (button != 0) {
            return formBounds != null && formBounds.contains(mouseX, mouseY) && editor != null && editor.mouseClicked(mouseX, mouseY, button);
        }
        if (navigationScrollbar.beginDrag(mouseX, mouseY)) return true;
        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }

        if (masterBounds != null && masterBounds.contains(mouseX, mouseY)) {
            clearPendingDelete();
            state.setMasterEnabled(!state.masterEnabled());
            return true;
        }
        if (contains(searchBounds, mouseX, mouseY)) {
            clearPendingDelete();
            categoryField.setFocused(false);
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (contains(categoryNameBounds, mouseX, mouseY)) {
            clearPendingDelete();
            searchField.setFocused(false);
            categoryField.setFocused(true);
            categoryField.mouseClicked(mouseX, mouseY, button);
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
                if (prepareDraft()) {
                    clearPendingDelete();
                    state.selectCategory(hit.name);
                    categoryField.setText(hit.name);
                    if (!collapsedGroups.add(hit.name)) {
                        collapsedGroups.remove(hit.name);
                    }
                }
                return true;
            }
        }
        for (RuleHit hit : ruleHits) {
            if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                if (prepareDraft()) {
                    clearPendingDelete();
                    boolean toggle = ruleToggle.doubleClicked(hit.rule);
                    state.select(hit.rule);
                    if (toggle) {
                        hit.rule.enabled = !hit.rule.enabled;
                    }
                    categoryField.setText(state.selectedCategory());
                    rebuildEditor();
                    status = ModernFormI18n.tr(toggle
                            ? (hit.rule.enabled ? "gui.modern.wb.fmt.enabled" : "gui.modern.wb.fmt.disabled")
                            : "gui.modern.wb.fmt.selected", safe(hit.rule.name));
                }
                return true;
            }
        }
        if (contains(moveUpBounds, mouseX, mouseY)) { return navigationUp(); }
        if (contains(moveDownBounds, mouseX, mouseY)) { return navigationDown(); }
        if (contains(addBounds, mouseX, mouseY)) { return navigationAdd(); }
        if (contains(duplicateBounds, mouseX, mouseY)) { return navigationCopy(); }
        if (contains(deleteBounds, mouseX, mouseY)) { return navigationDelete(); }
        if (contains(revertBounds, mouseX, mouseY)) {
            discardDraft();
            status = "gui.modern.pickup_wb.u034";
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            save();
            return true;
        }

        return formBounds != null && formBounds.contains(mouseX, mouseY)
                && editor != null && editor.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
        if (sequencePicker.isOpen()) {
            return sequencePicker.keyTyped(typedChar, keyCode);
        }
        if (overlay != Overlay.NONE) {
            return handleOverlayKey(typedChar, keyCode);
        }
        if (searchField != null && searchField.isFocused()
                && searchField.textboxKeyTyped(typedChar, keyCode)) {
            navigationScroll = 0;
            return true;
        }
        if (categoryField != null && categoryField.isFocused()
                && categoryField.textboxKeyTyped(typedChar, keyCode)) {
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
        if (sequencePicker.isOpen()) {
            return sequencePicker.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (navigationActions.isOpen()) return true;
        if (navigationScrollbar.isDragging()) { navigationScrollbar.applyDrag(mouseX, mouseY); return true; }
        if (overlay == Overlay.SLOTS && clickedMouseButton == 0 && slotDragging) {
            updateSlotDrag(mouseX, mouseY);
            return true;
        }
        if ((overlay == Overlay.FILTER_LIST || overlay == Overlay.ACTION_LIST)
                && clickedMouseButton == 0 && overlayEntryDragging) {
            updateEntryDrag(mouseX, mouseY);
            return true;
        }
        if (draggingNavigationDivider && clickedMouseButton == 0) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, 150, 270, 110, 160);
            navigationRatio = split.ratio;
            return true;
        }
        if (sequencePicker.isOpen()) {
            return true;
        }
        return editor != null && editor.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (sequencePicker.isOpen()) {
            return sequencePicker.mouseReleased(mouseX, mouseY, mouseButton);
        }
        if (navigationScrollbar.isDragging()) { navigationScrollbar.endDrag(); return true; }
        if (mouseButton == 0 && slotDragging) {
            slotDragging = false;
            slotDragAnchor = -1;
            slotDragCurrent = -1;
            return true;
        }
        if (mouseButton == 0 && overlayEntryDragging) {
            overlayEntryDragging = false;
            overlayDragIndex = -1;
            return true;
        }
        if (mouseButton == 0 && draggingNavigationDivider) {
            MainUiLayoutManager.setModernSplitRatio("rules.auto_pickup.navigation", navigationRatio);
            draggingNavigationDivider = false;
            return true;
        }
        return editor != null && editor.mouseReleased(mouseX, mouseY, mouseButton);
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
        if (sequencePicker.isOpen()) {
            return formBounds == null || formBounds.contains(mouseX, mouseY)
                    ? sequencePicker.handleMouseWheel(wheel, mouseX, mouseY) : true;
        }
        if (overlay != Overlay.NONE) {
            return handleOverlayWheel(wheel, mouseX, mouseY);
        }
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll);
            return true;
        }

        return formBounds != null && formBounds.contains(mouseX, mouseY)
                && editor != null && editor.handleMouseWheel(wheel, mouseX, mouseY);
    }

    @Override
    public boolean handleEscape() {
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (sequencePicker.isOpen()) {
            return sequencePicker.handleEscape();
        }
        if (overlay != Overlay.NONE) {
            closeOverlay();
            return true;
        }
        if (draggingNavigationDivider) {
            draggingNavigationDivider = false;
            return true;
        }
        if (categoryField != null && categoryField.isFocused()) {
            categoryField.setFocused(false);
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
            return true;
        }
        return editor != null && editor.handleEscape();
    }

    @Override
    public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true;
        if (searchField != null && searchField.isFocused() || categoryField != null && categoryField.isFocused()) {
            return true;
        }
        for (GuiTextField field : overlayFields) {
            if (field != null && field.isFocused()) {
                return true;
            }
        }
        return sequencePicker.isTextInputFocused() || editor != null && editor.isTextInputFocused();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return sequencePicker.isOpen() || overlay != Overlay.NONE || editor == null
                ? "" : editor.getHoveredTooltip(mouseX, mouseY);
    }

    @Override
    public boolean isDirty() {
        if (state.isDirty() || editor != null && editor.isDirty()) {
            return true;
        }
        if (overlay == Overlay.SLOTS && editorRule != null) {
            return !new LinkedHashSet<Integer>(copySlots(editorRule.inventoryDetectionSlots)).equals(slotDraft);
        }
        if (overlay == Overlay.FILTER_EDITOR) {
            return overlayFilter != null && (!safe(overlayKeywordField.getText()).equals(safe(overlayFilter.keyword))
                    || !normalizeKeywordList(overlayFilter.requiredNbtTags).equals(overlayNbtTags)
                    || !safe(overlayNbtInputField.getText()).trim().isEmpty());
        }
        if (overlay == Overlay.ACTION_EDITOR) {
            if (overlayAction == null || overlayActionOriginal == null) {
                return false;
            }
            return !safe(overlayKeywordField.getText()).equals(safe(overlayActionOriginal.keyword))
                    || !normalizeKeywordList(overlayActionOriginal.requiredNbtTags).equals(overlayNbtTags)
                    || !safe(overlayDelayField.getText()).trim()
                            .equals(String.valueOf(Math.max(0, overlayActionOriginal.executeDelaySeconds)))
                    || !safe(overlayAction.sequenceName).equals(safe(overlayActionOriginal.sequenceName));
        }
        return false;
    }

    @Override
    public void save() {
        if (sequencePicker.isOpen()) {
            status = "gui.modern.pickup_wb.u035";
            return;
        }
        if (overlay == Overlay.FILTER_EDITOR || overlay == Overlay.ACTION_EDITOR) {
            status = "gui.modern.pickup_wb.u036";
            return;
        }
        if (overlay == Overlay.SLOTS) {
            status = "gui.modern.pickup_wb.u037";
            return;
        }
        clearPendingDelete();
        if (!syncEditorDraft()) {
            if (editorInputError.isEmpty()) {
                status = "gui.modern.pickup_wb.u038";
            }
            return;
        }
        AutoPickupRule invalidRule = null;
        String error = null;
        for (AutoPickupRule rule : state.rules()) {
            error = validateRule(rule);
            if (error != null) {
                invalidRule = rule;
                break;
            }
        }
        if (error != null) {
            state.select(invalidRule);
            categoryField.setText(state.selectedCategory());
            rebuildEditor();
            status = error;
            return;
        }
        if (editor != null) {
            editor.save();
        }
        for (AutoPickupRule rule : state.rules()) {
            AutoPickupWorkbenchState.normalizeRule(rule);
        }
        AutoPickupHandler.globalEnabled = state.masterEnabled();
        AutoPickupHandler.rules.clear();
        AutoPickupHandler.rules.addAll(copyRules(state.rules()));
        AutoPickupHandler.replaceCategoryOrder(new ArrayList<String>(state.categories()));
        AutoPickupHandler.saveConfig();
        state.markCommitted();
        status = "gui.modern.pickup_wb.u039";
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        clearPendingDelete();
        if (sequencePicker.isOpen()) {
            sequencePicker.handleEscape();
        }
        closeOverlay();
        state.discard();
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (categoryField != null) {
            categoryField.setFocused(false);
            categoryField.setText(state.selectedCategory());
        }
        rebuildEditor();
    }

    private void ensureEditor() {
        if (editor == null || editorRule != state.selected()) {
            rebuildEditor();
        }
    }

    private void rebuildEditor() {
        editorRule = state.selected();
        editor = editorRule == null ? emptyEditor() : buildEditor(editorRule);
        editorInputError = "";

    }

    private boolean prepareDraft() {
        if (!syncEditorDraft()) {
            if (editorInputError.isEmpty()) {
                status = "gui.modern.pickup_wb.u038";
            }
            return false;
        }
        return true;
    }

    private boolean syncEditorDraft() {
        editorInputError = "";
        if (editor instanceof ModernFormSettingsTab
                && !((ModernFormSettingsTab<?>) editor).tryApplyDraftValues()) {
            return false;
        }
        if (!editorInputError.isEmpty()) {
            status = editorInputError;
            return false;
        }
        if (editorRule != null) {
            String previousCategory = safe(editorRule.category);
            String category = state.ensureCategoryFor(editorRule);
            state.selectCategory(category);
            if (!previousCategory.equals(category) && editor instanceof ModernFormSettingsTab) {
                ((ModernFormSettingsTab<?>) editor).refreshValues();
            }
            if (categoryField != null && !categoryField.isFocused()) {
                categoryField.setText(category);
            }
        }
        return true;
    }

    private ModernSettingsTab buildEditor(final AutoPickupRule rule) {
        ModernFormSettingsTab.Builder<AutoPickupRule> builder = ModernFormSettingsTab.builder(
                safe(rule.name), "gui.modern.pickup_wb.u041", "gui.modern.pickup_wb.u042",
                adapter(rule)).footerVisible(false)
                .headerToggle(bool(() -> rule.enabled, value -> rule.enabled = value), "gui.modern.pickup_wb.u051")
                .section("gui.modern.pickup_wb.u001", "gui.modern.pickup_wb.u043")
                .text("gui.modern.pickup_wb.u044", "gui.modern.pickup_wb.u045", text(() -> safe(rule.name), value -> rule.name = value),
                        "gui.modern.pickup_wb.u046", 160)
                .choice("gui.modern.pickup_wb.u047", "gui.modern.pickup_wb.u048",
                        new ModernFormSettingsTab.ChoiceValue<String>() {
                            @Override public String get() { return safe(rule.category); }
                            @Override public void set(String value) { rule.category = value; }
                        }, ModernFormSettingsTab.stringOptions(state.categories()))

                .section("范围与拾取", "设置拾取中心、半径和到达参数。")
                .text("gui.modern.pickup_wb.u052", "gui.modern.pickup_wb.u053", text(() -> formatDouble(rule.centerX),
                        value -> rule.centerX = parseDouble(value, rule.centerX, "gui.modern.pickup_wb.u052")), "0", 64)
                .text("gui.modern.pickup_wb.u054", "gui.modern.pickup_wb.u055", text(() -> formatDouble(rule.centerY),
                        value -> rule.centerY = parseDouble(value, rule.centerY, "gui.modern.pickup_wb.u054")), "0", 64)
                .text("gui.modern.pickup_wb.u056", "gui.modern.pickup_wb.u057", text(() -> formatDouble(rule.centerZ),
                        value -> rule.centerZ = parseDouble(value, rule.centerZ, "gui.modern.pickup_wb.u056")), "0", 64)
                .text("gui.modern.pickup_wb.u058", "gui.modern.pickup_wb.u059", text(() -> formatDouble(rule.radius),
                        value -> rule.radius = parseDouble(value, rule.radius, "gui.modern.pickup_wb.u058")), "20", 64)
                .action("可视化取中心与半径", "灵魂出窍：左键选中心，右键确定半径，中键确认，Esc取消。", "开始取点",
                        ModernFormSettingsTab.ActionStyle.PRIMARY, tab -> fillPlayerCoordinates())
                .section("拾取参数", "到达距离与最大拾取尝试次数。")
                .text("gui.modern.pickup_wb.u060", "gui.modern.pickup_wb.u061",
                        text(() -> formatDouble(rule.targetReachDistance),
                                value -> rule.targetReachDistance = parseDouble(value, rule.targetReachDistance, "gui.modern.pickup_wb.u060")), "0.5", 64)
                .integer("gui.modern.pickup_wb.u062", "gui.modern.pickup_wb.u063",
                        integer(() -> rule.maxPickupAttempts, value -> rule.maxPickupAttempts = value), 1,
                        Integer.MAX_VALUE)
                .toggle("gui.modern.pickup_wb.u067", "gui.modern.pickup_wb.u068",
                        bool(() -> rule.visualizeRange, value -> rule.visualizeRange = value))
                .section("gui.modern.pickup_wb.u002", "gui.modern.pickup_wb.u069")
                .readOnly("gui.modern.pickup_wb.u070", "gui.modern.pickup_wb.u071",
                        () -> inventorySummary(rule.inventoryDetectionSlots))
                .action("gui.modern.pickup_wb.u072", "gui.modern.pickup_wb.u073",
                        inventoryButtonLabel(rule), ModernFormSettingsTab.ActionStyle.SECONDARY,
                        tab -> openSlotPicker())
                .section("gui.modern.pickup_wb.u003", "gui.modern.pickup_wb.u074")
                .toggle("gui.modern.pickup_wb.u075", "gui.modern.pickup_wb.u076",
                        bool(() -> rule.enableItemWhitelist, value -> rule.enableItemWhitelist = value))
                .custom(filterCards(true))
                .section("gui.modern.pickup_wb.u004", "gui.modern.pickup_wb.u082")
                .toggle("gui.modern.pickup_wb.u083", "gui.modern.pickup_wb.u084",
                        bool(() -> rule.enableItemBlacklist, value -> rule.enableItemBlacklist = value))
                .custom(filterCards(false))
                .section("gui.modern.pickup_wb.u005", "gui.modern.pickup_wb.u089")
                .custom(new RuleCardsPanel(() -> {
                    List<String> result = new ArrayList<>();
                    for (AutoPickupRule.PickupActionEntry entry : actionEntries()) result.add(describeAction(entry));
                    return result;
                }, () -> { if (prepareDraft()) { inlineEntryEditor = true; openActionEditor(-1); } },
                index -> { if (prepareDraft()) { inlineEntryEditor = true; openActionEditor(index); } },
                index -> actionEntries().remove(index), (from, to) -> actionEntries().add(to, actionEntries().remove((int) from))))
                .action("gui.modern.pickup_wb.u094", "gui.modern.pickup_wb.u095",
                        sequenceLabel(rule.postPickupSequence), ModernFormSettingsTab.ActionStyle.SECONDARY,
                        tab -> openSequencePicker(SequenceTarget.POST_PICKUP))
                .integer("gui.modern.pickup_wb.u096", "gui.modern.pickup_wb.u097",
                        integer(() -> rule.postPickupDelaySeconds, value -> rule.postPickupDelaySeconds = value), 0,
                        Integer.MAX_VALUE)
                .toggle("gui.modern.pickup_wb.u098", "gui.modern.pickup_wb.u099",
                        bool(() -> rule.stopOnExit, value -> rule.stopOnExit = value))
                .section("gui.modern.pickup_wb.u006", "gui.modern.pickup_wb.u100")
                .toggle("gui.modern.pickup_wb.u101", "gui.modern.pickup_wb.u102",
                        bool(() -> rule.antiStuckEnabled, value -> rule.antiStuckEnabled = value))
                .integer("gui.modern.pickup_wb.u103", "gui.modern.pickup_wb.u104",
                        integer(() -> rule.antiStuckTimeoutSeconds, value -> rule.antiStuckTimeoutSeconds = value), 1,
                        Integer.MAX_VALUE)
                .action("gui.modern.pickup_wb.u105", "gui.modern.pickup_wb.u106",
                        sequenceLabel(rule.antiStuckRestartSequence), ModernFormSettingsTab.ActionStyle.SECONDARY,
                        tab -> openSequencePicker(SequenceTarget.ANTI_STUCK));
        return builder.build().sectionPages(editorPositions.computeIfAbsent(rule, key -> new com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState()));
    }

    private RuleCardsPanel filterCards(final boolean whitelist) {
        return new RuleCardsPanel(() -> {
            List<String> result = new ArrayList<>();
            for (AutoPickupRule.ItemMatchEntry entry : entryList(whitelist)) result.add(describeFilter(entry));
            return result;
        }, () -> { if (prepareDraft()) { inlineEntryEditor = true; openFilterEditor(whitelist, -1); } },
        index -> { if (prepareDraft()) { inlineEntryEditor = true; openFilterEditor(whitelist, index); } },
        index -> removeFilterEntry(whitelist, index),
        (from, to) -> entryList(whitelist).add(to, entryList(whitelist).remove((int) from)));
    }

    private ModernFormSettingsTab.StateAdapter<AutoPickupRule> adapter(final AutoPickupRule rule) {
        return new ModernFormSettingsTab.StateAdapter<AutoPickupRule>() {
            @Override
            public void load() {
            }

            @Override
            public AutoPickupRule capture() {
                return AutoPickupWorkbenchState.copyRule(rule);
            }

            @Override
            public AutoPickupRule copy(AutoPickupRule value) {
                return AutoPickupWorkbenchState.copyRule(value);
            }

            @Override
            public void restore(AutoPickupRule value) {
                copyRuleValues(value, rule);
            }

            @Override
            public void save() {
                AutoPickupWorkbenchState.normalizeRule(rule);
            }

            @Override
            public void restoreDefaults() {
                copyRuleValues(new AutoPickupRule(), rule);
            }

            @Override
            public AutoPickupRule createDefaults() {
                return new AutoPickupRule();
            }
        };
    }

    private ModernSettingsTab emptyEditor() {
        return ModernFormSettingsTab.builder("gui.modern.pickup_wb.u107", "gui.modern.pickup_wb.u108",
                "gui.modern.pickup_wb.u109").build();
    }

    private void fillPlayerCoordinates() {
        if (!editorInputError.isEmpty()) {
            status = editorInputError;
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (editorRule == null || minecraft == null || minecraft.player == null) {
            status = "gui.modern.pickup_wb.u110";
            return;
        }
        final AutoPickupRule selected = editorRule;
        AutoFollowAreaPicker.startRadius((center, radius) -> {
            selected.centerX = center.getX() + 0.5;
            selected.centerY = center.getY();
            selected.centerZ = center.getZ() + 0.5;
            selected.radius = radius;
            if (editorRule == selected && editor instanceof ModernFormSettingsTab)
                ((ModernFormSettingsTab<?>) editor).refreshValues();
            status = "中心与半径已回填";
        });
    }

    private void openSequencePicker(SequenceTarget target) {
        if (target != SequenceTarget.ACTION && !editorInputError.isEmpty()) {
            status = editorInputError;
            return;
        }
        sequenceTarget = target;
        sequencePicker.open();
    }

    private void selectSequence(String value) {
        String selected = safe(value).trim();
        if (sequenceTarget == SequenceTarget.ACTION && overlayAction != null) {
            overlayAction.sequenceName = selected;
            overlayValidation = "";
        } else if (editorRule != null && sequenceTarget == SequenceTarget.POST_PICKUP) {
            editorRule.postPickupSequence = selected;
            rebuildEditor();
        } else if (editorRule != null && sequenceTarget == SequenceTarget.ANTI_STUCK) {
            editorRule.antiStuckRestartSequence = selected;
            rebuildEditor();
        }
        sequenceTarget = SequenceTarget.NONE;
    }

    private String sequencePickerTitle() {
        if (sequenceTarget == SequenceTarget.ANTI_STUCK) {
            return "gui.modern.pickup_wb.u112";
        }
        if (sequenceTarget == SequenceTarget.ACTION) {
            return "gui.modern.pickup_wb.u113";
        }
        return "gui.modern.pickup_wb.u114";
    }

    private void addCategoryFromInput() {
        addCategoryValue(categoryField == null ? "" : categoryField.getText());
    }

    private void addCategoryValue(String rawValue) {
        String value = safe(rawValue).trim();
        if (value.isEmpty()) {
            status = "gui.modern.pickup_wb.u115";
            return;
        }
        if (!state.addCategory(value)) {
            status = "gui.modern.pickup_wb.u116";
            return;
        }
        state.selectCategory(value);
        collapsedGroups.remove(state.selectedCategory());
        categoryField.setText(state.selectedCategory());
        navigationScroll = 0;
        // The navigation derives its rows from the draft state. Rebuild the editor so a just-created empty
        // category is visible immediately, even when no rule currently belongs to it.
        rebuildEditor();
        status = "gui.modern.pickup_wb.u117";
    }

    private void renameSelectedCategoryFromInput() {
        String oldValue = state.selectedCategory();
        String newValue = safe(categoryField.getText()).trim();
        if (newValue.isEmpty()) {
            status = "gui.modern.pickup_wb.u115";
            return;
        }
        if (!state.renameCategory(oldValue, newValue)) {
            status = "gui.modern.pickup_wb.u118";
            return;
        }
        categoryField.setText(state.selectedCategory());
        rebuildEditor();
        status = "gui.modern.pickup_wb.u119";
    }

    private void deleteSelectedCategoryWithConfirmation() {
        String category = state.selectedCategory();
        if (isDefaultCategory(category)) {
            pendingDeleteCategory = null;
            status = "gui.modern.pickup_wb.u120";
            return;
        }
        if (pendingDeleteCategory == null || !pendingDeleteCategory.equalsIgnoreCase(category)) {
            pendingDeleteCategory = category;
            pendingDeleteRule = null;
            status = "gui.modern.pickup_wb.u121";
            return;
        }
        if (!state.deleteCategory(category)) {
            status = "gui.modern.pickup_wb.u122";
            pendingDeleteCategory = null;
            return;
        }
        clearPendingDelete();
        categoryField.setText(state.selectedCategory());
        rebuildEditor();
        status = "gui.modern.pickup_wb.u123";
    }

    private boolean moveCategory(int delta) {
        int index = indexOfCategory(state.categories(), state.selectedCategory());
        int target = index + delta;
        if (index < 0 || target < 0 || target >= state.categories().size()) {
            return false;
        }
        return state.moveCategory(state.selectedCategory(), target);
    }

    private boolean canMoveCategory(int delta) {
        int index = indexOfCategory(state.categories(), state.selectedCategory());
        int target = index + delta;
        return index >= 0 && target >= 0 && target < state.categories().size();
    }

    private void clearPendingDelete() {
        pendingDeleteRule = null;
        pendingDeleteCategory = null;
    }

    private List<AutoPickupRule> matching(AutoPickupWorkbenchState.Group group, String query) {
        if (query.isEmpty() || PinyinSearchHelper.matchesNormalized(group.name(), query)) {
            return group.rules();
        }
        List<AutoPickupRule> result = new ArrayList<>();
        for (AutoPickupRule rule : group.rules()) {
            if (PinyinSearchHelper.matchesNormalized(safe(rule.name) + " " + safe(rule.category), query)) {
                result.add(rule);
            }
        }
        return result;
    }

    private String normalizedSearch() {
        return searchField == null ? "" : PinyinSearchHelper.normalizeQuery(safe(searchField.getText()));
    }

    private void drawGroup(FontRenderer font, ModernMainLayout.Rect rect, String name, int count, boolean collapsed,
            int mouseX, int mouseY) {
        boolean selected = state.selectedCategory().equalsIgnoreCase(name);
        boolean hover = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM : hover ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(rect.x + 8, rect.y + 7, collapsed, ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawText(font, name, rect.x + 20, rect.y + 7, ModernUiRenderer.TEXT,
                Math.max(1, rect.width - 54));
        ModernUiRenderer.drawText(font, String.valueOf(count), rect.right() - 23, rect.y + 7,
                ModernUiRenderer.MUTED_TEXT, 18);
    }

    private void drawRule(FontRenderer font, ModernMainLayout.Rect rect, AutoPickupRule rule, int mouseX, int mouseY) {
        boolean selected = rule == state.selected();
        boolean hover = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hover ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(rect.x + 7, rect.y + 7,
                rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        String name = safe(rule.name).trim();
        if (name.isEmpty()) {
            name = "gui.modern.pickup_wb.u124";
        }
        ModernUiRenderer.drawText(font, name, rect.x + 20, rect.y + 4,
                rule.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, rect.width - 28));
        ModernUiRenderer.drawText(font,
                "半径 " + formatDouble(rule.radius) + " | 白 "
                        + ModernFormI18n.tr(rule.enableItemWhitelist ? "gui.modern.pickup_wb.u125" : "gui.modern.pickup_wb.u126")
                        + " | 黑 "
                        + ModernFormI18n.tr(rule.enableItemBlacklist ? "gui.modern.pickup_wb.u125" : "gui.modern.pickup_wb.u126"),
                rect.x + 20, rect.y + 16, ModernUiRenderer.MUTED_TEXT, Math.max(1, rect.width - 28));
    }

    private void drawToggle(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean on, int mouseX,
            int mouseY) {
        boolean hover = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 5, ModernUiRenderer.SURFACE,
                hover ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, label, rect.x + 7, rect.y + 6, ModernUiRenderer.TEXT,
                Math.max(1, rect.width - 47));
        ModernUiRenderer.drawToggle(rect.right() - 37, rect.y + 4, 28, 12, on, hover);
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary,
            boolean enabled, int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        boolean hover = enabled && rect.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = 0xFF151E26;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.MUTED_TEXT;
        } else if (primary) {
            fill = hover ? 0xFFFF86A7 : ModernUiRenderer.ACCENT;
            border = hover ? 0xFFFFB0C4 : ModernUiRenderer.ACCENT;
            text = ModernUiRenderer.SHELL;
        } else {
            fill = hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hover ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, rect.x + 4,
                rect.y + Math.max(3, (rect.height - font.FONT_HEIGHT) / 2), text, Math.max(1, rect.width - 8));
    }

    private static ModernFormSettingsTab.TextValue text(final StringGet getter, final StringSet setter) {
        return new ModernFormSettingsTab.TextValue() {
            @Override
            public String get() {
                return safe(getter.get());
            }

            @Override
            public void set(String value) {
                setter.set(value);
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

    private void ensureOverlayFields(FontRenderer font) {
        if (overlayKeywordField == null) {
            overlayKeywordField = createField(font, 256);
            overlayNbtInputField = createField(font, 256);
            overlayDelayField = createField(font, 10);
            overlayFields.add(overlayKeywordField);
            overlayFields.add(overlayNbtInputField);
            overlayFields.add(overlayDelayField);
        }
    }

    private void openFilterList(boolean whitelist) {
        inlineEntryEditor = false;
        if (!prepareDraft() || editorRule == null) {
            return;
        }
        overlay = inlineEntryEditor ? Overlay.NONE : Overlay.FILTER_LIST;
        overlayWhitelist = whitelist;
        overlaySelectedEntryIndex = entryList(whitelist).isEmpty() ? -1 : 0;
        overlayScroll = 0;
        overlayPendingDeleteIndex = -1;
        overlayValidation = "";
        overlayTitle = whitelist ? "gui.modern.pickup_wb.u077" : "gui.modern.pickup_wb.u085";
    }

    private void openActionList() {
        inlineEntryEditor = false;
        if (!prepareDraft() || editorRule == null) {
            return;
        }
        overlay = inlineEntryEditor ? Overlay.NONE : Overlay.ACTION_LIST;
        overlaySelectedEntryIndex = safeActionEntries(editorRule.pickupActionEntries).isEmpty() ? -1 : 0;
        overlayScroll = 0;
        overlayPendingDeleteIndex = -1;
        overlayValidation = "";
        overlayTitle = "gui.modern.pickup_wb.u127";
    }

    private void openFilterEditor(boolean whitelist, int index) {
        List<AutoPickupRule.ItemMatchEntry> entries = entryList(whitelist);
        overlayWhitelist = whitelist;
        overlayEntryIndex = index;
        overlayFilter = index >= 0 && index < entries.size()
                ? new AutoPickupRule.ItemMatchEntry(entries.get(index)) : new AutoPickupRule.ItemMatchEntry();
        overlayNbtTags.clear();
        if (overlayFilter.requiredNbtTags != null) {
            for (String tag : overlayFilter.requiredNbtTags) {
                addUniqueTag(overlayNbtTags, tag);
            }
        }
        overlay = Overlay.FILTER_EDITOR;
        overlayScroll = 0;
        overlayValidation = "";
        overlayTitle = index >= 0 ? "gui.modern.pickup_wb.u128" : "gui.modern.pickup_wb.u129";
        overlayKeywordField.setText(safe(overlayFilter.keyword));
        overlayNbtInputField.setText("");
        overlayDelayField.setText("0");
        clearOverlayFieldFocus();
    }

    private void openActionEditor(int index) {
        List<AutoPickupRule.PickupActionEntry> entries = safeActionEntries(editorRule == null ? null
                : editorRule.pickupActionEntries);
        overlayEntryIndex = index;
        overlayAction = index >= 0 && index < entries.size()
                ? new AutoPickupRule.PickupActionEntry(entries.get(index))
                : new AutoPickupRule.PickupActionEntry();
        overlayActionOriginal = new AutoPickupRule.PickupActionEntry(overlayAction);
        overlayNbtTags.clear();
        if (overlayAction.requiredNbtTags != null) {
            for (String tag : overlayAction.requiredNbtTags) {
                addUniqueTag(overlayNbtTags, tag);
            }
        }
        overlay = Overlay.ACTION_EDITOR;
        overlayScroll = 0;
        overlayValidation = "";
        overlayTitle = index >= 0 ? "gui.modern.pickup_wb.u130" : "gui.modern.pickup_wb.u131";
        overlayKeywordField.setText(safe(overlayAction.keyword));
        overlayNbtInputField.setText("");
        overlayDelayField.setText(String.valueOf(Math.max(0, overlayAction.executeDelaySeconds)));
        clearOverlayFieldFocus();
    }

    private void openSlotPicker() {
        if (editorRule == null || !editorInputError.isEmpty()) {
            if (!editorInputError.isEmpty()) {
                status = editorInputError;
            }
            return;
        }
        slotDraft.clear();
        slotDraft.addAll(copySlots(editorRule.inventoryDetectionSlots));
        slotDragging = false;
        slotDragAnchor = -1;
        slotDragCurrent = -1;
        overlay = Overlay.SLOTS;
        overlayTitle = "gui.modern.pickup_wb.u132";
        overlayValidation = "";
    }

    private void closeOverlay() {
        overlay = Overlay.NONE;
        overlayEntryIndex = -1;
        overlaySelectedEntryIndex = -1;
        overlayPendingDeleteIndex = -1;
        overlayEntryDragging = false;
        overlayDragIndex = -1;
        overlayFilter = null;
        overlayAction = null;
        overlayActionOriginal = null;
        overlayNbtTags.clear();
        overlayValidation = "";
        overlaySlotGridBounds = null;
        clearOverlayFieldFocus();
        slotDragging = false;
        slotDragAnchor = -1;
        slotDragCurrent = -1;
    }

    private void drawOverlay(FontRenderer font, int mouseX, int mouseY) {
        if (overlay == Overlay.NONE || formBounds == null) {
            return;
        }
        ModernUiRenderer.drawBackdropOverlay(formBounds, 0xB80A1016);
        ModernUiRenderer.beginClip(formBounds);
        if (overlay == Overlay.FILTER_LIST || overlay == Overlay.ACTION_LIST) {
            drawEntryListOverlay(font, mouseX, mouseY);
        } else if (overlay == Overlay.FILTER_EDITOR || overlay == Overlay.ACTION_EDITOR) {
            drawEntryEditorOverlay(font, mouseX, mouseY);
        } else if (overlay == Overlay.SLOTS) {
            drawSlotOverlay(font, mouseX, mouseY);
        }
        ModernUiRenderer.endClip();
    }

    private void drawEntryListOverlay(FontRenderer font, int mouseX, int mouseY) {
        int width = overlayWidth(430);
        int height = overlayHeight(330);
        overlayBounds = centeredOverlay(width, height);
        drawOverlayPanel(font, overlayBounds, overlayTitle,
                overlay == Overlay.ACTION_LIST ? "gui.modern.pickup_wb.u133"
                        : "gui.modern.pickup_wb.u134", mouseX, mouseY);
        List<?> entries = overlay == Overlay.ACTION_LIST ? safeActionEntries(editorRule.pickupActionEntries)
                : entryList(overlayWhitelist);
        int listY = overlayBounds.y + 58;
        int listHeight = Math.max(1, overlayBounds.height - 98);
        overlayListBounds = new ModernMainLayout.Rect(overlayBounds.x + 12, listY,
                Math.max(1, overlayBounds.width - 24), listHeight);
        ModernUiRenderer.drawSubtlePanel(overlayListBounds.x, overlayListBounds.y, overlayListBounds.width,
                overlayListBounds.height, 5, 0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        int visible = Math.max(1, Math.min(ENTRY_VISIBLE_ROWS,
                (overlayListBounds.height - 8) / ENTRY_ROW_HEIGHT));
        overlayMaxScroll = Math.max(0, entries.size() - visible);
        overlayScroll = clamp(overlayScroll, 0, overlayMaxScroll);
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(overlayListBounds.x + 3, overlayListBounds.y + 4,
                Math.max(1, overlayListBounds.width - 6), Math.max(1, overlayListBounds.height - 8));
        ModernUiRenderer.beginClip(clip);
        for (int i = 0; i < visible; i++) {
            int index = overlayScroll + i;
            if (index >= entries.size()) {
                break;
            }
            int rowY = overlayListBounds.y + 4 + i * ENTRY_ROW_HEIGHT;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(overlayListBounds.x + 5, rowY,
                    Math.max(1, overlayListBounds.width - 10), ENTRY_ROW_HEIGHT - 3);
            boolean selected = index == overlaySelectedEntryIndex;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER
                            : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            String description = overlay == Overlay.ACTION_LIST
                    ? describeAction((AutoPickupRule.PickupActionEntry) entries.get(index))
                    : describeFilter((AutoPickupRule.ItemMatchEntry) entries.get(index));
            ModernUiRenderer.drawText(font, (index + 1) + ". " + description, row.x + 7, row.y + 5,
                    ModernUiRenderer.TEXT, Math.max(1, row.width - 14));
        }
        ModernUiRenderer.endClip();
        if (!overlayValidation.isEmpty()) {
            ModernUiRenderer.drawText(font, overlayValidation, overlayBounds.x + 12, overlayBounds.y + 47,
                    ModernUiRenderer.WARNING, Math.max(1, overlayBounds.width - 24));
        }
        if (entries.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.pickup_wb.u135", overlayListBounds.x + 9,
                    overlayListBounds.y + 10, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, overlayListBounds.width - 18));
        }
        if (entries.size() > visible) {
            int thumbHeight = Math.max(16, (int) ((visible / (float) entries.size()) * (listHeight - 8)));
            int track = Math.max(1, listHeight - 8 - thumbHeight);
            int thumbY = listY + 4 + (int) ((overlayScroll / (float) Math.max(1, overlayMaxScroll)) * track);
            ModernRuleEditorUi.drawScrollbar(overlayListBounds.right() - 6, listY + 4, listHeight - 8, thumbY,
                    thumbHeight);
        }
        int actionY = overlayBounds.bottom() - 29;
        int gap = 4;
        int actionWidth = Math.max(1, (overlayBounds.width - 24 - gap * 4) / 5);
        int x = overlayBounds.x + 12;
        overlayAddBounds = new ModernMainLayout.Rect(x, actionY, actionWidth, 20);
        x += actionWidth + gap;
        overlayEditBounds = new ModernMainLayout.Rect(x, actionY, actionWidth, 20);
        x += actionWidth + gap;
        overlayDeleteBounds = new ModernMainLayout.Rect(x, actionY, actionWidth, 20);
        x += actionWidth + gap;
        overlayUpBounds = new ModernMainLayout.Rect(x, actionY, actionWidth, 20);
        x += actionWidth + gap;
        overlayDownBounds = new ModernMainLayout.Rect(x, actionY,
                Math.max(1, overlayBounds.right() - x - 12), 20);
        boolean hasSelection = overlaySelectedEntryIndex >= 0 && overlaySelectedEntryIndex < entries.size();
        drawButton(font, overlayAddBounds, "gui.modern.pickup_wb.u019", true, true, mouseX, mouseY);
        drawButton(font, overlayEditBounds, "gui.modern.pickup_wb.u136", false, hasSelection, mouseX, mouseY);
        drawButton(font, overlayDeleteBounds,
                overlayPendingDeleteIndex == overlaySelectedEntryIndex ? "gui.modern.pickup_wb.u021" : "gui.modern.pickup_wb.u023", false, hasSelection,
                mouseX, mouseY);
        drawButton(font, overlayUpBounds, "gui.modern.pickup_wb.u137", false, hasSelection && overlaySelectedEntryIndex > 0,
                mouseX, mouseY);
        drawButton(font, overlayDownBounds, "gui.modern.pickup_wb.u138", false,
                hasSelection && overlaySelectedEntryIndex < entries.size() - 1, mouseX, mouseY);
        overlayCancelBounds = new ModernMainLayout.Rect(overlayBounds.right() - 74, overlayBounds.y + 31, 62, 20);
        drawButton(font, overlayCancelBounds, "gui.modern.pickup_wb.u139", false, true, mouseX, mouseY);
    }

    private void drawEntryEditorOverlay(FontRenderer font, int mouseX, int mouseY) {
        boolean action = overlay == Overlay.ACTION_EDITOR;
        int width = overlayWidth(action ? 450 : 420);
        int height = overlayHeight(action ? 365 : 330);
        overlayBounds = centeredOverlay(width, height);
        drawOverlayPanel(font, overlayBounds, overlayTitle,
                action ? "gui.modern.pickup_wb.u140"
                        : "gui.modern.pickup_wb.u141", mouseX, mouseY);

        int x = overlayBounds.x + 12;
        int contentWidth = Math.max(1, overlayBounds.width - 24);
        int y = overlayBounds.y + 55;
        drawFieldLabel(font, action ? "gui.modern.pickup_wb.u142" : "gui.modern.pickup_wb.u142", x, y - 12, contentWidth);
        ModernMainLayout.Rect keywordBounds = new ModernMainLayout.Rect(x, y, contentWidth, TEXT_FIELD_HEIGHT);
        drawOverlayFieldSurface(keywordBounds, overlayKeywordField);
        layoutOverlayField(overlayKeywordField, keywordBounds);
        drawTextField(overlayKeywordField);
        y += 38;
        drawFieldLabel(font, "gui.modern.pickup_wb.u143", x, y - 12, contentWidth);
        int addWidth = Math.min(72, Math.max(44, contentWidth / 4));
        overlayNbtAddBounds = new ModernMainLayout.Rect(x + contentWidth - addWidth, y, addWidth, TEXT_FIELD_HEIGHT);
        ModernMainLayout.Rect nbtBounds = new ModernMainLayout.Rect(x, y,
                Math.max(1, contentWidth - addWidth - 5), TEXT_FIELD_HEIGHT);
        drawOverlayFieldSurface(nbtBounds, overlayNbtInputField);
        layoutOverlayField(overlayNbtInputField, nbtBounds);
        drawTextField(overlayNbtInputField);
        drawButton(font, overlayNbtAddBounds, "gui.modern.pickup_wb.u144", false, true, mouseX, mouseY);
        y += 34;
        int tagHeight = action ? 86 : 104;
        overlayNbtListBounds = new ModernMainLayout.Rect(x, y, contentWidth, tagHeight);
        drawTagList(font, overlayNbtListBounds, mouseX, mouseY);
        y += tagHeight + 17;
        if (action) {
            drawFieldLabel(font, "gui.modern.pickup_wb.u145", x, y - 12, contentWidth);
            ModernMainLayout.Rect delayBounds = new ModernMainLayout.Rect(x, y, Math.min(120, contentWidth), TEXT_FIELD_HEIGHT);
            drawOverlayFieldSurface(delayBounds, overlayDelayField);
            layoutOverlayField(overlayDelayField, delayBounds);
            drawTextField(overlayDelayField);
            y += 38;
            drawFieldLabel(font, "gui.modern.pickup_wb.u146", x, y - 12, contentWidth);
            overlaySequenceBounds = new ModernMainLayout.Rect(x, y, contentWidth, 20);
            drawButton(font, overlaySequenceBounds, sequenceLabel(overlayAction == null ? "" : overlayAction.sequenceName),
                    false, true, mouseX, mouseY);
        } else {
            overlaySequenceBounds = null;
        }
        if (!overlayValidation.isEmpty()) {
            ModernUiRenderer.drawText(font, overlayValidation, x, overlayBounds.bottom() - 52,
                    0xFFFF8E8E, Math.max(1, contentWidth));
        }
        int buttonY = overlayBounds.bottom() - 28;
        int half = Math.max(1, (contentWidth - 6) / 2);
        overlaySaveBounds = new ModernMainLayout.Rect(x, buttonY, half, 20);
        overlayCancelBounds = new ModernMainLayout.Rect(x + half + 6, buttonY,
                Math.max(1, contentWidth - half - 6), 20);
        drawButton(font, overlaySaveBounds, "gui.modern.pickup_wb.u147", true, true, mouseX, mouseY);
        drawButton(font, overlayCancelBounds, "gui.modern.pickup_wb.u148", false, true, mouseX, mouseY);
    }

    private void drawSlotOverlay(FontRenderer font, int mouseX, int mouseY) {
        int width = overlayWidth(330);
        int height = overlayHeight(245);
        overlayBounds = centeredOverlay(width, height);
        drawOverlayPanel(font, overlayBounds, overlayTitle,
                "未选择时检查全背包；当前已选 " + slotDraft.size() + " 格。", mouseX, mouseY);
        int cellGap = 2;
        int available = Math.max(1, overlayBounds.width - 28);
        int cellSize = Math.max(4, Math.min(24,
                (available - cellGap * (INVENTORY_SLOT_COLUMNS - 1)) / INVENTORY_SLOT_COLUMNS));
        int gridWidth = INVENTORY_SLOT_COLUMNS * cellSize + cellGap * (INVENTORY_SLOT_COLUMNS - 1);
        int gridHeight = INVENTORY_SLOT_ROWS * cellSize + cellGap * (INVENTORY_SLOT_ROWS - 1);
        int gridX = overlayBounds.x + Math.max(12, (overlayBounds.width - gridWidth) / 2);
        int gridY = overlayBounds.y + 60;
        ModernMainLayout.Rect gridClip = new ModernMainLayout.Rect(overlayBounds.x + 8, gridY,
                Math.max(1, overlayBounds.width - 16), Math.max(1, Math.min(gridHeight, overlayBounds.bottom() - gridY - 45)));
        overlaySlotGridBounds = gridClip;
        ModernUiRenderer.beginClip(gridClip);
        slotHits.clear();
        for (int row = 0; row < INVENTORY_SLOT_ROWS; row++) {
            for (int col = 0; col < INVENTORY_SLOT_COLUMNS; col++) {
                int index = row * INVENTORY_SLOT_COLUMNS + col;
                int cellX = gridX + col * (cellSize + cellGap);
                int cellY = gridY + row * (cellSize + cellGap);
                ModernMainLayout.Rect cell = new ModernMainLayout.Rect(cellX, cellY, cellSize, cellSize);
                slotHits.add(new SlotHit(index, cell));
                boolean selected = slotDraft.contains(index);
                boolean hovered = cell.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(cell.x, cell.y, cell.width, cell.height, 3,
                        selected ? ModernUiRenderer.ACCENT_DIM
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                String label = String.valueOf(index);
                ModernUiRenderer.drawText(font, label,
                        cell.x + (cell.width - font.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label)) / 2,
                        cell.y + Math.max(2, (cell.height - font.FONT_HEIGHT) / 2),
                        ModernUiRenderer.TEXT, Math.max(1, cell.width));
            }
        }
        ModernUiRenderer.endClip();
        int buttonY = overlayBounds.bottom() - 28;
        int gap = 4;
        int buttonWidth = Math.max(1, (overlayBounds.width - 24 - gap * 3) / 4);
        int x = overlayBounds.x + 12;
        overlaySlotsClearBounds = new ModernMainLayout.Rect(x, buttonY, buttonWidth, 20);
        x += buttonWidth + gap;
        overlaySlotsAllBounds = new ModernMainLayout.Rect(x, buttonY, buttonWidth, 20);
        x += buttonWidth + gap;
        overlaySlotsCancelBounds = new ModernMainLayout.Rect(x, buttonY, buttonWidth, 20);
        x += buttonWidth + gap;
        overlaySlotsDoneBounds = new ModernMainLayout.Rect(x, buttonY,
                Math.max(1, overlayBounds.right() - x - 12), 20);
        drawButton(font, overlaySlotsClearBounds, "gui.modern.pickup_wb.u149", false, true, mouseX, mouseY);
        drawButton(font, overlaySlotsAllBounds, "gui.modern.pickup_wb.u150", false, true, mouseX, mouseY);
        drawButton(font, overlaySlotsCancelBounds, "gui.modern.pickup_wb.u148", false, true, mouseX, mouseY);
        drawButton(font, overlaySlotsDoneBounds, "gui.modern.pickup_wb.u151", true, true, mouseX, mouseY);
    }

    private void drawOverlayPanel(FontRenderer font, ModernMainLayout.Rect panel, String title, String subtitle,
            int mouseX, int mouseY) {
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, title, panel.x + 13, panel.y + 10,
                ModernUiRenderer.TEXT, Math.max(1, panel.width - 30));
        ModernUiRenderer.drawText(font, subtitle, panel.x + 13, panel.y + 25,
                ModernUiRenderer.MUTED_TEXT, Math.max(1, panel.width - 26));
        ModernUiRenderer.drawDivider(panel.x + 11, panel.y + 44, Math.max(1, panel.width - 22),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawTagList(FontRenderer font, ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        tagHits.clear();
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        if (overlayNbtTags.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.pickup_wb.u152", rect.x + 7, rect.y + 8,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, rect.width - 14));
            return;
        }
        int visible = Math.max(1, (rect.height - 8) / TAG_ROW_HEIGHT);
        int max = Math.max(0, overlayNbtTags.size() - visible);
        int offset = clamp(overlayScroll, 0, max);
        ModernUiRenderer.beginClip(new ModernMainLayout.Rect(rect.x + 2, rect.y + 2,
                Math.max(1, rect.width - 4), Math.max(1, rect.height - 4)));
        for (int i = 0; i < visible; i++) {
            int index = offset + i;
            if (index >= overlayNbtTags.size()) {
                break;
            }
            int y = rect.y + 4 + i * TAG_ROW_HEIGHT;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(rect.x + 4, y,
                    Math.max(1, rect.width - 20), TAG_ROW_HEIGHT - 2);
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, overlayNbtTags.get(index), row.x + 6, row.y + 5,
                    ModernUiRenderer.TEXT, Math.max(1, row.width - 28));
            ModernUiRenderer.drawCloseIcon(row.right() - 17, row.y + 4,
                    hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
            tagHits.add(new TagHit(index, new ModernMainLayout.Rect(row.right() - 22, row.y, 20, row.height)));
        }
        ModernUiRenderer.endClip();
        if (max > 0) {
            int thumbHeight = Math.max(14, rect.height * visible / Math.max(visible, overlayNbtTags.size()));
            int travel = Math.max(1, rect.height - thumbHeight - 8);
            int thumbY = rect.y + 4 + travel * offset / Math.max(1, max);
            ModernRuleEditorUi.drawScrollbar(rect.right() - 6, rect.y + 4, rect.height - 8, thumbY, thumbHeight);
        }
    }

    private boolean handleOverlayClick(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return true;
        }
        if (overlay == Overlay.FILTER_LIST || overlay == Overlay.ACTION_LIST) {
            return handleEntryListClick(mouseX, mouseY);
        }
        if (overlay == Overlay.FILTER_EDITOR || overlay == Overlay.ACTION_EDITOR) {
            return handleEntryEditorClick(mouseX, mouseY);
        }
        if (overlay == Overlay.SLOTS) {
            return handleSlotClick(mouseX, mouseY);
        }
        return true;
    }

    private boolean handleEntryListClick(int mouseX, int mouseY) {
        List<?> entries = overlay == Overlay.ACTION_LIST ? safeActionEntries(editorRule.pickupActionEntries)
                : entryList(overlayWhitelist);
        if (contains(overlayCancelBounds, mouseX, mouseY)) {
            closeOverlay();
            return true;
        }
        if (contains(overlayListBounds, mouseX, mouseY)) {
            int visible = Math.max(1, Math.min(ENTRY_VISIBLE_ROWS,
                    (overlayListBounds.height - 8) / ENTRY_ROW_HEIGHT));
            int local = mouseY - overlayListBounds.y - 4;
            int row = local < 0 ? -1 : local / ENTRY_ROW_HEIGHT;
            if (row >= 0 && row < visible && local % ENTRY_ROW_HEIGHT < ENTRY_ROW_HEIGHT - 3) {
                int index = overlayScroll + row;
                if (index >= 0 && index < entries.size()) {
                    overlaySelectedEntryIndex = index;
                    overlayPendingDeleteIndex = -1;
                    overlayEntryDragging = true;
                    overlayDragIndex = index;
                }
            }
            return true;
        }
        if (contains(overlayAddBounds, mouseX, mouseY)) {
            if (overlay == Overlay.ACTION_LIST) {
                openActionEditor(-1);
            } else {
                openFilterEditor(overlayWhitelist, -1);
            }
            return true;
        }
        boolean hasSelection = overlaySelectedEntryIndex >= 0 && overlaySelectedEntryIndex < entries.size();
        if (contains(overlayEditBounds, mouseX, mouseY)) {
            if (hasSelection) {
                if (overlay == Overlay.ACTION_LIST) {
                    openActionEditor(overlaySelectedEntryIndex);
                } else {
                    openFilterEditor(overlayWhitelist, overlaySelectedEntryIndex);
                }
            }
            return true;
        }
        if (contains(overlayDeleteBounds, mouseX, mouseY)) {
            if (!hasSelection) {
                return true;
            }
            if (overlayPendingDeleteIndex != overlaySelectedEntryIndex) {
                overlayPendingDeleteIndex = overlaySelectedEntryIndex;
                overlayValidation = "gui.modern.pickup_wb.u153";
                return true;
            }
            if (overlay == Overlay.ACTION_LIST) {
                actionEntries().remove(overlaySelectedEntryIndex);
            } else {
                removeFilterEntry(overlayWhitelist, overlaySelectedEntryIndex);
            }
            overlaySelectedEntryIndex = Math.min(overlaySelectedEntryIndex,
                    (overlay == Overlay.ACTION_LIST ? actionEntries().size() : entryList(overlayWhitelist).size()) - 1);
            overlayPendingDeleteIndex = -1;
            overlayValidation = "gui.modern.pickup_wb.u154";
            return true;
        }
        if (contains(overlayUpBounds, mouseX, mouseY)) {
            if (hasSelection && overlaySelectedEntryIndex > 0) {
                if (overlay == Overlay.ACTION_LIST) {
                    Collections.swap(actionEntries(), overlaySelectedEntryIndex, overlaySelectedEntryIndex - 1);
                } else {
                    Collections.swap(entryList(overlayWhitelist), overlaySelectedEntryIndex,
                            overlaySelectedEntryIndex - 1);
                }
                overlaySelectedEntryIndex--;
                overlayPendingDeleteIndex = -1;
            }
            return true;
        }
        if (contains(overlayDownBounds, mouseX, mouseY)) {
            if (hasSelection && overlaySelectedEntryIndex < entries.size() - 1) {
                if (overlay == Overlay.ACTION_LIST) {
                    Collections.swap(actionEntries(), overlaySelectedEntryIndex, overlaySelectedEntryIndex + 1);
                } else {
                    Collections.swap(entryList(overlayWhitelist), overlaySelectedEntryIndex,
                            overlaySelectedEntryIndex + 1);
                }
                overlaySelectedEntryIndex++;
                overlayPendingDeleteIndex = -1;
            }
            return true;
        }
        return true;
    }

    private boolean handleEntryEditorClick(int mouseX, int mouseY) {
        if (contains(overlayCancelBounds, mouseX, mouseY)) {
            closeOverlay();
            return true;
        }
        if (contains(overlaySaveBounds, mouseX, mouseY)) {
            if (overlay == Overlay.ACTION_EDITOR) {
                saveActionEditor();
            } else {
                saveFilterEditor();
            }
            return true;
        }
        if (contains(overlayNbtAddBounds, mouseX, mouseY)) {
            addOverlayNbtTag();
            return true;
        }
        if (overlay == Overlay.ACTION_EDITOR && contains(overlaySequenceBounds, mouseX, mouseY)) {
            openSequencePicker(SequenceTarget.ACTION);
            return true;
        }
        for (TagHit hit : tagHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                if (hit.index >= 0 && hit.index < overlayNbtTags.size()) {
                    overlayNbtTags.remove(hit.index);
                    overlayScroll = Math.min(overlayScroll,
                            Math.max(0, overlayNbtTags.size() - visibleTagRows()));
                }
                return true;
            }
        }
        GuiTextField clicked = null;
        for (GuiTextField field : overlayFields) {
            if (field != null && field.getVisible() && field.x <= mouseX && mouseX < field.x + field.width
                    && field.y <= mouseY && mouseY < field.y + field.height) {
                clicked = field;
                break;
            }
        }
        clearOverlayFieldFocus();
        if (clicked != null) {
            clicked.setFocused(true);
            clicked.mouseClicked(mouseX, mouseY, 0);
        }
        return true;
    }

    private boolean handleSlotClick(int mouseX, int mouseY) {
        if (contains(overlaySlotsCancelBounds, mouseX, mouseY)) {
            closeOverlay();
            return true;
        }
        if (contains(overlaySlotsClearBounds, mouseX, mouseY)) {
            slotDraft.clear();
            return true;
        }
        if (contains(overlaySlotsAllBounds, mouseX, mouseY)) {
            if (slotDraft.size() == INVENTORY_SLOT_COUNT) {
                slotDraft.clear();
            } else {
                for (int i = 0; i < INVENTORY_SLOT_COUNT; i++) {
                    slotDraft.add(i);
                }
            }
            return true;
        }
        if (contains(overlaySlotsDoneBounds, mouseX, mouseY)) {
            if (editorRule != null) {
                editorRule.inventoryDetectionSlots = new ArrayList<Integer>(slotDraft);
            }
            closeOverlay();
            rebuildEditor();
            status = "gui.modern.pickup_wb.u155";
            return true;
        }
        if (overlaySlotGridBounds != null && overlaySlotGridBounds.contains(mouseX, mouseY)) {
            for (SlotHit hit : slotHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    slotDragging = true;
                    slotDragAnchor = hit.index;
                    slotDragCurrent = hit.index;
                    slotDragAddMode = !slotDraft.contains(hit.index);
                    slotDragSnapshot.clear();
                    slotDragSnapshot.addAll(slotDraft);
                    applySlotDrag();
                    return true;
                }
            }
        }
        return true;
    }

    private boolean handleOverlayKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeOverlay();
            return true;
        }
        if ((overlay == Overlay.FILTER_EDITOR || overlay == Overlay.ACTION_EDITOR)
                && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)
                && overlayNbtInputField != null && overlayNbtInputField.isFocused()) {
            addOverlayNbtTag();
            return true;
        }
        for (GuiTextField field : overlayFields) {
            if (field != null && field.getVisible() && field.isFocused()
                    && field.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return true;
    }

    private boolean handleOverlayWheel(int wheel, int mouseX, int mouseY) {
        if (overlay == Overlay.FILTER_LIST || overlay == Overlay.ACTION_LIST) {
            if (overlayListBounds != null && overlayListBounds.contains(mouseX, mouseY)) {
                overlayScroll = clamp(overlayScroll + (wheel > 0 ? -1 : 1), 0, overlayMaxScroll);
                return true;
            }
            return true;
        }
        if (overlay == Overlay.FILTER_EDITOR || overlay == Overlay.ACTION_EDITOR) {
            if (overlayNbtListBounds != null && overlayNbtListBounds.contains(mouseX, mouseY)) {
                overlayScroll = clamp(overlayScroll + (wheel > 0 ? -1 : 1), 0,
                        Math.max(0, overlayNbtTags.size() - visibleTagRows()));
            }
            return true;
        }
        return true;
    }

    private void saveFilterEditor() {
        String keyword = normalizeToken(overlayKeywordField.getText());
        if (keyword.isEmpty() && overlayNbtTags.isEmpty()) {
            if (overlayEntryIndex >= 0) {
                List<AutoPickupRule.ItemMatchEntry> entries = entryList(overlayWhitelist);
                if (overlayEntryIndex < entries.size()) {
                    removeFilterEntry(overlayWhitelist, overlayEntryIndex);
                    overlaySelectedEntryIndex = Math.min(overlayEntryIndex, entries.size() - 1);
                }
                clearOverlayFieldFocus();
                overlay = inlineEntryEditor ? Overlay.NONE : Overlay.FILTER_LIST;
                overlayValidation = "gui.modern.pickup_wb.u156";
                return;
            }
            overlayValidation = "gui.modern.pickup_wb.u157";
            return;
        }
        AutoPickupRule.ItemMatchEntry value = new AutoPickupRule.ItemMatchEntry();
        value.keyword = keyword;
        value.requiredNbtTags = new ArrayList<String>(overlayNbtTags);
        List<AutoPickupRule.ItemMatchEntry> entries = entryList(overlayWhitelist);
        if (overlayEntryIndex >= 0 && overlayEntryIndex < entries.size()) {
            entries.set(overlayEntryIndex, value);
            overlaySelectedEntryIndex = overlayEntryIndex;
        } else {
            entries.add(value);
            overlaySelectedEntryIndex = entries.size() - 1;
        }
        overlay = inlineEntryEditor ? Overlay.NONE : Overlay.FILTER_LIST;
        overlayPendingDeleteIndex = -1;
        overlayValidation = "gui.modern.pickup_wb.u158";
        clearOverlayFieldFocus();
    }

    private void saveActionEditor() {
        String keyword = normalizeToken(overlayKeywordField.getText());
        int delay;
        try {
            delay = Integer.parseInt(safe(overlayDelayField.getText()).trim());
            if (delay < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ignored) {
            overlayValidation = "gui.modern.pickup_wb.u159";
            return;
        }
        String sequence = safe(overlayAction == null ? "" : overlayAction.sequenceName).trim();
        if (sequence.isEmpty()) {
            overlayValidation = "gui.modern.pickup_wb.u160";
            return;
        }
        if (!PathSequenceManager.hasSequence(sequence)) {
            overlayValidation = "执行序列不存在: " + sequence;
            return;
        }
        AutoPickupRule.PickupActionEntry value = new AutoPickupRule.PickupActionEntry();
        value.keyword = keyword;
        value.requiredNbtTags = new ArrayList<String>(overlayNbtTags);
        value.sequenceName = sequence;
        value.executeDelaySeconds = delay;
        List<AutoPickupRule.PickupActionEntry> entries = actionEntries();
        if (overlayEntryIndex >= 0 && overlayEntryIndex < entries.size()) {
            entries.set(overlayEntryIndex, value);
            overlaySelectedEntryIndex = overlayEntryIndex;
        } else {
            entries.add(value);
            overlaySelectedEntryIndex = entries.size() - 1;
        }
        overlay = inlineEntryEditor ? Overlay.NONE : Overlay.ACTION_LIST;
        overlayPendingDeleteIndex = -1;
        overlayValidation = "gui.modern.pickup_wb.u161";
        clearOverlayFieldFocus();
    }

    private void addOverlayNbtTag() {
        if (overlayNbtInputField == null) {
            return;
        }
        String value = normalizeToken(overlayNbtInputField.getText());
        if (value.isEmpty()) {
            return;
        }
        addUniqueTag(overlayNbtTags, value);
        overlayNbtInputField.setText("");
        overlayScroll = Math.max(0, overlayNbtTags.size() - visibleTagRows());
    }

    private void clearOverlayFieldFocus() {
        for (GuiTextField field : overlayFields) {
            if (field != null) {
                field.setFocused(false);
                field.setVisible(false);
            }
        }
    }

    private void layoutOverlayField(GuiTextField field, ModernMainLayout.Rect rect) {
        if (field == null || rect == null) {
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        field.x = rect.x + 5;
        field.y = rect.y + 3;
        field.width = Math.max(1, rect.width - 10);
        field.height = Math.max(1, rect.height - 6);
    }

    private void drawTextField(GuiTextField field) {
        if (field == null || !field.getVisible() || field.width <= 0 || field.height <= 0) {
            return;
        }
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setEnableBackgroundDrawing(false);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void drawOverlayFieldSurface(ModernMainLayout.Rect bounds, GuiTextField field) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                field != null && field.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawFieldLabel(FontRenderer font, String label, int x, int y, int width) {
        ModernUiRenderer.drawText(font, label, x, y, ModernUiRenderer.SUBTLE_TEXT, Math.max(1, width));
    }

    private int visibleTagRows() {
        return overlayNbtListBounds == null ? 1 : Math.max(1, (overlayNbtListBounds.height - 8) / TAG_ROW_HEIGHT);
    }

    private int overlayWidth(int preferred) {
        if (formBounds == null) {
            return preferred;
        }
        return Math.max(1, Math.min(preferred, Math.max(1, formBounds.width - 8)));
    }

    private int overlayHeight(int preferred) {
        if (formBounds == null) {
            return preferred;
        }
        return Math.max(1, Math.min(preferred, Math.max(1, formBounds.height - 8)));
    }

    private ModernMainLayout.Rect centeredOverlay(int width, int height) {
        return new ModernMainLayout.Rect(formBounds.x + (formBounds.width - width) / 2,
                formBounds.y + (formBounds.height - height) / 2, width, height);
    }

    private void updateSlotDrag(int mouseX, int mouseY) {
        for (SlotHit hit : slotHits) {
            if (!hit.bounds.contains(mouseX, mouseY) || hit.index == slotDragCurrent) {
                continue;
            }
            slotDragCurrent = hit.index;
            applySlotDrag();
            return;
        }
    }

    private void applySlotDrag() {
        if (slotDragAnchor < 0 || slotDragCurrent < 0) {
            return;
        }
        slotDraft.clear();
        slotDraft.addAll(slotDragSnapshot);
        int startRow = Math.min(slotDragAnchor / INVENTORY_SLOT_COLUMNS,
                slotDragCurrent / INVENTORY_SLOT_COLUMNS);
        int endRow = Math.max(slotDragAnchor / INVENTORY_SLOT_COLUMNS,
                slotDragCurrent / INVENTORY_SLOT_COLUMNS);
        int startColumn = Math.min(slotDragAnchor % INVENTORY_SLOT_COLUMNS,
                slotDragCurrent % INVENTORY_SLOT_COLUMNS);
        int endColumn = Math.max(slotDragAnchor % INVENTORY_SLOT_COLUMNS,
                slotDragCurrent % INVENTORY_SLOT_COLUMNS);
        for (int row = startRow; row <= endRow; row++) {
            for (int column = startColumn; column <= endColumn; column++) {
                int index = row * INVENTORY_SLOT_COLUMNS + column;
                if (index < 0 || index >= INVENTORY_SLOT_COUNT) {
                    continue;
                }
                if (slotDragAddMode) {
                    slotDraft.add(index);
                } else {
                    slotDraft.remove(index);
                }
            }
        }
    }

    private void updateEntryDrag(int mouseX, int mouseY) {
        if (!overlayEntryDragging || overlayListBounds == null) {
            return;
        }
        List<?> entries = overlay == Overlay.ACTION_LIST ? safeActionEntries(editorRule.pickupActionEntries)
                : entryList(overlayWhitelist);
        if (entries.size() < 2) {
            return;
        }
        int visible = Math.max(1, Math.min(ENTRY_VISIBLE_ROWS,
                (overlayListBounds.height - 8) / ENTRY_ROW_HEIGHT));
        if (mouseY <= overlayListBounds.y + 6) {
            overlayScroll = Math.max(0, overlayScroll - 1);
        } else if (mouseY >= overlayListBounds.bottom() - 6) {
            overlayScroll = Math.min(overlayMaxScroll, overlayScroll + 1);
        }
        int row = (mouseY - overlayListBounds.y - 4) / ENTRY_ROW_HEIGHT;
        int target = clamp(overlayScroll + row, 0, entries.size() - 1);
        if (target == overlayDragIndex || target < 0) {
            return;
        }
        if (overlay == Overlay.ACTION_LIST) {
            Collections.swap(actionEntries(), overlayDragIndex, target);
        } else {
            Collections.swap(entryList(overlayWhitelist), overlayDragIndex, target);
        }
        overlayDragIndex = target;
        overlaySelectedEntryIndex = target;
        overlayPendingDeleteIndex = -1;
    }

    private void removeFilterEntry(boolean whitelist, int index) {
        List<AutoPickupRule.ItemMatchEntry> entries = entryList(whitelist);
        if (index < 0 || index >= entries.size()) return;
        entries.remove(index);
        List<String> legacy = new ArrayList<>();
        for (AutoPickupRule.ItemMatchEntry entry : entries)
            if (entry != null && entry.keyword != null && !entry.keyword.isEmpty()) legacy.add(entry.keyword);
        if (whitelist) editorRule.itemWhitelist = legacy; else editorRule.itemBlacklist = legacy;
    }

    private List<AutoPickupRule.ItemMatchEntry> entryList(boolean whitelist) {
        if (editorRule == null) {
            return new ArrayList<AutoPickupRule.ItemMatchEntry>();
        }
        if (whitelist) {
            if (editorRule.itemWhitelistEntries == null) {
                editorRule.itemWhitelistEntries = new ArrayList<AutoPickupRule.ItemMatchEntry>();
            }
            if (editorRule.itemWhitelistEntries.isEmpty() && editorRule.itemWhitelist != null) {
                for (String keyword : normalizeKeywordList(editorRule.itemWhitelist)) {
                    AutoPickupRule.ItemMatchEntry entry = new AutoPickupRule.ItemMatchEntry();
                    entry.keyword = keyword;
                    editorRule.itemWhitelistEntries.add(entry);
                }
            }
            return editorRule.itemWhitelistEntries;
        }
        if (editorRule.itemBlacklistEntries == null) {
            editorRule.itemBlacklistEntries = new ArrayList<AutoPickupRule.ItemMatchEntry>();
        }
        if (editorRule.itemBlacklistEntries.isEmpty() && editorRule.itemBlacklist != null) {
            for (String keyword : normalizeKeywordList(editorRule.itemBlacklist)) {
                AutoPickupRule.ItemMatchEntry entry = new AutoPickupRule.ItemMatchEntry();
                entry.keyword = keyword;
                editorRule.itemBlacklistEntries.add(entry);
            }
        }
        return editorRule.itemBlacklistEntries;
    }

    private List<AutoPickupRule.PickupActionEntry> actionEntries() {
        if (editorRule == null) {
            return new ArrayList<AutoPickupRule.PickupActionEntry>();
        }
        if (editorRule.pickupActionEntries == null) {
            editorRule.pickupActionEntries = new ArrayList<AutoPickupRule.PickupActionEntry>();
        }
        return editorRule.pickupActionEntries;
    }

    private List<AutoPickupRule.ItemMatchEntry> safeItemEntries(
            List<AutoPickupRule.ItemMatchEntry> entries, List<String> legacyKeywords) {
        if (entries != null && !entries.isEmpty()) {
            return entries;
        }
        List<AutoPickupRule.ItemMatchEntry> result = new ArrayList<>();
        for (String keyword : normalizeKeywordList(legacyKeywords)) {
            AutoPickupRule.ItemMatchEntry entry = new AutoPickupRule.ItemMatchEntry();
            entry.keyword = keyword;
            result.add(entry);
        }
        return result;
    }

    private List<AutoPickupRule.PickupActionEntry> safeActionEntries(
            List<AutoPickupRule.PickupActionEntry> entries) {
        return entries == null ? new ArrayList<AutoPickupRule.PickupActionEntry>() : entries;
    }

    private String inventorySummary(Iterable<Integer> slots) {
        int count = copySlots(slots).size();
        return count == 0 ? "gui.modern.pickup_wb.u162" : count + " 格";
    }

    private String inventoryButtonLabel(AutoPickupRule rule) {
        int count = copySlots(rule == null ? null : rule.inventoryDetectionSlots).size();
        return count == 0 ? ModernFormI18n.tr("gui.modern.pickup_wb.u163")
                : ModernFormI18n.tr("gui.modern.pickup_wb.u164") + count + " 格）";
    }

    private String entryButtonLabel(String title, List<AutoPickupRule.ItemMatchEntry> entries,
            List<String> legacyKeywords) {
        return title + " (" + safeItemEntries(entries, legacyKeywords).size() + ")";
    }

    private String sequenceLabel(String sequence) {
        String value = safe(sequence).trim();
        return value.isEmpty() ? "gui.modern.pickup_wb.u165" : value;
    }

    private String describeFilter(AutoPickupRule.ItemMatchEntry entry) {
        if (entry == null) {
            return "gui.modern.pickup_wb.u166";
        }
        String keyword = safe(entry.keyword).trim();
        int tags = normalizeKeywordList(entry.requiredNbtTags).size();
        if (keyword.isEmpty() && tags == 0) {
            return "gui.modern.pickup_wb.u166";
        }
        if (keyword.isEmpty()) {
            return "NBT(" + tags + ")";
        }
        return tags == 0 ? keyword : keyword + " | NBT(" + tags + ")";
    }

    private String describeAction(AutoPickupRule.PickupActionEntry entry) {
        if (entry == null) {
            return "gui.modern.pickup_wb.u166";
        }
        String keyword = safe(entry.keyword).trim();
        int tags = normalizeKeywordList(entry.requiredNbtTags).size();
        String condition = keyword.isEmpty() && tags == 0 ? "gui.modern.pickup_wb.u167"
                : keyword.isEmpty() ? "NBT(" + tags + ")"
                        : tags == 0 ? keyword : keyword + " | NBT(" + tags + ")";
        String delay = entry.executeDelaySeconds > 0 ? "（" + entry.executeDelaySeconds + " 秒）" : "";
        return condition + " -> " + sequenceLabel(entry.sequenceName) + delay;
    }

    private List<String> normalizeKeywordList(List<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (source != null) {
            for (String value : source) {
                String normalized = normalizeToken(value);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
        }
        return new ArrayList<String>(result);
    }

    private void addUniqueTag(List<String> target, String value) {
        String normalized = normalizeToken(value);
        if (normalized.isEmpty()) {
            return;
        }
        for (String existing : target) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        target.add(normalized);
    }

    private String normalizeToken(String value) {
        return KillAuraHandler.normalizeFilterName(value);
    }

    private List<Integer> copySlots(Iterable<Integer> source) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (source != null) {
            for (Integer slot : source) {
                if (slot != null && slot >= 0 && slot < INVENTORY_SLOT_COUNT) {
                    result.add(slot);
                }
            }
        }
        return new ArrayList<Integer>(result);
    }

    private AutoPickupRule.ItemMatchEntry normalizeFilterEntry(AutoPickupRule.ItemMatchEntry source) {
        if (source == null) {
            return null;
        }
        AutoPickupRule.ItemMatchEntry result = new AutoPickupRule.ItemMatchEntry();
        result.keyword = normalizeToken(source.keyword);
        result.requiredNbtTags = normalizeKeywordList(source.requiredNbtTags);
        return result.keyword.isEmpty() && result.requiredNbtTags.isEmpty() ? null : result;
    }

    private AutoPickupRule.PickupActionEntry normalizeActionEntry(AutoPickupRule.PickupActionEntry source) {
        if (source == null || safe(source.sequenceName).trim().isEmpty()) {
            return null;
        }
        AutoPickupRule.PickupActionEntry result = new AutoPickupRule.PickupActionEntry();
        result.keyword = normalizeToken(source.keyword);
        result.requiredNbtTags = normalizeKeywordList(source.requiredNbtTags);
        result.sequenceName = source.sequenceName.trim();
        result.executeDelaySeconds = Math.max(0, source.executeDelaySeconds);
        return result;
    }

    private String validateRule(AutoPickupRule rule) {
        if (rule == null) {
            return "gui.modern.pickup_wb.u168";
        }
        if (safe(rule.name).trim().isEmpty()) {
            return "gui.modern.pickup_wb.u169";
        }
        if (!finite(rule.centerX) || !finite(rule.centerY) || !finite(rule.centerZ)
                || !finite(rule.radius) || rule.radius <= 0.0D) {
            return "gui.modern.pickup_wb.u170";
        }
        if (!finite(rule.targetReachDistance) || rule.targetReachDistance <= 0.0D) {
            return "gui.modern.pickup_wb.u171";
        }
        if (rule.maxPickupAttempts < 1) {
            return "gui.modern.pickup_wb.u172";
        }
        if (rule.postPickupDelaySeconds < 0) {
            return "gui.modern.pickup_wb.u173";
        }
        if (rule.antiStuckTimeoutSeconds < 1) {
            return "gui.modern.pickup_wb.u174";
        }
        for (Integer slot : rule.inventoryDetectionSlots == null
                ? Collections.<Integer>emptyList() : rule.inventoryDetectionSlots) {
            if (slot == null || slot < 0 || slot >= INVENTORY_SLOT_COUNT) {
                return "背包检测槽位超出范围: " + slot;
            }
        }
        if (rule.antiStuckEnabled && !safe(rule.antiStuckRestartSequence).trim().isEmpty()
                && !PathSequenceManager.hasSequence(rule.antiStuckRestartSequence.trim())) {
            return "防卡重启序列不存在: " + rule.antiStuckRestartSequence;
        }
        if (rule.pickupActionEntries != null) {
            for (AutoPickupRule.PickupActionEntry entry : rule.pickupActionEntries) {
                if (entry == null) {
                    continue;
                }
                if (safe(entry.sequenceName).trim().isEmpty()) {
                    return "gui.modern.pickup_wb.u175";
                }
                if (!PathSequenceManager.hasSequence(entry.sequenceName.trim())) {
                    return "拾取动作卡片序列不存在: " + entry.sequenceName;
                }
                if (entry.executeDelaySeconds < 0) {
                    return "gui.modern.pickup_wb.u176";
                }
            }
        }
        return null;
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private double parseDouble(String value, double fallback, String label) {
        try {
            double parsed = Double.parseDouble(safe(value).trim().replace(',', '.'));
            if (!finite(parsed)) {
                throw new NumberFormatException();
            }
            if (editorInputError.startsWith(label + "：")) {
                editorInputError = "";
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            if (editorInputError.isEmpty()) {
                editorInputError = label + ModernFormI18n.tr("gui.modern.pickup_wb.u177");
            }
            return fallback;
        }
    }

    private static String formatDouble(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.indexOf('.') >= 0 && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static void copyRuleValues(AutoPickupRule source, AutoPickupRule target) {
        if (target == null) {
            return;
        }
        AutoPickupRule value = AutoPickupWorkbenchState.copyRule(source);
        target.name = value.name;
        target.category = value.category;
        target.enabled = value.enabled;
        target.centerX = value.centerX;
        target.centerY = value.centerY;
        target.centerZ = value.centerZ;
        target.radius = value.radius;
        target.targetReachDistance = value.targetReachDistance;
        target.maxPickupAttempts = value.maxPickupAttempts;
        target.visualizeRange = value.visualizeRange;
        target.enableItemWhitelist = value.enableItemWhitelist;
        target.enableItemBlacklist = value.enableItemBlacklist;
        target.itemWhitelist = new ArrayList<String>(value.itemWhitelist);
        target.itemBlacklist = new ArrayList<String>(value.itemBlacklist);
        target.itemWhitelistEntries = new ArrayList<AutoPickupRule.ItemMatchEntry>();
        for (AutoPickupRule.ItemMatchEntry entry : value.itemWhitelistEntries) {
            target.itemWhitelistEntries.add(new AutoPickupRule.ItemMatchEntry(entry));
        }
        target.itemBlacklistEntries = new ArrayList<AutoPickupRule.ItemMatchEntry>();
        for (AutoPickupRule.ItemMatchEntry entry : value.itemBlacklistEntries) {
            target.itemBlacklistEntries.add(new AutoPickupRule.ItemMatchEntry(entry));
        }
        target.pickupActionEntries = new ArrayList<AutoPickupRule.PickupActionEntry>();
        for (AutoPickupRule.PickupActionEntry entry : value.pickupActionEntries) {
            target.pickupActionEntries.add(new AutoPickupRule.PickupActionEntry(entry));
        }
        target.inventoryDetectionSlots = new ArrayList<Integer>(value.inventoryDetectionSlots);
        target.postPickupSequence = value.postPickupSequence;
        target.postPickupDelaySeconds = value.postPickupDelaySeconds;
        target.stopOnExit = value.stopOnExit;
        target.antiStuckEnabled = value.antiStuckEnabled;
        target.antiStuckTimeoutSeconds = value.antiStuckTimeoutSeconds;
        target.antiStuckRestartSequence = value.antiStuckRestartSequence;
    }

    private static List<AutoPickupRule> copyRules(List<AutoPickupRule> source) {
        List<AutoPickupRule> result = new ArrayList<>();
        if (source != null) {
            for (AutoPickupRule rule : source) {
                if (rule != null) {
                    result.add(AutoPickupWorkbenchState.copyRule(rule));
                }
            }
        }
        return result;
    }

    private static GuiTextField createField(FontRenderer font, int maxLength) {
        GuiTextField field = new GuiTextField(0, font, 0, 0, 1, TEXT_FIELD_HEIGHT);
        field.setEnableBackgroundDrawing(false);
        field.setCanLoseFocus(true);
        field.setMaxStringLength(Math.max(1, maxLength));
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        return field;
    }

    private static int indexOfCategory(List<String> categories, String category) {
        for (int i = 0; i < categories.size(); i++) {
            if (safe(category).equalsIgnoreCase(safe(categories.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isDefaultCategory(String category) {
        return "gui.modern.pickup_wb.u049".equalsIgnoreCase(safe(category).trim());
    }

    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect rect, int amount) {
        if (rect == null) {
            return new ModernMainLayout.Rect(0, 0, 1, 1);
        }
        int safeAmount = Math.max(0, amount);
        return new ModernMainLayout.Rect(rect.x + safeAmount, rect.y + safeAmount,
                Math.max(1, rect.width - safeAmount * 2), Math.max(1, rect.height - safeAmount * 2));
    }

    private static boolean contains(ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        return rect != null && rect.contains(mouseX, mouseY);
    }

    private static boolean intersectsVertically(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.bottom() > second.y && first.y < second.bottom();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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

    private static final class RuleHit {
        private final AutoPickupRule rule;
        private final ModernMainLayout.Rect bounds;

        private RuleHit(AutoPickupRule rule, ModernMainLayout.Rect bounds) {
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



    private static final class TagHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private TagHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class SlotHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private SlotHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }
}
