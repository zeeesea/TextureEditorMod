package com.zeeesea.textureeditor.mixin.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlasSprite.class)
public interface SpriteAccessor {
    @Accessor("padding")
    int getPadding();
}
