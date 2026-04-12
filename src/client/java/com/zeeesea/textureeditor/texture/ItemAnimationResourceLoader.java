package com.zeeesea.textureeditor.texture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads animated item texture data from resource packs (png + .mcmeta).
 */
public final class ItemAnimationResourceLoader {

    public record LoadedAnimation(List<int[][]> frames, int width, int height, int frameTimeTicks) {}

    private ItemAnimationResourceLoader() {}

    public static LoadedAnimation load(Identifier textureId) {
        if (textureId == null) return null;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourceManager() == null) return null;

        Identifier mcmetaId = Identifier.of(textureId.getNamespace(), textureId.getPath() + ".mcmeta");
        JsonObject animation = readAnimationObject(client, mcmetaId);
        if (animation == null) return null;

        try (InputStream pngStream = client.getResourceManager().getResource(textureId).orElseThrow().getInputStream();
             NativeImage image = NativeImage.read(pngStream)) {

            int width = image.getWidth();
            int frameHeight = width;
            if (animation.has("height") && animation.get("height").isJsonPrimitive()) {
                frameHeight = Math.max(1, animation.get("height").getAsInt());
            }
            if (frameHeight <= 0 || frameHeight > image.getHeight()) return null;

            int frameCountFromSheet = image.getHeight() / frameHeight;
            if (frameCountFromSheet <= 1 || image.getHeight() % frameHeight != 0) return null;

            List<Integer> frameOrder = parseFrameOrder(animation, frameCountFromSheet);
            if (frameOrder.isEmpty()) {
                for (int i = 0; i < frameCountFromSheet; i++) frameOrder.add(i);
            }

            List<int[][]> frames = new ArrayList<>(frameOrder.size());
            for (int idx : frameOrder) {
                if (idx < 0 || idx >= frameCountFromSheet) continue;
                int[][] frame = new int[width][frameHeight];
                int yOffset = idx * frameHeight;
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < frameHeight; y++) {
                        frame[x][y] = image.getColorArgb(x, y + yOffset);
                    }
                }
                frames.add(frame);
            }

            if (frames.size() <= 1) return null;

            int frameTimeTicks = 1;
            if (animation.has("frametime") && animation.get("frametime").isJsonPrimitive()) {
                frameTimeTicks = Math.max(1, animation.get("frametime").getAsInt());
            }

            return new LoadedAnimation(frames, width, frameHeight, frameTimeTicks);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject readAnimationObject(Minecraft client, Identifier mcmetaId) {
        try (InputStream mcmetaStream = client.getResourceManager().getResource(mcmetaId).orElseThrow().getInputStream()) {
            JsonElement root = JsonParser.parseReader(new java.io.InputStreamReader(mcmetaStream));
            if (!root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("animation") || !obj.get("animation").isJsonObject()) return null;
            return obj.getAsJsonObject("animation");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Integer> parseFrameOrder(JsonObject animation, int maxFrameIndexExclusive) {
        List<Integer> out = new ArrayList<>();
        if (!animation.has("frames") || !animation.get("frames").isJsonArray()) return out;

        JsonArray arr = animation.getAsJsonArray("frames");
        for (JsonElement e : arr) {
            Integer frameIdx = null;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                frameIdx = e.getAsInt();
            } else if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("index") && o.get("index").isJsonPrimitive()) {
                    frameIdx = o.get("index").getAsInt();
                }
            }
            if (frameIdx != null && frameIdx >= 0 && frameIdx < maxFrameIndexExclusive) {
                out.add(frameIdx);
            }
        }
        return out;
    }
}


