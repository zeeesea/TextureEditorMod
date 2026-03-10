package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.ResourcePackExporter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * Export screen where the user can name their texture pack, set description/author,
 * optionally draw a pack icon, and export everything as a resource pack ZIP.
 */
public class ExportScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget packNameInput;
    private TextFieldWidget descriptionInput;
    private TextFieldWidget authorInput;
    private PixelCanvas iconCanvas;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    // Icon canvas settings
    private static final int ICON_SIZE = 64;
    private int iconZoom = 4;
    private int iconScreenX;
    private int iconScreenY;

    private int currentColor = 0xFFFF0000;
    private boolean showGrid = true;

    // Continuous stroke tracking for smooth line drawing
    private int lastIconDragX = -1, lastIconDragY = -1;

    // Simple palette for icon
    private static final int[] PALETTE = {
            0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
            0xFFFFFF00, 0xFFFF8000, 0xFF800080, 0xFF008080, 0xFF808080,
    };

    public ExportScreen(Screen parent) {
        super(Text.literal("Export Texture Pack"));
        this.parent = parent;
        this.iconCanvas = new PixelCanvas(ICON_SIZE, ICON_SIZE);
        for (int x = 0; x < ICON_SIZE; x++) {
            for (int y = 0; y < ICON_SIZE; y++) {
                iconCanvas.setPixel(x, y, 0x000000);
            }
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = 200;

        // Pack name
        packNameInput = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, 40, fieldWidth, 18, Text.literal("Pack Name"));
        packNameInput.setMaxLength(64);
        packNameInput.setText("My Texture Pack");
        addDrawableChild(packNameInput);

        // Description
        descriptionInput = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, 75, fieldWidth, 18, Text.literal("Description"));
        descriptionInput.setMaxLength(128);
        descriptionInput.setText("Created with Texture Editor Mod");
        addDrawableChild(descriptionInput);

        // Author
        authorInput = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, 110, fieldWidth, 18, Text.literal("Author"));
        authorInput.setMaxLength(64);
        authorInput.setText("");
        addDrawableChild(authorInput);

        // Icon canvas position — scale-aware zoom
        int guiScale = (int) net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
        iconZoom = guiScale >= 4 ? 2 : (guiScale >= 3 ? 3 : 4);
        iconScreenX = centerX - (ICON_SIZE * iconZoom) / 2;
        iconScreenY = 145;

        // Grid toggle
        addDrawableChild(ButtonWidget.builder(Text.literal("Grid"), btn -> showGrid = !showGrid)
                .position(iconScreenX + ICON_SIZE * iconZoom + 10, iconScreenY).size(50, 20).build());

        // Export button
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7aExport"), btn -> doExport())
                .position(centerX - 60, this.height - 50).size(120, 20).build());

        // Back button
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> client.setScreen(parent))
                .position(centerX - 60, this.height - 26).size(120, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, "Export Texture Pack", this.width / 2, 10, 0xFFFFFF);
        context.drawText(textRenderer, "Pack Name:", this.width / 2 - 100, 30, 0xCCCCCC, false);
        context.drawText(textRenderer, "Description:", this.width / 2 - 100, 65, 0xCCCCCC, false);
        context.drawText(textRenderer, "Author:", this.width / 2 - 100, 100, 0xCCCCCC, false);

        // Draw "Pack Icon (optional):" label
        context.drawText(textRenderer, "Pack Icon (draw below):", iconScreenX, 135, 0xCCCCCC, false);

        // Draw icon canvas
        drawIconCanvas(context, mouseX, mouseY);

        // Draw mini palette next to icon
        int palX = iconScreenX + ICON_SIZE * iconZoom + 10;
        int palY = iconScreenY + 25;
        for (int i = 0; i < PALETTE.length; i++) {
            int px = palX;
            int py = palY + i * 18;
            context.fill(px, py, px + 16, py + 16, PALETTE[i]);
            if (PALETTE[i] == currentColor) {
                context.fill(px - 1, py - 1, px + 17, py, 0xFFFFFF00);
                context.fill(px - 1, py + 16, px + 17, py + 17, 0xFFFFFF00);
                context.fill(px - 1, py, px, py + 16, 0xFFFFFF00);
                context.fill(px + 16, py, px + 17, py + 16, 0xFFFFFF00);
            }
        }

        // Status message
        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, this.width / 2, this.height - 65, statusColor);
        }
    }

    private void drawIconCanvas(DrawContext context, int mouseX, int mouseY) {
        for (int x = 0; x < ICON_SIZE; x++) {
            for (int y = 0; y < ICON_SIZE; y++) {
                int sx = iconScreenX + x * iconZoom;
                int sy = iconScreenY + y * iconZoom;
                int color = iconCanvas.getPixel(x, y);
                context.fill(sx, sy, sx + iconZoom, sy + iconZoom, color);
            }
        }

        if (showGrid) {
            for (int x = 0; x <= ICON_SIZE; x += 8) {
                int sx = iconScreenX + x * iconZoom;
                context.fill(sx, iconScreenY, sx + 1, iconScreenY + ICON_SIZE * iconZoom, 0x40FFFFFF);
            }
            for (int y = 0; y <= ICON_SIZE; y += 8) {
                int sy = iconScreenY + y * iconZoom;
                context.fill(iconScreenX, sy, iconScreenX + ICON_SIZE * iconZoom, sy + 1, 0x40FFFFFF);
            }
        }

        int endX = iconScreenX + ICON_SIZE * iconZoom;
        int endY = iconScreenY + ICON_SIZE * iconZoom;
        context.fill(iconScreenX - 1, iconScreenY - 1, endX + 1, iconScreenY, 0xFFFFFFFF);
        context.fill(iconScreenX - 1, endY, endX + 1, endY + 1, 0xFFFFFFFF);
        context.fill(iconScreenX - 1, iconScreenY, iconScreenX, endY, 0xFFFFFFFF);
        context.fill(endX, iconScreenY, endX + 1, endY, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check palette click
        int palX = iconScreenX + ICON_SIZE * iconZoom + 10;
        int palY = iconScreenY + 25;
        for (int i = 0; i < PALETTE.length; i++) {
            int py = palY + i * 18;
            if (mouseX >= palX && mouseX < palX + 16 && mouseY >= py && mouseY < py + 16) {
                currentColor = PALETTE[i];
                return true;
            }
        }

        if (handleIconCanvasClick(mouseX, mouseY)) {
            int px = (int) ((mouseX - iconScreenX) / iconZoom);
            int py = (int) ((mouseY - iconScreenY) / iconZoom);
            lastIconDragX = px;
            lastIconDragY = py;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        lastIconDragX = -1;
        lastIconDragY = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int px = (int) ((mouseX - iconScreenX) / iconZoom);
        int py = (int) ((mouseY - iconScreenY) / iconZoom);
        if (px >= 0 && px < ICON_SIZE && py >= 0 && py < ICON_SIZE) {
            if (lastIconDragX >= 0 && lastIconDragY >= 0 && (px != lastIconDragX || py != lastIconDragY)) {
                drawIconLine(lastIconDragX, lastIconDragY, px, py);
            } else {
                iconCanvas.setPixel(px, py, currentColor);
            }
            lastIconDragX = px;
            lastIconDragY = py;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /**
     * Draw a line on the icon canvas using Bresenham's algorithm.
     */
    private void drawIconLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int startX = x0, startY = y0;
        while (true) {
            if (!(x0 == startX && y0 == startY)) {
                if (x0 >= 0 && x0 < ICON_SIZE && y0 >= 0 && y0 < ICON_SIZE) {
                    iconCanvas.setPixel(x0, y0, currentColor);
                }
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private boolean handleIconCanvasClick(double mouseX, double mouseY) {
        int px = (int) ((mouseX - iconScreenX) / iconZoom);
        int py = (int) ((mouseY - iconScreenY) / iconZoom);
        if (px >= 0 && px < ICON_SIZE && py >= 0 && py < ICON_SIZE) {
            iconCanvas.setPixel(px, py, currentColor);
            return true;
        }
        return false;
    }

    private void doExport() {
        String name = packNameInput.getText().trim();
        if (name.isEmpty()) {
            statusMessage = "Please enter a pack name!";
            statusColor = 0xFF5555;
            return;
        }

        String description = descriptionInput.getText().trim();
        if (description.isEmpty()) description = name;

        String author = authorInput.getText().trim();

        File result = ResourcePackExporter.export(name, description, author,
                iconCanvas.getPixels(), ICON_SIZE, ICON_SIZE);
        if (result != null) {
            statusMessage = "Exported to: " + result.getName();
            statusColor = 0x55FF55;
        } else {
            statusMessage = "Export failed! No modified textures.";
            statusColor = 0xFF5555;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
