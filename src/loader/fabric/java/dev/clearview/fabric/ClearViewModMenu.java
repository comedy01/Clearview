package dev.clearview.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.clearview.client.gui.ClearViewSettingsScreen;
import net.minecraft.client.Minecraft;

public final class ClearViewModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ClearViewSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
