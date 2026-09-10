package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.*;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import org.lwjgl.input.Keyboard;
import com.zszl.zszlScriptMod.gui.modern.*;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormWidget;

/** A compact two-column layout for related numeric settings, sharing the normal draft validation. */
final class AutoFollowNumericPanel implements ModernFormWidget {
    private final String[] labels;
    private final ModernFormSettingsTab.TextValue[] values;
    private final ModernTextField[] fields;
    private final List<Rect> hits = new ArrayList<>();
    AutoFollowNumericPanel(String[] labels, ModernFormSettingsTab.TextValue... values) {
        this.labels=labels; this.values=values; fields=new ModernTextField[values.length];
    }
    private int columns(int width) { return width>=280?2:1; }
    @Override public int height(int width,int available) { return ((values.length+columns(width)-1)/columns(width))*44+6; }
    @Override public void draw(FontRenderer font,Rect b,int mx,int my) {
        hits.clear(); int cols=columns(b.width), cell=b.width/cols;
        for(int i=0;i<values.length;i++) {
            if(fields[i]==null) { fields[i]=new ModernTextField(i,font,0,0,1,18); fields[i].setEnableBackgroundDrawing(false); fields[i].setMaxStringLength(32); }
            ModernTextField f=fields[i]; if(!f.isFocused()) f.setText(values[i].get());
            int x=b.x+(i%cols)*cell+6, y=b.y+(i/cols)*44+4;
            ModernUiRenderer.drawText(font,labels[i],x,y,ModernUiRenderer.SUBTLE_TEXT,cell-12);
            Rect r=new Rect(x,y+13,cell-12,23); hits.add(r);
            ModernUiRenderer.drawSubtlePanel(r.x,r.y,r.width,r.height,4,ModernUiRenderer.SHELL_RAISED,f.isFocused()?ModernUiRenderer.ACCENT:ModernUiRenderer.BORDER);
            f.layout(r);
            f.setVisible(true);
            f.setEnabled(true);
            f.drawTextContents(font);
        }
    }
    @Override public boolean mouseClicked(int x,int y,int button) {
        if(button==0) for(int i=0;i<fields.length;i++) if(fields[i]!=null) { fields[i].setFocused(hits.get(i).contains(x,y)); if(fields[i].isFocused()) fields[i].mouseClicked(x,y,button); }
        return true;
    }
    @Override public boolean keyTyped(char c,int key) {
        for(int i=0;i<fields.length;i++) if(fields[i]!=null&&fields[i].isFocused()) {
            if(key==Keyboard.KEY_TAB) { fields[i].setFocused(false); fields[(i+1)%fields.length].setFocused(true); return true; }
            if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER) { fields[i].setFocused(false); return true; }
            boolean handled=fields[i].textboxKeyTyped(c,key); if(handled) values[i].set(fields[i].getText()); return handled;
        }
        return false;
    }
    @Override public boolean isTextInputFocused() { for(ModernTextField f:fields) if(f!=null&&f.isFocused()) return true; return false; }
    @Override public void blur() { for(ModernTextField f:fields) if(f!=null) f.setFocused(false); }
    @Override public boolean handleEscape() { boolean focused=isTextInputFocused(); blur(); return focused; }
    @Override public Object snapshot() { List<String> result=new ArrayList<>(); for(ModernFormSettingsTab.TextValue value:values) result.add(value.get()); return result; }
}
