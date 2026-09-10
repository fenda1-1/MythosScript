package com.zszl.zszlScriptMod.gui.modern.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMemorySettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernDebugSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernResolutionSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernTerrainScannerSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUtilitySettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernWarehouseWorkbenchTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Command routes for utility workbenches. */
public final class ModernUtilityRoutes {
    private ModernUtilityRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        List<ModernTabDescriptor> routes = new ArrayList<>();
        routes.add(descriptor("gui_inspector_manager", "gui.modern.utilroute.u001", (minecraft, context) -> ModernUtilitySettingsTab.guiInspector()));
        routes.add(descriptor("performance_monitor", "gui.modern.utilroute.u002", (minecraft, context) -> ModernUtilitySettingsTab.performance()));
        routes.add(descriptor("terrain_scanner", "gui.modern.utilroute.u003", (minecraft, context) -> ModernUtilitySettingsTab.terrainScanner()));
        routes.add(descriptor("memory_manager", "gui.modern.utilroute.u004", (minecraft, context) -> ModernMemorySettingsTab.create()));
        routes.add(descriptor("current_resolution_info", "gui.modern.utilroute.u005", (minecraft, context) -> new ModernResolutionSettingsTab()));
        routes.add(descriptor("debug_settings", "gui.modern.utilroute.u006", (minecraft, context) -> new ModernDebugSettingsTab()));
        routes.add(descriptor("smart_warehouse", "gui.modern.utilroute.u007",
                (minecraft, context) -> new ModernWarehouseWorkbenchTab()));
        routes.add(descriptor("notes", "gui.modern.utilroute.notes", (minecraft, context) -> new ModernNotesTab()));
        return Collections.unmodifiableList(routes);
    }

    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor route : routes()) {
            if (route.getCommand().equals(command)) {
                return route.getFactory().create(context == null ? null : context.getMinecraft(), context);
            }
        }
        return null;
    }

    private static ModernTabDescriptor descriptor(String command, String title,
            com.zszl.zszlScriptMod.gui.modern.core.ModernTabFactory factory) {
        return new ModernTabDescriptor(command, title, factory);
    }

}
