ackage com.zeeesea.textureeditor.editor;

import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.settings.ModSettings;
import net.minecraft.client.gui.components.toasts.SystemToast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Editable pixel buffer with layers, undo/redo support and drawing tool operations.
 * Drawing operations affect the active layer. getPixel returns the composited result.
 */
public class PixelCanvas {
    private final int width;
    private final int height;
    private LayerStack layerStack;
    private final Deque<LayerSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<LayerSnapshot> redoStack = new ArrayDeque<>();
    private static int MAX_UNDO = 50;
    private boolean dirty = false;
    private long version = 0;

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
        this.layerStack = new LayerStack(width, height, ModSettings.getInstance().oneLayerByDefault);
        MAX_UNDO = ModSettings.getInstance().maxUndoSteps;
    }

    public PixelCanvas(int width, int height, int[][] initialPixels) {
        this.width = width;
        this.height = height;
        this.layerStack = new LayerStack(width, height, initialPixels, ModSettings.getInstance().oneLayerByDefault);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public LayerStack getLayerStack() { return layerStack; }
    public void setLayerStack(LayerStack layerStack) {
        this.layerStack = layerStack;
    }

    /**
     * Get the composited pixel at (x, y) - flattening all visible layers.
     */
    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        if (!cacheValid) {
            // Use full flatten (alpha compositing) so semi-transparent pixels
            // on upper layers correctly blend with lower layers instead of
            // simply taking the topmost non-transparent pixel.
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
        invalidateCache();
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
        invalidateCache();
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
        invalidateCache();
    }

    /**
     * Draw a single pixel on active layer (pencil tool).
     */
    public void drawPixel(int x, int y, int color) {
        setPixel(x, y, color);
    }

    /**
     * Draw a pixel with random brightness variation (brush tool).
     * @param variation brightness variation strength (0.0-1.0, e.g. 0.15 = ±15%)
     */
    public void drawBrushPixel(int x, int y, int color, float variation) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float offset = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * variation;
        r = clamp((int) (r + r * offset), 0, 255);
        g = clamp((int) (g + g * offset), 0, 255);
        b = clamp((int) (b + b * offset), 0, 255);

        setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
    }

    /**
     * Draw a pixel area (for tool size > 1).
     */
    public void drawPixelArea(int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int dx = -half; dx < size - half; dx++) {
            for (int dy = -half; dy < size - half; dy++) {
                drawPixel(cx + dx, cy + dy, color);
            }
        }
    }

    /**
     * Erase a pixel area (for tool size > 1).
     */
    public void erasePixelArea(int cx, int cy, int size) {
        int half = size / 2;
        for (int dx = -half; dx < size - half; dx++) {
            for (int dy = -half; dy < size - half; dy++) {
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    setPixel(x, y, 0x00000000);
                }
            }
        }
    }

    /**
     * Draw a brush area with variation (for tool size > 1).
     */
    public void drawBrushArea(int cx, int cy, int size, int color, float variation) {
        int half = size / 2;
        for (int dx = -half; dx < size - half; dx++) {
            for (int dy = -half; dy < size - half; dy++) {
                drawBrushPixel(cx + dx, cy + dy, color, variation);
            }
        }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Erase a pixel on the active layer (set to transparent). Shows toast feedback with cooldown.
     * Only shows the 'layer already empty' message if not shown in the last 5 seconds.
     */
    public void erasePixel(int x, int y) {
        Layer active = layerStack.getActiveLayer();
        if (active == null) return;
        if (active.isEmpty()) {
            NotificationHelper.addToast(SystemToast.SystemToastId.PACK_LOAD_FAILURE, "Layer is already completely empty!");
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
        invalidateCache();
    }

    /**
     * Compute the set of coordinates that would be affected by a flood fill on the active layer
     * starting at (x,y). Returns a list of int[]{px,py} positions. Does not modify the canvas.
     */
    public java.util.List<int[]> computeFloodRegion(int x, int y) {
        java.util.List<int[]> acc = new java.util.ArrayList<>();
        Layer active = layerStack.getActiveLayer();
        if (active == null) return acc;
        if (x < 0 || x >= width || y < 0 || y >= height) return acc;
        int targetColor = active.getPixel(x, y);

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
            acc.add(new int[]{px, py});

            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
        return acc;
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
     * Draw a line with given integer thickness (in pixels) by drawing pixel areas along the Bresenham line.
     */
    public void drawLineThickness(int x0, int y0, int x1, int y1, int color, int size) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            drawPixelArea(x0, y0, size, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /**
     * Draw a line with given thickness and optional variation. If variation <= 0 the draw is solid.
     */
    public void drawLineThickness(int x0, int y0, int x1, int y1, int color, int size, float variation) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (variation > 0f) drawBrushArea(x0, y0, size, color, variation);
            else drawPixelArea(x0, y0, size, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /**
     * Draw a line (1px thickness) with optional variation.
     */
    public void drawLine(int x0, int y0, int x1, int y1, int color, float variation) {
        if (variation > 0f) {
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;

            while (true) {
                drawBrushPixel(x0, y0, color, variation);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 > -dy) { err -= dy; x0 += sx; }
                if (e2 < dx) { err += dx; y0 += sy; }
            }
        } else {
            drawLine(x0, y0, x1, y1, color);
        }
    }

    /**
     * Draw a filled rectangle on the active layer defined by two corners (inclusive).
     */
    public void drawRect(int x0, int y0, int x1, int y1, int color) {
        // Draw only the 1px outline of the rectangle defined by two corners (inclusive).
        int sx = Math.min(x0, x1);
        int ex = Math.max(x0, x1);
        int sy = Math.min(y0, y1);
        int ey = Math.max(y0, y1);
        // Top and bottom edges
        for (int x = sx; x <= ex; x++) {
            setPixel(x, sy, color);
            setPixel(x, ey, color);
        }
        // Left and right edges
        for (int y = sy; y <= ey; y++) {
            setPixel(sx, y, color);
            setPixel(ex, y, color);
        }
    }

    /**
     * Draw a rectangle outline with given thickness (in pixels). Thickness is applied by painting
     * pixel areas along the edges.
     */
    public void drawRectOutlineThickness(int x0, int y0, int x1, int y1, int color, int size) {
        int sx = Math.min(x0, x1);
        int ex = Math.max(x0, x1);
        int sy = Math.min(y0, y1);
        int ey = Math.max(y0, y1);
        // Top and bottom edges
        for (int x = sx; x <= ex; x++) {
            drawPixelArea(x, sy, size, color);
            if (ey != sy) drawPixelArea(x, ey, size, color);
        }
        // Left and right edges
        for (int y = sy; y <= ey; y++) {
            drawPixelArea(sx, y, size, color);
            if (ex != sx) drawPixelArea(ex, y, size, color);
        }
    }

    /**
     * Draw a rectangle outline with thickness and optional variation.
     */
    public void drawRectOutlineThickness(int x0, int y0, int x1, int y1, int color, int size, float variation) {
        int sx = Math.min(x0, x1);
        int ex = Math.max(x0, x1);
        int sy = Math.min(y0, y1);
        int ey = Math.max(y0, y1);
        // Top and bottom edges
        for (int x = sx; x <= ex; x++) {
            if (variation > 0f) {
                drawBrushArea(x, sy, size, color, variation);
                if (ey != sy) drawBrushArea(x, ey, size, color, variation);
            } else {
                drawPixelArea(x, sy, size, color);
                if (ey != sy) drawPixelArea(x, ey, size, color);
            }
        }
        // Left and right edges
        for (int y = sy; y <= ey; y++) {
            if (variation > 0f) {
                drawBrushArea(sx, y, size, color, variation);
                if (ex != sx) drawBrushArea(ex, y, size, color, variation);
            } else {
                drawPixelArea(sx, y, size, color);
                if (ex != sx) drawPixelArea(ex, y, size, color);
            }
        }
    }

    /**
     * Flood fill with optional variation applied when setting pixels.
     */
    public void floodFill(int x, int y, int color, float variation) {
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
            if (variation > 0f) drawBrushPixel(px, py, color, variation);
            else active.setPixel(px, py, color);

            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
        dirty = true;
        invalidateCache();
    }

    /**
     * Pick color at position from the active layer (eyedropper).
     */
    public int pickColorOnActiveLayer(int x, int y) {
        Layer active = layerStack.getActiveLayer();
        if (active == null) return getPixel(x, y); // fallback to composited
        return active.getPixel(x, y);
    }

    /**
     * Pick color at position from composited Texture (color on top layer) (eyedropper).
     */
    public int pickColorComposited(int x, int y) {
        return getPixel(x, y);
    }

    /**
     * Invalidate the flattened cache (call when layers change externally).
     */
    public void invalidateCache() {
        cacheValid = false;
        version++;
    }

    /** Returns a version counter that increments on any change. */
    public long getVersion() { return version; }
}
