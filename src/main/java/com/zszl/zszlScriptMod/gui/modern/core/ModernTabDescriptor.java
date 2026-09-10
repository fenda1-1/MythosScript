package com.zszl.zszlScriptMod.gui.modern.core;

/** Immutable command-to-tab registration entry. */
public final class ModernTabDescriptor {

    private final String command;
    private final String title;
    private final ModernTabFactory factory;
    private final String parentCommand;

    public ModernTabDescriptor(String command, String title, ModernTabFactory factory) {
        this(command, title, factory, null);
    }

    public ModernTabDescriptor(String command, String title, ModernTabFactory factory, String parentCommand) {
        this.command = requireText(command, "command");
        this.title = requireText(title, "title");
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        this.factory = factory;
        this.parentCommand = parentCommand == null || parentCommand.trim().isEmpty()
                ? null : parentCommand.trim();
    }

    public String getCommand() {
        return command;
    }

    public String getTitle() {
        return title;
    }

    public ModernTabFactory getFactory() {
        return factory;
    }

    /** Returns the command of the tab this tab depends on, if any. */
    public String getParentCommand() {
        return parentCommand;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
