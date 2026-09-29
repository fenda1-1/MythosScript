package com.mythos.mythosScriptMod.mcp;

import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import java.util.*;
import java.util.concurrent.*;

/** Game-thread reads are time-sliced; the worker only receives immutable strings. */
final class McpWorldTerrain {
    private static final ExecutorService PACKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mythos terrain packer"); t.setDaemon(true); return t;
    });
    static final class Snapshot {
        final int[] origin;
        final int extent;
        final JsonObject world;
        final String[] cells;
        Snapshot(int[] origin, int extent, JsonObject world, String[] cells) {
            this.origin=origin; this.extent=extent; this.world=world; this.cells=cells;
        }
    }
    private World world;
    private int[] center;
    private int extent;
    private Scan scan;
    private Future<Snapshot> packing;
    private Snapshot ready;
    private long nextScan;

    void clear() {
        world=null; center=null; scan=null; ready=null; nextScan=0;
        // Packing never reads the world. Cancellation does not interrupt game state.
        if(packing!=null) packing.cancel(false);
        packing=null;
    }
    Snapshot request(World source, int[] desired, int size) {
        if(world!=source || extent!=size) { clear(); world=source; extent=size; }
        center=desired.clone();
        return ready;
    }
    void tick() {
        if(world==null || center==null) return;
        if(packing!=null) {
            if(!packing.isDone()) return;
            try { ready=packing.get(); }
            catch(Exception failure) { nextScan=System.nanoTime()+1_000_000_000L; }
            packing=null;
        }
        long now=System.nanoTime();
        if(scan==null) {
            if(now<nextScan && ready!=null && Arrays.equals(center,ready.origin)) return;
            scan=new Scan(world,center.clone(),extent);
        }
        if(!scan.step(now+2_000_000L,32768)) return;
        final Scan complete=scan;
        final Snapshot previous=ready;
        scan=null; nextScan=System.nanoTime()+1_000_000_000L;
        packing=PACKER.submit(() -> {
            if(previous!=null && previous.extent==complete.extent
                    && Arrays.equals(previous.origin,complete.origin)
                    && Arrays.equals(previous.cells,complete.cells)) return previous;
            int n=complete.extent;
            JsonObject packed=McpBlockVolume.pack(complete.cells,n,24,n,new int[]{-n/2,-12,-n/2});
            return new Snapshot(complete.origin,n,packed,complete.cells);
        });
    }
    // Separately testable cursor: exact coverage, bounded work, negative coordinates.
    abstract static class Cursor {
        final int[] origin;
        final int extent;
        final String[] cells;
        int cursor;
        Cursor(int[] origin,int extent) {
            this.origin=origin; this.extent=extent; cells=new String[extent*24*extent];
        }
        abstract String read(int x,int y,int z);
        final boolean step(long deadline,int maxCells) {
            int end=Math.min(cells.length,cursor+maxCells);
            while(cursor<end) {
                cells[cursor]=read(origin[0]+cursor%extent-extent/2,
                    origin[1]+cursor/(extent*extent)-12,
                    origin[2]+cursor/extent%extent-extent/2);
                cursor++;
                if((cursor&127)==0 && System.nanoTime()>=deadline) break;
            }
            return cursor==cells.length;
        }
    }
    private static final class Scan extends Cursor {
        final World source;
        final Map<Long,Chunk> chunks=new HashMap<>();
        final IdentityHashMap<IBlockState,String> states=new IdentityHashMap<>();
        final BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos();
        Scan(World source,int[] origin,int extent) { super(origin,extent); this.source=source; }
        @Override String read(int x,int y,int z) {
            if(y<0 || y>=source.getHeight()) return "__out_of_world__";
            int cx=x>>4,cz=z>>4;
            long key=((long)cx<<32)^(cz&0xffffffffL);
            if(!chunks.containsKey(key)) chunks.put(key,source.getChunkProvider().getLoadedChunk(cx,cz));
            Chunk chunk=chunks.get(key);
            if(chunk==null) return "__unloaded__";
            IBlockState block=chunk.getBlockState(position.setPos(x,y,z));
            String state=states.get(block);
            if(state==null) { state=block.toString(); states.put(block,state); }
            return state;
        }
    }
}
