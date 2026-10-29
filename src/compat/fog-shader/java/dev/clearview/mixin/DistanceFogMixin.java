package dev.clearview.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.clearview.client.ClearViewRuntime;
import dev.clearview.client.Medium;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
abstract class DistanceFogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void clearview$remove(CallbackInfo ci, @Local(argsOnly = true) Camera camera,
                                         @Local(argsOnly = true) FogRenderer.FogMode mode) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || camera.getFluidInCamera() != FogType.NONE
                || !ClearViewRuntime.clears(Medium.AIR, camera.getEntity())) {
            return;
        }
        RenderSystem.setShaderFogStart(Medium.AIR.start());
        RenderSystem.setShaderFogEnd(Medium.AIR.end());
    }
}
