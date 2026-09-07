package com.zszl.zszlScriptMod.utils;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * HUD文本扫描器.
 * <p>
 * 这个类通过一个自定义的FontRenderer来捕获屏幕上绘制的所有文本及其坐标.
 * 它能够将空间上相邻的文本行聚类成逻辑区块.
 */
public class HudTextScanner {

    // 使用单例模式, 方便全局调用
    public static final HudTextScanner INSTANCE = new HudTextScanner();

    // 存储本帧捕获的所有文本及其元数据
    private final List<CapturedText> capturedTextsThisFrame = new CopyOnWriteArrayList<>();

    // --- 内部数据结构 ---

    /**
     * 代表一个被捕获的文本元素，包含内容和坐标。
     */
    public static class CapturedText {
        public final String text;
        public final float x, y;

        public CapturedText(String text, float x, float y) {
            // 移除Minecraft颜色代码, 以获得纯净的文本
            this.text = text.replaceAll("§[0-9a-fk-or]", "").trim();
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return String.format("'%s' @ (%.1f, %.1f)", text, x, y);
        }
    }

    /**
     * 代表一个由多行文本组成的逻辑区块。
     */
    public static class TextBlock {
        private final List<CapturedText> lines = new ArrayList<>();
        public final float initialX, initialY;

        public TextBlock(CapturedText firstLine) {
            this.lines.add(firstLine);
            this.initialX = firstLine.x;
            this.initialY = firstLine.y;
        }

        public void addLine(CapturedText line) {
            this.lines.add(line);
        }

        public List<String> getLinesAsString() {
            return lines.stream().map(l -> l.text).collect(Collectors.toList());
        }

        public String getJoinedText(String separator) {
            return String.join(separator, getLinesAsString());
        }

        public CapturedText getLastLine() {
            return lines.get(lines.size() - 1);
        }

        @Override
        public String toString() {
            return String.format("Block @ (%.1f, %.1f) - %s", initialX, initialY, getJoinedText(" | "));
        }
    }

    private HudTextScanner() {
        // 私有构造函数, 防止外部实例化
    }

    /**
     * 在每帧的游戏覆盖层渲染开始前, 清空上一帧的文本数据.
     */
    @SubscribeEvent
    public void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            capturedTextsThisFrame.clear();
        }
    }

    /**
     * 由自定义的 CapturingFontRenderer 调用, 用于添加捕获到的文本和坐标.
     * 
     * @param text 从屏幕上捕获到的文本字符串.
     * @param x    文本的x坐标
     * @param y    文本的y坐标
     */
    public void addText(String text, float x, float y) {
        if (text != null && !text.trim().isEmpty()) {
            capturedTextsThisFrame.add(new CapturedText(text, x, y));
        }
    }

    /**
     * 获取当前屏幕上所有文本的原始列表（带坐标）。
     * 
     * @return 包含当前屏幕所有文本的列表
     */
    public List<CapturedText> getCurrentHudText() {
        return Collections.unmodifiableList(capturedTextsThisFrame);
    }

    /**
     * 分析捕获的文本并将其组合成逻辑区块。
     * 
     * @return 一个包含所有已识别文本区块的列表
     */
    public List<TextBlock> getProcessedTextBlocks() {
        if (capturedTextsThisFrame.isEmpty()) {
            return Collections.emptyList();
        }

        // 复制并排序，按Y坐标优先，然后按X坐标
        List<CapturedText> sortedTexts = new ArrayList<>(capturedTextsThisFrame);
        sortedTexts.sort(Comparator.<CapturedText>comparingDouble(t -> t.y).thenComparingDouble(t -> t.x));

        List<TextBlock> blocks = new ArrayList<>();
        Set<CapturedText> processedTexts = new HashSet<>();

        for (CapturedText currentText : sortedTexts) {
            if (processedTexts.contains(currentText) || currentText.text.isEmpty()) {
                continue;
            }

            // 这是一个新区块的开始
            TextBlock newBlock = new TextBlock(currentText);
            processedTexts.add(currentText);

            // 尝试为这个新区块寻找后续行
            boolean lineAdded;
            do {
                lineAdded = false;
                CapturedText lastLineInBlock = newBlock.getLastLine();

                // 在剩余的文本中寻找最合适的下一行
                CapturedText bestCandidate = null;
                double minDistance = Double.MAX_VALUE;

                for (CapturedText candidate : sortedTexts) {
                    if (processedTexts.contains(candidate))
                        continue;

                    // 候选行必须在当前行的下方
                    if (candidate.y > lastLineInBlock.y) {
                        // 计算距离：垂直距离权重远大于水平距离
                        double yDiff = candidate.y - lastLineInBlock.y;
                        double xDiff = Math.abs(candidate.x - lastLineInBlock.x);
                        double distance = yDiff + xDiff * 2.0; // Y距离更重要

                        // 必须在合理的垂直和水平范围内
                        if (yDiff < 20 && xDiff < 15 && distance < minDistance) {
                            minDistance = distance;
                            bestCandidate = candidate;
                        }
                    }
                }

                if (bestCandidate != null) {
                    newBlock.addLine(bestCandidate);
                    processedTexts.add(bestCandidate);
                    lineAdded = true;
                }

            } while (lineAdded);

            blocks.add(newBlock);
        }

        return blocks;
    }
}
