package com.zszl.zszlScriptMod.gui.modern.profile;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.legacy.EmbeddedLegacyWorkbenchTab;

import net.minecraft.client.Minecraft;

/** Hosts the complete three-pane profile workbench inside the modern shell. */
public final class ProfileWorkbenchTab {
    private ProfileWorkbenchTab() {
    }

    public static ModernSettingsTab create() {
        return create("profile_manager");
    }

    public static ModernSettingsTab create(String command) {
        return EmbeddedLegacyWorkbenchTab.profile(Minecraft.getMinecraft());
    }
}
