package com.ye.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void ye$hideElytraForFlightModel(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                             HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState)) return;
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && avatarState.id == mc.player.getId();

        DragonWingsModule wings = ModuleManager.get(DragonWingsModule.class);
        if (wings != null && wings.shouldRenderWings(avatarState.isFallFlying) && wings.hideElytra.get() && (!wings.onlySelf.get() || isSelf)) {
            ci.cancel();
            return;
        }

        if (!avatarState.isFallFlying) return;
        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight == null || !flight.isEnabled() || !flight.hideElytra.get()) return;

        if (!flight.onlySelf.get() || isSelf) {
            ci.cancel();
        }
    }
}
