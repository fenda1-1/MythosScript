package com.zszl.zszlScriptMod.gui.modern.baritone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;

import com.zszl.zszlScriptMod.config.BaritoneParkourSettingsHelper;
import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ParkourProfile;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;

/** Embedded Baritone settings, block list/map and human-like movement editor. */
public final class BaritoneWorkbenchTab {
    private BaritoneWorkbenchTab() { }
    public static ModernSettingsTab create() {
        return create("baritone_setting_editor");
    }
    public static ModernSettingsTab create(String command) {
        final Draft state = new Draft();
        ModernFormSettingsTab<Draft> workbench = ModernFormSettingsTab.builder("gui.modern.baritone_wb.u001", "gui.modern.baritone_wb.u002", "gui.modern.baritone_wb.u003", adapter(state))
                .section("gui.modern.baritone_wb.u004", "gui.modern.baritone_wb.u005")
                .toggle("gui.modern.baritone_wb.u006", "gui.modern.baritone_wb.u007", bool(state, "sprint"))
                .toggle("gui.modern.baritone_wb.u008", "gui.modern.baritone_wb.u009", bool(state, "parkour"))
                .choice("gui.modern.baritone_wb.u010", "gui.modern.baritone_wb.u011", profile(state), ModernFormSettingsTab.options(ModernFormSettingsTab.option(ParkourProfile.STABLE, "gui.modern.baritone_wb.u012"), ModernFormSettingsTab.option(ParkourProfile.BALANCED, "gui.modern.baritone_wb.u013"), ModernFormSettingsTab.option(ParkourProfile.EXTREME, "gui.modern.baritone_wb.u014")))
                .section("gui.modern.baritone_wb.u015", "gui.modern.baritone_wb.u016")
                .text("gui.modern.baritone_wb.u017", "gui.modern.baritone_wb.u018", text(state, false), "gui.modern.baritone_wb.u019", 4096)
                .section("gui.modern.baritone_wb.u020", "gui.modern.baritone_wb.u021")
                .text("gui.modern.baritone_wb.u022", "gui.modern.baritone_wb.u023", text(state, true), "minecraft:stone=minecraft:cobblestone", 8192)
                .action("gui.modern.baritone_wb.u024", "gui.modern.baritone_wb.u025", "gui.modern.baritone_wb.u026", ModernFormSettingsTab.ActionStyle.PRIMARY, "gui.modern.baritone_wb.u027", tab -> { try { state.apply(); } catch (Exception e) { tab.showStatus(com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr("gui.modern.baritone_wb.u028") + e.getMessage()); } })
                .action("gui.modern.baritone_wb.u029", "gui.modern.baritone_wb.u030", "gui.modern.baritone_wb.u031", ModernFormSettingsTab.ActionStyle.SECONDARY, "gui.modern.baritone_wb.u032", tab -> { state.cancel(); tab.refreshValues(); })
                .action("gui.modern.baritone_wb.u033", "gui.modern.baritone_wb.u034", "gui.modern.baritone_wb.u035", ModernFormSettingsTab.ActionStyle.SECONDARY, "gui.modern.baritone_wb.u036", tab -> { state.stable(); tab.refreshValues(); })
                .build();
        if ("baritone_block_list".equals(command)) {
            workbench.scrollToSection(1);
        } else if ("baritone_block_map".equals(command)) {
            workbench.scrollToSection(2);
        }
        return workbench;
    }
    private static ModernFormSettingsTab.BooleanValue bool(final Draft d, final String key) { return new ModernFormSettingsTab.BooleanValue() { public boolean get() { return "sprint".equals(key) ? d.allowSprint : d.allowParkour; } public void set(boolean value) { if ("sprint".equals(key)) d.allowSprint = value; else d.allowParkour = value; } }; }
    private static ModernFormSettingsTab.ChoiceValue<ParkourProfile> profile(final Draft d) { return new ModernFormSettingsTab.ChoiceValue<ParkourProfile>() { public ParkourProfile get() { return d.profile; } public void set(ParkourProfile value) { d.profile = value == null ? ParkourProfile.STABLE : value; } }; }
    private static ModernFormSettingsTab.TextValue text(final Draft d, final boolean map) { return new ModernFormSettingsTab.TextValue() { public String get() { return map ? d.mappingText : d.listText; } public void set(String value) { if (map) d.mappingText = value == null ? "" : value.trim(); else d.listText = value == null ? "" : value.trim(); } }; }
    private static ModernFormSettingsTab.StateAdapter<Draft> adapter(final Draft d) { return new ModernFormSettingsTab.StateAdapter<Draft>() { public void load() { } public Draft capture() { return d.copy(); } public Draft copy(Draft source) { return source.copy(); } public void restore(Draft source) { d.copyFrom(source); } public void save() { d.apply(); } public Draft createDefaults() { return new Draft(); } }; }
    static final class Draft {
        boolean allowSprint; boolean allowParkour; ParkourProfile profile; String listText; String mappingText; final Draft initial;
        Draft() { Settings s = BaritoneAPI.getSettings(); allowSprint = s.allowSprint.value; allowParkour = s.allowParkour.value; profile = s.parkourProfile.value; listText = names(s.blocksToAvoid.value); mappingText = mappings(s.buildSubstitutes.value); initial = null; }
        Draft(Draft source) { allowSprint = source.allowSprint; allowParkour = source.allowParkour; profile = source.profile; listText = source.listText; mappingText = source.mappingText; initial = null; }
        Draft copy() { return new Draft(this); }
        void copyFrom(Draft source) { allowSprint = source.allowSprint; allowParkour = source.allowParkour; profile = source.profile; listText = source.listText; mappingText = source.mappingText; }
        void cancel() { Settings s = BaritoneAPI.getSettings(); allowSprint = s.allowSprint.value; allowParkour = s.allowParkour.value; profile = s.parkourProfile.value; listText = names(s.blocksToAvoid.value); mappingText = mappings(s.buildSubstitutes.value); }
        void stable() { allowParkour = true; profile = ParkourProfile.STABLE; }
        void apply() { Settings s = BaritoneAPI.getSettings(); s.allowSprint.value = allowSprint; s.allowParkour.value = allowParkour; s.parkourProfile.value = profile; s.blocksToAvoid.value = blocks(listText); s.buildSubstitutes.value = mappings(mappingText); BaritoneParkourSettingsHelper.syncRuntimeOverrides(); SettingsUtil.save(s); }
        private static String names(List<Block> blocks) { List<String> names = new ArrayList<String>(); for (Block block : blocks) names.add(String.valueOf(Block.REGISTRY.getNameForObject(block))); return join(names, ","); }
        private static String mappings(Map<Block, List<Block>> values) { List<String> result = new ArrayList<String>(); for (Map.Entry<Block, List<Block>> entry : values.entrySet()) result.add(String.valueOf(Block.REGISTRY.getNameForObject(entry.getKey())) + "=" + names(entry.getValue())); return join(result, ";"); }
        private static List<Block> blocks(String raw) { List<Block> result = new ArrayList<Block>(); if (raw.trim().isEmpty()) return result; for (String value : raw.split(",")) result.add(block(value)); return result; }
        private static Map<Block, List<Block>> mappings(String raw) { Map<Block, List<Block>> result = new LinkedHashMap<Block, List<Block>>(); if (raw.trim().isEmpty()) return result; for (String entry : raw.split(";")) { String[] pair = entry.split("=", 2); if (pair.length != 2) throw new IllegalArgumentException("gui.modern.baritone_wb.u037"); result.put(block(pair[0]), blocks(pair[1])); } return result; }
        private static Block block(String value) { Block block = Block.getBlockFromName(value.trim()); if (block == null) throw new IllegalArgumentException(com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr("gui.modern.baritone_wb.u038") + value.trim()); return block; }
        private static String join(List<String> values, String separator) { StringBuilder result = new StringBuilder(); for (String value : values) { if (result.length() > 0) result.append(separator); result.append(value); } return result.toString(); }
    }
}
