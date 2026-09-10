package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import java.util.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Lossless greedy cuboids, inclusive corners, world-axis relative coordinates. */
public final class McpBlockVolume {
    private McpBlockVolume() {}
    public static int[] origin(double x,double y,double z) {
        return new int[]{(int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)};
    }
    public static JsonObject pack(String[] cells, int sx, int sy, int sz, int[] min) {
        if (sx<1 || sy<1 || sz<1 || (long)sx*sy*sz != cells.length) throw new IllegalArgumentException("Invalid volume");
        boolean[] seen = new boolean[cells.length];
        Map<String,Integer> palette = new LinkedHashMap<>();
        JsonArray boxes = new JsonArray();
        for (int y=0;y<sy;y++) for (int z=0;z<sz;z++) for (int x=0;x<sx;x++) {
            int at = (y*sz+z)*sx+x;
            if (seen[at]) continue;
            String state = cells[at];
            int ex=x;
            while (ex+1<sx && matches(cells,seen,sx,sz,ex+1,y,z,state)) ex++;
            int ez=z;
            outer: while (ez+1<sz) {
                for (int xx=x;xx<=ex;xx++) if (!matches(cells,seen,sx,sz,xx,y,ez+1,state)) break outer;
                ez++;
            }
            int ey=y;
            outer: while (ey+1<sy) {
                for (int zz=z;zz<=ez;zz++) for (int xx=x;xx<=ex;xx++)
                    if (!matches(cells,seen,sx,sz,xx,ey+1,zz,state)) break outer;
                ey++;
            }
            for (int yy=y;yy<=ey;yy++) for (int zz=z;zz<=ez;zz++) for (int xx=x;xx<=ex;xx++) seen[(yy*sz+zz)*sx+xx]=true;
            if (!palette.containsKey(state)) palette.put(state,palette.size());
            boxes.add(object("min",new int[]{x+min[0],y+min[1],z+min[2]},"max",new int[]{ex+min[0],ey+min[1],ez+min[2]},"state",palette.get(state)));
        }
        return object("encoding","palette_cuboids_inclusive","palette",palette.keySet(),"cuboids",boxes,"blockCount",cells.length);
    }
    private static boolean matches(String[] c, boolean[] s, int sx,int sz,int x,int y,int z,String state) {
        int i=(y*sz+z)*sx+x; return !s[i] && Objects.equals(c[i],state);
    }
}
