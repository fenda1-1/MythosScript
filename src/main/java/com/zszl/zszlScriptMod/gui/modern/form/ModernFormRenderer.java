package com.zszl.zszlScriptMod.gui.modern.form;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** Draws a form definition and owns only transient Minecraft widget geometry. */
final class ModernFormRenderer {

    static final class ChoiceHit {
        final ModernMainLayout.Rect bounds;
        final ModernFormSettingsTab.ChoiceOption<?> option;

        ChoiceHit(ModernMainLayout.Rect bounds, ModernFormSettingsTab.ChoiceOption<?> option) {
            this.bounds = bounds;
            this.option = option;
        }
    }

    static final class ItemView {
        ModernTextField textField;
        ModernMainLayout.Rect rowBounds;
        ModernMainLayout.Rect controlBounds;
        ModernMainLayout.Rect infoBounds;
        final List<ChoiceHit> choiceHits = new ArrayList<>();

        void clear() {
            rowBounds = null;
            controlBounds = null;
            infoBounds = null;
            choiceHits.clear();
            if (textField != null) {
                textField.setVisible(false);
                textField.setFocused(false);
            }
        }
    }

    private final ModernFormDefinition<?> definition;
    private final Map<ModernFormDefinition.Item, ItemView> views = new IdentityHashMap<>();
    private final List<Integer> sectionOffsets = new ArrayList<>();
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect contentClipBounds;
    private boolean compactHeader;
    private RuleSectionState sectionState;

    void setSectionPages(RuleSectionState state) {
        sectionState = state;
        scrollOffset = state.scroll();
    }

    void selectSection(int index) {
        if (sectionState == null || index < 0 || index >= definition.getSections().size()) return;
        sectionState.remember(scrollOffset);
        sectionState.select(index);
        scrollOffset = sectionState.scroll();
        clearTextFieldFocus();
        clearConfirmation();
        endScrollbarDrag();
        for (ModernFormDefinition.Section section : definition.getSections()) clearSectionBounds(section);
    }

    private boolean sectionSelected(ModernFormDefinition.Section section) {
        return sectionState == null || definition.getSections().indexOf(section) == sectionState.selected();
    }

    List<ModernFormWidget> activeWidgets() {
        List<ModernFormWidget> result = new ArrayList<>();
        for (ModernFormDefinition.Section section : definition.getSections()) if (sectionSelected(section))
            for (ModernFormDefinition.Item item : section.getItems())
                if (item.getType() == ModernFormDefinition.ItemType.CUSTOM && item.isVisible())
                    result.add((ModernFormWidget)item.getValue());
        return result;
    }

    void setCompactHeader(boolean compact) {
        compactHeader = compact;
    }

    private ModernMainLayout.Rect headerToggleBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private ModernMainLayout.Rect defaultsBounds;
    private int scrollOffset;
    private int maxScrollOffset;
    private boolean drawingContent;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private boolean statusError;
    private boolean statusWarning;
    private long statusVersion;
    private ModernMainLayout.Rect scrollbarTrackBounds;
    private ModernMainLayout.Rect scrollbarThumbBounds;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final Set<ModernFormDefinition.Item> invalidItems = Collections
            .newSetFromMap(new IdentityHashMap<ModernFormDefinition.Item, Boolean>());
    private ModernFormDefinition.Item pendingConfirmation;
    private long pendingConfirmationUntil;

    ModernFormRenderer(ModernFormDefinition<?> definition) {
        this.definition = definition;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            for (ModernFormDefinition.Item item : section.getItems()) {
                views.put(item, new ItemView());
            }
        }
    }

    void initialize(FontRenderer fontRenderer) {
        for (ModernFormDefinition.Item item : items()) {
            if (item.getType() == ModernFormDefinition.ItemType.TEXT
                    || item.getType() == ModernFormDefinition.ItemType.INTEGER
                    || item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                ItemView view = view(item);
                if (view.textField == null) {
                    view.textField = createTextField(fontRenderer, item.getMaxLength());
                }
            }
        }
    }

    void updateScreen() {
        for (ItemView view : views.values()) {
            if (view.textField != null) {
                view.textField.updateCursorCounter();
            }
        }
    }

    void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        hoveredTooltip = "";
        panelBounds = buildPanelBounds(contentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        int headerHeight = compactHeader ? 2 : translated(definition.getSubtitle()).isEmpty()
                ? Math.min(38, Math.max(30, panelBounds.height / 7))
                : Math.min(44, Math.max(35, panelBounds.height / 5));
        if (!compactHeader) {
            drawHeader(fontRenderer, headerHeight, mouseX, mouseY);
            ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + headerHeight,
                    Math.max(1, panelBounds.width - 24), ModernUiRenderer.BORDER_SUBTLE);
        }
        int footerHeight = definition.isFooterVisible() ? 33 : 8;
        if (compactHeader && statusIsVisible()) footerHeight += 20;
        contentClipBounds = new ModernMainLayout.Rect(panelBounds.x + 8, panelBounds.y + headerHeight + 6,
                Math.max(1, panelBounds.width - 16), Math.max(1, panelBounds.height - headerHeight - footerHeight - 8));
        int formX = contentClipBounds.x + 7;
        int formWidth = ModernHoverScrollbar.contentWidth(contentClipBounds.width - 7);
        int formHeight = computeFormHeight(fontRenderer, formWidth);
        maxScrollOffset = Math.max(0, formHeight - contentClipBounds.height);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);
        if (sectionState != null) sectionState.remember(scrollOffset);
        drawingContent = true;
        sectionOffsets.clear();
        ModernUiRenderer.beginClip(contentClipBounds);
        int y = contentClipBounds.y + 2 - scrollOffset;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            sectionOffsets.add(y + scrollOffset);
            if (sectionSelected(section)) y = drawSection(fontRenderer, section, formX, y, formWidth, mouseX, mouseY);
            else clearSectionBounds(section);
        }
        ModernUiRenderer.endClip();
        drawingContent = false;
        drawScrollbar(mouseX, mouseY);
        if (definition.isFooterVisible()) {
            drawFooter(fontRenderer, mouseX, mouseY);
        }
    }

    ModernFormDefinition<?> getDefinition() {
        return definition;
    }

    /**
     * Exports the controls that were actually laid out in the current form
     * page. Geometry is still useful for the legacy click injector, while the
     * semantic fields let MCP callers inspect and set values directly.
     */
    List<GuiElementInspector.GuiElementInfo> getMcpGuiElements(String pathPrefix) {
        List<GuiElementInspector.GuiElementInfo> result = new ArrayList<>();
        String base = normalizePrefix(pathPrefix);
        if (headerToggleBounds != null && definition.getHeaderToggle() != null) {
            boolean value = definition.getHeaderToggle().get();
            addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                    GuiElementInspector.ElementType.CUSTOM, base + "header/toggle",
                    translated(value ? "gui.modern.form.running" : "gui.modern.form.stopped"),
                    headerToggleBounds.x, headerToggleBounds.y, headerToggleBounds.width, headerToggleBounds.height,
                    Integer.MIN_VALUE, -1, "toggle", String.valueOf(value), true, true,
                    java.util.Arrays.asList("click", "set"), Collections.<String>emptyList()));
        }

        int sectionIndex = 0;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            String sectionPath = base + "section/" + mcpSegment(section.getTitle()) + "-" + sectionIndex;
            int itemIndex = 0;
            for (ModernFormDefinition.Item item : section.getItems()) {
                ModernFormRenderer.ItemView view = view(item);
                if (!item.isVisible() || view == null || view.rowBounds == null) {
                    itemIndex++;
                    continue;
                }
                String fieldPath = sectionPath + "/field/" + mcpSegment(item.getTitle()) + "-" + itemIndex;
                addMcpFormItem(result, fieldPath, item, view);
                itemIndex++;
            }
            sectionIndex++;
        }

        if (saveBounds != null) {
            addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                    GuiElementInspector.ElementType.CUSTOM, base + "footer/save", translated(definition.getSaveLabel()),
                    saveBounds.x, saveBounds.y, saveBounds.width, saveBounds.height, Integer.MIN_VALUE, -1,
                    "action", "", true, false, Collections.singletonList("click"), Collections.<String>emptyList()));
        }
        if (defaultsBounds != null) {
            addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                    GuiElementInspector.ElementType.CUSTOM, base + "footer/defaults",
                    translated(definition.getDefaultsLabel()), defaultsBounds.x, defaultsBounds.y,
                    defaultsBounds.width, defaultsBounds.height, Integer.MIN_VALUE, -1,
                    "action", "", true, false, Collections.singletonList("click"), Collections.<String>emptyList()));
        }
        if (revertBounds != null) {
            addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                    GuiElementInspector.ElementType.CUSTOM, base + "footer/revert",
                    translated(definition.getRevertLabel()), revertBounds.x, revertBounds.y,
                    revertBounds.width, revertBounds.height, Integer.MIN_VALUE, -1,
                    "action", "", true, false, Collections.singletonList("click"), Collections.<String>emptyList()));
        }
        if (scrollbarTrackBounds != null && maxScrollOffset > 0) {
            addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                    GuiElementInspector.ElementType.CUSTOM, base + "scrollbar", "",
                    scrollbarTrackBounds.x, scrollbarTrackBounds.y, scrollbarTrackBounds.width,
                    scrollbarTrackBounds.height, Integer.MIN_VALUE, -1, "scrollbar",
                    String.valueOf(scrollOffset), true, false, Collections.singletonList("scroll"),
                    Collections.<String>emptyList()));
        }
        return result;
    }

    private void addMcpFormItem(List<GuiElementInspector.GuiElementInfo> result, String fieldPath,
            ModernFormDefinition.Item item, ItemView view) {
        ModernMainLayout.Rect bounds = view.controlBounds == null ? view.rowBounds : view.controlBounds;
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        boolean enabled = item.isEnabled();
        String value = valueFor(item, view);
        String controlType;
        boolean editable;
        List<String> actions;
        List<String> choices = new ArrayList<>();
        switch (item.getType()) {
        case TOGGLE:
            controlType = "toggle";
            editable = true;
            actions = java.util.Arrays.asList("click", "set");
            break;
        case TEXT:
        case INTEGER:
        case DECIMAL:
            controlType = "input";
            editable = true;
            actions = java.util.Arrays.asList("click", "input", "set");
            break;
        case CHOICE:
            controlType = "choice";
            editable = true;
            actions = java.util.Arrays.asList("click", "set");
            for (ModernFormSettingsTab.ChoiceOption<?> option : item.getOptions()) {
                choices.add(optionValue(option));
            }
            break;
        case ACTION:
            controlType = "action";
            editable = false;
            actions = Collections.singletonList("click");
            break;
        case READ_ONLY:
            controlType = "read_only";
            editable = false;
            actions = Collections.emptyList();
            break;
        case CUSTOM:
        default:
            controlType = "custom";
            editable = false;
            actions = Collections.singletonList("click");
            break;
        }
        addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                GuiElementInspector.ElementType.CUSTOM, fieldPath, translated(item.getTitle()), bounds.x, bounds.y,
                bounds.width, bounds.height, Integer.MIN_VALUE, -1, controlType, value, enabled, editable,
                actions, choices).withVisibility(intersects(contentClipBounds, bounds)));

        if (item.getType() == ModernFormDefinition.ItemType.CHOICE) {
            for (ModernFormRenderer.ChoiceHit hit : view.choiceHits) {
                if (hit == null || hit.bounds == null || hit.option == null) continue;
                String optionPath = fieldPath + "/choice/" + mcpSegment(optionValue(hit.option));
                addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                        GuiElementInspector.ElementType.CUSTOM, optionPath,
                        translated(hit.option.getLabel()), hit.bounds.x, hit.bounds.y, hit.bounds.width,
                        hit.bounds.height, Integer.MIN_VALUE, -1, "choice_option", optionValue(hit.option),
                        enabled, true, Collections.singletonList("click"), Collections.<String>emptyList())
                        .withVisibility(intersects(contentClipBounds, hit.bounds)));
            }
        } else if (item.getType() == ModernFormDefinition.ItemType.TEXT
                || item.getType() == ModernFormDefinition.ItemType.INTEGER
                || item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
            addMcpElement(result, new GuiElementInspector.GuiElementInfo(
                    GuiElementInspector.ElementType.CUSTOM, fieldPath + "/input", translated(item.getTitle()),
                    bounds.x, bounds.y, bounds.width, bounds.height, Integer.MIN_VALUE, -1, controlType, value,
                    enabled, editable, actions, choices).withVisibility(intersects(contentClipBounds, bounds)));
        }

        if (item.getType() == ModernFormDefinition.ItemType.CUSTOM && item.getValue() instanceof ModernFormWidget) {
            ModernFormWidget widget = (ModernFormWidget) item.getValue();
            List<GuiElementInspector.GuiElementInfo> children = widget.getMcpGuiElements();
            if (children != null) {
                for (GuiElementInspector.GuiElementInfo child : children) {
                    if (child == null) continue;
                    String childPath = trimPath(child.getPath());
                    if (childPath.startsWith("custom/")) childPath = childPath.substring("custom/".length());
                    addMcpElement(result, child.getPath().startsWith("screen/") ? child : child.withPath(
                            fieldPath + "/custom/" + childPath));
                }
            }
        }
    }

    private static String valueFor(ModernFormDefinition.Item item, ItemView view) {
        if (item == null) return "";
        try {
            switch (item.getType()) {
            case TOGGLE:
                ModernFormSettingsTab.BooleanValue toggle = (ModernFormSettingsTab.BooleanValue) item.getValue();
                return String.valueOf(toggle != null && toggle.get());
            case TEXT:
            case INTEGER:
            case DECIMAL:
                if (view != null && view.textField != null) return safe(view.textField.getText());
                if (item.getType() == ModernFormDefinition.ItemType.TEXT) {
                    ModernFormSettingsTab.TextValue text = (ModernFormSettingsTab.TextValue) item.getValue();
                    return text == null ? "" : safe(text.get());
                }
                if (item.getType() == ModernFormDefinition.ItemType.INTEGER) {
                    ModernFormSettingsTab.IntValue integer = (ModernFormSettingsTab.IntValue) item.getValue();
                    return integer == null ? "" : String.valueOf(integer.get());
                }
                ModernFormSettingsTab.FloatValue decimal = (ModernFormSettingsTab.FloatValue) item.getValue();
                return decimal == null ? "" : formatFloat(decimal.get());
            case CHOICE:
                ModernFormSettingsTab.ChoiceValue<?> choice = (ModernFormSettingsTab.ChoiceValue<?>) item.getValue();
                return choice == null || choice.get() == null ? "" : String.valueOf(choice.get());
            case READ_ONLY:
                ModernFormSettingsTab.ReadOnlyValue readOnly = (ModernFormSettingsTab.ReadOnlyValue) item.getValue();
                return readOnly == null ? "" : safe(readOnly.get());
            default:
                return "";
            }
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String optionValue(ModernFormSettingsTab.ChoiceOption<?> option) {
        return option == null || option.getValue() == null ? "" : String.valueOf(option.getValue());
    }

    private static void addMcpElement(List<GuiElementInspector.GuiElementInfo> result,
            GuiElementInspector.GuiElementInfo element) {
        if (element != null && element.getPath() != null && !element.getPath().trim().isEmpty()
                && element.getWidth() > 0 && element.getHeight() > 0) {
            for (GuiElementInspector.GuiElementInfo existing : result) {
                if (existing != null && element.getPath().equals(existing.getPath())) return;
            }
            result.add(element);
        }
    }

    private static String normalizePrefix(String value) {
        String result = safe(value).replace('\\', '/').trim();
        while (result.startsWith("/")) result = result.substring(1);
        return result.isEmpty() || result.endsWith("/") ? result : result + "/";
    }

    private static String trimPath(String value) {
        String result = safe(value).replace('\\', '/');
        while (result.startsWith("/")) result = result.substring(1);
        return result;
    }

    private static String mcpSegment(String value) {
        String result = safe(value).replaceAll("[^A-Za-z0-9_.-]+", "_");
        return result.isEmpty() ? "item" : result;
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first == null || second == null
                || first.x < second.right() && second.x < first.right()
                        && first.y < second.bottom() && second.y < first.bottom();
    }

    List<ModernFormWidget> widgets() {
        List<ModernFormWidget> result = new ArrayList<>();
        for (ModernFormDefinition.Item item : items()) {
            if (item.getType() == ModernFormDefinition.ItemType.CUSTOM && item.isVisible())
                result.add((ModernFormWidget) item.getValue());
        }
        return result;
    }

    void revealWidget(ModernFormWidget widget) {
        if (contentClipBounds == null) return;
        for (ModernFormDefinition.Item item : items()) {
            ItemView view = view(item);
            if (item.getValue() == widget && view.rowBounds != null) {
                scrollOffset = clamp(scrollOffset + view.rowBounds.y - contentClipBounds.y, 0, maxScrollOffset);
                return;
            }
        }
    }

    ModernFormWidget widgetAt(int x, int y) {
        if (contentClipBounds == null || !contentClipBounds.contains(x, y)) return null;
        for (ModernFormDefinition.Item item : items()) {
            if (item.getType() == ModernFormDefinition.ItemType.CUSTOM && item.isVisible()
                    && view(item).rowBounds != null && view(item).rowBounds.contains(x, y))
                return (ModernFormWidget) item.getValue();
        }
        return null;
    }

    List<ModernFormDefinition.Item> items() {
        List<ModernFormDefinition.Item> result = new ArrayList<>();
        for (ModernFormDefinition.Section section : definition.getSections()) {
            result.addAll(section.getItems());
        }
        return result;
    }

    ItemView view(ModernFormDefinition.Item item) {
        return views.get(item);
    }

    boolean numericSliderContains(ItemView view, int mouseX, int mouseY) {
        return view != null && isNumericSliderHit(view.controlBounds, mouseX, mouseY);
    }

    private static boolean isNumericSliderHit(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        if (bounds == null || mouseX < bounds.x || mouseX >= bounds.right()
                || mouseY < bounds.bottom() - 8 || mouseY >= bounds.bottom()) {
            return false;
        }
        int trackX = bounds.x + 8;
        int trackW = Math.max(4, bounds.width - 16);
        return mouseX >= trackX - 2 && mouseX < trackX + trackW + 2;
    }

    ModernMainLayout.Rect getPanelBounds() {
        return panelBounds;
    }

    ModernMainLayout.Rect getContentClipBounds() {
        return contentClipBounds;
    }

    ModernMainLayout.Rect getHeaderToggleBounds() {
        return headerToggleBounds;
    }

    ModernMainLayout.Rect getSaveBounds() {
        return saveBounds;
    }

    ModernMainLayout.Rect getRevertBounds() {
        return revertBounds;
    }

    ModernMainLayout.Rect getDefaultsBounds() {
        return defaultsBounds;
    }

    boolean revealMcpItem(ModernFormDefinition.Item item) {
        if (item == null || contentClipBounds == null) return false;
        ItemView view = view(item);
        if (view == null || view.rowBounds == null) return false;
        int delta = 0;
        if (view.rowBounds.y < contentClipBounds.y) {
            delta = view.rowBounds.y - contentClipBounds.y - 4;
        } else if (view.rowBounds.bottom() > contentClipBounds.bottom()) {
            delta = view.rowBounds.bottom() - contentClipBounds.bottom() + 4;
        }
        if (delta != 0) scrollBy(delta);
        return true;
    }

    int getScrollOffset() {
        return scrollOffset;
    }

    int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    String getHoveredTooltip() {
        return hoveredTooltip;
    }

    void showStatus(String message) {
        statusMessage = safe(message);
        statusError = looksLikeError(statusMessage);
        statusWarning = !statusError && looksLikeWarning(statusMessage);
        statusMessageUntil = System.currentTimeMillis() + 2600L;
        statusVersion++;
    }

    void showError(String message) {
        statusMessage = safe(message);
        statusError = true;
        statusWarning = false;
        statusMessageUntil = System.currentTimeMillis() + 3600L;
        statusVersion++;
    }

    boolean hasVisibleStatus() {
        return statusIsVisible();
    }

    long getStatusVersion() {
        return statusVersion;
    }

    ModernMainLayout.Rect getScrollbarTrackBounds() {
        return scrollbarTrackBounds;
    }

    boolean scrollbarContains(int mouseX, int mouseY) {
        return scrollbar.contains(mouseX, mouseY);
    }

    boolean beginScrollbarDrag(int mouseX, int mouseY) {
        return scrollbar.beginDrag(mouseX, mouseY);
    }

    void dragScrollbar(int mouseX, int mouseY) {
        scrollbar.applyDrag(mouseX, mouseY);
    }

    void endScrollbarDrag() {
        scrollbar.endDrag();
    }

    void beginScrollbarDrag(int mouseY) {
        scrollbar.beginDrag(contentClipBounds == null ? 0 : contentClipBounds.right() - 2, mouseY);
    }

    void scrollTo(int mouseY) {
        scrollbar.beginDrag(contentClipBounds == null ? 0 : contentClipBounds.right() - 2, mouseY);
        scrollbar.endDrag();
    }

    void markInvalid(ModernFormDefinition.Item item, boolean invalid) {
        if (item == null) {
            return;
        }
        if (invalid) {
            invalidItems.add(item);
        } else {
            invalidItems.remove(item);
        }
    }

    void clearInvalidItems() {
        invalidItems.clear();
    }

    boolean isInvalid(ModernFormDefinition.Item item) {
        return invalidItems.contains(item);
    }

    void armConfirmation(ModernFormDefinition.Item item) {
        pendingConfirmation = item;
        pendingConfirmationUntil = System.currentTimeMillis() + 4200L;
    }

    boolean isConfirmationPending(ModernFormDefinition.Item item) {
        if (pendingConfirmation != null && System.currentTimeMillis() > pendingConfirmationUntil) {
            pendingConfirmation = null;
        }
        return pendingConfirmation == item;
    }

    boolean clearConfirmation() {
        if (pendingConfirmation == null) {
            return false;
        }
        pendingConfirmation = null;
        pendingConfirmationUntil = 0L;
        return true;
    }

    void syncInputFields() {
        for (ModernFormDefinition.Item item : items()) {
            ItemView view = view(item);
            if (view.textField == null) {
                continue;
            }
            if (item.getType() == ModernFormDefinition.ItemType.TEXT) {
                ModernFormSettingsTab.TextValue value = (ModernFormSettingsTab.TextValue) item.getValue();
                view.textField.setText(value == null ? "" : safe(value.get()));
            } else if (item.getType() == ModernFormDefinition.ItemType.INTEGER) {
                ModernFormSettingsTab.IntValue value = (ModernFormSettingsTab.IntValue) item.getValue();
                view.textField.setText(value == null ? "" : String.valueOf(value.get()));
            } else if (item.getType() == ModernFormDefinition.ItemType.DECIMAL) {
                ModernFormSettingsTab.FloatValue value = (ModernFormSettingsTab.FloatValue) item.getValue();
                view.textField.setText(value == null ? "" : formatFloat(value.get()));
            }
        }
        clearTextFieldFocus();
    }

    void clearTextFieldFocus() {
        for (ItemView view : views.values()) {
            if (view.textField != null) {
                view.textField.setFocused(false);
            }
        }
    }

    boolean hasFocusedTextField() {
        for (ModernFormWidget widget : widgets()) if (widget.isTextInputFocused()) return true;
        for (ItemView view : views.values()) {
            if (view.textField != null && view.textField.isFocused()) {
                return true;
            }
        }
        return false;
    }

    void scrollBy(int amount) {
        scrollOffset = clamp(scrollOffset + amount, 0, maxScrollOffset);
        if (sectionState != null) sectionState.remember(scrollOffset);
    }

    void scrollToSection(int index) {
        if (sectionState != null) { selectSection(index); return; }
        if (contentClipBounds == null || index < 0 || index >= sectionOffsets.size()) {
            return;
        }
        scrollOffset = clamp(sectionOffsets.get(index) - contentClipBounds.y - 2, 0, maxScrollOffset);
    }

    private void drawHeader(FontRenderer fontRenderer, int headerHeight, int mouseX, int mouseY) {
        String title = translated(definition.getTitle());
        String subtitle = translated(definition.getSubtitle());
        int titleX = panelBounds.x + 15;
        int titleY = subtitle.isEmpty() ? panelBounds.y + (headerHeight - fontRenderer.FONT_HEIGHT) / 2
                : panelBounds.y + 9;
        int rightReserve = definition.getHeaderToggle() == null ? 20 : 123;
        int statusReserve = statusIsVisible() ? Math.min(112, fontRenderer.getStringWidth(statusMessage) + 5) : 0;
        int titleWidth = Math.max(32, panelBounds.width - rightReserve - statusReserve - 25);
        ModernUiRenderer.drawText(fontRenderer, title, titleX, titleY, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
                titleY - 1, definition.getHeaderTooltip(), mouseX, mouseY);
        if (!subtitle.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, subtitle, titleX, titleY + 13,
                    ModernUiRenderer.MUTED_TEXT, titleWidth);
        }
        ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
        if (toggle != null) {
            int toggleWidth = 44;
            headerToggleBounds = new ModernMainLayout.Rect(panelBounds.right() - toggleWidth - 15,
                    panelBounds.y + (headerHeight - 16) / 2, toggleWidth, 16);
            boolean hovered = headerToggleBounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawToggle(headerToggleBounds.x, headerToggleBounds.y, headerToggleBounds.width,
                    headerToggleBounds.height, toggle.get(), hovered);
            String label = toggle.get() ? translated("gui.modern.form.running")
                    : translated("gui.modern.form.stopped");
            int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
            ModernUiRenderer.drawText(fontRenderer, label, headerToggleBounds.x - 8 - labelWidth,
                    panelBounds.y + (headerHeight - fontRenderer.FONT_HEIGHT) / 2,
                    toggle.get() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, labelWidth);
            drawInfoIcon(headerToggleBounds.x - 15, headerToggleBounds.y + 2,
                    definition.getHeaderToggleTooltip(), mouseX, mouseY);
        } else {
            headerToggleBounds = null;
        }
        if (statusIsVisible()) {
            int maxStatusWidth = Math.max(40, panelBounds.width / 3);
            int textWidth = Math.min(maxStatusWidth - 16, fontRenderer.getStringWidth(statusMessage));
            int width = Math.max(42, textWidth + 23);
            int x = toggle == null ? panelBounds.right() - width - 14 : titleX + titleWidth + 5;
            int y = titleY - 3;
            int statusColor = statusError ? ModernUiRenderer.DANGER
                    : statusWarning ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS;
            ModernUiRenderer.drawSubtlePanel(x, y, width, 18, 4,
                    ModernUiRenderer.statusSurface(statusColor), statusColor);
            ModernUiRenderer.drawStatusDot(x + 6, y + 6, statusColor);
            ModernUiRenderer.drawText(fontRenderer, statusMessage, x + 17, y + 5, statusColor,
                    Math.max(20, width - 21));
        }
    }

    private int drawSection(FontRenderer fontRenderer, ModernFormDefinition.Section section, int x, int y, int width,
            int mouseX, int mouseY) {
        if (!sectionHasVisibleItems(section)) {
            clearSectionBounds(section);
            return y;
        }
        String sectionTitle = translated(section.getTitle());
        ModernUiRenderer.drawText(fontRenderer, sectionTitle, x, y, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(32, width - 12));
        drawInfoIcon(x + Math.min(width - 11, fontRenderer.getStringWidth(sectionTitle) + 5), y - 1,
                section.getTooltip(), mouseX, mouseY);
        y += 17;
        y = layoutSectionItems(fontRenderer, section, x, y, width, mouseX, mouseY, true);
        return y + 5;
    }

    private void drawItem(FontRenderer fontRenderer, ModernFormDefinition.Item item, ItemView view, int mouseX,
            int mouseY) {
        ModernMainLayout.Rect row = view.rowBounds;
        if (item.getType() == ModernFormDefinition.ItemType.CUSTOM) {
            ModernFormWidget widget = (ModernFormWidget) item.getValue();
            widget.setViewport(contentClipBounds);
            widget.draw(fontRenderer, row, mouseX, mouseY);
            if (contentClipBounds.contains(mouseX, mouseY) && row.contains(mouseX, mouseY)) {
                hoveredTooltip = widget.getHoveredTooltip(mouseX, mouseY);
            }
            return;
        }
        boolean enabled = item.isEnabled();
        boolean hovered = row.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D25 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int rowBorder = isInvalid(item) ? 0xFFE06A78 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5, fill, rowBorder);
        if (item.getType() == ModernFormDefinition.ItemType.CHOICE) {
            drawChoiceItem(fontRenderer, item, view, enabled, mouseX, mouseY);
            return;
        }
        boolean compact = usesStackedLayout(item, row.width);
        String itemTitle = translated(item.getTitle());
        int titleY = row.y + (compact ? 5 : (row.height - fontRenderer.FONT_HEIGHT) / 2);
        int titleWidth = compact ? row.width - 20 : labelWidthFor(row.width);
        ModernUiRenderer.drawText(fontRenderer, itemTitle, row.x + 9, titleY,
                enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(24, titleWidth));
        view.infoBounds = drawInfoIcon(row.x + 9 + Math.min(Math.max(0, titleWidth - 12),
                fontRenderer.getStringWidth(itemTitle) + 5), titleY - 1, item.getTooltip(), mouseX, mouseY);
        if (item.getType() == ModernFormDefinition.ItemType.TOGGLE) {
            int toggleWidth = Math.max(28, Math.min(38, row.width / 5));
            view.controlBounds = new ModernMainLayout.Rect(row.right() - toggleWidth - 9,
                    row.y + (row.height - 16) / 2, toggleWidth, 16);
            ModernFormSettingsTab.BooleanValue value = (ModernFormSettingsTab.BooleanValue) item.getValue();
            ModernUiRenderer.drawToggle(view.controlBounds.x, view.controlBounds.y, view.controlBounds.width,
                    view.controlBounds.height, value != null && value.get(), hovered && enabled);
            return;
        }
        view.controlBounds = controlBoundsFor(item, row, compact);
        if (item.getType() == ModernFormDefinition.ItemType.READ_ONLY) {
            drawReadOnlyValue(fontRenderer, item, view.controlBounds, enabled);
        } else if (item.getType() == ModernFormDefinition.ItemType.ACTION) {
            String actionLabel = isConfirmationPending(item) ? translated("gui.modern.form.confirm_again")
                    : translated(item.getButtonLabel());
            drawActionButton(fontRenderer, view.controlBounds, actionLabel, item.getActionStyle(), enabled,
                    mouseX, mouseY);
        } else {
            drawTextInput(fontRenderer, item, view, enabled, mouseX, mouseY);
        }
    }

    private void drawChoiceItem(FontRenderer fontRenderer, ModernFormDefinition.Item item, ItemView view,
            boolean enabled, int mouseX, int mouseY) {
        ModernMainLayout.Rect row = view.rowBounds;
        String itemTitle = translated(item.getTitle());
        ModernUiRenderer.drawText(fontRenderer, itemTitle, row.x + 9, row.y + 5,
                enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(24, row.width - 20));
        view.infoBounds = drawInfoIcon(row.x + 9 + Math.min(Math.max(0, row.width - 32),
                fontRenderer.getStringWidth(itemTitle) + 5), row.y + 4, item.getTooltip(), mouseX, mouseY);
        view.choiceHits.clear();
        if (item.getOptions().isEmpty()) {
            view.controlBounds = null;
            return;
        }
        ModernFormSettingsTab.ChoiceValue<?> value = (ModernFormSettingsTab.ChoiceValue<?>) item.getValue();
        Object selected = value == null ? null : value.get();
        int x = row.x + 9;
        int y = row.y + 18;
        int available = Math.max(1, row.width - 18);
        for (ModernFormSettingsTab.ChoiceOption<?> option : item.getOptions()) {
            String optionLabel = translated(option.getLabel());
            int desired = Math.max(42, fontRenderer.getStringWidth(optionLabel) + 16);
            int optionWidth = Math.min(available, desired);
            if (x > row.x + 9 && x + optionWidth > row.right() - 9) {
                x = row.x + 9;
                y += 21;
            }
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, optionWidth, 18);
            view.choiceHits.add(new ChoiceHit(bounds, option));
            boolean selectedOption = option.matches(selected);
            boolean hovered = bounds.contains(mouseX, mouseY);
            int fill = selectedOption ? ModernUiRenderer.ACCENT_DIM
                    : hovered && enabled ? ModernUiRenderer.SURFACE_PRESSED : 0xFF111A22;
            int border = selectedOption ? ModernUiRenderer.ACCENT : hovered && enabled ? 0xFF617581
                    : ModernUiRenderer.BORDER_SUBTLE;
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
            int color = selectedOption ? ModernUiRenderer.TEXT
                    : enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT;
            ModernUiRenderer.drawText(fontRenderer, optionLabel, bounds.x + 7,
                    bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, color, bounds.width - 12);
            x += optionWidth + 4;
        }
        view.controlBounds = new ModernMainLayout.Rect(row.x + 9, row.y + 18, Math.max(1, row.width - 18),
                Math.max(18, row.bottom() - row.y - 23));
    }

    private void drawTextInput(FontRenderer fontRenderer, ModernFormDefinition.Item item, ItemView view, boolean enabled,
            int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = view.controlBounds;
        boolean focused = view.textField != null && view.textField.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        int border = isInvalid(item) ? 0xFFE06A78 : focused ? ModernUiRenderer.ACCENT
                : hovered && enabled ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820, border);
        if (enabled && (item.getType() == ModernFormDefinition.ItemType.INTEGER
                || item.getType() == ModernFormDefinition.ItemType.DECIMAL)) {
            double min = item.getType() == ModernFormDefinition.ItemType.INTEGER ? item.getIntMin() : item.getFloatMin();
            double max = item.getType() == ModernFormDefinition.ItemType.INTEGER ? item.getIntMax() : item.getFloatMax();
            double value = item.getType() == ModernFormDefinition.ItemType.INTEGER
                    ? ((ModernFormSettingsTab.IntValue) item.getValue()).get()
                    : ((ModernFormSettingsTab.FloatValue) item.getValue()).get();
            float fraction = (float) Math.max(0D, Math.min(1D,
                    (value - min) / Math.max(1.0E-6D, max - min)));
            boolean trackHovered = isNumericSliderHit(bounds, mouseX, mouseY);
            int trackX = bounds.x + 8;
            int trackW = Math.max(4, bounds.width - 16);
            int trackHeight = trackHovered ? 4 : 2;
            int trackY = bounds.bottom() - (trackHovered ? 5 : 3);
            int trackColor = trackHovered ? ModernUiRenderer.BORDER : 0xFF43515C;
            int fillColor = trackHovered ? ModernUiRenderer.ACCENT : 0xFF58B6D8;
            int filledWidth = Math.max(0, Math.min(trackW, Math.round(trackW * fraction)));
            net.minecraft.client.gui.Gui.drawRect(trackX, trackY, trackX + trackW, trackY + trackHeight, trackColor);
            if (filledWidth > 0) {
                net.minecraft.client.gui.Gui.drawRect(trackX, trackY, trackX + filledWidth, trackY + trackHeight,
                        fillColor);
            }
            if (trackHovered) {
                int thumbX = trackX + Math.round(trackW * fraction);
                net.minecraft.client.gui.Gui.drawRect(thumbX - 3, trackY, thumbX + 3, trackY + trackHeight,
                        ModernUiRenderer.TEXT);
                net.minecraft.client.gui.Gui.drawRect(thumbX - 2, trackY, thumbX + 2, trackY + trackHeight,
                        fillColor);
            }
        }
        if (view.textField == null) {
            return;
        }
        view.textField.layout(bounds);
        view.textField.setPlaceholder(item.getPlaceholder());
        view.textField.setVisible(true);
        view.textField.setEnabled(enabled);
        view.textField.drawTextContents(fontRenderer);
    }

    private void drawReadOnlyValue(FontRenderer fontRenderer, ModernFormDefinition.Item item,
            ModernMainLayout.Rect bounds, boolean enabled) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF111A22,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernFormSettingsTab.ReadOnlyValue value = (ModernFormSettingsTab.ReadOnlyValue) item.getValue();
        String text = value == null ? "" : safe(value.get());
        ModernUiRenderer.drawText(fontRenderer, text, bounds.x + 7,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(1, bounds.width - 14));
    }

    private void drawFooter(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int y = panelBounds.bottom() - 28;
        int count = definition.getStateAdapter().supportsDefaults() ? 3 : 2;
        boolean footerToggle = compactHeader && definition.getHeaderToggle() != null;
        if (footerToggle) count++;
        int gap = 6;
        int available = Math.max(1, panelBounds.width - 28 - gap * (count - 1));
        int baseWidth = Math.max(1, available / count);
        int x = panelBounds.x + 14;
        if (footerToggle) {
            int toggleWidth = Math.min(44, Math.max(1, baseWidth - 8));
            headerToggleBounds = new ModernMainLayout.Rect(x + baseWidth - toggleWidth - 4, y + 3, toggleWidth, 16);
            ModernFormSettingsTab.BooleanValue toggle = definition.getHeaderToggle();
            ModernUiRenderer.drawToggle(headerToggleBounds.x, headerToggleBounds.y, toggleWidth, 16,
                    toggle.get(), headerToggleBounds.contains(mouseX, mouseY));
            ModernUiRenderer.drawText(fontRenderer, translated(toggle.get() ? "gui.modern.form.running"
                    : "gui.modern.form.stopped"), x + 4, y + 7,
                    toggle.get() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, baseWidth - toggleWidth - 24));
            drawInfoIcon(headerToggleBounds.x - 15, y + 5,
                    definition.getHeaderToggleTooltip(), mouseX, mouseY);
            x += baseWidth + gap;
        }
        if (compactHeader && statusIsVisible()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, panelBounds.x + 14, y - 16,
                    statusError ? ModernUiRenderer.DANGER : statusWarning ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS,
                    Math.max(1, panelBounds.width - 28));
        }
        saveBounds = new ModernMainLayout.Rect(x, y, Math.max(baseWidth, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(fontRenderer, definition.getSaveLabel(), 16)), 22);
        x = saveBounds.right() + gap;
        if (definition.getStateAdapter().supportsDefaults()) {
            defaultsBounds = new ModernMainLayout.Rect(x, y, baseWidth, 22);
            x = defaultsBounds.right() + gap;
        } else {
            defaultsBounds = null;
        }
        revertBounds = new ModernMainLayout.Rect(x, y, Math.max(1, panelBounds.right() - 14 - x), 22);
        drawActionButton(fontRenderer, saveBounds, translated(definition.getSaveLabel()),
                ModernFormSettingsTab.ActionStyle.PRIMARY, true, mouseX, mouseY);
        if (defaultsBounds != null) {
            drawActionButton(fontRenderer, defaultsBounds, translated(definition.getDefaultsLabel()),
                    ModernFormSettingsTab.ActionStyle.SECONDARY, true, mouseX, mouseY);
        }
        drawActionButton(fontRenderer, revertBounds, translated(definition.getRevertLabel()),
                ModernFormSettingsTab.ActionStyle.SECONDARY, true, mouseX, mouseY);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label,
            ModernFormSettingsTab.ActionStyle style, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = ModernUiRenderer.DISABLED_SURFACE;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.DISABLED_TEXT;
        } else if (style == ModernFormSettingsTab.ActionStyle.PRIMARY) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT;
            border = ModernUiRenderer.BORDER;
            text = ModernUiRenderer.TEXT;
        } else if (style == ModernFormSettingsTab.ActionStyle.WARNING) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.WARNING;
            border = ModernUiRenderer.BORDER;
            text = ModernUiRenderer.TEXT;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = ModernUiRenderer.BORDER;
            text = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                ModernUiRenderer.readableText(text, fill), Math.max(1, bounds.width - 8));
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (maxScrollOffset <= 0 || contentClipBounds == null) {
            scrollbar.idle();
            scrollbarTrackBounds = null;
            scrollbarThumbBounds = null;
            return;
        }
        scrollbar.draw(contentClipBounds, scrollOffset, maxScrollOffset, contentClipBounds.height,
                contentClipBounds.height + maxScrollOffset, mouseX, mouseY, value -> { scrollOffset = value; if (sectionState != null) sectionState.remember(value); });
        scrollbarTrackBounds = new ModernMainLayout.Rect(contentClipBounds.right() - 14, contentClipBounds.y, 14,
                contentClipBounds.height);
        scrollbarThumbBounds = scrollbarTrackBounds;
    }

    private int itemHeight(FontRenderer fontRenderer, ModernFormDefinition.Item item, int width) {
        if (item.getType() == ModernFormDefinition.ItemType.CUSTOM) {
            return ((ModernFormWidget) item.getValue()).height(width, contentClipBounds.height);
        }
        if (item.getType() == ModernFormDefinition.ItemType.CHOICE) {
            int available = Math.max(1, width - 18);
            int x = 0;
            int rows = 1;
            for (ModernFormSettingsTab.ChoiceOption<?> option : item.getOptions()) {
                int optionWidth = Math.min(available,
                        Math.max(42, fontRenderer.getStringWidth(translated(option.getLabel())) + 16));
                if (x > 0 && x + optionWidth > available) {
                    rows++;
                    x = 0;
                }
                x += optionWidth + 4;
            }
            return 23 + rows * 21;
        }
        return usesStackedLayout(item, width) ? 48 : 32;
    }

    private int computeFormHeight(FontRenderer fontRenderer, int width) {
        int height = 2;
        for (ModernFormDefinition.Section section : definition.getSections()) {
            if (!sectionSelected(section) || !sectionHasVisibleItems(section)) {
                continue;
            }
            height += 17;
            height = layoutSectionItems(fontRenderer, section, 0, height, width, 0, 0, false);
            height += 5;
        }
        return height;
    }

    private boolean compactCell(ModernFormDefinition.Item item) {
        switch (item.getType()) {
        case INTEGER: case DECIMAL: case TOGGLE: return true;
        case TEXT: return item.getMaxLength() <= 64;
        default: return false;
        }
    }

    private int layoutSectionItems(FontRenderer font, ModernFormDefinition.Section section, int x, int y,
            int width, int mouseX, int mouseY, boolean draw) {
        List<ModernFormDefinition.Item> visible = new ArrayList<>();
        for (ModernFormDefinition.Item item : section.getItems()) {
            if (item.isVisible()) visible.add(item);
            else if (draw) view(item).clear();
        }
        for (int i=0;i<visible.size();i++) {
            ModernFormDefinition.Item first=visible.get(i);
            boolean pair=sectionState!=null && width>=300 && compactCell(first) && i+1<visible.size()
                    && compactCell(visible.get(i+1)) && first.getType()==visible.get(i+1).getType();
            int cellWidth=pair?(width-6)/2:width;
            int height=itemHeight(font, first, cellWidth);
            if (pair) height=Math.max(height,itemHeight(font,visible.get(i+1),cellWidth));
            if (draw) {
                view(first).rowBounds=new ModernMainLayout.Rect(x,y,cellWidth,height);
                drawItem(font,first,view(first),mouseX,mouseY);
            }
            if (pair) {
                ModernFormDefinition.Item second=visible.get(++i);
                if (draw) {
                    view(second).rowBounds=new ModernMainLayout.Rect(x+cellWidth+6,y,width-cellWidth-6,height);
                    drawItem(font,second,view(second),mouseX,mouseY);
                }
            }
            y+=height+5;
        }
        return y;
    }

    private boolean usesStackedLayout(ModernFormDefinition.Item item, int width) {
        return item.getType() != ModernFormDefinition.ItemType.TOGGLE
                && item.getType() != ModernFormDefinition.ItemType.CHOICE && width < 260;
    }

    private int labelWidthFor(int rowWidth) {
        int controlWidth = Math.max(72, Math.min(170, rowWidth / 2));
        return Math.max(24, rowWidth - controlWidth - 28);
    }

    private ModernMainLayout.Rect controlBoundsFor(ModernFormDefinition.Item item, ModernMainLayout.Rect row,
            boolean compact) {
        if (compact) {
            return new ModernMainLayout.Rect(row.x + 9, row.bottom() - 25, Math.max(1, row.width - 18), 21);
        }
        int controlWidth = Math.max(72, Math.min(170, row.width / 2));
        return new ModernMainLayout.Rect(row.right() - controlWidth - 8, row.y + 5, controlWidth, 22);
    }

    private ModernMainLayout.Rect drawInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        tooltip = translated(tooltip);
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return null;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean inViewport = !drawingContent || contentClipBounds == null
                || contains(contentClipBounds, bounds);
        if (!inViewport) {
            return null;
        }
        ModernTooltipSupport.registerInfoIcon(Minecraft.getMinecraft().currentScreen, x, y, 11, 11, tooltip);
        boolean active = bounds.contains(mouseX, mouseY);
        if (active) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, active ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        return bounds;
    }

    private static boolean contains(ModernMainLayout.Rect outer, ModernMainLayout.Rect inner) {
        return outer != null && inner != null && inner.x >= outer.x && inner.y >= outer.y
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect contentBounds) {
        ModernMainLayout.Rect source = contentBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : contentBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 16));
        int insetY = Math.max(6, Math.min(9, source.height / 14));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private boolean sectionHasVisibleItems(ModernFormDefinition.Section section) {
        for (ModernFormDefinition.Item item : section.getItems()) {
            if (item.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void clearSectionBounds(ModernFormDefinition.Section section) {
        for (ModernFormDefinition.Item item : section.getItems()) {
            view(item).clear();
        }
    }

    private boolean statusIsVisible() {
        return !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
    }

    private static boolean looksLikeError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return message.contains("失败") || message.contains("错误") || message.contains("无效")
                || message.contains("不能") || message.contains("不存在") || lower.contains("fail")
                || lower.contains("error") || lower.contains("invalid") || lower.contains("cannot")
                || lower.contains("missing");
    }

    private static boolean looksLikeWarning(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return message.contains("再次") || message.contains("请先") || message.contains("未保存")
                || lower.contains("again") || lower.contains("unsaved") || lower.contains("first");
    }

    private static String translated(String value) {
        return ModernFormI18n.tr(value);
    }

    private static ModernTextField createTextField(FontRenderer fontRenderer, int maxLength) {
        ModernTextField field = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setMaxStringLength(Math.max(1, maxLength));
        return field;
    }

    private static String formatFloat(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.indexOf('.') >= 0 && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
