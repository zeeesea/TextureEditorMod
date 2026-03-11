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

    // Overlay support: some blocks (like grass) have multiple texture layers per face
    private int currentQuadIndex = 0;
    private int maxQuadIndex = 0;

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

        // Count overlay layers for this face
        maxQuadIndex = TextureExtractor.getFaceTextureCount(blockState, face) - 1;
        if (maxQuadIndex < 0) maxQuadIndex = 0;
        if (currentQuadIndex > maxQuadIndex) currentQuadIndex = 0;

        TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, face, currentQuadIndex);
        if (tex != null) {
            textureId = tex.textureId();
            spriteId = Identifier.of(tex.textureId().getNamespace(),
                    tex.textureId().getPath().replace("textures/", "").replace(".png", ""));

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
        String overlayLabel = (maxQuadIndex > 0 && currentQuadIndex > 0) ? " \u00a7e[Overlay]" : "";
        return "Block Editor - " + name + " (" + face.asString() + ")" + tintLabel + overlayLabel;
    }

    @Override
    protected String getResetCurrentLabel() { return "Reset Face"; }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected int addExtraButtons(int toolY) {
        int tbw = getToolButtonWidth();
        int tbh = getToolButtonHeight();
        // Face cycle button — only at scale <= 4
        if (showFaceButton()) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Face: " + face.asString().toUpperCase()),
                    btn -> {
                        Direction[] dirs = Direction.values();
                        face = dirs[(face.ordinal() + 1) % dirs.length];
                        btn.setMessage(Text.literal("Face: " + face.asString().toUpperCase()));
                        currentQuadIndex = 0;
                        switchFace(face);
                    }
            ).position(5, toolY).size(tbw, tbh).build());
            toolY += tbh + 4;
        }

        // Overlay toggle button — only shown when face has multiple texture layers (e.g. grass overhang)
        if (maxQuadIndex > 0) {
            String layerLabel = currentQuadIndex == 0 ? "Layer: Base" : "Layer: Overlay " + currentQuadIndex;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00a7e" + layerLabel),
                    btn -> switchOverlay()
            ).position(5, toolY).size(tbw, tbh).build());
            toolY += tbh + 4;
        }

        // Reset Block button — always shown
        int rsw = getRightSidebarWidth();
        int resetBtnW = rsw - 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Block"), btn -> resetBlock())
                .position(this.width - rsw + 5, this.height - 124).size(resetBtnW, tbh).build());

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

    private void switchOverlay() {
        applyLive();
        currentQuadIndex = (currentQuadIndex + 1) % (maxQuadIndex + 1);
        panOffsetX = 0; panOffsetY = 0;
        canvas = null;
        this.clearChildren();
        this.init();
    }

    @Override
    protected void applyLive() {
        if (spriteId == null || canvas == null) return;
        final int[][] origCopy = originalPixels;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight(), origCopy));
    }

    @Override
    protected void resetCurrent() {
        if (spriteId == null || canvas == null) return;

        // Get the true originals from TextureManager (stored before any modification)
        int[][] trueOriginals = null;
        if (textureId != null) {
            trueOriginals = TextureManager.getInstance().getOriginalPixels(textureId);
        }
        // If no stored originals, re-extract from the actual block model as fallback
        if (trueOriginals == null && blockState != null) {
            TextureExtractor.BlockFaceTexture freshTex = TextureExtractor.extract(blockState, face, currentQuadIndex);
            if (freshTex != null) {
                trueOriginals = freshTex.pixels();
            }
        }
        if (trueOriginals == null) {
            trueOriginals = originalPixels;
        }
        if (trueOriginals == null) return;

        // Update our originalPixels to the true originals
        originalPixels = copyPixels(trueOriginals, canvas.getWidth(), canvas.getHeight());

        // Reset canvas: delete all layers, create fresh base layer with original pixels
        canvas.saveSnapshot();
        canvas.setLayerStack(new LayerStack(canvas.getWidth(), canvas.getHeight(), originalPixels));
        canvas.invalidateCache();

        // Remove stored modifications so this face is no longer "modified"
        if (textureId != null) {
            TextureManager.getInstance().removeTexture(textureId);
            TextureManager.getInstance().removeOriginal(textureId);
        }

        // Apply original pixels back to the atlas
        final Identifier sid = spriteId;
        final int[][] origCopy = copyPixels(originalPixels, canvas.getWidth(), canvas.getHeight());
        final int cw = canvas.getWidth(), ch = canvas.getHeight();
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(sid, origCopy, cw, ch));
    }

    private void resetBlock() {
        if (blockState == null) return;
        for (Direction dir : Direction.values()) {
            int layerCount = TextureExtractor.getFaceTextureCount(blockState, dir);
            for (int qi = 0; qi < layerCount; qi++) {
                TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, dir, qi);
                if (tex == null) continue;
                Identifier tid = tex.textureId();
                Identifier sid = Identifier.of(tid.getNamespace(),
                        tid.getPath().replace("textures/", "").replace(".png", ""));
                // Use stored originals (the true unmodified pixels)
                int[][] origPx = TextureManager.getInstance().getOriginalPixels(tid);
                if (origPx == null) continue; // not modified, skip
                int w = tex.width(), h = tex.height();
                final int[][] px = copyPixels(origPx, w, h);
                // Remove AFTER copying
                TextureManager.getInstance().removeTexture(tid);
                TextureManager.getInstance().removeOriginal(tid);
                MinecraftClient.getInstance().execute(() ->
                        TextureManager.getInstance().applyLive(sid, px, w, h));
            }
        }
        // Reload current face canvas from the block model (not from stored data)
        panOffsetX = 0; panOffsetY = 0;
        canvas = null;
        this.clearChildren();
        this.init();
    }
}
