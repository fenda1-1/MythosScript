package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.movement;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;

import net.minecraft.client.resources.I18n;

import java.util.function.Function;

public final class MovementActionLibraryGroup {
    private MovementActionLibraryGroup() {
    }

    public static ActionLibraryNode buildRoot(Function<String, ActionLibraryNode> itemFactory) {
        return ActionLibraryNode.group("group_movement",
                I18n.format("gui.path.action_editor.group.movement"),
                ActionLibraryNode.group("group_movement.control",
                        I18n.format("gui.path.action_editor.group.movement.control"),
                        itemFactory.apply("delay"),
                        itemFactory.apply("jump"),
                        itemFactory.apply("setview"),
                        itemFactory.apply("hunt"),
                        itemFactory.apply("follow_entity")),
                ActionLibraryNode.group("group_movement_target",
                        I18n.format("gui.path.action_editor.group.movement.target"),
                        itemFactory.apply("rightclickblock"),
                        itemFactory.apply("rightclickentity")));
    }
}
