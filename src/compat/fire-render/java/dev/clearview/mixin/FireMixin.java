package dev.clearview.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clearview.client.ClearViewRuntime;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class FireMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void clearview$lower(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        if (ClearViewRuntime.hideFire()) {
            ci.cancel();
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, -ClearViewRuntime.fireDrop(), 0.0F);
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void clearview$restore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.popPose();
    }
}
