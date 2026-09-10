package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.*;
import com.zszl.zszlScriptMod.gui.modern.form.*;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;

final class AutoFollowTargetPanel implements ModernFormWidget {
    private final String[][] options;
    private final Predicate<String> selected;
    private final BiConsumer<String,Boolean> set;
    private final List<Rect> hits = new ArrayList<>();
    AutoFollowTargetPanel(String[][] options, Predicate<String> selected, BiConsumer<String,Boolean> set) {
        this.options=options; this.selected=selected; this.set=set;
    }
    private int columns(int width) { return Math.max(1, (width-12)/94); }
    @Override public int height(int width,int available) { return 28+((options.length+columns(width)-1)/columns(width))*27; }
    @Override public void draw(FontRenderer font,Rect b,int mx,int my) {
        hits.clear();
        ModernUiRenderer.drawSubtlePanel(b.x,b.y,b.width,b.height,5,ModernUiRenderer.SHELL,ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font,"目标类型 · 可多选",b.x+8,b.y+8,ModernUiRenderer.TEXT,b.width-16);
        int columns=columns(b.width), w=(b.width-12)/columns;
        for(int i=0;i<options.length;i++) {
            Rect r=new Rect(b.x+6+i%columns*w,b.y+25+i/columns*27,w-4,23); hits.add(r);
            boolean active=selected.test(options[i][0]);
            ModernUiRenderer.drawSubtlePanel(r.x,r.y,r.width,r.height,4,
                    active?ModernUiRenderer.ACCENT_DIM:r.contains(mx,my)?ModernUiRenderer.SURFACE_HOVER:ModernUiRenderer.SURFACE,
                    active?ModernUiRenderer.ACCENT:ModernUiRenderer.BORDER_SUBTLE);
            if (active) ModernUiRenderer.drawCheckMark(r.x + 7, r.y + 5, 12, ModernUiRenderer.TEXT);
            ModernUiRenderer.drawText(font, ModernFormI18n.tr(options[i][1]), r.x + (active ? 23 : 7), r.y + 7,
                    ModernUiRenderer.TEXT, r.width - (active ? 28 : 12));
        }
    }
    @Override public Object snapshot() {
        List<String> result = new ArrayList<>();
        for (String[] option : options) if (selected.test(option[0])) result.add(option[0]);
        return result;
    }
    @Override public boolean mouseClicked(int x,int y,int button) {
        if(button==0) for(int i=0;i<hits.size();i++) if(hits.get(i).contains(x,y)) { String key=options[i][0]; set.accept(key,!selected.test(key)); break; }
        return true;
    }
}
