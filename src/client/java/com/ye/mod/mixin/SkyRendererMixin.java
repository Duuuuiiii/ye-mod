package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.AmbienceModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ye$overrideSkyState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.customSkyAndFog.get()) {
            state.skyColor = ambience.getSkyArgb();
            state.sunriseAndSunsetColor = 0;
            state.rainBrightness = 0.0f;
            state.shouldRenderDarkDisc = true;
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

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void ye$hideCelestialBodies(PoseStack poseStack, float timeOfDay, float rainLevel, float starBrightness,
                                        MoonPhase moonPhase, float moonAngle, float sunAlpha, CallbackInfo ci) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.hideCelestial.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void ye$hideSunriseSunset(PoseStack poseStack, float sunAngle, int color, CallbackInfo ci) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.customSkyAndFog.get()) {
            ci.cancel();
        }
    }
}
