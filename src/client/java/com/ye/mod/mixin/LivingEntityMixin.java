package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.SwingSpeedModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getCurrentSwingDuration()I", at = @At("RETURN"), cancellable = true)
    private void ye$customSwingSpeed(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this != mc.player) return;
        SwingSpeedModule mod = ModuleManager.get(SwingSpeedModule.class);
        if (mod != null && mod.isEnabled()) {
            int orig = cir.getReturnValue();
            int scaled = Math.max(1, (int) Math.round(orig / mod.speed.get()));
            cir.setReturnValue(scaled);
        }
    }
}
