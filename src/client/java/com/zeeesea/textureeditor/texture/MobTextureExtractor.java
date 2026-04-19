ackage com.zeeesea.textureeditor.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the texture from a mob/entity.
 * Uses resource path conventions since EntityRenderer.getTexture was removed in 1.21.4.
 */
public class MobTextureExtractor {

    public record MobTexture(Identifier textureId, int[][] pixels, int width, int height, String entityName) {}

    /**
     * Extract texture data from an entity.
     */
    public static MobTexture extract(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        EntityType<?> type = entity.getType();
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getId(type);
        String name = type.getName().getString();

        System.out.println("[TextureEditor] Extracting mob texture for: " + entityId);

        // Step 1: Try to extract texture path from entity variant (1.21.2+ variant system)
        Identifier variantTexture = tryExtractVariantTexture(entity);
        if (variantTexture != null) {
            System.out.println("[TextureEditor] Variant texture detected: " + variantTexture);
            MobTexture result = tryLoadTexture(client, variantTexture, name);
            if (result != null) return result;
        }

        // Step 2: Build candidate texture paths from hardcoded list
        List<Identifier> candidates = buildTexturePaths(entityId);

        for (Identifier textureId : candidates) {
            System.out.println("[TextureEditor] Trying texture path: " + textureId);
            MobTexture result = tryLoadTexture(client, textureId, name);
            if (result != null) return result;
        }

        System.out.println("[TextureEditor] FAILED to find texture for entity: " + entityId);
        System.out.println("[TextureEditor] Total candidates tried: " + (candidates.size() + (variantTexture != null ? 1 : 0)));
        return null;
    }

    /**
     * Try to extract texture path from entity's variant system via reflection.
     * In 1.21.2+, entities like Cow, Pig, Chicken use data-driven variants
     * (CowVariant, PigVariant, ChickenVariant) that contain the texture path.
     */
    private static Identifier tryExtractVariantTexture(Entity entity) {
        try {
            // Try getVariant() -> RegistryEntry -> value() -> modelAndTexture() -> asset() -> texturePath()
            // This works for CowEntity, PigEntity, ChickenEntity etc. in 1.21.2+
            Method getVariant = null;
            for (Method m : entity.getClass().getMethods()) {
                if (m.getName().equals("getVariant") && m.getParameterCount() == 0) {
                    getVariant = m;
                    break;
                }
            }
            if (getVariant == null) {
                System.out.println("[TextureEditor] No getVariant() method found on " + entity.getClass().getSimpleName());
                return null;
            }

            Object variantEntry = getVariant.invoke(entity);
            System.out.println("[TextureEditor] getVariant() returned: " + (variantEntry != null ? variantEntry.getClass().getSimpleName() : "null"));
            if (variantEntry == null) return null;

            // RegistryEntry.value()
            Object variant = null;
            for (Method m : variantEntry.getClass().getMethods()) {
                if (m.getName().equals("value") && m.getParameterCount() == 0) {
                    variant = m.invoke(variantEntry);
                    break;
                }
            }
            if (variant == null) {
                System.out.println("[TextureEditor] variant.value() returned null");
                return null;
            }
            System.out.println("[TextureEditor] Variant value: " + variant.getClass().getSimpleName());

            // Try modelAndTexture() -> asset() -> texturePath()
            Object modelAndTexture = null;
            for (Method m : variant.getClass().getMethods()) {
                if (m.getName().equals("modelAndTexture") && m.getParameterCount() == 0) {
                    modelAndTexture = m.invoke(variant);
                    break;
                }
            }
            if (modelAndTexture != null) {
                System.out.println("[TextureEditor] modelAndTexture: " + modelAndTexture.getClass().getSimpleName());
                Object asset = null;
                for (Method m : modelAndTexture.getClass().getMethods()) {
                    if (m.getName().equals("asset") && m.getParameterCount() == 0) {
                        asset = m.invoke(modelAndTexture);
                        break;
                    }
                }
                if (asset != null) {
                    System.out.println("[TextureEditor] asset: " + asset.getClass().getSimpleName());
                    for (Method m : asset.getClass().getMethods()) {
                        if (m.getName().equals("texturePath") && m.getParameterCount() == 0) {
                            Object result = m.invoke(asset);
                            if (result instanceof Identifier texId) {
                                System.out.println("[TextureEditor] Found variant texturePath: " + texId);
                                return texId;
                            }
                        }
                    }
                }
            }

            // Alternative: try texture() directly on variant (some variants use this pattern)
            for (Method m : variant.getClass().getMethods()) {
                if ((m.getName().equals("texture") || m.getName().equals("texturePath"))
                        && m.getParameterCount() == 0
                        && Identifier.class.isAssignableFrom(m.getReturnType())) {
                    Identifier texId = (Identifier) m.invoke(variant);
                    if (texId != null) {
                        System.out.println("[TextureEditor] Found variant texture directly: " + texId);
                        return texId;
                    }
                }
            }

            // Log all methods on the variant for debugging
            System.out.println("[TextureEditor] Variant methods on " + variant.getClass().getSimpleName() + ":");
            for (Method m : variant.getClass().getMethods()) {
                if (m.getDeclaringClass() != Object.class) {
                    System.out.println("[TextureEditor]   " + m.getName() + "() -> " + m.getReturnType().getSimpleName());
                }
            }

        } catch (Exception e) {
            System.out.println("[TextureEditor] Variant extraction failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }

    private static MobTexture tryLoadTexture(Minecraft client, Identifier textureId, String name) {
        var optResource = client.getResourceManager().getResource(textureId);
        if (optResource.isPresent()) {
            try {
                InputStream stream = optResource.get().getInputStream();
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

                System.out.println("[TextureEditor] Found mob texture: " + textureId + " size=" + w + "x" + h);
                return new MobTexture(textureId, pixels, w, h, name);

            } catch (IOException e) {
                System.out.println("[TextureEditor] Failed to read texture: " + textureId + " - " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Build a list of candidate texture paths for a given entity type.
     * Minecraft entity textures follow various conventions.
     */
    private static List<Identifier> buildTexturePaths(Identifier entityId) {
        List<Identifier> paths = new ArrayList<>();
        String ns = entityId.getNamespace();
        String path = entityId.getPath(); // e.g. "zombie", "skeleton", "armor_stand"

        // Standard: textures/entity/<name>/<name>.png (most mobs)
        paths.add(new Identifier(ns, "textures/entity/" + path + "/" + path + ".png"));

        // Common variant: textures/entity/<name>.png (simpler entities)
        paths.add(new Identifier(ns, "textures/entity/" + path + ".png"));

        // Special cases for known entity texture paths
        switch (path) {
            case "iron_golem" -> paths.add(0, new Identifier(ns, "textures/entity/iron_golem/iron_golem.png"));
            case "snow_golem" -> paths.add(0, new Identifier(ns, "textures/entity/snow_golem.png"));
            case "armor_stand" -> paths.add(0, new Identifier(ns, "textures/entity/armor_stand/wood.png"));
            case "ender_dragon" -> paths.add(0, new Identifier(ns, "textures/entity/enderdragon/dragon.png"));
            case "wither" -> paths.add(0, new Identifier(ns, "textures/entity/wither/wither.png"));
            case "guardian" -> paths.add(0, new Identifier(ns, "textures/entity/guardian.png"));
            case "elder_guardian" -> paths.add(0, new Identifier(ns, "textures/entity/guardian_elder.png"));
            case "horse" -> paths.add(0, new Identifier(ns, "textures/entity/horse/horse_white.png"));
            case "donkey" -> paths.add(0, new Identifier(ns, "textures/entity/horse/donkey.png"));
            case "mule" -> paths.add(0, new Identifier(ns, "textures/entity/horse/mule.png"));
            case "skeleton_horse" -> paths.add(0, new Identifier(ns, "textures/entity/horse/horse_skeleton.png"));
            case "zombie_horse" -> paths.add(0, new Identifier(ns, "textures/entity/horse/horse_zombie.png"));
            case "llama" -> paths.add(0, new Identifier(ns, "textures/entity/llama/creamy.png"));
            case "trader_llama" -> paths.add(0, new Identifier(ns, "textures/entity/llama/creamy.png"));
            case "cat" -> paths.add(0, new Identifier(ns, "textures/entity/cat/tabby.png"));
            case "wolf" -> paths.add(0, new Identifier(ns, "textures/entity/wolf/wolf.png"));
            case "fox" -> paths.add(0, new Identifier(ns, "textures/entity/fox/fox.png"));
            case "parrot" -> paths.add(0, new Identifier(ns, "textures/entity/parrot/parrot_red_blue.png"));
            case "rabbit" -> paths.add(0, new Identifier(ns, "textures/entity/rabbit/brown.png"));
            case "villager" -> paths.add(0, new Identifier(ns, "textures/entity/villager/villager.png"));
            case "zombie_villager" -> paths.add(0, new Identifier(ns, "textures/entity/zombie_villager/zombie_villager.png"));
            case "wandering_trader" -> paths.add(0, new Identifier(ns, "textures/entity/wandering_trader.png"));
            case "witch" -> paths.add(0, new Identifier(ns, "textures/entity/witch.png"));
            case "shulker" -> paths.add(0, new Identifier(ns, "textures/entity/shulker/shulker.png"));
            case "slime" -> paths.add(0, new Identifier(ns, "textures/entity/slime/slime.png"));
            case "magma_cube" -> paths.add(0, new Identifier(ns, "textures/entity/slime/magmacube.png"));
            case "ghast" -> paths.add(0, new Identifier(ns, "textures/entity/ghast/ghast.png"));
            case "blaze" -> paths.add(0, new Identifier(ns, "textures/entity/blaze.png"));
            case "enderman" -> paths.add(0, new Identifier(ns, "textures/entity/enderman/enderman.png"));
            case "endermite" -> paths.add(0, new Identifier(ns, "textures/entity/endermite.png"));
            case "silverfish" -> paths.add(0, new Identifier(ns, "textures/entity/silverfish.png"));
            case "bat" -> paths.add(0, new Identifier(ns, "textures/entity/bat.png"));
            case "phantom" -> paths.add(0, new Identifier(ns, "textures/entity/phantom.png"));
            case "panda" -> paths.add(0, new Identifier(ns, "textures/entity/panda/panda.png"));
            case "bee" -> paths.add(0, new Identifier(ns, "textures/entity/bee/bee.png"));
            case "piglin" -> paths.add(0, new Identifier(ns, "textures/entity/piglin/piglin.png"));
            case "piglin_brute" -> paths.add(0, new Identifier(ns, "textures/entity/piglin/piglin_brute.png"));
            case "zombified_piglin" -> paths.add(0, new Identifier(ns, "textures/entity/piglin/zombified_piglin.png"));
            case "hoglin" -> paths.add(0, new Identifier(ns, "textures/entity/hoglin/hoglin.png"));
            case "zoglin" -> paths.add(0, new Identifier(ns, "textures/entity/hoglin/zoglin.png"));
            case "strider" -> paths.add(0, new Identifier(ns, "textures/entity/strider/strider.png"));
            case "ravager" -> paths.add(0, new Identifier(ns, "textures/entity/illager/ravager.png"));
            case "pillager" -> paths.add(0, new Identifier(ns, "textures/entity/illager/pillager.png"));
            case "vindicator" -> paths.add(0, new Identifier(ns, "textures/entity/illager/vindicator.png"));
            case "evoker" -> paths.add(0, new Identifier(ns, "textures/entity/illager/evoker.png"));
            case "illusioner" -> paths.add(0, new Identifier(ns, "textures/entity/illager/illusioner.png"));
            case "vex" -> paths.add(0, new Identifier(ns, "textures/entity/illager/vex.png"));
            case "warden" -> paths.add(0, new Identifier(ns, "textures/entity/warden/warden.png"));
            case "allay" -> paths.add(0, new Identifier(ns, "textures/entity/allay/allay.png"));
            case "frog" -> paths.add(0, new Identifier(ns, "textures/entity/frog/temperate_frog.png"));
            case "tadpole" -> paths.add(0, new Identifier(ns, "textures/entity/tadpole/tadpole.png"));
            case "camel" -> paths.add(0, new Identifier(ns, "textures/entity/camel/camel.png"));
            case "sniffer" -> paths.add(0, new Identifier(ns, "textures/entity/sniffer/sniffer.png"));
            case "breeze" -> paths.add(0, new Identifier(ns, "textures/entity/breeze/breeze.png"));
            case "bogged" -> paths.add(0, new Identifier(ns, "textures/entity/skeleton/bogged.png"));
            case "axolotl" -> paths.add(0, new Identifier(ns, "textures/entity/axolotl/axolotl_lucy.png"));
            case "glow_squid" -> paths.add(0, new Identifier(ns, "textures/entity/squid/glow_squid.png"));
            case "squid" -> paths.add(0, new Identifier(ns, "textures/entity/squid/squid.png"));
            case "goat" -> paths.add(0, new Identifier(ns, "textures/entity/goat/goat.png"));
            case "tropical_fish" -> paths.add(0, new Identifier(ns, "textures/entity/fish/tropical_a.png"));
            case "salmon" -> paths.add(0, new Identifier(ns, "textures/entity/fish/salmon.png"));
            case "cod" -> paths.add(0, new Identifier(ns, "textures/entity/fish/cod.png"));
            case "pufferfish" -> paths.add(0, new Identifier(ns, "textures/entity/fish/pufferfish.png"));
            case "dolphin" -> paths.add(0, new Identifier(ns, "textures/entity/dolphin.png"));
            case "turtle" -> paths.add(0, new Identifier(ns, "textures/entity/turtle/big_sea_turtle.png"));
            case "ocelot" -> paths.add(0, new Identifier(ns, "textures/entity/cat/ocelot.png"));
            case "mooshroom" -> {
                paths.add(0, new Identifier(ns, "textures/entity/cow/red_mooshroom.png"));
                paths.add(new Identifier(ns, "textures/entity/cow/brown_mooshroom.png"));
            }
            case "cow" -> {
                paths.add(0, new Identifier(ns, "textures/entity/cow/temperate_cow.png"));
                paths.add(new Identifier(ns, "textures/entity/cow/cold_cow.png"));
                paths.add(new Identifier(ns, "textures/entity/cow/warm_cow.png"));
                paths.add(new Identifier(ns, "textures/entity/cow/temperate.png"));
                paths.add(new Identifier(ns, "textures/entity/cow/cow.png"));
            }
            case "pig" -> {
                paths.add(0, new Identifier(ns, "textures/entity/pig/temperate_pig.png"));
                paths.add(new Identifier(ns, "textures/entity/pig/cold_pig.png"));
                paths.add(new Identifier(ns, "textures/entity/pig/warm_pig.png"));
                paths.add(new Identifier(ns, "textures/entity/pig/temperate.png"));
                paths.add(new Identifier(ns, "textures/entity/pig/pig.png"));
            }
            case "sheep" -> paths.add(0, new Identifier(ns, "textures/entity/sheep/sheep.png"));
            case "chicken" -> {
                paths.add(0, new Identifier(ns, "textures/entity/chicken/temperate_chicken.png"));
                paths.add(new Identifier(ns, "textures/entity/chicken/cold_chicken.png"));
                paths.add(new Identifier(ns, "textures/entity/chicken/warm_chicken.png"));
                paths.add(new Identifier(ns, "textures/entity/chicken/temperate.png"));
                paths.add(new Identifier(ns, "textures/entity/chicken/chicken.png"));
            }
            case "creeper" -> paths.add(0, new Identifier(ns, "textures/entity/creeper/creeper.png"));
            case "skeleton" -> paths.add(0, new Identifier(ns, "textures/entity/skeleton/skeleton.png"));
            case "wither_skeleton" -> paths.add(0, new Identifier(ns, "textures/entity/skeleton/wither_skeleton.png"));
            case "stray" -> paths.add(0, new Identifier(ns, "textures/entity/skeleton/stray.png"));
            case "zombie" -> paths.add(0, new Identifier(ns, "textures/entity/zombie/zombie.png"));
            case "husk" -> paths.add(0, new Identifier(ns, "textures/entity/zombie/husk.png"));
            case "drowned" -> paths.add(0, new Identifier(ns, "textures/entity/zombie/drowned.png"));
            case "spider" -> paths.add(0, new Identifier(ns, "textures/entity/spider/spider.png"));
            case "cave_spider" -> paths.add(0, new Identifier(ns, "textures/entity/spider/cave_spider.png"));
            // Vehicles / special entities
            case "boat", "oak_boat" -> paths.add(0, new Identifier(ns, "textures/entity/boat/oak.png"));
            case "chest_boat", "oak_chest_boat" -> paths.add(0, new Identifier(ns, "textures/entity/chest_boat/oak.png"));
            case "minecart" -> paths.add(0, new Identifier(ns, "textures/entity/minecart.png"));
        }

        // Also try with underscores replaced or sub-paths
        // e.g. textures/entity/<name>/default.png
        paths.add(new Identifier(ns, "textures/entity/" + path + "/default.png"));

        return paths;
    }
}

