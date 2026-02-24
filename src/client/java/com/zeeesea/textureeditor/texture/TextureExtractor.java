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
     * Extract the texture for a given block state and face direction.
     */
    public static BlockFaceTexture extract(BlockState state, Direction face) {
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getBlockRenderManager().getModel(state);

        // Get quads for the specific face
        List<BakedQuad> quads = model.getQuads(state, face, Random.create());
        if (quads.isEmpty()) {
            // Try null direction (for full-cube models that use null direction quads)
            quads = model.getQuads(state, null, Random.create());
        }
        if (quads.isEmpty()) {
            // Try any face
            for (Direction dir : Direction.values()) {
                quads = model.getQuads(state, dir, Random.create());
                if (!quads.isEmpty()) break;
            }
        }

        if (!quads.isEmpty()) {
            BakedQuad quad = quads.get(0);
            Sprite sprite = quad.getSprite();
            if (!sprite.getContents().getId().getPath().equals("missingno")) {
                return extractFromSprite(sprite);
            }
        }

        // Fallback: particle sprite (works for leaves, signs, etc.)
        Sprite particle = model.getParticleSprite();
        if (particle != null && !particle.getContents().getId().getPath().equals("missingno")) {
            return extractFromSprite(particle);
        }

        return null;
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
