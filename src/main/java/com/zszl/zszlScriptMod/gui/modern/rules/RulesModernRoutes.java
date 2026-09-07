package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;
import com.zszl.zszlScriptMod.gui.modern.ModernWarehouseWorkbenchTab;
import net.minecraft.client.Minecraft;

/** Composition data for the automation domain; the shell registers these entries. */
public final class RulesModernRoutes {
    private static final String OTHER_PREFIX = "other_feature:";
    private static final String[] COMMANDS = { "toggle_auto_pickup", "warehouse_manager", "toggle_auto_use_item",
            "block_replacement_config", "followconfig", "conditional_execution", "auto_escape" };
    private static final String[] TITLES = { "自动拾取", "智能仓库", "自动使用物品", "方块替换",
            "自动追怪", "条件执行", "自动逃离" };
    private static final String[] COMPATIBILITY_COMMANDS = { "autopickup", "warehouse", "auto_use_item" };

    private RulesModernRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        List<ModernTabDescriptor> result = new ArrayList<>();
        for (int i = 0; i < COMMANDS.length; i++) {
            result.add(descriptor(COMMANDS[i], TITLES[i], i));
        }
        result.add(descriptor(COMPATIBILITY_COMMANDS[0], TITLES[0], 0));
        result.add(descriptor(COMPATIBILITY_COMMANDS[1], TITLES[1], 1));
        result.add(descriptor(COMPATIBILITY_COMMANDS[2], TITLES[2], 2));
        // GuiModernMainScreen owns other_feature:* as a single replaceable tab
        // instance. Do not expose the empty RulesWorkbenchTab as a registry
        // fallback for these dynamic entries.
        return result;
    }

    public static boolean isDynamicCommand(String command) {
        return command != null && command.toLowerCase(Locale.ROOT).startsWith(OTHER_PREFIX)
                && isValidFeatureId(command.substring(OTHER_PREFIX.length()));
    }

    public static boolean isKnownCommand(String command) {
        if (command == null) return false;
        for (String known : COMMANDS) if (known.equals(command)) return true;
        for (String known : COMPATIBILITY_COMMANDS) if (known.equals(command)) return true;
        return isDynamicCommand(command);
    }

    public static String featureId(String command) {
        return isDynamicCommand(command) ? normalize(command.substring(OTHER_PREFIX.length())) : "";
    }

    private static ModernTabDescriptor descriptor(final String command, String title, final int type) {
        return new ModernTabDescriptor(command, title, (Minecraft minecraft, ModernScreenContext context) ->
                createTab(command, title, context, type));
    }

    private static com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab createTab(String command, String title,
            ModernScreenContext context, int type) {
        switch (type) {
        case 0: return new ModernAutoPickupWorkbenchTab();
        case 1: return new ModernWarehouseWorkbenchTab();
        case 2: return new ModernAutoUseItemWorkbenchTab();
        case 3: return new ModernBlockReplacementWorkbenchTab();
        case 4: return new ModernAutoFollowWorkbenchTab();
        case 5: return new ModernConditionalExecutionWorkbenchTab();
        case 6: return new ModernAutoEscapeWorkbenchTab();
        default: throw new IllegalArgumentException("No concrete rules workbench for " + command);
        }
    }

    private static boolean isValidFeatureId(String value) {
        String id = normalize(value);
        return !id.isEmpty() && id.length() <= 80 && id.matches("[a-z0-9][a-z0-9_.-]*");
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
