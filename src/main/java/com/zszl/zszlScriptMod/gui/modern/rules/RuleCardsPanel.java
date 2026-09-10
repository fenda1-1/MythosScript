package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormWidget;
import net.minecraft.client.gui.FontRenderer;

/** Scrollable card list; the owner retains validation and editing of each record. */
final class RuleCardsPanel implements ModernFormWidget {
    private final Supplier<List<String>> rows;
    private final Runnable add;
    private final IntConsumer edit, delete;
    private final BiConsumer<Integer, Integer> move;
    private final List<Rect> hits = new ArrayList<>();
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private Rect bounds, body, addButton, resize, menu;
    private int scroll, maxScroll, desiredHeight = 210, selected = -1, dragging = -1, drop = -1;
    private int resizeY, resizeHeight;
    private boolean resizing;
    RuleCardsPanel(Supplier<List<String>> rows, Runnable add, IntConsumer edit, IntConsumer delete,
            BiConsumer<Integer, Integer> move) {
        this.rows=rows; this.add=add; this.edit=edit; this.delete=delete; this.move=move;
    }
    public int height(int width, int available) { return Math.max(120, Math.min(desiredHeight, Math.max(120, available-28))); }
    public void draw(FontRenderer font, Rect b, int mx, int my) {
        bounds=b;
        ModernUiRenderer.drawSubtlePanel(b.x,b.y,b.width,b.height,5,ModernUiRenderer.SHELL,ModernUiRenderer.BORDER);
        List<String> values=rows.get();
        ModernUiRenderer.drawText(font,"共 "+values.size()+" 项"+(move==null?"":" · 拖动左侧调整顺序"),b.x+8,b.y+9,ModernUiRenderer.SUBTLE_TEXT,Math.max(1,b.width-90));
        addButton=add==null?null:new Rect(b.right()-64,b.y+4,56,22);
        if(addButton!=null) button(font,addButton,"添加",mx,my);
        body=new Rect(b.x+5,b.y+31,Math.max(1,b.width-10),Math.max(1,b.height-41));
        resize=new Rect(b.x+5,b.bottom()-7,Math.max(1,b.width-10),7);
        maxScroll=Math.max(0,values.size()*34-body.height); scroll=Math.max(0,Math.min(maxScroll,scroll));
        hits.clear(); ModernUiRenderer.beginClip(body);
        for(int i=0;i<values.size();i++) {
            Rect r=new Rect(body.x,body.y+i*34-scroll,Math.max(1,body.width-ModernHoverScrollbar.GUTTER),30); hits.add(r);
            boolean hovered=body.contains(mx,my)&&r.contains(mx,my);
            ModernUiRenderer.drawSubtlePanel(r.x,r.y,r.width,r.height,4,selected==i?ModernUiRenderer.ACCENT_DIM:hovered?ModernUiRenderer.SURFACE_HOVER:ModernUiRenderer.SURFACE,selected==i?ModernUiRenderer.ACCENT:ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font,move==null?String.valueOf(i+1):"≡",r.x+5,r.y+10,ModernUiRenderer.SUBTLE_TEXT,18);
            ModernUiRenderer.drawText(font,values.get(i),r.x+24,r.y+10,ModernUiRenderer.TEXT,Math.max(1,r.width-53));
            if(delete!=null) ModernUiRenderer.drawText(font,"×",r.right()-18,r.y+10,ModernUiRenderer.WARNING,14);
            if(dragging>=0&&drop==i) ModernUiRenderer.drawDivider(r.x,r.y,r.width,ModernUiRenderer.ACCENT);
        }
        if(values.isEmpty()) ModernUiRenderer.drawText(font,add==null?"暂无内容":"暂无条目，点击添加",body.x+8,body.y+10,ModernUiRenderer.MUTED_TEXT,body.width-16);
        ModernUiRenderer.endClip();
        scrollbar.draw(body,scroll,maxScroll,body.height,values.size()*34,mx,my,v->scroll=v);
        ModernUiRenderer.drawDivider(b.x+b.width/2-18,b.bottom()-4,36,ModernUiRenderer.BORDER);
        if(menu!=null) button(font,menu,"删除此项",mx,my);
    }
    private void button(FontRenderer f, Rect r, String text, int x, int y) {
        ModernUiRenderer.drawSubtlePanel(r.x,r.y,r.width,r.height,4,r.contains(x,y)?ModernUiRenderer.SURFACE_HOVER:ModernUiRenderer.SURFACE,ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(f,text,r.x+5,r.y+7,ModernUiRenderer.TEXT,r.width-10);
    }
    public boolean mouseClicked(int x,int y,int button) {
        if(menu!=null) { if(button==0&&menu.contains(x,y)&&selected>=0&&selected<rows.get().size()) delete.accept(selected); menu=null; return true; }
        if(button==0&&addButton!=null&&addButton.contains(x,y)) { add.run(); return true; }
        if(button==0&&resize!=null&&resize.contains(x,y)) { resizing=true;resizeY=y;resizeHeight=desiredHeight;return true; }
        if(button==0&&scrollbar.beginDrag(x,y)) return true;
        if(body!=null&&body.contains(x,y)) for(int i=0;i<hits.size();i++) if(hits.get(i).contains(x,y)) {
            selected=i; Rect r=hits.get(i);
            if(button==1&&delete!=null) menu=new Rect(Math.max(body.x,Math.min(x,body.right()-90)),Math.max(body.y,Math.min(y,body.bottom()-24)),90,24);
            else if(button==0) {
                if(delete!=null&&x>=r.right()-24) delete.accept(i);
                else if(move!=null&&x<r.x+24) { dragging=i;drop=i; }
                else if(edit!=null) edit.accept(i);
            }
            return true;
        }
        return true;
    }
    public boolean mouseClickMove(int x,int y,int button,long elapsed) {
        if(button!=0) return false;
        if(resizing) { desiredHeight=Math.max(120,Math.min(800,resizeHeight+y-resizeY));return true; }
        if(scrollbar.isDragging()) { scrollbar.applyDrag(x,y);return true; }
        if(dragging>=0&&body!=null) {
            if(y<body.y+12) scroll=Math.max(0,scroll-6);
            if(y>body.bottom()-12) scroll=Math.min(maxScroll,scroll+6);
            drop=Math.max(0,Math.min(rows.get().size()-1,(y-body.y+scroll)/34));return true;
        }
        return false;
    }
    public boolean mouseReleased(int x,int y,int button) {
        if(button!=0) return false;
        if(dragging>=0&&drop>=0&&dragging!=drop&&dragging<rows.get().size()) move.accept(dragging,drop);
        boolean handled=resizing||dragging>=0||scrollbar.isDragging();
        resizing=false;dragging=-1;scrollbar.endDrag();return handled;
    }
    public boolean handleMouseWheel(int wheel,int x,int y) {
        if(wheel==0||body==null||!body.contains(x,y)) return false;
        scroll=Math.max(0,Math.min(maxScroll,scroll+(wheel>0?-34:34)));return true;
    }
    public boolean handleEscape() { boolean handled=menu!=null; menu=null;return handled; }
    public void blur() { menu=null;dragging=-1;resizing=false;scrollbar.endDrag(); }
    public String getHoveredTooltip(int x,int y) {
        if(body!=null&&body.contains(x,y)) for(int i=0;i<hits.size();i++) if(hits.get(i).contains(x,y)&&i<rows.get().size()) return rows.get().get(i);
        return "";
    }
    public Object snapshot() { return new ArrayList<>(rows.get()); }
}
