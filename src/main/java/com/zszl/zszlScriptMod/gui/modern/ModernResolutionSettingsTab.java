package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Read-only display of the current Minecraft window and GUI scale.
 */
public final class ModernResolutionSettingsTab implements ModernSettingsTab {

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect refreshBounds;
    private ModernMainLayout.Rect widthInfoBounds;
    private ModernMainLayout.Rect heightInfoBounds;
    private ModernMainLayout.Rect scaleInfoBounds;
    private String hoveredInfoTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private int displayWidth;
    private int displayHeight;
    private int guiScale;
    private boolean initialized;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!initialized) {
            refreshValues();
            initialized = true;
        }
    }

    @Override
    public void updateScreen() {
        // Values are intentionally stable until the user explicitly refreshes.
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        hoveredInfoTooltip = "";
        contentBounds = requestedContentBounds;

        ModernMainLayout.Rect panel = createPanel(requestedContentBounds);
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 7, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);

        int innerX = panel.x + 16;
        int innerWidth = Math.max(1, panel.width - 32);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.res.u001", innerX, panel.y + 15, ModernUiRenderer.TEXT,
                Math.max(40, innerWidth - 20));
        drawInfoIcon(fontRenderer, innerX + Math.min(Math.max(0, innerWidth - 13),
                fontRenderer.getStringWidth(ModernFormI18n.tr("gui.modern.res.u001")) + 7), panel.y + 14,
                "gui.modern.res.u002", mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.res.u003", innerX, panel.y + 29,
                ModernUiRenderer.MUTED_TEXT, innerWidth);
        ModernUiRenderer.drawDivider(innerX, panel.y + 48, innerWidth, ModernUiRenderer.BORDER_SUBTLE);

        boolean compact = panel.height < 190;
        int rowY = panel.y + (compact ? 50 : 56);
        int rowStep = compact ? 20 : 32;
        int rowHeight = compact ? 18 : 28;
        widthInfoBounds = drawValueRow(fontRenderer, panel, rowY, rowHeight, "gui.modern.res.u004", displayWidth + " px",
                "gui.modern.res.u005", mouseX, mouseY);
        rowY += rowStep;
        heightInfoBounds = drawValueRow(fontRenderer, panel, rowY, rowHeight, "gui.modern.res.u006", displayHeight + " px",
                "gui.modern.res.u007", mouseX, mouseY);
        rowY += rowStep;
        scaleInfoBounds = drawValueRow(fontRenderer, panel, rowY, rowHeight, "gui.modern.res.u008", String.valueOf(guiScale),
                "gui.modern.res.u009", mouseX, mouseY);

        refreshBounds = new ModernMainLayout.Rect(innerX, panel.bottom() - (compact ? 24 : 32), innerWidth,
                compact ? 20 : 24);
        drawActionButton(fontRenderer, refreshBounds, "gui.modern.res.u010", true, mouseX, mouseY);

        if (!compact && !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, innerX, refreshBounds.y - 13,
                    ModernUiRenderer.SUCCESS, innerWidth);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        if (contains(refreshBounds, mouseX, mouseY)) {
            refreshValues();
            showStatus("gui.modern.res.u011");
            return true;
        }
        // Information labels are deliberately inert; clicking a row does not mutate read-only values.
        return contains(contentBounds, mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(contentBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredInfoTooltip;
    }

    @Override
    public void discardDraft() {
        // This page has no editable draft.
    }

    private ModernMainLayout.Rect drawValueRow(FontRenderer fontRenderer, ModernMainLayout.Rect panel, int y,
            int rowHeight, String title, String value, String tooltip, int mouseX, int mouseY) {
        int x = panel.x + 16;
        int width = Math.max(1, panel.width - 32);
        ModernMainLayout.Rect row = new ModernMainLayout.Rect(x, y, width, rowHeight);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, title, row.x + 10, row.y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(32, row.width - 100));
        drawInfoIcon(fontRenderer, row.x + Math.min(Math.max(0, row.width - 76),
                fontRenderer.getStringWidth(title) + 15), row.y + 5, tooltip, mouseX, mouseY);
        int valueWidth = Math.max(40, Math.min(116, row.width / 3));
        ModernUiRenderer.drawText(fontRenderer, value, row.right() - valueWidth - 10,
                row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT, valueWidth);
        return row;
    }

    private void refreshValues() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            displayWidth = displayHeight = guiScale = 0;
            return;
        }
        displayWidth = Math.max(0, minecraft.displayWidth);
        displayHeight = Math.max(0, minecraft.displayHeight);
        try {
            guiScale = Math.max(0, new ScaledResolution(minecraft).getScaleFactor());
        } catch (Throwable ignored) {
            guiScale = 0;
        }
    }

    private ModernMainLayout.Rect createPanel(ModernMainLayout.Rect bounds) {
        int panelWidth = Math.max(1, bounds.width - 28);
        int preferredHeight = 218;
        int panelHeight = Math.min(Math.max(1, bounds.height - 26), preferredHeight);
        return new ModernMainLayout.Rect(bounds.x + 14, bounds.y + Math.max(12, (bounds.height - panelHeight) / 2),
                panelWidth, panelHeight);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean primary,
            int mouseX, int mouseY) {
        boolean hovered = contains(bounds, mouseX, mouseY);
        int fill = primary ? hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = primary ? 0xFFFFA4BC : ModernUiRenderer.BORDER;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int textWidth = fontRenderer.getStringWidth(ModernFormI18n.tr(label));
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(5, (bounds.width - textWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, bounds.width - 10);
    }

    private void drawInfoIcon(FontRenderer fontRenderer, int x, int y, String tooltip, int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredInfoTooltip = tooltip == null ? "" : tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }
}
