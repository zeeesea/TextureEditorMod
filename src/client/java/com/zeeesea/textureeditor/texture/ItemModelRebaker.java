package com.zeeesea.textureeditor.texture;

import com.google.common.base.Suppliers;
import com.zeeesea.textureeditor.mixin.client.BasicItemModelAccessor;
import com.zeeesea.textureeditor.mixin.client.BakedModelManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.GeneratedItemModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Rebakes item models after texture edits to update the 3D thickness quads.
 *
 * When a player edits an item texture (e.g. adds pixels outside the original shape),
 * Minecraft's item model still has the old baked quads that only include thickness
 * for the original pixel shape. This class regenerates those quads from the updated
 * sprite data so newly-drawn pixels also get proper 3D thickness.
 */
public class ItemModelRebaker {

    // Cache mapping spriteId -> BasicItemModel instances that use that sprite
    // Built lazily on first rebake to avoid scanning all models every time
    private static Map<Identifier, List<BasicItemModel>> spriteToItemModels = null;

    /**
     * Rebake all item models that reference the given sprite.
     * Must be called on the render thread after the sprite's NativeImage has been updated.
     *
     * @param spriteId The sprite identifier (e.g. minecraft:item/diamond_sword)
     */
    public static void rebake(Identifier spriteId) {
        // Only rebake for item sprites (item/diamond_sword, etc.)
        // Block sprites (block/stone, block/grass_block_top, etc.) must NOT be rebaked
        // because block items use 3D cube models, not flat GeneratedItemModel geometry.
        // Rebaking a block sprite with GeneratedItemModel would turn the 3D block into a 1px flat sheet.
        String path = spriteId.getPath();
        if (!path.startsWith("item/")) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getBakedModelManager() == null) return;

        BakedModelManager modelManager = client.getBakedModelManager();
        Map<Identifier, ItemModel> bakedItemModels;
        try {
            bakedItemModels = ((BakedModelManagerAccessor) modelManager).getBakedItemModels();
        } catch (Exception e) {
            System.out.println("[TextureEditor] ItemModelRebaker: Failed to access bakedItemModels: " + e.getMessage());
            return;
        }

        if (bakedItemModels == null || bakedItemModels.isEmpty()) return;

        // Build reverse index if not cached yet
        if (spriteToItemModels == null) {
            buildReverseIndex(bakedItemModels);
        }

        // Find item models that use this sprite
        List<BasicItemModel> affectedModels = spriteToItemModels.get(spriteId);
        if (affectedModels == null || affectedModels.isEmpty()) {
            // Sprite not found in cache - might be a new mapping, rebuild and retry
            buildReverseIndex(bakedItemModels);
            affectedModels = spriteToItemModels.get(spriteId);
            if ((affectedModels == null || affectedModels.isEmpty()) && spriteId.getPath().endsWith("_in_hand")) {
                // Some in-hand models keep quads indexed by the base sprite; use that as fallback.
                Identifier baseSpriteId = Identifier.of(spriteId.getNamespace(), spriteId.getPath().replace("_in_hand", ""));
                List<BasicItemModel> baseModels = spriteToItemModels.get(baseSpriteId);
                if (baseModels != null && !baseModels.isEmpty()) {
                    affectedModels = baseModels;
                    System.out.println("[TextureEditor] ItemModelRebaker: Using base sprite fallback " + baseSpriteId + " for " + spriteId);
                }
            }
        }

        if (affectedModels == null || affectedModels.isEmpty()) {
            System.out.println("[TextureEditor] ItemModelRebaker: No item models found for sprite " + spriteId);
            return;
        }

        System.out.println("[TextureEditor] ItemModelRebaker: Rebaking " + affectedModels.size() + " model(s) for sprite " + spriteId);

        for (BasicItemModel basicModel : affectedModels) {
            rebakeBasicItemModel(basicModel, spriteId);
        }
    }

    /**
     * Build a reverse index: sprite ID -> list of item model IDs that use it.
     */
    private static void buildReverseIndex(Map<Identifier, ItemModel> bakedItemModels) {
        spriteToItemModels = new HashMap<>();
        Set<BasicItemModel> discovered = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ItemModel model : bakedItemModels.values()) {
            collectBasicModels(model, discovered, Collections.newSetFromMap(new IdentityHashMap<>()));
        }

        for (BasicItemModel basicModel : discovered) {
            BasicItemModelAccessor accessor = (BasicItemModelAccessor) basicModel;
            List<BakedQuad> quads = accessor.getQuads();
            if (quads == null) continue;

            Set<Identifier> seenSprites = new HashSet<>();
            for (BakedQuad quad : quads) {
                if (quad.sprite() != null) {
                    Identifier sid = quad.sprite().getContents().getId();
                    if (seenSprites.add(sid)) {
                        spriteToItemModels.computeIfAbsent(sid, k -> new ArrayList<>()).add(basicModel);
                    }
                }
            }
        }

        System.out.println("[TextureEditor] ItemModelRebaker: Built reverse index with " + spriteToItemModels.size() + " sprite entries and " + discovered.size() + " basic models");
    }

    private static void collectBasicModels(Object node, Set<BasicItemModel> out, Set<Object> visited) {
        if (node == null || !visited.add(node)) return;

        if (node instanceof BasicItemModel basic) {
            out.add(basic);
        }

        Class<?> cls = node.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                try {
                    Object value = field.get(node);
                    if (value == null) continue;

                    if (value instanceof ItemModel || value instanceof BasicItemModel) {
                        collectBasicModels(value, out, visited);
                    } else if (value instanceof Iterable<?> it) {
                        for (Object entry : it) {
                            if (entry instanceof ItemModel || entry instanceof BasicItemModel) {
                                collectBasicModels(entry, out, visited);
                            }
                        }
                    } else if (value.getClass().isArray()) {
                        int len = Array.getLength(value);
                        for (int i = 0; i < len; i++) {
                            Object entry = Array.get(value, i);
                            if (entry instanceof ItemModel || entry instanceof BasicItemModel) {
                                collectBasicModels(entry, out, visited);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            cls = cls.getSuperclass();
        }
    }

    /**
     * Rebake a single BasicItemModel by regenerating quads from the updated sprite.
     */
    private static void rebakeBasicItemModel(BasicItemModel model, Identifier spriteId) {
        BasicItemModelAccessor accessor = (BasicItemModelAccessor) model;
        List<BakedQuad> oldQuads = accessor.getQuads();
        if (oldQuads == null || oldQuads.isEmpty()) return;

        // Find the sprite in the atlas (it should be updated already)
        Sprite sprite = findSprite(spriteId);
        if (sprite == null) {
            System.out.println("[TextureEditor] ItemModelRebaker: Could not find sprite " + spriteId + " in any atlas");
            return;
        }

        try {
            // Create a minimal Baker that returns sprites from the atlas
            MinimalBaker baker = new MinimalBaker(sprite, spriteId);

            // Build ModelTextures for the generated item model
            // Items typically have layer0 = the sprite, and optionally more layers
            // We need to figure out which layers this model uses
            Map<String, SpriteIdentifier> texMap = new HashMap<>();
            Set<Identifier> usedSprites = new LinkedHashSet<>();
            for (BakedQuad quad : oldQuads) {
                if (quad.sprite() != null) {
                    usedSprites.add(quad.sprite().getContents().getId());
                }
            }

            // Map layers to sprites
            int layerIdx = 0;
            for (Identifier sid : usedSprites) {
                String layerName = "layer" + layerIdx;
                texMap.put(layerName, new SpriteIdentifier(BakedModelManager.BLOCK_OR_ITEM, sid));
                layerIdx++;
            }
            // Particle texture = layer0
            if (!usedSprites.isEmpty()) {
                texMap.put("particle", new SpriteIdentifier(BakedModelManager.BLOCK_OR_ITEM, usedSprites.iterator().next()));
            }

            ModelTextures modelTextures = new ModelTextures(texMap);

            // Use GeneratedItemModel's geometry to rebake
            GeneratedItemModel generatedModel = new GeneratedItemModel();
            Geometry geometry = generatedModel.geometry();

            // Create a dummy SimpleModel for the bake call
            SimpleModel dummyModel = () -> "textureeditor:rebake/" + spriteId;

            BakedGeometry bakedGeometry = geometry.bake(modelTextures, baker, ModelRotation.IDENTITY, dummyModel);
            List<BakedQuad> newQuads = bakedGeometry.getAllQuads();

            if (newQuads != null && !newQuads.isEmpty()) {
                // Replace quads and vector
                accessor.setQuads(newQuads);
                accessor.setVector(Suppliers.memoize(() -> BasicItemModel.bakeQuads(newQuads)));

                System.out.println("[TextureEditor] ItemModelRebaker: Rebaked model with " + newQuads.size() +
                    " quads (was " + oldQuads.size() + ") for sprite " + spriteId);
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] ItemModelRebaker: Failed to rebake model for " + spriteId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find a sprite in the items or block atlas.
     */
    private static Sprite findSprite(Identifier spriteId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return null;

        // Try items atlas first
        try {
            var tex = client.getTextureManager().getTexture(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE);
            if (tex instanceof SpriteAtlasTexture atlas) {
                Sprite sprite = atlas.getSprite(spriteId);
                if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                    return sprite;
                }
            }
        } catch (Exception ignored) {}

        // Try block atlas
        try {
            var tex = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (tex instanceof SpriteAtlasTexture atlas) {
                Sprite sprite = atlas.getSprite(spriteId);
                if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                    return sprite;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Invalidate the cached reverse index (e.g. after resource reload).
     */
    public static void invalidateCache() {
        spriteToItemModels = null;
    }

    /**
     * Minimal Baker implementation that provides just enough functionality
     * to rebake item model quads from atlas sprites.
     */
    private static class MinimalBaker implements Baker {
        private final Sprite primarySprite;
        private final Identifier primarySpriteId;
        private final ErrorCollectingSpriteGetter spriteGetter;
        private final Vec3fInterner interner;

        MinimalBaker(Sprite primarySprite, Identifier primarySpriteId) {
            this.primarySprite = primarySprite;
            this.primarySpriteId = primarySpriteId;
            this.spriteGetter = new MinimalSpriteGetter();
            this.interner = vec -> vec; // Simple passthrough, no interning needed for rebake
        }

        @Override
        public BakedSimpleModel getModel(Identifier id) {
            throw new UnsupportedOperationException("ItemModelRebaker minimal baker does not support getModel()");
        }

        @Override
        public BlockModelPart getBlockPart() {
            throw new UnsupportedOperationException("ItemModelRebaker minimal baker does not support getBlockPart()");
        }

        @Override
        public ErrorCollectingSpriteGetter getSpriteGetter() {
            return spriteGetter;
        }

        @Override
        public Vec3fInterner getVec3fInterner() {
            return interner;
        }

        @Override
        public <T> T compute(ResolvableCacheKey<T> key) {
            return key.compute(this);
        }

        /**
         * Sprite getter that resolves sprites from the live atlases.
         */
        private class MinimalSpriteGetter implements ErrorCollectingSpriteGetter {
            @Override
            public Sprite get(SpriteIdentifier id, SimpleModel model) {
                Identifier textureId = id.getTextureId();
                Sprite found = findSprite(textureId);
                if (found != null) return found;

                // Fallback: return primary sprite
                System.out.println("[TextureEditor] MinimalSpriteGetter: Could not find sprite " + textureId + ", using primary");
                return primarySprite;
            }

            @Override
            public Sprite getMissing(String name, SimpleModel model) {
                return primarySprite; // Use primary as fallback
            }
        }
    }
}
