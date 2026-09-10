package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryVisibleRow;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ActionLibraryViewSupport {
    public enum MatchKind {
        NONE,
        LABEL,
        ACTION_TYPE,
        PINYIN
    }

    private ActionLibraryViewSupport() {
    }

    public static List<ActionLibraryVisibleRow> buildVisibleRows(List<ActionLibraryNode> roots,
            Set<String> expandedGroupIds, String filter) {
        List<ActionLibraryVisibleRow> rows = new ArrayList<ActionLibraryVisibleRow>();
        if (roots == null) {
            return rows;
        }
        for (ActionLibraryNode root : roots) {
            appendVisibleRows(root, 0, filter, expandedGroupIds, rows);
        }
        return rows;
    }

    public static MatchKind getMatchKind(ActionLibraryNode node, String normalizedFilter) {
        if (node == null) {
            return MatchKind.NONE;
        }
        if (normalizedFilter == null || normalizedFilter.isEmpty()) {
            return MatchKind.LABEL;
        }
        String label = safe(node.label);
        String actionType = safe(node.actionType);
        String normalizedLabel = PinyinSearchHelper.normalizeQuery(label);
        if (!normalizedLabel.isEmpty() && normalizedLabel.contains(normalizedFilter)) {
            return MatchKind.LABEL;
        }
        String normalizedActionType = PinyinSearchHelper.normalizeQuery(actionType);
        if (!normalizedActionType.isEmpty() && normalizedActionType.contains(normalizedFilter)) {
            return MatchKind.ACTION_TYPE;
        }
        StringBuilder searchText = new StringBuilder();
        if (!label.isEmpty()) {
            searchText.append(label);
        }
        if (!actionType.isEmpty()) {
            if (searchText.length() > 0) {
                searchText.append(' ');
            }
            searchText.append(actionType);
        }
        return PinyinSearchHelper.matchesNormalized(searchText.toString(), normalizedFilter)
                ? MatchKind.PINYIN
                : MatchKind.NONE;
    }

    public static String getMatchBadge(ActionLibraryNode node, String normalizedFilter) {
        MatchKind kind = getMatchKind(node, normalizedFilter);
        switch (kind) {
            case LABEL:
                return "名称";
            case ACTION_TYPE:
                return "类型";
            case PINYIN:
                return "拼音";
            default:
                return "";
        }
    }

    public static int getMatchColor(MatchKind kind, boolean isGroup) {
        switch (kind) {
            case ACTION_TYPE:
                return isGroup ? 0xFFF2D78F : 0xFFFFE4A8;
            case PINYIN:
                return isGroup ? 0xFFAEE8FF : 0xFFC7F1FF;
            case LABEL:
                return isGroup ? 0xFFE6EEF8 : 0xFFFFFFFF;
            default:
                return isGroup ? 0xFFE6EEF8 : 0xFFFFFFFF;
        }
    }

    private static boolean appendVisibleRows(ActionLibraryNode node, int depth, String filter,
            Set<String> expandedGroupIds, List<ActionLibraryVisibleRow> target) {
        if (node == null) {
            return false;
        }

        boolean hasFilter = filter != null && !filter.isEmpty();
        boolean selfMatches = !hasFilter || getMatchKind(node, filter) != MatchKind.NONE;

        if (!node.isGroup()) {
            if (!selfMatches) {
                return false;
            }
            target.add(new ActionLibraryVisibleRow(node, depth));
            return true;
        }

        List<ActionLibraryVisibleRow> pendingChildren = new ArrayList<ActionLibraryVisibleRow>();
        boolean childVisible = false;
        if (expandedGroupIds != null && expandedGroupIds.contains(node.id)) {
            int beforeSize = target.size();
            for (ActionLibraryNode child : node.children) {
                childVisible |= appendVisibleRows(child, depth + 1, filter, expandedGroupIds, target);
            }
            if (childVisible) {
                pendingChildren.addAll(target.subList(beforeSize, target.size()));
                target.subList(beforeSize, target.size()).clear();
            }
        } else if (!hasFilter) {
            childVisible = node.children != null && !node.children.isEmpty();
        } else {
            for (ActionLibraryNode child : node.children) {
                if (appendVisibleRows(child, depth + 1, filter, expandedGroupIds, target)) {
                    childVisible = true;
                }
            }
            if (childVisible) {
                int childCount = 0;
                for (ActionLibraryVisibleRow row : target) {
                    if (row.depth > depth) {
                        childCount++;
                    }
                }
                if (childCount > 0) {
                    pendingChildren.addAll(target.subList(target.size() - childCount, target.size()));
                    target.subList(target.size() - childCount, target.size()).clear();
                }
            }
        }

        if (!selfMatches && !childVisible) {
            return false;
        }

        target.add(new ActionLibraryVisibleRow(node, depth));
        target.addAll(pendingChildren);
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
