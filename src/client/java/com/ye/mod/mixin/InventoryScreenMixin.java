package com.ye.mod.mixin;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.player.PlayerUiModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Side-Slide Drawer Survival Inventory (生存模式侧边滑出式物品栏):
 * Docks the survival inventory cleanly to the Left or Right edge of the screen with a smooth slide-in animation,
 * leaving the center of the screen 100% unobstructed and undimmed so the player can watch their surroundings!
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
    @Unique private long ye$openTimeNs = 0L;

    private InventoryScreenMixin(InventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, null, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ye$dockSurvivalToSide(CallbackInfo ci) {
        PlayerUiModule ui = ModuleManager.get(PlayerUiModule.class);
        if (ui != null && ui.isEnabled() && ui.sideSurvivalInv.get()) {
            boolean left = ui.survivalSide.get() == PlayerUiModule.DrawerSide.LEFT;
            this.leftPos = left ? 14 : Math.max(14, this.width - this.imageWidth - 14);
            this.topPos = (this.height - this.imageHeight) / 2;
            if (ye$openTimeNs == 0L) {
                ye$openTimeNs = System.nanoTime();
            }
        }
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void ye$keepDockedIfRecipeClosed(CallbackInfo ci) {
        PlayerUiModule ui = ModuleManager.get(PlayerUiModule.class);
        if (ui != null && ui.isEnabled() && ui.sideSurvivalInv.get()) {
            boolean left = ui.survivalSide.get() == PlayerUiModule.DrawerSide.LEFT;
            this.leftPos = left ? 14 : Math.max(14, this.width - this.imageWidth - 14);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PlayerUiModule ui = ModuleManager.get(PlayerUiModule.class);
        if (ui != null && ui.isEnabled() && ui.sideSurvivalInv.get()) {
            // Skip full-screen black dim so center screen stays 100% clear! Draw only a subtle side shadow behind the drawer!
            boolean left = ui.survivalSide.get() == PlayerUiModule.DrawerSide.LEFT;
            if (left) {
                g.fillGradient(0, 0, this.leftPos + this.imageWidth + 28, this.height, 0x66060911, 0x00060911);
            } else {
                g.fillGradient(Math.max(0, this.leftPos - 28), 0, this.width, this.height, 0x00060911, 0x66060911);
            }
            return;
        }
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ye$pushSideSlideAnim(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PlayerUiModule ui = ModuleManager.get(PlayerUiModule.class);
        if (ui != null && ui.isEnabled() && ui.sideSurvivalInv.get()) {
            float prog = 1.0f;
            if (ConfigManager.uiStyle == ConfigManager.UiStyle.SILKY && ye$openTimeNs > 0L) {
                float sec = (System.nanoTime() - ye$openTimeNs) / 1_000_000_000.0f;
                prog = 1.0f - (float) Math.pow(Math.max(0.0f, 1.0f - Math.min(1.0f, sec / 0.22f)), 3.0);
            }
            boolean left = ui.survivalSide.get() == PlayerUiModule.DrawerSide.LEFT;
            float slideX = (1.0f - prog) * (left ? -(this.imageWidth + 20.0f) : (this.imageWidth + 20.0f));
            g.pose().pushMatrix();
            g.pose().translate(slideX, 0.0f);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ye$popSideSlideAnim(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PlayerUiModule ui = ModuleManager.get(PlayerUiModule.class);
        if (ui != null && ui.isEnabled() && ui.sideSurvivalInv.get()) {
            g.pose().popMatrix();
        }
    }
}
