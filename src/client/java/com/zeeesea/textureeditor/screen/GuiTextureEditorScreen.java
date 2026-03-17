package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.EntityTextureSyncPayload;
import com.zeeesea.textureeditor.TextureSyncPayload;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GUI/HUD texture editor. Loads textures directly from resource manager.
 */
public class GuiTextureEditorScreen extends AbstractEditorScreen {

    private final Identifier guiTextureId;
    private final String displayName;
    private final Screen parent;
    private Identifier fullTextureId;

    // Detect if it's a sprite-atlas based texture (gui/sprites/*)
    private boolean isSpriteTexture = false;
    private Identifier spriteAtlasId;

    // Known alternative paths for textures that moved in newer versions
    private static final java.util.Map<String, String[]> ALTERNATIVE_PATHS = java.util.Map.of(
            "textures/entity/elytra.png", new String[]{
                    "textures/entity/equipment/wings/elytra.png",
                    "textures/entity/elytra/elytra.png",
                    "textures/entity/equipment/elytra.png"
            }
    );

    public GuiTextureEditorScreen(Identifier guiTextureId, String displayName, Screen parent) {
        super(Text.translatable("textureeditor.screen.gui.title"));
        this.guiTextureId = guiTextureId;
        this.displayName = displayName;
        this.parent = parent;
        this.zoom = 4;
    }

    @Override
    protected void loadTexture() {
        System.out.println("[TextureEditor] Loading GUI texture: " + guiTextureId);
        if (guiTextureId.getPath().startsWith("textures/")) {
            fullTextureId = guiTextureId;
        } else {
            fullTextureId = Identifier.of(guiTextureId.getNamespace(), "textures/" + guiTextureId.getPath() + ".png");
        }

        isSpriteTexture = guiTextureId.getPath().startsWith("gui/sprites/");
        if (isSpriteTexture) {
            spriteAtlasId = Identifier.of(guiTextureId.getNamespace(),
                    guiTextureId.getPath().replace("gui/sprites/", ""));
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int[][] savedPixels = TextureManager.getInstance().getPixels(fullTextureId);
        int[] savedDims = TextureManager.getInstance().getDimensions(fullTextureId);

        // Build candidate list: requested ID + known alternatives + armor path aliases
        Set<Identifier> candidateSet = new LinkedHashSet<>();
        candidateSet.add(fullTextureId);
        String[] alts = ALTERNATIVE_PATHS.get(fullTextureId.getPath());
        if (alts != null) {
            for (String alt : alts) {
                candidateSet.add(Identifier.of(fullTextureId.getNamespace(), alt));
            }
        }
        addArmorAliasCandidates(candidateSet, fullTextureId);

        // Also try a generic scan for entity textures that might have moved
        if (fullTextureId.getPath().startsWith("textures/entity/") && fullTextureId.getPath().endsWith(".png")) {
            String filename = fullTextureId.getPath().replace("textures/entity/", "").replace(".png", "");
            // Try textures/entity/<name>/<name>.png
            if (!filename.contains("/")) {
                candidateSet.add(Identifier.of(fullTextureId.getNamespace(), "textures/entity/" + filename + "/" + filename + ".png"));
                // Try textures/entity/equipment/wings/<name>.png (1.21.4 pattern)
                candidateSet.add(Identifier.of(fullTextureId.getNamespace(), "textures/entity/equipment/wings/" + filename + ".png"));
                candidateSet.add(Identifier.of(fullTextureId.getNamespace(), "textures/entity/equipment/" + filename + ".png"));
            }
        }

        List<Identifier> candidates = new ArrayList<>(candidateSet);

        try {
            for (Identifier candidateId : candidates) {
                System.out.println("[TextureEditor] Trying texture path: " + candidateId);
                var optResource = client.getResourceManager().getResource(candidateId);
                if (optResource.isPresent()) {
                    System.out.println("[TextureEditor] Resource FOUND: " + candidateId);
                    fullTextureId = candidateId; // Update to the found path
                    System.out.println("[TextureEditor] Resolved texture: requested=" + guiTextureId + " -> resolved=" + fullTextureId);
                    InputStream stream = optResource.get().getInputStream();
                    NativeImage image = NativeImage.read(stream);
                    int w = image.getWidth(), h = image.getHeight();
                    System.out.println("[TextureEditor] Image loaded: " + w + "x" + h);
                    originalPixels = new int[w][h];
                    for (int x = 0; x < w; x++)
                        for (int y = 0; y < h; y++)
                            originalPixels[x][y] = image.getColorArgb(x, y);
                    image.close();
                    stream.close();

                    // Check for saved modified pixels using the found ID
                    savedPixels = TextureManager.getInstance().getPixels(fullTextureId);
                    savedDims = TextureManager.getInstance().getDimensions(fullTextureId);

                    if (savedPixels != null && savedDims != null && savedDims[0] == w && savedDims[1] == h) {
                        canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
                        System.out.println("[TextureEditor] Loaded saved pixels for " + fullTextureId + " (" + savedDims[0] + "x" + savedDims[1] + ")");
                    } else {
                        canvas = new PixelCanvas(w, h, originalPixels);
                        System.out.println("[TextureEditor] Created fresh canvas for " + fullTextureId + " (" + w + "x" + h + ")");
                    }
                    return; // Success!
                }
            }
            System.out.println("[TextureEditor] FAILED to find texture after trying all candidates for: " + guiTextureId);
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load GUI texture: " + fullTextureId + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addArmorAliasCandidates(Set<Identifier> candidates, Identifier baseId) {
        String path = baseId.getPath();
        if (!path.startsWith("textures/models/armor/") || !path.endsWith(".png")) return;

        String name = path.substring("textures/models/armor/".length(), path.length() - 4);
        String suffix = "";
        if (name.endsWith("_overlay")) {
            suffix = "_overlay";
            name = name.substring(0, name.length() - "_overlay".length());
        }

        if (name.endsWith("_layer_1")) {
            String rawMaterial = name.substring(0, name.length() - "_layer_1".length());
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/humanoid/", rawMaterial, suffix);
            return;
        }
        if (name.endsWith("_layer_2")) {
            String rawMaterial = name.substring(0, name.length() - "_layer_2".length());
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/humanoid_leggings/", rawMaterial, suffix);
            return;
        }

        if (name.contains("_piglin_helmet")) {
            String rawMaterial = name.replace("_piglin_helmet", "");
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/piglin_head/", rawMaterial, suffix);
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/humanoid/", rawMaterial, suffix);
        }
    }

    private void addEquipmentCandidate(Set<Identifier> candidates, Identifier baseId, String folderPath, String rawMaterial, String suffix) {
        String normalizedMaterial = normalizeArmorMaterial(rawMaterial);
        candidates.add(Identifier.of(baseId.getNamespace(), folderPath + rawMaterial + suffix + ".png"));
        if (!normalizedMaterial.equals(rawMaterial)) {
            candidates.add(Identifier.of(baseId.getNamespace(), folderPath + normalizedMaterial + suffix + ".png"));
        }
    }

    private String normalizeArmorMaterial(String material) {
        String normalized = material;
        if (normalized.startsWith("piglin_")) {
            normalized = normalized.substring("piglin_".length());
        }
        if ("turtle".equals(normalized)) {
            normalized = "turtle_scute";
        }
        return normalized;
    }

    @Override
    protected String getEditorTitle() { return Text.translatable("textureeditor.screen.gui.editor_title", displayName).getString(); }

    @Override
    protected String getResetCurrentLabel() { return Text.translatable("textureeditor.button.reset").getString(); }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override protected int getMaxZoom() { return 20; }
    @Override protected int getMinZoom() { return 1; }
    @Override protected int getZoomStep() { return 1; }

    @Override
    protected int addExtraButtons(int toolY) {
        // If this is the elytra entity texture, add a button to switch to item view
        if (guiTextureId.getPath().contains("elytra")) {
            int rsw = getRightSidebarWidth();
            int resetBtnW = rsw - 10;
            int tbh = getToolButtonHeight();
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.edit_item"), btn -> {
                MinecraftClient.getInstance().setScreen(new ItemEditorScreen(new ItemStack(Items.ELYTRA), parent));
            }).position(this.width - rsw + 5, this.height - 124).size(resetBtnW, tbh).build());
        }
        return toolY;
    }

    @Override
    protected void applyLive() {
        if (fullTextureId == null || canvas == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager.getInstance().putTexture(fullTextureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());

        var atlasAndSprite = findSpriteInAtlases(client, guiTextureId);
        final Identifier foundSpriteId = atlasAndSprite != null ?
                atlasAndSprite.getRight().getContents().getId() : null;

        client.execute(() -> {
            try {
                System.out.println("[TextureEditor] GUI applyLive: fullTextureId=" + fullTextureId + ", guiTextureId=" + guiTextureId + ", isSpriteTexture=" + isSpriteTexture);

                if (atlasAndSprite != null) {
                    // Use the proper sprite ID that was found in the atlas
                    net.minecraft.client.texture.Sprite sprite = atlasAndSprite.getRight();
                    System.out.println("[TextureEditor] Updating sprite in atlas: " + foundSpriteId + " (atlas: " + atlasAndSprite.getLeft() + ")");
                    // Use TextureManager's proper RenderPass blit to write at correct atlas position
                    TextureManager.getInstance().applyLive(foundSpriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
                } else {
                    System.out.println("[TextureEditor] No atlas sprite found, using dynamic texture for: " + fullTextureId);
                    // Non-atlas texture: use NativeImageBackedTexture (for container textures etc.)
                    NativeImage img = new NativeImage(canvas.getWidth(), canvas.getHeight(), false);
                    for (int x = 0; x < canvas.getWidth(); x++)
                        for (int y = 0; y < canvas.getHeight(); y++)
                            img.setColorArgb(x, y, canvas.getPixels()[x][y]);
                    var existing = client.getTextureManager().getTexture(fullTextureId);
                    System.out.println("[TextureEditor] Existing texture type: " + (existing != null ? existing.getClass().getSimpleName() : "null"));
                    if (existing instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt) {
                        nibt.setImage(img);
                        nibt.upload();
                        System.out.println("[TextureEditor] Updated existing NativeImageBackedTexture for: " + fullTextureId);
                    } else {
                        var dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "textureeditor_gui", img);
                        client.getTextureManager().registerTexture(fullTextureId, dynamicTex);
                        dynamicTex.upload();
                        System.out.println("[TextureEditor] Registered new NativeImageBackedTexture for: " + fullTextureId);
                    }
                }
            } catch (Throwable t) {
                System.out.println("[TextureEditor] ERROR in GUI applyLive: " + t.getClass().getName() + ": " + t.getMessage());
                t.printStackTrace();
            }
        });

        final int[][] px = canvas.getPixels();
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final Identifier sid = fullTextureId;

        // Send to other players if multiplayer sync enabled
        if (ModSettings.getInstance().multiplayerSync) {
            int[] flat = new int[w * h];
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++)
                    flat[y * w + x] = px[x][y];

            int[] origFlat = new int[w * h];
            if (originalPixels != null) {
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        origFlat[y * w + x] = originalPixels[x][y];
            }
            ClientPlayNetworking.send(new EntityTextureSyncPayload(fullTextureId, foundSpriteId, w, h, flat, origFlat));
        }
    }

    // Utility: Find sprite in block/gui/items atlases
    private org.apache.commons.lang3.tuple.Pair<net.minecraft.client.texture.SpriteAtlasTexture, net.minecraft.client.texture.Sprite> findSpriteInAtlases(MinecraftClient client, Identifier id) {
        // Check block atlas
        try {
            var blockAtlas = (net.minecraft.client.texture.SpriteAtlasTexture) client.getTextureManager().getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            var sprite = blockAtlas.getSprite(id);
            if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                return org.apache.commons.lang3.tuple.Pair.of(blockAtlas, sprite);
            }
        } catch (Exception ignored) {}

        // Check items atlas
        try {
            var tex = client.getTextureManager().getTexture(net.minecraft.client.texture.SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE);
            if (tex instanceof net.minecraft.client.texture.SpriteAtlasTexture itemsAtlas) {
                var sprite = itemsAtlas.getSprite(id);
                if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                    return org.apache.commons.lang3.tuple.Pair.of(itemsAtlas, sprite);
                }
            }
        } catch (Exception ignored) {}

        // Check GUI atlas (used for HUD sprites)
        Identifier guiAtlasId = Identifier.ofVanilla("textures/atlas/gui.png");
        try {
            var tex = client.getTextureManager().getTexture(guiAtlasId);
            if (tex instanceof net.minecraft.client.texture.SpriteAtlasTexture guiAtlas) {
                // Try full path first
                var sprite = guiAtlas.getSprite(id);
                if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                    return org.apache.commons.lang3.tuple.Pair.of(guiAtlas, sprite);
                }
                // Try without gui/sprites/ prefix (HUD sprites use short IDs like hud/hotbar)
                if (id.getPath().startsWith("gui/sprites/")) {
                    String shortPath = id.getPath().substring("gui/sprites/".length());
                    Identifier shortId = Identifier.of(id.getNamespace(), shortPath);
                    sprite = guiAtlas.getSprite(shortId);
                    if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                        return org.apache.commons.lang3.tuple.Pair.of(guiAtlas, sprite);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    protected void resetCurrent() {
        if (canvas == null) return;
        if (originalPixels == null) return;

        // Reset canvas: delete all layers, fresh base layer
        canvas.saveSnapshot();
        canvas.setLayerStack(new com.zeeesea.textureeditor.editor.LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();

        if (fullTextureId != null) {
            TextureManager.getInstance().removeTexture(fullTextureId);
        }
        applyLive();
    }
}