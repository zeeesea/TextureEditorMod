package com.zeeesea.textureeditor.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gl.RenderPipelines;
import com.zeeesea.textureeditor.editor.EditorTool;

public class QuickSelectWheel {
    private boolean visible = false;
    private int radius;
    private int centerX;
    private int centerY;

    int defaultColor = 0xFF17172e;
    int highlightColor = 0xFF222245;

    Slice highlightedSlice;

    private static final RenderLayer PIE_SLICE_LAYER = RenderLayer.of(
            "pie_slice",
            RenderSetup.builder(RenderPipelines.GUI).translucent().build()
    );

    public enum Slice {
        PENCIL ("Pencil"),
        BRUSH ("Brush"),
        ERASER ("Pencil"),
        //COLOR_WHEEL ("Color Wheel"),
        LINE ("Line"),
        FILL ("Fill");

        private final String name;

        Slice(String name) {
            this.name = name;
        }

        /** Returns the corresponding EditorTool, or null if no direct mapping exists */
        public EditorTool toTool() {
            return switch (this) {
                case PENCIL -> EditorTool.PENCIL;
                case BRUSH -> EditorTool.BRUSH;
                case ERASER -> EditorTool.ERASER;
                case LINE -> EditorTool.LINE;
                case FILL -> EditorTool.FILL;
                default -> null; // COLOR_WHEEL has no direct EditorTool
            };
        }
        public String getName() {
            return name;
        }
    }

    public QuickSelectWheel(int radius) {
        this.radius =  radius;
    }

    public void tick() {}

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!visible) return;

        Slice[] slices = Slice.values();
        float anglePerPart = 360f / slices.length;
        float startAngle = -90f; // start at top

        // Determine which slice the mouse is hovering over
        highlightedSlice = getSliceAtMouse(mouseX, mouseY, radius, startAngle, anglePerPart);

        // Draw each slice with highlight if hovered
        for (int i = 0; i < slices.length; i++) {
            float partStart = startAngle + i * anglePerPart;
            float partEnd = partStart + anglePerPart;
            int color = slices[i] == highlightedSlice ? highlightColor : defaultColor;
            drawPieSlice(context, centerX, centerY, radius, partStart, partEnd, color);
        }

        // Draw label in the center of each slice
        for (int i = 0; i < slices.length; i++) {
            float partStart = startAngle + i * anglePerPart;
            float partEnd = partStart + anglePerPart;
            float mid = (float) Math.toRadians((partStart + partEnd) / 2f);

            // Position text at ~60% of radius along the slice's middle angle
            int textX = (int)(centerX + radius * 0.6f * Math.cos(mid));
            int textY = (int)(centerY + radius * 0.6f * Math.sin(mid)) - 4;

            // First letter only
            String label = slices[i].name().substring(0, 1);
            context.drawCenteredTextWithShadow(textRenderer, label, textX, textY, 0xFFFFFFFF);
        }

        // Show full name of hovered slice above the wheel
        if (highlightedSlice != null) {
            context.drawCenteredTextWithShadow(textRenderer, highlightedSlice.name(), centerX, centerY - radius - 12, 0xFFFFFFFF);
        }
    }

    /** Determines which slice the mouse is currently pointing at, or null if outside the wheel */
    private Slice getSliceAtMouse(int mouseX, int mouseY, float radius, float startAngle, float anglePerPart) {
        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);


        // Calculate angle relative to startAngle and normalize to 0-360
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        angle -= startAngle;
        if (angle < 0) angle += 360f;
        if (angle >= 360f) angle -= 360f;

        // Map angle to slice index
        int index = (int)(angle / anglePerPart);
        index = Math.max(0, Math.min(index, Slice.values().length - 1));
        return Slice.values()[index];
    }

    /** Draws a pie slice using triangle fans via scanline fill */
    public void drawPieSlice(DrawContext context, float cx, float cy, float radius, float startAngle, float endAngle, int color) {
        int segments = 2; // segments per slice for smoother edges
        float step = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.toRadians(startAngle + i * step);
            float a2 = (float) Math.toRadians(startAngle + (i + 1) * step);

            float x1 = cx + radius * (float) Math.cos(a1);
            float y1 = cy + radius * (float) Math.sin(a1);
            float x2 = cx + radius * (float) Math.cos(a2);
            float y2 = cy + radius * (float) Math.sin(a2);

            fillTriangle(context, (int)cx, (int)cy, (int)x1, (int)y1, (int)x2, (int)y2, color);
        }
    }

    /** Fills a triangle using a scanline algorithm */
    public void fillTriangle(DrawContext context, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        // Sort vertices by Y (top to bottom)
        if (y1 < y0) { int tx=x0,ty=y0; x0=x1;y0=y1;x1=tx;y1=ty; }
        if (y2 < y0) { int tx=x0,ty=y0; x0=x2;y0=y2;x2=tx;y2=ty; }
        if (y2 < y1) { int tx=x1,ty=y1; x1=x2;y1=y2;x2=tx;y2=ty; }

        int totalHeight = y2 - y0;
        if (totalHeight == 0) return;

        for (int y = y0; y <= y2; y++) {
            boolean secondHalf = y >= y1;
            int segmentHeight = secondHalf ? y2 - y1 : y1 - y0;
            if (segmentHeight == 0) continue;

            float alpha = (float)(y - y0) / totalHeight;
            float beta  = secondHalf ? (float)(y - y1) / segmentHeight : (float)(y - y0) / segmentHeight;

            int ax = (int)(x0 + (x2 - x0) * alpha);
            int bx = secondHalf ? (int)(x1 + (x2 - x1) * beta) : (int)(x0 + (x1 - x0) * beta);

            if (ax > bx) { int t=ax; ax=bx; bx=t; }
            context.fill(ax, y, bx + 1, y + 1, color);
        }
    }

    /** Returns the currently highlighted slice (hovered by mouse) */
    public Slice getSelectedSlice() {
        return highlightedSlice;
    }

    /** Returns the current visibility state */
    public boolean isVisible() {
        return visible;
    }


    /** Show the wheel centered at the given screen position */
    public void activate(int x, int y) {
        centerX = x;
        centerY = y;
        visible = true;
    }

    /** Hide the wheel */
    public void deactivate() {
        visible = false;
    }
}