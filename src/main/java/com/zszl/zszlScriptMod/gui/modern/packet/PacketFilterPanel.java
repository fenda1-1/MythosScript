package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.gui.FontRenderer;

/** Full packet capture filter editor with explicit working-copy semantics. */
final class PacketFilterPanel extends PacketPanelBase {
    private final PacketWorkbenchState.Filter state = new PacketWorkbenchState.Filter(PacketFilterConfig.INSTANCE);
    private final PacketTextField whitelist = new PacketTextField(7101, 32767);
    private final PacketTextField blacklist = new PacketTextField(7102, 32767);
    private final PacketTextField max = new PacketTextField(7103, 8);
    private final PacketTextField threshold = new PacketTextField(7104, 8);
    private final PacketTextField modulo = new PacketTextField(7105, 4);
    private ModernMainLayout.Rect modeBounds, businessBounds, adaptiveBounds, saveBounds, cancelBounds;
    private ModernMainLayout.Rect contentBounds;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final PacketDropdown modeDrop = new PacketDropdown("gui.modern.pktflt.u005", "gui.modern.pktflt.u003");
    private int scrollOffset, maxScrollOffset, lastMouseX, lastMouseY;
    private String validation = "";
    private boolean saved;

    PacketFilterPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktflt.u001"); }
    @Override protected void initializePanel() {
        whitelist.ensure(font); blacklist.ensure(font); max.ensure(font); threshold.ensure(font); modulo.ensure(font);
        whitelist.setText(join(state.whitelist())); blacklist.setText(join(state.blacklist())); max.setText(String.valueOf(state.max()));
        threshold.setText(String.valueOf(state.threshold())); modulo.setText(String.valueOf(state.modulo()));
        registerDropdown(modeDrop);
        modeDrop.setSelected(state.mode() == PacketCaptureHandler.CaptureMode.WHITELIST ? 1 : 0);
    }
    @Override public void updateScreen() { whitelist.update(); blacklist.update(); max.update(); threshold.update(); modulo.update(); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int x = area.x + 14, width = Math.max(1, area.width - 28), top = area.y + 42;
        int footer = area.bottom() - 30;
        boolean compact = ModernHoverScrollbar.contentWidth(width) < 360;
        int controlHeight = compact ? 84 : 22;
        int contentHeight = 24 + 5 * 46 + 4 + controlHeight + 8;
        contentBounds = new ModernMainLayout.Rect(x, top, width, Math.max(1, footer - top - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        maxScrollOffset = Math.max(0, contentHeight - contentBounds.height);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        ModernUiRenderer.beginClip(contentBounds);
        int y = top - scrollOffset;
        text(font, "gui.modern.pktflt.u002", x, y,
                ModernUiRenderer.MUTED_TEXT, width);
        y += 24;
        y = drawFieldRow(font, "gui.modern.pktflt.u003", "gui.modern.pktflt.u004", whitelist, x, y, width);
        y = drawFieldRow(font, "gui.modern.pktflt.u005", "gui.modern.pktflt.u006", blacklist, x, y, width);
        y = drawFieldRow(font, "gui.modern.pktflt.u007", "gui.modern.pktflt.u008", max, x, y, width);
        y = drawFieldRow(font, "gui.modern.pktflt.u009", "gui.modern.pktflt.u010", threshold, x, y, width);
        y = drawFieldRow(font, "gui.modern.pktflt.u011", "gui.modern.pktflt.u012", modulo, x, y, width);
        int controlY = y + 4;
        if (compact) {
            int controlWidth = Math.max(1, width);
            modeBounds = new ModernMainLayout.Rect(x, controlY, controlWidth, 22);
            businessBounds = new ModernMainLayout.Rect(x, controlY + 28, controlWidth, 22);
            adaptiveBounds = new ModernMainLayout.Rect(x, controlY + 56, controlWidth, 22);
        } else {
            int controlWidth = Math.max(1, (width - 12) / 3);
            modeBounds = new ModernMainLayout.Rect(x, controlY, controlWidth, 22);
            businessBounds = new ModernMainLayout.Rect(modeBounds.right() + 6, controlY, controlWidth, 22);
            adaptiveBounds = new ModernMainLayout.Rect(businessBounds.right() + 6, controlY,
                    Math.max(1, x + width - businessBounds.right() - 6), 22);
        }
        state.setMode(modeDrop.selected() == 1 ? PacketCaptureHandler.CaptureMode.WHITELIST : PacketCaptureHandler.CaptureMode.BLACKLIST);
        modeDrop.drawButton(font, modeBounds, mouseX, mouseY);
        drawToggle(font, businessBounds, tr("gui.modern.pktflt.fmt.business", tr(state.business() ? "gui.modern.pktflt.u013" : "gui.modern.pktflt.u014")), mouseX, mouseY, state.business());
        drawToggle(font, adaptiveBounds, tr("gui.modern.pktflt.fmt.adaptive", tr(state.adaptive() ? "gui.modern.pktflt.u013" : "gui.modern.pktflt.u014")), mouseX, mouseY, state.adaptive());
        if (!validation.isEmpty()) {
            text(font, validation, x, controlY + controlHeight + 2, ModernUiRenderer.WARNING, width);
        }
        ModernUiRenderer.endClip();
        drawScrollbar(mouseX, mouseY);
        saveBounds = new ModernMainLayout.Rect(x, footer, Math.max(80, width / 2 - 4), 22);
        cancelBounds = new ModernMainLayout.Rect(saveBounds.right() + 8, saveBounds.y, Math.max(80, width - saveBounds.width - 8), 22);
        drawButton(font, saveBounds, "gui.modern.pktflt.u015", mouseX, mouseY, true, true);
        drawButton(font, cancelBounds, "gui.modern.pktflt.u016", mouseX, mouseY, true, false);
    }

    private int drawFieldRow(FontRenderer font, String label, String hint, PacketTextField field, int x, int y, int width) {
        int height = 40, fieldWidth = Math.max(120, Math.min(360, width / 2));
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, label, x + 10, y + 7, ModernUiRenderer.TEXT, Math.max(60, width - fieldWidth - 28));
        text(font, hint, x + 10, y + 23, ModernUiRenderer.MUTED_TEXT, Math.max(60, width - fieldWidth - 28));
        this.field(field, new ModernMainLayout.Rect(x + width - fieldWidth - 10, y + 10, fieldWidth, 20), null);
        return y + height + 6;
    }

    private void drawToggle(FontRenderer font, ModernMainLayout.Rect r, String label, int mx, int my, boolean selected) {
        boolean hover = r.contains(mx, my); ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4,
                hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE, selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        centeredLabel(font, label, r, ModernUiRenderer.TEXT);
    }

    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button != 0) return true;
        if (fieldClick(whitelist, x, y, button) || fieldClick(blacklist, x, y, button) || fieldClick(max, x, y, button)
                || fieldClick(threshold, x, y, button) || fieldClick(modulo, x, y, button)) { focusOnly(x, y); return true; }
        if (businessBounds != null && businessBounds.contains(x, y)) { state.setBusiness(!state.business()); return true; }
        if (adaptiveBounds != null && adaptiveBounds.contains(x, y)) { state.setAdaptive(!state.adaptive()); return true; }
        if (scrollbar.beginDrag(x, y)) { return true; }
        if (saveBounds != null && saveBounds.contains(x, y)) { if (saveDraft()) owner.back(); return true; }
        if (cancelBounds != null && cancelBounds.contains(x, y)) { owner.requestBack(); return true; }
        return true;
    }

    private void focusOnly(int x, int y) { whitelist.focus(whitelist.hit(x, y)); blacklist.focus(blacklist.hit(x, y)); max.focus(max.hit(x, y)); threshold.focus(threshold.hit(x, y)); modulo.focus(modulo.hit(x, y)); }
    private boolean sync() {
        Integer maxValue = parseBound(max.text(), 100, 10000, "gui.modern.pktflt.u007");
        Integer thresholdValue = parseBound(threshold.text(), 200, 10000, "gui.modern.pktflt.u009");
        Integer moduloValue = parseBound(modulo.text(), 2, 64, "gui.modern.pktflt.u011");
        if (maxValue == null || thresholdValue == null || moduloValue == null) {
            return false;
        }
        state.setWhitelist(whitelist.text());
        state.setBlacklist(blacklist.text());
        state.setMax(maxValue.intValue());
        state.setThreshold(thresholdValue.intValue());
        state.setModulo(moduloValue.intValue());
        validation = "";
        return true;
    }

    private boolean saveDraft() {
        if (!sync()) {
            owner.status(validation);
            return false;
        }
        state.save();
        saved = true;
        owner.status("gui.modern.pktflt.u017");
        return true;
    }

    private Integer parseBound(String value, int min, int max, String label) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed < min || parsed > max) {
                validation = tr("gui.modern.pktflt.fmt.bound", tr(label), String.valueOf(min), String.valueOf(max));
                return null;
            }
            return Integer.valueOf(parsed);
        } catch (NumberFormatException ignored) {
            validation = tr("gui.modern.pktflt.fmt.bound", tr(label), String.valueOf(min), String.valueOf(max));
            return null;
        }
    }

    @Override public void save() { saveDraft(); }

    @Override public boolean isDirty() {
        return !join(state.whitelist()).equals(whitelist.text()) || !join(state.blacklist()).equals(blacklist.text())
                || !String.valueOf(state.max()).equals(max.text().trim())
                || !String.valueOf(state.threshold()).equals(threshold.text().trim())
                || !String.valueOf(state.modulo()).equals(modulo.text().trim()) || state.isDirty();
    }
    @Override public boolean keyTyped(char c, int code) { if (fieldKey(whitelist, c, code) || fieldKey(blacklist, c, code) || fieldKey(max, c, code) || fieldKey(threshold, c, code) || fieldKey(modulo, c, code)) return true; if (code == Keyboard.KEY_RETURN) { sync(); return true; } return false; }
    @Override public void discardDraft() {
        if (!saved) state.cancel();
        whitelist.setText(join(state.whitelist())); blacklist.setText(join(state.blacklist())); max.setText(String.valueOf(state.max()));
        threshold.setText(String.valueOf(state.threshold())); modulo.setText(String.valueOf(state.modulo()));
        validation = "";
        PacketTextField.clearActiveFocus();
        scrollbar.endDrag();
    }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    private static String join(List<String> values) { return values == null ? "" : String.join(", ", values); }

    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || contentBounds == null || !contentBounds.contains(lastMouseX, lastMouseY)) return false;
        int before = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset + (wheel > 0 ? -30 : 30)));
        return before != scrollOffset;
    }

    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (button == 0 && scrollbar.isDragging()) { scrollbar.applyDrag(x, y); return true; }
        return false;
    }

    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button == 0 && scrollbar.isDragging()) { scrollbar.endDrag(); return true; }
        return false;
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (contentBounds == null || maxScrollOffset <= 0) {
            scrollbar.idle();
            return;
        }
        scrollbar.draw(contentBounds, scrollOffset, maxScrollOffset, contentBounds.height,
                contentBounds.height + maxScrollOffset, mouseX, mouseY, value -> scrollOffset = value);
    }
}
