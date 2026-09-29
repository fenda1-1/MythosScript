package com.mythos.mythosScriptMod.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MenuFontInitializationTest {
    @Test(timeout = 30000)
    public void backgroundWorkerRasterizesHighDefinitionChinese() throws Exception {
        Class<?> renderer = Class.forName("com.mythos.mythosScriptMod.gui.DetachedSharpFontRenderer");
        Field font = renderer.getDeclaredField("workerFont");
        font.setAccessible(true);
        assertNull("Class initialization must not load AWT fonts", font.get(null));
        Field worker = renderer.getDeclaredField("FONT_WORKER");
        worker.setAccessible(true);
        ExecutorService executor = (ExecutorService) worker.get(null);
        Method rasterize = renderer.getDeclaredMethod("rasterize", String.class, int.class, int.class);
        rasterize.setAccessible(true);
        CountDownLatch gate = new CountDownLatch(1);
        Future<?> blocked = executor.submit(() -> { try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        Future<BufferedImage> result = executor.submit(() -> {
            assertEquals("MythosScript-FontRasterizer", Thread.currentThread().getName());
            return (BufferedImage) rasterize.invoke(null, "控制中心路径动作参数", 240, 24);
        });
        try {
            assertFalse("Font work must queue without waiting on the caller", result.isDone());
        } finally { gate.countDown(); }
        blocked.get(5, TimeUnit.SECONDS);
        BufferedImage image = result.get(20, TimeUnit.SECONDS);
        assertNotNull("High definition rendering must actually succeed", image);
        boolean painted = false;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) painted |= (image.getRGB(x, y) >>> 24) != 0;
        assertTrue("Chinese glyphs must produce visible pixels", painted);
    }
}
