package com.zszl.zszlScriptMod.shadowbaritone.api.utils;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.HoverEvent;

/**
 * ShadowBaritone 本地化工具：
 * 1) 兼容旧版“英文原文 -> 中文”查找
 * 2) 支持新版结构化 key（例如 shadowbaritone.command.proc.status）
 * 3) 新旧方案可并存，便于逐步迁移
 */
public final class ShadowBaritoneI18n {

    private ShadowBaritoneI18n() {
    }

    /**
     * 兼容旧用法：
     * - 若传入的是旧版英文原文，则按原文查表
     * - 若传入的是结构化 key，则也能正常翻译
     * - 查不到时回退原文
     */
    public static String tr(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        try {
            String translated = I18n.format(raw);
            return translated == null ? raw : translated;
        } catch (Throwable ignored) {
            return raw;
        }
    }

    /**
     * 新版结构化 key 翻译：
     * - 查不到时回退 key 本身
     */
    public static String trKey(String key, Object... args) {
        return trKeyOrDefault(key, key, args);
    }

    /**
     * 新版结构化 key 翻译：
     * - 查不到时回退到 fallback
     * - fallback 支持 String.format 风格参数
     */
    public static String trKeyOrDefault(String key, String fallback, Object... args) {
        if (key == null || key.isEmpty()) {
            return fallback;
        }
        try {
            String translated = I18n.format(key, args);
            if (translated != null && !translated.equals(key)) {
                return translated;
            }
        } catch (Throwable ignored) {
        }
        return safeFormat(fallback == null ? key : fallback, args);
    }

    private static String safeFormat(String pattern, Object... args) {
        if (pattern == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return String.format(pattern, args);
        } catch (Throwable ignored) {
            return pattern;
        }
    }

    public static ITextComponent trComponent(ITextComponent component) {
        if (component == null) {
            return null;
        }

        ITextComponent result;
        if (component instanceof TextComponentString) {
            TextComponentString text = (TextComponentString) component;
            TextComponentString translated = new TextComponentString(tr(text.getText()));

            Style style = component.getStyle().createShallowCopy();
            HoverEvent hover = style.getHoverEvent();
            if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT && hover.getValue() != null) {
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, trComponent(hover.getValue())));
            }
            translated.setStyle(style);
            result = translated;
        } else {
            result = component.createCopy();
        }

        result.getSiblings().clear();
        for (ITextComponent sibling : component.getSiblings()) {
            result.appendSibling(trComponent(sibling));
        }
        return result;
    }
}