package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.config.BaritoneParkourSettingsHelper;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ParkourProfile;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;

/** Modern embedded editor for the Baritone parkour-specific settings set. */
public final class ModernBaritoneParkourSettingsTab {

    private ModernBaritoneParkourSettingsTab() {
    }

    public static ModernSettingsTab create() {
        return create("baritone_parkour");
    }

    public static ModernSettingsTab create(String command) {
        ModernFormSettingsTab<State> workbench = ModernFormSettingsTab.builder("gui.modern.parkour.u001", "gui.modern.parkour.u002",
                "gui.modern.parkour.u003",
                adapter())
                .headerToggle(bool("parkourMode"), "gui.modern.parkour.u004")
                .section("gui.modern.parkour.u005", "gui.modern.parkour.u006")
                .choice("gui.modern.parkour.u007", "gui.modern.parkour.u008", profile(),
                        ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(ParkourProfile.STABLE, "gui.modern.parkour.u009"),
                                ModernFormSettingsTab.option(ParkourProfile.BALANCED, "gui.modern.parkour.u010"),
                                ModernFormSettingsTab.option(ParkourProfile.EXTREME, "gui.modern.parkour.u011")))
                .toggle("gui.modern.parkour.u012", "gui.modern.parkour.u013", bool("allowParkour"))
                .toggle("gui.modern.parkour.u014", "gui.modern.parkour.u015", bool("allowParkourAscend"))
                .toggle("gui.modern.parkour.u016", "gui.modern.parkour.u017",
                        bool("allowParkourPlace"))
                .section("gui.modern.parkour.u018", "gui.modern.parkour.u019")
                .toggle("gui.modern.parkour.u020", "gui.modern.parkour.u021", bool("parkourDebugRender"))
                .toggle("gui.modern.parkour.u022", "gui.modern.parkour.u023", bool("backfill"))
                .section("gui.modern.parkour.u024", "gui.modern.parkour.u025")
                .action("gui.modern.parkour.u026", "gui.modern.parkour.u027",
                        "gui.modern.parkour.u028", ModernFormSettingsTab.ActionStyle.SECONDARY, "gui.modern.parkour.u029",
                        new ModernFormSettingsTab.FormAction<State>() {
                            @Override
                            public void execute(ModernFormSettingsTab<State> tab) {
                                State.applyStablePresetDraft();
                                tab.refreshValues();
                            }
                        })
                .build().compactHeader();
        if ("baritone_parkour_preset".equals(command)) {
            workbench.scrollToSection(2);
        }
        return workbench;
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                BaritoneParkourSettingsHelper.syncRuntimeOverrides();
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
                BaritoneParkourSettingsHelper.syncRuntimeOverrides();
                SettingsUtil.save(settings());
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

    private static ModernFormSettingsTab.ChoiceValue<ParkourProfile> profile() {
        return new ModernFormSettingsTab.ChoiceValue<ParkourProfile>() {
            @Override
            public ParkourProfile get() {
                ParkourProfile value = settings().parkourProfile.value;
                return value == null ? ParkourProfile.STABLE : value;
            }

            @Override
            public void set(ParkourProfile value) {
                settings().parkourProfile.value = value == null ? ParkourProfile.STABLE : value;
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
        private final boolean parkourMode;
        private final ParkourProfile parkourProfile;
        private final boolean parkourDebugRender;
        private final boolean allowParkour;
        private final boolean allowParkourPlace;
        private final boolean allowParkourAscend;
        private final boolean backfill;

        private State() {
            Settings settings = settings();
            parkourMode = settings.parkourMode.value;
            parkourProfile = settings.parkourProfile.value == null ? ParkourProfile.STABLE : settings.parkourProfile.value;
            parkourDebugRender = settings.parkourDebugRender.value;
            allowParkour = settings.allowParkour.value;
            allowParkourPlace = settings.allowParkourPlace.value;
            allowParkourAscend = settings.allowParkourAscend.value;
            backfill = settings.backfill.value;
        }

        private State(State source) {
            parkourMode = source.parkourMode;
            parkourProfile = source.parkourProfile;
            parkourDebugRender = source.parkourDebugRender;
            allowParkour = source.allowParkour;
            allowParkourPlace = source.allowParkourPlace;
            allowParkourAscend = source.allowParkourAscend;
            backfill = source.backfill;
        }

        private static State defaults() {
            Settings settings = settings();
            return new State(settings.parkourMode.defaultValue,
                    settings.parkourProfile.defaultValue == null ? ParkourProfile.STABLE : settings.parkourProfile.defaultValue,
                    settings.parkourDebugRender.defaultValue, settings.allowParkour.defaultValue,
                    settings.allowParkourPlace.defaultValue, settings.allowParkourAscend.defaultValue,
                    settings.backfill.defaultValue);
        }

        private State(boolean parkourMode, ParkourProfile parkourProfile, boolean parkourDebugRender,
                boolean allowParkour, boolean allowParkourPlace, boolean allowParkourAscend, boolean backfill) {
            this.parkourMode = parkourMode;
            this.parkourProfile = parkourProfile == null ? ParkourProfile.STABLE : parkourProfile;
            this.parkourDebugRender = parkourDebugRender;
            this.allowParkour = allowParkour;
            this.allowParkourPlace = allowParkourPlace;
            this.allowParkourAscend = allowParkourAscend;
            this.backfill = backfill;
        }

        private void apply() {
            Settings settings = settings();
            settings.parkourProfile.value = parkourProfile;
            settings.parkourDebugRender.value = parkourDebugRender;
            settings.allowParkour.value = allowParkour;
            settings.allowParkourPlace.value = allowParkourPlace;
            settings.allowParkourAscend.value = allowParkourAscend;
            settings.backfill.value = backfill;
            settings.parkourMode.value = parkourMode;
            BaritoneParkourSettingsHelper.syncRuntimeOverrides();
        }

        private static boolean getBoolean(String key) {
            Settings settings = settings();
            if ("parkourMode".equals(key)) return settings.parkourMode.value;
            if ("parkourDebugRender".equals(key)) return settings.parkourDebugRender.value;
            if ("allowParkour".equals(key)) return settings.allowParkour.value;
            if ("allowParkourPlace".equals(key)) return settings.allowParkourPlace.value;
            if ("allowParkourAscend".equals(key)) return settings.allowParkourAscend.value;
            if ("backfill".equals(key)) return settings.backfill.value;
            return false;
        }

        private static void setBoolean(String key, boolean value) {
            Settings settings = settings();
            if ("parkourMode".equals(key)) {
                settings.parkourMode.value = value;
                BaritoneParkourSettingsHelper.syncRuntimeOverrides();
            } else if ("parkourDebugRender".equals(key)) {
                settings.parkourDebugRender.value = value;
            } else if ("allowParkour".equals(key)) {
                settings.allowParkour.value = value;
            } else if ("allowParkourPlace".equals(key)) {
                settings.allowParkourPlace.value = value;
            } else if ("allowParkourAscend".equals(key)) {
                settings.allowParkourAscend.value = value;
            } else if ("backfill".equals(key)) {
                settings.backfill.value = value;
            }
        }

        private static void applyStablePresetDraft() {
            Settings settings = settings();
            settings.parkourMode.value = true;
            settings.parkourProfile.value = ParkourProfile.STABLE;
            settings.parkourDebugRender.value = false;
            settings.allowParkour.value = true;
            settings.allowParkourAscend.value = true;
            settings.allowParkourPlace.value = false;
            settings.backfill.value = false;
            BaritoneParkourSettingsHelper.syncRuntimeOverrides();
        }
    }
}
