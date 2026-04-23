package com.zeeesea.textureeditor;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.zeeesea.textureeditor.screen.SettingsScreen;

/**
 * Mod Menu integration — provides a config screen entry for Texture Editor.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SettingsScreen(parent);
    }
}
