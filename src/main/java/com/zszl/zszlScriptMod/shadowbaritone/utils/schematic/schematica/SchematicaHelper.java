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
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.util.Optional;

public enum SchematicaHelper {
    ;

    private static final String SCHEMATICA_MAIN_CLASS = "com.github.lunatrius.schematica.Schematica";
    private static final String SCHEMATICA_CLIENT_PROXY_CLASS = "com.github.lunatrius.schematica.proxy.ClientProxy";

    public static boolean isSchematicaPresent() {
        try {
            Class.forName(SCHEMATICA_MAIN_CLASS);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            return false;
        }
    }

    public static Optional<Tuple<IStaticSchematic, BlockPos>> getOpenSchematic() {
        Object schematic = getClientProxySchematic();
        if (schematic == null) {
            return Optional.empty();
        }

        BlockPos pos = readBlockPosField(schematic, "position");
        if (pos == null) {
            pos = BlockPos.ORIGIN;
        }
        return Optional.of(new Tuple<>(new SchematicAdapter(schematic), pos));
    }

    private static Object getClientProxySchematic() {
        try {
            Class<?> proxy = Class.forName(SCHEMATICA_CLIENT_PROXY_CLASS);
            Field schematicField = proxy.getField("schematic");
            return schematicField.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BlockPos readBlockPosField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object value = field.get(target);
            return value instanceof BlockPos ? (BlockPos) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

}
