package com.zeeesea.textureeditor.mixin.client;

import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(BakedModelManager.class)
public interface BakedModelManagerAccessor {
    @Accessor("bakedItemModels")
    Map<Identifier, ItemModel> getBakedItemModels();
}
