package com.zeeesea.textureeditor;

import com.zeeesea.textureeditor.screen.EditorScreen;
import com.zeeesea.textureeditor.screen.ItemEditorScreen;
import com.zeeesea.textureeditor.screen.MobEditorScreen;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;


public class TextureEditorClient implements ClientModInitializer {

    private static boolean editorModeEnabled = false;

    private static KeyBinding toggleEditorKey;
    private static KeyBinding openEditorKey;
    private static KeyBinding undoKey;
    private static KeyBinding redoKey;

    public static boolean isEditorModeEnabled() {
        return editorModeEnabled;
    }

    public static KeyBinding getUndoKey() { return undoKey; }
    public static KeyBinding getRedoKey() { return redoKey; }

    @Override
    public void onInitializeClient() {
        toggleEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.textureeditor"
        ));

        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.textureeditor"
        ));

        undoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.undo",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.textureeditor"
        ));

        redoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.redo",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "category.textureeditor"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleEditorKey.wasPressed()) {
                editorModeEnabled = !editorModeEnabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("\u00a76[Texture Editor] \u00a7r" +
                                    (editorModeEnabled ? "\u00a7aEnabled" : "\u00a7cDisabled")),
                            true
                    );
                }
            }

            if (editorModeEnabled && client.currentScreen == null) {
                while (openEditorKey.wasPressed()) {
                    if (client.player == null) continue;

                    // Priority 1: If holding an item in main hand, open item editor
                    ItemStack heldItem = client.player.getMainHandStack();
                    if (!heldItem.isEmpty()) {
                        client.execute(() -> client.setScreen(new ItemEditorScreen(heldItem)));
                        continue;
                    }

                    // Priority 2: Aimed at block or entity
                    if (client.crosshairTarget == null) continue;

                    if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
                        client.execute(() -> client.setScreen(new EditorScreen(hitResult)));
                    } else if (client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                        EntityHitResult entityHit = (EntityHitResult) client.crosshairTarget;
                        Entity entity = entityHit.getEntity();
                        if (entity instanceof LivingEntity livingEntity) {
                            client.execute(() -> client.setScreen(new MobEditorScreen(livingEntity)));
                        }
                    }
                }
            }
        });
    }
}