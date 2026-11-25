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
 * High-detail textured 3D RenderLayer for thick backward-swept Wings (4 styles)
 * and articulated textured Flight Models (Ender Dragon, Phantom, Spirit Sword).
 */
public class YeCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WING_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_wing");
    private static final Identifier SCALE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_scale");
    private static final Identifier SWORD_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_sword");
    private static boolean texturesRegistered = false;

    public YeCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static void ensureTextures() {
        if (texturesRegistered) return;
        texturesRegistered = true;

        // 1. Wing membrane texture (64x64 with radial veins, gradient shading & edge trim)
        NativeImage wingImg = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float u = x / 63.0f;
                float v = y / 63.0f;
                boolean vein = (Math.abs((x * 3 - y * 2)) % 19 < 2) || (y < 4) || (x < 3);
                float shade = 0.72f + 0.28f * (1.0f - v * 0.65f);
                if (vein) shade = Math.min(1.0f, shade + 0.22f);
                int c = Math.max(90, Math.min(255, Math.round(shade * 255)));
                int a = (v > 0.90f && ((x / 4) % 2 == 0)) ? 175 : 235;
                wingImg.setPixel(x, y, (a << 24) | (c << 16) | (c << 8) | c);
            }
        }
        Minecraft.getInstance().getTextureManager().register(WING_TEX, new DynamicTexture(() -> "ye_wing_tex", wingImg));

        // 2. Dragon scale texture (64x64 with interlocking scales & subtle highlights)
        NativeImage scaleImg = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int sx = (x + ((y / 8) % 2) * 4) % 8;
                int sy = y % 8;
                boolean ridge = (sx == 0 || sy == 0 || sx + sy == 7);
                int v = ridge ? 235 : (175 + ((x * 7 + y * 13) % 30));
                scaleImg.setPixel(x, y, (0xFF << 24) | (v << 16) | (v << 8) | v);
            }
        }
        Minecraft.getInstance().getTextureManager().register(SCALE_TEX, new DynamicTexture(() -> "ye_scale_tex", scaleImg));

        // 3. Sword rune texture (64x64 metallic fuller & glowing center runes)
        NativeImage swordImg = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int dist = Math.abs(x - 32);
                boolean rune = dist < 5 && ((y % 10) < 4);
                int v = rune ? 255 : (dist < 10 ? 225 : 180);
                swordImg.setPixel(x, y, (0xFF << 24) | (v << 16) | (v << 8) | v);
            }
        }
        Minecraft.getInstance().getTextureManager().register(SWORD_TEX, new DynamicTexture(() -> "ye_sword_tex", swordImg));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        ensureTextures();

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();

        DragonWingsModule wings = ModuleManager.get(DragonWingsModule.class);
        if (wings != null && wings.isEnabled() && (!wings.onlySelf.get() || isSelf)) {
            if (!wings.hideWithElytra.get() || !state.isFallFlying) {
                renderStyledWings(poseStack, collector, packedLight, state, wings);
            }
        }

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight != null && flight.isEnabled() && state.isFallFlying && (!flight.onlySelf.get() || isSelf)) {
            renderFlightMount(poseStack, collector, packedLight, state, flight);
        }
    }

    private void renderStyledWings(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   AvatarRenderState state, DragonWingsModule wings) {
        long now = System.currentTimeMillis();
        int boneCol = wings.getBoneColor(now);
        int boneDark = darken(boneCol, 0.72f);
        int memCol = wings.getMembraneColor(now);
        int memBright = brighten(memCol, 1.22f);
        float sc = wings.scale.get().floatValue();
        float speed = wings.flapSpeed.get().floatValue();
        DragonWingsModule.WingStyle style = wings.wingStyle.get();

        float rate = state.isFallFlying ? 7.2f : (state.walkAnimationSpeed > 0.1f ? 4.4f : 2.1f);
        float t = (now % 60000L) / 1000.0f * speed * rate;
        // Swept far backward (+Z) so it NEVER clips with player arms!
        float flap = (float) Math.sin(t) * (state.isFallFlying ? 20.0f : 8.5f) + 52.0f;
        float outerFlap = (float) Math.cos(t - 0.55f) * 12.0f + 24.0f;

        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            poseStack.translate(side * 0.06f, state.isCrouching ? 0.28f : 0.15f, 0.19f);
            poseStack.scale(sc, sc, sc);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * flap));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -16.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(18.0f));

            if (style == DragonWingsModule.WingStyle.SERAPH) {
                renderSeraphWing(poseStack, collector, packedLight, side, boneCol, memCol, memBright, outerFlap);
            } else if (style == DragonWingsModule.WingStyle.CYBER_MECHA) {
                renderCyberWing(poseStack, collector, side, boneCol, memBright, outerFlap);
            } else {
                boolean demon = (style == DragonWingsModule.WingStyle.DEMON);
                renderDragonOrDemonWing(poseStack, collector, packedLight, side, boneCol, boneDark, memCol, memBright, outerFlap, demon);
            }
            poseStack.popPose();
        }
    }

    private void renderDragonOrDemonWing(PoseStack poseStack, SubmitNodeCollector collector, int light, int side,
                                         int boneCol, int boneDark, int memCol, int memBright, float outerFlap, boolean demon) {
        // Thick 3D shoulder + upper arm bone + inner textured membrane
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            drawBox(pose, vc, light, boneDark, -0.05f, -0.055f, -0.05f, 0.05f, 0.055f, 0.05f);
            drawThickBone(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.46f, -0.15f, 0.05f, 0.048f);
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) ->
                drawThickMembrane(pose, vc, light, memCol, 0.016f,
                        0.0f, 0.01f, 0.0f,
                        side * 0.46f, -0.14f, 0.05f,
                        side * 0.40f, 0.58f, 0.11f,
                        side * 0.03f, 0.48f, 0.04f));

        poseStack.translate(side * 0.46f, -0.15f, 0.05f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * outerFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 12.0f));

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            drawBox(pose, vc, light, boneDark, -0.055f, -0.055f, -0.055f, 0.055f, 0.055f, 0.055f);
            drawThickBone(pose, vc, light, memBright, 0f, -0.04f, -0.01f, side * 0.07f, -0.18f, -0.08f, 0.025f);
            float[][] ribs = demon
                    ? new float[][]{{0.82f, -0.18f, 0.06f}, {0.68f, 0.24f, 0.10f}, {0.44f, 0.56f, 0.12f}, {0.16f, 0.70f, 0.10f}}
                    : new float[][]{{0.88f, -0.10f, 0.05f}, {0.74f, 0.26f, 0.09f}, {0.50f, 0.54f, 0.11f}, {0.18f, 0.68f, 0.09f}};
            for (int i = 0; i < ribs.length; i++) {
                float rx = side * ribs[i][0];
                float ry = ribs[i][1];
                float rz = ribs[i][2];
                drawThickBone(pose, vc, light, (i == 0) ? boneCol : boneDark, 0f, 0f, 0f, rx, ry, rz, (i == 0) ? 0.036f : 0.022f);
                if (i == 0) {
                    drawThickBone(pose, vc, light, memBright, rx, ry, rz, rx + side * 0.11f, ry - 0.07f, rz - 0.02f, 0.020f);
                }
            }
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
            float[][] ribs = demon
                    ? new float[][]{{0.82f, -0.18f, 0.06f}, {0.68f, 0.24f, 0.10f}, {0.44f, 0.56f, 0.12f}, {0.16f, 0.70f, 0.10f}}
                    : new float[][]{{0.88f, -0.10f, 0.05f}, {0.74f, 0.26f, 0.09f}, {0.50f, 0.54f, 0.11f}, {0.18f, 0.68f, 0.09f}};
            for (int i = 0; i < ribs.length - 1; i++) {
                float rx = side * ribs[i][0];
                float ry = ribs[i][1];
                float rz = ribs[i][2];
                float nx = side * ribs[i + 1][0];
                float ny = ribs[i + 1][1];
                float nz = ribs[i + 1][2];
                drawThickMembrane(pose, vc, light, (i % 2 == 0) ? memCol : darken(memCol, 0.88f), 0.012f,
                        0f, 0f, 0f, rx, ry, rz,
                        (rx + nx) * (demon ? 0.43f : 0.48f), (ry + ny) * (demon ? 0.43f : 0.48f), (rz + nz) * 0.5f,
                        nx, ny, nz);
            }
        });
    }

    private void renderSeraphWing(PoseStack poseStack, SubmitNodeCollector collector, int light, int side,
                                  int boneCol, int memCol, int memBright, float outerFlap) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) ->
                drawThickBone(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.44f, -0.14f, 0.04f, 0.042f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
            for (int i = 0; i < 5; i++) {
                float u = i / 4.0f;
                float fx = side * (0.08f + u * 0.34f);
                float fy = -0.10f * u;
                float len = 0.42f + u * 0.18f;
                drawThickMembrane(pose, vc, light, (i % 2 == 0) ? memBright : memCol, 0.014f,
                        fx - side * 0.04f, fy, 0.012f * i,
                        fx + side * 0.06f, fy - 0.02f, 0.012f * i,
                        fx + side * 0.03f, fy + len, 0.06f + 0.012f * i,
                        fx - side * 0.06f, fy + len * 0.92f, 0.05f + 0.012f * i);
            }
        });

        poseStack.translate(side * 0.44f, -0.14f, 0.04f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * outerFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) ->
                drawThickBone(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.58f, -0.06f, 0.04f, 0.032f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
            for (int i = 0; i < 6; i++) {
                float u = i / 5.0f;
                float fx = side * (0.06f + u * 0.56f);
                float fy = -0.06f * u;
                float len = 0.64f - u * 0.24f;
                drawThickMembrane(pose, vc, light, (i % 2 == 0) ? memBright : memCol, 0.012f,
                        fx - side * 0.045f, fy, 0.012f * i,
                        fx + side * 0.065f, fy - 0.02f, 0.012f * i,
                        fx + side * 0.12f, fy + len, 0.08f + 0.012f * i,
                        fx - side * 0.02f, fy + len * 0.94f, 0.06f + 0.012f * i);
            }
        });
    }

    private void renderCyberWing(PoseStack poseStack, SubmitNodeCollector collector, int side,
                                 int frameCol, int glowCol, float outerFlap) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            drawThickBone(pose, vc, 0xF000F0, frameCol, 0f, 0f, 0f, side * 0.42f, -0.16f, 0.05f, 0.048f);
            drawBox(pose, vc, 0xF000F0, glowCol, side * 0.12f, -0.11f, -0.02f, side * 0.24f, 0.03f, 0.09f);
        });
        poseStack.translate(side * 0.42f, -0.16f, 0.05f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * outerFlap));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SWORD_TEX), (pose, vc) -> {
            for (int i = 0; i < 4; i++) {
                float oy = i * 0.15f;
                float ox = side * (0.72f - i * 0.13f);
                drawThickBone(pose, vc, 0xF000F0, frameCol, side * 0.04f, oy, 0.01f, side * 0.22f, oy + 0.02f, 0.03f, 0.026f);
                drawThickBone(pose, vc, 0xF000F0, glowCol, side * 0.18f, oy + 0.01f, 0.02f, ox, oy + 0.14f, 0.06f, 0.024f);
            }
        });
    }

    private void renderFlightMount(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   AvatarRenderState state, FlightModelModule flight) {
        FlightModelModule.FlightPreset preset = flight.preset.get();
        if (preset == FlightModelModule.FlightPreset.UPRIGHT || preset == FlightModelModule.FlightPreset.SUPERMAN) {
            return;
        }

        float sc = flight.modelScale.get().floatValue();
        long now = System.currentTimeMillis();
        float flap = (float) Math.sin((now % 60000L) / 115.0) * 28.0f;

        if (preset == FlightModelModule.FlightPreset.SWORD_SURF) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.48f, 0.0f);
            poseStack.scale(sc, sc, sc);
            float spin = ((now % 4000L) / 4000.0f) * 360.0f;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SWORD_TEX), (pose, vc) -> {
                drawBox(pose, vc, 0xF000F0, 0xFFE0F2FE, -0.055f, -0.03f, -1.08f, 0.055f, 0.03f, 0.55f);
                drawBox(pose, vc, 0xF000F0, 0xCC38BDF8, -0.14f, -0.015f, -0.98f, 0.14f, 0.015f, 0.55f);
                drawThickMembrane(pose, vc, 0xF000F0, 0xFFBAE6FD, 0.014f,
                        -0.14f, 0f, -0.98f, 0.14f, 0f, -0.98f, 0f, 0f, -1.36f, 0f, 0f, -1.36f);
                drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, -0.36f, -0.045f, 0.53f, 0.36f, 0.045f, 0.65f);
                drawThickBone(pose, vc, 0xF000F0, 0xFFF59E0B, -0.34f, 0f, 0.58f, -0.48f, -0.07f, 0.40f, 0.03f);
                drawThickBone(pose, vc, 0xF000F0, 0xFFF59E0B, 0.34f, 0f, 0.58f, 0.48f, -0.07f, 0.40f, 0.03f);
                drawBox(pose, vc, packedLight, 0xFF7C2D12, -0.048f, -0.035f, 0.65f, 0.048f, 0.035f, 1.00f);
                drawBox(pose, vc, 0xF000F0, 0xFFA855F7, -0.075f, -0.055f, 1.00f, 0.075f, 0.055f, 1.12f);
            });
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SWORD_TEX), (pose, vc) -> {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3.0;
                    float rx = (float) Math.cos(a) * 0.48f;
                    float rz = (float) Math.sin(a) * 0.48f;
                    drawBox(pose, vc, 0xF000F0, 0xAA38BDF8, rx - 0.035f, 0.06f, rz - 0.035f, rx + 0.035f, 0.09f, rz + 0.035f);
                }
            });
            poseStack.popPose();
            return;
        }

        if (preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.38f, 0.0f);
            poseStack.scale(sc, sc, sc);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
                // Detailed segmented Phantom body + rib stripes + head + glowing green eyes + 3-segment tail
                drawBox(pose, vc, packedLight, 0xFF1E293B, -0.26f, -0.11f, -0.52f, 0.26f, 0.14f, 0.48f);
                drawBox(pose, vc, packedLight, 0xFFCBD5E1, -0.22f, -0.12f, -0.42f, 0.22f, -0.09f, 0.38f);
                drawBox(pose, vc, packedLight, 0xFF334155, -0.21f, -0.09f, -0.84f, 0.21f, 0.12f, -0.52f);
                drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, -0.18f, -0.03f, -0.85f, -0.06f, 0.05f, -0.80f);
                drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, 0.06f, -0.03f, -0.85f, 0.18f, 0.05f, -0.80f);
                for (int tSeg = 0; tSeg < 3; tSeg++) {
                    float tz0 = 0.48f + tSeg * 0.26f;
                    float hw = 0.12f - tSeg * 0.03f;
                    drawBox(pose, vc, packedLight, 0xFF0F172A, -hw, -0.04f, tz0, hw, 0.06f, tz0 + 0.26f);
                }
            });
            for (int side : new int[]{-1, 1}) {
                poseStack.pushPose();
                poseStack.translate(side * 0.25f, 0.02f, -0.1f);
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * flap));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
                    drawThickBone(pose, vc, packedLight, 0xFF334155, 0f, 0f, 0f, side * 1.05f, -0.02f, 0.04f, 0.03f);
                    drawThickMembrane(pose, vc, packedLight, 0xDD1E293B, 0.014f,
                            0f, 0f, -0.25f, side * 1.05f, 0f, 0.04f, side * 0.90f, 0.02f, 0.44f, 0f, 0.01f, 0.36f);
                });
                poseStack.popPose();
            }
            poseStack.popPose();
            return;
        }

        // Detailed Textured Large Ender Dragon Mount
        poseStack.pushPose();
        poseStack.translate(0.0f, 1.38f, 0.0f);
        poseStack.scale(sc, sc, sc);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            drawBox(pose, vc, packedLight, 0xFF14131B, -0.30f, -0.18f, -0.62f, 0.30f, 0.22f, 0.66f);
            drawBox(pose, vc, packedLight, 0xFF2E2A3E, -0.06f, -0.27f, -0.52f, 0.06f, -0.18f, 0.56f);
            drawBox(pose, vc, packedLight, 0xFF181622, -0.36f, 0.04f, -0.45f, -0.20f, 0.32f, -0.20f);
            drawBox(pose, vc, packedLight, 0xFF181622, 0.20f, 0.04f, -0.45f, 0.36f, 0.32f, -0.20f);
            drawBox(pose, vc, packedLight, 0xFF181622, -0.36f, 0.04f, 0.30f, -0.20f, 0.34f, 0.56f);
            drawBox(pose, vc, packedLight, 0xFF181622, 0.20f, 0.04f, 0.30f, 0.36f, 0.34f, 0.56f);

            for (int n = 0; n < 3; n++) {
                float nz0 = -0.62f - n * 0.24f;
                float nz1 = nz0 - 0.24f;
                float ny = -0.08f - n * 0.06f;
                drawBox(pose, vc, packedLight, 0xFF161420, -0.13f, ny - 0.12f, nz1, 0.13f, ny + 0.12f, nz0);
                drawBox(pose, vc, packedLight, 0xFF343048, -0.04f, ny - 0.19f, nz1 + 0.04f, 0.04f, ny - 0.12f, nz0 - 0.04f);
            }

            drawBox(pose, vc, packedLight, 0xFF1A1826, -0.21f, -0.42f, -1.68f, 0.21f, -0.10f, -1.32f);
            drawBox(pose, vc, packedLight, 0xFF121118, -0.13f, -0.30f, -1.94f, 0.13f, -0.14f, -1.68f);
            drawBox(pose, vc, packedLight, 0xFF1E1B2E, -0.11f, -0.14f, -1.90f, 0.11f, -0.07f, -1.66f);
            drawThickBone(pose, vc, packedLight, 0xFF475569, -0.14f, -0.40f, -1.42f, -0.22f, -0.56f, -1.16f, 0.042f);
            drawThickBone(pose, vc, packedLight, 0xFF475569, 0.14f, -0.40f, -1.42f, 0.22f, -0.56f, -1.16f, 0.042f);
            drawBox(pose, vc, 0xF000F0, 0xFFC084FC, -0.215f, -0.34f, -1.60f, -0.12f, -0.26f, -1.46f);
            drawBox(pose, vc, 0xF000F0, 0xFFC084FC, 0.12f, -0.34f, -1.60f, 0.215f, -0.26f, -1.46f);

            for (int tSeg = 0; tSeg < 4; tSeg++) {
                float tz0 = 0.66f + tSeg * 0.28f;
                float tz1 = tz0 + 0.28f;
                float halfW = 0.13f - tSeg * 0.02f;
                float sway = (float) Math.sin((now % 60000L) / 220.0 + tSeg * 0.7) * 0.06f;
                drawBox(pose, vc, packedLight, 0xFF14131A, -halfW + sway, -0.08f, tz0, halfW + sway, 0.10f, tz1);
                drawBox(pose, vc, packedLight, 0xFF2B2838, -0.03f + sway, -0.14f, tz0 + 0.05f, 0.03f + sway, -0.08f, tz1 - 0.05f);
            }
        });

        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            poseStack.translate(side * 0.28f, -0.12f, -0.22f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * flap));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
                drawThickBone(pose, vc, packedLight, 0xFF1E1B2E, 0f, 0f, 0f, side * 1.45f, -0.08f, 0.08f, 0.050f);
                drawThickBone(pose, vc, packedLight, 0xFF2B2838, side * 0.65f, -0.04f, 0.04f, side * 0.80f, 0.02f, 0.72f, 0.028f);
            });
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) ->
                    drawThickMembrane(pose, vc, packedLight, 0xD84C1D95, 0.015f,
                            0f, 0f, 0f, side * 1.45f, -0.08f, 0.08f, side * 1.32f, 0.04f, 0.85f, 0f, 0.02f, 0.68f));
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawThickBone(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                      float x0, float y0, float z0, float x1, float y1, float z1, float r) {
        drawDoubleQuad(pose, vc, light, argb, x0 - r, y0, z0, x0 + r, y0, z0, x1 + r, y1, z1, x1 - r, y1, z1);
        drawDoubleQuad(pose, vc, light, argb, x0, y0 - r, z0, x0, y0 + r, z0, x1, y1 + r, z1, x1, y1 - r, z1);
        drawDoubleQuad(pose, vc, light, argb, x0, y0, z0 - r, x0, y0, z0 + r, x1, y1, z1 + r, x1, y1, z1 - r);
    }

    private static void drawThickMembrane(PoseStack.Pose pose, VertexConsumer vc, int light, int argb, float halfThick,
                                          float x1, float y1, float z1,
                                          float x2, float y2, float z2,
                                          float x3, float y3, float z3,
                                          float x4, float y4, float z4) {
        drawDoubleQuad(pose, vc, light, argb,
                x1, y1, z1 - halfThick, x2, y2, z2 - halfThick, x3, y3, z3 - halfThick, x4, y4, z4 - halfThick);
        drawDoubleQuad(pose, vc, light, darken(argb, 0.85f),
                x1, y1, z1 + halfThick, x2, y2, z2 + halfThick, x3, y3, z3 + halfThick, x4, y4, z4 + halfThick);
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
        quad(pose, vc, light, argb, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0f, -1f, 0f);
        quad(pose, vc, light, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
        quad(pose, vc, light, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
        quad(pose, vc, light, argb, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0f, 0f, 1f);
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

    private static int darken(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
