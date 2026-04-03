package com.zeeesea.textureeditor.texture;

import com.google.common.base.Suppliers;
import com.zeeesea.textureeditor.mixin.client.BakedModelManagerAccessor;
import com.zeeesea.textureeditor.mixin.client.BasicItemModelAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.BakedGeometry;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedSimpleModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ErrorCollectingSpriteGetter;
import net.minecraft.client.render.model.Geometry;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.render.model.SimpleModel;
import net.minecraft.client.render.model.json.GeneratedItemModel;
import net.minecraft.client.texture.atlas.Atlases;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Rebakes item models after live sprite edits so generated item thickness reflects new opaque pixels.
 */
public final class ItemModelRebaker {
    private static final Identifier ITEMS_ATLAS_TEXTURE_ID = Identifier.ofVanilla("textures/atlas/items.png");
    private static Map<Identifier, List<Identifier>> spriteToItemModels;

    private ItemModelRebaker() {}

    public static void rebake(Identifier spriteId) {
        if (spriteId == null || !spriteId.getPath().startsWith("item/")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getBakedModelManager() == null) return;

        BakedModelManager modelManager = client.getBakedModelManager();
        Map<Identifier, ItemModel> bakedItemModels;
        try {
            bakedItemModels = ((BakedModelManagerAccessor) modelManager).getBakedItemModels();
        } catch (Throwable ignored) {
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
            ItemModel model = bakedItemModels.get(modelId);
            if (model instanceof BasicItemModel basic) {
                rebakeBasicItemModel(basic, spriteId);
            }
        }
    }

    public static void invalidateCache() {
        spriteToItemModels = null;
    }

    private static void buildReverseIndex(Map<Identifier, ItemModel> bakedItemModels) {
        Map<Identifier, List<Identifier>> reverse = new HashMap<>();
        for (Map.Entry<Identifier, ItemModel> entry : bakedItemModels.entrySet()) {
            if (!(entry.getValue() instanceof BasicItemModel basic)) continue;
            BasicItemModelAccessor acc = (BasicItemModelAccessor) basic;
            List<BakedQuad> quads = acc.getQuads();
            if (quads == null || quads.isEmpty()) continue;

            Set<Identifier> seen = new HashSet<>();
            for (BakedQuad quad : quads) {
                if (quad.sprite() == null) continue;
                Identifier sid = quad.sprite().getContents().getId();
                if (seen.add(sid)) {
                    reverse.computeIfAbsent(sid, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }
        spriteToItemModels = reverse;
    }

    private static void rebakeBasicItemModel(BasicItemModel model, Identifier editedSpriteId) {
        BasicItemModelAccessor accessor = (BasicItemModelAccessor) model;
        List<BakedQuad> oldQuads = accessor.getQuads();
        if (oldQuads == null || oldQuads.isEmpty()) return;

        Sprite primary = findSprite(editedSpriteId);
        if (primary == null) return;

        try {
            Set<Identifier> usedSprites = new LinkedHashSet<>();
            for (BakedQuad q : oldQuads) {
                if (q.sprite() != null) usedSprites.add(q.sprite().getContents().getId());
            }
            if (usedSprites.isEmpty()) usedSprites.add(editedSpriteId);

            Map<String, SpriteIdentifier> texMap = new HashMap<>();
            int idx = 0;
            for (Identifier sid : usedSprites) {
                texMap.put("layer" + idx, new SpriteIdentifier(Atlases.BLOCKS, sid));
                idx++;
            }
            texMap.put("particle", new SpriteIdentifier(Atlases.BLOCKS, usedSprites.iterator().next()));

            ModelTextures modelTextures = new ModelTextures(texMap);
            Geometry geometry = new GeneratedItemModel().geometry();
            SimpleModel dummyModel = () -> "textureeditor:rebake/" + editedSpriteId;
            Baker baker = new MinimalBaker(primary);

            BakedGeometry baked = geometry.bake(modelTextures, baker, ModelRotation.X0_Y0, dummyModel);
            List<BakedQuad> newQuads = baked.getAllQuads();
            if (newQuads == null || newQuads.isEmpty()) return;

            accessor.setQuads(newQuads);
            Supplier<Vector3fc[]> vec = Suppliers.memoize(() -> BasicItemModel.bakeQuads(newQuads));
            accessor.setVector(vec);
        } catch (Throwable ignored) {
            // Keep old model intact if rebake fails for any reason.
        }
    }

    private static Sprite findSprite(Identifier spriteId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return null;

        try {
            var tex = client.getTextureManager().getTexture(ITEMS_ATLAS_TEXTURE_ID);
            if (tex instanceof SpriteAtlasTexture atlas) {
                Sprite s = atlas.getSprite(spriteId);
                if (s != null && !s.getContents().getId().getPath().equals("missingno")) return s;
            }
        } catch (Throwable ignored) {}

        try {
            var tex = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (tex instanceof SpriteAtlasTexture atlas) {
                Sprite s = atlas.getSprite(spriteId);
                if (s != null && !s.getContents().getId().getPath().equals("missingno")) return s;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static final class MinimalBaker implements Baker {
        private final Sprite fallbackSprite;
        private final ErrorCollectingSpriteGetter spriteGetter;

        private MinimalBaker(Sprite fallbackSprite) {
            this.fallbackSprite = fallbackSprite;
            this.spriteGetter = new MinimalSpriteGetter();
        }

        @Override
        public BakedSimpleModel getModel(Identifier id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ErrorCollectingSpriteGetter getSpriteGetter() {
            return spriteGetter;
        }

        @Override
        public <T> T compute(ResolvableCacheKey<T> key) {
            return key.compute(this);
        }

        private final class MinimalSpriteGetter implements ErrorCollectingSpriteGetter {
            @Override
            public Sprite get(SpriteIdentifier id, SimpleModel model) {
                Sprite found = findSprite(id.getTextureId());
                return found != null ? found : fallbackSprite;
            }

            @Override
            public Sprite getMissing(String name, SimpleModel model) {
                return fallbackSprite;
            }
        }
    }
}
