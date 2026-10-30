package com.mythos.mythosScriptMod.gui.modern;

import com.mythos.mythosScriptMod.gui.modern.core.ModernScreenContext;
import com.mythos.mythosScriptMod.gui.modern.core.ModernTabDescriptor;
import com.mythos.mythosScriptMod.gui.modern.core.ModernTabFactory;
import com.mythos.mythosScriptMod.gui.modern.core.ModernTabRegistry;
import com.mythos.mythosScriptMod.gui.modern.baritone.BaritoneModernRoutes;
import com.mythos.mythosScriptMod.gui.modern.keybind.KeybindModernRoutes;
import com.mythos.mythosScriptMod.gui.modern.nonmanagement.ModernNonManagementRoutes;
import com.mythos.mythosScriptMod.gui.modern.packet.PacketModernRoutes;
import com.mythos.mythosScriptMod.gui.modern.path.ModernPathRoutes;
import com.mythos.mythosScriptMod.gui.modern.plugin.ModernPluginCenterTab;
import com.mythos.mythosScriptMod.gui.modern.profile.ProfileModernRoutes;
import com.mythos.mythosScriptMod.gui.modern.rules.RulesModernRoutes;
import com.mythos.mythosScriptMod.gui.modern.utilities.ModernUtilityRoutes;
import com.mythos.mythosScriptMod.gui.modern.world.ModernWorldRoutes;

import net.minecraft.client.Minecraft;

/** Composition root for tabs embedded by the modern shell. */
public final class ModernTabCatalog {

    private ModernTabCatalog() {
    }

    public static ModernTabRegistry createRegistry() {
        ModernTabRegistry registry = new ModernTabRegistry();
        register(registry, "mcp_settings", "gui.mcp.title", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return new ModernMcpSettingsTab(); }
        }));
        register(registry, "general_settings", "gui.modern.tabcat.u001", new ModernTabFactory() {
            @Override public ModernSettingsTab create(Minecraft mc, ModernScreenContext context) {
                return ModernGeneralSettingsTab.create(context);
            }
        });
        register(registry, "other_feature_hud_position", "gui.modern.hud_position.title", new ModernTabFactory() {
            @Override public ModernSettingsTab create(Minecraft mc, ModernScreenContext context) {
                return new ModernHudPositionSettingsTab(mc);
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
        register(registry, "plugin_center", "gui.modern.plugin.title", factory(new TabCreator() {
            @Override public ModernSettingsTab create() { return new ModernPluginCenterTab(); }
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
        registerPluginTabs(registry);
        return registry;
    }

    /**
     * Appends tabs contributed by running plugins.
     *
     * <p>A plugin route never replaces a built-in one: the registry is asked first,
     * so a plugin that picks a colliding name simply does not get a tab. Failing
     * loudly here would turn a naming mistake into a broken main window.</p>
     *
     * <p>Idempotent: calling it again after more plugins loaded only adds what is
     * still missing, which is what lets an open screen pick up a hot-loaded plugin.</p>
     */
    public static void registerPluginTabs(ModernTabRegistry registry) {
        for (com.mythos.mythosScriptMod.gui.modern.plugin.PluginLoader.TabRegistration tab
                : com.mythos.mythosScriptMod.gui.modern.plugin.PluginLoader.tabs()) {
            if (registry.find(tab.command) != null) {
                continue;
            }
            registry.register(new ModernTabDescriptor(tab.command, tab.title, new ModernTabFactory() {
                @Override public ModernSettingsTab create(Minecraft mc, ModernScreenContext context) {
                    return tab.factory.create();
                }
            }));
        }
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
