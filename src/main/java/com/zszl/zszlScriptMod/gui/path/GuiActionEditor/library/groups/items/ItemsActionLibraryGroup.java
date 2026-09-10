package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.items;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;

import net.minecraft.client.resources.I18n;

import java.util.function.Function;

public final class ItemsActionLibraryGroup {
    private ItemsActionLibraryGroup() {
    }

    public static ActionLibraryNode buildRoot(Function<String, ActionLibraryNode> itemFactory) {
        return ActionLibraryNode.group("group_items",
                I18n.format("gui.path.action_editor.group.items"),
                ActionLibraryNode.group("group_items_usage",
                        I18n.format("gui.path.action_editor.group.items.usage"),
                        itemFactory.apply("use_hotbar_item"),
                        itemFactory.apply("use_held_item"),
                        itemFactory.apply("move_inventory_item_to_hotbar"),
                        itemFactory.apply("spread_inventory_item"),
                        itemFactory.apply("stack_inventory_item"),
                        itemFactory.apply("pickup_nearby_items"),
                        itemFactory.apply("switch_hotbar_slot"),
                        itemFactory.apply("silentuse"),
                         itemFactory.apply("autoeat"),
                         itemFactory.apply("autoequip"),
                        itemFactory.apply("autopickup")));
    }
}
