package com.ye.mod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.BlackHoleSkyModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.List;

/**
 * High-resolution 3D World Textured Geometry Renderer + 3D->2D Screen Projector.
 * Renders 512x512 Black Hole, 256x256 Step Decals, 256x256 Hit Shockwaves,
 * 128x128 Soul Wisps, and Single-Batch Voxel Shatter Shards directly through SubmitNodeCollector!
 */
public final class YeWorldFxRenderer {
    private static final Matrix4f LAST_VIEW_PROJ = new Matrix4f();
    private static Vec3 lastCameraPos = Vec3.ZERO;
    private static boolean hasValidMatrix = false;

    private YeWorldFxRenderer() {}

    public static void captureMatrices(Vec3 camPos, Matrix4f viewMat, Matrix4f projMat) {
        lastCameraPos = camPos;
        projMat.mul(viewMat, LAST_VIEW_PROJ);
        hasValidMatrix = true;
    }

    /**
     * Projects a 3D world position onto 2D GUI screen coordinates [x, y], or returns null if behind camera.
     */
    public static float[] projectWorldToGui(double wx, double wy, double wz, int guiW, int guiH) {
        if (!hasValidMatrix) return null;
        float rx = (float) (wx - lastCameraPos.x);
        float ry = (float) (wy - lastCameraPos.y);
        float rz = (float) (wz - lastCameraPos.z);
        Vector4f clip = new Vector4f(rx, ry, rz, 1.0f).mul(LAST_VIEW_PROJ);
        if (clip.w <= 0.05f) return null;
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (ndcX < -1.2f || ndcX > 1.2f || ndcY < -1.2f || ndcY > 1.2f) return null;
        float sx = (ndcX * 0.5f + 0.5f) * guiW;
        float sy = (0.5f - ndcY * 0.5f) * guiH;
        return new float[]{sx, sy};
    }

    public static void submitWorldEffects(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        if (state == null || state.cameraRenderState == null || state.cameraRenderState.pos == null) return;
        YeHighResTextures.ensureInit();

        Vec3 cam = state.cameraRenderState.pos;
        Quaternionf camRot = state.cameraRenderState.orientation;
        long now = System.currentTimeMillis();

        renderBlackHole(poseStack, collector, cam, camRot, now);
        renderStepDecals(poseStack, collector, cam, now);
        renderHitShockwaves(poseStack, collector, cam, camRot, now);
        renderSoulWisps(poseStack, collector, cam, camRot, now);
        renderVoxelShardsSingleBatch(poseStack, collector, cam, now);
    }

    private static void renderBlackHole(PoseStack ps, SubmitNodeCollector collector, Vec3 cam, Quaternionf camRot, long now) {
        BlackHoleSkyModule bh = ModuleManager.get(BlackHoleSkyModule.class);
        if (bh == null || !bh.isEnabled()) return;

        float sz = bh.size.get().floatValue();
        float elev = bh.elevation.get().floatValue();
        float spinDeg = (float) (((now / 25.0) * bh.spinSpeed.get()) % 360.0);
        BlackHoleSkyModule.Theme th = bh.theme.get();

        // Positioned high in the sky relative to camera so it hangs majestically like a celestial body
        float dx = 0.0f;
        float dy = elev;
        float dz = -78.0f;
        float coreHalf = 15.5f * sz;
        float diskHalf = 34.0f * sz;

        // 1) Camera-facing Gargantua Shadow + Photon Ring + Einstein Lensing Halo (512x512)
        ps.pushPose();
        ps.translate(dx, dy, dz);
        if (camRot != null) ps.mulPose(camRot);
        int haloArgb = 0xFF000000 | (th.innerCol & 0x00FFFFFF);
        collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.BLACK_HOLE_CORE_TEX), (pose, vc) ->
                emitBillboardQuad(pose, vc, coreHalf, haloArgb));
        ps.popPose();

        // 2) Tilted 3D Relativistic Accretion Disk (outer + hot inner counter-swirl, 512x512)
        ps.pushPose();
        ps.translate(dx, dy, dz);
        ps.mulPose(Axis.XP.rotationDegrees(72.0f));
        ps.mulPose(Axis.ZP.rotationDegrees(-12.0f));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        int diskCol = 0xFA000000 | (th.midCol & 0x00FFFFFF);
        collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.BLACK_HOLE_DISK_TEX), (pose, vc) ->
                emitHorizontalQuad(pose, vc, diskHalf, diskCol));
        ps.popPose();

        ps.pushPose();
        ps.translate(0.0f, 0.18f, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg * 1.65f + 45.0f));
        int innerCol = 0xDD000000 | (th.innerCol & 0x00FFFFFF);
        collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.BLACK_HOLE_DISK_TEX), (pose, vc) ->
                emitHorizontalQuad(pose, vc, diskHalf * 0.68f, innerCol));
        ps.popPose();
        ps.popPose();
    }

    private static void renderStepDecals(PoseStack ps, SubmitNodeCollector collector, Vec3 cam, long now) {
        StepDecalsModule sd = ModuleManager.get(StepDecalsModule.class);
        if (sd == null || !sd.isEnabled()) return;
        List<StepDecalsModule.Decal> list = sd.getDecals();
        if (list.isEmpty()) return;

        Identifier tex = switch (sd.style.get()) {
            case LAVA_SCORCH -> YeHighResTextures.STEP_LAVA_TEX;
            case CYBER_RUNE -> YeHighResTextures.STEP_CYBER_TEX;
            case VOID_ASTROLABE -> YeHighResTextures.STEP_VOID_TEX;
            case STAR_SIGIL -> YeHighResTextures.STEP_STAR_TEX;
        };
        float baseR = sd.scale.get().floatValue();

        for (StepDecalsModule.Decal d : list) {
            long age = now - d.spawnMs();
            if (age < 0 || age > 2600L) continue;
            float t = age / 2600.0f;
            float r = baseR * (0.85f + 0.25f * t);
            int alpha = Math.max(8, Math.round((1.0f - t * t) * 240.0f));
            int col = (alpha << 24) | 0x00FFFFFF;

            ps.pushPose();
            ps.translate(d.x() - cam.x, d.y() - cam.y, d.z() - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-d.yaw() + t * 28.0f));
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(tex), (pose, vc) ->
                    emitHorizontalQuad(pose, vc, r, col));
            ps.popPose();
        }
    }

    private static void renderHitShockwaves(PoseStack ps, SubmitNodeCollector collector, Vec3 cam, Quaternionf camRot, long now) {
        HitFeedbackModule hf = ModuleManager.get(HitFeedbackModule.class);
        if (hf == null || !hf.isEnabled()) return;
        List<HitFeedbackModule.HitPulse> pulses = hf.getPulses();
        if (pulses.isEmpty()) return;

        HitFeedbackModule.HitPreset pType = hf.preset.get();
        float sc = hf.scale.get().floatValue();

        for (HitFeedbackModule.HitPulse p : pulses) {
            long age = now - p.startMs();
            if (age < 0 || age > 450L) {
                pulses.remove(p);
                continue;
            }
            float t = age / 450.0f;
            float ease = 1.0f - (1.0f - t) * (1.0f - t);
            float waveR = (0.35f + ease * 1.35f) * sc;
            float flareR = (0.95f - t * 0.45f) * sc;
            int aWave = Math.max(10, Math.round((1.0f - t) * 245.0f));
            int aFlare = Math.max(10, Math.round((1.0f - t) * (1.0f - t) * 255.0f));

            int colOuter = (aWave << 24) | pType.outerRgb;
            int colCore = (aFlare << 24) | pType.coreRgb;

            // 1) Camera-facing star flare & shockwave burst
            ps.pushPose();
            ps.translate(p.pos().x - cam.x, p.pos().y - cam.y, p.pos().z - cam.z);
            if (camRot != null) ps.mulPose(camRot);
            ps.mulPose(Axis.ZP.rotationDegrees(p.rotOffset() + t * 55.0f));
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.HIT_SHOCKWAVE_TEX), (pose, vc) ->
                    emitBillboardQuad(pose, vc, flareR, colCore));
            ps.popPose();

            // 2) Horizontal expanding 3D shockwave ring
            ps.pushPose();
            ps.translate(p.pos().x - cam.x, p.pos().y - cam.y, p.pos().z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(p.rotOffset() - t * 90.0f));
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.HIT_SHOCKWAVE_TEX), (pose, vc) ->
                    emitHorizontalQuad(pose, vc, waveR, colOuter));
            ps.popPose();
        }
    }

    private static void renderSoulWisps(PoseStack ps, SubmitNodeCollector collector, Vec3 cam, Quaternionf camRot, long now) {
        SoulAscendModule sa = ModuleManager.get(SoulAscendModule.class);
        if (sa == null || !sa.isEnabled()) return;
        List<SoulAscendModule.SoulWisp> wisps = sa.getWisps();
        if (wisps.isEmpty()) return;

        for (SoulAscendModule.SoulWisp w : wisps) {
            long age = now - w.spawnMs();
            if (age < 0 || age > 2800L) continue;
            float t = age / 2800.0f;
            int alpha = Math.max(10, Math.round((1.0f - t) * 235.0f));
            int col = (alpha << 24) | 0x007DD3FC;

            double wx = w.x() + Math.sin(t * 7.5 + w.phase()) * 0.36;
            double wy = w.y() + t * 5.2;
            double wz = w.z() + Math.cos(t * 6.5 + w.phase()) * 0.36;

            ps.pushPose();
            ps.translate(wx - cam.x, wy - cam.y, wz - cam.z);
            if (camRot != null) ps.mulPose(camRot);
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.SOUL_WISP_TEX), (pose, vc) ->
                    emitBillboardQuad(pose, vc, 0.45f * (1.0f - t * 0.3f), col));
            ps.popPose();
        }
    }

    private static void renderVoxelShardsSingleBatch(PoseStack ps, SubmitNodeCollector collector, Vec3 cam, long now) {
        VoxelShatterModule vs = ModuleManager.get(VoxelShatterModule.class);
        if (vs == null || !vs.isEnabled()) return;
        List<VoxelShatterModule.VoxelPiece> pieces = vs.getPieces();
        if (pieces.isEmpty()) return;

        // ONE SINGLE GPU draw call for all active shards -> ZERO FPS drop!
        final double cx = cam.x, cy = cam.y, cz = cam.z;
        collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
            for (VoxelShatterModule.VoxelPiece p : pieces) {
                float age = (now - p.spawnMs) / 1600.0f;
                if (age < 0f || age > 1f) continue;
                int a = Math.max(15, Math.round((1.0f - age) * 240.0f));
                int argb = (a << 24) | (p.rgb & 0x00FFFFFF);
                float hs = (float) (p.size * 0.5 * (1.0f - age * 0.35f));
                float rx = (float) (p.x - cx);
                float ry = (float) (p.y - cy);
                float rz = (float) (p.z - cz);
                drawSmallBox(pose, vc, argb, rx - hs, ry - hs, rz - hs, rx + hs, ry + hs, rz + hs);
            }
        });
    }

    private static void emitBillboardQuad(PoseStack.Pose pose, VertexConsumer vc, float half, int argb) {
        emit(pose, vc, -half, -half, 0f, argb, 0f, 1f, 0f, 0f, 1f);
        emit(pose, vc,  half, -half, 0f, argb, 1f, 1f, 0f, 0f, 1f);
        emit(pose, vc,  half,  half, 0f, argb, 1f, 0f, 0f, 0f, 1f);
        emit(pose, vc, -half,  half, 0f, argb, 0f, 0f, 0f, 0f, 1f);

        emit(pose, vc, -half,  half, 0f, argb, 0f, 0f, 0f, 0f, -1f);
        emit(pose, vc,  half,  half, 0f, argb, 1f, 0f, 0f, 0f, -1f);
        emit(pose, vc,  half, -half, 0f, argb, 1f, 1f, 0f, 0f, -1f);
        emit(pose, vc, -half, -half, 0f, argb, 0f, 1f, 0f, 0f, -1f);
    }

    private static void emitHorizontalQuad(PoseStack.Pose pose, VertexConsumer vc, float half, int argb) {
        emit(pose, vc, -half, 0f, -half, argb, 0f, 0f, 0f, 1f, 0f);
        emit(pose, vc,  half, 0f, -half, argb, 1f, 0f, 0f, 1f, 0f);
        emit(pose, vc,  half, 0f,  half, argb, 1f, 1f, 0f, 1f, 0f);
        emit(pose, vc, -half, 0f,  half, argb, 0f, 1f, 0f, 1f, 0f);

        emit(pose, vc, -half, 0f,  half, argb, 0f, 1f, 0f, -1f, 0f);
        emit(pose, vc,  half, 0f,  half, argb, 1f, 1f, 0f, -1f, 0f);
        emit(pose, vc,  half, 0f, -half, argb, 1f, 0f, 0f, -1f, 0f);
        emit(pose, vc, -half, 0f, -half, argb, 0f, 0f, 0f, -1f, 0f);
    }

    private static void drawSmallBox(PoseStack.Pose pose, VertexConsumer vc, int argb,
                                     float x0, float y0, float z0, float x1, float y1, float z1) {
        boxFace(pose, vc, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
        boxFace(pose, vc, argb, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0f, -1f, 0f);
        boxFace(pose, vc, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
        boxFace(pose, vc, argb, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0f, 0f, 1f);
        boxFace(pose, vc, argb, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1f, 0f, 0f);
        boxFace(pose, vc, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f);
    }

    private static void boxFace(PoseStack.Pose pose, VertexConsumer vc, int argb,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float nx, float ny, float nz) {
        emit(pose, vc, x1, y1, z1, argb, 0f, 0f, nx, ny, nz);
        emit(pose, vc, x2, y2, z2, argb, 1f, 0f, nx, ny, nz);
        emit(pose, vc, x3, y3, z3, argb, 1f, 1f, nx, ny, nz);
        emit(pose, vc, x4, y4, z4, argb, 0f, 1f, nx, ny, nz);
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc,
                             float x, float y, float z, int argb, float u, float v,
                             float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, nx, ny, nz);
    }
}
