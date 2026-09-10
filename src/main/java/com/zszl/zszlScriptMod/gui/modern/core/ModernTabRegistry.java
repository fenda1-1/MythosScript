package com.zszl.zszlScriptMod.gui.modern.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;

import net.minecraft.client.Minecraft;

/** The only command registry used by the modern window composition root. */
public final class ModernTabRegistry {

    private final Map<String, ModernTabDescriptor> descriptors = new LinkedHashMap<>();

    public void register(ModernTabDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        String command = descriptor.getCommand();
        if (descriptors.containsKey(command)) {
            throw new IllegalArgumentException("Duplicate modern tab command: " + command);
        }
        descriptors.put(command, descriptor);
    }

    public ModernTabDescriptor find(String command) {
        if (command == null) {
            return null;
        }
        return descriptors.get(command);
    }

    public ModernSettingsTab create(String command, Minecraft minecraft, ModernScreenContext context) {
        ModernTabDescriptor descriptor = find(command);
        return descriptor == null ? null : descriptor.getFactory().create(minecraft, context);
    }

    public Set<String> commands() {
        return Collections.unmodifiableSet(descriptors.keySet());
    }
}
