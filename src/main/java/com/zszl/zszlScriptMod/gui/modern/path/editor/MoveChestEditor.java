package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.resources.I18n;

/**
 * Dedicated dual-canvas editor for move_inventory_items_to_chest_slots.
 * Writes the original JSON fields; slotLimits is the only additive model.
 */
public final class MoveChestEditor {
    public interface Host {
        boolean canEdit();

        void pushHistory(String reason);

        void markDirty();

        void status(String message);

        void setTooltip(String text);

        void openChoiceMenu(String title, List<ClickSemantics.Option> options, int mouseX, int mouseY);

        ModernTextField field(String key);

        void focusField(String key);

        void showField(String key, ModernMainLayout.Rect rect, boolean enabled);
    }

    private enum CanvasId {
        INVENTORY, CHEST
    }

    private enum Focus {
        DIRECTION, INVENTORY, CHEST, STRATEGY, FILTER
    }

    private final SlotCanvasWidget.Machine inventoryMachine = new SlotCanvasWidget.Machine();
    private final SlotCanvasWidget.Machine chestMachine = new SlotCanvasWidget.Machine();
    private final SlotCanvasWidget.Machine inventoryLimitMachine = new SlotCanvasWidget.Machine();
    private final SlotCanvasWidget.Machine chestLimitMachine = new SlotCanvasWidget.Machine();
    private final Set<Integer> inventoryLimitSelection = new LinkedHashSet<Integer>();
    private final Set<Integer> chestLimitSelection = new LinkedHashSet<Integer>();
    private Host host;
    private JsonObject params;
    private SlotCanvasWidget.Layout inventoryLayout;
    private SlotCanvasWidget.Layout chestLayout;
    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect directionBounds;
    private ModernMainLayout.Rect arrowBounds;
    private ModernMainLayout.Rect clickBounds;
    private ModernMainLayout.Rect delayMinus;
    private ModernMainLayout.Rect delayPlus;
    private ModernMainLayout.Rect applyLimitsBounds;
    private ModernMainLayout.Rect sizePopupBounds;
    private ModernMainLayout.Rect sizeConfirmBounds;
    private CanvasId sizeTarget;
    private CanvasId activeCanvas = CanvasId.INVENTORY;
    private Focus focus = Focus.INVENTORY;
    private boolean sizeEditing;
    private int editRows;
    private int editCols;
    private CanvasId leftPressCanvas;
    private int leftPressIndex = -1;
    private boolean leftPressMoved;
    private boolean leftPressExclusive;
    private boolean leftPressRemove;
    private String tooltip = "";

    public void bind(Host host) {
        this.host = host;
    }

    public void resetTransient() {
        inventoryMachine.reset();
        chestMachine.reset();
        inventoryLimitMachine.reset();
        chestLimitMachine.reset();
        inventoryLimitSelection.clear();
        chestLimitSelection.clear();
        sizeEditing = false;
        sizeTarget = null;
        leftPressCanvas = null;
        leftPressIndex = -1;
        leftPressMoved = false;
        leftPressExclusive = false;
        leftPressRemove = false;
        tooltip = "";
    }

    public int preferredHeight(int width, JsonObject params) {
        int inventoryRows = ActionEditorJson.readInt(params, "inventoryRows", 4, 1, 12);
        int chestRows = ActionEditorJson.readInt(params, "chestRows", 6, 1, 12);
        return 34 + SlotCanvasWidget.height(inventoryRows, width) + 26
                + SlotCanvasWidget.height(chestRows, width) + 58 + (sizeEditing ? 36 : 0);
    }

    public void draw(FontRenderer font, ModernMainLayout.Rect row, JsonObject params, boolean enabled, int mouseX,
            int mouseY) {
        if (this.params != params) {
            resetTransient();
            ModernTextField takeField = host == null ? null : host.field("move.chest.maxTake");
            ModernTextField putField = host == null ? null : host.field("move.chest.maxPut");
            if (takeField != null) takeField.setText("");
            if (putField != null) putField.setText("");
        }
        this.params = params;
        this.bounds = row;
        tooltip = "";
        if (params == null) {
            return;
        }
        boolean inventoryToChest = ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY
                .equalsIgnoreCase(ActionEditorJson.readString(params, "moveDirection",
                        ItemFilterHandler.MOVE_DIRECTION_INVENTORY_TO_CHEST)) ? false : true;
        String clickType = ActionEditorJson.readString(params, "clickType", "PICKUP");
        int button = ActionEditorJson.readInt(params, "button", 0, 0, 8);
        ClickSemantics.Option click = ClickSemantics.resolve(clickType, button);
        int delay = ActionEditorJson.readInt(params, "delayTicks", 2, 0, 400);
        int inventoryRows = ActionEditorJson.readInt(params, "inventoryRows", 4, 1, 12);
        int inventoryCols = ActionEditorJson.readInt(params, "inventoryCols", 9, 1, 18);
        int chestRows = ActionEditorJson.readInt(params, "chestRows", 6, 1, 12);
        int chestCols = ActionEditorJson.readInt(params, "chestCols", 9, 1, 18);
        int[] live = ItemFilterHandler.livePhysicalSlotCounts();
        Set<Integer> inventorySelected = selectedOrPreview(CanvasId.INVENTORY, null);
        Set<Integer> chestSelected = selectedOrPreview(CanvasId.CHEST, null);

        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5, ModernUiRenderer.SHELL,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.title"), row.x + 10, row.y + 8,
                ModernUiRenderer.TEXT, Math.max(80, row.width - 20));

        int toolbarY = row.y + 24;
        clickBounds = new ModernMainLayout.Rect(row.x + 10, toolbarY, Math.min(168, row.width / 3), 20);
        drawChip(font, clickBounds, click.label(), enabled, mouseX, mouseY, !click.usesTargetCanvas);
        if (clickBounds.contains(mouseX, mouseY)) {
            tooltip = click.hint();
        }
        ModernMainLayout.Rect toolbarDirection = new ModernMainLayout.Rect(clickBounds.right() + 6, toolbarY, 118, 20);
        String directionLabel = inventoryToChest
                ? I18n.format("gui.path.action_editor.option.move_chest_direction.inventory_to_chest")
                : I18n.format("gui.path.action_editor.option.move_chest_direction.chest_to_inventory");
        drawChip(font, toolbarDirection, directionLabel, enabled, mouseX, mouseY, false);
        delayMinus = new ModernMainLayout.Rect(toolbarDirection.right() + 8, toolbarY, 18, 20);
        ModernMainLayout.Rect delayValue = new ModernMainLayout.Rect(delayMinus.right() + 2, toolbarY, 48, 20);
        delayPlus = new ModernMainLayout.Rect(delayValue.right() + 2, toolbarY, 18, 20);
        drawChip(font, delayMinus, "-", enabled, mouseX, mouseY, false);
        drawChip(font, delayValue, I18n.format("gui.path.action_editor.move_chest.delay", Integer.valueOf(delay)),
                false, mouseX, mouseY, false);
        drawChip(font, delayPlus, "+", enabled, mouseX, mouseY, false);
        int filterCount = InventoryItemFilterExpressionEngine.readExpressions(params).size();
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.filters",
                Integer.valueOf(filterCount)), delayPlus.right() + 8, toolbarY + 6, ModernUiRenderer.MUTED_TEXT, 90);

        int inventoryHeight = SlotCanvasWidget.height(inventoryRows, row.width);
        ModernMainLayout.Rect inventoryBounds = new ModernMainLayout.Rect(row.x + 8, toolbarY + 28, row.width - 16,
                inventoryHeight);
        inventoryLayout = SlotCanvasWidget.layout(inventoryBounds, inventoryRows, inventoryCols, live[0]);
        inventorySelected = selectedOrPreview(CanvasId.INVENTORY, inventoryLayout);
        SlotCanvasWidget.Role inventoryRole = click.usesTargetCanvas
                ? (inventoryToChest ? SlotCanvasWidget.Role.SOURCE : SlotCanvasWidget.Role.TARGET)
                : SlotCanvasWidget.Role.SOURCE;
        SlotCanvasWidget.draw(font, inventoryLayout,
                I18n.format("gui.path.action_editor.move_chest.inventory")
                        + liveLabel(live[0], inventoryLayout.capacity()),
                inventorySelected.size(), inventorySelected, inventoryMachine, inventoryRole, mouseX, mouseY, enabled,
                Integer.valueOf(inventoryMachine.focusIndex), limitLookup("inventory"),
                limitSelection(CanvasId.INVENTORY, inventoryLayout));
        hoverCanvas(inventoryLayout, true, mouseX, mouseY);

        arrowBounds = new ModernMainLayout.Rect(row.x + Math.max(10, (row.width - 132) / 2),
                inventoryBounds.bottom() + 4, 132, 18);
        directionBounds = toolbarDirection;
        String arrow = inventoryToChest ? "↓  " + I18n.format("gui.path.action_editor.move_chest.reverse")
                : "↑  " + I18n.format("gui.path.action_editor.move_chest.reverse");
        drawChip(font, arrowBounds, arrow, enabled, mouseX, mouseY, false);

        int chestHeight = SlotCanvasWidget.height(chestRows, row.width);
        boolean targetDimmed = !click.usesTargetCanvas;
        ModernMainLayout.Rect chestBounds = new ModernMainLayout.Rect(row.x + 8, arrowBounds.bottom() + 4,
                row.width - 16, chestHeight);
        chestLayout = SlotCanvasWidget.layout(chestBounds, chestRows, chestCols, live[1]);
        chestSelected = selectedOrPreview(CanvasId.CHEST, chestLayout);
        SlotCanvasWidget.Role chestRole = targetDimmed ? SlotCanvasWidget.Role.DISABLED
                : (inventoryToChest ? SlotCanvasWidget.Role.TARGET : SlotCanvasWidget.Role.SOURCE);
        SlotCanvasWidget.draw(font, chestLayout,
                I18n.format("gui.path.action_editor.move_chest.chest") + liveLabel(live[1], chestLayout.capacity()),
                chestSelected.size(), chestSelected, chestMachine, chestRole, mouseX, mouseY,
                enabled && !targetDimmed, Integer.valueOf(chestMachine.focusIndex), limitLookup("chest"),
                limitSelection(CanvasId.CHEST, chestLayout));
        hoverCanvas(chestLayout, false, mouseX, mouseY);
        if (targetDimmed && chestBounds.contains(mouseX, mouseY)) {
            tooltip = click.hint();
        }

        int strategyY = chestBounds.bottom() + 8;
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.max_take"), row.x + 10,
                strategyY + 6, ModernUiRenderer.SUBTLE_TEXT, 120);
        ModernTextField takeField = host == null ? null : host.field("move.chest.maxTake");
        ModernTextField putField = host == null ? null : host.field("move.chest.maxPut");
        ModernMainLayout.Rect takeBounds = new ModernMainLayout.Rect(row.x + 132, strategyY, 58, 20);
        ModernMainLayout.Rect putLabel = new ModernMainLayout.Rect(takeBounds.right() + 8, strategyY, 120, 20);
        ModernMainLayout.Rect putBounds = new ModernMainLayout.Rect(putLabel.right() + 4, strategyY, 58, 20);
        applyLimitsBounds = new ModernMainLayout.Rect(putBounds.right() + 8, strategyY, 52, 20);
        boolean limitsEnabled = enabled && click.supportsSlotLimits;
        drawFieldFrame(font, takeBounds, takeField, limitsEnabled, mouseX, mouseY);
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.max_put"), putLabel.x,
                strategyY + 6, ModernUiRenderer.SUBTLE_TEXT, putLabel.width);
        drawFieldFrame(font, putBounds, putField, limitsEnabled, mouseX, mouseY);
        drawChip(font, applyLimitsBounds, I18n.format("gui.path.action_editor.move_chest.apply_limits"),
                limitsEnabled, mouseX, mouseY, false);
        if (!click.supportsSlotLimits) {
            ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.limits_disabled"),
                    row.x + 10, strategyY + 24, ModernUiRenderer.WARNING, row.width - 20);
        } else {
            ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.limits_hint"),
                    row.x + 10, strategyY + 24, ModernUiRenderer.MUTED_TEXT, row.width - 20);
        }
        if (takeBounds.contains(mouseX, mouseY)) {
            tooltip = I18n.format("gui.path.action_editor.move_chest.tooltip.max_take");
        } else if (putBounds.contains(mouseX, mouseY) || putLabel.contains(mouseX, mouseY)) {
            tooltip = I18n.format("gui.path.action_editor.move_chest.tooltip.max_put");
        } else if (inventoryLayout != null && inventoryLayout.sizeBounds.contains(mouseX, mouseY)
                || chestLayout != null && chestLayout.sizeBounds.contains(mouseX, mouseY)) {
            tooltip = I18n.format("gui.path.action_editor.move_chest.tooltip.canvas_size");
        }
        if (sizeEditing) {
            drawSizePopup(font, mouseX, mouseY, enabled);
        }
        if (host != null && !tooltip.isEmpty()) {
            host.setTooltip(tooltip);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (params == null || host == null || bounds == null || !bounds.contains(mouseX, mouseY)) {
            if (sizeEditing && (sizePopupBounds == null || !sizePopupBounds.contains(mouseX, mouseY))) {
                sizeEditing = false;
                return true;
            }
            if (mouseButton == 1) {
                clearLimitSelection();
                return true;
            }
            if (inventoryMachine.hasActiveRect() || chestMachine.hasActiveRect()) {
                inventoryMachine.reset();
                chestMachine.reset();
                return true;
            }
            return false;
        }
        if (sizeEditing) {
            return handleSizePopupClick(mouseX, mouseY, mouseButton);
        }
        if (mouseButton != 0 && mouseButton != 1) {
            return true;
        }
        if (mouseButton == 0) {
            if (clickBounds != null && clickBounds.contains(mouseX, mouseY)) {
                if (host.canEdit()) {
                    host.openChoiceMenu(I18n.format("gui.path.action_editor.label.click_type"),
                            ClickSemantics.options(), mouseX, mouseY);
                }
                return true;
            }
            if ((directionBounds != null && directionBounds.contains(mouseX, mouseY))
                    || (arrowBounds != null && arrowBounds.contains(mouseX, mouseY))) {
                toggleDirection();
                return true;
            }
            if (delayMinus != null && delayMinus.contains(mouseX, mouseY)) {
                nudgeDelay(-1);
                return true;
            }
            if (delayPlus != null && delayPlus.contains(mouseX, mouseY)) {
                nudgeDelay(1);
                return true;
            }
            if (applyLimitsBounds != null && applyLimitsBounds.contains(mouseX, mouseY)) {
                applyLimits();
                return true;
            }
            if (inventoryLayout != null && inventoryLayout.sizeBounds.contains(mouseX, mouseY)) {
                openSizeEditor(CanvasId.INVENTORY);
                return true;
            }
            if (chestLayout != null && chestLayout.sizeBounds.contains(mouseX, mouseY)) {
                openSizeEditor(CanvasId.CHEST);
                return true;
            }
            if (beginLeftPress(CanvasId.INVENTORY, inventoryLayout, mouseX, mouseY, true)) {
                return true;
            }
            if (beginLeftPress(CanvasId.CHEST, chestLayout, mouseX, mouseY, ClickSemantics.usesTargetCanvas(
                    ActionEditorJson.readString(params, "clickType", "PICKUP"),
                    ActionEditorJson.readInt(params, "button", 0, 0, 8)))) {
                return true;
            }
            if (inventoryMachine.hasActiveRect() || chestMachine.hasActiveRect()) {
                inventoryMachine.reset();
                chestMachine.reset();
                return true;
            }
            return false;
        }
        if (beginLimitPress(CanvasId.INVENTORY, inventoryLayout, mouseX, mouseY, true)) {
            return true;
        }
        if (beginLimitPress(CanvasId.CHEST, chestLayout, mouseX, mouseY, ClickSemantics.usesTargetCanvas(
                ActionEditorJson.readString(params, "clickType", "PICKUP"),
                ActionEditorJson.readInt(params, "button", 0, 0, 8)))) {
            return true;
        }
        if (mouseButton == 1) {
            if (inventoryLayout != null && inventoryLayout.bounds.contains(mouseX, mouseY)) {
                clearLimitSelection(CanvasId.INVENTORY);
            } else if (chestLayout != null && chestLayout.bounds.contains(mouseX, mouseY)) {
                clearLimitSelection(CanvasId.CHEST);
            } else {
                clearLimitSelection();
            }
            return true;
        }
        if (inventoryMachine.hasActiveRect() || chestMachine.hasActiveRect()) {
            inventoryMachine.reset();
            chestMachine.reset();
        }
        return true;
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int button) {
        if (params == null) {
            return false;
        }
        if (button == 0 && leftPressCanvas != null) {
            SlotCanvasWidget.Layout layout = leftPressCanvas == CanvasId.INVENTORY ? inventoryLayout : chestLayout;
            SlotCanvasWidget.Machine machine = leftPressCanvas == CanvasId.INVENTORY
                    ? inventoryMachine : chestMachine;
            int index = layout == null ? -1 : layout.indexAt(mouseX, mouseY);
            if (index >= 0 && layout.isLive(index)) {
                if (index != machine.current) {
                    leftPressMoved = true;
                }
                machine.current = index;
                machine.focusIndex = index;
            }
            return true;
        }
        if (button != 1) {
            return false;
        }
        if (inventoryLimitMachine.dragging && inventoryLayout != null) {
            int index = inventoryLayout.indexAt(mouseX, mouseY);
            if (index >= 0 && inventoryLayout.isLive(index)) {
                inventoryLimitMachine.current = index;
                inventoryLimitMachine.focusIndex = index;
            }
            return true;
        }
        if (chestLimitMachine.dragging && chestLayout != null) {
            int index = chestLayout.indexAt(mouseX, mouseY);
            if (index >= 0 && chestLayout.isLive(index)) {
                chestLimitMachine.current = index;
                chestLimitMachine.focusIndex = index;
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0 && leftPressCanvas != null) {
            CanvasId canvas = leftPressCanvas;
            SlotCanvasWidget.Layout layout = canvas == CanvasId.INVENTORY ? inventoryLayout : chestLayout;
            SlotCanvasWidget.Machine machine = canvas == CanvasId.INVENTORY ? inventoryMachine : chestMachine;
            if (machine.dragging) {
                if (leftPressMoved) {
                    commitLeftSelection(canvas, layout, machine);
                } else {
                    machine.reset();
                    if (leftPressRemove) {
                        removeIndex(canvas, layout, leftPressIndex);
                    } else {
                        toggleIndex(canvas, layout, leftPressIndex, leftPressExclusive);
                    }
                }
            }
            leftPressCanvas = null;
            leftPressIndex = -1;
            leftPressMoved = false;
            leftPressExclusive = false;
            leftPressRemove = false;
            return true;
        }
        if (button != 1) {
            return false;
        }
        boolean handled = false;
        if (inventoryLimitMachine.dragging) {
            commitLimitSelection(CanvasId.INVENTORY, inventoryLayout, inventoryLimitMachine);
            handled = true;
        }
        if (chestLimitMachine.dragging) {
            commitLimitSelection(CanvasId.CHEST, chestLayout, chestLimitMachine);
            handled = true;
        }
        return handled;
    }

    public boolean keyTyped(int keyCode) {
        if (params == null || host == null || !host.canEdit()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (sizeEditing) {
                sizeEditing = false;
                return true;
            }
            if (inventoryMachine.hasActiveRect() || chestMachine.hasActiveRect()) {
                inventoryMachine.reset();
                chestMachine.reset();
                return true;
            }
            if (inventoryLimitMachine.hasActiveRect() || chestLimitMachine.hasActiveRect()
                    || !inventoryLimitSelection.isEmpty() || !chestLimitSelection.isEmpty()) {
                clearLimitSelection();
                return true;
            }
            return false;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (ctrl && keyCode == Keyboard.KEY_A) {
            selectAll(shift);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            cycleFocus(shift);
            return true;
        }
        SlotCanvasWidget.Layout layout = activeLayout();
        SlotCanvasWidget.Machine machine = activeMachine();
        if (layout == null || machine == null) {
            return false;
        }
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_UP
                || keyCode == Keyboard.KEY_DOWN) {
            machine.focusIndex = SlotCanvasWidget.moveFocus(layout, machine.focusIndex, keyCode);
            return true;
        }
        if (keyCode == Keyboard.KEY_SPACE) {
            toggleIndex(activeCanvas, layout, machine.focusIndex, false);
            return true;
        }
        return false;
    }

    public void applyClickOption(ClickSemantics.Option option) {
        if (option == null || option.disabled || params == null || host == null || !host.canEdit()) {
            if (option != null && option.disabled && host != null) {
                host.status(I18n.format("gui.path.action_editor.move_chest.quick_craft_disabled"));
            }
            return;
        }
        host.pushHistory("choose-move-chest-click");
        params.addProperty("clickType", option.clickType);
        params.addProperty("button", option.button);
        host.markDirty();
    }

    private boolean beginLeftPress(CanvasId canvas, SlotCanvasWidget.Layout layout, int mouseX, int mouseY,
            boolean enabledCanvas) {
        if (!enabledCanvas || layout == null || !layout.bounds.contains(mouseX, mouseY)) {
            return false;
        }
        int index = layout.indexAt(mouseX, mouseY);
        if (index < 0 || !layout.isLive(index)) {
            return false;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        activeCanvas = canvas;
        SlotCanvasWidget.Machine machine = canvas == CanvasId.INVENTORY ? inventoryMachine : chestMachine;
        machine.reset();
        machine.dragging = true;
        machine.removeMode = shift && !ctrl;
        machine.anchor = index;
        machine.current = index;
        machine.focusIndex = index;
        machine.snapshot.addAll(ActionEditorJson.readSortedSlots(params, slotKey(canvas)));
        if (ctrl) {
            machine.snapshot.clear();
        }
        leftPressCanvas = canvas;
        leftPressIndex = index;
        leftPressMoved = false;
        leftPressExclusive = ctrl;
        leftPressRemove = shift && !ctrl;
        return true;
    }

    private void removeIndex(CanvasId canvas, SlotCanvasWidget.Layout layout, int index) {
        if (params == null || host == null || !host.canEdit() || layout == null || index < 0
                || !layout.isLive(index)) {
            return;
        }
        Set<Integer> selected = new LinkedHashSet<Integer>(ActionEditorJson.readSortedSlots(params, slotKey(canvas)));
        if (!selected.remove(Integer.valueOf(index))) {
            return;
        }
        host.pushHistory("remove-move-chest-slot");
        ActionEditorJson.writeSortedSlots(params, slotKey(canvas), selected, layout.capacity());
        host.markDirty();
    }

    private boolean beginLimitPress(CanvasId canvas, SlotCanvasWidget.Layout layout, int mouseX, int mouseY,
            boolean enabledCanvas) {
        if (!enabledCanvas || layout == null || host == null || !host.canEdit()) {
            return false;
        }
        int index = layout.indexAt(mouseX, mouseY);
        if (index < 0 || !layout.isLive(index)) {
            return false;
        }
        activeCanvas = canvas;
        SlotCanvasWidget.Machine machine = canvas == CanvasId.INVENTORY
                ? inventoryLimitMachine : chestLimitMachine;
        machine.reset();
        machine.dragging = true;
        machine.anchor = index;
        machine.current = index;
        machine.focusIndex = index;
        return true;
    }

    private void commitLeftSelection(CanvasId canvas, SlotCanvasWidget.Layout layout,
            SlotCanvasWidget.Machine machine) {
        if (params == null || host == null || !host.canEdit() || layout == null || machine == null) {
            return;
        }
        Set<Integer> rect = SlotCanvasWidget.rectangle(layout, machine.anchor, machine.current);
        Set<Integer> next = new LinkedHashSet<Integer>(machine.snapshot);
        if (leftPressExclusive) {
            next.clear();
            next.addAll(rect);
        } else if (machine.removeMode) {
            next.removeAll(rect);
        } else if (next.containsAll(rect)) {
            next.removeAll(rect);
        } else {
            next.addAll(rect);
        }
        machine.reset();
        if (rect.isEmpty()) {
            return;
        }
        host.pushHistory("toggle-move-chest-rect");
        ActionEditorJson.writeSortedSlots(params, slotKey(canvas), next, layout.capacity());
        host.markDirty();
    }

    private void toggleIndex(CanvasId canvas, SlotCanvasWidget.Layout layout, int index, boolean exclusive) {
        if (params == null || host == null || !host.canEdit() || layout == null || index < 0 || !layout.isLive(index)) {
            return;
        }
        host.pushHistory("toggle-move-chest-slot");
        Set<Integer> selected = new LinkedHashSet<Integer>(ActionEditorJson.readSortedSlots(params, slotKey(canvas)));
        Integer boxed = Integer.valueOf(index);
        if (exclusive) {
            selected.clear();
            selected.add(boxed);
        } else if (!selected.add(boxed)) {
            selected.remove(boxed);
        }
        ActionEditorJson.writeSortedSlots(params, slotKey(canvas), selected, layout.capacity());
        activeMachine().focusIndex = index;
        host.markDirty();
    }

    private void commitLimitSelection(CanvasId canvas, SlotCanvasWidget.Layout layout,
            SlotCanvasWidget.Machine machine) {
        if (layout == null || machine == null) {
            return;
        }
        Set<Integer> next = SlotCanvasWidget.rectangle(layout, machine.anchor, machine.current);
        Set<Integer> target = canvas == CanvasId.INVENTORY ? inventoryLimitSelection : chestLimitSelection;
        target.clear();
        target.addAll(next);
        machine.reset();
    }

    private Set<Integer> limitSelection(CanvasId canvas, SlotCanvasWidget.Layout layout) {
        SlotCanvasWidget.Machine machine = canvas == CanvasId.INVENTORY
                ? inventoryLimitMachine : chestLimitMachine;
        if (machine.hasActiveRect() && layout != null) {
            return SlotCanvasWidget.rectangle(layout, machine.anchor, machine.current);
        }
        return canvas == CanvasId.INVENTORY ? inventoryLimitSelection : chestLimitSelection;
    }

    private void clearLimitSelection() {
        inventoryLimitMachine.reset();
        chestLimitMachine.reset();
        inventoryLimitSelection.clear();
        chestLimitSelection.clear();
    }

    private void clearLimitSelection(CanvasId canvas) {
        if (canvas == CanvasId.INVENTORY) {
            inventoryLimitMachine.reset();
            inventoryLimitSelection.clear();
        } else {
            chestLimitMachine.reset();
            chestLimitSelection.clear();
        }
    }

    private void toggleDirection() {
        if (params == null || host == null || !host.canEdit()) {
            return;
        }
        host.pushHistory("toggle-move-chest-direction");
        boolean toChest = !ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY
                .equalsIgnoreCase(ActionEditorJson.readString(params, "moveDirection",
                        ItemFilterHandler.MOVE_DIRECTION_INVENTORY_TO_CHEST));
        params.addProperty("moveDirection", toChest
                ? ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY
                : ItemFilterHandler.MOVE_DIRECTION_INVENTORY_TO_CHEST);
        host.markDirty();
    }

    private void nudgeDelay(int delta) {
        if (params == null || host == null || !host.canEdit()) {
            return;
        }
        host.pushHistory("edit-move-chest-delay");
        int delay = ActionEditorJson.readInt(params, "delayTicks", 2, 0, 400) + delta;
        params.addProperty("delayTicks", ActionEditorJson.clamp(delay, 0, 400));
        host.markDirty();
    }

    private void applyLimits() {
        if (params == null || host == null || !host.canEdit()) {
            return;
        }
        if (!ClickSemantics.supportsSlotLimits(ActionEditorJson.readString(params, "clickType", "PICKUP"),
                ActionEditorJson.readInt(params, "button", 0, 0, 8))) {
            host.status(I18n.format("gui.path.action_editor.move_chest.limits_disabled"));
            return;
        }
        Integer take = parseLimit(host.field("move.chest.maxTake"));
        Integer put = parseLimit(host.field("move.chest.maxPut"));
        if (take == null && put == null && blank(host.field("move.chest.maxTake")) && blank(host.field("move.chest.maxPut"))) {
            take = null;
            put = null;
        }
        if ((take != null && take.intValue() < 0) || (put != null && put.intValue() < 0)) {
            host.status(I18n.format("gui.path.action_editor.move_chest.limits_invalid"));
            return;
        }
        Set<Integer> inventory = new LinkedHashSet<Integer>(inventoryLimitSelection);
        Set<Integer> chest = new LinkedHashSet<Integer>(chestLimitSelection);
        if (inventory.isEmpty() && chest.isEmpty()) {
            host.status(I18n.format("gui.path.action_editor.move_chest.limits_need_selection"));
            return;
        }
        host.pushHistory("apply-move-chest-limits");
        if (!inventory.isEmpty()) {
            ActionEditorJson.applyLimits(params, "inventory", inventory, take, put);
        }
        if (!chest.isEmpty()) {
            ActionEditorJson.applyLimits(params, "chest", chest, take, put);
        }
        host.markDirty();
    }

    private Integer parseLimit(ModernTextField field) {
        if (field == null) {
            return null;
        }
        String text = ActionEditorJson.safe(field.getText()).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return Integer.valueOf(-1);
        }
    }

    private boolean blank(ModernTextField field) {
        return field == null || ActionEditorJson.safe(field.getText()).trim().isEmpty();
    }

    private void openSizeEditor(CanvasId canvas) {
        if (!host.canEdit()) {
            return;
        }
        sizeTarget = canvas;
        sizeEditing = true;
        if (canvas == CanvasId.INVENTORY) {
            editRows = ActionEditorJson.readInt(params, "inventoryRows", 4, 1, 12);
            editCols = ActionEditorJson.readInt(params, "inventoryCols", 9, 1, 18);
        } else {
            editRows = ActionEditorJson.readInt(params, "chestRows", 6, 1, 12);
            editCols = ActionEditorJson.readInt(params, "chestCols", 9, 1, 18);
        }
    }

    private void drawSizePopup(FontRenderer font, int mouseX, int mouseY, boolean enabled) {
        sizePopupBounds = new ModernMainLayout.Rect(bounds.x + 20, bounds.y + 48, Math.min(220, bounds.width - 40), 70);
        ModernUiRenderer.drawPanel(sizePopupBounds.x, sizePopupBounds.y, sizePopupBounds.width, sizePopupBounds.height,
                6, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.edit_size"),
                sizePopupBounds.x + 10, sizePopupBounds.y + 8, ModernUiRenderer.TEXT, sizePopupBounds.width - 20);
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.move_chest.size_value",
                Integer.valueOf(editRows), Integer.valueOf(editCols)), sizePopupBounds.x + 10,
                sizePopupBounds.y + 26, ModernUiRenderer.ACCENT, sizePopupBounds.width - 20);
        sizeConfirmBounds = new ModernMainLayout.Rect(sizePopupBounds.right() - 58, sizePopupBounds.bottom() - 24, 48, 18);
        drawChip(font, new ModernMainLayout.Rect(sizePopupBounds.x + 10, sizePopupBounds.bottom() - 24, 22, 18), "R-",
                enabled, mouseX, mouseY, false);
        drawChip(font, new ModernMainLayout.Rect(sizePopupBounds.x + 34, sizePopupBounds.bottom() - 24, 22, 18), "R+",
                enabled, mouseX, mouseY, false);
        drawChip(font, new ModernMainLayout.Rect(sizePopupBounds.x + 62, sizePopupBounds.bottom() - 24, 22, 18), "C-",
                enabled, mouseX, mouseY, false);
        drawChip(font, new ModernMainLayout.Rect(sizePopupBounds.x + 86, sizePopupBounds.bottom() - 24, 22, 18), "C+",
                enabled, mouseX, mouseY, false);
        drawChip(font, sizeConfirmBounds, I18n.format("gui.path.action_editor.move_chest.confirm"), enabled, mouseX,
                mouseY, false);
    }

    private boolean handleSizePopupClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || sizePopupBounds == null) {
            sizeEditing = false;
            return true;
        }
        if (new ModernMainLayout.Rect(sizePopupBounds.x + 10, sizePopupBounds.bottom() - 24, 22, 18).contains(mouseX, mouseY)) {
            editRows = ActionEditorJson.clamp(editRows - 1, 1, 12);
            return true;
        }
        if (new ModernMainLayout.Rect(sizePopupBounds.x + 34, sizePopupBounds.bottom() - 24, 22, 18).contains(mouseX, mouseY)) {
            editRows = ActionEditorJson.clamp(editRows + 1, 1, 12);
            return true;
        }
        if (new ModernMainLayout.Rect(sizePopupBounds.x + 62, sizePopupBounds.bottom() - 24, 22, 18).contains(mouseX, mouseY)) {
            editCols = ActionEditorJson.clamp(editCols - 1, 1, 18);
            return true;
        }
        if (new ModernMainLayout.Rect(sizePopupBounds.x + 86, sizePopupBounds.bottom() - 24, 22, 18).contains(mouseX, mouseY)) {
            editCols = ActionEditorJson.clamp(editCols + 1, 1, 18);
            return true;
        }
        if (sizeConfirmBounds != null && sizeConfirmBounds.contains(mouseX, mouseY)) {
            applySize();
            return true;
        }
        if (!sizePopupBounds.contains(mouseX, mouseY)) {
            sizeEditing = false;
        }
        return true;
    }

    private void applySize() {
        if (params == null || host == null || !host.canEdit() || sizeTarget == null) {
            sizeEditing = false;
            return;
        }
        host.pushHistory("edit-move-chest-size");
        String rowsKey = sizeTarget == CanvasId.INVENTORY ? "inventoryRows" : "chestRows";
        String colsKey = sizeTarget == CanvasId.INVENTORY ? "inventoryCols" : "chestCols";
        String slotsKey = slotKey(sizeTarget);
        params.addProperty(rowsKey, editRows);
        params.addProperty(colsKey, editCols);
        int capacity = editRows * editCols;
        Set<Integer> slots = new LinkedHashSet<Integer>(ActionEditorJson.readSortedSlots(params, slotsKey));
        ActionEditorJson.writeSortedSlots(params, slotsKey, slots, capacity);
        sizeEditing = false;
        host.markDirty();
    }

    private void selectAll(boolean clear) {
        SlotCanvasWidget.Layout layout = activeLayout();
        if (layout == null || host == null || !host.canEdit()) {
            return;
        }
        host.pushHistory(clear ? "clear-move-chest-slots" : "select-all-move-chest-slots");
        Set<Integer> next = clear ? new LinkedHashSet<Integer>() : SlotCanvasWidget.allLive(layout);
        ActionEditorJson.writeSortedSlots(params, slotKey(activeCanvas), next, layout.capacity());
        host.markDirty();
    }

    private void cycleFocus(boolean reverse) {
        Focus[] values = Focus.values();
        int index = focus.ordinal();
        focus = values[(index + (reverse ? values.length - 1 : 1)) % values.length];
        if (focus == Focus.INVENTORY) {
            activeCanvas = CanvasId.INVENTORY;
        } else if (focus == Focus.CHEST) {
            activeCanvas = CanvasId.CHEST;
        }
    }

    private Set<Integer> selectedOrPreview(CanvasId canvas, SlotCanvasWidget.Layout layout) {
        SlotCanvasWidget.Machine machine = canvas == CanvasId.INVENTORY ? inventoryMachine : chestMachine;
        if (machine != null && machine.hasActiveRect() && layout != null) {
            return machine.preview(layout);
        }
        return new LinkedHashSet<Integer>(ActionEditorJson.readSortedSlots(params, slotKey(canvas)));
    }

    private SlotCanvasWidget.LimitLookup limitLookup(final String region) {
        return new SlotCanvasWidget.LimitLookup() {
            @Override
            public Integer maxTake(int index) {
                return ActionEditorJson.maxTake(params, region, index);
            }

            @Override
            public Integer maxPut(int index) {
                return ActionEditorJson.maxPut(params, region, index);
            }
        };
    }

    private void hoverCanvas(SlotCanvasWidget.Layout layout, boolean inventory, int mouseX, int mouseY) {
        if (layout == null) {
            return;
        }
        int index = layout.indexAt(mouseX, mouseY);
        if (index >= 0) {
            tooltip = SlotCanvasWidget.tooltip(layout, index, inventory);
        }
    }

    private String liveLabel(int live, int capacity) {
        if (live <= 0) {
            return "";
        }
        return "  " + I18n.format("gui.path.action_editor.move_chest.live_slots", Integer.valueOf(live),
                Integer.valueOf(capacity));
    }

    private String slotKey(CanvasId canvas) {
        return canvas == CanvasId.INVENTORY ? "inventorySlots" : "chestSlots";
    }

    private SlotCanvasWidget.Layout activeLayout() {
        return activeCanvas == CanvasId.INVENTORY ? inventoryLayout : chestLayout;
    }

    private SlotCanvasWidget.Machine activeMachine() {
        return activeCanvas == CanvasId.INVENTORY ? inventoryMachine : chestMachine;
    }

    private void drawChip(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean enabled, int mouseX,
            int mouseY, boolean warning) {
        if (rect == null) {
            return;
        }
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF151E26 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = warning ? ModernUiRenderer.WARNING
                : hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        ModernUiRenderer.drawText(font, label, rect.x + 6, rect.y + 6,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(12, rect.width - 12));
    }

    private void drawFieldFrame(FontRenderer font, ModernMainLayout.Rect rect, ModernTextField field, boolean enabled,
            int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 3,
                enabled ? hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE : 0xFF151E26,
                hovered && enabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if (host != null && field != null) {
            String key = field == host.field("move.chest.maxTake") ? "move.chest.maxTake" : "move.chest.maxPut";
            host.showField(key, new ModernMainLayout.Rect(rect.x + 4, rect.y + 2, Math.max(12, rect.width - 8),
                    Math.max(14, rect.height - 4)),
                    enabled);
            String text = ActionEditorJson.safe(field.getText());
            field.setTextColor(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
            field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
            ModernUiRenderer.reflowTextField(field);
            ModernUiRenderer.drawTextField(field);
            if (text.isEmpty() && !field.isFocused()) {
                ModernUiRenderer.drawText(font, "gui.modern.path.chest.u001", rect.x + 7,
                        rect.y + Math.max(3, (rect.height - font.FONT_HEIGHT) / 2),
                        ModernUiRenderer.MUTED_TEXT, Math.max(12, rect.width - 14));
            }
        }
    }
}
