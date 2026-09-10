/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.shadowbaritone.utils.accessor.IBitArray;
import net.minecraft.util.BitArray;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BitArray.class)
public abstract class MixinBitArray implements IBitArray {

    @Shadow(remap = false)
    @Final
    private long[] field_188145_a;

    @Shadow(remap = false)
    @Final
    private int field_188146_b;

    @Shadow(remap = false)
    @Final
    private long field_188147_c;

    @Shadow(remap = false)
    @Final
    private int field_188148_d;

    @Override
    @Unique
    public int[] toArray() {
        int[] out = new int[field_188148_d];

        for (int idx = 0, kl = field_188146_b - 1; idx < field_188148_d; idx++, kl += field_188146_b) {
            final int i = idx * field_188146_b;
            final int j = i >> 6;
            final int l = i & 63;
            final int k = kl >> 6;
            final long jl = field_188145_a[j] >>> l;

            if (j == k) {
                out[idx] = (int) (jl & field_188147_c);
            } else {
                out[idx] = (int) ((jl | field_188145_a[k] << (64 - l)) & field_188147_c);
            }
        }

        return out;
    }

    @Override
    public long getMaxEntryValue() {
        return field_188147_c;
    }

    @Override
    public int getBitsPerEntry() {
        return field_188146_b;
    }
}
