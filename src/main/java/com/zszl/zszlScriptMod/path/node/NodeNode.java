package com.zszl.zszlScriptMod.path.node;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NodeNode {

    public static final String PORT_IN = "in";
    public static final String PORT_NEXT = "next";
    public static final String PORT_TRUE = "true";
    public static final String PORT_FALSE = "false";
    public static final String PORT_TIMEOUT = "timeout";

    public static final String TYPE_START = "start";
    public static final String TYPE_ACTION = "action";
    public static final String TYPE_IF = "if";
    public static final String TYPE_SET_VAR = "setvar";
    public static final String TYPE_WAIT_UNTIL = "waituntil";
    public static final String TYPE_END = "end";

    public static final String TYPE_TRIGGER = "trigger";
    public static final String TYPE_RUN_SEQUENCE = "runsequence";
    public static final String TYPE_SUBGRAPH = "subgraph";
    public static final String TYPE_PARALLEL = "parallel";
    public static final String TYPE_JOIN = "join";
    public static final String TYPE_DELAY_TASK = "delaytask";
    public static final String TYPE_RESOURCE_LOCK = "resourcelock";
    public static final String TYPE_RESULT_CACHE = "resultcache";

    private String id;
    private String type;
    private int x;
    private int y;
    private JsonObject data;
    private String colorTag;
    private boolean collapsed;
    private boolean minimized;

    public NodeNode() {
        this.data = new JsonObject();
        this.colorTag = "";
        this.collapsed = false;
        this.minimized = false;
    }

    public NodeNode(String id, String type, int x, int y, JsonObject data) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.data = data == null ? new JsonObject() : data;
        this.colorTag = "";
        this.collapsed = false;
        this.minimized = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNormalizedType() {
        return normalizeType(type);
    }

    public static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePort(String port) {
        return port == null ? "" : port.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> getInputPorts(String type) {
        String normalized = normalizeType(type);
        if (TYPE_START.equals(normalized) || TYPE_TRIGGER.equals(normalized)) {
            return Collections.emptyList();
        }
        if (TYPE_END.equals(normalized)
                || TYPE_ACTION.equals(normalized)
                || TYPE_IF.equals(normalized)
                || TYPE_SET_VAR.equals(normalized)
                || TYPE_WAIT_UNTIL.equals(normalized)
                || TYPE_RUN_SEQUENCE.equals(normalized)
                || TYPE_SUBGRAPH.equals(normalized)
                || TYPE_PARALLEL.equals(normalized)
                || TYPE_JOIN.equals(normalized)
                || TYPE_DELAY_TASK.equals(normalized)
                || TYPE_RESOURCE_LOCK.equals(normalized)
                || TYPE_RESULT_CACHE.equals(normalized)) {
            return Collections.singletonList(PORT_IN);
        }
        return Collections.emptyList();
    }

    public static List<String> getOutputPorts(String type) {
        String normalized = normalizeType(type);
        if (TYPE_START.equals(normalized)
                || TYPE_ACTION.equals(normalized)
                || TYPE_SET_VAR.equals(normalized)
                || TYPE_TRIGGER.equals(normalized)
                || TYPE_PARALLEL.equals(normalized)
                || TYPE_JOIN.equals(normalized)) {
            return Collections.singletonList(PORT_NEXT);
        }
        if (TYPE_IF.equals(normalized)) {
            List<String> ports = new ArrayList<String>();
            ports.add(PORT_TRUE);
            ports.add(PORT_FALSE);
            return Collections.unmodifiableList(ports);
        }
        if (TYPE_RUN_SEQUENCE.equals(normalized) || TYPE_SUBGRAPH.equals(normalized)) {
            List<String> ports = new ArrayList<String>();
            ports.add(PORT_NEXT);
            ports.add(PORT_TRUE);
            ports.add(PORT_FALSE);
            return Collections.unmodifiableList(ports);
        }
        if (TYPE_RESOURCE_LOCK.equals(normalized)) {
            List<String> ports = new ArrayList<String>();
            ports.add(PORT_NEXT);
            ports.add(PORT_FALSE);
            return Collections.unmodifiableList(ports);
        }
        if (TYPE_RESULT_CACHE.equals(normalized)) {
            List<String> ports = new ArrayList<String>();
            ports.add(PORT_NEXT);
            ports.add(PORT_TRUE);
            ports.add(PORT_FALSE);
            return Collections.unmodifiableList(ports);
        }
        if (TYPE_WAIT_UNTIL.equals(normalized) || TYPE_DELAY_TASK.equals(normalized)) {
            List<String> ports = new ArrayList<String>();
            ports.add(PORT_NEXT);
            ports.add(PORT_TIMEOUT);
            return Collections.unmodifiableList(ports);
        }
        return Collections.emptyList();
    }

    public static String getDisplayName(String type) {
        String normalized = normalizeType(type);
        if (TYPE_START.equals(normalized)) {
            return "\u5f00\u59cb";
        }
        if (TYPE_ACTION.equals(normalized)) {
            return "\u52a8\u4f5c";
        }
        if (TYPE_IF.equals(normalized)) {
            return "\u6761\u4ef6";
        }
        if (TYPE_SET_VAR.equals(normalized)) {
            return "\u53d8\u91cf";
        }
        if (TYPE_WAIT_UNTIL.equals(normalized)) {
            return "\u7b49\u5f85";
        }
        if (TYPE_END.equals(normalized)) {
            return "\u7ed3\u675f";
        }
        if (TYPE_TRIGGER.equals(normalized)) {
            return "\u89e6\u53d1\u5668";
        }
        if (TYPE_RUN_SEQUENCE.equals(normalized)) {
            return "\u8fd0\u884c\u5e8f\u5217";
        }
        if (TYPE_SUBGRAPH.equals(normalized)) {
            return "\u5b50\u56fe";
        }
        if (TYPE_PARALLEL.equals(normalized)) {
            return "\u5e76\u884c";
        }
        if (TYPE_JOIN.equals(normalized)) {
            return "\u6c47\u805a";
        }
        if (TYPE_DELAY_TASK.equals(normalized)) {
            return "\u5ef6\u8fdf\u4efb\u52a1";
        }
        if (TYPE_RESOURCE_LOCK.equals(normalized)) {
            return "\u8d44\u6e90\u9501";
        }
        if (TYPE_RESULT_CACHE.equals(normalized)) {
            return "\u7ed3\u679c\u7f13\u5b58";
        }
        return normalized;
    }

    public static String getPortDisplayName(String port) {
        String normalized = normalizePort(port);
        if (PORT_IN.equals(normalized)) {
            return "\u8f93\u5165";
        }
        if (PORT_NEXT.equals(normalized)) {
            return "\u4e0b\u4e00\u6b65";
        }
        if (PORT_TRUE.equals(normalized)) {
            return "\u662f";
        }
        if (PORT_FALSE.equals(normalized)) {
            return "\u5426";
        }
        if (PORT_TIMEOUT.equals(normalized)) {
            return "\u8d85\u65f6";
        }
        return normalized;
    }

    public static String getHelpText(String type) {
        String normalized = normalizeType(type);
        if (TYPE_START.equals(normalized)) {
            return "\u6d41\u7a0b\u5165\u53e3\u8282\u70b9\u3002\u8fd0\u884c\u65f6\u4f1a\u4ece\u8fd9\u91cc\u5f00\u59cb\uff0c\u901a\u5e38\u53ea\u4fdd\u7559\u4e00\u4e2a\u5f00\u59cb\u8282\u70b9\u3002";
        }
        if (TYPE_ACTION.equals(normalized)) {
            return "\u6267\u884c\u4e00\u4e2a\u5177\u4f53\u52a8\u4f5c\uff0c\u4f8b\u5982\u547d\u4ee4\u3001\u70b9\u51fb\u6216\u8def\u5f84\u884c\u4e3a\u3002\u5b8c\u6210\u540e\u8fdb\u5165\u201c\u4e0b\u4e00\u6b65\u201d\u3002";
        }
        if (TYPE_IF.equals(normalized)) {
            return "\u6839\u636e\u6761\u4ef6\u5224\u65ad\u8d70\u5411\u4e0d\u540c\u5206\u652f\u3002\u6761\u4ef6\u6210\u7acb\u8d70\u201c\u662f\u201d\uff0c\u5426\u5219\u8d70\u201c\u5426\u201d\u3002";
        }
        if (TYPE_SET_VAR.equals(normalized)) {
            return "\u5199\u5165\u6216\u66f4\u65b0\u53d8\u91cf\uff0c\u4f9b\u540e\u7eed\u8282\u70b9\u8bfb\u53d6\u3002";
        }
        if (TYPE_WAIT_UNTIL.equals(normalized)) {
            return "\u6301\u7eed\u7b49\u5f85\u76f4\u5230\u6761\u4ef6\u6ee1\u8db3\uff1b\u8d85\u8fc7\u8d85\u65f6\u65f6\u95f4\u65f6\u8d70\u201c\u8d85\u65f6\u201d\u5206\u652f\u3002";
        }
        if (TYPE_END.equals(normalized)) {
            return "\u6d41\u7a0b\u7ed3\u675f\u8282\u70b9\u3002\u5230\u8fbe\u8fd9\u91cc\u540e\u5f53\u524d\u56fe\u505c\u6b62\u6267\u884c\u3002";
        }
        if (TYPE_TRIGGER.equals(normalized)) {
            return "\u4e8b\u4ef6\u89e6\u53d1\u5165\u53e3\u3002\u6ee1\u8db3\u89e6\u53d1\u6761\u4ef6\u540e\u4ece\u8fd9\u91cc\u8fdb\u5165\u540e\u7eed\u6d41\u7a0b\u3002";
        }
        if (TYPE_RUN_SEQUENCE.equals(normalized)) {
            return "\u8fd0\u884c\u53e6\u4e00\u4e2a\u8282\u70b9\u56fe\u3002\u53ef\u6839\u636e\u6267\u884c\u7ed3\u679c\u8d70\u4e0d\u540c\u5206\u652f\u3002";
        }
        if (TYPE_SUBGRAPH.equals(normalized)) {
            return "\u8c03\u7528\u4e00\u4e2a\u5b50\u56fe\uff0c\u9002\u5408\u590d\u7528\u516c\u5171\u6d41\u7a0b\u3002";
        }
        if (TYPE_PARALLEL.equals(normalized)) {
            return "\u540c\u65f6\u542f\u52a8\u591a\u4e2a\u5b50\u6d41\u7a0b\uff0c\u9002\u5408\u5e76\u884c\u6267\u884c\u4efb\u52a1\u3002";
        }
        if (TYPE_JOIN.equals(normalized)) {
            return "\u7b49\u5f85\u591a\u4e2a\u5e76\u884c\u5206\u652f\u6c47\u805a\u540e\u518d\u7ee7\u7eed\u6267\u884c\u3002";
        }
        if (TYPE_DELAY_TASK.equals(normalized)) {
            return "\u5ef6\u8fdf\u4e00\u6bb5\u65f6\u95f4\u540e\u7ee7\u7eed\uff1b\u82e5\u7b49\u5f85\u8fc7\u4e45\u53ef\u8d70\u201c\u8d85\u65f6\u201d\u5206\u652f\u3002";
        }
        if (TYPE_RESOURCE_LOCK.equals(normalized)) {
            return "\u5c1d\u8bd5\u5360\u7528\u4e00\u4e2a\u8d44\u6e90\u9501\uff1b\u6210\u529f\u7ee7\u7eed\uff0c\u5931\u8d25\u8d70\u201c\u5426\u201d\u5206\u652f\u3002";
        }
        if (TYPE_RESULT_CACHE.equals(normalized)) {
            return "\u6309\u7f13\u5b58\u952e\u8bfb\u53d6\u6216\u5199\u5165\u7ed3\u679c\uff1b\u547d\u4e2d\u7f13\u5b58\u65f6\u8d70\u201c\u662f\u201d\u5206\u652f\u3002";
        }
        return "\u8be5\u8282\u70b9\u6682\u65e0\u5e2e\u52a9\u8bf4\u660e\u3002";
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data == null ? new JsonObject() : data;
    }

    public String getColorTag() {
        return colorTag == null ? "" : colorTag;
    }

    public void setColorTag(String colorTag) {
        this.colorTag = colorTag == null ? "" : colorTag;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        if (collapsed) {
            this.minimized = false;
        }
    }

    public boolean isMinimized() {
        return minimized;
    }

    public void setMinimized(boolean minimized) {
        this.minimized = minimized;
        if (minimized) {
            this.collapsed = false;
        }
    }
}