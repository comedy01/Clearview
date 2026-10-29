package dev.clearview.fabric;

import dev.clearview.client.ClearViewClient;
import dev.clearview.client.KeyRegistrar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ClearViewFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClearViewClient.init(FabricLoader.getInstance().getConfigDir());
        ClearViewClient.setKeys(
                KeyRegistrar.register(ClearViewClient.createFullbrightKey()),
                KeyRegistrar.register(ClearViewClient.createSettingsKey()));
    }
}
