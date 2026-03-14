package com.zeeesea.textureeditor.util;

import net.minecraft.client.texture.NativeImage;

/**
 * 1.21 uses NativeImage colors in ABGR order. The editor stores colors as ARGB.
 */
public final class ImageColorCompat {
    private ImageColorCompat() {
    }

    public static int readArgb(NativeImage image, int x, int y) {
        return swapRedBlue(image.getColor(x, y));
    }

    public static void writeArgb(NativeImage image, int x, int y, int argb) {
        image.setColor(x, y, swapRedBlue(argb));
    }

    private static int swapRedBlue(int color) {
        return (color & 0xFF00FF00)
                | ((color & 0x00FF0000) >>> 16)
                | ((color & 0x000000FF) << 16);
    }
}

