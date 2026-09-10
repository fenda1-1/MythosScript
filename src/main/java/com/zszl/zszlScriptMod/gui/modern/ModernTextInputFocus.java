package com.zszl.zszlScriptMod.gui.modern;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

/** Finds focused text fields in the nested controls owned by a GUI. */
public final class ModernTextInputFocus {

    private static final int MAX_DEPTH = 8;

    private ModernTextInputFocus() {
    }

    public static boolean isFocused(Object root) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        return isFocused(root, visited, 0);
    }

    /** Clears nested inputs when a click lands outside every visible text field. */
    public static void clearFocusOutside(Object root, int mouseX, int mouseY) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        boolean fieldAtPoint = containsTextField(root, visited, 0, mouseX, mouseY);
        visited.clear();
        clearFocus(root, visited, 0, mouseX, mouseY, fieldAtPoint);
    }

    public static void clearFocus(Object root) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        clearFocus(root, visited, 0, 0, 0, false);
    }

    private static boolean containsTextField(Object value, Set<Object> visited, int depth, int mouseX, int mouseY) {
        if (value == null || depth > MAX_DEPTH) {
            return false;
        }
        if (value instanceof GuiTextField) {
            GuiTextField field = (GuiTextField) value;
            return field.getVisible() && field.x <= mouseX && mouseX < field.x + field.width
                    && field.y <= mouseY && mouseY < field.y + field.height;
        }
        return inspectChildren(value, visited, depth, child -> containsTextField(child, visited, depth + 1, mouseX, mouseY));
    }

    private static void clearFocus(Object value, Set<Object> visited, int depth, int mouseX, int mouseY,
            boolean preservePoint) {
        if (value == null || depth > MAX_DEPTH) {
            return;
        }
        if (value instanceof GuiTextField) {
            GuiTextField field = (GuiTextField) value;
            boolean keep = preservePoint && field.getVisible() && field.x <= mouseX && mouseX < field.x + field.width
                    && field.y <= mouseY && mouseY < field.y + field.height;
            if (!keep) {
                field.setFocused(false);
            }
            return;
        }
        inspectChildren(value, visited, depth, child -> {
            clearFocus(child, visited, depth + 1, mouseX, mouseY, preservePoint);
            return false;
        });
    }

    private interface ChildVisitor {
        boolean visit(Object child);
    }

    private static boolean inspectChildren(Object value, Set<Object> visited, int depth, ChildVisitor visitor) {
        if (value == null || depth > MAX_DEPTH || !visited.add(value)) {
            return false;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (visitor.visit(Array.get(value, i))) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map) {
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                if (visitor.visit(entry.getKey()) || visitor.visit(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable) {
            for (Object child : (Iterable<?>) value) {
                if (visitor.visit(child)) {
                    return true;
                }
            }
            return false;
        }
        Class<?> valueClass = value.getClass();
        if (!shouldInspect(valueClass)) {
            return false;
        }
        for (Class<?> current = valueClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (visitor.visit(field.get(value))) {
                        return true;
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean isFocused(Object value, Set<Object> visited, int depth) {
        if (value == null || depth > MAX_DEPTH) {
            return false;
        }
        if (value instanceof GuiTextField) {
            return ((GuiTextField) value).isFocused();
        }
        if (!visited.add(value)) {
            return false;
        }

        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (isFocused(Array.get(value, i), visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map) {
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                if (isFocused(entry.getKey(), visited, depth + 1)
                        || isFocused(entry.getValue(), visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable) {
            for (Object child : (Iterable<?>) value) {
                if (isFocused(child, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (!shouldInspect(valueClass)) {
            return false;
        }

        for (Class<?> current = valueClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (isFocused(field.get(value), visited, depth + 1)) {
                        return true;
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean shouldInspect(Class<?> type) {
        if (type.isArray() || Map.class.isAssignableFrom(type) || Iterable.class.isAssignableFrom(type)) {
            return true;
        }
        if (GuiScreen.class.isAssignableFrom(type)) {
            return true;
        }
        return type.getName().startsWith("com.zszl.zszlScriptMod.gui");
    }
}
