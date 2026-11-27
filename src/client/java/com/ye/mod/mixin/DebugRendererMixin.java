package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.EntityEspModule;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.render.TrajectoryPreviewModule;
import com.ye.mod.modules.world.BlockEspModule;
import com.ye.mod.modules.world.BlockSearchModule;
import com.ye.mod.modules.world.LogoutGhostsModule;
import com.ye.mod.modules.world.PortalFinderModule;
import com.ye.mod.modules.world.StashRadarModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {

    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void ye$emitWorldGizmos(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
        // Compute 100% interpolated camera crosshair origin so tracer lines NEVER jitter or fly around while moving!
        Vec3 tracerStart = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vector3fc fwd = cam.forwardVector();
            tracerStart = new Vec3(camX + fwd.x() * 0.65, camY + fwd.y() * 0.65, camZ + fwd.z() * 0.65);
        }

        LavaEspModule lavaEsp = ModuleManager.get(LavaEspModule.class);
        if (lavaEsp != null) lavaEsp.emitGizmos();

        TrajectoryPreviewModule traj = ModuleManager.get(TrajectoryPreviewModule.class);
        if (traj != null) traj.emitGizmos();

        StashRadarModule radar = ModuleManager.get(StashRadarModule.class);
        if (radar != null) radar.emitGizmos(tracerStart);

        BlockEspModule blockEsp = ModuleManager.get(BlockEspModule.class);
        if (blockEsp != null) blockEsp.emitGizmos(tracerStart);

        BlockSearchModule blockSearch = ModuleManager.get(BlockSearchModule.class);
        if (blockSearch != null) blockSearch.emitGizmos(tracerStart);

        LogoutGhostsModule ghosts = ModuleManager.get(LogoutGhostsModule.class);
        if (ghosts != null) ghosts.emitGizmos();

        PortalFinderModule portal = ModuleManager.get(PortalFinderModule.class);
        if (portal != null) portal.emitGizmos(tracerStart);

        EntityEspModule entityEsp = ModuleManager.get(EntityEspModule.class);
        if (entityEsp != null) entityEsp.emitGizmos(partialTick);
    }
}
