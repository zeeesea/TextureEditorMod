package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Extracts texture data for a specific block face.
 */
public class TextureExtractor {

    public record BlockFaceTexture(Identifier textureId, int[][] pixels, int width, int height) {}

    /**
     * Extract the texture for a given block state and face direction.
     */
    public static BlockFaceTexture extract(BlockState state, Direction face) {
        return extract(state, face, 0);
    }

    /**
     * Extract a specific quad layer for a given face (0 = base, 1+ = overlay like grass overhang).
     */
    public static BlockFaceTexture extract(BlockState state, Direction face, int quadIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        var model = client.getBlockRenderManager().getModel(state);
        List<BlockModelPart> parts = model.getParts(Random.create());

        // Collect all unique sprites for this face across all parts
        java.util.List<Sprite> sprites = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (BlockModelPart part : parts) {
            List<BakedQuad> faceQuads = part.getQuads(face);
            if (faceQuads != null) {
                for (BakedQuad quad : faceQuads) {
                    Sprite s = quad.sprite();
                    if (!s.getContents().getId().getPath().equals("missingno")) {
                        String key = s.getContents().getId().toString();
                        if (seen.add(key)) {
                            sprites.add(s);
                        }
                    }
                }
            }
        }

        if (sprites.isEmpty()) {
            // Try null direction (general quads)
            for (BlockModelPart part : parts) {
                List<BakedQuad> generalQuads = part.getQuads(null);
                if (generalQuads != null) {
                    for (BakedQuad quad : generalQuads) {
                        Sprite s = quad.sprite();
                        if (!s.getContents().getId().getPath().equals("missingno")) {
                            String key = s.getContents().getId().toString();
                            if (seen.add(key)) {
                                sprites.add(s);
                            }
                        }
                    }
                }
            }
        }

        if (quadIndex < sprites.size()) {
            return extractFromSprite(sprites.get(quadIndex));
        }

        // Fallback: particle sprite
        if (quadIndex == 0) {
            Sprite particle = model.particleSprite();
            if (particle != null && !particle.getContents().getId().getPath().equals("missingno")) {
                return extractFromSprite(particle);
            }
        }

        return null;
    }

    /**
     * Count how many unique texture layers a face has (1 = normal, 2+ = has overlay e.g. grass).
     */
    public static int getFaceTextureCount(BlockState state, Direction face) {
        MinecraftClient client = MinecraftClient.getInstance();
        var model = client.getBlockRenderManager().getModel(state);
        List<BlockModelPart> parts = model.getParts(Random.create());

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (BlockModelPart part : parts) {
            List<BakedQuad> faceQuads = part.getQuads(face);
            if (faceQuads != null) {
                for (BakedQuad quad : faceQuads) {
                    if (!quad.sprite().getContents().getId().getPath().equals("missingno")) {
                        seen.add(quad.sprite().getContents().getId().toString());
                    }
                }
            }
        }
        return Math.max(1, seen.size());
    }

    private static BlockFaceTexture extractFromSprite(Sprite sprite) {
        SpriteContents contents = sprite.getContents();
        int w = contents.getWidth();
        int h = contents.getHeight();

        NativeImage image = ((SpriteContentsAccessor) contents).getImage();

        int[][] pixels = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                pixels[x][y] = image.getColorArgb(x, y);
            }
        }

        Identifier spriteId = contents.getId();
        Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        return new BlockFaceTexture(textureId, pixels, w, h);
    }
}
