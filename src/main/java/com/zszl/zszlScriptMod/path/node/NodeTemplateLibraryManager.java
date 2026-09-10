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

public final class NodeTemplateLibraryManager {

    private static final String FILE_NAME = "node_templates.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NodeTemplateLibraryManager() {
    }

    public static Path getTemplateFile() {
        return ProfileManager.getCurrentProfileDir().resolve(FILE_NAME);
    }

    public static synchronized List<NodeGraph> loadTemplates() {
        Path file = getTemplateFile();
        if (!Files.exists(file)) {
            return new ArrayList<NodeGraph>();
        }
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            TemplateRoot root = GSON.fromJson(json, TemplateRoot.class);
            if (root == null || root.templates == null) {
                return new ArrayList<NodeGraph>();
            }
            return new ArrayList<NodeGraph>(root.templates);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("读取节点模板库失败: {}", file, e);
            return new ArrayList<NodeGraph>();
        }
    }

    public static synchronized void saveTemplates(List<NodeGraph> templates) throws IOException {
        Path file = getTemplateFile();
        Files.createDirectories(file.getParent());
        TemplateRoot root = new TemplateRoot();
        root.templates = templates == null ? new ArrayList<NodeGraph>() : new ArrayList<NodeGraph>(templates);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public static synchronized void addTemplate(NodeGraph graph) throws IOException {
        if (graph == null) {
            return;
        }
        List<NodeGraph> templates = loadTemplates();
        templates.add(cloneGraph(graph));
        saveTemplates(templates);
        NodeAuditLogManager.log("template_add", graph.getName(), "保存到模板库");
    }

    public static synchronized NodeGraph importTemplate(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        for (NodeGraph graph : loadTemplates()) {
            if (graph != null && name.equalsIgnoreCase(safe(graph.getName()))) {
                return cloneGraph(graph);
            }
        }
        return null;
    }

    private static NodeGraph cloneGraph(NodeGraph graph) {
        String json = GSON.toJson(graph);
        NodeGraph cloned = GSON.fromJson(json, NodeGraph.class);
        if (cloned == null) {
            cloned = new NodeGraph();
            cloned.setName(graph == null ? "" : graph.getName());
        }
        if (cloned.getSchemaVersion() <= 0) {
            cloned.setSchemaVersion(NodeGraph.CURRENT_SCHEMA_VERSION);
        }
        return cloned;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class TemplateRoot {
        private List<NodeGraph> templates = new ArrayList<NodeGraph>();
    }
}
