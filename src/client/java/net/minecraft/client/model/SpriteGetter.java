package net.minecraft.client.model;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.render.model.SimpleModel;

public interface SpriteGetter {
    Sprite get(SpriteIdentifier id, SimpleModel model);

    default Sprite getMissing(String textureId) {
        return null;
    }
}

