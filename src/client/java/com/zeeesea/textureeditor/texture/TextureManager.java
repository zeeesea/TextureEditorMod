package com.zeeesea.textureeditor.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages modified textures and applies them live by uploading to the block atlas.
 */
public class TextureManager {
    private static final TextureManager INSTANCE = new TextureManager();

    // Maps texture identifier -> modified pixel data (ARGB int[][])
    private final Map<Identifier, int[][]> modifiedTextures = new HashMap<>();
    // Maps texture identifier -> dimensions
    private final Map<Identifier, int[]> textureDimensions = new HashMap<>();

    private TextureManager() {}

    public static TextureManager getInstance() {
        return INSTANCE;
    }

    /**
     * Store a modified texture.
     */
    public void putTexture(Identifier textureId, int[][] pixels, int width, int height) {
        modifiedTextures.put(textureId, pixels);
        textureDimensions.put(textureId, new int[]{width, height});
    }

    /**
     * Get all modified texture identifiers.
     */
    public Set<Identifier> getModifiedTextureIds() {
        return modifiedTextures.keySet();
    }

    /**
     * Get modified pixels for a texture.
     */
    public int[][] getPixels(Identifier textureId) {
        return modifiedTextures.get(textureId);
    }

    /**
     * Get dimensions for a texture.
     */
    public int[] getDimensions(Identifier textureId) {
        return textureDimensions.get(textureId);
    }

    public boolean hasModifiedTextures() {
        return !modifiedTextures.isEmpty();
    }

    /**
     * Remove a single modified texture (for reset).
     */
    public void removeTexture(Identifier textureId) {
        modifiedTextures.remove(textureId);
        textureDimensions.remove(textureId);
    }

    /**
     * Apply a modified texture live by uploading pixels directly to the sprite atlas on the GPU.
     * Also generates and uploads mipmap levels so the texture remains visible at distance.
     */
    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Store the modified texture
        Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        putTexture(textureId, pixels, width, height);

        // Find the sprite in the block atlas
        Sprite sprite = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(spriteId);
        if (sprite == null) return;

        int atlasX = sprite.getX();
        int atlasY = sprite.getY();

        // Create a NativeImage and upload to the atlas
        RenderSystem.assertOnRenderThread();

        // Bind the block atlas texture
        SpriteAtlasTexture atlas = (SpriteAtlasTexture) client.getTextureManager()
                .getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        atlas.bindTexture();

        // Upload the base (level 0) image
        try (NativeImage img = new NativeImage(width, height, false)) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    img.setColorArgb(x, y, pixels[x][y]);
                }
            }
            img.upload(0, atlasX, atlasY, false);
        }

        // Generate and upload mipmap levels so the texture is visible at distance
        int mipWidth = width;
        int mipHeight = height;
        int[][] currentPixels = pixels;
        int level = 1;

        while (mipWidth > 1 && mipHeight > 1) {
            int newW = mipWidth / 2;
            int newH = mipHeight / 2;
            if (newW < 1 || newH < 1) break;

            int[][] mipPixels = new int[newW][newH];
            for (int x = 0; x < newW; x++) {
                for (int y = 0; y < newH; y++) {
                    // Average 2x2 block of pixels
                    int sx = x * 2;
                    int sy = y * 2;
                    mipPixels[x][y] = averageColors(
                            currentPixels[sx][sy],
                            sx + 1 < mipWidth ? currentPixels[sx + 1][sy] : currentPixels[sx][sy],
                            sy + 1 < mipHeight ? currentPixels[sx][sy + 1] : currentPixels[sx][sy],
                            sx + 1 < mipWidth && sy + 1 < mipHeight ? currentPixels[sx + 1][sy + 1] : currentPixels[sx][sy]
                    );
                }
            }

            try (NativeImage mipImg = new NativeImage(newW, newH, false)) {
                for (int x = 0; x < newW; x++) {
                    for (int y = 0; y < newH; y++) {
                        mipImg.setColorArgb(x, y, mipPixels[x][y]);
                    }
                }
                mipImg.upload(level, atlasX >> level, atlasY >> level, false);
            }

            currentPixels = mipPixels;
            mipWidth = newW;
            mipHeight = newH;
            level++;

            // Minecraft typically uses 4 mipmap levels max
            if (level > 4) break;
        }
    }

    /**
     * Average 4 ARGB colors for mipmap generation.
     */
    private static int averageColors(int c1, int c2, int c3, int c4) {
        int a = ((c1 >> 24 & 0xFF) + (c2 >> 24 & 0xFF) + (c3 >> 24 & 0xFF) + (c4 >> 24 & 0xFF)) / 4;
        int r = ((c1 >> 16 & 0xFF) + (c2 >> 16 & 0xFF) + (c3 >> 16 & 0xFF) + (c4 >> 16 & 0xFF)) / 4;
        int g = ((c1 >> 8 & 0xFF) + (c2 >> 8 & 0xFF) + (c3 >> 8 & 0xFF) + (c4 >> 8 & 0xFF)) / 4;
        int b = ((c1 & 0xFF) + (c2 & 0xFF) + (c3 & 0xFF) + (c4 & 0xFF)) / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Clear all modified textures.
     */
    public void clear() {
        modifiedTextures.clear();
        textureDimensions.clear();
    }
}
