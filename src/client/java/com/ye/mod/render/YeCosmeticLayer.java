package com.ye.mod.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Custom RenderLayer attached to AvatarRenderer for animated 3D Dragon Wings
 * and client-side Elytra Flight Model mounts (Mini Dragon, Phantom, Flying Sword).
 */
public class YeCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/white_pixel");
    private static boolean whiteRegistered = false;

    public YeCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static Identifier getWhiteTex() {
        if (!whiteRegistered) {
            whiteRegistered = true;
            NativeImage img = new NativeImage(1, 1, true);
            img.setPixel(0, 0, 0xFFFFFFFF);
            Minecraft.getInstance().getTextureManager().register(WHITE_TEX, new DynamicTexture(() -> "ye_white_pixel", img));
        }
        return WHITE_TEX;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();

        DragonWingsModule wings = ModuleManager.get(DragonWingsModule.class);
        if (wings != null && wings.isEnabled() && (!wings.onlySelf.get() || isSelf)) {
            if (!wings.hideWithElytra.get() || !state.isFallFlying) {
                renderDragonWings(poseStack, collector, packedLight, state, wings);
            }
        }

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight != null && flight.isEnabled() && state.isFallFlying && (!flight.onlySelf.get() || isSelf)) {
            renderFlightMount(poseStack, collector, packedLight, state, flight);
        }
    }

    private void renderDragonWings(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   AvatarRenderState state, DragonWingsModule wings) {
        long now = System.currentTimeMillis();
        int boneColor = wings.getBoneColor(now);
        int membraneColor = wings.getMembraneColor(now);
        float sc = wings.scale.get().floatValue();
        float speed = wings.flapSpeed.get().floatValue();

        float t = (now % 60000L) / 1000.0f * speed * (state.isFallFlying ? 7.5f : (state.walkAnimationSpeed > 0.1f ? 4.5f : 2.2f));
        float flap = (float) Math.sin(t) * (state.isFallFlying ? 28.0f : 12.0f) + 22.0f;
        float outerBend = (float) Math.cos(t - 0.5f) * 14.0f + 22.0f;

        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            // Attach behind upper back
            poseStack.translate(side * 0.08f, state.isCrouching ? 0.30f : 0.18f, 0.14f);
            poseStack.scale(sc, sc, sc);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * flap));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -14.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(8.0f));

            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                // Inner wing bone
                float ix = side * 0.48f;
                drawBox(pose, vc, packedLight, boneColor,
                        Math.min(0, ix), -0.04f, -0.025f,
                        Math.max(0, ix), 0.04f, 0.025f);
                // Inner membrane quad
                drawDoubleQuad(pose, vc, packedLight, membraneColor,
                        0.0f, 0.02f, 0.0f,
                        ix, 0.02f, 0.0f,
                        ix * 0.92f, 0.56f, 0.03f,
                        side * 0.05f, 0.44f, 0.02f);
            });

            // Outer wing segment
            poseStack.translate(side * 0.48f, 0.0f, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * outerBend));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0f));

            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                float ox = side * 0.58f;
                drawBox(pose, vc, packedLight, boneColor,
                        Math.min(0, ox), -0.03f, -0.02f,
                        Math.max(0, ox), 0.03f, 0.02f);
                // Wingtip claw
                drawBox(pose, vc, packedLight, boneColor,
                        Math.min(ox, ox + side * 0.10f), -0.06f, -0.015f,
                        Math.max(ox, ox + side * 0.10f), 0.02f, 0.015f);
                // Outer membrane
                drawDoubleQuad(pose, vc, packedLight, membraneColor,
                        0.0f, 0.01f, 0.0f,
                        ox, 0.01f, 0.0f,
                        ox * 0.85f, 0.48f, 0.02f,
                        -side * 0.04f, 0.54f, 0.03f);
            });

            poseStack.popPose();
        }
    }

    private void renderFlightMount(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   AvatarRenderState state, FlightModelModule flight) {
        FlightModelModule.FlightPreset preset = flight.preset.get();
        if (preset == FlightModelModule.FlightPreset.UPRIGHT || preset == FlightModelModule.FlightPreset.SUPERMAN) {
            return;
        }

        float sc = flight.modelScale.get().floatValue();
        long now = System.currentTimeMillis();
        float flap = (float) Math.sin((now % 60000L) / 110.0) * 26.0f;

        if (preset == FlightModelModule.FlightPreset.SWORD_SURF) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.48f, 0.0f);
            poseStack.scale(sc, sc, sc);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                // Glowing cyan-gold flying sword beneath player's feet
                drawBox(pose, vc, 0xF000F0, 0xEE38BDF8, -0.10f, -0.03f, -0.95f, 0.10f, 0.03f, 0.55f);
                drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, -0.28f, -0.045f, 0.55f, 0.28f, 0.045f, 0.65f);
                drawBox(pose, vc, 0xF000F0, 0xFF7C2D12, -0.05f, -0.035f, 0.65f, 0.05f, 0.035f, 0.95f);
            });
            poseStack.popPose();
            return;
        }

        if (preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.45f, 0.0f);
            poseStack.scale(sc, sc, sc);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                // Phantom body + glowing green eyes + tail
                drawBox(pose, vc, packedLight, 0xFF1E293B, -0.22f, -0.08f, -0.45f, 0.22f, 0.12f, 0.45f);
                drawBox(pose, vc, packedLight, 0xFF334155, -0.18f, -0.06f, -0.72f, 0.18f, 0.10f, -0.45f);
                drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, -0.16f, -0.02f, -0.73f, -0.06f, 0.04f, -0.70f);
                drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, 0.06f, -0.02f, -0.73f, 0.16f, 0.04f, -0.70f);
                drawBox(pose, vc, packedLight, 0xFF0F172A, -0.08f, -0.03f, 0.45f, 0.08f, 0.05f, 1.05f);
            });
            for (int side : new int[]{-1, 1}) {
                poseStack.pushPose();
                poseStack.translate(side * 0.22f, 0.02f, -0.1f);
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * flap));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) ->
                        drawBox(pose, vc, packedLight, 0xFF283548,
                                Math.min(0, side * 0.85f), -0.02f, -0.28f,
                                Math.max(0, side * 0.85f), 0.02f, 0.32f));
                poseStack.popPose();
            }
            poseStack.popPose();
            return;
        }

        // Default: MINI_DRAGON
        poseStack.pushPose();
        poseStack.translate(0.0f, 1.48f, 0.0f);
        poseStack.scale(sc, sc, sc);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            // Dragon body (obsidian) + spine ridge + neck/head + glowing purple eyes + tail
            drawBox(pose, vc, packedLight, 0xFF14131A, -0.24f, -0.14f, -0.50f, 0.24f, 0.16f, 0.55f);
            drawBox(pose, vc, packedLight, 0xFF2D2B3B, -0.05f, -0.22f, -0.40f, 0.05f, -0.14f, 0.45f);
            // Neck & head
            drawBox(pose, vc, packedLight, 0xFF181622, -0.11f, -0.16f, -0.92f, 0.11f, 0.04f, -0.50f);
            drawBox(pose, vc, packedLight, 0xFF1E1B2E, -0.18f, -0.24f, -1.25f, 0.18f, 0.06f, -0.88f);
            // Snout + glowing ender purple eyes
            drawBox(pose, vc, packedLight, 0xFF14131A, -0.11f, -0.14f, -1.42f, 0.11f, 0.04f, -1.25f);
            drawBox(pose, vc, 0xF000F0, 0xFFC084FC, -0.185f, -0.17f, -1.16f, -0.10f, -0.10f, -1.04f);
            drawBox(pose, vc, 0xF000F0, 0xFFC084FC, 0.10f, -0.17f, -1.16f, 0.185f, -0.10f, -1.04f);
            // Tail
            drawBox(pose, vc, packedLight, 0xFF14131A, -0.10f, -0.06f, 0.55f, 0.10f, 0.08f, 1.35f);
        });
        // Flapping Ender Dragon wings
        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            poseStack.translate(side * 0.22f, -0.08f, -0.18f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * flap));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                drawBox(pose, vc, packedLight, 0xFF1A1826,
                        Math.min(0, side * 1.15f), -0.03f, -0.06f,
                        Math.max(0, side * 1.15f), 0.03f, 0.06f);
                drawDoubleQuad(pose, vc, packedLight, 0xCC4C1D95,
                        0.0f, 0.0f, 0.0f,
                        side * 1.15f, 0.0f, 0.0f,
                        side * 1.05f, 0.02f, 0.62f,
                        0.0f, 0.02f, 0.48f);
            });
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawDoubleQuad(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3,
                                       float x4, float y4, float z4) {
        emitVertex(pose, vc, x1, y1, z1, argb, 0f, 0f, light, 0f, 1f, 0f);
        emitVertex(pose, vc, x2, y2, z2, argb, 1f, 0f, light, 0f, 1f, 0f);
        emitVertex(pose, vc, x3, y3, z3, argb, 1f, 1f, light, 0f, 1f, 0f);
        emitVertex(pose, vc, x4, y4, z4, argb, 0f, 1f, light, 0f, 1f, 0f);

        emitVertex(pose, vc, x4, y4, z4, argb, 0f, 1f, light, 0f, -1f, 0f);
        emitVertex(pose, vc, x3, y3, z3, argb, 1f, 1f, light, 0f, -1f, 0f);
        emitVertex(pose, vc, x2, y2, z2, argb, 1f, 0f, light, 0f, -1f, 0f);
        emitVertex(pose, vc, x1, y1, z1, argb, 0f, 0f, light, 0f, -1f, 0f);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                float x0, float y0, float z0, float x1, float y1, float z1) {
        // Top (-Y in player local space) & Bottom (+Y)
        quad(pose, vc, light, argb, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0f, -1f, 0f);
        quad(pose, vc, light, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
        // Front (-Z) & Back (+Z)
        quad(pose, vc, light, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
        quad(pose, vc, light, argb, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0f, 0f, 1f);
        // Left (-X) & Right (+X)
        quad(pose, vc, light, argb, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1f, 0f, 0f);
        quad(pose, vc, light, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float nx, float ny, float nz) {
        emitVertex(pose, vc, x1, y1, z1, argb, 0f, 0f, light, nx, ny, nz);
        emitVertex(pose, vc, x2, y2, z2, argb, 1f, 0f, light, nx, ny, nz);
        emitVertex(pose, vc, x3, y3, z3, argb, 1f, 1f, light, nx, ny, nz);
        emitVertex(pose, vc, x4, y4, z4, argb, 0f, 1f, light, nx, ny, nz);
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer vc,
                                   float x, float y, float z, int argb,
                                   float u, float v, int light,
                                   float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
