package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.LayerStack;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.texture.TextureResourceLoader;
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

    // Multiple textures per face (e.g. base + overlay)
    private java.util.List<TextureExtractor.BlockFaceTexture> faceTextures = new java.util.ArrayList<>();
    private int faceTextureIndex = 0;

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
        faceTextures = TextureExtractor.extractAll(blockState, face);
        if (faceTextures.isEmpty()) return;
        if (faceTextureIndex >= faceTextures.size()) faceTextureIndex = 0;

        TextureExtractor.BlockFaceTexture tex = faceTextures.get(faceTextureIndex);
        TextureResourceLoader.LoadedTexture defaultTex = TextureResourceLoader.loadTexture(tex.textureId());
        int baseW = defaultTex != null ? defaultTex.width() : tex.width();
        int baseH = defaultTex != null ? defaultTex.height() : tex.height();
        int[][] basePixels = defaultTex != null ? defaultTex.pixels() : tex.pixels();

        originalPixels = copyPixels(basePixels, baseW, baseH);
        textureId = tex.textureId();
        spriteId = Identifier.of(tex.textureId().getNamespace(),
                tex.textureId().getPath().replace("textures/", "").replace(".png", ""));

        System.out.println("[TextureEditor] loadTexture: face=" + face + " layer=" + (faceTextureIndex + 1) + "/" + faceTextures.size() + " texture=" + textureId);

        int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
        int[] savedDims = TextureManager.getInstance().getDimensions(textureId);
        if (savedPixels != null && savedDims != null && savedDims[0] == baseW && savedDims[1] == baseH) {
            canvas = new com.zeeesea.textureeditor.editor.PixelCanvas(savedDims[0], savedDims[1], savedPixels);
        } else {
            canvas = new com.zeeesea.textureeditor.editor.PixelCanvas(baseW, baseH, basePixels);
        }
    }

    @Override
    protected String getEditorTitle() {
        String name = block != null ? block.getName().getString() : (blockState != null ? blockState.getBlock().getName().getString() : "Unknown");
        String tintLabel = isTinted ? " \u00a7a[Tinted]" : "";
        String layerLabel = "";
        if (faceTextures.size() > 1) {
            // Show a short sprite name for clarity
            String spriteName = spriteId != null ? spriteId.getPath() : "";
            // Extract last path segment as short name
            int lastSlash = spriteName.lastIndexOf('/');
            if (lastSlash >= 0) spriteName = spriteName.substring(lastSlash + 1);
            layerLabel = " \u00a7b[" + spriteName + " " + (faceTextureIndex + 1) + "/" + faceTextures.size() + "]";
        }
        return "Block Editor - " + name + " (" + face.name() + ")" + tintLabel + layerLabel;
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
                    Text.literal("Face: " + face.name().toUpperCase()),
                    btn -> {
                        Direction[] dirs = Direction.values();
                        face = dirs[(face.ordinal() + 1) % dirs.length];
                        btn.setMessage(Text.literal("Face: " + face.name().toUpperCase()));
                        faceTextureIndex = 0; // Reset layer index on face switch
                        switchFace(face);
                    }
            ).position(5, toolY).size(tbw, tbh).build());
            toolY += tbh + 4;
        }

        // Texture layer cycle button — only shown when there are multiple textures per face (e.g. grass overlay)
        if (faceTextures.size() > 1) {
            String currentLayerName = getCurrentLayerShortName();
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Tex: " + currentLayerName + " (" + (faceTextureIndex + 1) + "/" + faceTextures.size() + ")"),
                    btn -> {
                        // Save current work before switching
                        if (textureId != null && canvas != null) {
                            TextureManager.getInstance().putTexture(textureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
                        }
                        applyLive();
                        faceTextureIndex = (faceTextureIndex + 1) % faceTextures.size();
                        panOffsetX = 0; panOffsetY = 0;
                        canvas = null;
                        this.clearChildren();
                        this.init();
                    }
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

    private String getCurrentLayerShortName() {
        if (spriteId == null) return "?";
        String path = spriteId.getPath();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private void switchFace(Direction newFace) {
        // Persist current face pixels and apply live before switching
        if (textureId != null && canvas != null) {
            TextureManager.getInstance().putTexture(textureId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight());
        }
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
        final int[][] origCopy = originalPixels;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight(), origCopy));
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
            java.util.List<TextureExtractor.BlockFaceTexture> allTex = TextureExtractor.extractAll(blockState, dir);
            for (TextureExtractor.BlockFaceTexture tex : allTex) {
                TextureResourceLoader.LoadedTexture defaultTex = TextureResourceLoader.loadTexture(tex.textureId());
                int[][] resetPixels = defaultTex != null ? defaultTex.pixels() : tex.pixels();
                int resetW = defaultTex != null ? defaultTex.width() : tex.width();
                int resetH = defaultTex != null ? defaultTex.height() : tex.height();
                Identifier sid = Identifier.of(tex.textureId().getNamespace(),
                        tex.textureId().getPath().replace("textures/", "").replace(".png", ""));
                TextureManager.getInstance().removeTexture(tex.textureId());
                MinecraftClient.getInstance().execute(() ->
                        TextureManager.getInstance().applyLive(sid, resetPixels, resetW, resetH));
            }
        }
        resetCurrent();
    }
}