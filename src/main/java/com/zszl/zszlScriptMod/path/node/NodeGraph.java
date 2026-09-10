package com.zszl.zszlScriptMod.path.node;

import java.util.ArrayList;
import java.util.List;

public class NodeGraph {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    private String name;
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<NodeNode> nodes;
    private List<NodeEdge> edges;
    private List<NodeCanvasNote> notes;
    private List<NodeGroupBox> groups;

    public NodeGraph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.notes = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    public NodeGraph(String name, List<NodeNode> nodes, List<NodeEdge> edges) {
        this.name = name;
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.nodes = nodes == null ? new ArrayList<NodeNode>() : nodes;
        this.edges = edges == null ? new ArrayList<NodeEdge>() : edges;
        this.notes = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
    }

    public List<NodeNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeNode> nodes) {
        this.nodes = nodes == null ? new ArrayList<NodeNode>() : nodes;
    }

    public List<NodeEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<NodeEdge> edges) {
        this.edges = edges == null ? new ArrayList<NodeEdge>() : edges;
    }

    public List<NodeCanvasNote> getNotes() {
        return notes;
    }

    public void setNotes(List<NodeCanvasNote> notes) {
        this.notes = notes == null ? new ArrayList<NodeCanvasNote>() : notes;
    }

    public List<NodeGroupBox> getGroups() {
        return groups;
    }

    public void setGroups(List<NodeGroupBox> groups) {
        this.groups = groups == null ? new ArrayList<NodeGroupBox>() : groups;
    }
}