package com.zszl.zszlScriptMod.gui.modern.baritone;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernDropdown;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.block.Block;
import net.minecraft.util.text.TextFormatting;

/**
 * Native modern editor for all user-editable Baritone settings.
 *
 * <p>The runtime {@link Settings} registry is authoritative. The bundled
 * manifest only supplies friendly descriptions and the type filter uses the
 * runtime parser type, so custom settings and the project's added settings
 * remain visible without another hard-coded list.</p>
 */
public final class ModernBaritoneSettingsTab implements ModernSettingsTab {

    private static final String SETTINGS_RESOURCE = "baritone_settings.json";
    private static final int MAX_PAGE_ROWS = 8;
    private static final int CARD_HEIGHT = BaritoneSettingsLayout.CARD_HEIGHT;
    private static final int CARD_GAP = BaritoneSettingsLayout.CARD_GAP;
    private static final int PAGE_BUTTON_WIDTH = 52;
    private static final String[] TYPE_FILTERS = {
            "all", "boolean", "int", "long", "float", "double", "string", "list", "map", "color", "vec3i"
    };

    private final Gson gson = new GsonBuilder().create();
    private final List<SettingDef> allSettings = new ArrayList<>();
    private final List<SettingDef> filteredSettings = new ArrayList<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final Map<String, String> draftValues = new HashMap<>();
    private final Map<String, GuiTextField> valueFields = new HashMap<>();
    private final Map<String, ModernMainLayout.Rect> controlBounds = new HashMap<>();
    private final Map<String, ModernMainLayout.Rect> cardBounds = new HashMap<>();
    private final Map<String, NumericRange> numericRanges = new HashMap<>();
    private final ModernDropdown typeDropdown = new ModernDropdown(TYPE_FILTERS, TYPE_FILTERS);
    private final ModernDropdown modifiedDropdown = new ModernDropdown(
            new String[] { "all", "modified" }, new String[] { "gui.modern.baritone_set.u009", "gui.modern.baritone_set.u008" });
    private final ModernDropdown columnsDropdown = new ModernDropdown(
            new String[] { "0", "1", "2", "3", "4" }, new String[] {
                    "gui.modern.baritone_set.columns.auto", "gui.modern.baritone_set.columns.one",
                    "gui.modern.baritone_set.columns.two", "gui.modern.baritone_set.columns.three",
                    "gui.modern.baritone_set.columns.four" });
    private final ModernHoverScrollbar pageScrollbar = new ModernHoverScrollbar();

    private FontRenderer fontRenderer;
    private GuiTextField searchField;
    private ModernBaritoneBlockListEditor blockListEditor;

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect typeFilterBounds;
    private ModernMainLayout.Rect modifiedFilterBounds;
    private ModernMainLayout.Rect columnsBounds;
    private ModernMainLayout.Rect reloadBounds;
    private ModernMainLayout.Rect contentClipBounds;
    private ModernMainLayout.Rect previousBounds;
    private ModernMainLayout.Rect nextBounds;
    private ModernMainLayout.Rect resetBounds;
    private ModernMainLayout.Rect applyBounds;
    private ModernMainLayout.Rect saveBounds;

    private String searchText = "";
    private String typeFilter = "all";
    private boolean modifiedOnly;
    private boolean initialized;
    private int page;
    private int pageCount = 1;
    private int columns = 2;
    private int rows = MAX_PAGE_ROWS;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private int lastMouseX;
    private int lastMouseY;
    private String draggingNumericKey;

    private static final class Manifest {
        private List<ManifestSetting> settings;
    }

    private static final class ManifestSetting {
        private String key;
        private String desc;
    }

    private static final class SettingDef {
        private final String key;
        private final String type;
        private final String description;
        private final Settings.Setting<?> setting;

        private SettingDef(String key, String type, String description, Settings.Setting<?> setting) {
            this.key = key == null ? "" : key;
            this.type = type == null ? "string" : type;
            this.description = description == null || description.trim().isEmpty() ? this.key : description;
            this.setting = setting;
        }
    }

    private static final class NumericRange {
        private double min;
        private double max;

        private NumericRange(double min, double max) {
            this.min = min;
            this.max = max;
        }

        private void include(double value) {
            if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
    }

    public static ModernSettingsTab create() {
        return new ModernBaritoneSettingsTab();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        this.fontRenderer = fontRenderer;
        loadDescriptions();
        loadRuntimeSettings();
        searchField = createField(128);
        initialized = true;
        reloadDraftFromRuntime();
        refreshFilteredSettings();
    }

    private GuiTextField createField(int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setMaxStringLength(maxLength);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    private void loadDescriptions() {
        try (InputStream input = ModernBaritoneSettingsTab.class.getClassLoader()
                .getResourceAsStream(SETTINGS_RESOURCE)) {
            if (input == null) {
                return;
            }
            Manifest manifest;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                manifest = gson.fromJson(reader, Manifest.class);
            }
            if (manifest == null || manifest.settings == null) {
                return;
            }
            for (ManifestSetting setting : manifest.settings) {
                if (setting != null && setting.key != null && !setting.key.trim().isEmpty()) {
                    descriptions.put(setting.key.toLowerCase(Locale.ROOT), safe(setting.desc));
                }
            }
        } catch (IOException | JsonSyntaxException ignored) {
            // The runtime registry still provides a complete editable page.
        }
    }

    private void loadRuntimeSettings() {
        allSettings.clear();
        Settings settings = settings();
        for (Settings.Setting<?> setting : settings.allSettings) {
            if (setting == null || setting.isJavaOnly()) {
                continue;
            }
            String key = setting.getName();
            String type;
            try {
                type = SettingsUtil.settingTypeToString(setting);
            } catch (Exception ignored) {
                type = setting.getValueClass() == null ? "string" : setting.getValueClass().getSimpleName();
            }
            String description = descriptions.get(key.toLowerCase(Locale.ROOT));
            allSettings.add(new SettingDef(key, type, description, setting));
        }
        allSettings.sort(Comparator.comparing(def -> def.key.toLowerCase(Locale.ROOT)));
    }

    private Settings settings() {
        Settings settings = BaritoneAPI.getSettings();
        if (settings == null) {
            throw new IllegalStateException(ModernFormI18n.tr("gui.modern.baritone_set.u001"));
        }
        return settings;
    }

    private void reloadDraftFromRuntime() {
        syncFieldsToDraft();
        draftValues.clear();
        numericRanges.clear();
        for (SettingDef def : allSettings) {
            draftValues.put(def.key, currentValue(def));
        }
        clearFieldFocus();
        showStatus("gui.modern.baritone_set.u002", false);
    }

    private void refreshFilteredSettings() {
        syncFieldsToDraft();
        filteredSettings.clear();
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        for (SettingDef def : allSettings) {
            if (!matchesType(def) || modifiedOnly && !isModified(def)) {
                continue;
            }
            String searchable = (def.key + " " + def.description + " " + def.type).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) {
                continue;
            }
            filteredSettings.add(def);
        }
        recalculatePaging();
    }

    private void recalculatePaging() {
        int pageSize = pageSize();
        pageCount = Math.max(1, (filteredSettings.size() + pageSize - 1) / pageSize);
        page = clamp(page, 0, pageCount - 1);
    }

    private boolean matchesType(SettingDef def) {
        if ("all".equals(typeFilter)) {
            return true;
        }
        return baseType(def.type).equals(typeFilter);
    }

    private String baseType(String type) {
        String normalized = safe(type).toLowerCase(Locale.ROOT);
        int genericStart = normalized.indexOf('<');
        return genericStart < 0 ? normalized : normalized.substring(0, genericStart);
    }

    private boolean isModified(SettingDef def) {
        String current = normalizeValue(def, draftValues.get(def.key));
        String defaultValue = normalizeValue(def, defaultValue(def));
        if (isBoolean(def)) {
            return Boolean.parseBoolean(current) != Boolean.parseBoolean(defaultValue);
        }
        return !current.equalsIgnoreCase(defaultValue);
    }

    private String currentValue(SettingDef def) {
        try {
            return SettingsUtil.settingValueToString(def.setting);
        } catch (Exception ignored) {
            return String.valueOf(def.setting.value);
        }
    }

    private String defaultValue(SettingDef def) {
        try {
            return SettingsUtil.settingDefaultToString(def.setting);
        } catch (Exception ignored) {
            return String.valueOf(def.setting.defaultValue);
        }
    }

    private String displayValue(SettingDef def) {
        return displayValue(def, draftValues.get(def.key));
    }

    private String displayValue(SettingDef def, String rawValue) {
        String value = safe(rawValue);
        if (isBoolean(def)) {
            return ModernFormI18n.tr(Boolean.parseBoolean(value)
                    ? "gui.modern.baritone_set.u003" : "gui.modern.baritone_set.u004");
        }
        return value.isEmpty() ? ModernFormI18n.tr("gui.modern.baritone_set.u005") : value;
    }

    private boolean isBoolean(SettingDef def) {
        return "boolean".equals(baseType(def.type));
    }

    private boolean isNumeric(SettingDef def) {
        String type = baseType(def.type);
        return isIntegralType(type) || isFloatingType(type);
    }

    private static boolean isIntegralType(String type) {
        return "int".equals(type) || "integer".equals(type) || "long".equals(type);
    }

    private static boolean isFloatingType(String type) {
        return "float".equals(type) || "double".equals(type);
    }

    private NumericRange numericRange(SettingDef def) {
        double current = parseNumeric(draftValues.get(def.key));
        double initial = parseNumeric(defaultValue(def));
        if (Double.isNaN(current)) {
            current = Double.isNaN(initial) ? 0D : initial;
        }
        if (Double.isNaN(initial)) {
            initial = current;
        }
        NumericRange range = numericRanges.get(def.key);
        if (range == null) {
            boolean integral = isIntegralType(baseType(def.type));
            double anchor = Math.max(Math.abs(current), Math.abs(initial));
            double span = Math.max(integral ? 10D : 1D, anchor);
            double minimum = Math.min(current, initial);
            double maximum = Math.max(current, initial);
            if (minimum >= 0D && !def.description.contains("-1")) {
                minimum = 0D;
            } else {
                minimum -= span;
            }
            maximum += span;
            if (!(maximum > minimum)) {
                maximum = minimum + (integral ? 10D : 1D);
            }
            range = new NumericRange(minimum, maximum);
            numericRanges.put(def.key, range);
        }
        range.include(current);
        if (!(range.max > range.min)) {
            range.max = range.min + 1D;
        }
        return range;
    }

    private static double parseNumeric(String raw) {
        String value = safe(raw).trim();
        if (!value.isEmpty()) {
            char suffix = value.charAt(value.length() - 1);
            if (suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D'
                    || suffix == 'l' || suffix == 'L') {
                value = value.substring(0, value.length() - 1).trim();
            }
        }
        try {
            return value.isEmpty() ? Double.NaN : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private String formatNumeric(SettingDef def, double value) {
        String type = baseType(def.type);
        if ("int".equals(type) || "integer".equals(type)) {
            return String.valueOf(Math.round(Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value))));
        }
        if ("long".equals(type)) {
            return String.valueOf(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.4f", value);
        while (text.indexOf('.') >= 0 && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private void updateNumericFromMouse(SettingDef def, ModernMainLayout.Rect bounds, int mouseX) {
        NumericRange range = numericRange(def);
        double fraction = (mouseX - bounds.x - 8D) / Math.max(1D, bounds.width - 16D);
        double value = range.min + Math.max(0D, Math.min(1D, fraction)) * (range.max - range.min);
        String formatted = formatNumeric(def, value);
        draftValues.put(def.key, formatted);
        GuiTextField field = valueFields.get(def.key);
        if (field != null) {
            field.setText(formatted);
        }
    }

    private boolean isBlockList(SettingDef def) {
        if (def == null || !(def.setting.getType() instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterized = (ParameterizedType) def.setting.getType();
        Type rawType = parameterized.getRawType();
        Type[] arguments = parameterized.getActualTypeArguments();
        if (!(rawType instanceof Class) || arguments.length != 1
                || !(arguments[0] instanceof Class)) {
            return false;
        }
        Class<?> elementType = (Class<?>) arguments[0];
        // acceptableThrowawayItems is a List<Item>, but its user-facing
        // contract is still a list of placeable blocks and the parser accepts
        // the corresponding block IDs.
        return List.class.isAssignableFrom((Class<?>) rawType)
                && (Block.class.isAssignableFrom(elementType)
                        || ("acceptableThrowawayItems".equalsIgnoreCase(def.key)
                                && net.minecraft.item.Item.class.isAssignableFrom(elementType)));
    }

    private String normalizedBlockListValue(SettingDef def) {
        return normalizeValue(def, draftValues.get(def.key));
    }

    private boolean isEnum(SettingDef def) {
        try {
            return def.setting.getType() instanceof Class
                    && Enum.class.isAssignableFrom((Class<?>) def.setting.getType());
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> enumValues(SettingDef def) {
        if (!isEnum(def)) {
            return Collections.emptyList();
        }
        Object[] constants = ((Class<?>) def.setting.getType()).getEnumConstants();
        List<String> result = new ArrayList<>();
        if (constants != null) {
            for (Object constant : constants) {
                result.add(((Enum<?>) constant).name());
            }
        }
        return result;
    }

    @Override
    public void updateScreen() {
        if (!initialized) {
            return;
        }
        if (blockListEditor != null) {
            blockListEditor.updateScreen();
            return;
        }
        syncFieldsToDraft();
        if (searchField != null) {
            searchField.updateCursorCounter();
            String next = safe(searchField.getText());
            if (!next.equals(searchText)) {
                searchText = next;
                page = 0;
                refreshFilteredSettings();
            }
        }
        for (GuiTextField field : valueFields.values()) {
            field.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        this.fontRenderer = fontRenderer;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        syncFieldsToDraft();
        contentBounds = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requestedBounds;
        panelBounds = safePanel(contentBounds);
        hoveredTooltip = "";

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawSearch(mouseX, mouseY);

        boolean compact = panelBounds.width < 470;
        int toolbarY = panelBounds.y + 41;
        drawToolbar(toolbarY, mouseX, mouseY);
        int footerY = panelBounds.bottom() - (compact ? 58 : 32);
        int listY = toolbarY + (panelBounds.width < 670 ? 58 : 31);
        ModernMainLayout.Rect listViewport = new ModernMainLayout.Rect(panelBounds.x + 8, listY,
                Math.max(1, panelBounds.width - 16), Math.max(1, footerY - listY - 7));
        contentClipBounds = ModernHoverScrollbar.contentBounds(listViewport);
        int firstVisible = page * pageSize();
        int nextColumns = BaritoneSettingsLayout.chooseColumns(contentClipBounds.width, Integer.parseInt(columnsDropdown.value()));
        int nextRows = BaritoneSettingsLayout.visibleRows(contentClipBounds.height);
        if (columns != nextColumns || rows != nextRows) {
            columns = nextColumns;
            rows = nextRows;
            page = firstVisible / pageSize();
            clearFieldFocus();
        }
        recalculatePaging();
        drawCards(mouseX, mouseY);
        pageScrollbar.draw(listViewport, page, Math.max(0, pageCount - 1), 1, pageCount,
                mouseX, mouseY, value -> {
                    syncFieldsToDraft();
                    page = clamp(value, 0, pageCount - 1);
                    clearFieldFocus();
                });
        drawFooter(footerY, mouseX, mouseY);
        typeDropdown.drawMenu(fontRenderer, panelBounds, mouseX, mouseY);
        modifiedDropdown.drawMenu(fontRenderer, panelBounds, mouseX, mouseY);
        columnsDropdown.drawMenu(fontRenderer, panelBounds, mouseX, mouseY);
        if (blockListEditor != null) {
            blockListEditor.draw(contentBounds, mouseX, mouseY);
            if (blockListEditor.isClosed()) {
                blockListEditor = null;
            }
        }
    }

    private void drawSearch(int mouseX, int mouseY) {
        searchBounds = new ModernMainLayout.Rect(panelBounds.x + 12, panelBounds.y + 10,
                Math.max(1, panelBounds.width - 24), 25);
        drawSearchField(mouseX, mouseY);
    }

    private void drawToolbar(int y, int mouseX, int mouseY) {
        int x = panelBounds.x + 12;
        if (panelBounds.width < 670) {
            int width = Math.max(1, panelBounds.width - 24);
            int half = Math.max(1, (width - 6) / 2);
            typeFilterBounds = new ModernMainLayout.Rect(x, y, half, 23);
            modifiedFilterBounds = new ModernMainLayout.Rect(typeFilterBounds.right() + 6, y, half, 23);
            columnsBounds = new ModernMainLayout.Rect(x, y + 28, half, 23);
            reloadBounds = new ModernMainLayout.Rect(columnsBounds.right() + 6, y + 28, half, 23);
            typeDropdown.drawButton(fontRenderer, typeFilterBounds, mouseX, mouseY);
            modifiedDropdown.drawButton(fontRenderer, modifiedFilterBounds, mouseX, mouseY);
            columnsDropdown.drawButton(fontRenderer, columnsBounds, mouseX, mouseY);
            drawButton(reloadBounds, "gui.modern.baritone_set.u010", false, reloadBounds.contains(mouseX, mouseY));
            return;
        }
        typeFilterBounds = new ModernMainLayout.Rect(x, y, 106, 23);
        modifiedFilterBounds = new ModernMainLayout.Rect(typeFilterBounds.right() + 6, y, 112, 23);
        reloadBounds = new ModernMainLayout.Rect(modifiedFilterBounds.right() + 6, y, 52, 23);
        columnsBounds = new ModernMainLayout.Rect(reloadBounds.right() + 6, y, 138, 23);
        typeDropdown.drawButton(fontRenderer, typeFilterBounds, mouseX, mouseY);
        modifiedDropdown.drawButton(fontRenderer, modifiedFilterBounds, mouseX, mouseY);
        columnsDropdown.drawButton(fontRenderer, columnsBounds, mouseX, mouseY);
        drawButton(reloadBounds, "gui.modern.baritone_set.u010", false, reloadBounds.contains(mouseX, mouseY));

        String status = ModernFormI18n.tr("gui.modern.baritone_set.fmt.page",
                String.valueOf(filteredSettings.size()), String.valueOf(allSettings.size()));
        ModernUiRenderer.drawText(fontRenderer, status, columnsBounds.right() + 12, y + 7,
                ModernUiRenderer.MUTED_TEXT, Math.max(1, panelBounds.right() - columnsBounds.right() - 24));
    }

    private String typeLabel() {
        return "all".equals(typeFilter) ? ModernFormI18n.tr("gui.modern.baritone_set.u011") : typeFilter;
    }

    private void drawCards(int mouseX, int mouseY) {
        for (GuiTextField field : valueFields.values()) {
            field.setVisible(false);
        }
        controlBounds.clear();
        cardBounds.clear();
        int pageSize = pageSize();
        int start = page * pageSize;
        int end = Math.min(filteredSettings.size(), start + pageSize);
        int gap = CARD_GAP;
        int cardWidth = Math.max(1, (contentClipBounds.width - gap * (columns - 1) - 12) / columns);
        ModernUiRenderer.beginClip(contentClipBounds);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int column = local % columns;
            int row = local / columns;
            int cardX = contentClipBounds.x + 6 + column * (cardWidth + gap);
            int cardY = contentClipBounds.y + 5 + row * (CARD_HEIGHT + gap);
            drawCard(filteredSettings.get(index), new ModernMainLayout.Rect(cardX, cardY, cardWidth, CARD_HEIGHT),
                    mouseX, mouseY);
        }
        if (filteredSettings.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_set.u012", contentClipBounds.x + 14,
                    contentClipBounds.y + 18, ModernUiRenderer.MUTED_TEXT,
                    Math.max(40, contentClipBounds.width - 28));
        }
        ModernUiRenderer.endClip();
    }

    private void drawCard(SettingDef def, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY) && contentClipBounds.contains(mouseX, mouseY);
        boolean modified = isModified(def);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                modified ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        cardBounds.put(def.key, bounds);
        ModernUiRenderer.drawText(fontRenderer, def.description, bounds.x + 8, bounds.y + 6,
                ModernUiRenderer.TEXT, Math.max(24, bounds.width - 42));
        ModernUiRenderer.drawText(fontRenderer, def.key, bounds.x + 8, bounds.y + 21,
                modified ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT,
                Math.max(24, bounds.width - 52));
        ModernUiRenderer.drawText(fontRenderer, def.type, bounds.right() - 42, bounds.y + 21,
                ModernUiRenderer.MUTED_TEXT, 34);
        if (modified) {
            ModernUiRenderer.drawStatusDot(bounds.right() - 13, bounds.y + 10, ModernUiRenderer.WARNING);
        }

        ModernMainLayout.Rect control = new ModernMainLayout.Rect(bounds.x + 7, bounds.y + 38,
                Math.max(1, bounds.width - 14), 21);
        controlBounds.put(def.key, control);
        if (isBlockList(def)) {
            drawButton(control, ModernFormI18n.tr("gui.modern.baritone_set.fmt.visual",
                    String.valueOf(ModernBaritoneBlockListEditor.count(normalizedBlockListValue(def)))),
                    false, control.contains(mouseX, mouseY));
        } else if (isBoolean(def)) {
            drawButton(control, displayValue(def), Boolean.parseBoolean(draftValues.get(def.key)),
                    control.contains(mouseX, mouseY));
        } else if (isEnum(def)) {
            drawButton(control, displayValue(def) + "  ▼", false, control.contains(mouseX, mouseY));
        } else {
            drawValueField(def, control, mouseX, mouseY);
        }
        if (hovered) {
            hoveredTooltip = ModernFormI18n.tr("gui.modern.baritone_set.fmt.tip",
                    safe(def.description), safe(def.type), displayValue(def), displayValue(def, defaultValue(def)));
        }
    }

    private void drawValueField(SettingDef def, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        GuiTextField field = valueFields.get(def.key);
        if (field == null) {
            field = createField(32767);
            valueFields.put(def.key, field);
        }
        String value = safe(draftValues.get(def.key));
        if (!field.isFocused() && !value.equals(field.getText())) {
            field.setText(value);
        }
        field.setVisible(true);
        field.setEnabled(true);
        field.x = bounds.x + 6;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 12);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                field.isFocused() ? ModernUiRenderer.ACCENT
                        : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        if (isNumeric(def)) {
            drawNumericSlider(def, bounds, mouseX, mouseY);
        }
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void drawNumericSlider(SettingDef def, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        NumericRange range = numericRange(def);
        double value = parseNumeric(draftValues.get(def.key));
        if (Double.isNaN(value)) {
            value = range.min;
        }
        float fraction = (float) Math.max(0D, Math.min(1D,
                (value - range.min) / Math.max(1.0E-9D, range.max - range.min)));
        boolean hovered = numericSliderHit(bounds, mouseX, mouseY);
        int trackX = bounds.x + 8;
        int trackW = Math.max(4, bounds.width - 16);
        int trackHeight = hovered ? 4 : 2;
        int trackY = bounds.bottom() - (hovered ? 5 : 3);
        int trackColor = hovered ? ModernUiRenderer.BORDER : 0xFF43515C;
        int fillColor = hovered ? ModernUiRenderer.ACCENT : 0xFF58B6D8;
        int filledWidth = Math.max(0, Math.min(trackW, Math.round(trackW * fraction)));
        net.minecraft.client.gui.Gui.drawRect(trackX, trackY, trackX + trackW, trackY + trackHeight, trackColor);
        if (filledWidth > 0) {
            net.minecraft.client.gui.Gui.drawRect(trackX, trackY, trackX + filledWidth, trackY + trackHeight,
                    fillColor);
        }
        if (hovered) {
            int thumbX = trackX + Math.round(trackW * fraction);
            net.minecraft.client.gui.Gui.drawRect(thumbX - 3, trackY, thumbX + 3, trackY + trackHeight,
                    ModernUiRenderer.TEXT);
            net.minecraft.client.gui.Gui.drawRect(thumbX - 2, trackY, thumbX + 2, trackY + trackHeight,
                    fillColor);
        }
    }

    private static boolean numericSliderHit(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        if (bounds == null || mouseX < bounds.x || mouseX >= bounds.right()
                || mouseY < bounds.bottom() - 8 || mouseY >= bounds.bottom()) {
            return false;
        }
        int trackX = bounds.x + 8;
        int trackW = Math.max(4, bounds.width - 16);
        return mouseX >= trackX - 2 && mouseX < trackX + trackW + 2;
    }

    private void drawFooter(int y, int mouseX, int mouseY) {
        ModernUiRenderer.drawDivider(panelBounds.x + 10, y - 7, Math.max(1, panelBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        if (panelBounds.width < 470) {
            int width = Math.max(1, panelBounds.width - 24);
            int gap = 5;
            int buttonWidth = Math.max(1, (width - gap * 2) / 3);
            previousBounds = new ModernMainLayout.Rect(panelBounds.x + 12, y, buttonWidth, 22);
            nextBounds = new ModernMainLayout.Rect(previousBounds.right() + gap, y, buttonWidth, 22);
            resetBounds = new ModernMainLayout.Rect(nextBounds.right() + gap, y, buttonWidth, 22);
            applyBounds = new ModernMainLayout.Rect(panelBounds.x + 12, y + 28, buttonWidth, 22);
            saveBounds = new ModernMainLayout.Rect(applyBounds.right() + gap, y + 28, buttonWidth, 22);
            drawButton(previousBounds, "gui.modern.baritone_set.u013", false, page > 0 && previousBounds.contains(mouseX, mouseY));
            drawButton(nextBounds, "gui.modern.baritone_set.u014", false, page + 1 < pageCount && nextBounds.contains(mouseX, mouseY));
            drawButton(resetBounds, "gui.modern.baritone_set.u015", false, resetBounds.contains(mouseX, mouseY));
            drawButton(applyBounds, "gui.modern.baritone_set.u016", false, applyBounds.contains(mouseX, mouseY));
            drawButton(saveBounds, "gui.modern.baritone_set.u017", true, saveBounds.contains(mouseX, mouseY));
            ModernUiRenderer.drawText(fontRenderer, (page + 1) + "/" + pageCount, saveBounds.right() + gap, y + 35,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, panelBounds.right() - saveBounds.right() - gap - 12));
            return;
        }
        previousBounds = new ModernMainLayout.Rect(panelBounds.x + 12, y, PAGE_BUTTON_WIDTH, 22);
        nextBounds = new ModernMainLayout.Rect(previousBounds.right() + 5, y, PAGE_BUTTON_WIDTH, 22);
        resetBounds = new ModernMainLayout.Rect(panelBounds.right() - 270, y, 78, 22);
        applyBounds = new ModernMainLayout.Rect(resetBounds.right() + 6, y, 78, 22);
        saveBounds = new ModernMainLayout.Rect(applyBounds.right() + 6, y, 78, 22);
        drawButton(previousBounds, "gui.modern.baritone_set.u013", false, page > 0 && previousBounds.contains(mouseX, mouseY));
        drawButton(nextBounds, "gui.modern.baritone_set.u014", false, page + 1 < pageCount && nextBounds.contains(mouseX, mouseY));
        drawButton(resetBounds, "gui.modern.baritone_set.u018", false, resetBounds.contains(mouseX, mouseY));
        drawButton(applyBounds, "gui.modern.baritone_set.u019", false, applyBounds.contains(mouseX, mouseY));
        drawButton(saveBounds, "gui.modern.baritone_set.u020", true, saveBounds.contains(mouseX, mouseY));
        String pageText = (page + 1) + "/" + pageCount;
        ModernUiRenderer.drawText(fontRenderer, pageText, panelBounds.x + 132, y + 7, ModernUiRenderer.MUTED_TEXT, 46);
        if (statusVisible()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, panelBounds.x + 185, y + 7,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(40, resetBounds.x - panelBounds.x - 195));
        }
    }

    private void drawSearchField(int mouseX, int mouseY) {
        boolean focused = searchField != null && searchField.isFocused();
        boolean hovered = searchBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                0xFF101820, focused ? ModernUiRenderer.ACCENT
                        : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        if (searchBounds.width >= 23) {
            ModernUiRenderer.drawSearchIcon(searchBounds.x + 7, searchBounds.y + 7, ModernUiRenderer.SUBTLE_TEXT);
        }
        searchField.setVisible(true);
        searchField.setEnabled(true);
        searchField.x = searchBounds.x + 23;
        searchField.y = searchBounds.y + (searchBounds.height - fontRenderer.FONT_HEIGHT) / 2;
        searchField.width = Math.max(1, searchBounds.width - 29);
        searchField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);
        if (safe(searchField.getText()).isEmpty() && !focused) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.baritone_set.u021", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, searchField.width));
        }
    }

    private void drawButton(ModernMainLayout.Rect bounds, String label, boolean primary, boolean hovered) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                primary ? (hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT)
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                primary ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, Math.max(8, bounds.width - 12));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (blockListEditor != null) {
            blockListEditor.mouseClicked(mouseX, mouseY, mouseButton);
            if (blockListEditor.isClosed()) {
                blockListEditor = null;
            }
            return true;
        }
        if (mouseButton != 0) {
            return true;
        }
        draggingNumericKey = null;
        if (columnsDropdown.isOpen()) {
            syncFieldsToDraft();
            clearFieldFocus();
            return columnsDropdown.mouseClicked(mouseX, mouseY);
        }
        if (typeDropdown.isOpen()) {
            boolean handled = typeDropdown.mouseClicked(mouseX, mouseY);
            String selected = typeDropdown.value();
            if (!selected.equals(typeFilter)) {
                typeFilter = selected;
                page = 0;
                refreshFilteredSettings();
            }
            return handled;
        }
        if (modifiedDropdown.isOpen()) {
            boolean handled = modifiedDropdown.mouseClicked(mouseX, mouseY);
            boolean selected = "modified".equals(modifiedDropdown.value());
            if (selected != modifiedOnly) {
                syncFieldsToDraft();
                modifiedOnly = selected;
                page = 0;
                refreshFilteredSettings();
            }
            return handled;
        }
        if (contains(searchBounds, mouseX, mouseY)) {
            clearFieldFocus();
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(typeFilterBounds, mouseX, mouseY)) {
            typeDropdown.mouseClicked(mouseX, mouseY);
            String selected = typeDropdown.value();
            if (!selected.equals(typeFilter)) {
                typeFilter = selected;
                page = 0;
                refreshFilteredSettings();
            }
            return true;
        }
        if (contains(modifiedFilterBounds, mouseX, mouseY)) {
            modifiedDropdown.mouseClicked(mouseX, mouseY);
            boolean selected = "modified".equals(modifiedDropdown.value());
            if (selected != modifiedOnly) {
                syncFieldsToDraft();
                modifiedOnly = selected;
                page = 0;
                refreshFilteredSettings();
            }
            return true;
        }
        if (contains(columnsBounds, mouseX, mouseY)) {
            clearFieldFocus();
            columnsDropdown.mouseClicked(mouseX, mouseY);
            return true;
        }
        if (pageScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (contains(reloadBounds, mouseX, mouseY)) {
            reloadDraftFromRuntime();
            refreshFilteredSettings();
            return true;
        }
        if (contains(previousBounds, mouseX, mouseY) && page > 0) {
            syncFieldsToDraft();
            page--;
            clearFieldFocus();
            return true;
        }
        if (contains(nextBounds, mouseX, mouseY) && page + 1 < pageCount) {
            syncFieldsToDraft();
            page++;
            clearFieldFocus();
            return true;
        }
        if (contains(resetBounds, mouseX, mouseY)) {
            resetPageToDefaults();
            return true;
        }
        if (contains(applyBounds, mouseX, mouseY)) {
            applyPage();
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            saveAll();
            return true;
        }
        for (SettingDef def : pageSettings()) {
            ModernMainLayout.Rect control = controlBounds.get(def.key);
            if (!contains(contentClipBounds, mouseX, mouseY) || !contains(control, mouseX, mouseY)) {
                continue;
            }
            if (isBlockList(def)) {
                openBlockListEditor(def);
                return true;
            }
            if (isBoolean(def)) {
                draftValues.put(def.key, String.valueOf(!Boolean.parseBoolean(draftValues.get(def.key))));
                return true;
            }
            if (isEnum(def)) {
                cycleEnum(def);
                return true;
            }
            if (isNumeric(def) && numericSliderHit(control, mouseX, mouseY)) {
                draggingNumericKey = def.key;
                clearFieldFocus();
                updateNumericFromMouse(def, control, mouseX);
                return true;
            }
            GuiTextField field = valueFields.get(def.key);
            if (field != null) {
                clearFieldFocus();
                field.setFocused(true);
                field.mouseClicked(mouseX, mouseY, mouseButton);
                return true;
            }
        }
        clearFieldFocus();
        return true;
    }

    private List<SettingDef> pageSettings() {
        int pageSize = pageSize();
        int start = Math.min(filteredSettings.size(), page * pageSize);
        int end = Math.min(filteredSettings.size(), start + pageSize);
        return filteredSettings.subList(start, end);
    }

    private void openBlockListEditor(final SettingDef def) {
        syncFieldsToDraft();
        blockListEditor = new ModernBaritoneBlockListEditor(def.key, def.description,
                draftValues.get(def.key), defaultValue(def), value -> {
                    draftValues.put(def.key, value == null ? "" : value);
                    showStatus(ModernFormI18n.tr("gui.modern.baritone_set.fmt.draft_updated", def.key), false);
                });
        blockListEditor.ensureInitialized(fontRenderer);
        clearFieldFocus();
    }

    private void cycleEnum(SettingDef def) {
        List<String> values = enumValues(def);
        if (values.isEmpty()) {
            return;
        }
        String current = safe(draftValues.get(def.key));
        int index = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        draftValues.put(def.key, values.get((index + 1) % values.size()));
    }

    private void resetPageToDefaults() {
        syncFieldsToDraft();
        for (SettingDef def : pageSettings()) {
            draftValues.put(def.key, normalizeValue(def, defaultValue(def)));
        }
        clearFieldFocus();
        showStatus("gui.modern.baritone_set.u022", false);
    }

    private void applyPage() {
        syncFieldsToDraft();
        int applied = 0;
        int failed = 0;
        for (SettingDef def : pageSettings()) {
            if (applySetting(def)) {
                applied++;
            } else {
                failed++;
            }
        }
        SettingsUtil.save(settings());
        showStatus(failed == 0
                ? ModernFormI18n.tr("gui.modern.baritone_set.fmt.applied_ok", String.valueOf(applied))
                : ModernFormI18n.tr("gui.modern.baritone_set.fmt.applied_fail", String.valueOf(applied), String.valueOf(failed)),
                failed > 0);
        refreshFilteredSettings();
    }

    @Override
    public void save() {
        saveAll();
    }

    private void saveAll() {
        syncFieldsToDraft();
        int applied = 0;
        int failed = 0;
        for (SettingDef def : allSettings) {
            if (applySetting(def)) {
                applied++;
            } else {
                failed++;
            }
        }
        SettingsUtil.save(settings());
        showStatus(failed == 0
                ? ModernFormI18n.tr("gui.modern.baritone_set.fmt.saved_ok", String.valueOf(applied))
                : ModernFormI18n.tr("gui.modern.baritone_set.fmt.saved_fail", String.valueOf(applied), String.valueOf(failed)),
                failed > 0);
        refreshFilteredSettings();
    }

    private boolean applySetting(SettingDef def) {
        try {
            SettingsUtil.parseAndApply(settings(), def.key.toLowerCase(Locale.ROOT),
                    normalizeValue(def, draftValues.get(def.key)));
            draftValues.put(def.key, currentValue(def));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void syncFieldsToDraft() {
        for (Map.Entry<String, GuiTextField> entry : valueFields.entrySet()) {
            GuiTextField field = entry.getValue();
            if (field != null && field.getVisible()) {
                draftValues.put(entry.getKey(), safe(field.getText()));
            }
        }
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (blockListEditor != null && blockListEditor.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                timeSinceLastClick)) {
            return true;
        }
        if (draggingNumericKey != null && clickedMouseButton == 0) {
            SettingDef def = findSetting(draggingNumericKey);
            ModernMainLayout.Rect bounds = controlBounds.get(draggingNumericKey);
            if (def == null || bounds == null) {
                draggingNumericKey = null;
                return false;
            }
            updateNumericFromMouse(def, bounds, mouseX);
            return true;
        }
        if (pageScrollbar.isDragging()) {
            pageScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (blockListEditor != null && blockListEditor.mouseReleased(mouseX, mouseY, state)) {
            return true;
        }
        if (draggingNumericKey != null && state == 0) {
            draggingNumericKey = null;
            return true;
        }
        if (pageScrollbar.isDragging()) {
            pageScrollbar.endDrag();
            return true;
        }
        return false;
    }

    private SettingDef findSetting(String key) {
        for (SettingDef def : allSettings) {
            if (def.key.equals(key)) {
                return def;
            }
        }
        return null;
    }

    private String normalizeValue(SettingDef def, String value) {
        String normalized = safe(value).trim();
        String type = baseType(def.type);
        if ("list".equals(type) || "map".equals(type)) {
            if (normalized.length() >= 2
                    && (normalized.startsWith("[") && normalized.endsWith("]")
                            || normalized.startsWith("{") && normalized.endsWith("}"))) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        if ("color".equals(type)) {
            try {
                java.lang.reflect.Field field = Color.class.getField(normalized.toUpperCase(Locale.ROOT));
                Object object = field.get(null);
                if (object instanceof Color) {
                    Color color = (Color) object;
                    return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
                }
            } catch (ReflectiveOperationException ignored) {
                // The parser will report malformed color input on apply.
            }
        }
        return normalized;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (blockListEditor != null) {
            blockListEditor.keyTyped(typedChar, keyCode);
            if (blockListEditor.isClosed()) {
                blockListEditor = null;
            }
            return true;
        }
        if (searchField != null && searchField.isFocused()
                && searchField.textboxKeyTyped(typedChar, keyCode)) {
            searchText = safe(searchField.getText());
            page = 0;
            refreshFilteredSettings();
            return true;
        }
        for (GuiTextField field : valueFields.values()) {
            if (field.isFocused() && field.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            applyPage();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (blockListEditor != null) {
            blockListEditor.handleMouseWheel(wheel, lastMouseX, lastMouseY);
            return true;
        }
        if (wheel == 0) {
            return false;
        }
        if (columnsDropdown.isOpen() || typeDropdown.isOpen() || modifiedDropdown.isOpen()) {
            return true;
        }
        if (wheel > 0 && page > 0) {
            syncFieldsToDraft();
            page--;
            clearFieldFocus();
            return true;
        }
        if (wheel < 0 && page + 1 < pageCount) {
            syncFieldsToDraft();
            page++;
            clearFieldFocus();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (blockListEditor != null) {
            blockListEditor.handleMouseWheel(wheel, mouseX, mouseY);
            return true;
        }
        if (contentClipBounds != null && contentClipBounds.contains(mouseX, mouseY)) {
            return handleMouseWheel(wheel);
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (blockListEditor != null) {
            blockListEditor.keyTyped((char) 0, Keyboard.KEY_ESCAPE);
            blockListEditor = null;
            return true;
        }
        if (columnsDropdown.isOpen() || typeDropdown.isOpen() || modifiedDropdown.isOpen()) {
            columnsDropdown.close();
            typeDropdown.close();
            modifiedDropdown.close();
            return true;
        }
        if (searchField != null && !safe(searchField.getText()).isEmpty()) {
            searchField.setText("");
            searchText = "";
            page = 0;
            refreshFilteredSettings();
            clearFieldFocus();
            return true;
        }
        for (GuiTextField field : valueFields.values()) {
            if (field.isFocused()) {
                clearFieldFocus();
                return true;
            }
        }
        return false;
    }

    private void clearFieldFocus() {
        if (searchField != null) {
            searchField.setFocused(false);
        }
        for (GuiTextField field : valueFields.values()) {
            field.setFocused(false);
        }
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contentBounds != null && contentBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        if (!initialized) {
            return;
        }
        blockListEditor = null;
        reloadDraftFromRuntime();
        refreshFilteredSettings();
    }

    @Override
    public boolean isDirty() {
        if (blockListEditor != null) {
            return true;
        }
        for (SettingDef def : allSettings) {
            String draft = normalizeValue(def, draftValues.get(def.key));
            String current = normalizeValue(def, currentValue(def));
            if (!draft.equals(current)) {
                return true;
            }
        }
        for (Map.Entry<String, GuiTextField> entry : valueFields.entrySet()) {
            GuiTextField field = entry.getValue();
            if (field != null && field.getVisible()
                    && !safe(field.getText()).equals(safe(draftValues.get(entry.getKey())))) {
                return true;
            }
        }
        return false;
    }

    private boolean statusVisible() {
        return !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
    }

    private void showStatus(String message, boolean error) {
        statusMessage = safe(message);
        statusMessageUntil = System.currentTimeMillis() + (error ? 3500L : 2400L);
    }

    private int pageSize() {
        return Math.max(1, columns * rows);
    }

    private static ModernMainLayout.Rect safePanel(ModernMainLayout.Rect source) {
        int inset = Math.min(12, Math.max(4, Math.min(source.width, source.height) / 10));
        return new ModernMainLayout.Rect(source.x + inset, source.y + inset,
                Math.max(1, source.width - inset * 2), Math.max(1, source.height - inset * 2));
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
