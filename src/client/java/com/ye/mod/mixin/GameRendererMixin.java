package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.modules.visual.SinCityFilterModule;
import com.ye.mod.modules.visual.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
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
    @Unique private Identifier ye$appliedFilter = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void ye$syncStylizedShader(CallbackInfo ci) {
        SinCityFilterModule mod = ModuleManager.get(SinCityFilterModule.class);
        Identifier target = (mod != null && mod.isEnabled()) ? mod.getActiveEffectId() : null;
        if (target != null) {
            if (!target.equals(this.currentPostEffect())) {
                this.setPostEffect(target);
                ye$appliedFilter = target;
            }
        } else if (ye$appliedFilter != null) {
            if (ye$appliedFilter.equals(this.currentPostEffect())) {
                this.clearPostEffect();
            }
            ye$appliedFilter = null;
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void ye$hideHandDuringIdle(float partialTick, boolean sleeping, Matrix4f projectionMatrix, CallbackInfo ci) {
        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.shouldHideHand()) {
            ci.cancel();
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
