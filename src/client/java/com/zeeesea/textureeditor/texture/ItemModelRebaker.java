package com.zeeesea.textureeditor.texture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflection-based item model rebake that stays compatible across 1.21.x API shifts.
 */
public final class ItemModelRebaker {
    private static final Map<Identifier, List<Object>> SPRITE_TO_MODELS = new LinkedHashMap<>();
    private static boolean indexed = false;

    private ItemModelRebaker() {}

    public static void rebake(Identifier spriteId) {
        if (spriteId == null || !spriteId.getPath().startsWith("item/")) return;

        try {
            List<Object> models = resolveModels(spriteId);
            if (models.isEmpty()) {
                return;
            }

            for (Object itemModel : models) {
                try {
                    rebakeOne(itemModel, spriteId);
                } catch (Throwable modelError) {
                    System.out.println("[TextureEditor] Item rebake model failed for " + spriteId + " model=" + itemModel.getClass().getName());
                    modelError.printStackTrace();
                }
            }
        } catch (Throwable t) {
            System.out.println("[TextureEditor] Item rebake failed for " + spriteId + ": " + t.getClass().getName() + " - " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static void invalidateCache() {
        SPRITE_TO_MODELS.clear();
        indexed = false;
    }

    private static List<Object> resolveModels(Identifier spriteId) throws Exception {
        if (!indexed) rebuildIndex();
        return SPRITE_TO_MODELS.getOrDefault(spriteId, List.of());
    }

    private static void rebuildIndex() throws Exception {
        SPRITE_TO_MODELS.clear();
        indexed = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        Object bakedModelManager = callNoArgs(client, "getBakedModelManager");
        if (bakedModelManager == null) return;

        Field bakedItemModelsField = findField(bakedModelManager.getClass(), "bakedItemModels");
        if (bakedItemModelsField == null) return;
        bakedItemModelsField.setAccessible(true);

        Object mapObj = bakedItemModelsField.get(bakedModelManager);
        if (!(mapObj instanceof Map<?, ?> map)) return;

        for (Object itemModel : map.values()) {
            List<?> quads = readQuads(itemModel);
            if (quads == null || quads.isEmpty()) continue;

            Set<Identifier> spriteIds = collectSpriteIds(quads);
            for (Identifier id : spriteIds) {
                if (!id.getPath().startsWith("item/")) continue;
                SPRITE_TO_MODELS.computeIfAbsent(id, k -> new ArrayList<>()).add(itemModel);
            }
        }
    }

    private static void rebakeOne(Object itemModel, Identifier changedSpriteId) throws Exception {
        List<?> oldQuads = readQuads(itemModel);
        if (oldQuads == null || oldQuads.isEmpty()) return;

        List<Identifier> layers = collectOrderedLayers(oldQuads);
        if (layers.isEmpty() || !layers.contains(changedSpriteId)) return;

        // Keep only layers that can actually resolve to a live sprite.
        List<Identifier> resolvableLayers = new ArrayList<>();
        for (Identifier layer : layers) {
            if (findSprite(layer) != null) {
                resolvableLayers.add(layer);
            }
        }
        if (resolvableLayers.isEmpty()) {
            return;
        }
        if (!resolvableLayers.contains(changedSpriteId)) {
            // Always keep the changed sprite as primary fallback for bake-time lookups.
            resolvableLayers.add(0, changedSpriteId);
        }

        Object newQuads = buildGeneratedItemQuads(resolvableLayers, changedSpriteId);
        if (!(newQuads instanceof List<?> list) || list.isEmpty()) return;

        writeQuads(itemModel, list);
    }

    private static Object buildGeneratedItemQuads(List<Identifier> layers, Identifier primarySpriteId) throws Exception {
        ClassLoader cl = ItemModelRebaker.class.getClassLoader();

        Class<?> generatedItemModelClass = Class.forName("net.minecraft.client.render.model.json.GeneratedItemModel", false, cl);
        Class<?> modelTexturesBuilderClass = Class.forName("net.minecraft.client.render.model.ModelTextures$Builder", false, cl);
        Class<?> modelTexturesTexturesBuilderClass = Class.forName("net.minecraft.client.render.model.ModelTextures$Textures$Builder", false, cl);
        Class<?> spriteIdentifierClass = Class.forName("net.minecraft.client.util.SpriteIdentifier", false, cl);
        Class<?> modelTexturesClass = Class.forName("net.minecraft.client.render.model.ModelTextures", false, cl);
        Class<?> bakerClass = Class.forName("net.minecraft.client.render.model.Baker", false, cl);
        Class<?> modelBakeSettingsClass = Class.forName("net.minecraft.client.render.model.ModelBakeSettings", false, cl);
        Class<?> simpleModelClass = Class.forName("net.minecraft.client.render.model.SimpleModel", false, cl);

        Object texturesBuilder = modelTexturesTexturesBuilderClass.getConstructor().newInstance();
        Method addSprite = modelTexturesTexturesBuilderClass.getMethod("addSprite", String.class, spriteIdentifierClass);
        Method addRef = modelTexturesTexturesBuilderClass.getMethod("addTextureReference", String.class, String.class);

        Identifier atlasId = resolveBlockOrItemAtlasId();
        for (int i = 0; i < layers.size() && i < 5; i++) {
            String layer = "layer" + i;
            Object spriteIdentifier = spriteIdentifierClass.getConstructor(Identifier.class, Identifier.class).newInstance(atlasId, layers.get(i));
            addSprite.invoke(texturesBuilder, layer, spriteIdentifier);
        }
        addRef.invoke(texturesBuilder, "particle", "layer0");

        Object mergedTexturesBuilder = modelTexturesBuilderClass.getConstructor().newInstance();
        Method addLast = modelTexturesBuilderClass.getMethod("addLast", Class.forName("net.minecraft.client.render.model.ModelTextures$Textures", false, cl));
        Method buildTextures = modelTexturesBuilderClass.getMethod("build", simpleModelClass);
        addLast.invoke(mergedTexturesBuilder, callNoArgs(texturesBuilder, "build"));

        Object dummySimpleModel = Proxy.newProxyInstance(cl, new Class<?>[]{simpleModelClass}, (proxy, method, args) -> {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, args, "textureeditor_rebake");
            }
            if ("name".equals(method.getName())) return "textureeditor_rebake";
            return defaultValue(method.getReturnType());
        });
        Object modelTextures = buildTextures.invoke(mergedTexturesBuilder, dummySimpleModel);

        Object baker = createMinimalBakerProxy(cl, bakerClass, ItemModelRebaker::findSprite, primarySpriteId);
        Object settings = resolveBakeSettings(cl, modelBakeSettingsClass);

        Object generated = generatedItemModelClass.getConstructor().newInstance();
        Object geometry = callNoArgs(generated, "geometry");
        if (geometry == null) {
            throw new IllegalStateException("GeneratedItemModel.geometry() returned null");
        }
        Method bake = findMethod(geometry.getClass(), "bake", modelTexturesClass, bakerClass, modelBakeSettingsClass, simpleModelClass);
        if (bake == null) {
            throw new NoSuchMethodException("Geometry.bake(...) not found on " + geometry.getClass().getName());
        }
        Object bakedGeometry = bake.invoke(geometry, modelTextures, baker, settings, dummySimpleModel);
        if (bakedGeometry == null) {
            throw new IllegalStateException("Geometry.bake(...) returned null");
        }
        return callNoArgs(bakedGeometry, "getAllQuads");
    }

    private interface SpriteFinder {
        Object find(Identifier spriteId) throws Exception;
    }

    private static Object createMinimalBakerProxy(ClassLoader cl, Class<?> bakerClass, SpriteFinder finder, Identifier primary) throws Exception {
        Class<?> simpleModelClass = Class.forName("net.minecraft.client.render.model.SimpleModel", false, cl);
        Class<?> errorGetterClass = Class.forName("net.minecraft.client.render.model.ErrorCollectingSpriteGetter", false, cl);
        Class<?> spriteIdentifierClass = Class.forName("net.minecraft.client.util.SpriteIdentifier", false, cl);
        Class<?> blockModelPartClass = Class.forName("net.minecraft.client.render.model.BlockModelPart", false, cl);

        Object emptyBlockPart = Proxy.newProxyInstance(cl, new Class<?>[]{blockModelPartClass}, (proxy, method, args) -> {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, args, "TextureEditorEmptyBlockPart");
            }
            String n = method.getName();
            if ("getQuads".equals(n)) return List.of();
            if ("useAmbientOcclusion".equals(n)) return Boolean.FALSE;
            if ("particleSprite".equals(n)) return findSprite(primary);
            return defaultValue(method.getReturnType());
        });

        Object spriteGetter = Proxy.newProxyInstance(cl, new Class<?>[]{errorGetterClass}, (proxy, method, args) -> {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, args, "TextureEditorSpriteGetter");
            }
            String n = method.getName();

            // Path A: get(SpriteIdentifier, SimpleModel)
            if ("get".equals(n) && args != null && args.length >= 1 && args[0] != null && spriteIdentifierClass.isInstance(args[0])) {
                Object texId = callNoArgs(args[0], "getTextureId");
                if (texId instanceof Identifier id) {
                    Object sprite = finder.find(id);
                    if (sprite != null) return sprite;
                }
                Object fallback = findSprite(primary);
                if (fallback != null) return fallback;
                throw new IllegalStateException("No sprite available for generated rebake (requested=" + texId + ", primary=" + primary + ")");
            }

            // Path B: get(ModelTextures, String, SimpleModel)
            if ("get".equals(n) && args != null && args.length == 3 && args[0] != null && args[1] instanceof String key) {
                Object spriteIdentifier = call(args[0], "get", new Class<?>[]{String.class}, new Object[]{key});
                if (spriteIdentifier != null && spriteIdentifierClass.isInstance(spriteIdentifier)) {
                    Object texId = callNoArgs(spriteIdentifier, "getTextureId");
                    if (texId instanceof Identifier id) {
                        Object sprite = finder.find(id);
                        if (sprite != null) return sprite;
                    }
                }
                Object fallback = findSprite(primary);
                if (fallback != null) return fallback;
                throw new IllegalStateException("No fallback sprite available for generated rebake (primary=" + primary + ", key=" + key + ")");
            }

            if ("getMissing".equals(n)) {
                Object fallback = findSprite(primary);
                if (fallback != null) return fallback;
                throw new IllegalStateException("No fallback sprite available for generated rebake (primary=" + primary + ")");
            }
            return defaultValue(method.getReturnType());
        });

        InvocationHandler bakerHandler = (proxy, method, args) -> {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, args, "TextureEditorMinimalBaker");
            }
            String n = method.getName();
            if ("getSpriteGetter".equals(n)) return spriteGetter;
            if ("getBlockPart".equals(n)) return emptyBlockPart;
            if ("compute".equals(n) && args != null && args.length == 1 && args[0] != null) {
                Method compute = findMethod(args[0].getClass(), "compute", bakerClass);
                return compute != null ? compute.invoke(args[0], proxy) : null;
            }
            if ("getVec3fInterner".equals(n) || "getPositionMatrixInterner".equals(n)) {
                Class<?> returnType = method.getReturnType();
                return Proxy.newProxyInstance(cl, new Class<?>[]{returnType}, (p, m, a) -> {
                    if (isObjectMethod(m)) {
                        return handleObjectMethod(p, m, a, "TextureEditorInterner");
                    }
                    return a != null && a.length > 0 ? a[0] : defaultValue(m.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        };

        return Proxy.newProxyInstance(cl, new Class<?>[]{bakerClass}, bakerHandler);
    }

    private static Object resolveBakeSettings(ClassLoader cl, Class<?> modelBakeSettingsClass) throws Exception {
        Class<?> modelRotationClass = Class.forName("net.minecraft.client.render.model.ModelRotation", false, cl);
        Field idField = findField(modelRotationClass, "IDENTITY");
        if (idField != null) {
            idField.setAccessible(true);
            Object v = idField.get(null);
            if (v != null) return v;
        }
        for (Field f : modelRotationClass.getDeclaredFields()) {
            if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0 && modelBakeSettingsClass.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null) return v;
            }
        }
        return Proxy.newProxyInstance(cl, new Class<?>[]{modelBakeSettingsClass}, (proxy, method, args) -> {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, args, "TextureEditorModelBakeSettings");
            }
            Object defaultVal = method.getDefaultValue();
            return defaultVal != null ? defaultVal : defaultValue(method.getReturnType());
        });
    }

    private static Identifier resolveBlockOrItemAtlasId() {
        try {
            Class<?> manager = Class.forName("net.minecraft.client.render.model.BakedModelManager");
            Field f = findField(manager, "BLOCK_OR_ITEM");
            if (f != null) {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof Identifier id) return id;
            }
        } catch (Throwable ignored) {
        }
        return Identifier.ofVanilla("block_or_item");
    }

    private static Object findSprite(Identifier spriteId) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;

        // Primary path for 1.21.10: items live in block atlas.
        Sprite sprite = getAtlasSprite(client, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, spriteId);
        if (isValidSprite(sprite)) return sprite;

        // Fallback atlases used by GUI/celestials in this mod.
        sprite = getAtlasSprite(client, Identifier.ofVanilla("textures/atlas/gui.png"), spriteId);
        if (isValidSprite(sprite)) return sprite;

        sprite = getAtlasSprite(client, Identifier.ofVanilla("textures/atlas/celestials.png"), spriteId);
        if (isValidSprite(sprite)) return sprite;

        // Last-resort: try the runtime-mapped block atlas id via reflection field lookup.
        Identifier reflectedBlockAtlasId = resolveStaticIdentifier("net.minecraft.client.texture.SpriteAtlasTexture", "BLOCK_ATLAS_TEXTURE");
        if (reflectedBlockAtlasId != null && !reflectedBlockAtlasId.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)) {
            sprite = getAtlasSprite(client, reflectedBlockAtlasId, spriteId);
            if (isValidSprite(sprite)) return sprite;
        }

        return null;
    }

    private static Sprite getAtlasSprite(MinecraftClient client, Identifier atlasId, Identifier spriteId) {
        var texture = client.getTextureManager().getTexture(atlasId);
        if (!(texture instanceof SpriteAtlasTexture atlas)) return null;
        Sprite sprite = atlas.getSprite(spriteId);
        return isValidSprite(sprite) ? sprite : null;
    }

    private static boolean isValidSprite(Object sprite) {
        if (sprite == null) return false;
        try {
            Object contents = callNoArgs(sprite, "getContents");
            Object id = contents != null ? callNoArgs(contents, "getId") : null;
            return id instanceof Identifier identifier && !"missingno".equals(identifier.getPath());
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<Identifier> collectSpriteIds(List<?> quads) {
        Set<Identifier> out = new LinkedHashSet<>();
        for (Object quad : quads) {
            Identifier id = quadSpriteId(quad);
            if (id != null) out.add(id);
        }
        return out;
    }

    private static List<Identifier> collectOrderedLayers(List<?> quads) {
        List<Identifier> out = new ArrayList<>();
        Set<Identifier> seen = new LinkedHashSet<>();
        for (Object quad : quads) {
            Identifier id = quadSpriteId(quad);
            if (id != null && seen.add(id)) out.add(id);
        }
        return out;
    }

    private static Identifier quadSpriteId(Object quad) {
        try {
            Object sprite = callNoArgs(quad, "getSprite");
            if (sprite == null) sprite = callNoArgs(quad, "sprite");
            Object contents = sprite != null ? callNoArgs(sprite, "getContents") : null;
            Object id = contents != null ? callNoArgs(contents, "getId") : null;
            return id instanceof Identifier identifier ? identifier : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<?> readQuads(Object itemModel) {
        try {
            Field f = findField(itemModel.getClass(), "quads");
            if (f != null) {
                f.setAccessible(true);
                Object v = f.get(itemModel);
                if (v instanceof List<?>) return (List<?>) v;
            }
        } catch (Throwable ignored) {
        }

        // Some versions wrap quads inside an inner baked model object.
        try {
            Field modelField = findField(itemModel.getClass(), "model");
            if (modelField != null) {
                modelField.setAccessible(true);
                Object bakedModel = modelField.get(itemModel);
                if (bakedModel != null) {
                    Field q = findField(bakedModel.getClass(), "quads");
                    if (q != null) {
                        q.setAccessible(true);
                        Object v = q.get(bakedModel);
                        if (v instanceof List<?>) return (List<?>) v;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void writeQuads(Object itemModel, List<?> newQuads) {
        if (tryWriteQuadsOnTarget(itemModel, newQuads)) {
            return;
        }

        // Fallback: nested model object may hold mutable quad storage.
        try {
            Field modelField = findField(itemModel.getClass(), "model");
            if (modelField != null) {
                modelField.setAccessible(true);
                Object nestedModel = modelField.get(itemModel);
                if (nestedModel != null) {
                    tryWriteQuadsOnTarget(nestedModel, newQuads);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean tryWriteQuadsOnTarget(Object target, List<?> newQuads) {
        try {
            Field f = findField(target.getClass(), "quads");
            if (f == null) return false;
            f.setAccessible(true);
            f.set(target, newQuads);

            // Keep optional cached vector in sync when present.
            Field vector = findField(target.getClass(), "vector");
            Method bakeQuads = findMethod(target.getClass(), "bakeQuads", List.class);
            if (vector != null && bakeQuads != null) {
                vector.setAccessible(true);
                bakeQuads.setAccessible(true);
                java.util.function.Supplier<Object> supplier = () -> {
                    try {
                        return bakeQuads.invoke(null, newQuads);
                    } catch (Exception e) {
                        return null;
                    }
                };
                vector.set(target, supplier);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Identifier resolveStaticIdentifier(String className, String fieldName) {
        try {
            Class<?> cls = Class.forName(className);
            Field f = findField(cls, fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            Object v = f.get(null);
            return v instanceof Identifier id ? id : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> c = type;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Object callNoArgs(Object target, String method) throws Exception {
        Method m = findMethod(target.getClass(), method);
        if (m == null) return null;
        return m.invoke(target);
    }

    private static Object call(Object target, String method, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = findMethod(target.getClass(), method, paramTypes);
        if (m == null) return null;
        return m.invoke(target, args);
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args, String label) {
        return switch (method.getName()) {
            case "toString" -> label;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) return null;
        if (!returnType.isPrimitive()) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Character.TYPE) return '\0';
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Double.TYPE) return 0d;
        return null;
    }
}





