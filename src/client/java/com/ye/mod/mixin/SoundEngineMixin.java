package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.NoRenderModule;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void ye$filterSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender == null || !noRender.isEnabled() || sound == null) return;

        if (noRender.muteAllSound.get()) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            return;
        }

        String path = sound.getIdentifier().getPath();
        if (noRender.mutePortalSound.get() && path.contains("portal")) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } else if (noRender.muteExplosionSound.get() && path.contains("explode")) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } else if (noRender.muteThunderSound.get() && (path.contains("thunder") || path.contains("lightning"))) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
