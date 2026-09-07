package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.zszl.zszlScriptMod.utils.ModUtils;

import net.minecraft.client.resources.I18n;

/**
 * Contextual clickType + button display. Button 0/1/2 is never shown as a
 * generic mouse button outside the matching ClickType.
 */
public final class ClickSemantics {
    public static final class Option {
        public final String clickType;
        public final int button;
        public final String labelKey;
        public final String hintKey;
        public final boolean usesTargetCanvas;
        public final boolean supportsSlotLimits;
        public final boolean disabled;

        Option(String clickType, int button, String labelKey, String hintKey, boolean usesTargetCanvas,
                boolean supportsSlotLimits, boolean disabled) {
            this.clickType = clickType;
            this.button = button;
            this.labelKey = labelKey;
            this.hintKey = hintKey;
            this.usesTargetCanvas = usesTargetCanvas;
            this.supportsSlotLimits = supportsSlotLimits;
            this.disabled = disabled;
        }

        public String label() {
            if ("SWAP".equals(clickType)) {
                return I18n.format(labelKey, Integer.valueOf(button + 1));
            }
            return I18n.format(labelKey);
        }

        public String hint() {
            return hintKey == null || hintKey.isEmpty() ? "" : I18n.format(hintKey);
        }
    }

    private ClickSemantics() {
    }

    public static List<Option> options() {
        List<Option> options = new ArrayList<Option>();
        options.add(new Option("PICKUP", 0, "gui.path.action_editor.click.pickup_left",
                "gui.path.action_editor.move_chest.tooltip.pickup_left", true, true, false));
        options.add(new Option("PICKUP", 1, "gui.path.action_editor.click.pickup_right",
                "gui.path.action_editor.move_chest.tooltip.pickup_right", false, false, false));
        options.add(new Option("QUICK_MOVE", 0, "gui.path.action_editor.click.quick_move_left",
                "gui.path.action_editor.move_chest.tooltip.quick_move", false, false, false));
        options.add(new Option("QUICK_MOVE", 1, "gui.path.action_editor.click.quick_move_right",
                "gui.path.action_editor.move_chest.tooltip.quick_move", false, false, false));
        options.add(new Option("THROW", 0, "gui.path.action_editor.click.throw_one",
                "gui.path.action_editor.move_chest.tooltip.throw", false, false, false));
        options.add(new Option("THROW", 1, "gui.path.action_editor.click.throw_all",
                "gui.path.action_editor.move_chest.tooltip.throw", false, false, false));
        options.add(new Option("PICKUP_ALL", 0, "gui.path.action_editor.click.pickup_all",
                "gui.path.action_editor.move_chest.tooltip.pickup_all", false, false, false));
        options.add(new Option("CLONE", 0, "gui.path.action_editor.click.clone",
                "gui.path.action_editor.move_chest.tooltip.clone", false, false, false));
        for (int hotbar = 0; hotbar <= 8; hotbar++) {
            options.add(new Option("SWAP", hotbar, "gui.path.action_editor.click.swap_hotbar",
                    "gui.path.action_editor.move_chest.tooltip.swap", false, false, false));
        }
        options.add(new Option("QUICK_CRAFT", 0, "gui.path.action_editor.click.quick_craft",
                "gui.path.action_editor.move_chest.tooltip.quick_craft", false, false, true));
        return options;
    }

    public static Option resolve(String clickType, int button) {
        String type = ModUtils.normalizeClickTypeName(clickType);
        int safeButton = Math.max(0, button);
        Option fallback = null;
        for (Option option : options()) {
            if (!option.clickType.equals(type)) {
                continue;
            }
            if (fallback == null) {
                fallback = option;
            }
            if (option.button == safeButton) {
                return option;
            }
        }
        return fallback != null ? fallback : options().get(0);
    }

    public static String display(String clickType, int button) {
        return resolve(clickType, button).label();
    }

    public static String displayClickType(String clickType) {
        String type = ModUtils.normalizeClickTypeName(clickType).toLowerCase(Locale.ROOT);
        String key = "gui.path.action_editor.option.click_type." + type;
        String translated = I18n.format(key);
        return translated.equals(key) ? ModUtils.clickTypeToDisplayName(clickType) : translated;
    }

    public static String displayButton(String clickType, int button) {
        String type = ModUtils.normalizeClickTypeName(clickType);
        if ("PICKUP".equals(type) || "QUICK_MOVE".equals(type)) {
            return button == 1
                    ? I18n.format("gui.path.action_editor.option.mouse_button.right")
                    : I18n.format("gui.path.action_editor.option.mouse_button.left");
        }
        if ("THROW".equals(type)) {
            return button == 1
                    ? I18n.format("gui.path.action_editor.click.throw_all")
                    : I18n.format("gui.path.action_editor.click.throw_one");
        }
        if ("SWAP".equals(type)) {
            return I18n.format("gui.path.action_editor.click.swap_hotbar", Integer.valueOf(Math.max(0, button) + 1));
        }
        if ("CLONE".equals(type)) {
            return I18n.format("gui.path.action_editor.click.clone");
        }
        if ("PICKUP_ALL".equals(type)) {
            return I18n.format("gui.path.action_editor.click.pickup_all");
        }
        if ("QUICK_CRAFT".equals(type)) {
            return I18n.format("gui.path.action_editor.click.quick_craft");
        }
        return String.valueOf(button);
    }

    public static boolean usesTargetCanvas(String clickType, int button) {
        return resolve(clickType, button).usesTargetCanvas;
    }

    public static boolean supportsSlotLimits(String clickType, int button) {
        return resolve(clickType, button).supportsSlotLimits;
    }

    public static boolean isIllegalSwapButton(String clickType, int button) {
        return "SWAP".equals(ModUtils.normalizeClickTypeName(clickType)) && (button < 0 || button > 8);
    }
}
