package com.mythos.mythosScriptMod.gui.modern.core;

import com.mythos.mythosScriptMod.gui.modern.ModernSettingsTab;

import net.minecraft.client.Minecraft;

/** Creates one modern tab without coupling the registry to a feature domain. */
public interface ModernTabFactory {

    ModernSettingsTab create(Minecraft minecraft, ModernScreenContext context);
}
