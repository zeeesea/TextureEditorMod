package com.zeeesea.textureeditor.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/**
 * @deprecated Use {@link ItemEditorScreen} with parent parameter instead.
 */
@Deprecated
public class ItemEditorScreenWithBack extends ItemEditorScreen {
    public ItemEditorScreenWithBack(ItemStack itemStack, Screen parent) {
        super(itemStack, parent);
    }
}
