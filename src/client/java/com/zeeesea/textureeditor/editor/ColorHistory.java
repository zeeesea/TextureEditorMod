package com.zeeesea.textureeditor.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Global color history that persists across editor sessions.
 * Stores recently used colors so they can be reused between blocks/mobs/items.
 */
public class ColorHistory {
    private static final ColorHistory INSTANCE = new ColorHistory();
    private static final int MAX_HISTORY = 20;

    private final List<Integer> history = new ArrayList<>();

    private ColorHistory() {}

    public static ColorHistory getInstance() {
        return INSTANCE;
    }

    /**
     * Add a color to history. Moves it to front if already present.
     */
    public void addColor(int color) {
        // Remove if already in history (avoid duplicates)
        history.remove(Integer.valueOf(color));
        // Add to front
        history.add(0, color);
        // Trim
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * Get all colors in history (most recent first).
     */
    public List<Integer> getColors() {
        return history;
    }

    public int size() {
        return history.size();
    }
}
