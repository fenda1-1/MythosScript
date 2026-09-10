package com.zszl.zszlScriptMod.gui.modern.core;

import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import net.minecraft.client.Minecraft;

/** Immutable context passed to a modern tab when it is created. */
public final class ModernScreenContext {

    private final Minecraft minecraft;
    private final int screenWidth;
    private final int screenHeight;
    private final ModernMainLayout.Rect contentBounds;
    private final String command;
    private final String statusMessage;
    private final Consumer<String> routeRequest;

    public ModernScreenContext(Minecraft minecraft, int screenWidth, int screenHeight,
            ModernMainLayout.Rect contentBounds, String command, String statusMessage) {
        this(minecraft, screenWidth, screenHeight, contentBounds, command, statusMessage, null);
    }

    public ModernScreenContext(Minecraft minecraft, int screenWidth, int screenHeight,
            ModernMainLayout.Rect contentBounds, String command, String statusMessage,
            Consumer<String> routeRequest) {
        this.minecraft = minecraft;
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        this.contentBounds = contentBounds == null
                ? new ModernMainLayout.Rect(0, 0, this.screenWidth, this.screenHeight)
                : contentBounds;
        this.command = command == null ? "" : command;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
        this.routeRequest = routeRequest;
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public ModernMainLayout.Rect getContentBounds() {
        return contentBounds;
    }

    public String getCommand() {
        return command;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Consumer<String> getRouteRequest() {
        return routeRequest;
    }
}
