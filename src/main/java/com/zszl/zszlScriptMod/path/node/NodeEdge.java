package com.zszl.zszlScriptMod.path.node;

public class NodeEdge {

    private String id;
    private String fromNodeId;
    private String fromPort;
    private String toNodeId;
    private String toPort;

    public NodeEdge() {
    }

    public NodeEdge(String id, String fromNodeId, String fromPort, String toNodeId, String toPort) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.fromPort = fromPort;
        this.toNodeId = toNodeId;
        this.toPort = toPort;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(String fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public String getFromPort() {
        return fromPort;
    }

    public void setFromPort(String fromPort) {
        this.fromPort = fromPort;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getToPort() {
        return toPort;
    }

    public void setToPort(String toPort) {
        this.toPort = toPort;
    }
}
