package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages modified textures and applies them live by uploading to the block atlas.
 * In 1.21.11, direct GL texture access is abstracted away via GpuTexture.
 * We modify sprite NativeImages directly via mixin accessor instead.
 */
public class TextureManager {
    private static final TextureManager INSTANCE = new TextureManager();

    private final Map<Identifier, int[][]> modifiedTextures = new HashMap<>();
    private final Map<Identifier, int[]> textureDimensions = new HashMap<>();
    private final Map<Identifier, int[][]> originalTextures = new HashMap<>();
    private boolean previewingOriginals = false;

    private TextureManager() {}

    public static TextureManager getInstance() { return INSTANCE; }

    public void putTexture(Identifier textureId, int[][] pixels, int width, int height) {
        modifiedTextures.put(textureId, pixels);
        textureDimensions.put(textureId, new int[]{width, height});
    }

    public void storeOriginal(Identifier textureId, int[][] pixels, int width, int height) {
        if (!originalTextures.containsKey(textureId)) {
            int[][] copy = new int[width][height];
            for (int x = 0; x < width; x++) {
                System.arraycopy(pixels[x], 0, copy[x], 0, height);
            }
            originalTextures.put(textureId, copy);
        }
    }

    public int[][] getOriginalPixels(Identifier textureId) { return originalTextures.get(textureId); }
    public boolean isPreviewingOriginals() { return previewingOriginals; }
    public Set<Identifier> getModifiedTextureIds() { return modifiedTextures.keySet(); }
    public int[][] getPixels(Identifier textureId) { return modifiedTextures.get(textureId); }
    public int[] getDimensions(Identifier textureId) { return textureDimensions.get(textureId); }
    public boolean hasModifiedTextures() { return !modifiedTextures.isEmpty(); }

    public void removeTexture(Identifier textureId) {
        modifiedTextures.remove(textureId);
        textureDimensions.remove(textureId);
    }

    /**
     * Toggle previewing original textures. Writes either originals or modified pixels
     * directly into the sprite's NativeImage in the atlas.
     */
    public void setPreviewingOriginals(boolean previewing) {
        if (previewing == previewingOriginals) return;
        previewingOriginals = previewing;

        for (Identifier textureId : modifiedTextures.keySet()) {
            int[] dims = textureDimensions.get(textureId);
            if (dims == null) continue;
            int w = dims[0], h = dims[1];

            String path = textureId.getPath();
            if (path.startsWith("textures/") && path.endsWith(".png")) {
                path = path.substring("textures/".length(), path.length() - ".png".length());
            }
            Identifier spriteId = Identifier.of(textureId.getNamespace(), path);

            int[][] pixels;
            if (previewing) {
                pixels = originalTextures.get(textureId);
                if (pixels == null) continue;
            } else {
                pixels = modifiedTextures.get(textureId);
            }
            writeSpritePixels(spriteId, pixels, w, h);
        }
    }

    /**
     * Write pixel data directly into a sprite's NativeImage in the block atlas.
     * The rendering pipeline will pick up the changes on the next frame.
     */
    private void writeSpritePixels(Identifier spriteId, int[][] pixels, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        SpriteAtlasTexture atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Sprite sprite = atlas.getSprite(spriteId);
        if (sprite == null) return;

        SpriteContents contents = sprite.getContents();
        NativeImage image = ((SpriteContentsAccessor) contents).getImage();
        if (image == null) return;

        int imgW = image.getWidth();
        int imgH = image.getHeight();
        for (int x = 0; x < Math.min(width, imgW); x++) {
            for (int y = 0; y < Math.min(height, imgH); y++) {
                image.setColorArgb(x, y, pixels[x][y]);
            }
        }
        // Force the sprite to re-upload on next tick
        contents.upload(atlas.getGlTexture(), 0);
    }

    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height) {
        applyLive(spriteId, pixels, width, height, null);
    }

    /**
     * Apply live by writing directly into the sprite's NativeImage and re-uploading.
     */
    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height, int[][] origPixels) {
        Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        if (origPixels != null) {
            storeOriginal(textureId, origPixels, width, height);
        }

        putTexture(textureId, pixels, width, height);
        writeSpritePixels(spriteId, pixels, width, height);
    }

    private static int averageColors(int c1, int c2, int c3, int c4) {
        int a = ((c1 >> 24 & 0xFF) + (c2 >> 24 & 0xFF) + (c3 >> 24 & 0xFF) + (c4 >> 24 & 0xFF)) / 4;
        int r = ((c1 >> 16 & 0xFF) + (c2 >> 16 & 0xFF) + (c3 >> 16 & 0xFF) + (c4 >> 16 & 0xFF)) / 4;
        int g = ((c1 >> 8 & 0xFF) + (c2 >> 8 & 0xFF) + (c3 >> 8 & 0xFF) + (c4 >> 8 & 0xFF)) / 4;
        int b = ((c1 & 0xFF) + (c2 & 0xFF) + (c3 & 0xFF) + (c4 & 0xFF)) / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void clear() {
        modifiedTextures.clear();
        textureDimensions.clear();
        originalTextures.clear();
        previewingOriginals = false;
    }
}
