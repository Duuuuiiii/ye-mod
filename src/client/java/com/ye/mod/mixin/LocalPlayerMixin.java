package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.NoRenderModule;
import com.ye.mod.modules.visual.NoFovModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Shadow
    public float portalEffectIntensity;

    @Shadow
    public float oPortalEffectIntensity;

    @Inject(method = "handlePortalTransitionEffect", at = @At("HEAD"), cancellable = true)
    private void ye$noPortalDistortionAndGuiLock(boolean inPortal, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        NoFovModule noFov = ModuleManager.get(NoFovModule.class);
        boolean block = (noRender != null && noRender.isEnabled() && noRender.noPortalGuiLock.get())
                || (noFov != null && noFov.isEnabled() && noFov.noDistortion.get());
        if (block) {
            this.portalEffectIntensity = 0.0F;
            this.oPortalEffectIntensity = 0.0F;
            ci.cancel();
        }
    }
}
