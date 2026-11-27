package com.ye.mod.render.wings;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Authentic Hypixel PvP / Lunar / Badlion classic 2-bone Dragon Wings:
 * Fixed 1.21 PoseStack coordinate axes so the wing bones sweep UPWARD (-Y) and BACKWARD (+Z)
 * from the shoulder blades, with double-sided bat-dragon membranes unfurling behind the player!
 */
public class YePvpDragonWingsModel {
    public static final Identifier PVP_DRAGON_TEX = Identifier.fromNamespaceAndPath("ye-mod", "textures/wings/pvp_dragon.png");

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex,
                       int packedLight, int tint, float flapSpeed, long nowMs) {
        float periodMs = Math.max(250.0f, 1150.0f / Math.max(0.2f, flapSpeed));
        float phase = ((nowMs % (long) periodMs) / periodMs) * (float) (Math.PI * 2.0);

        // In 1.21 PlayerModel.body space:
        // -X = right outward, +X = left outward, -Y = UP toward head, +Y = DOWN toward waist, +Z = BEHIND back!
        // Lift bone upward (+Z rot lifts -X toward -Y), sweep backward (+Y rot sweeps -X toward +Z),
        // and angle membrane downward-back (+X rot pitches +Z membrane ~24 deg downward for classic dragon silhouette)!
        float liftZ = (float) Math.toRadians(24.0f) + (float) Math.cos(phase) * 0.14f;
        float sweepY = (float) Math.toRadians(32.0f) + (float) Math.sin(phase) * 0.38f;
        float pitchX = (float) Math.toRadians(-22.0f) - (float) Math.cos(phase) * 0.10f;

        float tipSweepY = (float) Math.sin(phase - 0.85f) * 0.34f + 0.12f;
        float tipFoldZ = -((float) Math.cos(phase - 0.65f) * 0.22f) - 0.16f;

        for (int side = 0; side < 2; side++) {
            final float signX = (side == 0) ? 1.0f : -1.0f;
            poseStack.pushPose();
            // Upper-back shoulder blade attachment (y = +2px below neck, z = +2.2px on back of torso)
            poseStack.translate(0.0f, 2.0f / 16.0f, 2.2f / 16.0f);
            poseStack.scale(signX, 1.0f, 1.0f);

            // Inner shoulder joint (-1.5px outward)
            poseStack.translate(-1.5f / 16.0f, 0.0f, 0.0f);
            poseStack.mulPose(Axis.YP.rotation(sweepY));
            poseStack.mulPose(Axis.ZP.rotation(liftZ));
            poseStack.mulPose(Axis.XP.rotation(pitchX));

            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
                // Inner wing bone along -X
                drawUvBox30(pose, vc, packedLight, tint, 0, 0, -10f, -1f, -0.5f, 10f, 2f, 2f);
                // Inner dragon membrane extending behind bone (+Z: 0.5..10.5)
                drawDoubleSidedMembrane30(pose, vc, packedLight, tint, -10f, 0f, 0.5f, 10f, 10f, 0f, 8f, 10f, 18f);
            });

            // Outer elbow joint at end of inner bone (-10px)
            poseStack.translate(-10.0f / 16.0f, 0.0f, 0.0f);
            poseStack.mulPose(Axis.YP.rotation(tipSweepY));
            poseStack.mulPose(Axis.ZP.rotation(tipFoldZ));

            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
                // Outer wingtip bone along -X
                drawUvBox30(pose, vc, packedLight, tint, 0, 5, -10f, -0.5f, -0.5f, 10f, 1f, 1f);
                // Outer dragon membrane extending behind bone (+Z: 0.5..10.5)
                drawDoubleSidedMembrane30(pose, vc, packedLight, tint, -10f, 0f, 0.5f, 10f, 10f, 0f, 18f, 10f, 28f);
            });

            poseStack.popPose();
        }
    }

    private static void drawDoubleSidedMembrane30(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                                  float ox, float oy, float oz, float dx, float dz,
                                                  float u0, float v0, float u1, float v1) {
        float x0 = ox / 16.0f, x1 = (ox + dx) / 16.0f;
        float y = oy / 16.0f;
        float z0 = oz / 16.0f, z1 = (oz + dz) / 16.0f;
        float tu0 = u0 / 30.0f, tu1 = u1 / 30.0f;
        float tv0 = v0 / 30.0f, tv1 = v1 / 30.0f;

        // Upper face (-Y normal)
        emit(pose, vc, x1, y, z0, argb, tu1, tv0, light, 0f, -1f, 0f);
        emit(pose, vc, x0, y, z0, argb, tu0, tv0, light, 0f, -1f, 0f);
        emit(pose, vc, x0, y, z1, argb, tu0, tv1, light, 0f, -1f, 0f);
        emit(pose, vc, x1, y, z1, argb, tu1, tv1, light, 0f, -1f, 0f);

        // Lower face (+Y normal) so both front & back views have proper lighting!
        emit(pose, vc, x1, y, z1, argb, tu1, tv1, light, 0f, 1f, 0f);
        emit(pose, vc, x0, y, z1, argb, tu0, tv1, light, 0f, 1f, 0f);
        emit(pose, vc, x0, y, z0, argb, tu0, tv0, light, 0f, 1f, 0f);
        emit(pose, vc, x1, y, z0, argb, tu1, tv0, light, 0f, 1f, 0f);
    }

    private static void drawUvBox30(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                    int texU, int texV,
                                    float ox, float oy, float oz,
                                    float dx, float dy, float dz) {
        float x0 = ox / 16.0f, x1 = (ox + dx) / 16.0f;
        float y0 = oy / 16.0f, y1 = (oy + dy) / 16.0f;
        float z0 = oz / 16.0f, z1 = (oz + dz) / 16.0f;

        int sx = Math.round(dx), sy = Math.round(dy), sz = Math.round(dz);
        float f4 = texU / 30f;
        float f5 = (texU + sz) / 30f;
        float f6 = (texU + sz + sx) / 30f;
        float f7 = (texU + sz + sx + sx) / 30f;
        float f8 = (texU + sz + sx + sz) / 30f;
        float f9 = (texU + sz + sx + sz + sx) / 30f;
        float f10 = texV / 30f;
        float f11 = (texV + sz) / 30f;
        float f12 = (texV + sz + sy) / 30f;

        quadUv(pose, vc, light, argb, x1, y0, z1, f6, f11, x0, y0, z1, f5, f11, x0, y0, z0, f5, f10, x1, y0, z0, f6, f10, 0, -1, 0);
        quadUv(pose, vc, light, argb, x1, y1, z0, f7, f10, x0, y1, z0, f6, f10, x0, y1, z1, f6, f11, x1, y1, z1, f7, f11, 0, 1, 0);
        quadUv(pose, vc, light, argb, x0, y0, z0, f5, f11, x0, y0, z1, f4, f11, x0, y1, z1, f4, f12, x0, y1, z0, f5, f12, -1, 0, 0);
        quadUv(pose, vc, light, argb, x1, y0, z0, f6, f11, x0, y0, z0, f5, f11, x0, y1, z0, f5, f12, x1, y1, z0, f6, f12, 0, 0, -1);
        quadUv(pose, vc, light, argb, x1, y0, z1, f8, f11, x1, y0, z0, f6, f11, x1, y1, z0, f6, f12, x1, y1, z1, f8, f12, 1, 0, 0);
        quadUv(pose, vc, light, argb, x0, y0, z1, f8, f11, x1, y0, z1, f9, f11, x1, y1, z1, f9, f12, x0, y1, z1, f8, f12, 0, 0, 1);
    }

    private static void quadUv(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                               float x1, float y1, float z1, float u1, float v1,
                               float x2, float y2, float z2, float u2, float v2,
                               float x3, float y3, float z3, float u3, float v3,
                               float x4, float y4, float z4, float u4, float v4,
                               float nx, float ny, float nz) {
        emit(pose, vc, x1, y1, z1, argb, u1, v1, light, nx, ny, nz);
        emit(pose, vc, x2, y2, z2, argb, u2, v2, light, nx, ny, nz);
        emit(pose, vc, x3, y3, z3, argb, u3, v3, light, nx, ny, nz);
        emit(pose, vc, x4, y4, z4, argb, u4, v4, light, nx, ny, nz);
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc,
                             float x, float y, float z, int argb,
                             float u, float v, int light,
                             float nx, float ny, float nz) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
