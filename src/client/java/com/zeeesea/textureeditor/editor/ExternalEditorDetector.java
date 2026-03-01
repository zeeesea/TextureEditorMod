package com.zeeesea.textureeditor.editor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-detects installed image editing programs on Windows.
 */
public class ExternalEditorDetector {

    public record DetectedEditor(String name, String executablePath) {}

    private static List<DetectedEditor> cachedEditors = null;

    /**
     * Scan for known image editors. Results are cached.
     */
    public static List<DetectedEditor> detectEditors() {
        if (cachedEditors != null) return cachedEditors;
        cachedEditors = new ArrayList<>();

        // MS Paint — try multiple detection methods
        detectMSPaint();

        // Paint.NET
        String[] programDirs = {
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                System.getenv("LOCALAPPDATA")
        };
        for (String dir : programDirs) {
            if (dir == null) continue;
            Path paintNet = Path.of(dir, "paint.net", "PaintDotNet.exe");
            if (Files.exists(paintNet)) {
                cachedEditors.add(new DetectedEditor("Paint.NET", paintNet.toString()));
                break;
            }
        }

        // Aseprite — check common install locations
        for (String dir : programDirs) {
            if (dir == null) continue;
            Path aseprite = Path.of(dir, "Aseprite", "Aseprite.exe");
            if (Files.exists(aseprite)) {
                cachedEditors.add(new DetectedEditor("Aseprite", aseprite.toString()));
                break;
            }
        }
        // Also check Steam Aseprite
        Path steamAseprite = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Aseprite", "Aseprite.exe");
        if (Files.exists(steamAseprite) && cachedEditors.stream().noneMatch(e -> e.name().equals("Aseprite"))) {
            cachedEditors.add(new DetectedEditor("Aseprite", steamAseprite.toString()));
        }
        // Steam common location
        for (String dir : programDirs) {
            if (dir == null) continue;
            Path steamAse = Path.of(dir, "Steam", "steamapps", "common", "Aseprite", "Aseprite.exe");
            if (Files.exists(steamAse) && cachedEditors.stream().noneMatch(e -> e.name().equals("Aseprite"))) {
                cachedEditors.add(new DetectedEditor("Aseprite", steamAse.toString()));
                break;
            }
        }

        // Adobe Photoshop — search Program Files for any version
        for (String dir : programDirs) {
            if (dir == null) continue;
            File adobeDir = new File(dir, "Adobe");
            if (adobeDir.isDirectory()) {
                File[] children = adobeDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory() && child.getName().contains("Photoshop")) {
                            File ps = new File(child, "Photoshop.exe");
                            if (ps.exists()) {
                                cachedEditors.add(new DetectedEditor("Photoshop", ps.getAbsolutePath()));
                                break;
                            }
                        }
                    }
                }
            }
        }

        // GIMP
        for (String dir : programDirs) {
            if (dir == null) continue;
            File gimpBase = new File(dir);
            File[] children = gimpBase.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && child.getName().toLowerCase().contains("gimp")) {
                        File gimp = new File(child, "bin" + File.separator + "gimp-2.10.exe");
                        if (gimp.exists()) {
                            cachedEditors.add(new DetectedEditor("GIMP", gimp.getAbsolutePath()));
                            break;
                        }
                        // Try generic gimp.exe
                        gimp = new File(child, "bin" + File.separator + "gimp.exe");
                        if (gimp.exists()) {
                            cachedEditors.add(new DetectedEditor("GIMP", gimp.getAbsolutePath()));
                            break;
                        }
                    }
                }
            }
        }

        return cachedEditors;
    }

    /**
     * Detect MS Paint via multiple methods:
     * 1. Classic system32/mspaint.exe
     * 2. Windows Store version (via 'where mspaint' command)
     * 3. Fallback: use "mspaint" command directly (works if it's on PATH)
     */
    private static void detectMSPaint() {
        // Method 1: Classic path
        String system32 = System.getenv("SystemRoot");
        if (system32 != null) {
            Path mspaint = Path.of(system32, "system32", "mspaint.exe");
            if (Files.exists(mspaint)) {
                cachedEditors.add(new DetectedEditor("MS Paint", mspaint.toString()));
                return;
            }
        }

        // Method 2: Try 'where mspaint' to find it on PATH (works for both classic and Store versions)
        try {
            ProcessBuilder pb = new ProcessBuilder("where", "mspaint");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = reader.readLine();
            proc.waitFor();
            if (line != null && !line.isEmpty() && new File(line.trim()).exists()) {
                cachedEditors.add(new DetectedEditor("MS Paint", line.trim()));
                return;
            }
        } catch (Exception ignored) {}

        // Method 3: Try 'where mspaint.exe'
        try {
            ProcessBuilder pb = new ProcessBuilder("where", "mspaint.exe");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = reader.readLine();
            proc.waitFor();
            if (line != null && !line.isEmpty() && new File(line.trim()).exists()) {
                cachedEditors.add(new DetectedEditor("MS Paint", line.trim()));
                return;
            }
        } catch (Exception ignored) {}

        // Method 4: Fallback — just use "mspaint" as command (it may be a UWP alias)
        // We verify by trying to check if the command exists
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "where", "mspaint");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = reader.readLine();
            int exitCode = proc.waitFor();
            if (exitCode == 0 && line != null && !line.isEmpty()) {
                // The command exists on PATH, use "mspaint" directly
                cachedEditors.add(new DetectedEditor("MS Paint", "mspaint"));
                return;
            }
        } catch (Exception ignored) {}

        // Method 5: Check WindowsApps folder for Store version
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            File windowsApps = new File(localAppData, "Microsoft" + File.separator + "WindowsApps");
            if (windowsApps.isDirectory()) {
                File[] files = windowsApps.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().toLowerCase().contains("mspaint") && f.getName().endsWith(".exe")) {
                            cachedEditors.add(new DetectedEditor("MS Paint", f.getAbsolutePath()));
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Force re-scan (e.g., after user installs a new editor).
     */
    public static void clearCache() {
        cachedEditors = null;
    }

    /**
     * Get the default editor — the first detected one.
     */
    public static DetectedEditor getDefault() {
        List<DetectedEditor> editors = detectEditors();
        return editors.isEmpty() ? null : editors.get(0);
    }
}
