package com.zszl.zszlScriptMod.gui.modern.profile;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;

/** Native input or confirmation overlay for profile create, delete and import paste. */
final class ProfileModalPanel extends ModernEmbeddedPanel {

    interface Result {
        void accept(String value);
    }

    private final ProfileWorkbenchTab owner;
    private final String message;
    private final boolean input;
    private final boolean danger;
    private final Result result;
    private ModernTextField field;
    private String pendingInitial = "";
    private boolean committed;
    private ModernMainLayout.Rect area = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect cancelBounds;
    private ModernMainLayout.Rect confirmBounds;

    ProfileModalPanel(ProfileWorkbenchTab owner, String title, String message, String initial, boolean input,
            boolean danger, Result result) {
        super(title);
        this.owner = owner;
        this.message = message == null ? "" : message;
        this.input = input;
        this.danger = danger;
        this.result = result;
        this.pendingInitial = initial == null ? "" : initial;
    }

    @Override
    protected void onInitialize(FontRenderer fontRenderer) {
        if (input && fontRenderer != null) {
            field = new ModernTextField(9101, fontRenderer, 0, 0, 1, 18);
            field.setMaxStringLength(4096);
            field.setEnableBackgroundDrawing(false);
            field.setText(pendingInitial);
            field.setFocused(true);
        }
    }

    @Override
    public void updateScreen() {
        if (field != null) {
            field.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        area = bounds == null ? area : bounds;
        ModernUiRenderer.drawBackdropOverlay(area, 0xAA081016);
        int width = Math.min(440, Math.max(240, area.width - 36));
        int height = input ? 148 : 118;
        int x = area.x + Math.max(0, (area.width - width) / 2);
        int y = area.y + Math.max(0, (area.height - height) / 2);
        ModernMainLayout.Rect card = new ModernMainLayout.Rect(x, y, width, height);
        setBounds(card);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL_RAISED,
                danger ? ModernUiRenderer.DANGER : ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, getTitle(), x + 16, y + 14, ModernUiRenderer.TEXT, width - 32);
        ModernUiRenderer.drawText(fontRenderer, message, x + 16, y + 36, ModernUiRenderer.SUBTLE_TEXT, width - 32);
        if (input && field != null) {
            ProfileUi.drawField(fontRenderer, field, new ModernMainLayout.Rect(x + 16, y + 64, width - 32, 22),
                    "", mouseX, mouseY);
        }
        cancelBounds = new ModernMainLayout.Rect(x + width - 176, y + height - 32, 76, 22);
        confirmBounds = new ModernMainLayout.Rect(x + width - 92, y + height - 32, 76, 22);
        ProfileUi.drawButton(fontRenderer, cancelBounds, "取消", ProfileUi.Tone.DEFAULT, true,
                cancelBounds.contains(mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, confirmBounds, input ? "确定" : (danger ? "删除" : "确定"),
                danger ? ProfileUi.Tone.DANGER : ProfileUi.Tone.PRIMARY, true,
                confirmBounds.contains(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (input && field != null) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (ProfileUi.hit(confirmBounds, mouseX, mouseY)) {
            commit();
            return true;
        }
        if (ProfileUi.hit(cancelBounds, mouseX, mouseY) || !getBounds().contains(mouseX, mouseY)) {
            owner.back();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            owner.back();
            return true;
        }
        if (input && field != null && field.isFocused() && field.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            commit();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        return true;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return true;
    }

    @Override
    public void discardDraft() {
        if (!committed && field != null) {
            field.setText("");
            field.setFocused(false);
        }
    }

    private void commit() {
        if (committed) {
            return;
        }
        committed = true;
        String value = input && field != null ? field.getText() : "";
        owner.back();
        if (result != null) {
            result.accept(value);
        }
    }
}
