// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/dungeon/ChestData.java
package com.zszl.zszlScriptMod.system.dungeon;

import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.zszl.zszlScriptMod.zszlScriptMod;

public class ChestData {
    public BlockPos pos;
    public boolean hasBeenScanned = false;
    public String itemsNBTString = "";

    // !! 核心新增：为每个箱子添加整理规则列表和开关 !!
    public List<SortingRule> sortingRules = new ArrayList<>();
    public boolean sortEnabled = false;

    public Set<String> designatedItems = new HashSet<>();

    // !! 核心新增：为每个箱子添加独立的自动存入设置 !!
    public boolean autoDepositEnabled = false;
    public int depositFrequency = 100; // 默认点击间隔100ms

    public ChestData() {
    }

    public ChestData(BlockPos pos) {
        this.pos = pos;
    }

    public void snapshotContents(NonNullList<ItemStack> items) {
        NBTTagCompound compound = new NBTTagCompound();
        ItemStackHelper.saveAllItems(compound, items);
        this.itemsNBTString = compound.toString();
        this.hasBeenScanned = true;
    }

    public NonNullList<ItemStack> getSnapshotContents(int size) {
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        if (this.itemsNBTString == null || this.itemsNBTString.isEmpty()) {
            return items;
        }
        try {
            NBTTagCompound compound = JsonToNBT.getTagFromJson(this.itemsNBTString);
            ItemStackHelper.loadAllItems(compound, items);
        } catch (NBTException e) {
            zszlScriptMod.LOGGER.error("Failed to parse NBT snapshot from string for chest @ " + this.pos, e);
        }
        return items;
    }
}

