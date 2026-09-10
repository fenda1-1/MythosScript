package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model;

public class ExpressionTemplateCard {
    public final String name;
    public final String example;
    public final String description;
    public final String format;
    public final String outputExample;
    public final String[] keywords;

    public ExpressionTemplateCard(String name, String example, String description, String format,
            String outputExample, String... keywords) {
        this.name = name == null ? "" : name;
        this.example = example == null ? "" : example;
        this.description = description == null ? "" : description;
        this.format = format == null ? "" : format;
        this.outputExample = outputExample == null ? "" : outputExample;
        this.keywords = keywords == null ? new String[0] : keywords;
    }
}
