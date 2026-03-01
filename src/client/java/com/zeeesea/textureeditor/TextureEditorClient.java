package com.zeeesea.textureeditor;

import com.zeeesea.textureeditor.screen.BrowseScreen;
import com.zeeesea.textureeditor.screen.EditorScreen;
import com.zeeesea.textureeditor.screen.ItemEditorScreen;
import com.zeeesea.textureeditor.screen.MobEditorScreen;
import com.zeeesea.textureeditor.screen.AbstractEditorScreen;
import com.zeeesea.textureeditor.util.BlockFilter;
import com.zeeesea.textureeditor.util.EntityMapper;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;


public class TextureEditorClient implements ClientModInitializer {

    private static boolean editorModeEnabled = false;

    private static KeyBinding toggleEditorKey;
    private static KeyBinding openEditorKey;
    private static KeyBinding previewOriginalKey;

    public static boolean isEditorModeEnabled() {
        return editorModeEnabled;
    }

    public static KeyBinding getOpenEditorKey() { return openEditorKey; }
    public static KeyBinding getPreviewOriginalKey() { return previewOriginalKey; }

    @Override
    public void onInitializeClient() {
        Category category = new Category(Identifier.of("textureeditor", "category"));

        toggleEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                category
        ));

        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category
        ));

        previewOriginalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textureeditor.preview",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                category
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
                if (editorModeEnabled) {
                    while (openEditorKey.wasPressed()) { /* discard stale presses */ }
                }
            }

            if (editorModeEnabled) {
                // If an editor screen is open, R closes it
                if (client.currentScreen instanceof AbstractEditorScreen) {
                    while (openEditorKey.wasPressed()) {
                        client.currentScreen.close();
                    }
                    // Handle preview key inside editor (AbstractEditorScreen handles it directly)
                    while (previewOriginalKey.wasPressed()) { /* consumed */ }
                    return;
                }

                // Preview original texture (hold key) — world-level preview
                boolean previewKeyHeld = InputUtil.isKeyPressed(
                        client.getWindow(),
                        KeyBindingHelper.getBoundKeyOf(previewOriginalKey).getCode()
                );
                TextureManager tm = TextureManager.getInstance();
                if (previewKeyHeld && !tm.isPreviewingOriginals() && tm.hasModifiedTextures()) {
                    client.execute(() -> tm.setPreviewingOriginals(true));
                } else if (!previewKeyHeld && tm.isPreviewingOriginals()) {
                    client.execute(() -> tm.setPreviewingOriginals(false));
                }

                if (client.currentScreen == null) {
                    while (openEditorKey.wasPressed()) {
                        if (client.player == null) continue;

                        // Priority 1: Holding a spawn egg -> mob editor
                        ItemStack heldItem = client.player.getMainHandStack();
                        if (!heldItem.isEmpty() && heldItem.getItem() instanceof SpawnEggItem) {
                            Entity entity = EntityMapper.getEntityFromItem(heldItem, client.world);
                            if (entity != null) {
                                client.execute(() -> client.setScreen(new MobEditorScreen(entity, null)));
                            }
                            continue;
                        }

                        // Priority 2: Holding a block item -> block editor
                        if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
                            if (BlockFilter.isEditableBlock(blockItem.getBlock().getDefaultState())) {
                                client.execute(() -> client.setScreen(new EditorScreen(blockItem.getBlock(), null)));
                                continue;
                            }
                        }

                        // Priority 3: Holding any other item -> item editor
                        if (!heldItem.isEmpty()) {
                            client.execute(() -> client.setScreen(new ItemEditorScreen(heldItem, null)));
                            continue;
                        }

                        // Priority 4: Aimed at entity -> mob editor
                        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                            EntityHitResult entityHit = (EntityHitResult) client.crosshairTarget;
                            Entity entity = entityHit.getEntity();
                            client.execute(() -> client.setScreen(new MobEditorScreen(entity, null)));
                            continue;
                        }

                        // Priority 5: Aimed at block
                        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                            BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
                            BlockState state = client.world != null ? client.world.getBlockState(hitResult.getBlockPos()) : null;
                            if (state != null && BlockFilter.isEditableBlock(state)) {
                                client.execute(() -> client.setScreen(new EditorScreen(hitResult)));
                                continue;
                            }
                        }

                        // Priority 6: Nothing -> browse
                        client.execute(() -> client.setScreen(new BrowseScreen()));
                    }
                }
            }
        });
    }
}