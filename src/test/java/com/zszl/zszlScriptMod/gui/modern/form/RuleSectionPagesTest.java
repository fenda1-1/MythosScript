package com.zszl.zszlScriptMod.gui.modern.form;

import com.zszl.zszlScriptMod.gui.modern.ModernFormSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import net.minecraft.client.gui.FontRenderer;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class RuleSectionPagesTest {
    private ModernFormDefinition<String> definition(ModernFormWidget first, ModernFormWidget second) {
        return new ModernFormBuilder<String>("", "", "", new ModernFormSettingsTab.StateAdapter<String>() {
            public void load() { }
            public String capture() { return ""; }
            public void restore(String value) { }
        }).section("first", "").custom(first).section("second", "").custom(second).build();
    }
    private ModernFormWidget widget() {
        return new ModernFormWidget() {
            public int height(int width, int available) { return 180; }
            public void draw(FontRenderer font, ModernMainLayout.Rect bounds, int x, int y) { }
            public boolean mouseClicked(int x, int y, int button) { return true; }
        };
    }
    @Test public void eachSectionAndRebuiltFormRetainTheirOwnScroll() throws Exception {
        RuleSectionState state = new RuleSectionState();
        ModernFormDefinition<String> definition = definition(widget(), widget());
        ModernFormRenderer renderer = new ModernFormRenderer(definition);
        renderer.setSectionPages(state);
        Field maximum = ModernFormRenderer.class.getDeclaredField("maxScrollOffset");
        maximum.setAccessible(true); maximum.setInt(renderer, 1000);
        renderer.scrollBy(275);
        renderer.selectSection(1);
        assertEquals(0, renderer.getScrollOffset());
        renderer.scrollBy(90);
        renderer.selectSection(0);
        assertEquals(275, renderer.getScrollOffset());
        renderer.selectSection(1);
        assertEquals(90, renderer.getScrollOffset());
        ModernFormRenderer rebuilt = new ModernFormRenderer(definition);
        rebuilt.setSectionPages(state);
        assertEquals(90, rebuilt.getScrollOffset());
        assertEquals(1, state.selected());
    }
    @Test public void hiddenWidgetsStayInSaveModelButNotKeyboardOrDragRouting() {
        ModernFormWidget first = widget(), second = widget();
        ModernFormRenderer renderer = new ModernFormRenderer(definition(first, second));
        renderer.setSectionPages(new RuleSectionState());
        assertEquals(2, renderer.widgets().size());
        assertEquals(java.util.Collections.singletonList(first), renderer.activeWidgets());
        renderer.selectSection(1);
        assertEquals(java.util.Collections.singletonList(second), renderer.activeWidgets());
        assertEquals(2, renderer.widgets().size());
    }
    @Test public void sectionSwitchImmediatelyClearsHiddenHitTargets() {
        ModernFormDefinition<String> definition = definition(widget(), widget());
        ModernFormRenderer renderer = new ModernFormRenderer(definition);
        renderer.setSectionPages(new RuleSectionState());
        ModernFormDefinition.Item first = definition.getSections().get(0).getItems().get(0);
        renderer.view(first).rowBounds = new ModernMainLayout.Rect(0, 0, 100, 100);
        renderer.selectSection(1);
        assertNull(renderer.view(first).rowBounds);
        renderer.selectSection(50);
        assertEquals(1, renderer.activeWidgets().size());
    }
    @Test public void differentRulesDoNotShareSectionOrScroll() {
        RuleSectionState first = new RuleSectionState(), second = new RuleSectionState();
        first.select(3); first.remember(120);
        assertEquals(0, second.selected());
        assertEquals(0, second.scroll());
        first.remember(-9);
        assertEquals(0, first.scroll());
    }
}
