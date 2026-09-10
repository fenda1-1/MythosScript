package com.zszl.zszlScriptMod.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;

public class ToggleGuiButton extends GuiButton {
    private boolean enabledState;

    public ToggleGuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
            boolean initialState) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
        this.enabledState = initialState;
    }

    public void setEnabledState(boolean state) {
        this.enabledState = state;
    }

    public boolean getEnabledState() {
        return enabledState;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (mc == null || mc.fontRenderer == null || !this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
        ModernRuleEditorUi.drawToggleButton(mc.fontRenderer, this, mouseX, mouseY, enabledState);
    }
}
