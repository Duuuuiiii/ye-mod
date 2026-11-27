package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.BodyScaleModule;
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

        ye$wasFallFlying = true;
        state.isFallFlying = false;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void ye$afterSetupAnim(AvatarRenderState state, CallbackInfo ci) {
        BodyScaleModule bs = ModuleManager.get(BodyScaleModule.class);
        if (bs != null && bs.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            boolean isSelf = mc.player != null && state.id == mc.player.getId();
            if (!bs.onlySelf.get() || isSelf) {
                float hs = bs.getEffectiveHeadScale();
                this.head.xScale = hs;
                this.head.yScale = hs;
                this.head.zScale = hs;
            } else {
                this.head.xScale = 1.0f;
                this.head.yScale = 1.0f;
                this.head.zScale = 1.0f;
            }
        } else {
            this.head.xScale = 1.0f;
            this.head.yScale = 1.0f;
            this.head.zScale = 1.0f;
        }

        if (!ye$wasFallFlying) return;
        state.isFallFlying = true;
        ye$wasFallFlying = false;

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight == null || !flight.isEnabled()) return;

        FlightModelModule.FlightPreset preset = flight.preset.get();
        if (preset.seatedPose || preset == FlightModelModule.FlightPreset.MINI_DRAGON
                || preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            // Seated riding posture
            this.rightLeg.xRot = -1.32F;
            this.leftLeg.xRot = -1.32F;
            this.rightLeg.yRot = 0.26F;
            this.leftLeg.yRot = -0.26F;
            this.rightLeg.zRot = 0.08F;
            this.leftLeg.zRot = -0.08F;

            this.rightArm.xRot = -0.58F;
            this.leftArm.xRot = -0.58F;
            this.rightArm.yRot = -0.16F;
            this.leftArm.yRot = 0.16F;
        } else if (preset == FlightModelModule.FlightPreset.MECHA_JET) {
            // Sleek Iron-Man / Jetpack angled flight stance
            this.rightLeg.xRot = 0.22F;
            this.leftLeg.xRot = 0.22F;
            this.rightLeg.zRot = 0.06F;
            this.leftLeg.zRot = -0.06F;
            this.rightArm.xRot = 0.28F;
            this.leftArm.xRot = 0.28F;
            this.rightArm.zRot = 0.26F;
            this.leftArm.zRot = -0.26F;
        } else if (preset.standingSurfPose) {
            // Heroic surfing stance for Sword, Star Astrolabe, Giant Shuriken, Paper Glider
            this.body.yRot = 0.30F;
            this.rightLeg.xRot = 0.14F;
            this.leftLeg.xRot = -0.14F;
            this.rightArm.xRot = -0.32F;
            this.leftArm.xRot = -0.32F;
            this.rightArm.zRot = 0.24F;
            this.leftArm.zRot = -0.24F;
        }
    }
}
