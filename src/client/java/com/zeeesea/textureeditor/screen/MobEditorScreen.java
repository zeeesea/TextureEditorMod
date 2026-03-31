package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.EntityTextureSyncPayload;
import com.zeeesea.textureeditor.TextureSyncPayload;
import com.zeeesea.textureeditor.editor.LayerStack;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.MobTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.util.EntityMapper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
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
        super(Text.translatable("textureeditor.screen.mob.title"));
        this.entity = entity;
        this.entityName = entity.getType().getName().getString();
        this.parent = parent;
        this.zoom = 6; // mob textures are usually 64x64
        // initialize preview widget early so General-tab buttons can safely toggle it
        this.mobPreview = new MobPreviewWidget(entity);
        this.mobPreview.setPosition(115, 30, 140, 160);
    }


    @Override
    protected void loadTexture() {
        MobTextureExtractor.MobTexture tex = MobTextureExtractor.extract(entity);

        if (tex != null) {
            textureId = tex.textureId();

            // Use stored originals if available (texture may already be modified)
            int[][] storedOriginals = TextureManager.getInstance().getOriginalPixels(textureId);
            if (storedOriginals != null) {
                originalPixels = copyPixels(storedOriginals, tex.width(), tex.height());
            } else {
                originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
            }

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
    protected String getEditorTitle() { return Text.translatable("textureeditor.screen.mob.editor_title", entityName).getString(); }

    @Override
    protected String getResetCurrentLabel() { return Text.translatable("textureeditor.button.reset_mob").getString(); }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override protected int getMaxZoom() { return 1024; }
    @Override protected int getMinZoom() { return 1; }
    @Override protected int getZoomStep() { return 1; }

    @Override
    protected int addExtraButtons(int toolY) {
        // No absolute-positioned controls here any more; controls live in the General tab via addExtraLeftGeneralButtons
        return toolY;
    }

    @Override
    protected int addExtraLeftGeneralButtons(int y, int x, int w, int bh) {
        int px = x;
        // 3D Preview toggle in general tab
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.preview_3d"), btn -> {
            if (mobPreview == null) {
                mobPreview = new MobPreviewWidget(entity);
                mobPreview.setPosition(115, 30, 140, 160);
            }
            mobPreview.toggleVisible(); mobPreviewActive = mobPreview.isVisible();
        })
                .position(px, y).size(w, bh).build());
        y += bh + 4;

        // Edit Item button if this entity maps to an item
        if (EntityMapper.hasItemMode(entity)) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.edit_item"), btn -> {
                ItemStack itemStack = EntityMapper.getItemFromEntity(entity);
                if (itemStack != null) MinecraftClient.getInstance().setScreen(new ItemEditorScreen(itemStack, parent));
            }).position(px, y).size(w, bh).build());
            y += bh + 4;
        }
        return y;
    }

    @Override
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {
        if (mobPreview != null) {
            // Compute dynamic position: if the left panel is collapsed, move preview to the left side;
            // otherwise keep it near the right area so it doesn't overlap the panels.
            int pw = 140, ph = 160;
            int px;
            if (!leftOpen) {
                // left panel is collapsed — place preview near left edge (after the toggle)
                px = 18; // small offset to avoid the toggle button at x=0
            } else {
                // left panel open — place preview on the right side, avoiding the right sidebar
                px = this.width - getRightSidebarWidth() - pw - 12;
            }
            // Clamp to screen
            if (px < 2) px = 2;
            mobPreview.setPosition(px, 30, pw, ph);
            mobPreview.render(context, mouseX, mouseY);
        }
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
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        boolean b = super.mouseDragged(click, dx, dy);
        if (mobPreviewActive) applyLive();
        return b;
    }

    @Override
    protected void applyLive() {
        if (textureId == null || canvas == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        // Store original for preview support
        if (originalPixels != null) {
            TextureManager.getInstance().storeOriginal(textureId, originalPixels, canvas.getWidth(), canvas.getHeight());
        }
        TextureManager.getInstance().putTexture(textureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final int[][] pixelsCopy = copyPixels(canvas.getPixels(), w, h);
        final Identifier texId = textureId;
        client.execute(() -> {
            // Create NativeImage from canvas
            var img = new net.minecraft.client.texture.NativeImage(w, h, false);
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++)
                    img.setColorArgb(x, y, pixelsCopy[x][y]);

            var existing = client.getTextureManager().getTexture(texId);

            if (existing != null) {
                try {
                    var gpuTex = existing.getGlTexture();
                    if (gpuTex != null) {
                        int texW = gpuTex.getWidth(0);
                        int texH = gpuTex.getHeight(0);

                        if (texW == w && texH == h) {
                            com.mojang.blaze3d.systems.RenderSystem.getDevice()
                                .createCommandEncoder()
                                .writeToTexture(gpuTex, img);
                            img.close();
                            return;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[TextureEditor] Direct mob upload failed: " + e.getMessage());
                }
            }

            // Fallback: create new NativeImageBackedTexture
            try {
                if (existing != null) {
                    existing.close();
                }
            } catch (Exception ignored) {}
            var dynamicTex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "textureeditor_mob", img);
            client.getTextureManager().registerTexture(texId, dynamicTex);
            dynamicTex.upload();
        });


        final int[][] px = canvas.getPixels();
        final Identifier sid = textureId;

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
            ClientPlayNetworking.send(new EntityTextureSyncPayload(sid, null, w, h, flat, origFlat));
        }
    }

    @Override
    protected void resetCurrent() {
        if (canvas == null || textureId == null) return;

        // Get true originals
        int[][] trueOriginals = TextureManager.getInstance().getOriginalPixels(textureId);
        if (trueOriginals == null) trueOriginals = originalPixels;
        if (trueOriginals == null) return;

        originalPixels = copyPixels(trueOriginals, canvas.getWidth(), canvas.getHeight());

        // Reset canvas: delete all layers, create fresh base layer
        canvas.saveSnapshot();
        canvas.setLayerStack(new LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels, com.zeeesea.textureeditor.settings.ModSettings.getInstance().oneLayerByDefault));
        canvas.invalidateCache();

        // Remove stored modifications
        TextureManager.getInstance().removeTexture(textureId);
        TextureManager.getInstance().removeOriginal(textureId);

        applyLive();
    }
}
