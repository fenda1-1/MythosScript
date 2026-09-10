package com.zszl.zszlScriptMod.utils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public final class PinyinSearchHelper {

    private static final class SearchIndex {
        final String compactText;
        final String compactPinyin;
        final String initials;

        private SearchIndex(String compactText, String compactPinyin, String initials) {
            this.compactText = compactText;
            this.compactPinyin = compactPinyin;
            this.initials = initials;
        }
    }

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();
    private static final Map<String, SearchIndex> CACHE = new ConcurrentHashMap<>();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinSearchHelper() {
    }

    public static String normalizeQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(query.length());
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (shouldKeep(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    public static boolean matchesNormalized(String source, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return true;
        }
        SearchIndex index = CACHE.computeIfAbsent(source == null ? "" : source, PinyinSearchHelper::buildIndex);
        return index.compactText.contains(normalizedQuery)
                || index.compactPinyin.contains(normalizedQuery)
                || index.initials.contains(normalizedQuery);
    }

    private static SearchIndex buildIndex(String source) {
        String safeSource = source == null ? "" : source.trim();
        StringBuilder compactText = new StringBuilder(safeSource.length());
        StringBuilder compactPinyin = new StringBuilder(safeSource.length() * 2);
        StringBuilder initials = new StringBuilder(safeSource.length());

        for (int i = 0; i < safeSource.length(); i++) {
            char ch = safeSource.charAt(i);
            if (!shouldKeep(ch)) {
                continue;
            }

            compactText.append(Character.toLowerCase(ch));
            if (isChinese(ch)) {
                try {
                    String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
                    if (pinyins != null && pinyins.length > 0) {
                        String pinyin = pinyins[0].toLowerCase(Locale.ROOT);
                        compactPinyin.append(pinyin);
                        initials.append(pinyin.charAt(0));
                        continue;
                    }
                } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                }
            }

            char lowered = Character.toLowerCase(ch);
            compactPinyin.append(lowered);
            if (Character.isLetterOrDigit(lowered)) {
                initials.append(lowered);
            }
        }

        return new SearchIndex(compactText.toString(), compactPinyin.toString(), initials.toString());
    }

    private static boolean shouldKeep(char ch) {
        return Character.isLetterOrDigit(ch) || isChinese(ch);
    }

    private static boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }
}
