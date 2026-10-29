package dev.clearview.selftest.mixin;

import dev.clearview.client.Medium;
import dev.clearview.selftest.Probe;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.clearview.client.ClearViewRuntime", remap = false)
abstract class RuntimeProbeMixin {
    @Inject(method = "fireDrop", at = @At("RETURN"))
    private static void clearviewSelftest$fire(CallbackInfoReturnable<Float> cir) {
        Probe.fire(cir.getReturnValue());
    }

    @Inject(method = "hideFire", at = @At("RETURN"))
    private static void clearviewSelftest$fireHidden(CallbackInfoReturnable<Boolean> cir) {
        Probe.fireHidden(cir.getReturnValue());
    }

    @Inject(method = "shieldDrop", at = @At("RETURN"))
    private static void clearviewSelftest$shield(CallbackInfoReturnable<Float> cir) {
        Probe.shield(cir.getReturnValue());
    }

    @Inject(method = "hideOverlay", at = @At("RETURN"))
    private static void clearviewSelftest$overlay(String texture, CallbackInfoReturnable<Boolean> cir) {
        Probe.overlay(texture, cir.getReturnValue());
    }

    @Inject(method = "clears", at = @At("RETURN"))
    private static void clearviewSelftest$fog(Medium medium, Entity viewer, CallbackInfoReturnable<Boolean> cir) {
        Probe.fog(medium, cir.getReturnValue());
    }
}
