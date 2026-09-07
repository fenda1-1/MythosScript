package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.handlers.AutoEatHandler;

/** Modern embedded settings page for automatic eating. */
public final class ModernAutoEatSettingsTab {

    private ModernAutoEatSettingsTab() {
    }

    public static ModernSettingsTab create() {
        return ModernFormSettingsTab.builder("gui.modern.autoeat.title", "gui.modern.autoeat.subtitle",
                "gui.modern.autoeat.header_tip", adapter())
                .headerToggle(bool("enabled"), "gui.modern.autoeat.header_toggle_tip")
                .section("gui.modern.autoeat.section.strategy", "gui.modern.autoeat.section.strategy.tip")
                .integer("gui.modern.autoeat.threshold", "gui.modern.autoeat.threshold.tip", integer("threshold"), 0,
                        20)
                .toggle("gui.modern.autoeat.auto_move", "gui.modern.autoeat.auto_move.tip", bool("autoMove"))
                .toggle("gui.modern.autoeat.look_down", "gui.modern.autoeat.look_down.tip", bool("lookDown"))
                .toggle("gui.modern.autoeat.smooth_look_down", "gui.modern.autoeat.smooth_look_down.tip",
                        bool("smoothLookDown"))
                .toggle("gui.modern.autoeat.pathing_only", "gui.modern.autoeat.pathing_only.tip", bool("pathingOnly"))
                .section("gui.modern.autoeat.section.filter", "gui.modern.autoeat.section.filter.tip")
                .integer("gui.modern.autoeat.hotbar", "gui.modern.autoeat.hotbar.tip", integer("hotbarSlot"), 1,
                        9)
                .text("gui.modern.autoeat.keywords", "gui.modern.autoeat.keywords.tip", text("keywords"),
                        "gui.modern.autoeat.keywords.placeholder", 256)
                .build().compactHeader();
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                AutoEatHandler.loadAutoEatConfig();
            }

            @Override
            public State capture() {
                return new State();
            }

            @Override
            public void restore(State state) {
                if (state != null) {
                    state.apply();
                }
            }

            @Override
            public void save() {
                AutoEatHandler.saveAutoEatConfig();
            }

            @Override
            public boolean supportsDefaults() {
                return true;
            }

            @Override
            public State createDefaults() {
                State original = new State();
                State.applyDefaults();
                State defaults = new State();
                original.apply();
                return defaults;
            }
        };
    }

    private static ModernFormSettingsTab.BooleanValue bool(final String name) {
        return new ModernFormSettingsTab.BooleanValue() {
            @Override
            public boolean get() {
                return State.getBoolean(name);
            }

            @Override
            public void set(boolean value) {
                State.setBoolean(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.IntValue integer(final String name) {
        return new ModernFormSettingsTab.IntValue() {
            @Override
            public int get() {
                return State.getInteger(name);
            }

            @Override
            public void set(int value) {
                State.setInteger(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.TextValue text(final String name) {
        return new ModernFormSettingsTab.TextValue() {
            @Override
            public String get() {
                return State.getText(name);
            }

            @Override
            public void set(String value) {
                State.setText(name, value);
            }
        };
    }

    /** Defensive snapshot of every persisted automatic-eating value. */
    private static final class State {
        private final boolean enabled = AutoEatHandler.autoEatEnabled;
        private final int threshold = AutoEatHandler.foodLevelThreshold;
        private final boolean autoMove = AutoEatHandler.autoMoveFoodEnabled;
        private final boolean lookDown = AutoEatHandler.eatWithLookDown;
        private final boolean smoothLookDown = AutoEatHandler.smoothLookDown;
        private final boolean pathingOnly = AutoEatHandler.onlyDuringSequenceStepPathing;
        private final int hotbarSlot = AutoEatHandler.targetHotbarSlot;
        private final List<String> keywords = new ArrayList<>(AutoEatHandler.foodKeywords == null
                ? AutoEatHandler.DEFAULT_FOOD_KEYWORDS : AutoEatHandler.foodKeywords);

        private void apply() {
            AutoEatHandler.autoEatEnabled = enabled;
            AutoEatHandler.foodLevelThreshold = threshold;
            AutoEatHandler.autoMoveFoodEnabled = autoMove;
            AutoEatHandler.eatWithLookDown = lookDown;
            AutoEatHandler.smoothLookDown = smoothLookDown;
            AutoEatHandler.onlyDuringSequenceStepPathing = pathingOnly;
            AutoEatHandler.targetHotbarSlot = hotbarSlot;
            AutoEatHandler.foodKeywords = new ArrayList<>(keywords);
        }

        private static void applyDefaults() {
            AutoEatHandler.foodLevelThreshold = 12;
            AutoEatHandler.autoMoveFoodEnabled = true;
            AutoEatHandler.eatWithLookDown = false;
            AutoEatHandler.smoothLookDown = true;
            AutoEatHandler.onlyDuringSequenceStepPathing = true;
            AutoEatHandler.targetHotbarSlot = 9;
            AutoEatHandler.foodKeywords = new ArrayList<>(AutoEatHandler.DEFAULT_FOOD_KEYWORDS);
        }

        private static boolean getBoolean(String name) {
            switch (name) {
                case "enabled": return AutoEatHandler.autoEatEnabled;
                case "autoMove": return AutoEatHandler.autoMoveFoodEnabled;
                case "lookDown": return AutoEatHandler.eatWithLookDown;
                case "smoothLookDown": return AutoEatHandler.smoothLookDown;
                case "pathingOnly": return AutoEatHandler.onlyDuringSequenceStepPathing;
                default: return false;
            }
        }

        private static void setBoolean(String name, boolean value) {
            switch (name) {
                case "enabled": AutoEatHandler.autoEatEnabled = value; break;
                case "autoMove": AutoEatHandler.autoMoveFoodEnabled = value; break;
                case "lookDown": AutoEatHandler.eatWithLookDown = value; break;
                case "smoothLookDown": AutoEatHandler.smoothLookDown = value; break;
                case "pathingOnly": AutoEatHandler.onlyDuringSequenceStepPathing = value; break;
                default: break;
            }
        }

        private static int getInteger(String name) {
            if ("threshold".equals(name)) return AutoEatHandler.foodLevelThreshold;
            if ("hotbarSlot".equals(name)) return AutoEatHandler.targetHotbarSlot;
            return 0;
        }

        private static void setInteger(String name, int value) {
            if ("threshold".equals(name)) AutoEatHandler.foodLevelThreshold = value;
            if ("hotbarSlot".equals(name)) AutoEatHandler.targetHotbarSlot = value;
        }

        private static String getText(String name) {
            if (!"keywords".equals(name)) return "";
            List<String> values = AutoEatHandler.foodKeywords;
            return values == null ? "" : String.join(", ", values);
        }

        private static void setText(String name, String value) {
            if (!"keywords".equals(name)) return;
            List<String> parsed = new ArrayList<>();
            String[] entries = (value == null ? "" : value).replace('，', ',').split(",");
            for (String entry : entries) {
                String trimmed = entry == null ? "" : entry.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed);
                }
            }
            if (parsed.isEmpty()) {
                parsed.addAll(AutoEatHandler.DEFAULT_FOOD_KEYWORDS);
            }
            AutoEatHandler.foodKeywords = parsed;
        }
    }
}
