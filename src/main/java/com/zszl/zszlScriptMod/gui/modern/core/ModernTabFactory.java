package com.zszl.zszlScriptMod.gui.modern.core;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;

import net.minecraft.client.Minecraft;

/** Creates one modern tab without coupling the registry to a feature domain. */
public interface ModernTabFactory {

    ModernSettingsTab create(Minecraft minecraft, ModernScreenContext context);
}
