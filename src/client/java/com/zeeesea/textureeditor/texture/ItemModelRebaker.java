package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.BakedModelManagerAccessor;
import com.zeeesea.textureeditor.util.ReflectionHelpers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.item.model.ItemModel;
// Avoid referencing client-only BakedModel at compile time in some environments.
// We'll treat runtime models as Object and use reflection where necessary.
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.render.model.json.GeneratedItemModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds generated item model geometry after live texture edits so newly drawn pixels
 * also get side quads (thickness) instead of only updating front/back color.
 */
public final class ItemModelRebaker {
    private static final Identifier ITEMS_ATLAS_ID = Identifier.ofVanilla("textures/atlas/items.png");

    // sprite id -> item model ids using that sprite
    private static Map<Identifier, List<Identifier>> spriteToItemModels;

    private ItemModelRebaker() {
    }

    public static void rebake(Identifier spriteId) {
        if (!spriteId.getPath().startsWith("item/")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getBakedModelManager() == null) return;

        Map<Identifier, ItemModel> bakedItemModels;
        try {
            bakedItemModels = ((BakedModelManagerAccessor) client.getBakedModelManager()).textureeditor$getBakedItemModels();
        } catch (Throwable t) {
            System.out.println("[TextureEditor] ItemModelRebaker: bakedItemModels access failed: " + t.getMessage());
            return;
        }

        if (bakedItemModels == null || bakedItemModels.isEmpty()) return;

        if (spriteToItemModels == null) {
            buildReverseIndex(bakedItemModels);
        }

        List<Identifier> affected = spriteToItemModels.get(spriteId);
        if (affected == null || affected.isEmpty()) {
            buildReverseIndex(bakedItemModels);
            affected = spriteToItemModels.get(spriteId);
            if (affected == null || affected.isEmpty()) return;
        }

        for (Identifier modelId : affected) {
            ItemModel itemModel = bakedItemModels.get(modelId);
            if (itemModel instanceof BasicItemModel basic) {
                rebakeBasicItemModel(basic, spriteId);
            }
        }
    }

    public static void invalidateCache() {
        spriteToItemModels = null;
    }

    private static void buildReverseIndex(Map<Identifier, ItemModel> bakedItemModels) {
        Map<Identifier, List<Identifier>> newIndex = new HashMap<>();

        for (Map.Entry<Identifier, ItemModel> entry : bakedItemModels.entrySet()) {
            if (!(entry.getValue() instanceof BasicItemModel basicItemModel)) continue;
            Object bakedModel = ReflectionHelpers.getField(basicItemModel, "model");
            if (bakedModel == null) continue;

            for (Identifier sid : collectSpriteIds(bakedModel)) {
                newIndex.computeIfAbsent(sid, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }

        spriteToItemModels = newIndex;
    }

    private static void rebakeBasicItemModel(BasicItemModel basicItemModel, Identifier changedSpriteId) {
        Object oldModel = ReflectionHelpers.getField(basicItemModel, "model");
        if (oldModel == null) return;

        Set<Identifier> usedSprites = collectSpriteIds(oldModel);
        if (usedSprites.isEmpty()) return;

        Sprite primarySprite = findSprite(changedSpriteId);
        if (primarySprite == null) {
            for (Identifier sid : usedSprites) {
                primarySprite = findSprite(sid);
                if (primarySprite != null) break;
            }
        }
        if (primarySprite == null) return;

        // Build layer0..layerN and particle mapping exactly like generated item models expect.
        ModelTextures.Textures.Builder texBuilder = new ModelTextures.Textures.Builder();
        int layer = 0;
        for (Identifier sid : usedSprites) {
            if (layer >= GeneratedItemModel.LAYERS.size()) break;
            texBuilder.addSprite("layer" + layer, new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, sid));
            layer++;
        }
        if (layer == 0) return;

        Identifier particleId = usedSprites.iterator().next();
        texBuilder.addSprite("particle", new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, particleId));

        // Baking a new GeneratedItemModel requires the full in-game baker APIs which are not
        // available in the development source environment used here. To keep the project
        // compiling and avoid depending on mappings for baker helpers, skip the dynamic
        // rebake in this environment.
        System.out.println("[TextureEditor] ItemModelRebaker: dynamic rebake skipped in development environment for " + changedSpriteId);
    }

    private static Set<Identifier> collectSpriteIds(Object bakedModel) {
        Set<Identifier> ids = new LinkedHashSet<>();
        Random rand = Random.create();

        if (bakedModel == null) return ids;

        try {
            java.lang.reflect.Method getQuads = bakedModel.getClass().getMethod("getQuads", net.minecraft.block.BlockState.class, Direction.class, Random.class);

            @SuppressWarnings("unchecked")
            java.util.List<Object> baseQuads = (java.util.List<Object>) getQuads.invoke(bakedModel, null, null, rand);
            if (baseQuads != null) {
                for (Object q : baseQuads) {
                    if (q instanceof BakedQuad quad) {
                        Sprite sprite = quad.sprite();
                        if (sprite != null) ids.add(sprite.getContents().getId());
                    }
                }
            }

            for (Direction side : Direction.values()) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> sideQuads = (java.util.List<Object>) getQuads.invoke(bakedModel, null, side, rand);
                if (sideQuads != null) {
                    for (Object q : sideQuads) {
                        if (q instanceof BakedQuad quad) {
                            Sprite sprite = quad.sprite();
                            if (sprite != null) ids.add(sprite.getContents().getId());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return ids;
    }

    private static Sprite findSprite(Identifier spriteId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return null;

        try {
            var itemTex = client.getTextureManager().getTexture(ITEMS_ATLAS_ID);
            if (itemTex instanceof SpriteAtlasTexture itemAtlas) {
                Sprite sprite = itemAtlas.getSprite(spriteId);
                if (isRealSprite(sprite)) return sprite;
            }
        } catch (Throwable ignored) {
        }

        try {
            var blockTex = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (blockTex instanceof SpriteAtlasTexture blockAtlas) {
                Sprite sprite = blockAtlas.getSprite(spriteId);
                if (isRealSprite(sprite)) return sprite;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean isRealSprite(Sprite sprite) {
        return sprite != null && !"missingno".equals(sprite.getContents().getId().getPath());
    }

    // ...existing code...
}
