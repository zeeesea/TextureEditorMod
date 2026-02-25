package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.util.EntityMapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Item texture editor. Optionally has a parent screen for back navigation.
 */
public class ItemEditorScreen extends AbstractEditorScreen {

    private final ItemStack itemStack;
    private final String itemName;
    private final Screen parent;

    public ItemEditorScreen(ItemStack itemStack, Screen parent) {
        super(Text.literal("Item Texture Editor"));
        this.itemStack = itemStack;
        this.itemName = itemStack.getName().getString();
        this.parent = parent;
    }

    @Override
    protected void loadTexture() {
        ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(itemStack);
        if (tex != null) {
            originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
            textureId = tex.textureId();
            spriteId = tex.spriteId();

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
    protected String getEditorTitle() { return "Item Editor - " + itemName; }

    @Override
    protected String getResetCurrentLabel() { return "Reset Item"; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected int addExtraButtons(int toolY) {
        // "Edit Mob/Entity" button if applicable
        if (EntityMapper.hasEntityMode(itemStack)) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit Mob/Entity"), btn -> {
                var entity = EntityMapper.getEntityFromItem(itemStack, MinecraftClient.getInstance().world);
                if (entity != null) {
                    MinecraftClient.getInstance().setScreen(new MobEditorScreen(entity, parent));
                }
            }).position(this.width - 120, 55).size(110, 20).build());
        }
        return toolY;
    }

    @Override
    protected void applyLive() {
        if (spriteId == null || canvas == null) return;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight()));
    }

    @Override
    protected void resetCurrent() {
        if (originalPixels == null) return;
        canvas.saveSnapshot();
        for (int x = 0; x < canvas.getWidth(); x++)
            for (int y = 0; y < canvas.getHeight(); y++)
                canvas.setPixel(x, y, originalPixels[x][y]);
        if (textureId != null) TextureManager.getInstance().removeTexture(textureId);
        applyLive();
    }
}
