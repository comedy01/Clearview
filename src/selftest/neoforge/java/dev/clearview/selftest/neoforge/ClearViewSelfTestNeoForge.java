package dev.clearview.selftest.neoforge;

import dev.clearview.selftest.ClearViewSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "clearview_selftest", dist = Dist.CLIENT)
public final class ClearViewSelfTestNeoForge {
    public ClearViewSelfTestNeoForge() {
        ClearViewSelfTest test = new ClearViewSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
