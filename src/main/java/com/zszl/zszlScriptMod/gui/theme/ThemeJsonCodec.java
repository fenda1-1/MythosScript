package com.zszl.zszlScriptMod.gui.theme;

import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager.ThemeProfile;

/** Portable single-theme format shared by clipboard export and AI instructions. */
public final class ThemeJsonCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] COLORS = {
        "panelBorder", "panelBgTop", "panelBgBottom", "titleLeft", "titleRight",
        "titleText", "labelText", "subText", "stateSuccess", "stateWarning", "stateDanger",
        "stateDisabled", "stateSelected", "buttonBgNormal", "buttonBgHover", "buttonBgPressed",
        "buttonBorderNormal", "buttonBorderHover", "inputBg", "inputBorder", "inputBorderHover", "inputCursor", "shadowColor"
    };

    private ThemeJsonCodec() { }

    public static String exportTheme(ThemeProfile profile) {
        JsonObject json = GSON.toJsonTree(profile).getAsJsonObject();
        json.addProperty("inputCursor", profile.getInputCursorColor());
        json.remove("builtIn");
        json.remove("builtInId");
        for (java.lang.reflect.Field field : ThemeProfile.class.getFields()) {
            if (field.isAnnotationPresent(Deprecated.class)) json.remove(field.getName());
        }
        for (String key : COLORS) {
            json.addProperty(key, String.format(Locale.ROOT, "#%08X", json.get(key).getAsInt()));
        }
        return GSON.toJson(json);
    }

    public static ThemeProfile importTheme(String source) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("剪贴板为空，请先复制主题 JSON");
        if (source.length() > 65536) throw new IllegalArgumentException("主题 JSON 不能超过 64 KB 字符");
        try {
            JsonElement root = new JsonParser().parse(source);
            if (!root.isJsonObject()) throw new IllegalArgumentException("需要单个主题 JSON 对象");
            JsonObject input = root.getAsJsonObject();
            JsonObject schema = new JsonParser().parse(exportTheme(new ThemeProfile())).getAsJsonObject();
            schema.addProperty("name", "");
            for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
                if (!schema.has(entry.getKey())) throw new IllegalArgumentException("未知字段：" + entry.getKey());
                if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("字段不能为空或嵌套：" + entry.getKey());
            }
            if (!input.has("name") || !input.get("name").getAsJsonPrimitive().isString()
                    || input.get("name").getAsString().trim().isEmpty()
                    || input.get("name").getAsString().length() > 96) {
                throw new IllegalArgumentException("name 必须为 1–96 字符的主题名称");
            }
            for (String key : COLORS) {
                // Older exported themes predate the independent caret color.
                if (key.equals("inputCursor") && !input.has(key)) continue;
                if (!input.has(key) || !input.get(key).getAsJsonPrimitive().isString()
                        || !input.get(key).getAsString().matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                    throw new IllegalArgumentException(key + " 必须为 #RRGGBB 或 #AARRGGBB 颜色");
                }
                String hex = input.get(key).getAsString().substring(1);
                input.addProperty(key, (int) Long.parseLong(hex.length() == 6 ? "FF" + hex : hex, 16));
            }
            for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
                String key = entry.getKey();
                if (key.equals("panelImageEnabled")) {
                    if (!entry.getValue().getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " 必须为布尔值");
                } else if (key.equals("name") || key.equals("panelImagePath") || key.equals("panelImageQuality")) {
                    if (!entry.getValue().getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " 必须为字符串");
                } else {
                    if (!entry.getValue().getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " 必须为整数");
                    entry.getValue().getAsBigDecimal().intValueExact();
                }
            }
            ThemeProfile profile = GSON.fromJson(input, ThemeProfile.class);
            profile.inputCursor = profile.getInputCursorColor();
            range("cornerRadius", profile.cornerRadius, 0, 12);
            range("borderWidth", profile.borderWidth, 1, 4);
            range("shadowOffset", profile.shadowOffset, 0, 8);
            range("panelOpacityPercent", profile.panelOpacityPercent, 0, 100);
            range("buttonOpacityPercent", profile.buttonOpacityPercent, 0, 100);
            range("inputOpacityPercent", profile.inputOpacityPercent, 0, 100);
            range("textOpacityPercent", profile.textOpacityPercent, 0, 100);
            range("panelImageScale", profile.panelImageScale, 10, 300);
            range("panelCropX", profile.panelCropX, 0, Integer.MAX_VALUE);
            range("panelCropY", profile.panelCropY, 0, Integer.MAX_VALUE);
            if (!profile.panelImageQuality.matches("LOW|MEDIUM|HIGH|ORIGINAL")) throw new IllegalArgumentException("panelImageQuality 无效");
            profile.name = profile.name.trim();
            return profile;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("JSON 格式或字段类型不正确，请复制完整的纯 JSON", ex);
        }
    }

    private static void range(String key, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(key + " 范围应为 " + min + "–" + max);
    }

    public static String aiPrompt() {
        ThemeProfile query = new ThemeProfile();
        query.builtInId = "ghibli";
        query.builtIn = true;
        ThemeProfile example = ThemeConfigManager.getBuiltInDefault(query);
        example.name = "森林晨光";
        example.panelImagePath = "";
        example.panelImageEnabled = false;
        return "请根据我描述的风格，为 Minecraft 脚本界面设计主题。只返回一个可直接导入的纯 JSON 对象，"
                + "不要 Markdown 代码围栏、解释、注释或多余文本。保留示例全部字段，不添加其他字段。\n"
                + "name 为 1–96 字符名称；所有颜色使用 #AARRGGBB（AA 为透明度，通常 FF）。"
                + "panel 为面板，title 为标题，labelText 为正文，subText 为次要文字，state 为状态，"
                + "button 为按钮，input 为输入框，inputCursor 为输入框光标（插入符）颜色，shadowColor 为阴影。保证文字、输入框光标与背景清晰可读，悬停、按下和选中状态有区分。\n"
                + "inputCursor 必须单独设计：使用不透明颜色（FF），与 inputBg 保持至少 4.5:1 的对比度；浅色输入框用深色光标，深色输入框用亮色光标，并与主题配色协调。\n"
                + "cornerRadius 为 0–12，borderWidth 为 1–4，shadowOffset 为 0–8；四个 OpacityPercent 为 0–100 的整数。"
                + "panelImageScale 为 10–300 的整数，panelCropX/Y 为非负整数，panelImageQuality 使用 LOW、MEDIUM、HIGH 或 ORIGINAL。"
                + "除非我提供图片路径，否则 panelImagePath 使用空字符串，panelImageEnabled 使用 false，不虚构图片路径。\n"
                + "完整格式示例：\n" + exportTheme(example) + "\n\n我想要的主题：〔在这里描述颜色、风格、明暗和氛围〕";
    }
}
