package com.zeeesea.textureeditor;

import com.zeeesea.textureeditor.screen.BrowseScreen;
import com.zeeesea.textureeditor.screen.EditorScreen;
import com.zeeesea.textureeditor.screen.ItemEditorScreen;
import com.zeeesea.textureeditor.screen.MobEditorScreen;
import com.zeeesea.textureeditor.screen.AbstractEditorScreen;
import com.zeeesea.textureeditor.editor.ExternalEditorManager;
import com.zeeesea.textureeditor.settings.ModSettings;
import com.zeeesea.textureeditor.util.BlockFilter;
import com.zeeesea.textureeditor.util.EntityMapper;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.MobTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
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


    private static KeyBinding toggleEditorKey;
    private static KeyBinding openEditorKey;
    private static KeyBinding previewOriginalKey;

    public static boolean isEditorModeEnabled() {
        return ModSettings.getInstance().modEnabled;
    }

    public static KeyBinding getOpenEditorKey() { return openEditorKey; }
    public static KeyBinding getPreviewOriginalKey() { return previewOriginalKey; }

    @Override
    public void onInitializeClient() {
        ModSettings s = ModSettings.getInstance();

        String category = "category.textureeditor";

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
                GLFW.GLFW_KEY_X,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TextureManager.getInstance().tickItemAnimations();
            while (toggleEditorKey.wasPressed()) {
                s.modEnabled = !s.modEnabled;
                s.save();
                if (client.player != null) {
                    Text prefix = Text.translatable("textureeditor.status.prefix")
                            .formatted(net.minecraft.util.Formatting.GOLD);

                    Text status = ModSettings.getInstance().modEnabled
                            ? Text.translatable("textureeditor.status.enabled").formatted(net.minecraft.util.Formatting.GREEN)
                            : Text.translatable("textureeditor.status.disabled").formatted(net.minecraft.util.Formatting.RED);

                    Text fullMessage = Text.empty()
                            .append(prefix)
                            .append(Text.literal(" "))
                            .append(status);

                    client.player.sendMessage(fullMessage, true); // true = Action Bar (über dem Inventar)
                }
                if (ModSettings.getInstance().modEnabled) {
                    while (openEditorKey.wasPressed()) { /* discard stale presses */ }
                }
            }

            if (s.modEnabled) {
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
                        client.getWindow().getHandle(),
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

                        ExternalEditorManager extMgr = ExternalEditorManager.getInstance();
                        boolean useExternal = extMgr.isExternalEditorEnabled();

                        // Priority 1: Holding a spawn egg -> mob editor
                        ItemStack heldItem = client.player.getMainHandStack();
                        if (!heldItem.isEmpty() && heldItem.getItem() instanceof SpawnEggItem) {
                            Entity entity = EntityMapper.getEntityFromItem(heldItem, client.world);
                            if (entity != null) {
                                if (useExternal) {
                                    client.execute(() -> openExternalForMob(entity));
                                } else {
                                    client.execute(() -> client.setScreen(new MobEditorScreen(entity, null)));
                                }
                            }
                            continue;
                        }

                        // Priority 2: Holding a block item -> block editor
                        if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
                            if (BlockFilter.isEditableBlock(blockItem.getBlock().getDefaultState())) {
                                if (useExternal) {
                                    client.execute(() -> openExternalForBlock(blockItem.getBlock()));
                                } else {
                                    client.execute(() -> client.setScreen(new EditorScreen(blockItem.getBlock(), null)));
                                }
                                continue;
                            }
                        }

                        // Priority 3: Holding any other item -> item editor
                        if (!heldItem.isEmpty()) {
                            if (useExternal) {
                                client.execute(() -> openExternalForItem(heldItem));
                            } else {
                                client.execute(() -> client.setScreen(new ItemEditorScreen(heldItem, null)));
                            }
                            continue;
                        }

                        // Priority 4: Aimed at entity -> mob editor
                        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                            EntityHitResult entityHit = (EntityHitResult) client.crosshairTarget;
                            Entity entity = entityHit.getEntity();
                            if (useExternal) {
                                client.execute(() -> openExternalForMob(entity));
                            } else {
                                client.execute(() -> client.setScreen(new MobEditorScreen(entity, null)));
                            }
                            continue;
                        }

                        // Priority 5: Aimed at block
                        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                            BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
                            BlockState state = client.world != null ? client.world.getBlockState(hitResult.getBlockPos()) : null;
                            if (state != null && BlockFilter.isEditableBlock(state)) {
                                if (useExternal) {
                                    client.execute(() -> openExternalForBlockFace(state.getBlock(), hitResult.getSide()));
                                } else {
                                    client.execute(() -> client.setScreen(new EditorScreen(hitResult)));
                                }
                                continue;
                            }
                        }

                        // Priority 6: Nothing -> browse (always in-game)
                        client.execute(() -> client.setScreen(new BrowseScreen()));
                    }
                }
            }
        });


        // Receive texture sync from server and apply it
        ClientPlayNetworking.registerGlobalReceiver(TextureSyncPayload.ID, (payload, context) -> {
            // Only apply if multiplayer sync is enabled in settings
            if (ModSettings.getInstance().multiplayerSync) {
                int w = payload.width();
                int h = payload.height();
                context.client().execute(() ->
                        TextureManager.getInstance().applyLive(payload.spriteId(), payload.pixels(), w, h)
                );
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(EntityTextureSyncPayload.ID, (payload, context) -> {
            if (ModSettings.getInstance().multiplayerSync) {
                int w = payload.width();
                int h = payload.height();
                context.client().execute(() -> {
                    if (payload.spriteId() != null) {
                        // GUI/Sky — use sprite-based apply
                        TextureManager.getInstance().applyLive(
                                payload.spriteId(), payload.pixels(), payload.originalPixels(), w, h);
                    } else {
                        // Mob/Entity — use entity apply
                        TextureManager.getInstance().applyLiveEntity(
                                payload.textureId(), payload.pixels(), payload.originalPixels(), w, h);
                    }
                });
            }
        });
    }


    // --- External editor helpers ---

    private static void openExternalForBlock(net.minecraft.block.Block block) {
        openExternalForBlockFace(block, net.minecraft.util.math.Direction.UP);
    }

    private static void openExternalForBlockFace(net.minecraft.block.Block block, net.minecraft.util.math.Direction face) {
        TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(block.getDefaultState(), face);
        if (tex == null) {
            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                tex = TextureExtractor.extract(block.getDefaultState(), dir);
                if (tex != null) break;
            }
        }
        if (tex != null) {
            Identifier spriteId = Identifier.of(tex.textureId().getNamespace(),
                    tex.textureId().getPath().replace("textures/", "").replace(".png", ""));
            int[][] origCopy = new int[tex.width()][tex.height()];
            for (int x = 0; x < tex.width(); x++)
                System.arraycopy(tex.pixels()[x], 0, origCopy[x], 0, tex.height());
            ExternalEditorManager.getInstance().startAtlasSession(
                    tex.textureId(), spriteId, tex.pixels(), origCopy, tex.width(), tex.height());
        }
    }

    private static void openExternalForItem(ItemStack stack) {
        ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(stack);
        if (tex != null) {
            int[][] origCopy = new int[tex.width()][tex.height()];
            for (int x = 0; x < tex.width(); x++)
                System.arraycopy(tex.pixels()[x], 0, origCopy[x], 0, tex.height());

            if (tex.spriteId() != null && !tex.textureId().getPath().startsWith("textures/entity/")) {
                ExternalEditorManager.getInstance().startAtlasSession(
                        tex.textureId(), tex.spriteId(), tex.pixels(), origCopy, tex.width(), tex.height());
            } else {
                ExternalEditorManager.getInstance().startEntitySession(
                        tex.textureId(), tex.pixels(), origCopy, tex.width(), tex.height());
            }
        }
    }

    private static void openExternalForMob(Entity entity) {
        MobTextureExtractor.MobTexture tex = MobTextureExtractor.extract(entity);
        if (tex != null) {
            int[][] origCopy = new int[tex.width()][tex.height()];
            for (int x = 0; x < tex.width(); x++)
                System.arraycopy(tex.pixels()[x], 0, origCopy[x], 0, tex.height());
            ExternalEditorManager.getInstance().startEntitySession(
                    tex.textureId(), tex.pixels(), origCopy, tex.width(), tex.height());
        }
    }
}