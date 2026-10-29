package dev.clearview.mixin;

import dev.clearview.client.ClearViewRuntime;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
abstract class OptionsMixin {
    @Shadow
    public abstract OptionInstance<Double> gamma();

    @Inject(method = "save", at = @At("HEAD"))
    private void clearview$beforeSave(CallbackInfo ci) {
        ClearViewRuntime.beforeSave(gamma());
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void clearview$afterSave(CallbackInfo ci) {
        ClearViewRuntime.afterSave(gamma());
    }
}
