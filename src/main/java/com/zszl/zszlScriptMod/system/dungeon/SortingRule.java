// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/dungeon/SortingRule.java
package com.zszl.zszlScriptMod.system.dungeon;

import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

public class SortingRule {
    public String name;
    public boolean enabled;
    public List<Integer> targetSlots; // 目标槽位列表
    public List<String> itemKeywords; // 物品关键词
    public MatchMode matchMode; // 匹配模式
    public ItemType itemType; // 物品类型

    public enum MatchMode {
        ANY, ALL
    }

    public enum ItemType {
        ANY, SHULKER_ONLY, NON_SHULKER_ONLY
    }

    public SortingRule() {
        this.name = I18n.format("rule.sorting.default_name");
        this.enabled = true;
        this.targetSlots = new ArrayList<>();
        this.itemKeywords = new ArrayList<>();
        this.matchMode = MatchMode.ANY;
        this.itemType = ItemType.ANY;
    }
}
