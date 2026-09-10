package com.zszl.zszlScriptMod.path.node;

public class NodeCanvasNote {

    private String id;
    private int x;
    private int y;
    private int width;
    private int height;
    private String title;
    private String content;
    private String color;

    public NodeCanvasNote() {
        this.width = 180;
        this.height = 90;
        this.title = "注释";
        this.content = "";
        this.color = "#F6E58D";
    }

    public NodeCanvasNote(String id, int x, int y, int width, int height, String title, String content, String color) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width <= 0 ? 180 : width;
        this.height = height <= 0 ? 90 : height;
        this.title = title == null ? "注释" : title;
        this.content = content == null ? "" : content;
        this.color = color == null ? "#F6E58D" : color;
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
        return width <= 0 ? 180 : width;
    }

    public void setWidth(int width) {
        this.width = width <= 0 ? 180 : width;
    }

    public int getHeight() {
        return height <= 0 ? 90 : height;
    }

    public void setHeight(int height) {
        this.height = height <= 0 ? 90 : height;
    }

    public String getTitle() {
        return title == null ? "注释" : title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "注释" : title;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public String getColor() {
        return color == null ? "#F6E58D" : color;
    }

    public void setColor(String color) {
        this.color = color == null ? "#F6E58D" : color;
    }
}