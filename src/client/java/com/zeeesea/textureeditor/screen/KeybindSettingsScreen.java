package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.settings.ModSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind configuration screen for editor-internal keybinds.
 */
public class KeybindSettingsScreen extends Screen {

    private final Screen parent;
    private String waitingForKey = null; // action name waiting for rebind

    private static final String[][] ACTIONS = {
            {"pencil", "Pencil Tool"},
            {"eraser", "Eraser Tool"},
            {"fill", "Fill Tool"},
            {"eyedropper", "Eyedropper Tool"},
            {"line", "Line Tool"},
            {"undo", "Undo"},
            {"redo", "Redo"},
            {"grid", "Toggle Grid"},
    };

    public KeybindSettingsScreen(Screen parent) {
        super(Text.literal("Editor Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModSettings s = ModSettings.getInstance();
        int centerX = this.width / 2;
        int y = 50;

        for (String[] action : ACTIONS) {
            String actionId = action[0];
            String displayName = action[1];
            String keyName = s.getKeyName(actionId);
            boolean isWaiting = actionId.equals(waitingForKey);

            String btnText = isWaiting ? "\u00a7e> Press a key <" : displayName + ": [\u00a7b" + keyName + "\u00a7r]";

            final String aid = actionId;
            addDrawableChild(ButtonWidget.builder(Text.literal(btnText), btn -> {
                waitingForKey = aid;
                this.clearChildren();
                this.init();
            }).position(centerX - 130, y).size(260, 20).build());
            y += 24;
        }

        y += 20;

        // Reset all keybinds
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cReset to Defaults"), btn -> {
            ModSettings settings = ModSettings.getInstance();
            settings.keybinds.put("pencil", GLFW.GLFW_KEY_B);
            settings.keybinds.put("eraser", GLFW.GLFW_KEY_E);
            settings.keybinds.put("fill", GLFW.GLFW_KEY_F);
            settings.keybinds.put("eyedropper", GLFW.GLFW_KEY_I);
            settings.keybinds.put("line", GLFW.GLFW_KEY_L);
            settings.keybinds.put("undo", GLFW.GLFW_KEY_Z);
            settings.keybinds.put("redo", GLFW.GLFW_KEY_Y);
            settings.keybinds.put("grid", GLFW.GLFW_KEY_G);
            settings.save();
            waitingForKey = null;
            this.clearChildren();
            this.init();
        }).position(centerX - 80, y).size(160, 20).build());

        y += 30;

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .position(centerX - 50, y).size(100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a7l\u00a7eEditor Keybinds", this.width / 2, 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "\u00a77Click a keybind to change it, then press the new key", this.width / 2, 30, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
