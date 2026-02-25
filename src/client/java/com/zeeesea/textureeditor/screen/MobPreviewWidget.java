package com.zeeesea.textureeditor.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * A collapsible 3D mob preview panel that renders the entity with rotation support.
 * Click and drag to rotate the preview.
 */
public class MobPreviewWidget {
    private boolean visible = false;
    private int x, y, width, height;
    private float rotationYaw = 30f;
    private float rotationPitch = -15f;
    private boolean isDragging = false;
    private double dragStartX, dragStartY;
    private float dragStartYaw, dragStartPitch;
    private final Entity entity;
    private float scale = 1.0f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.5f;

    public MobPreviewWidget(Entity entity) {
        this.entity = entity;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void toggleVisible() { this.visible = !visible; }

    public void setPosition(int x, int y, int width, int height) {
        this.x = x;
        this.y = y + 30; // weiter unten (default y + 30)
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!visible || entity == null) return;

        // Panel background
        context.fill(x - 2, y - 16, x + width + 2, y + height + 2, 0xEE222244);
        // Border
        context.fill(x - 2, y - 16, x + width + 2, y - 15, 0xFFFFFFFF);
        context.fill(x - 2, y + height + 1, x + width + 2, y + height + 2, 0xFFFFFFFF);
        context.fill(x - 2, y - 16, x - 1, y + height + 2, 0xFFFFFFFF);
        context.fill(x + width + 1, y - 16, x + width + 2, y + height + 2, 0xFFFFFFFF);

        // Title
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawText(client.textRenderer, "\u00a7b3D Preview", x + 2, y - 13, 0xFFFFFF, false);

        // Render entity
        if (entity instanceof LivingEntity livingEntity) {
            int centerX = x + width / 2;
            int centerY = y + height - 10;
            float entityHeight = livingEntity.getHeight();
            float entityWidth = livingEntity.getWidth();
            float maxDim = Math.max(entityHeight, entityWidth);
            int entitySize = (int) (Math.min(width, height) * 0.35f * scale / maxDim);
            entitySize = Math.max(10, Math.min(entitySize, (int)(80 * scale)));

            // Use Minecraft's built-in entity rendering
            // Create rotation quaternions from our yaw/pitch
            Quaternionf rotQuat = new Quaternionf()
                    .rotateY((float) Math.toRadians(-rotationYaw))
                    .rotateX((float) Math.toRadians(rotationPitch));

            Quaternionf modelQuat = new Quaternionf()
                    .rotateX((float) Math.toRadians(rotationPitch));

            try {
                InventoryScreen.drawEntity(
                        context,
                        x, y,
                        x + width, y + height,
                        entitySize,
                        0.0625f,
                        mouseX, mouseY,
                        livingEntity
                );
            } catch (Exception e) {
                // If rendering fails, show error text
                context.drawText(client.textRenderer, "Preview N/A", x + 10, y + height / 2, 0xFF5555, false);
            }
        } else {
            MinecraftClient client2 = MinecraftClient.getInstance();
            context.drawText(client2.textRenderer, "Non-living entity", x + 5, y + height / 2, 0xAAAAAA, false);
        }

        // Instructions
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (mouseX >= x - 2 && mouseX <= x + width + 2 && mouseY >= y - 16 && mouseY <= y + height + 2) {
            if (button == 0) {
                isDragging = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragStartYaw = rotationYaw;
                dragStartPitch = rotationPitch;
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible || !isDragging) return false;
        rotationYaw = dragStartYaw + (float) (mouseX - dragStartX) * 0.8f;
        rotationPitch = dragStartPitch + (float) (mouseY - dragStartY) * 0.5f;
        rotationPitch = Math.max(-90f, Math.min(90f, rotationPitch));
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double ha, double va) {
        if (!visible) return false;
        // Nur zoomen, wenn Maus im Preview-Bereich
        if (mouseX >= x - 2 && mouseX <= x + width + 2 && mouseY >= y - 16 && mouseY <= y + height + 2) {
            if (va > 0) scale = Math.min(MAX_SCALE, scale + 0.1f);
            else if (va < 0) scale = Math.max(MIN_SCALE, scale - 0.1f);
            return true;
        }
        return false;
    }
}
