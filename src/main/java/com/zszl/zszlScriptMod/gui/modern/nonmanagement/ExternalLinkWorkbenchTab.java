package com.zszl.zszlScriptMod.gui.modern.nonmanagement;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.UpdateManager;

import net.minecraft.client.gui.FontRenderer;

/** Modern status surface for actions that finish in the system browser. */
final class ExternalLinkWorkbenchTab implements ModernSettingsTab {

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect openBounds;
    private String status = "";
    private boolean requested;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!requested) {
            requestUpdateLink();
        }
    }
    @Override public void updateScreen() { }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        contentBounds = bounds;
        ModernMainLayout.Rect panel = bounds.inset(Math.min(14, Math.min(bounds.width, bounds.height) / 8));
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, "更新", panel.x + 14, panel.y + 14, ModernUiRenderer.TEXT,
                Math.max(40, panel.width - 28));
        ModernUiRenderer.drawText(fontRenderer, "检查更新后在系统浏览器打开下载页面。", panel.x + 14, panel.y + 33,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(60, panel.width - 28));
        openBounds = new ModernMainLayout.Rect(panel.x + 14, panel.y + 62, Math.min(150, panel.width - 28), 24);
        boolean hovered = openBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(openBounds.x, openBounds.y, openBounds.width, openBounds.height, 4,
                hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT, ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, "重新检查更新", openBounds.x + 9,
                openBounds.y + (openBounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.SHELL,
                openBounds.width - 18);
        if (!status.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, status, panel.x + 14, panel.y + 101,
                    ModernUiRenderer.SUCCESS, Math.max(20, panel.width - 28));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || openBounds == null || !openBounds.contains(mouseX, mouseY)) return false;
        requestUpdateLink();
        return true;
    }

    private void requestUpdateLink() {
        requested = true;
        UpdateManager.fetchUpdateLinkAndOpen();
        status = "已发起检查，链接准备完成后会打开浏览器";
    }

    @Override public boolean keyTyped(char typedChar, int keyCode) { return false; }
    @Override public boolean handleMouseWheel(int wheel) { return false; }
    @Override public boolean containsContent(int mouseX, int mouseY) { return contentBounds != null && contentBounds.contains(mouseX, mouseY); }
    @Override public String getHoveredTooltip(int mouseX, int mouseY) { return ""; }
    @Override public void discardDraft() { status = ""; }
}
