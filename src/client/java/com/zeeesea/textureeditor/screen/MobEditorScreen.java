package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.LayerStack;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.MobTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Mob/Entity texture editor with optional parent screen and 3D preview.
 */
public class MobEditorScreen extends AbstractEditorScreen {

    private final Entity entity;
    private final String entityName;
    private final Screen parent;
    private MobPreviewWidget mobPreview;
    private boolean mobPreviewActive = false;

    public MobEditorScreen(Entity entity, Screen parent) {
        super(Text.literal("Mob Texture Editor"));
        this.entity = entity;
        this.entityName = entity.getType().getName().getString();
        this.parent = parent;
        this.zoom = 6; // mob textures are usually 64x64
    }

    @Override
    protected void loadTexture() {
        MobTextureExtractor.MobTexture tex = MobTextureExtractor.extract(entity);
        if (tex != null) {
            originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
            textureId = tex.textureId();

            int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
            int[] savedDims = TextureManager.getInstance().getDimensions(textureId);
            if (savedPixels != null && savedDims != null && savedDims[0] == tex.width() && savedDims[1] == tex.height()) {
                canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
            } else {
                canvas = new PixelCanvas(tex.width(), tex.height(), tex.pixels());
            }
        }
        if (canvas == null) {
            canvas = new PixelCanvas(64, 64);
            originalPixels = new int[64][64];
        }
    }

    @Override
    protected String getEditorTitle() { return "Mob Editor - " + entityName; }

    @Override
    protected String getResetCurrentLabel() { return "Reset Mob"; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override protected int getMaxZoom() { return 20; }
    @Override protected int getMinZoom() { return 1; }
    @Override protected int getZoomStep() { return 1; }

    @Override
    protected int addExtraButtons(int toolY) {
        // 3D Preview toggle
        mobPreview = new MobPreviewWidget(entity);
        mobPreview.setPosition(115, 30, 140, 160);
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7d3D"), btn -> {mobPreview.toggleVisible(); mobPreviewActive = mobPreview.isVisible(); })
                .position(this.width - 195, this.height - 26).size(60, 20).build());
        return toolY;
    }

    @Override
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {
        if (mobPreview != null) mobPreview.render(context, mouseX, mouseY);
    }

    @Override
    protected boolean handleExtraClick(double mx, double my, int btn) {
        return mobPreview != null && mobPreview.mouseClicked(mx, my, btn);
    }

    @Override
    protected boolean handleExtraRelease(double mx, double my, int btn) {
        return mobPreview != null && mobPreview.mouseReleased(mx, my, btn);
    }

    @Override
    protected boolean handleExtraDrag(double mx, double my, int btn, double dx, double dy) {
        return mobPreview != null && mobPreview.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean handleExtraScroll(double mx, double my, double ha, double va) {
        return mobPreview != null && mobPreview.mouseScrolled(mx, my, ha, va);
    }

    @Override
    protected void handleCanvasClick(int px, int py, int btn) {
        super.handleCanvasClick(px,py,btn);
        if (mobPreviewActive) applyLive();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        boolean b = super.mouseDragged(mx, my, btn, dx, dy);
        if (mobPreviewActive) applyLive();
        return b;
    }

    @Override
    protected void applyLive() {
        if (textureId == null || canvas == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager.getInstance().putTexture(textureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
        client.execute(() -> {
            try (var img = new net.minecraft.client.texture.NativeImage(canvas.getWidth(), canvas.getHeight(), false)) {
                for (int x = 0; x < canvas.getWidth(); x++)
                    for (int y = 0; y < canvas.getHeight(); y++)
                        img.setColorArgb(x, y, canvas.getPixels()[x][y]);
                var tex = client.getTextureManager().getTexture(textureId);
                if (tex != null) { tex.bindTexture(); img.upload(0, 0, 0, false); }
            }
        });
    }

    @Override
    protected void resetCurrent() {
        if (originalPixels == null || textureId == null) return;
        canvas.saveSnapshot();
        canvas.setLayerStack(new LayerStack(canvas.getWidth(),canvas.getHeight(), originalPixels));
        canvas.invalidateCache();
        applyLive();
    }
}
