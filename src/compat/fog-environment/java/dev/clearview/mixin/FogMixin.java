package dev.clearview.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.clearview.client.ClearViewRuntime;
import dev.clearview.client.Medium;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.PowderedSnowFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LavaFogEnvironment.class, WaterFogEnvironment.class, PowderedSnowFogEnvironment.class})
abstract class FogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void clearview$clear(CallbackInfo ci, @Local(argsOnly = true) FogData fog) {
        Object self = this;
        Medium medium = self instanceof LavaFogEnvironment ? Medium.LAVA
                : self instanceof WaterFogEnvironment ? Medium.WATER
                : Medium.POWDER_SNOW;
        if (!ClearViewRuntime.clears(medium, null)) {
            return;
        }
        fog.environmentalStart = medium.start();
        fog.environmentalEnd = medium.end();
    }
}
