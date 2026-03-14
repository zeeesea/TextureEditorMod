package com.zeeesea.textureeditor.mixin.client;

import com.zeeesea.textureeditor.texture.TextureManager;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates GuiRenderer's cached item atlas when live-edited item/block sprites change.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Invoker("onItemAtlasChanged")
    protected abstract void textureeditor$invokeOnItemAtlasChanged();

    @Inject(method = "prepareItemElements", at = @At("HEAD"))
    private void textureeditor$invalidateGuiItemAtlas(CallbackInfo ci) {
        if (TextureManager.getInstance().consumeItemGuiAtlasDirty()) {
            textureeditor$invokeOnItemAtlasChanged();
        }
    }
}

