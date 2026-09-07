package com.zszl.zszlScriptMod.gui.packet;

import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class InputTimelineManager {

    public static final class InputEventRecord {
        private final long timestamp;
        private final String timestampText;
        private final String category;
        private final String action;
        private final String detail;
        private final int sessionId;
        private final String guiTitle;
        private final String screenSimpleName;

        private InputEventRecord(long timestamp, String category, String action, String detail,
                int sessionId, String guiTitle, String screenSimpleName) {
            this.timestamp = timestamp;
            this.timestampText = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
            this.category = category == null ? "" : category;
            this.action = action == null ? "" : action;
            this.detail = detail == null ? "" : detail;
            this.sessionId = sessionId;
            this.guiTitle = guiTitle == null ? "" : guiTitle;
            this.screenSimpleName = screenSimpleName == null ? "" : screenSimpleName;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getTimestampText() {
            return timestampText;
        }

        public String getCategory() {
            return category;
        }

        public String getAction() {
            return action;
        }

        public String getDetail() {
            return detail;
        }

        public int getSessionId() {
            return sessionId;
        }

        public String getGuiTitle() {
            return guiTitle;
        }

        public String getScreenSimpleName() {
            return screenSimpleName;
        }
    }

    private static final int MAX_EVENTS = 400;
    private static final List<InputEventRecord> EVENTS = new ArrayList<>();
    private static int nextSessionId = 0;
    private static String lastSessionKey = "";

    private InputTimelineManager() {
    }

    public static synchronized void recordKeyPress(int keyCode) {
        if (!PacketCaptureHandler.isCapturing) {
            return;
        }
        if (keyCode <= 0) {
            return;
        }
        String keyName = Keyboard.getKeyName(keyCode);
        SessionInfo session = resolveCurrentSession();
        addEvent(new InputEventRecord(System.currentTimeMillis(), "键盘", "按键按下", keyName,
                session.sessionId, session.guiTitle, session.screenSimpleName));
    }

    public static synchronized void recordMouseClick(int button) {
        if (!PacketCaptureHandler.isCapturing) {
            return;
        }
        String detail;
        switch (button) {
            case 0:
                detail = "左键";
                break;
            case 1:
                detail = "右键";
                break;
            case 2:
                detail = "中键";
                break;
            default:
                detail = "按钮 " + button;
                break;
        }
        SessionInfo session = resolveCurrentSession();
        addEvent(new InputEventRecord(System.currentTimeMillis(), "鼠标", "点击", detail,
                session.sessionId, session.guiTitle, session.screenSimpleName));
    }

    public static synchronized List<InputEventRecord> getEventsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(EVENTS));
    }

    public static synchronized void clear() {
        EVENTS.clear();
        lastSessionKey = "";
        nextSessionId = 1;
    }

    private static void addEvent(InputEventRecord event) {
        EVENTS.add(0, event);
        while (EVENTS.size() > MAX_EVENTS) {
            EVENTS.remove(EVENTS.size() - 1);
        }
    }

    private static final class SessionInfo {
        private final int sessionId;
        private final String guiTitle;
        private final String screenSimpleName;

        private SessionInfo(int sessionId, String guiTitle, String screenSimpleName) {
            this.sessionId = sessionId;
            this.guiTitle = guiTitle;
            this.screenSimpleName = screenSimpleName;
        }
    }

    private static SessionInfo resolveCurrentSession() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        String guiTitle = GuiElementInspector.getCurrentGuiTitle(mc);
        String screenSimpleName = screen == null ? "Hud" : screen.getClass().getSimpleName();
        String stableTitle = guiTitle == null || guiTitle.trim().isEmpty() ? "(无标题)" : guiTitle.trim();
        String sessionKey = screenSimpleName + "|" + stableTitle;
        if (!sessionKey.equals(lastSessionKey)) {
            lastSessionKey = sessionKey;
            nextSessionId++;
        }
        return new SessionInfo(nextSessionId, stableTitle, screenSimpleName);
    }
}
