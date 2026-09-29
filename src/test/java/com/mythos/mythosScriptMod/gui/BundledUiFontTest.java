package com.mythos.mythosScriptMod.gui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class BundledUiFontTest {
    @Test
    public void bundledFontLoadsAndRasterizesChineseWithoutSystemFontLookup() throws Exception {
        Font font = BundledUiFont.load().deriveFont(24f);
        assertEquals("Noto Sans CJK SC", font.getFamily(java.util.Locale.ROOT));
        assertEquals(-1, font.canDisplayUpTo("控制中心路径动作参数搜索默认分类 战斗移动 玩家配置 繁體中文 Default 1.0.72"));
        BufferedImage image = new BufferedImage(128, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setFont(font);
            graphics.drawString("中文字体", 0, 30);
        } finally {
            graphics.dispose();
        }
        int painted = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) painted++;
            }
        }
        assertTrue("Chinese text should produce visible pixels", painted > 0);
    }
}
