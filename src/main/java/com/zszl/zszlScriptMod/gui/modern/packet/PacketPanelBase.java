package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

/** Shared frame, header, button and text helpers for packet panels. */
abstract class PacketPanelBase extends ModernEmbeddedPanel {
    protected final PacketWorkbenchTab owner;
    protected FontRenderer font;
    protected ModernMainLayout.Rect area = new ModernMainLayout.Rect(0, 0, 1, 1);
    private final boolean backButton;
    private final List<PacketDropdown> dropdowns = new ArrayList<PacketDropdown>();
    private final PacketContextMenu contextMenu = new PacketContextMenu();
    private ModernMainLayout.Rect moreBounds;

    PacketPanelBase(PacketWorkbenchTab owner, String title) { this(owner, title, true); }
    PacketPanelBase(PacketWorkbenchTab owner, String title, boolean backButton) {
        super(title); this.owner = owner; this.backButton = backButton;
    }

    @Override protected final void onInitialize(FontRenderer fontRenderer) {
        font = fontRenderer;
        initializePanel();
    }

    protected void initializePanel() { }

    @Override public final void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        area = bounds == null ? area : bounds;
        setBounds(area);
        ModernUiRenderer.drawPanel(area.x, area.y, area.width, area.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        int iconX = area.x + (backButton ? 40 : 12);
        int titleX = iconX + 28;
        if (backButton) drawBack(mouseX, mouseY);
        ModernUiRenderer.drawRoundedRect(iconX, area.y + 7, 22, 22, 5, 0xFF2A4A63);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.PACKET, iconX + 3, area.y + 10, 16, ModernUiRenderer.ACCENT);
        int statusWidth = owner.status() == null || owner.status().isEmpty() ? 0
                : Math.min(220, Math.max(90, area.width / 3));
        statusWidth = Math.min(statusWidth, Math.max(0, area.width - (titleX - area.x) - 48));
        moreBounds = new ModernMainLayout.Rect(Math.max(titleX, area.right() - 36), area.y + 7, 24, 22);
        int titleWidth = Math.max(30, area.width - (titleX - area.x) - 54 - statusWidth);
        ModernUiRenderer.drawText(fontRenderer, getTitle(), titleX, area.y + 10, ModernUiRenderer.TEXT, titleWidth);
        if (statusWidth > 0) {
            int statusX = area.right() - statusWidth - 42;
            ModernUiRenderer.drawSubtlePanel(statusX, area.y + 7, statusWidth, 22, 5,
                    ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
            boolean error = owner.status().contains("gui.modern.pktbase.u001")
                    || owner.status().contains("gui.modern.pktbase.u002");
            ModernUiRenderer.drawStatusDot(statusX + 8, area.y + 15,
                    error ? 0xFFFF7777 : ModernUiRenderer.SUCCESS);
            ModernUiRenderer.drawText(fontRenderer, owner.status(), statusX + 20, area.y + 11,
                    error ? 0xFFFF9A9A : ModernUiRenderer.SUBTLE_TEXT, statusWidth - 26);
        }
        ModernUiRenderer.drawSubtlePanel(moreBounds.x, moreBounds.y, moreBounds.width, moreBounds.height, 5,
                moreBounds.contains(mouseX, mouseY) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                moreBounds.contains(mouseX, mouseY) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawMoreIcon(moreBounds.x + 6, moreBounds.y + 5,
                moreBounds.contains(mouseX, mouseY) ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawDivider(area.x + 10, area.y + 32, Math.max(1, area.width - 20), ModernUiRenderer.BORDER_SUBTLE);
        drawBody(fontRenderer, area, mouseX, mouseY);
        drawNavigationOverlay(mouseX, mouseY);
        drawDropdownMenus(fontRenderer, mouseX, mouseY);
        contextMenu.draw(fontRenderer, mouseX, mouseY);
    }

    protected abstract void drawBody(FontRenderer fontRenderer, ModernMainLayout.Rect area, int mouseX, int mouseY);

    protected void drawNavigationOverlay(int mouseX, int mouseY) { }
    protected boolean navigationOverlayOpen() { return false; }

    @Override public final boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (navigationOverlayOpen()) return handleBodyClick(mouseX, mouseY, mouseButton);
        if (contextMenu.isOpen()) return contextMenu.click(mouseX, mouseY, mouseButton);
        if (!area.contains(mouseX, mouseY) && !dropdownMenuContains(mouseX, mouseY)) return false;
        if (handleDropdownClick(mouseX, mouseY, mouseButton)) return true;
        if (backButton && mouseButton == 0 && backBounds().contains(mouseX, mouseY)) { owner.requestBack(); return true; }
        if (mouseButton == 0 && moreBounds != null && moreBounds.contains(mouseX, mouseY)) {
            openContextMenu(mouseX, mouseY, moreItems());
            return true;
        }
        return handleBodyClick(mouseX, mouseY, mouseButton);
    }

    protected boolean handleBodyClick(int mouseX, int mouseY, int mouseButton) { return true; }
    protected final void openContextMenu(int mouseX, int mouseY, List<PacketContextMenu.Item> items) {
        contextMenu.open(mouseX, mouseY, items, area);
    }
    protected List<PacketContextMenu.Item> moreItems() {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("返回", () -> owner.requestBack()));
        return items;
    }
    protected ModernMainLayout.Rect backBounds() { return new ModernMainLayout.Rect(area.x + 9, area.y + 7, 24, 22); }
    protected void drawBack(int mouseX, int mouseY) {
        ModernMainLayout.Rect r = backBounds();
        boolean hover = r.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 5,
                hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hover ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(r.x + 10, r.y + 7, false, hover ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
    }

    protected ModernMainLayout.Rect button(int row, int count) {
        int gap = 6; int pad = 12; int width = Math.max(1, (area.width - pad * 2 - gap * Math.max(0, count - 1)) / Math.max(1, count));
        return new ModernMainLayout.Rect(area.x + pad + row * (width + gap), area.bottom() - 30, width, 22);
    }
    protected ModernMainLayout.Rect buttonAt(int x, int y, int width, int height) {
        return new ModernMainLayout.Rect(x, y, Math.max(1, width), Math.max(1, height));
    }
    protected void drawButton(FontRenderer renderer, ModernMainLayout.Rect r, String label, int mouseX, int mouseY) {
        drawButton(renderer, r, label, mouseX, mouseY, true, false);
    }
    protected void drawButton(FontRenderer renderer, ModernMainLayout.Rect r, String label, int mouseX, int mouseY,
            boolean enabled, boolean primary) {
        if (r == null) return;
        boolean hover = enabled && r.contains(mouseX, mouseY);
        boolean pressed = hover && Mouse.isButtonDown(0);
        int fill = !enabled ? 0xFF151E26 : primary ? (pressed ? 0xFFD9577D : hover ? 0xFFFF86A7 : ModernUiRenderer.ACCENT)
                : (pressed ? ModernUiRenderer.SURFACE_PRESSED : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE);
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE : primary ? ModernUiRenderer.ACCENT
                : (hover ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4, fill, border);
        if (com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label)) {
            com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(renderer, label, r.x + 4, r.y + (r.height - renderer.FONT_HEIGHT) / 2, ModernUiRenderer.readableText(ModernUiRenderer.TEXT, fill), r.width - 8);
            return;
        }
        String value = ModernFormI18n.tr(label);
        int textColor = !enabled ? ModernUiRenderer.MUTED_TEXT : primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT;
        int textWidth = renderer == null ? 0 : renderer.getStringWidth(value);
        int available = Math.max(4, r.width - 8);
        float scale = textWidth > available && textWidth > 0 ? (float) available / textWidth : 1.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(r.x + r.width / 2.0F, r.y + r.height / 2.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        if (renderer != null) renderer.drawString(value, -textWidth / 2.0F, -renderer.FONT_HEIGHT / 2.0F, textColor, false);
        GlStateManager.popMatrix();
    }
    protected void text(FontRenderer renderer, String value, int x, int y, int color, int width) {
        ModernUiRenderer.drawText(renderer, value == null ? "" : value, x, y, color, Math.max(1, width));
    }
    protected void centeredLabel(FontRenderer renderer, String value, ModernMainLayout.Rect bounds, int color) {
        if (renderer == null || bounds == null) return;
        String text = ModernFormI18n.tr(value);
        int width = renderer.getStringWidth(text);
        int available = Math.max(4, bounds.width - 10);
        float scale = width > available && width > 0 ? (float) available / width : 1.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(bounds.x + bounds.width / 2.0F, bounds.y + bounds.height / 2.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        renderer.drawString(text, -width / 2.0F, -renderer.FONT_HEIGHT / 2.0F, color, false);
        GlStateManager.popMatrix();
    }
    protected void field(PacketTextField field, ModernMainLayout.Rect r, String value) {
        if (field == null) return;
        field.setBounds(r); field.draw();
    }
    protected boolean fieldClick(PacketTextField field, int x, int y, int button) { return field != null && field.click(x, y, button); }
    protected boolean fieldKey(PacketTextField field, char c, int code) { return field != null && field.key(c, code); }
    protected void updateField(PacketTextField field) { if (field != null) field.update(); }
    protected static boolean hit(ModernMainLayout.Rect r, int x, int y) { return r != null && r.contains(x, y); }
    protected static int parseInt(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value == null ? "" : value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    protected void registerDropdown(PacketDropdown dropdown) {
        if (dropdown != null && !dropdowns.contains(dropdown)) dropdowns.add(dropdown);
    }

    protected void closeDropdowns() {
        for (int i = 0; i < dropdowns.size(); i++) dropdowns.get(i).close();
    }

    private void drawDropdownMenus(FontRenderer fontRenderer, int mouseX, int mouseY) {
        for (int i = 0; i < dropdowns.size(); i++) {
            dropdowns.get(i).drawMenu(fontRenderer, area, mouseX, mouseY);
        }
    }

    private boolean dropdownMenuContains(int mouseX, int mouseY) {
        for (int i = 0; i < dropdowns.size(); i++) {
            if (dropdowns.get(i).isOpen()) return true;
        }
        return false;
    }

    private boolean handleDropdownClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || dropdowns.isEmpty()) return false;
        PacketDropdown opened = null;
        for (int i = 0; i < dropdowns.size(); i++) {
            if (dropdowns.get(i).isOpen()) opened = dropdowns.get(i);
        }
        if (opened != null && opened.click(mouseX, mouseY)) {
            for (int i = 0; i < dropdowns.size(); i++) {
                if (dropdowns.get(i) != opened) dropdowns.get(i).close();
            }
            return true;
        }
        for (int i = 0; i < dropdowns.size(); i++) {
            PacketDropdown dropdown = dropdowns.get(i);
            if (dropdown.click(mouseX, mouseY)) {
                for (int j = 0; j < dropdowns.size(); j++) {
                    if (dropdowns.get(j) != dropdown) dropdowns.get(j).close();
                }
                return true;
            }
        }
        return false;
    }

    protected String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    protected String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
