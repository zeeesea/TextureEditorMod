package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Environment(EnvType.CLIENT)
public interface BakedModel {
    List<BakedQuad> getQuads(@Nullable net.minecraft.block.BlockState state, @Nullable Direction face, Random random);

    Sprite getParticleSprite();

    boolean useAmbientOcclusion();

    boolean isSideLit();

    net.minecraft.client.render.model.json.ModelTransformation getTransformation();

    boolean hasDepth();
}

