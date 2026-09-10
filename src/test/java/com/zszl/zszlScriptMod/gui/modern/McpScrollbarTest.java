package com.zszl.zszlScriptMod.gui.modern;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class McpScrollbarTest {
    @Test public void verticalTrackClickDragAndReleaseReachBothEnds() {
        ModernHoverScrollbar bar = new ModernHoverScrollbar();
        AtomicInteger position = new AtomicInteger();
        bar.layout(200, 30, 200, 0, 980, 20, 1000, position::set);
        assertFalse(bar.beginDrag(180, 40));
        assertTrue(bar.beginDrag(195, 120));
        assertTrue(position.get() > 0);
        assertTrue(bar.applyDrag(195, 1000));
        assertEquals(980, position.get());
        bar.applyDrag(195, -100);
        assertEquals(0, position.get());
        bar.endDrag();
        assertFalse(bar.applyDrag(195, 120));
    }

    @Test public void horizontalScrollUsesPixelExtentsAndClearsWhenContentFits() {
        ModernHoverScrollbar bar = new ModernHoverScrollbar(ModernHoverScrollbar.Axis.HORIZONTAL);
        AtomicInteger position = new AtomicInteger();
        bar.layout(300, 40, 300, 0, 1700, 300, 2000, position::set);
        assertTrue(bar.beginDrag(45, 295));
        bar.applyDrag(1000, 295);
        assertEquals(1700, position.get());
        bar.layout(300, 40, 300, 1700, 0, 300, 200, position::set);
        assertFalse(bar.isDragging());
        assertFalse(bar.contains(45, 295));
        assertEquals(0, bar.getScroll());
    }
}
