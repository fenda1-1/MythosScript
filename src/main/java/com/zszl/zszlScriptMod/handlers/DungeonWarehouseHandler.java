// 文件路径: src/main/java/com/keycommand2/zszlScriptMod/handlers/DungeonWarehouseHandler.java
// (这是移除了“一键存入全部”功能的最终版本)
package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DungeonWarehouseHandler {

    public static final DungeonWarehouseHandler INSTANCE = new DungeonWarehouseHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Settings {
        public int clickIntervalMs = 100;
        public boolean shiftClickEnabled = true;
        public boolean ctrlClickEnabled = true;
    }
    public static Settings settings = new Settings();

    private DungeonWarehouseHandler() {}

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("dungeon_warehouse_config.json");
    }

    public static void loadConfig() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                settings = GSON.fromJson(reader, Settings.class);
                if (settings == null) settings = new Settings();
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("加载副本仓库配置失败", e);
                settings = new Settings();
            }
        }
    }

    public static void saveConfig() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存副本仓库配置失败", e);
        }
    }

    public static boolean isDungeonWarehouseGui(GuiChest gui) {
        if (gui.inventorySlots instanceof ContainerChest) {
            ContainerChest container = (ContainerChest) gui.inventorySlots;
            String title = container.getLowerChestInventory().getDisplayName().getUnformattedText();
            return title.contains("副本仓库:");
        }
        return false;
    }

    public static void handleClick(Slot slot, boolean isShiftDown, boolean isCtrlDown, int ctrlAmount) {
        if (isShiftDown && settings.shiftClickEnabled) {
            handleShiftClick(slot);
        } else if (isCtrlDown && settings.ctrlClickEnabled) {
            handleCtrlClick(slot, ctrlAmount);
        }
    }

    private static void handleShiftClick(Slot slot) {
        if (mc.player == null || mc.player.openContainer == null || slot == null || !slot.getHasStack()) return;

        if (slot.slotNumber >= 54 && slot.slotNumber <= 89) {
            ItemStack clickedStack = slot.getStack();
            mc.player.sendMessage(new TextComponentString("§a[副本仓库] §f开始存入所有 " + clickedStack.getDisplayName()));

            List<Integer> slotsToClick = new ArrayList<>();
            ContainerChest container = (ContainerChest) mc.player.openContainer;
            
            // [新增] 捕获窗口ID
            final int windowId = container.windowId;

            for (int i = 54; i <= 89; i++) {
                Slot currentSlot = container.getSlot(i);
                if (currentSlot != null && currentSlot.getHasStack()) {
                    if (currentSlot.getStack().isItemEqual(clickedStack) && ItemStack.areItemStackTagsEqual(currentSlot.getStack(), clickedStack)) {
                        slotsToClick.add(i);
                    }
                }
            }

            for (int i = 0; i < slotsToClick.size(); i++) {
                final int slotIndex = slotsToClick.get(i);
                ModUtils.DelayScheduler.instance.schedule(() -> {
                    // [核心修复] 添加安全检查
                    if (mc.player == null || !(mc.currentScreen instanceof GuiChest) || 
                        mc.player.openContainer == null || mc.player.openContainer.windowId != windowId) {
                        return;
                    }
                    mc.playerController.windowClick(windowId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player);
                }, i * (settings.clickIntervalMs / 50));
            }
        }
    }

    private static void handleCtrlClick(Slot slot, int amount) {
        if (mc.player == null || mc.player.openContainer == null || slot == null || !slot.getHasStack() || amount <= 0) return;

        // [新增] 捕获窗口ID
        final int windowId = mc.player.openContainer.windowId;

        if (slot.slotNumber >= 0 && slot.slotNumber <= 53) {
            mc.player.sendMessage(new TextComponentString("§a[副本仓库] §f开始取出 " + amount + " 次: " + slot.getStack().getDisplayName()));
            for (int i = 0; i < amount; i++) {
                ModUtils.DelayScheduler.instance.schedule(() -> {
                    // [核心修复] 添加安全检查
                    if (mc.player == null || !(mc.currentScreen instanceof GuiChest) || 
                        mc.player.openContainer == null || mc.player.openContainer.windowId != windowId) {
                        return;
                    }
                    mc.playerController.windowClick(windowId, slot.slotNumber, 0, ClickType.PICKUP, mc.player);
                }, i * (settings.clickIntervalMs / 50));
            }
        } else if (slot.slotNumber >= 54 && slot.slotNumber <= 89) {
            ItemStack clickedStack = slot.getStack();
            mc.player.sendMessage(new TextComponentString("§a[副本仓库] §f开始存入 " + amount + " 组: " + clickedStack.getDisplayName()));

            List<Integer> slotsToClick = new ArrayList<>();
            ContainerChest container = (ContainerChest) mc.player.openContainer;

            for (int i = 54; i <= 89; i++) {
                Slot currentSlot = container.getSlot(i);
                if (currentSlot != null && currentSlot.getHasStack()) {
                    if (currentSlot.getStack().isItemEqual(clickedStack) && ItemStack.areItemStackTagsEqual(currentSlot.getStack(), clickedStack)) {
                        slotsToClick.add(i);
                        if (slotsToClick.size() >= amount) {
                            break;
                        }
                    }
                }
            }

            for (int i = 0; i < slotsToClick.size(); i++) {
                final int slotIndex = slotsToClick.get(i);
                ModUtils.DelayScheduler.instance.schedule(() -> {
                    // [核心修复] 添加安全检查
                    if (mc.player == null || !(mc.currentScreen instanceof GuiChest) || 
                        mc.player.openContainer == null || mc.player.openContainer.windowId != windowId) {
                        return;
                    }
                    mc.playerController.windowClick(windowId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player);
                }, i * (settings.clickIntervalMs / 50));
            }
        }
    }
}
