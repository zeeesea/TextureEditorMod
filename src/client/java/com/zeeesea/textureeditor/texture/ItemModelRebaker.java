package com.zeeesea.textureeditor.texture;

import net.minecraft.resources.Identifier;

/**
 * Rebakes item models after texture edits to update the 3D thickness quads.
 *
 * When a player edits an item texture (e.g. adds pixels outside the original shape),
 * Minecraft's item model still has the old baked quads that only include thickness
 * for the original pixel shape. This class regenerates those quads from the updated
 * sprite data so newly-drawn pixels also get proper 3D thickness.
 */
public class ItemModelRebaker {

    /**
     * Rebake all item models that reference the given sprite.
     * Must be called on the render thread after the sprite's NativeImage has been updated.
     *
     * @param spriteId The sprite identifier (e.g. minecraft:item/diamond_sword)
     */
    public static void rebake(Identifier spriteId) {
        // TODO 26.1+: re-enable after full item model pipeline migration.
    }

    /**
     * Build a reverse index: sprite ID -> list of item model IDs that use it.
     */
    public static void invalidateCache() {
    }
}
