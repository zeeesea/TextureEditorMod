package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.ExternalEditorDetector;
import com.zeeesea.textureeditor.editor.ExternalEditorManager;
import com.zeeesea.textureeditor.helper.NotificationHelper;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

/**
 * Settings screen for configuring the external image editor integration.
 * Ported from 1.21.4 to 1.21.11.
 */
public class ExternalEditorSettingsScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget customPathField;
    private int detectedEditorsEndY;
    // scrolling state for tall external editor settings
    private int scrollY = 0;
    private int contentTop = 35;
    private int contentHeight = 0;
    private final java.util.List<Integer> baseYs = new java.util.ArrayList<>();

    public ExternalEditorSettingsScreen(Screen parent) {
        super(Text.literal("External Editor Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();
        int centerX = this.width / 2;
        int startY = 35;
        int y = startY;

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

        // Detected editors section
        List<ExternalEditorDetector.DetectedEditor> detected = ExternalEditorDetector.detectEditors();

        if (!detected.isEmpty()) {
            y += 14;
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
            y += 18;
        }
        detectedEditorsEndY = y;

        y += 8;

        // Custom path label will be drawn in render(); position field below detected editors
        y += 12;
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

        // Delete Cache button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7cDelete Cache"),
                btn -> {
                    ExternalEditorManager.deleteCache();
                    MinecraftClient.getInstance().execute(() ->
                        NotificationHelper.addToast(SystemToast.Type.PERIODIC_NOTIFICATION,
                            "Cache Cleared", "All temp files deleted.")
                    );
                }
        ).position(centerX + 5, y).size(115, 20).build());
        y += 24;

        // Reset All Textures button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7cReset All Textures"),
                btn -> {
                    TextureManager.getInstance().clear();
                    ExternalEditorManager.deleteCache();
                    MinecraftClient.getInstance().reloadResources();
                }
        ).position(centerX - 120, y).size(240, 20).build());
        y += 30;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());

        // record content height and clamp scroll
        contentTop = startY;
        contentHeight = y;

        // capture base Y positions for all child widgets so we can offset them at render time
        baseYs.clear();
        var ch = this.children();
        for (var d : ch) {
            if (d instanceof net.minecraft.client.gui.widget.Widget w) baseYs.add(w.getY());
            else baseYs.add(-1);
        }

        int avail = Math.max(0, this.height - contentTop - 40);
        int maxScroll = Math.max(0, contentHeight - avail);
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.BROWSE_BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a7l\u00a76External Editor Settings", this.width / 2, 10, 0xFFFFFF);

        int centerX = this.width / 2;
        // Render the variable content inside a scissored region so title stays fixed.
        int left = 0;
        int top = contentTop;
        int availH = Math.max(0, this.height - top - 40);

        context.enableScissor(left, top, this.width, top + availH);
        try {
            // offset widget Y positions according to scrollY
            var ch2 = this.children();
            for (int i = 0; i < ch2.size(); i++) {
                var d = ch2.get(i);
                if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                    int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                    if (by >= 0) w.setY(by - scrollY);
                }
            }

            // "Detected Editors:" label (scrolled)
            List<ExternalEditorDetector.DetectedEditor> detected = ExternalEditorDetector.detectEditors();
            int labelY = contentTop + 30 - scrollY;
            if (!detected.isEmpty()) {
                context.drawText(textRenderer, "\u00a7eDetected Editors:", centerX - 118, labelY, 0xFFFFFFFF, false);
            } else {
                context.drawText(textRenderer, "\u00a7cNo editors auto-detected", centerX - 118, labelY + 4, 0xFFFF5555, false);
            }

            // "Custom Editor Path" label (descriptive, scrolled)
            int customLabelY = detectedEditorsEndY + 8 - scrollY;
            // Short, clear label placed directly above the custom path text field
            context.drawText(textRenderer, "Custom Path (overrides selection above)", centerX - 118, customLabelY, 0xFFAAAAAA, false);

            super.render(context, mouseX, mouseY, delta);
        } finally {
            // restore widget positions
            var ch3 = this.children();
            for (int i = 0; i < ch3.size(); i++) {
                var d = ch3.get(i);
                if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                    int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                    if (by >= 0) w.setY(by);
                }
            }
            context.disableScissor();
        }

        // Status at bottom (fixed)
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int top = contentTop;
        int availH = Math.max(0, this.height - top - 40);
        if (contentHeight <= availH) return false;
        if (my < top || my > top + availH) return false;

        int maxScroll = Math.max(0, contentHeight - availH);
        int step = 12;
        if (va > 0) scrollY = Math.max(0, scrollY - step);
        else if (va < 0) scrollY = Math.min(maxScroll, scrollY + step);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseClicked(mouseX, mouseY, button);
        var ch3 = this.children();
        for (int i = 0; i < ch3.size(); i++) {
            var d = ch3.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by);
            }
        }
        return res;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseReleased(mouseX, mouseY, button);
        var ch3 = this.children();
        for (int i = 0; i < ch3.size(); i++) {
            var d = ch3.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by);
            }
        }
        return res;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
        var ch3 = this.children();
        for (int i = 0; i < ch3.size(); i++) {
            var d = ch3.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by);
            }
        }
        return res;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        super.mouseMoved(mouseX, mouseY);
        var ch3 = this.children();
        for (int i = 0; i < ch3.size(); i++) {
            var d = ch3.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by);
            }
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
