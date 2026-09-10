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

package com.zszl.zszlScriptMod.shadowbaritone.utils.schematic.schematica;

import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.IStaticSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.List;

public final class SchematicAdapter implements IStaticSchematic {

    private final Object schematic;

    public SchematicAdapter(Object schematicWorld) {
        this.schematic = schematicWorld;
    }

    @Override
    public IBlockState desiredState(int x, int y, int z, IBlockState current, List<IBlockState> approxPlaceable) {
        return this.getDirect(x, y, z);
    }

    @Override
    public IBlockState getDirect(int x, int y, int z) {
        Object schematicObj = invoke(schematic, "getSchematic");
        Object blockState = invoke(schematicObj, "getBlockState", new Class[] { BlockPos.class },
                new Object[] { new BlockPos(x, y, z) });
        return blockState instanceof IBlockState ? (IBlockState) blockState : null;
    }

    @Override
    public int widthX() {
        Object schematicObj = invoke(schematic, "getSchematic");
        Object width = invoke(schematicObj, "getWidth");
        return width instanceof Number ? ((Number) width).intValue() : 0;
    }

    @Override
    public int heightY() {
        Object schematicObj = invoke(schematic, "getSchematic");
        Object height = invoke(schematicObj, "getHeight");
        return height instanceof Number ? ((Number) height).intValue() : 0;
    }

    @Override
    public int lengthZ() {
        Object schematicObj = invoke(schematic, "getSchematic");
        Object length = invoke(schematicObj, "getLength");
        return length instanceof Number ? ((Number) length).intValue() : 0;
    }

    private static Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
