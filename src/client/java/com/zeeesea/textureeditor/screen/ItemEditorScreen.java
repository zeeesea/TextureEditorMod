package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.TextureSyncPayload;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.settings.ModSettings;
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

/**
 * Item texture editor. Optionally has a parent screen for back navigation.
 */
public class ItemEditorScreen extends AbstractEditorScreen {

    private final ItemStack itemStack;
    private final String itemName;
    private final Screen parent;

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
        }
    }

    @Override
    protected String getEditorTitle() { return Text.translatable("textureeditor.screen.item.editor_title", itemName).getString(); }

    @Override
    protected String getResetCurrentLabel() { return Text.translatable("textureeditor.button.reset_item").getString(); }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected int addExtraButtons(int toolY) {
        int rsw = getRightSidebarWidth();
        int resetBtnW = rsw - 10;
        int tbh = getToolButtonHeight();

        // "Edit Mob/Entity" button for spawn eggs, boats, minecarts, etc.
        if (EntityMapper.hasEntityMode(itemStack)) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.edit_mob_entity"), btn -> {
                var entity = EntityMapper.getEntityFromItem(itemStack, MinecraftClient.getInstance().world);
                if (entity != null) {
                    MinecraftClient.getInstance().setScreen(new MobEditorScreen(entity, parent));
                }
            }).position(this.width - rsw + 5, this.height - 124).size(resetBtnW, tbh).build());
        }

        // "Edit Wing Texture" button for elytra → opens the entity/elytra.png texture
        if (itemStack.getItem() == Items.ELYTRA) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.edit_wing_tex"), btn -> {
                Identifier elytraTexId = Identifier.of("minecraft", "textures/entity/elytra.png");
                MinecraftClient.getInstance().setScreen(new GuiTextureEditorScreen(elytraTexId, Text.translatable("textureeditor.elytra.wings").getString(), parent != null ? parent : this));
            }).position(this.width - rsw + 5, this.height - 148).size(resetBtnW, tbh).build());
        }

        return toolY;
    }

    @Override
    protected void applyLive() {
        if (spriteId == null || canvas == null) return;
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
            TextureManager.getInstance().removeTexture(textureId);
            TextureManager.getInstance().removeOriginal(textureId);
        }
        applyLive();
    }
}