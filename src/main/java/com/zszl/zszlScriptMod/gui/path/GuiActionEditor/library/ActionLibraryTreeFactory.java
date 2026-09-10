package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.flow.FlowActionLibraryGroup;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.interaction.InteractionActionLibraryGroup;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.items.ItemsActionLibraryGroup;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.movement.MovementActionLibraryGroup;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.groups.toggle.ToggleActionLibraryGroup;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ActionLibraryTreeFactory {
    private ActionLibraryTreeFactory() {
    }

    public static List<ActionLibraryNode> buildRoots(Collection<String> favoriteActionTypes,
            Collection<String> recentActionTypes,
            Predicate<String> shouldHideActionType,
            Function<String, String> labelResolver) {
        List<ActionLibraryNode> roots = new ArrayList<ActionLibraryNode>();
        Function<String, ActionLibraryNode> itemFactory = actionType -> createActionLibraryItem(actionType,
                shouldHideActionType, labelResolver);

        ActionLibraryNode quickAccessRoot = buildQuickAccessRoot(favoriteActionTypes, recentActionTypes, itemFactory);
        if (quickAccessRoot != null) {
            roots.add(quickAccessRoot);
        }

        roots.add(InteractionActionLibraryGroup.buildRoot(itemFactory));
        roots.add(MovementActionLibraryGroup.buildRoot(itemFactory));
        roots.add(ItemsActionLibraryGroup.buildRoot(itemFactory));
        roots.add(ToggleActionLibraryGroup.buildRoot(itemFactory));
        roots.add(FlowActionLibraryGroup.buildRoot(itemFactory));
        return roots;
    }

    private static ActionLibraryNode buildQuickAccessRoot(Collection<String> favoriteActionTypes,
            Collection<String> recentActionTypes, Function<String, ActionLibraryNode> itemFactory) {
        List<ActionLibraryNode> children = new ArrayList<ActionLibraryNode>();
        ActionLibraryNode favoritesGroup = buildQuickAccessGroup("group_quick_access_favorites", "收藏动作",
                favoriteActionTypes, itemFactory);
        if (favoritesGroup != null) {
            children.add(favoritesGroup);
        }
        ActionLibraryNode recentGroup = buildQuickAccessGroup("group_quick_access_recent", "最近使用",
                recentActionTypes, itemFactory);
        if (recentGroup != null) {
            children.add(recentGroup);
        }
        if (children.isEmpty()) {
            return null;
        }
        ActionLibraryNode root = new ActionLibraryNode();
        root.id = "group_quick_access_root";
        root.label = "快捷访问";
        root.children = children;
        return root;
    }

    private static ActionLibraryNode buildQuickAccessGroup(String id, String label, Collection<String> actionTypes,
            Function<String, ActionLibraryNode> itemFactory) {
        if (actionTypes == null || actionTypes.isEmpty()) {
            return null;
        }
        List<ActionLibraryNode> items = new ArrayList<ActionLibraryNode>();
        LinkedHashSet<String> dedup = new LinkedHashSet<String>();
        for (String actionType : actionTypes) {
            String normalized = normalizeActionType(actionType);
            if (normalized.isEmpty() || !dedup.add(normalized)) {
                continue;
            }
            ActionLibraryNode item = itemFactory.apply(normalized);
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        ActionLibraryNode group = new ActionLibraryNode();
        group.id = id;
        group.label = label;
        group.children = items;
        return group;
    }

    private static ActionLibraryNode createActionLibraryItem(String actionType, Predicate<String> shouldHideActionType,
            Function<String, String> labelResolver) {
        String normalized = normalizeActionType(actionType);
        if (normalized.isEmpty() || shouldHideActionType != null && shouldHideActionType.test(normalized)) {
            return null;
        }
        String label = labelResolver == null ? normalized : labelResolver.apply(normalized);
        return ActionLibraryNode.item("item_" + normalized, label, normalized);
    }

    private static String normalizeActionType(String actionType) {
        return actionType == null ? "" : actionType.trim().toLowerCase(Locale.ROOT);
    }
}
