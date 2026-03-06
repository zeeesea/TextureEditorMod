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
        // In 1.21.11, sun/moon are sprites in the celestials atlas (textures/atlas/celestials.png)
        // Sprite IDs are relative to the atlas source directory "environment/celestial"
        SUN("Sun", new String[]{"environment/celestial/sun", "environment/sun"}, "sun", 32, 32),
        MOON_FULL("Moon Full", new String[]{"environment/celestial/moon/full_moon", "environment/moon_phases"}, "moon/full_moon", 32, 32),
        MOON_WANING_G("Moon Wan.Gib", new String[]{"environment/celestial/moon/waning_gibbous"}, "moon/waning_gibbous", 32, 32),
        MOON_THIRD_Q("Moon 3rd Qtr", new String[]{"environment/celestial/moon/third_quarter"}, "moon/third_quarter", 32, 32),
        MOON_WANING_C("Moon Wan.Cres", new String[]{"environment/celestial/moon/waning_crescent"}, "moon/waning_crescent", 32, 32),
        MOON_NEW("Moon New", new String[]{"environment/celestial/moon/new_moon"}, "moon/new_moon", 32, 32),
        MOON_WAXING_C("Moon Wax.Cres", new String[]{"environment/celestial/moon/waxing_crescent"}, "moon/waxing_crescent", 32, 32),
        MOON_FIRST_Q("Moon 1st Qtr", new String[]{"environment/celestial/moon/first_quarter"}, "moon/first_quarter", 32, 32),
        MOON_WAXING_G("Moon Wax.Gib", new String[]{"environment/celestial/moon/waxing_gibbous"}, "moon/waxing_gibbous", 32, 32),
        END_SKY("End Sky", new String[]{"environment/end_sky"}, null, 128, 128);

        final String displayName;
        final String[] paths;
        final String spriteId; // sprite ID in celestials atlas (null = standalone texture)
        final int defaultWidth, defaultHeight;

        SkyTexture(String dn, String[] p, String sid, int dw, int dh) { displayName = dn; paths = p; spriteId = sid; defaultWidth = dw; defaultHeight = dh; }

        Identifier getTextureId() { return Identifier.of("minecraft", "textures/" + paths[0] + ".png"); }

        /** Get the sprite Identifier for celestials atlas lookup (null for standalone textures) */
        Identifier getSpriteId() { return spriteId != null ? Identifier.ofVanilla(spriteId) : null; }

        /** Get all candidate texture IDs to try */
        java.util.List<Identifier> getCandidateIds() {
            java.util.List<Identifier> ids = new java.util.ArrayList<>();
            for (String p : paths) {
                ids.add(Identifier.of("minecraft", "textures/" + p + ".png"));
            }
            return ids;
        }
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
        System.out.println("[TextureEditor] Loading sky texture: " + textureId);
        MinecraftClient client = MinecraftClient.getInstance();
        int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
        int[] savedDims = TextureManager.getInstance().getDimensions(textureId);

        try {
            // Try all candidate paths
            for (Identifier candidateId : currentSkyTexture.getCandidateIds()) {
                System.out.println("[TextureEditor] Trying sky path: " + candidateId);
                var optResource = client.getResourceManager().getResource(candidateId);
                if (optResource.isPresent()) {
                    System.out.println("[TextureEditor] Sky resource FOUND: " + candidateId);
                    textureId = candidateId;
                    InputStream stream = optResource.get().getInputStream();
                    NativeImage image = NativeImage.read(stream);
                    int w = image.getWidth(), h = image.getHeight();
                    System.out.println("[TextureEditor] Sky image size: " + w + "x" + h);
                    originalPixels = new int[w][h];
                    for (int x = 0; x < w; x++)
                        for (int y = 0; y < h; y++)
                            originalPixels[x][y] = image.getColorArgb(x, y);
                    image.close();
                    stream.close();

                    // Check saved modified pixels
                    savedPixels = TextureManager.getInstance().getPixels(textureId);
                    savedDims = TextureManager.getInstance().getDimensions(textureId);
                    if (savedPixels != null && savedDims != null && savedDims[0] == w && savedDims[1] == h) {
                        canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
                    } else {
                        canvas = new PixelCanvas(w, h, originalPixels);
                    }
                    return;
                }
            }
            System.out.println("[TextureEditor] FAILED to find sky texture after trying all paths");
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load sky texture: " + textureId + " - " + e.getMessage());
        }
        if (canvas == null) {
            canvas = new PixelCanvas(currentSkyTexture.defaultWidth, currentSkyTexture.defaultHeight);
            originalPixels = new int[currentSkyTexture.defaultWidth][currentSkyTexture.defaultHeight];
        }
    }

    @Override
    protected int addExtraButtons(int toolY) {
        // Sky texture cycle button (many moon phases now)
        SkyTexture[] textures = SkyTexture.values();
        int skyBtnX = this.width / 2 - 120;
        addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), btn -> {
            int idx = (currentSkyTexture.ordinal() - 1 + textures.length) % textures.length;
            switchTexture(textures[idx]);
        }).position(skyBtnX, 5).size(60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(currentSkyTexture.displayName), btn -> {
            int idx = (currentSkyTexture.ordinal() + 1) % textures.length;
            switchTexture(textures[idx]);
        }).position(skyBtnX + 64, 5).size(110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), btn -> {
            int idx = (currentSkyTexture.ordinal() + 1) % textures.length;
            switchTexture(textures[idx]);
        }).position(skyBtnX + 178, 5).size(60, 20).build());
        return toolY;
    }

    @Override
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {
        // Active texture indicator
        int skyBtnX = this.width / 2 - 120;
        context.fill(skyBtnX + 64, 25, skyBtnX + 174, 27, 0xFFFFFF00);
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

        Identifier spriteId = currentSkyTexture.getSpriteId();
        if (spriteId != null) {
            // Sun/moon: sprite in celestials atlas — use the atlas blit approach
            client.execute(() -> {
                try {
                    // Find the sprite in the celestials atlas
                    Identifier celestialsAtlasId = Identifier.ofVanilla("textures/atlas/celestials.png");
                    var tex = client.getTextureManager().getTexture(celestialsAtlasId);
                    if (tex instanceof net.minecraft.client.texture.SpriteAtlasTexture celestialsAtlas) {
                        var sprite = celestialsAtlas.getSprite(spriteId);
                        if (sprite != null && !sprite.getContents().getId().getPath().equals("missingno")) {
                            // Use TextureManager's writeSpritePixels (RenderPass blit) to update the atlas
                            TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
                            System.out.println("[TextureEditor] Sky sprite updated in celestials atlas: " + spriteId);
                        } else {
                            System.out.println("[TextureEditor] Sky sprite not found in celestials atlas: " + spriteId);
                        }
                    } else {
                        System.out.println("[TextureEditor] Celestials atlas not found or wrong type");
                    }
                } catch (Exception e) {
                    System.out.println("[TextureEditor] Sky atlas apply failed: " + e.getMessage());
                }
            });
        } else {
            // End sky: standalone texture — replace via NativeImageBackedTexture
            client.execute(() -> {
                NativeImage img = new NativeImage(canvas.getWidth(), canvas.getHeight(), false);
                for (int x = 0; x < canvas.getWidth(); x++)
                    for (int y = 0; y < canvas.getHeight(); y++)
                        img.setColorArgb(x, y, canvas.getPixels()[x][y]);
                var existing = client.getTextureManager().getTexture(textureId);
                if (existing instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt) {
                    nibt.setImage(img);
                    nibt.upload();
                } else {
                    var dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "textureeditor_sky", img);
                    client.getTextureManager().registerTexture(textureId, dynamicTex);
                    dynamicTex.upload();
                }
            });
        }
    }

    @Override
    protected void resetCurrent() {
        if (originalPixels == null || canvas == null) return;

        // Reset canvas: delete all layers, fresh base layer
        canvas.saveSnapshot();
        canvas.setLayerStack(new com.zeeesea.textureeditor.editor.LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();

        if (textureId != null) {
            TextureManager.getInstance().removeTexture(textureId);
        }
        applyLive();
    }
}
