// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/dungeon/Warehouse.java
package com.zszl.zszlScriptMod.system.dungeon;

import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Warehouse {
    public String name;
    public String category;
    public boolean isActive;
    public double x1, z1, x2, z2;

    public List<ChestData> chests = new CopyOnWriteArrayList<>();

    public transient double minX, maxX, minZ, maxZ;
    
    // --- 核心新增：定义一个5格的边界容差 ---
    private static final double BOUNDARY_TOLERANCE = 5.0;

    public Warehouse() {
        this.category = "默认";
    }

    public Warehouse(String name, double x1, double z1, double x2, double z2) {
        this.name = name;
        this.category = "默认";
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.isActive = false;
        updateBounds();
    }

    public void updateBounds() {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
    }

    // --- 核心修改：在判断时加上容差 ---
    public boolean isPlayerInside(double playerX, double playerZ) {
        return playerX >= (minX - BOUNDARY_TOLERANCE) && playerX <= (maxX + BOUNDARY_TOLERANCE) &&
               playerZ >= (minZ - BOUNDARY_TOLERANCE) && playerZ <= (maxZ + BOUNDARY_TOLERANCE);
    }
    
    public boolean isPosInside(BlockPos pos) {
        return pos.getX() >= (minX - BOUNDARY_TOLERANCE) && pos.getX() <= (maxX + BOUNDARY_TOLERANCE) &&
               pos.getZ() >= (minZ - BOUNDARY_TOLERANCE) && pos.getZ() <= (maxZ + BOUNDARY_TOLERANCE);
    }
    // --- 修改结束 ---

    public ChestData getChestAt(BlockPos pos) {
        for (ChestData chest : chests) {
            if (chest.pos.equals(pos)) {
                return chest;
            }
        }
        return null;
    }
}