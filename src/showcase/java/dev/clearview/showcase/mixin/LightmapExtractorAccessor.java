package dev.clearview.showcase.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapRenderStateExtractor.class)
public interface LightmapExtractorAccessor {
    @Accessor("needsUpdate")
    void clearviewShowcase$setNeedsUpdate(boolean value);
}
