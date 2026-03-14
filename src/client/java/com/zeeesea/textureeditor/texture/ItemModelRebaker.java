package com.zeeesea.textureeditor.texture;

import com.zeeesea.textureeditor.mixin.client.BakedModelManagerAccessor;
import com.zeeesea.textureeditor.mixin.client.BasicItemModelAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelNameSupplier;
import net.minecraft.client.model.SpriteGetter;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.ModelRotation;
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

            BakedModel bakedModel = ((BasicItemModelAccessor) basicItemModel).textureeditor$getModel();
            if (bakedModel == null) continue;

            for (Identifier sid : collectSpriteIds(bakedModel)) {
                newIndex.computeIfAbsent(sid, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }

        spriteToItemModels = newIndex;
    }

    private static void rebakeBasicItemModel(BasicItemModel basicItemModel, Identifier changedSpriteId) {
        BasicItemModelAccessor accessor = (BasicItemModelAccessor) basicItemModel;
        BakedModel oldModel = accessor.textureeditor$getModel();
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

        ModelNameSupplier nameSupplier = () -> "textureeditor:rebake/" + changedSpriteId;
        ModelTextures modelTextures = new ModelTextures.Builder().addLast(texBuilder.build()).build(nameSupplier);

        MinimalBaker baker = new MinimalBaker(nameSupplier, primarySprite);
        GeneratedItemModel generated = new GeneratedItemModel();
        BakedModel newModel = generated.bake(
                modelTextures,
                baker,
                ModelRotation.X0_Y0,
                oldModel.useAmbientOcclusion(),
                oldModel.isSideLit(),
                oldModel.getTransformation()
        );

        if (newModel != null) {
            accessor.textureeditor$setModel(newModel);
        }
    }

    private static Set<Identifier> collectSpriteIds(BakedModel bakedModel) {
        Set<Identifier> ids = new LinkedHashSet<>();
        Random rand = Random.create();

        for (BakedQuad quad : bakedModel.getQuads(null, null, rand)) {
            Sprite sprite = quad.getSprite();
            if (sprite != null) ids.add(sprite.getContents().getId());
        }

        for (Direction side : Direction.values()) {
            for (BakedQuad quad : bakedModel.getQuads(null, side, rand)) {
                Sprite sprite = quad.getSprite();
                if (sprite != null) ids.add(sprite.getContents().getId());
            }
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

    private static final class MinimalBaker implements Baker {
        private final ModelNameSupplier modelNameSupplier;
        private final SpriteGetter spriteGetter;

        private MinimalBaker(ModelNameSupplier modelNameSupplier, Sprite primarySprite) {
            this.modelNameSupplier = modelNameSupplier;
            this.spriteGetter = new MinimalSpriteGetter(primarySprite);
        }

        @Override
        public BakedModel bake(Identifier id, ModelBakeSettings settings) {
            throw new UnsupportedOperationException("ItemModelRebaker MinimalBaker does not support nested model bake: " + id);
        }

        @Override
        public SpriteGetter getSpriteGetter() {
            return this.spriteGetter;
        }

        @Override
        public ModelNameSupplier getModelNameSupplier() {
            return this.modelNameSupplier;
        }
    }

    private static final class MinimalSpriteGetter implements SpriteGetter {
        private final Sprite primarySprite;

        private MinimalSpriteGetter(Sprite primarySprite) {
            this.primarySprite = primarySprite;
        }

        @Override
        public Sprite get(SpriteIdentifier spriteId) {
            Sprite sprite = findSprite(spriteId.getTextureId());
            return sprite != null ? sprite : this.primarySprite;
        }

        @Override
        public Sprite getMissing(String textureId) {
            return this.primarySprite;
        }
    }
}
