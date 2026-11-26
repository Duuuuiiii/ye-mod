package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.modules.visual.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void ye$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        NoFovModule noFov = ModuleManager.get(NoFovModule.class);
        if (noFov != null && noFov.isEnabled() && noFov.noHurtCam.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void ye$noViewBobbing(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        NoFovModule noFov = ModuleManager.get(NoFovModule.class);
        if (noFov != null && noFov.isEnabled() && noFov.noViewBobbing.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void ye$smoothZoomFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        ZoomModule zoom = ModuleManager.get(ZoomModule.class);
        if (zoom != null) {
            cir.setReturnValue(zoom.adjustFov(cir.getReturnValue()));
        }
    }
}
