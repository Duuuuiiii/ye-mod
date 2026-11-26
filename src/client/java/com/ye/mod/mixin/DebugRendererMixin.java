package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.render.TrajectoryPreviewModule;
import com.ye.mod.modules.visual.BlackHoleSkyModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import com.ye.mod.modules.world.LogoutGhostsModule;
import com.ye.mod.modules.world.PortalFinderModule;
import com.ye.mod.modules.world.StashRadarModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {

    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void ye$emitWorldGizmos(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
        LavaEspModule lavaEsp = ModuleManager.get(LavaEspModule.class);
        if (lavaEsp != null) lavaEsp.emitGizmos();

        TrajectoryPreviewModule traj = ModuleManager.get(TrajectoryPreviewModule.class);
        if (traj != null) traj.emitGizmos();

        StashRadarModule radar = ModuleManager.get(StashRadarModule.class);
        if (radar != null) radar.emitGizmos();

        LogoutGhostsModule ghosts = ModuleManager.get(LogoutGhostsModule.class);
        if (ghosts != null) ghosts.emitGizmos();

        HitFeedbackModule hit = ModuleManager.get(HitFeedbackModule.class);
        if (hit != null) hit.emitGizmos();

        PortalFinderModule portal = ModuleManager.get(PortalFinderModule.class);
        if (portal != null) portal.emitGizmos();

        BlackHoleSkyModule bh = ModuleManager.get(BlackHoleSkyModule.class);
        if (bh != null) bh.emitGizmos();

        VoxelShatterModule vs = ModuleManager.get(VoxelShatterModule.class);
        if (vs != null) vs.emitGizmos();

        StepDecalsModule sd = ModuleManager.get(StepDecalsModule.class);
        if (sd != null) sd.emitGizmos();

        SoulAscendModule sa = ModuleManager.get(SoulAscendModule.class);
        if (sa != null) sa.emitGizmos();
    }
}
