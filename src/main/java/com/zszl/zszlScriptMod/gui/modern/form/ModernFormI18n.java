package com.zszl.zszlScriptMod.gui.modern.form;

import net.minecraft.client.resources.I18n;

/** Resolves form labels at draw time so language switches update text without rebuilding tabs. */
public final class ModernFormI18n {

    private ModernFormI18n() {
    }

    public static String tr(String keyOrLiteral) {
        if (keyOrLiteral == null || keyOrLiteral.isEmpty()) {
            return "";
        }
        if (!isKey(keyOrLiteral)) {
            return keyOrLiteral;
        }
        String translated = I18n.format(keyOrLiteral);
        if (translated != null && translated.startsWith("Format error: ")) {
            return translated.substring("Format error: ".length());
        }
        return translated == null || translated.isEmpty() ? keyOrLiteral : translated;
    }

    public static String tr(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String translated = I18n.format(key, args);
        return translated == null || translated.isEmpty() ? key : translated;
    }

    public static boolean isKey(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        boolean hasDot = false;
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                hasDot = true;
                continue;
            }
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                hasLetter = true;
                continue;
            }
            if (c > 127 || !(c >= '0' && c <= '9' || c == '_' || c == '-')) {
                return false;
            }
        }
        return hasDot && hasLetter;
    }
}
