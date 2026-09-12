package com.zszl.zszlScriptMod.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort cache for temporarily hiding and later restoring the current GUI.
 * This preserves the current GuiScreen instance only on the client side.
 */
public final class GuiVisibilityHandler {

    private static final Map<String, GuiScreen> hiddenGuis = new LinkedHashMap<>();
    private static final Map<String, String> hiddenGuiLabels = new LinkedHashMap<>();
    private static String latestHiddenGuiId;

    private GuiVisibilityHandler() {
    }

    public static boolean hideCurrentGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null) {
            return false;
        }
        recordHiddenGui(mc.currentScreen, GuiElementInspector.getCurrentGuiTitle(mc));
        mc.displayGuiScreen(null);
        return true;
    }

    public static boolean showHiddenGui() {
        return showHiddenGui("");
    }

    public static boolean showHiddenGui(String id) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen guiToRestore = findHiddenGui(id);
        if (mc == null || guiToRestore == null) {
            return false;
        }
        mc.displayGuiScreen(guiToRestore);
        return true;
    }

    static synchronized String recordHiddenGui(GuiScreen screen, String title) {
        if (screen == null) return null;
        for (Map.Entry<String, GuiScreen> entry : hiddenGuis.entrySet()) {
            if (entry.getValue() == screen) {
                latestHiddenGuiId = entry.getKey();
                return latestHiddenGuiId;
            }
        }
        String id = UUID.randomUUID().toString();
        String name = screen.getClass().getSimpleName();
        if (name.isEmpty()) name = screen.getClass().getName();
        String label = title == null || title.trim().isEmpty() ? name : title + " (" + name + ")";
        hiddenGuis.put(id, screen);
        hiddenGuiLabels.put(id, label + " #" + hiddenGuis.size());
        latestHiddenGuiId = id;
        return id;
    }

    static synchronized GuiScreen findHiddenGui(String id) {
        return hiddenGuis.get(id == null || id.trim().isEmpty() ? latestHiddenGuiId : id);
    }

    public static synchronized Map<String, String> getHiddenGuiChoices() {
        return new LinkedHashMap<>(hiddenGuiLabels);
    }

    public static synchronized boolean hasHiddenGui() {
        return !hiddenGuis.isEmpty();
    }

    public static synchronized void reset() {
        hiddenGuis.clear();
        hiddenGuiLabels.clear();
        latestHiddenGuiId = null;
    }
}
