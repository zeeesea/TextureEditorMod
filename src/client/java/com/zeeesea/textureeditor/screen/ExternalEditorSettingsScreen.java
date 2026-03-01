package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.ExternalEditorDetector;
import com.zeeesea.textureeditor.editor.ExternalEditorManager;
import com.zeeesea.textureeditor.settings.ModSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

/**
 * Settings screen for configuring the external image editor integration.
 */
public class ExternalEditorSettingsScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget customPathField;
    private int detectedEditorsEndY; // track where detected editors end for render

    public ExternalEditorSettingsScreen(Screen parent) {
        super(Text.literal("External Editor Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();
        int centerX = this.width / 2;
        int y = 35;

        // Toggle: Use External Editor
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Use External Editor: " + (s.useExternalEditor ? "\u00a7aON" : "\u00a7cOFF")),
                btn -> {
                    s.useExternalEditor = !s.useExternalEditor;
                    s.save();
                    this.clearChildren();
                    this.init();
                }
        ).position(centerX - 120, y).size(240, 20).build());
        y += 30;

        // Detected editors section — label drawn in render(), buttons below
        List<ExternalEditorDetector.DetectedEditor> detected = ExternalEditorDetector.detectEditors();

        if (!detected.isEmpty()) {
            y += 14; // space for "Detected Editors:" label
            for (ExternalEditorDetector.DetectedEditor editor : detected) {
                boolean isSelected = editor.name().equals(s.selectedEditorName);
                String label = (isSelected ? "\u00a7a\u2714 " : "\u00a77  ") + editor.name();
                addDrawableChild(ButtonWidget.builder(
                        Text.literal(label),
                        btn -> {
                            s.selectedEditorName = editor.name();
                            s.save();
                            this.clearChildren();
                            this.init();
                        }
                ).position(centerX - 120, y).size(240, 20).build());
                y += 22;
            }
        } else {
            y += 18; // space for "No editors detected" text
        }
        detectedEditorsEndY = y;

        y += 8;

        // Custom path label drawn in render()
        y += 12;
        // Custom path field
        customPathField = new TextFieldWidget(this.textRenderer, centerX - 120, y, 240, 18, Text.literal("Custom Path"));
        customPathField.setMaxLength(500);
        customPathField.setText(s.externalEditorCustomPath != null ? s.externalEditorCustomPath : "");
        customPathField.setChangedListener(text -> {
            s.externalEditorCustomPath = text.trim();
            s.save();
        });
        addDrawableChild(customPathField);
        y += 28;

        // Refresh detection button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7eRefresh Detection"),
                btn -> {
                    ExternalEditorDetector.clearCache();
                    this.clearChildren();
                    this.init();
                }
        ).position(centerX - 120, y).size(115, 20).build());

        // Delete Cache button — deletes all temp files
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7cDelete Cache"),
                btn -> {
                    ExternalEditorManager.deleteCache();
                    net.minecraft.client.MinecraftClient.getInstance().execute(() ->
                        com.zeeesea.textureeditor.helper.NotificationHelper.addToast(
                            net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION,
                            "Cache Cleared", "All temp files deleted.")
                    );
                }
        ).position(centerX + 5, y).size(115, 20).build());
        y += 24;

        // Reset All Textures button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7cReset All Textures"),
                btn -> {
                    com.zeeesea.textureeditor.texture.TextureManager.getInstance().clear();
                    ExternalEditorManager.deleteCache();
                    net.minecraft.client.MinecraftClient.getInstance().reloadResources();
                }
        ).position(centerX - 120, y).size(240, 20).build());
        y += 30;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a7l\u00a76External Editor Settings", this.width / 2, 10, 0xFFFFFF);

        int centerX = this.width / 2;

        // "Detected Editors:" label
        List<ExternalEditorDetector.DetectedEditor> detected = ExternalEditorDetector.detectEditors();
        int labelY = 35 + 30; // after toggle button
        if (!detected.isEmpty()) {
            context.drawText(textRenderer, "\u00a7eDetected Editors:", centerX - 118, labelY, 0xFFFFFF, false);
        } else {
            context.drawText(textRenderer, "\u00a7cNo editors auto-detected", centerX - 118, labelY + 4, 0xFF5555, false);
        }

        // "Custom Editor Path" label
        int customLabelY = detectedEditorsEndY + 8;
        context.drawText(textRenderer, "Custom Editor Path (overrides auto):", centerX - 118, customLabelY, 0xAAAAAA, false);

        // Show status at bottom
        ModSettings s = ModSettings.getInstance();
        String editorPath = getResolvedPath(s);
        int statusY = this.height - 20;
        if (editorPath != null && !editorPath.isEmpty()) {
            boolean exists = new File(editorPath).exists() || editorPath.equals("mspaint");
            String status = exists ? "\u00a7aCurrent: " + shortenPath(editorPath) : "\u00a7cNot found: " + shortenPath(editorPath);
            context.drawCenteredTextWithShadow(textRenderer, status, centerX, statusY, 0xFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, "\u00a77No editor configured", centerX, statusY, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String getResolvedPath(ModSettings s) {
        if (s.externalEditorCustomPath != null && !s.externalEditorCustomPath.isEmpty()) {
            return s.externalEditorCustomPath;
        }
        if (s.selectedEditorName != null && !s.selectedEditorName.isEmpty()) {
            for (var editor : ExternalEditorDetector.detectEditors()) {
                if (editor.name().equals(s.selectedEditorName)) return editor.executablePath();
            }
        }
        var def = ExternalEditorDetector.getDefault();
        return def != null ? def.executablePath() : null;
    }

    private String shortenPath(String path) {
        if (path.length() > 60) {
            return "..." + path.substring(path.length() - 57);
        }
        return path;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int m) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(kc, sc, m);
    }

    @Override
    public boolean shouldPause() { return false; }
}
