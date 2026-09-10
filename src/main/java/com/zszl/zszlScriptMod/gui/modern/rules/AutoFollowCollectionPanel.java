package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;
import com.zszl.zszlScriptMod.gui.modern.*;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormWidget;
import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;

/** Bounded, resizable collection editor; coordinates and names stay in the owning draft. */
final class AutoFollowCollectionPanel implements ModernFormWidget {
    enum Mode { POINTS, NAMES, SCORES }
    private final Mode mode;
    private final String title;
    private final Supplier<String> get;
    private final Consumer<String> set;
    private final Supplier<double[]> area;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final List<Rect> cards = new ArrayList<>();
    private final Map<String, Rect> buttons = new LinkedHashMap<>();
    private final ModernTextField[] fields = new ModernTextField[3];
    private Rect bounds, body, resize, context, viewport;
    private FontRenderer font;
    private int desiredHeight = 220, scroll, maxScroll, selected = -1, contextIndex = -1;
    private int dragIndex = -1, dropIndex, dragStartY, resizeStartY, resizeStartHeight;
    private boolean resizing, dragging, editing, suggestions;
    private String error = "";
    private List<String> rows = Collections.emptyList();
    private List<String> liveRows = Collections.emptyList();
    private long nextRefresh;

    AutoFollowCollectionPanel(Mode mode, String title, Supplier<String> get, Consumer<String> set) {
        this(mode, title, get, set, null);
    }

    AutoFollowCollectionPanel(Mode mode, String title, Supplier<String> get, Consumer<String> set, Supplier<double[]> area) {
        this.mode = mode; this.title = title; this.get = get; this.set = set; this.area = area;
    }

    @Override public void setViewport(Rect viewport) { this.viewport = viewport; }

    @Override public int height(int width, int availableHeight) {
        int minimum = mode == Mode.POINTS && editing && width < 280 ? 180 : 152;
        return Math.max(minimum, Math.min(desiredHeight, Math.max(minimum, availableHeight - 24)));
    }

    private List<String> values() { return AutoFollowUiLists.entries(get == null ? "" : get.get(), mode == Mode.POINTS); }
    private void write(List<String> values) { set.accept(String.join(mode == Mode.POINTS ? "; " : ", ", values)); }

    @Override public void ensureInitialized(FontRenderer font) {
        this.font = font;
        for (int i = 0; i < fields.length; i++) if (fields[i] == null) {
            fields[i] = new ModernTextField(i, font, 0, 0, 1, 18);
            fields[i].setMaxStringLength(mode == Mode.POINTS ? 48 : 256);
            fields[i].setEnableBackgroundDrawing(false);
        }
    }

    @Override public void draw(FontRenderer font, Rect bounds, int mx, int my) {
        ensureInitialized(font); this.bounds = bounds;
        buttons.clear(); cards.clear();
        if ((mode == Mode.SCORES || suggestions) && System.currentTimeMillis() >= nextRefresh) {
            liveRows = mode == Mode.SCORES ? scoreCards() : nearbyNames();
            nextRefresh = System.currentTimeMillis() + 250;
        }
        rows = mode == Mode.SCORES || suggestions ? liveRows : values();
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        text(title + "  ·  " + rows.size(), bounds.x + 8, bounds.y + 8, bounds.width - 16, ModernUiRenderer.TEXT);
        int top = bounds.y + 26;
        if (mode == Mode.POINTS) {
            int columns = bounds.width < 280 ? 2 : 4;
            int w = Math.max(16, (bounds.width - 12) / columns - 4);
            String[] keys = {"pick", "manual", "repick", "delete"};
            String[] labels = {"批量取点", "手动添加", "重选位置", "删除"};
            for (int i = 0; i < 4; i++) button(keys[i], labels[i], bounds.x + 6 + (i % columns) * (w + 4),
                    top + (i / columns) * 26, w, mx, my);
            top += (4 / columns) * 26;
            if (editing) {
                int fw = Math.max(20, (bounds.width - 80) / 3);
                for (int i = 0; i < 3; i++) field(i, new Rect(bounds.x + 7 + i * (fw + 4), top, fw, 21), new String[]{"X", "Y(可空)", "Z"}[i]);
                button("apply", "应用", bounds.right() - 60, top, 53, mx, my);
                top += 27;
            }
        } else if (mode == Mode.NAMES) {
            boolean narrow = bounds.width < 260;
            int iw = Math.max(30, bounds.width - (narrow ? 14 : 153));
            field(0, new Rect(bounds.x + 7, top, iw, 21), "输入目标名称");
            if (narrow) top += 26;
            int start = narrow ? bounds.x + 7 : bounds.x + iw + 12;
            int bw = narrow ? Math.max(16, (bounds.width - 22) / 3) : 42;
            button("add", "添加", start, top, bw, mx, my);
            button("nearby", suggestions ? "返回" : "附近", start + bw + 4, top, bw, mx, my);
            button("delete", "删除", start + 2 * (bw + 4), top, bw, mx, my);
            top += 27;
        }
        String hint = !error.isEmpty() ? error : mode == Mode.POINTS ? "拖动左侧把手排序 · 点击卡片编辑 · 拖底边调整高度"
                : mode == Mode.NAMES ? (suggestions ? "点击附近目标即可添加" : "支持批量添加 · 右键卡片可删除") : "实时评分 · 按总分降序 · 滚动查看全部候选";
        text(hint, bounds.x + 8, top, bounds.width - 16, error.isEmpty() ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.WARNING);
        body = new Rect(bounds.x + 6, top + 15, Math.max(1, bounds.width - 12), Math.max(12, bounds.bottom() - top - 27));
        int contentWidth = ModernHoverScrollbar.contentWidth(body.width);
        int total = 0;
        for (String row : rows) total += cardHeight(row, contentWidth) + 4;
        maxScroll = Math.max(0, total - body.height);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ModernUiRenderer.beginClip(body);
        int y = body.y - scroll;
        for (int i = 0; i < rows.size(); i++) {
            String value = rows.get(i);
            int h = cardHeight(value, contentWidth);
            Rect card = new Rect(body.x, y, contentWidth, h); cards.add(card);
            if (card.bottom() >= body.y && card.y < body.bottom()) {
                boolean highlight = !suggestions && i == selected;
                ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 4,
                        highlight ? ModernUiRenderer.ACCENT_DIM : card.contains(mx,my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        highlight ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                if (mode == Mode.POINTS) {
                    text("≡", card.x + 5, card.y + 14, 12, ModernUiRenderer.MUTED_TEXT);
                    text("回点 " + (i + 1), card.x + 23, card.y + 6, card.width - 30, ModernUiRenderer.TEXT);
                    String[] xyz = AutoFollowUiLists.coordinates(value);
                    text("X " + display(xyz[0]) + "   Y " + (xyz[1].isEmpty() ? "自动" : display(xyz[1])) + "   Z " + display(xyz[2]),
                            card.x + 23, card.y + 23, card.width - 30, ModernUiRenderer.SUBTLE_TEXT);
                } else if (mode == Mode.SCORES) {
                    int lineY = card.y + 6;
                    for (String line : wrap(value, card.width - 14)) {
                        text(line, card.x + 7, lineY, card.width - 14, ModernUiRenderer.TEXT); lineY += 12;
                    }
                } else text(value, card.x + 8, card.y + 9, card.width - 16, ModernUiRenderer.TEXT);
            }
            y += h + 4;
        }
        if (rows.isEmpty()) text(mode == Mode.SCORES ? "暂无候选目标，运行追怪后显示评分。" : "列表为空，使用上方按钮添加。", body.x + 6, body.y + 8, contentWidth - 12, ModernUiRenderer.MUTED_TEXT);
        if (dragging) {
            int markerY = dropIndex < cards.size() ? cards.get(dropIndex).y : y;
            ModernUiRenderer.drawRoundedRect(body.x, markerY - 2, contentWidth, 2, 1, ModernUiRenderer.ACCENT);
        }
        ModernUiRenderer.endClip();
        scrollbar.draw(body, scroll, maxScroll, body.height, total, mx, my, v -> scroll = v);
        resize = new Rect(bounds.x + 6, bounds.bottom() - 9, bounds.width - 12, 8);
        ModernUiRenderer.drawRoundedRect(bounds.x + bounds.width / 2 - 18, bounds.bottom() - 5, 36, 2, 1,
                resizing || resize.contains(mx,my) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER);
        if (context != null) {
            ModernUiRenderer.drawSubtlePanel(context.x, context.y, context.width, context.height, 4,
                    ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
            text("删除此项", context.x + 8, context.y + 8, context.width - 16, ModernUiRenderer.WARNING);
        }
    }

    private String display(String value) { return String.format(Locale.ROOT, "%.1f", Double.parseDouble(value)); }
    private List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\n")) lines.addAll(font.listFormattedStringToWidth(line, Math.max(12,width)));
        return lines;
    }
    private int cardHeight(String value, int width) { return mode == Mode.POINTS ? 42 : mode == Mode.NAMES ? 28 : 12 + wrap(value, width - 14).size() * 12; }
    private void text(String s, int x, int y, int w, int color) { ModernUiRenderer.drawText(font, s, x, y, color, Math.max(1,w)); }
    private void button(String key, String label, int x, int y, int w, int mx, int my) {
        Rect r = new Rect(x,y,w,21); buttons.put(key,r);
        ModernUiRenderer.drawSubtlePanel(x,y,w,21,4,r.contains(mx,my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER);
        text(label,x+5,y+6,w-10,ModernUiRenderer.TEXT);
    }
    private void field(int i, Rect r, String placeholder) {
        ModernTextField f = fields[i];
        f.layout(r);
        f.setVisible(true);
        f.setEnabled(true);
        f.setPlaceholder(placeholder);
        ModernUiRenderer.drawSubtlePanel(r.x,r.y,r.width,r.height,4,ModernUiRenderer.SHELL_RAISED,f.isFocused()?ModernUiRenderer.ACCENT:ModernUiRenderer.BORDER);
        f.drawTextContents(font);
    }

    @Override public boolean mouseClicked(int x, int y, int button) {
        if (context != null) {
            boolean delete = context.contains(x,y); context = null;
            if (delete && button == 0) { remove(contextIndex); return true; }
        }
        if (button == 0 && resize != null && resize.contains(x,y)) {
            resizing=true; resizeStartY=y; resizeStartHeight=bounds.height; return true;
        }
        if (button == 0 && scrollbar.beginDrag(x,y)) return true;
        if (button == 0) {
            for (Map.Entry<String,Rect> entry : buttons.entrySet()) if (entry.getValue().contains(x,y)) { action(entry.getKey()); return true; }
            for (int i=0;i<fields.length;i++) {
                boolean visible = mode == Mode.NAMES && i==0 || mode == Mode.POINTS && editing;
                fields[i].setFocused(false);
                if (visible) fields[i].mouseClicked(x,y,button);
            }
        }
        if (body != null && body.contains(x,y)) for (int i=0;i<cards.size();i++) if (cards.get(i).contains(x,y)) {
            if (mode == Mode.SCORES) return true;
            if (suggestions) { if (button == 0) addName(rows.get(i)); return true; }
            if (button == 1) {
                contextIndex=i;
                int topLimit = viewport == null ? bounds.y : Math.max(bounds.y, viewport.y);
                int bottomLimit = viewport == null ? bounds.bottom() : Math.min(bounds.bottom(), viewport.bottom());
                context=new Rect(Math.min(x, bounds.right()-98), Math.max(topLimit, Math.min(y,bottomLimit-26)),90,25); return true;
            }
            if (button == 0) {
                if (!commit()) return true;
                selected=i;
                if (mode == Mode.POINTS) {
                    if (x < cards.get(i).x+20) { dragIndex=i; dropIndex=i; dragStartY=y; }
                    else editPoint(i);
                }
            }
            return true;
        }
        return true;
    }

    private void action(String key) {
        if ("apply".equals(key)) { commit(); return; }
        if ("add".equals(key)) { addName(fields[0].getText()); return; }
        if ("nearby".equals(key)) { suggestions=!suggestions; nextRefresh=0; scroll=0; context=null; return; }
        if ("delete".equals(key)) { if (!suggestions) remove(selected); return; }
        if (!commit()) return;
        if ("manual".equals(key)) { selected=-1; editing=true; for (ModernTextField f:fields) f.setText(""); fields[0].setFocused(true); }
        if ("pick".equals(key) || "repick".equals(key)) {
            final int replace = "repick".equals(key) ? selected : -1;
            if ("repick".equals(key) && (replace<0 || replace>=values().size())) { error="请先选择一个回点"; return; }
            try {
                AutoFollowAreaPicker.startReturns(area == null ? null : area.get(), values(), replace, list -> {
                    write(list); selected = list.isEmpty() ? -1 : list.size() - 1; editing = false; error = "";
                });
            } catch (IllegalArgumentException invalid) {
                error = "无法取点：请检查点1点2范围和回点坐标。";
            }
        }
    }

    private void editPoint(int index) {
        String[] xyz=AutoFollowUiLists.coordinates(values().get(index));
        for (int i=0;i<3;i++) { fields[i].setText(xyz[i]); fields[i].setFocused(false); }
        editing=true; error="";
    }
    private void remove(int index) {
        List<String> list=values(); if(index<0 || index>=list.size()) { error="请先选择一项"; return; }
        list.remove(index); write(list); selected=-1; editing=false; context=null; error="";
    }
    private void addName(String value) {
        List<String> additions=AutoFollowUiLists.entries(value,false);
        if(additions.isEmpty()) { error="请输入目标名称"; return; }
        List<String> list=values(); for(String name:additions) if(!list.contains(name)) list.add(name);
        write(list); fields[0].setText(""); error="";
    }
    @Override public boolean commit() {
        if (mode == Mode.NAMES && fields[0] != null && !fields[0].getText().trim().isEmpty()) addName(fields[0].getText());
        if (mode != Mode.POINTS || !editing) return true;
        try {
            String value=AutoFollowUiLists.point(fields[0].getText(),fields[1].getText(),fields[2].getText());
            List<String> list=values(); if(selected>=0 && selected<list.size()) list.set(selected,value); else { list.add(value); selected=list.size()-1; }
            write(list); editing=false; blur(); error=""; return true;
        } catch (IllegalArgumentException e) { error=e.getMessage(); return false; }
    }
    @Override public boolean keyTyped(char c, int key) {
        if (key==Keyboard.KEY_ESCAPE && handleEscape()) return true;
        if (!isTextInputFocused()) return false;
        if (key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER) { if(mode==Mode.NAMES) addName(fields[0].getText()); else commit(); return true; }
        if (key==Keyboard.KEY_TAB && mode==Mode.POINTS) {
            for(int i=0;i<3;i++) if(fields[i].isFocused()) { fields[i].setFocused(false); fields[(i+1)%3].setFocused(true); break; } return true;
        }
        for(ModernTextField f:fields) if(f.isFocused()) return f.textboxKeyTyped(c,key);
        return false;
    }
    @Override public boolean handleEscape() {
        if(context!=null) { context=null; return true; }
        if(editing) { editing=false; error=""; blur(); return true; }
        if(suggestions) { suggestions=false; scroll=0; return true; }
        if(isTextInputFocused()) { blur(); return true; }
        return false;
    }
    @Override public boolean mouseClickMove(int x,int y,int button,long time) {
        if(button!=0) return false;
        if(resizing) { desiredHeight=Math.max(128,Math.min(640,resizeStartHeight+y-resizeStartY)); return true; }
        if(scrollbar.isDragging()) { scrollbar.applyDrag(x,y); return true; }
        if(dragIndex>=0) {
            dragging |= Math.abs(y-dragStartY)>4;
            if(dragging) {
                if(y<body.y+15) scroll=Math.max(0,scroll-6);
                if(y>body.bottom()-15) scroll=Math.min(maxScroll,scroll+6);
                dropIndex=cards.size();
                for(int i=0;i<cards.size();i++) if(y<cards.get(i).y+cards.get(i).height/2) { dropIndex=i; break; }
            }
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(int x,int y,int button) {
        if(button!=0) return false;
        boolean handled=resizing||dragIndex>=0||scrollbar.isDragging();
        if(dragging) { List<String> list=values(); AutoFollowUiLists.move(list,dragIndex,dropIndex); write(list); selected=-1; editing=false; }
        resizing=false; dragging=false; dragIndex=-1; scrollbar.endDrag(); return handled;
    }
    @Override public boolean handleMouseWheel(int wheel,int x,int y) {
        if(body==null || !body.contains(x,y)) return false;
        scroll=Math.max(0,Math.min(maxScroll,scroll+(wheel>0?-30:30))); context=null; return true;
    }
    @Override public boolean isTextInputFocused() { for(ModernTextField f:fields) if(f!=null&&f.isFocused()) return true; return false; }
    @Override public Object snapshot() { return get == null ? null : get.get(); }
    @Override public boolean isDirty() {
        if (mode == Mode.NAMES) return fields[0] != null && !fields[0].getText().isEmpty();
        if (!editing) return false;
        List<String> current = values();
        if (selected < 0 || selected >= current.size()) return true;
        try {
            return !current.get(selected).equals(AutoFollowUiLists.point(fields[0].getText(), fields[1].getText(), fields[2].getText()));
        } catch (IllegalArgumentException invalid) { return true; }
    }
    @Override public void blur() { for(ModernTextField f:fields) if(f!=null) f.setFocused(false); context=null; }
    @Override public String getHoveredTooltip(int x,int y) {
        if(body!=null&&body.contains(x,y)) for(int i=0;i<cards.size();i++) if(cards.get(i).contains(x,y)) return rows.get(i);
        return error;
    }

    private List<String> nearbyNames() {
        List<String> result=new ArrayList<>(); Minecraft mc=Minecraft.getMinecraft();
        if(mc.world!=null) mc.world.loadedEntityList.stream().filter(e->e instanceof EntityLivingBase && e!=mc.player)
                .sorted(Comparator.comparingDouble(e->mc.player==null?0:e.getDistanceSq(mc.player)))
                .forEach(e->{ String name=net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(e.getName()); if(name!=null&&!result.contains(name)) result.add(name); });
        return result;
    }
    private List<String> scoreCards() {
        List<AutoFollowHandler.ScoredMonsterInfo> scores=new ArrayList<>(AutoFollowHandler.getLastScoredMonstersSnapshot());
        scores.sort(Comparator.comparingDouble((AutoFollowHandler.ScoredMonsterInfo s)->s.totalScore).reversed());
        List<String> result=new ArrayList<>(); int rank=1;
        for(AutoFollowHandler.ScoredMonsterInfo s:scores) result.add(String.format(Locale.ROOT,
                "#%d  %s  ·  总分 %.2f\n距离 %.1f / %.2f分   可见 %s / %.2f分\n可达 %s / %.2f分   高差 %.1f / %.2f分\n锁定加分 %.2f   实体 #%d",
                rank++,s.name,s.totalScore,s.distance,s.distanceScore,s.visible?"是":"否",s.visibilityScore,
                s.reachable?"是":"否",s.reachabilityScore,s.verticalDiff,s.verticalScore,s.lockBonusScore,s.entityId));
        return result;
    }
}
