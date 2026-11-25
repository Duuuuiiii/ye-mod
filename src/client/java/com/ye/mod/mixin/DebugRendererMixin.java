package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.LavaEspModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {

    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void ye$emitLavaEspGizmos(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
        LavaEspModule lavaEsp = ModuleManager.get(LavaEspModule.class);
        if (lavaEsp != null) {
            lavaEsp.emitGizmos();
        }
    }
}
