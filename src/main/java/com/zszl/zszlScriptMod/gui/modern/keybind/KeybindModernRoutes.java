package com.zszl.zszlScriptMod.gui.modern.keybind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Modern action, script shortcut, conflict and recorder routes. */
public final class KeybindModernRoutes {
    private KeybindModernRoutes() { }
    public static List<ModernTabDescriptor> routes() {
        return Collections.unmodifiableList(Arrays.asList(
                new ModernTabDescriptor("keybind_manager", "gui.modern.keybind_route.u001",
                        (minecraft, context) -> createWorkbench("keybind_manager")),
                route("keybind_recorder", "gui.modern.keybind_route.u002", "gui.modern.keybind_route.u003", "keybind_manager"),
                route("keybind_conflicts", "gui.modern.keybind_route.u004", "gui.modern.keybind_route.u005", "keybind_manager"),
                route("keybind_scripts", "gui.modern.keybind_route.u006", "gui.modern.keybind_route.u007", "keybind_manager")));
    }
    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor route : routes()) if (route.getCommand().equals(command))
            return createWorkbench(command);
        return null;
    }
    private static ModernTabDescriptor route(String command, String title, String subtitle, String... children) {
        return new ModernTabDescriptor(command, title, (minecraft, context) -> createWorkbench(command));
    }
    private static ModernSettingsTab createWorkbench(String command) {
        ModernSettingsTab tab = KeybindWorkbenchTab.create();
        tab.focusCommand(command);
        return tab;
    }
}
