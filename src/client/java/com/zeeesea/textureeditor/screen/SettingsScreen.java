package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.helper.NotificationHelper;
import net.minecraft.client.toast.SystemToast;
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
    // scrolling state for tall settings screens
    private int scrollY = 0;
    private int contentTop = 40; // y where settings widgets start
    private int contentHeight = 0; // computed after init
    private final java.util.List<Integer> baseYs = new java.util.ArrayList<>();

    public SettingsScreen(Screen parent) {
        // Nutzt jetzt den Key aus deiner JSON für den Fenstertitel
        super(Text.translatable("textureeditor.screen.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();

        int centerX = this.width / 2;
        int startY = 40;
        int y = startY;

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

        // Default layers preference: One layer (only Base) vs Two layers (Base + Layer 0)
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("textureeditor.label.default_layers", s.oneLayerByDefault ? Text.translatable("textureeditor.label.one_layer") : Text.translatable("textureeditor.label.two_layers")),
                btn -> {
                    s.oneLayerByDefault = !s.oneLayerByDefault;
                    s.save();
                    // Immediately update this button's visible message so the user gets instant feedback
                    btn.setMessage(Text.translatable("textureeditor.label.default_layers",
                            s.oneLayerByDefault ? Text.translatable("textureeditor.label.one_layer") : Text.translatable("textureeditor.label.two_layers")
                    ));
                    // Debug output for log-based verification
                    System.out.println("[TextureEditor] oneLayerByDefault toggled -> " + s.oneLayerByDefault);
                }
        ).position(centerX - 100, y).size(200, 20).build());
        y += 28;


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

        // Color Preset / Profile switcher
        String currentPreset = ModSettings.getInstance().colorPreset;
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.color_preset", currentPreset), btn -> {
            // Cycle through presets
            com.zeeesea.textureeditor.util.ColorPalette.Preset[] vals = com.zeeesea.textureeditor.util.ColorPalette.Preset.values();
            int idx = 0;
            for (int i = 0; i < vals.length; i++) if (vals[i].name().equalsIgnoreCase(currentPreset)) { idx = i; break; }
            int next = (idx + 1) % vals.length;
            String nextName = vals[next].name();
            ModSettings.getInstance().setColorPreset(nextName);
            // Rebuild screen to update displayed name
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 40;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.label.done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());

        // record total content height for scrolling calculations
        contentTop = startY;
        contentHeight = y;

        // capture base Y positions for all child widgets so we can offset them at render time
        baseYs.clear();
        var ch = this.children();
        for (var d : ch) {
            if (d instanceof net.minecraft.client.gui.widget.Widget w) baseYs.add(w.getY());
            else baseYs.add(-1);
        }

        // clamp scrollY to valid range in case window size changed
        int avail = Math.max(0, this.height - contentTop - 40);
        int maxScroll = Math.max(0, contentHeight - avail);
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, com.zeeesea.textureeditor.util.ColorPalette.INSTANCE.BROWSE_BACKGROUND);

        // Draw centered title with custom shadow color when the text is very light (white)
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        String titleStr = this.title.getString();
        int titleW = textRenderer.getWidth(titleStr);
        int titleX = this.width / 2 - titleW / 2;
        int titleY = 15;
        int textColor = pal.TEXT_NORMAL;
        // If the text color is pure white, use the palette's TITLE_TEXT_SHADOW_ON_WHITE (user asked white-on-white),
        // otherwise use the standard TITLE_TEXT_SHADOW
        int shadowColor = (textColor == 0xFFFFFFFF) ? pal.TITLE_TEXT_SHADOW_ON_WHITE : pal.TITLE_TEXT_SHADOW;
        shadowColor = pal.TITLE_TEXT_SHADOW;
        context.drawText(textRenderer, titleStr, titleX, titleY, textColor, false);

        // Render children (buttons) inside a scissored, translated region so the title stays fixed
        int left = 0;
        int top = contentTop;
        int availH = Math.max(0, this.height - top - 40); // leave some bottom padding for done button visibility

        // Offset widgets' Y positions according to scroll, render within scissor, then restore positions.
        context.enableScissor(left, top, this.width, top + availH);
        try {
            var ch2 = this.children();
            for (int i = 0; i < ch2.size(); i++) {
                var d = ch2.get(i);
                if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                    int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                    if (by >= 0) w.setY(by - scrollY);
                }
            }
            super.render(context, mouseX, mouseY, delta);
        } finally {
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
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {

    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        // Only scroll when content is taller than available area and mouse is over the content region
        int top = contentTop;
        int availH = Math.max(0, this.height - top - 40);
        if (contentHeight <= availH) return false;
        if (my < top || my > top + availH) return false;

        int maxScroll = Math.max(0, contentHeight - availH);
        int step = 12; // pixels per scroll
        if (va > 0) scrollY = Math.max(0, scrollY - step);
        else if (va < 0) scrollY = Math.min(maxScroll, scrollY + step);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        // Apply widget Y offset used for rendering so hitboxes match visual positions
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseClicked(click, doubled);
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
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseReleased(click);
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
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseDragged(click, offsetX, offsetY);
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
