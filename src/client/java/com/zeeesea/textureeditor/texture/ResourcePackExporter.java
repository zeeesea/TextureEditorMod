package com.zeeesea.textureeditor.texture;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports all modified textures as a resource pack ZIP file.
 */
public class ResourcePackExporter {

    /**
     * Export all modified textures as a resource pack.
     *
     * @param packName    Name of the resource pack
     * @param description Description of the resource pack
     * @param author      Author name (can be empty)
     * @param iconPixels  Optional 64x64 pack icon pixels (ARGB), null for no icon
     * @param iconWidth   Icon width
     * @param iconHeight  Icon height
     * @return The exported file, or null on failure
     */
    public static File export(String packName, String description, String author,
                              int[][] iconPixels, int iconWidth, int iconHeight) {
        TextureManager manager = TextureManager.getInstance();
        if (!manager.hasModifiedTextures()) return null;

        // Determine output directory
        File resourcePacksDir = new File(MinecraftClient.getInstance().runDirectory, "resourcepacks");
        if (!resourcePacksDir.exists()) {
            resourcePacksDir.mkdirs();
        }

        // Sanitize filename
        String sanitized = packName.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
        if (sanitized.isEmpty()) sanitized = "TextureEditorPack";
        File outputFile = new File(resourcePacksDir, sanitized + ".zip");

        // Avoid overwriting
        int counter = 1;
        while (outputFile.exists()) {
            outputFile = new File(resourcePacksDir, sanitized + "_" + counter + ".zip");
            counter++;
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Write pack.mcmeta
            JsonObject packMcmeta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 75); // 1.21.11
            // For pack_format > 64, Minecraft requires min_format and max_format as
            // top-level fields in the pack object (not inside supported_formats)
            pack.addProperty("min_format", 1);
            pack.addProperty("max_format", 9999);
            // supported_formats for older versions (1.20.2 - 1.21.4) that use this field instead
            JsonObject supportedFormats = new JsonObject();
            supportedFormats.addProperty("min_inclusive", 1);
            supportedFormats.addProperty("max_inclusive", 9999);
            pack.add("supported_formats", supportedFormats);

            // Build description with author if provided
            String desc = description != null && !description.isEmpty() ? description : packName;
            if (author != null && !author.isEmpty()) {
                desc += "\nBy " + author;
            }
            pack.addProperty("description", desc);
            packMcmeta.add("pack", pack);

            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write(packMcmeta.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Write pack.png if icon provided
            if (iconPixels != null) {
                try (NativeImage iconImg = new NativeImage(iconWidth, iconHeight, false)) {
                    for (int x = 0; x < iconWidth; x++) {
                        for (int y = 0; y < iconHeight; y++) {
                            iconImg.setColorArgb(x, y, iconPixels[x][y]);
                        }
                    }
                    byte[] pngBytes = nativeImageToBytes(iconImg);
                    zos.putNextEntry(new ZipEntry("pack.png"));
                    zos.write(pngBytes);
                    zos.closeEntry();
                }
            }

            Set<Identifier> exportedAnimatedTextures = new HashSet<>();

            // Write animated textures first (stacked PNG + mcmeta)
            for (Identifier textureId : manager.getAnimatedTextureIds()) {
                TextureManager.ItemAnimationData anim = manager.getItemAnimation(textureId);
                if (anim == null || anim.frames() == null || anim.frames().size() <= 1) continue;

                int frameCount = anim.frames().size();
                int w = anim.width();
                int h = anim.height();
                if (w <= 0 || h <= 0) continue;

                String path = "assets/" + textureId.getNamespace() + "/" + textureId.getPath();

                try (NativeImage stacked = new NativeImage(w, h * frameCount, false)) {
                    for (int i = 0; i < frameCount; i++) {
                        int[][] frame = anim.frames().get(i);
                        if (frame == null || frame.length != w || frame[0].length != h) continue;
                        for (int x = 0; x < w; x++) {
                            for (int y = 0; y < h; y++) {
                                stacked.setColorArgb(x, y + i * h, frame[x][y]);
                            }
                        }
                    }

                    byte[] pngBytes = nativeImageToBytes(stacked);
                    zos.putNextEntry(new ZipEntry(path));
                    zos.write(pngBytes);
                    zos.closeEntry();
                }

                JsonObject animationMcmeta = new JsonObject();
                JsonObject animation = new JsonObject();
                animation.addProperty("frametime", Math.max(1, anim.frameTimeTicks()));
                if (anim.pingPong() && frameCount > 1) {
                    JsonArray frames = new JsonArray();
                    for (int i = 0; i < frameCount; i++) frames.add(i);
                    for (int i = frameCount - 2; i >= 1; i--) frames.add(i);
                    animation.add("frames", frames);
                }
                animationMcmeta.add("animation", animation);
                zos.putNextEntry(new ZipEntry(path + ".mcmeta"));
                zos.write(animationMcmeta.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                exportedAnimatedTextures.add(textureId);
            }

            // Write each non-animated modified texture
            for (Identifier textureId : manager.getModifiedTextureIds()) {
                if (exportedAnimatedTextures.contains(textureId)) continue;
                int[][] pixels = manager.getPixels(textureId);
                int[] dims = manager.getDimensions(textureId);
                if (pixels == null || dims == null) continue;

                int w = dims[0], h = dims[1];

                // Convert Identifier to file path: "assets/namespace/path"
                String path = "assets/" + textureId.getNamespace() + "/" + textureId.getPath();

                try (NativeImage img = new NativeImage(w, h, false)) {
                    for (int x = 0; x < w; x++) {
                        for (int y = 0; y < h; y++) {
                            img.setColorArgb(x, y, pixels[x][y]);
                        }
                    }
                    byte[] pngBytes = nativeImageToBytes(img);
                    zos.putNextEntry(new ZipEntry(path));
                    zos.write(pngBytes);
                    zos.closeEntry();
                }
            }

            zos.flush();
            return outputFile;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Legacy overload for backward compatibility.
     */
    public static File export(String packName, int[][] iconPixels, int iconWidth, int iconHeight) {
        return export(packName, packName, "", iconPixels, iconWidth, iconHeight);
    }

    /**
     * Convert a NativeImage to PNG bytes by writing to a temp file.
     */
    private static byte[] nativeImageToBytes(NativeImage image) throws IOException {
        Path tempFile = Files.createTempFile("textureeditor_", ".png");
        try {
            image.writeTo(tempFile);
            return Files.readAllBytes(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
