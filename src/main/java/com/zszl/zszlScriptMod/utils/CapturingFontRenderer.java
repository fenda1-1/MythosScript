package com.zszl.zszlScriptMod.utils;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

/**
 * 一个自定义的FontRenderer, 用于捕获所有通过它绘制的文本.
 * 它继承了原版的FontRenderer, 并重写了核心的drawString方法.
 * 新增了禁用/启用捕获的功能，以过滤掉我们自己GUI的文本。
 */
public class CapturingFontRenderer extends FontRenderer {

    private boolean captureEnabled = true;

    public CapturingFontRenderer(GameSettings gameSettings, ResourceLocation location, TextureManager textureManager, boolean unicode) {
        super(gameSettings, location, textureManager, unicode);
    }

    /**
     * 禁用文本捕获。
     */
    public void disableCapture() {
        this.captureEnabled = false;
    }

    /**
     * 启用文本捕获。
     */
    public void enableCapture() {
        this.captureEnabled = true;
    }

    /**
     * 重写核心的绘制字符串方法.
     * 这是大多数drawString变体最终会调用的方法.
     */
    @Override
    public int drawString(String text, float x, float y, int color, boolean dropShadow) {
        // 步骤 1: 如果捕获已启用，则将文本和坐标传递给扫描器
        if (this.captureEnabled && text != null) {
            HudTextScanner.INSTANCE.addText(text, x, y);
        }
        
        // 步骤 2: 调用父类的原始方法, 让文本正常绘制到屏幕上
        return super.drawString(text, x, y, color, dropShadow);
    }
}
