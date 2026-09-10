package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
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
import com.zszl.zszlScriptMod.handlers.AutoUseItemHandler;
import com.zszl.zszlScriptMod.system.AutoUseItemRule;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;

/** Native list-detail workbench for silent item-use rules. */
public final class ModernAutoUseItemWorkbenchTab implements ModernSettingsTab {
    private final com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar navigationScrollbar = new com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar();
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("autouseitem");

    private static final Gson GSON = new Gson();
    private ModernMainLayout.Rect masterBounds;

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 30;
    private static final int GROUP_HEIGHT = ModernTreeGuide.GROUP_HEIGHT;
    private static final int RULE_HEIGHT = ModernTreeGuide.ITEM_HEIGHT;
    private static final int CATEGORY_TOOLBAR_HEIGHT = 24;
    private static final int RULE_ACTION_RESERVE = 77;

    private final WorkbenchState state = new WorkbenchState(
            AutoUseItemHandler.rules, AutoUseItemHandler.getCategoriesSnapshot(), AutoUseItemHandler.globalEnabled);
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final RuleTreeToggle ruleToggle = new RuleTreeToggle();

    private final java.util.Map<Object, com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState> editorPositions = new java.util.WeakHashMap<>();
    private ModernSettingsTab editor;
    private AutoUseItemRule editorRule;
    private ModernTextField searchField;
    private ModernTextField categoryField;
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
    private ModernMainLayout.Rect addBounds;
    private ModernMainLayout.Rect duplicateBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect moveUpBounds;
    private ModernMainLayout.Rect moveDownBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private int navigationScroll;
    private int navigationMaxScroll;
    private double navigationRatio = 0.28D;
    private boolean layoutPreferencesLoaded;
    private boolean draggingNavigationDivider;
    private AutoUseItemRule pendingDeleteRule;
    private String pendingDeleteCategory;
    private String status = "";

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("rules.auto_use_item.navigation", navigationRatio);
            layoutPreferencesLoaded = true;
        }
        if (searchField == null) {
            searchField = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
            searchField.setEnableBackgroundDrawing(false);
            searchField.setCanLoseFocus(true);
            searchField.setMaxStringLength(120);
        }
        if (categoryField == null) {
            categoryField = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
            categoryField.setEnableBackgroundDrawing(false);
            categoryField.setCanLoseFocus(true);
            categoryField.setMaxStringLength(80);
            categoryField.setText(state.selectedCategory());
        }
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
        ensureEditor();
        editor.updateScreen();
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
        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + TOP_INSET,
                split.firstWidth, Math.max(1, bounds.height - TOP_INSET - FOOTER_HEIGHT - 6));
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 0,
                navigationBounds.y, navigationBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationDividerBounds.right(), navigationBounds.y,
                Math.max(1, bounds.right() - navigationDividerBounds.right() - 8), navigationBounds.height);

        formBounds = editorBounds;

        drawNavigation(fontRenderer, mouseX, mouseY);
        editor.draw(fontRenderer, formBounds, mouseX, mouseY);

        drawFooter(fontRenderer, mouseX, mouseY);
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

        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_add", "", value -> { categoryField.setText(value); navigationCategoryAdd(); }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", state.selectedCategory(), value -> { categoryField.setText(value); navigationCategoryRename(); }));
        navigationActions.action("category_delete", state.selectedCategory().equalsIgnoreCase(pendingDeleteCategory) ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.category_delete", false, true, () -> !isDefaultCategory(state.selectedCategory()), this::navigationCategoryDelete);
        navigationActions.action("category_up", "gui.modern.nav.category_up", false, false, () -> state.canMoveCategory(-1), this::navigationCategoryUp);
        navigationActions.action("category_down", "gui.modern.nav.category_down", false, false, () -> state.canMoveCategory(1), this::navigationCategoryDown);
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> state.selected() != null, this::navigationCopy);
        navigationActions.action("delete", state.selected() != null && pendingDeleteRule == state.selected() ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.delete", true, true, () -> state.selected() != null, this::navigationDelete);
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> state.canMoveSelectedRule(-1), this::navigationUp);
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> state.canMoveSelectedRule(1), this::navigationDown);
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> state.selected() != null, () -> { if (prepareDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.rename", state.selected().name, value -> { state.selected().name = value; rebuildEditor(); }); });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> state.selected() != null, () -> { if (prepareDraft() && state.selected() != null) { state.selected().enabled = !state.selected().enabled; rebuildEditor(); } });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedGroups.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (WorkbenchState.Group group : state.groups()) collapsedGroups.add(group.name()); });
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
            clearPendingDelete();
            if (prepareDraft()) {
                state.addRule(state.selectedCategory());
                categoryField.setText(state.selectedCategory());
                rebuildEditor();
                status = "gui.modern.useitem_wb.u025";
            }
            return true;
        }

    private boolean navigationCopy() {
            clearPendingDelete();
            if (prepareDraft() && state.selected() != null) {
                state.duplicateSelected();
                categoryField.setText(state.selectedCategory());
                rebuildEditor();
                status = "gui.modern.useitem_wb.u026";
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
                status = "gui.modern.useitem_wb.u027";
                return true;
            }
            state.deleteSelected();
            clearPendingDelete();
            rebuildEditor();
            if (categoryField != null) {
                categoryField.setText(state.selectedCategory());
            }
            status = "gui.modern.useitem_wb.u028";
            return true;
        }

    private boolean navigationUp() {
            clearPendingDelete();
            if (prepareDraft() && state.moveSelectedRule(-1)) {
                status = "gui.modern.useitem_wb.u024";
            }
            return true;
        }

    private boolean navigationDown() {
            clearPendingDelete();
            if (prepareDraft() && state.moveSelectedRule(1)) {
                status = "gui.modern.useitem_wb.u024";
            }
            return true;
        }

    private boolean navigationCategoryAdd() {
            clearPendingDelete();
            if (!prepareDraft()) {
                return true;
            }
            addCategoryFromInput();
            return true;
        }

    private boolean navigationCategoryRename() {
            clearPendingDelete();
            if (!prepareDraft()) {
                return true;
            }
            renameSelectedCategoryFromInput();
            return true;
        }

    private boolean navigationCategoryDelete() {
            if (!prepareDraft()) {
                return true;
            }
            deleteSelectedCategoryWithConfirmation();
            return true;
        }

    private boolean navigationCategoryUp() {
            clearPendingDelete();
            if (prepareDraft() && state.moveCategory(-1)) {
                status = "gui.modern.useitem_wb.u023";
            }
            return true;
        }

    private boolean navigationCategoryDown() {
            clearPendingDelete();
            if (prepareDraft() && state.moveCategory(1)) {
                status = "gui.modern.useitem_wb.u023";
            }
            return true;
        }

    private void drawNavigation(FontRenderer font, int mouseX, int mouseY) {
        configureNavigationActions();
        navigationActions.begin(font, navigationBounds);
        ModernUiRenderer.drawSubtlePanel(navigationBounds.x, navigationBounds.y, navigationBounds.width,
                navigationBounds.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);

        searchBounds = new ModernMainLayout.Rect(navigationBounds.x + 8, navigationBounds.y + 30,
                Math.max(30, navigationBounds.width - 16), 20);
        searchField.x = searchBounds.x + 22;
        searchField.y = searchBounds.y + 5;
        searchField.width = Math.max(1, searchBounds.width - 28);
        searchField.height = 12;
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, searchField.isFocused() ? ModernUiRenderer.ACCENT
                        : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(font, "gui.modern.useitem_wb.u008", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, searchField.width);
        }
        searchField.setTextColor(ModernUiRenderer.TEXT);
        searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);

        int contentTop = searchBounds.bottom() + 6;
        int contentBottom = Math.max(contentTop + 1, navigationActions.contentBottom());
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(navigationBounds.x + 5, contentTop,
                Math.max(1, navigationBounds.width - 10), Math.max(1, contentBottom - contentTop));
        ruleHits.clear();
        groupHits.clear();
        ModernUiRenderer.beginClip(clip);
        int y = clip.y - navigationScroll;
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        for (WorkbenchState.Group group : state.groups()) {
            List<AutoUseItemRule> visible = matching(group, query);
            if (!query.isEmpty() && visible.isEmpty()
                    && !group.name().toLowerCase(Locale.ROOT).contains(query)) {
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
                for (AutoUseItemRule rule : visible) {
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
        ModernUiRenderer.endClip();
        navigationMaxScroll = Math.max(0, y + navigationScroll - clip.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        navigationScrollbar.draw(clip, navigationScroll, navigationMaxScroll, clip.height, clip.height + navigationMaxScroll, mouseX, mouseY, value -> navigationScroll = value);

        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);
        navigationActions.draw(mouseX, mouseY);
    }



    private void drawFooter(FontRenderer font, int mouseX, int mouseY) {
        int y = bounds.bottom() - 25;
        masterBounds = new ModernMainLayout.Rect(bounds.x + 8, y,
                Math.max(1, Math.min(130, navigationBounds.width - 8)), 20);
        drawToggle(font, masterBounds, "gui.modern.useitem_wb.u007", state.masterEnabled(), mouseX, mouseY);
        int saveWidth = Math.max(70, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(
                net.minecraft.client.Minecraft.getMinecraft().fontRenderer, "保存修改", 24));
        saveBounds = new ModernMainLayout.Rect(editorBounds.right() - saveWidth - 8, y, saveWidth, 20);
        revertBounds = new ModernMainLayout.Rect(saveBounds.x - 76, y, 70, 20);
        drawButton(font, revertBounds, "gui.modern.useitem_wb.u020", false, true, mouseX, mouseY);
        drawButton(font, saveBounds, isDirty() ? "gui.modern.useitem_wb.u021" : "gui.modern.useitem_wb.u022", true, true, mouseX, mouseY);
        String message = status.isEmpty()
                ? ModernFormI18n.tr(isDirty() ? "gui.modern.wb.fmt.rules_cats_dirty_pipe"
                        : "gui.modern.wb.fmt.rules_cats_synced_pipe",
                        String.valueOf(state.rules().size()), String.valueOf(state.categories().size()))
                : status;
        ModernUiRenderer.drawText(font, message, editorBounds.x + 8, y + 6,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, revertBounds.x - editorBounds.x - 16));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
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
        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            clearPendingDelete();
            categoryField.setFocused(false);
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (categoryNameBounds != null && categoryNameBounds.contains(mouseX, mouseY)) {
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
                if (!prepareDraft()) {
                    return true;
                }
                clearPendingDelete();
                state.selectCategory(hit.name);
                categoryField.setText(hit.name);
                if (!collapsedGroups.add(hit.name)) {
                    collapsedGroups.remove(hit.name);
                }
                return true;
            }
        }
        for (RuleHit hit : ruleHits) {
            if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                if (!prepareDraft()) {
                    return true;
                }
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
            status = "gui.modern.useitem_wb.u029";
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            save();
            return true;
        }

        return formBounds != null && formBounds.contains(mouseX, mouseY)
                ? editor.mouseClicked(mouseX, mouseY, button)
                : true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
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
        if (draggingNavigationDivider && clickedMouseButton == 0) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, 150, 260, 120, 180);
            navigationRatio = split.ratio;
            return true;
        }
        return editor != null && editor.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (navigationScrollbar.isDragging()) { navigationScrollbar.endDrag(); return true; }
        if (mouseButton == 0 && draggingNavigationDivider) {
            MainUiLayoutManager.setModernSplitRatio("rules.auto_use_item.navigation", navigationRatio);
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
        if (editor != null && editor.handleEscape()) {
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
        if (pendingDeleteRule != null || pendingDeleteCategory != null) {
            clearPendingDelete();
            status = "gui.modern.useitem_wb.u030";
            return true;
        }
        return false;
    }

    @Override
    public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true;
        return searchField != null && searchField.isFocused()
                || categoryField != null && categoryField.isFocused()
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
        return state.isDirty() || editor != null && editor.isDirty();
    }

    @Override
    public void save() {
        clearPendingDelete();
        if (!syncEditorDraft()) {
            status = "gui.modern.useitem_wb.u031";
            return;
        }
        AutoUseItemRule invalid = null;
        String error = null;
        for (AutoUseItemRule rule : state.rules()) {
            normalizeRuleForSave(rule);
            error = validateRule(rule);
            if (error != null) {
                invalid = rule;
                break;
            }
        }
        if (error != null) {
            state.select(invalid);
            if (categoryField != null) {
                categoryField.setText(state.selectedCategory());
            }
            rebuildEditor();
            status = error;
            return;
        }

        if (editor != null) {
            editor.save();
        }
        AutoUseItemHandler.globalEnabled = state.masterEnabled();
        AutoUseItemHandler.rules.clear();
        AutoUseItemHandler.rules.addAll(copyRules(state.rules()));
        AutoUseItemHandler.replaceCategoryOrder(state.categories());
        AutoUseItemHandler.saveConfig();
        AutoUseItemHandler.INSTANCE.resetSchedule();
        state.markCommitted();
        status = "gui.modern.useitem_wb.u032";
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        clearPendingDelete();
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
        editor = editorRule == null ? emptyEditor() : buildEditor(editorRule);

    }

    private boolean prepareDraft() {
        if (!syncEditorDraft()) {
            status = "gui.modern.useitem_wb.u031";
            return false;
        }
        return true;
    }

    private boolean syncEditorDraft() {
        String editorCategoryBefore = editorRule == null ? "" : normalizeCategory(editorRule.category);
        if (editor instanceof ModernFormSettingsTab
                && !((ModernFormSettingsTab<?>) editor).tryApplyDraftValues()) {
            return false;
        }
        if (editorRule != null) {
            String category = state.ensureCategoryFor(editorRule);
            if (!editorCategoryBefore.equalsIgnoreCase(category)) {
                state.selectCategory(category);
                if (categoryField != null && !categoryField.isFocused()) {
                    categoryField.setText(category);
                }
            }
        }
        return true;
    }

    private ModernSettingsTab buildEditor(final AutoUseItemRule rule) {
        ModernFormSettingsTab.Builder<AutoUseItemRule> builder = ModernFormSettingsTab.builder(
                safe(rule.name), "gui.modern.useitem_wb.u034", "gui.modern.useitem_wb.u035", adapter(rule))
                .footerVisible(false)
                .headerToggle(bool(() -> rule.enabled, value -> rule.enabled = value), "gui.modern.useitem_wb.u049")
                .section("gui.modern.useitem_wb.u001", "gui.modern.useitem_wb.u036")
                .text("gui.modern.useitem_wb.u037", "gui.modern.useitem_wb.u038",
                        text(() -> safe(rule.name), value -> rule.name = value), "gui.modern.useitem_wb.u039", 256)
                .choice("gui.modern.useitem_wb.u040", "gui.modern.useitem_wb.u041",
                        new ModernFormSettingsTab.ChoiceValue<String>() {
                            @Override public String get() { return safe(rule.category); }
                            @Override public void set(String value) { rule.category = value; }
                        }, ModernFormSettingsTab.stringOptions(state.categories()))
                .choice("gui.modern.useitem_wb.u043", "gui.modern.useitem_wb.u044",
                        new ModernFormSettingsTab.ChoiceValue<AutoUseItemRule.MatchMode>() {
                            @Override
                            public AutoUseItemRule.MatchMode get() {
                                return safeMatchMode(rule.matchMode);
                            }

                            @Override
                            public void set(AutoUseItemRule.MatchMode value) {
                                rule.matchMode = safeMatchMode(value);
                            }
                        }, ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(AutoUseItemRule.MatchMode.CONTAINS, "gui.modern.useitem_wb.u045"),
                                ModernFormSettingsTab.option(AutoUseItemRule.MatchMode.EXACT, "gui.modern.useitem_wb.u046")))
                .section("gui.modern.useitem_wb.u002", "gui.modern.useitem_wb.u047")

                .readOnly("gui.modern.useitem_wb.u050", "gui.modern.useitem_wb.u051",
                        () -> conditionSummary(rule))
                .integer("gui.modern.useitem_wb.u052", "gui.modern.useitem_wb.u053",
                        integer(() -> Math.max(10, rule.intervalMs), value -> rule.intervalMs = value), 10,
                        Integer.MAX_VALUE)
                .section("gui.modern.useitem_wb.u003", "gui.modern.useitem_wb.u054")
                .choice("gui.modern.useitem_wb.u055", "gui.modern.useitem_wb.u056",
                        new ModernFormSettingsTab.ChoiceValue<AutoUseItemRule.UseMode>() {
                            @Override
                            public AutoUseItemRule.UseMode get() {
                                return safeUseMode(rule.useMode);
                            }

                            @Override
                            public void set(AutoUseItemRule.UseMode value) {
                                rule.useMode = safeUseMode(value);
                            }
                        }, ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(AutoUseItemRule.UseMode.RIGHT_CLICK, "gui.modern.useitem_wb.u057"),
                                ModernFormSettingsTab.option(AutoUseItemRule.UseMode.LEFT_CLICK, "gui.modern.useitem_wb.u058")))
                .toggle("gui.modern.useitem_wb.u059", "gui.modern.useitem_wb.u060",
                        bool(() -> rule.changeLocalSlot, value -> rule.changeLocalSlot = value))
                .section("gui.modern.useitem_wb.u004", "gui.modern.useitem_wb.u061")
                .integer("gui.modern.useitem_wb.u062", "gui.modern.useitem_wb.u063",
                        integer(() -> Math.max(0, rule.switchItemDelayTicks), value -> rule.switchItemDelayTicks = value), 0,
                        Integer.MAX_VALUE)
                .integer("gui.modern.useitem_wb.u064", "gui.modern.useitem_wb.u065",
                        integer(() -> Math.max(0, rule.switchDelayTicks), value -> rule.switchDelayTicks = value), 0,
                        Integer.MAX_VALUE)
                .integer("gui.modern.useitem_wb.u066", "gui.modern.useitem_wb.u067",
                        integer(() -> Math.max(0, rule.restoreDelayTicks), value -> rule.restoreDelayTicks = value), 0,
                        Integer.MAX_VALUE);
        return builder.build().sectionPages(editorPositions.computeIfAbsent(rule, key -> new com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState()));
    }

    private ModernFormSettingsTab.StateAdapter<AutoUseItemRule> adapter(final AutoUseItemRule rule) {
        return new ModernFormSettingsTab.StateAdapter<AutoUseItemRule>() {
            @Override
            public void load() {
            }

            @Override
            public AutoUseItemRule capture() {
                return copyRule(rule);
            }

            @Override
            public AutoUseItemRule copy(AutoUseItemRule value) {
                return copyRule(value);
            }

            @Override
            public void restore(AutoUseItemRule value) {
                copyRuleValues(value, rule);
            }

            @Override
            public void save() {
                normalizeRuleForSave(rule);
            }

            @Override
            public void restoreDefaults() {
                copyRuleValues(new AutoUseItemRule(), rule);
            }

            @Override
            public AutoUseItemRule createDefaults() {
                return new AutoUseItemRule();
            }
        };
    }

    private ModernSettingsTab emptyEditor() {
        return ModernFormSettingsTab.builder("gui.modern.useitem_wb.u068", "gui.modern.useitem_wb.u069", "").build();
    }

    private void addCategoryFromInput() {
        String value = safe(categoryField.getText()).trim();
        if (value.isEmpty()) {
            status = "gui.modern.useitem_wb.u070";
            return;
        }
        String added = state.addCategory(value);
        if (added == null) {
            status = "gui.modern.useitem_wb.u071";
            return;
        }
        categoryField.setText(added);
        status = "gui.modern.useitem_wb.u072";
    }

    private void renameSelectedCategoryFromInput() {
        String oldCategory = state.selectedCategory();
        String value = safe(categoryField.getText()).trim();
        if (value.isEmpty()) {
            status = "gui.modern.useitem_wb.u070";
            return;
        }
        if (!state.renameCategory(oldCategory, value)) {
            status = "gui.modern.useitem_wb.u073";
            return;
        }
        categoryField.setText(state.selectedCategory());
        rebuildEditor();
        status = "gui.modern.useitem_wb.u074";
    }

    private void deleteSelectedCategoryWithConfirmation() {
        String category = state.selectedCategory();
        if (isDefaultCategory(category)) {
            clearPendingDelete();
            status = "gui.modern.useitem_wb.u075";
            return;
        }
        if (pendingDeleteCategory == null || !pendingDeleteCategory.equalsIgnoreCase(category)) {
            pendingDeleteCategory = category;
            pendingDeleteRule = null;
            status = "gui.modern.useitem_wb.u076";
            return;
        }
        if (!state.deleteCategory(category)) {
            status = "gui.modern.useitem_wb.u077";
            clearPendingDelete();
            return;
        }
        clearPendingDelete();
        categoryField.setText(state.selectedCategory());
        rebuildEditor();
        status = "gui.modern.useitem_wb.u078";
    }

    private void clearPendingDelete() {
        pendingDeleteRule = null;
        pendingDeleteCategory = null;
    }

    private List<AutoUseItemRule> matching(WorkbenchState.Group group, String query) {
        if (query.isEmpty() || group.name().toLowerCase(Locale.ROOT).contains(query)) {
            return group.rules();
        }
        List<AutoUseItemRule> result = new ArrayList<>();
        for (AutoUseItemRule rule : group.rules()) {
            if (safe(rule.name).toLowerCase(Locale.ROOT).contains(query)) {
                result.add(rule);
            }
        }
        return result;
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

    private void drawRule(FontRenderer font, ModernMainLayout.Rect rect, AutoUseItemRule rule, int mouseX, int mouseY) {
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
            name = ModernFormI18n.tr("gui.modern.useitem_wb.u079");
        }
        ModernUiRenderer.drawText(font, name, rect.x + 20, rect.y + 4,
                rule.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, rect.width - 28));
        ModernUiRenderer.drawText(font,
                ModernFormI18n.tr(matchModeText(rule.matchMode)) + " | "
                        + ModernFormI18n.tr(useModeText(rule.useMode)) + " | "
                        + Math.max(10, rule.intervalMs) + " ms",
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

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary, boolean enabled,
            int mouseX, int mouseY) {
        drawButton(font, rect, label, primary, enabled, false, mouseX, mouseY);
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary, boolean enabled,
            boolean danger, int mouseX, int mouseY) {
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
        } else if (danger) {
            fill = hover ? 0xFFF29A78 : 0xFFD96A52;
            border = hover ? 0xFFFFC0A7 : 0xFFD96A52;
            text = ModernUiRenderer.SHELL;
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
        int labelWidth = font.getStringWidth(label == null ? "" : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, rect.x + Math.max(4, (rect.width - labelWidth) / 2),
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

    private static void normalizeRuleForSave(AutoUseItemRule rule) {
        if (rule == null) {
            return;
        }
        rule.name = safe(rule.name).trim();
        rule.category = normalizeCategory(rule.category);
        rule.useMode = safeUseMode(rule.useMode);
        rule.matchMode = safeMatchMode(rule.matchMode);
        rule.intervalMs = Math.max(10, rule.intervalMs);
        rule.switchItemDelayTicks = Math.max(0, rule.switchItemDelayTicks);
        rule.switchDelayTicks = Math.max(0, rule.switchDelayTicks);
        rule.restoreDelayTicks = Math.max(0, rule.restoreDelayTicks);
        rule.lastUseAtMs = 0L;
    }

    private static String validateRule(AutoUseItemRule rule) {
        if (rule == null) {
            return "gui.modern.useitem_wb.u080";
        }
        if (safe(rule.name).trim().isEmpty()) {
            return "gui.modern.useitem_wb.u081";
        }
        if (rule.intervalMs < 10) {
            return "gui.modern.useitem_wb.u082";
        }
        if (rule.switchItemDelayTicks < 0 || rule.switchDelayTicks < 0 || rule.restoreDelayTicks < 0) {
            return "gui.modern.useitem_wb.u083";
        }
        if (rule.useMode == null || rule.matchMode == null) {
            return "gui.modern.useitem_wb.u084";
        }
        return null;
    }

    private static String conditionSummary(AutoUseItemRule rule) {
        String enabled = ModernFormI18n.tr(rule != null && rule.enabled
                ? "gui.modern.useitem_wb.u085" : "gui.modern.useitem_wb.u086");
        String match = rule == null || safe(rule.name).trim().isEmpty()
                ? ModernFormI18n.tr("gui.modern.useitem_wb.u087")
                : ModernFormI18n.tr("gui.modern.useitem_wb.u088")
                        + ModernFormI18n.tr(matchModeText(rule.matchMode))
                        + ModernFormI18n.tr("gui.modern.useitem_wb.u089");
        return ModernFormI18n.tr("gui.modern.useitem_wb.fmt.condition", enabled, match);
    }

    private static AutoUseItemRule.UseMode safeUseMode(AutoUseItemRule.UseMode mode) {
        return mode == null ? AutoUseItemRule.UseMode.RIGHT_CLICK : mode;
    }

    private static AutoUseItemRule.MatchMode safeMatchMode(AutoUseItemRule.MatchMode mode) {
        return mode == null ? AutoUseItemRule.MatchMode.CONTAINS : mode;
    }

    private static String useModeText(AutoUseItemRule.UseMode mode) {
        return safeUseMode(mode) == AutoUseItemRule.UseMode.LEFT_CLICK ? "gui.modern.useitem_wb.u058" : "gui.modern.useitem_wb.u057";
    }

    private static String matchModeText(AutoUseItemRule.MatchMode mode) {
        return safeMatchMode(mode) == AutoUseItemRule.MatchMode.EXACT ? "gui.modern.useitem_wb.u046" : "gui.modern.useitem_wb.u045";
    }

    private static String normalizeCategory(String value) {
        String normalized = safe(value).trim();
        // Older drafts wrote the translation key itself.  Treat it as the
        // canonical default so it cannot become a second visible "默认" group.
        return normalized.isEmpty() || "gui.modern.useitem_wb.u042".equalsIgnoreCase(normalized)
                ? "默认" : normalized;
    }

    private static boolean isDefaultCategory(String value) {
        return "默认".equalsIgnoreCase(normalizeCategory(value));
    }

    private static List<AutoUseItemRule> copyRules(List<AutoUseItemRule> source) {
        List<AutoUseItemRule> result = new ArrayList<>();
        if (source != null) {
            for (AutoUseItemRule rule : source) {
                if (rule != null) {
                    result.add(copyRule(rule));
                }
            }
        }
        return result;
    }

    private static AutoUseItemRule copyRule(AutoUseItemRule source) {
        AutoUseItemRule target = new AutoUseItemRule();
        copyRuleValues(source, target);
        return target;
    }

    private static void copyRuleValues(AutoUseItemRule source, AutoUseItemRule target) {
        if (target == null) {
            return;
        }
        if (source == null) {
            source = new AutoUseItemRule();
        }
        target.name = source.name;
        target.category = source.category;
        target.enabled = source.enabled;
        target.changeLocalSlot = source.changeLocalSlot;
        target.useMode = source.useMode;
        target.matchMode = source.matchMode;
        target.intervalMs = source.intervalMs;
        target.switchItemDelayTicks = source.switchItemDelayTicks;
        target.switchDelayTicks = source.switchDelayTicks;
        target.restoreDelayTicks = source.restoreDelayTicks;
        target.lastUseAtMs = 0L;
    }

    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect rect, int amount) {
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
        private final AutoUseItemRule rule;
        private final ModernMainLayout.Rect bounds;

        private RuleHit(AutoUseItemRule rule, ModernMainLayout.Rect bounds) {
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



    private static final class WorkbenchState {
        private List<AutoUseItemRule> originalRules;
        private final List<AutoUseItemRule> rules = new ArrayList<>();
        private List<String> originalCategories;
        private final List<String> categories = new ArrayList<>();
        private AutoUseItemRule selected;
        private String selectedCategory = "默认";
        private boolean masterEnabled;
        private boolean originalMasterEnabled;

        private WorkbenchState(List<AutoUseItemRule> source, List<String> sourceCategories, boolean masterEnabled) {
            this.masterEnabled = masterEnabled;
            this.originalMasterEnabled = masterEnabled;
            if (sourceCategories != null) {
                for (String category : sourceCategories) {
                    addCategory(category);
                }
            }
            rules.addAll(copyRules(source));
            for (AutoUseItemRule rule : rules) {
                ensureCategoryFor(rule);
            }
            if (categories.isEmpty()) {
                categories.add("默认");
            }
            if (!rules.isEmpty()) {
                selected = rules.get(0);
                selectedCategory = normalizeCategory(selected.category);
            } else {
                selectedCategory = categories.get(0);
            }
            originalRules = copyRules(rules);
            originalCategories = new ArrayList<>(categories);
        }

        private List<AutoUseItemRule> rules() {
            return Collections.unmodifiableList(rules);
        }

        private List<String> categories() {
            return Collections.unmodifiableList(categories);
        }

        private AutoUseItemRule selected() {
            return selected;
        }

        private String selectedCategory() {
            return normalizeCategory(selectedCategory);
        }

        private boolean masterEnabled() {
            return masterEnabled;
        }

        private void setMasterEnabled(boolean value) {
            masterEnabled = value;
        }

        private List<Group> groups() {
            Map<String, List<AutoUseItemRule>> grouped = new LinkedHashMap<>();
            for (String category : categories) {
                grouped.put(category, new ArrayList<AutoUseItemRule>());
            }
            for (AutoUseItemRule rule : rules) {
                String category = normalizeCategory(rule.category);
                List<AutoUseItemRule> group = grouped.get(category);
                if (group == null) {
                    group = new ArrayList<>();
                    grouped.put(category, group);
                    categories.add(category);
                }
                group.add(rule);
            }
            List<Group> result = new ArrayList<>();
            for (Map.Entry<String, List<AutoUseItemRule>> entry : grouped.entrySet()) {
                result.add(new Group(entry.getKey(), entry.getValue()));
            }
            return result;
        }

        private void select(AutoUseItemRule rule) {
            if (rules.contains(rule)) {
                selected = rule;
                selectCategory(rule.category);
            }
        }

        private void selectCategory(String category) {
            String normalized = normalizeCategory(category);
            for (String existing : categories) {
                if (existing.equalsIgnoreCase(normalized)) {
                    selectedCategory = existing;
                    return;
                }
            }
            categories.add(normalized);
            selectedCategory = normalized;
        }

        private String ensureCategoryFor(AutoUseItemRule rule) {
            if (rule == null) {
                return selectedCategory();
            }
            String normalized = normalizeCategory(rule.category);
            String actual = normalized;
            for (String existing : categories) {
                if (existing.equalsIgnoreCase(normalized)) {
                    actual = existing;
                    break;
                }
            }
            if (!categories.contains(actual)) {
                categories.add(actual);
            }
            rule.category = actual;
            return actual;
        }

        private String addCategory(String value) {
            String normalized = normalizeCategory(value);
            for (String existing : categories) {
                if (existing.equalsIgnoreCase(normalized)) {
                    return null;
                }
            }
            categories.add(normalized);
            selectedCategory = normalized;
            return normalized;
        }

        private boolean renameCategory(String oldValue, String newValue) {
            String oldCategory = normalizeCategory(oldValue);
            String newCategory = normalizeCategory(newValue);
            int index = indexOfCategory(oldCategory);
            if (index < 0) {
                return false;
            }
            if (oldCategory.equalsIgnoreCase(newCategory)) {
                selectedCategory = categories.get(index);
                return true;
            }
            for (String existing : categories) {
                if (existing.equalsIgnoreCase(newCategory)) {
                    return false;
                }
            }
            categories.set(index, newCategory);
            for (AutoUseItemRule rule : rules) {
                if (normalizeCategory(rule.category).equalsIgnoreCase(oldCategory)) {
                    rule.category = newCategory;
                }
            }
            selectedCategory = newCategory;
            return true;
        }

        private boolean deleteCategory(String value) {
            String target = normalizeCategory(value);
            if (isDefaultCategory(target)) {
                return false;
            }
            int index = indexOfCategory(target);
            if (index < 0) {
                return false;
            }
            categories.remove(index);
            for (AutoUseItemRule rule : rules) {
                if (normalizeCategory(rule.category).equalsIgnoreCase(target)) {
                    rule.category = "默认";
                }
            }
            ensureCategoryName("默认");
            selectedCategory = "默认";
            return true;
        }

        private boolean moveCategory(int delta) {
            int index = indexOfCategory(selectedCategory());
            int target = index + delta;
            if (index < 0 || target < 0 || target >= categories.size()) {
                return false;
            }
            Collections.swap(categories, index, target);
            selectedCategory = categories.get(target);
            return true;
        }

        private boolean canMoveCategory(int delta) {
            int index = indexOfCategory(selectedCategory());
            int target = index + delta;
            return index >= 0 && target >= 0 && target < categories.size();
        }

        private void addRule(String category) {
            AutoUseItemRule rule = new AutoUseItemRule();
            rule.category = normalizeCategory(category);
            ensureCategoryName(rule.category);
            rules.add(rule);
            selected = rule;
            selectedCategory = rule.category;
        }

        private void duplicateSelected() {
            if (selected == null) {
                return;
            }
            AutoUseItemRule copy = copyRule(selected);
            String baseName = safe(selected.name).trim();
            copy.name = baseName.isEmpty() ? ModernFormI18n.tr("gui.modern.useitem_wb.u090")
                    : ModernFormI18n.tr("gui.modern.wb.fmt.copy", baseName);
            copy.lastUseAtMs = 0L;
            int index = rules.indexOf(selected);
            rules.add(Math.min(rules.size(), index + 1), copy);
            selected = copy;
            selectedCategory = normalizeCategory(copy.category);
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

        private boolean moveSelectedRule(int delta) {
            if (selected == null) {
                return false;
            }
            String category = normalizeCategory(selected.category);
            int index = rules.indexOf(selected);
            int target = -1;
            for (int i = index + (delta < 0 ? -1 : 1); i >= 0 && i < rules.size(); i += delta < 0 ? -1 : 1) {
                if (normalizeCategory(rules.get(i).category).equalsIgnoreCase(category)) {
                    target = i;
                    break;
                }
            }
            if (target < 0) {
                return false;
            }
            Collections.swap(rules, index, target);
            return true;
        }

        private boolean canMoveSelectedRule(int delta) {
            if (selected == null) {
                return false;
            }
            String category = normalizeCategory(selected.category);
            int index = rules.indexOf(selected);
            for (int i = index + (delta < 0 ? -1 : 1); i >= 0 && i < rules.size(); i += delta < 0 ? -1 : 1) {
                if (normalizeCategory(rules.get(i).category).equalsIgnoreCase(category)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isDirty() {
            return masterEnabled != originalMasterEnabled
                    || !GSON.toJson(originalCategories).equals(GSON.toJson(categories))
                    || !GSON.toJson(originalRules).equals(GSON.toJson(rules));
        }

        private void markCommitted() {
            originalRules = copyRules(rules);
            originalCategories = new ArrayList<>(categories);
            originalMasterEnabled = masterEnabled;
        }

        private void discard() {
            String selectedName = selected == null ? "" : safe(selected.name);
            String selectedCat = selected == null ? selectedCategory() : normalizeCategory(selected.category);
            rules.clear();
            rules.addAll(copyRules(originalRules));
            categories.clear();
            categories.addAll(originalCategories);
            masterEnabled = originalMasterEnabled;
            selected = null;
            for (AutoUseItemRule rule : rules) {
                if (safe(rule.name).equals(selectedName)
                        && normalizeCategory(rule.category).equalsIgnoreCase(selectedCat)) {
                    selected = rule;
                    break;
                }
            }
            if (selected == null && !rules.isEmpty()) {
                selected = rules.get(0);
            }
            selectedCategory = selected == null ? (categories.isEmpty() ? "默认" : categories.get(0))
                    : normalizeCategory(selected.category);
            if (categories.isEmpty()) {
                categories.add("默认");
            }
        }

        private int indexOfCategory(String value) {
            String target = normalizeCategory(value);
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(target)) {
                    return i;
                }
            }
            return -1;
        }

        private void ensureCategoryName(String value) {
            String normalized = normalizeCategory(value);
            for (String category : categories) {
                if (category.equalsIgnoreCase(normalized)) {
                    return;
                }
            }
            categories.add(normalized);
        }

        private static final class Group {
            private final String name;
            private final List<AutoUseItemRule> rules;

            private Group(String name, List<AutoUseItemRule> rules) {
                this.name = name;
                this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            }

            private String name() {
                return name;
            }

            private List<AutoUseItemRule> rules() {
                return rules;
            }
        }
    }
}
