package com.zszl.zszlScriptMod.mcp;
import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class McpBlockVolumeTest {
    @Test public void originUsesFloorIncludingNegativeFractionalCoordinates() {
        assertArrayEquals(new int[]{0,-1,-2},McpBlockVolume.origin(.5,-.5,-1.1));
        assertArrayEquals(new int[]{-5,4,0},McpBlockVolume.origin(-5,4,0));
    }
    @Test public void uniformTenCubeHasOneInclusiveBox() {
        String[] c=new String[1000];Arrays.fill(c,"minecraft:air");
        JsonObject packed=McpBlockVolume.pack(c,10,10,10,new int[]{-5,-5,-5});
        assertEquals(1,packed.getAsJsonArray("cuboids").size());
        assertEquals("[-5,-5,-5]",packed.getAsJsonArray("cuboids").get(0).getAsJsonObject().get("min").toString());
        assertEquals("[4,4,4]",packed.getAsJsonArray("cuboids").get(0).getAsJsonObject().get("max").toString());
    }
    @Test public void arbitraryVolumeRoundTripsWithNoGapsOrOverlaps() {
        Random r=new Random(420);
        for(int trial=0;trial<30;trial++) {
            int sx=1+r.nextInt(9),sy=1+r.nextInt(9),sz=1+r.nextInt(9);String[] c=new String[sx*sy*sz];
            for(int i=0;i<c.length;i++)c[i]=Arrays.asList("air","stone[axis=x]","__unloaded__","water[level=3]").get(r.nextInt(4));
            int[] offset={-7,-2,3};JsonObject p=McpBlockVolume.pack(c,sx,sy,sz,offset);String[] restored=new String[c.length];
            for(JsonElement e:p.getAsJsonArray("cuboids")) {
                JsonObject box=e.getAsJsonObject();JsonArray lo=box.getAsJsonArray("min"),hi=box.getAsJsonArray("max");
                String state=p.getAsJsonArray("palette").get(box.get("state").getAsInt()).getAsString();
                for(int y=lo.get(1).getAsInt();y<=hi.get(1).getAsInt();y++)for(int z=lo.get(2).getAsInt();z<=hi.get(2).getAsInt();z++)for(int x=lo.get(0).getAsInt();x<=hi.get(0).getAsInt();x++) {
                    int i=((y-offset[1])*sz+z-offset[2])*sx+x-offset[0];assertNull(restored[i]);restored[i]=state;
                }
            }
            assertArrayEquals(c,restored);
        }
    }
}
