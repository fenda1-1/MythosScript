package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/** Modern in-shell editor for the other-features status HUD position. */
public final class ModernHudPositionSettingsTab implements ModernSettingsTab {

    private final Minecraft minecraft;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect canvasBounds;
    private ModernMainLayout.Rect hudBounds;
    private ModernMainLayout.Rect closeBounds;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean returnRequested;

    public ModernHudPositionSettingsTab(Minecraft minecraft) {
        this.minecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requested, int mouseX, int mouseY) {
        panelBounds = requested == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requested;
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.hud_position.title", panelBounds.x + 13,
                panelBounds.y + 10, ModernUiRenderer.TEXT, Math.max(1, panelBounds.width - 26));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.hud_position.subtitle", panelBounds.x + 13,
                panelBounds.y + 27, ModernUiRenderer.MUTED_TEXT, Math.max(1, panelBounds.width - 26));
        ModernUiRenderer.drawDivider(panelBounds.x + 11, panelBounds.y + 44,
                Math.max(1, panelBounds.width - 22), ModernUiRenderer.BORDER_SUBTLE);

        int footerHeight = 34;
        canvasBounds = new ModernMainLayout.Rect(panelBounds.x + 12, panelBounds.y + 55,
                Math.max(1, panelBounds.width - 24), Math.max(1, panelBounds.height - 55 - footerHeight));
        ModernUiRenderer.drawSubtlePanel(canvasBounds.x, canvasBounds.y, canvasBounds.width, canvasBounds.height, 6,
                ModernUiRenderer.INPUT_SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawPreview(fontRenderer, canvasBounds, mouseX, mouseY);

        closeBounds = new ModernMainLayout.Rect(panelBounds.right() - 112, panelBounds.bottom() - 28, 100, 20);
        drawButton(fontRenderer, closeBounds, "gui.modern.hud_position.done", closeBounds.contains(mouseX, mouseY));
    }

    private void drawPreview(FontRenderer fontRenderer, ModernMainLayout.Rect canvas, int mouseX, int mouseY) {
        List<String> lines = statusLines();
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fontRenderer.getStringWidth(line));
        }
        int hudWidth = Math.max(120, maxWidth + 12);
        int hudHeight = Math.max(28, lines.size() * 10 + 10);
        int screenWidth = screenWidth();
        int screenHeight = screenHeight();
        int storedX = clamp(MovementFeatureManager.getMasterStatusHudX(), 0, Math.max(0, screenWidth - hudWidth));
        int storedY = clamp(MovementFeatureManager.getMasterStatusHudY(), 0, Math.max(0, screenHeight - hudHeight));
        int usableWidth = Math.max(1, canvas.width - hudWidth - 8);
        int usableHeight = Math.max(1, canvas.height - hudHeight - 8);
        int previewX = canvas.x + 4 + Math.round(storedX * usableWidth / (float) Math.max(1, screenWidth - hudWidth));
        int previewY = canvas.y + 4 + Math.round(storedY * usableHeight / (float) Math.max(1, screenHeight - hudHeight));
        hudBounds = new ModernMainLayout.Rect(previewX, previewY, hudWidth, hudHeight);

        ModernUiRenderer.drawText(fontRenderer, "gui.modern.hud_position.canvas_hint", canvas.x + 10, canvas.y + 10,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(1, canvas.width - 20));
        ModernUiRenderer.drawSubtlePanel(hudBounds.x, hudBounds.y, hudBounds.width, hudBounds.height, 4,
                0xC80F1720, hudBounds.contains(mouseX, mouseY) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER);
        int y = hudBounds.y + 5;
        for (String line : lines) {
            fontRenderer.drawStringWithShadow(line, hudBounds.x + 6, y, 0xFFFFFF);
            y += 10;
        }
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.hud_position.drag_hint", hudBounds.x + 6,
                hudBounds.bottom() + 6, ModernUiRenderer.MUTED_TEXT,
                Math.max(1, Math.min(canvas.width - 12, hudWidth + 80)));
    }

    private List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.addAll(SpeedHandler.getStatusLines(true));
        lines.addAll(MovementFeatureManager.getStatusLines(true));
        lines.addAll(BlockFeatureManager.getStatusLines(true));
        lines.addAll(WorldFeatureManager.getStatusLines(true));
        lines.addAll(ItemFeatureManager.getStatusLines(true));
        lines.addAll(MiscFeatureManager.getStatusLines(true));
        if (lines.isEmpty()) {
            lines.add("§a[总状态HUD] §f位置预览");
            lines.add("§7当前没有可显示的状态行");
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelBounds == null) return false;
        if (mouseButton != 0) return true;
        if (closeBounds != null && closeBounds.contains(mouseX, mouseY)) {
            returnRequested = true;
            return true;
        }
        if (hudBounds != null && hudBounds.contains(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - hudBounds.x;
            dragOffsetY = mouseY - hudBounds.y;
            return true;
        }
        return panelBounds.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton != 0 || !dragging) return false;
        updatePosition(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state != 0 || !dragging) return false;
        updatePosition(mouseX, mouseY);
        dragging = false;
        MovementFeatureManager.persistMasterStatusHudPosition();
        return true;
    }

    private void updatePosition(int mouseX, int mouseY) {
        if (canvasBounds == null || hudBounds == null) return;
        int screenWidth = screenWidth();
        int screenHeight = screenHeight();
        int usableWidth = Math.max(1, canvasBounds.width - hudBounds.width - 8);
        int usableHeight = Math.max(1, canvasBounds.height - hudBounds.height - 8);
        int canvasX = clamp(mouseX - dragOffsetX - canvasBounds.x - 4, 0, usableWidth);
        int canvasY = clamp(mouseY - dragOffsetY - canvasBounds.y - 4, 0, usableHeight);
        int nextX = Math.round(canvasX * Math.max(0, screenWidth - hudBounds.width) / (float) usableWidth);
        int nextY = Math.round(canvasY * Math.max(0, screenHeight - hudBounds.height) / (float) usableHeight);
        MovementFeatureManager.setMasterStatusHudPositionTransient(nextX, nextY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            returnRequested = true;
            dragging = false;
            MovementFeatureManager.persistMasterStatusHudPosition();
            return true;
        }
        return true;
    }

    @Override
    public boolean handleEscape() {
        return keyTyped('\0', Keyboard.KEY_ESCAPE);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean consumeReturnRequest() {
        boolean result = returnRequested;
        returnRequested = false;
        return result;
    }

    @Override public boolean isTextInputFocused() { return false; }
    @Override public boolean containsContent(int mouseX, int mouseY) { return panelBounds != null && panelBounds.contains(mouseX, mouseY); }
    @Override public String getHoveredTooltip(int mouseX, int mouseY) { return ""; }
    @Override public void discardDraft() { dragging = false; returnRequested = false; }
    @Override public boolean isDirty() { return false; }

    private void drawButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean hovered) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + Math.max(3, (bounds.height - fontRenderer.FONT_HEIGHT) / 2),
                ModernUiRenderer.TEXT, Math.max(1, bounds.width - 12));
    }

    private int screenWidth() {
        return minecraft == null ? 1 : new ScaledResolution(minecraft).getScaledWidth();
    }

    private int screenHeight() {
        return minecraft == null ? 1 : new ScaledResolution(minecraft).getScaledHeight();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
