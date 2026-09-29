package com.mythos.mythosScriptMod.mcp;

import com.google.gson.JsonObject;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.opengl.GL11;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

public final class McpScreenshot {
    private McpScreenshot() {}

    public static JsonObject capture(JsonObject parameters) throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (isStubRendering()) return object("available", false, "mode", "headless_stub",
                "reason", "HeadlessMC -lwjgl replaces OpenGL with stubs. Use a world-state map or restart with real rendering.");
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        if (vendor == null || vendor.isEmpty()) return object("available", false, "reason", "No real OpenGL context available");
        int width = integer(parameters, "width", 480, 320, 1280);
        int height = integer(parameters, "height", 270, 180, 720);
        if (minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0
                || (long) minecraft.displayWidth * minecraft.displayHeight > 8388608)
            throw new IllegalStateException("Framebuffer dimensions outside screenshot budget");
        BufferedImage source = ScreenShotHelper.createScreenshot(minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
        double scale = Math.min(width / (double) source.getWidth(), height / (double) source.getHeight());
        int scaledWidth = Math.max(1, (int) (source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) (source.getHeight() * scale));
        BufferedImage output = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        Path directory = minecraft.mcDataDir.toPath().resolve("console-preview");
        Files.createDirectories(directory);
        Path destination = directory.resolve("latest.png");
        Path temporary = directory.resolve("latest.tmp");
        try {
            if (!ImageIO.write(output, "png", temporary.toFile())) throw new IOException("PNG encoder unavailable");
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            output.flush();
        }
        return object("available", true, "mode", "framebuffer", "path", destination.toAbsolutePath().toString(),
                "width", scaledWidth, "height", scaledHeight, "timestampMs", System.currentTimeMillis(), "renderer", vendor);
    }

    private static boolean isStubRendering() {
        String configured = System.getProperty("mythosscript.headless.stub");
        if (configured != null) return Boolean.parseBoolean(configured);
        try {
            Class.forName("io.github.headlesshq.headlessmc.lwjgl.api.RedirectionApi", false, GL11.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
