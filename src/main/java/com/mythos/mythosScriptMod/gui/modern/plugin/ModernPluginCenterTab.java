package com.mythos.mythosScriptMod.gui.modern.plugin;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mythos.mythosScriptMod.gui.modern.ModernHoverScrollbar;
import com.mythos.mythosScriptMod.gui.modern.ModernMainLayout.Rect;
import com.mythos.mythosScriptMod.gui.modern.ModernSettingsTab;
import com.mythos.mythosScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Plugin centre: what is installed, and what actually happened to it.
 *
 * <p>两层数据：{@link PluginCatalog} 给出磁盘上的清单，{@link PluginLoader} 给出运行态
 * （加载成功 / 失败原因 / 注册了几条命令）。卡片上两者必须同时可见——只显示清单会让
 * “装了一个跑不起来的插件”看起来像装成功了。</p>
 *
 * <p>安装新插件仍然需要重启客户端：类加载没有热替换，重启是唯一的装载时机。</p>
 */
public final class ModernPluginCenterTab implements ModernSettingsTab {

    private static final int CARD_HEIGHT = 84;
    private static final int CARD_EXPANDED_HEIGHT = 190;
    private static final int CARD_GAP = 8;
    private static final int CONTENT_INSET = 8;

    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final Set<String> expanded = new HashSet<>();
    private final List<Card> cards = new ArrayList<>();

    private Rect bounds;
    private Rect viewport;
    private Rect refreshButton;
    private Rect openDirectoryButton;
    private Rect copyButton;
    private FontRenderer font;
    private String message = "";
    private long messageUntil;
    private int fontHeight = 9;
    private int scroll;
    private int maxScroll;
    private int totalHeight;
    private boolean initialized;
    /** 空状态里那一段清单示例自己的滚动条：窗口矮的时候示例放不下，只滚它的框体。 */
    private final ModernHoverScrollbar exampleScrollbar = new ModernHoverScrollbar();
    private Rect exampleBox;
    private int exampleScroll;
    private int exampleMaxScroll;

    /** One rendered card, rebuilt every frame so hit testing matches the pixels. */
    private static final class Card {
        final Rect bounds;
        final PluginCatalog.Plugin plugin;
        /** Bottom-row action buttons; null when the card is collapsed. */
        Rect toggle;
        Rect reload;

        Card(Rect bounds, PluginCatalog.Plugin plugin) {
            this.bounds = bounds;
            this.plugin = plugin;
        }
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (fontRenderer != null) {
            font = fontRenderer;
            fontHeight = fontRenderer.FONT_HEIGHT;
        }
        if (!initialized) {
            initialized = true;
            PluginCatalog.rescan();
        }
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void draw(FontRenderer fontRenderer, Rect contentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        if (fontRenderer == null || contentBounds == null) {
            return;
        }
        bounds = contentBounds;
        cards.clear();
        panel(bounds);
        drawHeader(mouseX, mouseY);
        viewport = new Rect(bounds.x + CONTENT_INSET, bounds.y + 79,
                Math.max(1, bounds.width - CONTENT_INSET * 2), Math.max(1, bounds.bottom() - bounds.y - 108));
        List<PluginCatalog.Plugin> plugins = PluginCatalog.plugins();
        // Clip to the scroll viewport rather than the whole panel: clipping to the panel
        // let the last card paint over the footer text and the bottom border.
        ModernUiRenderer.beginClip(viewport);
        try {
            if (plugins.isEmpty()) {
                drawEmptyState(mouseX, mouseY);
                scrollbar.idle();
            } else {
                exampleBox = null;
                exampleMaxScroll = 0;
                exampleScrollbar.idle();
                drawCards(plugins, mouseX, mouseY);
            }
        } finally {
            ModernUiRenderer.endClip();
        }
        drawFooter();
    }

    private void drawHeader(int mouseX, int mouseY) {
        text("§l" + tr("title", "插件中心"), bounds.x + 16, bounds.y + 12,
                ModernUiRenderer.TEXT, bounds.width - 32);
        int count = PluginCatalog.plugins().size();
        int running = 0;
        for (PluginLoader.Runtime runtime : PluginLoader.runtimes()) {
            if (runtime.state() == PluginLoader.State.LOADED) {
                running++;
            }
        }
        String subtitle = count == 0
                ? tr("subtitle_empty", "尚未安装插件")
                : tr("subtitle", "已安装 %s 个插件", count)
                        + "    ·    " + tr("running_count", "运行中 %s", running);
        File directory = PluginCatalog.modsDirectory();
        if (directory != null) {
            subtitle = subtitle + "    ·    " + directory.getAbsolutePath();
        }
        text(subtitle, bounds.x + 16, bounds.y + 29, ModernUiRenderer.SUBTLE_TEXT, bounds.width - 32);
        int buttonWidth = Math.min(112, Math.max(1, (bounds.width - 40) / 3));
        refreshButton = new Rect(bounds.x + 12, bounds.y + 47, buttonWidth, 23);
        openDirectoryButton = new Rect(refreshButton.right() + 6, refreshButton.y, buttonWidth, 23);
        copyButton = new Rect(openDirectoryButton.right() + 6, copyButtonY(), buttonWidth, 23);
        button(refreshButton, tr("refresh", "重新扫描"), true, mouseX, mouseY);
        button(openDirectoryButton, tr("open_folder", "打开 mods 目录"), directory != null, mouseX, mouseY);
        button(copyButton, tr("copy", "复制清单"), count > 0, mouseX, mouseY);
    }

    private int copyButtonY() {
        return refreshButton == null ? bounds.y + 47 : refreshButton.y;
    }

    private void drawCards(List<PluginCatalog.Plugin> plugins, int mouseX, int mouseY) {
        int cardWidth = Math.max(1, viewport.width - 24);
        int y = 6;
        for (PluginCatalog.Plugin plugin : plugins) {
            boolean open = expanded.contains(cardKey(plugin));
            int height = open ? CARD_EXPANDED_HEIGHT : CARD_HEIGHT;
            Rect card = new Rect(viewport.x + 8, viewport.y + y - scroll, cardWidth, height);
            y += height + CARD_GAP;
            if (card.bottom() < viewport.y || card.y > viewport.bottom()) {
                continue;
            }
            Card entry = new Card(card, plugin);
            cards.add(entry);
            drawCard(entry, open, mouseX, mouseY);
        }
        totalHeight = y + 6;
        maxScroll = Math.max(0, totalHeight - viewport.height);
        scroll = clamp(scroll, maxScroll);
        scrollbar.draw(viewport, scroll, maxScroll, viewport.height, totalHeight, mouseX, mouseY,
                value -> scroll = value);
    }

    private void drawCard(Card entry, boolean open, int mouseX, int mouseY) {
        Rect card = entry.bounds;
        PluginCatalog.Plugin plugin = entry.plugin;
        entry.toggle = null;
        entry.reload = null;
        boolean hovered = card.contains(mouseX, mouseY);
        ModernUiRenderer.drawPanel(card.x, card.y, card.width, card.height, 6,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.HOVER_BORDER : ModernUiRenderer.BORDER_SUBTLE);

        PluginLoader.Runtime runtime = PluginLoader.runtimeFor(plugin.fileName);
        int iconColor;
        String stateLabel;
        int stateColor;
        if (runtime == null) {
            iconColor = ModernUiRenderer.MUTED_TEXT;
            stateLabel = tr("state_pending", "未装载");
            stateColor = ModernUiRenderer.MUTED_TEXT;
        } else if (runtime.state() == PluginLoader.State.LOADED) {
            iconColor = ModernUiRenderer.SUCCESS;
            stateLabel = tr("state_loaded", "运行中");
            stateColor = ModernUiRenderer.SUCCESS;
        } else if (runtime.state() == PluginLoader.State.DISABLED) {
            iconColor = ModernUiRenderer.WARNING;
            stateLabel = tr("state_disabled", "已禁用");
            stateColor = ModernUiRenderer.WARNING;
        } else if (runtime.state() == PluginLoader.State.FAILED) {
            iconColor = ModernUiRenderer.DANGER;
            stateLabel = tr("state_failed", "加载失败");
            stateColor = ModernUiRenderer.DANGER;
        } else {
            iconColor = ModernUiRenderer.MUTED_TEXT;
            stateLabel = tr("state_skipped", "未启用");
            stateColor = ModernUiRenderer.MUTED_TEXT;
        }

        ModernUiRenderer.drawRoundedRect(card.x + 12, card.y + 14, 30, 30, 6, ModernUiRenderer.ICON_SURFACE);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.BLOCKS, card.x + 20, card.y + 22, 14, iconColor);

        int textX = card.x + 52;
        int textWidth = Math.max(1, card.right() - textX - 34);
        text("§l" + plugin.name, textX, card.y + 13, ModernUiRenderer.TEXT, textWidth);
        String meta = plugin.author + "    ·    " + plugin.fileName;
        text(meta, textX, card.y + 28, ModernUiRenderer.SUBTLE_TEXT, textWidth);

        String description = plugin.description;
        if (description.isEmpty()) {
            description = tr("no_description", "该插件没有提供描述。");
        }
        int descriptionY = card.y + 44;
        for (String line : font.listFormattedStringToWidth(description, Math.max(1, textWidth))) {
            if (descriptionY + fontHeight > card.y + CARD_HEIGHT - 6) {
                break;
            }
            text(line, textX, descriptionY, ModernUiRenderer.MUTED_TEXT, textWidth);
            descriptionY += fontHeight + 2;
        }
        drawFeatureChips(plugin, textX, card.y + CARD_HEIGHT - 20, card.right() - 26);

        String version = "v" + plugin.version;
        int versionWidth = font.getStringWidth(version) + 12;
        int versionX = card.right() - 26 - versionWidth;
        ModernUiRenderer.drawRoundedRect(versionX, card.y + 11, versionWidth, 14, 4, ModernUiRenderer.SHELL_RAISED);
        text(version, versionX + 6, card.y + 14, ModernUiRenderer.SUBTLE_TEXT, versionWidth - 12);

        // 状态徽标排在版本号左边；标题行挤不下时宁可不画，也不能压到插件名上。
        int stateWidth = font.getStringWidth(stateLabel) + 12;
        int stateX = versionX - 6 - stateWidth;
        if (stateX > textX + 48) {
            ModernUiRenderer.drawRoundedRect(stateX, card.y + 11, stateWidth, 14, 4,
                    ModernUiRenderer.SHELL_RAISED);
            text(stateLabel, stateX + 6, card.y + 14, stateColor, stateWidth - 12);
        }
        ModernUiRenderer.drawChevron(card.right() - 20, card.y + 13, !open, ModernUiRenderer.SUBTLE_TEXT);

        if (open) {
            ModernUiRenderer.drawDivider(card.x + 12, card.y + CARD_HEIGHT - 4, card.width - 24,
                    ModernUiRenderer.BORDER_SUBTLE);
            int detailY = card.y + CARD_HEIGHT + 4;
            String entrypoint = plugin.entrypoint.isEmpty()
                    ? tr("no_entrypoint", "未声明入口点")
                    : plugin.entrypoint;
            text(tr("entrypoint", "入口点") + ": " + entrypoint, textX, detailY,
                    ModernUiRenderer.SUBTLE_TEXT, textWidth);
            detailY += fontHeight + 3;
            text(tr("identifier", "标识") + ": " + plugin.id
                            + "    ·    " + tr("api_version", "API 版本") + " " + plugin.apiVersion,
                    textX, detailY, ModernUiRenderer.SUBTLE_TEXT, textWidth);
            detailY += fontHeight + 3;

            if (runtime != null && runtime.state() != PluginLoader.State.SKIPPED) {
                String registered = tr("registered", "已注册") + ": "
                        + runtime.commandCount() + tr("commands_unit", " 条命令")
                        + " · " + runtime.toolCount() + tr("tools_unit", " 个 MCP 工具")
                        + " · " + runtime.tabCount() + tr("tabs_unit", " 个标签页")
                        + " · " + runtime.activeTickCount() + tr("ticks_unit", " 个 tick");
                text(registered, textX, detailY,
                        runtime.state() == PluginLoader.State.LOADED
                                ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, textWidth);
                detailY += fontHeight + 3;
            }

            if (!plugin.requires.isEmpty()) {
                text(tr("requires", "依赖") + ": " + joinList(plugin.requires), textX, detailY,
                        ModernUiRenderer.SUBTLE_TEXT, textWidth);
                detailY += fontHeight + 3;
            }

            if (runtime == null) {
                text(tr("state_pending_hint", "插件表已刷新，但本次启动没有装载它；重启客户端后生效。"),
                        textX, detailY, ModernUiRenderer.MUTED_TEXT, textWidth);
            } else if (!runtime.message().isEmpty()) {
                text(runtime.message(), textX, detailY,
                        runtime.state() == PluginLoader.State.FAILED
                                ? ModernUiRenderer.DANGER : ModernUiRenderer.MUTED_TEXT, textWidth);
            }

            // 操作按钮贴右下角。卡片底部留 28px，保证按钮不和最后一行文字挤在一起。
            int buttonY = card.bottom() - 28;
            int buttonWidth = 64;
            if (runtime != null && runtime.state() != PluginLoader.State.SKIPPED) {
                boolean running = runtime.state() == PluginLoader.State.LOADED;
                entry.reload = new Rect(card.right() - 14 - buttonWidth, buttonY, buttonWidth, 20);
                entry.toggle = new Rect(entry.reload.x - 6 - buttonWidth, buttonY, buttonWidth, 20);
                button(entry.reload, tr("reload", "重载"), true, mouseX, mouseY);
                button(entry.toggle, running ? tr("disable", "禁用") : tr("enable", "启用"),
                        true, mouseX, mouseY);
            }
        }
    }

    private static String joinList(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private void drawFeatureChips(PluginCatalog.Plugin plugin, int x, int y, int rightLimit) {
        if (plugin.features.isEmpty()) {
            text(tr("no_features", "未声明功能"), x, y + 2, ModernUiRenderer.MUTED_TEXT, rightLimit - x);
            return;
        }
        int cursor = x;
        int shown = 0;
        for (String feature : plugin.features) {
            int chipWidth = font.getStringWidth(feature) + 10;
            if (cursor + chipWidth > rightLimit && shown > 0) {
                break;
            }
            ModernUiRenderer.drawRoundedRect(cursor, y, chipWidth, 13, 4, ModernUiRenderer.SHELL_RAISED);
            text(feature, cursor + 5, y + 3, ModernUiRenderer.SUBTLE_TEXT, chipWidth - 10);
            cursor += chipWidth + 4;
            shown++;
        }
        int remaining = plugin.features.size() - shown;
        if (remaining > 0) {
            String more = "+" + remaining;
            int moreWidth = font.getStringWidth(more) + 10;
            if (cursor + moreWidth <= rightLimit) {
                ModernUiRenderer.drawRoundedRect(cursor, y, moreWidth, 13, 4, ModernUiRenderer.ACCENT_DIM);
                text(more, cursor + 5, y + 3, ModernUiRenderer.TEXT, moreWidth - 10);
            }
        }
    }

    private void drawEmptyState(int mouseX, int mouseY) {
        // Match the card geometry exactly (x = viewport.x + 8, width = viewport.width - 24).
        // Centring a narrower column here made the empty state look indented relative to
        // the toolbar above and to the cards that replace it once a plugin exists.
        int columnX = viewport.x + 8;
        int columnWidth = Math.max(1, viewport.width - 24);
        int lineHeight = fontHeight + 2;
        String[] exampleLines = PluginCatalog.manifestExample().split("\n");
        int exampleHeight = exampleLines.length * lineHeight;
        // 示例清单是固定行数，但矮窗口会把整块空状态顶出可视区。示例框高度取「内容全高」与
        // 「面板余量」中较小者，放不下的部分留在框内滚动，别让文字画到框外面去。
        int fixedHeight = 84 + 12 + fontHeight + 8;
        int boxHeight = Math.min(exampleHeight + 8,
                Math.max(lineHeight * 2 + 8, viewport.height - fixedHeight - 8));
        int innerHeight = Math.max(lineHeight, boxHeight - 8);
        exampleMaxScroll = Math.max(0, exampleHeight - innerHeight);
        exampleScroll = clamp(exampleScroll, exampleMaxScroll);
        int blockHeight = fixedHeight + boxHeight + 8;
        int top = viewport.y + Math.max(10, (viewport.height - blockHeight) / 2);

        ModernUiRenderer.drawPanel(columnX, top, columnWidth, 62, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.BLOCKS, columnX + 14, top + 24, 18,
                ModernUiRenderer.MUTED_TEXT);
        int textX = columnX + 42;
        int textWidth = Math.max(1, columnWidth - 58);
        text(tr("empty_title", "未检测到插件"), textX, top + 22, ModernUiRenderer.TEXT, textWidth);
        text(tr("empty_hint", "把插件 jar 放进 mods 目录后点「重新扫描」。"), textX, top + 36,
                ModernUiRenderer.SUBTLE_TEXT, textWidth);
        text(tr("empty_manifest", "插件 jar 内需包含 META-INF/mythos-plugin.json。"), textX,
                top + 48, ModernUiRenderer.MUTED_TEXT, textWidth);

        int codeY = top + 84;
        ModernUiRenderer.drawRoundedRect(columnX, codeY, columnWidth, 4, 2, ModernUiRenderer.ACCENT_DIM);
        codeY += 12;
        text(tr("example_title", "清单示例"), columnX, codeY, ModernUiRenderer.SUBTLE_TEXT, columnWidth);
        codeY += fontHeight + 8;

        int boxTop = codeY - 4;
        ModernUiRenderer.drawPanel(columnX, boxTop, columnWidth, boxHeight, 4,
                ModernUiRenderer.INPUT_SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        exampleBox = new Rect(columnX, boxTop, columnWidth, boxHeight);
        boolean scrollable = exampleMaxScroll > 0;
        int lineWidth = Math.max(1, columnWidth - 16
                - (scrollable ? ModernHoverScrollbar.GUTTER : 0));
        ModernUiRenderer.beginClip(new Rect(columnX + 8, codeY, Math.max(1, columnWidth - 16), innerHeight));
        try {
            int lineY = codeY - exampleScroll;
            for (String line : exampleLines) {
                if (lineY + lineHeight > codeY && lineY < codeY + innerHeight) {
                    text(line, columnX + 8, lineY, ModernUiRenderer.MUTED_TEXT, lineWidth);
                }
                lineY += lineHeight;
            }
        } finally {
            ModernUiRenderer.endClip();
        }
        if (scrollable) {
            exampleScrollbar.draw(new Rect(columnX, codeY, Math.max(1, columnWidth - 4), innerHeight),
                    exampleScroll, exampleMaxScroll, innerHeight, exampleHeight, mouseX, mouseY,
                    value -> exampleScroll = value);
        } else {
            exampleScrollbar.idle();
        }
        totalHeight = viewport.height;
        maxScroll = 0;
        scroll = 0;
    }

    private void drawFooter() {
        // 加载器支持热重载，这句话必须说清"点按钮就生效"，否则用户会以为要重启客户端。
        String status = messageOr(tr("hint", "插件实时生效：增删 jar 后点「重新扫描」即可，无需重启客户端。"));
        text(status, bounds.x + 16, bounds.bottom() - 18, ModernUiRenderer.SUBTLE_TEXT, bounds.width - 85);
        String ratio = maxScroll <= 0 ? "100%" : (int) (scroll * 100L / maxScroll) + "%";
        text(ratio, bounds.right() - 53, bounds.bottom() - 18, ModernUiRenderer.ACCENT, 40);
    }

    private String messageOr(String fallback) {
        String error = PluginCatalog.lastError();
        if (!error.isEmpty()) {
            return error;
        }
        return System.currentTimeMillis() < messageUntil ? message : fallback;
    }

    private void togglePlugin(PluginCatalog.Plugin plugin) {
        PluginLoader.Runtime runtime = PluginLoader.runtimeFor(plugin.fileName);
        if (runtime == null) {
            return;
        }
        if (runtime.state() == PluginLoader.State.LOADED) {
            PluginLoader.disable(runtime.id());
            showMessage(tr("notice_disabled", "已禁用 %s", plugin.name), 2600);
        } else if (PluginLoader.enable(runtime.id())) {
            showMessage(tr("notice_enabled", "已启用 %s", plugin.name), 2600);
        } else {
            showMessage(tr("notice_enable_failed", "启用 %s 失败：%s", plugin.name, runtime.message()),
                    3600);
        }
    }

    private void reloadPlugin(PluginCatalog.Plugin plugin) {
        PluginLoader.Runtime runtime = PluginLoader.runtimeFor(plugin.fileName);
        if (runtime == null) {
            return;
        }
        if (PluginLoader.reloadPlugin(runtime.id())) {
            showMessage(tr("notice_reloaded", "已重载 %s", plugin.name), 2600);
        } else {
            showMessage(tr("notice_reload_failed", "重载 %s 失败：%s", plugin.name, runtime.message()),
                    3600);
        }
    }

    private void button(Rect rect, String label, boolean enabled, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                enabled && rect.contains(mouseX, mouseY) ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER_SUBTLE);
        int labelX = rect.x + Math.max(5, (rect.width - font.getStringWidth(label)) / 2);
        text(label, labelX, rect.y + 7, enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.DISABLED_TEXT,
                rect.width - 10);
    }

    private void panel(Rect rect) {
        ModernUiRenderer.drawPanel(rect.x, rect.y, rect.width, rect.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
    }

    private void text(String value, int x, int y, int color, int width) {
        ModernUiRenderer.drawText(font, value, x, y, color, Math.max(1, width));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!containsContent(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (refreshButton != null && refreshButton.contains(mouseX, mouseY)) {
            int known = PluginCatalog.plugins().size();
            // reload() 内部会 rescan，所以这里不再单独重扫一次。
            PluginLoader.reload();
            int after = PluginCatalog.plugins().size();
            int loaded = 0;
            for (PluginLoader.Runtime runtime : PluginLoader.runtimes()) {
                if (runtime.state() == PluginLoader.State.LOADED) {
                    loaded++;
                }
            }
            String text = known == after
                    ? tr("rescanned", "已重新扫描：%s 个插件", after)
                    : tr("rescanned_new", "发现新插件，清单共 %s 个", after);
            showMessage(text + "    ·    " + tr("running_count", "运行中 %s", loaded), 3200);
            return true;
        }
        if (openDirectoryButton != null && openDirectoryButton.contains(mouseX, mouseY)) {
            openModsDirectory();
            return true;
        }
        if (copyButton != null && copyButton.contains(mouseX, mouseY)) {
            GuiScreen.setClipboardString(PluginCatalog.toJson());
            showMessage(tr("copied", "插件清单已复制到剪贴板"), 2600);
            return true;
        }
        if (scrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (exampleScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        for (Card card : cards) {
            if (!card.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            // Buttons live inside the card, so they must win over the expand toggle.
            if (card.toggle != null && card.toggle.contains(mouseX, mouseY)) {
                togglePlugin(card.plugin);
                return true;
            }
            if (card.reload != null && card.reload.contains(mouseX, mouseY)) {
                reloadPlugin(card.plugin);
                return true;
            }
            String key = cardKey(card.plugin);
            if (!expanded.remove(key)) {
                expanded.add(key);
            }
            return true;
        }
        return true;
    }

    private void openModsDirectory() {
        File directory = PluginCatalog.modsDirectory();
        if (directory == null) {
            showMessage(tr("open_failed", "找不到 mods 目录"), 3000);
            return;
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            showMessage(tr("open_failed", "找不到 mods 目录"), 3000);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("desktop unsupported");
            }
            Desktop.getDesktop().open(directory);
        } catch (Throwable unavailable) {
            showMessage(tr("open_manual", "无法打开文件夹：%s", directory.getAbsolutePath()), 4500);
        }
    }

    private void showMessage(String text, long durationMillis) {
        message = text;
        messageUntil = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton != 0) {
            return false;
        }
        // 两个滚动条互斥：空状态只有示例框那条有轨道，卡片列表不在时它返回 false。
        boolean listDragged = scrollbar.applyDrag(mouseX, mouseY);
        boolean exampleDragged = exampleScrollbar.applyDrag(mouseX, mouseY);
        return listDragged || exampleDragged;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        boolean captured = scrollbar.isDragging() || exampleScrollbar.isDragging();
        if (state == 0) {
            scrollbar.endDrag();
            exampleScrollbar.endDrag();
        }
        return captured;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (viewport == null) {
            return false;
        }
        int page = Math.max(16, viewport.height - 24);
        // 空状态下外层列表没有可滚内容，Home/End/PgUp/PgDn 转给示例框。
        if (maxScroll <= 0 && exampleMaxScroll > 0) {
            int examplePage = Math.max(8, exampleBox == null ? 24 : exampleBox.height - 16);
            if (keyCode == Keyboard.KEY_HOME) {
                exampleScroll = 0;
            } else if (keyCode == Keyboard.KEY_END) {
                exampleScroll = exampleMaxScroll;
            } else if (keyCode == Keyboard.KEY_PRIOR) {
                exampleScroll = clamp(exampleScroll - examplePage, exampleMaxScroll);
            } else if (keyCode == Keyboard.KEY_NEXT) {
                exampleScroll = clamp(exampleScroll + examplePage, exampleMaxScroll);
            } else {
                return false;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            scroll = 0;
        } else if (keyCode == Keyboard.KEY_END) {
            scroll = maxScroll;
        } else if (keyCode == Keyboard.KEY_PRIOR) {
            scroll = clamp(scroll - page, maxScroll);
        } else if (keyCode == Keyboard.KEY_NEXT) {
            scroll = clamp(scroll + page, maxScroll);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0 || viewport == null || !viewport.contains(mouseX, mouseY)) {
            return false;
        }
        // 空状态的示例框自己可滚时优先吃滚轮，否则滚的是外层卡片列表。
        if (exampleMaxScroll > 0 && exampleBox != null && exampleBox.contains(mouseX, mouseY)) {
            exampleScroll = clamp(exampleScroll - Integer.signum(wheel) * 24, exampleMaxScroll);
            return true;
        }
        scroll = clamp(scroll - Integer.signum(wheel) * 48, maxScroll);
        return true;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        if (refreshButton != null && refreshButton.contains(mouseX, mouseY)) {
            return tr("tooltip_refresh", "重新读取 mods 目录下的插件清单");
        }
        if (openDirectoryButton != null && openDirectoryButton.contains(mouseX, mouseY)) {
            return tr("tooltip_open", "在文件管理器中打开 mods 目录");
        }
        if (copyButton != null && copyButton.contains(mouseX, mouseY)) {
            return tr("tooltip_copy", "复制已检测到的插件清单为 JSON");
        }
        for (Card card : cards) {
            if (card.bounds.contains(mouseX, mouseY)) {
                return tr("tooltip_card", "点击展开或收起插件详情");
            }
        }
        return "";
    }

    @Override
    public void discardDraft() {
        scrollbar.endDrag();
        exampleScrollbar.endDrag();
    }

    private static String cardKey(PluginCatalog.Plugin plugin) {
        return plugin.id + "|" + plugin.fileName;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private static String tr(String key, String fallback, Object... args) {
        String fullKey = "gui.modern.plugin." + key;
        String value = I18n.format(fullKey, args);
        if (value == null || value.equals(fullKey)) {
            return args.length == 0 ? fallback : String.format(Locale.ROOT, fallback, args);
        }
        return value;
    }
}
