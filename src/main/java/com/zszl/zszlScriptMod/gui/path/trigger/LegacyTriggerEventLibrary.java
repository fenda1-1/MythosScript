package com.zszl.zszlScriptMod.gui.path.trigger;

import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;

import java.util.Arrays;
import java.util.List;

public final class LegacyTriggerEventLibrary {

    private LegacyTriggerEventLibrary() {
    }

    public static List<LegacyTriggerEventItem> createDefaultItems() {
        return Arrays.asList(
                new LegacyTriggerEventItem(true, "界面 / 聊天 / 网络", "", ""),
                new LegacyTriggerEventItem(false, "界面打开", LegacySequenceTriggerManager.TRIGGER_GUI_OPEN, "配置 GUI 标题或 GUI 类名。"),
                new LegacyTriggerEventItem(false, "界面关闭", LegacySequenceTriggerManager.TRIGGER_GUI_CLOSE, "配置关闭前 GUI 标题或 GUI 类名。"),
                new LegacyTriggerEventItem(false, "聊天消息", LegacySequenceTriggerManager.TRIGGER_CHAT, "配置聊天框显示文本，玩家消息、系统消息都可触发。"),
                new LegacyTriggerEventItem(false, "数据包", LegacySequenceTriggerManager.TRIGGER_PACKET, "配置包文本、频道、方向。"),
                new LegacyTriggerEventItem(false, "标题文本", LegacySequenceTriggerManager.TRIGGER_TITLE, "配置标题文本匹配。"),
                new LegacyTriggerEventItem(false, "动作栏提示", LegacySequenceTriggerManager.TRIGGER_ACTIONBAR, "配置快捷栏上方动作栏提示文本匹配。"),
                new LegacyTriggerEventItem(false, "Boss血条", LegacySequenceTriggerManager.TRIGGER_BOSSBAR, "配置屏幕顶部 Boss 血条标题文本匹配。"),
                new LegacyTriggerEventItem(true, "玩家状态", "", ""),
                new LegacyTriggerEventItem(false, "按键触发", LegacySequenceTriggerManager.TRIGGER_KEY_INPUT, "配置按键名称匹配。"),
                new LegacyTriggerEventItem(false, "站立不动", LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE, "当角色持续站立不动达到设定毫秒数时触发。"),
                new LegacyTriggerEventItem(false, "定时器", LegacySequenceTriggerManager.TRIGGER_TIMER, "配置触发间隔秒数。"),
                new LegacyTriggerEventItem(false, "低血量", LegacySequenceTriggerManager.TRIGGER_HP_LOW, "配置血量阈值。"),
                new LegacyTriggerEventItem(false, "低饱食度", LegacySequenceTriggerManager.TRIGGER_FOOD_LOW, "配置饱食度阈值。"),
                new LegacyTriggerEventItem(false, "受到伤害", LegacySequenceTriggerManager.TRIGGER_PLAYER_HURT, "配置伤害来源和最小伤害值。"),
                new LegacyTriggerEventItem(false, "攻击实体", LegacySequenceTriggerManager.TRIGGER_ATTACK_ENTITY, "配置目标实体文本。"),
                new LegacyTriggerEventItem(false, "击杀目标", LegacySequenceTriggerManager.TRIGGER_TARGET_KILL, "配置被击杀实体文本。"),
                new LegacyTriggerEventItem(false, "玩家列表", LegacySequenceTriggerManager.TRIGGER_PLAYER_LIST,
                        "当 Tab 玩家列表中存在匹配玩家时按冷却触发，可配置多个名称卡片并选择包含或全匹配。"),
                new LegacyTriggerEventItem(false, "死亡", LegacySequenceTriggerManager.TRIGGER_DEATH, "角色死亡时触发。"),
                new LegacyTriggerEventItem(false, "重生", LegacySequenceTriggerManager.TRIGGER_RESPAWN, "角色重生后触发。"),
                new LegacyTriggerEventItem(true, "世界 / 区域 / 背包", "", ""),
                new LegacyTriggerEventItem(false, "世界切换", LegacySequenceTriggerManager.TRIGGER_WORLD_CHANGED, "配置 from / to 世界文本。"),
                new LegacyTriggerEventItem(false, "Scoreboard变化", LegacySequenceTriggerManager.TRIGGER_SCOREBOARD_CHANGED, "配置侧边记分板文本匹配。"),
                new LegacyTriggerEventItem(false, "区域变化", LegacySequenceTriggerManager.TRIGGER_AREA_CHANGED, "配置 from / to 文本。"),
                new LegacyTriggerEventItem(false, "背包变化", LegacySequenceTriggerManager.TRIGGER_INVENTORY_CHANGED, "配置背包变化文本。"),
                new LegacyTriggerEventItem(false, "背包已满", LegacySequenceTriggerManager.TRIGGER_INVENTORY_FULL, "配置最少占满槽位数。"),
                new LegacyTriggerEventItem(false, "拾取物品", LegacySequenceTriggerManager.TRIGGER_ITEM_PICKUP, "配置物品文本和最少数量。"),
                new LegacyTriggerEventItem(false, "附近实体", LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY, "配置实体文本和最少数量。"),
                new LegacyTriggerEventItem(true, "连接状态", "", ""),
                new LegacyTriggerEventItem(false, "连接服务器", LegacySequenceTriggerManager.TRIGGER_SERVER_CONNECT, "连接到服务器时触发。"),
                new LegacyTriggerEventItem(false, "断开服务器", LegacySequenceTriggerManager.TRIGGER_SERVER_DISCONNECT, "与服务器断开连接时触发。"));
    }
}
