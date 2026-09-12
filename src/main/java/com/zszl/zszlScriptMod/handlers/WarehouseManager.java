// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/WarehouseManager.java
package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.system.dungeon.ChestData;
import com.zszl.zszlScriptMod.system.dungeon.Warehouse;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class WarehouseManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CATEGORY_DEFAULT = "默认";
    private static final List<String> categories = new CopyOnWriteArrayList<>();
    public static List<Warehouse> warehouses = new CopyOnWriteArrayList<>();
    public static Warehouse currentWarehouse = null;

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("warehouses.json");
    }

    public static synchronized void loadWarehouses() {
        Path configFile = getConfigFile();
        categories.clear();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                com.google.gson.JsonElement parsed = new com.google.gson.JsonParser().parse(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    com.google.gson.JsonObject root = parsed.getAsJsonObject();
                    if (root.has("categories") && root.get("categories").isJsonArray()) {
                        for (com.google.gson.JsonElement element : root.getAsJsonArray("categories")) {
                            if (element != null && element.isJsonPrimitive()) {
                                categories.add(element.getAsString());
                            }
                        }
                    }
                    if (root.has("warehouses") && root.get("warehouses").isJsonArray()) {
                        Type listType = new TypeToken<CopyOnWriteArrayList<Warehouse>>() {
                        }.getType();
                        warehouses = GSON.fromJson(root.get("warehouses"), listType);
                    } else {
                        warehouses = new CopyOnWriteArrayList<>();
                    }
                } else if (parsed != null && parsed.isJsonArray()) {
                    Type listType = new TypeToken<CopyOnWriteArrayList<Warehouse>>() {
                    }.getType();
                    warehouses = GSON.fromJson(parsed, listType);
                } else {
                    warehouses = new CopyOnWriteArrayList<>();
                }

                if (warehouses == null) {
                    warehouses = new CopyOnWriteArrayList<>();
                }
                for (Warehouse warehouse : warehouses) {
                    if (warehouse == null) {
                        continue;
                    }
                    warehouse.category = normalizeCategory(warehouse.category);
                    warehouse.updateBounds();
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error(I18n.format("log.warehouse.load_failed"), e);
                warehouses = new CopyOnWriteArrayList<>();
            }
        } else {
            warehouses = new CopyOnWriteArrayList<>();
        }
        ensureCategoriesSynced();
    }

    public static synchronized void saveWarehouses() {
        Path configFile = getConfigFile();
        try {
            ensureCategoriesSynced();
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                root.add("categories", GSON.toJsonTree(new ArrayList<>(categories)));
                root.add("warehouses", GSON.toJsonTree(warehouses));
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.warehouse.save_failed"), e);
        }
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        for (Warehouse warehouse : warehouses) {
            if (warehouse == null) {
                continue;
            }
            warehouse.category = normalizeCategory(warehouse.category);
            normalized.add(warehouse.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        categories.clear();
        categories.addAll(normalized);
    }

    private static boolean containsCategoryIgnoreCase(String category) {
        for (String existing : categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalizeCategory(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeCategoryIgnoreCase(String category) {
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizeCategory(category))) {
                categories.remove(i);
                return true;
            }
        }
        return false;
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureCategoriesSynced();
        return new ArrayList<>(categories);
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        categories.add(normalized);
        saveWarehouses();
        return true;
    }

    public static synchronized boolean renameCategory(String oldCategory, String newCategory) {
        String normalizedOld = normalizeCategory(oldCategory);
        String normalizedNew = normalizeCategory(newCategory);
        ensureCategoriesSynced();

        if (normalizedOld.equalsIgnoreCase(normalizedNew)) {
            return true;
        }
        if (containsCategoryIgnoreCase(normalizedNew)) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizedOld)) {
                categories.set(i, normalizedNew);
                changed = true;
                break;
            }
        }

        for (Warehouse warehouse : warehouses) {
            if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(normalizedOld)) {
                warehouse.category = normalizedNew;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveWarehouses();
        return true;
    }

    public static synchronized boolean deleteCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();

        boolean changed = removeCategoryIgnoreCase(normalized);
        for (Warehouse warehouse : warehouses) {
            if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(normalized)) {
                warehouse.category = CATEGORY_DEFAULT;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveWarehouses();
        return true;
    }

    public static void updateCurrentWarehouse() {
        if (mc.player == null) {
            currentWarehouse = null;
            return;
        }
        for (Warehouse warehouse : warehouses) {
            if (warehouse.isActive && warehouse.isPlayerInside(mc.player.posX, mc.player.posZ)) {
                if (currentWarehouse != warehouse) {
                    currentWarehouse = warehouse;
                    zszlScriptMod.LOGGER.info(I18n.format("log.warehouse.enter_area"), warehouse.name);
                    checkBrokenChests(warehouse);
                }
                return;
            }
        }
        if (currentWarehouse != null) {
            zszlScriptMod.LOGGER.info(I18n.format("log.warehouse.leave_area"), currentWarehouse.name);
            currentWarehouse = null;
        }
    }

    public static void scanChest(IInventory chestInventory, BlockPos pos) {
        if (chestInventory == null || pos == null) {
            zszlScriptMod.LOGGER.error(I18n.format("log.warehouse.scan_chest_null_abort"));
            return;
        }

        Warehouse targetWarehouse = null;
        for (Warehouse warehouse : warehouses) {
            if (warehouse.isPosInside(pos)) {
                targetWarehouse = warehouse;
                break;
            }
        }

        if (targetWarehouse == null) {
            zszlScriptMod.LOGGER.warn(I18n.format("log.warehouse.opened_outside_defined_area"), pos);
            return;
        }

        ChestData chestData = targetWarehouse.getChestAt(pos);

        if (chestData == null) {
            chestData = new ChestData(pos);
            targetWarehouse.chests.add(chestData);
            zszlScriptMod.LOGGER.info(I18n.format("log.warehouse.new_chest_recorded"), targetWarehouse.name, pos);
        }

        // 直接从传入的 IInventory 创建快照，这是最准确的数据源
        NonNullList<ItemStack> items = NonNullList.withSize(chestInventory.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < chestInventory.getSizeInventory(); i++) {
            items.set(i, chestInventory.getStackInSlot(i).copy());
        }

        chestData.snapshotContents(items); // 调用 ChestData 中更新后的方法
        zszlScriptMod.LOGGER.info(I18n.format("log.warehouse.snapshot_updated"), targetWarehouse.name, pos);
        saveWarehouses();
    }

    public static Warehouse findWarehouseForPos(BlockPos pos) {
        for (Warehouse warehouse : warehouses) {
            if (warehouse.isPosInside(pos)) {
                return warehouse;
            }
        }
        return null;
    }

    public static void scanForChestsInWarehouse(Warehouse warehouse) {
        if (mc.world == null || mc.player == null)
            return;

        mc.player.sendMessage(new TextComponentString(I18n.format("msg.warehouse.scan.start", warehouse.name)));

        Set<BlockPos> foundChestPositions = new HashSet<>();
        Set<BlockPos> processedPositions = new HashSet<>();

        int playerY = mc.player.getPosition().getY();
        int minY = Math.max(0, playerY - 30);
        int maxY = Math.min(255, playerY + 30);

        for (int y = minY; y <= maxY; y++) {
            for (int x = (int) warehouse.minX; x <= (int) warehouse.maxX; x++) {
                for (int z = (int) warehouse.minZ; z <= (int) warehouse.maxZ; z++) {
                    BlockPos currentPos = new BlockPos(x, y, z);
                    if (processedPositions.contains(currentPos)) {
                        continue;
                    }

                    TileEntity te = mc.world.getTileEntity(currentPos);
                    if (te instanceof TileEntityChest) {
                        foundChestPositions.add(currentPos);
                        processedPositions.add(currentPos);

                        TileEntityChest chest = (TileEntityChest) te;
                        if (chest.adjacentChestXNeg != null)
                            processedPositions.add(chest.adjacentChestXNeg.getPos());
                        if (chest.adjacentChestXPos != null)
                            processedPositions.add(chest.adjacentChestXPos.getPos());
                        if (chest.adjacentChestZNeg != null)
                            processedPositions.add(chest.adjacentChestZNeg.getPos());
                        if (chest.adjacentChestZPos != null)
                            processedPositions.add(chest.adjacentChestZPos.getPos());
                    }
                }
            }
        }

        int newChests = 0;
        for (BlockPos pos : foundChestPositions) {
            if (warehouse.getChestAt(pos) == null) {
                warehouse.chests.add(new ChestData(pos));
                newChests++;
            }
            snapshotLoadedChest(warehouse, pos);
        }

        // The previous implementation only appended newly found records. That
        // made removed chests stay visible forever. Remove records that are
        // absent from loaded chunks; unloaded chunks remain unknown and are
        // preserved until they can be inspected.
        int removedChests = 0;
        for (int i = warehouse.chests.size() - 1; i >= 0; i--) {
            ChestData chest = warehouse.chests.get(i);
            if (chest == null || chest.pos == null
                    || (mc.world.isBlockLoaded(chest.pos) && !foundChestPositions.contains(chest.pos))) {
                warehouse.chests.remove(i);
                removedChests++;
            }
        }

        mc.player.sendMessage(new TextComponentString(I18n.format("msg.warehouse.scan.done",
                foundChestPositions.size(), newChests)));
        if (removedChests > 0) {
            mc.player.sendMessage(new TextComponentString(I18n.format("msg.warehouse.scan.removed", removedChests)));
        }
        saveWarehouses();
    }

    /** Reads the currently loaded chest tile entities for existing records. */
    public static int scanUnscannedChestsInWarehouse(Warehouse warehouse) {
        if (warehouse == null || mc.world == null) {
            return 0;
        }
        int scanned = 0;
        for (ChestData chest : warehouse.chests) {
            if (chest != null && !chest.hasBeenScanned && snapshotLoadedChest(warehouse, chest.pos)) {
                scanned++;
            }
        }
        if (scanned > 0) {
            saveWarehouses();
        }
        return scanned;
    }

    /**
     * Region scanning used to record only chest positions. That left every row
     * marked “未扫描” until the player opened each chest manually. Loaded chest
     * tile entities already expose their current inventory, so capture that data
     * while scanning the region as well.
     */
    private static boolean snapshotLoadedChest(Warehouse warehouse, BlockPos pos) {
        if (warehouse == null || pos == null || mc.world == null) {
            return false;
        }
        ChestData data = warehouse.getChestAt(pos);
        TileEntity tile = mc.world.getTileEntity(pos);
        if (data == null || !(tile instanceof IInventory)) {
            return false;
        }
        IInventory inventory = (IInventory) tile;
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            items.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        data.snapshotContents(items);
        return true;
    }

    public static void checkBrokenChests(Warehouse warehouse) {
        boolean changed = warehouse.chests
                .removeIf(chestData -> !(mc.world.getBlockState(chestData.pos).getBlock() instanceof BlockChest));
        if (changed) {
            zszlScriptMod.LOGGER.info(I18n.format("log.warehouse.broken_chests_removed"), warehouse.name);
            saveWarehouses();
        }
    }
}
