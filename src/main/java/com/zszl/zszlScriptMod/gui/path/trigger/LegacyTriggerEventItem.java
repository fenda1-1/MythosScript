package com.zszl.zszlScriptMod.gui.path.trigger;

public final class LegacyTriggerEventItem {

    public final boolean header;
    public final String label;
    public final String type;
    public final String help;

    public LegacyTriggerEventItem(boolean header, String label, String type, String help) {
        this.header = header;
        this.label = label;
        this.type = type;
        this.help = help == null ? "" : help;
    }
}
