package com.zszl.zszlScriptMod.gui.path;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.path.node.NodeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NodeParameterSchemaRegistry {

        private NodeParameterSchemaRegistry() {
        }

        public enum FieldType {
                TEXT,
                NUMBER,
                BOOLEAN,
                SELECT,
                TEXTAREA,
                GRAPH_REF,
                KV_LINES,
                TAGS,
                COLOR
        }

        public static final class FieldSchema {
                private final String key;
                private final String label;
                private final FieldType type;
                private final boolean required;
                private final String tooltip;
                private final String validationRule;
                private final String defaultValue;
                private final String[] options;
                private final boolean paramsField;

                private FieldSchema(String key, String label, FieldType type, boolean required, String tooltip,
                                String validationRule, String defaultValue, String[] options, boolean paramsField) {
                        this.key = key;
                        this.label = label;
                        this.type = type;
                        this.required = required;
                        this.tooltip = tooltip == null ? "" : tooltip;
                        this.validationRule = validationRule == null ? "" : validationRule;
                        this.defaultValue = defaultValue == null ? "" : defaultValue;
                        this.options = options == null ? new String[0] : options;
                        this.paramsField = paramsField;
                }

                public static FieldSchema field(String key, String label, FieldType type, boolean required,
                                String tooltip,
                                String validationRule, String defaultValue, String... options) {
                        return new FieldSchema(key, label, type, required, tooltip, validationRule, defaultValue,
                                        options, false);
                }

                public static FieldSchema paramsField(String key, String label, FieldType type, boolean required,
                                String tooltip,
                                String validationRule, String defaultValue, String... options) {
                        return new FieldSchema(key, label, type, required, tooltip, validationRule, defaultValue,
                                        options, true);
                }

                public String getKey() {
                        return key;
                }

                public String getLabel() {
                        return label;
                }

                public FieldType getType() {
                        return type;
                }

                public boolean isRequired() {
                        return required;
                }

                public String getTooltip() {
                        return tooltip;
                }

                public String getValidationRule() {
                        return validationRule;
                }

                public String getDefaultValue() {
                        return defaultValue;
                }

                public String[] getOptions() {
                        return options;
                }

                public boolean isParamsField() {
                        return paramsField;
                }
        }

        public static List<FieldSchema> getSchemas(String nodeType, JsonObject currentData, List<String> graphNames) {
                String normalizedType = NodeNode.normalizeType(nodeType);
                JsonObject data = currentData == null ? new JsonObject() : currentData;
                List<FieldSchema> schemas = new ArrayList<>();

                if (NodeNode.TYPE_START.equals(normalizedType)) {
                        return schemas;
                }

                if (NodeNode.TYPE_END.equals(normalizedType)) {
                        schemas.addAll(commonConnectionSchemas("0"));
                        return schemas;
                }

                if (NodeNode.TYPE_TRIGGER.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("triggerType", "触发类型", FieldType.SELECT, true,
                                        "选择触发当前图的事件类型。", "必须是已支持的触发类型之一", "onchat",
                                        "onchat", "onpacket", "onguiopen", "onhplow", "ontimer",
                                        "oninventoryfull"));
                        schemas.add(FieldSchema.field("contains", "过滤内容", FieldType.TEXT, false,
                                        "当事件文本包含该内容时才触发。", "可为空", ""));
                        schemas.add(FieldSchema.field("throttleMs", "节流毫秒", FieldType.NUMBER, false,
                                        "同一触发器两次触发的最小间隔。", ">= 0", "1000"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_ACTION.equals(normalizedType)) {
                        String actionType = readString(data, "actionType", "type", "action");
                        if (actionType.isEmpty()) {
                                actionType = "command";
                        }
                        schemas.add(FieldSchema.field("actionType", "动作类型", FieldType.SELECT, true,
                                        "选择要执行的动作。", "必须是受支持的动作类型", actionType,
                                        "command", "system_message", "delay", "key", "jump", "click", "window_click",
                                        "conditional_window_click", "setview", "rightclickblock", "rightclickentity",
                                        "takeallitems",
                                         "take_all_items_safe", "dropfiltereditems", "autochestclick",
                                         "move_inventory_items_to_chest_slots",
                                         "pickup_nearby_items",
                                         "warehouse_auto_deposit",
                                        "blocknextgui", "hidecurrentgui", "showhiddengui", "close_container_window", "hud_text_check", "autoeat",
                                        "autoequip", "autopickup",
                                        "toggle_autoeat", "toggle_autofishing", "toggle_auto_pickup",
                                        "toggle_kill_aura", "toggle_auto_follow", "toggle_fly",
                                        "toggle_conditional_execution",
                                        "toggle_auto_escape",
                                        "toggle_baritone_free_look",
                                        "toggle_baritone_human_like",
                                        "toggle_baritone_flight",
                                        "runlastsequence", "run_sequence", "stop_current_sequence",
                                        "sequence_control",
                                        "goto_action", "skip_actions", "skip_steps", "repeat_actions",
                                        "restart_sequence",
                                        "silentuse", "switch_hotbar_slot", "use_hotbar_item",
                                        "move_inventory_item_to_hotbar", "use_held_item", "hunt",
                                        "send_packet"));
                        schemas.addAll(getActionParamSchemas(actionType, graphNames));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_IF.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("mode", "编辑模式", FieldType.SELECT, false,
                                        "简单模式使用左右值比较；高级模式填写 expression。", "simple / advanced", "simple",
                                        "simple", "advanced"));
                        schemas.add(FieldSchema.field("leftVar", "左值变量", FieldType.TEXT, false,
                                        "简单模式下使用的左值变量名。", "可为空，但简单模式下建议填写", "x"));
                        schemas.add(FieldSchema.field("operator", "操作符", FieldType.SELECT, false,
                                        "简单模式比较使用的操作符。", "==, !=, >, <, >=, <=", "==",
                                        "==", "!=", ">", "<", ">=", "<="));
                        schemas.add(FieldSchema.field("right", "右值", FieldType.TEXT, false,
                                        "简单模式下右侧比较值。", "按 rightType 解析", "1"));
                        schemas.add(FieldSchema.field("rightType", "右值类型", FieldType.SELECT, false,
                                        "决定右值如何解析。", "string / number / boolean", "number",
                                        "string", "number", "boolean"));
                        schemas.add(FieldSchema.field("expression", "高级表达式", FieldType.TEXTAREA, false,
                                        "高级模式表达式，例如 ready == true && hp > 10", "表达式语法需合法", ""));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_SET_VAR.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("name", "变量名", FieldType.TEXT, true,
                                        "要写入的变量名称。", "不能为空", "x"));
                        schemas.add(FieldSchema.field("scope", "作用域", FieldType.SELECT, false,
                                        "global 写入全局变量；其他值写入 scopedVariables。", "global / local / graph", "global",
                                        "global", "local", "graph"));
                        schemas.add(FieldSchema.field("valueType", "值类型", FieldType.SELECT, false,
                                        "决定 value 的解析方式。", "string / number / boolean", "number",
                                        "string", "number", "boolean"));
                        schemas.add(FieldSchema.field("value", "值", FieldType.TEXTAREA, false,
                                        "直接写入的值。若为空且 fromVar 有值，则取自 fromVar。", "与 valueType 对应", "1"));
                        schemas.add(FieldSchema.field("fromVar", "来源变量", FieldType.TEXT, false,
                                        "可选：从已有变量读取值。", "可为空", ""));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_WAIT_UNTIL.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("mode", "编辑模式", FieldType.SELECT, false,
                                        "简单模式使用左右值比较；高级模式填写 expression。", "simple / advanced", "simple",
                                        "simple", "advanced"));
                        schemas.add(FieldSchema.field("leftVar", "左值变量", FieldType.TEXT, false,
                                        "简单模式下使用的左值变量名。", "可为空", "ready"));
                        schemas.add(FieldSchema.field("operator", "操作符", FieldType.SELECT, false,
                                        "简单模式比较使用的操作符。", "==, !=, >, <, >=, <=", "==",
                                        "==", "!=", ">", "<", ">=", "<="));
                        schemas.add(FieldSchema.field("right", "右值", FieldType.TEXT, false,
                                        "简单模式下右侧比较值。", "按 rightType 解析", "true"));
                        schemas.add(FieldSchema.field("rightType", "右值类型", FieldType.SELECT, false,
                                        "决定右值如何解析。", "string / number / boolean", "boolean",
                                        "string", "number", "boolean"));
                        schemas.add(FieldSchema.field("expression", "高级表达式", FieldType.TEXTAREA, false,
                                        "高级模式表达式，例如 targetReady == true", "表达式语法需合法", ""));
                        schemas.add(FieldSchema.field("timeoutTicks", "超时 Tick", FieldType.NUMBER, false,
                                        "等待超过此时间后走 timeout 分支。", ">= -1", "100"));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_RUN_SEQUENCE.equals(normalizedType)
                                || NodeNode.TYPE_SUBGRAPH.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("graphName", "目标图", FieldType.GRAPH_REF, true,
                                        "选择要调用的节点图。", "必须存在于节点图列表中",
                                        firstGraphName(graphNames, "node_graph_1"), toArray(graphNames)));
                        schemas.add(FieldSchema.field("argsText", "入参映射", FieldType.KV_LINES, false,
                                        "每行一个参数，格式：key=value", "将转换为 args 对象", ""));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_PARALLEL.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("branchesText", "子图列表", FieldType.KV_LINES, false,
                                        "每行一个并行分支，格式：分支名=图名", "将转换为 branches 对象", ""));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_JOIN.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("joinKey", "汇聚 Key", FieldType.TEXT, false,
                                        "同一组 Join 使用相同的 joinKey。", "可为空，默认为节点 ID", "join_1"));
                        schemas.add(FieldSchema.field("required", "需要到达数", FieldType.NUMBER, false,
                                        "达到多少次后继续执行。", ">= 1", "2"));
                        schemas.addAll(commonConnectionSchemas("0"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_DELAY_TASK.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("delayTicks", "延迟 Tick", FieldType.NUMBER, false,
                                        "以 Tick 为单位的延迟。若 delayMs > 0，则优先使用 delayMs。", ">= 0", "20"));
                        schemas.add(FieldSchema.field("delayMs", "延迟毫秒", FieldType.NUMBER, false,
                                        "以毫秒为单位的延迟。", ">= 0", "0"));
                        schemas.add(FieldSchema.field("timeoutTicks", "超时 Tick", FieldType.NUMBER, false,
                                        "超时后走 timeout 分支。", ">= -1", "200"));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_RESOURCE_LOCK.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("resourceKey", "资源 Key", FieldType.TEXT, true,
                                        "需要竞争的资源锁名称。", "不能为空", "resource_1"));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                if (NodeNode.TYPE_RESULT_CACHE.equals(normalizedType)) {
                        schemas.add(FieldSchema.field("cacheKey", "缓存 Key", FieldType.TEXT, true,
                                        "缓存命中的唯一 key。", "不能为空", "cache_1"));
                        schemas.add(FieldSchema.field("value", "写入值", FieldType.BOOLEAN, false,
                                        "缓存未命中时要写入的值。", "true / false", "true"));
                        schemas.addAll(commonConnectionSchemas("1"));
                        schemas.addAll(commonErrorSchemas());
                        return schemas;
                }

                return schemas;
        }

        private static List<FieldSchema> getActionParamSchemas(String actionType, List<String> graphNames) {
                String normalized = actionType == null ? "" : actionType.trim().toLowerCase();
                List<FieldSchema> schemas = new ArrayList<>();

                if ("command".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("command", "命令内容", FieldType.TEXTAREA, true,
                                        "例如 /help 或 !goto 0 64 0", "不能为空", "/help"));
                } else if ("system_message".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("message", "提示文本", FieldType.TEXTAREA, true,
                                        "显示给玩家的文本内容。", "不能为空", "提示消息"));
                } else if ("delay".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("ticks", "延迟 Tick", FieldType.NUMBER, true,
                                        "PathSequence 动作延迟 Tick。", ">= 0", "20"));
                        schemas.add(FieldSchema.paramsField("normalizeDelayTo20Tps", "按20TPS基准自适应", FieldType.BOOLEAN, false,
                                        "开启后会根据当前 Timer 倍率自动补偿 Tick 数，让延迟保持原版 20TPS 的时间基准。", "true / false", "true"));
                } else if ("key".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("key", "按键名", FieldType.TEXT, true,
                                        "例如 W / SPACE / E。", "不能为空", "W"));
                        schemas.add(FieldSchema.paramsField("state", "按键状态", FieldType.SELECT, true,
                                        "单击会自动按下并在 10 ticks 后抬起。", "press / down / up", "press",
                                        "press", "down", "up"));
                } else if ("goto_action".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("targetActionIndex", "目标动作序号", FieldType.NUMBER, true,
                                        "跳转到当前步骤内的目标动作序号，从 0 开始。", ">= 0", "0"));
                } else if ("skip_actions".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("count", "跳过动作数", FieldType.NUMBER, true,
                                        "跳过当前动作后面的多少个动作。", ">= 0", "1"));
                } else if ("skip_steps".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("count", "跳过步骤数", FieldType.NUMBER, true,
                                        "0 表示立即结束当前步骤并进入下一步骤；1 表示额外再跳过一个步骤。", ">= 0", "0"));
                } else if ("repeat_actions".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("count", "循环次数", FieldType.NUMBER, true,
                                        "动作块总循环次数。", ">= 0", "2"));
                        schemas.add(FieldSchema.paramsField("bodyCount", "循环体动作数", FieldType.NUMBER, true,
                                        "从当前动作的下一个动作开始，连续多少个动作属于循环体。", ">= 1", "1"));
                        schemas.add(FieldSchema.paramsField("loopVar", "循环变量名", FieldType.TEXT, false,
                                        "每轮循环自动写入该变量名。", "可为空", "loop_index"));
                } else if ("jump".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("count", "跳跃次数", FieldType.NUMBER, false,
                                        "总跳跃次数。", ">= 1", "1"));
                        schemas.add(FieldSchema.paramsField("intervalTicks", "间隔 Tick", FieldType.NUMBER, false,
                                        "每次跳跃之间的间隔。", ">= 0", "0"));
                } else if ("click".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("x", "屏幕 X", FieldType.NUMBER, true,
                                        "录制时参考分辨率下的 X 坐标。", "数字", "960"));
                        schemas.add(FieldSchema.paramsField("y", "屏幕 Y", FieldType.NUMBER, true,
                                        "录制时参考分辨率下的 Y 坐标。", "数字", "540"));
                        schemas.add(FieldSchema.paramsField("left", "鼠标按键", FieldType.SELECT, false,
                                        "left=左键；right=右键；middle=中键。", "left / right / middle", "left",
                                        "left", "right", "middle"));
                        schemas.add(FieldSchema.paramsField("originalWidth", "参考宽度", FieldType.NUMBER, false,
                                        "录制时的屏幕宽度。", ">= 1", "2560"));
                        schemas.add(FieldSchema.paramsField("originalHeight", "参考高度", FieldType.NUMBER, false,
                                        "录制时的屏幕高度。", ">= 1", "1334"));
                } else if ("window_click".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("windowId", "窗口 ID", FieldType.TEXT, false,
                                        "目标容器窗口 ID。", "可为空", "-1"));
                        schemas.add(FieldSchema.paramsField("slot", "槽位", FieldType.TEXT, true,
                                        "槽位号，允许字符串形式。", "不能为空", "0"));
                        schemas.add(FieldSchema.paramsField("slotBase", "槽位进制", FieldType.SELECT, false,
                                        "槽位号解析进制。", "DEC / HEX", "DEC",
                                        "DEC", "HEX"));
                        schemas.add(FieldSchema.paramsField("contains", "物品名包含", FieldType.TEXT, false,
                                        "留空时直接点击；填写后仅当槽位内物品名包含该文本时才点击。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("button", "按钮值", FieldType.NUMBER, false,
                                        "原版 windowClick button 参数。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("clickType", "点击类型", FieldType.SELECT, false,
                                        "点击方式。", "普通点击 / Shift快速移动 / 数字键交换 / 丢弃 / 收集同类 / 拖拽分发 / 创造复制", "PICKUP",
                                        "PICKUP", "QUICK_MOVE", "SWAP", "THROW", "PICKUP_ALL", "QUICK_CRAFT",
                                        "CLONE"));
                        schemas.add(FieldSchema.paramsField("onlyOnSlotChange", "仅槽位变化时执行", FieldType.BOOLEAN, false,
                                        "为 true 时仅在槽位变化时触发。", "true / false", "false"));
                } else if ("conditional_window_click".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("windowId", "窗口 ID", FieldType.TEXT, false,
                                        "目标容器窗口 ID，-1 表示当前窗口。", "可为空", "-1"));
                        schemas.add(FieldSchema.paramsField("slot", "槽位", FieldType.TEXT, true,
                                        "要检查并点击的槽位号。", "不能为空", "0"));
                        schemas.add(FieldSchema.paramsField("slotBase", "槽位进制", FieldType.SELECT, false,
                                        "槽位号解析进制。", "DEC / HEX", "DEC",
                                        "DEC", "HEX"));
                        schemas.add(FieldSchema.paramsField("contains", "物品名包含", FieldType.TEXT, false,
                                        "仅当槽位内物品名称包含该文本时才点击。留空表示只要有物品就点击。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("button", "按钮值", FieldType.NUMBER, false,
                                        "原版 windowClick button 参数。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("clickType", "点击类型", FieldType.SELECT, false,
                                        "点击方式。", "普通点击 / Shift快速移动 / 数字键交换 / 丢弃 / 收集同类 / 拖拽分发 / 创造复制", "PICKUP",
                                        "PICKUP", "QUICK_MOVE", "SWAP", "THROW", "PICKUP_ALL", "QUICK_CRAFT",
                                        "CLONE"));
                } else if ("setview".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("yaw", "Yaw", FieldType.NUMBER, true,
                                        "水平朝向。", "数字", "0"));
                        schemas.add(FieldSchema.paramsField("pitch", "Pitch", FieldType.NUMBER, true,
                                        "俯仰角。", "数字", "0"));
                } else if ("rightclickblock".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("pos", "方块坐标", FieldType.TEXT, true,
                                        "目标方块坐标，格式：x,y,z", "不能为空，使用英文逗号分隔", "0,64,0"));
                        schemas.add(FieldSchema.paramsField("preserveView", "不移动视角", FieldType.BOOLEAN, false,
                                        "开启后右键方块时不主动改变玩家当前视角。", "true / false", "false"));
                } else if ("rightclickentity".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("pos", "目标中心坐标", FieldType.TEXT, true,
                                        "实体搜索中心坐标，格式：x,y,z", "不能为空，使用英文逗号分隔", "0,64,0"));
                        schemas.add(FieldSchema.paramsField("range", "搜索半径", FieldType.NUMBER, false,
                                        "在该范围内查找最近实体并右键交互。", ">= 0", "3"));
                        schemas.add(FieldSchema.paramsField("preserveView", "不移动视角", FieldType.BOOLEAN, false,
                                        "开启后右键实体时不主动改变玩家当前视角。", "true / false", "false"));
                } else if ("takeallitems".equals(normalized)) {
                } else if ("take_all_items_safe".equals(normalized)) {
                } else if ("dropfiltereditems".equals(normalized)) {
                } else if ("autochestclick".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("slot", "槽位", FieldType.NUMBER, true,
                                        "需要自动点击的容器槽位。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("delayTicks", "延迟 Tick", FieldType.NUMBER, false,
                                        "点击前等待的 Tick 数；为 0 时会尝试在同一 Tick 连续发送后续箱子点击。", ">= 0",
                                        "1"));
                        schemas.add(FieldSchema.paramsField("clickType", "点击类型", FieldType.SELECT, false,
                                        "点击方式。", "普通点击 / Shift快速移动 / 数字键交换 / 丢弃 / 收集同类 / 拖拽分发 / 创造复制", "PICKUP",
                                        "PICKUP", "QUICK_MOVE", "SWAP", "THROW", "PICKUP_ALL", "QUICK_CRAFT",
                                        "CLONE"));
                 } else if ("move_inventory_items_to_chest_slots".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("delayTicks", "每步延迟 Tick", FieldType.NUMBER, false,
                                        "每次拾取/放置点击之间的延迟。", ">= 0", "2"));
                        schemas.add(FieldSchema.paramsField("normalizeDelayTo20Tps", "按20TPS基准自适应", FieldType.BOOLEAN, false,
                                        "开启后会根据当前 Timer 倍率自动补偿 Tick 数，让延迟保持原版 20TPS 的时间基准。", "true / false", "true"));
                        schemas.add(FieldSchema.paramsField("chestRows", "容器行数", FieldType.NUMBER, false,
                                        "容器槽位网格行数。", "1 ~ 12", "6"));
                        schemas.add(FieldSchema.paramsField("chestCols", "容器列数", FieldType.NUMBER, false,
                                        "容器槽位网格列数。", "1 ~ 18", "9"));
                        schemas.add(FieldSchema.paramsField("inventoryRows", "背包行数", FieldType.NUMBER, false,
                                        "背包槽位网格行数。", "1 ~ 12", "4"));
                        schemas.add(FieldSchema.paramsField("inventoryCols", "背包列数", FieldType.NUMBER, false,
                                        "背包槽位网格列数。", "1 ~ 18", "9"));
                        schemas.add(FieldSchema.paramsField("chestSlotsText", "容器槽位列表", FieldType.TEXTAREA, false,
                                        "根据移动方向决定是来源容器槽位还是目标容器槽位，用逗号、空格或换行填写编号，例如 0,1,2,9。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("inventorySlotsText", "背包槽位列表", FieldType.TEXTAREA, false,
                                        "根据移动方向决定是来源背包槽位还是目标背包槽位，用逗号、空格或换行填写编号，例如 0,1,9,27。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("moveDirection", "移动方向", FieldType.SELECT, false,
                                        "选择物品移动方向。默认背包->容器，也可切换为容器->背包。", "INVENTORY_TO_CHEST / CHEST_TO_INVENTORY",
                                        "INVENTORY_TO_CHEST", "INVENTORY_TO_CHEST", "CHEST_TO_INVENTORY"));
                        schemas.add(FieldSchema.paramsField("button", "按钮值", FieldType.NUMBER, false,
                                        "原版 windowClick button 参数；通常 0=左键、1=右键，SWAP 模式为快捷栏槽位。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("clickType", "点击类型", FieldType.SELECT, false,
                                        "批量移动使用的 windowClick 类型。普通点击会按所选目标槽位搬运；其他类型会直接点击匹配的来源槽位。",
                                        "普通点击 / Shift快速移动 / 数字键交换 / 丢弃 / 收集同类 / 拖拽分发 / 创造复制", "PICKUP",
                                        "PICKUP", "QUICK_MOVE", "SWAP", "THROW", "PICKUP_ALL", "QUICK_CRAFT",
                                        "CLONE"));
                        schemas.add(FieldSchema.paramsField("itemName", "物品名", FieldType.TEXT, false,
                                        "按物品显示名称包含匹配，忽略颜色代码。与 NBT 条件至少填写一个。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("requiredNbtTagsMode", "NBT匹配模式", FieldType.SELECT, false,
                                        "包含=只移动匹配关键字的物品；不包含=排除匹配关键字的物品。", "包含 / 不包含", "CONTAINS",
                                        "CONTAINS", "NOT_CONTAINS"));
                         schemas.add(FieldSchema.paramsField("requiredNbtTagsText", "NBT标签条件", FieldType.TEXT, false,
                                         "输入 NBT/tooltip/附魔/物品 ID 关键字。与物品名至少填写一个。", "可为空", ""));
                 } else if ("pickup_nearby_items".equals(normalized)) {
                         schemas.add(FieldSchema.paramsField("itemFilterExpression", "物品过滤表达式",
                                         FieldType.TEXTAREA, true,
                                         "填写一条与动作库物品过滤表达式相同语法的规则。", "不能为空",
                                         "nameContains(\"材料\")"));
                         schemas.add(FieldSchema.paramsField("searchRadius", "搜索半径", FieldType.NUMBER, false,
                                         "以动作开始时玩家位置为中心的掉落物搜索半径。", "> 0", "16"));
                         schemas.add(FieldSchema.paramsField("maxItems", "最多拾取数量", FieldType.NUMBER, false,
                                         "0 表示不限。", ">= 0", "0"));
                         schemas.add(FieldSchema.paramsField("timeoutSeconds", "超时秒数", FieldType.NUMBER, false,
                                         "0 表示不限制。", ">= 0", "30"));
                         schemas.add(FieldSchema.paramsField("reachDistance", "到达距离", FieldType.NUMBER, false,
                                         "到达此距离后停止寻路，等待服务器拾取。", "> 0", "0.5"));
                 } else if ("warehouse_auto_deposit".equals(normalized)) {
                } else if ("blocknextgui".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("count", "屏蔽次数", FieldType.NUMBER, false,
                                        "后续要屏蔽的 GUI 打开次数。", ">= 1", "1"));
                        schemas.add(FieldSchema.paramsField("blockCurrentGui", "是否屏蔽当前GUI", FieldType.BOOLEAN, false,
                                        "开启后，动作执行时如果当前已有打开的 GUI，会立即将其屏蔽并消耗 1 次次数。", "true / false",
                                        "false"));
                } else if ("hud_text_check".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("contains", "包含文本", FieldType.TEXT, false,
                                        "仅输出包含该关键字的 HUD 文本。留空则匹配全部。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("matchBlock", "按文本块匹配", FieldType.BOOLEAN, false,
                                        "开启后按合并后的文本块进行识别，关闭则按逐条文本识别。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("separator", "文本块分隔符", FieldType.TEXT, false,
                                        "按文本块拼接时使用的连接符。", "可为空", " | "));
                } else if ("autoeat".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "启用自动吃", FieldType.BOOLEAN, false,
                                        "关闭时表示禁用自动吃食物。", "true / false", "true"));
                        schemas.add(FieldSchema.paramsField("foodLevelThreshold", "饥饿阈值", FieldType.NUMBER, false,
                                        "当饥饿值小于等于该值时触发自动进食。", "0 ~ 20", "12"));
                        schemas.add(FieldSchema.paramsField("autoMoveFoodEnabled", "自动移动食物", FieldType.BOOLEAN, false,
                                        "食物不在快捷栏时，是否尝试自动移动到快捷栏。", "true / false", "true"));
                        schemas.add(FieldSchema.paramsField("eatWithLookDown", "低头进食", FieldType.BOOLEAN, false,
                                        "进食时是否自动低头。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("targetHotbarSlot", "目标快捷栏槽位", FieldType.NUMBER, false,
                                        "自动移动食物时使用的快捷栏槽位。", "1 ~ 9", "9"));
                        schemas.add(FieldSchema.paramsField("foodKeywordsText", "食物关键字", FieldType.KV_LINES, false,
                                        "每行或逗号分隔一个食物关键字。", "留空则使用当前配置", ""));
                 } else if ("autoequip".equals(normalized)) {
                         schemas.add(FieldSchema.paramsField("enabled", "启用自动装备", FieldType.BOOLEAN, false,
                                         "关闭时表示禁用自动装备。", "true / false", "true"));
                } else if ("autopickup".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "启用自动拾取", FieldType.BOOLEAN, false,
                                        "关闭时表示禁用自动拾取。", "true / false", "true"));
                } else if ("toggle_autoeat".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "自动进食开关", FieldType.BOOLEAN, false,
                                        "true=开启自动进食，false=关闭自动进食。", "true / false", "true"));
                } else if ("toggle_autofishing".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "自动钓鱼开关", FieldType.BOOLEAN, false,
                                        "true=开启自动钓鱼，false=关闭自动钓鱼。", "true / false", "true"));
                } else if ("toggle_auto_pickup".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "自动拾取掉落物开关", FieldType.BOOLEAN, false,
                                        "true=开启自动拾取掉落物，false=关闭自动拾取掉落物。", "true / false", "true"));
                } else if ("toggle_kill_aura".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("presetName", "预设名称", FieldType.TEXT, false,
                                        "开启时应用该杀戮光环预设；留空则沿用当前配置。", "已保存的预设名称", ""));
                        schemas.add(FieldSchema.paramsField("enabled", "杀戮光环开关", FieldType.BOOLEAN, false,
                                        "true=开启杀戮光环，false=关闭杀戮光环。", "true / false", "true"));
                } else if ("toggle_auto_follow".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "自动追怪开关", FieldType.BOOLEAN, false,
                                        "true=开启自动追怪，false=关闭自动追怪。", "true / false", "true"));
                } else if ("toggle_fly".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "飞行开关", FieldType.BOOLEAN, false,
                                        "true=开启飞行，false=关闭飞行。", "true / false", "true"));
                } else if ("toggle_conditional_execution".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "条件执行总开关", FieldType.BOOLEAN, false,
                                        "true=开启条件执行，false=关闭条件执行。", "true / false", "true"));
                } else if ("toggle_auto_escape".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "自动逃离总开关", FieldType.BOOLEAN, false,
                                        "true=开启自动逃离检测，false=关闭自动逃离检测。", "true / false", "true"));
                } else if ("toggle_baritone_free_look".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "Baritone自由视角开关", FieldType.BOOLEAN, false,
                                        "true=开启 Baritone 自由视角，false=关闭 Baritone 自由视角。", "true / false", "true"));
                } else if ("toggle_baritone_human_like".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "Baritone模拟真人开关", FieldType.BOOLEAN, false,
                                        "true=开启 Baritone 模拟真人，false=关闭 Baritone 模拟真人。", "true / false", "true"));
                } else if ("toggle_baritone_flight".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("enabled", "飞行寻路开关", FieldType.BOOLEAN, false,
                                        "true=开启创造模式风格飞行寻路，false=恢复地面寻路。", "true / false", "true"));
                } else if ("runlastsequence".equals(normalized)) {
                } else if ("run_sequence".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("sequenceName", "序列名", FieldType.GRAPH_REF, true,
                                        "选择要执行的旧序列或图名。", "必须存在",
                                        firstGraphName(graphNames, "node_graph_1"), toArray(graphNames)));
                        schemas.add(FieldSchema.paramsField("executeMode", "执行方式", FieldType.SELECT, false,
                                        "always=每次执行；interval=按间隔次数执行。", "always / interval", "always",
                                        "always", "interval"));
                        schemas.add(FieldSchema.paramsField("executeEveryCount", "每N次执行1次", FieldType.NUMBER, false,
                                        "仅在 interval 模式下生效。", ">= 1", "1"));
                        schemas.add(FieldSchema.paramsField("backgroundExecution", "后台执行", FieldType.BOOLEAN, false,
                                        "开启后不等待目标序列结束，直接继续当前动作流。", "true / false", "false"));
                } else if ("stop_current_sequence".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("targetScope", "停止范围", FieldType.SELECT, false,
                                        "foreground=停止当前前台序列；background=停止当前后台序列。",
                                        "foreground / background", "foreground",
                                        "foreground", "background"));
                } else if ("sequence_control".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("targetScope", "控制目标", FieldType.SELECT, false,
                                        "foreground=控制当前前台序列；background=控制当前后台序列。",
                                        "foreground / background", "foreground",
                                        "foreground", "background"));
                        schemas.add(FieldSchema.paramsField("operation", "控制操作", FieldType.SELECT, false,
                                        "pause=暂停；resume=恢复。",
                                        "pause / resume", "pause",
                                        "pause", "resume"));
                } else if ("silentuse".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("item", "物品名", FieldType.TEXT, true,
                                        "要静默使用的物品名称。", "不能为空", ""));
                        schemas.add(FieldSchema.paramsField("tempslot", "临时槽位", FieldType.NUMBER, false,
                                        "切换物品时使用的临时槽位。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("switchDelayTicks", "切换物品延迟", FieldType.NUMBER, false,
                                        "换到临时槽位后等待多少 ticks 再切到该物品。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("useDelayTicks", "切后使用延迟", FieldType.NUMBER, false,
                                        "切到目标物品后等待多少 ticks 再执行使用。", ">= 0", "1"));
                        schemas.add(FieldSchema.paramsField("switchBackDelayTicks", "切回延迟", FieldType.NUMBER, false,
                                        "使用后等待多少 ticks 再切回原物品。", ">= 0", "0"));
                } else if ("switch_hotbar_slot".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("targetHotbarSlot", "目标快捷栏位", FieldType.NUMBER, true,
                                        "输入 1-9，对应快捷栏槽位编号。", "1 ~ 9", "1"));
                        schemas.add(FieldSchema.paramsField("useAfterSwitch", "切换后使用物品", FieldType.BOOLEAN, false,
                                        "开启后切换到目标快捷栏位后会自动使用当前主手物品。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("useAfterSwitchDelayTicks", "切后使用延迟", FieldType.NUMBER,
                                        false, "切换快捷栏后等待多少 ticks 再使用物品。", ">= 0", "0"));
                } else if ("use_hotbar_item".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("itemName", "物品名", FieldType.TEXT, true,
                                        "要在快捷栏中匹配并使用的物品名称。", "不能为空", ""));
                        schemas.add(FieldSchema.paramsField("matchMode", "匹配模式", FieldType.SELECT, false,
                                        "CONTAINS=包含匹配；EXACT=完全匹配。", "CONTAINS / EXACT", "CONTAINS",
                                        "CONTAINS", "EXACT"));
                        schemas.add(FieldSchema.paramsField("useMode", "使用方式", FieldType.SELECT, false,
                                        "RIGHT_CLICK=右键使用；LEFT_CLICK=左键动作。", "RIGHT_CLICK / LEFT_CLICK",
                                        "RIGHT_CLICK", "RIGHT_CLICK", "LEFT_CLICK"));
                        schemas.add(FieldSchema.paramsField("changeLocalSlot", "更改本地物品槽位", FieldType.BOOLEAN,
                                        false, "true=同步修改客户端本地选中的快捷栏槽位；false=仅发包不改本地显示。", "true / false",
                                        "false"));
                        schemas.add(FieldSchema.paramsField("count", "使用次数", FieldType.NUMBER, false,
                                        "至少 1。", ">= 1", "1"));
                        schemas.add(FieldSchema.paramsField("switchItemDelayTicks", "切换物品延迟", FieldType.NUMBER,
                                        false, "切到目标快捷栏物品前先等待多少 ticks。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("switchDelayTicks", "切后使用延迟", FieldType.NUMBER,
                                        false, "切到目标快捷栏物品后等待多少 ticks 再使用。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("switchBackDelayTicks", "切回延迟", FieldType.NUMBER,
                                        false, "使用后等待多少 ticks 再切回原物品。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("intervalTicks", "使用间隔", FieldType.NUMBER, false,
                                        "多次使用之间的 tick 间隔。", ">= 0", "0"));
                } else if ("move_inventory_item_to_hotbar".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("itemName", "物品名", FieldType.TEXT, true,
                                        "要移动到快捷栏的物品名称。", "不能为空", ""));
                        schemas.add(FieldSchema.paramsField("matchMode", "匹配模式", FieldType.SELECT, false,
                                        "CONTAINS=包含匹配；EXACT=完全匹配。", "CONTAINS / EXACT", "CONTAINS",
                                        "CONTAINS", "EXACT"));
                        schemas.add(FieldSchema.paramsField("targetHotbarSlot", "目标快捷栏位", FieldType.NUMBER, true,
                                        "输入 1-9，对应快捷栏槽位编号。", "1 ~ 9", "1"));
                        schemas.add(FieldSchema.paramsField("count", "切换数量", FieldType.NUMBER, false,
                                        "0=移动整堆；正数=最多移动指定数量。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("skipIfTargetOccupied", "已有物品不移动",
                                        FieldType.BOOLEAN, false,
                                        "目标快捷栏已有任意物品时跳过本次移动。", "true / false", "true"));
                } else if ("use_held_item".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("delayTicks", "延迟", FieldType.NUMBER, false,
                                        "使用当前主手物品前的延迟。", ">= 0", "0"));
                } else if ("hunt".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("radius", "搜索半径", FieldType.NUMBER, false,
                                        "中心搜怪固定范围半径。", ">= 0", "3"));
                        schemas.add(FieldSchema.paramsField("huntUpRange", "向上追击范围", FieldType.NUMBER, false,
                                        "只允许选择比动作触发坐标高出该范围内的目标。", ">= 0", "1"));
                        schemas.add(FieldSchema.paramsField("huntDownRange", "向下追击范围", FieldType.NUMBER, false,
                                        "只允许选择比动作触发坐标低出该范围内的目标。", ">= 0", "1"));
                        schemas.add(FieldSchema.paramsField("scanRadius", "扫描半径", FieldType.NUMBER, false,
                                        "“扫描附近实体”按钮使用的范围。", ">= 0", "10"));
                        schemas.add(FieldSchema.paramsField("noTargetSkipCount", "无目标时跳过动作数", FieldType.NUMBER, false,
                                        "搜不到目标时额外跳过后续几个动作。0 表示不跳过。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("enableNameWhitelist", "启用名称白名单", FieldType.BOOLEAN, false,
                                        "启用后仅匹配中心搜怪动作自己的白名单，不读取杀戮光环名称白名单。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("nameWhitelistText", "名称白名单", FieldType.TEXTAREA, false,
                                        "每行或逗号分隔一个关键字，按包含匹配。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("waitForWhitelistRespawn", "等待白名单目标复活", FieldType.BOOLEAN,
                                        false, "仅启用名称白名单时生效；击杀目标完成后等待匹配实体重新出现。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("whitelistRespawnTimeoutSeconds", "复活等待超时（秒）", FieldType.NUMBER,
                                        false, "等待白名单目标重新出现的最长时间。", ">= 0", "120"));
                        schemas.add(FieldSchema.paramsField("waitForWhitelistRespawnAfterCompletion", "击杀完成后等待复活",
                                        FieldType.BOOLEAN, false, "开启后，完成白名单击杀目标时也等待匹配实体重新出现。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("confirmKillCompletion", "击杀确认", FieldType.BOOLEAN, false,
                                "完成白名单目标后额外确认一段时间；期间目标重新出现时继续攻击。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("killCompletionConfirmSeconds", "击杀确认时间（秒）", FieldType.NUMBER,
                                        false, "击杀确认持续时间；默认 3 秒。", ">= 0", "3"));
                        schemas.add(FieldSchema.paramsField("forceEndHunt", "启用强制结束", FieldType.BOOLEAN, false,
                                        "从开始搜索起计时，到时立即结束并继续后续动作。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("forceEndHuntTimeoutSeconds", "强制结束时间（秒）", FieldType.NUMBER,
                                        false, "启用强制结束后生效。", ">= 0", "60"));
                        schemas.add(FieldSchema.paramsField("enableNameBlacklist", "启用名称黑名单", FieldType.BOOLEAN, false,
                                        "启用后忽略中心搜怪动作自己的黑名单，不读取杀戮光环名称黑名单。", "true / false", "false"));
                        schemas.add(FieldSchema.paramsField("nameBlacklistText", "名称黑名单", FieldType.TEXTAREA, false,
                                        "每行或逗号分隔一个关键字，按包含匹配。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("showHuntRange", "显示半径光环", FieldType.BOOLEAN, false,
                                        "显示以中心点为圆心的半径光环。", "true / false", "false"));
                } else if ("send_packet".equals(normalized)) {
                        schemas.add(FieldSchema.paramsField("direction", "方向", FieldType.SELECT, false,
                                        "C2S 表示发包，S2C 表示按入站处理。", "C2S / S2C", "C2S",
                                        "C2S", "S2C"));
                        schemas.add(FieldSchema.paramsField("channel", "频道", FieldType.TEXT, false,
                                        "FML 包频道，可为空。", "可为空", ""));
                        schemas.add(FieldSchema.paramsField("packetId", "包 ID", FieldType.NUMBER, false,
                                        "标准包 ID；若 channel 有值可不填。", ">= 0", "0"));
                        schemas.add(FieldSchema.paramsField("hex", "Hex 数据", FieldType.TEXTAREA, true,
                                        "16 进制包体数据。", "不能为空", ""));
                }

                return schemas;
        }

        private static List<FieldSchema> commonConnectionSchemas(String defaultLimit) {
                return Arrays.asList(
                                FieldSchema.field("maxIncomingConnections", "输入连接上限", FieldType.NUMBER, false,
                                                "当前节点输入端口允许接入的最大连线数。1=单连接且新连线会替换旧连线；0=不限。", ">= 0",
                                                defaultLimit == null ? "1" : defaultLimit));
        }

        private static List<FieldSchema> commonErrorSchemas() {
                return Arrays.asList(
                                FieldSchema.field("retry", "失败重试次数", FieldType.NUMBER, false,
                                                "节点失败后的自动重试次数。", ">= 0", "0"),
                                FieldSchema.field("retryDelayTicks", "重试延迟 Tick", FieldType.NUMBER, false,
                                                "两次重试之间的等待 Tick。", ">= 0", "0"),
                                FieldSchema.field("onError", "失败策略", FieldType.SELECT, false,
                                                "stop=停止，continue=继续，goto=跳到指定节点。", "stop / continue / goto", "stop",
                                                "stop", "continue", "goto"),
                                FieldSchema.field("onErrorGoto", "失败跳转节点", FieldType.TEXT, false,
                                                "当 onError=goto 时生效。", "节点 ID", ""));
        }

        public static JsonObject createDefaultData(String nodeType) {
                String normalizedType = NodeNode.normalizeType(nodeType);
                JsonObject data = new JsonObject();

                if (NodeNode.TYPE_END.equals(normalizedType)) {
                        data.addProperty("maxIncomingConnections", 0);
                } else if (NodeNode.TYPE_ACTION.equals(normalizedType)) {
                        data.addProperty("actionType", "command");
                        JsonObject params = new JsonObject();
                        params.addProperty("command", "/help");
                        data.add("params", params);
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_IF.equals(normalizedType)) {
                        data.addProperty("mode", "simple");
                        data.addProperty("leftVar", "x");
                        data.addProperty("operator", "==");
                        data.addProperty("right", 1);
                        data.addProperty("rightType", "number");
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_SET_VAR.equals(normalizedType)) {
                        data.addProperty("name", "x");
                        data.addProperty("scope", "global");
                        data.addProperty("value", 1);
                        data.addProperty("valueType", "number");
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_WAIT_UNTIL.equals(normalizedType)) {
                        data.addProperty("mode", "simple");
                        data.addProperty("leftVar", "ready");
                        data.addProperty("operator", "==");
                        data.addProperty("right", true);
                        data.addProperty("rightType", "boolean");
                        data.addProperty("timeoutTicks", 100);
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_TRIGGER.equals(normalizedType)) {
                        data.addProperty("triggerType", "onchat");
                        data.addProperty("contains", "hello");
                        data.addProperty("throttleMs", 1000);
                } else if (NodeNode.TYPE_RUN_SEQUENCE.equals(normalizedType)
                                || NodeNode.TYPE_SUBGRAPH.equals(normalizedType)) {
                        data.addProperty("graphName", "node_graph_1");
                        data.addProperty("argsText", "");
                        data.add("args", new JsonObject());
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_PARALLEL.equals(normalizedType)) {
                        data.addProperty("branchesText", "");
                        data.add("branches", new JsonObject());
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_JOIN.equals(normalizedType)) {
                        data.addProperty("joinKey", "join_1");
                        data.addProperty("required", 2);
                        data.addProperty("maxIncomingConnections", 0);
                } else if (NodeNode.TYPE_DELAY_TASK.equals(normalizedType)) {
                        data.addProperty("delayTicks", 20);
                        data.addProperty("timeoutTicks", 200);
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_RESOURCE_LOCK.equals(normalizedType)) {
                        data.addProperty("resourceKey", "resource_1");
                        data.addProperty("maxIncomingConnections", 1);
                } else if (NodeNode.TYPE_RESULT_CACHE.equals(normalizedType)) {
                        data.addProperty("cacheKey", "cache_1");
                        data.addProperty("value", true);
                        data.addProperty("maxIncomingConnections", 1);
                }

                return data;
        }

        private static String readString(JsonObject data, String... keys) {
                if (data == null || keys == null) {
                        return "";
                }
                for (String key : keys) {
                        if (key != null && data.has(key) && !data.get(key).isJsonNull()) {
                                return data.get(key).isJsonPrimitive() ? data.get(key).getAsString()
                                                : data.get(key).toString();
                        }
                }
                return "";
        }

        private static String firstGraphName(List<String> graphNames, String fallback) {
                if (graphNames != null) {
                        for (String graphName : graphNames) {
                                if (graphName != null && !graphName.trim().isEmpty()) {
                                        return graphName;
                                }
                        }
                }
                return fallback;
        }

        private static String[] toArray(List<String> values) {
                if (values == null || values.isEmpty()) {
                        return new String[0];
                }
                List<String> clean = new ArrayList<>();
                for (String value : values) {
                        if (value != null && !value.trim().isEmpty()) {
                                clean.add(value);
                        }
                }
                return clean.toArray(new String[0]);
        }

        public static List<String> defaultGraphOptions(List<String> graphNames) {
                if (graphNames == null) {
                        return Collections.emptyList();
                }
                List<String> result = new ArrayList<>();
                for (String name : graphNames) {
                        if (name != null && !name.trim().isEmpty()) {
                                result.add(name);
                        }
                }
                return result;
        }
}
