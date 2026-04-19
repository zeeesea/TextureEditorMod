ackage com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

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
        Minecraft client = Minecraft.getInstance();
        Identifier itemId = BuiltInRegistries.ITEM.getId(stack.getItem());
        System.out.println("[TextureEditor] Extracting item texture for: " + itemId);

        // Strategy 1: Handle spawn eggs specially ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â they share the 'item/spawn_egg' sprite with tints
        if (stack.getItem() instanceof SpawnEggItem) {
            System.out.println("[TextureEditor] Spawn egg detected: " + itemId);
            // Try items atlas first, then block atlas
            Identifier spawnEggSpriteId = new Identifier("minecraft", "item/spawn_egg");
            Sprite eggSprite = findSpriteInAnyAtlas(client, spawnEggSpriteId);
            if (eggSprite != null) {
                System.out.println("[TextureEditor] Found spawn egg sprite in atlas: " + eggSprite.getContents().getId());
                return extractFromSprite(eggSprite);
            }
            // Also try loading from resources directly
            Identifier eggTexId = new Identifier("minecraft", "textures/item/spawn_egg.png");
            Identifier eggSprId = new Identifier("minecraft", "item/spawn_egg");
            ItemTexture eggResult = tryLoadFromResource(client, eggTexId, eggSprId);
            if (eggResult != null) return eggResult;
            System.out.println("[TextureEditor] Spawn egg sprite not found in any atlas");
        }

        // Strategy 2: Try to get sprite directly from ITEMS atlas (1.21.11+ has separate items atlas)
        Identifier directSpriteId = new Identifier(itemId.getNamespace(), "item/" + itemId.getPath());
        try {
            var tex = client.getTextureManager().getTexture(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE);
            if (tex instanceof SpriteAtlasTexture sat) {
                Sprite directSprite = sat.getSprite(directSpriteId);
                if (directSprite != null && !directSprite.getContents().getId().getPath().equals("missingno")) {
                    System.out.println("[TextureEditor] Found sprite in ITEMS atlas: " + directSprite.getContents().getId());
                    return extractFromSprite(directSprite);
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Items atlas lookup failed: " + e.getMessage());
        }

        // Strategy 3: Try block atlas (for block items)
        SpriteAtlasTexture blockAtlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Sprite directSprite = blockAtlas.getSprite(directSpriteId);
        if (directSprite != null && !directSprite.getContents().getId().getPath().equals("missingno")) {
            System.out.println("[TextureEditor] Found sprite in BLOCK atlas: " + directSprite.getContents().getId());
            return extractFromSprite(directSprite);
        }

        // Strategy 3: Try via block model quads (for block items etc.)
        System.out.println("[TextureEditor] Direct sprite not found in either atlas, trying BakedModel approach...");

        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.getBlockFromItem(stack.getItem());
        if (block != net.minecraft.world.level.block.Blocks.AIR) {
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

        // Strategy 4: Fallback to Particle Sprite
        if (block != net.minecraft.world.level.block.Blocks.AIR) {
            var blockModel = client.getBlockRenderManager().getModel(block.getDefaultState());
            if (blockModel != null) {
                Sprite particle = blockModel.particleSprite();
                if (particle != null && !particle.getContents().getId().getPath().equals("missingno")) {
                    System.out.println("[TextureEditor] Found sprite via particle texture: " + particle.getContents().getId());
                    return extractFromSprite(particle);
                }
            }
        }

        // Strategy 5: Try loading texture directly from resources (textures/item/<name>.png)
        Identifier resourceTexId = new Identifier(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
        System.out.println("[TextureEditor] Trying resource manager for: " + resourceTexId);
        ItemTexture resourceResult = tryLoadFromResource(client, resourceTexId, directSpriteId);
        if (resourceResult != null) return resourceResult;

        System.out.println("[TextureEditor] FAILED to find valid sprite for item: " + itemId);

        // Strategy 6: Try loading entity/special texture paths directly from resources
        Identifier entityTexture = getSpecialItemTexturePath(itemId);
        if (entityTexture != null) {
            Identifier sprId = new Identifier(entityTexture.getNamespace(),
                    entityTexture.getPath().replace("textures/", "").replace(".png", ""));
            ItemTexture specialResult = tryLoadFromResource(client, entityTexture, sprId);
            if (specialResult != null) {
                System.out.println("[TextureEditor] Found special item texture: " + entityTexture);
                return specialResult;
            }
        }

        return null;
    }

    /**
     * Try to load a texture from the resource manager at the given path.
     */
    private static ItemTexture tryLoadFromResource(Minecraft client, Identifier textureId, Identifier spriteId) {
        try {
            var optResource = client.getResourceManager().getResource(textureId);
            if (optResource.isPresent()) {
                java.io.InputStream stream = optResource.get().getInputStream();
                NativeImage image = NativeImage.read(stream);
                int w = image.getWidth();
                int h = image.getHeight();
                int[][] pixels = new int[w][h];
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        pixels[x][y] = image.getColorArgb(x, y);
                image.close();
                stream.close();
                System.out.println("[TextureEditor] Loaded from resource: " + textureId + " size=" + w + "x" + h);
                return new ItemTexture(textureId, spriteId, pixels, w, h);
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load resource: " + textureId + " - " + e.getMessage());
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
            case "elytra" -> new Identifier(ns, "textures/entity/elytra.png");
            case "shield" -> new Identifier(ns, "textures/entity/shield/shield_base.png");
            case "trident" -> new Identifier(ns, "textures/entity/trident.png");
            case "decorated_pot" -> new Identifier(ns, "textures/entity/decorated_pot/decorated_pot_base.png");
            case "conduit" -> new Identifier(ns, "textures/entity/conduit/base.png");
            case "bell" -> new Identifier(ns, "textures/entity/bell/bell_body.png");
            default -> {
                // Signs
                if (path.endsWith("_sign") || path.endsWith("_hanging_sign")) {
                    String wood = path.replace("_hanging_sign", "").replace("_wall_sign", "").replace("_sign", "");
                    if (path.contains("hanging")) {
                        yield new Identifier(ns, "textures/entity/signs/hanging/" + wood + ".png");
                    }
                    yield new Identifier(ns, "textures/entity/signs/" + wood + ".png");
                }
                // Banners
                if (path.endsWith("_banner")) {
                    yield new Identifier(ns, "textures/entity/banner_base.png");
                }
                // Beds
                if (path.endsWith("_bed")) {
                    String color = path.replace("_bed", "");
                    yield new Identifier(ns, "textures/entity/bed/" + color + ".png");
                }
                // Chests
                if (path.equals("chest") || path.equals("trapped_chest")) {
                    yield new Identifier(ns, "textures/entity/chest/normal.png");
                }
                if (path.equals("ender_chest")) {
                    yield new Identifier(ns, "textures/entity/chest/ender.png");
                }
                // Boats
                if (path.contains("boat")) {
                    String wood = path.replace("_chest_boat", "").replace("_boat", "");
                    if (path.contains("chest")) {
                        yield new Identifier(ns, "textures/entity/chest_boat/" + wood + ".png");
                    }
                    yield new Identifier(ns, "textures/entity/boat/" + wood + ".png");
                }
                // Shulker boxes
                if (path.contains("shulker_box")) {
                    if (path.equals("shulker_box")) {
                        yield new Identifier(ns, "textures/entity/shulker/shulker.png");
                    }
                    String color = path.replace("_shulker_box", "");
                    yield new Identifier(ns, "textures/entity/shulker/shulker_" + color + ".png");
                }
                yield null;
            }
        };
    }

    /**
     * Find a sprite in either the ITEMS or BLOCK atlas.
     */
    private static Sprite findSpriteInAnyAtlas(Minecraft client, Identifier spriteId) {
        // Try items atlas
        try {
            var tex = client.getTextureManager().getTexture(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE);
            if (tex instanceof SpriteAtlasTexture sat) {
                Sprite s = sat.getSprite(spriteId);
                if (s != null && !s.getContents().getId().getPath().equals("missingno")) return s;
            }
        } catch (Exception ignored) {}
        // Try block atlas
        try {
            SpriteAtlasTexture blockAtlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            Sprite s = blockAtlas.getSprite(spriteId);
            if (s != null && !s.getContents().getId().getPath().equals("missingno")) return s;
        } catch (Exception ignored) {}
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
        Identifier textureId = new Identifier(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        System.out.println("[TextureEditor] Extracted: textureId=" + textureId + " spriteId=" + spriteId + " size=" + w + "x" + h);
        return new ItemTexture(textureId, spriteId, pixels, w, h);
    }
}


