package com.zszl.zszlScriptMod.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import com.zszl.zszlScriptMod.utils.locator.ActionTargetLocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/**
 * First-class GUI control surface for MCP callers.
 *
 * <p>The existing path actions remain useful for reusable workflows, but a
 * GUI controller needs to expose the current screen and perform one semantic
 * interaction at a time. This class deliberately runs only on the Minecraft
 * client thread (the MCP backend provides that boundary).</p>
 */
public final class McpGuiController {

    private McpGuiController() {
    }

    public static JsonObject call(JsonObject arguments) throws Exception {
        JsonObject p = arguments == null ? new JsonObject() : arguments;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            throw new IllegalStateException("Minecraft client is unavailable");
        }
        String operation = optionalString(p, "operation", "inspect").toLowerCase(Locale.ROOT);
        switch (operation) {
        case "inspect":
            return inspect(mc);
        case "open":
            return open(mc, p);
        case "select_tab":
            return selectTab(mc, p);
        case "click":
            return click(mc, p);
        case "input":
            return input(mc, p);
        case "set":
            return set(mc, p);
        case "key":
            return key(mc, p);
        case "scroll":
            return scroll(mc, p);
        case "save":
            return save(mc);
        case "close":
            return close(mc, p);
        default:
            throw new IllegalArgumentException("Unknown GUI operation: " + operation);
        }
    }

    private static JsonObject inspect(Minecraft mc) {
        GuiScreen active = GuiElementInspector.getActiveScreen();
        if (active instanceof GuiElementInspector.McpElementProvider) {
            ((GuiElementInspector.McpElementProvider) active).prepareMcpGui();
        }
        GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        JsonArray elements = new JsonArray();
        if (snapshot != null) {
            for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
                if (element == null) {
                    continue;
                }
                elements.add(object("type", element.getType().name(), "path", element.getPath(),
                        "text", element.getText(), "x", element.getX(), "y", element.getY(),
                        "width", element.getWidth(), "height", element.getHeight(),
                        "buttonId", element.getButtonId(), "slotIndex", element.getSlotIndex(),
                        "controlType", element.getControlType(), "value", element.getValue(),
                        "enabled", element.isEnabled(), "editable", element.isEditable(),
                        "actions", element.getActions(), "choices", element.getChoices(),
                        "visible", element.isVisible()));
            }
        }
        JsonObject result = object("operation", "inspect", "open", screen != null,
                "screenClass", snapshot == null ? "" : snapshot.getScreenClassName(),
                "title", snapshot == null ? "" : snapshot.getTitle(),
                "width", screen == null ? 0 : screen.width,
                "height", screen == null ? 0 : screen.height,
                "coordinateSpace", "scaled GUI pixels",
                "elements", elements,
                "capabilities", Arrays.asList("inspect", "open", "select_tab", "click", "input", "set", "key", "scroll", "save", "close"));
        if (screen instanceof GuiModernMainScreen) {
            result.add("modern", modernState((GuiModernMainScreen) screen));
        }
        return result;
    }

    private static JsonObject modernState(GuiModernMainScreen screen) {
        List<String> ids = screen.getMcpOpenTabIds();
        List<String> commands = screen.getMcpOpenTabCommands();
        String activeId = screen.getMcpActiveTabId();
        JsonArray tabs = new JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String command = i < commands.size() ? commands.get(i) : "";
            tabs.add(object("slot", i + 1, "id", id, "command", command, "active", id.equals(activeId)));
        }
        return object("activeTabId", activeId, "activeTabCommand", screen.getMcpActiveTabCommand(),
                "commandPaletteOpen", screen.isMcpCommandPaletteOpen(), "tabs", tabs,
                "availableTabCommands", screen.getMcpAvailableTabCommands(),
                "targeting", "Use element path/text returned in elements; modern settings routes use command names.");
    }

    private static JsonObject open(Minecraft mc, JsonObject p) {
        String target = optionalString(p, "target", "");
        if (target.isEmpty()) {
            target = optionalString(p, "command", "");
        }
        if (target.isEmpty() || "main".equalsIgnoreCase(target) || "control_center".equalsIgnoreCase(target)) {
            GuiModernMainScreen.openMenu(mc);
        } else {
            String command = target.startsWith("tab:") ? target.substring(4).trim() : target.trim();
            if (command.isEmpty()) {
                throw new IllegalArgumentException("GUI tab command cannot be empty");
            }
            GuiScreen screen = GuiElementInspector.getActiveScreen();
            if (screen instanceof GuiModernMainScreen) {
                if (!((GuiModernMainScreen) screen).openSettingsTabFromMcp(command)) {
                    throw new IllegalArgumentException("Unknown or unavailable modern GUI route: " + command);
                }
            } else {
                GuiModernMainScreen.openSettingsTab(mc, command);
            }
        }
        JsonObject result = inspect(mc);
        result.addProperty("requestedTarget", target);
        return result;
    }

    private static JsonObject selectTab(Minecraft mc, JsonObject p) {
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        if (!(screen instanceof GuiModernMainScreen)) {
            throw new IllegalStateException("The modern control center is not open");
        }
        String target = requiredTarget(p);
        if (!((GuiModernMainScreen) screen).selectTabFromMcp(target)) {
            throw new IllegalArgumentException("Tab is not open or cannot be selected: " + target);
        }
        return inspect(mc);
    }

    private static JsonObject click(Minecraft mc, JsonObject p) {
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        if (screen == null) {
            throw new IllegalStateException("No GUI screen is open");
        }
        String button = normalizeButton(optionalString(p, "button", "LEFT"));
        String target = optionalString(p, "target", optionalString(p, "path", ""));
        ActionTargetLocator.ClickPoint point;
        if (p.has("x") || p.has("y")) {
            if (!p.has("x") || !p.has("y")) {
                throw new IllegalArgumentException("GUI coordinate click requires both x and y");
            }
            int x = integer(p, "x", 0, -100000, 100000);
            int y = integer(p, "y", 0, -100000, 100000);
            point = new ActionTargetLocator.ClickPoint(x, y, "coordinate");
        } else {
            if (target.isEmpty()) {
                throw new IllegalArgumentException("GUI click requires target/path or x/y");
            }
            if (screen instanceof GuiModernMainScreen && target.startsWith("screen/")) {
                // A semantic path may refer to a control below the current
                // viewport. Let the active workbench reveal it before the
                // coordinate compatibility layer resolves its center.
                if (((GuiModernMainScreen) screen).revealMcpTarget(target)) {
                    ((GuiModernMainScreen) screen).prepareMcpGui();
                }
            }
            String mode = target.startsWith("screen/") ? ActionTargetLocator.CLICK_MODE_ELEMENT_PATH
                    : ActionTargetLocator.CLICK_MODE_BUTTON_TEXT;
            point = ActionTargetLocator.resolveScreenClickPoint(mode, target,
                    optionalString(p, "matchMode", ActionTargetLocator.MATCH_MODE_CONTAINS));
            if (point == null) {
                throw new IllegalArgumentException("No visible GUI element matches: " + target);
            }
        }
        if (!ActionTargetLocator.tryInvokeCurrentScreenClick(point.getX(), point.getY(), button)) {
            throw new IllegalStateException("GUI click injection failed");
        }
        JsonObject result = inspect(mc);
        result.addProperty("requestedTarget", target);
        result.add("point", object("x", point.getX(), "y", point.getY(), "description", point.getDescription(),
                "button", button));
        return result;
    }

    private static JsonObject input(Minecraft mc, JsonObject p) {
        String target = requiredTarget(p);
        String text = optionalString(p, "text", "");
        boolean append = bool(p, "append", false);
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        boolean changed = screen instanceof GuiModernMainScreen
                && ((GuiModernMainScreen) screen).setMcpText(target, text, append);
        if (!changed && screen instanceof GuiElementInspector.McpElementProvider) {
            changed = ((GuiElementInspector.McpElementProvider) screen).setMcpText(target, text, append);
        }
        if (!changed && screen != null) {
            changed = GuiElementInspector.setMcpText(screen, target, text, append);
        }
        if (!changed) {
            throw new IllegalArgumentException("No editable GUI input matches: " + target);
        }
        return inspect(mc);
    }

    private static JsonObject set(Minecraft mc, JsonObject p) {
        String target = requiredTarget(p);
        String value = scalarValue(p, "value");
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        boolean changed = screen instanceof GuiModernMainScreen
                && ((GuiModernMainScreen) screen).setMcpValue(target, value);
        if (!changed && screen instanceof GuiElementInspector.McpElementProvider) {
            changed = ((GuiElementInspector.McpElementProvider) screen).setMcpValue(target, value);
        }
        if (!changed && screen != null) {
            changed = GuiElementInspector.setMcpValue(screen, target, value);
        }
        if (!changed) {
            GuiElementInspector.GuiElementInfo element = GuiElementInspector.findFirstByPath(target,
                    ActionTargetLocator.MATCH_MODE_EXACT, GuiElementInspector.ElementType.BUTTON,
                    GuiElementInspector.ElementType.CUSTOM);
            if (element == null) {
                throw new IllegalArgumentException("No editable GUI control matches: " + target);
            }
            String controlType = element.getControlType() == null ? "" : element.getControlType().toLowerCase(Locale.ROOT);
            if ("input".equals(controlType)) {
                changed = screen != null && GuiElementInspector.setMcpText(screen, target, value, false);
            } else if ("toggle".equals(controlType)) {
                Boolean desired = parseBoolean(value);
                if (desired == null) {
                    throw new IllegalArgumentException("Toggle value must be true/false, on/off, yes/no or 1/0");
                }
                String current = element.getValue();
                if (current.isEmpty()) {
                    throw new IllegalArgumentException("Toggle state is not published for " + target
                            + "; use operation=click or add an explicit semantic adapter");
                } else if (desired.toString().equalsIgnoreCase(current)) {
                    changed = true;
                } else {
                    changed = true;
                    if (!ActionTargetLocator.tryInvokeCurrentScreenClick(
                            ActionTargetLocator.CLICK_MODE_ELEMENT_PATH, target,
                            ActionTargetLocator.MATCH_MODE_EXACT, "LEFT")) {
                        changed = false;
                    }
                }
            } else if ("choice".equals(controlType)) {
                String optionPath = findChoicePath(target, value);
                changed = optionPath != null && ActionTargetLocator.tryInvokeCurrentScreenClick(
                        ActionTargetLocator.CLICK_MODE_ELEMENT_PATH, optionPath,
                        ActionTargetLocator.MATCH_MODE_EXACT, "LEFT");
            } else if (containsAction(element, "click")) {
                throw new IllegalArgumentException("GUI control is an action, not a value; use operation=click: " + target);
            }
        }
        if (!changed) {
            throw new IllegalArgumentException("Unable to set GUI control: " + target);
        }
        JsonObject result = inspect(mc);
        result.addProperty("requestedTarget", target);
        result.addProperty("requestedValue", value);
        return result;
    }

    private static JsonObject key(Minecraft mc, JsonObject p) {
        String key = requiredString(p, "key");
        if (p.has("keyCode") || p.has("character") || isGuiKeyName(key)) {
            int keyCode = p.has("keyCode") ? integer(p, "keyCode", 0, 0, 255) : guiKeyCode(key);
            String character = optionalString(p, "character", "");
            char typed = character.isEmpty() ? '\0' : character.charAt(0);
            if (!ActionTargetLocator.tryInvokeCurrentScreenKey(typed, keyCode)) {
                throw new IllegalStateException("GUI key injection failed");
            }
            return inspect(mc);
        }
        String state = optionalString(p, "state", "Press");
        int duration = integer(p, "pressDurationTicks", 10, 1, 2000);
        ModUtils.simulateActionKey(key, state, duration, false);
        return inspect(mc);
    }

    private static JsonObject scroll(Minecraft mc, JsonObject p) {
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        if (!(screen instanceof GuiModernMainScreen)) {
            throw new IllegalStateException("Semantic GUI scrolling is currently available on the modern control center");
        }
        int wheel = integer(p, "wheel", 0, -120000, 120000);
        if (wheel == 0) {
            throw new IllegalArgumentException("wheel cannot be zero");
        }
        String target = optionalString(p, "target", "");
        int x;
        int y;
        if (!target.trim().isEmpty()) {
            GuiElementInspector.GuiElementInfo element = GuiElementInspector.findFirstByPath(target,
                    ActionTargetLocator.MATCH_MODE_EXACT, GuiElementInspector.ElementType.CUSTOM);
            if (element == null) {
                throw new IllegalArgumentException("No visible GUI region matches scroll target: " + target);
            }
            x = element.getX() + Math.max(1, element.getWidth()) / 2;
            y = element.getY() + Math.max(1, element.getHeight()) / 2;
        } else {
            x = integer(p, "x", screen.width / 2, -100000, 100000);
            y = integer(p, "y", screen.height / 2, -100000, 100000);
        }
        GuiModernMainScreen modern = (GuiModernMainScreen) screen;
        modern.handleDetachedMouseWheel(wheel, modern.toModernMouseX(x), modern.toModernMouseY(y));
        JsonObject result = inspect(mc);
        result.add("point", object("x", x, "y", y));
        return result;
    }

    private static JsonObject save(Minecraft mc) {
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        if (!(screen instanceof GuiModernMainScreen)) {
            throw new IllegalStateException("The modern control center is not open");
        }
        ((GuiModernMainScreen) screen).saveActiveConfiguration();
        return inspect(mc);
    }

    private static JsonObject close(Minecraft mc, JsonObject p) {
        GuiScreen screen = GuiElementInspector.getActiveScreen();
        if (screen == null) {
            return inspect(mc);
        }
        if (screen instanceof GuiModernMainScreen) {
            ((GuiModernMainScreen) screen).closeFromMcp(optionalString(p, "scope", "screen"));
        } else {
            mc.displayGuiScreen(null);
            if (mc.player != null && mc.player.openContainer != null
                    && mc.player.inventoryContainer != mc.player.openContainer) {
                mc.player.closeScreen();
            }
        }
        return inspect(mc);
    }

    private static String requiredTarget(JsonObject p) {
        String target = optionalString(p, "target", optionalString(p, "path", ""));
        if (target.isEmpty()) {
            throw new IllegalArgumentException("GUI operation requires target or path");
        }
        return target;
    }

    private static String scalarValue(JsonObject p, String key) {
        if (!p.has(key) || p.get(key).isJsonNull() || !p.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("GUI operation requires scalar: " + key);
        }
        return p.get(key).getAsString();
    }

    private static Boolean parseBoolean(String value) {
        if (value == null) return null;
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "1".equals(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value) || "0".equals(value)) return Boolean.FALSE;
        return null;
    }

    private static boolean containsAction(GuiElementInspector.GuiElementInfo element, String action) {
        return element != null && action != null && element.getActions().contains(action);
    }

    private static String findChoicePath(String target, String value) {
        GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
        String prefix = target == null ? "" : target.trim();
        for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
            if (element == null || !"choice_option".equalsIgnoreCase(element.getControlType())) continue;
            if (element.getPath().startsWith(prefix + "/choice/")
                    && (value.equalsIgnoreCase(element.getValue()) || value.equalsIgnoreCase(element.getText()))) {
                return element.getPath();
            }
        }
        return null;
    }

    private static boolean isGuiKeyName(String key) {
        String value = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        return value.equals("ESC") || value.equals("ESCAPE") || value.equals("ENTER")
                || value.equals("RETURN") || value.equals("TAB") || value.equals("BACKSPACE")
                || value.equals("DELETE") || value.equals("LEFT") || value.equals("RIGHT")
                || value.equals("UP") || value.equals("DOWN") || value.equals("HOME")
                || value.equals("END") || value.equals("PAGEUP") || value.equals("PAGEDOWN")
                || value.equals("SPACE");
    }

    private static int guiKeyCode(String key) {
        String value = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        switch (value) {
        case "ESC": case "ESCAPE": return org.lwjgl.input.Keyboard.KEY_ESCAPE;
        case "ENTER": case "RETURN": return org.lwjgl.input.Keyboard.KEY_RETURN;
        case "TAB": return org.lwjgl.input.Keyboard.KEY_TAB;
        case "BACKSPACE": return org.lwjgl.input.Keyboard.KEY_BACK;
        case "DELETE": return org.lwjgl.input.Keyboard.KEY_DELETE;
        case "LEFT": return org.lwjgl.input.Keyboard.KEY_LEFT;
        case "RIGHT": return org.lwjgl.input.Keyboard.KEY_RIGHT;
        case "UP": return org.lwjgl.input.Keyboard.KEY_UP;
        case "DOWN": return org.lwjgl.input.Keyboard.KEY_DOWN;
        case "HOME": return org.lwjgl.input.Keyboard.KEY_HOME;
        case "END": return org.lwjgl.input.Keyboard.KEY_END;
        case "PAGEUP": return org.lwjgl.input.Keyboard.KEY_PRIOR;
        case "PAGEDOWN": return org.lwjgl.input.Keyboard.KEY_NEXT;
        case "SPACE": return org.lwjgl.input.Keyboard.KEY_SPACE;
        default: return key != null && key.length() == 1 ? org.lwjgl.input.Keyboard.getKeyIndex(key.toUpperCase(Locale.ROOT)) : 0;
        }
    }

    private static String requiredString(JsonObject p, String key) {
        if (!p.has(key) || p.get(key).isJsonNull() || !p.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("GUI operation requires string: " + key);
        }
        return p.get(key).getAsString();
    }

    private static String optionalString(JsonObject p, String key, String fallback) {
        if (p == null || !p.has(key) || p.get(key).isJsonNull()) {
            return fallback == null ? "" : fallback;
        }
        if (!p.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected string: " + key);
        }
        return p.get(key).getAsString();
    }

    private static String normalizeButton(String value) {
        String normalized = value == null ? "LEFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!"LEFT".equals(normalized) && !"RIGHT".equals(normalized) && !"MIDDLE".equals(normalized)) {
            throw new IllegalArgumentException("button must be LEFT, RIGHT or MIDDLE");
        }
        return normalized;
    }
}
