package com.zeeesea.textureeditor.editor;

/**
 * A single layer in the layer stack. Contains its own pixel data, visibility flag, and name.
 */
public class Layer {
    private final int width;
    private final int height;
    private int[][] pixels;
    private boolean visible = true;
    private String name;

    public Layer(int width, int height, String name) {
        this.width = width;
        this.height = height;
        this.name = name;
        this.pixels = new int[width][height];
    }

    public Layer(int width, int height, String name, int[][] initialPixels) {
        this.width = width;
        this.height = height;
        this.name = name;
        this.pixels = copyPixels(initialPixels, width, height);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return pixels[x][y];
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        pixels[x][y] = color;
    }

    public int[][] getPixels() { return pixels; }

    public void setPixels(int[][] newPixels) {
        this.pixels = copyPixels(newPixels, width, height);
    }

    public int[][] copyPixelsOut() {
        return copyPixels(pixels, width, height);
    }

    /**
     * Gibt true zurück, wenn alle Pixel der Layer transparent (0x00000000) sind.
     */
    public boolean isEmpty() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if ((pixels[x][y] & 0xFF000000) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[][] copyPixels(int[][] src, int w, int h) {
        int[][] copy = new int[w][h];
        for (int x = 0; x < w; x++) {
            System.arraycopy(src[x], 0, copy[x], 0, h);
        }
        return copy;
    }
}
