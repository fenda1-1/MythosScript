package com.zszl.zszlScriptMod.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;

public class ThemedButton extends GuiButton {

    public ThemedButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (mc == null || mc.fontRenderer == null || !this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
        ModernRuleEditorUi.drawButton(mc.fontRenderer, this, mouseX, mouseY,
                ModernRuleEditorUi.ButtonTone.DEFAULT);
    }
}
