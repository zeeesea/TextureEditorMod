package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.util.ImageColorCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
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
        MinecraftClient client = MinecraftClient.getInstance();
        EntityType<?> type = entity.getType();
        Identifier entityId = Registries.ENTITY_TYPE.getId(type);
        String name = type.getName().getString();

        System.out.println("[TextureEditor] Extracting mob texture for: " + entityId);

        // Try to detect the entity's variant texture first (cow, cat, frog, wolf, etc.)
        Identifier variantTexture = tryGetVariantTexture(entity, entityId);
        if (variantTexture != null) {
            System.out.println("[TextureEditor] Variant texture detected: " + variantTexture);
            var optResource = client.getResourceManager().getResource(variantTexture);
            if (optResource.isPresent()) {
                try {
                    InputStream stream = optResource.get().getInputStream();
                    NativeImage image = NativeImage.read(stream);
                    int w = image.getWidth(), h = image.getHeight();
                    int[][] pixels = new int[w][h];
                    for (int x = 0; x < w; x++)
                        for (int y = 0; y < h; y++)
                            pixels[x][y] = ImageColorCompat.readArgb(image, x, y);
                    image.close();
                    stream.close();
                    System.out.println("[TextureEditor] Found mob texture: " + variantTexture + " size=" + w + "x" + h);
                    return new MobTexture(variantTexture, pixels, w, h, name);
                } catch (IOException e) {
                    System.out.println("[TextureEditor] Failed to read variant texture: " + variantTexture + " - " + e.getMessage());
                }
            }
        }

        // Build candidate texture paths
        List<Identifier> candidates = buildTexturePaths(entityId);

        for (Identifier textureId : candidates) {
            System.out.println("[TextureEditor] Trying texture path: " + textureId);
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
                            pixels[x][y] = ImageColorCompat.readArgb(image, x, y);
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
        }

        System.out.println("[TextureEditor] FAILED to find texture for entity: " + entityId);
        return null;
    }

    /**
     * Try to extract the variant-specific texture from an entity using reflection.
     * Uses reflection throughout because yarn mapping names can differ between versions.
     */
    private static Identifier tryGetVariantTexture(Entity entity, Identifier entityId) {
        try {
            String entityPath = entityId.getPath();

            // 1) Wolf: direct getTextureId() method
            var getTextureIdMethod = findMethod(entity.getClass(), "getTextureId");
            if (getTextureIdMethod != null) {
                getTextureIdMethod.setAccessible(true);
                Object result = getTextureIdMethod.invoke(entity);
                if (result instanceof Identifier id) {
                    System.out.println("[TextureEditor] Wolf-style getTextureId(): " + id);
                    return id;
                }
            }

            // 2) Horse: find any method returning HorseColor enum, then map to texture
            if (entityPath.equals("horse")) {
                Identifier horseTex = tryHorseVariant(entity);
                if (horseTex != null) return horseTex;
            }

            // 3) Llama: getVariant() returns enum directly
            if (entityPath.equals("llama") || entityPath.equals("trader_llama")) {
                Identifier llamaTex = tryEnumVariantTexture(entity, "textures/entity/llama/", null);
                if (llamaTex != null) return llamaTex;
            }

            // 4) Rabbit: find method returning int for type
            if (entityPath.equals("rabbit")) {
                Identifier rabbitTex = tryRabbitVariant(entity);
                if (rabbitTex != null) return rabbitTex;
            }

            // 5) Fox: find method returning fox type enum
            if (entityPath.equals("fox")) {
                Identifier foxTex = tryFoxVariant(entity);
                if (foxTex != null) return foxTex;
            }

            // 6) Parrot: variant enum
            if (entityPath.equals("parrot")) {
                Identifier parrotTex = tryParrotVariant(entity);
                if (parrotTex != null) return parrotTex;
            }

            // 7) Cow/Pig/Chicken/Cat/Frog: getVariant() -> RegistryEntry -> value() -> variant object -> texture chain
            var getVariantMethod = findMethod(entity.getClass(), "getVariant");
            if (getVariantMethod != null) {
                getVariantMethod.setAccessible(true);
                Object variantRef = getVariantMethod.invoke(entity);
                System.out.println("[TextureEditor] getVariant() returned: " + (variantRef != null ? variantRef.getClass().getSimpleName() : "null"));

                if (variantRef != null) {
                    // For RegistryEntry types, get the value
                    Object variantValue = variantRef;
                    var valueMethod = findMethod(variantRef.getClass(), "value");
                    if (valueMethod != null) {
                        valueMethod.setAccessible(true);
                        variantValue = valueMethod.invoke(variantRef);
                        System.out.println("[TextureEditor] Variant value: " + (variantValue != null ? variantValue.getClass().getSimpleName() : "null"));
                    }

                    if (variantValue != null) {
                        // Try to get texture from the variant value
                        Identifier texId = extractTextureFromVariant(variantValue);
                        if (texId != null) return texId;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Variant detection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Try to extract a texture Identifier from a variant object using reflection.
     * Handles multiple patterns:
     * - modelAndTexture().asset().texturePath() (Cow, Pig, Chicken)
     * - assetInfo().texturePath() (Cat, Frog)
     * - texture() / texturePath() directly
     */
    private static Identifier extractTextureFromVariant(Object variant) {
        if (variant == null) return null;
        try {
            // Pattern 1: modelAndTexture() -> asset() -> texturePath() (Cow, Pig, Chicken)
            var matMethod = findMethod(variant.getClass(), "modelAndTexture");
            if (matMethod != null) {
                matMethod.setAccessible(true);
                Object mat = matMethod.invoke(variant);
                if (mat != null) {
                    // Try asset() -> texturePath()
                    var assetMethod = findMethod(mat.getClass(), "asset");
                    if (assetMethod != null) {
                        assetMethod.setAccessible(true);
                        Object asset = assetMethod.invoke(mat);
                        if (asset != null) {
                            Identifier texId = callTexturePath(asset);
                            if (texId != null) {
                                System.out.println("[TextureEditor] Found via modelAndTexture().asset().texturePath(): " + texId);
                                return texId;
                            }
                        }
                    }
                    // Try texturePath() directly on modelAndTexture result
                    Identifier texId = callTexturePath(mat);
                    if (texId != null) {
                        System.out.println("[TextureEditor] Found via modelAndTexture().texturePath(): " + texId);
                        return texId;
                    }
                }
            }

            // Pattern 2: assetInfo() -> texturePath() (Cat, Frog)
            var assetInfoMethod = findMethod(variant.getClass(), "assetInfo");
            if (assetInfoMethod != null) {
                assetInfoMethod.setAccessible(true);
                Object assetInfo = assetInfoMethod.invoke(variant);
                if (assetInfo != null) {
                    Identifier texId = callTexturePath(assetInfo);
                    if (texId != null) {
                        System.out.println("[TextureEditor] Found via assetInfo().texturePath(): " + texId);
                        return texId;
                    }
                }
            }

            // Pattern 3: Direct texturePath() on variant
            Identifier texId = callTexturePath(variant);
            if (texId != null) {
                System.out.println("[TextureEditor] Found via direct texturePath(): " + texId);
                return texId;
            }

            // Pattern 4: Direct texture() on variant (fallback)
            var textureMethod = findMethod(variant.getClass(), "texture");
            if (textureMethod != null) {
                textureMethod.setAccessible(true);
                Object result = textureMethod.invoke(variant);
                texId = resolveIdentifier(result);
                if (texId != null) {
                    System.out.println("[TextureEditor] Found via direct texture(): " + texId);
                    return texId;
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] extractTextureFromVariant error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Call texturePath() on an object and return the result as Identifier.
     */
    private static Identifier callTexturePath(Object obj) {
        try {
            var method = findMethod(obj.getClass(), "texturePath");
            if (method != null) {
                method.setAccessible(true);
                Object result = method.invoke(obj);
                return resolveIdentifier(result);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Resolve an object to an Identifier texture path.
     */
    private static Identifier resolveIdentifier(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Identifier id) {
            // If it's already a full texture path, use it directly
            if (id.getPath().startsWith("textures/") && id.getPath().endsWith(".png")) {
                return id;
            }
            // Otherwise, construct the texture path
            return Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
        }
        // Try toString and parse
        String str = obj.toString();
        if (str.contains(":")) {
            try {
                Identifier id = Identifier.of(str);
                if (id.getPath().startsWith("textures/") && id.getPath().endsWith(".png")) return id;
                return Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Horse: scan all no-arg methods for one returning an enum whose simple name contains "HorseColor",
     * then map the enum constant name to a texture path.
     */
    private static Identifier tryHorseVariant(Entity entity) {
        try {
            for (var m : entity.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                Class<?> ret = m.getReturnType();
                if (ret.isEnum() && ret.getSimpleName().contains("HorseColor")) {
                    m.setAccessible(true);
                    Object color = m.invoke(entity);
                    if (color == null) continue;
                    String colorName = color.toString().toLowerCase();
                    System.out.println("[TextureEditor] Horse color (via " + m.getName() + "): " + colorName);
                    String texName = switch (colorName) {
                        case "white" -> "horse_white";
                        case "creamy" -> "horse_creamy";
                        case "chestnut" -> "horse_chestnut";
                        case "brown" -> "horse_brown";
                        case "black" -> "horse_black";
                        case "gray" -> "horse_gray";
                        case "dark_brown" -> "horse_darkbrown";
                        default -> "horse_" + colorName;
                    };
                    return Identifier.of("minecraft", "textures/entity/horse/" + texName + ".png");
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] tryHorseVariant error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Generic helper: call getVariant() and if it returns an enum, map it to textures/<prefix><enumName>.png
     */
    private static Identifier tryEnumVariantTexture(Entity entity, String prefix, java.util.Map<String, String> nameMap) {
        try {
            var getVariantMethod = findMethod(entity.getClass(), "getVariant");
            if (getVariantMethod != null) {
                getVariantMethod.setAccessible(true);
                Object variant = getVariantMethod.invoke(entity);
                if (variant != null && variant.getClass().isEnum()) {
                    String varName = variant.toString().toLowerCase();
                    if (nameMap != null && nameMap.containsKey(varName)) {
                        varName = nameMap.get(varName);
                    }
                    System.out.println("[TextureEditor] Enum variant: " + varName);
                    return Identifier.of("minecraft", prefix + varName + ".png");
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] tryEnumVariantTexture error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Rabbit: scan for any no-arg method returning int whose name contains "rabbit" or "type" (case insensitive).
     * Falls back to scanning for any method returning int on the rabbit-specific class.
     */
    private static Identifier tryRabbitVariant(Entity entity) {
        try {
            // Try known method name candidates
            for (String name : new String[]{"getRabbitType", "getVariant", "getRabbitVariant"}) {
                var m = findMethod(entity.getClass(), name);
                if (m != null) {
                    m.setAccessible(true);
                    Object result = m.invoke(entity);
                    if (result instanceof Integer typeInt) {
                        return rabbitTypeToTexture(typeInt);
                    }
                    // Some mappings may return an enum
                    if (result != null && result.getClass().isEnum()) {
                        int ordinal = ((Enum<?>) result).ordinal();
                        return rabbitTypeToTexture(ordinal);
                    }
                }
            }
            // Brute-force: find a method on the entity's own class (not superclass) returning int
            for (var m : entity.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == int.class) {
                    String mName = m.getName().toLowerCase();
                    if (mName.contains("rabbit") || mName.contains("type") || mName.contains("variant")) {
                        m.setAccessible(true);
                        int typeInt = (int) m.invoke(entity);
                        System.out.println("[TextureEditor] Rabbit type (via " + m.getName() + "): " + typeInt);
                        return rabbitTypeToTexture(typeInt);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] tryRabbitVariant error: " + e.getMessage());
        }
        return null;
    }

    private static Identifier rabbitTypeToTexture(int typeInt) {
        String texName = switch (typeInt) {
            case 0 -> "brown";
            case 1 -> "white";
            case 2 -> "black";
            case 3 -> "white_splotched";
            case 4 -> "gold";
            case 5 -> "salt";
            case 99 -> "caerbannog";
            default -> "brown";
        };
        System.out.println("[TextureEditor] Rabbit type: " + typeInt + " -> " + texName);
        return Identifier.of("minecraft", "textures/entity/rabbit/" + texName + ".png");
    }

    /**
     * Fox: scan for method returning an enum whose simple name contains "Fox" (e.g. FoxEntity$Type).
     */
    private static Identifier tryFoxVariant(Entity entity) {
        try {
            // Try known names first
            for (String name : new String[]{"getFoxType", "getVariant", "getFoxVariant"}) {
                var m = findMethod(entity.getClass(), name);
                if (m != null && m.getReturnType().isEnum()) {
                    m.setAccessible(true);
                    Object foxType = m.invoke(entity);
                    if (foxType != null) {
                        String typeName = foxType.toString().toLowerCase();
                        System.out.println("[TextureEditor] Fox type (via " + m.getName() + "): " + typeName);
                        String texName = typeName.equals("snow") ? "snow_fox" : "fox";
                        return Identifier.of("minecraft", "textures/entity/fox/" + texName + ".png");
                    }
                }
            }
            // Brute-force: scan for any method returning an enum with "Type" in its name on the entity class
            for (var m : entity.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().isEnum()) {
                    String retName = m.getReturnType().getSimpleName();
                    if (retName.contains("Type") || retName.contains("Fox")) {
                        m.setAccessible(true);
                        Object foxType = m.invoke(entity);
                        if (foxType != null) {
                            String typeName = foxType.toString().toLowerCase();
                            System.out.println("[TextureEditor] Fox type (via scan " + m.getName() + "): " + typeName);
                            String texName = typeName.equals("snow") ? "snow_fox" : "fox";
                            return Identifier.of("minecraft", "textures/entity/fox/" + texName + ".png");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] tryFoxVariant error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parrot: scan for method returning an enum whose simple name contains "Parrot" or "Variant".
     */
    private static Identifier tryParrotVariant(Entity entity) {
        try {
            for (String name : new String[]{"getVariant", "getParrotVariant"}) {
                var m = findMethod(entity.getClass(), name);
                if (m != null && m.getReturnType().isEnum()) {
                    m.setAccessible(true);
                    Object variant = m.invoke(entity);
                    if (variant != null) {
                        String varName = variant.toString().toLowerCase();
                        System.out.println("[TextureEditor] Parrot variant (via " + m.getName() + "): " + varName);
                        String texName = switch (varName) {
                            case "red_blue" -> "parrot_red_blue";
                            case "blue" -> "parrot_blue";
                            case "green" -> "parrot_green";
                            case "yellow_blue" -> "parrot_yellow_blue";
                            case "gray" -> "parrot_grey";
                            default -> "parrot_" + varName;
                        };
                        return Identifier.of("minecraft", "textures/entity/parrot/" + texName + ".png");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] tryParrotVariant error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Find a method by name in a class hierarchy.
     */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (var m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            // Also check interfaces
            for (var iface : c.getInterfaces()) {
                for (var m : iface.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == 0) {
                        return m;
                    }
                }
            }
            c = c.getSuperclass();
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
        paths.add(Identifier.of(ns, "textures/entity/" + path + "/" + path + ".png"));

        // Common variant: textures/entity/<name>.png (simpler entities)
        paths.add(Identifier.of(ns, "textures/entity/" + path + ".png"));

        // Special cases for known entity texture paths
        switch (path) {
            case "iron_golem" -> paths.add(0, Identifier.of(ns, "textures/entity/iron_golem/iron_golem.png"));
            case "snow_golem" -> paths.add(0, Identifier.of(ns, "textures/entity/snow_golem.png"));
            case "armor_stand" -> paths.add(0, Identifier.of(ns, "textures/entity/armor_stand/wood.png"));
            case "ender_dragon" -> paths.add(0, Identifier.of(ns, "textures/entity/enderdragon/dragon.png"));
            case "wither" -> paths.add(0, Identifier.of(ns, "textures/entity/wither/wither.png"));
            case "guardian" -> paths.add(0, Identifier.of(ns, "textures/entity/guardian.png"));
            case "elder_guardian" -> paths.add(0, Identifier.of(ns, "textures/entity/guardian_elder.png"));
            case "horse" -> paths.add(0, Identifier.of(ns, "textures/entity/horse/horse_white.png"));
            case "donkey" -> paths.add(0, Identifier.of(ns, "textures/entity/horse/donkey.png"));
            case "mule" -> paths.add(0, Identifier.of(ns, "textures/entity/horse/mule.png"));
            case "skeleton_horse" -> paths.add(0, Identifier.of(ns, "textures/entity/horse/horse_skeleton.png"));
            case "zombie_horse" -> paths.add(0, Identifier.of(ns, "textures/entity/horse/horse_zombie.png"));
            case "llama" -> paths.add(0, Identifier.of(ns, "textures/entity/llama/creamy.png"));
            case "trader_llama" -> paths.add(0, Identifier.of(ns, "textures/entity/llama/creamy.png"));
            case "cat" -> paths.add(0, Identifier.of(ns, "textures/entity/cat/tabby.png"));
            case "wolf" -> paths.add(0, Identifier.of(ns, "textures/entity/wolf/wolf.png"));
            case "fox" -> paths.add(0, Identifier.of(ns, "textures/entity/fox/fox.png"));
            case "parrot" -> paths.add(0, Identifier.of(ns, "textures/entity/parrot/parrot_red_blue.png"));
            case "rabbit" -> paths.add(0, Identifier.of(ns, "textures/entity/rabbit/brown.png"));
            case "villager" -> paths.add(0, Identifier.of(ns, "textures/entity/villager/villager.png"));
            case "zombie_villager" -> paths.add(0, Identifier.of(ns, "textures/entity/zombie_villager/zombie_villager.png"));
            case "wandering_trader" -> paths.add(0, Identifier.of(ns, "textures/entity/wandering_trader.png"));
            case "witch" -> paths.add(0, Identifier.of(ns, "textures/entity/witch.png"));
            case "shulker" -> paths.add(0, Identifier.of(ns, "textures/entity/shulker/shulker.png"));
            case "slime" -> paths.add(0, Identifier.of(ns, "textures/entity/slime/slime.png"));
            case "magma_cube" -> paths.add(0, Identifier.of(ns, "textures/entity/slime/magmacube.png"));
            case "ghast" -> paths.add(0, Identifier.of(ns, "textures/entity/ghast/ghast.png"));
            case "blaze" -> paths.add(0, Identifier.of(ns, "textures/entity/blaze.png"));
            case "enderman" -> paths.add(0, Identifier.of(ns, "textures/entity/enderman/enderman.png"));
            case "endermite" -> paths.add(0, Identifier.of(ns, "textures/entity/endermite.png"));
            case "silverfish" -> paths.add(0, Identifier.of(ns, "textures/entity/silverfish.png"));
            case "bat" -> paths.add(0, Identifier.of(ns, "textures/entity/bat.png"));
            case "phantom" -> paths.add(0, Identifier.of(ns, "textures/entity/phantom.png"));
            case "panda" -> paths.add(0, Identifier.of(ns, "textures/entity/panda/panda.png"));
            case "bee" -> paths.add(0, Identifier.of(ns, "textures/entity/bee/bee.png"));
            case "piglin" -> paths.add(0, Identifier.of(ns, "textures/entity/piglin/piglin.png"));
            case "piglin_brute" -> paths.add(0, Identifier.of(ns, "textures/entity/piglin/piglin_brute.png"));
            case "zombified_piglin" -> paths.add(0, Identifier.of(ns, "textures/entity/piglin/zombified_piglin.png"));
            case "hoglin" -> paths.add(0, Identifier.of(ns, "textures/entity/hoglin/hoglin.png"));
            case "zoglin" -> paths.add(0, Identifier.of(ns, "textures/entity/hoglin/zoglin.png"));
            case "strider" -> paths.add(0, Identifier.of(ns, "textures/entity/strider/strider.png"));
            case "ravager" -> paths.add(0, Identifier.of(ns, "textures/entity/illager/ravager.png"));
            case "pillager" -> paths.add(0, Identifier.of(ns, "textures/entity/illager/pillager.png"));
            case "vindicator" -> paths.add(0, Identifier.of(ns, "textures/entity/illager/vindicator.png"));
            case "evoker" -> paths.add(0, Identifier.of(ns, "textures/entity/illager/evoker.png"));
            case "illusioner" -> paths.add(0, Identifier.of(ns, "textures/entity/illager/illusioner.png"));
            case "vex" -> paths.add(0, Identifier.of(ns, "textures/entity/illager/vex.png"));
            case "warden" -> paths.add(0, Identifier.of(ns, "textures/entity/warden/warden.png"));
            case "allay" -> paths.add(0, Identifier.of(ns, "textures/entity/allay/allay.png"));
            case "frog" -> paths.add(0, Identifier.of(ns, "textures/entity/frog/temperate_frog.png"));
            case "tadpole" -> paths.add(0, Identifier.of(ns, "textures/entity/tadpole/tadpole.png"));
            case "camel" -> paths.add(0, Identifier.of(ns, "textures/entity/camel/camel.png"));
            case "sniffer" -> paths.add(0, Identifier.of(ns, "textures/entity/sniffer/sniffer.png"));
            case "breeze" -> paths.add(0, Identifier.of(ns, "textures/entity/breeze/breeze.png"));
            case "bogged" -> paths.add(0, Identifier.of(ns, "textures/entity/skeleton/bogged.png"));
            case "axolotl" -> paths.add(0, Identifier.of(ns, "textures/entity/axolotl/axolotl_lucy.png"));
            case "glow_squid" -> paths.add(0, Identifier.of(ns, "textures/entity/squid/glow_squid.png"));
            case "squid" -> paths.add(0, Identifier.of(ns, "textures/entity/squid/squid.png"));
            case "goat" -> paths.add(0, Identifier.of(ns, "textures/entity/goat/goat.png"));
            case "tropical_fish" -> paths.add(0, Identifier.of(ns, "textures/entity/fish/tropical_a.png"));
            case "salmon" -> paths.add(0, Identifier.of(ns, "textures/entity/fish/salmon.png"));
            case "cod" -> paths.add(0, Identifier.of(ns, "textures/entity/fish/cod.png"));
            case "pufferfish" -> paths.add(0, Identifier.of(ns, "textures/entity/fish/pufferfish.png"));
            case "dolphin" -> paths.add(0, Identifier.of(ns, "textures/entity/dolphin.png"));
            case "turtle" -> paths.add(0, Identifier.of(ns, "textures/entity/turtle/big_sea_turtle.png"));
            case "ocelot" -> paths.add(0, Identifier.of(ns, "textures/entity/cat/ocelot.png"));
            case "mooshroom" -> paths.add(0, Identifier.of(ns, "textures/entity/cow/red_mooshroom.png"));
            case "cow" -> paths.add(0, Identifier.of(ns, "textures/entity/cow/cow.png"));
            case "pig" -> paths.add(0, Identifier.of(ns, "textures/entity/pig/pig.png"));
            case "sheep" -> paths.add(0, Identifier.of(ns, "textures/entity/sheep/sheep.png"));
            case "chicken" -> paths.add(0, Identifier.of(ns, "textures/entity/chicken/chicken.png"));
            case "creeper" -> paths.add(0, Identifier.of(ns, "textures/entity/creeper/creeper.png"));
            case "skeleton" -> paths.add(0, Identifier.of(ns, "textures/entity/skeleton/skeleton.png"));
            case "wither_skeleton" -> paths.add(0, Identifier.of(ns, "textures/entity/skeleton/wither_skeleton.png"));
            case "stray" -> paths.add(0, Identifier.of(ns, "textures/entity/skeleton/stray.png"));
            case "zombie" -> paths.add(0, Identifier.of(ns, "textures/entity/zombie/zombie.png"));
            case "husk" -> paths.add(0, Identifier.of(ns, "textures/entity/zombie/husk.png"));
            case "drowned" -> paths.add(0, Identifier.of(ns, "textures/entity/zombie/drowned.png"));
            case "spider" -> paths.add(0, Identifier.of(ns, "textures/entity/spider/spider.png"));
            case "cave_spider" -> paths.add(0, Identifier.of(ns, "textures/entity/spider/cave_spider.png"));
            // Vehicles / special entities
            case "boat", "oak_boat" -> paths.add(0, Identifier.of(ns, "textures/entity/boat/oak.png"));
            case "chest_boat", "oak_chest_boat" -> paths.add(0, Identifier.of(ns, "textures/entity/chest_boat/oak.png"));
            case "minecart" -> paths.add(0, Identifier.of(ns, "textures/entity/minecart.png"));
        }

        // Also try with underscores replaced or sub-paths
        // e.g. textures/entity/<name>/default.png
        paths.add(Identifier.of(ns, "textures/entity/" + path + "/default.png"));

        return paths;
    }
}
