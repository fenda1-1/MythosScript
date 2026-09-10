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

package com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes;

import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.TabCompleteHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

public enum EntityClassById implements IDatatypeFor<Class<? extends Entity>> {
    INSTANCE;

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Entity> get(IDatatypeContext ctx) throws CommandException {
        ResourceLocation id = new ResourceLocation(ctx.getConsumer().getString());
        Class<? extends Entity> entity = null;
        try {
            Method getClassMethod = EntityList.class.getMethod("getClass", ResourceLocation.class);
            entity = (Class<? extends Entity>) getClassMethod.invoke(null, id);
        } catch (ReflectiveOperationException ignored) {
            // Fallback to reflected REGISTRY access for environments where getClass is
            // absent
            try {
                Field registryField = EntityList.class.getDeclaredField("REGISTRY");
                registryField.setAccessible(true);
                Object registry = registryField.get(null);

                Method getObjectMethod = registry.getClass().getMethod("getObject", Object.class);
                entity = (Class<? extends Entity>) getObjectMethod.invoke(registry, id);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(
                        "Failed to resolve entity class via EntityList.getClass(ResourceLocation) and reflected REGISTRY fallback",
                        ex);
            }
        }

        if (entity == null) {
            throw new IllegalArgumentException("no entity found by that id");
        }
        return entity;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                .append(EntityList.getEntityNameList().stream().map(Object::toString))
                .filterPrefixNamespaced(ctx.getConsumer().getString())
                .sortAlphabetically()
                .stream();
    }
}
