package com.zeeesea.textureeditor.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
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
    // Debugging: dump NativeImage PNGs to disk when enabled
    public static volatile boolean DEBUG_DUMP = false;
    public static volatile String DEBUG_DUMP_DIR = "run/textureeditor_dumps";

    private TextureManager() {}

    public static TextureManager getInstance() {
        return INSTANCE;
    }

    /**
     * Dump a NativeImage to a PNG file when DEBUG_DUMP is enabled. Uses NativeImage.getColorArgb
     * to produce a BufferedImage and writes it with ImageIO. Safe no-op when disabled.
     */
    private static void dumpIfEnabled(NativeImage img, String tag) {
        if (!DEBUG_DUMP || img == null) return;
        try {
            File dir = new File(DEBUG_DUMP_DIR);
            if (!dir.exists()) dir.mkdirs();
            long ts = System.currentTimeMillis();
            String safeTag = tag.replaceAll("[^A-Za-z0-9_.-]", "_");
            int w = img.getWidth();
            int h = img.getHeight();
            File out = new File(dir, ts + "_" + safeTag + "_" + w + "x" + h + ".png");
            System.out.println("[TextureEditor] dumping image tag=" + tag + " size=" + w + "x" + h + " -> " + out.getAbsolutePath());
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    try {
                        bi.setRGB(x, y, img.getColorArgb(x, y));
                    } catch (Throwable t) {
                        // fallback: fully transparent pixel on error
                        bi.setRGB(x, y, 0x00000000);
                    }
                }
            }
            ImageIO.write(bi, "PNG", out);
            System.out.println("[TextureEditor] dumped image: " + out.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[TextureEditor] dump failed: " + t.getMessage());
        }
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

        updateSpriteContents(sprite, pixels, width, height);

        int atlasX = sprite.getX();
        int atlasY = sprite.getY();

        RenderSystem.assertOnRenderThread();
        SpriteAtlasTexture atlas = (SpriteAtlasTexture) client.getTextureManager()
                .getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

        // Try to update the CPU-side sprite contents so the atlas shows the change without direct OpenGL upload
        try (NativeImage img = new NativeImage(width, height, false)) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    img.setColorArgb(x, y, pixels[x][y]);
                }
            }

            // Debug dump of the CPU-side image before any upload/register fallback
            dumpIfEnabled(img, "reupload_preupload_" + spriteId.getPath());

            try {
                Sprite found = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(Identifier.of(spriteId.getNamespace(), spriteId.getPath()));
                if (found != null) {
                    var contents = found.getContents();
                    var cpuImg = ((com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor)contents).getImage();
                    if (cpuImg != null) {
                        cpuImg.copyFrom(img);
                        dumpIfEnabled(cpuImg, "reupload_spritecontents_aftercopy_" + spriteId.getPath());
                    } else {
                        // Fallback: register a dynamic texture so the image is visible
                        net.minecraft.client.texture.NativeImageBackedTexture dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> Identifier.of(spriteId.getNamespace(), spriteId.getPath()).toString(), img);
                        client.getTextureManager().registerTexture(Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png"), dynamicTex);
                        dynamicTex.upload();
                    }
                }
            } catch (Throwable t) {
                // If anything fails, fallback to dynamic texture
                net.minecraft.client.texture.NativeImageBackedTexture dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> Identifier.of(spriteId.getNamespace(), spriteId.getPath()).toString(), img);
                client.getTextureManager().registerTexture(Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png"), dynamicTex);
                dynamicTex.upload();
            }
        }

        if (spriteId.getPath().startsWith("item/")) {
            ItemModelRebaker.rebake(spriteId);
        }
    }

    /**
     * Keep CPU-side sprite image in sync with live edits so generated item rebakes
     * can use the updated alpha mask to rebuild side quads.
     */
    private static void updateSpriteContents(Sprite sprite, int[][] pixels, int width, int height) {
        SpriteContents contents = sprite.getContents();
        NativeImage image = ((SpriteContentsAccessor) contents).getImage();
        if (image == null || image.getWidth() < width || image.getHeight() < height) return;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setColorArgb(x, y, pixels[x][y]);
            }
        }
        // Dump the sprite's CPU-side NativeImage after writing so we can compare
        dumpIfEnabled(image, "spritecontents_afterwrite_x" + sprite.getX() + "y" + sprite.getY());
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

        updateSpriteContents(sprite, pixels, width, height);

        int atlasX = sprite.getX();
        int atlasY = sprite.getY();

        // Create a NativeImage and upload to the atlas. The actual GL bind/upload
        // operations are recorded to the render thread below with RenderSystem.recordRenderCall.

        // Ensure bind + upload happen on the render thread in order. Create and upload
        // NativeImage(s) inside the recorded render call so the correct atlas is bound
        // when glTexSubImage2D is invoked.
        SpriteAtlasTexture atlas = (SpriteAtlasTexture) client.getTextureManager()
                .getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

        RenderSystem.queueFencedTask(() -> {
            try {
                // Bind the atlas via its AbstractTexture.bindTexture so the texture
                // is bound in the same manner the engine expects.
                // AbstractTexture.bindTexture may not exist in some mappings; bind by GL id instead
                com.mojang.blaze3d.opengl.GlStateManager._bindTexture(com.zeeesea.textureeditor.util.TextureCompat.getGlId(atlas));

                // Upload the base (level 0) image
                try (NativeImage img = new NativeImage(width, height, false)) {
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            img.setColorArgb(x, y, pixels[x][y]);
                        }
                    }
                    dumpIfEnabled(img, "gpu_upload_before_level0_" + spriteId.getPath());
                    com.zeeesea.textureeditor.util.NativeImageCompat.upload(img, 0, atlasX, atlasY, false);
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
                        dumpIfEnabled(mipImg, "gpu_upload_before_level" + level + "_" + spriteId.getPath());
                        com.zeeesea.textureeditor.util.NativeImageCompat.upload(mipImg, level, atlasX >> level, atlasY >> level, false);
                    }

                    currentPixels = mipPixels;
                    mipWidth = newW;
                    mipHeight = newH;
                    level++;

                    // Minecraft typically uses 4 mipmap levels max
                    if (level > 4) break;
                }
            } catch (Throwable t) {
                System.out.println("[TextureEditor] render-thread upload failed: " + t.getMessage());
            }
        });

        if (spriteId.getPath().startsWith("item/")) {
            ItemModelRebaker.rebake(spriteId);
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
        ItemModelRebaker.invalidateCache();
    }
}
