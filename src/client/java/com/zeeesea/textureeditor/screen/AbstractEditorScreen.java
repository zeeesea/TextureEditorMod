package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.ColorHistory;
import com.zeeesea.textureeditor.editor.EditorTool;
import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.util.ColorPalette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Redesigned base class for all texture editor screens.
 * Features collapsible left/right panels with tabs.
 * Left: General (actions) | Tools
 * Right: Color (picker + palette) | Layers
 */
public abstract class AbstractEditorScreen extends Screen {

    // ── Texture data ──────────────────────────────────────────────────────────
    protected Identifier spriteId;
    protected Identifier textureId;
    protected PixelCanvas canvas;
    protected int[][] originalPixels;

    // ── Tint ──────────────────────────────────────────────────────────────────
    protected int blockTint = -1;
    protected boolean isTinted = false;

    // ── Editor state ─────────────────────────────────────────────────────────
    protected EditorTool currentTool = EditorTool.PENCIL;
    protected int currentColor = 0xFFFF0000;
    protected int zoom = 12;
    protected boolean showGrid = true;
    protected int toolSize = 1;
    protected boolean previewingOriginal = false;

    // ── Canvas position ───────────────────────────────────────────────────────
    protected int canvasBaseX, canvasBaseY;
    protected int panOffsetX = 0, panOffsetY = 0;
    protected int canvasScreenX, canvasScreenY;
    protected boolean isPanning = false;
    private double panStartMouseX, panStartMouseY;
    private int panStartOffsetX, panStartOffsetY;

    // ── Line tool ─────────────────────────────────────────────────────────────
    private int lineStartX = -1, lineStartY = -1;
    private boolean lineFirstClick = false;
    // ── Rectangle tool ─────────────────────────────────────────────────-------
    private int rectStartX = -1, rectStartY = -1;
    private boolean rectFirstClick = false;
    // Preview coordinates for live previewing of line/rectangle tools
    private int previewEndX = -1, previewEndY = -1;
    // Track mouse-down state to distinguish click vs drag
    private boolean leftDown = false;
    private int downPx = -1, downPy = -1; // pixel coords where mouse was pressed
    private boolean movedSinceDown = false;
    // Has the user started dragging to adjust the preview shape after setting start point
    private boolean draggingShape = false;
    // If the shape start was created via a confirmed click (press+release without movement)
    private boolean shapeStartConfirmed = false;
    // Whether the current shape start was created on press (so drag should behave immediately)
    private boolean startCreatedOnPress = false;

    // Color picker drag-capture flags (keep interaction captured while dragging inside picker)
    private boolean pickingSv = false, pickingHue = false, pickingAlpha = false;

    // ── Panel system ──────────────────────────────────────────────────────────
    // Left panel tabs
    protected enum LeftTab { GENERAL, TOOLS }
    protected LeftTab leftTab = LeftTab.TOOLS;
    protected boolean leftOpen = true;

    // Right panel tabs
    protected enum RightTab { COLOR, LAYERS }
    protected RightTab rightTab = RightTab.COLOR;
    protected boolean rightOpen = true;

    // Session-persistent panel state (keeps panel open/tab choices for the JVM session)
    private static boolean sessionLeftOpen = true;
    private static LeftTab sessionLeftTab = LeftTab.TOOLS;
    private static boolean sessionRightOpen = true;
    private static RightTab sessionRightTab = RightTab.COLOR;

    // Panel widths (base value). Use getPanelWidth() to compute a responsive width at runtime.
    private static final int PANEL_W = 120;
    private static final int TAB_H = 22;
    private static final int TOGGLE_BTN_W = 14;

    /** Return an appropriate panel width for the current screen size / GUI scale to avoid overlap. */
    protected int getPanelWidth() {
        // Use the base PANEL_W but clamp it so panels never take more than a quarter of the screen
        int max = Math.max(80, this.width / 4);
        return Math.min(PANEL_W, max);
    }

    /** Allowed maximum zoom for the current layout: permit zoom up to getMaxZoom() but also consider very large desired zoom. */
    protected int getAllowedMaxZoom() {
        if (canvas == null) return getMaxZoom();
        int lw = (leftOpen ? getPanelWidth() : TOGGLE_BTN_W) + TOGGLE_BTN_W;
        int rw = (rightOpen ? getPanelWidth() : TOGGLE_BTN_W) + TOGGLE_BTN_W;
        int availW = this.width - lw - rw - 20;
        int availH = this.height - 60;
        int fitZoom = Math.min(availW / Math.max(1, canvas.getWidth()), availH / Math.max(1, canvas.getHeight()));
        if (fitZoom < getMinZoom()) fitZoom = getMinZoom();
        return Math.max(getMaxZoom(), fitZoom);
    }

    // ── Color picker state ────────────────────────────────────────────────────
    private float pickerHue = 0f, pickerSat = 1f, pickerVal = 1f;
    private float pickerAlpha = 1f;
    private float cachedPickerHue = -1f;
    // Shrink the picker widths so the controls fit inside the panel width
    private static final int PICKER_SV_W = 64, PICKER_SV_H = 80;
    private static final int HUE_W = 8, ALPHA_W = 8;
    private net.minecraft.client.texture.NativeImageBackedTexture pickerSvTexture = null;
    private net.minecraft.client.texture.NativeImageBackedTexture pickerHueTexture = null;
    private net.minecraft.client.texture.NativeImageBackedTexture pickerAlphaTexture = null;
    private boolean pickerHueBarBuilt = false;
    private boolean pickerAlphaBarBuilt = false;
    // Scrolling state for right-panel content (palette/history and layers)
    private int paletteScrollY = 0;
    private int layerScrollY = 0;
    private static final int SCROLL_STEP = 18;
    // Left panel scroll state (for General / Tools long content)
    private int leftPanelScrollY = 0;
    private int leftPanelContentHeight = 0;
    private static final Identifier PICKER_SV_ID   = Identifier.of("textureeditor", "picker_sv");
    private static final Identifier PICKER_HUE_ID  = Identifier.of("textureeditor", "picker_hue");
    private static final Identifier PICKER_ALPHA_ID = Identifier.of("textureeditor", "picker_alpha");

    // ── Hex input ─────────────────────────────────────────────────────────────
    protected TextFieldWidget hexInput;
    // When programmatically updating the hex input we suppress its change callback
    private boolean suppressHexCallback = false;
    // Size slider reference so we can programmatically update it when toolSize changes
    private net.minecraft.client.gui.widget.SliderWidget sizeSlider = null;
    // When programmatically updating the size slider, suppress its applyValue callback
    private boolean suppressSizeSliderCallback = false;
    // (No separate alpha slider widget — the alpha bar at the right of the picker
    // is the single alpha control used.)

    // ── Palette ───────────────────────────────────────────────────────────────
    protected static final int[] PALETTE = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF,
            0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF80FF00,
            0xFF00FF00, 0xFF00FF80, 0xFF00FFFF, 0xFF0080FF,
            0xFF0000FF, 0xFF8000FF, 0xFFFF00FF, 0xFFFF0080,
            0xFF800000, 0xFF804000, 0xFF808000, 0xFF408000,
            0xFF008000, 0xFF008040, 0xFF008080, 0xFF004080,
            0xFF000080, 0xFF400080, 0xFF800080, 0xFF800040,
            0xFFFFB3B3, 0xFFFFD9B3, 0xFFFFFFB3, 0xFFB3FFB3, 0xFFB3FFFF
    };

    // ── Canvas texture cache ──────────────────────────────────────────────────
    private net.minecraft.client.texture.NativeImageBackedTexture canvasTexture = null;
    private static final Identifier CANVAS_TEX_ID = Identifier.of("textureeditor", "canvas_preview");
    private long lastCanvasHash = 0;
    private boolean canvasTextureDirty = true;

    // ── Drawing interpolation ─────────────────────────────────────────────────
    private int lastDrawX = -1, lastDrawY = -1;

    // ── Quick select wheel ────────────────────────────────────────────────────
    protected QuickSelectWheel quickSelectWheel = new QuickSelectWheel(50);

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    protected AbstractEditorScreen(Text title) {
        super(title);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Abstract / overrideable
    // ─────────────────────────────────────────────────────────────────────────

    protected abstract void loadTexture();
    protected abstract void applyLive();
    protected abstract void resetCurrent();
    protected abstract String getEditorTitle();

    protected Screen getBackScreen()                                           { return null; }
    protected int    addExtraLeftGeneralButtons(int y, int x, int w, int bh)  { return y; }
    protected int    addExtraToolButtons(int y, int x, int w, int bh)         { return y; }
    protected void   renderExtra(DrawContext ctx, int mx, int my)             {}
    protected boolean handleExtraClick(double mx, double my, int btn)         { return false; }
    protected boolean handleExtraRelease(double mx, double my, int btn)       { return false; }
    protected boolean handleExtraDrag(double mx, double my, int btn, double dx, double dy) { return false; }
    protected boolean handleExtraScroll(double mx, double my, double ha, double va) { return false; }

    protected int  getBackgroundColor() { return ColorPalette.INSTANCE.EDITOR_BACKGROUND; }
    // Increased default max zoom to allow deep zooming into very small textures
    protected int  getMaxZoom()         { return 4096; }
    protected int  getMinZoom()         { return 1; }
    protected int  getZoomStep()        { return 1; }
    protected boolean usesTint()        { return isTinted; }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    protected int getGuiScale() {
        return (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    protected int getToolButtonHeight() {
        int base = getGuiScale() >= 4 ? 18 : 20;
        // scale down further on very small screens
        int scaled = Math.max(12, Math.min(24, base - Math.max(0, (4 - getGuiScale()))));
        return scaled;
    }

    /** Compute a responsive tool/button width so UI adapts to resolution */
    protected int getToolButtonWidth(int panelInnerWidth) {
        // default wide button is panelInnerWidth; small buttons (half/third) computed by caller
        int min = Math.max(36, panelInnerWidth / 6);
        int max = Math.max(80, panelInnerWidth);
        return Math.max(min, Math.min(panelInnerWidth, max));
    }

    /** Utility to return the inner X of a right panel (4px padding) for given panel left x. */
    protected int getPanelInnerX(int panelLeftX) { return panelLeftX + 4; }

    private String getVarLabel() {
        int pct = (int)(ModSettings.getInstance().variationPercent * 100);
        if (pct <= 0) return "Variation: OFF";
        return "Variation: " + pct + "%";
    }

    // Deterministic variation for preview: adjust RGB by a pseudo-random factor based on coordinates
    private int applyPreviewVariation(int baseColor, int x, int y, float var) {
        int a = (baseColor >> 24) & 0xFF;
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        // simple hash
        int h = (x * 73856093) ^ (y * 19349663);
        float rand = ((h & 0xFFFF) / (float)0xFFFF) * 2f - 1f; // [-1..1]
        float offset = rand * var;
        r = clamp((int)(r + r * offset), 0, 255);
        g = clamp((int)(g + g * offset), 0, 255);
        b = clamp((int)(b + b * offset), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    protected boolean showFaceButton() { return getGuiScale() <= 4; }

    /** Effective left panel width (0 if closed) */
    private int leftW() { return leftOpen ? getPanelWidth() : TOGGLE_BTN_W; }
    /** Effective right panel width (0 if closed) */
    private int rightW() { return rightOpen ? getPanelWidth() : TOGGLE_BTN_W; }

    // ─────────────────────────────────────────────────────────────────────────
    // Tint
    // ─────────────────────────────────────────────────────────────────────────

    protected int applyTint(int pixel) {
        if (!isTinted) return pixel;
        int a = (pixel >> 24) & 0xFF, r = (pixel >> 16) & 0xFF, g = (pixel >> 8) & 0xFF, b = pixel & 0xFF;
        int tr = (blockTint >> 16) & 0xFF, tg = (blockTint >> 8) & 0xFF, tb = blockTint & 0xFF;
        return (a << 24) | ((r * tr / 255) << 16) | ((g * tg / 255) << 8) | (b * tb / 255);
    }

    protected int removeTint(int color) {
        if (!isTinted) return color;
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int tr = (blockTint >> 16) & 0xFF, tg = (blockTint >> 8) & 0xFF, tb = blockTint & 0xFF;
        r = tr > 0 ? Math.min(255, r * 255 / tr) : r;
        g = tg > 0 ? Math.min(255, g * 255 / tg) : g;
        b = tb > 0 ? Math.min(255, b * 255 / tb) : b;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void setTint(int tint) {
        this.blockTint = tint | 0xFF000000;
        this.isTinted = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void removed() {
        super.removed();
        if (canvasTexture    != null) { canvasTexture.close();    canvasTexture    = null; }
        if (pickerSvTexture  != null) { pickerSvTexture.close();  pickerSvTexture  = null; }
        if (pickerHueTexture != null) { pickerHueTexture.close(); pickerHueTexture = null; }
        if (pickerAlphaTexture != null) { pickerAlphaTexture.close(); pickerAlphaTexture = null; }
    }

    @Override
    protected void init() {
        canvasTextureDirty = true;
        pickerHueBarBuilt  = false;
        pickerAlphaBarBuilt = false;

        loadTexture();
        if (canvas == null) { canvas = new PixelCanvas(16, 16); originalPixels = new int[16][16]; }

        // Restore panel open/tab state from session (keeps user's UI choices while the game is running)
        this.leftOpen = sessionLeftOpen;
        this.leftTab = sessionLeftTab;
        this.rightOpen = sessionRightOpen;
        this.rightTab = sessionRightTab;

        recalcCanvasPos();

        ModSettings s = ModSettings.getInstance();
        currentTool = EditorTool.getToolByName(s.defaultTool);
        ColorHistory.setMaxHistory(s.colorHistorySize);
        showGrid = s.gridOnByDefault;

        buildWidgets();
    }

    private void buildWidgets() {
        ModSettings s = ModSettings.getInstance();
        int bh = getToolButtonHeight();
        // reset computed left-panel content height before constructing
        leftPanelContentHeight = 0;

        // ── LEFT panel toggle button ──────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal(leftOpen ? "<" : ">"),
                btn -> { leftOpen = !leftOpen; sessionLeftOpen = leftOpen; clearChildren(); recalcCanvasPos(); buildWidgets(); }
        ).position(leftOpen ? getPanelWidth() : 0, 0).size(TOGGLE_BTN_W, bh).build());

        if (leftOpen) {
            // Tab buttons
            addDrawableChild(ButtonWidget.builder(Text.literal("General"),
                            btn -> { leftTab = LeftTab.GENERAL; sessionLeftTab = leftTab; clearChildren(); recalcCanvasPos(); buildWidgets(); })
                    .position(0, bh).size(getPanelWidth() / 2, TAB_H).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Tools"),
                            btn -> { leftTab = LeftTab.TOOLS; sessionLeftTab = leftTab; clearChildren(); recalcCanvasPos(); buildWidgets(); })
                    .position(getPanelWidth() / 2, bh).size(getPanelWidth() / 2, TAB_H).build());

            int py = bh + TAB_H + 4;

            if (leftTab == LeftTab.GENERAL) {
                // General tab
                py = buildGeneralTab(py, 4, getPanelWidth() - 8, bh, s);
                // record content height for scrolling
                leftPanelContentHeight = Math.max(leftPanelContentHeight, py - (bh + TAB_H + 4));
            } else {
                // Tools tab
                py = buildToolsTab(py, 4, getPanelWidth() - 8, bh, s);
                leftPanelContentHeight = Math.max(leftPanelContentHeight, py - (bh + TAB_H + 4));
            }
        }

        // ── RIGHT panel toggle button ─────────────────────────────────────────
        int rtx = this.width - rightW();
        addDrawableChild(ButtonWidget.builder(
                Text.literal(rightOpen ? ">" : "<"),
                btn -> { rightOpen = !rightOpen; sessionRightOpen = rightOpen; clearChildren(); recalcCanvasPos(); buildWidgets(); }
        ).position(rtx, 0).size(TOGGLE_BTN_W, bh).build());

        if (rightOpen) {
            int rpx = this.width - getPanelWidth();
            // Tab buttons
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.picker"),
                            btn -> { rightTab = RightTab.COLOR; sessionRightTab = rightTab; clearChildren(); recalcCanvasPos(); buildWidgets(); })
                    .position(rpx, bh).size(getPanelWidth() / 2, TAB_H).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.layers"),
                            btn -> { rightTab = RightTab.LAYERS; sessionRightTab = rightTab; clearChildren(); recalcCanvasPos(); buildWidgets(); })
                    .position(rpx + getPanelWidth() / 2, bh).size(getPanelWidth() / 2, TAB_H).build());

            if (rightTab == RightTab.COLOR) {
                buildColorTab(rpx, bh + TAB_H + 4, PANEL_W - 8, bh);
            }
            // Layers tab has no persistent buttons (all drawn manually)
        }

        // ── Top bar: title + close ────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("X"),
                        btn -> { if (s.autoApplyLive) applyLive(); this.close(); })
                .position(this.width - rightW() - 24, 0).size(22, bh).build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // General tab
    // ─────────────────────────────────────────────────────────────────────────

    private int buildGeneralTab(int py, int px, int w, int bh, ModSettings s) {
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("textureeditor.button.apply_live").styled(st -> st.withColor(ColorPalette.INSTANCE.STATUS_OK)),
                        btn -> applyLive())
                .position(px, py - leftPanelScrollY).size(w, bh).build());
        py += bh + 2;

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("textureeditor.button.export_pack").styled(st -> st.withColor(ColorPalette.INSTANCE.TAB_UNDERLINE)),
                        btn -> MinecraftClient.getInstance().setScreen(new ExportScreen(this)))
                .position(px, py - leftPanelScrollY).size(w, bh).build());
        py += bh + 2;

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("textureeditor.button.browse").styled(st -> st.withColor(ColorPalette.INSTANCE.TEXT_NORMAL)),
                        btn -> {
                            if (s.autoApplyLive) applyLive();
                            Screen bs = getBackScreen();
                            MinecraftClient.getInstance().setScreen(bs != null ? bs : new BrowseScreen());
                        })
                .position(px, py - leftPanelScrollY).size(w, bh).build());
        py += bh + 8;

        // Separator
        py += 2;
        addDrawableChild(ButtonWidget.builder(
                                Text.literal(getResetCurrentLabel()).styled(st -> st.withColor(ColorPalette.INSTANCE.TAB_UNDERLINE)),
                        btn -> resetCurrent())
                .position(px, py - leftPanelScrollY).size(w, bh).build());
        py += bh + 2;

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("textureeditor.button.reset_all").styled(st -> st.withColor(ColorPalette.INSTANCE.TEXT_ALERT)),
                        btn -> MinecraftClient.getInstance().setScreen(new ConfirmResetAllScreen(this)))
                .position(px, py - leftPanelScrollY).size(w, bh).build());
        py += bh + 8;

        // Extra buttons from subclass (face selector etc.)
        py = addExtraLeftGeneralButtons(py, px, w, bh);

        return py;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tools tab
    // ─────────────────────────────────────────────────────────────────────────

    private int buildToolsTab(int py, int px, int w, int bh, ModSettings s) {
        // Tool buttons (2 per row to save space)
        EditorTool[] tools = EditorTool.values();
        for (int i = 0; i < tools.length; i += 2) {
            int hw = (w - 2) / 2;
            final EditorTool t1 = tools[i];
            String h1 = s.showToolHints ? s.getKeyName(t1.name().toLowerCase()) : "";
            String l1 = t1.getDisplayName() + (h1.isEmpty() ? "" : " (" + h1 + ")");
            addDrawableChild(ButtonWidget.builder(Text.literal(l1), btn -> currentTool = t1)
                    .position(px, py - leftPanelScrollY).size(hw, bh).build());
            if (i + 1 < tools.length) {
                final EditorTool t2 = tools[i + 1];
                String h2 = s.showToolHints ? s.getKeyName(t2.name().toLowerCase()) : "";
                String l2 = t2.getDisplayName() + (h2.isEmpty() ? "" : " (" + h2 + ")");
                addDrawableChild(ButtonWidget.builder(Text.literal(l2), btn -> currentTool = t2)
                        .position(px + hw + 2, py - leftPanelScrollY).size(hw, bh).build());
            }
            py += bh + 2;
        }
        py += 4;

        // Undo / Redo
        int hw = (w - 2) / 2;
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.undo"), btn -> canvas.undo())
                .position(px, py - leftPanelScrollY).size(hw, bh).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.redo"), btn -> canvas.redo())
                .position(px + hw + 2, py - leftPanelScrollY).size(hw, bh).build());
        py += bh + 4;

        // Grid toggle
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.grid"), btn -> showGrid = !showGrid)
                .position(px, py - leftPanelScrollY).size(w, bh).build());
        py += bh + 2;


        // Tool size slider
        sizeSlider = new net.minecraft.client.gui.widget.SliderWidget(
                px, py, w, bh, Text.literal("Size: " + toolSize + "px"), (toolSize - 1) / 9.0) {
            @Override protected void updateMessage() { setMessage(Text.literal("Size: " + toolSize + "px")); }
            @Override protected void applyValue() {
                if (suppressSizeSliderCallback) return;
                toolSize = (int)(value * 9) + 1;
            }
        };
        sizeSlider.setX(px); sizeSlider.setY(py - leftPanelScrollY); addDrawableChild(sizeSlider);
        py += bh + 4;

        // Variation percent slider (0..100). When value == 0 show "OFF" instead of "0%".
        net.minecraft.client.gui.widget.SliderWidget varSlider = new net.minecraft.client.gui.widget.SliderWidget(px, py, w, bh, Text.literal(getVarLabel()), ModSettings.getInstance().variationPercent) {
            @Override protected void updateMessage() { setMessage(Text.literal(getVarLabel())); }
            @Override protected void applyValue() { ModSettings.getInstance().variationPercent = (float)this.value; ModSettings.getInstance().save(); }
        };
        varSlider.setX(px); varSlider.setY(py - leftPanelScrollY); addDrawableChild(varSlider);
        py += bh + 4;

        // Helper to display label for variation slider
        // (kept as a local method below via lambda-like closure through a private helper)

        // Extra tool buttons from subclass
        py = addExtraToolButtons(py, px, w, bh);

        return py;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Color tab — builds persistent widgets (hex input)
    // ─────────────────────────────────────────────────────────────────────────

    private void buildColorTab(int rpx, int py, int pw, int bh) {
        // Position the hex input so it sits next to the current color swatch
        int innerX = rpx + 4;
        int svY = py; // SV starts at py
        int swatchY = svY + PICKER_SV_H + 6;
        int swatchW = 28;
        int innerW = pw - 8;
        int hexX = innerX + swatchW + 6;
        int hexW = Math.max(40, innerW - swatchW - 10);

        hexInput = new TextFieldWidget(this.textRenderer, hexX, swatchY, hexW, bh, Text.literal("Hex"));
        hexInput.setMaxLength(9);
        hexInput.setText(colorToHex(currentColor));
        hexInput.setChangedListener(text -> {
            // Ignore callbacks triggered by programmatic updates
            if (suppressHexCallback) return;
            // Run parser on the client executor to break direct re-entrant
            // setText() -> onChanged() -> setText() recursion that can
            // otherwise cause a StackOverflowError in some environments.
            MinecraftClient.getInstance().execute(() -> {
                if (hexInput == null) return;
                if (suppressHexCallback) return;
                try {
                    String hex = text.startsWith("#") ? text.substring(1) : text;
                    if (hex.length() == 6 || hex.length() == 8) {
                        int parsed = (int) Long.parseLong(hex.length() == 6 ? "FF" + hex : hex, 16);
                        // Only update when the parsed color differs to avoid needless updates
                        if (parsed != currentColor) setColor(parsed, false);
                    }
                } catch (NumberFormatException ignored) {}
            });
        });
        addDrawableChild(hexInput);
        // No extra alpha slider widget; the alpha bar on the right is interactive.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Canvas texture cache
    // ─────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    // Canvas position
    // ─────────────────────────────────────────────────────────────────────────

    protected void recalcCanvasPos() {
        int lw = leftW() + TOGGLE_BTN_W;
        int rw = rightW() + TOGGLE_BTN_W;
        int availW = this.width - lw - rw - 20;
        int availH = this.height - 60;
        if (canvas != null) {
            // compute fitZoom (how large the texture can be to fit the available area)
            int fitZoom = Math.min(availW / Math.max(1, canvas.getWidth()), availH / Math.max(1, canvas.getHeight()));
            if (fitZoom < getMinZoom()) fitZoom = getMinZoom();
            // Allow zoom up to getMaxZoom(); previous logic limited zoom to fitZoom which prevented zooming to single pixels.
            int allowedMax = Math.max(fitZoom, getMaxZoom());
            zoom = Math.max(getMinZoom(), Math.min(zoom, allowedMax));
            canvasBaseX = lw + 10 + (availW - canvas.getWidth() * zoom) / 2;
            canvasBaseY = 30 + (availH - canvas.getHeight() * zoom) / 2;
        } else {
            canvasBaseX = lw + 10;
            canvasBaseY = 30;
        }
        canvasScreenX = canvasBaseX + panOffsetX;
        canvasScreenY = canvasBaseY + panOffsetY;
        canvasTextureDirty = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int bg = getBackgroundColor();
        ctx.fill(0, 0, this.width, this.height, bg);

        drawCanvas(ctx, mx, my);

        // Panel backgrounds
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        if (leftOpen) {
            ctx.fill(0, 0, PANEL_W + TOGGLE_BTN_W, this.height, pal.PANEL_DARK);
            ctx.fill(PANEL_W + TOGGLE_BTN_W, 0, PANEL_W + TOGGLE_BTN_W + 1, this.height, pal.PANEL_SEPARATOR);
            // Tab underline
            int tabLineX = leftTab == LeftTab.GENERAL ? 0 : PANEL_W / 2;
            ctx.fill(tabLineX, getToolButtonHeight() + TAB_H - 2, tabLineX + PANEL_W / 2, getToolButtonHeight() + TAB_H, pal.TAB_UNDERLINE);
        } else {
            ctx.fill(0, 0, TOGGLE_BTN_W, this.height, pal.PANEL_DARK);
        }

        if (rightOpen) {
            int panelW = getPanelWidth();
            int rpx = this.width - panelW - TOGGLE_BTN_W;
            ctx.fill(rpx, 0, this.width, this.height, pal.PANEL_DARK);
            ctx.fill(rpx - 1, 0, rpx, this.height, pal.PANEL_SEPARATOR);
            int tabLineX = this.width - panelW + (rightTab == RightTab.COLOR ? 0 : panelW / 2);
            ctx.fill(tabLineX, getToolButtonHeight() + TAB_H - 2, tabLineX + panelW / 2, getToolButtonHeight() + TAB_H, pal.TAB_UNDERLINE);
            // Draw color/layer tab content
            if (rightTab == RightTab.COLOR) drawColorTabContent(ctx, mx, my, this.width - panelW, getToolButtonHeight() + TAB_H + 4, panelW);
            else drawLayerTabContent(ctx, mx, my, this.width - panelW, getToolButtonHeight() + TAB_H + 4, panelW);
        } else {
            ctx.fill(this.width - TOGGLE_BTN_W, 0, this.width, this.height, pal.PANEL_DARK);
        }

        // Title bar
        ctx.fill(leftW() + TOGGLE_BTN_W, 0, this.width - rightW() - TOGGLE_BTN_W, getToolButtonHeight(), pal.TITLE_BAR_BG);
        boolean shadow = pal.TITLE_TEXT == 0xFF000000;
        ctx.drawText(textRenderer, getEditorTitle(), leftW() + TOGGLE_BTN_W + 4, 5, pal.TITLE_TEXT, shadow);

        // Status bar
        int statusY = this.height - 14;
        ctx.fill(leftW() + TOGGLE_BTN_W, statusY, this.width - rightW() - TOGGLE_BTN_W, this.height, pal.STATUS_BAR_BG);
        String status = "Tool: " + currentTool.getDisplayName() + " | Size: " + toolSize + "px"
                + (canvas != null ? " | " + canvas.getWidth() + "×" + canvas.getHeight() : "");
        if (canvas != null) {
            int hx = (mx - canvasScreenX) / Math.max(1, zoom);
            int hy = (my - canvasScreenY) / Math.max(1, zoom);
            if (hx >= 0 && hx < canvas.getWidth() && hy >= 0 && hy < canvas.getHeight()) {
                status += " | x:" + hx + ", y:" + hy;
            }
        }
        ctx.drawText(textRenderer, status, leftW() + TOGGLE_BTN_W + 4, statusY + 3, pal.STATUS_TEXT, false);

        super.render(ctx, mx, my, delta);

        if (quickSelectWheel != null) quickSelectWheel.render(ctx, textRenderer, mx, my);

        renderExtra(ctx, mx, my);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Color tab content (drawn every frame)
    // ─────────────────────────────────────────────────────────────────────────

    private void drawColorTabContent(DrawContext ctx, int mx, int my, int rpx, int py, int pw) {
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        int bh = getToolButtonHeight();
        int innerX = getPanelInnerX(rpx);
        int innerW = pw - 8;

        // ── SV square
        int svX = innerX;
        int svY = py;

        // Compute dynamic widths: split the available extra space between hue & alpha
        int avail = Math.max(0, innerW - PICKER_SV_W - 8);
        int hueW = Math.max(HUE_W, avail / 2);
        int alphaW = Math.max(ALPHA_W, avail - hueW);

        int hueX = svX + PICKER_SV_W + 4;
        int hueY = svY;
        int alphaX = hueX + hueW + 4;
        int alphaY = svY;

        // Prepare textures (build/update)
        if (cachedPickerHue != pickerHue || pickerSvTexture == null) {
            cachedPickerHue = pickerHue;
            var img = new net.minecraft.client.texture.NativeImage(PICKER_SV_W, PICKER_SV_H, false);
            for (int y = 0; y < PICKER_SV_H; y++) {
                float v = 1f - y / (float)(PICKER_SV_H - 1);
                for (int x = 0; x < PICKER_SV_W; x++) {
                    float s = x / (float)(PICKER_SV_W - 1);
                    img.setColorArgb(x, y, hsvToArgb(pickerHue, s, v, 1f));
                }
            }
            if (pickerSvTexture != null) { pickerSvTexture.setImage(img); pickerSvTexture.upload(); }
            else {
                pickerSvTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "picker_sv", img);
                MinecraftClient.getInstance().getTextureManager().registerTexture(PICKER_SV_ID, pickerSvTexture);
            }
        }
        if (!pickerHueBarBuilt || pickerHueTexture == null || pickerHueTexture.getGlTexture().getWidth(0) != hueW) {
            var himg = new net.minecraft.client.texture.NativeImage(hueW, PICKER_SV_H, false);
            for (int y = 0; y < PICKER_SV_H; y++) {
                int c = hsvToArgb(y / (float)(PICKER_SV_H - 1), 1f, 1f, 1f);
                for (int x = 0; x < hueW; x++) himg.setColorArgb(x, y, c);
            }
            if (pickerHueTexture != null) { pickerHueTexture.setImage(himg); pickerHueTexture.upload(); }
            else {
                pickerHueTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "picker_hue", himg);
                MinecraftClient.getInstance().getTextureManager().registerTexture(PICKER_HUE_ID, pickerHueTexture);
            }
            pickerHueBarBuilt = true;
        }
        if (!pickerAlphaBarBuilt || pickerAlphaTexture == null || pickerAlphaTexture.getGlTexture().getWidth(0) != alphaW) rebuildAlphaBar(alphaW);

        // Current swatch + hex are positioned under the picker: compute their Y now so we can
        // use it to mask the palette/history that scroll under this area.
        int swatchW = 28;
        int swatchY = svY + PICKER_SV_H + 6;
        // Build the y used by original code for palette start
        int paletteStartY = swatchY + bh + 4 + bh + 8;

        // ── Palette (centered)
        int cols = 5;
        int cs = (innerW - (cols - 1) * 1) / cols;
        int totalPaletteRows = (PALETTE.length + cols - 1) / cols;
        int paletteHeight = totalPaletteRows * (cs + 1);
        // allow scrolling only for palette + history combined; apply paletteScrollY as vertical offset
        int startY = paletteStartY - paletteScrollY;
        // picker clipping area so palette/history draw behind the picker
        int pickerLeft = svX - 1;
        int pickerRight = alphaX + alphaW + 1;
        int pickerTop = svY - 1;
        int pickerBottom = svY + PICKER_SV_H + 1;

        // Compute mask bounds: cover the entire upper area from the top of the
        // tab/content region down to just below the current swatch/hex input so
        // scrolled palette/history items disappear beneath the foreground UI.
        int maskTop = py - 2; // reach up to the top of the color tab content (near the tabs)
        int maskBottom = swatchY + bh + 1; // hide everything above the bottom of swatch/hex area

        // Calculate starting row index so we don't draw any partial rows that intersect the masked area
        int rowHeight = cs + 1;
        int firstRow = Math.max(0, (int) Math.ceil((maskBottom - startY) / (double) rowHeight));
        int startIndex = firstRow * cols;
        startIndex = Math.max(0, Math.min(startIndex, PALETTE.length));

        for (int i = startIndex; i < PALETTE.length; i++) {
            int col = i % cols, row = i / cols;
            int px2 = innerX + col * (cs + 1) + (innerW - (cols * cs + (cols - 1))) / 2; // center horizontally
            int py2 = startY + row * (cs + 1);
            // Skip any swatches that would overlap the picker's horizontal area
            if (!(px2 + cs > pickerLeft && px2 < pickerRight && py2 + cs > pickerTop && py2 < pickerBottom)) {
                ctx.fill(px2, py2, px2 + cs, py2 + cs, PALETTE[i]);
            }
            if (PALETTE[i] == currentColor) drawRectOutline(ctx, px2 - 1, py2 - 1, px2 + cs + 1, py2 + cs + 1, pal.HEADER_UNDERLINE);
        }

        int afterPaletteY = paletteStartY + paletteHeight + 4 - paletteScrollY;

        // ── Color history (centered)
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() > 0) {
            ctx.drawText(textRenderer, "History", innerX + (innerW - textRenderer.getWidth("History")) / 2, afterPaletteY, pal.STATUS_TEXT, false);
            int hy = afterPaletteY + 10;
            List<Integer> colors = hist.getColors();
            int hcs = Math.max(8, cs - 2);
            for (int i = 0; i < Math.min(colors.size(), 10); i++) {
                int col = i % cols, row = i / cols;
                int px2 = innerX + col * (hcs + 1) + (innerW - (cols * hcs + (cols - 1))) / 2;
                int py2 = hy + row * (hcs + 1);
                if (py2 >= maskBottom && !(px2 + hcs > pickerLeft && px2 < pickerRight && py2 + hcs > pickerTop && py2 < pickerBottom)) {
                    ctx.fill(px2, py2, px2 + hcs, py2 + hcs, colors.get(i));
                }
                if (colors.get(i) == currentColor) drawRectOutline(ctx, px2 - 1, py2 - 1, px2 + hcs + 1, py2 + hcs + 1, pal.HEADER_UNDERLINE);
            }
        }

        // Mask the whole upper area (picker + swatch/hex region) so scrolled
        // palette/history content is fully hidden beneath the foreground UI.
        ctx.fill(innerX, maskTop, innerX + innerW, maskBottom, pal.PANEL_DARK);

        // Draw the picker and controls on top
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PICKER_SV_ID, svX, svY, 0, 0, PICKER_SV_W, PICKER_SV_H, PICKER_SV_W, PICKER_SV_H, PICKER_SV_W, PICKER_SV_H);
        drawRectOutline(ctx, svX - 1, svY - 1, svX + PICKER_SV_W + 1, svY + PICKER_SV_H + 1, pal.PICKER_BORDER);
        // Cursor on SV
        int scx = svX + (int)(pickerSat * (PICKER_SV_W - 1));
        int scy = svY + (int)((1f - pickerVal) * (PICKER_SV_H - 1));
        ctx.fill(scx - 2, scy, scx + 3, scy + 1, pal.TEXT_NORMAL);
        ctx.fill(scx, scy - 2, scx + 1, scy + 3, pal.TEXT_NORMAL);

        // Draw hue and alpha bars on top as well
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PICKER_HUE_ID, hueX, hueY, 0, 0, hueW, PICKER_SV_H, hueW, PICKER_SV_H, hueW, PICKER_SV_H);
        drawRectOutline(ctx, hueX - 1, hueY - 1, hueX + hueW + 1, hueY + PICKER_SV_H + 1, pal.PICKER_BORDER);
        int hcy = hueY + (int)(pickerHue * (PICKER_SV_H - 1));
        ctx.fill(hueX - 1, hcy, hueX + hueW + 1, hcy + 1, 0xFFFFFFFF);

        // Alpha bar (checker background already written earlier via rebuildAlphaBar)
        // Align checker parity with absolute screen coords so the pattern doesn't shift when alphaW changes
        for (int y = 0; y < PICKER_SV_H; y += 4) for (int x2 = 0; x2 < alphaW; x2 += 4) {
            int absX = alphaX + x2;
            int absY = alphaY + y;
            int parity = ((absX / 4) + (absY / 4)) & 1;
            ctx.fill(absX, absY, absX + 4, absY + 4, (parity == 0) ? pal.CHECKER_DARK : pal.CHECKER_LIGHT);
        }
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PICKER_ALPHA_ID, alphaX, alphaY, 0, 0, alphaW, PICKER_SV_H, alphaW, PICKER_SV_H, alphaW, PICKER_SV_H);
        drawRectOutline(ctx, alphaX - 1, alphaY - 1, alphaX + alphaW + 1, alphaY + PICKER_SV_H + 1, pal.PICKER_BORDER);
        int acy = alphaY + (int)((1f - pickerAlpha) * (PICKER_SV_H - 1));
        ctx.fill(alphaX - 1, acy, alphaX + alphaW + 1, acy + 1, pal.TEXT_NORMAL);

        // Draw the current color swatch on top of the mask so it remains visible
        ctx.fill(innerX, swatchY, innerX + swatchW, swatchY + bh, currentColor);
        drawRectOutline(ctx, innerX, swatchY, innerX + swatchW, swatchY + bh, pal.SWATCH_BORDER);
    }

    private void rebuildAlphaBar(int width) {
        int r = (currentColor >> 16) & 0xFF, g = (currentColor >> 8) & 0xFF, b = currentColor & 0xFF;
        var img = new net.minecraft.client.texture.NativeImage(Math.max(1, width), PICKER_SV_H, false);
        for (int y = 0; y < PICKER_SV_H; y++) {
            float a = 1f - y / (float)(PICKER_SV_H - 1);
            int argb = ((int)(a * 255) << 24) | (r << 16) | (g << 8) | b;
            for (int x = 0; x < Math.max(1, width); x++) img.setColorArgb(x, y, argb);
        }
        if (pickerAlphaTexture != null) { pickerAlphaTexture.setImage(img); pickerAlphaTexture.upload(); }
        else {
            pickerAlphaTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "picker_alpha", img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(PICKER_ALPHA_ID, pickerAlphaTexture);
        }
        pickerAlphaBarBuilt = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer tab content
    // ─────────────────────────────────────────────────────────────────────────

    private void drawLayerTabContent(DrawContext ctx, int mx, int my, int rpx, int py, int pw) {
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        if (canvas == null) return;
        var stack = canvas.getLayerStack();
        int innerX = getPanelInnerX(rpx);
        int innerW = pw - 8;
        int rowH = 18;
        int bh = getToolButtonHeight();
        ctx.drawText(textRenderer, "Layers", innerX, py, pal.TITLE_TEXT, true);
        py += 12;
        // Compute action button geometry (two rows) so we can reserve space for the scrollable list
        int btnW = (innerW - 4) / 3;
        int actionY = py;
        int secondActionY = actionY + bh + 2;
        int listStartY = secondActionY + bh + 4;
        int contentY = listStartY - layerScrollY;
        // Compute mask bounds so top action area fully occludes the scrolling list
        // Extend mask to the absolute top so scrolled layers remain hidden all
        // the way up (prevents any layer from being visible above the action area).
        int maskTop = 0;
        int maskBottom = secondActionY + bh + 1; // bottom of the fixed action area

        // Draw the layer list first (so it scrolls underneath the fixed actions).
        // Skip any rows that would intersect the fixed action region so nothing
        // bleeds through the action buttons.
        for (int i = stack.getLayerCount() - 1; i >= 0; i--) {
            var layer = stack.getLayers().get(i);
            int rowY = contentY + (stack.getLayerCount() - 1 - i) * (rowH + 1);
            // If this row intersects the masked area, don't draw it (it will be
            // hidden by the opaque panel and fixed buttons above).
            if (rowY + rowH > maskTop && rowY < maskBottom) continue;
            boolean active = i == stack.getActiveIndex();
            ctx.fill(innerX, rowY, innerX + innerW, rowY + rowH, active ? pal.CELL_BG_HOVER : pal.CELL_BG);
            if (active) drawRectOutline(ctx, innerX, rowY, innerX + innerW, rowY + rowH, pal.ACTIVE_LAYER_BORDER);
            String eye = layer.isVisible() ? "●" : "○";
            ctx.drawText(textRenderer, eye, innerX + 2, rowY + 5, layer.isVisible() ? pal.STATUS_OK : pal.TEXT_SUBTLE, false);
            String name = layer.getName().length() > 10 ? layer.getName().substring(0, 10) + ".." : layer.getName();
            ctx.drawText(textRenderer, name, innerX + 14, rowY + 5, pal.TEXT_NORMAL, false);
        }
        // Draw an opaque panel area behind the fixed action buttons so the scrolling
        // layer list is fully hidden when it scrolls beneath the buttons.
        ctx.fill(innerX, actionY, innerX + innerW, secondActionY + bh, pal.PANEL_DARK);
        // Now draw the fixed action buttons on top so they occlude the scrolling list beneath
        // Row 1: Add, Del, Up
        ctx.fill(innerX, actionY, innerX + btnW, actionY + bh, pal.CELL_BG); ctx.drawText(textRenderer, "+ Add", innerX + 2, actionY + 4, pal.TEXT_NORMAL, false);
        ctx.fill(innerX + btnW + 2, actionY, innerX + 2 * btnW + 2, actionY + bh, pal.CELL_BG); ctx.drawText(textRenderer, "- Del", innerX + btnW + 4, actionY + 4, pal.TEXT_SUBTLE, false);
        ctx.fill(innerX + 2 * btnW + 4, actionY, innerX + innerW, actionY + bh, pal.CELL_BG); ctx.drawText(textRenderer, "▲ Up", innerX + 2 * btnW + 6, actionY + 4, pal.TEXT_NORMAL, false);
        // Row 2: Down, Merge, Copy
        ctx.fill(innerX, secondActionY, innerX + btnW, secondActionY + bh, pal.CELL_BG); ctx.drawText(textRenderer, "▼ Dn", innerX + 2, secondActionY + 4, pal.TEXT_NORMAL, false);
        ctx.fill(innerX + btnW + 2, secondActionY, innerX + 2 * btnW + 2, secondActionY + bh, pal.CELL_BG); ctx.drawText(textRenderer, "Merge", innerX + btnW + 4, secondActionY + 4, pal.TEXT_NORMAL, false);
        ctx.fill(innerX + 2 * btnW + 4, secondActionY, innerX + innerW, secondActionY + bh, pal.CELL_BG); ctx.drawText(textRenderer, "Copy", innerX + 2 * btnW + 6, secondActionY + 4, pal.TEXT_NORMAL, false);
        // No fixed bottom buttons anymore; actions are at top and the layer list scrolls underneath
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Canvas drawing
    // ─────────────────────────────────────────────────────────────────────────

    private void drawCanvas(DrawContext ctx, int mx, int my) {
        if (canvas == null) return;
        int w = canvas.getWidth(), h = canvas.getHeight();
        // Use a safe effective zoom for divisions (zoom should always be >= 1)
        int effectiveZoom = Math.max(1, zoom);
        int visMinX = Math.max(0, (-canvasScreenX) / effectiveZoom);
        int visMinY = Math.max(0, (-canvasScreenY) / effectiveZoom);
        int visMaxX = Math.min(w, (this.width - canvasScreenX + effectiveZoom - 1) / effectiveZoom);
        int visMaxY = Math.min(h, (this.height - canvasScreenY + effectiveZoom - 1) / effectiveZoom);

        if (w * h > 1024) {
            long hash = canvas.getVersion();
            if (hash != lastCanvasHash || canvasTextureDirty || canvasTexture == null) {
                lastCanvasHash = hash; canvasTextureDirty = false;
                if (w > 0 && h > 0) {
                    var img = new net.minecraft.client.texture.NativeImage(w, h, false);
                    for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
                        int c = previewingOriginal && originalPixels != null ? originalPixels[x][y] : canvas.getPixel(x, y);
                        img.setColorArgb(x, y, renderPixel(c, x, y));
                    }
                    if (canvasTexture != null) {
                        var gt = canvasTexture.getGlTexture();
                        if (gt != null && (gt.getWidth(0) != w || gt.getHeight(0) != h)) { canvasTexture.close(); canvasTexture = null; }
                        else { canvasTexture.setImage(img); canvasTexture.upload(); }
                    }
                    if (canvasTexture == null) {
                        canvasTexture = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "canvas_preview", img);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(CANVAS_TEX_ID, canvasTexture);
                    }
                }
            }
            if (w * zoom > 0 && h * zoom > 0)
                ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, CANVAS_TEX_ID, canvasScreenX, canvasScreenY, 0, 0, w * zoom, h * zoom, w, h, w, h);
        } else {
            for (int x = visMinX; x < visMaxX; x++) for (int y = visMinY; y < visMaxY; y++) {
                int c = previewingOriginal && originalPixels != null ? originalPixels[x][y] : canvas.getPixel(x, y);
                int sx = canvasScreenX + x * zoom, sy = canvasScreenY + y * zoom;
                int alpha = (c >> 24) & 0xFF;
                if (alpha < 255) ctx.fill(sx, sy, sx + zoom, sy + zoom, ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0);
                if (alpha > 0)   ctx.fill(sx, sy, sx + zoom, sy + zoom, usesTint() ? applyTint(c) : c);
            }
        }

        if (showGrid && zoom >= 4) {
            for (int x = visMinX; x <= visMaxX; x++) ctx.fill(canvasScreenX + x * zoom, canvasScreenY + visMinY * zoom, canvasScreenX + x * zoom + 1, canvasScreenY + visMaxY * zoom, 0x30FFFFFF);
            for (int y = visMinY; y <= visMaxY; y++) ctx.fill(canvasScreenX + visMinX * zoom, canvasScreenY + y * zoom, canvasScreenX + visMaxX * zoom, canvasScreenY + y * zoom + 1, 0x30FFFFFF);
        }
        drawRectOutline(ctx, canvasScreenX - 1, canvasScreenY - 1, canvasScreenX + w * zoom + 1, canvasScreenY + h * zoom + 1, 0xFF4444AA);

        // Hover highlight
        int hx = (mx - canvasScreenX) / effectiveZoom, hy = (my - canvasScreenY) / effectiveZoom;
        if (hx >= 0 && hx < w && hy >= 0 && hy < h) {
            int half = toolSize / 2;
            for (int dx = -half; dx < toolSize - half; dx++) for (int dy = -half; dy < toolSize - half; dy++) {
                int tx = hx + dx, ty = hy + dy;
                if (tx >= 0 && tx < w && ty >= 0 && ty < h) {
                    int sx = canvasScreenX + tx * zoom, sy = canvasScreenY + ty * zoom;
                    drawRectOutline(ctx, sx, sy, sx + zoom, sy + zoom, 0xAAFFFF00);
                }
            }
        }

        // Preview for line/rectangle tools (draw preview using real color and thickness)
        if (lineFirstClick && previewEndX >= 0 && previewEndY >= 0) {
            // draw each pixel of the preview line with thickness, clipped to canvas
            int half = toolSize / 2;
            for (int[] pt : bresenhamLine(lineStartX, lineStartY, previewEndX, previewEndY)) {
                int cx = pt[0], cy = pt[1];
                // iterate over the square area for the thickness and only draw cells inside canvas
                for (int dx = -half; dx < toolSize - half; dx++) {
                    for (int dy = -half; dy < toolSize - half; dy++) {
                        int tx = cx + dx, ty = cy + dy;
                        if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                        int sx = canvasScreenX + tx * zoom;
                        int sy = canvasScreenY + ty * zoom;
                        // apply preview variation if configured (deterministic per-cell based on coords)
                        float var = ModSettings.getInstance().variationPercent;
                        int drawColor = currentColor;
                        if (var > 0f) drawColor = applyPreviewVariation(currentColor, tx, ty, var);
                        ctx.fill(sx, sy, sx + zoom, sy + zoom, drawColor);
                    }
                }
            }
        }
        if (rectFirstClick && previewEndX >= 0 && previewEndY >= 0) {
            int half = toolSize / 2;
            // Convert start/end centers to top-left of their brush areas
            int sTLX = rectStartX - half;
            int sTLY = rectStartY - half;
            int eTLX = previewEndX - half;
            int eTLY = previewEndY - half;
            // full covered ranges for start and end areas
            int sMaxX = sTLX + toolSize - 1;
            int sMaxY = sTLY + toolSize - 1;
            int eMaxX = eTLX + toolSize - 1;
            int eMaxY = eTLY + toolSize - 1;

            int minX = Math.min(sTLX, eTLX);
            int maxX = Math.max(sMaxX, eMaxX);
            int minY = Math.min(sTLY, eTLY);
            int maxY = Math.max(sMaxY, eMaxY);

            // Special-case: if the covered box is a single cell, draw filled square
            if (minX == maxX && minY == maxY) {
                int cx = minX, cy = minY;
                if (cx >= 0 && cx < w && cy >= 0 && cy < h) {
                    int sx = canvasScreenX + cx * zoom;
                    int sy = canvasScreenY + cy * zoom;
                    ctx.fill(sx, sy, sx + zoom, sy + zoom, currentColor);
                }
                return;
            }

            // Top and bottom bands
            for (int tx = minX; tx <= maxX; tx++) {
                if (tx < 0 || tx >= w) continue;
                for (int pyOff = minY; pyOff <= Math.min(maxY, minY + toolSize - 1); pyOff++) {
                    if (pyOff >= 0 && pyOff < h) {
                        int drawColor = currentColor;
                        float var = ModSettings.getInstance().variationPercent;
                        if (var > 0f) drawColor = applyPreviewVariation(currentColor, tx, pyOff, var);
                        ctx.fill(canvasScreenX + tx * zoom, canvasScreenY + pyOff * zoom, canvasScreenX + (tx+1) * zoom, canvasScreenY + (pyOff+1) * zoom, drawColor);
                    }
                }
                for (int pyOff = Math.max(minY, maxY - (toolSize - 1)); pyOff <= maxY; pyOff++) {
                    if (pyOff >= 0 && pyOff < h) {
                        int drawColor = currentColor;
                        float var = ModSettings.getInstance().variationPercent;
                        if (var > 0f) drawColor = applyPreviewVariation(currentColor, tx, pyOff, var);
                        ctx.fill(canvasScreenX + tx * zoom, canvasScreenY + pyOff * zoom, canvasScreenX + (tx+1) * zoom, canvasScreenY + (pyOff+1) * zoom, drawColor);
                    }
                }
            }

            // Left and right bands (exclude corners - they are already drawn)
            for (int ty = minY + toolSize; ty <= maxY - toolSize; ty++) {
                if (ty < 0 || ty >= h) continue;
                // left band
                for (int pxOff = minX; pxOff < minX + toolSize; pxOff++) {
                    if (pxOff >= 0 && pxOff < w) {
                        int drawColor = currentColor;
                        float var = ModSettings.getInstance().variationPercent;
                        if (var > 0f) drawColor = applyPreviewVariation(currentColor, pxOff, ty, var);
                        ctx.fill(canvasScreenX + pxOff * zoom, canvasScreenY + ty * zoom, canvasScreenX + (pxOff+1) * zoom, canvasScreenY + (ty+1) * zoom, drawColor);
                    }
                }
                // right band
                for (int pxOff = maxX - (toolSize - 1); pxOff <= maxX; pxOff++) {
                    if (pxOff >= 0 && pxOff < w) {
                        int drawColor = currentColor;
                        float var = ModSettings.getInstance().variationPercent;
                        if (var > 0f) drawColor = applyPreviewVariation(currentColor, pxOff, ty, var);
                        ctx.fill(canvasScreenX + pxOff * zoom, canvasScreenY + ty * zoom, canvasScreenX + (pxOff+1) * zoom, canvasScreenY + (ty+1) * zoom, drawColor);
                    }
                }
            }
        }

        // Fill preview: if fill is active (start set, waiting for previewEnd) show affected cells
        if (!lineFirstClick && !rectFirstClick && currentTool == EditorTool.FILL && previewEndX >= 0 && previewEndY >= 0) {
            // compute flood region on preview coords
            var region = canvas.computeFloodRegion(previewEndX, previewEndY);
            float var = ModSettings.getInstance().variationPercent;
            for (var p : region) {
                int tx = p[0], ty = p[1];
                if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                int sx = canvasScreenX + tx * zoom;
                int sy = canvasScreenY + ty * zoom;
                int drawColor = currentColor;
                if (var > 0f) drawColor = applyPreviewVariation(currentColor, tx, ty, var);
                ctx.fill(sx, sy, sx + zoom, sy + zoom, drawColor);
            }
        }

        if (previewingOriginal)
            ctx.drawCenteredTextWithShadow(textRenderer, "§e§lPREVIEW", canvasScreenX + (w * zoom) / 2, canvasScreenY - 12, 0xFFFFFF00);

        // If a line/rectangle start is active and it was confirmed via click, show helpful overlay above the canvas
        if ((lineFirstClick || rectFirstClick) && shapeStartConfirmed && !draggingShape) {
            String msg = "Click another point to finish";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(msg), canvasScreenX + (w * zoom) / 2, canvasScreenY - 26, 0xFFFFFF00);
        }
    }

    private int renderPixel(int c, int x, int y) {
        int alpha = (c >> 24) & 0xFF;
        if (alpha == 0) return ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0;
        if (alpha < 255) {
            int checker = ((x + y) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0;
            int fc = usesTint() ? applyTint(c) : c;
            float a = alpha / 255f;
            int cr = (int)(((fc >> 16) & 0xFF) * a + ((checker >> 16) & 0xFF) * (1 - a));
            int cg = (int)(((fc >> 8) & 0xFF) * a + ((checker >> 8) & 0xFF) * (1 - a));
            int cb = (int)((fc & 0xFF) * a + (checker & 0xFF) * (1 - a));
            return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
        }
        return usesTint() ? applyTint(c) : c;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input handling
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isInUIRegion(double mx, double my) {
        return mx < leftW() + TOGGLE_BTN_W || mx > this.width - rightW() - TOGGLE_BTN_W || my < getToolButtonHeight() || my > this.height - 14;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        try {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            if (ctrl) {
                if (va > 0) toolSize = Math.min(10, toolSize + 1);
                else if (va < 0) toolSize = Math.max(1, toolSize - 1);

                clearChildren();
                recalcCanvasPos();
                buildWidgets();
                return true;
            }
        } catch (Exception ignored) {}

        if (handleExtraScroll(mx, my, ha, va)) return true;

        // Left panel scrolling
        if (leftOpen) {
            int leftPanelLeft = 0;
            int leftPanelInnerX = 4;
            int leftTop = getToolButtonHeight() + TAB_H + 4;
            int visibleH = this.height - leftTop - 20;
            if (mx >= leftPanelLeft && mx < leftPanelLeft + getPanelWidth() && my >= leftTop && my < leftTop + visibleH) {
                if (va > 0) leftPanelScrollY = Math.max(0, leftPanelScrollY - SCROLL_STEP);
                else if (va < 0) leftPanelScrollY = Math.min(Math.max(0, leftPanelContentHeight - visibleH), leftPanelScrollY + SCROLL_STEP);
                return true;
            }
        }

        // Right-panel scrolling: palette/history or layer list
        if (rightOpen) {
            int panelW = getPanelWidth();
            int rpx = this.width - panelW;
            // compute picker geometry for hover detection
            int pickerInnerX = rpx + 4;
            int pickerPy = getToolButtonHeight() + TAB_H + 4;
            int pickerInnerW = panelW - 8;
            int pickerAvail = Math.max(0, pickerInnerW - PICKER_SV_W - 8);
            int pickerHueW = Math.max(HUE_W, pickerAvail / 2);
            int pickerAlphaW = Math.max(ALPHA_W, pickerAvail - pickerHueW);
            int pickerSvX = pickerInnerX, pickerSvY = pickerPy;
            int pickerHueX = pickerSvX + PICKER_SV_W + 4;
            int pickerAlphaX = pickerHueX + pickerHueW + 4;
            // color tab region
            if (rightTab == RightTab.COLOR) {
                int innerX = getPanelInnerX(rpx), innerW = panelW - 8;
                int palTop = getToolButtonHeight() + TAB_H + 4 + PICKER_SV_H + 6 + getToolButtonHeight() + 4 + getToolButtonHeight() + 8;
                int cols = 5;
                int cs = (innerW - (cols - 1)) / cols;
                int totalPaletteRows = (PALETTE.length + cols - 1) / cols;
                int paletteHeight = totalPaletteRows * (cs + 1) + 4;
                int histSize = ColorHistory.getInstance().size();
                int histRows = (Math.min(histSize, 10) + cols - 1) / cols;
                int historyHeight = histRows * (Math.max(8, cs - 2) + 1) + 14;
                int contentHeight = paletteHeight + historyHeight;
                int visibleH = this.height - palTop - 20;
                if (mx >= rpx && mx < rpx + panelW && my >= palTop && my < palTop + visibleH) {
                    if (va > 0) paletteScrollY = Math.max(0, paletteScrollY - SCROLL_STEP);
                    else if (va < 0) paletteScrollY = Math.min(Math.max(0, contentHeight - visibleH), paletteScrollY + SCROLL_STEP);
                    return true;
                }
            }
            // If hovering over hue or alpha bars, use scroll to adjust them instead of zooming
            if (rightTab == RightTab.COLOR) {
                if (mx >= pickerHueX && mx < pickerHueX + pickerHueW && my >= pickerSvY && my < pickerSvY + PICKER_SV_H) {
                    // adjust hue
                    if (va > 0) pickerHue = Math.max(0f, pickerHue - 1f / (PICKER_SV_H - 1));
                    else if (va < 0) pickerHue = Math.min(1f, pickerHue + 1f / (PICKER_SV_H - 1));
                    setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
                    return true;
                }
                if (mx >= pickerAlphaX && mx < pickerAlphaX + pickerAlphaW && my >= pickerSvY && my < pickerSvY + PICKER_SV_H) {
                    // adjust alpha
                    if (va > 0) pickerAlpha = Math.min(1f, pickerAlpha + 1f / (PICKER_SV_H - 1));
                    else if (va < 0) pickerAlpha = Math.max(0f, pickerAlpha - 1f / (PICKER_SV_H - 1));
                    setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
                    pickerAlphaBarBuilt = false;
                    return true;
                }
            }
            // layers tab region
            if (rightTab == RightTab.LAYERS) {
                int listTop = getToolButtonHeight() + TAB_H + 4 + 12;
                int innerW = panelW - 8;
                int rowH = 18;
                int listHeight = canvas != null ? canvas.getLayerStack().getLayerCount() * (rowH + 1) : 0;
                int visibleH = this.height - listTop - 60;
                if (mx >= rpx && mx < rpx + panelW && my >= listTop && my < listTop + visibleH) {
                    if (va > 0) layerScrollY = Math.max(0, layerScrollY - SCROLL_STEP);
                    else if (va < 0) layerScrollY = Math.min(Math.max(0, listHeight - visibleH), layerScrollY + SCROLL_STEP);
                    return true;
                }
            }
        }

        int oldZoom = zoom;
        if (va > 0 && zoom < getAllowedMaxZoom()) {
            // multiplicative zoom for consistent perceived speed
            double factor = 1.1;
            int newZoom = (int)Math.max(getMinZoom(), Math.round(zoom * factor));
            if (newZoom == zoom) newZoom = Math.min(zoom + getZoomStep(), getAllowedMaxZoom());
            zoom = Math.min(newZoom, getAllowedMaxZoom());
        } else if (va < 0 && zoom > getMinZoom()) {
            double factor = 1.0 / 1.1;
            int newZoom = (int)Math.max(getMinZoom(), Math.round(zoom * factor));
            if (newZoom == zoom) newZoom = Math.max(zoom - getZoomStep(), getMinZoom());
            zoom = Math.max(newZoom, getMinZoom());
        }
        if (zoom != oldZoom && canvas != null) {
            // Use a safe previous zoom value to avoid division by zero
            int safeOldZoom = Math.max(oldZoom, getMinZoom());
            double rx = (mx - canvasScreenX) / (double)(canvas.getWidth() * safeOldZoom);
            double ry = (my - canvasScreenY) / (double)(canvas.getHeight() * safeOldZoom);
            recalcCanvasPos();
            canvasScreenX = (int)(mx - rx * canvas.getWidth() * zoom);
            canvasScreenY = (int)(my - ry * canvas.getHeight() * zoom);
            panOffsetX = canvasScreenX - canvasBaseX;
            panOffsetY = canvasScreenY - canvasBaseY;
            canvasTextureDirty = true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        double mx = click.x(), my = click.y(); int btn = click.button();
        // On press record down state so we can tell click vs drag on release
        if (btn == 0) {
            leftDown = true;
            if (canvas != null) {
                int dpx = (int)((mx - canvasScreenX) / zoom), dpy = (int)((my - canvasScreenY) / zoom);
                downPx = dpx; downPy = dpy; movedSinceDown = false;
            } else {
                downPx = downPy = -1; movedSinceDown = false;
            }
        }
        if (super.mouseClicked(click, bl)) return true;
        if (handleExtraClick(mx, my, btn)) return true;

        // Middle click = quick wheel
        if (btn == 2) { quickSelectWheel.activate((int)mx, (int)my); return true; }

        // Right click = pan
        if (btn == 1) { isPanning = true; panStartMouseX = mx; panStartMouseY = my; panStartOffsetX = panOffsetX; panStartOffsetY = panOffsetY; return true; }

        // Color picker interactions - begin drag capture if clicking inside picker
        if (btn == 0 && rightOpen && rightTab == RightTab.COLOR) {
            if (startColorPickerDrag(mx, my)) return true;
            if (handlePaletteClick(mx, my)) return true;
            if (handleHistoryClick(mx, my)) return true;
        }

        // Layer panel click
        if (btn == 0 && rightOpen && rightTab == RightTab.LAYERS && handleLayerClick(mx, my)) return true;

        if (isInUIRegion(mx, my)) return false;

        // Canvas press: we only record the press here; action happens on release
        if (btn == 0 && canvas != null) {
            int px = (int)((mx - canvasScreenX) / zoom), py = (int)((my - canvasScreenY) / zoom);
            if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
                lastDrawX = px; lastDrawY = py;
                // For LINE/RECTANGLE: start immediately on press (preserve original drag behavior)
                if (currentTool == EditorTool.LINE) {
                    if (!lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; previewEndX = -1; previewEndY = -1; shapeStartConfirmed = false; startCreatedOnPress = true; return true; }
                }
                if (currentTool == EditorTool.RECTANGLE) {
                    if (!rectFirstClick) { rectStartX = px; rectStartY = py; rectFirstClick = true; previewEndX = -1; previewEndY = -1; shapeStartConfirmed = false; startCreatedOnPress = true; return true; }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mx = click.x(), my = click.y(); int btn = click.button();
        if (btn == 0) {
            leftDown = false;
        }
        if (handleExtraRelease(mx, my, btn)) return true;
        if (btn == 1) { isPanning = false; return true; }
        // If a color picker drag was active, stop it
        if (btn == 0 && (pickingSv || pickingHue || pickingAlpha)) { pickingSv = pickingHue = pickingAlpha = false; return true; }

        // Commit previewed shapes on left release. Distinguish click (no move) vs drag.
        if (btn == 0) {
            // compute release pixel coords
            int px = (int)((mx - canvasScreenX) / zoom), py = (int)((my - canvasScreenY) / zoom);
            boolean insideCanvas = canvas != null && px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight();

            if (!movedSinceDown) {
                // This was a click (press+release without movement)
                if (insideCanvas) {
                    // If the start was created by press, this click confirms it and shows the overlay
                    if (startCreatedOnPress) {
                        shapeStartConfirmed = true;
                        startCreatedOnPress = false;
                        return true;
                    }
                    // If no start yet (rare), set start now and mark confirmed
                    if (currentTool == EditorTool.LINE && !lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; previewEndX = -1; previewEndY = -1; shapeStartConfirmed = true; return true; }
                    if (currentTool == EditorTool.RECTANGLE && !rectFirstClick) { rectStartX = px; rectStartY = py; rectFirstClick = true; previewEndX = -1; previewEndY = -1; shapeStartConfirmed = true; return true; }

                    // If a start exists and was confirmed, a second click commits
                    if (currentTool == EditorTool.LINE && lineFirstClick && shapeStartConfirmed) {
                        if (canvas != null) {
                            canvas.saveSnapshot();
                            float variation = ModSettings.getInstance().variationPercent;
                            if (toolSize > 1 || variation > 0f) canvas.drawLineThickness(lineStartX, lineStartY, px, py, currentColor, toolSize, variation);
                            else canvas.drawLine(lineStartX, lineStartY, px, py, currentColor);
                            canvas.invalidateCache(); canvasTextureDirty = true;
                        }
                        lineFirstClick = false; previewEndX = previewEndY = -1; draggingShape = false; shapeStartConfirmed = false; setColor(currentColor, true); return true;
                    }
                    if (currentTool == EditorTool.RECTANGLE && rectFirstClick && shapeStartConfirmed) {
                        if (canvas != null) {
                            canvas.saveSnapshot();
                            float variation = ModSettings.getInstance().variationPercent;
                            if (toolSize > 1 || variation > 0f) canvas.drawRectOutlineThickness(rectStartX, rectStartY, px, py, currentColor, toolSize, variation);
                            else canvas.drawRect(rectStartX, rectStartY, px, py, currentColor);
                            canvas.invalidateCache(); canvasTextureDirty = true;
                        }
                        rectFirstClick = false; previewEndX = previewEndY = -1; draggingShape = false; shapeStartConfirmed = false; setColor(currentColor, true); return true;
                    }

                    // Not a shape tool click: delegate to click handler
                    handleCanvasClick(px, py, btn);
                    return true;
                }
            } else {
                // This was a drag (movement occurred while holding). If preview exists, commit it.
                if (lineFirstClick && previewEndX >= 0) {
                    if (canvas != null) {
                        canvas.saveSnapshot();
                        float variation = ModSettings.getInstance().variationPercent;
                        if (toolSize > 1 || variation > 0f) canvas.drawLineThickness(lineStartX, lineStartY, previewEndX, previewEndY, currentColor, toolSize, variation);
                        else canvas.drawLine(lineStartX, lineStartY, previewEndX, previewEndY, currentColor);
                        canvas.invalidateCache(); canvasTextureDirty = true;
                    }
                    lineFirstClick = false; previewEndX = previewEndY = -1; draggingShape = false; shapeStartConfirmed = false; startCreatedOnPress = false; setColor(currentColor, true); return true;
                }
                if (rectFirstClick && previewEndX >= 0) {
                    if (canvas != null) {
                        canvas.saveSnapshot();
                        float variation = ModSettings.getInstance().variationPercent;
                        if (toolSize > 1 || variation > 0f) canvas.drawRectOutlineThickness(rectStartX, rectStartY, previewEndX, previewEndY, currentColor, toolSize, variation);
                        else canvas.drawRectOutlineThickness(rectStartX, rectStartY, previewEndX, previewEndY, currentColor, 1, variation);
                        canvas.invalidateCache(); canvasTextureDirty = true;
                    }
                    rectFirstClick = false; previewEndX = previewEndY = -1; draggingShape = false; shapeStartConfirmed = false; startCreatedOnPress = false; setColor(currentColor, true); return true;
                }
            }
            // reset movement tracking
            movedSinceDown = false;
            leftDown = false;
            shapeStartConfirmed = shapeStartConfirmed && (lineFirstClick || rectFirstClick);
        }

        if (btn == 2) {
            var sel = quickSelectWheel.getSelectedSlice();
            if (sel != null && sel.toTool() != null) currentTool = sel.toTool();
            quickSelectWheel.deactivate();
            return true;
        }
        lastDrawX = -1; lastDrawY = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        double mx = click.x(), my = click.y(); int btn = click.button();
        if (quickSelectWheel.isVisible()) return true;
        if (btn == 1 && isPanning) {
            panOffsetX = panStartOffsetX + (int)(mx - panStartMouseX);
            panOffsetY = panStartOffsetY + (int)(my - panStartMouseY);
            canvasScreenX = canvasBaseX + panOffsetX;
            canvasScreenY = canvasBaseY + panOffsetY;
            canvasTextureDirty = true;
            return true;
        }
        // If a color picker drag was started, keep updating it while mouse moves
        if (pickingSv || pickingHue || pickingAlpha) {
            updateColorPickerFromMouse(mx, my);
            return true;
        }
        // If left button is down and we're in the color panel, begin a drag-capture (prevents jump when entering slider)
        if (btn == 0 && rightOpen && rightTab == RightTab.COLOR && startColorPickerDrag(mx, my)) return true;
        if (handleExtraDrag(mx, my, btn, dx, dy)) return true;
        if (isInUIRegion(mx, my)) return super.mouseDragged(click, dx, dy);
        if (canvas != null && btn == 0) {
            int px = (int)((mx - canvasScreenX) / zoom), py = (int)((my - canvasScreenY) / zoom);
            if (px >= 0 && px < canvas.getWidth() && py >= 0 && py < canvas.getHeight()) {
                // Update movement tracking (click vs drag)
                if (leftDown && downPx >= 0 && downPy >= 0) {
                    if (px != downPx || py != downPy) movedSinceDown = true;
                }

                // If previewing a shape, update preview end and show live preview (immediate)
                if (lineFirstClick || rectFirstClick) {
                    previewEndX = px; previewEndY = py;
                    draggingShape = true;
                    movedSinceDown = true; // mark as drag so release will commit as drag
                    return true;
                }

                // Normal brush/eraser drawing while dragging
                float variation = ModSettings.getInstance().variationPercent;
                int sx = lastDrawX >= 0 ? lastDrawX : px, sy = lastDrawY >= 0 ? lastDrawY : py;
                for (int[] pt : bresenhamLine(sx, sy, px, py)) {
                    int ix = pt[0], iy = pt[1];
                    if (ix < 0 || ix >= canvas.getWidth() || iy < 0 || iy >= canvas.getHeight()) continue;
                    if (currentTool == EditorTool.PENCIL) {
                        if (variation > 0f) { if (toolSize > 1) canvas.drawBrushArea(ix, iy, toolSize, currentColor, variation); else canvas.drawBrushPixel(ix, iy, currentColor, variation); }
                        else { if (toolSize > 1) canvas.drawPixelArea(ix, iy, toolSize, currentColor); else canvas.drawPixel(ix, iy, currentColor); }
                    }
                    else if (currentTool == EditorTool.ERASER) { if (toolSize > 1) canvas.erasePixelArea(ix, iy, toolSize); else canvas.erasePixel(ix, iy); }
                }
                lastDrawX = px; lastDrawY = py;
                return true;
            }
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        int kc = keyInput.key();
        if (hexInput != null && hexInput.isFocused()) return super.keyPressed(keyInput);
        var previewKey = com.zeeesea.textureeditor.TextureEditorClient.getPreviewOriginalKey();
        if (previewKey != null && previewKey.matchesKey(keyInput)) { previewingOriginal = true; return true; }
        ModSettings s = ModSettings.getInstance();
        if (kc == s.getKeybind("undo"))       { canvas.undo(); return true; }
        if (kc == s.getKeybind("redo"))       { canvas.redo(); return true; }
        if (kc == s.getKeybind("grid"))       { showGrid = !showGrid; return true; }
        if (kc == s.getKeybind("pencil"))     { currentTool = EditorTool.PENCIL; return true; }
        if (kc == s.getKeybind("eraser"))     { currentTool = EditorTool.ERASER; return true; }
        if (kc == s.getKeybind("fill"))       { currentTool = EditorTool.FILL; return true; }
        if (kc == s.getKeybind("eyedropper")) { currentTool = EditorTool.EYEDROPPER; return true; }
        if (kc == s.getKeybind("rectangle"))  { currentTool = EditorTool.RECTANGLE; lineFirstClick = rectFirstClick = false; previewEndX = previewEndY = -1; draggingShape = false; return true; }
        if (kc == s.getKeybind("line"))       { currentTool = EditorTool.LINE; lineFirstClick = rectFirstClick = false; previewEndX = previewEndY = -1; draggingShape = false; return true; }
        // brush key removed
        var openKey = com.zeeesea.textureeditor.TextureEditorClient.getOpenEditorKey();
        if (openKey != null && openKey.matchesKey(keyInput)) { if (s.autoApplyLive) applyLive(); this.close(); return true; }
        // Browse keybind: jump back to the previous/backing screen or open BrowseScreen
        if (kc == s.getKeybind("browse")) {
            if (ModSettings.getInstance().autoApplyLive) applyLive();
            Screen bs = getBackScreen();
            MinecraftClient.getInstance().setScreen(bs != null ? bs : new BrowseScreen());
            return true;
        }
        if (kc == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { if (s.autoApplyLive) applyLive(); this.close(); return true; }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyInput keyInput) {
        var previewKey = com.zeeesea.textureeditor.TextureEditorClient.getPreviewOriginalKey();
        if (previewKey != null && previewKey.matchesKey(keyInput)) { previewingOriginal = false; return true; }
        return super.keyReleased(keyInput);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Click handlers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean handleColorPickerClick(double mx, double my) {
        int rpx = this.width - PANEL_W;
        int innerX = rpx + 4;
        int bh = getToolButtonHeight();
        int py = getToolButtonHeight() + TAB_H + 4;
        int innerW = PANEL_W - 8;
        int avail = Math.max(0, innerW - PICKER_SV_W - 8);
        int hueW = Math.max(HUE_W, avail / 2);
        int alphaW = Math.max(ALPHA_W, avail - hueW);
        int svX = innerX, svY = py;
        int hueX = svX + PICKER_SV_W + 4;
        int alphaX = hueX + hueW + 4;

        if (mx >= svX && mx < svX + PICKER_SV_W && my >= svY && my < svY + PICKER_SV_H) {
            pickerSat = (float) Math.max(0, Math.min(1, (mx - svX) / (PICKER_SV_W - 1)));
            pickerVal = (float) Math.max(0, Math.min(1, 1f - (my - svY) / (PICKER_SV_H - 1)));
            setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
            return true;
        }
        if (mx >= hueX && mx < hueX + hueW && my >= svY && my < svY + PICKER_SV_H) {
                        int rel = (int)Math.round(my - svY);
            rel = Math.max(0, Math.min(PICKER_SV_H - 2, rel)); // cap to one before the final pixel to avoid wrap
            pickerHue = rel / (float)(PICKER_SV_H - 1);
            setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
            return true;
        }
        if (mx >= alphaX && mx < alphaX + alphaW && my >= svY && my < svY + PICKER_SV_H) {
            int rel = (int)Math.round(my - svY);
            rel = Math.max(0, Math.min(PICKER_SV_H - 2, rel));
            pickerAlpha = 1f - rel / (float)(PICKER_SV_H - 1);
            setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
            pickerAlphaBarBuilt = false;
            return true;
        }
        return false;
    }

    private boolean handlePaletteClick(double mx, double my) {
        int panelW = getPanelWidth();
        int rpx = this.width - panelW;
        int innerX = getPanelInnerX(rpx), innerW = panelW - 8;
        int bh = getToolButtonHeight();
        int py = getToolButtonHeight() + TAB_H + 4 + PICKER_SV_H + 6 + bh + 4 + bh + 8;
        int cols = 5, cs = (innerW - (cols - 1)) / cols;
        int startY = py - paletteScrollY;
        int centerOffsetX = (innerW - (cols * cs + (cols - 1))) / 2;
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % cols, row = i / cols;
            int px2 = innerX + col * (cs + 1) + centerOffsetX;
            int py2 = startY + row * (cs + 1);
            if (mx >= px2 && mx < px2 + cs && my >= py2 && my < py2 + cs) {
                setColor(PALETTE[i], false);
                return true;
            }
        }
        return false;
    }

    private boolean handleHistoryClick(double mx, double my) {
        ColorHistory hist = ColorHistory.getInstance();
        if (hist.size() == 0) return false;
        int panelW = getPanelWidth();
        int rpx = this.width - panelW;
        int innerX = getPanelInnerX(rpx), innerW = panelW - 8;
        int bh = getToolButtonHeight();
        int cols = 5, cs = (innerW - (cols - 1)) / cols;
        int palRows = (PALETTE.length + cols - 1) / cols;
        int py = getToolButtonHeight() + TAB_H + 4 + PICKER_SV_H + 6 + bh + 4 + bh + 8 + palRows * (cs + 1) + 4 + 10;
        int hcs = Math.max(8, cs - 2);
        int startY = py - paletteScrollY;
        int centerOffsetX = (innerW - (cols * hcs + (cols - 1))) / 2;
        List<Integer> colors = hist.getColors();
        for (int i = 0; i < Math.min(colors.size(), 10); i++) {
            int col = i % cols, row = i / cols;
            int px2 = innerX + col * (hcs + 1) + centerOffsetX, py2 = startY + row * (hcs + 1);
            if (mx >= px2 && mx < px2 + hcs && my >= py2 && my < py2 + hcs) {
                setColor(colors.get(i), false);
                return true;
            }
        }
        return false;
    }

    private boolean handleLayerClick(double mx, double my) {
        if (canvas == null) return false;
        var stack = canvas.getLayerStack();
        int panelW = getPanelWidth();
        int rpx = this.width - panelW;
        int innerX = getPanelInnerX(rpx), innerW = panelW - 8;
        int bh = getToolButtonHeight();

        // Action buttons are fixed at the top under the "Layers" title. Check them first.
        int actionTop = getToolButtonHeight() + TAB_H + 4 + 12;
        int btnW = (innerW - 4) / 3;
        // First action row
        if (my >= actionTop && my < actionTop + bh && mx >= innerX && mx < innerX + innerW) {
            if (mx < innerX + btnW) { stack.addLayerAbove("Layer " + stack.getLayerCount()); canvas.invalidateCache(); return true; }
            if (mx < innerX + btnW + 2 + btnW) { stack.removeLayer(stack.getActiveIndex()); canvas.invalidateCache(); return true; }
            if (mx <= innerX + innerW) { int idx = stack.getActiveIndex(); if (idx < stack.getLayerCount() - 1) { stack.moveLayerDown(idx); canvas.invalidateCache(); } return true; }
        }
        // Second action row
        int secondActionTop = actionTop + bh + 2;
        if (my >= secondActionTop && my < secondActionTop + bh && mx >= innerX && mx < innerX + innerW) {
            if (mx < innerX + btnW) { int idx = stack.getActiveIndex(); if (idx > 0) { stack.moveLayerUp(idx); canvas.invalidateCache(); } return true; }
            if (mx < innerX + btnW + 2 + btnW) { stack.mergeDown(stack.getActiveIndex()); canvas.invalidateCache(); return true; }
            if (mx <= innerX + innerW) { stack.duplicateLayer(stack.getActiveIndex()); canvas.invalidateCache(); return true; }
        }

        // Layer rows (account for scroll) - these are drawn below the actions and scroll under them
        int listStartY = secondActionTop + bh + 4;
        int rowH = 18;
        int contentY = listStartY - layerScrollY;
        for (int i = stack.getLayerCount() - 1; i >= 0; i--) {
            int rowY = contentY + (stack.getLayerCount() - 1 - i) * (rowH + 1);
            if (my >= rowY && my < rowY + rowH && mx >= innerX && mx < innerX + innerW) {
                if (mx < innerX + 12) { stack.getLayers().get(i).setVisible(!stack.getLayers().get(i).isVisible()); canvas.invalidateCache(); }
                else stack.setActiveIndex(i);
                return true;
            }
        }
        return false;
    }

    // Start capturing a drag inside the color picker (SV/Hue/Alpha). Returns true if started.
    private boolean startColorPickerDrag(double mx, double my) {
        int rpx = this.width - PANEL_W;
        int innerX = rpx + 4;
        int py = getToolButtonHeight() + TAB_H + 4;
        int innerW = PANEL_W - 8;
        int avail = Math.max(0, innerW - PICKER_SV_W - 8);
        int hueW = Math.max(HUE_W, avail / 2);
        int alphaW = Math.max(ALPHA_W, avail - hueW);
        int svX = innerX, svY = py;
        int hueX = svX + PICKER_SV_W + 4;
        int alphaX = hueX + hueW + 4;

        if (mx >= svX && mx < svX + PICKER_SV_W && my >= svY && my < svY + PICKER_SV_H) {
            pickingSv = true;
            updateColorPickerFromMouse(mx, my);
            return true;
        }
        if (mx >= hueX && mx < hueX + hueW && my >= svY && my < svY + PICKER_SV_H) {
            pickingHue = true;
            updateColorPickerFromMouse(mx, my);
            return true;
        }
        if (mx >= alphaX && mx < alphaX + alphaW && my >= svY && my < svY + PICKER_SV_H) {
            pickingAlpha = true;
            updateColorPickerFromMouse(mx, my);
            return true;
        }
        return false;
    }

    private void updateColorPickerFromMouse(double mx, double my) {
        int rpx = this.width - PANEL_W;
        int innerX = rpx + 4;
        int py = getToolButtonHeight() + TAB_H + 4;
        int innerW = PANEL_W - 8;
        int avail = Math.max(0, innerW - PICKER_SV_W - 8);
        int hueW = Math.max(HUE_W, avail / 2);
        int alphaW = Math.max(ALPHA_W, avail - hueW);
        int svX = innerX, svY = py;
        int hueX = svX + PICKER_SV_W + 4;
        int alphaX = hueX + hueW + 4;

        if (pickingSv) {
            pickerSat = (float) Math.max(0, Math.min(1, (mx - svX) / (PICKER_SV_W - 1)));
            pickerVal = (float) Math.max(0, Math.min(1, 1f - (my - svY) / (PICKER_SV_H - 1)));
            setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
            return;
        }
        if (pickingHue) {
            int rel = (int)Math.round(my - svY);
            rel = Math.max(0, Math.min(PICKER_SV_H - 2, rel));
            pickerHue = rel / (float)(PICKER_SV_H - 1);
            setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
            return;
        }
        if (pickingAlpha) {
            int rel = (int)Math.round(my - svY);
            rel = Math.max(0, Math.min(PICKER_SV_H - 2, rel));
            pickerAlpha = 1f - rel / (float)(PICKER_SV_H - 1);
            setColor(hsvToArgb(pickerHue, pickerSat, pickerVal, pickerAlpha), false);
            pickerAlphaBarBuilt = false;
            return;
        }
    }


    protected void handleCanvasClick(int px, int py, int btn) {
        float variation = ModSettings.getInstance().variationPercent;
        switch (currentTool) {
            case PENCIL   -> { canvas.saveSnapshot(); if (variation > 0f) { if (toolSize > 1) canvas.drawBrushArea(px, py, toolSize, currentColor, variation); else canvas.drawBrushPixel(px, py, currentColor, variation); } else { if (toolSize > 1) canvas.drawPixelArea(px, py, toolSize, currentColor); else canvas.drawPixel(px, py, currentColor); } setColor(currentColor, true); }
            case ERASER   -> { canvas.saveSnapshot(); if (toolSize > 1) canvas.erasePixelArea(px, py, toolSize); else canvas.erasePixel(px, py); }
            case FILL     -> { canvas.saveSnapshot(); float v = ModSettings.getInstance().variationPercent; if (v > 0f) canvas.floodFill(px, py, currentColor, v); else canvas.floodFill(px, py, currentColor); setColor(currentColor, true); }
            case EYEDROPPER -> {
                int raw = canvas.pickColorComposited(px, py);
                if (raw == 0) { NotificationHelper.addToast(SystemToast.Type.PACK_LOAD_FAILURE, "Layer is empty!"); return; }
                setColor(usesTint() ? applyTint(raw) : raw, false);
            }
            case LINE -> {
                if (!lineFirstClick) { lineStartX = px; lineStartY = py; lineFirstClick = true; previewEndX = -1; previewEndY = -1; }
                else {
                    canvas.saveSnapshot();
                    if (toolSize > 1 || variation > 0f) canvas.drawLineThickness(lineStartX, lineStartY, px, py, currentColor, toolSize, variation);
                    else canvas.drawLine(lineStartX, lineStartY, px, py, currentColor);
                    lineFirstClick = false; setColor(currentColor, true);
                }
            }

            case RECTANGLE -> {
                if (!rectFirstClick) { rectStartX = px; rectStartY = py; rectFirstClick = true; previewEndX = -1; previewEndY = -1; }
                else {
                    canvas.saveSnapshot();
                    if (toolSize > 1 || variation > 0f) canvas.drawRectOutlineThickness(rectStartX, rectStartY, px, py, currentColor, toolSize, variation);
                    else canvas.drawRect(rectStartX, rectStartY, px, py, currentColor);
                    rectFirstClick = false; setColor(currentColor, true);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Color helpers
    // ─────────────────────────────────────────────────────────────────────────

    protected void setColor(int c, boolean addToHistory) {
        currentColor = c;
        if (addToHistory) ColorHistory.getInstance().addColor(c);
        syncPickerFromColor(c);
        pickerAlphaBarBuilt = false;
        if (hexInput != null) {
            // Update hex input without triggering its change listener to avoid recursion.
            // Only update when the displayed text differs to avoid re-entrant setText -> onChanged -> setText loops
            String newHex = colorToHex(c);
            if (!newHex.equals(hexInput.getText())) {
                suppressHexCallback = true;
                try { hexInput.setText(newHex); } finally { suppressHexCallback = false; }
            }
        }
    }

    private void syncPickerFromColor(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        float[] hsv = rgbToHsv(r, g, b);
        pickerHue = hsv[0]; pickerSat = hsv[1]; pickerVal = hsv[2];
        pickerAlpha = ((color >> 24) & 0xFF) / 255f;
    }

    public void setColorPickerFromInt(int color) { syncPickerFromColor(color); }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        float h, s, v = max, d = max - min;
        s = max == 0 ? 0 : d / max;
        if (d == 0) h = 0;
        else if (max == rf) h = ((gf - bf) / d + (gf < bf ? 6 : 0)) / 6f;
        else if (max == gf) h = ((bf - rf) / d + 2) / 6f;
        else h = ((rf - gf) / d + 4) / 6f;
        return new float[]{h, s, v};
    }

    protected static int hsvToArgb(float h, float s, float v, float a) {
        int ai = (int)(a * 255) & 0xFF;
        int i = (int)(h * 6) % 6;
        float f = h * 6 - (int)(h * 6);
        int p = (int)(255 * v * (1 - s)), q = (int)(255 * v * (1 - f * s)), t = (int)(255 * v * (1 - (1 - f) * s)), vi = (int)(255 * v);
        return (ai << 24) | switch (i) {
            case 0 -> (vi << 16) | (t << 8) | p;
            case 1 -> (q << 16) | (vi << 8) | p;
            case 2 -> (p << 16) | (vi << 8) | t;
            case 3 -> (p << 16) | (q << 8) | vi;
            case 4 -> (t << 16) | (p << 8) | vi;
            default -> (vi << 16) | (p << 8) | q;
        };
    }

    // keep legacy signature for subclasses
    protected static int hsvToRgb(float h, float s, float v) { return hsvToArgb(h, s, v, 1f); }

    private static String colorToHex(int c) {
        int a = (c >> 24) & 0xFF;
        int rgb = c & 0xFFFFFF;
        StringBuilder sb = new StringBuilder("#");
        if (a != 255) {
            // include alpha channel
            String hex = Integer.toHexString(c);
            while (hex.length() < 8) hex = "0" + hex;
            sb.append(hex.toUpperCase());
            return sb.toString();
        } else {
            String hex = Integer.toHexString(rgb);
            while (hex.length() < 6) hex = "0" + hex;
            sb.append(hex.toUpperCase());
            return sb.toString();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Misc
    // ─────────────────────────────────────────────────────────────────────────

    protected void doResetAll() {
        TextureManager.getInstance().clear();
        MinecraftClient.getInstance().reloadResources();
        if (originalPixels != null && canvas != null) {
            canvas.saveSnapshot();
            for (int x = 0; x < canvas.getWidth(); x++)
                for (int y = 0; y < canvas.getHeight(); y++)
                    canvas.setPixel(x, y, originalPixels[x][y]);
        }
    }

    /**
     * Confirmation screen shown before performing a destructive Reset All operation.
     */
    private static class ConfirmResetAllScreen extends Screen {
        private final AbstractEditorScreen parentScreen;
        protected ConfirmResetAllScreen(AbstractEditorScreen parent) {
            super(Text.translatable("textureeditor.confirm.reset_all.title"));
            this.parentScreen = parent;
        }

        @Override
        protected void init() {
            int w = 300, h = 80;
            int cx = this.width / 2 - w / 2;
            int cy = this.height / 2 - h / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> { parentScreen.doResetAll(); MinecraftClient.getInstance().setScreen(parentScreen); }).position(cx + 20, cy + 40).size(100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> MinecraftClient.getInstance().setScreen(parentScreen)).position(cx + 140, cy + 40).size(100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, this.width, this.height, 0x88000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Are you sure? All your changes will be lost."), this.width / 2, this.height / 2 - 10, 0xFFFFFF00);
            super.render(ctx, mx, my, delta);
        }
    }

    protected void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        ctx.fill(x1, y1, x2, y1 + 1, c);
        ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c);
        ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    private static java.util.List<int[]> bresenhamLine(int x0, int y0, int x1, int y1) {
        var pts = new java.util.ArrayList<int[]>();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        while (true) {
            pts.add(new int[]{x0, y0});
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx)  { err += dx; y0 += sy; }
        }
        return pts;
    }

    protected static int[][] copyPixels(int[][] src, int w, int h) {
        int[][] copy = new int[w][h];
        for (int x = 0; x < w; x++) System.arraycopy(src[x], 0, copy[x], 0, h);
        return copy;
    }

    @Override public boolean shouldPause() { return false; }

    // Legacy compat for subclasses that call these
    protected int getLeftSidebarWidth()  { return leftW() + TOGGLE_BTN_W; }
    protected int getRightSidebarWidth() { return rightW() + TOGGLE_BTN_W; }
    protected int getToolButtonWidth()   { return PANEL_W - 8; }

    // Legacy compatibility hooks for older subclass implementations
    /** Legacy method used by older subclasses to add arbitrary extra buttons (bottom/right area) */
    protected int addExtraButtons(int toolY) { return toolY; }

    /** Legacy hook to allow subclasses to change the reset button label. Return a translated string. */
    protected String getResetCurrentLabel() { return Text.translatable("textureeditor.button.reset").getString(); }
}


