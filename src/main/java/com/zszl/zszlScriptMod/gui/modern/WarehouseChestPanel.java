package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import com.zszl.zszlScriptMod.gui.modern.form.ModernPathSequencePicker;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler;
import com.zszl.zszlScriptMod.handlers.WarehouseManager;
import com.zszl.zszlScriptMod.system.dungeon.ChestData;
import com.zszl.zszlScriptMod.system.dungeon.Warehouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import java.util.*;

/** Live editor for the warehouse policy attached to the open chest. */
public final class WarehouseChestPanel {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ChestData chest;
    private final ModernTextField search;
    private final ModernTextField spread;
    private final ModernPathSequencePicker sequencePicker;
    private final Map<String, ModernMainLayout.Rect> hits = new LinkedHashMap<>();
    private final Map<String, ItemStack> candidates = new LinkedHashMap<>();
    private List<String> filtered = new ArrayList<>();
    private ModernMainLayout.Rect bounds, listBounds;
    private int scroll, suggestionScroll, selected = -1, dragging = -1, rows;
    private boolean suggestions, settings, slots;
    private boolean compactExpanded;
    private int settingsScroll;
    private long lastCandidateRefresh;

    public WarehouseChestPanel(ChestData chest) {
        this.chest = chest;
        search = field(9710, "gui.modern.warehouse.search_items");
        spread = field(9711, "gui.modern.warehouse.spread_names");
        sequencePicker = new ModernPathSequencePicker(name -> {
            chest.postDepositSequence = name == null ? "" : name.trim();
            save();
        });
        sequencePicker.ensureInitialized(mc.fontRenderer);
        spread.setText(chest.spreadItemNames == null ? "" : chest.spreadItemNames);
        refreshCandidates();
    }

    private ModernTextField field(int id, String placeholder) {
        ModernTextField field = new ModernTextField(id, mc.fontRenderer, 0, 0, 1, 20);
        field.setMaxStringLength(1024);
        field.setPlaceholder(I18n.format(placeholder));
        return field;
    }

    public void draw(GuiScreen gui, int mouseX, int mouseY) {
        hits.clear();
        int available = (gui.width - 176) / 2 - 12;
        if (available < 132 && !compactExpanded) {
            bounds = new ModernMainLayout.Rect(2, 8, 24, 24);
            listBounds = new ModernMainLayout.Rect(0, 0, 0, 0);
            icon("expand", ModernUiRenderer.Icon.STORAGE, 2, 8, mouseX, mouseY);
            return;
        }
        int width = Math.max(48, Math.min(236, available));
        if (available < 132) width = Math.min(236, gui.width - 12);
        int height = Math.max(120, Math.min(420, gui.height - 16));
        int x = Math.max(2, (gui.width - 176) / 2 - width - 6);
        if (available < 132) x = (gui.width - width) / 2;
        int y = Math.max(2, (gui.height - height) / 2);
        bounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 6, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text("gui.modern.warehouse.u078", x + 8, y + 9, width - (available < 132 ? 68 : 38));
        icon("settings", ModernUiRenderer.Icon.SETTINGS, x + width - 28, y + 4, mouseX, mouseY);
        if (available < 132) icon("collapse", ModernUiRenderer.Icon.CLOSE, x + width - 54, y + 4, mouseX, mouseY);
        int inner = Math.max(24, width - 16);
        search.layout(new ModernMainLayout.Rect(x + 8, y + 28, inner, 22), 5, 5);
        search.draw(mc.fontRenderer, mouseX, mouseY);
        button("add", "gui.modern.warehouse.add_item", x + 8, y + 55, (inner - 4) / 2, mouseX, mouseY);
        button("update", "gui.modern.warehouse.update_item", x + 10 + inner / 2, y + 55, (inner - 4) / 2, mouseX, mouseY);
        int bottom = y + height - 58;
        rows = Math.max(1, (bottom - (y + 82)) / 28);
        listBounds = new ModernMainLayout.Rect(x + 8, y + 82, inner, rows * 28);
        ModernUiRenderer.beginClip(listBounds);
        try {
        if (settings) {
            int contentHeight = slots ? 56 + Math.max(1, inner / 9) * 4 : 145;
            settingsScroll = Math.max(0, Math.min(settingsScroll, Math.max(0, contentHeight - listBounds.height)));
            drawSettings(x + 8, y + 83 - settingsScroll, inner, mouseX, mouseY);
        } else if (suggestions) {
            if (System.currentTimeMillis() - lastCandidateRefresh > 1500) refreshCandidates();
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            filtered = new ArrayList<>();
            for (String name : candidates.keySet()) if (name.toLowerCase(Locale.ROOT).contains(query)) filtered.add(name);
            suggestionScroll = Math.max(0, Math.min(suggestionScroll, Math.max(0, filtered.size() - rows)));
            for (int i = 0; i < rows && i + suggestionScroll < filtered.size(); i++) {
                String name = filtered.get(i + suggestionScroll);
                drawItem("suggest_" + (i + suggestionScroll), name, candidates.get(name), x + 8, y + 82 + i * 28,
                        inner, false, mouseX, mouseY);
            }
            scrollbar(filtered.size(), suggestionScroll);
        } else {
            List<String> names = WarehouseEventHandler.orderedDepositNames(chest);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - rows)));
            for (int i = 0; i < rows && i + scroll < names.size(); i++) {
                int index = i + scroll;
                String name = names.get(index);
                drawItem("item_" + index, (index + 1) + ". " + name, candidates.get(name), x + 8,
                        y + 82 + i * 28, inner - 24, selected == index, mouseX, mouseY);
                icon("delete_" + index, ModernUiRenderer.Icon.CLOSE, x + width - 32, y + 83 + i * 28, mouseX, mouseY);
            }
            scrollbar(names.size(), scroll);
        }
        } finally {
        ModernUiRenderer.endClip();
        if (sequencePicker.isOpen()) {
            sequencePicker.updateScreen();
            sequencePicker.draw(mc.fontRenderer, bounds, I18n.format("gui.modern.warehouse.post_sequence"), mouseX, mouseY);
        }
        }
        // Remove clipped controls from hit testing before drawing the fixed footer.
        hits.entrySet().removeIf(entry -> entry.getValue().y >= listBounds.y - settingsScroll
                && (entry.getKey().startsWith("slot_") || Arrays.asList("slots", "all_slots", "spread").contains(entry.getKey()))
                && (entry.getValue().y < listBounds.y || entry.getValue().bottom() > listBounds.bottom()));
        int controlsY = y + height - 53;
        text("gui.modern.warehouse.u084", x + 8, controlsY + 6, inner - 40);
        ModernMainLayout.Rect toggle = new ModernMainLayout.Rect(x + width - 44, controlsY + 2, 34, 16);
        hits.put("toggle", toggle);
        ModernUiRenderer.drawToggle(toggle.x, toggle.y, toggle.width, toggle.height, chest.autoDepositEnabled,
                toggle.contains(mouseX, mouseY));
        button("deposit", "gui.modern.warehouse.deposit_now", x + 8, controlsY + 24, (inner - 4) / 2, mouseX, mouseY);
        button("route", WarehouseEventHandler.isAutoDepositRouteRunning() ? "gui.modern.warehouse.busy"
                : "gui.modern.warehouse.u095", x + 10 + inner / 2, controlsY + 24, (inner - 4) / 2, mouseX, mouseY);
    }

    private void drawSettings(int x, int y, int width, int mouseX, int mouseY) {
        button("slots", "gui.modern.warehouse.select_deposit_slots", x, y, width, mouseX, mouseY);
        y += 26;
        if (slots) {
            int cell = Math.max(1, width / 9);
            for (int i = 0; i < 36; i++) {
                int raw = i < 27 ? i + 9 : i - 27;
                ModernMainLayout.Rect rect = new ModernMainLayout.Rect(x + i % 9 * cell, y + i / 9 * cell, cell - 1, cell - 1);
                hits.put("slot_" + raw, rect);
                boolean active = chest.depositInventorySlots != null && chest.depositInventorySlots.contains(raw);
                ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 2,
                        active ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(mc.fontRenderer, String.valueOf(i + 1), rect.x + 2, rect.y + 3,
                        ModernUiRenderer.TEXT, rect.width - 3);
            }
            button("all_slots", "gui.modern.warehouse.all_inventory", x, y + cell * 4 + 3, width, mouseX, mouseY);
            return;
        }
        button("spread", "", x, y, width, mouseX, mouseY);
        text("gui.modern.warehouse.spread_after_deposit", x + 4, y + 6, width - 40);
        ModernUiRenderer.drawToggle(x + width - 32, y + 4, 28, 14, chest.spreadAfterDeposit, false);
        y += 25;
        spread.layout(new ModernMainLayout.Rect(x, y, width, 22), 5, 5);
        spread.draw(mc.fontRenderer, mouseX, mouseY);
        y += 29;
        text("gui.modern.warehouse.post_sequence", x, y, width);
        String selectedSequence = chest.postDepositSequence == null || chest.postDepositSequence.trim().isEmpty()
                ? I18n.format("gui.modern.warehouse.choose_sequence") : chest.postDepositSequence;
        button("post_sequence_picker", selectedSequence, x, y + 14, width, mouseX, mouseY);
    }

    private void drawItem(String key, String name, ItemStack stack, int x, int y, int width, boolean selected,
            int mouseX, int mouseY) {
        ModernMainLayout.Rect rect = new ModernMainLayout.Rect(x, y, width, 25);
        hits.put(key, rect);
        ModernUiRenderer.drawSubtlePanel(x, y, width, 25, 4,
                selected ? ModernUiRenderer.ACCENT_DIM : rect.contains(mouseX, mouseY)
                        ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if (stack != null && !stack.isEmpty()) mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x + 4, y + 4);
        text(name, x + 24, y + 8, width - 28);
    }

    private void scrollbar(int total, int offset) {
        if (total <= rows) return;
        int height = Math.max(8, listBounds.height * rows / total);
        int y = listBounds.y + (listBounds.height - height) * offset / Math.max(1, total - rows);
        net.minecraft.client.gui.Gui.drawRect(listBounds.right() - 2, y, listBounds.right(), y + height, ModernUiRenderer.ACCENT);
    }

    private void text(String text, int x, int y, int width) {
        ModernUiRenderer.drawText(mc.fontRenderer, text, x, y, ModernUiRenderer.TEXT, Math.max(1, width));
    }

    private void button(String key, String label, int x, int y, int width, int mouseX, int mouseY) {
        ModernMainLayout.Rect rect = new ModernMainLayout.Rect(x, y, Math.max(1, width), 21);
        hits.put(key, rect);
        ModernUiRenderer.drawSubtlePanel(x, y, rect.width, 21, 4,
                rect.contains(mouseX, mouseY) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        text(label, x + 5, y + 6, width - 10);
    }

    private void icon(String key, ModernUiRenderer.Icon icon, int x, int y, int mouseX, int mouseY) {
        button(key, "", x, y, 22, mouseX, mouseY);
        ModernUiRenderer.drawIcon(icon, x + 4, y + 3, 14, ModernUiRenderer.TEXT);
        if (mc.currentScreen != null) ModernTooltipSupport.registerTooltipAnchor(mc.currentScreen, x, y, 22, 21,
                key.startsWith("delete_") ? I18n.format("gui.modern.warehouse.delete_item")
                        : key.equals("collapse") ? I18n.format("gui.modern.warehouse.close_panel")
                        : I18n.format("gui.modern.warehouse.policy_settings"));
    }

    public boolean mouse(int x, int y, int button, boolean pressed, int wheel) {
        if (bounds == null) return false;
        if (sequencePicker.isOpen()) {
            if (wheel != 0) return sequencePicker.handleMouseWheel(wheel, x, y);
            if (button == 0 && pressed) return sequencePicker.mouseClicked(x, y);
            if (button == 0 && !pressed) return sequencePicker.mouseReleased(x, y, 0);
            return true;
        }
        if (!pressed && button == 0) {
            boolean wasDragging = dragging >= 0;
            dragging = -1;
            if (wasDragging) { save(); return true; }
        }
        if (dragging >= 0 && button < 0 && listBounds.contains(x, y)) {
            List<String> names = WarehouseEventHandler.orderedDepositNames(chest);
            int target = Math.max(0, Math.min(names.size() - 1, scroll + (y - listBounds.y) / 28));
            if (target != dragging && dragging < names.size()) {
                names.add(target, names.remove(dragging));
                chest.depositItemOrder = names;
                dragging = selected = target;
            }
            return true;
        }
        if (!bounds.contains(x, y)) {
            if (pressed) { search.setFocused(false); spread.setFocused(false); suggestions = false; }
            return false;
        }
        if (wheel != 0) {
            if (settings) settingsScroll = Math.max(0, settingsScroll + (wheel > 0 ? -20 : 20));
            else if (suggestions) suggestionScroll = Math.max(0, suggestionScroll + (wheel > 0 ? -1 : 1));
            else scroll = Math.max(0, scroll + (wheel > 0 ? -1 : 1));
            return true;
        }
        if (button != 0 || !pressed) return true;
        if (search.mouseClicked(x, y, button)) { suggestions = true; settings = false; suggestionScroll = 0; return true; }
        if (settings && !slots && listBounds.contains(x, y)) {
            boolean spreadClicked = spread.mouseClicked(x, y, button);
            if (spreadClicked) return true;
        }
        for (Map.Entry<String, ModernMainLayout.Rect> hit : hits.entrySet()) {
            if (hit.getValue().contains(x, y)) { action(hit.getKey()); break; }
        }
        return true;
    }

    private void action(String key) {
        List<String> names = WarehouseEventHandler.orderedDepositNames(chest);
        List<String> before = new ArrayList<>(names);
        if ("expand".equals(key)) compactExpanded = true;
        else if ("collapse".equals(key)) compactExpanded = false;
        else if (key.startsWith("suggest_")) {
            search.setText(filtered.get(Integer.parseInt(key.substring(8))));
            search.setFocused(false); suggestions = false;
        } else if (key.startsWith("delete_")) {
            names.remove(Integer.parseInt(key.substring(7))); selected = -1;
        } else if (key.startsWith("item_")) {
            selected = dragging = Integer.parseInt(key.substring(5));
            search.setText(names.get(selected)); search.setFocused(false); suggestions = false;
        } else if ("add".equals(key) || "update".equals(key)) {
            String name = search.getText().trim();
            if (!name.isEmpty()) {
                if ("update".equals(key) && selected >= 0 && selected < names.size() && !names.contains(name)) names.set(selected, name);
                else if ("add".equals(key) && !names.contains(name)) names.add(name);
            }
            suggestions = false; search.setFocused(false);
        } else if ("settings".equals(key)) { settings = !settings; suggestions = false; settingsScroll = 0; }
        else if ("slots".equals(key)) slots = !slots;
        else if ("all_slots".equals(key)) chest.depositInventorySlots = new ArrayList<>();
        else if (key.startsWith("slot_")) {
            if (chest.depositInventorySlots == null) chest.depositInventorySlots = new ArrayList<>();
            Integer slot = Integer.valueOf(key.substring(5));
            if (!chest.depositInventorySlots.remove(slot)) chest.depositInventorySlots.add(slot);
        } else if ("spread".equals(key)) chest.spreadAfterDeposit = !chest.spreadAfterDeposit;
        else if ("post_sequence_picker".equals(key)) {
            sequencePicker.open(chest.postDepositSequence);
            return;
        }
        else if ("toggle".equals(key)) {
            chest.autoDepositEnabled = !chest.autoDepositEnabled;
            if (chest.autoDepositEnabled) WarehouseEventHandler.restartOpenChestDeposit();
        }
        else if ("deposit".equals(key)) WarehouseEventHandler.restartOpenChestDeposit();
        else if ("route".equals(key) && !WarehouseEventHandler.isAutoDepositRouteRunning()) WarehouseEventHandler.startAutoDepositByHighlights();
        chest.designatedItems = new HashSet<>(names);
        chest.depositItemOrder = names;
        chest.depositItemsConfigured = true;
        if (!before.equals(names) && chest.autoDepositEnabled) WarehouseEventHandler.restartOpenChestDeposit();
        save();
    }

    public boolean key(char character, int code) {
        if (sequencePicker.isOpen()) return sequencePicker.keyTyped(character, code);
        ModernTextField active = search.isFocused() ? search : settings && !slots && spread.isFocused() ? spread : null;
        if (active == null) return false;
        if (code == Keyboard.KEY_ESCAPE) { active.setFocused(false); suggestions = false; return true; }
        if (code == Keyboard.KEY_RETURN && active == search) { action("add"); return true; }
        active.keyTyped(character, code);
        if (active == search) { suggestions = true; suggestionScroll = 0; }
        else { chest.spreadItemNames = spread.getText(); save(); }
        return true;
    }

    private void save() { WarehouseManager.saveWarehouses(); }

    private void refreshCandidates() {
        candidates.clear();
        if (mc.player != null) {
            for (ItemStack stack : mc.player.inventory.mainInventory) addCandidate(stack);
            if (mc.player.openContainer != null) for (net.minecraft.inventory.Slot slot : mc.player.openContainer.inventorySlots) addCandidate(slot.getStack());
        }
        for (Warehouse warehouse : WarehouseManager.warehouses) {
            if (warehouse.chests == null) continue;
            for (ChestData data : warehouse.chests) if (data != null && data.hasBeenScanned)
                for (ItemStack stack : data.getSnapshotContents(54)) addCandidate(stack);
        }
        lastCandidateRefresh = System.currentTimeMillis();
    }

    private void addCandidate(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) candidates.putIfAbsent(stack.getDisplayName(), stack.copy());
    }
}
