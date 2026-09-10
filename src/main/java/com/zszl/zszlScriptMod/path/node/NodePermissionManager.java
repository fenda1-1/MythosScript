package com.zszl.zszlScriptMod.path.node;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class NodePermissionManager {

    private static final Set<String> HIGH_RISK_NODE_TYPES = new HashSet<String>(Arrays.asList(
            NodeNode.TYPE_ACTION,
            NodeNode.TYPE_RUN_SEQUENCE,
            NodeNode.TYPE_SUBGRAPH,
            NodeNode.TYPE_PARALLEL,
            NodeNode.TYPE_DELAY_TASK,
            NodeNode.TYPE_RESOURCE_LOCK));

    private NodePermissionManager() {
    }

    public static boolean isHighRiskNode(NodeNode node) {
        return node != null && HIGH_RISK_NODE_TYPES.contains(node.getNormalizedType());
    }

    public static boolean graphContainsHighRiskNode(NodeGraph graph) {
        if (graph == null || graph.getNodes() == null) {
            return false;
        }
        for (NodeNode node : graph.getNodes()) {
            if (isHighRiskNode(node)) {
                return true;
            }
        }
        return false;
    }

    public static String buildHighRiskSummary(NodeGraph graph) {
        if (graph == null || graph.getNodes() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NodeNode node : graph.getNodes()) {
            if (!isHighRiskNode(node)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(node.getId()).append("(").append(node.getNormalizedType()).append(")");
        }
        return sb.toString();
    }
}