package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Extracts texture data from an item's baked model.
 * Items use the same block atlas for their sprites.
 */
public class ItemTextureExtractor {

    public record ItemTexture(Identifier textureId, Identifier spriteId, int[][] pixels, int width, int height) {}

    /**
     * Extract the texture for a given item stack.
     */
    public static ItemTexture extract(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        System.out.println("[TextureEditor] Extracting item texture for: " + itemId);

        // Strategy 1: Try to get sprite directly from atlas using item texture path
        // Most items in 1.21.4 have their sprite at "item/<name>" in the block atlas
        Identifier directSpriteId = Identifier.of(itemId.getNamespace(), "item/" + itemId.getPath());
        Sprite directSprite = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(directSpriteId);

        if (directSprite != null && !directSprite.getContents().getId().getPath().equals("missingno")) {
            System.out.println("[TextureEditor] Found sprite directly in atlas: " + directSprite.getContents().getId());
            return extractFromSprite(directSprite);
        }

        // Strategy 2: Try via BakedModel quads (for block items etc.)
        System.out.println("[TextureEditor] Direct sprite not found, trying BakedModel approach...");

        // Try block item first
        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
        if (block != net.minecraft.block.Blocks.AIR) {
            BakedModel model = client.getBlockRenderManager().getModel(block.getDefaultState());
            if (model != null) {
                // Try null direction first (flat items), then each face
                List<BakedQuad> quads = model.getQuads(null, null, Random.create());
                if (quads.isEmpty()) {
                    for (Direction dir : Direction.values()) {
                        quads = model.getQuads(block.getDefaultState(), dir, Random.create());
                        if (!quads.isEmpty()) break;
                    }
                }
                if (!quads.isEmpty()) {
                    Sprite sprite = quads.getFirst().getSprite();
                    if (!sprite.getContents().getId().getPath().equals("missingno")) {
                        System.out.println("[TextureEditor] Found sprite via block model: " + sprite.getContents().getId());
                        return extractFromSprite(sprite);
                    }
                }
            }
        }

        // Strategy 3: Try model via BakedModelManager
        Identifier modelId = Identifier.of(itemId.getNamespace(), "item/" + itemId.getPath());
        BakedModel model = client.getBakedModelManager().getModel(modelId);
        if (model != null) {
            List<BakedQuad> quads = model.getQuads(null, null, Random.create());
            if (quads.isEmpty()) {
                for (Direction dir : Direction.values()) {
                    quads = model.getQuads(null, dir, Random.create());
                    if (!quads.isEmpty()) break;
                }
            }
            if (!quads.isEmpty()) {
                Sprite sprite = quads.getFirst().getSprite();
                if (!sprite.getContents().getId().getPath().equals("missingno")) {
                    System.out.println("[TextureEditor] Found sprite via model manager: " + sprite.getContents().getId());
                    return extractFromSprite(sprite);
                }
            }
        }

        System.out.println("[TextureEditor] FAILED to find valid sprite for item: " + itemId);
        return null;
    }

    private static ItemTexture extractFromSprite(Sprite sprite) {
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

        System.out.println("[TextureEditor] Extracted: textureId=" + textureId + " spriteId=" + spriteId + " size=" + w + "x" + h);
        return new ItemTexture(textureId, spriteId, pixels, w, h);
    }
}
