package dev.clearview.selftest.fabric;

import dev.clearview.selftest.ClearViewSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ClearViewSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClearViewSelfTest test = new ClearViewSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
