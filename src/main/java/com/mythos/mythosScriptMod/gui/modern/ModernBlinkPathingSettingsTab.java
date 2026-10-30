package com.mythos.mythosScriptMod.gui.modern;

import com.mythos.mythosScriptMod.config.BlinkPathingConfig;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.Settings;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.SettingsUtil;

/** Modern settings page for Baritone blink (teleport) pathing. */
public final class ModernBlinkPathingSettingsTab {

    private ModernBlinkPathingSettingsTab() {
    }

    public static ModernSettingsTab create() {
        return ModernFormSettingsTab.builder("gui.modern.blink_pathing.title", "gui.modern.blink_pathing.subtitle",
                "gui.modern.blink_pathing.header_tip", adapter())
                .headerToggle(bool("allowBlinkPathing"), "gui.modern.blink_pathing.header_toggle_tip")
                .section("gui.modern.blink_pathing.section.step", "gui.modern.blink_pathing.section.step.tip")
                .decimal("gui.modern.blink_pathing.step_distance", "gui.modern.blink_pathing.step_distance.tip",
                        decimal("stepDistance"), 0.5F, 64.0F)
                .decimal("gui.modern.blink_pathing.max_horizontal", "gui.modern.blink_pathing.max_horizontal.tip",
                        decimal("maxHorizontalStep"), 0.5F, 128.0F)
                .decimal("gui.modern.blink_pathing.max_vertical", "gui.modern.blink_pathing.max_vertical.tip",
                        decimal("maxVerticalStep"), 0.5F, 128.0F)
                .integer("gui.modern.blink_pathing.tick_interval", "gui.modern.blink_pathing.tick_interval.tip",
                        integer("tickInterval"), 1, 20)
                .integer("gui.modern.blink_pathing.route_height_range", "gui.modern.blink_pathing.route_height_range.tip",
                        integer("routeHeightRange"), 1, 100)
                .decimal("gui.modern.blink_pathing.collision_margin", "gui.modern.blink_pathing.collision_margin.tip",
                        decimal("collisionMargin"), 0.0F, 10.0F)
                .toggle("gui.modern.blink_pathing.debug_log", "gui.modern.blink_pathing.debug_log.tip",
                        bool("blinkDebugLog"))
                .build().compactHeader();
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                BlinkPathingConfig.load();
            }

            @Override
            public State capture() {
                return new State();
            }

            @Override
            public State copy(State state) {
                return state == null ? null : new State(state);
            }

            @Override
            public void restore(State state) {
                if (state != null) {
                    state.apply();
                }
            }

            @Override
            public void save() {
                Settings settings = settings();
                BlinkPathingConfig.normalize();
                BlinkPathingConfig.save();
                SettingsUtil.save(settings);
            }

            @Override
            public boolean supportsDefaults() {
                return true;
            }

            @Override
            public State createDefaults() {
                return State.defaults();
            }
        };
    }

    private static ModernFormSettingsTab.BooleanValue bool(final String key) {
        return new ModernFormSettingsTab.BooleanValue() {
            @Override
            public boolean get() {
                return State.getBoolean(key);
            }

            @Override
            public void set(boolean value) {
                State.setBoolean(key, value);
            }
        };
    }

    private static ModernFormSettingsTab.IntValue integer(final String key) {
        return new ModernFormSettingsTab.IntValue() {
            @Override
            public int get() {
                return State.getInt(key);
            }

            @Override
            public void set(int value) {
                State.setInt(key, value);
            }
        };
    }

    private static ModernFormSettingsTab.FloatValue decimal(final String key) {
        return new ModernFormSettingsTab.FloatValue() {
            @Override
            public float get() {
                return State.getFloat(key);
            }

            @Override
            public void set(float value) {
                State.setFloat(key, value);
            }
        };
    }

    private static Settings settings() {
        Settings settings = BaritoneAPI.getSettings();
        if (settings == null) {
            throw new IllegalStateException("Baritone settings are unavailable");
        }
        return settings;
    }

    private static final class State {
        private final boolean allowBlinkPathing;
        private final boolean blinkDebugLog;
        private final double stepDistance;
        private final double maxHorizontalStep;
        private final double maxVerticalStep;
        private final int tickInterval;
        private final int routeHeightRange;
        private final double collisionMargin;

        private State() {
            allowBlinkPathing = settings().allowBlinkPathing.value;
            blinkDebugLog = settings().blinkDebugLog.value;
            stepDistance = BlinkPathingConfig.cfg().stepDistance;
            maxHorizontalStep = BlinkPathingConfig.cfg().maxHorizontalStep;
            maxVerticalStep = BlinkPathingConfig.cfg().maxVerticalStep;
            tickInterval = BlinkPathingConfig.cfg().tickInterval;
            routeHeightRange = BlinkPathingConfig.cfg().routeHeightRange;
            collisionMargin = BlinkPathingConfig.cfg().collisionMargin;
        }

        private State(State source) {
            allowBlinkPathing = source.allowBlinkPathing;
            blinkDebugLog = source.blinkDebugLog;
            stepDistance = source.stepDistance;
            maxHorizontalStep = source.maxHorizontalStep;
            maxVerticalStep = source.maxVerticalStep;
            tickInterval = source.tickInterval;
            routeHeightRange = source.routeHeightRange;
            collisionMargin = source.collisionMargin;
        }

        private State(boolean allowBlinkPathing, boolean blinkDebugLog, double stepDistance,
                double maxHorizontalStep, double maxVerticalStep, int tickInterval,
                int routeHeightRange, double collisionMargin) {
            this.allowBlinkPathing = allowBlinkPathing;
            this.blinkDebugLog = blinkDebugLog;
            this.stepDistance = stepDistance;
            this.maxHorizontalStep = maxHorizontalStep;
            this.maxVerticalStep = maxVerticalStep;
            this.tickInterval = tickInterval;
            this.routeHeightRange = routeHeightRange;
            this.collisionMargin = collisionMargin;
        }

        private static State defaults() {
            return new State(settings().allowBlinkPathing.defaultValue, false, 5.0D, 8.0D, 6.0D, 1, 10, 2.0D);
        }

        private void apply() {
            Settings settings = settings();
            settings.allowBlinkPathing.value = allowBlinkPathing;
            settings.blinkDebugLog.value = blinkDebugLog;
            if (allowBlinkPathing) {
                settings.allowFlightPathing.value = false;
            }
            BlinkPathingConfig.cfg().stepDistance = stepDistance;
            BlinkPathingConfig.cfg().maxHorizontalStep = maxHorizontalStep;
            BlinkPathingConfig.cfg().maxVerticalStep = maxVerticalStep;
            BlinkPathingConfig.cfg().tickInterval = tickInterval;
            BlinkPathingConfig.cfg().routeHeightRange = routeHeightRange;
            BlinkPathingConfig.cfg().collisionMargin = collisionMargin;
            BlinkPathingConfig.normalize();
        }

        private static boolean getBoolean(String key) {
            if ("allowBlinkPathing".equals(key)) return settings().allowBlinkPathing.value;
            if ("blinkDebugLog".equals(key)) return settings().blinkDebugLog.value;
            return false;
        }

        private static void setBoolean(String key, boolean value) {
            Settings settings = settings();
            if ("allowBlinkPathing".equals(key)) {
                settings.allowBlinkPathing.value = value;
                if (value) {
                    settings.allowFlightPathing.value = false;
                }
            } else if ("blinkDebugLog".equals(key)) {
                settings.blinkDebugLog.value = value;
            }
        }

        private static int getInt(String key) {
            if ("tickInterval".equals(key)) return BlinkPathingConfig.cfg().tickInterval;
            if ("routeHeightRange".equals(key)) return BlinkPathingConfig.cfg().routeHeightRange;
            return 0;
        }

        private static void setInt(String key, int value) {
            if ("tickInterval".equals(key)) BlinkPathingConfig.cfg().tickInterval = value;
            if ("routeHeightRange".equals(key)) BlinkPathingConfig.cfg().routeHeightRange = value;
            BlinkPathingConfig.normalize();
        }

        private static float getFloat(String key) {
            if ("stepDistance".equals(key)) return (float) BlinkPathingConfig.cfg().stepDistance;
            if ("maxHorizontalStep".equals(key)) return (float) BlinkPathingConfig.cfg().maxHorizontalStep;
            if ("maxVerticalStep".equals(key)) return (float) BlinkPathingConfig.cfg().maxVerticalStep;
            if ("collisionMargin".equals(key)) return (float) BlinkPathingConfig.cfg().collisionMargin;
            return 0.0F;
        }

        private static void setFloat(String key, float value) {
            if ("stepDistance".equals(key)) BlinkPathingConfig.cfg().stepDistance = value;
            else if ("maxHorizontalStep".equals(key)) BlinkPathingConfig.cfg().maxHorizontalStep = value;
            else if ("maxVerticalStep".equals(key)) BlinkPathingConfig.cfg().maxVerticalStep = value;
            else if ("collisionMargin".equals(key)) BlinkPathingConfig.cfg().collisionMargin = value;
            BlinkPathingConfig.normalize();
        }
    }
}
