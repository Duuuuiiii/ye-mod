package com.ye.mod.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.render.YeSkyPanoramaRenderer;
import com.ye.mod.render.YeWorldFxRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ye$captureViewProj(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                    boolean renderBlockOutline, Camera camera, Matrix4f frustumMatrix,
                                    Matrix4f projectionMatrix, Matrix4f cullingProjectionMatrix,
                                    GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (camera != null && frustumMatrix != null && projectionMatrix != null) {
            YeWorldFxRenderer.captureMatrices(camera.position(), frustumMatrix, projectionMatrix);
        }
    }

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void ye$submitHighResWorldFx(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        com.ye.mod.modules.render.EntityEspModule esp = ModuleManager.get(com.ye.mod.modules.render.EntityEspModule.class);
        if (esp != null && esp.consumeOutlineFlag() && state != null) {
            state.haveGlowingEntities = true;
        }
        AmbienceModule amb = ModuleManager.get(AmbienceModule.class);
        if (amb != null && amb.usesPanoramaDome()) {
            YeSkyPanoramaRenderer.submitSkyDome(poseStack, collector, amb);
        }
        YeWorldFxRenderer.submitWorldEffects(poseStack, state, collector);
    }
}
