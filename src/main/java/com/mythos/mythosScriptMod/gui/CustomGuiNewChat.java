// 文件路径: src/main/java/com/keycommand2/mythosScriptMod/gui/CustomGuiNewChat.java
// (这是修复了鼠标点击位置问题的最终版本)
package com.mythos.mythosScriptMod.gui;

import com.google.common.collect.Lists;
import com.mythos.mythosScriptMod.config.ChatOptimizationConfig;
import com.mythos.mythosScriptMod.config.DebugLogManager;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.handlers.ChatEventHandler;
import com.mythos.mythosScriptMod.utils.AnimationTools;
import com.mythos.mythosScriptMod.utils.TextureManagerHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern; // !! 新增导入 !!
import java.util.regex.PatternSyntaxException; // !! 新增导入 !!
import java.util.concurrent.ConcurrentHashMap;

public class CustomGuiNewChat extends GuiNewChat {

    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft mc;

    // --- 核心状态字段 ---
    private final List<String> sentMessages = Lists.newArrayList();
    // CopyOnWriteArrayList: session bots call sendMessage from their own tick
    // threads — a plain ArrayList races with the render thread's drawChat pass
    // (and with another bot's deleteChatLine). Chat lists are small and writes
    // are rare, so the copy cost is noise.
    private final List<ChatLine> chatLines = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<ChatLine> drawnChatLines = new java.util.concurrent.CopyOnWriteArrayList<>();
    private int scrollPos;
    private boolean isScrolled;

    // !! CORE REFACTOR: 使用Map来跟踪多个不同的刷屏消息 !!
    private static class SpamInfo {
        int count;
        long lastTime;
        int logicalChatLineId; // 存储整个逻辑消息的唯一ID

        SpamInfo(int count, long lastTime, int logicalChatLineId) {
            this.count = count;
            this.lastTime = lastTime;
            this.logicalChatLineId = logicalChatLineId;
        }
    }

    private final Map<String, SpamInfo> spamTracker = new ConcurrentHashMap<>();

    // !! 新增：缓存编译后的正则表达式，提高性能 !!
    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    // --- [CHAT] 日志限流 ---
    // Some servers echo the same action-bar / quest line hundreds of times per
    // second. The [CHAT] mirror line is diagnostic only: the HUD line and the
    // chat ring are written regardless of whether we log, so throttling it
    // removes the sync-IO stall without changing anything the player sees.
    //
    // The flood lines are NOT byte-identical: the anti-spam path appends a
    // counter ("msg [x2]", "msg [x3]", ...), so keying on the raw text would
    // never hit. The key therefore drops a trailing " [xN]" counter, which
    // collapses the whole flood into one key while keeping distinct messages
    // distinct. Nothing is lost from the diagnostics: the next logged line
    // carries the exact number of lines that were folded into it.
    private static final long CHAT_LOG_MIN_INTERVAL_MS = 200L;
    private static final int CHAT_LOG_KEY_LIMIT = 64;
    private static final java.util.Map<String, Long> chatLogLastMs =
            new java.util.LinkedHashMap<String, Long>(CHAT_LOG_KEY_LIMIT, 0.75F, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest) {
                    return size() > CHAT_LOG_KEY_LIMIT;
                }
            };
    private static final java.util.Map<String, Integer> chatLogSuppressed =
            new java.util.HashMap<String, Integer>();

    /** Drops the trailing anti-spam counter so "msg [x2]" and "msg [x99]" share one key. */
    private static String chatLogKey(String text) {
        if (text == null)
            return "";
        return text.replaceAll(" \\[x\\d+\\]$", "");
    }

    private static synchronized void logChatLine(String text) {
        long now = System.currentTimeMillis();
        String key = chatLogKey(text);
        Long last = chatLogLastMs.get(key);
        if (last != null && now - last < CHAT_LOG_MIN_INTERVAL_MS) {
            Integer suppressed = chatLogSuppressed.get(key);
            chatLogSuppressed.put(key, suppressed == null ? Integer.valueOf(1) : Integer.valueOf(suppressed.intValue() + 1));
            return;
        }
        Integer suppressed = chatLogSuppressed.remove(key);
        chatLogLastMs.put(key, Long.valueOf(now));
        String suffix = suppressed != null && suppressed.intValue() > 0
                ? " (identical lines suppressed: " + suppressed + ")"
                : "";
        LOGGER.info("[CHAT] {}{}", text, suffix);
    }

    // --- 时间戳 ---
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    // --- 动画与编辑 ---
    public static float percentComplete = 0.0F;
    public static int newLines;
    public static long prevMillis = -1;
    public boolean configuring = false;

    public CustomGuiNewChat(Minecraft mcIn) {
        super(mcIn);
        this.mc = mcIn;
    }

    public static void updatePercentage(long diff) {
        if (percentComplete < 1)
            percentComplete += 0.004f * diff;
        percentComplete = AnimationTools.clamp(percentComplete, 0, 1);
    }

    @Override
    public void drawChat(int updateCounter) {
        // (此方法无需修改，保持原样)
        if (configuring)
            return;

        if (prevMillis == -1) {
            prevMillis = System.currentTimeMillis();
            return;
        }
        long current = System.currentTimeMillis();
        long diff = current - prevMillis;
        prevMillis = current;
        updatePercentage(diff);
        float t = percentComplete;
        float percent = 1 - (--t) * t * t * t;
        percent = AnimationTools.clamp(percent, 0, 1);

        if (this.mc.gameSettings.chatVisibility != EntityPlayer.EnumChatVisibility.HIDDEN) {
            int lineCount = this.getLineCount();
            // Bot tick threads can add/remove chat lines while the client thread
            // renders. Keep one immutable view for both halves of this pass so
            // an index collected before a deletion is never looked up again in
            // a shorter live list.
            List<ChatLine> drawnChatSnapshot = new ArrayList<>(this.drawnChatLines);
            int drawnLineCount = drawnChatSnapshot.size();
            float chatOpacity = this.mc.gameSettings.chatOpacity * 0.9F + 0.1F;

            if (drawnLineCount > 0) {
                int renderScrollPos = Math.max(0,
                        Math.min(this.scrollPos, Math.max(0, drawnLineCount - lineCount)));
                boolean chatIsOpen = this.getChatOpen();
                float chatScale = this.getChatScale();
                int chatWidth = MathHelper.ceil((float) this.getChatWidth() / chatScale);

                ChatOptimizationConfig settings = ChatOptimizationConfig.instance();

                // Single pass: compute per-line alpha and collect the lines that
                // will actually render, so we don't walk drawnChatLines twice
                // and don't redo alpha math per line.
                java.util.List<int[]> visible = null; // [index, finalAlpha, textY]
                int visibleLineCountForBg = 0;
                int bgTop = 0;
                int bgBottom = 0;
                int maxBgAlpha = 0;

                for (int i = 0; i + renderScrollPos < drawnLineCount && i < lineCount; ++i) {
                    ChatLine chatline = drawnChatSnapshot.get(i + renderScrollPos);
                    if (chatline == null) {
                        continue;
                    }
                    int age = updateCounter - chatline.getUpdatedCounter();
                    if (age >= 200 && !chatIsOpen) {
                        continue;
                    }
                    double alpha = chatIsOpen ? 1.0D : 1.0D - (double) age / 200.0D;
                    alpha = MathHelper.clamp(alpha * 10.0D, 0.0D, 1.0D) * alpha;
                    int finalAlpha = (int) (255.0D * alpha * chatOpacity);
                    if (finalAlpha <= 3) {
                        continue;
                    }

                    int textY = -i * 9;
                    if (visibleLineCountForBg == 0) {
                        bgBottom = textY;
                    }
                    bgTop = textY - 9;
                    maxBgAlpha = Math.max(maxBgAlpha, finalAlpha);
                    if (visible == null) {
                        visible = new java.util.ArrayList<>(lineCount);
                    }
                    visible.add(new int[] { i + renderScrollPos, finalAlpha, textY });
                    visibleLineCountForBg++;
                }

                if (visibleLineCountForBg > 0) {
                    GlStateManager.pushMatrix();
                    if (settings.smooth && !this.isScrolled) {
                        GlStateManager.translate(2.0F + settings.xOffset,
                                8.0F + settings.yOffset + (9 - 9 * percent) * chatScale, 0.0F);
                    } else {
                        GlStateManager.translate(2.0F + settings.xOffset, 8.0F + settings.yOffset, 0.0F);
                    }
                    GlStateManager.scale(chatScale, chatScale, 1.0F);
                    renderChatBody(visible, drawnChatSnapshot, drawnLineCount, lineCount,
                            renderScrollPos, chatWidth,
                            chatOpacity, percent, settings, chatIsOpen,
                            bgTop, bgBottom, maxBgAlpha);
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    /** Second half of drawChat — runs inside the already-pushed chat matrix.
     *  Pulled out so the push/pop pair stays next to each other and the early
     *  return path (no visible lines) can't leave the stack pushed. */
    private void renderChatBody(java.util.List<int[]> visible,
                                 List<ChatLine> drawnChatSnapshot,
                                 int drawnLineCount, int lineCount,
                                 int renderScrollPos,
                                 int chatWidth, float chatOpacity, float percent,
                                 ChatOptimizationConfig settings,
                                 boolean chatIsOpen,
                                 int bgTop, int bgBottom, int maxBgAlpha) {
                    ResourceLocation bgLocation = TextureManagerHelper.getResourceLocationForPath(
                            settings.backgroundImagePath, settings.imageQuality);
                    int targetW = chatWidth + 4;
                    int targetH = Math.max(9, bgBottom - bgTop);

                    if (bgLocation != null) {
                        mc.getTextureManager().bindTexture(bgLocation);
                        float transparencyRatio = settings.backgroundTransparencyPercent / 100.0f;
                        float opacityRatio = 1.0f - transparencyRatio;
                        float finalImageAlpha = (maxBgAlpha / 255.0F) * opacityRatio;
                        GlStateManager.color(1.0F, 1.0F, 1.0F, finalImageAlpha);
                        GlStateManager.enableBlend();

                        int[] texSize = TextureManagerHelper.getTextureSizeForPath(
                                settings.backgroundImagePath, settings.imageQuality);
                        int texW = (texSize != null) ? texSize[0] : targetW;
                        int texH = (texSize != null) ? texSize[1] : targetH;

                        int safeScale = Math.max(10, Math.min(300, settings.backgroundImageScale));
                        float scaleFactor = 100.0F / safeScale;
                        int sampleW = Math.max(1, Math.min(texW, (int) (targetW * scaleFactor)));
                        int sampleH = Math.max(1, Math.min(texH, (int) (targetH * scaleFactor)));

                        int u = Math.max(0, settings.backgroundCropX);
                        int v = Math.max(0, settings.backgroundCropY);
                        if (u + sampleW > texW) {
                            u = Math.max(0, texW - sampleW);
                        }
                        if (v + sampleH > texH) {
                            v = Math.max(0, texH - sampleH);
                        }

                        Gui.drawScaledCustomSizeModalRect(-2, bgTop, u, v,
                                sampleW, sampleH,
                                targetW, targetH,
                                texW, texH);
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    } else if (settings.backgroundTransparencyPercent < 100) {
                        float transparencyRatio = settings.backgroundTransparencyPercent / 100.0f;
                        float opacityRatio = 1.0f - transparencyRatio;
                        int backgroundAlpha = (int) ((maxBgAlpha / 2.0f) * opacityRatio);
                        int backgroundColor = backgroundAlpha << 24;
                        drawRect(-2, bgTop, chatWidth + 4, bgBottom, backgroundColor);
                    }

                if (visible != null) {
                    for (int k = 0; k < visible.size(); k++) {
                        int[] entry = visible.get(k);
                        int idx = entry[0];
                        int finalAlpha = entry[1];
                        int textY = entry[2];
                        ChatLine chatline = drawnChatSnapshot.get(idx);
                        if (chatline == null) continue;
                        String s = chatline.getChatComponent().getFormattedText();
                        GlStateManager.enableBlend();
                        if (settings.smooth && idx - renderScrollPos <= newLines) {
                            this.mc.fontRenderer.drawStringWithShadow(s, 0.0F, (float) (textY - 8),
                                    16777215 + ((int) (finalAlpha * percent) << 24));
                        } else {
                            this.mc.fontRenderer.drawStringWithShadow(s, 0.0F, (float) (textY - 8),
                                    16777215 + (finalAlpha << 24));
                        }
                        GlStateManager.disableAlpha();
                        GlStateManager.disableBlend();
                    }
                }

                if (chatIsOpen) {
                    int k2 = this.mc.fontRenderer.FONT_HEIGHT;
                    GlStateManager.translate((float) chatWidth + 4.0F, 0.0F, 0.0F);
                    int l2 = drawnLineCount * k2 + drawnLineCount;
                    int i3 = lineCount * k2 + lineCount;
                    int j3 = renderScrollPos * i3 / drawnLineCount;
                    int k1 = i3 * i3 / l2;
                    if (l2 != i3) {
                        int k3 = j3 > 0 ? 170 : 96;
                        int l3 = this.isScrolled ? 13382451 : 3355562;
                        drawRect(0, -j3, 2, -j3 - k1, l3 + (k3 << 24));
                        drawRect(2, -j3, 1, -j3 - k1, 13421772 + (k3 << 24));
                    }
                }
    }

    // !! CORE REFACTOR: 完全重写此方法以实现高级防刷屏 !!
    @Override
    public void printChatMessageWithOptionalDeletion(ITextComponent chatComponent, int chatLineId) {
        // Legacy handlers still send diagnostic lines through player chat.
        // Capture them before any chat filtering/anti-spam logic so they enter
        // the bounded terminal instead of being rendered in the chat HUD.
        if (chatComponent != null && DebugLogManager.isDebugChatMessage(chatComponent.getFormattedText())) {
            DebugLogManager.appendChatDebug(chatComponent.getFormattedText());
            return;
        }

        // 如果 chatLineId 不为 0，说明是系统消息（如/say），我们直接用旧逻辑处理，不进行过滤
        if (chatLineId != 0) {
            if (!ModConfig.isInternalChatSuppressed()) {
                ChatEventHandler.triggerDisplayedChatMessage(chatComponent, chatComponent, chatLineId);
            }
            this.setChatLine(chatComponent, chatLineId, this.mc.ingameGUI.getUpdateCounter(), false);
            return;
        }

        String rawText = chatComponent.getUnformattedText();
        long currentTime = System.currentTimeMillis();
        ChatOptimizationConfig settings = ChatOptimizationConfig.instance();

        // 清理过期的刷屏记录
        spamTracker.entrySet().removeIf(
                entry -> currentTime - entry.getValue().lastTime > settings.antiSpamThresholdSeconds * 1000L * 2);

        // !! 核心修改：黑白名单过滤逻辑 !!
        boolean isBlacklisted = false;
        if (settings.enableBlacklist && !settings.blacklist.isEmpty()) {
            isBlacklisted = settings.blacklist.stream()
                    .filter(s -> s != null && !s.trim().isEmpty())
                    .anyMatch(filter -> matches(rawText, filter, settings.regexFilter));
        }

        boolean isWhitelisted = false;
        if (settings.enableWhitelist && !settings.whitelist.isEmpty()) {
            isWhitelisted = settings.whitelist.stream()
                    .filter(s -> s != null && !s.trim().isEmpty())
                    .anyMatch(filter -> matches(rawText, filter, settings.regexFilter));
        }

        if (isBlacklisted)
            return;
        if (settings.enableWhitelist && !isWhitelisted)
            return;
        // !! 修改结束 !!

        ITextComponent componentToDisplay = chatComponent;
        SpamInfo spamInfo = spamTracker.get(rawText);
        int logicalIdToUse;

        if (settings.enableAntiSpam && spamInfo != null
                && (currentTime - spamInfo.lastTime < settings.antiSpamThresholdSeconds * 1000L)) {
            // 这是刷屏行为
            spamInfo.count++;

            // 删除旧的、带有计数的整个逻辑消息
            this.deleteChatLine(spamInfo.logicalChatLineId);

            // 创建新的带计数的行
            ITextComponent newComponent = chatComponent.createCopy();
            newComponent.appendText(TextFormatting.GRAY + " [x" + spamInfo.count + "]");
            componentToDisplay = newComponent;

            if (settings.antiSpamScrollToBottom) {
                this.resetScroll();
            }
            // 生成一个新的唯一ID
            logicalIdToUse = (componentToDisplay.getUnformattedText() + this.mc.ingameGUI.getUpdateCounter())
                    .hashCode();
        } else {
            // 这不是刷屏行为 (新消息或超时)
            spamInfo = new SpamInfo(1, currentTime, 0); // ID 稍后设置
            spamTracker.put(rawText, spamInfo);
            // 生成一个新的唯一ID
            logicalIdToUse = (componentToDisplay.getUnformattedText() + this.mc.ingameGUI.getUpdateCounter())
                    .hashCode();
        }

        // 时间戳逻辑
        if (settings.enableTimestamp) {
            String time = TextFormatting.GRAY + "[" + timeFormat.format(new Date()) + "] ";
            ITextComponent timestampComponent = new TextComponentString(time);
            timestampComponent.appendSibling(componentToDisplay);
            componentToDisplay = timestampComponent;
        }

        percentComplete = 0.0F;

        if (!ModConfig.isInternalChatSuppressed()) {
            ChatEventHandler.triggerDisplayedChatMessage(chatComponent, componentToDisplay, logicalIdToUse);
        }

        // 使用新的唯一ID来设置聊天行
        this.setChatLine(componentToDisplay, logicalIdToUse, this.mc.ingameGUI.getUpdateCounter(), false);
        logChatLine(componentToDisplay.getUnformattedText().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n"));

        // 更新刷屏信息中的ID和时间
        spamInfo.logicalChatLineId = logicalIdToUse;
        spamInfo.lastTime = currentTime;
    }

    private boolean matches(String text, String filter, boolean regex) {
        if (regex) {
            try {
                Pattern pattern = patternCache.computeIfAbsent(filter, Pattern::compile);
                return pattern.matcher(text).find();
            } catch (PatternSyntaxException e) {
                // 如果正则表达式无效，则回退到普通包含匹配
                return text.contains(filter);
            }
        } else {
            return text.contains(filter);
        }
    }

    // !! 核心修复：确保 setChatLine 在创建新行时生成一个唯一的、安全的ID !!
    private void setChatLine(ITextComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly) {
        // 如果传入的ID不为0，说明是替换操作或刷屏更新，我们需要先删除与此ID关联的所有旧行。
        if (chatLineId != 0) {
            this.deleteChatLine(chatLineId);
        }

        int i = MathHelper.floor((float) this.getChatWidth() / this.getChatScale());
        List<ITextComponent> list = GuiUtilRenderComponents.splitText(chatComponent, i, this.mc.fontRenderer, false,
                false);
        boolean flag = this.getChatOpen();
        newLines = list.size() - 1;

        for (ITextComponent itextcomponent : list) {
            // 关键修复：所有被分割出来的视觉行，都使用同一个 chatLineId
            this.drawnChatLines.add(0, new ChatLine(updateCounter, itextcomponent, chatLineId));
        }

        while (this.drawnChatLines.size() > 100) {
            // COW list: size() can change between the check and remove — guard
            synchronized (this.drawnChatLines) {
                int n = this.drawnChatLines.size();
                if (n > 0) this.drawnChatLines.remove(n - 1);
            }
        }

        if (!displayOnly) {
            // 原始的、未分割的逻辑消息行也使用同一个 chatLineId 存入 chatLines
            this.chatLines.add(0, new ChatLine(updateCounter, chatComponent, chatLineId));
            while (this.chatLines.size() > 100) {
                synchronized (this.chatLines) {
                    int n = this.chatLines.size();
                    if (n > 0) this.chatLines.remove(n - 1);
                }
            }
        }
    }

    // (以下所有方法保持不变)

    @Override
    public void clearChatMessages(boolean clearSent) {
        this.drawnChatLines.clear();
        this.chatLines.clear();
        if (clearSent) {
            this.sentMessages.clear();
        }
        this.spamTracker.clear(); // 清空刷屏记录
    }

    @Override
    public void refreshChat() {
        this.drawnChatLines.clear();
        this.resetScroll();
        for (int i = this.chatLines.size() - 1; i >= 0; --i) {
            ChatLine chatline = this.chatLines.get(i);
            this.setChatLine(chatline.getChatComponent(), chatline.getChatLineID(), chatline.getUpdatedCounter(), true);
        }
    }

    @Override
    public List<String> getSentMessages() {
        return this.sentMessages;
    }

    @Override
    public void addToSentMessages(String message) {
        if (this.sentMessages.isEmpty() || !this.sentMessages.get(this.sentMessages.size() - 1).equals(message)) {
            this.sentMessages.add(message);
        }
    }

    @Override
    public void resetScroll() {
        this.scrollPos = 0;
        this.isScrolled = false;
    }

    @Override
    public void scroll(int amount) {
        this.scrollPos += amount;
        int i = this.drawnChatLines.size();
        if (this.scrollPos > i - this.getLineCount()) {
            this.scrollPos = i - this.getLineCount();
        }
        if (this.scrollPos <= 0) {
            this.scrollPos = 0;
            this.isScrolled = false;
        }
    }

    @Override
    @Nullable
    public ITextComponent getChatComponent(int mouseX, int mouseY) {
        if (!this.getChatOpen()) {
            return null;
        } else {
            ScaledResolution scaledresolution = new ScaledResolution(this.mc);
            int scaleFactor = scaledresolution.getScaleFactor();
            float chatScale = this.getChatScale();
            ChatOptimizationConfig settings = ChatOptimizationConfig.instance(); // 获取设置实例

            // 1. 获取在缩放后的GUI空间中的鼠标坐标
            int scaledMouseX = mouseX / scaleFactor;
            int scaledMouseY = mouseY / scaleFactor;

            // 2. 计算鼠标相对于聊天框平移原点(2 + xOffset, 8 + yOffset)的局部坐标
            // 这是 GlStateManager.translate(...) 的逆运算
            float localX = (scaledMouseX - (2.0F + settings.xOffset)) / chatScale;

            // !! 核心修复：这里的Y坐标计算是正确的，但后续逻辑错了 !!
            float localY = (scaledMouseY - (35.0F + settings.yOffset)) / chatScale;

            // 3. 检查鼠标是否在聊天框的水平范围内
            if (localX >= 0 && localX <= (float) this.getChatWidth() / chatScale) { // 稍微调整水平检查范围
                int visibleLineCount = this.getLineCount();

                // 4. !! 核心修复：修正垂直范围检查 !!
                // localY 现在是相对于聊天框顶部的正值。我们检查它是否在可见行区域内。
                if (localY >= 0 && localY < visibleLineCount * 9) {

                    // 5. !! 核心修复：修正行索引计算 !!
                    // 因为 localY 是正数，所以不再需要负号。
                    int lineIndex = MathHelper.floor(localY / 9.0F);

                    // 6. 加上滚动偏移量，得到在drawnChatLines列表中的最终索引
                    int finalIndex = lineIndex + this.scrollPos;

                    // 7. 检查索引是否有效，然后执行原有的组件查找逻辑
                    if (finalIndex >= 0 && finalIndex < this.drawnChatLines.size()) {
                        ChatLine chatline = this.drawnChatLines.get(finalIndex);
                        int componentXOffset = 0;

                        for (ITextComponent itextcomponent : chatline.getChatComponent()) {
                            if (itextcomponent instanceof TextComponentString) {
                                componentXOffset += this.mc.fontRenderer
                                        .getStringWidth(GuiUtilRenderComponents.removeTextColorsIfConfigured(
                                                ((TextComponentString) itextcomponent).getText(), false));
                                // 使用 localX 进行比较
                                if (componentXOffset > localX) {
                                    return itextcomponent;
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }
    }

    // !! 新增辅助方法：为了让 ChatEventHandler 能访问到原始逻辑消息列表 !!
    public List<ChatLine> getLogicalChatLines() {
        return this.chatLines;
    }

    public List<ChatLine> getDrawnChatLines() {
        return this.drawnChatLines;
    }

    @Override
    public void deleteChatLine(int id) {
        this.drawnChatLines.removeIf(chatline -> chatline.getChatLineID() == id);
        this.chatLines.removeIf(chatline1 -> chatline1.getChatLineID() == id);
    }

    public int getScrollPos() {
        return this.scrollPos;
    }

    public boolean isScrolled() {
        return this.isScrolled;
    }
}

