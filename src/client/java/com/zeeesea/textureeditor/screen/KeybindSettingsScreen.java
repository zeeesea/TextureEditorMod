package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.settings.ModSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind configuration screen for editor-internal keybinds.
 */
public class KeybindSettingsScreen extends Screen {

    private final Screen parent;
    private String waitingForKey = null; // action name waiting for rebind
    // scrolling state for tall keybind screens
    private int scrollY = 0;
    private int contentTop = 50;
    private int contentHeight = 0;
    private final java.util.List<Integer> baseYs = new java.util.ArrayList<>();

    private static final String[][] ACTIONS = {
            {"pencil", "Pencil Tool"},
            {"select", "Select Tool"},
            {"eraser", "Eraser Tool"},
            {"fill", "Fill Tool"},
            {"eyedropper", "Eyedropper Tool"},
            {"line", "Line Tool"},
            {"rectangle", "Rectangle Tool"},
            {"undo", "Undo"},
            {"redo", "Redo"},
            {"grid", "Toggle Grid"},
            {"browse", "Browse Tool"},
    };

    public KeybindSettingsScreen(Screen parent) {
        super(Component.literal("Editor Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();
        int centerX = this.width / 2;
        int startY = 50;
        int y = startY;

        for (String[] action : ACTIONS) {
            String actionId = action[0];
            String displayName = action[1];
            String keyName = s.getKeyName(actionId);
            boolean isWaiting = actionId.equals(waitingForKey);

            String btnText = isWaiting ? "\u00a7e> Press a key <" : displayName + ": [\u00a7b" + keyName + "\u00a7r]";

            final String aid = actionId;
            addDrawableChild(Button.builder(Component.literal(btnText), btn -> {
                waitingForKey = aid;
                this.clearChildren();
                this.init();
            }).position(centerX - 100, y).size(200, 20).build());
            y += 24;
        }

        y += 10;
        addDrawableChild(Button.builder(Component.literal("\u00a7cReset to Defaults"), btn -> {
            s.resetKeybinds();
            s.save();
            this.clearChildren();
            this.init();
        }).position(centerX - 100, y).size(200, 20).build());
        y += 30;

        addDrawableChild(Button.builder(Component.literal("Done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());

        // record content height and clamp scroll
        contentTop = startY;
        contentHeight = y;
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
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        context.fill(0, 0, this.width, this.height, pal.BROWSE_BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a7l\u00a7eEditor Keybinds", this.width / 2, 15, pal.TEXT_NORMAL);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a77Click a keybind to change it, then press the new key", this.width / 2, 30, pal.TEXT_SUBTLE);
        // scissored area for the list of keybinds so title stays fixed
        int left = 0;
        int top = contentTop;
        int availH = Math.max(0, this.height - top - 40);

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
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

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
        // Apply the same widget Y offset used during rendering so hitboxes align with visual positions
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseClicked(mouseX, mouseY, button);
        // restore positions
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        var ch2 = this.children();
        for (int i = 0; i < ch2.size(); i++) {
            var d = ch2.get(i);
            if (d instanceof net.minecraft.client.gui.widget.Widget w) {
                int by = baseYs.size() > i ? baseYs.get(i) : w.getY();
                if (by >= 0) w.setY(by - scrollY);
            }
        }
        boolean res = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int keyCode = keyCode;
        if (waitingForKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // Cancel rebind
                waitingForKey = null;
            } else {
                ModSettings.getInstance().setKeybind(waitingForKey, keyCode);
                waitingForKey = null;
            }
            this.clearChildren();
            this.init();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}


