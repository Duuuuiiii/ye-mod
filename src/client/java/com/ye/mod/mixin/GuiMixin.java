package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AntiLagModule;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.render.NoRenderModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.visual.CinematicBarsModule;
import com.ye.mod.modules.visual.CrosshairModule;
import com.ye.mod.modules.visual.DamagePopupsModule;
import com.ye.mod.modules.visual.HealthPlatesModule;
import com.ye.mod.modules.visual.HitCrosshairModule;
import com.ye.mod.modules.visual.HudModule;
import com.ye.mod.modules.visual.LowHpVignetteModule;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.modules.world.PortalSyncModule;
import com.ye.mod.modules.world.StashRadarModule;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void ye$noPortalOverlay(GuiGraphics guiGraphics, float alpha, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noPortalOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void ye$noConfusionOverlay(GuiGraphics guiGraphics, float strength, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        NoFovModule noFov = ModuleManager.get(NoFovModule.class);
        boolean block = (noRender != null && noRender.isEnabled() && noRender.noNausea.get())
                || (noFov != null && noFov.isEnabled() && noFov.noDistortion.get());
        if (block) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void ye$noVignette(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noVignette.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true)
    private void ye$noBossBar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noBossBar.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void ye$noSpyglassOverlay(GuiGraphics guiGraphics, float scale, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noSpyglassOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void ye$noTextureOverlay(GuiGraphics guiGraphics, Identifier texture, float alpha, CallbackInfo ci) {
        NoRenderModule noRender = ModuleManager.get(NoRenderModule.class);
        if (noRender != null && noRender.isEnabled() && noRender.noPumpkinAndSnow.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void ye$customCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HitCrosshairModule hit = ModuleManager.get(HitCrosshairModule.class);
        if (hit != null) {
            hit.render(guiGraphics);
        }
        CrosshairModule ch = ModuleManager.get(CrosshairModule.class);
        if (ch != null && ch.isEnabled()) {
            ch.render(guiGraphics);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ye$renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        AmbienceModule amb = ModuleManager.get(AmbienceModule.class);
        if (amb != null) {
            amb.renderOverlay(guiGraphics);
        }
        LowHpVignetteModule lowHp = ModuleManager.get(LowHpVignetteModule.class);
        if (lowHp != null) {
            lowHp.render(guiGraphics);
        }
        DamagePopupsModule dmg = ModuleManager.get(DamagePopupsModule.class);
        if (dmg != null) {
            dmg.render(guiGraphics);
        }
        HealthPlatesModule hpBar = ModuleManager.get(HealthPlatesModule.class);
        if (hpBar != null) {
            hpBar.render(guiGraphics);
        }
        CinematicBarsModule bars = ModuleManager.get(CinematicBarsModule.class);
        if (bars != null) {
            bars.render(guiGraphics);
        }
        AntiLagModule antiLag = ModuleManager.get(AntiLagModule.class);
        if (antiLag != null) {
            antiLag.renderHudAlert(guiGraphics);
        }
        LavaEspModule lavaEsp = ModuleManager.get(LavaEspModule.class);
        if (lavaEsp != null) {
            lavaEsp.renderHudAlert(guiGraphics);
        }
        StashRadarModule stashRadar = ModuleManager.get(StashRadarModule.class);
        if (stashRadar != null) {
            stashRadar.renderHud(guiGraphics);
        }
        if (net.minecraft.client.Minecraft.getInstance().screen == null) {
            YeQuickMenu.render(guiGraphics, -1, -1, false);
        }
    }
}
