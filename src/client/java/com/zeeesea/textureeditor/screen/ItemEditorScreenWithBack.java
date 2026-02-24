package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.TextureEditorClient;
import com.zeeesea.textureeditor.editor.ColorHistory;
import com.zeeesea.textureeditor.editor.EditorTool;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Item editor screen with a "Browse" back button to return to BrowseScreen.
 */
public class ItemEditorScreenWithBack extends Screen {

    private final ItemStack itemStack;
    private final String itemName;
    private final Screen parent;

    private Identifier spriteId;
    private Identifier textureId;
    private PixelCanvas canvas;
    private int[][] originalPixels;

    private EditorTool currentTool = EditorTool.PENCIL;
    private int currentColor = 0xFFFF0000;
    private int zoom = 12;
    private boolean showGrid = true;

    private int canvasBaseX, canvasBaseY;
    private int panOffsetX = 0, panOffsetY = 0;
    private int canvasScreenX, canvasScreenY;

    private boolean isPanning = false;
    private double panStartMouseX, panStartMouseY;
    private int panStartOffsetX, panStartOffsetY;

    private int lineStartX = -1, lineStartY = -1;
    private boolean lineFirstClick = false;

    private boolean showColorPicker = false;
    private float pickerHue = 0f, pickerSat = 1f, pickerVal = 1f;

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

    public ItemEditorScreenWithBack(ItemStack itemStack, Screen parent) {
        super(Text.literal("Item Texture Editor"));
        this.itemStack = itemStack;
        this.itemName = itemStack.getName().getString();
        this.parent = parent;
    }

    @Override
    protected void init() {
        ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(itemStack);
        if (tex != null) {
            originalPixels = copyPixels(tex.pixels(), tex.width(), tex.height());
            textureId = tex.textureId();
            spriteId = tex.spriteId();
            int[][] savedPixels = TextureManager.getInstance().getPixels(textureId);
            int[] savedDims = TextureManager.getInstance().getDimensions(textureId);
            if (savedPixels != null && savedDims != null && savedDims[0] == tex.width() && savedDims[1] == tex.height()) {
                canvas = new PixelCanvas(savedDims[0], savedDims[1], savedPixels);
            } else {
                canvas = new PixelCanvas(tex.width(), tex.height(), tex.pixels());
            }
        }
        if (canvas == null) { canvas = new PixelCanvas(16, 16); originalPixels = new int[16][16]; }

        int canvasPixelSize = Math.min(zoom, Math.min((this.width - 200) / canvas.getWidth(), (this.height - 80) / canvas.getHeight()));
        if (canvasPixelSize < 2) canvasPixelSize = 2;
        zoom = canvasPixelSize;

        canvasBaseX = 120 + (this.width - 240 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX; canvasScreenY = canvasBaseY + panOffsetY;

        int toolY = 30;
        for (EditorTool tool : EditorTool.values()) {
            final EditorTool t = tool;
            addDrawableChild(ButtonWidget.builder(Text.literal(tool.getDisplayName()), btn -> currentTool = t).position(5, toolY).size(100, 20).build());
            toolY += 24;
        }
        String undoK = TextureEditorClient.getUndoKey().getBoundKeyLocalizedText().getString();
        String redoK = TextureEditorClient.getRedoKey().getBoundKeyLocalizedText().getString();
        toolY += 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("Undo (" + undoK + ")"), btn -> canvas.undo()).position(5, toolY).size(100, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Redo (" + redoK + ")"), btn -> canvas.redo()).position(5, toolY).size(100, 20).build());
        toolY += 34;
        addDrawableChild(ButtonWidget.builder(Text.literal("Grid (G)"), btn -> showGrid = !showGrid).position(5, toolY).size(100, 20).build());
        toolY += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom +"), btn -> { if (zoom < 40) { zoom += 2; recalcCanvasPos(); } }).position(5, toolY).size(48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom -"), btn -> { if (zoom > 2) { zoom -= 2; recalcCanvasPos(); } }).position(57, toolY).size(48, 20).build());

        int resetX = this.width - 115;
        int resetBaseY = this.height - 80;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Item"), btn -> resetItem()).position(resetX, resetBaseY).size(110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cReset All"), btn -> resetAll()).position(resetX, resetBaseY + 24).size(110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7aApply Live"), btn -> applyLive()).position(5, this.height - 78).size(100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a76Export Pack"), btn -> client.setScreen(new ExportScreen(this))).position(5, this.height - 54).size(100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7dBrowse"), btn -> client.setScreen(parent)).position(5, this.height - 30).size(100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cClose"), btn -> this.close()).position(this.width - 65, 5).size(60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7bPicker"), btn -> showColorPicker = !showColorPicker).position(this.width - 65, this.height - 26).size(60, 20).build());

        int paletteX = this.width - 115;
        int paletteEndY = 30 + ((PALETTE.length + 4) / 5) * 22 + 10;
        hexInput = new TextFieldWidget(this.textRenderer, paletteX, paletteEndY, 110, 18, Text.literal("Hex"));
        hexInput.setMaxLength(9);
        hexInput.setText(String.format("#%06X", currentColor & 0xFFFFFF));
        hexInput.setChangedListener(text -> {
            try {
                String hex = text.startsWith("#") ? text.substring(1) : text;
                if (hex.length() == 6 || hex.length() == 8) currentColor = (int) Long.parseLong(hex.length() == 6 ? "FF" + hex : hex, 16);
            } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(hexInput);
    }

    private void recalcCanvasPos() {
        canvasBaseX = 120 + (this.width - 240 - canvas.getWidth() * zoom) / 2;
        canvasBaseY = 30 + (this.height - 80 - canvas.getHeight() * zoom) / 2;
        canvasScreenX = canvasBaseX + panOffsetX; canvasScreenY = canvasBaseY + panOffsetY;
    }

    private boolean isInUIRegion(double mx, double my) { return mx < 110 || mx > this.width - 120 || my < 28; }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);
        drawCanvas(context, mouseX, mouseY);
        context.fill(0, 0, 110, this.height, 0xFF1A1A2E);
        context.fill(this.width - 120, 0, this.width, this.height, 0xFF1A1A2E);
        context.fill(110, 0, this.width - 120, 28, 0xFF1A1A2E);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(textRenderer, "Item Editor - " + itemName, 120, 8, 0xFFFFFF, true);
        context.drawText(textRenderer, "Tool: " + currentTool.getDisplayName() + "  |  " + canvas.getWidth() + "x" + canvas.getHeight(), 120, 20, 0xCCCCCC, false);
        drawPalette(context, mouseX, mouseY);
        int paletteX = this.width - 115;
        int paletteEndY = 30 + ((PALETTE.length + 4) / 5) * 22 + 35;
        context.drawText(textRenderer, "Current Color:", paletteX, paletteEndY, 0xCCCCCC, false);
        context.fill(paletteX, paletteEndY + 12, paletteX + 30, paletteEndY + 32, currentColor);
        drawRectOutline(context, paletteX, paletteEndY + 12, paletteX + 30, paletteEndY + 32, 0xFFFFFFFF);
        drawColorHistory(context, mouseX, mouseY);
        if (showColorPicker) drawColorPicker(context, mouseX, mouseY);
        if (currentTool == EditorTool.LINE && lineFirstClick) context.drawText(textRenderer, "Click endpoint...", canvasScreenX, canvasScreenY - 12, 0xFFFF00, false);
    }

    private void drawCanvas(DrawContext ctx, int mx, int my) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            int sx = canvasScreenX + x * zoom, sy = canvasScreenY + y * zoom;
            ctx.fill(sx, sy, sx + zoom, sy + zoom, ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0);
            int c = canvas.getPixel(x, y); if (((c >> 24) & 0xFF) > 0) ctx.fill(sx, sy, sx + zoom, sy + zoom, c);
        }
        if (showGrid && zoom >= 4) {
            for (int x = 0; x <= w; x++) ctx.fill(canvasScreenX + x * zoom, canvasScreenY, canvasScreenX + x * zoom + 1, canvasScreenY + h * zoom, 0x40FFFFFF);
            for (int y = 0; y <= h; y++) ctx.fill(canvasScreenX, canvasScreenY + y * zoom, canvasScreenX + w * zoom, canvasScreenY + y * zoom + 1, 0x40FFFFFF);
        }
        drawRectOutline(ctx, canvasScreenX - 1, canvasScreenY - 1, canvasScreenX + w * zoom + 1, canvasScreenY + h * zoom + 1, 0xFFFFFFFF);
        int hx = (mx - canvasScreenX) / zoom, hy = (my - canvasScreenY) / zoom;
        if (hx >= 0 && hx < w && hy >= 0 && hy < h) { int sx = canvasScreenX + hx * zoom, sy = canvasScreenY + hy * zoom; drawRectOutline(ctx, sx, sy, sx + zoom, sy + zoom, 0xFFFFFF00); }
    }

    private void drawPalette(DrawContext ctx, int mx, int my) {
        int px0 = this.width - 115, py0 = 30, cs = 20, cols = 5;
        for (int i = 0; i < PALETTE.length; i++) { int c = i%cols, r = i/cols; int px = px0+c*(cs+2), py = py0+r*(cs+2); ctx.fill(px, py, px+cs, py+cs, PALETTE[i]); if (PALETTE[i] == currentColor) drawRectOutline(ctx, px-1, py-1, px+cs+1, py+cs+1, 0xFFFFFF00); else drawRectOutline(ctx, px, py, px+cs, py+cs, 0xFF333333); }
    }

    private void drawColorHistory(DrawContext ctx, int mx, int my) {
        ColorHistory hist = ColorHistory.getInstance(); if (hist.size() == 0) return;
        int px0 = this.width - 115; int sy = 30 + ((PALETTE.length + 4) / 5) * 22 + 80;
        ctx.drawText(textRenderer, "History:", px0, sy, 0x999999, false); sy += 12;
        int cols = 5, cs = 18; List<Integer> colors = hist.getColors();
        for (int i = 0; i < colors.size(); i++) { int c = i%cols, r = i/cols; int px = px0+c*(cs+2), py = sy+r*(cs+2); ctx.fill(px, py, px+cs, py+cs, colors.get(i)); if (colors.get(i) == currentColor) drawRectOutline(ctx, px-1, py-1, px+cs+1, py+cs+1, 0xFFFFFF00); else drawRectOutline(ctx, px, py, px+cs, py+cs, 0xFF333333); }
    }

    private void drawColorPicker(DrawContext ctx, int mx, int my) {
        int cpX = this.width / 2 - 80, cpY = this.height - 90, svW = 120, svH = 80;
        ctx.fill(cpX - 2, cpY - 14, cpX + 162, cpY + 82, 0xFF222244);
        drawRectOutline(ctx, cpX - 2, cpY - 14, cpX + 162, cpY + 82, 0xFFFFFFFF);
        ctx.drawText(textRenderer, "Color Picker", cpX, cpY - 12, 0xFFFFFF, false);
        for (int x = 0; x < svW; x++) for (int y = 0; y < svH; y++) { float s = x/(float)(svW-1), v = 1f-y/(float)(svH-1); ctx.fill(cpX+x, cpY+y, cpX+x+1, cpY+y+1, hsvToRgb(pickerHue, s, v)); }
        int scx = cpX+(int)(pickerSat*(svW-1)), scy = cpY+(int)((1f-pickerVal)*(svH-1));
        drawRectOutline(ctx, scx-2, scy-2, scx+3, scy+3, 0xFFFFFFFF);
        int hueX = cpX+svW+5, hueW = 20;
        for (int y = 0; y < svH; y++) { float h = y/(float)(svH-1); ctx.fill(hueX, cpY+y, hueX+hueW, cpY+y+1, hsvToRgb(h, 1f, 1f)); }
        int hcy = cpY+(int)(pickerHue*(svH-1));
        ctx.fill(hueX-1, hcy-1, hueX+hueW+1, hcy+2, 0xFFFFFFFF);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int)(h*6)%6; float f = h*6-(int)(h*6);
        int p = (int)(255*v*(1-s)), q = (int)(255*v*(1-f*s)), t = (int)(255*v*(1-(1-f)*s)), vi = (int)(255*v);
        return switch (i) { case 0 -> 0xFF000000|(vi<<16)|(t<<8)|p; case 1 -> 0xFF000000|(q<<16)|(vi<<8)|p; case 2 -> 0xFF000000|(p<<16)|(vi<<8)|t; case 3 -> 0xFF000000|(p<<16)|(q<<8)|vi; case 4 -> 0xFF000000|(t<<16)|(p<<8)|vi; default -> 0xFF000000|(vi<<16)|(p<<8)|q; };
    }

    private void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) { ctx.fill(x1,y1,x2,y1+1,c); ctx.fill(x1,y2-1,x2,y2,c); ctx.fill(x1,y1,x1+1,y2,c); ctx.fill(x2-1,y1,x2,y2,c); }
    private void setColor(int c) { currentColor = c; ColorHistory.getInstance().addColor(c); if (hexInput != null) hexInput.setText(String.format("#%06X", c & 0xFFFFFF)); }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int old = zoom; if (va > 0 && zoom < 40) zoom += 2; else if (va < 0 && zoom > 2) zoom -= 2;
        if (zoom != old) { double rx = (mx-canvasScreenX)/(double)(canvas.getWidth()*old), ry = (my-canvasScreenY)/(double)(canvas.getHeight()*old); recalcCanvasPos(); canvasScreenX=(int)(mx-rx*canvas.getWidth()*zoom); canvasScreenY=(int)(my-ry*canvas.getHeight()*zoom); panOffsetX=canvasScreenX-canvasBaseX; panOffsetY=canvasScreenY-canvasBaseY; }
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (showColorPicker && btn == 0 && handlePickerClick(mx, my)) return true;
        if (btn == 0 && handlePaletteClick(mx, my)) return true;
        if (btn == 0 && handleHistoryClick(mx, my)) return true;
        if (btn == 1) { isPanning = true; panStartMouseX = mx; panStartMouseY = my; panStartOffsetX = panOffsetX; panStartOffsetY = panOffsetY; return true; }
        if (isInUIRegion(mx, my)) return false;
        if (btn == 0) { int px = (int)((mx-canvasScreenX)/zoom), py = (int)((my-canvasScreenY)/zoom); if (px>=0 && px<canvas.getWidth() && py>=0 && py<canvas.getHeight()) { handleCanvasClick(px, py, btn); return true; } }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) { if (btn == 1) { isPanning = false; return true; } return super.mouseReleased(mx, my, btn); }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 1 && isPanning) { panOffsetX = panStartOffsetX+(int)(mx-panStartMouseX); panOffsetY = panStartOffsetY+(int)(my-panStartMouseY); canvasScreenX = canvasBaseX+panOffsetX; canvasScreenY = canvasBaseY+panOffsetY; return true; }
        if (showColorPicker && btn == 0 && handlePickerClick(mx, my)) return true;
        if (isInUIRegion(mx, my)) return super.mouseDragged(mx, my, btn, dx, dy);
        int px = (int)((mx-canvasScreenX)/zoom), py = (int)((my-canvasScreenY)/zoom);
        if (px>=0 && px<canvas.getWidth() && py>=0 && py<canvas.getHeight()) { if (currentTool == EditorTool.PENCIL) canvas.drawPixel(px, py, currentColor); else if (currentTool == EditorTool.ERASER) canvas.erasePixel(px, py); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    private boolean handlePickerClick(double mx, double my) {
        int cpX = this.width/2-80, cpY = this.height-90, svW = 120, svH = 80, hueX = cpX+svW+5, hueW = 20;
        if (mx>=cpX&&mx<cpX+svW&&my>=cpY&&my<cpY+svH) { pickerSat = Math.max(0, Math.min(1, (float)(mx-cpX)/(svW-1))); pickerVal = Math.max(0, Math.min(1, 1f-(float)(my-cpY)/(svH-1))); setColor(hsvToRgb(pickerHue, pickerSat, pickerVal)); return true; }
        if (mx>=hueX&&mx<hueX+hueW&&my>=cpY&&my<cpY+svH) { pickerHue = Math.max(0, Math.min(1, (float)(my-cpY)/(svH-1))); setColor(hsvToRgb(pickerHue, pickerSat, pickerVal)); return true; }
        return false;
    }

    private boolean handleHistoryClick(double mx, double my) {
        ColorHistory hist = ColorHistory.getInstance(); if (hist.size() == 0) return false;
        int px0 = this.width-115; int sy = 30+((PALETTE.length+4)/5)*22+92; int cols = 5, cs = 18;
        List<Integer> colors = hist.getColors();
        for (int i = 0; i < colors.size(); i++) { int c = i%cols, r = i/cols; int px = px0+c*(cs+2), py = sy+r*(cs+2); if (mx>=px&&mx<px+cs&&my>=py&&my<py+cs) { currentColor = colors.get(i); hexInput.setText(String.format("#%06X", currentColor & 0xFFFFFF)); return true; } }
        return false;
    }

    private void handleCanvasClick(int px, int py, int btn) {
        switch (currentTool) {
            case PENCIL -> { canvas.saveSnapshot(); canvas.drawPixel(px, py, btn == 1 ? 0 : currentColor); ColorHistory.getInstance().addColor(currentColor); }
            case ERASER -> { canvas.saveSnapshot(); canvas.erasePixel(px, py); }
            case FILL -> { canvas.saveSnapshot(); canvas.floodFill(px, py, currentColor); ColorHistory.getInstance().addColor(currentColor); }
            case EYEDROPPER -> { currentColor = canvas.pickColor(px, py); hexInput.setText(String.format("#%08X", currentColor)); }
            case LINE -> { if (!lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; } else { canvas.saveSnapshot(); canvas.drawLine(lineStartX, lineStartY, px, py, currentColor); lineFirstClick = false; ColorHistory.getInstance().addColor(currentColor); } }
        }
    }

    private boolean handlePaletteClick(double mx, double my) {
        int px0 = this.width-115, py0 = 30, cs = 20, cols = 5;
        for (int i = 0; i < PALETTE.length; i++) { int c = i%cols, r = i/cols; int px = px0+c*(cs+2), py = py0+r*(cs+2); if (mx>=px&&mx<px+cs&&my>=py&&my<py+cs) { setColor(PALETTE[i]); return true; } }
        return false;
    }

    @Override public boolean keyPressed(int kc, int sc, int m) {
        if (TextureEditorClient.getUndoKey().matchesKey(kc, sc)) { canvas.undo(); return true; }
        if (TextureEditorClient.getRedoKey().matchesKey(kc, sc)) { canvas.redo(); return true; }
        if (kc == GLFW.GLFW_KEY_G && !hexInput.isFocused()) { showGrid = !showGrid; return true; }
        if (kc == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(kc, sc, m);
    }

    private void resetItem() {
        if (originalPixels == null) return; canvas.saveSnapshot();
        for (int x = 0; x < canvas.getWidth(); x++) for (int y = 0; y < canvas.getHeight(); y++) canvas.setPixel(x, y, originalPixels[x][y]);
        if (textureId != null) TextureManager.getInstance().removeTexture(textureId);
        applyLive();
    }

    private void resetAll() {
        TextureManager.getInstance().clear(); MinecraftClient.getInstance().reloadResources();
        if (originalPixels != null) { canvas.saveSnapshot(); for (int x = 0; x < canvas.getWidth(); x++) for (int y = 0; y < canvas.getHeight(); y++) canvas.setPixel(x, y, originalPixels[x][y]); }
    }

    private void applyLive() {
        if (spriteId == null || canvas == null) return;
        MinecraftClient.getInstance().execute(() ->
                TextureManager.getInstance().applyLive(spriteId, canvas.getPixels(), canvas.getWidth(), canvas.getHeight()));
    }

    private static int[][] copyPixels(int[][] s, int w, int h) { int[][] c = new int[w][h]; for (int x = 0; x < w; x++) System.arraycopy(s[x], 0, c[x], 0, h); return c; }
    @Override public boolean shouldPause() { return false; }
}
