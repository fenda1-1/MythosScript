package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionLibraryNode {
    public String id;
    public String label;
    public String actionType;
    public List<ActionLibraryNode> children = new ArrayList<>();

    public static ActionLibraryNode group(String id, String label, ActionLibraryNode... children) {
        ActionLibraryNode node = new ActionLibraryNode();
        node.id = id;
        node.label = label;
        node.children = children == null ? new ArrayList<ActionLibraryNode>()
                : new ArrayList<ActionLibraryNode>(Arrays.asList(children));
        return node;
    }

    public static ActionLibraryNode item(String id, String label, String actionType) {
        ActionLibraryNode node = new ActionLibraryNode();
        node.id = id;
        node.label = label;
        node.actionType = actionType;
        return node;
    }

    public boolean isGroup() {
        return actionType == null;
    }
}
