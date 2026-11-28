package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AntiLagModule;
import com.ye.mod.modules.render.NoRenderModule;
import com.ye.mod.modules.world.AutoLogModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"))
    private void ye$detectTotemPop(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
        if (options != null && options.getType() == ParticleTypes.TOTEM_OF_UNDYING) {
            AutoLogModule autoLog = ModuleManager.get(AutoLogModule.class);
            if (autoLog != null) {
                autoLog.onEntityTotemPop(entity);
            }
        }
    }

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void ye$filterCreateParticle(ParticleOptions options, double x, double y, double z,
                                         double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        AntiLagModule antiLag = ModuleManager.get(AntiLagModule.class);
        if (antiLag != null && antiLag.shouldSkipParticle()) {
            cir.setReturnValue(null);
            return;
        }
        com.ye.mod.modules.visual.ParticleManagerModule pm = ModuleManager.get(com.ye.mod.modules.visual.ParticleManagerModule.class);
        if (pm != null && pm.shouldBlockParticle(options)) {
            cir.setReturnValue(null);
            return;
        }
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender == null || !noRender.isEnabled()) return;

        if (noRender.noParticles.get()) {
            cir.setReturnValue(null);
            return;
        }

        if (noRender.noExplosionParticles.get() &&
                (options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ye$filterAddParticle(Particle particle, CallbackInfo ci) {
        AntiLagModule antiLag = ModuleManager.get(AntiLagModule.class);
        if (antiLag != null && antiLag.shouldSkipParticle()) {
            ci.cancel();
            return;
        }
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noParticles.get()) {
            ci.cancel();
        }
    }
}
