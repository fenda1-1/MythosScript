package com.zszl.zszlScriptMod.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;

import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.system.GlobalKeybindListener;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.EmbeddedGuiScreenInputBridge;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Renders the existing modern screen into an off-screen Minecraft framebuffer
 * and presents that framebuffer in a native Swing window. Input remains owned
 * by the original GuiScreen, so the detached view does not need a second copy
 * of the feature-specific controls.
 */
public final class DetachedSwingWindowManager {

    public static final DetachedSwingWindowManager INSTANCE = new DetachedSwingWindowManager();

    // A detached frame is a convenience preview, not the primary game view.
    // Ten captures per second keeps the native readback and image conversion
    // from competing with Minecraft's render loop.
    private static final long FOCUSED_CAPTURE_INTERVAL_NANOS = 100_000_000L;
    private static final long RESIZE_SETTLE_NANOS = 90_000_000L;
    private static final int DEFAULT_UI_SCALE_PERCENT = 200;
    private static final int MIN_UI_SCALE_PERCENT = 100;
    private static final int MAX_UI_SCALE_PERCENT = 300;
    private static final int UI_SCALE_STEP_PERCENT = 25;
    private static final int MIN_WINDOW_WIDTH = 720;
    private static final int MIN_WINDOW_HEIGHT = 520;
    private static final int PIXEL_PACK_BUFFER_COUNT = 2;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    private static final Color PANEL_BACKGROUND = new Color(10, 16, 22);

    private volatile boolean detached;
    private volatile GuiModernMainScreen activeScreen;
    private volatile int virtualMouseX = -1;
    private volatile int virtualMouseY = -1;
    private volatile int detachedUiScalePercent = DEFAULT_UI_SCALE_PERCENT;
    private volatile long lastCaptureNanos;
    private volatile DetachedFrame swingFrame;
    private Framebuffer detachedFramebuffer;
    private int framebufferWidth;
    private int framebufferHeight;
    private final int[] pixelPackBuffers = new int[PIXEL_PACK_BUFFER_COUNT];
    private int pixelPackBufferWidth;
    private int pixelPackBufferHeight;
    private int pixelPackWriteIndex;
    private boolean pixelReadbackPending;
    private boolean loggedCaptureFailure;
    private boolean loggedFirstFrame;
    private DetachedSharpFontRenderer sharpFontRenderer;
    private volatile boolean renderingNativeFrame;
    private volatile int nativeRenderPixelWidth = 1;
    private volatile int nativeRenderPixelHeight = 1;
    private volatile int nativeRenderLogicalWidth = 1;
    private volatile int nativeRenderLogicalHeight = 1;
    private final AtomicBoolean interactionCancelQueued = new AtomicBoolean();

    private DetachedSwingWindowManager() {
    }

    public static boolean isDetached() {
        return INSTANCE.detached;
    }

    public static boolean isDetachedScreen(GuiScreen screen) {
        return INSTANCE.detached && INSTANCE.activeScreen == screen;
    }

    public static GuiModernMainScreen getActiveScreen() {
        return INSTANCE.detached ? INSTANCE.activeScreen : null;
    }

    /** Gives keyboard focus back to the detached Swing window without changing
     * detached mode or recreating the retained GuiScreen. */
    public void focusDetachedWindow() {
        if (!detached) {
            return;
        }
        DetachedFrame frame = swingFrame;
        if (frame != null) {
            frame.focusWindow();
        }
    }

    /** Temporarily hides the detached frame so Minecraft receives keyboard
     * focus.  Detached mode remains active and the next game F press can bring
     * the frame back. */
    public void focusGameWindow() {
        if (!detached) {
            return;
        }
        DetachedFrame frame = swingFrame;
        if (frame != null) {
            frame.hideWindow();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.currentScreen == null) {
            minecraft.mouseHelper.grabMouseCursor();
        }
    }

    public static int getVirtualMouseX() {
        return INSTANCE.virtualMouseX;
    }

    public static int getVirtualMouseY() {
        return INSTANCE.virtualMouseY;
    }

    public static boolean isRenderingNativeFrame() {
        return INSTANCE.renderingNativeFrame;
    }

    public static int getNativeRenderPixelWidth() {
        return INSTANCE.nativeRenderPixelWidth;
    }

    public static int getNativeRenderPixelHeight() {
        return INSTANCE.nativeRenderPixelHeight;
    }

    public static int getNativeRenderLogicalWidth() {
        return INSTANCE.nativeRenderLogicalWidth;
    }

    public static int getNativeRenderLogicalHeight() {
        return INSTANCE.nativeRenderLogicalHeight;
    }

    /** Opens the native window while keeping the same GuiScreen instance alive. */
    public void detach(GuiModernMainScreen screen) {
        if (screen == null || detached) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            zszlScriptMod.LOGGER.warn("无法打开独立 GUI：当前 Java 环境处于 headless 模式");
            return;
        }

        activeScreen = screen;
        virtualMouseX = Math.max(0, screen.width / 2);
        virtualMouseY = Math.max(0, screen.height / 2);
        lastCaptureNanos = 0L;
        loggedCaptureFailure = false;
        loggedFirstFrame = false;
        detached = true;
        ensureSwingFrame(screen.width, screen.height);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.currentScreen == screen) {
            // The actual Minecraft window must leave GuiScreen input mode.
            // The screen object itself remains alive for the Swing copy.
            minecraft.displayGuiScreen(null);
            // displayGuiScreen(null) normally grabs the pointer, but keep the
            // final state explicit: the game owns its mouse while the retained
            // screen is rendered and interacted with by the Swing window.
            minecraft.mouseHelper.grabMouseCursor();
        }
        zszlScriptMod.LOGGER.info("主界面已切换到 Swing 独立窗口");
    }

    /** Returns rendering to Minecraft without closing the GuiScreen instance. */
    public void dock() {
        if (!detached) {
            return;
        }
        GuiModernMainScreen screen = clearDetachedState();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (screen != null && minecraft != null && minecraft.currentScreen == null && minecraft.world != null) {
            minecraft.displayGuiScreen(screen);
        } else if (screen != null && minecraft != null && minecraft.currentScreen == screen
                && minecraft.world != null) {
            // Swing input temporarily binds the retained screen so legacy
            // controls can inspect Minecraft.currentScreen. Keep it bound
            // after a dock request instead of restoring the old null value.
            minecraft.displayGuiScreen(screen);
        }
        zszlScriptMod.LOGGER.info("主界面已返回 Minecraft 窗口");
    }

    /** Closes the detached menu entirely, preserving the original close action. */
    public void closeDetachedMenu() {
        if (!detached) {
            return;
        }
        GuiModernMainScreen screen = clearDetachedState();
        if (screen != null) {
            screen.onGuiClosed();
        }
        zszlScriptMod.isGuiVisible = false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.currentScreen == screen) {
            minecraft.currentScreen = null;
        }
        if (minecraft != null && minecraft.currentScreen == null && minecraft.world != null) {
            minecraft.mouseHelper.grabMouseCursor();
        }
        zszlScriptMod.LOGGER.info("独立窗口中的主界面已关闭");
    }

    /** Called when the Swing title-bar close button is used. */
    private void requestCloseFromSwing() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        minecraft.addScheduledTask(this::closeDetachedMenu);
    }

    private void requestCancelInteractions() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiModernMainScreen screen = activeScreen;
        if (minecraft == null || screen == null || !detached || !interactionCancelQueued.compareAndSet(false, true)) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            try {
                if (detached && activeScreen == screen) {
                    screen.cancelDetachedInteractions();
                }
            } finally {
                interactionCancelQueued.set(false);
            }
        });
    }

    private void persistWindowLayout(Rectangle bounds, boolean maximized) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Rectangle snapshot = new Rectangle(bounds);
        Runnable task = () -> MainUiLayoutManager.setDetachedWindowLayout(snapshot.x, snapshot.y,
                snapshot.width, snapshot.height, maximized);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            minecraft.addScheduledTask(task);
        } else {
            task.run();
        }
    }

    private void setDetachedUiScalePercent(int scalePercent) {
        int normalized = normalizeUiScalePercent(scalePercent);
        if (detachedUiScalePercent == normalized) {
            return;
        }
        detachedUiScalePercent = normalized;
        virtualMouseX = -1;
        virtualMouseY = -1;
        lastCaptureNanos = 0L;
        DetachedFrame frame = swingFrame;
        if (frame != null) {
            frame.onUiScaleChanged();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        Runnable persist = () -> MainUiLayoutManager.setDetachedUiScalePercent(normalized);
        if (minecraft != null) {
            minecraft.addScheduledTask(persist);
        } else {
            persist.run();
        }
        zszlScriptMod.LOGGER.info("独立 GUI 缩放已设置为 {}%", normalized);
    }

    private void adjustDetachedUiScale(int direction) {
        setDetachedUiScalePercent(detachedUiScalePercent + direction * UI_SCALE_STEP_PERCENT);
    }

    public void adjustUiScaleFromAction(int direction) {
        adjustDetachedUiScale(direction);
    }

    public void setUiScaleFromAction(int scalePercent) {
        setDetachedUiScalePercent(scalePercent);
    }

    public void resetUiScaleFromAction() {
        setDetachedUiScalePercent(DEFAULT_UI_SCALE_PERCENT);
    }

    private static int normalizeUiScalePercent(int scalePercent) {
        int clamped = Math.max(MIN_UI_SCALE_PERCENT, Math.min(MAX_UI_SCALE_PERCENT, scalePercent));
        return Math.round(clamped / (float) UI_SCALE_STEP_PERCENT) * UI_SCALE_STEP_PERCENT;
    }

    /** Releases the native window only after detached mode has explicitly ended. */
    public void onScreenClosed(GuiScreen screen) {
        if (isDetachedScreen(screen)) {
            return;
        }
        if (activeScreen == null && swingFrame != null && screen instanceof GuiModernMainScreen) {
            DetachedFrame frame = swingFrame;
            swingFrame = null;
            frame.disposeWindow();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!detached) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || activeScreen == null || minecraft.world == null) {
            clearDetachedState();
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            DetachedFrame frame = swingFrame;
            long now = System.nanoTime();
            if (frame != null && frame.isCaptureAllowed()
                    && now - lastCaptureNanos >= FOCUSED_CAPTURE_INTERVAL_NANOS) {
                updateDetachedScreen(minecraft, activeScreen);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !detached || activeScreen == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null) {
            clearDetachedState();
            return;
        }

        long now = System.nanoTime();
        DetachedFrame frame = swingFrame;
        if (frame == null || !frame.isCaptureAllowed()) {
            return;
        }
        Dimension renderSize = frame.getRenderPixelSize();
        boolean framebufferResizePending = renderSize.width > 1 && renderSize.height > 1
                && (framebufferWidth != renderSize.width || framebufferHeight != renderSize.height);
        if (framebufferResizePending && !frame.isViewportResizeSettled(now)) {
            return;
        }
        if (now - lastCaptureNanos < FOCUSED_CAPTURE_INTERVAL_NANOS) {
            return;
        }
        lastCaptureNanos = now;
        renderDetachedFrame(minecraft, activeScreen, 0.0F);
    }

    private void renderDetachedFrame(Minecraft minecraft, GuiModernMainScreen screen, float partialTicks) {
        Dimension logicalSize = syncDetachedScreenSize(screen);
        DetachedFrame frame = swingFrame;
        Dimension renderSize = frame == null ? logicalSize : frame.getRenderPixelSize();
        int pixelWidth = renderSize.width;
        int pixelHeight = renderSize.height;
        Framebuffer target = ensureFramebuffer(pixelWidth, pixelHeight);
        if (target == null) {
            publishError("独立窗口渲染目标创建失败");
            return;
        }

        BufferedImage image = null;
        GuiScreen previousScreen = minecraft.currentScreen;
        int previousDisplayWidth = minecraft.displayWidth;
        int previousDisplayHeight = minecraft.displayHeight;
        int previousGuiScale = minecraft.gameSettings.guiScale;
        FontRenderer previousMinecraftFont = minecraft.fontRenderer;
        FontRenderer previousScreenFont = null;
        boolean screenFontReplaced = false;
        try {
            // Framebuffer.framebufferClear() unbinds the framebuffer in
            // Minecraft 1.12. Rebind it before drawing and again before the
            // readback, otherwise glReadPixels reads the game backbuffer.
            minecraft.currentScreen = screen;
            // The framebuffer matches the native Swing device pixels. Layout
            // remains logical, while projection and clipping scale directly to
            // final pixels so text is never enlarged from a low-res image.
            minecraft.displayWidth = pixelWidth;
            minecraft.displayHeight = pixelHeight;
            minecraft.gameSettings.guiScale = 1;
            target.bindFramebuffer(true);
            target.framebufferClear();
            target.bindFramebuffer(true);
            prepareDetachedGuiState();
            minecraft.entityRenderer.setupOverlayRendering();
            setupNativeLogicalProjection(logicalSize.width, logicalSize.height, pixelWidth, pixelHeight);
            renderingNativeFrame = true;
            nativeRenderPixelWidth = pixelWidth;
            nativeRenderPixelHeight = pixelHeight;
            nativeRenderLogicalWidth = logicalSize.width;
            nativeRenderLogicalHeight = logicalSize.height;
            if (sharpFontRenderer == null) {
                sharpFontRenderer = new DetachedSharpFontRenderer(minecraft, previousMinecraftFont);
            }
            minecraft.fontRenderer = sharpFontRenderer;
            previousScreenFont = screen.replaceDetachedFontRenderer(sharpFontRenderer);
            screenFontReplaced = true;
            ModernTooltipSupport.clearRegisteredInfoIcons(screen);
            screen.drawScreen(getVirtualMouseX(), getVirtualMouseY(), partialTicks);
            ModernTooltipSupport.draw(screen, Collections.emptyList(), getVirtualMouseX(), getVirtualMouseY());
            target.bindFramebuffer(true);
            image = capturePixels(pixelWidth, pixelHeight);
            if (!loggedFirstFrame && image != null) {
                loggedFirstFrame = true;
                zszlScriptMod.LOGGER.info("独立 GUI 首帧已生成: {}x{}, 中心像素 ARGB={}", image.getWidth(),
                        image.getHeight(), Integer.toHexString(image.getRGB(image.getWidth() / 2,
                                image.getHeight() / 2)));
            }
            loggedCaptureFailure = false;
        } catch (Throwable throwable) {
            if (!loggedCaptureFailure) {
                loggedCaptureFailure = true;
                zszlScriptMod.LOGGER.error("独立 Swing 窗口渲染失败", throwable);
            }
        } finally {
            renderingNativeFrame = false;
            if (screenFontReplaced) {
                screen.replaceDetachedFontRenderer(previousScreenFont);
            }
            minecraft.fontRenderer = previousMinecraftFont;
            minecraft.displayWidth = previousDisplayWidth;
            minecraft.displayHeight = previousDisplayHeight;
            minecraft.gameSettings.guiScale = previousGuiScale;
            if (minecraft.currentScreen == screen) {
                minecraft.currentScreen = previousScreen;
            }
            try {
                Framebuffer main = minecraft.getFramebuffer();
                if (main != null) {
                    main.bindFramebuffer(true);
                }
                minecraft.entityRenderer.setupOverlayRendering();
                GlStateManager.enableTexture2D();
                GlStateManager.enableAlpha();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            } catch (Throwable throwable) {
                zszlScriptMod.LOGGER.warn("恢复 Minecraft GUI 渲染状态失败", throwable);
            }
        }

        if (image != null) {
            DetachedFrame publishFrame = swingFrame;
            if (publishFrame != null) {
                publishFrame.publish(image);
            }
        } else if (!pixelReadbackPending) {
            publishError("独立窗口暂时无法获取画面");
        }
    }

    private void setupNativeLogicalProjection(int logicalWidth, int logicalHeight, int pixelWidth, int pixelHeight) {
        GL11.glViewport(0, 0, Math.max(1, pixelWidth), Math.max(1, pixelHeight));
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, Math.max(1, logicalWidth), Math.max(1, logicalHeight), 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
    }

    private void prepareDetachedGuiState() {
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private GuiModernMainScreen clearDetachedState() {
        if (!detached) {
            return null;
        }
        GuiModernMainScreen screen = activeScreen;
        detached = false;
        activeScreen = null;
        virtualMouseX = -1;
        virtualMouseY = -1;
        lastCaptureNanos = 0L;
        releaseFramebuffer();
        releaseSharpFontRenderer();
        DetachedFrame frame = swingFrame;
        if (frame != null) {
            frame.releaseKeys();
            frame.hideWindow();
        }
        return screen;
    }

    private void updateDetachedScreen(Minecraft minecraft, GuiModernMainScreen screen) {
        syncDetachedScreenSize(screen);
        GuiScreen previousScreen = minecraft.currentScreen;
        minecraft.currentScreen = screen;
        try {
            screen.updateScreen();
        } finally {
            if (minecraft.currentScreen == screen) {
                minecraft.currentScreen = previousScreen;
            }
        }
    }

    private Dimension syncDetachedScreenSize(GuiModernMainScreen screen) {
        int width = Math.max(1, screen.width);
        int height = Math.max(1, screen.height);
        DetachedFrame frame = swingFrame;
        if (frame != null) {
            Dimension logicalSize = toLogicalSize(frame.getViewportSize());
            if (logicalSize.width > 1 && logicalSize.height > 1) {
                width = logicalSize.width;
                height = logicalSize.height;
            }
        }
        if (screen.width != width || screen.height != height) {
            screen.width = width;
            screen.height = height;
            if (virtualMouseX >= 0) {
                virtualMouseX = Math.min(virtualMouseX, width - 1);
            }
            if (virtualMouseY >= 0) {
                virtualMouseY = Math.min(virtualMouseY, height - 1);
            }
        }
        return new Dimension(width, height);
    }

    private Dimension toLogicalSize(Dimension viewport) {
        if (viewport == null || viewport.width <= 0 || viewport.height <= 0) {
            return new Dimension();
        }
        int scalePercent = Math.max(MIN_UI_SCALE_PERCENT, detachedUiScalePercent);
        int width = (int) Math.max(1L, (viewport.width * 100L + scalePercent - 1L) / scalePercent);
        int height = (int) Math.max(1L, (viewport.height * 100L + scalePercent - 1L) / scalePercent);
        return new Dimension(width, height);
    }

    private BufferedImage capturePixels(int width, int height) {
        int byteCount = width * height * 4;
        ensurePixelPackBuffers(width, height, byteCount);

        BufferedImage image = null;
        int writeIndex = pixelPackWriteIndex;
        int readIndex = 1 - writeIndex;
        if (pixelReadbackPending) {
            GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelPackBuffers[readIndex]);
            ByteBuffer mapped = null;
            try {
                mapped = GL15.glMapBuffer(GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, byteCount, null);
                if (mapped != null) {
                    image = createImageFromPixels(mapped, width, height);
                }
            } finally {
                if (mapped != null) {
                    GL15.glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
                }
                GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            }
        }

        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelPackBuffers[writeIndex]);
        GL15.glBufferData(GL_PIXEL_PACK_BUFFER, byteCount, GL15.GL_STREAM_READ);
        // With a pixel-pack buffer bound, the final argument is an offset,
        // so the transfer can proceed asynchronously while Minecraft renders.
        // Request packed ARGB-compatible pixels so publication only needs a
        // bulk row copy instead of four ByteBuffer reads and bit assembly for
        // every pixel on Minecraft's render thread.
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        pixelPackWriteIndex = readIndex;
        pixelReadbackPending = true;
        return image;
    }

    private BufferedImage createImageFromPixels(ByteBuffer pixels, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] colors = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        IntBuffer packedPixels = pixels.order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int y = 0; y < height; y++) {
            int sourceY = height - y - 1;
            // Keep the invoked descriptor compatible with Java 8. Java 9+
            // changed IntBuffer.position(int) to return IntBuffer instead of
            // Buffer, even when sourceCompatibility is set to 1.8.
            ((Buffer) packedPixels).position(sourceY * width);
            packedPixels.get(colors, y * width, width);
        }
        return image;
    }

    private void ensurePixelPackBuffers(int width, int height, int byteCount) {
        if (pixelPackBuffers[0] != 0 && pixelPackBufferWidth == width && pixelPackBufferHeight == height) {
            return;
        }
        releasePixelPackBuffers();
        for (int i = 0; i < PIXEL_PACK_BUFFER_COUNT; i++) {
            pixelPackBuffers[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelPackBuffers[i]);
            GL15.glBufferData(GL_PIXEL_PACK_BUFFER, byteCount, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        pixelPackBufferWidth = width;
        pixelPackBufferHeight = height;
        pixelPackWriteIndex = 0;
        pixelReadbackPending = false;
    }

    private void releasePixelPackBuffers() {
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        for (int i = 0; i < PIXEL_PACK_BUFFER_COUNT; i++) {
            if (pixelPackBuffers[i] != 0) {
                GL15.glDeleteBuffers(pixelPackBuffers[i]);
                pixelPackBuffers[i] = 0;
            }
        }
        pixelPackBufferWidth = 0;
        pixelPackBufferHeight = 0;
        pixelPackWriteIndex = 0;
        pixelReadbackPending = false;
    }

    private Framebuffer ensureFramebuffer(int width, int height) {
        if (detachedFramebuffer != null && framebufferWidth == width && framebufferHeight == height) {
            return detachedFramebuffer;
        }
        releaseFramebuffer();
        try {
            detachedFramebuffer = new Framebuffer(width, height, true);
            framebufferWidth = width;
            framebufferHeight = height;
            return detachedFramebuffer;
        } catch (Throwable throwable) {
            zszlScriptMod.LOGGER.error("创建独立 GUI framebuffer 失败", throwable);
            detachedFramebuffer = null;
            return null;
        }
    }

    private void releaseFramebuffer() {
        if (detachedFramebuffer != null) {
            try {
                detachedFramebuffer.deleteFramebuffer();
            } catch (Throwable throwable) {
                zszlScriptMod.LOGGER.warn("释放独立 GUI framebuffer 失败", throwable);
            }
        }
        detachedFramebuffer = null;
        framebufferWidth = 0;
        framebufferHeight = 0;
        releasePixelPackBuffers();
    }

    private void releaseSharpFontRenderer() {
        if (sharpFontRenderer == null) {
            return;
        }
        try {
            sharpFontRenderer.release();
        } catch (Throwable throwable) {
            zszlScriptMod.LOGGER.warn("释放独立窗口高清字体纹理失败", throwable);
        }
        sharpFontRenderer = null;
    }

    private void ensureSwingFrame(int logicalWidth, int logicalHeight) {
        MainUiLayoutManager.DetachedWindowLayout storedLayout = MainUiLayoutManager.getDetachedWindowLayout();
        Runnable task = () -> {
            if (swingFrame == null) {
                swingFrame = new DetachedFrame(this);
            }
            swingFrame.showWindow(logicalWidth, logicalHeight, storedLayout);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void publishError(String message) {
        DetachedFrame frame = swingFrame;
        if (frame != null) {
            frame.publishError(message);
        }
    }

    private void dispatchMousePressed(int x, int y, int button) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiModernMainScreen screen = activeScreen;
        if (minecraft == null || screen == null) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            if (!detached || activeScreen != screen) {
                return;
            }
            syncDetachedScreenSize(screen);
            GuiScreen previousScreen = minecraft.currentScreen;
            minecraft.currentScreen = screen;
            try {
                EmbeddedGuiScreenInputBridge.mouseClicked(screen, x, y, button);
            } catch (IOException exception) {
                zszlScriptMod.LOGGER.warn("处理独立窗口鼠标按下事件失败", exception);
            } finally {
                if (detached && minecraft.currentScreen == screen) {
                    minecraft.currentScreen = previousScreen;
                }
            }
        });
    }

    private void dispatchMouseMove(int x, int y, int button, long elapsed) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiModernMainScreen screen = activeScreen;
        if (minecraft == null || screen == null) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            if (!detached || activeScreen != screen) {
                return;
            }
            syncDetachedScreenSize(screen);
            GuiScreen previousScreen = minecraft.currentScreen;
            minecraft.currentScreen = screen;
            try {
            EmbeddedGuiScreenInputBridge.mouseClickMove(screen, x, y, button, elapsed);
            } finally {
                if (detached && minecraft.currentScreen == screen) {
                    minecraft.currentScreen = previousScreen;
                }
            }
        });
    }

    private void dispatchMouseReleased(int x, int y, int button) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiModernMainScreen screen = activeScreen;
        if (minecraft == null || screen == null) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            if (!detached || activeScreen != screen) {
                return;
            }
            syncDetachedScreenSize(screen);
            GuiScreen previousScreen = minecraft.currentScreen;
            minecraft.currentScreen = screen;
            try {
            EmbeddedGuiScreenInputBridge.mouseReleased(screen, x, y, button);
            } finally {
                if (detached && minecraft.currentScreen == screen) {
                    minecraft.currentScreen = previousScreen;
                }
            }
        });
    }

    private void dispatchMouseWheel(int x, int y, int wheel) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiModernMainScreen screen = activeScreen;
        if (minecraft == null || screen == null) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            if (detached && activeScreen == screen) {
                syncDetachedScreenSize(screen);
                GuiScreen previousScreen = minecraft.currentScreen;
                minecraft.currentScreen = screen;
                try {
                    screen.handleDetachedMouseWheel(wheel, x, y);
                } finally {
                    if (detached && minecraft.currentScreen == screen) {
                        minecraft.currentScreen = previousScreen;
                    }
                }
            }
        });
    }

    private void dispatchKey(int keyCode, char typedChar, Set<Integer> modifiers) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiModernMainScreen screen = activeScreen;
        if (minecraft == null || screen == null || keyCode == Keyboard.KEY_NONE && typedChar == 0) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            if (!detached || activeScreen != screen) {
                return;
            }
            syncDetachedScreenSize(screen);
            GuiScreen previousScreen = minecraft.currentScreen;
            minecraft.currentScreen = screen;
            try {
                if (keyCode == zszlScriptMod.getGuiToggleKeyCode()) {
                    // F is the focus toggle while detached.  Do not forward it
                    // to GuiModernMainScreen, whose normal behavior closes the
                    // in-game screen.
                    focusGameWindow();
                    return;
                }
                if (keyCode != Keyboard.KEY_NONE) {
                    GlobalKeybindListener.dispatchExternalKeyPress(keyCode, modifiers);
                }
                EmbeddedGuiScreenInputBridge.keyTyped(screen, typedChar, keyCode);
            } catch (IOException exception) {
                zszlScriptMod.LOGGER.warn("处理独立窗口键盘事件失败", exception);
            } finally {
                if (detached && minecraft.currentScreen == screen) {
                    minecraft.currentScreen = previousScreen;
                }
            }
        });
    }

    private static int mapMouseButton(int awtButton) {
        if (awtButton == MouseEvent.BUTTON1) {
            return 0;
        }
        if (awtButton == MouseEvent.BUTTON3) {
            return 1;
        }
        if (awtButton == MouseEvent.BUTTON2) {
            return 2;
        }
        return -1;
    }

    private static int mapKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE: return Keyboard.KEY_ESCAPE;
            case KeyEvent.VK_BACK_SPACE: return Keyboard.KEY_BACK;
            case KeyEvent.VK_TAB: return Keyboard.KEY_TAB;
            case KeyEvent.VK_ENTER: return Keyboard.KEY_RETURN;
            case KeyEvent.VK_SHIFT: return Keyboard.KEY_LSHIFT;
            case KeyEvent.VK_CONTROL: return Keyboard.KEY_LCONTROL;
            case KeyEvent.VK_ALT: return Keyboard.KEY_LMENU;
            case KeyEvent.VK_SPACE: return Keyboard.KEY_SPACE;
            case KeyEvent.VK_CAPS_LOCK: return Keyboard.KEY_CAPITAL;
            case KeyEvent.VK_PAGE_UP: return Keyboard.KEY_PRIOR;
            case KeyEvent.VK_PAGE_DOWN: return Keyboard.KEY_NEXT;
            case KeyEvent.VK_END: return Keyboard.KEY_END;
            case KeyEvent.VK_HOME: return Keyboard.KEY_HOME;
            case KeyEvent.VK_LEFT: return Keyboard.KEY_LEFT;
            case KeyEvent.VK_UP: return Keyboard.KEY_UP;
            case KeyEvent.VK_RIGHT: return Keyboard.KEY_RIGHT;
            case KeyEvent.VK_DOWN: return Keyboard.KEY_DOWN;
            case KeyEvent.VK_INSERT: return Keyboard.KEY_INSERT;
            case KeyEvent.VK_DELETE: return Keyboard.KEY_DELETE;
            case KeyEvent.VK_MINUS: return Keyboard.KEY_MINUS;
            case KeyEvent.VK_EQUALS: return Keyboard.KEY_EQUALS;
            case KeyEvent.VK_PLUS: return Keyboard.KEY_ADD;
            case KeyEvent.VK_OPEN_BRACKET: return Keyboard.KEY_LBRACKET;
            case KeyEvent.VK_CLOSE_BRACKET: return Keyboard.KEY_RBRACKET;
            case KeyEvent.VK_BACK_SLASH: return Keyboard.KEY_BACKSLASH;
            case KeyEvent.VK_SEMICOLON: return Keyboard.KEY_SEMICOLON;
            case KeyEvent.VK_QUOTE: return Keyboard.KEY_APOSTROPHE;
            case KeyEvent.VK_BACK_QUOTE: return Keyboard.KEY_GRAVE;
            case KeyEvent.VK_COMMA: return Keyboard.KEY_COMMA;
            case KeyEvent.VK_PERIOD: return Keyboard.KEY_PERIOD;
            case KeyEvent.VK_SLASH: return Keyboard.KEY_SLASH;
            case KeyEvent.VK_NUMPAD0: return Keyboard.KEY_NUMPAD0;
            case KeyEvent.VK_NUMPAD1: return Keyboard.KEY_NUMPAD1;
            case KeyEvent.VK_NUMPAD2: return Keyboard.KEY_NUMPAD2;
            case KeyEvent.VK_NUMPAD3: return Keyboard.KEY_NUMPAD3;
            case KeyEvent.VK_NUMPAD4: return Keyboard.KEY_NUMPAD4;
            case KeyEvent.VK_NUMPAD5: return Keyboard.KEY_NUMPAD5;
            case KeyEvent.VK_NUMPAD6: return Keyboard.KEY_NUMPAD6;
            case KeyEvent.VK_NUMPAD7: return Keyboard.KEY_NUMPAD7;
            case KeyEvent.VK_NUMPAD8: return Keyboard.KEY_NUMPAD8;
            case KeyEvent.VK_NUMPAD9: return Keyboard.KEY_NUMPAD9;
            case KeyEvent.VK_ADD: return Keyboard.KEY_ADD;
            case KeyEvent.VK_SUBTRACT: return Keyboard.KEY_SUBTRACT;
            case KeyEvent.VK_MULTIPLY: return Keyboard.KEY_MULTIPLY;
            case KeyEvent.VK_DIVIDE: return Keyboard.KEY_DIVIDE;
            case KeyEvent.VK_DECIMAL: return Keyboard.KEY_DECIMAL;
            case KeyEvent.VK_F1: return Keyboard.KEY_F1;
            case KeyEvent.VK_F2: return Keyboard.KEY_F2;
            case KeyEvent.VK_F3: return Keyboard.KEY_F3;
            case KeyEvent.VK_F4: return Keyboard.KEY_F4;
            case KeyEvent.VK_F5: return Keyboard.KEY_F5;
            case KeyEvent.VK_F6: return Keyboard.KEY_F6;
            case KeyEvent.VK_F7: return Keyboard.KEY_F7;
            case KeyEvent.VK_F8: return Keyboard.KEY_F8;
            case KeyEvent.VK_F9: return Keyboard.KEY_F9;
            case KeyEvent.VK_F10: return Keyboard.KEY_F10;
            case KeyEvent.VK_F11: return Keyboard.KEY_F11;
            case KeyEvent.VK_F12: return Keyboard.KEY_F12;
            case KeyEvent.VK_1: return Keyboard.KEY_1;
            case KeyEvent.VK_2: return Keyboard.KEY_2;
            case KeyEvent.VK_3: return Keyboard.KEY_3;
            case KeyEvent.VK_4: return Keyboard.KEY_4;
            case KeyEvent.VK_5: return Keyboard.KEY_5;
            case KeyEvent.VK_6: return Keyboard.KEY_6;
            case KeyEvent.VK_7: return Keyboard.KEY_7;
            case KeyEvent.VK_8: return Keyboard.KEY_8;
            case KeyEvent.VK_9: return Keyboard.KEY_9;
            case KeyEvent.VK_0: return Keyboard.KEY_0;
            case KeyEvent.VK_A: return Keyboard.KEY_A;
            case KeyEvent.VK_B: return Keyboard.KEY_B;
            case KeyEvent.VK_C: return Keyboard.KEY_C;
            case KeyEvent.VK_D: return Keyboard.KEY_D;
            case KeyEvent.VK_E: return Keyboard.KEY_E;
            case KeyEvent.VK_F: return Keyboard.KEY_F;
            case KeyEvent.VK_G: return Keyboard.KEY_G;
            case KeyEvent.VK_H: return Keyboard.KEY_H;
            case KeyEvent.VK_I: return Keyboard.KEY_I;
            case KeyEvent.VK_J: return Keyboard.KEY_J;
            case KeyEvent.VK_K: return Keyboard.KEY_K;
            case KeyEvent.VK_L: return Keyboard.KEY_L;
            case KeyEvent.VK_M: return Keyboard.KEY_M;
            case KeyEvent.VK_N: return Keyboard.KEY_N;
            case KeyEvent.VK_O: return Keyboard.KEY_O;
            case KeyEvent.VK_P: return Keyboard.KEY_P;
            case KeyEvent.VK_Q: return Keyboard.KEY_Q;
            case KeyEvent.VK_R: return Keyboard.KEY_R;
            case KeyEvent.VK_S: return Keyboard.KEY_S;
            case KeyEvent.VK_T: return Keyboard.KEY_T;
            case KeyEvent.VK_U: return Keyboard.KEY_U;
            case KeyEvent.VK_V: return Keyboard.KEY_V;
            case KeyEvent.VK_W: return Keyboard.KEY_W;
            case KeyEvent.VK_X: return Keyboard.KEY_X;
            case KeyEvent.VK_Y: return Keyboard.KEY_Y;
            case KeyEvent.VK_Z: return Keyboard.KEY_Z;
            default: return Keyboard.KEY_NONE;
        }
    }

    private static int toLogicalX(MouseEvent event, int logicalWidth, int componentWidth) {
        return scaleCoordinate(event.getX(), logicalWidth, componentWidth);
    }

    private static int toLogicalY(MouseEvent event, int logicalHeight, int componentHeight) {
        return scaleCoordinate(event.getY(), logicalHeight, componentHeight);
    }

    private static int scaleCoordinate(int value, int logicalSize, int componentSize) {
        if (logicalSize <= 0 || componentSize <= 0) {
            return 0;
        }
        int scaled = value * logicalSize / componentSize;
        return Math.max(0, Math.min(logicalSize - 1, scaled));
    }

    private static final class DetachedFrame {
        private final DetachedSwingWindowManager owner;
        private final JFrame frame;
        private final DetachedPanel panel;
        private final AtomicReference<BufferedImage> pendingImage = new AtomicReference<>();
        private final AtomicBoolean imageDispatchQueued = new AtomicBoolean();
        private volatile boolean visible;
        private volatile boolean iconified;
        private volatile long viewportChangedAtNanos;
        private boolean windowLayoutRestored;
        private Rectangle normalBounds;

        private DetachedFrame(DetachedSwingWindowManager owner) {
            this.owner = owner;
            this.panel = new DetachedPanel(this);
            this.frame = new JFrame("MythosScript - 独立控制中心");
            this.frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            this.frame.setLayout(new java.awt.BorderLayout());
            this.frame.getContentPane().setBackground(PANEL_BACKGROUND);
            this.frame.add(panel, java.awt.BorderLayout.CENTER);
            this.frame.setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));
            this.frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    owner.requestCloseFromSwing();
                }

                @Override
                public void windowActivated(WindowEvent event) {
                    owner.lastCaptureNanos = 0L;
                    panel.requestFocusInWindow();
                }

                @Override
                public void windowDeactivated(WindowEvent event) {
                    panel.releaseKeys();
                    owner.requestCancelInteractions();
                }
            });
            this.frame.addWindowStateListener(event -> iconified = (event.getNewState() & Frame.ICONIFIED) != 0);
            this.frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent event) {
                    captureNormalBounds();
                    panel.updateViewportSize();
                }

                @Override
                public void componentResized(ComponentEvent event) {
                    captureNormalBounds();
                    panel.updateViewportSize();
                }
            });
        }

        private void showWindow(int logicalWidth, int logicalHeight,
                MainUiLayoutManager.DetachedWindowLayout storedLayout) {
            owner.detachedUiScalePercent = storedLayout.uiScalePercent < MIN_UI_SCALE_PERCENT
                    || storedLayout.uiScalePercent > MAX_UI_SCALE_PERCENT
                            ? DEFAULT_UI_SCALE_PERCENT : normalizeUiScalePercent(storedLayout.uiScalePercent);
            updateScaleTitle();
            panel.setLogicalSize(logicalWidth, logicalHeight);
            if (!windowLayoutRestored) {
                restoreWindowLayout(logicalWidth, logicalHeight, storedLayout);
                windowLayoutRestored = true;
            }
            frame.setVisible(true);
            visible = true;
            iconified = (frame.getExtendedState() & Frame.ICONIFIED) != 0;
            captureNormalBounds();
            panel.updateViewportSize();
            frame.toFront();
            panel.requestFocusInWindow();
        }

        private void focusWindow() {
            Runnable task = () -> {
                if (!visible) {
                    frame.setVisible(true);
                    visible = true;
                }
                if (frame.getExtendedState() == Frame.ICONIFIED) {
                    frame.setExtendedState(Frame.NORMAL);
                }
                frame.toFront();
                frame.requestFocus();
                panel.requestFocusInWindow();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
        }

        private void hideWindow() {
            Runnable task = () -> {
                persistWindowLayout();
                releaseKeys();
                visible = false;
                frame.setVisible(false);
            };
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
        }

        private void disposeWindow() {
            Runnable task = () -> {
                persistWindowLayout();
                releaseKeys();
                visible = false;
                frame.dispose();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
        }

        private Dimension getViewportSize() {
            return panel.getViewportSize();
        }

        private Dimension getRenderPixelSize() {
            Dimension viewport = getViewportSize();
            GraphicsConfiguration configuration = frame.getGraphicsConfiguration();
            double scaleX = configuration == null ? 1.0D
                    : Math.max(1.0D, configuration.getDefaultTransform().getScaleX());
            double scaleY = configuration == null ? 1.0D
                    : Math.max(1.0D, configuration.getDefaultTransform().getScaleY());
            return new Dimension(Math.max(1, (int) Math.round(viewport.width * scaleX)),
                    Math.max(1, (int) Math.round(viewport.height * scaleY)));
        }

        private boolean isCaptureAllowed() {
            // Use live AWT state as the source of truth. Activation callbacks
            // only trigger an immediate frame; they never keep an unfocused
            // window capturing because an earlier focus event was missed.
            boolean liveVisible = visible && frame.isVisible();
            boolean liveIconified = iconified || (frame.getExtendedState() & Frame.ICONIFIED) != 0;
            boolean liveFocused = frame.isFocused() || frame.isActive();
            return liveVisible && liveFocused && !liveIconified;
        }

        private boolean isViewportResizeSettled(long now) {
            return now - viewportChangedAtNanos >= RESIZE_SETTLE_NANOS;
        }

        private void markViewportChanged() {
            viewportChangedAtNanos = System.nanoTime();
        }

        private void onUiScaleChanged() {
            updateScaleTitle();
            panel.updateViewportSize();
            markViewportChanged();
            owner.requestCancelInteractions();
        }

        private void updateScaleTitle() {
            frame.setTitle("MythosScript - 独立控制中心 (" + owner.detachedUiScalePercent + "%)");
        }

        private void restoreWindowLayout(int logicalWidth, int logicalHeight,
                MainUiLayoutManager.DetachedWindowLayout stored) {
            Rectangle requested;
            if (stored.isValid()) {
                requested = new Rectangle(stored.x, stored.y, stored.width, stored.height);
            } else {
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                int width = Math.max(900,
                        Math.min(1400, logicalWidth * owner.detachedUiScalePercent / 100));
                int height = Math.max(600,
                        Math.min(900, logicalHeight * owner.detachedUiScalePercent / 100));
                requested = new Rectangle((screen.width - width) / 2, (screen.height - height) / 2, width, height);
            }
            frame.setBounds(clampToVisibleScreen(requested));
            normalBounds = new Rectangle(frame.getBounds());
            if (stored.isValid() && stored.maximized) {
                frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
            }
        }

        private void persistWindowLayout() {
            Rectangle bounds = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0
                    ? normalBounds : frame.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                return;
            }
            owner.persistWindowLayout(bounds, (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0);
        }

        private void captureNormalBounds() {
            if ((frame.getExtendedState() & (Frame.MAXIMIZED_BOTH | Frame.ICONIFIED)) != 0) {
                return;
            }
            Rectangle bounds = frame.getBounds();
            if (bounds.width > 0 && bounds.height > 0) {
                normalBounds = new Rectangle(bounds);
            }
        }

        private static Rectangle clampToVisibleScreen(Rectangle requested) {
            GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            GraphicsConfiguration best = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
            long bestIntersection = -1L;
            for (GraphicsDevice device : devices) {
                GraphicsConfiguration configuration = device.getDefaultConfiguration();
                Rectangle intersection = requested.intersection(usableBounds(configuration));
                long area = Math.max(0, intersection.width) * (long) Math.max(0, intersection.height);
                if (area > bestIntersection) {
                    bestIntersection = area;
                    best = configuration;
                }
            }
            Rectangle usable = usableBounds(best);
            int width = Math.min(Math.max(MIN_WINDOW_WIDTH, requested.width), usable.width);
            int height = Math.min(Math.max(MIN_WINDOW_HEIGHT, requested.height), usable.height);
            int x = Math.max(usable.x, Math.min(requested.x, usable.x + usable.width - width));
            int y = Math.max(usable.y, Math.min(requested.y, usable.y + usable.height - height));
            return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
        }

        private static Rectangle usableBounds(GraphicsConfiguration configuration) {
            Rectangle bounds = new Rectangle(configuration.getBounds());
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            bounds.x += insets.left;
            bounds.y += insets.top;
            bounds.width = Math.max(1, bounds.width - insets.left - insets.right);
            bounds.height = Math.max(1, bounds.height - insets.top - insets.bottom);
            return bounds;
        }

        private void publish(BufferedImage image) {
            pendingImage.set(image);
            scheduleImageDispatch();
        }

        private void publishError(String message) {
            Runnable task = () -> panel.setError(message);
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
        }

        private void scheduleImageDispatch() {
            if (!imageDispatchQueued.compareAndSet(false, true)) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    BufferedImage image = pendingImage.getAndSet(null);
                    if (image != null) {
                        panel.setImage(image);
                    }
                } finally {
                    imageDispatchQueued.set(false);
                    if (pendingImage.get() != null) {
                        scheduleImageDispatch();
                    }
                }
            });
        }

        private void releaseKeys() {
            panel.releaseKeys();
        }
    }

    private static final class DetachedPanel extends JPanel {
        private final DetachedFrame owner;
        private final Set<Integer> pressedKeys = new HashSet<>();
        private volatile BufferedImage image;
        private volatile String errorMessage;
        private volatile int logicalWidth = 1;
        private volatile int logicalHeight = 1;
        private final AtomicReference<Dimension> viewportSize = new AtomicReference<>(new Dimension());
        private long lastMousePressAt;

        private DetachedPanel(DetachedFrame owner) {
            this.owner = owner;
            setFocusable(true);
            setOpaque(true);
            setBackground(PANEL_BACKGROUND);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    updateViewportSize();
                    owner.owner.requestCancelInteractions();
                }

                @Override
                public void componentShown(ComponentEvent event) {
                    updateViewportSize();
                }
            });
            installListeners();
        }

        private void installListeners() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    updateMouse(event);
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    updateMouse(event);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    owner.owner.virtualMouseX = -1;
                    owner.owner.virtualMouseY = -1;
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    owner.frame.requestFocus();
                    requestFocusInWindow();
                    updateMouse(event);
                    int button = mapMouseButton(event.getButton());
                    if (button >= 0) {
                        lastMousePressAt = System.currentTimeMillis();
                        owner.owner.dispatchMousePressed(logicalX(event), logicalY(event), button);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    updateMouse(event);
                    int button = buttonFromModifiers(event.getModifiersEx());
                    if (button >= 0) {
                        long elapsed = Math.max(0L, System.currentTimeMillis() - lastMousePressAt);
                        owner.owner.dispatchMouseMove(logicalX(event), logicalY(event), button, elapsed);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    updateMouse(event);
                    int button = mapMouseButton(event.getButton());
                    if (button >= 0) {
                        owner.owner.dispatchMouseReleased(logicalX(event), logicalY(event), button);
                    }
                }

                private int buttonFromModifiers(int modifiers) {
                    if ((modifiers & MouseEvent.BUTTON1_DOWN_MASK) != 0) return 0;
                    if ((modifiers & MouseEvent.BUTTON3_DOWN_MASK) != 0) return 1;
                    if ((modifiers & MouseEvent.BUTTON2_DOWN_MASK) != 0) return 2;
                    return -1;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener((MouseWheelEvent event) -> {
                updateMouse(event);
                int wheel = -event.getWheelRotation() * 120;
                owner.owner.dispatchMouseWheel(logicalX(event), logicalY(event), wheel);
            });

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    int code = mapKeyCode(event.getKeyCode());
                    Set<Integer> modifiers = new HashSet<>();
                    if (event.isControlDown()) {
                        modifiers.add(Keyboard.KEY_LCONTROL);
                    }
                    if (event.isShiftDown()) {
                        modifiers.add(Keyboard.KEY_LSHIFT);
                    }
                    if (event.isAltDown()) {
                        modifiers.add(Keyboard.KEY_LMENU);
                    }
                    if (code == Keyboard.KEY_NONE) {
                        return;
                    }
                    synchronized (pressedKeys) {
                        pressedKeys.add(code);
                    }
                    SimulatedKeyInputManager.setExternalKeyState(code, true);
                    owner.owner.dispatchKey(code, (char) 0, modifiers);
                }

                @Override
                public void keyReleased(KeyEvent event) {
                    int code = mapKeyCode(event.getKeyCode());
                    if (code == Keyboard.KEY_NONE) {
                        return;
                    }
                    synchronized (pressedKeys) {
                        pressedKeys.remove(code);
                    }
                    SimulatedKeyInputManager.setExternalKeyState(code, false);
                }

                @Override
                public void keyTyped(KeyEvent event) {
                    char typed = event.getKeyChar();
                    if (typed != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(typed)
                            && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
                        owner.owner.dispatchKey(Keyboard.KEY_NONE, typed, Collections.<Integer>emptySet());
                    }
                }
            });
        }

        private void updateMouse(MouseEvent event) {
            owner.owner.virtualMouseX = logicalX(event);
            owner.owner.virtualMouseY = logicalY(event);
        }

        private int logicalX(MouseEvent event) {
            return toLogicalX(event, logicalWidth, getWidth());
        }

        private int logicalY(MouseEvent event) {
            return toLogicalY(event, logicalHeight, getHeight());
        }

        private void setLogicalSize(int width, int height) {
            logicalWidth = Math.max(1, width);
            logicalHeight = Math.max(1, height);
        }

        private void updateViewportSize() {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            Dimension size = new Dimension(width, height);
            Dimension previous = viewportSize.getAndSet(size);
            Dimension logicalSize = owner.owner.toLogicalSize(size);
            setLogicalSize(logicalSize.width, logicalSize.height);
            if (previous.width != size.width || previous.height != size.height) {
                owner.markViewportChanged();
            }
        }

        private Dimension getViewportSize() {
            return viewportSize.get();
        }

        private void setImage(BufferedImage nextImage) {
            image = nextImage;
            errorMessage = null;
            repaint();
        }

        private void setError(String message) {
            errorMessage = message;
            repaint();
        }

        private void releaseKeys() {
            Set<Integer> keys;
            synchronized (pressedKeys) {
                keys = new HashSet<>(pressedKeys);
                pressedKeys.clear();
            }
            for (Integer key : keys) {
                SimulatedKeyInputManager.setExternalKeyState(key, false);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(PANEL_BACKGROUND);
                g.fillRect(0, 0, getWidth(), getHeight());
                BufferedImage current = image;
                if (current != null) {
                    // Frames already contain native device pixels, including
                    // antialiased text. Do not smooth them a second time when
                    // fractional DPI rounding or a pending resize changes the mapping.
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(current, 0, 0, getWidth(), getHeight(), null);
                } else {
                    g.setColor(new Color(243, 247, 250));
                    g.drawString(errorMessage == null ? "等待 Minecraft GUI 渲染..." : errorMessage,
                            Math.max(16, getWidth() / 2 - 110), Math.max(24, getHeight() / 2));
                }
            } finally {
                g.dispose();
            }
        }
    }
}
