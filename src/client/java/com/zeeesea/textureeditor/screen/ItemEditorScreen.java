package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.TextureSyncPayload;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.ItemAnimationResourceLoader;
import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.util.EntityMapper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Item texture editor. Optionally has a parent screen for back navigation.
 */
public class ItemEditorScreen extends AbstractEditorScreen {

    private final ItemStack itemStack;
    private final String itemName;
    private final Screen parent;
    private boolean redirectEvaluated = false;
    private boolean redirectToAnimationEditor = false;
    private List<int[][]> redirectFrames = null;
    private int redirectFrameTimeTicks = 1;
    private boolean redirectInterpolate = false;

    public ItemEditorScreen(ItemStack itemStack, Screen parent) {
        super(Text.translatable("textureeditor.screen.item.title"));
        this.itemStack = itemStack;
        this.itemName = itemStack.getName().getString();
        this.parent = parent;
    }

    @Override
    protected void loadTexture() {
        ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(itemStack);
        if (tex != null) {
            textureId = tex.textureId();
            spriteId = tex.spriteId();

            // Use stored originals if available (atlas may already be modified)
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

            evaluateAnimationRedirect(savedPixels);
        }
    }

    @Override
    protected void init() {
        super.init();
        if (!redirectEvaluated || !redirectToAnimationEditor || textureId == null || spriteId == null) return;

        int[][] current = (canvas != null) ? copyPixels(canvas.getPixels(), canvas.getWidth(), canvas.getHeight()) : null;
        int[][] orig = null;
        if (originalPixels != null && originalPixels.length > 0 && originalPixels[0].length > 0) {
            orig = copyPixels(originalPixels, originalPixels.length, originalPixels[0].length);
        }

        MinecraftClient.getInstance().setScreen(new ItemAnimationEditorScreen(
                itemStack,
                parent,
                textureId,
                spriteId,
                orig,
                current,
                redirectFrames,
                redirectFrameTimeTicks,
                redirectInterpolate
        ));
    }

    @Override
    protected String getEditorTitle() { return Text.translatable("textureeditor.screen.item.editor_title", itemName).getString(); }


    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected String getResetCurrentLabel() { return Text.translatable("textureeditor.button.reset_item").getString(); }

    @Override
    protected int addExtraLeftGeneralButtons(int y, int x, int w, int bh) {
        int px = x;

        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.create_animation"), btn -> {
            int[][] current = (canvas != null) ? copyPixels(canvas.getPixels(), canvas.getWidth(), canvas.getHeight()) : null;
            int[][] orig = null;
            if (originalPixels != null && originalPixels.length > 0 && originalPixels[0].length > 0) {
                orig = copyPixels(originalPixels, originalPixels.length, originalPixels[0].length);
            }
            MinecraftClient.getInstance().setScreen(new ItemAnimationEditorScreen(itemStack, this, textureId, spriteId, orig, current));
        }).position(px, y).size(w, bh).build());
        y += bh + 4;

        // "Edit Mob/Entity" button for spawn eggs, boats, minecarts, etc.
        if (EntityMapper.hasEntityMode(itemStack)) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.edit_mob_entity"), btn -> {
                var entity = EntityMapper.getEntityFromItem(itemStack, MinecraftClient.getInstance().world);
                if (entity != null) {
                    MinecraftClient.getInstance().setScreen(new MobEditorScreen(entity, parent));
                }
            }).position(px, y).size(w, bh).build());
            y += bh + 4;
        }

        // "Edit Wing Texture" button for elytra -> opens the entity/elytra.png texture
        if (itemStack.getItem() == Items.ELYTRA) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.edit_wing_tex"), btn -> {
                Identifier elytraTexId = Identifier.of("minecraft", "textures/entity/elytra.png");
                MinecraftClient.getInstance().setScreen(new GuiTextureEditorScreen(elytraTexId, Text.translatable("textureeditor.elytra.wings").getString(), parent != null ? parent : this));
            }).position(px, y).size(w, bh).build());
            y += bh + 4;
        }

        return y;
    }

    @Override
    protected void applyLive() {
        if (spriteId == null || canvas == null) return;
        if (textureId != null) {
            TextureManager.getInstance().stopItemAnimationLive(textureId);
            TextureManager.getInstance().removeItemAnimation(textureId);
        }
        System.out.println("[TextureEditor] ItemEditor.applyLive: spriteId=" + spriteId + " textureId=" + textureId + " canvas=" + canvas.getWidth() + "x" + canvas.getHeight());
        final int[][] origCopy = originalPixels;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight(), origCopy));

        final int[][] px = canvas.getPixels();
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final Identifier sid = spriteId;

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
            ClientPlayNetworking.send(new TextureSyncPayload(sid, w, h, flat, origFlat));
        }
    }

    @Override
    protected void resetCurrent() {
        if (canvas == null) return;

        // Get true originals
        int[][] trueOriginals = textureId != null ? TextureManager.getInstance().getOriginalPixels(textureId) : null;
        if (trueOriginals == null) trueOriginals = originalPixels;
        if (trueOriginals == null) return;

        originalPixels = copyPixels(trueOriginals, canvas.getWidth(), canvas.getHeight());

        // Reset canvas: delete all layers, fresh base layer
        canvas.saveSnapshot();
        canvas.setLayerStack(new com.zeeesea.textureeditor.editor.LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();

        if (textureId != null) {
            TextureManager.getInstance().removeItemAnimation(textureId);
            TextureManager.getInstance().removeTexture(textureId);
            TextureManager.getInstance().removeOriginal(textureId);
        }
        applyLive();
    }

    private void evaluateAnimationRedirect(int[][] savedPixels) {
        if (redirectEvaluated || textureId == null) return;
        redirectEvaluated = true;

        TextureManager tm = TextureManager.getInstance();

        TextureManager.ItemAnimationData data = tm.getItemAnimation(textureId);
        if (data != null && data.frames() != null && data.frames().size() > 1) {
            redirectToAnimationEditor = true;
            redirectFrames = data.frames();
            redirectFrameTimeTicks = Math.max(1, data.frameTimeTicks());
            redirectInterpolate = data.interpolate();
            return;
        }

        // Local non-animated override should win over pack animation auto-open behavior.
        if (savedPixels != null) return;

        ItemAnimationResourceLoader.LoadedAnimation loaded = ItemAnimationResourceLoader.load(textureId);
        if (loaded != null && loaded.frames() != null && loaded.frames().size() > 1) {
            redirectToAnimationEditor = true;
            redirectFrames = loaded.frames();
            redirectFrameTimeTicks = Math.max(1, loaded.frameTimeTicks());
            redirectInterpolate = loaded.interpolate();
        }
    }
}