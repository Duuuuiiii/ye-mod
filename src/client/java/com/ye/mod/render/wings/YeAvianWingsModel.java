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
 * 4-joint articulated 3D Wing Model ported from Fantastic Wings (AvianWingsModel).
 * Renders 4-bone 3D skeleton via ModelPart + 4 crisp 64x64 UV-mapped feather/membrane panels.
 */
public class YeAvianWingsModel {
    private static final float DEG2RAD = (float) (Math.PI / 180.0);

    private final ModelPart root;
    private final ModelPart[] bonesLeft;
    private final ModelPart[] bonesRight;
    private final ModelPart[] feathersLeft;
    private final ModelPart[] feathersRight;

    public YeAvianWingsModel() {
        this.root = createWingsLayer().bakeRoot();
        ModelPart leftCoracoid = root.getChild("left_coracoid");
        ModelPart rightCoracoid = root.getChild("right_coracoid");
        ModelPart leftHumerus = leftCoracoid.getChild("left_humerus");
        ModelPart rightHumerus = rightCoracoid.getChild("right_humerus");
        ModelPart leftUlna = leftHumerus.getChild("left_ulna");
        ModelPart rightUlna = rightHumerus.getChild("right_ulna");
        ModelPart leftCarpals = leftUlna.getChild("left_carpals");
        ModelPart rightCarpals = rightUlna.getChild("right_carpals");

        this.bonesLeft = new ModelPart[]{leftCoracoid, leftHumerus, leftUlna, leftCarpals};
        this.bonesRight = new ModelPart[]{rightCoracoid, rightHumerus, rightUlna, rightCarpals};
        this.feathersLeft = new ModelPart[]{
                leftCoracoid.getChild("left_coracoid_feathers"),
                leftHumerus.getChild("left_tertiary_feathers"),
                leftUlna.getChild("left_secondary_feathers"),
                leftCarpals.getChild("left_primary_feathers")
        };
        this.feathersRight = new ModelPart[]{
                rightCoracoid.getChild("right_coracoid_feathers"),
                rightHumerus.getChild("right_tertiary_feathers"),
                rightUlna.getChild("right_secondary_feathers"),
                rightCarpals.getChild("right_primary_feathers")
        };
    }

    private static LayerDefinition createWingsLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition leftCoracoid = root.addOrReplaceChild("left_coracoid",
                CubeListBuilder.create().texOffs(0, 28).addBox(0.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F),
                PartPose.offset(1.5F, 5.5F, 2.5F));
        PartDefinition rightCoracoid = root.addOrReplaceChild("right_coracoid",
                CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F),
                PartPose.offset(-1.5F, 5.5F, 2.5F));
        PartDefinition leftHumerus = leftCoracoid.addOrReplaceChild("left_humerus",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.1F, -1.1F, -2.0F, 7.0F, 3.0F, 4.0F),
                PartPose.offset(4.7F, -0.6F, 0.1F));
        PartDefinition rightHumerus = rightCoracoid.addOrReplaceChild("right_humerus",
                CubeListBuilder.create().texOffs(0, 7).addBox(-6.9F, -1.1F, -2.0F, 7.0F, 3.0F, 4.0F),
                PartPose.offset(-4.7F, -0.6F, 0.1F));
        PartDefinition leftUlna = leftHumerus.addOrReplaceChild("left_ulna",
                CubeListBuilder.create().texOffs(22, 0).addBox(0.0F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F),
                PartPose.offset(6.5F, 0.2F, 0.1F));
        PartDefinition rightUlna = rightHumerus.addOrReplaceChild("right_ulna",
                CubeListBuilder.create().texOffs(22, 6).addBox(-9.0F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F),
                PartPose.offset(-6.5F, 0.2F, 0.1F));
        PartDefinition leftCarpals = leftUlna.addOrReplaceChild("left_carpals",
                CubeListBuilder.create().texOffs(46, 0).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F),
                PartPose.offset(8.5F, 0.0F, 0.0F));
        PartDefinition rightCarpals = rightUlna.addOrReplaceChild("right_carpals",
                CubeListBuilder.create().texOffs(46, 4).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F),
                PartPose.offset(-8.5F, 0.0F, 0.0F));

        // Empty feather pivot nodes (exact joint hierarchy, rendered as crisp double-sided UV panels)
        leftCoracoid.addOrReplaceChild("left_coracoid_feathers", CubeListBuilder.create(), PartPose.offset(0.4F, 0.0F, 1.0F));
        rightCoracoid.addOrReplaceChild("right_coracoid_feathers", CubeListBuilder.create(), PartPose.offset(-0.4F, 0.0F, 1.0F));
        leftHumerus.addOrReplaceChild("left_tertiary_feathers", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, 1.0F));
        rightHumerus.addOrReplaceChild("right_tertiary_feathers", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, 1.0F));
        leftUlna.addOrReplaceChild("left_secondary_feathers", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, 0.0F));
        rightUlna.addOrReplaceChild("right_secondary_feathers", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, 0.0F));
        leftCarpals.addOrReplaceChild("left_primary_feathers", CubeListBuilder.create(), PartPose.ZERO);
        rightCarpals.addOrReplaceChild("right_primary_feathers", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static final float[][] REST_BONES = {
            {0.0f, -23.5f, -16.0f},
            {0.0f, 13.0f, 29.0f},
            {0.0f, 12.0f, -28.0f},
            {0.0f, 4.0f, 18.3f}
    };
    private static final float[][] IDLE_BONES = {
            {40.0f, -60.0f, -50.0f},
            {72.0f, 10.0f, 100.0f},
            {0.0f, -10.0f, -120.0f},
            {10.0f, 0.0f, 100.0f}
    };
    private static final float[][] IDLE_FEATHERS = {
            {10.0f, 20.0f, 23.48f},
            {0.0f, 20.0f, -70.0f},
            {0.0f, 10.0f, 40.0f},
            {-20.0f, 0.0f, 20.0f}
    };

    public void setupProceduralAnim(boolean gliding, boolean walking, float speed, long nowMs) {
        float t = (nowMs % 120000L) / 1000.0f * speed * (gliding ? 3.2f : (walking ? 3.8f : 1.4f));

        for (int i = 0; i < 4; i++) {
            float pos = Math.min(Math.abs(i - 1), 2) / 2.0f;
            float bx, by, bz;
            float fx = 0, fy = 0, fz = 0;
            if (gliding) {
                float cycle = t - pos * 1.1f;
                bx = REST_BONES[i][0] + (float) ((Math.sin(cycle + Math.PI / 2.0) - 1.0) * 6.0);
                by = REST_BONES[i][1] + (float) ((Math.sin(cycle) * 14.0 - 12.0) * pos);
                bz = REST_BONES[i][2];
                if (i == 0) fz = 23.48f;
            } else if (walking) {
                float cycle = t - pos * 0.9f;
                bx = IDLE_BONES[i][0] * 0.65f + REST_BONES[i][0] * 0.35f;
                by = IDLE_BONES[i][1] * 0.65f + REST_BONES[i][1] * 0.35f + (float) Math.sin(cycle) * 8.0f * pos;
                bz = IDLE_BONES[i][2] * 0.65f + REST_BONES[i][2] * 0.35f;
                fx = IDLE_FEATHERS[i][0] * 0.65f;
                fy = IDLE_FEATHERS[i][1] * 0.65f - (float) Math.sin(cycle) * 6.0f * pos;
                fz = IDLE_FEATHERS[i][2] * 0.65f + (i == 0 ? 23.48f * 0.35f : 0.0f);
            } else {
                bx = IDLE_BONES[i][0];
                by = IDLE_BONES[i][1] + (float) Math.sin(t) * 3.0f * pos;
                bz = IDLE_BONES[i][2];
                fx = IDLE_FEATHERS[i][0];
                fy = IDLE_FEATHERS[i][1] - (float) Math.sin(t) * 5.0f * pos;
                fz = IDLE_FEATHERS[i][2];
            }
            setAngles(bonesLeft[i], bonesRight[i], bx, by, bz);
            setAngles(feathersLeft[i], feathersRight[i], fx, fy, fz);
        }
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex,
                       int packedLight, int tint, int outlineColor) {
        // 1) Submit 4-bone 3D skeleton
        collector.submitModelPart(
                root, poseStack, RenderTypes.entityCutoutNoCull(tex),
                packedLight, OverlayTexture.NO_OVERLAY, null, false, false, tint, null, outlineColor
        );

        // 2) Submit 4 feather/membrane panels per side with exact 64x64 UVs
        submitFeatherChain(poseStack, collector, tex, packedLight, tint, bonesLeft, feathersLeft, true);
        submitFeatherChain(poseStack, collector, tex, packedLight, tint, bonesRight, feathersRight, false);
    }

    private void submitFeatherChain(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex,
                                    int light, int tint, ModelPart[] bones, ModelPart[] feathers, boolean left) {
        poseStack.pushPose();
        for (int i = 0; i < 4; i++) {
            bones[i].translateAndRotate(poseStack);
            poseStack.pushPose();
            feathers[i].translateAndRotate(poseStack);
            final int seg = i;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> {
                switch (seg) {
                    case 0 -> drawFeatherPanel(pose, vc, light, tint, left ? 6 : 0, 40, left ? 0 : -6, 0, -0.5f, 6, 8);
                    case 1 -> drawFeatherPanel(pose, vc, light, tint, left ? 10 : 0, 14, left ? 0 : -10, 0, -0.25f, 10, 14);
                    case 2 -> drawFeatherPanel(pose, vc, light, tint, left ? 31 : 20, 14, left ? -2 : -9, 0, -0.25f, 11, 12);
                    case 3 -> drawFeatherPanel(pose, vc, light, tint, left ? 53 : 42, 14, left ? 0 : -11, -2.1f, -0.25f, 11, 11);
                }
            });
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawFeatherPanel(PoseStack.Pose pose, VertexConsumer vc, int light, int argb,
                                         float texU, float texV, float ox, float oy, float oz, float w, float h) {
        float x0 = ox / 16.0f, x1 = (ox + w) / 16.0f;
        float y0 = oy / 16.0f, y1 = (oy + h) / 16.0f;
        float z = oz / 16.0f;
        float u0 = texU / 64.0f, u1 = (texU + w) / 64.0f;
        float v0 = texV / 64.0f, v1 = (texV + h) / 64.0f;
        // Front & Back faces (entityCutoutNoCull handles double-sided rendering cleanly)
        emit(pose, vc, x0, y0, z, argb, u0, v0, light, 0f, 0f, -1f);
        emit(pose, vc, x1, y0, z, argb, u1, v0, light, 0f, 0f, -1f);
        emit(pose, vc, x1, y1, z, argb, u1, v1, light, 0f, 0f, -1f);
        emit(pose, vc, x0, y1, z, argb, u0, v1, light, 0f, 0f, -1f);
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

    private static void setAngles(ModelPart left, ModelPart right, float xDeg, float yDeg, float zDeg) {
        right.xRot = (left.xRot = xDeg * DEG2RAD);
        right.yRot = -(left.yRot = yDeg * DEG2RAD);
        right.zRot = -(left.zRot = zDeg * DEG2RAD);
    }
}
