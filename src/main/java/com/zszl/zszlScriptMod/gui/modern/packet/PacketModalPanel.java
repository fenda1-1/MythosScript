package com.zszl.zszlScriptMod.gui.modern.packet;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;

import net.minecraft.client.gui.FontRenderer;

/** Embedded input/confirmation overlay used instead of legacy modal screens. */
final class PacketModalPanel extends ModernEmbeddedPanel {
    interface Result { void accept(String value); }
    private final PacketWorkbenchTab owner;
    private final String message;
    private final boolean input;
    private final Result result;
    private final PacketTextField field = new PacketTextField(9100, 32767);
    private boolean committed;
    private ModernMainLayout.Rect area = new ModernMainLayout.Rect(0, 0, 1, 1);

    PacketModalPanel(PacketWorkbenchTab owner, String title, String message, String initial, boolean input, Result result) {
        super(title); this.owner = owner; this.message = message == null ? "" : message; this.input = input; this.result = result;
        if (initial != null) pendingInitial = initial;
    }
    private String pendingInitial = "";

    @Override protected void onInitialize(FontRenderer fontRenderer) { field.ensure(fontRenderer); field.setText(pendingInitial); if (input) field.focus(true); }
    @Override public void updateScreen() { field.update(); }
    @Override public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        area = bounds == null ? area : bounds;
         ModernUiRenderer.drawBackdropOverlay(area, 0xAA081016);
        int width = Math.min(420, Math.max(220, area.width - 28));
        int height = input ? 142 : 112;
        int x = area.x + Math.max(0, (area.width - width) / 2), y = area.y + Math.max(0, (area.height - height) / 2);
        ModernMainLayout.Rect card = new ModernMainLayout.Rect(x, y, width, height);
        setBounds(card);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, getTitle(), x + 16, y + 12, ModernUiRenderer.TEXT, width - 32);
        ModernUiRenderer.drawText(fontRenderer, message, x + 16, y + 36, ModernUiRenderer.SUBTLE_TEXT, width - 32);
        if (input) { field.setBounds(new ModernMainLayout.Rect(x + 16, y + 62, width - 32, 20)); field.draw(); }
        ModernMainLayout.Rect cancel = new ModernMainLayout.Rect(x + width - 174, y + height - 30, 74, 22);
        ModernMainLayout.Rect okay = new ModernMainLayout.Rect(x + width - 92, y + height - 30, 76, 22);
        drawAction(fontRenderer, cancel, "gui.modern.pktmodal.u001", mouseX, mouseY, false);
        drawAction(fontRenderer, okay, "gui.modern.pktmodal.u002", mouseX, mouseY, true);
    }
    @Override public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!getBounds().contains(mouseX, mouseY)) return true;
        if (input && field.click(mouseX, mouseY, mouseButton)) return true;
        int y = getBounds().bottom() - 30;
        if (mouseButton == 0 && mouseY >= y) {
            if (mouseX >= getBounds().right() - 92) { commit(); return true; }
            if (mouseX >= getBounds().right() - 174) { owner.back(); return true; }
        }
        return true;
    }
    @Override public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) { owner.back(); return true; }
        if (field.key(typedChar, keyCode)) return true;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { commit(); return true; }
        return true;
    }
    @Override public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return true;
    }
    @Override public boolean mouseReleased(int mouseX, int mouseY, int state) { return true; }
    private void commit() {
        if (committed) return; committed = true;
        if (result != null) result.accept(input ? field.text() : "");
        owner.back();
    }

    private void drawAction(FontRenderer renderer, ModernMainLayout.Rect r, String label, int mouseX, int mouseY,
            boolean primary) {
        boolean hover = r.contains(mouseX, mouseY);
        int fill = primary ? (hover ? 0xFFFF86A7 : ModernUiRenderer.ACCENT)
                : (hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE);
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4, fill,
                primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if (renderer != null) {
            String text = com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label);
            int textWidth = renderer.getStringWidth(text);
            float scale = textWidth > r.width - 10 && textWidth > 0 ? (float) (r.width - 10) / textWidth : 1.0F;
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(r.x + r.width / 2.0F, r.y + r.height / 2.0F, 0.0F);
            net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1.0F);
            renderer.drawString(text, -textWidth / 2.0F, -renderer.FONT_HEIGHT / 2.0F,
                    primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, false);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }
    @Override public void discardDraft() { if (!committed) field.setText(""); PacketTextField.clearActiveFocus(); }
}
