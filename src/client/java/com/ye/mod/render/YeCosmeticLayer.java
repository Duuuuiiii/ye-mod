package com.ye.mod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.render.wings.YeAvianWingsModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
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
    private final PlayerModel standModel;
    private final YeAvianWingsModel avianWings = new YeAvianWingsModel();
    private final EnderDragonRenderState dragonState = new EnderDragonRenderState();
    private final PhantomRenderState phantomState = new PhantomRenderState();

    public YeCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet modelSet) {
        super(parent);
        this.dragonModel = new EnderDragonModel(modelSet.bakeLayer(ModelLayers.ENDER_DRAGON));
        this.phantomModel = new PhantomModel(modelSet.bakeLayer(ModelLayers.PHANTOM));
        this.standModel = new PlayerModel(modelSet.bakeLayer(ModelLayers.PLAYER), false);
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
            if (rings != null && rings.isEnabled() && (rings.showWhenGliding.get() || !state.isFallFlying)
                    && !(rings.hideInFirstPerson.get() && mc.options.getCameraType().isFirstPerson())) {
                renderSoulRings(poseStack, collector, rings);
            }
        }

        StandBuddyModule stand = ModuleManager.get(StandBuddyModule.class);
        if (stand != null && stand.isEnabled() && (!stand.onlySelf.get() || isSelf)
                && !(isSelf && mc.options.getCameraType().isFirstPerson())) {
            renderStandBuddy(poseStack, collector, state, stand);
        }
    }

    private void renderStandBuddy(PoseStack poseStack, SubmitNodeCollector collector,
                                  AvatarRenderState state, StandBuddyModule stand) {
        long now = System.currentTimeMillis();
        float bob = (float) Math.sin(now / 360.0) * 0.08f;
        float sc = stand.scale.get().floatValue();

        standModel.setupAnim(state);
        if (state.attackTime > 0.0f) {
            float rush = (float) Math.sin(now / 35.0) * 0.9f;
            standModel.rightArm.xRot = -1.65f + rush;
            standModel.leftArm.xRot = -1.65f - rush;
        } else {
            standModel.rightArm.xRot = -0.35f;
            standModel.leftArm.xRot = -0.35f;
        }

        poseStack.pushPose();
        poseStack.translate(-0.72f, -0.32f + bob, 0.72f);
        poseStack.scale(sc, sc, sc);
        collector.submitModelPart(
                standModel.root(),
                poseStack,
                RenderTypes.entityTranslucent(state.skin.body().texturePath()),
                0xF000F0,
                OverlayTexture.NO_OVERLAY,
                null,
                false,
                false,
                stand.style.get().tintArgb,
                null,
                state.outlineColor
        );
        poseStack.popPose();
    }

    private void renderHeadAccessory(PoseStack poseStack, SubmitNodeCollector collector, int light, AccessoriesModule acc) {
        AccessoriesModule.HeadItem head = acc.headItem.get();
        if (head != AccessoriesModule.HeadItem.NONE) {
            AccessoriesModule.ItemTransform ht = acc.getHeadTransform(head);
            poseStack.pushPose();
            this.getParentModel().head.translateAndRotate(poseStack);
            if (ht != null) {
                poseStack.translate(0.0f, ht.y().get().floatValue(), ht.z().get().floatValue());
                float sc = ht.scale().get().floatValue();
                poseStack.scale(sc, sc, sc);
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                switch (head) {
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
                    case BUNNY_EARS -> {
                        for (int s : new int[]{-1, 1}) {
                            drawBox(pose, vc, light, 0xFFF8FAFC, s * 0.12f - 0.045f, -0.82f, -0.04f, s * 0.12f + 0.045f, -0.50f, 0.04f);
                            drawBox(pose, vc, 0xF000F0, 0xFFF9A8D4, s * 0.12f - 0.025f, -0.78f, -0.046f, s * 0.12f + 0.025f, -0.53f, -0.035f);
                            drawBox(pose, vc, light, 0xFFF1F5F9, s * 0.13f - 0.04f, -0.86f, 0.02f, s * 0.13f + 0.04f, -0.76f, 0.10f);
                        }
                    }
                    case ANTLERS -> {
                        for (int s : new int[]{-1, 1}) {
                            drawBox(pose, vc, light, 0xFF78350F, s * 0.14f - 0.025f, -0.74f, -0.025f, s * 0.14f + 0.025f, -0.49f, 0.025f);
                            drawBox(pose, vc, light, 0xFF92400E, s * 0.22f - 0.02f, -0.86f, -0.04f, s * 0.22f + 0.02f, -0.68f, 0.02f);
                            drawBox(pose, vc, light, 0xFFB45309, s * 0.10f - 0.018f, -0.82f, -0.06f, s * 0.10f + 0.018f, -0.66f, -0.01f);
                        }
                    }
                    case GHOST_BUDDY -> {
                        float bob = (float) Math.sin(System.currentTimeMillis() / 320.0) * 0.03f;
                        drawBox(pose, vc, 0xF000F0, 0xEEF8FAFC, -0.11f, -0.84f + bob, -0.11f, 0.11f, -0.62f + bob, 0.11f);
                        drawBox(pose, vc, 0xF000F0, 0xFF0F172A, -0.07f, -0.77f + bob, -0.115f, -0.02f, -0.71f + bob, -0.105f);
                        drawBox(pose, vc, 0xF000F0, 0xFF0F172A, 0.02f, -0.77f + bob, -0.115f, 0.07f, -0.71f + bob, -0.105f);
                        drawBox(pose, vc, 0xF000F0, 0xCCBFEFFF, -0.15f, -0.74f + bob, -0.03f, -0.11f, -0.68f + bob, 0.03f);
                        drawBox(pose, vc, 0xF000F0, 0xCCBFEFFF, 0.11f, -0.74f + bob, -0.03f, 0.15f, -0.68f + bob, 0.03f);
                    }
                    case DEVIL_HORNS -> {
                        for (int s : new int[]{-1, 1}) {
                            drawBox(pose, vc, light, 0xFF7F1D1D, s * 0.16f - 0.045f, -0.62f, -0.10f, s * 0.16f + 0.045f, -0.49f, -0.02f);
                            drawBox(pose, vc, 0xF000F0, 0xFFDC2626, s * 0.21f - 0.035f, -0.74f, -0.05f, s * 0.21f + 0.035f, -0.62f, 0.02f);
                            drawBox(pose, vc, 0xF000F0, 0xFFFCA5A5, s * 0.19f - 0.02f, -0.82f, -0.10f, s * 0.19f + 0.02f, -0.74f, -0.04f);
                        }
                    }
                    case DRAGON_HORNS -> {
                        for (int s : new int[]{-1, 1}) {
                            drawBox(pose, vc, light, 0xFF1E1B2E, s * 0.16f - 0.04f, -0.60f, -0.04f, s * 0.16f + 0.04f, -0.49f, 0.08f);
                            drawBox(pose, vc, 0xF000F0, 0xFFA855F7, s * 0.20f - 0.03f, -0.70f, 0.08f, s * 0.20f + 0.03f, -0.58f, 0.22f);
                            drawBox(pose, vc, 0xF000F0, 0xFFFBBF24, s * 0.23f - 0.02f, -0.78f, 0.22f, s * 0.23f + 0.02f, -0.68f, 0.34f);
                        }
                    }
                    case SPROUT -> {
                        drawBox(pose, vc, light, 0xFF15803D, -0.02f, -0.66f, -0.02f, 0.02f, -0.50f, 0.02f);
                        drawBox(pose, vc, 0xF000F0, 0xFF4ADE80, -0.14f, -0.71f, -0.05f, -0.01f, -0.64f, 0.05f);
                        drawBox(pose, vc, 0xF000F0, 0xFF86EFAC, 0.01f, -0.73f, -0.05f, 0.14f, -0.66f, 0.05f);
                    }
                    default -> {}
                }
            });
            poseStack.popPose();
        }

        AccessoriesModule.FaceItem face = acc.faceItem.get();
        if (face != AccessoriesModule.FaceItem.NONE) {
            AccessoriesModule.ItemTransform ft = acc.getFaceTransform(face);
            poseStack.pushPose();
            this.getParentModel().head.translateAndRotate(poseStack);
            if (ft != null) {
                poseStack.translate(0.0f, ft.y().get().floatValue(), ft.z().get().floatValue());
                float sc = ft.scale().get().floatValue();
                poseStack.scale(sc, sc, sc);
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                switch (face) {
                    case SUNGLASSES -> {
                        drawBox(pose, vc, 0xF000F0, 0xEE0B0D14, -0.24f, -0.28f, -0.285f, -0.04f, -0.17f, -0.255f);
                        drawBox(pose, vc, 0xF000F0, 0xEE0B0D14, 0.04f, -0.28f, -0.285f, 0.24f, -0.17f, -0.255f);
                        drawBox(pose, vc, light, 0xFF94A3B8, -0.25f, -0.29f, -0.27f, 0.25f, -0.26f, -0.25f);
                        drawBox(pose, vc, light, 0xFF64748B, -0.26f, -0.27f, -0.26f, -0.24f, -0.23f, 0.16f);
                        drawBox(pose, vc, light, 0xFF64748B, 0.24f, -0.27f, -0.26f, 0.26f, -0.23f, 0.16f);
                    }
                    case CYBER_VISOR -> {
                        drawBox(pose, vc, 0xF000F0, 0xCC22D3EE, -0.26f, -0.31f, -0.295f, 0.26f, -0.17f, -0.26f);
                        drawBox(pose, vc, light, 0xFF1E293B, -0.275f, -0.32f, -0.28f, -0.24f, -0.16f, 0.14f);
                        drawBox(pose, vc, light, 0xFF1E293B, 0.24f, -0.32f, -0.28f, 0.275f, -0.16f, 0.14f);
                        drawBox(pose, vc, 0xF000F0, 0xFF38BDF8, -0.29f, -0.29f, -0.08f, -0.26f, -0.19f, 0.06f);
                        drawBox(pose, vc, 0xF000F0, 0xFF38BDF8, 0.26f, -0.29f, -0.08f, 0.29f, -0.19f, 0.06f);
                    }
                    case FOX_MASK -> {
                        drawBox(pose, vc, light, 0xFFF8FAFC, -0.23f, -0.46f, -0.29f, 0.23f, -0.10f, -0.255f);
                        drawBox(pose, vc, 0xF000F0, 0xFFEF4444, -0.20f, -0.44f, -0.295f, -0.06f, -0.34f, -0.285f);
                        drawBox(pose, vc, 0xF000F0, 0xFFEF4444, 0.06f, -0.44f, -0.295f, 0.20f, -0.34f, -0.285f);
                        drawBox(pose, vc, light, 0xFF0F172A, -0.18f, -0.27f, -0.296f, -0.06f, -0.21f, -0.285f);
                        drawBox(pose, vc, light, 0xFF0F172A, 0.06f, -0.27f, -0.296f, 0.18f, -0.21f, -0.285f);
                        drawBox(pose, vc, light, 0xFFF8FAFC, -0.21f, -0.58f, -0.28f, -0.09f, -0.46f, -0.23f);
                        drawBox(pose, vc, light, 0xFFF8FAFC, 0.09f, -0.58f, -0.28f, 0.21f, -0.46f, -0.23f);
                    }
                    case ROSE_MOUTH -> {
                        drawBox(pose, vc, light, 0xFF15803D, 0.04f, -0.10f, -0.42f, 0.07f, -0.07f, -0.24f);
                        drawBox(pose, vc, 0xF000F0, 0xFFE11D48, 0.01f, -0.13f, -0.48f, 0.10f, -0.05f, -0.41f);
                    }
                    case CLOWN_NOSE -> {
                        drawBox(pose, vc, 0xF000F0, 0xFFEF4444, -0.055f, -0.21f, -0.34f, 0.055f, -0.11f, -0.25f);
                    }
                    case EYEPATCH -> {
                        drawBox(pose, vc, light, 0xFF0F172A, 0.04f, -0.28f, -0.282f, 0.21f, -0.16f, -0.255f);
                        drawBox(pose, vc, light, 0xFF1E293B, -0.26f, -0.26f, -0.265f, 0.26f, -0.23f, 0.26f);
                    }
                    default -> {}
                }
            });
            poseStack.popPose();
        }

        AccessoriesModule.TailItem tail = acc.tailItem.get();
        if (tail != AccessoriesModule.TailItem.NONE) {
            poseStack.pushPose();
            this.getParentModel().body.translateAndRotate(poseStack);
            poseStack.translate(0.0f, 0.62f, 0.14f);
            float ts = acc.tailScale.get().floatValue();
            poseStack.scale(ts, ts, ts);
            float wag = (float) Math.sin(System.currentTimeMillis() / 240.0) * 14.0f;
            poseStack.mulPose(Axis.YP.rotationDegrees(wag));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                switch (tail) {
                    case NINE_TAILS -> {
                        for (int i = -2; i <= 2; i++) {
                            float ox = i * 0.12f;
                            float oz = 0.20f + Math.abs(i) * 0.04f;
                            drawBox(pose, vc, light, 0xFFF8FAFC, ox - 0.05f, -0.08f, 0.0f, ox + 0.05f, 0.06f, oz + 0.18f);
                            drawBox(pose, vc, 0xF000F0, 0xFFF472B6, ox - 0.04f, -0.14f, oz + 0.14f, ox + 0.04f, -0.02f, oz + 0.30f);
                        }
                    }
                    case DEVIL_TAIL -> {
                        drawBox(pose, vc, light, 0xFF7F1D1D, -0.03f, -0.04f, 0.0f, 0.03f, 0.02f, 0.34f);
                        drawBox(pose, vc, 0xF000F0, 0xFFEF4444, -0.07f, -0.07f, 0.32f, 0.07f, 0.05f, 0.44f);
                    }
                    case DRAGON_TAIL -> {
                        drawBox(pose, vc, light, 0xFF1E1B2E, -0.05f, -0.05f, 0.0f, 0.05f, 0.05f, 0.42f);
                        drawBox(pose, vc, 0xF000F0, 0xFFA855F7, -0.03f, -0.09f, 0.16f, 0.03f, -0.04f, 0.24f);
                        drawBox(pose, vc, 0xF000F0, 0xFFA855F7, -0.03f, -0.09f, 0.32f, 0.03f, -0.04f, 0.40f);
                    }
                    default -> {}
                }
            });
            poseStack.popPose();
        }
    }

    private void renderHighResWings(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                    AvatarRenderState state, DragonWingsModule wings) {
        long now = System.currentTimeMillis();
        int tint = wings.getTintColor(now);
        float sc = wings.scale.get().floatValue();
        float speed = wings.flapSpeed.get().floatValue();
        Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", wings.wingStyle.get().texPath);

        avianWings.setupProceduralAnim(state.isFallFlying, state.walkAnimationSpeed > 0.1f, speed, now);

        poseStack.pushPose();
        poseStack.translate(0.0f, -0.0625f, 0.0f);
        if (!state.chestEquipment.isEmpty()) {
            poseStack.translate(0.0f, 0.0f, 0.0625f);
        }
        this.getParentModel().body.translateAndRotate(poseStack);
        poseStack.scale(sc, sc, sc);
        avianWings.submit(poseStack, collector, tex, packedLight, tint, state.outlineColor);
        poseStack.popPose();
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

        if (preset == FlightModelModule.FlightPreset.MAGIC_BROOM) {
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.38f, 0.0f);
            poseStack.scale(sc, sc, sc);
            float hover = (float) Math.sin((now % 3000L) / 3000.0 * Math.PI * 2.0) * 0.03f;
            poseStack.translate(0.0f, hover, 0.0f);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                // Multi-segment dark oak broomstick shaft
                drawBox(pose, vc, packedLight, 0xFF5C3A21, -0.045f, -0.045f, -1.05f, 0.045f, 0.045f, 0.62f);
                drawBox(pose, vc, packedLight, 0xFF78350F, -0.035f, -0.085f, -1.18f, 0.035f, -0.015f, -1.02f);
                // Ornate gold binding collar
                drawBox(pose, vc, 0xF000F0, 0xFFFACC15, -0.075f, -0.075f, 0.54f, 0.075f, 0.075f, 0.64f);
                // Layered straw bristles tail
                drawBox(pose, vc, packedLight, 0xFFD97706, -0.14f, -0.12f, 0.64f, 0.14f, 0.12f, 1.04f);
                drawBox(pose, vc, packedLight, 0xFFF59E0B, -0.18f, -0.15f, 0.80f, 0.18f, 0.15f, 1.24f);
                drawBox(pose, vc, packedLight, 0xFFB45309, -0.11f, -0.09f, 1.18f, 0.11f, 0.09f, 1.34f);
            });
            poseStack.popPose();
            return;
        }

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
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                    drawDoubleUvQuad(pose, vc, 0xF000F0, col,
                            -r, 0f, -r, 0f, 0f,
                            r, 0f, -r, 1f, 0f,
                            r, 0f, r, 1f, 1f,
                            -r, 0f, r, 0f, 1f));
            poseStack.popPose();
        }
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
