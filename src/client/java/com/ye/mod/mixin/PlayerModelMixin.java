package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.FlightModelModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {

    @Unique
    private boolean ye$wasFallFlying = false;

    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
    private void ye$beforeSetupAnim(AvatarRenderState state, CallbackInfo ci) {
        ye$wasFallFlying = false;
        if (!state.isFallFlying) return;

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight == null || !flight.isEnabled()) return;
        if (flight.preset.get() == FlightModelModule.FlightPreset.SUPERMAN) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();
        if (flight.onlySelf.get() && !isSelf) return;

        // Prevent vanilla fall-flying pose from locking the head looking straight up
        ye$wasFallFlying = true;
        state.isFallFlying = false;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void ye$afterSetupAnim(AvatarRenderState state, CallbackInfo ci) {
        if (!ye$wasFallFlying) return;
        state.isFallFlying = true;
        ye$wasFallFlying = false;

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight == null || !flight.isEnabled()) return;

        FlightModelModule.FlightPreset preset = flight.preset.get();
        if (preset == FlightModelModule.FlightPreset.MINI_DRAGON
                || preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            // Proper sitting pose straddling the mount
            this.rightLeg.xRot = -1.35F;
            this.leftLeg.xRot = -1.35F;
            this.rightLeg.yRot = 0.28F;
            this.leftLeg.yRot = -0.28F;
            this.rightLeg.zRot = 0.08F;
            this.leftLeg.zRot = -0.08F;

            this.rightArm.xRot = -0.62F;
            this.leftArm.xRot = -0.62F;
            this.rightArm.yRot = -0.18F;
            this.leftArm.yRot = 0.18F;
        } else if (preset == FlightModelModule.FlightPreset.SWORD_SURF) {
            // Heroic sword-surfing stance
            this.body.yRot = 0.35F;
            this.rightLeg.xRot = 0.15F;
            this.leftLeg.xRot = -0.15F;
            this.rightArm.xRot = -0.35F;
            this.leftArm.xRot = -0.35F;
            this.rightArm.zRot = 0.25F;
            this.leftArm.zRot = -0.25F;
        }
    }
}
