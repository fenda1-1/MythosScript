package com.zszl.zszlScriptMod.path.node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NodeEditorDraftManager {

    private static final String FILE_NAME = "node_editor_drafts.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NodeEditorDraftManager() {
    }

    public static Path getDraftFile() {
        return ProfileManager.getCurrentProfileDir().resolve(FILE_NAME);
    }

    public static synchronized List<NodeGraph> loadDrafts() {
        Path file = getDraftFile();
        if (!Files.exists(file)) {
            return new ArrayList<NodeGraph>();
        }
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            DraftRoot root = GSON.fromJson(json, DraftRoot.class);
            if (root == null || root.graphs == null) {
                return new ArrayList<NodeGraph>();
            }
            return new ArrayList<NodeGraph>(root.graphs);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("读取节点编辑器草稿失败: {}", file, e);
            return new ArrayList<NodeGraph>();
        }
    }

    public static synchronized void saveDrafts(List<NodeGraph> graphs) throws IOException {
        Path file = getDraftFile();
        Files.createDirectories(file.getParent());
        DraftRoot root = new DraftRoot();
        root.graphs = graphs == null ? new ArrayList<NodeGraph>() : new ArrayList<NodeGraph>(graphs);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public static synchronized void saveDraft(NodeGraph graph) {
        if (graph == null) {
            return;
        }
        try {
            List<NodeGraph> drafts = loadDrafts();
            boolean replaced = false;
            for (int i = 0; i < drafts.size(); i++) {
                NodeGraph item = drafts.get(i);
                if (item != null && safe(item.getName()).equalsIgnoreCase(safe(graph.getName()))) {
                    drafts.set(i, cloneGraph(graph));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                drafts.add(cloneGraph(graph));
            }
            saveDrafts(drafts);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存节点编辑器草稿失败: {}", safe(graph.getName()), e);
        }
    }

    public static synchronized NodeGraph loadDraft(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        for (NodeGraph graph : loadDrafts()) {
            if (graph != null && safe(graph.getName()).equalsIgnoreCase(name.trim())) {
                return cloneGraph(graph);
            }
        }
        return null;
    }

    public static synchronized void removeDraft(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        try {
            List<NodeGraph> drafts = loadDrafts();
            drafts.removeIf(graph -> graph != null && safe(graph.getName()).equalsIgnoreCase(name.trim()));
            saveDrafts(drafts);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("删除节点编辑器草稿失败: {}", name, e);
        }
    }

    private static NodeGraph cloneGraph(NodeGraph graph) {
        String json = GSON.toJson(graph);
        NodeGraph cloned = GSON.fromJson(json, NodeGraph.class);
        if (cloned == null) {
            cloned = new NodeGraph();
            cloned.setName(graph == null ? "" : graph.getName());
        }
        if (cloned.getNodes() == null) {
            cloned.setNodes(new ArrayList<NodeNode>());
        }
        if (cloned.getEdges() == null) {
            cloned.setEdges(new ArrayList<NodeEdge>());
        }
        if (cloned.getSchemaVersion() <= 0) {
            cloned.setSchemaVersion(NodeGraph.CURRENT_SCHEMA_VERSION);
        }
        return cloned;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class DraftRoot {
        private List<NodeGraph> graphs = new ArrayList<NodeGraph>();
    }
}