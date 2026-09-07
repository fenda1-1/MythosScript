package com.zszl.zszlScriptMod.utils.guiinspect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.TextFormatting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

        public GuiElementInfo(ElementType type, String path, String text, int x, int y, int width, int height,
                int buttonId, int slotIndex) {
            this.type = type;
            this.path = path == null ? "" : path;
            this.text = text == null ? "" : text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.buttonId = buttonId;
            this.slotIndex = slotIndex;
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

    public static GuiSnapshot captureCurrentSnapshot() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        if (screen == null) {
            return new GuiSnapshot("", "", "", Collections.<GuiElementInfo>emptyList());
        }

        String className = screen.getClass().getName();
        String simpleName = screen.getClass().getSimpleName();
        String title = getCurrentGuiTitle(mc);
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

        collectCustomElements(screen, simpleName, elements);

        return new GuiSnapshot(className, simpleName, title, elements);
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
        if (mc == null || mc.currentScreen == null) {
            return "";
        }

        GuiScreen screen = mc.currentScreen;
        if (screen instanceof GuiChest && mc.player != null && mc.player.openContainer instanceof ContainerChest) {
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
