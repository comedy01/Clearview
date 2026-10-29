package dev.clearview.selftest.mixin;

import dev.clearview.selftest.ClearViewSelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void clearviewSelftest$frame(boolean advanceGameTime, CallbackInfo ci) {
        ClearViewSelfTest.frame();
    }
}
