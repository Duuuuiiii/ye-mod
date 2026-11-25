package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.AmbienceModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void ye$overrideFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                     float darkenWorldAmount, CallbackInfoReturnable<Vector4f> cir) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.customSkyAndFog.get()) {
            cir.setReturnValue(ambience.getFogColor());
        }
    }

    @ModifyArgs(method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void ye$scaleFogDistance(Args args) {
        AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
        if (ambience != null && ambience.isEnabled() && ambience.customSkyAndFog.get()) {
            float scale = ambience.fogDistanceScale.get().floatValue();
            float envEnd = args.get(4);
            float renderDistEnd = args.get(6);
            args.set(4, envEnd * scale);
            args.set(6, renderDistEnd * scale);
        }
    }
}
