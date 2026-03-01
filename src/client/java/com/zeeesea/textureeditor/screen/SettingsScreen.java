package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.settings.ModSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Mod settings screen. Accessible from BrowseScreen gear icon and Mod Menu.
 */
public class SettingsScreen extends Screen {

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Texture Editor Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();

        int centerX = this.width / 2;
        int y = 40;

        // Auto Apply live
        addDrawableChild(ButtonWidget.builder(Text.literal("Auto Apply Live: " + (s.autoApplyLive ? "ON" : "OFF")), btn -> {
            s.autoApplyLive = !s.autoApplyLive;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Grid On By Default
        addDrawableChild(ButtonWidget.builder(Text.literal("Grid Default: " + (s.gridOnByDefault ? "ON" : "OFF")), btn -> {
            s.gridOnByDefault = !s.gridOnByDefault;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Show Tool Hints
        addDrawableChild(ButtonWidget.builder(Text.literal("Tool Keybind Hints: " + (s.showToolHints ? "ON" : "OFF")), btn -> {
            s.showToolHints = !s.showToolHints;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Max Undo Steps
        addDrawableChild(ButtonWidget.builder(Text.literal("Max Undo Steps: " + s.maxUndoSteps), btn -> {
            s.maxUndoSteps = switch (s.maxUndoSteps) {
                case 10 -> 25;
                case 25 -> 50;
                case 50 -> 100;
                case 100 -> 200;
                default -> 10;
            };
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Color History Size
        addDrawableChild(ButtonWidget.builder(Text.literal("Color History Size: " + s.colorHistorySize), btn -> {
            s.colorHistorySize = switch (s.colorHistorySize) {
                case 5 -> 10;
                case 10 -> 15;
                case 15 -> 20;
                default -> 5;
            };
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Default Tool
        addDrawableChild(ButtonWidget.builder(Text.literal("Default Tool: " + s.defaultTool), btn -> {
            s.defaultTool = switch (s.defaultTool) {
                case "Pencil" -> "Brush";
                case "Brush" -> "Eraser";
                case "Eraser" -> "Fill";
                case "Fill" -> "Eyedropper";
                case "Eyedropper" -> "Line";
                default -> "Pencil";
            };
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Brush Variation
        int variationPercent = Math.round(s.brushVariation * 100);
        addDrawableChild(ButtonWidget.builder(Text.literal("Brush Variation: " + variationPercent + "%"), btn -> {
            s.brushVariation = switch (variationPercent) {
                case 5 -> 0.10f;
                case 10 -> 0.15f;
                case 15 -> 0.20f;
                case 20 -> 0.30f;
                case 30 -> 0.50f;
                default -> 0.05f;
            };
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 40;

        // Keybind Settings
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7eEditor Keybinds..."), btn ->
                client.setScreen(new KeybindSettingsScreen(this)))
                .position(centerX - 100, y).size(200, 20).build());
        y += 40;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a7l\u00a76Texture Editor Settings", this.width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean shouldPause() { return false; }
}
