package com.zeeesea.textureeditor.legacy;

import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.screen.AbstractEditorScreen;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;


/**
 * LEGACY: Use EditorScreen instead. This class is no longer maintained.
 */
@Deprecated
public class BlockBrowseEditorScreen extends AbstractEditorScreen {

    private final Block block;
    private final BlockState blockState;
    private final Screen parent;
    private Direction currentFace = Direction.UP;

    public BlockBrowseEditorScreen(Block block, Screen parent) {
        super(Text.literal("Block Texture Editor"));
        this.block = block;
        this.blockState = block.getDefaultState();
        this.parent = parent;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.player != null) {
            int color = client.getBlockColors().getColor(blockState, client.world, client.player.getBlockPos(), 0);
            if (color != -1) {
                blockTint = color | 0xFF000000;
                isTinted = true;
            }
        }
    }

    @Override
    protected void loadTexture() {
        TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, currentFace);
        if (tex != null) {
            textureId = tex.textureId();
            spriteId = Identifier.of(tex.textureId().getNamespace(),
                    tex.textureId().getPath().replace("textures/", "").replace(".png", ""));

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
    protected String getEditorTitle() {
        String name = block.getName().getString();
        String tintLabel = isTinted ? " \u00a7a[Tinted]" : "";
        return "Block Editor - " + name + " (" + currentFace.asString() + ")" + tintLabel;
    }

    @Override
    protected String getResetCurrentLabel() { return "Reset Face"; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected int addExtraButtons(int toolY) {
        // Face selection buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("Top"), btn -> switchFace(Direction.UP)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Bottom"), btn -> switchFace(Direction.DOWN)).position(57, toolY).size(48, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("North"), btn -> switchFace(Direction.NORTH)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("South"), btn -> switchFace(Direction.SOUTH)).position(57, toolY).size(48, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("East"), btn -> switchFace(Direction.EAST)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("West"), btn -> switchFace(Direction.WEST)).position(57, toolY).size(48, 20).build());
        toolY += 24;

        // Reset block button
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Block"), btn -> resetBlock())
                .position(this.width - 115, this.height - 124).size(110, 20).build());

        return toolY;
    }

    private void switchFace(Direction newFace) {
        applyLive();
        currentFace = newFace;
        panOffsetX = 0; panOffsetY = 0;
        canvas = null;
        this.clearChildren();
        this.init();
    }

    @Override
    protected void applyLive() {
        if (spriteId == null || canvas == null) return;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight()));
    }

    @Override
    protected void resetCurrent() {
        if (spriteId == null || canvas == null) return;

        int[][] trueOriginals = textureId != null ? TextureManager.getInstance().getOriginalPixels(textureId) : null;
        if (trueOriginals == null) trueOriginals = originalPixels;
        if (trueOriginals == null) return;

        originalPixels = copyPixels(trueOriginals, canvas.getWidth(), canvas.getHeight());
        canvas.saveSnapshot();
        canvas.setLayerStack(new com.zeeesea.textureeditor.editor.LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();

        if (textureId != null) {
            TextureManager.getInstance().removeTexture(textureId);
            TextureManager.getInstance().removeOriginal(textureId);
        }
        applyLive();
    }

    private void resetBlock() {
        for (Direction dir : Direction.values()) {
            TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, dir);
            if (tex == null) continue;
            Identifier tid = tex.textureId();
            Identifier sid = Identifier.of(tid.getNamespace(),
                    tid.getPath().replace("textures/", "").replace(".png", ""));
            int[][] origPx = TextureManager.getInstance().getOriginalPixels(tid);
            if (origPx == null) continue;
            int w = tex.width(), h = tex.height();
            TextureManager.getInstance().removeTexture(tid);
            TextureManager.getInstance().removeOriginal(tid);
            final int[][] px = copyPixels(origPx, w, h);
            MinecraftClient.getInstance().execute(() ->
                    TextureManager.getInstance().applyLive(sid, px, w, h));
        }
        resetCurrent();
    }
}
