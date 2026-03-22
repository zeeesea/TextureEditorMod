package com.zeeesea.textureeditor.editor;

import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;

/**
 * Manages a single external editor session: exports a texture as PNG,
 * watches for file changes, and reloads the texture live when the user saves.
 */
public class ExternalEditorSession {

    public enum TextureType {
        BLOCK_ATLAS,    // Block/item textures — uploaded via sprite atlas
        ENTITY_TEXTURE, // Mob/entity textures — uploaded via direct texture
        GUI_SPRITE      // GUI/HUD textures — uploaded to GUI sprite atlas
    }

    private final Identifier textureId;
    private final Identifier spriteId; // Used for BLOCK_ATLAS and GUI_SPRITE types
    private final Identifier guiSpriteId; // Original GUI sprite path (e.g. gui/sprites/hud/hotbar)
    private final int[][] originalPixels;
    private final int width;
    private final int height;
    private final TextureType textureType;
    private final Path tempFile;
    private Thread watchThread;
    private volatile boolean active = true;
    private WatchService watchService;

    /**
     * Create a new session.
     *
     * @param pixels The current pixels to export (may already be modified)
     * @param originalPixels The original unmodified pixels for reset/preview
     * @param launchEditor Whether to launch the editor process
     */
    public ExternalEditorSession(Identifier textureId, Identifier spriteId,
                                  int[][] pixels, int[][] originalPixels,
                                  int width, int height,
                                  TextureType textureType, String editorPath,
                                  boolean launchEditor, Identifier guiSpriteId) {
        this.textureId = textureId;
        this.spriteId = spriteId;
        this.guiSpriteId = guiSpriteId;
        this.originalPixels = originalPixels;
        this.width = width;
        this.height = height;
        this.textureType = textureType;

        // Create temp directory
        File tempDir = getTempDir();
        if (!tempDir.exists()) tempDir.mkdirs();

        // Generate temp file name from texture ID
        String safeName = textureId.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
        this.tempFile = new File(tempDir, safeName).toPath();

        // Only export pixels if the file doesn't exist yet.
        // If it already exists, the user may have edits in their editor — don't overwrite.
        if (!Files.exists(tempFile)) {
            exportPixels(pixels);
        }

        // Launch external editor if requested
        if (launchEditor) {
            doLaunchEditor(editorPath);
        }

        // Start file watcher thread
        startWatcher();

        System.out.println("[TextureEditor] External editor session started for: " + textureId);
        MinecraftClient.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.Type.PERIODIC_NOTIFICATION,
                    "External Editor", "Opened in external editor. Save to apply changes live.")
        );
    }

    /** Legacy constructor that always launches editor */
    public ExternalEditorSession(Identifier textureId, Identifier spriteId,
                                  int[][] pixels, int[][] originalPixels,
                                  int width, int height,
                                  TextureType textureType, String editorPath) {
        this(textureId, spriteId, pixels, originalPixels, width, height, textureType, editorPath, true, null);
    }

    private void startWatcher() {
        String safeName = tempFile.getFileName().toString();
        this.watchThread = new Thread(this::watchLoop, "TextureEditor-FileWatcher-" + safeName);
        this.watchThread.setDaemon(true);
        this.watchThread.start();
    }

    public static File getTempDir() {
        return new File(MinecraftClient.getInstance().runDirectory, "textureeditor_temp");
    }

    /**
     * Export int[][] pixels (ARGB, [x][y]) to a PNG file.
     */
    private void exportPixels(int[][] pixels) {
        try {
            int w = pixels.length;
            int h = w > 0 ? pixels[0].length : 0;
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    image.setRGB(x, y, pixels[x][y]);
                }
            }
            ImageIO.write(image, "png", tempFile.toFile());
            System.out.println("[TextureEditor] Exported temp texture: " + tempFile);
        } catch (IOException e) {
            System.out.println("[TextureEditor] Failed to export temp texture: " + e.getMessage());
        }
    }

    /**
     * Force-export pixels to the temp file (used for reset).
     * This overwrites whatever the external editor has.
     */
    public void forceExportPixels(int[][] pixels) {
        exportPixels(pixels);
    }

    /**
     * Read PNG file back into int[][] pixels (ARGB, [x][y]).
     */
    private int[][] readPixels() {
        try {
            BufferedImage image = ImageIO.read(tempFile.toFile());
            if (image == null) return null;
            int w = image.getWidth();
            int h = image.getHeight();
            int[][] pixels = new int[w][h];
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    pixels[x][y] = image.getRGB(x, y);
                }
            }
            return pixels;
        } catch (IOException e) {
            System.out.println("[TextureEditor] Failed to read temp texture: " + e.getMessage());
            return null;
        }
    }

    private void doLaunchEditor(String editorPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(editorPath, tempFile.toString());
            pb.redirectErrorStream(true);
            pb.start();
            System.out.println("[TextureEditor] Launched external editor: " + editorPath);
        } catch (IOException e) {
            System.out.println("[TextureEditor] Failed to launch editor: " + e.getMessage());
            MinecraftClient.getInstance().execute(() ->
                NotificationHelper.addToast(SystemToast.Type.PACK_LOAD_FAILURE,
                        "Editor Failed", "Could not launch: " + editorPath)
            );
        }
    }

    /**
     * Watch loop running on daemon thread. Monitors the temp file for modifications.
     */
    private void watchLoop() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            tempFile.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            long lastApply = 0;
            String fileName = tempFile.getFileName().toString();

            while (active) {
                WatchKey key;
                try {
                    key = watchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    break;
                }
                if (key == null) continue;

                boolean fileChanged = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = (Path) event.context();
                    if (changed != null && changed.toString().equals(fileName)) {
                        fileChanged = true;
                    }
                }
                key.reset();

                if (fileChanged) {
                    // Debounce: wait 600ms after detecting a change to avoid partial writes
                    long now = System.currentTimeMillis();
                    if (now - lastApply < 600) continue;

                    Thread.sleep(600); // Wait for the editor to finish writing
                    lastApply = System.currentTimeMillis();

                    // Read the modified file
                    int[][] newPixels = readPixels();
                    if (newPixels == null) continue;

                    System.out.println("[TextureEditor] Detected external change, applying live: " + textureId);

                    // Apply on the render thread
                    MinecraftClient.getInstance().execute(() -> {
                        applyChanges(newPixels);
                        NotificationHelper.addToast(SystemToast.Type.PERIODIC_NOTIFICATION,
                                "Texture Updated", "External changes applied live!");
                    });
                }
            }
        } catch (Exception e) {
            if (active) {
                System.out.println("[TextureEditor] File watcher error: " + e.getMessage());
            }
        }
    }

    /**
     * Apply changed pixels live to the game. Must be called on the render thread.
     */
    private void applyChanges(int[][] newPixels) {
        int newW = newPixels.length;
        int newH = newW > 0 ? newPixels[0].length : 0;

        TextureManager tm = TextureManager.getInstance();

        // Store original if not already stored
        if (originalPixels != null) {
            tm.storeOriginal(textureId, originalPixels, width, height);
        }

        if (textureType == TextureType.BLOCK_ATLAS && spriteId != null) {
            // Block/item: use atlas upload
            tm.applyLive(spriteId, newPixels, newW, newH, originalPixels);
        } else if (textureType == TextureType.GUI_SPRITE) {
            // GUI sprite: find in atlases and upload
            tm.putTexture(textureId, newPixels, newW, newH);
            MinecraftClient client = MinecraftClient.getInstance();
            try (var img = new net.minecraft.client.texture.NativeImage(newW, newH, false)) {
                for (int x = 0; x < newW; x++)
                    for (int y = 0; y < newH; y++)
                        img.setColorArgb(x, y, newPixels[x][y]);

                // Try to find the sprite in GUI or block atlases
                boolean uploaded = false;
                Identifier lookupId = guiSpriteId != null ? guiSpriteId : spriteId;
                if (lookupId != null) {
                    uploaded = tryUploadToAtlas(client, img, lookupId);
                }
                if (!uploaded && textureId != null) {
                    // Try with a derived sprite ID from the texture path
                    String path = textureId.getPath();
                    if (path.startsWith("textures/") && path.endsWith(".png")) {
                        path = path.substring("textures/".length(), path.length() - ".png".length());
                    }
                    Identifier derivedId = Identifier.of(textureId.getNamespace(), path);
                    uploaded = tryUploadToAtlas(client, img, derivedId);
                }
                if (!uploaded) {
                    // Fallback: register as dynamic texture using the Supplier<String> constructor available in this workspace
                    net.minecraft.client.texture.NativeImageBackedTexture dynamicTex =
                            new net.minecraft.client.texture.NativeImageBackedTexture(() -> textureId.toString(), img);
                    client.getTextureManager().registerTexture(textureId, dynamicTex);
                    // upload() will push the current image to the GPU
                    dynamicTex.upload();
                    System.out.println("[TextureEditor] Applied GUI texture as dynamic: " + textureId);
                }
            }
        } else {
            // Entity: upload directly to named texture
            tm.putTexture(textureId, newPixels, newW, newH);
            MinecraftClient client = MinecraftClient.getInstance();
            try (var img = new net.minecraft.client.texture.NativeImage(newW, newH, false)) {
                for (int x = 0; x < newW; x++)
                    for (int y = 0; y < newH; y++)
                        img.setColorArgb(x, y, newPixels[x][y]);
                var tex = client.getTextureManager().getTexture(textureId);
                if (tex != null) {
                    if (tex instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt) {
                        nibt.setImage(img);
                        nibt.upload();
                    } else {
                        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
                                com.mojang.blaze3d.opengl.GlStateManager._bindTexture(com.zeeesea.textureeditor.util.TextureCompat.getGlId(tex));
                        com.zeeesea.textureeditor.util.NativeImageCompat.upload(img, 0, 0, 0, false);
                    }
                } else {
                    net.minecraft.client.texture.NativeImageBackedTexture dynamicTex =
                            new net.minecraft.client.texture.NativeImageBackedTexture(() -> textureId.toString(), img);
                    client.getTextureManager().registerTexture(textureId, dynamicTex);
                    dynamicTex.upload();
                }
            }
        }
    }

    /**
     * Try to find a sprite in the block atlas or GUI atlas and upload the image to it.
     * Returns true if successful.
     */
    private boolean tryUploadToAtlas(MinecraftClient client, net.minecraft.client.texture.NativeImage img, Identifier id) {
        // Try block atlas
        var blockAtlas = client.getBakedModelManager().getAtlas(
                net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        var sprite = blockAtlas.getSprite(id);
            if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")
                && sprite.getContents().getId().equals(id)) {
            com.mojang.blaze3d.opengl.GlStateManager._bindTexture(com.zeeesea.textureeditor.util.TextureCompat.getGlId(blockAtlas));
            com.zeeesea.textureeditor.util.NativeImageCompat.upload(img, 0, sprite.getX(), sprite.getY(), false);
            System.out.println("[TextureEditor] Uploaded GUI sprite to block atlas: " + id);
            return true;
        }

        // Try GUI atlas
        Identifier guiAtlasId = Identifier.of("textures/atlas/gui.png");
        var tex = client.getTextureManager().getTexture(guiAtlasId);
        if (tex instanceof net.minecraft.client.texture.SpriteAtlasTexture guiAtlas) {
            sprite = guiAtlas.getSprite(id);
                if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")
                    && sprite.getContents().getId().equals(id)) {
                com.mojang.blaze3d.opengl.GlStateManager._bindTexture(com.zeeesea.textureeditor.util.TextureCompat.getGlId(guiAtlas));
                com.zeeesea.textureeditor.util.NativeImageCompat.upload(img, 0, sprite.getX(), sprite.getY(), false);
                System.out.println("[TextureEditor] Uploaded GUI sprite to GUI atlas: " + id);
                return true;
            }
            // Try without gui/sprites/ prefix
            if (id.getPath().startsWith("gui/sprites/")) {
                String shortPath = id.getPath().substring("gui/sprites/".length());
                Identifier shortId = Identifier.of(id.getNamespace(), shortPath);
                sprite = guiAtlas.getSprite(shortId);
                    if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                    com.mojang.blaze3d.opengl.GlStateManager._bindTexture(com.zeeesea.textureeditor.util.TextureCompat.getGlId(guiAtlas));
                    com.zeeesea.textureeditor.util.NativeImageCompat.upload(img, 0, sprite.getX(), sprite.getY(), false);
                    System.out.println("[TextureEditor] Uploaded GUI sprite to GUI atlas (short ID): " + shortId);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Stop this session: close the watcher. Does NOT delete temp file
     * so the external editor can still have it open.
     */
    public void stop() {
        active = false;
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
        if (watchThread != null) watchThread.interrupt();

        System.out.println("[TextureEditor] External editor session stopped for: " + textureId);
    }

    /**
     * Stop and also delete the temp file (used on full cleanup).
     */
    public void stopAndCleanup() {
        stop();
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {}
    }

    public Identifier getTextureId() { return textureId; }
    public Identifier getSpriteId() { return spriteId; }
    public Identifier getGuiSpriteId() { return guiSpriteId; }
    public int[][] getOriginalPixels() { return originalPixels; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public TextureType getTextureType() { return textureType; }
    public boolean isActive() { return active; }
    public Path getTempFile() { return tempFile; }
}
