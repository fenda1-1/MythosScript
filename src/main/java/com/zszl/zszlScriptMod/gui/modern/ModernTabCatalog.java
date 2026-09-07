package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabFactory;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabRegistry;
import com.zszl.zszlScriptMod.gui.modern.baritone.BaritoneModernRoutes;
import com.zszl.zszlScriptMod.gui.modern.keybind.KeybindModernRoutes;
import com.zszl.zszlScriptMod.gui.modern.nonmanagement.ModernNonManagementRoutes;
import com.zszl.zszlScriptMod.gui.modern.packet.PacketModernRoutes;
import com.zszl.zszlScriptMod.gui.modern.path.ModernPathRoutes;
import com.zszl.zszlScriptMod.gui.modern.profile.ProfileModernRoutes;
import com.zszl.zszlScriptMod.gui.modern.rules.RulesModernRoutes;
import com.zszl.zszlScriptMod.gui.modern.utilities.ModernUtilityRoutes;
import com.zszl.zszlScriptMod.gui.modern.world.ModernWorldRoutes;

import net.minecraft.client.Minecraft;

/** Composition root for tabs embedded by the modern shell. */
public final class ModernTabCatalog {

    private ModernTabCatalog() {
    }

    public static ModernTabRegistry createRegistry() {
        ModernTabRegistry registry = new ModernTabRegistry();
        register(registry, "general_settings", "gui.modern.tabcat.u001", new ModernTabFactory() {
            @Override public ModernSettingsTab create(Minecraft mc, ModernScreenContext context) {
                return ModernGeneralSettingsTab.create(context);
            }
        });
        register(registry, "autoeat", "gui.modern.tabcat.u002", new ModernTabFactory() {
            @Override public ModernSettingsTab create(Minecraft mc, ModernScreenContext context) {
                return ModernAutoEatSettingsTab.create();
            }
        });
        register(registry, "toggle_auto_fishing", "gui.modern.tabcat.u003", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return ModernAutoFishingSettingsTab.create(); }
        }));
        register(registry, "toggle_fly", "gui.modern.tabcat.u004", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return ModernFlySettingsTab.create(); }
        }));
        register(registry, "setloop", "gui.modern.tabcat.u005", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return new ModernLoopCountSettingsTab(); }
        }));
        register(registry, "current_resolution_info", "gui.modern.tabcat.u006", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return new ModernResolutionSettingsTab(); }
        }));
        register(registry, "chat_optimization", "gui.modern.tabcat.u007", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return ModernChatOptimizationSettingsTab.create(); }
        }));
        register(registry, "toggle_kill_aura", "gui.modern.tabcat.u008", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return ModernKillAuraSettingsTab.create(); }
        }));
        registerMissing(registry, RulesModernRoutes.routes());
        registerMissing(registry, PacketModernRoutes.routes());
        registerMissing(registry, ProfileModernRoutes.routes());
        registerMissing(registry, KeybindModernRoutes.routes());
        registerMissing(registry, BaritoneModernRoutes.routes());
        registerMissing(registry, ModernPathRoutes.routes());
        registerMissing(registry, ModernUtilityRoutes.routes());
        registerMissing(registry, ModernWorldRoutes.routes());
        registerMissing(registry, ModernNonManagementRoutes.routes());
        return registry;
    }

    private interface TabCreator {
        ModernSettingsTab create();
    }

    private static ModernTabFactory factory(final TabCreator creator) {
        return new ModernTabFactory() {
            @Override public ModernSettingsTab create(Minecraft mc, ModernScreenContext context) {
                return creator.create();
            }
        };
    }

    private static void register(ModernTabRegistry registry, String command, String title, ModernTabFactory factory) {
        registry.register(new ModernTabDescriptor(command, title, factory));
    }

    private static void registerMissing(ModernTabRegistry registry,
            java.util.List<ModernTabDescriptor> descriptors) {
        for (ModernTabDescriptor descriptor : descriptors) {
            if (registry.find(descriptor.getCommand()) == null) {
                registry.register(descriptor);
            }
        }
    }
}
