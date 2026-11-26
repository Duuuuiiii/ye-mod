package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.modules.visual.SinCityFilterModule;
import com.ye.mod.modules.visual.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow private void setPostEffect(Identifier id) {}
    @Shadow public abstract void clearPostEffect();
    @Shadow public abstract Identifier currentPostEffect();
    @Unique private boolean ye$sinCityApplied = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void ye$syncSinCityShader(CallbackInfo ci) {
        SinCityFilterModule mod = ModuleManager.get(SinCityFilterModule.class);
        boolean want = mod != null && mod.isEnabled();
        if (want && !SinCityFilterModule.EFFECT_ID.equals(this.currentPostEffect())) {
            this.setPostEffect(SinCityFilterModule.EFFECT_ID);
            ye$sinCityApplied = true;
        } else if (!want && ye$sinCityApplied) {
            if (SinCityFilterModule.EFFECT_ID.equals(this.currentPostEffect())) {
                this.clearPostEffect();
            }
            ye$sinCityApplied = false;
        }
    }

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
