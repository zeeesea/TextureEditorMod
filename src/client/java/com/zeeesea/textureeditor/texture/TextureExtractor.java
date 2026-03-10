package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
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
     * Extract ALL unique textures for a given block state and face direction.
     * This includes overlay textures (e.g. grass_block_side_overlay) that are
     * rendered as separate quads on the same face.
     */
    public static List<BlockFaceTexture> extractAll(BlockState state, Direction face) {
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getBlockRenderManager().getModel(state);
        List<BlockFaceTexture> results = new java.util.ArrayList<>();
        java.util.Set<Identifier> seen = new java.util.HashSet<>();

        // Get quads for the specific face
        List<BakedQuad> quads = model.getQuads(state, face, Random.create());

        // Also check null-direction quads (some overlay quads use null direction)
        List<BakedQuad> nullQuads = model.getQuads(state, null, Random.create());
        List<BakedQuad> allQuads = new java.util.ArrayList<>(quads);
        allQuads.addAll(nullQuads);

        for (BakedQuad quad : allQuads) {
            Sprite sprite = quad.getSprite();
            if (sprite.getContents().getId().getPath().equals("missingno")) continue;
            Identifier spriteId = sprite.getContents().getId();
            Identifier textureId = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
            if (seen.contains(textureId)) continue;
            seen.add(textureId);
            BlockFaceTexture tex = extractFromSprite(sprite);
            if (tex != null) {
                results.add(tex);
                System.out.println("[TextureEditor] extractAll: found " + textureId + " for face " + face);
            }
        }

        // If we still have nothing, try particle sprite
        if (results.isEmpty()) {
            Sprite particle = model.getParticleSprite();
            if (particle != null && !particle.getContents().getId().getPath().equals("missingno")) {
                BlockFaceTexture tex = extractFromSprite(particle);
                if (tex != null) results.add(tex);
            }
        }

        return results;
    }

    /**
     * Extract the texture for a given block state and face direction.
     * Returns only the first (primary) texture.
     */
    public static BlockFaceTexture extract(BlockState state, Direction face) {
        List<BlockFaceTexture> all = extractAll(state, face);
        return all.isEmpty() ? null : all.get(0);
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
