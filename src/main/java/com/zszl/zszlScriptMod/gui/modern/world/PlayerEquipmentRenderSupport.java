package com.zszl.zszlScriptMod.gui.modern.world;

import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;

/** Rendering and text helpers kept separate from the equipment interaction state. */
final class PlayerEquipmentRenderSupport {

    private static final int GRID_GAP = 4;

    private PlayerEquipmentRenderSupport() {
    }

    static void drawStack(Minecraft minecraft, FontRenderer fontRenderer, ItemStack stack, int x, int y) {
        if (minecraft == null || minecraft.getRenderItem() == null || fontRenderer == null) {
            return;
        }
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        minecraft.getRenderItem().renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static List<String> tooltipLines(Minecraft minecraft, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        try {
            List<String> lines = stack.getTooltip(minecraft == null ? null : minecraft.player,
                    ITooltipFlag.TooltipFlags.NORMAL);
            return lines == null || lines.isEmpty() ? Collections.singletonList(stack.getDisplayName()) : lines;
        } catch (Throwable ignored) {
            return Collections.singletonList(stack.getDisplayName());
        }
    }

    static List<String> wrap(FontRenderer fontRenderer, String value, int width) {
        if (fontRenderer == null) return Collections.singletonList(value == null ? "" : value);
        List<String> lines = fontRenderer.listFormattedStringToWidth(value == null ? "" : value, Math.max(20, width));
        return lines == null || lines.isEmpty() ? Collections.singletonList("") : lines;
    }

    static void drawLines(FontRenderer fontRenderer, List<String> lines, int x, int y, int width, int maxLines,
            int color) {
        if (lines == null || fontRenderer == null) return;
        int count = Math.min(Math.max(0, maxLines), lines.size());
        for (int i = 0; i < count; i++) {
            ModernUiRenderer.drawText(fontRenderer, lines.get(i), x, y + i * fontRenderer.FONT_HEIGHT, color, width);
        }
    }

    static String itemId(ItemStack stack) {
        return stack == null || stack.getItem() == null ? "unknown" : String.valueOf(stack.getItem().getRegistryName());
    }

    static int gridSlotSize(int width) {
        return Math.max(16, Math.min(28, (Math.max(1, width - 20) - GRID_GAP * 8) / 9));
    }
}
