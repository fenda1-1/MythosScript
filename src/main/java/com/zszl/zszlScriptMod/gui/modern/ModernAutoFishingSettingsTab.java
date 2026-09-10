package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.handlers.AutoFishingHandler;

/** Modern embedded settings page for the complete automatic fishing feature. */
public final class ModernAutoFishingSettingsTab {

    private ModernAutoFishingSettingsTab() {
    }

    public static ModernSettingsTab create() {
        return ModernFormSettingsTab.builder("gui.modern.autofishing.title", "gui.modern.autofishing.subtitle",
                "gui.modern.autofishing.header_tip", adapter())
                .headerToggle(bool("enabled"), "gui.modern.autofishing.header_toggle_tip")
                .section("gui.modern.autofishing.section.basic", "gui.modern.autofishing.section.basic.tip")
                .toggle("gui.modern.autofishing.require_rod", "gui.modern.autofishing.require_rod.tip", bool("requireFishingRod"))
                .toggle("gui.modern.autofishing.auto_switch", "gui.modern.autofishing.auto_switch.tip", bool("autoSwitchToRod"))
                .integer("gui.modern.autofishing.preferred_slot", "gui.modern.autofishing.preferred_slot.tip", integer("preferredRodSlot"), 0, 9)
                .toggle("gui.modern.autofishing.pause_gui", "gui.modern.autofishing.pause_gui.tip", bool("disableWhenGuiOpen"))
                .toggle("gui.modern.autofishing.allow_moving", "gui.modern.autofishing.allow_moving.tip", bool("allowWhilePlayerMoving"))
                .toggle("gui.modern.autofishing.status_message", "gui.modern.autofishing.status_message.tip", bool("sendStatusMessage"))
                .section("gui.modern.autofishing.section.cast", "gui.modern.autofishing.section.cast.tip")
                .toggle("gui.modern.autofishing.auto_cast", "gui.modern.autofishing.auto_cast.tip", bool("enableAutoCastOnStart"))
                .integer("gui.modern.autofishing.initial_delay", "gui.modern.autofishing.initial_delay.tip", integer("initialCastDelayTicks"), 0, 100)
                .toggle("gui.modern.autofishing.auto_recast", "gui.modern.autofishing.auto_recast.tip", bool("autoRecastAfterCatch"))
                .integer("gui.modern.autofishing.recast_min", "gui.modern.autofishing.recast_min.tip", integer("recastDelayMinTicks"), 0, 100)
                .enabledWhen(predicate("autoRecastAfterCatch"))
                .integer("gui.modern.autofishing.recast_max", "gui.modern.autofishing.recast_max.tip", integer("recastDelayMaxTicks"), 0, 100)
                .enabledWhen(predicate("autoRecastAfterCatch"))
                .toggle("gui.modern.autofishing.retry_missing", "gui.modern.autofishing.retry_missing.tip", bool("retryCastWhenBobberMissing"))
                .integer("gui.modern.autofishing.retry_delay", "gui.modern.autofishing.retry_delay.tip", integer("retryCastDelayTicks"), 5, 100)
                .enabledWhen(predicate("retryCastWhenBobberMissing"))
                .toggle("gui.modern.autofishing.timeout_recast", "gui.modern.autofishing.timeout_recast.tip", bool("timeoutRecastEnabled"))
                .integer("gui.modern.autofishing.max_wait", "gui.modern.autofishing.max_wait.tip", integer("maxFishingWaitTicks"), 40, 2400)
                .enabledWhen(predicate("timeoutRecastEnabled"))
                .section("gui.modern.autofishing.section.bite", "gui.modern.autofishing.section.bite.tip")
                .choice("gui.modern.autofishing.bite_mode", "gui.modern.autofishing.bite_mode.tip", choice("biteDetectMode"),
                        ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(AutoFishingHandler.BITE_MODE_SMART, "gui.modern.autofishing.bite.smart"),
                                ModernFormSettingsTab.option(AutoFishingHandler.BITE_MODE_MOTION_ONLY, "gui.modern.autofishing.bite.motion"),
                                ModernFormSettingsTab.option(AutoFishingHandler.BITE_MODE_STRICT, "gui.modern.autofishing.bite.strict")))
                .integer("gui.modern.autofishing.ignore_settle", "gui.modern.autofishing.ignore_settle.tip", integer("ignoreInitialBobberSettleTicks"), 0, 40)
                .integer("gui.modern.autofishing.reel_delay", "gui.modern.autofishing.reel_delay.tip", integer("reelDelayTicks"), 0, 20)
                .decimal("gui.modern.autofishing.drop_threshold", "gui.modern.autofishing.drop_threshold.tip", decimal("minVerticalDropThreshold"), 0.01F, 1.0F)
                .decimal("gui.modern.autofishing.move_threshold", "gui.modern.autofishing.move_threshold.tip", decimal("minHorizontalMoveThreshold"), 0.0F, 1.0F)
                .integer("gui.modern.autofishing.confirm_ticks", "gui.modern.autofishing.confirm_ticks.tip", integer("confirmBiteTicks"), 1, 5)
                .toggle("gui.modern.autofishing.debug", "gui.modern.autofishing.debug.tip", bool("debugBiteInfo"))
                .section("gui.modern.autofishing.section.recover", "gui.modern.autofishing.section.recover.tip")
                .integer("gui.modern.autofishing.post_reel", "gui.modern.autofishing.post_reel.tip", integer("postReelPauseTicks"), 0, 40)
                .integer("gui.modern.autofishing.prevent_double", "gui.modern.autofishing.prevent_double.tip", integer("preventDoubleReelTicks"), 0, 20)
                .toggle("gui.modern.autofishing.recast_on_loot", "gui.modern.autofishing.recast_on_loot.tip", bool("recastOnlyIfLootSuccess"))
                .toggle("gui.modern.autofishing.reset_gone", "gui.modern.autofishing.reset_gone.tip", bool("resetStateWhenHookGone"))
                .toggle("gui.modern.autofishing.auto_recover", "gui.modern.autofishing.auto_recover.tip", bool("autoRecoverFromInterruptedCast"))
                .section("gui.modern.autofishing.section.safety", "gui.modern.autofishing.section.safety.tip")
                .toggle("gui.modern.autofishing.stop_low_dura", "gui.modern.autofishing.stop_low_dura.tip", bool("stopWhenRodDurabilityLow"))
                .integer("gui.modern.autofishing.min_dura", "gui.modern.autofishing.min_dura.tip", integer("minRodDurability"), 1, 64)
                .enabledWhen(predicate("stopWhenRodDurabilityLow"))
                .toggle("gui.modern.autofishing.stop_no_rod", "gui.modern.autofishing.stop_no_rod.tip", bool("stopWhenNoRodFound"))
                .toggle("gui.modern.autofishing.pause_entity", "gui.modern.autofishing.pause_entity.tip", bool("pauseWhenHookedEntity"))
                .toggle("gui.modern.autofishing.stop_world", "gui.modern.autofishing.stop_world.tip", bool("stopOnWorldChange"))
                .build().compactHeader();
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                AutoFishingHandler.loadConfig();
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
                AutoFishingHandler.saveConfig();
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
                return State.getInt(name);
            }

            @Override
            public void set(int value) {
                State.setInt(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.FloatValue decimal(final String name) {
        return new ModernFormSettingsTab.FloatValue() {
            @Override
            public float get() {
                return State.getFloat(name);
            }

            @Override
            public void set(float value) {
                State.setFloat(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.ChoiceValue<String> choice(final String name) {
        return new ModernFormSettingsTab.ChoiceValue<String>() {
            @Override
            public String get() {
                return State.getString(name);
            }

            @Override
            public void set(String value) {
                State.setString(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.Condition predicate(final String name) {
        return new ModernFormSettingsTab.Condition() {
            @Override
            public boolean matches() {
                return State.getBoolean(name);
            }
        };
    }

    /** Defensive snapshot of every persisted automatic fishing value. */
    private static final class State {
        private final boolean enabled = AutoFishingHandler.enabled;
        private final boolean requireFishingRod = AutoFishingHandler.requireFishingRod;
        private final boolean autoSwitchToRod = AutoFishingHandler.autoSwitchToRod;
        private final int preferredRodSlot = AutoFishingHandler.preferredRodSlot;
        private final boolean disableWhenGuiOpen = AutoFishingHandler.disableWhenGuiOpen;
        private final boolean allowWhilePlayerMoving = AutoFishingHandler.allowWhilePlayerMoving;
        private final boolean sendStatusMessage = AutoFishingHandler.sendStatusMessage;
        private final boolean enableAutoCastOnStart = AutoFishingHandler.enableAutoCastOnStart;
        private final int initialCastDelayTicks = AutoFishingHandler.initialCastDelayTicks;
        private final boolean autoRecastAfterCatch = AutoFishingHandler.autoRecastAfterCatch;
        private final int recastDelayMinTicks = AutoFishingHandler.recastDelayMinTicks;
        private final int recastDelayMaxTicks = AutoFishingHandler.recastDelayMaxTicks;
        private final boolean retryCastWhenBobberMissing = AutoFishingHandler.retryCastWhenBobberMissing;
        private final int retryCastDelayTicks = AutoFishingHandler.retryCastDelayTicks;
        private final boolean timeoutRecastEnabled = AutoFishingHandler.timeoutRecastEnabled;
        private final int maxFishingWaitTicks = AutoFishingHandler.maxFishingWaitTicks;
        private final String biteDetectMode = AutoFishingHandler.biteDetectMode;
        private final int ignoreInitialBobberSettleTicks = AutoFishingHandler.ignoreInitialBobberSettleTicks;
        private final int reelDelayTicks = AutoFishingHandler.reelDelayTicks;
        private final float minVerticalDropThreshold = AutoFishingHandler.minVerticalDropThreshold;
        private final float minHorizontalMoveThreshold = AutoFishingHandler.minHorizontalMoveThreshold;
        private final int confirmBiteTicks = AutoFishingHandler.confirmBiteTicks;
        private final boolean debugBiteInfo = AutoFishingHandler.debugBiteInfo;
        private final int postReelPauseTicks = AutoFishingHandler.postReelPauseTicks;
        private final int preventDoubleReelTicks = AutoFishingHandler.preventDoubleReelTicks;
        private final boolean recastOnlyIfLootSuccess = AutoFishingHandler.recastOnlyIfLootSuccess;
        private final boolean resetStateWhenHookGone = AutoFishingHandler.resetStateWhenHookGone;
        private final boolean autoRecoverFromInterruptedCast = AutoFishingHandler.autoRecoverFromInterruptedCast;
        private final boolean stopWhenRodDurabilityLow = AutoFishingHandler.stopWhenRodDurabilityLow;
        private final int minRodDurability = AutoFishingHandler.minRodDurability;
        private final boolean stopWhenNoRodFound = AutoFishingHandler.stopWhenNoRodFound;
        private final boolean pauseWhenHookedEntity = AutoFishingHandler.pauseWhenHookedEntity;
        private final boolean stopOnWorldChange = AutoFishingHandler.stopOnWorldChange;

        private void apply() {
            AutoFishingHandler.requireFishingRod = requireFishingRod;
            AutoFishingHandler.autoSwitchToRod = autoSwitchToRod;
            AutoFishingHandler.preferredRodSlot = preferredRodSlot;
            AutoFishingHandler.disableWhenGuiOpen = disableWhenGuiOpen;
            AutoFishingHandler.allowWhilePlayerMoving = allowWhilePlayerMoving;
            AutoFishingHandler.sendStatusMessage = sendStatusMessage;
            AutoFishingHandler.enableAutoCastOnStart = enableAutoCastOnStart;
            AutoFishingHandler.initialCastDelayTicks = initialCastDelayTicks;
            AutoFishingHandler.autoRecastAfterCatch = autoRecastAfterCatch;
            AutoFishingHandler.recastDelayMinTicks = recastDelayMinTicks;
            AutoFishingHandler.recastDelayMaxTicks = recastDelayMaxTicks;
            AutoFishingHandler.retryCastWhenBobberMissing = retryCastWhenBobberMissing;
            AutoFishingHandler.retryCastDelayTicks = retryCastDelayTicks;
            AutoFishingHandler.timeoutRecastEnabled = timeoutRecastEnabled;
            AutoFishingHandler.maxFishingWaitTicks = maxFishingWaitTicks;
            AutoFishingHandler.biteDetectMode = biteDetectMode;
            AutoFishingHandler.ignoreInitialBobberSettleTicks = ignoreInitialBobberSettleTicks;
            AutoFishingHandler.reelDelayTicks = reelDelayTicks;
            AutoFishingHandler.minVerticalDropThreshold = minVerticalDropThreshold;
            AutoFishingHandler.minHorizontalMoveThreshold = minHorizontalMoveThreshold;
            AutoFishingHandler.confirmBiteTicks = confirmBiteTicks;
            AutoFishingHandler.debugBiteInfo = debugBiteInfo;
            AutoFishingHandler.postReelPauseTicks = postReelPauseTicks;
            AutoFishingHandler.preventDoubleReelTicks = preventDoubleReelTicks;
            AutoFishingHandler.recastOnlyIfLootSuccess = recastOnlyIfLootSuccess;
            AutoFishingHandler.resetStateWhenHookGone = resetStateWhenHookGone;
            AutoFishingHandler.autoRecoverFromInterruptedCast = autoRecoverFromInterruptedCast;
            AutoFishingHandler.stopWhenRodDurabilityLow = stopWhenRodDurabilityLow;
            AutoFishingHandler.minRodDurability = minRodDurability;
            AutoFishingHandler.stopWhenNoRodFound = stopWhenNoRodFound;
            AutoFishingHandler.pauseWhenHookedEntity = pauseWhenHookedEntity;
            AutoFishingHandler.stopOnWorldChange = stopOnWorldChange;
            // Apply the enabled flag last so runtime scheduling uses the
            // restored delay and auto-cast values.
            AutoFishingHandler.INSTANCE.setEnabledForSettingsPreview(enabled);
        }

        private static void applyDefaults() {
            AutoFishingHandler.requireFishingRod = true;
            AutoFishingHandler.autoSwitchToRod = false;
            AutoFishingHandler.preferredRodSlot = 0;
            AutoFishingHandler.disableWhenGuiOpen = true;
            AutoFishingHandler.allowWhilePlayerMoving = false;
            AutoFishingHandler.sendStatusMessage = true;
            AutoFishingHandler.enableAutoCastOnStart = true;
            AutoFishingHandler.initialCastDelayTicks = 8;
            AutoFishingHandler.autoRecastAfterCatch = true;
            AutoFishingHandler.recastDelayMinTicks = 10;
            AutoFishingHandler.recastDelayMaxTicks = 16;
            AutoFishingHandler.retryCastWhenBobberMissing = true;
            AutoFishingHandler.retryCastDelayTicks = 20;
            AutoFishingHandler.timeoutRecastEnabled = true;
            AutoFishingHandler.maxFishingWaitTicks = 600;
            AutoFishingHandler.biteDetectMode = AutoFishingHandler.BITE_MODE_SMART;
            AutoFishingHandler.ignoreInitialBobberSettleTicks = 8;
            AutoFishingHandler.reelDelayTicks = 2;
            AutoFishingHandler.minVerticalDropThreshold = 0.08F;
            AutoFishingHandler.minHorizontalMoveThreshold = 0.03F;
            AutoFishingHandler.confirmBiteTicks = 1;
            AutoFishingHandler.debugBiteInfo = false;
            AutoFishingHandler.postReelPauseTicks = 6;
            AutoFishingHandler.preventDoubleReelTicks = 6;
            AutoFishingHandler.recastOnlyIfLootSuccess = false;
            AutoFishingHandler.resetStateWhenHookGone = true;
            AutoFishingHandler.autoRecoverFromInterruptedCast = true;
            AutoFishingHandler.stopWhenRodDurabilityLow = true;
            AutoFishingHandler.minRodDurability = 5;
            AutoFishingHandler.stopWhenNoRodFound = true;
            AutoFishingHandler.pauseWhenHookedEntity = true;
            AutoFishingHandler.stopOnWorldChange = true;
        }

        private static boolean getBoolean(String name) {
            switch (name) {
                case "enabled": return AutoFishingHandler.enabled;
                case "requireFishingRod": return AutoFishingHandler.requireFishingRod;
                case "autoSwitchToRod": return AutoFishingHandler.autoSwitchToRod;
                case "disableWhenGuiOpen": return AutoFishingHandler.disableWhenGuiOpen;
                case "allowWhilePlayerMoving": return AutoFishingHandler.allowWhilePlayerMoving;
                case "sendStatusMessage": return AutoFishingHandler.sendStatusMessage;
                case "enableAutoCastOnStart": return AutoFishingHandler.enableAutoCastOnStart;
                case "autoRecastAfterCatch": return AutoFishingHandler.autoRecastAfterCatch;
                case "retryCastWhenBobberMissing": return AutoFishingHandler.retryCastWhenBobberMissing;
                case "timeoutRecastEnabled": return AutoFishingHandler.timeoutRecastEnabled;
                case "debugBiteInfo": return AutoFishingHandler.debugBiteInfo;
                case "recastOnlyIfLootSuccess": return AutoFishingHandler.recastOnlyIfLootSuccess;
                case "resetStateWhenHookGone": return AutoFishingHandler.resetStateWhenHookGone;
                case "autoRecoverFromInterruptedCast": return AutoFishingHandler.autoRecoverFromInterruptedCast;
                case "stopWhenRodDurabilityLow": return AutoFishingHandler.stopWhenRodDurabilityLow;
                case "stopWhenNoRodFound": return AutoFishingHandler.stopWhenNoRodFound;
                case "pauseWhenHookedEntity": return AutoFishingHandler.pauseWhenHookedEntity;
                case "stopOnWorldChange": return AutoFishingHandler.stopOnWorldChange;
                default: return false;
            }
        }

        private static void setBoolean(String name, boolean value) {
            switch (name) {
                case "enabled": AutoFishingHandler.INSTANCE.setEnabledForSettingsPreview(value); break;
                case "requireFishingRod": AutoFishingHandler.requireFishingRod = value; break;
                case "autoSwitchToRod": AutoFishingHandler.autoSwitchToRod = value; break;
                case "disableWhenGuiOpen": AutoFishingHandler.disableWhenGuiOpen = value; break;
                case "allowWhilePlayerMoving": AutoFishingHandler.allowWhilePlayerMoving = value; break;
                case "sendStatusMessage": AutoFishingHandler.sendStatusMessage = value; break;
                case "enableAutoCastOnStart": AutoFishingHandler.enableAutoCastOnStart = value; break;
                case "autoRecastAfterCatch": AutoFishingHandler.autoRecastAfterCatch = value; break;
                case "retryCastWhenBobberMissing": AutoFishingHandler.retryCastWhenBobberMissing = value; break;
                case "timeoutRecastEnabled": AutoFishingHandler.timeoutRecastEnabled = value; break;
                case "debugBiteInfo": AutoFishingHandler.debugBiteInfo = value; break;
                case "recastOnlyIfLootSuccess": AutoFishingHandler.recastOnlyIfLootSuccess = value; break;
                case "resetStateWhenHookGone": AutoFishingHandler.resetStateWhenHookGone = value; break;
                case "autoRecoverFromInterruptedCast": AutoFishingHandler.autoRecoverFromInterruptedCast = value; break;
                case "stopWhenRodDurabilityLow": AutoFishingHandler.stopWhenRodDurabilityLow = value; break;
                case "stopWhenNoRodFound": AutoFishingHandler.stopWhenNoRodFound = value; break;
                case "pauseWhenHookedEntity": AutoFishingHandler.pauseWhenHookedEntity = value; break;
                case "stopOnWorldChange": AutoFishingHandler.stopOnWorldChange = value; break;
                default: break;
            }
        }

        private static int getInt(String name) {
            switch (name) {
                case "preferredRodSlot": return AutoFishingHandler.preferredRodSlot;
                case "initialCastDelayTicks": return AutoFishingHandler.initialCastDelayTicks;
                case "recastDelayMinTicks": return AutoFishingHandler.recastDelayMinTicks;
                case "recastDelayMaxTicks": return AutoFishingHandler.recastDelayMaxTicks;
                case "retryCastDelayTicks": return AutoFishingHandler.retryCastDelayTicks;
                case "maxFishingWaitTicks": return AutoFishingHandler.maxFishingWaitTicks;
                case "ignoreInitialBobberSettleTicks": return AutoFishingHandler.ignoreInitialBobberSettleTicks;
                case "reelDelayTicks": return AutoFishingHandler.reelDelayTicks;
                case "confirmBiteTicks": return AutoFishingHandler.confirmBiteTicks;
                case "postReelPauseTicks": return AutoFishingHandler.postReelPauseTicks;
                case "preventDoubleReelTicks": return AutoFishingHandler.preventDoubleReelTicks;
                case "minRodDurability": return AutoFishingHandler.minRodDurability;
                default: return 0;
            }
        }

        private static void setInt(String name, int value) {
            switch (name) {
                case "preferredRodSlot": AutoFishingHandler.preferredRodSlot = value; break;
                case "initialCastDelayTicks": AutoFishingHandler.initialCastDelayTicks = value; break;
                case "recastDelayMinTicks": AutoFishingHandler.recastDelayMinTicks = value; break;
                case "recastDelayMaxTicks": AutoFishingHandler.recastDelayMaxTicks = value; break;
                case "retryCastDelayTicks": AutoFishingHandler.retryCastDelayTicks = value; break;
                case "maxFishingWaitTicks": AutoFishingHandler.maxFishingWaitTicks = value; break;
                case "ignoreInitialBobberSettleTicks": AutoFishingHandler.ignoreInitialBobberSettleTicks = value; break;
                case "reelDelayTicks": AutoFishingHandler.reelDelayTicks = value; break;
                case "confirmBiteTicks": AutoFishingHandler.confirmBiteTicks = value; break;
                case "postReelPauseTicks": AutoFishingHandler.postReelPauseTicks = value; break;
                case "preventDoubleReelTicks": AutoFishingHandler.preventDoubleReelTicks = value; break;
                case "minRodDurability": AutoFishingHandler.minRodDurability = value; break;
                default: break;
            }
        }

        private static float getFloat(String name) {
            if ("minVerticalDropThreshold".equals(name)) return AutoFishingHandler.minVerticalDropThreshold;
            if ("minHorizontalMoveThreshold".equals(name)) return AutoFishingHandler.minHorizontalMoveThreshold;
            return 0.0F;
        }

        private static void setFloat(String name, float value) {
            if ("minVerticalDropThreshold".equals(name)) AutoFishingHandler.minVerticalDropThreshold = value;
            if ("minHorizontalMoveThreshold".equals(name)) AutoFishingHandler.minHorizontalMoveThreshold = value;
        }

        private static String getString(String name) {
            return "biteDetectMode".equals(name) ? AutoFishingHandler.biteDetectMode : "";
        }

        private static void setString(String name, String value) {
            if ("biteDetectMode".equals(name)) AutoFishingHandler.biteDetectMode = value;
        }
    }
}
