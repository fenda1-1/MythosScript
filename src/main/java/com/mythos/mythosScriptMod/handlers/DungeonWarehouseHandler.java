// 文件路径: src/main/java/com/keycommand2/mythosScriptMod/handlers/DungeonWarehouseHandler.java
// (这是移除了“一键存入全部”功能的最终版本)
package com.mythos.mythosScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.ModUtils;

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

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public Settings settings = new Settings();
    }

    /** 当前线程所属会话的配置；bot 线程取自己的，客户端线程取挂载中的会话。 */
    public static OwnerConfig cfg() {
        String owner = com.mythos.mythosScriptMod.system.ProfileManager.currentConfigOwner();
        OwnerConfig config = CONFIGS.get(owner);
        return config != null ? config : loadOwnerConfig(owner);
    }

    // 不能用 computeIfAbsent：读盘过程中会反复调用 cfg()，ConcurrentHashMap 不允许递归更新。
    private static OwnerConfig loadOwnerConfig(String owner) {
        synchronized (com.mythos.mythosScriptMod.system.ProfileManager.CONFIG_LOAD_LOCK) {
            OwnerConfig existing = CONFIGS.get(owner);
            if (existing != null) {
                return existing;
            }
            OwnerConfig config = new OwnerConfig();
            CONFIGS.put(owner, config);
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) DungeonWarehouseHandler::loadConfig);
            return config;
        }
    }


    public static final DungeonWarehouseHandler INSTANCE = new DungeonWarehouseHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Settings {
        public int clickIntervalMs = 100;
        public boolean shiftClickEnabled = true;
        public boolean ctrlClickEnabled = true;
    }

    private DungeonWarehouseHandler() {}

    private static Path getConfigFile() {
        return ProfileManager.currentConfigFile("dungeon_warehouse_config.json");
    }

    public static void loadConfig() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                cfg().settings = GSON.fromJson(reader, Settings.class);
                if (cfg().settings == null) cfg().settings = new Settings();
            } catch (Exception e) {
                mythosScriptMod.LOGGER.error("加载副本仓库配置失败", e);
                cfg().settings = new Settings();
            }
        }
    }

    public static void saveConfig() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg().settings, writer);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存副本仓库配置失败", e);
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
        if (isShiftDown && cfg().settings.shiftClickEnabled) {
            handleShiftClick(slot);
        } else if (isCtrlDown && cfg().settings.ctrlClickEnabled) {
            handleCtrlClick(slot, ctrlAmount);
        }
    }

    private static void handleShiftClick(Slot slot) {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer == null || slot == null || !slot.getHasStack()) return;

        if (slot.slotNumber >= 54 && slot.slotNumber <= 89) {
            ItemStack clickedStack = slot.getStack();
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString("§a[副本仓库] §f开始存入所有 " + clickedStack.getDisplayName()));

            List<Integer> slotsToClick = new ArrayList<>();
            ContainerChest container = (ContainerChest) com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer;
            
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
                    if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || !(com.mythos.mythosScriptMod.mcp.NexusSessions.boundScreen() instanceof GuiChest) || 
                        com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer.windowId != windowId) {
                        return;
                    }
                    com.mythos.mythosScriptMod.mcp.NexusSessions.boundController().windowClick(windowId, slotIndex, 0, ClickType.QUICK_MOVE, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer());
                }, i * (cfg().settings.clickIntervalMs / 50));
            }
        }
    }

    private static void handleCtrlClick(Slot slot, int amount) {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer == null || slot == null || !slot.getHasStack() || amount <= 0) return;

        // [新增] 捕获窗口ID
        final int windowId = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer.windowId;

        if (slot.slotNumber >= 0 && slot.slotNumber <= 53) {
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString("§a[副本仓库] §f开始取出 " + amount + " 次: " + slot.getStack().getDisplayName()));
            for (int i = 0; i < amount; i++) {
                ModUtils.DelayScheduler.instance.schedule(() -> {
                    // [核心修复] 添加安全检查
                    if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || !(com.mythos.mythosScriptMod.mcp.NexusSessions.boundScreen() instanceof GuiChest) || 
                        com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer.windowId != windowId) {
                        return;
                    }
                    com.mythos.mythosScriptMod.mcp.NexusSessions.boundController().windowClick(windowId, slot.slotNumber, 0, ClickType.PICKUP, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer());
                }, i * (cfg().settings.clickIntervalMs / 50));
            }
        } else if (slot.slotNumber >= 54 && slot.slotNumber <= 89) {
            ItemStack clickedStack = slot.getStack();
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString("§a[副本仓库] §f开始存入 " + amount + " 组: " + clickedStack.getDisplayName()));

            List<Integer> slotsToClick = new ArrayList<>();
            ContainerChest container = (ContainerChest) com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer;

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
                    if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || !(com.mythos.mythosScriptMod.mcp.NexusSessions.boundScreen() instanceof GuiChest) || 
                        com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().openContainer.windowId != windowId) {
                        return;
                    }
                    com.mythos.mythosScriptMod.mcp.NexusSessions.boundController().windowClick(windowId, slotIndex, 0, ClickType.QUICK_MOVE, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer());
                }, i * (cfg().settings.clickIntervalMs / 50));
            }
        }
    }
}
