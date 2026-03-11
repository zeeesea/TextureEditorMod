package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteAccessor;
import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

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

    public void removeOriginal(Identifier textureId) {
        originalTextures.remove(textureId);
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
     * Write pixel data into a sprite's NativeImage and re-upload to the block atlas.
     *
     * In 1.21.11 the atlas uses RenderPass + ANIMATE_SPRITE_BLIT shader to position sprites.
     * We replicate that approach: create temp texture, upload pixels, blit to atlas via RenderPass.
     */
    private void writeSpritePixels(Identifier spriteId, int[][] pixels, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Try block atlas first, then items atlas, then GUI atlas
        SpriteAtlasTexture atlas = null;
        Sprite sprite = null;

        SpriteAtlasTexture blockAtlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        if (blockAtlas != null) {
            sprite = blockAtlas.getSprite(spriteId);
            if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                atlas = blockAtlas;
            } else {
                sprite = null;
            }
        }

        if (sprite == null) {
            try {
                var tex = client.getTextureManager().getTexture(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE);
                if (tex instanceof SpriteAtlasTexture itemsAtlas) {
                    sprite = itemsAtlas.getSprite(spriteId);
                    if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                        atlas = itemsAtlas;
                    } else {
                        sprite = null;
                    }
                }
            } catch (Exception e) {
                // Items atlas not available
            }
        }

        // Try GUI atlas (for HUD sprites like hotbar, hearts, etc.)
        if (sprite == null) {
            try {
                Identifier guiAtlasId = Identifier.ofVanilla("textures/atlas/gui.png");
                var tex = client.getTextureManager().getTexture(guiAtlasId);
                if (tex instanceof SpriteAtlasTexture guiAtlas) {
                    sprite = guiAtlas.getSprite(spriteId);
                    if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                        atlas = guiAtlas;
                    } else {
                        sprite = null;
                    }
                }
            } catch (Exception e) {
                // GUI atlas not available
            }
        }

        // Try celestials atlas (for sun, moon sprites)
        if (sprite == null) {
            try {
                Identifier celestialsAtlasId = Identifier.ofVanilla("textures/atlas/celestials.png");
                var tex = client.getTextureManager().getTexture(celestialsAtlasId);
                if (tex instanceof SpriteAtlasTexture celestialsAtlas) {
                    sprite = celestialsAtlas.getSprite(spriteId);
                    if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                        atlas = celestialsAtlas;
                    } else {
                        sprite = null;
                    }
                }
            } catch (Exception e) {
                // Celestials atlas not available
            }
        }

        if (atlas == null || sprite == null) {
            System.out.println("[TextureEditor] ERROR: Sprite not found in any atlas for " + spriteId);
            return;
        }

        // Debug: log which atlas and usage flags
        GpuTexture atlasTex = atlas.getGlTexture();
        int usage = atlasTex.usage();
        System.out.println("[TextureEditor] writeSpritePixels: " + spriteId + " in atlas " +
            atlasTex.getWidth(0) + "x" + atlasTex.getHeight(0) +
            " usage=0x" + Integer.toHexString(usage) +
            " RENDER_ATTACHMENT=" + ((usage & 8) != 0));

        SpriteContents contents = sprite.getContents();
        SpriteContentsAccessor contentsAccessor = (SpriteContentsAccessor) contents;
        NativeImage image = contentsAccessor.getImage();
        if (image == null) {
            System.out.println("[TextureEditor] ERROR: NativeImage is NULL for " + spriteId);
            return;
        }

        int padding = ((SpriteAccessor) sprite).getPadding();
        int spriteX = sprite.getX();
        int spriteY = sprite.getY();
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        int writeW = Math.min(width, imgW);
        int writeH = Math.min(height, imgH);

        // Step 1: Write new pixel data into the sprite's NativeImage (CPU side)
        for (int x = 0; x < writeW; x++) {
            for (int y = 0; y < writeH; y++) {
                image.setColorArgb(x, y, pixels[x][y]);
            }
        }

        // Step 2: Regenerate mipmaps from updated base image
        int mipLevels = client.options.getMipmapLevels().getValue();
        try {
            contents.generateMipmaps(mipLevels);
        } catch (Throwable t) {
            System.out.println("[TextureEditor] WARN: Failed to regenerate mipmaps: " + t.getMessage());
        }

        NativeImage[] mipmaps = contentsAccessor.getMipmapLevelsImages();
        int spriteMipLevels = mipmaps.length;

        // Step 3: Upload via RenderPass blit (same approach as SpriteAtlasTexture.upload())
        try {
            GpuTexture atlasTexture = atlas.getGlTexture();
            int atlasW = atlasTexture.getWidth(0);
            int atlasH = atlasTexture.getHeight(0);
            // Use the atlas's mip level count, not the sprite's — GUI atlas may have fewer mip levels than block atlas
            int atlasMipLevels = atlasTexture.getMipLevels();
            int numMipLevels = Math.min(spriteMipLevels, atlasMipLevels);
            if (numMipLevels <= 0) numMipLevels = 1;

            System.out.println("[TextureEditor] Blitting " + spriteId + ": atlasMips=" + atlasMipLevels + " spriteMips=" + spriteMipLevels + " using=" + numMipLevels);

            // Create temp GpuTexture for the sprite
            GpuTexture tempTexture = RenderSystem.getDevice().createTexture(
                () -> "TextureEditor temp " + spriteId,
                5, // COPY_DST(1) | TEXTURE_BINDING(4)
                TextureFormat.RGBA8,
                contents.getWidth(),
                contents.getHeight(),
                1,
                numMipLevels
            );

            // Upload each mip level to the temp texture at (0,0)
            for (int mip = 0; mip < numMipLevels; mip++) {
                int mipW = contents.getWidth() >> mip;
                int mipH = contents.getHeight() >> mip;
                if (mipW <= 0 || mipH <= 0) break;
                RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToTexture(tempTexture, mipmaps[mip], mip, 0, 0, 0, mipW, mipH, 0, 0);
            }

            // Build the sprite info uniform buffer (same layout as Sprite.putSpriteInfo)
            int uniformAlignment = RenderSystem.getDevice().getUniformOffsetAlignment();
            int spriteInfoSize = SpriteContents.SPRITE_INFO_SIZE;
            int stride = MathHelper.roundUpToMultiple(spriteInfoSize, uniformAlignment);
            int totalSize = stride * numMipLevels;
            ByteBuffer buffer = MemoryUtil.memAlloc(totalSize);

            // Fill uniform data for each mip level (replicating Sprite.putSpriteInfo)
            for (int mip = 0; mip < numMipLevels; mip++) {
                int bufOffset = mip * stride;
                Std140Builder.intoBuffer(MemoryUtil.memSlice(buffer, bufOffset, stride))
                    .putMat4f(new Matrix4f().ortho2D(0.0F, atlasW >> mip, 0.0F, atlasH >> mip))
                    .putMat4f(new Matrix4f()
                        .translate(spriteX >> mip, spriteY >> mip, 0.0F)
                        .scale(contents.getWidth() + padding * 2 >> mip, contents.getHeight() + padding * 2 >> mip, 1.0F))
                    .putFloat((float) padding / contents.getWidth())
                    .putFloat((float) padding / contents.getHeight())
                    .putInt(mip);
            }

            GpuBuffer uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "TextureEditor uniform",
                GpuBuffer.USAGE_UNIFORM,
                buffer
            );
            MemoryUtil.memFree(buffer);

            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST, true);

            GpuTextureView[] atlasMipViews = new GpuTextureView[numMipLevels];
            for (int mip = 0; mip < numMipLevels; mip++) {
                atlasMipViews[mip] = RenderSystem.getDevice().createTextureView(atlasTexture, mip, 1);
            }

            GpuTextureView[] tempViews = new GpuTextureView[numMipLevels];
            for (int mip = 0; mip < numMipLevels; mip++) {
                tempViews[mip] = RenderSystem.getDevice().createTextureView(tempTexture);
            }

            // Blit via RenderPass (same as SpriteAtlasTexture.upload)
            for (int mip = 0; mip < numMipLevels; mip++) {
                try (RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                            () -> "TextureEditor blit " + spriteId,
                            atlasMipViews[mip],
                            OptionalInt.empty())) {
                    renderPass.setPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT);
                    renderPass.bindTexture("Sprite", tempViews[mip], sampler);
                    renderPass.setUniform("SpriteAnimationInfo", uniformBuffer.slice((long)mip * stride, spriteInfoSize));
                    renderPass.draw(0, 6);
                }
            }

            // Cleanup
            for (GpuTextureView v : atlasMipViews) v.close();
            for (GpuTextureView v : tempViews) v.close();
            tempTexture.close();
            uniformBuffer.close();
        } catch (Throwable t) {
            System.out.println("[TextureEditor] ERROR during upload: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
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
