package com.zeeesea.textureeditor.texture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Loads texture pixels directly from resource files (resource packs/vanilla),
 * independent from live atlas edits.
 */
public final class TextureResourceLoader {
    private TextureResourceLoader() {}

    public record LoadedTexture(Identifier textureId, int[][] pixels, int width, int height) {}

    public static LoadedTexture loadTexture(Identifier textureId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;

        try {
            var opt = client.getResourceManager().getResource(textureId);
            if (opt.isEmpty()) return null;

            try (InputStream stream = opt.get().getInputStream(); NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                int[][] pixels = new int[width][height];
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        pixels[x][y] = image.getColorArgb(x, y);
                    }
                }
                return new LoadedTexture(textureId, pixels, width, height);
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load default texture " + textureId + ": " + e.getMessage());
            return null;
        }
    }
}

