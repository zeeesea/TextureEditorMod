package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.editor.PixelCanvas;
import com.zeeesea.textureeditor.texture.ResourcePackExporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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

    // Cached icon texture for performance (avoids 4096 fill() calls per frame)
    private NativeImageBackedTexture iconTexture = null;
    private static final Identifier ICON_TEX_ID = Identifier.of("textureeditor", "export_icon");
    private long lastIconVersion = -1;

    // Continuous drawing: track last drawn pixel for line interpolation
    private int lastDrawX = -1, lastDrawY = -1;

    // Simple palette for icon
    private static final int[] PALETTE = {
            0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
            0xFFFFFF00, 0xFFFF8000, 0xFF800080, 0xFF008080, 0xFF808080,
    };

    public ExportScreen(Screen parent) {
        super(Text.translatable("textureeditor.screen.export.title"));
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
        packNameInput = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, 40, fieldWidth, 18, Text.translatable("textureeditor.field.pack_name"));
        packNameInput.setMaxLength(64);
        packNameInput.setText(Text.translatable("textureeditor.default.pack_name").getString());
        addDrawableChild(packNameInput);

        // Description
        descriptionInput = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, 75, fieldWidth, 18, Text.translatable("textureeditor.field.description"));
        descriptionInput.setMaxLength(128);
        descriptionInput.setText(Text.translatable("textureeditor.default.pack_description").getString());
        addDrawableChild(descriptionInput);

        // Author
        authorInput = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, 110, fieldWidth, 18, Text.translatable("textureeditor.field.author"));
        authorInput.setMaxLength(64);
        authorInput.setText("");
        addDrawableChild(authorInput);

        // Icon canvas position — scale-aware zoom
        int guiScale = (int) net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
        iconZoom = guiScale >= 4 ? 2 : (guiScale >= 3 ? 3 : 4);
        iconScreenX = centerX - (ICON_SIZE * iconZoom) / 2;
        iconScreenY = 145;

        // Grid toggle
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.grid"), btn -> showGrid = !showGrid)
                .position(iconScreenX + ICON_SIZE * iconZoom + 10, iconScreenY).size(50, 20).build());

        int exportBackPosX = centerX - 60;
        int exportBackPosY = this.height - 50;

        if (getWindowHeight() < 1080 || getGuiScale() >= 3) {
            exportBackPosX = centerX + fieldWidth / 2 + 30;
            exportBackPosY = 40;
        }
            // Export button
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.export"), btn -> doExport())
                    .position(exportBackPosX, exportBackPosY).size(120, 20).build());

            // Back button
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.back"), btn -> client.setScreen(parent))
                    .position(exportBackPosX, exportBackPosY + 24).size(120, 20).build());

    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("textureeditor.screen.export.title"), this.width / 2, 10, 0xFFFFFFFF);
        context.drawText(textRenderer, Text.translatable("textureeditor.label.pack_name"), this.width / 2 - 100, 30, 0xFFCCCCCC, false);
        context.drawText(textRenderer, Text.translatable("textureeditor.label.description"), this.width / 2 - 100, 65, 0xFFCCCCCC, false);
        context.drawText(textRenderer, Text.translatable("textureeditor.label.author"), this.width / 2 - 100, 100, 0xFFCCCCCC, false);

        // Draw "Pack Icon (optional):" label
        context.drawText(textRenderer, Text.translatable("textureeditor.label.pack_icon"), iconScreenX, 135, 0xFFCCCCCC, false);

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
        // Use cached texture rendering (1 draw call instead of 4096 fill calls)
        long version = iconCanvas.getVersion();
        if (version != lastIconVersion || iconTexture == null) {
            lastIconVersion = version;
            NativeImage img = new NativeImage(ICON_SIZE, ICON_SIZE, false);
            for (int x = 0; x < ICON_SIZE; x++) {
                for (int y = 0; y < ICON_SIZE; y++) {
                    int c = iconCanvas.getPixel(x, y);
                    int alpha = (c >> 24) & 0xFF;
                    if (alpha == 0) {
                        c = ((x / 4 + y / 4) % 2 == 0) ? 0xFF808080 : 0xFFA0A0A0;
                    }
                    img.setColorArgb(x, y, c);
                }
            }
            if (iconTexture != null) {
                iconTexture.setImage(img);
                iconTexture.upload();
            } else {
                iconTexture = new NativeImageBackedTexture(() -> "export_icon", img);
                MinecraftClient.getInstance().getTextureManager().registerTexture(ICON_TEX_ID, iconTexture);
            }
        }

        int drawW = ICON_SIZE * iconZoom;
        int drawH = ICON_SIZE * iconZoom;
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, ICON_TEX_ID,
            iconScreenX, iconScreenY, 0, 0, drawW, drawH, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        if (showGrid) {
            for (int x = 0; x <= ICON_SIZE; x += 8) {
                int sx = iconScreenX + x * iconZoom;
                context.fill(sx, iconScreenY, sx + 1, iconScreenY + drawH, 0x40FFFFFF);
            }
            for (int y = 0; y <= ICON_SIZE; y += 8) {
                int sy = iconScreenY + y * iconZoom;
                context.fill(iconScreenX, sy, iconScreenX + drawW, sy + 1, 0x40FFFFFF);
            }
        }

        int endX = iconScreenX + drawW;
        int endY = iconScreenY + drawH;
        context.fill(iconScreenX - 1, iconScreenY - 1, endX + 1, iconScreenY, 0xFFFFFFFF);
        context.fill(iconScreenX - 1, endY, endX + 1, endY + 1, 0xFFFFFFFF);
        context.fill(iconScreenX - 1, iconScreenY, iconScreenX, endY, 0xFFFFFFFF);
        context.fill(endX, iconScreenY, endX + 1, endY, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        double mouseX = click.x(); double mouseY = click.y();
        // Reset line interpolation on new click
        lastDrawX = -1;
        lastDrawY = -1;

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

        if (handleIconCanvasClick(mouseX, mouseY)) return true;
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        double mouseX = click.x(); double mouseY = click.y();
        if (handleIconCanvasClick(mouseX, mouseY)) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private boolean handleIconCanvasClick(double mouseX, double mouseY) {
        int px = (int) ((mouseX - iconScreenX) / iconZoom);
        int py = (int) ((mouseY - iconScreenY) / iconZoom);
        if (px >= 0 && px < ICON_SIZE && py >= 0 && py < ICON_SIZE) {
            if (lastDrawX >= 0 && lastDrawY >= 0 && (lastDrawX != px || lastDrawY != py)) {
                // Bresenham line interpolation between last point and current point
                int x0 = lastDrawX, y0 = lastDrawY, x1 = px, y1 = py;
                int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
                int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
                int err = dx - dy;
                while (true) {
                    if (x0 >= 0 && x0 < ICON_SIZE && y0 >= 0 && y0 < ICON_SIZE) {
                        iconCanvas.setPixel(x0, y0, currentColor);
                    }
                    if (x0 == x1 && y0 == y1) break;
                    int e2 = 2 * err;
                    if (e2 > -dy) { err -= dy; x0 += sx; }
                    if (e2 < dx) { err += dx; y0 += sy; }
                }
            } else {
                iconCanvas.setPixel(px, py, currentColor);
            }
            lastDrawX = px;
            lastDrawY = py;
            return true;
        }
        return false;
    }

    private void doExport() {
        String name = packNameInput.getText().trim();
        if (name.isEmpty()) {
            statusMessage = Text.translatable("textureeditor.status.enter_pack_name").getString();
            statusColor = 0xFF5555;
            return;
        }

        String description = descriptionInput.getText().trim();
        if (description.isEmpty()) description = name;

        String author = authorInput.getText().trim();

        File result = ResourcePackExporter.export(name, description, author,
                iconCanvas.getPixels(), ICON_SIZE, ICON_SIZE);
        if (result != null) {
            statusMessage = Text.translatable("textureeditor.status.exported_to", result.getName()).getString();
            statusColor = 0x55FF55;
        } else {
            statusMessage = Text.translatable("textureeditor.status.export_failed").getString();
            statusColor = 0xFF5555;
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private int getWindowHeight() {
        return MinecraftClient.getInstance().getWindow().getHeight();
    }

    protected int getGuiScale() {
        return (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
