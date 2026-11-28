package com.ye.mod.render.wings;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Rock-Solid Baked ModelPart Lunar/Badlion PvP Dragon Wings:
 * Uses explicit symmetrical left (+X) & right (-X) ModelPart bone hierarchy (zero negative scale flips!)
 * anchored firmly to the player's upper-back shoulder blades with smooth cambered 3D dragon membranes!
 */
public class YePvpDragonWingsModel {
    public static final Identifier PVP_DRAGON_TEX = Identifier.fromNamespaceAndPath("ye-mod", "textures/wings/pvp_dragon.png");
    private static final float DEG2RAD = (float) (Math.PI / 180.0);

    private final ModelPart root;
    private final ModelPart leftProximal;
    private final ModelPart rightProximal;
    private final ModelPart leftDistal;
    private final ModelPart rightDistal;

    public YePvpDragonWingsModel() {
        this.root = createLayer().bakeRoot();
        this.leftProximal = root.getChild("left_proximal");
        this.rightProximal = root.getChild("right_proximal");
        this.leftDistal = leftProximal.getChild("left_distal");
        this.rightDistal = rightProximal.getChild("right_distal");
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Left shoulder blade (+X outward, +Z behind torso)
        PartDefinition lp = root.addOrReplaceChild("left_proximal",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0.0F, -1.0F, -0.8F, 10.0F, 2.0F, 2.0F)
                        // Sculpted dragon thumb claw at elbow
                        .texOffs(0, 5).addBox(8.8F, -2.6F, -1.2F, 1.2F, 2.0F, 1.2F),
                PartPose.offset(1.6F, 2.4F, 2.1F));

        // Right shoulder blade (-X outward, +Z behind torso)
        PartDefinition rp = root.addOrReplaceChild("right_proximal",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-10.0F, -1.0F, -0.8F, 10.0F, 2.0F, 2.0F)
                        .texOffs(0, 5).addBox(-10.0F, -2.6F, -1.2F, 1.2F, 2.0F, 1.2F),
                PartPose.offset(-1.6F, 2.4F, 2.1F));

        // Left outer wingtip (+X from elbow)
        lp.addOrReplaceChild("left_distal",
                CubeListBuilder.create()
                        .texOffs(0, 5).addBox(0.0F, -0.6F, -0.6F, 10.5F, 1.2F, 1.2F),
                PartPose.offset(9.8F, 0.0F, 0.0F));

        // Right outer wingtip (-X from elbow)
        rp.addOrReplaceChild("right_distal",
                CubeListBuilder.create()
                        .texOffs(0, 5).addBox(-10.5F, -0.6F, -0.6F, 10.5F, 1.2F, 1.2F),
                PartPose.offset(-9.8F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 30, 30);
    }

    public void setupAnim(boolean gliding, boolean moving, float flapSpeed, long nowMs) {
        float rate = flapSpeed * (gliding ? 1.55f : (moving ? 1.25f : 0.85f));
        float t = ((nowMs % 120000L) / 1000.0f) * rate * 3.2f;

        float flap = (float) Math.sin(t);
        float lagFlap = (float) Math.sin(t - 0.85f);

        float proxX = 0.0f;
        float proxY = (gliding ? -8.0f : -12.0f) + flap * (gliding ? 14.0f : 8.0f);
        float proxZ = -16.0f + flap * 7.0f;

        float distX = 0.0f;
        float distY = (gliding ? -6.0f : -10.0f) + lagFlap * (gliding ? 12.0f : 8.0f);
        float distZ = 20.0f + lagFlap * 7.0f;

        setAngles(leftProximal, rightProximal, proxX, proxY, proxZ);
        setAngles(leftDistal, rightDistal, distX, distY, distZ);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex,
                       int packedLight, int tint, boolean gliding, boolean moving, float flapSpeed, long nowMs, int outlineColor) {
        setupAnim(gliding, moving, flapSpeed, nowMs);

        // 1) Submit articulated 3D bones & elbow claws
        collector.submitModelPart(
                root, poseStack, RenderTypes.entityCutoutNoCull(tex),
                packedLight, OverlayTexture.NO_OVERLAY, null, false, false, tint, null, outlineColor
        );

        // 2) Submit smooth cambered membranes attached directly to each bone's exact transform
        submitSideMembranes(poseStack, collector, tex, packedLight, tint, leftProximal, leftDistal, true);
        submitSideMembranes(poseStack, collector, tex, packedLight, tint, rightProximal, rightDistal, false);
    }

    private static void submitSideMembranes(PoseStack ps, SubmitNodeCollector collector, Identifier tex,
                                            int light, int tint, ModelPart prox, ModelPart dist, boolean left) {
        ps.pushPose();
        prox.translateAndRotate(ps);
        collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
            float xStart = left ? 0.0f : -10.0f;
            drawCamberedMembrane(pose, vc, light, tint, xStart, 10.0f, 0.4f, 10.4f, 0f, 8f, 10f, 18f, left);
        });

        dist.translateAndRotate(ps);
        collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
            float xStart = left ? 0.0f : -10.5f;
            drawCamberedMembrane(pose, vc, light, tint, xStart, 10.5f, 0.3f, 10.2f, 0f, 18f, 10f, 28f, left);
        });
        ps.popPose();
    }

    /**
     * Subdivides the wing membrane into 4 smooth cambered strips so it arches naturally like a real dragon wing!
     */
    private static void drawCamberedMembrane(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                             float ox, float widthX, float oz, float depthZ,
                                             float u0, float v0, float u1, float v1, boolean left) {
        final int strips = 4;
        for (int i = 0; i < strips; i++) {
            float t0 = i / (float) strips;
            float t1 = (i + 1) / (float) strips;
            // Hang downward (+Y) from the upper wing spar with a gentle backward camber (+Z)
            float y0 = (oz + depthZ * t0) / 16.0f;
            float y1 = (oz + depthZ * t1) / 16.0f;
            float zArch0 = (float) Math.sin(t0 * Math.PI) * (0.95f / 16.0f);
            float zArch1 = (float) Math.sin(t1 * Math.PI) * (0.95f / 16.0f);

            float x0 = ox / 16.0f;
            float x1 = (ox + widthX) / 16.0f;
            float tu0 = (left ? u0 : u1) / 30.0f;
            float tu1 = (left ? u1 : u0) / 30.0f;
            float tv0 = (v0 + (v1 - v0) * t0) / 30.0f;
            float tv1 = (v0 + (v1 - v0) * t1) / 30.0f;

            emit(pose, vc, x0, y0, zArch0, argb, tu0, tv0, light, 0f, 0f, 1f);
            emit(pose, vc, x1, y0, zArch0, argb, tu1, tv0, light, 0f, 0f, 1f);
            emit(pose, vc, x1, y1, zArch1, argb, tu1, tv1, light, 0f, 0f, 1f);
            emit(pose, vc, x0, y1, zArch1, argb, tu0, tv1, light, 0f, 0f, 1f);
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc,
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

    private static void setAngles(ModelPart left, ModelPart right, float xDeg, float yDeg, float zDeg) {
        right.xRot = (left.xRot = xDeg * DEG2RAD);
        right.yRot = -(left.yRot = yDeg * DEG2RAD);
        right.zRot = -(left.zRot = zDeg * DEG2RAD);
    }
}
