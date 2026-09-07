package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernNavigationActions;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.handlers.AutoEscapeHandler;
import com.zszl.zszlScriptMod.system.AutoEscapeRule;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Native list-detail editor for automatic escape rules. */
public final class ModernAutoEscapeWorkbenchTab implements ModernSettingsTab {
    private String navigationCategory;
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("autoescape");

    private ModernMainLayout.Rect masterBounds;

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 30;
    private static final int GROUP_HEIGHT = ModernTreeGuide.GROUP_HEIGHT;
    private static final int RULE_HEIGHT = ModernTreeGuide.ITEM_HEIGHT;
    private static final int SECTION_NAV_WIDTH = 112;
    private static final int SECTION_ROW_HEIGHT = 27;
    private static final String[] EDITOR_SECTIONS = { "gui.modern.escape.u001", "gui.modern.escape.u002", "gui.modern.escape.u003", "gui.modern.escape.u004",
            "gui.modern.escape.u005", "gui.modern.escape.u006", "gui.modern.escape.u007" };
    private static final String[][] ENTITY_TYPES = {
            {"player", "gui.modern.escape.u008"}, {"monster", "gui.modern.escape.u009"}, {"neutral", "gui.modern.escape.u010"}, {"animal", "gui.modern.escape.u011"},
            {"water", "gui.modern.escape.u012"}, {"ambient", "gui.modern.escape.u013"}, {"villager", "gui.modern.escape.u014"}, {"golem", "gui.modern.escape.u015"},
            {"tameable", "gui.modern.escape.u016"}, {"boss", "gui.modern.escape.u017"}, {"living", "gui.modern.escape.u018"}, {"any", "gui.modern.escape.u019"}
    };
    private final AutoEscapeWorkbenchState state = new AutoEscapeWorkbenchState(
            AutoEscapeHandler.getRulesSnapshot(), AutoEscapeHandler.getCategoriesSnapshot(),
            AutoEscapeHandler.isMasterEnabled());
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final List<SectionHit> sectionHits = new ArrayList<>();
    private ModernSettingsTab editor;
    private AutoEscapeRule editorRule;
    private GuiTextField searchField;
    private AutoEscapeSequencePicker sequencePicker;
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
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private int navigationScroll;
    private int navigationMaxScroll;
    private double navigationRatio = 0.28D;
    private boolean layoutPreferencesLoaded;
    private int selectedSection;
    private int sectionScroll;
    private int sectionMaxScroll;
    private final ModernHoverScrollbar sectionScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar navigationScrollbar = new ModernHoverScrollbar();
    private boolean restoreSectionAfterRebuild;
    private boolean draggingNavigationDivider;
    private AutoEscapeRule pendingDelete;
    private String status = "";

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("rules.auto_escape.navigation", navigationRatio);
            layoutPreferencesLoaded = true;
        }
        if (searchField == null) {
            searchField = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
            searchField.setEnableBackgroundDrawing(false);
            searchField.setMaxStringLength(80);
        }
        if (sequencePicker == null) sequencePicker = new AutoEscapeSequencePicker(this::selectSequence);
        sequencePicker.ensureInitialized(fontRenderer);
        ensureEditor();
        editor.ensureInitialized(fontRenderer);
    }

    @Override public void updateScreen() {
        if (searchField != null) searchField.updateCursorCounter();
        if (sequencePicker != null) sequencePicker.updateScreen();
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
        int railWidth = split.firstWidth;
        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bounds.y + TOP_INSET,
                railWidth, Math.max(1, bounds.height - TOP_INSET - FOOTER_HEIGHT - 6));
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 0,
                navigationBounds.y, navigationBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationDividerBounds.right(), navigationBounds.y,
                Math.max(1, bounds.right() - navigationDividerBounds.right() - 8), navigationBounds.height);
        int sectionNavWidth = Math.min(SECTION_NAV_WIDTH, Math.max(78, editorBounds.width - 180));
        sectionNavBounds = new ModernMainLayout.Rect(editorBounds.x + 8, editorBounds.y + 8, sectionNavWidth,
                Math.max(1, editorBounds.height - 16));
        formBounds = new ModernMainLayout.Rect(sectionNavBounds.right() + 8, editorBounds.y,
                Math.max(1, editorBounds.right() - sectionNavBounds.right() - 8), editorBounds.height);
        drawNavigation(fontRenderer, mouseX, mouseY);
        drawSectionNavigation(fontRenderer, mouseX, mouseY);
        editor.draw(fontRenderer, formBounds, mouseX, mouseY);
        if (restoreSectionAfterRebuild && editor instanceof ModernFormSettingsTab) {
            ((ModernFormSettingsTab<?>) editor).scrollToSection(selectedSection);
            restoreSectionAfterRebuild = false;
        }
        if (sequencePicker != null) sequencePicker.draw(fontRenderer, formBounds, "gui.modern.escape.u020", mouseX, mouseY);
        drawFooter(fontRenderer, mouseX, mouseY);
        navigationActions.drawOverlay(mouseX, mouseY);
    }


    private void drawSectionNavigation(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(sectionNavBounds.x, sectionNavBounds.y, sectionNavBounds.width,
                sectionNavBounds.height, 5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, "gui.modern.escape.u024", sectionNavBounds.x + 10, sectionNavBounds.y + 11,
                ModernUiRenderer.SUBTLE_TEXT, sectionNavBounds.width - 20);
        sectionHits.clear();
        int contentTop = sectionNavBounds.y + 33;
        int contentHeight = Math.max(1, sectionNavBounds.height - 41);
        int rowHeight = Math.min(SECTION_ROW_HEIGHT,
                Math.max(20, (contentHeight - (EDITOR_SECTIONS.length - 1) * 3) / EDITOR_SECTIONS.length));
        int contentTotal = EDITOR_SECTIONS.length * rowHeight + (EDITOR_SECTIONS.length - 1) * 3;
        sectionMaxScroll = Math.max(0, contentTotal - contentHeight);
        sectionScroll = clamp(sectionScroll, 0, sectionMaxScroll);
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(sectionNavBounds.x + 3, contentTop,
                sectionNavBounds.width - 6, contentHeight);
        ModernUiRenderer.beginClip(clip);
        int y = contentTop - sectionScroll;
        for (int i = 0; i < EDITOR_SECTIONS.length; i++) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(sectionNavBounds.x + 5, y,
                    ModernHoverScrollbar.contentWidth(sectionNavBounds.width - 8), rowHeight);
            boolean selected = i == selectedSection;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, EDITOR_SECTIONS[i], row.x + 9,
                    row.y + Math.max(5, (row.height - font.FONT_HEIGHT) / 2),
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 18);
            if (intersectsVertically(row, clip)) sectionHits.add(new SectionHit(i, row));
            y += rowHeight + 3;
        }
        ModernUiRenderer.endClip();
        if (sectionMaxScroll > 0) {
            sectionScrollbar.draw(clip, sectionScroll, sectionMaxScroll, contentHeight, contentTotal, mouseX, mouseY,
                    value -> sectionScroll = value);
        } else {
            sectionScrollbar.idle();
        }
    }

    private void configureNavigationActions() {
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_add", "", value -> { if (syncEditorDraft()) { state.addCategory(value); navigationCategory = value; } }));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> true, () -> navigationActions.prompt("gui.modern.nav.category_rename", selectedCategory(), value -> { if (syncEditorDraft() && state.renameCategory(selectedCategory(), value)) { navigationCategory = value; rebuildEditor(); } }));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> !"默认".equals(selectedCategory()), () -> navigationActions.confirm("gui.modern.nav.category_delete", () -> { if (syncEditorDraft()) { state.deleteCategory(selectedCategory()); navigationCategory = "默认"; rebuildEditor(); } }));
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.move", state.selected().category, value -> { state.selected().category = value; state.addCategory(value); navigationCategory = value; rebuildEditor(); }); });
        navigationActions.action("up", "gui.modern.nav.up", false, false, () -> state.canMoveRule(-1), () -> { if (syncEditorDraft()) state.moveRule(-1); });
        navigationActions.action("down", "gui.modern.nav.down", false, false, () -> state.canMoveRule(1), () -> { if (syncEditorDraft()) state.moveRule(1); });

        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::navigationAdd);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> state.selected() != null, this::navigationCopy);
        navigationActions.action("delete", state.selected() != null && pendingDelete == state.selected() ? "gui.modern.nav.confirm_delete" : "gui.modern.nav.delete", true, true, () -> state.selected() != null, this::navigationDelete);
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) navigationActions.prompt("gui.modern.nav.rename", state.selected().name, value -> { state.selected().name = value; rebuildEditor(); }); });
        navigationActions.action("toggle", "gui.modern.nav.toggle", false, false, () -> state.selected() != null, () -> { if (syncEditorDraft() && state.selected() != null) { state.selected().enabled = !state.selected().enabled; rebuildEditor(); } });
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedGroups.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> { for (AutoEscapeWorkbenchState.Group group : state.groups()) collapsedGroups.add(group.name()); });
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

    private boolean navigationAdd() { if (syncEditorDraft()) { pendingDelete = null; state.addRule(selectedCategory()); rebuildEditor(); } return true; }

    private boolean navigationCopy() { if (syncEditorDraft()) { pendingDelete = null; state.duplicateSelected(); rebuildEditor(); } return true; }

    private boolean navigationDelete() {
            if (!syncEditorDraft()) return true;
            if (pendingDelete != state.selected()) {
                pendingDelete = state.selected(); status = "gui.modern.escape.u033"; return true;
            }
            pendingDelete = null; state.deleteSelected(); rebuildEditor(); status = "gui.modern.escape.u034"; return true;
        }

    private void drawNavigation(FontRenderer font, int mouseX, int mouseY) {
        configureNavigationActions();
        navigationActions.begin(font, navigationBounds);
        ModernUiRenderer.drawSubtlePanel(navigationBounds.x, navigationBounds.y, navigationBounds.width,
                navigationBounds.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        searchBounds = new ModernMainLayout.Rect(navigationBounds.x + 8, navigationBounds.y + 30,
                navigationBounds.width - 16, 20);
        searchField.x = searchBounds.x + 22; searchField.y = searchBounds.y + 5;
        searchField.width = searchBounds.width - 28; searchField.height = 12;
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, searchField.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 6, searchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (searchField.getText().isEmpty() && !searchField.isFocused())
            ModernUiRenderer.drawText(font, "gui.modern.escape.u025", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, searchField.width);
        ModernUiRenderer.drawTextField(searchField);
        ruleHits.clear(); groupHits.clear();
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(navigationBounds.x + 5, searchBounds.bottom() + 6,
                navigationBounds.width - 10, Math.max(1, navigationActions.contentBottom() - searchBounds.bottom() - 6));
        ModernUiRenderer.beginClip(clip);
        int y = clip.y - navigationScroll;
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (AutoEscapeWorkbenchState.Group group : state.groups()) {
            List<AutoEscapeRule> visible = matching(group, query);
            if (!query.isEmpty() && visible.isEmpty() && !group.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            ModernMainLayout.Rect groupRect = ModernTreeGuide.groupRow(clip, y);
            boolean collapsed = collapsedGroups.contains(group.name()) && query.isEmpty();
            drawGroup(font, groupRect, group.name(), visible.size(), collapsed, mouseX, mouseY);
            if (intersectsVertically(groupRect, clip)) groupHits.add(new GroupHit(group.name(), groupRect));
            y = ModernTreeGuide.nextY(y, GROUP_HEIGHT);
            if (!collapsed) for (AutoEscapeRule rule : visible) {
                ModernMainLayout.Rect row = ModernTreeGuide.row(clip.x,
                            com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar.contentWidth(clip.width), y, RULE_HEIGHT, 1);
                ModernTreeGuide.drawChild(clip.x, 0, groupRect, row);
                drawRule(font, row, rule, mouseX, mouseY);
                if (intersectsVertically(row, clip)) ruleHits.add(new RuleHit(rule, row));
                y = ModernTreeGuide.nextY(y, RULE_HEIGHT);
            }
        }
        ModernUiRenderer.endClip();
        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);
        navigationMaxScroll = Math.max(0, y + navigationScroll - clip.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        if (navigationMaxScroll > 0) {
            navigationScrollbar.draw(clip, navigationScroll, navigationMaxScroll, clip.height,
                    clip.height + navigationMaxScroll, mouseX, mouseY, value -> navigationScroll = value);
        } else {
            navigationScrollbar.idle();
        }
        navigationActions.draw(mouseX, mouseY);
    }

    private void drawFooter(FontRenderer font, int mouseX, int mouseY) {
        int y = bounds.bottom() - 25;
        masterBounds = new ModernMainLayout.Rect(bounds.x + 8, y,
                Math.max(1, Math.min(130, navigationBounds.width - 8)), 20);
        drawToggle(font, masterBounds, "gui.modern.escape.u023", state.masterEnabled(), mouseX, mouseY);
        int saveWidth = Math.max(70, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(
                net.minecraft.client.Minecraft.getMinecraft().fontRenderer, "保存修改", 24));
        saveBounds = new ModernMainLayout.Rect(editorBounds.right() - saveWidth - 8, y, saveWidth, 20);
        revertBounds = new ModernMainLayout.Rect(saveBounds.x - 76, y, 70, 20);
        drawButton(font, revertBounds, "gui.modern.escape.u030", false, mouseX, mouseY);
        drawButton(font, saveBounds, isDirty() ? "gui.modern.escape.u031" : "gui.modern.escape.u032", true, mouseX, mouseY);
        String message = status.isEmpty()
                ? ModernFormI18n.tr(isDirty() ? "gui.modern.wb.fmt.rules_dirty" : "gui.modern.wb.fmt.rules_synced",
                        String.valueOf(state.rules().size()))
                : status;
        ModernUiRenderer.drawText(font, message, editorBounds.x + 8, y + 6,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, revertBounds.x - editorBounds.x - 16));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) return false;
        if (sequencePicker != null && sequencePicker.isOpen()) return sequencePicker.mouseClicked(mouseX, mouseY);
        if (navigationActions.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1 && navigationContext(mouseX, mouseY)) return true;
        if (button != 0) return true;

        if (navigationScrollbar.beginDrag(mouseX, mouseY) || sectionScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }
        if (masterBounds != null && masterBounds.contains(mouseX, mouseY)) { state.setMasterEnabled(!state.masterEnabled()); return true; }
        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            pendingDelete = null;
            searchField.setFocused(true); searchField.mouseClicked(mouseX, mouseY, button); return true;
        }
        searchField.setFocused(false);
        for (GroupHit hit : groupHits) if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                navigationCategory = hit.name;
            if (!collapsedGroups.add(hit.name)) collapsedGroups.remove(hit.name); return true;
        }
        for (RuleHit hit : ruleHits) if (navigationActions.inTree(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
            if (!syncEditorDraft()) return true;
            pendingDelete = null;
            navigationCategory = null; state.select(hit.rule); rebuildEditor(); status = ModernFormI18n.tr("gui.modern.wb.fmt.selected", hit.rule.name); return true;
        }
        if (contains(addBounds, mouseX, mouseY)) { return navigationAdd(); }
        if (contains(duplicateBounds, mouseX, mouseY)) { return navigationCopy(); }
        if (contains(deleteBounds, mouseX, mouseY)) { return navigationDelete(); }
        if (contains(saveBounds, mouseX, mouseY)) { save(); return true; }
        if (contains(revertBounds, mouseX, mouseY)) { discardDraft(); status = "gui.modern.escape.u035"; return true; }
        if (sectionNavBounds != null && sectionNavBounds.contains(mouseX, mouseY)) {
            for (SectionHit hit : sectionHits) if (hit.bounds.contains(mouseX, mouseY)) {
                selectedSection = hit.index;
                if (editor instanceof ModernFormSettingsTab) {
                    ((ModernFormSettingsTab<?>) editor).scrollToSection(hit.index);
                }
                break;
            }
            return true;
        }
        return formBounds != null && formBounds.contains(mouseX, mouseY)
                ? editor.mouseClicked(mouseX, mouseY, button) : true;
    }

    @Override public boolean keyTyped(char c, int key) {
        if (navigationActions.keyTyped(c, key)) return true;
        if (sequencePicker != null && sequencePicker.isOpen()) return sequencePicker.keyTyped(c, key);
        if (searchField != null && searchField.textboxKeyTyped(c, key)) { navigationScroll = 0; return true; }
        if (key == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchField.setFocused(true); return true;
        }
        return editor != null && editor.keyTyped(c, key);
    }

    @Override public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && navigationScrollbar.isDragging()) {
            navigationScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && sectionScrollbar.isDragging()) {
            sectionScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (draggingNavigationDivider && clickedMouseButton == 0) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, 150, 260, 120, 180);
            navigationRatio = split.ratio;
            return true;
        }
        if (sequencePicker != null && sequencePicker.isOpen()) return true;
        return editor != null && editor.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (navigationScrollbar.isDragging() || sectionScrollbar.isDragging())) {
            navigationScrollbar.endDrag();
            sectionScrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingNavigationDivider) {
            MainUiLayoutManager.setModernSplitRatio("rules.auto_escape.navigation", navigationRatio);
            draggingNavigationDivider = false;
            return true;
        }
        return editor != null && editor.mouseReleased(mouseX, mouseY, state);
    }

    @Override public boolean handleMouseWheel(int wheel) { return false; }
    @Override public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (navigationActions.wheel(wheel)) return true;
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll); return true;
        }
        if (sectionNavBounds != null && sectionNavBounds.contains(mouseX, mouseY)) {
            sectionScroll = clamp(sectionScroll + (wheel > 0 ? -24 : 24), 0, sectionMaxScroll); return true;
        }
        if (sequencePicker != null && sequencePicker.isOpen()) return sequencePicker.handleMouseWheel(wheel, mouseX, mouseY);
        return editor != null && editor.handleMouseWheel(wheel, mouseX, mouseY);
    }
    @Override public boolean handleEscape() {
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (sequencePicker != null && sequencePicker.isOpen()) return sequencePicker.handleEscape();
        if (draggingNavigationDivider) {
            draggingNavigationDivider = false;
            return true;
        }
        return editor != null && editor.handleEscape();
    }
    @Override public boolean isTextInputFocused() {
        if (navigationActions.isOpen()) return true; return searchField != null && searchField.isFocused() || sequencePicker != null && sequencePicker.isTextInputFocused() || editor != null && editor.isTextInputFocused(); }
    @Override public boolean containsContent(int x, int y) { return bounds != null && bounds.contains(x, y); }
    @Override public String getHoveredTooltip(int x, int y) { return sequencePicker != null && sequencePicker.isOpen() ? "" : editor == null ? "" : editor.getHoveredTooltip(x, y); }
    @Override public boolean isDirty() { return state.isDirty() || editor != null && editor.isDirty(); }

    @Override public void save() {
        if (!syncEditorDraft()) { status = "gui.modern.escape.u036"; return; }
        if (editor != null) editor.save();
        AutoEscapeHandler.replaceAllRules(copyRules(state.rules()));
        for (String category : AutoEscapeHandler.getCategoriesSnapshot()) {
            if (!state.categories().contains(category)) AutoEscapeHandler.deleteCategory(category);
        }
        AutoEscapeHandler.replaceCategoryOrder(state.categories());
        if (AutoEscapeHandler.isMasterEnabled() != state.masterEnabled()) AutoEscapeHandler.setMasterEnabled(state.masterEnabled());
        state.markCommitted(); status = "gui.modern.escape.u037";
    }

    @Override public void discardDraft() {
        navigationActions.close();
        navigationCategory = null;
        pendingDelete = null; state.discard(); rebuildEditor();
        if (searchField != null) searchField.setFocused(false);
    }

    private void ensureEditor() { if (editor == null || editorRule != state.selected()) rebuildEditor(); }
    private void rebuildEditor() { editorRule = state.selected(); editor = editorRule == null ? emptyEditor() : buildEditor(editorRule); }
    private boolean syncEditorDraft() {
        return !(editor instanceof ModernFormSettingsTab)
                || ((ModernFormSettingsTab<?>) editor).tryApplyDraftValues();
    }

    private ModernSettingsTab buildEditor(final AutoEscapeRule rule) {
        ModernFormSettingsTab.Builder<AutoEscapeRule> builder = ModernFormSettingsTab.builder(
                safe(rule.name), "gui.modern.escape.u038", "gui.modern.escape.u039", adapter(rule))
                .footerVisible(false)
                .section("gui.modern.escape.u001", "gui.modern.escape.u040")
                .text("gui.modern.escape.u041", "gui.modern.escape.u042", text(() -> rule.name, value -> rule.name = value), "gui.modern.escape.u041", 96)
                .text("gui.modern.escape.u043", "gui.modern.escape.u044", text(() -> rule.category, value -> rule.category = value), "gui.modern.escape.u045", 64)
                .toggle("gui.modern.escape.u046", "gui.modern.escape.u047", bool(() -> rule.enabled, value -> rule.enabled = value));
        for (final String[] type : ENTITY_TYPES) builder.toggle(type[1], "gui.modern.escape.u048",
                bool(() -> rule.entityTypes.contains(type[0]), value -> toggleToken(rule.entityTypes, type[0], value)));
        builder.decimal("gui.modern.escape.u049", "gui.modern.escape.u050", number(() -> (float) rule.detectionRange, value -> rule.detectionRange = value), 1, 128)
                .section("gui.modern.escape.u002", "gui.modern.escape.u051")
                .toggle("gui.modern.escape.u052", "gui.modern.escape.u053", bool(() -> rule.enablePlayerGameModeFilter, value -> rule.enablePlayerGameModeFilter = value))
                .choice("gui.modern.escape.u054", "gui.modern.escape.u055", choice(() -> rule.playerGameModeFilter, value -> rule.playerGameModeFilter = value),
                        Arrays.asList(ModernFormSettingsTab.option("all", "gui.modern.escape.u056"), ModernFormSettingsTab.option("survival", "gui.modern.escape.u057"),
                                ModernFormSettingsTab.option("creative", "gui.modern.escape.u058"), ModernFormSettingsTab.option("spectator", "gui.modern.escape.u059"),
                                ModernFormSettingsTab.option("unknown", "gui.modern.escape.u060")))
                .section("gui.modern.escape.u003", "gui.modern.escape.u061")
                .toggle("gui.modern.escape.u062", "gui.modern.escape.u063", bool(() -> rule.enableNameWhitelist, value -> rule.enableNameWhitelist = value))
                .text("gui.modern.escape.u064", "gui.modern.escape.u065", list(rule.nameWhitelist), "gui.modern.escape.u066", 512)
                .section("gui.modern.escape.u004", "gui.modern.escape.u067")
                .toggle("gui.modern.escape.u068", "gui.modern.escape.u069", bool(() -> rule.enableNameBlacklist, value -> rule.enableNameBlacklist = value))
                .text("gui.modern.escape.u070", "gui.modern.escape.u065", list(rule.nameBlacklist), "gui.modern.escape.u066", 512)
                .section("gui.modern.escape.u005", "gui.modern.escape.u071")
                .toggle("gui.modern.escape.u072", "gui.modern.escape.u073", bool(() -> rule.enableAreaBlacklist, value -> rule.enableAreaBlacklist = value))
                .text("gui.modern.escape.u074", "gui.modern.escape.u075", areas(rule), "gui.modern.escape.u076", 1024)
                .section("gui.modern.escape.u006", "gui.modern.escape.u077")
                .action("gui.modern.escape.u006", "gui.modern.escape.u078",
                        sequenceButtonLabel(rule.escapeSequenceName), ModernFormSettingsTab.ActionStyle.SECONDARY,
                        tab -> openSequenceSelector())
                .section("gui.modern.escape.u007", "gui.modern.escape.u079")
                .toggle("gui.modern.escape.u080", "gui.modern.escape.u081",
                        bool(() -> rule.restartEnabled, value -> rule.restartEnabled = value))
                .integer("gui.modern.escape.u082", "gui.modern.escape.u083", integer(() -> rule.restartDelaySeconds, value -> rule.restartDelaySeconds = value), 0, 3600)
                .toggle("gui.modern.escape.u084", "gui.modern.escape.u085",
                        bool(() -> rule.ignoreTargetsUntilRestartComplete, value -> rule.ignoreTargetsUntilRestartComplete = value));
        return builder.build();
    }

    private ModernFormSettingsTab.StateAdapter<AutoEscapeRule> adapter(final AutoEscapeRule rule) {
        return new ModernFormSettingsTab.StateAdapter<AutoEscapeRule>() {
            public void load() { }
            public AutoEscapeRule capture() { return rule.copy(); }
            public AutoEscapeRule copy(AutoEscapeRule value) { return value == null ? null : value.copy(); }
            public void restore(AutoEscapeRule value) { if (value != null) copyRule(value, rule); }
            public void save() {
                rule.restartSequenceName = rule.escapeSequenceName;
                rule.normalize();
            }
            public void restoreDefaults() { copyRule(new AutoEscapeRule(), rule); }
            public AutoEscapeRule createDefaults() { return new AutoEscapeRule(); }
        };
    }

    private ModernSettingsTab emptyEditor() { return ModernFormSettingsTab.builder("gui.modern.escape.u086", "gui.modern.escape.u087", "").build(); }

    private void openSequenceSelector() {
        sequencePicker.open();
    }

    private static String sequenceButtonLabel(String value) {
        return safe(value).trim().isEmpty() ? "gui.modern.escape.u088" : safe(value).trim();
    }

    private void selectSequence(String name) {
        if (editorRule == null) return;
        String selected = name == null ? "" : name;
        editorRule.escapeSequenceName = selected;
        editorRule.restartSequenceName = selected;
        restoreSectionAfterRebuild = true;
        rebuildEditor();
        if (editor instanceof ModernFormSettingsTab) {
            ((ModernFormSettingsTab<?>) editor).refreshValues();
            ((ModernFormSettingsTab<?>) editor).scrollToSection(selectedSection);
        }
    }

    private List<AutoEscapeRule> matching(AutoEscapeWorkbenchState.Group group, String query) {
        if (query.isEmpty() || group.name().toLowerCase(Locale.ROOT).contains(query)) return group.rules();
        List<AutoEscapeRule> result = new ArrayList<>();
        for (AutoEscapeRule rule : group.rules()) if (safe(rule.name).toLowerCase(Locale.ROOT).contains(query)) result.add(rule);
        return result;
    }
    private String selectedCategory() {
        if (navigationCategory != null) return navigationCategory; return state.selected() == null ? "gui.modern.escape.u045" : state.selected().category; }
    private void drawGroup(FontRenderer f, ModernMainLayout.Rect r, String name, int count, boolean collapsed, int mx, int my) {
        boolean hover = r.contains(mx, my); ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4,
                hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(r.x + 8, r.y + 7, collapsed, ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawText(f, name, r.x + 20, r.y + 7, ModernUiRenderer.TEXT, r.width - 54);
        ModernUiRenderer.drawText(f, String.valueOf(count), r.right() - 23, r.y + 7, ModernUiRenderer.MUTED_TEXT, 18);
    }
    private void drawRule(FontRenderer f, ModernMainLayout.Rect r, AutoEscapeRule rule, int mx, int my) {
        boolean selected = rule == state.selected(), hover = r.contains(mx, my);
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(r.x + 7, r.y + 9, rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawText(f, safe(rule.name), r.x + 20, r.y + 5, rule.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, r.width - 28);
        ModernUiRenderer.drawText(f, ModernFormI18n.tr("gui.modern.escape.fmt.range",
                String.valueOf(rule.detectionRange), safe(rule.escapeSequenceName)), r.x + 20, r.y + 16,
                ModernUiRenderer.MUTED_TEXT, r.width - 28);
    }
    private void drawToggle(FontRenderer f, ModernMainLayout.Rect r, String label, boolean on, int mx, int my) {
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 5, ModernUiRenderer.SURFACE, r.contains(mx, my) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(f, label, r.x + 7, r.y + 6, ModernUiRenderer.TEXT, r.width - 47);
        ModernUiRenderer.drawToggle(r.right() - 37, r.y + 4, 28, 12, on, r.contains(mx, my));
    }
    private void drawButton(FontRenderer f, ModernMainLayout.Rect r, String label, boolean primary, int mx, int my) {
        boolean hover = r.contains(mx, my); int fill = primary ? (hover ? 0xFFFF86A7 : ModernUiRenderer.ACCENT) : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4, fill, primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(f, label, r.x + 6, r.y + 6, primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, r.width - 12);
    }

    private interface StringGet { String get(); } private interface StringSet { void set(String v); }
    private interface BoolGet { boolean get(); } private interface BoolSet { void set(boolean v); }
    private interface FloatGet { float get(); } private interface FloatSet { void set(float v); }
    private interface IntGet { int get(); } private interface IntSet { void set(int v); }
    private static ModernFormSettingsTab.TextValue text(final StringGet g, final StringSet s) { return new ModernFormSettingsTab.TextValue() { public String get(){return safe(g.get());} public void set(String v){s.set(v);} }; }
    private static ModernFormSettingsTab.BooleanValue bool(final BoolGet g, final BoolSet s) { return new ModernFormSettingsTab.BooleanValue() { public boolean get(){return g.get();} public void set(boolean v){s.set(v);} }; }
    private static ModernFormSettingsTab.FloatValue number(final FloatGet g, final FloatSet s) { return new ModernFormSettingsTab.FloatValue() { public float get(){return g.get();} public void set(float v){s.set(v);} }; }
    private static ModernFormSettingsTab.IntValue integer(final IntGet g, final IntSet s) { return new ModernFormSettingsTab.IntValue() { public int get(){return g.get();} public void set(int v){s.set(v);} }; }
    private static ModernFormSettingsTab.ChoiceValue<String> choice(final StringGet g, final StringSet s) { return new ModernFormSettingsTab.ChoiceValue<String>() { public String get(){return g.get();} public void set(String v){s.set(v);} }; }
    private static ModernFormSettingsTab.TextValue list(final List<String> values) { return text(() -> join(values), value -> { values.clear(); values.addAll(split(value, ",")); }); }
    private static ModernFormSettingsTab.TextValue areas(final AutoEscapeRule rule) { return text(() -> joinAreas(rule.areaBlacklist), value -> rule.areaBlacklist = parseAreas(value)); }
    private static void toggleToken(List<String> values, String token, boolean enabled) { if (enabled) { if (!values.contains(token)) values.add(token); } else values.remove(token); }
    private static String join(List<String> values) { return values == null ? "" : String.join(", ", values); }
    private static List<String> split(String value, String separator) { List<String> out = new ArrayList<>(); for (String item : safe(value).split(separator)) if (!item.trim().isEmpty()) out.add(item.trim()); return out; }
    private static String joinAreas(List<AutoEscapeRule.AreaBlacklistEntry> entries) { if (entries == null) return ""; List<String> out = new ArrayList<>(); for (AutoEscapeRule.AreaBlacklistEntry e : entries) if (e != null) out.add(e.areaKey + "|" + e.chunkRadius); return String.join("; ", out); }
    private static List<AutoEscapeRule.AreaBlacklistEntry> parseAreas(String value) { List<AutoEscapeRule.AreaBlacklistEntry> out = new ArrayList<>(); for (String raw : split(value, ";")) { String[] p = raw.split("\\|", 2); int radius = 0; if (p.length > 1) try { radius = Integer.parseInt(p[1].trim()); } catch (NumberFormatException ignored) { } out.add(new AutoEscapeRule.AreaBlacklistEntry(p[0], radius)); } return out; }
    private static List<AutoEscapeRule> copyRules(List<AutoEscapeRule> rules) { List<AutoEscapeRule> out = new ArrayList<>(); for (AutoEscapeRule r : rules) out.add(r.copy()); return out; }
    private static void copyRule(AutoEscapeRule source, AutoEscapeRule target) {
        AutoEscapeRule copy = source.copy();
        target.name = copy.name; target.category = copy.category; target.enabled = copy.enabled;
        target.entityTypes = new ArrayList<>(copy.entityTypes); target.detectionRange = copy.detectionRange;
        target.enableNameWhitelist = copy.enableNameWhitelist; target.nameWhitelist = new ArrayList<>(copy.nameWhitelist);
        target.enableNameBlacklist = copy.enableNameBlacklist; target.nameBlacklist = new ArrayList<>(copy.nameBlacklist);
        target.enableAreaBlacklist = copy.enableAreaBlacklist; target.areaBlacklist = new ArrayList<>();
        for (AutoEscapeRule.AreaBlacklistEntry entry : copy.areaBlacklist) target.areaBlacklist.add(entry.copy());
        target.enablePlayerGameModeFilter = copy.enablePlayerGameModeFilter;
        target.playerGameModeFilter = copy.playerGameModeFilter; target.escapeSequenceName = copy.escapeSequenceName;
        target.restartEnabled = copy.restartEnabled; target.restartDelaySeconds = copy.restartDelaySeconds;
        target.restartSequenceName = copy.restartSequenceName;
        target.ignoreTargetsUntilRestartComplete = copy.ignoreTargetsUntilRestartComplete;
    }
    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect r, int i) { return new ModernMainLayout.Rect(r.x + i, r.y + i, Math.max(1, r.width - i * 2), Math.max(1, r.height - i * 2)); }
    private static boolean contains(ModernMainLayout.Rect r, int x, int y) { return r != null && r.contains(x, y); }
    private static boolean intersectsVertically(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.bottom() > second.y && first.y < second.bottom();
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static final class RuleHit { final AutoEscapeRule rule; final ModernMainLayout.Rect bounds; RuleHit(AutoEscapeRule r, ModernMainLayout.Rect b){rule=r;bounds=b;} }
    private static final class GroupHit { final String name; final ModernMainLayout.Rect bounds; GroupHit(String n, ModernMainLayout.Rect b){name=n;bounds=b;} }
    private static final class SectionHit { final int index; final ModernMainLayout.Rect bounds; SectionHit(int i, ModernMainLayout.Rect b){index=i;bounds=b;} }
}
