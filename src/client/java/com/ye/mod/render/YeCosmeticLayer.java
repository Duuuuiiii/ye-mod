package com.ye.mod.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.MotionTrailModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.model.monster.phantom.PhantomModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.PhantomRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Unified 3D Cosmetic & Visual Layer on AvatarRenderer:
 * - Real vanilla EnderDragonModel & PhantomModel for Flight Models
 * - Backward-swept 2-jointed thick 3D Wings (4 styles)
 * - Head-tracked 3D Accessories (Sunglasses, Crown, Top Hat, Halo, Cat Ears)
 * - Zero-lag player-attached textured Soul Rings
 * - Wide 3D Ribbon Motion Trail
 */
public class YeCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier DRAGON_TEX = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final Identifier PHANTOM_TEX = Identifier.withDefaultNamespace("textures/entity/phantom.png");
    private static final Identifier WING_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_wing");
    private static final Identifier SCALE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_scale");
    private static final Identifier SWORD_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_sword");
    private static final Identifier RING_TEX = Identifier.fromNamespaceAndPath("ye", "textures/cosmetic_ring");
    private static boolean texturesRegistered = false;

    private final EnderDragonModel dragonModel;
    private final PhantomModel phantomModel;
    private final EnderDragonRenderState dragonState = new EnderDragonRenderState();
    private final PhantomRenderState phantomState = new PhantomRenderState();

    public YeCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet modelSet) {
        super(parent);
        this.dragonModel = new EnderDragonModel(modelSet.bakeLayer(ModelLayers.ENDER_DRAGON));
        this.phantomModel = new PhantomModel(modelSet.bakeLayer(ModelLayers.PHANTOM));
    }

    private static void ensureTextures() {
        if (texturesRegistered) return;
        texturesRegistered = true;

        NativeImage wingImg = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
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

        NativeImage swordImg = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int dist = Math.abs(x - 32);
                boolean rune = dist < 5 && ((y % 10) < 4);
                int v = rune ? 255 : (dist < 10 ? 225 : 185);
                swordImg.setPixel(x, y, (0xFF << 24) | (v << 16) | (v << 8) | v);
            }
        }
        Minecraft.getInstance().getTextureManager().register(SWORD_TEX, new DynamicTexture(() -> "ye_sword_tex", swordImg));

        // Glowing Soul Ring rune texture (64x64 radial ring with etched glyphs)
        NativeImage ringImg = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                double dx = x - 31.5;
                double dy = y - 31.5;
                double r = Math.hypot(dx, dy);
                double ang = Math.atan2(dy, dx);
                int a = 0;
                int v = 255;
                if (r >= 24.5 && r <= 30.5) {
                    boolean rune = ((int) Math.floor((ang + Math.PI) * 6.0) % 2 == 0) && r > 26.0 && r < 29.0;
                    double edge = 1.0 - Math.abs(r - 27.5) / 3.2;
                    a = Math.max(0, Math.min(255, (int) Math.round((rune ? 255 : 210) * edge)));
                } else if (r >= 20.5 && r <= 22.5) {
                    a = 150;
                }
                ringImg.setPixel(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        Minecraft.getInstance().getTextureManager().register(RING_TEX, new DynamicTexture(() -> "ye_ring_tex", ringImg));
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

        AccessoriesModule acc = ModuleManager.get(AccessoriesModule.class);
        if (acc != null && acc.isEnabled() && (!acc.onlySelf.get() || isSelf)) {
            renderHeadAccessory(poseStack, collector, packedLight, acc);
        }

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        if (flight != null && flight.isEnabled() && state.isFallFlying && (!flight.onlySelf.get() || isSelf)) {
            renderFlightMount(poseStack, collector, packedLight, state, flight);
        }

        if (isSelf) {
            SoulRingsModule rings = ModuleManager.get(SoulRingsModule.class);
            if (rings != null && rings.isEnabled()
                    && !(rings.hideInFirstPerson.get() && mc.options.getCameraType().isFirstPerson())) {
                renderSoulRings(poseStack, collector, state, rings);
            }

            MotionTrailModule trail = ModuleManager.get(MotionTrailModule.class);
            if (trail != null && trail.isEnabled()) {
                renderMotionRibbon(poseStack, collector, state, trail);
            }
        }
    }

    private void renderHeadAccessory(PoseStack poseStack, SubmitNodeCollector collector, int light, AccessoriesModule acc) {
        poseStack.pushPose();
        this.getParentModel().head.translateAndRotate(poseStack);
        float sc = acc.scale.get().floatValue();
        poseStack.scale(sc, sc, sc);
        AccessoriesModule.AccessoryType type = acc.type.get();

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            switch (type) {
                case SUNGLASSES -> {
                    // Sleek dark-tinted sunglasses on face (-Z)
                    drawBox(pose, vc, 0xF000F0, 0xEE0B0D14, -0.24f, -0.28f, -0.285f, -0.04f, -0.17f, -0.255f);
                    drawBox(pose, vc, 0xF000F0, 0xEE0B0D14, 0.04f, -0.28f, -0.285f, 0.24f, -0.17f, -0.255f);
                    drawBox(pose, vc, light, 0xFF94A3B8, -0.25f, -0.29f, -0.27f, 0.25f, -0.26f, -0.25f); // Bridge + brow bar
                    drawBox(pose, vc, light, 0xFF64748B, -0.26f, -0.27f, -0.26f, -0.24f, -0.23f, 0.16f); // Left temple arm
                    drawBox(pose, vc, light, 0xFF64748B, 0.24f, -0.27f, -0.26f, 0.26f, -0.23f, 0.16f);  // Right temple arm
                }
                case TOP_HAT -> {
                    drawBox(pose, vc, light, 0xFF14151C, -0.34f, -0.53f, -0.34f, 0.34f, -0.49f, 0.34f); // Brim
                    drawBox(pose, vc, light, 0xFF991B1B, -0.24f, -0.60f, -0.24f, 0.24f, -0.53f, 0.24f); // Red band
                    drawBox(pose, vc, light, 0xFF181922, -0.23f, -0.92f, -0.23f, 0.23f, -0.60f, 0.23f); // Crown
                }
                case CROWN -> {
                    drawBox(pose, vc, 0xF000F0, 0xFFFACC15, -0.26f, -0.56f, -0.26f, 0.26f, -0.49f, 0.26f);
                    // Crown spikes + ruby/sapphire gems
                    for (int i = -1; i <= 1; i++) {
                        float px = i * 0.18f;
                        drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, px - 0.04f, -0.67f, -0.26f, px + 0.04f, -0.56f, -0.21f);
                        drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, px - 0.04f, -0.67f, 0.21f, px + 0.04f, -0.56f, 0.26f);
                    }
                    drawBox(pose, vc, 0xF000F0, 0xFFEF4444, -0.04f, -0.55f, -0.27f, 0.04f, -0.50f, -0.24f);
                }
                case HALO -> {
                    drawBox(pose, vc, 0xF000F0, 0xFFFDE047, -0.24f, -0.74f, -0.24f, 0.24f, -0.70f, -0.18f);
                    drawBox(pose, vc, 0xF000F0, 0xFFFDE047, -0.24f, -0.74f, 0.18f, 0.24f, -0.70f, 0.24f);
                    drawBox(pose, vc, 0xF000F0, 0xFFFDE047, -0.24f, -0.74f, -0.18f, -0.18f, -0.70f, 0.18f);
                    drawBox(pose, vc, 0xF000F0, 0xFFFDE047, 0.18f, -0.74f, -0.18f, 0.24f, -0.70f, 0.18f);
                }
                case CAT_EARS -> {
                    for (int s : new int[]{-1, 1}) {
                        drawBox(pose, vc, light, 0xFF1E1B2E, s * 0.11f - 0.06f, -0.64f, -0.06f, s * 0.11f + 0.06f, -0.50f, 0.06f);
                        drawBox(pose, vc, 0xF000F0, 0xFFF472B6, s * 0.11f - 0.03f, -0.61f, -0.07f, s * 0.11f + 0.03f, -0.52f, -0.05f);
                    }
                }
            }
        });
        poseStack.popPose();
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
        // NEGATED Y-rot (-side * flap) sweeps wings BACKWARD (+Z) behind the player!
        float flap = (float) Math.sin(t) * (state.isFallFlying ? 18.0f : 8.0f) + 52.0f;
        float outerFlap = (float) Math.cos(t - 0.55f) * 14.0f + 28.0f;

        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            poseStack.translate(side * 0.07f, state.isCrouching ? 0.28f : 0.15f, 0.18f);
            poseStack.scale(sc, sc, sc);
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * flap));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -14.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(16.0f));

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
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            drawBox(pose, vc, light, boneDark, -0.025f, -0.028f, -0.025f, 0.025f, 0.028f, 0.025f);
            drawThickBone(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.48f, -0.16f, 0.04f, 0.024f);
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) ->
                drawThickMembrane(pose, vc, light, memCol, 0.004f,
                        0.0f, 0.01f, 0.0f,
                        side * 0.48f, -0.15f, 0.04f,
                        side * 0.42f, 0.58f, 0.08f,
                        side * 0.03f, 0.48f, 0.03f));

        poseStack.translate(side * 0.48f, -0.16f, 0.04f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-side * outerFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0f));

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) -> {
            drawBox(pose, vc, light, boneDark, -0.025f, -0.025f, -0.025f, 0.025f, 0.025f, 0.025f);
            drawThickBone(pose, vc, light, memBright, 0f, -0.02f, -0.01f, side * 0.06f, -0.14f, -0.06f, 0.014f);
            float[][] ribs = demon
                    ? new float[][]{{0.84f, -0.18f, 0.04f}, {0.70f, 0.24f, 0.08f}, {0.46f, 0.56f, 0.10f}, {0.18f, 0.70f, 0.08f}}
                    : new float[][]{{0.90f, -0.10f, 0.03f}, {0.76f, 0.26f, 0.07f}, {0.52f, 0.54f, 0.09f}, {0.20f, 0.68f, 0.07f}};
            for (int i = 0; i < ribs.length; i++) {
                float rx = side * ribs[i][0];
                float ry = ribs[i][1];
                float rz = ribs[i][2];
                drawThickBone(pose, vc, light, (i == 0) ? boneCol : boneDark, 0f, 0f, 0f, rx, ry, rz, (i == 0) ? 0.018f : 0.011f);
                if (i == 0) {
                    drawThickBone(pose, vc, light, memBright, rx, ry, rz, rx + side * 0.10f, ry - 0.06f, rz - 0.02f, 0.010f);
                }
            }
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
            float[][] ribs = demon
                    ? new float[][]{{0.84f, -0.18f, 0.04f}, {0.70f, 0.24f, 0.08f}, {0.46f, 0.56f, 0.10f}, {0.18f, 0.70f, 0.08f}}
                    : new float[][]{{0.90f, -0.10f, 0.03f}, {0.76f, 0.26f, 0.07f}, {0.52f, 0.54f, 0.09f}, {0.20f, 0.68f, 0.07f}};
            for (int i = 0; i < ribs.length - 1; i++) {
                float rx = side * ribs[i][0];
                float ry = ribs[i][1];
                float rz = ribs[i][2];
                float nx = side * ribs[i + 1][0];
                float ny = ribs[i + 1][1];
                float nz = ribs[i + 1][2];
                drawThickMembrane(pose, vc, light, (i % 2 == 0) ? memCol : darken(memCol, 0.88f), 0.004f,
                        0f, 0f, 0f, rx, ry, rz,
                        (rx + nx) * (demon ? 0.43f : 0.48f), (ry + ny) * (demon ? 0.43f : 0.48f), (rz + nz) * 0.5f,
                        nx, ny, nz);
            }
        });
    }

    private void renderSeraphWing(PoseStack poseStack, SubmitNodeCollector collector, int light, int side,
                                  int boneCol, int memCol, int memBright, float outerFlap) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) ->
                drawThickBone(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.44f, -0.14f, 0.04f, 0.044f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
            for (int i = 0; i < 5; i++) {
                float u = i / 4.0f;
                float fx = side * (0.08f + u * 0.34f);
                float fy = -0.10f * u;
                float len = 0.42f + u * 0.18f;
                drawThickMembrane(pose, vc, light, (i % 2 == 0) ? memBright : memCol, 0.015f,
                        fx - side * 0.04f, fy, 0.012f * i,
                        fx + side * 0.06f, fy - 0.02f, 0.012f * i,
                        fx + side * 0.03f, fy + len, 0.06f + 0.012f * i,
                        fx - side * 0.06f, fy + len * 0.92f, 0.05f + 0.012f * i);
            }
        });

        poseStack.translate(side * 0.44f, -0.14f, 0.04f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-side * outerFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SCALE_TEX), (pose, vc) ->
                drawThickBone(pose, vc, light, boneCol, 0f, 0f, 0f, side * 0.58f, -0.06f, 0.04f, 0.034f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WING_TEX), (pose, vc) -> {
            for (int i = 0; i < 6; i++) {
                float u = i / 5.0f;
                float fx = side * (0.06f + u * 0.56f);
                float fy = -0.06f * u;
                float len = 0.64f - u * 0.24f;
                drawThickMembrane(pose, vc, light, (i % 2 == 0) ? memBright : memCol, 0.013f,
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
        poseStack.mulPose(Axis.YP.rotationDegrees(-side * outerFlap));
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

        if (preset == FlightModelModule.FlightPreset.MINI_DRAGON) {
            // Render REAL vanilla EnderDragonModel scaled down beneath the sitting player!
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.35f, 0.0f);
            float dScale = sc * 0.24f;
            poseStack.scale(dScale, dScale, dScale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            dragonState.flapTime = (now % 60000L) / 1000.0f * 1.15f;
            dragonState.ageInTicks = state.ageInTicks;
            collector.submitModel(dragonModel, dragonState, poseStack, RenderTypes.entityCutoutNoCull(DRAGON_TEX),
                    packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            return;
        }

        if (preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            // Render REAL vanilla PhantomModel beneath the sitting player!
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.45f, 0.0f);
            float pScale = sc * 1.15f;
            poseStack.scale(pScale, pScale, pScale);
            phantomState.flapTime = (now % 60000L) / 1000.0f * 1.6f;
            phantomState.ageInTicks = state.ageInTicks;
            collector.submitModel(phantomModel, phantomState, poseStack, RenderTypes.entityCutoutNoCull(PHANTOM_TEX),
                    packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            return;
        }

        // Detailed Textured Xianxia Spirit Sword
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
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RING_TEX), (pose, vc) ->
                drawDoubleQuad(pose, vc, 0xF000F0, 0xCC38BDF8, -0.65f, 0.07f, -0.65f, 0.65f, 0.07f, -0.65f, 0.65f, 0.07f, 0.65f, -0.65f, 0.07f, 0.65f));
        poseStack.popPose();
    }

    private void renderSoulRings(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, SoulRingsModule rings) {
        // Attached 1:1 to player's PoseStack -> ZERO lag at any speed!
        int count = Math.max(1, Math.min(9, (int) Math.round(rings.ringCount.get())));
        float baseR = rings.radius.get().floatValue();
        double speed = rings.spinSpeed.get();
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            float y = 1.32f - i * 0.22f + (float) Math.sin((now / 400.0) * speed + i) * 0.05f;
            float r = baseR + ((i % 2 == 0) ? 0.0f : 0.14f);
            int rgb = rings.pickRingRgb(i);
            int col = 0xEE000000 | rgb;
            float spin = (float) (((now / 15.0) * speed * ((i % 2 == 0) ? 1 : -1)) % 360.0);

            poseStack.pushPose();
            poseStack.translate(0.0f, y, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RING_TEX), (pose, vc) ->
                    drawDoubleQuad(pose, vc, 0xF000F0, col, -r, 0f, -r, r, 0f, -r, r, 0f, r, -r, 0f, r));
            poseStack.popPose();
        }
    }

    private void renderMotionRibbon(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, MotionTrailModule trail) {
        List<MotionTrailModule.TrailPoint> pts = trail.getPointsSnapshot();
        if (pts.size() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 playerPos = mc.player.getPosition(pt);
        Vec3 look = mc.player.getLookAngle();
        Vec3 lookRaw = new Vec3(look.x, 0, look.z);
        Vec3 lookXZ = lookRaw.lengthSqr() > 0.001 ? lookRaw.normalize() : new Vec3(0, 0, 1);

        // Undo LivingEntityRenderer setupRotations to enter world-aligned space at player feet
        poseStack.pushPose();
        poseStack.translate(0.0f, 1.501f, 0.0f);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0f - state.bodyRot)));

        long now = System.currentTimeMillis();
        long maxAge = Math.max(300L, Math.round(trail.durationSec.get() * 1000.0));
        float halfW = trail.width.get().floatValue() * 0.5f;
        MotionTrailModule.TrailPreset preset = trail.preset.get();

        int coreRgb;
        int outerRgb;
        switch (preset) {
            case PHOENIX_FLAME -> { coreRgb = 0xFDE047; outerRgb = 0xEF4444; }
            case VOID_LIGHTNING -> { coreRgb = 0xE879F9; outerRgb = 0x7C3AED; }
            case SAKURA_STREAM -> { coreRgb = 0xFFF1F2; outerRgb = 0xF472B6; }
            default -> { coreRgb = 0x7DD3FC; outerRgb = 0x6366F1; }
        }

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SWORD_TEX), (pose, vc) -> {
            for (int i = 1; i < pts.size(); i++) {
                Vec3 w0 = pts.get(i - 1).pos().subtract(playerPos);
                Vec3 w1 = pts.get(i).pos().subtract(playerPos);

                // Prevent any point ahead of the player in 1st or 3rd person!
                if (w1.x * lookXZ.x + w1.z * lookXZ.z > 0.05) continue;

                float age = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) (now - pts.get(i).timeMs()) / maxAge));
                int a = Math.max(18, Math.round(age * 210));
                int col = (a << 24) | ((i % 2 == 0) ? outerRgb : coreRgb);

                Vec3 dir = w1.subtract(w0);
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                if (side.lengthSqr() < 0.0001) side = new Vec3(1, 0, 0);
                else side = side.normalize().scale(halfW * age);

                drawDoubleQuad(pose, vc, 0xF000F0, col,
                        (float) (w0.x - side.x), (float) w0.y, (float) (w0.z - side.z),
                        (float) (w0.x + side.x), (float) w0.y, (float) (w0.z + side.z),
                        (float) (w1.x + side.x), (float) w1.y, (float) (w1.z + side.z),
                        (float) (w1.x - side.x), (float) w1.y, (float) (w1.z - side.z));
            }
        });
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
