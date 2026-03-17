package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.TextureSyncPayload;
import com.zeeesea.textureeditor.editor.LayerStack;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
        super(Text.translatable("textureeditor.screen.block.title"));
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
        super(Text.translatable("textureeditor.screen.block.title"));
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
        String tintLabel = isTinted ? " \u00a7a" + Text.translatable("textureeditor.label.tinted").getString() : "";
        return Text.translatable("textureeditor.screen.block.editor_title", name, Text.translatable("textureeditor.face." + face.asString().toLowerCase()), tintLabel).getString();
    }

    @Override
    protected String getResetCurrentLabel() { return Text.translatable("textureeditor.button.reset_face").getString(); }

    @Override
    protected Screen getBackScreen() { return parent; }

    @Override
    protected int addExtraButtons(int toolY) {
        int tbw = getToolButtonWidth();
        int tbh = getToolButtonHeight();
        // Face cycle button — only at scale <= 4
        if (showFaceButton()) {
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("textureeditor.button.face", Text.translatable("textureeditor.face." + face.asString().toLowerCase())),
                    btn -> {
                        Direction[] dirs = Direction.values();
                        face = dirs[(face.ordinal() + 1) % dirs.length];
                        btn.setMessage(Text.translatable("textureeditor.button.face", Text.translatable("textureeditor.face." + face.asString().toLowerCase())));
                        switchFace(face);
                    }
            ).position(5, toolY).size(tbw, tbh).build());
            toolY += tbh + 4;
        }

        // Reset Block button — always shown
        int rsw = getRightSidebarWidth();
        int resetBtnW = rsw - 10;
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.reset_block"), btn -> resetBlock())
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

    @Override
    protected void applyLive() {
        if (spriteId == null || canvas == null) return;
        // Capture everything NOW before the lambda runs (canvas may be nulled by switchFace)
        final Identifier sid = spriteId;
        final int[][] px = canvas.getPixels();
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final int[][] origCopy = originalPixels;
        System.out.println("[TextureEditor] EditorScreen.applyLive: spriteId=" + sid + " size=" + w + "x" + h);
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(sid, px, w, h, origCopy));

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
        if (spriteId == null || canvas == null) return;

        // Get the true originals from TextureManager (stored before any modification)
        int[][] trueOriginals = null;
        if (textureId != null) {
            trueOriginals = TextureManager.getInstance().getOriginalPixels(textureId);
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
        final int cw = canvas.getWidth();
        final int ch = canvas.getHeight();
        final int[][] origCopy = copyPixels(originalPixels, cw, ch);
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(sid, origCopy, cw, ch));
    }

    private void resetBlock() {
        if (blockState == null) return;
        for (Direction dir : Direction.values()) {
            TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, dir);
            if (tex == null) continue;
            Identifier tid = tex.textureId();
            Identifier sid = Identifier.of(tid.getNamespace(),
                    tid.getPath().replace("textures/", "").replace(".png", ""));
            // Use stored originals (the true unmodified pixels)
            int[][] origPx = TextureManager.getInstance().getOriginalPixels(tid);
            if (origPx == null) continue; // not modified, skip
            int w = tex.width(), h = tex.height();
            TextureManager.getInstance().removeTexture(tid);
            TextureManager.getInstance().removeOriginal(tid);
            final int[][] px = copyPixels(origPx, w, h);
            MinecraftClient.getInstance().execute(() ->
                    TextureManager.getInstance().applyLive(sid, px, w, h));
        }
        // Reset the current face canvas
        resetCurrent();
    }
}
