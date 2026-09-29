package com.mythos.mythosScriptMod.shadowbaritone.cache;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.Collections;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class CachedChunkTest {
    @BeforeClass public static void registerBlocks() {
        Bootstrap.register();
    }

    @Test public void sparseCacheRetainsAllBitsWithoutUnusedUpperAirStorage() throws Exception {
        BitSet data = new BitSet(CachedChunk.SIZE);
        data.set(0, 4 * 16 * 16 * 2);
        CachedChunk chunk = new CachedChunk(0, 0, data, new IBlockState[256], Collections.emptyMap(), 123);
        assertArrayEquals(data.toByteArray(), chunk.toByteArray());
        Field field = CachedChunk.class.getDeclaredField("data");
        field.setAccessible(true);
        BitSet retained = (BitSet) field.get(chunk);
        assertEquals(4 * 16 * 16 * 2, retained.size());
        data.clear();
        assertTrue(retained.get(0));
        assertEquals(Blocks.AIR.getDefaultState(), chunk.getBlock(15, 255, 15, 0));
    }

    @Test public void surfaceHeightsAboveSignedByteRangeAndFullHeightRoundTrip() {
        for (int height : new int[]{0, 127, 128, 255}) {
            BitSet data = new BitSet(CachedChunk.SIZE);
            int offset = CachedChunk.getPositionIndex(15, height, 15);
            data.set(offset, offset + 2);
            IBlockState[] surface = new IBlockState[256];
            surface[255] = Blocks.GOLD_BLOCK.getDefaultState();
            CachedChunk chunk = new CachedChunk(0, 0, data, surface, Collections.emptyMap(), 123);
            assertEquals(surface[255], chunk.getBlock(15, height, 15, 0));
            assertArrayEquals(data.toByteArray(), chunk.toByteArray());
            CachedChunk restored = new CachedChunk(0, 0, BitSet.valueOf(chunk.toByteArray()),
                    surface, Collections.emptyMap(), 123);
            assertEquals(surface[255], restored.getBlock(15, height, 15, 0));
        }
    }
}
