package com.zeeesea.textureeditor.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Editable pixel buffer with undo/redo support and drawing tool operations.
 */
public class PixelCanvas {
    private final int width;
    private final int height;
    private int[][] pixels; // ARGB format
    private final Deque<int[][]> undoStack = new ArrayDeque<>();
    private final Deque<int[][]> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;
    private boolean dirty = false;

    public PixelCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width][height];
        // Initialize to transparent
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pixels[x][y] = 0x00000000;
            }
        }
    }

    public PixelCanvas(int width, int height, int[][] initialPixels) {
        this.width = width;
        this.height = height;
        this.pixels = copyPixels(initialPixels);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return pixels[x][y];
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        pixels[x][y] = color;
        dirty = true;
    }

    public int[][] getPixels() {
        return pixels;
    }

    /**
     * Save current state to undo stack before making changes.
     */
    public void saveSnapshot() {
        undoStack.push(copyPixels(pixels));
        if (undoStack.size() > MAX_UNDO) {
            ((ArrayDeque<int[][]>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(copyPixels(pixels));
        pixels = undoStack.pop();
        dirty = true;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(copyPixels(pixels));
        pixels = redoStack.pop();
        dirty = true;
    }

    /**
     * Draw a single pixel (pencil tool).
     */
    public void drawPixel(int x, int y, int color) {
        setPixel(x, y, color);
    }

    /**
     * Erase a pixel (set to transparent).
     */
    public void erasePixel(int x, int y) {
        setPixel(x, y, 0x00000000);
    }

    /**
     * Flood fill from (x, y) with the given color.
     */
    public void floodFill(int x, int y, int color) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int targetColor = pixels[x][y];
        if (targetColor == color) return;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{x, y});
        boolean[][] visited = new boolean[width][height];

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int px = pos[0], py = pos[1];
            if (px < 0 || px >= width || py < 0 || py >= height) continue;
            if (visited[px][py]) continue;
            if (pixels[px][py] != targetColor) continue;

            visited[px][py] = true;
            pixels[px][py] = color;

            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
        dirty = true;
    }

    /**
     * Draw a line using Bresenham's algorithm.
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
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /**
     * Pick color at position (eyedropper).
     */
    public int pickColor(int x, int y) {
        return getPixel(x, y);
    }

    private int[][] copyPixels(int[][] src) {
        int[][] copy = new int[width][height];
        for (int x = 0; x < width; x++) {
            System.arraycopy(src[x], 0, copy[x], 0, height);
        }
        return copy;
    }
}
