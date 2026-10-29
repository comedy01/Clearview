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
abstract class FogMixin {
    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private static FogParameters clearview$clear(FogParameters fog, @Local(argsOnly = true) Camera camera,
                                                 @Local(argsOnly = true, ordinal = 0) float far) {
        Medium medium = clearview$medium(camera.getFluidInCamera());
        if (medium == null || !ClearViewRuntime.clears(medium, camera.getEntity())) {
            return fog;
        }
        return new FogParameters(medium.start(), Math.min(far, medium.end()), fog.shape(),
                fog.red(), fog.green(), fog.blue(), fog.alpha());
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
