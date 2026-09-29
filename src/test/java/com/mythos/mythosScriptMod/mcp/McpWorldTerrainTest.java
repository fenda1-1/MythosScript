package com.mythos.mythosScriptMod.mcp;
import org.junit.Test;
import static org.junit.Assert.*;

public class McpWorldTerrainTest {
    @Test public void scanYieldsAndCoversEightChunksWithoutGaps() {
        McpWorldTerrain.Cursor scan=new McpWorldTerrain.Cursor(new int[]{-3,64,-5},128) {
            @Override String read(int x,int y,int z) { return x+","+y+","+z; }
        };
        assertFalse(scan.step(Long.MAX_VALUE,13));
        assertEquals(13,scan.cursor);
        assertFalse(scan.step(0,32768));
        assertEquals(128,scan.cursor);
        int steps=0;
        while(!scan.step(Long.MAX_VALUE,32768)) { assertTrue(++steps<20); }
        assertEquals(128*24*128,scan.cursor);
        assertEquals("-67,52,-69",scan.cells[0]);
        assertEquals("60,75,58",scan.cells[scan.cells.length-1]);
        for(int i=0;i<scan.cells.length;i++) {
            assertEquals((-67+i%128)+","+(52+i/(128*128))+","+(-69+i/128%128),scan.cells[i]);
        }
    }
}
