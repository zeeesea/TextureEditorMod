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

        if (isSpriteTexture && spriteAtlasId != null) {
            // Sprite atlas based GUI textures
            client.execute(() ->
                    TextureManager.getInstance().applyLive(spriteAtlasId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight()));
        } else {
            // Direct texture replacement (container UIs, etc.)
            client.execute(() -> {
                NativeImage img = new NativeImage(canvas.getWidth(), canvas.getHeight(), false);
                for (int x = 0; x < canvas.getWidth(); x++)
                    for (int y = 0; y < canvas.getHeight(); y++)
                        img.setColorArgb(x, y, canvas.getPixels()[x][y]);
                var dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(img);
                client.getTextureManager().registerTexture(fullTextureId, dynamicTex);
            });
        }
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
