package com.zszl.zszlScriptMod.utils.guiinspect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.TextFormatting;

import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.modern.ModernDropdown;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiElementInspector {

    private static Field guiButtonListField;

    private GuiElementInspector() {
    }

    public enum ElementType {
        TITLE,
        BUTTON,
        SLOT,
        CUSTOM
    }

    /**
     * Optional semantic surface supplied by custom screens. Vanilla GUI
     * controls can be discovered through reflection, but self-rendered
     * screens need to publish their stable hit targets explicitly so MCP
     * callers do not have to reverse-engineer pixels or source layouts.
     */
    public interface McpElementProvider {
        List<GuiElementInfo> getMcpGuiElements();

        /**
         * Allows a provider to use a route-aware prefix when it has one. The
         * old no-argument method remains the compatibility entry point for
         * screens that already publish absolute paths.
         */
        default List<GuiElementInfo> getMcpGuiElements(String pathPrefix) {
            return getMcpGuiElements();
        }

        /** Gives a custom screen a chance to synchronously rebuild its hit map. */
        default void prepareMcpGui() {
        }

        /** Optional direct semantic text editing hook. */
        default boolean setMcpText(String target, String text, boolean append) {
            return false;
        }

        /** Optional direct semantic value editing hook. */
        default boolean setMcpValue(String target, String value) {
            return false;
        }

        /**
         * Converts custom element coordinates to the coordinates accepted by
         * the current GuiScreen mouse event. Vanilla elements are never
         * passed through this hook.
         */
        default GuiElementInfo normalizeMcpGuiElement(GuiElementInfo element) {
            return element;
        }
    }

    public static final class GuiElementInfo {
        private final ElementType type;
        private final String path;
        private final String text;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int buttonId;
        private final int slotIndex;
        private final String controlType;
        private final String value;
        private final boolean enabled;
        private final boolean editable;
        private final List<String> actions;
        private final List<String> choices;
        private final boolean visible;

        public GuiElementInfo(ElementType type, String path, String text, int x, int y, int width, int height,
                int buttonId, int slotIndex) {
            this(type, path, text, x, y, width, height, buttonId, slotIndex,
                    defaultControlType(type), "", type != ElementType.TITLE, false,
                    defaultActions(type), Collections.<String>emptyList(), true);
        }

        public GuiElementInfo(ElementType type, String path, String text, int x, int y, int width, int height,
                int buttonId, int slotIndex, String controlType, String value, boolean enabled, boolean editable,
                List<String> actions, List<String> choices) {
            this(type, path, text, x, y, width, height, buttonId, slotIndex, controlType, value, enabled, editable,
                    actions, choices, true);
        }

        public GuiElementInfo(ElementType type, String path, String text, int x, int y, int width, int height,
                int buttonId, int slotIndex, String controlType, String value, boolean enabled, boolean editable,
                List<String> actions, List<String> choices, boolean visible) {
            this.type = type;
            this.path = path == null ? "" : path;
            this.text = text == null ? "" : text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.buttonId = buttonId;
            this.slotIndex = slotIndex;
            this.controlType = controlType == null || controlType.trim().isEmpty()
                    ? defaultControlType(type) : controlType;
            this.value = value == null ? "" : value;
            this.enabled = enabled;
            this.editable = editable;
            this.actions = immutableCopy(actions);
            this.choices = immutableCopy(choices);
            this.visible = visible;
        }

        public ElementType getType() {
            return type;
        }

        public String getPath() {
            return path;
        }

        public String getText() {
            return text;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getButtonId() {
            return buttonId;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        /** Machine-readable semantic role, for example input, toggle or choice. */
        public String getControlType() {
            return controlType;
        }

        /** Current draft/runtime value when the control has one. */
        public String getValue() {
            return value;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isEditable() {
            return editable;
        }

        public List<String> getActions() {
            return actions;
        }

        public List<String> getChoices() {
            return choices;
        }

        /** True when the control intersects the currently rendered viewport. */
        public boolean isVisible() {
            return visible;
        }

        /** Copies this element while preserving all semantic metadata. */
        public GuiElementInfo withGeometry(int x, int y, int width, int height) {
            return new GuiElementInfo(type, path, text, x, y, width, height, buttonId, slotIndex,
                    controlType, value, enabled, editable, actions, choices, visible);
        }

        /** Copies this element while preserving geometry and semantic metadata. */
        public GuiElementInfo withPath(String path) {
            return new GuiElementInfo(type, path, text, x, y, width, height, buttonId, slotIndex,
                    controlType, value, enabled, editable, actions, choices, visible);
        }

        public GuiElementInfo withVisibility(boolean visible) {
            return new GuiElementInfo(type, path, text, x, y, width, height, buttonId, slotIndex,
                    controlType, value, enabled, editable, actions, choices, visible);
        }

        public GuiElementInfo withSemantics(String controlType, String value, boolean enabled, boolean editable,
                List<String> actions, List<String> choices) {
            return new GuiElementInfo(type, path, text, x, y, width, height, buttonId, slotIndex,
                    controlType, value, enabled, editable, actions, choices, visible);
        }
    }

    public static final class GuiSnapshot {
        private final String screenClassName;
        private final String screenSimpleName;
        private final String title;
        private final List<GuiElementInfo> elements;

        private GuiSnapshot(String screenClassName, String screenSimpleName, String title,
                List<GuiElementInfo> elements) {
            this.screenClassName = screenClassName == null ? "" : screenClassName;
            this.screenSimpleName = screenSimpleName == null ? "" : screenSimpleName;
            this.title = title == null ? "" : title;
            this.elements = elements == null ? Collections.<GuiElementInfo>emptyList() : elements;
        }

        public String getScreenClassName() {
            return screenClassName;
        }

        public String getScreenSimpleName() {
            return screenSimpleName;
        }

        public String getTitle() {
            return title;
        }

        public List<GuiElementInfo> getElements() {
            return elements;
        }
    }

    private static final class CustomElementCandidate {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int id;
        private final String text;
        private final String sourceField;
        private final String className;

        private CustomElementCandidate(int x, int y, int width, int height, int id, String text, String sourceField,
                String className) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.id = id;
            this.text = text == null ? "" : text;
            this.sourceField = sourceField == null ? "" : sourceField;
            this.className = className == null ? "" : className;
        }
    }

    public static GuiScreen getActiveScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            return mc.currentScreen;
        }
        return DetachedSwingWindowManager.isDetached() ? DetachedSwingWindowManager.getActiveScreen() : null;
    }

    public static GuiSnapshot captureCurrentSnapshot() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = getActiveScreen();
        if (screen == null) {
            return new GuiSnapshot("", "", "", Collections.<GuiElementInfo>emptyList());
        }

        String className = screen.getClass().getName();
        String simpleName = screen.getClass().getSimpleName();
        String title = getCurrentGuiTitle(mc, screen);
        List<GuiElementInfo> elements = new ArrayList<>();

        elements.add(new GuiElementInfo(ElementType.TITLE,
                "screen/" + simpleName + "/title",
                title,
                0,
                0,
                0,
                0,
                Integer.MIN_VALUE,
                -1));

        List<GuiButton> buttons = getButtonList(screen);
        for (int i = 0; i < buttons.size(); i++) {
            GuiButton button = buttons.get(i);
            if (button == null) {
                continue;
            }
            String text = stripFormatting(button.displayString);
            String path = "screen/" + simpleName + "/button[" + i + "]";
            if (button.id >= 0) {
                path += "/button#" + button.id;
            }
            elements.add(new GuiElementInfo(ElementType.BUTTON, path, text, button.x, button.y, button.width,
                    button.height, button.id, -1));
        }

        if (screen instanceof GuiContainer) {
            GuiContainer gui = (GuiContainer) screen;
            int xSize = readIntField(gui, 176, "xSize", "field_146999_f");
            int ySize = readIntField(gui, 166, "ySize", "field_147000_g");
            int guiLeft = readIntField(gui, (screen.width - xSize) / 2, "guiLeft", "field_147003_i");
            int guiTop = readIntField(gui, (screen.height - ySize) / 2, "guiTop", "field_147009_r");
            int chestSize = resolvePrimaryContainerSize(gui.inventorySlots);

            for (int i = 0; i < gui.inventorySlots.inventorySlots.size(); i++) {
                Object slotObj = gui.inventorySlots.inventorySlots.get(i);
                if (!(slotObj instanceof Slot)) {
                    continue;
                }
                Slot slot = (Slot) slotObj;
                String pathPrefix = i < chestSize ? "chest_slot" : "player_slot";
                String path = "screen/" + simpleName + "/" + pathPrefix + "[" + i + "]"
                        + "/slot[" + i + "]";
                String text = slot.getHasStack() ? stripFormatting(slot.getStack().getDisplayName()) : "";
                elements.add(new GuiElementInfo(ElementType.SLOT, path, text,
                        guiLeft + slot.xPos,
                        guiTop + slot.yPos,
                        16,
                        16,
                        Integer.MIN_VALUE,
                        i));
            }
        }

        McpElementProvider provider = screen instanceof McpElementProvider
                ? (McpElementProvider) screen : null;
        if (provider != null) {
            try {
                List<GuiElementInfo> semantic = provider.getMcpGuiElements();
                if (semantic != null) {
                    for (GuiElementInfo element : semantic) {
                        if (element != null && element.getWidth() >= 0 && element.getHeight() >= 0) {
                            elements.add(element);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // A diagnostic surface must never make ordinary GUI
                // inspection fail when a custom screen is mid-layout.
            }
        }

        collectCustomElements(screen, simpleName, elements);

        // Vanilla/custom screens often keep GuiTextField instances outside
        // buttonList. Publish those widgets through the same safe semantic
        // scanner so input is available without knowing private field names.
        // Provider screens already publish their complete active surface; do
        // not recursively walk the modern shell and all cached tabs again.
        if (provider == null) {
            List<GuiElementInfo> reflected = collectMcpElements(screen, "screen/" + simpleName + "/mcp");
            for (GuiElementInfo element : reflected) {
                if (element != null && ("input".equals(element.getControlType())
                        || "choice".equals(element.getControlType())
                        || "toggle".equals(element.getControlType()))) {
                    elements.add(element);
                }
            }
        }

        if (provider != null) {
            for (int i = 0; i < elements.size(); i++) {
                GuiElementInfo element = elements.get(i);
                if (element != null && element.getType() == ElementType.CUSTOM) {
                    GuiElementInfo normalized = provider.normalizeMcpGuiElement(element);
                    if (normalized != null) {
                        elements.set(i, normalized);
                    }
                }
            }
        }

        return new GuiSnapshot(className, simpleName, title, elements);
    }

    /**
     * Reflective fallback for self-rendered settings pages. It only exposes
     * already-laid-out rectangles and text fields; it never invokes arbitrary
     * application methods. Pages with richer semantics can override this with
     * explicit controls, while older pages still become addressable by stable
     * field paths.
     */
    public static List<GuiElementInfo> collectMcpElements(Object owner, String pathPrefix) {
        List<GuiElementInfo> result = new ArrayList<>();
        if (owner == null) {
            return result;
        }
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        Map<String, Boolean> paths = new java.util.HashMap<>();
        collectMcpObject(owner, normalizePrefix(pathPrefix), "", 0, visited, paths, result);
        return result;
    }

    /** Sets a reflected GuiTextField addressed by its semantic path. */
    public static boolean setMcpText(Object owner, String target, String text, boolean append) {
        GuiTextField field = findMcpTextField(owner, "", "", 0,
                target, new IdentityHashMap<Object, Boolean>());
        if (field == null) {
            return false;
        }
        String incoming = text == null ? "" : text;
        field.setText(append ? safeFieldText(field) + incoming : incoming);
        field.setFocused(true);
        return true;
    }

    /** Sets a reflected ModernDropdown addressed by its semantic path. */
    public static boolean setMcpValue(Object owner, String target, String value) {
        ModernDropdown dropdown = findMcpDropdown(owner, "", "", 0,
                target, new IdentityHashMap<Object, Boolean>());
        if (dropdown != null) {
            String requested = value == null ? "" : value;
            dropdown.setValue(requested);
            return requested.equalsIgnoreCase(dropdown.value());
        }
        return setMcpText(owner, target, value, false);
    }

    private static final int MAX_SEMANTIC_DEPTH = 5;
    private static final int MAX_SEMANTIC_ELEMENTS = 2048;

    private static void collectMcpObject(Object value, String basePath, String hint, int depth,
            Map<Object, Boolean> visited, Map<String, Boolean> paths, List<GuiElementInfo> result) {
        if (value == null || result.size() >= MAX_SEMANTIC_ELEMENTS || depth > MAX_SEMANTIC_DEPTH) {
            return;
        }
        if (value instanceof GuiTextField) {
            GuiTextField field = (GuiTextField) value;
            if (!readBooleanProperty(field, true, "visible", "isVisible", "getVisible", "field_146220_v")) {
                return;
            }
            Integer x = readNullableInt(field, "x", "xPosition", "posX", "field_146209_f");
            Integer y = readNullableInt(field, "y", "yPosition", "posY", "field_146210_g");
            Integer width = readNullableInt(field, "width", "fieldWidth", "field_146218_h");
            Integer height = readNullableInt(field, "height", "fieldHeight", "field_146219_i");
            if (x != null && y != null && width != null && height != null && width > 0 && height > 0) {
                addMcpElement(result, paths, new GuiElementInfo(ElementType.CUSTOM, basePath,
                        safeFieldText(field), x, y, width, height, Integer.MIN_VALUE, -1,
                        "input", safeFieldText(field), readBooleanProperty(field, true, "enabled", "isEnabled", "field_146226_p"), true,
                        java.util.Arrays.asList("click", "input", "set"), Collections.<String>emptyList()));
            }
            return;
        }
        if (value instanceof ModernMainLayout.Rect) {
            ModernMainLayout.Rect rect = (ModernMainLayout.Rect) value;
            if (rect.width > 0 && rect.height > 0) {
                String controlType = classifyControlType(hint, basePath);
                addMcpElement(result, paths, new GuiElementInfo(ElementType.CUSTOM, basePath,
                        hint == null ? "" : hint, rect.x, rect.y, rect.width, rect.height,
                        Integer.MIN_VALUE, -1, controlType, "", true,
                        isEditableControl(controlType), actionsFor(controlType), Collections.<String>emptyList()));
            }
            return;
        }
        if (value instanceof ModernDropdown) {
            ModernDropdown dropdown = (ModernDropdown) value;
            Object rawButton = readNamedField(dropdown, "button");
            ModernMainLayout.Rect button = rawButton instanceof ModernMainLayout.Rect
                    ? (ModernMainLayout.Rect) rawButton : null;
            if (button != null && button.width > 0 && button.height > 0) {
                Object rawValues = readNamedField(dropdown, "values");
                List<String> choices = new ArrayList<>();
                if (rawValues instanceof String[]) {
                    for (String option : (String[]) rawValues) {
                        choices.add(option == null ? "" : option);
                    }
                }
                String current = safeDropdownValue(dropdown);
                addMcpElement(result, paths, new GuiElementInfo(ElementType.CUSTOM, basePath,
                        current, button.x, button.y, button.width, button.height, Integer.MIN_VALUE, -1,
                        "choice", current, true, true, java.util.Arrays.asList("click", "set"), choices));
            }
            return;
        }
        if (isScalar(value)) {
            return;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof Map) {
            int index = 0;
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                if (entryObject instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                    String key = mcpSegment(entry.getKey() == null ? String.valueOf(index) : String.valueOf(entry.getKey()));
                    collectMcpObject(entry.getValue(), joinPath(basePath, key), hint, depth + 1, visited, paths, result);
                    index++;
                    if (result.size() >= MAX_SEMANTIC_ELEMENTS) return;
                }
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length && result.size() < MAX_SEMANTIC_ELEMENTS; i++) {
                collectMcpObject(Array.get(value, i), joinPath(basePath, String.valueOf(i)), hint, depth + 1,
                        visited, paths, result);
            }
            return;
        }
        if (value instanceof Iterable) {
            int index = 0;
            for (Object item : (Iterable<?>) value) {
                if (index >= MAX_SEMANTIC_ELEMENTS || result.size() >= MAX_SEMANTIC_ELEMENTS) return;
                collectMcpObject(item, joinPath(basePath, String.valueOf(index)), hint, depth + 1,
                        visited, paths, result);
                index++;
            }
            return;
        }

        Class<?> current = value.getClass();
        while (current != null && current != Object.class && result.size() < MAX_SEMANTIC_ELEMENTS) {
            for (Field field : current.getDeclaredFields()) {
                if (field == null || field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || shouldSkipSemanticField(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child == null) {
                        continue;
                    }
                    String childPath = joinPath(basePath, mcpSegment(field.getName()));
                    if (child instanceof GuiTextField || child instanceof ModernMainLayout.Rect
                            || child instanceof ModernDropdown || child instanceof Map
                            || child instanceof Iterable || child.getClass().isArray()) {
                        collectMcpObject(child, childPath, field.getName(), depth + 1, visited, paths, result);
                    } else if (depth < MAX_SEMANTIC_DEPTH && isLikelyUiObject(child, field.getName())) {
                        collectMcpObject(child, childPath, field.getName(), depth + 1, visited, paths, result);
                    }
                } catch (Exception ignored) {
                    // A best-effort inspector must tolerate inaccessible or
                    // partially initialized widget fields.
                }
            }
            current = current.getSuperclass();
        }
    }

    private static GuiTextField findMcpTextField(Object value, String basePath, String hint, int depth,
            String target, Map<Object, Boolean> visited) {
        if (value == null || depth > MAX_SEMANTIC_DEPTH) {
            return null;
        }
        if (value instanceof GuiTextField) {
            return matchesSemanticPath(target, basePath)
                    && readBooleanProperty(value, true, "visible", "isVisible", "getVisible", "field_146220_v")
                            ? (GuiTextField) value : null;
        }
        if (isScalar(value) || value instanceof ModernMainLayout.Rect || value instanceof ModernDropdown
                || visited.put(value, Boolean.TRUE) != null) {
            return null;
        }
        if (value instanceof Map) {
            int index = 0;
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                if (entryObject instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                    String key = mcpSegment(entry.getKey() == null ? String.valueOf(index) : String.valueOf(entry.getKey()));
                    GuiTextField found = findMcpTextField(entry.getValue(), joinPath(basePath, key), hint,
                            depth + 1, target, visited);
                    if (found != null) return found;
                    index++;
                }
            }
            return null;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                GuiTextField found = findMcpTextField(Array.get(value, i), joinPath(basePath, String.valueOf(i)), hint,
                        depth + 1, target, visited);
                if (found != null) return found;
            }
            return null;
        }
        if (value instanceof Iterable) {
            int index = 0;
            for (Object item : (Iterable<?>) value) {
                GuiTextField found = findMcpTextField(item, joinPath(basePath, String.valueOf(index)), hint,
                        depth + 1, target, visited);
                if (found != null) return found;
                index++;
            }
            return null;
        }
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field == null || field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || shouldSkipSemanticField(field.getName())) continue;
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child == null) continue;
                    GuiTextField found = findMcpTextField(child, joinPath(basePath, mcpSegment(field.getName())),
                            field.getName(), depth + 1, target, visited);
                    if (found != null) return found;
                } catch (Exception ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static ModernDropdown findMcpDropdown(Object value, String basePath, String hint, int depth,
            String target, Map<Object, Boolean> visited) {
        if (value == null || depth > MAX_SEMANTIC_DEPTH) return null;
        if (value instanceof ModernDropdown) {
            return matchesSemanticPath(target, basePath) ? (ModernDropdown) value : null;
        }
        if (isScalar(value) || value instanceof ModernMainLayout.Rect || value instanceof GuiTextField
                || visited.put(value, Boolean.TRUE) != null) return null;
        if (value instanceof Map) {
            int index = 0;
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                if (entryObject instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                    String key = mcpSegment(entry.getKey() == null ? String.valueOf(index) : String.valueOf(entry.getKey()));
                    ModernDropdown found = findMcpDropdown(entry.getValue(), joinPath(basePath, key), hint,
                            depth + 1, target, visited);
                    if (found != null) return found;
                    index++;
                }
            }
            return null;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                ModernDropdown found = findMcpDropdown(Array.get(value, i), joinPath(basePath, String.valueOf(i)), hint,
                        depth + 1, target, visited);
                if (found != null) return found;
            }
            return null;
        }
        if (value instanceof Iterable) {
            int index = 0;
            for (Object item : (Iterable<?>) value) {
                ModernDropdown found = findMcpDropdown(item, joinPath(basePath, String.valueOf(index)), hint,
                        depth + 1, target, visited);
                if (found != null) return found;
                index++;
            }
            return null;
        }
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field == null || field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || shouldSkipSemanticField(field.getName())) continue;
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child == null) continue;
                    ModernDropdown found = findMcpDropdown(child, joinPath(basePath, mcpSegment(field.getName())),
                            field.getName(), depth + 1, target, visited);
                    if (found != null) return found;
                } catch (Exception ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void addMcpElement(List<GuiElementInfo> result, Map<String, Boolean> paths,
            GuiElementInfo element) {
        if (result == null || paths == null || element == null || element.getPath().trim().isEmpty()
                || element.getWidth() <= 0 || element.getHeight() <= 0
                || paths.put(element.getPath(), Boolean.TRUE) != null) return;
        result.add(element);
    }

    private static boolean isLikelyUiObject(Object value, String fieldName) {
        if (value == null) return false;
        String name = value.getClass().getName();
        String hint = safe(fieldName).toLowerCase(Locale.ROOT);
        return name.startsWith("com.zszl.zszlScriptMod.gui.")
                || hint.contains("hit") || hint.contains("layout") || hint.contains("control")
                || hint.contains("bound") || hint.contains("dropdown") || hint.contains("scroll")
                || hint.contains("panel") || hint.contains("row");
    }

    private static boolean shouldSkipSemanticField(String fieldName) {
        String name = safe(fieldName).toLowerCase(Locale.ROOT);
        return "font".equals(name) || "fontrenderer".equals(name) || "minecraft".equals(name)
                || "mc".equals(name) || "owner".equals(name) || "context".equals(name)
                || "definition".equals(name) || "session".equals(name) || "action".equals(name)
                || "routeRequest".equals(fieldName) || name.contains("logger");
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>;
    }

    private static String classifyControlType(String hint, String path) {
        String value = (safe(hint) + "/" + safe(path)).toLowerCase(Locale.ROOT);
        if (value.contains("scroll") || value.contains("scrollbar")) return "scrollbar";
        if (value.contains("toggle") || value.contains("enable") || value.contains("disable")
                || value.contains("autofocus") || value.contains("autopause")
                || value.contains("runningstatus") || value.contains("master")) return "toggle";
        if (value.contains("dropdown") || value.contains("choice") || value.contains("option")
                || value.contains("language") || value.contains("scale") || value.contains("theme")) return "choice";
        if (value.contains("save") || value.contains("apply") || value.contains("delete") || value.contains("remove")
                || value.contains("clear") || value.contains("reset") || value.contains("refresh")
                || value.contains("reload") || value.contains("create") || value.contains("new")
                || value.contains("add") || value.contains("copy") || value.contains("confirm")
                || value.contains("cancel") || value.contains("scan") || value.contains("compare")
                || value.contains("return") || value.contains("back") || value.contains("edit")
                || value.contains("open")) return "action";
        if (value.contains("panel") || value.contains("clip") || value.contains("viewport")
                || value.contains("content") || value.contains("divider") || value.contains("rail")) return "region";
        return "custom";
    }

    private static boolean isEditableControl(String controlType) {
        return "input".equals(controlType) || "toggle".equals(controlType) || "choice".equals(controlType);
    }

    private static List<String> actionsFor(String controlType) {
        if ("input".equals(controlType)) return java.util.Arrays.asList("click", "input", "set");
        if ("toggle".equals(controlType) || "choice".equals(controlType))
            return java.util.Arrays.asList("click", "set");
        if ("scrollbar".equals(controlType)) return Collections.singletonList("scroll");
        if ("action".equals(controlType) || "custom".equals(controlType)) return Collections.singletonList("click");
        return Collections.emptyList();
    }

    private static String safeDropdownValue(ModernDropdown dropdown) {
        try {
            return dropdown.value() == null ? "" : dropdown.value();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String safeFieldText(GuiTextField field) {
        try {
            return field == null || field.getText() == null ? "" : field.getText();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean readBooleanProperty(Object target, boolean fallback, String... names) {
        Object value = readFieldOrGetter(target, false, names);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static String mcpSegment(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        String result = text.replaceAll("[^A-Za-z0-9_.-]+", "_");
        return result.isEmpty() ? "item" : result;
    }

    private static String normalizePrefix(String value) {
        String result = safe(value).replace('\\', '/').trim();
        while (result.startsWith("/")) result = result.substring(1);
        return result.isEmpty() || result.endsWith("/") ? result : result + "/";
    }

    private static String joinPath(String base, String segment) {
        String left = normalizePrefix(base);
        String right = safe(segment).replace('\\', '/');
        while (right.startsWith("/")) right = right.substring(1);
        return left + right;
    }

    private static boolean matchesSemanticPath(String target, String candidate) {
        String query = normalizePath(target);
        String path = normalizePath(candidate);
        if (query.isEmpty() || path.isEmpty()) return false;
        return query.equals(path) || query.endsWith("/" + path) || path.endsWith("/" + query);
    }

    private static String normalizePath(String value) {
        String result = safe(value).replace('\\', '/').trim().toLowerCase(Locale.ROOT);
        while (result.startsWith("/")) result = result.substring(1);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String defaultControlType(ElementType type) {
        if (type == ElementType.TITLE) return "title";
        if (type == ElementType.BUTTON) return "button";
        if (type == ElementType.SLOT) return "slot";
        return "custom";
    }

    private static List<String> defaultActions(ElementType type) {
        if (type == ElementType.TITLE) return Collections.emptyList();
        return Collections.singletonList("click");
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null || values.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static GuiElementInfo findFirstByPath(String pathQuery, String matchMode, ElementType... allowedTypes) {
        String normalizedQuery = normalize(pathQuery);
        if (normalizedQuery.isEmpty()) {
            return null;
        }
        GuiSnapshot snapshot = captureCurrentSnapshot();
        if (snapshot.getElements().isEmpty()) {
            return null;
        }
        for (GuiElementInfo element : snapshot.getElements()) {
            if (element == null || !isAllowed(element.getType(), allowedTypes)) {
                continue;
            }
            String normalizedPath = normalize(element.getPath());
            if (matches(normalizedPath, normalizedQuery, matchMode)) {
                return element;
            }
        }
        return null;
    }

    public static String getCurrentGuiTitle(Minecraft mc) {
        return getCurrentGuiTitle(mc, getActiveScreen());
    }

    private static String getCurrentGuiTitle(Minecraft mc, GuiScreen screen) {
        if (screen == null) {
            return "";
        }

        if (mc != null && screen == mc.currentScreen && screen instanceof GuiChest && mc.player != null
                && mc.player.openContainer instanceof ContainerChest) {
            try {
                IInventory inv = ((ContainerChest) mc.player.openContainer).getLowerChestInventory();
                if (inv != null && inv.getDisplayName() != null) {
                    return inv.getDisplayName().getUnformattedText();
                }
            } catch (Exception ignored) {
            }
        }
        if (screen instanceof GuiMerchant) {
            return "Merchant";
        }
        return screen.getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtonList(GuiScreen screen) {
        if (screen == null) {
            return Collections.emptyList();
        }
        try {
            if (guiButtonListField == null) {
                guiButtonListField = resolveField(GuiScreen.class, "buttonList", "field_146292_n");
            }
            Object value = guiButtonListField == null ? null : guiButtonListField.get(screen);
            if (value instanceof List) {
                return (List<GuiButton>) value;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private static int resolvePrimaryContainerSize(Container container) {
        if (container instanceof ContainerChest) {
            try {
                return ((ContainerChest) container).getLowerChestInventory().getSizeInventory();
            } catch (Exception ignored) {
            }
        }
        return container == null || container.inventorySlots == null ? 0 : container.inventorySlots.size();
    }

    private static boolean isAllowed(ElementType type, ElementType... allowedTypes) {
        if (type == null) {
            return false;
        }
        if (allowedTypes == null || allowedTypes.length == 0) {
            return true;
        }
        for (ElementType allowed : allowedTypes) {
            if (allowed == type) {
                return true;
            }
        }
        return false;
    }

    private static void collectCustomElements(GuiScreen screen, String simpleName, List<GuiElementInfo> elements) {
        if (screen == null || elements == null) {
            return;
        }
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        Class<?> current = screen.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field == null || field.isSynthetic()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (!(value instanceof List)) {
                        continue;
                    }
                    List<?> list = (List<?>) value;
                    for (int i = 0; i < list.size(); i++) {
                        Object item = list.get(i);
                        if (item == null || item instanceof GuiButton || item instanceof Slot || visited.containsKey(item)) {
                            continue;
                        }
                        CustomElementCandidate candidate = readCustomElementCandidate(item, field.getName());
                        if (candidate == null) {
                            continue;
                        }
                        StringBuilder path = new StringBuilder("screen/")
                                .append(simpleName)
                                .append("/custom/")
                                .append(candidate.sourceField)
                                .append("[")
                                .append(i)
                                .append("]/")
                                .append(candidate.className);
                        if (candidate.id != Integer.MIN_VALUE) {
                            path.append("/id#").append(candidate.id);
                        }
                        elements.add(new GuiElementInfo(ElementType.CUSTOM, path.toString(), candidate.text,
                                candidate.x, candidate.y, candidate.width, candidate.height, candidate.id, -1));
                        visited.put(item, Boolean.TRUE);
                    }
                } catch (Exception ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    private static CustomElementCandidate readCustomElementCandidate(Object item, String sourceField) {
        if (item == null) {
            return null;
        }

        Integer x = readNullableInt(item, "x", "posX", "xPos", "left", "xPosition");
        Integer y = readNullableInt(item, "y", "posY", "yPos", "top", "yPosition");
        Integer width = readNullableInt(item, "width", "w", "sizeX", "buttonWidth");
        Integer height = readNullableInt(item, "height", "h", "sizeY", "buttonHeight");
        Integer id = readNullableInt(item, "id", "buttonId", "componentId", "widgetId");
        String text = readNullableString(item, "displayString", "text", "label", "name", "title", "message");

        if (x == null || y == null) {
            return null;
        }
        int resolvedWidth = width == null || width <= 0 ? 16 : width;
        int resolvedHeight = height == null || height <= 0 ? 16 : height;
        boolean meaningful = resolvedWidth > 0 || resolvedHeight > 0 || (text != null && !text.trim().isEmpty());
        if (!meaningful) {
            return null;
        }

        return new CustomElementCandidate(x, y, resolvedWidth, resolvedHeight,
                id == null ? Integer.MIN_VALUE : id,
                stripFormatting(text),
                sourceField,
                item.getClass().getSimpleName());
    }

    private static boolean matches(String sourceText, String queryText, String matchMode) {
        if (sourceText.isEmpty() || queryText.isEmpty()) {
            return false;
        }
        if ("EXACT".equalsIgnoreCase(safe(matchMode))) {
            return sourceText.equals(queryText);
        }
        return sourceText.contains(queryText);
    }

    private static int readIntField(Object target, int fallback, String... names) {
        if (target == null) {
            return fallback;
        }
        try {
            Field field = resolveField(target.getClass(), names);
            Object value = field == null ? null : field.get(target);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static Integer readNullableInt(Object target, String... names) {
        Object value = readFieldOrGetter(target, true, names);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static String readNullableString(Object target, String... names) {
        Object value = readFieldOrGetter(target, false, names);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object readFieldOrGetter(Object target, boolean numericPreferred, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            Object value = readNamedField(target, name);
            if (isAcceptableValue(value, numericPreferred)) {
                return value;
            }
            value = readNamedGetter(target, name);
            if (isAcceptableValue(value, numericPreferred)) {
                return value;
            }
        }
        return null;
    }

    private static Object readNamedField(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object readNamedGetter(Object target, String name) {
        if (target == null || name == null || name.isEmpty()) {
            return null;
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String[] methodNames = new String[] { "get" + suffix, "is" + suffix };
        for (String methodName : methodNames) {
            Class<?> current = target.getClass();
            while (current != null && current != Object.class) {
                try {
                    Method method = current.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception ignored) {
                }
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isAcceptableValue(Object value, boolean numericPreferred) {
        if (value == null) {
            return false;
        }
        return !numericPreferred || value instanceof Number;
    }

    private static Field resolveField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String normalize(String text) {
        return stripFormatting(safe(text)).trim().toLowerCase(Locale.ROOT).replace('\u3000', ' ')
                .replaceAll("\\s+", " ");
    }

    private static String stripFormatting(String text) {
        String cleaned = TextFormatting.getTextWithoutFormattingCodes(safe(text));
        return cleaned == null ? safe(text) : cleaned;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
