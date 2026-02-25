package com.zeeesea.textureeditor.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/**
 * @deprecated Use {@link MobEditorScreen} with parent parameter instead.
 */
@Deprecated
public class MobEditorScreenWithBack extends MobEditorScreen {
    public MobEditorScreenWithBack(Entity entity, ItemStack itemStack, Screen parent) {
        super(entity, parent);
    }
}
