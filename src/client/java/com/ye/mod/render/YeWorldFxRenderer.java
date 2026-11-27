package com.ye.mod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.List;

/**
 * High-resolution 3D World Geometry Renderer + 3D->2D Screen Projector:
 * - Crisp 3D Glowing Voxel/Block Matrix Footprints (single GPU batch!)
 * - 256x256 Hit Shockwaves
 * - 128x128 Soul Wisps
 * - 144/240Hz Frame-Interpolated Voxel Shatter Shards (single GPU batch!)
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

        renderBlockStepMatrix(poseStack, collector, cam, now);
        renderHitShockwaves(poseStack, collector, cam, camRot, now);
        renderSoulWisps(poseStack, collector, cam, camRot, now);
        renderVoxelShardsSingleBatch(poseStack, collector, cam, now);
    }

    /**
     * Crisp 3D Voxel/Block Matrix Footprints (single-batch or clean rotated 3D slabs — zero blurry grey rings!).
     */
    private static void renderBlockStepMatrix(PoseStack ps, SubmitNodeCollector collector, Vec3 cam, long now) {
        StepDecalsModule sd = ModuleManager.get(StepDecalsModule.class);
        if (sd == null || !sd.isEnabled()) return;
        List<StepDecalsModule.Decal> list = sd.snapshotDecals();
        if (list.isEmpty()) return;

        StepDecalsModule.Style st = sd.style.get();
        int outerRgb = GlobalColorModule.resolveRgb(sd.syncGlobalColor.get(), st.outerArgb & 0x00FFFFFF, 6);
        int coreRgb = sd.syncGlobalColor.get() ? 0xFFFFFF : (st.coreArgb & 0x00FFFFFF);
        float baseS = sd.scale.get().floatValue();

        for (StepDecalsModule.Decal d : list) {
            long age = now - d.spawnMs();
            if (age < 0 || age > 2400L) continue;
            float t = age / 2400.0f;
            float fade = (1.0f - t) * (1.0f - t * 0.5f);
            int aMain = Math.max(12, Math.round(fade * 235.0f));
            int aSub = Math.max(8, Math.round(fade * 145.0f));
            int colOuter = (aMain << 24) | outerRgb;
            int colCore = (aMain << 24) | coreRgb;
            int colRim = (aSub << 24) | outerRgb;

            float r = baseS * (0.92f + t * 0.16f);
            float h = 0.026f * (1.0f - t * 0.45f);

            ps.pushPose();
            ps.translate(d.x() - cam.x, d.y() - cam.y, d.z() - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-d.yaw()));

            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                switch (st) {
                    case CYBER_MATRIX -> {
                        // Crisp 3D pixel-frame borders + glowing center block + floating corner micro-voxels!
                        float tk = r * 0.18f;
                        drawSmallBox(pose, vc, colOuter, -r, 0f, -r, r, h, -r + tk);
                        drawSmallBox(pose, vc, colOuter, -r, 0f, r - tk, r, h, r);
                        drawSmallBox(pose, vc, colOuter, -r, 0f, -r + tk, -r + tk, h, r - tk);
                        drawSmallBox(pose, vc, colOuter, r - tk, 0f, -r + tk, r, h, r - tk);
                        float cr = r * 0.36f;
                        drawSmallBox(pose, vc, colCore, -cr, 0.005f, -cr, cr, h * 1.45f, cr);
                        float lift = t * 0.14f;
                        float cs = r * 0.16f;
                        drawSmallBox(pose, vc, colRim, -r - cs, lift, -r - cs, -r, lift + cs, -r);
                        drawSmallBox(pose, vc, colRim, r, lift, r, r + cs, lift + cs, r + cs);
                    }
                    case MAGMA_EMBER -> {
                        // Dark basalt outer slab + bright molten lava core blocks + rising ember cubes!
                        int crustCol = (aMain << 24) | 0x291D1E;
                        drawSmallBox(pose, vc, crustCol, -r, 0f, -r, r, h * 0.85f, r);
                        float mr = r * 0.72f;
                        drawSmallBox(pose, vc, colOuter, -mr, 0.004f, -mr, mr, h * 1.35f, mr);
                        float ir = r * 0.38f;
                        drawSmallBox(pose, vc, colCore, -ir, 0.008f, -ir, ir, h * 1.75f, ir);
                        float emberY = 0.03f + t * 0.22f;
                        float es = r * 0.14f;
                        drawSmallBox(pose, vc, colCore, -es, emberY, -es, es, emberY + es * 1.5f, es);
                    }
                    case FROST_CRYSTAL -> {
                        // Crossed 3D ice-crystal bars + elevated central prism cube
                        float wBar = r * 0.28f;
                        drawSmallBox(pose, vc, colOuter, -r, 0f, -wBar, r, h * 1.2f, wBar);
                        drawSmallBox(pose, vc, colOuter, -wBar, 0f, -r, wBar, h * 1.2f, r);
                        float diag = r * 0.62f;
                        drawSmallBox(pose, vc, colRim, -diag, 0.003f, -diag, diag, h * 0.9f, diag);
                        float pc = r * 0.26f;
                        drawSmallBox(pose, vc, colCore, -pc, 0.008f, -pc, pc, h * 2.0f, pc);
                    }
                    case GOLD_SEAL -> {
                        // Stepped imperial square seal with four corner pillars
                        drawSmallBox(pose, vc, colRim, -r, 0f, -r, r, h * 0.8f, r);
                        float r2 = r * 0.66f;
                        drawSmallBox(pose, vc, colOuter, -r2, h * 0.8f, -r2, r2, h * 1.5f, r2);
                        float psSz = r * 0.22f;
                        for (int sx : new int[]{-1, 1}) {
                            for (int sz : new int[]{-1, 1}) {
                                float px0 = sx > 0 ? r - psSz : -r;
                                float pz0 = sz > 0 ? r - psSz : -r;
                                drawSmallBox(pose, vc, colCore, px0, 0f, pz0, px0 + psSz, h * 2.1f, pz0 + psSz);
                            }
                        }
                    }
                }
            });
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

            int outerRgb = GlobalColorModule.resolveRgb(hf.syncGlobalColor.get(), pType.outerRgb, 7);
            int coreRgb = hf.syncGlobalColor.get() ? 0xFFFFFF : pType.coreRgb;
            int colOuter = (aWave << 24) | outerRgb;
            int colCore = (aFlare << 24) | coreRgb;

            ps.pushPose();
            ps.translate(p.pos().x - cam.x, p.pos().y - cam.y, p.pos().z - cam.z);
            if (camRot != null) ps.mulPose(camRot);
            ps.mulPose(Axis.ZP.rotationDegrees(p.rotOffset() + t * 55.0f));
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.HIT_SHOCKWAVE_TEX), (pose, vc) ->
                    emitBillboardQuad(pose, vc, flareR, colCore));
            ps.popPose();

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
        List<VoxelShatterModule.VoxelPiece> pieces = vs.stepAndSnapshot(now);
        if (pieces.isEmpty()) return;

        final double cx = cam.x, cy = cam.y, cz = cam.z;
        collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
            for (VoxelShatterModule.VoxelPiece p : pieces) {
                float age = (now - p.spawnMs) / 1600.0f;
                if (age < 0f || age > 1f) continue;
                int a = Math.max(15, Math.round((1.0f - age) * 245.0f));
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
