package com.zszl.zszlScriptMod.gui.modern.world;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernOtherFeatureSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernWarehouseWorkbenchTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Routes for world state and read-only player data workbenches. */
public final class ModernWorldRoutes {
    private ModernWorldRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        return Collections.unmodifiableList(Arrays.asList(
                new ModernTabDescriptor("warehouse_manager", "gui.modern.worldroute.u001",
                        (minecraft, context) -> new ModernWarehouseWorkbenchTab()),
                new ModernTabDescriptor("player_equipment_viewer", "gui.modern.worldroute.u002",
                        (minecraft, context) -> PlayerEquipmentWorkbenchTab.create(minecraft)),
                new ModernTabDescriptor("nbt_detail", "gui.modern.worldroute.u003",
                        (minecraft, context) -> new ModernNbtDetailSettingsTab(minecraft)),
                new ModernTabDescriptor("nbt_editor", "gui.modern.worldroute.u004",
                        (minecraft, context) -> new ModernNbtEditorSettingsTab(minecraft)),
                new ModernTabDescriptor("world_features", "gui.modern.worldroute.u005",
                        (minecraft, context) -> ModernOtherFeatureSettingsTab.create("coord_display", "gui.modern.worldroute.u006",
                                "gui.modern.worldroute.u007")),
                new ModernTabDescriptor("coord_display", "gui.modern.worldroute.u006",
                        (minecraft, context) -> ModernOtherFeatureSettingsTab.create("coord_display", "gui.modern.worldroute.u006",
                                "gui.modern.worldroute.u007"))));
    }

    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor descriptor : routes()) {
            if (descriptor.getCommand().equals(command)) {
                return descriptor.getFactory().create(context == null ? null : context.getMinecraft(), context);
            }
        }
        return null;
    }

}
