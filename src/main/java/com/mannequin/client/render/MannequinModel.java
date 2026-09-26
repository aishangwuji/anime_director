package com.mannequin.client.render;

import com.mannequin.MannequinMod;
import com.mannequin.entity.MannequinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The seven-head-tall, textureless mannequin body.
 *
 * <p>Unlike the vanilla player model (~3.6 heads tall), this body uses
 * animation-style proportions so a director's blockout reads correctly once it
 * is fed to a video model. Bone pivots are placed on the actual joints
 * (shoulder, elbow, hip, knee, neck) so the future pose presets can rotate
 * limbs around the right axis without re-authoring the rig.
 *
 * <p>The single flat texture means every box shares one uniform white texel;
 * LimbUV coordinates are therefore irrelevant and all cuboids use
 * {@code texOffs(0, 0)}.
 */
public class MannequinModel extends EntityModel<MannequinEntity> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(MannequinMod.id("mannequin"), "main");

    private static final int TEXTURE_SIZE = 16;

    private final ModelPart root;
    private final ModelPart pelvis;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MannequinModel(ModelPart root) {
        this.root = root;
        this.pelvis = root.getChild("pelvis");
        this.rightLeg = pelvis.getChild("right_leg");
        this.leftLeg = pelvis.getChild("left_leg");
        ModelPart chest = pelvis.getChild("chest");
        this.rightArm = chest.getChild("right_arm");
        this.leftArm = chest.getChild("left_arm");
        ModelPart neck = chest.getChild("neck");
        this.head = neck.getChild("head");
    }

    /**
     * Builds the mannequin rig.
     *
     * <p>Reason: the 28-pixel total height with a 4-pixel head is what yields
     * the intended 7-head (1:7) body ratio; changing the head height without
     * rebalancing the other segments breaks the ratio the art direction calls
     * for.
     *
     * @return the layer definition registered under {@link #LAYER_LOCATION}
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Pelvis sits at hip height (y=16); its box spans y=14..16.
        PartDefinition pelvis = root.addOrReplaceChild("pelvis",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.0F, -1.5F, 5.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 16.0F, 0.0F));

        pelvis.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, 0.0F, -1.5F, 5.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition chest = pelvis.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 0.0F, -2.0F, 7.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 3.0F, 0.0F));

        PartDefinition neck = chest.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.8F, 0.0F, -1.8F, 3.6F, 4.0F, 3.6F),
                PartPose.offset(0.0F, 1.0F, 0.0F));

        PartDefinition rightArm = chest.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
                PartPose.offset(4.0F, 4.0F, 0.0F));
        rightArm.addOrReplaceChild("right_forearm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
                PartPose.offset(0.0F, -5.0F, 0.0F));

        PartDefinition leftArm = chest.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
                PartPose.offset(-4.0F, 4.0F, 0.0F));
        leftArm.addOrReplaceChild("left_forearm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
                PartPose.offset(0.0F, -5.0F, 0.0F));

        // Legs hang from the pelvis; knee pivot is at the bottom of the thigh.
        PartDefinition rightLeg = pelvis.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(1.5F, -2.0F, 0.0F));
        rightLeg.addOrReplaceChild("right_shin",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.9F, -6.5F, -0.9F, 1.8F, 6.5F, 1.8F)
                        .addBox(-0.9F, -8.0F, -0.9F, 1.8F, 1.5F, 2.8F),
                PartPose.offset(0.0F, -6.0F, 0.0F));

        PartDefinition leftLeg = pelvis.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(-1.5F, -2.0F, 0.0F));
        leftLeg.addOrReplaceChild("left_shin",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.9F, -6.5F, -0.9F, 1.8F, 6.5F, 1.8F)
                        .addBox(-0.9F, -8.0F, -0.9F, 1.8F, 1.5F, 2.8F),
                PartPose.offset(0.0F, -6.0F, 0.0F));

        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    public void setupAnim(MannequinEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 重置所有关节旋转角基准
        this.pelvis.xRot = 0.0F; this.pelvis.yRot = 0.0F; this.pelvis.zRot = 0.0F;
        this.head.xRot = 0.0F; this.head.yRot = 0.0F; this.head.zRot = 0.0F;
        this.rightArm.xRot = 0.0F; this.rightArm.yRot = 0.0F; this.rightArm.zRot = 0.0F;
        this.leftArm.xRot = 0.0F; this.leftArm.yRot = 0.0F; this.leftArm.zRot = 0.0F;
        this.rightLeg.xRot = 0.0F; this.rightLeg.yRot = 0.0F; this.rightLeg.zRot = 0.0F;
        this.leftLeg.xRot = 0.0F; this.leftLeg.yRot = 0.0F; this.leftLeg.zRot = 0.0F;

        com.mannequin.entity.MannequinPose pose = entity.getMannequinPose();
        switch (pose) {
            case RUNNING -> {
                // 奔跑冲刺：躯干前倾，四肢大步迈进
                this.pelvis.xRot = 0.25F;
                this.head.xRot = headPitch * ((float) Math.PI / 180F) - 0.2F;
                this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
                this.rightArm.xRot = -1.1F + (float) Math.cos(limbSwing * 0.6662F) * 0.6F;
                this.leftArm.xRot = 0.9F + (float) Math.cos(limbSwing * 0.6662F + Math.PI) * 0.6F;
                this.rightLeg.xRot = (float) Math.cos(limbSwing * 0.6662F) * 1.5F;
                this.leftLeg.xRot = (float) Math.cos(limbSwing * 0.6662F + Math.PI) * 1.5F;
            }
            case FIGHTING -> {
                // 双手抱胸 / 格斗迎敌防御架势
                this.head.xRot = headPitch * ((float) Math.PI / 180F);
                this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
                this.rightArm.xRot = -0.85F; this.rightArm.yRot = -0.45F; this.rightArm.zRot = 0.55F;
                this.leftArm.xRot = -0.85F; this.leftArm.yRot = 0.45F; this.leftArm.zRot = -0.55F;
                this.rightLeg.xRot = 0.15F; this.leftLeg.xRot = -0.15F;
            }
            case CROUCHING -> {
                // 下蹲潜行 / 刺客单膝潜伏
                this.pelvis.xRot = 0.4F;
                this.head.xRot = headPitch * ((float) Math.PI / 180F) + 0.2F;
                this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
                this.rightLeg.xRot = -0.7F; this.leftLeg.xRot = -0.7F;
                this.rightArm.xRot = -0.4F; this.leftArm.xRot = -0.4F;
            }
            case AIMING -> {
                // 抬臂向前瞄准指引 / 施法
                this.head.xRot = headPitch * ((float) Math.PI / 180F);
                this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
                this.rightArm.xRot = -1.57F; this.rightArm.yRot = -0.1F;
                this.leftArm.xRot = -0.2F; this.leftArm.zRot = -0.2F;
            }
            case FALLEN -> {
                // 受击后仰 / 倒地负伤
                this.pelvis.xRot = -1.35F;
                this.head.xRot = -0.4F;
                this.rightArm.xRot = 0.3F; this.rightArm.zRot = 0.85F;
                this.leftArm.xRot = 0.3F; this.leftArm.zRot = -0.85F;
                this.rightLeg.xRot = -0.2F; this.leftLeg.xRot = -0.35F;
            }
            case REST -> {
                // 自然站立待机与行走摆臂联动
                this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
                this.head.xRot = headPitch * ((float) Math.PI / 180F);
                this.rightLeg.xRot = (float) Math.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
                this.leftLeg.xRot = (float) Math.cos(limbSwing * 0.6662F + Math.PI) * 1.4F * limbSwingAmount;
                this.rightArm.xRot = (float) Math.cos(limbSwing * 0.6662F + Math.PI) * 1.2F * limbSwingAmount;
                this.leftArm.xRot = (float) Math.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
