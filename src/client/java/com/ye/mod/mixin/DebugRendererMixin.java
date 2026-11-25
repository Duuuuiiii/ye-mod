package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.render.MotionTrailModule;
import com.ye.mod.modules.render.TrajectoryPreviewModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.SoulRingsModule;
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

        MotionTrailModule trail = ModuleManager.get(MotionTrailModule.class);
        if (trail != null) trail.emitGizmos();

        StashRadarModule radar = ModuleManager.get(StashRadarModule.class);
        if (radar != null) radar.emitGizmos();

        LogoutGhostsModule ghosts = ModuleManager.get(LogoutGhostsModule.class);
        if (ghosts != null) ghosts.emitGizmos();

        HitFeedbackModule hit = ModuleManager.get(HitFeedbackModule.class);
        if (hit != null) hit.emitGizmos();

        SoulRingsModule rings = ModuleManager.get(SoulRingsModule.class);
        if (rings != null) rings.emitGizmos();

        PortalFinderModule portal = ModuleManager.get(PortalFinderModule.class);
        if (portal != null) portal.emitGizmos();
    }
}
