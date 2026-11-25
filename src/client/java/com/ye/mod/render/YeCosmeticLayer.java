package com.ye.mod.render;

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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * High-resolution 3D Cosmetic Layer on AvatarRenderer:
 * - 256x256 sculpted layered Wings (6 styles, angled cleanly outward & back)
 * - Forward-facing vanilla EnderDragonModel & fast-flapping PhantomModel
 * - Adjustable 3D Head Accessories
 * - Zero-lag 256x256 Magic-Array Soul Rings (auto-hidden when gliding)
 * - Foot-level multi-strand Energy Ribbon Trail
 */
public class YeCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier DRAGON_TEX = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final Identifier PHANTOM_TEX = Identifier.withDefaultNamespace("textures/entity/phantom.png");

    private final EnderDragonModel dragonModel;
    private final PhantomModel phantomModel;
    private final EnderDragonRenderState dragonState = new EnderDragonRenderState();
    private final PhantomRenderState phantomState = new PhantomRenderState();

    public YeCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet modelSet) {
        super(parent);
        this.dragonModel = new EnderDragonModel(modelSet.bakeLayer(ModelLayers.ENDER_DRAGON));
        this.phantomModel = new PhantomModel(modelSet.bakeLayer(ModelLayers.PHANTOM));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        YeHighResTextures.ensureInit();

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();

        DragonWingsModule wings = ModuleManager.get(DragonWingsModule.class);
        if (wings != null && wings.isEnabled() && (!wings.onlySelf.get() || isSelf)) {
            if (!wings.hideWithElytra.get() || !state.isFallFlying) {
                renderHighResWings(poseStack, collector, packedLight, state, wings);
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
            // Auto-hide Soul Rings when elytra-gliding
            if (rings != null && rings.isEnabled() && !state.isFallFlying
                    && !(rings.hideInFirstPerson.get() && mc.options.getCameraType().isFirstPerson())) {
                renderSoulRings(poseStack, collector, rings);
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
        poseStack.translate(0.0f, acc.offsetY.get().floatValue(), acc.offsetZ.get().floatValue());
        float sc = acc.scale.get().floatValue();
        poseStack.scale(sc, sc, sc);
        AccessoriesModule.AccessoryType type = acc.type.get();

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
            switch (type) {
                case SUNGLASSES -> {
                    drawBox(pose, vc, 0xF000F0, 0xEE0B0D14, -0.24f, -0.28f, -0.285f, -0.04f, -0.17f, -0.255f);
                    drawBox(pose, vc, 0xF000F0, 0xEE0B0D14, 0.04f, -0.28f, -0.285f, 0.24f, -0.17f, -0.255f);
                    drawBox(pose, vc, light, 0xFF94A3B8, -0.25f, -0.29f, -0.27f, 0.25f, -0.26f, -0.25f);
                    drawBox(pose, vc, light, 0xFF64748B, -0.26f, -0.27f, -0.26f, -0.24f, -0.23f, 0.16f);
                    drawBox(pose, vc, light, 0xFF64748B, 0.24f, -0.27f, -0.26f, 0.26f, -0.23f, 0.16f);
                }
                case TOP_HAT -> {
                    drawBox(pose, vc, light, 0xFF14151C, -0.34f, -0.53f, -0.34f, 0.34f, -0.49f, 0.34f);
                    drawBox(pose, vc, light, 0xFF991B1B, -0.24f, -0.60f, -0.24f, 0.24f, -0.53f, 0.24f);
                    drawBox(pose, vc, light, 0xFF181922, -0.23f, -0.92f, -0.23f, 0.23f, -0.60f, 0.23f);
                }
                case CROWN -> {
                    drawBox(pose, vc, 0xF000F0, 0xFFFACC15, -0.26f, -0.56f, -0.26f, 0.26f, -0.49f, 0.26f);
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

    private void renderHighResWings(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                    AvatarRenderState state, DragonWingsModule wings) {
        long now = System.currentTimeMillis();
        int tint = wings.getTintColor(now);
        int secondaryTint = darken(tint, 0.80f);
        float sc = wings.scale.get().floatValue();
        float speed = wings.flapSpeed.get().floatValue();
        DragonWingsModule.WingStyle style = wings.wingStyle.get();
        Identifier tex = YeHighResTextures.getWingTexture(style);

        float rate = state.isFallFlying ? 6.8f : (state.walkAnimationSpeed > 0.1f ? 4.2f : 2.0f);
        float t = (now % 60000L) / 1000.0f * speed * rate;
        // Spread outward & gently back (28..42 deg) so wings NEVER collide behind the back or clip arms!
        float flap = (float) Math.sin(t) * (state.isFallFlying ? 16.0f : 7.5f) + 32.0f;

        for (int side : new int[]{-1, 1}) {
            poseStack.pushPose();
            poseStack.translate(side * 0.11f, state.isCrouching ? 0.26f : 0.12f, 0.16f);
            poseStack.scale(sc, sc, sc);
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * flap));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -10.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(8.0f));

            // Primary sculpted 256x256 wing plane (curved in Z for 3D depth)
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(tex), (pose, vc) -> {
                float w = side * 1.15f;
                float hTop = -0.35f;
                float hBot = 0.78f;
                // Curved 2-segment wing mesh (inner half + outer half angled slightly back)
                drawDoubleUvQuad(pose, vc, 0xF000F0, tint,
                        0.0f, hTop, 0.0f, 0.0f, 0.0f,
                        w * 0.5f, hTop - 0.06f, 0.05f, 0.5f, 0.0f,
                        w * 0.5f, hBot + 0.04f, 0.08f, 0.5f, 1.0f,
                        0.0f, hBot, 0.02f, 0.0f, 1.0f);
                drawDoubleUvQuad(pose, vc, 0xF000F0, tint,
                        w * 0.5f, hTop - 0.06f, 0.05f, 0.5f, 0.0f,
                        w, hTop - 0.14f, 0.18f, 1.0f, 0.0f,
                        w, hBot - 0.08f, 0.20f, 1.0f, 1.0f,
                        w * 0.5f, hBot + 0.04f, 0.08f, 0.5f, 1.0f);

                // Secondary layered plumage/depth plane offset slightly behind
                drawDoubleUvQuad(pose, vc, packedLight, secondaryTint,
                        0.0f, hTop + 0.12f, 0.05f, 0.0f, 0.0f,
                        w * 0.85f, hTop + 0.02f, 0.16f, 1.0f, 0.0f,
                        w * 0.85f, hBot - 0.06f, 0.18f, 1.0f, 1.0f,
                        0.0f, hBot + 0.06f, 0.07f, 0.0f, 1.0f);
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
        float wSpeed = flight.wingSpeed.get().floatValue();
        long now = System.currentTimeMillis();

        if (preset == FlightModelModule.FlightPreset.MINI_DRAGON) {
            // Fixed orientation: face FORWARD (-Z), NOT 180 deg backward!
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.35f, 0.0f);
            float dScale = sc * 0.24f;
            poseStack.scale(dScale, dScale, dScale);
            dragonState.flapTime = (now % 60000L) / 1000.0f * wSpeed;
            dragonState.ageInTicks = state.ageInTicks;
            collector.submitModel(dragonModel, dragonState, poseStack, RenderTypes.entityCutoutNoCull(DRAGON_TEX),
                    packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            return;
        }

        if (preset == FlightModelModule.FlightPreset.MINI_PHANTOM) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.45f, 0.0f);
            float pScale = sc * 1.15f;
            poseStack.scale(pScale, pScale, pScale);
            phantomState.flapTime = (now % 60000L) / 1000.0f * wSpeed * 2.2f;
            phantomState.ageInTicks = state.ageInTicks;
            collector.submitModel(phantomModel, phantomState, poseStack, RenderTypes.entityCutoutNoCull(PHANTOM_TEX),
                    packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            return;
        }

        // Textured Xianxia Spirit Sword
        poseStack.pushPose();
        poseStack.translate(0.0f, 1.48f, 0.0f);
        poseStack.scale(sc, sc, sc);
        float spin = ((now % 4000L) / 4000.0f) * 360.0f;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
            drawBox(pose, vc, 0xF000F0, 0xFFE0F2FE, -0.055f, -0.03f, -1.08f, 0.055f, 0.03f, 0.55f);
            drawBox(pose, vc, 0xF000F0, 0xCC38BDF8, -0.14f, -0.015f, -0.98f, 0.14f, 0.015f, 0.55f);
            drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, -0.36f, -0.045f, 0.53f, 0.36f, 0.045f, 0.65f);
            drawBox(pose, vc, packedLight, 0xFF7C2D12, -0.048f, -0.035f, 0.65f, 0.048f, 0.035f, 1.00f);
            drawBox(pose, vc, 0xF000F0, 0xFFA855F7, -0.075f, -0.055f, 1.00f, 0.075f, 0.055f, 1.12f);
        });
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                drawDoubleUvQuad(pose, vc, 0xF000F0, 0xCC38BDF8,
                        -0.75f, 0.07f, -0.75f, 0f, 0f,
                        0.75f, 0.07f, -0.75f, 1f, 0f,
                        0.75f, 0.07f, 0.75f, 1f, 1f,
                        -0.75f, 0.07f, 0.75f, 0f, 1f));
        poseStack.popPose();
    }

    private void renderSoulRings(PoseStack poseStack, SubmitNodeCollector collector, SoulRingsModule rings) {
        int count = Math.max(1, Math.min(9, (int) Math.round(rings.ringCount.get())));
        float baseR = rings.radius.get().floatValue();
        double speed = rings.spinSpeed.get();
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            float y = 1.38f - i * 0.22f + (float) Math.sin((now / 400.0) * speed + i) * 0.04f;
            float r = baseR + ((i % 2 == 0) ? 0.0f : 0.16f);
            int rgb = rings.pickRingRgb(i);
            int col = 0xEE000000 | rgb;
            float spin = (float) (((now / 14.0) * speed * ((i % 2 == 0) ? 1 : -1)) % 360.0);

            poseStack.pushPose();
            poseStack.translate(0.0f, y, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                    drawDoubleUvQuad(pose, vc, 0xF000F0, col,
                            -r, 0f, -r, 0f, 0f,
                            r, 0f, -r, 1f, 0f,
                            r, 0f, r, 1f, 1f,
                            -r, 0f, r, 0f, 1f));
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

        poseStack.pushPose();
        poseStack.translate(0.0f, 1.501f, 0.0f);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0f - state.bodyRot)));

        long now = System.currentTimeMillis();
        long maxAge = Math.max(300L, Math.round(trail.durationSec.get() * 1000.0));
        float halfW = trail.width.get().floatValue() * 0.5f;
        MotionTrailModule.TrailPreset preset = trail.preset.get();

        int c1, c2;
        switch (preset) {
            case PHOENIX_FLAME -> { c1 = 0xFDE047; c2 = 0xEF4444; }
            case VOID_LIGHTNING -> { c1 = 0xE879F9; c2 = 0x7C3AED; }
            case SAKURA_STREAM -> { c1 = 0xFFF1F2; c2 = 0xF472B6; }
            default -> { c1 = 0x7DD3FC; c2 = 0x6366F1; }
        }

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(YeHighResTextures.TRAIL_STREAM_TEX), (pose, vc) -> {
            for (int i = 1; i < pts.size(); i++) {
                Vec3 w0 = pts.get(i - 1).pos().subtract(playerPos);
                Vec3 w1 = pts.get(i).pos().subtract(playerPos);

                if (w1.x * lookXZ.x + w1.z * lookXZ.z > -0.05) continue;

                float age = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) (now - pts.get(i).timeMs()) / maxAge));
                int a = Math.max(18, Math.round(age * 220));
                int col = (a << 24) | ((i % 2 == 0) ? c2 : c1);

                Vec3 dir = w1.subtract(w0);
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                if (side.lengthSqr() < 0.0001) side = new Vec3(1, 0, 0);
                else side = side.normalize().scale(halfW * (0.4f + 0.6f * age));

                float u0 = ((i - 1) % 8) / 8.0f;
                float u1 = u0 + 0.125f;
                drawDoubleUvQuad(pose, vc, 0xF000F0, col,
                        (float) (w0.x - side.x), (float) w0.y, (float) (w0.z - side.z), u0, 0f,
                        (float) (w0.x + side.x), (float) w0.y, (float) (w0.z + side.z), u0, 1f,
                        (float) (w1.x + side.x), (float) w1.y, (float) (w1.z + side.z), u1, 1f,
                        (float) (w1.x - side.x), (float) w1.y, (float) (w1.z - side.z), u1, 0f);
            }
        });
        poseStack.popPose();
    }

    private static void drawDoubleUvQuad(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                         float x1, float y1, float z1, float u1, float v1,
                                         float x2, float y2, float z2, float u2, float v2,
                                         float x3, float y3, float z3, float u3, float v3,
                                         float x4, float y4, float z4, float u4, float v4) {
        emitVertex(pose, vc, x1, y1, z1, argb, u1, v1, light, 0f, 1f, 0f);
        emitVertex(pose, vc, x2, y2, z2, argb, u2, v2, light, 0f, 1f, 0f);
        emitVertex(pose, vc, x3, y3, z3, argb, u3, v3, light, 0f, 1f, 0f);
        emitVertex(pose, vc, x4, y4, z4, argb, u4, v4, light, 0f, 1f, 0f);

        emitVertex(pose, vc, x4, y4, z4, argb, u4, v4, light, 0f, -1f, 0f);
        emitVertex(pose, vc, x3, y3, z3, argb, u3, v3, light, 0f, -1f, 0f);
        emitVertex(pose, vc, x2, y2, z2, argb, u2, v2, light, 0f, -1f, 0f);
        emitVertex(pose, vc, x1, y1, z1, argb, u1, v1, light, 0f, -1f, 0f);
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
}
