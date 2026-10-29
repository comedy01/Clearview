package dev.clearview.showcase;

import net.fabricmc.api.ClientModInitializer;

public final class ClearViewShowcaseFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Showcase.start();
    }
}
