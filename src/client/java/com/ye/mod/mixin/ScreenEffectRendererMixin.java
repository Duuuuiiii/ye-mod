package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void ye$noFire(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noFire.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void ye$noWater(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noWater.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void ye$noBlockOverlay(TextureAtlasSprite sprite, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noBlockOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void ye$noTotemPop(PoseStack poseStack, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noTotemPop.get()) {
            ci.cancel();
        }
    }
}
