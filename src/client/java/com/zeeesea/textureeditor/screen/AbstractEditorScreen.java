package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.ColorHistory;
import com.zeeesea.textureeditor.editor.EditorTool;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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

    // UI toggles
    protected boolean showColorPicker = false;
    protected boolean showLayerPanel = false;
    private float pickerHue = 0f, pickerSat = 1f, pickerVal = 1f;

    protected static final int[] PALETTE = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF,
            0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF80FF00, 0xFF00FF00,
            0xFF00FF80, 0xFF00FFFF, 0xFF0080FF, 0xFF0000FF, 0xFF8000FF,
            0xFFFF00FF, 0xFFFF0080, 0xFF800000, 0xFF804000, 0xFF808000,
            0xFF408000, 0xFF008000, 0xFF008040, 0xFF008080, 0xFF004080,
            0xFF000080, 0xFF400080, 0xFF800080, 0xFF800040, 0xFF663300,
            0xFFCC6600, 0xFFFFCC99, 0xFF336633, 0xFF66CCCC, 0xFF6666CC,
    };

    protected TextFieldWidget hexInput;

    protected AbstractEditorScreen(Text title) {
        super(title);
    }

    // --- Abstract methods for subclasses ---

    /** Load texture data, set canvas, originalPixels, textureId, spriteId. */
    protected abstract void loadTexture();

    /** Apply the current canvas to the game live. */
    protected abstract void applyLive();

    /** Reset the current texture (face/item/mob). */
    protected abstract void resetCurrent();

    /** Get the title string shown at the top. */
    protected abstract String getEditorTitle();

    /** Get the screen to go back to (null = close). */
    protected Screen getBackScreen() { return null; }

    /** Override to add extra buttons (face selectors, 3D preview, etc). Returns next toolY. */
    protected int addExtraButtons(int toolY) { return toolY; }

    /** Override to add extra rendering after standard UI. */
    protected void renderExtra(DrawContext context, int mouseX, int mouseY) {}

    /** Override for extra mouseClicked handling (before canvas). Return true if consumed. */
    protected boolean handleExtraClick(double mx, double my, int btn) { return false; }

    /** Override for extra mouseReleased handling. */
    protected boolean handleExtraRelease(double mx, double my, int btn) { return false; }

    /** Override for extra mouseDragged handling. */
    protected boolean handleExtraDrag(double mx, double my, int btn, double dx, double dy) { return false; }

    /** Override for extra mouseScrolled handling. */
    protected boolean handleExtraScroll(double mx, double my, double ha, double va) { return false; }

    /** Background color. Default dark blue. */
    protected int getBackgroundColor() { return 0xFF1A1A2E; }

    /** Max/min zoom and step. */
    protected int getMaxZoom() { return 40; }
    protected int getMinZoom() { return 2; }
    protected int getZoomStep() { return 2; }

    /** Whether to show reset button labels. Override for custom. */
    protected String getResetCurrentLabel() { return "Reset"; }

    /** Whether this editor uses tint. */
    protected boolean usesTint() { return isTinted; }

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
    protected void init() {
        loadTexture();
        if (canvas == null) {
            canvas = new PixelCanvas(16, 16);
            originalPixels = new int[16][16];
        }

        int canvasPixelSize = Math.min(zoom, Math.min(
                (this.width - 200) / canvas.getWidth(),
                (this.height - 80) / canvas.getHeight()
        ));
        if (canvasPixelSize < getMinZoom()) canvasPixelSize = getMinZoom();
        zoom = canvasPixelSize;

        canvasBaseX = 120 + (this.width - 240 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;

        ModSettings settings = ModSettings.getInstance();

        // Tool buttons with keybind hints
        int toolY = 30;
        for (EditorTool tool : EditorTool.values()) {
            final EditorTool t = tool;
            String keyHint = getToolKeyHint(tool);
            String label = keyHint.isEmpty() ? tool.getDisplayName() : tool.getDisplayName() + " (" + keyHint + ")";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> currentTool = t)
                    .position(5, toolY).size(100, 20).build());
            toolY += 24;
        }

        // Undo/Redo
        toolY += 10;
        String undoHint = settings.getKeyName("undo");
        String redoHint = settings.getKeyName("redo");
        addDrawableChild(ButtonWidget.builder(Text.literal("Undo (" + undoHint + ")"), btn -> canvas.undo())
                .position(5, toolY).size(100, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Redo (" + redoHint + ")"), btn -> canvas.redo())
                .position(5, toolY).size(100, 20).build());

        // Grid
        toolY += 34;
        String gridHint = settings.getKeyName("grid");
        addDrawableChild(ButtonWidget.builder(Text.literal("Grid (" + gridHint + ")"), btn -> showGrid = !showGrid)
                .position(5, toolY).size(100, 20).build());

        // Zoom
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom +"), btn -> {
            if (zoom < getMaxZoom()) { zoom += getZoomStep(); recalcCanvasPos(); }
        }).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom -"), btn -> {
            if (zoom > getMinZoom()) { zoom -= getZoomStep(); recalcCanvasPos(); }
        }).position(57, toolY).size(48, 20).build());

        // Extra buttons from subclass (face selectors, etc)
        toolY += 30;
        toolY = addExtraButtons(toolY);

        // Reset buttons (bottom-right)
        int resetX = this.width - 115;
        int resetBaseY = this.height - 100;
        addDrawableChild(ButtonWidget.builder(Text.literal(getResetCurrentLabel()), btn -> resetCurrent())
                .position(resetX, resetBaseY).size(110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cReset All"), btn -> doResetAll())
                .position(resetX, resetBaseY + 24).size(110, 20).build());

        // Bottom-left action buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7aApply Live"), btn -> applyLive())
                .position(5, this.height - 78).size(100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a76Export Pack"), btn ->
                        MinecraftClient.getInstance().setScreen(new ExportScreen(this)))
                .position(5, this.height - 54).size(100, 20).build());

        Screen back = getBackScreen();
        addDrawableChild(ButtonWidget.builder(Text.literal(back != null ? "\u00a7dBrowse" : "\u00a7dMenu"), btn -> {
            Screen bs = getBackScreen();
            if (bs != null) MinecraftClient.getInstance().setScreen(bs);
            else MinecraftClient.getInstance().setScreen(new BrowseScreen());
        }).position(5, this.height - 30).size(100, 20).build());

        // Top-right close
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cClose"), btn -> this.close())
                .position(this.width - 65, 5).size(60, 20).build());

        // Bottom-right row: Picker, Layers (and optionally 3D from subclass)
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7bPicker"), btn -> showColorPicker = !showColorPicker)
                .position(this.width - 195, this.height - 26).size(60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7eLayers"), btn -> showLayerPanel = !showLayerPanel)
                .position(this.width - 130, this.height - 26).size(60, 20).build());

        // Hex input
        int paletteX = this.width - 115;
        int paletteEndY = 30 + ((PALETTE.length + 4) / 5) * 22 + 10;
        hexInput = new TextFieldWidget(this.textRenderer, paletteX, paletteEndY, 110, 18, Text.literal("Hex"));
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
            case ERASER -> s.getKeyName("eraser");
            case FILL -> s.getKeyName("fill");
            case EYEDROPPER -> s.getKeyName("eyedropper");
            case LINE -> s.getKeyName("line");
        };
    }

    protected void recalcCanvasPos() {
        canvasBaseX = 120 + (this.width - 240 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;
    }

    protected boolean isInUIRegion(double mx, double my) {
        return mx < 110 || mx > this.width - 120 || my < 28;
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

    protected void setColor(int c) {
        currentColor = c;
        ColorHistory.getInstance().addColor(c);
        if (hexInput != null) hexInput.setText(String.format("#%06X", c & 0xFFFFFF));
    }

    // --- Rendering ---

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int bg = getBackgroundColor();
        context.fill(0, 0, this.width, this.height, bg);
        drawCanvas(context, mouseX, mouseY);
        context.fill(0, 0, 110, this.height, bg);
        context.fill(this.width - 120, 0, this.width, this.height, bg);
        context.fill(110, 0, this.width - 120, 28, bg);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(textRenderer, getEditorTitle(), 120, 8, 0xFFFFFF, true);
        context.drawText(textRenderer, "Tool: " + currentTool.getDisplayName() +
                (canvas != null ? "  |  " + canvas.getWidth() + "x" + canvas.getHeight() : ""), 120, 20, 0xCCCCCC, false);

        drawPalette(context);
        int paletteX = this.width - 115;
        int paletteEndY = 30 + ((PALETTE.length + 4) / 5) * 22 + 35;
        context.drawText(textRenderer, "Current Color:", paletteX, paletteEndY, 0xCCCCCC, false);
        context.fill(paletteX, paletteEndY + 12, paletteX + 30, paletteEndY + 32, currentColor);
        drawRectOutline(context, paletteX, paletteEndY + 12, paletteX + 30, paletteEndY + 32, 0xFFFFFFFF);
        drawColorHistory(context);
        if (showColorPicker) drawColorPicker(context);
        if (showLayerPanel) drawLayerPanel(context);
        if (currentTool == EditorTool.LINE && lineFirstClick)
            context.drawText(textRenderer, "Click endpoint...", canvasScreenX, canvasScreenY - 12, 0xFFFF00, false);

        renderExtra(context, mouseX, mouseY);
    }

    private void drawCanvas(DrawContext ctx, int mx, int my) {
        if (canvas == null) return;
        int w = canvas.getWidth(), h = canvas.getHeight();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            int sx = canvasScreenX + x * zoom, sy = canvasScreenY + y * zoom;
            ctx.fill(sx, sy, sx + zoom, sy + zoom, ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0);
            int c = canvas.getPixel(x, y);
            if (((c >> 24) & 0xFF) > 0) ctx.fill(sx, sy, sx + zoom, sy + zoom, usesTint() ? applyTint(c) : c);
        }
        if (showGrid && zoom >= 4) {
            for (int x = 0; x <= w; x++) ctx.fill(canvasScreenX + x * zoom, canvasScreenY, canvasScreenX + x * zoom + 1, canvasScreenY + h * zoom, 0x40FFFFFF);
            for (int y = 0; y <= h; y++) ctx.fill(canvasScreenX, canvasScreenY + y * zoom, canvasScreenX + w * zoom, canvasScreenY + y * zoom + 1, 0x40FFFFFF);
        }
        drawRectOutline(ctx, canvasScreenX - 1, canvasScreenY - 1, canvasScreenX + w * zoom + 1, canvasScreenY + h * zoom + 1, 0xFFFFFFFF);
        int hx = (mx - canvasScreenX) / zoom, hy = (my - canvasScreenY) / zoom;
        if (hx >= 0 && hx < w && hy >= 0 && hy < h) {
            int sx = canvasScreenX + hx * zoom, sy = canvasScreenY + hy * zoom;
            drawRectOutline(ctx, sx, sy, sx + zoom, sy + zoom, 0xFFFFFF00);
        }
    }

    private void drawPalette(DrawContext ctx) {
        int px0 = this.width - 115, py0 = 30, cs = 20, cols = 5;
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
        int px0 = this.width - 115;
        int sy = 30 + ((PALETTE.length + 4) / 5) * 22 + 80;
        ctx.drawText(textRenderer, "History:", px0, sy, 0x999999, false);
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
        int cpX = this.width / 2 - 80, cpY = this.height - 90, svW = 120, svH = 80;
        ctx.fill(cpX - 2, cpY - 14, cpX + 162, cpY + 82, 0xFF222244);
        drawRectOutline(ctx, cpX - 2, cpY - 14, cpX + 162, cpY + 82, 0xFFFFFFFF);
        ctx.drawText(textRenderer, "Color Picker", cpX, cpY - 12, 0xFFFFFF, false);
        for (int x = 0; x < svW; x++) for (int y = 0; y < svH; y++) {
            float s = x / (float) (svW - 1), v = 1f - y / (float) (svH - 1);
            ctx.fill(cpX + x, cpY + y, cpX + x + 1, cpY + y + 1, hsvToRgb(pickerHue, s, v));
        }
        int scx = cpX + (int) (pickerSat * (svW - 1)), scy = cpY + (int) ((1f - pickerVal) * (svH - 1));
        drawRectOutline(ctx, scx - 2, scy - 2, scx + 3, scy + 3, 0xFFFFFFFF);
        int hueX = cpX + svW + 5, hueW = 20;
        for (int y = 0; y < svH; y++) {
            float h = y / (float) (svH - 1);
            ctx.fill(hueX, cpY + y, hueX + hueW, cpY + y + 1, hsvToRgb(h, 1f, 1f));
        }
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
        ctx.drawText(textRenderer, "\u00a7eLayers", panelX + 4, panelY + 2, 0xFFFFFF, false);
        int listY = panelY + 16;
        for (int i = stack.getLayerCount() - 1; i >= 0; i--) {
            var layer = stack.getLayers().get(i);
            int rowY = listY + (stack.getLayerCount() - 1 - i) * rowH;
            int bg = (i == stack.getActiveIndex()) ? 0xFF444488 : 0xFF333355;
            ctx.fill(panelX, rowY, panelX + panelW, rowY + rowH - 1, bg);
            String eye = layer.isVisible() ? "\u00a7a\u25CF" : "\u00a7c\u25CB";
            ctx.drawText(textRenderer, eye, panelX + 3, rowY + 5, 0xFFFFFF, false);
            String name = layer.getName();
            if (name.length() > 14) name = name.substring(0, 14) + "..";
            ctx.drawText(textRenderer, name, panelX + 16, rowY + 5, 0xDDDDDD, false);
            if (i == stack.getActiveIndex())
                ctx.drawText(textRenderer, "\u25B6", panelX + panelW - 12, rowY + 5, 0xFFFF00, false);
        }
        int btnY = listY + stack.getLayerCount() * rowH + 4;
        ctx.fill(panelX, btnY, panelX + 34, btnY + 18, 0xFF335533);
        ctx.drawText(textRenderer, "+ Add", panelX + 3, btnY + 4, 0xAAFFAA, false);
        ctx.fill(panelX + 38, btnY, panelX + 76, btnY + 18, 0xFF553333);
        ctx.drawText(textRenderer, "- Del", panelX + 41, btnY + 4, 0xFFAAAA, false);
        ctx.fill(panelX + 80, btnY, panelX + 110, btnY + 18, 0xFF333355);
        ctx.drawText(textRenderer, "\u25B2 Up", panelX + 83, btnY + 4, 0xAAAAFF, false);
        ctx.fill(panelX + 114, btnY, panelX + panelW, btnY + 18, 0xFF333355);
        ctx.drawText(textRenderer, "\u25BC Dn", panelX + 117, btnY + 4, 0xAAAAFF, false);
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
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (handleExtraClick(mx, my, btn)) return true;
        if (showColorPicker && btn == 0 && handlePickerClick(mx, my)) return true;
        if (showLayerPanel && btn == 0 && handleLayerPanelClick(mx, my)) return true;
        if (btn == 0 && handleHistoryClick(mx, my)) return true;
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
                handleCanvasClick(px, py, btn);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (handleExtraRelease(mx, my, btn)) return true;
        if (btn == 1) { isPanning = false; return true; }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 1 && isPanning) {
            panOffsetX = panStartOffsetX + (int) (mx - panStartMouseX);
            panOffsetY = panStartOffsetY + (int) (my - panStartMouseY);
            canvasScreenX = canvasBaseX + panOffsetX;
            canvasScreenY = canvasBaseY + panOffsetY;
            return true;
        }
        if (handleExtraDrag(mx, my, btn, dx, dy)) return true;
        if (showColorPicker && btn == 0 && handlePickerClick(mx, my)) return true;
        if (isInUIRegion(mx, my)) return super.mouseDragged(mx, my, btn, dx, dy);
        if (canvas != null) {
            int px = (int) ((mx - canvasScreenX) / zoom), py = (int) ((my - canvasScreenY) / zoom);
            if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
                int col = usesTint() ? removeTint(currentColor) : currentColor;
                if (currentTool == EditorTool.PENCIL) canvas.drawPixel(px, py, col);
                else if (currentTool == EditorTool.ERASER) canvas.erasePixel(px, py);
                return true;
            }
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int m) {
        if (hexInput != null && hexInput.isFocused()) return super.keyPressed(kc, sc, m);

        ModSettings s = ModSettings.getInstance();
        if (kc == s.getKeybind("undo")) { canvas.undo(); return true; }
        if (kc == s.getKeybind("redo")) { canvas.redo(); return true; }
        if (kc == s.getKeybind("grid")) { showGrid = !showGrid; return true; }
        if (kc == s.getKeybind("pencil")) { currentTool = EditorTool.PENCIL; return true; }
        if (kc == s.getKeybind("eraser")) { currentTool = EditorTool.ERASER; return true; }
        if (kc == s.getKeybind("fill")) { currentTool = EditorTool.FILL; return true; }
        if (kc == s.getKeybind("eyedropper")) { currentTool = EditorTool.EYEDROPPER; return true; }
        if (kc == s.getKeybind("line")) { currentTool = EditorTool.LINE; return true; }

        // R key (openEditor) closes this screen too
        var openKey = com.zeeesea.textureeditor.TextureEditorClient.getOpenEditorKey();
        if (openKey != null && openKey.matchesKey(kc, sc)) { this.close(); return true; }

        if (kc == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(kc, sc, m);
    }

    // --- Click handlers ---

    private boolean handlePickerClick(double mx, double my) {
        int cpX = this.width / 2 - 80, cpY = this.height - 90, svW = 120, svH = 80, hueX = cpX + svW + 5, hueW = 20;
        if (mx >= cpX && mx < cpX + svW && my >= cpY && my < cpY + svH) {
            pickerSat = Math.max(0, Math.min(1, (float) (mx - cpX) / (svW - 1)));
            pickerVal = Math.max(0, Math.min(1, 1f - (float) (my - cpY) / (svH - 1)));
            setColor(hsvToRgb(pickerHue, pickerSat, pickerVal));
            return true;
        }
        if (mx >= hueX && mx < hueX + hueW && my >= cpY && my < cpY + svH) {
            pickerHue = Math.max(0, Math.min(1, (float) (my - cpY) / (svH - 1)));
            setColor(hsvToRgb(pickerHue, pickerSat, pickerVal));
            return true;
        }
        return false;
    }

    private boolean handleHistoryClick(double mx, double my) {
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() == 0) return false;
        int px0 = this.width - 115;
        int sy = 30 + ((PALETTE.length + 4) / 5) * 22 + 92;
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
        if (!showLayerPanel || canvas == null) return false;
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
            if (mx >= panelX && mx < panelX + 34) { stack.addLayerAbove("Layer " + stack.getLayerCount()); canvas.invalidateCache(); return true; }
            if (mx >= panelX + 38 && mx < panelX + 76) { stack.removeLayer(stack.getActiveIndex()); canvas.invalidateCache(); return true; }
            if (mx >= panelX + 80 && mx < panelX + 110) { int idx = stack.getActiveIndex(); if (idx < stack.getLayerCount() - 1) { stack.moveLayerDown(idx); canvas.invalidateCache(); } return true; }
            if (mx >= panelX + 114 && mx < panelX + panelW) { int idx = stack.getActiveIndex(); if (idx > 0) { stack.moveLayerUp(idx); canvas.invalidateCache(); } return true; }
        }
        return true;
    }

    protected boolean handlePaletteClick(double mx, double my) {
        int px0 = this.width - 115, py0 = 30, cs = 20, cols = 5;
        for (int i = 0; i < PALETTE.length; i++) {
            int c = i % cols, r = i / cols;
            int px = px0 + c * (cs + 2), py = py0 + r * (cs + 2);
            if (mx >= px && mx < px + cs && my >= py && my < py + cs) { setColor(PALETTE[i]); return true; }
        }
        return false;
    }

    private void handleCanvasClick(int px, int py, int btn) {
        int storeColor = usesTint() ? removeTint(currentColor) : currentColor;
        switch (currentTool) {
            case PENCIL -> { canvas.saveSnapshot(); canvas.drawPixel(px, py, btn == 1 ? 0 : storeColor); ColorHistory.getInstance().addColor(currentColor); }
            case ERASER -> { canvas.saveSnapshot(); canvas.erasePixel(px, py); }
            case FILL -> { canvas.saveSnapshot(); canvas.floodFill(px, py, storeColor); ColorHistory.getInstance().addColor(currentColor); }
            case EYEDROPPER -> {
                int raw = canvas.pickColor(px, py);
                currentColor = usesTint() ? applyTint(raw) : raw;
                if (hexInput != null) hexInput.setText(String.format("#%08X", currentColor));
            }
            case LINE -> {
                if (!lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; }
                else { canvas.saveSnapshot(); canvas.drawLine(lineStartX, lineStartY, px, py, storeColor); lineFirstClick = false; ColorHistory.getInstance().addColor(currentColor); }
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

    @Override
    public boolean shouldPause() { return false; }
}
