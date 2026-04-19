ackage com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.EntityTextureSyncPayload;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/**
 * Editor for block breaking (destroy_stage_0..9) frames. Allows switching forward/back like the sky editor.
 */
public class BreakingEditorScreen extends AbstractEditorScreen {

    private final Screen parent;
    private int currentStage = 0;

    public BreakingEditorScreen(Screen parent) {
        super(Component.translatable("textureeditor.screen.editor.title"));
        this.parent = parent;
        this.zoom = 4;
    }

    @Override
    protected int getBackgroundColor() { return com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.SKY_BACKGROUND; }

    @Override
    protected int getMaxZoom() { return 1024; }

    @Override
    protected int getMinZoom() { return 1; }

    @Override
    protected int getZoomStep() { return 1; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected String getEditorTitle() {
        return Component.translatable("textureeditor.screen.editor.title", currentStage).getString();
    }

    @Override
    protected String getResetCurrentLabel() {
        return Component.translatable("textureeditor.button.reset").getString();
    }

    private Identifier stageId(int stage) {
        return new Identifier("minecraft", "textures/block/destroy_stage_" + stage + ".png");
    }

    @Override
    protected void loadTexture() {
        textureId = stageId(currentStage);
        Minecraft client = Minecraft.getInstance();

        int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
        int[] savedDims = TextureManager.getInstance().getDimensions(textureId);

        try {
            var opt = client.getResourceManager().getResource(textureId);
            if (opt.isPresent()) {
                InputStream stream = opt.get().getInputStream();
                NativeImage image = NativeImage.read(stream);
                int w = image.getWidth(), h = image.getHeight();
                originalPixels = new int[w][h];
                for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) originalPixels[x][y] = image.getColorArgb(x, y);
                image.close(); stream.close();

                // Use saved modified if present
                savedPixels = TextureManager.getInstance().getPixels(textureId);
                savedDims = TextureManager.getInstance().getDimensions(textureId);
                if (savedPixels != null && savedDims != null && savedDims[0] == originalPixels.length && savedDims[1] == originalPixels[0].length) {
                    canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
                } else {
                    canvas = new PixelCanvas(originalPixels.length, originalPixels[0].length, originalPixels);
                }
                return;
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load breaking texture: " + textureId + " - " + e.getMessage());
        }

        // fallback: create default small canvas
        if (canvas == null) {
            canvas = new PixelCanvas(32, 32);
            originalPixels = new int[32][32];
        }
    }

    @Override
    protected int addExtraButtons(int toolY) {
        // Add prev/next buttons at top similar to SkyEditorScreen
        int btnX = this.width / 2 - 120;
        addDrawableChild(Button.builder(Component.translatable("textureeditor.button.prev"), btn -> {
            int idx = (currentStage - 1 + 10) % 10;
            switchStage(idx);
        }).position(btnX, 5).size(60, 20).build());
        addDrawableChild(Button.builder(Component.translatable("textureeditor.button.next"), btn -> {
            int idx = (currentStage + 1) % 10;
            switchStage(idx);
        }).position(btnX + 178, 5).size(60, 20).build());
        return toolY;
    }

    @Override
    protected int addExtraLeftGeneralButtons(int y, int x, int w, int bh) {
        int btnW = Math.max(48, (w - 4) / 3);
        int px = x;
        addDrawableChild(Button.builder(Component.translatable("textureeditor.button.prev"), btn -> {
            int idx = (currentStage - 1 + 10) % 10;
            switchStage(idx);
        }).position(px, y).size(btnW, bh).build());
        addDrawableChild(Button.builder(Component.translatable("textureeditor.button.next"), btn -> {
            int idx = (currentStage + 1) % 10;
            switchStage(idx);
        }).position(px + btnW + 4, y).size(btnW, bh).build());
        return y + bh + 4;
    }

    @Override
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {
        int btnX = this.width / 2 - 120;
        context.fill(btnX + 64, 25, btnX + 174, 27, com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.HEADER_UNDERLINE);
    }

    private void switchStage(int stage) {
        applyLive();
        currentStage = stage;
        panOffsetX = 0; panOffsetY = 0;
        canvas = null;
        this.clearChildren();
        this.init();
    }

    @Override
    protected void applyLive() {
        if (textureId == null || canvas == null) return;
        Minecraft client = Minecraft.getInstance();
        TextureManager.getInstance().putTexture(textureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
        // breaking stages are used as standalone textures in block rendering -> update native texture
        client.execute(() -> {
            NativeImage img = new NativeImage(canvas.getWidth(), canvas.getHeight(), false);
            for (int x = 0; x < canvas.getWidth(); x++) for (int y = 0; y < canvas.getHeight(); y++) img.setColorArgb(x, y, canvas.getPixels()[x][y]);
            var existing = client.getTextureManager().getTexture(textureId);
            if (existing instanceof DynamicTexture nibt) {
                nibt.setImage(img);
                nibt.upload();
            } else {
                var dynamicTex = new DynamicTexture(() -> "textureeditor_breaking", img);
                client.getTextureManager().register(textureId, dynamicTex);
                dynamicTex.upload();
            }
        });

        final int[][] px = canvas.getPixels();
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final Identifier sid = textureId;

        if (ModSettings.getInstance().multiplayerSync && sid != null) {
            int[] flat = new int[w * h];
            for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) flat[y * w + x] = px[x][y];

            int[] origFlat = new int[w * h];
            if (originalPixels != null) for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) origFlat[y * w + x] = originalPixels[x][y];
            ClientPlayNetworking.send(new EntityTextureSyncPayload(textureId, null, w, h, flat, origFlat));
        }
    }

    @Override
    protected void resetCurrent() {
        if (originalPixels == null || canvas == null) return;
        canvas.saveSnapshot();
        canvas.setLayerStack(new com.zeeesea.textureeditor.editor.LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();
        if (textureId != null) TextureManager.getInstance().removeTexture(textureId);
        applyLive();
    }
}



