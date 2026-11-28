package com.ye.mod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.render.stand.YeBedrockGeoModel;
import com.ye.mod.render.wings.YeAvianWingsModel;
import com.ye.mod.render.wings.YePvpDragonWingsModel;
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

/**
 * High-resolution 3D Cosmetic Layer on AvatarRenderer:
 * - Correctly oriented Lunar/Badlion PvP DragonWings + 4-joint Fantastic Avian Wings (with Only-When-Gliding support)
 * - 7 Authentic JoJo 3D Stand Models + Player Avatar Stand
 * - 10 Sculpted 3D Flight Mounts (Cloud Nimbus, Mecha Jet, Star Astrolabe, Giant Shuriken, Paper Glider, Sword, Broom, Dragon, Phantom)
 * - Head & Face 3D Accessories (all tails removed!)
 * - 512x512 Ultra-HD Soul Rings
 */
public class YeCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier DRAGON_TEX = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final Identifier PHANTOM_TEX = Identifier.withDefaultNamespace("textures/entity/phantom.png");

    private final EnderDragonModel dragonModel;
    private final PhantomModel phantomModel;
    private final PlayerModel standPlayerModel;
    private final YeAvianWingsModel avianWings = new YeAvianWingsModel();
    private final YePvpDragonWingsModel pvpDragonWings = new YePvpDragonWingsModel();
    private final EnderDragonRenderState dragonState = new EnderDragonRenderState();
    private final PhantomRenderState phantomState = new PhantomRenderState();

    public YeCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet modelSet) {
        super(parent);
        this.dragonModel = new EnderDragonModel(modelSet.bakeLayer(ModelLayers.ENDER_DRAGON));
        this.phantomModel = new PhantomModel(modelSet.bakeLayer(ModelLayers.PHANTOM));
        this.standPlayerModel = new PlayerModel(modelSet.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        YeHighResTextures.ensureInit();

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();

        DragonWingsModule wings = ModuleManager.get(DragonWingsModule.class);
        boolean forceWingsPreview = com.ye.mod.ui.YePreviewRenderer.isPreviewingWings;
        if (wings != null && (forceWingsPreview || wings.shouldRenderWings(state.isFallFlying)) && (!wings.onlySelf.get() || isSelf)) {
            renderWings(poseStack, collector, packedLight, state, wings);
        }

        AccessoriesModule acc = ModuleManager.get(AccessoriesModule.class);
        if (acc != null && acc.isEnabled() && (!acc.onlySelf.get() || isSelf)) {
            renderHeadAndFaceAccessory(poseStack, collector, packedLight, acc);
        }

        FlightModelModule flight = ModuleManager.get(FlightModelModule.class);
        boolean forceMountPreview = com.ye.mod.ui.YePreviewRenderer.isPreviewingMount;
        if (flight != null && (forceMountPreview || (flight.isEnabled() && state.isFallFlying)) && (!flight.onlySelf.get() || isSelf)) {
            renderFlightMount(poseStack, collector, packedLight, state, flight);
        }

        if (isSelf) {
            SoulRingsModule rings = ModuleManager.get(SoulRingsModule.class);
            if (rings != null && rings.isEnabled() && (rings.showWhenGliding.get() || !state.isFallFlying)
                    && !(rings.hideInFirstPerson.get() && mc.options.getCameraType().isFirstPerson())) {
                renderSoulRings(poseStack, collector, rings);
            }
        }

        StandBuddyModule stand = ModuleManager.get(StandBuddyModule.class);
        if (stand != null && stand.isEnabled() && (!stand.onlySelf.get() || isSelf)
                && !(isSelf && mc.options.getCameraType().isFirstPerson())) {
            renderStandBuddy(poseStack, collector, state, stand, yRot, xRot);
        }
    }

    private void renderWings(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                             AvatarRenderState state, DragonWingsModule wings) {
        long now = System.currentTimeMillis();
        int tint = wings.getTintColor(now);
        float sc = wings.scale.get().floatValue();
        float speed = wings.flapSpeed.get().floatValue();

        poseStack.pushPose();
        poseStack.translate(0.0f, -0.0625f, 0.0f);
        if (!state.chestEquipment.isEmpty() && !wings.hideArmor.get()) {
            poseStack.translate(0.0f, 0.0f, 0.0625f);
        }
        this.getParentModel().body.translateAndRotate(poseStack);
        poseStack.scale(sc, sc, sc);

        if (wings.wingCategory.get() == DragonWingsModule.WingCategory.PVP_DRAGON) {
            pvpDragonWings.submit(poseStack, collector, YePvpDragonWingsModel.PVP_DRAGON_TEX,
                    packedLight, tint, state.isFallFlying, state.walkAnimationSpeed > 0.1f, speed, now, state.outlineColor);
        } else {
            Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", wings.wingStyle.get().texPath);
            avianWings.setupProceduralAnim(state.isFallFlying, state.walkAnimationSpeed > 0.1f, speed, now);
            avianWings.submit(poseStack, collector, tex, packedLight, tint, state.outlineColor);
        }
        poseStack.popPose();
    }

    private void renderStandBuddy(PoseStack poseStack, SubmitNodeCollector collector,
                                  AvatarRenderState state, StandBuddyModule stand, float yRot, float xRot) {
        long now = System.currentTimeMillis();
        float bob = (float) Math.sin(now / 360.0) * 0.07f;
        float sc = stand.scale.get().floatValue();
        int alphaByte = Math.max(40, Math.min(255, Math.round(stand.alpha.get().floatValue() * 255.0f)));
        boolean attacking = state.attackTime > 0.0f;

        float ox = stand.offsetX.get().floatValue();
        float oy = stand.offsetY.get().floatValue();
        float oz = stand.offsetZ.get().floatValue();
        if (attacking) {
            ox *= 0.45f;
            oz = -0.55f;
        }

        StandBuddyModule.StandModelType mType = stand.modelType.get();
        poseStack.pushPose();
        poseStack.translate(ox, oy + bob, oz);
        poseStack.scale(sc, sc, sc);

        if (mType == StandBuddyModule.StandModelType.PLAYER_AVATAR) {
            standPlayerModel.setupAnim(state);
            if (attacking) {
                float rush = (float) Math.sin(now / 30.0) * 0.85f;
                standPlayerModel.rightArm.xRot = -1.60f + rush;
                standPlayerModel.leftArm.xRot = -1.60f - rush;
            } else {
                standPlayerModel.rightArm.xRot = -0.38f;
                standPlayerModel.leftArm.xRot = -0.32f;
            }
            int tintRgb = stand.syncGlobalColor.get() ? GlobalColorModule.resolveRgb(true, 0xA5F3FC, 1) : 0x00A5F3FC;
            int col = (alphaByte << 24) | tintRgb;
            collector.submitModelPart(
                    standPlayerModel.root(),
                    poseStack,
                    RenderTypes.entityTranslucent(state.skin.body().texturePath()),
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY,
                    null, false, false, col, null, state.outlineColor
            );
        } else {
            YeBedrockGeoModel geo = YeBedrockGeoModel.getOrLoad(mType.geoPath);
            geo.setupStandAnim(yRot, xRot, attacking, now);
            Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", mType.texPath);
            int tintRgb = stand.syncGlobalColor.get() ? GlobalColorModule.resolveRgb(true, 0xFFFFFF, 1) : 0x00FFFFFF;
            int col = (alphaByte << 24) | tintRgb;
            geo.submit(poseStack, collector, tex, 0xF000F0, col);
        }
        poseStack.popPose();
    }

    private void renderHeadAndFaceAccessory(PoseStack poseStack, SubmitNodeCollector collector, int light, AccessoriesModule acc) {
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
                    case BALLOON -> renderSmoothHighPolyBalloon(pose, vc, light);
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
                    case CLOWN_NOSE -> drawBox(pose, vc, 0xF000F0, 0xFFEF4444, -0.055f, -0.21f, -0.34f, 0.055f, -0.11f, -0.25f);
                    case EYEPATCH -> {
                        drawBox(pose, vc, light, 0xFF0F172A, 0.04f, -0.28f, -0.282f, 0.21f, -0.16f, -0.255f);
                        drawBox(pose, vc, light, 0xFF1E293B, -0.26f, -0.26f, -0.265f, 0.26f, -0.23f, 0.26f);
                    }
                    default -> {}
                }
            });
            poseStack.popPose();
        }
    }

    private void renderFlightMount(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   AvatarRenderState state, FlightModelModule flight) {
        FlightModelModule.FlightPreset preset = flight.preset.get();
        if (preset == FlightModelModule.FlightPreset.SUPERMAN) {
            return;
        }

        float sc = flight.modelScale.get().floatValue();
        float wSpeed = flight.wingSpeed.get().floatValue();
        boolean trail = flight.energyTrail.get();
        float offY = flight.customMode.get() ? flight.offsetY.get().floatValue() : 0.0f;
        float offZ = flight.customMode.get() ? flight.offsetZ.get().floatValue() : 0.0f;
        float tilt = flight.customMode.get() ? flight.tiltAngle.get().floatValue() : 0.0f;
        long now = System.currentTimeMillis();
        int accentArgb = GlobalColorModule.resolveArgb(flight.syncGlobalColor.get(), 0xFF38BDF8, 3);
        int goldArgb = GlobalColorModule.resolveArgb(flight.syncGlobalColor.get(), 0xFFFACC15, 1);

        poseStack.pushPose();
        poseStack.translate(0.0f, offY, offZ);
        if (Math.abs(tilt) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
        }

        switch (preset) {
            case CLOUD_NIMBUS -> {
                // Soft Fluffy Sun Wukong Somersault Cloud (悟空蓬松筋斗云)
                float bob = (float) Math.sin((now / 280.0) * wSpeed) * 0.05f;
                poseStack.translate(0.0f, 0.92f + bob, 0.0f);
                poseStack.scale(sc, sc, sc);
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                    int cPure = 0xF5FFFBEB;
                    int cSoft = 0xEEFDE68A;
                    int cGold = (0xE8 << 24) | (goldArgb & 0x00FFFFFF);
                    // Fluffy multi-puff main cloud cluster
                    drawBox(pose, vc, 0xF000F0, cPure, -0.44f, -0.16f, -0.52f, 0.44f, 0.14f, 0.44f);
                    drawBox(pose, vc, 0xF000F0, cSoft, -0.58f, -0.10f, -0.38f, 0.58f, 0.16f, 0.34f);
                    drawBox(pose, vc, 0xF000F0, cPure, -0.30f, -0.24f, -0.36f, 0.30f, 0.06f, 0.24f);
                    // Front & side soft rounded puffs
                    drawBox(pose, vc, 0xF000F0, cPure, -0.32f, -0.20f, -0.74f, 0.32f, 0.10f, -0.42f);
                    drawBox(pose, vc, 0xF000F0, cGold, -0.22f, -0.26f, -0.66f, 0.22f, -0.04f, -0.46f);
                    drawBox(pose, vc, 0xF000F0, cPure, -0.72f, -0.14f, -0.24f, -0.42f, 0.12f, 0.24f);
                    drawBox(pose, vc, 0xF000F0, cPure, 0.42f, -0.14f, -0.24f, 0.72f, 0.12f, 0.24f);
                    drawBox(pose, vc, 0xF000F0, cSoft, -0.64f, -0.06f, -0.46f, -0.36f, 0.14f, -0.12f);
                    drawBox(pose, vc, 0xF000F0, cSoft, 0.36f, -0.06f, -0.46f, 0.64f, 0.14f, -0.12f);
                    if (trail) {
                        for (int i = 1; i <= 4; i++) {
                            float tz = 0.42f + i * 0.28f;
                            float ty = (float) Math.sin((now / 190.0) * wSpeed - i) * 0.055f;
                            float hw = Math.max(0.10f, 0.34f - i * 0.06f);
                            int a = Math.max(70, 235 - i * 42);
                            drawBox(pose, vc, 0xF000F0, (a << 24) | 0x00FFFBEB, -hw, ty - 0.09f, tz, hw, ty + 0.09f, tz + 0.24f);
                        }
                    }
                });
            }
            case MECHA_JET -> {
                // Back-mounted Armored Mecha Thruster Pack + Swept Hard-Light Blades + Dual Plasma Cones!
                poseStack.translate(0.0f, 0.22f, 0.18f);
                poseStack.scale(sc, sc, sc);
                float pulse = 0.85f + 0.28f * (float) Math.sin((now / 55.0) * wSpeed);
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                    int armorDark = 0xFF1E293B;
                    int armorSilver = 0xFFCBD5E1;
                    int plasma = (0xEE << 24) | (accentArgb & 0x00FFFFFF);
                    int plasmaCore = 0xFFFFFFFF;

                    // Spine reactor core
                    drawBox(pose, vc, packedLight, armorDark, -0.20f, -0.10f, 0.0f, 0.20f, 0.44f, 0.15f);
                    drawBox(pose, vc, 0xF000F0, plasma, -0.08f, 0.04f, 0.14f, 0.08f, 0.28f, 0.18f);

                    for (int s : new int[]{-1, 1}) {
                        // Angled twin turbine pods
                        float px = s * 0.28f;
                        drawBox(pose, vc, packedLight, armorSilver, px - 0.09f, -0.08f, 0.04f, px + 0.09f, 0.36f, 0.22f);
                        drawBox(pose, vc, packedLight, armorDark, px - 0.07f, 0.36f, 0.06f, px + 0.07f, 0.46f, 0.20f);
                        // Swept hard-light aero blades
                        drawBox(pose, vc, packedLight, armorSilver, s * 0.36f - 0.05f, -0.04f, 0.08f, s * 0.92f + 0.05f, 0.04f, 0.16f);
                        drawBox(pose, vc, 0xF000F0, plasma, s * 0.38f - 0.04f, 0.04f, 0.10f, s * 1.15f + 0.04f, 0.14f, 0.14f);

                        if (trail) {
                            float flameLen = 0.58f * pulse;
                            drawBox(pose, vc, 0xF000F0, plasma, px - 0.065f, 0.46f, 0.07f, px + 0.065f, 0.46f + flameLen, 0.19f);
                            drawBox(pose, vc, 0xF000F0, plasmaCore, px - 0.03f, 0.46f, 0.10f, px + 0.03f, 0.46f + flameLen * 0.65f, 0.16f);
                        }
                    }
                });
            }
            case STAR_ASTROLABE -> {
                // Dual Counter-Rotating 512x512 Star Astrolabe Discs + Floating Crystal Nodes under feet!
                poseStack.translate(0.0f, 1.48f, 0.0f);
                poseStack.scale(sc, sc, sc);
                float spin1 = (float) (((now / 18.0) * wSpeed) % 360.0);
                float spin2 = -spin1 * 1.45f;

                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(spin1));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                        drawDoubleUvQuad(pose, vc, 0xF000F0, accentArgb,
                                -1.15f, 0.0f, -1.15f, 0f, 0f,
                                1.15f, 0.0f, -1.15f, 1f, 0f,
                                1.15f, 0.0f, 1.15f, 1f, 1f,
                                -1.15f, 0.0f, 1.15f, 0f, 1f));
                poseStack.popPose();

                poseStack.pushPose();
                poseStack.translate(0.0f, -0.05f, 0.0f);
                poseStack.mulPose(Axis.YP.rotationDegrees(spin2));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                        drawDoubleUvQuad(pose, vc, 0xF000F0, goldArgb,
                                -0.78f, 0.0f, -0.78f, 0f, 0f,
                                0.78f, 0.0f, -0.78f, 1f, 0f,
                                0.78f, 0.0f, 0.78f, 1f, 1f,
                                -0.78f, 0.0f, 0.78f, 0f, 1f));
                if (trail) {
                    collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                        for (int i = 0; i < 4; i++) {
                            float ang = (float) (i * Math.PI * 0.5);
                            float cx = (float) Math.cos(ang) * 0.92f;
                            float cz = (float) Math.sin(ang) * 0.92f;
                            drawBox(pose, vc, 0xF000F0, goldArgb, cx - 0.05f, -0.10f, cz - 0.05f, cx + 0.05f, 0.06f, cz + 0.05f);
                        }
                    });
                }
                poseStack.popPose();
            }
            case GIANT_SHURIKEN -> {
                // Iconic Naruto 4-Bladed Pinwheel Shuriken with hollow center ring & swept silver edges!
                poseStack.translate(0.0f, 1.48f, 0.0f);
                poseStack.scale(sc, sc, sc);
                float spin = (float) (((now / 6.5) * wSpeed) % 360.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(spin));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                    int steelDark = 0xFF1E293B;
                    int steelEdge = 0xFFF1F5F9;
                    // Hollow center ring (4 frame bars)
                    drawBox(pose, vc, packedLight, steelDark, -0.24f, -0.024f, -0.24f, 0.24f, 0.024f, -0.13f);
                    drawBox(pose, vc, packedLight, steelDark, -0.24f, -0.024f, 0.13f, 0.24f, 0.024f, 0.24f);
                    drawBox(pose, vc, packedLight, steelDark, -0.24f, -0.024f, -0.13f, -0.13f, 0.024f, 0.13f);
                    drawBox(pose, vc, packedLight, steelDark, 0.13f, -0.024f, -0.13f, 0.24f, 0.024f, 0.13f);
                    // 4 swept Naruto-style pinwheel blades
                    for (int b = 0; b < 4; b++) {
                        poseStack.pushPose();
                        poseStack.mulPose(Axis.YP.rotationDegrees(b * 90.0f));
                        var bp = poseStack.last();
                        drawBox(bp, vc, packedLight, steelDark, -0.12f, -0.018f, -0.95f, 0.10f, 0.018f, -0.24f);
                        drawBox(bp, vc, 0xF000F0, steelEdge, 0.06f, -0.012f, -1.16f, 0.20f, 0.012f, -0.32f);
                        drawBox(bp, vc, 0xF000F0, steelEdge, -0.04f, -0.012f, -1.24f, 0.12f, 0.012f, -0.92f);
                        poseStack.popPose();
                    }
                });
            }
            case PAPER_GLIDER -> {
                // Crisp Origami Paper Airplane / Delta Glider under feet!
                float sway = (float) Math.sin((now / 380.0) * wSpeed) * 4.5f;
                poseStack.translate(0.0f, 1.48f, 0.0f);
                poseStack.scale(sc, sc, sc);
                poseStack.mulPose(Axis.ZP.rotationDegrees(sway));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                    int paperWhite = 0xFFF8FAFC;
                    int paperFold = 0xFFCBD5E1;
                    // Sharp nose -> widening delta wings + center keel crease + upturned winglets!
                    drawBox(pose, vc, packedLight, paperFold, -0.03f, 0.0f, -1.25f, 0.03f, 0.16f, 0.85f);
                    for (int step = 0; step < 6; step++) {
                        float z0 = -1.20f + step * 0.34f;
                        float z1 = z0 + 0.35f;
                        float halfSpan = 0.10f + step * 0.17f;
                        drawBox(pose, vc, packedLight, paperWhite, -halfSpan, -0.015f, z0, halfSpan, 0.012f, z1);
                    }
                    // Accent stripe & upturned wingtips
                    drawBox(pose, vc, 0xF000F0, accentArgb, -0.95f, -0.12f, 0.35f, -0.91f, 0.0f, 0.85f);
                    drawBox(pose, vc, 0xF000F0, accentArgb, 0.91f, -0.12f, 0.35f, 0.95f, 0.0f, 0.85f);
                });
            }
            case MAGIC_BROOM -> {
                poseStack.translate(0.0f, 0.86f, 0.0f);
                poseStack.scale(sc, sc, sc);
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                    drawBox(pose, vc, packedLight, 0xFF4A2C16, -0.052f, -0.052f, -1.08f, 0.052f, 0.052f, 0.62f);
                    drawBox(pose, vc, packedLight, 0xFF6B3E1E, -0.042f, -0.105f, -1.24f, 0.042f, -0.015f, -1.04f);
                    drawBox(pose, vc, 0xF000F0, goldArgb, -0.062f, -0.115f, -1.28f, 0.062f, -0.055f, -1.20f);
                    drawBox(pose, vc, packedLight, 0xFF7F1D1D, -0.072f, -0.068f, -0.22f, 0.072f, 0.062f, 0.22f);
                    drawBox(pose, vc, 0xF000F0, goldArgb, -0.088f, -0.088f, 0.54f, 0.088f, 0.088f, 0.64f);
                    drawBox(pose, vc, 0xF000F0, 0xFFF59E0B, -0.125f, -0.115f, 0.78f, 0.125f, 0.115f, 0.84f);
                    drawBox(pose, vc, packedLight, 0xFFD97706, -0.145f, -0.125f, 0.64f, 0.145f, 0.125f, 1.04f);
                    drawBox(pose, vc, packedLight, 0xFFF59E0B, -0.195f, -0.165f, 0.84f, 0.195f, 0.165f, 1.28f);
                    drawBox(pose, vc, packedLight, 0xFFB45309, -0.125f, -0.105f, 1.22f, 0.125f, 0.105f, 1.44f);
                });
            }
            case MINI_DRAGON -> {
                poseStack.translate(0.0f, 1.35f, 0.0f);
                float dScale = sc * 0.24f;
                poseStack.scale(dScale, dScale, dScale);
                dragonState.flapTime = (now % 60000L) / 1000.0f * wSpeed;
                dragonState.ageInTicks = state.ageInTicks;
                collector.submitModel(dragonModel, dragonState, poseStack, RenderTypes.entityCutoutNoCull(DRAGON_TEX),
                        packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            }
            case MINI_PHANTOM -> {
                poseStack.translate(0.0f, 1.45f, 0.0f);
                float pScale = sc * 1.15f;
                poseStack.scale(pScale, pScale, pScale);
                phantomState.flapTime = (now % 60000L) / 1000.0f * wSpeed * 2.2f;
                phantomState.ageInTicks = state.ageInTicks;
                collector.submitModel(phantomModel, phantomState, poseStack, RenderTypes.entityCutoutNoCull(PHANTOM_TEX),
                        packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            }
            case SWORD_SURF -> {
                poseStack.translate(0.0f, 1.48f, 0.0f);
                poseStack.scale(sc, sc, sc);
                float spin = ((now % 4000L) / 4000.0f) * 360.0f * wSpeed;
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX), (pose, vc) -> {
                    drawBox(pose, vc, 0xF000F0, 0xFFE0F2FE, -0.055f, -0.03f, -1.12f, 0.055f, 0.03f, 0.55f);
                    drawBox(pose, vc, 0xF000F0, accentArgb, -0.145f, -0.015f, -1.00f, 0.145f, 0.015f, 0.55f);
                    drawBox(pose, vc, 0xF000F0, goldArgb, -0.36f, -0.045f, 0.53f, 0.36f, 0.045f, 0.65f);
                    drawBox(pose, vc, packedLight, 0xFF7C2D12, -0.048f, -0.035f, 0.65f, 0.048f, 0.035f, 1.00f);
                    drawBox(pose, vc, 0xF000F0, 0xFFA855F7, -0.075f, -0.055f, 1.00f, 0.075f, 0.055f, 1.12f);
                });
                if (trail) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(spin));
                    collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                            drawDoubleUvQuad(pose, vc, 0xF000F0, (0xCC << 24) | (accentArgb & 0x00FFFFFF),
                                    -0.75f, 0.07f, -0.75f, 0f, 0f,
                                    0.75f, 0.07f, -0.75f, 1f, 0f,
                                    0.75f, 0.07f, 0.75f, 1f, 1f,
                                    -0.75f, 0.07f, 0.75f, 0f, 1f));
                }
            }
            default -> {}
        }
        poseStack.popPose();
    }

    private void renderSoulRings(PoseStack poseStack, SubmitNodeCollector collector, SoulRingsModule rings) {
        int count = Math.max(1, Math.min(6, (int) Math.round(rings.ringCount.get())));
        float baseR = rings.radius.get().floatValue();
        float startY = rings.customMode.get() ? rings.baseHeight.get().floatValue() : 1.38f;
        float gapY = rings.customMode.get() ? rings.verticalGap.get().floatValue() : 0.24f;
        float bri = rings.brightness.get().floatValue();
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            double spd = rings.pickRingSpeed(i);
            float y = startY - i * gapY + (float) Math.sin((now / 420.0) * Math.abs(spd) + i) * 0.035f;
            float r = baseR + (i * 0.09f);
            int rawRgb = rings.pickRingRgb(i);
            int boostedRgb = scaleRgbWithWhiteHot(rawRgb, bri);
            int alphaMain = Math.max(55, Math.min(255, Math.round(Math.min(1.0f, bri) * 245.0f)));
            int col = (alphaMain << 24) | boostedRgb;
            float spin = (float) (((now / 15.0) * spd) % 360.0);

            poseStack.pushPose();
            poseStack.translate(0.0f, y, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));

            // 1) Crisp 1024x1024 Runic Disc (Base Translucent + Additive Emissive Eyes Pass for intense HDR brightness!)
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) ->
                    drawDoubleUvQuad(pose, vc, 0xF000F0, col,
                            -r, 0f, -r, 0f, 0f,
                            r, 0f, -r, 1f, 0f,
                            r, 0f, r, 1f, 1f,
                            -r, 0f, r, 0f, 1f));

            if (bri > 0.65f) {
                float glowStrength = Math.min(1.0f, (bri - 0.55f) / 1.85f);
                int eRgb = scaleRgbWithWhiteHot(rawRgb, glowStrength * 1.15f);
                int eCol = 0xFF000000 | eRgb;
                collector.submitCustomGeometry(poseStack, RenderTypes.eyes(YeHighResTextures.RING_ARRAY_TEX), (pose, vc) -> {
                    drawDoubleUvQuad(pose, vc, 0xF000F0, eCol,
                            -r, 0.002f, -r, 0f, 0f,
                            r, 0.002f, -r, 1f, 0f,
                            r, 0.002f, r, 1f, 1f,
                            -r, 0.002f, r, 0f, 1f);
                    if (bri > 1.75f) {
                        int superRgb = scaleRgbWithWhiteHot(0xFFFFFF, (bri - 1.75f) * 0.70f);
                        drawDoubleUvQuad(pose, vc, 0xF000F0, 0xFF000000 | superRgb,
                                -r * 1.015f, -0.002f, -r * 1.015f, 0f, 0f,
                                r * 1.015f, -0.002f, -r * 1.015f, 1f, 0f,
                                r * 1.015f, -0.002f, r * 1.015f, 1f, 1f,
                                -r * 1.015f, -0.002f, r * 1.015f, 0f, 1f);
                    }
                });
            }

            // 2) Smooth 48-segment 3D Curved Torus Rim (gives real sculptural 3D volume from every camera angle!)
            float majorR = r * 0.905f;
            float minorR = 0.018f + Math.min(0.012f, bri * 0.005f);
            int coreCol = (0xFA << 24) | scaleRgbWithWhiteHot(boostedRgb, 1.35f);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(YeHighResTextures.SWORD_RUNE_TEX),
                    (pose, vc) -> drawSmoothTorus(pose, vc, majorR, minorR, 48, 8, coreCol));

            poseStack.popPose();
        }
    }

    private static int scaleRgbWithWhiteHot(int rgb, float mult) {
        float r = ((rgb >> 16) & 0xFF) * mult;
        float g = ((rgb >> 8) & 0xFF) * mult;
        float b = (rgb & 0xFF) * mult;
        // Spill excess HDR energy across channels so high brightness turns incandescent white-gold/cyan!
        float overflow = Math.max(0f, (Math.max(r, Math.max(g, b)) - 255f) * 0.42f);
        int ir = Math.max(0, Math.min(255, Math.round(r + overflow)));
        int ig = Math.max(0, Math.min(255, Math.round(g + overflow)));
        int ib = Math.max(0, Math.min(255, Math.round(b + overflow)));
        return (ir << 16) | (ig << 8) | ib;
    }

    private static void drawSmoothTorus(PoseStack.Pose pose, VertexConsumer vc, float majorR, float minorR,
                                        int segU, int segV, int argb) {
        for (int i = 0; i < segU; i++) {
            double a0 = (i / (double) segU) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segU) * Math.PI * 2.0;
            float cosA0 = (float) Math.cos(a0), sinA0 = (float) Math.sin(a0);
            float cosA1 = (float) Math.cos(a1), sinA1 = (float) Math.sin(a1);

            for (int j = 0; j < segV; j++) {
                double b0 = (j / (double) segV) * Math.PI * 2.0;
                double b1 = ((j + 1) / (double) segV) * Math.PI * 2.0;
                float cosB0 = (float) Math.cos(b0), sinB0 = (float) Math.sin(b0);
                float cosB1 = (float) Math.cos(b1), sinB1 = (float) Math.sin(b1);

                float r0 = majorR + minorR * cosB0;
                float r1 = majorR + minorR * cosB1;
                float y0 = minorR * sinB0;
                float y1 = minorR * sinB1;

                emitVertex(pose, vc, r0 * cosA0, y0, r0 * sinA0, argb, 0.5f, 0.5f, 0xF000F0, cosB0 * cosA0, sinB0, cosB0 * sinA0);
                emitVertex(pose, vc, r0 * cosA1, y0, r0 * sinA1, argb, 0.5f, 0.5f, 0xF000F0, cosB0 * cosA1, sinB0, cosB0 * sinA1);
                emitVertex(pose, vc, r1 * cosA1, y1, r1 * sinA1, argb, 0.5f, 0.5f, 0xF000F0, cosB1 * cosA1, sinB1, cosB1 * sinA1);
                emitVertex(pose, vc, r1 * cosA0, y1, r1 * sinA0, argb, 0.5f, 0.5f, 0xF000F0, cosB1 * cosA0, sinB1, cosB1 * sinA0);
            }
        }
    }

    /**
     * Smooth 28x18 Curved 3D Lathe Balloon + Knot Flange + Gently Swaying Catenary String!
     */
    private static void renderSmoothHighPolyBalloon(PoseStack.Pose pose, VertexConsumer vc, int light) {
        long now = System.currentTimeMillis();
        float swayX = (float) Math.sin(now / 650.0) * 0.045f;
        float bobY = (float) Math.cos(now / 520.0) * 0.03f;
        float cx = 0.38f + swayX;
        float cy = -1.28f + bobY;
        float cz = 0.08f;

        int baseRgb = GlobalColorModule.resolveRgb(true, 0xA855F7, 2);
        int balloonCol = 0xFA000000 | baseRgb;
        int stringCol = 0xFFE2E8F0;

        final int segU = 28;
        final int segV = 18;
        for (int iv = 0; iv < segV; iv++) {
            double v0 = iv / (double) segV;
            double v1 = (iv + 1) / (double) segV;
            float[] prof0 = balloonProfile(v0);
            float[] prof1 = balloonProfile(v1);

            for (int iu = 0; iu < segU; iu++) {
                double p0 = (iu / (double) segU) * Math.PI * 2.0;
                double p1 = ((iu + 1) / (double) segU) * Math.PI * 2.0;
                float cp0 = (float) Math.cos(p0), sp0 = (float) Math.sin(p0);
                float cp1 = (float) Math.cos(p1), sp1 = (float) Math.sin(p1);

                emitVertex(pose, vc, cx + prof0[0] * cp0, cy + prof0[1], cz + prof0[0] * sp0, balloonCol, 0.5f, 0.5f, light, cp0 * prof0[2], prof0[3], sp0 * prof0[2]);
                emitVertex(pose, vc, cx + prof0[0] * cp1, cy + prof0[1], cz + prof0[0] * sp1, balloonCol, 0.5f, 0.5f, light, cp1 * prof0[2], prof0[3], sp1 * prof0[2]);
                emitVertex(pose, vc, cx + prof1[0] * cp1, cy + prof1[1], cz + prof1[0] * sp1, balloonCol, 0.5f, 0.5f, light, cp1 * prof1[2], prof1[3], sp1 * prof1[2]);
                emitVertex(pose, vc, cx + prof1[0] * cp0, cy + prof1[1], cz + prof1[0] * sp0, balloonCol, 0.5f, 0.5f, light, cp0 * prof1[2], prof1[3], sp0 * prof1[2]);
            }
        }

        // Smooth curved string from balloon knot (cx, cy + 0.30, cz) down to shoulder/head side (0.20, -0.46, 0.04)
        final int strSegs = 10;
        float sx0 = cx, sy0 = cy + 0.31f, sz0 = cz;
        float sxEnd = 0.22f, syEnd = -0.46f, szEnd = 0.05f;
        float rStr = 0.0065f;
        for (int s = 0; s < strSegs; s++) {
            float t0 = s / (float) strSegs;
            float t1 = (s + 1) / (float) strSegs;
            float px0 = sx0 + (sxEnd - sx0) * t0 + (float) Math.sin(t0 * Math.PI) * 0.025f;
            float py0 = sy0 + (syEnd - sy0) * (t0 * t0 * 0.25f + t0 * 0.75f);
            float pz0 = sz0 + (szEnd - sz0) * t0;

            float px1 = sx0 + (sxEnd - sx0) * t1 + (float) Math.sin(t1 * Math.PI) * 0.025f;
            float py1 = sy0 + (syEnd - sy0) * (t1 * t1 * 0.25f + t1 * 0.75f);
            float pz1 = sz0 + (szEnd - sz0) * t1;

            drawBox(pose, vc, 0xF000F0, stringCol,
                    Math.min(px0, px1) - rStr, Math.min(py0, py1), Math.min(pz0, pz1) - rStr,
                    Math.max(px0, px1) + rStr, Math.max(py0, py1), Math.max(pz0, pz1) + rStr);
        }
    }

    // Returns {radius, localY, normRadial, normY} for v in [0..1] (0 = top crown, 0.90 = pinch neck, 1.0 = flared knot)
    private static float[] balloonProfile(double v) {
        if (v <= 0.90) {
            double u = v / 0.90; // 0..1 from top pole (-0.28) to neck (+0.27)
            double th = u * Math.PI;
            double taper = 1.0 - 0.22 * Math.pow(u, 1.8);
            float r = (float) (Math.sin(th) * 0.245 * taper);
            float y = (float) (-Math.cos(th) * 0.275);
            float nr = (float) Math.sin(th);
            float ny = (float) (-Math.cos(th));
            return new float[]{r, y, nr, ny};
        } else {
            double k = (v - 0.90) / 0.10; // Flared balloon tie knot!
            float r = (float) (0.012 + k * 0.028);
            float y = (float) (0.275 + k * 0.036);
            return new float[]{r, y, 0.85f, 0.52f};
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
}
