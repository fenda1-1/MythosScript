package com.zszl.zszlScriptMod.gui.modern.legacy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import com.zszl.zszlScriptMod.gui.config.GuiProfileManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernTextInputFocus;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.path.GuiNodeEditor;
import com.zszl.zszlScriptMod.gui.path.GuiNodeEditorHotkeyManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.EmbeddedGuiScreenInputBridge;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Hosts the active profile and node workbenches inside the modern shell.
 * This class owns framing, clipping, scaling,
 * coordinate conversion, and nested-screen interception.
 */
public final class EmbeddedLegacyWorkbenchTab implements ModernSettingsTab {

    private final Minecraft minecraft;
    private final ReturnTargetScreen returnTarget = new ReturnTargetScreen();
    private final GuiScreen rootScreen;
    private final Deque<GuiScreen> nestedScreens = new ArrayDeque<>();
    private EmbeddedWorkbenchLayout.Layout layout = EmbeddedWorkbenchLayout.calculate(
            new EmbeddedWorkbenchLayout.Rect(0, 0, 1, 1));
    private GuiScreen activeScreen;
    private GuiScreen initializedScreen;
    private GuiScreen modernHost;
    private int initializedWidth = -1;
    private int initializedHeight = -1;
    private int capturedMouseButton = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private boolean initialized;
    private boolean returnRequested;

    private EmbeddedLegacyWorkbenchTab(Minecraft minecraft, String title, ScreenFactory factory) {
        this.minecraft = minecraft;
        this.rootScreen = factory.create(returnTarget);
        this.activeScreen = rootScreen;
    }

    public static ModernSettingsTab nodeEditor(Minecraft minecraft) {
        return workbench(minecraft, "节点编辑器", GuiNodeEditor::new);
    }

    public static ModernSettingsTab nodeHotkeys(Minecraft minecraft) {
        return workbench(minecraft, "节点编辑器快捷键", GuiNodeEditorHotkeyManager::new);
    }

    public static ModernSettingsTab profile(Minecraft minecraft) {
        return workbench(minecraft, "配置管理", GuiProfileManager::new);
    }

    private static ModernSettingsTab workbench(Minecraft minecraft, String title, ScreenFactory factory) {
        return new EmbeddedLegacyWorkbenchTab(minecraft, title, factory);
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        // The logical viewport is only known during draw.
    }

    @Override
    public void updateScreen() {
        reconcileScreenTransition();
        if (!initialized || activeScreen == null) {
            return;
        }
        dispatchScreenUpdate();
        reconcileScreenTransition();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        layout = EmbeddedWorkbenchLayout.calculateEmbedded(toLayoutRect(contentBounds));
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        ensureModernHost();
        reconcileScreenTransition();
        ensureLegacyInitialized();
        drawFrame(fontRenderer, mouseX, mouseY);
        if (!initialized || activeScreen == null) {
            return;
        }

        // The global decorator clears anchors for the modern host, not for the
        // temporary legacy screen used during this nested draw. Clear the
        // nested frame explicitly so resized panes cannot leave stale hit
        // regions behind.
        ModernTooltipSupport.clearRegisteredInfoIcons(activeScreen);
        ModernUiRenderer.beginClip(toModernRect(layout.viewport));
        ModernUiRenderer.pushClipTransform(layout.canvas.x, layout.canvas.y, layout.scale);
        GlStateManager.pushMatrix();
        Minecraft activeMinecraft = resolveMinecraft();
        GuiScreen previousScreen = activeMinecraft == null ? null : activeMinecraft.currentScreen;
        if (activeMinecraft != null) {
            // Legacy renderers register their anchors through Minecraft's current
            // screen. Give them their own coordinate space while drawing, then
            // restore the modern host before the next top-level pass.
            activeMinecraft.currentScreen = activeScreen;
        }
        try {
            GlStateManager.translate(layout.canvas.x, layout.canvas.y, 0.0F);
            GlStateManager.scale(layout.scale, layout.scale, 1.0F);
            int logicalMouseX = layout.containsCanvas(mouseX, mouseY) ? layout.toLogicalX(mouseX) : -1;
            int logicalMouseY = layout.containsCanvas(mouseX, mouseY) ? layout.toLogicalY(mouseY) : -1;
            activeScreen.drawScreen(logicalMouseX, logicalMouseY, 0.0F);
            // A nested screen does not receive Forge's top-level draw event,
            // so decorate generic buttons/fields here while still in its
            // logical coordinate system. The host resolves and paints the
            // delayed panel separately in physical coordinates.
            ModernTooltipSupport.drawEmbeddedControlIcons(activeScreen, logicalMouseX, logicalMouseY);
        } finally {
            GlStateManager.popMatrix();
            ModernUiRenderer.popClipTransform();
            ModernUiRenderer.endClip();
            if (activeMinecraft != null
                    && (activeMinecraft.currentScreen == activeScreen || activeMinecraft.currentScreen == null)) {
                activeMinecraft.currentScreen = previousScreen;
            }
        }
        reconcileScreenTransition();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!layout.frame.contains(mouseX, mouseY)) {
            return false;
        }
        if (!layout.containsCanvas(mouseX, mouseY) || !ensureLegacyInitialized()) {
            return true;
        }
        int logicalX = layout.toLogicalX(mouseX);
        int logicalY = layout.toLogicalY(mouseY);
        if (ModernTooltipSupport.isRegisteredInfoIconHit(activeScreen, logicalX, logicalY)) {
            // Information labels are passive and must never activate the
            // button or text field underneath them.
            return true;
        }
        capturedMouseButton = mouseButton;
        return dispatchClick(logicalX, logicalY, mouseButton);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        boolean captured = capturedMouseButton == clickedMouseButton;
        if (!captured && !layout.containsCanvas(mouseX, mouseY)) {
            return false;
        }
        if (!ensureLegacyInitialized()) {
            return captured;
        }
        dispatchMouseMove(layout.toLogicalX(mouseX), layout.toLogicalY(mouseY), clickedMouseButton, timeSinceLastClick);
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        boolean captured = capturedMouseButton == state;
        if (!captured && !layout.containsCanvas(mouseX, mouseY)) {
            return false;
        }
        try {
            if (ensureLegacyInitialized()) {
                dispatchMouseRelease(layout.toLogicalX(mouseX), layout.toLogicalY(mouseY), state);
            }
        } finally {
            if (captured) {
                capturedMouseButton = -1;
            }
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!ensureLegacyInitialized()) {
            return false;
        }
        return dispatchKey(typedChar, keyCode);
    }

    @Override
    public void save() {
        if (!ensureLegacyInitialized() || activeScreen == null) {
            return;
        }
        try {
            beginLegacyDispatch();
            EmbeddedGuiScreenInputBridge.invokeSaveButton(activeScreen);
        } catch (IOException ignored) {
            // The legacy screen owns its save error reporting. Keep the global
            // shortcut path from breaking the modern host when it rejects the action.
        } finally {
            reconcileScreenTransition();
        }
    }

    @Override
    public boolean isTextInputFocused() {
        return activeScreen != null && ModernTextInputFocus.isFocused(activeScreen);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0 || !layout.frame.contains(mouseX, mouseY)) {
            return false;
        }
        if (!layout.containsCanvas(mouseX, mouseY) || !ensureLegacyInitialized()) {
            return true;
        }
        return dispatchWheel(layout.toLogicalX(mouseX), layout.toLogicalY(mouseY));
    }

    @Override
    public boolean handleEscape() {
        if (!ensureLegacyInitialized()) {
            returnToParentLayer();
            return true;
        }
        return dispatchKey('\0', Keyboard.KEY_ESCAPE);
    }

    @Override
    public boolean consumeReturnRequest() {
        boolean requested = returnRequested;
        returnRequested = false;
        return requested;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return layout.frame.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        if (activeScreen == null || !initialized || !layout.containsCanvas(mouseX, mouseY)
                || ModernTooltipSupport.isSuppressed(activeScreen)) {
            return "";
        }
        int logicalX = layout.toLogicalX(mouseX);
        int logicalY = layout.toLogicalY(mouseY);
        return ModernTooltipSupport.getTooltipAt(activeScreen, logicalX, logicalY);
    }

    @Override
    public void discardDraft() {
        nestedScreens.clear();
        activeScreen = rootScreen;
        initializedScreen = null;
        initialized = false;
        capturedMouseButton = -1;
        returnRequested = false;
    }

    private void drawFrame(FontRenderer fontRenderer, int mouseX, int mouseY) {
        EmbeddedWorkbenchLayout.Rect frame = layout.frame;
        EmbeddedWorkbenchLayout.Rect viewport = layout.viewport;
        ModernUiRenderer.drawPanel(frame.x, frame.y, frame.width, frame.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        // Leave the viewport unframed. The legacy workbench paints its tree and
        // editor panels, while this single outer frame is the host boundary.
        if (viewport.width > 0 && viewport.height > 0) {
            ModernUiRenderer.drawRoundedRect(viewport.x, viewport.y, viewport.width, viewport.height, 4,
                    ModernUiRenderer.SHELL);
        }
    }

    private boolean ensureLegacyInitialized() {
        Minecraft activeMinecraft = resolveMinecraft();
        if (activeMinecraft == null || activeScreen == null) {
            return false;
        }
        if (initializedScreen == activeScreen && initializedWidth == layout.logicalWidth
                && initializedHeight == layout.logicalHeight) {
            return initialized;
        }
        GuiScreen target = activeScreen;

        beginLegacyDispatch();
        target.setWorldAndResolution(activeMinecraft, layout.logicalWidth, layout.logicalHeight);
        reconcileScreenTransition();
        if (activeScreen != target) {
            return ensureLegacyInitialized();
        }
        initializedScreen = target;
        initializedWidth = layout.logicalWidth;
        initializedHeight = layout.logicalHeight;
        initialized = true;
        return true;
    }

    private void returnToParentLayer() {
        if (!nestedScreens.isEmpty()) {
            activeScreen = nestedScreens.pop();
            initializedScreen = null;
            initialized = false;
            return;
        }
        returnRequested = true;
    }

    private boolean dispatchClick(int mouseX, int mouseY, int mouseButton) {
        try {
            beginLegacyDispatch();
            EmbeddedGuiScreenInputBridge.mouseClicked(activeScreen, mouseX, mouseY, mouseButton);
            return true;
        } catch (IOException exception) {
            return false;
        } finally {
            reconcileScreenTransition();
        }
    }

    private void dispatchMouseMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        try {
            beginLegacyDispatch();
            EmbeddedGuiScreenInputBridge.mouseClickMove(activeScreen, mouseX, mouseY, mouseButton, timeSinceLastClick);
        } finally {
            reconcileScreenTransition();
        }
    }

    private void dispatchMouseRelease(int mouseX, int mouseY, int state) {
        try {
            beginLegacyDispatch();
            EmbeddedGuiScreenInputBridge.mouseReleased(activeScreen, mouseX, mouseY, state);
        } finally {
            reconcileScreenTransition();
        }
    }

    private boolean dispatchKey(char typedChar, int keyCode) {
        try {
            beginLegacyDispatch();
            EmbeddedGuiScreenInputBridge.keyTyped(activeScreen, typedChar, keyCode);
            return true;
        } catch (IOException exception) {
            return false;
        } finally {
            reconcileScreenTransition();
        }
    }

    private boolean dispatchWheel(int mouseX, int mouseY) {
        Minecraft activeMinecraft = resolveMinecraft();
        if (activeMinecraft == null) {
            return false;
        }
        int originalDisplayWidth = activeMinecraft.displayWidth;
        int originalDisplayHeight = activeMinecraft.displayHeight;
        try {
            beginLegacyDispatch();
            activeMinecraft.displayWidth = displayDimension(Mouse.getEventX(), activeScreen.width, mouseX);
            int logicalFromBottom = Math.max(0, activeScreen.height - 1 - mouseY);
            activeMinecraft.displayHeight = displayDimension(Mouse.getEventY(), activeScreen.height, logicalFromBottom);
            EmbeddedGuiScreenInputBridge.handleMouseInput(activeScreen);
            return true;
        } catch (IOException exception) {
            return false;
        } finally {
            activeMinecraft.displayWidth = originalDisplayWidth;
            activeMinecraft.displayHeight = originalDisplayHeight;
            reconcileScreenTransition();
        }
    }

    private void dispatchScreenUpdate() {
        try {
            beginLegacyDispatch();
            activeScreen.updateScreen();
        } finally {
            reconcileScreenTransition();
        }
    }

    private void beginLegacyDispatch() {
        Minecraft activeMinecraft = resolveMinecraft();
        if (activeMinecraft == null || activeScreen == null) {
            return;
        }
        ensureModernHost();
        if (modernHost != null) {
            activeMinecraft.currentScreen = activeScreen;
        }
    }

    private void reconcileScreenTransition() {
        Minecraft activeMinecraft = resolveMinecraft();
        if (activeMinecraft == null) {
            return;
        }
        ensureModernHost();
        // During the first tick Forge may temporarily clear currentScreen
        // before the modern host has been observed. Do not interpret that
        // transient state as a user-requested return from the embedded tab.
        if (modernHost == null) {
            return;
        }
        GuiScreen currentScreen = activeMinecraft.currentScreen;
        if (currentScreen == null) {
            return;
        }
        if (currentScreen == returnTarget) {
            returnToParentLayer();
        } else if (currentScreen != modernHost && currentScreen != activeScreen) {
            if (!nestedScreens.isEmpty() && currentScreen == nestedScreens.peek()) {
                activeScreen = nestedScreens.pop();
            } else {
                nestedScreens.push(activeScreen);
                activeScreen = currentScreen;
            }

            initializedScreen = null;
            initialized = false;
        }
        if (modernHost != null && activeMinecraft.currentScreen != modernHost) {
            activeMinecraft.currentScreen = modernHost;
            Keyboard.enableRepeatEvents(true);
        }
    }

    private void ensureModernHost() {
        Minecraft activeMinecraft = resolveMinecraft();
        if (modernHost == null && activeMinecraft != null && activeMinecraft.currentScreen != null
                && activeMinecraft.currentScreen != returnTarget && activeMinecraft.currentScreen != activeScreen) {
            modernHost = activeMinecraft.currentScreen;
        }
    }

    private Minecraft resolveMinecraft() {
        return minecraft == null ? Minecraft.getMinecraft() : minecraft;
    }

    private static EmbeddedWorkbenchLayout.Rect toLayoutRect(ModernMainLayout.Rect bounds) {
        return bounds == null ? new EmbeddedWorkbenchLayout.Rect(0, 0, 1, 1)
                : new EmbeddedWorkbenchLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private static ModernMainLayout.Rect toModernRect(EmbeddedWorkbenchLayout.Rect bounds) {
        return new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private static int displayDimension(int eventCoordinate, int logicalSize, int logicalCoordinate) {
        int target = Math.max(0, Math.min(Math.max(1, logicalSize) - 1, logicalCoordinate));
        long numerator = (long) Math.max(0, eventCoordinate) * Math.max(1, logicalSize);
        if (target == 0) {
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, numerator + 1L));
        }
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, numerator / target));
    }

    private interface ScreenFactory {
        GuiScreen create(GuiScreen parent);
    }

    private static final class ReturnTargetScreen extends GuiScreen {
    }
}
