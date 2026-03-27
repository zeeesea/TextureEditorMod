package com.zeeesea.textureeditor.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * Small helper to create cropped icons from arbitrary textures.
 * Generates a runtime texture (registered in the TextureManager) and caches the result.
 */
public class TextureIconManager {

    public static class IconData {
        public final Identifier id;
        public final int width;
        public final int height;

        public IconData(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    private static final ConcurrentHashMap<Identifier, CompletableFuture<IconData>> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("TextureIconGen");
        return t;
    });

    public static CompletableFuture<IconData> getOrCreateIconForTexture(Identifier textureId) {
        Identifier fullId = asFullTextureId(textureId);
        return CACHE.computeIfAbsent(fullId, id -> CompletableFuture.supplyAsync(() -> createIcon(id), EXEC));
    }

    private static IconData createIcon(Identifier fullId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            var opt = client.getResourceManager().getResource(fullId);
            if (opt.isEmpty()) return null;
            try (InputStream is = opt.get().getInputStream()) {
                NativeImage img = NativeImage.read(is);

                NativeImage cropped = cropTransparentBorder(img);
                img.close();
                if (cropped == null) return null; // fully transparent or error

                // register as dynamic texture (use supplier constructor for naming)
                String safe = sha1(fullId.toString());
                Identifier genId = Identifier.of("textureeditor_runtime", "icon_" + safe);

                // Registration must happen on the render thread. Enqueue the registration and wait
                // for it to complete so callers get a valid texture id.
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                final String name = "textureeditor_icon_" + safe;
                RenderSystem.queueFencedTask(() -> {
                    try {
                        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> name, cropped);
                        client.getTextureManager().registerTexture(genId, tex);
                    } catch (Exception e) {
                        System.out.println("[TextureEditor] Failed to register runtime texture " + genId + " : " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });

                try {
                    // Wait a short time for the render thread to perform registration.
                    // If interrupted or it times out, we still return the IconData (texture might not be ready yet),
                    // but avoid blocking forever.
                    latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }

                return new IconData(genId, cropped.getWidth(), cropped.getHeight());
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to generate icon for " + fullId + " : " + e.getMessage());
            return null;
        }
    }

    private static NativeImage cropTransparentBorder(NativeImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getColorArgb(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a != 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < 0 || maxY < 0) {
            // fully transparent
            return null;
        }

        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        NativeImage out = new NativeImage(cw, ch, true);
        for (int yy = 0; yy < ch; yy++) {
            for (int xx = 0; xx < cw; xx++) {
                out.setColor(xx, yy, src.getColorArgb(minX + xx, minY + yy));
            }
        }
        return out;
    }

    private static Identifier asFullTextureId(Identifier id) {
        return id.getPath().startsWith("textures/")
                ? id
                : Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length && sb.length() < 16; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            if (sb.length() > 16) sb.setLength(16);
            while (sb.length() < 16) sb.append('0');
            return sb.toString();
        } catch (Exception e) {
            // fallback: lowercase hex of hashCode (remove minus sign)
            String h = Integer.toHexString(s.hashCode());
            h = h.replace('-', '0');
            return h.length() > 16 ? h.substring(0, 16) : String.format("%-16s", h).replace(' ', '0');
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }
}





