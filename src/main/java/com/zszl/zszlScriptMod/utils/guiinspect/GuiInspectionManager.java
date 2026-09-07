package com.zszl.zszlScriptMod.utils.guiinspect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class GuiInspectionManager {

    public static final int MAX_HISTORY = 120;

    private static boolean captureEnabled = false;
    private static String lastSignature = "";
    private static final List<CapturedGuiSnapshot> history = new ArrayList<>();

    private GuiInspectionManager() {
    }

    public static final class CapturedGuiSnapshot {
        private final long timestamp;
        private final String timestampText;
        private final String screenClassName;
        private final String screenSimpleName;
        private final String title;
        private final int windowId;
        private final int totalSlots;
        private final int containerSlots;
        private final int playerInventorySlots;
        private final List<GuiElementInspector.GuiElementInfo> elements;
        private final String signature;

        private CapturedGuiSnapshot(long timestamp,
                String screenClassName,
                String screenSimpleName,
                String title,
                int windowId,
                int totalSlots,
                int containerSlots,
                int playerInventorySlots,
                List<GuiElementInspector.GuiElementInfo> elements,
                String signature) {
            this.timestamp = timestamp;
            this.timestampText = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
            this.screenClassName = screenClassName == null ? "" : screenClassName;
            this.screenSimpleName = screenSimpleName == null ? "" : screenSimpleName;
            this.title = title == null ? "" : title;
            this.windowId = windowId;
            this.totalSlots = totalSlots;
            this.containerSlots = containerSlots;
            this.playerInventorySlots = playerInventorySlots;
            this.elements = elements == null ? Collections.<GuiElementInspector.GuiElementInfo>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(elements));
            this.signature = signature == null ? "" : signature;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getTimestampText() {
            return timestampText;
        }

        public String getScreenClassName() {
            return screenClassName;
        }

        public String getScreenSimpleName() {
            return screenSimpleName;
        }

        public String getTitle() {
            return title;
        }

        public int getWindowId() {
            return windowId;
        }

        public int getTotalSlots() {
            return totalSlots;
        }

        public int getContainerSlots() {
            return containerSlots;
        }

        public int getPlayerInventorySlots() {
            return playerInventorySlots;
        }

        public List<GuiElementInspector.GuiElementInfo> getElements() {
            return elements;
        }

        public String getSignature() {
            return signature;
        }
    }

    public static boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public static void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
        if (!enabled) {
            lastSignature = "";
        }
    }

    public static void toggleCaptureEnabled() {
        setCaptureEnabled(!captureEnabled);
    }

    public static void clearHistory() {
        history.clear();
        lastSignature = "";
    }

    public static List<CapturedGuiSnapshot> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public static void onClientTick() {
        if (!captureEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        if (screen == null) {
            lastSignature = "";
            return;
        }
        String screenClassName = screen.getClass().getName();
        if (screenClassName.contains("GuiGuiInspectorManager")
                || screenClassName.contains("GuiModernMainScreen")) {
            return;
        }

        captureCurrentGui(mc, screen);
    }

    private static void captureCurrentGui(Minecraft mc, GuiScreen screen) {
        GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
        int windowId = -1;
        int totalSlots = 0;
        int containerSlots = 0;
        int playerInventorySlots = 0;

        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player != null && screen instanceof GuiContainer) {
            Container container = player.openContainer;
            if (container != null) {
                windowId = container.windowId;
                totalSlots = container.inventorySlots == null ? 0 : container.inventorySlots.size();
                for (Slot slot : container.inventorySlots) {
                    if (slot == null) {
                        continue;
                    }
                    if (slot.inventory == player.inventory && slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 36) {
                        playerInventorySlots++;
                    } else if (slot.inventory != player.inventory) {
                        containerSlots++;
                    }
                }
            }
        }

        String signature = buildSignature(snapshot, windowId, totalSlots, containerSlots, playerInventorySlots);
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;

        history.add(0, new CapturedGuiSnapshot(
                System.currentTimeMillis(),
                snapshot.getScreenClassName(),
                snapshot.getScreenSimpleName(),
                snapshot.getTitle(),
                windowId,
                totalSlots,
                containerSlots,
                playerInventorySlots,
                snapshot.getElements(),
                signature));
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    private static String buildSignature(GuiElementInspector.GuiSnapshot snapshot,
            int windowId,
            int totalSlots,
            int containerSlots,
            int playerInventorySlots) {
        StringBuilder sb = new StringBuilder();
        sb.append(snapshot.getScreenClassName()).append('|')
                .append(snapshot.getTitle()).append('|')
                .append(windowId).append('|')
                .append(totalSlots).append('|')
                .append(containerSlots).append('|')
                .append(playerInventorySlots);
        for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
            if (element == null) {
                continue;
            }
            sb.append('\n')
                    .append(element.getType().name())
                    .append('|').append(element.getPath())
                    .append('|').append(element.getText())
                    .append('|').append(element.getButtonId())
                    .append('|').append(element.getSlotIndex());
        }
        return sb.toString();
    }
}
