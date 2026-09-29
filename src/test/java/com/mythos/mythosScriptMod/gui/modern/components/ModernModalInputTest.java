package com.mythos.mythosScriptMod.gui.modern.components;

import com.mythos.mythosScriptMod.gui.modern.ModernMainLayout;
import net.minecraft.client.gui.FontRenderer;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModernModalInputTest {
    @Test public void modalBlocksFocusedAndCapturedBackgroundButRoutesToItsBody() {
        ModernComponentHost host = new ModernComponentHost(null);
        Probe background = host.add(new Probe());
        host.mouseClicked(10, 10, 0);
        host.mouseClickMove(10, 10, 0, 1);
        assertTrue(background.focused);
        background.reset();

        ModernModal modal = host.add(new ModernModal());
        modal.layout(new ModernMainLayout.Rect(0, 0, 200, 200));
        Probe body = modal.body().add(new Probe());
        modal.open();

        assertTrue(host.keyTyped('x', 45));
        assertTrue(host.mouseClickMove(10, 10, 0, 1));
        assertTrue(host.mouseReleased(10, 10, 0));
        assertTrue(host.handleMouseWheel(120, 10, 10));
        assertTrue(host.mouseClicked(10, 10, 0));
        assertEquals(0, background.events);
        assertEquals(5, body.events);
        assertFalse(background.focused);
    }

    @Test public void emptyModalConsumesEveryInputIncludingOutsideDismissal() {
        ModernComponentHost host = new ModernComponentHost(null);
        Probe background = host.add(new Probe());
        ModernModal modal = host.add(new ModernModal());
        modal.layout(new ModernMainLayout.Rect(0, 0, 100, 100));
        modal.open();
        assertTrue(host.keyTyped('x', 45));
        assertTrue(host.handleMouseWheel(-120, 200, 200));
        assertTrue(host.mouseClickMove(200, 200, 1, 1));
        assertTrue(host.mouseReleased(200, 200, 1));
        assertTrue(host.mouseClicked(200, 200, 0));
        assertFalse(modal.isOpen());
        assertEquals(0, background.events);
        assertTrue(host.mouseClicked(10, 10, 0));
        assertEquals(1, background.events);
    }

    @Test public void onlyTopModalReceivesInputAndClosingRestoresPreviousModal() {
        ModernComponentHost host = new ModernComponentHost(null);
        ModernModal first = host.add(new ModernModal().open());
        Probe firstBody = first.body().add(new Probe());
        ModernModal second = host.add(new ModernModal().open());
        Probe secondBody = second.body().add(new Probe());
        assertTrue(host.keyTyped('x', 45));
        assertEquals(0, firstBody.events);
        assertEquals(1, secondBody.events);
        second.close();
        assertTrue(host.keyTyped('x', 45));
        assertEquals(1, firstBody.events);
        assertEquals(1, secondBody.events);
    }

    private static final class Probe implements ModernComponent {
        int events;
        boolean focused;
        void reset() { events = 0; }
        @Override public void layout(ModernMainLayout.Rect bounds) { }
        @Override public ModernMainLayout.Rect bounds() { return null; }
        @Override public void draw(FontRenderer font, int x, int y) { }
        @Override public boolean click(int x, int y, int button) { events++; return true; }
        @Override public boolean mouseClickMove(int x, int y, int button, long elapsed) { events++; return true; }
        @Override public boolean mouseReleased(int x, int y, int button) { events++; return true; }
        @Override public boolean keyTyped(char c, int key) { events++; return true; }
        @Override public boolean handleMouseWheel(int wheel, int x, int y) { events++; return true; }
        @Override public boolean isFocusable() { return true; }
        @Override public boolean isFocused() { return focused; }
        @Override public void setFocused(boolean value) { focused = value; }
    }
}
