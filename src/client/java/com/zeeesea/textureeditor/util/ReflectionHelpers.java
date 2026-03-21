package com.zeeesea.textureeditor.util;

import java.lang.reflect.Field;

public final class ReflectionHelpers {
    private ReflectionHelpers() {}

    public static Object getField(Object target, String fieldName) {
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setField(Object target, String fieldName, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return false;
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Field findField(Class<?> cls, String fieldName) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}

