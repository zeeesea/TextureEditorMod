package com.zeeesea.textureeditor.editor;

import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.util.Identifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton managing active external editor sessions.
 * Handles creating sessions, preventing duplicates, and cleanup.
 */
public class ExternalEditorManager {

    private static final ExternalEditorManager INSTANCE = new ExternalEditorManager();
    private final ConcurrentHashMap<String, ExternalEditorSession> sessions = new ConcurrentHashMap<>();

    private ExternalEditorManager() {}

    public static ExternalEditorManager getInstance() {
        return INSTANCE;
    }

    /**
     * Check if external editor mode is enabled and a valid editor path is available.
     */
    public boolean isExternalEditorEnabled() {
        ModSettings s = ModSettings.getInstance();
        if (!s.useExternalEditor) return false;
        String path = getEditorPath();
        if (path == null || path.isEmpty()) return false;
        // Accept both file paths and simple command names (e.g., "mspaint")
        return new File(path).exists() || !path.contains(File.separator);
    }

    /**
     * Get the resolved editor executable path.
     * Custom path takes priority over auto-detected.
     */
    public String getEditorPath() {
        ModSettings s = ModSettings.getInstance();

        // Custom path takes priority
        if (s.externalEditorCustomPath != null && !s.externalEditorCustomPath.isEmpty()) {
            if (new File(s.externalEditorCustomPath).exists()) {
                return s.externalEditorCustomPath;
            }
        }

        // Try selected editor name from detected list
        if (s.selectedEditorName != null && !s.selectedEditorName.isEmpty()) {
            for (ExternalEditorDetector.DetectedEditor editor : ExternalEditorDetector.detectEditors()) {
                if (editor.name().equals(s.selectedEditorName)) {
                    return editor.executablePath();
                }
            }
        }

        // Fallback: first detected editor
        ExternalEditorDetector.DetectedEditor def = ExternalEditorDetector.getDefault();
        return def != null ? def.executablePath() : null;
    }

    // ---------- session creation ----------

    /**
     * Start an external editor session for a block/item (sprite atlas based).
     * If a session already exists and is active, just brings focus (no new instance).
     */
    public void startAtlasSession(Identifier textureId, Identifier spriteId,
                                   int[][] pixels, int[][] originalPixels,
                                   int width, int height) {
        String key = textureId.toString();
        String editorPath = getEditorPath();
        if (editorPath == null) { warnNoEditor(); return; }

        // Use already-modified pixels if available
        int[][] currentPixels = TextureManager.getInstance().getPixels(textureId);
        if (currentPixels != null) pixels = currentPixels;

        ExternalEditorSession existing = sessions.get(key);
        if (existing != null && existing.isActive()) {
            // Session already active — don't launch a new instance, just sync the file
            syncTempFile(existing, pixels);
            return;
        }
        if (existing != null) existing.stop();

        ExternalEditorSession session = new ExternalEditorSession(
                textureId, spriteId, pixels, originalPixels, width, height,
                ExternalEditorSession.TextureType.BLOCK_ATLAS, editorPath,
                true, null // no guiSpriteId for atlas textures
        );
        sessions.put(key, session);
    }

    /**
     * Start an external editor session for an entity/mob (direct texture upload).
     */
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

    /**
     * Start an external editor session for a GUI sprite texture.
     * GUI sprites are stored in sprite atlases, not as standalone textures.
     *
     * @param textureId    Full texture path (e.g. minecraft:textures/gui/sprites/hud/hotbar.png)
     * @param guiSpriteId  Original sprite ID (e.g. minecraft:gui/sprites/hud/hotbar)
     */
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

    // ---------- sync ----------

    /**
     * If the internal editor made changes, update the temp file so external
     * editor picks them up on next open/reload.
     */
    private void syncTempFile(ExternalEditorSession session, int[][] pixels) {
        // Don't overwrite — the user may have unsaved work in the external editor.
        // Just show a toast that it's already open.
        MinecraftClient.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.Type.PERIODIC_NOTIFICATION,
                    "Already Open", "Texture is already open in external editor.")
        );
    }

    /**
     * Sync ALL temp files with current TextureManager state.
     * Called when switching from internal to external editor, or after a reset.
     */
    public void syncAllTempFiles() {
        for (var entry : sessions.entrySet()) {
            ExternalEditorSession session = entry.getValue();
            Identifier texId = session.getTextureId();
            int[][] currentPixels = TextureManager.getInstance().getPixels(texId);
            if (currentPixels != null) {
                session.forceExportPixels(currentPixels);
            } else {
                // Texture was reset — write original pixels
                int[][] orig = session.getOriginalPixels();
                if (orig != null) {
                    session.forceExportPixels(orig);
                }
            }
        }
    }

    // ---------- reset ----------

    /**
     * Reset a single texture to its original state.
     * Overwrites the temp file and reloads the texture in-game.
     */
    public void resetTexture(Identifier textureId) {
        String key = textureId.toString();
        ExternalEditorSession session = sessions.get(key);

        // Get original pixels either from session or from TextureManager
        int[][] origPixels = null;
        if (session != null) {
            origPixels = session.getOriginalPixels();
        }

        // Remove from TextureManager
        TextureManager.getInstance().removeTexture(textureId);

        if (origPixels != null) {
            // Overwrite the temp file with original pixels
            if (session != null) session.forceExportPixels(origPixels);

            // Re-apply the original texture live
            if (session != null && session.getTextureType() == ExternalEditorSession.TextureType.BLOCK_ATLAS
                    && session.getSpriteId() != null) {
                TextureManager.getInstance().applyLive(session.getSpriteId(), origPixels,
                        session.getWidth(), session.getHeight());
            } else if (session != null) {
                uploadEntityTexture(textureId, origPixels, session.getWidth(), session.getHeight());
            }
        }

        MinecraftClient.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.Type.PERIODIC_NOTIFICATION,
                    "Texture Reset", "Texture reset to default.")
        );
    }

    /**
     * Reset a texture by its textureId (for use from browser — works even without a session).
     */
    public static void resetTextureStatic(Identifier textureId, Identifier spriteId,
                                           int[][] originalPixels, int width, int height) {
        TextureManager tm = TextureManager.getInstance();
        tm.removeTexture(textureId);

        // Delete temp file if it exists
        String safeName = textureId.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
        Path tempFile = new File(ExternalEditorSession.getTempDir(), safeName).toPath();
        try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}

        // Re-apply original live
        if (spriteId != null) {
            tm.applyLive(spriteId, originalPixels, width, height);
        } else {
            INSTANCE.uploadEntityTexture(textureId, originalPixels, width, height);
        }

        // Remove session if exists
        INSTANCE.sessions.remove(textureId.toString());

        MinecraftClient.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.Type.PERIODIC_NOTIFICATION,
                    "Texture Reset", "Texture reset to default.")
        );
    }

    private void uploadEntityTexture(Identifier textureId, int[][] pixels, int w, int h) {
        MinecraftClient client = MinecraftClient.getInstance();
        try (var img = new net.minecraft.client.texture.NativeImage(w, h, false)) {
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++)
                    img.setColorArgb(x, y, pixels[x][y]);
            var tex = client.getTextureManager().getTexture(textureId);
            if (tex != null) {
                // If the existing texture is a NativeImageBackedTexture we can call upload()
                if (tex instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt) {
                    nibt.setImage(img);
                    nibt.upload();
                } else {
                    // Fallback: bind the texture by GL id and sub-upload the native image
                    com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
                    // AbstractTexture.bindTexture() was removed/changed; use GlStateManager to bind by GL id
                    com.mojang.blaze3d.opengl.GlStateManager._bindTexture(com.zeeesea.textureeditor.util.TextureCompat.getGlId(tex));
                    com.zeeesea.textureeditor.util.NativeImageCompat.upload(img, 0, 0, 0, false);
                }
            }
        }
    }

    // ---------- cleanup ----------

    /**
     * Stop a specific session.
     */
    public void stopSession(Identifier textureId) {
        ExternalEditorSession session = sessions.remove(textureId.toString());
        if (session != null) session.stop();
    }

    /**
     * Stop all active sessions and clean up temp files.
     */
    public void stopAll() {
        for (ExternalEditorSession session : sessions.values()) {
            session.stopAndCleanup();
        }
        sessions.clear();
    }

    /**
     * Delete all temp files in textureeditor_temp/ folder.
     */
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

    // ---------- queries ----------

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
        MinecraftClient.getInstance().execute(() ->
            NotificationHelper.addToast(SystemToast.Type.PACK_LOAD_FAILURE,
                    "No Editor", "Configure an external editor in settings first.")
        );
    }
}
