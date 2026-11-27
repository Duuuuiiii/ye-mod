package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.player.FlightSpeedModule;
import com.ye.mod.modules.visual.CameraTweaksModule;
import com.ye.mod.modules.visual.ZoomModule;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ye$interceptScrollWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        ZoomModule zoom = ModuleManager.get(ZoomModule.class);
        if (zoom != null && zoom.onMouseScroll(yOffset)) {
            ci.cancel();
            return;
        }
        FlightSpeedModule fs = ModuleManager.get(FlightSpeedModule.class);
        if (fs != null && fs.onMouseScroll(yOffset)) {
            ci.cancel();
            return;
        }
        CameraTweaksModule cam = ModuleManager.get(CameraTweaksModule.class);
        if (cam != null && cam.onMouseScroll(yOffset)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void ye$redirectTurnForFreeLook(net.minecraft.client.player.LocalPlayer player, double yRot, double xRot) {
        CameraTweaksModule cam = ModuleManager.get(CameraTweaksModule.class);
        if (cam != null && cam.isFreeLookActive()) {
            cam.onMouseTurnFreeLook(yRot, xRot);
        } else {
            player.turn(yRot, xRot);
        }
    }
}
