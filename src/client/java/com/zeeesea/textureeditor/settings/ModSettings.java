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
    public boolean gridOnByDefault = true;
    public String defaultTool = "PENCIL"; // EditorTool enum name

    // Editor keybinds (GLFW key codes)
    public Map<String, Integer> keybinds = new HashMap<>();

    // Behavior
    public boolean showToolHints = true;
    public boolean autoApplyLive = false;
    public boolean confirmResetAll = true;
    public int maxUndoSteps = 50;
    public int colorHistorySize = 20;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModSettings() {
        // Default keybinds
        keybinds.put("pencil", GLFW.GLFW_KEY_B);
        keybinds.put("eraser", GLFW.GLFW_KEY_E);
        keybinds.put("fill", GLFW.GLFW_KEY_F);
        keybinds.put("eyedropper", GLFW.GLFW_KEY_I);
        keybinds.put("line", GLFW.GLFW_KEY_L);
        keybinds.put("undo", GLFW.GLFW_KEY_Z);
        keybinds.put("redo", GLFW.GLFW_KEY_Y);
        keybinds.put("grid", GLFW.GLFW_KEY_G);
    }

    public static ModSettings getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
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
                    return settings;
                }
            } catch (Exception e) {
                System.out.println("[TextureEditor] Failed to load settings, using defaults: " + e.getMessage());
            }
        }
        ModSettings settings = new ModSettings();
        settings.save();
        return settings;
    }
}
