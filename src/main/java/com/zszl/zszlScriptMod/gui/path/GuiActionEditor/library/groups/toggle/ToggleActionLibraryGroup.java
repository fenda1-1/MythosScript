package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.toggle;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;

import net.minecraft.client.resources.I18n;

import java.util.function.Function;

public final class ToggleActionLibraryGroup {
    private ToggleActionLibraryGroup() {
    }

    public static ActionLibraryNode buildRoot(Function<String, ActionLibraryNode> itemFactory) {
        return ActionLibraryNode.group("group_script_feature_toggle",
                I18n.format("gui.path.action_editor.group.script_feature_toggle"),
                ActionLibraryNode.group("group_script_feature_toggle_core",
                        I18n.format("gui.path.action_editor.group.script_feature_toggle.core"),
                        itemFactory.apply("toggle_autoeat"),
                        itemFactory.apply("toggle_autofishing"),
                        itemFactory.apply("toggle_kill_aura"),
                        itemFactory.apply("toggle_fly"),
                        itemFactory.apply("toggle_conditional_execution"),
                        itemFactory.apply("toggle_auto_escape"),
                        itemFactory.apply("toggle_baritone_free_look"),
                        itemFactory.apply("toggle_baritone_human_like"),
                        itemFactory.apply("toggle_baritone_flight"),
                        itemFactory.apply("toggle_other_feature")));
    }
}
