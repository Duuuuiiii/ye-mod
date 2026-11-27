package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.BodyScaleModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.visual.CameraTweaksModule;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Vec3 position;
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void move(float zoom, float dy, float dx);

    @Inject(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void ye$centerRiderAndFreeLook(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (entity instanceof LocalPlayer lp) {
            BodyScaleModule scaleMod = ModuleManager.get(BodyScaleModule.class);
            if (scaleMod != null && scaleMod.isEnabled()) {
                float bs = scaleMod.getEffectiveBodyScale();
                if (Math.abs(bs - 1.0f) > 0.01f) {
                    this.setPosition(this.position.x, this.position.y + lp.getEyeHeight() * (bs - 1.0f), this.position.z);
                }
            }
            if (thirdPerson && lp.isFallFlying()) {
                FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
                if (flight != null && flight.isEnabled() && flight.preset.get() != FlightModelModule.FlightPreset.SUPERMAN) {
                    this.setPosition(this.position.x, this.position.y + 1.25, this.position.z);
                }
            }
        }

        CameraTweaksModule camMod = ModuleManager.get(CameraTweaksModule.class);
        if (camMod != null && camMod.isFreeLookActive()) {
            this.setRotation(camMod.getFreeYaw(), camMod.getFreePitch());
        }
    }

    @ModifyArg(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
            index = 0)
    private float ye$overrideDesiredCameraDistance(float vanillaDistance) {
        CameraTweaksModule camMod = ModuleManager.get(CameraTweaksModule.class);
        if (camMod != null && camMod.isEnabled()) {
            return camMod.computeSmoothedDistance(true);
        }
        return vanillaDistance;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void ye$cameraClipThroughBlocks(float maxZoom, CallbackInfoReturnable<Float> cir) {
        CameraTweaksModule camMod = ModuleManager.get(CameraTweaksModule.class);
        if (camMod != null && camMod.isEnabled() && camMod.clipBlocks.get()) {
            cir.setReturnValue(maxZoom);
        }
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void ye$applyShoulderOffsetAndIdleOrbit(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof LocalPlayer lp)) return;

        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.isIdleActive()) {
            this.detached = true;
            IdleWallpaperModule.ShotPose pose = idle.computeShotPose(lp.getYRot());
            this.setRotation(pose.yaw(), pose.pitch());
            this.move(-pose.zoomDist(), pose.verticalOffset(), pose.sideOffset());
            return;
        }

        CameraTweaksModule camMod = ModuleManager.get(CameraTweaksModule.class);
        if (camMod != null && camMod.isEnabled()) {
            if (!thirdPerson) {
                camMod.computeSmoothedDistance(false);
            } else {
                float ox = camMod.shoulderOffsetX.get().floatValue();
                float oy = camMod.shoulderOffsetY.get().floatValue();
                if (Math.abs(ox) > 0.001f || Math.abs(oy) > 0.001f) {
                    this.move(0.0f, oy, -ox);
                }
            }
        }
    }
}
