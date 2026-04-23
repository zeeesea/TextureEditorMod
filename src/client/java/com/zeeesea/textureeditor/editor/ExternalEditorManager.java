package com.zeeesea.textureeditor.editor;

import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton managing active external editor sessions.
 * Ported from 1.21.4 to 1.21.11 (uses GpuTexture API).
 */
public class ExternalEditorManager {

    private static final ExternalEditorManager INSTANCE = new ExternalEditorManager();
    private final ConcurrentHashMap<String, ExternalEditorSession> sessions = new ConcurrentHashMap<>();

    private ExternalEditorManager() {}

    public static ExternalEditorManager getInstance() {
        return INSTANCE;
    }

    public boolean isExternalEditorEnabled() {
        ModSettings s = ModSettings.getInstance();
        if (!s.useExternalEditor) return false;
        String path = getEditorPath();
        if (path == null || path.isEmpty()) return false;
        return new File(path).exists() || !path.contains(File.separator);
    }

    public String getEditorPath() {
        ModSettings s = ModSettings.getInstance();

        if (s.externalEditorCustomPath != null && !s.externalEditorCustomPath.isEmpty()) {
            if (new File(s.externalEditorCustomPath).exists()) {
                return s.externalEditorCustomPath;
            }
        }

        if (s.selectedEditorName != null && !s.selectedEditorName.isEmpty()) {
            for (ExternalEditorDetector.DetectedEditor editor : ExternalEditorDetector.detectEditors()) {
                if (editor.name().equals(s.selectedEditorName)) {
                    return editor.executablePath();
                }
            }
        }

        ExternalEditorDetector.DetectedEditor def = ExternalEditorDetector.getDefault();
        return def != null ? def.executablePath() : null;
    }

    public void startAtlasSession(Identifier textureId, Identifier spriteId,
                                   int[][] pixels, int[][] originalPixels,
                                   int width, int height) {
        String key = textureId.toString();
        String editorPath = getEditorPath();
        if (editorPath == null) { warnNoEditor(); return; }

        int[][] currentPixels = TextureManager.getInstance().getPixels(textureId);
        if (currentPixels != null) pixels = currentPixels;

        ExternalEditorSession existing = sessions.get(key);
        if (existing != null && existing.isActive()) {
            syncTempFile(existing, pixels);
            return;
        }
        if (existing != null) existing.stop();

        ExternalEditorSession session = new ExternalEditorSession(
                textureId, spriteId, pixels, originalPixels, width, height,
                ExternalEditorSession.TextureType.BLOCK_ATLAS, editorPath,
                true, null
        );
        sessions.put(key, session);
    }

    public void startEntitySession(Identifier textureId,
                                    int[][] pixels, int[][] originalPixels,
                                    int width, int height) {
        String key = textureId.toString();
        String editorPath = getEditorPath();
        if (editorPath == null) { warnNoEditor(); return; }

        int[][] currentPixels = TextureManager.getInstance().getPixels(textureId);
        if (currentPixels != null) pixels = currentPixels;

        ExternalEditorSession existing = sessions.get(key);
        if (existing != null && existing.isActive()) {
            syncTempFile(existing, pixels);
            return;
        }
        if (existing != null) existing.stop();

        ExternalEditorSession session = new ExternalEditorSession(
                textureId, null, pixels, originalPixels, width, height,
                ExternalEditorSession.TextureType.ENTITY_TEXTURE, editorPath,
                true, null
        );
        sessions.put(key, session);
    }

    public void startGuiSession(Identifier textureId, Identifier guiSpriteId,
                                 int[][] pixels, int[][] originalPixels,
                                 int width, int height) {
        String key = textureId.toString();
        String editorPath = getEditorPath();
        if (editorPath == null) { warnNoEditor(); return; }

        int[][] currentPixels = TextureManager.getInstance().getPixels(textureId);
        if (currentPixels != null) pixels = currentPixels;

        ExternalEditorSession existing = sessions.get(key);
        if (existing != null && existing.isActive()) {
            syncTempFile(existing, pixels);
            return;
        }
        if (existing != null) existing.stop();

        ExternalEditorSession session = new ExternalEditorSession(
                textureId, null, pixels, originalPixels, width, height,
                ExternalEditorSession.TextureType.GUI_SPRITE, editorPath,
                true, guiSpriteId
        );
        sessions.put(key, session);
    }

    private void syncTempFile(ExternalEditorSession session, int[][] pixels) {
        Minecraft.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    "Already Open", "Texture is already open in external editor.")
        );
    }

    public void syncAllTempFiles() {
        for (var entry : sessions.entrySet()) {
            ExternalEditorSession session = entry.getValue();
            Identifier texId = session.getTextureId();
            int[][] currentPixels = TextureManager.getInstance().getPixels(texId);
            if (currentPixels != null) {
                session.forceExportPixels(currentPixels);
            } else {
                int[][] orig = session.getOriginalPixels();
                if (orig != null) {
                    session.forceExportPixels(orig);
                }
            }
        }
    }

    public void resetTexture(Identifier textureId) {
        String key = textureId.toString();
        ExternalEditorSession session = sessions.get(key);

        int[][] origPixels = null;
        if (session != null) {
            origPixels = session.getOriginalPixels();
        }

        TextureManager.getInstance().removeTexture(textureId);

        if (origPixels != null) {
            if (session != null) session.forceExportPixels(origPixels);

            if (session != null && session.getTextureType() == ExternalEditorSession.TextureType.BLOCK_ATLAS
                    && session.getSpriteId() != null) {
                TextureManager.getInstance().applyLive(session.getSpriteId(), origPixels,
                        session.getWidth(), session.getHeight());
            } else if (session != null) {
                uploadEntityTexture(textureId, origPixels, session.getWidth(), session.getHeight());
            }
        }

        Minecraft.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    "Texture Reset", "Texture reset to default.")
        );
    }

    public static void resetTextureStatic(Identifier textureId, Identifier spriteId,
                                           int[][] originalPixels, int width, int height) {
        TextureManager tm = TextureManager.getInstance();
        tm.removeTexture(textureId);

        String safeName = textureId.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
        Path tempFile = new File(ExternalEditorSession.getTempDir(), safeName).toPath();
        try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}

        if (spriteId != null) {
            tm.applyLive(spriteId, originalPixels, width, height);
        } else {
            INSTANCE.uploadEntityTexture(textureId, originalPixels, width, height);
        }

        INSTANCE.sessions.remove(textureId.toString());

        Minecraft.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    "Texture Reset", "Texture reset to default.")
        );
    }

    /**
     * Upload entity texture using 1.21.11 GpuTexture API.
     */
    private void uploadEntityTexture(Identifier textureId, int[][] pixels, int w, int h) {
        Minecraft client = Minecraft.getInstance();
        try {
            NativeImage img = new NativeImage(w, h, false);
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++)
                    img.setColorArgb(x, y, pixels[x][y]);

            var existing = client.getTextureManager().getTexture(textureId);
            if (existing != null) {
                try {
                    var gpuTex = existing.getGlTexture();
                    if (gpuTex != null && gpuTex.getWidth(0) == w && gpuTex.getHeight(0) == h) {
                        com.mojang.blaze3d.systems.RenderSystem.getDevice()
                            .createCommandEncoder()
                            .writeToTexture(gpuTex, img);
                        img.close();
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("[TextureEditor] Direct entity upload failed: " + e.getMessage());
                }
            }

            try { if (existing != null) existing.close(); } catch (Exception ignored) {}
            var dynamicTex = new DynamicTexture(() -> "textureeditor_ext", img);
            client.getTextureManager().register(textureId, dynamicTex);
            dynamicTex.upload();
        } catch (Exception e) {
            System.out.println("[TextureEditor] Entity upload error: " + e.getMessage());
        }
    }

    public void stopSession(Identifier textureId) {
        ExternalEditorSession session = sessions.remove(textureId.toString());
        if (session != null) session.stop();
    }

    public void stopAll() {
        for (ExternalEditorSession session : sessions.values()) {
            session.stopAndCleanup();
        }
        sessions.clear();
    }

    public static void deleteCache() {
        File tempDir = ExternalEditorSession.getTempDir();
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        INSTANCE.stopAll();
        System.out.println("[TextureEditor] Temp cache cleared.");
    }

    public boolean hasSession(Identifier textureId) {
        ExternalEditorSession session = sessions.get(textureId.toString());
        return session != null && session.isActive();
    }

    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(ExternalEditorSession::isActive).count();
    }

    public java.util.Set<String> getActiveSessionKeys() {
        return sessions.keySet();
    }

    private void warnNoEditor() {
        System.out.println("[TextureEditor] No external editor configured!");
        Minecraft.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                    "No Editor", "Configure an external editor in settings first.")
        );
    }
}
