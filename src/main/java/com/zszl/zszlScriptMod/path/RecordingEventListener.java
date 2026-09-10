package com.zszl.zszlScriptMod.path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Records meaningful interactions while leaving the player's original input intact.
 */
public class RecordingEventListener {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND
                || !PathRecordingManager.isRecording()) {
            return;
        }

        EntityPlayerSP player = mc.player;
        if (player == null)
            return;
        pendingGuiKeyCode = -1;

        JsonObject params = new JsonObject();
        params.addProperty("locatorMode", "POSITION");
        params.addProperty("pos", "[" + event.getPos().getX() + "," + event.getPos().getY() + ","
                + event.getPos().getZ() + "]");
        params.addProperty("range", PathRecordingManager.getInfluenceRadius());
        PathRecordingManager.recordInputAction(player.getPositionVector(), player.rotationYaw, player.rotationPitch,
                "rightclickblock", params, System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onPlayerRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND
                || !PathRecordingManager.isRecording() || !(event.getTarget() instanceof Entity)) return;
        Entity target = event.getTarget();
        pendingGuiKeyCode = -1;
        JsonObject params = new JsonObject();
        params.addProperty("locatorMode", "NAME");
        params.addProperty("locatorText", target.getName());
        params.addProperty("locatorMatchMode", "EXACT");
        JsonArray pos = new JsonArray();
        pos.add(target.posX); pos.add(target.posY); pos.add(target.posZ);
        params.add("pos", pos);
        params.addProperty("range", PathRecordingManager.getInfluenceRadius());
        PathRecordingManager.recordInputAction("rightclickentity", params, System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND
                || !PathRecordingManager.isRecording()) return;
        ItemStack stack = event.getEntityPlayer().getHeldItem(event.getHand());
        if (stack == null || stack.isEmpty()) return;
        pendingGuiKeyCode = -1;
        JsonObject params = new JsonObject();
        params.addProperty("itemName", stack.getDisplayName());
        params.addProperty("matchMode", "EXACT");
        params.addProperty("useMode", "RIGHT_CLICK");
        params.addProperty("count", 1);
        PathRecordingManager.recordInputAction("use_hotbar_item", params, System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (!PathRecordingManager.isRecording() || event == null
                || event.getEntityPlayer() != mc.player || !(event.getTarget() instanceof EntityLivingBase)) return;
        recordCombatTarget((EntityLivingBase) event.getTarget());
    }

    @SubscribeEvent
    public void onEntityHurt(LivingHurtEvent event) {
        if (!PathRecordingManager.isRecording() || event == null || event.getEntityLiving() == null
                || event.getSource() == null || !(event.getSource().getTrueSource() instanceof EntityPlayer)) return;
        EntityPlayer attacker = (EntityPlayer) event.getSource().getTrueSource();
        if (attacker != mc.player || mc.player == null) return;
        recordCombatTarget(event.getEntityLiving());
    }

    private void recordCombatTarget(EntityLivingBase target) {
        if (!PathRecordingManager.isRecording() || target == null || mc.player == null) return;
        if (!isWithinCombatRecordingScope(target)) return;
        pendingGuiKeyCode = -1;
        String entityName = target.getDisplayName() == null ? "" : target.getDisplayName().getFormattedText();
        entityName = stripSectionFormatting(entityName);
        if (entityName.isEmpty()) {
            entityName = stripSectionFormatting(target.getName());
        }
        entityName = KillAuraHandler.normalizeFilterName(entityName);
        if (entityName.isEmpty()) return;
        // Entity IDs are only runtime de-duplication keys; they are never serialized into the action.
        if (!PathRecordingManager.markCombatEntity(target.getEntityId())) return;

        JsonObject hunt = new JsonObject();
        hunt.addProperty("radius", PathRecordingManager.getInfluenceRadius());
        hunt.addProperty("scanRadius", PathRecordingManager.getInfluenceRadius());
        hunt.addProperty("entityType", "all");
        hunt.addProperty("enableNameWhitelist", true);
        JsonArray whitelist = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("name", entityName);
        entry.addProperty("killCount", 0);
        whitelist.add(entry);
        hunt.add("nameWhitelistEntries", whitelist);
        PathRecordingManager.recordInputAction("hunt", hunt, System.currentTimeMillis());
    }

    private boolean isWithinCombatRecordingScope(EntityLivingBase target) {
        double radius = PathRecordingManager.getInfluenceRadius();
        if (target.getDistanceSq(mc.player) > radius * radius || !mc.player.canEntityBeSeen(target)) {
            return false;
        }
        Vec3d targetDirection = target.getPositionEyes(1.0F).subtract(mc.player.getPositionEyes(1.0F));
        return targetDirection.lengthSquared() <= 1.0E-6D
                || mc.player.getLookVec().dotProduct(targetDirection) >= 0.0D;
    }

    private String stripSectionFormatting(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder plain = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\u00a7' && i + 1 < value.length()) {
                i++;
                continue;
            }
            plain.append(character);
        }
        return plain.toString().trim();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !PathRecordingManager.isRecording()) return;
        PathRecordingManager.refreshPacketAssociations(false);
        GuiScreen screen = mc.currentScreen;
        String screenName = screen == null ? "" : screen.getClass().getName();
        String title = GuiElementInspector.getCurrentGuiTitle(mc);
        if ((!screenName.equals(lastScreenName) || !title.equals(lastGuiTitle)) && !screenName.isEmpty()
                && !(screen instanceof GuiModernMainScreen)) {
            lastScreenName = screenName;
            lastGuiTitle = title;
            lastContainerSlotRelative = -1;
            if (pendingGuiKeyCode >= 0 && System.currentTimeMillis() - pendingGuiKeyAt <= 1500L) {
                JsonObject key = new JsonObject();
                key.addProperty("key", Keyboard.getKeyName(pendingGuiKeyCode));
                key.addProperty("state", "Press");
                PathRecordingManager.recordInputAction("key", key, pendingGuiKeyAt);
            }
            pendingGuiKeyCode = -1;
            JsonObject wait = new JsonObject();
            wait.addProperty("title", title);
            wait.addProperty("timeoutTicks", 200);
            wait.addProperty("timeoutSkipCount", 0);
            PathRecordingManager.recordAction("wait_until_gui_title", wait);
        } else if (screenName.isEmpty()) {
            lastScreenName = "";
            lastGuiTitle = "";
        }
    }

    @SubscribeEvent
    public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Post event) {
        if (!PathRecordingManager.isRecording() || event.getGui() == null
                || event.getGui() instanceof GuiModernMainScreen
                || !Mouse.getEventButtonState() || Mouse.getEventButton() < 0) return;
        GuiScreen screen = event.getGui();
        GuiContainer gui = screen instanceof GuiContainer ? (GuiContainer) screen : null;
        int x = Mouse.getEventX() * screen.width / Math.max(1, mc.displayWidth);
        int y = screen.height - Mouse.getEventY() * screen.height / Math.max(1, mc.displayHeight) - 1;
        int button = Mouse.getEventButton();
        GuiElementInspector.GuiElementInfo clicked = null;
        if (gui != null) {
            for (GuiElementInspector.GuiElementInfo element : GuiElementInspector.captureCurrentSnapshot().getElements()) {
                if (element.getType() == GuiElementInspector.ElementType.SLOT
                        && x >= element.getX() && x < element.getX() + element.getWidth()
                        && y >= element.getY() && y < element.getY() + element.getHeight()) {
                    clicked = element;
                    break;
                }
            }
        }
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        String clickType = shift && button == 0 ? "QUICK_MOVE" : "PICKUP";
        if (clicked != null) {
            int count = 0;
            if (mc.player.openContainer != null && clicked.getSlotIndex() >= 0
                    && clicked.getSlotIndex() < mc.player.openContainer.inventorySlots.size()) {
                Slot slot = mc.player.openContainer.inventorySlots.get(clicked.getSlotIndex());
                if (slot != null && slot.getHasStack()) count = slot.getStack().getCount();
            }
            if (shift && button == 0 && count > 0 && clicked.getPath().contains("player_slot")) {
                JsonObject move = new JsonObject();
                move.addProperty("itemName", GuiElementInspector.captureCurrentSnapshot().getElements().contains(clicked)
                        ? clicked.getText() : "");
                String movedItemName = clicked.getText() == null ? "" : clicked.getText().trim();
                String itemExpression = InventoryItemFilterExpressionEngine.buildLegacyCompatibleExpression(
                        movedItemName, "CONTAINS", java.util.Collections.<String>emptyList(), "CONTAINS");
                com.google.gson.JsonArray itemExpressions = new com.google.gson.JsonArray();
                itemExpressions.add(itemExpression);
                move.add("itemFilterExpressions", itemExpressions);
                move.addProperty("itemFilterExpression", itemExpression);
                move.addProperty("sourceScope", "INVENTORY");
                move.addProperty("targetScope", "CONTAINER");
                move.addProperty("moveDirection", "INVENTORY_TO_CHEST");
                move.addProperty("delayTicks", 2);
                move.addProperty("normalizeDelayTo20Tps", true);
                move.addProperty("button", 0);
                move.addProperty("clickType", "PICKUP");
                move.addProperty("maxTransferCount", count);
                com.google.gson.JsonArray inventorySlots = new com.google.gson.JsonArray();
                com.google.gson.JsonArray chestSlots = new com.google.gson.JsonArray();
                int inventoryIndex = 0;
                int chestIndex = 0;
                if (mc.player.openContainer != null) {
                    for (int i = 0; i < mc.player.openContainer.inventorySlots.size(); i++) {
                        Slot candidate = mc.player.openContainer.inventorySlots.get(i);
                        if (candidate == null) continue;
                        if (candidate.inventory == mc.player.inventory) {
                            if (candidate.getSlotIndex() >= 0 && candidate.getSlotIndex() < 36) {
                                inventorySlots.add(inventoryIndex++);
                            }
                        } else {
                            chestSlots.add(chestIndex++);
                        }
                    }
                }
                if (lastContainerSlotRelative >= 0) {
                    com.google.gson.JsonArray selectedTarget = new com.google.gson.JsonArray();
                    selectedTarget.add(lastContainerSlotRelative);
                    chestSlots = selectedTarget;
                }
                move.add("inventorySlots", inventorySlots);
                move.add("chestSlots", chestSlots);
                JsonObject limits = new JsonObject();
                JsonObject sourceLimits = new JsonObject();
                JsonObject targetLimits = new JsonObject();
                for (com.google.gson.JsonElement element : inventorySlots) {
                    JsonObject limit = new JsonObject();
                    limit.addProperty("maxTake", count);
                    sourceLimits.add(element.getAsString(), limit);
                }
                for (com.google.gson.JsonElement element : chestSlots) {
                    JsonObject limit = new JsonObject();
                    limit.addProperty("maxPut", count);
                    targetLimits.add(element.getAsString(), limit);
                }
                limits.add("inventory", sourceLimits);
                limits.add("chest", targetLimits);
                move.add("slotLimits", limits);
                PathRecordingManager.recordInputAction("move_inventory_items_to_chest_slots", move,
                        System.currentTimeMillis());
                return;
            }
            if (!clicked.getPath().contains("player_slot")) {
                lastContainerSlotRelative = containerRelativeIndex(clicked.getSlotIndex());
            }
            PathRecordingManager.recordContainerClick(clicked.getSlotIndex(), button, clickType, count, x, y,
                    GuiElementInspector.getCurrentGuiTitle(mc));
        } else {
            PathRecordingManager.recordScreenClick(x, y, button, GuiElementInspector.getCurrentGuiTitle(mc));
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) return;
        if (Keyboard.getEventKey() == Keyboard.KEY_M && PathRecordingManager.isRecording()) {
            PathRecordingManager.togglePaused();
        } else if (Keyboard.getEventKey() == Keyboard.KEY_N && !PathRecordingManager.isRecording()) {
            PathRecordingManager.startRecording();
            if (mc.currentScreen instanceof GuiModernMainScreen) {
                mc.displayGuiScreen(null);
            }
        } else if (PathRecordingManager.isRecording() && !PathRecordingManager.isPaused()
                && !isMovementOrModifierKey(Keyboard.getEventKey())) {
            pendingGuiKeyCode = Keyboard.getEventKey();
            pendingGuiKeyAt = System.currentTimeMillis();
        }
    }

    private boolean isMovementOrModifierKey(int keyCode) {
        return keyCode == Keyboard.KEY_W || keyCode == Keyboard.KEY_A || keyCode == Keyboard.KEY_S
                || keyCode == Keyboard.KEY_D || keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_LSHIFT
                || keyCode == Keyboard.KEY_RSHIFT || keyCode == Keyboard.KEY_LCONTROL
                || keyCode == Keyboard.KEY_RCONTROL || keyCode == Keyboard.KEY_LMENU || keyCode == Keyboard.KEY_RMENU;
    }

    private int containerRelativeIndex(int physicalIndex) {
        if (mc.player == null || mc.player.openContainer == null) return -1;
        int relative = 0;
        for (int i = 0; i < mc.player.openContainer.inventorySlots.size(); i++) {
            Slot slot = mc.player.openContainer.inventorySlots.get(i);
            if (slot == null) continue;
            if (slot.inventory == mc.player.inventory) {
                if (slot.getSlotIndex() < 0 || slot.getSlotIndex() >= 36) continue;
            } else {
                if (i == physicalIndex) return relative;
                relative++;
            }
        }
        return -1;
    }

    private String lastScreenName = "";
    private String lastGuiTitle = "";
    private int pendingGuiKeyCode = -1;
    private long pendingGuiKeyAt;
    private int lastContainerSlotRelative = -1;

    /** Flushes a key that did not cause a GUI transition before recording stopped. */
    public void flushPendingInput() {
        if (pendingGuiKeyCode < 0 || !PathRecordingManager.isRecording() || PathRecordingManager.isPaused()) {
            return;
        }
        JsonObject key = new JsonObject();
        key.addProperty("key", Keyboard.getKeyName(pendingGuiKeyCode));
        key.addProperty("state", "Press");
        PathRecordingManager.recordInputAction("key", key, pendingGuiKeyAt);
        pendingGuiKeyCode = -1;
        pendingGuiKeyAt = 0L;
    }

    public void resetScreenObservation() {
        lastScreenName = "";
        lastGuiTitle = "";
        pendingGuiKeyCode = -1;
        pendingGuiKeyAt = 0L;
        lastContainerSlotRelative = -1;
    }
}
