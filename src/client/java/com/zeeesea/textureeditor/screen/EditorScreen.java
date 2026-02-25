package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Block face texture editor. Opened when clicking a block in the world.
 */
public class EditorScreen extends AbstractEditorScreen {

    private final BlockPos blockPos;
    private Direction face;
    private final BlockState blockState;

    public EditorScreen(BlockHitResult hitResult) {
        super(Text.literal("Texture Editor"));
        this.blockPos = hitResult.getBlockPos();
        this.face = hitResult.getSide();
        MinecraftClient client = MinecraftClient.getInstance();
        this.blockState = client.world != null ? client.world.getBlockState(blockPos) : null;

        if (blockState != null && client.world != null) {
            int color = client.getBlockColors().getColor(blockState, client.world, blockPos, 0);
            if (color != -1) {
                blockTint = color | 0xFF000000;
                isTinted = true;
            }
        }
    }

    @Override
    protected void loadTexture() {
        if (blockState == null) return;
        TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, face);
        if (tex != null) {
            originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
            textureId = tex.textureId();
            spriteId = Identifier.of(tex.textureId().getNamespace(),
                    tex.textureId().getPath().replace("textures/", "").replace(".png", ""));

            int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
            int[] savedDims = TextureManager.getInstance().getDimensions(textureId);
            if (savedPixels != null && savedDims != null && savedDims[0] == tex.width() && savedDims[1] == tex.height()) {
                canvas = new com.zeeesea.textureeditor.editor.PixelCanvas(savedDims[0], savedDims[1], savedPixels);
            } else {
                canvas = new com.zeeesea.textureeditor.editor.PixelCanvas(tex.width(), tex.height(), tex.pixels());
            }
        }
    }

    @Override
    protected String getEditorTitle() {
        String blockName = blockState != null ? blockState.getBlock().getName().getString() : "Unknown";
        String tintLabel = isTinted ? " \u00a7a[Tinted]" : "";
        return "Block Editor - " + blockName + " (" + face.getName() + ")" + tintLabel;
    }

    @Override
    protected String getResetCurrentLabel() { return "Reset Face"; }

    @Override
    protected int addExtraButtons(int toolY) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Top"), btn -> switchFace(Direction.UP)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Bottom"), btn -> switchFace(Direction.DOWN)).position(57, toolY).size(48, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("North"), btn -> switchFace(Direction.NORTH)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("South"), btn -> switchFace(Direction.SOUTH)).position(57, toolY).size(48, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("East"), btn -> switchFace(Direction.EAST)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("West"), btn -> switchFace(Direction.WEST)).position(57, toolY).size(48, 20).build());
        toolY += 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Block"), btn -> resetBlock())
                .position(this.width - 115, this.height - 124).size(110, 20).build());

        return toolY;
    }

    private void switchFace(Direction newFace) {
        applyLive();
        this.face = newFace;
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
        if (originalPixels == null || spriteId == null) return;
        canvas.saveSnapshot();
        for (int x = 0; x < canvas.getWidth(); x++)
            for (int y = 0; y < canvas.getHeight(); y++)
                canvas.setPixel(x, y, originalPixels[x][y]);
        applyLive();
    }

    private void resetBlock() {
        if (blockState == null) return;
        for (Direction dir : Direction.values()) {
            TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, dir);
            if (tex != null) {
                Identifier sid = Identifier.of(tex.textureId().getNamespace(),
                        tex.textureId().getPath().replace("textures/", "").replace(".png", ""));
                TextureManager.getInstance().removeTexture(tex.textureId());
                MinecraftClient.getInstance().execute(() ->
                        TextureManager.getInstance().applyLive(sid, tex.pixels(), tex.width(), tex.height()));
            }
        }
        if (originalPixels != null) {
            canvas.saveSnapshot();
            for (int x = 0; x < canvas.getWidth(); x++)
                for (int y = 0; y < canvas.getHeight(); y++)
                    canvas.setPixel(x, y, originalPixels[x][y]);
        }
    }
}
