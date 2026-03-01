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
    // Maps texture identifier -> original (pre-edit) pixel data for preview
    private final Map<Identifier, int[][]> originalTextures = new HashMap<>();

    // Whether we're currently showing original textures (preview mode)
    private boolean previewingOriginals = false;

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
     * Store original (pre-edit) pixels for preview comparison.
     * Only stores if not already stored for that texture.
     */
    public void storeOriginal(Identifier textureId, int[][] pixels, int width, int height) {
        if (!originalTextures.containsKey(textureId)) {
            int[][] copy = new int[width][height];
            for (int x = 0; x < width; x++) {
                System.arraycopy(pixels[x], 0, copy[x], 0, height);
            }
            originalTextures.put(textureId, copy);
        }
    }

    /**
     * Get stored original pixels for a texture.
     */
    public int[][] getOriginalPixels(Identifier textureId) {
        return originalTextures.get(textureId);
    }

    public boolean isPreviewingOriginals() {
        return previewingOriginals;
    }

    /**
     * Toggle previewing original textures. Re-uploads either originals or modified to atlas.
     */
    public void setPreviewingOriginals(boolean previewing) {
        if (previewing == previewingOriginals) return;
        previewingOriginals = previewing;

        for (Identifier textureId : modifiedTextures.keySet()) {
            int[] dims = textureDimensions.get(textureId);
            if (dims == null) continue;
            int w = dims[0], h = dims[1];

            // Derive spriteId from textureId: remove "textures/" prefix and ".png" suffix
            String path = textureId.getPath();
            if (path.startsWith("textures/") && path.endsWith(".png")) {
                path = path.substring("textures/".length(), path.length() - ".png".length());
            }
            Identifier spriteId = Identifier.of(textureId.getNamespace(), path);

            int[][] pixels;
            if (previewing) {
                pixels = originalTextures.get(textureId);
                if (pixels == null) continue; // no original stored, skip
            } else {
                pixels = modifiedTextures.get(textureId);
            }
            reuploadToAtlas(spriteId, pixels, w, h);
        }
    }

    /**
     * Re-upload pixels to the sprite atlas (used for preview toggling).
     */
    private void reuploadToAtlas(Identifier spriteId, int[][] pixels, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        Sprite sprite = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(spriteId);
        if (sprite == null) return;

        int atlasX = sprite.getX();
        int atlasY = sprite.getY();

        RenderSystem.assertOnRenderThread();
        SpriteAtlasTexture atlas = (SpriteAtlasTexture) client.getTextureManager()
                .getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        atlas.bindTexture();

        try (NativeImage img = new NativeImage(width, height, false)) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    img.setColorArgb(x, y, pixels[x][y]);
                }
            }
            img.upload(0, atlasX, atlasY, false);
        }
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
        applyLive(spriteId, pixels, width, height, null);
    }

    /**
     * Apply live with optional original pixels for preview support.
     */
    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height, int[][] origPixels) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Store the modified texture
        Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        // Store original pixels for preview if provided and not yet stored
        if (origPixels != null) {
            storeOriginal(textureId, origPixels, width, height);
        }

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
        originalTextures.clear();
        previewingOriginals = false;
    }
}
