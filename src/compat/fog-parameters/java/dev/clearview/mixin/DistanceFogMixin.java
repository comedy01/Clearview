package dev.clearview.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.clearview.client.ClearViewRuntime;
import dev.clearview.client.Medium;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
abstract class DistanceFogMixin {
    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private static FogParameters clearview$remove(FogParameters fog, @Local(argsOnly = true) Camera camera,
                                                  @Local(argsOnly = true) FogRenderer.FogMode mode) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || camera.getFluidInCamera() != FogType.NONE
                || !ClearViewRuntime.clears(Medium.AIR, camera.getEntity())) {
            return fog;
        }
        return new FogParameters(Medium.AIR.start(), Medium.AIR.end(), fog.shape(),
                fog.red(), fog.green(), fog.blue(), fog.alpha());
    }
}
