package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.toggle;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;

import net.minecraft.client.resources.I18n;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class ToggleActionLibraryGroup {
    public static final List<String> CORE_ACTION_TYPES = Collections.unmodifiableList(Arrays.asList(
            "toggle_autoeat",
            "toggle_autofishing",
            "toggle_auto_pickup",
            "toggle_kill_aura",
            "toggle_auto_follow",
            "toggle_fly",
            "toggle_conditional_execution",
            "toggle_auto_escape",
            "toggle_baritone_free_look",
            "toggle_baritone_human_like",
            "toggle_baritone_flight",
            "toggle_other_feature"));

    private ToggleActionLibraryGroup() {
    }

    public static ActionLibraryNode buildRoot(Function<String, ActionLibraryNode> itemFactory) {
        ActionLibraryNode[] items = new ActionLibraryNode[CORE_ACTION_TYPES.size()];
        for (int i = 0; i < CORE_ACTION_TYPES.size(); i++) {
            items[i] = itemFactory.apply(CORE_ACTION_TYPES.get(i));
        }
        return ActionLibraryNode.group("group_script_feature_toggle",
                I18n.format("gui.path.action_editor.group.script_feature_toggle"),
                ActionLibraryNode.group("group_script_feature_toggle_core",
                        I18n.format("gui.path.action_editor.group.script_feature_toggle.core"),
                        items));
    }
}
