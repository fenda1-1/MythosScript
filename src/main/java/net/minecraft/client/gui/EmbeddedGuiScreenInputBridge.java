package net.minecraft.client.gui;

import java.io.IOException;
import java.util.Locale;

import net.minecraft.util.text.TextFormatting;

/**
 * Access bridge for embedded screens. This class remains in GuiScreen's
 * package in the production JAR, so Java's package-level protected access
 * remains valid after ProGuard processing.
 */
public final class EmbeddedGuiScreenInputBridge {

    private EmbeddedGuiScreenInputBridge() {
    }

    public static void mouseClicked(GuiScreen screen, int mouseX, int mouseY, int mouseButton) throws IOException {
        screen.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public static void mouseClickMove(GuiScreen screen, int mouseX, int mouseY, int mouseButton,
            long timeSinceLastClick) {
        screen.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceLastClick);
    }

    public static void mouseReleased(GuiScreen screen, int mouseX, int mouseY, int state) {
        screen.mouseReleased(mouseX, mouseY, state);
    }

    public static void keyTyped(GuiScreen screen, char typedChar, int keyCode) throws IOException {
        screen.keyTyped(typedChar, keyCode);
    }

    public static void handleMouseInput(GuiScreen screen) throws IOException {
        screen.handleMouseInput();
    }

    /** Invokes the most likely visible save action on a legacy screen. */
    public static boolean invokeSaveButton(GuiScreen screen) throws IOException {
        if (screen == null || screen.buttonList == null) {
            return false;
        }
        GuiButton bestButton = null;
        int bestScore = -1;
        for (GuiButton button : screen.buttonList) {
            if (button == null || !button.visible || !button.enabled) {
                continue;
            }
            int score = saveButtonScore(button.displayString);
            if (score > bestScore) {
                bestButton = button;
                bestScore = score;
            }
        }
        if (bestButton == null) {
            return false;
        }
        screen.actionPerformed(bestButton);
        return true;
    }

    private static int saveButtonScore(String displayString) {
        String normalized = TextFormatting.getTextWithoutFormattingCodes(displayString == null ? "" : displayString);
        String label = normalized == null ? "" : normalized.trim().toLowerCase(Locale.ROOT);
        if (label.isEmpty() || label.contains("快照") || label.contains("snapshot")) {
            return -1;
        }
        if (!label.contains("保存") && !label.contains("save")) {
            return -1;
        }
        if ("保存".equals(label) || "save".equals(label)) {
            return 1000;
        }
        if (label.startsWith("保存并关闭") || label.startsWith("save and close")) {
            return 950;
        }
        if (label.contains("忽略并保存") || label.contains("save anyway")) {
            return 940;
        }
        if (label.contains("保存当前") || label.contains("save current")) {
            return 930;
        }
        if (label.contains("保存配置") || label.contains("save config")) {
            return 920;
        }
        return label.startsWith("保存") || label.startsWith("save") ? 800 : 700;
    }

}
