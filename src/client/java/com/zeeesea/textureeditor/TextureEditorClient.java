package com.zeeesea.textureeditor;

import com.zeeesea.textureeditor.screen.BrowseScreen;
import com.zeeesea.textureeditor.screen.EditorScreen;
import com.zeeesea.textureeditor.screen.ItemEditorScreen;
import com.zeeesea.textureeditor.screen.MobEditorScreen;
import com.zeeesea.textureeditor.util.BlockFilter;
import com.zeeesea.textureeditor.util.EntityMapper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
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

                    // Priority 1: If holding a spawn egg, open mob editor
                    ItemStack heldItem = client.player.getMainHandStack();
                    if (!heldItem.isEmpty() && heldItem.getItem() instanceof SpawnEggItem) {
                        Entity entity = EntityMapper.getEntityFromItem(heldItem, client.world);
                        if (entity != null) {
                            client.execute(() -> client.setScreen(new MobEditorScreen(entity)));
                        }
                        continue;
                    }

                    // Priority 2: If holding a non-spawn-egg item, open item editor
                    if (!heldItem.isEmpty()) {
                        client.execute(() -> client.setScreen(new ItemEditorScreen(heldItem)));
                        continue;
                    }

                    // Priority 3: Aimed at entity -> mob editor
                    if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                        EntityHitResult entityHit = (EntityHitResult) client.crosshairTarget;
                        Entity entity = entityHit.getEntity();
                        client.execute(() -> client.setScreen(new MobEditorScreen(entity)));
                        continue;
                    }

                    // Priority 4: Aimed at block (only editable full blocks)
                    if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
                        BlockState state = client.world != null ? client.world.getBlockState(hitResult.getBlockPos()) : null;
                        if (state != null && BlockFilter.isEditableBlock(state)) {
                            client.execute(() -> client.setScreen(new EditorScreen(hitResult)));
                            continue;
                        }
                    }

                    // Priority 5: Nothing valid targeted -> open Browse screen
                    client.execute(() -> client.setScreen(new BrowseScreen()));
                }
            }
        });
    }
}