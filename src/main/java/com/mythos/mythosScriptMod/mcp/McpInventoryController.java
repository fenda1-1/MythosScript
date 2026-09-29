package com.mythos.mythosScriptMod.mcp;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/** Player inventory gestures; always invoked on the client thread. No synthetic stacks. */
public final class McpInventoryController {
    private McpInventoryController() {}
    private static ItemStack lastShift = ItemStack.EMPTY;
    private static Object lastShiftPlayer;
    private static int lastShiftSlot;
    private static long lastShiftAt;
    public static int containerSlot(int slot) {
        if (slot >= 0 && slot < 9) return 36 + slot;
        if (slot < 36 && slot >= 9) return slot;
        if (slot >= 36 && slot < 40) return 44 - slot;
        if (slot == 40) return 45;
        if (slot >= 41 && slot <= 45) return slot - 41;
        if (slot == -999) return -999;
        throw new IllegalArgumentException("Invalid inventory slot");
    }
    private static int[] quickMoveRange(int slot) {
        if (slot >= 0 && slot < 9) return new int[]{0, 9};
        if (slot >= 9 && slot < 36) return new int[]{9, 36};
        if (slot >= 42 && slot <= 45) return new int[]{42, 46};
        return new int[]{slot, slot + 1};
    }
    public static JsonObject state() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.player.openContainer == null
                || mc.player.inventoryContainer == null || mc.playerController == null) return object("available", false);
        JsonArray items = new JsonArray();
        for (int i = 0; i < 46; i++) items.add(McpObservation.item(mc.player.inventoryContainer.getSlot(containerSlot(i)).getStack()));
        JsonObject cursor = McpObservation.item(mc.player.inventory.getItemStack());
        String signature = mc.player.getUniqueID() + ":" + System.identityHashCode(mc.player) + ":" + mc.player.dimension + ":" +
                mc.player.openContainer.windowId + ":" + items + ":" + cursor;
        StringBuilder token = new StringBuilder();
        try { for (byte b : MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8))) token.append(String.format("%02x", b & 255)); }
        catch (Exception e) { throw new IllegalStateException(e); }
        return object("available", mc.player.openContainer == mc.player.inventoryContainer,
                "token", token.toString(), "cursor", cursor, "creative", mc.player.capabilities.isCreativeMode);
    }
    private static void click(int slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId, containerSlot(slot), button, type, mc.player);
    }
    public static JsonObject call(JsonObject p) {
        Minecraft mc = Minecraft.getMinecraft();
        JsonObject state = state();
        if (!state.get("available").getAsBoolean()) throw new IllegalStateException("请进入世界并关闭其他容器后操作背包");
        if (!string(state, "token").equals(string(p, "expectedToken"))) throw new IllegalStateException("背包已变化，本次操作未执行；请刷新后重试");
        String operation = string(p, "operation");
        int slot = integer(p, "slot", -999, -999, 45);
        containerSlot(slot); // Validate before any mutation.
        int button = integer(p, "button", 0, 0, 8);
        if ("drag".equals(operation) || "transfer".equals(operation)) {
            if (button > 2 || (button == 2 && !mc.player.capabilities.isCreativeMode)) throw new IllegalArgumentException("Invalid drag button");
            JsonArray values = p.getAsJsonArray("slots");
            if (values == null || values.size() == 0 || values.size() > 46) throw new IllegalArgumentException("Invalid drag slots");
            Set<Integer> slots = new LinkedHashSet<>();
            for (JsonElement value : values) {
                int index = integer(object("slot", value), "slot", -1, 0, 45);
                containerSlot(index); slots.add(index);
            }
            if ("transfer".equals(operation)) {
                if (slot < 0 || !mc.player.inventory.getItemStack().isEmpty()) throw new IllegalStateException("Cursor changed");
                if (slots.size() == 1 && slots.contains(slot)) throw new IllegalArgumentException("Transfer requires a destination slot");
                click(slot, button, button == 2 ? ClickType.CLONE : ClickType.PICKUP);
                slots.remove(slot);
                if (slots.size() == 1) {
                    click(slots.iterator().next(), button == 2 ? 0 : button, ClickType.PICKUP);
                    slots.clear();
                }
            }
            if (!mc.player.inventory.getItemStack().isEmpty() && !slots.isEmpty()) {
                click(-999, button << 2, ClickType.QUICK_CRAFT);
                for (int index : slots) click(index, (button << 2) | 1, ClickType.QUICK_CRAFT);
                click(-999, (button << 2) | 2, ClickType.QUICK_CRAFT);
            }
        } else if ("quick_move_all".equals(operation)) {
            if (slot < 0 || lastShiftPlayer != mc.player || slot != lastShiftSlot || System.nanoTime() - lastShiftAt > 2000000000L || lastShift.isEmpty()) throw new IllegalStateException("请重新 Shift 点击需要移动的物品");
            net.minecraft.inventory.Slot source = mc.player.inventoryContainer.getSlot(containerSlot(slot));
            int[] range = quickMoveRange(slot);
            for (int i=range[0]; i<range[1]; i++) {
                net.minecraft.inventory.Slot candidate = mc.player.inventoryContainer.getSlot(containerSlot(i));
                if (candidate.isSameInventory(source) && candidate.canTakeStack(mc.player) && candidate.getHasStack() && net.minecraft.inventory.Container.canAddItemToSlot(candidate,lastShift,true)) click(i,0,ClickType.QUICK_MOVE);
            }
            lastShift = ItemStack.EMPTY;
        } else if ("cancel".equals(operation)) {
            // Return to the pickup slot first, then merge into compatible inventory stacks.
            if (!mc.player.inventory.getItemStack().isEmpty() && slot >= 0 && mc.player.inventoryContainer.getSlot(containerSlot(slot)).isItemValid(mc.player.inventory.getItemStack())) {
                ItemStack destination = mc.player.inventoryContainer.getSlot(containerSlot(slot)).getStack();
                ItemStack cursor = mc.player.inventory.getItemStack();
                if (destination.isEmpty() || (ItemStack.areItemsEqual(destination, cursor) && ItemStack.areItemStackTagsEqual(destination, cursor))) click(slot, 0, ClickType.PICKUP);
            }
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 36 && !mc.player.inventory.getItemStack().isEmpty(); i++) {
                ItemStack destination = mc.player.inventory.getStackInSlot(i), cursor = mc.player.inventory.getItemStack();
                if (pass == 0 ? !destination.isEmpty() && ItemStack.areItemsEqual(destination, cursor) && ItemStack.areItemStackTagsEqual(destination, cursor) : destination.isEmpty()) click(i, 0, ClickType.PICKUP);
            }
        } else if ("click".equals(operation)) {
            ClickType type = ClickType.valueOf(string(p, "type"));
            if (type == ClickType.QUICK_CRAFT
                    || (type == ClickType.SWAP ? button > 8
                    : type == ClickType.CLONE ? button != 2 || !mc.player.capabilities.isCreativeMode
                    : button > 1)) throw new IllegalArgumentException("Invalid click button/type");
            if (slot == -999 && type != ClickType.PICKUP) throw new IllegalArgumentException("Outside only supports PICKUP");
            if (type == ClickType.QUICK_MOVE && slot >= 0) {
                lastShift = mc.player.inventoryContainer.getSlot(containerSlot(slot)).getStack().copy();
                lastShiftPlayer = mc.player; lastShiftSlot = slot; lastShiftAt = System.nanoTime();
            }
            click(slot, button, type);
        } else throw new IllegalArgumentException("Unknown inventory operation");
        mc.player.inventoryContainer.detectAndSendChanges();
        return McpObservation.INSTANCE.snapshot(object("groups", Arrays.asList("player", "inventory"), "includeNbt", true));
    }
}
