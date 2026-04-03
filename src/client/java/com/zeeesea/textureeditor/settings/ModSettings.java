package com.zeeesea.textureeditor.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    // Fill tool options
    public int fillTolerance = 0; // 0..255 (0 = exact match)
    public boolean fillContiguous = true;
    public boolean fillWholeCanvas = false;

    public boolean multiplayerSync = false;


    // External editor
    public boolean useExternalEditor = false;
    public String selectedEditorName = ""; // name from auto-detected list
    public String externalEditorCustomPath = ""; // custom path overrides auto-detect
    
    // UI color preset (name of ColorPalette.Preset)
    public String colorPreset = "DEFAULT";

    // Palette profiles (name -> list of ARGB hex strings, e.g. FFFF0000).
    public Map<String, List<String>> paletteProfiles = new LinkedHashMap<>();
    public String activePaletteProfile = "Default";

    private static final int[] DEFAULT_EDITOR_PALETTE = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF,
            0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF80FF00,
            0xFF00FF00, 0xFF00FF80, 0xFF00FFFF, 0xFF0080FF,
            0xFF0000FF, 0xFF8000FF, 0xFFFF00FF, 0xFFFF0080,
            0xFF800000, 0xFF804000, 0xFF808000, 0xFF408000,
            0xFF008000, 0xFF008040, 0xFF008080, 0xFF004080,
            0xFF000080, 0xFF400080, 0xFF800080, 0xFF800040,
            0xFFFFB3B3, 0xFFFFD9B3, 0xFFFFFFB3, 0xFFB3FFB3, 0xFFB3FFFF
    };

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModSettings() {
        resetKeybinds();
        ensurePaletteProfilesInitialized();
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
        ensurePaletteProfilesInitialized();
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
                    settings.ensurePaletteProfilesInitialized();
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

    public synchronized List<Integer> getActivePaletteColors() {
        ensurePaletteProfilesInitialized();
        List<String> encoded = paletteProfiles.get(activePaletteProfile);
        if (encoded == null || encoded.isEmpty()) {
            encoded = toHexList(DEFAULT_EDITOR_PALETTE);
            paletteProfiles.put(activePaletteProfile, encoded);
        }
        List<Integer> out = new ArrayList<>(encoded.size());
        for (String color : encoded) {
            out.add(parseColorHex(color));
        }
        if (out.isEmpty()) {
            for (int c : DEFAULT_EDITOR_PALETTE) out.add(c);
        }
        return out;
    }

    public synchronized void setActivePaletteColors(List<Integer> colors) {
        ensurePaletteProfilesInitialized();
        List<String> encoded = new ArrayList<>();
        if (colors != null) {
            for (Integer c : colors) {
                if (c == null) continue;
                encoded.add(toHexColor(c));
            }
        }
        if (encoded.isEmpty()) {
            encoded = toHexList(DEFAULT_EDITOR_PALETTE);
        }
        paletteProfiles.put(activePaletteProfile, encoded);
        save();
    }

    public synchronized String getActivePaletteProfileName() {
        ensurePaletteProfilesInitialized();
        return activePaletteProfile;
    }

    public synchronized List<String> getPaletteProfileNames() {
        ensurePaletteProfilesInitialized();
        return new ArrayList<>(paletteProfiles.keySet());
    }

    public synchronized boolean setActivePaletteProfile(String profileName) {
        ensurePaletteProfilesInitialized();
        if (profileName == null || !paletteProfiles.containsKey(profileName)) return false;
        activePaletteProfile = profileName;
        save();
        return true;
    }

    public synchronized String cyclePaletteProfile() {
        ensurePaletteProfilesInitialized();
        List<String> names = new ArrayList<>(paletteProfiles.keySet());
        if (names.isEmpty()) {
            activePaletteProfile = "Default";
            paletteProfiles.put(activePaletteProfile, toHexList(DEFAULT_EDITOR_PALETTE));
            save();
            return activePaletteProfile;
        }
        int idx = names.indexOf(activePaletteProfile);
        if (idx < 0) idx = 0;
        int next = (idx + 1) % names.size();
        activePaletteProfile = names.get(next);
        save();
        return activePaletteProfile;
    }

    public synchronized String createPaletteProfile(String preferredName, List<Integer> seedColors) {
        ensurePaletteProfilesInitialized();
        String base = preferredName == null || preferredName.isBlank() ? "Profile" : preferredName.trim();
        String name = base;
        int i = 2;
        while (paletteProfiles.containsKey(name)) {
            name = base + " " + i;
            i++;
        }
        List<String> encoded = new ArrayList<>();
        if (seedColors != null) {
            for (Integer c : seedColors) if (c != null) encoded.add(toHexColor(c));
        }
        if (encoded.isEmpty()) encoded = toHexList(DEFAULT_EDITOR_PALETTE);
        paletteProfiles.put(name, encoded);
        activePaletteProfile = name;
        save();
        return name;
    }

    public synchronized boolean deleteActivePaletteProfile() {
        ensurePaletteProfilesInitialized();
        if (paletteProfiles.size() <= 1) return false;
        paletteProfiles.remove(activePaletteProfile);
        if (!paletteProfiles.containsKey(activePaletteProfile)) {
            activePaletteProfile = paletteProfiles.keySet().iterator().next();
        }
        save();
        return true;
    }

    public synchronized boolean renameActivePaletteProfile(String newName) {
        ensurePaletteProfilesInitialized();
        if (newName == null) return false;
        String cleaned = newName.trim();
        if (cleaned.isEmpty() || paletteProfiles.containsKey(cleaned)) return false;
        List<String> colors = paletteProfiles.remove(activePaletteProfile);
        if (colors == null) colors = toHexList(DEFAULT_EDITOR_PALETTE);
        paletteProfiles.put(cleaned, colors);
        activePaletteProfile = cleaned;
        save();
        return true;
    }

    public synchronized void resetActivePaletteToDefault() {
        ensurePaletteProfilesInitialized();
        paletteProfiles.put(activePaletteProfile, toHexList(DEFAULT_EDITOR_PALETTE));
        save();
    }

    private void ensurePaletteProfilesInitialized() {
        if (paletteProfiles == null) paletteProfiles = new LinkedHashMap<>();
        if (paletteProfiles.isEmpty()) {
            paletteProfiles.put("Default", toHexList(DEFAULT_EDITOR_PALETTE));
        }
        if (activePaletteProfile == null || activePaletteProfile.isBlank() || !paletteProfiles.containsKey(activePaletteProfile)) {
            activePaletteProfile = paletteProfiles.keySet().iterator().next();
        }
    }

    private static List<String> toHexList(int[] colors) {
        List<String> out = new ArrayList<>(colors.length);
        for (int c : colors) out.add(toHexColor(c));
        return out;
    }

    private static String toHexColor(int argb) {
        String hex = Integer.toHexString(argb).toUpperCase();
        while (hex.length() < 8) hex = "0" + hex;
        return hex;
    }

    private static int parseColorHex(String hex) {
        if (hex == null) return 0xFFFFFFFF;
        String cleaned = hex.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        try {
            if (cleaned.length() == 6) {
                return (int) Long.parseLong("FF" + cleaned, 16);
            }
            if (cleaned.length() == 8) {
                return (int) Long.parseLong(cleaned, 16);
            }
        } catch (Exception ignored) {}
        return 0xFFFFFFFF;
    }
}
