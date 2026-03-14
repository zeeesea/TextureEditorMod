package com.zeeesea.textureeditor.texture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

/**
 * 1.21-compatible fallback rebaker.
 *
 * We trigger a throttled resource reload to rebuild item model geometry (thickness), then
 * reapply all in-memory modified textures so edits stay visible after the reload.
 */
public final class ItemModelRebaker {
    private static final long RELOAD_COOLDOWN_MS = 400L;
    private static long lastReloadMs = 0L;
    private static boolean reloadInProgress = false;
    private static boolean reloadQueued = false;

    private ItemModelRebaker() {
    }

    public static void rebake(Identifier spriteId) {
        if (spriteId == null || !spriteId.getPath().startsWith("item/")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        boolean startReload;
        synchronized (ItemModelRebaker.class) {
            long now = System.currentTimeMillis();
            if (reloadInProgress) {
                reloadQueued = true;
                return;
            }
            if (now - lastReloadMs < RELOAD_COOLDOWN_MS) {
                reloadQueued = true;
                return;
            }
            reloadInProgress = true;
            lastReloadMs = now;
            startReload = true;
        }

        if (!startReload) return;

        CompletableFuture<Void> future = client.reloadResources();
        future.whenComplete((ignored, throwable) -> {
            boolean shouldReloadAgain;
            synchronized (ItemModelRebaker.class) {
                reloadInProgress = false;
                shouldReloadAgain = reloadQueued;
                reloadQueued = false;
                lastReloadMs = System.currentTimeMillis();
            }

            if (throwable != null) {
                System.out.println("[TextureEditor] ItemModelRebaker: resource reload failed: " + throwable.getMessage());
                return;
            }

            // Restore all live-edited textures after reload so nothing appears to vanish.
            client.execute(() -> TextureManager.getInstance().reapplyAllModifiedTextures());

            if (shouldReloadAgain) {
                rebake(spriteId);
            }
        });
    }

    public static boolean shouldSuppressLiveRebake(Identifier spriteId) {
        if (spriteId == null || !spriteId.getPath().startsWith("item/")) return false;
        synchronized (ItemModelRebaker.class) {
            if (reloadInProgress) {
                reloadQueued = true;
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastReloadMs < RELOAD_COOLDOWN_MS) {
                reloadQueued = true;
                return true;
            }
        }
        return false;
    }

    public static void invalidateCache() {
        synchronized (ItemModelRebaker.class) {
            reloadQueued = false;
        }
    }
}
