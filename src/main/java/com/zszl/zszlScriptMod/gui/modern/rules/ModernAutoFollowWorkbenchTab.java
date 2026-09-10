package com.zszl.zszlScriptMod.gui.modern.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.system.AutoFollowRule;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.Entity;

/** Native list-detail editor for automatic follow rules. */
public final class ModernAutoFollowWorkbenchTab implements ModernSettingsTab {
    /** Stored category names are data, never translation keys. */
    private static final String DEFAULT_CATEGORY = "默认";
    private static final String LEGACY_DEFAULT_CATEGORY_KEY = "gui.modern.autofollow.u046";
    private String navigationCategory;
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("autofollow");

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 34;
    private static final int GROUP_HEIGHT = ModernTreeGuide.GROUP_HEIGHT;
    private static final int RULE_HEIGHT = ModernTreeGuide.ITEM_HEIGHT;
    private static final ModernUiRenderer.Icon[] SECTION_ICONS = {
            ModernUiRenderer.Icon.SETTINGS, ModernUiRenderer.Icon.ROUTE,
            ModernUiRenderer.Icon.SEARCH, ModernUiRenderer.Icon.FOLLOW,
            ModernUiRenderer.Icon.ESCAPE, ModernUiRenderer.Icon.RUNNING_STATUS
    };
    private static final int NAVIGATION_MIN_WIDTH = 150;
    private static final int EDITOR_MIN_WIDTH = 260;

    private static final String[] EDITOR_SECTIONS = {
            "gui.modern.autofollow.u001", "gui.modern.autofollow.u002", "gui.modern.autofollow.u003", "gui.modern.autofollow.u004", "gui.modern.autofollow.u005", "gui.modern.autofollow.u006"
    };

    private static final String[][] ENTITY_TYPES = {
            { AutoFollowRule.ENTITY_TYPE_MONSTER, "gui.modern.autofollow.u007" },
            { AutoFollowRule.ENTITY_TYPE_BOSS, "gui.modern.autofollow.u008" },
            { AutoFollowRule.ENTITY_TYPE_GOLEM, "gui.modern.autofollow.u009" },
            { AutoFollowRule.ENTITY_TYPE_NEUTRAL, "gui.modern.autofollow.u010" },
            { AutoFollowRule.ENTITY_TYPE_ANIMAL, "gui.modern.autofollow.u011" },
            { AutoFollowRule.ENTITY_TYPE_WATER, "gui.modern.autofollow.u012" },
            { AutoFollowRule.ENTITY_TYPE_AMBIENT, "gui.modern.autofollow.u013" },
            { AutoFollowRule.ENTITY_TYPE_VILLAGER, "gui.modern.autofollow.u014" },
            { AutoFollowRule.ENTITY_TYPE_TAMEABLE, "gui.modern.autofollow.u015" },
            { AutoFollowRule.ENTITY_TYPE_PLAYER, "gui.modern.autofollow.u016" },
            { AutoFollowRule.ENTITY_TYPE_LIVING, "gui.modern.autofollow.u017" },
            { AutoFollowRule.ENTITY_TYPE_ANY, "gui.modern.autofollow.u018" }
    };

    private final WorkbenchState state = new WorkbenchState(
            new ArrayList<>(AutoFollowHandler.rules), AutoFollowHandler.getCategoriesSnapshot(),
            AutoFollowHandler.antiStuckEnabled, AutoFollowHandler.avoidVinesProactively,
            AutoFollowHandler.vineAvoidanceDistance, AutoFollowHandler.timeoutReloadEnabled,
            AutoFollowHandler.timeoutReloadSeconds);

    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final RuleTreeToggle ruleToggle = new RuleTreeToggle();
    private final com.zszl.zszlScriptMod.gui.modern.RuleSectionNavigation sectionNavigation = new com.zszl.zszlScriptMod.gui.modern.RuleSectionNavigation(EDITOR_SECTIONS, SECTION_ICONS);

    private ModernSettingsTab editor;
    private final Map<Integer, ModernSettingsTab> sectionEditors = new LinkedHashMap<>();
    private AutoFollowRule editorRule;
    private EditorDraft editorDraft;
    private String originalArea = "";
    private GuiTextField searchField;
    private AutoEscapeSequencePicker sequencePicker;

    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect navigationBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect formBounds;
    private ModernMainLayout.Rect navigationDividerBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect addBounds;
    private ModernMainLayout.Rect duplicateBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;

    private int navigationScroll;
    private int navigationMaxScroll;
    private double navigationRatio = 0.28D;
    private boolean layoutPreferencesLoaded;
    private int selectedSection;
    /** Section used to build the currently displayed form editor. */
    private int editorSection = -1;
    private int selectedReturnPointIndex = -1;
    private final Map<AutoFollowRule, Map<Integer, com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState>> editorPositions = new java.util.WeakHashMap<>();
    private boolean draggingNavigationDivider;
    private AutoFollowRule pendingDelete;
    private String status = "";

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("rules.auto_follow.navigation", navigationRatio);
            layoutPreferencesLoaded = true;
        }
        if (searchField == null) {
            searchField = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
            searchField.setEnableBackgroundDrawing(false);
            searchField.setMaxStringLength(120);
        }
        if (sequencePicker == null) {
            sequencePicker = new AutoEscapeSequencePicker(this::selectSequence);
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
        if (sequencePicker != null) {
            sequencePicker.updateScreen();
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
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, navigationRatio,
                NAVIGATION_MIN_WIDTH, EDITOR_MIN_WIDTH, 120, 180);
        navigationRatio = split.ratio;

        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + TOP_INSET,
                split.firstWidth, Math.max(1, bounds.height - TOP_INSET - FOOTER_HEIGHT - 6));
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width,
                0, navigationBounds.y, navigationBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationDividerBounds.right(), navigationBounds.y,
                Math.max(1, bounds.right() - navigationDividerBounds.right() - 8), navigationBounds.height);

        formBounds = sectionNavigation.layout(fontRenderer, editorBounds);

        drawNavigation(fontRenderer, mouseX, mouseY);
        editor.draw(fontRenderer, formBounds, mouseX, mouseY);
        sectionNavigation.draw(fontRenderer, selectedSection, mouseX, mouseY);
        if (sequencePicker != null) {
            sequencePicker.draw(fontRenderer, formBounds, "gui.modern.autofollow.u019", mouseX, mouseY);
        }
        drawFooter(fontRenderer, mouseX, mouseY);
        navigationActions.drawOverlay(mouseX, mouseY);
    }


    private void configureNavigationActions() {
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_add", "", value -> { if (syncEditorDraft()) { state.addCategory(value); navigationCategory = value; } }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", selectedCategory(), value -> { if (syncEditorDraft() && state.renameCategory(selectedCategory(), value)) { navigationCategory = value; rebuildEditor(); } }));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !"默认".equals(selectedCategory()), () -> navigationActions.confirm("gui.modern.nav.category_delete", () -> { if (syncEditorDraft()) { state.deleteCategory(selectedCategory()); navigationCategory = "默认"; rebuildEditor(); } }));
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> state.selected() != null, () -> {
            if (syncEditorDraft() && state.selected() != null) {
                final AutoFollowRule rule = state.selected();
                navigationActions.choose("移动到分类", state.categories(), rule.category, value -> {
                    rule.category = value;
                    navigationCategory = value;
                    collapsedGroups.remove(value);
                    rebuildEditor();
                });
            }
        });
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> state.canMoveRule(-1), () -> { if (syncEditorDraft()) state.moveRule(-1); });
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> state.canMoveRule(1), () -> { if (syncEditorDraft()) state.moveRule(1); });

        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> state.selected() != null, this::navigationCopy);
        navigationActions.action("delete", state.selected() != null && pendingDelete == state.selected() ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.delete", true, true, () -> state.selected() != null, this::navigationDelete);
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.rename", state.selected().name, value -> { state.selected().name = value; rebuildEditor(); }); });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) { state.selected().enabled = !state.selected().enabled; rebuildEditor(); } });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedGroups.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (WorkbenchState.Group group : state.groups()) collapsedGroups.add(group.name()); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationContext(int mouseX, int mouseY) {
        if (!navigationActions.inTree(mouseX, mouseY)) return false;
        if (!syncEditorDraft()) return true;
        searchField.setFocused(false);
        for (RuleHit hit : ruleHits) if (hit.bounds.contains(mouseX, mouseY)) {
            navigationCategory = null;
            if (state.selected() != hit.rule) { state.select(hit.rule); rebuildEditor(); }

            navigationActions.context(mouseX, mouseY, "add", "copy", "rename", "move", "toggle", "up", "down", "delete");
            return true;
        }
        for (GroupHit hit : groupHits) if (hit.bounds.contains(mouseX, mouseY)) {
            navigationCategory = hit.name;

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
                pendingDelete = null;
                state.addRule(selectedCategory());
                rebuildEditor();
                status = "gui.modern.autofollow.u031";
            }
            return true;
        }

    private boolean navigationCopy() {
            if (syncEditorDraft()) {
                pendingDelete = null;
                state.duplicateSelected();
                rebuildEditor();
                status = "gui.modern.autofollow.u032";
            }
            return true;
        }

    private boolean navigationDelete() {
            if (!syncEditorDraft()) {
                return true;
            }
            if (state.selected() == null) {
                status = "gui.modern.autofollow.u033";
                return true;
            }
            if (pendingDelete != state.selected()) {
                pendingDelete = state.selected();
                status = "gui.modern.autofollow.u034";
                return true;
            }
            pendingDelete = null;
            state.deleteSelected();
            rebuildEditor();
            status = "gui.modern.autofollow.u035";
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
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(font, "gui.modern.autofollow.u022", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, searchField.width);
        }
        ModernUiRenderer.drawTextField(searchField);

        ruleHits.clear();
        groupHits.clear();
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(navigationBounds.x + 5, searchBounds.bottom() + 6,
                Math.max(1, navigationBounds.width - 10), Math.max(1, navigationActions.contentBottom() - searchBounds.bottom() - 6));
        ModernUiRenderer.beginClip(clip);
        int y = clip.y - navigationScroll;
        String query = PinyinSearchHelper.normalizeQuery(searchField.getText());
        for (WorkbenchState.Group group : state.groups()) {
            List<AutoFollowRule> visible = matching(group, query);
            if (!query.isEmpty() && visible.isEmpty()
                    && !PinyinSearchHelper.matchesNormalized(group.name(), query)) {
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
                for (AutoFollowRule rule : visible) {
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

        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);
        navigationMaxScroll = Math.max(0, y + navigationScroll - clip.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        if (navigationMaxScroll > 0) {
            int thumbHeight = Math.max(16, clip.height * clip.height
                    / Math.max(1, clip.height + navigationMaxScroll));
            int thumbY = clip.y + (clip.height - thumbHeight) * navigationScroll
                    / Math.max(1, navigationMaxScroll);
            ModernRuleEditorUi.drawScrollbar(clip.right() - 2, clip.y, clip.height, thumbY, thumbHeight);
        }

        navigationActions.draw(mouseX, mouseY);
    }

    private void drawFooter(FontRenderer font, int mouseX, int mouseY) {
        int y = bounds.bottom() - 25;
        ModernUiRenderer.drawStatusDot(bounds.x + 14, y + 10,
                state.hasEnabledRule() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawText(font, state.activeSummary(), bounds.x + 24, y + 6,
                state.hasEnabledRule() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, navigationBounds.width - 24));
        int saveWidth = Math.max(70, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(
                net.minecraft.client.Minecraft.getMinecraft().fontRenderer, "保存修改", 24));
        saveBounds = new ModernMainLayout.Rect(editorBounds.right() - saveWidth - 8, y, saveWidth, 20);
        revertBounds = new ModernMainLayout.Rect(saveBounds.x - 76, y, 70, 20);
        drawButton(font, revertBounds, "gui.modern.autofollow.u028", false, mouseX, mouseY);
        drawButton(font, saveBounds, isDirty() ? "gui.modern.autofollow.u029" : "gui.modern.autofollow.u030", true, mouseX, mouseY);

        String message = status.isEmpty()
                ? t(isDirty() ? "gui.modern.autofollow.fmt.rules_dirty" : "gui.modern.autofollow.fmt.rules_synced",
                        String.valueOf(state.rules().size()))
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
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.mouseClicked(mouseX, mouseY);
        }
        if (navigationActions.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1 && navigationContext(mouseX, mouseY)) return true;
        if (button != 0) {
            return formBounds != null && formBounds.contains(mouseX, mouseY)
                    ? editor.mouseClicked(mouseX, mouseY, button) : true;
        }

        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }

        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            pendingDelete = null;
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        searchField.setFocused(false);

        for (GroupHit hit : groupHits) {
            if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                navigationCategory = hit.name;
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
                pendingDelete = null;
                navigationCategory = null;
                boolean toggle = ruleToggle.doubleClicked(hit.rule);
                state.select(hit.rule);
                if (toggle) {
                    hit.rule.enabled = !hit.rule.enabled;
                }
                rebuildEditor();
                status = toggle
                        ? t(hit.rule.enabled ? "gui.modern.wb.fmt.enabled" : "gui.modern.wb.fmt.disabled",
                                safe(hit.rule.name))
                        : t("gui.modern.autofollow.fmt.selected", safe(hit.rule.name));
                return true;
            }
        }

        if (contains(addBounds, mouseX, mouseY)) { return navigationAdd(); }
        if (contains(duplicateBounds, mouseX, mouseY)) { return navigationCopy(); }
        if (contains(deleteBounds, mouseX, mouseY)) { return navigationDelete(); }
        if (contains(saveBounds, mouseX, mouseY)) {
            save();
            return true;
        }
        if (contains(revertBounds, mouseX, mouseY)) {
            discardDraft();
            status = "gui.modern.autofollow.u036";
            return true;
        }

        if (sectionNavigation.contains(mouseX, mouseY)) {
            int index = sectionNavigation.click(mouseX, mouseY);
            if (index >= 0 && index != selectedSection && editor instanceof ModernFormSettingsTab
                    && ((ModernFormSettingsTab<?>) editor).tryApplyDraftValues()) {
                selectedSection = index;
                editor = sectionEditors.get(selectedSection);
                if (editor == null) {
                    editor = editorRule == null ? emptyEditor() : buildEditor(editorRule, editorDraft);
                    sectionEditors.put(selectedSection, editor);
                }
                editorSection = selectedSection;
                editor.ensureInitialized(Minecraft.getMinecraft().fontRenderer);
            }
            return true;
        }
        return formBounds != null && formBounds.contains(mouseX, mouseY)
                ? editor.mouseClicked(mouseX, mouseY, button) : true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.keyTyped(typedChar, keyCode);
        }
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            navigationScroll = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_F
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchField.setFocused(true);
            return true;
        }
        return editor != null && editor.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (clickedMouseButton == 0 && sectionNavigation.drag(mouseX, mouseY)) return true;
        if (draggingNavigationDivider && clickedMouseButton == 0) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, NAVIGATION_MIN_WIDTH, EDITOR_MIN_WIDTH, 120, 180);
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
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.mouseReleased(mouseX, mouseY, state);
        }
        sectionNavigation.release();
        if (state == 0 && draggingNavigationDivider) {
            MainUiLayoutManager.setModernSplitRatio("rules.auto_follow.navigation", navigationRatio);
            draggingNavigationDivider = false;
            return true;
        }
        return editor != null && editor.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return editor != null && editor.handleMouseWheel(wheel);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (navigationActions.wheel(wheel)) return true;
        if (sequencePicker != null && sequencePicker.isOpen()) return sequencePicker.handleMouseWheel(wheel, mouseX, mouseY);
        if (sectionNavigation.wheel(wheel, mouseX, mouseY)) return true;
        if (wheel == 0) {
            return false;
        }
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll);
            return true;
        }
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.handleMouseWheel(wheel, mouseX, mouseY);
        }
        if (formBounds != null && formBounds.contains(mouseX, mouseY)) {
            return editor != null && editor.handleMouseWheel(wheel);
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (sequencePicker != null && sequencePicker.isOpen()) {
            return sequencePicker.handleEscape();
        }
        if (draggingNavigationDivider) {
            draggingNavigationDivider = false;
            return true;
        }
        return editor != null && editor.handleEscape();
    }

    @Override
    public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true;
        return searchField != null && searchField.isFocused()
                || sequencePicker != null && sequencePicker.isTextInputFocused()
                || editor != null && editor.isTextInputFocused();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        if (navigationActions.isOpen() || sequencePicker != null && sequencePicker.isOpen()) return "";
        String navTooltip = sectionNavigation.tooltip(mouseX, mouseY);
        if (!navTooltip.isEmpty()) return navTooltip;
        return editor == null ? "" : editor.getHoveredTooltip(mouseX, mouseY);
    }

    @Override
    public boolean isDirty() {
        if (state.isDirty() || !originalArea.equals(areaDraftKey())) return true;
        for (ModernSettingsTab page : sectionEditors.values()) if (page.isDirty()) return true;
        return false;
    }

    @Override
    public void save() {
        if (!syncEditorDraft()) {
            return;
        }
        String error = validateState();
        if (!error.isEmpty()) {
            status = error;
            return;
        }

        for (ModernSettingsTab page : sectionEditors.values()) page.save();
        state.normalizeRules();

        AutoFollowRule previousActive = AutoFollowHandler.getActiveRule();
        String previousActiveName = previousActive == null ? "" : safe(previousActive.name).trim();
        AutoFollowRule desiredActive = state.chooseActive(previousActiveName);
        state.keepOnlyEnabled(desiredActive);

        AutoFollowHandler.antiStuckEnabled = state.antiStuckEnabled();
        AutoFollowHandler.avoidVinesProactively = state.avoidVinesProactively();
        AutoFollowHandler.vineAvoidanceDistance = state.vineAvoidanceDistance();
        AutoFollowHandler.timeoutReloadEnabled = state.timeoutReloadEnabled();
        AutoFollowHandler.timeoutReloadSeconds = state.timeoutReloadSeconds();

        AutoFollowHandler.rules.clear();
        AutoFollowHandler.rules.addAll(copyRules(state.rules()));
        for (String category : AutoFollowHandler.getCategoriesSnapshot()) {
            if (!state.categories().contains(category)) AutoFollowHandler.deleteCategory(category);
        }
        AutoFollowHandler.replaceCategoryOrder(state.categories());

        AutoFollowRule handlerActive = findByName(AutoFollowHandler.rules,
                desiredActive == null ? "" : desiredActive.name);
        if (handlerActive == null) {
            AutoFollowHandler.setActiveRule(null);
        } else {
            AutoFollowHandler.setActiveRule(handlerActive);
        }
        AutoFollowHandler.saveFollowConfig();

        state.markCommitted();
        originalArea = areaDraftKey();
        status = "gui.modern.autofollow.u037";
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        navigationCategory = null;
        if (sequencePicker != null && sequencePicker.isOpen()) {
            sequencePicker.handleEscape();
        }
        pendingDelete = null;
        state.discard();
        rebuildEditor();
        if (searchField != null) {
            searchField.setFocused(false);
        }
    }

    private String areaDraftKey() {
        return editorDraft == null ? "" : editorDraft.point1X + "," + editorDraft.point1Z
                + ";" + editorDraft.point2X + "," + editorDraft.point2Z;
    }

    private void ensureEditor() {
        if (editor == null || editorRule != state.selected() || editorSection != selectedSection) {
            rebuildEditor();
        }
    }

    private void rebuildEditor() {
        sectionEditors.clear();
        editorRule = state.selected();
        selectedReturnPointIndex = normalizeReturnPointSelection(editorRule, selectedReturnPointIndex);
        editorDraft = editorRule == null ? new EditorDraft(new AutoFollowRule(), state)
                : new EditorDraft(editorRule, state);
        editor = editorRule == null ? emptyEditor() : buildEditor(editorRule, editorDraft);
        sectionEditors.put(selectedSection, editor);
        editorSection = selectedSection;
        originalArea = areaDraftKey();
    }

    @Override
    public void refreshAfterStateRestore() {
        ensureEditor();
        if (editor != null && Minecraft.getMinecraft() != null
                && Minecraft.getMinecraft().fontRenderer != null) {
            editor.ensureInitialized(Minecraft.getMinecraft().fontRenderer);
        }
    }

    private boolean syncEditorDraft() {
        if (!(editor instanceof ModernFormSettingsTab) || editorRule == null || editorDraft == null) {
            return true;
        }
        if (!((ModernFormSettingsTab<?>) editor).tryApplyDraftValues()) {
            return false;
        }
        String error = applyDraftFields(editorRule, editorDraft);
        if (!error.isEmpty()) {
            status = error;
            return false;
        }
        return true;
    }

    private ModernSettingsTab buildEditor(final AutoFollowRule rule, final EditorDraft draft) {
        ModernFormSettingsTab.Builder<EditorSnapshot> builder = ModernFormSettingsTab.builder(
                safe(rule.name).isEmpty() ? "gui.modern.autofollow.u038" : safe(rule.name),
                "gui.modern.autofollow.u039", "gui.modern.autofollow.u040",
                adapter(rule, draft)).footerVisible(false)
                .headerToggle(bool(() -> rule.enabled, value -> rule.enabled = value), "gui.modern.autofollow.u048");

        if (selectedSection == 0) {
            builder.section("gui.modern.autofollow.u001", "gui.modern.autofollow.u041")
                    .text("gui.modern.autofollow.u042", "gui.modern.autofollow.u043", text(() -> safe(rule.name), value -> rule.name = value),
                            "gui.modern.autofollow.u042", 96)
                    .choice("gui.modern.autofollow.u044", "gui.modern.autofollow.u045",
                            new ModernFormSettingsTab.ChoiceValue<String>() {
                                @Override public String get() { return safe(rule.category); }
                                @Override public void set(String value) { rule.category = value; }
                            }, ModernFormSettingsTab.stringOptions(state.categories()))
                    .readOnly("范围点1", "范围点1的 X / Z 坐标，保留一位小数。",
                            () -> "X " + draft.point1X + "   Z " + draft.point1Z)
                    .readOnly("范围点2", "范围点2的 X / Z 坐标，保留一位小数。",
                            () -> "X " + draft.point2X + "   Z " + draft.point2Z)
                    .action("范围取点", "开启灵魂出窍，左键选点1，右键选点2，中键确认，Esc取消。", "开始取点",
                            ModernFormSettingsTab.ActionStyle.PRIMARY,
                            tab -> startAreaSelection(draft, tab));

        }
        if (selectedSection == 1) {
            builder.section("gui.modern.autofollow.u002", "gui.modern.autofollow.u064")
                    .custom(new AutoFollowCollectionPanel(AutoFollowCollectionPanel.Mode.POINTS,
                            "巡逻回点", () -> draft.returnPointsText, value -> draft.returnPointsText = value,
                            () -> new double[] {Double.parseDouble(draft.point1X), Double.parseDouble(draft.point1Z),
                                    Double.parseDouble(draft.point2X), Double.parseDouble(draft.point2Z)}))
                    .custom(new AutoFollowNumericPanel(new String[] {"回点停留（毫秒）", "巡逻卡住重启（秒）", "到达距离（格）", "最大恢复距离（格）"},
                            text(() -> draft.returnStayMillis, value -> draft.returnStayMillis = value),
                            text(() -> draft.patrolStuckRestartSeconds, value -> draft.patrolStuckRestartSeconds = value),
                            text(() -> draft.returnArriveDistance, value -> draft.returnArriveDistance = value),
                            text(() -> draft.maxRecoveryDistance, value -> draft.maxRecoveryDistance = value)))
                    .choice("gui.modern.autofollow.u090", "gui.modern.autofollow.u091", choice(() -> rule.patrolMode,
                            value -> rule.patrolMode = value), Arrays.asList(
                                    ModernFormSettingsTab.option(AutoFollowRule.PATROL_MODE_ORDER, "gui.modern.autofollow.u092"),
                                    ModernFormSettingsTab.option(AutoFollowRule.PATROL_MODE_RANDOM, "gui.modern.autofollow.u093")));

        }
        if (selectedSection == 2) {
            builder.section("gui.modern.autofollow.u003", "gui.modern.autofollow.u096")
                    .custom(new AutoFollowTargetPanel(ENTITY_TYPES, type -> hasEntityType(rule, type),
                            (type, enabled) -> setEntityType(rule, type, enabled)))
                    .toggle("gui.modern.autofollow.u100", "gui.modern.autofollow.u101",
                            bool(() -> rule.enableMonsterNameList, value -> rule.enableMonsterNameList = value))
                    .custom(new AutoFollowCollectionPanel(AutoFollowCollectionPanel.Mode.NAMES,
                            "白名单", () -> draft.monsterWhitelist, value -> draft.monsterWhitelist = value))
                    .custom(new AutoFollowCollectionPanel(AutoFollowCollectionPanel.Mode.NAMES,
                            "黑名单", () -> draft.monsterBlacklist, value -> draft.monsterBlacklist = value))
                    .toggle("gui.modern.autofollow.u107", "gui.modern.autofollow.u108",
                            bool(() -> rule.targetInvisibleMonsters, value -> rule.targetInvisibleMonsters = value))
                    .readOnly("gui.modern.autofollow.u109", "gui.modern.autofollow.u110",
                            () -> rule.targetSpecialMobs ? "gui.modern.autofollow.u111" : "gui.modern.autofollow.u112");

        }
        if (selectedSection == 3) {
            builder.section("gui.modern.autofollow.u004", "gui.modern.autofollow.u113")
                    .custom(new AutoFollowNumericPanel(new String[] {"垂直搜索范围（格）", "向上搜索范围（格）", "向下搜索范围（格）", "追怪高度限制（格）"},
                            text(() -> draft.monsterVerticalRange, value -> draft.monsterVerticalRange = value),
                            text(() -> draft.monsterUpwardRange, value -> draft.monsterUpwardRange = value),
                            text(() -> draft.monsterDownwardRange, value -> draft.monsterDownwardRange = value),
                            text(() -> draft.monsterChaseYLimit, value -> draft.monsterChaseYLimit = value)))
                    .choice("gui.modern.autofollow.u122", "gui.modern.autofollow.u123", choice(() -> rule.monsterChaseMode,
                            value -> rule.monsterChaseMode = value), Arrays.asList(
                                    ModernFormSettingsTab.option(AutoFollowRule.MONSTER_CHASE_MODE_APPROACH, "gui.modern.autofollow.u124"),
                                    ModernFormSettingsTab.option(AutoFollowRule.MONSTER_CHASE_MODE_FIXED_DISTANCE, "gui.modern.autofollow.u125")))
                    .text("gui.modern.autofollow.u126", "gui.modern.autofollow.u127",
                            text(() -> draft.monsterStopDistance, value -> draft.monsterStopDistance = value), "1.1", 32)
                    .visibleWhen(() -> !isFixedDistanceMode(rule))
                    .text("gui.modern.autofollow.u128", "gui.modern.autofollow.u129",
                            text(() -> draft.monsterFixedDistance, value -> draft.monsterFixedDistance = value), "3", 32)
                    .visibleWhen(() -> isFixedDistanceMode(rule))
                    .readOnly("gui.modern.autofollow.u130", "gui.modern.autofollow.u131", () -> chaseDistanceSummary(rule, draft));

        }
        if (selectedSection == 4) {
            builder.section("gui.modern.autofollow.u005", "gui.modern.autofollow.u132")
                    .text("gui.modern.autofollow.u133", "gui.modern.autofollow.u134",
                            text(() -> draft.lockChaseOutOfBoundsDistance,
                                    value -> draft.lockChaseOutOfBoundsDistance = value), "10", 32)
                    .toggle("gui.modern.autofollow.u135", "gui.modern.autofollow.u136",
                            bool(() -> rule.visualizeRange, value -> rule.visualizeRange = value))
                    .toggle("gui.modern.autofollow.u137", "gui.modern.autofollow.u138",
                            bool(() -> rule.visualizeLockChaseRadius, value -> rule.visualizeLockChaseRadius = value))
                    .toggle("gui.modern.autofollow.u139", "gui.modern.autofollow.u140",
                            bool(state::antiStuckEnabled, state::setAntiStuckEnabled))
                    .toggle("gui.modern.autofollow.u145", "gui.modern.autofollow.u146",
                            bool(state::timeoutReloadEnabled, state::setTimeoutReloadEnabled))
                    .text("gui.modern.autofollow.u147", "gui.modern.autofollow.u148",
                            text(() -> draft.timeoutReloadSeconds, value -> draft.timeoutReloadSeconds = value), "60", 16)
                    .enabledWhen(condition(state::timeoutReloadEnabled));

        }
        if (selectedSection == 5) {
            builder.section("gui.modern.autofollow.u006", "gui.modern.autofollow.u149")
                    .toggle("gui.modern.autofollow.u150", "gui.modern.autofollow.u151",
                            bool(() -> rule.runSequenceWhenOutOfRecoveryRange,
                                    value -> rule.runSequenceWhenOutOfRecoveryRange = value))
                    .action("gui.modern.autofollow.u152", "gui.modern.autofollow.u153",
                            sequenceButtonLabel(rule.outOfRangeSequenceName), ModernFormSettingsTab.ActionStyle.SECONDARY,
                            tab -> openSequenceSelector())
                    .visibleWhen(() -> rule.runSequenceWhenOutOfRecoveryRange)
                    .enabledWhen(condition(() -> rule.runSequenceWhenOutOfRecoveryRange))
                    .readOnly("gui.modern.autofollow.u154", "gui.modern.autofollow.u155", () -> stateSummary(rule))
                    .custom(new AutoFollowCollectionPanel(AutoFollowCollectionPanel.Mode.SCORES,
                            "目标评分优先级", null, null))
                    .readOnly("gui.modern.autofollow.u158", "gui.modern.autofollow.u159",
                            () -> "gui.modern.autofollow.u160")
                    .readOnly("gui.modern.autofollow.u161", "gui.modern.autofollow.u162",
                            () -> "gui.modern.autofollow.u163");

        }
        return builder.build().rememberScroll(editorPositions.computeIfAbsent(rule, key -> new LinkedHashMap<>())
                .computeIfAbsent(selectedSection, key -> new com.zszl.zszlScriptMod.gui.modern.form.RuleSectionState()));
    }


    private ModernFormSettingsTab.StateAdapter<EditorSnapshot> adapter(final AutoFollowRule rule,
            final EditorDraft draft) {
        return new ModernFormSettingsTab.StateAdapter<EditorSnapshot>() {
            @Override
            public void load() {
            }

            @Override
            public EditorSnapshot capture() {
                return new EditorSnapshot(copyRule(rule), draft.copy(), state, selectedReturnPointIndex);
            }

            @Override
            public EditorSnapshot copy(EditorSnapshot value) {
                return value == null ? null : value.copy();
            }

            @Override
            public void restore(EditorSnapshot value) {
                if (value == null) {
                    return;
                }
                copyRuleValues(rule, value.rule);
                draft.restore(value.draft);
                state.restoreGlobals(value);
                selectedReturnPointIndex = normalizeReturnPointSelection(rule, value.selectedReturnPointIndex);
            }

            @Override
            public void save() {
                rule.updateBounds();
                rule.ensureReturnPoints();
            }

            @Override
            public void restoreDefaults() {
            }

            @Override
            public EditorSnapshot createDefaults() {
                AutoFollowRule defaults = new AutoFollowRule();
                return new EditorSnapshot(defaults, new EditorDraft(defaults, 2.0, 60),
                        false, false, 2.0, false, 60, -1);
            }
        };
    }

    private ModernSettingsTab emptyEditor() {
        return ModernFormSettingsTab.builder("gui.modern.autofollow.u164", "gui.modern.autofollow.u165", "").build();
    }

    private void openSequenceSelector() {
        if (sequencePicker != null) {
            sequencePicker.open();
        }
    }

    private void selectSequence(String name) {
        if (editorRule == null) {
            return;
        }
        if (!syncEditorDraft()) {
            return;
        }
        editorRule.outOfRangeSequenceName = safe(name).trim();
        status = editorRule.outOfRangeSequenceName.isEmpty() ? "gui.modern.autofollow.u166"
                : t("gui.modern.autofollow.fmt.overrange", editorRule.outOfRangeSequenceName);
        rebuildEditor();
    }

    private void startAreaSelection(EditorDraft draft, ModernFormSettingsTab<?> tab) {
        AutoFollowAreaPicker.start((first, second) -> {
            draft.point1X = formatCoordinate(first.getX() + 0.5D);
            draft.point1Z = formatCoordinate(first.getZ() + 0.5D);
            draft.point2X = formatCoordinate(second.getX() + 0.5D);
            draft.point2Z = formatCoordinate(second.getZ() + 0.5D);
            tab.refreshValues();
        });
    }

    private static String formatCoordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", finite(value) ? value : 0.0D);
    }

    private void captureReturnPoint(EditorDraft draft, ModernFormSettingsTab<?> tab) {
        Entity entity = renderEntity();
        if (entity == null) {
            status = "gui.modern.autofollow.u167";
            return;
        }
        draft.returnPointX = formatDouble(entity.posX);
        draft.returnPointY = formatDouble(Math.floor(entity.posY));
        draft.returnPointZ = formatDouble(entity.posZ);
        if (tab != null) {
            tab.refreshValues();
        }
        status = "gui.modern.autofollow.u170";
    }

    private Entity renderEntity() {
        try {
            return Minecraft.getMinecraft().getRenderViewEntity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void addReturnPoint(AutoFollowRule rule, EditorDraft draft) {
        if (!syncEditorDraft()) {
            return;
        }
        try {
            double x = parseFinite(draft.returnPointX, "gui.modern.autofollow.u171");
            double y = parseFinite(draft.returnPointY, "gui.modern.autofollow.u172");
            double z = parseFinite(draft.returnPointZ, "gui.modern.autofollow.u173");
            AutoFollowHandler.Point point = new AutoFollowHandler.Point(x, y, z);

            rule.updateBounds();
            if (isBoundsValidationReady(rule) && !rule.isPointWithinBounds(point)) {
                status = "gui.modern.autofollow.u174";
                return;
            }

            List<AutoFollowHandler.Point> points = copyReturnPoints(rule.returnPoints);
            for (int i = 0; i < points.size(); i++) {
                AutoFollowHandler.Point existing = points.get(i);
                if (samePoint(existing, point)) {
                    selectedReturnPointIndex = i;
                    status = "gui.modern.autofollow.u175";
                    rebuildEditor();
                    return;
                }
                if (existing != null && !existing.hasY()
                        && Math.abs(existing.x - point.x) < 0.01
                        && Math.abs(existing.z - point.z) < 0.01) {
                    points.set(i, point);
                    selectedReturnPointIndex = i;
                    rule.returnPoints = points;
                    draft.returnPointsText = formatReturnPoints(points);
                    status = "gui.modern.autofollow.u176";
                    rebuildEditor();
                    return;
                }
            }

            points.add(point);
            rule.returnPoints = points;
            rule.point3 = AutoFollowRule.copyPoint(point);
            draft.returnPointsText = formatReturnPoints(points);
            selectedReturnPointIndex = points.size() - 1;
            status = t("gui.modern.autofollow.fmt.added_point", formatDouble(x), formatDouble(y), formatDouble(z));
            rebuildEditor();
        } catch (IllegalArgumentException exception) {
            status = exception.getMessage();
        }
    }

    private void removeSelectedReturnPoint(AutoFollowRule rule, EditorDraft draft) {
        if (!syncEditorDraft()) {
            return;
        }
        if (!hasSelectedReturnPoint(rule)) {
            status = "gui.modern.autofollow.u177";
            return;
        }
        List<AutoFollowHandler.Point> points = copyReturnPoints(rule.returnPoints);
        points.remove(selectedReturnPointIndex);
        rule.returnPoints = points;
        draft.returnPointsText = formatReturnPoints(points);
        selectedReturnPointIndex = points.isEmpty() ? -1 : Math.min(selectedReturnPointIndex, points.size() - 1);
        status = "gui.modern.autofollow.u178";
        rebuildEditor();
    }

    private boolean hasSelectedReturnPoint(AutoFollowRule rule) {
        return rule != null && rule.returnPoints != null && selectedReturnPointIndex >= 0
                && selectedReturnPointIndex < rule.returnPoints.size();
    }

    private String applyDraftFields(AutoFollowRule rule, EditorDraft draft) {
        try {
            double point1X = parseFinite(draft.point1X, "gui.modern.autofollow.u049");
            double point1Z = parseFinite(draft.point1Z, "gui.modern.autofollow.u051");
            double point2X = parseFinite(draft.point2X, "gui.modern.autofollow.u056");
            double point2Z = parseFinite(draft.point2Z, "gui.modern.autofollow.u058");
            int returnStay = parseInteger(draft.returnStayMillis, "gui.modern.autofollow.u084");
            int stuckRestart = parseInteger(draft.patrolStuckRestartSeconds, "gui.modern.autofollow.u086");
            double arriveDistance = parseFinite(draft.returnArriveDistance, "gui.modern.autofollow.u088");
            int chaseYLimit = parseInteger(draft.monsterChaseYLimit, "gui.modern.autofollow.u120");
            double maxRecovery = parseFinite(draft.maxRecoveryDistance, "gui.modern.autofollow.u094");
            double vertical = parseFinite(draft.monsterVerticalRange, "gui.modern.autofollow.u114");
            double upward = parseFinite(draft.monsterUpwardRange, "gui.modern.autofollow.u116");
            double downward = parseFinite(draft.monsterDownwardRange, "gui.modern.autofollow.u118");
            double stopDistance = parseFinite(draft.monsterStopDistance, "gui.modern.autofollow.u126");
            double fixedDistance = parseFinite(draft.monsterFixedDistance, "gui.modern.autofollow.u128");
            double lockDistance = parseFinite(draft.lockChaseOutOfBoundsDistance, "gui.modern.autofollow.u133");
            int timeoutSeconds = parseInteger(draft.timeoutReloadSeconds, "gui.modern.autofollow.u147");
            ParsedReturnPoints parsedPoints = parseReturnPoints(draft.returnPointsText);

            if (returnStay <= 0 || stuckRestart <= 0 || arriveDistance <= 0 || chaseYLimit < 0
                    || maxRecovery < 0 || vertical <= 0 || upward <= 0 || downward <= 0
                    || stopDistance <= 0 || fixedDistance <= 0 || lockDistance <= 0
                    || timeoutSeconds <= 0) {
                throw new IllegalArgumentException("gui.modern.autofollow.u179");
            }

            AutoFollowHandler.Point point1 = AutoFollowRule.copyPoint(rule.point1);
            AutoFollowHandler.Point point2 = AutoFollowRule.copyPoint(rule.point2);
            point1.x = point1X;
            point1.z = point1Z;
            point2.x = point2X;
            point2.z = point2Z;

            rule.name = safe(rule.name).trim();
            rule.category = category(rule.category);
            rule.point1 = point1;
            rule.point2 = point2;
            rule.returnStayMillis = returnStay;
            rule.patrolStuckRestartSeconds = stuckRestart;
            rule.returnArriveDistance = arriveDistance;
            rule.monsterChaseYLimit = chaseYLimit;
            rule.maxRecoveryDistance = maxRecovery;
            rule.monsterVerticalRange = vertical;
            rule.monsterUpwardRange = upward;
            rule.monsterDownwardRange = downward;
            rule.monsterStopDistance = stopDistance;
            rule.monsterFixedDistance = fixedDistance;
            rule.lockChaseOutOfBoundsDistance = lockDistance;
            rule.returnPoints = copyReturnPoints(parsedPoints.points);
            rule.monsterWhitelistNames = parseNameList(draft.monsterWhitelist);
            rule.monsterBlacklistNames = parseNameList(draft.monsterBlacklist);
            rule.targetSpecialMobs = hasEntityType(rule, AutoFollowRule.ENTITY_TYPE_BOSS)
                    || hasEntityType(rule, AutoFollowRule.ENTITY_TYPE_GOLEM);

            rule.updateBounds();
            if (isBoundsValidationReady(rule)) {
                for (AutoFollowHandler.Point point : parsedPoints.points) {
                    if (point != null && !rule.isPointWithinBounds(point)) {
                        throw new IllegalArgumentException("gui.modern.autofollow.u180");
                    }
                }
            }
            rule.ensureReturnPoints();
            draft.returnPointsText = formatReturnPoints(rule.returnPoints);
            selectedReturnPointIndex = normalizeReturnPointSelection(rule, selectedReturnPointIndex);

            state.setTimeoutReloadSeconds(timeoutSeconds);
            return "";
        } catch (IllegalArgumentException exception) {
            return safe(exception.getMessage()).isEmpty() ? "gui.modern.autofollow.u181" : exception.getMessage();
        }
    }

    private String validateState() {
        if (state.timeoutReloadSeconds() <= 0) {
            return "gui.modern.autofollow.u183";
        }
        for (AutoFollowRule rule : state.rules()) {
            String error = validateRule(rule);
            if (!error.isEmpty()) {
                return error + "：" + safe(rule == null ? "" : rule.name);
            }
        }
        return "";
    }

    private String validateRule(AutoFollowRule rule) {
        if (rule == null) {
            return "gui.modern.autofollow.u184";
        }
        if (safe(rule.name).trim().isEmpty()) {
            return "gui.modern.autofollow.u185";
        }
        if (rule.entityTypes == null || rule.entityTypes.isEmpty()) {
            return "gui.modern.autofollow.u186";
        }
        if (!isFinitePoint(rule.point1) || !isFinitePoint(rule.point2)) {
            return "gui.modern.autofollow.u187";
        }
        if (rule.maxRecoveryDistance < 0 || !finite(rule.maxRecoveryDistance)) {
            return "gui.modern.autofollow.u188";
        }
        if (rule.returnStayMillis <= 0 || rule.patrolStuckRestartSeconds <= 0
                || !finite(rule.returnArriveDistance) || rule.returnArriveDistance <= 0) {
            return "gui.modern.autofollow.u189";
        }
        if (rule.monsterChaseYLimit == null || rule.monsterChaseYLimit < 0) {
            return "gui.modern.autofollow.u190";
        }
        if (!finite(rule.monsterVerticalRange) || rule.monsterVerticalRange <= 0
                || !finite(rule.monsterUpwardRange) || rule.monsterUpwardRange <= 0
                || !finite(rule.monsterDownwardRange) || rule.monsterDownwardRange <= 0
                || !finite(rule.monsterStopDistance) || rule.monsterStopDistance <= 0
                || !finite(rule.monsterFixedDistance) || rule.monsterFixedDistance <= 0
                || !finite(rule.lockChaseOutOfBoundsDistance) || rule.lockChaseOutOfBoundsDistance <= 0) {
            return "gui.modern.autofollow.u191";
        }
        if (rule.returnPoints != null) {
            for (AutoFollowHandler.Point point : rule.returnPoints) {
                if (!isFinitePoint(point)) {
                    return "gui.modern.autofollow.u192";
                }
                if (isBoundsValidationReady(rule) && !rule.isPointWithinBounds(point)) {
                    return "gui.modern.autofollow.u180";
                }
            }
        }
        return "";
    }

    private boolean isBoundsValidationReady(AutoFollowRule rule) {
        return rule != null && (!approximatelyZero(rule.point1.x) || !approximatelyZero(rule.point1.z))
                && (!approximatelyZero(rule.point2.x) || !approximatelyZero(rule.point2.z));
    }

    private List<AutoFollowRule> matching(WorkbenchState.Group group, String query) {
        if (query.isEmpty() || PinyinSearchHelper.matchesNormalized(group.name(), query)) {
            return group.rules();
        }
        List<AutoFollowRule> result = new ArrayList<>();
        for (AutoFollowRule rule : group.rules()) {
            if (PinyinSearchHelper.matchesNormalized(rule.name, query)) {
                result.add(rule);
            }
        }
        return result;
    }

    private String selectedCategory() {
        if (navigationCategory != null) return navigationCategory;
        return state.selected() == null ? DEFAULT_CATEGORY : category(state.selected().category);
    }

    private void drawGroup(FontRenderer font, ModernMainLayout.Rect row, String name, int count,
            boolean collapsed, int mouseX, int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(row.x + 8, row.y + 7, collapsed, ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawText(font, name, row.x + 20, row.y + 7, ModernUiRenderer.TEXT,
                Math.max(1, row.width - 54));
        ModernUiRenderer.drawText(font, String.valueOf(count), row.right() - 23, row.y + 7,
                ModernUiRenderer.MUTED_TEXT, 18);
    }

    private void drawRule(FontRenderer font, ModernMainLayout.Rect row, AutoFollowRule rule,
            int mouseX, int mouseY) {
        boolean selected = rule == state.selected();
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 9,
                rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawText(font, safe(rule.name), row.x + 20, row.y + 5,
                rule.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, row.width - 28));
        String detail = t("gui.modern.autofollow.fmt.rule_row", entityTypeSummary(rule),
                String.valueOf(rule.returnPoints == null ? 0 : rule.returnPoints.size()),
                chaseModeLabel(rule.monsterChaseMode));
        ModernUiRenderer.drawText(font, detail, row.x + 20, row.y + 17, ModernUiRenderer.MUTED_TEXT,
                Math.max(1, row.width - 28));
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect row, String label, boolean primary,
            int mouseX, int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY);
        int fill = primary ? (hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT)
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4, fill,
                primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, row.x + 6, row.y + 6,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, Math.max(1, row.width - 12));
    }

    private String rangeSummary(AutoFollowRule rule) {
        if (rule == null || rule.point1 == null || rule.point2 == null) {
            return t("gui.modern.autofollow.u193");
        }
        return "X " + formatDouble(Math.min(rule.point1.x, rule.point2.x)) + " .. "
                + formatDouble(Math.max(rule.point1.x, rule.point2.x)) + " · Z "
                + formatDouble(Math.min(rule.point1.z, rule.point2.z)) + " .. "
                + formatDouble(Math.max(rule.point1.z, rule.point2.z));
    }

    private String returnPointsSummary(AutoFollowRule rule) {
        int count = rule == null || rule.returnPoints == null ? 0 : rule.returnPoints.size();
        if (count == 0) {
            return t("gui.modern.autofollow.u194");
        }
        AutoFollowHandler.Point first = rule.returnPoints.get(0);
        return t("gui.modern.autofollow.fmt.points_first", String.valueOf(count), pointText(first));
    }

    private String stateSummary(AutoFollowRule rule) {
        return t("gui.modern.autofollow.fmt.state",
                t(rule != null && rule.enabled ? "gui.modern.autofollow.u195" : "gui.modern.autofollow.u196"),
                chaseModeLabel(rule == null ? "" : rule.monsterChaseMode),
                String.valueOf(rule == null || rule.returnPoints == null ? 0 : rule.returnPoints.size()),
                t(state.antiStuckEnabled() ? "gui.modern.autofollow.u197" : "gui.modern.autofollow.u198"));
    }

    private String chaseDistanceSummary(AutoFollowRule rule, EditorDraft draft) {
        if (isFixedDistanceMode(rule)) {
            return t("gui.modern.autofollow.fmt.fixed", safe(draft.monsterFixedDistance));
        }
        return t("gui.modern.autofollow.fmt.chase", safe(draft.monsterStopDistance));
    }

    private String scoredSummary() {
        List<AutoFollowHandler.ScoredMonsterInfo> scores = AutoFollowHandler.getLastScoredMonstersSnapshot();
        if (scores.isEmpty()) {
            return t("gui.modern.autofollow.u199");
        }
        StringBuilder details = new StringBuilder();
        int count = Math.min(3, scores.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                details.append(" | ");
            }
            AutoFollowHandler.ScoredMonsterInfo info = scores.get(i);
            details.append(safe(info.name)).append(" ").append(formatDouble(info.totalScore));
        }
        return t("gui.modern.autofollow.fmt.candidates", String.valueOf(scores.size()), details.toString());
    }

    private String entityTypeSummary(AutoFollowRule rule) {
        if (rule == null || rule.entityTypes == null || rule.entityTypes.isEmpty()) {
            return t("gui.modern.autofollow.u200");
        }
        List<String> labels = new ArrayList<>();
        for (String[] type : ENTITY_TYPES) {
            if (hasEntityType(rule, type[0])) {
                labels.add(t(type[1]));
            }
        }
        return labels.isEmpty() ? t("gui.modern.autofollow.u200") : join(labels);
    }

    private List<ModernFormSettingsTab.ChoiceOption<Integer>> returnPointOptions(AutoFollowRule rule) {
        List<ModernFormSettingsTab.ChoiceOption<Integer>> options = new ArrayList<>();
        if (rule == null || rule.returnPoints == null || rule.returnPoints.isEmpty()) {
            options.add(ModernFormSettingsTab.option(-1, "gui.modern.autofollow.u201"));
            return options;
        }
        for (int i = 0; i < rule.returnPoints.size(); i++) {
            options.add(ModernFormSettingsTab.option(i, (i + 1) + ". " + pointText(rule.returnPoints.get(i))));
        }
        return options;
    }

    private static boolean hasEntityType(AutoFollowRule rule, String token) {
        return rule != null && rule.entityTypes != null && rule.entityTypes.contains(token);
    }

    private static void setEntityType(AutoFollowRule rule, String token, boolean enabled) {
        if (rule.entityTypes == null) {
            rule.entityTypes = new ArrayList<>();
        }
        if (enabled) {
            if (!rule.entityTypes.contains(token)) {
                rule.entityTypes.add(token);
            }
        } else {
            rule.entityTypes.remove(token);
        }
    }

    private static boolean isFixedDistanceMode(AutoFollowRule rule) {
        return rule != null && AutoFollowRule.MONSTER_CHASE_MODE_FIXED_DISTANCE.equalsIgnoreCase(rule.monsterChaseMode);
    }

    private static String chaseModeLabel(String mode) {
        return t(AutoFollowRule.MONSTER_CHASE_MODE_FIXED_DISTANCE.equalsIgnoreCase(mode)
                ? "gui.modern.autofollow.u128" : "gui.modern.autofollow.u202");
    }

    private static String sequenceButtonLabel(String value) {
        return safe(value).trim().isEmpty() ? "gui.modern.autofollow.u203" : safe(value).trim();
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

    private static ModernFormSettingsTab.Condition condition(final BoolGet getter) {
        return new ModernFormSettingsTab.Condition() {
            @Override
            public boolean matches() {
                return getter.get();
            }
        };
    }

    private static <T> ModernFormSettingsTab.ChoiceValue<T> choice(final ChoiceGet<T> getter,
            final ChoiceSet<T> setter) {
        return new ModernFormSettingsTab.ChoiceValue<T>() {
            @Override
            public T get() {
                return getter.get();
            }

            @Override
            public void set(T value) {
                setter.set(value);
            }
        };
    }

    private static String category(String value) {
        String normalized = safe(value).trim();
        // Earlier workbench versions accidentally stored the localization key. It translates to the same visible
        // label as the real default category, producing two indistinguishable rows in the navigator.
        if (normalized.isEmpty() || LEGACY_DEFAULT_CATEGORY_KEY.equalsIgnoreCase(normalized)) {
            return DEFAULT_CATEGORY;
        }
        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String t(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String t(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(", ", values);
    }

    private static String formatDouble(double value) {
        if (!finite(value)) {
            return "0";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String formatReturnPoints(List<AutoFollowHandler.Point> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (AutoFollowHandler.Point point : points) {
            if (point == null) {
                continue;
            }
            values.add(point.hasY()
                    ? formatDouble(point.x) + "," + formatDouble(point.y) + "," + formatDouble(point.z)
                    : formatDouble(point.x) + "," + formatDouble(point.z));
        }
        return joinWithSeparator(values, "; ");
    }

    private static String pointText(AutoFollowHandler.Point point) {
        if (point == null) {
            return t("gui.modern.autofollow.u204");
        }
        return point.hasY()
                ? "(" + formatDouble(point.x) + ", " + formatDouble(point.y) + ", " + formatDouble(point.z) + ")"
                : t("gui.modern.autofollow.fmt.legacy_y", formatDouble(point.x), formatDouble(point.z));
    }

    private static String joinWithSeparator(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (result.length() > 0) {
                    result.append(separator);
                }
                result.append(value);
            }
        }
        return result.toString();
    }

    private static ParsedReturnPoints parseReturnPoints(String value) {
        String normalized = safe(value).replace('；', ';').replace('，', ',')
                .replace('\n', ';').replace('\r', ';').trim();
        List<AutoFollowHandler.Point> points = new ArrayList<>();
        if (normalized.isEmpty()) {
            return new ParsedReturnPoints(points);
        }
        String[] entries = normalized.split(";");
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.startsWith("(") && entry.endsWith(")")) {
                entry = entry.substring(1, entry.length() - 1).trim();
            }
            String[] coordinates = entry.split("\\s*(?:,|\\|)\\s*");
            if (coordinates.length != 2 && coordinates.length != 3) {
                throw new IllegalArgumentException(t("gui.modern.autofollow.fmt.queue_format", String.valueOf(i + 1)));
            }
            double x = parseFinite(coordinates[0], t("gui.modern.autofollow.fmt.queue_x", String.valueOf(i + 1)));
            if (coordinates.length == 2) {
                double z = parseFinite(coordinates[1], t("gui.modern.autofollow.fmt.queue_z", String.valueOf(i + 1)));
                points.add(new AutoFollowHandler.Point(x, z));
            } else {
                double y = parseFinite(coordinates[1], t("gui.modern.autofollow.fmt.queue_y", String.valueOf(i + 1)));
                double z = parseFinite(coordinates[2], t("gui.modern.autofollow.fmt.queue_z", String.valueOf(i + 1)));
                points.add(new AutoFollowHandler.Point(x, y, z));
            }
        }
        return new ParsedReturnPoints(points);
    }

    private static List<String> parseNameList(String value) {
        List<String> result = new ArrayList<>();
        String normalized = safe(value).replace('，', ',').replace('\n', ',').replace('\r', ',');
        for (String part : normalized.split(",")) {
            String item = KillAuraHandler.normalizeFilterName(part);
            if (!item.isEmpty() && !containsIgnoreCase(result, item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private static double parseFinite(String value, String label) {
        try {
            double parsed = Double.parseDouble(safe(value).trim().replace(',', '.'));
            if (!finite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(t("gui.modern.autofollow.fmt.need_number", label));
        }
    }

    private static int parseInteger(String value, String label) {
        try {
            return Integer.parseInt(safe(value).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(t("gui.modern.autofollow.fmt.need_int", label));
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinitePoint(AutoFollowHandler.Point point) {
        return point != null && finite(point.x) && finite(point.z) && (!point.hasY() || finite(point.y));
    }

    private static boolean approximatelyZero(double value) {
        return Math.abs(value) < 0.01;
    }

    private static boolean samePoint(AutoFollowHandler.Point left, AutoFollowHandler.Point right) {
        return left != null && right != null && left.hasY() == right.hasY()
                && Math.abs(left.x - right.x) < 0.01
                && Math.abs(left.z - right.z) < 0.01
                && (!left.hasY() || Math.abs(left.y - right.y) < 0.01);
    }

    private static int normalizeReturnPointSelection(AutoFollowRule rule, int selected) {
        int count = rule == null || rule.returnPoints == null ? 0 : rule.returnPoints.size();
        return count == 0 ? -1 : Math.max(0, Math.min(selected, count - 1));
    }

    private static List<AutoFollowHandler.Point> copyReturnPoints(List<AutoFollowHandler.Point> source) {
        List<AutoFollowHandler.Point> result = new ArrayList<>();
        if (source != null) {
            for (AutoFollowHandler.Point point : source) {
                if (point != null) {
                    result.add(AutoFollowRule.copyPoint(point));
                }
            }
        }
        return result;
    }

    private static List<AutoFollowRule> copyRules(List<AutoFollowRule> source) {
        List<AutoFollowRule> result = new ArrayList<>();
        if (source != null) {
            for (AutoFollowRule rule : source) {
                if (rule != null) {
                    result.add(copyRule(rule));
                }
            }
        }
        return result;
    }

    private static AutoFollowRule copyRule(AutoFollowRule source) {
        AutoFollowRule target = new AutoFollowRule();
        if (source == null) {
            return target;
        }
        copyRuleValues(target, source);
        return target;
    }

    private static void copyRuleValues(AutoFollowRule target, AutoFollowRule source) {
        if (target == null || source == null) {
            return;
        }
        target.name = source.name;
        target.category = source.category;
        target.enabled = source.enabled;
        target.point1 = AutoFollowRule.copyPoint(source.point1);
        target.point2 = AutoFollowRule.copyPoint(source.point2);
        target.point3 = AutoFollowRule.copyPoint(source.point3);
        target.returnPoints = source.returnPoints == null ? null : copyReturnPoints(source.returnPoints);
        target.returnStayMillis = source.returnStayMillis;
        target.patrolStuckRestartSeconds = source.patrolStuckRestartSeconds;
        target.returnArriveDistance = source.returnArriveDistance;
        target.patrolMode = source.patrolMode;
        target.monsterVerticalRange = source.monsterVerticalRange;
        target.monsterUpwardRange = source.monsterUpwardRange;
        target.monsterDownwardRange = source.monsterDownwardRange;
        target.monsterChaseYLimit = source.monsterChaseYLimit;
        target.monsterChaseMode = source.monsterChaseMode;
        target.monsterStopDistance = source.monsterStopDistance;
        target.monsterFixedDistance = source.monsterFixedDistance;
        target.entityTypes = source.entityTypes == null ? new ArrayList<>() : new ArrayList<>(source.entityTypes);
        target.enableMonsterNameList = source.enableMonsterNameList;
        target.monsterWhitelistNames = source.monsterWhitelistNames == null
                ? new ArrayList<>() : new ArrayList<>(source.monsterWhitelistNames);
        target.monsterBlacklistNames = source.monsterBlacklistNames == null
                ? new ArrayList<>() : new ArrayList<>(source.monsterBlacklistNames);
        target.targetInvisibleMonsters = source.targetInvisibleMonsters;
        target.targetSpecialMobs = source.targetSpecialMobs;
        target.lockChaseOutOfBoundsDistance = source.lockChaseOutOfBoundsDistance;
        target.maxRecoveryDistance = source.maxRecoveryDistance;
        target.runSequenceWhenOutOfRecoveryRange = source.runSequenceWhenOutOfRecoveryRange;
        target.outOfRangeSequenceName = source.outOfRangeSequenceName;
        target.visualizeRange = source.visualizeRange;
        target.visualizeLockChaseRadius = source.visualizeLockChaseRadius;
        target.updateBounds();
        target.ensureReturnPoints();
    }

    private static AutoFollowRule findByName(List<AutoFollowRule> rules, String name) {
        String normalized = safe(name).trim();
        if (normalized.isEmpty() || rules == null) {
            return null;
        }
        for (AutoFollowRule rule : rules) {
            if (rule != null && normalized.equalsIgnoreCase(safe(rule.name).trim())) {
                return rule;
            }
        }
        return null;
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

    private interface ChoiceGet<T> {
        T get();
    }

    private interface ChoiceSet<T> {
        void set(T value);
    }

    private static final class RuleHit {
        private final AutoFollowRule rule;
        private final ModernMainLayout.Rect bounds;

        private RuleHit(AutoFollowRule rule, ModernMainLayout.Rect bounds) {
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

    private static final class ParsedReturnPoints {
        private final List<AutoFollowHandler.Point> points;

        private ParsedReturnPoints(List<AutoFollowHandler.Point> points) {
            this.points = points;
        }
    }

    private static final class EditorDraft {
        private String point1X;
        private String point1Z;
        private String point2X;
        private String point2Z;
        private String returnPointX;
        private String returnPointY;
        private String returnPointZ;
        private String returnPointsText;
        private String returnStayMillis;
        private String patrolStuckRestartSeconds;
        private String returnArriveDistance;
        private String monsterChaseYLimit;
        private String maxRecoveryDistance;
        private String monsterVerticalRange;
        private String monsterUpwardRange;
        private String monsterDownwardRange;
        private String monsterStopDistance;
        private String monsterFixedDistance;
        private String monsterWhitelist;
        private String monsterBlacklist;
        private String lockChaseOutOfBoundsDistance;
        private String vineAvoidanceDistance;
        private String timeoutReloadSeconds;

        private EditorDraft(AutoFollowRule rule, WorkbenchState state) {
            this(rule, state.vineAvoidanceDistance(), state.timeoutReloadSeconds());
        }

        private EditorDraft(AutoFollowRule rule, double vineAvoidanceDistance, int timeoutReloadSeconds) {
            AutoFollowRule model = rule == null ? new AutoFollowRule() : rule;
            model.updateBounds();
            model.ensureReturnPoints();
            point1X = formatCoordinate(model.point1 == null ? 0 : model.point1.x);
            point1Z = formatCoordinate(model.point1 == null ? 0 : model.point1.z);
            point2X = formatCoordinate(model.point2 == null ? 0 : model.point2.x);
            point2Z = formatCoordinate(model.point2 == null ? 0 : model.point2.z);
            AutoFollowHandler.Point primary = model.getPrimaryReturnPoint();
            returnPointX = formatDouble(primary == null ? 0 : primary.x);
            returnPointY = primary != null && primary.hasY() ? formatDouble(primary.y) : "";
            returnPointZ = formatDouble(primary == null ? 0 : primary.z);
            returnPointsText = formatReturnPoints(model.returnPoints);
            returnStayMillis = String.valueOf(model.returnStayMillis);
            patrolStuckRestartSeconds = String.valueOf(model.patrolStuckRestartSeconds);
            returnArriveDistance = formatDouble(model.returnArriveDistance);
            monsterChaseYLimit = String.valueOf(model.monsterChaseYLimit == null ? 0 : model.monsterChaseYLimit);
            maxRecoveryDistance = formatDouble(model.maxRecoveryDistance);
            monsterVerticalRange = formatDouble(model.monsterVerticalRange);
            monsterUpwardRange = formatDouble(model.monsterUpwardRange);
            monsterDownwardRange = formatDouble(model.monsterDownwardRange);
            monsterStopDistance = formatDouble(model.monsterStopDistance);
            monsterFixedDistance = formatDouble(model.monsterFixedDistance);
            monsterWhitelist = join(model.monsterWhitelistNames);
            monsterBlacklist = join(model.monsterBlacklistNames);
            lockChaseOutOfBoundsDistance = formatDouble(model.lockChaseOutOfBoundsDistance);
            this.vineAvoidanceDistance = formatDouble(vineAvoidanceDistance);
            this.timeoutReloadSeconds = String.valueOf(timeoutReloadSeconds);
        }

        private EditorDraft copy() {
            EditorDraft copy = new EditorDraft(new AutoFollowRule(), 2.0, 60);
            copy.restore(this);
            return copy;
        }

        private void restore(EditorDraft source) {
            if (source == null) {
                return;
            }
            point1X = source.point1X;
            point1Z = source.point1Z;
            point2X = source.point2X;
            point2Z = source.point2Z;
            returnPointX = source.returnPointX;
            returnPointY = source.returnPointY;
            returnPointZ = source.returnPointZ;
            returnPointsText = source.returnPointsText;
            returnStayMillis = source.returnStayMillis;
            patrolStuckRestartSeconds = source.patrolStuckRestartSeconds;
            returnArriveDistance = source.returnArriveDistance;
            monsterChaseYLimit = source.monsterChaseYLimit;
            maxRecoveryDistance = source.maxRecoveryDistance;
            monsterVerticalRange = source.monsterVerticalRange;
            monsterUpwardRange = source.monsterUpwardRange;
            monsterDownwardRange = source.monsterDownwardRange;
            monsterStopDistance = source.monsterStopDistance;
            monsterFixedDistance = source.monsterFixedDistance;
            monsterWhitelist = source.monsterWhitelist;
            monsterBlacklist = source.monsterBlacklist;
            lockChaseOutOfBoundsDistance = source.lockChaseOutOfBoundsDistance;
            vineAvoidanceDistance = source.vineAvoidanceDistance;
            timeoutReloadSeconds = source.timeoutReloadSeconds;
        }
    }

    private static final class EditorSnapshot {
        private final AutoFollowRule rule;
        private final EditorDraft draft;
        private final boolean antiStuckEnabled;
        private final boolean avoidVinesProactively;
        private final double vineAvoidanceDistance;
        private final boolean timeoutReloadEnabled;
        private final int timeoutReloadSeconds;
        private final int selectedReturnPointIndex;

        private EditorSnapshot(AutoFollowRule rule, EditorDraft draft, WorkbenchState state,
                int selectedReturnPointIndex) {
            this(rule, draft, state.antiStuckEnabled(), state.avoidVinesProactively(), state.vineAvoidanceDistance(),
                    state.timeoutReloadEnabled(), state.timeoutReloadSeconds(), selectedReturnPointIndex);
        }

        private EditorSnapshot(AutoFollowRule rule, EditorDraft draft, boolean antiStuckEnabled,
                boolean avoidVinesProactively, double vineAvoidanceDistance, boolean timeoutReloadEnabled,
                int timeoutReloadSeconds, int selectedReturnPointIndex) {
            this.rule = rule;
            this.draft = draft;
            this.antiStuckEnabled = antiStuckEnabled;
            this.avoidVinesProactively = avoidVinesProactively;
            this.vineAvoidanceDistance = vineAvoidanceDistance;
            this.timeoutReloadEnabled = timeoutReloadEnabled;
            this.timeoutReloadSeconds = timeoutReloadSeconds;
            this.selectedReturnPointIndex = selectedReturnPointIndex;
        }

        private EditorSnapshot copy() {
            return new EditorSnapshot(copyRule(rule), draft == null ? null : draft.copy(), antiStuckEnabled,
                    avoidVinesProactively, vineAvoidanceDistance, timeoutReloadEnabled, timeoutReloadSeconds,
                    selectedReturnPointIndex);
        }
    }

    private static final class WorkbenchState {
        private static final Gson GSON = new Gson();

        private List<AutoFollowRule> original;
        private final List<AutoFollowRule> rules = new ArrayList<>();
        private final List<String> categoryOrder = new ArrayList<>();
        private List<String> originalCategoryOrder;
        private AutoFollowRule selected;

        private boolean antiStuckEnabled;
        private boolean avoidVinesProactively;
        private double vineAvoidanceDistance;
        private boolean timeoutReloadEnabled;
        private int timeoutReloadSeconds;
        private boolean originalAntiStuckEnabled;
        private boolean originalAvoidVinesProactively;
        private double originalVineAvoidanceDistance;
        private boolean originalTimeoutReloadEnabled;
        private int originalTimeoutReloadSeconds;

        private WorkbenchState(List<AutoFollowRule> source, List<String> categories, boolean antiStuckEnabled,
                boolean avoidVinesProactively, double vineAvoidanceDistance, boolean timeoutReloadEnabled,
                int timeoutReloadSeconds) {
            this.antiStuckEnabled = antiStuckEnabled;
            this.avoidVinesProactively = avoidVinesProactively;
            this.vineAvoidanceDistance = vineAvoidanceDistance;
            this.timeoutReloadEnabled = timeoutReloadEnabled;
            this.timeoutReloadSeconds = timeoutReloadSeconds;
            this.originalAntiStuckEnabled = antiStuckEnabled;
            this.originalAvoidVinesProactively = avoidVinesProactively;
            this.originalVineAvoidanceDistance = vineAvoidanceDistance;
            this.originalTimeoutReloadEnabled = timeoutReloadEnabled;
            this.originalTimeoutReloadSeconds = timeoutReloadSeconds;
            if (categories != null) {
                for (String category : categories) {
                    addCategory(category);
                }
            }
            rules.addAll(copyRules(source));
            for (AutoFollowRule rule : rules) {
                addCategory(rule.category);
            }
            if (rules.isEmpty()) {
                rules.add(new AutoFollowRule());
                addCategory(DEFAULT_CATEGORY);
            }
            selected = rules.get(0);
            originalCategoryOrder = new ArrayList<>(categoryOrder);
            original = copyRules(rules);
        }

        private List<AutoFollowRule> rules() {
            return Collections.unmodifiableList(rules);
        }

        private AutoFollowRule selected() {
            return selected;
        }

        private boolean antiStuckEnabled() {
            return antiStuckEnabled;
        }

        private boolean avoidVinesProactively() {
            return avoidVinesProactively;
        }

        private double vineAvoidanceDistance() {
            return vineAvoidanceDistance;
        }

        private boolean timeoutReloadEnabled() {
            return timeoutReloadEnabled;
        }

        private int timeoutReloadSeconds() {
            return timeoutReloadSeconds;
        }

        private void setAntiStuckEnabled(boolean value) {
            antiStuckEnabled = value;
        }

        private void setAvoidVinesProactively(boolean value) {
            avoidVinesProactively = value;
        }

        private void setVineAvoidanceDistance(double value) {
            vineAvoidanceDistance = value;
        }

        private void setTimeoutReloadEnabled(boolean value) {
            timeoutReloadEnabled = value;
        }

        private void setTimeoutReloadSeconds(int value) {
            timeoutReloadSeconds = value;
        }

        private void restoreGlobals(EditorSnapshot snapshot) {
            antiStuckEnabled = snapshot.antiStuckEnabled;
            avoidVinesProactively = snapshot.avoidVinesProactively;
            vineAvoidanceDistance = snapshot.vineAvoidanceDistance;
            timeoutReloadEnabled = snapshot.timeoutReloadEnabled;
            timeoutReloadSeconds = snapshot.timeoutReloadSeconds;
        }

        private List<String> categories() {
            ensureCategories();
            return new ArrayList<>(categoryOrder);
        }

        private List<Group> groups() {
            ensureCategories();
            Map<String, List<AutoFollowRule>> grouped = new LinkedHashMap<>();
            for (String category : categoryOrder) {
                grouped.put(category, new ArrayList<AutoFollowRule>());
            }
            for (AutoFollowRule rule : rules) {
                String key = canonicalCategory(rule.category);
                List<AutoFollowRule> group = grouped.get(key);
                if (group == null) {
                    group = new ArrayList<>();
                    grouped.put(key, group);
                }
                group.add(rule);
            }
            List<Group> result = new ArrayList<>();
            for (Map.Entry<String, List<AutoFollowRule>> entry : grouped.entrySet()) {
                result.add(new Group(entry.getKey(), entry.getValue()));
            }
            return result;
        }

        private boolean hasEnabledRule() {
            for (AutoFollowRule rule : rules) {
                if (rule != null && rule.enabled) {
                    return true;
                }
            }
            return false;
        }

        private String activeSummary() {
            for (AutoFollowRule rule : rules) {
                if (rule != null && rule.enabled) {
                    return t("gui.modern.autofollow.fmt.enabled_named", safe(rule.name));
                }
            }
            return "gui.modern.autofollow.u207";
        }

        private void select(AutoFollowRule rule) {
            if (rules.contains(rule)) {
                selected = rule;
            }
        }

        private void addRule(String category) {
            AutoFollowRule rule = new AutoFollowRule();
            rule.category = category(category);
            rule.name = uniqueName(t("gui.modern.autofollow.u208"));
            rules.add(rule);
            selected = rule;
            addCategory(rule.category);
        }

        private void duplicateSelected() {
            if (selected == null) {
                return;
            }
            AutoFollowRule copy = copyRule(selected);
            copy.enabled = false;
            copy.name = uniqueName(t("gui.modern.autofollow.fmt.copy", safe(selected.name)));
            int index = rules.indexOf(selected);
            rules.add(index < 0 ? rules.size() : index + 1, copy);
            selected = copy;
        }

        private void deleteSelected() {
            if (selected == null) {
                return;
            }
            int index = rules.indexOf(selected);
            rules.remove(selected);
            if (rules.isEmpty()) {
                addRule(DEFAULT_CATEGORY);
            } else {
                selected = rules.get(Math.min(Math.max(0, index), rules.size() - 1));
            }
        }

        private boolean isDirty() {
            return !GSON.toJson(original).equals(GSON.toJson(rules))
                    || !categoryOrder.equals(originalCategoryOrder)
                    || antiStuckEnabled != originalAntiStuckEnabled
                    || avoidVinesProactively != originalAvoidVinesProactively
                    || Double.compare(vineAvoidanceDistance, originalVineAvoidanceDistance) != 0
                    || timeoutReloadEnabled != originalTimeoutReloadEnabled
                    || timeoutReloadSeconds != originalTimeoutReloadSeconds;
        }

        private void markCommitted() {
            original = copyRules(rules);
            originalCategoryOrder = new ArrayList<>(categoryOrder);
            originalAntiStuckEnabled = antiStuckEnabled;
            originalAvoidVinesProactively = avoidVinesProactively;
            originalVineAvoidanceDistance = vineAvoidanceDistance;
            originalTimeoutReloadEnabled = timeoutReloadEnabled;
            originalTimeoutReloadSeconds = timeoutReloadSeconds;
        }

        private void discard() {
            String selectedName = selected == null ? "" : safe(selected.name);
            rules.clear();
            rules.addAll(copyRules(original));
            categoryOrder.clear();
            categoryOrder.addAll(originalCategoryOrder);
            antiStuckEnabled = originalAntiStuckEnabled;
            avoidVinesProactively = originalAvoidVinesProactively;
            vineAvoidanceDistance = originalVineAvoidanceDistance;
            timeoutReloadEnabled = originalTimeoutReloadEnabled;
            timeoutReloadSeconds = originalTimeoutReloadSeconds;
            selected = findByName(rules, selectedName);
            if (selected == null && !rules.isEmpty()) {
                selected = rules.get(0);
            }
        }

        private AutoFollowRule chooseActive(String previousActiveName) {
            if (selected != null && selected.enabled) {
                return selected;
            }
            AutoFollowRule previous = findByName(rules, previousActiveName);
            if (previous != null && previous.enabled) {
                return previous;
            }
            for (AutoFollowRule rule : rules) {
                if (rule != null && rule.enabled) {
                    return rule;
                }
            }
            return null;
        }

        private void keepOnlyEnabled(AutoFollowRule active) {
            for (AutoFollowRule rule : rules) {
                if (rule != active) {
                    rule.enabled = false;
                }
            }
            if (active != null) {
                active.enabled = true;
            }
        }

        private void normalizeRules() {
            for (AutoFollowRule rule : rules) {
                if (rule != null) {
                    rule.category = category(rule.category);
                    rule.updateBounds();
                    rule.ensureReturnPoints();
                }
            }
            ensureCategories();
        }

    public boolean renameCategory(String oldValue, String newValue) {
        String next = newValue.trim();
        if (next.isEmpty()) return false;
        for (String existing : categoryOrder) if (existing.equalsIgnoreCase(next)) return false;
        int index = -1;
        for (int i = 0; i < categoryOrder.size(); i++) if (categoryOrder.get(i).equalsIgnoreCase(oldValue)) index = i;
        if (index < 0) return false;
        categoryOrder.set(index, next);
        for (AutoFollowRule rule : rules) if (category(rule.category).equalsIgnoreCase(oldValue)) rule.category = next;
        return true;
    }
    public void deleteCategory(String value) {
        if ("默认".equals(value)) return;
        categoryOrder.removeIf(existing -> existing.equalsIgnoreCase(value));
        for (AutoFollowRule rule : rules) if (category(rule.category).equalsIgnoreCase(value)) rule.category = "默认";
        addCategory("默认");
    }
    public boolean canMoveRule(int delta) {
        int index = rules.indexOf(selected), target = index + delta;
        return index >= 0 && target >= 0 && target < rules.size();
    }
    public void moveRule(int delta) { if (canMoveRule(delta)) Collections.swap(rules, rules.indexOf(selected), rules.indexOf(selected) + delta); }

        private void addCategory(String value) {
            String normalized = category(value);
            for (String existing : categoryOrder) {
                if (existing.equalsIgnoreCase(normalized)) {
                    return;
                }
            }
            categoryOrder.add(normalized);
        }

        private void ensureCategories() {
            for (AutoFollowRule rule : rules) {
                if (rule != null) {
                    addCategory(rule.category);
                }
            }
            if (categoryOrder.isEmpty()) {
                categoryOrder.add(DEFAULT_CATEGORY);
            }
        }

        private String canonicalCategory(String value) {
            String normalized = category(value);
            for (String existing : categoryOrder) {
                if (existing.equalsIgnoreCase(normalized)) {
                    return existing;
                }
            }
            return normalized;
        }

        private String uniqueName(String base) {
            String candidate = safe(base).trim();
            if (candidate.isEmpty()) {
                candidate = "gui.modern.autofollow.u209";
            }
            String root = candidate;
            int suffix = 2;
            while (findByName(rules, candidate) != null) {
                candidate = root + " " + suffix++;
            }
            return candidate;
        }

        private static final class Group {
            private final String name;
            private final List<AutoFollowRule> rules;

            private Group(String name, List<AutoFollowRule> rules) {
                this.name = name;
                this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            }

            private String name() {
                return name;
            }

            private List<AutoFollowRule> rules() {
                return rules;
            }
        }
    }
}
