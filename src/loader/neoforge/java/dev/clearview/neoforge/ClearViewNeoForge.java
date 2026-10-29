package dev.clearview.neoforge;

import dev.clearview.client.ClearViewClient;
import dev.clearview.client.gui.ClearViewSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ClearViewClient.MOD_ID, dist = Dist.CLIENT)
public final class ClearViewNeoForge {
    public ClearViewNeoForge(IEventBus modBus, ModContainer container) {
        ClearViewClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping fullbright = ClearViewClient.createFullbrightKey();
        KeyMapping settings = ClearViewClient.createSettingsKey();
        ClearViewClient.setKeys(fullbright, settings);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(fullbright);
            event.register(settings);
        });

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new ClearViewSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
