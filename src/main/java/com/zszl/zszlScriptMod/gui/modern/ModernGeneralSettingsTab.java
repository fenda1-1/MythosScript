package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager.ThemeProfile;
import com.zszl.zszlScriptMod.utils.ClientTranslationInjector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

/** Compact, cross-cutting preferences page for the modern control center. */
public final class ModernGeneralSettingsTab implements ModernSettingsTab {

    private static final String[] LANGUAGE_CODES = { "zh_cn", "en_us" };
    private static final String[] LANGUAGE_NAMES = { "简体中文", "English" };
    private static final String[] SCALE_VALUES = { "100", "125", "150", "175", "200", "225", "250", "275", "300" };
    private static final String[] SCALE_LABELS = { "100%", "125%", "150%", "175%", "200%", "225%", "250%", "275%", "300%" };

    private final Minecraft minecraft;
    private final java.util.function.Consumer<String> routeRequest;
    private final List<ModernMainLayout.Rect> themeBounds = new ArrayList<>();
    private final List<Integer> themeIndices = new ArrayList<>();
    private final ModernMainLayout.Rect[] themeGroupBounds = new ModernMainLayout.Rect[2];
    private final boolean[] themeGroupCollapsed = new boolean[2];
    private final List<ModernMainLayout.Rect> languageBounds = new ArrayList<>();
    private final List<ModernMainLayout.Rect> scaleBounds = new ArrayList<>();
    private final ModernDropdown languageDropdown = new ModernDropdown(LANGUAGE_CODES, LANGUAGE_NAMES);
    private final ModernDropdown scaleDropdown = new ModernDropdown(SCALE_VALUES, SCALE_LABELS);
    private final ModernHoverScrollbar appearanceScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar behaviorScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar compactScrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect appearanceBounds;
    private ModernMainLayout.Rect behaviorBounds;
    private ModernMainLayout.Rect appearanceClipBounds;
    private ModernMainLayout.Rect behaviorClipBounds;
    private ModernMainLayout.Rect compactContentBounds;
    private ModernMainLayout.Rect dividerBounds;
    private ModernMainLayout.Rect editThemeBounds;
    private ModernMainLayout.Rect autoFocusBounds;
    private ModernMainLayout.Rect autoPauseBounds;
    private double appearanceRatio = 0.30D;
    private boolean draggingDivider;
    private int appearanceScroll;
    private int appearanceMaxScroll;
    private int behaviorScroll;
    private int behaviorMaxScroll;
    private int compactScroll;
    private int compactMaxScroll;
    private int lastMouseX;
    private int lastMouseY;
    private boolean compactMode;
    private boolean layoutPreferencesLoaded;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private String pendingLanguage;
    private long statusUntil;

    public ModernGeneralSettingsTab(ModernScreenContext context) {
        this.minecraft = context == null ? Minecraft.getMinecraft() : context.getMinecraft();
        this.routeRequest = context == null ? null : context.getRouteRequest();
    }

    public static ModernSettingsTab create(ModernScreenContext context) {
        return new ModernGeneralSettingsTab(context);
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            appearanceRatio = MainUiLayoutManager.getModernSplitRatio("general.appearance", appearanceRatio);
            layoutPreferencesLoaded = true;
        }
        ThemeConfigManager.ensureLoaded();
        ThemeConfigManager.ensureDefaultPresets();
    }

    @Override
    public void updateScreen() {
        if (pendingLanguage != null && ClientTranslationInjector.INSTANCE.applyLanguage(pendingLanguage)) {
            GuiInventory.refreshGuiLists();
            pendingLanguage = null;
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        hoveredTooltip = "";
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        panelBounds = safePanel(bounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        int x = panelBounds.x + 16;
        int width = Math.max(1, panelBounds.width - 32);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.title", "通用设置"), x, panelBounds.y + 12, ModernUiRenderer.TEXT,
                Math.max(30, width));
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.subtitle", "控制整个脚本界面的外观、语言与交互体验"), x, panelBounds.y + 27,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(30, width));
        ModernUiRenderer.drawDivider(x, panelBounds.y + 47, width, ModernUiRenderer.BORDER_SUBTLE);

        compactMode = panelBounds.width < 500 || panelBounds.height < 280;
        if (compactMode) {
            drawCompact(fontRenderer, x, panelBounds.y + 58, width, mouseX, mouseY);
            return;
        }
        int bodyY = panelBounds.y + 59;
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 9);
        int gap = 10;
        int splitTotal = Math.max(2, width - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, appearanceRatio,
                178, 250, 122, 160);
        appearanceBounds = new ModernMainLayout.Rect(x, bodyY, split.firstWidth, bodyHeight);
        behaviorBounds = new ModernMainLayout.Rect(appearanceBounds.right() + gap, bodyY, split.secondWidth, bodyHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(appearanceBounds.x, appearanceBounds.width, gap, bodyY,
                bodyHeight);
        drawAppearance(fontRenderer, appearanceBounds, mouseX, mouseY);
        drawBehavior(fontRenderer, behaviorBounds, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
        drawDropdownMenus(fontRenderer, behaviorBounds, mouseX, mouseY);
    }

    private void drawCompact(FontRenderer fontRenderer, int x, int y, int width, int mouseX, int mouseY) {
        themeBounds.clear();
        languageBounds.clear();
        scaleBounds.clear();
        appearanceBounds = null;
        behaviorBounds = null;
        dividerBounds = null;
        appearanceScrollbar.idle();
        behaviorScrollbar.idle();
        int contentHeight = themeContentHeight() + 4 * 31 + 49;
        compactContentBounds = new ModernMainLayout.Rect(x, y, Math.max(1, width),
                Math.max(1, panelBounds.bottom() - y - 8));
        compactMaxScroll = Math.max(0, contentHeight - compactContentBounds.height);
        compactScroll = clamp(compactScroll, 0, compactMaxScroll);
        ModernUiRenderer.beginClip(compactContentBounds);
        int contentY = compactContentBounds.y + 2 - compactScroll;
        int sectionWidth = ModernHoverScrollbar.contentWidth(compactContentBounds.width);
        int appearanceHeight = themeContentHeight();
        ModernUiRenderer.drawSubtlePanel(compactContentBounds.x, contentY, sectionWidth, appearanceHeight, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawCompactAppearance(fontRenderer, new ModernMainLayout.Rect(compactContentBounds.x + 8, contentY + 8,
                Math.max(1, sectionWidth - 16), Math.max(1, appearanceHeight - 16)), mouseX, mouseY);
        int behaviorY = contentY + appearanceHeight + 10;
        int behaviorHeight = Math.max(1, contentHeight - appearanceHeight - 10);
        ModernUiRenderer.drawSubtlePanel(compactContentBounds.x, behaviorY, sectionWidth, behaviorHeight, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawCompactBehavior(fontRenderer, new ModernMainLayout.Rect(compactContentBounds.x + 8, behaviorY + 8,
                Math.max(1, sectionWidth - 16), Math.max(1, behaviorHeight - 16)), mouseX, mouseY);
        ModernUiRenderer.endClip();
        compactScrollbar.draw(compactContentBounds, compactScroll, compactMaxScroll, compactContentBounds.height,
                contentHeight, mouseX, mouseY, value -> compactScroll = value);
        drawDropdownMenus(fontRenderer, compactContentBounds, mouseX, mouseY);
    }

    private void drawAppearance(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        int x = bounds.x + 11;
        int width = Math.max(1, bounds.width - 22);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.appearance", "外观"), x, bounds.y + 10, ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.theme_profile", "主题方案"), x, bounds.y + 31, ModernUiRenderer.SUBTLE_TEXT, width);
        themeBounds.clear();
        themeIndices.clear();
        appearanceClipBounds = new ModernMainLayout.Rect(x, bounds.y + 48, Math.max(1, width),
                Math.max(1, bounds.height - 84));
        int contentHeight = themeListHeight();
        appearanceMaxScroll = Math.max(0, contentHeight - appearanceClipBounds.height);
        appearanceScroll = clamp(appearanceScroll, 0, appearanceMaxScroll);
        ModernUiRenderer.beginClip(appearanceClipBounds);
        int y = appearanceClipBounds.y - appearanceScroll;
        drawThemeGroups(fontRenderer, appearanceClipBounds, appearanceClipBounds, y, mouseX, mouseY);
        ModernUiRenderer.endClip();
        appearanceScrollbar.draw(appearanceClipBounds, appearanceScroll, appearanceMaxScroll,
                appearanceClipBounds.height, contentHeight, mouseX, mouseY, value -> appearanceScroll = value);
        editThemeBounds = new ModernMainLayout.Rect(x, bounds.bottom() - 28, width, 22);
        drawButton(fontRenderer, editThemeBounds, text("gui.general.manage_themes", "管理主题方案"), false, editThemeBounds.contains(mouseX, mouseY));
    }

    private void drawBehavior(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        int x = bounds.x + 12;
        int width = Math.max(1, bounds.width - 24);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.interface", "界面与交互"), x, bounds.y + 10, ModernUiRenderer.TEXT, width);
        behaviorClipBounds = new ModernMainLayout.Rect(x, bounds.y + 33, Math.max(1, width),
                Math.max(1, bounds.height - 42));
        int labelWidth = Math.min(74, Math.max(42, behaviorClipBounds.width / 4));
        int controlX = behaviorClipBounds.x + labelWidth;
        int controlWidth = ModernHoverScrollbar.contentWidth(behaviorClipBounds.width - labelWidth);
        int rowHeight = 25;
        int contentHeight = rowHeight * 4 + 6 * 3 + 15;
        behaviorMaxScroll = Math.max(0, contentHeight - behaviorClipBounds.height);
        behaviorScroll = clamp(behaviorScroll, 0, behaviorMaxScroll);
        ModernUiRenderer.beginClip(behaviorClipBounds);
        int y = behaviorClipBounds.y + 3 - behaviorScroll;
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.language", "语言"), behaviorClipBounds.x, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth - 6);
        languageBounds.clear();
        String currentLanguage = minecraft == null || minecraft.gameSettings == null ? "zh_cn" : minecraft.gameSettings.language;
        languageDropdown.setValue(currentLanguage);
        ModernMainLayout.Rect languageButton = new ModernMainLayout.Rect(controlX, y, controlWidth, rowHeight);
        languageBounds.add(languageButton);
        languageDropdown.drawButton(fontRenderer, languageButton, mouseX, mouseY);
        y += rowHeight + 6;
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.font_size", "字体大小"), behaviorClipBounds.x, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth - 6);
        scaleBounds.clear();
        scaleDropdown.setValue(String.valueOf(getUiScalePercent()));
        ModernMainLayout.Rect scaleButton = new ModernMainLayout.Rect(controlX, y, controlWidth, rowHeight);
        scaleBounds.add(scaleButton);
        scaleDropdown.drawButton(fontRenderer, scaleButton, mouseX, mouseY);
        y += rowHeight + 6;
        autoFocusBounds = new ModernMainLayout.Rect(behaviorClipBounds.x, y, ModernHoverScrollbar.contentWidth(behaviorClipBounds.width), rowHeight);
        drawToggleRow(fontRenderer, autoFocusBounds, text("gui.general.auto_focus", "自动聚焦搜索框"),
                MainUiLayoutManager.isModernAutoFocus(), mouseX, mouseY);
        y += rowHeight + 6;
        autoPauseBounds = new ModernMainLayout.Rect(behaviorClipBounds.x, y, ModernHoverScrollbar.contentWidth(behaviorClipBounds.width), rowHeight);
        drawToggleRow(fontRenderer, autoPauseBounds, text("gui.general.auto_pause", "打开菜单时自动暂停脚本"),
                MainUiLayoutManager.isModernAutoPauseOnMenuOpen(), mouseX, mouseY);
        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, behaviorClipBounds.x, y + rowHeight + 8,
                    ModernUiRenderer.SUCCESS, width);
        }
        ModernUiRenderer.endClip();
        behaviorScrollbar.draw(behaviorClipBounds, behaviorScroll, behaviorMaxScroll,
                behaviorClipBounds.height, contentHeight, mouseX, mouseY, value -> behaviorScroll = value);
    }

    private void drawToggleRow(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean enabled,
            int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : 0xFF151F28, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, bounds.width - 48));
        ModernUiRenderer.drawToggle(bounds.right() - 34, bounds.y + 5, 26, 15, enabled, hovered);
    }

    private void drawButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean selected,
            boolean hovered) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? 0xFF293B46 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                selected ? ModernUiRenderer.ACCENT_DIM : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(12, bounds.width - 12));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Let the shell handle header, sidebar, and tab-strip clicks.
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) return false;
        if (mouseButton != 0) return true;
        int previousLanguage = languageDropdown.selectedIndex();
        int previousScale = scaleDropdown.selectedIndex();
        if (languageDropdown.mouseClicked(mouseX, mouseY) || scaleDropdown.mouseClicked(mouseX, mouseY)) {
            applyDropdownChanges(previousLanguage, previousScale);
            return true;
        }
        if (compactMode && compactContentBounds != null && compactContentBounds.contains(mouseX, mouseY)
                && compactScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (!compactMode && dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (appearanceScrollbar.beginDrag(mouseX, mouseY) || behaviorScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        ModernMainLayout.Rect themeClip = compactMode ? compactContentBounds : appearanceClipBounds;
        for (int group = 0; group < themeGroupBounds.length; group++) {
            if (contains(themeClip, mouseX, mouseY) && contains(themeGroupBounds[group], mouseX, mouseY)) {
                themeGroupCollapsed[group] = !themeGroupCollapsed[group];
                themeBounds.clear();
                themeIndices.clear();
                return true;
            }
        }
        for (int i = 0; i < themeBounds.size(); i++) {
            if (contains(themeClip, mouseX, mouseY) && themeBounds.get(i).contains(mouseX, mouseY)) {
                ThemeConfigManager.setActiveIndex(themeIndices.get(i));
                ThemeConfigManager.save();
                showStatus(text("gui.general.theme_changed", "主题已切换"));
                return true;
            }
        }
        if (editThemeBounds != null && editThemeBounds.contains(mouseX, mouseY) && routeRequest != null) {
            routeRequest.accept("theme");
            return true;
        }
        if (autoFocusBounds != null && autoFocusBounds.contains(mouseX, mouseY)) {
            MainUiLayoutManager.setModernAutoFocus(!MainUiLayoutManager.isModernAutoFocus());
            return true;
        }
        if (autoPauseBounds != null && autoPauseBounds.contains(mouseX, mouseY)) {
            MainUiLayoutManager.setModernAutoPauseOnMenuOpen(!MainUiLayoutManager.isModernAutoPauseOnMenuOpen());
            return true;
        }
        return true;
    }

    private void applyDropdownChanges(int previousLanguage, int previousScale) {
        if (languageDropdown.selectedIndex() != previousLanguage) {
            applyLanguage(languageDropdown.value());
        }
        if (scaleDropdown.selectedIndex() != previousScale) {
            try {
                setUiScalePercent(Integer.parseInt(scaleDropdown.value()));
                showStatus(text("gui.general.font_applied", "字体大小已应用"));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void applyLanguage(String language) {
        if (minecraft == null || minecraft.gameSettings == null) return;
        GuiInventory.prepareForLanguageChange();
        minecraft.gameSettings.language = language;
        boolean applied = ClientTranslationInjector.INSTANCE.applyLanguage(language);
        try {
            minecraft.gameSettings.saveOptions();
        } catch (Throwable ignored) {
        }
        if (applied) {
            GuiInventory.refreshGuiLists();
            pendingLanguage = null;
        } else {
            pendingLanguage = language;
        }
        showStatus(applied ? text("gui.general.language_changed", "语言已切换，布局保持不变") : text("gui.general.language_loading", "语言正在后台加载"));
    }

    private int getUiScalePercent() {
        return DetachedSwingWindowManager.isDetached()
                ? MainUiLayoutManager.getDetachedUiScalePercent()
                : MainUiLayoutManager.getModernUiScalePercent();
    }

    private void setUiScalePercent(int scalePercent) {
        if (DetachedSwingWindowManager.isDetached()) {
            DetachedSwingWindowManager.INSTANCE.setUiScaleFromAction(scalePercent);
        } else {
            MainUiLayoutManager.setModernUiScalePercent(scalePercent);
        }
    }

    private String text(String key, String fallback) {
        String value = I18n.format(key);
        return value == null || value.equals(key) ? fallback : value;
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusUntil = System.currentTimeMillis() + 1800L;
    }

    private void drawCompactAppearance(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.appearance", "外观"), bounds.x, bounds.y,
                ModernUiRenderer.TEXT, bounds.width);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.theme_profile", "主题方案"), bounds.x,
                bounds.y + 19, ModernUiRenderer.SUBTLE_TEXT, bounds.width);
        themeBounds.clear();
        themeIndices.clear();
        int y = bounds.y + 34;
        y = drawThemeGroups(fontRenderer, bounds, compactContentBounds, y, mouseX, mouseY);
        editThemeBounds = new ModernMainLayout.Rect(bounds.x, y + 2, bounds.width, 22);
        drawButton(fontRenderer, editThemeBounds, text("gui.general.manage_themes", "管理主题方案"), false,
                editThemeBounds.contains(mouseX, mouseY));
    }

    private void drawCompactBehavior(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        int labelWidth = Math.min(76, Math.max(44, bounds.width / 4));
        int controlX = bounds.x + labelWidth;
        int controlWidth = Math.max(1, bounds.width - labelWidth);
        int rowHeight = 25;
        int y = bounds.y;
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.interface", "界面与交互"), bounds.x, y,
                ModernUiRenderer.TEXT, bounds.width);
        y += 23;
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.language", "语言"), bounds.x, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth - 6);
        languageDropdown.setValue(minecraft == null || minecraft.gameSettings == null ? "zh_cn"
                : minecraft.gameSettings.language);
        ModernMainLayout.Rect languageButton = new ModernMainLayout.Rect(controlX, y, controlWidth, rowHeight);
        languageBounds.add(languageButton);
        languageDropdown.drawButton(fontRenderer, languageButton, mouseX, mouseY);
        y += rowHeight + 6;
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.font_size_short", "字号"), bounds.x, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth - 6);
        scaleDropdown.setValue(String.valueOf(getUiScalePercent()));
        ModernMainLayout.Rect scaleButton = new ModernMainLayout.Rect(controlX, y, controlWidth, rowHeight);
        scaleBounds.add(scaleButton);
        scaleDropdown.drawButton(fontRenderer, scaleButton, mouseX, mouseY);
        y += rowHeight + 6;
        autoFocusBounds = new ModernMainLayout.Rect(bounds.x, y, bounds.width, rowHeight);
        drawToggleRow(fontRenderer, autoFocusBounds, text("gui.general.auto_focus", "自动聚焦搜索框"),
                MainUiLayoutManager.isModernAutoFocus(), mouseX, mouseY);
        y += rowHeight + 6;
        autoPauseBounds = new ModernMainLayout.Rect(bounds.x, y, bounds.width, rowHeight);
        drawToggleRow(fontRenderer, autoPauseBounds, text("gui.general.auto_pause_short", "打开菜单时自动暂停"),
                MainUiLayoutManager.isModernAutoPauseOnMenuOpen(), mouseX, mouseY);
        y += rowHeight + 8;
        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, bounds.x, y, ModernUiRenderer.SUCCESS,
                    Math.max(20, bounds.width));
        }
    }

    private void drawDropdownMenus(FontRenderer fontRenderer, ModernMainLayout.Rect host, int mouseX, int mouseY) {
        languageDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
        scaleDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
    }

    @Override public boolean keyTyped(char typedChar, int keyCode) { return false; }

    @Override public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton != 0) return false;
        if (compactScrollbar.isDragging()) {
            compactScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (appearanceScrollbar.isDragging()) {
            appearanceScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (behaviorScrollbar.isDragging()) {
            behaviorScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (draggingDivider && !compactMode && panelBounds != null) {
            int total = Math.max(2, panelBounds.width - 32 - 10);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - panelBounds.x - 16 - 5, 178, 250, 122, 160);
            appearanceRatio = split.ratio;
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state != 0) return false;
        boolean handled = compactScrollbar.isDragging() || appearanceScrollbar.isDragging()
                || behaviorScrollbar.isDragging() || draggingDivider;
        compactScrollbar.endDrag();
        appearanceScrollbar.endDrag();
        behaviorScrollbar.endDrag();
        if (draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("general.appearance", appearanceRatio);
        }
        draggingDivider = false;
        return handled;
    }

    @Override public boolean handleEscape() {
        if (languageDropdown.isOpen() || scaleDropdown.isOpen()) {
            languageDropdown.close();
            scaleDropdown.close();
            return true;
        }
        if (compactScrollbar.isDragging() || appearanceScrollbar.isDragging() || behaviorScrollbar.isDragging()
                || draggingDivider) {
            compactScrollbar.endDrag();
            appearanceScrollbar.endDrag();
            behaviorScrollbar.endDrag();
            draggingDivider = false;
            return true;
        }
        return false;
    }

    @Override public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) return false;
        int amount = wheel > 0 ? -34 : 34;
        if (compactMode && contains(compactContentBounds, mouseX, mouseY) && compactMaxScroll > 0) {
            int previous = compactScroll;
            compactScroll = clamp(compactScroll + amount, 0, compactMaxScroll);
            return previous != compactScroll;
        }
        if (contains(appearanceClipBounds, mouseX, mouseY) && appearanceMaxScroll > 0) {
            int previous = appearanceScroll;
            appearanceScroll = clamp(appearanceScroll + amount, 0, appearanceMaxScroll);
            return previous != appearanceScroll;
        }
        if (contains(behaviorClipBounds, mouseX, mouseY) && behaviorMaxScroll > 0) {
            int previous = behaviorScroll;
            behaviorScroll = clamp(behaviorScroll + amount, 0, behaviorMaxScroll);
            return previous != behaviorScroll;
        }
        return false;
    }

    @Override public boolean containsContent(int mouseX, int mouseY) { return panelBounds != null && panelBounds.contains(mouseX, mouseY); }
    @Override public String getHoveredTooltip(int mouseX, int mouseY) { return hoveredTooltip; }
    @Override public void discardDraft() { }

    private ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        if (bounds == null) return new ModernMainLayout.Rect(0, 0, 1, 1);
        return new ModernMainLayout.Rect(bounds.x + 10, bounds.y + 8, Math.max(1, bounds.width - 20),
                Math.max(1, bounds.height - 16));
    }

    private int themeListHeight() {
        int height = 2 * (ModernTreeGuide.GROUP_HEIGHT + ModernTreeGuide.GAP);
        for (ThemeProfile profile : ThemeConfigManager.getProfiles()) {
            if (profile != null && !themeGroupCollapsed[ThemeConfigManager.isBuiltInProfile(profile) ? 0 : 1]) {
                height += ModernTreeGuide.ITEM_HEIGHT + ModernTreeGuide.GAP;
            }
        }
        return height;
    }

    private int drawThemeGroups(FontRenderer fontRenderer, ModernMainLayout.Rect list,
            ModernMainLayout.Rect clip, int y, int mouseX, int mouseY) {
        List<ThemeProfile> profiles = ThemeConfigManager.getProfiles();
        int active = ThemeConfigManager.getActiveIndex();
        for (int groupIndex = 0; groupIndex < 2; groupIndex++) {
            boolean builtIn = groupIndex == 0;
            int count = 0;
            for (ThemeProfile profile : profiles) {
                if (profile != null && ThemeConfigManager.isBuiltInProfile(profile) == builtIn) count++;
            }
            ModernMainLayout.Rect group = ModernTreeGuide.groupRow(list, y);
            themeGroupBounds[groupIndex] = group;
            boolean hovered = clip.contains(mouseX, mouseY) && group.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(group.x, group.y, group.width, group.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawChevron(group.x + 8, group.y + 7, themeGroupCollapsed[groupIndex], ModernUiRenderer.SUBTLE_TEXT);
            ModernUiRenderer.drawText(fontRenderer, builtIn ? text("gui.general.builtin_themes", "内置主题")
                    : text("gui.general.custom_themes", "自定义主题"), group.x + 20, group.y + 7,
                    ModernUiRenderer.TEXT, Math.max(1, group.width - 48));
            ModernUiRenderer.drawText(fontRenderer, String.valueOf(count), group.right() - 23, group.y + 7,
                    ModernUiRenderer.MUTED_TEXT, 18);
            y = ModernTreeGuide.nextY(y, ModernTreeGuide.GROUP_HEIGHT);
            if (themeGroupCollapsed[groupIndex]) continue;
            for (int i = 0; i < profiles.size(); i++) {
                ThemeProfile profile = profiles.get(i);
                if (profile == null || ThemeConfigManager.isBuiltInProfile(profile) != builtIn) continue;
                ModernMainLayout.Rect row = ModernTreeGuide.itemRow(list, y);
                ModernTreeGuide.drawChild(list.x, 0, group, row);
                boolean selected = i == active;
                drawButton(fontRenderer, row, "", selected, clip.contains(mouseX, mouseY) && row.contains(mouseX, mouseY));
                ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 12,
                        selected ? ModernUiRenderer.ACCENT : builtIn ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT);
                ModernUiRenderer.drawText(fontRenderer, profile.name == null
                        ? text("gui.general.unnamed_theme", "未命名主题") : profile.name, row.x + 20, row.y + 11,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, row.width - 25));
                if (intersects(row, clip)) {
                    themeBounds.add(row);
                    themeIndices.add(i);
                }
                y = ModernTreeGuide.nextY(y, ModernTreeGuide.ITEM_HEIGHT);
            }
        }
        return y;
    }

    private int themeContentHeight() {
        return 34 + themeListHeight() + 40;
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
