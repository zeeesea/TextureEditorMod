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
        // Nutzt jetzt den Key aus deiner JSON für den Fenstertitel
        super(Text.translatable("textureeditor.screen.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();

        int centerX = this.width / 2;
        int y = 40;

        // Hilfs-Texte für ON/OFF
        Text textOn = Text.translatable("textureeditor.label.on");
        Text textOff = Text.translatable("textureeditor.label.off");

        // Auto Apply live
        // Wir nutzen hier Text.translatable(KEY, ARGUMENT), um das %s in deiner JSON zu füllen
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.auto_apply_live", s.autoApplyLive ? textOn : textOff), btn -> {
            s.autoApplyLive = !s.autoApplyLive;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Grid On By Default
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.grid_default", s.gridOnByDefault ? textOn : textOff), btn -> {
            s.gridOnByDefault = !s.gridOnByDefault;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Show Tool Hints
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.tool_keybind_hints", s.showToolHints ? textOn : textOff), btn -> {
            s.showToolHints = !s.showToolHints;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Max Undo Steps
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.max_undo_steps", String.valueOf(s.maxUndoSteps)), btn -> {
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
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.color_hist_size", String.valueOf(s.colorHistorySize)), btn -> {
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
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.default_tool", s.defaultTool), btn -> {
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
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.brush_variation", variationPercent + "%"), btn -> {
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

        /* DEFAULT: OFF, feature alr implemented, but too unstable to publish
        // Multiplayer Sync
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.multiplayer_sync", s.multiplayerSync ? textOn : textOff), btn -> {
            s.multiplayerSync = !s.multiplayerSync;
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 40;
         */

        // External Editor Settings
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.externalEditor"), btn ->
                        client.setScreen(new ExternalEditorSettingsScreen(this)))
                .position(centerX - 100, y).size(200, 20).build());
        y += 28;

        // Keybind Settings
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.editor_keybinds"), btn ->
                        client.setScreen(new KeybindSettingsScreen(this)))
                .position(centerX - 100, y).size(200, 20).build());
        y += 40;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);

        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {

    }

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
