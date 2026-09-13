package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormWidget;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ExpressionEditorPreview;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ModernExpressionEditorPanel;
import net.minecraft.client.gui.FontRenderer;

/** A compact filter-expression list. Each row opens the shared editor on demand. */
public final class ModernExpressionListWidget implements ModernFormWidget {
    private final ModernFormSettingsTab.TextValue value;
    private final ModernExpressionEditorPanel editor = new ModernExpressionEditorPanel();
    private ModernMainLayout.Rect bounds;
    private FontRenderer font;
    private int editing = -1;
    private boolean initialized;
    public ModernExpressionListWidget(ModernFormSettingsTab.TextValue value) { this.value = value; }
    private List<String> list() {
        List<String> result = new ArrayList<>(); String raw = value == null ? "" : value.get();
        for (String s : (raw == null ? "" : raw).replace("\\n", "\n").split("\n")) if (!s.trim().isEmpty()) result.add(s.trim());
        return result;
    }
    private void save(List<String> xs) { StringBuilder b = new StringBuilder(); for (String s : xs) { if (b.length() > 0) b.append('\n'); b.append(s); } if (value != null) value.set(b.toString()); }
    @Override public void ensureInitialized(FontRenderer f) { font = f; initialized = true; }
    @Override public void updateScreen() { if (editor.isOpen()) editor.updateScreen(); }
    @Override public void draw(FontRenderer f, ModernMainLayout.Rect r, int mx, int my) {
        font = f; bounds = r;
        if (editor.isOpen()) { editor.draw(f, r, mx, my); return; }
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 6, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(f, "过滤表达式", r.x + 14, r.y + 10, ModernUiRenderer.TEXT, r.width - 110);
        ModernUiRenderer.drawText(f, "添加", r.right() - 58, r.y + 10, ModernUiRenderer.ACCENT, 48);
        List<String> xs = list(); int y = r.y + 38;
        for (int i=0;i<xs.size();i++) { ModernUiRenderer.drawSubtlePanel(r.x+10,y,r.width-20,32,5,ModernUiRenderer.INPUT_SURFACE,ModernUiRenderer.BORDER_SUBTLE); ModernUiRenderer.drawText(f,(i+1)+". "+xs.get(i),r.x+20,y+9,ModernUiRenderer.TEXT,r.width-150); ModernUiRenderer.drawText(f,"改",r.right()-78,y+9,ModernUiRenderer.ACCENT,24); ModernUiRenderer.drawText(f,"删",r.right()-42,y+9,ModernUiRenderer.DANGER,24); y+=38; }
    }
    private void open(final int index) {
        editing=index; List<String> xs=list(); String initial=index>=0&&index<xs.size()?xs.get(index):"";
        editor.open(font, initial, "编辑过滤表达式", ExpressionEditorPreview.Mode.ITEM_FILTER, Collections.emptyList(), null,-1,-1,
            expression -> { List<String> ys=list(); String s=expression==null?"":expression.trim(); if(index>=0&&index<ys.size()) ys.set(index,s); else if(!s.isEmpty()) ys.add(s); ys.removeIf(String::isEmpty); save(ys); editing=-1; },
            () -> editing=-1);
    }
    @Override public boolean mouseClicked(int x,int y,int b) { if(editor.isOpen()) return editor.mouseClicked(x,y,b); if(b!=0||bounds==null||!bounds.contains(x,y)) return false; if(y<bounds.y+34&&x>bounds.right()-90){open(-1);return true;} int i=(y-(bounds.y+38))/38; List<String> xs=list(); if(i>=0&&i<xs.size()){int rowY=bounds.y+38+i*38;if(y<rowY+32){if(x>bounds.right()-55){xs.remove(i);save(xs);}else open(i);return true;}} return true; }
    @Override public boolean mouseClickMove(int x,int y,int b,long t){return editor.isOpen()&&editor.mouseClickMove(x,y,b,t);} @Override public boolean mouseReleased(int x,int y,int s){return editor.isOpen()&&editor.mouseReleased(x,y,s);} @Override public boolean keyTyped(char c,int k){return editor.isOpen()&&editor.keyTyped(c,k);} @Override public boolean handleMouseWheel(int w){return editor.isOpen()&&editor.handleMouseWheel(w,0,0);} @Override public boolean handleMouseWheel(int w,int x,int y){return editor.isOpen()&&editor.handleMouseWheel(w,x,y);} @Override public boolean handleEscape(){return editor.isOpen()&&editor.handleEscape();} @Override public boolean isTextInputFocused(){return editor.isOpen()&&editor.isTextInputFocused();} @Override public void clearTextInputFocusOutside(int x,int y){if(editor.isOpen())editor.clearTextInputFocusOutside(x,y);} @Override public boolean containsContent(int x,int y){return bounds!=null&&bounds.contains(x,y);} @Override public String getHoveredTooltip(int x,int y){return editor.isOpen()?editor.getHoveredTooltip():"";} @Override public int height(int w,int h){return Math.max(90,Math.min(300,h));} @Override public void setViewport(ModernMainLayout.Rect r){bounds=r;} @Override public Object snapshot(){return value==null?"":value.get();} @Override public boolean commit(){return true;} @Override public void blur(){} @Override public void discardDraft(){}
}

