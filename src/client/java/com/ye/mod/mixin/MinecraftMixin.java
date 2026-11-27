package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.player.PlayerUiModule;
import com.ye.mod.ui.YeGiantCreativeScreen;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public abstract void setScreen(Screen screen);

    @Inject(method = "pauseGame(Z)V", at = @At("HEAD"), cancellable = true)
    private void ye$interceptEscForQuickMenu(boolean pauseOnly, CallbackInfo ci) {
        if (YeQuickMenu.isOpen() && YeQuickMenu.handleEsc()) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void ye$interceptGiantCreativeScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof CreativeModeInventoryScreen) {
            if (PlayerUiModule.bypassGiantCreativeOnce) {
                PlayerUiModule.bypassGiantCreativeOnce = false;
                return;
            }
            PlayerUiModule pui = ModuleManager.get(PlayerUiModule.class);
            if (pui != null && pui.isEnabled() && pui.giantCreativeInv.get()) {
                ci.cancel();
                this.setScreen(new YeGiantCreativeScreen());
            }
        }
    }
}
