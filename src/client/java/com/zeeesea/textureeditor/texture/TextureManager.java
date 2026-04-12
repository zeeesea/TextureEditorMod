package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteAccessor;
import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
    private final Map<Identifier, ItemAnimationData> itemAnimations = new HashMap<>();
    private final Map<Identifier, LiveItemAnimation> liveItemAnimations = new HashMap<>();
    private boolean previewingOriginals = false;
    private volatile boolean itemGuiAtlasDirty = false;

    public record ItemAnimationData(Identifier textureId, Identifier spriteId, List<int[][]> frames, int width, int height, int frameTimeTicks, boolean pingPong) {}

    private static final class LiveItemAnimation {
        private final Identifier textureId;
        private final Identifier spriteId;
        private final List<int[][]> frames;
        private final int width;
        private final int height;
        private final int frameTimeTicks;
        private final boolean pingPong;
        private int tickCounter = 0;
        private int frameIndex = 0;
        private int direction = 1;
        private int lastGeometryHash;

        private LiveItemAnimation(Identifier textureId, Identifier spriteId, List<int[][]> frames,
                                  int width, int height, int frameTimeTicks, boolean pingPong) {
            this.textureId = textureId;
            this.spriteId = spriteId;
            this.frames = frames;
            this.width = width;
            this.height = height;
            this.frameTimeTicks = Math.max(1, frameTimeTicks);
            this.pingPong = pingPong;
            this.lastGeometryHash = frames.isEmpty() ? 0 : computeOpaqueMaskHash(frames.getFirst(), width, height);
        }
    }

    private TextureManager() {}

    public static TextureManager getInstance() { return INSTANCE; }

    public void putTexture(Identifier textureId, int[][] pixels, int width, int height) {
        // Try to ensure we have the original pixels to compare against
        ensureOriginalStored(textureId);
        int[][] orig = originalTextures.get(textureId);

        // If we don't have original pixels available, assume modified (can't compare)
        if (orig == null) {
            modifiedTextures.put(textureId, pixels);
            textureDimensions.put(textureId, new int[]{width, height});
            return;
        }

        // Quick size check
        boolean isDifferent = true;
        if (orig.length == width && (width == 0 || orig[0].length == height)) {
            // Compare pixels; bail out early on first difference
            isDifferent = false;
            outer:
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (orig[x][y] != pixels[x][y]) {
                        isDifferent = true;
                        break outer;
                    }
                }
            }
        }

        if (isDifferent) {
            modifiedTextures.put(textureId, pixels);
            textureDimensions.put(textureId, new int[]{width, height});
            // If this is an armor/equipment texture alias, also mark common model/alias ids so UI picks it up
            try {
                addArmorAliasMarks(textureId, pixels, width, height);
            } catch (Exception ignored) {}
        } else {
            // Pixels match original -> ensure we don't mark this texture as modified
            modifiedTextures.remove(textureId);
            textureDimensions.remove(textureId);
        }
    }

    // When a texture is modified, also mark any known armor/equipment aliases so BrowseScreen can detect them
    private void addArmorAliasMarks(Identifier textureId, int[][] pixels, int width, int height) {
        String path = textureId.getPath();
        String ns = textureId.getNamespace();

        // equipment -> models/armor
        if (path.startsWith("textures/entity/equipment/humanoid/")) {
            String name = path.substring("textures/entity/equipment/humanoid/".length());
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
            Identifier modelId = Identifier.of(ns, "textures/models/armor/" + name + "_layer_1.png");
            modifiedTextures.putIfAbsent(modelId, pixels);
            textureDimensions.putIfAbsent(modelId, new int[]{width, height});
        }
        if (path.startsWith("textures/entity/equipment/humanoid_leggings/")) {
            String name = path.substring("textures/entity/equipment/humanoid_leggings/".length());
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
            Identifier modelId = Identifier.of(ns, "textures/models/armor/" + name + "_layer_2.png");
            modifiedTextures.putIfAbsent(modelId, pixels);
            textureDimensions.putIfAbsent(modelId, new int[]{width, height});
        }
        if (path.startsWith("textures/entity/equipment/piglin_head/")) {
            String name = path.substring("textures/entity/equipment/piglin_head/".length());
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
            Identifier modelId = Identifier.of(ns, "textures/models/armor/" + name + "_piglin_helmet.png");
            modifiedTextures.putIfAbsent(modelId, pixels);
            textureDimensions.putIfAbsent(modelId, new int[]{width, height});
        }

        // models/armor -> equipment aliases
        if (path.startsWith("textures/models/armor/") && path.endsWith(".png")) {
            String name = path.substring("textures/models/armor/".length());
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
            String raw = name;
            String suffix = "";
            if (raw.endsWith("_overlay")) { suffix = "_overlay"; raw = raw.substring(0, raw.length() - "_overlay".length()); }

            if (raw.endsWith("_layer_1")) {
                String material = raw.substring(0, raw.length() - "_layer_1".length());
                Identifier eq = Identifier.of(ns, "textures/entity/equipment/humanoid/" + material + suffix + ".png");
                modifiedTextures.putIfAbsent(eq, pixels);
                textureDimensions.putIfAbsent(eq, new int[]{width, height});
            } else if (raw.endsWith("_layer_2")) {
                String material = raw.substring(0, raw.length() - "_layer_2".length());
                Identifier eq = Identifier.of(ns, "textures/entity/equipment/humanoid_leggings/" + material + suffix + ".png");
                modifiedTextures.putIfAbsent(eq, pixels);
                textureDimensions.putIfAbsent(eq, new int[]{width, height});
            } else if (raw.contains("_piglin_helmet")) {
                String material = raw.replace("_piglin_helmet", "");
                Identifier eq1 = Identifier.of(ns, "textures/entity/equipment/piglin_head/" + material + suffix + ".png");
                Identifier eq2 = Identifier.of(ns, "textures/entity/equipment/humanoid/" + material + suffix + ".png");
                modifiedTextures.putIfAbsent(eq1, pixels);
                textureDimensions.putIfAbsent(eq1, new int[]{width, height});
                modifiedTextures.putIfAbsent(eq2, pixels);
                textureDimensions.putIfAbsent(eq2, new int[]{width, height});
            }
        }
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
    public boolean hasModifiedTextures() { return !modifiedTextures.isEmpty() || !itemAnimations.isEmpty(); }
    public Set<Identifier> getAnimatedTextureIds() { return itemAnimations.keySet(); }
    public ItemAnimationData getItemAnimation(Identifier textureId) { return itemAnimations.get(textureId); }

    /**
     * Consumed by GuiRenderer mixin to rebuild its per-frame item icon atlas once
     * after a live item texture change.
     */
    public boolean consumeItemGuiAtlasDirty() {
        if (!itemGuiAtlasDirty) return false;
        itemGuiAtlasDirty = false;
        return true;
    }

    private void markItemGuiAtlasDirty(Identifier spriteId) {
        if (spriteId == null) return;
        String path = spriteId.getPath();
        // GUI item rendering caches both classic item sprites and block sprites used by block items.
        if (path.startsWith("item/") || path.startsWith("block/")) {
            itemGuiAtlasDirty = true;
        }
    }

    public void removeTexture(Identifier textureId) {
        modifiedTextures.remove(textureId);
        textureDimensions.remove(textureId);
    }

    public void removeOriginal(Identifier textureId) {
        originalTextures.remove(textureId);
    }

    public void setItemAnimation(Identifier textureId, Identifier spriteId, List<int[][]> frames,
                                 int width, int height, int frameTimeTicks, boolean pingPong) {
        if (textureId == null || spriteId == null || frames == null || frames.isEmpty() || width <= 0 || height <= 0) return;
        List<int[][]> frameCopies = new ArrayList<>(frames.size());
        for (int[][] frame : frames) {
            if (frame == null || frame.length != width || frame[0].length != height) continue;
            frameCopies.add(copyFrame(frame, width, height));
        }
        if (frameCopies.isEmpty()) return;
        itemAnimations.put(textureId, new ItemAnimationData(textureId, spriteId, frameCopies, width, height, Math.max(1, frameTimeTicks), pingPong));
    }

    public void removeItemAnimation(Identifier textureId) {
        if (textureId == null) return;
        itemAnimations.remove(textureId);
        liveItemAnimations.remove(textureId);
    }

    public void startItemAnimationLive(Identifier textureId, Identifier spriteId, List<int[][]> frames,
                                       int width, int height, int frameTimeTicks, boolean pingPong, int[][] origPixels) {
        if (textureId == null || spriteId == null || frames == null || frames.isEmpty()) return;
        setItemAnimation(textureId, spriteId, frames, width, height, frameTimeTicks, pingPong);
        ItemAnimationData data = itemAnimations.get(textureId);
        if (data == null) return;

        LiveItemAnimation live = new LiveItemAnimation(textureId, spriteId, data.frames(), data.width(), data.height(), data.frameTimeTicks(), data.pingPong());
        liveItemAnimations.put(textureId, live);

        if (origPixels != null) {
            applyLive(spriteId, live.frames.getFirst(), live.width, live.height, origPixels, true);
        } else {
            applyLive(spriteId, live.frames.getFirst(), live.width, live.height, null, true);
        }
    }

    public void stopItemAnimationLive(Identifier textureId) {
        if (textureId == null) return;
        liveItemAnimations.remove(textureId);
    }

    public void tickItemAnimations() {
        if (liveItemAnimations.isEmpty()) return;
        Iterator<LiveItemAnimation> it = liveItemAnimations.values().iterator();
        while (it.hasNext()) {
            LiveItemAnimation live = it.next();
            if (live.frames.isEmpty() || live.spriteId == null) {
                it.remove();
                continue;
            }

            live.tickCounter++;
            if (live.tickCounter < live.frameTimeTicks) continue;
            live.tickCounter = 0;
            if (live.pingPong && live.frames.size() > 1) {
                int next = live.frameIndex + live.direction;
                if (next >= live.frames.size()) {
                    live.direction = -1;
                    next = Math.max(0, live.frames.size() - 2);
                } else if (next < 0) {
                    live.direction = 1;
                    next = Math.min(live.frames.size() - 1, 1);
                }
                live.frameIndex = next;
            } else {
                live.frameIndex = (live.frameIndex + 1) % live.frames.size();
            }
            int[][] frame = live.frames.get(live.frameIndex);
            int geometryHash = computeOpaqueMaskHash(frame, live.width, live.height);
            boolean rebake = geometryHash != live.lastGeometryHash;
            live.lastGeometryHash = geometryHash;
            applyLive(live.spriteId, frame, live.width, live.height, null, rebake);
        }
    }

    private static int computeOpaqueMaskHash(int[][] frame, int width, int height) {
        if (frame == null || width <= 0 || height <= 0) return 0;
        int hash = 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = (frame[x][y] >>> 24) & 0xFF;
                hash = 31 * hash + (a > 0 ? 1 : 0);
            }
        }
        return hash;
    }

    private static int[][] copyFrame(int[][] src, int w, int h) {
        int[][] out = new int[w][h];
        for (int x = 0; x < w; x++) {
            System.arraycopy(src[x], 0, out[x], 0, h);
        }
        return out;
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
            markItemGuiAtlasDirty(spriteId);

            // Also rebake item models for updated 3D thickness
            try {
                ItemModelRebaker.rebake(spriteId);
            } catch (Exception e) {
                System.out.println("[TextureEditor] ItemModelRebaker preview toggle failed: " + e.getMessage());
            }
        }
    }

    /**
     * Write pixel data into a sprite and re-upload to ALL atlases that contain it.
     * In 1.21.10, sprites can exist in multiple atlases (e.g. block AND items atlas).
     * We must write to ALL so both world rendering and hotbar/inventory update immediately.
     */
    private void writeSpritePixels(Identifier spriteId, int[][] pixels, int width, int height) {
        Minecraft client = Minecraft.getInstance();
        int hitCount = 0;

        // Try each atlas Ã¢â‚¬â€ blit to ALL that contain this sprite
        hitCount += tryBlitToAtlas(client, spriteId, pixels, width, height,
                SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, "BLOCK");
        try {
            int itemsHit = tryBlitToAtlas(client, spriteId, pixels, width, height,
                    SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE, "ITEMS");
            System.out.println("[TextureEditor] ITEMS atlas hit: " + itemsHit + " for " + spriteId);
            hitCount += itemsHit;
        } catch (Exception e) {
            System.out.println("[TextureEditor] ITEMS atlas FAILED: " + e.getMessage());
        }

        try {
            hitCount += tryBlitToAtlas(client, spriteId, pixels, width, height,
                    Identifier.ofVanilla("textures/atlas/gui.png"), "GUI");
        } catch (Exception ignored) {}

        try {
            hitCount += tryBlitToAtlas(client, spriteId, pixels, width, height,
                    Identifier.ofVanilla("textures/atlas/celestials.png"), "CELESTIALS");
        } catch (Exception ignored) {}

        if (hitCount == 0) {
            System.out.println("[TextureEditor] ERROR: Sprite not found in any atlas for " + spriteId);
        } else {
            System.out.println("[TextureEditor] writeSpritePixels: " + spriteId + " updated in " + hitCount + " atlas(es)");
        }
    }

    /**
     * Try to find the sprite in the given atlas and blit pixels to it.
     * Returns 1 if found and blitted, 0 otherwise.
     */
    private int tryBlitToAtlas(Minecraft client, Identifier spriteId, int[][] pixels,
                                int width, int height, Identifier atlasId, String atlasName) {
        var tex = client.getTextureManager().getTexture(atlasId);
        if (!(tex instanceof SpriteAtlasTexture atlas)) return 0;

        Sprite sprite = atlas.getSprite(spriteId);
        if (sprite == null || sprite.getContents().getId().getPath().equals("missingno")) return 0;

        blitSpriteToAtlas(atlas, sprite, spriteId, pixels, width, height, atlasName, client);
        return 1;
    }

    /**
     * Blit updated pixel data for a single sprite into a single atlas texture.
     * Updates the sprite's CPU-side NativeImage, regenerates mipmaps, and uploads via RenderPass.
     */
    private void blitSpriteToAtlas(SpriteAtlasTexture atlas, Sprite sprite, Identifier spriteId,
                                    int[][] pixels, int width, int height,
                                    String atlasName, Minecraft client) {
        SpriteContents contents = sprite.getContents();
        SpriteContentsAccessor contentsAccessor = (SpriteContentsAccessor) contents;
        NativeImage image = contentsAccessor.getImage();
        if (image == null) {
            System.out.println("[TextureEditor] ERROR: NativeImage is NULL for " + spriteId + " in " + atlasName);
            return;
        }

        int padding = ((SpriteAccessor) sprite).getPadding();
        int spriteX = sprite.getX();
        int spriteY = sprite.getY();
        int writeW = Math.min(width, image.getWidth());
        int writeH = Math.min(height, image.getHeight());

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
            System.out.println("[TextureEditor] WARN: Failed to regenerate mipmaps for " + atlasName + ": " + t.getMessage());
        }

        NativeImage[] mipmaps = contentsAccessor.getMipmapLevelsImages();
        int spriteMipLevels = mipmaps.length;

        // Step 3: Upload via RenderPass blit
        try {
            GpuTexture atlasTexture = atlas.getGlTexture();
            int atlasW = atlasTexture.getWidth(0);
            int atlasH = atlasTexture.getHeight(0);
            int atlasMipLevels = atlasTexture.getMipLevels();
            int numMipLevels = Math.min(spriteMipLevels, atlasMipLevels);
            if (numMipLevels <= 0) numMipLevels = 1;

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

            // Upload each mip level to the temp texture
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

            System.out.println("[TextureEditor] Blitted " + spriteId + " to " + atlasName +
                " atlas (" + atlasW + "x" + atlasH + ") at " + spriteX + "," + spriteY +
                " mips=" + numMipLevels);
        } catch (Throwable t) {
            System.out.println("[TextureEditor] ERROR during " + atlasName + " upload: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height) {
        applyLive(spriteId, pixels, width, height, null, true);
    }

    /**
     * Apply live by writing directly into the sprite's NativeImage and re-uploading.
     */
    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height, int[][] origPixels) {
        applyLive(spriteId, pixels, width, height, origPixels, true);
    }

    public void applyLive(Identifier spriteId, int[][] pixels, int width, int height, int[][] origPixels, boolean rebakeModel) {
        Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        if (origPixels != null) {
            storeOriginal(textureId, origPixels, width, height);
        } else {
            ensureOriginalStored(textureId);
        }

        putTexture(textureId, pixels, width, height);
        writeSpritePixels(spriteId, pixels, width, height);
        markItemGuiAtlasDirty(spriteId);

        if (rebakeModel) {
            try {
                ItemModelRebaker.rebake(spriteId);
            } catch (Exception e) {
                System.out.println("[TextureEditor] ItemModelRebaker failed: " + e.getMessage());
            }
        }
    }

    public void applyLive(Identifier spriteId, int[] flatPixels, int[] flatOriginals, int width, int height) {
        Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        int[][] origPixels = null;
        if (flatOriginals != null) {
            origPixels = new int[width][height];
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    origPixels[x][y] = flatOriginals[y * width + x];
        }

        // Check if local texture has different size Ã¢â‚¬â€ scale if needed
        int[] localDims = getDimensions(textureId);
        if (localDims != null && (localDims[0] != width || localDims[1] != height)) {
            int lw = localDims[0], lh = localDims[1];
            int[][] scaled = new int[lw][lh];
            for (int x = 0; x < lw; x++)
                for (int y = 0; y < lh; y++)
                    scaled[x][y] = flatPixels[((int)(y * height / (float)lh)) * width + (int)(x * width / (float)lw)];
            applyLive(spriteId, scaled, lw, lh, origPixels != null ? scalePixels(origPixels, width, height, lw, lh) : null);
            return;
        }

        int[][] pixels = new int[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                pixels[x][y] = flatPixels[y * width + x];
        applyLive(spriteId, pixels, width, height, origPixels);
    }



    public void applyLive(Identifier spriteId, int[] flatPixels, int width, int height) {
        applyLive(spriteId, flatPixels, null, width, height);
    }

    // Used by Multiplayer - Entity
    public void applyLiveEntity(Identifier textureId, int[] flatPixels, int[] flatOriginals, int width, int height) {
        if (flatOriginals != null && flatOriginals.length > 0) {
            int[][] origPixels = new int[width][height];
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    origPixels[x][y] = flatOriginals[y * width + x];
            storeOriginal(textureId, origPixels, width, height);
        } else {
            ensureOriginalStored(textureId);
        }

        // Check local texture size
        int[] localDims = getDimensions(textureId);
        int lw = (localDims != null) ? localDims[0] : width;
        int lh = (localDims != null) ? localDims[1] : height;

        int[][] pixels = new int[lw][lh];
        for (int x = 0; x < lw; x++)
            for (int y = 0; y < lh; y++)
                pixels[x][y] = flatPixels[((int)(y * height / (float)lh)) * width + (int)(x * width / (float)lw)];

        Minecraft client = Minecraft.getInstance();
        putTexture(textureId, pixels, lw, lh);
        final int fw = lw, fh = lh;
        client.execute(() -> {
            var img = new com.mojang.blaze3d.platform.NativeImage(fw, fh, false);
            for (int x = 0; x < fw; x++)
                for (int y = 0; y < fh; y++)
                    img.setColorArgb(x, y, pixels[x][y]);
            var existing = client.getTextureManager().getTexture(textureId);
            if (existing instanceof net.minecraft.client.renderer.texture.DynamicTexture nibt) {
                nibt.setImage(img);
                nibt.upload();
            } else {
                var dynamicTex = new net.minecraft.client.renderer.texture.DynamicTexture(
                        () -> "textureeditor_sync", img);
                client.getTextureManager().registerTexture(textureId, dynamicTex);
                dynamicTex.upload();
            }
        });
    }

    private int[][] scalePixels(int[][] src, int srcW, int srcH, int dstW, int dstH) {
        int[][] result = new int[dstW][dstH];
        for (int x = 0; x < dstW; x++)
            for (int y = 0; y < dstH; y++)
                result[x][y] = src[(int)(x * srcW / (float)dstW)][(int)(y * srcH / (float)dstH)];
        return result;
    }

    /**
     * Load and store original pixels from resource manager if not already stored.
     * Called before any sync apply to ensure reset works for all players.
     */
    private void ensureOriginalStored(Identifier textureId) {
        if (originalTextures.containsKey(textureId)) return;
        try {
            var client = Minecraft.getInstance();
            var resource = client.getResourceManager().getResource(textureId);
            if (resource.isPresent()) {
                var img = com.mojang.blaze3d.platform.NativeImage.read(
                        resource.get().getInputStream());
                int w = img.getWidth(), h = img.getHeight();
                int[][] orig = new int[w][h];
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        orig[x][y] = img.getColorArgb(x, y);
                img.close();
                storeOriginal(textureId, orig, w, h);
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Could not store original for: " + textureId);
        }
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
        itemAnimations.clear();
        liveItemAnimations.clear();
        previewingOriginals = false;
        itemGuiAtlasDirty = false;
        ItemModelRebaker.invalidateCache();
    }
}

