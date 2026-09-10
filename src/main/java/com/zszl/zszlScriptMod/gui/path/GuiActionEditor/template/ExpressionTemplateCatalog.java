package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.template;

import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ExpressionTemplateCard;

import java.util.ArrayList;
import java.util.List;

public final class ExpressionTemplateCatalog {
    private ExpressionTemplateCatalog() {
    }

    public static List<ExpressionTemplateCard> buildSetVarCards() {
        List<ExpressionTemplateCard> cards = new ArrayList<ExpressionTemplateCard>();
        cards.add(card("数字字面量", "123", "直接写整数或小数。", "123 / -4 / 3.14",
                "返回对应数字值", "number", "literal"));
        cards.add(card("字符串字面量", "\"boss\"", "用单引号或双引号包裹固定文本。", "\"text\" / 'text'",
                "\"boss\" -> boss", "string", "literal", "quoted"));
        cards.add(card("布尔字面量", "true", "直接写布尔值 true 或 false。", "true / false",
                "用于条件或开关值", "boolean", "literal"));
        cards.add(card("空值字面量", "null", "直接写 null，也支持 nil 别名。", "null / nil",
                "可配合 ?? 或 coalesce 使用", "null", "nil", "literal"));
        cards.add(card("括号分组", "(global.money + 1) * 2", "用括号强制指定计算顺序。", "(表达式)",
                "先加后乘", "group", "priority"));
        cards.add(card("当前变量 += 值", "+= 1", "用当前目标变量的旧值加上新值，适合累计计数。", "[+|-|*|/|%]= 表达式",
                "旧值 5 -> 结果 6", "shorthand", "plusEquals"));
        cards.add(card("完整变量 += 写法", "global.money += 5", "也可以对任意变量先取值再参与简写运算。", "变量 [+|-|*|/|%]= 表达式",
                "等价于 (global.money) + 5", "fullShorthand", "assignment"));
        cards.add(card("当前变量 -= 值", "-= 1", "用当前目标变量的旧值减去新值。", "-= 表达式",
                "旧值 5 -> 结果 4", "minusEquals"));
        cards.add(card("当前变量 *= 值", "*= 2", "用当前目标变量的旧值乘以新值。", "*= 表达式",
                "旧值 5 -> 结果 10", "timesEquals"));
        cards.add(card("当前变量 /= 值", "/= 2", "用当前目标变量的旧值除以新值。", "/= 表达式",
                "旧值 8 -> 结果 4", "divideEquals"));
        cards.add(card("当前变量 %= 值", "%= 10", "对当前目标变量做取模运算。", "%= 表达式",
                "旧值 23 -> 结果 3", "modEquals"));
        cards.add(card("直接变量引用", "global.money", "直接读取一个作用域变量的值。", "scope.name 或 sequenceName",
                "返回变量当前值", "variable", "scope"));
        cards.add(card("流程内置变量", "step_index", "可直接读取 sequence_name、step_index、action_index、gui_title 这些运行时内置值。",
                "sequence_name / step_index / action_index / gui_title", "当前步骤序号等内置信息",
                "builtin", "runtime"));
        cards.add(card("玩家内置变量", "player_health",
                "可直接读取 player_x/y/z、block_x/y/z、yaw、pitch、name、health、food 等玩家运行时值。",
                "player_health / player_x / player_block_x ...", "返回当前玩家状态",
                "player", "builtin", "runtime"));
        cards.add(card("$变量引用", "$target.name", "用 $ 前缀读取运行时变量，适合模板化引用。", "$变量名",
                "返回目标变量值", "dollar", "runtime"));
        cards.add(card("点路径访问", "$target.info.name", "从对象或 Map 里继续读取子字段。", "变量.字段.字段",
                "返回 name 子字段", "path", "member"));
        cards.add(card("索引访问", "$targets[-1]", "从列表里按索引读取，支持负索引。", "变量[索引]",
                "[-1] 返回最后一个元素", "index", "list"));
        cards.add(card("列表字面量", "[\"boss\", 1, true]", "直接在表达式里声明一个列表。", "[值1, 值2, 值3]",
                "返回一个列表对象", "array", "literal"));
        cards.add(card("random / rand", "random(1, 100)",
                "生成随机数。random()/rand() 返回 0~1 之间小数；传两个整数返回闭区间随机整数；传小数返回区间随机浮点。",
                "random() / random(min,max) / rand(min,max)", "random(1,100) -> 1..100",
                "random", "rand", "number"));
        cards.add(card("randomInt", "randomInt(1, 100)", "显式生成闭区间随机整数。", "randomInt(min,max)",
                "返回 1~100 的整数", "random", "int"));
        cards.add(card("randomFloat", "randomFloat(0, 1)", "显式生成 [min,max) 区间随机小数。",
                "randomFloat(min,max)", "返回 0~1 之间小数", "random", "float"));
        cards.add(card("算术计算", "global.money + 1", "支持 +、-、*、/、% 组合数值表达式。", "表达式 运算符 表达式",
                "5 + 1 -> 6", "math", "arithmetic"));
        cards.add(card("逻辑与 &&", "global.money > 10 && local.loop_index < 3", "两个条件都成立时返回 true。",
                "条件 && 条件", "true / false", "and"));
        cards.add(card("逻辑或 ||", "exists(global.money) || exists(temp.money)", "任一条件成立就返回 true。",
                "条件 || 条件", "true / false", "or"));
        cards.add(card("逻辑非 !", "!contains(sequence.targets, \"boss\")", "把布尔结果反转。", "!条件",
                "true -> false", "not"));
        cards.add(card("三元 ?:",
                "temp.current_match == \"boss\" ? 1 : 0",
                "条件成立返回前值，否则返回后值。", "条件 ? 成立值 : 失败值", "匹配 boss 时返回 1",
                "ternary", "ifElse"));
        cards.add(card("空值回退 ??", "global.money ?? 0", "左侧为空时回退到右侧默认值。", "值1 ?? 值2",
                "null -> 0", "coalesceOp", "nullish"));
        cards.add(card("contains",
                "contains(sequence.targets, \"boss\")",
                "检查字符串或列表里是否包含目标值。", "contains(源值, 目标值)",
                "包含时返回 true", "list", "string"));
        cards.add(card("containsIgnoreCase",
                "containsIgnoreCase(temp.current_match, \"boss\")",
                "忽略大小写检查包含关系。", "containsIgnoreCase(源值, 目标值)", "Boss 也会匹配 boss",
                "containsignorecase"));
        cards.add(card("startsWith / startsWithIgnoreCase",
                "startsWithIgnoreCase(temp.current_match, \"boss\")",
                "判断文本前缀。", "startsWith(text, prefix)", "Boss_01 -> true",
                "startswith", "startswithignorecase"));
        cards.add(card("endsWith / endsWithIgnoreCase",
                "endsWith(temp.current_match, \"_done\")",
                "判断文本后缀。", "endsWith(text, suffix)", "task_done -> true",
                "endswith", "endswithignorecase"));
        cards.add(card("equalsIgnoreCase",
                "equalsIgnoreCase(temp.current_match, \"boss\")",
                "忽略大小写做全文相等比较。", "equalsIgnoreCase(text1, text2)", "Boss == boss -> true",
                "equalsignorecase"));
        cards.add(card("regex / matches",
                "regex(temp.current_match, \"boss_[0-9]+\")",
                "用正则匹配文本。", "regex(text, pattern)", "boss_12 -> true",
                "matches", "pattern"));
        cards.add(card("regexGroup / regexCapture",
                "regexGroup(global.slot_nbt, \"(\\\\d+);\\\\s*(\\\\d+);\\\\s*(\\\\d+)\", 1)",
                "提取正则第一次匹配的指定捕获组，组号从 1 开始；0 表示完整匹配。",
                "regexGroup(text, pattern, group)", "坐标正则的第 1 组 -> 763",
                "regexgroup", "capture", "extract"));
        cards.add(card("正则捕获组拼接",
                "join([regexGroup(global.slot_nbt, \"(\\\\d+);\\\\s*(\\\\d+);\\\\s*(\\\\d+)\", 1), 100, regexGroup(global.slot_nbt, \"(\\\\d+);\\\\s*(\\\\d+);\\\\s*(\\\\d+)\", 3)], \" \")",
                "提取 X 和 Z，并把中间 Y 坐标固定为 100。",
                "join([regexGroup(文本, 正则, 1), 固定Y, regexGroup(文本, 正则, 3)], \" \")",
                "763 100 952", "regexgroup", "join", "coordinate"));
        cards.add(card("trim", "trim(temp.current_match)", "去掉文本首尾空白。", "trim(text)",
                "\" boss \" -> \"boss\"", "string"));
        cards.add(card("lower", "lower(temp.current_match)", "转成小写。", "lower(text)",
                "\"Boss\" -> \"boss\"", "lowercase"));
        cards.add(card("upper", "upper(temp.current_match)", "转成大写。", "upper(text)",
                "\"Boss\" -> \"BOSS\"", "uppercase"));
        cards.add(card("replace",
                "replace(temp.current_match, \"boss\", \"elite\")",
                "替换文本里的内容。", "replace(text, old, new)", "\"boss_1\" -> \"elite_1\"",
                "string"));
        cards.add(card("substring / substr",
                "substring(temp.current_match, 0, 4)",
                "截取文本片段。", "substring(text, start, end?)", "\"boss_1\" -> \"boss\"",
                "substr"));
        cards.add(card("indexOf / lastIndexOf",
                "indexOf(temp.current_match, \"_\")",
                "返回文本里第一次或最后一次出现的位置。", "indexOf(text, keyword)", "\"boss_1\" -> 4",
                "lastindexof"));
        cards.add(card("split",
                "split(temp.current_match, \"_\")",
                "按分隔符拆成列表。", "split(text, sep)", "\"boss_1\" -> [boss, 1]",
                "list"));
        cards.add(card("join",
                "join(sequence.targets, \",\")",
                "把列表按分隔符拼回文本。", "join(list, sep)", "[a,b] -> \"a,b\"",
                "string"));
        cards.add(card("coalesce",
                "coalesce(temp.money, global.money, 0)",
                "返回第一个非空值。", "coalesce(a, b, c...)", "null, 12, 0 -> 12",
                "fallback"));
        cards.add(card("if 函数",
                "if(global.money > 10, \"rich\", \"poor\")",
                "函数形式的条件选择。", "if(条件, 成立值, 失败值)", "money=12 -> rich",
                "ternary"));
        cards.add(card("empty / exists",
                "empty(temp.current_match)",
                "判断值是否为空，exists 则相反。", "empty(value) / exists(value)", "\"\" -> true",
                "null", "exists"));
        cards.add(card("len / size / count",
                "len(sequence.targets)",
                "求字符串、列表、集合长度。", "len(value)", "[a,b,c] -> 3",
                "size", "count"));
        cards.add(card("first / last",
                "last(sequence.targets)",
                "取列表第一个或最后一个元素。", "first(list) / last(list)", "[a,b,c] -> c",
                "list"));
        cards.add(card("any / all",
                "all(global.money > 0, local.loop_index < 5)",
                "any 任一成立，all 全部成立。", "any(a,b,...) / all(a,b,...)", "all(true,true) -> true",
                "bool"));
        cards.add(card("eq / ne",
                "eq(global.money, 100)",
                "函数形式的相等 / 不等判断。", "eq(a, b) / ne(a, b)", "100 == 100 -> true",
                "equal", "notEqual"));
        cards.add(card("gt / lt / gte / lte",
                "gte(global.money, 100)",
                "函数形式的大小比较。", "gt(a,b) / lt(a,b) / gte(a,b) / lte(a,b)", "money=120 -> true",
                "compare"));
        cards.add(card("min / max",
                "max(global.money, local.reward, 0)",
                "返回多个值中的最小或最大值。", "min(a,b,...) / max(a,b,...)", "max(2,9,4) -> 9",
                "math"));
        cards.add(card("sum",
                "sum(sequence.damage_list)",
                "对数字列表求和。", "sum(list 或 多个参数)", "[2,3,4] -> 9",
                "math"));
        cards.add(card("avg / average",
                "avg(sequence.damage_list)",
                "对数字列表求平均值。", "avg(list) / average(list)", "[2,4,6] -> 4",
                "mean"));
        cards.add(card("abs",
                "abs(local.delta)",
                "取绝对值。", "abs(number)", "-3 -> 3",
                "math"));
        cards.add(card("pow",
                "pow(local.level, 2)",
                "做乘方运算。", "pow(base, exponent)", "pow(3,2) -> 9",
                "math"));
        cards.add(card("clamp",
                "clamp(global.money, 0, 9999)",
                "把数值限制在区间内。", "clamp(value, min, max)", "12000 -> 9999",
                "range"));
        cards.add(card("between / betweenInc",
                "betweenInclusive(global.money, 10, 20)",
                "判断数值是否落在区间中。", "between(value,min,max) / betweenInclusive(value,min,max)", "15 -> true",
                "betweeninc", "betweeninclusive"));
        cards.add(card("toNumber / number / int",
                "toNumber(temp.current_match)",
                "把文本转成数字；int/toInt 会截断小数部分。", "toNumber(value) / int(value)", "\"12\" -> 12，int(16.8) -> 16",
                "number", "int", "toint"));
        cards.add(card("捕获ID HEX",
                "cap(dd)",
                "读取捕获ID当前值，返回形如 00 00 00 2A 的HEX文本。", "cap(name) / cap(\"name\")",
                "dd -> 00 00 00 2A", "captured", "cap", "hex"));
        cards.add(card("捕获ID转数字",
                "int(hextodec(cap(dd)) / 2.5)",
                "把捕获ID的HEX值转成十进制后参与运算。capdec(name) 是 hextodec(cap(name)) 的短写。",
                "hextodec(cap(name)) / capdec(name)", "00 00 00 2A / 2.5 -> 16",
                "captured", "hextodec", "capdec", "math"));
        cards.add(card("toBoolean / bool / boolean",
                "toBoolean(temp.current_match)",
                "把文本或数字转成布尔值。", "toBoolean(value)", "\"true\" -> true",
                "bool", "boolean"));
        cards.add(card("toString / string",
                "toString(global.money)",
                "把值转成字符串。", "toString(value)", "12 -> \"12\"",
                "string"));
        cards.add(card("round / floor / ceil",
                "round(global.money / 3)",
                "常用取整函数。", "round(x) / floor(x) / ceil(x)", "2.6 -> 3",
                "math"));
        return cards;
    }

    public static List<ExpressionTemplateCard> buildBooleanCards() {
        List<ExpressionTemplateCard> cards = new ArrayList<ExpressionTemplateCard>();
        cards.add(card("布尔字面量", "true", "直接返回 true 或 false。", "true / false",
                "true / false", "boolean", "literal"));
        cards.add(card("括号分组",
                "(global.money > 10 || local.loop_index == 0) && exists(temp.current_match)",
                "把多个布尔判断分组后再继续组合。", "(布尔表达式)", "true / false", "group", "priority"));
        cards.add(card("逻辑与 &&",
                "global.money > 10 && local.loop_index < 3",
                "左右两侧都成立时返回 true。", "条件 && 条件", "true / false", "and"));
        cards.add(card("逻辑或 ||",
                "exists(global.money) || exists(temp.money)",
                "任意一侧成立就返回 true。", "条件 || 条件", "true / false", "or"));
        cards.add(card("逻辑非 !",
                "!contains(sequence.targets, \"boss\")",
                "对布尔结果取反。", "!条件", "true / false", "not"));
        cards.add(card("随机概率判定", "random() < 0.25", "每次计算都会重新取随机值，适合做概率触发。", "random() < 概率",
                "约 25% 返回 true", "random", "probability"));
        cards.add(card("相等比较 ==",
                "temp.current_match == \"boss\"",
                "比较两个值是否相等。", "值1 == 值2", "true / false", "equals"));
        cards.add(card("不等比较 !=",
                "global.money != 0",
                "比较两个值是否不相等。", "值1 != 值2", "true / false", "notEquals"));
        cards.add(card("大于比较 >",
                "global.money > 100",
                "左值大于右值时返回 true。", "值1 > 值2", "true / false", "greaterThan"));
        cards.add(card("大于等于比较 >=",
                "local.loop_index >= 1",
                "左值大于等于右值时返回 true。", "值1 >= 值2", "true / false", "greaterThanOrEqual"));
        cards.add(card("小于比较 <",
                "local.loop_index < 5",
                "左值小于右值时返回 true。", "值1 < 值2", "true / false", "lessThan"));
        cards.add(card("小于等于比较 <=",
                "player_health <= 10",
                "左值小于等于右值时返回 true。", "值1 <= 值2", "true / false", "lessThanOrEqual"));
        cards.add(card("区间组合判断",
                "$local.loop_index >= 1 && $local.loop_index <= 3",
                "用比较运算符组合出闭区间/开区间判断。", "a >= min && a <= max", "true / false",
                "range", "between"));
        cards.add(card("contains",
                "contains(sequence.targets, \"boss\")",
                "检查字符串或列表中是否包含目标值。", "contains(源值, 目标值)", "包含时返回 true",
                "contains", "list", "string"));
        cards.add(card("containsIgnoreCase",
                "containsIgnoreCase(temp.current_match, \"boss\")",
                "忽略大小写检查包含关系。", "containsIgnoreCase(源值, 目标值)", "Boss -> true",
                "containsignorecase"));
        cards.add(card("startsWith / startsWithIgnoreCase",
                "startsWithIgnoreCase(temp.current_match, \"boss\")",
                "判断文本前缀是否匹配。", "startsWith(text, prefix)", "Boss_01 -> true",
                "startswith", "startswithignorecase"));
        cards.add(card("endsWith / endsWithIgnoreCase",
                "endsWith(temp.current_match, \"_done\")",
                "判断文本后缀是否匹配。", "endsWith(text, suffix)", "task_done -> true",
                "endswith", "endswithignorecase"));
        cards.add(card("equalsIgnoreCase",
                "equalsIgnoreCase(temp.current_match, \"boss\")",
                "忽略大小写做全文相等比较。", "equalsIgnoreCase(text1, text2)", "Boss == boss -> true",
                "equalsignorecase"));
        cards.add(card("regex / matches",
                "regex(temp.current_match, \"boss_[0-9]+\")",
                "用正则匹配文本。", "regex(text, pattern)", "boss_12 -> true",
                "matches", "pattern"));
        cards.add(card("empty / exists",
                "exists(global.money)",
                "判断值是否存在，empty 则判断是否为空。", "empty(value) / exists(value)",
                "exists(12) -> true", "empty", "exists"));
        cards.add(card("any / all",
                "all(global.money > 0, local.loop_index < 5)",
                "any 任一成立，all 全部成立。", "any(a,b,...) / all(a,b,...)", "all(true,true) -> true",
                "any", "all"));
        cards.add(card("eq / ne",
                "eq(global.money, 100)",
                "函数形式的相等 / 不等判断。", "eq(a,b) / ne(a,b)", "eq(100,100) -> true",
                "eq", "ne"));
        cards.add(card("gt / lt / gte / lte",
                "gte(global.money, 100)",
                "函数形式的大小比较。", "gt(a,b) / lt(a,b) / gte(a,b) / lte(a,b)",
                "gte(120,100) -> true", "gt", "lt", "gte", "lte"));
        cards.add(card("between / betweenInclusive",
                "betweenInclusive(global.money, 10, 20)",
                "判断数值是否落在区间中。", "between(value,min,max) / betweenInclusive(value,min,max)",
                "15 -> true", "between", "betweeninc", "betweeninclusive"));
        cards.add(card("toBoolean / bool / boolean",
                "toBoolean(temp.current_match)",
                "把文本、数字或对象按运行时规则转成布尔值。", "toBoolean(value)", "\"true\" -> true",
                "toboolean", "bool", "boolean"));
        return cards;
    }

    public static List<ExpressionTemplateCard> buildItemFilterCards() {
        List<ExpressionTemplateCard> cards = new ArrayList<ExpressionTemplateCard>();
        cards.add(card("名称完全匹配", "name == \"雷霆护手\"", "按物品显示名精确匹配，会自动忽略颜色符号、空格和大小写。",
                "name == \"物品名\"", "匹配显示名为雷霆护手的物品", "name", "equals"));
        cards.add(card("名称包含", "nameContains(\"雷霆\")", "按物品显示名做包含匹配，适合模糊筛选同系列装备。",
                "nameContains(\"关键字\")", "命中名称里包含雷霆的装备", "name", "contains"));
        cards.add(card("注册名匹配", "id == \"minecraft:diamond_sword\"", "按物品注册名/id 精确比较。",
                "id == \"mod:item\"", "匹配指定 registryName", "id", "registry"));
        cards.add(card("注册名包含", "registryContains(\"helmet\")", "按注册名模糊匹配。",
                "registryContains(\"关键字\")", "匹配注册名中带 helmet 的物品", "registry", "contains"));
        cards.add(card("NBT 原文包含", "NBT(品质：稀有)", "直接在归一化后的 NBT / tooltip / lore 文本里查找关键片段，支持不加引号。",
                "NBT(品质：稀有)", "匹配任意出现 品质:稀有 的装备", "nbt", "raw"));
        cards.add(card("NBT 键值匹配", "NBT(\"品质\", \"稀有\")", "按键和值分别匹配，适合明确筛选某个自定义标签。",
                "NBT(\"键\", \"值\")", "品质=稀有 时返回 true", "nbt", "keyValue"));
        cards.add(card("NBT 值读取", "NBT_VALUE(\"品质\") == \"稀有\"", "先读取某个标签的值，再用 == / != 做比较。",
                "NBT_VALUE(\"键\") == \"值\"", "读取品质后判断是否等于稀有", "nbtValue", "compare"));
        cards.add(card("NBT 数值读取", "NBT_NUM(\"伤害\") >= 10", "从标签值里提取第一个数字，可直接做大小比较。",
                "NBT_NUM(\"键\") >= 数值", "伤害属性不小于 10", "nbtNum", "number"));
        cards.add(card("数值算术组合", "NBT_NUM(\"攻击\") + NBT_NUM(\"暴击\") > 20",
                "支持 +、-、*、/、% 和括号分组，可把多个 NBT 数值、count、damage 等字段组合起来判断。",
                "数值表达式 > 数值", "攻击与暴击总和大于 20", "math", "arithmetic"));
        cards.add(card("max / min / abs", "max(NBT_NUM(\"攻击\"), NBT_NUM(\"法伤\")) >= 30",
                "用 max/min/abs/sum/avg/round/floor/ceil/clamp 等函数做更复杂的数值过滤。",
                "max(a,b,...) / min(a,b,...) / abs(x)", "攻击或法伤任一最高值达到 30", "max", "min", "abs"));
        cards.add(card("NBT 区间匹配", "NBT_RANGE(\"伤害\", \"(1,2]\")", "按区间函数判断某个标签提取出的数值是否落在区间内。",
                "NBT_RANGE(\"键\", \"(1,2]\")", "伤害在 (1,2] 范围内", "nbtRange", "range"));
        cards.add(card("通用区间函数", "range(NBT_NUM(\"暴击率\"), \"[10,25]\")", "把任意数值表达式送入区间函数，支持闭区间/开区间。",
                "range(数值表达式, \"[a,b)\")", "暴击率位于 10~25", "range", "interval"));
        cards.add(card("NBT 数值求和", "NBT_SUM(\"伤害\") >= 50", "对同一个键出现的多个数值做聚合求和。",
                "NBT_SUM(\"键\") >= 数值", "多个伤害词条累计至少 50", "nbtSum", "tagSum"));
        cards.add(card("NBT 最大/最小值", "NBT_MAX(\"暴击率\") >= 15 && NBT_MIN(\"暴击率\") >= 8",
                "同时读取重复键里的最大值和最小值，适合筛选多条随机属性。",
                "NBT_MAX(\"键\") / NBT_MIN(\"键\")", "暴击率每条都不低于 8，且至少有一条达到 15", "nbtMax", "nbtMin"));
        cards.add(card("NBT 平均值", "NBT_AVG(\"强化等级\") >= 10", "对重复数值标签求平均值。",
                "NBT_AVG(\"键\") >= 数值", "多个强化等级平均值至少 10", "nbtAvg", "nbtAverage"));
        cards.add(card("NBT 数值区间计数", "NBT_RANGE_COUNT(\"伤害\", \"(1,2]\") >= 2",
                "统计同一个数值键里有多少个值落在指定区间，适合随机词条多段筛选。",
                "NBT_RANGE_COUNT(\"键\", \"[a,b)\") >= 次数", "伤害词条里至少 2 个值落在目标区间", "nbtRangeCount"));
        cards.add(card("NBT 数值全体区间", "NBT_ALL_RANGE(\"强化等级\", \"[10,+inf)\")",
                "要求同一个键的所有数值都满足区间条件；NBT_ANY_RANGE 则只要任意一个满足即可。",
                "NBT_ALL_RANGE(\"键\", \"区间\") / NBT_ANY_RANGE(\"键\", \"区间\")", "所有强化等级都至少 10", "nbtAllRange", "nbtAnyRange"));
        cards.add(card("Tooltip 文本", "tooltip(\"暴击\")", "在物品 tooltip 文本中查找关键字。", "tooltip(\"关键字\")",
                "匹配提示文本中出现暴击", "tooltip", "text"));
        cards.add(card("同词条出现次数", "occurs(lore, \"暴击\") >= 2",
                "统计某段文本里同一关键词出现了多少次。适合做“同词条出现2次以上”筛选。",
                "occurs(字段, \"关键字\") >= 次数", "lore 中暴击至少出现 2 次", "occurs", "count"));
        cards.add(card("按行统计词条", "lineCount(lore, \"暴击\") >= 2",
                "按 lore / tooltip 的行来统计，不会把同一行里的重复字符误算成多条词条。",
                "lineCount(字段, \"关键字\") >= 次数", "包含暴击的 lore 行数至少 2", "lineCount"));
        cards.add(card("按行正则统计", "lineRegexCount(lore, \"暴击[+＋]\\\\d+\") >= 2",
                "一行算一次，适合统计“暴击+数字”这类完整词条行。",
                "lineRegexCount(字段, \"pattern\") >= 次数", "满足正则的 lore 行至少 2 条", "lineRegexCount", "regexLineCount"));
        cards.add(card("去重计数", "distinctCount(lore, \"暴击\") >= 2",
                "统计去重后的匹配行数；同样内容重复出现多次时只算一条。",
                "distinctCount(字段, \"关键字\") >= 次数", "不同的暴击词条至少 2 条", "distinctCount", "uniqueCount"));
        cards.add(card("去重正则计数", "distinctRegexCount(lore, \"暴击[+＋]\\\\d+\") >= 2",
                "按正则匹配后再去重，适合去掉重复展示的同一词条。",
                "distinctRegexCount(字段, \"pattern\") >= 次数", "不同的暴击+数字词条至少 2 条", "distinctRegexCount"));
        cards.add(card("Lore 次数快捷函数", "loreCount(\"暴击\") >= 2",
                "快捷统计 lore 中某个关键词出现的次数。", "loreCount(\"关键字\") >= 次数",
                "lore 内暴击词条至少 2 条", "lorecount", "loreoccurs"));
        cards.add(card("Lore 行数快捷函数", "loreLineCount(\"暴击\") >= 2",
                "快捷统计 lore 中包含关键字的行数；loreRegexLineCount 则按正则匹配行。",
                "loreLineCount(\"关键字\") >= 次数", "暴击 lore 行至少 2 条", "loreLineCount", "loreRegexLineCount"));
        cards.add(card("Tooltip 次数快捷函数", "tooltipCount(\"暴击\") >= 2",
                "快捷统计 tooltip 中某个关键词出现的次数。", "tooltipCount(\"关键字\") >= 次数",
                "tooltip 中暴击至少出现 2 次", "tooltipcount", "tooltipoccurs"));
        cards.add(card("Tooltip 行数快捷函数", "tooltipLineCount(\"暴击\") >= 2",
                "快捷统计 tooltip 中匹配关键字或正则的行数。",
                "tooltipLineCount(\"关键字\") >= 次数", "tooltip 中暴击行至少 2 条", "tooltipLineCount", "tooltipRegexLineCount"));
        cards.add(card("任意文本次数", "textCount(\"稀有\") >= 3",
                "在名称、tooltip、lore、registryName、NBT 原文联合文本里统计关键词出现次数。",
                "textCount(\"关键字\") >= 次数", "整件物品文本里稀有至少出现 3 次", "textcount", "textoccurs"));
        cards.add(card("Lore 文本", "lore(\"对怪增伤\")", "只在 Lore 文本中查找关键字。", "lore(\"关键字\")",
                "匹配 lore 中包含对怪增伤", "lore", "text"));
        cards.add(card("任意文本搜索", "text(\"稀有\")", "在名称、tooltip、lore、registryName、NBT 原文的联合文本里查找。",
                "text(\"关键字\")", "只要任意位置出现稀有就命中", "text", "search"));
        cards.add(card("拥有 NBT", "hasNbt()", "判断物品是否带有任意 NBT。", "hasNbt()", "有自定义 NBT 时返回 true",
                "hasNbt"));
        cards.add(card("数量判断", "count >= 2", "按堆叠数量做比较。", "count >= 数值", "堆叠数量至少 2", "count"));
        cards.add(card("槽位判断", "slot == 0", "按当前正在检查的背包槽位编号比较。", "slot == 槽位号", "仅匹配第 0 格",
                "slot"));
        cards.add(card("耐久/元数据", "damage <= 10", "按物品 damage / meta 做比较。", "damage <= 数值",
                "只要 damage 不超过 10", "damage", "meta"));
        cards.add(card("与条件 &&", "NBT(品质：稀有) && tooltip(\"暴击\")", "同时满足多个过滤条件。",
                "条件 && 条件", "需要同时满足稀有和暴击词条", "and"));
        cards.add(card("或条件 ||", "NBT(\"品质\", \"绝佳\") || name == \"雷霆护手\"", "任一条件成立即可命中。",
                "条件 || 条件", "满足绝佳品质或指定名称即可", "or"));
        cards.add(card("取反 !", "!NBT(\"绑定\", \"已绑定\")", "排除某个标签或文本条件。", "!条件",
                "未绑定物品才返回 true", "not"));
        cards.add(card("all 函数", "all(NBT(\"品质\", \"稀有\"), lore(\"暴击\"), count >= 1)", "函数形式组合多个必须满足的条件。",
                "all(条件1, 条件2, ...)", "全部满足才返回 true", "all"));
        cards.add(card("any 函数", "any(nameContains(\"雷霆\"), NBT(\"品质\", \"绝佳\"))", "函数形式组合多个候选条件。",
                "any(条件1, 条件2, ...)", "任一命中即返回 true", "any"));
        cards.add(card("正则匹配", "regex(nbtRaw, \"品质.?稀有\")", "用正则对名称、NBT 原文或任意文本做高级匹配。",
                "regex(字段或文本, \"pattern\")", "匹配 品质 稀有 的多种写法", "regex", "matches"));
        cards.add(card("正则命中次数", "regexCount(lore, \"暴击[+＋]\\\\d+\") >= 2",
                "统计正则模式在某段文本中命中了多少次。适合筛选重复词条、多段数值词条。",
                "regexCount(字段, \"pattern\") >= 次数", "lore 中 暴击+数字 至少命中 2 次",
                "regexcount", "countmatches"));
        cards.add(card("NBT 键值出现次数", "NBT_OCCURS(\"词条\", \"暴击\") >= 2",
                "统计相同键值对在 tooltip / lore / NBT 键值结构中出现了多少次。",
                "NBT_OCCURS(\"键\", \"值\") >= 次数", "词条=暴击 至少出现 2 次", "nbtoccurs", "tagoccurs"));
        cards.add(card("NBT 键出现次数", "NBT_KEY_COUNT(\"词条\") >= 4",
                "统计某个键在已解析的键值结构里出现了多少次。",
                "NBT_KEY_COUNT(\"键\") >= 次数", "词条键至少出现 4 次", "nbtkeycount", "tagkeycount"));
        cards.add(card("NBT 去重值数量", "NBT_DISTINCT_VALUE_COUNT(\"词条\") >= 3",
                "统计同一个键下有多少个不同的值，适合筛“至少 3 条不同词条”。",
                "NBT_DISTINCT_VALUE_COUNT(\"键\") >= 次数", "词条键下至少 3 个不同值", "nbtDistinctValueCount", "nbtDistinctCount"));
        cards.add(card("归一化文本正则", "regex(norm(lore), \"品质:稀有\")",
                "先用 norm/normalize 去掉颜色符号、空格并统一中英文标点，再做正则或文本比较。",
                "regex(norm(字段), \"pattern\")", "忽略颜色和空格后再匹配品质:稀有", "norm", "normalize"));
        cards.add(card("复杂组合示例",
                "(NBT(品质：稀有) && lineCount(lore, \"暴击\") >= 2 && NBT_NUM(\"攻击\") + NBT_NUM(\"暴击\") > 20 && NBT_RANGE_COUNT(\"伤害\", \"(1,2]\") >= 1) || name == \"雷霆护手\"",
                "示例：先筛品质、词条行数、数值总和和区间命中次数，再额外放行一个特定装备。",
                "(条件A && 条件B) || 条件C", "支持和 / 或 / 区间 / 名称联合判断", "combo", "sample"));
        return cards;
    }

    private static ExpressionTemplateCard card(String name, String example, String description, String format,
            String outputExample, String... keywords) {
        return new ExpressionTemplateCard(name, example, description, format, outputExample, keywords);
    }
}
