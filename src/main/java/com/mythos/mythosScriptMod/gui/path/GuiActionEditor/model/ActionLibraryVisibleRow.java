package com.mythos.mythosScriptMod.gui.path.GuiActionEditor.model;

public class ActionLibraryVisibleRow {
    public final ActionLibraryNode node;
    public final int depth;

    public ActionLibraryVisibleRow(ActionLibraryNode node, int depth) {
        this.node = node;
        this.depth = depth;
    }
}
