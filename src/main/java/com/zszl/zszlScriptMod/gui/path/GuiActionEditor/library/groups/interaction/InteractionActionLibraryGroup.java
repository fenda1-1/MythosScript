package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.interaction;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;

import net.minecraft.client.resources.I18n;

import java.util.function.Function;

public final class InteractionActionLibraryGroup {
    private InteractionActionLibraryGroup() {
    }

    public static ActionLibraryNode buildRoot(Function<String, ActionLibraryNode> itemFactory) {
        return ActionLibraryNode.group("group_interaction",
                I18n.format("gui.path.action_editor.group.interaction"),
                ActionLibraryNode.group("group_interaction_user",
                        I18n.format("gui.path.action_editor.group.interaction.user"),
                        itemFactory.apply("command"),
                        itemFactory.apply("system_message"),
                        itemFactory.apply("paste_text"),
                        itemFactory.apply("disconnect"),
                        itemFactory.apply("key"),
                        itemFactory.apply("click")),
                ActionLibraryNode.group("group_interaction_gui",
                        I18n.format("gui.path.action_editor.group.interaction.gui"),
                        itemFactory.apply("window_click"),
                        itemFactory.apply("take_all_items_safe"),
                        itemFactory.apply("dropfiltereditems"),
                        itemFactory.apply("move_inventory_items_to_chest_slots"),
                        itemFactory.apply("warehouse_auto_deposit"),
                        itemFactory.apply("blocknextgui"),
                        itemFactory.apply("hidecurrentgui"),
                        itemFactory.apply("showhiddengui"),
                        itemFactory.apply("close_container_window"),
                        itemFactory.apply("hud_text_check")));
    }
}
