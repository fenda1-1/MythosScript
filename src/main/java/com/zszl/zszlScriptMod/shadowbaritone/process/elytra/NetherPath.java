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

package com.zszl.zszlScriptMod.shadowbaritone.process.elytra;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NetherPath extends AbstractList<BetterBlockPos> {

    private static final NetherPath EMPTY = new NetherPath(Collections.emptyList());

    private final List<BetterBlockPos> path;

    public NetherPath(List<BetterBlockPos> path) {
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
    }

    public static NetherPath emptyPath() {
        return EMPTY;
    }

    @Override
    public int size() {
        return this.path.size();
    }

    public boolean isEmpty() {
        return this.path.isEmpty();
    }

    @Override
    public BetterBlockPos get(int index) {
        return this.path.get(index);
    }

    public BetterBlockPos getLast() {
        return this.path.get(this.path.size() - 1);
    }

    public List<BetterBlockPos> subList(int fromIndex, int toIndex) {
        return this.path.subList(fromIndex, toIndex);
    }

    public Vec3d getVec(int index) {
        BetterBlockPos pos = this.path.get(index);
        return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    }
}
