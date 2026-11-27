package com.ye.mod.render.wings;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Authentic Hypixel PvP / Lunar / Badlion classic 2-bone Dragon Wings
 * ported faithfully from Canelex's DragonWingsMod (RenderWings.java + 300x300 wings.png).
 */
public class YePvpDragonWingsModel {
    public static final Identifier PVP_DRAGON_TEX = Identifier.fromNamespaceAndPath("ye-mod", "textures/wings/pvp_dragon.png");

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex,
                       int packedLight, int tint, float flapSpeed, long nowMs) {
        float periodMs = Math.max(250.0f, 1000.0f / Math.max(0.2f, flapSpeed));
        float f11 = ((nowMs % (long) periodMs) / periodMs) * (float) (Math.PI * 2.0);

        float wingRotX = (float) Math.toRadians(-80.0f) - (float) Math.cos(f11) * 0.2f;
        float wingRotY = (float) Math.toRadians(20.0f) + (float) Math.sin(f11) * 0.4f;
        float wingRotZ = (float) Math.toRadians(20.0f);
        float tipRotZ = -((float) (Math.sin(f11 + 2.0f) + 0.5)) * 0.75f;

        // Render left (mirrorX = false -> -X side) and right (mirrorX = true -> +X side)
        for (int side = 0; side < 2; side++) {
            final float signX = (side == 0) ? 1.0f : -1.0f;
            poseStack.pushPose();
            // Shoulder attachment on upper back
            poseStack.translate(0.0f, 0.125f, 0.125f);
            poseStack.scale(signX, 1.0f, 1.0f);

            // Inner wing pivot (-2px)
            poseStack.translate(-2.0f / 16.0f, 0.0f, 0.0f);
            poseStack.mulPose(Axis.ZP.rotation(wingRotZ));
            poseStack.mulPose(Axis.YP.rotation(wingRotY));
            poseStack.mulPose(Axis.XP.rotation(wingRotX));

            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
                // Inner bone: (-10, -1, -1) size (10, 2, 2), texOffs(0, 0) in 30x30 UV space
                drawUvBox30(pose, vc, packedLight, tint, 0, 0, -10f, -1f, -1f, 10f, 2f, 2f);
                // Inner membrane skin: (-10, 0, 0.5) size (10, 0, 10), top UV (0..10, 8..18), bottom UV (10..20, 8..18)
                drawMembrane30(pose, vc, packedLight, tint, -10f, 0f, 0.5f, 10f, 10f, 0f, 8f, 10f, 18f);
            });

            // Outer wingtip pivot (-10px from inner wing)
            poseStack.translate(-10.0f / 16.0f, 0.0f, 0.0f);
            poseStack.mulPose(Axis.ZP.rotation(tipRotZ));

            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
                // Outer bone: (-10, -0.5, -0.5) size (10, 1, 1), texOffs(0, 5) in 30x30 UV space
                drawUvBox30(pose, vc, packedLight, tint, 0, 5, -10f, -0.5f, -0.5f, 10f, 1f, 1f);
                // Outer membrane skin: (-10, 0, 0.5) size (10, 0, 10), UV (0..10, 18..28)
                drawMembrane30(pose, vc, packedLight, tint, -10f, 0f, 0.5f, 10f, 10f, 0f, 18f, 10f, 28f);
            });

            poseStack.popPose();
        }
    }

    private static void drawMembrane30(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                       float ox, float oy, float oz, float dx, float dz,
                                       float u0, float v0, float u1, float v1) {
        float x0 = ox / 16.0f, x1 = (ox + dx) / 16.0f;
        float y = oy / 16.0f;
        float z0 = oz / 16.0f, z1 = (oz + dz) / 16.0f;
        float tu0 = u0 / 30.0f, tu1 = u1 / 30.0f;
        float tv0 = v0 / 30.0f, tv1 = v1 / 30.0f;

        emit(pose, vc, x1, y, z0, argb, tu1, tv0, light, 0f, -1f, 0f);
        emit(pose, vc, x0, y, z0, argb, tu0, tv0, light, 0f, -1f, 0f);
        emit(pose, vc, x0, y, z1, argb, tu0, tv1, light, 0f, -1f, 0f);
        emit(pose, vc, x1, y, z1, argb, tu1, tv1, light, 0f, -1f, 0f);
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

        // Top (-Y in entity space)
        quadUv(pose, vc, light, argb, x1, y0, z1, f6, f11, x0, y0, z1, f5, f11, x0, y0, z0, f5, f10, x1, y0, z0, f6, f10, 0, -1, 0);
        // Bottom (+Y)
        quadUv(pose, vc, light, argb, x1, y1, z0, f7, f10, x0, y1, z0, f6, f10, x0, y1, z1, f6, f11, x1, y1, z1, f7, f11, 0, 1, 0);
        // West (-X)
        quadUv(pose, vc, light, argb, x0, y0, z0, f5, f11, x0, y0, z1, f4, f11, x0, y1, z1, f4, f12, x0, y1, z0, f5, f12, -1, 0, 0);
        // North (-Z)
        quadUv(pose, vc, light, argb, x1, y0, z0, f6, f11, x0, y0, z0, f5, f11, x0, y1, z0, f5, f12, x1, y1, z0, f6, f12, 0, 0, -1);
        // East (+X)
        quadUv(pose, vc, light, argb, x1, y0, z1, f8, f11, x1, y0, z0, f6, f11, x1, y1, z0, f6, f12, x1, y1, z1, f8, f12, 1, 0, 0);
        // South (+Z)
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
