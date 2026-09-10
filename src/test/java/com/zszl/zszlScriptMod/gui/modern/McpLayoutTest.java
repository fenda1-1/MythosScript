package com.zszl.zszlScriptMod.gui.modern;

import org.junit.Test;
import static org.junit.Assert.*;

public class McpLayoutTest {
    @Test public void panesStayInsideOffsetWindowsAtDifferentScales() {
        for (int width : new int[] {320, 400, 480, 640, 800, 1200}) {
            ModernMainLayout.Rect area = new ModernMainLayout.Rect(93, 57, width, 460);
            ModernMcpLayout layout = new ModernMcpLayout(area);
            for (ModernMainLayout.Rect rect : new ModernMainLayout.Rect[] {
                    layout.header, layout.controls, layout.feeds, layout.sidebar, layout.inspector}) {
                assertTrue("left " + width, rect.x >= area.x);
                assertTrue("top " + width, rect.y >= area.y);
                assertTrue("right " + width, rect.right() <= area.right());
                assertTrue("bottom " + width, rect.bottom() <= area.bottom());
                assertTrue(rect.width > 0 && rect.height > 0);
            }
            assertTrue(layout.feeds.y >= layout.header.bottom());
            assertTrue(layout.sidebar.y >= layout.feeds.bottom());
            if (layout.stacked) assertTrue(layout.inspector.y >= layout.sidebar.bottom());
            else {
                assertTrue(layout.inspector.x >= layout.sidebar.right());
                assertEquals(layout.sidebar.y, layout.inspector.y);
                assertEquals(layout.sidebar.bottom(), layout.inspector.bottom());
            }
        }
    }

    @Test public void wideWindowPrioritizesInspectorAndKeepsConnectionCompact() {
        ModernMcpLayout layout = new ModernMcpLayout(new ModernMainLayout.Rect(0, 0, 900, 420));
        assertTrue(layout.inlineControls);
        assertFalse(layout.stacked);
        assertTrue(layout.inspector.width > layout.sidebar.width * 2);
        assertTrue(layout.inspector.height > 290);
    }

    @Test public void wideWindowUsesRequestedResizableSplit() {
        ModernMainLayout.Rect area = new ModernMainLayout.Rect(12, 24, 900, 420);
        ModernMcpLayout compactRecords = new ModernMcpLayout(area, 0.20D);
        ModernMcpLayout wideRecords = new ModernMcpLayout(area, 0.50D);

        assertTrue(wideRecords.sidebar.width > compactRecords.sidebar.width);
        assertEquals(compactRecords.sidebar.right() + ModernMcpLayout.SPLIT_GAP,
                compactRecords.inspector.x);
        assertNotNull(compactRecords.divider);
        assertTrue(compactRecords.divider.contains(compactRecords.sidebar.right()
                + ModernMcpLayout.SPLIT_GAP / 2, compactRecords.sidebar.y + 20));
    }
}
