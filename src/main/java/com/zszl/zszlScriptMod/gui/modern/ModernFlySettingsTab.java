package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.handlers.FlyHandler;

/** Modern embedded settings page for the flight feature. */
public final class ModernFlySettingsTab {

    private ModernFlySettingsTab() {
    }

    public static ModernSettingsTab create() {
        return ModernFormSettingsTab.builder("gui.modern.fly.title", "gui.modern.fly.subtitle",
                "gui.modern.fly.header_tip", adapter())
                .headerToggle(bool("enabled"), "gui.modern.fly.header_toggle_tip")
                .section("gui.modern.fly.mode", "gui.modern.fly.section.mode.tip")
                .choice("gui.modern.fly.mode", "gui.modern.fly.mode.tip", choice("flightMode"),
                        ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(FlyHandler.MODE_MOTION, "gui.modern.fly.mode.motion"),
                                ModernFormSettingsTab.option(FlyHandler.MODE_GLIDE, "gui.modern.fly.mode.glide"),
                                ModernFormSettingsTab.option(FlyHandler.MODE_PULSE, "gui.modern.fly.mode.pulse")))
                .toggle("gui.modern.fly.auto_takeoff", "gui.modern.fly.auto_takeoff.tip", bool("autoTakeoff"))
                .toggle("gui.modern.fly.stop_motion", "gui.modern.fly.stop_motion.tip", bool("stopMotionOnDisable"))
                .section("gui.modern.fly.section.speed", "gui.modern.fly.section.speed.tip")
                .decimal("gui.modern.fly.horizontal", "gui.modern.fly.horizontal.tip", decimal("horizontalSpeed"), 0.05F, 10.00F)
                .decimal("gui.modern.fly.vertical", "gui.modern.fly.vertical.tip", decimal("verticalSpeed"), 0.05F, 10.00F)
                .decimal("gui.modern.fly.sprint", "gui.modern.fly.sprint.tip", decimal("sprintMultiplier"), 1.00F, 10.00F)
                .decimal("gui.modern.fly.glide_fall", "gui.modern.fly.glide_fall.tip", decimal("glideFallSpeed"), 0.00F, 0.50F)
                .decimal("gui.modern.fly.pulse_boost", "gui.modern.fly.pulse_boost.tip", decimal("pulseBoost"), 0.05F, 10.00F)
                .enabledWhen(predicateMode(FlyHandler.MODE_PULSE))
                .integer("gui.modern.fly.pulse_interval", "gui.modern.fly.pulse_interval.tip", integer("pulseIntervalTicks"), 1, 40)
                .enabledWhen(predicateMode(FlyHandler.MODE_PULSE))
                .section("gui.modern.fly.section.protect", "gui.modern.fly.section.protect.tip")
                .toggle("gui.modern.fly.no_collision", "gui.modern.fly.no_collision.tip", bool("enableNoCollision"))
                .toggle("gui.modern.fly.anti_knockback", "gui.modern.fly.anti_knockback.tip", bool("enableAntiKnockback"))
                .toggle("gui.modern.fly.anti_kick", "gui.modern.fly.anti_kick.tip", bool("enableAntiKick"))
                .integer("gui.modern.fly.anti_kick_interval", "gui.modern.fly.anti_kick_interval.tip", integer("antiKickIntervalTicks"), 4, 80)
                .enabledWhen(predicate("enableAntiKick"))
                .decimal("gui.modern.fly.anti_kick_distance", "gui.modern.fly.anti_kick_distance.tip", decimal("antiKickDistance"), 0.01F, 0.20F)
                .enabledWhen(predicate("enableAntiKick"))
                .build().compactHeader();
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                FlyHandler.loadConfig();
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
                FlyHandler.saveConfig();
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

    private static ModernFormSettingsTab.Condition predicateMode(final String mode) {
        return new ModernFormSettingsTab.Condition() {
            @Override
            public boolean matches() {
                return mode.equalsIgnoreCase(FlyHandler.flightMode);
            }
        };
    }

    /** Defensive snapshot of every persisted fly configuration value. */
    private static final class State {
        private final boolean enabled = FlyHandler.enabled;
        private final String flightMode = FlyHandler.flightMode;
        private final boolean autoTakeoff = FlyHandler.autoTakeoff;
        private final boolean stopMotionOnDisable = FlyHandler.stopMotionOnDisable;
        private final boolean enableNoCollision = FlyHandler.enableNoCollision;
        private final boolean enableAntiKnockback = FlyHandler.enableAntiKnockback;
        private final boolean enableAntiKick = FlyHandler.enableAntiKick;
        private final float horizontalSpeed = FlyHandler.horizontalSpeed;
        private final float verticalSpeed = FlyHandler.verticalSpeed;
        private final float glideFallSpeed = FlyHandler.glideFallSpeed;
        private final float sprintMultiplier = FlyHandler.sprintMultiplier;
        private final float pulseBoost = FlyHandler.pulseBoost;
        private final int pulseIntervalTicks = FlyHandler.pulseIntervalTicks;
        private final int antiKickIntervalTicks = FlyHandler.antiKickIntervalTicks;
        private final float antiKickDistance = FlyHandler.antiKickDistance;

        private void apply() {
            FlyHandler.flightMode = flightMode;
            FlyHandler.autoTakeoff = autoTakeoff;
            FlyHandler.stopMotionOnDisable = stopMotionOnDisable;
            FlyHandler.enableNoCollision = enableNoCollision;
            FlyHandler.enableAntiKnockback = enableAntiKnockback;
            FlyHandler.enableAntiKick = enableAntiKick;
            FlyHandler.horizontalSpeed = horizontalSpeed;
            FlyHandler.verticalSpeed = verticalSpeed;
            FlyHandler.glideFallSpeed = glideFallSpeed;
            FlyHandler.sprintMultiplier = sprintMultiplier;
            FlyHandler.pulseBoost = pulseBoost;
            FlyHandler.pulseIntervalTicks = pulseIntervalTicks;
            FlyHandler.antiKickIntervalTicks = antiKickIntervalTicks;
            FlyHandler.antiKickDistance = antiKickDistance;
            // Apply the enabled flag last so disabling honors the restored
            // stop-motion preference and enabling sees the restored mode.
            FlyHandler.INSTANCE.setEnabledForSettingsPreview(enabled);
        }

        private static void applyDefaults() {
            FlyHandler.flightMode = FlyHandler.MODE_MOTION;
            FlyHandler.autoTakeoff = true;
            FlyHandler.stopMotionOnDisable = true;
            FlyHandler.enableNoCollision = true;
            FlyHandler.enableAntiKnockback = true;
            FlyHandler.enableAntiKick = false;
            FlyHandler.horizontalSpeed = 0.85F;
            FlyHandler.verticalSpeed = 0.42F;
            FlyHandler.glideFallSpeed = 0.04F;
            FlyHandler.sprintMultiplier = 1.25F;
            FlyHandler.pulseBoost = 0.28F;
            FlyHandler.pulseIntervalTicks = 4;
            FlyHandler.antiKickIntervalTicks = 16;
            FlyHandler.antiKickDistance = 0.04F;
        }

        private static boolean getBoolean(String name) {
            switch (name) {
                case "enabled": return FlyHandler.enabled;
                case "autoTakeoff": return FlyHandler.autoTakeoff;
                case "stopMotionOnDisable": return FlyHandler.stopMotionOnDisable;
                case "enableNoCollision": return FlyHandler.enableNoCollision;
                case "enableAntiKnockback": return FlyHandler.enableAntiKnockback;
                case "enableAntiKick": return FlyHandler.enableAntiKick;
                default: return false;
            }
        }

        private static void setBoolean(String name, boolean value) {
            switch (name) {
                case "enabled": FlyHandler.INSTANCE.setEnabledForSettingsPreview(value); break;
                case "autoTakeoff": FlyHandler.autoTakeoff = value; break;
                case "stopMotionOnDisable": FlyHandler.stopMotionOnDisable = value; break;
                case "enableNoCollision": FlyHandler.enableNoCollision = value; break;
                case "enableAntiKnockback": FlyHandler.enableAntiKnockback = value; break;
                case "enableAntiKick": FlyHandler.enableAntiKick = value; break;
                default: break;
            }
        }

        private static int getInt(String name) {
            if ("pulseIntervalTicks".equals(name)) return FlyHandler.pulseIntervalTicks;
            if ("antiKickIntervalTicks".equals(name)) return FlyHandler.antiKickIntervalTicks;
            return 0;
        }

        private static void setInt(String name, int value) {
            if ("pulseIntervalTicks".equals(name)) FlyHandler.pulseIntervalTicks = value;
            if ("antiKickIntervalTicks".equals(name)) FlyHandler.antiKickIntervalTicks = value;
        }

        private static float getFloat(String name) {
            switch (name) {
                case "horizontalSpeed": return FlyHandler.horizontalSpeed;
                case "verticalSpeed": return FlyHandler.verticalSpeed;
                case "glideFallSpeed": return FlyHandler.glideFallSpeed;
                case "sprintMultiplier": return FlyHandler.sprintMultiplier;
                case "pulseBoost": return FlyHandler.pulseBoost;
                case "antiKickDistance": return FlyHandler.antiKickDistance;
                default: return 0.0F;
            }
        }

        private static void setFloat(String name, float value) {
            switch (name) {
                case "horizontalSpeed": FlyHandler.horizontalSpeed = value; break;
                case "verticalSpeed": FlyHandler.verticalSpeed = value; break;
                case "glideFallSpeed": FlyHandler.glideFallSpeed = value; break;
                case "sprintMultiplier": FlyHandler.sprintMultiplier = value; break;
                case "pulseBoost": FlyHandler.pulseBoost = value; break;
                case "antiKickDistance": FlyHandler.antiKickDistance = value; break;
                default: break;
            }
        }

        private static String getString(String name) {
            return "flightMode".equals(name) ? FlyHandler.flightMode : "";
        }

        private static void setString(String name, String value) {
            if ("flightMode".equals(name)) FlyHandler.flightMode = value;
        }
    }
}
