package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.ColorHistory;
import com.zeeesea.textureeditor.editor.EditorTool;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.zeeesea.textureeditor.screen.QuickSelectWheel.Slice;

import java.util.List;

/**
 * Base class for all texture editor screens.
 * Contains shared UI: canvas rendering, tools, palette, color picker, layer panel, zoom/pan, etc.
 * Subclasses override loadTexture(), applyLive(), resetCurrent(), resetAll(), and addExtraButtons().
 */
public abstract class AbstractEditorScreen extends Screen {

    // Texture data (set by subclass in loadTexture())
    protected Identifier spriteId;
    protected Identifier textureId;
    protected PixelCanvas canvas;
    protected int[][] originalPixels;

    // Tint support (used by block editors)
    protected int blockTint = -1;
    protected boolean isTinted = false;

    // Editor state
    protected EditorTool currentTool = EditorTool.PENCIL;
    protected int currentColor = 0xFFFF0000;
    protected int zoom = 12;
    protected boolean showGrid = true;
    protected int toolSize = 1;
    protected boolean previewingOriginal = false;

    // Canvas rendering position
    protected int canvasBaseX, canvasBaseY;
    protected int panOffsetX = 0, panOffsetY = 0;
    protected int canvasScreenX, canvasScreenY;

    // Panning state
    protected boolean isPanning = false;
    private double panStartMouseX, panStartMouseY;
    private int panStartOffsetX, panStartOffsetY;

    // Line tool
    private int lineStartX = -1, lineStartY = -1;
    private boolean lineFirstClick = false;

    protected PanelType currentPanel = PanelType.NONE;
    protected enum PanelType { NONE, COLOR_PANEL, LAYER_PANEL }
    private float pickerHue = 0f, pickerSat = 1f, pickerVal = 1f;
    private float cachedPickerHue = -1f;
    private static final int PICKER_SV_W = 120, PICKER_SV_H = 80;
    // Cached picker textures for performance (avoids thousands of fill() calls per frame)
    private net.minecraft.client.texture.NativeImageBackedTexture pickerSvTexture = null;
    private net.minecraft.client.texture.NativeImageBackedTexture pickerHueTexture = null;
    private static final Identifier PICKER_SV_TEX_ID = Identifier.of("textureeditor", "picker_sv");
    private static final Identifier PICKER_HUE_TEX_ID = Identifier.of("textureeditor", "picker_hue");
    private boolean pickerHueBarBuilt = false;

    private int lastDrawX = -1, lastDrawY = -1;

    protected static final int[] PALETTE = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF,

            0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF80FF00,
            0xFF00FF00, 0xFF00FF80, 0xFF00FFFF, 0xFF0080FF,
            0xFF0000FF, 0xFF8000FF, 0xFFFF00FF, 0xFFFF0080,

            0xFF800000, 0xFF804000, 0xFF808000, 0xFF408000,
            0xFF008000, 0xFF008040, 0xFF008080, 0xFF004080,
            0xFF000080, 0xFF400080, 0xFF800080, 0xFF800040,

            0xFFFFB3B3, 0xFFFFD9B3, 0xFFFFFFB3, 0xFFB3FFB3,
            0xFFB3FFFF
    };

    protected TextFieldWidget hexInput;

    protected QuickSelectWheel quickSelectWheel = new QuickSelectWheel(50);

    protected AbstractEditorScreen(Text title) {
        super(title);
    }

    // --- Abstract methods for subclasses ---

    protected abstract void loadTexture();
    protected abstract void applyLive();
    protected abstract void resetCurrent();
    protected abstract String getEditorTitle();

    protected Screen getBackScreen() { return null; }
    protected int addExtraButtons(int toolY) { return toolY; }
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {}
    protected boolean handleExtraClick(double mx, double my, int btn) { return false; }
    protected boolean handleExtraRelease(double mx, double my, int btn) { return false; }
    protected boolean handleExtraDrag(double mx, double my, int btn, double dx, double dy) { return false; }
    protected boolean handleExtraScroll(double mx, double my, double ha, double va) { return false; }
    //protected int getBackgroundColor() { return 0xFF1A1A2E; }
    protected int getBackgroundColor() { return 0xFF161626; }
    protected int getMaxZoom() { return 40; }
    protected int getMinZoom() { return 2; }
    protected int getZoomStep() { return 2; }
    protected String getResetCurrentLabel() { return "Reset"; }
    protected boolean usesTint() { return isTinted; }

    // --- Scale helpers ---

    /** Get the current GUI scale */
    protected int getGuiScale() {
        return (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    /** Left sidebar width — narrower at high scales */
    protected int getLeftSidebarWidth() {
        int scale = getGuiScale();
        if (scale >= 5) return 70;
        if (scale >= 4) return 85;
        return 110;
    }

    /** Right sidebar width — narrower at high scales */
    protected int getRightSidebarWidth() {
        int scale = getGuiScale();
        if (scale >= 5) return 80;
        if (scale >= 4) return 95;
        return 120;
    }

    /** Tool button width — narrower at high scales */
    protected int getToolButtonWidth() {
        int scale = getGuiScale();
        if (scale >= 5) return 65;
        if (scale >= 4) return 80;
        return 100;
    }

    /** Tool button height */
    protected int getToolButtonHeight() {
        int scale = getGuiScale();
        if (scale >= 4) return 18;
        return 20;
    }

    /** Grid + Zoom buttons: shown at scale 1-4, but hidden if scale >= 3 on FHD or smaller */
    protected boolean showExtraButtons() {
        int scale = getGuiScale();
        int height = MinecraftClient.getInstance().getWindow().getHeight();
        // On FHD (1080p) or smaller, hide these buttons at scale 3+ to save vertical space
        if (height <= 1080 && scale >= 3) return false;
        return scale <= 4;
    }

    /** Face button: shown at scale 1-4, hidden at 5+ */
    protected boolean showFaceButton() {
        return getGuiScale() <= 4;
    }

    /** Undo/Redo buttons: shown at scale 1-4, hidden at 5+ */
    private boolean showUndoRedo() {
        return getGuiScale() <= 4;
    }

    /** Color history: shown at scale 1-4, hidden at 5+ */
    private boolean showColorHistory() {
        int scale = getGuiScale();
        int height = MinecraftClient.getInstance().getWindow().getHeight();
        if (height <= 1080 && scale >= 3) return false;
        return scale <= 4;
    }

    // --- Tint helpers ---

    protected int applyTint(int pixel) {
        if (!isTinted) return pixel;
        int a = (pixel >> 24) & 0xFF;
        int r = (pixel >> 16) & 0xFF, g = (pixel >> 8) & 0xFF, b = pixel & 0xFF;
        int tr = (blockTint >> 16) & 0xFF, tg = (blockTint >> 8) & 0xFF, tb = blockTint & 0xFF;
        return (a << 24) | ((r * tr / 255) << 16) | ((g * tg / 255) << 8) | (b * tb / 255);
    }

    protected int removeTint(int color) {
        if (!isTinted) return color;
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int tr = (blockTint >> 16) & 0xFF, tg = (blockTint >> 8) & 0xFF, tb = blockTint & 0xFF;
        r = tr > 0 ? Math.min(255, r * 255 / tr) : r;
        g = tg > 0 ? Math.min(255, g * 255 / tg) : g;
        b = tb > 0 ? Math.min(255, b * 255 / tb) : b;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --- Init ---

    @Override
    public void removed() {
        super.removed();
        // Clean up cached canvas texture
        if (canvasTexture != null) {
            canvasTexture.close();
            canvasTexture = null;
        }
        // Clean up picker textures
        if (pickerSvTexture != null) {
            pickerSvTexture.close();
            pickerSvTexture = null;
        }
        if (pickerHueTexture != null) {
            pickerHueTexture.close();
            pickerHueTexture = null;
        }
    }

    @Override
    protected void init() {
        // Reset cached textures on re-init (screen resize, etc.)
        canvasTextureDirty = true;
        pickerHueBarBuilt = false;

        loadTexture();
        if (canvas == null) {
            canvas = new PixelCanvas(16, 16);
            originalPixels = new int[16][16];
        }

        int canvasPixelSize = Math.min(zoom, Math.min(
                (this.width - getLeftSidebarWidth() - getRightSidebarWidth() - 20) / canvas.getWidth(),
                (this.height - 80) / canvas.getHeight()
        ));
        if (canvasPixelSize < getMinZoom()) canvasPixelSize = getMinZoom();
        zoom = canvasPixelSize;

        int lsw = getLeftSidebarWidth();
        int rsw = getRightSidebarWidth();
        canvasBaseX = lsw + 10 + (this.width - lsw - rsw - 20 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;

        ModSettings settings = ModSettings.getInstance();
        currentTool = EditorTool.getToolByName(settings.defaultTool);
        ColorHistory.setMaxHistory(settings.colorHistorySize);
        showGrid = settings.gridOnByDefault;

        // Tool buttons
        int toolY = 30;
        int tbw = getToolButtonWidth();
        int tbh = getToolButtonHeight();
        for (EditorTool tool : EditorTool.values()) {
            final EditorTool t = tool;
            String keyHint = settings.showToolHints ? getToolKeyHint(tool) : "";
            String labelKey = "textureeditor.tool." + tool.name().toLowerCase();
            Text label = keyHint.isEmpty() ? Text.translatable(labelKey) : Text.translatable(labelKey).copy().append(" (" + keyHint + ")");
            addDrawableChild(ButtonWidget.builder(label, btn -> currentTool = t)
                    .position(5, toolY).size(tbw, tbh).build());
            toolY += tbh + 4;
        }

        toolY += 10;

        // Undo/Redo — only at scale <= 3
        if (showUndoRedo()) {
            String undoHint = settings.showToolHints ? settings.getKeyName("undo") : "";
            String redoHint = settings.showToolHints ? settings.getKeyName("redo") : "";
            Text undoText = undoHint.isEmpty() ? Text.translatable("textureeditor.button.undo") : Text.translatable("textureeditor.button.undo").copy().append(" (" + undoHint + ")");
            Text redoText = redoHint.isEmpty() ? Text.translatable("textureeditor.button.redo") : Text.translatable("textureeditor.button.redo").copy().append(" (" + redoHint + ")");
            int halfBtnW = (tbw - 4) / 2;
            addDrawableChild(ButtonWidget.builder(undoText, btn -> canvas.undo())
                    .position(5, toolY).size(halfBtnW, tbh).build());
            addDrawableChild(ButtonWidget.builder(redoText, btn -> canvas.redo())
                    .position(5 + halfBtnW + 4, toolY).size(halfBtnW, tbh).build());
            toolY += tbh + 4;
        }

        // Grid + Zoom — only at scale <= 3
        if (showExtraButtons()) {
            String gridHint = settings.showToolHints ? settings.getKeyName("grid") : "";
            Text gridText = gridHint.isEmpty() ? Text.translatable("textureeditor.button.grid") : Text.translatable("textureeditor.button.grid").copy().append(" (" + gridHint + ")");
            addDrawableChild(ButtonWidget.builder(gridText, btn -> showGrid = !showGrid)
                    .position(5, toolY).size(tbw, tbh).build());
            toolY += tbh + 4;
            int halfBtnW = (tbw - 4) / 2;
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.zoom_in"), btn -> {
                if (zoom < getMaxZoom()) { zoom += getZoomStep(); recalcCanvasPos(); }
            }).position(5, toolY).size(halfBtnW, tbh).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.zoom_out"), btn -> {
                if (zoom > getMinZoom()) { zoom -= getZoomStep(); recalcCanvasPos(); }
            }).position(5 + halfBtnW + 4, toolY).size(halfBtnW, tbh).build());
            toolY += tbh + 10;
        }

        // Tool size cycle button
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.size", toolSize), btn -> {
            toolSize = switch (toolSize) {
                case 1 -> 2;
                case 2 -> 3;
                case 3 -> 5;
                default -> 1;
            };
            btn.setMessage(Text.translatable("textureeditor.button.size", toolSize));
        }).position(5, toolY).size(tbw, tbh).build());
        toolY += tbh + 4;

        // Extra buttons from subclass (face selector, etc.)
        toolY = addExtraButtons(toolY);

        // Reset buttons (bottom-right)
        int resetBtnW = rsw - 10;
        int resetX = this.width - rsw + 5;
        int resetBaseY = this.height - 100;
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.reset"), btn -> resetCurrent())
                .position(resetX, resetBaseY).size(resetBtnW, tbh).build());
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.button.reset_all").styled(s -> s.withColor(0xFFFF4444)),
                btn -> doResetAll())
                .position(resetX, resetBaseY + tbh + 4).size(resetBtnW, tbh).build());

        // Bottom-left action buttons
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.button.apply_live").styled(s -> s.withColor(0xFF44FF44)),
                btn -> applyLive())
                .position(5, this.height - 78).size(tbw, tbh).build());
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.button.export_pack").styled(s -> s.withColor(0xFFFFCC44)),
                btn -> MinecraftClient.getInstance().setScreen(new ExportScreen(this)))
                .position(5, this.height - 54).size(tbw, tbh).build());
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.button.browse").styled(s -> s.withColor(0xFFFF66CC)),
                btn -> {
                    if (settings.autoApplyLive) this.applyLive();
                    Screen bs = getBackScreen();
                    if (bs != null) MinecraftClient.getInstance().setScreen(bs);
                    else MinecraftClient.getInstance().setScreen(new BrowseScreen());
                })
                .position(5, this.height - 30).size(tbw, tbh).build());

        // Top-right close
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.close"), btn -> {
            if (settings.autoApplyLive) this.applyLive();
            this.close();
        }).position(this.width - 65, 5).size(60, tbh).build());

        // Bottom-right: Picker, Layers
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.button.picker").styled(s -> s.withColor(0xFF00E6E6)),
                btn -> {
                    currentPanel = currentPanel == PanelType.COLOR_PANEL ? PanelType.NONE : PanelType.COLOR_PANEL;
                    setColor(currentColor, false);
                })
                .position(this.width - 65, this.height - 26).size(60, tbh).build());
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.button.layers").styled(s -> s.withColor(0xFFFFFF44)),
                btn -> {
                    currentPanel = currentPanel == PanelType.LAYER_PANEL ? PanelType.NONE : PanelType.LAYER_PANEL;
                })
                .position(this.width - 130, this.height - 26).size(60, tbh).build());

        // Hex Input
        int paletteEndY = getPaletteEndY() + 10;
        int hexW = Math.min(110, getRightSidebarWidth() - 30);
        hexInput = new TextFieldWidget(this.textRenderer, getPaletteX(), paletteEndY, hexW, 18, Text.literal("Hex"));
        hexInput.setMaxLength(9);
        hexInput.setText(String.format("#%06X", currentColor & 0xFFFFFF));
        hexInput.setChangedListener(text -> {
            try {
                String hex = text.startsWith("#") ? text.substring(1) : text;
                if (hex.length() == 6 || hex.length() == 8)
                    currentColor = (int) Long.parseLong(hex.length() == 6 ? "FF" + hex : hex, 16);
            } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(hexInput);
    }

    private String getToolKeyHint(EditorTool tool) {
        ModSettings s = ModSettings.getInstance();
        return switch (tool) {
            case PENCIL -> s.getKeyName("pencil");
            case BRUSH -> s.getKeyName("brush");
            case ERASER -> s.getKeyName("eraser");
            case FILL -> s.getKeyName("fill");
            case EYEDROPPER -> s.getKeyName("eyedropper");
            case LINE -> s.getKeyName("line");
        };
    }

    protected void recalcCanvasPos() {
        int lsw = getLeftSidebarWidth();
        int rsw = getRightSidebarWidth();
        canvasBaseX = lsw + 10 + (this.width - lsw - rsw - 20 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;
        canvasTextureDirty = true;
    }

    protected boolean isInUIRegion(double mx, double my) {
        return mx < getLeftSidebarWidth() || mx > this.width - getRightSidebarWidth() || my < 28;
    }

    protected void doResetAll() {
        TextureManager.getInstance().clear();
        MinecraftClient.getInstance().reloadResources();
        if (originalPixels != null) {
            canvas.saveSnapshot();
            for (int x = 0; x < canvas.getWidth(); x++)
                for (int y = 0; y < canvas.getHeight(); y++)
                    canvas.setPixel(x, y, originalPixels[x][y]);
        }
    }

    protected void setColor(int c, boolean addToHistory) {
        currentColor = c;
        if (addToHistory) ColorHistory.getInstance().addColor(c);
        if (currentPanel == PanelType.COLOR_PANEL) setColorPickerFromInt(c);
        if (hexInput != null) hexInput.setText(String.format("#%06X", c & 0xFFFFFF));
    }

    public void setColorPickerFromInt(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float[] hsv = new float[3];
        rgbToHsv(r, g, b, hsv);
        pickerHue = hsv[0];
        pickerSat = hsv[1];
        pickerVal = hsv[2];
    }

    private static void rgbToHsv(int r, int g, int b, float[] hsv) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, v = max;
        float d = max - min;
        s = max == 0 ? 0 : d / max;
        if (d == 0) h = 0;
        else if (max == rf) h = ((gf - bf) / d + (gf < bf ? 6 : 0)) / 6f;
        else if (max == gf) h = ((bf - rf) / d + 2) / 6f;
        else h = ((rf - gf) / d + 4) / 6f;
        hsv[0] = h; hsv[1] = s; hsv[2] = v;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int bg = getBackgroundColor();
        int lsw = getLeftSidebarWidth();
        int rsw = getRightSidebarWidth();
        context.fill(0, 0, this.width, this.height, bg);
        if (currentTool == EditorTool.LINE && lineFirstClick)
            context.drawText(textRenderer, "Click endpoint...", canvasScreenX, canvasScreenY - 12, 0xFFFFFF00, false);
        drawCanvas(context, mouseX, mouseY);
        context.fill(0, 0, lsw, this.height, bg);
        context.fill(this.width - rsw, 0, this.width, this.height, bg);
        context.fill(lsw, 0, this.width - rsw, 28, bg);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(textRenderer, getEditorTitle(), 5, 8, 0xFFFFFFFF, true);
        context.drawText(textRenderer, "Tool: " + currentTool.getDisplayName() +
                " | Size: " + toolSize + "px" +
                (canvas != null ? "  |  " + canvas.getWidth() + "x" + canvas.getHeight() : ""), 5, 20, 0xFFCCCCCC, false);

        drawPalette(context);
        int paletteX = getPaletteX();
        int paletteEndY = getPaletteEndY() + 10;
        // Current color swatch next to hex input
        int hexW = Math.min(110, getRightSidebarWidth() - 30);
        context.fill(paletteX + hexW + 4, paletteEndY, paletteX + hexW + 24, paletteEndY + 18, currentColor);
        drawRectOutline(context, paletteX + hexW + 4, paletteEndY, paletteX + hexW + 24, paletteEndY + 18, 0xFFFFFFFF);

        if (showColorHistory()) drawColorHistory(context);
        if (currentPanel == PanelType.COLOR_PANEL) {
            context.createNewRootLayer();
            drawColorPicker(context);
        }
        if (currentPanel == PanelType.LAYER_PANEL) {
            context.createNewRootLayer();
            drawLayerPanel(context);
        }

        // Render the quick select wheel if visible
        if (quickSelectWheel != null) {
            quickSelectWheel.render(context, textRenderer, mouseX, mouseY);
        }

        renderExtra(context, mouseX, mouseY);
    }

    // Cached canvas texture for performance on large textures
    private net.minecraft.client.texture.NativeImageBackedTexture canvasTexture = null;
    private static final Identifier CANVAS_TEX_ID = Identifier.of("textureeditor", "canvas_preview");
    private long lastCanvasHash = 0;
    private boolean canvasTextureDirty = true;

    private void drawCanvas(DrawContext ctx, int mx, int my) {
        if (canvas == null) return;
        try {
            drawCanvasInternal(ctx, mx, my);
        } catch (Throwable t) {
            System.out.println("[TextureEditor] ERROR in drawCanvas: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
    }

    private void drawCanvasInternal(DrawContext ctx, int mx, int my) {
        if (canvas == null) return;
        int w = canvas.getWidth(), h = canvas.getHeight();

        // Calculate visible pixel range (cull off-screen pixels for performance)
        int visMinX = Math.max(0, (-canvasScreenX) / zoom);
        int visMinY = Math.max(0, (-canvasScreenY) / zoom);
        int visMaxX = Math.min(w, (this.width - canvasScreenX + zoom - 1) / zoom);
        int visMaxY = Math.min(h, (this.height - canvasScreenY + zoom - 1) / zoom);

        int visW = visMaxX - visMinX;
        int visH = visMaxY - visMinY;

        // For large textures (>32x32), use cached texture rendering (1 draw call vs thousands of fill calls)
        // Always use cached texture for large textures regardless of zoom level to prevent watchdog crashes
        if (w * h > 1024) {
            // Check if canvas changed
            long hash = canvas.getVersion();
            if (hash != lastCanvasHash || canvasTextureDirty || canvasTexture == null) {
                lastCanvasHash = hash;
                canvasTextureDirty = false;

                // Build a 1:1 pixel image (NOT zoomed - let drawTexture handle scaling)
                if (w > 0 && h > 0) {
                    var canvasImage = new net.minecraft.client.texture.NativeImage(w, h, false);
                    for (int x = 0; x < w; x++) {
                        for (int y = 0; y < h; y++) {
                            int c;
                            if (previewingOriginal && originalPixels != null && x < originalPixels.length && y < originalPixels[x].length) {
                                c = originalPixels[x][y];
                            } else {
                                c = canvas.getPixel(x, y);
                            }
                            int alpha = (c >> 24) & 0xFF;
                            int display;
                            if (alpha == 0) {
                                display = ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0;
                            } else if (alpha < 255) {
                                int checker = ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0;
                                int fc = usesTint() ? applyTint(c) : c;
                                float a = alpha / 255f;
                                int cr = (int)(((fc >> 16) & 0xFF) * a + ((checker >> 16) & 0xFF) * (1 - a));
                                int cg = (int)(((fc >> 8) & 0xFF) * a + ((checker >> 8) & 0xFF) * (1 - a));
                                int cb = (int)((fc & 0xFF) * a + (checker & 0xFF) * (1 - a));
                                display = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
                            } else {
                                display = usesTint() ? applyTint(c) : c;
                            }
                            canvasImage.setColorArgb(x, y, display);
                        }
                    }
                    if (canvasTexture != null) {
                        var oldTex = canvasTexture.getGlTexture();
                        if (oldTex != null && (oldTex.getWidth(0) != w || oldTex.getHeight(0) != h)) {
                            canvasTexture.close();
                            canvasTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "canvas_preview", canvasImage);
                            MinecraftClient.getInstance().getTextureManager().registerTexture(CANVAS_TEX_ID, canvasTexture);
                        } else {
                            canvasTexture.setImage(canvasImage);
                            canvasTexture.upload();
                        }
                    } else {
                        canvasTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "canvas_preview", canvasImage);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(CANVAS_TEX_ID, canvasTexture);
                    }
                }
            }

            // Draw the canvas texture scaled by zoom
            // Use regionWidth=w, regionHeight=h to sample the entire texture once,
            // and width=w*zoom, height=h*zoom to scale it up on screen
            int drawX = canvasScreenX;
            int drawY = canvasScreenY;
            int drawW = w * zoom;
            int drawH = h * zoom;
            if (drawW > 0 && drawH > 0) {
                ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, CANVAS_TEX_ID,
                        drawX, drawY, 0, 0, drawW, drawH, w, h, w, h);
            }
        } else {
            // Small textures: use original fill-based rendering (fine for <=32x32)
            for (int x = visMinX; x < visMaxX; x++) for (int y = visMinY; y < visMaxY; y++) {
                int sx = canvasScreenX + x * zoom, sy = canvasScreenY + y * zoom;
                int c;
                if (previewingOriginal && originalPixels != null && x < originalPixels.length && y < originalPixels[x].length) {
                    c = originalPixels[x][y];
                } else {
                    c = canvas.getPixel(x, y);
                }
                int alpha = (c >> 24) & 0xFF;
                if (alpha < 255) {
                    ctx.fill(sx, sy, sx + zoom, sy + zoom, ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0);
                }
                if (alpha > 0) ctx.fill(sx, sy, sx + zoom, sy + zoom, usesTint() ? applyTint(c) : c);
            }
        }
        if (showGrid && zoom >= 4) {
            // Only draw grid lines in visible range
            for (int x = visMinX; x <= visMaxX; x++) ctx.fill(canvasScreenX + x * zoom, canvasScreenY + visMinY * zoom, canvasScreenX + x * zoom + 1, canvasScreenY + visMaxY * zoom, 0x40FFFFFF);
            for (int y = visMinY; y <= visMaxY; y++) ctx.fill(canvasScreenX + visMinX * zoom, canvasScreenY + y * zoom, canvasScreenX + visMaxX * zoom, canvasScreenY + y * zoom + 1, 0x40FFFFFF);
        }
        drawRectOutline(ctx, canvasScreenX - 1, canvasScreenY - 1, canvasScreenX + w * zoom + 1, canvasScreenY + h * zoom + 1, 0xFFFFFFFF);
        // Hover highlight with tool size
        int hx = (mx - canvasScreenX) / zoom, hy = (my - canvasScreenY) / zoom;
        if (hx >= 0 && hx < w && hy >= 0 && hy < h) {
            int half = toolSize / 2;
            for (int dx = -half; dx < toolSize - half; dx++) {
                for (int dy = -half; dy < toolSize - half; dy++) {
                    int tx = hx + dx, ty = hy + dy;
                    if (tx >= 0 && tx < w && ty >= 0 && ty < h) {
                        int sx = canvasScreenX + tx * zoom, sy = canvasScreenY + ty * zoom;
                        drawRectOutline(ctx, sx, sy, sx + zoom, sy + zoom, 0xFFFFFF00);
                    }
                }
            }
        }
        if (previewingOriginal) {
            ctx.drawCenteredTextWithShadow(textRenderer, "\u00a7e\u00a7lPREVIEW (Original)", canvasScreenX + (w * zoom) / 2, canvasScreenY - 14, 0xFFFFFF00);
        }
    }

    private void drawPalette(DrawContext ctx) {
        int cs = getPaletteCellSize();
        int cols = getPaletteColumns();
        int px0 = getPaletteX(), py0 = 30;
        for (int i = 0; i < PALETTE.length; i++) {
            int c = i % cols, r = i / cols;
            int px = px0 + c * (cs + 2), py = py0 + r * (cs + 2);
            ctx.fill(px, py, px + cs, py + cs, PALETTE[i]);
            if (PALETTE[i] == currentColor) drawRectOutline(ctx, px - 1, py - 1, px + cs + 1, py + cs + 1, 0xFFFFFF00);
            else drawRectOutline(ctx, px, py, px + cs, py + cs, 0xFF333333);
        }
    }

    private void drawColorHistory(DrawContext ctx) {
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() == 0) return;
        int px0 = getPaletteX();
        int sy = getPaletteEndY() + 50;
        ctx.drawText(textRenderer, Text.translatable("textureeditor.label.color_history").getString(), px0, sy, 0xFF999999, false);
        sy += 12;
        int cols = 5, cs = 18;
        List<Integer> colors = hist.getColors();
        for (int i = 0; i < colors.size(); i++) {
            int c = i % cols, r = i / cols;
            int px = px0 + c * (cs + 2), py = sy + r * (cs + 2);
            ctx.fill(px, py, px + cs, py + cs, colors.get(i));
            if (colors.get(i) == currentColor) drawRectOutline(ctx, px - 1, py - 1, px + cs + 1, py + cs + 1, 0xFFFFFF00);
            else drawRectOutline(ctx, px, py, px + cs, py + cs, 0xFF333333);
        }
    }

    private void drawColorPicker(DrawContext ctx) {
        int lsw = getLeftSidebarWidth();
        int rsw = getRightSidebarWidth();
        int canvasAreaCenter = lsw + (this.width - lsw - rsw) / 2;
        int cpX = canvasAreaCenter - 80, cpY = this.height - 90;
        int svW = PICKER_SV_W, svH = PICKER_SV_H;

        ctx.fill(cpX - 2, cpY - 14, cpX + 162, cpY + 82, 0xFF222244);
        drawRectOutline(ctx, cpX - 2, cpY - 14, cpX + 162, cpY + 82, 0xFFFFFFFF);
        ctx.drawText(textRenderer, Text.translatable("textureeditor.button.picker").getString(), cpX, cpY - 12, 0xFFFFFFFF, true);

        // Build/update SV gradient texture when hue changes
        if (cachedPickerHue != pickerHue || pickerSvTexture == null) {
            cachedPickerHue = pickerHue;
            var svImage = new net.minecraft.client.texture.NativeImage(svW, svH, false);
            for (int y = 0; y < svH; y++) {
                float v = 1f - y / (float) (svH - 1);
                for (int x = 0; x < svW; x++) {
                    float s = x / (float) (svW - 1);
                    svImage.setColorArgb(x, y, hsvToRgb(pickerHue, s, v));
                }
            }
            if (pickerSvTexture != null) {
                pickerSvTexture.setImage(svImage);
                pickerSvTexture.upload();
            } else {
                pickerSvTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "picker_sv", svImage);
                MinecraftClient.getInstance().getTextureManager().registerTexture(PICKER_SV_TEX_ID, pickerSvTexture);
                pickerSvTexture.upload();
            }
        }

        // Build hue bar texture once
        if (!pickerHueBarBuilt || pickerHueTexture == null) {
            int hueW = 20;
            var hueImage = new net.minecraft.client.texture.NativeImage(hueW, svH, false);
            for (int y = 0; y < svH; y++) {
                float h = y / (float) (svH - 1);
                int c = hsvToRgb(h, 1f, 1f);
                for (int x = 0; x < hueW; x++) {
                    hueImage.setColorArgb(x, y, c);
                }
            }
            if (pickerHueTexture != null) {
                pickerHueTexture.setImage(hueImage);
                pickerHueTexture.upload();
            } else {
                pickerHueTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "picker_hue", hueImage);
                MinecraftClient.getInstance().getTextureManager().registerTexture(PICKER_HUE_TEX_ID, pickerHueTexture);
                pickerHueTexture.upload();
            }
            pickerHueBarBuilt = true;
        }

        // Draw SV gradient as single texture (1 draw call instead of ~9600 fill calls!)
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PICKER_SV_TEX_ID, cpX, cpY, 0, 0, svW, svH, svW, svH);

        // Draw hue bar as single texture
        int hueX = cpX + svW + 5, hueW = 20;
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PICKER_HUE_TEX_ID, hueX, cpY, 0, 0, hueW, svH, hueW, svH);

        // Cursors
        int scx = cpX + (int) (pickerSat * (svW - 1)), scy = cpY + (int) ((1f - pickerVal) * (svH - 1));
        drawRectOutline(ctx, scx - 2, scy - 2, scx + 3, scy + 3, 0xFFFFFFFF);
        int hcy = cpY + (int) (pickerHue * (svH - 1));
        ctx.fill(hueX - 1, hcy - 1, hueX + hueW + 1, hcy + 2, 0xFFFFFFFF);
    }

    private void drawLayerPanel(DrawContext ctx) {
        if (canvas == null) return;
        var stack = canvas.getLayerStack();
        int panelW = 150, rowH = 20;
        int panelH = 30 + stack.getLayerCount() * rowH + 30;
        int panelX = this.width / 2 - panelW / 2;
        int panelY = this.height - panelH - 30;

        ctx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xEE222244);
        drawRectOutline(ctx, panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFFFFFFFF);
        ctx.drawText(textRenderer, Text.translatable("textureeditor.button.layers"), panelX + 4, panelY + 2, 0xFFFFFF00, true);
        int listY = panelY + 16;
        for (int i = stack.getLayerCount() - 1; i >= 0; i--) {
            var layer = stack.getLayers().get(i);
            int rowY = listY + (stack.getLayerCount() - 1 - i) * rowH;
            int bg = (i == stack.getActiveIndex()) ? 0xFF444488 : 0xFF333355;
            ctx.fill(panelX, rowY, panelX + panelW, rowY + rowH - 1, bg);
            String eye = layer.isVisible() ? "\u25CF" : "\u25CB";
            int eyeColor = layer.isVisible() ? 0xFF55FF55 : 0xFFFF5555;
            ctx.drawText(textRenderer, eye, panelX + 3, rowY + 5, eyeColor, true);
            String name = layer.getName();
            if (name.length() > 14) name = name.substring(0, 14) + "..";
            ctx.drawText(textRenderer, name, panelX + 16, rowY + 5, 0xFFDDDDDD, true);
            if (i == stack.getActiveIndex())
                ctx.drawText(textRenderer, "\u25B6", panelX + panelW - 12, rowY + 5, 0xFFFFFF00, true);
        }
        int btnY = listY + stack.getLayerCount() * rowH + 4;
        ctx.fill(panelX, btnY, panelX + 34, btnY + 18, 0xFF335533);
        ctx.drawText(textRenderer, "+ Add", panelX + 3, btnY + 4, 0xFFAAFFAA, true);
        ctx.fill(panelX + 38, btnY, panelX + 76, btnY + 18, 0xFF553333);
        ctx.drawText(textRenderer, "- Del", panelX + 41, btnY + 4, 0xFFFFAAAA, true);
        ctx.fill(panelX + 80, btnY, panelX + 110, btnY + 18, 0xFF333355);
        ctx.drawText(textRenderer, "\u25B2 Up", panelX + 83, btnY + 4, 0xFFAAAAFF, true);
        ctx.fill(panelX + 114, btnY, panelX + panelW, btnY + 18, 0xFF333355);
        ctx.drawText(textRenderer, "\u25BC Dn", panelX + 117, btnY + 4, 0xFFAAAAFF, true);
    }

    // --- Input handling ---

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (handleExtraScroll(mx, my, ha, va)) return true;
        int oldZoom = zoom;
        if (va > 0 && zoom < getMaxZoom()) zoom += getZoomStep();
        else if (va < 0 && zoom > getMinZoom()) zoom -= getZoomStep();
        if (zoom != oldZoom) {
            double rx = (mx - canvasScreenX) / (double) (canvas.getWidth() * oldZoom);
            double ry = (my - canvasScreenY) / (double) (canvas.getHeight() * oldZoom);
            recalcCanvasPos();
            canvasScreenX = (int) (mx - rx * canvas.getWidth() * zoom);
            canvasScreenY = (int) (my - ry * canvas.getHeight() * zoom);
            panOffsetX = canvasScreenX - canvasBaseX;
            panOffsetY = canvasScreenY - canvasBaseY;
            canvasTextureDirty = true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        double mx = click.x(); double my = click.y(); int btn = click.button();
        if (super.mouseClicked(click, bl)) return true;
        if (handleExtraClick(mx, my, btn)) return true;
        if (currentPanel == PanelType.COLOR_PANEL && btn == 0 && handlePickerClick(mx, my)) return true;
        if (currentPanel == PanelType.LAYER_PANEL && btn == 0 && handleLayerPanelClick(mx, my)) return true;
        if (btn == 0 && handleHistoryClick(mx, my)) return true;
        if (btn == 2) {
            quickSelectWheel.activate((int)mx, (int)my);
            return true;
        }
        if (btn == 1) {
            isPanning = true;
            panStartMouseX = mx; panStartMouseY = my;
            panStartOffsetX = panOffsetX; panStartOffsetY = panOffsetY;
            return true;
        }
        if (btn == 0 && handlePaletteClick(mx, my)) return true;
        if (isInUIRegion(mx, my)) return false;
        if (btn == 0 && canvas != null) {
            int px = (int) ((mx - canvasScreenX) / zoom), py = (int) ((my - canvasScreenY) / zoom);
            if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
                lastDrawX = px;
                lastDrawY = py;
                handleCanvasClick(px, py, btn);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mx = click.x(); double my = click.y(); int btn = click.button();
        if (handleExtraRelease(mx, my, btn)) return true;
        if (btn == 1) { isPanning = false; return true; }
        if (btn == 2) {
            QuickSelectWheel.Slice selected = quickSelectWheel.getSelectedSlice();
            if (selected != null && selected.toTool() != null) {
                currentTool = selected.toTool();
            }
            quickSelectWheel.deactivate();
            return true;
        }
        lastDrawX = -1;
        lastDrawY = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        double mx = click.x(); double my = click.y(); int btn = click.button();

        //Block input if mouse wheel visible
        if (quickSelectWheel.isVisible()) return true;

        if (btn == 1 && isPanning) {
            panOffsetX = panStartOffsetX + (int) (mx - panStartMouseX);
            panOffsetY = panStartOffsetY + (int) (my - panStartMouseY);
            canvasScreenX = canvasBaseX + panOffsetX;
            canvasScreenY = canvasBaseY + panOffsetY;
            canvasTextureDirty = true;
            return true;
        }
        if (handleExtraDrag(mx, my, btn, dx, dy)) return true;
        if (currentPanel == PanelType.COLOR_PANEL && btn == 0 && handlePickerClick(mx, my)) return true;
        if (isInUIRegion(mx, my)) return super.mouseDragged(click, dx, dy);
        if (canvas != null) {
            int px = (int) ((mx - canvasScreenX) / zoom), py = (int) ((my - canvasScreenY) / zoom);
            if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
                float variation = ModSettings.getInstance().brushVariation;
                // Interpolate between last and current position for continuous lines
                int startX = (lastDrawX >= 0) ? lastDrawX : px;
                int startY = (lastDrawY >= 0) ? lastDrawY : py;
                java.util.List<int[]> points = bresenhamLine(startX, startY, px, py);
                for (int[] pt : points) {
                    int ix = pt[0], iy = pt[1];
                    if (ix < 0 || ix >= canvas.getWidth() || iy < 0 || iy >= canvas.getHeight()) continue;
                    if (currentTool == EditorTool.PENCIL) {
                        if (toolSize > 1) canvas.drawPixelArea(ix, iy, toolSize, currentColor);
                        else canvas.drawPixel(ix, iy, currentColor);
                    } else if (currentTool == EditorTool.BRUSH) {
                        if (toolSize > 1) canvas.drawBrushArea(ix, iy, toolSize, currentColor, variation);
                        else canvas.drawBrushPixel(ix, iy, currentColor, variation);
                    } else if (currentTool == EditorTool.ERASER) {
                        if (toolSize > 1) canvas.erasePixelArea(ix, iy, toolSize);
                        else canvas.erasePixel(ix, iy);
                    }
                }
                lastDrawX = px;
                lastDrawY = py;
                return true;
            }
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        int kc = keyInput.key(); int sc = keyInput.scancode(); int m = keyInput.modifiers();
        if (hexInput != null && hexInput.isFocused()) return super.keyPressed(keyInput);
        // Preview original texture (hold key)
        var previewKey = com.zeeesea.textureeditor.TextureEditorClient.getPreviewOriginalKey();
        if (previewKey != null && previewKey.matchesKey(keyInput)) {
            previewingOriginal = true;
            return true;
        }
        ModSettings s = ModSettings.getInstance();
        if (kc == s.getKeybind("undo")) { canvas.undo(); return true; }
        if (kc == s.getKeybind("redo")) { canvas.redo(); return true; }
        if (kc == s.getKeybind("grid")) { showGrid = !showGrid; return true; }
        if (kc == s.getKeybind("pencil")) { currentTool = EditorTool.PENCIL; return true; }
        if (kc == s.getKeybind("eraser")) { currentTool = EditorTool.ERASER; return true; }
        if (kc == s.getKeybind("fill")) { currentTool = EditorTool.FILL; return true; }
        if (kc == s.getKeybind("eyedropper")) { currentTool = EditorTool.EYEDROPPER; return true; }
        if (kc == s.getKeybind("line")) { currentTool = EditorTool.LINE; return true; }
        if (kc == s.getKeybind("brush")) { currentTool = EditorTool.BRUSH; return true; }
        if (kc == s.getKeybind("browse")) {
            if (s.autoApplyLive) this.applyLive();
            Screen bs = getBackScreen();
            if (bs != null) MinecraftClient.getInstance().setScreen(bs);
            else MinecraftClient.getInstance().setScreen(new BrowseScreen());
        }
        var openKey = com.zeeesea.textureeditor.TextureEditorClient.getOpenEditorKey();
        if (openKey != null && openKey.matchesKey(keyInput)) {
            if (s.autoApplyLive) this.applyLive();
            this.close(); return true;
        }
        if (kc == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (s.autoApplyLive) this.applyLive();
            this.close(); return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyInput keyInput) {
        int kc = keyInput.key();
        ModSettings s = ModSettings.getInstance();

        var previewKey = com.zeeesea.textureeditor.TextureEditorClient.getPreviewOriginalKey();
        if (previewKey != null && previewKey.matchesKey(keyInput)) {
            previewingOriginal = false;
            return true;
        }
        return super.keyReleased(keyInput);
    }

    // --- Click handlers ---

    private boolean handlePickerClick(double mx, double my) {
        int lsw = getLeftSidebarWidth();
        int rsw = getRightSidebarWidth();
        int canvasAreaCenter = lsw + (this.width - lsw - rsw) / 2;
        int cpX = canvasAreaCenter - 80, cpY = this.height - 90, svW = 120, svH = 80, hueX = cpX + svW + 5, hueW = 20;
        if (mx >= cpX && mx < cpX + svW && my >= cpY && my < cpY + svH) {
            pickerSat = Math.max(0, Math.min(1, (float) (mx - cpX) / (svW - 1)));
            pickerVal = Math.max(0, Math.min(1, 1f - (float) (my - cpY) / (svH - 1)));
            setColor(hsvToRgb(pickerHue, pickerSat, pickerVal), false);
            return true;
        }
        if (mx >= hueX && mx < hueX + hueW && my >= cpY && my < cpY + svH) {
            pickerHue = Math.max(0, Math.min(1, (float) (my - cpY) / (svH - 1)));
            setColor(hsvToRgb(pickerHue, pickerSat, pickerVal), false);
            return true;
        }
        return false;
    }

    private boolean handleHistoryClick(double mx, double my) {
        if (!showColorHistory()) return false;
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() == 0) return false;
        int px0 = getPaletteX();
        int sy = getPaletteEndY() + 50 + 12;
        int cols = 5, cs = 18;
        List<Integer> colors = hist.getColors();
        for (int i = 0; i < colors.size(); i++) {
            int c = i % cols, r = i / cols;
            int px = px0 + c * (cs + 2), py = sy + r * (cs + 2);
            if (mx >= px && mx < px + cs && my >= py && my < py + cs) {
                currentColor = colors.get(i);
                if (hexInput != null) hexInput.setText(String.format("#%06X", currentColor & 0xFFFFFF));
                return true;
            }

        }
        return false;
    }

    private boolean handleLayerPanelClick(double mx, double my) {
        if (!(currentPanel == PanelType.LAYER_PANEL) || canvas == null) return false;
        var stack = canvas.getLayerStack();
        int panelW = 150, rowH = 20;
        int panelH = 30 + stack.getLayerCount() * rowH + 30;
        int panelX = this.width / 2 - panelW / 2;
        int panelY = this.height - panelH - 30;
        if (mx < panelX - 2 || mx > panelX + panelW + 2 || my < panelY - 2 || my > panelY + panelH + 2) return false;
        int listY = panelY + 16;
        for (int i = stack.getLayerCount() - 1; i >= 0; i--) {
            int rowY = listY + (stack.getLayerCount() - 1 - i) * rowH;
            if (my >= rowY && my < rowY + rowH - 1 && mx >= panelX && mx < panelX + panelW) {
                if (mx < panelX + 14) { stack.getLayers().get(i).setVisible(!stack.getLayers().get(i).isVisible()); canvas.invalidateCache(); }
                else stack.setActiveIndex(i);
                return true;
            }
        }
        int btnY = listY + stack.getLayerCount() * rowH + 4;
        if (my >= btnY && my < btnY + 18) {
            if (mx >= panelX && mx < panelX + 34) { stack.addLayerAbove( Text.translatable("textureeditor.button.layer").getString() + " " + (stack.getLayerCount() - 1)); canvas.invalidateCache(); return true; }
            if (mx >= panelX + 38 && mx < panelX + 76) { stack.removeLayer(stack.getActiveIndex()); canvas.invalidateCache(); return true; }
            if (mx >= panelX + 80 && mx < panelX + 110) { int idx = stack.getActiveIndex(); if (idx < stack.getLayerCount() - 1) { stack.moveLayerDown(idx); canvas.invalidateCache(); } return true; }
            if (mx >= panelX + 114 && mx < panelX + panelW) { int idx = stack.getActiveIndex(); if (idx > 0) { stack.moveLayerUp(idx); canvas.invalidateCache(); } return true; }
        }
        return true;
    }

    protected boolean handlePaletteClick(double mx, double my) {
        int cs = getPaletteCellSize();
        int cols = getPaletteColumns();
        int px0 = getPaletteX(), py0 = 30;
        for (int i = 0; i < PALETTE.length; i++) {
            int c = i % cols, r = i / cols;
            int px = px0 + c * (cs + 2), py = py0 + r * (cs + 2);
            if (mx >= px && mx < px + cs && my >= py && my < py + cs) {
                setColor(PALETTE[i], false);
                return true;
            }
        }
        return false;
    }

    protected void handleCanvasClick(int px, int py, int btn) {
        int storeColor = currentColor;
        float variation = ModSettings.getInstance().brushVariation;
        switch (currentTool) {
            case PENCIL -> {
                canvas.saveSnapshot();
                if (toolSize > 1) canvas.drawPixelArea(px, py, toolSize, btn == 1 ? 0 : storeColor);
                else canvas.drawPixel(px, py, btn == 1 ? 0 : storeColor);
                setColor(currentColor, true);
            }
            case BRUSH -> {
                canvas.saveSnapshot();
                if (toolSize > 1) canvas.drawBrushArea(px, py, toolSize, storeColor, variation);
                else canvas.drawBrushPixel(px, py, storeColor, variation);
                setColor(currentColor, true);
            }
            case ERASER -> {
                canvas.saveSnapshot();
                if (toolSize > 1) canvas.erasePixelArea(px, py, toolSize);
                else canvas.erasePixel(px, py);
            }
            case FILL -> { canvas.saveSnapshot(); canvas.floodFill(px, py, storeColor); setColor(currentColor, true); }
            case EYEDROPPER -> {
                int raw = canvas.pickColorComposited(px, py);
                if (raw == 0x00000000) {
                    NotificationHelper.addToast(SystemToast.Type.PACK_LOAD_FAILURE, "Layer is completely empty!");
                    return;
                }
                currentColor = usesTint() ? applyTint(raw) : raw;
                setColor(currentColor, false);
                if (hexInput != null) hexInput.setText(String.format("#%08X", currentColor));
            }
            case LINE -> {
                if (!lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; }
                else { canvas.saveSnapshot(); canvas.drawLine(lineStartX, lineStartY, px, py, storeColor); lineFirstClick = false; setColor(currentColor, true); }
            }
        }
    }

    // --- Utilities ---

    protected void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        ctx.fill(x1, y1, x2, y1 + 1, c);
        ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c);
        ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    /** Bresenham line algorithm — returns list of [x,y] points between (x0,y0) and (x1,y1) */
    private static java.util.List<int[]> bresenhamLine(int x0, int y0, int x1, int y1) {
        java.util.List<int[]> points = new java.util.ArrayList<>();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            points.add(new int[]{x0, y0});
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
        return points;
    }

    protected static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        int p = (int) (255 * v * (1 - s)), q = (int) (255 * v * (1 - f * s)), t = (int) (255 * v * (1 - (1 - f) * s)), vi = (int) (255 * v);
        return switch (i) {
            case 0 -> 0xFF000000 | (vi << 16) | (t << 8) | p;
            case 1 -> 0xFF000000 | (q << 16) | (vi << 8) | p;
            case 2 -> 0xFF000000 | (p << 16) | (vi << 8) | t;
            case 3 -> 0xFF000000 | (p << 16) | (q << 8) | vi;
            case 4 -> 0xFF000000 | (t << 16) | (p << 8) | vi;
            default -> 0xFF000000 | (vi << 16) | (p << 8) | q;
        };
    }

    protected static int[][] copyPixels(int[][] src, int w, int h) {
        int[][] copy = new int[w][h];
        for (int x = 0; x < w; x++) System.arraycopy(src[x], 0, copy[x], 0, h);
        return copy;
    }

    private int getPaletteX() {
        return this.width - getRightSidebarWidth() + 5;
    }

    private int getPaletteColumns() {
        int scale = getGuiScale();
        if (scale >= 5) return 6;
        if (scale >= 4) return 5;
        return 5;
    }

    private int getPaletteCellSize() {
        int scale = getGuiScale();
        if (scale >= 5) return 10;
        if (scale >= 4) return 14;
        return 20;
    }

    public void setTint(int tint) {
        this.blockTint = tint | 0xFF000000;
        this.isTinted = true;
    }

    private int getPaletteEndY() {
        int cs = getPaletteCellSize();
        int cols = getPaletteColumns();
        return 30 + ((PALETTE.length + cols - 1) / cols) * (cs + 2);
    }

    @Override
    public boolean shouldPause() { return false; }
}
