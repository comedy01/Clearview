package dev.clearview.selftest.forge;

import dev.clearview.selftest.ClearViewSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("clearview_selftest")
public final class ClearViewSelfTestForge {
    public ClearViewSelfTestForge() {
        ClearViewSelfTest test = new ClearViewSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
