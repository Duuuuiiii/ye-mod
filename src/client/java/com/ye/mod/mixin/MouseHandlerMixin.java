package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.ZoomModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ye$zoomScrollWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        ZoomModule zoom = ModuleManager.get(ZoomModule.class);
        if (zoom != null && zoom.onMouseScroll(yOffset)) {
            ci.cancel();
        }
    }
}
