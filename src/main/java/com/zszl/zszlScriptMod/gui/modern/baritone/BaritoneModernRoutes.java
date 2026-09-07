package com.zszl.zszlScriptMod.gui.modern.baritone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.zszl.zszlScriptMod.gui.modern.ModernBaritoneParkourSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernFlightPathingSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Modern Baritone command, setting, block mapping and parkour routes. */
public final class BaritoneModernRoutes {
    private BaritoneModernRoutes() { }
    public static List<ModernTabDescriptor> routes() {
        return Collections.unmodifiableList(Arrays.asList(
                new ModernTabDescriptor("baritone_settings", "gui.modern.baritone_route.u001",
                        (minecraft, context) -> ModernBaritoneSettingsTab.create()),
                new ModernTabDescriptor("baritone_command_table", "gui.modern.baritone_route.u002",
                        (minecraft, context) -> ModernBaritoneCommandTableTab.create()),
                new ModernTabDescriptor("baritone_setting_editor", "gui.modern.baritone_route.u003",
                        (minecraft, context) -> BaritoneWorkbenchTab.create("baritone_setting_editor")),
                new ModernTabDescriptor("baritone_block_list", "gui.modern.baritone_route.u004",
                        (minecraft, context) -> BaritoneWorkbenchTab.create("baritone_block_list")),
                new ModernTabDescriptor("baritone_block_map", "gui.modern.baritone_route.u005",
                        (minecraft, context) -> BaritoneWorkbenchTab.create("baritone_block_map")),
                new ModernTabDescriptor("baritone_parkour", "gui.modern.baritone_route.u006",
                        (minecraft, context) -> ModernBaritoneParkourSettingsTab.create("baritone_parkour")),
                new ModernTabDescriptor("baritone_flight_pathing", "gui.modern.baritone_route.u007",
                        (minecraft, context) -> ModernFlightPathingSettingsTab.create()),
                new ModernTabDescriptor("baritone_parkour_preset", "gui.modern.baritone_route.u008",
                        (minecraft, context) -> ModernBaritoneParkourSettingsTab.create("baritone_parkour_preset"))));
    }
    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor route : routes()) if (route.getCommand().equals(command))
            return route.getFactory().create(context == null ? null : context.getMinecraft(), context);
        return null;
    }
}
