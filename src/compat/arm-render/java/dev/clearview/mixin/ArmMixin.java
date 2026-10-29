package dev.clearview.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clearview.client.ClearViewRuntime;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ArmMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void clearview$lower(CallbackInfo ci, @Local(argsOnly = true) ItemStack stack,
                                 @Local(argsOnly = true) PoseStack pose) {
        pose.pushPose();
        pose.translate(0.0F, -ClearViewRuntime.shieldDrop(stack), 0.0F);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void clearview$restore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.popPose();
    }
}
