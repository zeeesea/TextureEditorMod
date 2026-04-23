package com.zeeesea.textureeditor.mixin.client;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("image")
    NativeImage getImage();

    @Accessor("mipmapLevelsImages")
    NativeImage[] getMipmapLevelsImages();

    @Accessor("mipmapLevelsImages")
    void setMipmapLevelsImages(NativeImage[] images);
}
