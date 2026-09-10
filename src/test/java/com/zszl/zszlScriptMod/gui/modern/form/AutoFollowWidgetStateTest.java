package com.zszl.zszlScriptMod.gui.modern.form;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import net.minecraft.client.gui.FontRenderer;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoFollowWidgetStateTest {
    @Test public void collectionChangesParticipateInSaveAndRevert() {
        final String[] model = {"1,64,2"};
        ModernFormWidget widget = new ModernFormWidget() {
            public int height(int width,int available) { return 180; }
            public void draw(FontRenderer font, ModernMainLayout.Rect bounds,int x,int y) { }
            public boolean mouseClicked(int x,int y,int button) { return true; }
            public Object snapshot() { return model[0]; }
        };
        ModernFormSettingsTab.StateAdapter<String> adapter = new ModernFormSettingsTab.StateAdapter<String>() {
            public void load() { }
            public String capture() { return model[0]; }
            public void restore(String value) { model[0]=value; }
        };
        ModernFormSession<String> session = new ModernFormSession<>(
                new ModernFormBuilder<>("", "", "", adapter).section("", "").custom(widget).build());
        session.load(); assertFalse(session.isDirty());
        model[0]="1,64,2;3,70,4"; assertTrue(session.isDirty());
        session.save(); assertFalse(session.isDirty());
        model[0]="3,70,4;1,64,2"; assertTrue(session.isDirty());
        session.revert(); assertEquals("1,64,2;3,70,4",model[0]); assertFalse(session.isDirty());
    }
}
