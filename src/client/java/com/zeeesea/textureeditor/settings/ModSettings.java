package com.zeeesea.textureeditor.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent mod settings stored in config/textureeditor.json.
 */
public class ModSettings {
    private static ModSettings instance;

    // Editor defaults
    public int defaultZoom = 12;
    public boolean gridOnByDefault = false;
    public boolean modEnabled = true;
    public String defaultTool = "PENCIL"; // EditorTool enum name

    // Layers preference: when true, new textures open with only a single "Base" layer;
    // when false (default) they open with Base + Layer 0 as before.
    public boolean oneLayerByDefault = false;

    // Editor keybinds (GLFW key codes)
    public Map<String, Integer> keybinds = new HashMap<>();

    // Behavior
    public boolean showToolHints = true;
    public boolean autoApplyLive = true;
    public boolean confirmResetAll = true;
    public int maxUndoSteps = 50;
    public int colorHistorySize = 20;
    public float brushVariation = 0.15f; // legacy, kept for compatibility
    // Global variation percent applied to tools; 0.0 means OFF
    public float variationPercent = 0.0f;

    public boolean multiplayerSync = false;


    // External editor
    public boolean useExternalEditor = false;
    public String selectedEditorName = ""; // name from auto-detected list
    public String externalEditorCustomPath = ""; // custom path overrides auto-detect
    
    // UI color preset (name of ColorPalette.Preset)
    public String colorPreset = "DEFAULT";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModSettings() {
        resetKeybinds();
    }

    public static ModSettings getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public void resetKeybinds() {
        keybinds.clear();
        keybinds.put("pencil", GLFW.GLFW_KEY_B);
        keybinds.put("eraser", GLFW.GLFW_KEY_E);
        keybinds.put("fill", GLFW.GLFW_KEY_F);
        keybinds.put("eyedropper", GLFW.GLFW_KEY_I);
        keybinds.put("line", GLFW.GLFW_KEY_L);
        // brush key removed; variation is now a global toggle
        keybinds.put("rectangle", GLFW.GLFW_KEY_T);
        keybinds.put("select", GLFW.GLFW_KEY_Q);
        keybinds.put("undo", GLFW.GLFW_KEY_Z);
        keybinds.put("redo", GLFW.GLFW_KEY_Y);
        keybinds.put("grid", GLFW.GLFW_KEY_G);
        keybinds.put("browse", GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    public int getKeybind(String action) {
        return keybinds.getOrDefault(action, -1);
    }

    public void setKeybind(String action, int keyCode) {
        keybinds.put(action, keyCode);
        save();
    }

    public String getKeyName(String action) {
        int code = getKeybind(action);
        if (code <= 0) return "?";
        String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null) return name.toUpperCase();
        // Fallback for special keys
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            default -> "KEY" + code;
        };
    }

    private static File getConfigFile() {
        File configDir = new File(MinecraftClient.getInstance().runDirectory, "config");
        if (!configDir.exists()) configDir.mkdirs();
        return new File(configDir, "textureeditor.json");
    }

    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(getConfigFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.out.println("[TextureEditor] Failed to save settings: " + e.getMessage());
        }
    }

    private static ModSettings load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                ModSettings settings = GSON.fromJson(reader, ModSettings.class);
                if (settings != null) {
                    // Ensure all default keybinds exist
                    ModSettings defaults = new ModSettings();
                    for (var entry : defaults.keybinds.entrySet()) {
                        settings.keybinds.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    // Set the singleton instance early so callers (e.g. ColorPalette) can safely access settings
                    instance = settings;
                    // Apply stored color preset (if present)
                    try {
                        com.zeeesea.textureeditor.util.ColorPalette.Preset p = com.zeeesea.textureeditor.util.ColorPalette.Preset.valueOf(settings.colorPreset);
                        com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.setPreset(p);
                    } catch (Exception ignored) {}
                    return settings;
                }
            } catch (Exception e) {
                System.out.println("[TextureEditor] Failed to load settings, using defaults: " + e.getMessage());
            }
        }
        ModSettings settings = new ModSettings();
        // Set singleton before applying palette so ColorPalette persistence won't re-enter load
        instance = settings;
        settings.save();
        // Ensure default palette applied on fresh config
        try {
            com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.setPreset(com.zeeesea.textureeditor.util.ColorPalette.Preset.valueOf(settings.colorPreset));
        } catch (Exception ignored) {}
        return settings;
    }

    /** Change and persist the color preset. */
    public void setColorPreset(String presetName) {
        if (presetName == null) return;
        this.colorPreset = presetName;
        try {
            com.zeeesea.textureeditor.util.ColorPalette.Preset p = com.zeeesea.textureeditor.util.ColorPalette.Preset.valueOf(presetName);
            com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.setPreset(p);
        } catch (Exception ignored) {}
        save();
    }
}
