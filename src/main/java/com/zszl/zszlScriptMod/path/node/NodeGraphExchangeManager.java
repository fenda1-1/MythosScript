package com.zszl.zszlScriptMod.path.node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NodeGraphExchangeManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NodeGraphExchangeManager() {
    }

    public static void exportGraph(NodeGraph graph, Path targetFile) throws IOException {
        if (graph == null) {
            throw new IOException("导出失败：图为空");
        }
        if (targetFile == null) {
            throw new IOException("导出失败：目标文件为空");
        }

        Files.createDirectories(targetFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            GSON.toJson(graph, writer);
        }

        NodeAuditLogManager.log("export", graph.getName(), "导出到 " + targetFile.toAbsolutePath());
    }

    public static NodeGraph importGraph(Path sourceFile) throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("导入失败：文件不存在");
        }

        String json = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
        NodeGraph graph = GSON.fromJson(new JsonParser().parse(json), NodeGraph.class);
        if (graph == null) {
            throw new IOException("导入失败：文件内容无效");
        }
        if (graph.getSchemaVersion() <= 0) {
            graph.setSchemaVersion(NodeGraph.CURRENT_SCHEMA_VERSION);
        }
        if (graph.getNodes() == null) {
            graph.setNodes(new ArrayList<NodeNode>());
        }
        if (graph.getEdges() == null) {
            graph.setEdges(new ArrayList<NodeEdge>());
        }

        NodeAuditLogManager.log("import", graph.getName(), "从 " + sourceFile.toAbsolutePath() + " 导入");
        return graph;
    }

    public static List<String> diffGraphs(NodeGraph oldGraph, NodeGraph newGraph) {
        List<String> diffs = new ArrayList<>();
        if (oldGraph == null && newGraph == null) {
            return diffs;
        }
        if (oldGraph == null) {
            diffs.add("新增整张图: " + safeName(newGraph));
            return diffs;
        }
        if (newGraph == null) {
            diffs.add("删除整张图: " + safeName(oldGraph));
            return diffs;
        }

        if (!safe(oldGraph.getName()).equals(safe(newGraph.getName()))) {
            diffs.add("图名称变化: " + safe(oldGraph.getName()) + " -> " + safe(newGraph.getName()));
        }
        if (oldGraph.getSchemaVersion() != newGraph.getSchemaVersion()) {
            diffs.add("schemaVersion 变化: " + oldGraph.getSchemaVersion() + " -> " + newGraph.getSchemaVersion());
        }

        Map<String, NodeNode> oldNodes = indexNodes(oldGraph);
        Map<String, NodeNode> newNodes = indexNodes(newGraph);
        Map<String, NodeEdge> oldEdges = indexEdges(oldGraph);
        Map<String, NodeEdge> newEdges = indexEdges(newGraph);

        for (String nodeId : oldNodes.keySet()) {
            if (!newNodes.containsKey(nodeId)) {
                diffs.add("删除节点: " + nodeId);
            }
        }
        for (String nodeId : newNodes.keySet()) {
            if (!oldNodes.containsKey(nodeId)) {
                diffs.add("新增节点: " + nodeId);
                continue;
            }
            compareNode(oldNodes.get(nodeId), newNodes.get(nodeId), diffs);
        }

        for (String edgeId : oldEdges.keySet()) {
            if (!newEdges.containsKey(edgeId)) {
                diffs.add("删除边: " + edgeId);
            }
        }
        for (String edgeId : newEdges.keySet()) {
            if (!oldEdges.containsKey(edgeId)) {
                diffs.add("新增边: " + edgeId);
                continue;
            }
            compareEdge(oldEdges.get(edgeId), newEdges.get(edgeId), diffs);
        }

        return diffs;
    }

    private static Map<String, NodeNode> indexNodes(NodeGraph graph) {
        Map<String, NodeNode> result = new LinkedHashMap<>();
        if (graph == null || graph.getNodes() == null) {
            return result;
        }
        for (NodeNode node : graph.getNodes()) {
            if (node != null && node.getId() != null) {
                result.put(node.getId(), node);
            }
        }
        return result;
    }

    private static Map<String, NodeEdge> indexEdges(NodeGraph graph) {
        Map<String, NodeEdge> result = new LinkedHashMap<>();
        if (graph == null || graph.getEdges() == null) {
            return result;
        }
        for (NodeEdge edge : graph.getEdges()) {
            if (edge != null && edge.getId() != null) {
                result.put(edge.getId(), edge);
            }
        }
        return result;
    }

    private static void compareNode(NodeNode oldNode, NodeNode newNode, List<String> diffs) {
        if (!safe(oldNode.getType()).equals(safe(newNode.getType()))) {
            diffs.add("节点类型变化: " + oldNode.getId() + " " + safe(oldNode.getType()) + " -> " + safe(newNode.getType()));
        }
        if (oldNode.getX() != newNode.getX() || oldNode.getY() != newNode.getY()) {
            diffs.add("节点位置变化: " + oldNode.getId() + " (" + oldNode.getX() + "," + oldNode.getY() + ") -> ("
                    + newNode.getX() + "," + newNode.getY() + ")");
        }

        String oldData = oldNode.getData() == null ? "{}" : oldNode.getData().toString();
        String newData = newNode.getData() == null ? "{}" : newNode.getData().toString();
        if (!oldData.equals(newData)) {
            diffs.add("节点参数变化: " + oldNode.getId());
        }
    }

    private static void compareEdge(NodeEdge oldEdge, NodeEdge newEdge, List<String> diffs) {
        if (!safe(oldEdge.getFromNodeId()).equals(safe(newEdge.getFromNodeId()))
                || !safe(oldEdge.getFromPort()).equals(safe(newEdge.getFromPort()))
                || !safe(oldEdge.getToNodeId()).equals(safe(newEdge.getToNodeId()))
                || !safe(oldEdge.getToPort()).equals(safe(newEdge.getToPort()))) {
            diffs.add("边连接变化: " + safe(oldEdge.getId()));
        }
    }

    private static String safeName(NodeGraph graph) {
        return graph == null ? "" : safe(graph.getName());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
