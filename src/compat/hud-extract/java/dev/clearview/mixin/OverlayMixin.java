package dev.clearview.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.clearview.client.ClearViewRuntime;
import net.minecraft.client.gui.Gui;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class OverlayMixin {
    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void clearview$hide(CallbackInfo ci, @Local(argsOnly = true) Identifier texture) {
        if (ClearViewRuntime.hideOverlay(texture.getPath())) {
            ci.cancel();
        }
    }
}
