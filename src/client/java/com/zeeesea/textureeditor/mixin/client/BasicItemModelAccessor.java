package com.zeeesea.textureeditor.mixin.client;

import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.model.BakedQuad;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Supplier;

@Mixin(BasicItemModel.class)
public interface BasicItemModelAccessor {
    @Accessor("quads")
    List<BakedQuad> getQuads();

    @Mutable
    @Accessor("quads")
    void setQuads(List<BakedQuad> quads);

    @Accessor("vector")
    Supplier<Vector3fc[]> getVector();

    @Mutable
    @Accessor("vector")
    void setVector(Supplier<Vector3fc[]> vector);
}
