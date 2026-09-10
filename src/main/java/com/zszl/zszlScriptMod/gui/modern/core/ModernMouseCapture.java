package com.zszl.zszlScriptMod.gui.modern.core;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;

/** Stores the physical mouse position captured immediately before opening the modern menu. */
public final class ModernMouseCapture {

    private static Capture lastBeforeMenu;

    private ModernMouseCapture() {
    }

    public static void captureBeforeMenu(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        int width = Math.max(1, minecraft.displayWidth);
        int height = Math.max(1, minecraft.displayHeight);
        try {
            int x = Math.max(0, Math.min(width - 1, Mouse.getX()));
            int y = Math.max(0, Math.min(height - 1, height - Mouse.getY() - 1));
            lastBeforeMenu = new Capture(x, y, width, height);
        } catch (Exception ignored) {
        }
    }

    public static Capture getLastBeforeMenu() {
        return lastBeforeMenu;
    }

    public static final class Capture {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Capture(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
