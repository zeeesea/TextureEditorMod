package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;

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

    public GuiTextureEditorScreen(Identifier guiTextureId, String displayName, Screen parent) {
        super(Text.literal("GUI Texture Editor"));
        this.guiTextureId = guiTextureId;
        this.displayName = displayName;
        this.parent = parent;
        this.zoom = 4;
    }

    @Override
    protected void loadTexture() {
        fullTextureId = Identifier.of(guiTextureId.getNamespace(), "textures/" + guiTextureId.getPath() + ".png");
        isSpriteTexture = guiTextureId.getPath().startsWith("gui/sprites/");
        if (isSpriteTexture) {
            spriteAtlasId = Identifier.of(guiTextureId.getNamespace(),
                    guiTextureId.getPath().replace("gui/sprites/", ""));
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int[][] savedPixels = TextureManager.getInstance().getPixels(fullTextureId);
        int[] savedDims = TextureManager.getInstance().getDimensions(fullTextureId);

        try {
            var optResource = client.getResourceManager().getResource(fullTextureId);
            if (optResource.isPresent()) {
                InputStream stream = optResource.get().getInputStream();
                NativeImage image = NativeImage.read(stream);
                int w = image.getWidth(), h = image.getHeight();
                originalPixels = new int[w][h];
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        originalPixels[x][y] = image.getColorArgb(x, y);
                image.close();
                stream.close();

                if (savedPixels != null && savedDims != null && savedDims[0] == w && savedDims[1] == h) {
                    canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
                } else {
                    canvas = new PixelCanvas(w, h, originalPixels);
                }
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load GUI texture: " + fullTextureId + " - " + e.getMessage());
        }
    }

    @Override
    protected String getEditorTitle() { return "GUI Editor - " + displayName; }

    @Override
    protected String getResetCurrentLabel() { return "Reset"; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override protected int getMaxZoom() { return 20; }
    @Override protected int getMinZoom() { return 1; }
    @Override protected int getZoomStep() { return 1; }

    @Override
    protected void applyLive() {
        if (fullTextureId == null || canvas == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager.getInstance().putTexture(fullTextureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());

        client.execute(() -> {
            NativeImage img = new NativeImage(canvas.getWidth(), canvas.getHeight(), false);
            for (int x = 0; x < canvas.getWidth(); x++) {
                for (int y = 0; y < canvas.getHeight(); y++) {
                    img.setColorArgb(x, y, canvas.getPixels()[x][y]);
                }
            }

            Identifier spriteId = guiTextureId;
            var atlasAndSprite = findSpriteInAtlases(client, spriteId);

            if (atlasAndSprite != null) {
                net.minecraft.client.texture.SpriteAtlasTexture atlas = atlasAndSprite.getLeft();
                net.minecraft.client.texture.Sprite sprite = atlasAndSprite.getRight();
                System.out.println("[TextureEditor] Updating sprite in atlas: " + spriteId);
                atlas.bindTexture();
                img.upload(0, sprite.getX(), sprite.getY(), false);
            } else {
                net.minecraft.client.texture.NativeImageBackedTexture dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(img);
                client.getTextureManager().registerTexture(fullTextureId, dynamicTex);
                dynamicTex.bindTexture();
                img.upload(0, 0, 0, false);
                System.out.println("[TextureEditor] Applied live GUI texture: " + fullTextureId);
            }
        });
    }

    // Utility: Find sprite in block/gui atlases
    private org.apache.commons.lang3.tuple.Pair<net.minecraft.client.texture.SpriteAtlasTexture, net.minecraft.client.texture.Sprite> findSpriteInAtlases(MinecraftClient client, Identifier id) {
        var blockAtlas = client.getBakedModelManager().getAtlas(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        var sprite = blockAtlas.getSprite(id);
        if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
            if (sprite.getContents().getId().equals(id)) {
                return org.apache.commons.lang3.tuple.Pair.of(blockAtlas, sprite);
            }
        }
        Identifier guiAtlasId = Identifier.of("textures/atlas/gui.png");
        var tex = client.getTextureManager().getTexture(guiAtlasId);
        if (tex instanceof net.minecraft.client.texture.SpriteAtlasTexture guiAtlas) {
            sprite = guiAtlas.getSprite(id);
            if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                if (sprite.getContents().getId().equals(id)) {
                    return org.apache.commons.lang3.tuple.Pair.of(guiAtlas, sprite);
                }
            }
            if (id.getPath().startsWith("gui/sprites/")) {
                String shortPath = id.getPath().substring("gui/sprites/".length());
                Identifier shortId = Identifier.of(id.getNamespace(), shortPath);
                sprite = guiAtlas.getSprite(shortId);
                if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                    return org.apache.commons.lang3.tuple.Pair.of(guiAtlas, sprite);
                }
            }
        }
        return null;
    }

    @Override
    protected void resetCurrent() {
        if (originalPixels == null) return;
        canvas.saveSnapshot();
        for (int x = 0; x < canvas.getWidth(); x++)
            for (int y = 0; y < canvas.getHeight(); y++)
                canvas.setPixel(x, y, originalPixels[x][y]);
        TextureManager.getInstance().removeTexture(fullTextureId);
        applyLive();
    }
}
