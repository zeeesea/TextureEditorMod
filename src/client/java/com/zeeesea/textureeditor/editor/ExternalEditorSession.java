package com.zeeesea.textureeditor.editor;

import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages a single external editor session: exports a texture as PNG,
 * watches for file changes, and reloads the texture live when the user saves.
 * Ported from 1.21.4 to 1.21.11 (uses GpuTexture API instead of direct GL uploads).
 */
public class ExternalEditorSession {

    public enum TextureType {
        BLOCK_ATLAS,    // Block/item textures — uploaded via sprite atlas
        ENTITY_TEXTURE, // Mob/entity textures — uploaded via direct texture
        GUI_SPRITE      // GUI/HUD textures — uploaded to GUI sprite atlas
    }

    private final Identifier textureId;
    private final Identifier spriteId;
    private final Identifier guiSpriteId;
    private final int[][] originalPixels;
    private final int width;
    private final int height;
    private final TextureType textureType;
    private final Path tempFile;
    private Thread watchThread;
    private volatile boolean active = true;
    private WatchService watchService;

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

        File tempDir = getTempDir();
        if (!tempDir.exists()) tempDir.mkdirs();

        String safeName = textureId.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
        this.tempFile = new File(tempDir, safeName).toPath();

        if (!Files.exists(tempFile)) {
            exportPixels(pixels);
        }

        if (launchEditor) {
            doLaunchEditor(editorPath);
        }

        startWatcher();

        System.out.println("[TextureEditor] External editor session started for: " + textureId);
        Minecraft.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    "External Editor", "Opened in external editor. Save to apply changes live.")
        );
    }

    private void startWatcher() {
        String safeName = tempFile.getFileName().toString();
        this.watchThread = new Thread(this::watchLoop, "TextureEditor-FileWatcher-" + safeName);
        this.watchThread.setDaemon(true);
        this.watchThread.start();
    }

    public static File getTempDir() {
        return new File(Minecraft.getInstance().gameDirectory, "textureeditor_temp");
    }

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

    public void forceExportPixels(int[][] pixels) {
        exportPixels(pixels);
    }

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
            Minecraft.getInstance().execute(() ->
                NotificationHelper.addToast(SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                        "Editor Failed", "Could not launch: " + editorPath)
            );
        }
    }

    private void watchLoop() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            tempFile.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            long lastApply = 0;
            String fileName = tempFile.getFileName().toString();

            while (active) {
                WatchKey key;
                try {
                    key = watchService.poll(500, TimeUnit.MILLISECONDS);
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
                    long now = System.currentTimeMillis();
                    if (now - lastApply < 600) continue;

                    Thread.sleep(600);
                    lastApply = System.currentTimeMillis();

                    int[][] newPixels = readPixels();
                    if (newPixels == null) continue;

                    System.out.println("[TextureEditor] Detected external change, applying live: " + textureId);

                    Minecraft.getInstance().execute(() -> {
                        applyChanges(newPixels);
                        NotificationHelper.addToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
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
     * Ported for 1.21.11: uses GpuTexture writeToTexture instead of img.upload().
     */
    private void applyChanges(int[][] newPixels) {
        int newW = newPixels.length;
        int newH = newW > 0 ? newPixels[0].length : 0;

        TextureManager tm = TextureManager.getInstance();

        if (originalPixels != null) {
            tm.storeOriginal(textureId, originalPixels, width, height);
        }

        if (textureType == TextureType.BLOCK_ATLAS && spriteId != null) {
            tm.applyLive(spriteId, newPixels, newW, newH, originalPixels);
        } else if (textureType == TextureType.GUI_SPRITE) {
            tm.putTexture(textureId, newPixels, newW, newH);
            // Try to find sprite in GUI atlas and upload via TextureManager's writeSpritePixels
            Identifier lookupId = guiSpriteId != null ? guiSpriteId : spriteId;
            if (lookupId != null) {
                // Derive sprite ID from full path
                String path = lookupId.getPath();
                if (path.startsWith("textures/") && path.endsWith(".png")) {
                    path = path.substring("textures/".length(), path.length() - ".png".length());
                }
                Identifier derivedSpriteId = Identifier.of(lookupId.getNamespace(), path);
                tm.applyLive(derivedSpriteId, newPixels, newW, newH, originalPixels);
            }
        } else {
            // Entity texture — upload directly via GpuTexture API
            tm.putTexture(textureId, newPixels, newW, newH);
            uploadEntityTexture(textureId, newPixels, newW, newH);
        }
    }

    /**
     * Upload entity texture using 1.21.11 GpuTexture API.
     */
    private void uploadEntityTexture(Identifier texId, int[][] pixels, int w, int h) {
        Minecraft client = Minecraft.getInstance();
        try {
            NativeImage img = new NativeImage(w, h, false);
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++)
                    img.setColorArgb(x, y, pixels[x][y]);

            var existing = client.getTextureManager().getTexture(texId);
            if (existing != null) {
                try {
                    var gpuTex = existing.getGlTexture();
                    if (gpuTex != null && gpuTex.getWidth(0) == w && gpuTex.getHeight(0) == h) {
                        com.mojang.blaze3d.systems.RenderSystem.getDevice()
                            .createCommandEncoder()
                            .writeToTexture(gpuTex, img);
                        img.close();
                        System.out.println("[TextureEditor] Direct entity upload OK: " + texId);
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("[TextureEditor] Direct entity upload failed: " + e.getMessage());
                }
            }

            // Fallback: create a new dynamic texture
            try {
                if (existing != null) existing.close();
            } catch (Exception ignored) {}
            var dynamicTex = new DynamicTexture(() -> "textureeditor_ext", img);
            client.getTextureManager().register(texId, dynamicTex);
            dynamicTex.upload();
            System.out.println("[TextureEditor] Fallback entity upload OK: " + texId);
        } catch (Exception e) {
            System.out.println("[TextureEditor] Entity upload error: " + e.getMessage());
        }
    }

    public void stop() {
        active = false;
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
        if (watchThread != null) watchThread.interrupt();
        System.out.println("[TextureEditor] External editor session stopped for: " + textureId);
    }

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
