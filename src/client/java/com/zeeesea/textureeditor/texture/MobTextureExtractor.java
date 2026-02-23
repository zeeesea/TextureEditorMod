package com.zeeesea.textureeditor.texture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts the texture from a mob/entity.
 */
public class MobTextureExtractor {

    public record MobTexture(Identifier textureId, int[][] pixels, int width, int height, String entityName) {}

    /**
     * Extract texture data from a living entity.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static MobTexture extract(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        var renderer = client.getEntityRenderDispatcher().getRenderer(entity);

        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return null;
        }

        // In 1.21.4 getTexture() requires a render state, not an entity.
        // Create the render state and update it from the entity.
        LivingEntityRenderState renderState = (LivingEntityRenderState) livingRenderer.createRenderState();
        livingRenderer.updateRenderState(entity, renderState, 0f);

        Identifier textureId = (Identifier) livingRenderer.getTexture(renderState);
        if (textureId == null) return null;

        // Load the texture as NativeImage from resources
        try {
            InputStream stream = client.getResourceManager()
                    .getResource(textureId)
                    .orElseThrow()
                    .getInputStream();

            NativeImage image = NativeImage.read(stream);
            int w = image.getWidth();
            int h = image.getHeight();

            int[][] pixels = new int[w][h];
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    pixels[x][y] = image.getColorArgb(x, y);
                }
            }

            image.close();
            stream.close();

            String name = entity.getType().getName().getString();
            return new MobTexture(textureId, pixels, w, h, name);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
