package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
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
        SpriteAtlasTexture atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Sprite directSprite = atlas.getSprite(directSpriteId);

        if (directSprite != null && !directSprite.getContents().getId().getPath().equals("missingno")) {
            System.out.println("[TextureEditor] Found sprite directly in atlas: " + directSprite.getContents().getId());
            return extractFromSprite(directSprite);
        }

        // Strategy 2: Try via block model quads (for block items etc.)
        System.out.println("[TextureEditor] Direct sprite not found, trying BakedModel approach...");

        // Try block item first
        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
        if (block != net.minecraft.block.Blocks.AIR) {
            var model = client.getBlockRenderManager().getModel(block.getDefaultState());
            if (model != null) {
                List<BlockModelPart> parts = model.getParts(Random.create());
                for (BlockModelPart part : parts) {
                    for (Direction dir : Direction.values()) {
                        List<BakedQuad> quads = part.getQuads(dir);
                        if (quads != null && !quads.isEmpty()) {
                            Sprite sprite = quads.getFirst().sprite();
                            if (!sprite.getContents().getId().getPath().equals("missingno")) {
                                System.out.println("[TextureEditor] Found sprite via block model: " + sprite.getContents().getId());
                                return extractFromSprite(sprite);
                            }
                        }
                    }
                }
            }
        }

        // Strategy 3: Fallback to Particle Sprite (fixes Leaves and other complex blocks)
        if (block != net.minecraft.block.Blocks.AIR) {
            var blockModel = client.getBlockRenderManager().getModel(block.getDefaultState());
            if (blockModel != null) {
                Sprite particle = blockModel.particleSprite();
                if (particle != null && !particle.getContents().getId().getPath().equals("missingno")) {
                    System.out.println("[TextureEditor] Found sprite via particle texture: " + particle.getContents().getId());
                    return extractFromSprite(particle);
                }
            }
        }

        System.out.println("[TextureEditor] FAILED to find valid sprite for item: " + itemId);

        // Strategy 4: Try loading entity/special texture paths directly from resources
        // This handles items like elytra, shields, signs, banners, etc. that use entity textures
        Identifier entityTexture = getSpecialItemTexturePath(itemId);
        if (entityTexture != null) {
            try {
                var optResource = client.getResourceManager().getResource(entityTexture);
                if (optResource.isPresent()) {
                    java.io.InputStream stream = optResource.get().getInputStream();
                    net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage.read(stream);
                    int w = image.getWidth();
                    int h = image.getHeight();
                    int[][] pixels = new int[w][h];
                    for (int x = 0; x < w; x++)
                        for (int y = 0; y < h; y++)
                            pixels[x][y] = image.getColorArgb(x, y);
                    image.close();
                    stream.close();
                    // Use the entity texture path itself as both IDs (not atlas-based)
                    Identifier spriteId = Identifier.of(entityTexture.getNamespace(),
                            entityTexture.getPath().replace("textures/", "").replace(".png", ""));
                    System.out.println("[TextureEditor] Found special item texture: " + entityTexture);
                    return new ItemTexture(entityTexture, spriteId, pixels, w, h);
                }
            } catch (Exception e) {
                System.out.println("[TextureEditor] Failed to load special texture: " + entityTexture + " - " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Get known texture paths for special items that don't use the block atlas.
     */
    private static Identifier getSpecialItemTexturePath(Identifier itemId) {
        String path = itemId.getPath();
        String ns = itemId.getNamespace();
        return switch (path) {
            case "elytra" -> Identifier.of(ns, "textures/entity/elytra.png");
            case "shield" -> Identifier.of(ns, "textures/entity/shield/shield_base.png");
            case "trident" -> Identifier.of(ns, "textures/entity/trident.png");
            case "decorated_pot" -> Identifier.of(ns, "textures/entity/decorated_pot/decorated_pot_base.png");
            case "conduit" -> Identifier.of(ns, "textures/entity/conduit/base.png");
            case "bell" -> Identifier.of(ns, "textures/entity/bell/bell_body.png");
            default -> {
                // Signs
                if (path.endsWith("_sign") || path.endsWith("_hanging_sign")) {
                    String wood = path.replace("_hanging_sign", "").replace("_wall_sign", "").replace("_sign", "");
                    if (path.contains("hanging")) {
                        yield Identifier.of(ns, "textures/entity/signs/hanging/" + wood + ".png");
                    }
                    yield Identifier.of(ns, "textures/entity/signs/" + wood + ".png");
                }
                // Banners
                if (path.endsWith("_banner")) {
                    yield Identifier.of(ns, "textures/entity/banner_base.png");
                }
                // Beds
                if (path.endsWith("_bed")) {
                    String color = path.replace("_bed", "");
                    yield Identifier.of(ns, "textures/entity/bed/" + color + ".png");
                }
                // Chests
                if (path.equals("chest") || path.equals("trapped_chest")) {
                    yield Identifier.of(ns, "textures/entity/chest/normal.png");
                }
                if (path.equals("ender_chest")) {
                    yield Identifier.of(ns, "textures/entity/chest/ender.png");
                }
                // Boats
                if (path.contains("boat")) {
                    String wood = path.replace("_chest_boat", "").replace("_boat", "");
                    if (path.contains("chest")) {
                        yield Identifier.of(ns, "textures/entity/chest_boat/" + wood + ".png");
                    }
                    yield Identifier.of(ns, "textures/entity/boat/" + wood + ".png");
                }
                // Shulker boxes
                if (path.contains("shulker_box")) {
                    if (path.equals("shulker_box")) {
                        yield Identifier.of(ns, "textures/entity/shulker/shulker.png");
                    }
                    String color = path.replace("_shulker_box", "");
                    yield Identifier.of(ns, "textures/entity/shulker/shulker_" + color + ".png");
                }
                yield null;
            }
        };
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
