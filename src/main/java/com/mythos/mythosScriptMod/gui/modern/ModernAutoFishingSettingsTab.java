package com.mythos.mythosScriptMod.gui.modern;

import com.mythos.mythosScriptMod.handlers.AutoFishingHandler;

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
                                ModernFormSettingsTab.option(AutoFishingHandler.BITE_MODE_SERVER_SIGNAL, "gui.modern.autofishing.bite.server")))
                .integer("gui.modern.autofishing.reel_delay", "gui.modern.autofishing.reel_delay.tip", integer("reelDelayTicks"), 0, 20)
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
        private final boolean enabled = AutoFishingHandler.enabled();
        private final boolean requireFishingRod = AutoFishingHandler.cfg().requireFishingRod;
        private final boolean autoSwitchToRod = AutoFishingHandler.cfg().autoSwitchToRod;
        private final int preferredRodSlot = AutoFishingHandler.cfg().preferredRodSlot;
        private final boolean disableWhenGuiOpen = AutoFishingHandler.cfg().disableWhenGuiOpen;
        private final boolean allowWhilePlayerMoving = AutoFishingHandler.cfg().allowWhilePlayerMoving;
        private final boolean sendStatusMessage = AutoFishingHandler.cfg().sendStatusMessage;
        private final boolean enableAutoCastOnStart = AutoFishingHandler.cfg().enableAutoCastOnStart;
        private final int initialCastDelayTicks = AutoFishingHandler.cfg().initialCastDelayTicks;
        private final boolean autoRecastAfterCatch = AutoFishingHandler.cfg().autoRecastAfterCatch;
        private final int recastDelayMinTicks = AutoFishingHandler.cfg().recastDelayMinTicks;
        private final int recastDelayMaxTicks = AutoFishingHandler.cfg().recastDelayMaxTicks;
        private final boolean retryCastWhenBobberMissing = AutoFishingHandler.cfg().retryCastWhenBobberMissing;
        private final int retryCastDelayTicks = AutoFishingHandler.cfg().retryCastDelayTicks;
        private final boolean timeoutRecastEnabled = AutoFishingHandler.cfg().timeoutRecastEnabled;
        private final int maxFishingWaitTicks = AutoFishingHandler.cfg().maxFishingWaitTicks;
        private final String biteDetectMode = AutoFishingHandler.cfg().biteDetectMode;
        private final int ignoreInitialBobberSettleTicks = AutoFishingHandler.cfg().ignoreInitialBobberSettleTicks;
        private final int reelDelayTicks = AutoFishingHandler.cfg().reelDelayTicks;
        private final float minVerticalDropThreshold = AutoFishingHandler.cfg().minVerticalDropThreshold;
        private final float minHorizontalMoveThreshold = AutoFishingHandler.cfg().minHorizontalMoveThreshold;
        private final int confirmBiteTicks = AutoFishingHandler.cfg().confirmBiteTicks;
        private final boolean debugBiteInfo = AutoFishingHandler.cfg().debugBiteInfo;
        private final int postReelPauseTicks = AutoFishingHandler.cfg().postReelPauseTicks;
        private final int preventDoubleReelTicks = AutoFishingHandler.cfg().preventDoubleReelTicks;
        private final boolean recastOnlyIfLootSuccess = AutoFishingHandler.cfg().recastOnlyIfLootSuccess;
        private final boolean resetStateWhenHookGone = AutoFishingHandler.cfg().resetStateWhenHookGone;
        private final boolean autoRecoverFromInterruptedCast = AutoFishingHandler.cfg().autoRecoverFromInterruptedCast;
        private final boolean stopWhenRodDurabilityLow = AutoFishingHandler.cfg().stopWhenRodDurabilityLow;
        private final int minRodDurability = AutoFishingHandler.cfg().minRodDurability;
        private final boolean stopWhenNoRodFound = AutoFishingHandler.cfg().stopWhenNoRodFound;
        private final boolean pauseWhenHookedEntity = AutoFishingHandler.cfg().pauseWhenHookedEntity;
        private final boolean stopOnWorldChange = AutoFishingHandler.cfg().stopOnWorldChange;

        private void apply() {
            AutoFishingHandler.cfg().requireFishingRod = requireFishingRod;
            AutoFishingHandler.cfg().autoSwitchToRod = autoSwitchToRod;
            AutoFishingHandler.cfg().preferredRodSlot = preferredRodSlot;
            AutoFishingHandler.cfg().disableWhenGuiOpen = disableWhenGuiOpen;
            AutoFishingHandler.cfg().allowWhilePlayerMoving = allowWhilePlayerMoving;
            AutoFishingHandler.cfg().sendStatusMessage = sendStatusMessage;
            AutoFishingHandler.cfg().enableAutoCastOnStart = enableAutoCastOnStart;
            AutoFishingHandler.cfg().initialCastDelayTicks = initialCastDelayTicks;
            AutoFishingHandler.cfg().autoRecastAfterCatch = autoRecastAfterCatch;
            AutoFishingHandler.cfg().recastDelayMinTicks = recastDelayMinTicks;
            AutoFishingHandler.cfg().recastDelayMaxTicks = recastDelayMaxTicks;
            AutoFishingHandler.cfg().retryCastWhenBobberMissing = retryCastWhenBobberMissing;
            AutoFishingHandler.cfg().retryCastDelayTicks = retryCastDelayTicks;
            AutoFishingHandler.cfg().timeoutRecastEnabled = timeoutRecastEnabled;
            AutoFishingHandler.cfg().maxFishingWaitTicks = maxFishingWaitTicks;
            AutoFishingHandler.cfg().biteDetectMode = biteDetectMode;
            AutoFishingHandler.cfg().ignoreInitialBobberSettleTicks = ignoreInitialBobberSettleTicks;
            AutoFishingHandler.cfg().reelDelayTicks = reelDelayTicks;
            AutoFishingHandler.cfg().minVerticalDropThreshold = minVerticalDropThreshold;
            AutoFishingHandler.cfg().minHorizontalMoveThreshold = minHorizontalMoveThreshold;
            AutoFishingHandler.cfg().confirmBiteTicks = confirmBiteTicks;
            AutoFishingHandler.cfg().debugBiteInfo = debugBiteInfo;
            AutoFishingHandler.cfg().postReelPauseTicks = postReelPauseTicks;
            AutoFishingHandler.cfg().preventDoubleReelTicks = preventDoubleReelTicks;
            AutoFishingHandler.cfg().recastOnlyIfLootSuccess = recastOnlyIfLootSuccess;
            AutoFishingHandler.cfg().resetStateWhenHookGone = resetStateWhenHookGone;
            AutoFishingHandler.cfg().autoRecoverFromInterruptedCast = autoRecoverFromInterruptedCast;
            AutoFishingHandler.cfg().stopWhenRodDurabilityLow = stopWhenRodDurabilityLow;
            AutoFishingHandler.cfg().minRodDurability = minRodDurability;
            AutoFishingHandler.cfg().stopWhenNoRodFound = stopWhenNoRodFound;
            AutoFishingHandler.cfg().pauseWhenHookedEntity = pauseWhenHookedEntity;
            AutoFishingHandler.cfg().stopOnWorldChange = stopOnWorldChange;
            // Apply the enabled flag last so runtime scheduling uses the
            // restored delay and auto-cast values.
            AutoFishingHandler.INSTANCE.setEnabledForSettingsPreview(enabled);
        }

        private static void applyDefaults() {
            AutoFishingHandler.cfg().requireFishingRod = true;
            AutoFishingHandler.cfg().autoSwitchToRod = false;
            AutoFishingHandler.cfg().preferredRodSlot = 0;
            AutoFishingHandler.cfg().disableWhenGuiOpen = true;
            AutoFishingHandler.cfg().allowWhilePlayerMoving = false;
            AutoFishingHandler.cfg().sendStatusMessage = true;
            AutoFishingHandler.cfg().enableAutoCastOnStart = true;
            AutoFishingHandler.cfg().initialCastDelayTicks = 8;
            AutoFishingHandler.cfg().autoRecastAfterCatch = true;
            AutoFishingHandler.cfg().recastDelayMinTicks = 10;
            AutoFishingHandler.cfg().recastDelayMaxTicks = 16;
            AutoFishingHandler.cfg().retryCastWhenBobberMissing = true;
            AutoFishingHandler.cfg().retryCastDelayTicks = 20;
            AutoFishingHandler.cfg().timeoutRecastEnabled = true;
            AutoFishingHandler.cfg().maxFishingWaitTicks = 1200;
            AutoFishingHandler.cfg().biteDetectMode = AutoFishingHandler.BITE_MODE_SERVER_SIGNAL;
            AutoFishingHandler.cfg().ignoreInitialBobberSettleTicks = 8;
            AutoFishingHandler.cfg().reelDelayTicks = 0;
            AutoFishingHandler.cfg().minVerticalDropThreshold = 0.04F;
            AutoFishingHandler.cfg().minHorizontalMoveThreshold = 0.10F;
            AutoFishingHandler.cfg().confirmBiteTicks = 1;
            AutoFishingHandler.cfg().debugBiteInfo = false;
            AutoFishingHandler.cfg().postReelPauseTicks = 6;
            AutoFishingHandler.cfg().preventDoubleReelTicks = 6;
            AutoFishingHandler.cfg().recastOnlyIfLootSuccess = false;
            AutoFishingHandler.cfg().resetStateWhenHookGone = true;
            AutoFishingHandler.cfg().autoRecoverFromInterruptedCast = true;
            AutoFishingHandler.cfg().stopWhenRodDurabilityLow = true;
            AutoFishingHandler.cfg().minRodDurability = 5;
            AutoFishingHandler.cfg().stopWhenNoRodFound = true;
            AutoFishingHandler.cfg().pauseWhenHookedEntity = true;
            AutoFishingHandler.cfg().stopOnWorldChange = true;
        }

        private static boolean getBoolean(String name) {
            switch (name) {
                case "enabled": return AutoFishingHandler.enabled();
                case "requireFishingRod": return AutoFishingHandler.cfg().requireFishingRod;
                case "autoSwitchToRod": return AutoFishingHandler.cfg().autoSwitchToRod;
                case "disableWhenGuiOpen": return AutoFishingHandler.cfg().disableWhenGuiOpen;
                case "allowWhilePlayerMoving": return AutoFishingHandler.cfg().allowWhilePlayerMoving;
                case "sendStatusMessage": return AutoFishingHandler.cfg().sendStatusMessage;
                case "enableAutoCastOnStart": return AutoFishingHandler.cfg().enableAutoCastOnStart;
                case "autoRecastAfterCatch": return AutoFishingHandler.cfg().autoRecastAfterCatch;
                case "retryCastWhenBobberMissing": return AutoFishingHandler.cfg().retryCastWhenBobberMissing;
                case "timeoutRecastEnabled": return AutoFishingHandler.cfg().timeoutRecastEnabled;
                case "debugBiteInfo": return AutoFishingHandler.cfg().debugBiteInfo;
                case "recastOnlyIfLootSuccess": return AutoFishingHandler.cfg().recastOnlyIfLootSuccess;
                case "resetStateWhenHookGone": return AutoFishingHandler.cfg().resetStateWhenHookGone;
                case "autoRecoverFromInterruptedCast": return AutoFishingHandler.cfg().autoRecoverFromInterruptedCast;
                case "stopWhenRodDurabilityLow": return AutoFishingHandler.cfg().stopWhenRodDurabilityLow;
                case "stopWhenNoRodFound": return AutoFishingHandler.cfg().stopWhenNoRodFound;
                case "pauseWhenHookedEntity": return AutoFishingHandler.cfg().pauseWhenHookedEntity;
                case "stopOnWorldChange": return AutoFishingHandler.cfg().stopOnWorldChange;
                default: return false;
            }
        }

        private static void setBoolean(String name, boolean value) {
            switch (name) {
                case "enabled": AutoFishingHandler.INSTANCE.setEnabledForSettingsPreview(value); break;
                case "requireFishingRod": AutoFishingHandler.cfg().requireFishingRod = value; break;
                case "autoSwitchToRod": AutoFishingHandler.cfg().autoSwitchToRod = value; break;
                case "disableWhenGuiOpen": AutoFishingHandler.cfg().disableWhenGuiOpen = value; break;
                case "allowWhilePlayerMoving": AutoFishingHandler.cfg().allowWhilePlayerMoving = value; break;
                case "sendStatusMessage": AutoFishingHandler.cfg().sendStatusMessage = value; break;
                case "enableAutoCastOnStart": AutoFishingHandler.cfg().enableAutoCastOnStart = value; break;
                case "autoRecastAfterCatch": AutoFishingHandler.cfg().autoRecastAfterCatch = value; break;
                case "retryCastWhenBobberMissing": AutoFishingHandler.cfg().retryCastWhenBobberMissing = value; break;
                case "timeoutRecastEnabled": AutoFishingHandler.cfg().timeoutRecastEnabled = value; break;
                case "debugBiteInfo": AutoFishingHandler.cfg().debugBiteInfo = value; break;
                case "recastOnlyIfLootSuccess": AutoFishingHandler.cfg().recastOnlyIfLootSuccess = value; break;
                case "resetStateWhenHookGone": AutoFishingHandler.cfg().resetStateWhenHookGone = value; break;
                case "autoRecoverFromInterruptedCast": AutoFishingHandler.cfg().autoRecoverFromInterruptedCast = value; break;
                case "stopWhenRodDurabilityLow": AutoFishingHandler.cfg().stopWhenRodDurabilityLow = value; break;
                case "stopWhenNoRodFound": AutoFishingHandler.cfg().stopWhenNoRodFound = value; break;
                case "pauseWhenHookedEntity": AutoFishingHandler.cfg().pauseWhenHookedEntity = value; break;
                case "stopOnWorldChange": AutoFishingHandler.cfg().stopOnWorldChange = value; break;
                default: break;
            }
        }

        private static int getInt(String name) {
            switch (name) {
                case "preferredRodSlot": return AutoFishingHandler.cfg().preferredRodSlot;
                case "initialCastDelayTicks": return AutoFishingHandler.cfg().initialCastDelayTicks;
                case "recastDelayMinTicks": return AutoFishingHandler.cfg().recastDelayMinTicks;
                case "recastDelayMaxTicks": return AutoFishingHandler.cfg().recastDelayMaxTicks;
                case "retryCastDelayTicks": return AutoFishingHandler.cfg().retryCastDelayTicks;
                case "maxFishingWaitTicks": return AutoFishingHandler.cfg().maxFishingWaitTicks;
                case "ignoreInitialBobberSettleTicks": return AutoFishingHandler.cfg().ignoreInitialBobberSettleTicks;
                case "reelDelayTicks": return AutoFishingHandler.cfg().reelDelayTicks;
                case "confirmBiteTicks": return AutoFishingHandler.cfg().confirmBiteTicks;
                case "postReelPauseTicks": return AutoFishingHandler.cfg().postReelPauseTicks;
                case "preventDoubleReelTicks": return AutoFishingHandler.cfg().preventDoubleReelTicks;
                case "minRodDurability": return AutoFishingHandler.cfg().minRodDurability;
                default: return 0;
            }
        }

        private static void setInt(String name, int value) {
            switch (name) {
                case "preferredRodSlot": AutoFishingHandler.cfg().preferredRodSlot = value; break;
                case "initialCastDelayTicks": AutoFishingHandler.cfg().initialCastDelayTicks = value; break;
                case "recastDelayMinTicks": AutoFishingHandler.cfg().recastDelayMinTicks = value; break;
                case "recastDelayMaxTicks": AutoFishingHandler.cfg().recastDelayMaxTicks = value; break;
                case "retryCastDelayTicks": AutoFishingHandler.cfg().retryCastDelayTicks = value; break;
                case "maxFishingWaitTicks": AutoFishingHandler.cfg().maxFishingWaitTicks = value; break;
                case "ignoreInitialBobberSettleTicks": AutoFishingHandler.cfg().ignoreInitialBobberSettleTicks = value; break;
                case "reelDelayTicks": AutoFishingHandler.cfg().reelDelayTicks = value; break;
                case "confirmBiteTicks": AutoFishingHandler.cfg().confirmBiteTicks = value; break;
                case "postReelPauseTicks": AutoFishingHandler.cfg().postReelPauseTicks = value; break;
                case "preventDoubleReelTicks": AutoFishingHandler.cfg().preventDoubleReelTicks = value; break;
                case "minRodDurability": AutoFishingHandler.cfg().minRodDurability = value; break;
                default: break;
            }
        }

        private static float getFloat(String name) {
            if ("minVerticalDropThreshold".equals(name)) return AutoFishingHandler.cfg().minVerticalDropThreshold;
            if ("minHorizontalMoveThreshold".equals(name)) return AutoFishingHandler.cfg().minHorizontalMoveThreshold;
            return 0.0F;
        }

        private static void setFloat(String name, float value) {
            if ("minVerticalDropThreshold".equals(name)) AutoFishingHandler.cfg().minVerticalDropThreshold = value;
            if ("minHorizontalMoveThreshold".equals(name)) AutoFishingHandler.cfg().minHorizontalMoveThreshold = value;
        }

        private static String getString(String name) {
            return "biteDetectMode".equals(name) ? AutoFishingHandler.cfg().biteDetectMode : "";
        }

        private static void setString(String name, String value) {
            if ("biteDetectMode".equals(name)) AutoFishingHandler.cfg().biteDetectMode = value;
        }
    }
}
