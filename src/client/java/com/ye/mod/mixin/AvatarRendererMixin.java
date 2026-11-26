package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.BodyScaleModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.render.YeCosmeticLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin extends LivingEntityRenderer<net.minecraft.world.entity.Avatar, AvatarRenderState, PlayerModel> {

    protected AvatarRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ye$addCosmeticLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        this.addLayer(new YeCosmeticLayer(this, context.getModelSet()));
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void ye$applyBodyScale(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        BodyScaleModule bs = ModuleManager.get(BodyScaleModule.class);
        if (bs == null || !bs.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();
        if (bs.onlySelf.get() && !isSelf) return;
        float s = bs.getEffectiveBodyScale();
        if (Math.abs(s - 1.0f) > 0.01f) {
            poseStack.scale(s, s, s);
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void ye$overrideFlightRotations(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        if (!state.isFallFlying) return;
        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight == null || !flight.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();
        if (flight.onlySelf.get() && !isSelf) return;

        FlightModelModule.FlightPreset preset = flight.preset.get();
        if (preset == FlightModelModule.FlightPreset.SUPERMAN) {
            return;
        }

        // Keep upright rotation instead of vanilla horizontal elytra pitch
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
        if (preset == FlightModelModule.FlightPreset.MINI_DRAGON
                || preset == FlightModelModule.FlightPreset.MAGIC_BROOM
                || preset == FlightModelModule.FlightPreset.MINI_PHANTOM
                || preset == FlightModelModule.FlightPreset.SWORD_SURF) {
            poseStack.translate(0.0F, 0.25F, 0.0F);
        }
        ci.cancel();
    }
}
