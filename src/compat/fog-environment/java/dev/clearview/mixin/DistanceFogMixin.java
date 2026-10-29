package dev.clearview.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.clearview.client.ClearViewRuntime;
import dev.clearview.client.Medium;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class DistanceFogMixin {
    @Inject(method = "setupFog", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F"))
    private void clearview$remove(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) Camera camera,
                                  @Local FogData fog) {
        if (camera.getFluidInCamera() != FogType.NONE
                || !ClearViewRuntime.clears(Medium.AIR, Minecraft.getInstance().player)) {
            return;
        }
        fog.environmentalStart = Medium.AIR.start();
        fog.environmentalEnd = Medium.AIR.end();
        fog.renderDistanceStart = Medium.AIR.start();
        fog.renderDistanceEnd = Medium.AIR.end();
    }
}
