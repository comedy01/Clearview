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
abstract class FogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void clearview$clear(CallbackInfo ci, @Local(argsOnly = true) Camera camera,
                                        @Local(argsOnly = true, ordinal = 0) float far) {
        Medium medium = clearview$medium(camera.getFluidInCamera());
        if (medium == null || !ClearViewRuntime.clears(medium, camera.getEntity())) {
            return;
        }
        RenderSystem.setShaderFogStart(medium.start());
        RenderSystem.setShaderFogEnd(Math.min(far, medium.end()));
    }

    private static Medium clearview$medium(FogType type) {
        if (type == FogType.LAVA) {
            return Medium.LAVA;
        }
        if (type == FogType.WATER) {
            return Medium.WATER;
        }
        return type == FogType.POWDER_SNOW ? Medium.POWDER_SNOW : null;
    }
}
