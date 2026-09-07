package com.zszl.zszlScriptMod.gui.components;

import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.lwjgl.input.Mouse;

@SideOnly(Side.CLIENT)
public class GlobalThemedButtonHandler {

    private static Field guiButtonListField;

    @SubscribeEvent
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        applyTheme(event.getGui(), event.getButtonList());
    }

    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        ModernTooltipSupport.clearRegisteredInfoIcons(event.getGui());
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (DetachedSwingWindowManager.isDetachedScreen(gui)) {
            return;
        }
        List<GuiButton> buttons = getButtonList(gui);
        applyTheme(gui, buttons);
        if (gui instanceof GuiModernMainScreen) {
            ((GuiModernMainScreen) gui).drawModernTooltips(event.getMouseX(), event.getMouseY());
        } else if (gui instanceof ThemedGuiScreen) {
            ThemedGuiScreen themed = (ThemedGuiScreen) gui;
            int mouseX = themed.toReadableMouseX(event.getMouseX());
            int mouseY = themed.toReadableMouseY(event.getMouseY());
            themed.pushReadableUiScale();
            try {
                ModernTooltipSupport.draw(gui, buttons, mouseX, mouseY);
            } finally {
                themed.popReadableUiScale();
            }
        } else {
            ModernTooltipSupport.draw(gui, buttons, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public void onMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (gui == null) {
            return;
        }
        List<GuiButton> buttons = getButtonList(gui);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        // A readable ThemedGuiScreen may intentionally expose a reduced
        // logical width/height. Use that same contract as GuiScreen's own
        // event dispatcher instead of the unscaled ScaledResolution size.
        int logicalWidth = Math.max(1, gui.width);
        int logicalHeight = Math.max(1, gui.height);
        int fallbackWidth = resolution.getScaledWidth();
        int fallbackHeight = resolution.getScaledHeight();
        if (logicalWidth <= 1 && fallbackWidth > 1) {
            logicalWidth = fallbackWidth;
        }
        if (logicalHeight <= 1 && fallbackHeight > 1) {
            logicalHeight = fallbackHeight;
        }
        int mouseX = Mouse.getX() * logicalWidth / minecraft.displayWidth;
        int mouseY = logicalHeight - Mouse.getY() * logicalHeight / minecraft.displayHeight - 1;
        if (gui instanceof GuiModernMainScreen) {
            GuiModernMainScreen modern = (GuiModernMainScreen) gui;
            mouseX = modern.toModernMouseX(mouseX);
            mouseY = modern.toModernMouseY(mouseY);
        }
        if (gui instanceof ThemedGuiScreen) {
            ThemedGuiScreen themed = (ThemedGuiScreen) gui;
            mouseX = themed.toReadableMouseX(mouseX);
            mouseY = themed.toReadableMouseY(mouseY);
        }
        boolean genericInfoHit = gui instanceof ThemedGuiScreen
                && !(gui instanceof ModernTooltipSupport.OwnsTooltipAnchors)
                && ModernTooltipSupport.isInfoIconHit(gui, buttons, mouseX, mouseY);
        if (genericInfoHit || ModernTooltipSupport.isRegisteredInfoIconHit(gui, mouseX, mouseY)) {
            // Information labels are passive controls. Consume the event so
            // the button or text field underneath cannot be activated.
            event.setCanceled(true);
        }
    }

    private void applyTheme(GuiScreen gui, List<GuiButton> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return;
        }

        Map<GuiButton, GuiButton> replaced = new IdentityHashMap<>();

        ListIterator<GuiButton> it = buttons.listIterator();
        while (it.hasNext()) {
            GuiButton old = it.next();
            if (!shouldTheme(gui, old)) {
                continue;
            }

            ThemedButton themed = new ThemedButton(old.id, old.x, old.y, old.width, old.height, old.displayString);
            themed.enabled = old.enabled;
            themed.visible = old.visible;
            themed.packedFGColour = old.packedFGColour;
            it.set(themed);
            replaced.put(old, themed);
        }

        if (!replaced.isEmpty()) {
            syncButtonFields(gui, replaced);
        }
    }

    private void syncButtonFields(GuiScreen gui, Map<GuiButton, GuiButton> replaced) {
        if (gui == null || replaced == null || replaced.isEmpty()) {
            return;
        }
        try {
            for (Class<?> c = gui.getClass(); c != null; c = c.getSuperclass()) {
                Field[] fields = c.getDeclaredFields();
                for (Field f : fields) {
                    if (!GuiButton.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object value = f.get(gui);
                    if (!(value instanceof GuiButton)) {
                        continue;
                    }
                    GuiButton mapped = replaced.get(value);
                    if (mapped != null) {
                        try {
                            f.set(gui, mapped);
                        } catch (Throwable ignored) {
                            // final 字段或安全限制时忽略
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 反射同步失败不影响主流程
        }
    }

    private boolean shouldTheme(GuiScreen gui, GuiButton button) {
        if (button == null) {
            return false;
        }
        if (button instanceof ThemedButton) {
            return false;
        }
        // 保留已有自定义按钮子类行为（如下拉、切换按钮等）
        if (button.getClass() != GuiButton.class) {
            return false;
        }

        if (gui != null) {
            String className = gui.getClass().getName();
            if (className.startsWith("com.zszl.zszlScriptMod.")) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private List<GuiButton> getButtonList(GuiScreen gui) {
        if (gui == null) {
            return null;
        }
        try {
            if (guiButtonListField == null) {
                guiButtonListField = GuiScreen.class.getDeclaredField("buttonList");
                guiButtonListField.setAccessible(true);
            }
            return (List<GuiButton>) guiButtonListField.get(gui);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
