package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.LayerStack;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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

    private final Block block;
    private final BlockState blockState;
    private final BlockPos blockPos;
    private Direction face;
    private final Screen parent;

    // World block constructor
    public EditorScreen(BlockHitResult hitResult) {
        super(Text.literal("Texture Editor"));
        this.blockPos = hitResult.getBlockPos();
        this.face = hitResult.getSide();
        MinecraftClient client = MinecraftClient.getInstance();
        this.blockState = client.world != null ? client.world.getBlockState(blockPos) : null;
        this.block = blockState != null ? blockState.getBlock() : null;
        this.parent = null;
        setTint();
    }

    // Browse block constructor
    public EditorScreen(Block block, Screen parent) {
        super(Text.literal("Block Texture Editor"));
        this.block = block;
        this.blockState = block.getDefaultState();
        this.face = Direction.UP;
        this.blockPos = null;
        this.parent = parent;
        setTint();
    }

    private void setTint() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (blockState != null && client.world != null) {
            int color = client.getBlockColors().getColor(blockState, client.world,
                    blockPos != null ? blockPos : client.player != null ? client.player.getBlockPos() : null, 0);
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
        String name = block != null ? block.getName().getString() : (blockState != null ? blockState.getBlock().getName().getString() : "Unknown");
        String tintLabel = isTinted ? " \u00a7a[Tinted]" : "";
        return "Block Editor - " + name + " (" + face.getName() + ")" + tintLabel;
    }

    @Override
    protected String getResetCurrentLabel() { return "Reset Face"; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected int addExtraButtons(int toolY) {
        // Face cycle button — only at scale <= 4
        if (showFaceButton()) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Face: " + face.getName().toUpperCase()),
                    btn -> {
                        Direction[] dirs = Direction.values();
                        face = dirs[(face.ordinal() + 1) % dirs.length];
                        btn.setMessage(Text.literal("Face: " + face.getName().toUpperCase()));
                        switchFace(face);
                    }
            ).position(5, toolY).size(100, 20).build());
            toolY += 24;
        }

        // Reset Block button — always shown
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
        canvas.setLayerStack(new LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();
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
        resetCurrent();
    }
}