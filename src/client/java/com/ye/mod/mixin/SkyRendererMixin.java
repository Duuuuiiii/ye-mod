package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.AmbienceModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow private void renderStars(float starBrightness, PoseStack poseStack) {}

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ye$overrideSkyState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled()) {
            if (state.skybox == DimensionType.Skybox.NONE) {
                state.skybox = DimensionType.Skybox.OVERWORLD;
            }
            if (ambience.customSkyAndFog.get()) {
                state.skyColor = ambience.getSkyArgb();
                state.sunriseAndSunsetColor = (0xAA << 24) | (ambience.getHorizonArgb() & 0x00FFFFFF);
            }
            state.rainBrightness = 1.0f;
            state.starBrightness = ambience.getStarBrightness(state.starBrightness);
            state.shouldRenderDarkDisc = false;
        }
    }

    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
    private int ye$overrideSkyDiscColor(int color) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.customSkyAndFog.get()) {
            return ambience.getSkyArgb();
        }
        return color;
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void ye$preventBlackHorizonDisc(CallbackInfo ci) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void ye$controlSunMoonStars(PoseStack poseStack, float timeOfDay, float rainLevel, float starBrightness,
                                        net.minecraft.world.level.MoonPhase moonPhase, float moonAngle, float sunAlpha, CallbackInfo ci) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.hideSunMoon.get()) {
            float stars = ambience.getStarBrightness(starBrightness);
            if (stars > 0.01f) {
                this.renderStars(stars, poseStack);
            }
            ci.cancel();
        }
    }
}
