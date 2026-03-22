package com.zeeesea.textureeditor.util;

/**
 * Compatibility helper to obtain the GL texture id from AbstractTexture instances
 * across different mappings where the accessor may have changed.
 */
public final class TextureCompat {
    private TextureCompat() {}

    public static int getGlId(Object abstractTexture) {
        if (abstractTexture == null) return -1;
        try {
            // Try method getGlId()
            try {
                java.lang.reflect.Method m = abstractTexture.getClass().getMethod("getGlId");
                Object r = m.invoke(abstractTexture);
                if (r instanceof Number) return ((Number) r).intValue();
            } catch (NoSuchMethodException ignored) {}

            // Fallback: search for a field named glId up the class hierarchy
            Class<?> c = abstractTexture.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("glId");
                    f.setAccessible(true);
                    Object v = f.get(abstractTexture);
                    if (v instanceof Number) return ((Number) v).intValue();
                    break;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }
}

