package com.zszl.zszlScriptMod.utils;

import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ReflectionCompat {

    private static final Method LEGACY_GET_PRIVATE_VALUE = findLegacyMethod(
            "getPrivateValue", Class.class, Object.class, String.class);
    private static final Method LEGACY_SET_PRIVATE_VALUE = findLegacyMethod(
            "setPrivateValue", Class.class, Object.class, Object.class, String.class);

    private ReflectionCompat() {
    }

    public static <T, E> T getPrivateValue(Class<? super E> clazz, E instance, String... fieldNames) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(clazz, instance, fieldNames);
        } catch (Throwable primaryError) {
            return getPrivateValueLegacy(clazz, instance, primaryError, fieldNames);
        }
    }

    public static <T, E> void setPrivateValue(Class<? super T> clazz, T instance, E value, String... fieldNames) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(clazz, instance, value, fieldNames);
        } catch (Throwable primaryError) {
            setPrivateValueLegacy(clazz, instance, value, primaryError, fieldNames);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E> T getPrivateValueLegacy(Class<? super E> clazz, E instance, Throwable primaryError,
            String... fieldNames) {
        if (LEGACY_GET_PRIVATE_VALUE == null) {
            throw propagate(primaryError);
        }

        Throwable lastError = primaryError;
        if (fieldNames != null) {
            for (String fieldName : fieldNames) {
                if (fieldName == null || fieldName.isEmpty()) {
                    continue;
                }
                try {
                    return (T) LEGACY_GET_PRIVATE_VALUE.invoke(null, clazz, instance, fieldName);
                } catch (InvocationTargetException invocationError) {
                    lastError = invocationError.getCause() != null ? invocationError.getCause() : invocationError;
                } catch (Throwable legacyError) {
                    lastError = legacyError;
                }
            }
        }

        throw propagate(lastError);
    }

    private static <T, E> void setPrivateValueLegacy(Class<? super T> clazz, T instance, E value,
            Throwable primaryError, String... fieldNames) {
        if (LEGACY_SET_PRIVATE_VALUE == null) {
            throw propagate(primaryError);
        }

        Throwable lastError = primaryError;
        if (fieldNames != null) {
            for (String fieldName : fieldNames) {
                if (fieldName == null || fieldName.isEmpty()) {
                    continue;
                }
                try {
                    LEGACY_SET_PRIVATE_VALUE.invoke(null, clazz, instance, value, fieldName);
                    return;
                } catch (InvocationTargetException invocationError) {
                    lastError = invocationError.getCause() != null ? invocationError.getCause() : invocationError;
                } catch (Throwable legacyError) {
                    lastError = legacyError;
                }
            }
        }

        throw propagate(lastError);
    }

    private static Method findLegacyMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = ObfuscationReflectionHelper.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException) {
            return (RuntimeException) error;
        }
        return new RuntimeException(error);
    }
}
