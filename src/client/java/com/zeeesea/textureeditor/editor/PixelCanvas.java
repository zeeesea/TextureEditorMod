package com.zeeesea.textureeditor.editor;

import com.zeeesea.textureeditor.settings.ModSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Editable pixel buffer with layers, undo/redo support and drawing tool operations.
 * Drawing operations affect the active layer. getPixel returns the composited result.
 */
public class PixelCanvas {
    private final int width;
    private final int height;
    private final LayerStack layerStack;
    private final Deque<LayerSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<LayerSnapshot> redoStack = new ArrayDeque<>();
    private static int MAX_UNDO = 50;
    private boolean dirty = false;

    // Cached flattened pixels (invalidated on changes)
    private int[][] flattenedCache;
    private boolean cacheValid = false;

    // Toast cooldowns (ms)
    private static long lastLayerEmptyToast = 0;
    private static final long TOAST_COOLDOWN_MS = 5000;

    /**
     * Snapshot of a single layer for undo/redo.
     */
    private record LayerSnapshot(int layerIndex, int[][] pixels) {}

    public PixelCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.layerStack = new LayerStack(width, height);
        MAX_UNDO = ModSettings.getInstance().maxUndoSteps;
    }

    public PixelCanvas(int width, int height, int[][] initialPixels) {
        this.width = width;
        this.height = height;
        this.layerStack = new LayerStack(width, height, initialPixels);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public LayerStack getLayerStack() { return layerStack; }

    /**
     * Get the composited pixel at (x, y) - flattening all visible layers.
     */
    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        if (!cacheValid) {
            flattenedCache = layerStack.flatten();
            cacheValid = true;
        }
        return flattenedCache[x][y];
    }

    /**
     * Set pixel on the active layer.
     */
    public void setPixel(int x, int y, int color) {
        Layer active = layerStack.getActiveLayer();
        if (active == null || x < 0 || x >= width || y < 0 || y >= height) return;
        active.setPixel(x, y, color);
        dirty = true;
        cacheValid = false;
    }

    /**
     * Get the flattened pixels (all layers composited).
     */
    public int[][] getPixels() {
        if (!cacheValid) {
            flattenedCache = layerStack.flatten();
            cacheValid = true;
        }
        return flattenedCache;
    }

    /**
     * Save current active layer state to undo stack before making changes.
     */
    public void saveSnapshot() {
        Layer active = layerStack.getActiveLayer();
        if (active == null) return;
        undoStack.push(new LayerSnapshot(layerStack.getActiveIndex(), active.copyPixelsOut()));
        if (undoStack.size() > MAX_UNDO) {
            ((ArrayDeque<LayerSnapshot>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (undoStack.isEmpty()) return;
        LayerSnapshot snapshot = undoStack.pop();
        int idx = snapshot.layerIndex();
        if (idx >= 0 && idx < layerStack.getLayerCount()) {
            Layer layer = layerStack.getLayers().get(idx);
            redoStack.push(new LayerSnapshot(idx, layer.copyPixelsOut()));
            layer.setPixels(snapshot.pixels());
        }
        dirty = true;
        cacheValid = false;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        LayerSnapshot snapshot = redoStack.pop();
        int idx = snapshot.layerIndex();
        if (idx >= 0 && idx < layerStack.getLayerCount()) {
            Layer layer = layerStack.getLayers().get(idx);
            undoStack.push(new LayerSnapshot(idx, layer.copyPixelsOut()));
            layer.setPixels(snapshot.pixels());
        }
        dirty = true;
        cacheValid = false;
    }

    /**
     * Draw a single pixel on active layer (pencil tool).
     */
    public void drawPixel(int x, int y, int color) {
        setPixel(x, y, color);
    }

    /**
     * Erase a pixel on the active layer (set to transparent). Shows toast feedback with cooldown.
     * Only shows the 'layer already empty' message if not shown in the last 5 seconds.
     */
    public void erasePixel(int x, int y) {
        Layer active = layerStack.getActiveLayer();
        if (active == null) return;
        long now = System.currentTimeMillis();
        if (active.isEmpty()) {
            if (now - lastLayerEmptyToast > TOAST_COOLDOWN_MS) {
                MinecraftClient.getInstance().getToastManager().add(SystemToast.create(MinecraftClient.getInstance(), SystemToast.Type.PACK_LOAD_FAILURE, Text.literal("Layer is already completely empty!"), Text.empty()));
                lastLayerEmptyToast = now;
            }
            return;
        }
        setPixel(x, y, 0x00000000);
    }

    /**
     * Flood fill from (x, y) with the given color on the active layer.
     */
    public void floodFill(int x, int y, int color) {
        Layer active = layerStack.getActiveLayer();
        if (active == null) return;
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int targetColor = active.getPixel(x, y);
        if (targetColor == color) return;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{x, y});
        boolean[][] visited = new boolean[width][height];

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int px = pos[0], py = pos[1];
            if (px < 0 || px >= width || py < 0 || py >= height) continue;
            if (visited[px][py]) continue;
            if (active.getPixel(px, py) != targetColor) continue;

            visited[px][py] = true;
            active.setPixel(px, py, color);

            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
        dirty = true;
        cacheValid = false;
    }

    /**
     * Draw a line using Bresenham's algorithm on active layer.
     */
    public void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            setPixel(x0, y0, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /**
     * Pick color at position from the active layer (eyedropper).
     */
    public int pickColor(int x, int y) {
        Layer active = layerStack.getActiveLayer();
        if (active == null) return getPixel(x, y); // fallback to composited
        return active.getPixel(x, y);
    }

    /**
     * Invalidate the flattened cache (call when layers change externally).
     */
    public void invalidateCache() {
        cacheValid = false;
    }
}
