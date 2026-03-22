package com.zeeesea.textureeditor.util;

/**
 * Compatibility helper for NativeImage upload differences between mappings.
 * Tries to call NativeImage.upload(...) via reflection and falls back to
 * using GlStateManager._texSubImage2D with the native image pointer.
 */
public final class NativeImageCompat {
    private NativeImageCompat() {}

    public static void upload(net.minecraft.client.texture.NativeImage img, int level, int x, int y, boolean unused) {
        if (img == null) return;
        try {
            java.lang.reflect.Method m = img.getClass().getMethod("upload", int.class, int.class, int.class, boolean.class);
            m.invoke(img, level, x, y, unused);
            return;
        } catch (Throwable ignored) {}

        // Fallback: use low-level tex sub-image call
        try {
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            int w = img.getWidth();
            int h = img.getHeight();
            com.mojang.blaze3d.opengl.GlStateManager._texSubImage2D(
                    com.mojang.blaze3d.opengl.GlConst.GL_TEXTURE_2D,
                    level, x, y, w, h,
                    com.mojang.blaze3d.opengl.GlConst.GL_RGBA,
                    com.mojang.blaze3d.opengl.GlConst.GL_UNSIGNED_BYTE,
                    img.imageId()
            );
        } catch (Throwable t) {
            // last resort: ignore
        }
    }
}

