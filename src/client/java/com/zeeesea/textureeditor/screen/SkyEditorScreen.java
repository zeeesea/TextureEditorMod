package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Sky texture editor for sun, moon, and end sky textures.
 */
public class SkyEditorScreen extends AbstractEditorScreen {

    private enum SkyTexture {
        SUN("Sun", "environment/sun", 32, 32),
        MOON("Moon Phases", "environment/moon_phases", 128, 64),
        END_SKY("End Sky", "environment/end_sky", 128, 128);

        final String displayName;
        final String path;
        final int defaultWidth, defaultHeight;

        SkyTexture(String dn, String p, int dw, int dh) { displayName = dn; path = p; defaultWidth = dw; defaultHeight = dh; }

        Identifier getTextureId() { return Identifier.of("minecraft", "textures/" + path + ".png"); }
    }

    private final Screen parent;
    private SkyTexture currentSkyTexture = SkyTexture.SUN;

    public SkyEditorScreen(Screen parent) {
        super(Text.literal("Sky Editor"));
        this.parent = parent;
        this.zoom = 4;
    }

    @Override protected int getBackgroundColor() { return 0xFF0A0A1E; }
    @Override protected int getMaxZoom() { return 20; }
    @Override protected int getMinZoom() { return 1; }
    @Override protected int getZoomStep() { return 1; }
    @Override protected Screen getBackScreen() { return parent; }
    @Override protected String getEditorTitle() { return "Sky Editor - " + currentSkyTexture.displayName; }
    @Override protected String getResetCurrentLabel() { return "Reset"; }

    @Override
    protected void loadTexture() {
        textureId = currentSkyTexture.getTextureId();
        MinecraftClient client = MinecraftClient.getInstance();
        int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
        int[] savedDims = TextureManager.getInstance().getDimensions(textureId);

        try {
            var optResource = client.getResourceManager().getResource(textureId);
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
            System.out.println("[TextureEditor] Failed to load sky texture: " + textureId + " - " + e.getMessage());
        }
        if (canvas == null) {
            canvas = new PixelCanvas(currentSkyTexture.defaultWidth, currentSkyTexture.defaultHeight);
            originalPixels = new int[currentSkyTexture.defaultWidth][currentSkyTexture.defaultHeight];
        }
        // Repositioning editor title
        editorTitleXPos = 5;
    }

    @Override
    protected int addExtraButtons(int toolY) {
        // Sky texture tabs at top
        int skyBtnX = this.width / 2 - 120;
        addDrawableChild(ButtonWidget.builder(Text.literal("Sun"), btn -> switchTexture(SkyTexture.SUN))
                .position(skyBtnX, 5).size(70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Moon"), btn -> switchTexture(SkyTexture.MOON))
                .position(skyBtnX + 74, 5).size(70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("End Sky"), btn -> switchTexture(SkyTexture.END_SKY))
                .position(skyBtnX + 148, 5).size(70, 20).build());
        return toolY;
    }

    @Override
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {
        // Active tab indicator
        int skyBtnX = this.width / 2 - 120;
        int tabIdx = currentSkyTexture.ordinal();
        int indicatorX = skyBtnX + tabIdx * 74;
        context.fill(indicatorX, 25, indicatorX + 70, 27, 0xFFFFFF00);
    }

    private void switchTexture(SkyTexture tex) {
        applyLive();
        currentSkyTexture = tex;
        panOffsetX = 0; panOffsetY = 0;
        canvas = null;
        this.clearChildren();
        this.init();
    }

    @Override
    protected void applyLive() {
        if (textureId == null || canvas == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager.getInstance().putTexture(textureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
        client.execute(() -> {
            NativeImage img = new NativeImage(canvas.getWidth(), canvas.getHeight(), false);
            for (int x = 0; x < canvas.getWidth(); x++)
                for (int y = 0; y < canvas.getHeight(); y++)
                    img.setColorArgb(x, y, canvas.getPixels()[x][y]);
            var dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(img);
            client.getTextureManager().registerTexture(textureId, dynamicTex);
        });
    }

    @Override
    protected void resetCurrent() {
        if (originalPixels == null) return;
        canvas.saveSnapshot();
        for (int x = 0; x < canvas.getWidth(); x++)
            for (int y = 0; y < canvas.getHeight(); y++)
                canvas.setPixel(x, y, originalPixels[x][y]);
        TextureManager.getInstance().removeTexture(textureId);
        applyLive();
    }
}
