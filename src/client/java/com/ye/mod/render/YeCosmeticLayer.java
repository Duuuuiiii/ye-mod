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
 * Custom RenderLayer for intricate multi-jointed 3D wings (4 styles, swept back to avoid arm clipping)
 * and detailed client-side Flight Model mounts (Ender Dragon, Phantom, Xianxia Spirit Sword).
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
        int memBright = brighten(memCol, 1.25f);
        float sc = wings.scale.get().floatValue();
        float speed = wings.flapSpeed.get().floatValue();
        DragonWingsModule.WingStyle style = wings.wingStyle.get();

        float rate = state.isFallFlying ? 7.2f : (state.walkAnimationSpeed > 0.1f ? 4.4f : 2.1f);
        float t = (now % 60000L) / 1000.0f * speed * rate;
        float baseSweep = 44.0f; // Swept backward (+Z) so it never clips with arms!
        float flap = (float) Math.sin(t) * (state.isFallFlying ? 22.0f : 9.0f) + baseSweep;
        float outerFlap = (float) Math.cos(t - 0.55f) * 12.0f + 22.0f;

        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            // Anchor on upper spine behind torso (+Z)
            poseStack.translate(side * 0.06f, state.isCrouching ? 0.28f : 0.16f, 0.16f);
            poseStack.scale(sc, sc, sc);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * flap));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -18.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(14.0f));

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
        // 1. Shoulder & upper arm bone (angled up and out)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            // Shoulder joint + upper arm bone
            drawBox(pose, vc, light, boneDark, -0.04f, -0.045f, -0.04f, 0.04f, 0.045f, 0.04f);
            float armLen = 0.44f;
            drawSlantedBeam(pose, vc, light, boneCol,
                    0f, 0f, 0f,
                    side * armLen, -0.14f, 0.04f, 0.036f);
            // Inner wing membrane from spine to elbow
            drawDoubleQuad(pose, vc, light, memCol,
                    0.0f, 0.01f, 0.0f,
                    side * armLen, -0.13f, 0.04f,
                    side * (armLen * 0.88f), 0.56f, 0.10f,
                    side * 0.03f, 0.46f, 0.04f);
            // Secondary vein highlight
            drawSlantedBeam(pose, vc, light, boneDark,
                    side * 0.18f, -0.06f, 0.02f,
                    side * 0.22f, 0.48f, 0.08f, 0.015f);
        });

        // 2. Forearm + Wrist Joint + 4 Radiating Finger Ribs + Scalloped Membranes
        poseStack.translate(side * 0.44f, -0.14f, 0.04f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * outerFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 12.0f));

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            // Wrist claw / horn
            drawBox(pose, vc, light, boneDark, -0.045f, -0.045f, -0.045f, 0.045f, 0.045f, 0.045f);
            drawSlantedBeam(pose, vc, light, memBright,
                    0f, -0.03f, -0.01f,
                    side * 0.06f, -0.16f, -0.07f, 0.020f);

            // 4 Finger ribs fanning out
            float[][] ribs = demon
                    ? new float[][]{{0.78f, -0.18f, 0.05f}, {0.66f, 0.22f, 0.09f}, {0.42f, 0.54f, 0.11f}, {0.16f, 0.68f, 0.09f}}
                    : new float[][]{{0.84f, -0.10f, 0.04f}, {0.72f, 0.24f, 0.08f}, {0.48f, 0.52f, 0.10f}, {0.18f, 0.65f, 0.08f}};

            for (int i = 0; i < ribs.length; i++) {
                float rx = side * ribs[i][0];
                float ry = ribs[i][1];
                float rz = ribs[i][2];
                float thickness = (i == 0) ? 0.028f : 0.018f;
                drawSlantedBeam(pose, vc, light, (i == 0) ? boneCol : boneDark, 0f, 0f, 0f, rx, ry, rz, thickness);

                if (i == 0) {
                    // Wingtip claw
                    drawSlantedBeam(pose, vc, light, memBright, rx, ry, rz, rx + side * 0.10f, ry - 0.06f, rz - 0.02f, 0.016f);
                }

                if (i < ribs.length - 1) {
                    float nx = side * ribs[i + 1][0];
                    float ny = ribs[i + 1][1];
                    float nz = ribs[i + 1][2];
                    // Curved scalloped mid-point
                    float mx = (rx + nx) * (demon ? 0.43f : 0.48f);
                    float my = (ry + ny) * (demon ? 0.43f : 0.48f);
                    float mz = (rz + nz) * 0.5f;
                    int segColor = (i % 2 == 0) ? memCol : darken(memCol, 0.86f);
                    drawDoubleQuad(pose, vc, light, segColor,
                            0f, 0f, 0f,
                            rx, ry, rz,
                            mx, my, mz,
                            nx, ny, nz);
                }
            }
            // Connect last rib back toward inner membrane
            float lx = side * ribs[3][0];
            float ly = ribs[3][1];
            float lz = ribs[3][2];
            drawDoubleQuad(pose, vc, light, darken(memCol, 0.84f),
                    0f, 0f, 0f,
                    lx, ly, lz,
                    -side * 0.05f, 0.62f, 0.06f,
                    -side * 0.03f, 0.12f, 0.01f);
        });
    }

    private void renderSeraphWing(PoseStack poseStack, SubmitNodeCollector collector, int light, int side,
                                  int boneCol, int memCol, int memBright, float outerFlap) {
        // Inner feather bank (5 layered feathers)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            drawSlantedBeam(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.42f, -0.14f, 0.04f, 0.035f);
            for (int i = 0; i < 5; i++) {
                float u = i / 4.0f;
                float fx = side * (0.08f + u * 0.34f);
                float fy = -0.10f * u;
                float len = 0.40f + u * 0.18f;
                int col = (i % 2 == 0) ? memBright : memCol;
                drawDoubleQuad(pose, vc, light, col,
                        fx - side * 0.04f, fy, 0.01f * i,
                        fx + side * 0.06f, fy - 0.02f, 0.01f * i,
                        fx + side * 0.03f, fy + len, 0.06f + 0.01f * i,
                        fx - side * 0.06f, fy + len * 0.92f, 0.05f + 0.01f * i);
            }
        });

        // Outer pinion bank (6 long primary feathers)
        poseStack.translate(side * 0.42f, -0.14f, 0.04f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * outerFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            drawSlantedBeam(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.56f, -0.06f, 0.04f, 0.026f);
            for (int i = 0; i < 6; i++) {
                float u = i / 5.0f;
                float fx = side * (0.06f + u * 0.54f);
                float fy = -0.06f * u;
                float len = 0.62f - u * 0.24f;
                int col = (i % 2 == 0) ? memBright : memCol;
                drawDoubleQuad(pose, vc, light, col,
                        fx - side * 0.045f, fy, 0.01f * i,
                        fx + side * 0.065f, fy - 0.02f, 0.01f * i,
                        fx + side * 0.12f, fy + len, 0.08f + 0.01f * i,
                        fx - side * 0.02f, fy + len * 0.94f, 0.06f + 0.01f * i);
            }
        });
    }

    private void renderCyberWing(PoseStack poseStack, SubmitNodeCollector collector, int side,
                                 int frameCol, int glowCol, float outerFlap) {
        // Armored thruster spine + 3 floating energy blades
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            drawSlantedBeam(pose, vc, 0xF000F0, frameCol, 0f, 0f, 0f, side * 0.40f, -0.16f, 0.05f, 0.042f);
            drawBox(pose, vc, 0xF000F0, glowCol, side * 0.12f, -0.10f, -0.02f, side * 0.22f, 0.02f, 0.08f);
        });
        poseStack.translate(side * 0.40f, -0.16f, 0.05f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * outerFlap));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            for (int i = 0; i < 4; i++) {
                float oy = i * 0.15f;
                float ox = side * (0.68f - i * 0.12f);
                drawSlantedBeam(pose, vc, 0xF000F0, frameCol, side * 0.04f, oy, 0.01f, side * 0.22f, oy + 0.02f, 0.03f, 0.022f);
                drawSlantedBeam(pose, vc, 0xF000F0, glowCol, side * 0.18f, oy + 0.01f, 0.02f, ox, oy + 0.14f, 0.06f, 0.018f);
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
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                // Detailed Xianxia Spirit Sword: central fuller, glowing outer edge, beveled tip, winged guard, hilt, gem
                drawBox(pose, vc, 0xF000F0, 0xFFE0F2FE, -0.05f, -0.025f, -1.05f, 0.05f, 0.025f, 0.55f); // Core fuller
                drawBox(pose, vc, 0xF000F0, 0xCC38BDF8, -0.12f, -0.012f, -0.95f, 0.12f, 0.012f, 0.55f); // Blade edges
                // Beveled sword tip
                drawDoubleQuad(pose, vc, 0xF000F0, 0xFFBAE6FD,
                        -0.12f, 0f, -0.95f, 0.12f, 0f, -0.95f, 0f, 0f, -1.32f, 0f, 0f, -1.32f);
                // Winged crossguard
                drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, -0.34f, -0.04f, 0.53f, 0.34f, 0.04f, 0.64f);
                drawSlantedBeam(pose, vc, 0xF000F0, 0xFFF59E0B, -0.32f, 0f, 0.58f, -0.46f, -0.06f, 0.42f, 0.025f);
                drawSlantedBeam(pose, vc, 0xF000F0, 0xFFF59E0B, 0.32f, 0f, 0.58f, 0.46f, -0.06f, 0.42f, 0.025f);
                // Wrapped hilt & glowing pommel gem
                drawBox(pose, vc, packedLight, 0xFF7C2D12, -0.045f, -0.03f, 0.64f, 0.045f, 0.03f, 0.98f);
                drawBox(pose, vc, 0xF000F0, 0xFFA855F7, -0.07f, -0.05f, 0.98f, 0.07f, 0.05f, 1.10f);
            });
            // Rotating spirit ring beneath sword
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI * 0.5;
                    float rx = (float) Math.cos(a) * 0.45f;
                    float rz = (float) Math.sin(a) * 0.45f;
                    drawBox(pose, vc, 0xF000F0, 0xAA38BDF8, rx - 0.04f, 0.05f, rz - 0.04f, rx + 0.04f, 0.08f, rz + 0.04f);
                }
            });
            poseStack.popPose();
            return;
        }

        if (preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.38f, 0.0f);
            poseStack.scale(sc, sc, sc);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                drawBox(pose, vc, packedLight, 0xFF1E293B, -0.24f, -0.09f, -0.48f, 0.24f, 0.13f, 0.48f);
                drawBox(pose, vc, packedLight, 0xFF334155, -0.19f, -0.07f, -0.78f, 0.19f, 0.11f, -0.48f);
                drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, -0.17f, -0.02f, -0.79f, -0.06f, 0.04f, -0.75f);
                drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, 0.06f, -0.02f, -0.79f, 0.17f, 0.04f, -0.75f);
                drawBox(pose, vc, packedLight, 0xFF0F172A, -0.08f, -0.03f, 0.48f, 0.08f, 0.05f, 1.15f);
            });
            for (int side : new int[]{-1, 1}) {
                poseStack.pushPose();
                poseStack.translate(side * 0.24f, 0.02f, -0.1f);
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * flap));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) ->
                        drawBox(pose, vc, packedLight, 0xFF283548,
                                Math.min(0, side * 0.95f), -0.02f, -0.30f,
                                Math.max(0, side * 0.95f), 0.02f, 0.34f));
                poseStack.popPose();
            }
            poseStack.popPose();
            return;
        }

        // Detailed Large Ender Dragon Mount (player sits cleanly on torso)
        poseStack.pushPose();
        poseStack.translate(0.0f, 1.38f, 0.0f);
        poseStack.scale(sc, sc, sc);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
            // Muscular Torso + Dorsal Spine Plates
            drawBox(pose, vc, packedLight, 0xFF121118, -0.30f, -0.18f, -0.62f, 0.30f, 0.22f, 0.66f);
            drawBox(pose, vc, packedLight, 0xFF2B2838, -0.06f, -0.27f, -0.52f, 0.06f, -0.18f, 0.56f);
            // 4 Tucked Clawed Legs
            drawBox(pose, vc, packedLight, 0xFF181622, -0.36f, 0.04f, -0.45f, -0.20f, 0.32f, -0.20f);
            drawBox(pose, vc, packedLight, 0xFF181622, 0.20f, 0.04f, -0.45f, 0.36f, 0.32f, -0.20f);
            drawBox(pose, vc, packedLight, 0xFF181622, -0.36f, 0.04f, 0.30f, -0.20f, 0.34f, 0.56f);
            drawBox(pose, vc, packedLight, 0xFF181622, 0.20f, 0.04f, 0.30f, 0.36f, 0.34f, 0.56f);

            // 3-Segment Arched Neck
            for (int n = 0; n < 3; n++) {
                float nz0 = -0.62f - n * 0.24f;
                float nz1 = nz0 - 0.24f;
                float ny = -0.08f - n * 0.06f;
                drawBox(pose, vc, packedLight, 0xFF161420, -0.13f, ny - 0.12f, nz1, 0.13f, ny + 0.12f, nz0);
                drawBox(pose, vc, packedLight, 0xFF312E42, -0.04f, ny - 0.19f, nz1 + 0.04f, 0.04f, ny - 0.12f, nz0 - 0.04f);
            }

            // Detailed Dragon Head + Lower Jaw + Swept Horns + Glowing Purple Eyes
            drawBox(pose, vc, packedLight, 0xFF1A1826, -0.21f, -0.42f, -1.68f, 0.21f, -0.10f, -1.32f);
            drawBox(pose, vc, packedLight, 0xFF121118, -0.13f, -0.30f, -1.94f, 0.13f, -0.14f, -1.68f); // Upper snout
            drawBox(pose, vc, packedLight, 0xFF1E1B2E, -0.11f, -0.14f, -1.90f, 0.11f, -0.07f, -1.66f); // Lower jaw
            // Swept-back Horns
            drawSlantedBeam(pose, vc, packedLight, 0xFF334155, -0.14f, -0.40f, -1.42f, -0.22f, -0.56f, -1.16f, 0.04f);
            drawSlantedBeam(pose, vc, packedLight, 0xFF334155, 0.14f, -0.40f, -1.42f, 0.22f, -0.56f, -1.16f, 0.04f);
            // Glowing Ender Eyes
            drawBox(pose, vc, 0xF000F0, 0xFFC084FC, -0.215f, -0.34f, -1.60f, -0.12f, -0.26f, -1.46f);
            drawBox(pose, vc, 0xF000F0, 0xFFC084FC, 0.12f, -0.34f, -1.60f, 0.215f, -0.26f, -1.46f);

            // 4-Segment Articulated Tail + Tail Fin
            for (int tSeg = 0; tSeg < 4; tSeg++) {
                float tz0 = 0.66f + tSeg * 0.28f;
                float tz1 = tz0 + 0.28f;
                float halfW = 0.13f - tSeg * 0.02f;
                float sway = (float) Math.sin((now % 60000L) / 220.0 + tSeg * 0.7) * 0.06f;
                drawBox(pose, vc, packedLight, 0xFF14131A, -halfW + sway, -0.08f, tz0, halfW + sway, 0.10f, tz1);
                drawBox(pose, vc, packedLight, 0xFF2B2838, -0.03f + sway, -0.14f, tz0 + 0.05f, 0.03f + sway, -0.08f, tz1 - 0.05f);
            }
        });

        // Large Articulated Ender Dragon Wings on the Mount
        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            poseStack.translate(side * 0.28f, -0.12f, -0.22f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * flap));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(getWhiteTex()), (pose, vc) -> {
                drawSlantedBeam(pose, vc, packedLight, 0xFF1E1B2E, 0f, 0f, 0f, side * 1.45f, -0.08f, 0.08f, 0.045f);
                drawDoubleQuad(pose, vc, packedLight, 0xD04C1D95,
                        0f, 0f, 0f,
                        side * 1.45f, -0.08f, 0.08f,
                        side * 1.32f, 0.04f, 0.85f,
                        0f, 0.02f, 0.68f);
                drawSlantedBeam(pose, vc, packedLight, 0xFF2B2838, side * 0.65f, -0.04f, 0.04f, side * 0.80f, 0.02f, 0.72f, 0.025f);
            });
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawSlantedBeam(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                        float x0, float y0, float z0, float x1, float y1, float z1, float r) {
        drawDoubleQuad(pose, vc, light, argb, x0 - r, y0, z0, x0 + r, y0, z0, x1 + r, y1, z1, x1 - r, y1, z1);
        drawDoubleQuad(pose, vc, light, argb, x0, y0 - r, z0, x0, y0 + r, z0, x1, y1 + r, z1, x1, y1 - r, z1);
        drawDoubleQuad(pose, vc, light, argb, x0, y0, z0 - r, x0, y0, z0 + r, x1, y1, z1 + r, x1, y1, z1 - r);
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
