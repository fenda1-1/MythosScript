package com.zszl.zszlScriptMod.gui.modern.nonmanagement;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class ChangelogDocumentTest {
    @Test public void rendersComponentOwnTextOnceAndPreservesBold() {
        net.minecraft.util.text.ITextComponent root = new net.minecraft.util.text.TextComponentString("");
        root.appendSibling(new net.minecraft.util.text.TextComponentString("First"));
        root.appendSibling(new net.minecraft.util.text.TextComponentString("Second")
                .setStyle(new net.minecraft.util.text.Style().setBold(true)));
        StringBuilder rendered = new StringBuilder();
        for (net.minecraft.util.text.ITextComponent part : root) rendered.append(ModernChangelogTab.formatOwnText(part));
        assertEquals("FirstSecond", net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(rendered.toString()));
        assertTrue(rendered.toString().contains("§l"));
        assertEquals("", ModernChangelogTab.formatOwnText(root));
    }

    @Test public void foldingHidesDescendantsButKeepsSiblingAndNestedState() throws Exception {
        ModernChangelogTab tab = new ModernChangelogTab();
        Class<?> headingType = Class.forName(ModernChangelogTab.class.getName() + "$Heading");
        java.lang.reflect.Constructor<?> constructor = headingType.getDeclaredConstructor(String.class, int.class, int.class);
        constructor.setAccessible(true);
        java.lang.reflect.Field headingsField = ModernChangelogTab.class.getDeclaredField("headings");
        headingsField.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> headings = (List<Object>) headingsField.get(tab);
        for (int level : new int[] {1, 2, 3, 2, 3}) headings.add(constructor.newInstance("Heading", 0, level));
        java.lang.reflect.Method visible = ModernChangelogTab.class.getDeclaredMethod("visibleHeadings");
        visible.setAccessible(true);
        java.lang.reflect.Field expanded = headingType.getDeclaredField("expanded");
        expanded.setAccessible(true);
        expanded.setBoolean(headings.get(1), false);
        assertEquals(java.util.Arrays.asList(0, 1, 3, 4), visible.invoke(tab));
        expanded.setBoolean(headings.get(0), false);
        assertEquals(java.util.Arrays.asList(0), visible.invoke(tab));
        expanded.setBoolean(headings.get(0), true);
        assertEquals(java.util.Arrays.asList(0, 1, 3, 4), visible.invoke(tab));
    }

    @Test public void changelogRouteCreatesNativeReaderWithoutStartingNetworkWork() {
        com.zszl.zszlScriptMod.gui.modern.core.ModernTabRegistry registry =
                com.zszl.zszlScriptMod.gui.modern.ModernTabCatalog.createRegistry();
        assertTrue(registry.create("changelog", null, null) instanceof ModernChangelogTab);
    }

    @Test public void preservesReleaseHeadingsAndLegacyMarkdown() {
        List<ChangelogDocument.Block> blocks = ChangelogDocument.parse(
                "# [v1.0.6]\r\n## **修复**\r\n- 点击穿透\r\n> 提示\r\n---\r\n1. 重启");
        assertEquals(6, blocks.size());
        assertEquals("[v1.0.6]", blocks.get(0).title());
        assertEquals(1, blocks.get(0).level);
        assertEquals("修复", blocks.get(1).title());
        assertEquals(2, blocks.get(1).level);
        assertEquals(ChangelogDocument.Kind.LIST, blocks.get(2).kind);
        assertEquals(ChangelogDocument.Kind.QUOTE, blocks.get(3).kind);
        assertEquals(ChangelogDocument.Kind.RULE, blocks.get(4).kind);
        assertEquals("1. 重启", blocks.get(5).title());
    }

    @Test public void codeFencesDoNotCreateFakeOutlineHeadings() {
        List<ChangelogDocument.Block> blocks = ChangelogDocument.parse("```text\n# literal\n  indent\n```\n### Real");
        assertEquals(3, blocks.size());
        assertEquals(ChangelogDocument.Kind.CODE, blocks.get(0).kind);
        assertEquals("  indent", blocks.get(1).title());
        assertEquals(3, blocks.get(2).level);
    }

    @Test public void linksRemainClickableOnlyForWebAddresses() {
        List<ChangelogDocument.Span> spans = ChangelogDocument.inline(
                "[release](https://example.com/release) [local](file:///secret) [bad](javascript:alert)");
        assertEquals("https://example.com/release", spans.get(0).url);
        assertEquals("", spans.get(2).url);
        assertEquals("local", spans.get(2).text);
        assertEquals("", spans.get(4).url);
    }

    @Test public void malformedMarkupAndEmptyInputDoNotLoseText() {
        assertEquals("**unfinished [v1.0.6]", ChangelogDocument.parse("**unfinished [v1.0.6]").get(0).title());
        assertEquals(ChangelogDocument.Kind.SPACE, ChangelogDocument.parse(null).get(0).kind);
        assertEquals("v1.0.6", ChangelogDocument.parse("## v1.0.6 ##").get(0).title());
    }

    @Test public void retainsInlineEmphasisAndCode() {
        List<ChangelogDocument.Span> spans = ChangelogDocument.inline("**bold** *italic* `code` __strong__");
        assertTrue(spans.get(0).bold);
        assertTrue(spans.get(2).italic);
        assertTrue(spans.get(4).code);
        assertTrue(spans.get(6).bold);
    }
}
