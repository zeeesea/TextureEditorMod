package com.zeeesea.textureeditor.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a stack of layers with compositing, active layer tracking, and per-layer undo/redo.
 */
public class LayerStack {
    private final int width;
    private final int height;
    private final List<Layer> layers = new ArrayList<>();
    private int activeIndex = 0;

    public LayerStack(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Create a layer stack with a base layer initialized from existing pixels.
     */
    public LayerStack(int width, int height, int[][] basePixels) {
        this.width = width;
        this.height = height;
        layers.add(new Layer(width, height, "Base", basePixels));
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public List<Layer> getLayers() { return layers; }
    public int getLayerCount() { return layers.size(); }

    public int getActiveIndex() { return activeIndex; }
    public void setActiveIndex(int index) {
        if (index >= 0 && index < layers.size()) {
            this.activeIndex = index;
        }
    }

    public Layer getActiveLayer() {
        if (activeIndex >= 0 && activeIndex < layers.size()) {
            return layers.get(activeIndex);
        }
        return null;
    }

    public void addLayer(String name) {
        Layer layer = new Layer(width, height, name);
        layers.add(layer);
        activeIndex = layers.size() - 1;
    }

    public void addLayerAbove(String name) {
        Layer layer = new Layer(width, height, name);
        int insertAt = Math.min(activeIndex + 1, layers.size());
        layers.add(insertAt, layer);
        activeIndex = insertAt;
    }

    public void removeLayer(int index) {
        if (layers.size() <= 1) return; // Keep at least one layer
        if (index < 0 || index >= layers.size()) return;
        layers.remove(index);
        if (activeIndex >= layers.size()) {
            activeIndex = layers.size() - 1;
        }
    }

    public void moveLayerUp(int index) {
        if (index <= 0 || index >= layers.size()) return;
        Layer l = layers.remove(index);
        layers.add(index - 1, l);
        if (activeIndex == index) activeIndex = index - 1;
        else if (activeIndex == index - 1) activeIndex = index;
    }

    public void moveLayerDown(int index) {
        if (index < 0 || index >= layers.size() - 1) return;
        Layer l = layers.remove(index);
        layers.add(index + 1, l);
        if (activeIndex == index) activeIndex = index + 1;
        else if (activeIndex == index + 1) activeIndex = index;
    }

    /**
     * Flatten all visible layers into a single pixel array using alpha compositing.
     * Layers are composited bottom (index 0) to top (last index).
     */
    public int[][] flatten() {
        int[][] result = new int[width][height];
        // Start with transparent
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                result[x][y] = 0x00000000;
            }
        }

        // Composite bottom-to-top
        for (Layer layer : layers) {
            if (!layer.isVisible()) continue;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int src = layer.getPixel(x, y);
                    int dst = result[x][y];
                    result[x][y] = alphaBlend(src, dst);
                }
            }
        }
        return result;
    }

    /**
     * Alpha-blend src over dst (both ARGB).
     */
    private static int alphaBlend(int src, int dst) {
        int srcA = (src >> 24) & 0xFF;
        if (srcA == 0) return dst;
        if (srcA == 255) return src;

        int srcR = (src >> 16) & 0xFF, srcG = (src >> 8) & 0xFF, srcB = src & 0xFF;
        int dstA = (dst >> 24) & 0xFF;
        int dstR = (dst >> 16) & 0xFF, dstG = (dst >> 8) & 0xFF, dstB = dst & 0xFF;

        int outA = srcA + dstA * (255 - srcA) / 255;
        if (outA == 0) return 0;

        int outR = (srcR * srcA + dstR * dstA * (255 - srcA) / 255) / outA;
        int outG = (srcG * srcA + dstG * dstA * (255 - srcA) / 255) / outA;
        int outB = (srcB * srcA + dstB * dstA * (255 - srcA) / 255) / outA;

        return (outA << 24) | (Math.min(255, outR) << 16) | (Math.min(255, outG) << 8) | Math.min(255, outB);
    }
}
