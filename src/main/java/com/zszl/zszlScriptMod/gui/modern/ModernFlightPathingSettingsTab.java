package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.config.FlightPathingConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;

/** Modern settings page for Baritone flight routing and its fly-feature lifecycle. */
public final class ModernFlightPathingSettingsTab {

    private ModernFlightPathingSettingsTab() {
    }

    public static ModernSettingsTab create() {
        return ModernFormSettingsTab.builder("gui.modern.flight_pathing.title", "gui.modern.flight_pathing.subtitle",
                "gui.modern.flight_pathing.header_tip", adapter())
                .headerToggle(bool("allowFlightPathing"), "gui.modern.flight_pathing.header_toggle_tip")
                .section("gui.modern.flight_pathing.section.life", "gui.modern.flight_pathing.section.life.tip")
                .toggle("gui.modern.flight_pathing.auto_enable", "gui.modern.flight_pathing.auto_enable.tip", bool("autoEnableFly"))
                .toggle("gui.modern.flight_pathing.auto_disable", "gui.modern.flight_pathing.auto_disable.tip", bool("autoDisableFlyOnArrival"))
                .toggle("gui.modern.flight_pathing.keep_on_disconnect", "gui.modern.flight_pathing.keep_on_disconnect.tip",
                        bool("keepFlyEnabledOnDisconnect"))
                .integer("gui.modern.flight_pathing.arrival", "gui.modern.flight_pathing.arrival.tip", integer("arrivalRange"), 0, 16)
                .section("gui.modern.flight_pathing.section.plan", "gui.modern.flight_pathing.section.plan.tip")
                .toggle("gui.modern.flight_pathing.direct", "gui.modern.flight_pathing.direct.tip", bool("flightDirectLine"))
                .toggle("gui.modern.flight_pathing.descend", "gui.modern.flight_pathing.descend.tip", bool("flightAutoDescend"))
                .integer("gui.modern.flight_pathing.min_alt", "gui.modern.flight_pathing.min_alt.tip",
                        integer("flightMinAltitude"), 1, 356)
                .integer("gui.modern.flight_pathing.max_alt", "gui.modern.flight_pathing.max_alt.tip", integer("flightMaxAltitude"), 1, 356)
                .decimal("gui.modern.flight_pathing.speed", "gui.modern.flight_pathing.speed.tip", decimal("flightSpeed"), 0.05F,
                        3.00F)
                .section("gui.modern.flight_pathing.section.corridor", "gui.modern.flight_pathing.section.corridor.tip")
                .integer("gui.modern.flight_pathing.clearance", "gui.modern.flight_pathing.clearance.tip", integer("flightClearance"), 0, 8)
                .toggle("gui.modern.flight_pathing.corridor", "gui.modern.flight_pathing.corridor.tip",
                        bool("flightEntityCorridor"))
                .integer("gui.modern.flight_pathing.corridor_width", "gui.modern.flight_pathing.corridor_width.tip", integer("flightCorridorWidth"), 1,
                        9)
                .decimal("gui.modern.flight_pathing.corner", "gui.modern.flight_pathing.corner.tip",
                        decimal("flightCorridorCornerExtension"), 0.0F, 4.0F)
                .build().compactHeader();
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                FlightPathingConfig.load();
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
                normalize(settings);
                FlightPathingConfig.save();
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

    private static void normalize(Settings settings) {
        FlightPathingConfig.normalize();
        int minimumAltitude = clamp(settings.flightMinAltitude.value, 1, 356);
        int maximumAltitude = clamp(settings.flightMaxAltitude.value, 1, 356);
        settings.flightMinAltitude.value = minimumAltitude;
        settings.flightMaxAltitude.value = Math.max(minimumAltitude, maximumAltitude);
        settings.flightClearance.value = clamp(settings.flightClearance.value, 0, 8);
        settings.flightCorridorWidth.value = clamp(settings.flightCorridorWidth.value, 1, 9);
        double maximumExtension = settings.flightCorridorWidth.value / 2.0D;
        settings.flightCorridorCornerExtension.value = clamp(settings.flightCorridorCornerExtension.value, 0.0D,
                maximumExtension);
        settings.flightSpeed.value = clamp(settings.flightSpeed.value, 0.05D, 3.0D);
    }

    private static int clamp(Integer value, int minimum, int maximum) {
        int safe = value == null ? minimum : value.intValue();
        return Math.max(minimum, Math.min(maximum, safe));
    }

    private static double clamp(Double value, double minimum, double maximum) {
        double safe = value == null || value.isNaN() || value.isInfinite() ? minimum : value.doubleValue();
        return Math.max(minimum, Math.min(maximum, safe));
    }

    private static final class State {
        private final boolean allowFlightPathing;
        private final boolean flightDirectLine;
        private final int flightClearance;
        private final int flightCorridorWidth;
        private final double flightCorridorCornerExtension;
        private final boolean flightEntityCorridor;
        private final int flightMinAltitude;
        private final int flightMaxAltitude;
        private final double flightSpeed;
        private final boolean flightAutoDescend;
        private final boolean autoEnableFly;
        private final boolean autoDisableFlyOnArrival;
        private final boolean keepFlyEnabledOnDisconnect;
        private final int arrivalRange;

        private State() {
            Settings settings = settings();
            allowFlightPathing = settings.allowFlightPathing.value;
            flightDirectLine = settings.flightDirectLine.value;
            flightClearance = settings.flightClearance.value;
            flightCorridorWidth = settings.flightCorridorWidth.value;
            flightCorridorCornerExtension = settings.flightCorridorCornerExtension.value;
            flightEntityCorridor = settings.flightEntityCorridor.value;
            flightMinAltitude = settings.flightMinAltitude.value;
            flightMaxAltitude = settings.flightMaxAltitude.value;
            flightSpeed = settings.flightSpeed.value;
            flightAutoDescend = settings.flightAutoDescend.value;
            autoEnableFly = FlightPathingConfig.autoEnableFly;
            autoDisableFlyOnArrival = FlightPathingConfig.autoDisableFlyOnArrival;
            keepFlyEnabledOnDisconnect = FlightPathingConfig.keepFlyEnabledOnDisconnect;
            arrivalRange = FlightPathingConfig.arrivalRange;
        }

        private State(State source) {
            allowFlightPathing = source.allowFlightPathing;
            flightDirectLine = source.flightDirectLine;
            flightClearance = source.flightClearance;
            flightCorridorWidth = source.flightCorridorWidth;
            flightCorridorCornerExtension = source.flightCorridorCornerExtension;
            flightEntityCorridor = source.flightEntityCorridor;
            flightMinAltitude = source.flightMinAltitude;
            flightMaxAltitude = source.flightMaxAltitude;
            flightSpeed = source.flightSpeed;
            flightAutoDescend = source.flightAutoDescend;
            autoEnableFly = source.autoEnableFly;
            autoDisableFlyOnArrival = source.autoDisableFlyOnArrival;
            keepFlyEnabledOnDisconnect = source.keepFlyEnabledOnDisconnect;
            arrivalRange = source.arrivalRange;
        }

        private State(boolean allowFlightPathing, boolean flightDirectLine, int flightClearance, int flightCorridorWidth,
                double flightCorridorCornerExtension, boolean flightEntityCorridor, int flightMinAltitude,
                int flightMaxAltitude, double flightSpeed, boolean flightAutoDescend, boolean autoEnableFly,
                boolean autoDisableFlyOnArrival, boolean keepFlyEnabledOnDisconnect, int arrivalRange) {
            this.allowFlightPathing = allowFlightPathing;
            this.flightDirectLine = flightDirectLine;
            this.flightClearance = flightClearance;
            this.flightCorridorWidth = flightCorridorWidth;
            this.flightCorridorCornerExtension = flightCorridorCornerExtension;
            this.flightEntityCorridor = flightEntityCorridor;
            this.flightMinAltitude = flightMinAltitude;
            this.flightMaxAltitude = flightMaxAltitude;
            this.flightSpeed = flightSpeed;
            this.flightAutoDescend = flightAutoDescend;
            this.autoEnableFly = autoEnableFly;
            this.autoDisableFlyOnArrival = autoDisableFlyOnArrival;
            this.keepFlyEnabledOnDisconnect = keepFlyEnabledOnDisconnect;
            this.arrivalRange = arrivalRange;
        }

        private static State defaults() {
            Settings settings = settings();
            return new State(settings.allowFlightPathing.defaultValue, settings.flightDirectLine.defaultValue,
                    settings.flightClearance.defaultValue, settings.flightCorridorWidth.defaultValue,
                    settings.flightCorridorCornerExtension.defaultValue, settings.flightEntityCorridor.defaultValue,
                    settings.flightMinAltitude.defaultValue, settings.flightMaxAltitude.defaultValue,
                    settings.flightSpeed.defaultValue, settings.flightAutoDescend.defaultValue, true, true, true, 2);
        }

        private void apply() {
            Settings settings = settings();
            settings.allowFlightPathing.value = allowFlightPathing;
            settings.flightDirectLine.value = flightDirectLine;
            settings.flightClearance.value = flightClearance;
            settings.flightCorridorWidth.value = flightCorridorWidth;
            settings.flightCorridorCornerExtension.value = flightCorridorCornerExtension;
            settings.flightEntityCorridor.value = flightEntityCorridor;
            settings.flightMinAltitude.value = flightMinAltitude;
            settings.flightMaxAltitude.value = flightMaxAltitude;
            settings.flightSpeed.value = flightSpeed;
            settings.flightAutoDescend.value = flightAutoDescend;
            FlightPathingConfig.autoEnableFly = autoEnableFly;
            FlightPathingConfig.autoDisableFlyOnArrival = autoDisableFlyOnArrival;
            FlightPathingConfig.keepFlyEnabledOnDisconnect = keepFlyEnabledOnDisconnect;
            FlightPathingConfig.arrivalRange = arrivalRange;
            normalize(settings);
        }

        private static boolean getBoolean(String key) {
            Settings settings = settings();
            if ("allowFlightPathing".equals(key)) return settings.allowFlightPathing.value;
            if ("flightDirectLine".equals(key)) return settings.flightDirectLine.value;
            if ("flightEntityCorridor".equals(key)) return settings.flightEntityCorridor.value;
            if ("flightAutoDescend".equals(key)) return settings.flightAutoDescend.value;
            if ("autoEnableFly".equals(key)) return FlightPathingConfig.autoEnableFly;
            if ("autoDisableFlyOnArrival".equals(key)) return FlightPathingConfig.autoDisableFlyOnArrival;
            if ("keepFlyEnabledOnDisconnect".equals(key)) return FlightPathingConfig.keepFlyEnabledOnDisconnect;
            return false;
        }

        private static void setBoolean(String key, boolean value) {
            Settings settings = settings();
            if ("allowFlightPathing".equals(key)) settings.allowFlightPathing.value = value;
            else if ("flightDirectLine".equals(key)) settings.flightDirectLine.value = value;
            else if ("flightEntityCorridor".equals(key)) settings.flightEntityCorridor.value = value;
            else if ("flightAutoDescend".equals(key)) settings.flightAutoDescend.value = value;
            else if ("autoEnableFly".equals(key)) FlightPathingConfig.autoEnableFly = value;
            else if ("autoDisableFlyOnArrival".equals(key)) FlightPathingConfig.autoDisableFlyOnArrival = value;
            else if ("keepFlyEnabledOnDisconnect".equals(key)) FlightPathingConfig.keepFlyEnabledOnDisconnect = value;
        }

        private static int getInt(String key) {
            Settings settings = settings();
            if ("flightClearance".equals(key)) return settings.flightClearance.value;
            if ("flightCorridorWidth".equals(key)) return settings.flightCorridorWidth.value;
            if ("flightMinAltitude".equals(key)) return settings.flightMinAltitude.value;
            if ("flightMaxAltitude".equals(key)) return settings.flightMaxAltitude.value;
            if ("arrivalRange".equals(key)) return FlightPathingConfig.arrivalRange;
            return 0;
        }

        private static void setInt(String key, int value) {
            Settings settings = settings();
            if ("flightClearance".equals(key)) settings.flightClearance.value = value;
            else if ("flightCorridorWidth".equals(key)) settings.flightCorridorWidth.value = value;
            else if ("flightMinAltitude".equals(key)) settings.flightMinAltitude.value = value;
            else if ("flightMaxAltitude".equals(key)) settings.flightMaxAltitude.value = value;
            else if ("arrivalRange".equals(key)) FlightPathingConfig.arrivalRange = value;
            normalize(settings);
        }

        private static float getFloat(String key) {
            Settings settings = settings();
            if ("flightCorridorCornerExtension".equals(key)) return settings.flightCorridorCornerExtension.value.floatValue();
            if ("flightSpeed".equals(key)) return settings.flightSpeed.value.floatValue();
            return 0.0F;
        }

        private static void setFloat(String key, float value) {
            Settings settings = settings();
            if ("flightCorridorCornerExtension".equals(key)) settings.flightCorridorCornerExtension.value = (double) value;
            else if ("flightSpeed".equals(key)) settings.flightSpeed.value = (double) value;
            normalize(settings);
        }
    }
}
