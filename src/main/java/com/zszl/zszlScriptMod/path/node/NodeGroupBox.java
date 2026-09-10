package com.zszl.zszlScriptMod.path.node;

public class NodeGroupBox {

    private String id;
    private int x;
    private int y;
    private int width;
    private int height;
    private String title;
    private String color;
    private String tag;

    public NodeGroupBox() {
        this.width = 260;
        this.height = 160;
        this.title = "节点分组";
        this.color = "#7ED6DF";
        this.tag = "";
    }

    public NodeGroupBox(String id, int x, int y, int width, int height, String title, String color, String tag) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width <= 0 ? 260 : width;
        this.height = height <= 0 ? 160 : height;
        this.title = title == null ? "节点分组" : title;
        this.color = color == null ? "#7ED6DF" : color;
        this.tag = tag == null ? "" : tag;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public int getWidth() {
        return width <= 0 ? 260 : width;
    }

    public void setWidth(int width) {
        this.width = width <= 0 ? 260 : width;
    }

    public int getHeight() {
        return height <= 0 ? 160 : height;
    }

    public void setHeight(int height) {
        this.height = height <= 0 ? 160 : height;
    }

    public String getTitle() {
        return title == null ? "节点分组" : title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "节点分组" : title;
    }

    public String getColor() {
        return color == null ? "#7ED6DF" : color;
    }

    public void setColor(String color) {
        this.color = color == null ? "#7ED6DF" : color;
    }

    public String getTag() {
        return tag == null ? "" : tag;
    }

    public void setTag(String tag) {
        this.tag = tag == null ? "" : tag;
    }
}