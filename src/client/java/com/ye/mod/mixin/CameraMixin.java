package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.FlightModelModule;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Vec3 position;
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void move(float zoom, float dy, float dx);

    @Inject(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void ye$centerThirdPersonOnRider(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (!thirdPerson || !(entity instanceof LocalPlayer lp) || !lp.isFallFlying()) return;
        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight == null || !flight.isEnabled()) return;
        if (flight.preset.get() == FlightModelModule.FlightPreset.SUPERMAN) return;

        // Vanilla fall-flying drops eyeHeight to 0.40 (on the dragon mount); raise camera pivot back onto the player's head!
        this.setPosition(this.position.x, this.position.y + 1.25, this.position.z);
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void ye$idleWallpaperOrbit(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof LocalPlayer)) return;
        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.isIdleActive()) {
            this.detached = true;
            this.setRotation(idle.getOrbitYaw(), 12.0f);
            this.move(-idle.getOrbitDistance(), 0.0f, 0.0f);
        }
    }
}
