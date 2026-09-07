package com.zszl.zszlScriptMod.gui.modern.packet;

import org.lwjgl.input.Mouse;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

/** Capture dashboard and entry point for every packet child workflow. */
final class PacketOverviewPanel extends PacketPanelBase {
    private static final String[] ACTIONS = {
            "gui.modern.pktover.u001", "gui.modern.pktover.u002", "gui.modern.pktover.u003",
            "gui.modern.pktover.u004", "gui.modern.pktover.u005", "gui.modern.pktover.u006",
            "gui.modern.pktover.u007", "gui.modern.pktover.u008", "gui.modern.pktover.u009"
    };
    private static final String[] ACTION_HINTS = {
            "gui.modern.pktover.u041", "gui.modern.pktover.u042", "gui.modern.pktover.u043",
            "gui.modern.pktover.u044", "gui.modern.pktover.u045", "gui.modern.pktover.u046",
            "gui.modern.pktover.u047", "gui.modern.pktover.u048", "gui.modern.pktover.u049"
    };
    private static final ModernUiRenderer.Icon[] ACTION_ICONS = {
            ModernUiRenderer.Icon.PACKET, ModernUiRenderer.Icon.SETTINGS, ModernUiRenderer.Icon.FLOW,
            ModernUiRenderer.Icon.STOP, ModernUiRenderer.Icon.DEBUG, ModernUiRenderer.Icon.GENERIC,
            ModernUiRenderer.Icon.MEMORY, ModernUiRenderer.Icon.ROUTE, ModernUiRenderer.Icon.COMMAND
    };
    private static final int[] ACTION_BADGES = {
            0xFF2A4A63, 0xFF3A3348, 0xFF2F4A3A, 0xFF4A3030, 0xFF3A3A28, 0xFF2E3F4A, 0xFF3A2E48, 0xFF2E4840, 0xFF4A3A2E
    };
    private static final int CARD_HEIGHT = 50;
    private static final int CARD_GAP = 8;
    private static final int CONTROL_HEIGHT = 50;

    private ModernMainLayout.Rect workflowBounds;
    private ModernMainLayout.Rect captureBounds, modeBounds, businessBounds, clearBounds;
    private int workflowScroll, workflowMaxScroll, lastMouseX, lastMouseY;
    private boolean draggingWorkflowScrollbar;
    private final ModernHoverScrollbar workflowScrollbar = new ModernHoverScrollbar();

    PacketOverviewPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktover.u010", false); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        PacketCaptureHandler.PacketCaptureUiSnapshot snapshot = PacketCaptureHandler.getUiSnapshot();
        boolean capturing = PacketCaptureHandler.isCapturing;
        boolean whitelist = PacketFilterConfig.INSTANCE.captureMode == PacketCaptureHandler.CaptureMode.WHITELIST;
        boolean business = snapshot.businessProcessingEnabled;
        int x = area.x + 14;
        int width = Math.max(1, area.width - 28);
        int y = area.y + 42;
        int footer = area.bottom() - 18;

        y = drawHero(font, x, y, width, snapshot, capturing, whitelist, business, mouseX, mouseY) + 10;
        y = drawControls(font, x, y, width, capturing, whitelist, business, mouseX, mouseY) + 12;
        text(font, "gui.modern.pktover.u022", x, y, ModernUiRenderer.TEXT, width);
        y += 16;
        drawWorkflows(font, x, y, width, Math.max(1, footer - y), mouseX, mouseY);
        text(font, tr("gui.modern.pktover.fmt.recent", String.valueOf(owner.capturedPackets().size())),
                x, footer, ModernUiRenderer.MUTED_TEXT, width);
    }

    private int drawHero(FontRenderer font, int x, int y, int width,
            PacketCaptureHandler.PacketCaptureUiSnapshot snapshot, boolean capturing, boolean whitelist,
            boolean business, int mouseX, int mouseY) {
        int height = 86;
        int fill = capturing ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SURFACE;
        int border = capturing ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawPanel(x, y, width, height, 7, fill, border);
        if (capturing) {
            ModernUiRenderer.drawRoundedRect(x, y + 10, 4, height - 20, 2, pulse(ModernUiRenderer.ACCENT));
        }
        int statusWidth = width >= 620 ? 168 : Math.max(120, width / 4);
        int statusX = x + 16;
        ModernUiRenderer.drawStatusDot(statusX, y + 16, capturing ? pulse(ModernUiRenderer.SUCCESS) : ModernUiRenderer.MUTED_TEXT);
        text(font, "gui.modern.pktover.u011", statusX + 14, y + 13, ModernUiRenderer.MUTED_TEXT, statusWidth - 20);
        text(font, capturing ? "gui.modern.pktover.u012" : "gui.modern.pktover.u013", statusX, y + 32,
                capturing ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING, statusWidth);
        text(font, tr("gui.modern.pktover.fmt.mode", tr(whitelist ? "gui.modern.pktover.u020" : "gui.modern.pktover.u021"),
                tr(business ? "gui.modern.pktover.u014" : "gui.modern.pktover.u015")),
                statusX, y + 50, ModernUiRenderer.SUBTLE_TEXT, statusWidth);
        text(font, capturing ? "gui.modern.pktover.u035" : "gui.modern.pktover.u036", statusX, y + 66,
                ModernUiRenderer.MUTED_TEXT, statusWidth);

        int metricX = x + statusWidth + 24;
        int metricWidth = Math.max(1, x + width - metricX - 12);
        int tileGap = 8;
        int tiles = 4;
        int tileWidth = Math.max(1, (metricWidth - tileGap * (tiles - 1)) / tiles);
        String[] labels = { "gui.modern.pktover.u037", "gui.modern.pktover.u038", "gui.modern.pktover.u039", "gui.modern.pktover.u040" };
        String[] values = {
                String.valueOf(snapshot.sentCount), String.valueOf(snapshot.receivedCount),
                String.valueOf(snapshot.queueSize), String.valueOf(snapshot.droppedCount)
        };
        int[] accents = { 0xFF6AA9FF, ModernUiRenderer.SUCCESS, ModernUiRenderer.WARNING, 0xFFFF7777 };
        for (int i = 0; i < tiles; i++) {
            ModernMainLayout.Rect tile = new ModernMainLayout.Rect(metricX + i * (tileWidth + tileGap), y + 12, tileWidth, height - 24);
            boolean hover = tile.contains(mouseX, mouseY);
            int tileFill = hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
            ModernUiRenderer.drawSubtlePanel(tile.x, tile.y, tile.width, tile.height, 5,
                    tileFill, hover ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(tile.x + 8, tile.y + 8, 7, 7, 3, accents[i]);
            text(font, labels[i], tile.x + 20, tile.y + 8,
                    ModernUiRenderer.readableText(ModernUiRenderer.MUTED_TEXT, tileFill), tile.width - 28);
            drawMetricValue(font, values[i], tile.x + 10, tile.y + 28, accents[i], tile.width - 20);
        }
        return y + height;
    }

    private int drawControls(FontRenderer font, int x, int y, int width, boolean capturing, boolean whitelist,
            boolean business, int mouseX, int mouseY) {
        int columns = width >= 640 ? 4 : 2;
        int gap = 8;
        int cardWidth = Math.max(1, (width - gap * (columns - 1)) / columns);
        captureBounds = controlCard(x, y, cardWidth, gap, columns, 0);
        modeBounds = controlCard(x, y, cardWidth, gap, columns, 1);
        businessBounds = controlCard(x, y, cardWidth, gap, columns, 2);
        clearBounds = controlCard(x, y, cardWidth, gap, columns, 3);
        drawSwitchCard(font, captureBounds, "gui.modern.pktover.u023",
                capturing ? "gui.modern.pktover.u012" : "gui.modern.pktover.u013", capturing, true, mouseX, mouseY);
        drawSwitchCard(font, modeBounds, "gui.modern.pktover.u024",
                whitelist ? "gui.modern.pktover.u020" : "gui.modern.pktover.u021", whitelist, false, mouseX, mouseY);
        drawSwitchCard(font, businessBounds, "gui.modern.pktover.u018",
                business ? "gui.modern.pktover.u014" : "gui.modern.pktover.u015", business, true, mouseX, mouseY);
        drawClearCard(font, clearBounds, mouseX, mouseY);
        int rows = (4 + columns - 1) / columns;
        return y + rows * (CONTROL_HEIGHT + gap) - gap;
    }

    private ModernMainLayout.Rect controlCard(int x, int y, int cardWidth, int gap, int columns, int index) {
        int row = index / columns, column = index % columns;
        return new ModernMainLayout.Rect(x + column * (cardWidth + gap), y + row * (CONTROL_HEIGHT + gap),
                cardWidth, CONTROL_HEIGHT);
    }

    private void drawSwitchCard(FontRenderer font, ModernMainLayout.Rect r, String title, String value, boolean on,
            boolean showSwitch, int mouseX, int mouseY) {
        boolean hover = r.contains(mouseX, mouseY);
        boolean pressed = hover && Mouse.isButtonDown(0);
        int fill = pressed ? ModernUiRenderer.SURFACE_PRESSED : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = on ? ModernUiRenderer.ACCENT : hover ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 6, fill, border);
        if (on) {
            ModernUiRenderer.drawRoundedRect(r.x, r.y + 8, 3, r.height - 16, 1, ModernUiRenderer.ACCENT);
        }
        int textWidth = Math.max(40, r.width - (showSwitch ? 56 : 68));
        text(font, title, r.x + 12, r.y + 9, ModernUiRenderer.MUTED_TEXT, textWidth);
        if (showSwitch) {
            text(font, value, r.x + 12, r.y + 26, on ? ModernUiRenderer.SUCCESS : ModernUiRenderer.TEXT, textWidth);
            ModernUiRenderer.drawToggle(r.right() - 44, r.y + 17, 32, 16, on, hover);
        } else {
            text(font, "gui.modern.pktover.u051", r.x + 12, r.y + 26, ModernUiRenderer.SUBTLE_TEXT, textWidth);
            ModernUiRenderer.drawSubtlePanel(r.right() - 62, r.y + 14, 50, 22, 4,
                    on ? 0xFF2C3D49 : ModernUiRenderer.SHELL_RAISED, on ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, value, r.right() - 56, r.y + 20, on ? ModernUiRenderer.SUCCESS : ModernUiRenderer.TEXT, 40);
        }
    }

    private void drawClearCard(FontRenderer font, ModernMainLayout.Rect r, int mouseX, int mouseY) {
        boolean hover = r.contains(mouseX, mouseY);
        boolean pressed = hover && Mouse.isButtonDown(0);
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 6,
                pressed ? 0xFF3A2228 : hover ? 0xFF2A1C22 : ModernUiRenderer.SURFACE,
                hover ? 0xFFFF7777 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(r.x + 12, r.y + 17, 16, 16, 4, 0xFF4A3030);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.CLOSE, r.x + 13, r.y + 18, 14, 0xFFFF9A9A);
        text(font, "gui.modern.pktover.u019", r.x + 36, r.y + 12, ModernUiRenderer.TEXT, r.width - 48);
        text(font, "gui.modern.pktover.u050", r.x + 36, r.y + 28, ModernUiRenderer.MUTED_TEXT, r.width - 48);
    }

    private void drawWorkflows(FontRenderer font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int columns = workflowColumns();
        int rows = (ACTIONS.length + columns - 1) / columns;
        int contentHeight = Math.max(1, rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP);
        workflowBounds = new ModernMainLayout.Rect(x, y, width, height);
        workflowMaxScroll = Math.max(0, contentHeight - height);
        workflowScroll = Math.max(0, Math.min(workflowScroll, workflowMaxScroll));
        ModernUiRenderer.beginClip(workflowBounds);
        for (int i = 0; i < ACTIONS.length; i++) {
            ModernMainLayout.Rect r = workflowCard(i, x, y, width, columns);
            boolean hover = workflowBounds.contains(mouseX, mouseY) && r.contains(mouseX, mouseY);
            boolean pressed = hover && Mouse.isButtonDown(0);
            ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 6,
                    pressed ? ModernUiRenderer.SURFACE_PRESSED : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hover ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            if (hover) {
                ModernUiRenderer.drawRoundedRect(r.x, r.y + 8, 3, r.height - 16, 1, ModernUiRenderer.ACCENT);
            }
            ModernUiRenderer.drawRoundedRect(r.x + 10, r.y + 9, 32, 32, 6, ACTION_BADGES[i]);
            ModernUiRenderer.drawIcon(ACTION_ICONS[i], r.x + 18, r.y + 17, 16, hover ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT);
            text(font, ACTIONS[i], r.x + 50, r.y + 12, ModernUiRenderer.TEXT, r.width - 72);
            text(font, ACTION_HINTS[i], r.x + 50, r.y + 28, ModernUiRenderer.MUTED_TEXT, r.width - 72);
            ModernUiRenderer.drawChevron(r.right() - 18, r.y + 21, true,
                    hover ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        }
        ModernUiRenderer.endClip();
        drawWorkflowScrollbar(mouseX, mouseY);
    }

    private ModernMainLayout.Rect workflowCard(int index, int x, int y, int width, int columns) {
        int cardWidth = Math.max(1, (ModernHoverScrollbar.contentWidth(width) - CARD_GAP * (columns - 1)) / columns);
        int row = index / columns, col = index % columns;
        return new ModernMainLayout.Rect(x + col * (cardWidth + CARD_GAP),
                y + row * (CARD_HEIGHT + CARD_GAP) - workflowScroll, cardWidth, CARD_HEIGHT);
    }

    private int workflowColumns() {
        return area.width >= 760 ? 3 : area.width >= 520 ? 2 : 1;
    }

    private void drawMetricValue(FontRenderer font, String value, int x, int y, int color, int width) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(1.35F, 1.35F, 1.0F);
        ModernUiRenderer.drawText(font, value, 0, 0, color, Math.max(1, Math.round(width / 1.35F)));
        GlStateManager.popMatrix();
    }

    private static int pulse(int color) {
        return (System.currentTimeMillis() / 420L & 1L) == 0L ? color : 0xFF89F0C0;
    }

    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button != 0) return true;
        if (hit(captureBounds, x, y)) { owner.toggleCapture(); return true; }
        if (hit(modeBounds, x, y)) { owner.cycleCaptureMode(); return true; }
        if (hit(businessBounds, x, y)) { owner.toggleBusinessProcessing(); return true; }
        if (hit(clearBounds, x, y)) { owner.clearCapture(); return true; }
        if (workflowScrollbar.beginDrag(x, y)) {
            draggingWorkflowScrollbar = true;
            return true;
        }
        if (workflowBounds == null || !workflowBounds.contains(x, y)) return true;
        int columns = workflowColumns();
        for (int i = 0; i < ACTIONS.length; i++) {
            if (workflowCard(i, workflowBounds.x, workflowBounds.y, workflowBounds.width, columns).contains(x, y)) {
                openWorkflow(i);
                return true;
            }
        }
        return true;
    }

    private void openWorkflow(int index) {
        switch (index) {
            case 0: owner.openViewer(); break;
            case 1: owner.openFilter(); break;
            case 2: owner.openFieldRules(); break;
            case 3: owner.openInterceptRules(); break;
            case 4: owner.openCapturedIds(); break;
            case 5: owner.openCapturedIdGenerator(); break;
            case 6: owner.openSnapshots(); break;
            case 7: owner.openSequenceManager(); break;
            case 8: owner.openSequenceEditor(null); break;
            default: break;
        }
    }

    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || workflowBounds == null || !workflowBounds.contains(lastMouseX, lastMouseY)) return false;
        int before = workflowScroll;
        workflowScroll = Math.max(0, Math.min(workflowMaxScroll, workflowScroll + (wheel > 0 ? -36 : 36)));
        return before != workflowScroll;
    }

    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (draggingWorkflowScrollbar && button == 0) { workflowScrollbar.applyDrag(x, y); return true; }
        return false;
    }

    @Override public boolean mouseReleased(int x, int y, int button) {
        if (draggingWorkflowScrollbar && button == 0) { draggingWorkflowScrollbar = false; workflowScrollbar.endDrag(); return true; }
        return false;
    }

    private void drawWorkflowScrollbar(int mouseX, int mouseY) {
        if (workflowBounds == null || workflowMaxScroll <= 0) {
            workflowScrollbar.idle();
            return;
        }
        workflowScrollbar.draw(workflowBounds, workflowScroll, workflowMaxScroll, workflowBounds.height,
                workflowBounds.height + workflowMaxScroll, mouseX, mouseY, value -> workflowScroll = value);
    }
}
