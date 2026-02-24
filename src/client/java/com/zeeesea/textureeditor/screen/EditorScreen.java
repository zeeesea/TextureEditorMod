package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.TextureEditorClient;
import com.zeeesea.textureeditor.editor.ColorHistory;
import com.zeeesea.textureeditor.editor.EditorTool;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * The main pixel editor screen. Allows painting on a block face texture.
 */
public class EditorScreen extends Screen {

    private final BlockPos blockPos;
    private Direction face;
    private final BlockState blockState;

    // Texture data
    private Identifier spriteId;
    private Identifier textureId;
    private PixelCanvas canvas;
    private int[][] originalPixels;

    // Tint color for blocks like grass that use color maps
    private int blockTint = -1; // -1 = no tint (white)
    private boolean isTinted = false;

    // Editor state
    private EditorTool currentTool = EditorTool.PENCIL;
    private int currentColor = 0xFFFF0000;
    private int zoom = 12;
    private boolean showGrid = true;

    // Canvas rendering position (base + pan offset)
    private int canvasBaseX;
    private int canvasBaseY;
    private int panOffsetX = 0;
    private int panOffsetY = 0;
    private int canvasScreenX;
    private int canvasScreenY;

    // Panning state (right-click drag)
    private boolean isPanning = false;
    private double panStartMouseX, panStartMouseY;
    private int panStartOffsetX, panStartOffsetY;

    // Line tool state
    private int lineStartX = -1, lineStartY = -1;
    private boolean lineFirstClick = false;

    // HSV color picker state
    private boolean showColorPicker = false;
    private float pickerHue = 0f, pickerSat = 1f, pickerVal = 1f;

    // Color palette
    private static final int[] PALETTE = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF,
            0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF80FF00, 0xFF00FF00,
            0xFF00FF80, 0xFF00FFFF, 0xFF0080FF, 0xFF0000FF, 0xFF8000FF,
            0xFFFF00FF, 0xFFFF0080, 0xFF800000, 0xFF804000, 0xFF808000,
            0xFF408000, 0xFF008000, 0xFF008040, 0xFF008080, 0xFF004080,
            0xFF000080, 0xFF400080, 0xFF800080, 0xFF800040, 0xFF663300,
            0xFFCC6600, 0xFFFFCC99, 0xFF336633, 0xFF66CCCC, 0xFF6666CC,
    };

    private TextFieldWidget hexInput;

    public EditorScreen(BlockHitResult hitResult) {
        super(Text.literal("Texture Editor"));
        this.blockPos = hitResult.getBlockPos();
        this.face = hitResult.getSide();
        MinecraftClient client = MinecraftClient.getInstance();
        this.blockState = client.world != null ? client.world.getBlockState(blockPos) : null;

        // Get biome tint color for this block at this position
        if (blockState != null && client.world != null) {
            int color = client.getBlockColors().getColor(blockState, client.world, blockPos, 0);
            System.out.println("[TextureEditor] Block: " + blockState.getBlock().getName().getString() + " Tint Color: " + Integer.toHexString(color));
            if (color != -1) {
                blockTint = color | 0xFF000000; // ensure full alpha
                isTinted = true;
            }
        }
    }

    /**
     * Apply tint color to a grayscale pixel for display.
     */
    private int applyTint(int pixel) {
        if (!isTinted) return pixel;
        int a = (pixel >> 24) & 0xFF;
        int r = (pixel >> 16) & 0xFF, g = (pixel >> 8) & 0xFF, b = pixel & 0xFF;
        int tr = (blockTint >> 16) & 0xFF, tg = (blockTint >> 8) & 0xFF, tb = blockTint & 0xFF;
        return (a << 24) | ((r * tr / 255) << 16) | ((g * tg / 255) << 8) | (b * tb / 255);
    }

    /**
     * Remove tint from a color to get the grayscale value for storage.
     * Inverse of applyTint.
     */
    private int removeTint(int color) {
        if (!isTinted) return color;
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int tr = (blockTint >> 16) & 0xFF, tg = (blockTint >> 8) & 0xFF, tb = blockTint & 0xFF;
        r = tr > 0 ? Math.min(255, r * 255 / tr) : r;
        g = tg > 0 ? Math.min(255, g * 255 / tg) : g;
        b = tb > 0 ? Math.min(255, b * 255 / tb) : b;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String getKeyName(net.minecraft.client.option.KeyBinding key) {
        return key.getBoundKeyLocalizedText().getString();
    }

    @Override
    protected void init() {
        // Check if TextureManager already has modified pixels for this texture
        if (blockState != null) {
            TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, face);
            if (tex != null) {
                originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
                textureId = tex.textureId();
                spriteId = Identifier.of(
                        tex.textureId().getNamespace(),
                        tex.textureId().getPath().replace("textures/", "").replace(".png", "")
                );

                // Load previously saved pixels if they exist
                int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
                int[] savedDims = TextureManager.getInstance().getDimensions(textureId);
                if (savedPixels != null && savedDims != null && savedDims[0] == tex.width() && savedDims[1] == tex.height()) {
                    canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
                } else {
                    canvas = new PixelCanvas(tex.width(), tex.height(), tex.pixels());
                }
            }
        }
        if (canvas == null) {
            canvas = new PixelCanvas(16, 16);
            originalPixels = new int[16][16];
        }

        int canvasPixelSize = Math.min(zoom, Math.min(
                (this.width - 200) / canvas.getWidth(),
                (this.height - 80) / canvas.getHeight()
        ));
        if (canvasPixelSize < 2) canvasPixelSize = 2;
        zoom = canvasPixelSize;

        canvasBaseX = 120 + (this.width - 240 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;

        // Tool buttons (left side)
        int toolY = 30;
        for (EditorTool tool : EditorTool.values()) {
            final EditorTool t = tool;
            addDrawableChild(ButtonWidget.builder(Text.literal(tool.getDisplayName()), btn -> currentTool = t)
                    .position(5, toolY).size(100, 20).build());
            toolY += 24;
        }

        // Undo / Redo with key name from Controls
        String undoKeyName = getKeyName(TextureEditorClient.getUndoKey());
        String redoKeyName = getKeyName(TextureEditorClient.getRedoKey());

        toolY += 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("Undo (" + undoKeyName + ")"), btn -> canvas.undo())
                .position(5, toolY).size(100, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Redo (" + redoKeyName + ")"), btn -> canvas.redo())
                .position(5, toolY).size(100, 20).build());

        // Grid toggle
        toolY += 34;
        addDrawableChild(ButtonWidget.builder(Text.literal("Grid (G)"), btn -> showGrid = !showGrid)
                .position(5, toolY).size(100, 20).build());

        // Zoom
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom +"), btn -> {
            if (zoom < 40) { zoom += 2; recalcCanvasPos(); }
        }).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom -"), btn -> {
            if (zoom > 2) { zoom -= 2; recalcCanvasPos(); }
        }).position(57, toolY).size(48, 20).build());

        // Face selection buttons
        toolY += 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Top"), btn -> switchFace(Direction.UP)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Bottom"), btn -> switchFace(Direction.DOWN)).position(57, toolY).size(48, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("North"), btn -> switchFace(Direction.NORTH)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("South"), btn -> switchFace(Direction.SOUTH)).position(57, toolY).size(48, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("East"), btn -> switchFace(Direction.EAST)).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("West"), btn -> switchFace(Direction.WEST)).position(57, toolY).size(48, 20).build());

        // Reset buttons - positioned at bottom of right panel, below history
        int resetX = this.width - 115;
        int resetBaseY = this.height - 100;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Face"), btn -> resetFace())
                .position(resetX, resetBaseY).size(110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Block"), btn -> resetBlock())
                .position(resetX, resetBaseY + 24).size(110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cReset All"), btn -> resetAll())
                .position(resetX, resetBaseY + 48).size(110, 20).build());

        // Apply live
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7aApply Live"), btn -> applyLive())
                .position(5, this.height - 78).size(100, 20).build());

        // Export
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a76Export Pack"), btn ->
                client.setScreen(new ExportScreen(this)))
                .position(5, this.height - 54).size(100, 20).build());

        // Browse
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7dBrowse"), btn ->
                client.setScreen(new BrowseScreen()))
                .position(5, this.height - 30).size(100, 20).build());

        // Close
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cClose"), btn -> this.close())
                .position(this.width - 65, 5).size(60, 20).build());

        // Color picker toggle
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7bPicker"), btn -> showColorPicker = !showColorPicker)
                .position(this.width - 65, this.height - 26).size(60, 20).build());

        // Hex color input
        int paletteX = this.width - 115;
        int paletteEndY = 30 + ((PALETTE.length + 4) / 5) * 22 + 10;
        hexInput = new TextFieldWidget(this.textRenderer, paletteX, paletteEndY, 110, 18, Text.literal("Hex"));
        hexInput.setMaxLength(9);
        hexInput.setText(String.format("#%06X", currentColor & 0xFFFFFF));
        hexInput.setChangedListener(text -> {
            try {
                String hex = text.startsWith("#") ? text.substring(1) : text;
                if (hex.length() == 6 || hex.length() == 8) {
                    currentColor = (int) Long.parseLong(hex.length() == 6 ? "FF" + hex : hex, 16);
                }
            } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(hexInput);
    }

    private void recalcCanvasPos() {
        canvasBaseX = 120 + (this.width - 240 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;
    }

    private void switchFace(Direction newFace) {
        applyLive(); // save current work
        this.face = newFace;
        panOffsetX = 0; panOffsetY = 0;
        canvas = null;
        this.clearChildren();
        this.init();
    }

    private boolean isInUIRegion(double mouseX, double mouseY) {
        // Left toolbar
        if (mouseX < 110) return true;
        // Right palette area
        if (mouseX > this.width - 120) return true;
        // Top bar
        if (mouseY < 28) return true;
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't let super draw its default background - we handle it ourselves
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Full background
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);

        // 2. Canvas (drawn on top of background, behind UI)
        drawCanvas(context, mouseX, mouseY);

        // 3. UI panel backgrounds (cover canvas edges where buttons sit)
        context.fill(0, 0, 110, this.height, 0xFF1A1A2E); // left toolbar bg
        context.fill(this.width - 120, 0, this.width, this.height, 0xFF1A1A2E); // right palette bg
        context.fill(110, 0, this.width - 120, 28, 0xFF1A1A2E); // top bar bg

        // 4. Render all widgets (buttons, text fields) on top of panels
        super.render(context, mouseX, mouseY, delta);

        // Title and tool info
        String blockName = blockState != null ? blockState.getBlock().getName().getString() : "Unknown";
        String tintLabel = isTinted ? " \u00a7a[Tinted]" : "";
        context.drawText(textRenderer, "Texture Editor - " + blockName + " (" + face.getName() + ")" + tintLabel,
                120, 8, 0xFFFFFF, true);
        context.drawText(textRenderer, "Tool: " + currentTool.getDisplayName(), 120, 20, 0xCCCCCC, false);

        // Palette
        drawPalette(context, mouseX, mouseY);

        // Current color preview
        int paletteX = this.width - 115;
        int paletteEndY = 30 + ((PALETTE.length + 4) / 5) * 22 + 35;
        context.drawText(textRenderer, "Current Color:", paletteX, paletteEndY, 0xCCCCCC, false);
        context.fill(paletteX, paletteEndY + 12, paletteX + 30, paletteEndY + 32, currentColor);
        drawRectOutline(context, paletteX, paletteEndY + 12, paletteX + 30, paletteEndY + 32, 0xFFFFFFFF);

        // Color history
        drawColorHistory(context, mouseX, mouseY);

        // HSV Color picker
        if (showColorPicker) {
            drawColorPicker(context, mouseX, mouseY);
        }

        if (currentTool == EditorTool.LINE && lineFirstClick) {
            context.drawText(textRenderer, "Click endpoint...", canvasScreenX, canvasScreenY - 12, 0xFFFF00, false);
        }
    }

    private void drawCanvas(DrawContext context, int mouseX, int mouseY) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int sx = canvasScreenX + x * zoom, sy = canvasScreenY + y * zoom;
                int bg = ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0;
                context.fill(sx, sy, sx + zoom, sy + zoom, bg);
                int color = canvas.getPixel(x, y);
                if (((color >> 24) & 0xFF) > 0) {
                    context.fill(sx, sy, sx + zoom, sy + zoom, applyTint(color));
                }
            }
        }
        if (showGrid && zoom >= 4) {
            for (int x = 0; x <= w; x++)
                context.fill(canvasScreenX + x * zoom, canvasScreenY, canvasScreenX + x * zoom + 1, canvasScreenY + h * zoom, 0x40FFFFFF);
            for (int y = 0; y <= h; y++)
                context.fill(canvasScreenX, canvasScreenY + y * zoom, canvasScreenX + w * zoom, canvasScreenY + y * zoom + 1, 0x40FFFFFF);
        }
        drawRectOutline(context, canvasScreenX - 1, canvasScreenY - 1, canvasScreenX + w * zoom + 1, canvasScreenY + h * zoom + 1, 0xFFFFFFFF);
        int hoverX = (mouseX - canvasScreenX) / zoom, hoverY = (mouseY - canvasScreenY) / zoom;
        if (hoverX >= 0 && hoverX < w && hoverY >= 0 && hoverY < h) {
            int sx = canvasScreenX + hoverX * zoom, sy = canvasScreenY + hoverY * zoom;
            drawRectOutline(context, sx, sy, sx + zoom, sy + zoom, 0xFFFFFF00);
        }
    }

    private void drawPalette(DrawContext context, int mouseX, int mouseY) {
        int paletteX = this.width - 115, paletteY = 30, cellSize = 20, cols = 5;
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % cols, row = i / cols;
            int px = paletteX + col * (cellSize + 2), py = paletteY + row * (cellSize + 2);
            context.fill(px, py, px + cellSize, py + cellSize, PALETTE[i]);
            if (PALETTE[i] == currentColor) drawRectOutline(context, px - 1, py - 1, px + cellSize + 1, py + cellSize + 1, 0xFFFFFF00);
            else drawRectOutline(context, px, py, px + cellSize, py + cellSize, 0xFF333333);
        }
    }

    private void drawColorHistory(DrawContext context, int mouseX, int mouseY) {
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() == 0) return;
        int paletteX = this.width - 115;
        // Move history down to avoid overlapping "Current Color" box
        // Current color box ends at: 30 + ((PALETTE.length + 4) / 5) * 22 + 35 + 32 = ~67 offset
        int startY = 30 + ((PALETTE.length + 4) / 5) * 22 + 80;
        context.drawText(textRenderer, "History:", paletteX, startY, 0x999999, false);
        startY += 12;
        int cols = 5, cellSize = 18;
        java.util.List<Integer> colors = hist.getColors();
        for (int i = 0; i < colors.size(); i++) {
            int col = i % cols, row = i / cols;
            int px = paletteX + col * (cellSize + 2), py = startY + row * (cellSize + 2);
            context.fill(px, py, px + cellSize, py + cellSize, colors.get(i));
            if (colors.get(i) == currentColor) drawRectOutline(context, px - 1, py - 1, px + cellSize + 1, py + cellSize + 1, 0xFFFFFF00);
            else drawRectOutline(context, px, py, px + cellSize, py + cellSize, 0xFF333333);
        }
    }

    private void drawColorPicker(DrawContext context, int mouseX, int mouseY) {
        int cpX = this.width / 2 - 80, cpY = this.height - 90;
        int cpW = 160, cpH = 80;

        // Background
        context.fill(cpX - 2, cpY - 14, cpX + cpW + 2, cpY + cpH + 2, 0xFF222244);
        drawRectOutline(context, cpX - 2, cpY - 14, cpX + cpW + 2, cpY + cpH + 2, 0xFFFFFFFF);
        context.drawText(textRenderer, "Color Picker", cpX, cpY - 12, 0xFFFFFF, false);

        // SV gradient area (120x80)
        int svW = 120, svH = cpH;
        for (int x = 0; x < svW; x++) {
            for (int y = 0; y < svH; y++) {
                float s = x / (float)(svW - 1);
                float v = 1f - y / (float)(svH - 1);
                int c = hsvToRgb(pickerHue, s, v);
                context.fill(cpX + x, cpY + y, cpX + x + 1, cpY + y + 1, c);
            }
        }
        // SV cursor
        int svCurX = cpX + (int)(pickerSat * (svW - 1));
        int svCurY = cpY + (int)((1f - pickerVal) * (svH - 1));
        drawRectOutline(context, svCurX - 2, svCurY - 2, svCurX + 3, svCurY + 3, 0xFFFFFFFF);

        // Hue strip (20x80)
        int hueX = cpX + svW + 5, hueW = 20;
        for (int y = 0; y < cpH; y++) {
            float h = y / (float)(cpH - 1);
            int c = hsvToRgb(h, 1f, 1f);
            context.fill(hueX, cpY + y, hueX + hueW, cpY + y + 1, c);
        }
        // Hue cursor
        int hueCurY = cpY + (int)(pickerHue * (cpH - 1));
        context.fill(hueX - 1, hueCurY - 1, hueX + hueW + 1, hueCurY + 2, 0xFFFFFFFF);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int)(h * 6) % 6;
        float f = h * 6 - (int)(h * 6);
        int p = (int)(255 * v * (1 - s));
        int q = (int)(255 * v * (1 - f * s));
        int t = (int)(255 * v * (1 - (1 - f) * s));
        int vi = (int)(255 * v);
        return switch (i) {
            case 0 -> 0xFF000000 | (vi << 16) | (t << 8) | p;
            case 1 -> 0xFF000000 | (q << 16) | (vi << 8) | p;
            case 2 -> 0xFF000000 | (p << 16) | (vi << 8) | t;
            case 3 -> 0xFF000000 | (p << 16) | (q << 8) | vi;
            case 4 -> 0xFF000000 | (t << 16) | (p << 8) | vi;
            default -> 0xFF000000 | (vi << 16) | (p << 8) | q;
        };
    }

    private void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        ctx.fill(x1, y1, x2, y1 + 1, c); ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c); ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    private void setColor(int c) {
        currentColor = c;
        ColorHistory.getInstance().addColor(c);
        if (hexInput != null) hexInput.setText(String.format("#%06X", c & 0xFFFFFF));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int oldZoom = zoom;
        if (verticalAmount > 0 && zoom < 40) zoom += 2;
        else if (verticalAmount < 0 && zoom > 2) zoom -= 2;
        if (zoom != oldZoom) {
            double relX = (mouseX - canvasScreenX) / (double)(canvas.getWidth() * oldZoom);
            double relY = (mouseY - canvasScreenY) / (double)(canvas.getHeight() * oldZoom);
            recalcCanvasPos();
            canvasScreenX = (int)(mouseX - relX * canvas.getWidth() * zoom);
            canvasScreenY = (int)(mouseY - relY * canvas.getHeight() * zoom);
            panOffsetX = canvasScreenX - canvasBaseX;
            panOffsetY = canvasScreenY - canvasBaseY;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        System.out.println("[TextureEditor] EditorScreen.mouseClicked(" + mouseX + ", " + mouseY + ", btn=" + button + ") isInUIRegion=" + isInUIRegion(mouseX, mouseY));
        // Let widgets handle first (buttons, text fields)
        if (super.mouseClicked(mouseX, mouseY, button)) {
            System.out.println("[TextureEditor] -> handled by super (widget)");
            return true;
        }

        // Color picker click
        if (showColorPicker && button == 0 && handleColorPickerClick(mouseX, mouseY)) return true;

        // Color history click
        if (button == 0 && handleHistoryClick(mouseX, mouseY)) return true;

        // Right-click = pan
        if (button == 1) {
            isPanning = true;
            panStartMouseX = mouseX; panStartMouseY = mouseY;
            panStartOffsetX = panOffsetX; panStartOffsetY = panOffsetY;
            return true;
        }

        // Palette click (must be before UI region guard since palette IS in UI region)
        if (button == 0 && handlePaletteClick(mouseX, mouseY)) return true;

        // Don't draw on canvas if clicking UI region
        if (isInUIRegion(mouseX, mouseY)) return false;


        // Canvas click
        if (button == 0) {
            int px = (int)((mouseX - canvasScreenX) / zoom), py = (int)((mouseY - canvasScreenY) / zoom);
            if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
                handleCanvasClick(px, py, button);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) { isPanning = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 1 && isPanning) {
            panOffsetX = panStartOffsetX + (int)(mouseX - panStartMouseX);
            panOffsetY = panStartOffsetY + (int)(mouseY - panStartMouseY);
            canvasScreenX = canvasBaseX + panOffsetX;
            canvasScreenY = canvasBaseY + panOffsetY;
            return true;
        }

        // Color picker drag
        if (showColorPicker && button == 0 && handleColorPickerClick(mouseX, mouseY)) return true;

        if (isInUIRegion(mouseX, mouseY)) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        int px = (int)((mouseX - canvasScreenX) / zoom), py = (int)((mouseY - canvasScreenY) / zoom);
        if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
            if (currentTool == EditorTool.PENCIL) canvas.drawPixel(px, py, isTinted ? removeTint(currentColor) : currentColor);
            else if (currentTool == EditorTool.ERASER) canvas.erasePixel(px, py);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private boolean handleColorPickerClick(double mouseX, double mouseY) {
        int cpX = this.width / 2 - 80, cpY = this.height - 90;
        int svW = 120, svH = 80;
        int hueX = cpX + svW + 5, hueW = 20;

        // SV area
        if (mouseX >= cpX && mouseX < cpX + svW && mouseY >= cpY && mouseY < cpY + svH) {
            pickerSat = (float)(mouseX - cpX) / (svW - 1);
            pickerVal = 1f - (float)(mouseY - cpY) / (svH - 1);
            pickerSat = Math.max(0, Math.min(1, pickerSat));
            pickerVal = Math.max(0, Math.min(1, pickerVal));
            setColor(hsvToRgb(pickerHue, pickerSat, pickerVal));
            return true;
        }
        // Hue strip
        if (mouseX >= hueX && mouseX < hueX + hueW && mouseY >= cpY && mouseY < cpY + svH) {
            pickerHue = (float)(mouseY - cpY) / (svH - 1);
            pickerHue = Math.max(0, Math.min(1, pickerHue));
            setColor(hsvToRgb(pickerHue, pickerSat, pickerVal));
            return true;
        }
        return false;
    }

    private boolean handleHistoryClick(double mouseX, double mouseY) {
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() == 0) return false;
        int paletteX = this.width - 115;
        // Adjusted Y position to match drawColorHistory
        int startY = 30 + ((PALETTE.length + 4) / 5) * 22 + 92; // 80 + 12 (text height)
        int cols = 5, cellSize = 18;
        java.util.List<Integer> colors = hist.getColors();
        for (int i = 0; i < colors.size(); i++) {
            int col = i % cols, row = i / cols;
            int px = paletteX + col * (cellSize + 2), py = startY + row * (cellSize + 2);
            if (mouseX >= px && mouseX < px + cellSize && mouseY >= py && mouseY < py + cellSize) {
                currentColor = colors.get(i);
                hexInput.setText(String.format("#%06X", currentColor & 0xFFFFFF));
                return true;
            }
        }
        return false;
    }

    private void handleCanvasClick(int px, int py, int button) {
        int storeColor = isTinted ? removeTint(currentColor) : currentColor;
        switch (currentTool) {
            case PENCIL -> { canvas.saveSnapshot(); canvas.drawPixel(px, py, button == 1 ? 0x00000000 : storeColor); ColorHistory.getInstance().addColor(currentColor); }
            case ERASER -> { canvas.saveSnapshot(); canvas.erasePixel(px, py); }
            case FILL -> { canvas.saveSnapshot(); canvas.floodFill(px, py, storeColor); ColorHistory.getInstance().addColor(currentColor); }
            case EYEDROPPER -> { int raw = canvas.pickColor(px, py); currentColor = applyTint(raw); hexInput.setText(String.format("#%08X", currentColor)); }
            case LINE -> {
                if (!lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; }
                else { canvas.saveSnapshot(); canvas.drawLine(lineStartX, lineStartY, px, py, storeColor); lineFirstClick = false; ColorHistory.getInstance().addColor(currentColor); }
            }
        }
    }

    private boolean handlePaletteClick(double mouseX, double mouseY) {
        int paletteX = this.width - 115, paletteY = 30, cellSize = 20, cols = 5;
        System.out.println("[TextureEditor] handlePaletteClick called at (" + mouseX + ", " + mouseY + ") paletteX=" + paletteX + " paletteY=" + paletteY);
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % cols, row = i / cols;
            int px = paletteX + col * (cellSize + 2), py = paletteY + row * (cellSize + 2);
            if (mouseX >= px && mouseX < px + cellSize && mouseY >= py && mouseY < py + cellSize) {
                System.out.println("[TextureEditor] Palette color " + i + " clicked! Color=#" + String.format("%08X", PALETTE[i]));
                setColor(PALETTE[i]);
                return true;
            }
        }
        System.out.println("[TextureEditor] No palette cell hit.");
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (TextureEditorClient.getUndoKey().matchesKey(keyCode, scanCode)) { canvas.undo(); return true; }
        if (TextureEditorClient.getRedoKey().matchesKey(keyCode, scanCode)) { canvas.redo(); return true; }
        if (keyCode == GLFW.GLFW_KEY_G && !hexInput.isFocused()) { showGrid = !showGrid; return true; }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) { if (zoom < 40) { zoom += 2; recalcCanvasPos(); } return true; }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) { if (zoom > 2) { zoom -= 2; recalcCanvasPos(); } return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // --- Reset methods ---

    private void resetFace() {
        if (originalPixels == null || spriteId == null) return;
        canvas.saveSnapshot();
        for (int x = 0; x < canvas.getWidth(); x++) for (int y = 0; y < canvas.getHeight(); y++) canvas.setPixel(x, y, originalPixels[x][y]);
        applyLive();
    }

    private void resetBlock() {
        if (blockState == null) return;
        for (Direction dir : Direction.values()) {
            TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(blockState, dir);
            if (tex != null) {
                Identifier sid = Identifier.of(tex.textureId().getNamespace(), tex.textureId().getPath().replace("textures/", "").replace(".png", ""));
                TextureManager.getInstance().removeTexture(tex.textureId());
                MinecraftClient.getInstance().execute(() -> TextureManager.getInstance().applyLive(sid, tex.pixels(), tex.width(), tex.height()));
            }
        }
        if (originalPixels != null) {
            canvas.saveSnapshot();
            for (int x = 0; x < canvas.getWidth(); x++) for (int y = 0; y < canvas.getHeight(); y++) canvas.setPixel(x, y, originalPixels[x][y]);
        }
    }

    private void resetAll() {
        TextureManager.getInstance().clear();
        MinecraftClient.getInstance().reloadResources();
        if (originalPixels != null) {
            canvas.saveSnapshot();
            for (int x = 0; x < canvas.getWidth(); x++) for (int y = 0; y < canvas.getHeight(); y++) canvas.setPixel(x, y, originalPixels[x][y]);
        }
    }

    private void applyLive() {
        if (spriteId == null || canvas == null) return;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight()));
    }

    private static int[][] copyPixels(int[][] src, int w, int h) {
        int[][] copy = new int[w][h];
        for (int x = 0; x < w; x++) System.arraycopy(src[x], 0, copy[x], 0, h);
        return copy;
    }

    @Override
    public boolean shouldPause() { return false; }
}
