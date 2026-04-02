package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.LayerStack;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated item animation editor with frame strip controls under the canvas.
 */
public class ItemAnimationEditorScreen extends AbstractEditorScreen {

    private final ItemStack itemStack;
    private final String itemName;
    private final Screen parent;
    private final Identifier initialTextureId;
    private final Identifier initialSpriteId;
    private final int[][] initialOriginalPixels;
    private final int[][] initialFramePixels;
    private final List<int[][]> initialFrames;
    private final int initialFrameTimeTicks;

    private final List<int[][]> frames = new ArrayList<>();
    private int activeFrame = 0;
    private int fps = 10;

    private int controlsX;
    private int controlsY;
    private int controlsW;
    private int controlsH;
    private int addBtnX;
    private int removeBtnX;
    private int fpsMinusX;
    private int fpsPlusX;
    private int stripStartX;
    private int stripY;
    private int stripW;
    private int visibleFrameCount;
    private int frameStripOffset;
    private int scrollTrackX;
    private int scrollTrackY;
    private int scrollTrackW;

    private static final int BTN_W = 22;
    private static final int BTN_H = 16;
    private static final int THUMB_SIZE = 20;
    private static final int THUMB_GAP = 4;

    public ItemAnimationEditorScreen(ItemStack itemStack, Screen parent,
                                     Identifier textureId, Identifier spriteId,
                                     int[][] originalPixels, int[][] currentFramePixels) {
        this(itemStack, parent, textureId, spriteId, originalPixels, currentFramePixels, null, 1);
    }

    public ItemAnimationEditorScreen(ItemStack itemStack, Screen parent,
                                     Identifier textureId, Identifier spriteId,
                                     int[][] originalPixels, int[][] currentFramePixels,
                                     List<int[][]> preloadedFrames, int preloadedFrameTimeTicks) {
        super(Text.translatable("textureeditor.screen.item.title"));
        this.itemStack = itemStack;
        this.itemName = itemStack.getName().getString();
        this.parent = parent;
        this.initialTextureId = textureId;
        this.initialSpriteId = spriteId;
        this.initialOriginalPixels = copyMaybe(originalPixels);
        this.initialFramePixels = copyMaybe(currentFramePixels);
        this.initialFrames = preloadedFrames != null ? copyFrameList(preloadedFrames) : null;
        this.initialFrameTimeTicks = Math.max(1, preloadedFrameTimeTicks);
    }

    @Override
    protected void loadTexture() {
        TextureManager tm = TextureManager.getInstance();

        if (initialTextureId != null && initialSpriteId != null && initialFramePixels != null) {
            textureId = initialTextureId;
            spriteId = initialSpriteId;
            int w = initialFramePixels.length;
            int h = w > 0 ? initialFramePixels[0].length : 0;
            if (w > 0 && h > 0) {
                originalPixels = initialOriginalPixels != null ? copyPixels(initialOriginalPixels, w, h) : null;
            }
        }

        if (textureId == null || spriteId == null) {
            ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(itemStack);
            if (tex != null) {
                textureId = tex.textureId();
                spriteId = tex.spriteId();
                int[][] storedOriginals = tm.getOriginalPixels(textureId);
                if (storedOriginals != null) {
                    originalPixels = copyPixels(storedOriginals, tex.width(), tex.height());
                } else {
                    originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
                }
                if (initialFramePixels == null) {
                    frames.add(copyPixels(tex.pixels(), tex.width(), tex.height()));
                }
            }
        }

        if (textureId == null || spriteId == null) {
            if (canvas == null) {
                canvas = new PixelCanvas(16, 16);
                originalPixels = new int[16][16];
                frames.add(new int[16][16]);
            }
            return;
        }

        TextureManager.ItemAnimationData anim = tm.getItemAnimation(textureId);
        if (anim != null && anim.frames() != null && !anim.frames().isEmpty()) {
            frames.clear();
            for (int[][] frame : anim.frames()) {
                frames.add(copyPixels(frame, anim.width(), anim.height()));
            }
            fps = frameTimeToFps(anim.frameTimeTicks());
        } else if (initialFrames != null && !initialFrames.isEmpty()) {
            frames.clear();
            frames.addAll(copyFrameList(initialFrames));
            fps = frameTimeToFps(initialFrameTimeTicks);
        } else if (frames.isEmpty()) {
            int[][] source = null;
            int[] dims = tm.getDimensions(textureId);
            int[][] saved = tm.getPixels(textureId);
            if (saved != null && dims != null && dims[0] > 0 && dims[1] > 0) {
                source = copyPixels(saved, dims[0], dims[1]);
            } else if (initialFramePixels != null && initialFramePixels.length > 0 && initialFramePixels[0].length > 0) {
                source = copyPixels(initialFramePixels, initialFramePixels.length, initialFramePixels[0].length);
            }
            if (source == null && originalPixels != null && originalPixels.length > 0 && originalPixels[0].length > 0) {
                source = copyPixels(originalPixels, originalPixels.length, originalPixels[0].length);
            }
            if (source == null) source = new int[16][16];
            frames.add(source);
        }

        activeFrame = Math.max(0, Math.min(activeFrame, frames.size() - 1));
        int[][] frame = frames.get(activeFrame);
        int w = frame.length;
        int h = frame[0].length;
        canvas = new PixelCanvas(w, h, frame);
        if (originalPixels == null || originalPixels.length != w || originalPixels[0].length != h) {
            originalPixels = copyPixels(frame, w, h);
        }
    }

    @Override
    protected String getEditorTitle() {
        return "Item Animation Editor - " + itemName;
    }

    @Override
    protected Screen getBackScreen() {
        return parent;
    }

    @Override
    protected String getResetCurrentLabel() {
        return Text.translatable("textureeditor.button.reset_item").getString();
    }

    @Override
    protected int addExtraLeftGeneralButtons(int y, int x, int w, int bh) {
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.delete_animation"), btn -> {
            removeAnimationAndReturn();
        }).position(x, y).size(w, bh).build());
        return y + bh + 4;
    }

    @Override
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {
        if (canvas == null) return;
        saveCurrentFrame();
        computeControlsLayout();

        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        context.fill(controlsX, controlsY, controlsX + controlsW, controlsY + controlsH, pal.STATUS_BAR_BG);
        context.fill(controlsX, controlsY, controlsX + controlsW, controlsY + 1, pal.PANEL_SEPARATOR);
        context.fill(controlsX, controlsY + controlsH - 1, controlsX + controlsW, controlsY + controlsH, pal.PANEL_SEPARATOR);

        drawButton(context, addBtnX, controlsY + 4, "+F");
        drawButton(context, removeBtnX, controlsY + 4, "-F");
        drawButton(context, fpsMinusX, controlsY + 4, "-");
        drawButton(context, fpsPlusX, controlsY + 4, "+");
        context.drawText(textRenderer, "FPS " + fps, fpsPlusX + BTN_W + 6, controlsY + 8, pal.STATUS_TEXT, false);

        for (int i = 0; i < visibleFrameCount; i++) {
            int frameIndex = frameStripOffset + i;
            if (frameIndex >= frames.size()) break;

            int x = stripStartX + i * (THUMB_SIZE + THUMB_GAP);
            int y = stripY;
            context.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, 0xFF202020);
            drawFrameThumb(context, frames.get(frameIndex), x + 1, y + 1);
            int border = frameIndex == activeFrame ? 0xFFFFFF55 : 0xFF666666;
            drawRectOutline(context, x, y, x + THUMB_SIZE, y + THUMB_SIZE, border);
        }

        if (frames.size() > visibleFrameCount && scrollTrackW > 6) {
            context.fill(scrollTrackX, scrollTrackY, scrollTrackX + scrollTrackW, scrollTrackY + 3, 0xFF353535);
            int maxOffset = Math.max(1, frames.size() - visibleFrameCount);
            int thumbW = Math.max(8, Math.round(scrollTrackW * (visibleFrameCount / (float) frames.size())));
            int usableW = Math.max(1, scrollTrackW - thumbW);
            int thumbX = scrollTrackX + Math.round((frameStripOffset / (float) maxOffset) * usableW);
            context.fill(thumbX, scrollTrackY, thumbX + thumbW, scrollTrackY + 3, 0xFFDDDD88);
        }
    }

    @Override
    protected boolean handleExtraClick(double mx, double my, int btn) {
        if (btn != 0) return false;
        if (!inControls(mx, my)) return false;

        if (inRect(mx, my, addBtnX, controlsY + 4, BTN_W, BTN_H)) {
            addFrame();
            return true;
        }
        if (inRect(mx, my, removeBtnX, controlsY + 4, BTN_W, BTN_H)) {
            removeFrame();
            return true;
        }
        if (inRect(mx, my, fpsMinusX, controlsY + 4, BTN_W, BTN_H)) {
            fps = Math.max(1, fps - 1);
            return true;
        }
        if (inRect(mx, my, fpsPlusX, controlsY + 4, BTN_W, BTN_H)) {
            fps = Math.min(60, fps + 1);
            return true;
        }

        if (frames.size() > visibleFrameCount && inScrollBarRegion(mx, my)) {
            jumpScrollTo(mx);
            return true;
        }

        for (int i = 0; i < visibleFrameCount; i++) {
            int frameIndex = frameStripOffset + i;
            if (frameIndex >= frames.size()) break;
            int x = stripStartX + i * (THUMB_SIZE + THUMB_GAP);
            if (inRect(mx, my, x, stripY, THUMB_SIZE, THUMB_SIZE)) {
                selectFrame(frameIndex);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleExtraScroll(double mx, double my, double ha, double va) {
        if (!inFrameStripRegion(mx, my) && !inScrollBarRegion(mx, my)) return false;
        if (frames.size() <= visibleFrameCount) return true;
        if (va > 0) frameStripOffset--;
        else if (va < 0) frameStripOffset++;
        clampFrameStripOffset();
        return true;
    }

    @Override
    protected void applyLive() {
        if (spriteId == null || textureId == null || canvas == null || frames.isEmpty()) return;
        saveCurrentFrame();

        TextureManager tm = TextureManager.getInstance();
        if (frames.size() <= 1) {
            tm.stopItemAnimationLive(textureId);
            tm.removeItemAnimation(textureId);
            int[][] px = copyPixels(frames.getFirst(), canvas.getWidth(), canvas.getHeight());
            MinecraftClient.getInstance().execute(() -> tm.applyLive(spriteId, px, canvas.getWidth(), canvas.getHeight(), originalPixels));
            return;
        }

        int frameTime = fpsToFrameTime(fps);
        tm.startItemAnimationLive(textureId, spriteId, copyFrameList(frames), canvas.getWidth(), canvas.getHeight(), frameTime, originalPixels);
    }

    @Override
    protected void resetCurrent() {
        if (canvas == null || textureId == null) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int[][] trueOriginals = TextureManager.getInstance().getOriginalPixels(textureId);
        if (trueOriginals == null) trueOriginals = originalPixels;
        if (trueOriginals == null) return;

        originalPixels = copyPixels(trueOriginals, w, h);
        frames.clear();
        frames.add(copyPixels(trueOriginals, w, h));
        activeFrame = 0;
        canvas.saveSnapshot();
        canvas.setLayerStack(new LayerStack(w, h, frames.getFirst()));
        canvas.invalidateCache();

        TextureManager.getInstance().removeItemAnimation(textureId);
        TextureManager.getInstance().removeTexture(textureId);
        TextureManager.getInstance().removeOriginal(textureId);
        applyLive();
    }

    private void removeAnimationAndReturn() {
        if (textureId == null || spriteId == null || canvas == null) {
            MinecraftClient.getInstance().setScreen(new ItemEditorScreen(itemStack, parent));
            return;
        }

        saveCurrentFrame();
        TextureManager tm = TextureManager.getInstance();
        tm.stopItemAnimationLive(textureId);
        tm.removeItemAnimation(textureId);

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int[][] staticPixels = copyPixels(canvas.getPixels(), w, h);
        tm.applyLive(spriteId, staticPixels, w, h, originalPixels);

        MinecraftClient.getInstance().setScreen(new ItemEditorScreen(itemStack, parent));
    }

    private void addFrame() {
        if (canvas == null) return;
        saveCurrentFrame();
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int[][] duplicate = copyPixels(frames.get(activeFrame), w, h);
        int insertAt = activeFrame + 1;
        frames.add(insertAt, duplicate);
        selectFrame(insertAt);
    }

    private void removeFrame() {
        if (frames.size() <= 1 || canvas == null) return;
        saveCurrentFrame();
        frames.remove(activeFrame);
        activeFrame = Math.max(0, Math.min(activeFrame, frames.size() - 1));
        int[][] px = frames.get(activeFrame);
        canvas = new PixelCanvas(px.length, px[0].length, px);
        keepActiveFrameVisible();
    }

    private void selectFrame(int index) {
        if (canvas == null || index < 0 || index >= frames.size() || index == activeFrame) return;
        saveCurrentFrame();
        activeFrame = index;
        int[][] px = frames.get(activeFrame);
        canvas = new PixelCanvas(px.length, px[0].length, px);
        keepActiveFrameVisible();
    }

    private void saveCurrentFrame() {
        if (canvas == null || frames.isEmpty() || activeFrame < 0 || activeFrame >= frames.size()) return;
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        frames.set(activeFrame, copyPixels(canvas.getPixels(), w, h));
    }

    private void computeControlsLayout() {
        int workspaceX = getLeftSidebarWidth();
        int workspaceW = Math.max(120, this.width - getLeftSidebarWidth() - getRightSidebarWidth());

        controlsX = workspaceX;
        controlsW = workspaceW;
        controlsH = 32;
        // Fixed bar position above the status bar, independent from canvas position/zoom.
        controlsY = this.height - 14 - controlsH;

        addBtnX = controlsX + 8;
        removeBtnX = addBtnX + BTN_W + 2;
        fpsMinusX = removeBtnX + BTN_W + 10;
        fpsPlusX = fpsMinusX + BTN_W + 2;

        stripStartX = fpsPlusX + BTN_W + 54;
        stripY = controlsY + 5;
        stripW = Math.max(0, controlsX + controlsW - stripStartX - 10);
        visibleFrameCount = Math.max(1, stripW / (THUMB_SIZE + THUMB_GAP));

        scrollTrackX = stripStartX;
        scrollTrackY = stripY + THUMB_SIZE + 2;
        scrollTrackW = stripW;

        clampFrameStripOffset();
    }

    private void clampFrameStripOffset() {
        int maxOffset = Math.max(0, frames.size() - visibleFrameCount);
        frameStripOffset = Math.max(0, Math.min(maxOffset, frameStripOffset));
    }

    private void keepActiveFrameVisible() {
        if (activeFrame < frameStripOffset) frameStripOffset = activeFrame;
        int maxVisibleIndex = frameStripOffset + visibleFrameCount - 1;
        if (activeFrame > maxVisibleIndex) frameStripOffset = activeFrame - visibleFrameCount + 1;
        frameStripOffset = Math.max(0, Math.min(Math.max(0, frames.size() - visibleFrameCount), frameStripOffset));
    }

    private void jumpScrollTo(double mx) {
        int maxOffset = Math.max(0, frames.size() - visibleFrameCount);
        if (maxOffset <= 0 || scrollTrackW <= 1) return;
        double t = (mx - scrollTrackX) / (double) scrollTrackW;
        t = Math.max(0.0, Math.min(1.0, t));
        frameStripOffset = (int) Math.round(t * maxOffset);
        clampFrameStripOffset();
    }

    private void drawButton(DrawContext context, int x, int y, String label) {
        context.fill(x, y, x + BTN_W, y + BTN_H, 0xFF2B2B2B);
        drawRectOutline(context, x, y, x + BTN_W, y + BTN_H, 0xFF888888);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + BTN_W / 2, y + 4, 0xFFFFFFFF);
    }

    private void drawFrameThumb(DrawContext context, int[][] frame, int x, int y) {
        if (frame == null || frame.length == 0 || frame[0].length == 0) return;
        int fw = frame.length;
        int fh = frame[0].length;
        int tw = THUMB_SIZE - 2;
        int th = THUMB_SIZE - 2;
        for (int tx = 0; tx < tw; tx++) {
            int sx = (int) (tx * (fw / (float) tw));
            sx = Math.max(0, Math.min(fw - 1, sx));
            for (int ty = 0; ty < th; ty++) {
                int sy = (int) (ty * (fh / (float) th));
                sy = Math.max(0, Math.min(fh - 1, sy));
                context.fill(x + tx, y + ty, x + tx + 1, y + ty + 1, frame[sx][sy]);
            }
        }
    }

    private boolean inControls(double mx, double my) {
        return mx >= controlsX && mx < controlsX + controlsW && my >= controlsY && my < controlsY + controlsH;
    }

    private boolean inFrameStripRegion(double mx, double my) {
        return mx >= stripStartX && mx < stripStartX + stripW && my >= stripY && my < stripY + THUMB_SIZE;
    }

    private boolean inScrollBarRegion(double mx, double my) {
        return mx >= scrollTrackX && mx < scrollTrackX + scrollTrackW && my >= scrollTrackY - 1 && my < scrollTrackY + 5;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int fpsToFrameTime(int fps) {
        return Math.max(1, Math.round(20f / Math.max(1, fps)));
    }

    private static int frameTimeToFps(int frameTimeTicks) {
        return Math.max(1, Math.min(60, Math.round(20f / Math.max(1, frameTimeTicks))));
    }

    private static int[][] copyMaybe(int[][] src) {
        if (src == null || src.length == 0 || src[0].length == 0) return null;
        return copyPixels(src, src.length, src[0].length);
    }

    private static List<int[][]> copyFrameList(List<int[][]> src) {
        List<int[][]> out = new ArrayList<>(src.size());
        for (int[][] frame : src) {
            if (frame == null || frame.length == 0 || frame[0].length == 0) continue;
            out.add(copyPixels(frame, frame.length, frame[0].length));
        }
        return out;
    }
}

