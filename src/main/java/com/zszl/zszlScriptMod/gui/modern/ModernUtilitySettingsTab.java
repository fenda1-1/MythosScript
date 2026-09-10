package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.modern.packet.PacketWorkbenchTab;

import net.minecraft.client.Minecraft;

/**
 * Modern landing tabs for utility workbenches that intentionally retain a
 * richer, dedicated editor.  Keeping these entry points in one factory makes
 * the main dashboard's command routing explicit and avoids duplicating
 * metrics or parent-screen handling in each utility page.
 */
public final class ModernUtilitySettingsTab {

    private ModernUtilitySettingsTab() {
    }

    public static ModernSettingsTab packet() {
        return new PacketWorkbenchTab(Minecraft.getMinecraft(), null);
    }

    public static ModernSettingsTab packet(Minecraft minecraft, com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext context) {
        return new PacketWorkbenchTab(minecraft, context);
    }

    public static ModernSettingsTab guiInspector() {
        return new ModernGuiInspectorSettingsTab();
    }

    public static ModernSettingsTab performance() {
        return new ModernPerformanceSettingsTab();
    }

    public static ModernSettingsTab terrainScanner() {
        return new ModernTerrainScannerSettingsTab();
    }

    public static ModernSettingsTab memory() {
        return ModernMemorySettingsTab.create();
    }

    private static ModernManagerOverviewTab.Action action(String title, String tooltip, String label,
            ModernManagerOverviewTab.ScreenFactory factory) {
        return new ModernManagerOverviewTab.Action(title, tooltip, label, factory);
    }
}
